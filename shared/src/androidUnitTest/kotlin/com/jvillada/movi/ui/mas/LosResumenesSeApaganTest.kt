package com.jvillada.movi.ui.mas

import android.Manifest
import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.jvillada.movi.avisos.PreferenciasDeAvisos
import com.jvillada.movi.theme.MoviTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 68 · los dos resúmenes se apagan donde viven los demás avisos del teléfono
 *
 * Tú → Avisos (vuelta 73; antes Captura del banco → «Avisos de Movi»): junto a «Avisarme cuando cierre mi período» van
 * «Resumen de la mañana, a las 8:00» y «Resumen semanal de tus grupos, el domingo». Prendidos por
 * defecto (decisión del dueño, para quien ya usa Movi y para las cuentas nuevas), y cada uno se guarda
 * aparte en las preferencias de avisos del teléfono, como los otros.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class LosResumenesSeApaganTest {

    @get:Rule val composeRule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun conPermiso() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { AvisosDeEsteTelefono() }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `los dos interruptores estan y arrancan prendidos`() {
        assertTrue(PreferenciasDeAvisos.avisarResumenDeLaManana(app))
        assertTrue(PreferenciasDeAvisos.avisarResumenSemanal(app))
        montar()
        assertTrue(hay(TEXTO_INTERRUPTOR_RESUMEN_DE_LA_MANANA))
        assertTrue(hay(TEXTO_INTERRUPTOR_RESUMEN_SEMANAL))
    }

    @Test
    fun `cada uno se apaga solo y se guarda`() {
        montar()
        composeRule.onNodeWithText(TEXTO_INTERRUPTOR_RESUMEN_DE_LA_MANANA, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(PreferenciasDeAvisos.avisarResumenDeLaManana(app), "la mañana se apagó")
        assertTrue(PreferenciasDeAvisos.avisarResumenSemanal(app), "el semanal sigue prendido")

        composeRule.onNodeWithText(TEXTO_INTERRUPTOR_RESUMEN_SEMANAL, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(PreferenciasDeAvisos.avisarResumenSemanal(app))

        // Y vuelven a prenderse.
        composeRule.onNodeWithText(TEXTO_INTERRUPTOR_RESUMEN_DE_LA_MANANA, useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(true, PreferenciasDeAvisos.avisarResumenDeLaManana(app))
    }
}
