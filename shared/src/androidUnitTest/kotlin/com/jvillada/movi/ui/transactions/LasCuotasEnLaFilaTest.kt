package com.jvillada.movi.ui.transactions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 63 (revisión) · una compra diferida dice «a 12 cuotas» junto al monto
 *
 * Es lo que explica por qué el extracto de la tarjeta cuenta menos que la compra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp-xhdpi")
class LasCuotasEnLaFilaTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `el texto de las cuotas solo para mas de una`() {
        assertEquals("a 12 cuotas", textoDeLasCuotas(12))
        assertNull(textoDeLasCuotas(1))
        assertNull(textoDeLasCuotas(null))
    }

    @Test
    fun `la fila de Movimientos dice a cuantas cuotas va la compra`() {
        val tv = FinancialEvent(
            id = "tv", accountId = "nu", type = TransactionType.EXPENSE, amount = 1_200_000L, category = "Hogar",
            description = "Televisor", timestamp = 0L, reconciliationStatus = ReconciliationStatus.RECONCILED, cuotas = 12,
        )
        composeRule.setContent { MoviTheme { MovementSingleRow(tx = tv, accountNames = mapOf("nu" to "Nu"), onClick = {}) } }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("a 12 cuotas", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
