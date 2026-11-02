package com.jvillada.movi.ui.extractos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.COMO_LO_SACO
import com.jvillada.movi.shared.model.ExtractoPorSubir
import com.jvillada.movi.shared.model.GUIA_DE_BANCOLOMBIA
import com.jvillada.movi.shared.model.GUIA_DE_NU
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.AccionDeLaPropuesta
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.TipoDePropuesta
import com.jvillada.movi.ui.dashboard.propuestasDelInicio
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * # Vuelta 107 · «¿Cómo lo saco?» en los tres lugares
 *
 * La guía para bajar el extracto aparece en el recordatorio del extracto, en el detalle de una cuenta o
 * tarjeta del banco y en la revisión de un extracto cuyo banco se reconoció. Con un banco sin guía,
 * nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xhdpi")
class ComoSacarElExtractoEnLaAppTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiarLaCostura() {
        Repositories.sustitutoDePrueba = null
    }

    // ── 1. El recordatorio del extracto ──────────────────────────────────────

    private fun extracto(banco: String) = ExtractoPorSubir("tc", "Master Black 3684", banco, "2026-10-17")

    @Test
    fun el_recordatorio_de_una_tarjeta_de_Bancolombia_ofrece_como_lo_saco() {
        val propuesta = propuestasDelInicio(DashboardData(extractosPorSubir = listOf(extracto("Bancolombia"))))
            .single { it.tipo == TipoDePropuesta.EXTRACTO }
        assertEquals(COMO_LO_SACO, propuesta.accionTerciaria)
        val accion = propuesta.alTocarTerciaria as AccionDeLaPropuesta.VerComoSacarElExtracto
        assertEquals(GUIA_DE_BANCOLOMBIA, accion.guia)
        assertEquals("2026-10-17", accion.corte)
    }

    @Test
    fun el_recordatorio_de_un_banco_sin_guia_no_ofrece_nada() {
        val propuesta = propuestasDelInicio(DashboardData(extractosPorSubir = listOf(extracto("Davivienda"))))
            .single { it.tipo == TipoDePropuesta.EXTRACTO }
        assertNull(propuesta.accionTerciaria)
        assertNull(propuesta.alTocarTerciaria)
    }

    // ── 2. El detalle de una cuenta o tarjeta ────────────────────────────────

    @Test
    fun el_detalle_de_una_cuenta_o_tarjeta_del_banco_tiene_la_guia_y_las_demas_no() {
        assertEquals(GUIA_DE_BANCOLOMBIA, guiaDelExtractoDeLaCuenta(Account("a", "Bancolombia Ahorros 8133 · 4057", AccountType.SAVINGS, 0)))
        // La tarjeta no dice el banco en el nombre: lo dicen sus condiciones.
        assertEquals(GUIA_DE_BANCOLOMBIA, guiaDelExtractoDeLaCuenta(Account("t", "Master Black 3684", AccountType.CREDIT_CARD, 0), "Bancolombia"))
        assertEquals(GUIA_DE_NU, guiaDelExtractoDeLaCuenta(Account("n", "Nu Tarjeta", AccountType.CREDIT_CARD, 0)))
        // Un banco sin guía, un crédito o una inversión: nada.
        assertNull(guiaDelExtractoDeLaCuenta(Account("d", "Davivienda 9418", AccountType.CREDIT_CARD, 0), "Davivienda"))
        assertNull(guiaDelExtractoDeLaCuenta(Account("l", "Libre inversión Bancolombia", AccountType.LOAN, 0)))
        assertNull(guiaDelExtractoDeLaCuenta(Account("f", "Fiducuenta Bancolombia", AccountType.INVESTMENT, 0)))
    }

    @Test
    fun la_hoja_dice_los_pasos_el_formato_y_el_rango_ideal_desde_el_ultimo_corte() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile = UserProfile(id = "u", email = "u@movi.test", name = "Dueño", avatarColor = "#000000", periodCutoffDay = 25)
        }
        composeRule.setContent {
            MoviTheme {
                HojaDeComoSacarElExtracto(
                    guia = GUIA_DE_BANCOLOMBIA, esTarjeta = true, onDismiss = {},
                    corteDeLaTarjeta = LocalDate(2026, 1, 3),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cómo sacar tu extracto de Bancolombia").assertIsDisplayed()
        composeRule.onNodeWithText("Ve a «Tarjetas de crédito» y toca la tarjeta.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Formato: PDF (recomendado). También sirve el Excel.").assertIsDisplayed()
        // El corte de la tarjeta (3 de enero) es más viejo que el inicio del período: manda ese.
        composeRule.onNodeWithTag(TAG_RANGO_IDEAL_DEL_EXTRACTO, useUnmergedTree = true)
            .assertTextContains("Rango ideal: desde tu último corte, el 3 de enero, hasta hoy.")
    }

    // ── 3. La revisión de un extracto subido ─────────────────────────────────

    private fun revision(banco: String) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Bancolombia Ahorros 8133", AccountType.SAVINGS, 0))
            override suspend fun getUserProfile(): UserProfile = UserProfile(id = "u", email = "u@movi.test", name = "Dueño", avatarColor = "#000000")
        }
        val result = StatementParseResult(
            statementId = "st_1",
            bankName = banco,
            period = "Octubre 2026",
            newTransactions = listOf(
                ParsedTransaction("f1", "2026-10-28", "TIENDA LIBERTARIO", 8_100, "COP", TransactionType.EXPENSE, "Comida", "COMPRA EN TIENDA LIBERTARIO", "x"),
            ),
            matches = emptyList(),
        )
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { StatementReviewScreen(onNavigate = {}, result = result) } }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun la_revision_de_un_extracto_de_Bancolombia_ofrece_la_guia_y_la_abre() {
        revision("Bancolombia")
        composeRule.onNodeWithTag(TAG_FILA_DE_COMO_SACAR_EL_EXTRACTO).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(TE_FALTAN_MOVIMIENTOS).performClick()
        composeRule.waitForIdle()
        // La hoja entra animada desde abajo: basta con que esté, con los pasos de una cuenta.
        composeRule.onNodeWithTag(TAG_HOJA_DE_COMO_SACAR_EL_EXTRACTO, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Abre la pestaña «Movimientos» y toca «Descargar».", useUnmergedTree = true).assertExists()
    }

    @Test
    fun la_revision_de_un_extracto_de_un_banco_sin_guia_no_ofrece_nada() {
        revision("Davivienda")
        composeRule.onNodeWithTag(TAG_FILA_DE_COMO_SACAR_EL_EXTRACTO).assertDoesNotExist()
    }
}
