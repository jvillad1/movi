package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vuelta 77 · lo puro de la vuelta en `:core`: cuándo el alta pregunta «¿Ya pagaste la cuota de este
 * período?», desde cuándo corren las cuotas si dijo que sí, la primera cuota sin pagar con ese piso, la
 * comparación de avalancha y bola de nieve, y las categorías de lo fijo.
 */
class Vuelta77CoreTest {

    private val calendario = PeriodSettings()
    private val corte25 = PeriodSettings(cutoffDay = 25)

    // ── La cuota de este período ──────────────────────────────────────────────────────────────────

    @Test
    fun `el alta pregunta solo si la cuota de este periodo ya vencio`() {
        val hoy = LocalDate(2026, 10, 30)
        assertTrue(laCuotaDeEstePeriodoYaVencio(15, hoy, calendario), "el 15 de octubre ya pasó")
        assertTrue(laCuotaDeEstePeriodoYaVencio(30, hoy, calendario), "vence hoy")
        assertFalse(laCuotaDeEstePeriodoYaVencio(31, hoy, calendario), "el 31 todavía no")
        // Con corte 25 el período va del 25 de octubre al 24 de noviembre: la del 15 es la de noviembre.
        assertFalse(laCuotaDeEstePeriodoYaVencio(15, hoy, corte25))
        assertTrue(laCuotaDeEstePeriodoYaVencio(26, hoy, corte25))
    }

    @Test
    fun `si ya pagaste, las cuotas corren desde el arranque del periodo siguiente`() {
        assertEquals(LocalDate(2026, 11, 1), arranqueDeLasCuotasSiYaPagaste(LocalDate(2026, 10, 30), calendario))
        assertEquals(LocalDate(2026, 11, 25), arranqueDeLasCuotasSiYaPagaste(LocalDate(2026, 10, 30), corte25))
    }

    @Test
    fun `el vencimiento del dia en el periodo, tambien con un corte a mitad de mes`() {
        assertEquals(LocalDate(2026, 10, 15), vencimientoEnElPeriodo(15, PeriodoFinanciero(2026, 10), calendario))
        // Con corte 25, «noviembre» va del 25 de octubre al 24 de noviembre.
        assertEquals(LocalDate(2026, 11, 15), vencimientoEnElPeriodo(15, PeriodoFinanciero(2026, 11), corte25))
        assertEquals(LocalDate(2026, 11, 30), vencimientoEnElPeriodo(31, PeriodoFinanciero(2026, 11), calendario))
    }

    private fun credito(cuotasDesde: String?, pagadas: List<String>? = emptyList()) = CreditSummary(
        account = Account("acc_r6", "Libre Inversión R6", AccountType.LOAN, 18_500_000L),
        terms = CreditTerms(
            "acc_r6", "Bancolombia", 25_000_000L, 21.0, 48, 770_000L, 15, "2025-06-01", cuotasDesde = cuotasDesde,
        ),
        paidPct = null,
        cuotasPagadas = pagadas,
    )

    @Test
    fun `la primera cuota sin pagar respeta el piso de cuotasDesde`() {
        val hoy = LocalDate(2026, 10, 30)
        assertEquals(PeriodoFinanciero(2026, 10), mesDeLaPrimeraCuota(credito(null), hoy), "sin piso: la de octubre, vencida")
        assertEquals(PeriodoFinanciero(2026, 11), mesDeLaPrimeraCuota(credito("2026-11-01"), hoy), "con «ya pagué»: noviembre")
    }

    @Test
    fun `cuotasDesde anterior al desembolso o ilegible no cambia nada`() {
        assertNull(credito("2024-01-01").terms!!.arranqueDeLasCuotas())
        assertNull(credito("no es fecha").terms!!.arranqueDeLasCuotas())
        assertEquals(LocalDate(2026, 11, 1), credito("2026-11-01").terms!!.arranqueDeLasCuotas())
    }

    // ── Avalancha contra bola de nieve ─────────────────────────────────────────────────────────────

    private val cara = DeudaParaSalir("cara", "Nu", TipoDeDeuda.TARJETA, 1_583_000L, 28.0, cuota = 180_000L)
    private val grande = DeudaParaSalir("libre", "Libre", TipoDeDeuda.CREDITO, 18_500_000L, 21.0, cuota = 770_000L)

    @Test
    fun `avalancha contra bola de nieve con la misma simulacion del plan`() {
        val c = assertNotNull(compararEstrategias(listOf(cara, grande), 500_000L))
        val avalancha = planDeSalida(listOf(cara, grande), 500_000L, EstrategiaDeSalida.AVALANCHA)
        assertEquals(avalancha.enElCalculo.sumOf { it.interesConAbono!! }, c.interesAvalancha)
        // Acá la de mayor tasa es también la de menor saldo: los dos órdenes son el mismo y dan igual.
        assertEquals(0L, c.loQueAhorraAvalancha)
    }

    @Test
    fun `cuando los ordenes difieren, avalancha ahorra mas`() {
        val chicaBarata = DeudaParaSalir("chica", "Chica", TipoDeDeuda.CREDITO, 1_000_000L, 10.0, cuota = 100_000L)
        val grandeCara = DeudaParaSalir("cara", "Cara", TipoDeDeuda.CREDITO, 3_000_000L, 30.0, cuota = 150_000L)
        val c = assertNotNull(compararEstrategias(listOf(chicaBarata, grandeCara), 200_000L))
        assertTrue(c.loQueAhorraAvalancha > 0L, "avalancha: ${c.interesAvalancha}, bola: ${c.interesBolaDeNieve}")
    }

    @Test
    fun `sin abono no hay comparacion`() {
        assertNull(compararEstrategias(listOf(cara, grande), 0L))
        assertNull(compararEstrategias(emptyList(), 500_000L))
    }

    // ── Lo fijo ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `las categorias de lo fijo son las de las reglas de gasto y la cuota`() {
        val reglas = listOf(
            RecurringRule(id = "r1", name = "Gimnasio", category = "Gimnasio", amount = 120_000L, dayOfMonth = 3, type = TransactionType.EXPENSE),
            RecurringRule(id = "r2", name = "Salario", category = "Salario", amount = 9_000_000L, dayOfMonth = 25, type = TransactionType.INCOME),
        )
        assertEquals(setOf("Gimnasio", CUOTA_CATEGORY), categoriasFijas(reglas))
    }
}
