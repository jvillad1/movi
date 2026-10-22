package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.accounts.AccountDetailScreen
import com.jvillada.movi.ui.categorias.TAG_ICONO_DE_CATEGORIA
import com.jvillada.movi.ui.components.TAG_AVATAR_DE_MARCA
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * # Ola 2a: el comercio protagonista y los gastos en neutro, en pantalla
 *
 * El catálogo se prueba en `:core` (`MarcasConocidasTest`); acá, que la fila de Movimientos —la
 * misma que usan Por revisar y el detalle de una cuenta— lleva la marca con su nombre para el
 * lector de pantalla, que sin marca queda el ícono de la categoría de siempre, que un gasto se
 * pinta en el color del texto y no en el rojo, y que el detalle de una cuenta abre con su saldo y
 * sin el «MANUAL» crudo que mostraba cada fila.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ElComercioEnLaFilaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahora = Clock.System.now().toEpochMilliseconds()

    private val rappi = FinancialEvent(
        id = "e-rappi",
        accountId = "acc-nu",
        type = TransactionType.EXPENSE,
        amount = 27_500L,
        category = "Comida",
        description = "Rappi · Las Doce",
        merchant = "RAPPI*LAS DOCE",
        timestamp = ahora,
        source = EventSource.SMS,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        countsAsCashFlow = true,
    )

    private val almuerzo = rappi.copy(
        id = "e-almuerzo",
        description = "Almuerzo con la hija",
        merchant = null,
        amount = 18_000L,
        source = EventSource.MANUAL,
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun fila(evento: FinancialEvent, alMontar: @Composable () -> Unit = {}) {
        composeRule.setContent {
            MoviTheme {
                alMontar()
                Box(Modifier.fillMaxSize()) {
                    MovementSingleRow(tx = evento, accountNames = mapOf("acc-nu" to "Nu"), onClick = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun cuantosConTag(tag: String): Int =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun colorDelTexto(texto: String): Color {
        val nodo = composeRule.onAllNodesWithText(texto, useUnmergedTree = true).onFirst().fetchSemanticsNode()
        val medidas = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(medidas)
        return medidas.single().layoutInput.style.color
    }

    @Test
    fun `una fila con marca lleva la marca con su nombre y no el icono de la categoria`() {
        fila(rappi)

        val marcas = composeRule.onAllNodesWithContentDescription("Rappi", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, marcas.size, "la marca dice su nombre al lector de pantalla")
        assertEquals(1, cuantosConTag(TAG_AVATAR_DE_MARCA))
        assertEquals(0, cuantosConTag(TAG_ICONO_DE_CATEGORIA), "con marca, la categoría no compite")
    }

    @Test
    fun `una fila sin marca queda con el icono de la categoria de siempre`() {
        fila(almuerzo)

        assertEquals(1, cuantosConTag(TAG_ICONO_DE_CATEGORIA))
        assertEquals(0, cuantosConTag(TAG_AVATAR_DE_MARCA))
    }

    @Test
    fun `un gasto se pinta en el color del texto y no en el rojo`() {
        var texto = Color.Unspecified
        var sale = Color.Unspecified
        fila(rappi) {
            texto = Movi.colores.texto
            sale = Movi.colores.sale
        }

        val color = colorDelTexto("−\$27.500")
        assertEquals(texto, color)
        assertNotEquals(sale, color)
    }

    @Test
    fun `el detalle de la cuenta abre con su saldo y sin MANUAL`() {
        val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 1_250_000L)
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccount(id: String): Account = nu
            override suspend fun getEvents(accountId: String?): List<FinancialEvent> = listOf(rappi, almuerzo)
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    AccountDetailScreen(onNavigate = {}, accountId = "acc-nu", group = AccountGroup.DINERO)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Almuerzo con la hija") }

        assertTrue(hay("\$1.250.000"), "el saldo arriba")
        assertTrue(hay("Cuadrar con el banco"))
        assertTrue(!hay("MANUAL"), "nada de la palabra técnica")
        assertTrue(!hay("SMS"), "ni ningún otro source crudo")
        // La cuenta con la marca de su banco, y la fila de Rappi con la suya: las mismas filas que Movimientos.
        assertTrue(composeRule.onAllNodesWithContentDescription("Nu", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Rappi", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
