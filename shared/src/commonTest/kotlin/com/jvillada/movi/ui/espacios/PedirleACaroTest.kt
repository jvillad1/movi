package com.jvillada.movi.ui.espacios

import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.saldoDelEspacio
import com.jvillada.movi.data.SaldosOcultosStore
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «Pedirle a Caro» (vuelta de diseño 26), sin dibujo
 *
 * El mensaje (el saldo, los tres gastos más recientes con la parte de quien lee y «y N más»), el
 * botón principal según quién debe, la pregunta antes de saldar y la fecha en el subtítulo de cada
 * gasto.
 */
class PedirleACaroTest {

    private val zona = TimeZone.of("America/Bogota")

    private val casa = Espacio(
        id = "esp_casa",
        nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )

    private fun dia(d: Int, mes: Int = 10): Long = LocalDateTime(2026, mes, d, 12, 0).toInstant(zona).toEpochMilliseconds()

    private fun gasto(id: String, desc: String, monto: Long, fecha: Long, pagadoPor: String = MIEMBRO_YO) = GastoDelEspacio(
        id = id, espacioId = casa.id, pagadoPor = pagadoPor, monto = monto, categoria = "Mercado",
        descripcion = desc, fecha = fecha, partes = partesDelGasto(monto, casa, pagadoPor),
    )

    private fun detalle(gastos: List<GastoDelEspacio>, liquidaciones: List<LiquidacionDelEspacio> = emptyList()) =
        DetalleDelEspacio(
            espacio = casa,
            saldo = saldoDelEspacio(casa, gastos, liquidaciones),
            delPeriodo = saldoDelEspacio(casa, gastos, liquidaciones),
            gastos = gastos.sortedByDescending { it.fecha },
            liquidaciones = liquidaciones,
        )

    /** Cinco gastos que pagó el dueño: Caro debe la mitad de cada uno, $76.050 en total. */
    private val cinco = listOf(
        gasto("g1", "Mercado Éxito", 80_000, dia(18)),
        gasto("g2", "Gas", 40_000, dia(15)),
        gasto("g3", "Internet", 32_100, dia(10)),
        gasto("g4", "Aseo", 20_000, dia(5)),
        gasto("g5", "Agua", 14_000, dia(2)),
    )

    @Test
    fun `el mensaje dice el saldo, los tres gastos mas recientes y cuantos mas`() {
        val mensaje = mensajeParaPedir(detalle(cinco), zona = zona)
        assertEquals(
            "Hola, Caro. Me debes \$93.050 de Casa con Caro:\n" +
                "• Mercado Éxito · 18 oct · tu parte \$40.000\n" +
                "• Gas · 15 oct · tu parte \$20.000\n" +
                "• Internet · 10 oct · tu parte \$16.050\n" +
                "y 2 más.\n" +
                "¿Me lo pasas cuando puedas? ¡Gracias!",
            mensaje,
        )
    }

    @Test
    fun `con pocos gastos no dice y N mas, y con enlace lo agrega`() {
        val mensaje = mensajeParaPedir(detalle(cinco.take(2)), enlace = "https://movi.app/compartido#abc", zona = zona)!!
        assertTrue(mensaje.lines().none { it.startsWith("y ") }, mensaje)
        assertTrue(mensaje.startsWith("Hola, Caro. Me debes \$60.000 de Casa con Caro:"), mensaje)
        assertTrue("El detalle: https://movi.app/compartido#abc" in mensaje, mensaje)
    }

    @Test
    fun `solo cuentan los gastos desde el ultimo saldado`() {
        // Saldaron el 12: lo de antes ya se pagó, y el saldo es solo lo del 15 y el 18.
        val antes = cinco.filter { it.fecha < dia(12) }
        val liquidacion = LiquidacionDelEspacio(
            id = "l1", espacioId = casa.id, de = "dst_caro", a = MIEMBRO_YO,
            monto = antes.sumOf { it.partes["dst_caro"] ?: 0L }, fecha = dia(12),
        )
        val mensaje = mensajeParaPedir(detalle(cinco, listOf(liquidacion)), zona = zona)!!
        assertTrue(mensaje.startsWith("Hola, Caro. Me debes \$60.000 de Casa con Caro:"), mensaje)
        assertTrue("Internet" !in mensaje && "y 2 más" !in mensaje, mensaje)
    }

    @Test
    fun `las cifras del mensaje van enteras aunque los saldos esten ocultos`() {
        SaldosOcultosStore.poner(true)
        try {
            assertTrue("\$93.050" in mensajeParaPedir(detalle(cinco), zona = zona)!!)
        } finally {
            SaldosOcultosStore.clear()
        }
    }

    @Test
    fun `sin deuda a tu favor no hay mensaje`() {
        // Caro pagó: tú le debes.
        val leDebes = detalle(listOf(gasto("g1", "Mercado", 80_000, dia(18), pagadoPor = "dst_caro")))
        assertNull(mensajeParaPedir(leDebes, zona = zona))
        assertNull(mensajeParaPedir(detalle(emptyList()), zona = zona))
    }

    @Test
    fun `el boton principal depende de quien debe`() {
        assertEquals(PrincipalDelSaldo.Pedir("Caro"), principalDelSaldo(casa, detalle(cinco).saldo))
        assertEquals("Pedirle a Caro", (principalDelSaldo(casa, detalle(cinco).saldo) as PrincipalDelSaldo.Pedir).texto)
        val leDebes = detalle(listOf(gasto("g1", "Mercado", 80_000, dia(18), pagadoPor = "dst_caro")))
        assertEquals(PrincipalDelSaldo.MarcarPagado, principalDelSaldo(casa, leDebes.saldo))
        assertEquals(PrincipalDelSaldo.Nada, principalDelSaldo(casa, detalle(emptyList()).saldo))
    }

    @Test
    fun `antes de saldar se pregunta si ya pago y que deja el espacio en cero`() {
        assertEquals(
            "¿Caro ya te pagó \$93.050?" to "Esto deja el espacio en cero.",
            confirmacionDeSaldado(casa, detalle(cinco).saldo),
        )
        val leDebes = detalle(listOf(gasto("g1", "Mercado", 80_000, dia(18), pagadoPor = "dst_caro")))
        assertNull(confirmacionDeSaldado(casa, leDebes.saldo), "si tú le debes, no se pregunta si te pagó")
    }

    @Test
    fun `cada gasto dice su fecha en el subtitulo, primero`() {
        val deCaro = gasto("g1", "Mercado del sábado", 80_000, dia(18), pagadoPor = "dst_caro")
        // Vuelta 31: la fecha va primero (al final se cortaba) y luego quién pagó y la categoría.
        assertEquals("18 oct · Pagó Caro · Mercado", quienPagoElGasto(casa, deCaro, zona))
        assertEquals("2 oct · Pagaste tú · Mercado", quienPagoElGasto(casa, cinco.last(), zona))
        assertEquals("Pagaste tú · Mercado", quienPagoElGasto(casa, cinco.last().copy(fecha = 0L), zona), "sin fecha, sin ese tramo")
        assertEquals("2 oct · Pagaste tú", quienPagoElGasto(casa, cinco.last().copy(categoria = ""), zona))
    }

    @Test
    fun `la categoria no se repite si ya es el titulo`() {
        val mercado = gasto("g1", "Mercado", 80_000, dia(21), pagadoPor = "dst_caro")
        assertEquals("Mercado", tituloDelGasto(mercado))
        assertEquals("21 oct · Pagó Caro", quienPagoElGasto(casa, mercado, zona))
        val sinDescripcion = mercado.copy(descripcion = "")
        assertEquals("Mercado", tituloDelGasto(sinDescripcion))
        assertEquals("21 oct · Pagó Caro", quienPagoElGasto(casa, sinDescripcion, zona))
    }

    @Test
    fun `el enlace de WhatsApp lleva el mensaje codificado`() {
        assertEquals("https://wa.me/?text=Me%20debes%20%2476.050%0A%C2%BFMe%20lo%20pasas%3F", enlaceDeWhatsApp("Me debes \$76.050\n¿Me lo pasas?"))
    }
}
