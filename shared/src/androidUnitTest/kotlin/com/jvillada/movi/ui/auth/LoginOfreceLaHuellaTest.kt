package com.jvillada.movi.ui.auth

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.EstadoDeHuella
import com.jvillada.movi.data.MENSAJE_CANCELADA
import com.jvillada.movi.data.PropositoDeHuella
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.ResultadoDeHuella
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.platform.Huella
import com.jvillada.movi.platform.HuellaDelAparato
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
 * y que cancelar el prompt lo deja en el formulario **con una explicación y sin perder la sesión**.
 *
 * Y una que no es de interfaz, pero se prueba acá porque necesita un lector enchufado para
 * afirmar que a ese lector NUNCA se le pidió nada: [laHuellaNoLeSacaElTokenAlTelefono].
 *
 * Lo que NO puede cubrir: el diálogo del sistema. Eso es teléfono.
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
        private val respuesta: ResultadoDeHuella = ResultadoDeHuella.EXITO,
    ) : HuellaDelAparato {
        var pedidos = mutableListOf<PropositoDeHuella>()

        override fun estado(): EstadoDeHuella = elEstado

        override fun pedir(proposito: PropositoDeHuella, alTerminar: (ResultadoDeHuella) -> Unit) {
            pedidos += proposito
            alTerminar(respuesta)
        }
    }

    private fun conLoginQueAnda() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun login(request: LoginRequest): AuthResponse =
                AuthResponse(token = "tok_123", userId = "usr_1", name = "Juan", email = "juan@correo.com")
        }
    }

    /**
     * **La prueba que justifica todo este diseño.**
     *
     * Con la huella prendida, un proceso que Android levanta por su cuenta —`SmsBackfillWorker`
     * cada 6 horas, el receptor en tiempo real tras un reinicio— tiene que poder leer el token
     * **sin una sola interacción biométrica**, con el teléfono en el bolsillo y nadie mirando.
     *
     * El diseño anterior cifraba el token con una llave del Keystore que exigía un dedo: acá
     * habría devuelto `null` y los SMS del banco dejaban de subirse hasta que él abriera Movi. Ese
     * costo diario es lo que el dueño no quiso pagar.
     *
     * Lo que se afirma es exactamente eso y nada más: el token está, y **al lector nunca se le
     * pidió nada**. Que además sobreviva a un reinicio del proceso no se puede probar acá
     * —`Settings()` no se construye ni bajo Robolectric, así que en esta suite todo el
     * almacenamiento cae a la copia en memoria—; lo que sí lo sostiene es que `guardar()` ya no
     * tiene ninguna rama que saque estas claves del almacenamiento, que era la que rompía esto.
     */
    @Test
    fun laHuellaNoLeSacaElTokenAlTelefono() {
        val lector = LectorDePrueba()
        Huella.sustitutoDePrueba = lector
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.huellaActivada = true

        // Lo que hace `SmsBackfill` antes de subir nada.
        assertEquals("tok_123", SessionManager.token)
        assertEquals("usr_1", SessionManager.userId)
        assertTrue(SessionManager.isLoggedIn)
        assertTrue("un worker no puede quedarse esperando un dedo", lector.pedidos.isEmpty())
    }

    @Test
    fun `tras entrar con la contrasena se le ofrece la huella, y activarla la deja prendida`() {
        conLoginQueAnda()
        val lector = LectorDePrueba()
        Huella.sustitutoDePrueba = lector
        var destino: Screen? = null

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = { destino = it }) } }
        composeRule.onNodeWithText("Entrar").performSemanticsAction(SemanticsActions.OnClick)

        // No entra de una: primero pregunta. Y dice para qué sirve y qué no guarda.
        composeRule.onNodeWithText("Entra con tu huella la próxima vez").assertIsDisplayed()
        composeRule.onNodeWithText("tu contraseña no se guarda nunca", substring = true).assertIsDisplayed()
        assertNull("no puede navegar mientras está preguntando", destino)

        composeRule.onNodeWithText("Activar").performSemanticsAction(SemanticsActions.OnClick)

        // Se pide el dedo ANTES de prender el interruptor, no después.
        assertEquals(listOf(PropositoDeHuella.ACTIVAR), lector.pedidos)
        assertTrue("el interruptor tiene que quedar prendido", SessionManager.huellaActivada)
        assertEquals(Screen.Dashboard, destino)
    }

    @Test
    fun `si el lector rechaza la activacion, el interruptor NO queda prendido`() {
        // Prenderlo igual lo dejaría con una puerta que recién falla la próxima vez que abra Movi.
        conLoginQueAnda()
        Huella.sustitutoDePrueba = LectorDePrueba(respuesta = ResultadoDeHuella.CANCELADA)
        var destino: Screen? = null

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = { destino = it }) } }
        composeRule.onNodeWithText("Entrar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithText("Activar").performSemanticsAction(SemanticsActions.OnClick)

        assertFalse(SessionManager.huellaActivada)
        composeRule.onNodeWithText(MENSAJE_CANCELADA).assertIsDisplayed()
        assertNull("sigue ofreciendo: puede reintentar o decir «Ahora no»", destino)
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
    fun `cancelar la puerta deja el formulario con el motivo, y la sesion intacta`() {
        // El arranque con el interruptor prendido y la sesión viva: App.kt manda a esta pantalla.
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.huellaActivada = true
        val lector = LectorDePrueba(respuesta = ResultadoDeHuella.CANCELADA)
        Huella.sustitutoDePrueba = lector
        var destino: Screen? = null

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = { destino = it }) } }

        assertEquals("el arranque tiene que pedir la huella una vez", listOf(PropositoDeHuella.ENTRAR), lector.pedidos)
        composeRule.onNodeWithText(MENSAJE_CANCELADA).assertIsDisplayed()
        assertNull("cancelar no puede dejarlo pasar", destino)

        // Y no se perdió nada: la sesión sigue viva, el interruptor prendido, y el enlace de
        // reintento a la vista. Las dos salidas siguen ahí: el dedo, o la contraseña de abajo.
        assertEquals("tok_123", SessionManager.token)
        assertTrue(SessionManager.huellaActivada)
        composeRule.onNodeWithText("Entrar con huella").assertIsDisplayed()
    }

    @Test
    fun `la huella aceptada en el arranque entra sin escribir nada`() {
        SessionManager.save("tok_123", "usr_1", "Juan", "juan@correo.com")
        SessionManager.huellaActivada = true
        Huella.sustitutoDePrueba = LectorDePrueba(respuesta = ResultadoDeHuella.EXITO)
        var destino: Screen? = null

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = { destino = it }) } }

        assertEquals(Screen.Dashboard, destino)
        // La sesión es la MISMA de siempre: la huella dejó pasar, no abrió ninguna caja fuerte.
        assertEquals("tok_123", SessionManager.token)
    }
}
