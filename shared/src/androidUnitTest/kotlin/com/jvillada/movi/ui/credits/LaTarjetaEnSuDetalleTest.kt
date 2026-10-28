package com.jvillada.movi.ui.credits

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.TAG_TOTAL_DEL_DIA_DE_LA_CUENTA
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.transactions.TAG_FILA_DE_MOVIMIENTO_SUELTO
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La tarjeta en su detalle (vuelta de diseño 32)
 *
 * - **El total del día dice lo mismo que su fila.** En «Nu» el día de «Almuerzo con el equipo» decía
 *   «+$42.000» arriba y «−$42.000» en la fila: el total salía del lado de la deuda.
 * - **Lo que el banco manda en mayúsculas** («EXITO SUBA») se lee en forma de título en sus filas.
 * - **La tarjeta también tiene «Qué puedes hacer»**, con o sin sus datos: «Registrar un pago» abre
 *   «Agregar» con la tarjeta en «Hacia», la misma hoja de «Anotar este pago».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h2400dp-xhdpi")
class LaTarjetaEnSuDetalleTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val nu = CardSummary(
        account = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, 42_000L, balancesByCurrency = mapOf("COP" to 42_000L)),
        terms = null,
    )

    private val ahora = Clock.System.now().toEpochMilliseconds()

    private fun compra(id: String, descripcion: String, monto: Long, haceDias: Int = 0) = FinancialEvent(
        id = id, accountId = nu.account.id, type = TransactionType.EXPENSE, amount = monto, category = "Comida",
        description = descripcion, timestamp = ahora - haceDias * 86_400_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textoDe(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    @Test
    fun `en una tarjeta una compra de 42000 dice menos 42000 en el dia y en su fila`() {
        composeRule.montarElDetalleDe(
            nu.account,
            RepositorioDeLaDeuda(tarjetas = listOf(nu), eventos = listOf(compra("e1", "Almuerzo con el equipo", 42_000))),
        )
        composeRule.waitUntil(5_000) { hay("Almuerzo con el equipo") }

        assertEquals("−\$42.000", textoDe(TAG_TOTAL_DEL_DIA_DE_LA_CUENTA), "el total del día")
        val cifraDeLaFila = composeRule.onAllNodes(
            hasTestTag(TAG_CIFRA) and hasAnyAncestor(hasTestTag(TAG_FILA_DE_MOVIMIENTO_SUELTO)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        assertEquals("−\$42.000", cifraDeLaFila, "la fila dice lo mismo")
    }

    @Test
    fun `lo que llego en mayusculas se lee en forma de titulo en la fila`() {
        composeRule.montarElDetalleDe(
            nu.account,
            RepositorioDeLaDeuda(tarjetas = listOf(nu), eventos = listOf(compra("e1", "EXITO SUBA", 42_000))),
        )
        composeRule.waitUntil(5_000) { hay("Éxito Suba") }
        assertTrue(!hay("EXITO SUBA"), "el nombre del banco no grita")
    }

    @Test
    fun `la tarjeta sin datos tambien dice que puedes hacer, y registrar un pago abre Agregar`() {
        val pantallas = mutableListOf<Screen>()
        composeRule.montarElDetalleDe(nu.account, RepositorioDeLaDeuda(tarjetas = listOf(nu)), onNavigate = { pantallas += it })

        assertTrue(hay("Faltan los datos de esta tarjeta"), "el aviso de arriba sigue")
        assertTrue(hay("Qué puedes hacer"))
        composeRule.onNode(hasText(REGISTRAR_UN_PAGO_DE_LA_TARJETA) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf<Screen>(Screen.QuickAdd(presetDeudaId = nu.account.id)), pantallas)
    }
}
