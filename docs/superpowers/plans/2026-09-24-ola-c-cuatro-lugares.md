# Ola C de «Movi sin grasa» — cuatro lugares

Origen: revisión de estructura del 23-sep (https://claude.ai/artifact/88BSTvLsPp8SjJBUjZkFpn),
propuestas 4, 5 y 6. El dueño aprobó la ola C con los nombres **Hoy · Movimientos · Plan ·
Patrimonio**. Hoy la app tiene la barra del teléfono «Inicio · Movs · + · Cuentas · Más», la barra de
la web «Inicio · Movimientos · Cuentas · Créditos · Presupuestos · Más», un «Más» con 11 fichas, lo que
falta pagar en cuatro lugares (Inicio, checklist, Próximos, Ya ocurrieron — estos tres dentro del chip
«Recurrentes» de Movimientos) y dos bandejas (el aviso «N movimientos entraron solos y faltan
confirmar» en Movimientos, y «Mensajes del banco» en Más, que además mezcla la configuración de la
captura con los mensajes).

Repo: `/private/tmp/movi-ola-c` (rama `ola-c-cuatro-lugares`, base `origin/master` 149d266a). Leer
`CLAUDE.md` antes de empezar.

## La estructura que se quiere

| Pestaña | Qué contesta | Qué junta |
|---|---|---|
| **Hoy** (el Inicio de hoy, renombrado) | ¿Cómo estoy? ¿Qué viene? | lo que ya muestra; su «Ver todos» del checklist lleva a Plan |
| **Movimientos** | ¿Qué pasó? | la lista (Todo / Gastos / Ingresos) y **arriba una sola bandeja «Por revisar»** |
| **Plan** | ¿Cuánto puedo gastar y qué me falta pagar? | el Disponible del período, **Pagos del mes** (el tablero de Recurrentes: checklist, próximos, ya ocurrieron, flujo libre, suscripciones, detectadas) y **Presupuestos** |
| **Patrimonio** | ¿Cuánto tengo y cuánto debo? | Cuentas (patrimonio neto + grupos), **Deudas** (resumen que abre Créditos), **Te deben** (Cuentas de otros), y «Cuadrar saldos» como acción |

«Más» deja de ser pestaña: **tocar el avatar** (arriba a la izquierda, hoy lleva a Perfil) abre
**«Ajustes»** (la pantalla de Más, más corta): Perfil, Categorías, Documentos, Compartir, Movi AI,
«Captura del banco» (permisos + historial de mensajes), Primeros pasos (solo si falta algo) y el editor
(solo admin). Teléfono y web muestran **las mismas cuatro pestañas** (+ el botón de agregar).

## Global Constraints

- Pruebas: `JAVA_HOME=/usr/local/share/jbrsdk-21/Contents/Home ./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs`
  es CI; mirar el `BUILD SUCCESSFUL`/`BUILD FAILED` real. Nunca `./gradlew build`. Un Gradle a la vez.
  Nunca correr pruebas desde `/Users/carolinarestrepo/Developer/movi` (su `server/.env` tiene llave).
- Texto visible en español neutro con tuteo (`VoseoScanTest`). Colores/espacios/formas desde `Movi.*`.
- **Nada de valores nuevos en enums serializados**. `NavTab` y `Screen` viven solo en la UI (no viajan):
  se pueden cambiar. Los **destinos SDUI** (`NAVIGATE_TARGETS` en `core/.../ScreenDefinition.kt`) **no se
  borran**: definiciones guardadas y APKs viejos los usan; se **remapean** en `screenForTarget`
  (`ui/sdui/SduiRenderer.kt`), como ya se hizo con `"investments"`.
- **Los índices de chip de Movimientos no se renumeran** (`CHIP_*` viajan en `Screen.Transactions` y en
  pilas restauradas): un chip que se va se remapea, no se reusa su número.
- Cada pantalla nueva sigue la regla de las olas A/B: **no afirma vacío ni cifras antes de una lectura
  exitosa**, esqueleto con la forma real mientras carga (componentes de `ui/components/Esqueleto.kt`,
  `FormaRecordada` si la forma depende de los datos), y las acciones del encabezado desde el primer cuadro.
- Todo `object`/store nuevo con datos del usuario → `SessionManager.clear()` + `ElForkLlegaLimpioTest`.
  `catch (e: Throwable)` relanzando `CancellationException`.
- Nada de lógica copiada: si dos pantallas muestran lo mismo, se extrae una vez y se usa en las dos.
- Commits chicos en español, `git add` con rutas, terminando con
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. KDocs al estilo del archivo: el porqué.

---

### Task 1: El tablero de Recurrentes sale de Movimientos a su propio composable

Refactor **sin cambio de comportamiento**. Hoy el tablero vive dentro de `TransactionsScreen.kt` (3.221
líneas): los `LaunchedEffect` que cargan reglas/suscripciones/sellos/candidatas cuando
`activeFilter == CHIP_RECURRENTES` (~l.1172-1300) y la UI con el chip activo (~l.1874-2100:
`SeccionChecklistDelPeriodo`, `SeccionProximosPagos`, `SeccionYaOcurrieron`, `ResumenFlujoLibreCard`,
`SeccionSuscripcionesActivas`, candidatas «Detectadas · por confirmar»), más su vacío y su barra de carga.

- Extraerlo a `shared/.../ui/plan/TableroDeRecurrentes.kt`: un composable que **carga lo suyo** (mismas
  lecturas, mismas reglas de «no afirmar vacío», mismo manejo de error/reintento) y pinta el tablero, con
  los parámetros que necesite del período (el visible) y la navegación. Las funciones puras que usa se
  mueven con él si solo él las usa; si Movimientos también las usa, quedan compartidas (no copiadas).
- `TransactionsScreen` con el chip «Recurrentes» activo usa el composable extraído (así nada cambia para
  el dueño todavía; la Task 3 saca el chip).
- Las pruebas existentes del tablero (buscar en `androidUnitTest/.../ui/transactions/` y `.../recurrentes/`
  las que abren Movimientos con `CHIP_RECURRENTES`) siguen pasando sin cambiar lo que afirman; se agrega
  una que monta `TableroDeRecurrentes` solo.

### Task 2: La pantalla Plan

`shared/.../ui/plan/PlanScreen.kt` + `Screen.Plan(segmento: Int = SEGMENTO_PAGOS)` (en `Navigation.kt`).

- **Encabezado**: «Plan» y debajo el rango del período con los días que quedan (la misma función que ya
  arma ese texto en Movimientos/Inicio; reservado desde el primer cuadro como en Movimientos).
- **«Cuánto puedes gastar»**: la tarjeta del Disponible del período que hoy pinta el Inicio
  (`DisponibleDelPeriodoSection` / `DisponibleDelPeriodo.kt`). Reusar el mismo composable y los mismos
  datos: leer de `DashboardDataCache.data` o de la instantánea del Inicio (`InstantaneaDelInicio`); si no
  hay ninguno, **cargar** lo mínimo que esa tarjeta necesita con las mismas llamadas que usa el Inicio
  (extraer esa carga a una función compartida si hace falta), con esqueleto mientras tanto. Nunca una
  cifra inventada.
- **Dos segmentos** debajo, con un selector segmentado de `Movi.*` (buscar el que ya use la app, p. ej. el
  de Gasto/Ingreso/Traspaso/Cuota): **«Pagos del mes»** (el `TableroDeRecurrentes` de la Task 1) y
  **«Presupuestos»** (el cuerpo de `PresupuestosScreen` extraído a un composable reusable, con su
  «+ Nuevo» y sus hojas; `Screen.Budgets` sigue existiendo y pinta ese mismo cuerpo — o redirige a
  `Screen.Plan(SEGMENTO_PRESUPUESTOS)`, lo que sea más simple sin duplicar). El segmento elegido se
  recuerda mientras se navega (estado guardable).
- Esqueletos y «no afirmar vacío» según las Global Constraints; el alto de arriba (encabezado + tarjeta
  de disponible) no salta al cargar (NATIVE ±8 dp).
- Pruebas Robolectric: los dos segmentos pintan su contenido; la tarjeta de disponible con datos en caché
  y sin ellos (esqueleto → cifra); el segmento inicial respeta el parámetro.

### Task 3: Las cuatro pestañas, en el teléfono y en la web

- `NavTab` (en `ui/components/MinBottomNav.kt`) pasa a `HOY, MOVIMIENTOS, ADD, PLAN, PATRIMONIO` (renombrar
  o reemplazar los valores; `CREDITS`, `BUDGETS`, `MORE` desaparecen). Barra del teléfono
  (`MinBottomNav`): «Hoy · Movimientos · + · Plan · Patrimonio» con íconos Material Rounded que digan eso
  (p. ej. `Today`/`Home`, `SwapVert`, `EventNote`/`Checklist`, `AccountBalance`). Verificar que
  «Movimientos» entra a 390 dp junto a las otras (NATIVE); si no entra, «Movs» y decirlo en el reporte.
  Barra de la web (`MinNavRail`): las mismas cuatro + «Agregar», sin Créditos, Presupuestos ni Más.
- `screenForTab`: HOY → `Screen.Dashboard`, MOVIMIENTOS → `Screen.Transactions()`, PLAN → `Screen.Plan()`,
  PATRIMONIO → `Screen.Accounts`. `navTabFor`: Dashboard → HOY; Transactions → MOVIMIENTOS; Plan y Budgets →
  PLAN; Accounts, AccountDetail (según su grupo), Credits, CuadreDeSaldos, Destinos → PATRIMONIO; Mas
  (Ajustes) y todo lo que hoy marca MORE (Perfil, Categorías, Documentos, Compartir, Movi AI, SMS…) →
  **ninguna** pestaña marcada (`null`), porque se llega por el avatar.
- **El Inicio se llama «Hoy»**: título del encabezado y rótulo de la pestaña. (La SDUI no cambia de
  generación: el título del encabezado vive en el binario.)
- **Recurrentes sale de Movimientos**: el chip se quita de `CHIPS_VISIBLES`; `Screen.Transactions(CHIP_RECURRENTES)`
  (pilas restauradas, avisos, notificaciones, destinos SDUI `"recurrentes"`/`"subscriptions"`) **lleva a
  `Screen.Plan(SEGMENTO_PAGOS)`** en vez de abrir Movimientos filtrado (resolverlo en un solo lugar, p. ej.
  donde `App.kt` decide qué pantalla pintar o en `NavStack.navegar`). El «Ver todos» del checklist del Hoy
  va a Plan. `Screen.Transactions` ya no pinta el tablero (quitar el código muerto que quede).
- **Destinos SDUI**: `"budgets"` → `Screen.Plan(SEGMENTO_PRESUPUESTOS)`; `"recurrentes"`, `"subscriptions"` →
  `Screen.Plan(SEGMENTO_PAGOS)`; `"accounts"`, `"investments"` → `Screen.Accounts`; `"credits"` →
  `Screen.Credits`; `"mas"` → `Screen.Mas`. El editor de pantallas ofrece los rótulos nuevos («Plan»,
  «Patrimonio», «Ajustes»).
- **Avatar → Ajustes**: todo `HeaderLeading.Avatar` abre `Screen.Mas` (título «Ajustes»), no Perfil. Ajustes
  muestra: Perfil, Categorías, Documentos, Compartir, Movi AI, Mensajes del banco (la Task 5 lo reemplaza
  por «Captura del banco»), Primeros pasos (si la guía está incompleta, como hoy) y nada que ya sea una
  pestaña (fuera Cuentas, Cuadre, Presupuestos, Créditos, Cuentas de otros de las fichas). Las pantallas a
  las que se llega desde Ajustes vuelven a Ajustes con «atrás».
- Pruebas: `navTabFor`/`screenForTab` (tabla completa), las dos barras pintan las cuatro pestañas (y no
  «Más»), `Screen.Transactions(CHIP_RECURRENTES)` termina en Plan, los remapeos SDUI, avatar → Ajustes,
  título «Hoy». Actualizar las pruebas existentes que dependían de la navegación vieja sin perder lo que
  protegen.

### Task 4: Patrimonio

`AccountsScreen` pasa a ser la pestaña **Patrimonio** (el `Screen.Accounts` sigue siendo el mismo objeto).

- Título «Patrimonio». Arriba, la tarjeta del patrimonio neto que ya tiene (Tu plata, uso condicionado,
  bienes, deudas).
- Debajo de los grupos de cuentas que ya muestra, dos secciones nuevas:
  - **«Deudas»**: una tarjeta con la deuda total y cuántos créditos/tarjetas hay (los mismos números que
    el encabezado de Créditos — reusar la función, no recalcular), que abre `Screen.Credits`. Créditos
    sigue siendo su pantalla de detalle y vuelve a Patrimonio.
  - **«Te deben»**: lo de Cuentas de otros (`Screen.Destinos`) resumido (cuántas y, si la pantalla ya lo
    calcula, cuánto), que abre esa pantalla.
- La acción del encabezado: **«Cuadrar»** (abre `Screen.CuadreDeSaldos`) junto a «+ Nueva cuenta» (o dentro
  de un menú si no entran las dos a 390 dp; medirlo).
- Las secciones nuevas cargan con la regla de siempre (esqueleto con su forma, nada de «$0» antes de la
  lectura, error con reintento) y su forma se agrega a `FormaRecordada` si su alto depende de los datos.
- Pruebas Robolectric: las dos secciones con datos y cargando; tocar abre Créditos / Cuentas de otros;
  «Cuadrar» abre el cuadre; el alto de arriba no salta (±8 dp).

### Task 5: Una sola bandeja «Por revisar»

- **Pantalla nueva** `Screen.PorRevisar` (`ui/porrevisar/PorRevisarScreen.kt`) que junta, en secciones, lo
  que hoy está en dos lugares:
  1. **«Mensajes del banco»**: los mensajes en estado pendiente (lo que hoy lista `SMSInbox` con estado
     `pending`), cada uno abre `Screen.SMSReconcile` como hoy.
  2. **«Entraron solos»**: los movimientos `UNCONFIRMED` (lo que hoy muestra el chip/modo «Por confirmar»
     de Movimientos), con la misma acción de confirmar que ya existe.
  3. Si ya existe la sección de **candidatos a pago de tarjeta** por confirmar, también acá.
  Reusar los composables/lógica existentes; nada copiado. Vacío verdadero: «Todo al día» solo después de
  que las lecturas contestaron bien.
- **Movimientos**: el aviso de «N entraron solos» y cualquier otro aviso de pendientes se reemplazan por
  **un solo** renglón arriba de la lista: «N por revisar» (la suma de las tres fuentes), que abre
  `Screen.PorRevisar`; no se pinta si la suma es 0. `CHIP_POR_CONFIRMAR` sigue existiendo como índice
  (no se renumera) pero lleva a `Screen.PorRevisar`.
- **Hoy**: las alertas «N mensajes del banco por confirmar» y «N pagos de tarjeta por confirmar»
  (`DashboardLogic.kt` ~l.530-533) llevan a `Screen.PorRevisar`.
- **La configuración de la captura sale de la bandeja**: la sección «Captura en este teléfono» (permiso de
  SMS, acceso a notificaciones, hibernación, barrido del historial — hoy dentro de `SMSInbox`) pasa a una
  pantalla «Captura del banco» en Ajustes, junto con el **historial** de mensajes (lo que queda de
  `SMSInbox`: todos los mensajes, confirmados e ignorados, para consultar). En la bandeja «Por revisar»,
  solo si la captura dejó de andar (la misma condición que hoy decide el aviso del Inicio), un renglón
  que lleva a «Captura del banco».
- En Android el «Mensajes del banco» de Ajustes se llama «Captura del banco»; en la web y en iOS (sin
  captura) el historial igual se puede ver (los correos del banco también llegan ahí).
- Pruebas: la bandeja con las tres fuentes y con ninguna; el renglón de Movimientos suma y abre la
  bandeja; las alertas del Hoy abren la bandeja; la captura vive en Ajustes; nada afirma vacío antes de
  leer.
