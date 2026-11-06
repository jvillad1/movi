package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.CuotaPagada
import com.jvillada.movi.shared.model.LaCuotaQueCambia
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.TipoDeCuota
import com.jvillada.movi.ui.quickadd.cuotaPropuesta
import com.jvillada.movi.ui.recurrentes.lineaDeLaCuotaAnterior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vuelta 133 · lo que dicen Pagos, la ficha y Agregar → Pagar de una cuota que cambia. */
class LaCuotaQueCambiaEnPantallaTest {

    private val libre = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 40_000_000)
    private val terms = CreditTerms(
        accountId = libre.id, bank = "Bancolombia", principal = 50_000_000, rateEa = 18.0, termMonths = 72,
        installment = 1_200_000, dayOfMonth = 5, startDate = "2025-01-10",
    )
    private val octubre = CuotaPagada(1_262_300, "2026-10")

    @Test
    fun `Agregar Pagar propone la cuota de este mes`() {
        assertEquals(1_200_000L, cuotaPropuesta(libre, terms, octubre), "fija: la de siempre")
        assertEquals(1_262_300L, cuotaPropuesta(libre, terms.copy(tipoDeCuota = TipoDeCuota.TASA_VARIABLE), octubre))
        assertEquals(1_200_000L, cuotaPropuesta(libre, terms.copy(tipoDeCuota = TipoDeCuota.UVR), null))
    }

    @Test
    fun `la fila de Pagos dice la de octubre debajo de unos X`() {
        val fila = PagoDelPeriodo(
            ruleId = "credit_l9695", nombre = "Cuota Libre inversión 9695", monto = 1_262_300, pagado = false,
            diasParaVencer = 3, cuotaQueCambia = LaCuotaQueCambia(octubre),
        )
        assertEquals("Como la de octubre", lineaDeLaCuotaAnterior(fila), "la estimación es la anterior")
        assertEquals("La de octubre fue \$1.262.300", lineaDeLaCuotaAnterior(fila.copy(monto = 1_300_000)))
        assertNull(lineaDeLaCuotaAnterior(fila.copy(cuotaQueCambia = null)), "una cuota fija no dice nada más")
        assertNull(lineaDeLaCuotaAnterior(fila.copy(cuotaQueCambia = LaCuotaQueCambia(null))))
    }

    @Test
    fun `la ficha dice unos X y por que cambia`() {
        val variable = terms.copy(tipoDeCuota = TipoDeCuota.TASA_VARIABLE, puntosSobreElIndice = 8.5)
        val lineas = lineasDeLaCuotaQueCambia(variable, octubre, plan = null)
        assertTrue(lineas.first().startsWith("La tasa es variable (IBR + puntos) · IBR + 8,5"), lineas.toString())
        assertEquals("Este mes, unos \$1.262.300, como la de octubre", lineas[1])
        // Con la cuota de este período ya pagada se dice lo pagado, no una estimación.
        assertEquals("Este mes pagaste \$1.262.300", lineasDeLaCuotaQueCambia(variable, octubre, plan = null, periodoActual = PeriodoFinanciero(2026, 10))[1])
        assertTrue(lineasDeLaCuotaQueCambia(terms, octubre, plan = null).isEmpty(), "una cuota fija no lleva líneas")
        val boton = textoDeLaCuotaEnLaFicha(LaCuotaEnLaFicha(PeriodoFinanciero(2026, 11), pagada = false, monto = 1_262_300, cambia = true, anterior = octubre))
        assertEquals("Pagar la cuota de noviembre · unos \$1.262.300", boton)
        assertEquals(
            "Pagar la cuota de noviembre · \$1.200.000",
            textoDeLaCuotaEnLaFicha(LaCuotaEnLaFicha(PeriodoFinanciero(2026, 11), pagada = false, monto = 1_200_000)),
        )
    }

    /**
     * Vuelta 134 (N-4) · con corte 25 y la cuota el 30, la del período de noviembre (25-oct → 24-nov) vence el
     * 30 de octubre y el server la sella «2026-10». La ficha comparaba con el prefijo del período («2026-11»)
     * y seguía diciendo «Este mes, unos $X» con la cuota ya pagada.
     */
    @Test
    fun `con corte 25 y cuota del 30 la ficha dice lo pagado`() {
        val variable = terms.copy(tipoDeCuota = TipoDeCuota.TASA_VARIABLE, dayOfMonth = 30)
        val corte25 = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 25)
        val noviembre = PeriodoFinanciero(2026, 11)
        assertEquals("2026-10", com.jvillada.movi.shared.model.selloDeLaCuotaDelPeriodo(30, noviembre, corte25))
        assertEquals(
            "Este mes pagaste \$1.262.300",
            lineasDeLaCuotaQueCambia(variable, octubre, plan = null, periodoActual = noviembre, ajustes = corte25)[1],
        )
        // La de septiembre (sellada «2026-09») no es la de este período: sigue la estimación.
        assertEquals(
            "Este mes, unos \$1.262.300, como la de septiembre",
            lineasDeLaCuotaQueCambia(variable, CuotaPagada(1_262_300, "2026-09"), plan = null, periodoActual = noviembre, ajustes = corte25)[1],
        )
        // Con la cuota el 5 el sello coincide con el período: lo de siempre.
        assertEquals("2026-11", com.jvillada.movi.shared.model.selloDeLaCuotaDelPeriodo(5, noviembre, corte25))
    }
}
