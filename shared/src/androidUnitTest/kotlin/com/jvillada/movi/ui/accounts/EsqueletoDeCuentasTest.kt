package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_INMUEBLE
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.components.TAG_FILA_DE_LISTA_ESQUELETO
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
 * # Cuentas carga con la forma de Cuentas
 *
 * Task 7 (ola A) cambió la rueda por seis filas sueltas. La ola B las cambia por la forma real de
 * la pantalla: la tarjeta del patrimonio neto (cifra grande + cuatro renglones) y un grupo con su
 * encabezado y cuatro filas con ícono. [puerta] mantiene `getAccounts()` colgada para mirar ese
 * momento.
 *
 * `@GraphicsMode(NATIVE)` y `sdk = [34]` para medir el alto de la tarjeta del patrimonio cargando
 * contra cargada con texto de verdad (ver el KDoc de `Esqueleto.kt`). `waitForIdle()` convive con
 * el pulso del esqueleto sin colgarse (mismo KDoc).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class EsqueletoDeCuentasTest {

    @get:Rule val composeRule = createComposeRule()

    private val puerta = CompletableDeferred<List<Account>>()

    /** Los cuatro renglones de la tarjeta: tu plata, lo condicionado, los bienes y las deudas. */
    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L)
    private val afc = Account("acc-afc", "AFC", AccountType.SAVINGS, 12_000_000L, condicionadaA = "vivienda")
    private val hipoteca = Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 1_030_600_000L)
    private val casa = Account(
        "acc-casa", "Casa Almendros", AccountType.INVESTMENT, 0L,
        bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = "2026-08-28", deudaId = "acc-1254"),
    )

    /** El «se guardó algo» de la hoja de Agregar: subirlo recarga la pantalla sin sacarla de la composición. */
    private val tick = mutableIntStateOf(0)

    @After
    fun salir() {
        Repositories.sustitutoDePrueba = null
    }

    /** Monta la pantalla con [cuentas] como `getAccounts()` — por default, colgada en [puerta]. */
    private fun montar(cuentas: suspend () -> List<Account> = { puerta.await() }) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = cuentas()
        }
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalRefreshTick provides tick.intValue) {
                    Box(Modifier.fillMaxSize()) { AccountsScreen(onNavigate = {}) }
                }
            }
        }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun contarTag(tag: String): Int = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun `desde el primer cuadro, la tarjeta del patrimonio y un grupo esqueleto, sin cifras ni vacios`() {
        montar()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(1, contarTag(TAG_ESQUELETO_DEL_PATRIMONIO))
        assertEquals(4, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
        assertTrue(!hay("\$0"))
        assertTrue(!hay("Sin cuentas"))
        assertTrue(!hay("No pudimos cargar"), "el primer cuadro no puede decir que falló una lectura que ni empezó")
        assertTrue(hay("Nueva cuenta"))
    }

    @Test
    fun `al llegar las cuentas, el esqueleto se va sin que salte la tarjeta del patrimonio`() {
        montar()
        composeRule.waitForIdle()
        val altoCargando = composeRule.onNodeWithTag(TAG_TARJETA_DEL_PATRIMONIO).getUnclippedBoundsInRoot().height

        puerta.complete(listOf(nu, afc, casa, hipoteca))
        composeRule.waitForIdle()

        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_PATRIMONIO))
        assertEquals(0, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
        composeRule.onNodeWithText("Nu", useUnmergedTree = true).assertIsDisplayed()

        val altoCargado = composeRule.onNodeWithTag(TAG_TARJETA_DEL_PATRIMONIO).getUnclippedBoundsInRoot().height
        val diferencia = abs(altoCargado.value - altoCargando.value)
        assertTrue(
            diferencia <= 8f,
            "La tarjeta del patrimonio mide ${altoCargando.value} dp cargando y ${altoCargado.value} dp " +
                "cargada — diferencia de $diferencia dp, el máximo son 8 dp",
        )
    }

    @Test
    fun `sin cuentas de verdad, el vacio de siempre`() {
        montar()
        composeRule.waitForIdle()

        puerta.complete(emptyList())
        composeRule.waitForIdle()

        assertTrue(hay("Sin cuentas aún"))
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_PATRIMONIO))
        assertEquals(0, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
    }

    @Test
    fun `si la lectura falla, el esqueleto se va y queda el error de siempre`() {
        montar()
        composeRule.waitForIdle()

        puerta.completeExceptionally(ApiException(503))
        composeRule.waitForIdle()

        assertTrue(hay("No pudimos cargar tus cuentas"))
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_PATRIMONIO))
        assertEquals(0, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
    }

    /**
     * Ola B, tarea 2: en el teléfono del dueño la fila real («Nu», con ícono, subtítulo y
     * chevron) medía más alto que `FilaDeListaEsqueleto(conIcono = true)` y la lista bajaba un
     * poco al llegar los datos — no los ~130 dp del hallazgo de `FormaRecordada`, unos pocos dp
     * por fila, pero se notaban porque son varias filas seguidas.
     */
    @Test
    fun `la fila esqueleto con icono mide lo mismo que la fila real de Cuentas`() {
        montar()
        composeRule.waitForIdle()
        val filaEsqueleto = composeRule.onAllNodesWithTag(TAG_FILA_DE_LISTA_ESQUELETO, useUnmergedTree = true)
            .onFirst().getUnclippedBoundsInRoot().height

        puerta.complete(listOf(nu))
        composeRule.waitForIdle()

        val filaReal = composeRule.onNodeWithTag(TAG_FILA_DE_CUENTA, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().height

        val diferencia = abs(filaReal.value - filaEsqueleto.value)
        assertTrue(
            diferencia <= 2f,
            "La fila esqueleto medía ${filaEsqueleto.value} dp y la real ${filaReal.value} dp " +
                "— diferencia de $diferencia dp, el máximo son 2 dp",
        )
    }

    @Test
    fun `una recarga con las cuentas ya pintadas no vuelve al esqueleto`() {
        val recarga = CompletableDeferred<List<Account>>()
        var lecturas = 0
        montar { if (lecturas++ == 0) listOf(nu) else recarga.await() }
        composeRule.waitForIdle()
        assertTrue(hay("Nu"))

        tick.intValue++
        composeRule.waitForIdle()

        assertEquals(2, lecturas, "la recarga tiene que estar en vuelo")
        assertEquals(0, contarTag(TAG_ESQUELETO_DEL_PATRIMONIO))
        assertEquals(0, contarTag(TAG_FILA_DE_LISTA_ESQUELETO))
        composeRule.onNodeWithText("Nu", useUnmergedTree = true).assertIsDisplayed()
    }
}
