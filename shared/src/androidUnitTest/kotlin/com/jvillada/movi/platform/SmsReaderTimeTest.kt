package com.jvillada.movi.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Reloj del backfill: qué timestamp del inbox se usa para el campo `time` del wire.
 *
 * El caso que motiva todo esto es [dateSentGanaConColaDeEntregaLarga]: con el teléfono
 * apagado / en avión / sin cobertura, `DATE` (recepción) y `DATE_SENT` (envío del banco)
 * se separan más que la tolerancia de dedupe del server y el backfill mete una segunda
 * fila del mismo movimiento.
 */
class SmsReaderTimeTest {

    private val MIN = 60_000L
    private val HOUR = 60 * MIN
    private val AHORA = 1_775_000_000_000L // 2026-04-01T..., un instante plausible cualquiera

    // ── el camino feliz: DATE_SENT usable ────────────────────────────────────

    @Test
    fun dateSentUsableSeUsa() {
        val dateSent = AHORA
        val date = AHORA + 3_000 // 3s de latencia normal de entrega
        assertEquals(dateSent, effectiveSmsTime(dateSent = dateSent, date = date))
    }

    @Test
    fun dateSentGanaConColaDeEntregaLarga() {
        // El banco mandó a las 14:00; el teléfono reconectó 20 min después.
        val dateSent = AHORA
        val date = AHORA + 20 * MIN
        assertEquals(dateSent, effectiveSmsTime(dateSent = dateSent, date = date))
        assertNotEquals(date, effectiveSmsTime(dateSent = dateSent, date = date))
    }

    @Test
    fun dateSentIgualADateSeUsa() {
        assertEquals(AHORA, effectiveSmsTime(dateSent = AHORA, date = AHORA))
    }

    // ── ausente o basura: se cae a DATE ──────────────────────────────────────

    @Test
    fun dateSentCeroSeCaeADate() {
        // El caso frecuente: la ROM/el carrier no llenó la columna.
        assertEquals(AHORA, effectiveSmsTime(dateSent = 0L, date = AHORA))
    }

    @Test
    fun dateSentNegativoSeCaeADate() {
        assertEquals(AHORA, effectiveSmsTime(dateSent = -1L, date = AHORA))
    }

    @Test
    fun dateSentEnSegundosSeCaeADate() {
        // Bug clásico de provider: epoch en segundos, no en milisegundos → 1970.
        val enSegundos = AHORA / 1000
        assertEquals(AHORA, effectiveSmsTime(dateSent = enSegundos, date = AHORA))
    }

    // ── cordura hacia el futuro: nada se recibe antes de mandarse ────────────

    @Test
    fun dateSentMuyEnElFuturoSeCaeADate() {
        // Desfase de zona horaria del SMSC: horas "adelante" de la recepción.
        assertEquals(AHORA, effectiveSmsTime(dateSent = AHORA + 5 * HOUR, date = AHORA))
    }

    @Test
    fun dateSentApenasEnElFuturoSeUsa() {
        // Deriva de reloj chica entre SMSC y teléfono: sigue siendo el mismo evento.
        val dateSent = AHORA + MIN
        assertEquals(dateSent, effectiveSmsTime(dateSent = dateSent, date = AHORA))
    }

    @Test
    fun bordeDeDerivaHaciaAdelanteSeUsa() {
        val dateSent = AHORA + MAX_SMS_CLOCK_SKEW_MILLIS
        assertEquals(dateSent, effectiveSmsTime(dateSent = dateSent, date = AHORA))
        assertEquals(AHORA, effectiveSmsTime(dateSent = dateSent + 1, date = AHORA))
    }

    // ── cordura hacia el pasado: la cola del SMSC es larga pero no infinita ──

    @Test
    fun bordeDeColaHaciaAtrasSeUsa() {
        val dateSent = AHORA - MAX_SMSC_QUEUE_MILLIS
        assertEquals(dateSent, effectiveSmsTime(dateSent = dateSent, date = AHORA))
        assertEquals(AHORA, effectiveSmsTime(dateSent = dateSent - 1, date = AHORA))
    }

    @Test
    fun ventanaDeColaCubreUnFinDeSemanaSinCobertura() {
        assertTrue(MAX_SMSC_QUEUE_MILLIS >= 24 * HOUR, "la cola tiene que cubrir al menos un día apagado")
    }

    // ── sin referencia usable ────────────────────────────────────────────────

    @Test
    fun dateInservibleUsaDateSent() {
        // No hay contra qué chequear cordura; DATE_SENT positivo es lo mejor que hay.
        assertEquals(AHORA, effectiveSmsTime(dateSent = AHORA, date = 0L))
    }

    @Test
    fun ambosInserviblesDevuelveDate() {
        assertEquals(0L, effectiveSmsTime(dateSent = 0L, date = 0L))
    }

    // ── la asimetría deliberada: el id sigue anclado a DATE ──────────────────

    @Test
    fun idDerivaDeDateYTimeDeDateSent() {
        val dateSent = AHORA
        val date = AHORA + 20 * MIN
        val fila = rowToSmsMessage(address = "85540", date = date, dateSent = dateSent, body = "Compra aprobada")

        // id: idéntico al que producía el backfill antes de este cambio.
        assertEquals(stableSmsId("85540", date, "Compra aprobada"), fila.id)
        // time: alineado con el reloj del banco, igual que el camino en tiempo real.
        assertEquals(smsWireTime(dateSent), fila.time)
        assertNotEquals(smsWireTime(date), fila.time)
    }

    @Test
    fun sinDateSentElIdYElTimeVuelvenACoincidir() {
        val fila = rowToSmsMessage(address = "85540", date = AHORA, dateSent = 0L, body = "Compra aprobada")
        assertEquals(stableSmsId("85540", AHORA, "Compra aprobada"), fila.id)
        assertEquals(smsWireTime(AHORA), fila.time)
    }
}
