package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * PR 1 del rediseño de Recurrentes (2026-09): el chip nuevo de Movimientos, con el mismo patrón
 * de prueba que ya cubre «Entre cuentas» en [ColorYEntreCuentasTest] — el chip es un filtro más,
 * armado sobre [matchesChip]/[diasVisibles], y no un mecanismo aparte.
 */
class ChipRecurrentesTest {

    private fun ev(
        id: String,
        cuenta: AccountType,
        type: TransactionType,
        category: String,
        description: String = id,
        amount: Long = 100_000L,
        transferId: String? = null,
    ) = FinancialEvent(
        id = id,
        accountId = "acc_${cuenta.name.lowercase()}",
        type = type,
        amount = amount,
        category = category,
        description = description,
        timestamp = 0L,
        transferId = transferId,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = isCashFlow(cuenta, type, category),
    )

    private fun regla(name: String) = RecurringRule(
        id = "rr_$name",
        name = name,
        category = "Vivienda",
        amount = 1_800_000L,
        dayOfMonth = 5,
        type = TransactionType.EXPENSE,
    )

    private val arriendo = ev("arriendo", AccountType.SAVINGS, TransactionType.EXPENSE, "Vivienda", description = "Arriendo", amount = 1_800_000L)
    private val netflix = ev("netflix", AccountType.SAVINGS, TransactionType.EXPENSE, "Entretenimiento", description = "Netflix", amount = 44_900L)
    private val mercado = ev("mercado", AccountType.SAVINGS, TransactionType.EXPENSE, "Mercado", description = "Mercado", amount = 250_000L)
    private val traspasoSale = ev("tr_out", AccountType.SAVINGS, TransactionType.EXPENSE, TRANSFER_CATEGORY, description = "Arriendo", transferId = "tr_1")

    private val reglas = listOf(regla("Arriendo"))
    private val nombresDeSuscripciones = listOf("Netflix")

    @Test
    fun `el chip reconoce un movimiento que matchea una regla o una suscripcion`() {
        assertTrue(matchesChip(arriendo, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
        assertTrue(matchesChip(netflix, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
    }

    @Test
    fun `el chip deja afuera lo que no matchea ninguna de las dos listas`() {
        assertFalse(matchesChip(mercado, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
    }

    @Test
    fun `sin reglas ni suscripciones cargadas el chip no reconoce nada, no explota`() {
        assertFalse(matchesChip(arriendo, CHIP_RECURRENTES))
        assertFalse(matchesChip(netflix, CHIP_RECURRENTES))
    }

    @Test
    fun `una pata de traspaso nunca entra al chip, aunque el nombre coincida con una regla`() {
        assertFalse(matchesChip(traspasoSale, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
    }

    @Test
    fun `diasVisibles filtra por el chip nuevo y recalcula el total del dia`() {
        val dia = EventDay(
            date = "2026-09-01",
            total = 0L,
            items = listOf(arriendo, netflix, mercado),
        )
        val visibles = diasVisibles(listOf(dia), CHIP_RECURRENTES, "", reglas, nombresDeSuscripciones).single()

        assertEquals(listOf("arriendo", "netflix"), visibles.items.map { it.id })
        assertEquals(-(arriendo.amount + netflix.amount), visibles.total)
    }

    @Test
    fun `el vacio del chip nuevo explica que son reglas y suscripciones ya reconocidas`() {
        val vacio = vacioDeMovimientos(CHIP_RECURRENTES, hayMovimientos = true)
        assertEquals("Nada recurrente", vacio.titulo)
        assertFalse(vacio.ofreceRegistrar)
    }

    @Test
    fun `el rotulo del chip nuevo esta en el indice nuevo`() {
        assertEquals("Recurrentes", CHIPS_DE_MOVIMIENTOS[CHIP_RECURRENTES])
    }

    // ── Las cuotas pagadas de los créditos ───────────────────────────────────
    //
    // El dueño: «en recurrentes no estoy viendo los pagos de cuota realizados para mis créditos,
    // considero que esto es importante verlo porque me permite entender mi flujo de caja mensual».

    /** La pata del dinero: el gasto real, en la cuenta de ahorros, con la categoría de la cuota. */
    private val cuotaDinero = ev(
        "cuota_dinero", AccountType.SAVINGS, TransactionType.EXPENSE, CUOTA_CATEGORY,
        description = "Cuota de Vehículo", amount = 4_215_223L, transferId = "tr_cuota",
    )

    /** La pata de la deuda: el otro lado del mismo hecho, en la cuenta LOAN. */
    private val cuotaDeuda = ev(
        "cuota_deuda", AccountType.LOAN, TransactionType.INCOME, CUOTA_CATEGORY,
        description = "Abono a capital desde Bancolombia", amount = 1_733_905L, transferId = "tr_cuota",
    )

    /** El pago de una tarjeta, que tiene la misma forma y NO es un gasto recurrente. */
    private val pagoDeTarjetaDinero = ev(
        "tarjeta_dinero", AccountType.SAVINGS, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY,
        description = "Pago de Nubank", amount = 1_200_000L, transferId = "tr_tarjeta",
    )

    /**
     * Lo que el dueño pidió, y **sin reglas cargadas**: el reconocimiento no pasa por la lista de
     * reglas, porque la de un crédito no está en esa lista (la fabrica el server al vuelo).
     */
    @Test
    fun `el chip incluye la cuota pagada de un credito`() {
        assertTrue(matchesChip(cuotaDinero, CHIP_RECURRENTES))
        assertTrue(matchesChip(cuotaDinero, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
    }

    /** Una sola fila por cuota: la pata de la deuda no entra al filtro. */
    @Test
    fun `la pata de la deuda de una cuota no entra al chip`() {
        assertFalse(matchesChip(cuotaDeuda, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
    }

    /** Plata que ya se contó cuando se compró: el pago de la tarjeta no es un gasto recurrente. */
    @Test
    fun `un pago de tarjeta sigue afuera del chip`() {
        assertFalse(matchesChip(pagoDeTarjetaDinero, CHIP_RECURRENTES, reglas, nombresDeSuscripciones))
    }

    /**
     * El día completo, que es lo que se pinta: de la cuota queda **una** fila —la del dinero— y el
     * «Flujo del día» la cuenta como el gasto que es. El pago de tarjeta no queda ninguna.
     */
    @Test
    fun `diasVisibles deja una sola fila por cuota y la suma al flujo del dia`() {
        val dia = EventDay(
            date = "2026-09-01",
            total = 0L,
            items = listOf(arriendo, cuotaDinero, cuotaDeuda, pagoDeTarjetaDinero, mercado),
        )
        val visibles = diasVisibles(listOf(dia), CHIP_RECURRENTES, "", reglas, nombresDeSuscripciones).single()

        assertEquals(listOf("arriendo", "cuota_dinero"), visibles.items.map { it.id })
        assertEquals(-(arriendo.amount + cuotaDinero.amount), visibles.total)
    }

    /**
     * Y con una sola pata a la vista, [collapseTransfers] la deja suelta: se ve como el gasto que
     * es —«Cuota de Vehículo · −$4.215.223»— y no como medio par sin hermana.
     */
    @Test
    fun `con el chip puesto la cuota se pinta como una fila suelta`() {
        val dia = EventDay(date = "2026-09-01", total = 0L, items = listOf(cuotaDinero, cuotaDeuda))
        val visibles = diasVisibles(listOf(dia), CHIP_RECURRENTES, "", reglas, nombresDeSuscripciones).single()
        val fila = collapseTransfers(visibles.items).single()

        assertIs<MovementRow.Single>(fila)
        assertEquals("cuota_dinero", fila.event.id)
        assertEquals(TonoDelMonto.GASTO, tonoDelEvento(fila.event))
    }

    /** Sin cuotas a la vista no hay nada que aclararle al «Flujo libre». */
    @Test
    fun `el aviso del flujo libre solo aparece si hay una cuota en la lista`() {
        val conCuota = EventDay("2026-09-01", 0L, listOf(cuotaDinero))
        val sinCuota = EventDay("2026-09-01", 0L, listOf(arriendo, netflix))

        assertTrue(hayCuotasPagadasEnLaLista(listOf(conCuota)))
        assertFalse(hayCuotasPagadasEnLaLista(listOf(sinCuota)))
        assertFalse(hayCuotasPagadasEnLaLista(emptyList()))
        // Ni la pata de la deuda ni el pago de tarjeta lo disparan.
        assertFalse(hayCuotasPagadasEnLaLista(listOf(EventDay("2026-09-01", 0L, listOf(cuotaDeuda, pagoDeTarjetaDinero)))))
    }

    // ── PR 2 del rediseño: el resumen de flujo libre y las candidatas ─────────

    @Test
    fun `el resumen de flujo libre solo se muestra con el chip Recurrentes activo`() {
        assertTrue(mostrarResumenDeRecurrentes(CHIP_RECURRENTES))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_TODO))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_GASTOS))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_INGRESOS))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_POR_CONFIRMAR))
        assertFalse(mostrarResumenDeRecurrentes(CHIP_ENTRE_CUENTAS))
    }
}
