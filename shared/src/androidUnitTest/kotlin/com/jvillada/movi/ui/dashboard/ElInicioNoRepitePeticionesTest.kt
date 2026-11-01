package com.jvillada.movi.ui.dashboard

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.primeruso.revisarLaGuiaDePrimerUso
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 78: el Inicio no repite peticiones
 *
 * Con **el cliente HTTP de verdad** (`WalletRepositoryImpl`) sobre un motor falso que cuenta cada
 * petición, envuelto como lo envuelve la app (`Repositories.paraLaApp`):
 *
 * - al abrir el Inicio —con la guía de primer uso preguntando a la vez, como en `App.kt`— `users/me`
 *   y `accounts` salen **una vez** (la revisión 6 contó dos de cada una);
 * - al volver a la pestaña antes de 30 s **no sale ninguna** (contó 20).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class ElInicioNoRepitePeticionesTest {

    @get:Rule val composeRule = createComposeRule()

    private val pedidos = ConcurrentHashMap<String, AtomicInteger>()
    private val json = Json { encodeDefaults = true }

    /** Los avisos que el server dice que esperan: un aviso que sube el worker lo cambia por fuera. */
    @Volatile private var avisosEnElServer = 6

    private fun respuestaDe(ruta: String): String? = when (ruta) {
        "/api/users/me" -> json.encodeToString(UserProfile.serializer(), UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "#1D4E9E"))
        "/api/accounts" -> json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(Account.serializer()),
            listOf(Account("a1", "Nu", AccountType.SAVINGS, 1_200_000)),
        )
        "/api/finance-summary" -> json.encodeToString(FinanceSummary.serializer(), FinanceSummary(Scope.SELF, balance = 1_200_000, ingresos = 3_000_000, egresos = 1_800_000))
        "/api/dashboard/summary" -> json.encodeToString(DashboardSummary.serializer(), DashboardSummary(pendingSms = avisosEnElServer))
        "/api/caja-proyectada/gasto-del-dia-a-dia" -> json.encodeToString(GastoDelDiaADia.serializer(), GastoDelDiaADia(porDia = 40_000))
        "/api/subscriptions" -> json.encodeToString(SubscriptionsResult.serializer(), SubscriptionsResult(subscriptions = emptyList(), monthlyTotalCop = 0))
        // 304: «la definición que tienes sigue vigente» (el Inicio usa la de siempre).
        "/api/screens/dashboard", "/api/cierre-del-periodo" -> null
        else -> "[]"
    }

    @Before
    fun entrar() {
        InstantaneaDelInicio.sustitutoDePrueba = instantaneaEnMemoria(mutableMapOf())
        SessionManager.save(token = "tok", userId = "u1", name = "Juan", email = "juan@ejemplo.com")
        val motor = MockEngine { pedido ->
            val ruta = pedido.url.encodedPath
            pedidos.getOrPut(ruta) { AtomicInteger() }.incrementAndGet()
            val cuerpo = respuestaDe(ruta)
            when {
                ruta == "/api/screens/dashboard" -> respond("", HttpStatusCode.NotModified)
                ruta == "/api/cierre-del-periodo" -> respond("", HttpStatusCode.NoContent)
                else -> respond(cuerpo ?: "[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        }
        val cliente = HttpClient(motor) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        Repositories.sustitutoDePrueba = Repositories.paraLaApp(WalletRepositoryImpl(cliente, "http://movi.test"))
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
        InstantaneaDelInicio.sustitutoDePrueba = null
    }

    private fun cuantas(ruta: String): Int = pedidos[ruta]?.get() ?: 0
    private fun total(): Int = pedidos.values.sumOf { it.get() }

    /**
     * Espera a que el Inicio termine su carga y a que no salga ninguna petición más.
     *
     * Los topes son de reloj de pared y solo cuentan si algo de verdad no llega: con la máquina cargada
     * (CI, varias suites a la vez) 10 s no alcanzaban y la prueba fallaba sin que nada estuviera mal.
     * «Quieto» son tres vueltas seguidas sin pedidos nuevos, no una: bajo carga, un pedido puede tardar
     * más de 150 ms en salir.
     */
    private fun esperarQueSeQuede() {
        composeRule.waitUntil(ESPERA_MS) { DashboardDataCache.cargadoEn > 0L }
        var antes = total()
        var quietas = 0
        while (quietas < 3) {
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
            Thread.sleep(150)
            val ahora = total()
            quietas = if (ahora == antes) quietas + 1 else 0
            antes = ahora
        }
    }

    @Test
    fun `al abrir el Inicio users me y accounts van una vez, y al volver antes de 30 s no sale nada`() {
        var enElInicio by mutableStateOf(true)
        composeRule.setContent {
            MoviTheme {
                // Lo que `App.kt` hace al llegar al Inicio: la guía de primer uso pregunta por el perfil
                // y las cuentas a la vez que el Inicio carga.
                LaunchedEffect(Unit) { revisarLaGuiaDePrimerUso(Repositories.wallets) }
                if (enElInicio) DashboardScreen(onNavigate = {})
            }
        }
        esperarQueSeQuede()

        assertEquals(1, cuantas("/api/users/me"), "users/me: ${pedidos.mapValues { it.value.get() }}")
        assertEquals(1, cuantas("/api/accounts"), "accounts: ${pedidos.mapValues { it.value.get() }}")
        assertTrue(pedidos.values.all { it.get() == 1 }, "ninguna petición sale dos veces: ${pedidos.mapValues { it.value.get() }}")
        val alAbrir = total()

        // Ir a otra pestaña y volver: el Inicio sale de la composición y entra de nuevo.
        salirYVolver(cambiar = { enElInicio = it })
        esperarQueSeQuede()

        assertEquals(alAbrir, total(), "al volver antes de 30 s no sale ninguna: ${pedidos.mapValues { it.value.get() }}")
    }

    /** Lo que se anota en la app sí vuelve vieja la lectura: la siguiente sale, aunque no pasaron 30 s. */
    @Test
    fun `una escritura propia hace que la lectura siguiente salga`() = kotlinx.coroutines.runBlocking {
        val repo = Repositories.wallets
        repo.getAccounts()
        repo.getAccounts()
        assertEquals(1, cuantas("/api/accounts"))
        repo.ignoreSms("sms-1")
        repo.getAccounts()
        assertEquals(2, cuantas("/api/accounts"))
    }

    private fun hayInicio() =
        composeRule.onAllNodesWithTag(com.jvillada.movi.ui.sdui.TAG_HEROE_EN_TARJETA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Sale del Inicio (y comprueba que salió de la composición), hace [alSalir] y vuelve. */
    private fun salirYVolver(alSalir: () -> Unit = {}, cambiar: (Boolean) -> Unit) {
        composeRule.runOnUiThread { cambiar(false) }
        composeRule.waitUntil(ESPERA_MS) { !hayInicio() }
        alSalir()
        composeRule.runOnUiThread { cambiar(true) }
        composeRule.waitUntil(ESPERA_MS) { hayInicio() }
    }

    private fun montarElInicio(enElInicio: () -> Boolean, conGesto: Boolean = false) {
        composeRule.setContent {
            MoviTheme {
                androidx.compose.runtime.CompositionLocalProvider(com.jvillada.movi.ui.components.LocalTirarParaActualizar provides conGesto) {
                    if (enElInicio()) DashboardScreen(onNavigate = {})
                }
            }
        }
        esperarQueSeQuede()
    }

    /**
     * Revisión de la 78 · **tirar para actualizar dentro de los 30 s sale a la red**: el dueño pidió lo de
     * ahora, no lo recordado.
     */
    @Test
    fun `tirar para actualizar dentro de los 30 s sale a la red`() {
        montarElInicio(enElInicio = { true }, conGesto = true)
        assertEquals(1, cuantas("/api/dashboard/summary"))

        composeRule.onRoot().performTouchInput { swipeDown(startY = top + height * 0.3f, endY = bottom, durationMillis = 400) }
        composeRule.waitUntil(ESPERA_MS) { cuantas("/api/dashboard/summary") >= 2 }
        esperarQueSeQuede()

        assertEquals(2, cuantas("/api/dashboard/summary"), "${pedidos.mapValues { it.value.get() }}")
        assertEquals(2, cuantas("/api/accounts"), "las cuentas también, aunque eran de hace un instante")
    }

    /**
     * Revisión de la 78 · **un aviso que sube el worker se ve al volver**. El worker no pasa por el
     * repositorio de la app: tras subir, olvida lo fresco (`LoFresco`), y volver al Inicio antes de los 30
     * s vuelve a preguntar. En la revisión el Inicio decía «6 avisos» y Actividad «7».
     */
    @Test
    fun `un aviso que entra por el worker se ve al volver al Inicio`() {
        var enElInicio by mutableStateOf(true)
        montarElInicio(enElInicio = { enElInicio })
        assertEquals(6, DashboardDataCache.data?.pendingSms)

        salirYVolver(alSalir = {
            // Lo que hace `SmsSyncWorker` después de una subida buena.
            avisosEnElServer = 7
            com.jvillada.movi.data.olvidarLoFresco()
        }, cambiar = { enElInicio = it })
        composeRule.waitUntil(ESPERA_MS) { cuantas("/api/dashboard/summary") >= 2 && DashboardDataCache.cargadoEn > 0L }
        esperarQueSeQuede()

        assertEquals(2, cuantas("/api/dashboard/summary"))
        assertEquals(7, DashboardDataCache.data?.pendingSms)
    }

    private companion object {
        /** Tope de las esperas por condición: vuelven apenas se cumple, así que solo alarga un fallo real. */
        const val ESPERA_MS = 60_000L
    }
}
