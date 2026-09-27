package com.jvillada.movi.ui.periodos

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.InvalidaElInicioAlEscribir
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.ui.VisitasDePrueba
import com.jvillada.movi.ui.components.TEXTO_ACTUALIZANDO
import com.jvillada.movi.ui.components.TEXTO_NO_PUDIMOS_ACTUALIZAR
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Volver al detalle de un período no vuelve a cargarlo — y lo de otro período no se muestra
 *
 * Mismo contrato que `MovimientosRecuerdaLoUltimoTest`, más el de las lecturas que dependen del
 * período.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class DetalleDePeriodoRecuerdaLoUltimoTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val mercado = FinancialEvent(
        id = "ev-mercado", accountId = banco.id, type = TransactionType.EXPENSE, amount = 420_000L,
        category = "Mercado", description = "Éxito Laureles", timestamp = 1_790_000_000_000L,
        reconciliationStatus = ReconciliationStatus.RECONCILED, countsAsCashFlow = true,
    )
    private val agosto = DetalleDePeriodo(
        resumen = ResumenDePeriodo(
            id = "2026-08", nombre = "Agosto 2026", desde = "2026-08-01", hasta = "2026-08-31",
            entradas = 1_000_000L, salidas = 420_000L, movimientos = 1,
        ),
        masGrandes = listOf(mercado),
    )

    private var detalle: suspend () -> DetalleDePeriodo = { agosto }
    private var perfil = UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getDetalleDePeriodo(id: String): DetalleDePeriodo = detalle()
        override suspend fun getUserProfile(): UserProfile = perfil
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun deleteDestino(id: String) = Unit
    }

    private val visitas = VisitasDePrueba(composeRule) { DetalleDePeriodoScreen(onNavigate = {}, id = "2026-08") }

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = InvalidaElInicioAlEscribir(repositorio)
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esqueleto() = visitas.cuantasConTag(TAG_DETALLE_DE_PERIODO_ESQUELETO)

    @Test
    fun `la segunda visita pinta el detalle en el primer cuadro con Actualizando, y al contestar lo quita`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<DetalleDePeriodo>()
        detalle = { puerta.await() }

        visitas.volverAlPrimerCuadro()

        assertEquals(1, visitas.cuantas("Éxito Laureles"))
        assertEquals(1, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(0, esqueleto())

        visitas.seguir()
        puerta.complete(agosto)
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(1, visitas.cuantas("Éxito Laureles"))
    }

    @Test
    fun `si la lectura falla con el detalle a la vista, se dice y se queda`() {
        visitas.primeraYSalir()
        detalle = { error("sin red") }

        visitas.volver()

        assertEquals(1, visitas.cuantas("Éxito Laureles"))
        assertEquals(1, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
        assertEquals(0, visitas.cuantas("No pudimos cargar este período"))

        detalle = { agosto }
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
    }

    @Test
    fun `una escritura entre visitas deja la segunda con su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<DetalleDePeriodo>()
        detalle = { puerta.await() }
        runBlocking { Repositories.wallets.deleteDestino("d-otra") }

        visitas.volver()

        assertEquals(0, visitas.cuantas("Éxito Laureles"))
        assertTrue(esqueleto() > 0)
        puerta.complete(agosto)
    }

    @Test
    fun `otra persona en el mismo aparato ve su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<DetalleDePeriodo>()
        detalle = { puerta.await() }
        SessionManager.save(token = "tok2", userId = "u2", name = "Otra", email = "otra@ejemplo.com")

        visitas.volver()

        assertEquals(0, visitas.cuantas("Éxito Laureles"))
        assertTrue(esqueleto() > 0)
        puerta.complete(agosto)
    }

    @Test
    fun `si el periodo cambio, lo recordado del anterior no se muestra`() {
        visitas.primeraYSalir()
        val corte = corteQueCambiaElPeriodoDeHoy()
        Assume.assumeTrue(corte != null) // el día 1 del mes todo corte cae en el de calendario
        perfil = perfil.copy(periodCutoffDay = corte!!)
        val puerta = CompletableDeferred<DetalleDePeriodo>()
        detalle = { puerta.await() }

        visitas.volver()

        assertEquals(0, visitas.cuantas("Éxito Laureles"))
        assertTrue(esqueleto() > 0)
        puerta.complete(agosto)
    }

    private fun corteQueCambiaElPeriodoDeHoy(): Int? {
        val ahora = Clock.System.now().toEpochMilliseconds()
        val deCalendario = periodoActual(ahora, PeriodSettings()).prefijo
        return (2..28).firstOrNull { periodoActual(ahora, PeriodSettings(cutoffDay = it)).prefijo != deCalendario }
    }
}
