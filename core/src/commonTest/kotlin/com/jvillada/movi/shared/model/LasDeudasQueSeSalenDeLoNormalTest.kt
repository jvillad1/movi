package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Los dos avisos de las deudas**: la deuda que crece sin compras y el interés del extracto lejos
 * del estimado. Cada uno con su positivo, su negativo y su umbral.
 */
class LasDeudasQueSeSalenDeLoNormalTest {

    private val dia = 24L * 60 * 60 * 1000
    private val inicio = 1_790_000_000_000L
    private val cierre = inicio until inicio + 30 * dia
    private val diaDe: (Long) -> String = { "d${(it - inicio) / dia}" }
    private val libranza = Account(id = "lib", name = "Libranza 4818", type = AccountType.LOAN, balance = 0L)
    private val tarjeta = Account(id = "tc", name = "Master 3684", type = AccountType.CREDIT_CARD, balance = 0L)
    private var n = 0

    private fun mov(cuenta: Account, tipo: TransactionType, monto: Long, dias: Int, categoria: String = "Comida", nombre: String = "Movimiento") =
        FinancialEvent(
            id = "e${n++}", accountId = cuenta.id, type = tipo, amount = monto, category = categoria,
            description = nombre, timestamp = inicio + dias * dia, reconciliationStatus = ReconciliationStatus.RECONCILED,
        )

    private fun crecen(vararg e: FinancialEvent) =
        deudasQueCrecen(listOf(libranza, tarjeta), e.toList(), cierre, "2026-09", diaDe)

    // ── La deuda que crece ──

    @Test
    fun `la libranza que sube por un ajuste contra el extracto es una deuda que crece`() {
        val avisos = crecen(
            mov(libranza, TransactionType.EXPENSE, 257_000_000L, -60, categoria = OPENING_CATEGORY),
            mov(libranza, TransactionType.EXPENSE, 5_386_162L, 10, categoria = ADJUSTMENT_CATEGORY, nombre = "Ajuste al saldo del banco"),
        )
        val aviso = avisos.single()
        assertEquals(TipoDeAnomalia.DEUDA_QUE_CRECE, aviso.tipo)
        assertEquals("deuda:lib:2026-09", aviso.huella)
        assertTrue("creció \$5.386.162 sin compras" in aviso.titulo, aviso.titulo)
        assertTrue("de \$257.000.000 a \$262.386.162" in aviso.detalle, aviso.detalle)
        assertEquals(listOf(5_386_162L), aviso.evidencia.map { it.monto })
    }

    @Test
    fun `una tarjeta que sube por compras no crece sola`() {
        assertTrue(
            crecen(
                mov(tarjeta, TransactionType.EXPENSE, 1_000_000L, -40),
                mov(tarjeta, TransactionType.EXPENSE, 2_500_000L, 5),
            ).isEmpty(),
        )
    }

    @Test
    fun `los intereses de la tarjeta sin pago la hacen crecer, y las compras se descuentan`() {
        val aviso = crecen(
            mov(tarjeta, TransactionType.EXPENSE, 20_000_000L, -40),
            mov(tarjeta, TransactionType.EXPENSE, 300_000L, 3),
            mov(tarjeta, TransactionType.EXPENSE, 480_000L, 25, categoria = CATEGORIA_DE_COMISIONES_DEL_BANCO, nombre = "INTERESES CORRIENTES"),
        ).single()
        assertTrue("creció \$480.000" in aviso.titulo, aviso.titulo)
        assertTrue("las compras solo explican \$300.000" in aviso.detalle, aviso.detalle)
    }

    @Test
    fun `una deuda que bajo, o que no existia antes del cierre, no avisa`() {
        assertTrue(
            crecen(
                mov(libranza, TransactionType.EXPENSE, 10_000_000L, -40),
                mov(libranza, TransactionType.EXPENSE, 200_000L, 3, categoria = ADJUSTMENT_CATEGORY),
                mov(libranza, TransactionType.INCOME, 2_000_000L, 5, categoria = CUOTA_CATEGORY),
            ).isEmpty(),
            "subió 200.000 por un ajuste pero bajó 2.000.000 con la cuota",
        )
        assertTrue(crecen(mov(libranza, TransactionType.EXPENSE, 9_000_000L, 5, categoria = ADJUSTMENT_CATEGORY)).isEmpty())
    }

    @Test
    fun `el umbral de la deuda que crece esta en cien mil`() {
        val base = mov(libranza, TransactionType.EXPENSE, 10_000_000L, -40)
        assertTrue(crecen(base, mov(libranza, TransactionType.EXPENSE, 99_999L, 3, categoria = ADJUSTMENT_CATEGORY)).isEmpty())
        assertEquals(1, crecen(base, mov(libranza, TransactionType.EXPENSE, 100_000L, 3, categoria = ADJUSTMENT_CATEGORY)).size)
    }

    // ── El interés lejos del estimado ──

    private val saldo = 40_710_555L
    private val estimado = round(saldo.toDouble() * tasaMensualDeUnaEA(11.27)).toLong()
    private val hoy = LocalDate(2026, 9, 1)

    private fun credito(interesReal: Long?, rateEa: Double = 11.27, sinIntereses: Boolean = false) = CreditSummary(
        account = Account(id = "c9695", name = "Libre inversión 9695", type = AccountType.LOAN, balance = saldo),
        terms = CreditTerms(
            accountId = "c9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = rateEa, termMonths = 132,
            installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15", sinIntereses = sinIntereses,
            interesReal = interesReal, interesRealCuota = "2026-09",
        ),
        paidPct = null,
    )

    @Test
    fun `el interes del extracto muy por encima del estimado avisa con las dos cifras`() {
        val aviso = interesesLejosDelEstimado(listOf(credito(594_567L)), hoy).single()
        assertEquals(TipoDeAnomalia.INTERES_LEJOS_DEL_ESTIMADO, aviso.tipo)
        assertEquals("interes:c9695:2026-09", aviso.huella)
        assertEquals(listOf(594_567L, estimado), aviso.evidencia.map { it.monto })
        assertTrue("11,27 %" in aviso.detalle, aviso.detalle)
    }

    @Test
    fun `cerca del estimado, sin interes del extracto, sin tasa o con la cuota pasada no avisa`() {
        assertTrue(interesesLejosDelEstimado(listOf(credito(estimado + 10_000L)), hoy).isEmpty())
        assertTrue(interesesLejosDelEstimado(listOf(credito(null)), hoy).isEmpty())
        assertTrue(interesesLejosDelEstimado(listOf(credito(594_567L, rateEa = 0.0)), hoy).isEmpty())
        assertTrue(interesesLejosDelEstimado(listOf(credito(594_567L)), LocalDate(2026, 10, 10)).isEmpty())
    }

    @Test
    fun `el umbral es mas de quince por ciento y al menos cincuenta mil`() {
        // 15 % exacto no avisa; un peso más, sí (la diferencia ya pasa de $50.000).
        val quincePorCiento = estimado * 15 / 100
        assertTrue(interesesLejosDelEstimado(listOf(credito(estimado + quincePorCiento)), hoy).isEmpty())
        assertEquals(1, interesesLejosDelEstimado(listOf(credito(estimado + quincePorCiento + 1)), hoy).size)
        // Un crédito chico: 40 % de diferencia pero menos de $50.000 no avisa.
        val chico = credito(70_000L).let { it.copy(account = it.account.copy(balance = 5_000_000L)) }
        assertTrue(interesesLejosDelEstimado(listOf(chico), hoy).isEmpty())
    }

    @Test
    fun `un credito que no cobra intereses y el extracto cobra, avisa`() {
        assertEquals(1, interesesLejosDelEstimado(listOf(credito(120_000L, rateEa = 0.0, sinIntereses = true)), hoy).size)
    }
}
