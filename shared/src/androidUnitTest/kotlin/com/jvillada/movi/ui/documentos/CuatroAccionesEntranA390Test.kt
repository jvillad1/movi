package com.jvillada.movi.ui.documentos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Fix round 1, hallazgo 6: con «Importar movimientos» sumada, la fila de un documento tiene
 * CUATRO acciones («Abrir», «Editar», «Borrar», «Importar movimientos») en un `Row` sin scroll ni
 * `FlowRow`. Esta prueba mide si entran en un teléfono real de 390 dp sin que ninguna se parta en
 * dos renglones — `@GraphicsMode(NATIVE)` porque sin el motor de texto real Robolectric mide todo
 * con un ancho que no sirve para esta pregunta (ver `FilaDeCategoriaNoSeRecortaTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h800dp-xhdpi")
class CuatroAccionesEntranA390Test {

    @get:Rule val composeRule = createComposeRule()

    private val elPdf = Documento(
        id = "doc_pdf",
        nombre = "Extracto_agosto.pdf",
        tipo = TipoDeDocumento.EXTRACTO,
        mimeType = "application/pdf",
        bytes = 1000,
        subidoEn = 0L,
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getDocuments(): List<Documento> = listOf(elPdf)
            override suspend fun getAccounts(): List<Account> = emptyList()
            override suspend fun getStatementImports(): List<StatementImport> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { DocumentosScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `las cuatro acciones de la fila entran en una sola linea a 390 dp`() {
        montar()

        listOf("Abrir", "Editar", "Borrar", "Importar movimientos").forEach { texto ->
            val nodos = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("«$texto» no se encontró en la fila", nodos.isNotEmpty())
            val nodo = nodos.first()
            val layout = layoutDe(nodo)
            assertTrue(
                "«$texto» se partió en ${layout.multiParagraph.lineCount} renglones a 390 dp " +
                    "(ancho del nodo ${nodo.size.width}px, ancho del texto en una línea " +
                    "${layout.multiParagraph.getLineWidth(0)}px) — considerar FlowRow",
                layout.multiParagraph.lineCount == 1,
            )
        }
    }

    private fun layoutDe(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        val accion = nodo.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("El nodo de texto no expuso su layout", accion?.invoke(resultados) == true)
        return resultados.single()
    }
}
