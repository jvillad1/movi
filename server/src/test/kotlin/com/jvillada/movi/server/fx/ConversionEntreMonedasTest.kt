package com.jvillada.movi.server.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `convertirEntreMonedas` — la conversión automática que usa `VincularPagoDeDeudaRoutes.kt` para
 * pagar una tarjeta en otra moneda sin pedirle al dueño una segunda cifra.
 */
class ConversionEntreMonedasTest {

    @Test
    fun mismas_monedas_no_necesita_tasa() {
        assertEquals(1_008_902L, convertirEntreMonedas(1_008_902L, "COP", "COP", tasa = null))
    }

    @Test
    fun pesos_a_dolares_divide_por_la_tasa() {
        // El caso real: pagar la Master Black en dólares desde la cuenta en pesos.
        val tasa = TasaUsdCop(valor = 4_012.5, esRespaldo = false)
        assertEquals(251L, convertirEntreMonedas(1_007_138L, "COP", "USD", tasa))
    }

    @Test
    fun dolares_a_pesos_multiplica_por_la_tasa() {
        val tasa = TasaUsdCop(valor = 4_000.0, esRespaldo = false)
        assertEquals(4_000_000L, convertirEntreMonedas(1_000L, "USD", "COP", tasa))
    }

    @Test
    fun sin_tasa_no_se_puede_convertir() {
        assertNull(convertirEntreMonedas(1_000_000L, "COP", "USD", tasa = null))
    }

    @Test
    fun una_tasa_de_respaldo_cuenta_como_no_poder_convertir() {
        // $4.000 es la constante del código, no una tasa que alguien haya elegido para hoy: mejor
        // rechazar que escribir una deuda equivocada con pinta de exacta.
        val respaldo = TasaUsdCop(valor = 4_000.0, esRespaldo = true)
        assertNull(convertirEntreMonedas(1_000_000L, "COP", "USD", respaldo))
    }

    @Test
    fun una_tercera_moneda_no_se_inventa() {
        val tasa = TasaUsdCop(valor = 4_000.0, esRespaldo = false)
        assertNull(convertirEntreMonedas(1_000L, "EUR", "USD", tasa))
    }
}
