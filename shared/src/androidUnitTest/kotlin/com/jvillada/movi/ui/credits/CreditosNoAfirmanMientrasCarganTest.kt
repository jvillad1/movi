package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.theme.MoviTheme
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
import kotlin.test.assertTrue

/**
 * # Créditos no afirma nada mientras carga (ola B)
 *
 * En el Pixel del dueño, en frío, Créditos decía «Deuda total $0» y «Sin créditos registrados»
 * durante la lectura, y después llegaban $2.191 millones en 12 préstamos. [puerta] deja
 * `getCredits()` colgada para mirar ese momento: esqueletos sí, cifras y vacíos no; al contestar,
 * los datos; con una lista vacía de verdad, el vacío de siempre.
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]` para que el texto mida lo que mide en el teléfono (ver el
 * KDoc de `Esqueleto.kt`): en `LEGACY` todo texto mide ~17,5 dp y comparar la tarjeta cargando
 * (bloques) con la cargada (texto) no diría nada. La pantalla es alta para que la `LazyColumn`
 * componga todo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class CreditosNoAfirmanMientrasCarganTest {

    @get:Rule val composeRule = createComposeRule()

    private val puerta = CompletableDeferred<List<CreditSummary>>()

    /**
     * Amortiza y tiene tasa: su resumen tiene los tres grupos (intereses del mes, los que faltan y
     * la última cuota) más el supuesto — la forma que reserva el esqueleto.
     */
    private val libreInversion = CreditSummary(
        account = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
            insuranceMonthly = 124_800L,
        ),
        paidPct = 0.5,
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = puerta.await()
            override suspend fun getCards(): List<CardSummary> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { CreditosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun contarTag(tag: String): Int = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun `mientras carga no dice deuda cero ni sin creditos, y muestra los esqueletos`() {
        montar()

        assertTrue(!hay("\$0"), "cargando no puede decir \$0")
        assertTrue(!hay("Sin créditos registrados"))
        assertTrue(!hay("Deuda total"), "el rótulo de la cifra espera con la cifra")
        assertEquals(1, contarTag(TAG_ESQUELETO_DEL_RESUMEN_DE_DEUDA))
        assertEquals(3, contarTag(TAG_ESQUELETO_TARJETA_DE_PRESTAMO))
        // El alta del encabezado está desde el primer cuadro: si llegara con los datos, correría el
        // título.
        assertTrue(hay("Nuevo crédito"))
    }

    @Test
    fun `al contestar llegan los datos, sin saltar la tarjeta de arriba ni el titulo`() {
        montar()
        val altoCargando = composeRule.onNodeWithTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA).getUnclippedBoundsInRoot().height
        val tituloCargando = composeRule.onNodeWithText("Créditos", useUnmergedTree = true).getUnclippedBoundsInRoot()

        puerta.complete(listOf(libreInversion))
        composeRule.waitForIdle()

        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_RESUMEN_DE_DEUDA))
        assertEquals(0, contarTag(TAG_ESQUELETO_TARJETA_DE_PRESTAMO))
        assertTrue(hay("Deuda total"))
        assertTrue(hay("Libre inversión 9695"))
        assertTrue(hay("\$40.104.518"))

        val altoCargado = composeRule.onNodeWithTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA).getUnclippedBoundsInRoot().height
        val diferencia = abs(altoCargado.value - altoCargando.value)
        assertTrue(
            diferencia <= 8f,
            "La tarjeta de «Deuda total» mide ${altoCargando.value} dp cargando y ${altoCargado.value} dp " +
                "cargada — diferencia de $diferencia dp, el máximo son 8 dp",
        )
        assertEquals(tituloCargando, composeRule.onNodeWithText("Créditos", useUnmergedTree = true).getUnclippedBoundsInRoot())
    }

    @Test
    fun `con la lista vacia de verdad, el vacio de siempre`() {
        montar()

        puerta.complete(emptyList())
        composeRule.waitForIdle()

        assertTrue(hay("Sin créditos registrados"))
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_RESUMEN_DE_DEUDA))
        assertEquals(0, contarTag(TAG_ESQUELETO_TARJETA_DE_PRESTAMO))
    }
}
