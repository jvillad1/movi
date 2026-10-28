package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UsedCategory
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * **Vuelta 45 · Agregar a 360: los chips de categoría no se cortan.** Con la elegida al final de la fila,
 * la fila se corría lo justo para mostrarla y dejaba «+ Nueva» partida contra el borde izquierdo
 * (r43-11). Ahora se corre hasta el borde de una pastilla: ninguna queda a medias en la izquierda, y la
 * elegida se ve entera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi")
class LosChipsNoSeCortanA360Test {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private val nombres = listOf("Restaurantes", "Mercado", "Transporte", "Comida", "Salud")

    private fun montar(elegida: String) {
        UsedCategoriesCache.recordFromServer(
            nombres.mapIndexed { i, n -> UsedCategory(n, listOf(TransactionType.EXPENSE), usosRecientes = 20 - i) },
        )
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> =
                listOf(Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_000_000))
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        QuickAddScreen(onDismiss = {}, presetCategoria = elegida)
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    // El encabezado «Categoría · Todas ›» va con el margen de la pantalla adentro; la fila de pastillas se
    // desliza hasta el borde de la tarjeta, un margen más afuera: ese es el borde que corta.
    private fun izquierdaDeLaFila(): Dp =
        composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_LA_CATEGORIA, useUnmergedTree = true).getUnclippedBoundsInRoot().left -
            Medida.margenDePantalla

    private fun ningunaCortadaALaIzquierda() {
        val borde = izquierdaDeLaFila()
        (listOf("Nueva") + nombres).forEach { nombre ->
            val caja = composeRule.onNodeWithText(nombre).getUnclippedBoundsInRoot()
            assertTrue(
                !(caja.left < borde && caja.right > borde),
                "«$nombre» queda cortada en el borde izquierdo (${caja.left}..${caja.right}, borde $borde)",
            )
        }
    }

    @Test
    fun `con la elegida al final, ninguna pastilla queda cortada y la elegida se ve entera`() {
        montar(elegida = "Salud")
        ningunaCortadaALaIzquierda()
        val salud = composeRule.onNodeWithText("Salud").getUnclippedBoundsInRoot()
        val encabezado = composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_LA_CATEGORIA, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(salud.right <= encabezado.right + Medida.margenDePantalla, "la elegida se ve entera (${salud.right} > ${encabezado.right})")
    }

    @Test
    fun `con la elegida en el medio, ninguna pastilla queda cortada`() {
        montar(elegida = "Transporte")
        ningunaCortadaALaIzquierda()
    }
}
