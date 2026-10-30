package com.jvillada.movi.ui.credits

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.components.RELLENO_DE_DOS_COLUMNAS
import com.jvillada.movi.ui.components.cabenDosColumnasIguales

/**
 * # Créditos en dos columnas (Ola W3)
 *
 * En pantalla ancha, **dos cosas en dos columnas** (vuelta 81): a la izquierda la tarjeta de «Deuda
 * total» (no se parte: es UNA cifra con su desglose) y «Cómo salir de tus deudas»; a la derecha las
 * deudas, Préstamos y debajo Tarjetas. Hasta la vuelta 80 el resumen iba arriba a lo ancho y sus cifras
 * quedaban a ~1.000 px de su rótulo.
 *
 * Sin deudas (el vacío que enseña de la Ola I), con una sola (quedaría sola a la izquierda) o sin
 * poder leerlas, la pantalla va en una sola columna de lectura, como en una ventana angosta ([hayDeudasParaDosColumnas]).
 *
 * ### La cuenta, desde la ventana (con el rail, SIEMPRE)
 *
 * Créditos es un `Disposicion.Tablero`: 840 dp de tope en mediano, 1.280 en escritorio. Panel =
 * ventana − rail (80 / 216), topado ahí (`anchoDelPanelEnLaCascara`). Cada columna mide
 * (panel − 48) / 2 ([RELLENO_DE_DOS_COLUMNAS]) y tiene que llegar a [ANCHO_MINIMO_DE_COLUMNA_DE_CREDITOS]:
 *
 * | Ventana | Rail | Panel | Columna | Resultado |
 * |---------|------|-------|---------|-----------|
 * | 768     | 80   | 688   | 320     | una       |
 * | 999     | 80   | 840   | 396     | una (tope mediano) |
 * | 1024    | 216  | 808   | 380     | una       |
 * | 1104    | 216  | 888   | 420     | **dos** (el borde) |
 * | 1280    | 216  | 1064  | 508     | **dos**   |
 * | 1440    | 216  | 1224  | 588     | **dos**   |
 * | 1920    | 216  | 1280 (tope) | 616 | **dos** |
 */

/**
 * Lo mínimo que mide cada columna: 420 dp. Una tarjeta de préstamo lleva nombre, tasa y lápiz en un
 * renglón, el saldo con su avance, la cuota, el plazo, la línea del interés y hasta tres acciones;
 * en el teléfono (~360) ya va justa, y partirla en columnas más angostas que un teléfono grande
 * (~412) no gana nada. El umbral (888 de panel) queda además por encima del tope mediano (840) y de
 * lo que deja una ventana de 1024 con el rail (808): en los dos, una columna.
 */
val ANCHO_MINIMO_DE_COLUMNA_DE_CREDITOS: Dp = 420.dp

/** El panel mínimo para dos columnas: 2 × 420 + 48 = 888 dp (una ventana de 1.104 en escritorio). */
val UMBRAL_DE_CREDITOS_EN_DOS_COLUMNAS: Dp = ANCHO_MINIMO_DE_COLUMNA_DE_CREDITOS * 2 + RELLENO_DE_DOS_COLUMNAS

/** Si en un panel de [anchoDelPanel] (ya sin el rail) caben las dos columnas de Créditos. */
fun creditosEnDosColumnas(anchoDelPanel: Dp): Boolean =
    cabenDosColumnasIguales(anchoDelPanel, ANCHO_MINIMO_DE_COLUMNA_DE_CREDITOS)

/**
 * Si hay algo que repartir en dos columnas: las deudas, o su esqueleto mientras cargan. **No** con
 * las dos listas vacías ([sinDeudas], el mismo criterio de la Ola I que decide la tarjeta de «Deuda
 * total»: sin deudas no hay resumen, y el vacío que enseña va solo), ni cuando no se pudo leer
 * ([noSeLeyo]: queda el aviso con «Reintentar»), **ni con una sola deuda** ([cuantasDeudas] < 2): esa
 * tarjeta quedaría sola a la izquierda con la columna derecha vacía al lado. En esos casos, una
 * columna de lectura. Mientras carga no se sabe cuántas hay: el esqueleto va en dos, que es la forma
 * de quien tiene deudas.
 */
internal fun hayDeudasParaDosColumnas(cargando: Boolean, sinDeudas: Boolean, noSeLeyo: Boolean, cuantasDeudas: Int): Boolean =
    !noSeLeyo && (cargando || (!sinDeudas && cuantasDeudas >= 2))

/**
 * Las dos columnas, para medirlas en una prueba. Vuelta 81: la izquierda lleva el resumen («Deuda total»
 * y «Cómo salir de tus deudas») y la derecha las deudas, Préstamos y debajo Tarjetas.
 */
const val TAG_COLUMNA_IZQUIERDA_DE_CREDITOS: String = "creditos-columna-izquierda"
const val TAG_COLUMNA_DERECHA_DE_CREDITOS: String = "creditos-columna-derecha"
