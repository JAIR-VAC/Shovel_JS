# Registro de cambios

## Sin publicar

### Cambio incompatible: todo pasa a llamarse `shovel_js`
- El nombre interno viejo `kve` desaparece de todo lo que se ve. Los items y bloques creados son `shovel_js:<id>`, las recetas `shovel_js:<id>`, los scripts van a `kubejs/{startup,server,client}_scripts/shovel_js/`, el estado del editor a `kubejs/config/shovel_js_*.json` y los datos a `kubejs/data/shovel_js/`.
- **No se migra nada.** Los scripts, recetas e items hechos con una versión anterior hay que volver a crearlos (o cambiar sus ids a mano).
- Se quitó el alias de script `KveAPI`; usa `ShovelAPI`.
- Todos los recursos que usa el juego viven en el jar de Shovel API, así que el pack sigue funcionando después de quitar el editor.

### Sin addons de KubeJS para loot, comercios y autocompletado
Shovel JS ya no usa ni necesita addons de KubeJS para estas tres cosas:
- **Loot tables**: siempre se escriben como JSON de datapack.
- **Comercios**: siempre se aplican con el backend propio de Shovel JS, que cubre todos los modos y los comerciantes de mods. El botón "Backend" desapareció del editor de comercios. Los comercios guardados con un backend que ya no existe pasan solos al propio. `trades.js` ya no se escribe cuando no hace falta.
- **Autocompletado**: vive en el editor de código del juego. Dentro de un evento, `event.` sugiere solo los métodos de ese evento (recetas, tags, modificación de items/bloques...), y los ids ahora incluyen entidades, atributos y tags (escribe `#` para tags, o un id de tag dentro de `ServerEvents.tags`).
- Se quitaron las opciones `loot_export_backend` y `trades_native_backend_enabled`.
- Curios/Accessories nunca fue obligatorio y ya no aparece en la pantalla de inicio. Si otro mod del pack lo trae, el editor de atributos al equiparlo aparece solo.

### Interfaz
- **Se adapta a tu pantalla.** Con la escala de GUI alta (por ejemplo Auto en 1080p) las ventanas del mod se salían. Ahora, mientras una pantalla de Shovel JS está abierta, se usa la escala más grande que no supera la tuya y en la que todo entra; al salir vuelve la del juego. Se cambia en Ajustes → "Tamaño de la interfaz" (Auto, Igual que el juego, 1×–4×).
- **Botones ordenados igual en todos los editores**: a la vista solo Guardar, "Más ▾" y Eliminar (al final, en rojo). El menú "Más ▾" tiene el resto con su nombre, en grupos separados: Vista previa y Duplicar; las herramientas de cada editor (plantillas, ver drops, historial…); Activo y Solo lectura; Deshacer y Restablecer. Desaparecen los botones "Mostrar más", que cambiaban el tamaño de la fila.
- En Atributos de items quedan a la vista Comida, Tooltip, Combate, Atributos de Curios y Ocultar de, con su cuenta. La lista de items y bloques modificados ya no se sale del Studio.
- En las ventanas chicas (valores, comida, combate, plantillas…), Confirmar o Guardar va destacado y Borrar o Descartar en rojo.
- Los textos del editor de máquinas y los avisos de tipo de receta estaban fijos en español; ahora siguen el idioma del juego.

- **Detector de conflictos corregido**: antes marcaba como error cualquier par de recetas con el mismo resultado, que es normal (varias formas de hacer lo mismo). Ahora el conflicto es **mismos ingredientes con distinto resultado**, porque el juego solo usa una de las dos. Compara la forma como lo hace la mesa: un patrón chico en cualquier posición y también espejado. La ventana tiene dos pestañas: "Mismos ingredientes" (los conflictos reales) y "Mismo resultado" (solo informativo).
- En el editor de recetas, "Editar varias", "Grafo" y "Compartir" pasan a la franja del título, a la derecha, siempre en el mismo lugar.
- En el editor de tags la fila de Guardar, Más y Eliminar vuelve a estar abajo, como en los demás editores.
- Pasar el mouse sobre un item en las listas y selectores ya no hace parpadear las pestañas ni desaparecer el Studio. Los tooltips de las pantallas del mod se dibujan al final, como en vanilla; mods de tooltips como Tooltip Overhaul cambiaban el dibujo de todo lo que venía después.
- Editor de mobs (1.20.1): estrella de favorito en la lista de guardados, y los mobs guardados de un mod que no está instalado se pueden abrir para revisarlos o borrarlos, como en 1.21.1.
- Editor de máquinas (1.21.1): las ofrendas de ritual que son tags muestran su tooltip con los items, como en 1.20.1.
- Al Aplicar vuelve el aviso en la esquina (como un logro), sin ventana que cerrar. El texto sigue el tamaño de letra de Ajustes y el mensaje se reparte en varias líneas en vez de cortarse.
- Los menús desplegables ("Más ▾" y los menús del editor de archivos) siguen el tamaño de letra de Ajustes; antes tenían un tamaño propio.
- Editor de comercios: la lista de comercios existentes usa toda la altura disponible.
- Editor de archivos: la barra de desplazamiento horizontal se puede arrastrar o pulsar para saltar, y Shift+rueda avanza el triple que antes. En 1.21.1 también funcionan la rueda lateral y el gesto horizontal del touchpad.
- Editor de generación de mundo más simple: debajo de "Vetas" quedan "Añadir veta" y "Desde plantilla ▾", que lista los minerales vanilla por nombre. Activo, "Vetas: la mitad" y "Vetas: el doble" pasan al menú "Más ▾"; desaparecen la fila de plantillas y los botones +, ×½ y ×2.
- Editor de estructuras: el contador de bloques ya no suma los bloques borrados (quedaban guardados como aire). En Materiales, las puertas, plantas altas y camas cuentan una vez y no dos.
- **Creador de items** con una columna nueva "Tipo y extras": tipo (item normal, espada, pico, hacha, pala, azada) con su material, brillo, tiempo de combustible, texto bajo el nombre, tags y comida (hambre, saturación, siempre comestible, se come rápido). Las herramientas se ven en diagonal en la mano.
- **Creador de bloques**: sonido, con qué herramienta se pica y desde qué nivel, transparente (como el vidrio), sin colisión, resbaladizo, lento y tags.
- **Creador de fluidos** nuevo, en la pestaña Crear: nombre, color (con colores rápidos), fino como el agua o espeso como la lava, con o sin cubo, y tags. KubeJS arma la textura, el bloque y el cubo.
- En 1.20.1 los bloques creados con "Requiere herramienta correcta" usaban un método que KubeJS 6 no tiene y no se registraban.
- Editor de máquinas: el selector de items de las entradas (y de las ofrendas y el centro de los rituales) permite elegir un tag, como en el editor normal.
- Editor de tags: cada tag de la lista tiene una estrella de favorito a la derecha (los favoritos van arriba) y una vista a la izquierda que va mostrando sus items despacio.
- Quitar receta: buscador para las recetas quitadas y en cola, por id o por el nombre de lo que fabrican.
- El botón de conflictos del editor de recetas también aparece cuando solo hay recetas con el mismo resultado; en ese caso es informativo y abre esa pestaña.
- Los selectores con selección múltiple (biomas, ocultar item, usos de un item, miembros de tag, exportar recetas) usan la misma casilla que "Editar varias".
- Materiales de la estructura: cada bloque tiene "Reemplazar", que cambia todos los de ese tipo por el bloque que elijas, conservando la orientación cuando se puede. Se deshace con Ctrl+Z.

### Rendimiento
- Los tags ya no se recalculan en cada fotograma: cada ranura de tag pedía la lista completa de sus items dos o tres veces por fotograma.
- El texto recortado ("…") y las fechas de las listas se calculan una vez y se reutilizan.
- Historial de cambios: los íconos que no se encuentran (por ejemplo de recetas borradas) ya no se buscan de nuevo en cada fotograma recorriendo todas las recetas.
- La ventana de conflictos y el editor de generación de mundo dejan de recalcular conflictos y vetas repetidas en cada fotograma.

### Editor de recetas: arreglos
- Descartar el cambio pendiente de un renombrado en el editor de máquinas ya no borra la receta original.
- El editor de máquinas ya no guarda encima de otra receta tuya cuando le pones un ID que ya existe; avisa, como el editor normal.
- Quitar dos veces la misma receta ya no crea dos pendientes (descartar uno deshacía la quita del otro).
- Cambiar de receta, pulsar "Nuevo" o "Receta existente" con cambios sin guardar pregunta antes de descartarlos.
- En modo máquina dentro del Studio se puede escribir en el ID, la categoría y el JSON extra; los arrastres ya no quedan pegados al soltar, y el buscador de la izquierda recibe Retroceso y las flechas.
- "Nuevo" en modo máquina vacía el editor de máquinas y conserva la estación elegida.
- Borrar una receta de un mod cargada en el editor de máquinas la manda a la pestaña Quitar, en vez de no hacer nada.
- La cantidad del resultado respeta el stack máximo del item (un pico ya no sale de a 64).
- Limpiar (Ctrl+R) también reinicia la caja de cantidad, y los cambios del resultado secundario se pueden deshacer.
- La lista de recetas conserva el scroll al volver de un selector, y el contador de páginas ya no tapa el botón de conflictos.

### Aplicar cambios
- El mensaje después de Aplicar dice cómo entra en efecto cada cambio: **ya activo** (comercios y loot por interacción en un mundo local, que el mod aplica en el momento), **con /reload** (recetas, tags, loot) o **al volver a entrar al mundo** (items, bloques, world gen, frecuencia de estructuras). Con cambios de varios tipos dice cuántos de cada uno. Antes casi todo se anunciaba como "/reload", también lo que ya estaba activo o lo que /reload no aplica.
- La barra inferior y el aviso flotante muestran el mismo texto, y el botón "Ejecutar /reload" solo aparece si algo lo necesita. "Recarga automática" funciona desde el Aplicar de cualquier editor.

### Primeros pasos
- Nueva pantalla de bienvenida: elige para qué vienes (Modpack, Servidor, Scripts, Mundo). Muestra cómo fluye un cambio (Guardar, Pendientes, Aplicar, /reload) y tus primeros pasos, que se tachan según lo que realmente hiciste, cada uno con un botón que abre la pestaña correcta. Se vuelve a abrir desde Ajustes ("Perfil y primeros pasos").
- El Studio abre en la pestaña principal de tu perfil y marca las pestañas de tu perfil con una línea dorada.
- Después de Aplicar, aparece un botón "Ejecutar /reload" en la barra inferior cuando hace falta recargar (salvo que Recarga automática esté activa).

### Editor de recetas
- Las probabilidades de salida (Ctrl+rueda) se muestran en el propio slot como un porcentaje chico.
- Los slots de tag van rotando entre los items del tag (como JEI) y se ponen rojos si el tag está vacío o roto.
- Los slots obligatorios que siguen vacíos llevan un borde ámbar y "+".
- Los tipos sin salida (combustibles, prohibiciones de colocación, tierras de macetas, fuentes termoeléctricas, modificadores de spawner, tallado de gemas, mejoras de armadura de Ars, llenado de TConstruct...) ahora tienen un lienzo de estación en vez de la grilla simple: entradas, la estación y un icono de lo que hace la receta.
- La vista 3D de la estación gira despacio, y los tipos de receta pueden declarar bloques alrededor en el JSON (`stationStructure`): la prensa de Create sobre un depósito, las flores de Botania sobre pasto dentro de un anillo de los bloques que convierten, la Mana Pool sobre un Alchemy Catalyst, el altar de Goety y el aparato de Ars con sus pedestales.
- Arreglados ids de estación que no existían (vista vacía): Botany Pots, Botania (apothecary, terra plate, mana pool, elven gateway), triturador de Ars, horno de aleación de IE, mesa y cuenca de fundición de TConstruct.
- **Químicos de Mekanism**: las recetas con gases, slurries, pigmentos o infusiones (por ejemplo plutonio, desechos nucleares o una slurry) ya no se rechazan al guardar como si no existieran. El químico se busca en el registro de Mekanism en vez de en los fluidos de Forge, también en los campos de gas e infusión (gas de entrada del inyector, tipo de infusión). Un químico que no existe sigue rechazándose. En 1.20.1 además se rechaza una slurry en una casilla de gas; en 1.21.1 no se puede distinguir, porque Mekanism 10.7 junta todos los químicos en un solo registro.

- **Grafo de recetas** (botón "Grafo"): cada receta es un nodo y una línea une su resultado con la receta que lo usa. Dos modos: "Cómo se hace" (desde las materias primas hasta el item) y "Para qué sirve". Desde cualquier nodo: Editar (carga la receta en el editor), recorrer las recetas alternativas de un item con ◀ ▶, y "Ver desde aquí". Se mueve arrastrando, zoom con la rueda, profundidad ajustable. Historial: botón "◀ item anterior", Retroceso/Alt+← y los botones laterales del mouse; al reabrir el grafo vuelve al último item visto. También hay una vista en lista.
- **Conflictos entre tus recetas**: un botón "⚠ N" en la barra avisa de recetas idénticas o con el mismo resultado, y al guardar se avisa si la receta choca con otra tuya.
- El selector de ingredientes tiene **"Por tag"**: lista todos los tags de items y pone el tag como ingrediente. Se abre en modo item como siempre.
- Las recetas de **Mekanism** se abren con sus entradas (antes se abrían vacías: su formato envuelve el ingrediente un nivel más). El crafteo mecánico de **Create** se abre con su tipo y su grilla completa en vez de un 3x3 vanilla.
- Las recetas de máquina (Mekanism, Create y cualquier tipo con casilleros propios) tienen la insignia **"N"** del editor de NBT en sus casilleros de item, igual que la grilla (clic en la "N" o Ctrl+clic).

#### Tipos de receta nuevos (JSON nativo, sin addons de KubeJS)
- **Immersive Engineering**: Bottling Machine, Squeezer, Mixer, Refinery, triturado con Engineer's Hammer, combustible del Diesel Generator, fuentes termoeléctricas, modificadores de bioma del molino y vetas minerales de la Excavator. Crusher, Arc Furnace y Sawmill ahora conservan sus salidas secundarias.
- **Botania** (1.21.1): Petal Apothecary, Runic Altar, Terrestrial Agglomeration y Mana Infusion exportan JSON nativo; nuevos Elven Trade, Pure Daisy, Orechid, Orechid Ignem y Marimorphosis.
- **Botany Pots**: cultivos y tierras derivados de bloques, interacciones de maceta.
- **Ars Nouveau**: conversión de Budding, rituales de Scrying, Summoning y Dispel, Alakarkinos, teñido y mejoras de armadura.
- **Apotheosis**: mejoras de herrería con tamaño y tallado de gemas.
- **Ad Astra**: recetas de estación espacial. **Deep Aether**: conversiones de Glowing Spores y Golden Swet Ball.
- **Tinkers' Construct** (1.20.1): fundición de entidades, cercenado, combustibles de fundición, mesa de moldeado, fundición de minerales, llenado de mesa/cuenca; la fundición conserva sus subproductos.
- **Blood Magic**: probabilidad de la salida principal del ARC.
- **ProjectE** (1.21.1): las transmutaciones en el mundo de la Philosopher's Stone se escriben en el archivo nativo de datapack `pe_world_transmutations`.

### Editor de tags
- Panel de miembros a la derecha, con buscador: reemplaza a la ventana "Miembros..." que había que volver a abrir cada vez. Editando un tag muestra sus miembros actuales; cada fila tiene casilla, icono y "x". "Todos" marca los visibles, "Quitar (N)" quita los marcados y "+ Id manual" quita uno por id. Editando un objeto muestra los tags donde está: un clic abre ese tag y la "x" lo quita.
- Se quitó el botón "+ Quitar" del centro: para quitar se usa el panel. En el centro quedan "Añadir" y la lista de lo que se va a quitar (o la lista de cambios, editando un objeto). Las listas usan todo el alto de la ventana, y la lista de tags guardados es más compacta y muestra más filas.
- Guardar, Restablecer y el resto de la fila de acciones ya no se salen del marco: si no entran en una línea pasan a dos, hacia arriba. Con la ventana baja el editor se achica en vez de pasarse del borde.
- Con poco ancho (escala de interfaz grande) el panel no se muestra y "Miembros..." vuelve a abrir la ventana aparte.
- Al pasar el mouse por un miembro el nombre se sigue leyendo (antes el resaltado era casi blanco), y la selección vuelve a ser la casilla ☑/☐ con el nombre en verde, como en la ventana de miembros.

### Items y mobs
- 1.21.1: al abrir un item vanilla (por ejemplo la espada de diamante) el editor de atributos no cargaba su daño, velocidad de ataque, armadura ni sus atributos extra, y los campos quedaban vacíos. Ahora se leen de los datos reales del item.
- Editor de mobs: "Nombre siempre visible" ahora funciona sin un nombre personalizado. Antes la exportación lo desactivaba sin avisar.

### Loot tables: pools y condiciones
- El editor está ordenado en secciones con título: **Tabla de loot** (tipo, evento, objetivo, categoría), **Pools** (el árbol con todos los pools, que ahora usa todo el ancho y el alto) y **Pool seleccionado** (tiradas, condición y probabilidad). El panel de la entrada se divide en Entrada, Funciones y Condiciones.
- Las entradas conservan sus propias condiciones (por ejemplo el Toque de seda de vanilla en stone, que decide si stone suelta stone o cobblestone). Antes se perdían al importar y la tabla guardada ya no era igual a la de vanilla.
- El panel de la entrada, a la derecha, tiene una sección Condiciones: cada condición en palabras simples con una "x" para quitarla, y "+" para agregar una (Toque de seda, sin Toque de seda, si sobrevive explosión, matado por un jugador, azar 50%). En la fila del árbol se sigue viendo un resumen.
- Las "alternatives" de vanilla (stone con Toque de seda, si no cobblestone) se importan bien: cada opción posterior solo sale cuando las anteriores no se cumplen. Antes, con Toque de seda el pool elegía stone o cobblestone al azar.
- Las condiciones de pool que el editor no modela (chequear la herramienta, una segunda condición...) se conservan y se muestran en la fila del pool. Antes se perdían al guardar. Una condición de entidad que no es "en llamas" ya no se convierte en "en llamas".

### Estructuras
- Generar con el ajuste de terreno "Enterrar" estando en el aire dejaba el esquema vacío arriba y la estructura en el suelo. Ahora el esquema y el fantasma se mueven con la estructura, y el aviso dice cuántos bloques bajó o subió.
- Vista fantasma: el esquema queda fijo en el bloque que mirabas al pulsar el botón y no sigue la mira del jugador. Para moverlo, vuelve a pulsar el botón o usa la tecla de anclar.
- Vista fantasma (1.21.1): el holograma quedaba pegado a la pantalla y nunca giraba con la cámara. Ahora se dibuja en el mundo, donde va a quedar la estructura.
- El panel derecho ya no esconde la mitad de los botones detrás de 4 pestañas (1.21.1): todos los grupos (Archivo, Editar, Mundo, Generación) se ven con su título y la columna se desplaza si no entra, igual que en 1.20.1.

### Generación de mundo
- **Editor rehecho**: a la izquierda Regla, Dónde se aplica y Vetas; abajo solo Guardar y "Más..." (Duplicar, Copiar a biomas, A la biblioteca, Vista previa, Deshacer, Restablecer y Eliminar). "Volver" pasa a la barra de título, junto al título (ya no lo tapa). Un clic en una veta la abre; en la fila quedan ▲▼ para reordenar, Duplicar y "x".
- **Vetas desde plantillas vanilla**: carbón, hierro, cobre, oro, redstone, lapislázuli, diamante, esmeralda, cuarzo, oro del Nether y escombros ancestrales, con valores parecidos a los de vanilla (un clic cambia la plantilla, "+" la agrega). En una regla de Nether o End se adapta la roca.
- **×½ / ×2**: cambia la abundancia de todas las vetas de la regla a la vez.
- **"Sin vanilla"** en cada veta de un mineral vanilla: quita la generación vanilla de ese mineral, para que no salga dos veces.
- Clic en una banda del corte vertical abre esa veta; al pasar el mouse dice qué veta es y su altura.
- El boceto del terreno ya no baja los FPS: el mapa se dibuja de una sola vez en vez de miles de rectángulos por frame.
- **Conflictos**: un botón "⚠ N conflicto(s)" en la lista de reglas avisa cuando dos reglas quitan la misma feature o una regla tiene vetas repetidas (marcadas con ⚠ en su fila). Al guardar también se avisa.
- **Boceto del terreno** en vez del visor 3D del chunk actual, que no mostraba el bioma elegido: un mapa topográfico simple (curvas de nivel, agua, nieve, roca) de cómo se vería más o menos el bioma, con un corte vertical debajo donde las vetas aparecen como bandas a su altura. Cambia con el preset de terreno (Plano, Amplificado, Mar alto...) y con "<" ">" se recorren los biomas elegidos. Es aproximado: no usa la semilla ni el generador real, y cuesta casi nada dibujarlo.
- Los presets de terreno se eligen en una grilla (otro clic lo quita); el destino del preset, "Ir" y su aviso aparecen solo con un preset elegido.
- **Regla por dimensión**: botones Overworld / Nether / End debajo de "Elegir biomas" convierten la regla en una de la dimensión entera. Las vetas que tenían la roca por defecto pasan a reemplazar la roca de esa dimensión (netherrack/basalto/blackstone en el Nether, end stone en el End).
- **Copiar a biomas**: crea una copia de la regla para otros biomas (como Duplicar, eligiendo los biomas nuevos).
- **Copiar a otro mundo o pack**: "A la biblioteca" guarda la regla en la biblioteca de plantillas. Al importarla en otro pack queda como cambio pendiente (Aplicar y volver a entrar al mundo). Si el id ya existe, la copia lleva un número.
- **Probar en mi chunk**: coloca las vetas y parches de la regla alrededor del jugador, en vivo, sin reiniciar el mundo. "Deshacer prueba" deja los bloques como estaban. Solo en un mundo local (un jugador o LAN abierta). Spawns, quitar features y terreno no se pueden probar así.
- **Presets de terreno, con nombres claros**: Plano, Normal (vanilla), Biomas grandes, Amplificado y Mar alto. Cada uno se aplica en una **dimensión de prueba** o en el **mundo principal** (Biomas grandes, Amplificado y Mar alto): cambia el terreno de todo el Overworld en los chunks nuevos al volver a entrar; lo ya explorado queda igual. Solo una regla puede cambiar el mundo principal.
- Elegir el mundo principal pide confirmación y explica qué pasa: afecta a todo el Overworld (no solo a los biomas de la regla) y deja cortes donde los chunks nuevos se juntan con los viejos. Mar alto avisa que inunda los bordes. Recomendado para mundos nuevos.
- El preset del mundo principal parte del terreno que ya tiene el pack (por ejemplo el de Terralith) y cambia solo el nivel del mar o la forma del terreno. Antes lo reemplazaba por el de vanilla y los biomas del mod perdían su superficie. Compatible con TerraBlender y Lithostitched, que siguen aplicando sus cambios encima.
- **Ir a la dimensión de prueba** lo hace el servidor: funciona sin trucos activados, sobre agua o en el vacío (pone una plataforma), avisa si la dimensión todavía no existe, y el mismo botón dice "Volver" para regresar a donde estabas. El terreno de destino se genera en segundo plano, sin congelar el juego.
- Mar alto antes escribía una etiqueta de biomas que nada usaba (no cambiaba nada). Ahora sube el nivel del mar a 80.
- **Extras de bioma** en cada regla: quitar placed features (un clic por mineral vanilla: carbón, hierro, cobre, oro, redstone, lapislázuli, diamante, esmeralda), agregar o quitar spawns naturales de mobs, y agregar parches de superficie de cualquier bloque (flores, hongos, piedras sueltas), con la misma forma que los parches de flores de vanilla.
- Las vetas de mineral pueden ser **raras**: "una veta cada N chunks" (`rarity_filter`), como las vetas grandes de diamante de vanilla.
- Al guardar se revisa que las features quitadas y las entidades de spawn existan; un id desconocido impediría cargar el mundo.

### Herramientas del pack
- **Probar el pack**: un solo reporte con todo lo que puede romper la carga: scripts generados con sintaxis rota, JSON de datapack inválido, recetas que no se pudieron exportar, errores de KubeJS de sus logs (señalando la entrada de Shovel JS que los causó), ids rotos y reglas de world gen inválidas. "Recargar y revisar" corre /reload y vuelve a revisar.
- **Revisión de balance**: ciclos de recetas que duplican items (también contra las recetas del propio juego, por ejemplo un nuevo "1 bloque -> 10 lingotes"), stats de mobs por encima de los topes que el juego aplica en silencio (vida 1024, daño 2048...), equipo muy por encima de netherite, recetas que dan más de un stack.
- **Comparar versiones**: elige dos instantáneas cualquiera (o una instantánea y el estado actual) y mira qué cambió, archivo por archivo y línea por línea.
- **Biblioteca de plantillas**: guarda las plantillas de mobs y comercios y las reglas de world gen de este pack en una biblioteca compartida por todos tus packs (`~/.shovel_js/library`) e impórtalas en otro. Los paquetes son archivos JSON simples que se pueden pasar.
- **Guía del pack**: una página en Markdown con todo lo que el pack agrega y cambia (items y bloques nuevos, recetas, cambios de items y mobs, loot, comercios, tags, world gen), lista para publicar.

### Las dos versiones tienen las mismas funciones
- 1.21.1 recibe de 1.20.1: el editor de minerales que el mundo ya genera (World gen > Minerales existentes), Reemplazar/Sumar (=/+) por atributo en el editor de items, avisos del editor de código que distinguen un error de tipeo (con el id correcto) de un mod que no está instalado, recetas que al importarse conservan los ingredientes-tag y las cantidades y reparan los tags rotos, iconos para recetas de máquina/ritual y para recetas quitadas, ofrendas de ritual ubicadas como en la vista JEI del propio mod (Goety), y el separador de grupo cuando la pestaña beta de Eventos está oculta.
- 1.20.1 recibe de 1.21.1: el selector de mobs para los spawns de biomas y selectores de solo bloques para minerales y features, arreglos del editor de mobs (las plantillas conservan Activo/Categoría, Descartar recarga el mob, coma decimal, favoritos primero), favoritos primero en items y bloques modificados, la pantalla Comparar versiones con restaurar un solo archivo, las notas de versión guardadas junto al juego, y la sección Beta en Ajustes.
- Eventos (beta) no aparece en el menú principal de ninguna versión hasta activarlo en Ajustes > Beta.

### Interfaz
- Botones y paneles con un bisel nuevo: esquinas levemente redondeadas, luz arriba y sombra abajo; un botón presionado se ve hundido. Las esquinas de las ventanas llevan marcas en "L".
- El selector de profesiones muestra a la derecha el aldeano con la profesión bajo el mouse (o la ya elegida), como el selector de mobs. Con los oficios de un mod muestra la entidad de ese mod.
- Se quitaron botones repetidos: "Cancelar"/"Cerrar" abajo en las ventanas que ya tienen la "X" del título (selectores de profesión, atributo, fluido, encantamiento, efecto y atributo de mob, combate, comida y atributos de curios del item, navegador de recetas, frecuencia de estructuras).
- **Editor de NBT** reordenado: la ventana se ajusta al tamaño de la pantalla y nada se solapa. Las pestañas aplican sus cambios al momento (se quitaron los botones "Aplicar" de cada pestaña y "Cancelar"); abajo quedan Guardar y Borrar todo. Los valores numéricos fuera de rango y el estado del fluido se avisan en la línea de estado mientras escribes.
- El tema Bronce tiene una paleta nueva de cobre y cuero. Los temas que se derivan del color de acento calculan sus tonos de otra forma, así que pueden verse un poco distintos.

### Arreglos
- Los items de mods rotos ya no tumban nuestras pantallas: el selector de items, el navegador de recetas, los editores de recetas y el editor de código muestran como "?" un item cuyo mod falla al dibujarse, y el log nombra ese mod una sola vez.
- Los scripts de recetas, tags, items, bloques y mobs se escriben de forma atómica con un `.bak` y una revisión de sintaxis, como ya pasaba con los de eventos. Un crash durante Aplicar ya no puede dejar un script a medio escribir que rompa todas las recetas en `/reload`.
- Atributos de items: una revisión de archivo corría en casi cada consulta de atributos (tooltips, equipo); ahora como mucho una vez por segundo. Arreglado un posible crash cuando el cliente y el servidor integrado leían a la vez los datos de un item personalizado.
- Los botones ya no se salen de su marco cuando la ventana es angosta (por ejemplo la fila de acciones del editor de loot). Un botón que no entra achica su texto.
- Las barras de desplazamiento solo aparecen cuando hay algo que desplazar. Una vacía ya no dibuja un carril vacío ni bloquea los clics de abajo.
- Tab pasa entre campos y los atajos con Ctrl llegan al shell en los editores de vetas, entradas de loot, creador de items/bloques y NBT (Ctrl+S guarda).
- Editor de NBT: los títulos de las listas de encantamientos y de campos numéricos ya no quedan debajo de los botones "+ Añadir".
- Los mensajes del mod en el log y los `console.error`/`console.warn` de los scripts generados empiezan con `[Shovel JS]`.

### Dependencias
- NeoForge 21.1.264, KubeJS 2101.7.2-build.379, Architectury 13.0.11, Jade 15.10.6, JEI 19.69.0.457 (1.21.1).
- Forge 47.4.28, Jade 11.13.3, JEI 15.74.0.228 (1.20.1).

## 1.0.0 — Primera versión pública

Shovel JS es un editor visual dentro del juego para quienes arman modpacks. Escribe los scripts de KubeJS y los archivos de datapack por ti, con una cola de cambios pendientes, diffs y deshacer. Disponible para **Forge 1.20.1** y **NeoForge 1.21.1**.

### Dos jars
- **Shovel API** (`shovel_api`): lo que el juego necesita para aplicar tus cambios. Déjalo en tu modpack.
- **Shovel JS** (`shovel_js`): el editor en sí. Requiere Shovel API. Lo puedes quitar cuando el pack esté terminado: todo lo que hiciste sigue funcionando.

### Editores
- **Recetas**: crafteo vanilla y máquinas de mods (Create, Mekanism, Ars Nouveau, Goety, Iron's Spells, Farmer's Delight y más), guardadas en el JSON nativo de cada mod cuando se puede. Abre cualquier receta desde JEI/EMI con el botón de la pala.
- **Items y bloques**: atributos, tooltips, comida, efectos de combate, reglas de minado y atributos de Curios al equiparlo.
- **Mobs**: stats, equipo, efectos, aliados y enemigos, comportamientos (actitud, IA, inmunidades) y ajustes de combate.
- **Loot tables, tags, comercios de aldeanos** (con plantillas de ofertas reutilizables), **estructuras** y **generación de mundo**. Las listas de comercios y loot muestran una vista chica del mob, bloque o tipo de tabla de cada entrada.
- Los **tags** se editan de dos formas: los miembros de un tag, o un item/bloque/fluido/entidad a la vez (ves todos los tags en los que está y lo agregas o quitas de varios a la vez).
- La validación del **editor de código** pasa tu script por el propio parser de KubeJS y además marca nombres que nunca se definen (errores como `evnt.remove(...)`) antes de que sean un `ReferenceError` al recargar.
- Editor de **Eventos** disponible como opción beta en Ajustes.

### Comodidad
- Navegación con teclado en listas y selectores, Enter para confirmar diálogos, avisos de cambios sin guardar.
- Los tags rotos del pack (por ejemplo tras un mod de port mal hecho) se detectan y se explican; las recetas que los usaban se reparan guardándolas de nuevo.
- Ahorro de recursos: con el editor abierto el mundo de fondo no se dibuja y el editor va a 60 FPS como mucho (se desactiva en Ajustes).
- Traducciones en inglés y español; la escala de texto de la interfaz también se aplica a los tooltips.

### Para quienes escriben scripts
- Binding de KubeJS **`ShovelAPI`** (el nombre viejo `KveAPI` sigue funcionando).

### Requisitos
- KubeJS es necesario para que corran los scripts generados.
- Opcionales: JEI o EMI, Jade, Curios (y ExtendedCrafting en 1.20.1).
