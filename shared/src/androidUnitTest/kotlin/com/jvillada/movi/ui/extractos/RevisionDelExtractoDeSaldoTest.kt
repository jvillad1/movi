package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.shared.model.ContratoDelExtracto
import com.jvillada.movi.shared.model.CuentaParaElSaldo
import com.jvillada.movi.shared.model.ExtractoDeSaldoDe
import com.jvillada.movi.shared.model.PropuestaDelExtractoDeSaldo
import com.jvillada.movi.shared.model.ResumenDelExtractoDeSaldo
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
 * # La revisión del extracto de Skandia o de la AFC
 *
 * Que se lea la propuesta («El extracto dice $X al 31 de agosto; Movi tiene $Y»), que **nada se
 * escriba** hasta tocar «Actualizar el saldo», y que al tocarlo llegue al repositorio la cuenta y
 * lo que dijo el extracto. Con cifras inventadas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class RevisionDelExtractoDeSaldoTest {

    @get:Rule val composeRule = createComposeRule()

    private val resumen = ResumenDelExtractoDeSaldo(
        de = ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO,
        saldo = 12_345_679L,
        fechaDeCorte = "2026-08-31",
        contratos = listOf(ContratoDelExtracto("1111", 10_000_000L), ContratoDelExtracto("2222", 2_345_679L)),
    )
    private val skandia = CuentaParaElSaldo(id = "acc-skandia", nombre = "Skandia FPV", saldoEnMovi = 12_000_000L)
    private val ahorros = CuentaParaElSaldo(id = "acc-ahorros", nombre = "Ahorros 8133", saldoEnMovi = 500_000L)

    /** Lo que llegó al repositorio: (cuenta, resumen). Vacío = no se escribió nada. */
    private val llamadas = mutableListOf<Pair<String, ResumenDelExtractoDeSaldo>>()

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun actualizarElSaldoConElExtracto(accountId: String, resumen: ResumenDelExtractoDeSaldo): AdjustAccountBalanceResponse {
            llamadas += accountId to resumen
            return AdjustAccountBalanceResponse(
                account = Account(id = accountId, name = "x", type = AccountType.INVESTMENT, balance = resumen.saldo),
            )
        }
    }

    private fun montar(propuesta: PropuestaDelExtractoDeSaldo) {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { RevisionDelExtractoDeSaldo(propuesta, titulo = "Skandia · Agosto 2026") } }
        }
        composeRule.waitForIdle()
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        llamadas.clear()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto, substring = true)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `muestra la propuesta y al tocar Actualizar el saldo llama al repositorio`() {
        montar(PropuestaDelExtractoDeSaldo(resumen, cuentaId = skandia.id, opciones = listOf(skandia, ahorros)))

        assertTrue(hay("El extracto dice $12.345.679 al 31 de agosto de 2026; Movi tiene $12.000.000."))
        assertTrue(hay("Contrato ·1111"))
        assertTrue(hay("Skandia FPV"))
        assertTrue(hay("lo que rindió"), "en el fondo, la diferencia es la valuación")
        assertTrue(llamadas.isEmpty(), "abrir la revisión no escribe nada")

        tocar(ACTUALIZAR_EL_SALDO)

        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Listo") }
        assertEquals(listOf(skandia.id to resumen), llamadas)
    }

    @Test
    fun `con duda, el duenio elige la cuenta antes de actualizar`() {
        montar(PropuestaDelExtractoDeSaldo(resumen, cuentaId = null, opciones = listOf(skandia, ahorros)))

        assertTrue(hay("¿En qué cuenta?"))
        assertTrue(hay("Elige la cuenta"))
        tocar(ACTUALIZAR_EL_SALDO)
        assertTrue(llamadas.isEmpty(), "sin cuenta elegida no hay nada que actualizar")

        tocar("Ahorros 8133")
        assertTrue(hay("Movi tiene $500.000."))
        tocar(ACTUALIZAR_EL_SALDO)

        composeRule.waitUntil(timeoutMillis = 5_000) { hay("Listo") }
        assertEquals(listOf(ahorros.id to resumen), llamadas)
    }

    @Test
    fun `si Movi ya tiene ese saldo, no ofrece nada`() {
        montar(PropuestaDelExtractoDeSaldo(resumen, cuentaId = skandia.id, opciones = listOf(skandia.copy(saldoEnMovi = 12_345_679L))))

        assertTrue(hay("no hay nada que actualizar"))
        tocar(ACTUALIZAR_EL_SALDO)
        assertTrue(llamadas.isEmpty())
    }
}
