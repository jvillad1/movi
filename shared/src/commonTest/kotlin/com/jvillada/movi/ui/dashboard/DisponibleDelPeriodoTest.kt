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
    fun `caso normal - las tres ventanas miden cada una su propio gasto`() {
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

        assertEquals(1_450_000, d.periodo.gastado)
        assertEquals(150_000, d.semana.gastado)
        assertEquals(150_000, d.hoy.gastado)
        // (6.900.000 − 1.450.000) ÷ 4 días.
        assertEquals(1_362_500, d.porDiaParaLoQueQueda)
    }

    // ── Las metas ────────────────────────────────────────────────────────────

    /** Las metas del dueño: 31 días. No dependen de lo gastado. */
    @Test
    fun `las metas de un periodo de 31 dias`() {
        val sinGasto = assertNotNull(calcular(recibidos = 8_500_000, checklist = emptyList(), hoy = LocalDate(2026, 9, 8)))
        // 8.500.000 ÷ 31 = 274.193 (se trunca), por 7 = 1.919.351.
        assertEquals(8_500_000, sinGasto.periodo.meta)
        assertEquals(274_193, sinGasto.metaPorDia)
        assertEquals(1_919_351, sinGasto.metaPorSemana)
        // Martes 8: la semana del lunes 7 al domingo 13 cabe entera.
        assertEquals(7, sinGasto.diasDeLaSemana)
        assertFalse(sinGasto.semanaCorta)
        assertEquals(1_919_351, sinGasto.semana.meta)
        assertEquals(274_193, sinGasto.hoy.meta)
        assertEquals("Esta semana", rotuloDeLaSemana(sinGasto))

        val conGasto = assertNotNull(
            calcular(
                recibidos = 8_500_000, checklist = emptyList(),
                gasto = mapOf("2026-09-01" to 6_000_000L, "2026-09-08" to 900_000L), hoy = LocalDate(2026, 9, 8),
            ),
        )
        assertEquals(sinGasto.periodo.meta, conGasto.periodo.meta)
        assertEquals(sinGasto.semana.meta, conGasto.semana.meta)
        assertEquals(sinGasto.hoy.meta, conGasto.hoy.meta)
    }

    /** Un miércoles 26 de agosto: la semana empezó el lunes 24, que es del período anterior. */
    @Test
    fun `la semana corta al empezar el periodo`() {
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

        // Del martes 25 al domingo 30; corridos el 25 y el 26.
        assertEquals(6, d.diasDeLaSemana)
        assertEquals(2, d.diasCorridosDeLaSemana)
        assertTrue(d.semanaCorta)
        assertEquals(100_000, d.semana.gastado)
        // 3.100.000 ÷ 31 = 100.000 por día.
        assertEquals(600_000, d.semana.meta)
        assertEquals(200_000, d.semana.esperadoAHoy)
        assertEquals("Esta semana · semana corta: 6 días", rotuloDeLaSemana(d))
        assertEquals(100_000, d.periodo.gastado)
        assertEquals(30, d.diasQueQuedan)
    }

    /** Martes 22 de septiembre: la semana va del lunes 21 al jueves 24, último día del período. */
    @Test
    fun `la semana corta al cerrar el periodo`() {
        val d = assertNotNull(calcular(recibidos = 3_100_000, checklist = emptyList(), hoy = LocalDate(2026, 9, 22)))
        assertEquals(4, d.diasDeLaSemana)
        assertEquals(2, d.diasCorridosDeLaSemana)
        assertEquals(400_000, d.semana.meta)
        assertEquals("Esta semana · semana corta: 4 días", rotuloDeLaSemana(d))
        // La meta de una semana entera se sigue diciendo por 7.
        assertEquals(700_000, d.metaPorSemana)
    }

    // ── Cómo viene ───────────────────────────────────────────────────────────

    /**
     * El ejemplo del encargo: disponible $8,5M, hoy martes 22 (día 29 de 31), $5M gastados en el
     * período, nada esta semana ni hoy.
     */
    @Test
    fun `el periodo por debajo de lo previsto`() {
        val d = assertNotNull(
            calcular(
                recibidos = 8_500_000, checklist = emptyList(),
                gasto = mapOf("2026-09-01" to 5_000_000L), hoy = LocalDate(2026, 9, 22),
            ),
        )
        // Previsto a hoy: 8.500.000 × 29 ÷ 31 = 7.951.612. Va 2.951.612 por debajo.
        assertEquals(7_951_612, d.periodo.esperadoAHoy)
        assertEquals(NivelDelGasto.BIEN, d.periodo.nivel)
        assertEquals("Este período · quedan 2 días", rotuloDelPeriodo(d))
        // Quedan $3,5M para 3 días (hoy incluido): $1.166.666.
        assertEquals(
            "Vas \$3M por debajo de lo previsto a hoy · te quedan \$3,5M, unos \$1,2M por día",
            comoVieneElPeriodo(d),
        )
        assertEquals("Vas bien: te quedan \$1,1M para esta semana", comoVieneLaSemana(d))
        assertEquals("Te quedan \$274.193 para hoy", comoVieneHoy(d))
    }

    @Test
    fun `el periodo por encima de lo previsto avisa aunque no se haya pasado`() {
        // Martes 8 de septiembre, día 15 de 31: previsto 3.100.000 × 15 ÷ 31 = 1.500.000.
        val d = assertNotNull(
            calcular(
                recibidos = 3_100_000, checklist = emptyList(),
                gasto = mapOf("2026-09-01" to 2_000_000L), hoy = LocalDate(2026, 9, 8),
            ),
        )
        assertEquals(1_500_000, d.periodo.esperadoAHoy)
        assertEquals(NivelDelGasto.CERCA, d.periodo.nivel)
        // Quedan $1,1M para 17 días: $64.705.
        assertEquals(
            "Vas \$500.000 por encima de lo previsto a hoy · te quedan \$1,1M, unos \$64.705 por día",
            comoVieneElPeriodo(d),
        )
    }

    @Test
    fun `la semana contra su ritmo`() {
        // Miércoles 9: corridos lunes 7, martes 8 y hoy. 100.000 por día → previsto 300.000.
        fun semanaCon(gastado: Long) = assertNotNull(
            calcular(
                recibidos = 3_100_000, checklist = emptyList(),
                gasto = mapOf("2026-09-07" to gastado), hoy = LocalDate(2026, 9, 9),
            ),
        )
        val bien = semanaCon(250_000)
        assertEquals(NivelDelGasto.BIEN, bien.semana.nivel)
        assertEquals("Vas bien: te quedan \$450.000 para esta semana", comoVieneLaSemana(bien))

        val porEncima = semanaCon(420_000)
        assertEquals(NivelDelGasto.CERCA, porEncima.semana.nivel)
        assertEquals("Vas \$120.000 por encima del ritmo de la semana", comoVieneLaSemana(porEncima))

        val pasada = semanaCon(800_000)
        assertEquals(NivelDelGasto.PASADO, pasada.semana.nivel)
        assertEquals("Te pasaste de la meta de la semana por \$100.000", comoVieneLaSemana(pasada))
    }

    @Test
    fun `hoy pasado de su meta`() {
        val d = assertNotNull(
            calcular(recibidos = 3_100_000, checklist = emptyList(), gasto = mapOf("2026-09-21" to 130_000L)),
        )
        assertEquals(NivelDelGasto.PASADO, d.hoy.nivel)
        assertEquals("Te pasaste de la meta de hoy por \$30.000", comoVieneHoy(d))

        val cerca = assertNotNull(
            calcular(recibidos = 3_100_000, checklist = emptyList(), gasto = mapOf("2026-09-21" to 90_000L)),
        )
        assertEquals(NivelDelGasto.CERCA, cerca.hoy.nivel)
        assertEquals("Te quedan \$10.000 para hoy", comoVieneHoy(cerca))
    }

    /** Lo que el dueño pidió explícito: con el período pasado, la semana y hoy siguen con su meta. */
    @Test
    fun `con el periodo pasado la semana y hoy siguen midiendo contra su meta`() {
        val d = assertNotNull(
            calcular(
                recibidos = 3_100_000, checklist = emptyList(),
                gasto = mapOf("2026-09-01" to 4_000_000L, "2026-09-21" to 20_000L),
            ),
        )
        assertEquals(NivelDelGasto.PASADO, d.periodo.nivel)
        assertEquals("Te pasaste por \$920.000", comoVieneElPeriodo(d))
        assertNull(d.porDiaParaLoQueQueda)

        assertEquals(400_000, d.semana.meta)
        assertEquals(100_000, d.hoy.meta)
        assertEquals(NivelDelGasto.BIEN, d.semana.nivel)
        assertEquals("Vas bien: te quedan \$380.000 para esta semana", comoVieneLaSemana(d))
        assertEquals("Te quedan \$80.000 para hoy", comoVieneHoy(d))
    }

    /**
     * Los números del dueño hoy: ~$22,2M de ingresos y fijos por encima. No hay nada que dividir:
     * las metas van en cero y la tarjeta no dibuja barras.
     */
    @Test
    fun `con los fijos por encima de los ingresos no hay metas ni barras`() {
        val d = assertNotNull(
            calcular(
                recibidos = 22_200_000,
                checklist = listOf(fijo("Hipotecas", 11_800_000), fijo("Créditos", 13_200_000, pagado = true)),
                gasto = mapOf("2026-09-21" to 90_000L),
            ),
        )

        assertEquals(-2_800_000, d.disponible)
        assertFalse(d.hayMargen)
        assertEquals(0, d.metaPorDia)
        assertEquals(0, d.periodo.meta)
        assertEquals(0, d.semana.meta)
        assertEquals(0, d.hoy.meta)
        assertNull(d.periodo.fraccion)
        assertNull(d.semana.fraccion)
        assertNull(d.hoy.fraccion)
        assertNull(d.porDiaParaLoQueQueda)
        // Lo gastado se sigue sabiendo: es lo único sobre lo que se puede actuar hoy.
        assertEquals(90_000, d.hoy.gastado)
        assertEquals(90_000, d.semana.gastado)
        assertEquals(90_000, d.periodo.gastado)
    }

    @Test
    fun `el ultimo dia del periodo`() {
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
        // Lo previsto a hoy es la meta entera.
        assertEquals(3_100_000, d.periodo.esperadoAHoy)
        assertEquals("Este período · último día", rotuloDelPeriodo(d))
        assertEquals("Te quedan \$2,1M para cerrar el período", comoVieneElPeriodo(d))
        // Lunes 21 al jueves 24, los cuatro corridos.
        assertEquals(4, d.diasDeLaSemana)
        assertEquals(4, d.diasCorridosDeLaSemana)
        assertEquals(50_000, d.hoy.gastado)
        assertEquals("Te quedan \$50.000 para hoy", comoVieneHoy(d))
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
    fun `las barras avisan por encima del ritmo o cerca del tope y se ponen rojas al pasarlo`() {
        val vacia = VentanaDelDisponible(gastado = 0, meta = 100_000, esperadoAHoy = 50_000)
        assertEquals(NivelDelGasto.BIEN, vacia.nivel)
        assertEquals(0f, vacia.fraccion)
        assertEquals(NivelDelGasto.CERCA, VentanaDelDisponible(gastado = 60_000, meta = 100_000, esperadoAHoy = 50_000).nivel)
        assertEquals(NivelDelGasto.CERCA, VentanaDelDisponible(gastado = 90_000, meta = 100_000, esperadoAHoy = 95_000).nivel)
        val pasada = VentanaDelDisponible(gastado = 150_000, meta = 100_000, esperadoAHoy = 100_000)
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
