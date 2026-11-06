package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.CuotaPagada
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.LaCuotaQueCambia
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TipoDeCuota
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.AccountDetailScreen
import com.jvillada.movi.ui.components.ds.TAG_UNOS_DE_LA_CIFRA
import com.jvillada.movi.ui.credits.RepositorioDeLaDeuda
import com.jvillada.movi.ui.credits.TAG_CUOTA_QUE_CAMBIA
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.LocalCargandoElInicio
import com.jvillada.movi.ui.plan.PlanScreen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.recurrentes.TAG_FILA_DE_FACTURA
import com.jvillada.movi.ui.sdui.ProximoPagoSection
import com.jvillada.movi.ui.sdui.TAG_PROXIMO_PAGO
import com.jvillada.movi.ui.sdui.TAG_WIDGET_FALTA_POR_PAGAR
import com.jvillada.movi.ui.sdui.WidgetFaltaPorPagar
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 134 · la cuota que cambia, en pantalla y con la letra grande
 *
 * - **M-2**: «Próximo pago» y el widget «Falta por pagar» del Inicio dicen «unos» delante de la cuota de un
 *   crédito que cambia (la misma regla que la fila de Pagos), y una cuota fija sigue sin nada.
 * - **M-9**: a 360 dp con la letra del teléfono al 130 % (×1,456, `ESCALA_DEL_TELEFONO_AL_130`) no se corta
 *   la fila de Pagos («unos» + monto, y «Como la de octubre» debajo) ni las líneas de la ficha del préstamo.
 *
 * «Nada se corta» es lo de `ElTextoAl130NoSeCortaTest`: ningún renglón con «…», ningún texto con más
 * renglones de los que le caben, ningún renglón más ancho que su caja y ninguna caja fuera de la pantalla.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class Vuelta134EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy: LocalDate = Clock.System.todayIn(TimeZone.of("America/Bogota"))

    private val octubre = CuotaPagada(1_262_300, "2026-10")

    /** La cuota de un crédito que cambia, como la manda el server (`virtualRuleFor` con la última pagada). */
    private val cuotaQueCambia = RecurringRule(
        id = "credit_l9695", name = "Cuota Libre inversión 9695", category = "Cuota de crédito", amount = 1_262_300,
        dayOfMonth = hoy.dayOfMonth, type = TransactionType.EXPENSE, cuotaQueCambia = LaCuotaQueCambia(octubre),
    )
    private val cuotaFija = RecurringRule(
        id = "credit_v8761", name = "Cuota Vehículo 8761", category = "Cuota de crédito", amount = 4_101_123,
        dayOfMonth = hoy.dayOfMonth, type = TransactionType.EXPENSE,
    )

    private fun vence(r: RecurringRule, dias: Int = 0) =
        UpcomingPayment(r, LocalDate.fromEpochDays(hoy.toEpochDays() + dias).toString(), dias, PaymentStatus.DUE_SOON)

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
            FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 6_800_000, egresos = 0)
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
            DashboardSummary(spentByCategory = emptyMap(), gastoVariablePorDia = emptyMap())
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "u@local", name = "Juan", avatarColor = "#000000", periodCutoffDay = 1)
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = listOf(vence(cuotaQueCambia), vence(cuotaFija))
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getGastoDelDiaADia(): GastoDelDiaADia = GastoDelDiaADia(porDia = 10_000L)
        override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 9_000_000))
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
    }

    @Before
    fun preparar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = Repo()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private fun montar(letraGrande: Boolean = true, pantalla: @Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            val escala = if (letraGrande) ESCALA_DEL_TELEFONO_AL_130 else base.fontScale
            CompositionLocalProvider(LocalDensity provides Density(base.density, escala)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { pantalla() } }
            }
        }
        composeRule.waitForIdle()
    }

    private fun conTag(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun esperarTag(tag: String) = composeRule.waitUntil(timeoutMillis = 5_000) { conTag(tag).isNotEmpty() }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun textosDentroDe(raiz: SemanticsNode): List<SemanticsNode> =
        (if (raiz.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) listOf(raiz) else emptyList()) +
            raiz.children.flatMap(::textosDentroDe)

    private fun todoElTexto(raiz: SemanticsNode): String = textosDentroDe(raiz).joinToString(" ") { texto(it) }

    /** Lo que se corta o se sale, dentro de [raices] (la regla de `ElTextoAl130NoSeCortaTest`). */
    private fun nadaSeCorta(donde: String, raices: List<SemanticsNode>) {
        assertTrue(raices.isNotEmpty(), "No encontré $donde")
        val anchoDePantalla = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode().size.width + 0.5f
        val fallas = raices.flatMap(::textosDentroDe).mapNotNull { nodo ->
            val r = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            val d = r.firstOrNull() ?: return@mapNotNull null
            val ancho = nodo.size.width + 0.5f
            val limites = nodo.boundsInRoot
            val problema = when {
                (0 until d.lineCount).any { d.isLineEllipsized(it) } -> "se corta con «…»"
                d.multiParagraph.didExceedMaxLines -> "no le caben los renglones"
                (0 until d.lineCount).any { d.getLineRight(it) - d.getLineLeft(it) > ancho } -> "es más ancho que su caja"
                limites.left < -0.5f || limites.right > anchoDePantalla -> "se sale de la pantalla"
                else -> null
            }
            problema?.let { "«${texto(nodo)}» $it" }
        }
        assertTrue(fallas.isEmpty(), "Con la letra a ×$ESCALA_DEL_TELEFONO_AL_130 a 360 dp, en $donde:\n" + fallas.joinToString("\n") { "  $it" })
    }

    private fun inicio(vararg pagos: UpcomingPayment) = DashboardData(
        upcoming = pagos.toList(),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(hoy.year, hoy.monthNumber),
        ajustesDePeriodo = PeriodSettings(),
    )

    // ── M-2 · el Inicio dice «unos» ────────────────────────────────────────

    @Test
    fun `proximo pago dice unos en la cuota que cambia, y no se corta con la letra grande`() {
        montar {
            CompositionLocalProvider(LocalCargandoElInicio provides false) {
                Column { ProximoPagoSection(inicio(vence(cuotaQueCambia), vence(cuotaFija, dias = 3)), onNavigate = {}) }
            }
        }
        val tarjeta = conTag(TAG_PROXIMO_PAGO).single()
        assertEquals(1, conTag(TAG_UNOS_DE_LA_CIFRA).size, todoElTexto(tarjeta))
        assertTrue("unos" in todoElTexto(tarjeta) && "\$1.262.300" in todoElTexto(tarjeta), todoElTexto(tarjeta))
        nadaSeCorta("«Próximo pago»", listOf(tarjeta))
    }

    @Test
    fun `proximo pago de una cuota fija no dice unos`() {
        montar(letraGrande = false) {
            CompositionLocalProvider(LocalCargandoElInicio provides false) {
                Column { ProximoPagoSection(inicio(vence(cuotaFija)), onNavigate = {}) }
            }
        }
        assertEquals(0, conTag(TAG_UNOS_DE_LA_CIFRA).size)
    }

    @Test
    fun `el widget Falta por pagar dice unos solo en la fila de la cuota que cambia, y no se corta`() {
        montar {
            CompositionLocalProvider(LocalCargandoElInicio provides false) {
                Column { WidgetFaltaPorPagar(inicio(vence(cuotaQueCambia), vence(cuotaFija, dias = 3)), onNavigate = {}) }
            }
        }
        val widget = conTag(TAG_WIDGET_FALTA_POR_PAGAR).single()
        assertEquals(1, conTag(TAG_UNOS_DE_LA_CIFRA).size, todoElTexto(widget))
        // Revisión de la vuelta 134 (m-9): a 360 dp con la letra a ×1,456.
        nadaSeCorta("el widget «Falta por pagar»", listOf(widget))
    }

    /** Revisión de la vuelta 134 (m-9): la campana con «unos», a 360 dp con la letra a ×1,456. */
    @Test
    fun `la campana dice unos en la cuota que cambia y no se corta`() {
        val filas = com.jvillada.movi.ui.dashboard.notificationRows(inicio(vence(cuotaQueCambia), vence(cuotaFija)))
        montar { com.jvillada.movi.ui.notifications.NotificationsPanel(filas, onDismiss = {}, onRowClick = {}) }
        esperarTag("${com.jvillada.movi.ui.notifications.TAG_AVISO_DE_LA_CAMPANA}:0")
        assertEquals(1, conTag(TAG_UNOS_DE_LA_CIFRA).size, "solo la cuota que cambia")
        val avisos = filas.indices.flatMap { conTag("${com.jvillada.movi.ui.notifications.TAG_AVISO_DE_LA_CAMPANA}:$it") }
        nadaSeCorta("los avisos de la campana", avisos)
    }

    /** Revisión de la vuelta 134 (m-1): «Lo que viene» y el período siguiente también dicen «unos». */
    @Test
    fun `lo que viene dice unos en la cuota que cambia`() {
        val cambia = com.jvillada.movi.shared.model.PagoDelPeriodo(
            ruleId = cuotaQueCambia.id, nombre = cuotaQueCambia.name, monto = 1_262_300, pagado = false, diasParaVencer = 9,
            cuotaQueCambia = LaCuotaQueCambia(octubre),
        )
        assertEquals("unos \$1.262.300", com.jvillada.movi.ui.plan.montoDeLoQueVence(cambia).first)
        assertEquals("\$1.262.300", com.jvillada.movi.ui.plan.montoDeLoQueVence(cambia.copy(cuotaQueCambia = null)).first)
    }

    /** Revisión de la vuelta 134 (m-3): «Lo que paga otra persona» dice la cuota de este mes, con «unos». */
    @Test
    fun `lo que paga otra persona dice la cuota de este mes con unos`() {
        val delPapa = CreditSummary(
            account = Account("hip-papa", "Hipotecario del papá", AccountType.LOAN, balance = 90_000_000L),
            terms = CreditTerms(
                accountId = "hip-papa", bank = "Davivienda", principal = 120_000_000L, rateEa = 12.0, termMonths = 180,
                installment = 1_200_000L, dayOfMonth = 5, startDate = "2020-01-10", paidBy = "Papá",
                tipoDeCuota = TipoDeCuota.UVR,
            ),
            paidPct = 0.3,
            ultimaCuotaPagada = octubre,
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getEspacios(): List<com.jvillada.movi.shared.model.Espacio> = emptyList()
            override suspend fun getCredits(): List<CreditSummary> = listOf(delPapa)
        }
        montar { com.jvillada.movi.ui.espacios.EspaciosScreen(onNavigate = {}) }
        esperarTag(com.jvillada.movi.ui.espacios.TAG_LO_QUE_PAGA_OTRA_PERSONA)
        val seccion = conTag(com.jvillada.movi.ui.espacios.TAG_LO_QUE_PAGA_OTRA_PERSONA).single()
        assertTrue("\$1.262.300" in todoElTexto(seccion), todoElTexto(seccion))
        assertTrue("\$1.200.000" !in todoElTexto(seccion), "nunca la pactada: " + todoElTexto(seccion))
        assertEquals(1, conTag(TAG_UNOS_DE_LA_CIFRA).size)
    }

    // ── M-9 · Pagos y la ficha a 360 dp con la letra a ×1,456 ──────────────

    @Test
    fun `la fila de Pagos con unos y la de octubre no se corta`() {
        montar { PlanScreen(onNavigate = {}, segmento = SEGMENTO_PAGOS) }
        esperarTag(TAG_FILA_DE_FACTURA)
        val filas = conTag(TAG_FILA_DE_FACTURA)
        val laQueCambia = filas.single { "Libre inversión" in todoElTexto(it) }
        assertTrue("unos" in todoElTexto(laQueCambia), todoElTexto(laQueCambia))
        assertTrue("Como la de octubre" in todoElTexto(laQueCambia), todoElTexto(laQueCambia))
        nadaSeCorta("la fila de la cuota que cambia", listOf(laQueCambia))
    }

    @Test
    fun `las lineas de la ficha de un prestamo que cambia no se cortan`() {
        val prestamo = CreditSummary(
            account = Account("l9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_000_000L),
            terms = CreditTerms(
                accountId = "l9695", bank = "Bancolombia", principal = 50_000_000L, rateEa = 18.0, termMonths = 72,
                installment = 1_200_000L, dayOfMonth = 5, startDate = "2025-01-10",
                tipoDeCuota = TipoDeCuota.TASA_VARIABLE, puntosSobreElIndice = 8.5,
                alCambiarLaTasa = com.jvillada.movi.shared.model.AlCambiarLaTasa.CAMBIA_LA_CUOTA,
            ),
            paidPct = 0.2,
            ultimaCuotaPagada = octubre,
        )
        Repositories.sustitutoDePrueba = RepositorioDeLaDeuda(
            creditos = listOf(prestamo),
            perfil = UserProfile(id = "u", email = "u@local", name = "Juan", avatarColor = "#000000", periodCutoffDay = 1),
        )
        montar { AccountDetailScreen(onNavigate = {}, accountId = prestamo.account.id, group = prestamo.account.type.group) }
        esperarTag(TAG_CUOTA_QUE_CAMBIA)
        // Revisión de la vuelta 134 (m-2): la cifra de la fila «Cuota» también dice «unos» (la de este período,
        // la de noviembre, todavía no se pagó: la última fue la de octubre).
        val cifra = conTag(com.jvillada.movi.ui.credits.TAG_CIFRA_DE_LA_CUOTA_EN_LA_FICHA).single()
        assertTrue("unos" in todoElTexto(cifra) && "\$1.262.300" in todoElTexto(cifra), todoElTexto(cifra))
        nadaSeCorta("la fila «Cuota» de la ficha", listOf(cifra))
        val lineas = conTag(TAG_CUOTA_QUE_CAMBIA)
        assertTrue(lineas.size >= 3, lineas.joinToString(" | ") { texto(it) })
        nadaSeCorta("las líneas de la cuota que cambia", lineas)
    }
}
