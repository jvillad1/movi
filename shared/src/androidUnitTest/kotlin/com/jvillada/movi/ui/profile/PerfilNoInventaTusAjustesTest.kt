package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Perfil no inventa tus ajustes
 *
 * El corte del dueño es **25**. Con la lectura del perfil caída —o todavía en vuelo— esta pantalla
 * mostraba «Mes de calendario» en «Inicio del mes» como si fuera *su* ajuste guardado, y la fila
 * se dejaba tocar: la hoja abría con el **día 1** preseleccionado y el «Guardar» lo escribía de
 * verdad. Un toque, y todas las cifras de Movimientos, Presupuestos, Créditos e Inicio pasaban a
 * contarse sobre otra ventana. Nada reintentaba, así que en una visita con mala conexión la fila
 * mentía hasta salir de la pantalla.
 *
 * Acá se fija lo contrario: mientras el valor guardado no se sepa **no se afirma ninguno**, no se
 * puede guardar, y la falla se dice con el «Reintentar» de siempre. Y las hojas se quedan con el
 * valor que llega, no con el que había cuando se abrieron — eso último se prueba sobre la hoja
 * sola, que es donde vivía el `remember` sin clave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class PerfilNoInventaTusAjustesTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val caida = ApiException(503)

    private val perfilDelDueno = UserProfile(
        id = "u1",
        email = "jvillad1@gmail.com",
        name = "Juan",
        avatarColor = "#FF0000",
        periodCutoffDay = 25,
        reminderLeadDays = 5,
    )

    private fun montar(repo: RepositorioDePrueba, pantalla: @Composable () -> Unit) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { pantalla() } } }
    }

    private fun hay(texto: String) = composeRule
        .onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    /** ¿Se puede tocar la fila cuyo título es [titulo]? */
    private fun filaTocable(titulo: String) = composeRule
        .onAllNodes(hasClickAction() and hasAnyDescendant(hasText(titulo)), useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()

    private fun tocar(matcher: SemanticsMatcher) {
        composeRule.onNode(matcher, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocarFila(titulo: String) = tocar(hasClickAction() and hasAnyDescendant(hasText(titulo)))

    private fun reintentar() = tocar(hasClickAction() and hasAnyChild(hasText("Reintentar")))

    // ── Con la lectura caída ──────────────────────────────────────────────────

    @Test
    fun `sin el perfil no se afirma el mes de calendario ni se deja guardar`() {
        montar(object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = throw caida
            override suspend fun isScreenAdmin(): Boolean = false
        }) { PerfilScreen(onNavigate = {}, onLogout = {}) }

        esperar(AJUSTES_NO_LEIDOS)

        // Lo que decía antes, y que era el valor por defecto disfrazado del suyo.
        assertTrue(!hay("Mes de calendario"), "«Mes de calendario» es el default, no su ajuste")
        assertTrue(!hay("Cada día"), "no se conoce ningún día de corte")
        assertTrue(!hay("Te avisamos"), "tampoco se conoce la anticipación del aviso")
        assertTrue(hay(NO_PUDIMOS_LEERLO), "la fila tiene que decir que no se pudo leer")

        // Y la puerta al «Guardar» está cerrada: sin valor conocido, la fila no abre nada.
        assertTrue(!filaTocable("Inicio del mes"), "no se puede elegir un corte que todavía no se leyó")
        assertTrue(!filaTocable("Avisarme antes de un vencimiento"), "lo mismo para el aviso")
        assertTrue(!hay("¿Qué día empieza tu mes?"), "la hoja no debería estar abierta")

        // Lo que no depende del perfil sigue estando: la pantalla no se apaga entera.
        // (Solo lo que entra en la primera pantalla: es una `LazyColumn`, lo de más abajo ni
        // siquiera está compuesto.)
        assertTrue(hay("Cambiar contraseña"), "la fila que no depende del perfil se queda")
    }

    @Test
    fun `al reintentar aparece el corte de verdad y recien ahi se puede tocar`() {
        var hayRed = false
        montar(object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                if (hayRed) perfilDelDueno else throw caida
            override suspend fun isScreenAdmin(): Boolean = false
        }) { PerfilScreen(onNavigate = {}, onLogout = {}) }

        esperar(AJUSTES_NO_LEIDOS)
        hayRed = true
        reintentar()

        esperar("Cada día 25")
        assertTrue(!hay(AJUSTES_NO_LEIDOS), "resuelto el problema, el aviso se va")
        assertTrue(!hay(NO_PUDIMOS_LEERLO))
        assertTrue(hay("Te avisamos 5 días antes del vencimiento."))
        assertTrue(filaTocable("Inicio del mes"), "con el valor en la mano la fila vuelve a abrir la hoja")
    }

    // ── Con el perfil leído ───────────────────────────────────────────────────

    @Test
    fun `con el perfil leido la hoja abre en su dia y guarda ese, no el default`() {
        var guardado: UpdateProfileRequest? = null
        montar(object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfilDelDueno
            override suspend fun isScreenAdmin(): Boolean = false
            override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
                guardado = request
                return perfilDelDueno
            }
        }) { PerfilScreen(onNavigate = {}, onLogout = {}) }

        esperar("Cada día 25")
        tocarFila("Inicio del mes")
        esperar("¿Qué día empieza tu mes?")

        // Guardar sin tocar ningún número: lo que se escriba es lo que la hoja traía preseleccionado.
        tocar(hasClickAction() and hasAnyChild(hasText("Guardar")))
        composeRule.waitUntil(5_000) { guardado != null }
        assertEquals(25, guardado?.periodCutoffDay, "la hoja no puede proponer un día que él no eligió")
    }

    // ── La hoja abierta antes de que llegara el valor ──────────────────────────

    /**
     * El caso exacto del reporte: la hoja se abre mientras el perfil viaja —el valor que tiene a
     * mano es el default— y el corte real llega un instante después. Antes se quedaba en el 1 para
     * siempre, porque su estado vivía en un `remember {}` sin clave.
     */
    @Test
    fun `PeriodoSheet se queda con el valor que llega, no con el que tenia al abrirse`() {
        var guardado: Int? = null
        var cutoff by mutableStateOf(1)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    PeriodoSheet(cutoffActual = cutoff, onDismiss = {}, onSave = { guardado = it })
                }
            }
        }
        composeRule.waitForIdle()

        // Llega el perfil de verdad.
        cutoff = 25
        composeRule.waitForIdle()

        tocar(hasClickAction() and hasAnyChild(hasText("Guardar")))
        assertEquals(25, guardado, "la hoja seguía en el día que tenía al abrirse")
    }

    @Test
    fun `DiasDeAvisoSheet tambien se queda con el valor que llega`() {
        var guardado: Int? = null
        var dias by mutableStateOf(3)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    DiasDeAvisoSheet(diasActuales = dias, onDismiss = {}, onSave = { guardado = it })
                }
            }
        }
        composeRule.waitForIdle()

        dias = 7
        composeRule.waitForIdle()
        assertNull(guardado)

        tocar(hasClickAction() and hasAnyChild(hasText("Guardar")))
        assertEquals(7, guardado, "la hoja seguía en los días que tenía al abrirse")
    }
}
