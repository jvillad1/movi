package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 134 (M-6) · el renglón de las cuotas no salta cuando llegan las cuentas
 *
 * El detalle de una compra con tarjeta decidía con las cuentas si las cuotas iban en su renglón («A 12 cuotas
 * · Cambiar»). Con las cuentas todavía en camino, el encabezado decía «a 12 cuotas» debajo de la cifra; al
 * llegar, ese texto se iba y el renglón aparecía más abajo: todo lo de abajo saltaba. Ahora se decide igual
 * antes y después (`ofreceLasCuotas` con las cuentas que el Inicio ya leyó, o con las cuotas que el movimiento
 * ya trae): de la cifra a las acciones nada se mueve más de 8 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LasCuotasDelDetalleNoSaltanTest {

    @get:Rule val composeRule = createComposeRule()

    private val master = Account("acc-master", "Master Black 3684", AccountType.CREDIT_CARD, 0L, "COP")

    private fun compra(cuotas: Int?) = FinancialEvent(
        id = "ev-nevera",
        accountId = master.id,
        type = TransactionType.EXPENSE,
        amount = 3_600_000L,
        category = "Hogar",
        description = "Alkosto",
        timestamp = LocalDateTime.parse("2026-11-02T10:00:00").toInstant(AppTimeZone.zone).toEpochMilliseconds(),
        cuotas = cuotas,
    )

    @Before fun preparar() { Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {} }

    @After fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DashboardDataCache.clear()
    }

    private var cuentas by mutableStateOf(emptyList<Account>())

    private fun montar(evento: FinancialEvent) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(event = evento, cuentas = cuentas, onDismiss = {}, onCambiado = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun cuantos(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    /** De la cifra a la primera acción, en px. */
    private fun distanciaALasAcciones(): Float {
        val cifra = composeRule.onAllNodesWithTag(TAG_MONTO_DEL_MOVIMIENTO, useUnmergedTree = true).fetchSemanticsNodes().first()
        val accion = composeRule.onAllNodesWithTag(TAG_ROTULO_DE_ACCION_DEL_MOVIMIENTO, useUnmergedTree = true).fetchSemanticsNodes().first()
        return accion.boundsInRoot.top - cifra.boundsInRoot.top
    }

    private val ochoDp: Float get() = with(composeRule.density) { 8.dp.toPx() }

    private fun llegaLaCuentaYNadaSalta() {
        val antes = distanciaALasAcciones()
        cuentas = listOf(master)
        composeRule.waitForIdle()
        assertEquals(1, cuantos(TAG_CUOTAS_EN_EL_DETALLE))
        assertEquals(0, cuantos(TAG_CUOTAS_DEL_MOVIMIENTO))
        val despues = distanciaALasAcciones()
        assertTrue(abs(despues - antes) <= ochoDp, "las acciones saltaron ${despues - antes} px (8 dp = $ochoDp px)")
    }

    @Test
    fun `una compra que ya trae cuotas pinta su renglon antes de que lleguen las cuentas`() {
        montar(compra(cuotas = 12))
        assertEquals(1, cuantos(TAG_CUOTAS_EN_EL_DETALLE), "el renglón está desde el principio")
        assertEquals(0, cuantos(TAG_CUOTAS_DEL_MOVIMIENTO), "el encabezado no dice «a 12 cuotas» para después mudarlo")
        llegaLaCuentaYNadaSalta()
    }

    @Test
    fun `sin cuotas, la cuenta que el Inicio ya leyo decide desde el principio`() {
        DashboardDataCache.data = DashboardData(accounts = listOf(master))
        montar(compra(cuotas = null))
        assertEquals(1, cuantos(TAG_CUOTAS_EN_EL_DETALLE), "«¿A cuántas cuotas? · Elegir» sin esperar las cuentas")
        llegaLaCuentaYNadaSalta()
    }

    @Test
    fun `la regla pura no cambia con o sin las cuentas`() {
        val ahorros = Account("acc-ahorros", "Ahorros 8133", AccountType.SAVINGS, 0L, "COP")
        assertTrue(ofreceLasCuotas(compra(12), emptyList()))
        assertTrue(ofreceLasCuotas(compra(12), listOf(master)))
        assertTrue(ofreceLasCuotas(compra(null), emptyList(), cacheadas = listOf(master)))
        assertEquals(false, ofreceLasCuotas(compra(null), emptyList()), "sin cuenta ni cuotas no se sabe: no se ofrece")
        assertEquals(false, ofreceLasCuotas(compra(12).copy(accountId = ahorros.id), listOf(ahorros)), "la cuenta, cuando llega, manda")
    }
}
