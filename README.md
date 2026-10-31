# Shovel JS

Editor visual dentro de Minecraft para armar modpacks. Editas recetas, items, mobs, loot, tags, comercios, estructuras y generación de mundo desde el juego, y Shovel JS escribe los scripts de KubeJS y los archivos de datapack por ti. Cada cambio pasa por una cola de pendientes, con diff antes de aplicar y deshacer.

Disponible para **Forge 1.20.1** y **NeoForge 1.21.1**.

## Qué incluye

- **Recetas**: crafteo vanilla y máquinas de mods (Create, Mekanism, Ars Nouveau, Immersive Engineering, Botania y más) en el JSON nativo de cada mod. Grafo de recetas para ver cómo se hace un item y para qué sirve, y editar cualquier receta desde ahí.
- **Items, bloques y mobs**: atributos, comida, combate, reglas de minado, stats, equipo y comportamiento.
- **Loot tables, tags y comercios**, con plantillas reutilizables.
- **Estructuras**: editor de vóxeles, vista fantasma en el mundo y frecuencia de generación.
- **Generación de mundo**: vetas de mineral, spawns, parches de superficie, quitar features y presets de terreno, con dimensión de prueba. Compatible con TerraBlender, Lithostitched y mods que reemplazan el terreno, como Terralith.
- **Herramientas del pack**: reporte de problemas, detector de conflictos, revisión de balance, comparar versiones y guía del pack en Markdown.

## Instalación

Cada versión trae dos jars:

| Jar | Qué es |
|---|---|
| `shovel_api-<mc>-<ver>.jar` | Lo que el juego necesita para aplicar los cambios. Va siempre en el modpack. |
| `shovel_js-<mc>-<ver>.jar` | El editor. Requiere Shovel API. Se puede quitar del pack terminado: todo lo hecho sigue funcionando. |

**Requisitos:** KubeJS. **Opcionales:** JEI o EMI, Jade, Curios.

## Compilar

```
cd 1.21.1          # o 1.20.1
.\gradlew.bat build      # compila, corre los tests y arma los dos jars en build/libs
.\gradlew.bat runClient  # cliente de prueba
```

## Estructura del repositorio

| Carpeta | Contenido |
|---|---|
| `1.20.1/` | Forge 1.20.1 |
| `1.21.1/` | NeoForge 1.21.1 |
| `1.21.1/ARCHITECTURE.md` | Mapa del código y reglas que verifican los tests |
| `1.21.1/docs/` | Notas de diseño |

Las dos versiones comparten las mismas funciones. Los cambios están en [CHANGELOG.md](CHANGELOG.md).

## Licencia

[MIT](LICENSE)
