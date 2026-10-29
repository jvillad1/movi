package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Vuelta 68 · Actividad con la API caída: un solo error, sin esqueletos eternos
 *
 * La revisión `r3-err-act.png`: el esqueleto de Entró · Salió · Neto pulsaba para siempre arriba de
 * «No pudimos cargar tus movimientos», y abajo salía además el aviso «No se pudo conectar…». Ahora la
 * franja se va y el error es uno: la tarjeta, con su «Reintentar».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class UnSoloErrorEnActividadTest {

    @get:Rule val composeRule = createComposeRule()

    private val perfil = UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")

    private val repositorio = object : RepositorioDePruebaDeMovimientos() {
        override suspend fun getUserProfile(): UserProfile = perfil
        override suspend fun getEventsByDay(): List<EventDay> = error("sin red")
        override suspend fun getAccounts(): List<com.jvillada.movi.shared.model.Account> = emptyList()
    }

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = repositorio
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    private fun cuantas(texto: String, substring: Boolean = false): Int =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `sin red la franja se va y el error es uno`() {
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } } }
        composeRule.waitUntil(timeoutMillis = 5_000) { cuantas("No pudimos cargar tus movimientos") == 1 }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag(TAG_RESUMEN_EN_EL_TELEFONO, useUnmergedTree = true).fetchSemanticsNodes().size, "sin esqueleto eterno")
        assertEquals(1, cuantas("Reintentar"), "un solo Reintentar")
        assertEquals(0, cuantas("Algo salió mal", substring = true), "sin el aviso de abajo")
    }
}
