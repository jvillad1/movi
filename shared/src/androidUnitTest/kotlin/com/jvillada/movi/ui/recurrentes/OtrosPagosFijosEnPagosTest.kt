package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.jvillada.movi.shared.model.FrecuenciaDelPago
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.epochDeFecha
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.components.ds.LlevaElAmarilloKey
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
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
 * # Vuelta 126 · lo que no vence en este período, en «Otros pagos fijos · N»
 *
 * Una regla cada 2, 3 o 6 meses, cada año o de una sola vez que no vence en este período no está en «Tus
 * facturas del período» (no suma): va al final, plegada como «Pausados · N», con su cadencia y su próximo
 * vencimiento. La factura de una no mensual del período dice su cadencia. Y el total «al mes» de lo que se
 * repite no la suma.
 *
 * Sin el cambio no compila (ni la sección ni la frecuencia existían).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class OtrosPagosFijosEnPagosTest {

    @get:Rule val composeRule = createComposeRule()

    /** Fin del período de noviembre de 2026 (exclusivo): el 1 de diciembre. */
    private val finDeNoviembre = epochDeFecha("2026-12-01") - 12 * 3_600_000L

    private fun regla(id: String, nombre: String, monto: Long, frecuencia: FrecuenciaDelPago? = null, ancla: String? = null, dia: Int = 15) =
        RecurringRule(id, nombre, nombre, monto, dia, TransactionType.EXPENSE, frecuencia = frecuencia, primerVencimiento = ancla)

    private fun proximo(regla: RecurringRule, vence: String, estado: PaymentStatus = PaymentStatus.UPCOMING) =
        UpcomingPayment(regla, vence, 0, estado)

    private val admin = regla("rr_admin", "Administración", 450_000L, FrecuenciaDelPago.TRIMESTRAL, "2026-12-15")
    private val soat = regla("rr_soat", "SOAT", 980_000L, FrecuenciaDelPago.ANUAL, "2027-03-05", dia = 5)
    private val arriendo = regla("rr_arriendo", "Arriendo", 1_800_000L)
    private val luz = regla("rr_luz", "Luz", 90_000L, FrecuenciaDelPago.BIMESTRAL, "2026-11-20", dia = 20)
    private val matricula = regla("rr_matricula", "Matrícula", 2_500_000L, FrecuenciaDelPago.UNA_VEZ, "2026-09-20", dia = 20)

    private val upcoming = listOf(
        proximo(arriendo, "2026-11-15"),
        proximo(admin, "2026-12-15"),
        proximo(soat, "2027-03-05"),
        proximo(luz, "2026-11-20"),
        proximo(matricula, "2026-09-20", PaymentStatus.OVERDUE),
    )

    @Test
    fun `solo las no mensuales que vencen despues de este periodo, por fecha`() {
        val otros = otrosPagosFijos(upcoming, finDeNoviembre)
        // El arriendo es de cada mes; la luz vence en noviembre (está en la lista); la matrícula vencida y sin
        // pagar está en «Del período de septiembre».
        assertEquals(listOf("rr_admin", "rr_soat"), otros.map { it.rule.id })
        assertEquals("Cada 3 meses · el próximo, 15 dic · \$450.000", subtituloDeOtroPagoFijo(otros[0]))
        assertEquals("Cada año · el próximo, 5 mar · \$980.000", subtituloDeOtroPagoFijo(otros[1]))
    }

    @Test
    fun `lo que ya tiene fila en las facturas no se repite`() {
        // Revisión (M3): la administración ya pagada en noviembre rodó al 15 de febrero, pero su fila pagada
        // está arriba.
        val pagada = listOf(proximo(admin, "2027-02-15"), proximo(soat, "2027-03-05"))
        assertEquals(listOf("rr_admin", "rr_soat"), otrosPagosFijos(pagada, finDeNoviembre).map { it.rule.id })
        assertEquals(listOf("rr_soat"), otrosPagosFijos(pagada, finDeNoviembre, enLaLista = setOf("rr_admin")).map { it.rule.id })
    }

    @Test
    fun `la seccion tampoco repite una fila de la lista`() {
        val adminPagada = PagoDelPeriodo(
            "rr_admin", "Administración", 450_000L, true, -5, vence = "2026-11-15", frecuencia = FrecuenciaDelPago.TRIMESTRAL,
            derivado = true, automatica = true, eventId = "ev_admin",
        )
        montar(listOf(adminPagada), listOf(proximo(admin, "2027-02-15"), proximo(soat, "2027-03-05")), mutableListOf())
        composeRule.onNodeWithText("Ver").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithTag(TAG_FILA_DE_OTRO_PAGO_FIJO, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(hay("Cada año · el próximo, 5 mar"))
    }

    @Test
    fun `la factura de una no mensual dice su cadencia, y una mensual nada`() {
        val delPeriodo = PagoDelPeriodo("rr_luz", "Luz", 90_000L, false, 5, vence = "2026-11-20", frecuencia = FrecuenciaDelPago.BIMESTRAL)
        assertEquals("Cada 2 meses", cadenciaDeLaFila(delPeriodo))
        assertNull(cadenciaDeLaFila(delPeriodo.copy(frecuencia = FrecuenciaDelPago.MENSUAL)))
    }

    @Test
    fun `el total al mes no suma una no mensual`() {
        assertTrue(cuentaComoCompromisoMensual(arriendo))
        assertFalse(cuentaComoCompromisoMensual(admin))
        assertFalse(cuentaComoCompromisoMensual(matricula))
    }

    private fun montar(checklist: List<PagoDelPeriodo>, otros: List<UpcomingPayment>, abiertas: MutableList<String>) {
        composeRule.setContent {
            MoviTheme {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
                        onAgregar = {},
                        onAbrirPausada = { abiertas += it.id },
                        otrosPagosFijos = otros,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun hay(texto: String) =
        composeRule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `Otros pagos fijos va plegada al final, con su proximo vencimiento, y abre la regla`() {
        val arriendoDelPeriodo = PagoDelPeriodo("rr_arriendo", "Arriendo", 1_800_000L, false, 12, vence = "2026-11-15", categoria = "Vivienda")
        val luzDelPeriodo = PagoDelPeriodo("rr_luz", "Luz", 90_000L, false, 17, vence = "2026-11-20", frecuencia = FrecuenciaDelPago.BIMESTRAL)
        val abiertas = mutableListOf<String>()
        montar(listOf(arriendoDelPeriodo, luzDelPeriodo), otrosPagosFijos(upcoming, finDeNoviembre), abiertas)

        assertTrue(hay(TITULO_DE_OTROS_PAGOS_FIJOS))
        assertTrue(hay("$TITULO_DE_OTROS_PAGOS_FIJOS · 2") || hay(" · 2"), "el conteo como en «Pausados · N»")
        assertFalse(hay("Administración"), "plegada: no se ve hasta abrirla")
        // La luz, que sí vence en noviembre, está en la lista con su cadencia.
        assertTrue(composeRule.onAllNodesWithTag(TAG_CADENCIA_DE_LA_FILA, useUnmergedTree = true).fetchSemanticsNodes().size == 1)
        assertTrue(hay("Cada 2 meses"))

        composeRule.onNodeWithText("Ver").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertTrue(hay("Cada 3 meses · el próximo, 15 dic"))
        assertTrue(hay("Cada año · el próximo, 5 mar"))
        assertEquals(2, composeRule.onAllNodesWithTag(TAG_FILA_DE_OTRO_PAGO_FIJO, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Administración").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf("rr_admin"), abiertas)

        // Un solo amarillo: el «Anotar pago» de la factura más urgente; la sección no agrega ninguno.
        fun cuenta(n: SemanticsNode): Int =
            (if (n.config.getOrNull(LlevaElAmarilloKey) == true) 1 else 0) + n.children.sumOf(::cuenta)
        assertEquals(1, cuenta(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()))
    }

    @Test
    fun `sin otros pagos fijos la seccion no se pinta`() {
        montar(emptyList(), emptyList(), mutableListOf())
        assertTrue(composeRule.onAllNodesWithTag(TAG_SECCION_DE_OTROS_PAGOS_FIJOS, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
