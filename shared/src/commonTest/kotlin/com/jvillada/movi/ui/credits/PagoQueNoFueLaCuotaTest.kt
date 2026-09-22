package com.jvillada.movi.ui.credits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El pago del 19 de septiembre, explicado
 *
 * El dueño pagó **$4.178.163** contra una cuota de **$4.101.123**: $77.040 de más, que se fueron
 * enteros a capital, y nada en la app se lo dijo. Estas pruebas fijan las cuatro cosas que la
 * línea tiene que poder decir —pagó de más, pagó de menos, el pago no cubrió los intereses, y
 * pagó justo la cuota (y entonces se calla)— con sus cifras.
 *
 * Los insumos son los que la fila de la deuda **ya tiene guardados**: su monto (el capital que
 * bajó) y `noAmortiza` (interés + seguro + otros cargos de ese mes). No se recalcula nada.
 */
class PagoQueNoFueLaCuotaTest {

    /** Vehículo ·8761: cuota $4.101.123, con $2.479.256 de interés y $89.100 de seguro ese mes. */
    private val cuota = 4_101_123L
    private val noAmortiza = 2_479_256L + 89_100L

    @Test
    fun el_pago_de_mas_dice_cuanto_y_que_bajo_la_deuda() {
        // Lo que de verdad pasó: pagó $4.178.163, así que el capital fue $77.040 más alto.
        val capital = 4_178_163L - noAmortiza
        assertEquals(1_609_807L, capital)

        val texto = assertNotNull(textoDelPagoQueNoFueLaCuota(capital, noAmortiza, cuota))
        assertEquals(
            "Pagaste \$77.040 más que la cuota de \$4.101.123: ese extra bajó la deuda completo.",
            texto,
        )
    }

    @Test
    fun pagar_justo_la_cuota_no_dice_nada() {
        val capital = cuota - noAmortiza
        assertNull(textoDelPagoQueNoFueLaCuota(capital, noAmortiza, cuota))
    }

    @Test
    fun el_pago_de_menos_dice_cuanto_capital_se_quedo_sin_abonar() {
        // Pagó $4.000.000: $101.123 menos que la cuota, y por lo tanto $101.123 menos de capital.
        val capital = 4_000_000L - noAmortiza
        val texto = assertNotNull(textoDelPagoQueNoFueLaCuota(capital, noAmortiza, cuota))
        assertTrue(texto.startsWith("Pagaste \$101.123 menos que la cuota de \$4.101.123"), texto)
        assertTrue(texto.contains("la deuda bajó \$101.123 menos"), texto)
    }

    @Test
    fun un_pago_que_no_cubre_los_intereses_lo_dice_y_no_habla_de_la_diferencia() {
        // Un abono parcial a la libranza ·4818: $3.000.000 contra $3.646.011 de interés. El capital
        // se clampó a cero (ver desglosarCuota), así que de esta fila no se puede deducir qué pagó
        // — y lo que importa es que la deuda no se movió.
        val texto = assertNotNull(textoDelPagoQueNoFueLaCuota(0L, 3_646_011L, 6_040_259L))
        assertEquals(
            "Este pago se fue entero en los \$3.646.011 de intereses y cargos de ese mes: " +
                "no bajó nada de la deuda.",
            texto,
        )
    }

    @Test
    fun un_par_simetrico_no_tiene_nada_que_explicar() {
        // Una tarjeta o un crédito sin tasa: la deuda bajó por todo lo pagado, y `noAmortiza` es
        // null justamente para decir eso. Ver FinancialEvent.noAmortiza.
        assertNull(textoDelPagoQueNoFueLaCuota(4_178_163L, null, cuota))
    }

    @Test
    fun sin_cuota_pactada_no_se_compara_contra_cero() {
        // Las condiciones que no se pudieron leer llegan como 0. Comparar contra 0 diría que pagó
        // los $4.178.163 enteros de más.
        assertNull(textoDelPagoQueNoFueLaCuota(1_609_807L, noAmortiza, cuotaPactada = 0L))
    }

    @Test
    fun en_otra_moneda_la_cifra_se_dice_en_su_moneda() {
        val texto = assertNotNull(
            textoDelPagoQueNoFueLaCuota(1_100L, 400L, cuotaPactada = 1_400L, moneda = "USD"),
        )
        assertTrue(texto.contains("US\$100 más que la cuota de US\$1.400"), texto)
    }
}
