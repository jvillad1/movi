package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CategoriaDelNumero
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Categorías del número en Revisar aviso, a 360 dp con la letra de la app (×1,12) y a 1,456. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class LasCategoriasDelNumeroEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val ahorros = Account("a4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 5_000_000)

    private val aviso = SmsMessage(
        id = "sms_llave",
        time = "2026-10-27 09:00",
        bank = "85540",
        text = "Bancolombia: Transferiste \$9,000.00 desde tu cuenta *4057 a la llave @pedro",
        state = SMS_STATE_PENDING,
        det = "",
    )

    private fun lectura(delNumero: List<CategoriaDelNumero>) = ParsedSms(
        amount = 9_000.0,
        merchant = "@pedro",
        type = TransactionType.EXPENSE,
        category = "Parqueadero",
        aprendidoDe = "Así lo anotaste 3 veces",
        categoriasDelNumero = delNumero,
        identificadorEsLlave = true,
    )

    private fun montar(delNumero: List<CategoriaDelNumero>, escala: Float = 1.12f, puerta: CompletableDeferred<Unit>? = null, alto: androidx.compose.ui.unit.Dp? = null) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros)
            override suspend fun getSms(id: String): SmsMessage = aviso
            override suspend fun parseSms(id: String): ParsedSms { puerta?.await(); return lectura(delNumero) }
            override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        }
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
                MoviTheme {
                    Box(if (alto != null) Modifier.fillMaxWidth().height(alto) else Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = aviso.id) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val dos = listOf(CategoriaDelNumero("Parqueadero", 3), CategoriaDelNumero("Gasolina", 2))

    private fun pastillasEnOrden(): List<String> {
        fun recorrer(n: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            n.children.flatMap { c ->
                val tag: String? = c.config.getOrNull(SemanticsProperties.TestTag)
                (if (tag != null && tag.startsWith("sms:pastilla:")) listOf<String>(tag) else emptyList<String>()) + recorrer(c)
            }
        val raiz = composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(TAG_PASTILLAS_DE_CATEGORIA_DEL_SMS), useUnmergedTree = true).fetchSemanticsNodes()
        return raiz.firstOrNull()?.let(::recorrer).orEmpty()
    }

    @Test
    fun `las pastillas arrancan abiertas con las del numero primero`() {
        montar(dos)
        val pastillas = pastillasEnOrden().filterNot { it == TAG_PASTILLA_NUEVA_CATEGORIA || it == TAG_PASTILLA_TODAS_LAS_CATEGORIAS_DEL_SMS }
        assertEquals(
            listOf(tagDePastillaDeCategoriaDelSms("Parqueadero"), tagDePastillaDeCategoriaDelSms("Gasolina")),
            pastillas.take(2),
            "las del número son las dos primeras de toda la lista: $pastillas",
        )
        assertTrue(composeRule.onAllNodesWithText("Cerrar").fetchSemanticsNodes().isNotEmpty(), "la fila dice «Cerrar»")
    }

    @Test
    fun `la explicacion de la fila se queda`() {
        montar(dos)
        assertTrue(composeRule.onAllNodesWithText("Así lo anotaste 3 veces").fetchSemanticsNodes().isNotEmpty())
    }

    private fun linea(): TextLayoutResult {
        val r = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(TAG_LINEA_DEL_NUMERO, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single()
    }

    private fun laLineaSeLeeEntera(escala: Float) {
        montar(dos, escala)
        val l = linea()
        assertEquals("A esta llave le pusiste Parqueadero 3 veces y Gasolina 2", l.layoutInput.text.text)
        assertFalse(l.isLineEllipsized(l.lineCount - 1), "sin cortar a escala $escala")
        assertTrue(l.lineCount <= 3)
    }

    @Test
    fun `la linea se lee entera a 360 dp`() = laLineaSeLeeEntera(1.12f)

    @Test
    fun `la linea se lee entera a 1,456`() = laLineaSeLeeEntera(1.456f)

    @Test
    fun `con una sola categoria todo queda como antes`() {
        montar(listOf(CategoriaDelNumero("Parqueadero", 3)))
        assertTrue(composeRule.onAllNodesWithText("Cerrar").fetchSemanticsNodes().isEmpty(), "las pastillas siguen cerradas")
        assertTrue(composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(TAG_LINEA_DEL_NUMERO), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(pastillasEnOrden().isEmpty())
    }

    @Test
    fun `nada salta al llegar la lectura`() {
        val puerta = CompletableDeferred<Unit>()
        // 540 dp: las pastillas quedan en la lista pero bajo el pliegue (más bajo, ni se componen y la prueba no vería el desplazamiento).
        montar(dos, puerta = puerta, alto = 540.dp)
        val cabeza = { composeRule.onAllNodesWithText("Bancolombia", substring = true).fetchSemanticsNodes().first().boundsInRoot.top }
        val lista = { composeRule.onNodeWithTag(TAG_LISTA_DE_REVISAR_AVISO).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() }
        val antes = cabeza()
        assertEquals(0f, lista(), "la lista arranca arriba")
        puerta.complete(Unit)
        composeRule.waitForIdle()
        assertEquals(0f, lista(), "la lista no se desplazó sola al llegar la lectura")
        val d = composeRule.density.density
        assertTrue(kotlin.math.abs(cabeza() - antes) <= 8f * d, "lo de arriba no se mueve: $antes → ${cabeza()}")
    }
}
