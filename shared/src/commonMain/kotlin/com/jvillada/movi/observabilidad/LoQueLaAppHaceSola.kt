package com.jvillada.movi.observabilidad

import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso

/**
 * **Lo que «Movi propone hoy» armó** (vuelta 104): una fila [EventoDeUso.PROPUESTAS_GENERADAS] por tipo
 * de propuesta (`PAGO`, `CUADRE`…) con cuántas cosas propone, solo cuando lo propuesto cambia en la
 * sesión ([Uso.cambio]): el Inicio se compone muchas veces y no puede anotar lo mismo en cada una.
 * Nunca la cifra ni el texto de una propuesta. Lo hace la app sola: no abre una sesión.
 */
fun anotarLasPropuestas(porTipo: Map<String, Int>) {
    val firma = porTipo.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
    if (!Uso.cambio("propuestas", firma)) return
    porTipo.forEach { (tipo, cuantas) ->
        if (!ClaveDeUso.PROPUESTA.admite(tipo)) return@forEach
        Uso.eventoDeFondo(
            EventoDeUso.PROPUESTAS_GENERADAS,
            mapOf(ClaveDeUso.PROPUESTA to tipo, ClaveDeUso.CUANTOS to cuantas.coerceIn(0, 999_999).toString()),
        )
    }
}
