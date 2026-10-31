# Arquitectura de KVE

Mapa corto para saber **dónde va cada cosa** antes de corregir un bug o agregar una feature.
Las reglas marcadas con 🔒 las verifica `ArchitectureRulesTest`: si las rompes, el build de tests falla.

## Capas

| Paquete | Qué contiene | Puede depender de |
|---|---|---|
| `com.kve.common.*` | Modelo de datos, generación de JS/JSON, persistencia, red. Carga también en servidor dedicado. | Minecraft/Forge común, `common.util` |
| `com.kve.client.*` | Pantallas, render, estado de UI, cola de cambios pendientes. Solo cliente. | `common` |
| `com.kve.integration.*` | Plugins opcionales (KubeJS, JEI/EMI, Jade). | `common`, `client` |
| `com.kve.mixin` | Mixins. | — |

🔒 `common/` no puede empezar a usar `com.kve.client.*` (crashea en servidor dedicado). Hay 18 archivos
heredados en una lista congelada en el test; esa lista solo puede achicarse.

### Paquetes de `client.gui`

Las pantallas viven en un subpaquete por dominio; en `client.gui` quedan solo las piezas compartidas
(`KveGuiStyle`, `KvePlateButton`, `KveScaledEditBox`, `ScrollBar`, `ResizeHandle`, `KveConfirmScreen`,
`DiffPreviewScreen`, `RegistryIndex`, `SearchFilter`...).

| Subpaquete | Qué contiene |
|---|---|
| `recipe` (+ `recipe.ritual`) | Editor de recetas, máquinas, navegador y herramientas de recetas |
| `properties` | Items y bloques: atributos, comida, combate, curios, ocultar, creador de items/bloques |
| `mob` | Editor de mobs y sus diálogos (atributos, equipo, plantillas, comportamiento) |
| `loot`, `tag`, `trade`, `worldgen`, `event` | Un editor por dominio con sus diálogos |
| `code` | Editor de código/archivos |
| `pack` | Herramientas del pack: escaneo, import/export, diagnóstico, reglas, permisos, "dónde se usa" |
| `settings` | Menú principal, Ajustes, aviso de inicio y guía rápida |
| `picker` | Pickers reusables (item, efecto, encantamiento, fluido, NBT...) |
| `studio`, `history`, `workspace` | El shell de pestañas, el historial y la cola de cambios pendientes |

Una pantalla nueva va en el subpaquete de su dominio. Si algo lo usan varios dominios, va en `picker`
(si es un selector) o en `client.gui` (si es un widget).

## Flujo de un cambio (todos los dominios)

```
Pantalla (client/gui)           edita un borrador en memoria
   │  PendingChangeManager.queue(PendingChange.of(...).withDiff(...).withFile(...))
   ▼
Cola de cambios pendientes      el usuario ve el diff y elige Apply o Discard
   │  onApply
   ▼
Exporter (common/kubejs, ...)   guarda el estado JSON y genera el script
   │  ScriptWriteGuard.writeIfValid(ruta, js, etiqueta)
   ▼
Disco                           kubejs/{startup,server,client}_scripts/shovel_js/...  (+ .bak)
   │
   ▼
KveChangeLog.record(log, acción, id)   historial → notas de versión
```

- **Estado del editor** (lo que el usuario armó): `kubejs/config/shovel_js_*.json` vía `SafeFiles.writeAtomic`.
- **Salida para KubeJS**: siempre pasa por `ScriptWriteGuard.writeIfValid`. Chequea llaves y comillas antes de escribir, escribe de forma atómica y deja un `.bak`.

## Dónde va cada cosa

| Necesito… | Usar | No hacer |
|---|---|---|
| Ruta de un script o archivo de config | `KveScriptPaths` (`SERVER_ROOT`, `CONFIG_ROOT`, `recipeAddFile(mod)`…) | 🔒 `Paths.get("kubejs", "config")` en un `configDir()` propio |
| Meter texto dentro de un string JS | `JsStrings.escape` / `quote` / `quoteDouble` | 🔒 `.replace("'", "\\'")` a mano; helpers `jsEscape` privados |
| Escribir un número en un script | `KveNumbers.trimmed(v, 4)` (código nuevo); `padded4` solo donde los golden tests ya lo fijan | 🔒 `String.format("%.4f")` en `common/` (y nunca sin `Locale.ROOT`) |
| Escribir un archivo de estado | `SafeFiles.writeAtomic(path, texto)` | 🔒 `Files.move(..., ATOMIC_MOVE)` a mano |
| Escribir un script generado | `ScriptWriteGuard.writeIfValid` | `Files.writeString` directo al script |
| Soportar un tipo de receta de otro mod | Un JSON en `src/main/resources/data/shovel_js/recipe_types/` (formato verificado contra el jar real) | Código Java por mod |
| Ícono chico de un ítem al lado de un id en una lista | `MiniItemIcon.draw(g, x, y, size, MiniItemIcon.item/block/itemOrTag(id))` | Otro bloque `pushPose/scale/renderItem` copiado |
| Que un dominio nuevo aparezca en "Donde se usa", el diagnostico de IDs rotos, el informe por mod y el chequeo al Aplicar | Agregar su bloque en `ProjectReferences.collect()` (con el `Kind` correcto) | Otro recorrido propio de los exporters |
| Abrir para editar una receta YA cargada de cualquier mod (navegador, JEI/EMI) | `ExistingRecipeLoader.toRecipeData` (entradas y campos extra via `RawRecipeInputs`, que lee el JSON crudo siguiendo el template `rawJson` del tipo) | Otra copia de la conversion Recipe -> RecipeData |
| Saber si un id lo declara el propio proyecto (items/bloques del creador, `event.create`, `.id(...)` de receta, tags del editor) | `DeclaredIds.isDeclared(id)` | Marcarlo como "id desconocido" porque todavia no esta en el registro |
| Elegir muchos ids por patron (`create:*`, `*_ore`, `#tag`, `!excluir`) | `IdPattern.select(...)` | Otro parser de comodines |
| Un cambio sobre muchos items | `BulkItemQueue.queue(ids, BulkItemEdit, ...)`: un solo cambio pendiente, con deshacer | Un `PendingChange` por item |
| Una regla global del pack (nombres, compost, spawns, gamerules, info JEI) | `PackRules` + `PackRulesExporter`; lo que corre en el juego va en Java (`PackRulesRuntime`), registrado desde el script con `ShovelAPI.*` | JavaScript que corre por evento |
| Un ingrediente "cualquier item del tag" en una grilla de ItemStacks | `TagIngredient.of(tag, item)` / `tagOf(stack)`; el exporter ya lo emite como `'#tag'` o `{tag: ...}` | Un campo nuevo en `RecipeData` o un id con `#` dentro de un ItemStack |
| Transformar la grilla (espejar, girar, desplazar, rellenar, plantillas) | `GridOps` / `RecipeTemplates` (devuelven grilla nueva o null si se perderian items) | Modificar `gridSlots` a mano sin `pushUndo()` |
| Un texto visible | Clave en `assets/shovel_js/lang/en_us.json`, `es_es.json` **y** `es_mx.json` | Strings literales en pantallas |
| Quitar features, spawns o parches de superficie en los biomas de una regla de world gen | `WorldGenEntry.removedFeatures/spawns/patches` + `BiomeExtrasExporter` (puro, con test) | Otro exporter de biome modifiers |
| Registrar en el historial | `KveChangeLog.record(KveChangeLog.Log.X, acción, id)` dentro del `onApply` | Escribir en el historial al encolar |

## Receta: agregar una feature nueva

1. **Lógica pura en `common/`**, sin clases de pantalla: validación, generación del script y lectura/escritura del estado. Así se puede testear sin arrancar el juego (ej. `RecipeReplacementExporter`, `PackSnapshotManager`).
2. **Test en `src/test/java`** con el mismo paquete. Para salida JS, un golden test (texto exacto esperado).
3. **Pantalla en `client/gui/`**: solo UI. Encola un `PendingChange` en vez de escribir directo a disco (ej. `RecipeReplacementQueue`).
4. **Textos** en los 3 archivos de lang.
5. **Rutas nuevas** en `KveScriptPaths`. Si generan archivos propios, confirmar que `PackSnapshotManager` los incluye.
6. `.\gradlew.bat test`. Los resultados están en `build/test-results/test/*.xml`.

## Tests

- **Golden tests** (`*GoldenTest`, `*GoldenSnapshotTest`): fijan byte a byte el JS de cada exporter. Si cambias el formato a propósito, actualiza el test en el mismo cambio y dilo en el changelog, porque los packs de los usuarios verán un diff.
- **`ArchitectureRulesTest`**: las reglas 🔒 de este documento.
- **`RecipeTypeSlotLayoutStaticQaTest`**: valida todos los descriptores de `recipe_types/`.

## Deuda conocida (en orden de impacto)

1. **Pantallas gigantes.** Tamaños actuales:

   | Pantalla | Líneas |
   |---|---|
   | `CodeEditorScreen` | 4.7k |
   | `MobEditorScreen` | 3.8k |
   | `MachineRecipeEditScreen` | 3.3k |
   | `CraftEditorScreen` | 3.3k |
   | `ItemAttributeScreen` | 3.1k |

   Mezclan layout, input, estado y lógica. Hay que extraer la lógica a clases de `common/` con test, primero la que más bugs acumula, y dejar la pantalla solo como vista.
2. **Sin clase base de pantalla.** 80 pantallas repiten `parent`, ESC, `isPauseScreen` y mensajes flash (28 copias). Una `KveScreen` base serviría para las pantallas nuevas y para migrar las existentes de a poco.
3. **Dependencias `common → client`** (18 archivos). Sobre todo `Lang.get` y `ClientPermissionState` dentro de exporters. Los mensajes deberían pasarse como claves y traducirse en la pantalla.
4. **35 `new GsonBuilder()`** repartidos por el código. Conviene una instancia compartida si algún día hay que cambiar la configuración de serialización.
