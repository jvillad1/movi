package com.jvillada.movi.server.routes

import com.jvillada.movi.server.espacios.loCompartidoDelUsuario
import com.jvillada.movi.server.balance.computeBalances
import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
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
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.CambioDeCategoriaDeUnRecurrente
import com.jvillada.movi.shared.model.EvidenciaDeAnomalia
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.anomaliaDelCambioDeCategoria
import com.jvillada.movi.shared.model.recurrentesQueCambiaronDeCategoria
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.deudasQueCrecen
import com.jvillada.movi.shared.model.interesesLejosDelEstimado
import com.jvillada.movi.shared.model.DescartarAnomaliaRequest
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.categoriasPorEncima
import com.jvillada.movi.server.reminders.emparejadasComoSellos
import com.jvillada.movi.server.reminders.loadOccurrenceRows
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
        // Las de las deudas solo a quien las pide: un APK instalado no conoce esos tipos y perdería
        // la lista entera al leerla (ver `TipoDeAnomalia`).
        val conDeudas = call.request.queryParameters["conDeudas"] == "1"
        // Vuelta 60: el recurrente que cambió de categoría, por la misma razón.
        val conRecurrentes = call.request.queryParameters["conRecurrentes"] == "1"
        call.respond(dbQuery { loQueSeSaleDeLoNormalDe(uid, AppClock.today(), ajustes, conDeudas, conRecurrentes) })
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
internal fun Transaction.loQueSeSaleDeLoNormalDe(
    uid: String,
    hoy: LocalDate,
    ajustes: PeriodSettings,
    /** Sumar las dos de las deudas ([deudasQueCrecen], [interesesLejosDelEstimado]). */
    conDeudas: Boolean = false,
    /** Sumar los recurrentes que cambiaron de categoría ([recurrentesQueCambiaronDeCategoria]). */
    conRecurrentes: Boolean = false,
): List<Anomalia> {
    val primerMovimiento: Long = Events.select(Events.timestamp.min())
        .where { Events.userId eq uid }
        .firstOrNull()?.get(Events.timestamp.min())
        ?: return emptyList()
    val actual = periodoDe(appDateToEpochMillis(hoy) + MEDIO_DIA_MS, ajustes)
    val ventana = ventanaDe(actual, ajustes)
    val delPeriodo = loadEventsBetween(uid, ventana.first, ventana.last + 1)
    // Sin movimientos en el período no hay nada de lo del período que mirar; las deudas sí.
    if (delPeriodo.isEmpty() && !conDeudas) return emptyList()

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
    val tuParteDeOtros = loCompartidoDelUsuario(uid).tuParteDeOtros
    // Vuelta 43: **el pago de una factura no es consumo nuevo.** El arriendo anotado con «Anotar
    // pago» salía en «Movi propone» como «Gasto nuevo en Otros $1.800.000»: es un fijo del período,
    // ya contado como fijo, y lo prueba su sello. Lo ligado a una ocurrencia —sellado por el dueño o
    // emparejado solo— no entra a «categoría por encima» ni a «comercio nuevo», ni como gasto de
    // este período ni como «lo normal» de los anteriores. El cobro duplicado sí lo sigue mirando: un
    // arriendo pagado dos veces es justo lo que tiene que avisar.
    val descartadas = AnomaliasDescartadas.selectAll()
        .where { AnomaliasDescartadas.userId eq uid }
        .map { it[AnomaliasDescartadas.huella] }
        .toSet()
    val sellos = sellosDe(uid, hoy, ajustes)
    val pagosDeFacturas = sellos.mapNotNull { it.eventId }.toSet()
    // Vuelta 60: **el recurrente que solo cambió de categoría no es gasto nuevo.** El arriendo que
    // este período quedó en Otros y el pasado fue Hogar salía como «Gasto nuevo en Otros $1.800.000»:
    // mientras el dueño no decide («¿Arriendo va en Hogar…?»), ese monto no entra a «categoría por
    // encima» ni a «comercio nuevo» (no es nuevo: es el mismo de siempre). Con «No» el dueño dijo que
    // sí es de esa categoría: desde ahí cuenta como cualquier gasto.
    val cambios = cambiosDeCategoria(
        delPeriodo = delPeriodo,
        delAnterior = historia.filter { it.timestamp in ventanaPrevia },
        reglas = reglasDe(uid),
        sellos = sellos + emparejadasDelPeriodoAnterior(uid, ventanaPrevia, ajustes),
        periodoAnterior = previo.prefijo,
    ).filterNot { it.huella in descartadas }
    val cambiados = cambios.map { it.eventoId }.toSet()
    val delConsumo = delPeriodo.filterNot { it.id in pagosDeFacturas || it.id in cambiados }

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
            // R3: tu parte de lo que pagó otro también es gasto de su categoría.
            actual = delConsumo + tuParteDeOtros.filter { it.timestamp in ventana },
            anteriores = anteriores.map { (_, v) ->
                (historia.filterNot { it.id in pagosDeFacturas } + tuParteDeOtros).filter { it.timestamp in v }
            },
            periodoActual = actual.prefijo,
            nombreDeCuenta = nombreDeCuenta,
            diaDe = diaDe,
            // Vuelta 57: lo que se sacó por fijo, para que el texto diga su base.
            fijosDelPeriodo = delPeriodo.filter { it.id in pagosDeFacturas },
        ) +
        comerciosNuevosConMontoAlto(delConsumo, historia, primerMovimiento, nombreDeCuenta, diaDe) +
        (if (conDeudas) loDeLasDeudas(uid, hoy, previo, ventanaPrevia, diaDe) else emptyList()) +
        (
            if (!conRecurrentes) {
                emptyList()
            } else {
                val porId = (delPeriodo + historia).associateBy { it.id }
                cambios.map { c ->
                    val evidencia = listOfNotNull(porId[c.eventoId], porId[c.eventoAnteriorId]).map { e ->
                        EvidenciaDeAnomalia(
                            id = e.id, fecha = diaDe(e.timestamp), nombre = e.description, monto = e.amount,
                            moneda = e.currency, cuenta = nombreDeCuenta[e.accountId],
                        )
                    }
                    anomaliaDelCambioDeCategoria(c, evidencia)
                }
            }
            )

    return todas.filterNot { it.huella in descartadas }.distinctBy { it.huella }
}

private const val MEDIO_DIA_MS = 12L * 60 * 60 * 1000

/**
 * Los sellos que atan un movimiento a una factura del checklist: los de `recurring_occurrences` (de
 * cualquier período: «Sí, fue este», «Anotar pago») y los que Movi emparejó solo en este período.
 */
private fun Transaction.sellosDe(uid: String, hoy: LocalDate, ajustes: PeriodSettings): List<RecurringOccurrence> =
    loadOccurrenceRows(uid) + emparejadasComoSellos(uid, hoy, ajustes)

/**
 * Lo que Movi emparejó solo en el período ANTERIOR (vuelta 60): `emparejadasComoSellos` mira el
 * período de su «hoy», así que se le pasa el último día de ese período. Los sellos del dueño ya vienen
 * de todos los períodos en [loadOccurrenceRows]. Solo para reconocer el recurrente que cambió de
 * categoría: no entra a [sellosDe] ni cambia qué se cuenta como pago de una factura.
 */
private fun Transaction.emparejadasDelPeriodoAnterior(
    uid: String,
    ventanaPrevia: LongRange,
    ajustes: PeriodSettings,
): List<RecurringOccurrence> =
    emparejadasComoSellos(uid, LocalDate.parse(epochMillisToAppDateString(ventanaPrevia.last)), ajustes)

/** Las reglas recurrentes de [uid], con lo que [recurrentesQueCambiaronDeCategoria] mira. */
private fun Transaction.reglasDe(uid: String): List<RecurringRule> =
    RecurringRules.selectAll().where { RecurringRules.userId eq uid }.mapNotNull { fila ->
        val tipo = runCatching { TransactionType.valueOf(fila[RecurringRules.type]) }.getOrNull() ?: return@mapNotNull null
        RecurringRule(
            id = fila[RecurringRules.id],
            name = fila[RecurringRules.name],
            category = fila[RecurringRules.category],
            amount = fila[RecurringRules.amount],
            dayOfMonth = fila[RecurringRules.dayOfMonth],
            type = tipo,
        )
    }

private fun cambiosDeCategoria(
    delPeriodo: List<FinancialEvent>,
    delAnterior: List<FinancialEvent>,
    reglas: List<RecurringRule>,
    sellos: List<RecurringOccurrence>,
    periodoAnterior: String,
): List<CambioDeCategoriaDeUnRecurrente> = recurrentesQueCambiaronDeCategoria(
    actual = delPeriodo,
    anterior = delAnterior,
    reglas = reglas,
    reglaDelMovimiento = sellos.mapNotNull { s -> s.eventId?.let { it to s.ruleId } }.toMap(),
    periodoAnterior = periodoAnterior,
)

/**
 * **Los recurrentes de [uid] que este período quedaron en otra categoría que el anterior** (vuelta
 * 60), sin los que el dueño ya descartó con «No». Los lee Movi AI para no comparar el arriendo como
 * gasto nuevo de Otros (ver `comoEnElPeriodoAnterior`).
 */
internal fun Transaction.cambiosDeCategoriaDe(uid: String, hoy: LocalDate, ajustes: PeriodSettings): List<CambioDeCategoriaDeUnRecurrente> {
    val actual = periodoDe(appDateToEpochMillis(hoy) + MEDIO_DIA_MS, ajustes)
    val ventana = ventanaDe(actual, ajustes)
    val previo = periodoAnterior(actual)
    val ventanaPrevia = ventanaDe(previo, ajustes)
    val delPeriodo = loadEventsBetween(uid, ventana.first, ventana.last + 1)
    if (delPeriodo.isEmpty()) return emptyList()
    val descartadas = AnomaliasDescartadas.selectAll()
        .where { AnomaliasDescartadas.userId eq uid }
        .map { it[AnomaliasDescartadas.huella] }
        .toSet()
    return cambiosDeCategoria(
        delPeriodo = delPeriodo,
        delAnterior = loadEventsBetween(uid, ventanaPrevia.first, ventanaPrevia.last + 1),
        reglas = reglasDe(uid),
        sellos = sellosDe(uid, hoy, ajustes) + emparejadasDelPeriodoAnterior(uid, ventanaPrevia, ajustes),
        periodoAnterior = previo.prefijo,
    ).filterNot { it.huella in descartadas }
}

/**
 * Las dos de las deudas: la que creció entre el cierre del período anterior y el de antes
 * ([deudasQueCrecen]), y el interés del extracto que se aparta de la estimación
 * ([interesesLejosDelEstimado]). Sobre los movimientos vivos de las deudas, enteros (un saldo se
 * suma desde el principio).
 */
private fun Transaction.loDeLasDeudas(
    uid: String,
    hoy: LocalDate,
    cerrado: PeriodoFinanciero,
    ventanaCerrada: LongRange,
    diaDe: (Long) -> String,
): List<Anomalia> {
    val deudas = Accounts.selectAll().where { Accounts.userId eq uid }
        .map { it.toAccount() }
        .filter { it.type == AccountType.LOAN || it.type == AccountType.CREDIT_CARD }
    if (deudas.isEmpty()) return emptyList()
    val ids = deudas.map { it.id }.toSet()
    val eventos = loadNonVoidedEventsIn(uid).filter { it.accountId in ids }
    val terminos = Credits.selectAll().where { Credits.userId eq uid }.map { it.toCreditTerms() }.associateBy { it.accountId }
    val creditos = deudas.filter { it.type == AccountType.LOAN && it.currency == "COP" }.map { cuenta ->
        val saldo = computeBalances(cuenta.type, eventos.filter { it.accountId == cuenta.id })["COP"] ?: 0L
        CreditSummary(account = cuenta.copy(balance = saldo), terms = terminos[cuenta.id], paidPct = null)
    }
    return deudasQueCrecen(deudas, eventos, ventanaCerrada, cerrado.prefijo, diaDe) +
        interesesLejosDelEstimado(creditos, hoy.year, hoy.monthValue, hoy.dayOfMonth)
}

