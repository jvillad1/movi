package com.jvillada.movi.ui.listas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.goals.GoalSheet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Metas, recurrentes, términos y suscripciones se borraban con un toque. Se prueba la meta —el
 * «Eliminar» más fácil de tocar sin querer, al lado del título—: tocarlo no borra nada hasta
 * confirmar, y cancelar no borra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class BorrarPreguntaAntesTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val cuenta = Account("acc-ahorro", "Ahorros", AccountType.SAVINGS, 1_000_000L, "COP")
    private val meta = Goal(id = "g1", name = "Viaje", target = 5_000_000L, accountId = cuenta.id)

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `eliminar una meta pregunta antes y cancelar no borra`() {
        val borradas = mutableListOf<String>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun deleteGoal(id: String) { borradas += id }
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { GoalSheet(accounts = listOf(cuenta), onDismiss = {}, onSaved = {}, existing = meta) } }
        }
        composeRule.waitForIdle()

        tocar("Eliminar")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("¿Eliminar la meta «Viaje»?", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(emptyList(), borradas, "tocar Eliminar solo pregunta")

        tocar("Cancelar")
        assertEquals(emptyList(), borradas)

        tocar("Eliminar")
        // El segundo «Eliminar» es el de la confirmación.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Eliminar", useUnmergedTree = true).fetchSemanticsNodes().size >= 2
        }
        val botones = composeRule.onAllNodesWithText("Eliminar", useUnmergedTree = true)
        botones[botones.fetchSemanticsNodes().size - 1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { borradas.isNotEmpty() }
        assertEquals(listOf("g1"), borradas)
    }
}
