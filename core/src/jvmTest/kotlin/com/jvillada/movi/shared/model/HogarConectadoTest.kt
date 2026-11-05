package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # El hogar conectado (vuelta 131): la unión al leer
 *
 * Juan (`dst_caro` es su tercero Caro) y Caro (`dst_juan` es su tercero Juan) conectan sus grupos. Lo que
 * anota uno se traduce al lado del otro: los ids de los miembros se cruzan, el movimiento no cruza
 * (`eventoId` nulo) y queda la marca de quién lo anotó. Con eso, el saldo de uno es el espejo del otro.
 */
class HogarConectadoTest {

    private val casaDeJuan = Espacio(
        id = "esp_juan", nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Juan"), MiembroDelEspacio("dst_caro", "Caro")),
    )
    private val casaDeCaro = Espacio(
        id = "esp_caro", nombre = "Casa con Caro",
        miembros = listOf(MiembroDelEspacio(MIEMBRO_YO, "Caro"), MiembroDelEspacio("dst_juan", "Juan")),
    )
    private val desde = 1_000L
    /** El lado de Juan: lo que anotó Caro, traducido a él. */
    private val enJuan = LadoDelOtro(miEspacioId = "esp_juan", miMiembroDelOtro = "dst_caro", suMiembroDeMi = "dst_juan", desde = desde)
    /** El lado de Caro: lo que anotó Juan, traducido a ella. */
    private val enCaro = LadoDelOtro(miEspacioId = "esp_caro", miMiembroDelOtro = "dst_juan", suMiembroDeMi = "dst_caro", desde = desde)

    private fun gasto(id: String, espacio: Espacio, pagadoPor: String, monto: Long, eventoId: String? = null, fecha: Long = 2_000L) =
        GastoDelEspacio(
            id = id, espacioId = espacio.id, pagadoPor = pagadoPor, monto = monto, categoria = "Mercado",
            descripcion = "Mercado", fecha = fecha, eventoId = eventoId, partes = partesDelGasto(monto, espacio, pagadoPor),
        )

    @Test
    fun `un gasto de Caro con su movimiento llega a Juan con los ids cruzados, sin el movimiento y con la marca`() {
        val deCaro = gasto("g1", casaDeCaro, MIEMBRO_YO, 240_001, eventoId = "ev_de_caro")
            .copy(reparto = RepartoDelGasto(forma = FormaDeRepartir.MONTOS, montos = mapOf(MIEMBRO_YO to 140_001, "dst_juan" to 100_000)))

        val enLadoDeJuan = gastoDelOtroLado(deCaro, enJuan, escritoEn = desde) { if (it == "Mercado") "Mercado" else "Otros" }!!

        assertEquals("esp_juan", enLadoDeJuan.espacioId)
        assertEquals("dst_caro", enLadoDeJuan.pagadoPor, "el «yo» de Caro es el tercero Caro de Juan")
        assertEquals(mapOf("dst_caro" to 120_001L, MIEMBRO_YO to 120_000L), enLadoDeJuan.partes)
        assertNull(enLadoDeJuan.eventoId, "nunca cruza el id de un movimiento de la otra cuenta")
        assertEquals("dst_caro", enLadoDeJuan.anotadoPor)
        assertEquals(mapOf("dst_caro" to 140_001L, MIEMBRO_YO to 100_000L), enLadoDeJuan.reparto!!.montos)
        assertEquals(240_001, enLadoDeJuan.monto)
        assertEquals("Mercado", enLadoDeJuan.descripcion)
    }

    @Test
    fun `lo que Caro anoto que pago Juan llega a Juan como pagado por el, y la categoria se resuelve en su lado`() {
        val pagoJuan = gasto("g2", casaDeCaro, "dst_juan", 50_000).copy(categoria = "Hogar raro")

        val enLadoDeJuan = gastoDelOtroLado(pagoJuan, enJuan, escritoEn = desde) { "Otros" }!!

        assertEquals(MIEMBRO_YO, enLadoDeJuan.pagadoPor)
        assertEquals("Otros", enLadoDeJuan.categoria)
    }

    @Test
    fun `una liquidacion cruza de y a, sin movimiento ni su concepto, y no espera transferencia`() {
        val l = LiquidacionDelEspacio(
            id = "l1", espacioId = "esp_caro", de = MIEMBRO_YO, a = "dst_juan", monto = 70_000, fecha = 3_000L,
            eventoId = "ev_transferencia_de_caro", movimiento = "Transferencia a Juan",
        )

        val enLadoDeJuan = liquidacionDelOtroLado(l, enJuan, escritoEn = desde)!!

        assertEquals("dst_caro", enLadoDeJuan.de)
        assertEquals(MIEMBRO_YO, enLadoDeJuan.a)
        assertNull(enLadoDeJuan.eventoId)
        assertNull(enLadoDeJuan.movimiento)
        assertEquals("dst_caro", enLadoDeJuan.anotadoPor)
        assertEquals("esp_juan", enLadoDeJuan.espacioId)
        // Revisión: el saldado de Caro espera la transferencia de Juan hasta que Juan la vincule (su
        // `eventoId`, de este lado, es el vínculo propio de Juan).
        assertTrue(esperaLaTransferencia(enLadoDeJuan, ahora = 3_000L))
        assertFalse(esperaLaTransferencia(enLadoDeJuan.copy(eventoId = "ev_de_juan"), ahora = 3_000L))
    }

    @Test
    fun `cruza lo escrito desde la conexion, aunque su fecha sea de antes, y no lo que nombra a un tercero ajeno`() {
        val deAyer = gasto("ayer", casaDeCaro, MIEMBRO_YO, 10_000, fecha = desde - 86_400_000L)
        assertNull(gastoDelOtroLado(deAyer, enJuan, escritoEn = desde - 1), "escrito antes de conectarse: no cruza")
        assertEquals("ayer", gastoDelOtroLado(deAyer, enJuan, escritoEn = desde)?.id, "el mercado de ayer repartido hoy sí cruza")
        val conOtro = gasto("raro", casaDeCaro, MIEMBRO_YO, 10_000).copy(partes = mapOf(MIEMBRO_YO to 5_000L, "dst_mama" to 5_000L))
        assertNull(gastoDelOtroLado(conOtro, enJuan, escritoEn = desde))
        assertNull(liquidacionDelOtroLado(LiquidacionDelEspacio(de = "dst_mama", a = MIEMBRO_YO, monto = 1, fecha = 2_000L), enJuan, escritoEn = desde))
        assertNull(liquidacionDelOtroLado(LiquidacionDelEspacio(de = MIEMBRO_YO, a = "dst_juan", monto = 1, fecha = 9_000L), enJuan, escritoEn = desde - 1))
    }

    /** El saldo que ve Juan es el espejo del que ve Caro, al peso, con montos impares. */
    @Test
    fun `el saldo es espejo - Juan mas X es Caro menos X`() {
        val deJuan = listOf(
            gasto("j1", casaDeJuan, MIEMBRO_YO, 240_001, eventoId = "ev1"),
            gasto("j2", casaDeJuan, "dst_caro", 33_333),
            gasto("j3", casaDeJuan, MIEMBRO_YO, 7, eventoId = "ev3"),
        )
        val deCaro = listOf(
            gasto("c1", casaDeCaro, MIEMBRO_YO, 99_999, eventoId = "evc1"),
            gasto("c2", casaDeCaro, "dst_juan", 12_345),
        )
        val liqDeJuan = listOf(LiquidacionDelEspacio(id = "lj", espacioId = "esp_juan", de = "dst_caro", a = MIEMBRO_YO, monto = 10_001, fecha = 2_500L))
        val liqDeCaro = listOf(LiquidacionDelEspacio(id = "lc", espacioId = "esp_caro", de = MIEMBRO_YO, a = "dst_juan", monto = 3_333, fecha = 2_600L))

        val saldoJuan = saldoDelEspacio(
            casaDeJuan,
            deJuan + deCaro.mapNotNull { gastoDelOtroLado(it, enJuan, escritoEn = desde) },
            liqDeJuan + liqDeCaro.mapNotNull { liquidacionDelOtroLado(it, enJuan, escritoEn = desde) },
        )
        val saldoCaro = saldoDelEspacio(
            casaDeCaro,
            deCaro + deJuan.mapNotNull { gastoDelOtroLado(it, enCaro, escritoEn = desde) },
            liqDeCaro + liqDeJuan.mapNotNull { liquidacionDelOtroLado(it, enCaro, escritoEn = desde) },
        )

        val x = saldoJuan.netoDe(MIEMBRO_YO)
        assertTrue(x != 0L, "el caso tiene que dejar una deuda")
        assertEquals(-x, saldoCaro.netoDe(MIEMBRO_YO))
        assertEquals(saldoJuan.netoDe("dst_caro"), saldoCaro.netoDe(MIEMBRO_YO))
        assertEquals(saldoJuan.total, saldoCaro.total)
        assertEquals(saldoJuan.reembolsos.single().monto, saldoCaro.reembolsos.single().monto)
    }

    @Test
    fun `el codigo se limpia y solo vale con su alfabeto`() {
        assertEquals("K7P3Q9", codigoLimpio(" k7p-3q 9 "))
        assertTrue(pareceUnCodigo("K7P3Q9"))
        assertFalse(pareceUnCodigo("K7P3O9"), "la O no está en el alfabeto")
        assertFalse(pareceUnCodigo("K7P3Q"))
        listOf('0', 'O', '1', 'I', 'L').forEach { assertFalse(it in ALFABETO_DEL_CODIGO, "$it se confunde") }
    }

    @Test
    fun `desconectar dice cuantos gastos de Caro dejan de verse y como queda el saldo`() {
        val propio = gasto("j1", casaDeJuan, MIEMBRO_YO, 100_000, eventoId = "ev1")
        val deCaro = gastoDelOtroLado(gasto("c1", casaDeCaro, MIEMBRO_YO, 60_000), enJuan, escritoEn = desde)!!
        val saldo = saldoDelEspacio(casaDeJuan, listOf(propio, deCaro), emptyList())
        val detalle = DetalleDelEspacio(casaDeJuan, saldo, saldo, listOf(propio, deCaro))

        val r = loQueSeDejaDeVer(detalle)

        assertEquals(1, r.gastos)
        assertEquals(20_000, r.netoAntes)
        assertEquals(50_000, r.netoDespues)
    }

    @Test
    fun `solo se invita desde un grupo de dos sin conectar`() {
        assertTrue(sePuedeInvitar(casaDeJuan))
        assertFalse(sePuedeInvitar(casaDeJuan.copy(conexion = ConexionDelEspacio("dst_caro", "Caro", 1L))))
        assertFalse(sePuedeInvitar(casaDeJuan.copy(miembros = casaDeJuan.miembros + MiembroDelEspacio("dst_mama", "Mamá"))))
    }

    @Test
    fun `el saldado que Caro ya marco se reconoce por direccion, monto parecido y fecha reciente`() {
        val dia = 24L * 60 * 60 * 1000
        val ahora = 100L * dia
        val deCaro = LiquidacionDelEspacio(id = "lc", de = "dst_caro", a = MIEMBRO_YO, monto = 100_000, fecha = ahora - dia, anotadoPor = "dst_caro")
        val propia = deCaro.copy(id = "lj", anotadoPor = null)
        assertEquals("lc", saldadoQueYaMarcoElOtro(listOf(propia, deCaro), emptyList(), "dst_caro", MIEMBRO_YO, 100_500, ahora)?.id)
        assertNull(saldadoQueYaMarcoElOtro(listOf(propia), emptyList(), "dst_caro", MIEMBRO_YO, 100_000, ahora), "lo propio no cuenta")
        assertNull(saldadoQueYaMarcoElOtro(listOf(deCaro), emptyList(), MIEMBRO_YO, "dst_caro", 100_000, ahora), "otra dirección")
        assertNull(saldadoQueYaMarcoElOtro(listOf(deCaro), emptyList(), "dst_caro", MIEMBRO_YO, 120_000, ahora), "otro monto")
        assertNull(saldadoQueYaMarcoElOtro(listOf(deCaro.copy(fecha = ahora - 8 * dia)), emptyList(), "dst_caro", MIEMBRO_YO, 100_000, ahora), "de hace más de 7 días")
    }

    @Test
    fun `con la conexion, Pago otro no ofrece a la persona conectada`() {
        assertEquals(listOf("dst_caro"), quienesPuedenPagarDesdeAca(casaDeJuan).map { it.id })
        assertTrue(quienesPuedenPagarDesdeAca(casaDeJuan.copy(conexion = ConexionDelEspacio("dst_caro", "Caro", 1L))).isEmpty())
    }

    @Test
    fun `el codigo dice si trae un caracter que no existe`() {
        assertTrue(traeCaracteresQueNoExisten("K7P3O9"))
        assertTrue(traeCaracteresQueNoExisten("K7P319"))
        assertFalse(traeCaracteresQueNoExisten("K7P3Q9"))
    }

    /** Revisión 2 (N-3): ni el que ya vinculé ni el anterior al último gasto del grupo. */
    @Test
    fun `ya lo marco no cuenta el que ya vincule ni el de antes del ultimo gasto`() {
        val dia = 24L * 60 * 60 * 1000
        val ahora = 100L * dia
        val deCaro = LiquidacionDelEspacio(id = "lc", de = "dst_caro", a = MIEMBRO_YO, monto = 100_000, fecha = ahora - 3 * dia, anotadoPor = "dst_caro")
        assertEquals("lc", saldadoQueYaMarcoElOtro(listOf(deCaro), emptyList(), "dst_caro", MIEMBRO_YO, 100_000, ahora)?.id)
        assertNull(saldadoQueYaMarcoElOtro(listOf(deCaro.copy(eventoId = "ev_mio")), emptyList(), "dst_caro", MIEMBRO_YO, 100_000, ahora), "ya vinculado")
        val gastoDespues = gasto("g", casaDeJuan, MIEMBRO_YO, 50_000, fecha = ahora - dia)
        assertNull(saldadoQueYaMarcoElOtro(listOf(deCaro), listOf(gastoDespues), "dst_caro", MIEMBRO_YO, 100_000, ahora), "es de antes del último gasto")
    }

    /** Revisión 2 (N-2): lo de antes de conectarse no cruza y se cuenta aparte. */
    @Test
    fun `lo de antes de conectarse no cruza y su saldo va aparte`() {
        val antes = gasto("a", casaDeJuan, MIEMBRO_YO, 60_000, eventoId = "ev").copy(deAntes = true)
        val despues = gasto("d", casaDeJuan, MIEMBRO_YO, 100_000, eventoId = "ev2")
        assertNull(gastoDelOtroLado(antes, enCaro, escritoEn = desde + 1), "un gasto de antes nunca cruza")
        assertNull(liquidacionDelOtroLado(LiquidacionDelEspacio(de = "dst_caro", a = MIEMBRO_YO, monto = 1, fecha = desde + 1, deAntes = true), enCaro, escritoEn = desde + 1))
        assertEquals(30_000, netoDeAntesDeConectarse(casaDeJuan, listOf(antes, despues), emptyList()))
        assertEquals(listOf("d"), loQueVenLosDos(listOf(antes, despues)).map { it.id })
        val saldadoDeAntes = LiquidacionDelEspacio(de = "dst_caro", a = MIEMBRO_YO, monto = 30_000, fecha = desde + 5, deAntes = true)
        assertEquals(0, netoDeAntesDeConectarse(casaDeJuan, listOf(antes, despues), listOf(saldadoDeAntes)))
    }
}
