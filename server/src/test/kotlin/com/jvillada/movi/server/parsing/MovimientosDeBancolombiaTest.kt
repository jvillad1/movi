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

    // ── La tarjeta de crédito ──────────────────────────────────────────────

    private val tarjeta = """
        Sucursal Virtual Personas
        Movimientos: Tarjetas de Crédito
        Lunes, 7 de septiembre de 2026, 10:38 p. m.
        JUAN EJEMPLO
        Tarjeta de Crédito
        MASTER EJEMPLO *1234
        Fecha Descripción Fecha de corte Valor Cuotas
        01 sept 2026 SUSCRIPCION* EJEMPLO USD $ 20,00 36
        31 ago 2026 PLATAFORMA DE VIDEO COP $ 44.900,00 1
        18 ago 2026 AJUSTE AUT INTERESES USD -$ 0,06 0
        17 ago 2026 INTERESES CORRIENTES 17 ago 2026 USD $ 35,72 0
        17 ago 2026 CUOTA DE MANEJO 17 ago 2026 COP $ 35.672,00 0
        Dirección IP 1.2.3.4
        Fecha Descripción Fecha de corte Valor Cuotas
        04 sept 2026 ABONO WOMPI/PSE COP -
        $ 9.000.000,00
        0
        19 ago 2026 AMPLIACION DE PLAZO COP 
        $ 26.807.543,00
        36
    """.trimIndent()

    @Test
    fun `la tarjeta - un cobro es un gasto, un abono una entrada, y cada uno en su moneda`() {
        val leido = assertNotNull(leerLosMovimientosDeBancolombia(tarjeta))
        assertEquals("1234", leido.numero)
        // 7 renglones con fecha; el ajuste de US$0,06 no se propone.
        assertEquals(6, leido.filas.size)

        val suscripcion = leido.filas[0]
        assertEquals("USD", suscripcion.currency)
        assertEquals(20L, suscripcion.amount)
        assertEquals(TransactionType.EXPENSE, suscripcion.type)

        val intereses = leido.filas[2]
        assertEquals("INTERESES CORRIENTES", intereses.description, "la fecha de corte no es parte de la descripción")
        assertEquals(36L, intereses.amount)
        assertNotNull(cargoDelBanco(intereses.description, intereses.type))

        val manejo = leido.filas[3]
        assertEquals("COP", manejo.currency)
        assertEquals(35_672L, manejo.amount)
        assertNotNull(cargoDelBanco(manejo.description, manejo.type))

        val abono = leido.filas[4]
        assertEquals("2026-09-04", abono.date)
        assertEquals(9_000_000L, abono.amount)
        assertEquals(TransactionType.INCOME, abono.type)

        assertEquals(26_807_543L, leido.filas[5].amount)
        assertEquals(TransactionType.EXPENSE, leido.filas[5].type)
    }

    @Test
    fun `vuelta 63 - cada cobro trae sus cuotas, un cargo cuenta como una y un abono no tiene`() {
        val leido = assertNotNull(leerLosMovimientosDeBancolombia(tarjeta))
        assertEquals(36, leido.filas[0].cuotas, "la suscripción en dólares diferida a 36")
        assertEquals(1, leido.filas[1].cuotas, "la plataforma de video a una cuota")
        assertEquals(1, leido.filas[3].cuotas, "un cargo del banco («0») no se difirió")
        assertEquals(null, leido.filas[4].cuotas, "un abono no tiene cuotas")
        assertEquals(36, leido.filas[5].cuotas, "la ampliación de plazo, partida en renglones")
    }

    // ── El fondo de inversión ──────────────────────────────────────────────

    private val fondo = """
        Sucursal Virtual Personas
        Movimientos: Inversiones
        Lunes, 26 de octubre de 2026, 1:20 a. m.
        JUAN EJEMPLO
        Fecha Descripción Número de unidades Valor unidad Valor en pesos
        19 Oct 2026
        Gravamen a los 
        movimientos 
        financieros
        0,158864 -$ 7.728,00 -$ 7.728,00
        19 Oct 2026
        Traslado hacia cuenta 
        de ahorros
        39,716000 -$ 1.932.000,00 -$ 1.932.000,00
        19 Oct 2026
        Retención en la fuente
        0,001849 -$ 89,94 -$ 89,94
        Dirección IP 1.2.3.4
        Fecha Descripción Número de unidades Valor unidad Valor en pesos
        28 Sep 2026
        Traslado desde cuenta 
        de ahorros
        82,596034 $ 4.000.000,00 $ 4.000.000,00
        07 Sep 2026
        Traslado desde cuenta 
        de ahorros
        -- $ 1.932.000,00 $ 1.932.000,00
    """.trimIndent()

    @Test
    fun `el fondo - traslados, 4x1000 y retencion, sin las unidades en la descripcion`() {
        val leido = assertNotNull(leerLosMovimientosDeBancolombia(fondo))
        assertEquals(true, leido.deUnFondo)
        assertNull(leido.numero)
        assertEquals(5, leido.filas.size)

        val gmf = leido.filas[0]
        assertEquals("2026-10-19", gmf.date)
        assertEquals("Gravamen a los movimientos financieros", gmf.description)
        assertEquals(7_728L, gmf.amount)
        assertEquals(TransactionType.EXPENSE, gmf.type)
        assertNotNull(cargoDelBanco(gmf.description, gmf.type))

        val retencion = leido.filas[2]
        assertEquals(90L, retencion.amount)
        assertNotNull(cargoDelBanco(retencion.description, retencion.type))

        val entrada = leido.filas[3]
        assertEquals("Traslado desde cuenta de ahorros", entrada.description)
        assertEquals(4_000_000L, entrada.amount)
        assertEquals(TransactionType.INCOME, entrada.type)
        // Sin unidades («--»), como el primer traslado de la cuenta.
        assertEquals(1_932_000L, leido.filas[4].amount)
    }
}
