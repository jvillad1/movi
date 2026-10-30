package com.jvillada.movi.ui.buscar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.BusquedasRecientesStore
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.tuParteComoEventos
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.quickadd.todayIsoInAppZone
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * # Vuelta 78 · Buscar muestra tu parte de lo que pagó otra persona
 *
 * «mercado» lista «Mercado del sábado» —pagado por Caro— con tu parte como cifra y debajo «de $240.000 ·
 * pagó Caro» (revisión de la 78).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class BuscarMuestraTuParteDelGrupoTest {

    @get:Rule val composeRule = createComposeRule()

    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val casa = Espacio(id = "esp_casa", nombre = "Casa con Caro", miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), MiembroDelEspacio("dst_caro", "Caro")))
    private val tuParte = tuParteComoEventos(
        listOf(
            GastoDelEspacio(
                id = "g1", espacioId = casa.id, pagadoPor = "dst_caro", monto = 240_000, categoria = "Mercado",
                descripcion = "Mercado del sábado", fecha = ahora - 60_000, partes = mapOf(MIEMBRO_YO to 120_000L, "dst_caro" to 120_000L),
            ),
        ),
    )
    private val d1 = FinancialEvent(
        id = "d1", accountId = "a1", type = TransactionType.EXPENSE, amount = 41_000, category = "Mercado",
        description = "Tienda D1 Laureles", timestamp = ahora - 120_000, countsAsCashFlow = true,
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    @Test
    fun `la parte que pago Caro es una fila con quien pago y tu parte`() {
        BusquedasRecientesStore.almacenDePrueba = mutableMapOf()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 1)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(EventDay(todayIsoInAppZone(), -41_000L, listOf(d1)))
            override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Nu", AccountType.SAVINGS, 0L))
            override suspend fun getTuParteDeOtros(): List<FinancialEvent> = tuParte
            override suspend fun getEspacios(): List<Espacio> = listOf(casa)
        }
        composeRule.setContent { MoviTheme { Box(Modifier.fillMaxSize()) { BuscarScreen(onNavigate = {}) } } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSCAR).performTextReplacement("mercado")
        val tag = TAG_RESULTADO_DE_BUSCAR + "mov:${tuParte.single().id}"
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        fun dentro(texto: String, substring: Boolean = false) =
            composeRule.onAllNodes(hasText(texto, substring = substring) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        composeRule.waitUntil(5_000) { dentro("pagó Caro", substring = true) }
        assertTrue(dentro("Mercado del sábado"), "se llama como en Actividad")
        assertTrue(dentro("−\$120.000"), "la cifra es tu parte")
        assertTrue(dentro("de \$240.000 · pagó Caro"), "y debajo, de cuánto fue y quién pagó")
        // Y tus propios movimientos siguen ahí.
        assertTrue(composeRule.onAllNodesWithTag(TAG_RESULTADO_DE_BUSCAR + "mov:d1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
