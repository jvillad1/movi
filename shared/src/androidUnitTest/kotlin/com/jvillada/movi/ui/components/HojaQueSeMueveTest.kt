package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.ui.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ola 1 · «Que Movi se sienta viva»: las hojas del teléfono **entran, salen y se cierran
 * arrastrando**, y **tapan la barra de pestañas**. Todo vive en [MarcoDeHoja], así que estas
 * pruebas cubren las treinta hojas de una vez.
 *
 * Lo que se sostiene acá:
 * - arrastrar hacia abajo pasado el umbral llama a `onDismiss` **una vez**;
 * - un arrastre corto no la cierra (vuelve a su lugar);
 * - tocar el velo dos veces mientras sale no la cierra dos veces;
 * - si quien la abrió no la saca, vuelve a verse en su lugar (no queda invisible tapando todo);
 * - con una lista adentro que todavía puede subir, arrastrar mueve la lista y no la hoja;
 * - con la hoja abierta, la barra de pestañas no recibe toques.
 *
 * Las animaciones corren con el reloj de la prueba (`mainClock.autoAdvance`, lo de siempre):
 * `waitForIdle` las deja terminar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class HojaQueSeMueveTest {

    @get:Rule val composeRule = createComposeRule()

    private var cierres = 0

    private fun montar(
        seCierra: Boolean = true,
        contenido: @Composable () -> Unit = { Text("Contenido de la hoja", Modifier.height(400.dp)) },
    ) {
        cierres = 0
        composeRule.setContent {
            ConClaseDeAncho {
                var abierta by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    if (abierta) {
                        MarcoDeHoja(onDismiss = { cierres++; if (seCierra) abierta = false }) { contenido() }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun panel(): Rect = composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun hayPanel(): Boolean =
        composeRule.onAllNodes(hasTestTag(TAG_PANEL_DE_HOJA), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun raiz(): Rect = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

    @Test
    fun `entra y queda pegada abajo`() {
        montar()
        assertEquals(raiz().bottom, panel().bottom, 1f)
    }

    @Test
    fun `arrastrarla hacia abajo pasado el umbral la cierra, y llama a onDismiss una vez`() {
        montar()
        val alto = panel().height
        composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA, useUnmergedTree = true).performTouchInput {
            swipeDown(startY = 10f, endY = 10f + alto * 0.6f, durationMillis = 600)
        }
        composeRule.waitForIdle()

        assertEquals(1, cierres)
        assertTrue(!hayPanel(), "la hoja se fue")
    }

    @Test
    fun `un arrastre corto no la cierra y la hoja vuelve a su lugar`() {
        montar()
        val antes = panel()
        composeRule.onNodeWithTag(TAG_PANEL_DE_HOJA, useUnmergedTree = true).performTouchInput {
            swipeDown(startY = 10f, endY = 10f + antes.height * 0.15f, durationMillis = 800)
        }
        composeRule.waitForIdle()

        assertEquals(0, cierres)
        assertEquals(antes.top, panel().top, 1f)
    }

    @Test
    fun `tocar el velo dos veces mientras sale la cierra una sola vez`() {
        montar()
        composeRule.onRoot().performTouchInput {
            click(Offset(centerX, 20f))
            click(Offset(centerX, 30f))
        }
        composeRule.waitForIdle()

        assertEquals(1, cierres)
    }

    @Test
    fun `la X la cierra animada, una vez`() {
        montar()
        val x = composeRule.onNodeWithContentDescription("Cerrar", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        composeRule.onRoot().performTouchInput { click(x.center) }
        composeRule.waitForIdle()

        assertEquals(1, cierres)
        assertTrue(!hayPanel())
    }

    @Test
    fun `si quien la abrio no la saca, vuelve a verse en su lugar`() {
        montar(seCierra = false)
        val antes = panel()
        composeRule.onRoot().performTouchInput { click(Offset(centerX, 20f)) }
        composeRule.waitForIdle()

        assertEquals(1, cierres)
        assertEquals(antes.top, panel().top, 1f)
        // Y se puede volver a cerrar: el «cerrando» no quedó prendido.
        composeRule.onRoot().performTouchInput { click(Offset(centerX, 20f)) }
        composeRule.waitForIdle()
        assertEquals(2, cierres)
    }

    @Test
    fun `con una lista adentro que puede subir, arrastrar mueve la lista y no la hoja`() {
        montar(contenido = {
            val lista = rememberLazyListState(initialFirstVisibleItemIndex = 30)
            LazyColumn(state = lista, modifier = Modifier.height(400.dp).testTag("lista")) {
                items(60) { Text("Fila $it", Modifier.height(40.dp)) }
            }
        })
        val antes = panel()
        composeRule.onNodeWithTag("lista", useUnmergedTree = true).performTouchInput {
            swipeDown(startY = 10f, endY = 10f + antes.height * 0.6f, durationMillis = 600)
        }
        composeRule.waitForIdle()

        assertEquals(0, cierres, "la lista se quedó con el gesto")
        assertEquals(antes.top, panel().top, 1f)
    }

    // ── La hoja tapa la barra de pestañas ─────────────────────────────────────

    private var pestanaElegida: NavTab? = null

    private fun montarEnLaCascara(dismissEnabled: Boolean = true) {
        pestanaElegida = null
        cierres = 0
        composeRule.setContent {
            ConClaseDeAncho {
                var abierta by remember { mutableStateOf(true) }
                EsqueletoDeLaCascara(
                    pantalla = Screen.Dashboard,
                    activeTab = NavTab.HOY,
                    conNavegacion = true,
                    onTabSelected = { pestanaElegida = it },
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    Box(Modifier.fillMaxSize().testTag("contenido"))
                    if (abierta) {
                        MarcoDeHoja(onDismiss = { cierres++; abierta = false }, dismissEnabled = dismissEnabled) {
                            Text("Hoja", Modifier.height(120.dp))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `guardando, con el velo apagado, la barra de pestanas tampoco recibe toques`() {
        montarEnLaCascara(dismissEnabled = false)
        val plan = composeRule.onAllNodesWithText("Plan", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        val hoy = composeRule.onAllNodesWithText("Inicio", useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        composeRule.onRoot().performTouchInput {
            click(plan.center)
            click(hoy.center)
            click(Offset(centerX, 40f))
        }
        composeRule.waitForIdle()

        assertNull(pestanaElegida, "ni la barra ni el velo hacen nada mientras se guarda")
        assertEquals(0, cierres)
    }

    @Test
    fun `con una hoja abierta la barra de pestanas no recibe toques`() {
        pestanaElegida = null
        cierres = 0
        composeRule.setContent {
            ConClaseDeAncho {
                var abierta by remember { mutableStateOf(true) }
                EsqueletoDeLaCascara(
                    pantalla = Screen.Dashboard,
                    activeTab = NavTab.HOY,
                    conNavegacion = true,
                    onTabSelected = { pestanaElegida = it },
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    Box(Modifier.fillMaxSize().testTag("contenido"))
                    if (abierta) {
                        MarcoDeHoja(onDismiss = { cierres++; abierta = false }) { Text("Hoja", Modifier.height(400.dp)) }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // La hoja llega al borde de abajo de la ventana: por encima de la barra, no arriba de ella.
        assertEquals(raiz().bottom, panel().bottom, 1f)
        val plan = composeRule.onAllNodesWithText("Plan", useUnmergedTree = true).fetchSemanticsNodes()
            .first().boundsInRoot
        assertTrue(plan.top >= panel().top, "la pestaña Plan queda debajo de la hoja")

        // Un toque donde está la pestaña cae en la hoja, no en la barra.
        composeRule.onRoot().performTouchInput { click(plan.center) }
        composeRule.waitForIdle()
        assertNull(pestanaElegida, "con la hoja abierta la barra no navega")

        // Y un toque arriba, sobre la barra de antes no: sobre el velo, que cierra la hoja.
        composeRule.onRoot().performTouchInput { click(Offset(centerX, 40f)) }
        composeRule.waitForIdle()
        assertEquals(1, cierres)
        assertNull(pestanaElegida)
    }
}
