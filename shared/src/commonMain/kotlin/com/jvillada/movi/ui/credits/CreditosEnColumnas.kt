package com.jvillada.movi.ui.credits

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.components.RELLENO_DE_DOS_COLUMNAS
import com.jvillada.movi.ui.components.cabenDosColumnasIguales

/**
 * # Créditos en dos columnas (Ola W3)
 *
 * En pantalla ancha la tarjeta de «Deuda total» va arriba, a lo ancho (no se parte: es UNA cifra
 * con su desglose), y debajo **Préstamos y Tarjetas uno al lado del otro**. Con uno solo de los
 * dos grupos, sus tarjetas se reparten en las dos columnas bajo un solo título: una columna vacía
 * al lado de otra llena no es un tablero, es un hueco.
 *
 * Sin deudas (el vacío que enseña de la Ola I) o sin poder leerlas, la pantalla va en una sola
 * columna de lectura, como en una ventana angosta ([hayDeudasParaDosColumnas]).
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
 * ([noSeLeyo]: queda el aviso con «Reintentar»). En esos casos, una columna de lectura.
 */
internal fun hayDeudasParaDosColumnas(cargando: Boolean, sinDeudas: Boolean, noSeLeyo: Boolean): Boolean =
    !noSeLeyo && (cargando || !sinDeudas)

/** Las dos columnas debajo del resumen, para medirlas en una prueba. */
const val TAG_COLUMNA_IZQUIERDA_DE_CREDITOS: String = "creditos-columna-izquierda"
const val TAG_COLUMNA_DERECHA_DE_CREDITOS: String = "creditos-columna-derecha"

/**
 * Reparte [elementos] en dos columnas, alternando (el 1.º a la izquierda, el 2.º a la derecha…):
 * así las dos quedan con el mismo número de tarjetas, ± 1, y la lectura sigue el orden de la lista
 * de izquierda a derecha y de arriba abajo.
 */
internal fun <T> repartirEnDos(elementos: List<T>): Pair<List<T>, List<T>> =
    elementos.withIndex().partition { it.index % 2 == 0 }.let { (i, d) -> i.map { it.value } to d.map { it.value } }
