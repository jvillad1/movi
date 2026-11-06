package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTAS_FRECUENTES
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import com.jvillada.movi.ui.quickadd.tagDeLaPastillaDeCuotas
import com.jvillada.movi.ui.transactions.CuotasDelMovimiento
import com.jvillada.movi.ui.transactions.TAG_CUOTAS_EN_EL_DETALLE
import com.jvillada.movi.ui.transactions.TAG_FILA_DE_CUOTAS_EN_EL_DETALLE
import com.jvillada.movi.ui.transactions.ofreceLasCuotas
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 133 · las cuotas de una compra con tarjeta, al confirmar el aviso y en el detalle
 *
 * La tarjeta de Por revisar de una compra con tarjeta lleva el renglón «¿A cuántas cuotas? · Elegir»,
 * que abre la fila de Agregar; elegir no bloquea «Confirmar». El detalle de una compra con tarjeta dice
 * «A 12 cuotas · Cambiar» y guarda con `updateEventCuotas`. Todo a 360 dp con la letra grande (1,456).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class LasCuotasDeLaCompraConTarjetaTest {

    @get:Rule val composeRule = createComposeRule()

    private val sms = SmsMessage(
        id = "s1", time = "2026-11-04 11:02", bank = "85540",
        text = "Bancolombia: Compraste \$1.200.000,00 en ALKOSTO con tu T.Cred *3684 el 04/11/2026 a las 11:02.",
        state = SMS_STATE_PENDING, det = "",
    )

    private fun conLetraGrande(contenido: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * ESCALA_DEL_TELEFONO_AL_130)) {
                MoviTheme {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla)) { contenido() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun nodos(tag: String): List<SemanticsNode> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()

    private fun tocar(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text }

    private fun montarLaTarjeta(conCuotas: Boolean, alElegir: (Int) -> Unit = {}, alConfirmar: () -> Unit = {}) = conLetraGrande {
        var elegidas by remember { mutableStateOf<Int?>(null) }
        TarjetaDeMensajeDelBanco(
            sms,
            onRevisar = {},
            onIgnorar = {},
            entendido = "Hogar · Master 3684 · \$1.200.000",
            onConfirmar = alConfirmar,
            leido = ParsedSms(1_200_000.0, "Alkosto", TransactionType.EXPENSE, "Hogar"),
            conPildoraDePendiente = false,
            loEntendido = "Hogar · Master 3684",
            cuotasDelAviso = if (!conCuotas) null else CuotasDelAviso(
                elegidas = elegidas,
                monto = 1_200_000L,
                moneda = "COP",
                onElegir = { elegidas = it; alElegir(it) },
            ),
        )
    }

    @Test
    fun `la tarjeta de una compra con tarjeta pregunta las cuotas y elegirlas no bloquea Confirmar`() {
        var elegidas: Int? = null
        var confirmado = false
        montarLaTarjeta(conCuotas = true, alElegir = { elegidas = it }, alConfirmar = { confirmado = true })
        val renglon = nodos(TAG_CUOTAS_EN_LA_TARJETA).single()
        assertTrue(composeRule.onAllNodesWithText("¿A cuántas cuotas?", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(nodos(TAG_FILA_DE_CUOTAS_EN_LA_TARJETA).isEmpty(), "la fila no se abre sola: no salta nada")
        val elegir = nodos(TAG_ELEGIR_CUOTAS_EN_LA_TARJETA).single()
        assertTrue(elegir.boundsInRoot.right <= renglon.boundsInRoot.right + 0.5f, "«Elegir» cabe en el renglón a 360 dp")

        tocar(TAG_ELEGIR_CUOTAS_EN_LA_TARJETA)
        // Las ocho de una (48 y 60 incluidas) y «Otra», en el renglón que se desliza.
        CUOTAS_FRECUENTES.forEach { n ->
            assertEquals(1, nodos(tagDeLaPastillaDeCuotas(n, TAG_FILA_DE_CUOTAS_EN_LA_TARJETA)).size, "la pastilla $n")
        }
        tocar(tagDeLaPastillaDeCuotas(60, TAG_FILA_DE_CUOTAS_EN_LA_TARJETA))
        assertEquals(60, elegidas)
        assertTrue(nodos(TAG_FILA_DE_CUOTAS_EN_LA_TARJETA).isEmpty(), "elegida, la fila se cierra")
        assertTrue(composeRule.onAllNodesWithText("A 60 cuotas", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        tocar(TAG_CONFIRMAR_EN_LA_FILA)
        assertTrue(confirmado)
    }

    @Test
    fun `sin elegir se confirma igual, y sin la pregunta no hay renglon`() {
        var confirmado = false
        montarLaTarjeta(conCuotas = false, alConfirmar = { confirmado = true })
        assertTrue(nodos(TAG_CUOTAS_EN_LA_TARJETA).isEmpty())
        tocar(TAG_CONFIRMAR_EN_LA_FILA)
        assertTrue(confirmado)
    }

    @Test
    fun `la pregunta solo va en un gasto de una tarjeta de credito`() {
        val compra = ParsedSms(1_200_000.0, "Alkosto", TransactionType.EXPENSE, "Hogar")
        assertTrue(com.jvillada.movi.ui.porrevisar.preguntaLasCuotasDelAviso(AccountType.CREDIT_CARD, compra))
        assertFalse(com.jvillada.movi.ui.porrevisar.preguntaLasCuotasDelAviso(AccountType.SAVINGS, compra))
        assertFalse(com.jvillada.movi.ui.porrevisar.preguntaLasCuotasDelAviso(AccountType.CREDIT_CARD, compra.copy(type = TransactionType.INCOME)))
    }

    // ── El detalle ─────────────────────────────────────────────────────────────────────────────

    private val tarjeta = Account(id = "master", name = "Master 3684", type = AccountType.CREDIT_CARD, balance = 0L)
    private val ahorros = Account(id = "ahorros", name = "Ahorros 8133", type = AccountType.SAVINGS, balance = 0L)
    private val nevera = FinancialEvent(
        id = "ev-nevera", accountId = "master", type = TransactionType.EXPENSE, amount = 6_000_000L,
        category = "Hogar", description = "Alkosto", timestamp = 1_793_000_000_000L, cuotas = 12,
    )

    @Test
    fun `el detalle de una compra con tarjeta dice sus cuotas y las cambia`() {
        val pedidos = mutableListOf<Pair<String, Int>>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun updateEventCuotas(id: String, cuotas: Int): FinancialEvent {
                pedidos += id to cuotas
                return nevera.copy(cuotas = cuotas)
            }
        }
        var cambiado: FinancialEvent? = null
        conLetraGrande { CuotasDelMovimiento(evento = nevera, onCambiado = { cambiado = it }) }
        assertTrue(composeRule.onAllNodesWithText("A 12 cuotas", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Cambiar", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(1, nodos(TAG_CUOTAS_EN_EL_DETALLE).size)

        tocar(TAG_ELEGIR_CUOTAS_EN_LA_TARJETA)
        tocar(tagDeLaPastillaDeCuotas(48, TAG_FILA_DE_CUOTAS_EN_EL_DETALLE))
        composeRule.waitForIdle()
        assertEquals(listOf("ev-nevera" to 48), pedidos)
        assertEquals(48, cambiado?.cuotas)
    }

    @Test
    fun `el detalle solo ofrece las cuotas en un gasto suelto de una tarjeta`() {
        val cuentas = listOf(tarjeta, ahorros)
        assertTrue(ofreceLasCuotas(nevera, cuentas))
        assertFalse(ofreceLasCuotas(nevera.copy(accountId = "ahorros"), cuentas))
        assertFalse(ofreceLasCuotas(nevera.copy(type = TransactionType.INCOME), cuentas), "un abono a la tarjeta no")
        assertFalse(ofreceLasCuotas(nevera.copy(transferId = "tr-1"), cuentas), "la pata de un pago no")
        assertNull(nevera.copy(cuotas = null).cuotas)
    }
}
