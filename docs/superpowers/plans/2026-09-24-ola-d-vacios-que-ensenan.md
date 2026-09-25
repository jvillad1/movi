# Ola D de «Movi sin grasa»: vacíos que enseñan

Origen: revisión de estructura del 23-sep (https://claude.ai/artifact/88BSTvLsPp8SjJBUjZkFpn),
propuesta 9. El dueño aprobó las olas A-D. Esta es la última.

> **Qué veo.** Cuando algo no tiene datos, Movi pone un texto gris o nada. Al dueño le pasa poco, pero a Caro o a
> cualquiera que entre nuevo le pasa en todas las pantallas.
> **Propongo.** Cada vacío dice qué va a aparecer ahí y trae el botón que lo llena: «Aún no tienes
> presupuestos. Movi te propone 4 a partir de lo que gastaste en agosto».

Esta ola incluye también tres detalles que se vieron en el teléfono del dueño tras la ola C (Task 5).

Repo: `/private/tmp/movi-ola-d` (rama `ola-d-vacios-que-ensenan`, base `origin/master` f066928f). Leer
`CLAUDE.md` antes de empezar.

## Inventario de hoy (usuario nuevo, sin datos)

| Pantalla | Hoy | file |
|---|---|---|
| Hoy · hero «Tu plata» | **«$0»** apenas `accounts` contesta vacío | `ui/sdui/SeccionesDeUnVistazo.kt` ~170-179 |
| Hoy · resto | secciones que no se dibujan + tarjeta «Primeros pasos» (ya enseña, se queda) | `ui/dashboard/DashboardScreen.kt` ~609-658 |
| Movimientos | «Sin movimientos aún» + «Registrar el primero» / «Crear una cuenta primero» (**ya está bien**, es el modelo) | `ui/transactions/TransactionsScreen.kt` ~617-645 |
| Plan · Pagos del mes | lista vacía sin explicación y «Flujo libre» con **$0** | `ui/plan/TableroDeRecurrentes.kt` |
| Plan · Presupuestos | solo el botón «Nuevo presupuesto» y la tarjeta «Gastado en septiembre **$0 de $0**» | `ui/budgets/PresupuestosScreen.kt` ~281, 357-378 |
| Patrimonio | encabezado «Tu plata **$0**» + «Sin cuentas aún» / «Crear primera cuenta» | `ui/accounts/AccountsScreen.kt` ~196-226, 279-317 |
| Créditos | «Sin créditos registrados», sin botón | `ui/credits/CreditosScreen.kt` ~249-264 |
| Cuadre de saldos | «Todavía no tienes cuentas de Dinero ni de Inversión.», sin botón | `ui/.../CuadreDeSaldosScreen.kt` ~184-190 |
| Compartir | «No tienes enlaces activos…», sin botón (crear está en el encabezado) | `ui/.../CompartirScreen.kt` ~182-184 |
| Documentos | explicación sin botón («Subir archivo» está en el encabezado) | `ui/documentos/DocumentosScreen.kt` ~286-303 |
| Por revisar | «Todo al día» + explicación, sin botón | `ui/porrevisar/PorRevisarScreen.kt` ~361-377 |

No existe un componente común de vacío: cada pantalla dibuja el suyo.

## Global Constraints

- Pruebas: `JAVA_HOME=/usr/local/share/jbrsdk-21/Contents/Home ./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs`
  es CI; mirar el `BUILD SUCCESSFUL`/`BUILD FAILED` real. Nunca `./gradlew build`. Un Gradle a la vez (la
  máquina tiene 16 GB; `timeout` no existe acá). Nunca correr nada desde `/Users/carolinarestrepo/Developer/movi`
  (su `server/.env` tiene una llave real).
- Texto visible en español neutro con tuteo, sin voseo (`VoseoScanTest`). Colores, espacios y formas
  desde `Movi.*` (Tokens.kt); nada de colores literales.
- **Un vacío se afirma solo después de una lectura que contestó vacía**: `null` = no llegó (esqueleto,
  como hoy), lista vacía = llegó vacía (vacío que enseña), error = `NoSePudoLeer` con reintento (como hoy).
  Nunca «$0» ni «0» presentados como un hecho para quien todavía no tiene nada.
- **Cada vacío dice qué va a aparecer ahí y trae el botón que lo llena**, cuando existe la acción. El botón
  abre **la misma** acción que ya existe (la hoja de crear cuenta, «Nuevo crédito», «Subir archivo»…): no
  se duplica lógica de creación.
- Los esqueletos no cambian. Un vacío que enseña no debe producir saltos al pasar de esqueleto a vacío
  mayores que los que ya hay hoy.
- Nada de valores nuevos en enums serializados; campos `@Serializable` nuevos con valor por defecto;
  destinos SDUI y `CHIP_*` intactos. Lecturas suspend con `intentar {}` (`data/Intentar.kt`), nunca
  `runCatching`. `catch (e: Throwable)` relanza `CancellationException`.
- Todo `object`/store nuevo con datos del usuario → `SessionManager.clear()` + `ElForkLlegaLimpioTest`.
- Ninguna prueba escribe en producción.
- Nada de lógica copiada: si dos pantallas muestran lo mismo, se extrae una vez.
- Commits chicos en español, `git add` con rutas, terminando con
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. KDocs al estilo del archivo: el porqué.

---

### Task 1: Un vacío que enseña, y el de Hoy

- Crear `shared/.../ui/components/VacioQueEnsena.kt`: `VacioQueEnsena(titulo: String, detalle: String,
  accion: String? = null, onAccion: (() -> Unit)? = null, modifier: Modifier = Modifier, icono: ImageVector? = null)`.
  Diseño: tarjeta del sistema (misma superficie y radio que las tarjetas de Movi), ícono opcional en un
  círculo tenue, título en la escala de título de sección, detalle en la escala de cuerpo con color de
  apoyo (legible en los dos temas: el contrato de contraste de Tokens aplica), y el botón con el estilo de
  acción principal existente (`NewItemButton` o el botón primario que use Movi; reusar, no inventar).
  Un `testTag` estable para las pruebas. KDoc con la regla: «dice qué va a aparecer y trae el botón que lo llena».
- **Movimientos**: pasar `VacioDeMovimientos` a dibujarse con `VacioQueEnsena` (mismos textos, mismos
  botones, mismas condiciones). Es la prueba de que el componente sirve para lo que ya estaba bien.
- **Hoy · hero «Tu plata»**: cuando `accounts` contestó **vacía** (no `null`), el hero no muestra «$0»:
  muestra `VacioQueEnsena(titulo = "Aquí vas a ver tu plata", detalle = "Crea la cuenta donde te llega la
  plata y Movi te muestra cuánto tienes, cuánto entra y cuánto sale en tu período.", accion = "Crear mi
  primera cuenta")`, que abre la misma hoja de crear cuenta que usa «Primeros pasos». Con `accounts == null`
  sigue el esqueleto de hoy. Con cuentas, nada cambia.
- Pruebas (Robolectric): el hero con `accounts = emptyList()` no contiene «$0» y sí el título y el botón;
  tocar el botón abre la hoja de crear cuenta; con `accounts = null` sigue el esqueleto; Movimientos vacío
  sigue mostrando los mismos textos y botones de antes.

### Task 2: Patrimonio, Créditos, Cuadre, Compartir, Documentos y Por revisar

Cada uno con `VacioQueEnsena`, solo con lectura contestada y vacía:

- **Patrimonio** (`AccountsScreen.kt`): si no hay cuentas, ni bienes, ni deudas (todas las lecturas
  contestaron), **no** se dibuja el encabezado «Patrimonio neto / Tu plata $0»; en su lugar
  `VacioQueEnsena("Aquí vive lo que tienes y lo que debes", "Tus cuentas de banco, tus inversiones, tu casa o tu carro, y tus créditos. Empieza por la cuenta donde te llega el sueldo.", "Crear mi primera cuenta")`
  → la hoja de crear cuenta de siempre. Reemplaza al «Sin cuentas aún / Crear primera cuenta» actual (no
  dos vacíos juntos). Las tarjetas de abajo (Cuadre, Movimientos entre cuentas, Deudas, Te deben) siguen.
- **Créditos** (`CreditosScreen.kt`): «Sin créditos registrados» → `VacioQueEnsena("Aquí van tus créditos y tarjetas", "Carga un préstamo o una tarjeta y Movi te dice cuánto pagas de intereses, cuándo terminas y si la cuota alcanza.", "Agregar un crédito")`
  → lo mismo que «Nuevo crédito» del encabezado (`showTypeChooser = true`).
- **Cuadre de saldos**: el texto actual pasa a `VacioQueEnsena("Todavía no hay nada que cuadrar", "El cuadre compara el saldo de cada cuenta de Dinero o Inversión con el que te dice tu banco. Crea una cuenta para empezar.", "Crear una cuenta")`
  → hoja de crear cuenta (si desde esa pantalla no se puede abrir, navegar a Patrimonio y decirlo en el reporte).
- **Compartir**: agregar la acción `"Crear un enlace"` al vacío actual (mismo texto), que hace lo mismo que
  la acción del encabezado.
- **Documentos**: agregar la acción `"Subir un archivo"` (misma `elegirArchivo` del encabezado) al vacío actual.
- **Por revisar** (`PorRevisarScreen.kt`): «Todo al día» se queda. Si la lectura de mensajes del banco
  contestó **y el usuario nunca recibió ninguno** (lista vacía; ver `CapturaDeSms.nuncaLlegoNada` /
  `avisoDeCaptura` para no inventar otro criterio), debajo va
  `VacioQueEnsena("Que tus movimientos entren solos", "Movi puede leer los mensajes y avisos de tu banco y dejarlos aquí para que los revises. Así no tienes que anotar cada compra.", "Configurar la captura")`
  → `Screen.CapturaDelBanco`. En web/iOS el texto dice «los correos de tu banco» si esa plataforma solo
  captura por correo; mirar cómo `CapturaDelBanco` decide su rótulo por plataforma y usar la misma decisión.
- Pruebas: una por pantalla (vacío con lectura contestada → título + botón + acción correcta; lectura en
  vuelo → sin vacío; con datos → sin vacío). Patrimonio: sin «$0» cuando está vacío.

### Task 3: Plan vacío

- **Pagos del mes** (`TableroDeRecurrentes.kt`): cuando las reglas recurrentes contestaron vacías (y no hay
  suscripciones ni candidatas «Detectadas» — si hay candidatas, se muestran como hoy y el vacío no aparece),
  mostrar `VacioQueEnsena("Aquí van tus pagos fijos", "Arriendo, colegio, cuotas, suscripciones: anótalos una vez y cada período Movi te dice cuáles faltan y los marca solos cuando salen.", "Agregar un pago fijo")`
  → la misma acción de crear recurrente que ya existe en el tablero. En ese caso **no** se dibuja «Flujo
  libre» ni ninguna cifra en $0 del tablero.
- **Presupuestos** (`PresupuestosScreen.kt`): con `sinPresupuestos` **no** se dibuja la tarjeta «Gastado
  en …» (hoy muestra $0 de $0). En su lugar, `VacioQueEnsena("Ponle un tope a lo que más gastas", "Elige una categoría y cuánto quieres gastar como máximo en cada período. Movi te avisa cuando te acerques.", "Nuevo presupuesto")`
  → `estado.abrirNuevo()`. (La Task 4 le agrega las propuestas; esta task deja el vacío listo para recibirlas.)
- Pruebas: Pagos vacío sin «Flujo libre»/«$0» y con botón; con candidatas no hay vacío; Presupuestos vacío
  sin «Gastado en» y con botón que abre la hoja.

### Task 4: Movi te propone presupuestos

- **Server**: `GET /api/budgets/propuestas` (en `FinanceRoutes.kt` junto a `/api/budgets`, autenticado)
  → `List<PropuestaDePresupuesto>`; modelo nuevo en `core/.../model/` `@Serializable data class
  PropuestaDePresupuesto(val category: String, val amount: Double, val gastado: Double, val desde: String, val hasta: String)`
  (fechas ISO `yyyy-MM-dd` del período usado). Cálculo:
  - El período **anterior** al actual según los ajustes de período del usuario (`periodoDe` +
    `periodoAnterior` de `PeriodoFinanciero.kt`, con los mismos `PeriodSettings` que usa el resto del server).
  - Gastos del usuario en ese período, **excluyendo anulados** (`void_events`), lo que no es flujo de caja
    (traspasos, pagos de tarjeta: la misma regla que usa «Gastos» del período — buscarla y reutilizarla,
    p. ej. `isCashFlow`/`countsAsCashFlow`), lo que espera en «Por confirmar», la categoría
    `CUOTA_CATEGORY`, las categorías reservadas (Saldo inicial, Ajuste, Traspaso — usar la lista que ya
    exista) y las categorías que **ya tienen presupuesto**. Solo COP.
  - Sumar por categoría, tomar las **4** de más gasto con gasto > 0, y proponer `amount` = el gasto
    redondeado hacia arriba al múltiplo de 10.000 (si es < 1.000.000) o de 50.000 (si es ≥ 1.000.000).
  - Sin gastos en el período anterior → lista vacía.
  - Pruebas del server: período con corte distinto de 1 (p. ej. 25), anulados excluidos, traspaso/pago de
    tarjeta excluidos, cuota excluida, categoría con presupuesto excluida, redondeos (99.001 → 100.000;
    1.020.000 → 1.050.000), orden y tope de 4, otro usuario no se mezcla.
- **Cliente**: `WalletRepository`/el repositorio de presupuestos que ya existe + `getPropuestasDePresupuesto()`.
  En el vacío de Presupuestos (Task 3), si hay propuestas: el detalle pasa a `"Movi te propone N a partir de lo que gastaste del {desde legible} al {hasta legible}."`
  (fechas como «25 de julio», reutilizando el formateador de fechas legibles que ya existe para el rango del
  período), debajo una lista con cada propuesta (ícono/color de la categoría con `AparienciaDeCategoria`,
  nombre, «Gastaste $X · tope $Y») con casilla marcada por defecto, y la acción principal
  `"Crear estos N"` (N = marcadas; deshabilitada con 0) que crea los presupuestos con el endpoint de crear
  que ya existe (uno por uno está bien; si alguno falla, los creados quedan y se dice cuál falló con
  reintento) y recarga. Acción secundaria `"Crear uno a mano"` → `estado.abrirNuevo()`. Sin propuestas (o si
  la lectura de propuestas falla) → el vacío de la Task 3 tal cual: **un fallo de propuestas no bloquea
  crear a mano** y no muestra error de pantalla completa.
- La lectura de propuestas se hace **solo** cuando `sinPresupuestos` (no cuesta nada a quien ya tiene).
- Pruebas (Robolectric): con propuestas → texto con N y fechas, casillas, «Crear estos N» llama crear con los
  montos correctos para las marcadas; desmarcar baja N; error en propuestas → vacío simple con botón;
  mientras cargan las propuestas el vacío no salta (reservar el alto o mostrar el vacío simple y crecer una
  sola vez — elegir y justificar).

### Task 5: Los tres detalles del teléfono

1. **Presupuestos dice «septiembre» y «este mes»** aunque el período del dueño va del 25-ago al 24-sep.
   `PresupuestosScreen.kt` ~250-254 y ~378 (`"Gastado en ${estado.monthName}"`), ~664 y
   `PresupuestoAvisos.kt` ~57, 72, 78 (`"este mes"`). Cambiar a lenguaje de período: «Gastado en este
   período» (el rango ya está arriba en Plan), las filas sin «este mes» («$290.340 de $200.000») y los avisos
   con «este período». Si el período del usuario coincide con el mes calendario (corte 1), puede seguir
   diciendo el mes («Gastado en septiembre»): decidir con `PeriodSettings`, una sola función.
2. **«Ya salieron los 12 pagos» junto a «Listos · 14 de 14»** (`dashboard/ResumenDelPeriodo.kt` ~427-450 y
   ~483-484): los 14 incluyen ingresos. Cuando no falta nada y hay ingresos en el checklist, la línea dice
   `"Ya está todo lo de este período: 12 pagos y 2 ingresos"` (singulares: «1 pago», «1 ingreso»); sin
   ingresos queda «Ya salieron los N pagos de este período». «Listos · X de Y» no cambia. Actualizar las
   pruebas que fijan el texto viejo.
3. **La fecha del último mensaje sale cruda** («2026-09-23 19:52»): `fechaLegibleDeSms` en
   `core/.../model/CapturaDeSms.kt` ~128-131 pasa a «23 de septiembre a las 7:52 p. m.» (mes en español,
   12 horas con «a. m.»/«p. m.», medianoche «12:05 a. m.», mediodía «12:30 p. m.»); si el texto no se puede
   leer, se devuelve tal cual (nunca lanza). Revisar las dos frases de `avisoDeCaptura` para que lean bien
   («llegó el 23 de septiembre a las 7:52 p. m.»). Pruebas en `:core:jvmTest`.
