package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Vuelta 94 · «¿Ya bajaste el extracto de Nu de octubre?»: antes de N días nada; después, la
 * propuesta; con un extracto subido de ese corte, nada; descartado, nada; sin corte, nada.
 */
class ExtractoPorSubirTest {

    private fun nu(
        hoy: LocalDate,
        diaDeCorte: Int? = 12,
        subidos: List<LocalDate> = emptyList(),
        descartadas: Set<String> = emptySet(),
        conActividad: (LocalDate) -> Boolean = { true },
    ) = extractoPorSubir("acc_nu", "Tarjeta Nu", "Nu", diaDeCorte, hoy, subidos, descartadas, conActividad)

    @Test
    fun `antes de los tres dias despues del corte no se pide nada`() {
        assertNull(nu(LocalDate(2026, 10, 12)))
        assertNull(nu(LocalDate(2026, 10, 14)))
    }

    @Test
    fun `a los tres dias se pide el extracto de ese corte`() {
        val p = assertNotNull(nu(LocalDate(2026, 10, 15)))
        assertEquals("2026-10-12", p.corte)
        assertEquals("extracto:acc_nu:2026-10-12", p.huella)
        assertEquals("¿Ya bajaste el extracto de Nu de octubre?", preguntaDelExtractoPorSubir(p))
        // Y se sigue pidiendo hasta el corte siguiente.
        assertEquals("2026-10-12", nu(LocalDate(2026, 11, 11))?.corte)
        assertNull(nu(LocalDate(2026, 11, 12)))
    }

    @Test
    fun `con un extracto subido desde el corte no se pide`() {
        assertNull(nu(LocalDate(2026, 10, 20), subidos = listOf(LocalDate(2026, 10, 13))))
        assertNull(nu(LocalDate(2026, 10, 20), subidos = listOf(LocalDate(2026, 10, 12))))
        // El del mes anterior no cuenta para este corte.
        assertNotNull(nu(LocalDate(2026, 10, 20), subidos = listOf(LocalDate(2026, 9, 14))))
    }

    @Test
    fun `descartado ese corte no se pide, y el siguiente si`() {
        val ya = setOf(huellaDelExtractoPorSubir("acc_nu", "2026-10-12"))
        assertNull(nu(LocalDate(2026, 10, 20), descartadas = ya))
        assertEquals("2026-11-12", nu(LocalDate(2026, 11, 16), descartadas = ya)?.corte)
    }

    @Test
    fun `sin dia de corte cargado no se pide nada`() {
        assertNull(nu(LocalDate(2026, 10, 20), diaDeCorte = null))
        assertNull(nu(LocalDate(2026, 10, 20), diaDeCorte = 0))
    }

    @Test
    fun `una tarjeta sin movimientos ni deuda en ese corte no pide extracto`() {
        assertNull(nu(LocalDate(2026, 10, 20), conActividad = { false }))
        var preguntado: LocalDate? = null
        nu(LocalDate(2026, 10, 20), conActividad = { preguntado = it; true })
        assertEquals(LocalDate(2026, 10, 12), preguntado)
    }

    @Test
    fun `el corte del 31 en un mes corto cae el ultimo dia`() {
        assertEquals("2026-09-30", nu(LocalDate(2026, 10, 5), diaDeCorte = 31)?.corte)
    }

    @Test
    fun `dos tarjetas del mismo banco se nombran por la cuenta`() {
        val master = ExtractoPorSubir("a", "Master Black 3684", "Bancolombia", "2026-10-20")
        val amex = ExtractoPorSubir("b", "AMEX 1234", "Bancolombia", "2026-10-20")
        assertEquals("¿Ya bajaste el extracto de Master Black 3684 de octubre?", preguntaDelExtractoPorSubir(master, listOf(master, amex)))
        assertEquals("¿Ya bajaste el extracto de Bancolombia de octubre?", preguntaDelExtractoPorSubir(master))
    }
}
