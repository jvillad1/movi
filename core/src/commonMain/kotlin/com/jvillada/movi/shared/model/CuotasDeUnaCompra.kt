package com.jvillada.movi.shared.model

/*
 * # Vuelta 71 · «¿A cuántas cuotas?» al anotar una compra con tarjeta
 *
 * Hasta acá [FinancialEvent.cuotas] solo lo llenaba el importador de los movimientos de la tarjeta de
 * Bancolombia (vuelta 63). Ahora también Agregar: con una tarjeta de crédito elegida en «Gasto», la
 * hoja pregunta a cuántas cuotas fue la compra y `POST /api/events` lo guarda.
 *
 * **No cambia cómo cuenta nada** (regla de siempre, sin regla nueva): la compra cuenta entera el día que
 * se hizo en los gastos del período, y solo `extractoDeLaTarjeta` reparte la compra entre los cortes. Lo
 * que se agrega acá es el dato, no una forma nueva de contar.
 */

/**
 * Las cuotas que la hoja ofrece de una: `1 · 3 · 6 · 12 · 24 · 36 · 48 · 60` (y «Otra»).
 *
 * Vuelta 133: 48 y 60 entraron porque el papá del dueño difiere a 60, 48, 36, 24 o 12 meses y la fila
 * no se lo ofrecía. Las pastillas van en un renglón que se desliza (`FilaDeCuotas`): a 360 dp con letra
 * grande las últimas quedan a un deslizamiento, nunca cortadas.
 */
val CUOTAS_FRECUENTES: List<Int> = listOf(1, 3, 6, 12, 24, 36, 48, 60)

/**
 * El tope de cuotas que se acepta. Los bancos de acá difieren hasta 36 y algunas tarjetas hasta 48 o 60;
 * 72 deja aire sin dejar pasar un número sin sentido. Lo mismo validan `POST /api/events`,
 * `PUT /api/events/{id}/cuotas` y la confirmación de un aviso (vuelta 133).
 */
const val MAXIMO_DE_CUOTAS: Int = 72

/** ¿[cuotas] es un número de cuotas que se puede guardar? */
fun cuotasValidas(cuotas: Int): Boolean = cuotas in 1..MAXIMO_DE_CUOTAS

/** El rechazo de `POST /api/events` cuando llegan unas cuotas fuera de rango. */
const val CUOTAS_FUERA_DE_RANGO: String = "Las cuotas van de 1 a $MAXIMO_DE_CUOTAS."

/**
 * **Cuánto va por mes** de una compra de [monto] a [cuotas], sin intereses: la división entera, como la
 * de `extractoDeLaTarjeta` (el peso que sobra va a la última cuota). Con una cuota (o menos), el monto.
 */
fun cuotaDeLaCompra(monto: Long, cuotas: Int): Long = if (cuotas <= 1) monto else monto / cuotas

/**
 * Vuelta 133 · el cuerpo de `PUT /api/events/{id}/cuotas`: **a cuántas cuotas fue una compra con tarjeta
 * que ya está anotada** («A 12 cuotas · Cambiar» en el detalle). Solo las cuotas: no toca monto, fecha ni
 * categoría, y la compra sigue contando entera el día que se hizo (solo `extractoDeLaTarjeta` reparte).
 */
@kotlinx.serialization.Serializable
data class UpdateEventCuotasRequest(val cuotas: Int)

/** El rechazo (422) de unas cuotas sobre algo que no es un gasto de una tarjeta de crédito. */
const val CUOTAS_SOLO_EN_UNA_COMPRA_CON_TARJETA: String =
    "Las cuotas son de una compra con tarjeta de crédito."

/**
 * Vuelta 133 · **¿este movimiento puede llevar cuotas?** Un gasto suelto (no la pata de un traspaso ni de
 * un pago) de una tarjeta de crédito. La misma regla que `POST /api/events` (que las ignora fuera de
 * eso), `PUT /api/events/{id}/cuotas` (que las rechaza) y las pantallas (que solo ahí preguntan). Pura.
 */
fun llevaCuotas(evento: FinancialEvent, tipoDeLaCuenta: AccountType?): Boolean =
    evento.type == TransactionType.EXPENSE && tipoDeLaCuenta == AccountType.CREDIT_CARD && evento.transferId == null

/** «A 12 cuotas», «A 1 cuota», o «¿A cuántas cuotas?» sin saberlas (vuelta 133: el detalle y el aviso). */
fun rotuloDeLasCuotas(cuotas: Int?): String = when {
    cuotas == null -> "¿A cuántas cuotas?"
    cuotas == 1 -> "A 1 cuota"
    else -> "A $cuotas cuotas"
}
