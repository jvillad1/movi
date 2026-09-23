package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * # Mandar un enlace a otra persona, con lo que ofrezca cada plataforma
 *
 * La pantalla «Compartir» necesita dos gestos: **compartir** (que el enlace llegue a Caro por
 * WhatsApp, correo, lo que sea) y **copiar** (tenerlo en el portapapeles para pegarlo donde uno
 * quiera). Lo que cada plataforma puede hacer es distinto:
 *
 * - **Android** tiene la hoja de compartir del sistema (`Intent.ACTION_SEND`): el gesto natural en
 *   un teléfono, y el que el dueño va a usar casi siempre. Ahí [abreLaHojaDelSistema] es `true` y la
 *   pantalla ofrece los dos botones.
 * - **La web** se usa sobre todo en el computador, donde no hay hoja de compartir que valga: el
 *   gesto es copiar y pegar. [compartir] copia, y la pantalla muestra un solo botón.
 * - **iOS**, lo mínimo: copiar al portapapeles. Una hoja de compartir de verdad
 *   (`UIActivityViewController`) necesita el controlador de vista de arriba, y hoy nadie usa Movi
 *   en un iPhone; cuando haga falta, va en el `actual` de iOS sin tocar la pantalla.
 */
interface HojaDeCompartir {
    /** `true` si [compartir] abre la hoja del sistema; `false` si es lo mismo que [copiar]. */
    val abreLaHojaDelSistema: Boolean

    /** Manda [texto] (el mensaje con el enlace adentro) por lo que ofrezca la plataforma. */
    fun compartir(texto: String)

    /** Deja [texto] en el portapapeles. */
    fun copiar(texto: String)
}

@Composable
internal expect fun hojaDeCompartirDeLaPlataforma(): HojaDeCompartir
