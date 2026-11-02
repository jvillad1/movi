# Períodos quincenales y semanales — diseño

**Fecha:** 2026-11-02 · **Estado:** propuesta, sin implementar · **Pedido:** Lina («poder tener un período de
quincena o de semana, no solo de mes»).

En Colombia es común que paguen **cada quincena** (el 15 y el 30, el 15 y el último día hábil, el 1 y el
16) o **cada semana** (el viernes, el sábado). Hoy Movi solo sabe de un período mensual con día de corte
(`PeriodSettings.cutoffDay`), corrido por fin de semana/festivo (`siCaeEnFinDeSemana`) y con excepciones
a mano (`iniciosPropios`). Este documento dice dónde el código asume «mes», qué modelo se propone, qué
decisiones son del dueño y en qué orden entregarlo.

**Garantía central:** con `frecuencia = MENSUAL` (el valor por defecto, y el del dueño) **todas** las
funciones devuelven exactamente lo mismo que hoy. Nada se migra; las columnas nuevas son nulas.

---

## 0. La idea en una frase

Separar dos relojes que hoy son el mismo:

| Reloj | Qué mide | Hoy | Con quincena/semana |
| --- | --- | --- | --- |
| **El período** | la plata del día a día: Disponible/«Te quedan», gasto, presupuestos, cierre, «Tus períodos», comparaciones, caja | el mes con corte | la quincena o la semana |
| **El mes de referencia** | las obligaciones mensuales: recurrentes, sellos, emparejamiento, cuotas de crédito, avisos, débitos automáticos | el mismo mes con corte | un mes con corte derivado (`mesDeReferencia()`), ver §2.6 |

Los recurrentes, las cuotas y las tarjetas **siguen siendo mensuales** (vencen un día del mes y se sellan
por el mes del vencimiento, `periodOf` en `DueDates.kt`, que ya hoy es independiente del período). Lo que
cambia es **en qué ventana se cuentan**.

---

## 1. Mapa de impacto

Leyenda del efecto: **G** = genérico, funciona solo si las primitivas de `PeriodoFinanciero.kt` aprenden la
frecuencia; **R** = debe pasar al mes de referencia; **E** = escalar umbrales/constantes por largo del
período; **T** = texto que dice «mes»; **N** = nada que hacer (habla del mes de calendario a propósito).

### 1.1 Primitivas (`core/.../model/PeriodoFinanciero.kt`) — el corazón

| Función | Asume mes en… | Qué cambia |
| --- | --- | --- |
| `PeriodSettings` | solo `cutoffDay`, `iniciosPropios`, `siCaeEnFinDeSemana` | + `frecuencia`, `segundoDia` (quincena), `diaDeLaSemana` (semana). `esMesDeCalendario` exige además `MENSUAL`. |
| `PeriodoFinanciero(year, month)` + `prefijo` | identidad = mes | + `dia: Int = 0` (0 = mensual). `prefijo` = `"AAAA-MM"` o `"AAAA-MM-DD"` (§2.3). Comparador propio (hoy hay `prefijo` lexicográfico y `year*12+month`). |
| `inicioDelPeriodo`, `inicioPorCorte`, `inicioDe` | arranque = día de corte del mes anterior/actual | arranque nominal por frecuencia (§2.2); luego `corteCorrido` y la excepción, igual que hoy. |
| `periodoDe`, `periodoPorElCorte` | candidato por día de corte; vecinos = ±1 mes | candidato = último arranque nominal ≤ fecha; vecinos = ±1 período. La comprobación por `ventanaDe` se mantiene. |
| `periodoAnterior`, `periodoSiguiente` | ±1 mes **sin settings** | pasan a recibir `settings` (46 llamadas, cambio mecánico guiado por el compilador). La versión sin settings queda como `mesAnterior/mesSiguiente` para los usos de mes. |
| `ventanaDe`, `rangoDelPeriodo`, `rangoCortoDelPeriodo`, `nombreDelPeriodo`, `rangoLegibleDe` | G | nada: ya son «fin = arranque del siguiente» y nombran por fechas. |
| `periodoDelPrefijo` | regex `AAAA-MM` | acepta las dos formas; la forma `AAAA-MM-DD` se valida contra los settings (que sea un arranque nominal). |
| `nombreDelMesDe`, `nombreDe`, `tituloDelPeriodo` | «septiembre» | solo válidos para MENSUAL; los demás llaman `nombreDelPeriodo`. |
| `fechaEsperadaDelIngreso` | el salario es la regla del día `cutoffDay` | también la del `segundoDia` en QUINCENAL (las dos reglas del salario). En SEMANAL no hay regla mensual que sea el salario (§3, D9). |
| `empezarElSiguienteHoy`, `conInicioPropio`, `MAX_INICIOS_PROPIOS` (120 = 10 años de meses) | rango válido = «mismo mes» | rango válido por frecuencia (§2.4); al escribir se podan excepciones de más de 2 años en vez de subir el tope. |
| `desdeDondeRecuperarSms` | «período anterior» ≈ 30-60 días | piso de 35 días: con semanas recuperaría 7-14. |
| `diasDelMes`, `rangoEntre`, `diaLegible` | N | — |

### 1.2 Perfil, server y almacenamiento

| Lugar | Qué cambia |
| --- | --- |
| `core/.../model/Profile.kt` `UserProfile`, `UpdateProfileRequest` | + `periodFrequency: String = "MENSUAL"` (String y no enum, como `periodWeekendRule`: un APK viejo no revienta), `periodSecondDay: Int?`, `periodWeekday: Int?`. `null` = no tocar. |
| `UserProfile.ajustesDelPeriodo()` (core) y `ajustesDelPeriodoSinSuspender` (`server/.../time/AppClock.kt`) | leen las tres columnas nuevas. Son los dos únicos constructores de `PeriodSettings`: todo lo demás hereda. |
| `server/.../db/Tables.kt` `Users` | + `period_frequency varchar(16)`, `period_second_day int`, `period_weekday int`, todas nulas → entran por `createMissingTablesAndColumns`. |
| `Tables.kt` `CierresEnviados`, `CierresGuardados`, `CierresDescartados` (`periodo varchar(7)`) | la clave pasa a admitir `AAAA-MM-DD` → **`varchar(10)`**. Requiere un `ALTER COLUMN … TYPE varchar(10)` idempotente en `Migrations` (agrandar un varchar en Postgres no reescribe la tabla); `createMissingTablesAndColumns` no cambia tipos. Riesgo: es DDL sobre tablas existentes → probarlo en `SchemaDeArranqueTest`. |
| `Tables.kt` `RecurringOccurrences.period`, `DebitosDescartados.periodo`, `RecurringRules/Credits/Cards.lastRemindedPeriod`, `Apartados.mesDelGasto` | **N**: son el mes del vencimiento o un mes de calendario, no el período. |
| `Tables.kt` `Budgets.monthlyLimit` | sin columna nueva con la opción recomendada (D2): sigue siendo mensual. |
| `server/.../routes/UserRoutes.kt` (PUT `/api/users/me`) | validar frecuencia, `segundoDia` (13 ≤ B − A ≤ 17), `diaDeLaSemana` (1..7); `PERIODO_VALIDO` acepta `AAAA-MM-DD`; al cambiar de frecuencia, vaciar `period_starts` (sus claves serían de otra forma). |

### 1.3 Obligaciones mensuales → mes de referencia (**R**)

Todas reciben hoy `settings` y deben recibir `settings.mesDeReferencia()`; si no, con quincenas un pago
vencido desaparecería al cambiar de quincena (el bug del «gimnasio del día 5» que ya se cerró una vez) y
un pago adelantado dos días a otra semana no se emparejaría.

| Archivo | Funciones |
| --- | --- |
| `server/.../reminders/DueDates.kt` | `diasDelPeriodo`, `ocurrenciaEnJuego`, `ocurrenciaPorPreguntar`, `ocurrenciaAnteriorQuePisaElPeriodo`, `dueDateFor`, `reminderKeyFor`, `upcomingPayments`, `siguienteVencimiento`, `selectDueForReminder`, `arranqueDeLaRegla`, `ruleIsActiveOn`, `fechaEsperada`. `periodoDelDueno` (nombre en pantalla) sí usa el período real. |
| `server/.../reminders/OccurrenceMatching.kt` | `occurrenceWindow`: **el piso es el arranque del período del vencimiento** → con semanas un pago 3 días antes, en la semana anterior, no se emparejaría. Con mes de referencia, igual que hoy. |
| `core/.../model/HastaDondeSePropone.kt` | `ultimoDiaParaProponer` («hasta el último día del período en que vence») → mes de referencia. |
| `server/.../reminders/PagosDeDeuda.kt` | `periodoQueSalda` / `periodoQueSaldaSinPiso` (#416, «un pago salda la cuota del período en que se hizo»), `primeraCuotaVigente`, `pagadoALaTarjetaEnElPeriodo`, `periodosSaldados`. Con quincenas, #416 casi nunca aplicaría (la quincena del pago no trae cuota); con mes de referencia sigue funcionando como el dueño lo decidió. |
| `server/.../reminders/Occurrences.kt`, `PagosDelChecklist.kt` (`vencimientoEnElChecklist`, `parteFijaDelChecklist`, `cuotasDelChecklistPagadas`), `ReminderScheduler.kt`, `routes/ReminderRoutes.kt`, `routes/DebitosAutomaticosRoutes.kt`, `routes/AnotarElPagoDeLaOcurrencia.kt`, `routes/VincularPagoDeDeudaRoutes.kt`, `routes/RepartirElAviso.kt`, `routes/CreditRoutes.kt` | el motor de ocurrencias con mes de referencia; **la pertenencia al checklist** sigue siendo la ventana real (§1.4). |
| `core/.../model/LaCuotaDeEstePeriodo.kt` | `vencimientoEnElPeriodo` ya devuelve `null` si el período no trae la cuota; `laCuotaDeEstePeriodoYaVencio` / `arranqueDeLasCuotasSiYaPagaste` («¿Ya pagaste la cuota de este período?» al dar de alta un crédito) → mes de referencia, y el texto dice «de este mes». |
| `core/.../model/ElRecurrenteQueSeRepite.kt` `seRepiteParaProponer` | cuenta **períodos distintos** con el mismo gasto: con semanas propondría un recurrente mensual por un café de dos sábados. Contar meses de referencia. |
| `core/.../model/RecurrenteQueCambioDeCategoria.kt`, `LoQueSeSaleDeLoNormal.kt` `suscripcionesQueSubieron` | comparan «el cobro de este período contra el del anterior»: una suscripción mensual nunca está en dos semanas seguidas → nunca dispara. Mes de referencia. |
| `core/.../model/PersonasYComercios.kt`, `DestinoConocido.kt` (lo enviado/recibido por período) | por semana sería ruido; agrupar por mes de referencia (D11). |
| `shared/.../data/RecurringOfferGate.kt`, `ui/plan/TableroDeRecurrentes.kt` | revisar caso por caso: lo que pregunta «¿ya ocurrió este mes?» va por referencia; lo que lista «los pagos de esta quincena» va por período. |

### 1.4 La plata del período (**G**, casi gratis)

| Archivo | Nota |
| --- | --- |
| `core/.../model/PagosDelPeriodo.kt` `checklistDelPeriodo`, `pendientesDePeriodosAnteriores`, `faltaPorPagar`, `loQueViene` | ya filtran por `ventanaDe(periodo)`: un recurrente mensual entra **entero en la quincena/semana en que vence** (opción recomendada de D1) sin tocar nada. Lo vencido de la quincena anterior aparece en «pendientes de períodos anteriores» gracias al mes de referencia. `DIAS_DE_LO_QUE_VIENE = 15` ya es una quincena. |
| `core/.../model/DisponibleDelPeriodo.kt` `disponibleDelPeriodo` | genérico en días (`inicio`, `finExclusivo`). La columna **«Esta semana»** (lunes a domingo recortada) sobra en SEMANAL → ocultarla; en QUINCENAL se queda. `diaDeInicio` («Tenías $X el 25») sirve igual. |
| `core/.../model/AhorroDelPeriodo.kt` `montoDelAhorro` | `PORCENTAJE` de «Entró» escala solo; `MONTO` es «por período» (D5). |
| `core/.../model/Apartado.kt`, `server/.../apartados/ApartadosDelUsuario.kt` | `restante` se deriva desde `desde`, no por período: **N**. `aporteMensualSugerido`/`mesesHastaElGasto` son meses de calendario: **N** (se podría decir «por quincena» = ÷2). |
| `core/.../model/ContraElPeriodoPasado.kt` `anteriorAMismaAltura`, `server/.../routes/ContraElPeriodoPasado.kt` | genérico; la opción recomendada en QUINCENAL compara con la quincena homóloga (D6) → `periodoHomologo(p, settings)`. |
| `core/.../model/CierreDelPeriodo.kt` | `DIAS_DE_LA_TARJETA_DE_CIERRE = 5`, `DIAS_PARA_AVISAR_EL_CIERRE = 3` (**E**: en una semana son 5 de 7 días). `mesDe(...)`/`tituloDelCierre` «Cerró tu período de octubre» (**T**). |
| `server/.../routes/CierreDelPeriodoRoutes.kt`, `reminders/CorreoDeCierre.kt`, `shared/.../avisos/AvisoDeCierre.kt` (+ `AvisoDeCierreWorker`) | genéricos con `periodoAnterior(p, settings)`; correo y aviso según D4. |
| `server/.../routes/PeriodosRoutes.kt` (`resumenesDePeriodos`, `periodosDelUsuario`, `detalleDePeriodo`, `pagosFijosDelPeriodo`, `pequenosDelPeriodo`) | `MAX_PERIODOS = 36` → 36 quincenas = 18 meses, 36 semanas = 8 meses (**E**: por frecuencia 36/48/52). Agrupación por mes (D3). `PresupuestoDelPeriodo(limite = monthlyLimit)` → límite prorrateado (D2). |
| `core/.../model/CajaProyectada.kt`, `server/.../routes/CajaRoutes.kt` | `PERIODOS_PARA_EL_GASTO_DIARIO = 3` → con semanas son 21 días de historia (**E**: 3/6/12 ≈ 90 días). `DIAS_DE_LA_CAJA_SIN_SALARIO = 30` y `horizonteDeLaCaja` (hasta que te paguen): bien en QUINCENAL; en SEMANAL, sin regla del salario semanal la caja no ve los próximos sueldos (D9). |
| `core/.../model/AnalisisDelPeriodo.kt` («En qué se va») | `DIAS_MAXIMOS_POR_DIA = 35` y la guarda de período cubierto: **G**. |
| `core/.../model/LoQueSeSaleDeLoNormal.kt` `categoriasPorEncima`, `server/.../routes/AnomaliasRoutes.kt` | promedio de hasta 3 períodos y `DIFERENCIA_MINIMA_DE_CATEGORIA = $300.000` (**E**: escalar el monto por el factor del período; promediar homólogos en QUINCENAL). Duplicados y comercio nuevo: **N**. `DEUDA_QUE_CRECE`/`INTERES_LEJOS…`: mes de referencia. |
| `pequenosQueSuman` (≤ $50.000, ≥ 3 veces, ≥ $100.000 por período) | **E**: el mínimo de $100.000 por el factor. |
| `server/.../routes/DashboardRoutes.kt`, `FinanceRoutes.kt` (`monthCashFlow`, `/api/finance-summary`, `/api/budgets/propuestas`) | ventanas genéricas. `propuestasDePresupuesto` se arma con el gasto **del período pasado**: con la opción D2-A hay que llevarlo a mes (las dos últimas quincenas, o el gasto del período ÷ factor). |
| `server/.../routes/CategoryRoutes.kt`, `EventRoutes.kt`, `EspacioRoutes.kt`, `DestinoRoutes.kt`, `push/PushPayload.kt` | genéricos; revisar textos. |

### 1.5 Cliente (`shared/.../ui`)

| Archivo | Qué cambia |
| --- | --- |
| `profile/PeriodoSheet.kt` («Inicio del período»), `profile/PiezasDeAjustes.kt` `guardarElInicioDelMes`, `profile/InicioDelPeriodoSheet.kt` (excepción a mano: hoy calcula «el mes natural» a mano en las líneas 115-121) | pregunta nueva **«¿Cada cuánto te pagan?»** (Cada mes · Cada quincena · Cada semana) y, según eso, el día, los dos días o el día de la semana; la regla de fin de semana y festivo igual. El rango permitido de la excepción sale de `:core` (§2.4), no de una copia. |
| `primeruso/PrimerUsoScreen.kt` (paso «Tu período»), `primeruso/SalarioDeLaGuia.kt` | misma pregunta; el salario quincenal crea **dos** reglas de ingreso (día A y día B). |
| `periodos/PeriodosScreen.kt`, `periodos/DetalleDePeriodoScreen.kt` | lista agrupada (D3); `mesDe(periodo)` («Empezar noviembre hoy», línea 1056-1080) → «Empezar la quincena hoy». |
| `plan/PlanScreen.kt`, `plan/PagosDeOtroPeriodo.kt` (`esAnterior` = `year*12+month`), `plan/CajaDelPeriodo.kt` (línea 352: `MESES_DEL_ANIO[it.month-1]` desde el prefijo), `plan/TuMesTipico.kt`, `plan/DisponibleDelPlan.kt` | navegación ‹ › con `settings`; comparador; nombres por `nombreDelPeriodo`. «Tu mes típico» sigue siendo mensual (es un promedio de mes). |
| `transactions/TransactionsScreen.kt` (selector de período, 36 usos), `ResumenEnElPanel.kt`, `LosAnulados.kt` («N anulados en octubre»), `DiaADia.kt`, `CategorySheets.kt`, `ActividadPorDentro.kt` | navegación con `settings`; textos. |
| `dashboard/DashboardLogic.kt`, `InicioDelRediseno.kt`, `ResumenDelPeriodo.kt`, `ElHeroeOptimista.kt`, `CajaEnHoy.kt`, `InstantaneaDelInicio.kt`, `sdui/SeccionesDeUnVistazo.kt`, `dashboard/TextoDelWidget.kt` (widget) | genéricos; presupuesto superado con el límite prorrateado; columna «Esta semana» oculta en SEMANAL. El widget no cambia (lee la instantánea). |
| `budgets/PresupuestosScreen.kt` (línea 405: `Month(periodoDe(...).month).spanishName()`), `budgets/PropuestasDePresupuesto.kt` | «$400.000 esta quincena · $800.000 al mes» (D2). |
| `analisis/EnQueSeVaScreen.kt` | navegación; línea del período pasado homóloga (D6). |
| `credits/*` (`CreditosLogic.kt`, `LaDeudaEnSuDetalle.kt`, `SalidaDeDeudas*.kt`, `CreditTermsSheet.kt`, `SimuladorDeAbonoSheet.kt`) | usan `PeriodoFinanciero` **como mes de la cuota** (`PeriodoFinanciero(vence.year, vence.monthNumber)`, `.mas(meses)` de `PlanDelCredito.kt`, `LaPrimeraCuotaSinPagar.kt`). **N** si esos usos pasan a un tipo `Mes` (o a `PeriodoFinanciero` con `dia = 0`, que es lo mismo). Lo que diga «este período» sobre una cuota → mes de referencia. |
| `accounts/CurvaDelPatrimonio.kt`, `accounts/ElApartadoQueAvisa.kt` (`PeriodoFinanciero(hoy.year, hoy.monthNumber)`), `accounts/ApartadoSheet.kt` | meses de calendario: **N**. |
| `fecha/FechaMovimiento.kt` (línea 264), `buscar/BuscarScreen.kt`, `categorias/*`, `destinos/*`, `quickadd/TransferForm.kt` | textos por mes → `nombreDelPeriodo`; destinos por mes de referencia. |
| `avisos/LecturaDeVencimientos.kt`, `sms/SmsBackfill.kt`, `data/Lectura.kt`, `data/FormaRecordada.kt`, `androidApp/.../sms/SmsBackfillWorker.kt` | genéricos; backfill con piso de 35 días. |

### 1.6 Movi AI

| Archivo | Qué cambia |
| --- | --- |
| `server/.../routes/AiRoutes.kt` (`"== Resumen del mes en curso =="`, línea 706; presupuestos con `monthlyLimit`, 693) | «Resumen del período en curso (quincena del 15 al 29 de octubre)»; presupuestos prorrateados. El prompt de sistema debe decir la frecuencia, para que «¿cuánto gasté este mes?» se conteste sumando con `totales_por_categoria` desde/hasta y no confundiendo mes con quincena. |
| `server/.../ai/ContextoDelPeriodo.kt` | «Cuotas al mes…», «al mes» de suscripciones y créditos: **N** (son mensuales de verdad). El bloque del período: **T**. |
| `server/.../ai/LasPreguntasSugeridas.kt` («¿Cómo voy este período?», presupuestos en 258) | genérico + límite prorrateado. |
| `server/.../ai/HerramientasDelAsistente.kt` (`proyectar_caja`, defaults `minusMonths`) | genérico; los rangos por defecto en meses están bien. |
| `server/.../ai/LoQueMoviPropone.kt`, `HechosDeLaPregunta.kt` («mensual») | revisar que «proponer_pago_hecho» use el checklist del período y el emparejamiento del mes de referencia. |

**Tamaño del mapa:** ~25 archivos de `:core`, ~30 del server, ~45 de `:shared`; la gran mayoría cambia una
firma (`periodoAnterior(p)` → `periodoAnterior(p, settings)`) o un texto. El trabajo de verdad está en
`PeriodoFinanciero.kt`, el mes de referencia en `reminders/`, y las pantallas de Ajustes, Primer uso, Tus
períodos, Presupuestos y Cierre.

---

## 2. Modelo propuesto

### 2.1 Ajustes

```text
PeriodSettings(
  frecuencia: FrecuenciaDelPeriodo = MENSUAL,   // MENSUAL | QUINCENAL | SEMANAL
  cutoffDay: Int = 1,                           // MENSUAL: el corte. QUINCENAL: el primer día (A).
  segundoDia: Int? = null,                      // QUINCENAL: el segundo día (B), A < B, 13 ≤ B − A ≤ 17
  diaDeLaSemana: Int? = null,                   // SEMANAL: 1 = lunes … 7 = domingo (ISO)
  iniciosPropios: Map<String, String> = {},     // excepciones a mano, clave = id del período
  siCaeEnFinDeSemana = ESE_DIA,                 // se aplica a cada arranque, en las tres frecuencias
)
```

Wire: `UserProfile.periodFrequency` (`String`, «MENSUAL» si falta o no se entiende — mismo patrón que
`periodWeekendRule`), `periodSecondDay`, `periodWeekday`. Un ajuste incompleto (QUINCENAL sin `segundoDia`)
se lee como MENSUAL: un dato roto nunca deja la app sin período (la misma filosofía que `inicioDelPeriodo`).

«El 15 y el último día del mes» = A 15, B 31: el 31 se recorta al largo del mes (30, 28/29), como hoy
`inicioDe`. «El último día hábil» = B 31 + `DIA_HABIL_ANTERIOR`.

### 2.2 Inicio y fin

Siempre **fin = arranque del siguiente** (`ventanaDe` no cambia) y siempre
`arranque efectivo = excepción válida ?: corteCorrido(arranqueNominal, regla)`. Solo cambia el
**arranque nominal**:

- **MENSUAL:** el de hoy (día de corte del mes anterior al nombre, o del mismo mes con corte 1).
- **QUINCENAL:** los días A y B de cada mes (recortados al largo del mes). La quincena que arranca el A
  termina el día antes del B; la del B, el día antes del A del mes siguiente.
- **SEMANAL:** cada fecha cuyo día de la semana es `diaDeLaSemana`; dura 7 días.

`periodoDe(fecha)`: candidato = el último arranque nominal ≤ fecha; como hoy, si hay regla de fin de
semana o excepciones se mira al vecino de cada lado y gana el que de verdad contiene la fecha.

**Festivos y fin de semana:** `corteCorrido` (Ley Emiliani + Semana Santa, `FestivosDeColombia.kt`) se
aplica a cada arranque nominal. Es monótona, así que el orden de los arranques se conserva; el
corrimiento máximo (4 días en Semana Santa) es menor que el hueco mínimo entre arranques (13 en
quincena, 7 en semana), así que dos arranques nunca caen el mismo día.

### 2.3 Cómo se identifica un período

| Frecuencia | Id (`prefijo`) | Ejemplo |
| --- | --- | --- |
| MENSUAL | `AAAA-MM` del mes que le da nombre (el mes en que termina) — **igual que hoy** | `2026-10` |
| QUINCENAL | `AAAA-MM-DD` del **arranque nominal** (antes de festivos y excepciones) | `2026-10-15`, `2026-10-30` |
| SEMANAL | `AAAA-MM-DD` del arranque nominal | `2026-10-30` (viernes) |

Por qué el arranque nominal y no el efectivo: el efectivo se mueve con un festivo o con una excepción, y el
id no puede moverse (es la clave de las excepciones, del cierre guardado, del correo enviado y del
descartado). Es la misma razón por la que el sello de un recurrente es el mes del vencimiento y no el
de su pago.

En código: `PeriodoFinanciero(year, month, dia = 0)`. `dia = 0` es mensual y conserva constructor,
serialización (el default no viaja) e igualdad para todos los usos actuales — incluidos los que usan
`PeriodoFinanciero` como **mes** (créditos, apartados, patrimonio). Para que esos usos no se confundan
con un período, conviene un `typealias Mes = PeriodoFinanciero` en esos archivos (cambio de nombre, cero
comportamiento). Orden: por `(year, month, dia)` y no por el `prefijo` (hoy `PeriodosRoutes` y
`LaPrimeraCuotaSinPagar` comparan cadenas; funciona dentro de una frecuencia, pero mejor no depender de
eso).

El nombre en pantalla no usa el id: es `nombreDelPeriodo` («15 – 29 oct», «30 oct – 5 nov»), la regla
única de la vuelta 75. Ver D8 por si se quiere «1ª quincena de octubre».

### 2.4 Excepciones a mano

Hoy una excepción es válida si cae **en el mismo mes** que el arranque por corte; si no, se ignora. Se
generaliza a **«dentro de N días del arranque nominal»**, con N elegido para que dos arranques vecinos
nunca se crucen aun con el corrimiento máximo por festivo:

| Frecuencia | Rango válido | Cuenta |
| --- | --- | --- |
| MENSUAL | el mismo mes (lo de hoy, sin cambio) | — |
| QUINCENAL | ± 5 días | hueco mínimo 13 − 5 − 4 > 0 |
| SEMANAL | ± 2 días | 7 − 2 − 4 > 0 |

«Empezar un período nuevo hoy» (`empezarElSiguienteHoy`) usa la misma regla y por eso se ofrece solo
cuando el sueldo llega pocos días antes. `MAX_INICIOS_PROPIOS` se queda en 120 y al escribir se podan las
de más de 2 años.

### 2.5 Compatibilidad

- **Datos existentes:** columnas nuevas nulas = MENSUAL. Ninguna fila cambia. El dueño (corte 25,
  `DIA_HABIL_ANTERIOR`, excepciones) sigue con los mismos ids `AAAA-MM`, las mismas ventanas y los mismos
  sellos. La prueba que lo garantiza: toda la suite actual pasa sin tocar una sola expectativa, más una
  prueba dorada que compara `ventanaDe/periodoDe` viejo contra nuevo para cada día de 2024–2030 con
  varias configuraciones mensuales.
- **Sellos de recurrentes, avisos y débitos descartados:** son `AAAA-MM` del vencimiento → no cambian
  con ninguna frecuencia.
- **Cierres** (`cierres_*`): la clave crece a `varchar(10)`; las filas viejas son `AAAA-MM` y siguen
  valiendo para quien sigue mensual.
- **Cambiar de frecuencia** recalcula toda la historia con la nueva (como hoy cambiar el corte), D7. Las
  excepciones se vacían (serían claves de otra forma); los cierres guardados con ids viejos quedan
  huérfanos e inofensivos.
- **APKs viejos:** no conocen `periodFrequency` y la ignoran (es un campo más del JSON), así que lo que
  calcule el **cliente** (Movimientos, Plan, Presupuestos) saldría por mes mientras lo que calcula el
  **server** (Hoy, Tus períodos, cierre) saldría por quincena. No hay control de versión mínima en Movi.
  Mitigación: la frecuencia solo se puede elegir desde un cliente que la entiende; Lina usa la web o un APK
  nuevo; el dueño no se ve afectado. Si se quiere blindar, una fase 0 puede mandar la versión del cliente
  en una cabecera y que Hoy diga «Actualiza Movi para ver tus quincenas».
- **Rutas con período en la URL** (`/api/periodos/{id}`, cierre `descartar`/`actualizar`): aceptan las dos
  formas; un id que no corresponde a la frecuencia del usuario → 404, como un id inexistente.

### 2.6 El mes de referencia

`PeriodSettings.mesDeReferencia(): PeriodSettings` =

- MENSUAL → él mismo (identidad: nada cambia).
- QUINCENAL → MENSUAL con `cutoffDay = A`, la misma regla de fin de semana y sin excepciones. Cada quincena
  cae entera dentro de un mes de referencia (las dos que arrancan en A y en B).
- SEMANAL → el mes de calendario (corte 1, misma regla de fin de semana).

Lo usan el motor de obligaciones (§1.3) y las agrupaciones mensuales. Es interno: el usuario no lo ve.

---

## 3. Decisiones de producto para el dueño

Cada una con la opción recomendada primero.

### D1 · Un pago mensual (arriendo, tarjeta, cuota) dentro de una quincena o semana

- **A (recomendada) — Entero en el período en que vence.** El arriendo del 5 resta completo en la quincena
  del 30 al 14. Por qué: es lo que pasa en la cuenta del banco, cumple la identidad del Disponible
  («cada peso una vez», ola E: lo que queda = Tu plata + por recibir − fijos pendientes − tarjeta), «Falta
  por pagar» sigue siendo plata real, y el checklist ya funciona así sin cambios. En quincena casa con cómo
  la gente paga: el arriendo del 5 sale de la quincena del 30.
- **B — Prorrateado** (cada quincena carga la mitad de cada fijo). Períodos parejos, pero el Disponible deja
  de cuadrar con el banco, el checklist mostraría medias filas que no se pueden pagar y «Falta por pagar»
  dejaría de ser lo que de verdad falta.
- **C — A + proponer un apartado** para los fijos grandes en SEMANAL. En una semana con sueldo de $400.000
  el arriendo de $1.200.000 deja esa semana en rojo y las anteriores «ricas». Movi propondría «Aparta
  $300.000 cada semana para el arriendo» con la función de **apartados** que ya existe (no mueve plata, ya
  baja «Te quedan»). Recomendada **como complemento de A en SEMANAL**, no en quincena.

### D2 · Presupuestos mensuales en períodos cortos

- **A (recomendada) — El presupuesto se escribe al mes y se mide prorrateado:** quincena = ½, semana =
  12/52 del mensual. La pantalla dice «$400.000 esta quincena · $800.000 al mes». Por qué: la gente piensa
  el mercado «al mes», la columna `monthly_limit` no miente, cambiar de frecuencia no cambia el sentido del
  número, y el dueño (mensual, factor 1) no ve nada distinto. Las dos quincenas suman exacto el mensual.
- **B — El presupuesto es por período** (lo que escribes es lo de la quincena). Es lo que hace el código hoy
  sin tocar nada, pero al cambiar de frecuencia un «$800.000» pasa a valer el doble al mes sin avisar.
- **C — El presupuesto se mide contra el mes de calendario** aunque el período sea quincena. Vuelve a haber
  dos ventanas en la misma pantalla, que es lo que el período vino a eliminar.

Con A, las propuestas de presupuesto (que hoy salen del gasto del período pasado) se llevan a mes.

### D3 · Qué muestra «Tus períodos»

- **A (recomendada) — Las quincenas/semanas, agrupadas bajo el mes** («Octubre» como encabezado con su
  subtotal Entró/Salió, debajo «1 – 14 oct» y «15 – 31 oct»). Por qué: el ritmo diario es la quincena, pero
  «¿cómo me fue en octubre?» sigue siendo la pregunta natural, y el encabezado la contesta sin otra pantalla.
- **B — Solo la lista de períodos.** Lo más simple; 36 semanas son una lista larga sin referencia.
- **C — Solo meses, con las quincenas dentro al abrir.** Esconde el período con el que se vive.

### D4 · El cierre del período

- **A (recomendada) — Cierre por período en Hoy siempre; correo y aviso del teléfono: quincenal sí (como
  hoy), semanal apagado por defecto** (se puede prender). Tarjeta en Hoy los primeros 3 días de la
  quincena / 2 de la semana (hoy son 5). Por qué: la tarjeta en Hoy cuesta poco y marca el ritmo; 52 correos
  al año son spam.
- **B — Solo un cierre mensual**, aunque el período sea quincena. Pierde lo que hace útil el cierre: hablar
  del período que se vivió.
- **C — Todo igual que hoy en cada período** (tarjeta 5 días, correo y aviso). En semanal la tarjeta
  ocuparía Hoy 5 de cada 7 días.

### D5 · Ahorro del período

- **A (recomendada) — El porcentaje queda igual (es % de lo que entró en el período, escala solo); el monto
  es «por período» y al cambiar de frecuencia la hoja propone la conversión** («Tu ahorro de $400.000 al mes
  pasa a $200.000 por quincena — ¿lo dejo así?»). Por qué: Ajustes ya dice «Ahorro de cada período», y lo
  que se aparta de cada quincena es como lo piensa quien cobra por quincena.
- **B — El monto se guarda al mes y se prorratea** (como D2-A). Coherente con presupuestos, pero el texto
  «de cada período» tendría que cambiar y la gente ahorra «de cada pago».

### D6 · Comparaciones con el anterior («vs. período pasado», «En qué se va», anomalías)

- **A (recomendada) — QUINCENAL contra la quincena homóloga del mes anterior** (la del 30 contra la del 30);
  **SEMANAL contra la semana anterior.** Por qué: los fijos grandes caen siempre en la misma quincena, así
  que comparar la del 15 con la del 30 marca diferencias estructurales («gastaste 80 % más» porque esta trae
  el arriendo), no de comportamiento. Las semanas son parejas entre sí.
- **B — Siempre contra el período inmediatamente anterior.** Lo que hace el código sin cambios; en quincena
  alterna «subiste/bajaste» cada 15 días por el arriendo.
- **C — Contra el promedio de los últimos N períodos.** Más estable, pero «vs. período pasado» deja de ser
  una cifra que se pueda verificar mirando un período.

### D7 · Cambiar de frecuencia y la historia

- **A (recomendada) — Toda la historia se recalcula con la nueva frecuencia**, como hoy al cambiar el día de
  corte. Por qué: una sola regla, ninguna línea de tiempo partida, y Lina es nueva (no tiene historia que
  perder). Los cierres guardados con la frecuencia vieja quedan sin uso.
- **B — La frecuencia tiene fecha «desde»**: los períodos de antes quedan mensuales y los de después
  quincenales. Más fiel para alguien que cambia de trabajo, pero toda función de período tendría que
  manejar un tramo de transición (un período «mixto») — mucho más riesgo para un caso raro.

### D8 · Cómo se nombra una quincena

- **A (recomendada) — Por fechas, como hoy** («15 – 29 oct»; la vuelta 75 dejó una sola regla de nombre).
- **B — «Quincena del 15 de octubre» / «1ª quincena de octubre».** Más coloquial, pero con festivos la
  quincena del 15 a veces arranca el 14 y el nombre mentiría.

### D9 · El sueldo semanal (y por qué semanal va después)

Los recurrentes son mensuales (`RecurringRule.dayOfMonth`). En QUINCENAL el sueldo son **dos reglas
mensuales** (día A y día B) y todo funciona: ingreso por recibir en el Disponible, salario esperado
corrido por festivo, caja hasta el próximo pago. En SEMANAL **no hay forma de decir «me pagan $400.000
cada viernes»**.

- **A (recomendada) — Entregar quincenal primero; semanal después con recurrentes semanales** (una regla con
  frecuencia SEMANAL y día de la semana; su sello sería `AAAA-MM-DD`, ver fase 3). Por qué: quincenal cubre
  la mayoría de los casos de Colombia sin tocar el motor de recurrentes.
- **B — Semanal ya, sin ingreso esperado:** el Disponible usa lo que entró de verdad (ya existe: saldo al
  empezar + entradas) y la caja proyectada solo llega al fin de la semana. Funciona, pero «Te quedan» el día
  antes del pago no cuenta el sueldo que viene y la caja no ve los próximos sueldos.
- **C — Estimar el sueldo semanal** con el promedio de las últimas semanas. Inventa una cifra que nadie
  confirmó; va contra «Movi no inventa».

### D10 · La columna «Esta semana» del Disponible

- **A (recomendada) — Se queda en QUINCENAL, se oculta en SEMANAL** (sería el mismo número que el período).
- **B — En SEMANAL, cambiarla por «Este mes».** Útil, pero mezcla una ventana mensual en una tarjeta que
  habla de la semana.

### D11 · Lo que se agrupa «por período» fuera de la plata del día (lo enviado a Caro, personas y comercios)

- **A (recomendada) — Por mes de referencia cuando la frecuencia no es mensual.** Por semana es ruido y lo
  que se quiere saber es «cuánto le mandé este mes».
- **B — Por período**, como todo lo demás.

### D12 · ¿Se ofrece a todos o solo a quien lo pide?

- **A (recomendada) — En Ajustes y en la guía de primer uso para todos**, con «Cada mes» elegido por defecto.
  Por qué: la regla «configurable desde la app» (otros usuarios no tienen a Claude al lado).
- **B — Escondido detrás de un interruptor de administración** mientras se prueba con Lina.

---

## 4. Plan por fases

| Fase | Qué entrega | Valor | Tamaño | Riesgo |
| --- | --- | --- | --- | --- |
| **0 · Cimientos sin cambio visible** | `FrecuenciaDelPeriodo` en `PeriodSettings` (siempre MENSUAL), `PeriodoFinanciero.dia`, comparador, `periodoAnterior/Siguiente(p, settings)` en las 46 llamadas, `mesDeReferencia()` (identidad) cableada en `reminders/`, `typealias Mes` en créditos/apartados/patrimonio, textos «octubre» → `nombreDelPeriodo`, `varchar(10)` en `cierres_*`. Pruebas: dorada mensual 2024–2030 y la de propiedad «cada día cae en exactamente un período». | Ninguno para el usuario; deja el terreno firme. | **M** (1 PR, mayormente mecánico, ~100 archivos tocados con cambios de una línea) | **Bajo-medio**: lo protege la suite actual; el DDL de `varchar(10)` es el único punto delicado. |
| **1 · Quincenal** | Columnas y campos del perfil, validación en `PUT /api/users/me`, arranques A/B con festivos y excepciones ±5, «¿Cada cuánto te pagan?» en Ajustes y en la guía (salario = dos reglas), obligaciones por mes de referencia de verdad, presupuestos prorrateados (D2), ahorro (D5), Tus períodos agrupado (D3), cierre por quincena (D4), homólogo (D6), umbrales escalados (anomalías, pequeños, `MAX_PERIODOS`, gasto del día a día), backfill de SMS con piso de 35 días, Movi AI con la frecuencia en el contexto. Web primero; APK cuando esté estable. | **Lina puede usarlo.** Cubre el caso más común en Colombia. | **L** (3-4 PRs: core + server, Ajustes/guía, pantallas, Movi AI) | **Medio**: el motor de ocurrencias con dos relojes es donde se esconden los bugs (pago vencido que desaparece, pago adelantado que no empareja). Mitigación: casos de prueba con el calendario real de 2026-2027 (Semana Santa, 25-dic, quincenas de febrero). |
| **2 · Semanal (versión B de D9)** | Arranques por día de la semana, excepciones ±2, columna «Esta semana» oculta, cierre semanal sin correo, caja hasta el fin de la semana, propuesta de apartados para fijos grandes (D1-C). | Quien cobra por semana ve su semana. Sin sueldo esperado. | **M** (1-2 PRs) | **Medio**: lo semanal estira todos los umbrales; la columna de semana y la caja necesitan revisión a ojo. |
| **3 · Recurrentes semanales** (opcional) | `RecurringRule` con frecuencia y día de la semana, sello `AAAA-MM-DD` (`recurring_occurrences.period` → `varchar(10)`), `dueDateFor`/emparejamiento por semana; habilita el sueldo semanal (D9-A) y gastos semanales. Abre la puerta a «catorcenal» (cada 2 semanas con fecha ancla). | Semanal completo. | **L** (2-3 PRs) | **Alto**: toca el motor de recurrentes, el más cargado de decisiones del dueño (#416, gracia, sellos). |

**Tamaño total:** para tener quincenal usable (fases 0 + 1) ≈ **L**, unos 5 PRs; con semanal básico (fase 2)
≈ **L-XL**, unos 7 PRs; con recurrentes semanales (fase 3) ≈ **XL**, unos 10 PRs.

### Pruebas que no pueden faltar

1. **Dorada mensual**: para corte 1, 25 (+ `DIA_HABIL_ANTERIOR`) y 31, y con las excepciones reales del
   dueño, `periodoDe`/`ventanaDe`/`prefijo` idénticos a los de hoy para cada día de 2024–2030.
2. **Propiedad**: para toda configuración (A/B en 1..31 con 13 ≤ B − A ≤ 17, día de la semana 1..7, las tres
   reglas de fin de semana, excepciones en el borde del rango), cada día de 2024–2030 cae en exactamente un
   período, los períodos son contiguos y ninguno queda vacío.
3. **Obligaciones**: arriendo del 5 sin pagar sigue en «pendientes de períodos anteriores» toda la quincena
   siguiente; tarjeta pagada 3 días antes en la semana anterior se empareja; #416 sigue igual.
4. **Presupuestos y ahorro**: dos quincenas suman exacto el límite mensual; un usuario mensual ve los mismos
   números.

---

## 5. Fuera de alcance

- Períodos de largo libre («cada 10 días»), catorcenal (queda abierto para la fase 3), cambio de frecuencia
  con fecha «desde» (D7-B).
- Presupuestos con una cifra distinta por período.
- Prorrateo de fijos en el checklist (D1-B).


## Decisiones del dueño (2-nov-2026)

- **D1:** un pago mensual entra **entero** en el período en que vence.
- **D2:** los presupuestos se escriben al mes y se miden **prorrateados** (½ por quincena).
- **D6:** «vs. período pasado» compara contra **la misma quincena del mes anterior**.
- **Alcance:** **quincenal primero**; semanal después, con recurrentes semanales.
- El resto (D3-D5, D7-D12) va con la recomendación de este documento.
