package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
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

/**
 * # Revisar aviso a 360 dp (vuelta 57)
 *
 * La revisión de la vuelta 56, con el aviso real de Occidente:
 *
 * - El comercio se leía «anco de Occidente S A ATH»: el campo de una línea, sin foco, quedaba desplazado
 *   hasta el final. Ahora, fuera de edición, es un texto que empieza por «Banco de…» (con «…» al final si
 *   no cabe en dos renglones); tocar la fila abre el campo.
 * - «Es la cuota de un crédito ›» y «Es el pago de una tarjeta ›» iban en letra más chica que «Esto pagó
 *   varias cosas»: ahora los tres son el mismo enlace.
 * - El último enlace quedaba a medias contra la barra de Ignorar / Confirmar: la lista termina con un
 *   relleno del alto de la barra.
 *
 * Con el motor de texto real, 360 × 640 dp y la letra de la app (×1,12).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class RevisarAvisoA360Test {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val ahorros = Account("a4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 5_000_000)
    private val vehiculo = Account("v4083", "Vehículo 4083", AccountType.LOAN, 40_000_000)
    private val nu = Account("nu", "Nu", AccountType.CREDIT_CARD, -300_000)

    private val aviso = SmsMessage(
        id = "sms_occidente",
        time = "2026-10-27 09:00",
        bank = "85784",
        text = "Bancolombia: Pagaste \$980,000.00 a Banco de Occidente S A ATH desde tu producto 4057",
        state = SMS_STATE_PENDING,
        det = "",
    )

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros, vehiculo, nu)
        override suspend fun getSms(id: String): SmsMessage = aviso
        override suspend fun parseSms(id: String): ParsedSms =
            ParsedSms(980_000.0, "Banco de Occidente S A ATH", TransactionType.EXPENSE, "Otros")
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme {
                    Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = aviso.id) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun disposicion(tag: String? = null, texto: String? = null): TextLayoutResult {
        val nodo = when {
            tag != null -> composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            else -> composeRule.onAllNodesWithText(texto!!, useUnmergedTree = true).fetchSemanticsNodes().first()
        }
        val r = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single()
    }

    @Test
    fun `el comercio se lee desde el principio y tocar la fila abre el campo`() {
        montar()

        val comercio = disposicion(tag = TAG_COMERCIO_A_LA_VISTA)
        val visto = comercio.layoutInput.text.text
        assertTrue(visto.startsWith("Banco de"), "empieza por «Banco de…»: «$visto»")
        assertEquals(0, comercio.getLineStart(0), "el primer renglón arranca en la primera letra")
        assertTrue(comercio.getBoundingBox(0).left >= 0f, "la «B» no queda afuera por la izquierda")
        assertFalse(composeRule.onAllNodesWithText("Banco de Occidente", substring = true).fetchSemanticsNodes().isEmpty())
        // Sin foco no hay un campo que se desplace.
        assertTrue(
            composeRule.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_CONFIRMA_O_AJUSTA_COMERCIO)), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty(),
        )

        composeRule.onNodeWithTag(TAG_CONFIRMA_O_AJUSTA_COMERCIO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(
            1,
            composeRule.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_CONFIRMA_O_AJUSTA_COMERCIO)), useUnmergedTree = true)
                .fetchSemanticsNodes().size,
            "tocar la fila abre el campo",
        )
    }

    @Test
    fun `los tres enlaces son parejos y la lista termina por encima de la barra`() {
        montar()

        val tamanos = listOf(ESTO_PAGO_VARIAS_COSAS, DeudaQuePagaElAviso.CREDITO.accion, DeudaQuePagaElAviso.TARJETA.accion)
            .map { disposicion(texto = it).layoutInput.style.fontSize }
        assertEquals(1, tamanos.distinct().size, "los tres enlaces en la misma letra: $tamanos")
        listOf(DeudaQuePagaElAviso.CREDITO.accion, DeudaQuePagaElAviso.TARJETA.accion).forEach { accion ->
            val l = disposicion(texto = accion)
            assertFalse(l.isLineEllipsized(0) || l.multiParagraph.didExceedMaxLines, "«$accion» entero")
        }

        // Hasta el final de la lista: lo último queda por encima de la barra, con su alto de aire.
        repeat(6) {
            composeRule.onNodeWithTag(TAG_LISTA_DE_REVISAR_AVISO).performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        val lista = composeRule.onNodeWithTag(TAG_LISTA_DE_REVISAR_AVISO).fetchSemanticsNode().boundsInRoot
        val tarjeta = composeRule.onNodeWithTag(DeudaQuePagaElAviso.TARJETA.tag).fetchSemanticsNode().boundsInRoot
        val ignorar = composeRule.onNodeWithText("Ignorar").fetchSemanticsNode().boundsInRoot
        assertTrue(tarjeta.bottom <= lista.bottom, "«Es el pago de una tarjeta ›» se ve entero")
        assertTrue(
            lista.bottom - tarjeta.bottom >= ignorar.height,
            "debajo del último enlace queda al menos el alto de la barra: ${lista.bottom - tarjeta.bottom} < ${ignorar.height}",
        )
        assertTrue(ignorar.top >= lista.bottom - 1f, "la barra no tapa la lista: va debajo")
    }
}
