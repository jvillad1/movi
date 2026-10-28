package com.jvillada.movi.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.jvillada.movi.shared.model.AhorroDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PasswordPolicy
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.TipoDeAhorro
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.mas.HojaDeAlertasPorCorreo
import com.jvillada.movi.ui.mas.RECIBE_TUS_ALERTAS_POR_CORREO
import com.jvillada.movi.ui.primeruso.DireccionDeMovi
import com.jvillada.movi.ui.components.ds.TAG_BOTON
import com.jvillada.movi.ui.components.ds.TAG_ENCABEZADO_DE_HOJA
import com.jvillada.movi.ui.components.ds.TAG_GRUPO_DE_CAMPOS
import com.jvillada.movi.ui.components.ds.TAG_GUIA_DEL_CAMPO
import com.jvillada.movi.ui.components.ds.TAG_VISTA_PREVIA
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Vuelta 41: las hojas de «Tú» en el sistema
 *
 * Cada hoja de `ui/profile` y `ui/mas` abre con un
 * `EncabezadoDeHoja` con su título, **ningún texto de adentro es más grande que el título** (la hoja
 * «Inicio del período» tenía «Si ese día cae en fin de semana o festivo» y «Hoy estarías en octubre»
 * más fuertes que «¿Qué día empieza tu período?»), lleva **una** acción principal en su pie y ningún
 * «Cancelar»: la × y el arrastre ya cancelan.
 *
 * Motor de texto real (`NATIVE`, sdk 34) para leer el estilo con que se pinta cada texto, y la letra
 * de la app (×1,12).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h2400dp-xhdpi")
class LasHojasDeTuSonDelSistemaTest {

    @get:Rule val composeRule = createComposeRule()

    private fun montar(contenido: @Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * 1.12f)) {
                MoviTheme { Box(Modifier.fillMaxSize()) { contenido() } }
            }
        }
        composeRule.waitForIdle()
    }

    private fun textoDe(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Lo que pinta cada texto de la pantalla: su texto, su tamaño (sp) y su peso. */
    private data class TextoPintado(val texto: String, val tamano: Float, val peso: Int, val enElEncabezado: Boolean)

    private fun textosPintados(): List<TextoPintado> {
        val delEncabezado = composeRule.onAllNodes(hasAnyAncestor(hasTestTag(TAG_ENCABEZADO_DE_HOJA)), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.id }.toSet()
        return composeRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().mapNotNull { nodo ->
            val resultados = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
            val estilo = resultados.firstOrNull()?.layoutInput?.style ?: return@mapNotNull null
            TextoPintado(
                texto = textoDe(nodo),
                tamano = estilo.fontSize.value,
                peso = (estilo.fontWeight ?: FontWeight.Normal).weight,
                enElEncabezado = nodo.id in delEncabezado,
            )
        }
    }

    /** El título del encabezado de hoja, y que hay uno solo. */
    private fun tituloDeLaHoja(): TextoPintado {
        val encabezados = composeRule.onAllNodes(hasTestTag(TAG_ENCABEZADO_DE_HOJA), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, encabezados.size, "un solo EncabezadoDeHoja")
        val delEncabezado = textosPintados().filter { it.enElEncabezado }
        return delEncabezado.first()
    }

    private fun botones(): List<Pair<String, VarianteDeBoton?>> =
        composeRule.onAllNodes(hasTestTag(TAG_BOTON)).fetchSemanticsNodes().map { nodo ->
            textoDe(nodo) to nodo.config.getOrNull(VarianteDeBotonKey)
        }

    /** Lo que vale para cada hoja de «Tú»; [accion] `null` es una hoja que solo informa. */
    private fun esUnaHojaDelSistema(titulo: String, accion: String?) {
        val elTitulo = tituloDeLaHoja()
        assertEquals(titulo, elTitulo.texto, "el título de la hoja")
        textosPintados().filterNot { it.enElEncabezado }.forEach { otro ->
            val loSupera = otro.tamano > elTitulo.tamano || (otro.tamano == elTitulo.tamano && otro.peso > elTitulo.peso)
            assertTrue(!loSupera, "«${otro.texto}» (${otro.tamano} sp, ${otro.peso}) supera al título (${elTitulo.tamano} sp, ${elTitulo.peso})")
        }
        val principales = botones().filter { it.second == VarianteDeBoton.Principal }
        assertEquals(listOfNotNull(accion), principales.map { it.first }, "una sola acción principal: ${botones()}")
        assertTrue(botones().none { it.first == "Cancelar" }, "sin «Cancelar»: ${botones()}")
        assertTrue(textosPintados().none { it.texto == "Cancelar" }, "sin «Cancelar» suelto")
    }

    private fun vistaPrevia(): List<String> =
        composeRule.onAllNodes(hasAnyAncestor(hasTestTag(TAG_VISTA_PREVIA)), useUnmergedTree = true)
            .fetchSemanticsNodes().map(::textoDe).filter { it.isNotEmpty() }

    // ── Inicio del período ─────────────────────────────────────────────────────────────────

    @Test
    fun `Inicio del periodo tiene su titulo arriba de todo, una vista previa de dos renglones y Guardar`() {
        montar { PeriodoSheet(cutoffActual = 25, reglaActual = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR, onDismiss = {}, onSave = { _, _ -> }) }
        esUnaHojaDelSistema(PREGUNTA_DEL_INICIO_DEL_PERIODO, "Guardar")
        val previa = vistaPrevia()
        assertTrue(previa.size in 1..2, "la vista previa dice dos renglones como mucho: $previa")
        assertTrue(previa.first().startsWith("Tu período: del "), previa.first())
        // El grupo de la regla es un GrupoDeCampos, no un texto suelto en `titulo`.
        assertTrue(
            composeRule.onAllNodes(hasTestTag(TAG_GRUPO_DE_CAMPOS), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
            "el grupo de la regla",
        )
        // Lo que decía en tres renglones ya no está.
        assertTrue(textosPintados().none { it.texto.startsWith("Hoy estarías en") || it.texto.startsWith("Es el mes de calendario") })
    }

    @Test
    fun `Inicio del periodo con mes de calendario no agrega renglones`() {
        montar { PeriodoSheet(cutoffActual = 1, onDismiss = {}, onSave = { _, _ -> }) }
        assertEquals(1, vistaPrevia().size, "con el mes de calendario la vista previa es un renglón: ${vistaPrevia()}")
    }

    // ── ¿Cuándo empezó…? ───────────────────────────────────────────────────────────────────

    @Test
    fun `cuando empezo un periodo abre con la pregunta y su accion es una sola`() {
        montar {
            InicioDelPeriodoSheet(
                periodo = PeriodoFinanciero(2026, 10),
                ajustes = PeriodSettings(cutoffDay = 25),
                onDismiss = {},
                onSave = {},
                onVerPeriodos = {},
            )
        }
        esUnaHojaDelSistema("¿Cuándo empezó octubre de 2026?", "Este mes empezó el 25")
        assertEquals(
            listOf("Octubre: del 25 de septiembre al 24 de octubre", "El período anterior se cierra el 24 de septiembre."),
            vistaPrevia(),
        )
    }

    // ── Ahorro de cada período ─────────────────────────────────────────────────────────────

    @Test
    fun `Ahorro de cada periodo guarda sin Cancelar`() {
        montar { AhorroDelPeriodoSheet(actual = AhorroDelPeriodo(TipoDeAhorro.MONTO, 500_000), onDismiss = {}, onSave = {}) }
        esUnaHojaDelSistema(AHORRO_DE_CADA_PERIODO, "Guardar")
    }

    // ── Días de aviso ──────────────────────────────────────────────────────────────────────

    @Test
    fun `los dias de aviso guardan con el pie del sistema`() {
        montar { DiasDeAvisoSheet(diasActuales = 3, onDismiss = {}, onSave = {}) }
        esUnaHojaDelSistema("¿Con cuánta anticipación te avisamos?", "Guardar")
    }

    // ── Tu perfil ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `Tu perfil guarda con el pie del sistema`() {
        montar { EditProfileSheet(initialName = "Juan Villada", initialColor = null, onDismiss = {}, onSaved = {}) }
        esUnaHojaDelSistema("Tu perfil", "Guardar")
    }

    // ── Cambiar contraseña ─────────────────────────────────────────────────────────────────

    @Test
    fun `Cambiar contrasena abre con su titulo y cambia con el pie del sistema`() {
        montar { ChangePasswordSheet(onDismiss = {}, onSaved = {}) }
        esUnaHojaDelSistema(CAMBIAR_CONTRASENA, CAMBIAR_CONTRASENA)
    }

    @Test
    fun `ninguna guia de Cambiar contrasena es solo puntos`() {
        montar { ChangePasswordSheet(onDismiss = {}, onSaved = {}) }
        val guias = composeRule.onAllNodes(hasTestTag(TAG_GUIA_DEL_CAMPO), useUnmergedTree = true)
            .fetchSemanticsNodes().map(::textoDe)
        assertEquals(3, guias.size, "los tres campos vacíos muestran su guía: $guias")
        assertTrue(guias.none { g -> g.isNotBlank() && g.all { it == '•' || it == '·' || it == '.' || it.isWhitespace() } }, "una guía de puntos parece un campo lleno: $guias")
        assertTrue("Al menos ${PasswordPolicy.MIN_LENGTH} caracteres" in guias, "la guía de la nueva dice la regla del server: $guias")
    }

    // ── Recibe tus alertas por correo (`ui/mas`) ───────────────────────────────────────────

    @Test
    fun `la hoja de las alertas por correo solo informa, con su titulo y sin Cancelar`() {
        val direccion = DireccionDeMovi().apply {
            leida = true
            this.direccion = "alertas+3f2a9c1d@movi.resend.app"
        }
        montar { HojaDeAlertasPorCorreo(direccion, onDismiss = {}) }
        // Su acción es «Copiar», junto a la dirección (la misma pieza que la guía de primer uso).
        esUnaHojaDelSistema(RECIBE_TUS_ALERTAS_POR_CORREO, accion = null)
        assertTrue(botones().any { it.first == "Copiar" })
    }
}
