package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
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
 * # El enlace del candidato lejano cabe entero (revisión de la vuelta 51)
 *
 * A 390 dp el Carro decía «Hay un movimiento de $260.000 ·» y la pregunta se perdía. Ahora es «¿Es el
 * de $260.000?», y se mide como la pastilla de vencimiento: el motor de texto real, 360 dp y la letra
 * de la app (×1,12), debajo de la fila del Carro con su «Anotar pago».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ElEnlaceDelCandidatoCabeTest {

    @get:Rule val composeRule = createComposeRule()

    private val tanqueo = FinancialEvent(
        id = "ev-tanqueo", accountId = "acc", type = TransactionType.EXPENSE, amount = 260_000L,
        category = "Transporte", description = "Terpel", timestamp = 1_793_000_000_000L,
    )
    private val carro = PagoDelPeriodo(
        ruleId = "rr_carro", nombre = "Carro", monto = 9_000L, pagado = false, diasParaVencer = -3,
        vence = "2026-10-25", periodoDelSello = "2026-10", categoria = "Transporte", candidatos = listOf(tanqueo),
    )

    private fun disposicion(texto: String): Pair<TextLayoutResult, Float> {
        val nodo = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().single()
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single() to nodo.boundsInRoot.right
    }

    /**
     * Revisión de la 136 (I1, M5): la pregunta va en la fila solo con un candidato **cerca** del monto, y cabe
     * entera al lado de la cifra más ancha que tiene Pagos («unos $4.101.123»).
     */
    @Test
    fun `Es el de 4 millones cabe entero al lado de unos 4101123 a 360 con la letra 1,12`() {
        val pago4M = tanqueo.copy(id = "ev-4m", amount = 4_000_000L)
        val cuota = PagoDelPeriodo(
            ruleId = "credit_acc_veh", nombre = "Cuota Vehículo 8761", monto = 4_101_123L, pagado = false, diasParaVencer = -1,
            vence = "2026-11-05", periodoDelSello = "2026-11", candidatos = listOf(pago4M),
            cuotaQueCambia = com.jvillada.movi.shared.model.LaCuotaQueCambia(),
        )
        val lo = loDeLaFactura(cuota, emptySet(), false, emptyMap(), emptyMap(), emptyMap(), enElTotal = true)
        montar { FilaDeFactura(pago = cuota, estado = lo.estado, extracto = null, onAbrir = {}) }

        val texto = "¿Es el de \$4.000.000?"
        assertEquals(texto, lo.estado.texto)
        val (enlace, derecha) = disposicion(texto)
        assertEquals(1, enlace.lineCount, "en un renglón")
        assertFalse(enlace.isLineEllipsized(0), "sin «…»")
        assertEquals(texto.length, enlace.getLineEnd(0, visibleEnd = true), "entero, con la pregunta")
        assertTrue(derecha <= 360f * composeRule.density.density, "dentro de la pantalla")
    }

    /** I1: el Carro de $9.000 con un tanqueo de $260.000 sigue viéndose vencido; el candidato va en la hoja, entero. */
    @Test
    fun `con un candidato lejano la fila dice que vencio y la hoja lo pregunta entero`() {
        montar {
            SeccionChecklistDelPeriodo(
                checklist = listOf(carro), cargando = false, pudoLeer = true, marcando = emptySet(),
                onConfirmar = { _, _ -> }, onNoFueEste = { _, _ -> }, onAnotarMovimiento = {}, onQuitarLaMarca = {},
                onReintentar = {},
            )
        }
        assertTrue(composeRule.onAllNodesWithText("Venció hace 3 días", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("¿Es el de \$260.000?", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "no en la fila")
        composeRule.abrirLaFactura("Carro")
        composeRule.waitForIdle()

        val texto = "¿Es el de \$260.000?"
        assertEquals(texto, enlaceDeLaPropuestaLejana(tanqueo))
        val (enlace, derecha) = disposicion(texto)
        assertEquals(1, enlace.lineCount, "en un renglón")
        assertFalse(enlace.isLineEllipsized(0), "sin «…»")
        assertEquals(texto.length, enlace.getLineEnd(0, visibleEnd = true), "entero, con la pregunta")
        assertTrue(derecha <= 360f * composeRule.density.density, "dentro de la pantalla")
    }

    private fun montar(contenido: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { contenido() } }
            }
        }
        composeRule.waitForIdle()
    }
}
