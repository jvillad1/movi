package com.jvillada.movi.ui.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecategorizarEnLoteResponse
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.tagDeCeldaDeCategoria
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # Arreglar uno pregunta por los parecidos, y solo cambia lo que el dueño dijo.
 *
 * El recorrido entero, en una pantalla de teléfono: elegir una categoría, ver la pregunta con los
 * movimientos que se van a mover, y que lo que llega al repositorio sea exactamente esa lista.
 *
 * Lo que más importa acá es la mitad negativa: **cerrar por «No, solo este» no manda ningún lote**.
 * Son movimientos que el dueño no está mirando, y moverlos sin que los haya confirmado sería
 * cambiarle cifras a sus espaldas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class OfrecerLosParecidosTest {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")

    private val gasto = FinancialEvent(
        id = "ev-qr",
        accountId = banco.id,
        type = TransactionType.EXPENSE,
        amount = 25_000L,
        category = "Otros",
        description = "Mora Soccer",
        merchant = "Pago QR · llave 0092184713",
        timestamp = 1_757_952_000_000L,
    )

    private fun parecido(id: String, categoria: String = "Otros") =
        gasto.copy(id = id, category = categoria, timestamp = gasto.timestamp - 86_400_000L)

    private fun ingreso(id: String) = parecido(id).copy(type = TransactionType.INCOME, description = "Transferencia recibida")

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    /** El repositorio de prueba anota qué lote se pidió, o `null` si no se pidió ninguno. */
    open class RepoConParecidos(private val parecidos: List<FinancialEvent>) : RepositorioDePrueba() {
        var lotePedido: Pair<List<String>, String>? = null
        val categoriasPedidas = mutableListOf<String>()
        override suspend fun getParecidos(id: String): List<FinancialEvent> = parecidos
        override suspend fun updateEventCategory(id: String, category: String): FinancialEvent =
            categoriasPedidas.add(category).let { _ -> FinancialEvent(
                id = id, accountId = "acc-banco", type = TransactionType.EXPENSE, amount = 25_000L,
                category = category, description = "Mora Soccer", timestamp = 1_757_952_000_000L,
            ) }
        override suspend fun recategorizarEnLote(ids: List<String>, category: String): RecategorizarEnLoteResponse {
            lotePedido = ids to category
            return RecategorizarEnLoteResponse(cambiados = ids, omitidos = 0)
        }
    }

    private fun montar(onCambiado: (FinancialEvent) -> Unit = {}) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    ChangeCategorySheet(
                        event = gasto,
                        cuentas = listOf(banco),
                        onDismiss = {},
                        onEventChanged = onCambiado,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // Ola 2b: el selector se abre desde el chip de la categoría, como lo abre el dueño.
        composeRule.onNodeWithTag(TAG_CHIP_DE_CATEGORIA, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun elegir(categoria: String) {
        // Ola L: la categoría se elige en la cuadrícula del selector; su celda tiene un tag propio
        // (con el texto, «Comida» ahora también sale como la categoría actual de otras hojas).
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria(categoria)).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(texto)), useUnmergedTree = true)
            .onLast().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun alElegirCategoriaLaHojaPreguntaPorLosParecidos() {
        Repositories.sustitutoDePrueba = RepoConParecidos(listOf(parecido("ev-2"), parecido("ev-3")))
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")

        composeRule.onNodeWithText("Hay 2 movimientos más de «Mora Soccer»", useUnmergedTree = true)
            .assertIsDisplayed()
        assertNull(cambiado, "la hoja no puede cerrarse antes de que él conteste")
    }

    @Test
    fun decirQueSiMandaElLoteConEsosMovimientos() {
        val repo = RepoConParecidos(listOf(parecido("ev-2"), parecido("ev-3")))
        Repositories.sustitutoDePrueba = repo
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")
        tocar("Poner 2 en «Comida»")

        assertEquals(listOf("ev-2", "ev-3") to "Comida", repo.lotePedido)
        assertNotNull(cambiado, "después del lote la hoja se cierra con el movimiento ya cambiado")
        assertEquals("Comida", cambiado?.category)
    }

    @Test
    fun decirQueNoNoMandaNingunLote() {
        val repo = RepoConParecidos(listOf(parecido("ev-2")))
        Repositories.sustitutoDePrueba = repo
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")
        tocar("No, solo este")

        assertNull(repo.lotePedido, "«solo este» no puede mover nada más")
        assertNotNull(cambiado, "y el movimiento que él abrió sí queda cambiado")
    }

    /** Sin parecidos no hay pregunta: la hoja hace lo de siempre y se cierra. */
    @Test
    fun sinParecidosLaHojaNoPreguntaNada() {
        Repositories.sustitutoDePrueba = RepoConParecidos(emptyList())
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")

        composeRule.onAllNodesWithText("¿Y los parecidos?", useUnmergedTree = true).assertCountEquals(0)
        assertNotNull(cambiado)
    }

    /** Un parecido que YA está en esa categoría no se ofrece: preguntar por nada gasta la pregunta. */
    @Test
    fun loQueYaEstaEnEsaCategoriaNoSeOfrece() {
        Repositories.sustitutoDePrueba = RepoConParecidos(listOf(parecido("ev-2", categoria = "Comida")))
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")

        composeRule.onAllNodesWithText("¿Y los parecidos?", useUnmergedTree = true).assertCountEquals(0)
        assertNotNull(cambiado)
    }

    /**
     * Revisión final de la Ola B: elegir en la cuadrícula ES elegir — un
     * toque, como en la lista de arriba. Antes el toque solo llenaba el campo y había que tocar
     * además «Usar "…"».
     */
    @Test
    fun tocarUnaCeldaDelCampoEligeEnUnSoloToque() {
        val repo = RepoConParecidos(emptyList())
        Repositories.sustitutoDePrueba = repo
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        // Ola L: la hoja ya no esconde el selector tras un campo plegado; desde la Ola 2b se abre
        // con el chip (ver `montar`), y una celda toca y guarda.
        composeRule.onNodeWithTag(tagDeCeldaDeCategoria("Comida"), useUnmergedTree = true).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals(listOf("Comida"), repo.categoriasPedidas)
        assertEquals("Comida", cambiado?.category)
        composeRule.onAllNodesWithText("Usar «", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    // ---- Vuelta 139 · elegir cuáles de los parecidos ----

    private fun casillas() = composeRule.onAllNodes(
        androidx.compose.ui.test.isToggleable(), useUnmergedTree = true,
    )

    @Test
    fun desmarcarUnaFilaMandaUnoMenos() {
        val repo = RepoConParecidos(listOf(parecido("ev-2"), parecido("ev-3"), parecido("ev-4")))
        Repositories.sustitutoDePrueba = repo
        montar()

        elegir("Comida")
        composeRule.onNodeWithText("Poner 3 en «Comida»", useUnmergedTree = true).assertIsDisplayed()
        casillas()[1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Poner 2 en «Comida»", useUnmergedTree = true).assertIsDisplayed()
        tocar("Poner 2 en «Comida»")

        assertEquals(listOf("ev-2", "ev-4") to "Comida", repo.lotePedido)
    }

    @Test
    fun ningunoDejaElBotonApagadoYPideMarcar() {
        val repo = RepoConParecidos(listOf(parecido("ev-2"), parecido("ev-3")))
        Repositories.sustitutoDePrueba = repo
        montar()

        elegir("Comida")
        tocar("Ninguno")

        composeRule.onNodeWithText("Marca los que quieras cambiar", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction() and hasAnyDescendant(hasText("Marca los que quieras cambiar")), useUnmergedTree = true)
            .onLast().assertIsNotEnabled()
        assertNull(repo.lotePedido)
    }

    @Test
    fun losDelOtroTipoNacenSinMarcar() {
        val repo = RepoConParecidos(listOf(parecido("ev-2"), ingreso("ev-3")))
        Repositories.sustitutoDePrueba = repo
        montar()

        elegir("Comida")
        composeRule.onNodeWithText("Salieron · 1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Entraron · 1", useUnmergedTree = true).assertIsDisplayed()
        tocar("Poner 1 en «Comida»")

        assertEquals(listOf("ev-2") to "Comida", repo.lotePedido)
    }

    @Test
    fun conCatorceFilasElBotonPrincipalSeVeSinDesplazar() {
        Repositories.sustitutoDePrueba = RepoConParecidos((1..14).map { parecido("ev-$it") })
        montar()

        elegir("Comida")

        composeRule.onNodeWithText("Poner 14 en «Comida»", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("No, solo este", useUnmergedTree = true).assertIsDisplayed()
    }

    /** El caso de la foto: 3 parecidos, 2 a una categoría y el que queda a otra, en dos llamadas. */
    @Test
    fun seReparteEnDosLotesACategoriasDistintas() {
        val llamadas = mutableListOf<Pair<List<String>, String>>()
        val repo = object : RepoConParecidos(listOf(parecido("ev-2"), parecido("ev-3"), parecido("ev-4"))) {
            override suspend fun recategorizarEnLote(ids: List<String>, category: String): RecategorizarEnLoteResponse {
                llamadas += ids to category
                return RecategorizarEnLoteResponse(cambiados = ids, omitidos = 0)
            }
        }
        Repositories.sustitutoDePrueba = repo
        var cambiado: FinancialEvent? = null
        montar { cambiado = it }

        elegir("Comida")
        // Se desmarca el tercero y los dos primeros van a «Comida».
        casillas()[2].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar("Poner 2 en «Comida»")

        composeRule.onNodeWithText("Pasaste 2 a «Comida»", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Quedan 1 de «Mora Soccer»", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Dejar el resto así", useUnmergedTree = true).assertIsDisplayed()
        assertNull(cambiado, "queda uno: la hoja sigue abierta")

        tocar("Ponerlos en: Comida")
        elegir("Transporte")
        tocar("Poner 1 en «Transporte»")

        assertEquals(listOf(listOf("ev-2", "ev-3") to "Comida", listOf("ev-4") to "Transporte"), llamadas)
        assertNotNull(cambiado, "sin nada por pasar la hoja se cierra sola")
    }
}
