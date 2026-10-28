package com.jvillada.movi.ui.porrevisar

import androidx.compose.ui.unit.Dp
import com.jvillada.movi.ui.accounts.ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO
import com.jvillada.movi.ui.components.cabenDosColumnasIguales

/**
 * # «Por revisar» en dos columnas (vuelta 42)
 *
 * A 1.280 px la bandeja era una columna de lectura de 720 dp con mucho aire a los lados. Ahora es un
 * `Disposicion.Tablero`, como Patrimonio y el detalle de una cuenta: a la **izquierda** «Mensajes del
 * banco» (con «Confirmar los N que Movi entendió», que es de esos mensajes), y a la **derecha** lo
 * demás —«Subir comprobante o extracto», el aviso de la captura, lo que el banco cobró solo, «Entraron
 * solos» y los pagos de tarjeta—, cada columna con su scroll. Las cuentas del ancho son las de
 * Patrimonio (columnas de 420 dp, dos desde un panel de 888). Solo con mensajes del banco por revisar:
 * sin ellos la izquierda quedaría vacía, y la bandeja va en una columna. En el teléfono no cambia nada.
 */

/** Lo mínimo que mide cada columna: lo mismo que Patrimonio y que una cuenta. */
val ANCHO_MINIMO_DE_COLUMNA_DE_POR_REVISAR: Dp = ANCHO_MINIMO_DE_COLUMNA_DE_PATRIMONIO

/** Si en un panel de [anchoDelPanel] (ya sin el rail) «Por revisar» cabe en dos columnas. */
fun porRevisarEnDosColumnas(anchoDelPanel: Dp): Boolean =
    cabenDosColumnasIguales(anchoDelPanel, ANCHO_MINIMO_DE_COLUMNA_DE_POR_REVISAR)

/** Las dos columnas de «Por revisar», para medirlas en una prueba. */
const val TAG_COLUMNA_DE_LOS_MENSAJES: String = "por-revisar-columna-de-los-mensajes"
const val TAG_COLUMNA_DE_LO_DEMAS: String = "por-revisar-columna-de-lo-demas"
