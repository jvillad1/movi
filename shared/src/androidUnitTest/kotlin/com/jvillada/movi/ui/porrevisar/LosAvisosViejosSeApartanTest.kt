package com.jvillada.movi.ui.porrevisar

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestaDelAviso
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * # Los avisos viejos se apartan (vuelta 51)
 *
 * El Rappi del 2 de septiembre seguía en «Por revisar» dos meses después. Los de más de 30 días van en
 * «Más antiguos · N», plegada, con «Ignorar los N» y su pregunta; no entran en «Confirmar los N».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class LosAvisosViejosSeApartanTest {

    @get:Rule val composeRule = createComposeRule()

    private val hoy = hoyEnAppZone()
    private val haceDosMeses = hoy.minus(DatePeriod(days = 56))

    private fun aviso(id: String, dia: String, monto: String, comercio: String, hora: String) = SmsMessage(
        id = id, time = "$dia $hora", bank = "85540", state = SMS_STATE_PENDING, det = "",
        text = "Bancolombia: Compraste \$$monto,00 en $comercio con tu T.Deb *4057 a las $hora.",
    )

    private val avisos = mutableListOf(
        aviso("uber", hoy.toString(), "18.300", "UBER RIDES", "08:05"),
        aviso("cafe", hoy.toString(), "23.400", "JUAN VALDEZ CAFE", "07:12"),
        aviso("rappi", haceDosMeses.toString(), "46.000", "RAPPI", "19:40"),
        aviso("pana", haceDosMeses.toString(), "54.000", "PANAMERICANA", "14:05"),
    )
    private val ahorros = Account("acc-4057", "Bancolombia Ahorros 4057", AccountType.SAVINGS, 3_000_000L, "COP")
    private val ignorados = mutableListOf<String>()

    private val propuestas = PropuestasDeLaBandeja(
        propuestas = listOf(
            PropuestaDelAviso("uber", leido = ParsedSms(18_300.0, "UBER RIDES", TransactionType.EXPENSE, "Transporte")),
            PropuestaDelAviso("cafe", leido = ParsedSms(23_400.0, "JUAN VALDEZ CAFE", TransactionType.EXPENSE, "Comida")),
            PropuestaDelAviso("rappi", leido = ParsedSms(46_000.0, "RAPPI", TransactionType.EXPENSE, "Comida")),
            PropuestaDelAviso("pana", leido = ParsedSms(54_000.0, "PANAMERICANA", TransactionType.EXPENSE, "Otros")),
        ),
        categoriasDelDueno = listOf("Comida", "Transporte"),
    )

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
        override suspend fun ignoreSms(id: String) {
            ignorados += id
            avisos.replaceAll { if (it.id == id) it.copy(state = SMS_STATE_IGNORED) else it }
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun cuantos(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun esperar(condicion: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { condicion() }
        composeRule.waitForIdle()
    }

    private fun tocarElBoton(texto: String) {
        composeRule.onAllNodes(hasText(texto), useUnmergedTree = true).onLast().onParent()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun montar() {
        Repositories.sustitutoDePrueba = Repo()
        composeRule.setContent { MoviTheme { PorRevisarScreen(onNavigate = {}) } }
        esperar { composeRule.onAllNodesWithTag(TAG_SECCION_DE_LOS_ANTIGUOS, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `los de mas de 30 dias van plegados en Mas antiguos y no entran en el lote`() {
        montar()
        assertEquals(1, cuantos("Mensajes del banco"))
        assertEquals(1, cuantos(TITULO_DE_LOS_ANTIGUOS))
        // Plegada: Rappi y Panamericana no se ven hasta abrirla.
        assertEquals(0, cuantos("Rappi"))
        assertEquals(0, cuantos("Panamericana"))
        // El lote es de los de hoy: «Confirmar los 2», no 3 ni 4.
        esperar { cuantos("Confirmar los 2") > 0 }
        assertEquals(0, cuantos("Confirmar los 3"))

        // La acción de la `Seccion` es el texto mismo, tocable.
        composeRule.onAllNodes(hasText(VER_LOS_ANTIGUOS) and hasClickAction(), useUnmergedTree = true).onLast()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        esperar { cuantos("Rappi") > 0 }
        assertEquals(1, cuantos("Panamericana"))
    }

    @Test
    fun `Ignorar los N pregunta antes y usa el Ignorar de siempre`() {
        montar()
        composeRule.onNodeWithTag(TAG_IGNORAR_LOS_ANTIGUOS, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(1, cuantos(preguntaDeIgnorarLosAntiguos(2)))
        assertEquals(0, ignorados.size, "nada se ignora sin la respuesta")
        tocarElBoton(textoDeIgnorarLosAntiguos(2))
        esperar { ignorados.size == 2 }
        assertEquals(setOf("rappi", "pana"), ignorados.toSet())
        esperar { cuantos(TITULO_DE_LOS_ANTIGUOS) == 0 }
        // Los de hoy siguen ahí.
        assertEquals(1, cuantos("Mensajes del banco"))
    }
}
