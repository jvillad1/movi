package com.jvillada.movi.ui.porrevisar

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/*
 * # Volver a Por revisar donde la dejaste (vuelta 44)
 *
 * La revisión de «un día del dueño»: tras confirmar un aviso en Revisar aviso, al volver la bandeja
 * quedaba en otro lugar. La lista recordaba su posición por **número de fila** (lo que guarda
 * `LazyListState`), y al volver las filas ya no eran las mismas —el aviso confirmado se fue, el lote
 * cambió, y mientras la bandeja se relee hay un esqueleto de una sola fila que la manda arriba—.
 *
 * Ahora se recuerda **por llave**: las filas que se veían, en orden, y cuánto estaba corrida la
 * primera. Al volver, la lista va a la primera de esas filas que siga existiendo; si la primera era
 * el aviso que se confirmó, la siguiente queda arriba, a la vista. Nada salta después.
 */

/** La llave de la fila del esqueleto mientras la bandeja se lee: esa no se recuerda ni se espera. */
const val LLAVE_DEL_ESQUELETO: String = "esqueleto"

/** Las llaves de la lista en el orden en que se declararon (ver [fila]). */
class LlavesDeLaLista internal constructor() {
    internal val orden: MutableList<String> = mutableListOf()

    /** Arranca una declaración nueva de la lista: se llama al principio del contenido del `LazyColumn`. */
    fun empezar() = orden.clear()
}

/** Un `item` con su llave, anotada en [llaves] para poder volver a ella. */
fun LazyListScope.fila(llaves: LlavesDeLaLista, llave: String, contenido: @Composable LazyItemScope.() -> Unit) {
    llaves.orden += llave
    item(key = llave, content = contenido)
}

/**
 * **A qué fila volver**, puro: la primera de las que se veían ([aLaVista], en orden) que siga en la
 * lista ([ahora]), con el corrimiento de antes solo si es la misma primera fila. `null` si no queda
 * ninguna (o no había nada que recordar): la lista arranca arriba.
 */
fun dondeVolver(aLaVista: List<String>, corrimiento: Int, ahora: List<String>): Pair<Int, Int>? {
    val destino = aLaVista.firstOrNull { it in ahora } ?: return null
    return ahora.indexOf(destino) to (if (destino == aLaVista.first()) corrimiento else 0)
}

/** El estado de una lista que vuelve a donde la dejaste, y las llaves con que se declara. */
class ListaQueSeConserva internal constructor(val estado: LazyListState, val llaves: LlavesDeLaLista)

/**
 * Una lista que se acuerda de sus filas a la vista y vuelve a ellas. [conDatos] dice si lo que se
 * pinta es la bandeja de verdad (no el esqueleto): solo eso se recuerda, y solo eso dispara la vuelta.
 */
@Composable
fun rememberListaQueSeConserva(conDatos: Boolean): ListaQueSeConserva {
    val estado = rememberLazyListState()
    val llaves = remember { LlavesDeLaLista() }
    var aLaVista by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var corrimiento by rememberSaveable { mutableStateOf(0) }
    // `remember` y no `rememberSaveable`: cada vez que la pantalla vuelve a componerse (al volver de
    // Revisar aviso) hay que volver a la fila.
    var yaVolvio by remember { mutableStateOf(false) }

    LaunchedEffect(conDatos) {
        if (!conDatos || yaVolvio) return@LaunchedEffect
        // Que la lista ya se haya medido con la bandeja de verdad, no con el esqueleto de la carga.
        snapshotFlow { estado.layoutInfo.visibleItemsInfo.map { it.key.toString() } }
            .first { llavesALaVista -> llavesALaVista.isNotEmpty() && LLAVE_DEL_ESQUELETO !in llavesALaVista }
        dondeVolver(aLaVista, corrimiento, llaves.orden.toList())?.let { (indice, corrido) ->
            estado.scrollToItem(indice, corrido)
        }
        yaVolvio = true
    }
    LaunchedEffect(estado, conDatos) {
        if (!conDatos) return@LaunchedEffect
        snapshotFlow {
            estado.layoutInfo.visibleItemsInfo.map { it.key.toString() } to estado.firstVisibleItemScrollOffset
        }.collect { (llavesALaVista, corrido) ->
            if (yaVolvio && llavesALaVista.isNotEmpty() && LLAVE_DEL_ESQUELETO !in llavesALaVista) {
                aLaVista = llavesALaVista
                corrimiento = corrido
            }
        }
    }
    return remember(estado, llaves) { ListaQueSeConserva(estado, llaves) }
}
