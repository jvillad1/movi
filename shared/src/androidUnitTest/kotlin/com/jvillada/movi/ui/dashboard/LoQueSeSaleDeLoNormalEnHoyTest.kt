package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.EvidenciaDeAnomalia
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.ParaRevisarSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **Lo que se sale de lo normal, en «Para revisar» de Hoy** (Ola 4): el aviso con su detalle, la
 * evidencia a un toque («Ver los movimientos») y «Está bien», que lo saca en el mismo toque, se lo
 * dice al server y no vuelve aunque la lista vieja lo siga trayendo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class LoQueSeSaleDeLoNormalEnHoyTest {

    @get:Rule val composeRule = createComposeRule()

    private val duplicado = Anomalia(
        huella = "duplicado:a,b",
        tipo = TipoDeAnomalia.COBRO_DUPLICADO,
        titulo = "¿Cobro duplicado en RAPPI COLOMBIA?",
        detalle = "2 cobros de \$45.900 en Bancolombia con menos de dos días entre uno y otro.",
        evidencia = listOf(
            EvidenciaDeAnomalia("a", "2026-09-28", "RAPPI COLOMBIA", 45_900L, cuenta = "Bancolombia"),
            EvidenciaDeAnomalia("b", "2026-09-29", "RAPPI COLOMBIA", 45_900L, cuenta = "Bancolombia"),
        ),
    )

    @Test
    fun `el aviso muestra su evidencia y esta bien lo descarta en el server`() {
        val descartadas = mutableListOf<String>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun descartarAnomalia(huella: String) {
                descartadas += huella
            }
        }
        val data = DashboardData(anomalias = listOf(duplicado))
        composeRule.setContent {
            MoviTheme { ParaRevisarSection(ScreenSection(type = "ALERTS", title = "Para revisar"), data) {} }
        }
        composeRule.onNodeWithText("¿Cobro duplicado en RAPPI COLOMBIA?").assertExists()
        composeRule.onNodeWithText("28 de septiembre · RAPPI COLOMBIA · Bancolombia").assertDoesNotExist()

        composeRule.onNodeWithText("Ver los movimientos").performClick()
        composeRule.onNodeWithText("28 de septiembre · RAPPI COLOMBIA · Bancolombia").assertExists()
        composeRule.onNodeWithText("29 de septiembre · RAPPI COLOMBIA · Bancolombia").assertExists()

        composeRule.onNodeWithText("Está bien").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¿Cobro duplicado en RAPPI COLOMBIA?").assertDoesNotExist()
        assertEquals(listOf("duplicado:a,b"), descartadas)
        assertTrue("duplicado:a,b" in AnomaliasDescartadasEnLaSesion.huellas)
        // Y no vuelve con la lista vieja (la del caché del Inicio, que todavía lo trae).
        assertTrue(anomaliasVisiblesDe(data).isEmpty())
    }

    @Test
    fun `si el server no guarda el descarte el aviso vuelve`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun descartarAnomalia(huella: String) = error("sin red")
        }
        val data = DashboardData(anomalias = listOf(duplicado))
        composeRule.setContent {
            MoviTheme { ParaRevisarSection(ScreenSection(type = "ALERTS", title = "Para revisar"), data) {} }
        }
        composeRule.onNodeWithText("Está bien").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¿Cobro duplicado en RAPPI COLOMBIA?").assertExists()
        assertFalse("duplicado:a,b" in AnomaliasDescartadasEnLaSesion.huellas)
    }
}
