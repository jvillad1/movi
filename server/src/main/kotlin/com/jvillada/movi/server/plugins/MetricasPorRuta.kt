package com.jvillada.movi.server.plugins

import com.jvillada.movi.server.db.DatabaseFactory
import com.jvillada.movi.server.observabilidad.ElPedidoEnCurso
import com.jvillada.movi.server.observabilidad.Metricas
import com.jvillada.movi.server.observabilidad.metodoNormalizado
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.PathSegmentOptionalParameterRouteSelector
import io.ktor.server.routing.PathSegmentParameterRouteSelector
import io.ktor.server.routing.PathSegmentTailcardRouteSelector
import io.ktor.server.routing.PathSegmentWildcardRouteSelector
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingRoot
import io.ktor.util.AttributeKey
import kotlinx.coroutines.withContext

/**
 * # Métricas por ruta (vuelta 102)
 *
 * Mide cada pedido —la **plantilla** de la ruta, el método, el status y la duración— y lo suma en
 * [Metricas], que lo agrega por minuto. Nunca la URL: `/api/events/ev-123?x=1` se anota como
 * `/api/events/{id}`, así que un id, un correo en una ruta o un query no llegan a ninguna métrica.
 *
 * Tres ganchos:
 * - **Setup**: toma la hora y envuelve el pedido entero con [ElPedidoEnCurso], para que una consulta
 *   lenta o una llamada a la IA sepan de qué pedido son.
 * - **`RoutingCallStarted`**: cuando el ruteo encontró la ruta, anota su plantilla.
 * - **`ResponseSent`**: con la respuesta ya mandada se sabe el status final, incluido el que puso
 *   `StatusPages` (un `BadRequestException` es un 400, no un 500).
 *
 * El usuario solo se mira para contar usuarios distintos por minuto, y de él queda un número.
 */
val MetricasPorRuta = createApplicationPlugin("MetricasPorRuta") {
    application.intercept(ApplicationCallPipeline.Setup) {
        // De una lista cerrada: el método lo escribe el cliente (ver `metodoNormalizado`).
        val metodo = metodoNormalizado(call.request.local.method.value)
        val pedido = ElPedidoEnCurso("$metodo $SIN_RUTA")
        call.attributes.put(LLAVE_DE_LA_MEDICION, Medicion(System.nanoTime(), metodo, pedido))
        withContext(ElPedidoEnCurso.comoContexto(pedido)) { proceed() }
    }

    application.monitor.subscribe(RoutingRoot.RoutingCallStarted) { call ->
        val medicion = call.attributes.getOrNull(LLAVE_DE_LA_MEDICION) ?: return@subscribe
        medicion.ruta = plantillaDe(call.route)
        medicion.pedido.donde = "${medicion.metodo} ${medicion.ruta}"
    }

    on(ResponseSent) { call ->
        val medicion = call.attributes.getOrNull(LLAVE_DE_LA_MEDICION) ?: return@on
        if (medicion.anotada) return@on
        medicion.anotada = true
        val ms = (System.nanoTime() - medicion.inicio) / 1_000_000
        val usuario = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        Metricas.registrarPedido(
            metodo = medicion.metodo,
            ruta = medicion.ruta,
            status = call.response.status()?.value ?: 200,
            duracionMs = ms,
            usuario = usuario,
            usoLaIa = medicion.pedido.usoLaIa,
        )
        DatabaseFactory.usoDelPool()?.let { (activas, esperando) -> Metricas.registrarPool(activas, esperando) }
    }
}

/** Lo que no encontró ninguna ruta (en este server casi nada: está el `/api/{...}` y lo estático). */
internal const val SIN_RUTA = "(sin ruta)"

internal class Medicion(val inicio: Long, val metodo: String, val pedido: ElPedidoEnCurso) {
    @Volatile var ruta: String = SIN_RUTA
    @Volatile var anotada: Boolean = false
}

private val LLAVE_DE_LA_MEDICION = AttributeKey<Medicion>("movi.medicion-del-pedido")

/**
 * La plantilla de una ruta: los segmentos del camino tal como se registraron (`{id}`, `{...}`), sin
 * los selectores que no son del camino (el método, `authenticate`).
 */
internal fun plantillaDe(nodo: RoutingNode): String {
    val partes = ArrayDeque<String>()
    var n: RoutingNode? = nodo
    while (n != null) {
        when (val s = n.selector) {
            is PathSegmentConstantRouteSelector -> partes.addFirst(s.value)
            is PathSegmentParameterRouteSelector -> partes.addFirst("${s.prefix.orEmpty()}{${s.name}}${s.suffix.orEmpty()}")
            is PathSegmentOptionalParameterRouteSelector -> partes.addFirst("${s.prefix.orEmpty()}{${s.name}?}${s.suffix.orEmpty()}")
            is PathSegmentWildcardRouteSelector -> partes.addFirst("*")
            is PathSegmentTailcardRouteSelector -> partes.addFirst("${s.prefix}{${s.name}...}")
            else -> Unit
        }
        n = n.parent
    }
    return "/" + partes.joinToString("/")
}
