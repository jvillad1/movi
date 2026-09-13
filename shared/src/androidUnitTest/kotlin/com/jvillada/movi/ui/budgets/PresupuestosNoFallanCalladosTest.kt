package com.jvillada.movi.ui.budgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Presupuestos no falla callado
 *
 * Editar un presupuesto ya dejaba la hoja abierta con el motivo cuando el server decía que no.
 * Crear y borrar no: se tragaban el error, recargaban y cerraban la hoja, así que un presupuesto
 * creado sin red «se guardaba» sin existir y uno que no se pudo borrar seguía ahí sin explicación.
 * Y «Guardar» no se bloqueaba mientras la llamada estaba en vuelo, cosa que todas las otras hojas
 * (metas, recurrentes, tarjetas, créditos) sí hacen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_SENSOR)
class PresupuestosNoFallanCalladosTest {

    @get:Rule val composeRule = createComposeRule()

    private val mercado = Budget("Mercado", 500_000L)

    private fun montar(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PresupuestosScreen(onNavigate = {}) } }
        }
    }

    private open inner class ConMercado : RepositorioDePrueba() {
        override suspend fun getBudgets(): List<Budget> = listOf(mercado)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * El clic va por la acción semántica y no por coordenadas: la hoja es más alta que la pantalla
     * de prueba, y un toque inyectado sobre un botón fuera de vista cae en el fondo oscuro, que
     * cierra la hoja. Lo que se prueba es qué hace el botón, no dónde queda.
     */
    private fun tocar(texto: String) {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun abrirMercado() {
        esperarTexto("Mercado")
        composeRule.onAllNodesWithText("Mercado", useUnmergedTree = true).onFirst().performClick()
        esperarTexto("Editar presupuesto")
    }

    @Test
    fun `si borrar falla la hoja queda abierta con el motivo`() {
        montar(object : ConMercado() {
            // `Nothing` porque así lo declara RepositorioDePrueba; lanzar es justo lo que queremos.
            override suspend fun deleteBudget(category: String): Nothing =
                throw ApiException(422, "No se pudo borrar ese presupuesto")
        })
        abrirMercado()

        tocar("Eliminar")

        esperarTexto("No se pudo borrar ese presupuesto")
        composeRule.onNodeWithText("Editar presupuesto", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `si crear falla la hoja queda abierta con el motivo y con lo escrito`() {
        montar(object : ConMercado() {
            override suspend fun createBudget(budget: Budget): Budget =
                throw ApiException(409, "Ya hay un presupuesto para Salud")
        })
        esperarTexto("Mercado")
        composeRule.onAllNodesWithText("Nuevo presupuesto", useUnmergedTree = true).onFirst().performClick()
        esperarTexto("Mercado, Salud, Restaurantes")

        composeRule.onNode(hasSetTextAction(), useUnmergedTree = true).performTextInput("Salud")
        tocar("5")
        tocar("Guardar")

        esperarTexto("Ya hay un presupuesto para Salud")
        composeRule.onNode(hasSetTextAction(), useUnmergedTree = true).assertExists()
        esperarTexto("Salud")
    }

    @Test
    fun `un doble toque en Guardar manda una sola llamada`() {
        val nunca = CompletableDeferred<Budget>()
        var llamadas = 0
        montar(object : ConMercado() {
            override suspend fun updateBudget(category: String, budget: Budget): Budget {
                llamadas++
                return nunca.await()
            }
        })
        abrirMercado()

        tocar("Guardar")
        tocar("Guardar")

        assertEquals(1, llamadas, "el segundo toque tiene que ignorarse mientras el primero está en vuelo")
    }
}

private const val AVD_MOVI_SENSOR = "w411dp-h731dp-xhdpi"
