package com.jvillada.movi.ui.mas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
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
import kotlin.test.assertFalse

/**
 * # «Ocultar los saldos» se lee entero en Tú (vuelta 57)
 *
 * A 390 dp el subtítulo se cortaba en «…en est…». Ahora va en dos renglones: con el motor de texto real
 * y la letra de la app (×1,12), a 390 y a 360 dp, no se corta ni pasa de dos renglones.
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
        val nodo = composeRule.onAllNodes(hasText(SUBTITULO_DE_OCULTAR_LOS_SALDOS), useUnmergedTree = true).fetchSemanticsNodes().single()
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        val layout = r.single()
        assertFalse(layout.multiParagraph.didExceedMaxLines, "«$SUBTITULO_DE_OCULTAR_LOS_SALDOS» entero")
        assertFalse((0 until layout.lineCount).any { layout.isLineEllipsized(it) }, "sin «…»")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h3200dp-xhdpi")
    fun `a 390 dp el subtitulo se lee entero`() = montarYMedir()

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 dp el subtitulo se lee entero`() = montarYMedir()
}
