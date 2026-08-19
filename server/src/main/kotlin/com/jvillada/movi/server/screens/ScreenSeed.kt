package com.jvillada.movi.server.screens

import com.jvillada.movi.server.db.Screens
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

private val json = Json { ignoreUnknownKeys = true }

/**
 * Definición del slug "dashboard". Es la misma lista que el cliente usa de fallback
 * (`defaultDashboardDefinition()` en `:core`): una sola fuente, imposible que se desalineen.
 * El chrome (top bar, guía de primeros pasos, barra inferior) NO viaja en el schema — sigue
 * siendo nativo en el cliente.
 *
 * La acción del banner de IA no tiene un slot propio en ScreenSection (solo
 * type/title/cards/text), así que -- igual que cualquier CARD_ROW/CARD_LIST/LINK_LIST -- vive
 * en `cards[0].action`; el renderer trata un BANNER con cards no vacío como clickeable vía esa
 * acción.
 */
fun dashboardScreen(): ScreenDefinition = defaultDashboardDefinition()

val SCREEN_SEED: List<ScreenDefinition> = listOf(dashboardScreen())

/**
 * Siembra y actualiza `screen_definitions` POR SLUG:
 *
 * - Slug que no existe → se inserta (así un slug nuevo llega a instalaciones ya desplegadas,
 *   lección del deferido de NeoVita -- seedIfEmpty global se saltaba slugs nuevos en
 *   instalaciones con tabla no vacía).
 * - Slug que existe con `seed_version` menor que la generación del seed → se reemplaza el
 *   contenido por el seed nuevo y `version` sube (para que los clientes con If-None-Match
 *   refresquen). Así un cambio de layout (Ola 4: el Inicio de alto nivel) llega a producción
 *   sin tocar la base a mano. El costo asumido: una edición hecha desde el Editor sobre la
 *   generación vieja se pierde cuando entra una generación nueva — el Editor ajusta detalles
 *   de un layout, no sobrevive a un rediseño.
 * - Slug que existe con `seed_version` igual o mayor → no se toca. Una edición manual por SQL
 *   o por el Editor sobrevive a reinicios mientras no cambie la generación del seed.
 */
fun seedScreens(defs: List<ScreenDefinition> = SCREEN_SEED) = transaction {
    val existing = Screens.selectAll().associate { it[Screens.slug] to (it[Screens.version] to it[Screens.seedVersion]) }
    val now = System.currentTimeMillis()
    defs.forEach { def ->
        val current = existing[def.slug]
        if (current == null) {
            // Dos boots pueden correr esta selección-luego-inserción en paralelo (p.ej. dos
            // instancias arrancando a la vez); si el otro boot ya insertó este slug entre el
            // select y el insert, esto lanzaría una violación de PK que mataría el arranque
            // vía DatabaseFactory.init. runCatching degrada esa carrera a un no-op: "el otro
            // boot ya lo insertó".
            runCatching {
                Screens.insert {
                    it[slug] = def.slug
                    it[version] = def.version
                    it[sectionsJson] = json.encodeToString(def.sections)
                    it[active] = true
                    it[updatedAt] = now
                    it[seedVersion] = def.version
                }
            }.onFailure { e -> println("seedScreens: slug '${def.slug}' already inserted by a racing boot (${e.message})") }
        } else if (current.second < def.version) {
            val (storedVersion, _) = current
            Screens.update({ Screens.slug eq def.slug }) {
                it[sectionsJson] = json.encodeToString(def.sections)
                // Nunca menor que la generación: si la fila venía "atrasada" (v1 de una
                // instalación vieja) salta directo a la generación; si venía editada muchas
                // veces (v7), sube una más para que el If-None-Match del cliente no la confunda
                // con lo que ya tenía en caché.
                it[version] = maxOf(storedVersion + 1, def.version)
                it[updatedAt] = now
                it[seedVersion] = def.version
            }
            println("seedScreens: slug '${def.slug}' upgraded to seed generation ${def.version}")
        }
    }
}
