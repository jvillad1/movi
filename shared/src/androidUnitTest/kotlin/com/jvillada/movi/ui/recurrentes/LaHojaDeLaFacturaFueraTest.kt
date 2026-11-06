package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Re-revisión de la vuelta 136 (4) · la hoja de la factura, fuera de la lista, sin colgarse ni cerrarse sola
 *
 * - Si la sección que la armó sale de la composición (cambia el período a la vista, por ejemplo), la hoja no queda
 *   pintada con lo que sabía: se limpia. Sin el `DisposableEffect`, seguía en pantalla.
 * - Una rotación (estado guardado y restaurado) no la cierra: la clave vuelve con `rememberSaveable`. Sin él, se
 *   cerraba.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaHojaDeLaFacturaFueraTest {

    @get:Rule val composeRule = createComposeRule()

    private val celular = PagoDelPeriodo(
        ruleId = "rr_celular", nombre = "Celular", monto = 53_077, pagado = false, diasParaVencer = 16, vence = "2026-11-22",
    )

    private fun hojas() = composeRule.onAllNodesWithTag(TAG_HOJA_DE_LA_FACTURA, useUnmergedTree = true).fetchSemanticsNodes().size

    @androidx.compose.runtime.Composable
    private fun Seccion(fuera: HojaDeLaFacturaFuera) {
        SeccionChecklistDelPeriodo(
            checklist = listOf(celular), cargando = false, pudoLeer = true, marcando = emptySet(),
            onConfirmar = { _, _ -> }, onNoFueEste = { _, _ -> }, onAnotarMovimiento = {}, onQuitarLaMarca = {},
            onReintentar = {}, hojaFuera = fuera,
        )
    }

    @Test
    fun `si la seccion sale de la composicion, su hoja no queda colgada`() {
        val fuera = HojaDeLaFacturaFuera()
        var conLaSeccion by mutableStateOf(true)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    if (conLaSeccion) Seccion(fuera)
                    fuera.Pintar()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.abrirLaFactura("Celular")
        composeRule.waitForIdle()
        assertEquals(1, hojas(), "la hoja se pinta afuera")

        conLaSeccion = false
        composeRule.waitForIdle()
        assertEquals(0, hojas(), "sin la sección que la armó, no se pinta")
    }

    @Test
    fun `una rotacion no cierra la hoja`() {
        val restaurador = StateRestorationTester(composeRule)
        restaurador.setContent {
            // Como el estado del tablero: se recrea al rotar (no es saveable).
            val fuera = remember { HojaDeLaFacturaFuera() }
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    Seccion(fuera)
                    fuera.Pintar()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.abrirLaFactura("Celular")
        composeRule.waitForIdle()
        assertEquals(1, hojas())

        restaurador.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertEquals(1, hojas(), "la hoja sigue abierta después de rotar")
    }
}
