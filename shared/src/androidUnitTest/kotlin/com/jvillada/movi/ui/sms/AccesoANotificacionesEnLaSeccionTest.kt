package com.jvillada.movi.ui.sms

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.jvillada.movi.theme.MoviTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **La tarjeta que explica el acceso a las notificaciones, y no miente sobre su alcance.**
 *
 * Este permiso es el más caro que Movi pide: quien lo concede le está dando a la app la capacidad
 * técnica de leer TODAS las notificaciones del teléfono. Que Movi solo mire las de una lista corta
 * es una decisión de su código, no un límite del sistema, así que la pantalla tiene que decirlo con
 * esas palabras — y tiene que ofrecer el camino, porque no hay diálogo que la app pueda lanzar: el
 * interruptor vive en una pantalla de ajustes del sistema.
 *
 * Lo que se afirma acá es lo que una función pura no alcanza a probar: que al montar la sección el
 * estado real del acceso se lee del sistema y se pinta, y que el botón aparece solo cuando falta.
 */
@RunWith(RobolectricTestRunner::class)
// Alta: la sección tiene cuatro tarjetas y esta es la segunda.
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class AccesoANotificacionesEnLaSeccionTest {

    @get:Rule val composeRule = createComposeRule()

    private val contexto: Context get() = ApplicationProvider.getApplicationContext()

    private fun concederAcceso() {
        Settings.Secure.putString(
            contexto.contentResolver,
            "enabled_notification_listeners",
            "${contexto.packageName}/com.jvillada.movi.notificaciones.EscuchaDeNotificaciones",
        )
    }

    private fun montar() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SmsSensorSetupSection(onSynced = {})
                }
            }
        }
    }

    private fun cuantos(texto: String): Int =
        composeRule.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().size

    @Test
    fun `sin acceso concedido la tarjeta lo dice y ofrece el camino a los ajustes`() {
        montar()

        assertTrue("Falta la tarjeta del acceso a notificaciones", cuantos("Acceso a notificaciones") > 0)
        assertTrue(
            "Falta el botón que lleva a la pantalla del sistema — sin él no hay forma de conceder esto desde la app",
            cuantos("Dar acceso a las notificaciones") > 0,
        )
        assertTrue("No se explica para qué sirve", cuantos("su app sí muestra una notificación") > 0)
    }

    @Test
    fun `el alcance se dice completo — qué se lee y qué no sale del teléfono`() {
        montar()

        assertTrue(
            "Falta decir que solo se leen las apps de la lista",
            cuantos("solo lee las notificaciones de las apps de banco que tiene en su lista") > 0,
        )
        assertTrue(
            "Falta decir qué pasa con las notificaciones de las demás apps",
            cuantos("se descartan sin leerlas") > 0,
        )
    }

    @Test
    fun `con el acceso concedido desaparece el botón y aparece lo que se observó`() {
        concederAcceso()
        montar()

        assertEquals(
            "El botón sigue ofreciendo conceder algo que ya está concedido",
            0,
            cuantos("Dar acceso a las notificaciones"),
        )
        // El mismo criterio que el resto de esta pantalla: no se dice «captura activa», se dice
        // qué llegó. Sin ninguna notificación todavía, eso es «ninguna aún».
        assertTrue("Falta el hecho observado", cuantos("Última notificación capturada") > 0)
        assertTrue("Debería decir que todavía no llegó ninguna", cuantos("ninguna aún") > 0)
    }
}
