package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.RecuerdoDeCategoria

/**
 * **Task 5 — la memoria de categorías, del lado del cliente.** Es la misma memoria que ya usaba el
 * server para clasificar un SMS entrante ([com.jvillada.movi.shared.model.MemoriaDeCategorias],
 * en `:core`), pero acá viaja por `GET /api/categorias/memoria`
 * ([com.jvillada.movi.shared.repository.WalletRepository.getMemoriaDeCategorias]) para que
 * «Agregar» también pueda ofrecerle una categoría al dueño al escribir la nota a mano —no solo al
 * confirmar un mensaje del banco.
 *
 * Mismo patrón que [ReminderChannelsCache]: se pide **una vez por sesión**, la primera vez que se
 * abre «Agregar», y una lectura fallida no deja ningún rastro visible — la pantalla sigue
 * funcionando sin sugerencias, que es exactamente el comportamiento de antes de esta tarea. La
 * memoria no cambia mientras la app está abierta salvo por lo que el propio dueño anota, así que
 * [recargar] existe aparte: se llama después de guardar un movimiento, para que la próxima hoja de
 * «Agregar» de esta sesión ya vea la anotación recién hecha.
 *
 * Solo vive en memoria del proceso, igual que [UsedCategoriesCache.used]: no es una fuente de
 * verdad, es una ayuda para escribir, y repoblarla en cada apertura de la app no pierde nada que
 * valga la pena persistir.
 */
object MemoriaDeCategoriasCache {

    var recuerdos: List<RecuerdoDeCategoria> by mutableStateOf(emptyList())
        private set

    /** Ya se consiguió traer la memoria (con éxito) en esta sesión — ver [cargarSiHaceFalta]. */
    private var cargada = false

    /** Para no disparar dos lecturas simultáneas cuando se abren dos hojas de «Agregar» seguidas. */
    private var enVuelo = false

    private suspend fun buscar() {
        if (enVuelo) return
        enVuelo = true
        try {
            runCatching { Repositories.wallets.getMemoriaDeCategorias() }
                .onSuccess { recuerdos = it; cargada = true }
            // Si falla: sin sugerencias, sin error visible — `cargada` queda en `false` a
            // propósito, para que el próximo llamador (otra hoja de «Agregar» en esta misma
            // sesión) reintente. Un server que estaba caído hace un minuto puede contestar ahora.
        } finally {
            enVuelo = false
        }
    }

    /**
     * **La primera vez que se abre «Agregar» en la sesión.** Idempotente: si ya se sabe, no vuelve
     * a preguntar — la memoria no cambia mientras el dueño no guarde un movimiento nuevo, y eso lo
     * cubre [recargar].
     */
    suspend fun cargarSiHaceFalta() {
        if (cargada) return
        buscar()
    }

    /**
     * **Después de guardar un movimiento.** La memoria que trajo el server puede quedar vieja apenas
     * el dueño anota algo con una categoría nueva para ese destinatario — sin esto, la próxima hoja
     * de «Agregar» de la misma sesión seguiría sugiriendo (o sin sugerir) con datos de antes de ese
     * guardado. Se llama sin esperar (`coroutine.launch`, no `await`) desde `QuickAddScreen`: es una
     * ayuda para escribir, y no puede demorar un guardado que ya terminó.
     */
    suspend fun recargar() {
        buscar()
    }

    /** Al cerrar sesión: es la memoria del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        recuerdos = emptyList()
        cargada = false
    }
}
