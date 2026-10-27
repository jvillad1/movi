package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.PlanScreen
import com.jvillada.movi.ui.plan.TAG_CAJA_PROYECTADA
import com.jvillada.movi.ui.plan.TAG_GRAFICA_DE_LA_CAJA
import com.jvillada.movi.ui.sdui.TAG_PROPUESTAS_DEL_INICIO
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
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
 * # El primer día de alguien nuevo (vuelta de diseño 11)
 *
 * Una cuenta recién creada, que saltó la guía de primer uso tras elegir el día 25: sin cuentas, sin
 * movimientos, sin pagos fijos. Lo que se revisó en el navegador y lo que esta prueba sostiene:
 *
 * - **Hoy pide la primera cuenta una sola vez**: el héroe «Aquí vas a ver tu plata» con su botón va
 *   primero, y «Primeros pasos» debajo, sin el paso de la cuenta («0 de 3»). Antes la tarjeta iba
 *   arriba con «Crea tu primera cuenta» y el héroe repetía «Crear mi primera cuenta» justo debajo.
 * - **El pie de «Primeros pasos» dice qué hace**: «Sube un extracto y Movi anota los movimientos»
 *   (era «Deja que la app se llene sola · Documentos») y elige el archivo.
 * - **Ningún «$0» como cifra**, ni en Hoy ni en Plan: «Movi propone hoy» sin nada no se pinta, y la
 *   caja de Plan («Hoy es tu día más justo: tienes $0» sobre una línea plana) no se dibuja sin cuentas.
 * - **El subtítulo de Hoy no se corta** con un corte a mitad de mes, a 360 dp con la letra a ×1,3: es
 *   el nombre del período, no el rango entero.
 *
 * `sdk = [34]` y `NATIVE`: se mide el texto dibujado (ver `NadaSeRecortaEnElInicioTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class ElPrimerDiaTest {

    @get:Rule val composeRule = createComposeRule()

    /** El corte que eligió en la guía: el 25. */
    private val perfil = UserProfile(id = "u1", email = "papa@ejemplo.com", name = "Hernán Villada", avatarColor = "#000000", periodCutoffDay = 25)

    /** Las cuentas que tiene: ninguna, salvo en la prueba de control de la caja. */
    private var cuentas: List<Account> = emptyList()

    private val navegado = mutableListOf<Screen>()

    /** Todo lo que lee alguien que no cargó nada: listas vacías y ceros. */
    private inner class CuentaNueva : RepositorioDePrueba() {
        override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? = null
        override suspend fun getFinanceSummary(scope: Scope) = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0)
        override suspend fun getAccounts(): List<Account> = cuentas
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
        override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
        override suspend fun getSubscriptions() = SubscriptionsResult(emptyList(), monthlyTotalCop = 0)
        override suspend fun getBudgets(): List<Budget> = emptyList()
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getDashboardSummary(scope: Scope) = DashboardSummary()
        override suspend fun getUserProfile(): UserProfile = perfil
        override suspend fun getGastoDelDiaADia() = GastoDelDiaADia(porDia = 20_000L)
    }

    @Before
    fun preparar() {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = perfil.name, email = perfil.email)
        Repositories.sustitutoDePrueba = CuentaNueva()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
    }

    /** La letra del sistema agrandada a ×1,3, como la pide la vuelta. */
    private fun conLetraGrande(contenido: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.3f)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { contenido() } }
            }
        }
        composeRule.waitForIdle()
    }

    private fun entrarAHoy() = conLetraGrande { DashboardScreen(onNavigate = { navegado += it }) }

    private fun cuantos(texto: String, substring: Boolean = false): Int =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hay(texto: String, substring: Boolean = false) = cuantos(texto, substring) > 0

    private fun cuantosTags(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Todos los textos de la pantalla, también los de adentro de un botón o una fila tocable. */
    private fun textos(): List<String> {
        fun juntar(nodo: SemanticsNode): List<String> = listOf(texto(nodo)) + nodo.children.flatMap(::juntar)
        return juntar(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()).filter { it.isNotBlank() }
    }

    /** Un «$0» como cifra: «$0» suelto o seguido de algo que no es otro dígito («$0,5M» no lo es). */
    private val ceroComoCifra = Regex("""\$\s?0(?![\d.,])""")

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    /**
     * ¿El texto se dibuja entero? Ningún renglón con «…» y cada uno cabe en el ancho del nodo (el
     * criterio de `NadaSeRecortaEnElInicioTest`: `hasVisualOverflow` se prende por el redondeo del alto
     * del renglón, no dice si se cortó).
     */
    private fun seVeEntero(texto: String): Boolean {
        val nodo = composeRule.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode()
        val d = disposicion(nodo)
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    /** El encabezado del Inicio como lo arma `DashboardScreen`: avatar, saludo, período, lupa y campana. */
    @androidx.compose.runtime.Composable
    private fun EncabezadoDelInicio(subtitulo: String) {
        com.jvillada.movi.ui.components.ds.Encabezado(
            titulo = saludoDelInicio("Maximiliano"),
            subtitulo = subtitulo,
            inicio = { com.jvillada.movi.ui.components.AvatarButton(onClick = {}) },
            acciones = {
                com.jvillada.movi.ui.components.ds.BotonDeIcono(Icons.Rounded.Search, "Buscar", onClick = {})
                com.jvillada.movi.ui.components.ds.BotonDeIcono(Icons.Rounded.Notifications, "Notificaciones", onClick = {})
            },
        )
    }

    // ── Hoy: un solo llamado ─────────────────────────────────────────────────

    @Test
    fun `Hoy pide la primera cuenta una sola vez, desde el heroe y antes de Primeros pasos`() {
        entrarAHoy()

        assertTrue(hay("Aquí vas a ver tu plata"))
        assertEquals(1, cuantos("primera cuenta", substring = true), "un solo llamado a crear la cuenta: ${textos()}")
        assertTrue(hay("Crear mi primera cuenta"))
        assertFalse(hay("Crea tu primera cuenta"), "«Primeros pasos» no repite el paso que el héroe pide")

        // El héroe primero; la guía, debajo.
        val heroe = composeRule.onNodeWithTag(TAG_TARJETA_DEL_HERO, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val guia = composeRule.onNodeWithText("Primeros pasos", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(heroe.bottom <= guia.top, "el héroe ($heroe) va antes que «Primeros pasos» ($guia)")
        // Y la guía cuenta solo lo que lista.
        assertTrue(hay("0 de 3"))
    }

    @Test
    fun `el boton del heroe abre la hoja de crear cuenta`() {
        entrarAHoy()
        composeRule.onNodeWithText("Crear mi primera cuenta", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        // La hoja de «Nueva cuenta»: pide el nombre antes de guardar.
        assertTrue(hay("Falta el nombre"))
    }

    @Test
    fun `el pie de Primeros pasos dice que hace y elige el papel`() {
        var subio = false
        conLetraGrande {
            PrimerosPasosCard(
                data = DashboardData(accounts = emptyList()),
                onNavigate = { navegado += it },
                onShowCreateSheet = {},
                onSubirUnPapel = { subio = true },
                conElPasoDeLaCuenta = false,
            )
        }
        assertFalse(hay("Deja que la app se llene sola"))
        assertFalse(hay("Documentos"))
        composeRule.onNodeWithText(SUBIR_UN_EXTRACTO, useUnmergedTree = true).performClick()
        assertTrue(subio)
    }

    @Test
    fun `desde Mas, Primeros pasos sigue con sus cuatro pasos`() {
        conLetraGrande {
            PrimerosPasosCard(
                data = DashboardData(accounts = emptyList()),
                onNavigate = {},
                onShowCreateSheet = {},
                onSubirUnPapel = {},
            )
        }
        assertTrue(hay("Crea tu primera cuenta"))
        assertTrue(hay("0 de 4"))
    }

    // ── Hoy: nada vacío, ningún $0 ───────────────────────────────────────────

    @Test
    fun `Hoy no muestra Movi propone hoy vacio ni ningun $0`() {
        entrarAHoy()

        assertEquals(0, cuantosTags(TAG_PROPUESTAS_DEL_INICIO))
        assertFalse(hay("Movi propone hoy"))
        assertFalse(hay("Nada por decidir", substring = true))
        val ceros = textos().filter { ceroComoCifra.containsMatchIn(it) }
        assertTrue(ceros.isEmpty(), "Hoy dice un \$0 como cifra: $ceros")
    }

    @Test
    fun `sin cuentas, visibleSections deja el heroe y la cuadricula`() {
        val vacia = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0),
            accounts = emptyList(),
            credits = emptyList(),
            cards = emptyList(),
            upcoming = emptyList(),
            ocurrencias = emptyList(),
            spentByCategory = emptyMap(),
        )
        assertEquals(listOf("HERO_BALANCE", "ACCIONES"), visibleSections(defaultDashboardDefinition(), vacia).map { it.type })
    }

    // ── Hoy: el subtítulo no se corta ────────────────────────────────────────

    @Test
    fun `con un corte a mitad de mes el subtitulo de Hoy es el nombre del periodo y entra entero`() {
        entrarAHoy()

        val ajustes = perfil.ajustesDelPeriodo()
        val periodo = periodoActual(Clock.System.now().toEpochMilliseconds(), ajustes)
        val nombre = tituloDelPeriodo(periodo)
        assertTrue(seVeEntero(nombre), "«$nombre» se corta en el encabezado")
        // El rango entero no va en el encabezado (vive en Plan y Movimientos).
        val rango = rangoLegibleDe(periodo, ajustes)!!
        assertFalse(hay(rango), "el encabezado ya no dice «$rango»")
    }

    @Test
    fun `el nombre de periodo mas largo entra a 360 dp con letra grande, y el rango de antes no`() {
        // «Septiembre 2026» es el nombre más ancho; con un nombre de dueño largo, al lado del avatar y
        // de los dos íconos. Debajo, el subtítulo de antes, para que la prueba muestre lo que arregla.
        val ajustes = PeriodSettings(cutoffDay = 25)
        val septiembre = PeriodoFinanciero(2026, 9)
        val subtitulo = periodoDelEncabezado(DashboardData(periodoActual = septiembre, ajustesDePeriodo = ajustes))!!
        assertEquals("Septiembre 2026", subtitulo)
        val deAntes = rangoLegibleDe(septiembre, ajustes)!!
        conLetraGrande {
            androidx.compose.foundation.layout.Column {
                EncabezadoDelInicio(subtitulo)
                EncabezadoDelInicio(deAntes)
            }
        }
        assertTrue(seVeEntero(subtitulo), "«$subtitulo» se corta")
        assertFalse(seVeEntero(deAntes), "«$deAntes» entraba: la prueba no mide lo que dice")
    }

    // ── Plan: sin cuentas, la caja no se dibuja ──────────────────────────────

    private fun entrarAPlan() {
        // Lo que dejó el Inicio: las cuentas ya contestaron, vacías (Plan no las pide).
        DashboardDataCache.data = DashboardData(accounts = cuentas)
        conLetraGrande { PlanScreen(onNavigate = { navegado += it }) }
    }

    @Test
    fun `Plan sin cuentas no dibuja la caja ni dice ningun $0`() {
        entrarAPlan()

        assertTrue(hay("Aquí van tus pagos fijos"), "el vacío que enseña de los pagos sigue")
        assertEquals(0, cuantosTags(TAG_CAJA_PROYECTADA))
        assertFalse(hay("día más justo", substring = true))
        val ceros = textos().filter { ceroComoCifra.containsMatchIn(it) }
        assertTrue(ceros.isEmpty(), "Plan dice un \$0 como cifra: $ceros")
    }

    @Test
    fun `Plan con una cuenta si dibuja la caja`() {
        cuentas = listOf(Account("a1", "Bancolombia", AccountType.SAVINGS, 2_000_000))
        entrarAPlan()
        composeRule.waitUntil(5_000) { cuantosTags(TAG_GRAFICA_DE_LA_CAJA) > 0 }
        assertEquals(1, cuantosTags(TAG_CAJA_PROYECTADA))
    }
}
