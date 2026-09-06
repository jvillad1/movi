package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.THIRD_PARTY_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **El color de cada renglón y el filtro «Entre cuentas»**, sobre los siete casos que el dueño
 * tiene de verdad en su lista: gasto, ingreso, traspaso, cuota, pago de tarjeta, saldo inicial y
 * la cuota que paga un tercero.
 *
 * Cada evento se arma con `countsAsCashFlow` **derivado con `isCashFlow`** a partir del tipo de la
 * cuenta, igual que lo hace el server: dejar el default (`true`) sería probar contra un evento que
 * el server nunca manda. Que la derivación esté acá adentro es a propósito — si `isCashFlow`
 * cambia de opinión sobre alguno de estos casos, estos tests lo van a decir, y esa es la única
 * forma de que la pantalla y el mes no se vuelvan a desincronizar.
 */
class ColorYEntreCuentasTest {

    private fun ev(
        id: String,
        cuenta: AccountType,
        type: TransactionType,
        category: String,
        amount: Long = 100_000L,
        transferId: String? = null,
    ) = FinancialEvent(
        id = id,
        accountId = "acc_${cuenta.name.lowercase()}",
        type = type,
        amount = amount,
        category = category,
        description = id,
        timestamp = 0L,
        transferId = transferId,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = isCashFlow(cuenta, type, category),
    )

    // ── Los siete casos ──────────────────────────────────────────────────────────

    private val gasto = ev("mercado", AccountType.SAVINGS, TransactionType.EXPENSE, "Mercado", 250_000L)
    private val ingreso = ev("nomina", AccountType.SAVINGS, TransactionType.INCOME, "Salario", 4_500_000L)

    private val traspasoSale = ev("tr_out", AccountType.SAVINGS, TransactionType.EXPENSE, TRANSFER_CATEGORY, 5_000_000L, "tr_1")
    private val traspasoEntra = ev("tr_in", AccountType.INVESTMENT, TransactionType.INCOME, TRANSFER_CATEGORY, 5_000_000L, "tr_1")

    /** La cuota: la pata del dinero por el monto completo, la de la deuda solo por el capital. */
    private val cuotaDinero = ev("cuota_dinero", AccountType.SAVINGS, TransactionType.EXPENSE, CUOTA_CATEGORY, 4_215_223L, "tr_cuota")
    private val cuotaDeuda = ev("cuota_deuda", AccountType.LOAN, TransactionType.INCOME, CUOTA_CATEGORY, 1_733_905L, "tr_cuota")

    private val tarjetaDinero = ev("tarjeta_dinero", AccountType.SAVINGS, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, 1_000_000L, "tr_tarjeta")
    private val tarjetaDeuda = ev("tarjeta_deuda", AccountType.CREDIT_CARD, TransactionType.INCOME, CARD_PAYMENT_CATEGORY, 1_000_000L, "tr_tarjeta")
    /** El pago de tarjeta de antes de «Pagar cuota»: un gasto suelto con la categoría reservada. */
    private val tarjetaVieja = ev("tarjeta_vieja", AccountType.SAVINGS, TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, 900_000L)

    private val saldoInicial = ev("apertura", AccountType.SAVINGS, TransactionType.INCOME, OPENING_CATEGORY, 41_000_000L)
    private val pagoDeUnTercero = ev("skandia", AccountType.LOAN, TransactionType.INCOME, THIRD_PARTY_PAYMENT_CATEGORY, 5_880_561L)
    private val pataHuerfana = ev("huerfana", AccountType.CHECKING, TransactionType.INCOME, ORPHANED_LEG_CATEGORY, 257_000_000L)

    // ── El color ─────────────────────────────────────────────────────────────────

    @Test
    fun `el gasto va en rojo y el ingreso en verde`() {
        assertEquals(TonoDelMonto.GASTO, tonoDelEvento(gasto))
        assertEquals(TonoDelMonto.INGRESO, tonoDelEvento(ingreso))
    }

    @Test
    fun `las dos patas de un traspaso van en gris, juntas o sueltas`() {
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(traspasoSale))
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(traspasoEntra))
        assertEquals(TonoDelMonto.NEUTRO, tonoDelRenglon(MovementRow.Transfer(traspasoSale, traspasoEntra)))
    }

    @Test
    fun `la cuota como par va en gris, pero su pata del dinero suelta es un gasto en rojo`() {
        // El par: plata de una cuenta suya a otra, sin signo.
        assertEquals(TonoDelMonto.NEUTRO, tonoDelRenglon(MovementRow.Transfer(cuotaDinero, cuotaDeuda)))
        // Suelta —como queda en «Gastos», donde su hermana no entra— es plata que salió.
        assertEquals(TonoDelMonto.GASTO, tonoDelEvento(cuotaDinero))
        // La pata de la deuda nunca es un ingreso: baja la deuda, no llena el bolsillo.
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(cuotaDeuda))
    }

    @Test
    fun `el pago de tarjeta va en gris en sus tres formas`() {
        assertEquals(TonoDelMonto.NEUTRO, tonoDelRenglon(MovementRow.Transfer(tarjetaDinero, tarjetaDeuda)))
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(tarjetaDinero))
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(tarjetaDeuda))
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(tarjetaVieja))
    }

    @Test
    fun `lo que no cuenta en el mes va en gris aunque sea INCOME o EXPENSE`() {
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(saldoInicial))
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(pagoDeUnTercero))
        assertEquals(TonoDelMonto.NEUTRO, tonoDelEvento(pataHuerfana))
        assertFalse(rowShowsSign(saldoInicial))
        assertFalse(rowShowsSign(pagoDeUnTercero))
        assertTrue(rowShowsSign(gasto))
    }

    // ── «Entre cuentas» ──────────────────────────────────────────────────────────

    @Test
    fun `entre cuentas agrupa los tres pares y el pago de tarjeta viejo`() {
        for (e in listOf(traspasoSale, traspasoEntra, cuotaDinero, cuotaDeuda, tarjetaDinero, tarjetaDeuda, tarjetaVieja)) {
            assertTrue(esEntreCuentas(e), "${e.id} tendría que ser «entre cuentas»")
            assertTrue(matchesChip(e, CHIP_ENTRE_CUENTAS), "${e.id} tendría que entrar en el chip")
        }
    }

    @Test
    fun `entre cuentas deja afuera lo que no fue de una cuenta suya a otra`() {
        for (e in listOf(gasto, ingreso, saldoInicial, pagoDeUnTercero, pataHuerfana)) {
            assertFalse(esEntreCuentas(e), "${e.id} no es «entre cuentas»")
            assertFalse(matchesChip(e, CHIP_ENTRE_CUENTAS), "${e.id} no tendría que entrar en el chip")
        }
    }

    @Test
    fun `en entre cuentas las dos patas pasan y el par se lee como un solo renglon`() {
        val dia = EventDay(
            date = "2026-09-01",
            total = 0L,
            items = listOf(gasto, traspasoSale, traspasoEntra, cuotaDinero, cuotaDeuda, ingreso),
        )
        val visibles = diasVisibles(listOf(dia), CHIP_ENTRE_CUENTAS, "").single()
        val renglones = collapseTransfers(visibles.items)

        assertEquals(2, renglones.size)
        assertIs<MovementRow.Transfer>(renglones[0])
        assertIs<MovementRow.Transfer>(renglones[1])
        // El flujo del día recalculado sigue la regla del mes: la cuota cuenta, el traspaso no.
        assertEquals(-cuotaDinero.amount, visibles.total)
    }

    // ── El invariante de plata que más veces se rompió ───────────────────────────

    @Test
    fun `la cuota de credito SI aparece en Gastos y el traspaso y el pago de tarjeta NO`() {
        assertTrue(matchesChip(cuotaDinero, CHIP_GASTOS), "la cuota es plata que salió: va en Gastos")
        assertFalse(matchesChip(traspasoSale, CHIP_GASTOS), "un traspaso no es un gasto")
        assertFalse(matchesChip(tarjetaDinero, CHIP_GASTOS), "el pago de tarjeta ya se contó cuando se compró")
        assertFalse(matchesChip(tarjetaVieja, CHIP_GASTOS))
    }

    @Test
    fun `en Gastos la cuota suma en el flujo del dia, igual que en el mes`() {
        val dia = EventDay(
            date = "2026-09-01",
            total = 0L,
            items = listOf(gasto, traspasoSale, traspasoEntra, cuotaDinero, cuotaDeuda, tarjetaDinero, tarjetaDeuda),
        )
        val visibles = diasVisibles(listOf(dia), CHIP_GASTOS, "").single()

        assertEquals(listOf("mercado", "cuota_dinero"), visibles.items.map { it.id })
        assertEquals(-(gasto.amount + cuotaDinero.amount), visibles.total)
    }

    @Test
    fun `lo que no cuenta en el mes tampoco entra en Gastos ni en Ingresos`() {
        for (e in listOf(saldoInicial, pagoDeUnTercero, pataHuerfana, cuotaDeuda, tarjetaDeuda)) {
            assertFalse(matchesChip(e, CHIP_INGRESOS), "${e.id} no es un ingreso")
            assertFalse(matchesChip(e, CHIP_GASTOS), "${e.id} no es un gasto")
        }
        assertTrue(matchesChip(ingreso, CHIP_INGRESOS))
        assertTrue(matchesChip(gasto, CHIP_GASTOS))
    }

    // ── El vacío ─────────────────────────────────────────────────────────────────

    @Test
    fun `por confirmar vacio dice que todo lo registro el dueno y no ofrece registrar`() {
        val vacio = vacioDeMovimientos(CHIP_POR_CONFIRMAR, hayMovimientos = true)
        assertEquals("Nada por confirmar", vacio.titulo)
        assertTrue(vacio.detalle!!.contains("lo registraste tú"))
        assertFalse(vacio.ofreceRegistrar)
        // Y también con la lista vacía del todo: registrar no tiene nada que ver con confirmar.
        assertFalse(vacioDeMovimientos(CHIP_POR_CONFIRMAR, hayMovimientos = false).ofreceRegistrar)
    }

    @Test
    fun `solo la cuenta nueva ofrece registrar el primero`() {
        assertTrue(vacioDeMovimientos(CHIP_TODO, hayMovimientos = false).ofreceRegistrar)
        assertTrue(vacioDeMovimientos(CHIP_GASTOS, hayMovimientos = false).ofreceRegistrar)
        assertFalse(vacioDeMovimientos(CHIP_GASTOS, hayMovimientos = true).ofreceRegistrar)
        assertFalse(vacioDeMovimientos(CHIP_ENTRE_CUENTAS, hayMovimientos = true).ofreceRegistrar)
        assertEquals("Nada entre cuentas", vacioDeMovimientos(CHIP_ENTRE_CUENTAS, hayMovimientos = true).titulo)
    }

    @Test
    fun `los chips tienen el rotulo nuevo en el indice nuevo`() {
        assertEquals("Entre cuentas", CHIPS_DE_MOVIMIENTOS[CHIP_ENTRE_CUENTAS])
        assertEquals("Por confirmar", CHIPS_DE_MOVIMIENTOS[CHIP_POR_CONFIRMAR])
    }
}
