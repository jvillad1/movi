package com.jvillada.movi.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.HACE_MAS_DE_UN_MES_QUE_NO_LA_CUADRAS
import com.jvillada.movi.shared.model.NUNCA_LA_HAS_CUADRADO
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * # Patrimonio y el detalle de la cuenta dicen lo mismo del cuadre (vuelta 31)
 *
 * En producción, Patrimonio decía «Hace más de un mes que no cuadras Bancolombia Ahorros» y el
 * detalle de esa misma cuenta «Nunca la has cuadrado». Las dos salen ahora de `fraseDelCuadre`: se
 * monta una pantalla, se lee la frase, se cambia a la otra y tiene que estar la misma.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h2400dp-xhdpi")
class ElCuadreDiceLoMismoTest {

    @get:Rule val composeRule = createComposeRule()

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private val haceDosMeses = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000

    private fun repo(cuenta: Account) = object : RepositorioDePrueba() {
        override suspend fun getAccounts(): List<Account> = listOf(cuenta)
        override suspend fun getAccount(id: String): Account = cuenta
        override suspend fun getEvents(accountId: String?): List<FinancialEvent> = emptyList()
        override suspend fun getCredits(): List<CreditSummary> = emptyList()
        override suspend fun getCards(): List<CardSummary> = emptyList()
        override suspend fun getDestinos(): List<DestinoConocido> = emptyList()
    }

    private fun hay(texto: String): Boolean =
        composeRule.onAllNodes(hasText(texto, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun esperar(texto: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { hay(texto) }
    }

    /**
     * Monta Patrimonio y espera «[nombre] · [enPatrimonio]»; pasa al detalle y espera [enElDetalle],
     * que contiene el mismo tramo.
     */
    private fun lasDosDicen(cuenta: Account, enPatrimonio: String, enElDetalle: String) {
        assertTrue(enPatrimonio in enElDetalle, "la versión con nombre es la misma frase, más corta")
        Repositories.sustitutoDePrueba = repo(cuenta)
        var enElDetalleAhora by mutableStateOf(false)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    if (enElDetalleAhora) {
                        AccountDetailScreen(onNavigate = {}, accountId = cuenta.id, group = AccountGroup.DINERO)
                    } else {
                        AccountsScreen(onNavigate = {})
                    }
                }
            }
        }
        esperar("${cuenta.name} · $enPatrimonio")

        enElDetalleAhora = true
        composeRule.waitForIdle()
        esperar("Cuadrar con el banco")
        assertTrue(hay(enElDetalle), "el detalle de «${cuenta.name}» dice «$enElDetalle»")
        if (enElDetalle != NUNCA_LA_HAS_CUADRADO) assertTrue(!hay(NUNCA_LA_HAS_CUADRADO), "el detalle no puede decir «nunca»")
    }

    @Test
    fun `una cuenta que nunca se cuadro dice nunca en las dos pantallas`() {
        lasDosDicen(
            Account("acc-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L, firstEventAt = haceDosMeses),
            enPatrimonio = NUNCA_LA_HAS_CUADRADO,
            enElDetalle = NUNCA_LA_HAS_CUADRADO,
        )
    }

    /** Cuadrada hace 60 días: las dos dicen cuándo fue la última vez, con la misma fecha. */
    @Test
    fun `una cuenta cuadrada hace 60 dias dice la misma fecha en las dos pantallas`() {
        val cuenta = Account(
            "acc-bc", "Bancolombia Ahorros", AccountType.SAVINGS, 13_845_800L,
            firstEventAt = haceDosMeses, lastAdjustmentAt = haceDosMeses,
        )
        val dia = Instant.fromEpochMilliseconds(haceDosMeses).toLocalDateTime(AppTimeZone.zone).date
        val ultima = "última el ${dia.dayOfMonth} ${MESES[dia.monthNumber - 1]}" +
            if (dia.year != Clock.System.now().toLocalDateTime(AppTimeZone.zone).year) " ${dia.year}" else ""
        lasDosDicen(cuenta, enPatrimonio = ultima, enElDetalle = "$HACE_MAS_DE_UN_MES_QUE_NO_LA_CUADRAS · $ultima")
    }

    private companion object {
        val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    }
}
