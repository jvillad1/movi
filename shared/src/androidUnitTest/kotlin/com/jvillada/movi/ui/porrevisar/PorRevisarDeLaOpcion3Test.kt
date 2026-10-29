package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.components.ds.TAG_ICONO_EN_CIRCULO
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDeIconoKey
import com.jvillada.movi.ui.transactions.SIN_CONFIRMAR
import com.jvillada.movi.ui.transactions.TAG_SIN_CONFIRMAR
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 42: «Por revisar» con el lenguaje de la opción 3
 *
 * - **El punto marrón** delante de la cifra de «Entraron solos» («• +$30.000») no se entendía: marca
 *   «sin confirmar», así que ahora es una píldora suave que lo dice.
 * - **«Subir comprobante o extracto»** es una fila de navegación: el círculo neutro, no el amarillo.
 * - **A 1.280**, la bandeja ya no es una columna angosta: «Mensajes del banco» a la izquierda y
 *   «Entraron solos» con lo demás a la derecha, con el mismo umbral que Patrimonio y una cuenta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PorRevisarDeLaOpcion3Test {

    @get:Rule val composeRule = createComposeRule()

    private val banco = Account("acc-banco", "Bancolombia Ahorros", AccountType.SAVINGS, 853_037L, "COP")

    private val devolucion = FinancialEvent(
        id = "e-solo", accountId = banco.id, type = TransactionType.INCOME, amount = 30_000L, category = "Comida",
        description = "Devolucion almuerzo", timestamp = 1_757_000_000_000L, source = EventSource.SMS,
        reconciliationStatus = ReconciliationStatus.UNCONFIRMED, countsAsCashFlow = true,
    )

    private val aviso = SmsMessage(
        id = "s1", time = "${com.jvillada.movi.ui.fecha.hoyEnAppZone().let { kotlinx.datetime.LocalDate.fromEpochDays(it.toEpochDays() - 10) }} 11:02", bank = "Bancolombia",
        text = "Compra aprobada por \$42.500 en RAPPI el 02/09/2026.", state = SMS_STATE_PENDING, det = "Rappi",
    )

    private inner class Repo(private val mensajes: List<SmsMessage>) : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = mensajes
        override suspend fun getEventsByDay(): List<EventDay> =
            listOf(EventDay(date = "2025-09-08", total = 0L, items = listOf(devolucion)))
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(banco)
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
    }

    @After fun limpiar() { Repositories.sustitutoDePrueba = null }

    private fun esperar(texto: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun montarSola(mensajes: List<SmsMessage> = emptyList()) {
        Repositories.sustitutoDePrueba = Repo(mensajes)
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } } }
        esperar("Devolucion almuerzo")
    }

    private fun montarEnLaCascara(mensajes: List<SmsMessage>) {
        Repositories.sustitutoDePrueba = Repo(mensajes)
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.PorRevisar,
                    activeTab = NavTab.MOVIMIENTOS,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) {
                    PorRevisarScreen(onNavigate = {})
                }
            }
        }
        esperar("Devolucion almuerzo")
    }

    private fun hayTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun limites(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun limitesDelTexto(texto: String): Rect =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

    @Test
    @Config(qualifiers = "w390dp-h1200dp-xhdpi")
    fun `lo que entro solo dice Sin confirmar en una pildora, sin el punto marron`() {
        montarSola()

        composeRule.onNode(hasText(SIN_CONFIRMAR) and hasAnyAncestor(hasTestTag(TAG_SIN_CONFIRMAR)), useUnmergedTree = true)
            .assertExists()
        assertTrue(
            composeRule.onAllNodesWithContentDescription("Por confirmar", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            "el punto de «por confirmar» volvió",
        )
        // La píldora va con el monto: debajo de la cifra, a la derecha de la fila.
        val pildora = limites(TAG_SIN_CONFIRMAR)
        val cifra = limitesDelTexto("+\$30.000")
        assertTrue(pildora.top >= cifra.bottom - 0.5f, "debajo de la cifra: $pildora / $cifra")
    }

    @Test
    @Config(qualifiers = "w390dp-h1200dp-xhdpi")
    fun `subir comprobante es una fila de navegacion con el circulo neutro`() {
        montarSola()

        val tono = composeRule.onAllNodes(
            hasTestTag(TAG_ICONO_EN_CIRCULO) and hasAnyAncestor(hasTestTag(TAG_SUBIR_PAPEL_EN_LA_BANDEJA)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single().config[TonoDeIconoKey]
        assertEquals(TonoDeIcono.Neutro, tono)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 los mensajes del banco van a la izquierda y entraron solos a la derecha`() {
        montarEnLaCascara(listOf(aviso))

        val mensajes = limites(TAG_COLUMNA_DE_LOS_MENSAJES)
        val demas = limites(TAG_COLUMNA_DE_LO_DEMAS)
        assertTrue(demas.left >= mensajes.right - 0.5f, "$mensajes / $demas")
        assertEquals(mensajes.width, demas.width, 0.5f)
        assertEquals(1_064f, mensajes.width + demas.width, 0.5f, "llenan el panel (1280 − 216 del rail)")
        val tituloMensajes = limitesDelTexto("Mensajes del banco")
        assertTrue(tituloMensajes.right <= mensajes.right + 0.5f, "«Mensajes del banco» a la izquierda: $tituloMensajes")
        val entraron = limitesDelTexto("Entraron solos")
        assertTrue(entraron.left >= demas.left - 0.5f, "«Entraron solos» a la derecha: $entraron")
        // Las dos columnas arrancan arriba: «Entraron solos» no espera a que terminen los mensajes.
        assertTrue(entraron.top < 400f, "«Entraron solos» arranca arriba: $entraron")
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 sin mensajes del banco la bandeja va en una columna`() {
        montarEnLaCascara(emptyList())
        assertTrue(!hayTag(TAG_COLUMNA_DE_LOS_MENSAJES) && !hayTag(TAG_COLUMNA_DE_LO_DEMAS))
    }

    @Test
    @Config(qualifiers = "w390dp-h1200dp-xhdpi")
    fun `a 390 una sola columna, en el orden de siempre`() {
        Repositories.sustitutoDePrueba = Repo(listOf(aviso))
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { PorRevisarScreen(onNavigate = {}) } } }
        esperar("Entraron solos")
        assertTrue(!hayTag(TAG_COLUMNA_DE_LOS_MENSAJES) && !hayTag(TAG_COLUMNA_DE_LO_DEMAS))
        assertTrue(limitesDelTexto("Mensajes del banco").top < limitesDelTexto("Entraron solos").top)
    }
}
