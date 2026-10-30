package com.jvillada.movi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * # Un solo error por pantalla (vuelta 68)
 *
 * La tercera revisión (`r3-err-pagos.png`): con la API apagada, Pagos apilaba tres o cuatro tarjetas
 * «No pudimos…», cada una con su «Reintentar», más el aviso de abajo. Un servidor caído es **una**
 * noticia, no una por sección.
 *
 * Una pantalla que lo quiere pone [ErroresDeLaPantalla] en [LocalErroresDeLaPantalla] y pinta
 * [AvisoDeLaPantalla] arriba. Cada sección, en vez de su propia [NoSePudoLeer], llama
 * [NoSePudoLeerEnLaPantalla]: si hay quien junte los errores, **se anota y no pinta nada** (ni su
 * título: un título sobre un hueco es una promesa que no se cumple); si no lo hay —la sección suelta,
 * en una prueba o en otra pantalla—, pinta su tarjeta como siempre. «Reintentar» del aviso de arriba
 * reintenta todo lo que se anotó.
 */
@Stable
class ErroresDeLaPantalla {
    private val reintentos = mutableStateMapOf<String, () -> Unit>()

    /** ¿Alguna sección no pudo leer? */
    val hayError: Boolean get() = reintentos.isNotEmpty()

    /** Cuántas secciones no pudieron leer (para las pruebas). */
    val cuantos: Int get() = reintentos.size

    fun anotar(clave: String, reintentar: () -> Unit) { reintentos[clave] = reintentar }

    fun quitar(clave: String) { reintentos.remove(clave) }

    /** Reintenta todo lo anotado. Cada sección se borra sola cuando vuelve a leer bien. */
    fun reintentarTodo() { com.jvillada.movi.data.olvidarLoFresco(); reintentos.values.toList().forEach { it() } }
}

/** Quien junta los errores de la pantalla, o `null` (cada sección pinta el suyo). */
val LocalErroresDeLaPantalla = staticCompositionLocalOf<ErroresDeLaPantalla?> { null }

/** El `testTag` del aviso único de arriba. */
const val TAG_AVISO_DE_LA_PANTALLA: String = "aviso-de-error-de-la-pantalla"

/** Lo que dice el aviso único: «No pudimos cargar», con «Reintentar». */
const val NO_PUDIMOS_CARGAR: String = "No pudimos cargar"

/**
 * Una sección que no pudo leer: se anota en [LocalErroresDeLaPantalla] y no pinta nada, o —sin nadie
 * que junte— pinta su [NoSePudoLeer] con [texto] y [contenidoSuelto] (su título, por ejemplo).
 *
 * @param clave una por sección («disponible», «facturas»…): la misma sección no se anota dos veces.
 */
@Composable
fun NoSePudoLeerEnLaPantalla(
    clave: String,
    texto: String,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
    contenidoSuelto: @Composable () -> Unit = {},
) {
    val errores = LocalErroresDeLaPantalla.current
    if (errores == null) {
        contenidoSuelto()
        NoSePudoLeer(texto, onReintentar, modifier)
        return
    }
    val reintentar by rememberUpdatedState(onReintentar)
    DisposableEffect(errores, clave) {
        errores.anotar(clave) { reintentar() }
        onDispose { errores.quitar(clave) }
    }
}

/**
 * **El aviso único de arriba**: [NO_PUDIMOS_CARGAR] con «Reintentar», solo si alguna sección se anotó.
 * Es la misma tarjeta [NoSePudoLeer] de siempre, una vez.
 */
@Composable
fun AvisoDeLaPantalla(errores: ErroresDeLaPantalla, modifier: Modifier = Modifier) {
    if (!errores.hayError) return
    NoSePudoLeer(NO_PUDIMOS_CARGAR, onReintentar = { errores.reintentarTodo() }, modifier = modifier.testTag(TAG_AVISO_DE_LA_PANTALLA))
}
