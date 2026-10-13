package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.DestinosDescartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.sms.conElOrigenPropioDelIngresoDe
import com.jvillada.movi.server.sms.destinosDelDueno
import com.jvillada.movi.server.sms.loQueMoviSabeDelPagoDe
import com.jvillada.movi.server.sms.memoriaDe
import com.jvillada.movi.server.sms.numerosPropiosDe
import com.jvillada.movi.server.sms.sinLaCuentaPropia
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MemoriaDeCategorias
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.conElDestinoConocido
import com.jvillada.movi.shared.model.esUnComprobante
import com.jvillada.movi.shared.model.identificador
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.ofreceGuardarElDestino
import com.jvillada.movi.shared.model.unoPorPago
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll

/*
 * # Lo que Movi propone para un aviso, en un solo lugar
 *
 * `GET /api/sms/{id}/parse` y `GET /api/sms/{id}/coincidencias` son lo que lee Reconciliar; la bandeja
 * que se vacía sola necesita **lo mismo** para cada pago pendiente, de una vez, para saber cuáles
 * puede confirmar desde la fila (ver `LoQueMoviEntendio.kt` en `:core`). Por eso el cuerpo de las dos
 * rutas vive acá y lo llaman las tres: si la propuesta de la fila y la de Reconciliar salieran de dos
 * copias, el día que una cambie la fila confirmaría algo distinto de lo que Reconciliar muestra.
 */

/** Lo que dice `/parse` de un aviso que no trae un movimiento (un aviso, una ampliación de plazo). */
internal const val NO_TRAE_UN_MOVIMIENTO = "Este mensaje no trae un movimiento para anotar. Puedes ignorarlo."

/** La lectura de un aviso: lo que Movi propone, o por qué no propone nada. */
internal sealed interface LecturaDelAviso {
    data class Leido(val parsed: ParsedSms) : LecturaDelAviso
    data class NoTraeMovimiento(val motivo: String) : LecturaDelAviso
}

/**
 * **Lo que Movi sabe del dueño para leer sus avisos**: su memoria de categorías, sus terceros, los
 * números de sus cuentas y la bandeja con los pagos marcados. Se arma una vez por pedido: la bandeja
 * entera lee los mismos datos para cada aviso.
 */
internal class LoQueMoviSabeDelDueno(
    val memoria: MemoriaDeCategorias,
    val destinos: List<DestinoConocido>,
    val propios: Set<String>,
    leerLaBandeja: () -> List<SmsMessage>,
) {
    /** Solo se lee si hace falta: un aviso que no está pendiente no tiene otros avisos del pago. */
    val bandeja: List<SmsMessage> by lazy(leerLaBandeja)
}

internal fun Transaction.loQueMoviSabeDelDueno(uid: String, ahora: Long): LoQueMoviSabeDelDueno =
    LoQueMoviSabeDelDueno(memoriaDe(uid), destinosDelDueno(uid), numerosPropiosDe(uid)) { bandejaConLosPagos(uid, ahora) }

/**
 * **Lo que Movi propone para [sms]**: el cuerpo de `GET /api/sms/{id}/parse`. Ver los comentarios de
 * cada paso, que son los de siempre: el comprobante sale de su lectura guardada; el correo de PSE del
 * mismo pago le pone el nombre; la memoria del dueño, después sus terceros y al final sus propias
 * cuentas; lo que Movi sabe de los pagos de PSE; y el traspaso que entra.
 */
internal fun Transaction.leerElAviso(uid: String, sms: SmsMessage, sabe: LoQueMoviSabeDelDueno): LecturaDelAviso {
    // Ola 2: la propuesta de un comprobante no se lee con el parser de SMS sino con lo que leyó
    // Claude del papel, ya pasado por la memoria y las cuentas de otros (ver `PapelesRoutes`).
    if (esUnComprobante(sms)) {
        parsedDeUnComprobante(uid, sms.id, sms.text)?.let { return LecturaDelAviso.Leido(it) }
    }
    // No es un error de la app: el mensaje no trae un movimiento (un aviso, una ampliación de plazo).
    val leido = parseSms(sms.text, sms.bank) ?: return LecturaDelAviso.NoTraeMovimiento(NO_TRAE_UN_MOVIMIENTO)
    // El correo de PSE del mismo pago, si este aviso no lo es: ver [conLoQueDiceElCorreoDePse].
    val otrosDelPago = if (sms.state != SMS_STATE_PENDING) emptyList() else {
        val miembros = sabe.bandeja.firstOrNull { it.id == sms.id }?.miembrosDelGrupo.orEmpty()
        sabe.bandeja.filter { it.id in miembros && it.id != sms.id }
    }
    val correoDePse = if (esUnCorreoDePse(sms.text)) null else otrosDelPago.firstOrNull { esUnCorreoDePse(it.text) }
    val parsed = conLoQueDiceElCorreoDePse(leido, correoDePse?.let { leerElCorreoDePse(it.text) })
        // Si el propio correo de PSE es la propuesta de un pago con otros avisos, tampoco manda su
        // fecha: el día y la hora salen del aviso del banco más viejo (ver [conLoQueDiceElCorreoDePse]).
        .let { if (otrosDelPago.isNotEmpty()) it.copy(fecha = null) else it }
    // La historia del dueño entra acá y no adentro de `parseSms`: ese mismo parseo lo usan el sync y
    // la push, donde no hay a quién consultarle nada.
    //
    // **Y las cuentas de otros van DESPUÉS de la memoria**, no antes. Ese orden es la decisión: si el
    // dueño a ese número ya lo llamó «Mercado», sigue diciendo «Mercado» — el nombre que él puso es
    // suyo. El destino solo habla donde no hablaba nadie. Ver `conElDestinoConocido`.
    // Y al final, una cuenta del dueño no se le ofrece como «de otro» (ver [sinLaCuentaPropia]).
    val propuesta = sinLaCuentaPropia(
        conElDestinoConocido(conLoQueMoviRecuerda(parsed, sabe.memoria), sms.text, sabe.destinos),
        sabe.propios,
    )
    // Lo que el correo de PSE no dice —desde qué cuenta, qué crédito o tarjeta abona, a qué cuenta
    // propia fue un depósito— lo completa lo que el dueño ya tiene en Movi. Solo para los pagos de
    // PSE: es donde falta, y así nada cambia para los demás avisos.
    val esDePse = esUnCorreoDePse(sms.text) || correoDePse != null
    val completa = if (esDePse) loQueMoviSabeDelPagoDe(uid, propuesta) else propuesta
    // Un ingreso que nombra como origen una cuenta suya es un traspaso (ver `ElTraspasoQueEntra.kt`).
    return LecturaDelAviso.Leido(conElOrigenPropioDelIngresoDe(uid, completa, sms.text))
}

/**
 * **¿Este aviso ya está anotado?**: el cuerpo de `GET /api/sms/{id}/coincidencias`. Los movimientos
 * vivos que [coincidenciasDelSms] reconoce alrededor del momento del aviso.
 */
internal fun Transaction.coincidenciasDelAviso(uid: String, sms: SmsMessage, ahora: Long): List<FinancialEvent> {
    val parsed = (if (esUnComprobante(sms)) parsedDeUnComprobante(uid, sms.id, sms.text) else null)
        ?: parseSms(sms.text, sms.bank)
        ?: return emptyList()
    val momento = momentoDelSms(sms.time, ahora = ahora)
    val margen = DIAS_PARA_COINCIDIR * 86_400_000L
    val eventos = loadEventsBetween(uid, momento - margen, momento + margen + 1)
    return coincidenciasDelSms(parsed, momento, eventos)
}

/**
 * **Lo que Movi propone para cada pago pendiente de la bandeja**, uno por pago (un pago avisado tres
 * veces es una propuesta, la del aviso que más dice: [propuestaDelGrupo]). Un pago que ya tiene un
 * aviso confirmado no va: su tarjeta ofrece «Cerrar», no «Confirmar».
 */
internal fun Transaction.propuestasDeLaBandeja(uid: String, ahora: Long): PropuestasDeLaBandeja {
    val sabe = loQueMoviSabeDelDueno(uid, ahora)
    val bandeja = sabe.bandeja
    val cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() }
    val descartados = DestinosDescartados.selectAll().where { DestinosDescartados.userId eq uid }
        .map { it[DestinosDescartados.clave] }.toSet()
    val propuestas = unoPorPago(bandeja).mapNotNull { cabeza ->
        val miembros = cabeza.grupoId?.let { grupo -> bandeja.filter { it.grupoId == grupo } }?.ifEmpty { null } ?: listOf(cabeza)
        if (miembros.any { it.state == SMS_STATE_CONFIRMED }) return@mapNotNull null
        val propuesta = if (miembros.size > 1) propuestaDelGrupo(miembros) else cabeza
        when (val lectura = leerElAviso(uid, propuesta, sabe)) {
            is LecturaDelAviso.NoTraeMovimiento ->
                PropuestaDelAviso(avisoId = propuesta.id, grupoId = cabeza.grupoId, noSePudoLeer = lectura.motivo)
            is LecturaDelAviso.Leido -> PropuestaDelAviso(
                avisoId = propuesta.id,
                grupoId = cabeza.grupoId,
                leido = lectura.parsed,
                coincidencias = coincidenciasDelAviso(uid, propuesta, ahora),
                destinoSinNombre = ofreceGuardarElDestino(lectura.parsed.identificador(), sabe.destinos, cuentas, descartados),
            )
        }
    }
    return PropuestasDeLaBandeja(
        propuestas = propuestas,
        categoriasDelDueno = sabe.memoria.categoriasDelDueno.sorted(),
        inicioDeLasCuentas = inicioDeLasCuentasDe(uid),
    )
}

/** Cuenta → su primer movimiento vivo (epoch ms): de ahí arranca la cuenta en Movi. */
private fun Transaction.inicioDeLasCuentasDe(uid: String): Map<String, Long> {
    val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
    return Events.select(Events.id, Events.accountId, Events.timestamp)
        .where { Events.userId eq uid }
        .filterNot { it[Events.id] in anulados }
        .groupBy({ it[Events.accountId] }, { it[Events.timestamp] })
        .mapValues { (_, momentos) -> momentos.min() }
}

/**
 * `GET /api/sms/propuestas`: lo que Movi propone para cada pago pendiente de «Por revisar», con lo
 * que la app necesita para decidir cuáles confirma desde la fila. Solo los avisos del usuario.
 * Literal, así que gana sobre `/api/sms/{id}`.
 */
fun Route.bandejaQueSeVaciaRoutes() {
    get("/api/sms/propuestas") {
        val uid = call.userId()
        val ahora = System.currentTimeMillis()
        call.respond(conLoQueSeReparte(uid, dbQuery { propuestasDeLaBandeja(uid, ahora) }, ahora))
    }
}

/**
 * Marca las propuestas que son **la suma exacta de varios pagos del período** del tercero al que
 * fueron ([PropuestaDelAviso.seReparte]): esas se reparten en Reconciliar, y la fila no las anota
 * como un solo movimiento. Solo mira las salidas; [propuestaDeRepartoDe] corta enseguida las que no
 * van a nadie (una compra en un comercio).
 */
internal suspend fun conLoQueSeReparte(uid: String, leidas: PropuestasDeLaBandeja, ahora: Long): PropuestasDeLaBandeja =
    leidas.copy(
        propuestas = leidas.propuestas.map { propuesta ->
            if (propuesta.leido?.type != TransactionType.EXPENSE) propuesta
            else propuesta.copy(seReparte = propuestaDeRepartoDe(uid, propuesta.avisoId, ahora)?.exacto?.isNotEmpty() == true)
        },
    )
