package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AVANCE_DE_TARJETA_CATEGORY
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CuotaYaPagada
import com.jvillada.movi.shared.model.fraseDeLaCuotaYaPagada
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.ui.transactions.TAG_AYUDA_DEL_SELECTOR_DE_DEUDA
import com.jvillada.movi.ui.transactions.UN_ABONO_EXTRA
import com.jvillada.movi.ui.transactions.textoDeCuentaDeDeuda
import kotlinx.serialization.json.Json
import kotlin.test.assertFalse
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Reconciliar un aviso de dos patas
 *
 * Arreglo 1 de la auditoría de la ingesta. La pantalla de verdad, con un repositorio de prueba: antes
 * de confirmar, la tarjeta de resumen dice «Sale de … · entra a …»; confirmar manda la intención
 * (`DosPatasDelAviso`) al server en vez de crear un movimiento suelto; y un avance sin la cuenta a la
 * que entró no se puede confirmar, porque anotado en la tarjeta le bajaría la deuda.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class ReconciliarConLasDosPatasTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 50_000_000)
    private val otra = Account("o", "Nequi", AccountType.SAVINGS, 100_000)
    private val masterBlack = Account("m", "Master Black 3684", AccountType.CREDIT_CARD, -2_000_000)
    private val amex = Account("x", "AMEX 9208", AccountType.CREDIT_CARD, 0)
    private val fiducuenta = Account("f", "Fiducuenta 9586", AccountType.INVESTMENT, 20_000_000)
    private val masterBlackUsd = Account("mu", "Master Black USD", AccountType.CREDIT_CARD, -500, currency = "USD")

    private val pagoDeTarjeta = SmsMessage(
        id = "sms_pago",
        time = "2026-10-03 09:15",
        bank = "85784",
        text = "Bancolombia: Pagaste \$386.902 en la tarjeta de credito *3684 desde la cuenta *8133",
        state = SMS_STATE_PENDING,
        det = "",
    )
    private val avance = SmsMessage(
        id = "correo_avance",
        time = "2026-10-03 17:50",
        bank = "Correo · Bancolombia",
        // Sin el número de la cuenta de destino: la tiene que elegir el dueño.
        text = "Bancolombia: Hiciste un avance de \$6,200,000 en tu SUC VIRTUAL desde tu T.Credito *9208 a la cuenta *5555.",
        state = SMS_STATE_PENDING,
        det = "",
    )

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private open inner class Repo(
        private val sms: SmsMessage,
        private val leido: ParsedSms,
        private val cuentas: List<Account> = listOf(ahorros, otra, masterBlack, amex, fiducuenta),
    ) : RepositorioDePrueba() {
        val conPatas = mutableListOf<Pair<String, DosPatasDelAviso>>()
        val sueltos = mutableListOf<FinancialEvent>()
        override suspend fun getAccounts(): List<Account> = cuentas
        override suspend fun getSms(id: String): SmsMessage = sms
        override suspend fun parseSms(id: String): ParsedSms = leido
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { sueltos += it }
        override suspend fun confirmSms(id: String) = Unit
        override suspend fun confirmarConLasDosPatas(smsId: String, patas: DosPatasDelAviso): MismoPagoConfirmado {
            conPatas += smsId to patas
            return MismoPagoConfirmado(eventoId = patas.origenEventId, creado = true, cerrados = listOf(smsId))
        }
    }

    @Test
    fun el_pago_de_la_tarjeta_dice_lo_que_va_a_crear_y_confirma_con_la_intencion() {
        val repo = Repo(pagoDeTarjeta, ParsedSms(386_902.0, "Pago de tarjeta", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY))
        montar(repo, pagoDeTarjeta.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertIsDisplayed()
            .assertTextEquals("Sale de Bancolombia Ahorros 8133 · entra a Master Black 3684 como pago")
        composeRule.onNodeWithTag(TAG_MONTO_EN_LA_MONEDA_DE_LA_DEUDA).assertDoesNotExist()

        tocar("Confirmar")

        assertEquals(0, repo.sueltos.size, "no se crea un gasto suelto")
        val (aviso, patas) = repo.conPatas.single()
        assertEquals(pagoDeTarjeta.id, aviso)
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, patas.operacion)
        assertEquals(ahorros.id, patas.origenId)
        assertEquals(masterBlack.id, patas.destinoId)
        assertEquals(386_902L, patas.monto)
        assertEquals(3, setOf(patas.transferId, patas.origenEventId, patas.destinoEventId).size)
        assertNull(patas.montoEnLaMonedaDeLaDeuda, "entre pesos no hay nada que convertir")
    }

    @Test
    fun pagar_en_pesos_la_tarjeta_en_dolares_pide_cuanto_bajo_la_deuda_y_lo_manda() {
        // La tarjeta en dólares la propone Movi (como el correo de PSE): el aviso no la nombra.
        val pago = pagoDeTarjeta.copy(id = "sms_pago_usd", text = "Bancolombia: Pagaste \$480.000 en la tarjeta de credito desde la cuenta *8133")
        val leido = ParsedSms(480_000.0, "Pago de tarjeta", TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, deudaSugeridaId = masterBlackUsd.id)
        val repo = Repo(pago, leido, cuentas = listOf(ahorros, masterBlackUsd))
        montar(repo, pago.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de Bancolombia Ahorros 8133 · entra a Master Black USD como pago")
        composeRule.onNodeWithText(rotuloDelMontoEnLaDeuda("USD")).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TAG_MONTO_EN_LA_MONEDA_DE_LA_DEUDA)), useUnmergedTree = true)
            .performTextInput("120")
        composeRule.waitForIdle()
        tocar("Confirmar")

        val (_, patas) = repo.conPatas.single()
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, patas.operacion)
        assertEquals(masterBlackUsd.id, patas.destinoId)
        assertEquals(480_000L, patas.monto, "lo que salió de la cuenta, en pesos")
        assertEquals(120L, patas.montoEnLaMonedaDeLaDeuda, "lo que bajó la deuda, en dólares")
    }

    @Test
    fun el_avance_sin_la_cuenta_de_destino_no_se_confirma_y_al_elegirla_manda_el_avance() {
        val repo = Repo(avance, ParsedSms(6_200_000.0, "Avance de la tarjeta *9208", TransactionType.INCOME, AVANCE_DE_TARJETA_CATEGORY))
        montar(repo, avance.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS).assertTextEquals(faltaLaCuentaDeDestino(esAvance = true))
        composeRule.onNodeWithText("Confirmar").assertIsNotEnabled()
        tocar("Confirmar")
        assertTrue(repo.conPatas.isEmpty() && repo.sueltos.isEmpty(), "un avance en la tarjeta le bajaría la deuda")

        composeRule.onNodeWithTag(TAG_CAMBIAR_EL_DESTINO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tagDelDestinoElegible(ahorros.id)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de AMEX 9208 como avance · entra a Bancolombia Ahorros 8133")
        composeRule.onNodeWithText("Confirmar").assertIsEnabled()
        tocar("Confirmar")

        val (_, patas) = repo.conPatas.single()
        assertEquals(OperacionDelAviso.AVANCE, patas.operacion)
        assertEquals(amex.id, patas.origenId)
        assertEquals(ahorros.id, patas.destinoId)
        assertEquals(6_200_000L, patas.monto)
        assertEquals(0, repo.sueltos.size)
    }

    @Test
    fun un_aviso_sin_destino_propio_sigue_siendo_un_movimiento_suelto() {
        val compra = pagoDeTarjeta.copy(id = "sms_compra", text = "Bancolombia: Compraste \$15.100 en TOSTAO con tu T.Deb *8133")
        val repo = Repo(compra, ParsedSms(15_100.0, "TOSTAO", TransactionType.EXPENSE, "Comida"))
        montar(repo, compra.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS).assertDoesNotExist()
        tocar("Confirmar")
        assertEquals(1, repo.sueltos.size)
        assertTrue(repo.conPatas.isEmpty())
    }

    @Test
    fun el_aviso_que_entra_desde_una_cuenta_propia_se_confirma_como_traspaso() {
        // Sintético, con la forma del brief: el aviso del lado que recibe nombra de dónde vino.
        val recibido = pagoDeTarjeta.copy(
            id = "sms_recibido",
            text = "Bancolombia: Recibiste \$500.000 de tu cuenta *9586 en tu cuenta *8133 el 04/10/2026 a las 10:15.",
        )
        val leido = ParsedSms(500_000.0, "Desde Fiducuenta 9586", TransactionType.INCOME, "Transferencia", traspasoDesdeId = fiducuenta.id)
        val repo = Repo(recibido, leido)
        montar(repo, recibido.id)

        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de Fiducuenta 9586 · entra a Bancolombia Ahorros 8133 como traspaso")
        tocar("Confirmar")

        assertEquals(0, repo.sueltos.size, "no se anota un ingreso suelto")
        val (_, patas) = repo.conPatas.single()
        assertEquals(OperacionDelAviso.TRASPASO, patas.operacion)
        assertEquals(fiducuenta.id, patas.origenId)
        assertEquals(ahorros.id, patas.destinoId)
        assertTrue(patas.avisoDelLadoQueEntra)
    }

    /**
     * Vuelta 56 · **la segunda cuota del mes también pregunta desde Revisar aviso.** La cuota va con
     * `esLaCuota`; si el server contesta 409 `CUOTA_YA_PAGADA`, la pantalla no da un error: en el lugar de
     * los botones pregunta «La cuota de octubre de Vehículo 4083 ya está pagada. ¿Este pago es un abono
     * extra?» (sin «adelantada»), «Cancelar» vuelve a los botones, y «Un abono extra» confirma con
     * `comoAbono`.
     */
    @Test
    fun la_segunda_cuota_del_mes_pregunta_y_un_abono_extra_confirma_como_abono() {
        val vehiculo = Account("v", "Vehículo 4083", AccountType.LOAN, 40_000_000)
        val cuota = pagoDeTarjeta.copy(id = "sms_cuota", text = "Bancolombia: Pagaste \$980.000 a Banco de Occidente desde tu producto 8133")
        val leido = ParsedSms(980_000.0, "Banco de Occidente", TransactionType.EXPENSE, CUOTA_CATEGORY, deudaSugeridaId = vehiculo.id)
        val ya = CuotaYaPagada(periodo = "2026-10", credito = vehiculo.name, mensaje = fraseDeLaCuotaYaPagada(vehiculo.name, "2026-10"))
        val repo = object : Repo(cuota, leido, cuentas = listOf(ahorros, vehiculo)) {
            override suspend fun confirmarConLasDosPatas(smsId: String, patas: DosPatasDelAviso): MismoPagoConfirmado {
                conPatas += smsId to patas
                if (!patas.comoAbono) throw ApiException(409, Json { encodeDefaults = true }.encodeToString(CuotaYaPagada.serializer(), ya))
                return MismoPagoConfirmado(eventoId = patas.origenEventId, creado = true, cerrados = listOf(smsId))
            }
        }
        montar(repo, cuota.id)
        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de Bancolombia Ahorros 8133 · entra a Vehículo 4083 como cuota")

        tocar("Confirmar")
        val (_, laCuota) = repo.conPatas.single()
        assertEquals(OperacionDelAviso.CUOTA, laCuota.operacion)
        assertTrue(laCuota.esLaCuota, "una cuota va con la marca, para que el server pregunte")
        assertFalse(laCuota.comoAbono)

        val pregunta = "La cuota de octubre de Vehículo 4083 ya está pagada. ¿Este pago es un abono extra?"
        composeRule.onNodeWithTag(TAG_CUOTA_YA_PAGADA_EN_EL_AVISO).assertIsDisplayed()
        composeRule.onNodeWithText(pregunta).assertIsDisplayed()
        composeRule.onAllNodesWithText("adelantada", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Confirmar").assertCountEquals(0)
        composeRule.onAllNodesWithText("HTTP", substring = true).assertCountEquals(0)

        // «Cancelar» vuelve a los botones, y nada más se mandó.
        tocar("Cancelar")
        composeRule.onNodeWithTag(TAG_CUOTA_YA_PAGADA_EN_EL_AVISO).assertDoesNotExist()
        composeRule.onNodeWithText("Confirmar").assertIsEnabled()
        assertEquals(1, repo.conPatas.size)

        // Otra vez, y ahora «Un abono extra»: el mismo crédito, como abono.
        tocar("Confirmar")
        tocar(UN_ABONO_EXTRA)
        assertEquals(3, repo.conPatas.size)
        val (aviso, abono) = repo.conPatas.last()
        assertEquals(cuota.id, aviso)
        assertEquals(OperacionDelAviso.CUOTA, abono.operacion)
        assertEquals(vehiculo.id, abono.destinoId)
        assertTrue(abono.comoAbono)
        assertFalse(abono.esLaCuota)
        assertEquals(980_000L, abono.monto)
        assertEquals(0, repo.sueltos.size, "nunca un gasto suelto")
    }

    // ── Vuelta 56 · «Es la cuota de un crédito ›» y «Es el pago de una tarjeta ›» ───────────────

    private val ahorros4057 = Account("a4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 5_000_000)
    private val vehiculo4083 = Account("v4083", "Vehículo 4083", AccountType.LOAN, 40_000_000)

    /** El aviso real de la revisión: Movi lo deja en «Otros» y no nombra el crédito. */
    private val avisoDeOccidente = pagoDeTarjeta.copy(
        id = "sms_occidente",
        text = "Bancolombia: Pagaste \$980,000.00 a Banco de Occidente S A ATH desde tu producto 4057",
    )
    private val leidoDeOccidente = ParsedSms(980_000.0, "Banco de Occidente S A ATH", TransactionType.EXPENSE, "Otros")

    private fun elegirElCredito() {
        composeRule.onNodeWithTag(DeudaQuePagaElAviso.CREDITO.tag).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¿De cuál crédito?").assertIsDisplayed()
        // Solo los créditos: la tarjeta no está en la lista.
        composeRule.onAllNodesWithText(textoDeCuentaDeDeuda(masterBlack)).assertCountEquals(0)
        tocar(textoDeCuentaDeDeuda(vehiculo4083))
    }

    /**
     * Revisión de la vuelta 56: con el aviso de Occidente en «Otros» no había cómo llegar a «Sale de … ·
     * entra a …» (la vuelta 51 sacó «Cuota de crédito» del selector). «Es la cuota de un crédito ›» →
     * el crédito → Confirmar manda las dos patas de una cuota, con la marca.
     */
    @Test
    fun el_aviso_de_occidente_en_otros_se_confirma_como_la_cuota_de_un_credito() {
        val repo = Repo(avisoDeOccidente, leidoDeOccidente, cuentas = listOf(ahorros4057, vehiculo4083, masterBlack))
        montar(repo, avisoDeOccidente.id)
        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS).assertDoesNotExist()
        composeRule.onNodeWithTag(DeudaQuePagaElAviso.TARJETA.tag).assertIsDisplayed()

        elegirElCredito()
        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de Bancolombia Ahorros 4057 · entra a Vehículo 4083 como cuota")
        // Ya en el modo de dos patas, las acciones se van.
        composeRule.onNodeWithTag(DeudaQuePagaElAviso.CREDITO.tag).assertDoesNotExist()

        tocar("Confirmar")
        assertEquals(0, repo.sueltos.size, "no se crea un gasto suelto")
        val (aviso, patas) = repo.conPatas.single()
        assertEquals(avisoDeOccidente.id, aviso)
        assertEquals(OperacionDelAviso.CUOTA, patas.operacion)
        assertEquals(ahorros4057.id, patas.origenId)
        assertEquals(vehiculo4083.id, patas.destinoId)
        assertEquals(980_000L, patas.monto)
        assertTrue(patas.esLaCuota)
    }

    /** …y con la cuota del mes ya pagada: el 409, la pregunta, y «Un abono extra» como abono. */
    @Test
    fun el_aviso_de_occidente_con_la_cuota_ya_pagada_pregunta_y_va_como_abono() {
        val ya = CuotaYaPagada(periodo = "2026-10", credito = vehiculo4083.name, mensaje = fraseDeLaCuotaYaPagada(vehiculo4083.name, "2026-10"))
        val repo = object : Repo(avisoDeOccidente, leidoDeOccidente, cuentas = listOf(ahorros4057, vehiculo4083)) {
            override suspend fun confirmarConLasDosPatas(smsId: String, patas: DosPatasDelAviso): MismoPagoConfirmado {
                conPatas += smsId to patas
                if (!patas.comoAbono) throw ApiException(409, Json { encodeDefaults = true }.encodeToString(CuotaYaPagada.serializer(), ya))
                return MismoPagoConfirmado(eventoId = patas.origenEventId, creado = true, cerrados = listOf(smsId))
            }
        }
        montar(repo, avisoDeOccidente.id)
        // Sin tarjetas, solo se ofrece la del crédito.
        composeRule.onNodeWithTag(DeudaQuePagaElAviso.TARJETA.tag).assertDoesNotExist()
        elegirElCredito()
        tocar("Confirmar")
        composeRule.onNodeWithText("La cuota de octubre de Vehículo 4083 ya está pagada. ¿Este pago es un abono extra?").assertIsDisplayed()
        tocar(UN_ABONO_EXTRA)
        val (_, abono) = repo.conPatas.last()
        assertEquals(2, repo.conPatas.size)
        assertTrue(abono.comoAbono)
        assertEquals(vehiculo4083.id, abono.destinoId)
    }

    @Test
    fun es_el_pago_de_una_tarjeta_lista_solo_las_tarjetas_y_arma_el_pago() {
        val repo = Repo(avisoDeOccidente, leidoDeOccidente, cuentas = listOf(ahorros4057, vehiculo4083, masterBlack))
        montar(repo, avisoDeOccidente.id)
        composeRule.onNodeWithTag(DeudaQuePagaElAviso.TARJETA.tag).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¿De cuál tarjeta?").assertIsDisplayed()
        composeRule.onAllNodesWithText(textoDeCuentaDeDeuda(vehiculo4083)).assertCountEquals(0)
        tocar(textoDeCuentaDeDeuda(masterBlack))
        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de Bancolombia Ahorros 4057 · entra a Master Black 3684 como pago")
        tocar("Confirmar")
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, repo.conPatas.single().second.operacion)
    }

    // ── Vuelta 57 · una cuota sin crédito no se confirma, y el selector respeta la categoría ──────

    private val nu = Account("nu", "Nu", AccountType.CREDIT_CARD, -300_000)

    /**
     * Revisión de la vuelta 56: la memoria dejó «Cuota de crédito» («Así lo anotaste la última vez») y,
     * sin elegir el crédito, Confirmar escribía un gasto suelto en «Cuota de crédito». Ahora Confirmar no
     * prende, el pie dice «Elige el crédito», el selector pregunta «¿De cuál crédito?» sin la tarjeta, y con
     * el crédito la cuota va con sus dos patas.
     */
    @Test
    fun una_cuota_sin_credito_no_se_confirma_y_el_selector_lista_solo_creditos() {
        val leido = leidoDeOccidente.copy(category = CUOTA_CATEGORY, aprendidoDe = "Así lo anotaste la última vez")
        val repo = Repo(avisoDeOccidente, leido, cuentas = listOf(ahorros4057, vehiculo4083, nu))
        montar(repo, avisoDeOccidente.id)

        composeRule.onNodeWithText("Confirmar").assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_FALTA_EL_CREDITO).assertTextEquals(ELIGE_EL_CREDITO)
        tocar("Confirmar")
        assertTrue(repo.sueltos.isEmpty() && repo.conPatas.isEmpty(), "nunca un gasto suelto en «Cuota de crédito»")

        composeRule.onNodeWithText("¿De cuál crédito?").assertIsDisplayed()
        // Vuelta 58: no «Es opcional… ninguna» debajo de «¿De cuál crédito?» con Confirmar apagado.
        composeRule.onNodeWithTag(TAG_AYUDA_DEL_SELECTOR_DE_DEUDA).assertTextEquals("Elige el crédito: sin él no se confirma")
        composeRule.onAllNodesWithText("Es opcional", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("¿A cuál crédito o tarjeta corresponde?").assertCountEquals(0)
        composeRule.onAllNodesWithText(textoDeCuentaDeDeuda(nu)).assertCountEquals(0)
        tocar(textoDeCuentaDeDeuda(vehiculo4083))

        composeRule.onNodeWithTag(TAG_FALTA_EL_CREDITO).assertDoesNotExist()
        composeRule.onNodeWithText("Confirmar").assertIsEnabled()
        tocar("Confirmar")
        assertEquals(0, repo.sueltos.size)
        val (_, patas) = repo.conPatas.single()
        assertEquals(OperacionDelAviso.CUOTA, patas.operacion)
        assertEquals(vehiculo4083.id, patas.destinoId)
    }

    /** …y con «Pago de tarjeta» el selector lista solo las tarjetas. */
    @Test
    fun con_pago_de_tarjeta_el_selector_lista_solo_tarjetas() {
        val leido = leidoDeOccidente.copy(category = CARD_PAYMENT_CATEGORY)
        val repo = Repo(avisoDeOccidente, leido, cuentas = listOf(ahorros4057, vehiculo4083, nu))
        montar(repo, avisoDeOccidente.id)

        composeRule.onNodeWithText("¿De cuál tarjeta?").assertIsDisplayed()
        composeRule.onAllNodesWithText(textoDeCuentaDeDeuda(vehiculo4083)).assertCountEquals(0)
        // Vuelta 58: sin tarjeta elegida se puede confirmar, y el pie explica qué pasa.
        composeRule.onNodeWithText("Confirmar").assertIsEnabled()
        composeRule.onNodeWithTag(TAG_PAGO_DE_TARJETA_SIN_TARJETA).assertTextEquals(PAGO_DE_TARJETA_SIN_TARJETA)
        composeRule.onNodeWithTag(TAG_AYUDA_DEL_SELECTOR_DE_DEUDA).assertTextEquals(AYUDA_DE_LA_TARJETA_EN_EL_AVISO)
        tocar(textoDeCuentaDeDeuda(nu))
        composeRule.onNodeWithTag(TAG_PAGO_DE_TARJETA_SIN_TARJETA).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_RESUMEN_DE_LAS_DOS_PATAS)
            .assertTextEquals("Sale de Bancolombia Ahorros 4057 · entra a Nu como pago")
        tocar("Confirmar")
        assertEquals(OperacionDelAviso.PAGO_DE_TARJETA, repo.conPatas.single().second.operacion)
    }

    // ── Vuelta 58 · la tarjeta resumen y la fila de categoría dicen lo mismo; pago sin tarjeta ───────

    @Test
    fun la_tarjeta_resumen_dice_sin_categoria_cuando_la_fila_dice_elige_la_categoria() {
        val repo = Repo(avisoDeOccidente, leidoDeOccidente, cuentas = listOf(ahorros4057, vehiculo4083, nu))
        montar(repo, avisoDeOccidente.id)
        // La fila: «Elige la categoría · Movi no la entendió». La tarjeta: «Sin categoría», no «Otros».
        composeRule.onNodeWithText(ELIGE_LA_CATEGORIA).assertExists()
        composeRule.onNodeWithText("Sin categoría · ", substring = true).assertExists()
        composeRule.onAllNodesWithText("Otros · ", substring = true).assertCountEquals(0)
    }

    /** Un pago de tarjeta sin tarjeta elegida se confirma: es un pago a una tarjeta que no está en Movi. */
    @Test
    fun un_pago_de_tarjeta_sin_tarjeta_se_confirma_como_gasto_suelto_de_pago_de_tarjeta() {
        val leido = leidoDeOccidente.copy(category = CARD_PAYMENT_CATEGORY)
        val repo = Repo(avisoDeOccidente, leido, cuentas = listOf(ahorros4057, vehiculo4083, nu))
        montar(repo, avisoDeOccidente.id)
        composeRule.onNodeWithTag(TAG_PAGO_DE_TARJETA_SIN_TARJETA).assertTextEquals(PAGO_DE_TARJETA_SIN_TARJETA)
        tocar("Confirmar")
        assertEquals(CARD_PAYMENT_CATEGORY, repo.sueltos.single().category)
        assertTrue(repo.conPatas.isEmpty())
    }

    private fun montar(repo: Repo, smsId: String) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = smsId) }
            }
        }
        composeRule.waitForIdle()
    }

    /** `performSemanticsAction`: bajo Robolectric `performClick` no llega al composable. */
    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
