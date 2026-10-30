package com.jvillada.movi.ui.mas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.shared.model.ReminderChannels
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.recurrentes.EstadoDeLosRecordatorios
import com.jvillada.movi.ui.recurrentes.RECORDATORIOS_APAGADOS
import com.jvillada.movi.ui.recurrentes.estadoDeLosRecordatorios
import com.jvillada.movi.ui.recurrentes.shouldShowReminderWarning
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Los avisos apagados se ven en Ajustes (vuelta de diseño 22)
 *
 * La revisión: «Avisarme antes de un vencimiento · Te avisamos 3 días antes» convivía con «Notificaciones
 * push · Activar», como si los avisos funcionaran, y lo único que decía que no iban a llegar estaba al
 * final de Plan. Ahora la fila lo dice, en el tono de aviso y con la acción de activarlos, con la MISMA
 * regla de `ReminderWarning`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LosAvisosApagadosSeVenTest {

    @get:Rule val composeRule = createComposeRule()

    private val sinCorreo = ReminderChannels(email = false)
    private val conCorreo = ReminderChannels(email = true, emailTo = "jvillad1@gmail.com")

    private var activadas = 0

    private fun montar(pushStatus: String, canales: ReminderChannels?) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    Column {
                        FilasDeAvisos(
                            pushSoportado = true,
                            pushStatus = pushStatus,
                            canales = canales,
                            diasDeAviso = 3,
                            falloElPerfil = false,
                            onActivar = { activadas++ },
                            onDesactivar = {},
                            onElegirDias = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodes(hasText(texto, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /**
     * Vuelta 31: eran dos botones seguidos —«Activar» en las notificaciones y «Activar» en esta fila—
     * para lo mismo. Queda uno. Vuelta 76: dice «Activar» a secas (la sección ya es «Notificaciones»).
     */
    @Test
    fun `sin canal activo la fila dice Apagado y hay un solo Activar avisos`() {
        montar(pushStatus = "disabled", canales = sinCorreo)

        assertTrue(hay(RECORDATORIOS_APAGADOS), "dice «Apagado: no te va a llegar»")
        assertTrue(!hay("Te avisamos 3 días antes"), "no promete un aviso que no va a llegar")
        assertEquals(1, composeRule.onAllNodes(hasClickAction() and hasText(ACTIVAR_AVISOS)).fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Activar", useUnmergedTree = true).fetchSemanticsNodes().size, "un solo «Activar»: el botón")
        composeRule.onNodeWithText(ACTIVAR_AVISOS, useUnmergedTree = true).performClick()
        assertEquals(1, activadas)
    }

    @Test
    fun `activadas dice Desactivar y ningun Activar`() {
        montar(pushStatus = "enabled", canales = sinCorreo)
        assertTrue(hay("Desactivar"))
        assertTrue(!hay(ACTIVAR_AVISOS))
    }

    @Test
    fun `con correo dice cuando avisa, como siempre`() {
        montar(pushStatus = "disabled", canales = conCorreo)
        assertTrue(hay("Te avisamos 3 días antes"))
        assertTrue(!hay(RECORDATORIOS_APAGADOS))
    }

    @Test
    fun `bloqueadas por el navegador dice Apagado, con el boton apagado`() {
        montar(pushStatus = "denied", canales = sinCorreo)
        assertTrue(hay(RECORDATORIOS_APAGADOS))
        assertTrue(hay("Bloqueadas por el navegador"))
        assertEquals(1, composeRule.onAllNodesWithText(ACTIVAR_AVISOS, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNode(hasClickAction() and hasText(ACTIVAR_AVISOS)).assertIsNotEnabled()
    }

    @Test
    fun `mientras el server no contesta no afirma que esten apagados`() {
        montar(pushStatus = "disabled", canales = null)
        assertTrue(!hay(RECORDATORIOS_APAGADOS))
        assertTrue(hay("Te avisamos 3 días antes"))
    }

    /** La fila y el aviso de Plan usan la misma regla: si una dice «apagado», la otra también. */
    @Test
    fun `la regla es la de ReminderWarning`() {
        val casos = listOf("enabled", "disabled", "denied", "unsupported").flatMap { push ->
            listOf(null, sinCorreo, conCorreo).map { push to it }
        }
        casos.forEach { (push, canales) ->
            assertEquals(
                shouldShowReminderWarning(push, hayRecordatoriosPedidos = true, canales = canales),
                estadoDeLosRecordatorios(push, canales) == EstadoDeLosRecordatorios.APAGADOS,
                "push=$push canales=$canales",
            )
        }
        assertEquals(RECORDATORIOS_APAGADOS, subtituloDeLosRecordatorios(EstadoDeLosRecordatorios.APAGADOS, 3, false))
    }
}
