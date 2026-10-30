package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.AnfitrionDelAvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.AvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.LocalAvisoDeDeshacer
import com.jvillada.movi.ui.components.ds.TAG_DESHACER_DEL_AVISO
import com.jvillada.movi.ui.sms.TAG_ELEGIR_EL_ANOTADO_EN_LA_FILA
import com.jvillada.movi.ui.sms.TAG_ES_ESTE_EN_LA_FILA
import com.jvillada.movi.ui.sms.TAG_LINEA_DEL_YA_ANOTADO
import com.jvillada.movi.ui.Screen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 74 · el aviso que ya está anotado se cierra con «Es este», desde la fila
 *
 * El aviso de Crepes ($32.000) llega cuando el dueño ya anotó «Restaurantes $32.000». La tarjeta lo
 * dice y su botón amarillo es «Es este»: un toque, sin abrir nada, que **vincula** (`confirmSmsCon`
 * con el que ya estaba) y nunca crea un movimiento. Con dos candidatos, «Elegir». Y al tocarlo la
 * bandeja no pasa a esqueleto ni se mueve: la tarjeta sale y lo demás queda donde estaba.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h760dp-xhdpi")
class EsEsteEnLaFilaTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahorros = Account("acc-4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 5_000_000L, "COP")

    private fun aviso(id: String, comercio: String, monto: String, hora: String) = SmsMessage(
        id = id, time = "2026-10-29 $hora", bank = "85540",
        text = "Bancolombia: Compraste \$$monto,00 en $comercio con tu T.Deb *4057, el 29/10/2026 a las $hora.",
        state = SMS_STATE_PENDING, det = "",
    )

    private val crepes = aviso("crepes", "CREPES Y WAFFLES OVIEDO", "32.000", "13:05")
    private val restaurantes = FinancialEvent(
        id = "ev-restaurantes", accountId = ahorros.id, type = TransactionType.EXPENSE, amount = 32_000L,
        category = "Restaurantes", description = "Restaurantes", timestamp = 1_793_300_000_000L,
    )
    private val otroIgual = restaurantes.copy(id = "ev-otro", description = "Almuerzo")

    /** Avisos de relleno: más nuevos que Crepes (hay que bajar hasta él) y más viejos (quedan debajo). */
    private val relleno = (1..6).map { i -> aviso("r$i", "COMERCIO $i", "${i}1.000", "${13 + i}:00") } +
        (1..3).map { i -> aviso("v$i", "VIEJO $i", "${i}2.000", "${13 - i}:00") }

    private fun comercioDe(sms: SmsMessage) = sms.text.substringAfter(" en ").substringBefore(" con tu")

    private val confirmados = mutableListOf<Pair<String, String>>()
    private val desvinculados = mutableListOf<List<String>>()
    private val publicados = mutableListOf<FinancialEvent>()
    private var fallaElConfirmar = false

    private inner class Repo(private val coincidencias: List<FinancialEvent>) : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = relleno + crepes
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja = PropuestasDeLaBandeja(
            propuestas = relleno.map { PropuestaDelAviso(it.id, leido = ParsedSms(11_000.0, comercioDe(it), TransactionType.EXPENSE, "Otros")) } +
                PropuestaDelAviso("crepes", leido = ParsedSms(32_000.0, "CREPES Y WAFFLES OVIEDO", TransactionType.EXPENSE, "Comida"), coincidencias = coincidencias),
            categoriasDelDueno = listOf("Comida", "Restaurantes"),
        )
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
        override suspend fun confirmSmsCon(id: String, eventoId: String) {
            if (fallaElConfirmar) throw IllegalStateException("Sin conexión")
            confirmados += id to eventoId
        }
        override suspend fun desvincularAvisos(avisos: List<String>) {
            desvinculados += avisos
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val aviso = AvisoDeDeshacer()
    private var abierto: Screen? = null

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperar(condicion: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condicion() }
        composeRule.waitForIdle()
    }

    private fun montar(coincidencias: List<FinancialEvent>) {
        Repositories.sustitutoDePrueba = Repo(coincidencias)
        composeRule.setContent {
            MoviTheme {
                CompositionLocalProvider(LocalAvisoDeDeshacer provides aviso) {
                    Box(Modifier.fillMaxSize()) {
                        PorRevisarScreen(onNavigate = { abierto = it })
                        AnfitrionDelAvisoDeDeshacer(aviso, Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
        // Crepes está más abajo (la lista es perezosa): se espera a que la bandeja tenga sus tarjetas.
        esperar { composeRule.onAllNodesWithText("Comercio 6", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun bajarHastaCrepes() {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Ya anotado:", substring = true))
        composeRule.waitForIdle()
    }

    @Test
    fun `la tarjeta dice lo que ya estaba y su boton principal es Es este`() {
        montar(listOf(restaurantes))
        bajarHastaCrepes()

        composeRule.onNodeWithTag(TAG_LINEA_DEL_YA_ANOTADO, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Ya anotado: Restaurantes \$32.000 · ", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(1, conTag(TAG_ES_ESTE_EN_LA_FILA))
        // «Revisar» queda, como secundario, y no hay «Confirmar» (crearía otro).
        assertTrue(composeRule.onAllNodesWithText("Revisar").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, conTag(com.jvillada.movi.ui.sms.TAG_CONFIRMAR_EN_LA_FILA))
    }

    @Test
    fun `con dos anotados no hay un toque - Elegir abre Revisar aviso`() {
        montar(listOf(restaurantes, otroIgual))
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Puede ser uno de 2 anotados"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Puede ser uno de 2 anotados", useUnmergedTree = true).assertExists()
        assertEquals(0, conTag(TAG_ES_ESTE_EN_LA_FILA))
        composeRule.onNodeWithTag(TAG_ELEGIR_EL_ANOTADO_EN_LA_FILA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(Screen.SMSReconcile("crepes"), abierto)
    }

    @Test
    fun `Es este vincula sin crear nada, sin esqueleto y sin mover la lista`() {
        montar(listOf(restaurantes))
        bajarHastaCrepes()
        // La tarjeta de arriba de Crepes (el último del relleno): su lugar no puede cambiar.
        val antes = posicionDe("Comercio 1")

        composeRule.onNodeWithTag(TAG_ES_ESTE_EN_LA_FILA).performSemanticsAction(SemanticsActions.OnClick)
        // Ni un cuadro de esqueleto: la lista sigue a la vista mientras se guarda y se relee.
        assertEquals(0, conTag(TAG_ESQUELETO_DE_POR_REVISAR))
        esperar { confirmados.isNotEmpty() }

        assertEquals(listOf("crepes" to restaurantes.id), confirmados, "vincula con el que ya estaba")
        assertTrue(publicados.isEmpty(), "«Es este» nunca crea un movimiento")
        assertEquals(0, conTag(TAG_ESQUELETO_DE_POR_REVISAR))
        esperar { conTag(TAG_ES_ESTE_EN_LA_FILA) == 0 }
        assertEquals(antes, posicionDe("Comercio 1"), "la lista quedó donde estaba")
        // Abajo: «Anotado · Restaurantes $32.000», con «Deshacer».
        assertEquals("Anotado · Restaurantes \$32.000", aviso.actual?.texto)

        composeRule.onNodeWithTag(TAG_DESHACER_DEL_AVISO).performSemanticsAction(SemanticsActions.OnClick)
        esperar { desvinculados.isNotEmpty() }
        assertEquals(listOf(listOf("crepes")), desvinculados, "deshacer desvincula el aviso")
        assertTrue(publicados.isEmpty())
    }

    @Test
    fun `si el server falla, la tarjeta vuelve y se dice`() {
        fallaElConfirmar = true
        montar(listOf(restaurantes))
        bajarHastaCrepes()

        composeRule.onNodeWithTag(TAG_ES_ESTE_EN_LA_FILA).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        esperar { conTag(TAG_ES_ESTE_EN_LA_FILA) == 1 }
        assertTrue(confirmados.isEmpty())
        assertEquals(null, aviso.actual, "no se dice «Anotado» de algo que no se guardó")
    }

    private fun posicionDe(texto: String): Rect =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes()
            .first().boundsInRoot
}
