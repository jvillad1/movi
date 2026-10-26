package com.jvillada.movi.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **El login dice por qué está ahí cuando fue el server el que cerró la sesión.** El 26-oct el dueño
 * creía estar adentro con un token muerto; ahora la app lo saca, y esta pantalla tiene que decirle
 * que entre de nuevo para que la captura del banco siga — y no decir nada si salió él.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class LoginDicePorQueSeCerroTest {

    @get:Rule val composeRule = createComposeRule()

    private val linea = "Tu sesión se cerró. Entra de nuevo para seguir capturando los mensajes del banco."

    @Test
    fun `con la sesion rechazada por el server el login lo dice`() {
        SessionManager.save("tok_muerto", "usr_1", "Juan", "juan@correo.com")
        SessionManager.seRechazoElToken("tok_muerto")

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = {}) } }

        composeRule.onNodeWithText(linea).assertIsDisplayed()
    }

    @Test
    fun `si salio a mano el login no cuenta nada`() {
        SessionManager.save("tok", "usr_1", "Juan", "juan@correo.com")
        SessionManager.clear()

        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = {}) } }

        composeRule.onNodeWithText(linea).assertDoesNotExist()
    }
}
