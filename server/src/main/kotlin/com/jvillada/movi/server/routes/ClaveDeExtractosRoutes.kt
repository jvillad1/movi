package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.documentos.FallaDeLaClaveDelPdf
import com.jvillada.movi.server.documentos.MAX_BYTES_DEL_CUERPO_CON_CLAVE
import com.jvillada.movi.server.documentos.PdfConClave
import com.jvillada.movi.server.documentos.claveDelCuerpo
import com.jvillada.movi.server.documentos.hayClaveRecordada
import com.jvillada.movi.server.documentos.olvidarLaClave
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.ClaveDelPdf
import com.jvillada.movi.shared.model.EstadoDeLaClaveDeExtractos
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/**
 * # La clave de los extractos, vista desde Ajustes (vuelta 103)
 *
 * Ver `documentos/PdfConClave.kt`. Estas dos rutas **nunca devuelven la clave**: la de lectura dice si
 * hay una guardada y si el server puede guardar (sin `DOCUMENTS_ENCRYPTION_KEY`, no), y la otra la
 * borra («Olvidarla», Ajustes → Tus datos).
 */
fun Route.claveDeExtractosRoutes() {
    get("/api/extractos/clave") {
        val uid = call.userId()
        val guardada = dbQuery { hayClaveRecordada(uid) }
        call.respond(EstadoDeLaClaveDeExtractos(guardada = guardada, sePuedeRecordar = PdfConClave.sePuedeRecordar()))
    }

    delete("/api/extractos/clave") {
        val uid = call.userId()
        dbQuery { olvidarLaClave(uid) }
        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * La clave que trae el cuerpo de un reintento de `/leer` o `/leer-extracto`, o `null` si no trae
 * cuerpo. Lee con tope. Lanza [FallaDeLaClaveDelPdf] (400) si el cuerpo no se entiende o pesa de más,
 * sin repetir lo que llegó.
 */
internal suspend fun ApplicationCall.claveDelReintento(): ClaveDelPdf? {
    val leidos = receiveChannel().readRemaining(MAX_BYTES_DEL_CUERPO_CON_CLAVE + 1L).readByteArray()
    if (leidos.size > MAX_BYTES_DEL_CUERPO_CON_CLAVE) {
        throw FallaDeLaClaveDelPdf(HttpStatusCode.BadRequest, "No entendimos la clave que llegó.")
    }
    return claveDelCuerpo(leidos.decodeToString())
}

/**
 * Lo que se le contesta a una [FallaDeLaClaveDelPdf]: el código a la app que lo sabe mostrar
 * (`?conClave=1`); a un APK viejo, la frase de siempre ([viejo]).
 */
internal suspend fun ApplicationCall.contestarLaFallaDeLaClave(e: FallaDeLaClaveDelPdf, conClave: Boolean, viejo: String) {
    when {
        e.frase != null -> respond(e.status, e.frase)
        conClave || e.status == HttpStatusCode.BadRequest -> respond(e.status, e.codigo)
        else -> respond(HttpStatusCode.UnprocessableEntity, viejo)
    }
}
