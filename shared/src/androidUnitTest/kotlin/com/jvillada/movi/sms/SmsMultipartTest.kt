package com.jvillada.movi.sms

import com.jvillada.movi.platform.rowToSmsMessage
import com.jvillada.movi.platform.smsWireTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato entre los DOS caminos de captura para un SMS multiparte (>160 caracteres).
 *
 * El dedupe del server compara (texto, tiempo) — `SmsDedupe.SmsKey` — porque los ids de los
 * dos caminos son distintos a propósito (`sms_rt_` vs `sms_`). Si el reensamblado del camino
 * en tiempo real (`SmsRealtimeReceiver` junta los PDUs del broadcast) no da EXACTAMENTE el
 * mismo string que la columna `body` que el proveedor guardó en el inbox — la que lee el
 * backfill —, el dedupe no reconoce el mensaje y el dueño ve la misma compra dos veces.
 *
 * Estos tests fijan que ningún camino recorte, normalice ni agregue separadores. La parte del
 * tiempo real vive en [joinMultipartBody] (función pura en :shared) justo para poder pinnearla
 * acá: adentro del BroadcastReceiver, atada a `android.telephony.SmsMessage`, no se puede
 * testear sin dispositivo.
 */
class SmsMultipartTest {

    // Un aviso de compra real de Bancolombia, largo: no entra en un solo SMS de 160.
    private val fullText =
        "Bancolombia le informa compra por $189.900 en ALMACENES EXITO CALLE 80 con Tarjeta " +
            "*1234 el 21/08/2026 a las 19:42. Cupo disponible $2.310.100. Si no la reconoce " +
            "comuniquese al 018000931987 o al #444 desde su celular."

    /** Segmentos GSM 7-bit de un multiparte: 153 caracteres útiles por parte (7 son del UDH). */
    private fun segments(text: String) = text.chunked(153)

    @Test
    fun `un SMS multiparte da el mismo texto por tiempo real que por backfill`() {
        val parts = segments(fullText)
        assertTrue(parts.size > 1, "el mensaje de prueba tiene que ser multiparte")

        // Tiempo real: los PDUs del broadcast, reensamblados.
        val realtime = joinMultipartBody(parts)
        // Backfill: la fila del inbox, que el proveedor ya guardó reensamblada.
        val backfill = rowToSmsMessage(address = SENDER, date = TS, dateSent = TS, body = fullText).text

        assertEquals(fullText, realtime)
        assertEquals(backfill, realtime)
    }

    @Test
    fun `los dos caminos suben el mismo texto y la misma hora, o sea la misma clave de dedupe`() {
        val body = joinMultipartBody(segments(fullText))
        val tiempoReal = captureItem(
            id = smsRealtimeId(SENDER, TS, body),
            sender = SENDER,
            body = body,
            ts = TS,
        )
        val fila = rowToSmsMessage(address = SENDER, date = TS, dateSent = TS, body = fullText)
        val backfill = SmsSyncItem(id = fila.id, time = fila.time, bank = fila.bank, text = fila.text)

        // (texto, tiempo) es la clave que compara SmsDedupe en el server.
        assertEquals(backfill.text, tiempoReal.text)
        assertEquals(backfill.time, tiempoReal.time)
        // Los ids SÍ difieren a propósito (el hook de push del server está acotado a sms_rt_).
        assertTrue(tiempoReal.id.startsWith("sms_rt_"))
        assertTrue(backfill.id.startsWith("sms_"))
    }

    @Test
    fun `el reensamblado no pone separador ni recorta los espacios de los bordes`() {
        // Una parte que TERMINA en espacio y la siguiente que arranca con texto: ese espacio
        // es parte del mensaje. Un joinToString con separador, o un trim por parte, lo perdería
        // y el texto dejaría de coincidir con el del inbox.
        assertEquals("hola mundo", joinMultipartBody(listOf("hola ", "mundo")))
        // Y los espacios de los extremos del mensaje entero tampoco se tocan.
        assertEquals("  saldo  ", joinMultipartBody(listOf("  saldo", "  ")))
    }

    @Test
    fun `una parte vacia o nula no agrega nada`() {
        assertEquals("abc", joinMultipartBody(listOf("a", null, "b", "", "c")))
        assertEquals("", joinMultipartBody(emptyList()))
    }

    @Test
    fun `una sola parte pasa tal cual`() {
        assertEquals(fullText, joinMultipartBody(listOf(fullText)))
    }

    @Test
    fun `el salto de pagina se normaliza igual que lo guarda el proveedor`() {
        // El proveedor arma la fila del inbox con replaceFormFeeds (salto de página -> salto
        // de línea). Sin esta normalización, el mismo SMS se subiría con dos textos distintos.
        assertEquals("Compra\nen EXITO", joinMultipartBody(listOf("Compra\u000Cen EXITO")))
        assertEquals("Compra\nen EXITO", joinMultipartBody(listOf("Compra\u000C", "en EXITO")))
    }

    @Test
    fun `la hora del wire es la misma funcion en los dos caminos`() {
        // captureItem (tiempo real) y smsWireTime (backfill) formatean el mismo instante: si uno
        // de los dos cambiara de patrón o de zona, el dedupe por tiempo se rompería.
        assertEquals(smsWireTime(TS), captureItem("sms_rt_x", SENDER, "hola", TS).time)
    }

    private companion object {
        const val SENDER = "87400"
        const val TS = 1_787_000_000_000L
    }
}
