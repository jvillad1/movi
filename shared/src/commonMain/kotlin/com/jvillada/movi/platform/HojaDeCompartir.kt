package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

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
 * - **iOS** (vuelta de diseño 26) abre la hoja de compartir del sistema (`UIActivityViewController`
 *   sobre el controlador de vista de arriba); si no hay ninguno a la vista, copia.
 *
 * **Un mensaje para otra persona** («Pedirle a Caro», vuelta de diseño 26) va por [mandarMensaje]:
 * en Android y en iOS la hoja del sistema; en la web, `navigator.share` si el navegador lo tiene, y si
 * no se copia y la pantalla ofrece abrir WhatsApp ([ComoSalioElMensaje.COPIADO]).
 */
interface HojaDeCompartir {
    /** `true` si [compartir] abre la hoja del sistema; `false` si es lo mismo que [copiar]. */
    val abreLaHojaDelSistema: Boolean

    /** Manda [texto] (el mensaje con el enlace adentro) por lo que ofrezca la plataforma. */
    fun compartir(texto: String)

    /** Deja [texto] en el portapapeles. */
    fun copiar(texto: String)

    /**
     * Manda [texto], un mensaje para otra persona, por lo que ofrezca la plataforma, y dice cómo
     * salió. Por defecto es [compartir].
     */
    fun mandarMensaje(texto: String): ComoSalioElMensaje {
        compartir(texto)
        return if (abreLaHojaDelSistema) ComoSalioElMensaje.POR_LA_HOJA else ComoSalioElMensaje.COPIADO
    }
}

/** Cómo salió un mensaje de [HojaDeCompartir.mandarMensaje]. */
enum class ComoSalioElMensaje {
    /** Se abrió la hoja de compartir (del sistema o del navegador): la persona elige a dónde. */
    POR_LA_HOJA,

    /** No había hoja: el mensaje quedó en el portapapeles y la pantalla lo dice. */
    COPIADO,
}

@Composable
internal expect fun hojaDeCompartirDeLaPlataforma(): HojaDeCompartir

/**
 * Una hoja que reemplaza la de la plataforma: para las pruebas (en Robolectric la de Android abre un
 * `Intent` que nadie ve). `null`, lo de siempre.
 */
val LocalHojaDeCompartir = staticCompositionLocalOf<HojaDeCompartir?> { null }

/** La hoja de compartir: la de [LocalHojaDeCompartir] si hay una, o la de la plataforma. */
@Composable
internal fun hojaDeCompartir(): HojaDeCompartir = LocalHojaDeCompartir.current ?: hojaDeCompartirDeLaPlataforma()
