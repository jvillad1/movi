package com.jvillada.movi.ui.mas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.SUBTITULO_DE_OCULTAR_LOS_SALDOS
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * # Ningún texto de Tú se corta (vuelta 57 y 58)
 *
 * A 390 dp «Ocultar los saldos» se cortaba en «…en est…» (vuelta 57) y a 360 dp «Editar inicio» en «…y en
 * qué o…» (vuelta 58). Ahora los subtítulos de las filas de Tú van en dos renglones: con el motor de
 * texto real y la letra de la app (×1,12), a 390 y a 360 dp, se recorren **todas** las filas y ningún
 * texto pasa de sus renglones ni termina en «…».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OcultarLosSaldosEnteroTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile = UserProfile(
            id = "u1", email = "jvillad1@gmail.com", name = "Juan Villada", avatarColor = "#7C6CF0", periodCutoffDay = 25,
        )
        override suspend fun isScreenAdmin(): Boolean = false
    }

    private fun montarYMedir() {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) { MasScreen(onNavigate = {}, onLogout = {}, esAndroid = true) }
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(SUBTITULO_DE_OCULTAR_LOS_SALDOS), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Que el perfil ya haya llegado: «Inicio del período» deja de decir «Cargando…».
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Cargando", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()

        // Vuelta 58: **todas** las filas de Tú, no solo una. Cada texto con su medida real: ninguno pasa de
        // sus renglones ni termina en «…». (A 360 dp y ×1,12, «Qué secciones ves en Hoy y en qué o…» se cortaba.)
        val leidos = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val cortados = leidos.mapNotNull { nodo ->
            val r = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            val layout = r.firstOrNull() ?: return@mapNotNull null
            val texto = layout.layoutInput.text.text
            val corta = layout.multiParagraph.didExceedMaxLines || (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
            if (corta) texto else null
        }
        assertTrue(leidos.size > 20, "se leyeron las filas de Tú (${leidos.size})")
        assertTrue(cortados.isEmpty(), "ningún texto de Tú se corta; cortados: $cortados")
        assertTrue(
            leidos.any { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Qué secciones ves en Inicio y en qué orden" } == true },
            "el subtítulo de «Editar inicio» está en pantalla",
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h3200dp-xhdpi")
    fun `a 390 dp el subtitulo se lee entero`() = montarYMedir()

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 dp el subtitulo se lee entero`() = montarYMedir()
}
