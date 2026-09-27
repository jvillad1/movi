package com.jvillada.movi.ui.credits

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
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.UserProfile
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
 * # Créditos en dos columnas (Ola W3), montado DENTRO de la cáscara y medido
 *
 * Con el rail de verdad (216 en escritorio, 80 en mediano), el tope del tablero y la letra ×1,12
 * ([ConClaseDeAncho]). Se mide con `boundsInRoot`; `sdk = [34]` y `NATIVE` para el texto real.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CreditosEnColumnasEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun prestamo(id: String, nombre: String) = CreditSummary(
        account = Account(id, nombre, AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = id, bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
        ),
        paidPct = 0.5,
    )

    private val libreInversion = prestamo("acc_9695", "Libre inversión 9695")
    private val vehiculo = prestamo("acc_4411", "Vehículo 4411")
    private val libranza = prestamo("acc_7788", "Libranza 7788")
    private val masterBlack = CardSummary(
        account = Account("acc_mb", "Master Black", AccountType.CREDIT_CARD, balance = 3_200_000L),
        terms = null,
    )

    private val perfil = UserProfile(id = "u1", email = "u@local", name = "U", avatarColor = "#000000", periodCutoffDay = 25)

    private fun montar(
        creditos: List<CreditSummary> = listOf(libreInversion, vehiculo, libranza),
        tarjetas: List<CardSummary> = listOf(masterBlack),
        puerta: CompletableDeferred<Unit>? = null,
    ) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> {
                puerta?.await()
                return creditos
            }
            override suspend fun getCards(): List<CardSummary> = tarjetas
            override suspend fun getUserProfile(): UserProfile = perfil
        }
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Credits,
                    activeTab = NavTab.PATRIMONIO,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) { CreditosScreen(onNavigate = {}) }
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

    /** El resumen a lo ancho; Préstamos a la izquierda y Tarjetas a la derecha, debajo. */
    private fun comprobarDosColumnas(anchoDelPanel: Float) {
        esperar("Master Black")
        val resumen = limites(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA)
        val izquierda = limites(TAG_COLUMNA_IZQUIERDA_DE_CREDITOS)
        val derecha = limites(TAG_COLUMNA_DERECHA_DE_CREDITOS)
        // El resumen no se parte: ocupa el panel menos los 16 de cada lado.
        assertEquals(anchoDelPanel - 32f, resumen.width, 0.5f, "el resumen va a lo ancho: $resumen")
        assertTrue(izquierda.top >= resumen.bottom, "las columnas van debajo del resumen")
        assertTrue(derecha.left >= izquierda.right, "$izquierda / $derecha")
        assertEquals(izquierda.top, derecha.top, 0.5f)
        assertEquals(izquierda.width, derecha.width, 0.5f)
        assertTrue(izquierda.width >= ANCHO_MINIMO_DE_COLUMNA_DE_CREDITOS.value, "${izquierda.width}")
        assertTrue(limitesDelTexto("Libre inversión 9695").right <= izquierda.right + 0.5f)
        assertTrue(limitesDelTexto("Master Black").left >= derecha.left - 0.5f)
    }

    // ── Escritorio ─────────────────────────────────────────────────────────────

    /** 1280 − 216 = 1064 de panel: columnas de 508. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 dp prestamos y tarjetas van lado a lado debajo del resumen`() {
        montar()
        comprobarDosColumnas(anchoDelPanel = 1_064f)
    }

    @Test
    @Config(qualifiers = "w1440dp-h900dp-mdpi")
    fun `a 1440 dp tambien`() {
        montar()
        comprobarDosColumnas(anchoDelPanel = 1_224f)
    }

    @Test
    @Config(qualifiers = "w1024dp-h2400dp-mdpi")
    fun `a 1024 dp va en una columna de lectura`() {
        montar()
        esperar("Master Black")
        assertTrue(!hayTag(TAG_COLUMNA_DERECHA_DE_CREDITOS))
        assertTrue(limites(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA).width <= 720f - 32f + 0.5f)
        // Las tarjetas debajo de los préstamos, como en el teléfono.
        assertTrue(limitesDelTexto("Master Black").top > limitesDelTexto("Libranza 7788").top)
    }

    @Test
    @Config(qualifiers = "w999dp-h2400dp-mdpi")
    fun `en la ventana mediana mas ancha va en una columna`() {
        montar()
        esperar("Master Black")
        assertTrue(!hayTag(TAG_COLUMNA_DERECHA_DE_CREDITOS))
        assertTrue(limites(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA).width <= 720f - 32f + 0.5f)
    }

    // (Las de una columna, con una ventana alta: en la lista perezosa, lo que no entra no se compone.)

    // ── Los casos de la Ola I ──────────────────────────────────────────────────

    /**
     * Sin deudas no hay resumen (la Ola I) y el vacío que enseña va solo, en la columna de lectura
     * centrada en el panel: ni una columna vacía al lado ni un vacío estirado a 1.064 dp.
     */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `sin deudas a 1280 dp el vacio que ensena va solo y centrado`() {
        montar(creditos = emptyList(), tarjetas = emptyList())
        esperar("Aquí van tus créditos y tarjetas")
        assertTrue(!hayTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA), "sin deudas no hay «Deuda total»")
        assertTrue(!hayTag(TAG_COLUMNA_IZQUIERDA_DE_CREDITOS))
        assertTrue(!hayTag(TAG_COLUMNA_DERECHA_DE_CREDITOS))
        val vacio = limitesDelTexto("Aquí van tus créditos y tarjetas")
        val centroDelPanel = 216f + 1_064f / 2
        assertTrue(vacio.left >= centroDelPanel - 360f - 0.5f && vacio.right <= centroDelPanel + 360f + 0.5f, "$vacio")
    }

    /** Solo préstamos: se reparten en las dos columnas bajo un solo título, sin una columna vacía. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `solo con prestamos a 1280 dp se reparten en las dos columnas`() {
        montar(tarjetas = emptyList())
        esperar("Libranza 7788")
        val izquierda = limites(TAG_COLUMNA_IZQUIERDA_DE_CREDITOS)
        val derecha = limites(TAG_COLUMNA_DERECHA_DE_CREDITOS)
        assertTrue(limitesDelTexto("Libre inversión 9695").right <= izquierda.right + 0.5f)
        assertTrue(limitesDelTexto("Vehículo 4411").left >= derecha.left - 0.5f)
        assertTrue(limitesDelTexto("Libranza 7788").right <= izquierda.right + 0.5f)
        assertEquals(1, composeRule.onAllNodesWithText("PRÉSTAMOS", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().size, "un solo título para el grupo")
        assertTrue(!hay("TARJETAS"))
    }

    /** Mientras carga, el resumen esqueleto ya va a lo ancho: al llegar no cambia de lugar ni de ancho. */
    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 dp el resumen esqueleto no se mueve al llegar los datos`() {
        val puerta = CompletableDeferred<Unit>()
        montar(puerta = puerta)
        assertTrue(hayTag(TAG_ESQUELETO_DEL_RESUMEN_DE_DEUDA), "carga con esqueleto")
        val cargando = limites(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA)
        assertEquals(1_064f - 32f, cargando.width, 0.5f)

        puerta.complete(Unit)
        esperar("Master Black")
        val cargado = limites(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA)
        assertEquals(cargando.left, cargado.left, 0.5f)
        assertEquals(cargando.top, cargado.top, 0.5f)
        assertEquals(cargando.width, cargado.width, 0.5f)
    }
}
