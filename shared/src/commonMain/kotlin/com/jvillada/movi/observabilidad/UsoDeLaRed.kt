package com.jvillada.movi.observabilidad

import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import io.ktor.http.Url

/**
 * **Lo que la red le cuenta al uso** (vuelta 100), desde el cliente HTTP de cada plataforma
 * (`createHttpClient`): una escritura que el server contestó con 4xx/5xx es
 * [EventoDeUso.ESCRITURA_FALLIDA] con su código; un pedido que no llegó es [EventoDeUso.ERROR_DE_RED].
 *
 * Solo se anota el **recurso** (el segmento después de `/api/`: `events`), el método y el código —
 * nunca la ruta entera, que lleva ids, ni la consulta, ni el cuerpo—. Las rutas del propio uso y de
 * los fallos no se anotan (se anotarían a sí mismas). Un mismo error repetido dentro del minuto se
 * anota una vez (lo hace [Uso.evento]): un sync caído cada 30 s no llena la sesión.
 */
fun anotarLaRespuestaParaElUso(status: Int, metodo: String, url: Url) {
    if (status < 400 || metodo == "GET" || metodo == "HEAD") return
    val recurso = recursoDe(url.encodedPath) ?: return
    Uso.evento(
        EventoDeUso.ESCRITURA_FALLIDA,
        mapOf(ClaveDeUso.CODIGO to status.toString(), ClaveDeUso.METODO to metodo, ClaveDeUso.RECURSO to recurso),
    )
}

/** Un pedido a la API de Movi que no llegó al server (sin conexión, cortado). */
fun anotarElErrorDeRedParaElUso(metodo: String, url: Url) {
    val recurso = recursoDe(url.encodedPath) ?: return
    val datos = buildMap {
        put(ClaveDeUso.RECURSO, recurso)
        if (ClaveDeUso.METODO.admite(metodo)) put(ClaveDeUso.METODO, metodo)
    }
    Uso.evento(EventoDeUso.ERROR_DE_RED, datos)
}

/** `/api/events/ev_1/parecidos` → `events`; lo que no es de la API, o es del uso o de los fallos, `null`. */
internal fun recursoDe(path: String): String? {
    if (!path.startsWith("/api/")) return null
    val recurso = path.removePrefix("/api/").substringBefore('/').lowercase()
    if (recurso == "uso" || recurso == "fallos" || recurso == "errores-del-cliente") return null
    return recurso.takeIf { ClaveDeUso.RECURSO.admite(it) }
}
