package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO_DE_HOJA
import com.jvillada.movi.ui.components.ds.TAG_SELECTOR_DE_DIA_DEL_MES
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import com.jvillada.movi.ui.components.ds.tagDelDiaDelMes
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.profile.PeriodoSheet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Vuelta 72 · pagos que se manejan solos, en pantalla
 *
 * - «+ Agregar» en «Tus facturas del período» también con reglas.
 * - «Pausados (N)» al final, plegada, con «Reanudar».
 * - La tarjeta pagada por debajo del mínimo no queda «Pagado ✓»: dice cuánto falta.
 * - «Sí, fue este» con borde, nunca el amarillo.
 * - «¿Usar siempre Nu para Carro?» en la factura pagada cuya regla no tiene cuenta.
 * - Un solo selector de día (el de `ds/`) en la hoja del pago fijo y en la del período.
 * - «Editar recurrente» con el encabezado de hoja del sistema y «Eliminar» como acción destructiva.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp-xhdpi")
class PagosQueSeManejanSolosTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private val nu = Account("acc_nu", "Nu", AccountType.SAVINGS, 500_000L)

    private val arriendo = PagoDelPeriodo(
        ruleId = "rr_arriendo", nombre = "Arriendo", monto = 1_800_000L, pagado = false, diasParaVencer = 5,
        vence = "2026-11-03", categoria = "Vivienda",
    )

    private var agregar = 0
    private val reanudadas = mutableListOf<String>()
    private val cuentasGuardadas = mutableListOf<Pair<String, String>>()

    private fun montar(
        checklist: List<PagoDelPeriodo>,
        pausadas: List<RecurringRule> = emptyList(),
        nombres: Map<String, String> = emptyMap(),
    ) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SeccionChecklistDelPeriodo(
                        checklist = checklist,
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { _, _ -> },
                        onAnotarMovimiento = {},
                        onQuitarLaMarca = {},
                        onReintentar = {},
                        onAgregar = { agregar++ },
                        pausadas = pausadas,
                        onReanudar = { reanudadas += it.id },
                        nombresDeCuentas = nombres,
                        onUsarSiempreLaCuenta = { pago, cuenta -> cuentasGuardadas += pago.ruleId to cuenta },
                        onNoUsarSiempreLaCuenta = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun variante(texto: String): VarianteDeBoton? =
        composeRule.onNodeWithText(texto).fetchSemanticsNode().config.getOrNull(VarianteDeBotonKey)

    // ── «+ Agregar» y «Pausados (N)» ──────────────────────────────────────────

    @Test
    fun `mas Agregar esta tambien con reglas y abre la hoja de alta`() {
        montar(listOf(arriendo))
        assertTrue(hay(TITULO_TUS_FACTURAS))
        tocar(AGREGAR_UN_PAGO)
        assertEquals(1, agregar)
    }

    @Test
    fun `Pausados va al final, plegada, y Reanudar la devuelve`() {
        val gym = RecurringRule(
            "rr_gym", "Gimnasio", "Gimnasio", 180_000L, 5, TransactionType.EXPENSE, pausadaDesde = "2026-10-29",
        )
        montar(listOf(arriendo), pausadas = listOf(gym))
        assertTrue(hay(TITULO_DE_LOS_PAUSADOS))
        assertTrue(hay(" · 1"), "el conteo como en el resto del sistema: «Pausados · 1»")
        assertFalse(hay("Gimnasio"), "plegada: la regla no se ve hasta abrirla")
        tocar("Ver")
        assertTrue(hay("Pausado desde el 29 oct"))
        tocar(ETIQUETA_REANUDAR)
        assertEquals(listOf("rr_gym"), reanudadas)
    }

    // ── La tarjeta por debajo del mínimo ──────────────────────────────────────

    @Test
    fun `la tarjeta pagada por debajo del minimo dice cuanto falta y no Pagado`() {
        // La fila que vence en la fecha de pago del extracto, tildada por un pago de $100.000 desde el corte.
        val tarjeta = PagoDelPeriodo(
            ruleId = "${CARD_RULE_PREFIX}acc_nu", nombre = "Pago tarjeta Nu", monto = 1_583_000L, pagado = true,
            diasParaVencer = 7, vence = "2026-11-05", montoEsSaldo = true, pagoMinimo = 80_000L,
            montoPagado = 100_000L, pagadoEnElPeriodo = 100_000L, derivado = true, eventId = "ev_pago",
            restoDelMinimo = 80_000L, pagadoDesdeElCorte = 100_000L,
        )
        montar(listOf(tarjeta, arriendo))
        composeRule.onNodeWithTag(TAG_FALTA_DEL_MINIMO, useUnmergedTree = true).assertExists()
        assertTrue(hay("Pagaste \$100.000 · faltan \$80.000 para el mínimo"))
        assertTrue(composeRule.onAllNodesWithTag(TAG_FILA_PAGADA, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "no es una fila pagada")
        // «queda 2 de 2»: la tarjeta no cuenta como hecha, y lo que falta del mínimo suma.
        assertTrue(hay("quedan 2 de 2 pagos"))
        assertTrue(hay("\$1.880.000"), "falta: el arriendo más lo que le falta al mínimo")
    }

    // ── «Sí, fue este» con borde ──────────────────────────────────────────────

    @Test
    fun `Si fue este va con borde, aun en la fila mas urgente`() {
        val candidato = FinancialEvent(
            id = "ev_arr", accountId = "acc", type = TransactionType.EXPENSE, amount = 1_790_000L, category = "Vivienda",
            description = "Transferencia", timestamp = 1_793_000_000_000L,
        )
        montar(listOf(arriendo.copy(diasParaVencer = -2, periodoDelSello = "2026-10", candidatos = listOf(candidato))))
        assertEquals(VarianteDeBoton.Secundario, variante(ETIQUETA_SI_FUE_ESTE))
        val amarillos = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(VarianteDeBotonKey)).fetchSemanticsNodes()
            .filter { it.config.getOrNull(VarianteDeBotonKey) == VarianteDeBoton.Principal }
        assertEquals(0, amarillos.size)
    }

    // ── «¿Usar siempre Nu para Carro?» ────────────────────────────────────────

    @Test
    fun `la factura pagada sin cuenta en la regla propone guardarla, y solo con el toque`() {
        val carro = PagoDelPeriodo(
            ruleId = "rr_carro", nombre = "Carro", monto = 9_000L, pagado = true, diasParaVencer = -3,
            vence = "2026-10-20", eventId = "ev_carro", montoPagado = 9_000L, cuentaDelPago = nu.id,
        )
        montar(listOf(carro), nombres = mapOf(nu.id to nu.name))
        assertTrue(hay("¿Usar siempre Nu para Carro?"))
        assertTrue(cuentasGuardadas.isEmpty(), "nunca se guarda sola")
        // Vuelta 80: una propuesta de Movi arriba de la lista, no adentro de la fila pagada.
        val propuesta = composeRule.onNodeWithText("¿Usar siempre Nu para Carro?").fetchSemanticsNode().boundsInRoot.top
        val fila = composeRule.onNodeWithText("Carro").fetchSemanticsNode().boundsInRoot.top
        assertTrue(propuesta < fila, "la propuesta va arriba de la lista")
        assertTrue(composeRule.onAllNodesWithTag(com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_PROPUESTA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        tocar(GUARDAR_EN_LA_REGLA)
        assertEquals(listOf("rr_carro" to nu.id), cuentasGuardadas)
    }

    @Test
    fun `una regla pausada no propone guardar la cuenta`() {
        val carro = PagoDelPeriodo(
            ruleId = "rr_carro", nombre = "Carro", monto = 9_000L, pagado = true, diasParaVencer = -3,
            vence = "2026-10-20", eventId = "ev_carro", cuentaDelPago = nu.id, pausada = true,
        )
        montar(listOf(carro), nombres = mapOf(nu.id to nu.name))
        assertFalse(hay("¿Usar siempre"))
    }

    @Test
    fun `Llevas pagados suma lo pagado en el periodo entero`() {
        val tarjeta = PagoDelPeriodo(
            ruleId = "${CARD_RULE_PREFIX}acc_nu", nombre = "Pago tarjeta Nu", monto = 1_583_000L, pagado = true,
            diasParaVencer = -20, vence = "2026-10-05", montoEsSaldo = true, montoPagado = 80_000L,
            pagadoEnElPeriodo = 180_000L, derivado = true, eventId = "ev_80",
        )
        montar(listOf(tarjeta, arriendo))
        assertTrue(hay("Llevas \$180.000 pagados"), "el total, no el último pago")
        assertEquals("\$180.000", resumenDeLaFacturaPagada(tarjeta), "la fila dice el total pagado")
    }

    // ── «Pausar» en el «⋯» de una fila sin pagar ───────────────────────────────

    /** Vuelta 80: con «Anotar pago» al final, la fila no lleva además el «⋯» (Pausar está en la regla). */
    @Test
    fun `con Anotar pago la fila no lleva ademas el menu`() {
        composeRule.setContent {
            MoviTheme {
                SeccionChecklistDelPeriodo(
                    checklist = listOf(arriendo), cargando = false, pudoLeer = true, marcando = emptySet(),
                    onConfirmar = { _, _ -> }, onNoFueEste = { _, _ -> }, onAnotarMovimiento = {}, onQuitarLaMarca = {},
                    onReintentar = {}, onPausar = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(hay(com.jvillada.movi.ui.recurrentes.ETIQUETA_ANOTAR))
        assertTrue(composeRule.onAllNodesWithTag(TAG_MENU_DE_LA_FILA_PENDIENTE, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    // Vuelta 80: «Pausar esta en el menu de una fila sin pagar» se fue: con «Anotar pago» al final la fila
    // no lleva además el «⋯»; «Pausar» se ofrece en la hoja de la regla (`la hoja ofrece Pausar`).

    @Test
    fun `la fila vencida de una regla ya pausada no ofrece Pausar`() {
        val vencidaPausada = arriendo.copy(diasParaVencer = -9, vence = "2026-10-20", pausada = true)
        composeRule.setContent {
            MoviTheme {
                SeccionChecklistDelPeriodo(
                    checklist = listOf(vencidaPausada), cargando = false, pudoLeer = true, marcando = emptySet(),
                    onConfirmar = { _, _ -> }, onNoFueEste = { _, _ -> }, onAnotarMovimiento = {}, onQuitarLaMarca = {},
                    onReintentar = {}, onPausar = {},
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag(TAG_MENU_DE_LA_FILA_PENDIENTE, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `con algo vencido sin pagar, Pausar pregunta y No se debe pausa desde ese vencimiento`() {
        val pedidos = mutableListOf<Pair<String, String?>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(nu)
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun pausarRegla(id: String, desde: String?): RecurringRule {
                pedidos += id to desde
                return carroRegla.copy(pausadaDesde = desde ?: "2026-10-29")
            }
        }
        val vencido = PagoDelPeriodo(
            ruleId = "rr_carro", nombre = "Carro", monto = 9_000L, pagado = false, diasParaVencer = -9,
            vence = "2026-10-20", periodoDelDueno = "2026-10",
        )
        composeRule.setContent {
            MoviTheme { CreateRecurringRuleSheet(onDismiss = {}, onSaved = {}, existing = carroRegla, vencidoSinPagar = vencido) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_PAUSAR_LA_REGLA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay("Carro de octubre (\$9.000) no aparece pagado"))
        assertTrue(hay(DEJARLO_PENDIENTE) && hay(YA_LO_PAGUE))
        assertTrue(pedidos.isEmpty(), "preguntar no pausa")
        tocar(NO_SE_DEBE)
        composeRule.waitUntil(5_000) { pedidos.isNotEmpty() }
        assertEquals(listOf<Pair<String, String?>>("rr_carro" to "2026-10-20"), pedidos)
    }

    @Test
    fun `Dejarlo pendiente pausa desde hoy`() {
        val pedidos = mutableListOf<Pair<String, String?>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(nu)
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun pausarRegla(id: String, desde: String?): RecurringRule {
                pedidos += id to desde
                return carroRegla.copy(pausadaDesde = "2026-10-29")
            }
        }
        val vencido = PagoDelPeriodo(
            ruleId = "rr_carro", nombre = "Carro", monto = 9_000L, pagado = false, diasParaVencer = -9,
            vence = "2026-10-20", periodoDelDueno = "2026-10",
        )
        composeRule.setContent {
            MoviTheme { CreateRecurringRuleSheet(onDismiss = {}, onSaved = {}, existing = carroRegla, vencidoSinPagar = vencido) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_PAUSAR_LA_REGLA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar(DEJARLO_PENDIENTE)
        composeRule.waitUntil(5_000) { pedidos.isNotEmpty() }
        assertEquals(listOf<Pair<String, String?>>("rr_carro" to null), pedidos)
    }

    @Test
    fun `con cuenta en la regla no hay nada que proponer`() {
        val carro = PagoDelPeriodo(
            ruleId = "rr_carro", nombre = "Carro", monto = 9_000L, pagado = true, diasParaVencer = -3,
            vence = "2026-10-20", eventId = "ev_carro", cuentaId = nu.id, cuentaDelPago = nu.id,
        )
        montar(listOf(carro), nombres = mapOf(nu.id to nu.name))
        assertFalse(hay("¿Usar siempre"))
    }

    // ── La hoja del pago fijo: selector de día del sistema y encabezado ───────

    private fun montarLaHoja(regla: RecurringRule) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(nu)
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        }
        composeRule.setContent { MoviTheme { CreateRecurringRuleSheet(onDismiss = {}, onSaved = {}, existing = regla) } }
        composeRule.waitForIdle()
    }

    private val carroRegla = RecurringRule("rr_carro", "Carro", "Carro", 9_000L, 20, TransactionType.EXPENSE)

    @Test
    fun `Editar recurrente usa el encabezado del sistema y Eliminar es destructivo`() {
        montarLaHoja(carroRegla)
        composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_HOJA, useUnmergedTree = true).assertExists()
        assertTrue(hay(TITULO_EDITAR_RECURRENTE))
        val eliminar = composeRule.onNodeWithTag(TAG_ELIMINAR_LA_REGLA).fetchSemanticsNode()
        assertEquals(VarianteDeBoton.Peligro, eliminar.config.getOrNull(VarianteDeBotonKey))
        // Eliminar sigue preguntando.
        composeRule.onNodeWithTag(TAG_ELIMINAR_LA_REGLA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay("¿Eliminar el recurrente «Carro»?"))
    }

    @Test
    fun `la hoja ofrece Pausar, y la pausada Reanudar`() {
        montarLaHoja(carroRegla)
        composeRule.onNodeWithTag(TAG_PAUSAR_LA_REGLA).assertExists()
        assertTrue(hay(QUE_HACE_PAUSAR))
    }

    @Test
    fun `una regla pausada ofrece Reanudar en su hoja`() {
        montarLaHoja(carroRegla.copy(pausadaDesde = "2026-10-29"))
        composeRule.onNodeWithTag(TAG_REANUDAR_LA_REGLA).assertExists()
        assertTrue(hay("Pausado desde el 29 oct"))
    }

    @Test
    fun `el dia del mes se elige con el selector del sistema, el mismo del periodo`() {
        montarLaHoja(carroRegla)
        composeRule.onNodeWithTag(TAG_SELECTOR_DE_DIA_DEL_MES, useUnmergedTree = true).assertExists()
        val elegido = composeRule.onNodeWithTag(tagDelDiaDelMes(20), useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(true, elegido.config.getOrNull(SemanticsProperties.Selected))
        assertNotNull(composeRule.onNodeWithTag(tagDelDiaDelMes(31), useUnmergedTree = true).fetchSemanticsNode())
    }

    @Test
    fun `la hoja del periodo usa el mismo selector`() {
        composeRule.setContent {
            MoviTheme { PeriodoSheet(cutoffActual = 25, onDismiss = {}, onSave = { _, _ -> }) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_SELECTOR_DE_DIA_DEL_MES, useUnmergedTree = true).assertExists()
        assertEquals(true, composeRule.onNodeWithTag(tagDelDiaDelMes(25), useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected))
    }
}
