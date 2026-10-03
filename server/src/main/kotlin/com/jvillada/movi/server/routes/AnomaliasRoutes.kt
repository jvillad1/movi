package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.DescartarAnomaliaRequest
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.categoriasPorEncima
import com.jvillada.movi.shared.model.cobrosDuplicados
import com.jvillada.movi.shared.model.comerciosNuevosConMontoAlto
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.suscripcionesQueSubieron
import com.jvillada.movi.shared.model.ventanaDe
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.min
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate

/**
 * # Lo que se sale de lo normal (Ola 4)
 *
 * Los detectores son de `:core` (`LoQueSeSaleDeLoNormal.kt`); acá se les da lo que necesitan, se
 * sacan los que el dueño ya descartó, y se sirven. Hoy los lee del resumen del Inicio
 * (`DashboardSummary.loQueSeSaleDeLoNormal`), sin una llamada nueva; esta ruta es la misma lista
 * para quien la pida sola, y la puerta del «Está bien».
 */
fun Route.anomaliasRoutes() {
    get("/api/anomalias") {
        val uid = call.userId()
        val ajustes = ajustesDePeriodoDe(uid)
        call.respond(dbQuery { loQueSeSaleDeLoNormalDe(uid, AppClock.today(), ajustes) })
    }

    /**
     * «Está bien»: no volver a avisar esta huella. Idempotente (descartar dos veces deja una fila).
     * No toca ningún movimiento.
     */
    post("/api/anomalias/descartar") {
        val uid = call.userId()
        val huella = runCatching { call.receive<DescartarAnomaliaRequest>() }.getOrNull()?.huella?.trim()
        if (huella.isNullOrEmpty() || huella.length > 300) {
            return@post call.respond(HttpStatusCode.BadRequest, "Falta la huella del aviso")
        }
        dbQuery {
            AnomaliasDescartadas.insertIgnore {
                it[userId] = uid
                it[AnomaliasDescartadas.huella] = huella
                it[descartadaEn] = System.currentTimeMillis()
            }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

/** Cuántos períodos hacia atrás forman «lo normal» de una categoría, como mucho. */
private const val PERIODOS_DE_LO_NORMAL = 3

/** Cuánto hacia atrás se miran los comercios ya vistos: unos cuatro meses. */
private const val DIAS_DE_HISTORIA_DE_COMERCIOS = 120L

/**
 * **Los avisos vigentes de [uid]**, sin los que ya descartó. Todo lo que miran es del período en
 * curso (los cobros que se repiten, lo que subió, lo que va por encima, lo nuevo); la historia solo
 * sirve de vara.
 *
 * «Lo normal» de una categoría son los períodos anteriores que Movi cubrió enteros (el primer
 * movimiento del dueño es anterior a su arranque), con la misma regla que el gasto del día a día de
 * la caja proyectada: un período a medio anotar haría ver todo «por encima».
 */
internal fun Transaction.loQueSeSaleDeLoNormalDe(uid: String, hoy: LocalDate, ajustes: PeriodSettings): List<Anomalia> {
    val primerMovimiento: Long = Events.select(Events.timestamp.min())
        .where { Events.userId eq uid }
        .firstOrNull()?.get(Events.timestamp.min())
        ?: return emptyList()
    val actual = periodoDe(appDateToEpochMillis(hoy) + MEDIO_DIA_MS, ajustes)
    val ventana = ventanaDe(actual, ajustes)
    val delPeriodo = loadEventsBetween(uid, ventana.first, ventana.last + 1)
    if (delPeriodo.isEmpty()) return emptyList()

    val nombreDeCuenta = Accounts.selectAll().where { Accounts.userId eq uid }
        .associate { it[Accounts.id] to it[Accounts.name] }
    val diaDe: (Long) -> String = { epochMillisToAppDateString(it) }

    // Los períodos anteriores cubiertos, del más reciente al más viejo.
    val anteriores = mutableListOf<Pair<PeriodoFinanciero, LongRange>>()
    var p = actual
    repeat(PERIODOS_DE_LO_NORMAL) {
        p = periodoAnterior(p)
        val v = ventanaDe(p, ajustes)
        val arranque: Long = v.first
        if (primerMovimiento <= arranque) anteriores += p to v
    }
    val previo = periodoAnterior(actual)
    val ventanaPrevia = ventanaDe(previo, ajustes)
    // Una sola lectura para todo lo de antes: los comercios conocidos (unos cuatro meses), los
    // períodos de «lo normal» y el cobro anterior de cada suscripción.
    val desdeLaHistoria = minOf(
        ventana.first - DIAS_DE_HISTORIA_DE_COMERCIOS * 24 * 60 * 60 * 1000,
        anteriores.lastOrNull()?.second?.first ?: ventana.first,
        ventanaPrevia.first,
    )
    val historia = loadEventsBetween(uid, desdeLaHistoria, ventana.first)

    val suscripciones = Subscriptions.selectAll().where { Subscriptions.userId eq uid }.map { it.toSubscription() }
    val periodoDeUnInstante: (Long) -> String = { ts ->
        when {
            ts in ventana -> actual.prefijo
            ts in ventanaPrevia -> previo.prefijo
            else -> periodoDe(ts, ajustes).prefijo
        }
    }

    val todas = cobrosDuplicados(delPeriodo, nombreDeCuenta, diaDe) +
        suscripcionesQueSubieron(
            suscripciones, historia + delPeriodo, periodoDeUnInstante, actual.prefijo, previo.prefijo, nombreDeCuenta, diaDe,
        ) +
        categoriasPorEncima(
            actual = delPeriodo,
            anteriores = anteriores.map { (_, v) -> historia.filter { it.timestamp in v } },
            periodoActual = actual.prefijo,
            nombreDeCuenta = nombreDeCuenta,
            diaDe = diaDe,
        ) +
        comerciosNuevosConMontoAlto(delPeriodo, historia, primerMovimiento, nombreDeCuenta, diaDe)

    val descartadas = AnomaliasDescartadas.selectAll()
        .where { AnomaliasDescartadas.userId eq uid }
        .map { it[AnomaliasDescartadas.huella] }
        .toSet()
    return todas.filterNot { it.huella in descartadas }.distinctBy { it.huella }
}

private const val MEDIO_DIA_MS = 12L * 60 * 60 * 1000
