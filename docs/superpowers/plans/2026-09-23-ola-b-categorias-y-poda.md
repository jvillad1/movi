# Ola B de «Movi sin grasa» — categorías de verdad y poda

Origen: revisión de estructura del 23-sep-2026 (https://claude.ai/artifact/88BSTvLsPp8SjJBUjZkFpn),
propuestas 2 y 7. Decisiones del dueño: **Metas sale**; **categorías planas** (varias categorías si
hace falta, sin subcategorías). Evidencia en prod: 34 categorías en pantalla, 25 usadas, 6 de un solo
uso (Tecnología $15, Estadio, Familia, Crédito, Transporte, Mercado), duplicadas (Crédito / Cuota de
crédito), las del sistema (Saldo inicial, Ajuste de saldo, Traspaso) mezcladas con las suyas;
renombrar/unificar/esconder existen pero nadie los usó (category_prefs vacío); el catálogo trae
ícono y color y la app no los muestra; el selector de «Agregar» es una lista de texto que abre el
teclado; 0 metas; 0 extractos importados.

Repo: `/private/tmp/movi-ola-b` (worktree, rama `ola-b-categorias`, base `origin/master` 4ef70b04).
Leer `CLAUDE.md` del repo antes de empezar.

## Global Constraints

- **Pruebas**: `JAVA_HOME=/usr/local/share/jbrsdk-21/Contents/Home ./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs`
  es lo que corre CI. Cada tarea corre al menos las suites que toca y **mira el `BUILD SUCCESSFUL` /
  `BUILD FAILED` real**. **Nunca `./gradlew build`.** 16 GB: un Gradle a la vez. `timeout` no existe.
  Para medir alturas/anchos reales en Robolectric: `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (en LEGACY
  todo texto mide ~17,5 dp).
- **Todo texto visible en español neutro latinoamericano, tuteo, sin voseo** (`VoseoScanTest`).
- **Colores, espacios, formas y estilos solo desde `Movi.*`** (`shared/.../theme/Tokens.kt`). Todo color
  nuevo va a `Tokens.kt` en los dos temas (oscuro y claro) y lo mide `ContrasteDeLosTokensTest`.
- **Nada de valores nuevos en un `enum` serializado.** Campos nuevos en modelos `@Serializable` de
  `:core` siempre con valor por defecto (un APK viejo tiene que seguir leyendo al server nuevo y al revés).
  Las columnas nuevas del server se agregan con el mecanismo de `DatabaseFactory`
  (`createMissingTablesAndColumns`), nullable.
- **Íconos: solo Material Icons** (`compose.materialIconsExtended` ya está en `:shared`). **Nada de
  emoji en la UI**: en wasm no hay fuente de emoji y salen cuadrados.
- Todo `object` mutable nuevo con datos del usuario se limpia en `SessionManager.clear()` y se agrega a
  `shared/src/androidUnitTest/.../aislamiento/ElForkLlegaLimpioTest.kt`.
- Toda lectura del server que sume o cuente movimientos excluye los anulados (`VoidEvents`).
- Categorías del sistema = `isReservedCategory(...)` de `:core` (Saldo inicial, Ajuste de saldo,
  Traspaso, pata huérfana, Pago de tarjeta, etc.). No se ofrecen para elegir ni se listan como del dueño.
- `catch (e: Throwable)` (y relanzar `CancellationException`) alrededor de red/almacenamiento: lo exige
  `ExcepcionesDeRedScanTest`.
- Commits chicos, en español, con rutas explícitas en `git add`, y terminando con
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Comentarios KDoc al estilo del archivo que se toca: dicen el porqué.

---

### Task 1: El ícono y el color de una categoría viajan y se guardan

`:core` + `:server`. Sin UI.

- `CategoryPref` (`core/.../model/CategoryAdmin.kt`): agregar `val icono: String? = null` y
  `val color: String? = null` (claves de texto, p. ej. `"restaurante"`, `"naranja"`; `null` = el que
  Movi le asigna por defecto). Lo mismo en `CategoryPrefsRequest` (con default `null`) y en
  `UsedCategory` (`DashboardSummary.kt`), que es por donde el cliente recibe las preferencias.
- Server: columnas nullable `icono` y `color` en `CategoryPrefs` (`Tables.kt`), creadas por el mecanismo
  de columnas faltantes. `PUT /api/categories/prefs` las guarda. **Semántica del PUT**: hoy manda el
  estado completo (`hidden`, `pinnedType`); los campos nuevos, si vienen `null`, **no borran** lo que había
  (un APK viejo que manda solo `hidden`/`pinnedType` no puede borrarle el ícono al dueño). Para volver al
  ícono por defecto, el cliente manda la cadena vacía `""` (y el server guarda `null`). Documentarlo.
- `usedCategories(uid)` (DashboardRoutes) y `GET /api/categories` devuelven `icono`/`color`.
- Renombrar y unificar (`rewriteCategory` en `CategoryRoutes.kt`) conservan ícono y color con la misma
  regla con que ya conservan `pinnedType`: al renombrar viajan con el nombre; al unificar, gana lo del
  destino y, si el destino no tiene, se hereda del origen.
- Pruebas server: guardar y leer; PUT sin los campos nuevos no borra; `""` vuelve a `null`; renombrar y
  unificar conservan; aparecen en el resumen del Inicio.
- Cliente de repositorio: si `putCategoryPrefs` (o como se llame hoy) arma el request, que acepte los
  campos nuevos con default `null`. Actualizar `UsedCategoriesCache` para guardar `icono`/`color` en sus
  prefs (`CategoryPref`), incluido lo que persiste en `Settings`.

### Task 2: La apariencia de una categoría (ícono + color), en un solo lugar

`:shared` (y tokens). Sin pantallas todavía.

- **Paleta**: en `Tokens.kt`, 10 colores de categoría con nombre en español, para los dos temas
  (`naranja`, `rojo`, `rosa`, `violeta`, `azul`, `celeste`, `verde`, `lima`, `ambar`, `gris`), pensados
  para dibujar un ícono sobre un círculo de ese mismo color con alfa bajo, encima de `tarjeta`. Agregar
  al `ContrasteDeLosTokensTest` la medición de cada color de ícono contra `tarjeta` y contra el círculo
  (≥ 3:1, el mínimo para gráficos no textuales de WCAG), en los dos temas. Exponerlos como
  `Movi.colores.categoria(clave)` o equivalente, con `gris` como respaldo para una clave desconocida.
- **Íconos**: `shared/.../ui/categorias/IconosDeCategoria.kt` con un catálogo cerrado de ~40 claves →
  `ImageVector` (Material Icons Rounded), elegidas para la vida de una familia colombiana: comida,
  mercado, café, restaurante, transporte, carro, gasolina, taxi/bus, moto, casa, arriendo, servicios,
  luz, agua, internet, celular, salud, medicamentos, gimnasio, fútbol/deporte, estadio/evento, hija/niños,
  familia, mascota, educación, libros, ropa, tecnología, entretenimiento, música, viajes, regalos,
  donación, impuestos, comisiones/banco, crédito/cuota, tarjeta, ahorro, inversiones, salario/trabajo,
  ingreso/transferencia, suscripciones, belleza, arreglos del hogar, otros. Clave desconocida → ícono de
  «otros». Cada clave con un rótulo en español para mostrar en el selector.
- **La regla**: `aparienciaDe(nombre: String, pref: CategoryPref?): AparienciaDeCategoria` (pura, sin
  Compose salvo el `ImageVector`), con prioridad: (1) lo que eligió el dueño (`pref.icono`/`pref.color`,
  cada uno por su lado); (2) una tabla por **nombre normalizado** (sin tildes ni mayúsculas, con
  `normalizarParaBuscar`) para el catálogo y los nombres reales del dueño: Comida→comida/naranja,
  Mercado / Mercado extra→mercado/lima, Fútbol→fútbol/verde, Estadio→estadio/verde, Gimnasio→gimnasio/
  celeste, Hija→hija/rosa, Familia→familia/rosa, Celular→celular/azul, Cuota de crédito / Crédito→crédito/
  violeta, Pago de tarjeta→tarjeta/violeta, Comisiones del banco→banco/gris, Impuestos→impuestos/gris,
  Salud→salud/rojo, Transporte→transporte/azul, Tecnología→tecnología/celeste, Entretenimiento→
  entretenimiento/ambar, Servicios→servicios/ambar, Vivienda→casa/ambar, Gardenera→casa/ambar,
  Educación→educación/azul, Ropa→ropa/rosa, Otros→otros/gris, Salario / Nómina→salario/verde,
  Freelance→trabajo/verde, Arriendo recibido→arriendo/verde, Inversiones→inversiones/verde, Otros
  ingresos / Ingreso / Transferencia / Pago de un tercero→ingreso/verde; (3) palabras clave dentro del
  nombre (p. ej. contiene «restaurante»/«almuerzo» → comida; «uber»/«taxi» → transporte;
  «netflix»/«spotify»/«suscrip» → suscripciones; «médic»/«farmacia» → salud); (4) respaldo: ícono
  «otros» y un color **estable** elegido por un hash del nombre normalizado sobre la paleta (la misma
  categoría siempre del mismo color, en todos los aparatos).
- **Composable** `IconoDeCategoria(nombre, tamaño = normal|chico)`: círculo del color con alfa bajo y
  el ícono del color encima; lee las prefs de `UsedCategoriesCache`. Con `contentDescription = null`
  (el nombre siempre está al lado).
- Pruebas: la prioridad (pref > tabla > palabras > hash), normalización (tildes/mayúsculas), clave
  desconocida → respaldo, el hash es estable y cae dentro de la paleta, todas las claves del catálogo
  tienen rótulo, y el contraste en `ContrasteDeLosTokensTest`.

### Task 3: El ícono se ve en toda la app

`:shared`. Depende de la Task 2.

- **Movimientos** (`TransactionsScreen.kt`, `MovementSingleRow`): `IconoDeCategoria` a la izquierda de
  cada fila (los traspasos y los ajustes usan el ícono que ya tengan o uno neutro de la paleta — no el de
  una categoría). La fila no puede crecer de alto por esto: el ícono entra en el alto que ya tiene.
- **Inicio, «en qué se va»** (`GastoPorCategoriaSection` en `SeccionesDelPeriodo.kt`): ícono chico junto
  al nombre y la barra del color de la categoría (en vez del color único de hoy), si no rompe la lectura
  en el tema claro (mirarlo; si el color de la categoría no se distingue del fondo de la pista, usar el
  del ícono con alfa).
- **Presupuestos** (`PresupuestosScreen.kt`): ícono en cada fila de presupuesto.
- **Detalle de un movimiento / hoja de recategorizar** (`CategorySheets.kt`): el ícono junto a la
  categoría actual.
- Pruebas Robolectric de que el ícono aparece (por `testTag`) en una fila de Movimientos, una barra del
  Inicio y una fila de Presupuestos, y que la fila de Movimientos no cambió de alto (NATIVE, ±1 dp).

### Task 4: El selector de categoría es una cuadrícula

`:shared`. Depende de la Task 2. Afecta a `CategoryField` (usado por «Agregar», Presupuestos,
recurrentes y la hoja de recategorizar).

- Donde hoy se abre la lista de sugerencias de texto (el sub-picker de `CategoryField` y la rama
  `Picker.Category` de `QuickAddScreen`): una **cuadrícula** de celdas (ícono arriba, nombre abajo, dos
  líneas máx.), 4 columnas en teléfono (ajustar con `GridCells.Adaptive` a ~80 dp).
- Orden: primero hasta **8 frecuentes** del tipo (reusar `categoriasFrecuentes` de la ola A), después el
  resto **alfabético** (`categorySortKey`), sin repetir; solo las que `seOfreceParaTipo` acepta, sin
  escondidas ni reservadas. La elegida se marca.
- **Buscar sin teclado de entrada**: arriba, un campo «Buscar o crear categoría» que **no toma el foco al
  abrir** (el teclado sale solo si lo tocas). Al escribir, la cuadrícula se filtra (misma normalización
  que hoy) y, si no hay coincidencia exacta, aparece la celda «Crear "<texto>"» (lo que hoy hace escribir
  una categoría nueva). Las reglas de hoy se mantienen: nombre reservado → el aviso de hoy.
- Tocar una celda elige y cierra (como hoy elegir una sugerencia). En «Agregar», cuenta como elección a
  mano (`categoriaElegidaAMano`), igual que hoy.
- Pruebas Robolectric: al abrir no hay foco en el campo; las frecuentes van primero; escribir filtra y
  ofrece crear; tocar una celda elige; una reservada no aparece. Actualizar las pruebas existentes de
  `CategoryField`/«Agregar» que dependían de la lista vieja.

### Task 5: La pantalla de Categorías, compacta y editable ahí mismo

`:shared` (`ui/categorias/`). Depende de las Tasks 1-2.

- **Fuera las del sistema**: las reservadas (`isReservedCategory`) no se listan (hoy van al final, sin
  poder tocarse). Si hace falta explicarlo, una línea al pie: «Movi también usa categorías propias para
  traspasos, saldos iniciales y ajustes; no se editan.»
- **Filas compactas** (~56-64 dp): `IconoDeCategoria`, el nombre, y a la derecha lo del período actual
  (`resumenDelMes` o su cifra) o nada; debajo, en `apoyo`, el uso total corto («12 movimientos») o «Sin
  movimientos». Fuera las etiquetas «Tuya» y «Ambos»: el tipo se dice solo cuando ayuda, con palabras
  («Gasto», «Ingreso», «Gasto e ingreso») y en el detalle, no en la fila.
- **La hoja de detalle** (la que ya existe al tocar una fila) suma **Ícono** (cuadrícula del catálogo de
  la Task 2, con su rótulo) y **Color** (los 10 círculos de la paleta), con vista previa arriba, y
  «Volver al de Movi» (manda `""`). Guarda con el PUT de prefs (Task 1) y actualiza
  `UsedCategoriesCache` para que el cambio se vea en toda la app sin recargar. Renombrar, unificar,
  esconder y fijar el tipo siguen ahí, con sus textos.
- Pruebas: reservadas fuera; fila sin «Tuya»/«Ambos»; elegir ícono y color llama al repositorio con las
  claves y la fila se repinta; «Volver al de Movi» manda `""`.

### Task 6: «Ordena tus categorías»

`:shared` (lógica pura + UI en la pantalla de Categorías). Depende de las Tasks 1 y 5.

- Función pura `propuestasDeOrden(categorias: List<CategoryUsage>): List<PropuestaDeOrden>`:
  1. **Unificar parecidas**: dos categorías no reservadas donde el nombre normalizado de una está
     **contenido como palabra(s) completa(s)** en el de la otra y comparten tipo efectivo (p. ej.
     «Crédito» ⊂ «Cuota de crédito»). Propone unificar **la de menos movimientos en la de más**. No
     propone si las dos tienen ≥ 5 movimientos (probablemente son distintas a propósito, como «Mercado»
     y «Mercado extra» si las dos se usan).
  2. **Esconder las que nunca usaste**: del catálogo, 0 movimientos, sin presupuesto ni recurrente, no
     escondida.
  3. **Las de un solo uso**: propias (no del catálogo), 1 movimiento en total, sin presupuesto ni
     recurrente → proponer «unificar con…» (el dueño elige el destino en la cuadrícula) o «dejarla».
  Orden: 1, 3, 2. Máximo 12 propuestas.
- **UI**: si hay propuestas, una tarjeta arriba de la lista: «Movi encontró N cosas para ordenar» con
  «Revisar». Abre una hoja que las muestra **una por una**, cada una con su explicación en una línea
  («"Crédito" tiene 1 movimiento; "Cuota de crédito" tiene 10») y los botones de la acción («Unificar»,
  «Esconder», «Unificar con…») y «Ahora no». Cada acción usa los endpoints existentes (merge / prefs
  hidden) y refresca la lista. Nada se hace sin que el dueño toque el botón.
- «Ahora no» se recuerda **en el aparato** por propuesta (clave estable: tipo + nombres), con
  `Settings` como `TemaStore` (lazy + runCatching), para no volver a mostrarla; se limpia al cerrar
  sesión (`SessionManager.clear()` + `ElForkLlegaLimpioTest`).
- Pruebas puras con los datos reales de arriba (Crédito→Cuota de crédito; Mercado de 1 movimiento vs
  Mercado extra de 7 → propone unificar Mercado en Mercado extra; «Arriendo recibido» sin uso →
  esconder; «Tecnología» del catálogo con 1 movimiento no entra en la regla 3 (es del catálogo) pero
  tampoco en la 2; reservadas nunca), y Robolectric de la tarjeta y de una acción.

### Task 7: Menos pantallas: Metas y Extractos salen, Primeros pasos solo si falta algo

`:shared` + `:server` (una ruta). Independiente de las anteriores.

- **Metas sale de la navegación**: fuera del mosaico de «Más» (`MasScreen.kt`), fuera de la sección
  «Meta principal» de Perfil (`PerfilScreen.kt`), el destino SDUI `"goals"` se remapea a
  `Screen.Accounts` (como ya se hizo con `"investments"`; comentar por qué) y el Inicio deja de pedir
  `getGoals()` si nada visible lo usa (revisar `DashboardLogic.kt` `"goals"` en los accesos: si un acceso
  «Metas» existe en alguna definición guardada, que no se pinte). El editor de pantallas deja de ofrecer
  «Metas». **No se borra** `MetasScreen` ni la API (la ruta del server sigue; solo sale de la vista).
- **Extractos se une a Documentos**:
  - Server: `POST /api/documents/{id}/leer-extracto` (autenticada, `uid` del token, 404 si el documento
    no es del usuario): lee los bytes guardados del documento y corre **el mismo** proceso que
    `POST /api/statements/upload` (extraer ese cuerpo a una función compartida que reciba
    `fileName`, `mimeType`, `bytes`; sin duplicar la lógica), devolviendo el mismo `StatementParseResult`.
    Pruebas del server (dueño vs otro usuario, documento inexistente, reusa el camino del upload).
  - Cliente: método de repositorio nuevo; en `DocumentosScreen`, para documentos PDF o imagen, una acción
    más en la fila: «Importar movimientos» → llama y navega a `Screen.StatementReview(json)` como hoy lo
    hace Extractos (mismo manejo de errores: el motivo del server se muestra).
  - Documentos suma, si hay importaciones (`getStatementImports()` no vacío), una sección «Importaciones»
    con las filas que hoy muestra Extractos, que abren `Screen.ImportDetail`.
  - «Extractos» sale de «Más»; `ImportDetailScreen` y `StatementReviewScreen` vuelven a
    `Screen.Documentos` en su «atrás» por defecto; el destino SDUI `"extractos"` → `Screen.Documentos`; el
    enlace «Extractos» del pie de Primeros pasos del Inicio → «Documentos».
- **Primeros pasos**: el mosaico de «Más» solo aparece mientras la guía no esté completa (la misma
  condición con que el Inicio decide mostrar o no la guía; reusarla, no copiarla).
- **Editor de pantallas**: ya es solo para administradores (`isAdmin` en Perfil); verificar que no
  quede ninguna otra entrada visible para quien no lo es. Sin cambios si ya es así.
- Revisar que la barra lateral de la web y la barra del teléfono no tengan entradas a Metas/Extractos.
- Pruebas: «Más» sin Metas ni Extractos; Primeros pasos aparece con la guía incompleta y no con la guía
  completa; la acción «Importar movimientos» en un PDF navega a la revisión con el resultado del
  repositorio falso; los remapeos SDUI.
