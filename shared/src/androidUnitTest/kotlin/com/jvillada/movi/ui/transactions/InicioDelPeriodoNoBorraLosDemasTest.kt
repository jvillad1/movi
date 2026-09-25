package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La hoja del mes en Movimientos no borra las excepciones que no llegó a leer
 *
 * El caso: la red falla justo en la lectura del perfil al abrir Movimientos —la pantalla cae al mes
 * de calendario y no conoce `{"2026-10":"2026-09-24"}`— y un momento después el dueño toca el mes y
 * guarda un arranque. El mapa viaja entero, así que armado sobre lo que la pantalla tenía borraba
 * la excepción de octubre. La escritura relee el perfil antes de guardar; si esa relectura también
 * falla, no escribe nada y lo dice en la hoja.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class InicioDelPeriodoNoBorraLosDemasTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private val gasto = FinancialEvent(
        id = "e1", accountId = banco.id, type = TransactionType.EXPENSE, amount = 18_500L,
        category = "Comida", description = "Carnes y Legumbres Santa Elena",
        timestamp = Clock.System.now().toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
    )

    private val perfil = UserProfile(
        id = "u1", email = "jvillad1@gmail.com", name = "Juan", avatarColor = "#FF0000",
        periodCutoffDay = 25, periodStarts = mapOf("2026-10" to "2026-09-24"),
    )

    /** Sin el perfil, la pantalla cae al mes de calendario: ese es el mes que la hoja edita. */
    private val sinPerfil = PeriodSettings()
    private val mesVisible = periodoActual(Clock.System.now().toEpochMilliseconds(), sinPerfil)

    private inner class Repo(private val lecturaDelPerfil: (Int) -> UserProfile) : RepositorioDePrueba() {
        var lecturas = 0
        val escrituras = mutableListOf<UpdateProfileRequest>()
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun getEventsByDay(): List<EventDay> = listOf(
            EventDay(date = epochMillisToAppDate(gasto.timestamp).toString(), total = -18_500L, items = listOf(gasto)),
        )
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getUserProfile(): UserProfile = lecturaDelPerfil(++lecturas)
        override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
            escrituras += request
            return perfil.copy(periodStarts = request.periodStarts ?: perfil.periodStarts)
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun montarYGuardarElMes(repo: Repo) {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Carnes y Legumbres") }

        val rotulo = nombreDe(mesVisible).replaceFirstChar { it.uppercase() }
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText(rotulo)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        // En el árbol combinado el texto del botón es del botón mismo: la hoja y su fondo, que
        // también son tocables, no lo absorben.
        composeRule.onNode(hasClickAction() and hasText("Este mes empezó el", substring = true))
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun `con el perfil sin leer al abrir, guardar el mes conserva la excepcion de octubre`() {
        val repo = Repo { vez -> if (vez == 1) error("sin red") else perfil }
        montarYGuardarElMes(repo)
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.escrituras.isNotEmpty() }

        val elegido = inicioDelPeriodo(mesVisible, sinPerfil).toString()
        assertEquals(
            listOf(UpdateProfileRequest(periodStarts = perfil.periodStarts + (mesVisible.prefijo to elegido))),
            repo.escrituras,
        )
        // El mes que se editó es el de calendario; octubre, mientras no sea ese, sigue intacto.
        if (mesVisible.prefijo != "2026-10") {
            assertEquals("2026-09-24", repo.escrituras.single().periodStarts?.get("2026-10"))
        }
    }

    @Test
    fun `si la relectura tambien falla, no se escribe nada y la hoja lo dice`() {
        val repo = Repo { error("sin red") }
        montarYGuardarElMes(repo)
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Algo salió mal. Intenta de nuevo.") }

        assertTrue(repo.escrituras.isEmpty())
        assertTrue(repo.lecturas >= 2, "la hoja volvió a leer el perfil antes de escribir")
        assertTrue(hay("Este mes empezó el"), "la hoja sigue abierta para reintentar")
    }
}
