package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.cargoDelBanco
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * La lista de «Movimientos: Cuentas» de la Sucursal Virtual de Bancolombia, leída sin IA. El texto
 * tiene la forma exacta que saca PDFBox del PDF real (renglones partidos, el pie de página en el
 * medio, la referencia pegada a un nombre); las cifras, los números y los comercios son inventados.
 */
class MovimientosDeBancolombiaTest {

    private val lista = """
        Sucursal Virtual Personas
        Movimientos: Cuentas
        Lunes, 7 de septiembre de 2026, 10:40 p. m.
        JUAN EJEMPLO
        Cuenta de Ahorros
        Ahorros 012 - 345678 - 90
        Fecha Descripción Referencia Valor
        07 sept 2026 TRASLADO A FONDO DE
        INVERSION
        1234567890Fiducuenta -$ 1.000.000,00
        06 sept 2026 PAGO QR CAFETERIA 0091234567 -$ 12.500,00
        06 sept 2026 ABONO INTERESES AHORROS $ 15,58
        05 sept 2026 COMPRA EN  DROGUERIA -$ 31.895,00
        05 sept 2026 PAGO PSE Medicina Prepagada
        Pr
        629556373 -$ 138.600,00
        Dirección IP 1.2.3.4
        Fecha Descripción Referencia Valor
        05 sept 2026 PAGO QR LA TIENDA
        0088044519  Parque-Lleras-
        Pale
        -$ 6.800,00
        31 ago 2026 TRANSF DE PERSONA EJEMPLO null $ 1.000.000,00
        31 ago 2026 IMPTO GOBIERNO 4X1000 -$ 4.615,85
        24 ago 2026 ABONO INTERESES AHORROS $ 0,15
        24 ago 2026 PAGO INTERBANC
        EMPRESA EJEMPLO CO
        8300000000 $ 20.000.000,51
        Dirección IP 1.2.3.4
    """.trimIndent()

    @Test
    fun `lee cada movimiento con su fecha, su monto y su tipo`() {
        val leido = assertNotNull(leerLosMovimientosDeBancolombia(lista))
        assertEquals("7890", leido.numero)
        // 10 renglones con fecha; el abono de $0,15 no se propone.
        assertEquals(9, leido.filas.size)

        val traslado = leido.filas[0]
        assertEquals("2026-09-07", traslado.date)
        assertEquals(1_000_000L, traslado.amount)
        assertEquals(TransactionType.EXPENSE, traslado.type)
        assertEquals("TRASLADO A FONDO DE INVERSION", traslado.description)

        val intereses = leido.filas[2]
        assertEquals(TransactionType.INCOME, intereses.type)
        assertEquals(16L, intereses.amount, "los centavos se redondean al peso")

        val nomina = leido.filas.last()
        assertEquals("2026-08-24", nomina.date)
        assertEquals(20_000_001L, nomina.amount)
        assertEquals(TransactionType.INCOME, nomina.type)
    }

    @Test
    fun `la referencia sale de la descripcion y el prefijo del banco del comercio`() {
        val filas = assertNotNull(leerLosMovimientosDeBancolombia(lista)).filas
        assertEquals("PAGO QR CAFETERIA", filas[1].description)
        assertEquals("CAFETERIA", filas[1].merchant)
        assertEquals("DROGUERIA", filas[3].merchant)
        assertEquals("Medicina Prepagada Pr", filas[4].merchant)
        // Un movimiento partido en cuatro renglones, con el pie de la página en el medio de la lista.
        assertEquals("PAGO QR LA TIENDA Parque-Lleras- Pale", filas[5].description)
        assertEquals(6_800L, filas[5].amount)
        assertEquals("TRANSF DE PERSONA EJEMPLO", filas[6].description)
    }

    @Test
    fun `el 4x1000 y los intereses llegan como cargos del banco que la conciliacion reconoce`() {
        val filas = assertNotNull(leerLosMovimientosDeBancolombia(lista)).filas
        val impuesto = filas.single { it.description.contains("4X1000") }
        assertEquals(4_616L, impuesto.amount)
        assertNotNull(cargoDelBanco(impuesto.description, impuesto.type))
        assertNotNull(cargoDelBanco(filas[2].description, filas[2].type))
    }

    @Test
    fun `un movimiento sin valor no se adivina`() {
        val rota = """
            Sucursal Virtual Personas
            Movimientos: Cuentas
            07 sept 2026 PAGO QR CAFETERIA 0091234567
            06 sept 2026 COMPRA EN  DROGUERIA -$ 31.895,00
        """.trimIndent()
        assertNull(leerLosMovimientosDeBancolombia(rota))
    }

    @Test
    fun `otro papel no se lee como esta lista`() {
        assertNull(leerLosMovimientosDeBancolombia("Extracto de tarjeta de crédito\n07 sept 2026 COMPRA $ 1.000,00"))
        assertNull(leerLosMovimientosDeBancolombia("Sucursal Virtual Personas\nMovimientos: Inversiones\n07 Sep 2026"))
    }
}
