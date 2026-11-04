package com.jvillada.movi.ui.credits

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.recurrentes.SI_YA_VENCIO_DESDE_EL_PROXIMO_PERIODO
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Vuelta 127 · «¿Ya pagaste la cuota de este período?» sin el perfil
 *
 * El alta de un crédito que ya venía tenía el límite viejo: sin el perfil leído no preguntaba, y si la lectura
 * fallaba el crédito nacía con la cuota de este período vencida. Ahora usa el criterio de la vuelta 125
 * (`ajustesParaPreguntarSiYaPagaste`): mientras se lee no pregunta; si la lectura falla decide con el mes de
 * calendario y, con «Sí», no afirma desde cuándo (el server lo revalida con el período de verdad).
 *
 * El día de pago es el 1: con el mes de calendario la cuota de este período ya venció cualquier día.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class LaCuotaDelAltaSinPerfilTest {

    @get:Rule val composeRule = createComposeRule()

    @Volatile private var perfilPedido = 0

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(perfil: suspend () -> UserProfile) {
        perfilPedido = 0
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = emptyList()
            override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
            override suspend fun getUserProfile(): UserProfile {
                perfilPedido++
                return perfil()
            }
        }
        composeRule.setContent {
            MoviTheme { CreditTermsSheet(editing = null, candidates = emptyList(), onDismiss = {}, onSaved = {}) }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("No, ya lo tenía desde antes") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction() and hasContentDescription("Día de pago"), useUnmergedTree = true).apply {
            performScrollTo()
            performTextInput("1")
        }
        composeRule.waitForIdle()
    }

    private fun hayPregunta(): Boolean =
        composeRule.onAllNodesWithTag(TAG_YA_PAGASTE_LA_CUOTA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `si la lectura del perfil falla, pregunta con el mes de calendario y no afirma desde cuando`() {
        montar { error("sin señal") }
        composeRule.waitUntil(5_000) { hayPregunta() }
        assertTrue(hay(YA_PAGASTE_LA_CUOTA_DE_ESTE_PERIODO))
        assertTrue(hay(SI_YA_VENCIO_DESDE_EL_PROXIMO_PERIODO), "con «Sí» y sin el período del dueño, condicionado")
        assertFalse(hay(LA_PRIMERA_CUOTA_ES_LA_DEL_SIGUIENTE), "no afirma «desde el próximo período» sin saber cuál es")

        composeRule.onNode(hasText(YA_PAGASTE_LA_CUOTA_DE_ESTE_PERIODO) and hasClickAction(), useUnmergedTree = false)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay(LA_CUOTA_DE_ESTE_PERIODO_QUEDA_POR_PAGAR), "con «No», la de este período queda por pagar")
    }

    @Test
    fun `mientras se lee el perfil no pregunta`() {
        val nunca = CompletableDeferred<UserProfile>()
        montar { nunca.await() }
        composeRule.waitUntil(5_000) { perfilPedido > 0 }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertFalse(hayPregunta(), "sin saber cuál es «este período» todavía no se pregunta")
    }

    @Test
    fun `con el perfil leido dice desde el proximo periodo`() {
        montar { UserProfile(id = "u", email = "u@movi.test", name = "Juan", avatarColor = "#FFC21A", periodCutoffDay = 1) }
        composeRule.waitUntil(5_000) { hayPregunta() }
        assertTrue(hay(LA_PRIMERA_CUOTA_ES_LA_DEL_SIGUIENTE))
        assertFalse(hay(SI_YA_VENCIO_DESDE_EL_PROXIMO_PERIODO))
    }

    @Test
    fun `el subtitulo de la cuota`() {
        assertEquals(LA_CUOTA_DE_ESTE_PERIODO_QUEDA_POR_PAGAR, subtituloDeLaCuota(marcada = false, conElPeriodoDeRespaldo = true))
        assertEquals(LA_CUOTA_DE_ESTE_PERIODO_QUEDA_POR_PAGAR, subtituloDeLaCuota(marcada = false, conElPeriodoDeRespaldo = false))
        assertEquals(SI_YA_VENCIO_DESDE_EL_PROXIMO_PERIODO, subtituloDeLaCuota(marcada = true, conElPeriodoDeRespaldo = true))
        assertEquals(LA_PRIMERA_CUOTA_ES_LA_DEL_SIGUIENTE, subtituloDeLaCuota(marcada = true, conElPeriodoDeRespaldo = false))
    }
}
