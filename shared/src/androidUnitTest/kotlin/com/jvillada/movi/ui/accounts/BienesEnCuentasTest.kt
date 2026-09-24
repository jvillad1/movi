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
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Bien
import com.jvillada.movi.shared.model.CLASE_DE_BIEN_INMUEBLE
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # La casa en la pantalla Cuentas: se ve, se explica y se crea desde la app
 *
 * La regla de cuánto suma vive en `:core` con sus pruebas; acá se fija lo que el dueño toca: que
 * la sección «Bienes» aparezca con el valor, la fecha del avalúo y lo que es suyo de verdad, que
 * la tarjeta de patrimonio tenga el renglón que hace cerrar la resta, y que la hoja mande al
 * repositorio un bien con la forma que el server espera.
 *
 * Los clics van por la acción semántica, como en `DestinosScreenTest`: la hoja es más alta que la
 * pantalla de prueba y un toque inyectado fuera de vista caería en el fondo oscuro, que la cierra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_BIENES)
class BienesEnCuentasTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account("acc-nu", "Nu", AccountType.SAVINGS, 558_350L)
    private val hipoteca = Account("acc-1254", "Hipoteca 1254", AccountType.LOAN, 1_030_600_000L)
    private val casa = Account(
        "acc-casa", "Casa Almendros", AccountType.INVESTMENT, 0L,
        bien = Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = "2026-08-28", deudaId = "acc-1254"),
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

    private fun tocar(texto: String) {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `la seccion Bienes muestra el valor, el avaluo y lo que es tuyo de verdad`() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(casa, hipoteca, nu)
            override suspend fun getCredits(): List<CreditSummary> = emptyList()
            override suspend fun getCards(): List<CardSummary> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { AccountsScreen(onNavigate = {}) } } }

        esperarTexto("BIENES")
        esperarTexto("Casa Almendros")
        // Con o sin año según cuándo corra la prueba: lo que importa es que diga de cuándo es.
        esperarTexto("avalúo del 28 de agosto")
        esperarTexto("Debes $1.030,6M en Hipoteca 1254 · tuyo $381,3M")
        esperarTexto("1.411.903.920")
    }

    @Test
    fun `la hoja crea un bien con la forma que espera el server`() {
        var creada: Account? = null
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun createAccount(account: Account): Account = account.also { creada = it }
        }
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    BienSheet(
                        existente = null,
                        nombreInicial = "Casa Almendros",
                        cuentas = listOf(nu, hipoteca),
                        onDismiss = {},
                        onGuardado = {},
                    )
                }
            }
        }

        esperarTexto("Nuevo bien")
        // Sin valor el botón no guarda y dice por qué, con la misma frase que el server.
        esperarTexto("Escribe cuánto vale")

        val editables = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        editables[1].performTextInput("1411903920")
        composeRule.waitForIdle()
        tocar("Hipoteca 1254")
        tocar("Crear bien")
        composeRule.waitUntil(timeoutMillis = 5_000) { creada != null }

        val cuenta = creada!!
        assertEquals("Casa Almendros", cuenta.name)
        assertEquals(AccountType.INVESTMENT, cuenta.type)
        assertEquals(0L, cuenta.balance)
        assertEquals(Bien(CLASE_DE_BIEN_INMUEBLE, 1_411_903_920L, valorAl = null, deudaId = "acc-1254"), cuenta.bien)
    }
}

private const val AVD_MOVI_BIENES = "w411dp-h731dp-xhdpi"
