package com.jvillada.movi.ui.credits

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.ui.accounts.TAG_ENCABEZADO_DE_LA_CUENTA
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Ninguna acción se perdió en la mudanza (vuelta de diseño 4)
 *
 * La lista de Créditos dejó de tener botones. Todo lo que hacían —«Editar», «Ajustar saldo», «¿Y si
 * abonas de más?», «Registrar pago de …», «Registrar descuento»— está en el detalle de la deuda, y
 * cada uno abre lo mismo que abría.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class LaDeudaEnSuDetalleTest {

    @get:Rule val composeRule = createComposeRule()

    /** La cuota la gira Skandia, y la deuda crece sola: todas las acciones de un préstamo. */
    private val hipotecario = CreditSummary(
        account = Account("acc_2334", "Hipotecario 2334", AccountType.LOAN, balance = 204_183_376L),
        terms = CreditTerms(
            accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
            termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
            insuranceMonthly = 209_219L, paidBy = "Skandia",
        ),
        paidPct = 0.0,
    )

    private val libranza = CreditSummary(
        account = Account("acc_4818", "Libranza 4818", AccountType.LOAN, balance = 255_677_421L),
        terms = CreditTerms(
            accountId = "acc_4818", bank = "Bancolombia", principal = 283_000_000L, rateEa = 18.01,
            termMonths = 96, installment = 6_040_259L, dayOfMonth = 30, startDate = "2025-02-28",
            payrollDeduction = true,
        ),
        paidPct = 0.1,
    )

    private val vehiculo = CreditSummary(
        account = Account("acc_4083", "Vehículo 4083", AccountType.LOAN, balance = 0L),
        terms = null,
        paidPct = null,
        hasMovements = false,
    )

    private val master = CardSummary(
        account = Account("acc_3684", "Master Black 3684", AccountType.CREDIT_CARD, 3_450_000L, balancesByCurrency = mapOf("COP" to 3_450_000L)),
        terms = CardTerms(accountId = "acc_3684", bank = "Bancolombia", cutoffDay = 28, paymentDay = 14, pagoMinimo = 412_300L, creditLimit = 20_000_000L),
        available = 16_550_000L,
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocables(texto: String): Int =
        composeRule.onAllNodes(hasText(texto) and hasClickAction()).fetchSemanticsNodes().size

    private fun tocar(texto: String) {
        composeRule.onNode(hasText(texto) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `un prestamo trae todas sus acciones en el detalle`() {
        composeRule.montarElDetalleDe(hipotecario.account, RepositorioDeLaDeuda(creditos = listOf(hipotecario)))

        listOf(ACCION_SIMULAR_ABONO, "Registrar pago de Skandia", AJUSTAR_EL_SALDO, EDITAR_LAS_CONDICIONES).forEach {
            assertEquals(1, tocables(it), "«$it» tiene que seguir alcanzable")
        }
        assertTrue(hay("La paga Skandia"))
    }

    @Test
    fun `ajustar saldo abre su hoja y editar abre los terminos`() {
        composeRule.montarElDetalleDe(hipotecario.account, RepositorioDeLaDeuda(creditos = listOf(hipotecario)))

        tocar(AJUSTAR_EL_SALDO)
        assertTrue(hay("Deuda en Movi"), "la hoja de ajustar el saldo")
    }

    @Test
    fun `editar las condiciones abre la hoja de terminos`() {
        composeRule.montarElDetalleDe(hipotecario.account, RepositorioDeLaDeuda(creditos = listOf(hipotecario)))

        tocar(EDITAR_LAS_CONDICIONES)
        assertTrue(hay("Guardar crédito"))
    }

    @Test
    fun `registrar el pago de un tercero lo registra`() {
        val repo = RepositorioDeLaDeuda(creditos = listOf(hipotecario))
        composeRule.montarElDetalleDe(hipotecario.account, repo)

        tocar("Registrar pago de Skandia")
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.registrados.isNotEmpty() }
        assertEquals(listOf("acc_2334"), repo.registrados)
    }

    @Test
    fun `una libranza registra el descuento de la nomina`() {
        val repo = RepositorioDeLaDeuda(creditos = listOf(libranza))
        composeRule.montarElDetalleDe(libranza.account, repo)

        assertEquals(0, tocables("Registrar pago de"), "la nómina no es un tercero que gira")
        tocar("Registrar descuento")
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.registrados.isNotEmpty() }
        assertEquals(listOf("acc_4818"), repo.registrados)
        assertTrue(hay("De tu nómina"))
    }

    /**
     * Vuelta 31: una deuda sin términos se abre igual que las demás. El detalle lo dice arriba, en el
     * tono de aviso, y «Completar datos» abre el formulario de siempre.
     */
    @Test
    fun `una deuda sin terminos lo dice arriba, con Completar datos, y deja ajustar el saldo`() {
        composeRule.montarElDetalleDe(vehiculo.account, RepositorioDeLaDeuda(creditos = listOf(vehiculo)))

        assertTrue(hay("Faltan los datos de este préstamo"))
        // Vuelta 77: sin saldo, el saldo se ajusta con «Cuadrar» del encabezado (la misma hoja); «Ajustar
        // saldo» no se repite en «Qué puedes hacer».
        assertEquals(1, tocables(CUADRAR_LAS_DEUDAS_SIN_SALDO))
        assertEquals(0, tocables(AJUSTAR_EL_SALDO))
        val aviso = composeRule.onNodeWithTag(TAG_FALTAN_DATOS_DE_LA_DEUDA, useUnmergedTree = true).fetchSemanticsNode()
        val saldo = composeRule.onNodeWithTag(TAG_ENCABEZADO_DE_LA_CUENTA, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(aviso.boundsInRoot.bottom <= saldo.boundsInRoot.top, "el aviso va arriba del saldo")
        tocar(COMPLETAR_DATOS)
        assertTrue(hay("Guardar crédito"))
    }

    /** Una tarjeta con nombre de banco («Nu») llega al formulario con el banco ya lleno. */
    @Test
    fun `la Nu sin terminos abre su detalle y el formulario llega con el banco lleno`() {
        val nu = CardSummary(
            account = Account("acc_nu", "Nu", AccountType.CREDIT_CARD, 1_998_960L, balancesByCurrency = mapOf("COP" to 1_998_960L)),
            terms = null,
        )
        composeRule.montarElDetalleDe(nu.account, RepositorioDeLaDeuda(tarjetas = listOf(nu)))

        assertTrue(hay("Faltan los datos de esta tarjeta"))
        tocar(COMPLETAR_DATOS)
        assertTrue(hay("Guardar tarjeta"))
        val banco = composeRule.onAllNodes(hasSetTextAction() and hasText("Nu"), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, banco.size, "el campo Banco llega con «Nu»")
    }

    @Test
    fun `una tarjeta dice su pago y su cupo, y se edita desde el detalle`() {
        composeRule.montarElDetalleDe(master.account, RepositorioDeLaDeuda(tarjetas = listOf(master)))

        assertTrue(hay("Pago mínimo"))
        assertTrue(hay("\$412.300"))
        assertTrue(hay("Disponible"))
        assertTrue(hay("De un cupo de \$20.000.000"))
        tocar(EDITAR_LAS_CONDICIONES)
        assertTrue(hay("Guardar tarjeta"), "la hoja de los términos de la tarjeta")
    }
}
