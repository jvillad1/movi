package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 70 · «¿Me alcanza hasta que me paguen?»
 *
 * La caja llega hasta el próximo salario (o 30 días sin él), con los pagos del período siguiente que
 * vencen antes, con la misma regla de siempre; y su cifra titular es **lo libre**: el saldo menos el
 * ahorro del período y lo apartado fuera de los fijos, sin restar dos veces un apartado que es un fijo.
 *
 * Las filas salen de [checklistDelPeriodo] sobre respuestas armadas a mano, como las da el server: el
 * mismo camino que usan Pagos, Inicio y Movi AI.
 */
class CajaHastaQueTePaguenTest {

    /** El período del dueño: corte 25, y si cae en fin de semana o festivo, el día hábil anterior. */
    private val delDueno = PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)

    /** El mes de calendario (el usuario demo): el período cierra el 31. */
    private val mesDeCalendario = PeriodSettings(cutoffDay = 1)

    private fun regla(
        id: String,
        monto: Long,
        dia: Int,
        tipo: TransactionType = TransactionType.EXPENSE,
        montoEsSaldo: Boolean = false,
        pagoMinimo: Long? = null,
        cuenta: String? = null,
        categoria: String = "Varios",
    ) = RecurringRule(
        id = id, name = id.replaceFirstChar { it.uppercase() }, category = categoria, amount = monto, dayOfMonth = dia,
        type = tipo, montoEsSaldo = montoEsSaldo, pagoMinimoCop = pagoMinimo, accountId = cuenta,
    )

    /** Un vencimiento como lo da `/api/payments/upcoming`, con `daysUntil` contado desde [hoy]. */
    private fun vence(regla: RecurringRule, fecha: String, hoy: String) = UpcomingPayment(
        rule = regla,
        dueDate = fecha,
        daysUntil = LocalDate.parse(hoy).daysUntil(LocalDate.parse(fecha)),
        status = PaymentStatus.UPCOMING,
    )

    private fun ultimoDia(periodo: PeriodoFinanciero, ajustes: PeriodSettings): String =
        inicioDelPeriodo(periodoSiguiente(periodo), ajustes).minus(1, DateTimeUnit.DAY).toString()

    private class Listas(val periodo: PeriodoFinanciero, val delPeriodo: List<PagoDelPeriodo>, val delSiguiente: List<PagoDelPeriodo>, val ultimoDia: String)

    private fun listas(hoy: String, ajustes: PeriodSettings, upcoming: List<UpcomingPayment>, ocurrencias: List<OccurrenceState> = emptyList()): Listas {
        val periodo = assertNotNull(periodoDeLaFecha(hoy, ajustes))
        return Listas(
            periodo = periodo,
            delPeriodo = checklistDelPeriodo(upcoming, ocurrencias, periodo, ajustes),
            delSiguiente = checklistDelPeriodo(upcoming, ocurrencias, periodoSiguiente(periodo), ajustes),
            ultimoDia = ultimoDia(periodo, ajustes),
        )
    }

    // ── El horizonte ─────────────────────────────────────────────────────────

    @Test
    fun `con el periodo del dueno la caja llega al dia del salario, ese dia incluido`() {
        val hoy = "2026-10-29"
        val salario = regla("salario", 9_000_000L, 25, tipo = TransactionType.INCOME)
        val l = listas(
            hoy, delDueno,
            listOf(
                vence(salario, "2026-11-25", hoy),
                vence(regla("arriendo", 1_800_000L, 5), "2026-11-05", hoy),
            ),
        )
        assertEquals("2026-11-24", l.ultimoDia, "el 25-nov es miércoles: el período siguiente arranca ese día")
        val horizonte = assertNotNull(horizonteDeLaCaja(hoy, l.ultimoDia, l.delSiguiente))
        assertEquals("2026-11-25", horizonte.hasta.toString())
        assertEquals("salario", horizonte.salario?.ruleId)

        val caja = assertNotNull(cajaHastaQueTePaguen(10_000_000L, l.delPeriodo, l.delSiguiente, null, hoy, l.ultimoDia))
        assertEquals("2026-11-25", caja.alCierre.fecha)
        assertTrue(caja.hastaElSalario)
        assertEquals(LLEGA_TU_SALARIO, caja.salario?.nombre)
    }

    @Test
    fun `con el corte en festivo el salario llega el dia habil anterior y la caja termina ahi`() {
        // El 25-dic-2026 es viernes y festivo: el período arranca (y el salario se espera) el jueves 24.
        val hoy = "2026-12-20"
        val salario = regla("salario", 9_000_000L, 25, tipo = TransactionType.INCOME)
        val l = listas(hoy, delDueno, listOf(vence(salario, "2026-12-24", hoy)))
        assertEquals("2026-12-23", l.ultimoDia)
        val caja = assertNotNull(cajaHastaQueTePaguen(1_000_000L, l.delPeriodo, l.delSiguiente, null, hoy, l.ultimoDia))
        assertEquals("2026-12-24", caja.alCierre.fecha)
        assertEquals(10_000_000L, caja.alCierre.saldo, "el salario sube el último día")
    }

    @Test
    fun `sin un ingreso recurrente la caja mira 30 dias, y nunca menos que el cierre`() {
        val hoy = "2026-10-29"
        val l = listas(hoy, mesDeCalendario, listOf(vence(regla("arriendo", 1_800_000L, 5), "2026-11-05", hoy)))
        assertEquals("2026-10-31", l.ultimoDia)
        val horizonte = assertNotNull(horizonteDeLaCaja(hoy, l.ultimoDia, l.delSiguiente))
        assertEquals("2026-11-28", horizonte.hasta.toString())
        assertNull(horizonte.salario)

        // El primer día de un período de 31 días: 30 días más no alcanzan al cierre; manda el cierre.
        val largo = assertNotNull(horizonteDeLaCaja("2026-10-01", "2026-10-31", emptyList()))
        assertEquals("2026-10-31", largo.hasta.toString())
    }

    @Test
    fun `el salario que ya llego no cierra el horizonte`() {
        val hoy = "2026-10-29"
        val salario = PagoDelPeriodo(
            ruleId = "salario", nombre = "Salario", monto = 9_000_000L, pagado = true, diasParaVencer = 3,
            vence = "2026-11-01", esIngreso = true,
        )
        val horizonte = assertNotNull(horizonteDeLaCaja(hoy, "2026-10-31", listOf(salario)))
        assertNull(horizonte.salario)
        assertEquals("2026-11-28", horizonte.hasta.toString())
    }

    // ── Los pagos del período siguiente, con la regla de siempre ─────────────

    @Test
    fun `los pagos del periodo siguiente dentro del horizonte entran el dia que vencen`() {
        // 29-oct, el período cierra el 31: el 5-nov vencen el arriendo, la cuota y la tarjeta Nu (que paga
        // su mínimo, no su saldo). Lo que vence pasado el horizonte no entra.
        val hoy = "2026-10-29"
        val l = listas(
            hoy, mesDeCalendario,
            listOf(
                vence(regla("varios", 9_000L, 30), "2026-10-30", hoy),
                vence(regla("arriendo", 1_800_000L, 5), "2026-11-05", hoy),
                vence(regla("cuota", 980_000L, 5), "2026-11-05", hoy),
                vence(regla("nu", 6_000_000L, 5, montoEsSaldo = true, pagoMinimo = 1_583_000L), "2026-11-05", hoy),
                vence(regla("seguro", 300_000L, 29), "2026-11-29", hoy),
            ),
        )
        assertEquals(9_000L, faltaPorPagar(l.delPeriodo), "«Falta por pagar» sigue siendo del período")
        val caja = assertNotNull(cajaHastaQueTePaguen(10_500_000L, l.delPeriodo, l.delSiguiente, null, hoy, l.ultimoDia))
        assertEquals("2026-11-28", caja.alCierre.fecha)
        val cinco = caja.dias.first { it.fecha == "2026-11-05" }
        assertEquals(setOf("Arriendo" to -1_800_000L, "Cuota" to -980_000L, "Nu" to -1_583_000L), cinco.movimientos.map { it.nombre to it.monto }.toSet())
        assertEquals(10_500_000L - 9_000L - 1_800_000L - 980_000L - 1_583_000L, caja.alCierre.saldo, "el seguro del 29 queda afuera")
        assertEquals("2026-11-05", caja.diaMasBajo.fecha)
    }

    @Test
    fun `un pago del periodo siguiente ya saldado no se cuenta`() {
        val hoy = "2026-10-29"
        val arriendo = regla("arriendo", 1_800_000L, 5)
        val l = listas(
            hoy, mesDeCalendario,
            listOf(vence(arriendo, "2026-11-05", hoy)),
            ocurrencias = listOf(OccurrenceState(ruleId = "arriendo", period = "2026-11", dueDate = "2026-11-05", occurred = true)),
        )
        assertTrue(l.delSiguiente.single().pagado)
        val caja = assertNotNull(cajaHastaQueTePaguen(5_000_000L, l.delPeriodo, l.delSiguiente, null, hoy, l.ultimoDia))
        assertTrue(caja.dias.all { it.saldo == 5_000_000L }, "lo pagado ya está adentro de las cuentas")
    }

    @Test
    fun `una tarjeta del periodo siguiente sin minimo no se inventa y se dice una vez`() {
        val hoy = "2026-10-29"
        val nu = regla("nu", 6_000_000L, 5, montoEsSaldo = true)
        val l = listas(hoy, mesDeCalendario, listOf(vence(nu, "2026-11-05", hoy)))
        val caja = assertNotNull(cajaHastaQueTePaguen(5_000_000L, l.delPeriodo, l.delSiguiente, null, hoy, l.ultimoDia))
        assertTrue(caja.dias.all { it.saldo == 5_000_000L })
        assertEquals(listOf("Nu: falta el pago mínimo"), caja.sinContar)
    }

    @Test
    fun `el salario entra como subida, rotulado, el dia que se espera`() {
        val hoy = "2026-10-29"
        val l = listas(
            hoy, mesDeCalendario,
            listOf(
                vence(regla("salario", 7_000_000L, 1, tipo = TransactionType.INCOME), "2026-11-01", hoy),
                vence(regla("arriendo", 1_800_000L, 1), "2026-11-01", hoy),
            ),
        )
        val caja = assertNotNull(cajaHastaQueTePaguen(2_000_000L, l.delPeriodo, l.delSiguiente, 100_000L, hoy, l.ultimoDia))
        assertEquals("2026-11-01", caja.alCierre.fecha)
        val ultimo = caja.alCierre
        assertEquals(MovimientoDeLaCaja(LLEGA_TU_SALARIO, 7_000_000L), ultimo.movimientos.first { it.monto > 0 })
        // Tres días de gasto (30, 31 y 1), el arriendo del 1 y el salario del 1.
        assertEquals(2_000_000L - 3 * 100_000L - 1_800_000L + 7_000_000L, ultimo.saldo)
        assertEquals("2026-10-31", caja.diaMasBajo.fecha, "la víspera del salario")
    }

    // ── Lo libre: ahorro y apartados ────────────────────────────────────────

    @Test
    fun `lo libre resta el ahorro del periodo y el saldo no cambia`() {
        val hoy = "2026-10-29"
        val l = listas(hoy, mesDeCalendario, listOf(vence(regla("arriendo", 1_800_000L, 5), "2026-11-05", hoy)))
        val sin = assertNotNull(cajaHastaQueTePaguen(10_500_000L, l.delPeriodo, l.delSiguiente, 50_000L, hoy, l.ultimoDia))
        val con = assertNotNull(cajaHastaQueTePaguen(10_500_000L, l.delPeriodo, l.delSiguiente, 50_000L, hoy, l.ultimoDia, ahorro = 8_000_000L))
        assertEquals(sin.dias.map { it.saldo }, con.dias.map { it.saldo }, "el ahorro no mueve plata")
        assertEquals(sin.dias.map { it.libre - 8_000_000L }, con.dias.map { it.libre })
        assertEquals(2_500_000L, con.libreHoy)
        assertEquals(8_000_000L, con.reservado)
    }

    @Test
    fun `un ahorro mas grande que lo que sobra deja lo libre en rojo, no el saldo`() {
        val hoy = "2026-10-29"
        val l = listas(hoy, mesDeCalendario, listOf(vence(regla("arriendo", 1_800_000L, 5), "2026-11-05", hoy)))
        val caja = assertNotNull(cajaHastaQueTePaguen(9_000_000L, l.delPeriodo, l.delSiguiente, null, hoy, l.ultimoDia, ahorro = 8_000_000L))
        val rojo = assertNotNull(caja.primerDiaEnRojo)
        assertEquals("2026-11-05", rojo.fecha)
        assertEquals(-800_000L, rojo.libre)
        assertEquals(7_200_000L, rojo.saldo)
    }

    /**
     * El colegio: apartados $8M en Nu, y es un fijo de $4M en este período y otro de $4M en el siguiente
     * (dentro del horizonte). Los dos pagos los resta la caja el día que vencen, así que de lo apartado
     * no queda nada que reservar: restarlo también sería contarlo dos veces.
     */
    @Test
    fun `un apartado que es un fijo nunca se resta dos veces, tampoco el del periodo siguiente`() {
        val hoy = "2026-10-29"
        val cuentas = listOf(Account("nu", "Nu", AccountType.SAVINGS, 8_217_464L, "COP"))
        val apartados = listOf(Apartado(id = "ap", accountId = "nu", proposito = "Colegio", monto = 8_000_000L, categoria = "Hija"))
        val colegio = regla("colegio", 4_000_000L, 31, cuenta = "nu", categoria = "Hija")
        val colegioNov = regla("colegio-nov", 4_000_000L, 20, cuenta = "nu", categoria = "Hija")
        val l = listas(hoy, mesDeCalendario, listOf(vence(colegio, "2026-10-31", hoy), vence(colegioNov, "2026-11-20", hoy)))

        val p = patrimonioDe(cuentas, apartados)
        val enLasCuentas = p.tuPlata + p.apartado
        assertEquals(8_217_464L, enLasCuentas)
        val caja = assertNotNull(
            cajaHastaQueTePaguen(enLasCuentas, l.delPeriodo, l.delSiguiente, null, hoy, l.ultimoDia) {
                apartadoFueraDeLosFijos(cuentas, apartados, it)
            },
        )
        assertEquals(0L, caja.apartado, "los $8M los restan los dos pagos")
        assertEquals(217_464L, caja.alCierre.libre)
        assertEquals(217_464L, caja.alCierre.saldo)

        // Con solo el fijo de este período, la mitad del apartado sigue reservada: Te quedan resta igual.
        val soloEste = assertNotNull(
            cajaHastaQueTePaguen(enLasCuentas, l.delPeriodo, emptyList(), null, hoy, l.ultimoDia) {
                apartadoFueraDeLosFijos(cuentas, apartados, it)
            },
        )
        assertEquals(apartadoFueraDeLosFijos(cuentas, apartados, l.delPeriodo), soloEste.apartado)
        assertEquals(4_000_000L, soloEste.apartado)
        assertEquals(217_464L, soloEste.diaMasBajo.libre)
    }

    // ── Las invariantes ─────────────────────────────────────────────────────

    @Test
    fun `hoy lo libre es tu plata menos el ahorro menos lo apartado con la regla del cruce`() {
        val hoy = "2026-10-29"
        val cuentas = listOf(
            Account("nu", "Nu", AccountType.SAVINGS, 12_000_000L, "COP"),
            Account("ba", "Bancolombia", AccountType.CHECKING, 3_000_000L, "COP"),
        )
        val apartados = listOf(
            Apartado(id = "c", accountId = "nu", proposito = "Colegio", monto = 6_000_000L, categoria = "Hija"),
            Apartado(id = "s", accountId = "ba", proposito = "SOAT", monto = 1_000_000L),
        )
        val l = listas(
            hoy, delDueno,
            listOf(
                vence(regla("colegio", 4_000_000L, 5, cuenta = "nu", categoria = "Hija"), "2026-11-05", hoy),
                vence(regla("salario", 9_000_000L, 25, tipo = TransactionType.INCOME), "2026-11-25", hoy),
            ),
        )
        val p = patrimonioDe(cuentas, apartados)
        val ahorro = 2_000_000L
        val caja = assertNotNull(
            cajaHastaQueTePaguen(p.tuPlata + p.apartado, l.delPeriodo, l.delSiguiente, 80_000L, hoy, l.ultimoDia, ahorro) {
                apartadoFueraDeLosFijos(cuentas, apartados, it)
            },
        )
        val filas = filasDeLaCaja(l.delPeriodo, l.delSiguiente, caja.alCierre.fecha)
        // Con la regla del cruce: lo de las cuentas − ahorro − lo apartado fuera de los fijos …
        assertEquals(p.tuPlata + p.apartado - ahorro - apartadoFueraDeLosFijos(cuentas, apartados, filas), caja.libreHoy)
        // … que es Tu plata + lo apartado que cubre un fijo − ahorro.
        assertEquals(p.tuPlata + apartadoCubiertoPorLosFijos(cuentas, apartados, filas) - ahorro, caja.libreHoy)
        assertEquals(15_000_000L - 2_000_000L - 3_000_000L, caja.libreHoy, "$6M+$1M apartados, $4M los cubre el colegio")
        // Todos los días, lo libre es el saldo menos lo mismo.
        assertTrue(caja.dias.all { it.saldo - it.libre == caja.reservado })
        // Sin ahorro ni apartados, lo libre ES el saldo: la caja de siempre.
        val sinNada = assertNotNull(cajaHastaQueTePaguen(p.tuPlata, l.delPeriodo, l.delSiguiente, 80_000L, hoy, l.ultimoDia))
        assertTrue(sinNada.dias.all { it.libre == it.saldo })
        assertFalse(sinNada.reservado > 0L)
    }

    // ── Lo que viene ────────────────────────────────────────────────────────

    @Test
    fun `con siete dias o menos del periodo, lo que viene trae los proximos 15 dias y su total`() {
        val hoy = "2026-10-29"
        val upcoming = listOf(
            vence(regla("arriendo", 1_800_000L, 5), "2026-11-05", hoy),
            vence(regla("cuota", 980_000L, 5), "2026-11-05", hoy),
            vence(regla("nu", 6_000_000L, 5, montoEsSaldo = true, pagoMinimo = 1_583_000L), "2026-11-05", hoy),
            vence(regla("salario", 7_000_000L, 1, tipo = TransactionType.INCOME), "2026-11-01", hoy),
            vence(regla("seguro", 300_000L, 20), "2026-11-20", hoy),
        )
        val viene = assertNotNull(loQueViene(upcoming, emptyList(), PeriodoFinanciero(2026, 10), mesDeCalendario))
        assertEquals(listOf("Arriendo", "Cuota", "Nu"), viene.filas.map { it.nombre }.sorted())
        assertEquals(2_780_000L, viene.total, "con la regla de «Falta por pagar»: sin el saldo de la tarjeta")

        // A mitad de período no va.
        val temprano = upcoming.map { it.copy(daysUntil = it.daysUntil + 14) }
        assertNull(loQueViene(temprano, emptyList(), PeriodoFinanciero(2026, 10), mesDeCalendario))
    }
}
