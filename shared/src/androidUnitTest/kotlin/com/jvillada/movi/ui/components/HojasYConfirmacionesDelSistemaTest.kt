package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.DeleteAccountSheet
import com.jvillada.movi.ui.components.ds.ROTULO_CARGANDO
import com.jvillada.movi.ui.components.ds.TAG_BOTON
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO_DE_HOJA
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import com.jvillada.movi.ui.components.ds.coloresDelBoton
import com.jvillada.movi.ui.credits.CreditBalanceSheet
import com.jvillada.movi.ui.profile.PeriodoSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta de diseño 18: hojas y confirmaciones con un solo patrón
 *
 * - **La confirmación de borrar** ([ConfirmarEnHoja], [ConfirmacionEnLinea]) pinta dos `Boton` del
 *   sistema: «Cancelar» en `Fantasma` y la acción en `Peligro` —el `sale` del tema—. Eran dos
 *   `Text` tocables de 10/14 dp a mano, con el botón que no tiene vuelta igual de liviano que el
 *   que cierra. Se mira la variante por su semántica (`VarianteDeBotonKey`), no por un color.
 * - **Las hojas abren con `EncabezadoDeHoja`**: tres de las dieciséis que escribían su título a mano
 *   (con tres variantes distintas), por su `testTag`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h2400dp")
class HojasYConfirmacionesDelSistemaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(contenido: @Composable () -> Unit) {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { contenido() } } }
        composeRule.waitForIdle()
    }

    /** Los `Boton` que hay en pantalla, con su texto y su variante. */
    private fun botones(): List<Pair<String, VarianteDeBoton?>> =
        composeRule.onAllNodesWithTag(TAG_BOTON).fetchSemanticsNodes().map { nodo ->
            val texto = nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
            texto to nodo.config.getOrNull(VarianteDeBotonKey)
        }

    private fun tocar(texto: String) {
        composeRule.onNode(hasTestTag(TAG_BOTON) and hasText(texto)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun hayEncabezadoCon(texto: String): Boolean =
        composeRule.onAllNodes(hasTestTag(TAG_ENCABEZADO_DE_HOJA) and hasAnyDescendant(hasText(texto)), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    // ── La confirmación ──────────────────────────────────────────────────────────

    @Test
    fun `confirmar en hoja pinta Cancelar en Fantasma y Borrar en Peligro`() {
        var confirmado = 0
        var cancelado = 0
        montar {
            ConfirmarEnHoja(
                pregunta = "¿Borrar el enlace?",
                detalle = "Deja de abrir para quien lo tenga.",
                textoConfirmar = "Borrar",
                ocupado = false,
                onConfirmar = { confirmado++ },
                onCancelar = { cancelado++ },
            )
        }

        assertEquals(
            listOf("Cancelar" to VarianteDeBoton.Fantasma, "Borrar" to VarianteDeBoton.Peligro),
            botones(),
            "dos botones del sistema, y nada más",
        )
        assertTrue(
            composeRule.onAllNodesWithTag(TAG_BOTONES_DE_CONFIRMAR, useUnmergedTree = true).fetchSemanticsNodes().size == 1,
            "la fila de la confirmación",
        )
        // La pregunta y su detalle son el encabezado de la hoja.
        assertTrue(hayEncabezadoCon("¿Borrar el enlace?"))
        assertTrue(hayEncabezadoCon("Deja de abrir para quien lo tenga."))

        tocar("Borrar")
        assertEquals(1, confirmado)
        tocar("Cancelar")
        assertEquals(1, cancelado)
    }

    @Test
    fun `el boton de borrar va en el tono de alerta del tema`() {
        for (colores in listOf(COLORES_CLAROS)) {
            val (fondo, texto) = coloresDelBoton(VarianteDeBoton.Peligro, colores)
            assertEquals(colores.sale, texto, "el texto de «Borrar» es el `sale` del tema")
            assertEquals(colores.lavado(colores.sale), fondo, "sobre su lavado, nunca lleno de rojo")
        }
    }

    @Test
    fun `confirmar en linea usa los mismos botones, y mientras borra no se cancela`() {
        montar {
            ConfirmacionEnLinea(
                pregunta = "¿Eliminar «Caro»?",
                detalle = "Tus movimientos no se tocan.",
                textoConfirmar = "Eliminar",
                ocupado = true,
                onConfirmar = {},
                onCancelar = {},
            )
        }
        assertEquals(listOf("Cancelar" to VarianteDeBoton.Fantasma, "Eliminar" to VarianteDeBoton.Peligro), botones())
        val nodos = composeRule.onAllNodesWithTag(TAG_BOTON).fetchSemanticsNodes()
        assertTrue(nodos[0].config.getOrNull(SemanticsProperties.Disabled) != null, "«Cancelar» apagado mientras se borra")
        assertEquals(ROTULO_CARGANDO, nodos[1].config.getOrNull(SemanticsProperties.StateDescription), "la ruedita del `Peligro`")
    }

    // ── Las hojas ────────────────────────────────────────────────────────────────

    @Test
    fun `eliminar una cuenta abre con el encabezado de hoja y sus botones son del sistema`() {
        montar {
            DeleteAccountSheet(
                accountId = "acc_1",
                accountName = "Nu",
                eventCount = 3,
                onDismiss = {},
                onDeleted = {},
            )
        }
        assertTrue(hayEncabezadoCon("Eliminar cuenta"))
        assertTrue(hayEncabezadoCon("Se borra \"Nu\" y sus 3 movimientos. Esto no se puede deshacer."))
        assertEquals(
            listOf("Cancelar" to VarianteDeBoton.Fantasma, "Eliminar cuenta" to VarianteDeBoton.Peligro),
            botones(),
            "eran dos píldoras de 48 hechas a mano",
        )
    }

    @Test
    fun `ajustar el saldo de un credito pone el nombre de titulo y la deuda en una Cifra`() {
        montar {
            CreditBalanceSheet(
                credit = CreditSummary(
                    account = Account("acc_v", "Vehículo 4083", AccountType.LOAN, balance = 3_000_000L),
                    terms = null,
                    paidPct = null,
                ),
                onDismiss = {},
                onSaved = {},
            )
        }
        assertTrue(hayEncabezadoCon("Vehículo 4083"), "el nombre es el título")
        assertTrue(hayEncabezadoCon("Ajustar saldo"), "qué es la hoja, de subtítulo")
        assertTrue(
            composeRule.onAllNodes(hasTestTag(TAG_CIFRA) and hasText("$3.000.000"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
            "la deuda en Movi es una `Cifra`, no un `Text(formatCOP(…))`",
        )
    }

    @Test
    fun `el inicio del mes abre con el encabezado de hoja`() {
        montar { PeriodoSheet(cutoffActual = 25, onDismiss = {}, onSave = { _, _ -> }) }
        assertTrue(hayEncabezadoCon("¿Qué día empieza tu período?"))
        assertEquals(
            1,
            composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_HOJA, useUnmergedTree = true).fetchSemanticsNodes().size,
            "un solo encabezado",
        )
    }
}
