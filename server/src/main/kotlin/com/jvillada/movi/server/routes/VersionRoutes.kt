package com.jvillada.movi.server.routes

import com.jvillada.movi.server.version.DeployedCommit
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cuerpo de `/version`. Un solo campo a propósito: es un endpoint sin autenticar sobre la
 * instancia real del dueño, así que no expone variables de entorno, rutas ni versiones de
 * dependencias. Un commit de un repo al que ya se tiene acceso no agrega superficie.
 */
@Serializable
private data class VersionResponse(val commit: String?)

private val versionJson = Json { encodeDefaults = true }

/**
 * `GET /version` → `{"commit":"<sha>"}` con 200, o `{"commit":null}` con 503.
 *
 * **Por qué `/version` en la raíz y no `/api/version`.** Tiene que poder consultarlo un script de
 * CI sin credenciales, igual que `/health`: todo lo que cuelga de `/api` en este server vive
 * detrás del bloque `authenticate("jwt")`, y un token en el workflow sería un secreto más que
 * administrar para leer un dato público. Tampoco se mete dentro de `/health`: ese es el
 * healthcheck de Railway (`railway.toml`), su contrato es un `OK` de texto plano y no se toca.
 *
 * **Por qué 503 cuando no se sabe.** El modo de falla que este endpoint viene a matar es el
 * silencio: si el proceso no conoce su commit, tiene que *decirlo*, no devolver vacío ni un
 * placeholder que parezca una respuesta. Un `null` en el cuerpo y un código que no es 2xx hacen
 * imposible que un script confunda «no lo sé» con «ya desplegó».
 *
 * Se serializa a mano por el mismo motivo que `apiNotFound`: no depender de que
 * ContentNegotiation esté instalado.
 */
fun Route.versionRoutes() {
    get("/version") {
        val sha = DeployedCommit.sha()
        call.respondText(
            text = versionJson.encodeToString(VersionResponse.serializer(), VersionResponse(sha)),
            contentType = ContentType.Application.Json,
            status = if (sha != null) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
        )
    }
}
