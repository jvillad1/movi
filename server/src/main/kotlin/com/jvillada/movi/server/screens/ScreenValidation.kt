package com.jvillada.movi.server.screens

import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.ScreenTaxonomy
import com.jvillada.movi.shared.model.renderableSections

/**
 * Valida una definición ANTES de persistirla: el editor no puede guardar algo que el
 * renderer no sepa dibujar. Cierra en el origen el escenario que en F1 dejaba el
 * dashboard en blanco (definición "válida" cuyas secciones se filtran todas).
 * Devuelve null si es válida, o el mensaje del primer problema encontrado.
 */
fun validateDefinition(sections: List<ScreenSection>): String? {
    if (sections.isEmpty()) return "La pantalla debe tener al menos una sección"
    for (s in sections) {
        if (s.type !in ScreenTaxonomy.SECTION_TYPES) {
            return "Tipo de sección desconocido: ${s.type}"
        }
        for (c in s.cards) {
            val a = c.action ?: continue
            actionError(a)?.let { return it }
        }
    }
    val renderable = renderableSections(ScreenDefinition(slug = "_", version = 0, sections = sections))
    if (renderable.isEmpty()) return "La pantalla no tiene secciones que se puedan mostrar"
    return null
}

private fun actionError(a: ScreenAction): String? = when (a.type) {
    "NAVIGATE" -> if (a.target in ScreenTaxonomy.NAVIGATE_TARGETS) null
                  else "Destino de navegación inválido: ${a.target}"
    "OPEN_URL" -> if (a.target.startsWith("https://")) null
                  else "Los enlaces deben empezar con https:// — recibido: ${a.target}"
    else -> "Tipo de acción desconocido: ${a.type}"
}
