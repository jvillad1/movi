package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FormaDeRepartir
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.MotivoDeReparto
import com.jvillada.movi.shared.model.PropuestaDeRepartir
import com.jvillada.movi.shared.model.RepartoDelGasto
import com.jvillada.movi.theme.MoviTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * # Vuelta 97 · el selector de reparto en pantalla
 *
 * «Partes iguales» elegida de entrada; «Montos exactos» despliega un campo por persona y, si no suman,
 * la línea dice «Faltan $X» y la hoja no deja repartir. La propuesta de Movi abre la misma hoja con el
 * reparto de la última vez, y dos toques reparten una sola vez.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1400dp-xhdpi")
class SelectorDeRepartoEnPantallaTest {

    @get:Rule val composeRule = createComposeRule()

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    private fun botonDeRepartir() = composeRule.onNode(
        hasClickAction() and hasAnyAncestor(hasTestTag(TAG_CONFIRMAR_EL_REPARTO)),
        useUnmergedTree = true,
    )

    @Test
    fun `con montos que no suman dice Faltan y no deja repartir`() {
        val repartidos = mutableListOf<RepartoDelGasto>()
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    HojaDeRepartirElGasto(
                        espacio = casa, monto = 100_000, concepto = "Mercado", repartoInicial = null,
                        onDismiss = {}, onRepartir = { repartidos += it },
                    )
                }
            }
        }
        // De entrada, la regla del grupo, y la línea con lo de cada uno.
        composeRule.onNodeWithText("Partes iguales", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Te toca \$50.000 · a Caro \$50.000", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNode(hasClickAction() and hasText("Montos exactos")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tagDelCampoDelReparto(MIEMBRO_YO), useUnmergedTree = true).performTextReplacement("30000")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_LINEA_DEL_REPARTO, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Faltan \$20.000", useUnmergedTree = true).assertIsDisplayed()
        botonDeRepartir().assertIsNotEnabled()
        assertEquals(0, repartidos.size)
    }

    @Test
    fun `la propuesta abre la hoja con el reparto de la ultima vez, y dos toques reparten una vez`() {
        val llamadas = mutableListOf<Triple<String, String, RepartoDelGasto?>>()
        val respuesta = CompletableDeferred<GastoDelEspacio>()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun repartirMovimiento(espacioId: String, eventoId: String, reparto: RepartoDelGasto?): GastoDelEspacio {
                llamadas += Triple(espacioId, eventoId, reparto)
                return respuesta.await()
            }
        }
        val setenta = RepartoDelGasto(FormaDeRepartir.PORCENTAJES, porcentajes = mapOf(MIEMBRO_YO to 70, "dst_caro" to 30))
        val p = PropuestaDeRepartir(
            eventoId = "e_arriendo", descripcion = "Arriendo", monto = 2_400_000, fecha = 1L, espacio = casa,
            reparto = setenta, motivo = MotivoDeReparto.RECURRENTE, clave = "regla:rr_arriendo",
        )
        composeRule.setContent {
            MoviTheme { Box(Modifier.fillMaxSize()) { TarjetaDeRepartir(p = p, onHecho = {}) } }
        }
        composeRule.onNodeWithText("¿Repartes Arriendo \$2.400.000 con Caro?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("No es compartido", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Nunca para este recurrente", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNode(hasClickAction() and hasText("Repartir")).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        // La hoja, llena con el 70/30 de la última vez.
        composeRule.onNodeWithText("Te toca \$1.680.000 · a Caro \$720.000", useUnmergedTree = true).assertIsDisplayed()

        botonDeRepartir().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        botonDeRepartir().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals<List<Triple<String, String, RepartoDelGasto?>>>(listOf(Triple("esp_casa", "e_arriendo", setenta)), llamadas, "dos toques, un reparto")
        respuesta.complete(GastoDelEspacio(id = "g", espacioId = "esp_casa", monto = 2_400_000, eventoId = "e_arriendo"))
        composeRule.waitForIdle()
    }
}
