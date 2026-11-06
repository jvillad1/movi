package com.jvillada.movi.ui.credits

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AlCambiarLaTasa
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.IndiceDeLaTasa
import com.jvillada.movi.shared.model.TipoDeCuota
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 133 · «¿Tu cuota es siempre la misma?» en la hoja del crédito
 *
 * Sí por defecto (y guardar sin tocar nada deja la cuota fija); «No, cambia» abre el porqué y, con tasa
 * variable, los puntos sobre el IBR y qué cambia al reajustarse, y todo eso llega al repositorio. A 360 dp
 * con la letra grande (1,456): las pastillas no se cortan.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class HojaDeCondicionesCuotaQueCambiaTest {

    @get:Rule val composeRule = createComposeRule()

    private val libre = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, 40_000_000)
    private val condiciones = CreditTerms(
        accountId = libre.id, bank = "Bancolombia", principal = 50_000_000L, rateEa = 18.0, termMonths = 72,
        installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
    )
    private var guardado: CreditTerms? = null

    @After
    fun limpiar() { Repositories.sustitutoDePrueba = null }

    private fun montar(terms: CreditTerms = condiciones) {
        guardado = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(libre)
            override suspend fun getDestinos() = emptyList<com.jvillada.movi.shared.model.DestinoConocido>()
            override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary {
                guardado = terms
                return CreditSummary(account = libre, terms = terms, paidPct = null)
            }
        }
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * ESCALA_DEL_TELEFONO_AL_130)) {
                MoviTheme {
                    CreditTermsSheet(
                        editing = CreditSummary(account = libre, terms = terms, paidPct = null),
                        candidates = emptyList(),
                        onDismiss = {},
                        onSaved = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun guardar(): CreditTerms {
        composeRule.onNodeWithText("Guardar crédito").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { guardado != null }
        return assertNotNull(guardado)
    }

    @Test
    fun `por defecto la cuota es la misma, y guardar asi la deja fija`() {
        montar()
        assertEquals(1, nodos(TAG_LA_CUOTA_ES_FIJA).size)
        assertTrue(nodos(tagDelPorQue(TipoDeCuota.UVR)).isEmpty(), "sin «No, cambia» no se pregunta el porqué")
        val terms = guardar()
        assertNull(terms.tipoDeCuota, "fija se guarda como siempre")
        assertNull(terms.puntosSobreElIndice)
    }

    @Test
    fun `no cambia, con tasa variable, guarda los puntos y que cambia`() {
        montar()
        tocar(TAG_LA_CUOTA_CAMBIA)
        RAZONES_DE_LA_CUOTA_QUE_CAMBIA.forEach { razon -> assertEquals(1, nodos(tagDelPorQue(razon)).size, "la razón $razon") }
        // Las pastillas bajan de renglón, nunca se salen de la pantalla (360 dp, letra al 1,456).
        val ancho = composeRule.onRoot().fetchSemanticsNode().size.width
        RAZONES_DE_LA_CUOTA_QUE_CAMBIA.forEach { razon ->
            val nodo = nodos(tagDelPorQue(razon)).single()
            assertTrue(nodo.boundsInRoot.right <= ancho + 0.5f, "«${razon}» cabe a lo ancho (${nodo.boundsInRoot.right} > $ancho)")
        }
        composeRule.onNodeWithTag(TAG_PUNTOS_SOBRE_EL_IBR, useUnmergedTree = true).assertExists()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_PUNTOS_SOBRE_EL_IBR)), useUnmergedTree = true).performTextInput("8,5")
        tocar(tagDeAlCambiar(AlCambiarLaTasa.CAMBIA_EL_PLAZO))
        val terms = guardar()
        assertEquals(TipoDeCuota.TASA_VARIABLE, terms.tipoDeCuota)
        assertEquals(IndiceDeLaTasa.IBR, terms.indiceDeLaTasa)
        assertEquals(8.5, terms.puntosSobreElIndice)
        assertEquals(AlCambiarLaTasa.CAMBIA_EL_PLAZO, terms.alCambiarLaTasa)
    }

    @Test
    fun `editar un credito en UVR lo muestra y volver a Si lo apaga`() {
        montar(condiciones.copy(tipoDeCuota = TipoDeCuota.UVR))
        assertEquals(1, nodos(tagDelPorQue(TipoDeCuota.UVR)).size, "lo guardado se ve al editar")
        assertTrue(nodos(TAG_PUNTOS_SOBRE_EL_IBR).isEmpty(), "los puntos son solo de la tasa variable")
        tocar(TAG_LA_CUOTA_ES_FIJA)
        assertNull(guardar().tipoDeCuota)
    }
}
