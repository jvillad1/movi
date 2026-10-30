package com.jvillada.movi.ui.plan

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.DiaDeLaCaja
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.GastoVariableDeUnPeriodo
import com.jvillada.movi.shared.model.MovimientoDeLaCaja
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.ui.dashboard.DashboardData
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lo que dice «Tu plata, día a día» en Plan (Ola 4). La aritmética la prueba `CajaProyectadaTest`
 * en `:core`; acá se prueba que la pantalla la arme con la lista del período de Plan y que las
 * frases digan lo que pasa —fecha, cifra y por qué— sin inventar nada.
 */
class CajaDelPeriodoTest {

    private val hoy = LocalDate(2026, 10, 2)

    private fun dia(fecha: String, saldo: Long, vararg movimientos: Pair<String, Long>) =
        DiaDeLaCaja(fecha, saldo, movimientos.map { MovimientoDeLaCaja(it.first, it.second) })

    // ── Vuelta 75 · una cifra y una frase ──────────────────────────────────

    @Test
    fun `la frase corta dice hasta cuando y el dia mas justo, sin cifras`() {
        val caja = CajaProyectada(
            tuPlataHoy = 12_900_000L,
            gastoDiario = null,
            dias = listOf(
                dia("2026-10-02", 12_900_000L),
                dia("2026-10-17", 8_798_877L, "Cuota Vehiculo 8761" to -4_101_123L),
                dia("2026-10-25", 17_798_877L, "Llega tu salario (esperado)" to 9_000_000L),
            ),
            hastaElSalario = true,
        )
        assertEquals("Hasta el 25 oct · tu día más justo es el 17 oct", fraseCortaDeLaCaja(caja, hoy))
        assertTrue("\$" !in fraseCortaDeLaCaja(caja, hoy), "la cifra va arriba, una vez")
        assertTrue("te quedan" !in fraseCortaDeLaCaja(caja, hoy).lowercase(), "el verbo de «Te quedan» es del héroe")
        assertEquals("Libre en tu día más justo: \$8,8M", descripcionDeLaCifraDeLaCaja(caja))
        assertNull(fraseDelRojo(caja, hoy))
        assertNull(lineaSinSalario(caja), "con salario no hay línea encima")
    }

    @Test
    fun `si el dia mas justo es hoy, o no alcanza, la frase lo dice`() {
        val hoyEsElPeor = CajaProyectada(
            tuPlataHoy = 5_000_000L, gastoDiario = null,
            dias = listOf(DiaDeLaCaja("2026-10-02", 5_000_000L, libre = 3_000_000L), DiaDeLaCaja("2026-10-03", 5_100_000L, libre = 3_100_000L)),
        )
        assertEquals("Hasta el 3 oct · tu día más justo es hoy", fraseCortaDeLaCaja(hoyEsElPeor, hoy))
        val enRojo = CajaProyectada(
            tuPlataHoy = 1_000_000L, gastoDiario = null,
            dias = listOf(dia("2026-10-02", 1_000_000L), dia("2026-10-10", -683_361L, "Gimnasio" to -1_683_361L), dia("2026-10-25", 1_000_000L)),
        )
        assertEquals("Hasta el 25 oct · el 10 oct no te alcanza", fraseCortaDeLaCaja(enRojo, hoy))
        assertEquals("Libre en tu día más justo: −\$683\u00A0mil", descripcionDeLaCifraDeLaCaja(enRojo), "una sola forma de cifra: la corta")
    }

    /** El ejemplo de la revisión 4: «Te quedan» descuenta el ahorro y la caja ahora también. */
    @Test
    fun `que supone dice lo que habra en las cuentas y el ahorro, con cifras cortas`() {
        val ahorro = 8_000_000L
        val caja = CajaProyectada(
            tuPlataHoy = 10_500_000L,
            gastoDiario = null,
            dias = listOf(
                DiaDeLaCaja("2026-10-29", 10_500_000L, libre = 10_500_000L - ahorro),
                DiaDeLaCaja("2026-11-05", 6_137_000L, listOf(MovimientoDeLaCaja("Arriendo", -1_800_000L)), libre = 6_137_000L - ahorro),
                DiaDeLaCaja("2026-11-25", 13_137_000L, listOf(MovimientoDeLaCaja("Llega tu salario (esperado)", 7_000_000L)), libre = 13_137_000L - ahorro),
            ),
            ahorro = ahorro,
            hastaElSalario = true,
        )
        val octubre29 = LocalDate(2026, 10, 29)
        // Revisión de la 70: con el ahorro más grande que el saldo no dice «de eso $8M es tu ahorro» sobre $6,1M.
        assertEquals("En las cuentas habrá \$6,1M el 5 nov: menos que tu ahorro de \$8M.", lineaDeLasCuentas(caja, octubre29))
        assertEquals(
            "En las cuentas habrá \$6,1M el 5 nov: menos que tu ahorro y lo apartado (\$8,5M).",
            lineaDeLasCuentas(caja.copy(apartado = 500_000L, dias = caja.dias.map { it.copy(libre = it.saldo - 8_500_000L) }), octubre29),
        )
        val supone = loQueSupone(caja, GastoDelDiaADia(), octubre29, comparacion = null)
        assertEquals(lineaDeLasCuentas(caja, octubre29), supone.first(), "lo primero, lo que habrá en las cuentas")
        // La cifra de las cuentas no sale arriba: ni la leyenda ni la gráfica la repiten.
        assertEquals("Llega tu salario: +\$7M", leyendaDelCierre(caja))
        assertEquals("Libre el 25 nov, al llegar tu salario: \$5,1M", descripcionDelCierre(caja))
    }

    @Test
    fun `con un apartado lo dice tambien, y si el peor dia es hoy dice hay`() {
        val caja = CajaProyectada(
            tuPlataHoy = 5_000_000L,
            gastoDiario = null,
            dias = listOf(DiaDeLaCaja("2026-10-02", 5_000_000L, libre = 3_000_000L), DiaDeLaCaja("2026-10-03", 5_100_000L, libre = 3_100_000L)),
            ahorro = 1_500_000L,
            apartado = 500_000L,
        )
        assertEquals(
            "En las cuentas hay hoy \$5M; de eso \$1,5M es tu ahorro y \$500\u00A0mil está apartado.",
            lineaDeLasCuentas(caja, hoy),
        )
        assertNull(lineaDeLasCuentas(caja.copy(ahorro = 0L, apartado = 0L), hoy), "sin ahorro ni apartados, nada")
        assertEquals(TOCA_UN_DIA, leyendaDelCierre(caja))
    }

    @Test
    fun `sin salario la linea de encima lo dice, y con un ingreso fijo que no cuenta no`() {
        val caja = CajaProyectada(tuPlataHoy = 1_000_000L, gastoDiario = null, dias = listOf(dia("2026-10-02", 1_000_000L)), hastaElPeriodoSiguiente = true)
        assertEquals("Sin tu salario, Movi supone que no te entra nada", lineaSinSalario(caja))
        assertNull(lineaSinSalario(caja.copy(salarioQueNoCuenta = true)))
        assertTrue(
            deDondeSaleLaCaja(caja).first().contains("hasta el día que empieza tu próximo período"),
            deDondeSaleLaCaja(caja).first(),
        )
    }

    // ── Lo libre de hoy contra «Te quedan» (revisión de la 70), en «¿Qué supone?» ──

    /** El héroe dice «Te quedan $1,5M»; las cuentas tienen $2,6M libres hoy y $1,6M de deuda de tarjeta. */
    private val conAhorro = CajaProyectada(
        tuPlataHoy = 10_600_000L,
        gastoDiario = null,
        dias = listOf(
            DiaDeLaCaja("2026-10-29", 10_600_000L, libre = 2_600_000L),
            DiaDeLaCaja("2026-11-05", 10_000_000L, listOf(MovimientoDeLaCaja("Arriendo", -600_000L)), libre = 2_000_000L),
        ),
        ahorro = 8_000_000L,
    )
    private val octubre29 = LocalDate(2026, 10, 29)

    @Test
    fun `la diferencia con te quedan por las compras con tarjeta se nombra si las compras la explican`() {
        val c = ComparacionConTeQuedan(teQuedan = 1_500_000L, fijosQueFaltan = 0L, porRecibir = 0L, comprasConTarjetaSinPagar = 1_080_000L)
        val diferencia = "Hoy tienes \$2,6M libres, \$1,1M más que lo que te queda para gastar: tus compras con tarjeta se pagan después."
        assertEquals(diferencia, clausulaDeLaDiferencia(conAhorro, c))
        assertTrue(diferencia in loQueSupone(conAhorro, GastoDelDiaADia(), octubre29, c), "va en «¿Qué supone?»")
    }

    /**
     * Revisión de la 70 · **los contraejemplos**: tener deuda en la tarjeta no prueba la causa. Lo que sobra
     * puede ser tu parte de un gasto que pagó otro (Espacios), una liquidación que te pagaron o un apartado
     * cruzado con un fijo del período siguiente. Solo las compras **medidas** que lo explican la nombran.
     */
    @Test
    fun `sin compras con tarjeta medidas que la expliquen la diferencia se dice sin causa`() {
        val sinMedir = ComparacionConTeQuedan(teQuedan = 1_500_000L, fijosQueFaltan = 0L, porRecibir = 0L, comprasConTarjetaSinPagar = null)
        val sinCausa = "Hoy tienes \$2,6M libres, \$1,1M más que lo que te queda para gastar."
        assertEquals(sinCausa, clausulaDeLaDiferencia(conAhorro, sinMedir), "sin medirlas no se nombran")
        assertEquals(sinCausa, clausulaDeLaDiferencia(conAhorro, sinMedir.copy(comprasConTarjetaSinPagar = 300_000L)))
        assertEquals(sinCausa, clausulaDeLaDiferencia(conAhorro, sinMedir.copy(comprasConTarjetaSinPagar = 5_000_000L)))
        assertEquals(sinCausa, clausulaDeLaDiferencia(conAhorro, sinMedir.copy(comprasConTarjetaSinPagar = 0L)))
    }

    /** Lo libre de hoy es el del primer día, con el pago de hoy ya restado: una sola cifra de «hoy». */
    @Test
    fun `con un pago de hoy lo libre de hoy ya lo resta`() {
        val conPagoHoy = CajaProyectada(
            tuPlataHoy = 10_600_000L,
            gastoDiario = null,
            dias = listOf(
                DiaDeLaCaja("2026-10-29", 10_000_000L, listOf(MovimientoDeLaCaja("Arriendo", -600_000L)), libre = 2_000_000L),
                DiaDeLaCaja("2026-11-05", 10_100_000L, libre = 2_100_000L),
            ),
            ahorro = 8_000_000L,
        )
        val c = ComparacionConTeQuedan(teQuedan = 1_500_000L, fijosQueFaltan = 0L, porRecibir = 0L, comprasConTarjetaSinPagar = 500_000L)
        assertEquals(
            "Hoy tienes \$2M libres, \$500\u00A0mil más que lo que te queda para gastar: tus compras con tarjeta se pagan después.",
            clausulaDeLaDiferencia(conPagoHoy, c),
        )
    }

    @Test
    fun `si coinciden con te quedan, hasta mil pesos, no se dice nada`() {
        val c = ComparacionConTeQuedan(teQuedan = 2_599_200L, fijosQueFaltan = 0L, porRecibir = 0L, comprasConTarjetaSinPagar = null)
        assertNull(clausulaDeLaDiferencia(conAhorro, c))
        assertTrue(loQueSupone(conAhorro, GastoDelDiaADia(), octubre29, c).none { "lo que te queda para gastar" in it })
    }

    @Test
    fun `el primer dia que no alcanza se dice con el pago que lo causa si no es el peor`() {
        val caja = CajaProyectada(
            tuPlataHoy = 1_000_000L,
            gastoDiario = null,
            dias = listOf(
                dia("2026-10-02", 1_000_000L),
                dia("2026-10-10", -100_000L, "Gimnasio" to -1_100_000L),
                dia("2026-10-17", -4_201_123L, "Cuota Vehiculo 8761" to -4_101_123L),
            ),
        )
        val rojo = fraseDelRojo(caja, hoy)
        assertNotNull(rojo)
        assertTrue(rojo.startsWith("El 10 de octubre ya quedarías en"), rojo)
        assertTrue("por Gimnasio (\$1,1M)" in rojo, rojo)
        assertTrue(rojo in loQueSupone(caja, GastoDelDiaADia(), hoy, null), "va en «¿Qué supone?», no en rojo arriba")
        assertEquals("Hasta el 17 oct · ese día no te alcanza", fraseCortaDeLaCaja(caja, hoy))
        // Si el primero en rojo es el peor, ya lo dice la cifra: no se repite.
        assertNull(fraseDelRojo(caja.copy(dias = caja.dias.take(2)), hoy))
    }

    @Test
    fun `el supuesto dice de que periodos sale, y si no hay, que no hay`() {
        val uno = GastoDelDiaADia(100_000L, listOf(GastoVariableDeUnPeriodo("2026-09", 3_100_000L, 31)))
        assertEquals(
            "Supone que sigues como siempre: gastas en promedio \$100\u00A0mil por día (en tu período de septiembre).",
            fraseDelSupuesto(uno),
        )
        val dos = GastoDelDiaADia(
            100_000L,
            listOf(GastoVariableDeUnPeriodo("2026-09", 3_100_000L, 31), GastoVariableDeUnPeriodo("2026-08", 2_900_000L, 29)),
        )
        assertTrue("gastas en promedio \$100\u00A0mil por día (en tus últimos 2 períodos)" in fraseDelSupuesto(dos))
        assertTrue(fraseDelSupuesto(GastoDelDiaADia()).startsWith("Todavía no hay un período cerrado"))
    }

    @Test
    fun `lo que no entra se dice, y si entra todo no se dice nada`() {
        val caja = CajaProyectada(0L, null, listOf(dia("2026-10-02", 0L)), sinContar = listOf("AMEX: falta el pago mínimo"))
        assertEquals("No entra a la cuenta: AMEX: falta el pago mínimo.", fraseDeLoQueFalta(caja))
        assertNull(fraseDeLoQueFalta(caja.copy(sinContar = emptyList())))
    }

    /** La sección usa la lista «Pagos del período» de Plan y Tu plata de las cuentas. */
    @Test
    fun `la caja sale de la lista del periodo de plan y de tu plata`() {
        val arriendo = RecurringRule(
            id = "rr_arriendo", name = "Arriendo", category = "Vivienda",
            amount = 2_000_000L, dayOfMonth = 5, type = TransactionType.EXPENSE,
        )
        val data = DashboardData(
            accounts = listOf(
                Account("a1", "Bancolombia", AccountType.SAVINGS, 5_000_000L, "COP"),
                Account("tc", "Master Black", AccountType.CREDIT_CARD, 27_000_000L, "COP"),
            ),
            upcoming = listOf(UpcomingPayment(arriendo, "2026-10-05", 3, PaymentStatus.UPCOMING)),
            ocurrencias = emptyList(),
            ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
            periodoActual = PeriodoFinanciero(2026, 10),
        )
        val caja = cajaDelPeriodoDe(data, GastoDelDiaADia(), hoy)
        assertNotNull(caja)
        assertEquals(5_000_000L, caja.tuPlataHoy, "la tarjeta es deuda: no está en Tu plata")
        // Vuelta 75: sin un ingreso recurrente y con el período elegido (corte 25), la caja llega al arranque
        // del siguiente —el 25 de octubre—, no a 30 días (eran hasta el 1 de noviembre).
        assertEquals("2026-10-25", caja.alCierre.fecha)
        assertTrue(caja.hastaElPeriodoSiguiente)
        assertEquals(3_000_000L, caja.alCierre.saldo)
        assertNull(cajaDelPeriodoDe(data.copy(ocurrencias = null), GastoDelDiaADia(), hoy), "sin la lista no se proyecta")
    }

    /**
     * Vuelta 70: la sección mira hasta el salario, cuenta el pago del período siguiente que vence antes y
     * descuenta el ahorro del período de lo libre (el mismo de «Te quedan»).
     */
    @Test
    fun `la caja de pagos llega al salario, con el pago del siguiente y sin el ahorro en lo libre`() {
        val octubre = LocalDate(2026, 10, 29)
        fun regla(id: String, monto: Long, dia: Int, tipo: TransactionType = TransactionType.EXPENSE) =
            RecurringRule(id = id, name = id, category = "Varios", amount = monto, dayOfMonth = dia, type = tipo)
        val data = DashboardData(
            accounts = listOf(Account("a1", "Bancolombia", AccountType.SAVINGS, 10_500_000L, "COP")),
            upcoming = listOf(
                UpcomingPayment(regla("Varios", 9_000L, 30), "2026-10-30", 1, PaymentStatus.UPCOMING),
                UpcomingPayment(regla("Arriendo", 1_800_000L, 5), "2026-11-05", 7, PaymentStatus.UPCOMING),
                UpcomingPayment(regla("Salario", 7_000_000L, 25, TransactionType.INCOME), "2026-11-25", 27, PaymentStatus.UPCOMING),
            ),
            ocurrencias = emptyList(),
            ajustesDePeriodo = PeriodSettings(cutoffDay = 1),
            periodoActual = PeriodoFinanciero(2026, 10),
            plataDelDisponible = com.jvillada.movi.shared.model.PlataDelDisponible(
                saldoAlInicio = 0L, entradas = 0L, guardado = 0L, ahorroDelPeriodo = 8_000_000L,
            ),
        )
        val caja = assertNotNull(cajaDelPeriodoDe(data, GastoDelDiaADia(), octubre))
        assertEquals("2026-11-25", caja.alCierre.fecha)
        assertTrue(caja.hastaElSalario)
        assertEquals(8_000_000L, caja.ahorro)
        assertEquals(10_500_000L - 9_000L - 1_800_000L, caja.diaMasBajo.saldo)
        assertEquals(10_500_000L - 9_000L - 1_800_000L - 8_000_000L, caja.diaMasBajo.libre)
        assertEquals("2026-11-05", caja.diaMasBajo.fecha)
    }

    /**
     * El colegio apartado que además es un pago pendiente: la caja lo resta una vez, el día que vence.
     * Tu plata ya resta los $8M apartados; los $4M que el pago cubre se devuelven al arrancar.
     */
    @Test
    fun `el apartado que cubre un pago pendiente no se resta dos veces`() {
        val colegio = RecurringRule(
            id = "rr_colegio", name = "Colegio Hija", category = "Hija",
            amount = 4_000_000L, dayOfMonth = 20, type = TransactionType.EXPENSE, accountId = "nu",
        )
        val data = DashboardData(
            accounts = listOf(Account("nu", "Nu", AccountType.SAVINGS, 8_217_464L, "COP")),
            apartados = listOf(
                Apartado(id = "ap", accountId = "nu", proposito = "Colegio", monto = 8_000_000L, categoria = "Hija"),
            ),
            upcoming = listOf(UpcomingPayment(colegio, "2026-10-20", 18, PaymentStatus.UPCOMING)),
            ocurrencias = emptyList(),
            ajustesDePeriodo = PeriodSettings(cutoffDay = 25),
            periodoActual = PeriodoFinanciero(2026, 10),
        )
        val caja = assertNotNull(cajaDelPeriodoDe(data, GastoDelDiaADia(), hoy))

        // Vuelta 70: parte de lo que hay en las cuentas (lo apartado sigue ahí) y lo libre resta lo apartado
        // que el pago no cubre: $4M.
        assertEquals(8_217_464L, caja.tuPlataHoy)
        assertEquals(4_217_464L, caja.libreHoy)
        assertEquals(217_464L, caja.alCierre.libre, "lo libre de Nu, no $4M menos")
    }

    // ── Ola 4 · Ver la plata: la caja con forma ─────────────────────────────

    @Test
    fun `la escala se ajusta al rango de los datos y no arranca en cero`() {
        val (piso, tope) = escalaDeLaCaja(listOf(8_000_000L, 7_400_000L, 6_200_000L, 6_800_000L))
        // Un sexto del rango (1,8M) de aire a cada lado: ni cero abajo ni la línea aplastada.
        assertEquals(5_900_000L, piso)
        assertEquals(8_300_000L, tope)
    }

    @Test
    fun `con datos que cruzan el cero la escala lo incluye, y una linea plana no queda sin rango`() {
        val (piso, tope) = escalaDeLaCaja(listOf(600_000L, -300_000L))
        assertTrue(piso < -300_000L && tope > 600_000L)
        val (pisoPlano, topePlano) = escalaDeLaCaja(listOf(2_000_000L, 2_000_000L))
        assertTrue(pisoPlano < 2_000_000L && topePlano > 2_000_000L)
        val (pisoCero, topeCero) = escalaDeLaCaja(listOf(0L, 0L))
        assertTrue(pisoCero < 0L && topeCero > 0L)
    }

    @Test
    fun `los marcadores van en los dias con un pago pendiente y la leyenda los nombra`() {
        val caja = CajaProyectada(
            tuPlataHoy = 12_900_000L,
            gastoDiario = null,
            dias = listOf(
                dia("2026-10-02", 12_900_000L),
                dia("2026-10-17", 8_798_877L, "Cuota Vehiculo 8761" to -4_101_123L, "Netflix" to -45_000L),
                dia("2026-10-20", 17_798_877L, "Sueldo" to 9_000_000L),
            ),
        )
        // El sueldo no es un pago: solo el 17 lleva marcador.
        assertEquals(listOf(1), diasConPago(caja))
        assertEquals("17 oct: \$8,8M libres · Cuota Vehiculo 8761, Netflix", leyendaDelDiaDeLaCaja(caja.dias[1]))
        assertEquals("2 oct: \$12,9M libres", leyendaDelDiaDeLaCaja(caja.dias[0]))
    }
}
