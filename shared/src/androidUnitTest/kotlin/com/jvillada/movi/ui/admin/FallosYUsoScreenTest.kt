package com.jvillada.movi.ui.admin

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jvillada.movi.shared.model.ActivosDelDia
import com.jvillada.movi.shared.model.AdopcionDeFuncion
import com.jvillada.movi.shared.model.CohorteSemanal
import com.jvillada.movi.shared.model.Conteo
import com.jvillada.movi.shared.model.EventoDelRecorrido
import com.jvillada.movi.shared.model.FalloDeLaApp
import com.jvillada.movi.shared.model.GrupoDeFallos
import com.jvillada.movi.shared.model.ResumenDeFallos
import com.jvillada.movi.shared.model.ResumenDeUso
import com.jvillada.movi.shared.model.SesionDeUso
import com.jvillada.movi.shared.model.UsuarioConUso
import com.jvillada.movi.theme.MoviTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Vuelta 100 · «Fallos y uso»: las pestañas, el detalle de un fallo y el visor del recorrido. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class FallosYUsoScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val ejemplo = FalloDeLaApp(
        id = "f1", plataforma = "android", version = "1.90", tipo = "IllegalStateException", mensaje = "Se cayó",
        stack = "java.lang.IllegalStateException\n\tat com.jvillada.movi.sms.SmsSyncWorker.doWork(SmsSyncWorker.kt:43)",
        componente = "SmsSyncWorker", ocurridoEn = 1_800_000_000_000L,
    )

    private val eventos = listOf(
        EventoDelRecorrido(1, 1_800_000_000_000L, "SISTEMA", "SESION_EMPEZADA", "", mapOf("origen" to "FRIO")),
        EventoDelRecorrido(2, 1_800_000_001_234L, "PANTALLA", "PANTALLA", "PrimerUso(paso 1)", mapOf("pantalla" to "PrimerUso")),
        EventoDelRecorrido(3, 1_800_000_002_000L, "ACCION", "TOQUE", "PrimerUso(paso 1)", mapOf("accion" to "SIGUIENTE")),
    )

    private fun montar() {
        ObservabilidadParaAdmin.sustitutoDePrueba = object : ObservabilidadParaAdmin {
            override suspend fun fallos() = ResumenDeFallos(
                grupos = listOf(GrupoDeFallos("IllegalStateException · com.jvillada.movi.sms.SmsSyncWorker.doWork(SmsSyncWorker.kt)", "IllegalStateException", "SmsSyncWorker", listOf("android"), 3, 2, listOf("1.90"), 1_800_000_000_000L, ejemplo)),
            )
            override suspend fun uso() = ResumenDeUso(
                activosHoy = 4, activos7 = 9, activos30 = 20,
                activosPorDia = listOf(ActivosDelDia("2026-11-01", 4)),
                embudo = listOf(Conteo("BIENVENIDA", 0, 10), Conteo("PERIODO", 0, 7)),
                adopcion = listOf(AdopcionDeFuncion("MOVI_AI", 5, 25)),
                cohortes = listOf(CohorteSemanal("2026-10-19", 6, 50, 33)),
                sesionesPorUsuario = 2.5, duracionMedianaSegundos = 200,
            )
            override suspend fun usuarios() = listOf(UsuarioConUso("u1", "papa@movi.test", 2, 30, 1_800_000_000_000L, listOf("android")))
            override suspend fun sesiones(usuarioId: String) = listOf(SesionDeUso("s1", 1_800_000_000_000L, 1_800_000_002_000L, 2, "android", "1.90", 3))
            override suspend fun eventos(usuarioId: String, sesionId: String) = eventos
        }
        composeRule.setContent { MoviTheme { FallosYUsoScreen(onNavigate = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun losFallosSeAgrupanYSeAbreElDetalle() {
        montar()
        composeRule.onNodeWithText("IllegalStateException · SmsSyncWorker").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Se cayó").assertExists()
    }

    @Test
    fun elUsoMuestraActivosEmbudoAdopcionYCohortes() {
        montar()
        composeRule.onNodeWithText("Uso").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Últimos 30 días").assertExists()
        composeRule.onNodeWithText("Movi AI").performScrollTo().assertExists()
        composeRule.onNodeWithText("S1 50 % · S2 33 % · S4 — · S8 —").performScrollTo().assertExists()
    }

    @Test
    fun elVisorLlegaALaLineaDeTiempoYFiltra() {
        montar()
        composeRule.onNodeWithText("Recorridos").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("papa@movi.test").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 s · android 1.90 · 3 eventos").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3. TOQUE").assertExists()
        composeRule.onNodeWithText("Acciones").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1. SESION_EMPEZADA").assertDoesNotExist()
        composeRule.onNodeWithText("3. TOQUE").assertExists()
    }

    @Test
    fun laPestanaDelServidorTieneSuLugar() {
        montar()
        composeRule.onNodeWithText("Servidor").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Todavía no hay datos del servidor").assertExists()
    }

    @Test
    fun filtrarYBuscarEnElRecorrido() {
        assertEquals(listOf(3), filtrarElRecorrido(eventos, "ACCION", "").map { it.secuencia })
        assertEquals(listOf(1), filtrarElRecorrido(eventos, null, "sesion").map { it.secuencia })
        assertEquals("2 min 5 s", duracion(125))
        assertEquals(true, detalleDelEvento(eventos[2]).endsWith("ACCION · PrimerUso(paso 1) · accion=SIGUIENTE"))
        assertEquals(true, Regex("\\d\\d:\\d\\d:\\d\\d\\.234").containsMatchIn(detalleDelEvento(eventos[1])))
    }
}
