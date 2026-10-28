package com.jvillada.movi.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.jvillada.movi.platform.elPunteroPrincipalEsFino

/**
 * **¿Se está frente a un computador?** (vuelta 34): el puntero principal es un mouse o un trackpad
 * (ver [elPunteroPrincipalEsFino]). Con `true`, una hoja que dibuja un teclado numérico en pantalla
 * —«Agregar»— no lo dibuja: los dígitos llegan del teclado físico, por el mismo camino que los
 * botones.
 *
 * **Por qué esta señal y no la clase de ancho.** [LocalWindowWidthClass] no dice si hay tacto: una
 * tablet es mediana o expandida y se toca (ahí el teclado en pantalla se queda), y una ventana de
 * escritorio angosta es compacta y tiene un teclado al lado. [LocalHayTecladoFisico] tampoco
 * sirve: es `!isAndroid`, o sea que también es cierto en un iPhone.
 *
 * Un `CompositionLocal` para que una prueba —que corre en Android, donde vale `false`— lo pueda
 * encender. El valor por defecto sale de la plataforma, así que la app no tiene que proveerlo.
 */
val LocalPunteroFino = staticCompositionLocalOf { elPunteroPrincipalEsFino() }
