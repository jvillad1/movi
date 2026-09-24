package com.jvillada.movi.ui.categorias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.DiasPlegadosStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.budgets.PresupuestosScreen
import com.jvillada.movi.ui.components.TAG_TITULO_DE_FILA_ESQUELETO
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.transactions.TAG_FILA_DE_MOVIMIENTO_SUELTO
import com.jvillada.movi.ui.transactions.TransactionsScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * # Task 3 (Ola B): el ícono de categoría se ve en toda la app
 *
 * Lo que una prueba de `aparienciaDe` o de `IconoDeCategoria` sueltas no puede afirmar: que el
 * ícono efectivamente aparece dentro de cada pantalla que lo prometió —Movimientos, el Inicio,
 * Presupuestos— y que agregarlo a la fila de Movimientos no le movió el alto, que era la condición
 * explícita del brief.
 *
 * `@GraphicsMode(NATIVE)` + `sdk = [34]`: para medir el alto real hace falta el motor de texto de
 * verdad — ver el KDoc de `EsqueletosDelInicioTest`, que documenta por qué `LEGACY` no sirve para
 * esto (todo texto con `lineHeight` mide ~17,5 dp sin importar el estilo).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h731dp-xhdpi")
class IconoDeCategoriaEnLaAppTest {

    @get:Rule val composeRule = createComposeRule()

    private val cuenta = Account("a1", "Casa", AccountType.SAVINGS, 500_000L)

    private val gasto = FinancialEvent(
        id = "e1",
        accountId = cuenta.id,
        type = TransactionType.EXPENSE,
        amount = 18_500L,
        category = "Comida",
        description = "Café",
        timestamp = Clock.System.now().toEpochMilliseconds(),
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    private val dia = EventDay(date = HOY_ISO, total = -18_500L, items = listOf(gasto))

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        DiasPlegadosStore.clear()
    }

    private fun contarIconos(): Int =
        composeRule.onAllNodesWithTag(TAG_ICONO_DE_CATEGORIA, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun montarMovimientos() {
        DiasPlegadosStore.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(cuenta)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(dia)
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) }
            }
        }
        esperarTexto("Café")
    }

    @Test
    fun `el icono de categoria aparece en una fila de Movimientos`() {
        montarMovimientos()
        assertTrue(contarIconos() >= 1, "Movimientos tendría que mostrar al menos un ícono de categoría")
    }

    @Test
    fun `el icono de categoria aparece en una barra del Inicio`() {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SduiRenderer(
                        definition = defaultDashboardDefinition(),
                        data = DashboardData(spentByCategory = mapOf("Comida" to 500_000L)),
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(contarIconos() >= 1, "El Inicio tendría que mostrar el ícono junto a «Comida»")
    }

    @Test
    fun `el icono de categoria aparece en una fila de Presupuestos`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getBudgets(): List<Budget> = listOf(Budget("Comida", 1_000_000L))
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { PresupuestosScreen(onNavigate = {}) }
            }
        }
        esperarTexto("Comida")
        assertTrue(contarIconos() >= 1, "Presupuestos tendría que mostrar el ícono junto a «Comida»")
    }

    /**
     * **El renglón de Movimientos no puede crecer.** Se compara contra una reconstrucción exacta
     * de la misma fila SIN el ícono —mismo `Row`, mismo `padding(vertical = 14.dp)`, mismos
     * estilos de `Movi.textos`—, así la comparación no depende de recordar cuánto medía antes de
     * esta tarea: si algún día esos tokens cambian, la referencia cambia con ellos.
     *
     * Las dos filas van en la MISMA composición (no se puede llamar `setContent` dos veces en una
     * prueba), una arriba de la otra, con el mismo ancho — igual que ya hace `EsqueletosDelInicioTest`
     * para comparar el hero cargando contra el cargado.
     */
    @Test
    fun `la fila de Movimientos no cambia de alto al agregarle el icono`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(cuenta)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(dia)
            override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth()) { FilaDeReferenciaSinIcono() }
                    Box(Modifier.fillMaxWidth()) { TransactionsScreen(onNavigate = {}) }
                }
            }
        }
        esperarTexto("Café")

        val altoSinIcono = composeRule.onNodeWithTag(TAG_REFERENCIA_SIN_ICONO).getUnclippedBoundsInRoot().height
        val altoConIcono = composeRule.onNodeWithTag(TAG_FILA_DE_MOVIMIENTO_SUELTO).getUnclippedBoundsInRoot().height
        val diferencia = abs(altoConIcono.value - altoSinIcono.value)
        assertTrue(
            diferencia <= 1f,
            "La fila sin ícono medía ${altoSinIcono.value} dp y con ícono ${altoConIcono.value} dp " +
                "— diferencia de $diferencia dp, el máximo son 1 dp",
        )
    }

    /**
     * **Fix round 1, finding 2.** `FilaDeListaEsqueleto(diametroIconoAlFrente = 36.dp)` tiene que
     * dejar el mismo hueco que el ícono real: se mide el mismo movimiento —cargando, con
     * `getEventsByDay()` colgada, y después cargado— así que no hace falta reconstruir nada a
     * mano, a diferencia de la prueba de alto de arriba (ahí no había forma de "colgar" el alto de
     * un renglón que ya está en pantalla).
     */
    @Test
    fun `el titulo esqueleto de Movimientos arranca en el mismo x que el titulo real`() {
        val puerta = CompletableDeferred<List<EventDay>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")
            override suspend fun getEventsByDay(): List<EventDay> = puerta.await()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) }
            }
        }
        composeRule.waitForIdle()

        val xEsqueleto = composeRule.onAllNodesWithTag(TAG_TITULO_DE_FILA_ESQUELETO, useUnmergedTree = true)
            .onFirst().getUnclippedBoundsInRoot().left

        puerta.complete(listOf(dia))
        composeRule.waitForIdle()

        val xReal = composeRule.onNodeWithText("Café", useUnmergedTree = true).getUnclippedBoundsInRoot().left

        val diferencia = abs(xReal.value - xEsqueleto.value)
        assertTrue(
            diferencia <= 2f,
            "El título esqueleto arrancaba en ${xEsqueleto.value} dp y el real en ${xReal.value} dp " +
                "— diferencia de $diferencia dp, el máximo son 2 dp",
        )
    }

    /** Mismo caso que arriba, para el círculo de 24 dp que agregó `FilaDePresupuestoEsqueleto`. */
    @Test
    fun `el titulo esqueleto de Presupuestos arranca en el mismo x que el titulo real`() {
        val puertaDelGasto = CompletableDeferred<DashboardSummary>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getBudgets(): List<Budget> = listOf(Budget("Comida", 1_000_000L))
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = puertaDelGasto.await()
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { PresupuestosScreen(onNavigate = {}) }
            }
        }
        composeRule.waitForIdle()

        val xEsqueleto = composeRule.onAllNodesWithTag(TAG_TITULO_DE_FILA_ESQUELETO, useUnmergedTree = true)
            .onFirst().getUnclippedBoundsInRoot().left

        puertaDelGasto.complete(DashboardSummary(scope = Scope.SELF, spentByCategory = mapOf("Comida" to 500_000L)))
        composeRule.waitForIdle()

        val xReal = composeRule.onNodeWithText("Comida", useUnmergedTree = true).getUnclippedBoundsInRoot().left

        val diferencia = abs(xReal.value - xEsqueleto.value)
        assertTrue(
            diferencia <= 2f,
            "El título esqueleto arrancaba en ${xEsqueleto.value} dp y el real en ${xReal.value} dp " +
                "— diferencia de $diferencia dp, el máximo son 2 dp",
        )
    }
}

private const val TAG_REFERENCIA_SIN_ICONO = "referencia-fila-sin-icono"

/**
 * **Hoy, en la zona de la app.** Igual que en `AjustesEnMovimientosTest`: la fecha sale del reloj
 * para que el fixture caiga siempre adentro del período que Movimientos muestra al abrirse.
 */
private val HOY_ISO: String = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()).toString()

/**
 * Copia exacta de la geometría de `MovementSingleRow` (mismo `Row`, mismo padding, mismos
 * `Movi.textos`) pero **sin** el `IconoDeCategoria` que esta tarea agregó — la referencia contra
 * la que se mide que el renglón real no creció.
 */
@Composable
private fun FilaDeReferenciaSinIcono() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_REFERENCIA_SIN_ICONO)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Café",
                    style = Movi.textos.titulo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                    letterSpacing = (-0.1).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Comida · Casa",
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Text(
            text = "−$18.500",
            style = Movi.textos.monto,
            fontWeight = FontWeight.Medium,
            color = Movi.colores.sale,
        )
    }
}
