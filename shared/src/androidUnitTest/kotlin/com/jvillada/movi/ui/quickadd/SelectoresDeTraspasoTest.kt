package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.TAG_AVATAR_DE_CUENTA_SIN_MARCA
import com.jvillada.movi.ui.components.TAG_AVATAR_DE_MARCA
import com.jvillada.movi.ui.components.ds.TAG_CIFRA
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # «Desde» y «Hacia» se eligen como «Cuenta» (vuelta de diseño 15)
 *
 * La vuelta 13 pasó el selector de «Cuenta» de Gasto e Ingreso a una [com.jvillada.movi.ui.components.ds.Fila]
 * por cuenta —la marca, el nombre, el saldo con `Cifra`, «Debes» en una deuda y la elegida
 * marcada—, y los de Traspaso y Cuota se quedaron con el nombre pelado y un «Elegida» en texto.
 * Ahora usan la misma fila ([FilaDeCuentaEnAgregar]). Se monta la hoja entera, como
 * `HojaDeCuotaConInteresRealTest`, y se toca con la acción semántica.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class SelectoresDeTraspasoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800)
    private val nu = Account("n1", "Nu Ahorros", AccountType.SAVINGS, 2_300_000)
    private val libre = Account("l9695", "Libre inversión 9695", AccountType.LOAN, 40_710_555)

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(pestana: String) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(ahorros, nu, libre)
            override suspend fun getCredits(): List<CreditSummary> =
                listOf(CreditSummary(account = libre, terms = null, paidPct = null))
        }
        composeRule.setContent {
            MoviTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        QuickAddScreen(onDismiss = {})
                    }
                    Spacer(Modifier.height(Medida.altoDeBarraDePestanas))
                }
            }
        }
        composeRule.waitForIdle()
        tocar(pestana)
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun fila(cuenta: Account) = composeRule.onNodeWithTag(tagDeFilaDeCuentaEnAgregar(cuenta.id))

    /** Cada cuenta del selector es una `Fila` con su marca y su `Cifra`, una sola de cada una. */
    private fun cadaCuentaEsUnaFilaConSuCifra(cuentas: List<Account>) {
        cuentas.forEach { cuenta ->
            fila(cuenta).performScrollTo().assertIsDisplayed()
            composeRule.onAllNodes(
                hasTestTag(TAG_CIFRA) and hasAnyAncestor(hasTestTag(tagDeFilaDeCuentaEnAgregar(cuenta.id))),
                useUnmergedTree = true,
            ).assertCountEquals(1)
            composeRule.onAllNodes(
                (hasTestTag(TAG_AVATAR_DE_MARCA) or hasTestTag(TAG_AVATAR_DE_CUENTA_SIN_MARCA)) and
                    hasAnyAncestor(hasTestTag(tagDeFilaDeCuentaEnAgregar(cuenta.id))),
                useUnmergedTree = true,
            ).assertCountEquals(1)
        }
    }

    @Test
    fun desde_y_hacia_de_traspaso_pintan_una_fila_con_cifra_por_cuenta() {
        montar("Traspaso")

        tocar("Desde")
        cadaCuentaEsUnaFilaConSuCifra(listOf(ahorros, nu, libre))
        fila(ahorros).assert(hasText("$13.845.800"))
        // Un crédito dice lo que se debe, sin signo.
        fila(libre).assert(hasText("Debes")).assert(hasText("$40.710.555"))
        // Ya no hay un «Elegida» en texto: la elegida se marca en la marca y se anuncia como elegida.
        composeRule.onNodeWithText("Elegida").assertDoesNotExist()
        val elegidaDesde = listOf(ahorros, nu).single { cuenta ->
            runCatching { fila(cuenta).assertIsSelected() }.isSuccess
        }
        composeRule.onAllNodes(hasTestTag(TAG_CUENTA_ELEGIDA), useUnmergedTree = true).assertCountEquals(1)

        // Elegir otra cierra el selector y vuelve al formulario.
        val otra = listOf(ahorros, nu).single { it != elegidaDesde }
        fila(otra).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        tocar("Hacia")
        cadaCuentaEsUnaFilaConSuCifra(listOf(ahorros, nu, libre))
    }

    @Test
    fun la_cuota_elige_la_deuda_con_la_misma_fila() {
        montar("Cuota")

        tocar("Hacia")
        fila(libre).performScrollTo().assertIsDisplayed()
            .assert(hasText("Debes")).assert(hasText("$40.710.555")).assertIsNotSelected()
        composeRule.onAllNodes(
            hasTestTag(TAG_CIFRA) and hasAnyAncestor(hasTestTag(tagDeFilaDeCuentaEnAgregar(libre.id))),
            useUnmergedTree = true,
        ).assertCountEquals(1)

        fila(libre).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar("Desde")
        cadaCuentaEsUnaFilaConSuCifra(listOf(ahorros, nu))
    }
}
