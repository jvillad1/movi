package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **La pantalla que tenía que haber avisado.** La captura de SMS pasó varias entregas sin
 * entregar un solo mensaje: `sms_messages` vacía en producción y los 73 movimientos del dueño
 * escritos a mano. Lo único que podía delatarlo era esta pantalla, y decía «AUTO-LECTURA ACTIVA»
 * en cuanto el permiso estuviera concedido — que era exactamente su caso.
 *
 * Lo que se fija acá es lo que una función pura no alcanza a probar: que el hecho de verdad se
 * pinta al montar la pantalla, y que el rótulo viejo ya no puede volver.
 *
 * Se monta en Android (Robolectric) porque es la única plataforma en la que estas pruebas
 * corren, y es además la peor para este defecto: es la que tiene la sección de permisos que
 * antes se llevaba toda la atención. Lo que se afirma acá sale de `commonMain` —de
 * `CapturaDeSms` en `:core`, ver `CapturaDeSmsTest`— así que la web y iOS pintan lo mismo.
 */
@RunWith(RobolectricTestRunner::class)
// Alta a propósito: debajo del aviso viene la sección «Captura en este teléfono» y la bandeja
// entera. Ver el mismo criterio en `SuscripcionesActivasEnMovimientosTest`.
@Config(qualifiers = "w411dp-h1200dp-xhdpi")
class CapturaDeSmsEnLaBandejaTest {

    @get:Rule val composeRule = createComposeRule()

    private var mensajes: List<SmsMessage> = emptyList()
    private var silenciadoEnElServer = false
    /** Lo último que la pantalla le mandó al server, que es lo que distingue silenciar de no. */
    private var pedido: UpdateProfileRequest? = null

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = mensajes
        override suspend fun getUserProfile(): UserProfile = UserProfile(
            id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF",
            smsAlertMuted = silenciadoEnElServer,
        )

        override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
            pedido = request
            request.smsAlertMuted?.let { silenciadoEnElServer = it }
            return getUserProfile()
        }
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSInboxScreen(onNavigate = {}) } }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun sms(id: String, cuando: String, estado: String = SMS_STATE_PENDING) = SmsMessage(
        id = id, time = cuando, bank = "Bancolombia",
        text = "Compra aprobada \$28.500 en Uber BV.", state = estado, det = "Uber",
    )

    // ── El caso del dueño: nunca llegó nada ────────────────────────────────────

    @Test
    fun `sin un solo mensaje, la pantalla lo dice sin rodeos`() {
        mensajes = emptyList()
        montar()

        esperarTexto("NUNCA HA LLEGADO UN MENSAJE")
        composeRule.onNodeWithText(
            "Movi todavía no ha recibido ningún mensaje de tu banco",
            substring = true, useUnmergedTree = true,
        ).assertExists()
    }

    /**
     * El rótulo que mintió durante semanas. No vuelve: lo decidía un permiso concedido, y un
     * permiso concedido con el receiver muerto es precisamente el estado que hay que delatar.
     */
    @Test
    fun `la pantalla ya no afirma que la auto-lectura este activa`() {
        mensajes = listOf(sms("s1", "2026-08-01 10:00", SMS_STATE_CONFIRMED))
        montar()

        esperarTexto("ÚLTIMO MENSAJE RECIBIDO")
        composeRule.onNodeWithText("AUTO-LECTURA ACTIVA", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * Y el error simétrico, que sería igual de grave: **una lectura caída no es «nunca llegó
     * nada»**. Sin señal, la pantalla se calla en vez de acusar a una captura que puede estar
     * funcionando perfecto.
     */
    @Test
    fun `si la bandeja no contesta, no se afirma que nunca llego nada`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getSmsMessages(): List<SmsMessage> = error("sin señal")
            override suspend fun getUserProfile(): UserProfile = UserProfile(
                id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF",
            )
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSInboxScreen(onNavigate = {}) } }
        }

        // Con la sección de captura ya pintada (el rótulo va en mayúsculas), la pantalla
        // terminó de componer y el aviso, si fuera a aparecer, ya estaría.
        esperarTexto("CAPTURA EN ESTE TELÉFONO")
        composeRule.onNodeWithText("NUNCA HA LLEGADO UN MENSAJE", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("ÚLTIMO MENSAJE RECIBIDO", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Cuando sí llegó algo ───────────────────────────────────────────────────

    @Test
    fun `con mensajes dice cuando llego el ultimo, y ese es el mas reciente`() {
        mensajes = listOf(
            sms("s1", "2026-08-01 10:00", SMS_STATE_CONFIRMED),
            sms("s2", "2026-09-03 07:15"),
            sms("s3", "2026-08-30 23:59", SMS_STATE_CONFIRMED),
        )
        montar()

        esperarTexto("ÚLTIMO MENSAJE RECIBIDO")
        composeRule.onNodeWithText(
            "El último de los 3 mensajes de tu banco llegó el 2026-09-03 07:15",
            substring = true, useUnmergedTree = true,
        ).assertExists()
        // Y no se ofrece callar un aviso que no existe.
        composeRule.onNodeWithText("No me avises de esto en Inicio", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    // ── El freno al ruido crónico ──────────────────────────────────────────────

    /**
     * Quien nunca va a usar la captura (alguien de iOS, alguien que solo usa la web) necesita
     * poder callar el recordatorio del Inicio: para esa persona nunca va a llegar un mensaje que
     * lo apague solo. El botón vive acá y no en el Inicio a propósito — para callar el aviso hay
     * que estar viendo lo que se calla, que sigue en pantalla después de tocarlo.
     */
    @Test
    fun `se puede pedir que el Inicio no avise, sin que la bandeja deje de decirlo`() {
        mensajes = emptyList()
        montar()

        esperarTexto("No me avises de esto en Inicio")
        composeRule.onNodeWithText("No me avises de esto en Inicio", useUnmergedTree = true).performClick()

        esperarTexto("Este aviso no se muestra en Inicio.")
        assertEquals(true, pedido?.smsAlertMuted)
        // El hecho sigue ahí: lo que se calló es el recordatorio, no la noticia.
        composeRule.onNodeWithText("NUNCA HA LLEGADO UN MENSAJE", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `y se puede volver a pedir el aviso`() {
        mensajes = emptyList()
        silenciadoEnElServer = true
        montar()

        esperarTexto("Volver a avisarme en Inicio")
        composeRule.onNodeWithText("Volver a avisarme en Inicio", useUnmergedTree = true).performClick()

        esperarTexto("No me avises de esto en Inicio")
        assertEquals(false, pedido?.smsAlertMuted)
    }
}
