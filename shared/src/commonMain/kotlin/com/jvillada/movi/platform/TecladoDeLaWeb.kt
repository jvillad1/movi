package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * # El teclado en pantalla de la web en el teléfono (vuelta 117)
 *
 * En Android y en iOS el teclado llega como el inset `WindowInsets.ime`, y la raíz de `App.kt` lo
 * descuenta con `imePadding`. **En la web no llega nada**: Compose dibuja en un `<canvas>` del alto de
 * la ventana, y el navegador del teléfono abre el teclado ENCIMA (Safari de iOS siempre; Chrome de
 * Android desde la versión 108, que por defecto solo encoge el *visual viewport*). El campo de Movi AI,
 * que va al pie, quedaba tapado.
 *
 * Esto devuelve **el alto del teclado en la web**: lo que el *visual viewport* perdió por abajo frente
 * a la ventana (`innerHeight − visualViewport.height − offsetTop`), solo con el zoom en 1 (con los
 * dedos el *visual viewport* también encoge, y eso no es el teclado). La raíz lo suma al `imePadding`.
 * En Android y en iOS, cero: allá manda el inset.
 *
 * No cuenta dos veces con el `interactive-widget=resizes-content` del `index.html` (vuelta 115): donde
 * el navegador lo respeta (Chrome de Android) la ventana misma encoge, `innerHeight` baja con el
 * teclado y el hueco da cero; donde no (Safari de iOS), el hueco es el teclado.
 */
@Composable
expect fun altoDelTecladoDeLaWeb(): Dp
