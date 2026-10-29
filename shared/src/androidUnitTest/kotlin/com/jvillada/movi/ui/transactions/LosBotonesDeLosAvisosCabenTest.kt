package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.porrevisar.AnotacionEntendida
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
 * # Vuelta 53 · los botones de la tarjeta de avisos nunca se cortan
 *
 * La revisión de la vuelta 51 vio «Revisar uno por» a 390 dp: el `FlowRow` con `weight` decidía el
 * renglón con el ancho mínimo (la palabra más larga) y dejaba los dos botones lado a lado aunque no
 * cabían. Ahora [LadoALadoOApilados] mide el rótulo entero. A 360 y 390, con la letra de la app (×1,12)
 * y agrandada (×1,3), los rótulos se leen completos, con «Confirmar los 2» y con «Confirmar los 2 que
 * Movi entendió»; cuando ese no cabe ni solo (360 a ×1,3), va «Confirmar los 2». Se mide con el motor
 * de texto real.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LosBotonesDeLosAvisosCabenTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000L, "COP")

    private fun entendida(id: String, comercio: String) = AnotacionEntendida(
        cabezaId = id,
        sms = SmsMessage(id = id, time = "2026-10-28T09:00:00", bank = "Bancolombia", text = "Compra", state = SMS_STATE_PENDING, det = ""),
        pago = null,
        leido = ParsedSms(amount = 27_500.0, merchant = comercio, type = TransactionType.EXPENSE, category = "Comida"),
        cuenta = ahorros,
        categoria = "Comida",
    )

    private val dos = listOf(entendida("s1", "Rappi"), entendida("s2", "D1"))

    /** Todos los entendidos: «Confirmar los 2» · «Revisar uno por uno». */
    private val todos = AvisosPorConfirmar(cuantos = 2, suman = 55_000L, comercios = "Rappi y D1", confirmables = dos)

    /** Algunos: «Confirmar los 2 que Movi entendió» · «Revisar uno por uno». */
    private val algunos = AvisosPorConfirmar(cuantos = 3, suman = null, comercios = "Rappi, D1 y Uber", confirmables = dos)

    private fun montar(escala: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                MoviTheme {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla),
                        verticalArrangement = Arrangement.spacedBy(Espacio.m),
                    ) {
                        TarjetaDeAvisosDelBanco(avisos = todos, onConfirmar = {}, onRevisar = {})
                        TarjetaDeAvisosDelBanco(avisos = algunos, onConfirmar = {}, onRevisar = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Cada rótulo de los botones de las dos tarjetas: entero, en un renglón y dentro de la pantalla. */
    private fun losRotulos(ancho: Int): List<String> {
        val nodos = composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(TAG_BOTONES_DE_LOS_AVISOS)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        return nodos.map { nodo ->
            val texto = nodo.config[SemanticsProperties.Text].joinToString("") { it.text }
            val r = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            val d = r.single()
            assertEquals(1, d.lineCount, "«$texto» en un renglón")
            assertFalse(d.isLineEllipsized(0), "«$texto» sin «…»")
            assertEquals(texto.length, d.getLineEnd(0, visibleEnd = true), "«$texto» entero")
            assertTrue(d.getLineRight(0) - d.getLineLeft(0) <= nodo.size.width + 0.5f, "«$texto» dentro de su caja")
            assertTrue(nodo.boundsInRoot.right <= ancho * composeRule.density.density, "«$texto» dentro de la pantalla")
            texto
        }
    }

    /**
     * Las dos tarjetas: «Confirmar los 2» y «Revisar uno por uno» en las dos; en la segunda, el rótulo
     * largo si cabe ([largoCabe]) y si no, el corto (`botonCortoDeConfirmarLosAvisos`).
     */
    private fun losCuatroRotulos(ancho: Int, largoCabe: Boolean) {
        val rotulos = losRotulos(ancho)
        val largo = "Confirmar los 2 que Movi entendió"
        assertEquals(2, rotulos.count { it == REVISAR_UNO_POR_UNO }, rotulos.toString())
        if (largoCabe) {
            assertEquals(listOf("Confirmar los 2", largo), rotulos.filter { it.startsWith("Confirmar") }, rotulos.toString())
        } else {
            assertEquals(listOf("Confirmar los 2", "Confirmar los 2"), rotulos.filter { it.startsWith("Confirmar") }, rotulos.toString())
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 con la letra de la app`() { montar(1.12f); losCuatroRotulos(360, largoCabe = true) }

    @Test @Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 con la letra agrandada`() { montar(1.3f); losCuatroRotulos(360, largoCabe = false) }

    /** Vuelta 56 · el 130 % del teléfono por el ×1,12 de la app: 1,456 en la práctica. */
    @Test @Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 con la letra al 130 por ciento de verdad`() { montar(1.3f * 1.12f); losCuatroRotulos(360, largoCabe = false) }

    @Test @Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
    fun `a 390 con la letra de la app`() { montar(1.12f); losCuatroRotulos(390, largoCabe = true) }

    @Test @Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
    fun `a 390 con la letra agrandada`() { montar(1.3f); assertEquals(2, losRotulos(390).count { it == REVISAR_UNO_POR_UNO }) }

    /** Y cuando caben, siguen lado a lado y llenan el ancho, como en la maqueta. */
    @Test @Config(sdk = [34], qualifiers = "w600dp-h800dp-xhdpi")
    fun `con lugar van lado a lado`() {
        montar(1.12f)
        val confirmar = composeRule.onAllNodesWithText("Confirmar los 2", useUnmergedTree = true).fetchSemanticsNodes().first()
        val revisar = composeRule.onAllNodesWithText(REVISAR_UNO_POR_UNO, useUnmergedTree = true).fetchSemanticsNodes().first()
        assertEquals(confirmar.boundsInRoot.center.y, revisar.boundsInRoot.center.y, 1f)
        assertTrue(revisar.boundsInRoot.left > confirmar.boundsInRoot.right, "uno al lado del otro")
    }
}
