package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sdui.HeroDeUnVistazo
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 75 · el héroe cambia entero
 *
 * Al volver al Inicio llega un resumen nuevo: la cifra grande, «Tu plata» y «para N días · $X por día»
 * tienen que pasar JUNTAS de lo viejo a lo nuevo. Antes la cifra contaba 600 ms mientras la línea y «Tu
 * plata» ya eran las nuevas (`r5-volver-300.png`: «$1.417.943» junto a «$441.916 por día»).
 *
 * Se avanza el reloj cuadro por cuadro durante todo el cambio y, en cada cuadro, lo que dice la tarjeta
 * tiene que ser entero lo de antes o entero lo de ahora: nunca una mezcla.
 *
 * Escenario de `TeQuedanEnHoyEnPantallaTest`: lunes 21-sep-2026, $10M de ingresos, $3,1M de arriendo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class ElHeroeNoMezclaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = LocalDate(2026, 9, 21)

    private fun datos(gasto: Map<String, Long>, saldo: Long) = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = saldo)),
        upcoming = listOf(
            UpcomingPayment(
                rule = RecurringRule(
                    id = "rr_arriendo", name = "Arriendo", category = "Vivienda", amount = 3_100_000,
                    dayOfMonth = 5, type = TransactionType.EXPENSE,
                ),
                dueDate = "2026-10-05",
                daysUntil = 14,
                status = PaymentStatus.UPCOMING,
            ),
        ),
        ocurrencias = listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-09", dueDate = "2026-09-05", occurred = true)),
        gastoVariablePorDia = gasto,
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    /** Los textos de la tarjeta del héroe, en un conjunto: la cifra, su rótulo, la línea y «Tu plata». */
    private fun textosDelHeroe(): Set<String> =
        composeRule.onAllNodes(hasAnyAncestor(hasTestTag(TAG_TARJETA_DEL_HERO)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }
            .filter { it.isNotBlank() }
            .toSet()

    @Test
    fun `con un resumen nuevo nunca se ve la cifra vieja junto a la linea nueva`() {
        var data by mutableStateOf(datos(gasto = mapOf("2026-08-26" to 100_000L), saldo = 2_345_678L))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoviTheme {
                // Con movimiento: el cambio se anima (con menos movimiento es inmediato, y no habría qué mirar).
                CompositionLocalProvider(LocalMovimientoReducido provides false) {
                    Column(Modifier.fillMaxSize()) {
                        HeroDeUnVistazo(section = ScreenSection(type = "HERO_BALANCE"), data = data, onNavigate = {}, hoy = hoy)
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        val antes = textosDelHeroe()
        assertTrue(antes.any { it.startsWith("Tu plata \$2.345.678") }, "TEXTOS: $antes")

        // Llega el resumen nuevo: otro gasto y otra plata en las cuentas.
        data = datos(gasto = mapOf("2026-08-26" to 100_000L, "2026-09-20" to 500_000L), saldo = 1_845_678L)
        val vistos = mutableListOf<Set<String>>()
        repeat(40) {
            composeRule.mainClock.advanceTimeByFrame()
            vistos += textosDelHeroe()
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        val despues = textosDelHeroe()
        assertNotEquals(antes, despues, "el resumen nuevo cambió lo que dice el héroe")
        assertTrue(despues.any { it.startsWith("Tu plata \$1.845.678") }, "TEXTOS: $despues")

        vistos.forEachIndexed { cuadro, textos ->
            assertTrue(
                textos == antes || textos == despues,
                "En el cuadro $cuadro el héroe mezcló lo viejo con lo nuevo: $textos\nantes: $antes\ndespués: $despues",
            )
        }
        assertTrue(vistos.any { it == antes } || vistos.first() == despues, "el cambio pasa por lo de antes, entero")
        assertEquals(despues, vistos.last(), "y termina en lo nuevo, entero")
    }

    /**
     * Revisión de la 75 · **las cuentas llegan antes que el resumen**: durante la carga, el estado de la
     * pantalla junta «Tu plata» nueva con el resumen viejo. El héroe no lo muestra: mientras la carga está
     * en vuelo se queda con lo de antes, entero, y al terminar cambia entero a lo nuevo.
     */
    @Test
    fun `con las cuentas antes que el resumen, el heroe espera a que termine la carga`() {
        val viejo = datos(gasto = mapOf("2026-08-26" to 100_000L), saldo = 2_345_678L)
        var data by mutableStateOf(viejo)
        var cargando by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalMovimientoReducido provides false, LocalCargandoElInicio provides cargando) {
                    Column(Modifier.fillMaxSize()) {
                        HeroDeUnVistazo(section = ScreenSection(type = "HERO_BALANCE"), data = data, onNavigate = {}, hoy = hoy)
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        val antes = textosDelHeroe()

        // Arranca la carga y llegan las cuentas: el resumen sigue siendo el de antes.
        cargando = true
        data = viejo.copy(accounts = listOf(Account(id = "a1", name = "Ahorros", type = AccountType.SAVINGS, balance = 10_497_508L)))
        val vistos = mutableListOf<Set<String>>()
        repeat(40) {
            composeRule.mainClock.advanceTimeByFrame()
            vistos += textosDelHeroe()
        }
        vistos.forEachIndexed { cuadro, textos ->
            assertEquals(antes, textos, "En el cuadro $cuadro, con la carga en vuelo, el héroe cambió a medias: $textos")
        }

        // Llega el resumen y termina la carga: cambia entero.
        data = datos(gasto = mapOf("2026-08-26" to 100_000L, "2026-09-20" to 500_000L), saldo = 10_497_508L)
        cargando = false
        val despuesDeLaCarga = mutableListOf<Set<String>>()
        repeat(40) {
            composeRule.mainClock.advanceTimeByFrame()
            despuesDeLaCarga += textosDelHeroe()
        }
        composeRule.mainClock.advanceTimeBy(1_000L)
        val despues = textosDelHeroe()
        assertTrue(despues.any { it.startsWith("Tu plata \$10.497.508") }, "TEXTOS: $despues")
        despuesDeLaCarga.forEachIndexed { cuadro, textos ->
            assertTrue(textos == antes || textos == despues, "En el cuadro $cuadro el héroe mezcló: $textos")
        }
    }
}
