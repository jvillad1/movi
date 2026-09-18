package com.jvillada.movi.ui.auth

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.EstadoDeHuella
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
import com.jvillada.movi.ui.NavStack
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Entrar no deja la pantalla de entrada atrás
 *
 * ## Lo que se rompía
 *
 * `navigate` apilaba siempre, así que entrar dejaba la pila en `[Login, Dashboard]` y nada la
 * limpiaba. El «atrás» del teléfono devolvía al formulario de entrada —con la contraseña en
 * blanco— a alguien que acababa de entrar. Con «Entrar con huella» prendido era peor: volver a
 * `LoginScreen` re-dispara su efecto de arranque, así que el prompt del lector se abría **solo**,
 * sin que nadie lo hubiera pedido.
 *
 * Se monta la pantalla de verdad y se le da el mismo `onNavigate` que le da `App.kt`
 * ([NavStack.navegar] sobre la pila), que es donde vive la regla. Lo que se afirma es la forma de
 * la pila después de entrar: un solo destino, sin nada debajo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class EntrarNoDejaElLoginAtrasTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun desenchufar() {
        Huella.sustitutoDePrueba = null
        Repositories.sustitutoDePrueba = null
    }

    /** Un teléfono sin huellas registradas: entra derecho, sin ofrecer nada. */
    private class SinHuellas : HuellaDelAparato {
        override fun estado(): EstadoDeHuella = EstadoDeHuella.SIN_REGISTRAR
        override fun pedir(proposito: PropositoDeHuella, alTerminar: (ResultadoDeHuella) -> Unit) = Unit
    }

    private fun conLoginQueAnda() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun login(request: LoginRequest): AuthResponse =
                AuthResponse(token = "tok_123", userId = "usr_1", name = "Juan", email = "juan@correo.com")
        }
    }

    @Test
    fun `entrar deja la pila en el Inicio y nada debajo`() {
        SessionManager.clear()
        conLoginQueAnda()
        Huella.sustitutoDePrueba = SinHuellas()
        val pila = mutableStateListOf<Screen>(Screen.Login)

        composeRule.setContent {
            MoviTheme { LoginScreen(onNavigate = { NavStack.navegar(pila, it) }) }
        }
        composeRule.onNodeWithText("Entrar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(
            "el «atrás» no puede tener adónde volver: abajo estaba el formulario de entrada",
            listOf(Screen.Dashboard),
            pila.toList(),
        )
    }

    @Test
    fun `ir a crear cuenta sí apila - ahí volver tiene sentido`() {
        SessionManager.clear()
        Huella.sustitutoDePrueba = SinHuellas()
        val pila = mutableStateListOf<Screen>(Screen.Login)

        composeRule.setContent {
            MoviTheme { LoginScreen(onNavigate = { NavStack.navegar(pila, it) }) }
        }
        composeRule.onNodeWithText("¿No tienes cuenta? Regístrate")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(Screen.Login, Screen.Register), pila.toList())
    }
}
