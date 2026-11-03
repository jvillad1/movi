package com.jvillada.movi.ui.ai

import com.jvillada.movi.ui.recurrentes.LO_ESPERA_EN_ESTE_PERIODO
import com.jvillada.movi.ui.recurrentes.LO_ESPERA_DESDE_EL_PROXIMO_PERIODO
import com.jvillada.movi.ui.recurrentes.SI_YA_VENCIO_DESDE_EL_PROXIMO_PERIODO
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
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.abs
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import org.robolectric.annotation.GraphicsMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
    /** Revisión de la 124: con esto el perfil se demora hasta que la prueba lo suelta. */
    private var perfilDemorado: CompletableDeferred<Unit>? = null
    /** Vuelta 125: la lectura del perfil falla. */
    private var perfilFalla = false

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getUserProfile(): UserProfile {
            perfilDemorado?.await()
            if (perfilFalla) { perfilLeido++; error("sin señal") }
            return UserProfile(id = "u", email = "claudia@movi.test", name = "Claudia", avatarColor = "#FFC21A", periodCutoffDay = 1)
                .also { perfilLeido++ }
        }
        override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule {
            creadas += rule
            return rule.copy(id = "rr_nueva")
        }
        override suspend fun resolverPropuesta(id: String, estado: EstadoDePropuesta) = Unit
    }

    private fun pintar(p: AccionPropuesta, esperarElPerfil: Boolean = true, escala: Float = 1f) {
        creadas.clear()
        perfilLeido = 0
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * escala)) {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    var estado by remember { mutableStateOf(EstadoDePropuesta.PENDIENTE) }
                    PropuestaDelAsistente(p, estado, onEstado = { estado = it }, repo = { repo }, hoy = dosDeNoviembre)
                }
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

    // ── Revisión de la 124 ─────────────────────────────────────────────────

    private fun botonHacerlo() = composeRule.onNode(hasClickAction() and hasAnyChild(hasText(HACERLO)), useUnmergedTree = true)

    private fun arribaDeHacerlo(): Float = botonHacerlo().getUnclippedBoundsInRoot().top.value

    /** Con el perfil demorado: «Hacerlo» apagado, y al llegar el perfil los botones no se mueven más de 8 dp. */
    private fun conElPerfilDemorado(dia: Int, alLlegar: () -> Unit) {
        perfilDemorado = CompletableDeferred()
        pintar(propuesta(gimnasio(dia = dia)), esperarElPerfil = false)
        botonHacerlo().performClick()
        composeRule.waitForIdle()
        assertTrue(creadas.isEmpty(), "sin saber si se pregunta, «Hacerlo» no crea la regla sin el campo: $creadas")
        botonHacerlo().assertIsNotEnabled()
        assertTrue(composeRule.onAllNodesWithTag(TAG_ESPERANDO_EL_PERFIL_EN_LA_PROPUESTA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val antes = arribaDeHacerlo()

        perfilDemorado?.complete(Unit)
        composeRule.waitUntil(5_000) { perfilLeido > 0 }
        composeRule.waitForIdle()
        alLlegar()
        val despues = arribaDeHacerlo()
        assertTrue(abs(despues - antes) <= 8f, "los botones saltaron de $antes a $despues dp")
        botonHacerlo().assertIsEnabled()
    }

    @Test
    fun `con el perfil demorado Hacerlo espera, y la pregunta aparece sin mover los botones`() {
        conElPerfilDemorado(dia = 1) { assertTrue(hayPregunta()) }
        assertEquals(true, hacerlo().yaPagoElDeEstePeriodo)
    }

    @Test
    fun `con el perfil demorado y la fecha por venir tampoco salta`() {
        conElPerfilDemorado(dia = 20) {
            assertFalse(hayPregunta())
            assertTrue(composeRule.onAllNodesWithTag(TAG_LO_ESPERA_EN_LA_PROPUESTA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        }
        assertNull(hacerlo().yaPagoElDeEstePeriodo)
    }

    @Test
    fun `se toca el renglon entero, no solo la casilla`() {
        pintar(propuesta(gimnasio(dia = 1)))
        composeRule.onNodeWithText(YA_PAGASTE_EL_DE_ESTE_PERIODO).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_PROPUESTA).assertIsOff()
        assertEquals(false, hacerlo().yaPagoElDeEstePeriodo)
    }

    /**
     * Vuelta 125: si la lectura del perfil falla, la tarjeta pregunta con el mes de calendario (antes no
     * preguntaba y la regla nacía vencida sin aviso). «Hacerlo» manda la respuesta.
     */
    @Test
    fun `si la lectura del perfil falla pregunta con el mes de calendario`() {
        perfilFalla = true
        pintar(propuesta(gimnasio(dia = 1)))
        assertTrue(hayPregunta(), "sin perfil, el período por defecto: el 1 de noviembre ya pasó")
        // Revisión de la 125: con el respaldo, «Sí» no afirma «desde el próximo período».
        assertTrue(composeRule.onAllNodesWithText(SI_YA_VENCIO_DESDE_EL_PROXIMO_PERIODO).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText(LO_ESPERA_DESDE_EL_PROXIMO_PERIODO).fetchSemanticsNodes().isEmpty())
        assertEquals(true, hacerlo().yaPagoElDeEstePeriodo)
    }

    /**
     * Revisión de la 125: con el período de respaldo la tarjeta no afirma «Movi lo espera en este período»
     * (con corte 25, el 28-oct ya venció aunque el 28-nov no): el lugar queda vacío y la regla va sin el campo.
     */
    @Test
    fun `si la lectura del perfil falla y la fecha no llega en el mes, no afirma nada`() {
        perfilFalla = true
        pintar(propuesta(gimnasio(dia = 28)))
        assertFalse(hayPregunta())
        assertTrue(composeRule.onAllNodesWithTag(TAG_LO_ESPERA_EN_LA_PROPUESTA, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(LO_ESPERA_EN_ESTE_PERIODO).fetchSemanticsNodes().isEmpty())
        assertNull(hacerlo().yaPagoElDeEstePeriodo)
    }

    @Test
    fun `la regla del lugar de la pregunta`() {
        val calendario = PeriodSettings()
        assertEquals(LugarDeLaPregunta.LEYENDO, lugarDeLaPregunta(propuesta(gimnasio(1)), dosDeNoviembre, null, leyendoElPerfil = true))
        assertEquals(LugarDeLaPregunta.NINGUNO, lugarDeLaPregunta(propuesta(gimnasio(1)), dosDeNoviembre, null, leyendoElPerfil = false), "sin ajustes y sin estar leyendo: la tarjeta nunca llega acá (vuelta 125)")
        assertEquals(LugarDeLaPregunta.PREGUNTA, lugarDeLaPregunta(propuesta(gimnasio(1)), dosDeNoviembre, calendario, leyendoElPerfil = false))
        assertEquals(LugarDeLaPregunta.EN_ESTE_PERIODO, lugarDeLaPregunta(propuesta(gimnasio(20)), dosDeNoviembre, calendario, leyendoElPerfil = false))
        // Revisión de la 125: con el período de respaldo pregunta igual, pero no afirma «lo espera».
        assertEquals(
            LugarDeLaPregunta.PREGUNTA,
            lugarDeLaPregunta(propuesta(gimnasio(1)), dosDeNoviembre, calendario, leyendoElPerfil = false, conElPeriodoDeRespaldo = true),
        )
        assertEquals(
            LugarDeLaPregunta.NINGUNO,
            lugarDeLaPregunta(propuesta(gimnasio(28)), dosDeNoviembre, calendario, leyendoElPerfil = false, conElPeriodoDeRespaldo = true),
        )
        assertEquals(LugarDeLaPregunta.NINGUNO, lugarDeLaPregunta(propuesta(gimnasio(1, TransactionType.INCOME)), dosDeNoviembre, null, leyendoElPerfil = true))
        assertEquals(
            LugarDeLaPregunta.NINGUNO,
            lugarDeLaPregunta(propuesta(gimnasio(1).copy(eventoDeOrigen = "ev")), dosDeNoviembre, null, leyendoElPerfil = true),
        )
    }

    // ── A 360 con la letra grande ──────────────────────────────────────────

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val d = resultados.singleOrNull() ?: return true
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    private fun nadaSeRecorta(contexto: String) {
        val nodos = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val recortados = nodos.filterNot(::seVeEntero).map(::texto)
        assertTrue(recortados.isEmpty(), "$contexto: se recortan $recortados")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 con la letra grande la tarjeta con la pregunta no corta nada`() {
        pintar(propuesta(gimnasio(dia = 1)), escala = ESCALA_DEL_TELEFONO_AL_130)
        assertTrue(hayPregunta())
        nadaSeRecorta("con la pregunta")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 con la letra grande Movi lo espera en este periodo no corta nada`() {
        pintar(propuesta(gimnasio(dia = 20)), escala = ESCALA_DEL_TELEFONO_AL_130)
        nadaSeRecorta("con «Movi lo espera en este período»")
    }
}
