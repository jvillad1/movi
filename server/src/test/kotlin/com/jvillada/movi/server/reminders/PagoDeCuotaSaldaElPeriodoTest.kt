package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.desglosarCuota
import com.jvillada.movi.shared.model.pagoDeCuotaLegs
import com.jvillada.movi.shared.model.transferLegsFor
import com.jvillada.movi.shared.model.CreateTransferRequest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 48 · lo que guarda Agregar → Cuota salda la cuota del período, y el abono extra no
 *
 * En Agregar → Cuota, tocar un crédito pasa a ser «Cuota de X · mes» y guarda **el pago de cuota de
 * siempre** (`POST /api/credits/pago`, `pagoDeCuotaLegs`). El abono extra, la opción secundaria de la
 * misma hoja, guarda **el traspaso de siempre** (`transferLegsFor`). Sin reglas de plata nuevas: lo que
 * se fija acá es que esas dos piezas, juntas, dicen lo que la hoja promete —las dos patas saldan la
 * cuota del período (`pagosDeDeudaPorPeriodo`, la regla «un pago salda la cuota del período en que se
 * hizo») y el abono no—.
 */
class PagoDeCuotaSaldaElPeriodoTest {

    private val ahorros = Account("acc_nu", "Nu Ahorros", AccountType.SAVINGS, 2_300_000)
    private val libre = Account("acc_libre", "Libre inversión 9695", AccountType.LOAN, 40_710_555)

    private val regla = RecurringRule(
        id = "$CREDIT_RULE_PREFIX${libre.id}", name = "Cuota Libre inversión 9695", category = CUOTA_CATEGORY,
        amount = 1_204_064, dayOfMonth = 5, type = TransactionType.EXPENSE,
    )

    private val hoy = LocalDate.of(2026, 10, 6)

    @Test
    fun `la cuota de la hoja escribe dos patas y salda el periodo`() {
        val pedido = CreatePagoDeCuotaRequest(
            fromAccountId = ahorros.id, debtAccountId = libre.id, amount = 1_204_064,
            timestamp = appDateToEpochMillis(hoy), transferId = "tr_1", fromEventId = "ev_sale", toEventId = "ev_baja",
        )
        val desglose = desglosarCuota(
            cuota = pedido.amount, tipoDeLaDeuda = libre.type, saldoDeLaDeuda = libre.balance, rateEa = 11.27,
            seguroMensual = 124_800, otrosCargosMensuales = null, yaCobradoEnElMes = 0L, sinIntereses = false,
        )
        val (sale, baja) = pagoDeCuotaLegs(pedido, ahorros, libre, desglose)

        assertEquals(TransactionType.EXPENSE, sale.type)
        assertEquals(1_204_064L, sale.amount)
        assertEquals(TransactionType.INCOME, baja.type)
        assertEquals(libre.id, baja.accountId)
        assertEquals("ev_baja", pagosDeDeudaPorPeriodo(listOf(regla), listOf(sale, baja))[regla.id]?.get("2026-10")?.id)
    }

    @Test
    fun `el abono extra es un traspaso y no salda la cuota`() {
        val pedido = CreateTransferRequest(
            transferId = "tr_2", fromEventId = "ev_a", toEventId = "ev_b", fromAccountId = ahorros.id,
            toAccountId = libre.id, amount = 500_000, timestamp = appDateToEpochMillis(hoy),
        )
        val (sale, entra) = transferLegsFor(pedido, ahorros, libre)

        assertEquals(TRANSFER_CATEGORY, sale.category)
        assertEquals(TRANSFER_CATEGORY, entra.category)
        assertNull(pagosDeDeudaPorPeriodo(listOf(regla), listOf(sale, entra))[regla.id])
    }
}
