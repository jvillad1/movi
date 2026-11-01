package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ExtractoPorSubir
import com.jvillada.movi.shared.model.PREFIJO_DE_LA_HUELLA_DEL_EXTRACTO
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.model.extractoPorSubirAl
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate

/**
 * # «¿Ya bajaste el extracto de Nu de octubre?» (vuelta 94)
 *
 * `GET /api/extractos-por-subir`: las tarjetas cuyo corte pasó hace tres días
 * o más (`DIAS_PARA_PEDIR_EL_EXTRACTO`) sin que se subiera su extracto. **Se deriva en cada lectura** y no escribe nada, como los
 * débitos automáticos; la regla entera es `extractoPorSubir` (`:core`). Hoy la pide aparte (nunca en
 * `/api/dashboard/summary`) y el teléfono la avisa con `AvisoDeExtractoWorker`. «Ya no» es el `POST
 * /api/anomalias/descartar` de siempre con la huella de la propuesta.
 *
 * - **Qué tarjetas**: las cuentas `CREDIT_CARD` en pesos con día de corte cargado (`card_terms.cutoff_day`).
 *   La hermana en dólares de una tarjeta («… 3684 USD») no pide nada: su extracto es el mismo papel.
 *   Los créditos no tienen fecha de corte en Movi (`CreditTerms` solo sabe el día de pago): sin corte,
 *   no se propone nada.
 * - **Qué es «se subió»**: un documento de tipo extracto colgado de esa cuenta, o un importe de extracto
 *   contra ella (`statement_imports`), el día del corte o después.
 * - **Qué es «se usó»**: movimientos en el mes que cerró ese corte, o deuda al corte; los dos sin anulados.
 */
fun Route.extractosPorSubirRoutes() {
    get("/api/extractos-por-subir") {
        call.respond(extractosPorSubirDe(call.userId(), AppClock.today()))
    }
}

/** Ver [extractosPorSubirRoutes]. Con [hoy] elegido para las pruebas. */
internal suspend fun extractosPorSubirDe(uid: String, hoy: LocalDate): List<ExtractoPorSubir> = dbQuery {
    val tarjetas = tarjetasConCorte(uid)
    if (tarjetas.isEmpty()) return@dbQuery emptyList()
    val descartadas = AnomaliasDescartadas.selectAll()
        .where { AnomaliasDescartadas.userId eq uid }
        .map { it[AnomaliasDescartadas.huella] }
        .filter { it.startsWith(PREFIJO_DE_LA_HUELLA_DEL_EXTRACTO) }
        .toSet()
    val subidos = subidosPorCuenta(uid)
    tarjetas.mapNotNull { t ->
        extractoPorSubirAl(
            cuentaId = t.cuentaId,
            cuentaNombre = t.nombre,
            banco = t.banco,
            diaDeCorte = t.diaDeCorte,
            hoy = hoy.toString(),
            subidos = subidos[t.cuentaId].orEmpty().map { it.toString() },
            descartadas = descartadas,
            conActividad = { corte -> seUso(uid, t.cuentaId, LocalDate.parse(corte)) },
        )
    }
}

private data class TarjetaConCorte(val cuentaId: String, val nombre: String, val banco: String, val diaDeCorte: Int?)

/** Las tarjetas en pesos de [uid] con sus condiciones. */
private fun tarjetasConCorte(uid: String): List<TarjetaConCorte> {
    val condiciones = Cards.selectAll().where { Cards.userId eq uid }
        .associate { it[Cards.accountId] to (it[Cards.bank] to it[Cards.cutoffDay]) }
    return Accounts.selectAll()
        .where { (Accounts.userId eq uid) and (Accounts.type eq AccountType.CREDIT_CARD.name) and (Accounts.currency eq "COP") }
        .mapNotNull { fila ->
            val id = fila[Accounts.id]
            val (banco, corte) = condiciones[id] ?: return@mapNotNull null
            TarjetaConCorte(id, fila[Accounts.name], banco, corte)
        }
}

/** Las fechas (en Bogotá) en que se subió o importó un extracto de cada cuenta. */
private fun subidosPorCuenta(uid: String): Map<String, List<LocalDate>> {
    val documentos = Documents.select(Documents.accountId, Documents.uploadedAt)
        .where { (Documents.userId eq uid) and (Documents.kind eq TipoDeDocumento.EXTRACTO.name) }
        .mapNotNull { fila -> fila[Documents.accountId]?.let { it to epochMillisToAppDate(fila[Documents.uploadedAt]) } }
    val importes = StatementImports.select(StatementImports.accountId, StatementImports.importedAt)
        .where { StatementImports.userId eq uid }
        .map { it[StatementImports.accountId] to epochMillisToAppDate(it[StatementImports.importedAt]) }
    return (documentos + importes).groupBy({ it.first }, { it.second })
}

/**
 * ¿La tarjeta tuvo movimientos en el mes que cerró [corte], o deuda al cierre de ese día? Sin anulados
 * (`loadNonVoidedEventsIn`).
 */
private fun Transaction.seUso(uid: String, cuentaId: String, corte: LocalDate): Boolean {
    val finDelCorte = appDateToEpochMillis(corte.plusDays(1)) - 1
    val desde = appDateToEpochMillis(corte.minusMonths(1).plusDays(1))
    val hastaElCorte = loadNonVoidedEventsIn(uid, cuentaId).filter { it.timestamp <= finDelCorte }
    if (hastaElCorte.any { it.timestamp >= desde }) return true
    return (computeBalances(AccountType.CREDIT_CARD, hastaElCorte)["COP"] ?: 0L) != 0L
}
