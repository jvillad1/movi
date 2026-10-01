package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.exportar.leerDatosDe
import com.jvillada.movi.server.exportar.zipDeLosDatos
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.plugins.versionVigenteDe
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.EnlaceDeDescarga
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant

/**
 * # «Descarga tus datos» — `GET /api/export`
 *
 * Un ZIP con todo lo de quien pide (ver `exportar/ExportarDatos.kt`). Dos puertas al mismo ZIP,
 * igual que los documentos:
 *
 * - `GET /api/export`, **autenticada** con el token de sesión en `Authorization`. Para un script o
 *   un `curl`.
 * - `POST /api/export/enlace` (autenticada) + `GET /api/export/descarga?t=…` (pública): lo que usa
 *   la app. Bajar un archivo en el navegador o en el teléfono es una navegación, y ahí no hay
 *   dónde poner `Authorization`; el permiso viaja en la URL, dura dos minutos y no sirve para nada
 *   más (otra audiencia). Ver `JwtConfig.makeExportToken`.
 */
fun Route.exportRoutes() {
    get("/api/export") {
        responderZip(call, call.userId())
    }

    post("/api/export/enlace") {
        val uid = call.userId()
        val version = versionVigenteDe(uid) ?: return@post call.respond(HttpStatusCode.NotFound)
        call.respond(
            EnlaceDeDescarga(
                url = "/api/export/descarga?t=${JwtConfig.makeExportToken(uid, version)}",
                expiraEn = System.currentTimeMillis() + JwtConfig.EXPORT_VALIDITY_MS,
            ),
        )
    }
}

/** La mitad pública: va FUERA del bloque autenticado, como `documentContentRoutes`. */
fun Route.exportDescargaRoutes() {
    get("/api/export/descarga") {
        val token = call.request.queryParameters["t"]
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "Falta el permiso de descarga")
        val (uid, version) = JwtConfig.verifyExportToken(token)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ENLACE_VENCIDO)
        // Si cerró las sesiones en todos los aparatos después de pedir el enlace, el enlace muere
        // con ellas: es la exportación ENTERA, no un documento.
        if (versionVigenteDe(uid) != version) {
            return@get call.respond(HttpStatusCode.Unauthorized, ENLACE_VENCIDO)
        }
        responderZip(call, uid)
    }
}

private const val ENLACE_VENCIDO = "El enlace venció. Vuelve a pedir la descarga desde Movi."

private suspend fun responderZip(call: ApplicationCall, uid: String) {
    val datos = dbQuery { leerDatosDe(uid) }
    val zip = zipDeLosDatos(uid, datos, Instant.now().toString())
    val hoy = AppClock.today()
    call.application.log.info("export: $uid bajó sus datos (${datos.sumOf { it.filas.size }} filas, ${zip.size} bytes)")
    // Que nada en el camino se quede con una copia: es la plata entera de alguien.
    call.response.header(HttpHeaders.CacheControl, "no-store")
    call.response.header("X-Content-Type-Options", "nosniff")
    call.response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment
            .withParameter(ContentDisposition.Parameters.FileName, "movi-datos-$hoy.zip")
            .toString(),
    )
    call.respondBytes(zip, ContentType("application", "zip"))
}
