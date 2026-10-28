package com.jvillada.movi.ui.buscar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.BusquedasRecientesStore
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.TAG_SECCION
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 35 · los resultados de Buscar van en su superficie
 *
 * A 390 px, «Movimientos · 2» y «Comercios · 1» eran filas sueltas sobre el fondo, cuando en
 * Movimientos, Espacios, Ajustes y Patrimonio toda lista va dentro de una `Superficie` de lista. Ahora
 * cada grupo —los de resultados y las recientes— es su título y, debajo, una superficie con sus
 * filas: ningún resultado queda fuera de la de su grupo, y el título va encima, no adentro.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class LosResultadosDeBuscarVanEnSuSuperficieTest {

    @get:Rule val composeRule = createComposeRule()

    private fun gasto(id: String, monto: Long, comercio: String) = FinancialEvent(
        id = id, accountId = "a1", type = TransactionType.EXPENSE, amount = monto, category = "Transporte",
        description = comercio, merchant = comercio.uppercase(), timestamp = 1_790_000_000_000L,
    )

    private val uber1 = gasto("u1", 18_100L, "Uber")
    private val uber2 = gasto("u2", 22_000L, "Uber")

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF", periodCutoffDay = 25)
        override suspend fun getEventsByDay(): List<EventDay> =
            listOf(EventDay(date = "2026-09-21", total = -40_100L, items = listOf(uber1, uber2)))
        override suspend fun getAccounts(): List<Account> = listOf(Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 1_000_000L))
        override suspend fun getDestinos(): List<DestinoConocido> = listOf(DestinoConocido(id = "d1", nombre = "Caro", numero = "31973270756"))
        override suspend fun getCategories(): List<CategoryUsage> = listOf(CategoryUsage(name = "Transporte"))
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar() {
        BusquedasRecientesStore.almacenDePrueba = mutableMapOf()
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { BuscarScreen(onNavigate = {}) } }
        }
        composeRule.waitForIdle()
    }

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun tagDe(nodo: androidx.compose.ui.semantics.SemanticsNode): String? = nodo.config.getOrNull(SemanticsProperties.TestTag)

    private fun empiezaCon(prefijo: String) = SemanticsMatcher("testTag empieza con «$prefijo»") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefijo) == true
    }

    private fun escribir(texto: String) {
        composeRule.onNodeWithTag(TAG_CAMPO_DE_BUSCAR).performTextReplacement(texto)
        composeRule.waitForIdle()
    }

    @Test
    fun `cada resultado esta dentro de la superficie de su grupo`() {
        montar()
        escribir("uber")
        composeRule.waitUntil(5_000) { conTag(TAG_RESULTADO_DE_BUSCAR + "mov:u1") > 0 }

        val resultados = composeRule.onAllNodes(empiezaCon(TAG_RESULTADO_DE_BUSCAR), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(resultados.size >= 3, "dos movimientos y un comercio: ${resultados.map(::tagDe)}")
        val sueltos = composeRule.onAllNodes(
            empiezaCon(TAG_RESULTADO_DE_BUSCAR) and !hasAnyAncestor(empiezaCon(TAG_GRUPO_DE_BUSCAR)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(sueltos.isEmpty(), "resultados fuera de una superficie: ${sueltos.map(::tagDe)}")

        // Los movimientos en la de Movimientos, el comercio en la de Comercios: una por grupo.
        assertEquals(
            2,
            composeRule.onAllNodes(
                empiezaCon(TAG_RESULTADO_DE_BUSCAR + "mov:") and hasAnyAncestor(hasTestTag(TAG_GRUPO_DE_BUSCAR + TipoDeResultado.MOVIMIENTO.name)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size,
        )
        assertEquals(1, conTag(TAG_GRUPO_DE_BUSCAR + TipoDeResultado.MOVIMIENTO.name))
        assertEquals(1, conTag(TAG_GRUPO_DE_BUSCAR + TipoDeResultado.COMERCIO.name))

        // El título del grupo va encima de su superficie, no adentro.
        val dentro = composeRule.onAllNodes(hasTestTag(TAG_SECCION) and hasAnyAncestor(empiezaCon(TAG_GRUPO_DE_BUSCAR)), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(dentro.isEmpty(), "un título quedó dentro de la superficie")
        val titulo = composeRule.onAllNodes(hasTestTag(TAG_SECCION), useUnmergedTree = true).fetchSemanticsNodes().minBy { it.boundsInRoot.top }
        val superficie = composeRule.onNodeWithTag(TAG_GRUPO_DE_BUSCAR + TipoDeResultado.MOVIMIENTO.name, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(titulo.boundsInRoot.bottom <= superficie.boundsInRoot.top + 0.5f, "el título va arriba de la superficie")
    }

    @Test
    fun `las recientes tambien van en su superficie`() {
        montar()
        BusquedasRecientesStore.anotar("uber")
        BusquedasRecientesStore.anotar("caro")
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { conTag(TAG_RECIENTE_DE_BUSCAR + "uber") > 0 }

        assertEquals(1, conTag(TAG_GRUPO_DE_BUSCAR + "recientes"))
        val sueltas = composeRule.onAllNodes(
            empiezaCon(TAG_RECIENTE_DE_BUSCAR) and !hasAnyAncestor(hasTestTag(TAG_GRUPO_DE_BUSCAR + "recientes")),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue(sueltas.isEmpty(), "recientes fuera de su superficie: ${sueltas.map(::tagDe)}")
        assertEquals(
            2,
            composeRule.onAllNodes(empiezaCon(TAG_RECIENTE_DE_BUSCAR), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }
}
