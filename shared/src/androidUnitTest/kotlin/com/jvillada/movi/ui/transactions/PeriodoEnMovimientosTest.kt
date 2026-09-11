package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Movimientos dice de qué mes son sus cifras, y deja moverse entre meses
 *
 * El dueño: *«Como la periodicidad es mensual me debería dejar ver cada mes en las fechas que yo
 * establecí, de 25 a 25 o cuando comience el período»*.
 *
 * Se monta con **corte 26**, que es el suyo: su salario está registrado el 26 de agosto y se llama
 * «Salario Septiembre 2026». Lo que hay que poder afirmar y una función pura no alcanza a decir: que
 * el mes está **escrito arriba**, que abajo se explica de qué fecha a qué fecha va —sin esa línea,
 * «septiembre» empezando en agosto es un error de la app y no una decisión suya— y que las flechas
 * mueven de verdad.
 *
 * Los nombres esperados salen de las mismas funciones que la pantalla usa, no de constantes: el
 * test no puede fijar «septiembre» sin volverse falso el mes que viene.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_PERIODO)
class PeriodoEnMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private val ajustes = PeriodSettings(cutoffDay = 26)
    private val hoy = periodoActual(Clock.System.now().toEpochMilliseconds(), ajustes)

    private val gasto = FinancialEvent(
        id = "e1",
        accountId = banco.id,
        type = TransactionType.EXPENSE,
        amount = 18_500L,
        category = "Comida",
        description = "Carnes y Legumbres Santa Elena",
        timestamp = Clock.System.now().toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    @Before
    fun montar() {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(banco)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(
                EventDay(
                    date = epochMillisToAppDate(gasto.timestamp).toString(),
                    total = -18_500L,
                    items = listOf(gasto),
                ),
            )
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
            override suspend fun getUserProfile(): UserProfile = UserProfile(
                id = "u1",
                email = "jvillad1@gmail.com",
                name = "Juan",
                avatarColor = "#FF0000",
                periodCutoffDay = 26,
            )
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        esperarTexto("Carnes y Legumbres")
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun rotulo(periodo: com.jvillada.movi.shared.model.PeriodoFinanciero) =
        nombreDe(periodo).replaceFirstChar { it.uppercase() }

    @Test
    fun `arranca en el periodo de hoy y lo dice con todas las letras`() {
        composeRule.onNodeWithText(rotulo(hoy), useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * **La línea del rango es lo que hace honesto al encabezado.** Sin ella, un «Septiembre» que
     * empieza el 26 de agosto se lee como un error de la app en vez de como el ajuste que el dueño
     * eligió.
     */
    @Test
    fun `explica de que fecha a que fecha va su mes`() {
        val rango = rangoLegibleDe(hoy, ajustes)!!
        composeRule.onNodeWithText(rango, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `la flecha de atras lleva al periodo anterior`() {
        composeRule.onNodeWithContentDescription("Período anterior", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        val anterior = periodoAnterior(hoy)
        composeRule.onNodeWithText(rotulo(anterior), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(rangoLegibleDe(anterior, ajustes)!!, useUnmergedTree = true)
            .assertIsDisplayed()
        // Y el gasto de este mes ya no está: es de otro período.
        composeRule.onAllNodesWithText("Carnes y Legumbres Santa Elena", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .also { check(it.isEmpty()) { "el gasto es del período de hoy, no del anterior" } }
    }

    @Test
    fun `volver adelante devuelve el mes de hoy`() {
        composeRule.onNodeWithContentDescription("Período anterior", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Período siguiente", useUnmergedTree = true).performClick()
        esperarTexto("Carnes y Legumbres")

        composeRule.onNodeWithText(rotulo(hoy), useUnmergedTree = true).assertIsDisplayed()
    }
}

/** El mismo tamaño de pantalla que usan las otras pruebas de esta carpeta (privado por archivo). */
private const val AVD_PERIODO = "w411dp-h731dp-xhdpi"
