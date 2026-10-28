package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.PagoParaRepartir
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PartesDelAviso
import com.jvillada.movi.shared.model.PropuestaDeReparto
import com.jvillada.movi.shared.model.QueFueLaParte
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Un aviso que pagó varias cosas, en Reconciliar (vuelta 27)
 *
 * El caso del dueño con cifras de prueba: un giro de $4.273.333 a la cuenta del papá cubre la cuota
 * de la Libranza ($1.900.000) y la de la Hipoteca ($2.350.000), y sobran $23.333. Reconciliar lo
 * propone arriba; «Repartir así» abre «Repartir en partes» ya lleno, y nada se manda hasta tocar
 * «Confirmar las 3 partes». Sin propuesta, «Esto pagó varias cosas» abre el editor vacío: la línea
 * dice «Faltan $X» y el botón se prende solo cuando cuadra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class RepartirEnReconciliarTest {

    @get:Rule val composeRule = createComposeRule()

    private val texto = "Bancolombia: Transferiste \$4.273.333 desde tu cuenta *8133 a la cuenta *0000111122 el 27/10/2026 a las 10:00."
    private val aviso = SmsMessage(id = "sms_papa", time = "2026-10-27 10:00", bank = "85540", text = texto, state = SMS_STATE_PENDING, det = "")
    private val ahorros = Account("acc-ahorros", "Ahorros", AccountType.SAVINGS, 20_000_000L, "COP")
    private val libranza = Account("acc_libranza", "Libranza Papá", AccountType.LOAN, 90_000_000L, "COP")
    private val hipoteca = Account("acc_hipoteca", "Hipoteca Papá", AccountType.LOAN, 190_000_000L, "COP")

    private fun pago(cuenta: Account, nombre: String, monto: Long) = PagoParaRepartir(
        ruleId = "$CREDIT_RULE_PREFIX${cuenta.id}", nombre = nombre, monto = monto, categoria = CUOTA_CATEGORY,
        periodo = "2026-10", vence = "2026-10-27", cuentaDeLaDeudaId = cuenta.id, asociado = true,
    )
    private val cuotaLibranza = pago(libranza, "Cuota Libranza Papá", 1_900_000L)
    private val cuotaHipoteca = pago(hipoteca, "Cuota Hipoteca Papá", 2_350_000L)

    private var propuesta = PropuestaDeReparto(
        avisoId = "sms_papa", monto = 4_273_333L, destinoId = "dst_papa", destinoNombre = "Papá",
        pagos = listOf(cuotaLibranza, cuotaHipoteca),
    )
    private val enviadas = mutableListOf<Pair<String, PartesDelAviso>>()
    private val porGrupo = mutableListOf<ConfirmarElMismoPago>()

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSms(id: String): SmsMessage = aviso
        override suspend fun parseSms(id: String): ParsedSms =
            ParsedSms(4_273_333.0, "Transferencia a Papá", TransactionType.EXPENSE, "Familia")
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(ahorros, libranza, hipoteca)
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun getRepartoDelAviso(id: String): PropuestaDeReparto = propuesta
        override suspend fun confirmarEnPartes(smsId: String, partes: PartesDelAviso): MismoPagoConfirmado {
            enviadas += smsId to partes
            return MismoPagoConfirmado(eventoId = partes.partes.first().patas?.origenEventId, creado = true)
        }
        override suspend fun confirmarElMismoPago(grupoId: String, pedido: ConfirmarElMismoPago): MismoPagoConfirmado {
            porGrupo += pedido
            return MismoPagoConfirmado(creado = true)
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperar(condicion: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condicion() }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String, exacto: Boolean = false) {
        val nodo = composeRule.onNode(hasText(texto, substring = !exacto) and hasClickAction())
        // El botón de abajo no está dentro de la lista: no hay a dónde desplazarse.
        runCatching { nodo.performScrollTo() }
        nodo.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun botonDeConfirmar(texto: String) = composeRule.onNode(hasText(texto) and hasClickAction())

    private fun montar() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = "sms_papa") } }
        }
    }

    @Test
    fun `la propuesta del caso del dueno - dos cuotas y lo que sobra, y nada se manda solo`() {
        montar()
        val pregunta = "¿Pagaste las cuotas de Libranza Papá y Hipoteca Papá? Suman \$4.250.000 · sobran \$23.333"
        esperar { cuantos(pregunta) == 1 }

        tocar(REPARTIR_ASI)
        // El editor, lleno: las tres partes cuadran, pero no se mandó nada todavía.
        esperar { cuantos("Cuadra con el aviso") == 1 }
        assertTrue(enviadas.isEmpty())
        botonDeConfirmar(botonDeConfirmarLasPartes(3)).assertIsEnabled()

        botonDeConfirmar(botonDeConfirmarLasPartes(3)).performSemanticsAction(SemanticsActions.OnClick)
        esperar { enviadas.isNotEmpty() }
        val (smsId, cuerpo) = enviadas.single()
        assertEquals("sms_papa", smsId)
        val partes = cuerpo.partes
        assertEquals(listOf(QueFueLaParte.CUOTA, QueFueLaParte.CUOTA, QueFueLaParte.A_UN_TERCERO), partes.map { it.tipo })
        assertEquals(4_273_333L, partes.sumOf { it.monto })
        partes.take(2).forEach { parte ->
            val patas = assertNotNull(parte.patas)
            assertEquals(OperacionDelAviso.CUOTA, patas.operacion)
            assertEquals("acc-ahorros", patas.origenId)
            assertEquals(parte.ruleId.removePrefix(CREDIT_RULE_PREFIX), patas.destinoId)
        }
        val alPapa = assertNotNull(partes.last().evento)
        assertEquals(23_333L, alPapa.amount)
        assertEquals("Transferencia a Papá", alPapa.description)
        assertEquals(texto, alPapa.rawPayload)
    }

    @Test
    fun `sin propuesta, el modo partes dice Faltan y el boton se prende solo si cuadra`() {
        propuesta = propuesta.copy(pagos = emptyList())
        montar()
        esperar { cuantos(ESTO_PAGO_VARIAS_COSAS) == 1 }
        assertEquals(0, cuantos(REPARTIR_ASI))

        tocar(ESTO_PAGO_VARIAS_COSAS)
        esperar { cuantos(REPARTIR_EN_PARTES) >= 1 }
        // Una parte sin monto: falta todo el aviso.
        assertEquals(1, cuantos("Faltan \$4.273.333"))
        botonDeConfirmar(botonDeConfirmarLasPartes(1)).assertIsNotEnabled()

        // Una cuota a la Libranza por $4.000.000: faltan $273.333.
        tocar("Cuota", exacto = true)
        tocar("Libranza Papá", exacto = true)
        composeRule.onNodeWithTag(tagDelMontoDeLaParte(0)).performScrollTo().performTextInput("4000000")
        composeRule.waitForIdle()
        assertEquals(1, cuantos("Faltan \$273.333"))

        // «El resto a Papá»: una parte más por lo que falta, y ya cuadra.
        tocar(elRestoA(TerceroDelAviso("Transferencia a Papá", "Papá")))
        esperar { cuantos("Cuadra con el aviso") == 1 }
        botonDeConfirmar(botonDeConfirmarLasPartes(2)).assertIsEnabled()

        // Si se pasa, se apaga otra vez.
        composeRule.onNodeWithTag(tagDelMontoDeLaParte(0)).performScrollTo().performTextInput("0")
        composeRule.waitForIdle()
        assertEquals(1, cuantos("Te pasaste por"))
        botonDeConfirmar(botonDeConfirmarLasPartes(2)).assertIsNotEnabled()
        assertTrue(enviadas.isEmpty())
    }

    @Test
    @Config(qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 dp nada se recorta en la propuesta ni en el editor`() {
        montar()
        esperar { cuantos("¿Pagaste las cuotas") == 1 }
        nadaSeRecortaEn(TAG_REPARTO_EN_RECONCILIAR)
        tocar(REPARTIR_ASI)
        esperar { cuantos("Cuadra con el aviso") == 1 }
        nadaSeRecortaEn(TAG_EDITOR_DE_PARTES)
    }

    // ── Nada se recorta ──────────────────────────────────────────────────────────────────────

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(resultados)
        val d = resultados.singleOrNull() ?: return true
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    private fun nadaSeRecortaEn(tag: String) {
        val raiz = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        fun bajar(n: SemanticsNode): List<SemanticsNode> =
            (if (SemanticsActions.GetTextLayoutResult in n.config) listOf(n) else emptyList()) + n.children.flatMap(::bajar)
        val nodos = bajar(raiz)
        assertTrue(nodos.size > 2, "$tag pinta sus textos")
        val recortados = nodos.filterNot(::seVeEntero).map(::texto)
        assertTrue(recortados.isEmpty(), "$tag: se recortan $recortados")
    }
}
