package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 56 · «¿Es la cuota de X?» no empuja el detalle
 *
 * La sugerencia de la vuelta 53 espera `/upcoming` y `/occurrences`, y al llegar un instante después de
 * abrir empujaba las acciones hacia abajo. Ahora, si con lo que el Inicio ya leyó el gasto se parece a
 * una cuota (`cuotaPrevistaParaElDetalle`), la hoja guarda su alto mientras carga: las acciones quedan
 * donde estaban (nada salta más de 8 dp). Un gasto que no se parece a ninguna no reserva nada, y
 * tampoco se mueve.
 *
 * Se mide la distancia del chip de la categoría a las acciones: en el teléfono la hoja crece hacia
 * arriba y en el panel hacia abajo, pero lo que la revisión vio saltar es eso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaSugerenciaDeLaCuotaNoEmpujaTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DashboardDataCache.clear()
    }

    private val ahorros = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 5_000_000L, "COP")
    private val libre = Account("acc-libre", "Libre inversión 9695", AccountType.LOAN, 40_710_555L, "COP")

    private fun gasto(monto: Long) = FinancialEvent(
        id = "ev-$monto",
        accountId = ahorros.id,
        type = TransactionType.EXPENSE,
        amount = monto,
        category = "Otros",
        description = "Transferencia Bancolombia",
        timestamp = LocalDateTime.parse("2026-10-02T10:00:00").toInstant(AppTimeZone.zone).toEpochMilliseconds(),
    )

    private val cuotaDeOctubre = UpcomingPayment(
        rule = RecurringRule(
            id = "$CREDIT_RULE_PREFIX${libre.id}", name = "Cuota Libre inversión 9695", category = CUOTA_CATEGORY,
            amount = 980_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
        ),
        dueDate = "2026-10-05",
        daysUntil = 3,
        status = PaymentStatus.DUE_SOON,
    )

    /** Los vencimientos llegan cuando la prueba lo dice: así se mide antes y después. */
    private val vencimientos = CompletableDeferred<List<UpcomingPayment>>()

    private fun montar(evento: FinancialEvent) {
        // Lo que el Inicio ya leyó: los mismos vencimientos.
        DashboardDataCache.data = DashboardData(
            upcoming = listOf(cuotaDeOctubre),
            ocurrencias = emptyList(),
            ajustesDePeriodo = PeriodSettings(),
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = vencimientos.await()
            override suspend fun getOccurrenceStates(): List<OccurrenceState> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(event = evento, cuentas = listOf(ahorros, libre), onDismiss = {}, onCambiado = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun cuantos(tag: String) =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Del chip de la categoría a la primera acción, en px. */
    private fun distanciaALasAcciones(): Float {
        val chip = composeRule.onAllNodesWithTag(TAG_CHIP_DE_CATEGORIA, useUnmergedTree = true).fetchSemanticsNodes().first()
        val accion = composeRule.onAllNodesWithTag(TAG_ROTULO_DE_ACCION_DEL_MOVIMIENTO, useUnmergedTree = true).fetchSemanticsNodes().first()
        return accion.boundsInRoot.top - chip.boundsInRoot.top
    }

    private val ochoDp: Float get() = with(composeRule.density) { 8.dp.toPx() }

    @Test
    fun laSugerenciaLlegaSinMoverLasAcciones() {
        montar(gasto(980_000L))
        assertEquals(0, cuantos(TAG_SUGERENCIA_DE_LA_CUOTA), "mientras carga no se sugiere nada")
        assertEquals(1, cuantos(TAG_LUGAR_DE_LA_SUGERENCIA_DE_LA_CUOTA), "pero el lugar está guardado")
        val antes = distanciaALasAcciones()

        vencimientos.complete(listOf(cuotaDeOctubre))
        composeRule.waitUntil(5_000) { cuantos(TAG_SUGERENCIA_DE_LA_CUOTA) == 1 }
        composeRule.waitForIdle()

        assertEquals(0, cuantos(TAG_LUGAR_DE_LA_SUGERENCIA_DE_LA_CUOTA), "con la sugerencia, el lugar se va")
        val despues = distanciaALasAcciones()
        assertTrue(abs(despues - antes) <= ochoDp, "las acciones saltaron ${despues - antes} px (8 dp = $ochoDp px)")
    }

    @Test
    fun unGastoQueNoSePareceNoReservaNiSeMueve() {
        montar(gasto(50_000L))
        assertEquals(0, cuantos(TAG_LUGAR_DE_LA_SUGERENCIA_DE_LA_CUOTA), "no se parece a ninguna cuota: no guarda nada")
        val antes = distanciaALasAcciones()

        vencimientos.complete(listOf(cuotaDeOctubre))
        composeRule.waitForIdle()

        assertEquals(0, cuantos(TAG_SUGERENCIA_DE_LA_CUOTA))
        assertEquals(antes, distanciaALasAcciones())
    }

    @Test
    fun siLaLecturaFrescaYaNoLaSugiereElLugarSeSuelta() {
        // El Inicio la daba por pendiente, pero mientras tanto se pagó: `upcoming` ya rodó a noviembre.
        montar(gasto(980_000L))
        assertEquals(1, cuantos(TAG_LUGAR_DE_LA_SUGERENCIA_DE_LA_CUOTA))

        vencimientos.complete(listOf(cuotaDeOctubre.copy(dueDate = "2026-11-05", daysUntil = 34)))
        composeRule.waitForIdle()

        assertEquals(0, cuantos(TAG_LUGAR_DE_LA_SUGERENCIA_DE_LA_CUOTA))
        assertEquals(0, cuantos(TAG_SUGERENCIA_DE_LA_CUOTA), "lo que se muestra sale siempre de la lectura fresca")
    }
}
