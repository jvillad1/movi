package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.AnfitrionDelAvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.BordeAlEncimaKey
import com.jvillada.movi.ui.components.ds.CambioDelDueno
import com.jvillada.movi.ui.components.ds.DESHECHO
import com.jvillada.movi.ui.components.ds.DURACION_DEL_DESHECHO
import com.jvillada.movi.ui.components.ds.Interruptor
import com.jvillada.movi.ui.components.ds.InterruptorEncendidoKey
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TAG_DESHACER_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_ICONO_EN_CIRCULO
import com.jvillada.movi.ui.components.ds.TAG_INTERRUPTOR
import com.jvillada.movi.ui.components.ds.TAG_TEXTO_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDeIconoKey
import com.jvillada.movi.ui.components.ds.VeloAlPresionarKey
import com.jvillada.movi.ui.components.ds.SnackbarDeMovi
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.credits.TAG_DEUDAS_SIN_SALDO
import com.jvillada.movi.ui.credits.TAG_PRESTAMO_SIN_SALDO
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.LocalCargandoElInicio
import com.jvillada.movi.ui.dashboard.MovimientosDelDueno
import com.jvillada.movi.ui.espacios.textoDeLoAnotado
import com.jvillada.movi.ui.porrevisar.ConteoPorRevisar
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.dashboard.TAG_CIFRA_TE_QUEDAN
import com.jvillada.movi.ui.sdui.TAG_ENLACE_TU_PLATA
import com.jvillada.movi.ui.transactions.AvisosPorConfirmar
import com.jvillada.movi.ui.transactions.EsqueletoDeLaTarjetaDeAvisos
import com.jvillada.movi.ui.transactions.TAG_ESQUELETO_DE_LA_TARJETA_DE_AVISOS
import com.jvillada.movi.ui.transactions.TAG_TARJETA_DE_AVISOS
import com.jvillada.movi.ui.transactions.TarjetaDeAvisosDelBanco
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 82, montada
 *
 * La línea del préstamo sin saldo y la pieza una sola vez, el signo de las deudas en Créditos, el
 * optimista del héroe (las dos cifras en el mismo cuadro, la tarjeta solo en «Te quedan», la corrección
 * del server), el snackbar que no corta el monto y su «Deshecho», la tarjeta de avisos que no cambia de
 * forma, el borde de hover de una superficie y el interruptor apagado.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h1600dp-xhdpi")
class Vuelta82EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        MovimientosDelDueno.limpiar()
        CambioDelDueno.limpiar()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun cuantosConTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun texto(tag: String): String = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun pesos(s: String): Long = s.filter { it.isDigit() }.toLong()

    // ── 2 · La deuda total, honesta ──────────────────────────────────────────────────────────────

    private val vehiculo = CreditSummary(
        account = Account("acc_4083", "Vehículo 4083", AccountType.LOAN, balance = -980_000L),
        terms = CreditTerms(
            accountId = "acc_4083", bank = "Banco", principal = 40_000_000L, rateEa = 18.0,
            termMonths = 60, installment = 980_000L, dayOfMonth = 5, startDate = "2026-01-10",
        ),
        paidPct = null,
        hasMovements = true,
    )
    private val nu = CardSummary(
        account = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, 1_583_000L, balancesByCurrency = mapOf("COP" to 1_583_000L)),
        terms = CardTerms(accountId = "acc_nu", bank = "Nu", paymentDay = 7),
    )

    @Test
    fun `Creditos dice la linea del prestamo sin saldo, la pieza una sola vez y las deudas con signo`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = listOf(vehiculo)
            override suspend fun getCards(): List<CardSummary> = listOf(nu)
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CreditosScreen(onNavigate = {}) } } }
        composeRule.waitUntil(5_000) { hay("Vehículo 4083") }
        composeRule.waitForIdle()

        val linea = texto(TAG_DEUDAS_SIN_SALDO)
        assertTrue(linea.startsWith("Falta Vehículo 4083, sin saldo (unos \$"), "la línea debajo de la deuda total: $linea")
        assertTrue(linea.endsWith("según el plan)"), linea)
        assertEquals(1, cuantosConTag(TAG_PRESTAMO_SIN_SALDO), "la pieza sale una vez, en la fila del préstamo")
        // El signo de Patrimonio («Deudas −$1.583.000»): la cifra grande y la fila de Nu.
        assertTrue(composeRule.onAllNodesWithText("−\$1.583.000", useUnmergedTree = true).fetchSemanticsNodes().size >= 2, "la deuda total y la fila de Nu, con signo")
        assertFalse(composeRule.onAllNodesWithText("\$1.583.000", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "ninguna sin signo")
    }

    // ── 3 · El optimista del héroe ───────────────────────────────────────────────────────────────

    private val hoy = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds())
    private val ahorros = Account("ah", "Bancolombia Ahorros", AccountType.SAVINGS, 10_000_000L)
    private val tarjeta = Account("tc", "Master Black", AccountType.CREDIT_CARD, 500_000L)

    private val inicio = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 8_000_000, egresos = 2_000_000),
        accounts = listOf(ahorros, tarjeta),
        credits = emptyList(),
        cards = emptyList(),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(hoy.year, hoy.monthNumber),
        ajustesDePeriodo = PeriodSettings(),
        gastoVariablePorDia = emptyMap(),
    )

    private fun gasto(cuenta: String, monto: Long) = FinancialEvent(
        id = "g-$cuenta", accountId = cuenta, type = TransactionType.EXPENSE, amount = monto, category = "Restaurantes",
        description = "Almuerzo", timestamp = Clock.System.now().toEpochMilliseconds(), reconciliationStatus = ReconciliationStatus.RECONCILED,
    )

    private var datos by mutableStateOf(inicio)

    private fun montarElHeroe() {
        // La base del optimista: lo último que leyó el Inicio (acá, lo que se monta).
        MovimientosDelDueno.costurasDePrueba = MovimientosDelDueno.Costuras(base = { inicio })
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalCargandoElInicio provides false) {
                    Box(Modifier.fillMaxSize()) {
                        SduiRenderer(definition = defaultDashboardDefinition(), data = datos, modifier = Modifier.fillMaxSize(), onNavigate = {})
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
    }

    private fun teQuedan() = pesos(texto(TAG_CIFRA_TE_QUEDAN))
    private fun tuPlata() = pesos(texto(TAG_ENLACE_TU_PLATA))

    @Test
    fun `un gasto con cuenta de dinero mueve las dos cifras el mismo monto en el mismo cuadro`() {
        montarElHeroe()
        val (q0, p0) = teQuedan() to tuPlata()
        Snapshot.withMutableSnapshot { MovimientosDelDueno.registrar(gasto("ah", 12_300), signo = 1) }
        // Cuadro a cuadro: nunca una se mueve y la otra no.
        var termino = false
        repeat(30) {
            composeRule.mainClock.advanceTimeBy(16)
            val dq = teQuedan() - q0
            val dp = tuPlata() - p0
            assertEquals(dq, dp, "las dos cifras van juntas (cuadro ${it + 1}: «Te quedan» $dq, «Tu plata» $dp)")
            if (dq == -12_300L) termino = true
        }
        assertTrue(termino, "llegaron a −12.300")
    }

    @Test
    fun `un gasto con tarjeta mueve solo Te quedan`() {
        montarElHeroe()
        val (q0, p0) = teQuedan() to tuPlata()
        Snapshot.withMutableSnapshot { MovimientosDelDueno.registrar(gasto("tc", 40_000), signo = 1) }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(-40_000L, teQuedan() - q0)
        assertEquals(0L, tuPlata() - p0, "la tarjeta no es Tu plata")
    }

    @Test
    fun `al llegar el server con otra cifra, el heroe se corrige contando`() {
        montarElHeroe()
        val (q0, p0) = teQuedan() to tuPlata()
        var id = 0L
        Snapshot.withMutableSnapshot { id = MovimientosDelDueno.registrar(gasto("ah", 12_300), signo = 1) }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(q0 - 12_300, teQuedan())
        // El server ya lo tiene (la web: al volver la escritura).
        val listoEn = Clock.System.now().toEpochMilliseconds()
        Snapshot.withMutableSnapshot { MovimientosDelDueno.marcarListo(id, listoEn) }
        // El server leyó otra cosa: el gasto fue de $15.000 (y la carga terminó después del cambio).
        Snapshot.withMutableSnapshot {
            datos = inicio.copy(
                accounts = listOf(ahorros.copy(balance = 10_000_000L - 15_000L), tarjeta),
                gastoVariablePorDia = mapOf(hoy.toString() to 15_000L),
            )
        }
        MovimientosDelDueno.cargaBuenaEmpezo(listoEn + 1)
        composeRule.mainClock.advanceTimeBy(16 * 4)
        val enMedio = teQuedan()
        assertTrue(enMedio in (q0 - 15_000 + 1)..(q0 - 12_300), "la corrección cuenta: $enMedio")
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(q0 - 15_000, teQuedan())
        assertEquals(p0 - 15_000, tuPlata())
    }

    // ── 4 · El snackbar con el monto, y «Deshecho» ───────────────────────────────────────────────

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 se corta la categoria y nunca el monto`() {
        val t = textoDeLoAnotado("Restaurantes y bares del parque Lleras", 12_300, "COP")
        composeRule.setContent {
            MoviTheme {
                SnackbarDeMovi(
                    texto = t,
                    esError = false,
                    accion = com.jvillada.movi.ui.components.ds.AccionDelSnackbar("Repartir", "Repartir con Caro", "a", true, {}),
                    deshacer = com.jvillada.movi.ui.components.ds.AccionDelSnackbar("Deshacer", "Deshacer", "d", true, {}),
                )
            }
        }
        composeRule.waitForIdle()
        val r = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(com.jvillada.movi.ui.components.ds.TAG_CIFRA_DEL_AVISO, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        assertEquals("Anotado", texto(TAG_TEXTO_DEL_AVISO))
        assertEquals("\$12.300", texto(com.jvillada.movi.ui.components.ds.TAG_CIFRA_DEL_AVISO), "el monto va en su propia pieza")
        assertFalse(r.single().hasVisualOverflow, "el monto se ve entero")
        val resto = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(com.jvillada.movi.ui.components.ds.TAG_RESTO_DEL_AVISO, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(resto)
        assertTrue(resto.single().isLineEllipsized(0), "la categoría larga se corta con «…»")
    }

    @Test
    fun `despues de Deshacer sale Deshecho un momento`() {
        val aviso = AvisoDeDeshacer()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { MoviTheme { Column { AnfitrionDelAvisoDeDeshacer(aviso) } } }
        Snapshot.withMutableSnapshot { aviso.mostrar(textoDeLoAnotado("Restaurantes", 12_300, "COP")) {} }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(TAG_DESHACER_DEL_AVISO, useUnmergedTree = true).performClick()
        composeRule.mainClock.advanceTimeBy(100)
        assertEquals(DESHECHO, texto(TAG_TEXTO_DEL_AVISO))
        assertEquals(0, cuantosConTag(TAG_DESHACER_DEL_AVISO), "«Deshecho» va sin botones")
        composeRule.mainClock.advanceTimeBy(DURACION_DEL_DESHECHO + 600)
        assertEquals(null, aviso.actual, "y se va solo")
    }

    // ── 6 · La tarjeta de avisos con el conteo, sin saltar ───────────────────────────────────────

    @Test
    fun `la tarjeta de Actividad dice el numero del Inicio y su esqueleto mide lo mismo`() {
        var cargada by mutableStateOf(false)
        val conteo = ConteoPorRevisar(avisos = 5, movimientos = 1)
        val avisos = AvisosPorConfirmar(cuantos = 5, suman = null, comercios = null, confirmables = emptyList())
        composeRule.setContent {
            MoviTheme {
                Column {
                    if (cargada) {
                        TarjetaDeAvisosDelBanco(avisos = avisos, conteo = conteo, onConfirmar = {}, onRevisar = {})
                    } else {
                        EsqueletoDeLaTarjetaDeAvisos()
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val esqueleto = composeRule.onNodeWithTag(TAG_ESQUELETO_DE_LA_TARJETA_DE_AVISOS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        cargada = true
        composeRule.waitForIdle()
        assertTrue(hay("6 por revisar"), "el número de la insignia del Inicio")
        assertTrue(hay("5 avisos · 1 movimiento"), "y de qué es")
        val tarjeta = composeRule.onNodeWithTag(TAG_TARJETA_DE_AVISOS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val salto = (tarjeta.bottom - tarjeta.top) - (esqueleto.bottom - esqueleto.top)
        assertTrue(salto.value in -8f..8f, "la tarjeta no cambia de forma al cargar: $salto")
        // El círculo en el tono de aviso, no en el amarillo.
        val circulo = composeRule.onAllNodes(hasTestTag(TAG_ICONO_EN_CIRCULO) and hasAnyAncestor(hasTestTag(TAG_TARJETA_DE_AVISOS)), useUnmergedTree = true)
            .fetchSemanticsNodes().single()
        assertEquals(TonoDeIcono.Aviso, circulo.config[TonoDeIconoKey])
    }

    // ── 7 · El hover de las superficies ──────────────────────────────────────────────────────────

    @Test
    fun `una superficie tocable no se vela entera, cambia su borde`() {
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalMovimientoReducido provides true) {
                    Superficie(onClick = {}, modifier = Modifier.testTag("sup")) { Text("En qué se va") }
                }
            }
        }
        composeRule.waitForIdle()
        // Presionada con menos movimiento: la misma regla que el hover (`seVeElVelo`).
        composeRule.onNodeWithTag("sup", useUnmergedTree = true).performTouchInput { down(center) }
        composeRule.waitForIdle()
        val n = composeRule.onNodeWithTag("sup", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(true, n.config.getOrNull(BordeAlEncimaKey), "cambia el borde")
        assertEquals(false, n.config.getOrNull(VeloAlPresionarKey) ?: false, "sin el velo gris entero")
    }

    // ── 8 · El interruptor apagado ───────────────────────────────────────────────────────────────

    @Test
    fun `el interruptor apagado se toca y se prende`() {
        var encendido by mutableStateOf(false)
        composeRule.setContent { MoviTheme { Interruptor(encendido = encendido, onCambio = { encendido = it }, descripcion = "Ocultar los saldos") } }
        composeRule.waitForIdle()
        assertEquals(false, composeRule.onNodeWithTag(TAG_INTERRUPTOR, useUnmergedTree = true).fetchSemanticsNode().config[InterruptorEncendidoKey])
        composeRule.onNodeWithTag(TAG_INTERRUPTOR, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertTrue(encendido)
        assertEquals(true, composeRule.onNodeWithTag(TAG_INTERRUPTOR, useUnmergedTree = true).fetchSemanticsNode().config[InterruptorEncendidoKey])
    }

    // ── Revisión de la vuelta 82 ─────────────────────────────────────────────────────────────────

    @Test
    fun `una carga que empezo antes de que el server lo tuviera no suelta el optimista`() {
        montarElHeroe()
        val q0 = teQuedan()
        var id = 0L
        Snapshot.withMutableSnapshot { id = MovimientosDelDueno.registrar(gasto("ah", 12_300), signo = 1) }
        composeRule.mainClock.advanceTimeBy(1_000)
        // En el teléfono la fila no subió: la carga que arrancó al guardar terminó bien pero sin el gasto.
        MovimientosDelDueno.cargaBuenaEmpezo(Clock.System.now().toEpochMilliseconds() + 5)
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(q0 - 12_300, teQuedan(), "sigue adelantado: el server todavía no lo tiene")
        assertTrue(id > 0)
    }

    private fun esperarHasta(condicion: () -> Boolean) {
        val hasta = System.currentTimeMillis() + 5_000
        while (!condicion() && System.currentTimeMillis() < hasta) Thread.sleep(10)
    }

    private fun listoEn(): Long? = MovimientosDelDueno.estado.value.movimientos.firstOrNull()?.listoEn

    @Test
    fun `en el telefono, lo que no subio se sube y queda listo desde el sello real de la fila`() {
        var subidas = 0
        var recargas = 0
        MovimientosDelDueno.costurasDePrueba = MovimientosDelDueno.Costuras(
            desdeCuando = { if (subidas > 0) 12_345L else null },
            subirAhora = { subidas++ },
            pedirRecarga = { recargas++ },
            base = { inicio },
            ahora = { 1_000L },
        )
        val id = MovimientosDelDueno.registrar(gasto("ah", 12_300), signo = 1)
        MovimientosDelDueno.listo(id)
        esperarHasta { listoEn() != null && recargas > 0 }
        assertEquals(1, subidas, "pidió una vuelta del SyncEngine")
        assertEquals(12_345L, listoEn(), "listo desde el syncedAt de la fila, no desde cuando lo notó")
        assertEquals(1, recargas, "y la pantalla a la vista recarga para traerlo")
    }

    @Test
    fun `cuando algo sube, el que espera vuelve a mirar sin esperar 30 s`() {
        var subio = false
        MovimientosDelDueno.costurasDePrueba = MovimientosDelDueno.Costuras(
            desdeCuando = { if (subio) 2_000L else null },
            subirAhora = {},
            pedirRecarga = {},
            base = { inicio },
            ahora = { 1_000L },
        )
        val id = MovimientosDelDueno.registrar(gasto("ah", 12_300), signo = 1)
        MovimientosDelDueno.listo(id)
        Thread.sleep(200)
        assertEquals(null, listoEn(), "sin subir, espera")
        subio = true
        MovimientosDelDueno.despertar()
        esperarHasta { listoEn() != null }
        assertEquals(2_000L, listoEn(), "el SyncEngine subió algo: miró ya")
    }

    @Test
    fun `el bucle se corta cuando el movimiento vence, y lo vencido sale`() {
        var reloj = 1_000L
        var preguntas = 0
        MovimientosDelDueno.costurasDePrueba = MovimientosDelDueno.Costuras(
            desdeCuando = { preguntas++; null },
            subirAhora = {},
            pedirRecarga = {},
            base = { inicio },
            ahora = { reloj },
        )
        val id = MovimientosDelDueno.registrar(gasto("ah", 12_300), signo = 1, ahora = 1_000L)
        MovimientosDelDueno.listo(id)
        esperarHasta { preguntas >= 2 }
        reloj = 1_000L + com.jvillada.movi.ui.dashboard.VIGENCIA_SIN_SUBIR_MS + 1
        MovimientosDelDueno.despertar()
        esperarHasta { MovimientosDelDueno.estado.value.movimientos.isEmpty() }
        assertTrue(MovimientosDelDueno.estado.value.movimientos.isEmpty(), "lo vencido salió")
        val antes = preguntas
        MovimientosDelDueno.despertar()
        Thread.sleep(200)
        assertEquals(antes, preguntas, "y ya nadie vuelve a preguntar")
    }

    @Test
    fun `el interruptor tiene 48 dp de toque sin cambiar el riel`() {
        composeRule.setContent { MoviTheme { Box { Interruptor(encendido = false, onCambio = {}) } } }
        composeRule.waitForIdle()
        val b = composeRule.onNodeWithTag(TAG_INTERRUPTOR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(b.bottom - b.top >= com.jvillada.movi.theme.Medida.objetivoTactil, "alto de toque ${b.bottom - b.top}")
        assertTrue(b.right - b.left >= com.jvillada.movi.theme.Medida.objetivoTactil, "ancho de toque ${b.right - b.left}")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `con la letra grande y un monto de ocho cifras se corta la categoria y nunca el monto`() {
        val t = textoDeLoAnotado("Restaurantes y bares del parque Lleras", 12_300_000, "COP")
        composeRule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, fontScale = 1.3f)) {
                MoviTheme {
                    SnackbarDeMovi(
                        texto = t,
                        esError = false,
                        accion = com.jvillada.movi.ui.components.ds.AccionDelSnackbar("Repartir", "Repartir con Caro", "a", true, {}),
                        deshacer = com.jvillada.movi.ui.components.ds.AccionDelSnackbar("Deshacer", "Deshacer", "d", true, {}),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        fun disposicion(tag: String): TextLayoutResult {
            val r = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
            return r.single()
        }
        val cifra = disposicion(com.jvillada.movi.ui.components.ds.TAG_CIFRA_DEL_AVISO)
        assertEquals("\$12.300.000", texto(com.jvillada.movi.ui.components.ds.TAG_CIFRA_DEL_AVISO))
        assertFalse(cifra.hasVisualOverflow, "el monto se ve entero")
        val bordesDeLaCifra = composeRule.onNodeWithTag(com.jvillada.movi.ui.components.ds.TAG_CIFRA_DEL_AVISO, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bordesDeLaAccion = composeRule.onNodeWithTag("a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(bordesDeLaCifra.right <= bordesDeLaAccion.left, "y no se mete debajo de las acciones")
    }
}
