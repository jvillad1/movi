package com.jvillada.movi.ui.importados

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CargoEnElExtracto
import com.jvillada.movi.shared.model.ClaseDeCargo
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.SumaYaAnotada
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.extractos.StatementReviewScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # «Cargos y abonos del banco» en la revisión de un extracto
 *
 * El bloque agrupa lo que el banco cobra o abona solo, con un botón para anotar los tildados de un
 * toque. Lo que se fija desde la pantalla de verdad: el bloque y su conteo; que lo ya anotado sumado
 * en la cuenta del extracto sale destildado y dice dónde está; que la suma distinta avisa; y que
 * «Anotar» manda solo los cargos tildados, contra la cuenta del extracto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp-xhdpi")
class CargosDelBancoEnLaRevisionTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)

    private val anotado = SumaYaAnotada(
        eventoId = "ev_4x1000", cuentaId = ahorros.id, nombre = "4x1000 del 27 al 30 de septiembre",
        monto = 17_819, exacta = true,
    )

    private fun fila(id: String, rotulo: String, monto: Long, fecha: String, categoria: String, tipo: TransactionType = TransactionType.EXPENSE) =
        ParsedTransaction(id, fecha, rotulo, monto, "COP", tipo, categoria, rotulo, rotulo)

    private val filas = listOf(
        fila("f27", "IMPTO GOBIERNO 4X1000", 4_000, "2026-09-27", "Impuestos"),
        fila("f28", "IMPTO GOBIERNO 4X1000", 5_000, "2026-09-28", "Impuestos"),
        fila("i1", "IVA CUOTA MANEJO", 4_112, "2026-09-16", "Comisiones del banco"),
        fila("a1", "ABONO INTERESES AHORROS", 13, "2026-10-02", "Ingreso", TransactionType.INCOME),
        fila("c1", "CARULLA RINCON OVIED", 50_000, "2026-09-28", "Comida"),
    )

    private var decisiones = mutableListOf<ImportDecision>()

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(cargos: List<CargoEnElExtracto>) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            override suspend fun importStatement(decision: ImportDecision) {
                decisiones += decision
            }
        }
        val result = StatementParseResult(
            statementId = "st_1",
            bankName = "Bancolombia",
            period = "Septiembre 2026",
            newTransactions = filas,
            matches = emptyList(),
            cargosDelBanco = cargos,
        )
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { StatementReviewScreen(onNavigate = {}, result = result) } }
        }
        composeRule.waitForIdle()
    }

    private val cargosConElAnotado = listOf(
        CargoEnElExtracto("f27", ClaseDeCargo.IMPUESTO, listOf(anotado)),
        CargoEnElExtracto("f28", ClaseDeCargo.IMPUESTO, listOf(anotado)),
        CargoEnElExtracto("i1", ClaseDeCargo.COMISION),
        CargoEnElExtracto("a1", ClaseDeCargo.RENDIMIENTO),
    )

    @Test
    fun el_bloque_agrupa_los_cargos_y_lo_ya_anotado_sale_destildado() {
        montar(cargosConElAnotado)

        composeRule.onNodeWithText("Cargos y abonos del banco (4)").assertIsDisplayed()
        // Las dos líneas del 4x1000 dicen dónde ya están.
        assertEquals(
            2,
            composeRule.onAllNodesWithTextContaining("Ya anotado en «4x1000 del 27 al 30 de septiembre»").fetchSemanticsNodes().size,
        )
        // Dos ya anotados destildados: se anotan el IVA y los intereses.
        composeRule.onNodeWithText("Anotar los 2").assertIsDisplayed()
        // La compra sigue en su sección; el botón de siempre cuenta la compra y los dos cargos nuevos.
        composeRule.onNodeWithText("NUEVAS TRANSACCIONES").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Importar 3 seleccionadas").assertIsDisplayed()
    }

    @Test
    fun anotar_manda_solo_los_cargos_tildados_contra_la_cuenta_del_extracto() {
        montar(cargosConElAnotado)

        tocar("Anotar los 2")

        val decision = decisiones.single()
        assertEquals(ahorros.id, decision.accountId)
        assertEquals(setOf("i1", "a1"), decision.imports.map { it.id }.toSet())
        assertEquals("Ingreso", decision.imports.single { it.id == "a1" }.category)
        composeRule.onNodeWithText("Anotaste 2 cargos y abonos del banco.").assertIsDisplayed()
        // Lo que queda es la compra: el botón de siempre ya no cuenta los cargos anotados.
        composeRule.onNodeWithText("Importar 1 seleccionada").assertIsDisplayed()
    }

    @Test
    fun el_dueno_puede_destildar_un_cargo_antes_de_anotar() {
        montar(cargosConElAnotado)

        tocar("IVA CUOTA MANEJO")
        composeRule.onNodeWithText("Anotar 1").assertIsDisplayed()
        tocar("Anotar 1")

        assertEquals(listOf("a1"), decisiones.single().imports.map { it.id })
    }

    @Test
    fun la_suma_distinta_avisa_y_se_propone_tildada() {
        val inexacto = anotado.copy(exacta = false)
        montar(
            listOf(
                CargoEnElExtracto("f27", ClaseDeCargo.IMPUESTO, listOf(inexacto)),
                CargoEnElExtracto("f28", ClaseDeCargo.IMPUESTO, listOf(inexacto)),
            ),
        )

        composeRule.onNodeWithText("Cargos y abonos del banco (2)").assertIsDisplayed()
        composeRule.onAllNodesWithTextContaining("Puede que ya lo hayas anotado sumado").let { nodos ->
            assertEquals(2, nodos.fetchSemanticsNodes().size)
        }
        composeRule.onNodeWithText("Anotar los 2").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextContaining(texto: String) =
        onAllNodes(androidx.compose.ui.test.hasText(texto, substring = true))

    /** `performSemanticsAction`: bajo Robolectric el click no llega (ver `LoQueLlegaDelBancoEligeLaCuentaTest`). */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
