package com.jvillada.movi.ui.periodos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.InvalidaElInicioAlEscribir
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.ui.LocalRefreshTick
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
 * # Volver a «Tus períodos» no vuelve a cargar todo — y lo de otro período no se muestra
 *
 * Mismo contrato que `MovimientosRecuerdaLoUltimoTest`, más: los períodos dependen de cuál es el
 * de hoy («En curso»), así que si el período cambió lo recordado no se pinta; y la pantalla ahora
 * también escucha `LocalRefreshTick`, como el resto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class PeriodosRecuerdaLoUltimoTest {

    @get:Rule val composeRule = createComposeRule()

    private val enCurso = ResumenDePeriodo(
        id = "2026-09", nombre = "Septiembre 2026", desde = "2026-08-25", hasta = "2026-09-24",
        enCurso = true, entradas = 500_000L, salidas = 300_000L, movimientos = 6,
    )

    private var periodos: suspend () -> List<ResumenDePeriodo> = { listOf(enCurso) }
    private var pedidos = 0
    private var perfil = UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")

    private val repositorio = object : RepositorioDePrueba() {
        override suspend fun getPeriodos(): List<ResumenDePeriodo> { pedidos++; return periodos() }
        override suspend fun getUserProfile(): UserProfile = perfil
        override suspend fun deleteDestino(id: String) = Unit
    }

    private val tick = mutableIntStateOf(0)

    private val visitas = VisitasDePrueba(composeRule) {
        CompositionLocalProvider(LocalRefreshTick provides tick.intValue) { PeriodosScreen(onNavigate = {}) }
    }

    @Before
    fun entrar() {
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = InvalidaElInicioAlEscribir(repositorio)
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esqueleto() = visitas.cuantasConTag(TAG_FILA_DE_PERIODO_ESQUELETO)

    @Test
    fun `la segunda visita pinta los periodos en el primer cuadro con Actualizando, y al contestar lo quita`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<ResumenDePeriodo>>()
        periodos = { puerta.await() }

        visitas.volverAlPrimerCuadro()

        assertEquals(1, visitas.cuantas("Septiembre 2026"))
        assertEquals(1, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(0, esqueleto())

        visitas.seguir()
        puerta.complete(listOf(enCurso))
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_ACTUALIZANDO))
        assertEquals(1, visitas.cuantas("Septiembre 2026"))
    }

    @Test
    fun `si la lectura falla con los periodos a la vista, se dice y se quedan`() {
        visitas.primeraYSalir()
        periodos = { error("sin red") }

        visitas.volver()

        assertEquals(1, visitas.cuantas("Septiembre 2026"))
        assertEquals(1, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
        assertEquals(0, visitas.cuantas("No pudimos cargar tus períodos"))

        periodos = { listOf(enCurso) }
        composeRule.onNodeWithText("Reintentar", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(0, visitas.cuantas(TEXTO_NO_PUDIMOS_ACTUALIZAR))
    }

    @Test
    fun `una escritura entre visitas deja la segunda con su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<ResumenDePeriodo>>()
        periodos = { puerta.await() }
        runBlocking { Repositories.wallets.deleteDestino("d-otra") }

        visitas.volver()

        assertEquals(0, visitas.cuantas("Septiembre 2026"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }

    @Test
    fun `otra persona en el mismo aparato ve su esqueleto`() {
        visitas.primeraYSalir()
        val puerta = CompletableDeferred<List<ResumenDePeriodo>>()
        periodos = { puerta.await() }
        SessionManager.save(token = "tok2", userId = "u2", name = "Otra", email = "otra@ejemplo.com")

        visitas.volver()

        assertEquals(0, visitas.cuantas("Septiembre 2026"))
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }

    @Test
    fun `si el periodo cambio, lo recordado del anterior no se muestra`() {
        visitas.primeraYSalir()
        val corte = corteQueCambiaElPeriodoDeHoy()
        Assume.assumeTrue(corte != null) // el día 1 del mes todo corte cae en el de calendario
        perfil = perfil.copy(periodCutoffDay = corte!!)
        val puerta = CompletableDeferred<List<ResumenDePeriodo>>()
        periodos = { puerta.await() }

        visitas.volver()

        assertEquals(0, visitas.cuantas("Septiembre 2026"), "«En curso» era el de otro período")
        assertTrue(esqueleto() > 0)
        puerta.complete(emptyList())
    }

    @Test
    fun `se guardo algo desde la hoja de Agregar, y vuelve a leer`() {
        visitas.montar()
        composeRule.waitForIdle()
        val antes = pedidos

        tick.intValue++
        composeRule.waitForIdle()

        assertEquals(antes + 1, pedidos)
    }

    private fun corteQueCambiaElPeriodoDeHoy(): Int? {
        val ahora = Clock.System.now().toEpochMilliseconds()
        val deCalendario = periodoActual(ahora, PeriodSettings()).prefijo
        return (2..28).firstOrNull { periodoActual(ahora, PeriodSettings(cutoffDay = it)).prefijo != deCalendario }
    }
}
