package com.jvillada.movi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * # Qué hojas modales hay abiertas, para que el botón «atrás» del sistema las cierre
 *
 * ## El defecto que cierra
 *
 * El «atrás» del teléfono sabía de exactamente DOS hojas —«Agregar» y la de crear el recurrente—
 * porque las dos viven en `App.kt` como estado suyo, y ahí estaba escrita la regla a mano. Las
 * hojas de Perfil («Cambiar contraseña», el color del avatar, el día de corte, los días de aviso)
 * son overlays que cada pantalla dibuja por su cuenta: `App.kt` no las ve, así que el «atrás» no
 * las cerraba — **sacaba el tope de la pila**. El dueño escribía las dos contraseñas, apretaba
 * atrás para cerrar la hoja y aterrizaba en «Más» con todo lo tipeado perdido y sin una palabra
 * de explicación.
 *
 * La regla que `App.kt` ya declaraba en un comentario («el botón atrás tiene que cerrar la modal
 * antes de tocar la pila, igual que haría con cualquier diálogo») ahora está implementada de
 * verdad, y para TODAS las hojas, no para las dos que App.kt conoce.
 *
 * ## Cómo se usa
 *
 * Una línea adentro del bloque que dibuja la hoja, al lado del `onDismiss` que ya existe:
 *
 * ```kotlin
 * if (mostrandoLaHoja) {
 *     AtrasCierraEstaHoja { mostrandoLaHoja = false }
 *     MiHoja(onDismiss = { mostrandoLaHoja = false })
 * }
 * ```
 *
 * Es **opt-in**: una hoja que no lo llame se comporta como antes. Eso es a propósito — así cada
 * pantalla se suma cuando su dueño la toca, sin que nadie tenga que reescribir todas de una.
 *
 * ## Por qué una pila y no un booleano
 *
 * Porque las hojas se enciman: con «Agregar» abierta puede aparecer la de crear el recurrente
 * encima. «Atrás» tiene que cerrar **la de arriba** y dejar la de abajo donde estaba, que es lo
 * que cualquiera espera de un diálogo sobre otro. El orden sale solo: `DisposableEffect` registra
 * en orden de composición, y en un `Box` el hijo que se compone después es el que se pinta encima.
 */
@Stable
class PilaDeHojas {
    /**
     * Es `mutableStateListOf` y no una lista pelada porque `App.kt` LEE [hayHojaAbierta] para
     * decidir si el `BackHandlerEffect` va prendido: sin estado observable, abrir una hoja no
     * recompondría nada y el «atrás» seguiría apagado hasta la próxima recomposición por otro
     * motivo.
     */
    private val abiertas = mutableStateListOf<HojaAbierta>()

    val hayHojaAbierta: Boolean get() = abiertas.isNotEmpty()

    /** Cuántas hay encimadas. Para pruebas y diagnóstico; la app solo pregunta si hay alguna. */
    val cuantas: Int get() = abiertas.size

    fun registrar(hoja: HojaAbierta) {
        abiertas.add(hoja)
    }

    /** Idempotente: cerrar una hoja ya la saca, y el `onDispose` que sigue no tiene que enterarse. */
    fun soltar(hoja: HojaAbierta) {
        abiertas.remove(hoja)
    }

    /**
     * Cierra la hoja de más arriba. Devuelve `true` si había alguna — o sea, si el «atrás» ya
     * quedó atendido y NO hay que tocar la pila de navegación.
     *
     * Se saca de la lista acá mismo, antes de llamar al cierre: el `onDispose` que va a sacarla
     * llega recién en la próxima recomposición, y hasta entonces dos «atrás» seguidos cerrarían
     * dos veces la misma hoja en vez de bajar un piso.
     */
    fun cerrarLaDeArriba(): Boolean {
        val hoja = abiertas.lastOrNull() ?: return false
        abiertas.removeAt(abiertas.lastIndex)
        hoja.cerrar()
        return true
    }
}

/**
 * Una hoja abierta, vista por la pila: nada más que cómo cerrarla.
 *
 * Es una clase (identidad) y no la lambda pelada porque [PilaDeHojas.soltar] la busca por
 * igualdad: dos hojas con el mismo cierre no pueden confundirse entre sí.
 */
class HojaAbierta(private val alCerrar: () -> Unit) {
    fun cerrar() = alCerrar()
}

/** La pila de la app. El default vacío es para que un preview o una prueba aislada no explote. */
val LocalPilaDeHojas = staticCompositionLocalOf { PilaDeHojas() }

/**
 * Declara que hay una hoja modal abierta acá, y que el «atrás» del sistema tiene que cerrarla
 * antes de tocar la navegación. Ver [PilaDeHojas] para el porqué y el ejemplo.
 */
@Composable
fun AtrasCierraEstaHoja(onDismiss: () -> Unit) {
    val pila = LocalPilaDeHojas.current
    // `rememberUpdatedState` para que una recomposición con un `onDismiss` nuevo NO saque y
    // vuelva a poner la hoja: eso la mandaría al tope de la pila y, con dos hojas encimadas,
    // el «atrás» cerraría la de abajo.
    val cierreActual = rememberUpdatedState(onDismiss)
    DisposableEffect(pila) {
        val hoja = HojaAbierta { cierreActual.value() }
        pila.registrar(hoja)
        onDispose { pila.soltar(hoja) }
    }
}

/**
 * Un «atrás» del sistema, entero: primero las hojas, después la pila de navegación.
 *
 * Vive acá y no adentro de `App.kt` para poder probarlo — y porque la regla es una sola para
 * toda la app, no una lista de casos que crece cada vez que alguien agrega una hoja.
 */
fun atras(hojas: PilaDeHojas, pila: MutableList<Screen>) {
    if (hojas.cerrarLaDeArriba()) return
    if (pila.size > 1) pila.removeAt(pila.lastIndex)
}

/**
 * ¿Hay algo que el «atrás» pueda hacer? Si no, el handler va apagado y el sistema hace lo suyo
 * (en Android, salir de la app).
 */
fun hayAdondeVolver(hojas: PilaDeHojas, pila: List<Screen>): Boolean =
    hojas.hayHojaAbierta || pila.size > 1
