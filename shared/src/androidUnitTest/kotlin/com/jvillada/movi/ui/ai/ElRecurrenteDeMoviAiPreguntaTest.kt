package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.AccionPropuesta
import com.jvillada.movi.shared.model.EstadoDePropuesta
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TipoDeAccion
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.recurrentes.YA_PAGASTE_EL_DE_ESTE_PERIODO
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 124 · lo que propone Movi AI tampoco nace vencido
 *
 * `proponer_recurrente` llega como una tarjeta con «Hacerlo», que llama al `createRecurringRule` de
 * siempre. La vuelta 121 dejó la pregunta «¿Ya pagaste el de este período?» solo en la hoja; ahora la
 * tarjeta la hace con la misma regla (un gasto, no desde un movimiento, cuyo vencimiento de este período ya
 * pasó o es hoy), «Sí» por defecto, y «Hacerlo» manda la respuesta.
 *
 * «Hoy» fijo el 2 de noviembre de 2026, con el mes de calendario como período.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class ElRecurrenteDeMoviAiPreguntaTest {

    @get:Rule val composeRule = createComposeRule()

    private val dosDeNoviembre = LocalDate(2026, 11, 2)

    private fun propuesta(regla: RecurringRule) = AccionPropuesta(
        id = "ap_rec",
        tipo = TipoDeAccion.CREAR_RECURRENTE,
        frase = "Crear el pago recurrente «${regla.name}» de \$120.000, el día ${regla.dayOfMonth} de cada mes, en Gimnasio",
        recurrente = regla,
        categoria = "Gimnasio",
    )

    private fun gimnasio(dia: Int, tipo: TransactionType = TransactionType.EXPENSE) = RecurringRule(
        id = "", name = "Gimnasio", category = "Gimnasio", amount = 120_000L, dayOfMonth = dia, type = tipo,
    )

    private val creadas = mutableListOf<RecurringRule>()
    @Volatile private var perfilLeido = 0

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile =
            UserProfile(id = "u", email = "claudia@movi.test", name = "Claudia", avatarColor = "#FFC21A", periodCutoffDay = 1)
                .also { perfilLeido++ }
        override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule {
            creadas += rule
            return rule.copy(id = "rr_nueva")
        }
        override suspend fun resolverPropuesta(id: String, estado: EstadoDePropuesta) = Unit
    }

    private fun pintar(p: AccionPropuesta, esperarElPerfil: Boolean = true) {
        creadas.clear()
        perfilLeido = 0
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    var estado by remember { mutableStateOf(EstadoDePropuesta.PENDIENTE) }
                    PropuestaDelAsistente(p, estado, onEstado = { estado = it }, repo = { repo }, hoy = dosDeNoviembre)
                }
            }
        }
        if (esperarElPerfil) composeRule.waitUntil(5_000) { perfilLeido > 0 }
        composeRule.waitForIdle()
    }

    private fun hayPregunta(): Boolean =
        composeRule.onAllNodesWithTag(TAG_YA_PAGASTE_EN_LA_PROPUESTA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun hacerlo(): RecurringRule {
        composeRule.onNode(hasClickAction() and hasAnyChild(hasText(HACERLO)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { creadas.isNotEmpty() }
        composeRule.waitForIdle()
        return creadas.single()
    }

    @Test
    fun `un gasto cuyo dia ya paso pregunta con Si por defecto, y Hacerlo lo manda`() {
        pintar(propuesta(gimnasio(dia = 1)))
        assertTrue(hayPregunta())
        assertTrue(composeRule.onAllNodesWithText(YA_PAGASTE_EL_DE_ESTE_PERIODO).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_PROPUESTA).assertIsOn()

        val regla = hacerlo()
        assertEquals(true, regla.yaPagoElDeEstePeriodo)
        assertEquals("Gimnasio", regla.name, "lo demás va tal cual lo propuso el server")
        assertEquals(1, regla.dayOfMonth)
    }

    @Test
    fun `con No Hacerlo no manda el ya pague`() {
        pintar(propuesta(gimnasio(dia = 2)))
        assertTrue(hayPregunta(), "vence hoy")
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_PROPUESTA).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_PROPUESTA).assertIsOff()

        assertEquals(false, hacerlo().yaPagoElDeEstePeriodo)
    }

    @Test
    fun `un gasto por venir no pregunta y la regla va sin el campo`() {
        pintar(propuesta(gimnasio(dia = 20)))
        assertFalse(hayPregunta())
        assertNull(hacerlo().yaPagoElDeEstePeriodo)
    }

    @Test
    fun `un ingreso no pregunta`() {
        pintar(propuesta(gimnasio(dia = 1, tipo = TransactionType.INCOME)))
        assertFalse(hayPregunta())
        assertNull(hacerlo().yaPagoElDeEstePeriodo)
    }

    @Test
    fun `lo que nace de un movimiento ya anotado no pregunta`() {
        pintar(propuesta(gimnasio(dia = 1).copy(eventoDeOrigen = "ev_gym", activeFrom = "2026-11-01")))
        assertFalse(hayPregunta())
        assertNull(hacerlo().yaPagoElDeEstePeriodo)
    }

    @Test
    fun `la regla pura es la de la hoja`() {
        val calendario = PeriodSettings()
        assertTrue(laPropuestaPreguntaSiYaPago(propuesta(gimnasio(dia = 1)), dosDeNoviembre, calendario))
        assertTrue(laPropuestaPreguntaSiYaPago(propuesta(gimnasio(dia = 2)), dosDeNoviembre, calendario))
        assertFalse(laPropuestaPreguntaSiYaPago(propuesta(gimnasio(dia = 3)), dosDeNoviembre, calendario))
        assertFalse(laPropuestaPreguntaSiYaPago(propuesta(gimnasio(dia = 1)), dosDeNoviembre, ajustes = null), "sin el perfil no se pregunta")
        // Con corte 25, el 28 de octubre ya pasó dentro del período en curso.
        assertTrue(laPropuestaPreguntaSiYaPago(propuesta(gimnasio(dia = 28)), dosDeNoviembre, PeriodSettings(cutoffDay = 25)))
        assertFalse(laPropuestaPreguntaSiYaPago(propuesta(gimnasio(dia = 28)), dosDeNoviembre, calendario))
        val recordar = AccionPropuesta(id = "ap_m", tipo = TipoDeAccion.RECORDAR, frase = "Recordar", recuerdo = "x")
        assertFalse(laPropuestaPreguntaSiYaPago(recordar, dosDeNoviembre, calendario))
    }
}
