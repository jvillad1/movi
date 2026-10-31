package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.BusquedasRecientesStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.buscar.BuscarScreen
import com.jvillada.movi.ui.buscar.TAG_CAMPO_DE_BUSCAR
import com.jvillada.movi.ui.buscar.TAG_TOTALES_DE_BUSCAR
import com.jvillada.movi.ui.components.ds.AnfitrionDelAvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.TAG_ACCION_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_AVISO_DE_DESHACER
import com.jvillada.movi.ui.components.ds.TAG_CIFRA_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_COLA_DEL_SUBTITULO
import com.jvillada.movi.ui.components.ds.TAG_DESHACER_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_RESTO_DEL_AVISO
import com.jvillada.movi.ui.components.ds.TAG_CABEZA_DEL_SUBTITULO
import com.jvillada.movi.ui.dashboard.CONTRA_EL_PERIODO_PASADO
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.sdui.TAG_PROPUESTAS_DEL_INICIO
import com.jvillada.movi.ui.fecha.fechaCortaDelDia
import com.jvillada.movi.ui.sdui.CONTRA_EL_PASADO_MUY_CORTO
import com.jvillada.movi.ui.sdui.PropuestasDelInicioSection
import com.jvillada.movi.ui.sdui.TAG_CONTRA_EL_PERIODO_PASADO
import com.jvillada.movi.ui.sdui.TAG_DIFERENCIA_DEL_WIDGET
import com.jvillada.movi.ui.sdui.WidgetEnQueSeVa
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
 * # Vuelta 87 · el día uno y los bordes (en pantalla)
 *
 * La cola «·4057» de una fila a 360 con la letra grande, «En qué se va» en media columna de 1280 y a 390, el
 * aviso de abajo con un comercio largo, los totales de Buscar y el presupuesto pasado en «Movi propone hoy».
 * Lo puro está en `Vuelta87Test`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi")
class Vuelta87EnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun hay(texto: String, substring: Boolean = false): Boolean =
        composeRule.onAllNodesWithText(texto, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun disposicion(n: SemanticsNode): TextLayoutResult {
        val r = mutableListOf<TextLayoutResult>()
        n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single()
    }

    // ── (8) la cola «·4057» de la fila ───────────────────────────────────────

    private fun montarFila(letra: Float, subtitulo: String = "Vivienda · Bancolombia ·4057") {
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f * letra)) {
                    Box(Modifier.fillMaxSize()) {
                        Fila(
                            titulo = "Arriendo",
                            subtitulo = subtitulo,
                            final = { androidx.compose.material3.Text("-\$1.800.000") },
                            onClick = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun laColaSeVeEntera() {
        val cola = conTag(TAG_COLA_DEL_SUBTITULO).single()
        val d = disposicion(cola)
        assertEquals(1, d.lineCount)
        // Con softWrap apagado el párrafo mide el ancho máximo y `didOverflowWidth` siempre dice que sí: se
        // mide lo que importa, que la línea (los cuatro dígitos incluidos) quepa en lo que el texto ocupa.
        assertTrue(d.getLineRight(0) <= d.size.width + 0.5f, "«·4057» se ve entera: línea ${d.getLineRight(0)} en ${d.size.width}")
        assertEquals(" ·4057", d.layoutInput.text.text)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 con la letra al 130 la cola 4057 nunca se corta y se recorta la cabeza`() {
        montarFila(letra = 1.3f)
        laColaSeVeEntera()
        val cabeza = disposicion(conTag(TAG_CABEZA_DEL_SUBTITULO).single())
        assertTrue(cabeza.isLineEllipsized(0), "lo que cede es «Vivienda · Bancolombia», no los dígitos")
        // El lector (y quien busque el texto) ve el subtítulo entero.
        assertTrue(hay("Vivienda · Bancolombia ·4057"))
    }

    @Test
    fun `a 390 con espacio no se recorta nada`() {
        montarFila(letra = 1f)
        laColaSeVeEntera()
        assertFalse(disposicion(conTag(TAG_CABEZA_DEL_SUBTITULO).single()).isLineEllipsized(0))
    }

    @Test
    fun `un subtitulo sin cuenta con numero sigue siendo un solo texto`() {
        montarFila(letra = 1f, subtitulo = "Nu · Mercado · 28 oct")
        assertTrue(conTag(TAG_COLA_DEL_SUBTITULO).isEmpty())
        assertTrue(hay("Nu · Mercado · 28 oct"))
    }

    // ── (9) En qué se va a media columna ─────────────────────────────────────

    private val datos = DashboardData(
        spentByCategory = mapOf("Vivienda" to 1_800_000L, "Restaurantes" to 747_700L, "Entretenimiento" to 647_100L),
        gastoDelAnterior = mapOf("Vivienda" to 1_800_000L, "Restaurantes" to 253_400L, "Entretenimiento" to 489_000L),
        diasComparados = 12,
    )

    private fun montarWidget(ancho: Float, conLetraDeLaApp: Boolean = true) {
        composeRule.setContent {
            MoviTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * (if (conLetraDeLaApp) 1.12f else 1f))) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.width(ancho.dp).align(Alignment.TopStart)) {
                            WidgetEnQueSeVa(datos, onNavigate = {}, ancho = ancho.dp)
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun nombreEntero(nombre: String) {
        val n = composeRule.onAllNodesWithText(nombre, useUnmergedTree = true).fetchSemanticsNodes().first()
        val d = disposicion(n)
        assertEquals(1, d.lineCount, "«$nombre» en un renglón")
        assertFalse(d.isLineEllipsized(0), "«$nombre» entero, sin «…»")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `a 1280 en media columna ningun nombre se corta y la frase de arriba se acorta`() {
        montarWidget(244f)
        listOf("Vivienda", "Restaurantes", "Entretenimiento").forEach(::nombreEntero)
        val contra = conTag(TAG_CONTRA_EL_PERIODO_PASADO).single()
        assertFalse(disposicion(contra).hasVisualOverflow)
        assertTrue(hay(CONTRA_EL_PASADO_MUY_CORTO), "«Contra el período pasado» no cabe: «vs. el pasado»")
        assertFalse(hay(CONTRA_EL_PERIODO_PASADO))
        // La píldora baja: cada una queda debajo del nombre de su categoría, no entre el nombre y el monto.
        val pildoras = conTag(TAG_DIFERENCIA_DEL_WIDGET)
        assertEquals(3, pildoras.size)
        val restaurantes = composeRule.onAllNodesWithText("Restaurantes", useUnmergedTree = true).fetchSemanticsNodes().first()
        val suPildora = pildoras.first { it.boundsInRoot.top > restaurantes.boundsInRoot.top && it.boundsInRoot.top < restaurantes.boundsInRoot.top + 120f }
        assertTrue(suPildora.boundsInRoot.top >= restaurantes.boundsInRoot.bottom, "la píldora va debajo del nombre")
    }

    @Test
    fun `a 390 el widget sigue con la pildora junto al nombre y la frase entera`() {
        montarWidget(358f)
        listOf("Vivienda", "Restaurantes", "Entretenimiento").forEach(::nombreEntero)
        assertTrue(hay(CONTRA_EL_PERIODO_PASADO))
        val restaurantes = composeRule.onAllNodesWithText("Restaurantes", useUnmergedTree = true).fetchSemanticsNodes().first()
        val pildora = conTag(TAG_DIFERENCIA_DEL_WIDGET).first { it.boundsInRoot.top >= restaurantes.boundsInRoot.top - 8f && it.boundsInRoot.top < restaurantes.boundsInRoot.bottom + 8f }
        assertTrue(pildora.boundsInRoot.left >= restaurantes.boundsInRoot.right - 1f, "la píldora va al lado del nombre")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun `a 360 con el widget a todo el ancho los nombres tampoco se cortan`() {
        montarWidget(328f)
        listOf("Vivienda", "Restaurantes", "Entretenimiento").forEach(::nombreEntero)
        assertTrue(hay(CONTRA_EL_PERIODO_PASADO))
    }

    // ── Menores: el aviso de abajo con un comercio largo ─────────────────────

    private fun montarAviso(texto: String) {
        val aviso = AvisoDeDeshacer()
        aviso.mostrar(texto, accion = AvisoDeDeshacer.Accion("Repartir con Caro", rotuloCorto = "Repartir")) {}
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AnfitrionDelAvisoDeDeshacer(aviso, Modifier.align(Alignment.BottomCenter)) } }
        }
        composeRule.waitForIdle()
    }

    private fun bordes(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `un comercio que no cabe baja a un segundo renglon con el monto y las dos acciones a la vista`() {
        montarAviso("Anotado \$32.000 · Juan Valdez Cafe")
        val aviso = bordes(TAG_AVISO_DE_DESHACER)
        val cifra = bordes(TAG_CIFRA_DEL_AVISO)
        val resto = bordes(TAG_RESTO_DEL_AVISO)
        assertTrue(resto.top >= cifra.bottom - 1f, "el comercio va en su propio renglón: $resto bajo $cifra")
        assertFalse(
            disposicion(composeRule.onNodeWithTag(TAG_RESTO_DEL_AVISO, useUnmergedTree = true).fetchSemanticsNode()).isLineEllipsized(0),
            "«· Juan Valdez Cafe» entero",
        )
        assertFalse(disposicion(composeRule.onNodeWithTag(TAG_CIFRA_DEL_AVISO, useUnmergedTree = true).fetchSemanticsNode()).hasVisualOverflow)
        for (tag in listOf(TAG_ACCION_DEL_AVISO, TAG_DESHACER_DEL_AVISO)) {
            val b = bordes(tag)
            assertTrue(b.width > 0f && b.left >= aviso.left && b.right <= aviso.right, "$tag dentro del aviso")
        }
        assertTrue(resto.right <= bordes(TAG_ACCION_DEL_AVISO).left, "el comercio no se mete debajo de las acciones")
    }

    @Test
    fun `un comercio corto se queda en un renglon`() {
        montarAviso("Anotado \$12.500 · Uber")
        val cifra = bordes(TAG_CIFRA_DEL_AVISO)
        val resto = bordes(TAG_RESTO_DEL_AVISO)
        assertTrue(resto.top < cifra.bottom, "cabe en la misma línea")
    }

    // ── (11) Buscar con totales y un solo formato de fecha ───────────────────

    @Test
    fun `Buscar dice cuanto suman los movimientos y escribe todas las fechas igual`() {
        val ahora = Clock.System.now().toEpochMilliseconds()
        val hace400 = ahora - 400L * 24 * 60 * 60 * 1000
        val nu = Account("nu", "Nu", AccountType.SAVINGS, 0L)
        val ahorros = Account("a1", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 0L)
        fun uber(id: String, monto: Long, ts: Long, cuenta: String) = FinancialEvent(
            id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = monto, category = "Transporte",
            description = "Uber", timestamp = ts, reconciliationStatus = ReconciliationStatus.RECONCILED,
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 25)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(
                EventDay("hoy", -20_000L, listOf(uber("u1", 20_000, ahora, "nu"))),
                EventDay("viejo", -30_000L, listOf(uber("u2", 30_000, hace400, "a1"))),
            )
            override suspend fun getAccounts(): List<Account> = listOf(nu, ahorros)
        }
        BusquedasRecientesStore.almacenDePrueba = mutableMapOf()
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { BuscarScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSCAR).performTextReplacement("uber")
        composeRule.waitUntil(5_000) { conTag(TAG_TOTALES_DE_BUSCAR).isNotEmpty() }

        assertTrue(hay("Gastaste \$50.000 en total · \$20.000 este período"), "el total y lo del período")
        // Un solo formato: «Hoy» y la fecha corta con año si no es el actual; nunca «de septiembre».
        val hoy = epochMillisToAppDate(ahora)
        val vieja = fechaCortaDelDia(epochMillisToAppDate(hace400), hoy)
        assertTrue(hay(vieja, substring = true), "«$vieja» con año, en formato corto")
        assertFalse(hay(" de enero", substring = true) || hay(" de febrero", substring = true) || hay(" de marzo", substring = true) ||
            hay(" de abril", substring = true) || hay(" de mayo", substring = true) || hay(" de junio", substring = true) ||
            hay(" de julio", substring = true) || hay(" de agosto", substring = true) || hay(" de septiembre", substring = true) ||
            hay(" de octubre", substring = true) || hay(" de noviembre", substring = true) || hay(" de diciembre", substring = true))
    }

    // ── Menores: un presupuesto pasado en «Movi propone hoy» ─────────────────

    @Test
    fun `Movi propone hoy dice que Restaurantes va al 167 por ciento de su presupuesto`() {
        val data = DashboardData(
            periodoActual = PeriodoFinanciero(2026, 10),
            ajustesDePeriodo = PeriodSettings(),
            budgets = listOf(Budget("Restaurantes", 600_000)),
            spentByCategory = mapOf("Restaurantes" to 1_002_000L),
        )
        composeRule.setContent {
            MoviTheme { Column { PropuestasDelInicioSection(data, onNavigate = {}, onDescartarCierre = {}) } }
        }
        composeRule.waitForIdle()
        assertTrue(conTag(TAG_PROPUESTAS_DEL_INICIO).isNotEmpty())
        assertTrue(hay("Restaurantes va al 167 % de su presupuesto"))
        assertTrue(hay("Ver presupuestos"))
    }
}
