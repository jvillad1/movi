package com.jvillada.movi.ui.profile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.platform.BackHandlerEffect
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.LocalPilaDeHojas
import com.jvillada.movi.ui.PilaDeHojas
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.atras
import com.jvillada.movi.ui.hayAdondeVolver
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
 * # El «atrás» del teléfono sobre las hojas de Perfil
 *
 * `PilaDeHojas` trajo el mecanismo y `ElAtrasDelSistemaTest` lo probó con hojas de mentira; lo que
 * no podía cubrir —lo dice en su propio KDoc— es que **las hojas de Perfil se anoten**. Es una
 * línea por hoja y es opt-in, así que sin una prueba acá el arreglo del dueño no existe: apretar
 * atrás con «Cambiar contraseña» abierta seguía sacando Perfil de la pila y aterrizándolo en
 * «Más», con las dos contraseñas tipeadas perdidas.
 *
 * Se aprieta el «atrás» de verdad —el `OnBackPressedDispatcher` de la Activity— sobre el mismo
 * cableado que arma `App.kt`, con la pantalla de Perfil real adentro.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ElAtrasCierraLasHojasDePerfilTest {

    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val perfilDelDueno = UserProfile(
        id = "u1",
        email = "jvillad1@gmail.com",
        name = "Juan",
        avatarColor = "#FF0000",
        periodCutoffDay = 25,
        reminderLeadDays = 5,
    )

    /** Donde estaba parado cuando abrió Perfil: Inicio → Más → Perfil. */
    private fun pilaEnPerfil() = mutableStateListOf(Screen.Dashboard, Screen.Mas, Screen.Profile)

    /** El cableado de `App.kt`, con Perfil adentro. */
    @Composable
    private fun CascaraConPerfil(pila: SnapshotStateList<Screen>, hojas: PilaDeHojas) {
        MoviTheme {
            BackHandlerEffect(
                enabled = hayAdondeVolver(hojas, pila),
                onBack = { atras(hojas, pila) },
            )
            CompositionLocalProvider(LocalPilaDeHojas provides hojas) {
                Box(Modifier.fillMaxSize()) { PerfilScreen(onNavigate = {}, onLogout = {}) }
            }
        }
    }

    private fun montar(repo: RepositorioDePrueba, pila: SnapshotStateList<Screen>, hojas: PilaDeHojas) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { CascaraConPerfil(pila, hojas) }
    }

    private fun hay(texto: String) = composeRule
        .onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    private fun tocar(matcher: SemanticsMatcher) {
        composeRule.onNode(matcher, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocarFila(titulo: String) = tocar(hasClickAction() and hasAnyDescendant(hasText(titulo)))

    private fun apretarAtras() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun `atras cierra la hoja de contrasena y deja Perfil donde estaba`() {
        val pila = pilaEnPerfil()
        val hojas = PilaDeHojas()
        montar(object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfilDelDueno
            override suspend fun isScreenAdmin(): Boolean = false
        }, pila, hojas)

        esperar("Cambiar contraseña")
        tocarFila("Cambiar contraseña")
        esperar("Contraseña actual")
        assertEquals(1, hojas.cuantas, "la hoja tiene que anotarse en la pila de hojas")

        apretarAtras()

        assertFalse(hay("Contraseña actual"), "la hoja debería haberse cerrado")
        assertEquals(
            listOf(Screen.Dashboard, Screen.Mas, Screen.Profile),
            pila.toList(),
            "cerrar una hoja no es navegar: la pila no se mueve",
        )
        assertFalse(hojas.hayHojaAbierta)
        // Y Perfil sigue ahí, con sus datos leídos — no hubo que volver a cargar nada.
        assertTrue(hay("Cambiar contraseña"))
        assertTrue(hay("Cada día 25"))
    }

    @Test
    fun `atras sobre la hoja del periodo la cierra sin guardar nada`() {
        val pila = pilaEnPerfil()
        val hojas = PilaDeHojas()
        var guardado: UpdateProfileRequest? = null
        montar(object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfilDelDueno
            override suspend fun isScreenAdmin(): Boolean = false
            override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
                guardado = request
                return perfilDelDueno
            }
        }, pila, hojas)

        esperar("Cada día 25")
        tocarFila("Inicio del mes")
        esperar("¿Qué día empieza tu mes?")

        apretarAtras()

        assertFalse(hay("¿Qué día empieza tu mes?"), "la hoja debería haberse cerrado")
        assertNull(guardado, "salir de la hoja no guarda: atrás es cancelar, no confirmar")
        assertEquals(listOf(Screen.Dashboard, Screen.Mas, Screen.Profile), pila.toList())
        assertTrue(hay("Cada día 25"), "el corte del dueño queda como estaba")
    }

    /**
     * La otra mitad del contrato: sin hoja abierta, el «atrás» hace lo de siempre y saca Perfil de
     * la pila. Sin esto, una hoja que se anota y no se desanota dejaría la pantalla sin salida.
     */
    @Test
    fun `sin hoja abierta, atras sale de Perfil`() {
        val pila = pilaEnPerfil()
        val hojas = PilaDeHojas()
        montar(object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfilDelDueno
            override suspend fun isScreenAdmin(): Boolean = false
        }, pila, hojas)

        esperar("Cada día 25")
        // Abrir y cerrar con la X deja la pila de hojas vacía otra vez.
        tocarFila("Inicio del mes")
        esperar("¿Qué día empieza tu mes?")
        apretarAtras()
        assertFalse(hojas.hayHojaAbierta)

        apretarAtras()
        assertEquals(listOf(Screen.Dashboard, Screen.Mas), pila.toList())
    }
}
