package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AnotarElPagoDeLaOcurrencia
import com.jvillada.movi.shared.model.EstadoDeLaFila
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PagoDeLaOcurrenciaAnotado
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.AccionDeLaPropuesta
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.TipoDePropuesta
import com.jvillada.movi.ui.dashboard.proximoPagoDelInicio
import com.jvillada.movi.ui.dashboard.propuestasDelInicio
import com.jvillada.movi.ui.recurrentes.ETIQUETA_ANOTAR
import com.jvillada.movi.ui.recurrentes.SeccionChecklistDelPeriodo
import com.jvillada.movi.ui.recurrentes.hojaParaAnotar
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # «Anotar pago» salda la factura — de la fila al guardado (vuelta 43)
 *
 * El día del dueño: «Pago vencido · hace 23 días» → «Anotar pago» abría un Agregar genérico (la
 * categoría «Otros», la cuenta «Más usada»: Nu, la tarjeta) y al guardar quedaba un gasto suelto. La
 * fila seguía en «Movi no encontró el movimiento de este pago» y «Te quedan» bajaba $1.800.000 otra
 * vez.
 *
 * Acá, con la hoja de verdad y un repositorio falso, **desde cada lugar que tiene el botón** —Pagos,
 * «Próximo pago» / «Pago vencido» de Inicio y «Movi propone hoy»—: la hoja abre en modo pago («Pago
 * de Arriendo · octubre», el monto, la categoría y el título de la regla), propone una cuenta con la
 * lógica de Gasto aunque la regla no tenga (vuelta 72: antes la pedía cada mes), y al guardar manda el movimiento **con la
 * ocurrencia que paga**, nunca un `postEvent` suelto. Lo que eso hace con la fila y con «Te quedan» lo
 * prueban las del server (`AnotarPagoSaldaLaFacturaTest` en `:server`), con la regla de verdad.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-xhdpi")
class AnotarPagoSaldaLaFacturaTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val nu = Account("nu", "Nu", AccountType.CREDIT_CARD, 1_200_000)
    private val efectivo = Account("e1", "Efectivo", AccountType.CASH, 200_000)

    /** La regla de la demo: «Otros», sin cuenta, del 5. */
    private val arriendo = RecurringRule(
        id = "rr-arriendo", name = "Arriendo", category = "Otros", amount = 1_800_000,
        dayOfMonth = 5, type = TransactionType.EXPENSE,
    )
    private val octubre = PeriodoFinanciero(2026, 10)

    /** Lo que leen Pagos e Inicio el 28-oct: el arriendo vencido hace 23 días, sin movimiento. */
    private val datos = DashboardData(
        upcoming = listOf(
            UpcomingPayment(rule = arriendo, dueDate = "2026-10-05", daysUntil = -23, status = PaymentStatus.OVERDUE),
        ),
        ocurrencias = listOf(
            OccurrenceState(ruleId = arriendo.id, period = "2026-10", dueDate = "2026-10-05", occurred = false, periodoDelDueno = "2026-10"),
        ),
        periodoActual = octubre,
        ajustesDePeriodo = PeriodSettings(),
    )

    private val filaDelArriendo get() = checklistDelPeriodo(datos.upcoming!!, datos.ocurrencias!!, octubre, PeriodSettings()).single()

    private val pagos = mutableListOf<Pair<String, AnotarElPagoDeLaOcurrencia>>()
    private val sueltos = mutableListOf<FinancialEvent>()
    private var guardo = false

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun conElRepositorio() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia, nu, efectivo)
            override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
                sueltos += event
                return event
            }
            override suspend fun anotarElPagoDeLaOcurrencia(ruleId: String, pedido: AnotarElPagoDeLaOcurrencia): PagoDeLaOcurrenciaAnotado {
                pagos += ruleId to pedido
                return PagoDeLaOcurrenciaAnotado(
                    movimiento = pedido.movimiento,
                    ocurrencia = RecurringOccurrence(ruleId, pedido.periodo ?: "", pedido.movimiento.id),
                )
            }
        }
    }

    // ── Los tres lugares ─────────────────────────────────────────────────────

    @Test
    fun desde_Pagos_anotar_pago_salda_la_factura() {
        conElRepositorio()
        var hoja by mutableStateOf<Screen.QuickAdd?>(null)
        composeRule.setContent {
            MoviTheme {
                val abierta = hoja
                if (abierta == null) {
                    Box(Modifier.fillMaxSize()) {
                        SeccionChecklistDelPeriodo(
                            checklist = listOf(filaDelArriendo),
                            cargando = false,
                            pudoLeer = true,
                            marcando = emptySet(),
                            onConfirmar = { _, _ -> },
                            onNoFueEste = { _, _ -> },
                            // El mismo `onAnotarMovimiento` que el tablero de Pagos.
                            onAnotarMovimiento = { p -> hoja = hojaParaAnotar(p) as? Screen.QuickAdd },
                            onQuitarLaMarca = {},
                            onReintentar = {},
                        )
                    }
                } else {
                    LaHoja(abierta)
                }
            }
        }
        composeRule.waitForIdle()
        val botones = composeRule.onAllNodes(hasAnyDescendant(hasText(ETIQUETA_ANOTAR)) and hasClickAction(), useUnmergedTree = true)
        assertTrue(botones.fetchSemanticsNodes().isNotEmpty(), "la fila sin movimiento ofrece «Anotar pago»")
        botones[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        pagarYComprobar()
    }

    @Test
    fun desde_el_Proximo_pago_de_Inicio_anotar_pago_salda_la_factura() {
        val proximo = assertNotNull(proximoPagoDelInicio(datos))
        assertEquals("Pago vencido · hace 23 días", proximo.titulo)
        assertEquals(ETIQUETA_ANOTAR, proximo.accion)
        montarLaHoja(assertIs<Screen.QuickAdd>(proximo.alTocar))

        pagarYComprobar()
    }

    @Test
    fun desde_Movi_propone_anotar_pago_salda_la_factura() {
        val propuesta = propuestasDelInicio(datos).first { it.tipo == TipoDePropuesta.PAGO }
        assertEquals(ETIQUETA_ANOTAR, propuesta.accion)
        val destino = assertIs<AccionDeLaPropuesta.Ir>(propuesta.alTocar).pantalla
        montarLaHoja(assertIs<Screen.QuickAdd>(destino))

        pagarYComprobar()
    }

    /** Con la regla con cuenta, la hoja ya la trae puesta y dice por qué: «La de la regla». */
    @Test
    fun la_cuenta_de_la_regla_viene_puesta() {
        val conCuenta = datos.copy(
            upcoming = listOf(
                UpcomingPayment(rule = arriendo.copy(accountId = bancolombia.id), dueDate = "2026-10-05", daysUntil = -23, status = PaymentStatus.OVERDUE),
            ),
        )
        montarLaHoja(assertIs<Screen.QuickAdd>(assertNotNull(proximoPagoDelInicio(conCuenta)).alTocar))
        composeRule.onNodeWithText("La de la regla", useUnmergedTree = true).assertIsDisplayed()
        tocar("Guardar movimiento")
        composeRule.waitUntil(5_000) { pagos.isNotEmpty() }
        assertEquals(bancolombia.id, pagos.single().second.movimiento.accountId)
        assertTrue(sueltos.isEmpty())
    }

    // ── Andamio ──────────────────────────────────────────────────────────────

    @androidx.compose.runtime.Composable
    private fun LaHoja(abierta: Screen.QuickAdd) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (!guardo) {
                    QuickAddScreen(
                        onDismiss = {},
                        onSaved = { guardo = true },
                        presetAccountId = abierta.presetAccountId,
                        presetNota = abierta.presetNota,
                        presetMonto = abierta.presetMonto,
                        presetCategoria = abierta.presetCategoria,
                        presetFecha = abierta.presetFecha,
                        presetEsIngreso = abierta.presetEsIngreso,
                        presetDeudaId = abierta.presetDeudaId,
                        presetPago = abierta.presetPago,
                    )
                }
            }
        }
    }

    private fun montarLaHoja(abierta: Screen.QuickAdd) {
        conElRepositorio()
        composeRule.setContent { MoviTheme { LaHoja(abierta) } }
        composeRule.waitForIdle()
    }

    /** La hoja en modo pago, la cuenta elegida, Guardar: el pago sale con su ocurrencia. */
    private fun pagarYComprobar() {
        composeRule.waitUntil(5_000) { hay("Pago de Arriendo · octubre") && hay(bancolombia.name) }
        // El encabezado dice de qué es el pago; el monto, la categoría y el título son los de la regla.
        composeRule.onNodeWithTag(TAG_ENCABEZADO_DEL_PAGO, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Pago de Arriendo · octubre").assertIsDisplayed()
        assertTrue(hay("1.800.000"), "el monto de la regla")
        assertTrue(hay("Otros"), "la categoría de la regla")
        assertTrue(hay("Arriendo"), "el título es el nombre de la regla")
        assertTrue(hay("Hoy"), "la fecha es hoy")
        // Vuelta 72: la regla no tiene cuenta y la hoja igual propone una (la de Gasto, editable): no
        // pide «Falta la cuenta» cada mes.
        assertTrue(!hay("Falta la cuenta"), "con una cuenta propuesta no falta nada")
        assertTrue(!hay(ELIGE_LA_CUENTA), "ya no pide elegirla")

        // Se guarda con la propuesta (la primera cuenta de dinero: sin costumbre, «Por defecto»).
        tocar("Guardar movimiento")
        composeRule.waitUntil(5_000) { pagos.isNotEmpty() }

        val (ruleId, pedido) = pagos.single()
        assertEquals(arriendo.id, ruleId)
        assertEquals("2026-10", pedido.periodo, "la ocurrencia que salda: la de octubre")
        val movimiento = pedido.movimiento
        assertEquals(TransactionType.EXPENSE, movimiento.type)
        assertEquals(1_800_000L, movimiento.amount)
        assertEquals("Otros", movimiento.category)
        assertEquals("Arriendo", movimiento.description)
        assertEquals(bancolombia.id, movimiento.accountId)
        assertTrue(sueltos.isEmpty(), "nunca un gasto suelto que no salde la fila")
        composeRule.waitUntil(5_000) { guardo }

        // Lo que el server contesta después: la fila pasa a «Pagado» con ese movimiento.
        val leida = checklistDelPeriodo(
            datos.upcoming!!,
            listOf(
                OccurrenceState(
                    ruleId = arriendo.id, period = "2026-10", dueDate = "2026-10-05", occurred = true,
                    eventId = movimiento.id, montoDelPago = movimiento.amount, monedaDelPago = "COP",
                ),
            ),
            octubre,
            PeriodSettings(),
        ).single()
        assertEquals(EstadoDeLaFila.LISTO, leida.estado)
        assertEquals(movimiento.id, leida.eventId)
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Bajo Robolectric `performClick` no llega al composable: ver [HojaAgregarEligeLaCuentaTest]. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
