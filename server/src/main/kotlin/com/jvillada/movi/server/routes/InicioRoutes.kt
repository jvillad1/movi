package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.InicioPersonalizado
import com.jvillada.movi.server.db.Screens
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.MAXIMO_DE_SECCIONES_EN_LA_PREFERENCIA
import com.jvillada.movi.shared.model.PreferenciasDelInicio
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.limpias
import com.jvillada.movi.shared.model.seccionesConfigurablesDe
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

private val json = Json { ignoreUnknownKeys = true }

/**
 * # «Editar inicio» (Ola 3 · Hoy en una mirada)
 *
 * - `GET /api/inicio/preferencias` → la [PreferenciasDelInicio] del dueño, con `ordenDeMovi` (las
 *   secciones configurables de la definición de Hoy, en el orden de Movi). Sin nada guardado, las dos
 *   listas vacías.
 * - `PUT /api/inicio/preferencias` → guarda la preferencia (limpia: solo tipos configurables, sin
 *   repetidos) y devuelve lo mismo que el GET. Una preferencia vacía borra la fila: es «Volver al
 *   orden de Movi». Idempotente.
 *
 * Quien la aplica es `GET /api/screens/dashboard` (ver [preferenciasDelInicioDe] y
 * `inicioConPreferencias` en `:core`): el orden y las ocultas viajan ya puestos en la definición.
 */
fun Route.inicioRoutes() {
    get("/api/inicio/preferencias") {
        val uid = call.userId()
        call.respond(respuestaDelInicio(preferenciasDelInicioDe(uid) ?: PreferenciasDelInicio()))
    }

    put("/api/inicio/preferencias") {
        val uid = call.userId()
        val pedida = runCatching { call.receive<PreferenciasDelInicio>() }.getOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Preferencia ilegible")
        if (pedida.orden.size > MAXIMO_DE_SECCIONES_EN_LA_PREFERENCIA ||
            pedida.ocultas.size > MAXIMO_DE_SECCIONES_EN_LA_PREFERENCIA
        ) {
            return@put call.respond(HttpStatusCode.BadRequest, "Demasiadas secciones")
        }
        val limpias = pedida.limpias()
        dbQuery {
            if (limpias.esLaDeMovi) {
                InicioPersonalizado.deleteWhere { InicioPersonalizado.userId eq uid }
            } else {
                InicioPersonalizado.upsert {
                    it[userId] = uid
                    it[orden] = json.encodeToString(limpias.orden)
                    it[ocultas] = json.encodeToString(limpias.ocultas)
                    it[visibles] = json.encodeToString(limpias.visibles)
                    it[actualizadoEn] = System.currentTimeMillis()
                }
            }
        }
        call.respond(respuestaDelInicio(limpias))
    }
}

/**
 * La preferencia guardada de [uid], o `null` si no eligió nada (o si la fila no se puede leer).
 *
 * **Nunca lanza**: la lee `GET /api/screens/dashboard`, que promete no contestar 500 (lección
 * NeoVita). Va en su propia transacción para que un error acá —una tabla que todavía no existe en
 * una base de prueba, una fila corrupta— no aborte la de la definición: sin preferencia, Hoy sale
 * como lo arma Movi, que es lo sensato.
 */
internal suspend fun preferenciasDelInicioDe(uid: String): PreferenciasDelInicio? = runCatching {
    dbQuery {
        InicioPersonalizado.selectAll()
            .where { InicioPersonalizado.userId eq uid }
            .singleOrNull()
            ?.let { fila ->
                PreferenciasDelInicio(
                    orden = json.decodeFromString<List<String>>(fila[InicioPersonalizado.orden]),
                    ocultas = json.decodeFromString<List<String>>(fila[InicioPersonalizado.ocultas]),
                    visibles = fila[InicioPersonalizado.visibles]?.let { json.decodeFromString<List<String>>(it) }.orEmpty(),
                ).limpias()
            }
    }
}.getOrNull()

/** [preferencias] con el orden de Movi de la definición de Hoy que está sirviendo el server. */
private suspend fun respuestaDelInicio(preferencias: PreferenciasDelInicio): PreferenciasDelInicio =
    preferencias.copy(ordenDeMovi = seccionesConfigurablesDe(definicionGlobalDelInicio()))

/**
 * La definición de Hoy **sin** preferencias, la misma para todos: la fila de `screen_definitions`, o
 * la de `:core` si la fila falta o no se puede leer.
 */
private suspend fun definicionGlobalDelInicio(): ScreenDefinition = runCatching {
    dbQuery {
        Screens.selectAll().where { Screens.slug eq "dashboard" }.singleOrNull()?.let { fila ->
            ScreenDefinition(
                slug = fila[Screens.slug],
                version = fila[Screens.version],
                sections = json.decodeFromString<List<ScreenSection>>(fila[Screens.sectionsJson]),
            )
        }
    }
}.getOrNull() ?: defaultDashboardDefinition()
