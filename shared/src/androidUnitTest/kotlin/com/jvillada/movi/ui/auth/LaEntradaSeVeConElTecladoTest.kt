package com.jvillada.movi.ui.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.shared.model.PasswordProblem
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Crear la cuenta con el teclado abierto y la letra grande (vuelta 115)
 *
 * Un usuario con un Samsung S25 Ultra (Android 17, APK 1.92) no pudo crear su cuenta y el server
 * nunca recibió su registro: con el teclado abierto y la letra grande, el error («al menos 12
 * caracteres») y «Crear cuenta» quedaban recortados fuera de una columna sin desplazamiento.
 *
 * Teléfono chico (360×640 dp), letra al 130 % del teléfono por el 1,12 de la app, y el teclado
 * simulado: la pantalla vive en 320 dp de alto, lo que queda con el teclado arriba (la raíz de
 * `App.kt` descuenta su alto con `imePadding`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class LaEntradaSeVeConElTecladoTest {

    @get:Rule val composeRule = createComposeRule()

    private class Grabador : RepositorioDePrueba() {
        val registros = mutableListOf<RegisterRequest>()
        val entradas = mutableListOf<LoginRequest>()
        override suspend fun register(request: RegisterRequest): AuthResponse {
            registros += request
            return AuthResponse(token = "tok", userId = "usr_1", name = request.name, email = request.email)
        }
        override suspend fun login(request: LoginRequest): AuthResponse {
            entradas += request
            throw ApiException(401)
        }
    }

    private val repo = Grabador()

    @After
    fun limpiar() { Repositories.sustitutoDePrueba = null }

    private fun montar(contenido: @Composable () -> Unit) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * ESCALA_DEL_TELEFONO_AL_130)) {
                    // Lo que queda arriba del teclado.
                    Box(Modifier.fillMaxWidth().height(320.dp)) { contenido() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Dónde está el nodo **sin recortar**: `boundsInRoot` viene recortado por el clip del desplazamiento,
     * así que un nodo medio afuera daría siempre «adentro». La posición y el tamaño propios, no.
     */
    private fun limites(n: SemanticsNodeInteraction): Rect = n.fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    /** Lo que se ve de la columna: su caja, que va antes del `verticalScroll` (el viewport). */
    private fun areaVisible(): Rect = composeRule.onNodeWithTag(TAG_COLUMNA_DE_ENTRADA).fetchSemanticsNode().boundsInRoot

    /** El nodo está entero dentro de lo que se ve de la columna (no recortado por el desplazamiento). */
    private fun seVe(n: SemanticsNodeInteraction, que: String) {
        val visible = areaVisible()
        val nodo = limites(n)
        assertTrue(
            nodo.top >= visible.top - 0.5f && nodo.bottom <= visible.bottom + 0.5f,
            "$que se ve entero: $nodo dentro de $visible",
        )
    }

    private fun boton(texto: String) = composeRule.onNode(hasText(texto) and hasClickAction())
    private fun campo(i: Int) = composeRule.onAllNodes(hasSetTextAction())[i]

    @Test
    fun `con una contrasena corta el error y Crear cuenta quedan a la vista y no se manda nada`() {
        montar { RegisterScreen(onNavigate = {}) }
        campo(0).performTextReplacement("Andrés")
        campo(1).performTextReplacement("andres@correo.com")
        campo(2).performTextReplacement("corta")

        boton("Crear cuenta").performScrollTo().performClick()
        composeRule.waitForIdle()

        val error = composeRule.onNodeWithTag(TAG_ERROR_DE_ENTRADA, useUnmergedTree = true)
        assertEquals(
            PasswordPolicy.messageFor(PasswordProblem.TOO_SHORT),
            error.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text },
        )
        seVe(error, "el error")
        seVe(boton("Crear cuenta"), "«Crear cuenta»")
        assertTrue(
            error.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.LiveRegion),
            "el error se anuncia",
        )
        assertEquals(emptyList(), repo.registros, "con la contraseña corta no se manda nada")
    }

    @Test
    fun `el mismo error otra vez vuelve a traer el error y el boton a la vista`() {
        montar { RegisterScreen(onNavigate = {}) }
        campo(0).performTextReplacement("Andrés")
        campo(1).performTextReplacement("andres@correo.com")
        campo(2).performTextReplacement("corta")
        boton("Crear cuenta").performScrollTo().performClick()
        composeRule.waitForIdle()
        seVe(composeRule.onNodeWithTag(TAG_ERROR_DE_ENTRADA, useUnmergedTree = true), "el error, la primera vez")

        // Sube hasta el título y baja solo hasta el campo: el error y el botón quedan abajo, fuera de la vista.
        composeRule.onNode(hasText("Crear cuenta") and !hasClickAction()).performScrollTo()
        campo(2).performScrollTo()
        composeRule.waitForIdle()
        val visible = areaVisible()
        assertTrue(
            limites(boton("Crear cuenta")).bottom > visible.bottom + 0.5f,
            "la prueba solo vale si el botón quedó fuera de la vista: ${limites(boton("Crear cuenta"))} vs $visible",
        )

        // «Listo» del teclado con la misma contraseña corta: el mismo error, otro intento.
        campo(2).performImeAction()
        composeRule.waitForIdle()
        seVe(composeRule.onNodeWithTag(TAG_ERROR_DE_ENTRADA, useUnmergedTree = true), "el error, la segunda vez")
        seVe(boton("Crear cuenta"), "«Crear cuenta», la segunda vez")
        assertEquals(emptyList(), repo.registros)
    }

    @Test
    fun `con una contrasena valida se manda el registro`() {
        val navego = mutableListOf<com.jvillada.movi.ui.Screen>()
        montar { RegisterScreen(onNavigate = { navego += it }) }
        campo(0).performTextReplacement("Andrés")
        campo(1).performTextReplacement(" andres@correo.com ")
        campo(2).performTextReplacement("una clave bien larga")

        boton("Crear cuenta").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(RegisterRequest("andres@correo.com", "Andrés", "una clave bien larga")), repo.registros)
        assertTrue(navego.isNotEmpty(), "abre la guía de primer uso")
    }

    @Test
    fun `la regla de la contrasena se ve antes de tocar el boton`() {
        montar { RegisterScreen(onNavigate = {}) }
        campo(2).performTextReplacement("algo")
        val ayuda = composeRule.onNode(hasText("Al menos ${PasswordPolicy.MIN_LENGTH} caracteres"), useUnmergedTree = true)
        ayuda.performScrollTo()
        seVe(ayuda, "la regla, con algo escrito")
    }

    @Test
    fun `al entrar el error rechazado queda junto a Entrar y a la vista`() {
        montar { LoginScreen(onNavigate = {}) }
        campo(0).performTextReplacement("andres@correo.com")
        campo(1).performTextReplacement("no es esta")

        boton("Entrar").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, repo.entradas.size)
        val error = composeRule.onNodeWithTag(TAG_ERROR_DE_ENTRADA, useUnmergedTree = true)
        seVe(error, "el error")
        seVe(boton("Entrar"), "«Entrar»")
        assertTrue(limites(error).bottom <= limites(boton("Entrar")).top + 0.5f, "el error va arriba del botón")
    }
}
