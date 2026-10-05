package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.DescartarSugerido
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DestinoSugerido
import com.jvillada.movi.shared.model.DestinosDescartados
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.MotivoDeDescarte
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.OrigenDelNombre
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.RenombradosDelDestino
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.conIdentificadores
import com.jvillada.movi.shared.model.todosLosIdentificadores
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.sms.SMSReconcileScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Cuentas de terceros a la mano, en pantalla (4-oct-2026)
 *
 * Lo que se fija montando las pantallas de verdad: que «Cuentas de otros» muestre lo que Movi
 * encontró solo, que se guarde con un toque y con el nombre prellenado, que «¿Es Caro?» sume el
 * identificador a Caro en vez de crear otra, que la ficha diga todos los identificadores, y que la
 * fila «¿De quién es…?» deje sumarlo a un tercero que ya está guardado. Textos sintéticos.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class TercerosALaManoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc-banco", "Ahorros 9999", AccountType.SAVINGS, 2_000_000L)

    private val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "", deQuien = "esposa")
        .conIdentificadores(
            listOf(
                IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "55500001111"),
                IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "@caro.prueba"),
                IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "carolina prueba salazar"),
            ),
        )

    private val qr = DestinoSugerido(
        identificador = IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "0099887766"),
        nombrePropuesto = "Empanadas",
        origenDelNombre = OrigenDelNombre.TUYO,
        tipo = TipoDeTercero.COMERCIO,
        veces = 3,
        enviado = mapOf("COP" to 69_000L),
        ultimo = 1_790_000_000_000L,
    )

    private val parecidoACaro = DestinoSugerido(
        identificador = IdentificadorDelDestino(TipoDeIdentificador.LLAVE, "carolina otra salazar"),
        nombrePropuesto = "Carolina Otra Salazar",
        origenDelNombre = OrigenDelNombre.BANCO,
        veces = 2,
        recibido = mapOf("COP" to 500_000L),
        ultimo = 1_790_000_000_000L,
        pareceDe = "dst_caro",
        pareceDeNombre = "Caro",
    )

    private val creados = mutableListOf<DestinoConocido>()
    private val agregados = mutableListOf<Pair<String, AgregarIdentificador>>()
    private val descartados = mutableListOf<DescartarSugerido>()
    private val renombrados = mutableListOf<List<String>>()

    private open inner class Repo(
        private val guardados: List<DestinoConocido> = listOf(caro),
        private val sugeridos: List<DestinoSugerido> = listOf(qr, parecidoACaro),
    ) : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 25)
        override suspend fun getDestinos(): List<DestinoConocido> = guardados
        override suspend fun getDestinosSugeridos(): List<DestinoSugerido> = sugeridos
        override suspend fun getDestinosDescartados(): DestinosDescartados = DestinosDescartados()
        override suspend fun createDestino(destino: DestinoConocido): DestinoConocido =
            destino.copy(id = "dst_nuevo").also { creados += it }
        override suspend fun agregarIdentificador(id: String, pedido: AgregarIdentificador): DestinoConocido {
            agregados += id to pedido
            return guardados.first { it.id == id }.let { it.conIdentificadores(it.todosLosIdentificadores() + pedido.identificador) }
        }
        override suspend fun descartarSugerido(pedido: DescartarSugerido) { descartados += pedido }
        override suspend fun getMovimientosDelDestino(id: String): MovimientosDelDestino =
            MovimientosDelDestino(destino = guardados.first { it.id == id }, movimientos = emptyList())
        override suspend fun getRenombrablesDelDestino(id: String): MovimientosParaRenombrar =
            MovimientosParaRenombrar("Transferencia a Caro", "Transferencia de Caro", listOf(ilegible))
        override suspend fun renombrarMovimientosDelDestino(id: String, ids: List<String>): RenombradosDelDestino {
            renombrados += ids
            return RenombradosDelDestino(ids.size, 0)
        }

        // Reconciliar movimiento.
        override suspend fun getSms(id: String): SmsMessage = transferencia
        override suspend fun parseSms(id: String): ParsedSms = leida
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
    }

    private val ilegible = FinancialEvent(
        id = "ev-ilegible", accountId = ahorros.id, type = TransactionType.EXPENSE, amount = 90_000L,
        category = "Otros", description = "Transferencia a la cuenta *55500001111", timestamp = 1_789_000_000_000L,
    )

    private val transferencia = SmsMessage(
        id = "sms-1",
        time = "2026-10-01 10:02",
        bank = "Banco",
        text = "Banco: Transferiste \$80.000 desde tu cuenta *9999 a la cuenta *77700002222 el 01/10/2026 a las 10:02.",
        state = SMS_STATE_PENDING,
        det = "Transferencia · \$80.000",
    )
    private val leida = ParsedSms(
        amount = 80_000.0,
        merchant = "Transferencia a la cuenta *77700002222",
        type = TransactionType.EXPENSE,
        category = "Otros",
        identificadorDelDestino = "77700002222",
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()
    }

    @Test
    fun `lo que Movi encontro va en una linea que se abre, y lo guardado queda a la vista`() {
        montar(Repo())
        esperar(tituloDeLosSugeridos(2))
        // 4-oct-2026: cerrado por defecto con algo guardado — antes tapaba la lista entera.
        assertFalse(hay("Empanadas"), "los sugeridos tapaban lo guardado")
        assertTrue(hay("PERSONAS"), "las guardadas van por sección")
        assertTrue(hay("Caro"))
        tocar("Revisar")
        esperar("Empanadas")
        assertTrue(hay("Comercio · 3 veces"), "dice el tipo y las veces")
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_SUGERIDOS, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    /** La alerta de Hoy lleva acá: con lo que Movi encontró ya abierto, sin otro toque. */
    @Test
    fun `llegar desde la alerta de Hoy abre lo que Movi encontro`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { DestinosScreen(onNavigate = {}, conSugeridosAbiertos = true) } }
        }
        esperar("Empanadas")
        assertTrue(hay("Ocultar"))
    }

    @Test
    fun `guardar un sugerido es un toque, con el nombre prellenado`() {
        montar(Repo(sugeridos = listOf(qr)))
        abrirLosSugeridos()
        esperar("Empanadas")
        tocar("Guardar")
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }
        val creado = creados.single()
        assertEquals("Empanadas", creado.nombre)
        assertEquals(null, creado.tipo, "el tipo lo deduce el server al leer: no se guarda como elegido")
        assertEquals(listOf(qr.identificador), creado.identificadores)
        assertEquals("0099887766", creado.llave, "el APK instalado lee la llave de siempre")
        composeRule.waitForIdle()
        assertFalse(hay(tituloDeLosSugeridos(1)), "guardado, el sugerido se va")
    }

    @Test
    fun `es mia e ignorar se recuerdan`() {
        montar(Repo(sugeridos = listOf(qr)))
        abrirLosSugeridos()
        esperar("Empanadas")
        tocar(ES_MIA)
        composeRule.waitUntil(timeoutMillis = 5_000) { descartados.isNotEmpty() }
        assertEquals(DescartarSugerido(qr.identificador, MotivoDeDescarte.ES_MIA), descartados.single())
    }

    @Test
    fun `es Caro pregunta y, si dice que si, le suma el identificador a Caro sin crear otra`() {
        montar(Repo(sugeridos = listOf(parecidoACaro)))
        abrirLosSugeridos()
        esperar("¿Es Caro?")
        tocar("Sí, es Caro")
        composeRule.waitUntil(timeoutMillis = 5_000) { agregados.isNotEmpty() }
        assertEquals("dst_caro", agregados.single().first)
        assertEquals(parecidoACaro.identificador, agregados.single().second.identificador)
        assertTrue(creados.isEmpty(), "no se crea una segunda Caro")
    }

    @Test
    fun `la ficha dice todos sus identificadores y ofrece ponerle el nombre a los anteriores`() {
        montar(Repo(sugeridos = emptyList()))
        esperar("Caro")
        composeRule.onAllNodesWithText("Caro", useUnmergedTree = true).onFirst().performClick()
        esperar("ESTE PERÍODO")
        // En una línea, debajo del nombre: qué es y cómo lo reconoce Movi.
        assertTrue(hay("Persona · ·1111 · llave @caro.prueba · Carolina Prueba Salazar · esposa"))

        esperar(textoDePonerleElNombre(1))
        tocar("Ponerle el nombre")
        esperar("Los nombres que escribiste tú no se tocan")
        assertTrue(renombrados.isEmpty(), "nada se renombra sin la confirmación")
        tocar("Sí, cambiar 1")
        composeRule.waitUntil(timeoutMillis = 5_000) { renombrados.isNotEmpty() }
        assertEquals(listOf("ev-ilegible"), renombrados.single())
    }

    @Test
    fun `la fila de que es de quien deja sumarlo a un tercero que ya tengo`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = "sms-1") } }
        }
        esperar("¿De quién es la cuenta ·2222?")
        tocar(PONERLE_NOMBRE)
        tocar(YA_LO_TENGO_GUARDADO)
        esperar("Caro")
        composeRule.onAllNodes(
            hasClickAction() and hasAnyChild(hasText("Caro")),
            useUnmergedTree = true,
        )[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { agregados.isNotEmpty() }
        assertEquals("dst_caro", agregados.single().first)
        assertEquals(IdentificadorDelDestino(TipoDeIdentificador.NUMERO, "77700002222"), agregados.single().second.identificador)
        assertTrue(creados.isEmpty())
        composeRule.waitForIdle()
        assertTrue(hay("Transferencia a Caro"), "la propuesta ya dice el nombre del tercero")
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────

    private fun abrirLosSugeridos() {
        esperar("sin nombre")
        tocar("Revisar")
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto) }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onAllNodes(hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
