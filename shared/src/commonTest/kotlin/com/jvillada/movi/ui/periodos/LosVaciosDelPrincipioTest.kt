package com.jvillada.movi.ui.periodos

import com.jvillada.movi.shared.model.ResumenDePeriodo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * # Los meses vacíos, en una sola fila (vuelta de diseño 5, generalizada en la 7)
 *
 * [piezasDeLosPeriodos] solo decide cómo se pinta la lista: los vacíos que quedan antes del primer
 * período con movimientos se juntan al final; una racha de dos o más en el medio se junta en su
 * lugar; un vacío suelto entre dos con movimientos sigue siendo tarjeta; ninguno desaparece.
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

    private fun tarjeta(r: ResumenDePeriodo) = PiezaDePeriodos.Tarjeta(r)

    /** Las piezas aplanadas: la lista de períodos que pintan, en orden. */
    private fun aplanadas(piezas: List<PiezaDePeriodos>): List<ResumenDePeriodo> = piezas.flatMap {
        when (it) {
            is PiezaDePeriodos.Tarjeta -> listOf(it.resumen)
            is PiezaDePeriodos.Vacios -> it.periodos
        }
    }

    @Test
    fun `los vacios de antes del primero con movimientos van en una fila al final, y el del medio se queda`() {
        val piezas = piezasDeLosPeriodos(listOf(octubre, septiembre, agosto, julio, junio))
        assertEquals(
            listOf(
                tarjeta(octubre),
                tarjeta(septiembre),
                tarjeta(agosto),
                PiezaDePeriodos.Vacios(listOf(julio, junio), delPrincipio = true, texto = "Antes de agosto 2026 no hay movimientos"),
            ),
            piezas,
            "septiembre, vacío solo entre dos con movimientos, sigue siendo tarjeta",
        )
    }

    @Test
    fun `una racha de vacios en el medio va en una fila en su lugar`() {
        // La demo: movimientos en agosto y septiembre de 2025, nada de octubre de 2025 a julio de 2026.
        val nombres = listOf(
            "Julio 2026", "Junio 2026", "Mayo 2026", "Abril 2026", "Marzo 2026", "Febrero 2026",
            "Enero 2026", "Diciembre 2025", "Noviembre 2025", "Octubre 2025",
        )
        val vacios = nombres.mapIndexed { i, n -> periodo("v$i", n) }
        val septiembre25 = periodo("2025-09", "Septiembre 2025", salidas = 90_000)
        val agosto25 = periodo("2025-08", "Agosto 2025", entradas = 10_000)
        val agosto26 = agosto
        val todos = listOf(octubre, septiembre, agosto26) + vacios + listOf(septiembre25, agosto25)
        val piezas = piezasDeLosPeriodos(todos)
        assertEquals(
            listOf(
                tarjeta(octubre),
                tarjeta(septiembre),
                tarjeta(agosto26),
                PiezaDePeriodos.Vacios(vacios, delPrincipio = false, texto = "Octubre 2025 a julio 2026 · sin movimientos"),
                tarjeta(septiembre25),
                tarjeta(agosto25),
            ),
            piezas,
        )
        assertEquals(todos, aplanadas(piezas), "ningún período se pierde ni cambia de lugar")
    }

    @Test
    fun `racha en el medio y al principio a la vez`() {
        val mayo = periodo("2026-05", "Mayo 2026", salidas = 10_000)
        val abril = periodo("2026-04", "Abril 2026")
        val marzo = periodo("2026-03", "Marzo 2026")
        val todos = listOf(octubre, septiembre, agosto, julio, junio, mayo, abril, marzo)
        val piezas = piezasDeLosPeriodos(todos)
        assertEquals(
            listOf(
                tarjeta(octubre),
                tarjeta(septiembre),
                tarjeta(agosto),
                PiezaDePeriodos.Vacios(listOf(julio, junio), delPrincipio = false, texto = "Junio a julio 2026 · sin movimientos"),
                tarjeta(mayo),
                PiezaDePeriodos.Vacios(listOf(abril, marzo), delPrincipio = true, texto = "Antes de mayo 2026 no hay movimientos"),
            ),
            piezas,
        )
        assertEquals(todos, aplanadas(piezas))
    }

    @Test
    fun `sin vacios no hay nada aparte`() {
        val piezas = piezasDeLosPeriodos(listOf(octubre, agosto))
        assertEquals(listOf(tarjeta(octubre), tarjeta(agosto)), piezas)
    }

    @Test
    fun `el en curso es tarjeta aunque este vacio, y su racha no lo arrastra`() {
        val vacioEnCurso = octubre.copy(entradas = 0, salidas = 0)
        assertEquals(
            listOf(tarjeta(vacioEnCurso), PiezaDePeriodos.Vacios(listOf(septiembre), delPrincipio = true, texto = "Antes de octubre 2026 no hay movimientos")),
            piezasDeLosPeriodos(listOf(vacioEnCurso, septiembre)),
        )
        // En curso vacío, dos vacíos, y uno con movimientos: los dos del medio se juntan; el en curso no.
        assertEquals(
            listOf(
                tarjeta(vacioEnCurso),
                PiezaDePeriodos.Vacios(listOf(septiembre, julio), delPrincipio = false, texto = "Julio a septiembre 2026 · sin movimientos"),
                tarjeta(agosto),
            ),
            piezasDeLosPeriodos(listOf(vacioEnCurso, septiembre, julio, agosto)),
        )
        assertEquals(emptyList(), piezasDeLosPeriodos(emptyList()))
    }

    @Test
    fun `los textos de las filas`() {
        assertEquals("Antes de agosto 2026 no hay movimientos", textoDeLosVaciosDelPrincipio(agosto))
        assertEquals("Junio a julio 2026 · sin movimientos", textoDeLaRachaDeVacios(listOf(julio, junio)))
        assertEquals(
            "Diciembre 2025 a enero 2026 · sin movimientos",
            textoDeLaRachaDeVacios(listOf(periodo("a", "Enero 2026"), periodo("b", "Diciembre 2025"))),
        )
    }
}
