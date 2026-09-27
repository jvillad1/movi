package com.jvillada.movi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.CacheDeLecturas
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.budgets.PresupuestosScreen
import com.jvillada.movi.ui.components.TEXTO_ACTUALIZANDO
import com.jvillada.movi.ui.credits.CreditosScreen
import kotlinx.coroutines.CompletableDeferred
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
 * # «Actualizando…» no le corta el título a la pantalla
 *
 * Con lo último recordado a la vista, cada visita arranca diciendo «Actualizando…» en la cabecera.
 * Al lado del alta compacta («Nueva cuenta», «Nuevo crédito», «Nuevo») no entraba a 390 dp con la
 * escala de letra de la app (×1,12) y el título se leía «Patr…», «Cré…», «Presupues…». Se mide con
 * el motor de texto real (`NATIVE`, sdk 34): el título tiene que caber entero en su renglón.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class TituloEnteroMientrasActualizaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val periodo = periodoActual(ahora, PeriodSettings()).prefijo
    private val perfil = UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")
    private val prestamo = CreditSummary(
        account = Account("acc_viejo", "Préstamo viejo", AccountType.LOAN, balance = 3_000_000L),
        terms = null,
        paidPct = null,
    )

    /** Toda lectura queda colgada: lo que se ve es solo lo recordado, con «Actualizando…». */
    private val colgado = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = CompletableDeferred<List<Account>>().await()
        override suspend fun getCredits(): List<CreditSummary> = CompletableDeferred<List<CreditSummary>>().await()
        override suspend fun getCards(): List<CardSummary> = CompletableDeferred<List<CardSummary>>().await()
        override suspend fun getDestinos(): List<DestinoConocido> = CompletableDeferred<List<DestinoConocido>>().await()
        override suspend fun getUserProfile(): UserProfile = CompletableDeferred<UserProfile>().await()
        override suspend fun getBudgets(): List<Budget> = CompletableDeferred<List<Budget>>().await()
        override suspend fun getEventsByDay(): List<EventDay> = CompletableDeferred<List<EventDay>>().await()
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = CompletableDeferred<DashboardSummary>().await()
    }

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = colgado
        recordar(ClaveDeLectura.Perfil, perfil)
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    private fun <T : Any> recordar(clave: ClaveDeLectura<T>, valor: T, periodo: String? = null) =
        CacheDeLecturas.guardar(clave, valor, "u1", ahora, periodo)

    private fun montar(pantalla: @Composable () -> Unit) {
        composeRule.setContent {
            MoviTheme {
                // La escala de letra de Movi: `App.kt` la multiplica por 1,12 en toda la app.
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) { pantalla() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertTituloEntero(titulo: String) {
        assertEquals(
            1,
            composeRule.onAllNodesWithText(TEXTO_ACTUALIZANDO, useUnmergedTree = true).fetchSemanticsNodes().size,
            "la prueba tiene que medir la cabecera MIENTRAS dice «Actualizando…»",
        )
        val nodo = composeRule.onAllNodesWithText(titulo, useUnmergedTree = true).onFirst().fetchSemanticsNode()
        val medidas = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
        val medida = medidas.single()
        assertFalse(medida.isLineEllipsized(0), "«$titulo» sale cortado en la cabecera a 390 dp")
        // Lo que el título pide contra lo que le dieron. No `hasVisualOverflow` ni `didOverflowWidth`:
        // el renglón de `titular` declara un interlineado más bajo que la fuente a ×1,12 (desborde de
        // ALTO sin cortar nada), y con ancho ajustado al contenido el párrafo se arma al ancho máximo
        // y `didOverflowWidth` da verdadero aunque el texto entre.
        assertTrue(
            medida.multiParagraph.maxIntrinsicWidth <= medida.size.width,
            "«$titulo» pide ${medida.multiParagraph.maxIntrinsicWidth} px y le dieron ${medida.size.width}",
        )
    }

    @Test
    fun `Patrimonio`() {
        recordar(ClaveDeLectura.Cuentas, listOf(Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L)))
        recordar(ClaveDeLectura.Creditos, emptyList())
        recordar(ClaveDeLectura.Tarjetas, emptyList())
        recordar(ClaveDeLectura.Destinos, emptyList())
        montar { AccountsScreen(onNavigate = {}) }
        assertTituloEntero("Patrimonio")
    }

    @Test
    fun `Creditos`() {
        recordar(ClaveDeLectura.Creditos, listOf(prestamo))
        recordar(ClaveDeLectura.Tarjetas, emptyList())
        montar { CreditosScreen(onNavigate = {}) }
        assertTituloEntero("Créditos")
    }

    @Test
    fun `Presupuestos`() {
        recordar(ClaveDeLectura.Presupuestos, listOf(Budget("Mercado", 1_000_000L)), periodo)
        recordar(ClaveDeLectura.ResumenDelTablero(Scope.SELF), DashboardSummary(spentByCategory = mapOf("Mercado" to 250_000L)), periodo)
        recordar(ClaveDeLectura.EventosPorDia, emptyList())
        montar { PresupuestosScreen(onNavigate = {}) }
        assertTituloEntero("Presupuestos")
    }
}
