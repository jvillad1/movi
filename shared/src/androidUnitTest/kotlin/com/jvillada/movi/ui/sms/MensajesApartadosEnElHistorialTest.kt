package com.jvillada.movi.ui.sms

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
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.porrevisar.mensajesPorRevisar
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Lo que Movi apartó se ve en el historial, y se puede devolver
 *
 * Desde el 3-oct-2026 el server aparta solo los mensajes que no son movimientos (un código, una
 * promoción, un recordatorio de pago): `ignored` con su motivo. No están en «Por revisar», pero
 * tampoco desaparecen: el historial de «Captura del banco» dice «Movi lo apartó: …» y ofrece «Era
 * un movimiento», que lo devuelve a la bandeja.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class MensajesApartadosEnElHistorialTest {

    @get:Rule val composeRule = createComposeRule()

    private val compra = SmsMessage(
        id = "sms_compra", time = "2026-10-01 10:00", bank = "85540",
        text = "Bancolombia: Compraste \$23.400,00 en TIENDA DE PRUEBA con tu T.Deb *1111.",
        state = SMS_STATE_PENDING, det = "",
    )
    private val codigo = SmsMessage(
        id = "sms_codigo", time = "2026-10-01 10:05", bank = "85540",
        text = "Bancolombia: Tu clave dinamica es 482913. Es personal e intransferible.",
        state = SMS_STATE_IGNORED, det = "", apartadoPor = "CODIGO_DE_VERIFICACION",
    )
    /** Lo que ignoró el dueño: sin motivo, y sin «Era un movimiento». */
    private val ignoradoPorEl = SmsMessage(
        id = "sms_ignorado", time = "2026-09-30 09:00", bank = "85540",
        text = "Bancolombia: Compraste \$9.900,00 en OTRA TIENDA con tu T.Deb *1111.",
        state = SMS_STATE_IGNORED, det = "",
    )

    private var mensajes = listOf(compra, codigo, ignoradoPorEl)
    private val devueltos = mutableListOf<String>()

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = mensajes
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun devolverSmsALaBandeja(id: String) {
            devueltos += id
            mensajes = mensajes.map { if (it.id == id) it.copy(state = SMS_STATE_PENDING, apartadoPor = null) else it }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperar(condicion: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condicion() }
        composeRule.waitForIdle()
    }

    @Test
    fun `el historial dice por que lo aparto, y Era un movimiento lo devuelve`() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { CapturaDelBancoScreen(onNavigate = {}) } }
        }

        esperar { cuantos("Movi lo apartó: código de verificación") == 1 }
        assertEquals(1, cuantos("APARTADO"))
        // Uno solo: el que ignoró el dueño se queda como él lo dejó.
        assertEquals(1, cuantos(ERA_UN_MOVIMIENTO))
        assertEquals(1, cuantos("Revisar"), "solo la compra pendiente")

        composeRule.onAllNodes(hasClickAction() and (hasText(ERA_UN_MOVIMIENTO) or hasAnyChild(hasText(ERA_UN_MOVIMIENTO))), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        esperar { devueltos.isNotEmpty() && cuantos("Movi lo apartó") == 0 }

        assertEquals(listOf("sms_codigo"), devueltos)
        assertEquals(0, cuantos(ERA_UN_MOVIMIENTO))
        assertEquals(2, cuantos("Revisar"), "el devuelto vuelve a esperar una decisión")
    }

    @Test
    fun `lo apartado no esta en Por revisar`() {
        assertEquals(listOf("sms_compra"), mensajesPorRevisar(mensajes).map { it.id })
    }
}
