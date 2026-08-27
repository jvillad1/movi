package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.balance.dismissedCardPaymentEventIds
import com.jvillada.movi.server.balance.looksLikeCardPayment
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.shared.model.isCashFlow
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.monthWindowOf
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll
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

        // Misma convención de mes que `finance-summary` (FinanceRoutes.kt): el mes civil en la
        // zona de la app (AppClock, Bogotá), del primer milisegundo del mes al primero del
        // siguiente. Las dos rutas pasan por currentMonthWindow(): es el mismo "mes" para el usuario.
        val now = AppClock.now()
        val (monthStart, monthEnd) = monthWindowOf(now)
        val month = "${now.year}-${now.monthValue.toString().padStart(2, '0')}"

        val summary = dbQuery {
            val accountTypeById = accountTypesFor(uid)
            val voidedIds = VoidEvents.selectAll()
                .where { VoidEvents.userId eq uid }
                .map { it[VoidEvents.originalEventId] }
                .toSet()

            val (income, spentByCategory) = monthCashFlow(uid, monthStart, monthEnd, voidedIds, accountTypeById)

            DashboardSummary(
                scope = scope,
                month = month,
                monthIncome = income,
                monthSpent = spentByCategory.values.sum(),
                spentByCategory = spentByCategory,
                cardPaymentCandidates = cardPaymentCandidateCount(uid, voidedIds, accountTypeById),
                pendingSms = SmsMessages.select(SmsMessages.id.count())
                    .where { (SmsMessages.userId eq uid) and (SmsMessages.state eq SMS_STATE_PENDING) }
                    .single()[SmsMessages.id.count()].toInt(),
                usedCategories = usedCategories(uid),
            )
        }
        call.respond(summary)
    }
}

/**
 * Ingresos del mes y egresos del mes por categoría, en COP y solo flujo de caja — la misma
 * regla que `finance-summary` (ingresos/egresos) y que `spentByCategoryForMonth` del cliente,
 * ahora sobre las filas del mes nada más. Solo se piden las columnas que la regla necesita.
 */
private fun Transaction.monthCashFlow(
    uid: String,
    monthStart: Long,
    monthEnd: Long,
    voidedIds: Set<String>,
    accountTypeById: Map<String, AccountType>,
): Pair<Long, Map<String, Long>> {
    val rows = Events.select(Events.id, Events.accountId, Events.type, Events.amount, Events.category)
        .where {
            (Events.userId eq uid) and
                (Events.currency eq "COP") and
                (Events.timestamp greaterEq monthStart) and
                (Events.timestamp less monthEnd)
        }
        .filterNot { it[Events.id] in voidedIds }
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
 */
private fun Transaction.usedCategories(uid: String): List<UsedCategory> {
    val prefs = CategoryPrefs.selectAll()
        .where { CategoryPrefs.userId eq uid }
        .associate { it[CategoryPrefs.name].trim() to (it[CategoryPrefs.hidden] to it[CategoryPrefs.pinnedType]) }

    val porUso = Events.select(Events.category, Events.type)
        .where { Events.userId eq uid }
        .withDistinct()
        .map { it[Events.category].trim() to it[Events.type] }
        .filter { (category, _) -> category.isNotEmpty() }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, types) ->
            types.mapNotNull { t -> runCatching { TransactionType.valueOf(t) }.getOrNull() }.distinct()
        }

    val nombres = porUso.keys + prefs.keys.filter { it.isNotEmpty() }
    return nombres
        .map { nombre ->
            val pref = prefs[nombre]
            UsedCategory(
                name = nombre,
                types = porUso[nombre].orEmpty(),
                hidden = pref?.first ?: false,
                pinnedType = pref?.second,
            )
        }
        .sortedBy { it.name.lowercase() }
}

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
