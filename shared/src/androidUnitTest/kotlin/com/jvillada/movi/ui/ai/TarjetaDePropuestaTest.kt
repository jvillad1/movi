package com.jvillada.movi.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.AccionPropuesta
import com.jvillada.movi.shared.model.EstadoDePropuesta
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OrigenDelRecuerdo
import com.jvillada.movi.shared.model.RecuerdoDelAsistente
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecategorizarEnLoteResponse
import com.jvillada.movi.shared.model.TipoDeAccion
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.theme.MoviTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La tarjeta de lo que Movi propone (Ola 3 · 1)
 *
 * El asistente no escribe nada; esta tarjeta es el único lugar donde una propuesta se vuelve un
 * hecho, y solo con el toque del dueño. Se fija:
 *
 * - **«Hacerlo» llama al repositorio de siempre** —el mismo `postEvent` de la hoja de Agregar— con
 *   el movimiento tal cual lo trae la tarjeta, y después le avisa al asistente;
 * - **«No» no llama a ninguna escritura de su plata**: solo le avisa al asistente;
 * - si el endpoint rechaza, la tarjeta dice el motivo del server y sigue sin hacer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h731dp-xhdpi")
class TarjetaDePropuestaTest {

    @get:Rule val composeRule = createComposeRule()

    private val movimiento = FinancialEvent(
        id = "ev_fijo", accountId = "acc-nu", type = TransactionType.EXPENSE, amount = 45_000,
        category = "Comida", description = "Almuerzo", timestamp = 1_000L,
        source = EventSource.MANUAL, reconciliationStatus = ReconciliationStatus.RECONCILED,
    )
    private val propuesta = AccionPropuesta(
        id = "ap_1",
        tipo = TipoDeAccion.ANOTAR_MOVIMIENTO,
        frase = "Anotar un gasto de \$45.000 en Comida desde Nu, hoy",
        movimiento = movimiento,
    )

    private val posteados = mutableListOf<FinancialEvent>()
    private val avisos = mutableListOf<Pair<String, EstadoDePropuesta>>()
    private var lote: List<String>? = null
    private val recordados = mutableListOf<String>()

    private fun repo(rechazar: Boolean = false) = object : RepositorioDePrueba() {
        override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
            if (rechazar) throw ApiException(422, "La categoría la escribe Movi sola")
            posteados += event
            return event
        }
        override suspend fun recategorizarEnLote(ids: List<String>, category: String): RecategorizarEnLoteResponse {
            lote = ids
            return RecategorizarEnLoteResponse(cambiados = ids, omitidos = 0)
        }
        override suspend fun resolverPropuesta(id: String, estado: EstadoDePropuesta) {
            avisos += id to estado
        }
        override suspend fun guardarRecuerdo(texto: String, origen: OrigenDelRecuerdo, propuestaId: String?): RecuerdoDelAsistente {
            recordados += texto
            return RecuerdoDelAsistente("m1", texto, creadoEn = 1L, origen = origen)
        }
    }

    private fun pintar(p: AccionPropuesta, r: RepositorioDePrueba) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    var estado by remember { mutableStateOf(EstadoDePropuesta.PENDIENTE) }
                    PropuestaDelAsistente(p, estado, onEstado = { estado = it }, repo = { r })
                }
            }
        }
    }

    private fun tocar(texto: String) =
        composeRule.onNodeWithText(texto, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `Hacerlo llama al repositorio de siempre con el movimiento de la tarjeta`() {
        pintar(propuesta, repo())
        assertTrue(hay(propuesta.frase))

        tocar(HACERLO)
        composeRule.waitUntil(5_000) { hay(YA_ESTA_HECHO) }

        assertEquals(listOf(movimiento), posteados, "el mismo movimiento, con su id fijo")
        assertEquals(listOf("ap_1" to EstadoDePropuesta.HECHA), avisos)
    }

    @Test
    fun `No no escribe nada, solo le avisa al asistente`() {
        pintar(propuesta, repo())

        tocar(NO_HACERLO)
        composeRule.waitUntil(5_000) { hay(DESCARTADA) }
        composeRule.waitForIdle()

        assertTrue(posteados.isEmpty(), "«No» no puede crear el movimiento")
        assertEquals(listOf("ap_1" to EstadoDePropuesta.RECHAZADA), avisos)
    }

    @Test
    fun `si el endpoint rechaza, la tarjeta dice por que y sigue sin hacer`() {
        pintar(propuesta, repo(rechazar = true))

        tocar(HACERLO)
        composeRule.waitUntil(5_000) { hay("La categoría la escribe Movi sola") }

        assertTrue(hay(HACERLO), "se puede volver a intentar")
        assertTrue(avisos.isEmpty(), "no se le dice al asistente que se hizo")
    }

    @Test
    fun `cambiar varios va por el lote de siempre`() {
        val cambio = AccionPropuesta(
            id = "ap_2", tipo = TipoDeAccion.CAMBIAR_CATEGORIA,
            frase = "Pasar 2 movimientos de «Rappi» a Comida",
            idsDeMovimientos = listOf("r1", "r2"), categoria = "Comida",
        )
        pintar(cambio, repo())

        tocar(HACERLO)
        composeRule.waitUntil(5_000) { hay(YA_ESTA_HECHO) }

        assertEquals(listOf("r1", "r2"), lote)
    }

    @Test
    fun `recordar se guarda solo con Hacerlo, por el endpoint de la memoria`() {
        val recordar = AccionPropuesta(
            id = "ap_3", tipo = TipoDeAccion.RECORDAR, frase = "Recordar: «Caro es mi esposa»", recuerdo = "Caro es mi esposa",
        )
        pintar(recordar, repo())
        assertTrue(recordados.isEmpty(), "pintar la tarjeta no guarda nada")

        tocar(HACERLO)
        composeRule.waitUntil(5_000) { hay(YA_ESTA_HECHO) }

        assertEquals(listOf("Caro es mi esposa"), recordados)
    }
}
