package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # Vuelta 67 · lo anulado se restaura, y lo dividido se deshace
 *
 * Anular un movimiento deja una fila en `void_events` (el movimiento no se borra: deja de contar en
 * toda suma). Restaurar es **borrar esa marca**, nada más: el movimiento vuelve a contar como antes de
 * anularlo. Sin reglas de plata nuevas.
 *
 * - Un traspaso o una cuota son dos patas: anular una anula las dos, y restaurar una restaura las dos.
 * - Dividir un movimiento en categorías («Repartir en partes») lo anula y escribe las partes, cada una
 *   con [FinancialEvent.repartoDe]. Deshacerlo anula las partes y restaura el original: vuelve a ser un
 *   solo movimiento, con su aviso enlazado a él.
 */

/** **Un movimiento anulado**, para el filtro «Anulados» de Actividad (`GET /api/events/anulados`). */
@Serializable
data class MovimientoAnulado(
    val evento: FinancialEvent,
    /** El motivo que escribió el dueño al anular, si escribió uno. */
    val motivo: String? = null,
    /** Cuándo se anuló (epoch ms). */
    val anuladoEn: Long = 0L,
)

/**
 * **Una compra dividida en categorías** (`GET /api/events/{id}/reparto`): el movimiento original (anulado
 * mientras está dividido) y todas las patas de sus partes, vivas o no.
 */
@Serializable
data class RepartoDeUnMovimiento(
    val original: FinancialEvent,
    val partes: List<FinancialEvent>,
)

/** El motivo que queda en `void_events` en cada parte al deshacer el reparto. */
const val MOTIVO_DEL_REPARTO_DESHECHO: String = "Reparto deshecho"

/**
 * **Las partes que se ven** de un reparto: una por parte, la pata que sale de la cuenta del original (una
 * cuota de dos patas es una parte). Puras.
 */
fun partesQueSeVen(reparto: RepartoDeUnMovimiento): List<FinancialEvent> =
    reparto.partes.filter { it.accountId == reparto.original.accountId && it.type == TransactionType.EXPENSE }
