package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * **Los íconos del reloj, la señal y la batería, pintados del color que se vea.**
 *
 * No los dibuja Movi: los dibuja el sistema operativo, encima de la app, y por defecto los deja
 * claros porque Movi siempre fue oscura. La primera vez que se encendió el tema claro en el
 * emulador quedaron **blancos sobre gris claro**, o sea invisibles. Es la clase de detalle que no
 * aparece en ninguna prueba y que se ve de inmediato al mirar la pantalla.
 *
 * Va como `expect` porque quién manda sobre esa barra es distinto en cada plataforma, y en dos de
 * las tres directamente no manda nadie: en la web no hay barra de estado, y en iOS la controla el
 * `UIViewController` desde Swift, no desde acá.
 *
 * @param oscuro el tema puesto. `true` → íconos claros (lo de siempre); `false` → íconos oscuros.
 */
@Composable
expect fun AjustarBarrasDelSistema(oscuro: Boolean)
