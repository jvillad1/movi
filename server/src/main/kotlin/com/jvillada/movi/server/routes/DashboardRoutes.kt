package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.cuentasConSaldo
import com.jvillada.movi.server.balance.hayMovimientosEnOtraMoneda
import com.jvillada.movi.server.fx.FxRateService
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.server.balance.dismissedCardPaymentEventIds
import com.jvillada.movi.server.balance.looksLikeCardPayment
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.reminders.loadOccurredBy
import com.jvillada.movi.server.reminders.loadOccurrenceRows
import com.jvillada.movi.server.reminders.parteFijaDelChecklist
import com.jvillada.movi.server.reminders.emparejadasComoSellos
import com.jvillada.movi.server.reminders.periodosPorRegla
import com.jvillada.movi.server.reminders.unirOcurridos
import com.jvillada.movi.server.reminders.cuotasDelChecklistPagadas
import com.jvillada.movi.server.reminders.loadCreditRulePairs
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.gastoVariablePorDia
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.shared.model.CuentaDelDisponible
import com.jvillada.movi.shared.model.SumaDeMovimientos
import com.jvillada.movi.shared.model.normalizarCondicion
import com.jvillada.movi.shared.model.plataDelPeriodo
import com.jvillada.movi.shared.model.PlataDelPeriodo
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.pagosDeDeudaFueraDelChecklist
import com.jvillada.movi.shared.model.saldoDeTuPlata
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.shared.model.capturaDeSms
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.isReservedCategory
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.currentPeriodWindow
import com.jvillada.movi.server.time.cutoffDayOf
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.periodoDe
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Transaction
import com.jvillada.movi.shared.model.FinancialEvent
import java.time.LocalDate
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList

/**
 * `GET /api/dashboard/summary?scope=SELF|FAMILY` — los números del Inicio, ya reducidos.
 *
 * Existe porque la pantalla más usada se bajaba tres colecciones enteras para sacar tres
 * cifras: `GET /api/sms` para contar los pendientes, `GET /api/events/card-payment-candidates`
 * para un `.size` y `GET /api/events/by-day` (toda la historia) para el gasto del mes por
 * categoría. Con meses de uso real eso crece lineal; acá cada cifra se acota en SQL (el mes,
 * el estado, el tipo de cuenta) y lo que no se puede expresar en SQL sin reimplementar una
 * regla de negocio (`isCashFlow`, `looksLikeCardPayment`) se aplica en memoria sobre el
 * subconjunto ya acotado, con LA MISMA función que usa el resto del server.
 *
 * `scope` se valida y se devuelve igual que en `finance-summary` — y, igual que allí, hoy no
 * filtra nada: no hay modelo de familia todavía.
 */
fun Route.dashboardRoutes() {
    get("/api/dashboard/summary") {
        val uid = call.userId()
        val raw = call.request.queryParameters["scope"] ?: "SELF"
        val scope = runCatching { Scope.valueOf(raw.uppercase()) }.getOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown scope: $raw")

        // La ventana del PERÍODO del usuario, igual que `finance-summary` (FinanceRoutes.kt) y
        // que las rutas de categorías. Con corte 1 —el default— da exactamente el mes civil de
        // Bogotá, o sea lo mismo que decía esta ruta antes.
        //
        // **Esta ruta se quedó afuera cuando nació el período y nadie lo notó**, porque con corte
        // 1 las dos cuentas dan igual. El día que el dueño puso corte 25 (su día de pago) las
        // cifras se partieron en dos: Presupuestos pinta las barras con el `spentByCategory` que
        // sale de acá —mes civil— mientras la lista de movimientos de la misma pantalla usa la
        // ventana del período. Un presupuesto de «Mercado» con su único gasto el 27 de agosto
        // decía «$0 de $2.000.000» y otro de «Fútbol» contaba 2 de sus 6 movimientos, con el
        // renglón «este dispositivo tiene $121.210 que el total de arriba todavía no cuenta»
        // culpando a una desincronización que no existía: las dos mitades miraban meses
        // distintos. Es exactamente la contradicción que `currentPeriodWindow` vino a eliminar.
        // El período ENTERO —el corte y los meses que arrancaron otro día—, leído una sola vez y
        // usado para las dos cosas: la ventana que suma y el rótulo que la nombra. Antes acá se
        // leía solo el corte, así que un período con arranque propio movía Movimientos y dejaba
        // al Inicio contando otra ventana sobre la misma plata.
        val periodo = ajustesDePeriodoDe(uid)
        val (monthStart, monthEnd) = currentPeriodWindow(periodo)
        // Segunda lectura del reloj (`currentPeriodWindow` hace la suya): entre las dos podría
        // cruzarse la medianoche del corte y dejar el rótulo nombrando un período distinto del
        // que se sumó. Es una ventana de microsegundos una vez al mes y el rótulo hoy no lo lee
        // nadie; se deja anotado en vez de arrastrar un instante por la firma de una función que
        // comparten cuatro rutas.
        val ahora = AppClock.now().toInstant().toEpochMilli()
        // El período se llama por el mes en que TERMINA (ver PeriodoFinanciero en :core), no por
        // el mes en que cae hoy: con corte 25, el 27 de agosto ya es «septiembre». Con corte 1
        // devuelve el mes civil, igual que antes.
        val month = periodoDe(ahora, periodo).prefijo

        // Las cuotas de los créditos (reglas sintéticas), para saber qué pago de cuota ya está en
        // los fijos del checklist. Una consulta (créditos con su cuenta), fuera del `dbQuery` de
        // abajo porque abre el suyo.
        val reglasDeCredito = loadCreditRulePairs(uid).map { it.first }
        // Para estimar en pesos lo que haya en dólares al armar el patrimonio. Fuera del `dbQuery`
        // porque puede pegarle a la red y no debe alargar la transacción. **Y solo si hace falta**:
        // esta es la pantalla más abierta de la app, `FxRateService` no guarda los fallos (con
        // datos.gov.co caído, cada llamada espera su timeout de 5 s) y hasta hoy el resumen no
        // dependía de ningún servicio de afuera. Sin un solo movimiento en otra moneda la tasa no
        // se usa (ver `estimatedTotalCop`), así que no se pide.
        val usdToCop = if (dbQuery { hayMovimientosEnOtraMoneda(uid) }) FxRateService.usdToCop() else 0.0

        val summary = dbQuery {
            val accountTypeById = accountTypesFor(uid)
            val voidedIds = VoidEvents.selectAll()
                .where { VoidEvents.userId eq uid }
                .map { it[VoidEvents.originalEventId] }
                .toSet()

            val (income, spentByCategory) = monthCashFlow(uid, monthStart, monthEnd, voidedIds, accountTypeById)

            // **Una sola lectura de `sms_messages` para las tres cifras que salen de ahí.**
            //
            // Antes acá había un `COUNT(*)` de los pendientes y nada más. Ahora el Inicio además
            // tiene que poder decir «Movi nunca ha recibido un mensaje de tu banco» —ver
            // `CapturaDeSms` en :core, y el defecto que la hizo nacer— y eso necesita el total y
            // el `time` más reciente. Tres agregados serían tres consultas; dos columnas de las
            // mismas filas son una. El conjunto está acotado por usuario y son mensajes de banco,
            // no la historia de movimientos: bastante menos de lo que ya recorren en memoria
            // `cardPaymentCandidateCount` y `usedCategories` acá al lado.
            //
            // El `time` es un varchar libre y el criterio de «cuál es el último» vive en :core,
            // en la MISMA función que usa la bandeja de SMS del cliente: dos superficies que
            // ordenan por su cuenta terminan nombrando mensajes distintos.
            val filasDeSms = SmsMessages
                .select(SmsMessages.time, SmsMessages.state)
                .where { SmsMessages.userId eq uid }
                .map { it[SmsMessages.time] to it[SmsMessages.state] }
            val captura = capturaDeSms(filasDeSms.map { it.first })
            val disponible = disponibleDelServidor(
                uid = uid,
                hoy = epochMillisToAppDate(ahora),
                periodo = periodo,
                monthStart = monthStart,
                monthEnd = monthEnd,
                voidedIds = voidedIds,
                reglasDeCredito = reglasDeCredito,
            )
            val plata = disponible.plata

            DashboardSummary(
                scope = scope,
                month = month,
                monthIncome = income,
                monthSpent = spentByCategory.values.sum(),
                spentByCategory = spentByCategory,
                cardPaymentCandidates = cardPaymentCandidateCount(uid, voidedIds, accountTypeById),
                pendingSms = filasDeSms.count { (_, state) -> state == SMS_STATE_PENDING },
                smsTotal = captura.total,
                smsLastAt = captura.ultimo,
                smsAlertMuted = Users.select(Users.smsAlertMuted)
                    .where { Users.id eq uid }
                    .firstOrNull()?.get(Users.smsAlertMuted) ?: false,
                usedCategories = usedCategories(uid, ahora, voidedIds),
                gastoVariablePorDia = disponible.gastoVariablePorDia,
                saldoTuPlataAlInicio = plata.saldoAlInicio,
                entradasDelPeriodo = plata.entradas,
                guardadoDelPeriodo = plata.guardado,
                pagosDeDeudaFueraDelChecklist = disponible.pagosDeDeudaFueraDelChecklist,
                // **El patrimonio honesto**, con la casa y el carro adentro: la MISMA regla que usa
                // el cliente sobre la lista de cuentas (`patrimonioDe`, en :core), sobre los mismos
                // saldos que esa lista (ver `cuentasConSaldo`). No hay una segunda cuenta acá.
                patrimonio = patrimonioDe(cuentasConSaldo(uid, voidedIds, usdToCop)),
                // La cuenta que «Agregar» debería sugerir primero, en vez de la primera por orden
                // alfabético. Ver `cuentaMasUsada` acá abajo.
                cuentaMasUsada = cuentaMasUsada(uid, ahora, voidedIds),
            )
        }
        call.respond(summary)
    }
}

/**
 * **Lo que el server manda para la tarjeta «Disponible»**, fuera de la ruta para poder probar la
 * identidad entera con un «hoy» fijo (`AppClock` no se mueve desde una prueba). El cliente le suma
 * los fijos del checklist (ver `disponibleDelPeriodo` en la UI): lo que queda = saldo al inicio +
 * entradas − guardado − otros pagos de deuda − fijos − gasto variable.
 */
internal class DisponibleDelServidor(
    /** Lo que había en «Tu plata» al empezar el período y lo que le entró de afuera. Ver `PlataDelPeriodo.kt`. */
    val plata: PlataDelPeriodo,
    /**
     * El gasto variable del período, día por día: los mismos movimientos que suman «Gastos» (vivos,
     * flujo de caja, sin «Por confirmar», en pesos) menos las cuotas de crédito y la parte de cada
     * movimiento que paga un ítem del checklist (`PagosDelChecklist.kt`). La regla vive en :core
     * (`gastoVariablePorDia`).
     */
    val gastoVariablePorDia: Map<String, Long>,
    /**
     * Lo que salió de Tu plata a una deuda sin que los fijos ni el variable lo cuenten (ver
     * `pagosDeDeudaFueraDelChecklist` en :core). Lo que ya está en los fijos: la parte que un
     * recurrente reclamó y la cuota de un crédito del checklist.
     */
    val pagosDeDeudaFueraDelChecklist: Long,
)

/** Arma [DisponibleDelServidor] para el período `[monthStart, monthEnd)` que contiene [hoy]. */
internal fun Transaction.disponibleDelServidor(
    uid: String,
    hoy: LocalDate,
    periodo: PeriodSettings,
    monthStart: Long,
    monthEnd: Long,
    voidedIds: Set<String>,
    reglasDeCredito: List<RecurringRule>,
): DisponibleDelServidor {
    val eventosDelPeriodo = loadEventsBetween(uid, monthStart, monthEnd)
    val cuentas = cuentasDelDisponible(uid)
    val plata = plataDelPeriodo(
        saldoAlInicio = saldoDeTuPlata(sumasAntesDe(uid, monthStart, voidedIds, cuentas), cuentas),
        eventos = eventosDelPeriodo,
        cuentas = cuentas,
    )
    val parteFija = parteFijaDelDisponible(uid, hoy, periodo, eventosDelPeriodo)
    return DisponibleDelServidor(
        plata = plata,
        gastoVariablePorDia = gastoVariablePorDia(
            eventos = eventosDelPeriodo,
            parteFija = parteFija,
            diaDe = { epochMillisToAppDateString(it) },
        ),
        pagosDeDeudaFueraDelChecklist = pagosDeDeudaFueraDelChecklist(
            eventos = eventosDelPeriodo,
            cuentas = cuentas,
            enLosFijos = parteFija + cuotasDelChecklistPagadas(reglasDeCredito, eventosDelPeriodo, hoy, periodo),
        ),
    )
}

/**
 * **id de movimiento → la parte que paga un fijo del checklist**, para la tarjeta «Disponible» del
 * Inicio (ver `PagosDelChecklist.kt`).
 *
 * Afuera de la ruta para poder probarla con un «hoy» fijo: lo que decide si un pago sale del
 * variable depende del día (un vencimiento todavía en gracia, un período que arrancó por excepción)
 * y `AppClock` no se puede mover desde una prueba.
 */
internal fun Transaction.parteFijaDelDisponible(
    uid: String,
    hoy: LocalDate,
    periodo: PeriodSettings,
    eventosDelPeriodo: List<FinancialEvent>,
): Map<String, Long> {
    val sellos = loadOccurrenceRows(uid)
    // Lo que Movi emparejó solo cuenta como un sello con su movimiento, por dos cosas:
    //  - rueda el vencimiento igual que en `/upcoming`, que es de donde el cliente saca su
    //    checklist: sin esto, en la gracia el server no listaba el vencimiento que el cliente sí
    //    resta como fijo;
    //  - reserva el movimiento: el arriendo de septiembre pagado tarde no pasa a ser el pago de
    //    otro ítem pendiente del período.
    // Sacarlo del variable solo lo hace si es el sello del ítem de ESTE período: el pago de una
    // ocurrencia anterior no está en los fijos de este Disponible (ver `PagosDelChecklist.kt`).
    val emparejadas = emparejadasComoSellos(uid, hoy, periodo)
    return parteFijaDelChecklist(
        reglas = RecurringRules.selectAll()
            .where { RecurringRules.userId eq uid }
            .map { it.toRule() },
        sellos = sellos + emparejadas,
        ocurridos = unirOcurridos(loadOccurredBy(uid, sellos), emparejadas.periodosPorRegla()),
        eventos = eventosDelPeriodo,
        hoy = hoy,
        settings = periodo,
    )
}

/**
 * Ingresos del mes y egresos del mes por categoría, en COP y solo flujo de caja — la misma
 * regla que `finance-summary` (ingresos/egresos) y que `spentByCategoryForMonth` del cliente,
 * ahora sobre las filas del mes nada más. Solo se piden las columnas que la regla necesita.
 *
 * `internal` y no `private` porque las propuestas de presupuesto (`GET /api/budgets/propuestas`,
 * en FinanceRoutes) suman el gasto del período ANTERIOR con esta misma regla: si tuvieran la
 * suya, la propuesta y la barra del presupuesto recién creado podrían contar cosas distintas.
 */
internal fun Transaction.monthCashFlow(
    uid: String,
    monthStart: Long,
    monthEnd: Long,
    voidedIds: Set<String>,
    accountTypeById: Map<String, AccountType>,
): Pair<Long, Map<String, Long>> {
    val rows = Events.select(Events.id, Events.accountId, Events.type, Events.amount, Events.category, Events.reconciliationStatus)
        .where {
            (Events.userId eq uid) and
                (Events.currency eq "COP") and
                (Events.timestamp greaterEq monthStart) and
                (Events.timestamp less monthEnd)
        }
        .filterNot { it[Events.id] in voidedIds }
        // Lo que espera en «Por confirmar» no suma — ni en el Inicio ni en las barras de
        // Presupuestos, que leen este mismo `spentByCategory`. Misma regla que el chip «Gastos» y
        // que `spentByCategoryForPeriod` del cliente: las dos mitades tienen que coincidir.
        .filterNot { esperaEnPorConfirmar(it[Events.reconciliationStatus]) }
        .filter { row ->
            val accountType = accountTypeById[row[Events.accountId]]
            accountType == null ||
                isCashFlow(accountType, TransactionType.valueOf(row[Events.type]), row[Events.category])
        }
    val income = rows.filter { it[Events.type] == TransactionType.INCOME.name }.sumOf { it[Events.amount] }
    val spentByCategory = rows.filter { it[Events.type] == TransactionType.EXPENSE.name }
        .groupBy { it[Events.category] }
        .mapValues { (_, r) -> r.sumOf { it[Events.amount] } }
    return income to spentByCategory
}

/** Todas las cuentas del usuario con lo que el Disponible necesita saber de cada una. Una consulta. */
private fun Transaction.cuentasDelDisponible(uid: String): Map<String, CuentaDelDisponible> =
    Accounts.select(Accounts.id, Accounts.type, Accounts.conditionedTo, Accounts.assetKind)
        .where { Accounts.userId eq uid }
        .mapNotNull { row ->
            val tipo = runCatching { AccountType.valueOf(row[Accounts.type]) }.getOrNull() ?: return@mapNotNull null
            // `normalizarCondicion`: el mismo helper con el que la cuenta sale a la app, para que
            // una condición en blanco no deje la cuenta fuera de Tu plata solo de este lado.
            // `esBien`: la casa no es plata que tenías al empezar el período (ver `Account.bien`).
            row[Accounts.id] to CuentaDelDisponible(
                tipo,
                normalizarCondicion(row[Accounts.conditionedTo]),
                esBien = row[Accounts.assetKind] != null,
            )
        }
        .toMap()

/**
 * Los movimientos en pesos de las cuentas de «Tu plata» **anteriores a [antesDe]**, sumados por
 * cuenta y tipo en SQL: una sola consulta que devuelve unas pocas filas, sin traer la historia ni
 * recorrer cuenta por cuenta. Sin anulados; los «Por confirmar» sí, porque el saldo los incluye.
 */
private fun Transaction.sumasAntesDe(
    uid: String,
    antesDe: Long,
    voidedIds: Set<String>,
    cuentas: Map<String, CuentaDelDisponible>,
): List<SumaDeMovimientos> {
    val deTuPlata = cuentas.filterValues { it.esTuPlata }.keys.toList()
    if (deTuPlata.isEmpty()) return emptyList()
    val total = Events.amount.sum()
    return Events.select(Events.accountId, Events.type, total)
        .where {
            val base = (Events.userId eq uid) and
                (Events.currency eq "COP") and
                (Events.timestamp less antesDe) and
                (Events.accountId inList deTuPlata)
            if (voidedIds.isEmpty()) base else base and (Events.id notInList voidedIds.toList())
        }
        .groupBy(Events.accountId, Events.type)
        .mapNotNull { row ->
            val tipo = runCatching { TransactionType.valueOf(row[Events.type]) }.getOrNull() ?: return@mapNotNull null
            SumaDeMovimientos(row[Events.accountId], tipo, row[total] ?: 0L)
        }
}

/**
 * Ola 9 · A2: las categorías que este usuario ya usó, con los tipos con los que las usó.
 *
 * Va DENTRO de esta respuesta y no en una ruta propia porque el Inicio ya la pide y es donde la
 * app arranca: así «Agregar» ofrece las categorías propias del dueño sin agregar un viaje —
 * justamente en la pantalla de la que se quejó por disparar diez llamadas. El costo es un
 * `DISTINCT` que devuelve unas decenas de filas (categorías, no movimientos), y no se paginan
 * meses de historia para eso.
 *
 * Sin filtrar por mes a propósito: una categoría propia sigue siendo suya aunque no la haya
 * usado este mes. Tampoco se excluyen las anuladas ni las categorías reservadas — el cliente ya
 * las filtra en un solo lugar (`UsedCategoriesCache.recordAll`, por donde pasan todos sus caminos
 * de entrada), y duplicar esa regla acá sería una segunda copia que puede desincronizarse.
 *
 * **Ola 10 — acá también viajan las preferencias de «Más → Categorías»** (`category_prefs`:
 * escondida y tipo fijado). Sin esto, esconder una categoría o fijarle el tipo no cambiaría nada
 * en el único lugar donde se nota —el campo de categoría de «Agregar»—, que es para lo que
 * sirven. Se emite además una fila por cada categoría CON preferencia aunque no tenga ningún
 * movimiento: esconder una del catálogo que nunca usó es el caso normal, y esa fila viaja con
 * `types` vacío.
 *
 * **Ola A — [UsedCategory.usosRecientes] es la excepción a "sin filtrar por mes/anulados/
 * reservadas" de arriba.** Ese campo sí mira una ventana (60 días) y sí excluye los anulados: no
 * describe "¿la conozco?" sino "¿la sigue usando?", y ahí un movimiento borrado o de hace un año
 * no cuenta.
 */
private fun Transaction.usedCategories(uid: String, ahora: Long, voidedIds: Set<String>): List<UsedCategory> {
    // Misma lectura de `category_prefs` que usa `GET /api/categories` (ver
    // `CategoryRoutes.preferenciasDeCategorias`): una sola función para las dos rutas, para que
    // no vuelvan a desincronizarse las columnas que acarrea cada una.
    val prefs = preferenciasDeCategorias(uid)

    val porUso = Events.select(Events.category, Events.type)
        .where { Events.userId eq uid }
        .withDistinct()
        .map { it[Events.category].trim() to it[Events.type] }
        .filter { (category, _) -> category.isNotEmpty() }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, types) ->
            types.mapNotNull { t -> runCatching { TransactionType.valueOf(t) }.getOrNull() }.distinct()
        }

    // Ola A: cuántos movimientos recientes respaldan cada categoría, para que el cliente pueda
    // ordenar u ofrecer las que el dueño de verdad usa últimamente y no solo alguna vez en la
    // historia. Una sola consulta agregada (no una por categoría): se acota por fecha en SQL y
    // se agrupa en memoria, igual que el resto de esta ruta. Sin anulados, cualquier tipo.
    val hace60Dias = ahora - 60L * UN_DIA_MS
    val usosRecientesPorCategoria = Events.select(Events.id, Events.category, Events.timestamp)
        .where { (Events.userId eq uid) and (Events.timestamp greaterEq hace60Dias) }
        .filterNot { it[Events.id] in voidedIds }
        .groupingBy { it[Events.category].trim() }
        .eachCount()

    val nombres = porUso.keys + prefs.keys.filter { it.isNotEmpty() }
    return nombres
        .map { nombre ->
            val pref = prefs[nombre]
            UsedCategory(
                name = nombre,
                types = porUso[nombre].orEmpty(),
                hidden = pref?.hidden ?: false,
                pinnedType = pref?.pinnedType,
                icono = pref?.icono,
                color = pref?.color,
                usosRecientes = usosRecientesPorCategoria[nombre] ?: 0,
            )
        }
        .sortedBy { it.name.lowercase() }
}

/**
 * Ola A: **la cuenta con más gastos en los últimos 30 días**, para que «Agregar» arranque ahí en
 * vez de la primera por orden alfabético (ver el KDoc de [DashboardSummary.cuentaMasUsada]).
 *
 * No anulados y fuera las categorías reservadas ([isReservedCategory] ya incluye
 * `CARD_PAYMENT_CATEGORY`): un pago de tarjeta o un traspaso no dicen en qué cuenta el dueño
 * ANOTA sus gastos, dicen otra cosa. Empate en cantidad → la cuenta del movimiento más reciente.
 * Sin gastos en la ventana → `null`, y el cliente cae al orden de siempre.
 */
private fun Transaction.cuentaMasUsada(uid: String, ahora: Long, voidedIds: Set<String>): String? {
    val hace30Dias = ahora - 30L * UN_DIA_MS
    val gastos = Events.select(Events.id, Events.accountId, Events.category, Events.timestamp)
        .where {
            (Events.userId eq uid) and
                (Events.type eq TransactionType.EXPENSE.name) and
                (Events.timestamp greaterEq hace30Dias)
        }
        .filterNot { it[Events.id] in voidedIds }
        .filterNot { isReservedCategory(it[Events.category]) }

    return gastos
        .groupBy { it[Events.accountId] }
        .mapValues { (_, movs) -> movs.size to movs.maxOf { it[Events.timestamp] } }
        .entries
        .maxWithOrNull(compareBy({ it.value.first }, { it.value.second }))
        ?.key
}

/** Un día, en milisegundos — la unidad de las ventanas «últimos N días» de esta ruta. */
private const val UN_DIA_MS = 24L * 60 * 60 * 1000

/**
 * Cuántos devolvería `GET /api/events/card-payment-candidates` — mismo filtro (egreso, cuenta
 * de activo, no anulado, no descartado, `looksLikeCardPayment`), pero el SQL ya acota a los
 * egresos de cuentas de activo y solo se leen descripción y categoría.
 */
private fun Transaction.cardPaymentCandidateCount(
    uid: String,
    voidedIds: Set<String>,
    accountTypeById: Map<String, AccountType>,
): Int {
    val assetTypes = setOf(AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS, AccountType.INVESTMENT)
    val assetAccountIds = accountTypeById.filterValues { it in assetTypes }.keys.toList()
    if (assetAccountIds.isEmpty()) return 0
    val excluded = (voidedIds + dismissedCardPaymentEventIds(uid)).toList()
    return Events.select(Events.description, Events.category)
        .where {
            val base = (Events.userId eq uid) and
                (Events.type eq TransactionType.EXPENSE.name) and
                (Events.accountId inList assetAccountIds)
            if (excluded.isEmpty()) base else base and (Events.id notInList excluded)
        }
        .count { looksLikeCardPayment(it[Events.description], it[Events.category]) }
}
