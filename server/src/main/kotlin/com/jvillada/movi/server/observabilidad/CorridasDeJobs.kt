package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.db.CorridasDeJobs
import com.jvillada.movi.server.db.dbQuery
import org.jetbrains.exposed.sql.insert
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * # Cada corrida de un job queda anotada (vuelta 102)
 *
 * Los recordatorios, los cierres, el reproceso de correos y la retención corren en el scheduler y, si
 * fallaban, dejaban una línea en el log y nada más. Ahora cada corrida va a `corridas_de_jobs`: el
 * nombre, cuándo empezó, cuánto tardó, si salió bien y el error **recortado** (ver [errorRecortado]).
 *
 * [correrJob] no cambia lo que hace el job: devuelve lo mismo y vuelve a lanzar la misma excepción. Si
 * el job falló dos veces seguidas, avisa por correo al admin ([Alertas.revisarElJob]).
 */
suspend fun <T> correrJob(nombre: String, bloque: suspend () -> T): T {
    val inicio = Metricas.reloj()
    val t0 = System.nanoTime()
    val resultado = try {
        Result.success(enUnJob(nombre) { bloque() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
    val ms = (System.nanoTime() - t0) / 1_000_000
    val falla = resultado.exceptionOrNull()
    runCatching { anotarLaCorrida(nombre, inicio, ms, falla) }
        .onFailure { log.warn("jobs: no pude anotar la corrida de {}: {}", nombre, it.javaClass.simpleName) }
    if (falla != null) {
        runCatching { Alertas.revisarElJob(nombre) }
            .onFailure { log.warn("jobs: la alerta de {} falló: {}", nombre, it.javaClass.simpleName) }
    }
    return resultado.getOrThrow()
}

internal suspend fun anotarLaCorrida(nombre: String, inicio: Long, duracionMs: Long, falla: Throwable?) = dbQuery {
    CorridasDeJobs.insert {
        it[id] = UUID.randomUUID().toString()
        it[CorridasDeJobs.nombre] = nombre.take(60)
        it[CorridasDeJobs.inicio] = inicio
        it[CorridasDeJobs.duracionMs] = duracionMs
        it[bien] = falla == null
        it[error] = falla?.let(::errorRecortado)
    }
}

/**
 * El error de una corrida, para guardarlo: el tipo y el mensaje, **sin datos**. Un mensaje puede traer
 * un monto, un número de cuenta o un correo (`NumberFormatException: For input string "1843014"`), así
 * que los números de 5 o más cifras pasan a `#`, los correos a `@` y todo se corta a 200 caracteres.
 */
fun errorRecortado(e: Throwable): String {
    val crudo = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
    return crudo
        .replace(Regex("""[\w.+-]+@[\w-]+(\.[\w-]+)+"""), "@")
        .replace(Regex("""\d(?:[.,]?\d){4,}"""), "#")
        .replace(Regex("""\s+"""), " ")
        .take(200)
}

private val log = LoggerFactory.getLogger("com.jvillada.movi.server.observabilidad.CorridasDeJobs")
