package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.jvillada.movi.data.FormaDelHeroe
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.formaEnMemoria
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.HeroDeUnVistazo
import com.jvillada.movi.ui.sdui.TAG_ESQUELETO_ME_ALCANZA
import com.jvillada.movi.ui.sdui.TAG_ESQUELETO_POR_CONFIRMAR
import com.jvillada.movi.ui.sdui.TAG_FRANJA_POR_CONFIRMAR
import com.jvillada.movi.ui.sdui.TAG_HEROE_EN_TARJETA
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 78 · el esqueleto del héroe reserva el alto real
 *
 * La revisión 6 (`r6-frio-900.png` → `r6-frio-1300.png`): en un arranque en frío sin instantánea, la
 * tarjeta del héroe medía ~115 px de esqueleto y ~235 px con los datos —«¿Me alcanza?» y «Por
 * confirmar» llegaban después—, y todo el Inicio bajaba ~110 px a los 1,3 s. Ahora, mientras carga,
 * esos dos renglones se reservan con su esqueleto (o los que la tarjeta llevaba la última vez:
 * [FormaRecordada.heroe]).
 */
@RunWith(RobolectricTestRunner::class)
// El motor de texto de verdad (ver `EsqueletosDelInicioTest`): en LEGACY una línea mide lo que quiere.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class ElHeroeReservaSuAltoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ajustes = PeriodSettings(cutoffDay = 25)
    private val periodo = periodoActual(Clock.System.now().toEpochMilliseconds(), ajustes)
    private val almacen = mutableMapOf<String, String>()

    /** Todo lo del héroe: «Te quedan», «¿Me alcanza?» (con el supuesto del gasto) y «Por confirmar». */
    private val cargado = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 2_345_678)),
        credits = emptyList(),
        cards = emptyList(),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        gastoVariablePorDia = emptyMap(),
        spentByCategory = emptyMap(),
        ajustesDePeriodo = ajustes,
        periodoActual = periodo,
        pendingSms = 5,
        porConfirmarSuman = 1_100_200L,
    )

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        FormaRecordada.sustitutoDePrueba = formaEnMemoria(almacen)
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getGastoDelDiaADia(): GastoDelDiaADia = GastoDelDiaADia(porDia = 20_000L)
        }
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
        FormaRecordada.sustitutoDePrueba = null
    }

    private fun contarTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Las dos tarjetas en una composición: la de arriba cargando (sin nada), la de abajo con todo. */
    private fun montarLasDos() {
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalCargandoElInicio provides true) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            HeroDeUnVistazo(section = ScreenSection(type = "HERO_BALANCE"), data = DashboardData(ajustesDePeriodo = ajustes, periodoActual = periodo), onNavigate = {})
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        HeroDeUnVistazo(section = ScreenSection(type = "HERO_BALANCE"), data = cargado, onNavigate = {})
                    }
                }
            }
        }
        composeRule.waitUntil(5_000) { contarTag(TAG_ME_ALCANZA) == 1 }
    }

    @Test
    fun `la primera vez, el esqueleto mide lo que el heroe con sus dos renglones`() {
        montarLasDos()
        assertEquals(1, contarTag(TAG_ESQUELETO_ME_ALCANZA))
        assertEquals(1, contarTag(TAG_ESQUELETO_POR_CONFIRMAR))
        assertEquals(1, contarTag(TAG_FRANJA_POR_CONFIRMAR))

        val tarjetas = composeRule.onAllNodesWithTag(TAG_HEROE_EN_TARJETA)
        val altoCargando = tarjetas[0].getUnclippedBoundsInRoot().height.value
        val altoCargado = tarjetas[1].getUnclippedBoundsInRoot().height.value
        val diferencia = abs(altoCargado - altoCargando)
        fun alto(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.height / it.layoutInfo.density.density }
        val piezas = listOf(TAG_TARJETA_DEL_HERO, TAG_ESQUELETO_ME_ALCANZA, TAG_ME_ALCANZA, TAG_ESQUELETO_POR_CONFIRMAR, TAG_FRANJA_POR_CONFIRMAR).associateWith { alto(it) }
        assertTrue(
            diferencia <= 8f,
            "El héroe cargando mide $altoCargando dp y cargado $altoCargado dp — diferencia de $diferencia dp, el máximo son 8. Piezas: $piezas",
        )
    }

    @Test
    fun `con la forma recordada sin renglones, el esqueleto no los reserva`() {
        FormaRecordada.delAparato.guardarHeroe("u1", FormaDelHeroe(meAlcanza = false, porConfirmar = false))
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalCargandoElInicio provides true) {
                    HeroDeUnVistazo(section = ScreenSection(type = "HERO_BALANCE"), data = DashboardData(ajustesDePeriodo = ajustes, periodoActual = periodo), onNavigate = {})
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, contarTag(TAG_ESQUELETO_ME_ALCANZA))
        assertEquals(0, contarTag(TAG_ESQUELETO_POR_CONFIRMAR))
    }

    @Test
    fun `al terminar una carga se anota la forma de la tarjeta`() {
        composeRule.setContent {
            MoviTheme { HeroDeUnVistazo(section = ScreenSection(type = "HERO_BALANCE"), data = cargado.copy(pendingSms = 0, porConfirmarSuman = 0L), onNavigate = {}) }
        }
        composeRule.waitUntil(5_000) { contarTag(TAG_ME_ALCANZA) == 1 }
        composeRule.waitUntil(5_000) { FormaRecordada.delAparato.heroe("u1") == FormaDelHeroe(meAlcanza = true, porConfirmar = false) }
    }
}
