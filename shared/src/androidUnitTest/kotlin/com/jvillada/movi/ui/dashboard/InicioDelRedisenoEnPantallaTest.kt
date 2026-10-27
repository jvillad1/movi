package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.EvidenciaDeAnomalia
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TipoDeAnomalia
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.data.SaldosOcultosStore
import com.jvillada.movi.ui.components.PLATA_OCULTA
import com.jvillada.movi.ui.components.ds.TAG_CUADRICULA_DE_ACCIONES
import com.jvillada.movi.ui.components.ds.TAG_MARCA_DE_LA_TARJETA
import com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_CUENTA
import com.jvillada.movi.ui.components.ds.TAG_TARJETA_DE_PROPUESTA
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_CARRUSEL_DE_CUENTAS
import com.jvillada.movi.ui.sdui.TAG_ESQUELETO_DEL_CARRUSEL
import com.jvillada.movi.ui.sdui.TAG_ESQUELETO_DE_PROPUESTA
import com.jvillada.movi.ui.sdui.TAG_NADA_POR_DECIDIR
import com.jvillada.movi.ui.sdui.TAG_PROPUESTAS_DEL_INICIO
import com.jvillada.movi.ui.sdui.TAG_TARJETA_EN_QUE_SE_VA
import com.jvillada.movi.ui.sdui.TAG_WIDGET_FALTA_POR_PAGAR
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
 * **El Inicio del rediseño montado** (ola R1, generación 9): el orden de los bloques, «Movi propone
 * hoy» con una tarjeta por tipo y su tope, la línea tranquila, el carrusel con la marca de cada banco,
 * la cuadrícula que abre cada destino y que con los saldos ocultos no se vea ni una cifra.
 *
 * `sdk = [34]` y `NATIVE`: estas pruebas comparan posiciones de texto y miden altos (ver la memoria del
 * repo sobre SDK 24 y el KDoc de `EsqueletosDelInicioTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h3200dp-xhdpi")
class InicioDelRedisenoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val navegado = mutableListOf<Screen>()
    private var subioComprobante = false
    private val cierresDescartados = mutableListOf<String>()

    @org.junit.Before
    fun preparar() {
        // «Está bien» le avisa al server; el de prueba contesta sin red.
        com.jvillada.movi.data.Repositories.sustitutoDePrueba = object : com.jvillada.movi.data.RepositorioDePrueba() {
            override suspend fun descartarAnomalia(huella: String) = Unit
        }
    }

    @After
    fun limpiar() {
        com.jvillada.movi.data.Repositories.sustitutoDePrueba = null
        AnomaliasDescartadasEnLaSesion.olvidar()
        SaldosOcultosStore.poner(false)
    }

    private val septiembre = PeriodoFinanciero(2026, 9)

    private fun pago(id: String, nombre: String, monto: Long, dias: Int) = UpcomingPayment(
        rule = RecurringRule(id = id, name = nombre, category = "Vivienda", amount = monto, dayOfMonth = 10, type = TransactionType.EXPENSE),
        dueDate = "2026-09-10",
        daysUntil = dias,
        status = if (dias < 0) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    private val cuentas = listOf(
        Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800),
        Account("a2", "Nu", AccountType.SAVINGS, 2_890_000),
        Account("a3", "Efectivo", AccountType.CASH, 120_000),
    )

    /** Lo de todos los días, con todo leído y nada pendiente: alcanza para afirmar «nada por decidir». */
    private val sinPendientes = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = cuentas,
        credits = emptyList<CreditSummary>(),
        cards = emptyList<CardSummary>(),
        upcoming = listOf(pago("r_gym", "Gimnasio", 139_900, 12)),
        ocurrencias = emptyList(),
        periodoActual = septiembre,
        ajustesDePeriodo = PeriodSettings(),
        spentByCategory = mapOf("Mercado" to 392_100L, "Restaurantes" to 236_000L),
    )

    /** Una cosa de cada tipo que pide decisión. */
    private val conTodoPendiente = sinPendientes.copy(
        upcoming = listOf(pago("r_arr", "Arriendo", 1_850_000, 0), pago("r_gym", "Gimnasio", 139_900, 12)),
        debitosPorConfirmar = listOf(
            DebitoAutomaticoPorConfirmar(
                ruleId = "cr_1", periodo = "2026-09", origen = OrigenDelDebito.CUOTA_DE_CREDITO, nombre = "Libre inversión 9695",
                monto = 1_204_064, vence = "2026-09-05", cuentaId = "a1", cuentaNombre = "Bancolombia Ahorros",
                categoria = "Cuota de crédito", pataDelDineroId = "p1",
            ),
        ),
        pendingSms = 3,
        cuadresPorAviso = listOf(
            PropuestaDeCuadrePorAviso("s1", "a2", "Nu", "Notificación · Nu", saldoDelBanco = 2_900_000, saldoEnMovi = 2_890_000, momento = 0),
        ),
        anomalias = listOf(
            Anomalia(
                huella = "dup:a,b", tipo = TipoDeAnomalia.COBRO_DUPLICADO, titulo = "¿Cobro duplicado en RAPPI?",
                detalle = "2 cobros de \$45.900 en Nu", evidencia = listOf(EvidenciaDeAnomalia("a", "2026-09-28", "RAPPI", 45_900)),
            ),
        ),
        cierre = CierreDelPeriodo(periodo = "2026-08", nombre = "Agosto 2026", entro = 22_500_000, salio = 18_200_000, diaDelPeriodoNuevo = 1),
    )

    private fun montar(data: DashboardData, cargando: Boolean = false) {
        navegado.clear()
        subioComprobante = false
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalCargandoElInicio provides cargando) {
                    Box(Modifier.fillMaxSize()) {
                        SduiRenderer(
                            definition = defaultDashboardDefinition(),
                            data = data,
                            modifier = Modifier.fillMaxSize(),
                            onNavigate = { navegado += it },
                            onSubirComprobante = { subioComprobante = true },
                            onDescartarCierre = { cierresDescartados += it },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun cuantosTags(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun arribaDelTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)[0].getUnclippedBoundsInRoot().top

    // ── El orden ─────────────────────────────────────────────────────────────

    @Test
    fun `el orden por defecto es heroe, acciones, propuestas, cuentas y widgets`() {
        montar(conTodoPendiente)
        val hero = arribaDelTag(TAG_TARJETA_DEL_HERO)
        val acciones = arribaDelTag(TAG_CUADRICULA_DE_ACCIONES)
        val propuestas = arribaDelTag(TAG_PROPUESTAS_DEL_INICIO)
        val cuentas = arribaDelTag(TAG_CARRUSEL_DE_CUENTAS)
        val faltaPorPagar = arribaDelTag(TAG_WIDGET_FALTA_POR_PAGAR)
        val enQueSeVa = arribaDelTag(TAG_TARJETA_EN_QUE_SE_VA)
        assertTrue(hero < acciones && acciones < propuestas && propuestas < cuentas && cuentas < faltaPorPagar)
        // Los dos widgets van lado a lado: arrancan a la misma altura.
        assertEquals(faltaPorPagar, enQueSeVa)
        // Disponible y el patrimonio ya no vienen por defecto; «Para revisar» tampoco se pinta.
        composeRule.onNodeWithText("Tu patrimonio", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Para revisar", useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Movi propone hoy ─────────────────────────────────────────────────────

    @Test
    fun `el feed muestra una tarjeta por tipo pendiente y respeta el tope`() {
        montar(conTodoPendiente)
        // Seis tipos pendientes, cuatro a la vista: los más urgentes.
        assertEquals(TOPE_DE_PROPUESTAS, cuantosTags(TAG_TARJETA_DE_PROPUESTA))
        composeRule.onNodeWithText("Arriendo vence hoy", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("¿Se cobró la cuota de Libre inversión 9695?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Llegaron 3 avisos del banco", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("El saldo de Nu no cuadra con el banco", useUnmergedTree = true).assertIsDisplayed()
        // Lo que no entra queda para «Ver todo», que lleva a Por revisar.
        composeRule.onNodeWithText("¿Cobro duplicado en RAPPI?", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText(" · 6", useUnmergedTree = true).assertIsDisplayed()
        // El primer «Ver todo» es el del feed (el segundo, el del carrusel).
        composeRule.onAllNodesWithText("Ver todo", useUnmergedTree = true)[0].performClick()
        assertEquals(Screen.PorRevisar, navegado.last())
    }

    @Test
    fun `con menos propuestas se ven todas, y cada una hace lo suyo`() {
        montar(conTodoPendiente.copy(upcoming = emptyList(), debitosPorConfirmar = emptyList(), pendingSms = 0))
        assertEquals(3, cuantosTags(TAG_TARJETA_DE_PROPUESTA))
        // «Está bien» descarta la anomalía en el mismo toque.
        composeRule.onNodeWithText("Está bien", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¿Cobro duplicado en RAPPI?", useUnmergedTree = true).assertDoesNotExist()
        // «Ya lo vi» del cierre se lo pide a la pantalla, que es dueña de los datos.
        composeRule.onNodeWithText("Ya lo vi", useUnmergedTree = true).performClick()
        assertEquals(listOf("2026-08"), cierresDescartados)
        // «Revisar» del cuadre lleva a Cuadre de saldos.
        composeRule.onNodeWithText("Revisar", useUnmergedTree = true).performClick()
        assertEquals(Screen.CuadreDeSaldos, navegado.last())
    }

    @Test
    fun `sin pendientes, la linea tranquila`() {
        montar(sinPendientes)
        assertEquals(0, cuantosTags(TAG_TARJETA_DE_PROPUESTA))
        composeRule.onNodeWithTag(TAG_NADA_POR_DECIDIR, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(NADA_POR_DECIDIR, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `mientras carga, el feed no afirma nada y muestra un esqueleto`() {
        montar(DashboardData(), cargando = true)
        assertEquals(0, cuantosTags(TAG_NADA_POR_DECIDIR))
        assertEquals(1, cuantosTags(TAG_ESQUELETO_DE_PROPUESTA))
        assertEquals(1, cuantosTags(TAG_ESQUELETO_DEL_CARRUSEL))
    }

    // ── Tus cuentas ──────────────────────────────────────────────────────────

    @Test
    fun `el carrusel muestra las cuentas con su marca`() {
        montar(sinPendientes)
        // El carrusel es perezoso: se componen la primera y la que asoma; el efectivo, más allá.
        assertEquals(2, cuantosTags(TAG_TARJETA_DE_CUENTA))
        // Bancolombia y Nu llevan su marca.
        assertEquals(2, cuantosTags(TAG_MARCA_DE_LA_TARJETA))
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Bancolombia", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Nu", useUnmergedTree = true).fetchSemanticsNodes().size)
        // La de más plata primero, y tocarla abre su detalle.
        composeRule.onAllNodesWithTag(TAG_TARJETA_DE_CUENTA)[0].performClick()
        assertEquals(Screen.AccountDetail("a1", AccountGroup.DINERO), navegado.last())
        // «Ver todo» del carrusel (el segundo; el primero es el del feed) lleva a Patrimonio.
        composeRule.onAllNodesWithText("Ver todo", useUnmergedTree = true)[1].performClick()
        assertEquals(Screen.Accounts, navegado.last())
    }

    @Test
    fun `la siguiente tarjeta asoma`() {
        montar(sinPendientes)
        val carrusel = composeRule.onNodeWithTag(TAG_CARRUSEL_DE_CUENTAS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val segunda = composeRule.onAllNodesWithTag(TAG_TARJETA_DE_CUENTA, useUnmergedTree = true)[1].getUnclippedBoundsInRoot()
        assertTrue(segunda.left < carrusel.right, "la segunda tarjeta empieza adentro de la pantalla")
    }

    // ── La cuadrícula ────────────────────────────────────────────────────────

    @Test
    fun `la cuadricula abre cada destino`() {
        montar(conTodoPendiente)
        composeRule.onNodeWithContentDescription("Anotar un gasto", useUnmergedTree = true).performClick()
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.GASTO), navegado.last())
        composeRule.onNodeWithContentDescription("Anotar un ingreso", useUnmergedTree = true).performClick()
        assertEquals(Screen.QuickAdd(presetEsIngreso = true, presetPestana = PestanaDeAgregar.INGRESO), navegado.last())
        composeRule.onNodeWithContentDescription("Anotar un traspaso", useUnmergedTree = true).performClick()
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.TRASPASO), navegado.last())
        composeRule.onNodeWithContentDescription("Pagar una tarjeta", useUnmergedTree = true).performClick()
        assertEquals(Screen.QuickAdd(presetPestana = PestanaDeAgregar.PAGO_DE_TARJETA), navegado.last())
        // La insignia dice cuánto espera en Por revisar: 3 avisos y 1 débito.
        composeRule.onNodeWithContentDescription("Revisar los avisos, 4", useUnmergedTree = true).performClick()
        assertEquals(Screen.PorRevisar, navegado.last())
        composeRule.onNodeWithContentDescription("Preguntarle a Movi", useUnmergedTree = true).performClick()
        assertEquals(Screen.AIChat(), navegado.last())
        composeRule.onNodeWithContentDescription("Subir un comprobante", useUnmergedTree = true).performClick()
        assertTrue(subioComprobante, "elige un archivo en vez de navegar")
        // R3 · «Repartir» lleva a Espacios.
        composeRule.onNodeWithContentDescription("Repartir un gasto", useUnmergedTree = true).performClick()
        assertEquals(Screen.Espacios, navegado.last())
    }

    // ── Ocultar los saldos ───────────────────────────────────────────────────

    private fun textos(): List<String> {
        val textos = mutableListOf<String>()
        fun recorrer(nodo: SemanticsNode) {
            nodo.config.getOrNull(SemanticsProperties.Text)?.forEach { textos += it.text }
            nodo.config.getOrNull(SemanticsProperties.ContentDescription)?.let { textos += it }
            nodo.children.forEach(::recorrer)
        }
        recorrer(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return textos
    }

    @Test
    fun `con saldos ocultos no se ve ninguna cifra`() {
        val cifra = Regex("\\$\\s?\\d")
        montar(conTodoPendiente)
        assertTrue(textos().any { cifra.containsMatchIn(it) }, "a la vista, el Inicio sí tiene cifras")

        // El tema lee «Ocultar los saldos» de su store: alternarlo recompone lo que ya está montado.
        SaldosOcultosStore.poner(true)
        composeRule.waitForIdle()
        val aLaVista = textos().filter { cifra.containsMatchIn(it) }
        assertEquals(emptyList(), aLaVista, "ninguna cifra de plata con los saldos ocultos")
        assertTrue(textos().any { PLATA_OCULTA in it })
    }
}
