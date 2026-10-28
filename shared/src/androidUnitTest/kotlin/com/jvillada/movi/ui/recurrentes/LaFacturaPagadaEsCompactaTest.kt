package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 48 · una factura pagada no ocupa lugar
 *
 * La revisión de la 45 (r45-03): una factura pagada medía más que una pendiente, con «Ver movimiento» y
 * «Quitar la marca» como dos botones con borde apilados; la lista se llenaba de botones de cosas
 * resueltas. Ahora la pagada es un renglón —ícono, nombre, «Pagado ✓ · $1.800.000 · 27 oct»—, tocarla
 * abre el movimiento, y «Quitar la marca» (o «No fue este», solo en lo que Movi emparejó sola) va en su
 * «⋯».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class LaFacturaPagadaEsCompactaTest {

    @get:Rule val composeRule = createComposeRule()

    private val pendiente = PagoDelPeriodo(
        ruleId = "rr_internet", nombre = "Internet", monto = 145_000, pagado = false, diasParaVencer = 5,
        vence = "2026-11-03", periodoDelSello = null,
    )
    private val arriendo = PagoDelPeriodo(
        ruleId = "rr_arriendo", nombre = "Arriendo", monto = 1_800_000, pagado = true, diasParaVencer = -23,
        vence = "2026-10-05", periodoDelSello = "2026-10", eventId = "ev_pago", pagadoEl = "2026-10-27",
        montoPagado = 1_800_000,
    )
    private val mercado = PagoDelPeriodo(
        ruleId = "rr_mercado", nombre = "Mercado", monto = 400_000, pagado = true, diasParaVencer = -3,
        vence = "2026-10-25", periodoDelSello = "2026-10", eventId = "ev_mercado", pagadoEl = "2026-10-24",
        montoPagado = 400_000, automatica = true, derivado = true,
    )

    private val vistos = mutableListOf<String>()
    private val quitadas = mutableListOf<String>()
    private val rechazados = mutableListOf<Pair<String, String>>()

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SeccionChecklistDelPeriodo(
                        checklist = listOf(pendiente, arriendo, mercado),
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { p, id -> rechazados += p.ruleId to id },
                        onAnotarMovimiento = {},
                        onQuitarLaMarca = { quitadas += it.ruleId },
                        onReintentar = {},
                        onAbrir = {},
                        onVerMovimiento = { vistos += it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun fila(nombre: String) =
        composeRule.onNode(hasTestTag(TAG_FILA_DE_FACTURA) and hasAnyDescendant(hasText(nombre)), useUnmergedTree = true)

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(texto)), useUnmergedTree = true)
            .onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `la pagada es un renglon, sin botones, y no mide mas que una pendiente`() {
        montar()

        assertTrue(hay(" · \$1.800.000 · 27 oct"), "«Pagado ✓ · \$1.800.000 · 27 oct»")
        assertEquals(
            2,
            composeRule.onAllNodes(hasText("Pagado") and hasAnyAncestor(hasTestTag(TAG_FILA_PAGADA)), useUnmergedTree = true)
                .fetchSemanticsNodes().size,
            "las dos pagadas dicen «Pagado ✓» en su renglón",
        )
        for (boton in listOf(ETIQUETA_VER_MOVIMIENTO, ETIQUETA_QUITAR_LA_MARCA, ETIQUETA_NO_FUE_ESTE)) {
            assertTrue(!hay(boton), "«$boton» ya no es un botón de la fila")
        }

        val altoPendiente = fila("Internet").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val altoPagada = fila("Arriendo").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue(altoPagada <= altoPendiente, "la pagada ($altoPagada) no mide más que la pendiente ($altoPendiente)")
    }

    @Test
    fun `tocar la pagada abre su movimiento`() {
        montar()

        tocar(" · \$1.800.000 · 27 oct")

        assertEquals(listOf("ev_pago"), vistos)
    }

    @Test
    fun `quitar la marca esta en el menu de la fila`() {
        montar()

        composeRule.onNodeWithContentDescription(descripcionDelMenuDeLaFila("Arriendo")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(!hay(ETIQUETA_NO_FUE_ESTE), "lo que selló el dueño no se discute")
        tocar(ETIQUETA_QUITAR_LA_MARCA)

        assertEquals(listOf("rr_arriendo"), quitadas)
        assertTrue(vistos.isEmpty(), "abrir el menú no abre el movimiento")
    }

    @Test
    fun `no fue este, solo en lo que Movi emparejo sola, tambien en el menu`() {
        montar()

        composeRule.onNodeWithContentDescription(descripcionDelMenuDeLaFila("Mercado")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay("Movi lo emparejó"), "el menú dice de dónde salió el «Pagado»")
        assertTrue(!hay(ETIQUETA_QUITAR_LA_MARCA))
        tocar(ETIQUETA_NO_FUE_ESTE)

        assertEquals(listOf("rr_mercado" to "ev_mercado"), rechazados)
    }
}
