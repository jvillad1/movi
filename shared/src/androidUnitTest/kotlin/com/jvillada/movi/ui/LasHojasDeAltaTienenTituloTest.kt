package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.accounts.TITULO_DE_NUEVA_CUENTA
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO_DE_HOJA
import com.jvillada.movi.ui.credits.CreditTermsSheet
import com.jvillada.movi.ui.credits.DebtTypeChooserSheet
import com.jvillada.movi.ui.credits.PREGUNTA_DEL_DESEMBOLSO
import com.jvillada.movi.ui.credits.TITULO_DEL_ELECTOR_DE_DEUDA
import com.jvillada.movi.ui.credits.TITULO_DEL_NUEVO_PRESTAMO
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Las hojas de alta tienen su título (vuelta 61)
 *
 * La revisión encontró «Nuevo préstamo» y «Nueva cuenta» sin título de hoja —arrancaban en la pregunta
 * o en «Banco»— y «¿Qué deuda quieres registrar?» en `tituloMedio`, chica. Ahora cada una abre con su
 * `EncabezadoDeHoja`, ningún texto de adentro lo supera (la regla de la vuelta 41), y la pregunta del
 * desembolso va en la letra del título de un grupo (`titulo`, la de «Términos»), no en la de cuerpo.
 *
 * Motor de texto real (`NATIVE`, sdk 34) para leer el estilo de cada texto, con la letra ×1,12.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class LasHojasDeAltaTienenTituloTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(contenido: @Composable () -> Unit) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = emptyList()
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "u@movi.test", name = "U", avatarColor = "#000000")
        }
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { contenido() } }
            }
        }
        composeRule.waitForIdle()
    }

    private fun textoDe(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private data class TextoPintado(val texto: String, val tamano: Float, val peso: Int, val enElEncabezado: Boolean)

    private fun textosPintados(): List<TextoPintado> {
        val delEncabezado = composeRule.onAllNodes(hasAnyAncestor(hasTestTag(TAG_ENCABEZADO_DE_HOJA)), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.id }.toSet()
        return composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().mapNotNull { nodo ->
                val resultados = mutableListOf<TextLayoutResult>()
                nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
                val estilo = resultados.firstOrNull()?.layoutInput?.style ?: return@mapNotNull null
                TextoPintado(textoDe(nodo), estilo.fontSize.value, (estilo.fontWeight ?: FontWeight.Normal).weight, nodo.id in delEncabezado)
            }
    }

    /** Abre con un solo `EncabezadoDeHoja` que dice [titulo], y nada de adentro le gana. */
    private fun abreConSuTitulo(titulo: String): TextoPintado {
        val encabezados = composeRule.onAllNodes(hasTestTag(TAG_ENCABEZADO_DE_HOJA), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, encabezados.size, "un solo EncabezadoDeHoja")
        val textos = textosPintados()
        val elTitulo = textos.first { it.enElEncabezado }
        assertEquals(titulo, elTitulo.texto)
        textos.filterNot { it.enElEncabezado }.forEach { otro ->
            val loSupera = otro.tamano > elTitulo.tamano || (otro.tamano == elTitulo.tamano && otro.peso > elTitulo.peso)
            assertTrue(!loSupera, "«${otro.texto}» (${otro.tamano} sp, ${otro.peso}) supera al título (${elTitulo.tamano} sp)")
        }
        // El título va antes que cualquier otro texto de la hoja.
        val primero = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().minBy { it.boundsInRoot.top }
        assertEquals(titulo, textoDe(primero), "el título es lo primero de la hoja")
        return elTitulo
    }

    @Test
    fun `Nueva cuenta abre con su titulo`() {
        montar { CreateAccountSheet(onDismiss = {}, onAccountCreated = {}) }
        abreConSuTitulo(TITULO_DE_NUEVA_CUENTA)
    }

    @Test
    fun `el elector de deuda pregunta en el titulo de la hoja`() {
        montar { DebtTypeChooserSheet(onDismiss = {}, onLoan = {}, onCard = {}) }
        val titulo = abreConSuTitulo(TITULO_DEL_ELECTOR_DE_DEUDA)
        // Era `tituloMedio` (14,5 sp a la vista); ahora la letra del título de una hoja.
        assertTrue(titulo.tamano > 18f, "la pregunta va en la letra del título de la hoja: $titulo")
    }

    @Test
    fun `Nuevo prestamo abre con su titulo y la pregunta va como el titulo de un grupo`() {
        lateinit var esperado: Pair<Float, Int>
        montar {
            val titulo = Movi.textos.titulo
            esperado = titulo.fontSize.value to (titulo.fontWeight ?: FontWeight.Normal).weight
            CreditTermsSheet(editing = null, candidates = emptyList(), onDismiss = {}, onSaved = {})
        }
        abreConSuTitulo(TITULO_DEL_NUEVO_PRESTAMO)
        val pregunta = textosPintados().single { it.texto == PREGUNTA_DEL_DESEMBOLSO }
        val terminos = textosPintados().first { it.texto == "Términos" }
        assertEquals(terminos.tamano, pregunta.tamano, 0.01f, "la pregunta y «Términos» van en la misma letra")
        assertEquals(terminos.peso, pregunta.peso, "y el mismo peso")
        assertEquals(esperado.first, pregunta.tamano, 0.01f, "la letra `titulo`")
    }
}
