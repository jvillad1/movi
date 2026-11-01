package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.RepositorioDePrueba
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AnotarElPagoDeLaOcurrencia
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.PagoDeLaOcurrenciaAnotado
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.MoviTheme
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «Anotar pago» recuerda cómo se repartió la vez pasada (vuelta 87)
 *
 * El arriendo de agosto se repartió en «Casa con Andrés»; el de noviembre, con «Anotar pago», no ofrecía
 * nada. Ahora la hoja en modo pago muestra «Repartir en Casa con Andrés, como el mes pasado» **ya marcado**;
 * el dueño puede desmarcarlo, y **nunca se reparte solo**: marcado, al guardar se llama el `…/compartir` de
 * siempre sobre el pago ya guardado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h914dp-xhdpi")
class AnotarPagoRecuerdaElRepartoTest {

    @get:Rule val composeRule = createComposeRule()

    private val bancolombia = Account("a1", "Bancolombia Ahorros", AccountType.SAVINGS, 15_534_069)
    private val casa = Espacio(
        id = "esp_casa", nombre = "Casa con Andrés",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO), MiembroDelEspacio("dst_andres", "Andrés")),
    )
    private val ahora = Clock.System.now().toEpochMilliseconds()
    private val haceUnMes = ahora - 30L * 24 * 60 * 60 * 1000

    private fun arriendoDeAntes(repartido: Boolean, id: String = "ev_ago", descripcion: String = "Arriendo", ts: Long = haceUnMes) = FinancialEvent(
        id = id, accountId = bancolombia.id, type = TransactionType.EXPENSE, amount = 1_800_000, category = "Otros",
        description = descripcion, timestamp = ts, createdAt = ts,
        parteAjena = if (repartido) 900_000 else 0, espacioId = if (repartido) casa.id else null,
    )

    private val pagos = mutableListOf<AnotarElPagoDeLaOcurrencia>()
    private val repartos = mutableListOf<Pair<String, String>>()
    private val orden = mutableListOf<String>()
    private var guardo = false

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
    }

    /** Los movimientos que el sello de la regla reconoce como su ocurrencia: id del movimiento → id de la regla. */
    private var marcas: Map<String, String> = mapOf("ev_ago" to "rr-arriendo")

    private fun montar(historia: List<FinancialEvent>, falla: Boolean = false) {
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getAccounts(): List<Account> = listOf(bancolombia)
            override suspend fun getEventsByDay(): List<EventDay> = listOf(EventDay("2026-09-30", 0L, historia))
            override suspend fun getEspacios(): List<Espacio> = listOf(casa)
            override suspend fun getEventOccurrenceMark(id: String) = marcas[id]?.let {
                EventOccurrenceMark(ruleId = it, ruleName = "Arriendo", period = "2026-10", validFrom = "2026-10-01", validTo = "2026-10-31")
            }
            override suspend fun anotarElPagoDeLaOcurrencia(ruleId: String, pedido: AnotarElPagoDeLaOcurrencia): PagoDeLaOcurrenciaAnotado {
                orden += "pago"
                pagos += pedido
                return PagoDeLaOcurrenciaAnotado(
                    movimiento = pedido.movimiento,
                    ocurrencia = RecurringOccurrence(ruleId, pedido.periodo ?: "", pedido.movimiento.id),
                )
            }
            override suspend fun repartirMovimiento(espacioId: String, eventoId: String): GastoDelEspacio {
                orden += "reparto"
                if (falla) error("sin red")
                repartos += espacioId to eventoId
                return GastoDelEspacio(espacioId = espacioId, monto = 1_800_000, eventoId = eventoId, partes = mapOf(MIEMBRO_YO to 900_000L))
            }
        }
        val pago = PagoDeLaFactura(
            ruleId = "rr-arriendo", periodo = "2026-11", nombre = "Arriendo", mes = "noviembre", cuentaDeLaRegla = bancolombia.id,
        )
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    if (!guardo) {
                        QuickAddScreen(
                            onDismiss = {},
                            onSaved = { guardo = true },
                            presetAccountId = bancolombia.id,
                            presetNota = "Arriendo",
                            presetMonto = 1_800_000,
                            presetCategoria = "Otros",
                            presetPago = pago,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun filaDelReparto() = composeRule.onAllNodesWithTag(TAG_REPARTO_RECORDADO, useUnmergedTree = true).fetchSemanticsNodes()

    private fun guardar() {
        composeRule.onNodeWithText("Guardar movimiento").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { pagos.isNotEmpty() && guardo }
    }

    @Test
    fun `si el ultimo pago se repartio la hoja lo sugiere ya marcado`() {
        montar(listOf(arriendoDeAntes(repartido = true)))
        composeRule.waitUntil(5_000) { hay("Repartir en Casa con Andrés, como el mes pasado") }
        composeRule.onNodeWithTag(TAG_REPARTO_RECORDADO).assertIsSelected()
        assertTrue(repartos.isEmpty(), "nunca se reparte antes de guardar ni sin que el dueño guarde")
    }

    @Test
    fun `guardar con la casilla marcada guarda el pago y despues llama el mismo compartir`() {
        montar(listOf(arriendoDeAntes(repartido = true)))
        composeRule.waitUntil(5_000) { filaDelReparto().isNotEmpty() }
        guardar()
        composeRule.waitUntil(5_000) { repartos.isNotEmpty() }

        assertEquals(listOf("pago", "reparto"), orden, "el pago primero; el reparto, sobre el pago ya guardado")
        assertEquals(listOf(casa.id to pagos.single().movimiento.id), repartos)
    }

    @Test
    fun `desmarcada no reparte nada y el pago se guarda igual`() {
        montar(listOf(arriendoDeAntes(repartido = true)))
        composeRule.waitUntil(5_000) { filaDelReparto().isNotEmpty() }
        composeRule.onNodeWithTag(TAG_REPARTO_RECORDADO).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        guardar()
        assertEquals(1, pagos.size)
        assertTrue(repartos.isEmpty(), "desmarcada: no se reparte")
        assertEquals(listOf("pago"), orden)
    }

    @Test
    fun `si la vez pasada no se repartio no se sugiere nada`() {
        montar(listOf(arriendoDeAntes(repartido = false)))
        composeRule.waitForIdle()
        assertTrue(filaDelReparto().isEmpty())
        guardar()
        assertTrue(repartos.isEmpty())
    }

    @Test
    fun `si el reparto falla el pago queda guardado`() {
        montar(listOf(arriendoDeAntes(repartido = true)), falla = true)
        composeRule.waitUntil(5_000) { filaDelReparto().isNotEmpty() }
        guardar()
        composeRule.waitUntil(5_000) { orden.contains("reparto") }
        assertEquals(1, pagos.size, "el pago ya estaba guardado")
        assertTrue(guardo, "la hoja se cierra: lo guardado no se pierde")
    }

    @Test
    fun `un pago emparejado con un aviso del banco con otro nombre tambien se recuerda`() {
        marcas = mapOf("ev_aviso" to "rr-arriendo")
        montar(listOf(arriendoDeAntes(repartido = true, id = "ev_aviso", descripcion = "Transferencia a Andrés")))
        composeRule.waitUntil(5_000) { hay("Repartir en Casa con Andrés, como el mes pasado") }
    }

    @Test
    fun `sin la marca de la regla no se sugiere, aunque el nombre se parezca`() {
        // Un movimiento que se llama «Arriendo» y se repartió, pero que el sello no reconoce como de esta regla.
        marcas = mapOf("ev_ago" to "otra-regla")
        montar(listOf(arriendoDeAntes(repartido = true)))
        composeRule.waitForIdle()
        assertTrue(filaDelReparto().isEmpty())
    }

    @Test
    fun `solo cuenta el ultimo pago de la regla, no uno de hace dos meses`() {
        val haceDosMeses = ahora - 60L * 24 * 60 * 60 * 1000
        marcas = mapOf("ev_sep" to "rr-arriendo", "ev_jul" to "rr-arriendo")
        montar(
            listOf(
                arriendoDeAntes(repartido = false, id = "ev_sep", ts = haceUnMes),
                arriendoDeAntes(repartido = true, id = "ev_jul", ts = haceDosMeses),
            ),
        )
        composeRule.waitForIdle()
        assertTrue(filaDelReparto().isEmpty())
    }
}
