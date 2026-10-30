package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.COLORES_CLAROS
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MinNavRail
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
import com.jvillada.movi.ui.components.ds.SuperficieDestacadaKey
import com.jvillada.movi.ui.components.ds.TAG_MARCA_DE_LA_TARJETA
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDeIconoKey
import com.jvillada.movi.ui.components.ds.coloresDelAvatarDelBanco
import com.jvillada.movi.ui.components.fondoDeAgregarEnElRail
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.LocalCargandoElInicio
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.recurrentes.SeccionChecklistDelPeriodo
import com.jvillada.movi.ui.recurrentes.TAG_TARJETA_FALTA_POR_PAGAR
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_SUPERFICIE_DE_LAS_ACCIONES
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.RepositorioDePruebaDeMovimientos
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.components.TAG_AVATAR_DE_MARCA
import com.jvillada.movi.ui.components.ds.TAG_ICONO_EN_CIRCULO
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.porrevisar.PorRevisarScreen
import com.jvillada.movi.ui.transactions.TAG_TARJETA_DE_AVISOS
import com.jvillada.movi.ui.transactions.TransactionsScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 79 · un solo amarillo por vista
 *
 * La revisión visual 7: Inicio a 390, sin scroll, tenía cuatro manchas amarillas (la banda, «Anotar gasto»,
 * «Anotar pago», el círculo de la propuesta); a 1280 se sumaban «Agregar» y el destino del rail; en Pagos la
 * tarjeta «Falta por pagar» usaba el amarillo como superficie; y el avatar «B» de Bancolombia, como
 * identidad. La regla nueva del sistema: **un solo `Boton` principal (amarillo) visible por vista**. Se
 * cuenta sin píxeles: el `Boton` principal y la acción destacada dicen `llevaElAmarillo` en su semántica.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h3200dp-xhdpi")
class UnSoloAmarilloPorVistaTest {

    @get:Rule val composeRule = createComposeRule()

    private val septiembre = PeriodoFinanciero(2026, 9)

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        rule = RecurringRule(id = id, name = nombre, category = "Vivienda", amount = monto, dayOfMonth = 10, type = TransactionType.EXPENSE),
        dueDate = "2026-09-10",
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    /** Un pago vencido («Anotar pago»), propuestas por decidir y cuentas de banco: todo lo que era amarillo. */
    private val inicio = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(
            Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800),
            Account("a2", "Nu", AccountType.SAVINGS, 2_890_000),
        ),
        credits = emptyList<CreditSummary>(),
        cards = emptyList<CardSummary>(),
        upcoming = listOf(pago("r_carro", "Carro", 9_000, -4), pago("r_gym", "Gimnasio", 139_900, 12)),
        ocurrencias = emptyList(),
        periodoActual = septiembre,
        ajustesDePeriodo = PeriodSettings(),
        spentByCategory = mapOf("Mercado" to 392_100L),
        pendingSms = 3,
    )

    private fun montar(ancho: WindowWidthClass = WindowWidthClass.Compact, contenido: @Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalWindowWidthClass provides ancho, LocalCargandoElInicio provides false) {
                    Box(Modifier.fillMaxSize()) { contenido() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun amarillos(): Int {
        fun cuenta(n: androidx.compose.ui.semantics.SemanticsNode): Int =
            (if (n.config.getOrNull(LlevaElAmarilloKey) == true) 1 else 0) + n.children.sumOf(::cuenta)
        return cuenta(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
    }

    @Test
    fun `en Inicio el unico amarillo es Anotar gasto`() {
        montar {
            SduiRenderer(definition = defaultDashboardDefinition(), data = inicio, modifier = Modifier.fillMaxSize(), onNavigate = {})
        }
        assertEquals(1, amarillos(), "un solo amarillo en el Inicio")
        // Los avatares de banco, en la capa 3 con la tinta: nunca la marca.
        val avatares = composeRule.onAllNodesWithTag(TAG_MARCA_DE_LA_TARJETA, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(avatares.isNotEmpty())
        avatares.forEach { assertEquals(TonoDeIcono.Neutro, it.config[TonoDeIconoKey]) }
        assertEquals(COLORES_CLAROS.capa3 to COLORES_CLAROS.texto, coloresDelAvatarDelBanco(COLORES_CLAROS))
    }

    @Test
    fun `a pantalla ancha, la cuadricula va en una superficie y el rail no suma amarillo`() {
        montar(WindowWidthClass.Expanded) {
            SduiRenderer(definition = defaultDashboardDefinition(), data = inicio, modifier = Modifier.fillMaxSize(), onNavigate = {})
        }
        composeRule.onNodeWithTag(TAG_SUPERFICIE_DE_LAS_ACCIONES, useUnmergedTree = true).assertExists()
        assertEquals(1, amarillos())
    }

    @Test
    fun `el rail no lleva amarillo lleno`() {
        montar(WindowWidthClass.Expanded) { MinNavRail(active = NavTab.HOY, onTabSelected = {}, onActualizar = {}) }
        assertEquals(0, amarillos())
        assertEquals(COLORES_CLAROS.tarjeta, fondoDeAgregarEnElRail(COLORES_CLAROS), "«Agregar» va secundario")
    }

    @Test
    fun `en Pagos el unico amarillo es el pago mas urgente, y Falta por pagar es blanca`() {
        val lista = listOf(
            PagoDelPeriodo(ruleId = "r_carro", nombre = "Carro", monto = 9_000, pagado = false, diasParaVencer = -4, vence = "2026-09-06"),
            PagoDelPeriodo(ruleId = "r_arr", nombre = "Arriendo", monto = 1_800_000, pagado = false, diasParaVencer = 2, vence = "2026-09-12"),
            PagoDelPeriodo(ruleId = "r_gym", nombre = "Gimnasio", monto = 139_900, pagado = false, diasParaVencer = 9, vence = "2026-09-19"),
        )
        montar {
            SeccionChecklistDelPeriodo(
                checklist = lista,
                cargando = false,
                pudoLeer = true,
                marcando = emptySet(),
                onConfirmar = { _, _ -> },
                onNoFueEste = { _, _ -> },
                onAnotarMovimiento = {},
                onQuitarLaMarca = {},
                onReintentar = {},
            )
        }
        assertEquals(1, amarillos(), "un solo amarillo en Pagos")
        val tarjeta = composeRule.onNodeWithTag(TAG_TARJETA_FALTA_POR_PAGAR, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(true, tarjeta.config.getOrNull(SuperficieDestacadaKey), "«Falta por pagar» es una superficie blanca destacada")
    }

    // ── Vuelta 82 · Actividad, Por revisar y Patrimonio ──────────────────────────────────────────

    private val hoyTexto = hoyEnAppZone().toString()
    private val bancolombia = Account("acc-4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 3_000_000L, "COP")

    private fun avisoDelBanco(id: String, monto: String, comercio: String, hora: String) = SmsMessage(
        id = id, time = "$hoyTexto $hora", bank = "85540", state = SMS_STATE_PENDING, det = "",
        text = "Bancolombia: Compraste \$$monto,00 en $comercio con tu T.Deb *4057, el 28/10/2026 a las $hora.",
    )

    private val tresAvisos = listOf(
        avisoDelBanco("uber", "18.300", "UBER RIDES", "12:05"),
        avisoDelBanco("farma", "61.900", "FARMATODO LAURELES", "09:40"),
        avisoDelBanco("cafe", "23.400", "JUAN VALDEZ CAFE", "08:12"),
    )

    private val propuestasDeLosTres = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso("uber", leido = ParsedSms(18_300.0, "UBER RIDES", TransactionType.EXPENSE, "Transporte", aprendidoDe = "x")),
            PropuestaDelAviso("farma", leido = ParsedSms(61_900.0, "FARMATODO LAURELES", TransactionType.EXPENSE, "Salud", aprendidoDe = "x")),
            PropuestaDelAviso("cafe", leido = ParsedSms(23_400.0, "JUAN VALDEZ CAFE", TransactionType.EXPENSE, "Comida", aprendidoDe = "x")),
        ),
        categoriasDelDueno = listOf("Comida", "Salud", "Transporte"),
    )

    /** Un día con un almuerzo: Actividad con movimientos (sin ellos, su vacío trae su propia acción). */
    private val unDia = listOf(
        EventDay(
            date = hoyTexto, total = -46_489L,
            items = listOf(
                FinancialEvent(
                    id = "e1", accountId = "acc-4057", type = TransactionType.EXPENSE, amount = 46_489L, category = "Comida",
                    description = "Señor Gol", timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                    reconciliationStatus = com.jvillada.movi.shared.model.ReconciliationStatus.RECONCILED,
                ),
            ),
        ),
    )

    private fun repoDeAvisos(mensajes: List<SmsMessage>, dias: List<EventDay> = emptyList()) = object : RepositorioDePruebaDeMovimientos() {
        override suspend fun getSmsMessages(): List<SmsMessage> = mensajes
        override suspend fun getEventsByDay(): List<EventDay> = dias
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
        override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 1)
        override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja =
            propuestasDeLosTres.copy(propuestas = propuestasDeLosTres.propuestas.filter { p -> mensajes.any { it.id == p.avisoId } })
    }

    @After
    fun soltarElRepositorio() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `en Actividad el unico amarillo es Confirmar N, y el circulo de la tarjeta va en el tono de aviso`() {
        Repositories.sustitutoDePrueba = repoDeAvisos(tresAvisos, unDia)
        montar { TransactionsScreen(onNavigate = {}) }
        esperarTexto("Confirmar 3")
        assertEquals(1, amarillos(), "un solo amarillo en Actividad")
        assertTrue(composeRule.onAllNodesWithText("3 por revisar", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "el número del Inicio")
        val circulo = composeRule.onAllNodes(hasTestTag(TAG_ICONO_EN_CIRCULO) and hasAnyAncestor(hasTestTag(TAG_TARJETA_DE_AVISOS)), useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        assertEquals(TonoDeIcono.Aviso, circulo.config[TonoDeIconoKey], "la campana no es amarilla")
    }

    @Test
    fun `en Por revisar con varios avisos el unico amarillo es el lote`() {
        Repositories.sustitutoDePrueba = repoDeAvisos(tresAvisos)
        montar { PorRevisarScreen(onNavigate = {}) }
        esperarTexto("Confirmar 3")
        assertEquals(1, amarillos(), "solo «Confirmar 3»: cada «Confirmar» de las tarjetas va secundario")
    }

    @Test
    fun `en Por revisar con una sola tarjeta su Confirmar es el amarillo`() {
        Repositories.sustitutoDePrueba = repoDeAvisos(tresAvisos.take(1))
        montar { PorRevisarScreen(onNavigate = {}) }
        esperarTexto("UBER")
        composeRule.waitUntil(timeoutMillis = 5_000) { amarillos() == 1 }
        assertEquals(1, amarillos())
    }

    @Test
    fun `en Patrimonio la B de Bancolombia va en la capa 3, sin amarillo`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 1)
        }
        montar { AccountsScreen(onNavigate = {}) }
        esperarTexto("Bancolombia Ahorros 4057")
        assertEquals(0, amarillos(), "Patrimonio no tiene acción amarilla")
        val avatares = composeRule.onAllNodesWithTag(TAG_AVATAR_DE_MARCA, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(avatares.isNotEmpty())
        avatares.forEach { assertEquals(TonoDeIcono.Neutro, it.config.getOrNull(TonoDeIconoKey), "la «B» en la capa 3 con la tinta") }
    }
}
