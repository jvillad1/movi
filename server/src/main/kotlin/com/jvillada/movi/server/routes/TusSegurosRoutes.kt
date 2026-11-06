package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.cuentasConSaldo
import com.jvillada.movi.server.credits.paidPctFor
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.HUELLA_DE_ME_INTERESA
import com.jvillada.movi.shared.model.MAX_CLAVE_DE_SEGURO
import com.jvillada.movi.shared.model.MESES_DE_LA_VENTANA_DE_SEGUROS
import com.jvillada.movi.shared.model.MESES_QUE_SE_PUEDEN_RESPONDER
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.RespuestaDelSeguro
import com.jvillada.movi.shared.model.RespuestasDelDueno
import com.jvillada.movi.shared.model.SeguroDetectado
import com.jvillada.movi.shared.model.SegurosQuePagas
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.conLoQueDijoElDueno
import com.jvillada.movi.shared.model.huellaDeCadaCuanto
import com.jvillada.movi.shared.model.huellaDelSeguro
import com.jvillada.movi.shared.model.huellaDelSeguroConfirmado
import com.jvillada.movi.shared.model.respuestasDeLasHuellas
import com.jvillada.movi.shared.model.segurosDetectados
import com.jvillada.movi.shared.model.tramoDeLoQuePagasEnSeguros
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate

/*
 * # Vuelta 132 · «Tus seguros»: lo que ya pagas en seguros, sin vender nada
 *
 * - `GET /api/seguros` arma [SegurosQuePagas] **en cada lectura**, como `/api/debitos-automaticos`: no
 *   crea tablas ni guarda nada del resultado. La regla entera es `:core` `LosSegurosQuePagas.kt`.
 *   Con `?pantalla=1` (lo manda la pantalla, no la fila de Tú) deja un `SEGUROS_VISTOS` en el tracking
 *   propio, una vez por día, con cuántos seguros y el tramo del total.
 * - Lo que dice el dueño son huellas en `anomalias_descartadas` (la tabla del «Ya no» de los extractos),
 *   sin esquema nuevo, y cada respuesta contesta el resultado nuevo (la pantalla no vuelve a pedirlo):
 *   - `POST /api/seguros/no-es` («No es un seguro», «Es el mismo» de un posible duplicado): `seguro:<clave>`;
 *   - `POST /api/seguros/si-es` («Sí», «Es otro»): `seguro-si:<clave>`. Cada una borra la otra en la misma
 *     transacción: vale la última;
 *   - `POST /api/seguros/cada-cuanto` (`{clave, meses: 1|12}`, «¿Cada cuánto lo pagas?» de un pago de una
 *     vez): `seguro-cada:<clave>:<meses>`, borrando la respuesta anterior: gana la última.
 *   Las tres son idempotentes; una clave que hoy no se detecta contesta 404.
 * - `POST /api/seguros/me-interesa` guarda la huella `seguros-me-interesa` **siempre** (es una elección del
 *   dueño, no telemetría: no se borra a los 90 días ni depende del interruptor) y, **solo** con «Mandar
 *   datos de uso» encendido, un `SEGUROS_ME_INTERESA` (id estable: uno por persona) con cuántos seguros y el
 *   tramo. Nunca montos, comercios ni textos.
 *
 * **Lo que se lee** (revisión de la vuelta, I2): los movimientos de la ventana de 12 meses
 * (`loadEventsBetween`, sin anulados y con lo compartido), las cuentas con su saldo sumado en SQL
 * (`cuentasConSaldo`: de ahí sale si un crédito ya se pagó, sin traer su historia), las condiciones de
 * los créditos, las reglas, las suscripciones y las huellas: una transacción. Nada pasa por la IA.
 */

/** El id del `SEGUROS_ME_INTERESA` de cada persona: uno solo, para que tocar dos veces sea una. */
internal val ID_DE_ME_INTERESA: String = Observabilidad.idEstable("seguros", "me-interesa")

fun Route.tusSegurosRoutes() {
    get("/api/seguros") {
        val uid = call.userId()
        val hoy = AppClock.today()
        val resultado = segurosDe(uid, hoy)
        if (call.request.queryParameters["pantalla"] == "1") {
            Observabilidad.registrarSistema(
                uid,
                Observabilidad.idEstable("seguros-vistos", hoy.toString()),
                EventoDeUso.SEGUROS_VISTOS,
                datosDelUsoDeSeguros(resultado),
                System.currentTimeMillis(),
            )
        }
        call.respond(resultado)
    }

    post("/api/seguros/no-es") {
        val uid = call.userId()
        val clave = call.respuesta()?.clave ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el seguro")
        responderSobre(uid, clave) { f ->
            guardarLaRespuesta(uid, guardar = huellaDelSeguro(clave), quitar = listOf(huellaDelSeguroConfirmado(clave)))
            f.respuestas.copy(descartados = f.respuestas.descartados + clave, confirmados = f.respuestas.confirmados - clave)
        }
    }

    post("/api/seguros/si-es") {
        val uid = call.userId()
        val clave = call.respuesta()?.clave ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el seguro")
        responderSobre(uid, clave) { f ->
            guardarLaRespuesta(uid, guardar = huellaDelSeguroConfirmado(clave), quitar = listOf(huellaDelSeguro(clave)))
            f.respuestas.copy(confirmados = f.respuestas.confirmados + clave, descartados = f.respuestas.descartados - clave)
        }
    }

    post("/api/seguros/cada-cuanto") {
        val uid = call.userId()
        val pedido = call.respuesta() ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el seguro")
        val meses = pedido.meses?.takeIf { it in MESES_QUE_SE_PUEDEN_RESPONDER }
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Cada mes o cada año")
        responderSobre(uid, pedido.clave) { f ->
            val anteriores = MESES_QUE_SE_PUEDEN_RESPONDER.filter { it != meses }.map { huellaDeCadaCuanto(pedido.clave, it) }
            guardarLaRespuesta(uid, guardar = huellaDeCadaCuanto(pedido.clave, meses), quitar = anteriores)
            f.respuestas.copy(cadaCuanto = f.respuestas.cadaCuanto + (pedido.clave to meses))
        }
    }

    post("/api/seguros/me-interesa") {
        val uid = call.userId()
        val f = fuentesDe(uid, AppClock.today())
        dbQuery {
            AnomaliasDescartadas.insertIgnore {
                it[AnomaliasDescartadas.userId] = uid
                it[AnomaliasDescartadas.huella] = HUELLA_DE_ME_INTERESA
                it[AnomaliasDescartadas.descartadaEn] = System.currentTimeMillis()
            }
        }
        Observabilidad.registrarSistema(
            uid,
            ID_DE_ME_INTERESA,
            EventoDeUso.SEGUROS_ME_INTERESA,
            datosDelUsoDeSeguros(f.resultado()),
            System.currentTimeMillis(),
        )
        call.respond(HttpStatusCode.NoContent)
    }
}

/** Los datos de los dos eventos: cuántos seguros cuentan y el tramo del total. Nada más. */
internal fun datosDelUsoDeSeguros(s: SegurosQuePagas): Map<ClaveDeUso, String> = mapOf(
    ClaveDeUso.CUANTOS to s.seguros.size.coerceIn(0, 999_999).toString(),
    ClaveDeUso.TRAMO to tramoDeLoQuePagasEnSeguros(s.alMes),
)

private suspend fun ApplicationCall.respuesta(): RespuestaDelSeguro? =
    runCatching { receive<RespuestaDelSeguro>() }.getOrNull()
        ?.let { it.copy(clave = it.clave.trim()) }
        ?.takeIf { it.clave.isNotEmpty() && it.clave.length <= MAX_CLAVE_DE_SEGURO }

/**
 * Una respuesta del dueño sobre [clave]: lee las fuentes **una vez**, mira que el seguro exista (404 si
 * no), guarda con [guardar] (que devuelve las respuestas nuevas) y contesta el resultado con ellas, sin
 * volver a leer.
 */
private suspend fun io.ktor.server.routing.RoutingContext.responderSobre(
    uid: String,
    clave: String,
    guardar: suspend (FuentesDeLosSeguros) -> RespuestasDelDueno,
) {
    val f = fuentesDe(uid, AppClock.today())
    if (f.detectados.none { it.clave == clave }) return call.respond(HttpStatusCode.NotFound, "Movi ya no ve ese seguro")
    val nuevas = guardar(f)
    call.respond(f.copy(respuestas = nuevas).resultado())
}

private suspend fun guardarLaRespuesta(uid: String, guardar: String, quitar: List<String>) = dbQuery {
    quitar.forEach { h ->
        AnomaliasDescartadas.deleteWhere { (AnomaliasDescartadas.userId eq uid) and (AnomaliasDescartadas.huella eq h) }
    }
    AnomaliasDescartadas.insertIgnore {
        it[AnomaliasDescartadas.userId] = uid
        it[AnomaliasDescartadas.huella] = guardar
        it[AnomaliasDescartadas.descartadaEn] = System.currentTimeMillis()
    }
}

/** Las fuentes del detector, leídas una vez, y lo que dijo el dueño. */
private data class FuentesDeLosSeguros(
    val hoy: LocalDate,
    val eventos: List<FinancialEvent>,
    val cuentas: List<Account>,
    val creditos: List<CreditSummary>,
    val reglas: List<RecurringRule>,
    val suscripciones: List<Subscription>,
    val respuestas: RespuestasDelDueno,
) {
    /** Lo detectado con el «cada cuánto» que dijo el dueño (cambia las cifras, no qué existe). */
    val detectados: List<SeguroDetectado> by lazy {
        segurosDetectados(
            eventos, cuentas, creditos, reglas, suscripciones,
            hoy = hoy.toString(),
            diaDe = { epochMillisToAppDateString(it) },
            cadaCuanto = respuestas.cadaCuanto,
        )
    }

    fun resultado(): SegurosQuePagas = conLoQueDijoElDueno(detectados, respuestas)
}

private suspend fun fuentesDe(uid: String, hoy: LocalDate): FuentesDeLosSeguros = dbQuery {
    val desde = appDateToEpochMillis(hoy.minusMonths(MESES_DE_LA_VENTANA_DE_SEGUROS.toLong()))
    val hasta = appDateToEpochMillis(hoy.plusDays(1))
    val eventos = loadEventsBetween(uid, desde, hasta)
    val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
    // La tasa no importa: solo se usa el saldo en pesos de cada cuenta (si un crédito ya se pagó).
    val cuentas = cuentasConSaldo(uid, anulados, usdToCop = 0.0)
    val condiciones = Credits.selectAll().where { Credits.userId eq uid }.associate { it[Credits.accountId] to it.toCreditTerms() }
    val creditos = cuentas.filter { it.type == AccountType.LOAN }.mapNotNull { cuenta ->
        val terms = condiciones[cuenta.id]?.takeIf { (it.insuranceMonthly ?: 0L) > 0L } ?: return@mapNotNull null
        // Una deuda en $0 sin ningún movimiento es «todavía no se registró nada», no «pagado».
        val conMovimientos = cuenta.balance != 0L ||
            Events.select(Events.id).where { (Events.userId eq uid) and (Events.accountId eq cuenta.id) }.limit(1).any()
        CreditSummary(account = cuenta, terms = terms, paidPct = paidPctFor(terms.principal, cuenta.balance), hasMovements = conMovimientos)
    }
    val huellas = AnomaliasDescartadas.selectAll()
        .where { (AnomaliasDescartadas.userId eq uid) and (AnomaliasDescartadas.huella like "seguro%") }
        .map { it[AnomaliasDescartadas.huella] }
    FuentesDeLosSeguros(
        hoy = hoy,
        eventos = eventos,
        cuentas = cuentas,
        creditos = creditos,
        reglas = reglasRealesDe(uid),
        suscripciones = Subscriptions.selectAll().where { Subscriptions.userId eq uid }.map { it.toSubscription() },
        respuestas = respuestasDeLasHuellas(huellas),
    )
}

/** Ver [tusSegurosRoutes]. Con [hoy] elegido para las pruebas. */
internal suspend fun segurosDe(uid: String, hoy: LocalDate): SegurosQuePagas = fuentesDe(uid, hoy).resultado()
