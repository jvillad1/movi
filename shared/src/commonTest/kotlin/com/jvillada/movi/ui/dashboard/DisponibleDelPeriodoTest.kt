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
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La tarjeta «Disponible» (ingresos menos fijos) sin pantalla. El período es el del dueño: corte
 * 25, así que el 21 de septiembre de 2026 cae en el que va del 25 de agosto al 24 de septiembre
 * (31 días). Ese 21 es lunes.
 */
class DisponibleDelPeriodoTest {

    private val inicio = LocalDate(2026, 8, 25)
    private val finExclusivo = LocalDate(2026, 9, 25)

    private fun fijo(nombre: String, monto: Long, pagado: Boolean = false, montoPagado: Long? = null) =
        PagoDelPeriodo(
            ruleId = "rr_$nombre", nombre = nombre, monto = monto, pagado = pagado,
            diasParaVencer = 0, montoPagado = montoPagado,
        )

    private fun ingreso(nombre: String, monto: Long, recibido: Boolean = false) =
        PagoDelPeriodo(
            ruleId = "rr_$nombre", nombre = nombre, monto = monto, pagado = recibido,
            diasParaVencer = 0, esIngreso = true,
        )

    private fun calcular(
        recibidos: Long,
        checklist: List<PagoDelPeriodo>,
        gasto: Map<String, Long> = emptyMap(),
        hoy: LocalDate = LocalDate(2026, 9, 21),
    ) = disponibleDelPeriodo(recibidos, checklist, gasto, inicio, finExclusivo, hoy)

    @Test
    fun `caso normal - las tres ventanas y lo que queda por dia`() {
        val d = assertNotNull(
            calcular(
                recibidos = 10_000_000,
                checklist = listOf(
                    fijo("Arriendo", 2_000_000, pagado = true),
                    fijo("Carro", 1_100_000),
                ),
                gasto = mapOf(
                    "2026-09-21" to 150_000L, // hoy, lunes
                    "2026-09-20" to 300_000L, // domingo: semana pasada
                    "2026-08-30" to 1_000_000L,
                ),
            ),
        )

        assertEquals(3_100_000, d.fijos)
        assertEquals(6_900_000, d.disponible)
        assertTrue(d.hayMargen)
        assertEquals(31, d.diasDelPeriodo)
        // Del 21 al 24 inclusive: hoy cuenta.
        assertEquals(4, d.diasQueQuedan)

        assertEquals(VentanaDelDisponible(gastado = 1_450_000, disponible = 6_900_000), d.periodo)
        // Lunes 21 a domingo 27, recortada al 24: cuatro días de los 31.
        assertEquals(4, d.diasDeLaSemana)
        assertEquals(VentanaDelDisponible(gastado = 150_000, disponible = 6_900_000L * 4 / 31), d.semana)
        assertEquals(VentanaDelDisponible(gastado = 150_000, disponible = 6_900_000L / 31), d.hoy)

        // (6.900.000 − 1.450.000) ÷ 4 días.
        assertEquals(1_362_500, d.porDiaParaLoQueQueda)
        assertEquals(NivelDelGasto.BIEN, d.periodo.nivel)
    }

    /**
     * Los números del dueño hoy: ~$22,2M de ingresos y fijos por encima. La tarjeta no divide nada
     * ni dibuja barras: dice que no hay margen.
     */
    @Test
    fun `con los fijos por encima de los ingresos no hay margen ni barras`() {
        val d = assertNotNull(
            calcular(
                recibidos = 22_200_000,
                checklist = listOf(fijo("Hipotecas", 11_800_000), fijo("Créditos", 13_200_000, pagado = true)),
                gasto = mapOf("2026-09-21" to 90_000L),
            ),
        )

        assertEquals(-2_800_000, d.disponible)
        assertFalse(d.hayMargen)
        assertNull(d.periodo.fraccion)
        assertNull(d.semana.fraccion)
        assertNull(d.hoy.fraccion)
        assertNull(d.porDiaParaLoQueQueda)
        // Lo gastado se sigue sabiendo: es lo único sobre lo que se puede actuar hoy.
        assertEquals(90_000, d.hoy.gastado)
        assertEquals(NivelDelGasto.PASADO, d.hoy.nivel)
    }

    /** Un miércoles 26 de agosto: la semana empezó el lunes 24, que es del período anterior. */
    @Test
    fun `la semana se recorta en el borde del periodo`() {
        val d = assertNotNull(
            calcular(
                recibidos = 3_100_000,
                checklist = emptyList(),
                gasto = mapOf(
                    "2026-08-24" to 500_000L, // lunes, período anterior: no cuenta
                    "2026-08-25" to 40_000L,
                    "2026-08-26" to 60_000L,
                ),
                hoy = LocalDate(2026, 8, 26),
            ),
        )

        // Del martes 25 al domingo 30.
        assertEquals(6, d.diasDeLaSemana)
        assertEquals(100_000, d.semana.gastado)
        assertEquals(3_100_000L * 6 / 31, d.semana.disponible)
        assertEquals(100_000, d.periodo.gastado)
        assertEquals(30, d.diasQueQuedan)
    }

    @Test
    fun `el ultimo dia del periodo reparte lo que queda en un solo dia`() {
        val d = assertNotNull(
            calcular(
                recibidos = 3_100_000,
                checklist = emptyList(),
                gasto = mapOf("2026-09-01" to 1_000_000L, "2026-09-24" to 50_000L),
                hoy = LocalDate(2026, 9, 24),
            ),
        )

        assertEquals(1, d.diasQueQuedan)
        assertEquals(2_050_000, d.porDiaParaLoQueQueda)
        // Lunes 21 al jueves 24.
        assertEquals(4, d.diasDeLaSemana)
        assertEquals(50_000, d.hoy.gastado)
    }

    /**
     * Una cuota ya pagada entra UNA vez, por lo que de verdad salió. Su movimiento no llega al gasto
     * variable (eso lo prueba `GastoVariableTest` en :core): acá se ve que los fijos la cuentan una
     * sola vez y con el monto pagado, no con el pactado.
     */
    @Test
    fun `un fijo pagado se cuenta una vez y por lo que salio`() {
        val d = assertNotNull(
            calcular(
                recibidos = 5_000_000,
                checklist = listOf(fijo("Carro", 1_000_000, pagado = true, montoPagado = 1_077_040)),
            ),
        )
        assertEquals(1_077_040, d.fijos)
        assertEquals(3_922_960, d.disponible)
    }

    /** Y el monto pagado sale de la ocurrencia derivada, por el mismo checklist de «Falta por pagar». */
    @Test
    fun `el checklist del periodo trae lo que salio de la cuota`() {
        val regla = RecurringRule(
            id = "credit_carro", name = "Carro", category = "Cuota de crédito", amount = 1_000_000,
            dayOfMonth = 15, type = TransactionType.EXPENSE,
        )
        val checklist = checklistDelPeriodo(
            upcoming = listOf(
                UpcomingPayment(rule = regla, dueDate = "2026-10-15", daysUntil = 24, status = PaymentStatus.UPCOMING),
            ),
            ocurrencias = listOf(
                OccurrenceState(
                    ruleId = "credit_carro", period = "2026-09", dueDate = "2026-09-15", occurred = true,
                    derivadaDeUnMovimiento = true, montoDelPago = 1_077_040, monedaDelPago = "COP",
                ),
            ),
            periodo = PeriodoFinanciero(2026, 9),
            settings = PeriodSettings(cutoffDay = 25),
        )
        assertEquals(1_077_040, checklist.single().montoPagado)
        assertEquals(1_077_040, fijosDelPeriodo(checklist))
    }

    @Test
    fun `el saldo de una tarjeta y lo que esta en dolares no son fijos`() {
        val checklist = listOf(
            fijo("Arriendo", 2_000_000),
            fijo("Master", 27_500_000).copy(montoEsSaldo = true),
            fijo("AMEX USD", 1_200).copy(moneda = "USD"),
        )
        assertEquals(2_000_000, fijosDelPeriodo(checklist))
    }

    @Test
    fun `el sueldo que falta cuenta como ingreso y el ya recibido no se suma dos veces`() {
        val d = assertNotNull(
            calcular(
                recibidos = 11_000_000,
                checklist = listOf(
                    ingreso("Sueldo", 11_000_000, recibido = true),
                    ingreso("Arriendo del local", 1_500_000),
                    fijo("Arriendo", 2_000_000),
                ),
            ),
        )
        assertEquals(1_500_000, d.ingresosPorRecibir)
        assertEquals(12_500_000, d.ingresos)
        assertEquals(10_500_000, d.disponible)
    }

    /** Un usuario nuevo, o que no ha anotado su sueldo: la tarjeta no se afirma. */
    @Test
    fun `sin ingresos no hay tarjeta`() {
        assertNull(calcular(recibidos = 0, checklist = listOf(fijo("Arriendo", 2_000_000))))
        assertNull(calcular(recibidos = 0, checklist = emptyList()))
    }

    @Test
    fun `las barras avisan cerca del tope y se ponen rojas al pasarlo`() {
        assertEquals(NivelDelGasto.BIEN, VentanaDelDisponible(gastado = 0, disponible = 100_000).nivel)
        assertEquals(0f, VentanaDelDisponible(gastado = 0, disponible = 100_000).fraccion)
        assertEquals(NivelDelGasto.CERCA, VentanaDelDisponible(gastado = 90_000, disponible = 100_000).nivel)
        val pasada = VentanaDelDisponible(gastado = 150_000, disponible = 100_000)
        assertEquals(NivelDelGasto.PASADO, pasada.nivel)
        assertEquals(1f, pasada.fraccion)
        assertEquals(-50_000, pasada.teQuedan)
    }

    // ── El puente con el Inicio ──────────────────────────────────────────────

    private val datosCompletos = DashboardData(
        summary = FinanceSummary(scope = Scope.SELF, balance = 0, ingresos = 10_000_000, egresos = 0),
        upcoming = emptyList(),
        ocurrencias = emptyList(),
        gastoVariablePorDia = emptyMap(),
        ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
        periodoActual = PeriodoFinanciero(2026, 9),
    )

    @Test
    fun `el Inicio la muestra solo con todo leido`() {
        val hoy = LocalDate(2026, 9, 21)
        assertNotNull(disponibleDelInicio(datosCompletos, hoy))
        // Un server anterior al campo: no hay con qué medir lo gastado.
        assertNull(disponibleDelInicio(datosCompletos.copy(gastoVariablePorDia = null), hoy))
        assertNull(disponibleDelInicio(datosCompletos.copy(ocurrencias = null), hoy))
        assertNull(disponibleDelInicio(datosCompletos.copy(summary = null), hoy))
        assertNull(disponibleDelInicio(datosCompletos.copy(periodoActual = null), hoy))
    }
}
