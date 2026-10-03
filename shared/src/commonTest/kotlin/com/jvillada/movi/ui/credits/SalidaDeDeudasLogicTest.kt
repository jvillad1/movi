package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.DeudaParaSalir
import com.jvillada.movi.shared.model.EstrategiaDeSalida
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.TipoDeDeuda
import com.jvillada.movi.shared.model.planDeSalida
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Lo que dice «Cómo salir de tus deudas» (Ola 4). La cuenta la prueba `PlanDeSalidaTest`. */
class SalidaDeDeudasLogicTest {

    private val octubre = PeriodoFinanciero(2026, 10)
    private val unoPorCientoMensual = (1.01.pow(12) - 1.0) * 100.0

    @Test
    fun `cada deuda dice saldo, tasa, cuota e interes del mes`() {
        val d = DeudaParaSalir("c", "Crédito", TipoDeDeuda.CREDITO, 300_000L, 29.64, cuota = 100_000L)
        val texto = textoDeLaDeuda(d)
        assertTrue(texto.startsWith("Saldo \$300.000 · 29,64 % E.A. · cuota \$100.000 · "), texto)
        assertTrue(texto.endsWith("de interés este mes"), texto)
        val tarjeta = DeudaParaSalir("t", "AMEX", TipoDeDeuda.TARJETA, 19_000_000L, null, cuota = 1_008_902L)
        assertEquals("Saldo \$19.000.000 · mínimo \$1.008.902", textoDeLaDeuda(tarjeta), "sin tasa no se inventa un interés")
    }

    @Test
    fun `la salida dice cuando termina con y sin abono y cuanto se ahorra`() {
        val plan = planDeSalida(
            listOf(DeudaParaSalir("c", "Crédito", TipoDeDeuda.CREDITO, 300_000L, unoPorCientoMensual, cuota = 100_000L)),
            50_000L,
            EstrategiaDeSalida.AVALANCHA,
        )
        val s = plan.enElCalculo.single()
        assertEquals(
            "Con tu abono: 3 cuotas, hasta diciembre de 2026 · ahorras \$1.566 de interés. Sin abono: 4 cuotas, hasta enero de 2027.",
            textoDeLaSalida(s, 50_000L, octubre),
        )
        assertEquals("Termina en 4 cuotas, hasta enero de 2027", textoDeLaSalida(s, 0L, octubre))
        assertEquals(
            listOf("Te ahorras \$1.566 de interés", "Sales de estas deudas en diciembre de 2026, 1 mes antes que sin abono."),
            resumenDelSimulador(plan, octubre),
        )
    }

    @Test
    fun `sin abono el simulador invita a escribirlo`() {
        val plan = planDeSalida(
            listOf(DeudaParaSalir("c", "Crédito", TipoDeDeuda.CREDITO, 300_000L, 12.0, cuota = 100_000L)),
            0L,
            EstrategiaDeSalida.AVALANCHA,
        )
        assertTrue(resumenDelSimulador(plan, octubre).single().startsWith("Escribe cuánto podrías abonar"))
    }

    @Test
    fun `lo que no entra dice que falta y ofrece cargarlo`() {
        val sinTasa = DeudaParaSalir("t", "Master Black", TipoDeDeuda.TARJETA, 27_000_000L, null, cuota = 1_843_014L)
        assertEquals("Falta la tasa: cárgala para incluirla", motivoDeFuera(sinTasa))
        val sinMinimo = sinTasa.copy(tasaEa = 29.6, cuota = null)
        assertEquals("Falta el pago mínimo: cárgalo para incluirla", motivoDeFuera(sinMinimo))
    }

    @Test
    fun `la tasa se escribe con coma`() {
        assertEquals("29,64 %", tasaEnTexto(29.64))
        assertEquals("12 %", tasaEnTexto(12.0))
    }

    @Test
    fun `el pie dice que no es asesoria`() {
        assertEquals("Es un cálculo con tus datos, no una recomendación financiera.", PIE_DEL_PLAN_DE_SALIDA)
    }
}
