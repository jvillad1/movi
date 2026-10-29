package com.jvillada.movi.ui.destinos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.sumarIdentificador
import com.jvillada.movi.shared.model.todosLosIdentificadores
import com.jvillada.movi.ui.LocalNavigate
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.porrevisar.PorRevisarScreen
import com.jvillada.movi.ui.sms.SMSReconcileScreen
import com.jvillada.movi.ui.transactions.HojaDelMovimiento
import com.jvillada.movi.ui.transactions.AccionDelMovimiento
import com.jvillada.movi.ui.transactions.tagDeAccionDelMovimiento
import com.jvillada.movi.ui.transactions.TAG_PREGUNTA_DEL_TERCERO
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.numeros
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Una cuenta de otro se guarda desde donde aparece
 *
 * El pedido del dueño (29-sep): *«gestionar cuentas conocidas no propias es un feature que necesito
 * y debe ser de fácil acceso»*. Hasta acá, cuando el banco avisaba «Transferiste a la cuenta
 * *55500000756», la única forma de ponerle nombre era salir a Ajustes y copiar el número a mano.
 *
 * Lo que se fija, montando las pantallas de verdad:
 *
 * - en **Reconciliar movimiento**, con un `*NNNN` que nadie conoce aparece la fila; guardar crea el
 *   destino con el número del mensaje y la propuesta pasa a «Transferencia a <nombre>»;
 * - con un destino que ya conoce ese número, **la fila no aparece** (ni se inventa una segunda);
 * - el nombre de quien mandó plata llega prellenado en Título Caso, no en mayúsculas, y se guarda
 *   como llave;
 * - la tarjeta de **Por revisar** y el **detalle de un movimiento** ofrecen la misma fila.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// `sdk = [34]` y alta: mismo motivo que `ReconciliarSinDuplicarTest` (el texto con `lineHeight` mide
// cero en el SDK por defecto, y la columna perezosa tiene que componer todo).
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xhdpi")
class GuardarDesdeDondeApareceTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc-banco", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 2_000_000L)

    /**
     * El día de los avisos: ayer, no una fecha fija. Con el 29-sep fijo, desde el 29-oct los avisos
     * pasaban de 30 días y Por revisar los plegaba en «Más antiguos» (vuelta 51): las tres pruebas
     * de Por revisar dejaron de encontrar la tarjeta. El texto del SMS puede seguir diciendo otra fecha.
     */
    private val ayer = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota")).minusDays(1).toString()

    private val transferencia = SmsMessage(
        id = "sms-1",
        time = "$ayer 10:02",
        bank = "Bancolombia",
        text = "Bancolombia: Transferiste \$350.000 desde tu cuenta *8133 a la cuenta *55500000756 el 29/09/2026 a las 10:02.",
        state = SMS_STATE_PENDING,
        det = "Transferencia · \$350.000",
    )
    private val leida = ParsedSms(
        amount = 350_000.0,
        merchant = "Transferencia",
        type = TransactionType.EXPENSE,
        category = "Otros",
        identificadorDelDestino = "55500000756",
    )

    private val llego = SmsMessage(
        id = "sms-2",
        time = "$ayer 11:30",
        bank = "Notificación · Nu",
        text = "Te llegó dinero de ANA PRUEBA SALAZAR con tu llave. Recibiste \$120.000.",
        state = SMS_STATE_PENDING,
        det = "ANA PRUEBA SALAZAR · \$120.000",
    )
    private val llegoLeida = ParsedSms(
        amount = 120_000.0,
        merchant = "ANA PRUEBA SALAZAR",
        type = TransactionType.INCOME,
        category = "Otros ingresos",
        identificadorDelDestino = "ana prueba salazar",
        identificadorEsLlave = true,
    )

    private val caro = DestinoConocido(id = "dst_caro", nombre = "Caro", numero = "55500000756", deQuien = "esposa")

    private val creados = mutableListOf<DestinoConocido>()
    private val actualizados = mutableListOf<DestinoConocido>()
    private val publicados = mutableListOf<FinancialEvent>()

    private open inner class Repo(
        private val sms: SmsMessage = transferencia,
        private val parseo: ParsedSms = leida,
        private val guardados: List<DestinoConocido> = emptyList(),
    ) : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getSms(id: String): SmsMessage = sms
        override suspend fun parseSms(id: String): ParsedSms = parseo
        override suspend fun getSmsCoincidencias(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
        override suspend fun confirmSms(id: String) {}
        override suspend fun getDestinos(): List<DestinoConocido> = guardados
        override suspend fun createDestino(destino: DestinoConocido): DestinoConocido =
            destino.copy(id = "dst_nuevo").also { creados += it }
        override suspend fun updateDestino(id: String, destino: DestinoConocido): DestinoConocido =
            destino.also { actualizados += it }
        override suspend fun agregarIdentificador(id: String, pedido: AgregarIdentificador): DestinoConocido =
            guardados.first { it.id == id }.let { sumarIdentificador(it, pedido.identificador) }.also { actualizados += it }

        // Por revisar y la hoja del movimiento.
        override suspend fun getSmsMessages(): List<SmsMessage> = listOf(sms)
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun getParecidos(id: String): List<FinancialEvent> = emptyList()
        override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = null
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    // ── Reconciliar movimiento ──────────────────────────────────────────────────

    @Test
    fun `con un numero desconocido aparece la fila, guardar crea el destino y la propuesta cambia`() {
        reconciliar(Repo())

        esperar("¿De quién es la cuenta ·0756?")
        // Sin nombre en el aviso: «Ponerle nombre», el nombre y «Guardar» — dos toques.
        tocar(PONERLE_NOMBRE)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextReplacement("Caro")
        composeRule.waitForIdle()
        tocar(GUARDAR)
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }
        composeRule.waitForIdle()

        val creado = creados.single()
        assertEquals("Caro", creado.nombre)
        assertEquals("55500000756", creado.numero, "el número es el del mensaje, sin que él lo escriba")
        assertNull(creado.deQuien, "la nota no se pide al guardar desde el aviso")
        assertNull(creado.tipo, "el tipo lo deduce el server: no se pregunta")
        assertNull(creado.llave)

        // La propuesta cambió, y la fila se fue: ya es una cuenta conocida.
        assertTrue(hay("Transferencia a Caro"), "la propuesta no tomó el nombre del destino")
        assertFalse(hayLaFila(), "la fila sigue ofreciendo guardar una cuenta que ya se guardó")

        tocar("Confirmar")
        assertEquals("Transferencia a Caro", publicados.single().description)
        // El texto del banco viaja con el movimiento: es lo que lo engancha a «Cuentas de otros».
        assertEquals(transferencia.text, publicados.single().rawPayload)
    }

    /**
     * **Un solo campo, y nada más** (4-oct-2026): abierta, la fila suma solo el nombre. La nota y
     * Persona/Comercio salieron: el tipo lo deduce Movi y la nota se pone en la ficha si hace falta.
     */
    @Test
    fun `la fila abierta pide un solo campo, sin nota ni persona o comercio`() {
        reconciliar(Repo())

        esperar("¿De quién es la cuenta ·0756?")
        val antes = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size
        // «Comercio» ya está en Reconciliar (la fila del comercio que se edita): se cuenta antes y después.
        val comerciosAntes = composeRule.onAllNodesWithText("Comercio", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size
        tocar(PONERLE_NOMBRE)
        val despues = composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(1, despues - antes, "la fila abierta debe sumar un solo campo, el nombre")
        assertFalse(hay(AGREGAR_UNA_NOTA), "la nota volvió a la fila")
        assertEquals(
            comerciosAntes,
            composeRule.onAllNodesWithText("Comercio", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size,
            "Persona/Comercio volvió a la fila",
        )
        assertTrue(hay(YA_LO_TENGO_GUARDADO).not(), "sin nadie guardado no se ofrece elegir")
        assertTrue(hay(ES_MIA))
    }

    @Test
    fun `con un destino que ya conoce ese numero la fila no aparece`() {
        reconciliar(Repo(guardados = listOf(caro)))

        esperar("Confirma o ajusta", "Confirma o ajusta")
        assertFalse(hayLaFila())
        assertFalse(hay("¿De quién es"))
    }

    @Test
    fun `el numero de una cuenta suya no se ofrece como cuenta de otro`() {
        reconciliar(Repo(parseo = leida.copy(identificadorDelDestino = "8133")))

        esperar("Confirma o ajusta", "Confirma o ajusta")
        assertFalse(hayLaFila(), "ofreció guardar como ajena la cuenta ·8133, que es suya")
    }

    @Test
    fun `quien mando plata llega prellenado en Titulo Caso y se guarda como llave`() {
        reconciliar(Repo(sms = llego, parseo = llegoLeida))

        // Con el nombre, la fila cerrada ya es la pregunta y el botón: UN toque.
        esperar("¿Guardar a «Ana Prueba Salazar»?")
        composeRule.onNodeWithTag(TAG_GUARDAR_DE_UN_TOQUE, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }
        composeRule.waitForIdle()

        val creado = creados.single()
        assertEquals("Ana Prueba Salazar", creado.nombre)
        assertEquals("", creado.numero, "un destino conocido solo por su llave va sin número")
        assertEquals("ana prueba salazar", creado.llave)
        assertTrue(hay("Transferencia de Ana Prueba Salazar"))
    }

    @Test
    fun `la llave de alguien que ya esta guardada con su numero se le suma, no crea otra`() {
        reconciliar(
            Repo(
                parseo = leida.copy(identificadorDelDestino = "0092184713", identificadorEsLlave = true),
                guardados = listOf(caro),
            ),
        )

        esperar("¿De quién es la llave 0092184713?")
        tocar(PONERLE_NOMBRE)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextReplacement("caro")
        composeRule.waitForIdle()
        assertTrue(hay("Se agrega a «Caro», que ya tienes guardada."))
        tocar(GUARDAR)
        composeRule.waitUntil(timeoutMillis = 5_000) { actualizados.isNotEmpty() }

        assertTrue(creados.isEmpty(), "creó una segunda «Caro» en vez de sumarle la llave")
        val actualizado = actualizados.single()
        assertEquals("dst_caro", actualizado.id)
        assertEquals("55500000756", actualizado.numero)
        assertEquals("0092184713", actualizado.llave)
    }

    // ── Por revisar ─────────────────────────────────────────────────────────────

    @Test
    fun `la tarjeta de Por revisar ofrece guardar la cuenta, y con una conocida no`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } }
        }
        esperar("¿De quién es la cuenta ·0756?")
        tocar(PONERLE_NOMBRE)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextReplacement("Caro")
        composeRule.waitForIdle()
        tocar(GUARDAR)
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }
        composeRule.waitForIdle()

        assertEquals("55500000756", creados.single().numero)
        assertFalse(hayLaFila())
    }

    /**
     * **Por revisar prellena el nombre que trae el banco** (4-oct-2026): hasta esta fecha la tarjeta
     * pasaba `nombreDelBanco = null` y el campo quedaba vacío aunque el aviso dijera «… a MARTA
     * PRUEBA RUIZ». Ahora es un toque, y se guarda también ese nombre para reconocerla después.
     */
    @Test
    fun `en Por revisar un Bre-B con el nombre se guarda de un toque, con la llave y el nombre`() {
        val breB = transferencia.copy(
            text = "Bancolombia: JUAN, transferiste \$37,221.00 a la llave @marta.prueba desde tu cuenta *8133 a MARTA PRUEBA RUIZ el 04/10/26 a las 10:02.",
        )
        Repositories.sustitutoDePrueba = Repo(sms = breB)
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } }
        }
        esperar("¿Guardar a «Marta Prueba Ruiz»?")
        composeRule.onNodeWithTag(TAG_GUARDAR_DE_UN_TOQUE, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }

        val creado = creados.single()
        assertEquals("Marta Prueba Ruiz", creado.nombre)
        assertEquals(
            listOf("LLAVE:@marta.prueba", "LLAVE:marta prueba ruiz"),
            creado.todosLosIdentificadores().map { "${it.tipo}:${it.valor}" },
        )
    }

    @Test
    fun `en Por revisar una cuenta conocida no ofrece nada`() {
        Repositories.sustitutoDePrueba = Repo(guardados = listOf(caro))
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } }
        }
        esperar("Revisar")
        composeRule.waitForIdle()
        assertFalse(hayLaFila())
    }

    // ── El detalle de un movimiento ─────────────────────────────────────────────

    private val yaAnotado = FinancialEvent(
        id = "ev-1",
        accountId = ahorros.id,
        type = TransactionType.EXPENSE,
        amount = 350_000L,
        category = "Otros",
        description = "Mercado",
        merchant = "Transferencia",
        source = EventSource.SMS,
        rawPayload = transferencia.text,
        timestamp = 1_790_000_000_000L,
    )

    @Test
    fun `el detalle de un movimiento con un numero desconocido ofrece la misma fila`() {
        hoja(Repo(), yaAnotado)

        // Ola 2b: la pregunta está a la vista al abrir, en una línea —es una pregunta pendiente,
        // como «Por confirmar»—, y tocarla abre la sección «Tercero» con la fila de siempre.
        esperar("¿De quién es la cuenta ·0756?")
        composeRule.onNodeWithTag(TAG_PREGUNTA_DEL_TERCERO, useUnmergedTree = true).assertIsDisplayed()
        assertFalse(hayLaFila(), "la fila completa se abre desde la pregunta, no sola")
        composeRule.onNodeWithTag(TAG_PREGUNTA_DEL_TERCERO, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hayLaFila(), "tocar la pregunta no abrió la sección del tercero")
        assertFalse(hayTag(TAG_PREGUNTA_DEL_TERCERO), "con la sección abierta, la pregunta no se repite")
        tocar(PONERLE_NOMBRE)
        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performTextReplacement("Caro")
        composeRule.waitForIdle()
        tocar(GUARDAR)
        composeRule.waitUntil(timeoutMillis = 5_000) { creados.isNotEmpty() }
        composeRule.waitForIdle()

        assertEquals("55500000756", creados.single().numero)
        assertTrue(hay("«Caro» quedó en Personas y comercios"))
        assertFalse(hayLaFila())
    }

    /**
     * **Asociarlo a alguien que ya existe, desde el movimiento** (20-oct-2026): «Ya lo tengo» abre la
     * lista sin pasar por «Otro…», elegir a Papá le suma el número y la hoja ya muestra el nombre que
     * el server le puso al movimiento con la misma regla.
     */
    @Test
    fun `desde el movimiento se elige un tercero que ya existe y el nombre cambia en el acto`() {
        val papa = DestinoConocido(id = "dst_papa", nombre = "Papá", numero = "77700001234", tipo = TipoDeTercero.PERSONA)
        hoja(Repo(guardados = listOf(papa)), yaAnotado.copy(description = "Transferencia a la cuenta *55500000756"))
        abrirTercero()

        esperar("¿De quién es la cuenta ·0756?")
        assertFalse(hay("Papá"), "la lista no se abre sola")
        composeRule.onNodeWithTag(TAG_ES_DE_ALGUIEN_GUARDADO, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        esperar(ELIGE_A_QUIEN)
        tocar("Papá")
        composeRule.waitUntil(timeoutMillis = 5_000) { actualizados.isNotEmpty() }
        composeRule.waitForIdle()

        assertEquals("dst_papa", actualizados.single().id)
        assertTrue(actualizados.single().numeros().contains("55500000756"), "se le sumó el número del aviso")
        assertTrue(creados.isEmpty(), "no se creó otro tercero")
        esperar("Transferencia a Papá")
        assertTrue(hay("«Papá» quedó en Personas y comercios"))
    }

    /**
     * **«Ver su ficha» lleva a Personas y comercios con la ficha abierta** (4-oct-2026). Antes la
     * abría como una hoja adentro de la hoja del movimiento, y en el teléfono salía vacía.
     */
    @Test
    fun `ver su ficha desde un movimiento navega a Personas y comercios con esa ficha`() {
        var navegoA: Screen? = null
        Repositories.sustitutoDePrueba = Repo(guardados = listOf(caro))
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalNavigate provides { navegoA = it }) {
                    Box(Modifier.fillMaxSize()) {
                        HojaDelMovimiento(event = yaAnotado, cuentas = listOf(ahorros), onDismiss = {}, onCambiado = {})
                    }
                }
            }
        }
        abrirTercero()
        esperar("Es de «Caro»")
        tocar(VER_SU_FICHA)
        assertEquals(Screen.Destinos(abrir = "dst_caro"), navegoA)
    }

    @Test
    fun `el detalle de un movimiento a una cuenta conocida no ofrece nada`() {
        hoja(Repo(guardados = listOf(caro)), yaAnotado)

        esperar("Mercado")
        composeRule.waitForIdle()
        assertFalse(hayLaFila())
        // Ola 2b: con el tercero ya conocido no hay pregunta al abrir; «Tercero» sigue en la fila
        // de acciones y lleva a «Es de «Caro»».
        assertFalse(hayTag(TAG_PREGUNTA_DEL_TERCERO), "preguntó de quién es una cuenta ya guardada")
        assertFalse(hay("¿De quién es"))
        abrirTercero()
        esperar("Es de «Caro»")
    }

    @Test
    fun `un movimiento que no nombra ninguna cuenta no lee los destinos`() {
        var lecturas = 0
        hoja(
            object : Repo() {
                override suspend fun getDestinos(): List<DestinoConocido> = emptyList<DestinoConocido>().also { lecturas++ }
            },
            yaAnotado.copy(rawPayload = null, merchant = "TOSTAO CAFE Y PAN"),
        )

        esperar("Mercado")
        composeRule.waitForIdle()
        assertEquals(0, lecturas)
        assertFalse(hayLaFila())
    }

    // ── Andamio ─────────────────────────────────────────────────────────────────

    private fun reconciliar(repo: RepositorioDePrueba) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { SMSReconcileScreen(onNavigate = {}, smsId = "sms") } }
        }
        composeRule.waitForIdle()
    }

    private fun hoja(repo: RepositorioDePrueba, evento: FinancialEvent) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDelMovimiento(event = evento, cuentas = listOf(ahorros), onDismiss = {}, onCambiado = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Ola 2b: en el detalle de un movimiento, lo del tercero se abre desde su acción («Tercero»),
     * que aparece cuando el movimiento nombra una cuenta y ya se leyeron las guardadas.
     */
    private fun abrirTercero() {
        val accion = tagDeAccionDelMovimiento(AccionDelMovimiento.Tercero)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(accion, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(accion, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun hayTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun hayCampoCon(texto: String): Boolean =
        composeRule.onAllNodes(hasSetTextAction() and hasText(texto), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun hayLaFila(): Boolean =
        composeRule.onAllNodesWithTag(TAG_GUARDAR_EL_DESTINO, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    /** Espera a que aparezca cualquiera de [textos] (un encabezado puede ir en versales). */
    private fun esperar(vararg textos: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { textos.any { hay(it) } }
        composeRule.waitForIdle()
    }

    /** Por la acción semántica: bajo Robolectric un toque por coordenadas no llega a una hoja alta. */
    private fun tocar(texto: String) {
        val nodos = composeRule.onAllNodes(
            hasClickAction() and (hasText(texto) or hasAnyChild(hasText(texto))),
            useUnmergedTree = true,
        )
        nodos[0].performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }
}
