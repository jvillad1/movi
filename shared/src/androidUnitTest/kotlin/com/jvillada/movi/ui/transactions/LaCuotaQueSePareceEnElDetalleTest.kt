package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CuotaYaPagada
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SI_ES_LA_CUOTA
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.VincularPagoDeDeudaRequest
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * # Vuelta 53 · el detalle de un gasto sugiere la cuota, y la segunda del período pregunta
 *
 * - Un gasto de $980.000 del 2 de octubre, con la cuota de octubre de Libre inversión 9695 sin saldar,
 *   muestra «¿Es la cuota de Libre inversión 9695?» arriba de las acciones; «Sí, es la cuota» llama a
 *   `vincular-deuda` exactamente como la fila «Es la cuota de un crédito» (`esLaCuota`).
 * - Si el server contesta 409 `CUOTA_YA_PAGADA`, la hoja no vincula: pregunta «La cuota de octubre de …
 *   ya está pagada. ¿Este pago es un abono extra?», sin «la de noviembre adelantada» (no existe ese
 *   camino), y «Un abono extra» manda `comoAbono`.
 * - «Es la cuota de un crédito» también está en «Más».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LaCuotaQueSePareceEnElDetalleTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private val ahorros = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 5_000_000L, "COP")
    private val libre = Account("acc-libre", "Libre inversión 9695", AccountType.LOAN, 40_710_555L, "COP")

    private val gasto = FinancialEvent(
        id = "ev-980",
        accountId = ahorros.id,
        type = TransactionType.EXPENSE,
        amount = 980_000L,
        category = "Otros",
        description = "Transferencia Bancolombia",
        timestamp = LocalDateTime.parse("2026-10-02T10:00:00").toInstant(AppTimeZone.zone).toEpochMilliseconds(),
    )

    private val cuotaDeOctubre = UpcomingPayment(
        rule = RecurringRule(
            id = "$CREDIT_RULE_PREFIX${libre.id}", name = "Cuota Libre inversión 9695", category = CUOTA_CATEGORY,
            amount = 980_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
        ),
        dueDate = "2026-10-05",
        daysUntil = 3,
        status = PaymentStatus.DUE_SOON,
    )

    private fun pataDe(request: VincularPagoDeDeudaRequest, categoria: String) = PagoDeCuotaResult(
        deudaRestante = 40_000_000L,
        patas = listOf(
            gasto.copy(category = categoria, transferId = request.transferId),
            gasto.copy(id = request.toEventId, accountId = libre.id, type = TransactionType.INCOME, category = categoria, transferId = request.transferId),
        ),
    )

    private fun montar(onCambiado: () -> Unit = {}) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(event = gasto, cuentas = listOf(ahorros, libre), onDismiss = {}, onCambiado = { onCambiado() })
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String, dentroDe: String? = null) {
        val base = hasClickAction() and hasAnyDescendant(hasText(texto, substring = true))
        val matcher = if (dentroDe != null) base and hasAnyAncestor(hasTestTag(dentroDe)) else base
        composeRule.onAllNodes(matcher, useUnmergedTree = true).onLast().performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun laSugerenciaVinculaComoEsLaCuota() {
        val pedidos = mutableListOf<VincularPagoDeDeudaRequest>()
        var cambiado = 0
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUpcomingPayments() = listOf(cuotaDeOctubre)
            override suspend fun vincularPagoDeDeuda(eventId: String, request: VincularPagoDeDeudaRequest): PagoDeCuotaResult {
                pedidos += request
                return pataDe(request, CUOTA_CATEGORY)
            }
        }
        montar(onCambiado = { cambiado++ })
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_SUGERENCIA_DE_LA_CUOTA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("¿Es la cuota de Libre inversión 9695?", useUnmergedTree = true).assertCountEquals(1)

        tocar(SI_ES_LA_CUOTA, dentroDe = TAG_SUGERENCIA_DE_LA_CUOTA)
        composeRule.waitUntil(5_000) { pedidos.isNotEmpty() }
        val req = pedidos.single()
        assertEquals(libre.id, req.debtAccountId)
        assertEquals(true, req.esLaCuota, "lo mismo que «Es la cuota de un crédito»")
        assertFalse(req.comoAbono)
        composeRule.waitUntil(5_000) { cambiado > 0 }
    }

    /** Revisión de la vuelta 53: la cuota del 5 vencida y sin pagar, vista el 28. `upcoming` ya dice noviembre. */
    @Test
    fun laCuotaVencidaSinPagarTambienSeSugiere() {
        val delDia28 = gasto.copy(amount = 985_000L, timestamp = LocalDateTime.parse("2026-10-28T10:00:00").toInstant(AppTimeZone.zone).toEpochMilliseconds())
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUpcomingPayments() = listOf(cuotaDeOctubre.copy(dueDate = "2026-11-05", daysUntil = 8))
            override suspend fun getOccurrenceStates() = listOf(
                OccurrenceState(ruleId = cuotaDeOctubre.rule.id, period = "2026-10", dueDate = "2026-10-05", occurred = false),
            )
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(event = delDia28, cuentas = listOf(ahorros, libre), onDismiss = {}, onCambiado = {})
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_SUGERENCIA_DE_LA_CUOTA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("¿Es la cuota de Libre inversión 9695?", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun sinUnaCuotaParecidaNoSugiereNada() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            // La de octubre ya se saldó: el vencimiento vigente rodó a noviembre.
            override suspend fun getUpcomingPayments() = listOf(cuotaDeOctubre.copy(dueDate = "2026-11-05"))
        }
        montar()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(TAG_SUGERENCIA_DE_LA_CUOTA, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun laSegundaCuotaDelPeriodoPreguntaSiEsUnAbonoExtra() {
        val pedidos = mutableListOf<VincularPagoDeDeudaRequest>()
        var cambiado = 0
        val ya = CuotaYaPagada(periodo = "2026-10", credito = libre.name, mensaje = "La cuota de octubre de Libre inversión 9695 ya está pagada.")
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUpcomingPayments() = listOf(cuotaDeOctubre)
            override suspend fun vincularPagoDeDeuda(eventId: String, request: VincularPagoDeDeudaRequest): PagoDeCuotaResult {
                pedidos += request
                if (!request.comoAbono) throw ApiException(409, Json.encodeToString(CuotaYaPagada.serializer(), ya))
                return pataDe(request, TRANSFER_CATEGORY)
            }
        }
        montar(onCambiado = { cambiado++ })
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_SUGERENCIA_DE_LA_CUOTA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        tocar(SI_ES_LA_CUOTA, dentroDe = TAG_SUGERENCIA_DE_LA_CUOTA)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_CUOTA_YA_PAGADA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, cambiado, "no se vinculó en silencio")
        composeRule.onAllNodesWithText(
            "La cuota de octubre de Libre inversión 9695 ya está pagada. ¿Este pago es un abono extra?",
            useUnmergedTree = true,
        ).assertCountEquals(1)
        // Solo el abono y «Cancelar»: adelantar la de noviembre no es un camino de Movi.
        composeRule.onAllNodesWithText("adelantada", substring = true, useUnmergedTree = true).assertCountEquals(0)

        tocar(UN_ABONO_EXTRA, dentroDe = TAG_CUOTA_YA_PAGADA)
        composeRule.waitUntil(5_000) { pedidos.size == 2 }
        assertEquals(true, pedidos[1].comoAbono)
        assertFalse(pedidos[1].esLaCuota)
        assertEquals(libre.id, pedidos[1].debtAccountId)
        composeRule.waitUntil(5_000) { cambiado > 0 }
    }

    @Test
    fun esLaCuotaDeUnCreditoTambienEstaEnMas() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {}
        montar()
        composeRule.onNodeWithTag(tagDeAccionDelMovimiento(AccionDelMovimiento.Mas), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar(ES_LA_CUOTA_DE_UN_CREDITO)
        composeRule.onAllNodesWithTag(TAG_ELEGIR_EL_CREDITO, useUnmergedTree = true).assertCountEquals(1)
    }
}
