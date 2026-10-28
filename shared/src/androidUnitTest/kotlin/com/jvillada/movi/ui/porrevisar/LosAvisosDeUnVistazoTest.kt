package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
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
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.sdui.TAG_FRANJA_POR_CONFIRMAR
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.ui.sms.TAG_CONFIRMAR_EN_LA_FILA
import com.jvillada.movi.ui.sms.TAG_LINEA_DE_LO_ENTENDIDO
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
 * # Los avisos se confirman de un vistazo (vuelta 44)
 *
 * La revisión de «un día del dueño» con tres SMS de Bancolombia (Uber, Farmatodo, Juan Valdez): cada
 * tarjeta decía solo «Revisar | Ignorar» con el SMS crudo, el título de Revisar aviso decía «85540» y
 * confirmar los tres costaba siete toques desde el Inicio. Ahora cada tarjeta dice lo que Movi
 * entendió, el banco por su nombre, y se confirman los tres en tres toques: la franja, «Confirmar los
 * 3» y el «Confirmar» de la hoja.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class LosAvisosDeUnVistazoTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = hoyEnAppZone().toString()

    private fun aviso(id: String, monto: String, comercio: String, hora: String) = SmsMessage(
        id = id, time = "$hoy $hora", bank = "85540", state = SMS_STATE_PENDING, det = "",
        text = "Bancolombia: Compraste \$$monto,00 en $comercio con tu T.Deb *4057, el 28/10/2026 a las $hora.",
    )

    private val uber = aviso("uber", "18.300", "UBER RIDES", "12:05")
    private val farma = aviso("farma", "61.900", "FARMATODO LAURELES", "09:40")
    private val cafe = aviso("cafe", "23.400", "JUAN VALDEZ CAFE", "08:12")
    private val ahorros = Account("acc-4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 3_000_000L, "COP")

    private val avisos = mutableListOf(uber, farma, cafe)
    private val confirmados = mutableListOf<String>()
    private val navegado = mutableListOf<Screen>()

    private fun propuestas(categoriaDeFarmatodo: String) = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso("uber", leido = ParsedSms(18_300.0, "UBER RIDES", TransactionType.EXPENSE, "Transporte")),
            PropuestaDelAviso("farma", leido = ParsedSms(61_900.0, "FARMATODO LAURELES", TransactionType.EXPENSE, categoriaDeFarmatodo)),
            PropuestaDelAviso("cafe", leido = ParsedSms(23_400.0, "JUAN VALDEZ CAFE", TransactionType.EXPENSE, "Comida")),
        ),
        categoriasDelDueno = listOf("Comida", "Salud", "Transporte"),
    )

    private inner class Repo(private val laBandeja: PropuestasDeLaBandeja) : RepositorioDePrueba() {
        override suspend fun getSmsMessages(): List<SmsMessage> = avisos.toList()
        override suspend fun getEventsByDay(): List<EventDay> = emptyList()
        override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = emptyList()
        override suspend fun getAccounts(): List<Account> = listOf(ahorros)
        override suspend fun getDestinos(): List<com.jvillada.movi.shared.model.DestinoConocido> = emptyList()
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        override suspend fun getPropuestasDeLaBandeja(): PropuestasDeLaBandeja =
            laBandeja.copy(propuestas = laBandeja.propuestas.filter { p -> avisos.any { it.id == p.avisoId && it.state == SMS_STATE_PENDING } })
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent = event
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

    /** Toca el botón cuyo texto es [texto] (el botón es el padre del texto: el `Boton` del sistema). */
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
        pendingSms = 3,
        porConfirmarSuman = 103_600L,
    )

    /** El Inicio y, al tocar la franja, Por revisar: como lo hace la app. */
    private fun montarDesdeElInicio(bandeja: PropuestasDeLaBandeja) {
        Repositories.sustitutoDePrueba = Repo(bandeja)
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
                        PorRevisarScreen(onNavigate = { navegado += it })
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun entrarAPorRevisar() {
        composeRule.onNodeWithTag(TAG_FRANJA_POR_CONFIRMAR, useUnmergedTree = true).performClick()
        toques++
        esperar { conTag(TAG_CONFIRMAR_EN_LA_FILA) == 3 }
    }

    @Test
    fun `la franja del Inicio dice cuanto suman los avisos`() {
        montarDesdeElInicio(propuestas("Salud"))
        assertEquals(1, cuantos("Por confirmar \$103.600"))
        assertEquals(1, cuantos("En 3 avisos"))
    }

    @Test
    fun `cada aviso dice lo que Movi entendio, el banco por su nombre, y Confirmar o Cambiar`() {
        montarDesdeElInicio(propuestas("Salud"))
        entrarAPorRevisar()

        assertEquals(3, conTag(TAG_LINEA_DE_LO_ENTENDIDO))
        assertEquals(1, cuantos("Transporte · Bancolombia Ahorros 4057"))
        assertEquals(1, cuantos("Salud · Bancolombia Ahorros 4057"))
        assertEquals(1, cuantos("Comida · Bancolombia Ahorros 4057"))
        // El banco por su nombre: «85540» no aparece en ninguna parte.
        assertEquals(1, cuantos("Bancolombia · 12:05"))
        assertEquals(0, cuantos("85540"))
        assertEquals(3, cuantos("Cambiar"))
        assertEquals(0, cuantos("Revisar"), "con todo entendido, el principal es «Confirmar»")
    }

    @Test
    fun `tres avisos que Movi entendio se confirman desde el Inicio en tres toques`() {
        montarDesdeElInicio(propuestas("Salud"))
        entrarAPorRevisar()

        tocarElBoton("Confirmar los 3")
        assertEquals(1, cuantos("¿Confirmar estos 3 avisos?"))
        assertEquals(1, cuantos("Total \$103.600"))
        tocarElBoton("Confirmar", elUltimo = true)
        esperar { confirmados.size == 3 }

        assertEquals(setOf("uber", "farma", "cafe"), confirmados.toSet())
        assertEquals(3, toques, "la franja, «Confirmar los 3» y el «Confirmar» de la hoja")
        esperar { cuantos("3 confirmados") == 1 }
    }

    @Test
    fun `uno solo se confirma de un toque y dice Anotado`() {
        montarDesdeElInicio(propuestas("Salud"))
        entrarAPorRevisar()

        composeRule.onAllNodesWithTag(TAG_CONFIRMAR_EN_LA_FILA)[0].performSemanticsAction(SemanticsActions.OnClick)
        esperar { confirmados.isNotEmpty() }
        assertEquals(listOf("uber"), confirmados)
        esperar { cuantos("Anotado · Uber Rides \$18.300") == 1 }
        esperar { conTag(TAG_CONFIRMAR_EN_LA_FILA) == 2 }
    }

    /**
     * Con Farmatodo en «Transporte», el error se ve en la tarjeta sin abrirla, y «Cambiar» abre Revisar
     * aviso con las categorías ya a la vista. Los toques: la franja (1), «Cambiar» (2), la categoría y
     * «Confirmar» en Revisar aviso (3 y 4) —que vuelve sola a Por revisar—, y «Confirmar los 2» con su
     * hoja (5 y 6).
     */
    @Test
    fun `el mal categorizado se ve sin abrirlo y Cambiar abre Revisar aviso con las categorias`() {
        montarDesdeElInicio(propuestas("Transporte"))
        entrarAPorRevisar()

        assertEquals(2, cuantos("Transporte · Bancolombia Ahorros 4057"), "Farmatodo dice «Transporte» a la vista")
        // Las tarjetas van de la más nueva a la más vieja: Uber, Farmatodo, Juan Valdez.
        composeRule.onAllNodes(hasText("Cambiar"), useUnmergedTree = true)[1].onParent()
            .performSemanticsAction(SemanticsActions.OnClick)
        toques++
        composeRule.waitForIdle()
        assertEquals(listOf<Screen>(Screen.SMSReconcile("farma", abrirCategoria = true)), navegado)
        assertTrue(toques == 2)
    }
}
