package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.SuperficieDestacadaKey
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 42: «Revisar aviso» con el lenguaje de la opción 3
 *
 * La revisión del 28-oct (`full-04-reconciliar`, a 390): el aviso y «Movi sugiere» eran dos tarjetas
 * con sombra seguidas; el comercio salía en mayúsculas («RAPPI») en «Confirma o ajusta»; las pastillas
 * de categoría estaban siempre sueltas entre la lista y los botones (eso lo prueba
 * `ElAvisoDelBancoElegisTusCategoriasTest`); y el «Ojo: el aviso es de Falabella…» iba pegado al porqué
 * de la cuenta y se cortaba en «…de Bancolo…».
 *
 * A 360 dp y con la letra de la app (×1,12), lo más angosto que se usa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class ReconciliarDeLaOpcion3Test {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private fun montar(banco: String = "Falabella") {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            override suspend fun getSms(id: String): SmsMessage = SmsMessage(
                id = id,
                time = "11:02",
                bank = banco,
                text = "Compra aprobada por \$42.500 en EXITO SUBA el 02/09/2026.",
                state = SMS_STATE_PENDING,
                det = "",
            )
            override suspend fun parseSms(id: String): ParsedSms =
                ParsedSms(42_500.0, "EXITO SUBA", TransactionType.EXPENSE, "Mercado")
        }
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = "s1") }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val destacada = SemanticsMatcher.expectValue(SuperficieDestacadaKey, true)

    @Test
    fun `una sola sombra, la de Movi sugiere, y el aviso va plano`() {
        montar()

        val conSombra = composeRule.onAllNodes(destacada, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, conSombra.size, "solo «Movi sugiere» lleva sombra")
        composeRule.onNodeWithTag(TAG_AVISO_EN_RECONCILIAR, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SuperficieDestacadaKey))
        // La sombra es la de la propuesta: la que dice el comercio y el monto que Movi va a anotar.
        assertEquals(
            1,
            composeRule.onAllNodes(hasText("Éxito Suba") and hasAnyAncestor(destacada), useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `el comercio de Confirma o ajusta va en forma de titulo, no gritando`() {
        montar()

        // Vuelta 57: fuera de edición, el comercio es un texto (desde el principio, con «…» al final).
        composeRule.onNodeWithTag(TAG_COMERCIO_A_LA_VISTA, useUnmergedTree = true).assertTextEquals("Éxito Suba")
        assertTrue(
            composeRule.onAllNodesWithText("EXITO SUBA", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "«EXITO SUBA» en mayúsculas",
        )
    }

    @Test
    fun `el Ojo sale una sola vez y entero`() {
        montar()

        val ojos = composeRule.onAllNodesWithText("Ojo:", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, ojos.size, "el «Ojo» se repite")
        val nodos = composeRule.onAllNodesWithTag(TAG_ADVERTENCIA_DE_BANCOS, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, nodos.size)
        composeRule.onNodeWithTag(TAG_ADVERTENCIA_DE_BANCOS, useUnmergedTree = true)
            .assertTextEquals("Ojo: el aviso es de Falabella y la cuenta es de Bancolombia")
        val medidas = mutableListOf<TextLayoutResult>()
        nodos.single().config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
        val medida = medidas.single()
        assertTrue((0 until medida.lineCount).none { medida.isLineEllipsized(it) }, "el «Ojo» sale cortado")
        // Y la fila de la cuenta dice su porqué solo, sin el «Ojo» pegado.
        composeRule.onNodeWithTag(tagDeFilaDelSms("Cuenta")).assert(hasText("La puso Movi"))
    }

    @Test
    fun `sin bancos distintos no hay Ojo`() {
        montar(banco = "Bancolombia")
        assertTrue(composeRule.onAllNodesWithTag(TAG_ADVERTENCIA_DE_BANCOS, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
