package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBotonKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CuotaPorSaldar
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoParecidoALaCuota
import com.jvillada.movi.shared.model.SI_ES_LA_CUOTA
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.claveDeLaCuotaPorSaldar
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # La fila de Pagos ofrece el gasto que se parece a la cuota (vuelta 57)
 *
 * Revisión de la vuelta 56: la cuota pendiente del Vehículo 4083 solo decía «¿Se cobró?», y el detalle
 * del gasto de $980.000 en «Otros» sí sugería «¿Es la cuota de Vehículo 4083?». Ahora la fila ofrece
 * «¿Es el de $980.000?»; al tocarlo, «¿Es la cuota de Vehículo 4083?» con «Sí, es la cuota» (lo mismo
 * que el detalle) y «No fue este». Nunca se tilda solo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaCuotaParecidaEnPagosTest {

    @get:Rule val composeRule = createComposeRule()

    private val cuota = PagoDelPeriodo(
        ruleId = "${CREDIT_RULE_PREFIX}v4083", nombre = "Cuota Vehículo 4083", monto = 980_000L, pagado = false,
        diasParaVencer = -1, vence = "2026-10-27", periodoDelSello = "2026-11", seCobraSola = true,
    )
    private val gasto = FinancialEvent(
        id = "ev-980", accountId = "a4057", type = TransactionType.EXPENSE, amount = 980_000L, category = "Otros",
        description = "Banco de Occidente S A ATH", timestamp = 1_793_000_000_000L,
    )
    private val parecido = GastoParecidoALaCuota(
        CuotaPorSaldar(creditoId = "v4083", nombre = "Vehículo 4083", cuota = 980_000L, moneda = "COP", vence = "2026-10-27"),
        gasto,
    )

    private val esLaCuota = mutableListOf<String>()
    private val noEs = mutableListOf<String>()

    private fun montar(
        parecidos: Map<String, GastoParecidoALaCuota>,
        checklist: List<PagoDelPeriodo> = listOf(cuota),
    ) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize()) {
                    SeccionChecklistDelPeriodo(
                        checklist = checklist,
                        cargando = false,
                        pudoLeer = true,
                        marcando = emptySet(),
                        onConfirmar = { _, _ -> },
                        onNoFueEste = { _, _ -> },
                        onAnotarMovimiento = {},
                        onQuitarLaMarca = {},
                        onReintentar = {},
                        cuotasQueSeParecen = parecidos,
                        onEsLaCuota = { _, p -> esLaCuota += p.gasto.id },
                        onNoEsLaCuota = { _, p -> noEs += p.gasto.id },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tocar(texto: String) {
        composeRule.onNodeWithText(texto).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    @Test
    fun `la cuota pendiente ofrece el gasto parecido y Si es la cuota hace lo del detalle`() {
        montar(mapOf(claveDeLaCuotaPorSaldar("v4083", "2026-10-27") to parecido))

        composeRule.onNodeWithTag(TAG_CUOTA_QUE_SE_PARECE_EN_PAGOS).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¿Es la cuota de Vehículo 4083?").assertExists()
        assertTrue(esLaCuota.isEmpty(), "nunca se tilda solo")
        tocar(SI_ES_LA_CUOTA)
        assertEquals(listOf("ev-980"), esLaCuota)
    }

    @Test
    fun `No fue este deja de ofrecerlo, y sin parecido la fila sigue como antes`() {
        montar(mapOf(claveDeLaCuotaPorSaldar("v4083", "2026-10-27") to parecido))
        composeRule.onNodeWithText("¿Es el de \$980.000?").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        tocar(ETIQUETA_NO_FUE_ESTE)
        assertEquals(listOf("ev-980"), noEs)
    }

    @Test
    fun `sin un gasto parecido no hay enlace`() {
        // La clave de otro vencimiento no es la de esta fila.
        montar(mapOf(claveDeLaCuotaPorSaldar("v4083", "2026-11-27") to parecido))
        assertTrue(composeRule.onAllNodesWithText("¿Es el de", substring = true).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText(TEXTO_SIN_MOVIMIENTO_DEL_DEBITO).assertExists()
    }

    // ── Vuelta 58 · con un gasto parecido, la acción principal es el candidato, no «Anotar pago» ────

    private fun variante(texto: String): VarianteDeBoton? =
        composeRule.onNodeWithText(texto).fetchSemanticsNode().config.getOrNull(VarianteDeBotonKey)

    /**
     * Revisión de la vuelta 57: «Anotar pago» seguía siendo el botón amarillo y «¿Es el de $980.000?» un
     * enlace; tocar el principal duplicaba el gasto. Con un candidato **parecido** el principal es el
     * candidato y «Anotar pago» pasa a secundario.
     */
    @Test
    fun `con un gasto parecido el principal es el candidato y Anotar pago es secundario`() {
        montar(mapOf(claveDeLaCuotaPorSaldar("v4083", "2026-10-27") to parecido))
        assertEquals(VarianteDeBoton.Principal, variante("¿Es el de \$980.000?"))
        assertEquals(VarianteDeBoton.Secundario, variante(ETIQUETA_ANOTAR))
    }

    /** Con un candidato **lejano** (más del 50 %) queda como estaba: «Anotar pago» principal, el enlace al lado. */
    @Test
    fun `con un candidato lejano Anotar pago sigue siendo el principal y el enlace es secundario`() {
        val tanqueo = FinancialEvent(
            id = "ev-tanqueo", accountId = "acc", type = TransactionType.EXPENSE, amount = 260_000L,
            category = "Transporte", description = "Terpel", timestamp = 1_793_000_000_000L,
        )
        val carro = PagoDelPeriodo(
            ruleId = "rr_carro", nombre = "Carro", monto = 9_000L, pagado = false, diasParaVencer = -3,
            vence = "2026-10-25", periodoDelSello = "2026-10", categoria = "Transporte", candidatos = listOf(tanqueo),
        )
        montar(emptyMap(), checklist = listOf(carro))
        assertEquals(VarianteDeBoton.Principal, variante(ETIQUETA_ANOTAR))
        assertEquals(VarianteDeBoton.Fantasma, variante("¿Es el de \$260.000?"))
    }

    @Test
    fun `la accion principal de la fila, caso por caso`() {
        // Parecido (la regla de la cuota que se parece), en la más urgente: el candidato.
        assertEquals(AccionPrincipalDeLaFila.EL_CANDIDATO, accionPrincipalDeLaFila(esLaMasUrgente = true, conPropuesta = false, hayGastoParecido = true))
        // …pero amarillo solo en la más urgente: en otra fila no hay acción principal.
        assertEquals(AccionPrincipalDeLaFila.NINGUNA, accionPrincipalDeLaFila(esLaMasUrgente = false, conPropuesta = false, hayGastoParecido = true))
        // Candidato del server dentro del ±50 %: vuelta 72, «Sí, fue este» nunca es amarillo; la fila no lo tiene.
        assertEquals(AccionPrincipalDeLaFila.NINGUNA, accionPrincipalDeLaFila(esLaMasUrgente = true, conPropuesta = true, hayGastoParecido = false))
        assertEquals(AccionPrincipalDeLaFila.NINGUNA, accionPrincipalDeLaFila(esLaMasUrgente = false, conPropuesta = true, hayGastoParecido = false))
        // Sin candidato o con uno lejano: como estaba.
        assertEquals(AccionPrincipalDeLaFila.ANOTAR, accionPrincipalDeLaFila(esLaMasUrgente = true, conPropuesta = false, hayGastoParecido = false))
        assertEquals(AccionPrincipalDeLaFila.NINGUNA, accionPrincipalDeLaFila(esLaMasUrgente = false, conPropuesta = false, hayGastoParecido = false))
    }

    /**
     * Vuelta 40: **un solo amarillo, y solo en la fila más urgente.** Dos cuotas, cada una con su gasto
     * parecido: «¿Es el de…?» es el principal de la más urgente; en la otra va con borde. «Anotar pago»
     * es secundario en las dos.
     */
    @Test
    fun `con dos facturas con candidato parecido hay un solo amarillo y es el de la mas urgente`() {
        val menosUrgente = cuota
        val masUrgente = cuota.copy(
            ruleId = "${CREDIT_RULE_PREFIX}c777", nombre = "Cuota Casa 777", monto = 450_000L,
            diasParaVencer = -5, vence = "2026-10-23",
        )
        val gastoCasa = gasto.copy(id = "ev-450", amount = 450_000L)
        val parecidoCasa = GastoParecidoALaCuota(
            CuotaPorSaldar(creditoId = "c777", nombre = "Casa 777", cuota = 450_000L, moneda = "COP", vence = "2026-10-23"),
            gastoCasa,
        )
        montar(
            mapOf(
                claveDeLaCuotaPorSaldar("v4083", "2026-10-27") to parecido,
                claveDeLaCuotaPorSaldar("c777", "2026-10-23") to parecidoCasa,
            ),
            checklist = listOf(menosUrgente, masUrgente),
        )
        val botones = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(VarianteDeBotonKey)).fetchSemanticsNodes()
        val amarillos = botones.filter { it.config.getOrNull(VarianteDeBotonKey) == VarianteDeBoton.Principal }
        assertEquals(1, amarillos.size, "un solo botón amarillo en Pagos")
        assertEquals(VarianteDeBoton.Principal, variante("¿Es el de \$450.000?"), "el de la más urgente")
        assertEquals(VarianteDeBoton.Secundario, variante("¿Es el de \$980.000?"), "el de la otra, con borde")
        composeRule.onAllNodesWithText(ETIQUETA_ANOTAR).fetchSemanticsNodes().forEach {
            assertEquals(VarianteDeBoton.Secundario, it.config.getOrNull(VarianteDeBotonKey), "Anotar pago, secundario")
        }
    }

    /**
     * Vuelta 58: lo mismo con el candidato del **server** dentro del ±50 % («Sí, fue este»). Vuelta 72: ya
     * no es amarillo en ninguna fila. Tres facturas —una cuota con gasto parecido (la menos urgente), una con
     * candidato del server (la del medio) y una con candidato del server que es la más urgente—: un solo
     * amarillo, y es el «Sí, fue este» de la más urgente.
     */
    @Test
    fun `con candidatos del server en varias filas tambien hay un solo amarillo`() {
        fun candidato(id: String, monto: Long) = FinancialEvent(
            id = id, accountId = "acc", type = TransactionType.EXPENSE, amount = monto, category = "Servicios",
            description = "Pago $id", timestamp = 1_793_000_000_000L,
        )
        val agua = PagoDelPeriodo(
            ruleId = "rr_agua", nombre = "Agua", monto = 100_000L, pagado = false, diasParaVencer = -6,
            vence = "2026-10-22", periodoDelSello = "2026-10", categoria = "Servicios", candidatos = listOf(candidato("ev-agua", 98_000L)),
        )
        val gas = PagoDelPeriodo(
            ruleId = "rr_gas", nombre = "Gas", monto = 60_000L, pagado = false, diasParaVencer = -2,
            vence = "2026-10-26", periodoDelSello = "2026-10", categoria = "Servicios", candidatos = listOf(candidato("ev-gas", 59_000L)),
        )
        montar(
            mapOf(claveDeLaCuotaPorSaldar("v4083", "2026-10-27") to parecido),
            checklist = listOf(cuota, gas, agua),
        )
        val botones = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(VarianteDeBotonKey)).fetchSemanticsNodes()
        val amarillos = botones.filter { it.config.getOrNull(VarianteDeBotonKey) == VarianteDeBoton.Principal }
        // Vuelta 72: «Sí, fue este» nunca es amarillo; tildar es decisión del dueño. Ningún amarillo acá.
        assertEquals(0, amarillos.size, "sin un amarillo que empuje a tildar")
        val si = composeRule.onAllNodesWithText(ETIQUETA_SI_FUE_ESTE).fetchSemanticsNodes().map { it.config.getOrNull(VarianteDeBotonKey) }
        assertEquals(2, si.size)
        assertTrue(si.all { it == VarianteDeBoton.Secundario }, "los dos con borde")
        assertEquals(VarianteDeBoton.Secundario, variante("¿Es el de \$980.000?"), "la cuota parecida, con borde")
    }
}
