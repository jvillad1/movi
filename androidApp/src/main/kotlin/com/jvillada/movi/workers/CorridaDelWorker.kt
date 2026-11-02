package com.jvillada.movi.workers

import androidx.work.ListenableWorker
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.observabilidad.Uso
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import kotlinx.coroutines.CancellationException

/**
 * # Cada corrida de un Worker en el uso (vuelta 104)
 *
 * Los Workers del teléfono son lo que Movi hace **sola**: subir un aviso, avisar un vencimiento, mirar
 * si la escucha de notificaciones sigue viva. Cada `doWork` pasa por [medirLaCorrida], que anota
 * [EventoDeUso.WORKER] con el nombre del Worker (fijo, no el de la clase: R8 lo cambiaría), el
 * resultado (`OK`, `REINTENTO`, `FALLO`, o `ERROR` si lanzó) y cuánto tardó en ms, **sin abrir una
 * sesión** ([Uso.eventoDeFondo]: un Worker de las 9:00 no es que la persona usó Movi). Después sube lo
 * pendiente si hay sesión: el proceso puede morir apenas el Worker termina. No cambia lo que el Worker
 * devuelve ni lo que lanza.
 */
suspend fun medirLaCorrida(nombre: String, trabajo: suspend () -> ListenableWorker.Result): ListenableWorker.Result {
    val desde = System.currentTimeMillis()
    try {
        val resultado = trabajo()
        anotar(nombre, resultadoDe(resultado), desde)
        return resultado
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        anotar(nombre, "ERROR", desde)
        throw e
    } finally {
        if (SessionManager.isLoggedIn) runCatching { Uso.enviar() }
    }
}

/** [medirLaCorrida] para un `Worker` que no es de corrutinas (no sube: no hay dónde esperar la red). */
fun medirLaCorridaBloqueante(nombre: String, trabajo: () -> ListenableWorker.Result): ListenableWorker.Result {
    val desde = System.currentTimeMillis()
    try {
        return trabajo().also { anotar(nombre, resultadoDe(it), desde) }
    } catch (e: Throwable) {
        anotar(nombre, "ERROR", desde)
        throw e
    }
}

/** El resultado de un Worker como lo cuenta el uso. Pura. */
fun resultadoDe(resultado: ListenableWorker.Result): String = when (resultado) {
    is ListenableWorker.Result.Success -> "OK"
    is ListenableWorker.Result.Retry -> "REINTENTO"
    else -> "FALLO"
}

private fun anotar(nombre: String, resultado: String, desde: Long) {
    runCatching {
        Uso.eventoDeFondo(
            EventoDeUso.WORKER,
            mapOf(
                ClaveDeUso.WORKER to nombre,
                ClaveDeUso.RESULTADO to resultado,
                ClaveDeUso.MS to (System.currentTimeMillis() - desde).coerceIn(0, 999_999_999).toString(),
            ),
        )
    }
}
