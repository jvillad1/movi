package com.jvillada.movi.ui.credits

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.EsqueletoDeLaCascara
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.components.ds.TAG_CIFRA_CON_ROTULO
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
 * # El héroe de Créditos sin sangrías, con las cifras al borde derecho (vuelta 61, vuelta 77)
 *
 * La revisión a 1.280 de la vuelta 61: «Los pagas tú» y «Contando todas tus deudas» iban corridos 8 dp a
 * la derecha de su grupo. Ahora cada renglón arranca donde arranca su grupo. Vuelta 77: los renglones
 * —y «Cuadrar»— van a lo ancho de la tarjeta, con la cifra al borde derecho; topados a una columna, a
 * 1.280 las cifras terminaban a mitad de la tarjeta y «Cuadrar» flotaba en el centro. Solo la forma.
 *
 * Montado en la cáscara de verdad con la letra ×1,12; `sdk = [34]` y `NATIVE` para el texto real.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ElHeroeDeCreditosVaJuntoTest {

    @get:Rule val composeRule = createComposeRule()

    private fun prestamo(id: String, nombre: String) = CreditSummary(
        account = Account(id, nombre, AccountType.LOAN, balance = 40_104_518L),
        terms = CreditTerms(
            accountId = id, bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
        ),
        paidPct = 0.5,
    )

    /** Un préstamo sin saldo, para que «1 crédito sin saldo · Cuadrar» aparezca (vuelta 77). */
    private val sinSaldo = prestamo("acc_3", "Crédito sin saldo").let { it.copy(account = it.account.copy(balance = -980_000L)) }

    private val tarjeta = CardSummary(
        account = Account("acc_mb", "Master Black", AccountType.CREDIT_CARD, balance = 3_200_000L),
        terms = null,
    )

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> =
                listOf(prestamo("acc_1", "Vehículo 4083"), prestamo("acc_2", "Libranza 7788"), sinSaldo)
            override suspend fun getCards(): List<CardSummary> = listOf(tarjeta)
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "u@local", name = "U", avatarColor = "#000000", periodCutoffDay = 25)
        }
        composeRule.setContent {
            ConClaseDeAncho {
                EsqueletoDeLaCascara(
                    pantalla = Screen.Credits,
                    activeTab = NavTab.TU,
                    conNavegacion = true,
                    onTabSelected = {},
                    relevoDeScroll = remember { RelevoDeScroll() },
                ) { CreditosScreen(onNavigate = {}) }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(TITULO_INTERES_DEL_MES, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun comprobar() {
        val titulos = composeRule.onAllNodesWithTag(TAG_TITULO_DEL_GRUPO_DEL_RESUMEN, useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue(titulos.isNotEmpty(), "no encontré los grupos del resumen")
        val renglones = composeRule.onAllNodes(
            hasTestTag(TAG_CIFRA_CON_ROTULO).and(hasAnyAncestor(hasTestTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue(renglones.size >= 2, "no encontré los renglones del resumen: $renglones")
        val izquierda = titulos.first().left
        val derecha = renglones.maxOf { it.right }
        renglones.forEach { r ->
            assertEquals(izquierda, r.left, 0.5f, "el renglón va sin sangría, donde arranca su grupo: $r / $izquierda")
            assertEquals(derecha, r.right, 0.5f, "todas las cifras terminan en el mismo borde: $r / $derecha")
        }
        // El borde derecho es el de la tarjeta (menos su relleno), no la mitad de la tarjeta.
        val tarjeta = composeRule.onNodeWithTag(TAG_TARJETA_DEL_RESUMEN_DE_DEUDA, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val relleno = izquierda - tarjeta.left
        assertEquals(tarjeta.right - relleno, derecha, 0.5f, "las cifras van al borde derecho de la tarjeta")
        // «Cuadrar» va en la columna de las cifras.
        val cuadrar = composeRule.onNodeWithTag(TAG_DEUDAS_SIN_SALDO, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(derecha, cuadrar.right, 0.5f, "«Cuadrar» termina donde terminan las cifras")
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp-mdpi")
    fun `a 1280 dp el rotulo y la cifra van juntos y sin sangria`() {
        montar()
        comprobar()
    }

    @Test
    @Config(qualifiers = "w390dp-h2400dp-mdpi")
    fun `a 390 dp tambien sin sangria`() {
        montar()
        comprobar()
    }
}
