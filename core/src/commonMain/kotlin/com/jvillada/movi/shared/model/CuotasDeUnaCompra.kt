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

/** Las cuotas que la hoja ofrece de una: `1 · 3 · 6 · 12 · 24 · 36` (y «Otra»). */
val CUOTAS_FRECUENTES: List<Int> = listOf(1, 3, 6, 12, 24, 36)

/**
 * El tope de cuotas que se acepta. Los bancos de acá difieren hasta 36 (algunas tarjetas, 48); 72 deja
 * aire sin dejar pasar un número sin sentido. Lo mismo valida `POST /api/events`.
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
