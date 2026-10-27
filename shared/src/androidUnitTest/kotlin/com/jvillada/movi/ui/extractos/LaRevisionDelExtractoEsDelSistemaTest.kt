package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.ReconciliationMatch
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.COLORES_OSCUROS
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_BOTON
import com.jvillada.movi.ui.components.ds.TAG_CASILLA
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import com.jvillada.movi.ui.components.ds.coloresDelBoton
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta de diseño 20: la revisión de un extracto, en el sistema
 *
 * Era el archivo más fuera del sistema de la app, y se usa cada mes: dos `Button` y dos
 * `OutlinedButton` de Material, el texto del botón principal en `Color.White` (que sobre la lavanda
 * del tema oscuro no se leía), un ícono de casilla de Material para tildar cada fila y los montos en
 * `Text` sueltos. Lo que se fija acá, en el tema oscuro:
 *
 * - **cada botón es un `Boton`** (su variante viaja en la semántica): «Importar» principal, «Anotar
 *   los N» y «No son el mismo» secundarios, «Sí, es el mismo» principal;
 * - **cada fila lleva la `Casilla` del sistema**, que dice su estado, y tocar la fila la destilda;
 * - **los montos son `Cifra`**;
 * - **el texto del botón principal se lee en el tema oscuro** (≥ 4,5:1), y el blanco de antes no.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class LaRevisionDelExtractoEsDelSistemaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)

    private fun fila(id: String, rotulo: String, monto: Long, tipo: TransactionType = TransactionType.EXPENSE) =
        ParsedTransaction(id, "2026-09-28", rotulo, monto, "COP", tipo, "Comida", rotulo, rotulo)

    private val coincidencia = ReconciliationMatch(
        parsed = fila("m1", "RAPPI COLOMBIA", 27_500),
        existingEventId = "ev_1",
        existingEvent = FinancialEvent(
            id = "ev_1", accountId = ahorros.id, amount = 27_500, type = TransactionType.EXPENSE,
            category = "Comida", description = "Rappi", timestamp = 0L,
        ),
        matchConfidence = 0.9f,
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        }
        val result = StatementParseResult(
            statementId = "st_1",
            bankName = "Bancolombia",
            period = "Septiembre 2026",
            newTransactions = listOf(
                fila("c1", "CARULLA RINCON OVIED", 50_000),
                fila("n1", "NOMINA EMPRESA", 4_500_000, TransactionType.INCOME),
            ),
            matches = listOf(coincidencia),
        )
        composeRule.setContent {
            MoviTheme(oscuro = true) { Box(Modifier.fillMaxSize()) { StatementReviewScreen(onNavigate = {}, result = result) } }
        }
        composeRule.waitForIdle()
    }

    /** La variante del `Boton` cuyo texto empieza con [texto]; falla si no es un `Boton`. */
    private fun varianteDe(texto: String): VarianteDeBoton? =
        composeRule.onNode(hasTestTag(TAG_BOTON) and hasText(texto, substring = true))
            .fetchSemanticsNode().config.getOrNull(VarianteDeBotonKey)

    private fun estadoDeLaFila(rotulo: String): ToggleableState? =
        composeRule.onNodeWithText(rotulo).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)

    @Test
    fun cada_boton_es_un_Boton_del_sistema() {
        montar()
        assertEquals(VarianteDeBoton.Principal, varianteDe("Importar"))
        assertEquals(VarianteDeBoton.Secundario, varianteDe("No son el mismo"))
        assertEquals(VarianteDeBoton.Principal, varianteDe("Sí, es el mismo"))
    }

    @Test
    fun cada_fila_lleva_la_casilla_del_sistema_y_se_destilda_al_tocarla() {
        montar()
        assertEquals(
            2,
            composeRule.onAllNodesWithTag(TAG_CASILLA, useUnmergedTree = true).fetchSemanticsNodes().size,
            "una casilla por fila nueva",
        )
        assertEquals(ToggleableState.On, estadoDeLaFila("CARULLA RINCON OVIED"))
        composeRule.onNodeWithText("Importar 2 seleccionadas").assertExists()

        composeRule.onNodeWithText("CARULLA RINCON OVIED").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(ToggleableState.Off, estadoDeLaFila("CARULLA RINCON OVIED"))
        composeRule.onNodeWithText("Importar 1 seleccionada").assertExists()
    }

    @Test
    fun los_montos_son_Cifra() {
        montar()
        val cifras = composeRule.onAllNodesWithTag(TAG_CIFRA, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } }
        assertTrue("−$50.000" in cifras, "el gasto con su signo: $cifras")
        assertTrue("+$4.500.000" in cifras, "el ingreso con «+»: $cifras")
        assertTrue("−$27.500" in cifras, "el monto de la coincidencia: $cifras")
    }

    @Test
    fun el_texto_del_boton_principal_se_lee_en_el_tema_oscuro() {
        val (fondo, texto) = coloresDelBoton(VarianteDeBoton.Principal, COLORES_OSCUROS)
        val conElSistema = contraste(texto, fondo)
        assertTrue(conElSistema >= 4.5, "el texto del botón principal da ${conElSistema}:1 en oscuro")
        // El porqué: el blanco que tenía a mano no pasa sobre la lavanda.
        assertTrue(contraste(Color.White, fondo) < 4.5, "el blanco sobre la marca oscura no debería pasar AA")
    }

    // WCAG 2.2, como en `ContrasteDeLosTokensTest`.
    private fun canal(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminancia(c: Color): Double = 0.2126 * canal(c.red) + 0.7152 * canal(c.green) + 0.0722 * canal(c.blue)

    private fun contraste(a: Color, b: Color): Double {
        val la = luminancia(a)
        val lb = luminancia(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
