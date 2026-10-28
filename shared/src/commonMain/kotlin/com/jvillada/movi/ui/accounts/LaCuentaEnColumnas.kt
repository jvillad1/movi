package com.jvillada.movi.ui.accounts

import androidx.compose.ui.unit.Dp
import com.jvillada.movi.ui.components.cabenDosColumnasIguales

/**
 * # El detalle de una cuenta en dos columnas (vuelta 33)
 *
 * A 1.280 px el detalle de una cuenta (y el de una tarjeta o un préstamo, que es el mismo) era una
 * columna de lectura de 720 dp con aire a los lados. Ahora es un `Disposicion.Tablero`, como
 * Patrimonio: a la **izquierda** la cuenta —el saldo con su cuadre, la deuda con «Qué puedes hacer»,
 * para qué es esa plata, sus apartados y «Eliminar cuenta»—, y a la **derecha** sus movimientos, cada
 * columna con su scroll. Las cuentas del ancho son las de Patrimonio (columnas de 420 dp, dos desde
 * un panel de 888). En el teléfono no cambia nada: una lista, en el orden de siempre.
 */

/** Lo mínimo que mide cada columna: lo mismo que Patrimonio, de donde se abre. */
val ANCHO_MINIMO_DE_COLUMNA_DE_LA_CUENTA: Dp = ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO

/** Si en un panel de [anchoDelPanel] (ya sin el rail) el detalle de una cuenta va en dos columnas. */
fun cuentaEnDosColumnas(anchoDelPanel: Dp): Boolean =
    cabenDosColumnasIguales(anchoDelPanel, ANCHO_MINIMO_DE_COLUMNA_DE_LA_CUENTA)

/** Las dos columnas del detalle de una cuenta, para medirlas en una prueba. */
const val TAG_COLUMNA_DE_LA_CUENTA: String = "cuenta-columna-de-la-cuenta"
const val TAG_COLUMNA_DE_LOS_MOVIMIENTOS_DE_LA_CUENTA: String = "cuenta-columna-de-los-movimientos"
