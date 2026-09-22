package com.jvillada.movi.ui.documentos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.EnlaceDeDescarga
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.theme.MoviTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # La fila de un documento, con las acciones abajo
 *
 * En el teléfono del dueño (~390 dp) las tres acciones a la derecha se comían un tercio de la fila
 * y el nombre del archivo se partía a mitad de palabra. Las acciones bajaron a un renglón propio;
 * estas pruebas fijan que al mudarlas **siguen haciendo lo mismo**: «Abrir» pide el enlace del
 * documento, «Editar» abre la hoja, y «Borrar» pregunta antes — no hay papelera.
 *
 * Lo que NO cubre: es Robolectric, así que no mide dónde parte el renglón ni dice nada de la web.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class FilaDeDocumentoTest {

    @get:Rule val composeRule = createComposeRule()

    private val nombre = "Portal_Beneficios_3037_movimientos_09_2026.jpeg"
    private val doc = Documento(
        id = "d1",
        nombre = nombre,
        tipo = TipoDeDocumento.EXTRACTO,
        mimeType = "image/jpeg",
        bytes = 113 * 1024,
        subidoEn = 0L,
        periodo = "2026-10",
        notas = "El de beneficios",
    )

    private val borrados = mutableListOf<String>()
    private val enlacesPedidos = mutableListOf<String>()
    private val abiertos = mutableListOf<String>()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDocuments(): List<Documento> = listOf(doc)
            override suspend fun getAccounts() = emptyList<com.jvillada.movi.shared.model.Account>()
            override suspend fun getDocumentLink(id: String): EnlaceDeDescarga {
                enlacesPedidos += id
                return EnlaceDeDescarga(url = "https://ejemplo.test/$id", expiraEn = Long.MAX_VALUE)
            }
            override suspend fun deleteDocument(id: String) {
                borrados += id
            }
        }
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                abiertos += uri
            }
        }
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    Box(Modifier.fillMaxSize()) { DocumentosScreen(onNavigate = {}) }
                }
            }
        }
        composeRule.waitUntil(5_000) { hay("Abrir") }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** La acción con ese texto, tocable. Si hay varias («Borrar» en la fila y en la confirmación), la última. */
    private fun accion(texto: String): SemanticsNodeInteraction {
        val nodos = composeRule.onAllNodes(hasText(texto) and hasClickAction(), useUnmergedTree = true)
        val cuantos = nodos.fetchSemanticsNodes().size
        assertTrue(cuantos > 0, "no hay una acción tocable «$texto»")
        return nodos[cuantos - 1]
    }

    @Test
    fun el_nombre_se_muestra_con_cortes_pero_abrir_pide_el_documento_real() {
        montar()

        assertTrue(hay(nombreQueSePartePorSusSeparadores(nombre)), "el nombre se pinta con cortes posibles")

        accion("Abrir").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { abiertos.isNotEmpty() }

        assertEquals(listOf("d1"), enlacesPedidos)
        assertEquals(listOf("https://ejemplo.test/d1"), abiertos)
    }

    @Test
    fun editar_abre_la_hoja_de_correccion() {
        montar()

        accion("Editar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { hay("Guardar") }
    }

    @Test
    fun borrar_pregunta_antes_y_solo_borra_al_confirmar() {
        montar()

        accion("Borrar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { hay("¿Borrar este documento?") }
        assertTrue(borrados.isEmpty(), "tocar «Borrar» en la fila no puede borrar sin preguntar")

        // El «Borrar» de la confirmación es el último que se compuso.
        accion("Borrar").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { borrados.isNotEmpty() }
        assertEquals(listOf("d1"), borrados)
    }
}
