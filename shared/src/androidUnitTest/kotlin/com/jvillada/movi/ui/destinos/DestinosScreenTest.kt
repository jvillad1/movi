package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # «Cuentas de otros» se ve, se abre y se puede guardar desde la app
 *
 * Es la pantalla que hace cierta la regla del proyecto —nada se configura solo tocando código— así
 * que lo que se prueba es exactamente eso: que la lista muestre lo guardado con su total, que tocar
 * una ficha abra el detalle con sus movimientos, y que el alta llegue al repositorio con el número
 * ya normalizado.
 *
 * Los clics de las hojas van por la **acción semántica** y no por coordenadas: la hoja es más alta
 * que la pantalla de prueba y un toque inyectado sobre un botón fuera de vista cae en el fondo
 * oscuro, que la cierra. Mismo patrón que `PresupuestosNoFallanCalladosTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = AVD_MOVI_DESTINOS)
class DestinosScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val caro = DestinoConocido(
        id = "dst_caro",
        nombre = "Caro",
        numero = "31973270756",
        deQuien = "esposa",
        totales = mapOf("COP" to 4_931_488L),
        cuantos = 3,
    )

    private val cotrafa = FinancialEvent(
        id = "ev-cotrafa",
        accountId = "acc-bancolombia",
        type = TransactionType.EXPENSE,
        amount = 1_931_488L,
        category = "Otros",
        description = "Cuota de Cotrafa 5413 · transferida a Caro",
        timestamp = 1_789_000_000_000L,
    )

    private val laFiducuenta = Account(
        id = "acc-fidu",
        name = "Fiducuenta 9586",
        type = AccountType.SAVINGS,
        balance = 0L,
    )

    private open inner class ConCaro : RepositorioDePrueba() {
        override suspend fun getDestinos(): List<DestinoConocido> = listOf(caro)
        override suspend fun getAccounts(): List<Account> = listOf(laFiducuenta)
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "usr_1", email = "a@b.c", name = "Juan", avatarColor = "#B3C8FF", periodCutoffDay = 25)
        override suspend fun getMovimientosDelDestino(id: String): MovimientosDelDestino =
            MovimientosDelDestino(destino = caro, movimientos = listOf(cotrafa))
    }

    private fun montar(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}) } }
        }
    }

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
    fun `la lista muestra el nombre, la cola del numero, de quien es y el total`() {
        montar(ConCaro())

        esperarTexto("Caro")
        composeRule.onAllNodesWithText("·0756 · esposa", useUnmergedTree = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("3 movimientos", useUnmergedTree = true).onFirst().assertIsDisplayed()
        // El total, con el formato de plata de la app.
        esperarTexto("4.931.488")
    }

    @Test
    fun `tocar una cuenta abre el detalle con sus movimientos y el periodo`() {
        montar(ConCaro())
        esperarTexto("Caro")

        composeRule.onAllNodesWithText("Caro", useUnmergedTree = true).onFirst().performClick()

        esperarTexto("LE HAS ENVIADO")
        esperarTexto("Cuota de Cotrafa 5413")
        esperarTexto("POR PERÍODO")
        // Y deja claro lo que esta pantalla NO es.
        esperarTexto("No es una cuenta tuya")
    }

    @Test
    fun `sin ninguna guardada explica que es esto y ofrece guardar la primera`() {
        montar(object : ConCaro() {
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        })

        esperarTexto("Guardar una cuenta de otra persona")
        esperarTexto("No entran en tu plata")
    }

    @Test
    fun `no se pudo leer avisa y ofrece reintentar, en vez de decir que no hay ninguna`() {
        var intentos = 0
        montar(object : ConCaro() {
            override suspend fun getDestinos(): List<DestinoConocido> {
                intentos++
                throw ApiException(503, "sin señal")
            }
        })

        esperarTexto("No pudimos cargar las cuentas de otros")
        tocar("Reintentar")
        composeRule.waitUntil(timeoutMillis = 5_000) { intentos >= 2 }
    }

    /**
     * El alta llega al repositorio con **solo los dígitos**, aunque se pegue como lo manda el banco.
     * Dos formas del mismo número no pueden verse como dos destinos distintos.
     */
    @Test
    fun `guardar una cuenta nueva manda el numero sin el asterisco`() {
        var mandado: DestinoConocido? = null
        montar(object : ConCaro() {
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun createDestino(destino: DestinoConocido): DestinoConocido {
                mandado = destino
                return destino.copy(id = "dst_nuevo")
            }
        })

        esperarTexto("Guardar una cuenta de otra persona")
        tocar("Guardar una cuenta de otra persona")
        esperarTexto("Nueva cuenta de otro")

        // Los tres campos en orden: nombre, número, de quién.
        val editables = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        editables[0].performTextInput("Caro")
        editables[1].performTextInput("*319-7327-0756")
        editables[2].performTextInput("esposa")
        composeRule.waitForIdle()

        tocar("Guardar cuenta")
        composeRule.waitUntil(timeoutMillis = 5_000) { mandado != null }

        assertEquals("Caro", mandado!!.nombre)
        assertEquals("31973270756", mandado!!.numero, "se guardan solo los dígitos")
        assertEquals("esposa", mandado!!.deQuien)
    }

    /**
     * **La guarda del número propio se ve ANTES de mandar nada.** Si el número que escribe es el de
     * una cuenta suya, la hoja lo dice con el nombre de la cuenta que chocó y el botón no guarda: un
     * destino con un número propio haría que Movi le ponga el nombre de otra persona a sus propios
     * movimientos.
     */
    @Test
    fun `escribir el numero de una cuenta suya lo avisa y no guarda`() {
        var llamadas = 0
        montar(object : ConCaro() {
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun createDestino(destino: DestinoConocido): DestinoConocido {
                llamadas++
                return destino
            }
        })

        esperarTexto("Guardar una cuenta de otra persona")
        tocar("Guardar una cuenta de otra persona")
        esperarTexto("Nueva cuenta de otro")

        val editables = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        editables[0].performTextInput("Yo mismo")
        editables[1].performTextInput("*9586")
        composeRule.waitForIdle()

        esperarTexto("Fiducuenta 9586")
        tocar("Guardar cuenta")
        composeRule.waitForIdle()
        assertEquals(0, llamadas, "con un número propio el botón no puede guardar nada")
    }
}

private const val AVD_MOVI_DESTINOS = "w411dp-h731dp-xhdpi"
