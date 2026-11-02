package com.jvillada.movi.ui.auth

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_GUIA_DEL_CAMPO
import com.jvillada.movi.ui.profile.BorrarLaCuentaScreen
import com.jvillada.movi.ui.profile.GUIA_DE_LA_CONTRASENA_NUEVA
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * # Un campo de contraseña vacío no muestra puntos (vuelta 115)
 *
 * En el iPhone de Caro la entrada mostraba «••••••» en «Contraseña» sin nada escrito: parecía que ya
 * hubiera una guardada. Salía de la guía del campo, puesta a propósito (y en el registro, doce `•`).
 * Los puntos son de lo escrito; una guía se lee. Lo mismo en la entrada HTML de la web, que va encima
 * del wasm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class LaContrasenaVaciaNoTienePuntosTest {

    @get:Rule val composeRule = createComposeRule()

    /** Los puntos con que se tapa una contraseña (no el «·» que separa frases en Movi). */
    private val puntos = setOf('•', '●')

    private fun textoDe(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty() +
            nodo.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /**
     * Las guías a la vista. Ni ellas ni lo que pintan los campos (vacíos) llevan un punto de
     * contraseña. Solo los campos: una lista con viñetas «•» en la pantalla es otra cosa.
     */
    private fun guiasSinPuntos(): List<String> {
        composeRule.waitForIdle()
        val guias = composeRule.onAllNodes(hasTestTag(TAG_GUIA_DEL_CAMPO), useUnmergedTree = true)
            .fetchSemanticsNodes().map(::textoDe)
        val campos = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText), useUnmergedTree = true)
            .fetchSemanticsNodes().map(::textoDe)
        assertTrue(campos.isNotEmpty(), "la pantalla tiene sus campos")
        val conPuntos = (guias + campos).filter { t -> t.any { it in puntos } }
        assertEquals(emptyList(), conPuntos, "un campo vacío no muestra puntos")
        return guias
    }

    @Test
    fun `la entrada guia con palabras, sin puntos`() {
        composeRule.setContent { MoviTheme { LoginScreen(onNavigate = {}) } }
        val guias = guiasSinPuntos()
        assertTrue(GUIA_DE_LA_CONTRASENA_AL_ENTRAR in guias, "la contraseña vacía dice «Tu contraseña»: $guias")
    }

    @Test
    fun `el registro dice la regla del server en vez de doce puntos`() {
        composeRule.setContent { MoviTheme { RegisterScreen(onNavigate = {}) } }
        val guias = guiasSinPuntos()
        assertTrue(GUIA_DE_LA_CONTRASENA_NUEVA in guias, "la guía de la contraseña nueva: $guias")
        assertEquals("Al menos ${PasswordPolicy.MIN_LENGTH} caracteres", GUIA_DE_LA_CONTRASENA_NUEVA)
    }

    @Test
    fun `borrar la cuenta tampoco muestra puntos`() {
        composeRule.setContent { MoviTheme { BorrarLaCuentaScreen(onNavigate = {}) } }
        val guias = guiasSinPuntos()
        assertTrue("La que usas para entrar" in guias, "la guía de la contraseña: $guias")
    }

    // ── Lo que no se monta acá ─────────────────────────────────────────────────────────────

    private fun archivo(ruta: String): File =
        listOf("../$ruta", ruta).map(::File).firstOrNull { it.exists() }
            ?: fail("No encuentro $ruta desde ${File(".").absolutePath}")

    @Test
    fun `ningun campo de la app lleva una guia de puntos`() {
        // Una guía, un ejemplo o un placeholder con puntos, en cualquier pantalla (también las hojas
        // que no se montan arriba, como la clave de un PDF o cambiar la contraseña).
        val ui = archivo("shared/src/commonMain/kotlin/com/jvillada/movi/ui")
        val conPuntos = ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, linea ->
                val argumento = Regex("""\b(guia|ejemplo|placeholder)\s*=\s*(.*)""").find(linea)?.groupValues?.get(2)
                if (argumento != null && argumento.any { it in puntos }) "${f.name}:${i + 1}: ${linea.trim()}" else null
            }
        }.toList()
        assertEquals(emptyList(), conPuntos, "una guía de puntos parece un campo lleno")
    }

    @Test
    fun `la entrada html de la web tampoco`() {
        val html = archivo("webApp/src/wasmJsMain/resources/index.html").readText()
        val contrasenas = Regex("""<input[^>]*type="password"[^>]*>""").findAll(html).map { it.value }.toList()
        assertEquals(5, contrasenas.size, "entrar, nueva y repetir (recuperar y registro): $contrasenas")
        for (campo in contrasenas) {
            val guia = Regex("""placeholder="([^"]*)"""").find(campo)?.groupValues?.get(1) ?: fail("sin placeholder: $campo")
            assertTrue(guia.isNotBlank() && guia.none { it in puntos }, "placeholder legible: $campo")
        }
        assertTrue("""placeholder="Al menos ${PasswordPolicy.MIN_LENGTH} caracteres"""" in html, "la regla del server, con su número")
    }
}
