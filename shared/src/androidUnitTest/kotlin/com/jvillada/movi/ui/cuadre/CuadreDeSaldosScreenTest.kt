package com.jvillada.movi.ui.cuadre

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
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Cuadrar un saldo desde la pantalla, sin sorpresas
 *
 * Lo que se prueba es lo que puede costar plata: que la diferencia se **lea antes** de confirmar,
 * que **nada se escriba** hasta confirmar, que una cuenta en blanco **no se toque**, y que el
 * segundo toque no anote el ajuste dos veces.
 *
 * Los toques van por la **acción semántica** y no por coordenadas, igual que en el resto de las
 * pruebas de pantalla: el botón está al pie y la pantalla de prueba es más corta que el contenido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_CUADRE)
class CuadreDeSaldosScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val nu = Account(
        id = "acc-nu",
        name = "Nu",
        type = AccountType.SAVINGS,
        balance = 352_082L,
        balancesByCurrency = mapOf("COP" to 352_082L),
        firstEventAt = 1_700_000_000_000L,
    )
    private val fiducuenta = Account(
        id = "acc-fidu",
        name = "Fiducuenta",
        type = AccountType.SAVINGS,
        balance = 1_000_000L,
        balancesByCurrency = mapOf("COP" to 1_000_000L),
        firstEventAt = 1_700_000_000_000L,
    )
    private val masterBlack = Account(
        id = "acc-card",
        name = "Master Black",
        type = AccountType.CREDIT_CARD,
        balance = 2_000_000L,
        balancesByCurrency = mapOf("COP" to 2_000_000L),
    )

    /** Lo que llegó al repositorio: (cuenta, saldo objetivo). Vacío = no se escribió nada. */
    private val anotados = mutableListOf<Pair<String, Long>>()

    private open inner class ConDosCuentas : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(nu, fiducuenta, masterBlack)
        override suspend fun adjustAccountBalance(
            accountId: String,
            targetBalance: Long,
        ): AdjustAccountBalanceResponse {
            anotados += accountId to targetBalance
            val cuenta = listOf(nu, fiducuenta).first { it.id == accountId }
            return AdjustAccountBalanceResponse(
                account = cuenta.copy(
                    balance = targetBalance,
                    balancesByCurrency = mapOf("COP" to targetBalance),
                ),
                adjustmentEvent = null,
            )
        }
    }

    private fun montar(repo: RepositorioDePrueba = ConDosCuentas()) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { CuadreDeSaldosScreen(onNavigate = {}) } }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        anotados.clear()
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

    private fun tocar(texto: String) {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(texto)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    /** Escribe en el campo de la fila [fila] (0 = la primera cuenta de la lista). */
    private fun escribir(fila: Int, digitos: String) {
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[fila].performTextInput(digitos)
        composeRule.waitForIdle()
    }

    @Test
    fun `lista las cuentas de dinero con lo que cree Movi, y deja las deudas afuera`() {
        montar()

        esperarTexto("Nu")
        esperarTexto("Fiducuenta")
        esperarTexto("352.082")
        // La tarjeta no se cuadra acá: el banco no muestra UN número para una tarjeta, y su deuda
        // ya la concilian el extracto y la captura de mensajes.
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Master Black", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }

    /**
     * **La diferencia se lee antes de confirmar**, con las dos cifras que se están comparando. Y
     * mientras no se confirma, no se escribió nada — que es la otra mitad de la promesa.
     */
    @Test
    fun `escribir el saldo del banco muestra el ajuste que se va a anotar, sin escribir nada`() {
        montar()
        esperarTexto("Nu")

        escribir(fila = 0, digitos = "270730")

        esperarTexto("Movi dice \$352.082, tú dices \$270.730: se va a anotar un ajuste de −\$81.352.")
        assertTrue(anotados.isEmpty(), "nada se escribe hasta confirmar: $anotados")
    }

    /**
     * Confirmar anota **una sola vez**. El segundo toque es el reflejo de siempre —«no pasó
     * nada, toco otra vez»— y es el que duplicaría el ajuste: con el primero ya anotado, el saldo
     * de Movi cambió, así que el segundo se calcularía contra otra cifra.
     */
    @Test
    fun `confirmar anota el ajuste una sola vez aunque se toque de nuevo`() {
        montar()
        esperarTexto("Nu")
        escribir(fila = 0, digitos = "270730")

        tocar("Anotar el ajuste")
        composeRule.waitUntil(timeoutMillis = 5_000) { anotados.isNotEmpty() }
        tocar("Anotar el ajuste")
        composeRule.waitForIdle()

        assertEquals(listOf("acc-nu" to 270_730L), anotados)
    }

    /**
     * **Una cuenta en blanco no se toca.** Si el campo vacío se leyera como «el banco dice $0», el
     * dueño entraría a cuadrar una cuenta y saldría con las otras en cero.
     */
    @Test
    fun `la cuenta que queda en blanco no se toca`() {
        montar()
        esperarTexto("Nu")

        escribir(fila = 1, digitos = "1745856")
        tocar("Anotar el ajuste")
        composeRule.waitUntil(timeoutMillis = 5_000) { anotados.isNotEmpty() }

        assertEquals(listOf("acc-fidu" to 1_745_856L), anotados)
    }

    /** Escribir la misma cifra que ya tiene Movi no anota nada: un ajuste de $0 sería ruido. */
    @Test
    fun `escribir la misma cifra que Movi no anota ningun ajuste`() {
        montar()
        esperarTexto("Nu")

        escribir(fila = 0, digitos = "352082")
        esperarTexto("Coincide con Movi: no hay nada que anotar.")
        tocar("Anotar el ajuste")
        composeRule.waitForIdle()

        assertTrue(anotados.isEmpty(), "no había diferencia: $anotados")
    }

    // ── Ola D, Task 2: el vacío que enseña ──────────────────────────────────────

    /** Con datos, no hay vacío que enseñar: ya hay algo que cuadrar. */
    @Test
    fun `con cuentas, no aparece el vacio que ensena`() {
        montar()
        esperarTexto("Nu")

        assertTrue(!hay("Todavía no hay nada que cuadrar"))
    }

    /** Mientras la lectura no contestó, ni el vacío ni el «Sin cuentas» de antes: nada se afirma. */
    @Test
    fun `mientras la lectura esta en vuelo, no hay vacio`() {
        val puerta = CompletableDeferred<List<Account>>()
        montar(object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = puerta.await()
        })
        composeRule.waitForIdle()

        assertTrue(!hay("Todavía no hay nada que cuadrar"))
    }

    /**
     * Sin una sola cuenta de Dinero o Inversión, el vacío que enseña — título, detalle y el botón
     * que abre la MISMA hoja de crear cuenta que «Nueva cuenta» en Patrimonio.
     */
    @Test
    fun `sin cuentas, el vacio que ensena con su boton`() {
        montar(object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = emptyList()
        })
        esperarTexto("Todavía no hay nada que cuadrar")

        assertTrue(hay("El cuadre compara el saldo de cada cuenta de Dinero o Inversión"))
        assertTrue(hay("Crear una cuenta"))

        tocar("Crear una cuenta")
        esperarTexto("Falta el nombre")
    }
}

private const val AVD_MOVI_CUADRE = "w411dp-h731dp-xhdpi"
