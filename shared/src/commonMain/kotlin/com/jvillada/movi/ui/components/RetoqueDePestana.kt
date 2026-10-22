package com.jvillada.movi.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import com.jvillada.movi.theme.LocalMovimientoReducido
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * # Re-tocar la pestaña activa sube al principio
 *
 * Lo que hace toda app con barra abajo (Revolut, Monzo, la de cualquier banco): estando en el fondo
 * de Movimientos, tocar «Movimientos» otra vez vuelve arriba, con un scroll animado. Antes ese
 * toque no hacía nada —la pantalla ya estaba arriba de la pila—, y la única forma de volver al
 * principio de un mes largo era arrastrar.
 *
 * `App.kt` sube [veces] cuando se toca la pestaña de la pantalla raíz que ya se está viendo, y cada
 * una de las cuatro raíces (Hoy, Movimientos, Plan, Patrimonio) dice qué listas suben con
 * [SubirAlRetocarLaPestana]. No es un `object`: vive en la composición, la da `App()`.
 */
class RetoqueDePestana {
    /** Cuántas veces se re-tocó la pestaña activa. Solo importa que cambie. */
    var veces by mutableIntStateOf(0)
        private set

    fun retocar() {
        veces++
    }
}

/** Lo da `App()`; `null` fuera de la app (una pantalla montada sola en una prueba). */
val LocalRetoqueDePestana = staticCompositionLocalOf<RetoqueDePestana?> { null }

/**
 * «Al re-tocar mi pestaña, estas listas vuelven al principio.» Va en la pantalla, al lado de su
 * `ScrollDesdeLosMargenes`. Solo reacciona a los toques que llegan con la pantalla puesta, no al
 * número que ya había cuando se montó.
 */
@Composable
fun SubirAlRetocarLaPestana(vararg estados: ScrollableState) {
    val retoque = LocalRetoqueDePestana.current ?: return
    val reducido = LocalMovimientoReducido.current
    val actuales by rememberUpdatedState(estados.toList())
    LaunchedEffect(retoque) {
        snapshotFlow { retoque.veces }.drop(1).collect {
            actuales.forEach { estado -> launch { subirAlPrincipio(estado, reducido) } }
        }
    }
}

private suspend fun subirAlPrincipio(estado: ScrollableState, reducido: Boolean) {
    when (estado) {
        is LazyListState -> if (reducido) estado.scrollToItem(0) else estado.animateScrollToItem(0)
        is ScrollState -> if (reducido) estado.scrollTo(0) else estado.animateScrollTo(0)
        else -> Unit
    }
}
