package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Espacios: el saldo y «solo tu parte» (R3)
 *
 * La decisión del dueño (26-oct-2026): un gasto compartido cuenta **solo tu parte** en todo total
 * de gasto; la parte del otro es «te debe»; cuando el otro paga, no es ingreso, solo salda. «Tu
 * plata» y los saldos no cambian: la plata sí salió de tu cuenta.
 */
class EspacioTest {

    private val caro = MiembroDelEspacio("dst_caro", "Caro")
    private val casa = Espacio(id = "esp_casa", nombre = NOMBRE_DEL_PRIMER_ESPACIO, miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), caro))

    private fun deMi(id: String, monto: Long, eventoId: String? = "e_$id") = GastoDelEspacio(
        id = id, espacioId = casa.id, pagadoPor = MIEMBRO_YO, monto = monto, categoria = "Mercado",
        descripcion = "Mercado", fecha = 1L, eventoId = eventoId, partes = partesDelGasto(monto, casa, MIEMBRO_YO),
    )

    private fun deCaro(id: String, monto: Long, fecha: Long = 1L) = GastoDelEspacio(
        id = id, espacioId = casa.id, pagadoPor = caro.id, monto = monto, categoria = "Mercado",
        descripcion = "Mercado del sábado", fecha = fecha, partes = partesDelGasto(monto, casa, caro.id),
    )

    // ── saldoDelEspacio ──────────────────────────────────────────────────────

    @Test
    fun `50 50 - pagaste 2100000 y Caro 1320000, Caro te debe 390000`() {
        val saldo = saldoDelEspacio(casa, listOf(deMi("a", 2_100_000), deCaro("b", 1_320_000)), emptyList())

        assertEquals(3_420_000, saldo.total)
        assertEquals(390_000, saldo.netoDe(MIEMBRO_YO))
        assertEquals(-390_000, saldo.netoDe(caro.id))
        assertEquals(listOf(Reembolso(de = caro.id, a = MIEMBRO_YO, monto = 390_000)), saldo.reembolsos)
        val yo = saldo.miembros.first { it.miembroId == MIEMBRO_YO }
        assertEquals(2_100_000, yo.puso)
        assertEquals(1_710_000, yo.leTocaba)
    }

    @Test
    fun `un gasto que pago Caro - le debes tu mitad`() {
        val saldo = saldoDelEspacio(casa, listOf(deCaro("b", 240_000)), emptyList())

        assertEquals(-120_000, saldo.netoDe(MIEMBRO_YO))
        assertEquals(120_000, saldo.netoDe(caro.id))
        assertEquals(listOf(Reembolso(de = MIEMBRO_YO, a = caro.id, monto = 120_000)), saldo.reembolsos)
    }

    @Test
    fun `liquidacion parcial deja el resto, la total deja a mano`() {
        val gastos = listOf(deMi("a", 2_100_000), deCaro("b", 1_320_000))
        val parcial = saldoDelEspacio(casa, gastos, listOf(LiquidacionDelEspacio("l1", casa.id, caro.id, MIEMBRO_YO, 190_000)))
        assertEquals(200_000, parcial.netoDe(MIEMBRO_YO))
        assertEquals(listOf(Reembolso(caro.id, MIEMBRO_YO, 200_000)), parcial.reembolsos)

        val total = saldoDelEspacio(
            casa, gastos,
            listOf(
                LiquidacionDelEspacio("l1", casa.id, caro.id, MIEMBRO_YO, 190_000),
                LiquidacionDelEspacio("l2", casa.id, caro.id, MIEMBRO_YO, 200_000),
            ),
        )
        assertTrue(total.miembros.all { it.neto == 0L })
        assertTrue(total.reembolsos.isEmpty())
    }

    @Test
    fun `un gasto cuyo movimiento se anulo no cuenta, ni una liquidacion vinculada a uno anulado`() {
        val saldo = saldoDelEspacio(
            casa,
            listOf(deMi("a", 200_000, eventoId = "e_vivo"), deMi("b", 1_000_000, eventoId = "e_anulado")),
            listOf(LiquidacionDelEspacio("l", casa.id, caro.id, MIEMBRO_YO, 50_000, eventoId = "e_anulado_2")),
            anulados = setOf("e_anulado", "e_anulado_2"),
        )
        assertEquals(200_000, saldo.total)
        assertEquals(100_000, saldo.netoDe(MIEMBRO_YO))
    }

    @Test
    fun `los netos suman cero y con tres miembros alcanzan dos pagos`() {
        val papa = MiembroDelEspacio("dst_papa", "Papá")
        val tres = casa.copy(miembros = casa.miembros + papa)
        val gastos = listOf(
            GastoDelEspacio("x", tres.id, MIEMBRO_YO, 300_000, "Mercado", "", 1, "e_x", partesDelGasto(300_000, tres, MIEMBRO_YO)),
            GastoDelEspacio("y", tres.id, caro.id, 90_000, "Mercado", "", 1, null, partesDelGasto(90_000, tres, caro.id)),
        )
        val saldo = saldoDelEspacio(tres, gastos, emptyList())
        assertEquals(0L, saldo.miembros.sumOf { it.neto })
        assertTrue(saldo.reembolsos.size <= 2)
        assertEquals(saldo.netoDe(MIEMBRO_YO), saldo.reembolsos.filter { it.a == MIEMBRO_YO }.sumOf { it.monto })
    }

    @Test
    fun `el peso que sobra lo pone quien pago, y los porcentajes reparten por su parte`() {
        assertEquals(mapOf(MIEMBRO_YO to 50_001L, caro.id to 50_000L), partesDelGasto(100_001, casa, MIEMBRO_YO))
        assertEquals(mapOf(MIEMBRO_YO to 50_000L, caro.id to 50_001L), partesDelGasto(100_001, casa, caro.id))

        val sesenta = casa.copy(
            reparto = RepartoDelEspacio.PORCENTAJES,
            miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, porcentaje = 60), caro.copy(porcentaje = 40)),
        )
        assertTrue(repartoValido(sesenta))
        assertEquals(mapOf(MIEMBRO_YO to 60_000L, caro.id to 40_000L), partesDelGasto(100_000, sesenta, MIEMBRO_YO))
        assertFalse(repartoValido(sesenta.copy(miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, porcentaje = 60), caro.copy(porcentaje = 50)))))
    }

    // ── Solo tu parte en los totales ─────────────────────────────────────────

    private fun ts(dia: String) = LocalDate.parse(dia).atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun mov(id: String, dia: String, monto: Long, tipo: TransactionType = TransactionType.EXPENSE, categoria: String = "Mercado", descripcion: String = "Mercado") =
        FinancialEvent(
            id = id, accountId = "ahorros", type = tipo, amount = monto, category = categoria,
            description = descripcion, timestamp = ts(dia), reconciliationStatus = ReconciliationStatus.RECONCILED,
        )

    private val apertura = mov("ap", "2026-08-01", 5_000_000, TransactionType.INCOME, OPENING_CATEGORY, "Saldo inicial")
        .copy(countsAsCashFlow = false)

    @Test
    fun `un gasto repartido cuenta solo tu parte en el gasto, las categorias y el dia`() {
        val mercado = mov("e_mercado", "2026-10-05", 240_000)
        val almuerzo = mov("e_almuerzo", "2026-10-05", 30_000, categoria = "Comida")
        val indice = loCompartidoDe(listOf(deMi("g", 240_000, eventoId = "e_mercado")), emptyList())
        val eventos = listOf(apertura, mercado, almuerzo).map { it.conLoCompartido(indice) }

        val repartido = eventos.first { it.id == "e_mercado" }
        assertEquals(120_000, repartido.parteAjena)
        assertEquals(casa.id, repartido.espacioId)
        assertEquals(240_000, repartido.amount, "el monto del movimiento no cambia: la plata salió entera")
        assertEquals(120_000, montoContado(repartido))

        val analisis = analisisDelPeriodo(eventos, PeriodoFinanciero(2026, 10), PeriodSettings(), LocalDate(2026, 10, 20))
        assertEquals(150_000, analisis.total)
        val porCategoria = filasDelAnalisis(analisis, VistaDelAnalisis.CATEGORIA).associate { it.clave to it.gasto }
        assertEquals(120_000, porCategoria["Mercado"])
        assertEquals(30_000, porCategoria["Comida"])
        assertEquals(-150_000, eventos.sumOf { aporteAlFlujoDelDia(it) })
        assertEquals(mapOf("2026-10-05" to 150_000L), gastoVariablePorDia(eventos, emptyMap()) { "2026-10-05" })
    }

    @Test
    fun `tu parte de lo que pago Caro cuenta el dia del gasto, como gasto de su categoria`() {
        val deOtros = tuParteComoEventos(listOf(deCaro("g", 240_000, fecha = ts("2026-10-07")), deMi("m", 50_000)))

        assertEquals(1, deOtros.size, "lo tuyo repartido ya está en tus movimientos: no se repite")
        val tuParte = deOtros.single()
        assertTrue(esTuParteDeOtro(tuParte))
        assertEquals(120_000, montoContado(tuParte))
        val analisis = analisisDelPeriodo(listOf(apertura) + deOtros, PeriodoFinanciero(2026, 10), PeriodSettings(), LocalDate(2026, 10, 20))
        assertEquals(120_000, analisis.total)
        assertEquals(listOf(ts("2026-10-07")), analisis.gastos.map { it.timestamp })
    }

    @Test
    fun `un reembolso vinculado no suma en Entro, aunque mueve el saldo de su cuenta`() {
        val deCaroEntra = mov("e_transf", "2026-10-10", 390_000, TransactionType.INCOME, "Ingreso", "Transferencia de Caro")
        val indice = loCompartidoDe(emptyList(), listOf(LiquidacionDelEspacio("l", casa.id, caro.id, MIEMBRO_YO, 390_000, eventoId = "e_transf")))
        val vinculado = deCaroEntra.conLoCompartido(indice)

        assertFalse(vinculado.countsAsCashFlow)
        assertFalse(cuentaEnGastosEIngresos(vinculado))
        assertEquals(casa.id, vinculado.saldaEspacioId)
        assertEquals(0L, aporteAlFlujoDelDia(vinculado))
        val cuentas = mapOf("ahorros" to CuentaDelDisponible(AccountType.SAVINGS, null))
        assertEquals(0L, plataDelPeriodo(0L, listOf(vinculado), cuentas).ingresos)
        // El saldo sí lo cuenta: los saldos no pasan por la regla de flujo.
        assertEquals(390_000, signedDelta(AccountType.SAVINGS, vinculado.type, vinculado.amount))
    }

    @Test
    fun `Tu plata no cambia por repartir un gasto`() {
        val cuenta = Account(id = "ahorros", name = "Nu", type = AccountType.SAVINGS, balance = 1_000_000)
        val antes = patrimonioDe(listOf(cuenta))
        val indice = loCompartidoDe(listOf(deMi("g", 240_000, eventoId = "e_mercado")), emptyList())
        mov("e_mercado", "2026-10-05", 240_000).conLoCompartido(indice)
        // Repartir no toca la cuenta ni su saldo: el patrimonio sale de las mismas cuentas.
        assertEquals(antes, patrimonioDe(listOf(cuenta)))
        assertEquals(1_000_000, antes.tuPlata)
    }

    @Test
    fun `Movi propone la transferencia de Caro que salda, no una que pasa de lo que debe`() {
        val destinoCaro = DestinoConocido(id = caro.id, nombre = "Caro", numero = "")
        val saldo = saldoDelEspacio(casa, listOf(deMi("a", 780_000)), emptyList())
        val ahora = ts("2026-10-20")
        val justa = mov("t1", "2026-10-18", 390_000, TransactionType.INCOME, "Ingreso", "Transferencia de Caro")
        val dePapa = mov("t2", "2026-10-18", 390_000, TransactionType.INCOME, "Ingreso", "Transferencia de Papá")
        val demasiada = mov("t3", "2026-10-18", 900_000, TransactionType.INCOME, "Ingreso", "Transferencia de Caro")

        val propuestas = transferenciasParaSaldar(saldo, listOf(justa, dePapa, demasiada), listOf(destinoCaro), ahora)

        assertEquals(listOf("t1"), propuestas.map { it.eventoId })
        assertEquals(caro.id, propuestas.single().de)
    }

    // ── Vuelta 66 · el saldado que espera su transferencia ──────────────────

    @Test
    fun `saldado antes de que llegue la plata - se propone la primera transferencia de Caro por ese monto, mas o menos 1 por ciento`() {
        val destinoCaro = DestinoConocido(id = caro.id, nombre = "Caro", numero = "")
        val saldado = LiquidacionDelEspacio("l_esp", casa.id, caro.id, MIEMBRO_YO, 168_050, fecha = ts("2026-10-29"))
        // A mano después de saldar: la propuesta de siempre (la de la deuda) ya no sale.
        val saldo = saldoDelEspacio(casa, listOf(deMi("a", 336_100)), listOf(saldado))
        assertTrue(saldo.reembolsos.isEmpty())
        val ahora = ts("2026-10-31")
        val llega = mov("t_llega", "2026-10-30", 168_500, TransactionType.INCOME, "Ingreso", "Transferencia de Caro")
        val otraDespues = mov("t_otra", "2026-10-31", 168_050, TransactionType.INCOME, "Ingreso", "Transferencia de Caro")
        val muyDistinta = mov("t_mas", "2026-10-30", 200_000, TransactionType.INCOME, "Ingreso", "Transferencia de Caro")
        val deOtro = mov("t_papa", "2026-10-30", 168_050, TransactionType.INCOME, "Ingreso", "Transferencia de Papá")

        val propuestas = transferenciasParaSaldar(saldo, listOf(otraDespues, llega, muyDistinta, deOtro), listOf(destinoCaro), ahora, liquidaciones = listOf(saldado))

        assertEquals(listOf("t_llega"), propuestas.map { it.eventoId }, "la primera que llegó, dentro del 1 %")
        assertEquals("l_esp", propuestas.single().liquidacionId)
        assertTrue(montoParecido(168_050 + 1_680, 168_050))
        assertFalse(montoParecido(168_050 + 1_681, 168_050))
    }

    @Test
    fun `un saldado vinculado, uno viejo o uno sin el duenno no esperan nada`() {
        val ahora = ts("2026-10-31")
        assertTrue(esperaLaTransferencia(LiquidacionDelEspacio("l", casa.id, caro.id, MIEMBRO_YO, 100, fecha = ts("2026-10-29")), ahora))
        assertFalse(esperaLaTransferencia(LiquidacionDelEspacio("l", casa.id, caro.id, MIEMBRO_YO, 100, fecha = ts("2026-10-29"), eventoId = "e"), ahora))
        assertFalse(esperaLaTransferencia(LiquidacionDelEspacio("l", casa.id, caro.id, MIEMBRO_YO, 100, fecha = ts("2026-08-01")), ahora))
        assertFalse(esperaLaTransferencia(LiquidacionDelEspacio("l", casa.id, caro.id, "dst_ana", 100, fecha = ts("2026-10-29")), ahora))
    }

    @Test
    fun `si le pagaste tu, se propone tu transferencia a Caro`() {
        val destinoCaro = DestinoConocido(id = caro.id, nombre = "Caro", numero = "")
        val pagaste = LiquidacionDelEspacio("l_yo", casa.id, MIEMBRO_YO, caro.id, 120_000, fecha = ts("2026-10-29"))
        val sale = mov("t_sale", "2026-10-29", 120_000, TransactionType.EXPENSE, "Transferencias", "Transferencia a Caro")
        val propuestas = transferenciasQueEsperan(listOf(pagaste), listOf(sale), listOf(destinoCaro), ts("2026-10-30"))
        assertEquals(listOf("t_sale"), propuestas.map { it.eventoId })
    }
}
