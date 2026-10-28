package com.jvillada.movi.ui.credits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.AccountDetailScreen

/**
 * **El detalle de una deuda, montado como lo abre Créditos** (vuelta de diseño 4): el detalle de su
 * cuenta, con la sección de la deuda. Lo que la tarjeta de la lista decía de un préstamo —el interés
 * de la cuota, el plazo, cómo va— y sus botones viven ahí desde que la lista es de filas.
 *
 * [perfil] `null` = la lectura del perfil falla (el corte 1, el de calendario). [registrados] junta
 * los préstamos a los que se les registró un pago de un tercero o un descuento de nómina.
 */
internal class RepositorioDeLaDeuda(
    private val creditos: List<CreditSummary> = emptyList(),
    private val tarjetas: List<CardSummary> = emptyList(),
    private val perfil: UserProfile? = null,
    /** Los movimientos de la cuenta que se abre (vuelta de diseño 32: el total del día). */
    private val eventos: List<FinancialEvent> = emptyList(),
) : RepositorioDePrueba() {
    val registrados = mutableListOf<String>()
    override suspend fun getCredits(): List<CreditSummary> = creditos
    override suspend fun getCards(): List<CardSummary> = tarjetas
    override suspend fun getAccount(id: String): Account =
        (creditos.map { it.account } + tarjetas.map { it.account }).first { it.id == id }
    override suspend fun getEvents(accountId: String?): List<FinancialEvent> = eventos.filter { accountId == null || it.accountId == accountId }
    override suspend fun getUserProfile(): UserProfile = perfil ?: error("sin perfil")
    override suspend fun registerPayrollDeduction(accountId: String): CreditSummary {
        registrados += accountId
        return creditos.first { it.account.id == accountId }
    }
}

/** Monta el detalle de [cuenta] con [repo] y espera a que la sección de la deuda (o su aviso) llegue. */
internal fun ComposeContentTestRule.montarElDetalleDe(
    cuenta: Account,
    repo: RepositorioDeLaDeuda,
    onNavigate: (Screen) -> Unit = {},
) {
    Repositories.sustitutoDePrueba = repo
    setContent {
        MoviTheme {
            Box(Modifier.fillMaxSize()) {
                AccountDetailScreen(onNavigate = onNavigate, accountId = cuenta.id, group = cuenta.type.group)
            }
        }
    }
    // Un préstamo sin términos ni acciones no tendría sección: se espera la sección o el aviso de arriba.
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag(TAG_SECCION_DE_LA_DEUDA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithTag(TAG_FALTAN_DATOS_DE_LA_DEUDA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    waitForIdle()
}
