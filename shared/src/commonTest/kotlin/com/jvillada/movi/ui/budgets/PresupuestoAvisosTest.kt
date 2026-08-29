package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.OPENING_CATEGORY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresupuestoAvisosTest {

    /** Los datos reales del dueño el día que reportó esto. */
    private val gastoDelMes = mapOf(
        "Comida" to 2_113_575L,
        "Crédito" to 5_680_000L,
        "Gimnasio" to 180_000L,
        "Fútbol" to 121_210L,
        "Hija" to 50_000L,
    )

    private fun plata(v: Long) = "$$v"

    /**
     * El caso que originó todo: creó un presupuesto en «Mercado», que es la **descripción** de su
     * gasto del súper y no su categoría. Iba a decir «$0 gastado» para siempre.
     */
    @Test
    fun una_categoria_sin_gastos_avisa_y_sugiere_las_que_si_tienen() {
        val aviso = avisoDeCategoria("Mercado", gastoDelMes, ::plata)!!

        assertTrue(aviso.esAdvertencia, "va como advertencia, no como dato")
        assertTrue(aviso.texto.contains("No tienes gastos en \"Mercado\""))
        assertTrue(aviso.texto.contains("no vigilaría nada"), "dice la consecuencia, no solo el hecho")
        assertEquals(listOf("Crédito", "Comida", "Gimnasio"), aviso.sugerencias, "de mayor a menor")
    }

    /** Con gasto, el aviso es informativo: es lo que le habría mostrado que su límite ya estaba consumido. */
    @Test
    fun una_categoria_con_gastos_muestra_cuanto_lleva() {
        val aviso = avisoDeCategoria("Comida", gastoDelMes, ::plata)!!

        assertEquals("Ya llevas \$2113575 gastados en \"Comida\" este mes.", aviso.texto)
        assertTrue(!aviso.esAdvertencia)
        assertTrue(aviso.sugerencias.isEmpty())
    }

    /** Un dueño nuevo no tiene con qué comparar: no se le ofrece una lista vacía ni se le advierte. */
    @Test
    fun sin_ningun_gasto_no_se_sugiere_nada() {
        val aviso = avisoDeCategoria("Mercado", emptyMap(), ::plata)!!

        assertTrue(!aviso.esAdvertencia)
        assertTrue(aviso.sugerencias.isEmpty())
    }

    /** Mientras el campo está vacío no hay nada que decir. */
    @Test
    fun sin_nada_escrito_no_hay_aviso() {
        assertNull(avisoDeCategoria("", gastoDelMes, ::plata))
        assertNull(avisoDeCategoria("   ", gastoDelMes, ::plata))
    }

    /** Las reservadas las gobierna Movi: avisar ahí sería ruido sobre algo que no se presupuesta. */
    @Test
    fun las_categorias_reservadas_no_generan_aviso() {
        assertNull(avisoDeCategoria(OPENING_CATEGORY, gastoDelMes, ::plata))
    }

    /** Los espacios sobrantes no pueden convertir una categoría conocida en una desconocida. */
    @Test
    fun se_ignoran_los_espacios_al_cruzar() {
        val aviso = avisoDeCategoria("  Comida  ", gastoDelMes, ::plata)!!

        assertTrue(!aviso.esAdvertencia, "«  Comida  » es «Comida», no una categoría nueva")
    }

    /** No se ofrecen diez alternativas: el nombre buscado suele estar entre las de más gasto. */
    @Test
    fun se_ofrecen_a_lo_sumo_tres_sugerencias() {
        val aviso = avisoDeCategoria("Mercado", gastoDelMes, ::plata)!!

        assertEquals(3, aviso.sugerencias.size)
    }
}
