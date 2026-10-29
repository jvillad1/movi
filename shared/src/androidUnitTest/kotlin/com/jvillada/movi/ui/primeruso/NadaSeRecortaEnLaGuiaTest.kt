package com.jvillada.movi.ui.primeruso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import com.jvillada.movi.ConClaseDeAncho
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.DireccionDeCorreo
import com.jvillada.movi.shared.model.UserProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * **Ningún texto de la guía se recorta a 360 dp con la letra agrandada** (×1,3 encima del ×1,12 que
 * la app ya aplica), en ningún paso, al estilo de `NadaSeRecortaEnElInicioTest`: ningún renglón con
 * «…» y cada renglón cabe en el ancho que su nodo tiene en pantalla. Los formularios se miran
 * abiertos (una tarjeta, un pago de cada mes) y el período con un día elegido.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class NadaSeRecortaEnLaGuiaTest {

    @get:Rule val composeRule = createComposeRule()

    private val repo = object : RepositorioDePrueba() {
        override suspend fun getUserProfile() =
            UserProfile(id = "u1", email = "papa@movi.test", name = "Papá", avatarColor = "#7C6CF0")
        override suspend fun getDireccionDeCorreo() =
            DireccionDeCorreo(token = "0123456789abcdef", direccion = "alertas+0123456789abcdef@k3x9.resend.app", configurado = true)
    }

    private fun montar(paso: PasoDePrimerUso, modo: ModoDeCaptura = ModoDeCaptura.SUBIR_UN_EXTRACTO) {
        Repositories.sustitutoDePrueba = repo
        composeRule.setContent {
            ConClaseDeAncho(escala = 1.3f * 1.12f) {
                Box(Modifier.fillMaxSize()) {
                    PrimerUsoScreen(onNavigate = {}, pasoInicial = paso, modoDeCaptura = modo)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun texto(nodo: SemanticsNode): String =
        nodo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun disposicion(nodo: SemanticsNode): TextLayoutResult? {
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.singleOrNull()
    }

    private fun seVeEntero(nodo: SemanticsNode): Boolean {
        val d = disposicion(nodo) ?: return true
        val ancho = nodo.size.width + 0.5f
        return (0 until d.lineCount).all { linea ->
            !d.isLineEllipsized(linea) && d.getLineRight(linea) - d.getLineLeft(linea) <= ancho
        } && !d.multiParagraph.didExceedMaxLines
    }

    private fun nadaSeRecorta(contexto: String) {
        val nodos = composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(nodos.size > 3, "$contexto: la guía pinta sus textos")
        val recortados = nodos.filterNot(::seVeEntero).map(::texto)
        assertTrue(recortados.isEmpty(), "$contexto: se recortan $recortados")
    }

    @Test fun `bienvenida`() { montar(PasoDePrimerUso.BIENVENIDA); nadaSeRecorta("bienvenida") }

    @Test fun `periodo con un dia elegido`() {
        montar(PasoDePrimerUso.PERIODO)
        composeRule.onNodeWithText("31").performClick()
        composeRule.waitForIdle()
        nadaSeRecorta("período")
    }

    @Test fun `cuentas con la tarjeta abierta`() {
        montar(PasoDePrimerUso.CUENTAS)
        composeRule.onNodeWithText("Agregar una tarjeta").performScrollTo().performClick()
        composeRule.waitForIdle()
        nadaSeRecorta("cuentas")
    }

    @Test fun `captura con un extracto y el reenvio`() { montar(PasoDePrimerUso.CAPTURA); nadaSeRecorta("captura con un extracto") }

    @Test fun `captura en Android`() {
        montar(PasoDePrimerUso.CAPTURA, ModoDeCaptura.PERMISOS_DEL_TELEFONO)
        nadaSeRecorta("captura en Android")
    }

    @Test fun `pagos de cada mes con uno abierto`() {
        montar(PasoDePrimerUso.RECURRENTES)
        composeRule.onNodeWithText("Una suscripción").performClick()
        composeRule.waitForIdle()
        nadaSeRecorta("pagos de cada mes")
    }

    @Test fun `listo`() { montar(PasoDePrimerUso.LISTO); nadaSeRecorta("listo") }
}
