package com.jvillada.movi.shared.model

/**
 * **Las categorías de lo fijo** (vuelta 76, en `:core` desde la vuelta 77): las de las reglas recurrentes de
 * gasto ([reglas]: las del dueño y las cuotas y tarjetas sintéticas), [CUOTA_CATEGORY] y las de
 * [saldanUnPago] (los movimientos que saldan un pago del período, donde se sabe).
 *
 * Una categoría de lo fijo que el período pasado, a la misma altura, no tenía gasto no es «nuevo» ni
 * «$X más»: la cuota o el fijo cayó antes este período (o el pasado se pagó un día después). La usan el
 * widget «En qué se va» y Actividad (`diferenciaDeLaCategoria`) y, desde la vuelta 77, la pregunta sugerida
 * «¿En qué se me está yendo más la plata?» de Movi AI: las dos dicen lo mismo de la misma categoría. Pura;
 * no cambia ninguna cifra, solo qué palabra va al lado.
 */
fun categoriasFijas(reglas: List<RecurringRule>, saldanUnPago: Collection<String> = emptyList()): Set<String> =
    reglas.filter { it.type == TransactionType.EXPENSE }.map { it.category }.toSet() + CUOTA_CATEGORY + saldanUnPago
