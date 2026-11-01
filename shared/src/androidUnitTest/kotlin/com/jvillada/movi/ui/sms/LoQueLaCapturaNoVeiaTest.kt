package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.CanalDeCaptura
import com.jvillada.movi.shared.model.CorreosDescartados
import com.jvillada.movi.shared.model.GrupoDeCorreosDescartados
import com.jvillada.movi.shared.model.MotivoDeCorreoDescartado
import com.jvillada.movi.shared.model.OrigenMudo
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * # Vuelta 90 · lo que la pantalla de la captura no veía
 *
 * El banco mudo lo dice el server (con el motivo: Glim manda rechazos y promociones, no compras), y los
 * correos que llegaron y no se pudieron guardar se cuentan en una línea. Sin nada de eso, no se pinta
 * nada nuevo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class LoQueLaCapturaNoVeiaTest {

    @get:Rule val composeRule = createComposeRule()

    private var mudos: List<OrigenMudo> = emptyList()
    private var descartados: CorreosDescartados? = null

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = emptyList()
        override suspend fun getUserProfile(): UserProfile = UserProfile(
            id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", smsAlertMuted = false,
        )
        override suspend fun getOrigenesMudos(): List<OrigenMudo> = mudos
        override suspend fun getCorreosDescartados(): CorreosDescartados? = descartados
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { CapturaDelBancoScreen(onNavigate = {}) } }
        }
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `el banco mudo con motivo y los correos descartados se ven`() {
        mudos = listOf(
            OrigenMudo(
                clave = "NOTIFICACION|glim", canal = CanalDeCaptura.NOTIFICACION, nombre = "Glim",
                diasSinCaptura = 29, ultima = 0L, motivo = "llegan rechazos y promociones",
            ),
        )
        descartados = CorreosDescartados(
            propios = listOf(GrupoDeCorreosDescartados(MotivoDeCorreoDescartado.ILEGIBLE.name, 2, 1L)),
        )
        montar()
        composeRule.waitUntil(5_000) { hay("no traen movimientos") }
        assertTrue(hay("Hace 29 días las notificaciones de Glim no traen movimientos: llegan rechazos y promociones."))
        composeRule.waitUntil(5_000) { hay("no se pudieron guardar") }
        assertTrue(hay("2 correos tuyos no se pudieron guardar en los últimos 30 días: no se pudo leer."))
    }

    @Test
    fun `sin descartados no hay linea de correos`() {
        descartados = CorreosDescartados()
        montar()
        composeRule.waitUntil(5_000) { hay("Avisarme si un banco se calla") }
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag(TAG_CORREOS_DESCARTADOS, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
