package com.jvillada.movi.shared.model

/**
 * La definición del Inicio ("dashboard") tal como la siembra el server y tal como la pinta el
 * cliente cuando el server no responde. Vive en `:core` a propósito: es UNA sola lista, así el
 * seed de `ScreenSeed.kt` y el fallback de `DashboardScreen.kt` no pueden desalinearse (antes
 * eran dos copias "byte-idénticas" a mano).
 *
 * `version` es la *generación del layout*, no el contador de ediciones del Editor: el server
 * la usa para saber si la fila guardada todavía es de una generación anterior y hay que
 * reemplazarla (ver `seedScreens`). Subirla cada vez que cambie esta lista — si no, las
 * instalaciones ya desplegadas se quedan con el Inicio viejo.
 *
 * Ola 4 (F9/F40): generación 2 = Inicio de alto nivel. Balance neto + flujo del mes, próximos
 * pagos, alertas (solo cuando hay), accesos con cifra (lo que antes era Análisis) y Movi AI.
 */
const val DASHBOARD_LAYOUT_VERSION = 2

fun defaultDashboardDefinition(): ScreenDefinition = ScreenDefinition(
    slug = "dashboard",
    version = DASHBOARD_LAYOUT_VERSION,
    sections = listOf(
        ScreenSection(type = "HERO_BALANCE", title = "Balance neto"),
        ScreenSection(type = "UPCOMING_PAYMENTS", title = "Próximos pagos"),
        ScreenSection(type = "ALERTS", title = "Alertas"),
        ScreenSection(
            type = "QUICK_LINKS_WITH_TOTALS",
            title = "Explora",
            cards = listOf(
                ScreenCard(title = "Cuentas", action = ScreenAction("NAVIGATE", "accounts")),
                ScreenCard(title = "Créditos", action = ScreenAction("NAVIGATE", "credits")),
                ScreenCard(title = "Presupuestos", action = ScreenAction("NAVIGATE", "budgets")),
                ScreenCard(title = "Metas", action = ScreenAction("NAVIGATE", "goals")),
                ScreenCard(title = "Inversiones", action = ScreenAction("NAVIGATE", "investments")),
                ScreenCard(title = "Suscripciones", action = ScreenAction("NAVIGATE", "subscriptions")),
            ),
        ),
        // Sin el "✦" que llevaba antes: en la web salía como ▯ (la fuente no tiene el glifo),
        // mismo problema que la Ola 2 arregló en los íconos de texto.
        ScreenSection(
            type = "BANNER",
            text = "Pregúntale a Movi AI",
            cards = listOf(ScreenCard(title = "Pregúntale a Movi AI", action = ScreenAction("NAVIGATE", "aichat"))),
        ),
    ),
)
