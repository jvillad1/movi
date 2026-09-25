package com.jvillada.movi.ui.periodos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatMoneyCompact
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Tus períodos», ya montada
 *
 * Ola E, tarea 4. Lo que no cubre [com.jvillada.movi.ui.LaFormaRecordadaEnPantallaTest] (el
 * esqueleto con la forma de la última carga, sin salto ±8 dp): el orden, las marcas, las cifras de
 * cada fila, el error con reintento y que tocar una fila navega al detalle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class PeriodosScreenTest {

    @get:Rule val composeRule = createComposeRule()

    /** El en curso (con marca «En curso»), uno con inicio propio («Empezó el 26») y uno sin flujo. */
    private val enCurso = ResumenDePeriodo(
        id = "2026-09", nombre = "Septiembre 2026", desde = "2026-08-25", hasta = "2026-09-24",
        enCurso = true, entradas = 500_000L, salidas = 300_000L, movimientos = 6,
    )
    private val conInicioPropio = ResumenDePeriodo(
        id = "2026-08", nombre = "Agosto 2026", desde = "2026-07-26", hasta = "2026-08-24",
        inicioPropio = true, entradas = 400_000L, salidas = 450_000L, movimientos = 5,
    )
    private val sinFlujo = ResumenDePeriodo(
        id = "2026-07", nombre = "Julio 2026", desde = "2026-06-26", hasta = "2026-07-25",
        entradas = 0L, salidas = 0L, movimientos = 0,
    )

    private val periodos = listOf(enCurso, conInicioPropio, sinFlujo)

    private val navegado = mutableListOf<Screen>()

    private inner class ConPeriodos(
        private val periodos: List<ResumenDePeriodo>? = null,
        private val falla: ApiException? = null,
    ) : RepositorioDePrueba() {
        override suspend fun getPeriodos(): List<ResumenDePeriodo> {
            falla?.let { throw it }
            return periodos ?: emptyList()
        }
    }

    private fun montarConRepo(repo: RepositorioDePrueba) {
        navegado.clear()
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PeriodosScreen(onNavigate = { navegado += it }) } }
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto, substring = true) }
    }

    private fun arriba(texto: String) =
        composeRule.onNodeWithText(texto, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    fun `la lista pinta cada periodo con su nombre, rango, marcas y cifras, del en curso al mas viejo`() {
        montarConRepo(ConPeriodos(periodos))
        esperarTexto("Septiembre 2026")

        // Nombre, rango legible y marca de cada fila.
        assertTrue(hay("Septiembre 2026"))
        assertTrue(hay("Del 25 de agosto al 24 de septiembre"))
        assertTrue(hay("En curso"))
        assertTrue(hay("Agosto 2026"))
        assertTrue(hay("Del 26 de julio al 24 de agosto"))
        assertTrue(hay("Empezó el 26"))
        assertTrue(hay("Julio 2026"))
        assertTrue(hay("Del 26 de junio al 25 de julio"))

        // Cifras: entró/salió/te quedó, con las mismas funciones que la pantalla usa.
        assertTrue(hay(formatMoneyCompact(enCurso.entradas)))
        assertTrue(hay(formatMoneyCompact(enCurso.salidas)))
        assertTrue(hay("+" + formatMoneyCompact(enCurso.entradas - enCurso.salidas)))
        assertTrue(hay(formatMoneyCompact(conInicioPropio.entradas)))
        assertTrue(hay(formatMoneyCompact(conInicioPropio.salidas)))
        assertTrue(hay("−" + formatMoneyCompact(conInicioPropio.salidas - conInicioPropio.entradas)))

        // El período sin flujo no dice «$0»: lo dice con palabras.
        assertTrue(hay("Sin movimientos de flujo"))

        // Orden: del en curso al más viejo, de arriba hacia abajo.
        val topEnCurso = arriba("Septiembre 2026")
        val topInicioPropio = arriba("Agosto 2026")
        val topSinFlujo = arriba("Julio 2026")
        assertTrue(topEnCurso < topInicioPropio, "septiembre (en curso) va arriba de agosto")
        assertTrue(topInicioPropio < topSinFlujo, "agosto va arriba de julio")
    }

    @Test
    fun `un periodo sin marcas no dice ni En curso ni Empezo el`() {
        montarConRepo(ConPeriodos(listOf(sinFlujo)))
        esperarTexto("Julio 2026")

        assertTrue(!hay("En curso"))
        assertTrue(!hay("Empezó el", substring = true))
    }

    @Test
    fun `una lectura que falla dice que no se pudo, y Reintentar la trae`() {
        var falla = true
        montarConRepo(object : RepositorioDePrueba() {
            override suspend fun getPeriodos(): List<ResumenDePeriodo> {
                if (falla) error("sin red")
                return periodos
            }
        })
        esperarTexto("No pudimos cargar tus períodos")
        assertTrue(!hay("Septiembre 2026"))

        falla = false
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText("Reintentar")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)

        esperarTexto("Septiembre 2026")
        assertTrue(!hay("No pudimos cargar tus períodos"))
    }

    @Test
    fun `una lista vacia enseña, en vez de quedar en blanco`() {
        montarConRepo(ConPeriodos(emptyList()))
        esperarTexto("Todavía no hay períodos")
    }

    @Test
    fun `tocar una fila navega al detalle de ese periodo`() {
        montarConRepo(ConPeriodos(periodos))
        esperarTexto("Septiembre 2026")

        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText("Agosto 2026")), useUnmergedTree = true)
            .performClick()

        assertEquals(Screen.DetalleDePeriodo(conInicioPropio.id), navegado.single())
    }
}
