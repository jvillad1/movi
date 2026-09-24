package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Patrimonio: «Deudas», «Te deben» y «Cuadre de saldos» (Ola C, tarea 4)
 *
 * `AccountsScreen` pasó de ser solo Cuentas a ser Patrimonio: debajo de los grupos de cuentas
 * gana dos tarjetas nuevas —«Deudas» (el mismo total que el encabezado de Créditos, ver
 * [com.jvillada.movi.ui.credits.totalDebtCop]) y «Te deben» (la puerta que le faltaba a
 * `DestinosScreen`)—. Las tres lecturas (cuentas, créditos+tarjetas, destinos) son independientes,
 * así que se prueban por separado.
 *
 * Fix round 1: «Cuadrar» NO es una acción nueva del encabezado — se probó así (ícono solo) y se
 * revirtió: le quitaba el rótulo a «+ Nueva cuenta» (la única puerta permanente para crear una
 * cuenta) y era redundante con la tarjeta «Cuadre de saldos», que ya existía y ya abre
 * `Screen.CuadreDeSaldos` con su propio rótulo. Esa tarjeta ahora comparte `FilaDeResumenPatrimonio`
 * con «Deudas» y «Te deben» (antes era su propio `Row`), así que entra en el alcance de esta
 * prueba aunque no sea código nuevo de esta tarea.
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]` para medir la tarjeta del patrimonio con el motor de
 * texto real (mismo criterio que `EsqueletoDeCuentasTest`); la pantalla es alta para que la
 * `LazyColumn` componga las tres tarjetas sin desplazar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class TarjetaDeDeudasYTeDebenTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L)

    private val credito = CreditSummary(
        account = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_000_000L),
        terms = null,
        paidPct = null,
    )
    private val tarjeta = CardSummary(
        account = Account("acc_amex", "Amex", AccountType.CREDIT_CARD, balance = 2_000_000L),
        terms = null,
    )
    private val destino = DestinoConocido(
        id = "dst_1",
        nombre = "Caro",
        numero = "31973270756",
        totales = mapOf("COP" to 500_000L),
        cuantos = 3,
    )

    private var navegoA: Screen? = null

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(
        cuentas: suspend () -> List<Account> = { emptyList() },
        creditos: suspend () -> List<CreditSummary> = { emptyList() },
        tarjetas: suspend () -> List<CardSummary> = { emptyList() },
        destinos: suspend () -> List<DestinoConocido> = { emptyList() },
    ) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = cuentas()
            override suspend fun getCredits(): List<CreditSummary> = creditos()
            override suspend fun getCards(): List<CardSummary> = tarjetas()
            override suspend fun getDestinos(): List<DestinoConocido> = destinos()
        }
        navegoA = null
        composeRule.setContent {
            MoviTheme {
                // La escala de letra ×1,12 que `App.kt` le pone a toda la app — medir «Cuadrar»
                // y «Nueva cuenta» a 390 dp sin ella sería medir otra app (mismo criterio que
                // `LasCuatroPestanasTest`).
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                    Box(Modifier.fillMaxSize()) { AccountsScreen(onNavigate = { navegoA = it }) }
                }
            }
        }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun contarTag(tag: String): Int = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun layoutDelTexto(texto: String): TextLayoutResult {
        val nodo = composeRule.onNodeWithText(texto, useUnmergedTree = true).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        return resultados.single()
    }

    // ── Cargando: esqueleto, sin cifras ni conteos ──────────────────────────────

    @Test
    fun `mientras las dos lecturas cuelgan, sus tarjetas quedan en esqueleto sin cifras ni titulo`() {
        // `cuentas` vacía a propósito: con cuentas de verdad el grupo «Inversión» (sin ninguna)
        // dice «$0» en su subtotal de forma legítima, y esta prueba mide solo las dos tarjetas
        // nuevas — no la pantalla entera.
        val puertaDeudas = CompletableDeferred<List<CreditSummary>>()
        val puertaDestinos = CompletableDeferred<List<DestinoConocido>>()
        montar(
            cuentas = { emptyList() },
            creditos = { puertaDeudas.await() },
            destinos = { puertaDestinos.await() },
        )
        composeRule.waitForIdle()

        assertEquals(1, contarTag(TAG_TARJETA_DE_DEUDAS))
        assertEquals(1, contarTag(TAG_TARJETA_DE_TE_DEBEN))
        assertTrue(!hay("Deudas"), "el título de la tarjeta es parte de los datos, no del esqueleto")
        assertTrue(!hay("Te deben"))
        assertTrue(!hay("\$0"), "nada de cifras inventadas mientras cuelga la lectura")
    }

    // ── Con datos: mismo total que Créditos, mismo conteo que Destinos ──────────

    @Test
    fun `Deudas dice el mismo total y el mismo conteo que el encabezado de Creditos`() {
        montar(creditos = { listOf(credito) }, tarjetas = { listOf(tarjeta) })
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Deudas", useUnmergedTree = true).assertExists()
        // credito.account.balance (40.000.000) + tarjeta.account.balance (2.000.000) — la MISMA
        // cuenta que totalDebtCop, no una recalculada acá.
        composeRule.onNodeWithText("\$42.000.000", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("1 crédito · 1 tarjeta", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Deudas sin ningun credito ni tarjeta dice que no hay deudas registradas`() {
        montar()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sin deudas registradas", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Te deben dice cuantas cuentas hay guardadas`() {
        montar(destinos = { listOf(destino) })
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Te deben", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("1 cuenta guardada", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `Te deben sin ninguna guardada lo dice`() {
        montar()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Aún no hay ninguna guardada", useUnmergedTree = true).assertExists()
    }

    // ── Tocar cada tarjeta navega a su pantalla ─────────────────────────────────

    @Test
    fun `tocar Deudas abre Creditos`() {
        montar(creditos = { listOf(credito) })
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_TARJETA_DE_DEUDAS, useUnmergedTree = true).performClick()

        assertEquals(Screen.Credits, navegoA)
    }

    @Test
    fun `tocar Te deben abre Cuentas de otros`() {
        montar(destinos = { listOf(destino) })
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_TARJETA_DE_TE_DEBEN, useUnmergedTree = true).performClick()

        assertEquals(Screen.Destinos, navegoA)
    }

    // ── «Cuadrar» es la tarjeta «Cuadre de saldos», no una acción del encabezado ─────

    @Test
    fun `tocar Cuadre de saldos abre el cuadre`() {
        // «Cuadre de saldos» vive dentro del bloque de cuentas cargadas (con al menos una): sin
        // eso la pantalla está en el vacío de siempre y esa tarjeta no se dibuja.
        montar(cuentas = { listOf(nu) })
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_TARJETA_DE_CUADRE, useUnmergedTree = true).performClick()

        assertEquals(Screen.CuadreDeSaldos, navegoA)
    }

    /**
     * El encabezado volvió a ser el de siempre —título + «+ Nueva cuenta», con rótulo— así que
     * «Patrimonio» no tiene motivo para recortarse a 390 dp. Sigue siendo una prueba de
     * regresión útil: si algún día se le agrega otra acción, esto avisa si vuelve a apretar.
     */
    @Test
    fun `Patrimonio no se recorta a 390 dp junto a Nueva cuenta`() {
        montar()
        composeRule.waitForIdle()

        val titulo = layoutDelTexto("Patrimonio")
        assertFalse(
            titulo.multiParagraph.didExceedMaxLines,
            "«Patrimonio» se recortó a 390 dp junto a «+ Nueva cuenta»",
        )
    }

    @Test
    fun `Nueva cuenta del encabezado tiene su rotulo de siempre`() {
        montar()
        composeRule.waitForIdle()

        // Con rótulo — `NewItemButton`, no un ícono solo — porque es la única puerta permanente
        // para crear una cuenta una vez que los grupos ya tienen alguna.
        composeRule.onNodeWithText("Nueva cuenta", useUnmergedTree = true).assertExists()
    }

    // ── Error con reintento ───────────────────────────────────────────────────

    @Test
    fun `si Creditos o Cuentas de otros fallan, cada tarjeta dice que no pudo leer`() {
        montar(
            creditos = { throw ApiException(503) },
            destinos = { throw ApiException(503) },
        )
        composeRule.waitForIdle()

        assertTrue(hay("No pudimos cargar tus deudas"))
        assertTrue(hay("No pudimos cargar Cuentas de otros"))
    }

    @Test
    fun `reintentar despues de un error vuelve a leer las tres lecturas`() {
        var lecturasDeCreditos = 0
        montar(
            creditos = {
                if (lecturasDeCreditos++ == 0) throw ApiException(503) else listOf(credito)
            },
        )
        composeRule.waitForIdle()
        assertTrue(hay("No pudimos cargar tus deudas"))

        composeRule.onAllNodesWithText("Reintentar", useUnmergedTree = true).onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals(2, lecturasDeCreditos)
        composeRule.onNodeWithText("Deudas", useUnmergedTree = true).assertExists()
    }

    // ── El alto de arriba no salta ───────────────────────────────────────────────

    /**
     * Las dos tarjetas nuevas son lecturas propias que pueden seguir cargando (o haberse
     * rendido) mientras `accounts` ya contestó — la tarjeta del patrimonio, arriba de todo, no
     * se tiene que mover un pelo por eso.
     */
    @Test
    fun `la tarjeta del patrimonio mide lo mismo con Deudas y Te deben cargando que con las tres listas`() {
        val puertaDeudas = CompletableDeferred<List<CreditSummary>>()
        val puertaDestinos = CompletableDeferred<List<DestinoConocido>>()
        montar(
            cuentas = { listOf(nu) },
            creditos = { puertaDeudas.await() },
            destinos = { puertaDestinos.await() },
        )
        composeRule.waitForIdle()
        val cargando = composeRule.onNodeWithTag(TAG_TARJETA_DEL_PATRIMONIO).getUnclippedBoundsInRoot().height

        puertaDeudas.complete(listOf(credito))
        puertaDestinos.complete(listOf(destino))
        composeRule.waitForIdle()
        val cargado = composeRule.onNodeWithTag(TAG_TARJETA_DEL_PATRIMONIO).getUnclippedBoundsInRoot().height

        val diferencia = abs(cargado.value - cargando.value)
        assertTrue(
            diferencia <= 8f,
            "La tarjeta del patrimonio mide ${cargando.value} dp con Deudas/Te deben cargando y " +
                "${cargado.value} dp con las tres listas — diferencia de $diferencia dp, el máximo son 8 dp",
        )
    }
}
