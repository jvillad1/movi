package com.jvillada.movi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

/**
 * Cuánto tiempo puede pasar entre dos clics para que sigan siendo **un** doble clic.
 *
 * 500 ms es el valor por defecto del sistema operativo, tanto en macOS como en Windows, y encima
 * los dos lo dejan subir todavía más en sus opciones de accesibilidad. Compose usa 300, que es el
 * número de **Android** — donde el gesto es un toque con el dedo y no un clic con el mouse.
 */
const val DOBLE_CLIC_DEL_SISTEMA_MS: Long = 500L

/**
 * # El triple clic que no reemplazaba: 300 ms contra 500
 *
 * **El defecto, reportado por el dueño:** «Bancolombia» + triple clic + escribir «Nequi» dejaba
 * **«BancolombiaNequi»** en vez de «Nequi». Se anotó como «se pierden teclas escribiendo en
 * ráfaga», y ese nombre mandó a buscar en el lugar equivocado durante dos olas: **no se pierde
 * ninguna tecla**. Están todas. Lo que no pasa es el reemplazo.
 *
 * ## Lo que de verdad ocurre, medido
 *
 * Compose decide si dos clics son un doble clic con `ViewConfiguration.doubleTapTimeoutMillis`,
 * que por defecto vale **300 ms**. El doble clic del sistema, en cambio, son **500**. En esa
 * franja de 200 ms el usuario hace un gesto que su propio sistema operativo llama «doble clic» y
 * Compose lo recibe como dos clics sueltos.
 *
 * Y dos clics sueltos sobre un campo de texto **no seleccionan nada**: solo mueven el cursor. Lo
 * que se escriba después se inserta ahí. Si el último clic cayó al final de «Bancolombia», el
 * resultado es exactamente «BancolombiaNequi» — el reporte, explicado sin que falte ni sobre una
 * tecla.
 *
 * Barrido sobre la app real en el navegador (Playwright, 60 fps, texto «Mesada de la hija», dos
 * repeticiones por celda; PALABRA = el doble clic seleccionó la palabra, LÍNEA = el triple clic
 * seleccionó todo, CURSOR = no seleccionó nada):
 *
 * ```
 *   pausa entre clics   doble clic   triple clic
 *        200 ms          PALABRA       LÍNEA
 *        260 ms          PALABRA       LÍNEA
 *        290 ms          PALABRA       LÍNEA
 *        310 ms          CURSOR        CURSOR      <- el borde, exacto
 *        340 ms          CURSOR        CURSOR
 *        400 ms          CURSOR        CURSOR
 * ```
 *
 * El corte cae entre 290 y 310, que es el valor por defecto de Compose. No es una carrera, no
 * depende de la velocidad de la máquina y no tiene nada que ver con escribir «en ráfaga»: es un
 * umbral fijo, y el gesto de una persona que no se apura cae del lado malo.
 *
 * ## Por qué se arregla acá y no aguantando a que lo arregle Compose
 *
 * `LocalViewConfiguration` está hecho justamente para esto, y el valor correcto depende de la
 * plataforma: 300 ms es lo apropiado para un dedo en un teléfono, y por eso **esto se cablea solo
 * en la web** (`webApp`), donde el gesto se hace con un mouse. Android e iOS siguen con el número
 * de su sistema, que ahí es el bueno.
 *
 * Ojo con subirlo mucho más: el mismo número decide cuándo dos clics deliberados en el mismo lugar
 * dejan de ser dos cosas y pasan a ser un doble clic. 500 es lo que hace cualquier app nativa.
 */
class ViewConfigurationConDobleClicDelSistema(
    base: ViewConfiguration,
    private val dobleClicMs: Long = DOBLE_CLIC_DEL_SISTEMA_MS,
) : ViewConfiguration by base {
    override val doubleTapTimeoutMillis: Long get() = dobleClicMs
}

/**
 * Envuelve la app para que el doble y el triple clic usen el tiempo del sistema. Ver
 * [ViewConfigurationConDobleClicDelSistema], que trae la medición completa.
 */
@Composable
fun ConDobleClicDelSistema(contenido: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalViewConfiguration provides
            ViewConfigurationConDobleClicDelSistema(LocalViewConfiguration.current),
        content = contenido,
    )
}
