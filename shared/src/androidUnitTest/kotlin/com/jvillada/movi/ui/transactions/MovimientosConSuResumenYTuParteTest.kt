package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.ui.components.ds.TAG_CIFRA_CON_ROTULO
import com.jvillada.movi.ui.quickadd.todayIsoInAppZone
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Movimientos en el teléfono: su resumen, y tu parte primero (vuelta de diseño 30)
 *
 * - A 390 dp, Entró · Salió · Neto debajo del selector del período, con la MISMA [resumenDelPanel]
 *   del panel de escritorio; tocarlo abre «En qué se va». Sin «$0» mientras carga, y no va con un
 *   filtro que recorta la lista.
 * - En un gasto repartido, la cifra de la fila y del detalle es tu parte («−$44.300»), con «de
 *   $88.600 · con Caro» debajo; el detalle dice lo que salió de la cuenta.
 * - «Mercado / Mercado» muestra la cuenta en el título.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class MovimientosConSuResumenYTuParteTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 1_000_000L, "COP")
    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val casa = Espacio(
        id = "esp_casa", nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private fun evento(id: String, descripcion: String, monto: Long, categoria: String, tipo: TransactionType = TransactionType.EXPENSE) =
        FinancialEvent(
            id = id, accountId = nu.id, type = tipo, amount = monto, category = categoria, description = descripcion,
            timestamp = ahora - id.hashCode().mod(1000), reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
        )

    private val sueldo = evento("e_sueldo", "Nómina", 2_500_000, "Salario", TransactionType.INCOME)
    private val repartido = evento("e_mercado", "Carulla", 88_600, "Comida").copy(parteAjena = 44_300, espacioId = casa.id)
    private val mercado = evento("e_merc", "Mercado", 31_000, "Mercado")

    /** `null` = la lectura de los movimientos no contesta todavía. */
    private var dias: List<EventDay>? = listOf(EventDay(HOY, 0L, listOf(sueldo, repartido, mercado)))

    private inner class Repo : RepositorioDePruebaDeMovimientos() {
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado", periodCutoffDay = 1)
        override suspend fun getAccounts(): List<Account> = listOf(nu)
        override suspend fun getEventsByDay(): List<EventDay> {
            while (dias == null) kotlinx.coroutines.delay(50)
            return dias!!
        }
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), 0)
        override suspend fun getTuParteDeOtros(): List<FinancialEvent> = emptyList()
        override suspend fun getEspacios(): List<Espacio> = listOf(casa)
        override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
    }

    private val navegaciones = mutableListOf<Screen>()

    private fun montar(filtroInicial: com.jvillada.movi.shared.model.FiltroDeMovimientos? = null) {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    TransactionsScreen(onNavigate = { navegaciones += it }, filtroInicial = filtroInicial)
                }
            }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
        SaldosOcultosStore.poner(false)
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun enLaFranja(texto: String) = composeRule.onAllNodes(
        hasText(texto) and hasAnyAncestor(hasTestTag(TAG_RESUMEN_EN_EL_TELEFONO)), useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(condicion: () -> Boolean) = composeRule.waitUntil(timeoutMillis = 5_000, condition = condicion)

    @Test
    fun `en el telefono la franja dice lo mismo que el panel y lleva a En que se va`() {
        montar()
        esperar { hay("Carulla") }
        val ajustes = PeriodSettings()
        val esperado = resumenDelPanel(dias!!, periodoActual(ahora, ajustes), ajustes, LocalDate.parse(HOY), mapOf(nu.id to nu.type))
        // Solo tu parte del repartido: 44.300 + 31.000.
        assertEquals(75_300, esperado.salio)
        assertEquals(2_500_000, esperado.entro)

        esperar { enLaFranja("Entró") }
        // Vuelta de diseño 32: las tres en la misma forma —enteras si caben, abreviadas si una no
        // cabe—; antes decía «+$2,5M · $75.300 · +$2,4M». Salió es solo tu parte.
        val enteras = listOf("+\$2.500.000", "\$75.300", "+\$2.424.700")
        val abreviadas = listOf("+\$2,5M", "\$75\u00A0mil", "+\$2,4M")
        assertTrue(enteras.all(::enLaFranja) || abreviadas.all(::enLaFranja), "las tres en la misma forma")
        assertEquals(3, composeRule.onAllNodes(hasTestTag(TAG_CIFRA_CON_ROTULO) and hasAnyAncestor(hasTestTag(TAG_RESUMEN_EN_EL_TELEFONO)), useUnmergedTree = true).fetchSemanticsNodes().size)

        composeRule.onNodeWithTag(TAG_RESUMEN_EN_EL_TELEFONO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(navegaciones.any { it is Screen.EnQueSeVa }, "$navegaciones")
    }

    @Test
    fun `mientras carga la franja reserva su lugar sin decir $0`() {
        dias = null
        montar()
        esperar { composeRule.onAllNodesWithTag(TAG_RESUMEN_EN_EL_TELEFONO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertFalse(enLaFranja("\$0"))
        assertFalse(hay("Entró"))
        dias = listOf(EventDay(HOY, 0L, listOf(sueldo, repartido, mercado)))
        esperar { enLaFranja("Entró") }
    }

    @Test
    fun `con los saldos ocultos la franja no dice ninguna cifra`() {
        SaldosOcultosStore.poner(true)
        montar()
        esperar { enLaFranja("Entró") }
        assertFalse(enLaFranja("\$75.300"))
        assertFalse(enLaFranja("+\$2,5M"))
    }

    @Test
    fun `con un filtro que recorta la lista no hay franja`() {
        assertTrue(muestraElResumenEnElTelefono(conPanel = false, hayFiltro = false, busqueda = ""))
        assertFalse(muestraElResumenEnElTelefono(conPanel = true, hayFiltro = false, busqueda = ""), "en pantalla ancha lo dice el panel")
        assertFalse(muestraElResumenEnElTelefono(conPanel = false, hayFiltro = true, busqueda = ""))
        assertFalse(muestraElResumenEnElTelefono(conPanel = false, hayFiltro = false, busqueda = "uber"))
        montar(filtroInicial = com.jvillada.movi.shared.model.FiltroDeMovimientos(rotulo = "Comida", categoria = "Comida"))
        esperar { hay("Carulla") }
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_RESUMEN_EN_EL_TELEFONO, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `la fila de un gasto repartido dice tu parte y debajo de cuanto y con quien`() {
        montar()
        esperar { hay("de \$88.600 · con Caro") }
        assertTrue(hay("−\$44.300"))
        assertFalse(hay("−\$88.600"), "la cifra grande ya no es el total")
        assertFalse(hay("Tu parte \$44.300"))
    }

    @Test
    fun `el detalle de un gasto repartido pone tu parte y dice lo que salio de la cuenta`() {
        montar()
        esperar { hay("Carulla") }
        composeRule.onAllNodes(
            hasTestTag(TAG_FILA_DE_MOVIMIENTO_SUELTO) and androidx.compose.ui.test.hasAnyDescendant(hasText("Carulla")),
            useUnmergedTree = true,
        )[0].performSemanticsAction(SemanticsActions.OnClick)
        esperar { composeRule.onAllNodesWithTag(TAG_TOTAL_DEL_REPARTIDO, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(hay("Salieron \$88.600 de Nu"), "el saldo sigue siendo el de toda la plata que salió")
        val enElMonto = hasTestTag(TAG_MONTO_DEL_MOVIMIENTO) or hasAnyAncestor(hasTestTag(TAG_MONTO_DEL_MOVIMIENTO))
        assertTrue(
            composeRule.onAllNodes(hasText("−\$44.300") and enElMonto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "la cifra del detalle es tu parte",
        )
        assertTrue(composeRule.onAllNodes(hasText("−\$88.600") and enElMonto, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `Mercado sobre Mercado lleva la cuenta en el titulo`() {
        montar()
        esperar { hay("Carulla") }
        val fila = composeRule.onAllNodes(
            hasTestTag(TAG_FILA_DE_MOVIMIENTO_SUELTO) and androidx.compose.ui.test.hasAnyDescendant(hasText("Nu")) and
                androidx.compose.ui.test.hasAnyDescendant(hasText("Mercado")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals(1, fila.size)
        assertFalse(hay("Mercado · Nu"), "el subtítulo no repite la categoría")
    }
}

private val HOY: String = todayIsoInAppZone()
