package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.ui.components.ErroresDeLaPantalla
import com.jvillada.movi.ui.porrevisar.ConteoPorRevisar
import com.jvillada.movi.ui.sdui.textosDeLaFranjaPorConfirmar
import com.jvillada.movi.ui.transactions.ResumenDelPanel
import com.jvillada.movi.ui.transactions.laFranjaSePinta
import com.jvillada.movi.ui.transactions.loPendienteSeVeEn
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 68 · un solo error por pantalla, e Inicio sin repeticiones
 *
 * De la tercera revisión (`r3-err-*.png`, `r3-inicio-*.png`): Pagos apilaba un error por tarjeta,
 * Actividad dejaba el esqueleto de Entró · Salió para siempre, Inicio mostraba la cifra guardada sin
 * decir que era vieja y la tarjeta de avisos salía en septiembre. Y en Inicio, el Carro dos veces, dos
 * entradas a «Editar inicio» y «Por confirmar $X · En 5 avisos · y 1 movimiento» con la cifra solo de
 * los avisos.
 */
class UnSoloErrorYSinRepeticionesTest {

    private fun a(dia: Int, hora: Int, minuto: Int = 0): Long =
        LocalDateTime(2026, 10, dia, hora, minuto).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    // ── Un solo error ─────────────────────────────────────────────────────────

    @Test
    fun `los errores de la pantalla se juntan y Reintentar los reintenta todos`() {
        val errores = ErroresDeLaPantalla()
        assertFalse(errores.hayError)
        var facturas = 0
        var disponible = 0
        errores.anotar("facturas") { facturas++ }
        errores.anotar("disponible") { disponible++ }
        // La misma sección no se anota dos veces.
        errores.anotar("facturas") { facturas++ }
        assertEquals(2, errores.cuantos)
        errores.reintentarTodo()
        assertEquals(1, facturas)
        assertEquals(1, disponible)
        errores.quitar("facturas")
        errores.quitar("disponible")
        assertFalse(errores.hayError)
    }

    @Test
    fun `la franja de Actividad no queda en esqueleto si la lectura fallo`() {
        assertTrue(laFranjaSePinta(resumen = null, cargando = true), "cargando: su esqueleto")
        assertFalse(laFranjaSePinta(resumen = null, cargando = false), "falló: no se pinta")
        assertTrue(laFranjaSePinta(ResumenDelPanel(PeriodoFinanciero(2026, 10), entro = 1, salio = 2, categorias = emptyList()), cargando = false))
    }

    @Test
    fun `lo pendiente no sale en un periodo pasado`() {
        val octubre = PeriodoFinanciero(2026, 10)
        assertTrue(loPendienteSeVeEn(octubre, octubre))
        assertFalse(loPendienteSeVeEn(PeriodoFinanciero(2026, 9), octubre))
    }

    @Test
    fun `Inicio con la cifra guardada dice de cuando es`() {
        val ahora = a(29, 10)
        assertEquals("Sin conexión · cifras de las 9:06", lineaSinConexion(a(29, 9, 6), ahora))
        assertEquals("Sin conexión · cifras de ayer a las 21:05", lineaSinConexion(a(28, 21, 5), ahora))
        assertTrue(lineaSinConexion(a(3, 9, 40), ahora)!!.startsWith("Sin conexión · cifras del "))
        assertNull(lineaSinConexion(null, ahora))
    }

    @Test
    fun `el encabezado dice Sin conexion solo si la carga fallo y hay cifras`() {
        val ahora = a(29, 10)
        val conCifras = DashboardData(
            accounts = emptyList(), summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 0, egresos = 0),
            upcoming = emptyList(), credits = emptyList(), cards = emptyList(),
            periodoActual = PeriodoFinanciero(2026, 10), ajustesDePeriodo = PeriodSettings(),
        )
        assertEquals(
            "Sin conexión · cifras de las 9:06",
            subtituloDelEncabezadoDelInicio(conCifras, actualizandoConDatos = false, falloLaCarga = true, cifrasDe = a(29, 9, 6), ahora = ahora),
        )
        assertEquals(
            "Actualizando…",
            subtituloDelEncabezadoDelInicio(conCifras, actualizandoConDatos = true, falloLaCarga = true, cifrasDe = a(29, 9, 6), ahora = ahora),
        )
        // Sin falla, el período de siempre.
        assertEquals(
            periodoDelEncabezado(conCifras),
            subtituloDelEncabezadoDelInicio(conCifras, actualizandoConDatos = false, falloLaCarga = false, cifrasDe = a(29, 9, 6), ahora = ahora),
        )
        // Sin cifras a la vista no hay nada viejo que aclarar.
        assertEquals(
            periodoDelEncabezado(DashboardData()),
            subtituloDelEncabezadoDelInicio(DashboardData(), actualizandoConDatos = false, falloLaCarga = true, cifrasDe = a(29, 9, 6), ahora = ahora),
        )
    }

    // ── Inicio sin repeticiones ──────────────────────────────────────────────

    private fun regla(id: String, nombre: String, monto: Long, dia: Int) =
        RecurringRule(id = id, name = nombre, category = "Servicios", amount = monto, dayOfMonth = dia, type = TransactionType.EXPENSE)

    /** El de `UnaSolaVerdadEnHoyTest`: el Arriendo vencido hace 22 días, Carro hace 2 e Internet ayer. */
    private val demo = DashboardData(
        upcoming = listOf(
            UpcomingPayment(regla("rr_arriendo", "Arriendo", 1_800_000, 5), "2026-11-05", daysUntil = 9, status = PaymentStatus.UPCOMING),
            UpcomingPayment(regla("rr_internet", "Internet", 3_000, 26), "2026-10-26", daysUntil = -1, status = PaymentStatus.OVERDUE),
            UpcomingPayment(regla("rr_carro", "Carro", 9_000, 25), "2026-10-25", daysUntil = -2, status = PaymentStatus.OVERDUE),
        ),
        ocurrencias = listOf(OccurrenceState(ruleId = "rr_arriendo", period = "2026-10", dueDate = "2026-10-05", occurred = false)),
        periodoActual = PeriodoFinanciero(2026, 10),
        ajustesDePeriodo = PeriodSettings(),
    )

    @Test
    fun `el widget no repite el pago de Proximo pago`() {
        val arriba = proximoPagoDelInicio(demo)!!.pago
        val widget = filasDelWidgetDeFalta(demo, sinElProximoPago = true)
        assertEquals("Arriendo", arriba.nombre)
        assertFalse(widget.filas.any { it.ruleId == arriba.ruleId }, "el de arriba no es su primer renglón")
        assertEquals(listOf("Carro", "Internet"), widget.filas.map { it.nombre })
        assertEquals(0, widget.masSinFila)
        assertEquals(arriba.ruleId, widget.elDeArriba?.ruleId)
        // Sin «Próximo pago» en el Inicio, el widget lista lo de siempre.
        assertEquals(listOf("Arriendo", "Carro"), filasDelWidgetDeFalta(demo).filas.map { it.nombre })
    }

    @Test
    fun `con un solo pendiente arriba, el widget no lo repite`() {
        val soloElCarro = demo.copy(upcoming = demo.upcoming!!.filter { it.rule.id == "rr_carro" }, ocurrencias = emptyList())
        val widget = filasDelWidgetDeFalta(soloElCarro, sinElProximoPago = true)
        assertTrue(widget.filas.isEmpty())
        assertEquals("Carro", widget.elDeArriba?.nombre)
    }

    @Test
    fun `Por confirmar pega la cifra a lo que suma`() {
        assertEquals(
            "Por confirmar" to "\$1.100.200 en 5 avisos · y 1 movimiento",
            textosDeLaFranjaPorConfirmar(ConteoPorRevisar(avisos = 5, movimientos = 1), 1_100_200L),
        )
    }
}
