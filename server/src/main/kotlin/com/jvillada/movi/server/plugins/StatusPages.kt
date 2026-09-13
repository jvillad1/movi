package com.jvillada.movi.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException

/** Lo que se le dice a un cliente cuyo cuerpo no se pudo leer. */
const val PEDIDO_ILEGIBLE: String = "No se pudo leer el pedido. Revisa los datos y vuelve a intentarlo."

/**
 * **Un pedido mal formado es un 400 en toda la API, no un 500.**
 *
 * Sin este plugin cada ruta decidía sola: `POST /api/recurring-rules/{id}/occurrence` atrapaba el
 * `receive` y contestaba 400, y todas las demás dejaban escapar la excepción, que Ktor convierte en
 * un 500 sin mensaje. Un 500 le dice al cliente «el server se rompió» e invita a reintentar algo que
 * nunca va a funcionar; y en la app se leía «Algo salió mal» en vez del motivo.
 *
 * Solo se traducen las excepciones que **nacen del pedido**:
 * - [BadRequestException] — lo que tira `call.receive` con un JSON roto o sin un campo obligatorio.
 * - [SerializationException] — lo que tira un `decodeFromJsonElement` hecho a mano (tarjetas,
 *   créditos, suscripciones leen el `JsonObject` crudo para distinguir claves ausentes).
 *
 * Todo lo demás sigue siendo 500 a propósito: un error de base o un bug nuestro no es culpa del
 * pedido, y disfrazarlo de 400 escondería justo lo que hay que arreglar.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, PEDIDO_ILEGIBLE) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, PEDIDO_ILEGIBLE) }
    }
}
