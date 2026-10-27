package com.jvillada.movi.ui.periodos

import com.jvillada.movi.shared.model.ResumenDePeriodo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Los meses vacíos del principio, en una sola fila (vuelta de diseño 5)
 *
 * [separarLosVaciosDelPrincipio] solo decide cómo se pinta la lista: los vacíos que quedan antes del
 * primer período con movimientos se juntan; los del medio no; ninguno desaparece.
 */
class LosVaciosDelPrincipioTest {

    private fun periodo(id: String, nombre: String, entradas: Long = 0, salidas: Long = 0, enCurso: Boolean = false) =
        ResumenDePeriodo(
            id = id, nombre = nombre, desde = "$id-01", hasta = "$id-28",
            enCurso = enCurso, entradas = entradas, salidas = salidas,
        )

    private val octubre = periodo("2026-10", "Octubre 2026", entradas = 5_000_000, salidas = 3_000_000, enCurso = true)
    private val septiembre = periodo("2026-09", "Septiembre 2026")
    private val agosto = periodo("2026-08", "Agosto 2026", salidas = 120_000)
    private val julio = periodo("2026-07", "Julio 2026")
    private val junio = periodo("2026-06", "Junio 2026")

    @Test
    fun `los vacios de antes del primero con movimientos van aparte, y el del medio se queda`() {
        val (tarjetas, vacios) = separarLosVaciosDelPrincipio(listOf(octubre, septiembre, agosto, julio, junio))
        assertEquals(listOf(octubre, septiembre, agosto), tarjetas, "septiembre, vacío entre dos con movimientos, sigue")
        assertEquals(listOf(julio, junio), vacios)
    }

    @Test
    fun `ningun periodo se pierde ni cambia`() {
        val todos = listOf(octubre, septiembre, agosto, julio, junio)
        val (tarjetas, vacios) = separarLosVaciosDelPrincipio(todos)
        assertEquals(todos, tarjetas + vacios)
    }

    @Test
    fun `sin vacios al principio no hay nada aparte`() {
        val (tarjetas, vacios) = separarLosVaciosDelPrincipio(listOf(octubre, septiembre, agosto))
        assertEquals(listOf(octubre, septiembre, agosto), tarjetas)
        assertEquals(emptyList(), vacios)
    }

    @Test
    fun `sin ningun periodo con movimientos el en curso sigue siendo tarjeta`() {
        val vacioEnCurso = octubre.copy(entradas = 0, salidas = 0)
        val (tarjetas, vacios) = separarLosVaciosDelPrincipio(listOf(vacioEnCurso, septiembre))
        assertEquals(listOf(vacioEnCurso), tarjetas)
        assertEquals(listOf(septiembre), vacios)
        assertEquals(Pair(emptyList<ResumenDePeriodo>(), emptyList<ResumenDePeriodo>()), separarLosVaciosDelPrincipio(emptyList()))
    }

    @Test
    fun `la fila nombra el primero con movimientos en minuscula`() {
        assertEquals("Antes de agosto 2026 no hay movimientos", textoDeLosVaciosDelPrincipio(agosto))
    }
}
