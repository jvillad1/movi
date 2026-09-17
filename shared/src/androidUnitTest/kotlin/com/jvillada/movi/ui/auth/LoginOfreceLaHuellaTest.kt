package com.jvillada.movi.ui.auth

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.MENSAJE_CANCELADA
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.SesionGuardada
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.platform.Huella
import com.jvillada.movi.platform.HuellaDelAparato
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # La pantalla de entrada, con un teléfono que tiene lector de huellas
 *
 * El lector de verdad no existe en la JVM, así que se enchufa uno de mentira por
 * [Huella.sustitutoDePrueba] — la misma costura que [Repositories.sustitutoDePrueba], y por el
 * mismo motivo: sin ella la pantalla no se puede montar como se ve en el teléfono del dueño.
 *
 * Lo que se afirma acá es lo que él ve y toca: que después de entrar con la contraseña **se le
 * ofrece**, que «Ahora no» se recuerda, que un teléfono sin huellas registradas no le ofrece nada,
 * y que cancelar el prompt lo deja en el formulario **con una explicación** y no en una pantalla
 * muda.
 *
 * Lo que NO puede cubrir: el diálogo del sistema y el cifrado contra el Keystore. Eso es teléfono.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LoginOfreceLaHuellaTest {

    @get:Rule val composeRule = createComposeRule()

    // `AppDePrueba` ya limpia la sesión antes de cada método; esto agrega lo que el logout NO
    // borra a propósito (la respuesta «Ahora no», que es del teléfono y no de la sesión).
    @Before fun enCero() {
        SessionManager.clear()
        SessionManager.huellaRechazada = false
    }

    @After fun desenchufar() {
        Huella.sustitutoDePrueba = null
        Repositories.sustitutoDePrueba = null
    }

    /** Un lector de mentira al que la prueba le dice qué contestar y qué le preguntaron. */
    private class LectorDePrueba(
        private val elEstado: EstadoDeHuella = EstadoDeHuella.LISTA,
        private val hayGuardado: Boolean = false,
        private val respuestaDeAbrir: ResultadoDeHuella = ResultadoDeHuella.CANCELADA,
    ) : HuellaDelAparato {
        var loQueSeGuardo: SesionGuardada? = null
        var seOlvido = false
        var abrirLlamado = 0

        override fun estado(): EstadoDeHuella = elEstado
        override fun haySesionGuardada(): Boolean = hayGuardado

        override fun guardar(sesion: SesionGuardada, alTerminar: (ResultadoDeHuella) -> Unit) {
            loQueSeGuardo = sesion
            // El `actual` de Android escribe en SessionManager antes de avisar; el de mentira
            // hace lo mismo para que la prueba mida el estado que de verdad queda.
            SessionManager.activarHuella(iv = "aabb", datos = "ccdd")
            alTerminar(ResultadoDeHuella.EXITO)
        }

        override fun abrir(alTerminar: (ResultadoDeHuella, SesionGuardada?) -> Unit) {
            abrirLlamado++
            alTerminar(respuestaDeAbrir, null)
        }

        override fun olvidar() {
            seOlvido = true
            SessionManager.desactivarHuella()
        }
    }

    private fun conLoginQueAnda() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun login(request: LoginRequest): AuthResponse =
                AuthResponse(token = "tok_123", userId = "usr_1", name = "Juan", email = "juan@correo.com")
        }
    }

    @Test
    fun `tras entrar con la contrasena se le ofrece la huella, y activarla la deja prendida`() {
        conLoginQueAnda()
        val lector = LectorDePrueba()
        Huella.sustitutoDePrueba = lector
        var destino: Screen? = null

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = { destino = it }) } }
        composeRule.onNodeWithText("Entrar").performSemanticsAction(SemanticsActions.OnClick)

        // No entra de una: primero pregunta. Y dice qué guarda y qué no.
        composeRule.onNodeWithText("Entra con tu huella la próxima vez").assertIsDisplayed()
        composeRule.onNodeWithText("Tu contraseña no se guarda nunca.", substring = true).assertIsDisplayed()
        assertNull("no puede navegar mientras está preguntando", destino)

        composeRule.onNodeWithText("Activar").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("tok_123", lector.loQueSeGuardo?.token)
        assertEquals("juan@correo.com", lector.loQueSeGuardo?.correo)
        assertTrue("el interruptor tiene que quedar prendido", SessionManager.huellaActivada)
        assertEquals(Screen.Dashboard, destino)
    }

    @Test
    fun `«Ahora no» se recuerda - no se le vuelve a ofrecer solo`() {
        conLoginQueAnda()
        Huella.sustitutoDePrueba = LectorDePrueba()
        var destino: Screen? = null

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = { destino = it }) } }
        composeRule.onNodeWithText("Entrar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Ahora no").performSemanticsAction(SemanticsActions.OnClick)

        assertTrue("sin esto se le ofrece lo mismo en cada entrada", SessionManager.huellaRechazada)
        assertFalse(SessionManager.huellaActivada)
        assertEquals("decir que no tiene que entrar igual", Screen.Dashboard, destino)
    }

    @Test
    fun `un telefono sin huellas registradas no ofrece nada y entra derecho`() {
        conLoginQueAnda()
        Huella.sustitutoDePrueba = LectorDePrueba(elEstado = EstadoDeHuella.SIN_REGISTRAR)
        var destino: Screen? = null

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = { destino = it }) } }
        composeRule.onNodeWithText("Entrar").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithText("Entra con tu huella la próxima vez").assertDoesNotExist()
        assertEquals(Screen.Dashboard, destino)
    }

    @Test
    fun `cancelar el prompt del arranque deja el formulario con una explicacion, no una pantalla muda`() {
        // El teléfono ya tenía la huella activada y una sesión guardada: al abrir Movi se pide.
        SessionManager.activarHuella(iv = "aabb", datos = "ccdd")
        val lector = LectorDePrueba(hayGuardado = true, respuestaDeAbrir = ResultadoDeHuella.CANCELADA)
        Huella.sustitutoDePrueba = lector
        var destino: Screen? = null

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = { destino = it }) } }

        assertEquals("el arranque tiene que pedir la huella una vez", 1, lector.abrirLlamado)
        composeRule.onNodeWithText(MENSAJE_CANCELADA).assertIsDisplayed()
        // Y no se pierde nada por cancelar: lo guardado sigue ahí y el enlace deja reintentar.
        assertFalse("cancelar no puede borrar la sesión guardada", lector.seOlvido)
        assertTrue(SessionManager.huellaActivada)
        composeRule.onNodeWithText("Entrar con huella").assertIsDisplayed()
        assertNull(destino)
    }
}
