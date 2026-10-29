package com.jvillada.movi.ui.credits

import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
 * # El héroe de Créditos sin sangrías, y con el rótulo junto a su cifra (vuelta 61)
 *
 * La revisión a 1.280: «Los pagas tú» y «Contando todas tus deudas» iban corridos 8 dp a la derecha de
 * su grupo, y el rótulo y la cifra quedaban a ~900 px uno del otro (la tarjeta de «Deuda total» va a lo
 * ancho del tablero). Ahora cada renglón arranca donde arranca su grupo y mide a lo sumo
 * [ANCHO_MAXIMO_DEL_RESUMEN_DE_DEUDA]. Solo la forma: las cifras son de la vuelta 59.
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

    private val tarjeta = CardSummary(
        account = Account("acc_mb", "Master Black", AccountType.CREDIT_CARD, balance = 3_200_000L),
        terms = null,
    )

    private fun montar() {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getCredits(): List<CreditSummary> = listOf(prestamo("acc_1", "Vehículo 4083"), prestamo("acc_2", "Libranza 7788"))
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
        renglones.forEach { r ->
            assertEquals(izquierda, r.left, 0.5f, "el renglón va sin sangría, donde arranca su grupo: $r / $izquierda")
            assertTrue(
                r.width <= ANCHO_MAXIMO_DEL_RESUMEN_DE_DEUDA.value + 0.5f,
                "el rótulo y su cifra quedan juntos: el renglón mide ${r.width} dp",
            )
        }
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
