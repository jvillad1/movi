package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El pago del 19 de septiembre, explicado
 *
 * El dueño pagó **$4.178.163** contra una cuota de **$4.101.123**: $77.040 de más, que se fueron
 * enteros a capital, y nada en la app se lo dijo. Estas pruebas fijan lo que la línea tiene que
 * poder decir —pagó de más, pagó de menos, el pago no cubrió los intereses, y pagó justo la cuota
 * (y entonces se calla)— con sus cifras, y que una cuota pagada en partes se juzga entera.
 *
 * Los insumos son los que la fila de la deuda **ya tiene guardados**: su monto (el capital que
 * bajó) y `noAmortiza` (interés + seguro + otros cargos de ese mes). No se recalcula nada.
 */
class PagoQueNoFueLaCuotaTest {

    /** Vehículo ·8761: cuota $4.101.123, con $2.479.256 de interés y $89.100 de seguro ese mes. */
    private val cuota = 4_101_123L
    private val noAmortiza = 2_479_256L + 89_100L

    private fun una(
        capital: Long,
        cargos: Long,
        cuotaPactada: Long = cuota,
        abierta: Boolean = false,
        moneda: String = "COP",
    ) = textoDeLaCuota(listOf(capital), listOf(cargos), cuotaPactada, abierta, moneda)

    private fun pago(id: String, fecha: LocalDate, capital: Long, noAmortiza: Long?) = FinancialEvent(
        id = id,
        accountId = "credito",
        type = TransactionType.INCOME,
        amount = capital,
        category = "Crédito",
        description = "Pago de cuota",
        timestamp = LocalDateTime(fecha.year, fecha.monthNumber, fecha.dayOfMonth, 12, 0)
            .toInstant(AppTimeZone.zone).toEpochMilliseconds(),
        noAmortiza = noAmortiza,
    )

    @Test
    fun el_pago_de_mas_dice_cuanto_y_que_bajo_la_deuda() {
        // Lo que de verdad pasó: pagó $4.178.163, así que el capital fue $77.040 más alto.
        val capital = 4_178_163L - noAmortiza
        assertEquals(1_609_807L, capital)

        val texto = assertNotNull(una(capital, noAmortiza))
        assertEquals(
            "Pagaste \$77.040 más que la cuota de \$4.101.123: ese extra bajó la deuda completo.",
            texto,
        )
    }

    @Test
    fun pagar_justo_la_cuota_no_dice_nada() {
        assertNull(una(cuota - noAmortiza, noAmortiza))
    }

    @Test
    fun el_pago_de_menos_dice_cuanto_capital_se_quedo_sin_abonar() {
        // Pagó $4.000.000 y la cuota ya cerró: $101.123 menos que la cuota, y $101.123 menos de capital.
        val texto = assertNotNull(una(4_000_000L - noAmortiza, noAmortiza))
        assertTrue(texto.startsWith("Pagaste \$101.123 menos que la cuota de \$4.101.123"), texto)
        assertTrue(texto.contains("la deuda bajó \$101.123 menos"), texto)
    }

    @Test
    fun un_pago_corto_con_la_cuota_abierta_no_dice_que_la_deuda_bajo_menos() {
        // Puede faltar la otra parte: todavía no es «de menos».
        val texto = assertNotNull(una(4_000_000L - noAmortiza, noAmortiza, abierta = true))
        assertEquals(
            "Llevas \$4.000.000 de la cuota de \$4.101.123: te faltan \$101.123 para completarla.",
            texto,
        )
        assertFalse(texto.contains("menos"), texto)
    }

    @Test
    fun un_pago_que_no_cubre_los_intereses_lo_dice_y_no_habla_de_la_diferencia() {
        // Un abono parcial a la libranza ·4818: $3.000.000 contra $3.646.011 de interés, con la
        // cuota ya cerrada. El capital se clampó a cero (ver desglosarCuota), así que de esta fila
        // no se puede deducir qué pagó — y lo que importa es que la deuda no se movió.
        val texto = assertNotNull(una(0L, 3_646_011L, cuotaPactada = 6_040_259L))
        assertEquals(
            "Este pago se fue entero en los \$3.646.011 de intereses y cargos de ese mes: " +
                "no bajó nada de la deuda.",
            texto,
        )
    }

    @Test
    fun sin_cuota_pactada_no_se_compara_contra_cero() {
        // Las condiciones que no se pudieron leer llegan como 0. Comparar contra 0 diría que pagó
        // los $4.178.163 enteros de más.
        assertNull(una(1_609_807L, noAmortiza, cuotaPactada = 0L))
    }

    @Test
    fun en_otra_moneda_la_cifra_se_dice_en_su_moneda() {
        val texto = assertNotNull(una(1_100L, 400L, cuotaPactada = 1_400L, moneda = "USD"))
        assertTrue(texto.contains("US\$100 más que la cuota de US\$1.400"), texto)
    }

    // ── La cuota pagada en partes ──────────────────────────────────────────────────────────

    /**
     * La libranza ·4818 (día 15): cuota $6.040.259, interés $3.646.011, pagada como $3.000.000 el
     * 10 + $3.040.259 el 14. La primera parte guarda el interés entero y capital 0; la segunda,
     * lo que faltaba de interés ($646.011) y $2.394.248 de capital.
     */
    private val libranza = 6_040_259L
    private val parte1 = pago("p1", LocalDate(2026, 9, 10), capital = 0L, noAmortiza = 3_646_011L)
    private val parte2 = pago("p2", LocalDate(2026, 9, 14), capital = 2_394_248L, noAmortiza = 646_011L)

    @Test
    fun la_segunda_parte_ya_no_dice_que_fue_menos_que_la_cuota() {
        // Con la cuota todavía abierta y ya cerrada: juntas fueron exactamente la cuota.
        for (hoy in listOf(LocalDate(2026, 9, 21), LocalDate(2026, 12, 1))) {
            val lineas = textosDeLosPagosQueNoFueronLaCuota(listOf(parte2, parte1), libranza, 15, hoy)
            val texto = assertNotNull(lineas["p2"], "hoy=$hoy")
            assertFalse(texto.contains("menos que la cuota"), texto)
            assertEquals("Con este pago completaste la cuota de \$6.040.259 en 2 partes.", texto)
            // Y la primera parte ya no dice que se fue entera en los intereses.
            assertNull(lineas["p1"], "hoy=$hoy")
        }
    }

    @Test
    fun la_primera_parte_sola_con_la_cuota_abierta_no_dice_que_no_bajo_nada() {
        val lineas = textosDeLosPagosQueNoFueronLaCuota(listOf(parte1), libranza, 15, LocalDate(2026, 9, 11))
        val texto = assertNotNull(lineas["p1"])
        assertTrue(texto.contains("sigue abierta"), texto)
        assertFalse(texto.contains("no bajó nada"), texto)
    }

    @Test
    fun dos_partes_que_suman_de_mas_lo_dicen_sobre_el_total() {
        val parte2DeMas = pago("p2", LocalDate(2026, 9, 14), capital = 2_394_248L + 50_000L, noAmortiza = 646_011L)
        val lineas = textosDeLosPagosQueNoFueronLaCuota(
            listOf(parte1, parte2DeMas), libranza, 15, LocalDate(2026, 9, 21),
        )
        assertEquals(
            "Entre los 2 pagos de esta cuota pagaste \$50.000 más que la cuota de \$6.040.259: " +
                "ese extra bajó la deuda completo.",
            lineas["p2"],
        )
    }

    @Test
    fun un_par_simetrico_no_tiene_nada_que_explicar() {
        // Una tarjeta o un crédito sin tasa: la deuda bajó por todo lo pagado, y `noAmortiza` es
        // null justamente para decir eso. Ver FinancialEvent.noAmortiza.
        val tarjeta = pago("t", LocalDate(2026, 9, 14), capital = 4_178_163L, noAmortiza = null)
        assertTrue(textosDeLosPagosQueNoFueronLaCuota(listOf(tarjeta), cuota, 15, LocalDate(2026, 9, 21)).isEmpty())
    }

    // ── La cuota que cambió ────────────────────────────────────────────────────────────────

    @Test
    fun despues_de_un_cambio_de_cuota_los_pagos_viejos_no_se_juzgan_contra_la_nueva() {
        // Agosto se pagó justo con la cuota de entonces ($4.101.123). Después la cuota subió a
        // $4.300.000, y septiembre se pagó con $50.000 de más. Solo septiembre se juzga: contra la
        // cuota nueva, agosto habría dicho «$198.877 menos que la cuota», y fue una cuota justa.
        val nueva = 4_300_000L
        val agosto = pago("ago", LocalDate(2026, 8, 30), capital = cuota - noAmortiza, noAmortiza = noAmortiza)
        val septiembre = pago("sep", LocalDate(2026, 9, 29), capital = nueva + 50_000L - noAmortiza, noAmortiza = noAmortiza)

        val lineas = textosDeLosPagosQueNoFueronLaCuota(listOf(septiembre, agosto), nueva, 30, LocalDate(2026, 10, 5))

        assertEquals(setOf("sep"), lineas.keys)
        assertEquals(
            "Pagaste \$50.000 más que la cuota de \$4.300.000: ese extra bajó la deuda completo.",
            lineas["sep"],
        )
    }
}
