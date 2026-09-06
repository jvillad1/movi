package com.jvillada.movi.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * # La rueda del mouse sobre los márgenes también mueve la lista
 *
 * ### El bug
 *
 * En pantalla ancha, `App.kt` centra cada pantalla en una columna de 600 dp y pinta el resto del
 * ancho —entre la barra lateral y la columna, y entre la columna y el borde de la ventana— con un
 * `Box` que no hace nada. Compose entrega la rueda del mouse al nodo que está **debajo del
 * puntero**, así que con el mouse sobre esos márgenes el evento llegaba al `Box` vacío y la lista,
 * que vive adentro de la columna, no se enteraba. El dueño lo describió exacto: *«el scroll sobre
 * la lista no funciona si tengo el mouse por fuera de la columna donde se listan los
 * movimientos»*. A 2000 px de ancho, la columna es menos de un tercio de la ventana.
 *
 * ### El arreglo, y por qué vive en la cáscara
 *
 * No es un problema de Movimientos: es de la cáscara, que es la que dibuja los márgenes. Por eso
 * el `Box` de los márgenes recibe un `Modifier.scrollable` ([recibeElScrollDeLosMargenes]) cuyo
 * estado es un **relevo**: no scrollea nada propio, le pasa cada delta a la lista que la pantalla
 * activa haya registrado con [ScrollDesdeLosMargenes]. Una pantalla que no registra nada queda
 * como antes — el relevo sin objetivo no consume nada.
 *
 * No pisa a la lista cuando el mouse SÍ está sobre ella: Compose despacha la rueda primero al
 * nodo más profundo, la lista la consume, y el `scrollable` de afuera ve el evento consumido y
 * lo deja pasar. Tampoco le pelea el arrastre en el teléfono: como padre de nested scroll, lo
 * único que recibe es el **sobrante** de la lista (`onPostScroll` → `dispatchRawDelta`, sin tomar
 * el mutex de `scroll {}`), y ese sobrante solo existe cuando la lista ya está en un borde, donde
 * delegárselo de vuelta no hace nada.
 *
 * `reverseDirection` va con `ScrollableDefaults.reverseDirection`, la misma convención que usan
 * `LazyColumn` y `verticalScroll` por dentro: sin eso, la rueda hacia abajo movería la lista hacia
 * arriba.
 */
class RelevoDeScroll {
    /** La lista de la pantalla activa, o `null` si la pantalla no registró ninguna. */
    var objetivo: ScrollableState? by mutableStateOf(null)

    /** El estado que el `scrollable` de los márgenes maneja; delega todo en [objetivo]. */
    val estado: ScrollableState = object : ScrollableState {
        override fun dispatchRawDelta(delta: Float): Float = objetivo?.dispatchRawDelta(delta) ?: 0f

        override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
            objetivo?.scroll(scrollPriority, block)
        }

        override val isScrollInProgress: Boolean get() = objetivo?.isScrollInProgress ?: false
        override val canScrollForward: Boolean get() = objetivo?.canScrollForward ?: false
        override val canScrollBackward: Boolean get() = objetivo?.canScrollBackward ?: false
    }
}

/** Lo provee `App.kt`; `null` fuera de la cáscara (una hoja montada sola en un test). */
val LocalRelevoDeScroll = staticCompositionLocalOf<RelevoDeScroll?> { null }

/** Para el contenedor que dibuja los márgenes: la rueda y el arrastre sobre él van a [relevo]. */
@Composable
fun Modifier.recibeElScrollDeLosMargenes(relevo: RelevoDeScroll): Modifier = this.scrollable(
    state = relevo.estado,
    orientation = Orientation.Vertical,
    reverseDirection = ScrollableDefaults.reverseDirection(
        layoutDirection = LocalLayoutDirection.current,
        orientation = Orientation.Vertical,
        reverseScrolling = false,
    ),
)

/**
 * Para la pantalla: «mi lista es esta». Se registra mientras la pantalla esté en composición y se
 * da de baja al salir — solo si sigue siendo la registrada, para que el orden en que Compose
 * desmonta una pantalla y monta la siguiente no borre el registro de la nueva.
 */
@Composable
fun ScrollDesdeLosMargenes(state: ScrollableState) {
    val relevo = LocalRelevoDeScroll.current ?: return
    DisposableEffect(relevo, state) {
        relevo.objetivo = state
        onDispose { if (relevo.objetivo === state) relevo.objetivo = null }
    }
}
