package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_FRANJA_POR_CONFIRMAR
import com.jvillada.movi.ui.sms.ELEGIR_CATEGORIA
import com.jvillada.movi.ui.sms.TAG_CATEGORIAS_EN_LA_TARJETA
import com.jvillada.movi.ui.sms.TAG_CONFIRMAR_EN_LA_FILA
import com.jvillada.movi.ui.sms.TAG_ELEGIR_CATEGORIA_EN_LA_FILA
import com.jvillada.movi.ui.sms.TAG_TODAS_EN_LA_TARJETA
import com.jvillada.movi.ui.sms.tagDeCategoriaEnLaTarjeta
import com.jvillada.movi.ui.transactions.TAG_LISTA_DE_LA_CONFIRMACION
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
 * # Un aviso en «Otros» se resuelve en su tarjeta (vuelta 46)
 *
 * Con datos reales la revisión de la vuelta 44 midió 7 toques (8 si la categoría no estaba entre las
 * frecuentes) para tres avisos entendidos y uno en «Otros»: había que abrirlo en Revisar aviso. Ahora
 * la tarjeta pide la categoría ahí mismo: «Elegir categoría» y la pastilla —2 toques— y queda
 * confirmable; con el lote, los cuatro se confirman desde el Inicio en 5.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xhdpi")
class ElOtrosSeResuelveEnLaTarjetaTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = hoyEnAppZone().toString()

    private fun aviso(id: String, monto: String, comercio: String, hora: String) = SmsMessage(
        id = id, time = "$hoy $hora", bank = "85540", state = SMS_STATE_PENDING, det = "",
        text = "Bancolombia: Compraste \$$monto,00 en $comercio con tu T.Deb *4057, el 28/10/2026 a las $hora.",
    )

    private val panamericana = aviso("pana", "54.000", "PANAMERICANA LAURELES", "14:05")
    private val avisos = mutableListOf(
        panamericana,
        aviso("cafe", "12.300", "JUAN VALDEZ CAFE", "13:40"),
        aviso("uber", "18.300", "UBER RIDES", "12:05"),
        aviso("farma", "34.000", "FARMATODO LAURELES", "09:40"),
    )
    private val ahorros = Account("acc-4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 3_000_000L, "COP")

    private val propuestas = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso("pana", leido = ParsedSms(54_000.0, "PANAMERICANA LAURELES", TransactionType.EXPENSE, "Otros")),
            PropuestaDelAviso("cafe", leido = ParsedSms(12_300.0, "JUAN VALDEZ CAFE", TransactionType.EXPENSE, "Comida")),
            PropuestaDelAviso("uber", leido = ParsedSms(18_300.0, "UBER RIDES", TransactionType.EXPENSE, "Transporte")),
            PropuestaDelAviso("farma", leido = ParsedSms(34_000.0, "FARMATODO LAURELES", TransactionType.EXPENSE, "Salud")),
        ),
        categoriasDelDueno = listOf("Comida", "Papelería", "Salud", "Transporte"),
    )

    private val publicados = mutableListOf<FinancialEvent>()
    private val confirmados = mutableListOf<String>()

    private inner class Repo : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = avisos.toList()
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja =
            propuestas.copy(propuestas = propuestas.propuestas.filter { p -> avisos.any { it.id == p.avisoId && it.state == SMS_STATE_PENDING } })
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event.also { publicados += it }
        override suspend fun confirmSmsCon(id: String, eventoId: String) {
            confirmados += id
            avisos.replaceAll { if (it.id == id) it.copy(state = SMS_STATE_CONFIRMED) else it }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private var toques = 0

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun conTag(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperar(condicion: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condicion() }
        composeRule.waitForIdle()
    }

    private fun tocarElTag(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        toques++
        composeRule.waitForIdle()
    }

    private fun tocarElBoton(texto: String, elUltimo: Boolean = false) {
        val nodos = composeRule.onAllNodes(hasText(texto), useUnmergedTree = true)
        (if (elUltimo) nodos.onLast() else nodos.onFirst()).onParent().performSemanticsAction(SemanticsActions.OnClick)
        toques++
        composeRule.waitForIdle()
    }

    private val inicio = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 4_000_000),
        accounts = listOf(ahorros),
        credits = emptyList<CreditSummary>(),
        cards = emptyList<CardSummary>(),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        periodoActual = PeriodoFinanciero(2026, 10),
        ajustesDePeriodo = PeriodSettings(),
        pendingSms = 4,
        porConfirmarSuman = 118_600L,
    )

    private fun montarDesdeElInicio() {
        Repositories.sustitutoDePrueba = Repo()
        var enPorRevisar by mutableStateOf(false)
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    if (!enPorRevisar) {
                        SduiRenderer(
                            definition = defaultDashboardDefinition(),
                            data = inicio,
                            modifier = Modifier.fillMaxSize(),
                            onNavigate = { if (it == Screen.PorRevisar) enPorRevisar = true },
                            conBanda = true,
                        )
                    } else {
                        PorRevisarScreen(onNavigate = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_FRANJA_POR_CONFIRMAR, useUnmergedTree = true).performClick()
        toques++
        esperar { conTag(TAG_ELEGIR_CATEGORIA_EN_LA_FILA) == 1 && conTag(TAG_CONFIRMAR_EN_LA_FILA) == 3 }
    }

    @Test
    fun `el aviso en Otros pide la categoria en su tarjeta y queda confirmable con dos toques`() {
        montarDesdeElInicio()
        // Solo falta la categoría: el principal es «Elegir categoría», no «Revisar aviso».
        assertEquals(1, cuantos(ELEGIR_CATEGORIA))

        val antes = toques
        tocarElTag(TAG_ELEGIR_CATEGORIA_EN_LA_FILA)
        // Vuelta 47: abiertas las pastillas, el botón dice «Cerrar» y deja de ser el principal.
        assertEquals(0, cuantos(ELEGIR_CATEGORIA))
        assertEquals(1, composeRule.onAllNodes(hasText(com.jvillada.movi.ui.sms.CERRAR_LAS_CATEGORIAS) and hasAnyAncestor(hasTestTag(TAG_ELEGIR_CATEGORIA_EN_LA_FILA)), useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag(TAG_ELEGIR_CATEGORIA_EN_LA_FILA, useUnmergedTree = true).assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                com.jvillada.movi.ui.components.ds.VarianteDeBotonKey,
                com.jvillada.movi.ui.components.ds.VarianteDeBoton.Secundario,
            ),
        )
        // Las frecuentes, y «Todas ›» en el renglón del rótulo, a la vista sin deslizar.
        composeRule.onNodeWithTag(TAG_CATEGORIAS_EN_LA_TARJETA, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_TODAS_EN_LA_TARJETA, useUnmergedTree = true).assertIsDisplayed()
        tocarElTag(tagDeCategoriaEnLaTarjeta("Papelería"))
        assertEquals(2, toques - antes, "el mal categorizado, en 2 toques")

        // La tarjeta queda confirmable ahí mismo, con lo elegido arriba.
        esperar { conTag(TAG_CONFIRMAR_EN_LA_FILA) == 4 }
        assertEquals(1, cuantos("Papelería · Bancolombia Ahorros 4057"))
        assertEquals(0, cuantos(ELEGIR_CATEGORIA))
    }

    @Test
    fun `los cuatro, uno en Otros, se confirman desde el Inicio en cinco toques`() {
        montarDesdeElInicio()
        tocarElTag(TAG_ELEGIR_CATEGORIA_EN_LA_FILA)
        tocarElTag(tagDeCategoriaEnLaTarjeta("Papelería"))
        esperar { cuantos("Confirmar 4") > 0 }

        tocarElBoton("Confirmar 4")
        // La hoja lista los avisos como filas: comercio, «Categoría · Cuenta» y el monto.
        composeRule.onNodeWithTag(TAG_LISTA_DE_LA_CONFIRMACION, useUnmergedTree = true).assertExists()
        listOf("Panamericana Laureles", "Juan Valdez Cafe", "Uber Rides", "Farmatodo Laureles").forEach { comercio ->
            assertEquals(
                1,
                composeRule.onAllNodes(hasText(comercio) and hasAnyAncestor(hasTestTag(TAG_LISTA_DE_LA_CONFIRMACION)), useUnmergedTree = true)
                    .fetchSemanticsNodes().size,
                comercio,
            )
        }
        assertEquals(1, composeRule.onAllNodes(hasText("Papelería · Bancolombia Ahorros 4057") and hasAnyAncestor(hasTestTag(TAG_LISTA_DE_LA_CONFIRMACION)), useUnmergedTree = true).fetchSemanticsNodes().size)
        tocarElBoton("Confirmar", elUltimo = true)
        esperar { confirmados.size == 4 }

        assertEquals(5, toques, "la franja, «Elegir categoría», la pastilla, «Confirmar los 4» y la hoja")
        assertEquals("Papelería", publicados.single { it.amount == 54_000L }.category)
        // Sin «Deshacer»: no hay una vuelta limpia (ver el reporte de la vuelta 46).
        esperar { cuantos("4 confirmados") == 1 }
        assertEquals(0, cuantos("Deshacer"))
    }

    @Test
    fun `Todas va en el renglon del rotulo, arriba de las pastillas, a 360`() {
        montarDesdeElInicio()
        tocarElTag(TAG_ELEGIR_CATEGORIA_EN_LA_FILA)
        val todas = composeRule.onNodeWithTag(TAG_TODAS_EN_LA_TARJETA, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pastillas = composeRule.onNodeWithTag(TAG_CATEGORIAS_EN_LA_TARJETA, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(todas.bottom <= pastillas.top, "«Todas ›» va arriba de las pastillas, no al final: $todas / $pastillas")
        assertTrue(todas.right <= pastillas.right, "y dentro del ancho de la tarjeta")
    }
}
