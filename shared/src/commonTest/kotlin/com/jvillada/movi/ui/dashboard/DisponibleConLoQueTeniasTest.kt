package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.CuentaDelDisponible
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SumaDeMovimientos
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.gastoVariablePorDia
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.plataDelPeriodo
import com.jvillada.movi.shared.model.saldoDeTuPlata
import com.jvillada.movi.shared.model.signedDelta
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # El Disponible cuenta lo que tenías y lo que entró
 *
 * El dueño vio «te pasaste por $11M» con plata en Bancolombia Ahorros: la Gardenera se pagó con un
 * préstamo de su papá y el colegio desde Nu, y ninguna de las dos plata contaba — el gasto sí.
 *
 * El fixture son sus datos del período del 25 de agosto al 24 de septiembre de 2026, con hoy el 21:
 * las cuatro cuentas de «Tu plata» con los saldos de hoy, Nu y Skandia condicionadas, el préstamo
 * «Crédito Techo Gardenera», los traspasos con la Fiducuenta, el sueldo, la recarga de Glim y los
 * rendimientos de Nu. Lo que no se sabe (lo que había el 25, los gastos sueltos) está puesto para
 * que los saldos de hoy den los suyos.
 */
class DisponibleConLoQueTeniasTest {

    private val inicio = LocalDate(2026, 8, 25)
    private val finExclusivo = LocalDate(2026, 9, 25)
    private val hoy = LocalDate(2026, 9, 21)

    private val condiciones = mapOf(
        "ahorros" to (AccountType.SAVINGS to null),
        "glim" to (AccountType.SAVINGS to null),
        "fiducuenta" to (AccountType.INVESTMENT to null),
        "ahorros0031" to (AccountType.SAVINGS to null),
        "nu" to (AccountType.SAVINGS to "Educación"),
        "skandia" to (AccountType.INVESTMENT to "Vivienda"),
        "afc" to (AccountType.SAVINGS to "Vivienda"),
        "techo" to (AccountType.LOAN to null),
        "hipotecario" to (AccountType.LOAN to null),
        "master" to (AccountType.CREDIT_CARD to null),
    )
    private val cuentas = condiciones.mapValues { (_, c) -> CuentaDelDisponible(c.first, c.second) }

    // El «timestamp» es la fecha escrita como número (20260921): `diaDe` la devuelve tal cual, así
    // la prueba no depende de ninguna zona horaria.
    private val diaDe: (Long) -> String = { t ->
        val s = t.toString()
        "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
    }

    private fun mov(
        id: String,
        cuenta: String,
        tipo: TransactionType,
        monto: Long,
        dia: String,
        categoria: String = "Varios",
        traspaso: String? = null,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, category = categoria, description = id,
        timestamp = dia.replace("-", "").toLong(), reconciliationStatus = ReconciliationStatus.RECONCILED,
        transferId = traspaso, countsAsCashFlow = isCashFlow(cuentas.getValue(cuenta).tipo, tipo, categoria),
    )

    private fun gasto(id: String, cuenta: String, monto: Long, dia: String, categoria: String = "Varios") =
        mov(id, cuenta, TransactionType.EXPENSE, monto, dia, categoria)

    private fun patas(id: String, desde: String, hacia: String, monto: Long, dia: String, categoria: String = TRANSFER_CATEGORY) =
        listOf(
            mov("$id-sale", desde, TransactionType.EXPENSE, monto, dia, categoria, traspaso = id),
            mov("$id-entra", hacia, TransactionType.INCOME, monto, dia, categoria, traspaso = id),
        )

    /** Lo que había antes del 25: la apertura de cada cuenta de Tu plata, y los ahorros de afuera. */
    private val antes = listOf(
        mov("apertura-ahorros", "ahorros", TransactionType.INCOME, 1_200_000, "2026-08-01", OPENING_CATEGORY),
        mov("apertura-glim", "glim", TransactionType.INCOME, 7_420, "2026-08-01", OPENING_CATEGORY),
        mov("apertura-fiducuenta", "fiducuenta", TransactionType.INCOME, 179_063, "2026-08-01", OPENING_CATEGORY),
        mov("apertura-0031", "ahorros0031", TransactionType.INCOME, 211, "2026-08-01", OPENING_CATEGORY),
        mov("apertura-nu", "nu", TransactionType.INCOME, 14_778_000, "2026-08-01", OPENING_CATEGORY),
        mov("apertura-skandia", "skandia", TransactionType.INCOME, 106_000_000, "2026-08-01", OPENING_CATEGORY),
        // Una compra del período pasado, que la tarjeta cobra en este.
        gasto("compra-agosto", "master", 2_000_000, "2026-08-10", "Ropa"),
    )

    private val delPeriodo = listOf(
        mov("sueldo", "ahorros", TransactionType.INCOME, 20_308_659, "2026-08-25", "Salario"),
        mov("recarga-glim", "glim", TransactionType.INCOME, 621_788, "2026-08-26", "Alimentación"),
        // Nu es condicionada: sus rendimientos no son plata que se pueda gastar.
        mov("rendimientos-nu", "nu", TransactionType.INCOME, 1_222_041, "2026-09-15", "Rendimientos"),
    ) +
        patas("t-fidu-1", "ahorros", "fiducuenta", 8_000_000, "2026-08-26") +
        patas("t-techo", "techo", "ahorros", 10_000_000, "2026-09-01") +
        patas("t-fidu-2", "ahorros", "fiducuenta", 1_932_000, "2026-09-02") +
        patas("t-fidu-3", "fiducuenta", "ahorros", 4_200_000, "2026-09-10") +
        // Apartar plata en Nu: se guarda, ya no se puede gastar.
        patas("t-a-nu", "ahorros", "nu", 500_000, "2026-09-15") +
        // La cuota del hipotecario y el pago de la tarjeta, cada uno con su categoría.
        patas("p-hipotecario", "ahorros", "hipotecario", 2_500_000, "2026-08-30", CUOTA_CATEGORY) +
        patas("p-master", "ahorros", "master", 2_000_000, "2026-09-05", CARD_PAYMENT_CATEGORY) +
        listOf(
            gasto("gardenera", "ahorros", 9_960_000, "2026-09-01", "Hogar"),
            gasto("colegio", "nu", 3_000_000, "2026-09-03", "Educación"),
            gasto("gardenera-saldo", "fiducuenta", 5_500_000, "2026-09-03", "Hogar"),
            gasto("internet", "ahorros", 120_000, "2026-09-05", "Servicios"),
            gasto("mercado", "ahorros", 1_200_000, "2026-08-28", "Comida"),
            gasto("mercado-glim", "glim", 600_000, "2026-09-07", "Comida"),
            gasto("hogar", "ahorros", 3_603_421, "2026-09-08", "Hogar"),
            gasto("viaje", "ahorros", 5_500_000, "2026-09-12", "Viajes"),
            gasto("compra-tarjeta", "master", 2_400_000, "2026-09-14", "Ropa"),
            gasto("futbol", "ahorros", 150_000, "2026-09-21", "Fútbol"),
        )

    /** El checklist: el internet y la cuota ya pagados; el celular y la cuota del Techo, pendientes. */
    private val checklist = listOf(
        PagoDelPeriodo(ruleId = "rr_internet", nombre = "Internet", monto = 120_000, pagado = true, diasParaVencer = -16, montoPagado = 120_000),
        PagoDelPeriodo(ruleId = "rr_hipotecario", nombre = "Hipotecario", monto = 2_500_000, pagado = true, diasParaVencer = -22, montoPagado = 2_500_000),
        PagoDelPeriodo(ruleId = "rr_celular", nombre = "Celular", monto = 53_000, pagado = false, diasParaVencer = 1),
        PagoDelPeriodo(ruleId = "rr_techo", nombre = "Cuota Techo", monto = 1_000_000, pagado = false, diasParaVencer = 2),
        PagoDelPeriodo(ruleId = "rr_sueldo", nombre = "Sueldo", monto = 20_308_659, pagado = true, diasParaVencer = -27, esIngreso = true),
    )
    private val pendientes = 53_000L + 1_000_000L

    private fun sumas(eventos: List<FinancialEvent>) =
        eventos.groupBy { it.accountId to it.type }
            .map { (llave, evs) -> SumaDeMovimientos(llave.first, llave.second, evs.sumOf { it.amount }) }

    private fun disponible(): DisponibleDelPeriodo {
        val plata = plataDelPeriodo(
            saldoAlInicio = saldoDeTuPlata(sumas(antes), cuentas),
            eventos = delPeriodo,
            cuentas = cuentas,
        )
        val gastoPorDia = gastoVariablePorDia(delPeriodo, parteFija = mapOf("internet" to 120_000L), diaDe = diaDe)
        return assertNotNull(
            disponibleDelPeriodo(
                ingresosRecibidos = 22_152_488, // la cifra «Ingresos» de siempre: incluye lo de Nu
                checklist = checklist,
                gastoVariablePorDia = gastoPorDia,
                inicio = inicio,
                finExclusivo = finExclusivo,
                hoy = hoy,
                plata = PlataDelDisponible(plata.saldoAlInicio, plata.entradas, plata.guardado),
            ),
        )
    }

    /** «Tu plata» hoy, con la MISMA función del hero del Inicio, sobre los saldos de cada cuenta. */
    private fun tuPlataHoy(): Long {
        val todos = antes + delPeriodo
        val accounts = condiciones.map { (id, c) ->
            Account(
                id = id, name = id, type = c.first, condicionadaA = c.second,
                balance = todos.filter { it.accountId == id }.sumOf { signedDelta(c.first, it.type, it.amount) },
            )
        }
        return heroBalance(accounts).tuPlata
    }

    @Test
    fun `los saldos de hoy son los del duenio`() {
        // Ahorros $243.238 + Glim $29.208 + Fiducuenta $411.063 + Ahorros 0031 $211.
        assertEquals(683_720, tuPlataHoy())
        // Y el hero lo cuenta con la misma regla que el server usa para el saldo al inicio.
        assertEquals(tuPlataHoy(), saldoDeTuPlata(sumas(antes + delPeriodo), cuentas))
    }

    /**
     * **La identidad que hace creíble el número**: lo que queda del Disponible es lo que hay hoy en
     * Tu plata menos los fijos que faltan por pagar y menos las compras con tarjeta que todavía no
     * salieron de Tu plata (las del período, menos lo que se le pagó a la tarjeta en el período).
     * «Me quedan $X» cuadra con el banco menos lo que falta.
     */
    @Test
    fun `lo que queda es Tu plata de hoy menos los fijos pendientes y la tarjeta sin pagar`() {
        val d = disponible()
        val teQueda = d.disponible - d.periodo.gastado
        val tarjetaSinPagar = 2_400_000L - 2_000_000L

        assertEquals(tuPlataHoy() - pendientes - tarjetaSinPagar, teQueda)
        assertEquals(-769_280, teQueda)
    }

    @Test
    fun `el desglose del duenio - lo que tenia, lo que entro, lo que guardo y los fijos`() {
        val d = disponible()
        val plata = assertNotNull(d.plata)
        assertEquals(1_386_694, plata.saldoAlInicio)
        // Sueldo + Glim (los rendimientos de Nu no) + el préstamo del Techo + el colegio desde Nu.
        assertEquals(20_930_447 + 10_000_000 + 3_000_000, plata.entradas)
        assertEquals(500_000, plata.guardado)
        assertEquals(3_673_000, d.fijos)
        assertEquals(1_386_694 + 33_930_447 - 500_000 - 3_673_000, d.disponible)
        assertEquals(
            listOf("Tenías \$1,4M el 25 · entraron \$33,9M", "Guardaste \$500.000 · fijos \$3,7M"),
            desgloseDelDisponible(d),
        )
    }

    @Test
    fun `con la formula de antes se pasaba por 13 millones`() {
        val d = disponible().copy(plata = null)
        // $22,2M de ingresos menos $3,7M de fijos, contra $31,9M de gasto variable.
        assertEquals(-13_433_933, d.disponible - d.periodo.gastado)
    }

    @Test
    fun `sin los campos nuevos vuelve a ingresos menos fijos`() {
        assertNull(plataDelDisponibleDe(DashboardSummary()))
        assertNull(plataDelDisponibleDe(DashboardSummary(saldoTuPlataAlInicio = 1_000)))
        assertEquals(
            PlataDelDisponible(saldoAlInicio = 1_000, entradas = 2_000, guardado = 0),
            plataDelDisponibleDe(DashboardSummary(saldoTuPlataAlInicio = 1_000, entradasDelPeriodo = 2_000)),
        )

        val viejo = assertNotNull(
            disponibleDelPeriodo(10_000_000, checklist.take(1), emptyMap(), inicio, finExclusivo, hoy, plata = null),
        )
        assertEquals(9_880_000, viejo.disponible)
        assertEquals(listOf("Ingresos \$10M menos fijos \$120.000"), desgloseDelDisponible(viejo))
    }

    @Test
    fun `el Inicio usa lo que manda el server cuando llega`() {
        val datos = DashboardData(
            summary = com.jvillada.movi.shared.model.FinanceSummary(
                scope = com.jvillada.movi.shared.model.Scope.SELF, balance = 0, ingresos = 0, egresos = 0,
            ),
            upcoming = emptyList(),
            ocurrencias = emptyList(),
            gastoVariablePorDia = emptyMap(),
            ajustesDePeriodo = com.jvillada.movi.shared.model.PeriodSettings(cutoffDay = 25),
            periodoActual = com.jvillada.movi.shared.model.PeriodoFinanciero(2026, 9),
        )
        // Sin ingresos y sin lo del server: no hay nada que afirmar.
        assertNull(disponibleDelInicio(datos, hoy))
        // Con lo que tenías al empezar, aunque no haya entrado nada, la tarjeta se afirma.
        val d = assertNotNull(disponibleDelInicio(datos.copy(plataDelDisponible = PlataDelDisponible(2_000_000, 0, 0)), hoy))
        assertEquals(2_000_000, d.disponible)
        assertEquals(listOf("Tenías \$2M el 25 · entraron \$0", "Fijos \$0"), desgloseDelDisponible(d))
    }

    @Test
    fun `sin margen lo dice con lo que tenias y lo que entro`() {
        val d = assertNotNull(
            disponibleDelPeriodo(
                0, checklist.take(2), emptyMap(), inicio, finExclusivo, hoy,
                plata = PlataDelDisponible(saldoAlInicio = 500_000, entradas = 1_000_000, guardado = 0),
            ),
        )
        assertEquals(
            "Los fijos del período superan lo que tenías y lo que entró por \$1,1M",
            sinMargen(d),
        )
        assertEquals(listOf("Tenías \$500.000 el 25 · entraron \$1M", "Fijos \$2,6M"), desgloseDelDisponible(d))
    }
}
