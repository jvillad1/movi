package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.UsoDeLaApp
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.MAX_CLAVE_DE_SEGURO
import com.jvillada.movi.shared.model.PREFIJO_DE_LA_HUELLA_DEL_SEGURO
import com.jvillada.movi.shared.model.PREFIJO_DE_LA_HUELLA_DEL_SEGURO_CONFIRMADO
import com.jvillada.movi.shared.model.RespuestaDelSeguro
import com.jvillada.movi.shared.model.SegurosQuePagas
import com.jvillada.movi.shared.model.huellaDelSeguro
import com.jvillada.movi.shared.model.huellaDelSeguroConfirmado
import com.jvillada.movi.shared.model.losSegurosQuePagas
import com.jvillada.movi.shared.model.segurosDetectados
import com.jvillada.movi.shared.model.tramoDeLoQuePagasEnSeguros
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate

/*
 * # Vuelta 132 · «Tus seguros»: lo que ya pagas en seguros, sin vender nada
 *
 * - `GET /api/seguros` arma [SegurosQuePagas] **en cada lectura**, como `/api/debitos-automaticos`: no
 *   crea tablas ni guarda nada del resultado. La regla entera es `losSegurosQuePagas` (`:core`).
 *   Con `?pantalla=1` (lo manda la pantalla, no la fila de Tú) deja un `SEGUROS_VISTOS` en el tracking
 *   propio, una vez por día, con cuántos seguros y el tramo del total.
 * - `POST /api/seguros/no-es` («No es un seguro») guarda la huella `seguro:<clave>` en
 *   `anomalias_descartadas`, la misma tabla del «Ya no» de los extractos; lo saca de la lista y del total.
 * - `POST /api/seguros/si-es` («Sí, es un seguro») guarda `seguro-si:<clave>` en la misma tabla. Es la
 *   forma más simple sin esquema nuevo: la tabla ya es un conjunto (usuario, huella) con upsert. Cada una
 *   borra la otra en la misma transacción, así vale la última respuesta del dueño. Las dos son idempotentes.
 *   Una clave que hoy no se detecta contesta 404: no se guardan huellas de cosas que no existen.
 * - `POST /api/seguros/me-interesa` deja **un** `SEGUROS_ME_INTERESA` (id estable por persona) con
 *   cuántos seguros y el tramo: nunca montos, comercios ni textos. Respeta «Mandar datos de uso»
 *   (`Observabilidad.registrarSistema`).
 *
 * Nada de esto pasa por la IA.
 */

/** El id del `SEGUROS_ME_INTERESA` de cada persona: uno solo, para que tocar dos veces sea una. */
internal val ID_DE_ME_INTERESA: String = Observabilidad.idEstable("seguros", "me-interesa")

fun Route.tusSegurosRoutes() {
    get("/api/seguros") {
        val uid = call.userId()
        val resultado = segurosDe(uid, AppClock.today())
        if (call.request.queryParameters["pantalla"] == "1") {
            val hoy = AppClock.today().toString()
            Observabilidad.registrarSistema(
                uid,
                Observabilidad.idEstable("seguros-vistos", hoy),
                EventoDeUso.SEGUROS_VISTOS,
                datosDelUsoDeSeguros(resultado),
                System.currentTimeMillis(),
            )
        }
        call.respond(resultado)
    }

    post("/api/seguros/no-es") {
        val uid = call.userId()
        val clave = call.claveDelPedido() ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el seguro")
        if (!existeElSeguro(uid, clave)) return@post call.respond(HttpStatusCode.NotFound, "Movi ya no ve ese seguro")
        guardarLaRespuesta(uid, guardar = huellaDelSeguro(clave), quitar = huellaDelSeguroConfirmado(clave))
        call.respond(HttpStatusCode.NoContent)
    }

    post("/api/seguros/si-es") {
        val uid = call.userId()
        val clave = call.claveDelPedido() ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el seguro")
        if (!existeElSeguro(uid, clave)) return@post call.respond(HttpStatusCode.NotFound, "Movi ya no ve ese seguro")
        guardarLaRespuesta(uid, guardar = huellaDelSeguroConfirmado(clave), quitar = huellaDelSeguro(clave))
        call.respond(HttpStatusCode.NoContent)
    }

    post("/api/seguros/me-interesa") {
        val uid = call.userId()
        val resultado = segurosDe(uid, AppClock.today())
        Observabilidad.registrarSistema(
            uid,
            ID_DE_ME_INTERESA,
            EventoDeUso.SEGUROS_ME_INTERESA,
            datosDelUsoDeSeguros(resultado),
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

private suspend fun io.ktor.server.application.ApplicationCall.claveDelPedido(): String? =
    runCatching { receive<RespuestaDelSeguro>() }.getOrNull()?.clave?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= MAX_CLAVE_DE_SEGURO }

private suspend fun guardarLaRespuesta(uid: String, guardar: String, quitar: String) = dbQuery {
    AnomaliasDescartadas.deleteWhere { (AnomaliasDescartadas.userId eq uid) and (AnomaliasDescartadas.huella eq quitar) }
    AnomaliasDescartadas.insertIgnore {
        it[AnomaliasDescartadas.userId] = uid
        it[AnomaliasDescartadas.huella] = guardar
        it[AnomaliasDescartadas.descartadaEn] = System.currentTimeMillis()
    }
}

/** Las fuentes del detector, leídas una vez. */
private class FuentesDeLosSeguros(
    val eventos: List<com.jvillada.movi.shared.model.FinancialEvent>,
    val cuentas: List<com.jvillada.movi.shared.model.Account>,
    val creditos: List<com.jvillada.movi.shared.model.CreditSummary>,
    val reglas: List<com.jvillada.movi.shared.model.RecurringRule>,
    val suscripciones: List<com.jvillada.movi.shared.model.Subscription>,
)

private suspend fun fuentesDe(uid: String): FuentesDeLosSeguros {
    val creditos = creditosDe(uid)
    val eventos = loadNonVoidedEvents(uid)
    return dbQuery {
        FuentesDeLosSeguros(
            eventos = eventos,
            cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() },
            creditos = creditos,
            reglas = reglasRealesDe(uid),
            suscripciones = Subscriptions.selectAll().where { Subscriptions.userId eq uid }.map { it.toSubscription() },
        )
    }
}

/** ¿[clave] es de un seguro que Movi detecta hoy (antes de lo que dijo el dueño)? */
private suspend fun existeElSeguro(uid: String, clave: String): Boolean {
    val f = fuentesDe(uid)
    return segurosDetectados(
        f.eventos, f.cuentas, f.creditos, f.reglas, f.suscripciones,
        hoy = AppClock.today().toString(),
        diaDe = { epochMillisToAppDateString(it) },
    ).any { it.clave == clave }
}

/** Ver [tusSegurosRoutes]. Con [hoy] elegido para las pruebas. */
internal suspend fun segurosDe(uid: String, hoy: LocalDate): SegurosQuePagas {
    val f = fuentesDe(uid)
    val (huellas, meInteresa) = dbQuery {
        val h = AnomaliasDescartadas.selectAll().where { AnomaliasDescartadas.userId eq uid }
            .map { it[AnomaliasDescartadas.huella] }
        val ya = UsoDeLaApp.selectAll().where { (UsoDeLaApp.userId eq uid) and (UsoDeLaApp.id eq ID_DE_ME_INTERESA) }.limit(1).any()
        h to ya
    }
    val descartados = huellas.filter { it.startsWith(PREFIJO_DE_LA_HUELLA_DEL_SEGURO) }
        .map { it.removePrefix(PREFIJO_DE_LA_HUELLA_DEL_SEGURO) }.toSet()
    val confirmados = huellas.filter { it.startsWith(PREFIJO_DE_LA_HUELLA_DEL_SEGURO_CONFIRMADO) }
        .map { it.removePrefix(PREFIJO_DE_LA_HUELLA_DEL_SEGURO_CONFIRMADO) }.toSet()
    return losSegurosQuePagas(
        f.eventos, f.cuentas, f.creditos, f.reglas, f.suscripciones,
        hoy = hoy.toString(),
        diaDe = { epochMillisToAppDateString(it) },
        descartados = descartados,
        confirmados = confirmados,
    ).copy(meInteresa = meInteresa)
}
