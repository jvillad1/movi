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
    private val delExtracto = TarjetaDelExtracto(banco = "Nu", ultimosCuatro = "1111", diaDeCorte = 12, diaDePago = 3, cupo = 3_000_000)

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

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(cuentas: List<Account>, result: StatementParseResult) {
        decisiones.clear(); creadas.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = cuentas
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
        // La hoja de siempre, llena con lo que dice el extracto.
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

        // Creada, es el destino: el importe va a ella.
        tocar("Importar 1 seleccionada")
        assertEquals("c-nueva", decisiones.single().accountId)
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

    @Test
    fun la_cuenta_de_ahorros_puesta_al_subir_el_papel_se_advierte_y_ofrece_la_tarjeta() {
        montar(listOf(nuAhorros, nuTarjeta), extracto(cuentaDelPapel = nuAhorros.id))

        composeRule.onNodeWithText("Este extracto es de una tarjeta de crédito, no de Nu Ahorros.").assertIsDisplayed()
        tocar("Usar Nu Tarjeta")
        composeRule.onNodeWithText("Este extracto es de una tarjeta de crédito, no de Nu Ahorros.").assertDoesNotExist()
        tocar("Importar 1 seleccionada")
        assertEquals(nuTarjeta.id, decisiones.single().accountId)
    }

    @Test
    fun la_cuenta_de_ahorros_sin_tarjeta_en_movi_se_advierte_y_ofrece_crearla() {
        montar(listOf(nuAhorros), extracto(cuentaDelPapel = nuAhorros.id))

        composeRule.onNodeWithText("Este extracto es de una tarjeta de crédito, no de Nu Ahorros.").assertIsDisplayed()
        composeRule.onNodeWithText("Crear la tarjeta Nu").assertIsDisplayed()
    }
}
