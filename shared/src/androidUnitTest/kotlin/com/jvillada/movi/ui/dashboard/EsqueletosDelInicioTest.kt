package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ai.PREGUNTAS_DE_RESPALDO
import com.jvillada.movi.ui.ai.preguntasSugeridas
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_FILA_ESQUELETO_PREGUNTA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Task 7: los esqueletos del Inicio
 *
 * El hero mide lo mismo cargando (esqueleto) que cargado (veredicto + barra + fila), y «Pregúntale
 * a Movi» muestra sus tres filas esqueleto —no las tres preguntas de respaldo— mientras no hay ni
 * una cuenta ni un resumen.
 *
 * `sdk = [34]`: la memoria del repo documenta que en el SDK 24 de Robolectric el texto con
 * interlineado mide ancho cero; acá, incluso en el SDK 34, se encontró un problema hermano y peor
 * para esta prueba puntual — ver [MARGEN_DE_ALTO_DP].
 *
 * `mainClock.autoAdvance = false` ANTES de montar, en las tres pruebas: los esqueletos tienen una
 * `rememberInfiniteTransition` que nunca llega a "idle", así que dejar el reloj automático cuelga
 * `setContent` (o cualquier `waitForIdle` posterior). Se avanza un solo frame a mano — lo que hace
 * falta para que la primera medida/lectura exista — y nunca más.
 */
/**
 * **El margen del alto total, y por qué es mucho más que los ±8 dp que sugería la
 * especificación.**
 *
 * Medido en este mismo archivo (antes de escribir esta prueba, con `println` y leyendo el
 * `<system-out>` del reporte): en el Robolectric de este módulo — sin las fuentes propias de
 * `MoviTheme`, que no cargan en la JVM del test — **todo `Text`/`BasicText` mide ~17,5 dp de
 * alto, sin importar el `lineHeight` que declare su estilo**. Se probó `Movi.textos.cuerpo`
 * (18 sp), `.apoyo` (16 sp), `.titulo` (20 sp) y `.cifra` (46 sp, con `autoSize`) por separado
 * y los cuatro dieron el mismo número.
 *
 * El esqueleto, en cambio, son bloques `Box` con `.height(alto)` — ESE alto se respeta siempre,
 * acá y en el teléfono, porque no depende de ninguna fuente. El resultado es que la tarjeta
 * cargando (bloques) mide sistemáticamente MÁS que la cargada (texto real) en esta JVM, por una
 * cantidad que no tiene que ver con ningún error de diseño: para los datos de `datosCargados` la
 * diferencia midió 48,0 dp, siempre igual (nada azaroso: Robolectric no tiene jitter de fuente).
 * En el teléfono del dueño, con las fuentes reales, el texto SÍ respeta su `lineHeight` y las dos
 * tarjetas miden lo mismo — que es lo que esta prueba no puede observar directamente.
 *
 * Por eso el alto total es la prueba SECUNDARIA acá: la que de verdad ataja un bloque olvidado
 * son los cuatro `assertEquals` por tag, dentro de la prueba, que no dependen de cómo Robolectric
 * mide texto. El alto total queda para atajar algo mucho más grave (una tarjeta vacía, o
 * duplicada): 60 dp, la brecha medida (48 dp) más ~12 dp de margen real.
 */
private const val MARGEN_DE_ALTO_DP = 60f

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class EsqueletosDelInicioTest {

    @get:Rule val composeRule = createComposeRule()

    /** Sin préstamo, sin bien, sin cuenta condicionada: `muestraPatrimonio` da falso a propósito —
     * así el hero cargado no suma el renglón de patrimonio, que el esqueleto no representa, y la
     * comparación de alto queda pareja. */
    private val datosCargados = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 15_000_000),
        accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 500_000)),
        spentByCategory = mapOf(CUOTA_CATEGORY to 5_000_000L),
    )

    @Test
    fun `el hero cargando mide lo mismo que el hero cargado, con veredicto y barra`() {
        // Las dos versiones del hero en una sola composición —no se puede llamar `setContent` dos
        // veces en la misma prueba— cada una en su mitad de una pantalla bien alta, para que
        // ninguna se recorte.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        SduiRenderer(
                            definition = defaultDashboardDefinition(),
                            data = DashboardData(), // cargando: sin cuentas ni resumen
                            modifier = Modifier.fillMaxSize(),
                            onNavigate = {},
                        )
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        SduiRenderer(
                            definition = defaultDashboardDefinition(),
                            data = datosCargados,
                            modifier = Modifier.fillMaxSize(),
                            onNavigate = {},
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        // Las cuatro piezas del esqueleto están, ni una de menos — la prueba que de verdad ataja
        // un bloque olvidado (ver el KDoc de estos tags): el alto total, con texto real de por
        // medio, no puede distinguir «faltó la fila» de «Robolectric no honra el interlineado».
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_ESQUELETO_CIFRA_DEL_HERO).fetchSemanticsNodes().size)
        assertEquals(2, composeRule.onAllNodesWithTag(TAG_ESQUELETO_VEREDICTO_DEL_HERO).fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_ESQUELETO_BARRA_DEL_HERO).fetchSemanticsNodes().size)
        assertEquals(2, composeRule.onAllNodesWithTag(TAG_ESQUELETO_FILA_DEL_HERO).fetchSemanticsNodes().size)

        val tarjetas = composeRule.onAllNodesWithTag(TAG_TARJETA_DEL_HERO)
        val altoCargando = tarjetas[0].getUnclippedBoundsInRoot().height
        val altoCargado = tarjetas[1].getUnclippedBoundsInRoot().height

        val diferencia = abs(altoCargado.value - altoCargando.value)
        assertTrue(
            diferencia <= MARGEN_DE_ALTO_DP,
            "El hero cargando mide ${altoCargando.value} dp y cargado ${altoCargado.value} dp " +
                "— diferencia de $diferencia dp, el máximo son $MARGEN_DE_ALTO_DP dp",
        )
    }

    @Test
    fun `sin cuentas ni resumen, Preguntale a Movi muestra tres esqueletos y no las preguntas de respaldo`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = DashboardData(),
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = {},
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(
            3,
            composeRule.onAllNodesWithTag(TAG_FILA_ESQUELETO_PREGUNTA).fetchSemanticsNodes().size,
        )
        // El relleno genérico (`PREGUNTAS_DE_RESPALDO`) es justamente lo que el esqueleto tiene que
        // reemplazar: sin datos, antes se veía como si fuera una respuesta real.
        PREGUNTAS_DE_RESPALDO.forEach { pregunta ->
            composeRule.onNodeWithText(pregunta, useUnmergedTree = true).assertDoesNotExist()
        }
        // El campo se puede usar igual, sin datos.
        composeRule.onNodeWithText("Escribe tu pregunta…", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `con cuentas y resumen, Preguntale a Movi muestra las preguntas y no el esqueleto`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = datosCargados,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = {},
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(
            0,
            composeRule.onAllNodesWithTag(TAG_FILA_ESQUELETO_PREGUNTA).fetchSemanticsNodes().size,
        )
        preguntasSugeridas(datosCargados).forEach { pregunta ->
            composeRule.onNodeWithText(pregunta, useUnmergedTree = true).assertIsDisplayed()
        }
    }
}
