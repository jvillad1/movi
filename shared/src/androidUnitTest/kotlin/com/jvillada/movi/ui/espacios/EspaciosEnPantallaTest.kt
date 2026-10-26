package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import com.jvillada.movi.ui.transactions.MovementSingleRow
import com.jvillada.movi.ui.transactions.TAG_TU_PARTE
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Espacios en pantalla (R3)
 *
 * La pantalla del espacio dice el saldo con «Marcar como saldado» y saldar llama al repositorio;
 * «Repartir» desde el detalle de un movimiento lo manda al espacio; y la fila de un movimiento
 * repartido dice «Tu parte» bajo el monto entero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1400dp-xhdpi")
class EspaciosEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private fun gasto(id: String, pagadoPor: String, monto: Long, eventoId: String? = null) = GastoDelEspacio(
        id = id, espacioId = casa.id, pagadoPor = pagadoPor, monto = monto, categoria = "Mercado",
        descripcion = if (pagadoPor == MIEMBRO_YO) "Mercado Éxito" else "Mercado del sábado",
        fecha = 1_761_000_000_000L, eventoId = eventoId, partes = partesDelGasto(monto, casa, pagadoPor),
    )

    private val gastos = listOf(gasto("g1", MIEMBRO_YO, 2_100_000, "e1"), gasto("g2", "dst_caro", 1_320_000))
    private val detalle = DetalleDelEspacio(
        espacio = casa,
        saldo = saldoDelEspacio(casa, gastos, emptyList()),
        delPeriodo = saldoDelEspacio(casa, gastos, emptyList()),
        gastos = gastos,
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Toca el botón que dice [texto]; con varios a la vista, el último (el de la hoja de encima). */
    private fun tocarBoton(texto: String) {
        composeRule.onAllNodes(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .fetchSemanticsNodes().let { check(it.isNotEmpty()) { "no hay un botón «$texto»" } }
        val nodos = composeRule.onAllNodes(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
        val cuantos = nodos.fetchSemanticsNodes().size
        nodos[cuantos - 1].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private open inner class ConLaCasa : RepositorioDePrueba() {
        val liquidaciones = mutableListOf<LiquidacionDelEspacio>()
        override suspend fun getEspacios(): List<Espacio> = listOf(casa)
        override suspend fun getDetalleDelEspacio(id: String): DetalleDelEspacio = detalle
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun liquidarEspacio(espacioId: String, liquidacion: LiquidacionDelEspacio): LiquidacionDelEspacio =
            liquidacion.also { liquidaciones += it }
    }

    @Test
    fun `la pantalla del espacio dice quien debe, cuanto y deja marcar como saldado`() {
        val repo = ConLaCasa()
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { EspaciosScreen(onNavigate = {}) } } }

        esperarTexto("Caro te debe")
        esperarTexto("para quedar 50/50")
        esperarTexto("Quién puso qué")
        esperarTexto("Mitad")
        esperarTexto("Mercado del sábado")
        esperarTexto("Marcar como saldado")

        tocarBoton("Marcar como saldado")
        esperarTexto("Caro te paga")
        tocarBoton("Marcar como saldado")
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.liquidaciones.isNotEmpty() }

        val l = repo.liquidaciones.single()
        assertEquals("dst_caro", l.de)
        assertEquals(MIEMBRO_YO, l.a)
        assertEquals(390_000L, l.monto)
        assertNull(l.eventoId, "sin una transferencia elegida, no se vincula ninguna")
    }

    private val bancolombia = Account("acc-banco", "Bancolombia", AccountType.SAVINGS, 1_000_000L, "COP")
    private val mercado = FinancialEvent(
        id = "e_mercado",
        accountId = bancolombia.id,
        type = TransactionType.EXPENSE,
        amount = 240_000L,
        category = "Mercado",
        description = "Mercado Éxito",
        timestamp = 1_761_000_000_000L,
    )

    @Test
    fun `Repartir desde el detalle de un movimiento lo manda al espacio`() {
        val repartidos = mutableListOf<Pair<String, String>>()
        Repositories.sustitutoDePrueba = object : ConLaCasa() {
            override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
            override suspend fun repartirMovimiento(espacioId: String, eventoId: String): GastoDelEspacio {
                repartidos += espacioId to eventoId
                return gasto("g_nuevo", MIEMBRO_YO, 240_000, eventoId)
            }
        }
        var cambiado = false
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(event = mercado, cuentas = listOf(bancolombia), onDismiss = {}, onCambiado = { cambiado = true })
                }
            }
        }
        esperarTexto("¿Es un gasto compartido?")
        tocarBoton("Repartir")
        composeRule.waitUntil(timeoutMillis = 5_000) { repartidos.isNotEmpty() }

        assertEquals(listOf("esp_casa" to "e_mercado"), repartidos)
        composeRule.waitUntil(timeoutMillis = 5_000) { cambiado }
    }

    @Test
    fun `sin espacios el detalle no ofrece repartir`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { HojaDelMovimiento(event = mercado, cuentas = listOf(bancolombia), onDismiss = {}, onCambiado = {}) } }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(TAG_REPARTIR, useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun `la fila de un movimiento repartido dice Tu parte bajo el monto entero`() {
        val repartido = mercado.copy(parteAjena = 120_000L, espacioId = casa.id)
        composeRule.setContent {
            MoviTheme { MovementSingleRow(tx = repartido, accountNames = mapOf(bancolombia.id to "Bancolombia"), onClick = {}) }
        }
        composeRule.onNodeWithTag(TAG_TU_PARTE, useUnmergedTree = true).assertIsDisplayed()
        esperarTexto("Tu parte \$120.000")
        esperarTexto("240.000")
    }

    @Test
    fun `una fila sin repartir no dice Tu parte`() {
        composeRule.setContent {
            MoviTheme { MovementSingleRow(tx = mercado, accountNames = emptyMap(), onClick = {}) }
        }
        composeRule.onAllNodesWithTag(TAG_TU_PARTE, useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }
}
