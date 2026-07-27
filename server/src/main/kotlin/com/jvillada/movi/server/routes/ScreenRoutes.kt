package com.jvillada.movi.server.routes

import com.jvillada.movi.server.admin.AdminConfig
import com.jvillada.movi.server.db.Screens
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.screens.SCREEN_SEED
import com.jvillada.movi.server.screens.validateDefinition
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.vendors.ForUpdateOption

private val json = Json { ignoreUnknownKeys = true }

/**
 * GET /api/screens/{slug} -- ver docs/superpowers/specs/2026-07-26-sdui-movi-design.md.
 * 200 con la definición | 304 si If-None-Match == version | 404 si el slug no existe,
 * está inactivo, o su sections_json no deserializa (nunca 500 -- lección NeoVita).
 */
fun Route.screenRoutes() {
    route("/api/screens") {
        get("/{slug}") {
            val slug = call.parameters["slug"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing slug")

            val row = dbQuery {
                Screens.selectAll()
                    .where { (Screens.slug eq slug) and (Screens.active eq true) }
                    .singleOrNull()
            } ?: return@get call.respond(HttpStatusCode.NotFound)

            val version = row[Screens.version]

            val def = try {
                ScreenDefinition(
                    slug = row[Screens.slug],
                    version = version,
                    sections = json.decodeFromString<List<ScreenSection>>(row[Screens.sectionsJson]),
                )
            } catch (e: Exception) {
                call.application.log.warn("sections_json corrupto para slug=$slug, sirviendo 404", e)
                return@get call.respond(HttpStatusCode.NotFound)
            }

            val ifNoneMatch = call.request.headers[HttpHeaders.IfNoneMatch]
            if (ifNoneMatch == version.toString()) {
                return@get call.respond(HttpStatusCode.NotModified)
            }

            call.respond(def)
        }

        get("/admin/status") {
            call.respond(mapOf("isAdmin" to AdminConfig.isAdmin(call.userId())))
        }

        put("/{slug}") {
            val uid = call.userId()
            if (!AdminConfig.isAdmin(uid)) return@put call.respond(HttpStatusCode.Forbidden, "No autorizado")
            val slug = call.parameters["slug"] ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing slug")
            val body = call.receive<ScreenDefinition>()
            validateDefinition(body.sections)?.let {
                return@put call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to it))
            }
            val saved = dbQuery {
                // .forUpdate() bloquea la fila hasta el fin de la transacción: dos ediciones
                // concurrentes no pueden leer la misma versión y pisarse el contenido una a otra
                // manteniendo el mismo número de versión (la segunda espera a que la primera commitee).
                val current = Screens.selectAll().where { Screens.slug eq slug }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull()
                    ?: return@dbQuery null
                val newVersion = current[Screens.version] + 1
                Screens.update({ Screens.slug eq slug }) {
                    it[sectionsJson] = json.encodeToString(body.sections)
                    it[version] = newVersion
                    it[updatedAt] = System.currentTimeMillis()
                }
                ScreenDefinition(slug = slug, version = newVersion, sections = body.sections)
            } ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(saved)
        }

        post("/{slug}/restore") {
            val uid = call.userId()
            if (!AdminConfig.isAdmin(uid)) return@post call.respond(HttpStatusCode.Forbidden, "No autorizado")
            val slug = call.parameters["slug"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing slug")
            val seed = SCREEN_SEED.firstOrNull { it.slug == slug }
                ?: return@post call.respond(HttpStatusCode.NotFound)
            val saved = dbQuery {
                // Mismo lock de fila que en PUT /{slug}: evita que un restore concurrente con
                // otra edición/restore pisen el contenido mientras comparten número de versión.
                val current = Screens.selectAll().where { Screens.slug eq slug }
                    .forUpdate(ForUpdateOption.ForUpdate)
                    .singleOrNull()
                    ?: return@dbQuery null
                val newVersion = current[Screens.version] + 1
                Screens.update({ Screens.slug eq slug }) {
                    it[sectionsJson] = json.encodeToString(seed.sections)
                    it[version] = newVersion
                    it[updatedAt] = System.currentTimeMillis()
                }
                ScreenDefinition(slug = slug, version = newVersion, sections = seed.sections)
            } ?: return@post call.respond(HttpStatusCode.NotFound)
            call.respond(saved)
        }
    }
}
