package com.jvillada.movi.ui

import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.filasDelWidgetDeFalta
import com.jvillada.movi.ui.dashboard.proximoPagoDelInicio
import com.jvillada.movi.ui.espacios.DesgloseDelSaldo
import com.jvillada.movi.ui.espacios.desgloseDelSaldo
import com.jvillada.movi.ui.espacios.fraseDelSaldo
import com.jvillada.movi.ui.espacios.lineaDelDesgloseDelSaldo
import com.jvillada.movi.ui.recurrentes.PorQueNoSuma
import com.jvillada.movi.ui.recurrentes.aporteALoQueFalta
import com.jvillada.movi.ui.recurrentes.cuentaDeLaTarjetaDe
import com.jvillada.movi.ui.recurrentes.enOrdenDeFacturas
import com.jvillada.movi.ui.recurrentes.laMasUrgente
import com.jvillada.movi.ui.recurrentes.lineaDeLoQueNoSuma
import com.jvillada.movi.ui.recurrentes.nombreDeLoQueNoSuma
import com.jvillada.movi.ui.recurrentes.pildoraDeLoQueNoSuma
import com.jvillada.movi.ui.recurrentes.porQueNoSuma
import com.jvillada.movi.ui.recurrentes.resumenDeFacturas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **Vuelta 86 · una cifra por pregunta**, sin pantalla: el próximo pago es el que vence primero (el mismo
 * que va primero en Pagos), lo que no suma en «Falta por pagar» se dice y la suma no cambia, y el saldo de
 * un grupo se explica con lo del período y lo que venía de antes.
 */
class Vuelta86Test {

    // ── El período de la revisión 9 (hoy, 20 oct) ──────────────────────────

    private val octubre = PeriodoFinanciero(2026, 10)

    private fun regla(
        id: String,
        nombre: String,
        monto: Long,
        dia: Int,
        saldo: Boolean = false,
        minimo: Long? = null,
        tipo: TransactionType = TransactionType.EXPENSE,
    ) = RecurringRule(
        id = id, name = nombre, category = "Servicios", amount = monto, dayOfMonth = dia, type = tipo,
        montoEsSaldo = saldo, pagoMinimoCop = minimo,
    )

    /** Hoy es el 20: `daysUntil` = día − 20. */
    private fun vence(r: RecurringRule) = UpcomingPayment(
        rule = r,
        dueDate = "2026-10-" + r.dayOfMonth.toString().padStart(2, '0'),
        daysUntil = r.dayOfMonth - 20,
        status = if (r.dayOfMonth < 20) PaymentStatus.OVERDUE else PaymentStatus.UPCOMING,
    )

    private val bodytech = regla("rr_body", "Bodytech", 129_900, 24)
    private val cuota = regla("cr_carro", "Cuota Crédito carro Occidente", 1_090_000, 26)
    private val nu = regla("card_nu", "Pago tarjeta Tarjeta Nu", 540_000, 28, saldo = true)
    private val spotify = regla("rr_spot", "Spotify", 26_900, 29)
    private val salario = regla("rr_sal", "Salario", 6_800_000, 23, tipo = TransactionType.INCOME)

    private fun datos(vararg reglas: RecurringRule) = DashboardData(
        upcoming = reglas.map(::vence),
        ocurrencias = emptyList(),
        periodoActual = octubre,
        ajustesDePeriodo = PeriodSettings(),
    )

    private fun checklist(vararg reglas: RecurringRule): List<PagoDelPeriodo> =
        checklistDelPeriodo(reglas.map(::vence), emptyList(), octubre, PeriodSettings())

    // ── 4 · «Próximo pago» honesto ─────────────────────────────────────────

    @Test
    fun `el proximo pago es el que vence primero, no el que mas pesa`() {
        val d = datos(cuota, bodytech, nu, spotify, salario)
        val proximo = assertNotNull(proximoPagoDelInicio(d))
        // La revisión: la tarjeta decía la cuota (vence el 26) mientras Bodytech vencía el 24.
        assertEquals("Bodytech", proximo.pago.nombre)
        kotlin.test.assertTrue(proximo.titulo.startsWith("Próximo pago"), proximo.titulo)
        // El widget sigue desde el segundo, en el mismo orden.
        assertEquals(listOf("Cuota Crédito carro Occidente", "Pago tarjeta Tarjeta Nu"), filasDelWidgetDeFalta(d, sinElProximoPago = true).filas.map { it.nombre })
        // Inicio y Pagos dicen lo mismo: el primero de la lista de Pagos (el del «Anotar pago» amarillo).
        val facturas = enOrdenDeFacturas(checklist(cuota, bodytech, nu, spotify, salario).filter { !it.esIngreso })
        assertEquals(laMasUrgente(facturas)?.ruleId, proximo.pago.ruleId)
    }

    @Test
    fun `lo vencido va primero, lo que vencio hace mas arriba`() {
        val internet = regla("rr_int", "Internet", 3_000, 19)
        val arriendo = regla("rr_arr", "Arriendo", 1_800_000, 5)
        val d = datos(cuota, bodytech, internet, arriendo)
        assertEquals("Arriendo", proximoPagoDelInicio(d)?.pago?.nombre)
        assertEquals(
            listOf("Internet", "Bodytech"),
            filasDelWidgetDeFalta(d, sinElProximoPago = true).filas.map { it.nombre },
            "después de lo vencido, por fecha",
        )
    }

    // ── 2 · Lo que no suma se dice ahí mismo ───────────────────────────────

    @Test
    fun `la tarjeta sin minimo no suma y se dice, sin cambiar la cifra`() {
        val lista = checklist(cuota, bodytech, nu, spotify, salario)
        // La cifra de siempre: la cuota, Bodytech y Spotify (la tarjeta es un saldo sin mínimo).
        assertEquals(1_090_000L + 129_900L + 26_900L, faltaPorPagar(lista))
        // Fila por fila, la misma suma.
        assertEquals(faltaPorPagar(lista), lista.sumOf(::aporteALoQueFalta))
        val laDeNu = lista.single { it.ruleId == "card_nu" }
        assertEquals(PorQueNoSuma.FALTA_EL_MINIMO, porQueNoSuma(laDeNu))
        assertEquals("No suma: falta el mínimo", pildoraDeLoQueNoSuma(PorQueNoSuma.FALTA_EL_MINIMO))
        assertEquals("Tarjeta Nu", nombreDeLoQueNoSuma(laDeNu))
        assertEquals("nu", cuentaDeLaTarjetaDe(laDeNu))
        assertEquals("+ Tarjeta Nu sin contar", lineaDeLoQueNoSuma(lista))
        // La tarjeta de Pagos lo dice bajo el total; el total y el conteo no cambian.
        val resumen = resumenDeFacturas(lista)
        assertEquals("+ Tarjeta Nu sin contar", resumen.sinContar)
        assertEquals(faltaPorPagar(lista), resumen.falta)
        assertEquals(4, resumen.quedan, "la tarjeta sigue contando como un pago que queda")
        // Las demás filas suman: ninguna lleva la píldora.
        lista.filter { it.ruleId != "card_nu" }.forEach { assertNull(porQueNoSuma(it), it.nombre) }
    }

    @Test
    fun `con el minimo cargado pero sin extracto, tampoco suma, y se dice por que`() {
        val nuConMinimo = nu.copy(pagoMinimoCop = 80_000)
        val lista = checklist(bodytech, nuConMinimo)
        assertEquals(129_900L, faltaPorPagar(lista), "la regla de siempre: sin extracto no suma")
        assertEquals(PorQueNoSuma.FALTA_EL_CORTE, porQueNoSuma(lista.single { it.ruleId == "card_nu" }))
        assertEquals(faltaPorPagar(lista), lista.sumOf(::aporteALoQueFalta))
    }

    @Test
    fun `sin tarjetas que no sumen no hay linea, y con varias se nombran`() {
        assertNull(lineaDeLoQueNoSuma(checklist(cuota, bodytech, salario)))
        assertNull(resumenDeFacturas(checklist(cuota, bodytech)).sinContar)
        val master = regla("card_ms", "Pago tarjeta Master", 900_000, 27, saldo = true)
        assertEquals("+ Master y Tarjeta Nu sin contar", lineaDeLoQueNoSuma(checklist(nu, master)), "en el orden de la lista: por fecha")
        val amex = regla("card_ax", "Pago tarjeta AMEX", 100_000, 25, saldo = true)
        assertEquals("+ 3 tarjetas sin contar", lineaDeLoQueNoSuma(checklist(nu, master, amex)))
    }

    // ── 3 · Grupos cuadra ──────────────────────────────────────────────────

    private val casa = Espacio(
        id = "e1", nombre = "Casa con Andrés",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Laura"), MiembroDelEspacio("t_andres", "Andrés Rojas")),
    )

    private fun gasto(id: String, monto: Long, quien: String = MIEMBRO_YO) =
        GastoDelEspacio(id = id, espacioId = "e1", pagadoPor = quien, monto = monto, partes = partesDelGasto(monto, casa, quien))

    /** Las cifras de la revisión: período $921.300 (Tú $658.900, Andrés $262.400) y lo de antes. */
    private val delPeriodo = listOf(
        gasto("ara", 190_000), gasto("d1a", 338_900), gasto("d1b", 130_000), gasto("carulla", 262_400, "t_andres"),
    )
    private val deAntes = listOf(gasto("arriendo-ago", 682_750))

    private fun detalle(liquidaciones: List<LiquidacionDelEspacio> = emptyList()) = DetalleDelEspacio(
        espacio = casa,
        saldo = saldoDelEspacio(casa, delPeriodo + deAntes, liquidaciones),
        delPeriodo = saldoDelEspacio(casa, delPeriodo, emptyList()),
        gastos = delPeriodo + deAntes,
        liquidaciones = liquidaciones,
    )

    @Test
    fun `quien puso que del periodo mas lo de antes da el saldo`() {
        val d = detalle()
        val frase = fraseDelSaldo(casa, d.saldo)
        assertEquals("Andrés Rojas te debe", frase.titulo)
        assertEquals(539_625L, frase.monto)
        // «Quién puso qué»: Tú $658.900, Andrés $262.400, mitad $460.650 → $198.250 de este período.
        assertEquals(921_300L, d.delPeriodo.total)
        assertEquals(460_650L, d.delPeriodo.miembros.single { it.miembroId == MIEMBRO_YO }.leTocaba)
        val desglose = assertNotNull(desgloseDelSaldo(d))
        assertEquals(DesgloseDelSaldo(delPeriodo = 198_250L, deAntes = 341_375L), desglose)
        assertEquals(frase.monto, desglose.delPeriodo + desglose.deAntes, "las dos partes suman el saldo")
        assertEquals("\$198.250 de este período + \$341.375 que venía de antes", lineaDelDesgloseDelSaldo(desglose))
    }

    @Test
    fun `con lo de antes saldado no hay desglose, y si se salda de mas se dice`() {
        // Andrés pagó lo de antes: el saldo es solo lo del período.
        val saldado = detalle(listOf(LiquidacionDelEspacio(id = "l1", espacioId = "e1", de = "t_andres", a = MIEMBRO_YO, monto = 341_375)))
        assertEquals(198_250L, fraseDelSaldo(casa, saldado.saldo).monto)
        assertNull(desgloseDelSaldo(saldado), "todo es de este período: nada que explicar")
        // Pagó $400.000: lo saldado se come parte de este período.
        val deMas = detalle(listOf(LiquidacionDelEspacio(id = "l1", espacioId = "e1", de = "t_andres", a = MIEMBRO_YO, monto = 400_000)))
        val desglose = assertNotNull(desgloseDelSaldo(deMas))
        assertEquals(fraseDelSaldo(casa, deMas.saldo).monto, desglose.delPeriodo + desglose.deAntes)
        assertEquals("\$198.250 de este período − \$58.625 ya saldado o de antes", lineaDelDesgloseDelSaldo(desglose))
        // A mano: nada.
        val aMano = detalle(listOf(LiquidacionDelEspacio(id = "l1", espacioId = "e1", de = "t_andres", a = MIEMBRO_YO, monto = 539_625)))
        assertNull(desgloseDelSaldo(aMano))
    }
}
