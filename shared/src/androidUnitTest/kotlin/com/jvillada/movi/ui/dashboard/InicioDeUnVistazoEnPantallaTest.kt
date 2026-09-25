package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.ai.preguntasSugeridas
import com.jvillada.movi.ui.sdui.SduiRenderer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **El Inicio de un vistazo montado entero** (el renderer SDUI con la definición de la generación 8),
 * en un teléfono de 390 dp y en un escritorio de 1.400 dp.
 *
 * En `sdk = [34]` a propósito: en el SDK 24 de Robolectric el texto con interlineado mide ancho cero
 * (ver la memoria del repo sobre SDK 24), y estas pruebas comparan posiciones de texto en pantalla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InicioDeUnVistazoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val datos = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 22_152_488, egresos = 33_882_000),
        accounts = listOf(
            Account(id = "a1", name = "Cuenta de ahorros", type = AccountType.SAVINGS, balance = 558_350),
            Account(id = "l1", name = "Hipoteca", type = AccountType.LOAN, balance = 2_191_000_000),
        ),
        spentByCategory = mapOf(CUOTA_CATEGORY to 12_915_000L, "Comida" to 2_000_000L),
    )

    private var navegoA: Screen? = null

    private fun montar(data: DashboardData = datos) {
        navegoA = null
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = data,
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = { navegoA = it },
                    )
                }
            }
        }
    }

    private fun arriba(texto: String): Dp =
        composeRule.onNodeWithText(texto, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    /** «Tu plata» y su cifra se repiten en la tarjeta del patrimonio: el hero es el primero. */
    private fun primero(texto: String) = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).onFirst()

    private fun cuantas(texto: String): Int =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().size

    /** El veredicto es único en la pantalla: sirve para ubicar el hero. */
    private val veredicto =
        "Este período salieron \$11,7M más de los que entraron — las cuotas de crédito fueron \$12,9M"

    private fun izquierda(texto: String): Dp =
        composeRule.onNodeWithText(texto, useUnmergedTree = true).getUnclippedBoundsInRoot().left

    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `en el telefono una columna, el hero y enseguida Preguntale a Movi`() {
        montar()

        primero("\$558.350").assertIsDisplayed()
        composeRule.onNodeWithText(veredicto, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Entró", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Salió", useUnmergedTree = true).assertIsDisplayed()
        // El hero ya no lista las cuentas ni el patrimonio: eso vive en «Tu patrimonio».
        composeRule.onNodeWithText("Flujo del mes", useUnmergedTree = true).assertDoesNotExist()

        // Una columna: todo alineado a la izquierda y en el orden de la definición.
        val hero = arriba(veredicto)
        val preguntale = arriba("PREGÚNTALE A MOVI")
        val patrimonio = arriba("TU PATRIMONIO")
        assertTrue(hero < preguntale, "Pregúntale a Movi va debajo del hero")
        assertTrue(preguntale < patrimonio, "y el patrimonio más abajo")
        assertEquals(izquierda("PREGÚNTALE A MOVI"), izquierda("TU PATRIMONIO"))
        // El banner viejo no se pinta: la puerta a Movi AI está arriba.
        composeRule.onNodeWithText("Pregúntale a Movi AI", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1400dp-h1200dp-mdpi")
    fun `en escritorio dos columnas, Preguntale a Movi al lado del hero`() {
        montar()

        val heroIzq = izquierda(veredicto)
        val preguntaleIzq = izquierda("PREGÚNTALE A MOVI")
        assertTrue(preguntaleIzq.value > heroIzq.value + 300f, "Pregúntale a Movi va en la columna derecha")
        // Las dos columnas arrancan arriba, a la misma altura (±el rótulo de sección).
        assertTrue(arriba("PREGÚNTALE A MOVI") < arriba(veredicto), "la derecha no empieza debajo del hero")
        // El patrimonio también a la derecha; las categorías, a la izquierda debajo del hero.
        assertEquals(izquierda("PREGÚNTALE A MOVI"), izquierda("TU PATRIMONIO"))
        assertTrue(izquierda("EN QUÉ SE VA").value < preguntaleIzq.value)
    }

    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `las preguntas y el campo abren el chat`() {
        montar()
        val primera = preguntasSugeridas(datos).first()
        composeRule.onNodeWithText(primera, useUnmergedTree = true).performClick()
        assertEquals(Screen.AIChat(preguntaInicial = primera), navegoA)

        composeRule.onNodeWithText("Escribe tu pregunta…", useUnmergedTree = true).performClick()
        assertEquals(Screen.AIChat(), navegoA)
    }

    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `el patrimonio honesto con sus tramos`() {
        montar()
        composeRule.onNodeWithText("Tienes", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Debes", useUnmergedTree = true).assertIsDisplayed()
        primero("\$2.191M").assertIsDisplayed()
        composeRule.onNodeWithText("Deudas", useUnmergedTree = true).performClick()
        assertEquals(Screen.Credits, navegoA)
    }

    /**
     * Ola E, tarea 4: la línea del rango del período («Del 25 de agosto al 24 de septiembre · …»)
     * se volvió la puerta a «Tus períodos» — mismo criterio que «Deudas» más arriba: el dueño que
     * ya está mirando su período está a un toque de preguntarse por los anteriores. Solo existe con
     * un corte distinto de 1 ([PeriodSettings.esMesDeCalendario]); con el corte de siempre la línea
     * ni se pinta.
     */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `el rango del periodo es tocable y lleva a Tus periodos`() {
        val ajustes = PeriodSettings(cutoffDay = 25)
        val conRangoDePeriodo = datos.copy(ajustesDePeriodo = ajustes, periodoActual = PeriodoFinanciero(2026, 9))
        montar(conRangoDePeriodo)

        composeRule.onNodeWithText("Del 25 de agosto al 24 de septiembre", substring = true, useUnmergedTree = true)
            .performClick()
        assertEquals(Screen.Periodos, navegoA)
    }

    /**
     * La cifra grande entra contando, **una vez**: con el reloj quieto arranca en $0; al dejarlo
     * correr termina exacta; y si el Inicio se vuelve a montar (volver desde Movimientos), ya está
     * donde tiene que estar, sin volver a contar.
     */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `la cifra entra contando una sola vez`() {
        composeRule.mainClock.autoAdvance = false
        montar()
        composeRule.mainClock.advanceTimeByFrame()
        primero("\$0").assertIsDisplayed()
        // «$558.350» ya está en otros lados (el tramo «Tu plata» del patrimonio, una pregunta
        // sugerida), pero todavía no en el hero, que cuenta.
        val antes = cuantas("\$558.350")

        composeRule.mainClock.advanceTimeBy(DURACION_DE_LA_ENTRADA_MS.toLong() + 100L)
        assertEquals(antes + 1, cuantas("\$558.350"), "el hero terminó de contar, exacto")
        assertEquals(0, cuantas("\$0"))
        assertTrue("hero.cifra" in DashboardDataCache.entradasHechas)
    }

    @Test
    @Config(qualifiers = "w390dp-h2400dp-xhdpi")
    fun `al volver al Inicio la cifra ya esta en su lugar`() {
        DashboardDataCache.entradasHechas += "hero.cifra"
        composeRule.mainClock.autoAdvance = false
        montar()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(0, cuantas("\$0"), "sin contar de nuevo desde cero: la cifra ya está en su lugar")
    }
}
