package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.RecuerdoDeCategoria
import kotlinx.coroutines.CancellationException

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
 * funcionando sin sugerencias, que es exactamente el comportamiento de antes de esta tarea.
 *
 * **Fix round 1 — por qué [invalidar] y no un `recargar()` que vuelve a pedir.** La primera
 * versión llamaba a un `recargar()` `suspend` desde `QuickAddScreen`, con
 * `coroutine.launch { … }` sobre el `rememberCoroutineScope()` de la propia hoja, justo antes de
 * `onSaved()`. Y `onSaved()` es lo que hace que `App.kt` saque la hoja de composición — el mismo
 * frame en que ese `launch` recién arrancaba. Al salir de composición, Compose cancela el scope
 * de la hoja, y esa cancelación se llevaba puesta la petición a mitad de camino: `runCatching`
 * atrapa la `CancellationException` como una falla más, `cargada` se queda en `false` … pero el
 * PRÓXIMO `cargarSiHaceFalta()` (la hoja siguiente) también corre en un scope que se cancela apenas
 * el guardado exitoso la cierra a su vez, así que en la práctica ninguna sugerencia posterior a un
 * guardado alcanzaba a llegar en toda la sesión — un defecto silencioso, sin ningún test que lo
 * viera porque ningún test montaba una SEGUNDA hoja.
 *
 * La cura no es reintentar la red desde una corrutina a punto de morir: es invalidar sin pedir
 * nada, con una asignación de estado — ninguna corrutina que abortar. Que la próxima hoja pida de
 * verdad ya lo garantizaba [cargarSiHaceFalta]; lo único que faltaba era dejar `cargada` en
 * `false` de una forma que sobreviviera al cierre de la hoja que guardó.
 */
object MemoriaDeCategoriasCache {

    var recuerdos: List<RecuerdoDeCategoria> by mutableStateOf(emptyList())
        private set

    /** Ya se consiguió traer la memoria (con éxito) en esta sesión — ver [cargarSiHaceFalta]. */
    private var cargada = false

    /** Para no disparar dos lecturas simultáneas cuando se abren dos hojas de «Agregar» seguidas. */
    private var enVuelo = false

    /**
     * Cuántas veces se invalidó. No es un contador decorativo: es lo que distingue, cuando una
     * lectura en vuelo por fin contesta, si esa respuesta sigue siendo válida o si alguien la
     * invalidó MIENTRAS viajaba (ver el aviso dentro de [buscar]).
     */
    private var version = 0

    private suspend fun buscar() {
        if (enVuelo) return
        enVuelo = true
        val versionAlEmpezar = version
        try {
            val traida = Repositories.wallets.getMemoriaDeCategorias()
            recuerdos = traida
            // Si alguien invalidó (guardó un movimiento) MIENTRAS este pedido viajaba, lo que
            // acaba de llegar es la foto de ANTES de esa invalidación: no puede confirmar que la
            // memoria está al día. Dejar `cargada` en `false` acá es lo que hace que el próximo
            // `cargarSiHaceFalta()` vuelva a pedir en vez de darse por satisfecho con una
            // respuesta vieja que llegó tarde — la otra mitad del defecto que abrió esta ronda:
            // "una invalidación durante una carga en vuelo también tiene que llevar a una carga
            // fresca la próxima vez", no solo la invalidación en frío.
            if (version == versionAlEmpezar) cargada = true
        } catch (e: CancellationException) {
            // Nunca se traga: quien cancela este scope (una hoja que se cierra) tiene que
            // enterarse. Atraparla acá y no relanzarla dejaba a `buscar()` terminando "normal"
            // pese a que la corrutina de afuera ya estaba cancelada — la causa de fondo de esta
            // ronda de arreglos.
            throw e
        } catch (e: Throwable) {
            // `Throwable` y no `Exception`: en la web un `fetch` caído sube como `kotlin.Error`,
            // que no es `Exception` — `ExcepcionesDeRedScanTest` (`:core`) vigila justamente esto
            // en todo lo que corre en wasm. Si falla (que no sea cancelación): sin sugerencias,
            // sin error visible — `cargada` queda en `false` a propósito, para que el próximo
            // llamador (otra hoja de «Agregar» en esta misma sesión) reintente. Un server que
            // estaba caído hace un minuto puede contestar ahora.
        } finally {
            enVuelo = false
        }
    }

    /**
     * **La primera vez que se abre «Agregar» en la sesión.** Idempotente: si ya se sabe, no vuelve
     * a preguntar — la memoria no cambia mientras el dueño no guarde un movimiento nuevo, y eso lo
     * cubre [invalidar].
     */
    suspend fun cargarSiHaceFalta() {
        if (cargada) return
        buscar()
    }

    /**
     * **Después de guardar un movimiento.** Síncrona y sin red — a propósito, ver el KDoc de la
     * clase para el porqué (un `recargar()` que pedía de nuevo se cancelaba a medio camino apenas
     * la hoja que acababa de guardar se cerraba). Marca la memoria como vieja; la respuesta con la
     * anotación recién hecha la trae [cargarSiHaceFalta], la próxima vez que alguien la llame —que
     * en la práctica es la próxima hoja de «Agregar» que se abra, con un scope que sí sobrevive lo
     * suficiente para terminar el pedido.
     */
    fun invalidar() {
        cargada = false
        version++
    }

    /** Al cerrar sesión: es la memoria del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        recuerdos = emptyList()
        cargada = false
        version++
    }
}
