package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.shared.model.DeudaDelExtracto
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.coroutines.awaitCancellation
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TarjetaDelExtracto
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.OrigenDeLaCuentaDelBanco
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * # Vuelta 129 · el extracto de una tarjeta va a una tarjeta
 *
 * Una probadora sin su tarjeta Nu en Movi subió el extracto de la tarjeta. La revisión le proponía
 * «Nu Ahorros» —el número no era de ninguna cuenta suya, y el banco era «Nu»—, y las compras habrían
 * caído en la cuenta de ahorros. Ahora, cuando la lectura dice que es de una tarjeta:
 *
 * - sin la tarjeta en Movi no se elige ninguna cuenta y se ofrece «Crear la tarjeta Nu», ya llena;
 *   creada, el importe va a ella;
 * - con la tarjeta en Movi, se importa en ella;
 * - con una cuenta de ahorros elegida (o puesta al subir el papel), se advierte antes de importar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h900dp-xhdpi")
class ElExtractoDeUnaTarjetaVaAUnaTarjetaTest {

    @get:Rule val composeRule = createComposeRule()

    private val nuAhorros = Account("a-nu", "Nu Ahorros", AccountType.SAVINGS, 1_200_000)
    private val nuTarjeta = Account("c-nu", "Nu Tarjeta", AccountType.CREDIT_CARD, 0)
    private val delExtracto = TarjetaDelExtracto(
        banco = "Nu", ultimosCuatro = "1111", diaDeCorte = 12, diaDePago = 3, cupo = 3_000_000,
        deudaAlCorte = 411_240, deudaHasta = "2026-10-11",
    )

    private val compra = ParsedTransaction(
        id = "f1", date = "2026-10-10", merchant = "Panaderia La Esquina", amount = 50_000,
        type = TransactionType.EXPENSE, category = "Comida", description = "Panaderia La Esquina", rawText = "",
    )

    private fun extracto(cuentaDelPapel: String? = null, tarjeta: TarjetaDelExtracto? = delExtracto) = StatementParseResult(
        statementId = "st_nu", bankName = "Nu", period = "Octubre 2026",
        newTransactions = listOf(compra), matches = emptyList(),
        numerosDeCuenta = listOf("1111", "2222"), tarjeta = tarjeta, cuentaDelPapel = cuentaDelPapel,
    )

    private val decisiones = mutableListOf<ImportDecision>()
    private val creadas = mutableListOf<CreateCardRequest>()
    private val cuadres = mutableListOf<Pair<String, DeudaDelExtracto>>()

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(
        cuentas: List<Account>,
        result: StatementParseResult,
        leerCuentas: suspend () -> List<Account> = { cuentas },
    ) {
        decisiones.clear(); creadas.clear(); cuadres.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = leerCuentas()
            override suspend fun cuadrarLaDeudaConElExtracto(accountId: String, deuda: DeudaDelExtracto): AdjustAccountBalanceResponse {
                cuadres += accountId to deuda
                return AdjustAccountBalanceResponse(account = Account(accountId, "Nu Tarjeta 1111", AccountType.CREDIT_CARD, deuda.deuda))
            }
            override suspend fun importStatement(decision: ImportDecision) { decisiones += decision }
            override suspend fun createCard(request: CreateCardRequest): CardSummary {
                creadas += request
                return CardSummary(
                    account = Account("c-nueva", request.name, AccountType.CREDIT_CARD, 0),
                    terms = request.terms.copy(accountId = "c-nueva"),
                )
            }
        }
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { StatementReviewScreen(onNavigate = {}, result = result) } }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    // ── La regla ────────────────────────────────────────────────────────────────

    @Test
    fun sin_la_tarjeta_no_se_propone_la_cuenta_de_ahorros_del_mismo_banco() {
        val sola = cuentaDelExtracto(listOf(nuAhorros), extracto(), elegidaAMano = null)
        assertNull(sola.cuenta, "el extracto de la tarjeta no va a «Nu Ahorros»")
        assertIs<LoQueDiceLaTarjeta.FaltaLaTarjeta>(loQueDiceLaTarjeta(listOf(nuAhorros), extracto(), destino = null))
    }

    @Test
    fun con_la_tarjeta_en_movi_va_a_ella() {
        val resuelta = cuentaDelExtracto(listOf(nuAhorros, nuTarjeta), extracto(), elegidaAMano = null)
        assertEquals(nuTarjeta, resuelta.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_BANCO, resuelta.origen)
        assertEquals(LoQueDiceLaTarjeta.Nada, loQueDiceLaTarjeta(listOf(nuAhorros, nuTarjeta), extracto(), nuTarjeta))
    }

    @Test
    fun un_extracto_que_no_es_de_tarjeta_sigue_con_la_regla_de_siempre() {
        val resuelta = cuentaDelExtracto(listOf(nuAhorros), extracto(tarjeta = null), elegidaAMano = null)
        assertEquals(nuAhorros, resuelta.cuenta)
        assertEquals(LoQueDiceLaTarjeta.Nada, loQueDiceLaTarjeta(listOf(nuAhorros), extracto(tarjeta = null), nuAhorros))
    }

    // ── La pantalla ─────────────────────────────────────────────────────────────

    @Test
    fun sin_la_tarjeta_ofrece_crearla_llena_y_al_crearla_importa_en_ella() {
        montar(listOf(nuAhorros), extracto())

        composeRule.onNodeWithText("Elige la cuenta").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Este extracto es de una tarjeta de crédito Nu que no tienes en Movi. Créala para que sus movimientos se anoten ahí.",
        ).assertIsDisplayed()

        tocar("Crear la tarjeta Nu")
        // La hoja de siempre, llena con lo que dice el extracto. La deuda no se pide: la cuadra el
        // extracto después de importar (escrita acá, las compras contarían dos veces).
        composeRule.onNodeWithText("Deuda actual (opcional)").assertDoesNotExist()
        composeRule.onNodeWithText(com.jvillada.movi.ui.credits.LA_DEUDA_LA_CUADRA_EL_EXTRACTO).assertIsDisplayed()
        composeRule.onNodeWithText("Nu Tarjeta 1111").assertIsDisplayed()
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        tocar("Guardar tarjeta")

        val creada = creadas.single()
        assertEquals("Nu Tarjeta 1111", creada.name)
        assertEquals("Nu", creada.terms.bank)
        assertEquals(12, creada.terms.cutoffDay)
        assertEquals(3, creada.terms.paymentDay)
        assertEquals(3_000_000L, creada.terms.creditLimit)

        assertEquals(0L, creada.initialDebt)

        // Creada, es el destino: el importe va a ella.
        tocar("Importar 1 seleccionada")
        assertEquals("c-nueva", decisiones.single().accountId)

        // Y después de importar, la propuesta de cuadrar la deuda con el extracto, que no se hace sola.
        composeRule.onNodeWithText("Cuadrar la deuda con el extracto: ${'$'}411.240").assertIsDisplayed()
        assertEquals(emptyList(), cuadres)
        tocar("Cuadrar")
        assertEquals(listOf("c-nueva" to DeudaDelExtracto(deuda = 411_240, hasta = "2026-10-11")), cuadres)
    }

    /** Revisión: una tarjeta que ya se cuadró (o se creó con su saldo) después del corte no se cuadra otra vez. */
    @Test
    fun a_una_tarjeta_cuadrada_despues_del_corte_no_se_le_propone_cuadrar() {
        montar(listOf(nuAhorros, nuTarjeta), extracto().copy(tarjetasCuadradasDespues = listOf(nuTarjeta.id)))
        tocar("Importar 1 seleccionada")
        assertEquals(nuTarjeta.id, decisiones.single().accountId)
        composeRule.onNodeWithTag(TAG_CUADRAR_LA_DEUDA).assertDoesNotExist()
    }

    /** Revisión: con saldo a favor (deuda negativa) no se ofrece: el server no lo acepta. */
    @Test
    fun con_saldo_a_favor_no_se_propone_cuadrar() {
        montar(listOf(nuAhorros, nuTarjeta), extracto(tarjeta = delExtracto.copy(deudaAlCorte = -12_000)))
        tocar("Importar 1 seleccionada")
        composeRule.onNodeWithTag(TAG_CUADRAR_LA_DEUDA).assertDoesNotExist()
    }

    /** Revisión: la tarjeta de otro banco que se le puso al papel no gana contra el extracto de Nu. */
    @Test
    fun la_tarjeta_de_otro_banco_del_papel_pierde_contra_el_extracto() {
        val amex = Account("c-amex", "AMEX 9208", AccountType.CREDIT_CARD, 0)
        val cuentas = listOf(amex, nuTarjeta)
        val result = extracto(cuentaDelPapel = amex.id)
        assertEquals(nuTarjeta, cuentaDelExtracto(cuentas, result, elegidaAMano = null).cuenta)
        assertEquals(
            "Al subir el archivo elegiste AMEX 9208, pero este extracto es de una tarjeta Nu.",
            porQueNoLaDelPapel(cuentas, result, elegidaAMano = null),
        )
    }

    @Test
    fun sin_la_deuda_en_el_extracto_no_se_propone_cuadrar() {
        montar(listOf(nuAhorros, nuTarjeta), extracto(tarjeta = delExtracto.copy(deudaAlCorte = null)))
        tocar("Importar 1 seleccionada")
        assertEquals(nuTarjeta.id, decisiones.single().accountId)
        composeRule.onNodeWithTag(TAG_CUADRAR_LA_DEUDA).assertDoesNotExist()
    }

    // ── Revisión: mientras las cuentas no llegan, nada de «Crear» ──────────────────

    @Test
    fun mientras_cargan_las_cuentas_no_se_ofrece_crear_la_tarjeta() {
        montar(emptyList(), extracto(), leerCuentas = { awaitCancellation() })
        composeRule.onNodeWithText("Crear la tarjeta Nu").assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_AVISO_DE_LA_TARJETA).assertDoesNotExist()
    }

    @Test
    fun si_las_cuentas_no_se_pudieron_leer_tampoco() {
        montar(emptyList(), extracto(), leerCuentas = { throw IllegalStateException("sin red") })
        composeRule.onNodeWithText("Crear la tarjeta Nu").assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_AVISO_DE_LA_TARJETA).assertDoesNotExist()
    }

    @Test
    fun sin_la_tarjeta_no_se_puede_importar_en_la_cuenta_de_ahorros_sin_elegirla() {
        montar(listOf(nuAhorros), extracto())
        // Sin destino el botón está apagado: nada va a «Nu Ahorros» por descarte.
        composeRule.onNodeWithText("Importar 1 seleccionada").assertIsNotEnabled()
    }

    @Test
    fun con_la_tarjeta_creada_importa_en_ella() {
        montar(listOf(nuAhorros, nuTarjeta), extracto())

        composeRule.onNodeWithText("Nu Tarjeta").assertIsDisplayed()
        tocar("Importar 1 seleccionada")
        assertEquals(nuTarjeta.id, decisiones.single().accountId)
    }

    /** Revisión: la cuenta del papel es una sugerencia; el contenido (una tarjeta) gana y se dice por qué. */
    @Test
    fun la_cuenta_de_ahorros_puesta_al_subir_el_papel_pierde_contra_el_contenido() {
        montar(listOf(nuAhorros, nuTarjeta), extracto(cuentaDelPapel = nuAhorros.id))

        composeRule.onNodeWithText("Nu Tarjeta").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Al subir el archivo elegiste Nu Ahorros, pero este extracto es de una tarjeta de crédito.",
        ).assertIsDisplayed()
        tocar("Importar 1 seleccionada")
        assertEquals(nuTarjeta.id, decisiones.single().accountId)
    }

    @Test
    fun la_cuenta_de_ahorros_del_papel_sin_tarjeta_en_movi_ofrece_crearla() {
        montar(listOf(nuAhorros), extracto(cuentaDelPapel = nuAhorros.id))

        composeRule.onNodeWithText(
            "Al subir el archivo elegiste Nu Ahorros, pero este extracto es de una tarjeta de crédito.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Crear la tarjeta Nu").assertIsDisplayed()
        composeRule.onNodeWithText("Importar 1 seleccionada").assertIsNotEnabled()
    }

    /** Elegida con el dedo en esta pantalla, la cuenta de ahorros manda, pero se advierte. */
    @Test
    fun la_cuenta_de_ahorros_elegida_a_mano_se_advierte_y_ofrece_la_tarjeta() {
        montar(listOf(nuAhorros, nuTarjeta), extracto())
        tocar("Cambiar")
        tocar("Nu Ahorros")

        composeRule.onNodeWithText("Este extracto es de una tarjeta de crédito, no de Nu Ahorros.").assertIsDisplayed()
        tocar("Usar Nu Tarjeta")
        composeRule.onNodeWithText("Este extracto es de una tarjeta de crédito, no de Nu Ahorros.").assertDoesNotExist()
        tocar("Importar 1 seleccionada")
        assertEquals(nuTarjeta.id, decisiones.single().accountId)
    }

    // ── Revisión: la cuenta del papel en un extracto de cuenta ──────────────────

    private val bancolombia = Account("a-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 1)
    private val bancolombia0756 = Account("a-0756", "Bancolombia Ahorros 0756", AccountType.SAVINGS, 1)
    private fun deCuenta(numeros: List<String>, papel: String?) = StatementParseResult(
        statementId = "st_bc", bankName = "Bancolombia", period = "Octubre 2026",
        newTransactions = listOf(compra), matches = emptyList(), numerosDeCuenta = numeros, cuentaDelPapel = papel,
    )

    @Test
    fun la_cuenta_del_papel_se_usa_si_el_contenido_no_la_contradice() {
        val r = cuentaDelExtracto(listOf(bancolombia, nuAhorros), deCuenta(emptyList(), papel = nuAhorros.id), elegidaAMano = null)
        assertEquals(nuAhorros, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.LA_DEL_PAPEL, r.origen)
    }

    @Test
    fun el_numero_del_extracto_gana_a_la_cuenta_del_papel_y_se_dice_por_que() {
        val cuentas = listOf(bancolombia, bancolombia0756)
        val result = deCuenta(listOf("0756"), papel = bancolombia.id)
        val r = cuentaDelExtracto(cuentas, result, elegidaAMano = null)
        assertEquals(bancolombia0756, r.cuenta)
        assertEquals(OrigenDeLaCuentaDelBanco.POR_EL_NUMERO, r.origen)
        assertEquals(
            "Al subir el archivo elegiste Bancolombia Ahorros, pero el extracto es de Bancolombia Ahorros 0756: lo dice su número.",
            porQueNoLaDelPapel(cuentas, result, elegidaAMano = null),
        )
    }

    @Test
    fun sin_cuenta_en_el_papel_es_exactamente_lo_de_antes() {
        val cuentas = listOf(bancolombia, bancolombia0756, nuAhorros)
        for (numeros in listOf(emptyList(), listOf("0756"))) {
            val result = deCuenta(numeros, papel = null)
            assertEquals(
                com.jvillada.movi.ui.components.resolverCuentaDelBanco(
                    cuentas, com.jvillada.movi.shared.model.UsoDeCuenta.CUENTA_DEL_EXTRACTO, "Bancolombia", null,
                    numeros.joinToString(" ") { "*$it" },
                ),
                cuentaDelExtracto(cuentas, result, elegidaAMano = null),
            )
        }
    }
}
