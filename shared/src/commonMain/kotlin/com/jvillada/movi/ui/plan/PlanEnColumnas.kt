package com.jvillada.movi.ui.plan

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * # Plan en dos columnas (Ola W3)
 *
 * En pantalla ancha, a la izquierda una columna fija de [ANCHO_DE_LA_COLUMNA_DEL_DISPONIBLE] con lo
 * que contesta «¿cuánto puedo gastar?»: la línea del período, la tarjeta del disponible y la puerta a
 * «Tus períodos», con su propio scroll. A la derecha, con todo lo que sobra, lo que contesta «¿qué me
 * falta pagar?»: el selector Pagos del mes / Presupuestos y la lista de siempre (perezosa: el tablero
 * y los presupuestos son extensiones de `LazyListScope`).
 *
 * ### La cuenta, desde la ventana (con el rail, SIEMPRE)
 *
 * Plan es un `Disposicion.Tablero`: 840 dp de tope en mediano, 1.280 en escritorio. Panel = ventana −
 * rail (80 / 216), topado ahí (`anchoDelPanelEnLaCascara`). La izquierda mide 400 fijos (con sus
 * rellenos de 16 adentro); la derecha, el resto, y tiene que llegar a [ANCHO_MINIMO_DE_LOS_PAGOS]:
 *
 * | Ventana | Rail | Panel | Derecha | Resultado |
 * |---------|------|-------|---------|-----------|
 * | 768     | 80   | 688   | 288     | una       |
 * | 999     | 80   | 840   | 440     | una (tope mediano) |
 * | 1024    | 216  | 808   | 408     | una       |
 * | 1076    | 216  | 860   | 460     | **dos** (el borde) |
 * | 1280    | 216  | 1064  | 664     | **dos**   |
 * | 1440    | 216  | 1224  | 824     | **dos**   |
 * | 1920    | 216  | 1280 (tope) | 880 | **dos** |
 */

/**
 * El ancho fijo de la columna del disponible: 400 dp, lo que mide la tarjeta del disponible en un
 * teléfono grande. Más ancha no dice nada más —son tres cifras y una barra— y le quita lugar a la
 * lista de la derecha.
 */
val ANCHO_DE_LA_COLUMNA_DEL_DISPONIBLE: Dp = 400.dp

/**
 * Lo mínimo que mide la columna de los pagos: 460 dp, más que la del disponible, porque es la que
 * lleva la lista larga (nombre, cuenta, fecha, monto y las acciones de cada pago). Deja el umbral
 * (860 de panel) por encima del tope de una ventana mediana (840) y de lo que queda a 1024 con el rail
 * (808): en los dos, una columna.
 */
val ANCHO_MINIMO_DE_LOS_PAGOS: Dp = 460.dp

/** El panel mínimo para dos columnas: 400 + 460 = 860 dp (una ventana de 1.076 en escritorio). */
val UMBRAL_DE_PLAN_EN_DOS_COLUMNAS: Dp = ANCHO_DE_LA_COLUMNA_DEL_DISPONIBLE + ANCHO_MINIMO_DE_LOS_PAGOS

/** Si Plan, en un panel de [anchoDelPanel] (ya sin el rail), va en dos columnas. */
fun planEnDosColumnas(anchoDelPanel: Dp): Boolean = anchoDelPanel >= UMBRAL_DE_PLAN_EN_DOS_COLUMNAS

/** La columna del disponible (izquierda) y la de los pagos (derecha), para medirlas en una prueba. */
const val TAG_COLUMNA_DEL_DISPONIBLE: String = "plan-columna-del-disponible"
const val TAG_COLUMNA_DE_LOS_PAGOS: String = "plan-columna-de-los-pagos"
