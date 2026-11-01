package com.jvillada.movi.server.observabilidad

import kotlinx.coroutines.asContextElement
import kotlin.coroutines.CoroutineContext

/**
 * # Qué pedido (o qué job) está corriendo en este hilo (vuelta 102)
 *
 * Una consulta lenta tiene que decir **qué ruta la pidió**, y una llamada a la IA tiene que saber si
 * hubo un usuario detrás. Ninguna de las dos tiene el `call` a mano: la consulta corre dentro de un
 * `transaction {}` de Exposed en un hilo de `Dispatchers.IO`, y la IA, dentro de una función que no
 * sabe de Ktor. Lo que sí las acompaña es el contexto de la corrutina.
 *
 * Por eso esto: un `ThreadLocal` que la corrutina **lleva consigo** (`asContextElement`). Cada vez que
 * la corrutina del pedido corre en un hilo —el de Netty o uno de IO—, el hilo ve su [ElPedidoEnCurso].
 * El plugin de métricas lo pone al empezar el pedido, y [enUnJob] lo pone al correr un job.
 */
class ElPedidoEnCurso(
    /** `GET /api/events/{id}` o `job:recordatorios`. Se completa cuando el ruteo encuentra la ruta. */
    @Volatile var donde: String,
) {
    /** ¿Este pedido llamó a la IA? Lo marca [medirLlamada]; el plugin cuenta al usuario del día. */
    @Volatile var usoLaIa: Boolean = false

    companion object {
        private val actual = ThreadLocal<ElPedidoEnCurso?>()

        /** El de este hilo, o `null` fuera de un pedido o un job (el arranque, una prueba). */
        fun actual(): ElPedidoEnCurso? = actual.get()

        /** El elemento de contexto que lleva [pedido] a cada hilo donde corra la corrutina. */
        fun comoContexto(pedido: ElPedidoEnCurso): CoroutineContext = actual.asContextElement(pedido)
    }
}

/** Corre [bloque] con [nombre] como «quién pidió» de lo que pase adentro (consultas, llamadas). */
suspend fun <T> enUnJob(nombre: String, bloque: suspend () -> T): T =
    kotlinx.coroutines.withContext(ElPedidoEnCurso.comoContexto(ElPedidoEnCurso("job:$nombre"))) { bloque() }
