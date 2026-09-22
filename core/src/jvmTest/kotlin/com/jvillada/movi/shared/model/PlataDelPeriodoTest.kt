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
    fun `aperturas y ajustes dentro del periodo suman a lo que tenias, no a lo que entro`() {
        // La Fiducuenta empezó a llevarse el 6-sep con $113.264 y Ahorros se cuadró con el banco
        // (+$225.707, −$26). Esa plata ya estaba: Movi se enteró tarde. En Nu (condicionada) no
        // cuenta, igual que sus ingresos.
        val p = plataDelPeriodo(
            saldoAlInicio = 14_912L,
            eventos = listOf(
                evento("apertura-fidu", "fiducuenta", TransactionType.INCOME, 113_264, OPENING_CATEGORY),
                evento("ajuste-sube", "ahorros", TransactionType.INCOME, 225_707, ADJUSTMENT_CATEGORY),
                evento("ajuste-baja", "ahorros", TransactionType.EXPENSE, 26, ADJUSTMENT_CATEGORY),
                evento("apertura-nu", "nu", TransactionType.INCOME, 15_305_123, OPENING_CATEGORY),
            ),
            cuentas = cuentas,
        )
        assertEquals(14_912L + 113_264 + 225_707 - 26, p.saldoAlInicio)
        assertEquals(0L, p.entradas)
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

    // ── Otros pagos de deuda del período ─────────────────────────────────────────

    /** Las dos patas de un pago a una deuda, con la categoría que escribe `pagoDeCuotaLegs`. */
    private fun pago(id: String, desde: String, hacia: String, monto: Long, categoria: String) = listOf(
        evento("$id-sale", desde, TransactionType.EXPENSE, monto, categoria, traspaso = id),
        evento("$id-entra", hacia, TransactionType.INCOME, monto, categoria, traspaso = id),
    )

    private fun otros(vararg eventos: List<FinancialEvent>, enLosFijos: Map<String, Long> = emptyMap()) =
        pagosDeDeudaFueraDelChecklist(eventos.toList().flatten(), cuentas, enLosFijos)

    @Test
    fun `una cuota sin la otra pata que ningun fijo reclama se resta entera`() {
        val papa = listOf(evento("papa", "ahorros", TransactionType.EXPENSE, 4_280_000, CUOTA_CATEGORY))
        assertEquals(4_280_000, otros(papa))
        // Si un recurrente la reclama (su parte fija), esa parte ya está en los fijos.
        assertEquals(280_000, otros(papa, enLosFijos = mapOf("papa" to 4_000_000L)))
    }

    @Test
    fun `un traspaso a un prestamo cuenta salvo la parte que ya pago una cuota del checklist`() {
        val suelto = traspaso("t-techo", "ahorros", "techo", 700_000)
        val cuota = pago("p-hipo", "ahorros", "hipotecario", 2_500_000, CUOTA_CATEGORY)
        assertEquals(700_000 + 2_500_000, otros(suelto, cuota))
        assertEquals(700_000, otros(suelto, cuota, enLosFijos = mapOf("p-hipo-sale" to 2_500_000L)))
    }

    @Test
    fun `el pago de una tarjeta solo cuenta lo que pasa de lo comprado con ella en el periodo`() {
        val compra = listOf(evento("compra", "master", TransactionType.EXPENSE, 1_370_000, "Ropa"))
        // Paga menos de lo comprado en el período: todo es de este período, nada de antes.
        assertEquals(0, otros(compra, pago("p1", "ahorros", "master", 300_000, CARD_PAYMENT_CATEGORY)))
        assertEquals(1_070_000, comprasConTarjetaSinPagar(compra + pago("p1", "ahorros", "master", 300_000, CARD_PAYMENT_CATEGORY), cuentas))
        // Paga más: lo que pasa es deuda de antes del período. Como traspaso, igual.
        assertEquals(630_000, otros(compra, traspaso("t2", "ahorros", "master", 2_000_000)))
        assertEquals(0, comprasConTarjetaSinPagar(compra + traspaso("t2", "ahorros", "master", 2_000_000), cuentas))
    }

    @Test
    fun `una compra por confirmar no cubre el pago de la tarjeta`() {
        val porConfirmar = listOf(
            evento("nu-compra", "master", TransactionType.EXPENSE, 130_200, "Comida", estado = ReconciliationStatus.UNCONFIRMED),
        )
        assertEquals(115_113, otros(porConfirmar, pago("p-nu", "ahorros", "master", 115_113, CARD_PAYMENT_CATEGORY)))
    }

    @Test
    fun `un pago de tarjeta sin la otra pata se compensa contra lo que quede sin pagar`() {
        val compra = listOf(evento("compra", "master", TransactionType.EXPENSE, 400_000, "Ropa"))
        val suelto = listOf(evento("pago-suelto", "ahorros", TransactionType.EXPENSE, 1_000_000, CARD_PAYMENT_CATEGORY))
        assertEquals(600_000, otros(compra, suelto))
        assertEquals(0, comprasConTarjetaSinPagar(compra + suelto, cuentas))
    }

    @Test
    fun `lo que paga un ahorro de afuera cuenta porque ya entro, y un traspaso entre cuentas no es pago`() {
        // Nu paga la tarjeta: `pagadoDesdeFuera` lo suma como entrada, y acá sale.
        val desdeNu = pago("p-desde-nu", "nu", "master", 500_000, CARD_PAYMENT_CATEGORY)
        assertEquals(500_000, calcular(desdeNu).pagadoDesdeFuera)
        assertEquals(500_000, otros(desdeNu))
        // Un traspaso de Nu al préstamo no entró como nada: tampoco sale.
        assertEquals(0, otros(traspaso("t-nu-techo", "nu", "techo", 300_000)))
        // Entre cuentas de Tu plata, o hacia un ahorro, no es un pago de deuda.
        assertEquals(0, otros(traspaso("t-fidu", "ahorros", "fiducuenta", 1_000_000), traspaso("t-nu", "ahorros", "nu", 500_000)))
        // En dólares no cuenta: el resto de la tarjeta está en pesos.
        assertEquals(0, otros(listOf(evento("usd", "ahorros", TransactionType.EXPENSE, 100, CUOTA_CATEGORY, moneda = "USD"))))
    }
}
