package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **La caja proyectada día a día** (Ola 4): parte de Tu plata, resta lo pendiente de la lista del
 * período el día que vence, suma lo que falta por cobrar, y resta el gasto del día a día desde
 * mañana. Montos de su período real: del 25 de septiembre al 24 de octubre.
 */
class CajaProyectadaTest {

    private fun pago(
        nombre: String,
        monto: Long,
        vence: String,
        pagado: Boolean = false,
        esIngreso: Boolean = false,
        montoEsSaldo: Boolean = false,
        pagoMinimo: Long? = null,
        moneda: String = "COP",
    ) = PagoDelPeriodo(
        ruleId = "rr_$nombre",
        nombre = nombre,
        monto = monto,
        pagado = pagado,
        diasParaVencer = 0,
        montoEsSaldo = montoEsSaldo,
        pagoMinimo = pagoMinimo,
        moneda = moneda,
        vence = vence,
        esIngreso = esIngreso,
    )

    @Test
    fun `el dia mas bajo es el de la cuota del carro, antes del sueldo`() {
        val caja = cajaProyectada(
            tuPlataHoy = 12_900_000L,
            pagos = listOf(
                pago("Arriendo", 2_000_000L, "2026-10-05"),
                pago("Cuota Vehiculo 8761", 4_101_123L, "2026-10-17"),
                pago("Sueldo", 9_000_000L, "2026-10-20", esIngreso = true),
            ),
            gastoDiario = 100_000L,
            hoy = "2026-10-02",
            ultimoDia = "2026-10-24",
        )
        assertNotNull(caja)
        assertEquals(23, caja.dias.size)
        val bajo = caja.diaMasBajo
        assertEquals("2026-10-19", bajo.fecha, "el último día antes de que llegue el sueldo")
        // 12.900.000 − 2.000.000 − 4.101.123 − 17 días × 100.000
        assertEquals(12_900_000L - 2_000_000L - 4_101_123L - 17 * 100_000L, bajo.saldo)
        assertEquals("Cuota Vehiculo 8761", caja.dias.first { it.fecha == "2026-10-17" }.pagoMasGrande?.nombre)
        assertNull(caja.primerDiaEnRojo)
        // El sueldo entra el 20.
        val dia20 = caja.dias.first { it.fecha == "2026-10-20" }
        assertEquals(bajo.saldo - 100_000L + 9_000_000L, dia20.saldo)
    }

    @Test
    fun `hoy no se le resta un dia entero de gasto, desde manana si`() {
        val caja = cajaProyectada(1_000_000L, emptyList(), 50_000L, "2026-10-22", "2026-10-24")!!
        assertEquals(listOf(1_000_000L, 950_000L, 900_000L), caja.dias.map { it.saldo })
    }

    @Test
    fun `un pago ya hecho no se resta dos veces`() {
        val caja = cajaProyectada(
            tuPlataHoy = 5_000_000L,
            pagos = listOf(pago("Celular", 53_000L, "2026-10-10", pagado = true)),
            gastoDiario = null,
            hoy = "2026-10-02",
            ultimoDia = "2026-10-24",
        )!!
        assertTrue(caja.dias.all { it.saldo == 5_000_000L }, "el celular ya salió: Tu plata ya lo tiene adentro")
        assertTrue(caja.sinContar.isEmpty())
    }

    @Test
    fun `la tarjeta paga su minimo, no su saldo`() {
        val caja = cajaProyectada(
            tuPlataHoy = 5_000_000L,
            pagos = listOf(
                pago("Pago tarjeta Master Black", 27_647_837L, "2026-10-02", montoEsSaldo = true, pagoMinimo = 1_843_014L),
            ),
            gastoDiario = null,
            hoy = "2026-10-02",
            ultimoDia = "2026-10-24",
        )!!
        assertEquals(5_000_000L - 1_843_014L, caja.alCierre.saldo)
        assertEquals(-1_843_014L, caja.dias.first().movimientos.single().monto)
    }

    @Test
    fun `una tarjeta sin minimo no se inventa y se dice que falta`() {
        val caja = cajaProyectada(
            tuPlataHoy = 5_000_000L,
            pagos = listOf(pago("Pago tarjeta AMEX", 19_347_221L, "2026-10-08", montoEsSaldo = true)),
            gastoDiario = null,
            hoy = "2026-10-02",
            ultimoDia = "2026-10-24",
        )!!
        assertEquals(5_000_000L, caja.alCierre.saldo)
        assertEquals(listOf("Pago tarjeta AMEX: falta el pago mínimo"), caja.sinContar)
    }

    @Test
    fun `un pago en dolares no entra y se dice`() {
        val caja = cajaProyectada(
            tuPlataHoy = 5_000_000L,
            pagos = listOf(pago("Railway", 20L, "2026-10-08", moneda = "USD")),
            gastoDiario = null,
            hoy = "2026-10-02",
            ultimoDia = "2026-10-24",
        )!!
        assertEquals(5_000_000L, caja.alCierre.saldo)
        assertEquals(listOf("Railway: está en USD"), caja.sinContar)
    }

    @Test
    fun `el periodo que cruza de mes pone cada pago en su dia`() {
        val caja = cajaProyectada(
            tuPlataHoy = 3_000_000L,
            pagos = listOf(
                pago("Gimnasio", 180_000L, "2026-09-30"),
                pago("Arriendo", 2_000_000L, "2026-10-05"),
            ),
            gastoDiario = null,
            hoy = "2026-09-25",
            ultimoDia = "2026-10-24",
        )!!
        assertEquals(30, caja.dias.size)
        assertEquals("2026-09-25", caja.dias.first().fecha)
        assertEquals("2026-10-24", caja.dias.last().fecha)
        assertEquals(2_820_000L, caja.dias.first { it.fecha == "2026-09-30" }.saldo)
        assertEquals(3_000_000L, caja.dias.first { it.fecha == "2026-09-29" }.saldo)
        assertEquals(820_000L, caja.dias.first { it.fecha == "2026-10-05" }.saldo)
    }

    @Test
    fun `lo vencido y sin pagar sale hoy, y una fecha en rojo se nombra`() {
        val caja = cajaProyectada(
            tuPlataHoy = 1_000_000L,
            pagos = listOf(
                pago("Coomeva", 300_000L, "2026-09-28"),
                pago("Cuota Vehiculo 8761", 4_101_123L, "2026-10-17"),
            ),
            gastoDiario = null,
            hoy = "2026-10-02",
            ultimoDia = "2026-10-24",
        )!!
        assertEquals(700_000L, caja.dias.first().saldo)
        val rojo = caja.primerDiaEnRojo
        assertNotNull(rojo)
        assertEquals("2026-10-17", rojo.fecha)
        assertEquals("Cuota Vehiculo 8761", rojo.pagoMasGrande?.nombre)
    }

    @Test
    fun `sin dias que proyectar no hay caja`() {
        assertNull(cajaProyectada(1L, emptyList(), null, "2026-10-25", "2026-10-24"))
        assertNull(cajaProyectada(1L, emptyList(), null, "hoy", "2026-10-24"))
    }

    // ── El gasto del día a día ──────────────────────────────────────────────

    @Test
    fun `el gasto diario es el total sobre los dias, de los ultimos tres periodos`() {
        val gasto = gastoDelDiaADia(
            listOf(
                GastoVariableDeUnPeriodo("2026-09", 3_100_000L, 31),
                GastoVariableDeUnPeriodo("2026-08", 2_900_000L, 29),
                GastoVariableDeUnPeriodo("2026-07", 3_000_000L, 30),
                GastoVariableDeUnPeriodo("2026-06", 90_000_000L, 31),
            ),
        )
        assertEquals(100_000L, gasto.porDia)
        assertEquals(listOf("2026-09", "2026-08", "2026-07"), gasto.periodos.map { it.periodo })
    }

    @Test
    fun `sin periodos cerrados no hay supuesto`() {
        assertNull(gastoDelDiaADia(emptyList()).porDia)
    }
}
