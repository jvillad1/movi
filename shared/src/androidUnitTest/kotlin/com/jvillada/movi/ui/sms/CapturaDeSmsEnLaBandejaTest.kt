package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
import com.jvillada.movi.ui.components.ds.TEXTO_QUE_ES_ESTO
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
// entera. Ver el mismo criterio en `SuscripcionesActivasEnElTableroTest`. Ola 2: 1600 y no 1200,
// porque entre el aviso y la sección del teléfono entró la tarjeta del aviso de banco mudo.
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
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
            MoviTheme { Box(Modifier.fillMaxSize()) { CapturaDelBancoScreen(onNavigate = {}) } }
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

    /** Abre «¿Qué es esto?» de la primera tarjeta: lo largo se pliega, no se pierde (vuelta 8). */
    private fun abrirLaAyudaDelUltimoMensaje() {
        composeRule.onAllNodesWithText(TEXTO_QUE_ES_ESTO, useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
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

        esperarTexto("Nunca ha llegado un mensaje")
        // El detalle no está a la vista hasta que se pide: la tarjeta es una fila y una línea.
        composeRule.onNodeWithText(
            "Movi todavía no ha recibido ningún mensaje de tu banco",
            substring = true, useUnmergedTree = true,
        ).assertDoesNotExist()
        abrirLaAyudaDelUltimoMensaje()
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

        esperarTexto("Último mensaje")
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
            MoviTheme { Box(Modifier.fillMaxSize()) { CapturaDelBancoScreen(onNavigate = {}) } }
        }

        // Con la sección de captura ya pintada (el rótulo va en mayúsculas), la pantalla
        // terminó de componer y el aviso, si fuera a aparecer, ya estaría.
        esperarTexto("Captura en este teléfono")
        composeRule.onNodeWithText("Nunca ha llegado un mensaje", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_ULTIMO_MENSAJE, useUnmergedTree = true).assertDoesNotExist()
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

        // La fila dice cuándo y cuántos; el detalle entero se abre con «¿Qué es esto?».
        esperarTexto("Último mensaje · 3 de septiembre")
        esperarTexto("3 mensajes de tu banco")
        abrirLaAyudaDelUltimoMensaje()
        composeRule.onNodeWithText(
            "El último de los 3 mensajes de tu banco llegó el 3 de septiembre a las 7:15 a. m.",
            substring = true, useUnmergedTree = true,
        ).assertExists()
        // Y no se ofrece callar un aviso que no existe.
        composeRule.onNodeWithText("Silenciar este aviso", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    // ── Ola C: la pantalla es «Captura del banco» y lo que lista es el historial ──

    /**
     * Lo pendiente se revisa en «Por revisar»; acá quedan la configuración de la captura y el
     * historial de TODOS los mensajes — confirmados, ignorados y los que esperan.
     */
    @Test
    fun `es Captura del banco, con la configuracion y el historial entero`() {
        mensajes = listOf(
            sms("s1", "2026-08-01 10:00", SMS_STATE_CONFIRMED),
            sms("s2", "2026-09-03 07:15"),
        )
        montar()

        esperarTexto("Historial")
        composeRule.onNodeWithText("Captura del banco", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Captura en este teléfono", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Confirmado", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Pendiente", useUnmergedTree = true).assertExists()
        // Ya no cuenta pendientes en el encabezado: eso lo dice «Por revisar».
        composeRule.onNodeWithText("por confirmar", substring = true, useUnmergedTree = true).assertDoesNotExist()
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

        esperarTexto("Silenciar este aviso")
        composeRule.onNodeWithText("Silenciar este aviso", useUnmergedTree = true).performClick()

        esperarTexto("Este aviso no se muestra en Hoy ni en Por revisar.")
        assertEquals(true, pedido?.smsAlertMuted)
        // El hecho sigue ahí: lo que se calló es el recordatorio, no la noticia.
        composeRule.onNodeWithText("Nunca ha llegado un mensaje", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `y se puede volver a pedir el aviso`() {
        mensajes = emptyList()
        silenciadoEnElServer = true
        montar()

        esperarTexto("Volver a avisarme")
        composeRule.onNodeWithText("Volver a avisarme", useUnmergedTree = true).performClick()

        esperarTexto("Silenciar este aviso")
        assertEquals(false, pedido?.smsAlertMuted)
    }
}
