package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Screens
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll

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
    }
}
