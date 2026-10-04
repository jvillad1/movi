package com.jvillada.movi.ui.porrevisar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Lectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.alertaDeCapturaEnInicio
import com.jvillada.movi.shared.model.capturaDeSms
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.soloLoQueLlegoSolo
import com.jvillada.movi.shared.model.unoPorPago
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
 * **Cuánto hay por revisar**, sumando las tres fuentes. Una fuente en `null` —su lectura no
 * contestó— no suma: el número es una invitación a entrar, y adentro la bandeja dice cuál fuente
 * no se pudo leer. Lo que nunca hace es inventar pendientes que no se leyeron.
 */
fun cuantosPorRevisar(
    mensajes: List<SmsMessage>?,
    dias: List<EventDay>?,
    candidatos: List<FinancialEvent>?,
): Int =
    // Pagos, no avisos: el mismo pago avisado tres veces cuenta una.
    (mensajes?.let { pagosPorRevisar(it).size } ?: 0) +
        (dias?.let { renglonesQueEntraronSolos(it).size } ?: 0) +
        (candidatos?.size ?: 0)

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
): Boolean =
    mensajes != null && dias != null && candidatos != null &&
        cuantosPorRevisar(mensajes, dias, candidatos) == 0

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
) {
    val mensajes: List<SmsMessage>? get() = deMensajes.valor
    val candidatos: List<FinancialEvent>? get() = deCandidatos.valor
    val leyendoMensajes: Boolean get() = deMensajes.actualizando
    val leyendoCandidatos: Boolean get() = deCandidatos.actualizando

    /** Las dos lecturas terminaron, bien o mal: ya se puede decidir qué pintar. */
    val terminaron: Boolean get() = !leyendoMensajes && !leyendoCandidatos

    /** Alguna falló con lo de antes a la vista: lo que se ve es lo último que vimos. */
    val falloConAlgoALaVista: Boolean get() = deMensajes.falloConAlgoALaVista || deCandidatos.falloConAlgoALaVista
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
    return remember(mensajes, candidatos) { LecturasPorRevisar(mensajes, candidatos) }
}
