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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UltimoEnvio
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.TipoDeIdentificador
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.formatMoney
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # «Personas y comercios» se ve, se abre y se puede guardar desde la app
 *
 * Es la pantalla que hace cierta la regla del proyecto —nada se configura solo tocando código— así
 * que lo que se prueba es exactamente eso: que la lista muestre lo guardado con lo de este período,
 * que tocar una ficha abra el detalle con sus movimientos, y que el alta llegue al repositorio con
 * un solo campo «número o llave» ya clasificado. Números sintéticos.
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
        numero = "55500000756",
        deQuien = "esposa",
        totales = mapOf("COP" to 4_931_488L),
        cuantos = 3,
        totalesDelPeriodo = mapOf("COP" to 350_000L),
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

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun abrirLaNueva() {
        esperarTexto("Nueva")
        tocar("Nueva")
        esperarTexto(NUEVA_PERSONA_O_COMERCIO)
    }

    @Test
    fun `la lista muestra el nombre, la cola del numero, de quien es y lo de este periodo`() {
        montar(ConCaro())

        esperarTexto("Caro")
        composeRule.onAllNodesWithText("·0756 · esposa", useUnmergedTree = true).onFirst().assertIsDisplayed()
        esperarTexto(formatMoney(350_000L, "COP"))
        assertTrue(hay("este período"))
        // El total histórico vive en la ficha: con tres cifras por tarjeta no se sabía cuál mirar.
        assertTrue(!hay("4.931.488"), "el total histórico no va en la lista")
        assertTrue(hay(PERSONAS_Y_COMERCIOS_TITULO))
    }

    @Test
    fun `sin nada este periodo no inventa un cero`() {
        montar(object : ConCaro() {
            override suspend fun getDestinos(): List<DestinoConocido> = listOf(caro.copy(totalesDelPeriodo = emptyMap()))
        })
        esperarTexto("Nada este período")
        assertTrue(!hay("\$0"))
    }

    @Test
    fun `tocar una ficha abre lo de este periodo y sus movimientos`() {
        montar(ConCaro())
        esperarTexto("Caro")

        composeRule.onAllNodesWithText("Caro", useUnmergedTree = true).onFirst().performClick()

        esperarTexto("ESTE PERÍODO")
        esperarTexto("Le enviaste")
        esperarTexto("Cuota de Cotrafa 5413")
        esperarTexto("Persona · ·0756 · esposa")
    }

    @Test
    fun `sin nadie guardado explica que es esto y ofrece guardar el primero`() {
        montar(object : ConCaro() {
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
        })

        esperarTexto("Guardar una persona o comercio")
        esperarTexto("No suman en tu plata")
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

        esperarTexto("No pudimos cargar tus personas y comercios")
        tocar("Reintentar")
        composeRule.waitUntil(timeoutMillis = 5_000) { intentos >= 2 }
    }

    /**
     * **Un solo campo «número o llave»** (4-oct-2026): pegado como lo manda el banco se guarda como
     * número de cuenta con solo los dígitos, y la hoja dice qué entendió. Sin nota y sin tipo: el
     * tipo lo deduce el server.
     */
    @Test
    fun `guardar uno nuevo pide nombre y un solo campo, y manda el numero sin el asterisco`() {
        var mandado: DestinoConocido? = null
        montar(object : ConCaro() {
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun createDestino(destino: DestinoConocido): DestinoConocido {
                mandado = destino
                return destino.copy(id = "dst_nuevo")
            }
        })

        abrirLaNueva()
        val editables = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        assertEquals(2, editables.fetchSemanticsNodes().size, "nombre y «número o llave», nada más")
        editables[0].performTextInput("Caro")
        editables[1].performTextInput("*555-0000-0756")
        composeRule.waitForIdle()
        esperarTexto("Número de cuenta ·0756")

        tocar("Guardar")
        composeRule.waitUntil(timeoutMillis = 5_000) { mandado != null }

        assertEquals("Caro", mandado!!.nombre)
        assertEquals("55500000756", mandado!!.numero, "se guardan solo los dígitos")
        assertNull(mandado!!.llave)
        assertNull(mandado!!.deQuien)
        assertNull(mandado!!.tipo, "sin tocarlo, el tipo lo deduce el server")
    }

    @Test
    fun `un celular se entiende como llave, y se puede dar vuelta`() {
        var mandado: DestinoConocido? = null
        montar(object : ConCaro() {
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun createDestino(destino: DestinoConocido): DestinoConocido {
                mandado = destino
                return destino.copy(id = "dst_nuevo")
            }
        })

        abrirLaNueva()
        val editables = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        editables[0].performTextInput("Cancha")
        editables[1].performTextInput("300 111 2222")
        composeRule.waitForIdle()
        esperarTexto("Llave 3001112222")
        tocar("Es un número de cuenta")
        esperarTexto("Número de cuenta ·2222")
        tocar("Es una llave")
        esperarTexto("Llave 3001112222")
        tocar("Es un comercio")
        esperarTexto("Es una persona")

        tocar("Guardar")
        composeRule.waitUntil(timeoutMillis = 5_000) { mandado != null }
        assertEquals(listOf(IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "3001112222")), mandado!!.identificadores)
        assertEquals(com.jvillada.movi.shared.model.TipoDeTercero.COMERCIO, mandado!!.tipo, "lo eligió: va")
    }

    /**
     * **La guarda del número propio se ve ANTES de mandar nada**: un número de una cuenta suya
     * haría que Movi le ponga el nombre de otra persona a sus propios movimientos.
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

        abrirLaNueva()
        val editables = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
        editables[0].performTextInput("Yo mismo")
        editables[1].performTextInput("*9586")
        composeRule.waitForIdle()

        esperarTexto("Fiducuenta 9586")
        tocar("Guardar")
        composeRule.waitForIdle()
        assertEquals(0, llamadas, "con un número propio el botón no puede guardar nada")
    }
}

private const val AVD_MOVI_DESTINOS = "w411dp-h731dp-xhdpi"

private const val PERSONAS_Y_COMERCIOS_TITULO = "Personas y comercios"
