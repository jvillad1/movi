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
// F61 (Ola 7): generación 3 = sale el acceso «Inversiones» del Inicio — Inversiones ya no es
// pantalla; sus cuentas se ven en Cuentas, que ya tiene su acceso acá.
// Ola 8: generación 4 = el acceso «Suscripciones» pasa a llamarse «Recurrentes» y apunta ahí.
// Suscripciones dejó de ser pantalla, y el rótulo viejo mandaba al dueño a una pantalla con otro
// nombre y otra cifra: tocaba «Suscripciones · $312.000» y aterrizaba en «Recurrentes · Flujo
// libre», dos números sin relación visible. Ahora el acceso muestra el MISMO flujo libre que la
// pantalla de destino (ver `quickLinkFigure("recurrentes")`).
// Ola 9: la generación se queda en 4 A PROPÓSITO, aunque el Inicio cambió de portada.
//
// El hero pasó a mostrar «Tu plata» (los activos) con el patrimonio debajo, así que el título
// guardado acá —«Balance neto»— dejó de describir la cifra grande. La reacción obvia era
// cambiarlo y subir la generación, y es la trampa: la fila de `screen_definitions` llega a
// TODOS los clientes en el instante del deploy, pero el renderer viaja en el binario. El APK ya
// instalado sigue pintando el patrimonio, y con la fila nueva lo habría titulado «Tu plata
// −$1.492.710.542» — la lectura exacta que la Ola 9 vino a evitar, ahora afirmada por el rótulo.
//
// Por eso el rótulo del hero se cableó en el renderer (`HERO_BALANCE_TITLE` en DashboardLogic.kt,
// mismo trato que «Ingresos»/«Gastos»/«Flujo del mes») y esta lista **no cambió ni un byte**:
// producción sigue en `version 4 = seed_version 4`, `seedScreens` no toca nada, y el título que
// queda abajo es dato inerte para HERO_BALANCE — se conserva para que la semilla siga siendo
// byte a byte lo que ya está desplegado, no porque alguien lo lea.
const val DASHBOARD_LAYOUT_VERSION = 4

fun defaultDashboardDefinition(): ScreenDefinition = ScreenDefinition(
    slug = "dashboard",
    version = DASHBOARD_LAYOUT_VERSION,
    sections = listOf(
        ScreenSection(type = "HERO_BALANCE", title = "Balance neto"),  // rótulo inerte: el renderer usa HERO_BALANCE_TITLE
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
                ScreenCard(title = "Recurrentes", action = ScreenAction("NAVIGATE", "recurrentes")),
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
