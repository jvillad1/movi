package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import com.jvillada.movi.platform.altoDelTecladoDeLaWeb

/**
 * **¿El teclado está tapando la pantalla?**
 *
 * Hace falta porque Movi dibuja de borde a borde (`enableEdgeToEdge` en `MainActivity`), y con eso
 * el `adjustResize` del manifiesto **deja de encoger la ventana**: Android manda el alto del
 * teclado como un *inset* y la app tiene que descontarlo ella misma. Si nadie lo descuenta, el
 * teclado se abre ENCIMA de lo que estás escribiendo — que es exactamente lo que pasaba en el chat
 * de Movi AI: *«el teclado se levanta pero la pantalla no se ajusta, entonces no puedo ver lo que
 * estoy escribiendo sin cerrar el teclado»*.
 *
 * El descuento en sí lo hace `Modifier.imePadding()` en la columna raíz de `App.kt`, que arregla
 * todas las pantallas de una (y, en la web del teléfono, el `padding` de `altoDelTecladoDeLaWeb`, vuelta
 * 117). Esto de acá es para lo otro: **decidir qué se esconde** mientras el
 * teclado está arriba.
 */
@Composable
fun elTecladoEstaALaVista(): Boolean = altoDelTecladoEnPixeles() > 0

/**
 * **Cuánto tapa el teclado, en píxeles** (vuelta 117): el inset del sistema (Android, iOS) o, en la web
 * del teléfono, el hueco que mide `altoDelTecladoDeLaWeb`. Cambia cuadro a cuadro mientras el teclado
 * sube: el chat lo usa para seguir mostrando el último mensaje mientras la lista encoge.
 */
@Composable
fun altoDelTecladoEnPixeles(): Int {
    val densidad = LocalDensity.current
    val web = with(densidad) { altoDelTecladoDeLaWeb().roundToPx() }
    return maxOf(WindowInsets.ime.getBottom(densidad), web)
}
