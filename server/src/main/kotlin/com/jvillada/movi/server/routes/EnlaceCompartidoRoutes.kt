package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.compartir.PaginaCompartida
import com.jvillada.movi.server.compartir.TokenDeEnlace
import com.jvillada.movi.server.compartir.resumenCompartidoDe
import com.jvillada.movi.server.db.EnlacesCompartidos
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.ALCANCES_DE_ENLACE
import com.jvillada.movi.shared.model.EnlaceCompartido
import com.jvillada.movi.shared.model.EnlaceCompartidoCreado
import com.jvillada.movi.shared.model.NuevoEnlaceCompartido
import com.jvillada.movi.shared.model.VIGENCIAS_DE_ENLACE
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * # Compartir con un tercero
 *
 * El dueño quiere mostrarle su situación a Caro o a un asesor sin darle su contraseña ni mandar
 * capturas. Esto es un enlace de solo lectura, con vencimiento y revocable, que abre una página
 * liviana servida por acá (ver `compartir/PaginaCompartida.kt`).
 *
 * ## Las rutas
 *
 * | Ruta | Quién | Qué |
 * |---|---|---|
 * | `POST /api/enlaces-compartidos` | el dueño | crea un enlace; **la única respuesta que trae el token** |
 * | `GET /api/enlaces-compartidos` | el dueño | los suyos vigentes, sin token |
 * | `DELETE /api/enlaces-compartidos/{id}` | el dueño | lo revoca en el acto |
 * | `GET /compartido` | cualquiera | la cáscara de la página, igual para todos |
 * | `POST /compartido` | quien tenga el token | el resumen, con el token en el **cuerpo** |
 *
 * ## El token nunca viaja en la URL que llega al server
 *
 * `CallLogging` imprime el método y la ruta de cada petición, y no es el único: el borde de Railway
 * tiene su propio registro de peticiones, que no controlamos. Un enlace con el token en la ruta
 * (`/compartido/abc…`) o en la consulta (`?t=abc…`) quedaría escrito en los dos, y cualquiera que
 * lea esos registros tendría la capacidad de ver la plata del dueño.
 *
 * Había dos salidas. **Filtrar el log** —un `format {}` en `CallLogging` que tache la ruta— arregla
 * el nuestro y deja intacto el de Railway, y además hay que acordarse de mantenerlo. **Poner el
 * token en el fragmento** (`/compartido#abc…`) lo resuelve por construcción: el navegador *nunca*
 * manda el fragmento al server —es una regla de HTTP, no una promesa nuestra—, así que no hay log,
 * propio ni ajeno, que pueda verlo. Tampoco sale en un `Referer`. Se eligió la segunda.
 *
 * El precio es que la página necesita un script: la cáscara lee `location.hash` y hace un `POST`
 * con el token en el cuerpo, y los cuerpos no se registran. Es un `fetch` de doce líneas, sin
 * dependencias, autorizado por su hash en la política de contenido. `EnlaceCompartidoRoutesTest`
 * fija que el token no aparece en ninguna línea de log de una vista completa.
 *
 * ## Vencido, revocado o inventado: la misma respuesta
 *
 * Un 404 con el mismo cuerpo, byte por byte, en los tres casos (y si el token ni siquiera tiene la
 * forma de uno nuestro). Distinguirlos le diría a quien prueba tokens que acertó uno que existió, o
 * le confirmaría a alguien que el enlace que encontró sí era de verdad. A quien lo recibió de buena
 * fe, «pídele uno nuevo» le sirve igual en los tres casos.
 */
fun Route.enlaceCompartidoRoutes() {
    post("/api/enlaces-compartidos") {
        val uid = call.userId()
        val pedido = call.receive<NuevoEnlaceCompartido>()
        if (pedido.dias !in VIGENCIAS_DE_ENLACE) {
            return@post call.respond(HttpStatusCode.BadRequest, "Un enlace puede valer 1, 7 o 30 días.")
        }
        val alcance = pedido.alcance.trim().lowercase()
        if (alcance !in ALCANCES_DE_ENLACE) {
            return@post call.respond(HttpStatusCode.BadRequest, "No se puede compartir «${pedido.alcance}».")
        }

        val ahora = System.currentTimeMillis()
        val token = TokenDeEnlace.nuevo()
        val enlace = EnlaceCompartido(
            id = UUID.randomUUID().toString(),
            alcance = alcance,
            creadoEn = ahora,
            venceEn = ahora + pedido.dias * UN_DIA_MS,
        )
        val creado = dbQuery {
            val vigentes = EnlacesCompartidos.selectAll()
                .where { vigentesDe(uid, ahora) }
                .count()
            if (vigentes >= MAX_ENLACES_VIGENTES) return@dbQuery false
            EnlacesCompartidos.insert {
                it[id] = enlace.id
                it[userId] = uid
                it[tokenHash] = TokenDeEnlace.hashDelToken(token)
                it[EnlacesCompartidos.alcance] = enlace.alcance
                it[creadoEn] = enlace.creadoEn
                it[venceEn] = enlace.venceEn
            }
            true
        }
        if (!creado) {
            return@post call.respond(
                HttpStatusCode.Conflict,
                "Ya tienes $MAX_ENLACES_VIGENTES enlaces vigentes. Revoca alguno antes de crear otro.",
            )
        }
        call.respond(HttpStatusCode.Created, EnlaceCompartidoCreado(enlace = enlace, ruta = "$RUTA_PUBLICA#$token"))
    }

    get("/api/enlaces-compartidos") {
        val uid = call.userId()
        val ahora = System.currentTimeMillis()
        val lista = dbQuery {
            EnlacesCompartidos.selectAll()
                .where { vigentesDe(uid, ahora) }
                .map { it.toEnlace() }
                .sortedByDescending { it.creadoEn }
        }
        call.respond(lista)
    }

    delete("/api/enlaces-compartidos/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        val ahora = System.currentTimeMillis()
        // Solo los suyos y solo los que siguen vigentes: el de otro usuario contesta 404 igual que
        // uno que no existe, así que esta ruta no sirve para averiguar ids ajenos.
        val revocados = dbQuery {
            EnlacesCompartidos.update({
                (EnlacesCompartidos.id eq id) and vigentesDe(uid, ahora)
            }) { it[revocadoEn] = ahora }
        }
        if (revocados == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * La página pública. Va FUERA del bloque autenticado: quien la abre no tiene —ni puede tener— una
 * sesión de Movi. Ver el KDoc de [enlaceCompartidoRoutes].
 */
fun Route.paginaCompartidaRoutes() {
    get(RUTA_PUBLICA) {
        call.conCabecerasDePrivacidad()
        call.respondText(PaginaCompartida.cascara, ContentType.Text.Html.withCharset(Charsets.UTF_8))
    }

    post(RUTA_PUBLICA) {
        call.conCabecerasDePrivacidad()
        val html = ContentType.Text.Html.withCharset(Charsets.UTF_8)
        suspend fun noDisponible() = call.respondText(PaginaCompartida.noDisponible, html, HttpStatusCode.NotFound)

        // Un solo balde para todos: detrás del borde de Railway la IP que se ve es la del proxy
        // (ver AuthRoutes), así que un balde por IP sería uno solo de todos modos. Adivinar un
        // token de 256 bits es imposible con o sin límite; esto existe para que nadie pueda usar
        // la ruta para martillar la base.
        if (!RateLimiter.allow("compartido:global", maxAttempts = MAX_VISTAS_POR_MINUTO, windowMs = 60_000L)) {
            return@post call.respondText(
                "<div class=\"aviso\"><p class=\"marca\">Movi</p><h1>Demasiadas visitas seguidas</h1>" +
                    "<p class=\"apoyo\">Espera un minuto y vuelve a abrir el enlace.</p></div>",
                html,
                HttpStatusCode.TooManyRequests,
            )
        }

        // Un token son 43 caracteres. Un cuerpo más largo que esto no es un token, y no hay por qué
        // leerlo entero para saberlo.
        val anunciado = call.request.contentLength()
        if (anunciado != null && anunciado > MAX_CUERPO) return@post noDisponible()
        val token = call.receiveText().trim()
        if (!TokenDeEnlace.tieneForma(token)) return@post noDisponible()

        val hash = TokenDeEnlace.hashDelToken(token)
        val ahora = System.currentTimeMillis()
        val encontrado = dbQuery {
            val fila = EnlacesCompartidos.selectAll()
                .where { EnlacesCompartidos.tokenHash eq hash }
                .firstOrNull()
                ?: return@dbQuery null
            // La búsqueda ya fue por el hash; esto es el cinturón (ver TokenDeEnlace).
            if (!TokenDeEnlace.esElMismoHash(fila[EnlacesCompartidos.tokenHash], hash)) return@dbQuery null
            if (fila[EnlacesCompartidos.revocadoEn] != null) return@dbQuery null
            if (fila[EnlacesCompartidos.venceEn] <= ahora) return@dbQuery null
            EnlacesCompartidos.update({ EnlacesCompartidos.id eq fila[EnlacesCompartidos.id] }) {
                it[ultimaVista] = ahora
                it[vistas] = vistas + 1
            }
            fila[EnlacesCompartidos.userId] to fila[EnlacesCompartidos.venceEn]
        } ?: return@post noDisponible()

        val (uid, venceEn) = encontrado
        val resumen = resumenCompartidoDe(uid, venceEn)
        call.respondText(PaginaCompartida.contenido(resumen), html)
    }
}

/**
 * Lo que las dos respuestas de la página llevan siempre:
 *
 * - `Cache-Control: no-store` — ni el navegador ni un proxy guardan una copia de la plata de
 *   alguien; revocar tiene que cortar de verdad, no «cuando venza la caché».
 * - `Referrer-Policy: no-referrer` — si la página algún día enlaza a algo, no dice desde dónde.
 * - `X-Robots-Tag` — que ningún buscador la indexe aunque alguien pegue el enlace en un sitio
 *   público. Va además como `<meta>` en la página.
 * - `X-Content-Type-Options`, `X-Frame-Options` y la política de contenido — lo de siempre para
 *   una página que muestra datos: nadie la mete en un marco ni le inyecta un script.
 */
private fun ApplicationCall.conCabecerasDePrivacidad() {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header("Referrer-Policy", "no-referrer")
    response.header("X-Robots-Tag", "noindex, nofollow, noarchive")
    response.header("X-Content-Type-Options", "nosniff")
    response.header("X-Frame-Options", "DENY")
    response.header("Content-Security-Policy", PaginaCompartida.politicaDeContenido)
}

/** Los enlaces de [uid] que siguen sirviendo: ni revocados ni vencidos. */
private fun vigentesDe(uid: String, ahora: Long) =
    (EnlacesCompartidos.userId eq uid) and
        EnlacesCompartidos.revocadoEn.isNull() and
        (EnlacesCompartidos.venceEn greater ahora)

private fun ResultRow.toEnlace() = EnlaceCompartido(
    id = this[EnlacesCompartidos.id],
    alcance = this[EnlacesCompartidos.alcance],
    creadoEn = this[EnlacesCompartidos.creadoEn],
    venceEn = this[EnlacesCompartidos.venceEn],
    ultimaVista = this[EnlacesCompartidos.ultimaVista],
    vistas = this[EnlacesCompartidos.vistas],
)

/** Donde vive la página. El token va DESPUÉS del `#`, nunca en esta ruta. */
internal const val RUTA_PUBLICA = "/compartido"

private const val UN_DIA_MS = 24L * 60 * 60 * 1000

/**
 * Cuántos enlaces vigentes puede tener alguien a la vez. Veinte es mucho más de lo que hace falta
 * para Caro, un asesor y un par de repuestos; el tope existe para que un cliente con un bug no
 * llene la tabla.
 */
private const val MAX_ENLACES_VIGENTES = 20L

/** Vistas por minuto de la página, sumando todos los enlaces. Ver el comentario en la ruta. */
private const val MAX_VISTAS_POR_MINUTO = 120

/** Más que un token con holgura (43 caracteres), mucho menos que algo que valga la pena leer. */
private const val MAX_CUERPO = 256L
