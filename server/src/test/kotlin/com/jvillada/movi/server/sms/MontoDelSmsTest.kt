package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.montoDelSms
import com.jvillada.movi.server.routes.parseSms
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **El monto de un SMS, cuando el banco escribe con comas.**
 *
 * Bancolombia manda las dos formas: `$80.894` a la colombiana y `$3,500,000.00` a la gringa. El
 * parser daba por sentada la primera, y con la segunda **no fallaba: mentía**.
 *
 * | SMS | De verdad | Lo que se leía antes |
 * |---|---|---|
 * | `$3,500,000.00` | 3.500.000 | nada: «no pude parsear» |
 * | `$20,417` | 20.417 | **20,42** |
 * | `$24,000.00` | 24.000 | **24** |
 *
 * El dueño solo vio el primero, porque es el único que se queja. Los otros dos llegaban como
 * sugerencia mil veces más chica a una bandeja de 96 mensajes esperando que él confirmara — y
 * confirmar es lo que crea el movimiento.
 *
 * Los textos de esta clase son **sus mensajes reales**, copiados de la bandeja.
 */
class MontoDelSmsTest {

    // ── A la gringa: coma para miles ──────────────────────────────────────────

    @Test
    fun `tres separadores de miles con decimales`() {
        assertEquals(3_500_000.0, montoDelSms("3,500,000.00"))
    }

    @Test
    fun `un separador de miles, sin decimales`() {
        // El que se leía como veinte pesos con cuarenta y dos.
        assertEquals(20_417.0, montoDelSms("20,417"))
    }

    @Test
    fun `un separador de miles con decimales`() {
        assertEquals(24_000.0, montoDelSms("24,000.00"))
        assertEquals(25_910.0, montoDelSms("25,910.00"))
    }

    // ── A la colombiana: punto para miles ─────────────────────────────────────

    @Test
    fun `lo que ya funcionaba sigue funcionando`() {
        assertEquals(80_894.0, montoDelSms("80.894"))
        assertEquals(1_234_567.89, montoDelSms("1.234.567,89"))
        assertEquals(115_113.07, montoDelSms("115.113,07"))
    }

    // ── La zona gris, resuelta a propósito del lado de los miles ─────────────

    /**
     * `1,234` podría ser mil doscientos treinta y cuatro o uno con doscientos treinta y cuatro
     * milésimos. Se elige miles porque estos mensajes hablan de **pesos colombianos**, donde tres
     * decimales no existen y los montos de cuatro cifras son el pan de cada día.
     */
    @Test
    fun `tres digitos despues del separador son miles`() {
        assertEquals(1_234.0, montoDelSms("1,234"))
        assertEquals(1_234.0, montoDelSms("1.234"))
    }

    /** Una o dos cifras detrás sí son decimales: nadie escribe miles con dos dígitos. */
    @Test
    fun `una o dos cifras despues del separador son decimales`() {
        assertEquals(3.5, montoDelSms("3,5"))
        assertEquals(1.5, montoDelSms("1.50"))
    }

    @Test
    fun `sin separadores`() {
        assertEquals(50_000.0, montoDelSms("50000"))
    }

    // ── Y el SMS entero, que es como llega ───────────────────────────────────

    /**
     * El mensaje exacto que el dueño tenía abierto en la pantalla de reconciliar cuando dijo
     * «veo un mensaje de error de parseo».
     */
    @Test
    fun `el retiro de la Fiducuenta ya se lee`() {
        val sms = "Bancolombia: Retiraste \$3,500,000.00 de tu cuenta *9586 Fiducuenta el " +
            "2026/09/10 13:35:32, hacia la cuenta *25318624146. ¿Dudas? 6045109009"

        val parsed = assertNotNull(parseSms(sms), "antes devolvía null y la pantalla decía «No pude parsear el SMS»")
        assertEquals(3_500_000.0, parsed.amount)
    }

    @Test
    fun `la transferencia de veinte mil ya no vale veinte pesos`() {
        val sms = "Bancolombia: Transferiste \$20,417 desde tu cuenta *8133 a la cuenta " +
            "* 43087514791 el 11/08/2026 a las 13:38."

        assertEquals(20_417.0, assertNotNull(parseSms(sms)).amount)
    }

    // ── El monto sin separadores, que es como escribe el correo ──────────────

    /**
     * El correo de Bancolombia pega los dígitos: «$386902». La regex del monto exigía grupos de tres
     * después del primer bloque y se quedaba con «386» — un pago de $386.902 propuesto de $386.
     */
    @Test
    fun `el pago de tarjeta del correo, con el monto pegado, se lee entero`() {
        val correo = "Notificación Transaccional Bancolombia: Pagaste \$386902 en la tarjeta de credito *3684 " +
            "desde la cuenta *8133, el 27/09/2026 09:17."
        assertEquals(386_902.0, assertNotNull(parseSms(correo, "Correo · Bancolombia")).amount)
        assertEquals(1_542_634.0, assertNotNull(parseSms("Pagaste \$1542634 en la tarjeta de credito *3684")).amount)
    }

    @Test
    fun `sin separadores tambien con COP y USD pegados, y con decimales`() {
        assertEquals(249_000.0, assertNotNull(parseSms("Compraste COP249000 en RAPPI")).amount)
        val usd = assertNotNull(parseSms("Compraste USD20 en ANTHROPIC"))
        assertEquals(20.0, usd.amount)
        assertEquals("USD", usd.currency)
        assertEquals(1_234.56, assertNotNull(parseSms("Pagaste \$1234,56 en TIENDA")).amount)
    }

    @Test
    fun `lo que se leia con separadores se sigue leyendo igual`() {
        // Uno por cada forma que ya existía: ninguno puede cambiar con la forma nueva.
        assertEquals(974_550.0, assertNotNull(parseSms("Pagaste \$974,550 en la tarjeta de credito *9208")).amount)
        assertEquals(3_500_000.0, assertNotNull(parseSms("Retiraste \$3,500,000.00 de tu cuenta *9586")).amount)
        assertEquals(93_900.0, assertNotNull(parseSms("Compraste COP93.900,00 en DTV*DIRECTVGO")).amount)
        assertEquals(4_178_163.0, assertNotNull(parseSms("Valor: \$ 4.178.163,00")).amount)
        assertEquals(138_600.0, assertNotNull(parseSms("Valor: \$ 138.600")).amount)
        assertEquals(115_113.07, assertNotNull(parseSms("Valor: \$ 115.113,07")).amount)
        assertEquals(1.5, assertNotNull(parseSms("Compraste \$1.50 en X")).amount)
        assertEquals(3.5, assertNotNull(parseSms("Compraste \$3,5 en X")).amount)
    }
}
