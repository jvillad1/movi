package com.jvillada.movi.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.data.intentar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ola 4: **los «Está bien» de esta sesión**, para que el aviso se vaya en el mismo toque y no
 * vuelva al volver al Inicio antes de la próxima lectura (que ya no lo trae: el server guarda la
 * huella en `anomalias_descartadas`). Es del usuario que está: `SessionManager.clear()` lo vacía.
 */
object AnomaliasDescartadasEnLaSesion {
    var huellas: Set<String> by mutableStateOf(emptySet())
        private set

    /**
     * Vuelta 89: el alcance de [descartarYGuardar]. **No es de la pantalla, a propósito** (ver ahí). En el
     * hilo principal, como el `rememberCoroutineScope` de antes: [huellas] se sigue escribiendo desde un
     * solo hilo.
     */
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun descartar(huella: String) {
        huellas = huellas + huella
    }

    /** Si el server no lo guardó, el aviso vuelve: no se finge un descarte que no quedó. */
    fun deshacer(huella: String) {
        huellas = huellas - huella
    }

    /**
     * # Vuelta 89 · **el descarte vive más que su tarjeta**
     *
     * Saca [huella] en el acto y corre [guardar] (el pedido al server); si [guardar] falla de verdad, la
     * deshace y el aviso vuelve. Es lo único que deben llamar «Está bien», «No» y «Sí, cambiar».
     *
     * Antes cada sección lanzaba el pedido en su propio `rememberCoroutineScope()`. Pero sacar la huella
     * saca la tarjeta, y si era **la última**, saca también la sección («Movi propone hoy» o «Para revisar»
     * dejan de pintarse sin nada adentro): el alcance se cancelaba con el pedido en vuelo, el
     * `runCatching` atrapaba esa `CancellationException` como si fuera una falla y llamaba a [deshacer]. La
     * tarjeta volvía en el mismo cuadro —el dueño veía que «Está bien» no hacía nada— mientras el server sí
     * guardaba el descarte (31-oct: `deuda:acc_…:2026-10` en `anomalias_descartadas`). Las pruebas de antes
     * no lo veían: su repositorio falso contestaba sin suspender.
     *
     * Por eso el pedido va en un alcance que no es de ninguna pantalla, y una cancelación no cuenta como
     * falla ([intentar]).
     */
    fun descartarYGuardar(huella: String, guardar: suspend () -> Unit) {
        descartar(huella)
        alcance.launch {
            intentar { guardar() }.onFailure { deshacer(huella) }
        }
    }

    fun olvidar() {
        huellas = emptySet()
    }
}
