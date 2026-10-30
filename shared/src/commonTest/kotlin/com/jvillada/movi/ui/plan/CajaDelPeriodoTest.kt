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

    @Test
    fun `una sola frase con la cifra libre, hasta el salario, y el peor dia`() {
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
        assertEquals("Hasta el 25 de oct, en tu peor día (el 17 de oct) tendrás \$8,8M libres en tus cuentas", fraseDePuente(caja, hoy))
        assertTrue("te quedan" !in fraseDePuente(caja, hoy), "el verbo de «Te quedan» es del héroe")
        assertNull(fraseDelRojo(caja, hoy))
    }

    /** El ejemplo de la revisión 4: «Te quedan» descuenta el ahorro y la caja ahora también. */
    @Test
    fun `con ahorro la frase dice lo libre y lo que habra en las cuentas, una vez`() {
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
        val frase = fraseDePuente(caja, LocalDate(2026, 10, 29))
        // Revisión de la 70: con el ahorro más grande que el saldo no dice «de eso $8M es tu ahorro» sobre $6,1M.
        assertEquals(
            "Hasta el 25 de nov no te alcanza: el 5 de nov quedarías en −\$1,9M libres · en las cuentas habrá \$6,1M: " +
                "menos que tu ahorro de \$8M",
            frase,
        )
        assertEquals(
            "Hasta el 25 de nov no te alcanza: el 5 de nov quedarías en −\$2,4M libres · en las cuentas habrá \$6,1M: " +
                "menos que tu ahorro y lo apartado (\$8,5M)",
            fraseDePuente(
                caja.copy(apartado = 500_000L, dias = caja.dias.map { it.copy(libre = it.saldo - 8_500_000L) }),
                LocalDate(2026, 10, 29),
            ),
        )
        // La cifra de las cuentas sale una sola vez en toda la sección: ni la leyenda ni la gráfica la repiten.
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
            "Hasta el 3 de oct, tu peor día es hoy: tienes \$3M libres en tus cuentas · en las cuentas hay \$5M, de eso " +
                "\$1,5M es tu ahorro y \$500.000 está apartado",
            fraseDePuente(caja, hoy),
        )
        assertEquals(TOCA_UN_DIA, leyendaDelCierre(caja))
    }

    // ── Lo libre de hoy contra «Te quedan» (revisión de la 70) ──────────────

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
        assertEquals("hoy tienes \$2,6M libres, \$1,1M más que lo que te queda para gastar: tus compras con tarjeta se pagan después", clausulaDeLaDiferencia(conAhorro, c))
        // El peor día es el último del horizonte: la fecha va una vez.
        assertEquals(
            "Hasta el 5 de nov, tu peor día es ese mismo día: tendrás \$2M libres en tus cuentas · en las cuentas habrá \$10M, " +
                "de eso \$8M es tu ahorro · hoy tienes \$2,6M libres, \$1,1M más que lo que te queda para gastar: tus compras " +
                "con tarjeta se pagan después",
            fraseDePuente(conAhorro, octubre29, c),
        )
    }

    /**
     * Revisión de la 70 · **los contraejemplos**: tener deuda en la tarjeta no prueba la causa. Lo que sobra
     * puede ser tu parte de un gasto que pagó otro (Espacios), una liquidación que te pagaron o un apartado
     * cruzado con un fijo del período siguiente. Solo las compras **medidas** que lo explican la nombran.
     */
    @Test
    fun `sin compras con tarjeta medidas que la expliquen la diferencia se dice sin causa`() {
        val sinMedir = ComparacionConTeQuedan(teQuedan = 1_500_000L, fijosQueFaltan = 0L, porRecibir = 0L, comprasConTarjetaSinPagar = null)
        val sinCausa = "hoy tienes \$2,6M libres, \$1,1M más que lo que te queda para gastar"
        assertEquals(sinCausa, clausulaDeLaDiferencia(conAhorro, sinMedir), "sin medirlas no se nombran")
        // Lo que sobra es tu parte de un gasto de Espacios o una liquidación: las compras medidas son poquitas.
        assertEquals(sinCausa, clausulaDeLaDiferencia(conAhorro, sinMedir.copy(comprasConTarjetaSinPagar = 300_000L)))
        // La Master Black con diferidos a 36 cuotas: muchas compras sin pagar, mucho más que la diferencia.
        assertEquals(sinCausa, clausulaDeLaDiferencia(conAhorro, sinMedir.copy(comprasConTarjetaSinPagar = 5_000_000L)))
        // Sin compras sin pagar (todas pagadas en el período).
        assertEquals(sinCausa, clausulaDeLaDiferencia(conAhorro, sinMedir.copy(comprasConTarjetaSinPagar = 0L)))
    }

    /**
     * Revisión de la 70: con el peor día hoy y un pago que vence hoy, la frase decía dos cifras de «hoy»
     * ($2M en la base y $2,6M en la cláusula). Ahora la cláusula compara lo libre al cerrar hoy, la misma
     * cifra de la base, y no la repite.
     */
    @Test
    fun `con el peor dia hoy y un pago de hoy hay una sola cifra de hoy`() {
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
        val frase = fraseDePuente(conPagoHoy, octubre29, c)
        assertEquals(
            "Hasta el 5 de nov, tu peor día es hoy: tienes \$2M libres en tus cuentas · en las cuentas hay \$10M, de eso \$8M es " +
                "tu ahorro · \$500.000 más que lo que te queda para gastar: tus compras con tarjeta se pagan después",
            frase,
        )
        assertTrue("2,6M" !in frase, "lo libre antes de los pagos de hoy no aparece")
    }

    @Test
    fun `si coinciden con te quedan, hasta mil pesos, no se dice nada`() {
        val c = ComparacionConTeQuedan(teQuedan = 2_599_200L, fijosQueFaltan = 0L, porRecibir = 0L, comprasConTarjetaSinPagar = null)
        assertNull(clausulaDeLaDiferencia(conAhorro, c))
        assertTrue("lo que te queda para gastar" !in fraseDePuente(conAhorro, octubre29, c))
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
        assertTrue("por Gimnasio (\$1.100.000)" in rojo, rojo)
        // El peor día es el último: la fecha se dice una vez.
        assertTrue(fraseDePuente(caja, hoy).startsWith("Hasta el 17 de oct no te alcanza: ese día quedarías en"), fraseDePuente(caja, hoy))
        // Si el primero en rojo es el peor, ya lo dice la frase de puente: no se repite.
        assertNull(fraseDelRojo(caja.copy(dias = caja.dias.take(2)), hoy))
    }

    @Test
    fun `el supuesto dice de que periodos sale, y si no hay, que no hay`() {
        val uno = GastoDelDiaADia(100_000L, listOf(GastoVariableDeUnPeriodo("2026-09", 3_100_000L, 31)))
        assertEquals(
            "Supone que sigues como siempre: gastas en promedio \$100.000 por día (en tu período de septiembre).",
            fraseDelSupuesto(uno),
        )
        val dos = GastoDelDiaADia(
            100_000L,
            listOf(GastoVariableDeUnPeriodo("2026-09", 3_100_000L, 31), GastoVariableDeUnPeriodo("2026-08", 2_900_000L, 29)),
        )
        assertTrue("gastas en promedio \$100.000 por día (en tus últimos 2 períodos)" in fraseDelSupuesto(dos))
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
        // Vuelta 70: sin un ingreso recurrente la caja mira 30 días.
        assertEquals("2026-11-01", caja.alCierre.fecha)
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
