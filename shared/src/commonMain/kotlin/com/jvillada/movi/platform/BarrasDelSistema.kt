package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * **Los íconos del reloj, la señal y la batería, pintados del color que se vea.**
 *
 * No los dibuja Movi: los dibuja el sistema operativo, encima de la app. Desde la vuelta 29 (opción
 * 3) Movi es clara siempre, así que van **oscuros**: claros sobre el crema quedarían invisibles —lo
 * que pasó la primera vez que se encendió un tema claro en el emulador—.
 *
 * Va como `expect` porque quién manda sobre esa barra es distinto en cada plataforma, y en dos de
 * las tres directamente no manda nadie: en la web no hay barra de estado, y en iOS la controla el
 * `UIViewController` desde Swift, no desde acá.
 */
@Composable
expect fun AjustarBarrasDelSistema()
