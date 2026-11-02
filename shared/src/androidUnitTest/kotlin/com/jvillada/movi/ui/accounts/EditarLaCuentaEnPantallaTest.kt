package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.CacheDeLecturas
import com.jvillada.movi.data.InvalidaElInicioAlEscribir
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.EditarCuentaRequest
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.bancosParaElegir
import com.jvillada.movi.shared.model.BANCOS_CONOCIDOS
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_CAMPO_DE_MONTO
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 98 · «Editar la cuenta», en la pantalla
 *
 * La fila está a la vista en el detalle; la hoja abre llena con lo de hoy; la línea del ajuste aparece
 * solo si el saldo cambia; guardar cambia el nombre en el detalle y vacía lo recordado (la lista lo
 * relee con el nombre nuevo). Una deuda no ofrece ni el tipo ni el saldo. Y los bancos de un toque.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h900dp-xhdpi")
class EditarLaCuentaEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private val ahorros = Account(
        "acc-papa", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000L,
        balancesByCurrency = mapOf("COP" to 1_000_000L),
    )

    /** Un server de mentira que guarda lo editado, como el de verdad. */
    private inner class ServerDelPapa(var cuenta: Account) : RepositorioDePrueba() {
        val pedidos = mutableListOf<EditarCuentaRequest>()
        override suspend fun getAccount(id: String): Account = cuenta
        override suspend fun getAccounts(): List<Account> = listOf(cuenta)
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun getApartados(): List<Apartado> = emptyList()
        override suspend fun editarCuenta(id: String, pedido: EditarCuentaRequest): AdjustAccountBalanceResponse {
            pedidos += pedido
            val saldo = pedido.saldoDeHoy ?: cuenta.balance
            cuenta = cuenta.copy(
                name = pedido.name, type = pedido.type ?: cuenta.type,
                balance = saldo, balancesByCurrency = mapOf("COP" to saldo),
            )
            return AdjustAccountBalanceResponse(cuenta)
        }
    }

    private fun montar(server: ServerDelPapa) {
        // Envuelto como en la app: así se ve que guardar vacía lo recordado.
        Repositories.sustitutoDePrueba = InvalidaElInicioAlEscribir(server)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    AccountDetailScreen(onNavigate = {}, accountId = server.cuenta.id, group = AccountGroup.DINERO)
                }
            }
        }
    }

    private fun esperar(texto: String) = composeRule.waitUntil(5_000) { hay(texto) }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun abrirLaHoja() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_EDITAR_LA_CUENTA).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasTestTag(TAG_EDITAR_LA_CUENTA) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_HOJA_EDITAR_LA_CUENTA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Dentro de la hoja. El panel de `MarcoDeHoja` es tocable y funde a sus hijos en el árbol fundido,
     * así que todo lo de la hoja se busca en el árbol sin fundir.
     */
    private val enLaHoja = hasAnyAncestor(hasTestTag(TAG_HOJA_EDITAR_LA_CUENTA))

    private fun enLaHojaHay(m: androidx.compose.ui.test.SemanticsMatcher): Boolean =
        composeRule.onAllNodes(m and enLaHoja, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun campoDelSaldo() =
        composeRule.onNode(
            hasSetTextAction() and (hasTestTag(TAG_CAMPO_DE_MONTO) or hasAnyAncestor(hasTestTag(TAG_CAMPO_DE_MONTO))) and enLaHoja,
            useUnmergedTree = true,
        )

    @Test
    fun `la fila esta a la vista y la hoja abre llena`() {
        montar(ServerDelPapa(ahorros))
        esperar(EDITAR_LA_CUENTA)
        esperar("Nombre, banco, tipo y saldo")
        abrirLaHoja()

        assertTrue(enLaHojaHay(hasSetTextAction() and hasText("Bancolombia Ahorros")), "el nombre de hoy")
        assertTrue(enLaHojaHay(hasSetTextAction() and hasText("1.000.000", substring = true)), "el saldo de hoy")
        assertFalse(hay("Movi anota un ajuste"), "sin cambiar el saldo no hay línea")
        assertTrue(hay("No has cambiado nada"), "y el botón dice por qué está apagado")
    }

    @Test
    fun `cargar la cuenta elige el banco que nombra y su tipo`() {
        val estado = EstadoDelFormularioDeCuenta().apply { cargar(ahorros, saldoParaEditar(ahorros)) }
        assertEquals("Bancolombia", estado.banco)
        assertEquals(AccountType.SAVINGS, estado.tipo)
        assertEquals(1_000_000L, estado.saldo)
        // Cambiar el banco propone el nombre nuevo, porque el de hoy es el que Movi habría propuesto.
        estado.elegirBanco("Davibank")
        assertEquals("Davibank Ahorros", estado.nombre)
        // Uno escrito a mano no se toca.
        val propio = EstadoDelFormularioDeCuenta().apply { cargar(ahorros.copy(name = "Plata del papá"), null) }
        propio.elegirBanco("Davibank")
        assertEquals("Plata del papá", propio.nombre)
        // Un efectivo en negativo va vacío: vacío es «no lo toques», nunca $0.
        assertNull(saldoParaEditar(Account("e", "Efectivo", AccountType.CASH, -26_500L)))
        assertNull(lineaDelAjusteAlEditar(ahorros, null))
        assertNull(pedidoDeEdicion(ahorros, " Bancolombia Ahorros ", AccountType.SAVINGS, 1_000_000L), "nada cambió")
        // El tipo: solo Ahorros y Corriente se ofrecen; Efectivo e Inversión van fijos, con su porqué.
        assertEquals(listOf(AccountType.SAVINGS, AccountType.CHECKING), TIPOS_AL_EDITAR.map { it.second })
        assertNull(tipoFijoDe(ahorros))
        assertTrue(tipoFijoDe(Account("e", "Efectivo", AccountType.CASH, 0L))!!.startsWith("Efectivo. "))
        assertTrue(tipoFijoDe(Account("i", "Fiducuenta", AccountType.INVESTMENT, 0L))!!.startsWith("Inversión. "))
        // Lo que se dice debajo del nombre: la hermana en dólares que deja de emparejarse.
        val usd = Account("u", "Bancolombia Ahorros USD", AccountType.SAVINGS, 0L, currency = "USD")
        assertEquals(
            "La cuenta en USD «Bancolombia Ahorros USD» deja de emparejarse con esta.",
            ayudaAlRenombrar(ahorros, "Ahorros del papá", listOf(ahorros, usd)),
        )
        assertNull(ayudaAlRenombrar(ahorros, "Ahorros del papá", listOf(ahorros)))
    }

    @Test
    fun `la linea del ajuste aparece solo si el saldo cambia`() {
        montar(ServerDelPapa(ahorros))
        abrirLaHoja()

        campoDelSaldo().performTextClearance()
        campoDelSaldo().performTextInput("1250000")
        composeRule.waitForIdle()
        assertTrue(hay("Movi anota un ajuste de +\$250.000 para que cuadre."), "la línea, con el signo")

        campoDelSaldo().performTextClearance()
        campoDelSaldo().performTextInput("1000000")
        composeRule.waitForIdle()
        assertFalse(hay("Movi anota un ajuste"), "volver a la cifra de hoy la quita")
    }

    @Test
    fun `guardar cambia el nombre en el detalle y en la lista`() {
        val server = ServerDelPapa(ahorros)
        montar(server)
        abrirLaHoja()
        val antes = CacheDeLecturas.generacion

        // Por su rótulo, no por su texto: el texto cambia al escribir.
        val nombre = composeRule.onNode(
            hasSetTextAction() and enLaHoja and androidx.compose.ui.test.hasContentDescription("Nombre"),
            useUnmergedTree = true,
        )
        nombre.performTextClearance()
        nombre.performTextInput("Ahorros del papá")
        composeRule.onNode(hasClickAction() and androidx.compose.ui.test.hasAnyDescendant(hasText("Guardar")) and enLaHoja, useUnmergedTree = true)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(TAG_HOJA_EDITAR_LA_CUENTA, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }

        val pedido = server.pedidos.single()
        assertEquals("Ahorros del papá", pedido.name)
        assertNull(pedido.type, "el tipo no cambió: no viaja")
        assertNull(pedido.saldoDeHoy, "el saldo no cambió: no se anota ningún ajuste")
        esperar("Ahorros del papá")
        assertTrue(CacheDeLecturas.generacion > antes, "lo recordado se vacía: la lista vuelve a leer")
        assertEquals("Ahorros del papá", runBlocking { Repositories.wallets.getAccounts() }.single().name)
    }

    @Test
    fun `una deuda no ofrece cambiar el tipo ni el saldo`() {
        val tarjeta = Account("acc-tc", "Master Black 3684", AccountType.CREDIT_CARD, 300_000L)
        val server = object : RepositorioDePrueba() {
            override suspend fun getAccount(id: String): Account = tarjeta
            override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
            override suspend fun getApartados(): List<Apartado> = emptyList()
            override suspend fun getCards() = emptyList<com.jvillada.movi.shared.model.CardSummary>()
            override suspend fun getCredits() = emptyList<com.jvillada.movi.shared.model.CreditSummary>()
        }
        Repositories.sustitutoDePrueba = server
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { AccountDetailScreen(onNavigate = {}, accountId = "acc-tc", group = AccountGroup.DEUDA) } }
        }
        esperar("Nombre y banco")
        abrirLaHoja()
        assertTrue(enLaHojaHay(hasTestTag(TAG_TIPO_FIJO)), "el tipo, fijo y con su porqué")
        assertFalse(enLaHojaHay(hasTestTag(TAG_TIPOS_DE_CUENTA)), "sin píldoras de tipo")
        assertFalse(enLaHojaHay(hasTestTag(TAG_CAMPO_DE_MONTO)), "sin «Saldo de hoy»")
    }

    @Test
    fun `los bancos de un toque nuevos estan y davi encuentra Davibank y Davivienda`() {
        val conocidos = BANCOS_CONOCIDOS.map { it.id }.toSet()
        listOf("davibank", "occidente", "av_villas", "scotiabank", "banco_popular", "banco_falabella", "lulo", "itau", "rappipay")
            .forEach { assertTrue(it in BANCOS_DE_UN_TOQUE, it); assertTrue(it in conocidos, it) }
        assertEquals(BANCOS_DE_UN_TOQUE.size, BANCOS_DE_UN_TOQUE.toSet().size, "sin repetidos")
        assertEquals(8, BANCOS_A_LA_VISTA)
        val davi = bancosParaElegir("davi").map { it.nombre }
        assertTrue("Davibank" in davi && "Davivienda" in davi, "$davi")
        // Con o sin tildes.
        assertEquals(bancosParaElegir("bogota").map { it.id }, bancosParaElegir("Bogotá").map { it.id })
        assertEquals(listOf("itau"), bancosParaElegir("itau").map { it.id })
        // «Usar lo escrito» cuando nadie se llama exactamente así, aunque la búsqueda encuentre algo.
        assertTrue(ofrecerLoEscrito("Banco Mundo Mujer", bancosParaElegir("Banco Mundo Mujer")))
        assertTrue(ofrecerLoEscrito("banco", bancosParaElegir("banco")))
        assertFalse(ofrecerLoEscrito("davibank", bancosParaElegir("davibank")), "si ya está, se elige de la lista")
        assertFalse(ofrecerLoEscrito("  ", emptyList()))
    }

    @Test
    fun `la cuadricula muestra ocho y Ver todos los bancos abre el catalogo con buscador`() {
        val estado = EstadoDelFormularioDeCuenta()
        composeRule.setContent { MoviTheme { FormularioDeCuenta(estado) } }
        val enLaFila = hasAnyAncestor(hasTestTag(TAG_BANCOS_DE_UN_TOQUE))
        assertTrue(composeRule.onAllNodes(hasText("Davibank") and enLaFila, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "Davibank, de un toque")
        assertTrue(composeRule.onAllNodes(hasText("Lulo Bank") and enLaFila, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "el noveno no va a la vista")
        assertTrue(hay(OTRO_BANCO))

        composeRule.onNode(hasTestTag(TAG_OTRO_BANCO)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay("Busca tu banco"))
        composeRule.onNode(hasTestTag(TAG_BUSCAR_BANCO)).performTextInput("davi")
        composeRule.waitForIdle()
        val enLaLista = hasAnyAncestor(hasTestTag(TAG_LISTA_DE_BANCOS))
        listOf("Davibank", "Davivienda", "Daviplata").forEach { banco ->
            assertTrue(composeRule.onAllNodes(hasText(banco) and enLaLista, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), banco)
        }
        // Lo que no está en el catálogo se escribe tal cual.
        composeRule.onNode(hasTestTag(TAG_BUSCAR_BANCO)).performTextClearance()
        composeRule.onNode(hasTestTag(TAG_BUSCAR_BANCO)).performTextInput("Cootrafa")
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag(TAG_USAR_LO_ESCRITO).fetchSemanticsNodes().isNotEmpty(), "«Usar «Cootrafa»» se ve")
        composeRule.onNode(hasTestTag(TAG_USAR_LO_ESCRITO)).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals("Cootrafa", estado.banco)
        assertEquals("Cootrafa Ahorros", estado.nombre)
    }
}
