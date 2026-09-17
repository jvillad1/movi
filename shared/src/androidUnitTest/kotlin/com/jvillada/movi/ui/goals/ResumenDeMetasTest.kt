package com.jvillada.movi.ui.goals

import com.jvillada.movi.shared.model.Goal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El encabezado de Metas decía plata que no existe.
 *
 * Con «Ahorros» en $10.000.000 y dos metas encima —«Viaje» de $5.000.000 y «Colchón» de
 * $20.000.000— el encabezado cantaba «Total ahorrado $20.000.000 de $25.000.000 · 80 %»: el
 * mismo saldo contado dos veces, porque el `saved` de cada meta ES el saldo de su cuenta.
 */
class ResumenDeMetasTest {

    private fun meta(nombre: String, objetivo: Long, cuenta: String, saldo: Long) =
        Goal(id = "goal_$nombre", name = nombre, target = objetivo, accountId = cuenta, saved = saldo)

    @Test
    fun `dos metas sobre la misma cuenta no suman esa plata dos veces`() {
        val resumen = resumenDeMetas(
            listOf(
                meta("Viaje", 5_000_000, cuenta = "acc_ahorros", saldo = 10_000_000),
                meta("Colchón", 20_000_000, cuenta = "acc_ahorros", saldo = 10_000_000),
            ),
        )

        assertEquals(10_000_000L, resumen.ahorrado, "hay $10.000.000, no $20.000.000")
        assertEquals(25_000_000L, resumen.objetivo, "lo que quiere juntar sí se suma meta por meta")
        assertEquals(40, (resumen.porcentaje * 100).toInt(), "10 de 25, no 20 de 25")
        assertEquals("2 metas", resumen.rotuloDeCantidad)
        assertEquals(setOf("acc_ahorros"), resumen.cuentasCompartidas)
    }

    @Test
    fun `ninguna meta se declara completada con plata prometida a otra`() {
        val viaje = meta("Viaje", 5_000_000, cuenta = "acc_ahorros", saldo = 10_000_000)
        val colchon = meta("Colchón", 20_000_000, cuenta = "acc_ahorros", saldo = 10_000_000)
        val resumen = resumenDeMetas(listOf(viaje, colchon))

        // «Viaje» llega a su objetivo mirando el saldo solo — pero ese saldo también es de
        // «Colchón». La pantalla lo dice en vez de cantar COMPLETADA.
        assertTrue(viaje.saved >= viaje.target, "el saldo alcanza para el objetivo de Viaje")
        assertTrue(resumen.comparteCuenta(viaje))
        assertTrue(resumen.comparteCuenta(colchon))
        assertTrue(resumen.hayCuentasCompartidas)
    }

    @Test
    fun `metas en cuentas distintas suman las dos`() {
        val resumen = resumenDeMetas(
            listOf(
                meta("Viaje", 5_000_000, cuenta = "acc_ahorros", saldo = 4_000_000),
                meta("Carro", 15_000_000, cuenta = "acc_inversion", saldo = 6_000_000),
            ),
        )

        assertEquals(10_000_000L, resumen.ahorrado)
        assertEquals(20_000_000L, resumen.objetivo)
        assertEquals(50, (resumen.porcentaje * 100).toInt())
        assertEquals(emptySet(), resumen.cuentasCompartidas)
        assertFalse(resumen.hayCuentasCompartidas)
    }

    @Test
    fun `una sola meta se lee tal cual y dice «1 meta»`() {
        val resumen = resumenDeMetas(listOf(meta("Viaje", 5_000_000, cuenta = "acc_ahorros", saldo = 2_500_000)))

        assertEquals(2_500_000L, resumen.ahorrado)
        assertEquals(5_000_000L, resumen.objetivo)
        assertEquals(50, (resumen.porcentaje * 100).toInt())
        assertEquals("1 meta", resumen.rotuloDeCantidad, "decía «1 metas»")
        assertFalse(resumen.hayCuentasCompartidas)
    }

    @Test
    fun `sin metas no hay porcentaje ni division por cero`() {
        val resumen = resumenDeMetas(emptyList())

        assertEquals(0L, resumen.ahorrado)
        assertEquals(0L, resumen.objetivo)
        assertEquals(0f, resumen.porcentaje)
        assertEquals("0 metas", resumen.rotuloDeCantidad)
        assertFalse(resumen.hayCuentasCompartidas)
    }

    @Test
    fun `el porcentaje nunca pasa de cien`() {
        val resumen = resumenDeMetas(
            listOf(meta("Viaje", 1_000_000, cuenta = "acc_ahorros", saldo = 9_000_000)),
        )
        assertEquals(1f, resumen.porcentaje)
    }

    @Test
    fun `tres metas, dos compartiendo cuenta, cuentan esa cuenta una vez`() {
        val resumen = resumenDeMetas(
            listOf(
                meta("Viaje", 5_000_000, cuenta = "acc_ahorros", saldo = 10_000_000),
                meta("Colchón", 20_000_000, cuenta = "acc_ahorros", saldo = 10_000_000),
                meta("Carro", 15_000_000, cuenta = "acc_inversion", saldo = 5_000_000),
            ),
        )

        assertEquals(15_000_000L, resumen.ahorrado, "10 de Ahorros (una vez) + 5 de Inversión")
        assertEquals(40_000_000L, resumen.objetivo)
        assertEquals("3 metas", resumen.rotuloDeCantidad)
        assertEquals(setOf("acc_ahorros"), resumen.cuentasCompartidas)
    }
}
