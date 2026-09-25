# Ola E: el checklist se completa solo, y «Tus períodos»

Pedidos del dueño (25-sep), después de cerrar a mano su período de septiembre el 24 (el sueldo llegó un día antes):

> 1. (Aprobado de la lista de propuestas) «que el checklist se complete solo»: su sueldo «Salario Octubre 2026»
>    no se emparejó con la regla «Salario» (el nombre tiene que ser idéntico y el monto cambió), y un pago tardío
>    que cruzó el corte vuelve al gasto variable pasada la gracia (límite anotado en la ola D).
> 2. *«Sobre el cierre de ciclo y comienzo del actual, me gustaría que la app tenga una sección donde pueda ver mis
>    ciclos del pasado y detalles de cada uno.»*

Repo: `/private/tmp/movi-ola-e` (rama `ola-e-periodos`, base `origin/master` 712ea983). Leer `CLAUDE.md`.

## Lo que ya existe (inventario)

- Períodos: `core/.../model/PeriodoFinanciero.kt` — `PeriodSettings(cutoffDay, iniciosPropios)`, `periodoDe`,
  `periodoAnterior`, `periodoSiguiente`, `ventanaDe` (`[inicio, fin)` en epoch-ms, respeta excepciones),
  `inicioDelPeriodo` (ignora una excepción fuera del mes permitido), `rangoLegibleDe`, `diaLegible`.
  Server: `ajustesDePeriodoDe(uid)`.
- Declarar un inicio propio: `ui/profile/InicioDelPeriodoSheet.kt` (desde el nombre del mes en Movimientos,
  `TransactionsScreen.kt` ~1082 y ~1495-1516) → `PUT /api/users/me` con `periodStarts` (`UserRoutes.kt` ~71-152,
  valida formato y tope `MAX_INICIOS_PROPIOS`, no el mes).
- Sumas por ventana: `monthCashFlow(monthStart, monthEnd, …)` en `DashboardRoutes.kt` (lo que usan el Inicio, las
  barras de Presupuestos y `/api/budgets/propuestas`): excluye anulados, «Por confirmar», traspasos, pagos de tarjeta,
  no-COP. `sumasAntesDe` (privada, DashboardRoutes) suma movimientos antes de un instante para el saldo de «Tu plata»
  al inicio del período en curso. `patrimonioDe`/`cuentasConSaldo` en core `Patrimonio.kt`.
- **Todo resumen del server está atado al período en curso** (`/api/dashboard/summary`, `/api/finance-summary`,
  `/api/payments/occurrences`). Movimientos navega períodos en el cliente con `/api/events/by-day` (historia entera)
  y no muestra totales del período.
- Emparejamiento: `server/.../reminders/OccurrenceMatching.kt` (`candidatosPuntuados`, `nombrePega` con `==` sobre
  `claveComparableDeNombre` de core `NameKey.kt`, `ocurrenciaConcluyente` = exactamente un candidato concluyente:
  nombre, o categoría+cuenta+monto exacto; `OCCURRENCE_WINDOW_DAYS = 10`), `reminders/DueDates.kt`
  (`DEFAULT_GRACE_DAYS = 5`, `ocurrenciaPorPreguntar`), `reminders/Occurrences.kt` (`emparejadasComoSellos`, con el
  límite conocido en su KDoc), `routes/ReminderRoutes.kt` (`estadosDeLasOcurrenciasReales` ~774,
  `ocurridosDeLosVencimientos`), `routes/DashboardRoutes.kt` (`parteFijaDelDisponible` ~227).
  El principio del emparejador: **equivocarse hacia preguntar, nunca hacia «sí, es este»** (un pago dado por hecho
  que no lo está silencia un recordatorio de una deuda real).

## Global Constraints

- Pruebas: `JAVA_HOME=/usr/local/share/jbrsdk-21/Contents/Home ./gradlew --rerun-tasks :core:jvmTest :server:test :shared:testDebugUnitTest :shared:compileKotlinWasmJs`
  es CI; mirar el `BUILD SUCCESSFUL`/`BUILD FAILED` real. Nunca `./gradlew build`. Un Gradle a la vez (16 GB;
  `timeout` no existe). Nunca correr nada desde `/Users/carolinarestrepo/Developer/movi` (su `server/.env` tiene una
  llave real). Una prueba Robolectric que falle con `AppNotIdleException` bajo carga se corre sola y se reportan los
  dos resultados.
- **Toda suma de plata excluye `void_events`** y usa las mismas reglas que el resto de la app (reusar
  `monthCashFlow`/`isCashFlow`/`esperaEnPorConfirmar`; nada de una regla de «gasto» nueva). Solo COP donde el resto
  de la app suma solo COP. Un usuario nunca ve datos de otro.
- Los períodos se calculan **siempre** con `PeriodSettings` (corte + `iniciosPropios`) vía `ventanaDe`/`periodoDe`:
  el dueño tiene corte 25 y la excepción `{"2026-10":"2026-09-24"}`.
- Nada de valores nuevos en enums serializados; campos `@Serializable` nuevos con valor por defecto; destinos SDUI y
  `CHIP_*` intactos; las respuestas existentes no cambian de forma (el APK 1.52 instalado las lee).
- UI: texto en español neutro con tuteo (`VoseoScanTest`); colores/espacios de `Movi.*`; `null` = no llegó
  (esqueleto con la forma real), vacío = `VacioQueEnsena` solo con lectura contestada vacía, error = `NoSePudoLeer`
  con reintento; nunca «$0» como hecho antes de una lectura. Lecturas suspend con `intentar {}`, nunca `runCatching`.
  `catch (e: Throwable)` relanza `CancellationException`.
- Todo `object`/store nuevo con datos del usuario → `SessionManager.clear()` + `ElForkLlegaLimpioTest`.
- Ninguna prueba escribe en producción. Nada de lógica copiada: si dos lugares calculan lo mismo, se extrae una vez.
- Comentarios con el porqué, sin etiquetas de proceso («Task N», «Ola E», «Fix round») en código de producción.
- Commits chicos en español, `git add` con rutas, terminando con
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Commit temprano: `/private/tmp` se puede borrar.

---

### Task 1: El nombre pega aunque el movimiento diga el mes y el año

Hoy `nombrePega` exige igualdad exacta de `claveComparableDeNombre`. El dueño nombra su sueldo «Salario Octubre 2026»
y la regla se llama «Salario»: no pega, y como el monto cambió tampoco pegan las tres circunstancias.

- En core (junto a `claveComparableDeNombre`, `NameKey.kt`): una función que, para comparar con la clave de una
  regla, quite de la **descripción/comercio del movimiento** las palabras que solo dicen **cuándo**: nombres de mes en
  español (enero…diciembre, también «setiembre»), abreviaturas de 3 letras (ene, feb, mar, abr, may, jun, jul, ago,
  sep, oct, nov, dic) **solo como palabra completa**, años de 4 dígitos (19xx/20xx) y los conectores «de», «del»
  cuando quedan sueltos junto a esos. Después compara con `==` como hoy. Es decir: «Salario Octubre 2026», «Salario
  de octubre», «SALARIO OCT 2026» pegan con «Salario»; «Salario Caro», «Gimnasio Caro» vs «Gimnasio», «Cuota Hipoteca
  Papá» vs «Cuota Hipoteca» **no** pegan. Si quitar esas palabras deja la clave vacía, no pega.
- Usarla en los dos lugares donde se calcula `nombrePega` (`candidatosPuntuados` ~219 y `esConcluyente` ~343):
  extraer una sola función `nombrePegaCon(regla, evento)` y llamarla desde ambos. El resto de la regla
  (exactamente un concluyente; dos → preguntar) no cambia.
- No tocar las comparaciones de suscripciones (`RecurringOffer.kt` `clavesDeCobroDe`) ni `claveDescartada`.
- Pruebas (core): los ejemplos de arriba, mayúsculas/tildes, «2026» suelto, un nombre de regla que ya contiene un mes
  («Prima de junio» vs movimiento «Prima de junio 2026» → pega; vs «Prima» → no). Pruebas (server): el caso real del
  dueño — regla Salario $20.038.658 día 25, movimiento «Salario Octubre 2026» $20.038.658 el 24-sep en la misma
  cuenta — y la variante con **monto distinto** ($20.500.000): se empareja solo por nombre; con dos movimientos
  «Salario …» en la ventana → sigue preguntando.

### Task 2: El pago tardío no vuelve al gasto variable pasada la gracia

Límite anotado en la ola D (`emparejadasComoSellos`, KDoc): el checklist solo deriva la ocurrencia por la que
pregunta; pasada la gracia (`DEFAULT_GRACE_DAYS = 5`) pasa a la siguiente y el emparejamiento de la anterior deja de
salir, así que un pago tardío que cruzó el corte vuelve al gasto variable y baja el «Disponible».

- Hacer que `emparejadasComoSellos` (o la función de la que sale) derive **también** el emparejamiento de la
  ocurrencia **anterior** de cada regla mientras la ventana de esa ocurrencia (`occurrenceWindow`) se solape con el
  período en curso, con la misma regla de concluyente y respetando sellos y rechazos (`occurrence_rejections`). Un
  movimiento no puede quedar emparejado con dos ocurrencias.
- Lo consumen igual Próximos/barrido (`ocurridosDeLosVencimientos`) y el Disponible (`parteFijaDelDisponible`):
  verificar que Próximos no retroceda (una ocurrencia anterior emparejada no debe volver a mostrarse vencida ni mover
  el vencimiento vigente hacia atrás).
- Pruebas (server, día fijo): arriendo que vence el 23 con corte 25, pagado el 26 (cruzó el corte), hoy = 30 (gracia
  vencida): el pago sale del gasto variable del Disponible (falla antes); con sello a mano, igual que antes; con dos
  concluyentes, no cuenta; con el mismo movimiento candidato de dos ocurrencias, se usa una sola vez.

### Task 3: El server sabe contar cualquier período

- Core: `@Serializable` nuevos (en `core/.../model/`, con defaults):
  - `ResumenDePeriodo(id: String /*"2026-09"*/, nombre: String /*«Septiembre 2026»*/, desde: String, hasta: String
    /*ISO, hasta = último día incluido*/, inicioPropio: Boolean = false, enCurso: Boolean = false, entradas: Long = 0,
    salidas: Long = 0, movimientos: Int = 0)`.
  - `DetalleDePeriodo(resumen: ResumenDePeriodo, porCategoria: List<GastoDeCategoria> = emptyList(), pagosFijos:
    List<PagoFijoDelPeriodo> = emptyList(), presupuestos: List<PresupuestoDelPeriodo> = emptyList(), masGrandes:
    List<FinancialEvent> = emptyList(), tuPlataAlEmpezar: Long? = null, tuPlataAlCerrar: Long? = null)` con
    `GastoDeCategoria(category, monto)`, `PagoFijoDelPeriodo(ruleId, nombre, monto: Long, esIngreso: Boolean,
    vencimiento: String, estado: String /*"LISTO"|"PENDIENTE"|"CON_DUDAS"*/, eventId: String? = null, montoReal:
    Long? = null)` y `PresupuestoDelPeriodo(category, limite, gastado)`. `estado` es String a propósito (no un enum
    nuevo en el cable).
  - El nombre del período: el mes que le da nombre en español con mayúscula inicial + año («Octubre 2026»), una sola
    función en core.
- Server `routes/PeriodosRoutes.kt` (registrado en `plugins/Routing.kt`, autenticado):
  - `GET /api/periodos` → `List<ResumenDePeriodo>` del **más nuevo al más viejo**: desde el período en curso hasta el
    del movimiento vivo más viejo del usuario (tope 36). Sin movimientos → solo el en curso. `entradas`/`salidas` con
    `monthCashFlow` sobre `ventanaDe` de cada uno (una sola lectura de eventos, no una por período).
  - `GET /api/periodos/{id}` → `DetalleDePeriodo`; `id` inválido o fuera de rango → 404.
    - `porCategoria`: de `monthCashFlow` (gastos), de mayor a menor.
    - `pagosFijos`: para cada regla recurrente real, el vencimiento que cae en ese período y su estado: sello en
      `recurring_occurrences` (LISTO con su evento) o emparejamiento derivado con la misma `ocurrenciaConcluyente`/
      Task 1 (LISTO), dos concluyentes (CON_DUDAS), nada (PENDIENTE). Para el período en curso, el resultado tiene
      que coincidir con `/api/payments/occurrences`/el checklist (extraer lo común; no un tercer emparejador).
    - `presupuestos`: los presupuestos de hoy con lo gastado en esa ventana (mismo cálculo que las barras).
    - `masGrandes`: los 5 gastos de flujo más grandes del período (vivos, confirmados).
    - `tuPlataAlEmpezar`/`tuPlataAlCerrar`: saldo de «Tu plata» (las mismas cuentas que suma «Tu plata» del Inicio)
      al inicio y al fin de la ventana, generalizando `sumasAntesDe` (extraer, no copiar). Si no se puede calcular
      honestamente para alguna cuenta, `null`.
- Pruebas del server: corte 25 con la excepción `{"2026-10":"2026-09-24"}` (septiembre termina el 23, octubre empieza
  el 24); anulados excluidos; traspasos/pagos de tarjeta/por confirmar excluidos; otro usuario no se mezcla; lista
  ordenada y con tope; 404; pagos fijos LISTO por sello, por nombre (Task 1) y CON_DUDAS; coincidencia con
  `/api/payments/occurrences` en el período en curso; saldos al empezar/cerrar con un saldo inicial y movimientos
  antes/durante/después.

### Task 4: «Tus períodos» en la app

- `WalletRepository`: `getPeriodos()`, `getDetalleDePeriodo(id)` (con chequeo de status como `createBudget`).
- `Screen.Periodos` (UI-only, no viaja) — pantalla `ui/periodos/PeriodosScreen.kt`, título «Tus períodos»:
  - Una fila por período: nombre («Octubre 2026»), rango («Del 24 de septiembre al 24 de octubre», reusar
    `rangoLegibleDe`/`diaLegible`), una marca «En curso» en el primero y «Empezó el 24» cuando `inicioPropio`,
    entró / salió / te quedó (entradas − salidas, con signo y color de plata `entra`/`sale`), y una barra pequeña
    entró-vs-salió como la del Hoy (reusar la pieza si existe). Toca → detalle.
  - Esqueleto con la forma real (filas; `FormaRecordada` para el número de filas), `NoSePudoLeer` con reintento, y
    vacío que enseña si la lista llega vacía (no debería: siempre viene el en curso).
- Entradas: en **Hoy**, la línea del período del hero («Del 24 de septiembre al 24 de octubre · quedan 30 días») se
  vuelve tocable → `Screen.Periodos`; en **Plan**, una fila «Tus períodos» (subtítulo «Cómo te fue en cada ciclo»)
  arriba del selector o debajo de «Cuánto puedes gastar»; en **Movimientos**, la hoja que abre el nombre del mes
  (`InicioDelPeriodoSheet`) gana un enlace «Ver tus períodos».
- `navTabFor(Screen.Periodos)` = PLAN. Atrás vuelve a donde vino.
- Pruebas Robolectric: lista con datos (orden, marcas, cifras), esqueleto sin salto > 8 dp, error con reintento, las
  tres entradas navegan.

### Task 5: El detalle de un período, y empezar uno nuevo desde la app

- `Screen.DetalleDePeriodo(id: String)` — `ui/periodos/DetalleDePeriodoScreen.kt`, título = nombre del período:
  1. Arriba: rango y, si `inicioPropio`, «Este período empezó el 24 de septiembre (antes del día 25)». Entró / salió /
     te quedó, con la barra; «Tu plata: $X al empezar → $Y al cerrar» (o «hoy» si está en curso); omitido si `null`.
  2. «En qué se fue»: categorías con barras (reusar la pieza de categorías del Hoy, `AparienciaDeCategoria`).
  3. «Pagos fijos · N de M»: filas con ✓ (LISTO, monto real si difiere), «pendiente», o «con dudas».
  4. «Presupuestos» (si hay): cada uno gastado de límite, con color de estado igual que Presupuestos.
  5. «Los más grandes»: 5 movimientos (título, fecha, monto); tocar uno abre el movimiento como en Movimientos.
  6. Acción «Ver los movimientos de este período» → `Screen.Transactions` abierto en ese período (agregar un
     parámetro opcional de período inicial a `Screen.Transactions` con default que no cambie nada de hoy).
  7. **Solo en el período en curso**: «Empezar un período nuevo hoy». Abre una confirmación en la misma pantalla
     (nada de `confirm()`): «Octubre termina hoy y Noviembre empieza hoy, 20 de octubre. Úsalo cuando tu plata del
     mes nuevo ya llegó (por ejemplo, el sueldo se pagó antes).». Guarda `periodStarts[<id del siguiente>] = hoy` con
     `PUT /api/users/me` (misma escritura que `InicioDelPeriodoSheet`, reusar su camino) **solo si** hoy es un inicio
     válido para el período siguiente según `inicioDelPeriodo` (mismo mes permitido; si no, la acción no aparece) y
     no pasa el tope de excepciones. Después recarga la lista y el Hoy. Si hoy ya es el inicio del siguiente por la
     regla del corte, la acción no aparece.
- Estados: esqueleto con la forma del detalle, `NoSePudoLeer`, y vacíos que enseñan por sección (sin gastos, sin
  pagos fijos → «Agregar un pago fijo» como en Plan, etc.) solo con lectura contestada.
- Pruebas Robolectric: secciones con datos; período en curso muestra la acción y un período pasado no; la
  confirmación guarda el mapa correcto (`{"2026-11":"2026-10-20"}` sumado a los existentes) y recarga; hoy inválido
  → sin acción; «Ver los movimientos» abre Movimientos en ese período.
