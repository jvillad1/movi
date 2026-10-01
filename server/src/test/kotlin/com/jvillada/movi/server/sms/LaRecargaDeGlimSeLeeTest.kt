package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.parseSms
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Las notificaciones de Glim (la tarjeta de alimentación) que llegaron el 1-oct-2026** y quedaron en
 * «Por revisar» sin poder leerse: la moneda va después del número («640.000,00 COP»), cosa que
 * ningún lector de monto conocía. Y el rechazo por fondos insuficientes dice «se rechazó», que no
 * es «rechazada».
 */
class LaRecargaDeGlimSeLeeTest {

    private val RECARGA =
        "Aleluya: ¡tienes nuevo saldo! 💳: Mercado Libre Colombia Ltda recargó 640.000,00 COP en tu tarjeta de beneficios."
    private val RECARGA_CHICA =
        "Aleluya: ¡tienes nuevo saldo! 💳: Mercado Libre Colombia Ltda recargó 55.500,00 COP en tu tarjeta de beneficios."
    private val RECHAZO =
        "Fondos insuficientes ⛔: Se rechazó tu pago por \$17.150,00 COP. Revisa tus saldos actualizados en la sección de beneficios de tu app."
    private val COMPRA =
        "¡Usaste tus beneficios!: Pagaste \$15.100,00 COP con tu tarjeta de beneficios Glim el 25/09/2026 a las 14:15 en TOSTAO CAFE Y PAN."

    @Test
    fun `la recarga es plata que entra, del empleador, como salario`() {
        val p = assertNotNull(parseSms(RECARGA, "Notificación · Glim"), "no parseó: $RECARGA")
        assertEquals(640_000.0, p.amount)
        assertEquals("COP", p.currency)
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals("Recarga de beneficios · Mercado Libre Colombia Ltda", p.merchant)
        assertEquals("Salario", p.category)
    }

    @Test
    fun `la recarga chica tambien`() {
        val p = assertNotNull(parseSms(RECARGA_CHICA, "Notificación · Glim"))
        assertEquals(55_500.0, p.amount)
        assertEquals(TransactionType.INCOME, p.type)
    }

    @Test
    fun `el pago rechazado por fondos insuficientes no es un movimiento`() {
        assertNull(parseSms(RECHAZO, "Notificación · Glim"))
    }

    @Test
    fun `la compra con la tarjeta de beneficios sigue siendo un gasto`() {
        val p = assertNotNull(parseSms(COMPRA, "Notificación · Glim"))
        assertEquals(15_100.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
    }
}
