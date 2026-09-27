package com.jvillada.movi.ui.accounts

import androidx.compose.ui.unit.Dp
import com.jvillada.movi.shared.model.Account
import androidx.compose.ui.unit.dp
import com.jvillada.movi.ui.components.RELLENO_DE_DOS_COLUMNAS
import com.jvillada.movi.ui.components.cabenDosColumnasIguales

/**
 * # Patrimonio en dos columnas (Ola W3)
 *
 * En pantalla ancha, a la izquierda **el resumen** (la tarjeta del patrimonio neto con sus bienes y
 * sus deudas, y las puertas a Créditos, a Cuentas de otros, al cuadre y a los movimientos entre
 * cuentas); a la derecha **las cuentas** (Dinero, Inversión, Bienes). Las dos columnas miden lo
 * mismo.
 *
 * ### La cuenta, desde la ventana (con el rail, SIEMPRE)
 *
 * Patrimonio es un `Disposicion.Tablero`: 840 dp de tope en una ventana mediana, 1.280 en
 * escritorio. El panel es la ventana menos el rail (80 en mediano, 216 en escritorio), topado ahí
 * (`anchoDelPanelEnLaCascara`). Cada columna es (panel − 48) / 2 —16 de aire a cada lado y 16 entre
 * las dos ([RELLENO_DE_DOS_COLUMNAS])— y tiene que medir al menos [ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO]:
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
 *
 * Una columna, fuera del teléfono, va centrada en la columna de lectura de 720 (ver `PanelDeTablero`).
 */

/**
 * Lo mínimo que mide cada columna: 420 dp, algo más que un teléfono grande (~412). Con menos, la
 * tarjeta del patrimonio y las filas de cuenta —nombre, grupo, saldo y chevron— se leen igual que en
 * una sola columna, así que partir no gana nada. Y deja el umbral (888 de panel) por encima del tope
 * de una ventana mediana (840) y de lo que queda a 1024 con el rail (808): en los dos, una columna.
 */
val ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO: Dp = 420.dp

/** El panel mínimo para dos columnas: 2 × 420 + 48 = 888 dp (una ventana de 1.104 en escritorio). */
val UMBRAL_DE_PATRIMONIO_EN_DOS_COLUMNAS: Dp = ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO * 2 + RELLENO_DE_DOS_COLUMNAS

/** Si Patrimonio, en un panel de [anchoDelPanel] (ya sin el rail), va en dos columnas. */
fun patrimonioEnDosColumnas(anchoDelPanel: Dp): Boolean =
    cabenDosColumnasIguales(anchoDelPanel, ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO)

/** La columna del resumen (izquierda) y la de las cuentas (derecha), para medirlas en una prueba. */
const val TAG_COLUMNA_DEL_RESUMEN_DE_PATRIMONIO: String = "patrimonio-columna-del-resumen"
const val TAG_COLUMNA_DE_LAS_CUENTAS: String = "patrimonio-columna-de-las-cuentas"

/** Qué parte de Patrimonio pinta una lista: todo (una columna), o una de las dos columnas. */
internal enum class ParteDePatrimonio { Todo, Resumen, Cuentas }

/**
 * Si la columna de la derecha tendría algo: las cuentas, o su esqueleto mientras la primera lectura
 * sigue en vuelo. Sin ninguna cuenta (el vacío que enseña) o sin poder leerlas, la derecha quedaría
 * vacía al lado del resumen —un hueco, no un tablero—, así que Patrimonio va en una columna.
 */
internal fun hayCuentasParaLaDerecha(cuentas: List<Account>?, cargando: Boolean): Boolean =
    if (cuentas == null) cargando else cuentas.isNotEmpty()
