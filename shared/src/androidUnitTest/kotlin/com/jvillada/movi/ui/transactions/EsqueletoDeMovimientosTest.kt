package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.data.FormaDeMovimientos
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.formaEnMemoria
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Task 7 en Movimientos: filas esqueleto, no una rueda, en la primera carga
 *
 * Ola B, tarea 9: la tarjeta única de 6 filas sueltas se convirtió en tres grupos de 3 + 2 + 2
 * filas (7 en total) — la forma real de Movimientos, agrupada por día. Los números de abajo
 * cambiaron con ella; ver el KDoc de `movimientosEsqueleto` en `TransactionsScreen.kt`.
 *
 * Mismo mecanismo que `EsqueletoDeCuentasTest`: [puerta] mantiene `getEventsByDay()` colgada.
 * `getUserProfile()` está resuelta (no es lo que esta prueba mira, y sin ella el snackbar de
 * «no pudimos leer tu período» taparía lo que se está probando); `getAccounts()`,
 * `getCardPaymentCandidates()` y `getMovimientosRechazados()` son lecturas secundarias que
 * `RepositorioDePrueba` u observa el propio código de la pantalla como opcionales.
 */
// `h2400dp`: como en `EsqueletoDeCuentasTest`, alto de sobra para que `LazyColumn` COMPONGA los
// tres grupos de una — sin esto, la virtualización deja el tercer grupo fuera del viewport por
// default de Robolectric y una prueba que cuenta tags ve 2 en vez de 3. `@GraphicsMode(NATIVE)` +
// `sdk = [34]` (fix round 1): hace falta el motor de texto real para medir el Y de un renglón
// contra otro — ver el KDoc de `Esqueleto.kt`.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class EsqueletoDeMovimientosTest {

    @get:Rule val composeRule = createComposeRule()

    private val puerta = CompletableDeferred<List<EventDay>>()

    private fun repositorio() = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")
        override suspend fun getEventsByDay(): List<EventDay> = puerta.await()
    }

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    /**
     * **Whole-branch review, final fix wave.** Antes esta clase usaba `timestamp = 1_758_326_400_000L`
     * fijo (2026-09-20) para el evento de sus pruebas. Con corte 1 (el default de las pruebas
     * viejas) eso deja de estar en el período de HOY apenas cambia el mes de calendario, y con
     * corte 26 (la prueba nueva de la línea del período) deja de estarlo apenas se cruza el 26 —
     * `TransactionsScreen` filtra por el período REAL, calculado con el reloj de la máquina, así
     * que un evento fuera de ese período desaparece de `visibleDays` y la prueba se pone roja sin
     * que nadie haya tocado nada. `Clock.System.now()` es siempre HOY, así que el evento cae
     * siempre en el período que se esté mirando, sea cual sea el corte.
     */
    private fun eventoDeHoy(reconciliationStatus: ReconciliationStatus = ReconciliationStatus.UNCONFIRMED): Pair<FinancialEvent, EventDay> {
        val ahora = Clock.System.now().toEpochMilliseconds()
        val evento = FinancialEvent(
            id = "e1",
            accountId = "a1",
            type = TransactionType.EXPENSE,
            amount = 25_000L,
            category = "Comida",
            description = "Almuerzo",
            timestamp = ahora,
            reconciliationStatus = reconciliationStatus,
        )
        val dia = EventDay(date = epochMillisToAppDate(ahora).toString(), total = 25_000L, items = listOf(evento))
        return evento to dia
    }

    @Test
    fun `sin un dia pintado todavia, se ven 3 grupos y 7 filas esqueleto, y no la rueda`() {
        Repositories.sustitutoDePrueba = repositorio()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(3, composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA_ESQUELETO).fetchSemanticsNodes().size)
        assertEquals(7, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)
    }

    @Test
    fun `al llegar los movimientos, el esqueleto se va y queda la fila real`() {
        Repositories.sustitutoDePrueba = repositorio()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()
        assertEquals(3, composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA_ESQUELETO).fetchSemanticsNodes().size)
        assertEquals(7, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)

        val (_, dia) = eventoDeHoy()
        puerta.complete(listOf(dia))
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA_ESQUELETO).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Almuerzo", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * **Fix round 1, hallazgo 1.** `movimientosEsqueleto` usaba `top = 0.dp` para el primer
     * grupo y `top = 20.dp` para el resto, pero el `Column` de un día real (más abajo, en el
     * `forEach` de `visibleDays`) usa `top = 20.dp` para TODOS los días, primero incluido — así
     * que el primer grupo esqueleto arrancaba 20 dp más arriba que el primer día real, y la
     * lista entera saltaba 20 dp hacia abajo apenas llegaban los datos. Se corrigió a `20.dp`
     * siempre; esta prueba mide el TOP de los dos renglones de encabezado —
     * `TAG_ENCABEZADO_DE_DIA_ESQUELETO` cargando, `TAG_ENCABEZADO_DE_DIA` real— y no solo cuenta
     * tags, que es lo que dejó pasar el error original.
     */
    @Test
    fun `el primer grupo esqueleto arranca en el mismo Y que el primer dia real`() {
        Repositories.sustitutoDePrueba = repositorio()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()

        val yEsqueleto = composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA_ESQUELETO, useUnmergedTree = true)
            .onFirst().getUnclippedBoundsInRoot().top

        // RECONCILED y no el default (UNCONFIRMED): un evento sin confirmar dispara el aviso
        // «N por confirmar» ARRIBA de la lista (ver `avisoDePorConfirmar`), que es una fila
        // más entre el encabezado y el primer día — real, pero ajena a lo que esta prueba
        // mide (el padding del primer grupo). Con RECONCILED no hay nada que confirmar y el
        // único cambio entre las dos capturas es el esqueleto convirtiéndose en la fila real.
        val (_, dia) = eventoDeHoy(reconciliationStatus = ReconciliationStatus.RECONCILED)
        puerta.complete(listOf(dia))
        composeRule.waitForIdle()

        val yReal = composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_DIA, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top

        val diferencia = abs(yReal.value - yEsqueleto.value)
        assertTrue(
            diferencia <= 2f,
            "El encabezado esqueleto arrancaba en ${yEsqueleto.value} dp y el real en ${yReal.value} dp " +
                "— diferencia de $diferencia dp, el máximo son 2 dp",
        )
    }

    /**
     * **Ola B, tarea 2.** La línea del rango del período («Del 25 de agosto al 24 de
     * septiembre…») aparecía recién cuando el perfil contestaba y empujaba toda la lista de
     * abajo — el esqueleto de Movimientos incluido, aunque siguiera cargando. Con [puertaPerfil]
     * colgada por separado de [puerta] (los eventos), se puede mirar el Y del primer grupo
     * esqueleto ANTES de que el perfil conteste (con el esqueleto de la línea reservando su
     * lugar) y compararlo contra el del primer día real una vez que los dos —perfil (corte 26,
     * como el dueño) y eventos— contestaron.
     */
    @Test
    fun `la linea del rango se reserva antes de que el perfil conteste`() {
        val puertaPerfil = CompletableDeferred<UserProfile>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = puertaPerfil.await()
            override suspend fun getEventsByDay(): List<EventDay> = puerta.await()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()

        val yEsqueleto = composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA_ESQUELETO, useUnmergedTree = true)
            .onFirst().getUnclippedBoundsInRoot().top

        puertaPerfil.complete(
            UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado", periodCutoffDay = 26),
        )
        composeRule.waitForIdle()

        val (_, dia) = eventoDeHoy(reconciliationStatus = ReconciliationStatus.RECONCILED)
        puerta.complete(listOf(dia))
        composeRule.waitForIdle()

        val yReal = composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_DIA, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top

        val diferencia = abs(yReal.value - yEsqueleto.value)
        assertTrue(
            diferencia <= 2f,
            "El primer grupo (con la línea del período todavía esqueleto) arrancaba en " +
                "${yEsqueleto.value} dp y el primer día real, con la línea ya puesta, en " +
                "${yReal.value} dp — diferencia de $diferencia dp, el máximo son 2 dp",
        )
    }

    /**
     * **Whole-branch review, final fix wave.** Con corte 1 (nunca hay línea que mostrar) la línea
     * se reservaba igual para todos mientras el perfil no contestaba, así que un dueño con corte 1
     * veía el esqueleto SUBIR ~18 dp apenas el perfil confirmaba que no había nada que reservar.
     * Con `FormaDeMovimientos(lineaDePeriodo = false)` grabada de una carga anterior que salió
     * bien, el esqueleto ya sabe que no hace falta reservar nada — ni el tag esqueleto de la línea
     * aparece.
     */
    @Test
    fun `con la ultima carga sin linea recordada, el esqueleto no la reserva`() {
        SessionManager.save(token = "t", userId = "u-forma", name = "Juan", email = "juan@ejemplo.com")
        FormaRecordada.sustitutoDePrueba = formaEnMemoria(mutableMapOf())
        FormaRecordada.delAparato.guardarMovimientos("u-forma", FormaDeMovimientos(lineaDePeriodo = false))

        val puertaPerfil = CompletableDeferred<UserProfile>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = puertaPerfil.await()
            override suspend fun getEventsByDay(): List<EventDay> = puerta.await()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()

        // Todavía sin contestar el perfil (`puertaPerfil` sigue colgada): es acá, antes de saber
        // el corte de verdad, donde lo recordado tiene que evitar el esqueleto de la línea.
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_LINEA_DE_PERIODO_ESQUELETO, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    /**
     * **Whole-branch review, final fix wave.** El otro lado del mismo hallazgo: sin nada
     * recordado todavía (la primera vez en este aparato) el esqueleto sigue reservando la línea,
     * el comportamiento de siempre.
     */
    @Test
    fun `sin nada recordado, el esqueleto sigue reservando la linea`() {
        SessionManager.save(token = "t", userId = "u-forma", name = "Juan", email = "juan@ejemplo.com")
        FormaRecordada.sustitutoDePrueba = formaEnMemoria(mutableMapOf())

        val puertaPerfil = CompletableDeferred<UserProfile>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = puertaPerfil.await()
            override suspend fun getEventsByDay(): List<EventDay> = puerta.await()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithTag(TAG_LINEA_DE_PERIODO_ESQUELETO, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    /**
     * Ola B, tarea 9: la tercera fase de la regla compartida con las demás listas —«vacío de
     * verdad solo con lista vacía»—. Una lectura que CONTESTA vacía (no colgada, no caída) tiene
     * que mostrar «Sin movimientos aún» sin ningún tag de esqueleto de por medio.
     */
    @Test
    fun `sin movimientos de verdad, el vacio de siempre y ningun esqueleto`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@ejemplo.com", name = "Juan", avatarColor = "morado")
            override suspend fun getEventsByDay(): List<EventDay> = emptyList()
            override suspend fun getAccounts(): List<com.jvillada.movi.shared.model.Account> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sin movimientos aún", substring = true, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_ENCABEZADO_DE_DIA_ESQUELETO).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)
    }

    /**
     * **Fix round 1, punto 2.** Con «Recurrentes» la lista de días nunca se pinta (ni cargada ni
     * cargando: [mostrarLaListaDeDias] la apaga para ese chip), así que las filas esqueleto de
     * arriba tampoco aparecen ahí — y sin este arreglo la primera carga de ese chip se quedaba
     * SIN NINGUNA señal de que algo estaba en camino. La barra de progreso vuelve a cubrir ese
     * caso: `loading && (visibleDays.isNotEmpty() || !hayListaDeDias)`.
     */
    @Test
    fun `con Recurrentes, sin dia pintado todavia, la barra de carga esta pero no las filas esqueleto`() {
        Repositories.sustitutoDePrueba = repositorio()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TransactionsScreen(onNavigate = {}, chipInicial = CHIP_RECURRENTES) } }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_BARRA_DE_CARGA_DE_MOVIMIENTOS).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO).fetchSemanticsNodes().size)
    }
}
