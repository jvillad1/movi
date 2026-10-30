package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_GUIA_DEL_CAMPO
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 42: «Cómo salir de tus deudas» con una guía que cabe y una sola frase de apoyo
 *
 * `full-07-salida`: la guía del abono decía «Abono extra al mes, por ejemplo $500....» (cortada), y
 * debajo iban dos párrafos densos, uno negro y otro gris. Ahora la guía es «Ej: 500.000», debajo del
 * campo va una sola frase, y los supuestos pasaron a la nota del final.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1600dp-xhdpi")
class LaGuiaDelAbonoCabeTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        }
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) { SalidaDeDeudasScreen(onNavigate = {}) }
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_ABONO_EXTRA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `la guia del abono es Ej 500000 y cabe entera a 360`() {
        montar()

        val guia = composeRule.onNodeWithTag(TAG_GUIA_DEL_CAMPO, useUnmergedTree = true)
        guia.assertTextEquals("Ej: 500.000")
        val medidas = mutableListOf<TextLayoutResult>()
        guia.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
        assertFalse(medidas.single().isLineEllipsized(0), "la guía del abono sale cortada")
    }

    /** Vuelta 77: el resultado primero; los supuestos, una vez y detrás de «¿Qué supone?». */
    @Test
    fun `los supuestos van una vez, detras de que supone, y no dentro del simulador`() {
        montar()

        fun supuestos() = composeRule.onAllNodesWithText("Supone que", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(0, supuestos().size, "cerrados de entrada: el resultado va primero")
        composeRule.onNodeWithTag(TAG_QUE_SUPONE_EL_PLAN, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(1, supuestos().size, "los supuestos se dicen una sola vez")
        val nota = composeRule.onNodeWithTag(TAG_NOTA_DEL_PLAN_DE_SALIDA, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val campo = composeRule.onNodeWithTag(TAG_ABONO_EXTRA, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(nota.top > campo.bottom, "la nota va al final, debajo del simulador")
        composeRule.onNodeWithTag(TAG_NOTA_DEL_PLAN_DE_SALIDA, useUnmergedTree = true).assertTextEquals(PIE_DEL_PLAN_DE_SALIDA)
    }
}
