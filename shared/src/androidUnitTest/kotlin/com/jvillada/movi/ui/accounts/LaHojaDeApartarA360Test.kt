package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_VISTA_PREVIA
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.dashboard.PlataDelDisponible
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.datetime.Clock
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
 * # La hoja de apartar, a 360 dp (vuelta 64)
 *
 * Con la letra de la app (×1,12) en el teléfono más angosto: la vista previa de «Te quedan» se dice
 * entera y dentro de la pantalla, la categoría va con «Todas ›» y no con la cuadrícula en línea, abajo
 * hay una sola acción —sin «Cancelar»—, y el gasto de un mes puntual cabe con sus pastillas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LaHojaDeApartarA360Test {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 10_581_200L)

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    /**
     * Lo que el Inicio ya dejó leído: el mes de hoy (de calendario), $10M al empezar, $5M que
     * entraron, $8M de ahorro del período y $5.549.050 gastados → «Te quedan $1.450.950».
     */
    private fun inicioFresco() {
        val hoy = hoyEnAppZone()
        DashboardDataCache.data = DashboardData(
            summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 5_000_000, egresos = 0),
            upcoming = emptyList(),
            ocurrencias = emptyList(),
            gastoVariablePorDia = mapOf(kotlinx.datetime.LocalDate(hoy.year, hoy.monthNumber, 1).toString() to 5_549_050L),
            ajustesDePeriodo = PeriodSettings(cutoffDay = 1),
            periodoActual = PeriodoFinanciero(hoy.year, hoy.monthNumber),
            plataDelDisponible = PlataDelDisponible(
                saldoAlInicio = 10_000_000, entradas = 5_000_000, guardado = 0, ahorroDelPeriodo = 8_000_000,
            ),
            accounts = listOf(nu),
            apartados = emptyList(),
        )
        DashboardDataCache.cargadoEn = Clock.System.now().toEpochMilliseconds()
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {}
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) {
                        ApartadoSheet(account = nu, existente = null, onDismiss = {}, onGuardado = {}, onQuitado = {})
                    }
                }
            }
        }
    }

    private fun dentroDeLaPantalla(nodo: SemanticsNode, que: String) {
        val ancho = 360f * composeRule.density.density
        assertTrue(nodo.boundsInRoot.left >= 0f, "«$que» empieza dentro de la pantalla")
        assertTrue(nodo.boundsInRoot.right <= ancho + 0.5f, "«$que» termina dentro de la pantalla (${nodo.boundsInRoot.right} > $ancho)")
    }

    private fun unoConTexto(texto: String): SemanticsNode =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().single()

    @Test
    fun `la vista previa dice Te quedan antes y despues, entera y adentro`() {
        inicioFresco()
        montar()
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextInput("Colegio")
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[1].performTextInput("2000000")
        composeRule.waitForIdle()

        val frase = "Te quedan pasa de"
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(frase, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val nodo = unoConTexto(frase)
        dentroDeLaPantalla(nodo, frase)
        // Revisión de la vuelta 64 (rev64-06): a 360 el «−» quedaba solo al final del renglón. Ahora
        // «a −$549.050» va en el mismo renglón, entero.
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        val layout = r.single()
        val texto = layout.layoutInput.text.text
        val menos = texto.indexOf('−')
        assertTrue(menos > 0, texto)
        val linea = layout.getLineForOffset(menos)
        assertEquals(linea, layout.getLineForOffset(texto.indexOf('$', menos)), "el «−» y su «$» en el mismo renglón")
        assertEquals(linea, layout.getLineForOffset(menos - 2), "la «a» con su cifra")
        assertEquals(linea, layout.getLineForOffset(texto.length - 1), "la cifra entera en un renglón")
        val aviso = unoConTexto("se resta aparte")
        dentroDeLaPantalla(aviso, "la advertencia del ahorro")
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_VISTA_PREVIA, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `sin monto no hay vista previa`() {
        inicioFresco()
        montar()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag(TAG_VISTA_PREVIA, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `una sola accion abajo, la categoria con Todas y el gasto que cabe`() {
        montar()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("Cancelar", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "sin «Cancelar»")
        dentroDeLaPantalla(unoConTexto("Guardar"), "Guardar")
        val todas = composeRule.onAllNodesWithTag(TAG_TODAS_EN_APARTAR, useUnmergedTree = true).fetchSemanticsNodes().single()
        dentroDeLaPantalla(todas, "Todas ›")

        val casilla = composeRule.onAllNodesWithTag(TAG_GASTO_DEL_APARTADO, useUnmergedTree = true).fetchSemanticsNodes().single()
        dentroDeLaPantalla(casilla, "la casilla del gasto")
        dentroDeLaPantalla(unoConTexto("Es para un gasto de un mes puntual"), "el título de la casilla")
    }

    /**
     * Revisión de la vuelta 64 (rev64-19, rev64-13): en el detalle, a 360 y a 390, la fila del gasto se
     * cortaba justo en el aporte («… aparta $160.000 al m…»). Ahora va en dos renglones y se lee entera.
     */
    @Test
    fun `el aporte mensual del detalle se lee entero a 360`() {
        val hoy = hoyEnAppZone()
        val soat = com.jvillada.movi.shared.model.Apartado(
            id = "apt_soat", accountId = "acc-nu", proposito = "SOAT", monto = 400_000L,
            objetivo = 1_200_000L, mesDelGasto = mesesParaElGasto(hoy)[5].prefijo,
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccount(id: String): Account = nu
            override suspend fun getEvents(accountId: String?): List<com.jvillada.movi.shared.model.FinancialEvent> = emptyList()
            override suspend fun getApartados(): List<com.jvillada.movi.shared.model.Apartado> = listOf(soat)
        }
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) {
                        AccountDetailScreen(onNavigate = {}, accountId = "acc-nu", group = com.jvillada.movi.shared.model.AccountGroup.DINERO)
                    }
                }
            }
        }
        val aporte = "Aparta \$160.000 al mes"
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(aporte, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val nodo = unoConTexto(aporte)
        dentroDeLaPantalla(nodo, aporte)
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        val layout = r.single()
        val ultima = layout.lineCount - 1
        val texto = layout.layoutInput.text.text
        assertTrue(!layout.isLineEllipsized(ultima), "sin «…»: $texto")
        assertEquals(texto.length, layout.getLineEnd(ultima, visibleEnd = true), "«$aporte» entero")
        assertTrue(layout.lineCount <= LINEAS_DEL_GASTO, "en ${layout.lineCount} renglones")
    }
}
