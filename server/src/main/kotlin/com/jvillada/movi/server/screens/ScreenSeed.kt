package com.jvillada.movi.server.screens

import com.jvillada.movi.server.db.Screens
import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

private val json = Json { ignoreUnknownKeys = true }

/**
 * Definición inicial (v1) del slug "dashboard", transcrita del Dashboard hardcodeado
 * actual (shared/.../ui/dashboard/DashboardScreen.kt): tarjeta de Balance, Mis cuentas,
 * Alertas, Patrimonio (renombrado "Explora", con Suscripciones agregada como mejora
 * visible día 1) y el banner de Movi AI. El chrome (top bar, scope toggle, bottom nav)
 * NO viaja en el schema -- sigue siendo chrome nativo en el cliente (Task 3).
 *
 * La acción del banner de IA no tiene un slot propio en ScreenSection (solo
 * type/title/cards/text, ver ScreenDefinition.kt de Task 1), así que -- igual que
 * cualquier CARD_ROW/CARD_LIST/LINK_LIST -- vive en `cards[0].action`; el renderer
 * de Task 3 debe tratar un BANNER con cards no vacío como clickeable via esa acción.
 */
fun dashboardScreen(): ScreenDefinition = ScreenDefinition(
    slug = "dashboard",
    version = 1,
    sections = listOf(
        ScreenSection(type = "HERO_BALANCE"),
        ScreenSection(type = "ACCOUNTS_SUMMARY"),
        ScreenSection(type = "BANNER", title = "Alertas", text = "Sin alertas por ahora"),
        ScreenSection(
            type = "LINK_LIST",
            title = "Explora",
            cards = listOf(
                ScreenCard(title = "Inversiones", action = ScreenAction("NAVIGATE", "investments")),
                ScreenCard(title = "Créditos", action = ScreenAction("NAVIGATE", "credits")),
                ScreenCard(title = "Metas", action = ScreenAction("NAVIGATE", "goals")),
                ScreenCard(title = "Suscripciones", action = ScreenAction("NAVIGATE", "subscriptions")),
            ),
        ),
        ScreenSection(
            type = "BANNER",
            text = "✦ Pregúntale a Movi AI",
            cards = listOf(ScreenCard(title = "Pregúntale a Movi AI", action = ScreenAction("NAVIGATE", "aichat"))),
        ),
    ),
)

/**
 * Nota (ver task-2-report.md): el spec/plan mencionan "6 secciones" en la prosa, pero
 * la lista ordenada, VERBATIM, que ambos documentos enumeran tiene 5 elementos -- que
 * es también el número de secciones del Dashboard hardcodeado actual. Este seed
 * transcribe esa lista de 5 tal cual está escrita.
 */
val SCREEN_SEED: List<ScreenDefinition> = listOf(dashboardScreen())

/**
 * Inserta, POR SLUG, cada definición de [defs] que aún no exista en `screen_definitions`.
 * Nunca actualiza ni pisa una fila existente: una edición manual por SQL sobrevive a
 * reinicios del server, y un slug nuevo agregado al seed sí llega a instalaciones ya
 * desplegadas (lección del deferido de NeoVita -- seedIfEmpty global se saltaba slugs
 * nuevos en instalaciones con tabla no vacía).
 */
fun seedScreens(defs: List<ScreenDefinition> = SCREEN_SEED) = transaction {
    val existingSlugs = Screens.selectAll().map { it[Screens.slug] }.toSet()
    val now = System.currentTimeMillis()
    defs.filter { it.slug !in existingSlugs }.forEach { def ->
        // Dos boots pueden correr esta selección-luego-inserción en paralelo (p.ej. dos
        // instancias arrancando a la vez); si el otro boot ya insertó este slug entre el
        // select y el insert, esto lanzaría una violación de PK que mataría el arranque
        // vía DatabaseFactory.init. runCatching degrada esa carrera a un no-op: "el otro
        // boot ya lo insertó" -- nunca actualiza ni pisa la fila existente.
        runCatching {
            Screens.insert {
                it[slug] = def.slug
                it[version] = def.version
                it[sectionsJson] = json.encodeToString(def.sections)
                it[active] = true
                it[updatedAt] = now
            }
        }.onFailure { e -> println("seedScreens: slug '${def.slug}' already inserted by a racing boot (${e.message})") }
    }
}
