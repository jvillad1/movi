package com.jvillada.movi.ui.analisis

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FiltroDeMovimientos
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.transactions.CHIP_GASTOS
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «En qué se va» en pantalla (Ola 4 · Ver la plata)
 *
 * Las cuentas están probadas en `:core` (`AnalisisDelPeriodoTest`). Acá, montada en un teléfono de
 * 360 dp: que la pantalla pinte el gráfico y la lista, que el selector Categoría · Comercio · Cuenta
 * cambie la lista, y que tocar una fila lleve a Movimientos con el filtro de esa fila.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class EnQueSeVaScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val perfil = UserProfile(id = "u1", email = "a@b.c", name = "Juan", avatarColor = "#FF0000", periodCutoffDay = 1)
    private val hoy = LocalDate(2026, 10, 15)
    private val cuentas = listOf(
        Account("ahorros", "Bancolombia", AccountType.SAVINGS, 5_000_000L),
        Account("tarjeta", "Visa", AccountType.CREDIT_CARD, 300_000L),
    )

    private var n = 0
    private fun mov(dia: String, monto: Long, categoria: String, comercio: String? = null, cuenta: String = "ahorros", tipo: TransactionType = TransactionType.EXPENSE) =
        FinancialEvent(
            id = "e${n++}", accountId = cuenta, type = tipo, amount = monto, category = categoria,
            description = comercio ?: categoria, merchant = comercio,
            timestamp = LocalDate.parse(dia).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        )

    private val eventos = listOf(
        mov("2026-08-01", 5_000_000L, OPENING_CATEGORY, tipo = TransactionType.INCOME),
        mov("2026-09-03", 200_000L, "Comida", "RAPPI*LAS DOCE"),
        mov("2026-10-02", 450_000L, "Comida", "RAPPI*LAS DOCE"),
        mov("2026-10-05", 120_000L, "Transporte", "Uber", cuenta = "tarjeta"),
        mov("2026-10-09", 80_000L, "Comida", "Carnes Santa Elena"),
        // Un traspaso no es gasto: no puede aparecer ni sumar.
        mov("2026-10-10", 1_000_000L, TRANSFER_CATEGORY),
    )

    private val navegado = mutableListOf<Screen>()

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = perfil
            override suspend fun getAccounts(): List<Account> = cuentas
            override suspend fun getEventsByDay(): List<EventDay> =
                eventos.groupBy { it.timestamp }.map { (_, items) -> EventDay(date = "x", total = 0L, items = items) }
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    EnQueSeVaScreen(onNavigate = { navegado += it }, hoy = hoy)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { filas().isNotEmpty() }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun filas() = composeRule.onAllNodesWithTag(TAG_FILA_DEL_ANALISIS, useUnmergedTree = true).fetchSemanticsNodes()

    /** El nombre de cada fila, en orden. */
    private fun nombresDeLasFilas(): List<String> =
        composeRule.onAllNodesWithTag(TAG_NOMBRE_DE_LA_FILA, useUnmergedTree = true).fetchSemanticsNodes()
            .map { nodo -> nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `pinta el grafico, el total y la lista por categoria`() {
        montar()
        composeRule.onNodeWithTag(TAG_GRAFICA_DEL_ANALISIS, useUnmergedTree = true).assertExists()
        assertTrue(hay("Octubre 2026"))
        // 450.000 + 120.000 + 80.000: el traspaso no suma.
        assertTrue(hay(formatCOP(650_000L)))
        assertEquals(listOf("Comida", "Transporte"), nombresDeLasFilas())
        // Septiembre a la misma altura (15 días): los $200.000 del 3.
        assertTrue(hay("a esta altura del período pasado"))
    }

    @Test
    fun `el selector cambia la lista a comercios y a cuentas`() {
        montar()
        composeRule.onNode(hasClickAction() and hasText("Comercio"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf("Rappi", "Uber", "Carnes Santa Elena"), nombresDeLasFilas())

        composeRule.onNode(hasClickAction() and hasText("Cuenta"))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf("Bancolombia", "Visa"), nombresDeLasFilas())
    }

    @Test
    fun `tocar una fila abre Movimientos con el filtro de esa fila`() {
        montar()
        composeRule.onAllNodesWithTag(TAG_FILA_DEL_ANALISIS, useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(
            Screen.Transactions(
                chipInicial = CHIP_GASTOS,
                periodoInicial = "2026-10",
                filtro = FiltroDeMovimientos("Comida", categoria = "Comida"),
            ),
            navegado.last(),
        )
    }
}
