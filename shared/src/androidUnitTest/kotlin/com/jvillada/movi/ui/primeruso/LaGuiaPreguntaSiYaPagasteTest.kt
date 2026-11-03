package com.jvillada.movi.ui.primeruso

import com.jvillada.movi.ui.recurrentes.LO_ESPERA_DESDE_EL_PROXIMO_PERIODO
import com.jvillada.movi.ui.recurrentes.SI_YA_VENCIO_DESDE_EL_PROXIMO_PERIODO
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.DireccionDeCorreo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.ui.ESCALA_DEL_TELEFONO_AL_130
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
import com.jvillada.movi.ui.components.ds.tagDelDiaDelMes
import com.jvillada.movi.ui.recurrentes.YA_PAGASTE_EL_DE_ESTE_PERIODO
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 124 · la guía de primer uso tampoco crea pagos fijos vencidos
 *
 * La vuelta 121 hizo que la hoja de un pago fijo nuevo pregunte «¿Ya pagaste el de este período?» cuando
 * el vencimiento de este período ya pasó o es hoy. La guía («Lo que pagas cada mes») creaba el arriendo
 * sin preguntar: una cuenta nueva el día 2 con un arriendo del día 1 terminaba la guía con «Arriendo ·
 * vencido». Ahora el formulario de cada pago pregunta con la MISMA regla, «Sí» por defecto, y el `POST` de
 * siempre lleva `yaPagoElDeEstePeriodo`.
 *
 * «Hoy» fijo el lunes 2 de noviembre de 2026.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h3200dp-xhdpi")
class LaGuiaPreguntaSiYaPagasteTest {

    @get:Rule val composeRule = createComposeRule()

    private val dosDeNoviembre = LocalDate(2026, 11, 2)

    /** Una cuenta nueva: el perfil de siempre (corte 1, mes de calendario). */
    private val perfilNuevo = UserProfile(id = "u1", email = "nueva@movi.test", name = "Nueva", avatarColor = "#7C6CF0")

    private inner class Repo : RepositorioDePrueba() {
        val reglas = mutableListOf<RecurringRule>()
        val perfiles = mutableListOf<UpdateProfileRequest>()
        @Volatile var perfilLeido = 0
        /** Vuelta 125: la lectura del perfil falla. */
        var perfilFalla = false

        override suspend fun getUserProfile(): UserProfile {
            perfilLeido++
            if (perfilFalla) error("sin señal")
            return perfilNuevo
        }
        override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
            perfiles += request
            return perfilNuevo.copy(periodCutoffDay = request.periodCutoffDay ?: 1)
        }
        override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule {
            reglas += rule
            return rule.copy(id = "r${reglas.size}")
        }
        override suspend fun getDireccionDeCorreo(): DireccionDeCorreo =
            DireccionDeCorreo(token = "abc", direccion = "alertas+abc@movi.resend.app", configurado = true)
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun montar(paso: PasoDePrimerUso = PasoDePrimerUso.RECURRENTES, escala: Float = 1.12f, perfilFalla: Boolean = false): Repo {
        val repo = Repo().also { it.perfilFalla = perfilFalla }
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            ConClaseDeAncho(escala = escala) {
                Box(Modifier.fillMaxSize()) {
                    PrimerUsoScreen(
                        onNavigate = {},
                        pasoInicial = paso,
                        modoDeCaptura = ModoDeCaptura.SUBIR_UN_EXTRACTO,
                        hoy = dosDeNoviembre,
                    )
                }
            }
        }
        // El perfil dice cuál es «este período»: sin él la pregunta nunca sale, así que se espera primero.
        composeRule.waitUntil(5_000) { repo.perfilLeido > 0 }
        composeRule.waitForIdle()
        return repo
    }

    private fun escribirElArriendo(dia: Int) {
        composeRule.onNodeWithText("Arriendo").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("1200000")
        composeRule.onNodeWithTag(tagDelDiaDelMes(dia)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun hayPregunta(): Boolean =
        composeRule.onAllNodesWithTag(TAG_YA_PAGASTE_EN_LA_GUIA, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun guardarYSeguir(repo: Repo): RecurringRule {
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains(GUARDAR_Y_SEGUIR).performClick()
        composeRule.waitUntil(5_000) { repo.reglas.isNotEmpty() }
        composeRule.waitForIdle()
        return repo.reglas.single()
    }

    // ── Cuándo pregunta ────────────────────────────────────────────────────

    @Test
    fun `un pago cuyo dia de este periodo ya paso pregunta, con Si por defecto, y el POST lo lleva`() {
        val repo = montar()
        escribirElArriendo(dia = 1)

        assertTrue(hayPregunta(), "el arriendo del 1 ya venció el 2 de noviembre")
        assertTrue(composeRule.onAllNodesWithText(YA_PAGASTE_EL_DE_ESTE_PERIODO).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).assertIsOn()

        val regla = guardarYSeguir(repo)
        assertEquals("Arriendo", regla.name)
        assertEquals(true, regla.yaPagoElDeEstePeriodo, "con «Sí» la regla arranca en el período siguiente")
    }

    @Test
    fun `tambien pregunta si vence hoy`() {
        montar()
        escribirElArriendo(dia = 2)
        assertTrue(hayPregunta(), "el 2 de noviembre vence hoy")
    }

    @Test
    fun `con No el POST no lleva el ya pague`() {
        val repo = montar()
        escribirElArriendo(dia = 1)
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).assertIsOff()

        val regla = guardarYSeguir(repo)
        // Como la hoja de la 121: la respuesta va explícita, y `false` el server lo lee como sin el campo.
        assertEquals(false, regla.yaPagoElDeEstePeriodo)
    }

    @Test
    fun `un pago cuyo dia todavia no llega no pregunta ni manda el campo`() {
        val repo = montar()
        escribirElArriendo(dia = 5)
        assertFalse(hayPregunta(), "el 5 de noviembre todavía no vence")

        val regla = guardarYSeguir(repo)
        assertNull(regla.yaPagoElDeEstePeriodo)
    }

    @Test
    fun `cambiar el dia a uno por venir quita la pregunta`() {
        montar()
        escribirElArriendo(dia = 1)
        assertTrue(hayPregunta())
        composeRule.onNodeWithTag(tagDelDiaDelMes(20)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(hayPregunta())
    }

    @Test
    fun `con No, pasar a un dia por venir y volver la pregunta vuelve a Si`() {
        val repo = montar()
        escribirElArriendo(dia = 1)
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).assertIsOff()

        composeRule.onNodeWithTag(tagDelDiaDelMes(20)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(hayPregunta())
        composeRule.onNodeWithTag(tagDelDiaDelMes(1)).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).assertIsOn()
        assertEquals(true, guardarYSeguir(repo).yaPagoElDeEstePeriodo)
    }

    @Test
    fun `con No, pasar a otro dia que tambien ya paso la conserva`() {
        val repo = montar()
        escribirElArriendo(dia = 1)
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tagDelDiaDelMes(2)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).assertIsOff()
        assertEquals(false, guardarYSeguir(repo).yaPagoElDeEstePeriodo)
    }

    @Test
    fun `se toca el renglon entero, no solo la casilla`() {
        montar()
        escribirElArriendo(dia = 1)
        composeRule.onNodeWithText(YA_PAGASTE_EL_DE_ESTE_PERIODO).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).assertIsOff()
    }

    // ── El período que se acaba de elegir ──────────────────────────────────

    @Test
    fun `usa el corte elegido en Tu periodo, no el mes de calendario`() {
        val repo = montar(paso = PasoDePrimerUso.PERIODO)
        // Corte 25 con «Es el día que me pagan»: el período en curso va del 23 de octubre al 24 de noviembre.
        composeRule.onNodeWithText("25").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).performClick()
        composeRule.waitUntil(5_000) { repo.perfiles.isNotEmpty() }
        composeRule.waitForIdle()
        assertEquals(25, repo.perfiles.single().periodCutoffDay)
        // Cuentas y captura se saltan.
        composeRule.onNodeWithTag(TAG_SALTAR_EN_LA_GUIA).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_SALTAR_EN_LA_GUIA).performClick()
        composeRule.waitForIdle()

        // El 28 de octubre está dentro del período en curso y ya pasó; con el mes de calendario sería el
        // 28 de noviembre, que no ha llegado.
        escribirElArriendo(dia = 28)
        assertTrue(hayPregunta(), "con corte 25, el arriendo del 28 de este período ya venció")
        assertEquals(true, guardarYSeguir(repo).yaPagoElDeEstePeriodo)
    }

    /** Vuelta 125: sin perfil (la lectura falló) y sin período guardado en la guía, el mes de calendario. */
    @Test
    fun `si la lectura del perfil falla pregunta con el mes de calendario`() {
        val repo = montar(perfilFalla = true)
        escribirElArriendo(dia = 1)
        assertTrue(hayPregunta())
        // Revisión de la 125: con el respaldo, «Sí» no afirma «desde el próximo período».
        assertTrue(composeRule.onAllNodesWithText(SI_YA_VENCIO_DESDE_EL_PROXIMO_PERIODO).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText(LO_ESPERA_DESDE_EL_PROXIMO_PERIODO).fetchSemanticsNodes().isEmpty())
        assertEquals(true, guardarYSeguir(repo).yaPagoElDeEstePeriodo)
    }

    @Test
    fun `con el mes de calendario el mismo dia 28 no pregunta`() {
        montar()
        escribirElArriendo(dia = 28)
        assertFalse(hayPregunta())
    }

    @Test
    fun `los ajustes de la pregunta son los guardados en la guia, o los del perfil`() {
        val guardados = ajustesDeLaGuia(25, SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
        val conExcepcion = perfilNuevo.copy(periodStarts = mapOf("2026-09" to "2026-09-20"))
        assertEquals(guardados, ajustesParaLaPreguntaDeLaGuia(guardados, perfilNuevo, seLeyoElPerfil = true))
        assertEquals(mapOf("2026-09" to "2026-09-20"), ajustesParaLaPreguntaDeLaGuia(guardados, conExcepcion, seLeyoElPerfil = true)?.iniciosPropios)
        assertEquals(25, ajustesParaLaPreguntaDeLaGuia(guardados, conExcepcion, seLeyoElPerfil = true)?.cutoffDay)
        assertEquals(PeriodSettings(), ajustesParaLaPreguntaDeLaGuia(null, perfilNuevo, seLeyoElPerfil = true))
        assertNull(ajustesParaLaPreguntaDeLaGuia(null, null, seLeyoElPerfil = false), "mientras se lee el perfil, no se pregunta")
        // Vuelta 125: la lectura del perfil falló y no guardó período en la guía: el mes de calendario.
        assertEquals(PeriodSettings(), ajustesParaLaPreguntaDeLaGuia(null, null, seLeyoElPerfil = true))
        // Con un período guardado en la guía, ese manda aunque el perfil no esté.
        assertEquals(guardados, ajustesParaLaPreguntaDeLaGuia(guardados, null, seLeyoElPerfil = true))
        // Revisión de la 125: cuándo esos ajustes son el respaldo.
        assertTrue(laGuiaPreguntaConElPeriodoDeRespaldo(null, null, seLeyoElPerfil = true))
        assertFalse(laGuiaPreguntaConElPeriodoDeRespaldo(null, null, seLeyoElPerfil = false))
        assertFalse(laGuiaPreguntaConElPeriodoDeRespaldo(guardados, null, seLeyoElPerfil = true))
        assertFalse(laGuiaPreguntaConElPeriodoDeRespaldo(null, perfilNuevo, seLeyoElPerfil = true))
    }

    // ── El borrador la recuerda ────────────────────────────────────────────

    @Test
    fun `la respuesta sobrevive a Atras y Siguiente`() {
        val repo = montar()
        escribirElArriendo(dia = 1)
        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_ATRAS_EN_LA_GUIA).performClick()
        composeRule.waitForIdle()
        assertFalse(hayPregunta(), "se fue al paso de la captura")
        composeRule.onNodeWithTag(TAG_AVANZAR_EN_LA_GUIA).assertTextContains("Siguiente").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_YA_PAGASTE_EN_LA_GUIA).assertIsOff()
        assertEquals(false, guardarYSeguir(repo).yaPagoElDeEstePeriodo)
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

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
    fun `a 360 con la letra grande nada se corta y hay un solo amarillo`() {
        montar(escala = ESCALA_DEL_TELEFONO_AL_130)
        escribirElArriendo(dia = 1)
        assertTrue(hayPregunta())

        val nodos = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val recortados = nodos.filterNot(::seVeEntero).map(::texto)
        assertTrue(recortados.isEmpty(), "se recortan $recortados")
        assertTrue(nodos.any { texto(it) == YA_PAGASTE_EL_DE_ESTE_PERIODO }, "la pregunta está entre los textos medidos")

        val amarillos = composeRule.onAllNodes(SemanticsMatcher.expectValue(LlevaElAmarilloKey, true), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(1, amarillos.size, "solo «Guardar y seguir» lleva el amarillo")
    }
}
