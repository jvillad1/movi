package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 63 · el abono mensual a una deuda se tilda con su traspaso, y no es un fijo
 */
class AbonoADeudaTest {

    private val regla = reglaDelAbono(
        id = "rr_abono", deudaId = "v4083", nombreDeLaDeuda = "Vehículo 4083",
        monto = 500_000L, dia = 15, desdeCuentaId = "a1",
    )

    private fun pata(id: String, cuenta: String, tipo: TransactionType, monto: Long, categoria: String = TRANSFER_CATEGORY, traspaso: String? = "t1") =
        FinancialEvent(
            id = id, accountId = cuenta, type = tipo, amount = monto, category = categoria,
            description = "", timestamp = 0L, transferId = traspaso,
        )

    private fun traspaso(monto: Long, hacia: String = "v4083", categoria: String = TRANSFER_CATEGORY, t: String = "t1") = listOf(
        pata("sale-$t", "a1", TransactionType.EXPENSE, monto, categoria, t),
        pata("entra-$t", hacia, TransactionType.INCOME, monto, categoria, t),
    )

    @Test
    fun `la regla del abono va a la deuda, es un gasto en Traspaso y se llama por la deuda`() {
        assertTrue(regla.esAbonoADeuda)
        assertEquals("Abono extra a Vehículo 4083", regla.name)
        assertEquals(TRANSFER_CATEGORY, regla.category)
        assertEquals(TransactionType.EXPENSE, regla.type)
        assertFalse(regla.copy(haciaDeuda = null).esAbonoADeuda)
        assertFalse(regla.copy(haciaDeuda = "").esAbonoADeuda)
    }

    @Test
    fun `el traspaso hacia la deuda por el monto tilda el abono`() {
        val eventos = traspaso(500_000L)
        assertTrue(esElAbonoALaDeuda(regla, eventos[0], eventos))
        assertEquals("entra-t1", pataQueEntraALaDeuda("v4083", eventos[0], eventos)?.id)
        // La pata que entra a la deuda no es «la que sale»: el sello es con la plata que salió.
        assertFalse(esElAbonoALaDeuda(regla, eventos[1], eventos))
    }

    @Test
    fun `el monto pega a mas o menos 5 por ciento`() {
        assertTrue(montoDelAbonoPega(500_000L, 475_000L))
        assertTrue(montoDelAbonoPega(500_000L, 525_000L))
        assertFalse(montoDelAbonoPega(500_000L, 474_999L))
        assertFalse(montoDelAbonoPega(500_000L, 525_001L))
        assertFalse(montoDelAbonoPega(0L, 0L))

        val corto = traspaso(300_000L)
        assertFalse(esElAbonoALaDeuda(regla, corto[0], corto), "a otro monto no se tilda solo")
        assertTrue(esElAbonoALaDeuda(regla, corto[0], corto, conElMonto = false), "pero se puede confirmar a mano")
    }

    @Test
    fun `un traspaso a otra deuda o a una cuenta propia no es este abono`() {
        val aOtra = traspaso(500_000L, hacia = "libre9695")
        assertFalse(esElAbonoALaDeuda(regla, aOtra[0], aOtra))
        val sinPareja = listOf(pata("suelta", "a1", TransactionType.EXPENSE, 500_000L, traspaso = "t9"))
        assertFalse(esElAbonoALaDeuda(regla, sinPareja[0], sinPareja, conElMonto = false))
    }

    @Test
    fun `la pata de una cuota no es el abono`() {
        val cuota = traspaso(500_000L, categoria = CUOTA_CATEGORY)
        assertNull(pataQueEntraALaDeuda("v4083", cuota[0], cuota))
        assertFalse(esElAbonoALaDeuda(regla, cuota[0], cuota, conElMonto = false))
    }

    @Test
    fun `a una tarjeta el abono es su pago`() {
        val aNu = regla.copy(haciaDeuda = "nu")
        val pago = traspaso(500_000L, hacia = "nu", categoria = CARD_PAYMENT_CATEGORY)
        assertTrue(esElAbonoALaDeuda(aNu, pago[0], pago))
    }

    @Test
    fun `pendiente no suma en los fijos, en falta por pagar ni en la caja, y se dice aparte`() {
        val arriendo = PagoDelPeriodo(ruleId = "rr_arr", nombre = "Arriendo", monto = 1_800_000L, pagado = false, diasParaVencer = 3, vence = "2026-11-05")
        val abono = PagoDelPeriodo(
            ruleId = regla.id, nombre = regla.name, monto = 500_000L, pagado = false, diasParaVencer = 5,
            vence = "2026-11-15", haciaDeuda = "v4083",
        )
        val checklist = listOf(arriendo, abono)
        assertTrue(abono.esAbono)
        assertEquals(1_800_000L, fijosDelPeriodo(checklist))
        assertEquals(1_800_000L, faltaPorPagar(checklist))
        assertEquals(listOf(arriendo), pagosPendientes(checklist))
        assertEquals(500_000L, abonosPlaneados(checklist))
        assertNull(urgenciaDelPago(abono.copy(diasParaVencer = -3)), "no vence ni apura")
        assertFalse(abono.copy(diasParaVencer = -3).vencido)

        val caja = cajaProyectada(
            tuPlataHoy = 5_000_000L, pagos = checklist, gastoDiario = null,
            hoy = "2026-11-01", ultimoDia = "2026-11-24",
        )!!
        assertEquals(5_000_000L - 1_800_000L, caja.alCierre.saldo, "la caja solo resta el arriendo")
        assertTrue(caja.sinContar.isEmpty())

        // Hecho, tampoco es un fijo: su traspaso ya resta como otro pago de deuda.
        val hecho = abono.copy(pagado = true, montoPagado = 500_000L, eventId = "sale-t1")
        assertEquals(1_800_000L, fijosDelPeriodo(listOf(arriendo, hecho)))
        assertEquals(0L, abonosPlaneados(listOf(arriendo, hecho)))
    }
}
