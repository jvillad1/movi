package com.jvillada.movi.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.CorridaDeJob
import com.jvillada.movi.shared.model.IaDelDia
import com.jvillada.movi.shared.model.JobsDelServidor
import com.jvillada.movi.shared.model.PanelDelServidor
import com.jvillada.movi.shared.model.PuntoDelServidor
import com.jvillada.movi.shared.model.ResumenDelServidor
import com.jvillada.movi.shared.model.RutaMedida
import com.jvillada.movi.shared.model.SaludDelServidor
import com.jvillada.movi.shared.model.ServicioMedido
import com.jvillada.movi.shared.model.ServiciosDelServidor
import com.jvillada.movi.shared.model.VariableDeEntorno
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vuelta 102 · la pestaña «Servidor» de «Fallos y uso» (la pantalla es de la vuelta 100) pinta lo que contesta el server:
 * los pedidos, la ruta más lenta, un servicio con su error, un job que falló y la variable que falta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class PestanaServidorEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() {
        Repositories.sustitutoDePrueba = null
        ObservabilidadParaAdmin.sustitutoDePrueba = null
    }

    /** Las otras pestañas no tocan la red: la pantalla de la vuelta 100 abre en «Fallos». */
    private val sinObservabilidad = object : ObservabilidadParaAdmin {
        override suspend fun fallos() = error("sin red")
        override suspend fun uso() = error("sin red")
        override suspend fun usuarios() = error("sin red")
        override suspend fun sesiones(usuarioId: String) = error("sin red")
        override suspend fun eventos(usuarioId: String, sesionId: String) = error("sin red")
    }

    private fun montarEnServidor() {
        Repositories.sustitutoDePrueba = repo
        ObservabilidadParaAdmin.sustitutoDePrueba = sinObservabilidad
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { FallosYUsoScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Servidor").performClick()
    }

    private var pedidos = mutableListOf<Pair<Int, Int>>()

    private val panel = PanelDelServidor(
        resumen = ResumenDelServidor(
            desde = 0, hasta = 24 * 3_600_000L, horas = 24, pedidos = 1_240, errores4xx = 12, errores5xx = 31, p95Ms = 320,
            serie = (0 until 24).map { PuntoDelServidor(it * 3_600_000L, if (it == 20) 300 else 40, errores5xx = if (it == 20) 31 else 0) },
            masLentas = listOf(RutaMedida("GET", "/api/patrimonio/historia", 18, p95Ms = 2_000, maximoMs = 2_400)),
            conMas5xx = listOf(RutaMedida("POST", "/api/ai/chat", 40, errores5xx = 31, p95Ms = 7_500, maximoMs = 9_000)),
            usuariosPicoPorMinuto = 2,
        ),
        servicios = ServiciosDelServidor(
            desde = 0, hasta = 0,
            servicios = listOf(ServicioMedido("anthropic", "claude-haiku-4-5-20251001", llamadas = 40, sinCredito = 31, p95Ms = 1_800)),
            iaPorDia = listOf(IaDelDia("2026-11-01", llamadas = 40, errores = 31, fichasEntrada = 90_000, fichasSalida = 6_000, usuarios = 2)),
        ),
        jobs = JobsDelServidor(
            ultimas = listOf(CorridaDeJob("cierres", inicio = 0, duracionMs = 120, bien = false, error = "IllegalStateException: sin red")),
            fallidas = listOf(CorridaDeJob("cierres", inicio = 0, duracionMs = 120, bien = false, error = "IllegalStateException: sin red")),
        ),
        salud = SaludDelServidor(
            baseOk = true, baseMs = 3, version = "5e65e3aa0000", uptimeMs = 3_600_000,
            variables = listOf(VariableDeEntorno("JWT_SECRET", true), VariableDeEntorno("RESEND_API_KEY", false)),
        ),
    )

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getPanelDelServidor(horas: Int, dias: Int): PanelDelServidor {
            pedidos += horas to dias
            return panel
        }
    }

    private fun verQue(texto: String) {
        composeRule.onAllNodes(hasScrollAction()).fetchSemanticsNodes().let { lista ->
            if (lista.isNotEmpty()) composeRule.onAllNodes(hasScrollAction())[lista.size - 1].performScrollToNode(hasText(texto, substring = true))
        }
        assertTrue(
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "falta «$texto»",
        )
    }

    @Test
    fun `la pestana Servidor pinta lo que contesto el server`() {
        montarEnServidor()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("1.240 pedidos", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf(24 to 1), pedidos, "abre con las últimas 24 horas")
        verQue("2,5 % con 5xx")
        verQue("GET /api/patrimonio/historia")
        verQue("POST /api/ai/chat")
        verQue("31 sin crédito")
        verQue("48.000 por usuario")
        verQue("IllegalStateException: sin red")
        verQue("Faltan: RESEND_API_KEY")
    }

    @Test
    fun `cambiar a 7 dias vuelve a pedir con el lapso nuevo`() {
        montarEnServidor()
        composeRule.waitUntil(5_000) { pedidos.isNotEmpty() }
        composeRule.onNodeWithText("7 días").performClick()
        composeRule.waitUntil(5_000) { pedidos.size == 2 }
        assertEquals(168 to 7, pedidos.last())
    }
}
