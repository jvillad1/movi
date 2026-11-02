package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.shared.model.PropuestaDeSaldar
import com.jvillada.movi.shared.model.PropuestaDeRepartir
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Lectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.alertaDeCapturaEnInicio
import com.jvillada.movi.shared.model.capturaDeSms
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.soloLoQueLlegoSolo
import com.jvillada.movi.shared.model.unoPorPago
import com.jvillada.movi.shared.model.separarLosPagosAntiguos
import com.jvillada.movi.shared.model.PataPorConfirmar
import com.jvillada.movi.shared.model.renglonesPorConfirmar
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.sms.mensajesMasRecientesPrimero
import com.jvillada.movi.ui.transactions.MovementRow
import com.jvillada.movi.ui.transactions.collapseTransfers

/**
 * # «Por revisar»: una sola bandeja para lo que entró solo
 *
 * Hasta la ola C lo que esperaba una decisión del dueño vivía en dos lugares: los mensajes del
 * banco por confirmar en su propia pantalla (dentro de Más, mezclados con los permisos de la
 * captura) y, en Movimientos, un aviso de «N entraron solos» y otro de «N pagos de tarjeta sin
 * marcar». Tres fuentes, dos pantallas, y ninguna decía cuánto había en total.
 *
 * Este archivo es la parte sin dibujo: qué cuenta como pendiente en cada fuente y cuánto suman.
 * Lo usan **las dos** superficies —la bandeja ([PorRevisarScreen]) y el renglón «N por revisar»
 * de Movimientos— para que el número del renglón y lo que se ve al tocarlo no puedan diferir.
 */

/** Los mensajes del banco que esperan que el dueño los confirme o los ignore, del más nuevo al más viejo. */
fun mensajesPorRevisar(mensajes: List<SmsMessage>): List<SmsMessage> =
    mensajesMasRecientesPrimero(mensajes).filter { it.state == SMS_STATE_PENDING }

/**
 * **Los pagos que esperan**, uno por pago y del más nuevo al más viejo (4-oct-2026): un pago
 * avisado por SMS, por la app del banco y por Google Wallet es UNA tarjeta, la del aviso más
 * reciente de los tres. Es lo que pinta la bandeja y lo que cuenta [cuantosPorRevisar], con la
 * misma regla que el Inicio ([unoPorPago], en `:core`).
 */
fun pagosPorRevisar(mensajes: List<SmsMessage>): List<SmsMessage> = unoPorPago(mensajesPorRevisar(mensajes))

/**
 * **Los pagos de los últimos 30 días** (vuelta 51), del más nuevo al más viejo: lo que va arriba en la
 * bandeja, lo que cuenta «N por revisar» y lo que entra en «Confirmar los N». Los de más de 30 días
 * ([pagosAntiguosPorRevisar]) se apartan; la frontera es `esPagoAntiguo`, en `:core`.
 */
fun pagosRecientesPorRevisar(mensajes: List<SmsMessage>, ahora: Long = ahoraEnMilis()): List<SmsMessage> =
    separarLosPagosAntiguos(pagosPorRevisar(mensajes), mensajes, ahora).recientes

/** **Los pagos de más de 30 días**, del más nuevo al más viejo: «Más antiguos · N», plegado (vuelta 51). */
fun pagosAntiguosPorRevisar(mensajes: List<SmsMessage>, ahora: Long = ahoraEnMilis()): List<SmsMessage> =
    separarLosPagosAntiguos(pagosPorRevisar(mensajes), mensajes, ahora).antiguos

private fun ahoraEnMilis(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

/**
 * **Los movimientos que entraron solos** —por SMS, por un extracto, por OCR— y de los que el dueño
 * todavía no dijo si el monto y la categoría están bien.
 *
 * Se leen de todos los días y no de un período: lo que falta confirmar falta igual aunque sea del
 * mes pasado, y justamente lo viejo es lo que más fácil se olvida.
 */
fun entraronSolos(dias: List<EventDay>): List<FinancialEvent> =
    dias.flatMap { it.items }.filter { esperaEnPorConfirmar(it.reconciliationStatus) }

/**
 * **Los renglones de «Entraron solos»**, tal como la bandeja los dibuja: un traspaso —su salida y
 * su entrada, dos eventos— es un solo renglón (ver [collapseTransfers]). El número de la sección y
 * el del renglón de Movimientos cuentan **esto**, no los eventos: si contaran los eventos, «2 por
 * revisar» abriría una bandeja con una sola cosa adentro.
 */
fun renglonesQueEntraronSolos(dias: List<EventDay>): List<MovementRow> = collapseTransfers(entraronSolos(dias))

/**
 * **Lo que hay por revisar, fuente por fuente** (vuelta de diseño 22). Es el único contador de «Por
 * revisar»: la insignia de «Avisos» y «Movi propone hoy» en el Inicio, el renglón «N por revisar» de
 * Movimientos y la campana leen esto, así que no pueden decir números distintos —en la revisión del
 * diseñador la insignia decía 2, la propuesta «Llegaron 2 avisos» y Movimientos «3 por revisar»: el
 * tercero era un movimiento que entró solo, y nadie lo decía—. Donde se ve el total se dice también
 * de qué es ([desgloseDePorRevisar]).
 */
data class ConteoPorRevisar(
    /** Avisos del banco por confirmar, **uno por pago** (el mismo pago avisado tres veces cuenta uno). */
    val avisos: Int = 0,
    /** Movimientos que entraron solos y esperan en «Por confirmar», contados como renglones. */
    val movimientos: Int = 0,
    /** Pagos de tarjeta que Movi encontró y esperan que el dueño los confirme. */
    val pagosDeTarjeta: Int = 0,
    /** Lo que el banco cobró solo y Movi propone armado. */
    val debitos: Int = 0,
    /**
     * Vuelta 51 · **los avisos de más de 30 días**, uno por pago. Esperan en la bandeja, plegados en
     * «Más antiguos», pero **no** entran en [total]: no compiten con los de hoy. La franja del Inicio
     * los menciona aparte («y 2 antiguos»).
     */
    val avisosAntiguos: Int = 0,
    /**
     * Vuelta 67 · **las transferencias que saldan un grupo** («Para saldar en tus grupos», una por
     * propuesta). Esperan una decisión en la bandeja como lo demás: sin contarlas, Por revisar decía
     * «Todo al día» debajo de una y la insignia del Inicio no la nombraba.
     */
    val saldar: Int = 0,
    /**
     * Vuelta 97 · **lo que Movi propone repartir** («Para repartir en tus grupos»). Como [saldar]: espera
     * una decisión en la bandeja, así que cuenta y con una a la vista no se dice «Todo al día».
     */
    val repartir: Int = 0,
) {
    /** Todo lo que espera en la bandeja, sin los avisos antiguos ([avisosAntiguos]). */
    val total: Int get() = avisos + movimientos + pagosDeTarjeta + debitos + saldar + repartir

    /**
     * Lo que **entró solo** —avisos, movimientos, pagos de tarjeta—, sin los débitos: en «Movi propone
     * hoy» y en la cola los débitos van aparte, uno por uno, porque la plata ya salió.
     */
    val loQueEntroSolo: Int get() = avisos + movimientos + pagosDeTarjeta
}

/**
 * **De qué es el número**, en una línea: «2 avisos · 1 movimiento», «1 pago de tarjeta · 1 movimiento
 * esperado». Solo las fuentes que tienen algo; `null` sin nada. [conDebitos] en `false` deja afuera
 * los débitos (cuando la cifra que acompaña es [ConteoPorRevisar.loQueEntroSolo]).
 */
fun desgloseDePorRevisar(conteo: ConteoPorRevisar, conDebitos: Boolean = true): String? = listOfNotNull(
    conteo.avisos.takeIf { it > 0 }?.let { if (it == 1) "1 aviso" else "$it avisos" },
    conteo.movimientos.takeIf { it > 0 }?.let { if (it == 1) "1 movimiento" else "$it movimientos" },
    conteo.pagosDeTarjeta.takeIf { it > 0 }?.let { if (it == 1) "1 pago de tarjeta" else "$it pagos de tarjeta" },
    // Vuelta 90: el bloque ya no es solo débitos automáticos (también la nómina que no se ve).
    conteo.debitos.takeIf { it > 0 && conDebitos }?.let { if (it == 1) "1 movimiento esperado" else "$it movimientos esperados" },
    conteo.saldar.takeIf { it > 0 }?.let { if (it == 1) "1 para saldar" else "$it para saldar" },
    conteo.repartir.takeIf { it > 0 }?.let { if (it == 1) "1 para repartir" else "$it para repartir" },
).joinToString(" · ").ifEmpty { null }

/**
 * **Cuánto hay por revisar**, fuente por fuente, sobre lo que la bandeja bajó. Una fuente en `null`
 * —su lectura no contestó— no suma: el número es una invitación a entrar, y adentro la bandeja dice
 * cuál fuente no se pudo leer. Lo que nunca hace es inventar pendientes que no se leyeron.
 */
fun conteoPorRevisar(
    mensajes: List<SmsMessage>?,
    dias: List<EventDay>?,
    candidatos: List<FinancialEvent>?,
    /** Lo que el banco cobró solo y Movi propone armado (ver [DebitoAutomaticoPorConfirmar]). */
    debitos: List<DebitoAutomaticoPorConfirmar>? = null,
    ahora: Long = ahoraEnMilis(),
    /** Vuelta 67: las transferencias que saldan un grupo (ver [ConteoPorRevisar.saldar]). */
    propuestasDeSaldar: List<PropuestaDeSaldar>? = null,
    /** Vuelta 97: lo que Movi propone repartir (ver [ConteoPorRevisar.repartir]). */
    propuestasDeRepartir: List<PropuestaDeRepartir>? = null,
): ConteoPorRevisar = ConteoPorRevisar(
    // Pagos, no avisos: el mismo pago avisado tres veces cuenta una. Vuelta 51: los de los últimos 30 días.
    avisos = mensajes?.let { pagosRecientesPorRevisar(it, ahora).size } ?: 0,
    avisosAntiguos = mensajes?.let { pagosAntiguosPorRevisar(it, ahora).size } ?: 0,
    // Con la regla de `:core` que usa el server para el Inicio: un traspaso es un renglón.
    movimientos = dias?.let { d -> renglonesPorConfirmar(entraronSolos(d).map { PataPorConfirmar(it.id, it.transferId, it.type) }) } ?: 0,
    pagosDeTarjeta = candidatos?.size ?: 0,
    debitos = debitos?.size ?: 0,
    saldar = propuestasDeSaldar?.size ?: 0,
    repartir = propuestasDeRepartir?.size ?: 0,
)

/** El total de [conteoPorRevisar]: lo que dice «N por revisar». */
fun cuantosPorRevisar(
    mensajes: List<SmsMessage>?,
    dias: List<EventDay>?,
    candidatos: List<FinancialEvent>?,
    debitos: List<DebitoAutomaticoPorConfirmar>? = null,
    propuestasDeSaldar: List<PropuestaDeSaldar>? = null,
    /** Vuelta 97: lo que Movi propone repartir, ya sin lo descartado ([propuestasDeRepartirALaVista]). */
    propuestasDeRepartir: List<PropuestaDeRepartir>? = null,
): Int = conteoPorRevisar(
    mensajes, dias, candidatos, debitos,
    propuestasDeSaldar = propuestasDeSaldar, propuestasDeRepartir = propuestasDeRepartir,
).total

/**
 * Vuelta 97 · **las propuestas de repartir que cuentan**, sin las descartadas o repartidas en esta
 * sesión: la misma lista para el Inicio (`propuestasDeRepartirDelInicio`), la bandeja y «N por revisar»
 * de Movimientos, así los tres dicen el mismo número.
 */
fun propuestasDeRepartirALaVista(propuestas: List<PropuestaDeRepartir>?): List<PropuestaDeRepartir>? =
    propuestas?.let {
        com.jvillada.movi.ui.espacios.propuestasDeRepartirVisibles(it, com.jvillada.movi.ui.dashboard.AnomaliasDescartadasEnLaSesion.huellas)
    }

/** Lo que dice el renglón de Movimientos. Sin plural que conjugar: «1 por revisar», «4 por revisar». */
fun textoDePorRevisar(cuantos: Int): String = "$cuantos por revisar"

/**
 * **¿Se puede decir «Todo al día»?** Solo si las tres lecturas contestaron bien y ninguna trajo
 * nada. Una lectura en vuelo o caída no es «no hay nada»: es «no sé», y afirmar lo contrario es el
 * mismo error que la ola A le sacó a cada lista de la app.
 */
fun bandejaAlDia(
    mensajes: List<SmsMessage>?,
    dias: List<EventDay>?,
    candidatos: List<FinancialEvent>?,
    /** Default vacío: quien no pregunta por los débitos automáticos no los cuenta (ver [cuantosPorRevisar]). */
    debitos: List<DebitoAutomaticoPorConfirmar>? = emptyList(),
    /**
     * Vuelta 67: las transferencias que saldan un grupo. Con una a la vista, la bandeja no está al día
     * (antes decía «Todo al día» debajo de la propuesta). Default vacío, como [debitos].
     */
    propuestasDeSaldar: List<PropuestaDeSaldar>? = emptyList(),
    /** Vuelta 97: lo que Movi propone repartir. Default vacío, como [propuestasDeSaldar]. */
    propuestasDeRepartir: List<PropuestaDeRepartir>? = emptyList(),
): Boolean =
    mensajes != null && dias != null && candidatos != null && debitos != null && propuestasDeSaldar != null &&
        // Vuelta 97: si la lectura de las propuestas de repartir falla, se toma por vacía (es secundaria).
        // Los antiguos no cuentan en el número, pero esperan: con alguno, la bandeja no está al día.
        conteoPorRevisar(mensajes, dias, candidatos, debitos, propuestasDeSaldar = propuestasDeSaldar, propuestasDeRepartir = propuestasDeRepartir.orEmpty())
            .let { it.total == 0 && it.avisosAntiguos == 0 }

/**
 * El renglón «la captura dejó de andar» de la bandeja, o `null` si no hay nada que decir.
 *
 * **La misma condición que el aviso del Inicio** ([alertaDeCapturaEnInicio]), sobre los mismos
 * mensajes que la bandeja ya bajó: si el dueño la silenció allá, acá tampoco aparece, y se apaga
 * sola con el primer mensaje que llegue. Sin la lista o sin el perfil no se afirma nada — un
 * `null` no es «nunca llegó nada».
 */
fun avisoDeCapturaEnLaBandeja(mensajes: List<SmsMessage>?, silenciada: Boolean?): String? {
    if (mensajes == null || silenciada == null) return null
    // Sin los comprobantes (Ola 2): uno compartido hoy no prueba que el teléfono siga capturando.
    return alertaDeCapturaEnInicio(capturaDeSms(soloLoQueLlegoSolo(mensajes).map { it.time }), silenciada)
}

/**
 * Las dos lecturas que la bandeja y Movimientos comparten: los mensajes del banco y los candidatos
 * a pago de tarjeta. Los movimientos no están acá porque Movimientos ya los lee para su lista, y
 * leerlos dos veces sería pagar la misma lectura por el renglón.
 *
 * Cada valor en `null` hasta que su lectura contestó bien (en esta visita o en una reciente, ver
 * [rememberLectura]); `leyendo*` distingue «todavía no» de «falló». Un reintento que falla conserva
 * lo último leído.
 */
@Stable
class LecturasPorRevisar internal constructor(
    private val deMensajes: Lectura<List<SmsMessage>>,
    private val deCandidatos: Lectura<List<FinancialEvent>>,
    private val deDebitos: Lectura<List<DebitoAutomaticoPorConfirmar>>,
    private val deSaldar: Lectura<List<PropuestaDeSaldar>>,
    private val deRepartir: Lectura<List<PropuestaDeRepartir>>? = null,
) {
    val mensajes: List<SmsMessage>? get() = deMensajes.valor
    val candidatos: List<FinancialEvent>? get() = deCandidatos.valor
    /** Lo que el banco cobró solo y Movi propone armado; `null` hasta que la lectura contestó. */
    val debitos: List<DebitoAutomaticoPorConfirmar>? get() = deDebitos.valor
    val leyendoMensajes: Boolean get() = deMensajes.actualizando
    val leyendoCandidatos: Boolean get() = deCandidatos.actualizando
    val leyendoDebitos: Boolean get() = deDebitos.actualizando
    /**
     * Vuelta 67 · las transferencias que saldan un grupo; `null` hasta que la lectura contestó. Es
     * secundaria: si falla, quien la usa la toma por vacía (la bandeja no se queda sin poder decir
     * «Todo al día» porque esta lectura no llegó).
     */
    val propuestasDeSaldar: List<PropuestaDeSaldar>? get() = deSaldar.valor
    val leyendoSaldar: Boolean get() = deSaldar.actualizando
    /**
     * Vuelta 97 · lo que Movi propone repartir; `null` hasta que la lectura contestó. Secundaria, como
     * [propuestasDeSaldar]: si falla, se toma por vacía.
     */
    val propuestasDeRepartir: List<PropuestaDeRepartir>? get() = deRepartir?.valor
    val leyendoRepartir: Boolean get() = deRepartir?.actualizando == true

    /** Las lecturas terminaron, bien o mal: ya se puede decidir qué pintar. */
    val terminaron: Boolean get() = !leyendoMensajes && !leyendoCandidatos && !leyendoDebitos && !leyendoSaldar && !leyendoRepartir

    /** Alguna falló con lo de antes a la vista: lo que se ve es lo último que vimos. */
    val falloConAlgoALaVista: Boolean get() =
        deMensajes.falloConAlgoALaVista || deCandidatos.falloConAlgoALaVista || deDebitos.falloConAlgoALaVista
}

/**
 * Lee [LecturasPorRevisar] —empezando por lo último que se vio— y las vuelve a leer cuando cambia
 * [recarga] o cuando se guardó algo desde una ventana modal ([LocalRefreshTick], adentro de
 * [rememberLectura]).
 */
@Composable
fun rememberLecturasPorRevisar(recarga: Int): LecturasPorRevisar {
    val mensajes = rememberLectura(ClaveDeLectura.MensajesDelBanco, recarga) { Repositories.wallets.getSmsMessages() }
    val candidatos = rememberLectura(ClaveDeLectura.CandidatosPagoDeTarjeta, recarga) {
        Repositories.wallets.getCardPaymentCandidates()
    }
    val debitos = rememberLectura(ClaveDeLectura.DebitosAutomaticos, recarga) { Repositories.wallets.getDebitosAutomaticos() }
    val saldar = rememberLectura(ClaveDeLectura.PropuestasDeSaldar, recarga) { Repositories.wallets.getPropuestasDeSaldar() }
    val repartir = rememberLectura(ClaveDeLectura.PropuestasDeRepartir, recarga) { Repositories.wallets.getPropuestasDeRepartir() }
    return remember(mensajes, candidatos, debitos, saldar, repartir) { LecturasPorRevisar(mensajes, candidatos, debitos, saldar, repartir) }
}
