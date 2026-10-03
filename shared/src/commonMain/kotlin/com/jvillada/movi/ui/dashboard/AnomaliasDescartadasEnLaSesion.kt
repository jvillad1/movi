package com.jvillada.movi.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ola 4: **los «Está bien» de esta sesión**, para que el aviso se vaya en el mismo toque y no
 * vuelva al volver al Inicio antes de la próxima lectura (que ya no lo trae: el server guarda la
 * huella en `anomalias_descartadas`). Es del usuario que está: `SessionManager.clear()` lo vacía.
 */
object AnomaliasDescartadasEnLaSesion {
    var huellas: Set<String> by mutableStateOf(emptySet())
        private set

    fun descartar(huella: String) {
        huellas = huellas + huella
    }

    /** Si el server no lo guardó, el aviso vuelve: no se finge un descarte que no quedó. */
    fun deshacer(huella: String) {
        huellas = huellas - huella
    }

    fun olvidar() {
        huellas = emptySet()
    }
}
