package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.ClaseDeCargo
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.cargoDelBanco
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El extracto de la tarjeta de Nu, leído sin IA (vuelta 94). El texto tiene la forma exacta que saca
 * PDFBox del PDF real (la fecha partida en dos renglones, la descripción en varios, el rótulo «Intereses
 * en mora» pegado a la descripción, el pie de página en el medio de la tabla, los «↪»); el nombre, las
 * cifras, los comercios y las fechas son inventados.
 */
class ExtractoDeNuTest {

    private fun extracto(pagoMinimo: String = "\$101.240,00", sinLaLibreria: Boolean = false): String = """
        Hola, Ana
        Llegó tu extracto de Octubre
        Ana Ejemplo Tus tarjetas
        • • • • 1111 • • • • 2222
        Tarjeta virtualTarjeta física
        Resumen de tu extracto
        Tu cupo definido ${'$'}3.000.000,00
        Usado
        ${'$'}500.000,00
        ${'$'}100.000,00Deuda a pagar este mes
        + ${'$'}5.400,00Intereses
        Comisiones por servicio + ${'$'}0,00
        Cargos por conversión + ${'$'}540,00
        PAGO MÍNIMO $pagoMinimo
        Deuda restante + ${'$'}310.000,00
        Fecha límite de pago
        03 NOV 2026
        Fecha de corte
        12 OCT 2026
        Periodo facturado
        11 SEP 2026 - 11 OCT 2026
        1 / 3
        Nu Financiera
        Bogotá D.C. Colombia
        Fecha Descripción Valor Cuotas Valor
        del mes
        Interés del mes
        Porcentaje y valor este mes
        Total a pagar
        por pagar
        Restante
        10 OCT
        2026
        Panaderia La Esquina
        Norte
        ${'$'}50.000,00 1 de 1 ${'$'}50.000,00 2.09%        ${'$'}0,00 ${'$'}50.000,00 ${'$'}0,00
    """.trimIndent() + "\n" + (if (sinLaLibreria) "" else """
        04 OCT
        2026
        Libreria Central ${'$'}120.000,00 1 de 12 ${'$'}10.000,00 2.09%        ${'$'}0,00 ${'$'}10.000,00 ${'$'}110.000,00
        ↪ Comisión por cambio de moneda ${'$'}540,00
    """.trimIndent() + "\n") + """
        27 SEP
        2026
        Pago ${'$'}50.000,00 ${'$'}0,00 -${'$'}100,00
        ↪ A capital ${'$'}0,00
        ↪ A intereses ${'$'}50.000,00
        ↪ A cargos y comisiones ${'$'}0,00
        21 SEP
        2026
        Cafe Central ${'$'}15.300,00 ${'$'}15.300,00 2.16%        ${'$'}0,00 ${'$'}15.300,00 ${'$'}0,00
        Nu Financiera
        Bogotá D.C. Colombia
        NIT 900.000.000-0
        2 / 3
        15 AGO
        2026
        Curso Online ${'$'}240.000,00 2 de 12 ${'$'}20.000,00 2.09%        ${'$'}3.800,00 ${'$'}23.800,00 ${'$'}200.000,00
        18 JUL 2026 Tienda
        Vieja
        Intereses en
        mora
        ${'$'}60.000,00 ${'$'}0,00 2.10%        ${'$'}1.500,00
        2.10%        ${'$'}100,00
        ${'$'}1.600,00 ${'$'}0,00
        Pago mínimo
        $pagoMinimo
        ¿Tienes preguntas sobre tu extracto?
        Nu Financiera
        3 / 3
    """.trimIndent()

    @Test
    fun `lee las compras y los pagos del periodo, con su fecha, su valor entero y sus cuotas`() {
        val leido = assertNotNull(leerElExtractoDeNu(extracto()))
        assertEquals(LocalDate.of(2026, 10, 12), leido.corte)
        assertEquals(LocalDate.of(2026, 9, 11), leido.inicio)
        assertEquals(listOf("1111", "2222"), leido.numeros)

        val porNombre = leido.filas.associateBy { it.description }
        val panaderia = assertNotNull(porNombre["Panaderia La Esquina Norte"])
        assertEquals("2026-10-10", panaderia.date)
        assertEquals(50_000L, panaderia.amount)
        assertEquals(TransactionType.EXPENSE, panaderia.type)
        assertEquals(1, panaderia.cuotas)
        assertEquals("COP", panaderia.currency)

        // La compra diferida va con su valor entero y sus 12 cuotas, como la tarjeta de Bancolombia.
        val libreria = assertNotNull(porNombre["Libreria Central"])
        assertEquals(120_000L, libreria.amount)
        assertEquals(12, libreria.cuotas)

        // Sin columna de cuotas (la forma del extracto anterior): sin cuotas.
        val cafe = assertNotNull(porNombre["Cafe Central"])
        assertEquals(15_300L, cafe.amount)
        assertNull(cafe.cuotas)

        // El pago entra a la tarjeta: lo empareja la conciliación con el traspaso ya anotado.
        val pago = assertNotNull(porNombre["Pago"])
        assertEquals(TransactionType.INCOME, pago.type)
        assertEquals(50_000L, pago.amount)
        assertEquals("2026-09-27", pago.date)
        assertNull(pago.cuotas)
    }

    @Test
    fun `las compras de otro periodo no se proponen otra vez, solo sus intereses`() {
        val leido = assertNotNull(leerElExtractoDeNu(extracto()))
        val nombres = leido.filas.map { it.description }
        assertTrue("Curso Online" !in nombres)
        assertTrue(nombres.none { it.startsWith("Tienda") })
        // Panadería, librería, su comisión, el pago, el café y los dos intereses.
        assertEquals(7, leido.filas.size)
    }

    @Test
    fun `los cargos del extracto salen como movimientos que reconoce cargoDelBanco`() {
        val porNombre = assertNotNull(leerElExtractoDeNu(extracto())).filas.associateBy { it.description }

        // Los intereses de las filas, sumados, con la fecha de corte.
        val corrientes = assertNotNull(porNombre[INTERESES_CORRIENTES])
        assertEquals(5_300L, corrientes.amount)
        assertEquals("2026-10-12", corrientes.date)
        assertEquals(TransactionType.EXPENSE, corrientes.type)
        val mora = assertNotNull(porNombre[INTERESES_DE_MORA])
        assertEquals(100L, mora.amount)

        // La comisión que cuelga de una compra, con la fecha de esa compra.
        val comision = assertNotNull(porNombre["Comisión por cambio de moneda"])
        assertEquals(540L, comision.amount)
        assertEquals("2026-10-04", comision.date)

        listOf(corrientes, mora, comision).forEach {
            assertEquals(ClaseDeCargo.COMISION, cargoDelBanco(it.description, it.type)?.clase, it.description)
        }
    }

    @Test
    fun `un extracto que no cuadra con su pago minimo no se lee`() {
        // Un peso de diferencia.
        assertNull(leerElExtractoDeNu(extracto(pagoMinimo = "\$101.241,00")))
        // Una fila que no se leyó (acá, porque no está) rompe la suma.
        assertNull(leerElExtractoDeNu(extracto(sinLaLibreria = true)))
    }

    @Test
    fun `una fila cuyas cifras no cuadran no se lee`() {
        val mal = extracto().replace(
            "Panaderia La Esquina\nNorte\n\$50.000,00 1 de 1 \$50.000,00 2.09%        \$0,00 \$50.000,00 \$0,00",
            "Panaderia La Esquina\nNorte\n\$50.000,00 1 de 1 \$50.000,00 2.09%        \$0,00 \$51.000,00 \$0,00",
        )
        assertTrue(mal != extracto())
        assertNull(leerElExtractoDeNu(mal))
    }

    @Test
    fun `otro papel no es un extracto de Nu`() {
        assertNull(leerElExtractoDeNu("Sucursal Virtual Personas\nMovimientos: Cuentas\n07 sept 2026 COMPRA \$ 1.000,00"))
        assertNull(leerElExtractoDeNu("Nu Financiera\nTu cajita\nRendimientos \$1.000,00"))
    }
}
