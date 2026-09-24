package com.jvillada.movi.ui.porrevisar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.alertaDeCapturaEnInicio
import com.jvillada.movi.shared.model.capturaDeSms
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.sms.mensajesMasRecientesPrimero

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
 * **Los movimientos que entraron solos** —por SMS, por un extracto, por OCR— y de los que el dueño
 * todavía no dijo si el monto y la categoría están bien.
 *
 * Se leen de todos los días y no de un período: lo que falta confirmar falta igual aunque sea del
 * mes pasado, y justamente lo viejo es lo que más fácil se olvida.
 */
fun entraronSolos(dias: List<EventDay>): List<FinancialEvent> =
    dias.flatMap { it.items }.filter { esperaEnPorConfirmar(it.reconciliationStatus) }

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
    (mensajes?.let { mensajesPorRevisar(it).size } ?: 0) +
        (dias?.let { entraronSolos(it).size } ?: 0) +
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
    return alertaDeCapturaEnInicio(capturaDeSms(mensajes.map { it.time }), silenciada)
}

/**
 * Las dos lecturas que la bandeja y Movimientos comparten: los mensajes del banco y los candidatos
 * a pago de tarjeta. Los movimientos no están acá porque Movimientos ya los lee para su lista, y
 * leerlos dos veces sería pagar la misma lectura por el renglón.
 *
 * Cada valor en `null` hasta que su lectura contestó bien; `leyendo*` distingue «todavía no» de
 * «falló». Un reintento que falla conserva lo último leído.
 */
@Stable
class LecturasPorRevisar internal constructor() {
    var mensajes: List<SmsMessage>? by mutableStateOf(null)
        internal set
    var candidatos: List<FinancialEvent>? by mutableStateOf(null)
        internal set
    var leyendoMensajes: Boolean by mutableStateOf(true)
        internal set
    var leyendoCandidatos: Boolean by mutableStateOf(true)
        internal set

    /** Las dos lecturas terminaron, bien o mal: ya se puede decidir qué pintar. */
    val terminaron: Boolean get() = !leyendoMensajes && !leyendoCandidatos
}

/**
 * Lee [LecturasPorRevisar] y las vuelve a leer cuando cambia [recarga] o cuando se guardó algo
 * desde una ventana modal ([LocalRefreshTick]). `intentar` y no `runCatching`: una lectura
 * cancelada (se navegó a otro lado, cambió la clave) no puede quedar contada como contestada.
 */
@Composable
fun rememberLecturasPorRevisar(recarga: Int): LecturasPorRevisar {
    val lecturas = remember { LecturasPorRevisar() }
    val refreshTick = LocalRefreshTick.current
    LaunchedEffect(recarga, refreshTick) {
        lecturas.leyendoMensajes = true
        intentar { Repositories.wallets.getSmsMessages() }.onSuccess { lecturas.mensajes = it }
        lecturas.leyendoMensajes = false
    }
    LaunchedEffect(recarga, refreshTick) {
        lecturas.leyendoCandidatos = true
        intentar { Repositories.wallets.getCardPaymentCandidates() }.onSuccess { lecturas.candidatos = it }
        lecturas.leyendoCandidatos = false
    }
    return lecturas
}
