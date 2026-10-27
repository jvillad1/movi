package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.components.ds.TAG_ACCION_DEL_ENCABEZADO
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.destinos.DestinosScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La acción de un encabezado se ve igual en todas las pantallas (vuelta de diseño 4)
 *
 * Patrimonio pintaba «+ Nueva cuenta» como texto morado y Créditos «+ Nuevo crédito» como un botón
 * tonal con relleno. Ahora toda acción principal de un encabezado pasa por el mismo componente
 * (`AccionDelEncabezado`, con su `testTag`): se monta Patrimonio, Créditos y Personas y comercios, y
 * se mide que las tres sean ese componente, dentro del encabezado y del mismo alto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xhdpi")
class LaAccionDelEncabezadoEsUnaSolaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val hipoteca = Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 1_030_600_000L)

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(
            Account("acc-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L),
            hipoteca,
        )
        override suspend fun getCredits(): List<CreditSummary> = listOf(CreditSummary(account = hipoteca, terms = null, paidPct = null))
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> = listOf(DestinoConocido(id = "d1", nombre = "Caro", numero = "55500000756"))
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "jvillad1@gmail.com", name = "Juan", avatarColor = "#7C6CF0", periodCutoffDay = 25, reminderLeadDays = 5)
        override suspend fun isScreenAdmin(): Boolean = false
    }

    private enum class Pantalla { Patrimonio, Creditos, Personas }

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun textosDentro(nodo: SemanticsNode): List<String> =
        listOfNotNull(nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }) +
            nodo.children.flatMap(::textosDentro)

    private fun contiene(afuera: Rect, adentro: Rect): Boolean =
        adentro.left >= afuera.left - 0.5f && adentro.right <= afuera.right + 0.5f &&
            adentro.top >= afuera.top - 0.5f && adentro.bottom <= afuera.bottom + 0.5f

    @Test
    fun `Patrimonio, Creditos y Personas pintan su accion con el mismo componente`() {
        Repositories.sustitutoDePrueba = repo
        var pantalla by mutableStateOf(Pantalla.Patrimonio)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    when (pantalla) {
                        Pantalla.Patrimonio -> AccountsScreen(onNavigate = {})
                        Pantalla.Creditos -> CreditosScreen(onNavigate = {})
                        Pantalla.Personas -> DestinosScreen(onNavigate = {})
                    }
                }
            }
        }
        val esperado = mapOf(
            Pantalla.Patrimonio to "Nueva cuenta",
            Pantalla.Creditos to "Nuevo crédito",
            Pantalla.Personas to "Nueva",
        )
        val altos = esperado.map { (cual, texto) ->
            pantalla = cual
            composeRule.waitUntil(timeoutMillis = 5_000) {
                nodos(TAG_ACCION_DEL_ENCABEZADO).any { texto in textosDentro(it) }
            }
            composeRule.waitForIdle()
            val accion = nodos(TAG_ACCION_DEL_ENCABEZADO).single()
            assertTrue(texto in textosDentro(accion), "$cual: la acción dice «$texto»")
            val encabezado = nodos(TAG_ENCABEZADO).first()
            assertTrue(contiene(encabezado.boundsInRoot, accion.boundsInRoot), "$cual: la acción va dentro del encabezado")
            accion.size.height
        }
        assertTrue(altos.all { abs(it - altos.first()) <= 1 }, "la acción mide lo mismo en las tres: $altos")
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Nuevo crédito", useUnmergedTree = true).fetchSemanticsNodes().size,
            "al volver a Personas, «Nuevo crédito» ya no está",
        )
    }
}
