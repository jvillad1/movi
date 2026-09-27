package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.ui.dashboard.EstadoDeLaFila
import com.jvillada.movi.ui.dashboard.checklistDelPeriodo
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.pagosHechos
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.pendientesDePeriodosAnteriores
import com.jvillada.movi.ui.dashboard.yaPagadoEnElPeriodo
import com.jvillada.movi.ui.recurrentes.CasoDeOctubreDelDueno as Caso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **La lista del período, con el caso del dueño** (ver [CasoDeOctubreDelDueno]): Celular y Cotrafa
 * pagados en SEPTIEMBRE y pendientes en octubre; Crediágil pagado el 27 de septiembre.
 *
 * En el período de octubre, Celular y Cotrafa salen SOLO en «Falta por pagar», y en ninguna fila
 * dicen que ya se pagaron. Crediágil sale solo en «Ya pagaste». Cada regla, una vez.
 */
class UnaSolaListaDelPeriodoTest {

    private val lista = checklistDelPeriodo(Caso.upcoming, Caso.ocurrencias, Caso.octubre, Caso.ajustes)
    private val anteriores = pendientesDePeriodosAnteriores(Caso.upcoming, Caso.ocurrencias, Caso.octubre, Caso.ajustes)

    @Test
    fun celular_y_cotrafa_salen_solo_en_falta_por_pagar() {
        val falta = pagosPendientes(lista).map { it.nombre }
        val pagado = pagosHechos(lista).map { it.nombre }

        assertTrue("Celular" in falta && "Cotrafa" in falta, "lo de octubre todavía no se pagó: $falta")
        assertFalse("Celular" in pagado || "Cotrafa" in pagado, "lo pagado en septiembre no es de octubre: $pagado")

        for (nombre in listOf("Celular", "Cotrafa")) {
            val fila = lista.single { it.nombre == nombre }
            assertFalse(fila.pagado)
            assertEquals("vence el 22 de octubre", fechaDeLaFila(fila))
            assertNull(evidenciaDeLaFila(fila), "una fila que falta no dice con qué se pagó")
            assertEquals(EstadoDeLaFila.AUN_NO_VENCE, fila.estado, "no hay nada que preguntar ni que anotar todavía")
        }
    }

    @Test
    fun crediagil_sale_solo_en_ya_pagaste_y_dice_con_que_se_sabe() {
        val fila = pagosHechos(lista).single { it.nombre == "Crediágil" }
        assertEquals("pagado el 27 de septiembre", fechaDeLaFila(fila))
        assertEquals("Lo prueba un pago de \$1.204.064", evidenciaDeLaFila(fila))
        assertFalse(pagosPendientes(lista).any { it.nombre == "Crediágil" })
    }

    @Test
    fun cada_regla_aparece_una_sola_vez() {
        val nombres = lista.map { it.nombre } + anteriores.map { it.nombre }
        assertEquals(nombres.distinct(), nombres, "ninguna regla repetida: $nombres")
        assertEquals(
            setOf("Celular", "Cotrafa", "Crediágil", "Gimnasio Caro", "Mercado", "Internet"),
            nombres.toSet(),
        )
        assertTrue(anteriores.isEmpty(), "lo de septiembre que se pagó no se arrastra a octubre")
    }

    /**
     * Ningún texto de la lista dice «ya ocurrió» ni nombra otro mes como si fuera pagado: todo lo que
     * está, es de este período.
     */
    @Test
    fun ningun_texto_dice_ya_ocurrio() {
        val textos = lista.flatMap { listOfNotNull(fechaDeLaFila(it), evidenciaDeLaFila(it)) }
        assertTrue(textos.none { it.contains("ocurrió", ignoreCase = true) }, textos.toString())
    }

    @Test
    fun lo_vencido_va_primero_y_los_totales_son_la_suma_de_sus_filas() {
        assertEquals("Gimnasio Caro", pagosPendientes(lista).first().nombre)
        assertEquals("venció hace 2 días", fechaDeLaFila(pagosPendientes(lista).first()))
        assertEquals(180_000L + 53_000L + 410_000L, faltaPorPagar(lista))
        // Lo que de verdad salió: la cuota de Crediágil, el mercado que Movi emparejó y el sello a
        // mano del internet (sin movimiento: cuenta lo que dice la regla).
        assertEquals(1_204_064L + 2_000_000L + 120_000L, yaPagadoEnElPeriodo(lista))
    }

    /**
     * **Lo que era «Sin confirmar»**: la ocurrencia de SEPTIEMBRE de Coomeva quedó abierta. No es de
     * octubre, así que no entra en ningún grupo de este período: va aparte, diciendo de cuál es.
     */
    @Test
    fun lo_abierto_de_un_periodo_anterior_va_aparte_y_dice_de_cual() {
        val upcoming = Caso.upcoming + Caso.coomevaDeOctubre
        val ocurrencias = Caso.ocurrencias + Caso.coomevaDeSeptiembre

        val deOctubre = checklistDelPeriodo(upcoming, ocurrencias, Caso.octubre, Caso.ajustes)
        val deAntes = pendientesDePeriodosAnteriores(upcoming, ocurrencias, Caso.octubre, Caso.ajustes)

        val coomeva = deAntes.single()
        assertEquals("Coomeva", coomeva.nombre)
        assertEquals("2026-09", coomeva.periodoDelSello, "se sella el de septiembre, no el de octubre")
        assertEquals(EstadoDeLaFila.CON_DUDAS, coomeva.estado)
        assertEquals("Del período de septiembre", tituloDeLosAnteriores(deAntes))
        // La de octubre sigue siendo de octubre, sin pagar.
        assertFalse(deOctubre.single { it.nombre == "Coomeva" }.pagado)
        // Nunca con lo pagado: lo que un período anterior dejó abierto no es un «ya pagaste».
        assertFalse(pagosHechos(deOctubre).any { it.nombre == "Coomeva" })
    }

    /** Lo pagado de un período anterior no aparece en ningún lado: se ve en «Tus períodos». */
    @Test
    fun lo_pagado_de_un_periodo_anterior_no_es_pendiente_de_antes() {
        assertTrue(pendientesDePeriodosAnteriores(Caso.upcoming, Caso.ocurrencias, Caso.octubre, Caso.ajustes).isEmpty())
    }
}
