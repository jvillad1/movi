package com.jvillada.movi.ui.accounts

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_INMUEBLE
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Patrimonio en dos columnas (Ola W3), montado DENTRO de la cáscara y medido
 *
 * Con el rail de verdad (216 dp en escritorio, 80 en mediano), el tope del tablero y la letra ×1,12
 * de la app ([ConClaseDeAncho]). Nunca con la pantalla suelta: así se calibró mal la Ola W2. Se mide
 * con `boundsInRoot` (`captureToImage` se cuelga en Robolectric); `sdk = [34]` y `NATIVE` para que
 * el texto se mida con el motor real.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PatrimonioEnColumnasEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L)
    private val bancolombia = Account("acc-bc", "Bancolombia", AccountType.SAVINGS, 2_500_000L)
    private val fondo = Account("acc-fondo", "Fondo de inversión", AccountType.INVESTMENT, 10_000_000L)
    private val hipoteca = Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 1_030_600_000L)
    private val casa = Account(
        "acc-casa", "Casa Almendros", AccountType.INVESTMENT, 0L,
        bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = "2026-08-28", deudaId = "acc-1254"),
    )

    private fun montar(cuentas: List<Account> = listOf(nu, bancolombia, fondo, hipoteca, casa)) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = cuentas
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        }
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Accounts,
                    activeTab = NavTab.PATRIMONIO,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    AccountsScreen(onNavigate = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hayTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto) }
    }

    private fun limites(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun limitesDelTexto(texto: String): Rect =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

    /** El resumen a la izquierda, las cuentas a la derecha, a la misma altura y del mismo ancho. */
    private fun comprobarDosColumnas(anchoDelPanel: Float) {
        esperar("Casa Almendros")
        val resumen = limites(TAG_COLUMNA_DEL_RESUMEN_DE_PATRIMONIO)
        val cuentas = limites(TAG_COLUMNA_DE_LAS_CUENTAS)
        assertTrue(cuentas.left >= resumen.right - 0.5f, "las cuentas van a la derecha: $resumen / $cuentas")
        assertEquals(resumen.top, cuentas.top, 0.5f)
        assertEquals(anchoDelPanel, resumen.width + cuentas.width, 0.5f, "las dos columnas llenan el panel")
        assertEquals(resumen.width, cuentas.width, 0.5f)
        // La tarjeta del patrimonio, Deudas y el cuadre en el resumen; Dinero, Inversión y Bienes al
        // lado.
        val tarjeta = limites(TAG_TARJETA_DEL_PATRIMONIO)
        assertTrue(tarjeta.right <= resumen.right + 0.5f, "$tarjeta")
        assertTrue(limites(TAG_TARJETA_DE_DEUDAS).right <= resumen.right + 0.5f)
        assertTrue(limites(TAG_TARJETA_DE_CUADRE).right <= resumen.right + 0.5f)
        listOf("Nu", "Fondo de inversión", "Casa Almendros").forEach {
            assertTrue(limitesDelTexto(it).left >= cuentas.left, "$it va en la columna de las cuentas")
        }
        // «Cuentas de otros» va justo debajo de las cuentas propias: en su columna, no en el resumen.
        assertTrue(limites(TAG_TARJETA_DE_CUENTAS_DE_OTROS).left >= cuentas.left - 0.5f, "Cuentas de otros va con las cuentas")
        // El grupo Dinero arranca arriba, a la altura de la tarjeta del patrimonio.
        assertEquals(tarjeta.top, composeRule.onAllNodesWithTag(TAG_GRUPO_DE_CUENTAS, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot.top, 0.5f)
    }

    private fun comprobarUnaColumna(anchoMaximo: Float) {
        esperar("Casa Almendros")
        assertTrue(!hayTag(TAG_COLUMNA_DEL_RESUMEN_DE_PATRIMONIO))
        assertTrue(!hayTag(TAG_COLUMNA_DE_LAS_CUENTAS))
        val tarjeta = limites(TAG_TARJETA_DEL_PATRIMONIO)
        // La columna de lectura: 720 menos los 16 + 16 de la lista.
        assertTrue(tarjeta.width <= anchoMaximo - 32f + 0.5f, "una columna de lectura: $tarjeta")
    }

    // ── Escritorio ─────────────────────────────────────────────────────────────

    /** 1280 − 216 del rail = 1064 de panel: dos columnas de 508. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 dp el resumen y las cuentas van lado a lado`() {
        montar()
        comprobarDosColumnas(anchoDelPanel = 1_064f)
        // El panel empieza donde termina el rail.
        assertEquals(216f, limites(TAG_COLUMNA_DEL_RESUMEN_DE_PATRIMONIO).left, 0.5f)
    }

    /** 1440 − 216 = 1224 de panel, por debajo del tope de 1280. */
    @Test
    @Config(qualifiers = "w1440dp-h900dp-mdpi")
    fun `a 1440 dp tambien van lado a lado`() {
        montar()
        comprobarDosColumnas(anchoDelPanel = 1_224f)
    }

    /** 1024 − 216 = 808 de panel: columnas de 380, menos de 420. Una columna, la de lectura. */
    @Test
    @Config(qualifiers = "w1024dp-h900dp-mdpi")
    fun `a 1024 dp va en una columna de lectura`() {
        montar()
        comprobarUnaColumna(anchoMaximo = 720f)
    }

    // ── Mediano ────────────────────────────────────────────────────────────────

    /** El ancho más grande de una ventana mediana: 999 − 80 = 919, topado en 840. Una columna. */
    @Test
    @Config(qualifiers = "w999dp-h900dp-mdpi")
    fun `en la ventana mediana mas ancha va en una columna`() {
        montar()
        comprobarUnaColumna(anchoMaximo = 720f)
    }

    @Test
    @Config(qualifiers = "w768dp-h900dp-mdpi")
    fun `a 768 dp va en una columna`() {
        montar()
        comprobarUnaColumna(anchoMaximo = 720f)
    }

    /**
     * Mientras carga, el esqueleto ya va repartido como la pantalla real: la tarjeta del patrimonio
     * en el resumen y los grupos en la columna de las cuentas. Al llegar los datos nada cambia de
     * columna.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 dp el esqueleto ya va en dos columnas y la tarjeta no se mueve al llegar`() {
        val puerta = CompletableDeferred<Unit>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> {
                puerta.await()
                return listOf(nu, bancolombia, fondo)
            }
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        }
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Accounts,
                    activeTab = NavTab.PATRIMONIO,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) { AccountsScreen(onNavigate = {}) }
            }
        }
        composeRule.waitForIdle()
        assertTrue(hayTag(TAG_ESQUELETO_DEL_PATRIMONIO), "carga con esqueleto")
        val resumen = limites(TAG_COLUMNA_DEL_RESUMEN_DE_PATRIMONIO)
        val cuentas = limites(TAG_COLUMNA_DE_LAS_CUENTAS)
        val tarjetaCargando = limites(TAG_TARJETA_DEL_PATRIMONIO)
        assertTrue(tarjetaCargando.right <= resumen.right + 0.5f)
        val grupoCargando = composeRule.onAllNodesWithTag(TAG_GRUPO_DE_CUENTAS, useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        assertTrue(grupoCargando.left >= cuentas.left, "$grupoCargando")

        puerta.complete(Unit)
        esperar("Fondo de inversión")
        val tarjetaCargada = limites(TAG_TARJETA_DEL_PATRIMONIO)
        assertEquals(tarjetaCargando.left, tarjetaCargada.left, 0.5f)
        assertEquals(tarjetaCargando.top, tarjetaCargada.top, 0.5f)
        assertEquals(tarjetaCargando.width, tarjetaCargada.width, 0.5f)
    }

    // ── Sin cuentas ────────────────────────────────────────────────────────────

    /** Sin una cuenta, la derecha quedaría vacía: el vacío que enseña va en una columna. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `sin cuentas a 1280 dp el vacio va en una columna de lectura`() {
        montar(cuentas = emptyList())
        esperar("Aquí vive lo que tienes y lo que debes")
        assertTrue(!hayTag(TAG_COLUMNA_DE_LAS_CUENTAS))
        val vacio = limitesDelTexto("Aquí vive lo que tienes y lo que debes")
        // Centrado en el panel (216 + 1064 / 2 = 748), dentro de los 720 de lectura.
        assertTrue(vacio.left >= 216f + (1_064f - 720f) / 2 - 0.5f, "$vacio")
        assertTrue(vacio.right <= 216f + (1_064f + 720f) / 2 + 0.5f, "$vacio")
    }
}
