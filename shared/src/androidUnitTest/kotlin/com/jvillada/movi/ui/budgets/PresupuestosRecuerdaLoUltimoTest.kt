package com.jvillada.movi.ui.budgets

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.InvalidaElInicioAlEscribir
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.Scope
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
 * # Volver a Presupuestos no vuelve a cargar todo — y lo de otro período no se muestra
 *
 * Mismo contrato que `MovimientosRecuerdaLoUltimoTest`, más el propio de las lecturas que dependen
 * del período: los presupuestos y el gasto recordados son de UN período, y si el período cambió
 * no se pintan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class PresupuestosRecuerdaLoUltimoTest {

    @get:Rule val composeRule = createComposeRule()

    private val mercado = Budget("Mercado", 1_000_000L)

    private var presupuestos: suspend () -> List<Budget> = { listOf(mercado) }
    private var perfil = UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getBudgets(): List<Budget> = presupuestos()
        override suspend fun getUserProfile(): UserProfile = perfil
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
            DashboardSummary(spentByCategory = mapOf("Mercado" to 250_000L))
        override suspend fun deleteDestino(id: String) = Unit
    }

    private val visitas = VisitasDePrueba(composeRule) { PresupuestosScreen(onNavigate = {}) }

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = InvalidaElInicioAlEscribir(repositorio)
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esqueleto() = visitas.cuantasConTag(TAG_ESQUELETO_FILA_DE_PRESUPUESTO)

    @Test
    fun `la segunda visita pinta los presupuestos en el primer cuadro con Actualizando, y al contestar lo quita`() {
        visitas.primeraYSalir()
        assertEquals(0, esqueleto())
        val puerta = CompletableDeferred<List<Budget>>()
        presupuestos = { puerta.await() }

        visitas.volverAlPrimerCuadro()

        assertEquals(1, visitas.cuantas("Mercado"))
        assertEquals(1, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(0, esqueleto())

        visitas.seguir()
        puerta.complete(listOf(mercado))
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(1, visitas.cuantas("Mercado"))
    }

    @Test
    fun `si la lectura falla con los presupuestos a la vista, se dice y se quedan`() {
        visitas.primeraYSalir()
        presupuestos = { error("sin red") }

        visitas.volver()

        assertEquals(1, visitas.cuantas("Mercado"))
        assertEquals(1, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
        assertEquals(0, visitas.cuantas("No pudimos cargar tus presupuestos"))

        presupuestos = { listOf(mercado) }
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
    }

    @Test
    fun `una escritura entre visitas deja la segunda con su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<Budget>>()
        presupuestos = { puerta.await() }
        runBlocking { Repositories.wallets.deleteDestino("d-otra") }

        visitas.volver()

        assertEquals(0, visitas.cuantas("Mercado"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }

    @Test
    fun `otra persona en el mismo aparato ve su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<Budget>>()
        presupuestos = { puerta.await() }
        SessionManager.save(token = "tok2", userId = "u2", name = "Otra", email = "otra@ejemplo.com")

        visitas.volver()

        assertEquals(0, visitas.cuantas("Mercado"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }

    /**
     * El dueño cambió su día de corte (desde otro aparato: una escritura desde este ya habría
     * vaciado lo recordado) y hoy cae en otro período. Lo recordado era del anterior: en cuanto el
     * perfil lo dice, no se pinta más.
     */
    @Test
    fun `si el periodo cambio, lo recordado del anterior no se muestra`() {
        visitas.primeraYSalir()
        val corte = corteQueCambiaElPeriodoDeHoy()
        // El día 1 del mes todo corte cae en el período de calendario: no hay cambio que probar.
        Assume.assumeTrue(corte != null)
        perfil = perfil.copy(periodCutoffDay = corte!!)
        val puerta = CompletableDeferred<List<Budget>>()
        presupuestos = { puerta.await() }

        visitas.volver()

        assertEquals(0, visitas.cuantas("Mercado"), "lo del período anterior no se pinta en este")
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }

    /** Un día de corte con el que hoy cae en otro período que con el mes de calendario. */
    private fun corteQueCambiaElPeriodoDeHoy(): Int? {
        val ahora = Clock.System.now().toEpochMilliseconds()
        val deCalendario = periodoActual(ahora, PeriodSettings()).prefijo
        return (2..28).firstOrNull { periodoActual(ahora, PeriodSettings(cutoffDay = it)).prefijo != deCalendario }
    }
}
