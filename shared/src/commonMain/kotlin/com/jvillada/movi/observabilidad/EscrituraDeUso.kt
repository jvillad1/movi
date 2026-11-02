package com.jvillada.movi.observabilidad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso

/**
 * # Lo que se mide de un campo de texto (vuelta 104): que empezó y que terminó, nunca lo escrito
 *
 * Un campo de `ds/` (`Campo`, `CampoDeMonto`, `CampoDeBusqueda`) anota [EventoDeUso.CAMPO_EMPEZADO] con
 * la primera tecla que **cambia el texto** (mover el cursor o seleccionar no cuenta) y
 * [EventoDeUso.CAMPO_TERMINADO] cuando pierde el foco —o sale de la pantalla— después de haber
 * escrito. Los dos llevan solo el id del campo ([ClaveDeUso.CAMPO]: su tag del código o su rótulo de
 * la app, ver `Toques.kt`). El contenido no pasa por acá nunca: ni el texto, ni su largo, ni si quedó vacío.
 */
class EscrituraDeUso internal constructor() {
    /** El id del campo; lo actualiza cada composición. */
    var campo: String = "campo"
        internal set

    private var empezo = false

    /** El texto cambió: la primera vez, «empezó a escribir». */
    fun alCambiarElTexto() {
        if (empezo) return
        empezo = true
        Uso.evento(EventoDeUso.CAMPO_EMPEZADO, mapOf(ClaveDeUso.CAMPO to campo))
    }

    /** Perdió el foco o se fue: si había escrito, «terminó». */
    fun alSoltar() {
        if (!empezo) return
        empezo = false
        Uso.evento(EventoDeUso.CAMPO_TERMINADO, mapOf(ClaveDeUso.CAMPO to campo))
    }
}

/** El registro de escritura de un campo cuyo id es [campo] (ver [EscrituraDeUso]). */
@Composable
fun rememberEscrituraDeUso(campo: String): EscrituraDeUso {
    val escritura = remember { EscrituraDeUso() }
    escritura.campo = campo
    DisposableEffect(escritura) { onDispose { escritura.alSoltar() } }
    return escritura
}

/** El foco del campo, para [EscrituraDeUso.alSoltar]. Va en el `Modifier` del `BasicTextField`. */
fun Modifier.observarLaEscritura(escritura: EscrituraDeUso): Modifier =
    onFocusChanged { if (!it.isFocused) escritura.alSoltar() }
