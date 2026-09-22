package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Lo que el período le puso y le quitó a «Tu plata» para la tarjeta «Disponible». Ver
 * `PlataDelPeriodo.kt`. Las cuentas son las del dueño.
 */
class PlataDelPeriodoTest {

    private val cuentas = mapOf(
        "ahorros" to CuentaDelDisponible(AccountType.SAVINGS, null),
        "glim" to CuentaDelDisponible(AccountType.SAVINGS, null),
        "fiducuenta" to CuentaDelDisponible(AccountType.INVESTMENT, null),
        "nu" to CuentaDelDisponible(AccountType.SAVINGS, "Educación"),
        "skandia" to CuentaDelDisponible(AccountType.INVESTMENT, "Vivienda"),
        "techo" to CuentaDelDisponible(AccountType.LOAN, null),
        "hipotecario" to CuentaDelDisponible(AccountType.LOAN, null),
        "master" to CuentaDelDisponible(AccountType.CREDIT_CARD, null),
    )

    private fun evento(
        id: String,
        cuenta: String,
        tipo: TransactionType,
        monto: Long,
        categoria: String = "Comida",
        traspaso: String? = null,
        moneda: String = "COP",
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, currency = moneda,
        category = categoria, description = id, timestamp = 0L, reconciliationStatus = estado,
        transferId = traspaso,
        countsAsCashFlow = isCashFlow(cuentas.getValue(cuenta).tipo, tipo, categoria),
    )

    /** Las dos patas de un traspaso de [desde] a [hacia]. */
    private fun traspaso(id: String, desde: String, hacia: String, monto: Long) = listOf(
        evento("$id-sale", desde, TransactionType.EXPENSE, monto, TRANSFER_CATEGORY, traspaso = id),
        evento("$id-entra", hacia, TransactionType.INCOME, monto, TRANSFER_CATEGORY, traspaso = id),
    )

    private fun calcular(vararg eventos: List<FinancialEvent>) =
        plataDelPeriodo(saldoAlInicio = 0L, eventos = eventos.toList().flatten(), cuentas = cuentas)

    @Test
    fun `la regla de Tu plata - ni deuda ni condicionada`() {
        assertTrue(esDeTuPlata(AccountType.SAVINGS, null))
        assertTrue(esDeTuPlata(AccountType.INVESTMENT, null))
        assertTrue(esDeTuPlata(AccountType.CASH, "  "), "una condición en blanco no condiciona nada")
        assertFalse(esDeTuPlata(AccountType.SAVINGS, "Educación"))
        assertFalse(esDeTuPlata(AccountType.LOAN, null))
        assertFalse(esDeTuPlata(AccountType.CREDIT_CARD, null))
    }

    @Test
    fun `el saldo al inicio suma solo las cuentas de Tu plata, con el signo de cada movimiento`() {
        val saldo = saldoDeTuPlata(
            listOf(
                SumaDeMovimientos("ahorros", TransactionType.INCOME, 5_000_000),
                SumaDeMovimientos("ahorros", TransactionType.EXPENSE, 3_800_000),
                SumaDeMovimientos("glim", TransactionType.INCOME, 7_420),
                SumaDeMovimientos("nu", TransactionType.INCOME, 12_000_000),
                SumaDeMovimientos("master", TransactionType.EXPENSE, 2_000_000),
                SumaDeMovimientos("una-cuenta-borrada", TransactionType.INCOME, 1_000_000),
            ),
            cuentas,
        )
        assertEquals(1_207_420, saldo)
    }

    /** La Gardenera: un préstamo de su papá, anotado como traspaso desde la cuenta del crédito. */
    @Test
    fun `el desembolso de un prestamo entra, y el gasto que paga se sigue contando aparte`() {
        val p = calcular(
            traspaso("t-techo", desde = "techo", hacia = "ahorros", monto = 10_000_000),
            listOf(evento("gardenera", "ahorros", TransactionType.EXPENSE, 9_960_000, "Hogar")),
        )
        assertEquals(10_000_000, p.desdeFuera)
        assertEquals(10_000_000, p.entradas)
        assertEquals(0, p.pagadoDesdeFuera, "el gasto salió de Tu plata: no lo financia nadie de afuera")
    }

    /** El colegio: $3M directo desde Nu, que es ahorro condicionado. */
    @Test
    fun `un gasto pagado desde un ahorro condicionado entra como plata de afuera`() {
        val p = calcular(listOf(evento("colegio", "nu", TransactionType.EXPENSE, 3_000_000, "Educación")))
        assertEquals(3_000_000, p.pagadoDesdeFuera)
        assertEquals(3_000_000, p.entradas)
    }

    @Test
    fun `guardar en un ahorro condicionado resta, y retirar de el suma`() {
        val p = calcular(
            traspaso("t-a-nu", desde = "ahorros", hacia = "nu", monto = 500_000),
            traspaso("t-de-skandia", desde = "skandia", hacia = "fiducuenta", monto = 2_000_000),
        )
        assertEquals(500_000, p.guardado)
        assertEquals(2_000_000, p.desdeFuera)
        assertEquals(1_500_000, p.total)
    }

    @Test
    fun `un traspaso entre dos cuentas de Tu plata no cambia nada`() {
        val p = calcular(
            traspaso("t1", desde = "ahorros", hacia = "fiducuenta", monto = 8_000_000),
            traspaso("t2", desde = "fiducuenta", hacia = "ahorros", monto = 4_200_000),
        )
        assertEquals(PlataDelPeriodo(0, 0, 0, 0, 0), p)
    }

    @Test
    fun `un ingreso que cae en un ahorro condicionado no cuenta`() {
        val p = calcular(
            listOf(
                evento("sueldo", "ahorros", TransactionType.INCOME, 20_308_659, "Salario"),
                evento("recarga-glim", "glim", TransactionType.INCOME, 621_788, "Alimentación"),
                evento("rendimientos-nu", "nu", TransactionType.INCOME, 1_222_041, "Rendimientos"),
            ),
        )
        assertEquals(20_930_447, p.ingresos)
    }

    @Test
    fun `una compra con tarjeta no es plata que entra - se paga despues desde Tu plata`() {
        val p = calcular(
            listOf(evento("compra", "master", TransactionType.EXPENSE, 2_400_000, "Ropa")),
            // El pago de la tarjeta desde Tu plata tampoco: ni entra ni se guarda.
            listOf(
                evento("pago-sale", "ahorros", TransactionType.EXPENSE, 2_000_000, CARD_PAYMENT_CATEGORY, traspaso = "pt"),
                evento("pago-entra", "master", TransactionType.INCOME, 2_000_000, CARD_PAYMENT_CATEGORY, traspaso = "pt"),
            ),
        )
        assertEquals(PlataDelPeriodo(0, 0, 0, 0, 0), p)
    }

    @Test
    fun `pagar una deuda con un traspaso no es guardar`() {
        val p = calcular(traspaso("t-abono", desde = "ahorros", hacia = "hipotecario", monto = 2_500_000))
        assertEquals(0, p.guardado)
    }

    @Test
    fun `lo que espera en Por confirmar y lo que esta en dolares no cuentan`() {
        val p = calcular(
            listOf(
                evento("sin-confirmar", "ahorros", TransactionType.INCOME, 900_000, "Salario", estado = ReconciliationStatus.UNCONFIRMED),
                evento("usd", "ahorros", TransactionType.INCOME, 100, "Salario", moneda = "USD"),
                evento("colegio-sin-confirmar", "nu", TransactionType.EXPENSE, 3_000_000, "Educación", estado = ReconciliationStatus.UNCONFIRMED),
            ),
        )
        assertEquals(PlataDelPeriodo(0, 0, 0, 0, 0), p)
    }

    @Test
    fun `una pata de traspaso sin la otra no se cuenta`() {
        val p = calcular(listOf(evento("huerfana", "ahorros", TransactionType.INCOME, 1_000_000, TRANSFER_CATEGORY, traspaso = "sola")))
        assertEquals(0, p.desdeFuera)
    }
}
