package com.jvillada.movi.ui

import android.Manifest
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.destinos.DetalleDelDestinoSheet
import com.jvillada.movi.ui.destinos.SIN_TRANSFERENCIAS_ESTE_PERIODO
import com.jvillada.movi.ui.destinos.TAG_TERCERO_EN_UN_GRUPO
import com.jvillada.movi.ui.mas.AVISOS_EN_ESTE_TELEFONO
import com.jvillada.movi.ui.mas.AvisosDeEsteTelefono
import com.jvillada.movi.ui.mas.TAG_AVISO_DEL_TELEFONO
import com.jvillada.movi.ui.mas.TEXTO_INTERRUPTOR_RESUMEN_DE_LA_MANANA
import com.jvillada.movi.ui.mas.TEXTO_INTERRUPTOR_RESUMEN_SEMANAL
import com.jvillada.movi.ui.mas.TEXTO_INTERRUPTOR_EXTRACTO
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_FRANJA_POR_CONFIRMAR
import com.jvillada.movi.ui.sdui.TAG_PROXIMO_PAGO
import com.jvillada.movi.ui.sdui.TAG_TARJETA_EN_QUE_SE_VA
import com.jvillada.movi.ui.sdui.TAG_WIDGET_FALTA_POR_PAGAR
import com.jvillada.movi.ui.sms.CapturaDelBancoScreen
import com.jvillada.movi.ui.sms.SmsSensorSetupSection
import com.jvillada.movi.ui.sms.TAG_AVISOS_EN_TU
import com.jvillada.movi.ui.sms.SUBTITULO_DE_LOS_AVISOS_EN_CAPTURA
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 73, montada
 *
 * - La franja «Por confirmar» a 360 dp no se corta, ni con la letra de la app (×1,12) ni al 130 % de
 *   verdad (×1,456): «$1.100.200 en 5 avisos · y 1 movimiento» entero, sin «…».
 * - Con un solo pendiente, que ya está en «Próximo pago», el widget «Falta por pagar» no se pinta y
 *   «En qué se va» toma el ancho.
 * - La ficha de Caro dice lo del grupo («En Casa con Caro», «te debe $168.050») y, sin transferencias,
 *   «Sin transferencias este período» en vez de «Nada»; tocar el grupo lo abre.
 * - Los avisos del teléfono están en Tú; Captura del banco ya no los tiene y lleva a Tú.
 *
 * `sdk = [34]` y `NATIVE`: hace falta el motor de texto real para medir.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class Vuelta73EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String, substring: Boolean = true): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Los textos dentro de [raiz] que se cortan con «…» o no les caben los renglones. */
    private fun cortados(raiz: SemanticsNode): List<String> {
        fun textos(n: SemanticsNode): List<SemanticsNode> =
            (if (n.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null) listOf(n) else emptyList()) + n.children.flatMap(::textos)
        return textos(raiz).mapNotNull { nodo ->
            val r = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            val d = r.firstOrNull() ?: return@mapNotNull null
            when {
                (0 until d.lineCount).any { d.isLineEllipsized(it) } -> "«${texto(nodo)}» se corta con «…»"
                d.multiParagraph.didExceedMaxLines -> "«${texto(nodo)}» no le caben los renglones"
                else -> null
            }
        }
    }

    // ── (1) La franja «Por confirmar» ────────────────────────────────────────

    private val ahorros = Account("acc_bc", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 10_589_700L)

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        rule = RecurringRule(id = id, name = nombre, category = "Servicios", amount = monto, dayOfMonth = 25, type = TransactionType.EXPENSE),
        dueDate = "2026-10-25",
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    /** El Inicio de la revisión `r4`: 5 avisos por $1.100.200 y 1 movimiento; solo el Carro pendiente. */
    private val inicio = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(ahorros),
        credits = emptyList(),
        cards = emptyList(),
        upcoming = listOf(pago("rr_carro", "Carro", 9_000, -3)),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(2026, 10),
        ajustesDePeriodo = PeriodSettings(),
        spentByCategory = mapOf("Otros" to 1_800_000L, "Cuota de crédito" to 980_000L, "Mercado" to 499_050L),
        pendingSms = 5,
        porConfirmarSuman = 1_100_200L,
        movimientosPorConfirmar = 1,
    )

    private fun montarElInicio(escala: Float, datos: DashboardData = inicio) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, escala)) {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) {
                        SduiRenderer(definition = defaultDashboardDefinition(), data = datos, modifier = Modifier.fillMaxSize(), onNavigate = {}, conBanda = true)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun laFranjaNoSeCorta(escala: Float) {
        montarElInicio(escala)
        val franja = conTag(TAG_FRANJA_POR_CONFIRMAR).single()
        val fallas = cortados(franja)
        assertTrue(fallas.isEmpty(), "a 360 dp con la letra ×$escala:\n" + fallas.joinToString("\n"))
        assertTrue(hay("\$1.100.200 en 5 avisos · y 1 movimiento", substring = false), "el renglón, entero y con su cifra")
        assertTrue(!hay("6 por revisar"), "sin la pastilla que repetía la cuenta")
    }

    @Test
    fun `la franja no se corta a 360 con la letra de la app`() = laFranjaNoSeCorta(1.12f)

    @Test
    fun `la franja no se corta a 360 al 130 por ciento de verdad`() = laFranjaNoSeCorta(1.3f * 1.12f)

    // ── (2) El widget «Falta por pagar» ──────────────────────────────────────

    @Test
    fun `con un solo pendiente en Proximo pago, el widget no esta y En que se va toma el ancho`() {
        montarElInicio(1.12f)
        assertEquals(1, conTag(TAG_PROXIMO_PAGO).size, "el Carro está en «Próximo pago»")
        assertEquals(0, conTag(TAG_WIDGET_FALTA_POR_PAGAR).size, "el widget casi vacío ya no se pinta")
        assertTrue(!hay("Es el de «Próximo pago»"))
        val enQueSeVa = conTag(TAG_TARJETA_EN_QUE_SE_VA).single().boundsInRoot
        val pantalla = composeRule.onAllNodesWithTag(TAG_PROXIMO_PAGO, useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
        assertEquals(pantalla.width, enQueSeVa.width, 1f, "«En qué se va» mide lo que «Próximo pago»: todo el ancho")
    }

    @Test
    fun `con dos pendientes el widget sigue`() {
        montarElInicio(1.12f, inicio.copy(upcoming = inicio.upcoming!! + pago("rr_internet", "Internet", 3_000, 2)))
        assertEquals(1, conTag(TAG_WIDGET_FALTA_POR_PAGAR).size)
    }

    // ── (3) La ficha del tercero con el saldo del grupo ──────────────────────

    private val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "31973270756")
    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio("dst_caro", "Caro")))
    private val gastos = listOf(
        GastoDelEspacio(id = "g1", espacioId = casa.id, pagadoPor = "dst_caro", monto = 240_000, partes = mapOf(MIEMBRO_YO to 120_000L, "dst_caro" to 120_000L)),
        GastoDelEspacio(id = "g2", espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = 576_100, partes = mapOf(MIEMBRO_YO to 288_050L, "dst_caro" to 288_050L)),
    )

    private inner class RepoDelTercero : RepositorioDePrueba() {
        override suspend fun getMovimientosDelDestino(id: String) = MovimientosDelDestino(destino = caro, movimientos = emptyList())
        override suspend fun getRenombrablesDelDestino(id: String) = MovimientosParaRenombrar("", "", emptyList())
        override suspend fun getEspacios(): List<Espacio> = listOf(casa)
        override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio {
            val saldo = saldoDelEspacio(casa, gastos, emptyList())
            return DetalleDelEspacio(espacio = casa, saldo = saldo, delPeriodo = saldo, gastos = gastos)
        }
    }

    @Test
    fun `la ficha de Caro dice lo del grupo y no dice Nada`() {
        Repositories.sustitutoDePrueba = RepoDelTercero()
        val abiertos = mutableListOf<String>()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    DetalleDelDestinoSheet(caro, PeriodSettings(), onDismiss = {}, onEditar = null, onAbrirGrupo = { abiertos += it })
                }
            }
        }
        composeRule.waitUntil(5_000) { conTag(TAG_TERCERO_EN_UN_GRUPO).isNotEmpty() }
        composeRule.waitForIdle()
        assertTrue(hay("En Casa con Caro"))
        assertTrue(hay("Puso \$240.000 · te debe \$168.050"))
        assertTrue(hay(SIN_TRANSFERENCIAS_ESTE_PERIODO))
        assertTrue(!hay("Nada", substring = false), "«Le enviaste Nada» contradecía al grupo")
        composeRule.onNodeWithTag(TAG_TERCERO_EN_UN_GRUPO, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf("esp_casa"), abiertos)
    }

    // ── (5) Los avisos, en Tú ────────────────────────────────────────────────

    @Test
    fun `los avisos del telefono estan todos en Tu`() {
        val app: Application = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        composeRule.setContent {
            MoviTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { AvisosDeEsteTelefono() } }
        }
        composeRule.waitForIdle()
        assertTrue(hay(AVISOS_EN_ESTE_TELEFONO))
        listOf(
            "Avisarme cuando llegue un movimiento", "Avisarme antes de que venza un pago", "Avisarme si mi banco deja de avisar",
            "Avisarme el día de un débito automático", "Avisarme cuando cierre mi período",
            TEXTO_INTERRUPTOR_RESUMEN_DE_LA_MANANA, TEXTO_INTERRUPTOR_RESUMEN_SEMANAL,
            // Vuelta 94.
            TEXTO_INTERRUPTOR_EXTRACTO,
        ).forEach { assertTrue(hay(it), "falta «$it»") }
        assertEquals(8, conTag(TAG_AVISO_DEL_TELEFONO).size)
    }

    @Test
    fun `Captura del banco ya no tiene los avisos de Movi`() {
        composeRule.setContent {
            MoviTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { SmsSensorSetupSection(onSynced = {}) } }
        }
        composeRule.waitForIdle()
        assertTrue(hay("Captura en este teléfono"), "la sección de la captura sigue")
        assertTrue(!hay("Avisos de Movi"))
        assertTrue(!hay("Avisarme cuando llegue un movimiento"))
        assertTrue(!hay(TEXTO_INTERRUPTOR_RESUMEN_DE_LA_MANANA))
    }

    @Test
    fun `Captura del banco lleva a los avisos en Tu`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getSmsMessages(): List<SmsMessage> = emptyList()
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        }
        val navegado = mutableListOf<Screen>()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { CapturaDelBancoScreen(onNavigate = { navegado += it }) } }
        }
        composeRule.waitUntil(5_000) { conTag(TAG_AVISOS_EN_TU).isNotEmpty() }
        composeRule.onNode(hasClickAction() and hasText(SUBTITULO_DE_LOS_AVISOS_EN_CAPTURA, substring = true))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(Screen.Tu, navegado.last())
    }
}
