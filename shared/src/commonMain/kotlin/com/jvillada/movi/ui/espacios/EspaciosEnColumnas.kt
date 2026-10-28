package com.jvillada.movi.ui.espacios

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.accounts.ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.cabenDosColumnasIguales

/**
 * # Espacios en dos columnas (vuelta 33)
 *
 * A 1.280 px Espacios era una sola columna de ~690 dp con aire a los lados, y «Pedirle a Caro» un
 * botón de 656 dp. Ahora, como Patrimonio (Ola W3), es un `Disposicion.Tablero`, y con **un**
 * espacio a la vista se parte en dos:
 *
 * - **izquierda**: el espacio con su total, «Quién puso qué» y el saldo con «Pedirle a Caro» (lo que
 *   Movi propone saldar va arriba, como en una columna);
 * - **derecha**: «Gastos» con «Anotar lo que pagó Caro», «Lo que paga otra persona» y «Personas y
 *   comercios».
 *
 * Con varios espacios (la lista), sin ninguno (el vacío que enseña) o sin poder leerlos, una columna
 * de lectura centrada, como en una ventana angosta. Las cuentas del ancho son las de Patrimonio:
 * columnas de al menos 420 dp, o sea dos desde un panel de 888 (una ventana de 1.104 con el rail).
 * En el teléfono no cambia nada.
 */

/** Lo mínimo que mide cada columna: lo mismo que Patrimonio (el mismo tipo de filas y tarjetas). */
val ANCHO_MINIMO_DE_COLUMNA_DE_ESPACIOS: Dp = ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO

/** Si en un panel de [anchoDelPanel] (ya sin el rail) caben las dos columnas de un espacio. */
fun espacioEnDosColumnas(anchoDelPanel: Dp): Boolean =
    cabenDosColumnasIguales(anchoDelPanel, ANCHO_MINIMO_DE_COLUMNA_DE_ESPACIOS)

/** Las dos columnas de un espacio, para medirlas en una prueba. */
const val TAG_COLUMNA_IZQUIERDA_DEL_ESPACIO: String = "espacios:columna-izquierda"
const val TAG_COLUMNA_DERECHA_DEL_ESPACIO: String = "espacios:columna-derecha"

/**
 * El tope de un botón de ancho completo de Espacios: ninguno en el teléfono (llena su tarjeta, como
 * siempre) y [Medida.anchoMaximoDeBoton] fuera de él, en una o en dos columnas.
 */
@Composable
internal fun topeDelBoton(): Modifier =
    if (LocalWindowWidthClass.current == WindowWidthClass.Compact) Modifier else Modifier.widthIn(max = Medida.anchoMaximoDeBoton)
