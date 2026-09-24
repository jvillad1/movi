package com.jvillada.movi.ui.documentos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ola B, tarea 7: «Extractos» se une a Documentos — «Importar movimientos» vive ahora en cada fila
 * de PDF o imagen, y hace lo mismo que hacía subir el archivo de nuevo en Extractos: llama al
 * repositorio y navega a [Screen.StatementReview] con lo que contestó.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class DocumentosScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val elResultado = StatementParseResult(
        statementId = "st_1",
        bankName = "Bancolombia",
        period = "agosto 2026",
        newTransactions = emptyList(),
        matches = emptyList(),
    )

    private val elPdf = Documento(
        id = "doc_pdf",
        nombre = "Extracto_agosto.pdf",
        tipo = TipoDeDocumento.EXTRACTO,
        mimeType = "application/pdf",
        bytes = 1000,
        subidoEn = 0L,
    )

    private val laEscritura = Documento(
        id = "doc_contrato",
        nombre = "Escritura.docx",
        tipo = TipoDeDocumento.CONTRATO,
        mimeType = "application/msword",
        bytes = 2000,
        subidoEn = 0L,
    )

    private inner class ConDocumentos(private val docs: List<Documento>) : RepositorioDePrueba() {
        override suspend fun getDocuments(): List<Documento> = docs
        override suspend fun getAccounts(): List<Account> = emptyList()
        override suspend fun getStatementImports(): List<StatementImport> = emptyList()
        override suspend fun readStatementFromDocument(id: String): StatementParseResult {
            assertEquals(elPdf.id, id, "tiene que pedir el extracto del documento que se tocó")
            return elResultado
        }
    }

    private val navegado = mutableListOf<Screen>()

    private fun montar(docs: List<Documento>) {
        Repositories.sustitutoDePrueba = ConDocumentos(docs)
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { DocumentosScreen(onNavigate = { navegado += it }) } }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tocar(texto: String) {
        // A diferencia de otras pantallas, acá el `clickable` vive en el mismo `Text` —no en un
        // contenedor que lo envuelva— así que el nodo clickeable ES el que tiene el texto, no
        // uno con un hijo que lo tenga.
        composeRule.onNode(hasClickAction() and hasText(texto), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `Importar movimientos en un PDF navega a la revision con el resultado del repositorio`() {
        montar(listOf(elPdf))
        esperarTexto("Importar movimientos")

        tocar("Importar movimientos")
        composeRule.waitUntil(timeoutMillis = 5_000) { navegado.isNotEmpty() }

        val destino = navegado.single()
        assertTrue(destino is Screen.StatementReview, "navegó a $destino")
        // El resultado viaja serializado en el JSON del destino — el mismo camino que ya usaba
        // Extractos al subir un archivo.
        assertTrue((destino as Screen.StatementReview).resultJson.contains("Bancolombia"))
        assertTrue(destino.resultJson.contains("agosto 2026"))
    }

    @Test
    fun `un documento que no es PDF ni imagen no ofrece Importar movimientos`() {
        montar(listOf(laEscritura))
        esperarTexto("Escritura")

        composeRule.onAllNodesWithText("Importar movimientos", useUnmergedTree = true)
            .fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }
}
