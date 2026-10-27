package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Lo apartado, donde el dueño lo ve y lo toca
 *
 * La cuenta vive en `:core` (`patrimonioDe`) con sus pruebas; acá se fija la pantalla: el detalle de
 * Nu dice el colegio y lo libre, «Apartar plata» crea con un id que no cambia entre reintentos, un
 * corte de red se dice adentro de la hoja sin perder lo escrito, «Quitar» pregunta antes, y una
 * deuda no ofrece apartar. Los clics van por la acción semántica, como en `BienesEnCuentasTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_APARTADOS)
class ApartadosEnLaCuentaTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 8_217_464L)
    private val colegio = Apartado(
        id = "apt_colegio",
        accountId = "acc-nu",
        proposito = "Colegio nov-dic",
        monto = 8_000_000L,
        categoria = "Hija",
        desde = 1_760_000_000_000L,
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun esperarTexto(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    /**
     * La fila «Libre» de los apartados, por su texto entero: «Libre: para cualquier gasto» (la fila
     * de para qué es la plata, vuelta de diseño 5) es otra cosa.
     */
    private fun hayLaFilaLibre(): Boolean =
        composeRule.onAllNodesWithText("Libre", substring = false, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private open inner class ConNu(val cuenta: Account = nu, var lista: List<Apartado> = listOf(colegio)) : RepositorioDePrueba() {
        override suspend fun getAccount(id: String): Account = cuenta
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun getApartados(): List<Apartado> = lista
    }

    private fun detalle(group: AccountGroup = AccountGroup.DINERO) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    AccountDetailScreen(onNavigate = {}, accountId = "acc-nu", group = group)
                }
            }
        }
    }

    @Test
    fun `el detalle de Nu dice el colegio, lo que le queda y lo libre`() {
        Repositories.sustitutoDePrueba = ConNu()
        detalle()

        esperarTexto("Colegio nov-dic"); esperarTexto("\$8.000.000")
        composeRule.waitUntil(timeoutMillis = 5_000) { hayLaFilaLibre() }; esperarTexto("\$217.464")
        esperarTexto("Se descuenta con los gastos de Hija")
        esperarTexto("Apartar plata")
        // El saldo de la cuenta sigue cuadrando con el banco al peso.
        esperarTexto("8.217.464")
    }

    @Test
    fun `apartar crea con el mismo id aunque el primer intento no tenga red`() {
        val intentos = mutableListOf<Apartado>()
        Repositories.sustitutoDePrueba = object : ConNu(lista = emptyList()) {
            override suspend fun guardarApartado(a: Apartado): Apartado {
                intentos += a
                if (intentos.size == 1) throw RuntimeException("Unable to resolve host api.movi")
                return a.copy(restante = a.monto).also { lista = listOf(it) }
            }
        }
        detalle()

        esperarTexto("Apartar plata")
        assertTrue(!hayLaFilaLibre(), "sin apartados no hay nada libre que decir")
        tocar("Apartar plata")
        esperarTexto("¿Para qué?")
        esperarTexto("Se descuenta con los gastos de")

        val editables = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        editables[0].performTextInput("Colegio nov-dic")
        editables[1].performTextInput("8000000")
        composeRule.waitForIdle()

        tocar("Guardar")
        esperarTexto(APARTAR_SIN_CONEXION)
        // Lo escrito se queda: el segundo intento no hay que volver a llenarlo.
        esperarTexto("Colegio nov-dic")

        tocar("Guardar")
        esperarTexto("Colegio nov-dic"); esperarTexto("\$8.000.000")

        assertEquals(2, intentos.size)
        assertEquals(intentos[0].id, intentos[1].id, "reintentar no puede crear otro apartado")
        assertTrue(intentos[0].id.startsWith("apt_"))
        val enviado = intentos[1]
        assertEquals("acc-nu", enviado.accountId)
        assertEquals("Colegio nov-dic", enviado.proposito)
        assertEquals(8_000_000L, enviado.monto)
        assertEquals(null, enviado.categoria)
        assertEquals(0L, enviado.desde, "uno nuevo deja que el server ponga desde cuándo cuenta")
        composeRule.waitUntil(timeoutMillis = 5_000) { hayLaFilaLibre() }; esperarTexto("\$217.464")
    }

    @Test
    fun `quitar pregunta antes y despues quita`() {
        val quitados = mutableListOf<String>()
        Repositories.sustitutoDePrueba = object : ConNu() {
            override suspend fun quitarApartado(id: String) {
                quitados += id
                lista = emptyList()
            }
        }
        detalle()

        esperarTexto("Colegio nov-dic"); esperarTexto("\$8.000.000")
        tocar("Colegio nov-dic")
        esperarTexto("¿Para qué?")
        tocar("Quitar")
        esperarTexto("¿Quitar «Colegio nov-dic»?")
        assertTrue(quitados.isEmpty(), "el primer toque solo pregunta")

        tocar("Quitar")
        composeRule.waitUntil(timeoutMillis = 5_000) { quitados.isNotEmpty() }
        assertEquals(listOf("apt_colegio"), quitados)
        composeRule.waitUntil(timeoutMillis = 5_000) { !hayLaFilaLibre() }
    }

    /**
     * Vuelta de diseño 5: la fila de la condición dice el estado («Para qué es esta plata · Libre:
     * para cualquier gasto»), no la pregunta «¿Solo sirve para algo?», y la hoja habla igual.
     */
    @Test
    fun `la fila de la condicion dice para que es la plata y abre la hoja que habla igual`() {
        Repositories.sustitutoDePrueba = ConNu()
        detalle()
        esperarTexto(PARA_QUE_ES_ESTA_PLATA)
        assertTrue(hay(PLATA_LIBRE))
        assertTrue(!hay("¿Solo sirve para algo?"))

        tocar(PARA_QUE_ES_ESTA_PLATA)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(PARA_QUE_ES_ESTA_PLATA, useUnmergedTree = true).fetchSemanticsNodes().size == 2
        }
        assertTrue(hay("Vacío es libre: para cualquier gasto."))
    }

    @Test
    fun `una cuenta condicionada dice para que es en la fila`() {
        Repositories.sustitutoDePrueba = ConNu(cuenta = nu.copy(condicionadaA = "vivienda"))
        detalle()
        esperarTexto("Solo para vivienda · no suma en «Tu plata»")
        assertTrue(!hay(PLATA_LIBRE))
    }

    @Test
    fun `una deuda no ofrece apartar`() {
        val tarjeta = Account("acc-nu", "Nu Tarjeta", AccountType.CREDIT_CARD, 500_000L)
        Repositories.sustitutoDePrueba = ConNu(cuenta = tarjeta)
        detalle(AccountGroup.DEUDA)

        esperarTexto("Deuda actual")
        composeRule.waitForIdle()
        assertTrue(!hay("Apartar plata"))
        assertTrue(!hay("Colegio nov-dic"))
    }

    @Test
    fun `Patrimonio dice el renglon del colegio y Tu plata sin el`() {
        Repositories.sustitutoDePrueba = object : ConNu() {
            override suspend fun getAccounts(): List<Account> = listOf(nu)
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AccountsScreen(onNavigate = {}) } } }

        esperarTexto("Apartado · Colegio nov-dic")
        esperarTexto("\$217.464")
    }
}

private const val AVD_MOVI_APARTADOS = "w411dp-h731dp-xhdpi"
