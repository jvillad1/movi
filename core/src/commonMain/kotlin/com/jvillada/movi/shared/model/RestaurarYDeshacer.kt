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
    /**
     * Por qué **no** se restaura ([porQueNoSeRestaura]), o `null` si se puede. Lo pone el server: la app
     * no ofrece «Restaurar» y dice esta frase.
     */
    val porQueNo: String? = null,
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

/** El motivo de las anulaciones que hace deshacer el importe de un extracto (`StatementRoutes`). */
const val MOTIVO_DEL_EXTRACTO_DESHECHO: String = "Se deshizo el importe del extracto"

/** El motivo con que se anula el original al dividirlo en categorías (`repartirUnMovimiento`). */
const val MOTIVO_DEL_REPARTIDO_EN_PARTES: String = "Repartido en partes"

const val NO_SE_RESTAURA_UN_AJUSTE: String =
    "Un ajuste de saldo no se restaura: si el saldo no cuadra, cuádralo otra vez en Cuadre de saldos."
const val NO_SE_RESTAURA_LO_DEL_EXTRACTO: String =
    "Se anuló al deshacer el importe de un extracto: para recuperarlo, vuelve a importar el extracto."
const val NO_SE_RESTAURA_UNA_PARTE_DESHECHA: String =
    "Era una parte de una división que se deshizo: la compra ya cuenta entera."
const val NO_SE_RESTAURA_LO_DIVIDIDO: String =
    "Se dividió en partes y sus partes ya cuentan: restaurarlo lo contaría dos veces."

/**
 * **Vuelta 67 (revisión) · lo que el sistema anuló no se restaura**: devolverlo movería plata vieja o la
 * contaría dos veces. Un ajuste de saldo (el saldo ya se cuadró después), lo que se anuló al deshacer un
 * extracto, una parte de una división que se deshizo ([MOTIVO_DEL_REPARTO_DESHECHO], o una cuya compra
 * ya volvió a ser una, [suCompraVolvioAUna]) y un original dividido cuyas partes no se pueden deshacer
 * ([MOTIVO_DEL_REPARTIDO_EN_PARTES] sin partes vivas con `repartoDe`: lo de antes de esta vuelta).
 * `null` si se restaura. Pura.
 */
fun porQueNoSeRestaura(
    evento: FinancialEvent,
    motivo: String?,
    tienePartesVivas: Boolean,
    suCompraVolvioAUna: Boolean,
): String? = when {
    evento.category == ADJUSTMENT_CATEGORY -> NO_SE_RESTAURA_UN_AJUSTE
    motivo == MOTIVO_DEL_EXTRACTO_DESHECHO -> NO_SE_RESTAURA_LO_DEL_EXTRACTO
    motivo == MOTIVO_DEL_REPARTO_DESHECHO || suCompraVolvioAUna -> NO_SE_RESTAURA_UNA_PARTE_DESHECHA
    motivo == MOTIVO_DEL_REPARTIDO_EN_PARTES && !tienePartesVivas -> NO_SE_RESTAURA_LO_DIVIDIDO
    else -> null
}
