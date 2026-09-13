package com.jvillada.movi.ui.listas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.budgets.PresupuestosScreen
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.goals.MetasScreen
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.ui.sms.SMSInboxScreen
import com.jvillada.movi.ui.documentos.DocumentosScreen
import com.jvillada.movi.ui.categorias.CategoriasScreen
import com.jvillada.movi.ui.transactions.PERIODO_NO_LEIDO
import com.jvillada.movi.ui.transactions.TransactionsScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Una lista que no se pudo leer no dice que está vacía
 *
 * Créditos decía «Deuda total $0 · Sin créditos registrados» con el botón de crear uno; Metas,
 * «Aún no hay metas»; Presupuestos, «Gastado $0 de $0». A quien sí tiene, y con un botón que invita
 * a duplicar. Ahora las tres muestran [com.jvillada.movi.ui.components.NoSePudoLeer], como Cuentas,
 * y al reintentar con red vuelve lo que hay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class NoDiceVacioSiNoLeyoTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(repo: RepositorioDePrueba, pantalla: @Composable () -> Unit) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { pantalla() } } }
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    private fun reintentar() {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText("Reintentar")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private val caida = ApiException(503)

    @Test
    fun `Creditos sin respuesta no dice deuda cero ni ofrece crear, y al reintentar aparecen`() {
        var hayRed = false
        montar(object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = if (hayRed) emptyList() else throw caida
            override suspend fun getCards(): List<CardSummary> = emptyList()
        }) { CreditosScreen(onNavigate = {}) }

        esperar("No pudimos cargar tus créditos")
        assertTrue(!hay("Sin créditos registrados"))
        assertTrue(!hay("Nuevo crédito"))
        assertTrue(!hay("Deuda total"))

        hayRed = true
        reintentar()
        esperar("Sin créditos registrados")
        assertTrue(!hay("No pudimos cargar"))
    }

    @Test
    fun `Metas sin respuesta no dice que no hay metas`() {
        montar(object : RepositorioDePrueba() {
            override suspend fun getGoals(): List<Goal> = throw caida
            override suspend fun getAccounts(): List<Account> = emptyList()
        }) { MetasScreen(onNavigate = {}) }

        esperar("No pudimos cargar tus metas")
        assertTrue(!hay("Aún no hay metas"))
        assertTrue(!hay("Nueva meta"))
    }

    @Test
    fun `Presupuestos sin respuesta no dice gastado cero ni ofrece crear`() {
        var lecturas = 0
        montar(object : RepositorioDePrueba() {
            override suspend fun getBudgets(): List<Budget> { lecturas++; throw caida }
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        }) { PresupuestosScreen(onNavigate = {}) }

        esperar("No pudimos cargar tus presupuestos")
        assertTrue(!hay("Nuevo presupuesto"))
        assertTrue(!hay("Gastado en"))
        val antes = lecturas
        reintentar()
        composeRule.waitUntil(5_000) { lecturas > antes }
        assertEquals(antes + 1, lecturas)
    }

    // ── Segunda tanda: lo que no tiene botón de crear pero sí afirmaba «no hay» ────────────────
    // `RepositorioDePrueba` tira en todo lo que no se sobrescribe, así que un repo vacío es una
    // lectura caída en cada llamada.

    @Test
    fun `Movimientos sin respuesta no ofrece registrar el primero`() {
        montar(object : RepositorioDePrueba() {}) { TransactionsScreen(onNavigate = {}) }
        esperar("No pudimos cargar tus movimientos")
        assertTrue(!hay("Sin movimientos aún"))
        assertTrue(!hay("Registrar el primero"))
    }

    @Test
    fun `Categorias sin respuesta no dice cero categorias`() {
        montar(object : RepositorioDePrueba() {}) { CategoriasScreen(onNavigate = {}) }
        esperar("No pudimos cargar tus categorías")
        assertTrue(!hay("Nada por aquí todavía"))
        assertTrue(!hay("0 categorías"))
    }

    @Test
    fun `Documentos sin respuesta no queda en blanco y se puede reintentar`() {
        var hayRed = false
        montar(object : RepositorioDePrueba() {
            override suspend fun getDocuments(): List<Documento> = if (hayRed) emptyList() else throw caida
        }) { DocumentosScreen(onNavigate = {}) }
        esperar("No pudimos cargar tus documentos")
        hayRed = true
        reintentar()
        esperar("Aquí se guardan tus extractos")
    }

    @Test
    fun `la bandeja de SMS sin respuesta no dice cero por confirmar`() {
        montar(object : RepositorioDePrueba() {}) { SMSInboxScreen(onNavigate = {}) }
        esperar("No pudimos cargar tus mensajes")
        assertTrue(!hay("0 por confirmar"))
    }

    /** Sin el perfil, Movimientos cae al mes de calendario; eso se dice en vez de callarlo. */
    @Test
    fun `Movimientos avisa cuando no pudo leer el periodo`() {
        montar(object : RepositorioDePrueba() {
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getAccounts(): List<Account> = emptyList()
        }) { TransactionsScreen(onNavigate = {}) }
        esperar(PERIODO_NO_LEIDO)
    }
}
