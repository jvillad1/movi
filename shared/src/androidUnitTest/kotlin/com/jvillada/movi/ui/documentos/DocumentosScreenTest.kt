package com.jvillada.movi.ui.documentos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasAnyDescendant
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
import com.jvillada.movi.shared.repository.ApiException
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

    /**
     * `idSolicitado` graba el id en vez de afirmar adentro de `readStatementFromDocument`: esa
     * función corre bajo el `runCatching` de `DocumentosScreen.importar`, que atrapa
     * `Throwable` — un `AssertionError` ahí adentro queda envuelto en el snackbar de error en
     * vez de tumbar la prueba. Se graba y se afirma DESPUÉS, fuera de ese `runCatching`.
     */
    private inner class ConDocumentos(
        private val docs: List<Documento>,
        private val falla: ApiException? = null,
    ) : RepositorioDePrueba() {
        var idSolicitado: String? = null
        override suspend fun getDocuments(): List<Documento> = docs
        override suspend fun getAccounts(): List<Account> = emptyList()
        override suspend fun getStatementImports(): List<StatementImport> = emptyList()
        override suspend fun readStatementFromDocument(id: String): StatementParseResult {
            idSolicitado = id
            falla?.let { throw it }
            return elResultado
        }
    }

    private val navegado = mutableListOf<Screen>()

    private fun <T : RepositorioDePrueba> montarConRepo(repo: T): T {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { DocumentosScreen(onNavigate = { navegado += it }) } }
        }
        return repo
    }

    private fun montar(docs: List<Documento>, falla: ApiException? = null): ConDocumentos =
        montarConRepo(ConDocumentos(docs, falla))

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
        val repo = montar(listOf(elPdf))
        esperarTexto("Importar movimientos")

        tocar("Importar movimientos")
        composeRule.waitUntil(timeoutMillis = 5_000) { navegado.isNotEmpty() }

        // Pidió el extracto del documento que se tocó — afirmado ACÁ, fuera del `runCatching`
        // de la pantalla (ver el KDoc de `ConDocumentos`).
        assertEquals(elPdf.id, repo.idSolicitado)

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

    @Test
    fun `si el server rechaza el extracto, se muestra su motivo y no se navega`() {
        val motivo = "No encontramos movimientos en este archivo."
        montar(listOf(elPdf), falla = ApiException(422, motivo))
        esperarTexto("Importar movimientos")

        tocar("Importar movimientos")

        // El mismo criterio que tenía Extractos: `toUserMessage()` sobre un 4xx de validación
        // devuelve el cuerpo que escribió el server, no un genérico «Algo salió mal».
        esperarTexto(motivo)
        assertTrue(navegado.isEmpty(), "un extracto rechazado no navega a la revisión")
    }

    // ── «Importaciones»: el error se dice y «Reintentar» no revienta (fix round 2, hallazgo A) ──

    private inner class FallaLuegoTraeImportaciones(private val docs: List<Documento>) : RepositorioDePrueba() {
        var intento = 0
        override suspend fun getDocuments(): List<Documento> = docs
        override suspend fun getAccounts(): List<Account> = emptyList()
        override suspend fun getStatementImports(): List<StatementImport> {
            intento++
            if (intento == 1) throw ApiException(500, null)
            return listOf(
                StatementImport(
                    id = "imp1", accountId = "acc1", bankName = "Bancolombia",
                    period = "agosto 2026", importedAt = 0L, importedCount = 3, reconciledCount = 1,
                ),
            )
        }
    }

    /**
     * `NoSePudoLeer` pone el `clickable` en un `Box` que ENVUELVE el texto «Reintentar», a
     * diferencia de `AccionDeFila` (donde el `clickable` vive en el mismo `Text` — ver [tocar]).
     */
    private fun tocarBotonQueEnvuelveTexto(texto: String) {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `una lectura de importaciones que falla se dice, y Reintentar la trae sin reventar`() {
        // Antes del fix, tocar «Reintentar» ponía `importsError` en null mientras el `item` de
        // error de la lista todavía podía volver a componerse leyendo ese estado directo con
        // `!!` — este test ejercita exactamente esa transición error → éxito de punta a punta.
        montarConRepo(FallaLuegoTraeImportaciones(listOf(elPdf)))
        esperarTexto("No pude cargar el historial de importaciones")

        tocarBotonQueEnvuelveTexto("Reintentar")

        // «Importaciones» se pinta en mayúsculas (MinSectionHeader hace `.uppercase()`); lo que
        // de verdad importa es que la fila de la importación llegó.
        esperarTexto("BANCOLOMBIA")
        // Y el aviso de error ya no está — no se puede afirmar «no pude leer» y a la vez
        // mostrar la lista que sí llegó.
        composeRule.onAllNodesWithText("No pude cargar el historial de importaciones", useUnmergedTree = true)
            .fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    // ── Revisión final: sin documentos, «Importaciones» sigue ahí ─────────────────────────────

    private inner class SinDocumentosConUnaImportacion : RepositorioDePrueba() {
        override suspend fun getDocuments(): List<Documento> = emptyList()
        override suspend fun getAccounts(): List<Account> = emptyList()
        override suspend fun getStatementImports(): List<StatementImport> = listOf(
            StatementImport(
                id = "imp1", accountId = "acc1", bankName = "Bancolombia",
                period = "agosto 2026", importedAt = 0L, importedCount = 3, reconciledCount = 1,
            ),
        )
    }

    @Test
    fun `sin documentos, la seccion Importaciones y su fila se muestran y llevan al detalle`() {
        // Borrar los PDF de una importación que salió mal es el caso típico de quedar sin
        // documentos — y «Importaciones» es el único camino a «Deshacer importación».
        montarConRepo(SinDocumentosConUnaImportacion())
        esperarTexto("Aquí se guardan tus extractos")
        esperarTexto("IMPORTACIONES")
        esperarTexto("BANCOLOMBIA")

        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText("BANCOLOMBIA", substring = true)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(Screen.ImportDetail("imp1"), navegado.single())
    }

    @Test
    fun `sin documentos hay un solo Subir archivo, el del encabezado`() {
        montarConRepo(SinDocumentosConUnaImportacion())
        esperarTexto("Aquí se guardan tus extractos")
        assertEquals(
            1,
            composeRule.onAllNodesWithText("Subir archivo", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }
}
