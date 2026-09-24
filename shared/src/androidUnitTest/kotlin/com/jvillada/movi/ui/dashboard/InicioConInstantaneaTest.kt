package com.jvillada.movi.ui.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CapturaDeSms
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **El Inicio montado entero, arrancando en frío con la instantánea del aparato** (ver
 * [InstantaneaDelInicio]): pinta lo último que se supo antes de que conteste nada, dice
 * «Actualizando…», y al llegar lo nuevo lo reemplaza y la línea se va.
 *
 * El repositorio de prueba contesta cuando la prueba abre [puerta]: así se puede mirar la pantalla
 * en el hueco entre «montó» y «llegaron los datos», que es justo el hueco que esto viene a llenar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class InicioConInstantaneaTest {

    @get:Rule val composeRule = createComposeRule()

    private val puerta = CompletableDeferred<Unit>()
    private val puertaDeLaDefinicion = CompletableDeferred<ScreenDefinition?>()
    private var pidioLasCuentas = false

    private fun cuentas(saldo: Long) = listOf(
        Account(id = "a1", name = "Cuenta de ahorros", type = AccountType.SAVINGS, balance = saldo),
    )

    private val deAyer = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0),
        accounts = cuentas(558_350),
        credits = emptyList(),
        cards = emptyList(),
        upcoming = emptyList(),
    )

    @Before
    fun entrar() {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? =
                puertaDeLaDefinicion.await()
            override suspend fun getFinanceSummary(scope: Scope): FinanceSummary {
                puerta.await(); return FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0)
            }
            override suspend fun getAccounts(): List<Account> {
                pidioLasCuentas = true
                puerta.await(); return cuentas(1_234_000)
            }
            override suspend fun getCredits(): List<CreditSummary> { puerta.await(); return emptyList() }
            override suspend fun getCards(): List<CardSummary> { puerta.await(); return emptyList() }
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> { puerta.await(); return emptyList() }
            // Sin el resumen del Inicio la carga no se sella (revisión final): hace falta acá.
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary {
                puerta.await(); return DashboardSummary(pendingSms = 1)
            }
            // El resto (metas, perfil, presupuestos…) explota en RepositorioDePrueba y el Inicio
            // lo trata como una lectura secundaria caída: no se pinta, y no importa acá.
        }
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
    }

    private fun montar() = composeRule.setContent { MoviTheme { DashboardScreen(onNavigate = {}) } }

    private fun cuantas(texto: String): Int =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `pinta la instantanea, dice Actualizando y al llegar lo nuevo la reemplaza`() {
        InstantaneaDelInicio.delAparato.guardarDatos("u1", deAyer)
        assertNotNull(InstantaneaDelInicio.delAparato.datos("u1"), "la instantánea no quedó guardada")

        montar()
        composeRule.waitForIdle()

        assertTrue(cuantas("\$558.350") > 0, "la cifra de la instantánea se pinta antes de que conteste nada")
        assertEquals(1, cuantas("Actualizando…"))

        puertaDeLaDefinicion.complete(null)
        puerta.complete(Unit)
        composeRule.waitForIdle()

        assertTrue(cuantas("\$1.234.000") > 0, "llegó la cifra nueva")
        assertEquals(0, cuantas("\$558.350"), "y no queda nada de la de ayer")
        assertEquals(0, cuantas("Actualizando…"))
        assertTrue(DashboardDataCache.cargadoEn > 0L, "una carga buena sí sella")
        // La carga buena reescribe la instantánea: el próximo arranque en frío pinta esta.
        assertEquals(cuentas(1_234_000), InstantaneaDelInicio.delAparato.datos("u1")?.accounts)
    }

    /**
     * Revisión final, punto 4: con la instantánea en pantalla, «Actualizando…» está desde el
     * PRIMER cuadro — `loading` nacía en `false` y el efecto lo prendía después, así que el primer
     * cuadro mostraba lo de ayer sin decir que se estaba actualizando.
     *
     * `autoAdvance = false` congela el reloj de cuadros: lo que se lee es la primera composición,
     * antes de que corra ningún `LaunchedEffect`.
     */
    @Test
    fun `con instantanea dice Actualizando desde el primer cuadro`() {
        InstantaneaDelInicio.delAparato.guardarDatos("u1", deAyer)
        composeRule.mainClock.autoAdvance = false

        montar()

        assertEquals(1, cuantas("Actualizando…"), "el primer cuadro ya dice que se está actualizando")
        composeRule.mainClock.autoAdvance = true
    }

    /**
     * Revisión final, punto 2b: todo contesta MENOS `/api/dashboard/summary`. `llegado` alcanza
     * para afirmar (resumen y cuentas llegaron), pero el gasto por categoría, los SMS pendientes y
     * la captura que hay en pantalla son los de la instantánea. No se sella —volver tiene que
     * reintentar— y la instantánea no se reescribe con esos campos viejos como si fueran de hoy.
     */
    @Test
    fun `sin el resumen del Inicio no se sella ni se reescribe la instantanea`() {
        val deAyerConResumen = deAyer.copy(
            spentByCategory = mapOf("Comida" to 2_000_000L),
            pendingSms = 3,
            captura = CapturaDeSms(total = 11, ultimo = "2026-09-22 08:15"),
        )
        InstantaneaDelInicio.delAparato.guardarDatos("u1", deAyerConResumen)
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? = null
            override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
                FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0)
            override suspend fun getAccounts(): List<Account> = cuentas(1_234_000)
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = emptyList()
            override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = error("sin señal")
        }

        montar()
        composeRule.waitForIdle()

        assertEquals(0L, DashboardDataCache.cargadoEn, "sin el resumen del Inicio no se sella")
        assertEquals(
            deAyerConResumen,
            InstantaneaDelInicio.delAparato.datos("u1"),
            "la instantánea no se reescribe con el resumen de ayer como si fuera de hoy",
        )
    }

    /**
     * Y una carga buena guarda SOLO lo que contestó: los presupuestos de ayer (su lectura se cae
     * en esta carga) no pasan a la instantánea nueva como si fueran de hoy.
     */
    @Test
    fun `una carga buena guarda solo lo que llego en esta carga`() {
        InstantaneaDelInicio.delAparato.guardarDatos("u1", deAyer.copy(budgets = listOf(Budget("Comida", 1_000_000))))

        montar()
        puertaDeLaDefinicion.complete(null)
        puerta.complete(Unit)
        composeRule.waitForIdle()

        val guardada = InstantaneaDelInicio.delAparato.datos("u1")
        assertNotNull(guardada)
        assertTrue(DashboardDataCache.cargadoEn > 0L, "la carga salió bien y selló")
        assertEquals(1, guardada.pendingSms, "lo que llegó del resumen del Inicio sí se guarda")
        assertEquals(null, guardada.budgets, "lo que no llegó en esta carga no se guarda")
    }

    /**
     * Arranque en frío sin señal: la instantánea pinta cifras que alcanzan para afirmar, pero ESTA
     * carga no trajo nada. No se sella (volver dentro de 30 s tiene que reintentar) y la instantánea
     * no se reescribe con lo mismo de ayer como si fuera de hoy.
     */
    @Test
    fun `una recarga que falla no sella ni reescribe la instantanea`() {
        InstantaneaDelInicio.delAparato.guardarDatos("u1", deAyer)
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? = null
            override suspend fun getFinanceSummary(scope: Scope): FinanceSummary = error("sin señal")
            override suspend fun getAccounts(): List<Account> = error("sin señal")
            override suspend fun getCredits(): List<CreditSummary> = error("sin señal")
            override suspend fun getCards(): List<CardSummary> = error("sin señal")
            override suspend fun getUpcomingPayments(): List<UpcomingPayment> = error("sin señal")
        }

        montar()
        composeRule.waitForIdle()

        assertEquals(0L, DashboardDataCache.cargadoEn, "una carga que no trajo nada no sella")
        assertEquals(deAyer, InstantaneaDelInicio.delAparato.datos("u1"))
        assertTrue(cuantas("\$558.350") > 0, "y lo de ayer sigue a la vista")
    }

    /**
     * Ola C: el Inicio se llama **«Hoy»** —el título del encabezado vive en el binario, no en la
     * definición SDUI— y su avatar abre **Ajustes**, no Perfil.
     */
    @Test
    fun `el encabezado dice Hoy y el avatar abre Ajustes`() {
        var navegoA: Screen? = null
        composeRule.setContent { MoviTheme { DashboardScreen(onNavigate = { navegoA = it }) } }
        composeRule.waitForIdle()

        assertEquals("Hoy", TITULO_DEL_HOY)
        composeRule.onAllNodesWithText(TITULO_DEL_HOY, useUnmergedTree = true).onFirst().assertIsDisplayed()
        assertEquals(0, cuantas("Inicio"))

        composeRule.onNodeWithText("J", useUnmergedTree = true).performClick()
        assertEquals(Screen.Mas, navegoA)
    }

    @Test
    fun `sin instantanea no dice Actualizando`() {
        montar()
        composeRule.waitForIdle()
        assertEquals(0, cuantas("Actualizando…"))
    }

    /**
     * Con una definición guardada ya no hay parpadeo que evitar: los datos se piden sin esperarla.
     * [puertaDeLaDefinicion] no se abre nunca y las cuentas se piden igual.
     */
    @Test
    fun `con definicion guardada los datos no esperan a la definicion`() {
        InstantaneaDelInicio.delAparato.guardarDefinicion("u1", defaultDashboardDefinition())

        montar()
        composeRule.waitForIdle()

        assertTrue(pidioLasCuentas, "las cuentas se pidieron sin esperar la definición")
    }

    /** Sin definición a mano, el orden de siempre: primero la definición, después los datos. */
    @Test
    fun `sin definicion guardada la definicion va primero`() {
        montar()
        composeRule.waitForIdle()
        assertFalse(pidioLasCuentas, "con la definición todavía en camino no se pidió nada más")

        puertaDeLaDefinicion.complete(null)
        composeRule.waitForIdle()
        assertTrue(pidioLasCuentas)
    }

    @Test
    fun `cerrar sesion borra la instantanea de ese usuario`() {
        InstantaneaDelInicio.delAparato.guardarDatos("u1", deAyer)
        InstantaneaDelInicio.delAparato.guardarDefinicion("u1", defaultDashboardDefinition())

        SessionManager.clear()

        assertEquals(null, InstantaneaDelInicio.delAparato.datos("u1"))
        assertEquals(null, InstantaneaDelInicio.delAparato.definicion("u1"))
    }
}
