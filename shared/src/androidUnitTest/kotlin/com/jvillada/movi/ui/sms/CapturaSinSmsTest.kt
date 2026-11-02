package com.jvillada.movi.ui.sms

import android.Manifest
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.jvillada.movi.comoLaVersionDePlay
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.pedirEnElManifiesto
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.sms.BackfillOutcome
import com.jvillada.movi.sms.SmsBackfill
import com.jvillada.movi.sms.elAparatoPuedeLeerSms
import com.jvillada.movi.sms.elManifiestoPideSms
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Vuelta 109 · **la versión de Google Play no lee SMS, y lo sabe por su manifiesto.**
 *
 * Los dos canales de `androidApp` son la misma app: `directa` (Drive) pide `READ_SMS`/`RECEIVE_SMS` y
 * `play` no. La decisión de ofrecer SMS sale de los permisos que el paquete PIDE
 * (`PackageInfo.requestedPermissions`, que acá se fija con el `PackageManager` de Robolectric), no de
 * un flag de build. Estas pruebas cubren las dos ramas: la función, la sección «Captura en este
 * teléfono» y el barrido del inbox.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class CapturaSinSmsTest {

    @get:Rule val composeRule = createComposeRule()

    private val contexto: Context get() = ApplicationProvider.getApplicationContext()

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

    // ── La regla ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `la regla pura pide los dos permisos de SMS`() {
        assertTrue(elManifiestoPideSms(listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)))
        assertFalse("Sin RECEIVE_SMS no hay captura en vivo", elManifiestoPideSms(listOf(Manifest.permission.READ_SMS)))
        assertFalse("Sin READ_SMS no hay barrido", elManifiestoPideSms(listOf(Manifest.permission.RECEIVE_SMS)))
        assertFalse(elManifiestoPideSms(emptyList()))
        assertFalse("Un paquete sin permisos pedidos devuelve null", elManifiestoPideSms(null))
    }

    @Test
    fun `con SMS en el manifiesto (la version de Drive) el aparato lee SMS`() {
        pedirEnElManifiesto(contexto, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        assertTrue(elAparatoPuedeLeerSms(contexto))
    }

    @Test
    fun `sin SMS en el manifiesto (la version de Play) el aparato no lee SMS`() {
        comoLaVersionDePlay(contexto)
        assertFalse(elAparatoPuedeLeerSms(contexto))
    }

    @Test
    fun `lo que ve la UI comun sigue al manifiesto`() {
        comoLaVersionDePlay(contexto)
        composeRule.setContent { Text(if (rememberEsteAparatoLeeSms()) "lee" else "no lee") }
        assertEquals(1, cuantos("no lee"))
    }

    // ── La sección «Captura en este teléfono» ────────────────────────────────────────────────

    @Test
    fun `con SMS la seccion ofrece el permiso de SMS y el historial, como siempre`() {
        montar()
        assertTrue("Falta la tarjeta del permiso de SMS", cuantos("Permiso de SMS") > 0)
        assertTrue("Falta el historial de SMS", cuantos("Sincronizar el período") > 0)
        assertTrue(cuantos("Acceso a notificaciones") > 0)
    }

    @Test
    fun `sin SMS la seccion no ofrece el permiso ni el historial de SMS y no nombra los SMS`() {
        comoLaVersionDePlay(contexto)
        montar()

        assertEquals("Ofrece un permiso que el manifiesto no pide", 0, cuantos("Permiso de SMS"))
        assertEquals("Ofrece el historial de SMS", 0, cuantos("Sincronizar el período"))
        assertEquals("Algún texto promete SMS en la versión de Play", 0, cuantos("SMS"))
        assertTrue("Falta el acceso a las notificaciones del banco", cuantos("Acceso a notificaciones") > 0)
        assertTrue("Falta el camino para darlo", cuantos("Dar acceso a las notificaciones") > 0)
        assertTrue("Falta explicar para qué sirve", cuantos(TEXTO_DE_LAS_NOTIFICACIONES_SIN_SMS.take(40)) > 0)
    }

    @Test
    fun `sin SMS y con las notificaciones dadas, la captura del telefono esta lista`() {
        comoLaVersionDePlay(contexto)
        Settings.Secure.putString(
            contexto.contentResolver,
            "enabled_notification_listeners",
            "${contexto.packageName}/com.jvillada.movi.notificaciones.EscuchaDeNotificaciones",
        )
        composeRule.setContent { Text(if (rememberSmsCaptureReady()) "lista" else "falta") }
        assertEquals(1, cuantos("lista"))
    }

    @Test
    fun `sin SMS y sin las notificaciones, a la captura le falta algo`() {
        comoLaVersionDePlay(contexto)
        composeRule.setContent { Text(if (rememberSmsCaptureReady()) "lista" else "falta") }
        assertEquals(1, cuantos("falta"))
    }

    // ── El barrido del inbox ─────────────────────────────────────────────────────────────────

    @Test
    fun `sin SMS en el manifiesto el barrido no lee el inbox aunque haya sesion`() {
        comoLaVersionDePlay(contexto)
        SessionManager.save("token-de-prueba", "u1", "Prueba", "prueba@local")
        val resultado = runBlocking { SmsBackfill.conElPeriodo(contexto, PeriodSettings()) }
        assertEquals(BackfillOutcome.NoPermission, resultado)
    }
}
