package com.jvillada.movi.ui.components.ds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.credits.CardTermsSheet
import com.jvillada.movi.ui.credits.CreditTermsSheet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # El campo del sistema cumple lo que promete
 *
 * Vuelta de diseño 6: [Campo], [CampoDeMonto], [GrupoDeCampos] y [PieDeHoja], sin pantalla
 * alrededor, y después las dos hojas de créditos —las peores antes de esta vuelta— a 360 dp con la
 * letra a ×1,3.
 *
 * - el rótulo va en frase, aunque llegue en mayúsculas;
 * - en un monto, la guía va en la letra de cuerpo y lo escrito en la de las cifras;
 * - el error se pinta en el color de alerta (y el lector de pantalla lo dice);
 * - la ayuda larga se abre y se cierra con «¿Qué es esto?»;
 * - nada se recorta.
 *
 * `@GraphicsMode(NATIVE)` + `sdk = [34]`: hace falta el motor de texto real para medir cortes (en
 * SDK 24 un texto con `lineHeight` mide ancho 0).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LosCamposDelSistemaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private lateinit var familiaDeCuerpo: FontFamily
    private lateinit var familiaDeCifras: FontFamily
    private var colorDeAlerta: Color = Color.Unspecified

    private fun montar(escala: Float = 1f, contenido: @Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
                familiaDeCuerpo = Movi.textos.cuerpo.fontFamily!!
                familiaDeCifras = Movi.textos.monto.fontFamily!!
                colorDeAlerta = Movi.colores.sale
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                    Box(Modifier.fillMaxSize()) { contenido() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult? {
        val accion = nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult) ?: return null
        val resultados = mutableListOf<TextLayoutResult>()
        accion.action?.invoke(resultados)
        return resultados.firstOrNull()
    }

    private fun layoutDelTag(tag: String, indice: Int = 0): TextLayoutResult =
        assertNotNull(layoutDe(composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)[indice].fetchSemanticsNode()))

    private fun textoDe(nodo: SemanticsNode): String =
        (nodo.config.getOrNull(SemanticsProperties.Text) ?: nodo.config.getOrNull(SemanticsProperties.EditableText)?.let { listOf(it) })
            ?.joinToString { it.text }.orEmpty()

    // ── El rótulo ────────────────────────────────────────────────────────────────────────

    @Test
    fun `el rotulo va en frase, aunque llegue en mayusculas`() {
        montar {
            Column {
                Campo("", {}, rotulo = "Capital original")
                Campo("", {}, rotulo = "TÉRMINOS")
                GrupoDeCampos("LA PLATA QUE TE ENTRÓ") {}
            }
        }
        val rotulos = composeRule.onAllNodesWithTag(TAG_ROTULO_DEL_CAMPO, useUnmergedTree = true)
            .fetchSemanticsNodes().map(::textoDe)
        assertEquals(listOf("Capital original", "Términos"), rotulos)
        composeRule.onNodeWithText("La plata que te entró", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("TÉRMINOS", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `el rotulo sigue ahi con el campo lleno, y la guia se va`() {
        montar { Campo("240", {}, rotulo = "Plazo (meses)", guia = "60") }
        composeRule.onNodeWithText("Plazo (meses)", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_GUIA_DEL_CAMPO, useUnmergedTree = true).assertCountEquals0()
    }

    // ── El monto ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `en un monto la guia va en la letra de cuerpo y la moneda aparte`() {
        montar { CampoDeMonto(null, {}, rotulo = "Capital original", guia = "Lo que te prestaron") }
        val guia = layoutDelTag(TAG_GUIA_DEL_CAMPO)
        assertEquals(familiaDeCuerpo, guia.layoutInput.style.fontFamily, "la guía en la letra de cuerpo")
        assertEquals("Lo que te prestaron", guia.layoutInput.text.text, "la guía no lleva el «$»")
        assertEquals("$", textoDe(composeRule.onNodeWithTag(TAG_MONEDA_DEL_CAMPO, useUnmergedTree = true).fetchSemanticsNode()))
    }

    @Test
    fun `en un monto lo escrito va en la letra de las cifras`() {
        montar { CampoDeMonto(1_800_000L, {}, rotulo = "Capital original", guia = "Lo que te prestaron") }
        val valor = layoutDelTag(TAG_CAMPO_DE_MONTO)
        assertEquals("1.800.000", valor.layoutInput.text.text)
        assertEquals(familiaDeCifras, valor.layoutInput.style.fontFamily, "el valor en la letra de las cifras")
        assertNotEquals(familiaDeCuerpo, familiaDeCifras, "si las dos letras fueran la misma, esta prueba no diría nada")
        val moneda = layoutDelTag(TAG_MONEDA_DEL_CAMPO)
        assertEquals(familiaDeCifras, moneda.layoutInput.style.fontFamily, "la moneda va con la cifra")
    }

    @Test
    fun `MoneyField delega y quita el simbolo de su placeholder viejo`() {
        montar {
            com.jvillada.movi.ui.components.MoneyField(null, {}, placeholder = "Deuda real según el banco (COP)")
        }
        val guia = layoutDelTag(TAG_GUIA_DEL_CAMPO)
        assertEquals("Deuda real según el banco (COP)", guia.layoutInput.text.text)
        assertEquals(familiaDeCuerpo, guia.layoutInput.style.fontFamily)
    }

    // ── El error ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `el error se pinta en el color de alerta y el campo lo anuncia`() {
        montar { Campo("", {}, rotulo = "Banco", ayuda = "El que te prestó", error = "Falta el banco") }
        val error = layoutDelTag(TAG_ERROR_DEL_CAMPO)
        assertEquals("Falta el banco", error.layoutInput.text.text)
        assertEquals(colorDeAlerta, error.layoutInput.style.color)
        composeRule.onAllNodesWithTag(TAG_AYUDA_DEL_CAMPO, useUnmergedTree = true).assertCountEquals0()
        val campo = composeRule.onNodeWithTag(TAG_CAMPO, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("Falta el banco", campo.config.getOrNull(SemanticsProperties.Error))
    }

    // ── La ayuda ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `la ayuda larga se abre con que es esto y se vuelve a cerrar`() {
        val larga = "Esa parte no baja la deuda, así que sin este dato Movi te mostraría menos deuda."
        montar { CampoDeMonto(null, {}, rotulo = "Seguro mensual", ayuda = "El seguro de la cuota.", masAyuda = larga) }
        composeRule.onNodeWithText("El seguro de la cuota.", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(larga, useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithText(TEXTO_QUE_ES_ESTO, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag(TAG_MAS_AYUDA_DEL_CAMPO, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(larga, useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithText(TEXTO_OCULTAR_AYUDA, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText(larga, useUnmergedTree = true).assertDoesNotExist()
    }

    // ── El pie ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `el pie dice por que el boton esta apagado, y solo entonces`() {
        montar {
            Column {
                PieDeHoja("Guardar crédito", {}, habilitado = false, razon = "Falta el banco")
                PieDeHoja("Guardar tarjeta", {}, habilitado = true, razon = "No debería verse")
            }
        }
        composeRule.onNodeWithTag(TAG_RAZON_DEL_PIE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Falta el banco", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("No debería verse", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Nada se recorta ──────────────────────────────────────────────────────────────────

    /** Cada texto de la pantalla: ni «…», ni más ancho que su caja. */
    private fun ningunTextoSeRecorta() {
        val nodos = composeRule.onAllNodes(androidx.compose.ui.test.hasAnyAncestor(isRoot()), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val recortados = nodos.mapNotNull { nodo ->
            val layout = layoutDe(nodo) ?: return@mapNotNull null
            // «…» o un renglón que termina más allá del ancho que le tocó. No `hasVisualOverflow`
            // ni `didOverflowWidth`: en Robolectric los dos dicen «sí» de todo texto de ancho
            // ajustado (comparan con el ancho máximo, no con el que el renglón ocupa).
            val cortado = (0 until layout.lineCount).any {
                layout.isLineEllipsized(it) || layout.getLineRight(it) > layout.size.width + 1f
            }
            if (cortado) textoDe(nodo) else null
        }
        assertTrue(recortados.isEmpty(), "Textos recortados a 360 dp con la letra a ×1,3: $recortados")
    }

    /** Un detector que nunca encuentra nada no se distingue de uno roto: acá tiene que encontrar. */
    @Test
    fun `el detector de recortes ve un rotulo que no cabe`() {
        montar(escala = 1.3f) {
            Box(Modifier.padding(Medida.margenDePantalla)) {
                Box(Modifier.fillMaxSize(0.2f)) {
                    Campo("", {}, rotulo = "Un rótulo larguísimo que a este ancho no puede caber entero")
                }
            }
        }
        val fallo = runCatching { ningunTextoSeRecorta() }.exceptionOrNull()
        assertNotNull(fallo, "el rótulo de un renglón se cortó con «…» y el detector no lo vio")
    }

    @Test
    fun `un formulario del sistema no recorta nada a 360 dp con la letra a 1,3`() {
        montar(escala = 1.3f) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(Medida.margenDePantalla)) {
                GrupoDeCampos("Términos") {
                    Campo("", {}, rotulo = "Banco", guia = "Ej: Bancolombia")
                    CampoDeMonto(null, {}, rotulo = "Capital original")
                    Row(horizontalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion)) {
                        Box(Modifier.weight(1f)) { Campo("", {}, rotulo = "Corte (opcional)", guia = "20") }
                        Box(Modifier.weight(1f)) { Campo("", {}, rotulo = "Día de pago", guia = "5") }
                    }
                    CampoDeMonto(
                        4_101_123L, {},
                        rotulo = "Seguro mensual (opcional)",
                        ayuda = "El seguro de vida deudor que va dentro de la cuota.",
                        masAyuda = "Esa parte no baja la deuda.",
                    )
                }
                PieDeHoja("Guardar crédito", {}, habilitado = false, razon = "Falta el banco")
            }
        }
        ningunTextoSeRecorta()
    }

    @Test
    fun `cada caja se puede tocar con un dedo`() {
        montar { Campo("", {}, guia = "Buscar categoría", modifier = Modifier.testTag("caja")) }
        val alto = with(composeRule.density) {
            composeRule.onNodeWithTag("caja", useUnmergedTree = true).fetchSemanticsNode().size.height.toDp()
        }
        assertTrue(alto >= Medida.objetivoTactil, "la caja mide $alto")
    }

    @Test
    fun `la hoja de un credito no recorta nada a 360 dp con la letra a 1,3`() {
        val vehiculo = Account("acc_8761", "Vehículo 8761", AccountType.LOAN, 177_200_000)
        val terms = CreditTerms(
            accountId = vehiculo.id, bank = "Banco de Occidente", principal = 190_000_000L, rateEa = 18.16,
            termMonths = 60, installment = 4_101_123L, dayOfMonth = 20, startDate = "2024-06-01",
            insuranceMonthly = 89_100L, otrosCargosMensuales = 25_000L,
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(vehiculo)
        }
        montar(escala = 1.3f) {
            CreditTermsSheet(CreditSummary(account = vehiculo, terms = terms, paidPct = null), emptyList(), {}, {})
        }
        ningunTextoSeRecorta()
        composeRule.onNodeWithText("Términos", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `la hoja de una tarjeta no recorta nada a 360 dp con la letra a 1,3`() {
        val master = Account("acc_3684", "Master Black 3684", AccountType.CREDIT_CARD, 4_000_000)
        val terms = CardTerms(accountId = master.id, bank = "Bancolombia", creditLimit = 20_000_000L, cutoffDay = 30, paymentDay = 15)
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(master)
        }
        montar(escala = 1.3f) {
            CardTermsSheet(CardSummary(account = master, terms = terms), {}, {})
        }
        ningunTextoSeRecorta()
        composeRule.onNodeWithText("Pago mínimo del extracto (opcional)", useUnmergedTree = true).assertExists()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() {
        assertEquals(0, fetchSemanticsNodes().size)
    }
}
