package com.jvillada.movi.ui.profile

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.BorrarLaCuentaRequest
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # La pantalla de borrar la cuenta
 *
 * Lo que no se puede romper: **el botón no se habilita sin la contraseña y la palabra BORRAR**, y un
 * borrado que el server rechaza no toca nada del aparato. Con el borrado hecho, el aparato queda
 * limpio —lo local de la cuenta, también lo que no subió, y la sesión— y se vuelve a la entrada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class BorrarLaCuentaEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private class Grabador(private val rechazo: Throwable? = null) : RepositorioDePrueba() {
        val pedidos = mutableListOf<BorrarLaCuentaRequest>()
        val olvidados = mutableListOf<String>()
        override suspend fun borrarLaCuenta(request: BorrarLaCuentaRequest) {
            pedidos += request
            rechazo?.let { throw it }
        }
        override fun olvidarTodoLoDe(userId: String) { olvidados += userId }
    }

    private var navegoA: Screen? = null

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        SessionManager.borradoLocalPendiente = null
    }

    @Test
    fun `el boton no se habilita sin contrasena y BORRAR`() {
        montar(Grabador())
        boton().assertIsNotEnabled()

        escribir(TAG_CONTRASENA_PARA_BORRAR, "mi-clave")
        boton().assertIsNotEnabled()

        escribir(TAG_CONFIRMACION_PARA_BORRAR, "borrar")
        boton().assertIsNotEnabled()
        escribir(TAG_CONFIRMACION_PARA_BORRAR, "BORRA")
        boton().assertIsNotEnabled()

        escribir(TAG_CONFIRMACION_PARA_BORRAR, "BORRAR")
        boton().assertIsEnabled()

        // Y sin contraseña, aunque diga BORRAR, tampoco.
        escribir(TAG_CONTRASENA_PARA_BORRAR, "")
        boton().assertIsNotEnabled()
    }

    @Test
    fun `primero ofrece descargar los datos y dice que se borran los documentos y las personas`() {
        montar(Grabador())
        composeRule.onNodeWithText(DESCARGAR_TUS_DATOS, useUnmergedTree = true).assertIsDisplayed()
        LO_QUE_SE_BORRA.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).performScrollTo() }
        assertTrue(LO_QUE_SE_BORRA.any { "documentos" in it })
        assertTrue(LO_QUE_SE_BORRA.any { "personas y comercios" in it })
    }

    @Test
    fun `con todo puesto borra, limpia el aparato y vuelve a la entrada`() {
        val grabador = Grabador()
        SessionManager.save(token = "t", userId = "usr-que-se-va", name = "N", email = "n@movi.test")
        montar(grabador)

        escribir(TAG_CONTRASENA_PARA_BORRAR, "mi-clave")
        escribir(TAG_CONFIRMACION_PARA_BORRAR, "BORRAR")
        tocarElBoton()
        composeRule.waitUntil(5_000) { navegoA != null }

        assertEquals(listOf(BorrarLaCuentaRequest("mi-clave", "BORRAR")), grabador.pedidos)
        assertEquals(listOf("usr-que-se-va"), grabador.olvidados, "lo local de la cuenta, también lo que no subió")
        assertFalse(SessionManager.isLoggedIn, "la sesión se cerró")
        assertNull(SessionManager.userId)
        assertEquals(Screen.Login, navegoA)
    }

    @Test
    fun `si la contrasena no coincide lo dice y no toca nada del aparato`() {
        val grabador = Grabador(rechazo = ApiException(403, "La contraseña no coincide"))
        SessionManager.save(token = "t", userId = "usr-se-queda", name = "N", email = "n@movi.test")
        montar(grabador)

        escribir(TAG_CONTRASENA_PARA_BORRAR, "otra")
        escribir(TAG_CONFIRMACION_PARA_BORRAR, "BORRAR")
        tocarElBoton()
        composeRule.waitUntil(5_000) { grabador.pedidos.isNotEmpty() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("La contraseña no coincide. No se borró nada.").performScrollTo().assertIsDisplayed()
        assertTrue(grabador.olvidados.isEmpty())
        assertTrue(SessionManager.isLoggedIn, "la sesión sigue")
        assertEquals("usr-se-queda", SessionManager.userId)
        assertNull(navegoA)
        // Y se puede reintentar.
        boton().assertIsEnabled()
    }

    @Test
    fun `la regla del boton, pura`() {
        assertFalse(sePuedeBorrarLaCuenta("", "BORRAR", borrando = false))
        assertFalse(sePuedeBorrarLaCuenta("clave", "", borrando = false))
        assertFalse(sePuedeBorrarLaCuenta("clave", "Borrar", borrando = false))
        assertFalse(sePuedeBorrarLaCuenta("clave", "BORRAR", borrando = true), "no se manda dos veces")
        assertTrue(sePuedeBorrarLaCuenta("clave", "BORRAR", borrando = false))
        assertTrue(sePuedeBorrarLaCuenta("clave", " BORRAR ", borrando = false), "los espacios de más no frenan")
    }

    // ── Andamio ───────────────────────────────────────────────────────────────────────────

    private fun montar(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { MoviTheme { BorrarLaCuentaScreen(onNavigate = { navegoA = it }) } }
        composeRule.waitForIdle()
    }

    private fun boton() = composeRule.onNodeWithTag(TAG_BOTON_BORRAR).performScrollTo()

    private fun escribir(tag: String, texto: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextReplacement(texto)
        composeRule.waitForIdle()
    }

    /** Bajo Robolectric `performClick` no llega al composable: ver `HojaAgregarEligeLaCuentaTest`. */
    private fun tocarElBoton() {
        boton().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
