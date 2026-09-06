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
// mismo trato que «Ingresos»/«Gastos»/«Flujo del mes»), y hasta la generación 4 esta lista se
// quedó sin cambiar ni un byte por esa misma razón.
//
// Generación 5: sale la sección «Explora» (QUICK_LINKS_WITH_TOTALS). El dueño, viendo el Inicio:
// «no le veo mucho sentido a la sección de Explora si es lo mismo que veo en el menú». Tenía
// razón — sus cinco accesos (Cuentas, Créditos, Presupuestos, Metas, Recurrentes) ya son
// destinos de primera clase en el rail/bottom-nav y en «Más» (`MinNavRail`, `MasScreen`): no hay
// ahí ni un destino que no se pueda alcanzar desde el menú. Lo que pedía en su lugar —«que me
// traiga cosas a revisar o sugerencias»— ya existe: la sección ALERTS, justo arriba, que solo se
// pinta cuando hay algo que de verdad avisar (ver `dashboardAlerts`); su screenshot no tenía
// ninguna alerta pendiente en ese momento, por eso no la vio.
//
// Esta SÍ necesita subir la generación: a diferencia del rótulo del hero, quitar una sección
// entera de la lista es un cambio que solo el seed puede propagar — el fallback cliente (esta
// misma función) y la fila ya guardada en `screen_definitions` tienen que quedar iguales para
// que el Inicio del dueño, ya sembrado en generación 4, la deje de mostrar después del deploy
// (ver `seedScreens`: una fila con `seed_version < version` se reemplaza completa).
const val DASHBOARD_LAYOUT_VERSION = 5

fun defaultDashboardDefinition(): ScreenDefinition = ScreenDefinition(
    slug = "dashboard",
    version = DASHBOARD_LAYOUT_VERSION,
    sections = listOf(
        ScreenSection(type = "HERO_BALANCE", title = "Balance neto"),  // rótulo inerte: el renderer usa HERO_BALANCE_TITLE
        ScreenSection(type = "UPCOMING_PAYMENTS", title = "Próximos pagos"),
        ScreenSection(type = "ALERTS", title = "Alertas"),
        // Sin el "✦" que llevaba antes: en la web salía como ▯ (la fuente no tiene el glifo),
        // mismo problema que la Ola 2 arregló en los íconos de texto.
        ScreenSection(
            type = "BANNER",
            text = "Pregúntale a Movi AI",
            cards = listOf(ScreenCard(title = "Pregúntale a Movi AI", action = ScreenAction("NAVIGATE", "aichat"))),
        ),
    ),
)
