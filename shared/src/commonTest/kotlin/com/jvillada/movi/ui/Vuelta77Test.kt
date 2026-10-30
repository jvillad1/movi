package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ComparacionDeEstrategias
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuota
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuotaDelCredito
import com.jvillada.movi.ui.quickadd.queSeAnoto
import com.jvillada.movi.ui.quickadd.saldoDeLaCuenta
import com.jvillada.movi.ui.ai.PREGUNTAS_PARA_TOCAR_DE_RESPALDO
import com.jvillada.movi.ui.ai.PreguntaParaTocar
import com.jvillada.movi.ui.ai.preguntasDelArranque
import com.jvillada.movi.ui.credits.LOS_DOS_ORDENES_DAN_IGUAL
import com.jvillada.movi.ui.credits.explicacionDe
import com.jvillada.movi.ui.credits.laCuotaEnLaFicha
import com.jvillada.movi.ui.credits.saldoDelPlanEnLaFicha
import com.jvillada.movi.ui.credits.textoDeLaComparacion
import com.jvillada.movi.ui.credits.textoDeLaCuotaEnLaFicha
import com.jvillada.movi.ui.credits.textoDelSaldoDelPlanEnLaFicha
import com.jvillada.movi.shared.model.EstrategiaDeSalida
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 77 · lo puro de la vuelta en `:shared`: la cuota que la ficha ofrece pagar (o dice pagada), el
 * saldo según el plan, la comparación de los dos órdenes y las preguntas de una conversación nueva.
 */
class Vuelta77Test {

    private val hoy = LocalDate(2026, 10, 30)
    private val calendario = PeriodSettings()

    private fun credito(
        balance: Long = 18_500_000L,
        dia: Int = 15,
        desde: String = "2025-06-01",
        cuotasDesde: String? = null,
        pagadas: List<String>? = emptyList(),
        hasMovements: Boolean = true,
    ) = CreditSummary(
        account = Account("acc_r6", "Libre Inversión R6", AccountType.LOAN, balance),
        terms = CreditTerms("acc_r6", "Bancolombia", 25_000_000L, 21.0, 48, 770_000L, dia, desde, cuotasDesde = cuotasDesde),
        paidPct = null,
        hasMovements = hasMovements,
        cuotasPagadas = pagadas,
    )

    private fun laCuota(c: CreditSummary) = laCuotaEnLaFicha(c, mesDeLaPrimeraCuota(c, hoy)!!, hoy, calendario)

    @Test
    fun `la cuota de este periodo, vencida o no, se ofrece pagar`() {
        assertEquals("Pagar la cuota de octubre · \$770.000", textoDeLaCuotaEnLaFicha(laCuota(credito())!!))
    }

    @Test
    fun `saldada la de este periodo, dice pagada`() {
        val c = laCuota(credito(pagadas = listOf("2026-10")))!!
        assertTrue(c.pagada)
        assertEquals("Cuota de octubre · pagada", textoDeLaCuotaEnLaFicha(c))
    }

    @Test
    fun `con ya pague la cuota de este periodo, la que corresponde es la del siguiente`() {
        val c = laCuota(credito(cuotasDesde = "2026-11-01"))!!
        assertEquals(PeriodoFinanciero(2026, 11), c.mes)
        assertEquals("Pagar la cuota de noviembre · \$770.000", textoDeLaCuotaEnLaFicha(c))
        // Y pagada la de noviembre (por adelantado), lo dice.
        val pagada = laCuota(credito(cuotasDesde = "2026-11-01", pagadas = listOf("2026-11")))!!
        assertEquals("Cuota de noviembre · pagada", textoDeLaCuotaEnLaFicha(pagada))
    }

    @Test
    fun `un credito que arranca despues no tiene cuota de este periodo`() {
        val nuevo = laCuota(credito(desde = "2026-10-20"))!!
        assertEquals("Pagar la cuota de noviembre · \$770.000", textoDeLaCuotaEnLaFicha(nuevo))
    }

    @Test
    fun `sin cuotas pagadas del server no se afirma que este pagada`() {
        assertTrue(!laCuota(credito(pagadas = null))!!.pagada)
    }

    @Test
    fun `nomina, tercero o pagado entero no tienen Pagar la cuota`() {
        val base = credito()
        assertNull(laCuota(base.copy(terms = base.terms!!.copy(payrollDeduction = true))))
        assertNull(laCuota(base.copy(terms = base.terms!!.copy(paidBy = "Skandia"))))
        assertNull(laCuota(credito(balance = 0L)))
    }

    @Test
    fun `el saldo segun el plan solo para un credito sin saldo con sus condiciones`() {
        assertNull(saldoDelPlanEnLaFicha(credito(), hoy), "con saldo no se propone nada")
        val sinSaldo = credito(balance = -770_000L, desde = "2026-01-10")
        val plan = assertNotNull(saldoDelPlanEnLaFicha(sinSaldo, hoy))
        assertTrue(plan.saldo in 1L until 25_000_000L, plan.toString())
        // La última cuota: la primera (15 de enero de 2026, después del desembolso del 10) más 48 meses − 1.
        assertEquals(PeriodoFinanciero(2029, 12), plan.ultimaCuota)
        assertTrue(textoDelSaldoDelPlanEnLaFicha(plan).startsWith("Según tu plan debes unos \$"))
        assertTrue(textoDelSaldoDelPlanEnLaFicha(plan).endsWith(" y terminas en diciembre de 2029"))
        // Sin la tasa (y sin «no cobra intereses») no hay saldo honesto que proponer.
        val sinTasa = sinSaldo.copy(terms = sinSaldo.terms!!.copy(rateEa = 0.0))
        assertNull(saldoDelPlanEnLaFicha(sinTasa, hoy))
    }

    @Test
    fun `la comparacion de los dos ordenes, y el texto de avalancha al derecho`() {
        assertEquals("Avalancha te ahorra \$125.453 más que bola de nieve", textoDeLaComparacion(ComparacionDeEstrategias(1_000_000L, 1_125_453L)))
        assertEquals("Bola de nieve te ahorra \$1.000 más que avalancha", textoDeLaComparacion(ComparacionDeEstrategias(11_000L, 10_000L)))
        assertEquals(LOS_DOS_ORDENES_DAN_IGUAL, textoDeLaComparacion(ComparacionDeEstrategias(5_000L, 5_000L)))
        assertNull(textoDeLaComparacion(null))
        assertEquals("Primero la de mayor tasa: así pagas menos interés en total.", explicacionDe(EstrategiaDeSalida.AVALANCHA))
    }

    @Test
    fun `una conversacion nueva abre con las tres gratis y despues las del modelo`() {
        val delModelo = listOf(
            PreguntaParaTocar("¿Qué hago para no pasarme en Mercado el próximo período?"),
            PreguntaParaTocar("¿Por qué este período salieron \$4M más de los que entraron?"),
            PreguntaParaTocar("Pago \$329.122 de intereses al mes, ¿qué hago para bajarlo?"),
        )
        val arranque = preguntasDelArranque(delModelo, iaDisponible = true)
        assertEquals(PREGUNTAS_PARA_TOCAR_DE_RESPALDO, arranque.take(3))
        assertEquals(PreguntaSugerida.entries.toList(), arranque.take(3).map { it.sugerida })
        assertEquals(delModelo.take(2), arranque.drop(3))
        // Sin la IA, solo las tres.
        assertEquals(PREGUNTAS_PARA_TOCAR_DE_RESPALDO, preguntasDelArranque(delModelo, iaDisponible = false))
    }

    // ── La revisión ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `la primera cuota del credito, con ya pague o desembolsado hace poco`() {
        assertEquals(PeriodoFinanciero(2026, 11), credito(cuotasDesde = "2026-11-01").terms!!.mesDeLaPrimeraCuotaDelCredito())
        assertEquals(PeriodoFinanciero(2026, 11), credito(desde = "2026-10-20").terms!!.mesDeLaPrimeraCuotaDelCredito())
        assertEquals(PeriodoFinanciero(2025, 6), credito().terms!!.mesDeLaPrimeraCuotaDelCredito())
    }

    @Test
    fun `en Pagar un prestamo sin saldo dice Sin saldo en Movi, sin cifra, y una tarjeta a favor sigue a favor`() {
        val prestamo = saldoDeLaCuenta(Account("acc_veh", "Vehículo 4083", AccountType.LOAN, -980_000L))
        assertEquals(com.jvillada.movi.ui.credits.SIN_SALDO_EN_MOVI, prestamo.subtitulo)
        assertTrue(!prestamo.conCifra)
        val tarjeta = saldoDeLaCuenta(Account("acc_nu", "Nu", AccountType.CREDIT_CARD, -50_000L))
        assertEquals("A favor", tarjeta.subtitulo)
        assertTrue(tarjeta.conCifra)
    }

    @Test
    fun `el aviso de un abono dice abono`() {
        assertEquals("Abono a Vehículo 4083", queSeAnoto(esPago = true, esAbono = true, destino = "Vehículo 4083"))
        assertEquals("Pago a Nu", queSeAnoto(esPago = true, esAbono = false, destino = "Nu"))
        assertEquals("Traspaso", queSeAnoto(esPago = false, esAbono = false, destino = "Ahorros"))
    }
}
