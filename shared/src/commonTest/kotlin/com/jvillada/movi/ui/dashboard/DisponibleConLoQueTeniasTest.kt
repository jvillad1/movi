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
import com.jvillada.movi.shared.model.pagosDeDeudaFueraDelChecklist
import com.jvillada.movi.shared.model.comprasConTarjetaSinPagar
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
        "credito-papa" to (AccountType.LOAN to null),
        "credito-mama" to (AccountType.LOAN to null),
        "amex" to (AccountType.CREDIT_CARD to null),
        "nu-tarjeta" to (AccountType.CREDIT_CARD to null),
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
        estado: ReconciliationStatus = ReconciliationStatus.RECONCILED,
    ) = FinancialEvent(
        id = id, accountId = cuenta, type = tipo, amount = monto, category = categoria, description = id,
        timestamp = dia.replace("-", "").toLong(), reconciliationStatus = estado,
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

    /**
     * La tarjeta armada como la arman el server y el cliente juntos: [plataDelPeriodo] y
     * [pagosDeDeudaFueraDelChecklist] con los movimientos del período, [gastoVariablePorDia] con la
     * parte que paga cada fijo, y [disponibleDelPeriodo] con el checklist.
     *
     * @param parteFija lo que el server reclama con `parteFijaDelChecklist` (recurrentes reales).
     * @param cuotasDelChecklist lo que reclama `cuotasDelChecklistPagadas` (la cuota de un crédito).
     */
    private fun armar(
        antes: List<FinancialEvent>,
        delPeriodo: List<FinancialEvent>,
        checklist: List<PagoDelPeriodo>,
        parteFija: Map<String, Long>,
        cuotasDelChecklist: Map<String, Long>,
        ingresosRecibidos: Long,
    ): DisponibleDelPeriodo {
        val plata = plataDelPeriodo(
            saldoAlInicio = saldoDeTuPlata(sumas(antes), cuentas),
            eventos = delPeriodo,
            cuentas = cuentas,
        )
        val otros = pagosDeDeudaFueraDelChecklist(delPeriodo, cuentas, enLosFijos = parteFija + cuotasDelChecklist)
        val gastoPorDia = gastoVariablePorDia(delPeriodo, parteFija = parteFija, diaDe = diaDe)
        return assertNotNull(
            disponibleDelPeriodo(
                ingresosRecibidos = ingresosRecibidos,
                checklist = checklist,
                gastoVariablePorDia = gastoPorDia,
                inicio = inicio,
                finExclusivo = finExclusivo,
                hoy = hoy,
                plata = PlataDelDisponible(plata.saldoAlInicio, plata.entradas, plata.guardado, otros),
            ),
        )
    }

    private fun disponible(): DisponibleDelPeriodo = armar(
        antes = antes,
        delPeriodo = delPeriodo,
        checklist = checklist,
        parteFija = mapOf("internet" to 120_000L),
        // La cuota del hipotecario es la del checklist (ya está en los fijos).
        cuotasDelChecklist = mapOf("p-hipotecario-sale" to 2_500_000L),
        ingresosRecibidos = 22_152_488, // la cifra «Ingresos» de siempre: incluye lo de Nu
    )

    /** «Tu plata» hoy, con la MISMA función del hero del Inicio, sobre los saldos de cada cuenta. */
    private fun tuPlataHoy(todos: List<FinancialEvent> = antes + delPeriodo): Long {
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
        assertEquals(tarjetaSinPagar, comprasConTarjetaSinPagar(delPeriodo, cuentas))
        // Acá no hay pagos de deuda por fuera del checklist: la cuota es la del hipotecario y el
        // pago de la Master no pasa de lo comprado con ella en el período.
        assertEquals(0, assertNotNull(d.plata).otrosPagosDeDeuda)

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

    // ── Los pagos de deuda que ni los fijos ni el gasto variable contaban ────────────

    /**
     * El período del dueño con lo que el teléfono mostraba: «Tenías $14.912 el 25 · entraron $34M
     * · Fijos $13,8M», Tu plata hoy $683.720 y un solo fijo pendiente (el celular, $53.077).
     *
     * Los hechos: el «Crédito Papá» ($4.280.000) y el «Crédito Mamá» ($1.300.000) como gastos con
     * la categoría de cuota el 27 de agosto, que ningún ítem del checklist reclama; el pago mínimo
     * de AMEX ($1.008.902, un traspaso) el 30 y el de Nu Tarjeta ($115.113) el 5 de septiembre, los
     * dos por compras de antes del período; las compras de Nu Tarjeta del período, por confirmar;
     * y ~$1,07M de compras con la Master sin pagar.
     *
     * Además, lo que tiene que NO restarse dos veces: una compra con la Master del período pagada
     * en el mismo período ($300.000) y la cuota del hipotecario pagada con un traspaso que el
     * checklist reclama ($2.500.000). Lo que no se sabe (el arriendo, los gastos sueltos, un
     * reintegro) está puesto para que den las cifras del teléfono y los saldos de hoy.
     */
    private val antesDelDuenio = listOf(
        mov("d-apertura-ahorros", "ahorros", TransactionType.INCOME, 5_000, "2026-08-01", OPENING_CATEGORY),
        mov("d-apertura-glim", "glim", TransactionType.INCOME, 7_420, "2026-08-01", OPENING_CATEGORY),
        mov("d-apertura-fiducuenta", "fiducuenta", TransactionType.INCOME, 2_281, "2026-08-01", OPENING_CATEGORY),
        mov("d-apertura-0031", "ahorros0031", TransactionType.INCOME, 211, "2026-08-01", OPENING_CATEGORY),
        mov("d-apertura-nu", "nu", TransactionType.INCOME, 14_778_000, "2026-08-01", OPENING_CATEGORY),
        // Las compras de antes del período que las tarjetas cobran en este.
        gasto("d-amex-julio", "amex", 1_008_902, "2026-08-10", "Viajes"),
        gasto("d-nu-agosto", "nu-tarjeta", 115_113, "2026-08-12", "Comida"),
    )

    private val delPeriodoDelDuenio = listOf(
        mov("d-sueldo", "ahorros", TransactionType.INCOME, 20_308_659, "2026-08-25", "Salario"),
        mov("d-recarga-glim", "glim", TransactionType.INCOME, 621_788, "2026-08-26", "Alimentación"),
        mov("d-reintegro", "ahorros", TransactionType.INCOME, 109_553, "2026-09-10", "Reintegro"),
        // Los fijos pagados: el arriendo y el internet desde Ahorros, el colegio desde Nu.
        gasto("d-arriendo", "ahorros", 8_086_923, "2026-08-26", "Vivienda"),
        gasto("d-internet", "ahorros", 120_000, "2026-09-05", "Servicios"),
        gasto("d-colegio", "nu", 3_000_000, "2026-09-03", "Educación"),
        // Los dos créditos de la familia: cuotas que ningún ítem del checklist reclama.
        gasto("d-credito-papa", "ahorros", 4_280_000, "2026-08-27", CUOTA_CATEGORY),
        gasto("d-credito-mama", "ahorros", 1_300_000, "2026-08-27", CUOTA_CATEGORY),
        // Nu Tarjeta: compras del período por confirmar.
        mov("d-nu-compra-1", "nu-tarjeta", TransactionType.EXPENSE, 130_200, "2026-09-10", "Comida", estado = ReconciliationStatus.UNCONFIRMED),
        mov("d-nu-compra-2", "nu-tarjeta", TransactionType.EXPENSE, 39_920, "2026-09-18", "Comida", estado = ReconciliationStatus.UNCONFIRMED),
        // La Master: una compra pagada dentro del período y las del resto del período sin pagar.
        gasto("d-master-pagada", "master", 300_000, "2026-08-28", "Ropa"),
        gasto("d-master-sin-pagar", "master", 1_070_000, "2026-09-14", "Hogar"),
        // El gasto variable desde Tu plata.
        gasto("d-gardenera", "ahorros", 9_960_000, "2026-09-01", "Hogar"),
        gasto("d-mercado", "ahorros", 1_200_000, "2026-08-28", "Comida"),
        gasto("d-mercado-glim", "glim", 600_000, "2026-09-07", "Comida"),
        gasto("d-viaje", "ahorros", 750_254, "2026-09-12", "Viajes"),
        gasto("d-futbol", "ahorros", 150_000, "2026-09-21", "Fútbol"),
    ) +
        patas("d-t-techo", "techo", "ahorros", 10_000_000, "2026-09-01") +
        // La cuota del hipotecario: la reclama el checklist (la cuota del crédito, derivada).
        patas("d-p-hipotecario", "ahorros", "hipotecario", 2_500_000, "2026-08-30", CUOTA_CATEGORY) +
        // El pago mínimo de AMEX, anotado como traspaso.
        patas("d-p-amex", "ahorros", "amex", 1_008_902, "2026-08-30") +
        patas("d-p-nu", "ahorros", "nu-tarjeta", 115_113, "2026-09-05", CARD_PAYMENT_CATEGORY) +
        patas("d-p-master", "ahorros", "master", 300_000, "2026-09-10", CARD_PAYMENT_CATEGORY)

    private val checklistDelDuenio = listOf(
        PagoDelPeriodo(ruleId = "rr_arriendo", nombre = "Arriendo", monto = 8_086_923, pagado = true, diasParaVencer = -26, montoPagado = 8_086_923),
        PagoDelPeriodo(ruleId = "rr_internet", nombre = "Internet", monto = 120_000, pagado = true, diasParaVencer = -16, montoPagado = 120_000),
        PagoDelPeriodo(ruleId = "rr_colegio", nombre = "Colegio", monto = 3_000_000, pagado = true, diasParaVencer = -18),
        PagoDelPeriodo(ruleId = "credit_hipotecario", nombre = "Hipotecario", monto = 2_500_000, pagado = true, diasParaVencer = -22, derivado = true, montoPagado = 2_500_000),
        PagoDelPeriodo(ruleId = "rr_celular", nombre = "Celular", monto = 53_077, pagado = false, diasParaVencer = 1),
        PagoDelPeriodo(ruleId = "rr_sueldo", nombre = "Sueldo", monto = 20_308_659, pagado = true, diasParaVencer = -27, esIngreso = true),
    )

    private fun disponibleDelDuenio(): DisponibleDelPeriodo = armar(
        antes = antesDelDuenio,
        delPeriodo = delPeriodoDelDuenio,
        checklist = checklistDelDuenio,
        parteFija = mapOf("d-arriendo" to 8_086_923L, "d-internet" to 120_000L, "d-colegio" to 3_000_000L),
        cuotasDelChecklist = mapOf("d-p-hipotecario-sale" to 2_500_000L),
        ingresosRecibidos = 21_040_000,
    )

    @Test
    fun `los saldos de hoy del duenio - Tu plata 683720`() {
        assertEquals(683_720, tuPlataHoy(antesDelDuenio + delPeriodoDelDuenio))
    }

    /**
     * **La identidad, con los pagos de deuda del dueño.** Lo que queda es Tu plata de hoy más lo
     * que falta por recibir, menos el fijo pendiente y menos las compras con tarjeta sin pagar —
     * exacto, al peso.
     */
    @Test
    fun `con los pagos de deuda del duenio lo que queda cuadra con Tu plata de hoy`() {
        val d = disponibleDelDuenio()
        val teQueda = d.disponible - d.periodo.gastado
        val fijosPendientes = 53_077L
        // La Master: $1.370.000 comprados en el período, $300.000 pagados. Nu Tarjeta: sus compras
        // del período están por confirmar, así que no cuentan como gasto ni como deuda del período.
        val tarjetaSinPagar = 1_070_000L
        assertEquals(tarjetaSinPagar, comprasConTarjetaSinPagar(delPeriodoDelDuenio, cuentas))
        assertEquals(0, d.ingresosPorRecibir)

        assertEquals(
            tuPlataHoy(antesDelDuenio + delPeriodoDelDuenio) + d.ingresosPorRecibir - fijosPendientes - tarjetaSinPagar,
            teQueda,
        )
        assertEquals(-439_357, teQueda)
    }

    @Test
    fun `los otros pagos de deuda del duenio - los dos creditos y la deuda de antes de las tarjetas`() {
        val d = disponibleDelDuenio()
        val plata = assertNotNull(d.plata)
        // Crédito Papá + Crédito Mamá + AMEX + Nu Tarjeta. Ni la cuota del hipotecario (ya está en
        // los fijos) ni el pago de la Master (no pasa de lo comprado con ella en el período).
        assertEquals(4_280_000L + 1_300_000L + 1_008_902L + 115_113L, plata.otrosPagosDeDeuda)
        assertEquals(6_704_015, plata.otrosPagosDeDeuda)

        assertEquals(14_912, plata.saldoAlInicio)
        assertEquals(34_040_000, plata.entradas)
        assertEquals(0, plata.guardado)
        assertEquals(13_760_000, d.fijos)
        assertEquals(14_030_254, d.periodo.gastado)
        assertEquals(14_912L + 34_040_000L - 13_760_000L - 6_704_015L, d.disponible)
        assertEquals(13_590_897, d.disponible)
        assertEquals(
            listOf("Tenías \$14.912 el 25 · entraron \$34M", "Fijos \$13,8M · otros pagos de deuda \$6,7M"),
            desgloseDelDisponible(d),
        )
    }

    @Test
    fun `sin los otros pagos de deuda el Disponible decia que quedaban 6 millones`() {
        val d = disponibleDelDuenio()
        val sinEllos = d.copy(plata = assertNotNull(d.plata).copy(otrosPagosDeDeuda = 0L))
        assertEquals(20_294_912, sinEllos.disponible)
        assertEquals(6_264_658, sinEllos.disponible - sinEllos.periodo.gastado)
    }

    @Test
    fun `el campo nuevo llega del server, y sin el vale cero`() {
        assertEquals(
            PlataDelDisponible(saldoAlInicio = 1_000, entradas = 2_000, guardado = 0, otrosPagosDeDeuda = 500),
            plataDelDisponibleDe(
                DashboardSummary(saldoTuPlataAlInicio = 1_000, entradasDelPeriodo = 2_000, pagosDeDeudaFueraDelChecklist = 500),
            ),
        )
        assertEquals(
            0,
            assertNotNull(plataDelDisponibleDe(DashboardSummary(saldoTuPlataAlInicio = 1_000, entradasDelPeriodo = 2_000))).otrosPagosDeDeuda,
        )
    }

    @Test
    fun `sin margen nombra tambien los otros pagos de deuda`() {
        val d = assertNotNull(
            disponibleDelPeriodo(
                0, checklist.take(2), emptyMap(), inicio, finExclusivo, hoy,
                plata = PlataDelDisponible(saldoAlInicio = 500_000, entradas = 3_000_000, guardado = 0, otrosPagosDeDeuda = 1_500_000),
            ),
        )
        assertEquals(
            "Los fijos y los otros pagos de deuda superan lo que tenías y lo que entró por \$620.000",
            sinMargen(d),
        )
    }
}
