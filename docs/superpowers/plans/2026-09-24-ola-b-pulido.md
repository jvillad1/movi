# Ola B — lo que se vio en el teléfono

Probado en el Pixel del dueño el 24-sep con el APK 1.48 (arranque en frío, cuadros cada ~0,4 s). El
dueño pidió que ninguna sección salte al cargar, *«si el skeleton puede predecir o precalcular el
tamaño de las partes»*. Repo: `/private/tmp/movi-pulido` (rama `ola-b-pulido`, base `origin/master`
55f01238). Leer `CLAUDE.md`.

## Global Constraints

- Pruebas: `JAVA_HOME=/usr/local/share/jbrsdk-21/Contents/Home ./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs`
  es CI; mirar la línea `BUILD SUCCESSFUL`/`BUILD FAILED` real. Nunca `./gradlew build`. Un Gradle a la
  vez. Medir alturas/posiciones con `@GraphicsMode(GraphicsMode.Mode.NATIVE)` y `sdk = [34]`; el
  `testTag` va antes de `clip`/`clickable`/`padding` cuando se comparan posiciones. La app multiplica el
  `fontScale` por 1,12 (`App.kt`): medir con esa densidad cuando importe el alto del texto.
- Texto visible en español neutro con tuteo (`VoseoScanTest`). Colores/espacios/formas desde `Movi.*`.
- Todo `object` o store nuevo con datos del usuario se limpia en `SessionManager.clear()` y va a
  `ElForkLlegaLimpioTest`. `Settings()` se construye `by lazy` y todo acceso va en `runCatching` (ver
  `TemaStore`, `InstantaneaDelInicio`); `catch (e: Throwable)` relanzando `CancellationException`
  (`ExcepcionesDeRedScanTest`).
- Commits chicos en español, `git add` con rutas, terminando con
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

### Task 1: Movi recuerda la forma de la última carga y el esqueleto la copia

Medido en el teléfono:
- **Créditos**: el esqueleto de la tarjeta de resumen es más bajo que la real, porque la del dueño trae
  además **el aviso ámbar** («2 créditos no se terminan a este ritmo…») **y el rojo** («En 1 crédito la
  cuota… no alcanza para los intereses…»). Al cargar, la lista de préstamos baja ~130 dp.
- **Categorías**: la tarjeta «Movi encontró 12 cosas para ordenar» aparece después de cargar y empuja la
  lista ~50 dp.
- **Cuentas**: el esqueleto tiene un grupo de 4 filas; la pantalla real tiene grupos («DINERO · 5»,
  «INVERSIÓN · 2», …) con otra cantidad de filas.

Hacer:
- Un store chico por usuario en el aparato, p. ej. `FormaRecordada` (`shared/.../data/`), con `Settings`
  (lazy + runCatching), clave con el `SessionManager.userId`: guarda **números** (no datos): por pantalla,
  los que decidan su forma — Créditos: cuántos avisos hay en la tarjeta de resumen (y de qué tipo, si el
  alto difiere) y cuántas tarjetas de préstamo; Categorías: si había tarjeta de «ordenar» y cuántas filas;
  Cuentas: la cantidad de filas de cada grupo, en orden. Se escribe cuando la lectura sale bien; se lee al
  montar la pantalla. Sin nada recordado (primera vez), el esqueleto de hoy.
- Los esqueletos de esas tres pantallas usan lo recordado: Créditos reserva los avisos (bloques del alto
  de cada aviso) y N tarjetas; Categorías reserva la tarjeta de «ordenar» si la había; Cuentas dibuja
  los grupos con sus cantidades.
- Se limpia al cerrar sesión (`SessionManager.clear()` + `ElForkLlegaLimpioTest`).
- Pruebas: el store (ida y vuelta, otro usuario no lo ve, corrupto → nada); y para cada pantalla, con
  forma recordada y el repositorio detenido, el alto del bloque de arriba (resumen + avisos en Créditos,
  tarjeta de ordenar + primera fila en Categorías, primer grupo en Cuentas) cargando vs cargado dentro de
  ±8 dp con datos iguales a lo recordado.

### Task 2: Cuatro detalles que se vieron en el teléfono

- **Movimientos**: la línea del período bajo el mes («Del 25 de agosto al 24 de septiembre · queda 1
  día» o como se llame hoy) aparece recién cuando llegan los datos y empuja toda la lista. Reservarla
  desde el primer cuadro (esqueleto de una línea del mismo estilo, o el texto si ya se puede calcular sin
  la lectura). Prueba NATIVE: el primer grupo del esqueleto y el primer día real empiezan en el mismo Y
  (±2 dp) contando esa línea.
- **Cuentas**: las filas reales son más altas que las del esqueleto (`FilaDeListaEsqueleto(conIcono =
  true)`); igualar el alto de la fila esqueleto al de la fila real de Cuentas, sacándolo de las mismas
  constantes/estilos. Prueba NATIVE ±2 dp de la fila.
- **El aviso de unificar** (`avisoDeUnificacion` en `ui/categorias/CategoriasLogic.kt` o donde viva)
  dice «1 movimiento de «Crédito» **pasan** a decir…»: concordancia singular/plural en ese texto y en
  cualquier otro de la misma función («1 presupuesto», «1 recurrente»). Pruebas con 1 y con varios.
- **La cuadrícula de categorías** (`SelectorDeCategoria.kt`): «Entretenimiento» se parte a mitad de
  palabra («Entretenimient / o»). Una palabra nunca se corta: si la palabra más larga no entra en el ancho
  de la celda, el texto se achica (`autoSize` de `BasicText` si está disponible en la versión de Compose del
  repo, con un mínimo legible) o, si no, se muestra en una línea con «…». Prueba NATIVE con
  «Entretenimiento» a 390 dp: el texto no tiene un corte dentro de la palabra (p. ej. `lineCount` con
  `softWrap` y comparando el ancho de la palabra contra el de la celda).
- **El título de Categorías** dice «35 categorías» contando las del sistema, que ya no se listan: que
  cuente las que la pantalla muestra (sin reservadas). Prueba.
