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
// Generación 6: el Inicio pasa a ser el RESUMEN DEL PERÍODO. El dueño: «quiero que la home sea
// tipo un resumen del periodo … en qué categorías hice movimientos y en dónde se me fue la plata,
// qué me falta por pagar y qué ya pagué tipo checklist … que también tire insights de qué cosas
// debería revisar».
//
// Entran dos secciones nuevas —CHECKLIST_DEL_PERIODO y GASTO_POR_CATEGORIA— y sale
// UPCOMING_PAYMENTS, que contestaba otra pregunta («qué vence en siete días») y quedaría diciendo
// lo mismo que el checklist, dos veces.
//
// Las otras dos NO cambian de tipo a propósito, porque el tipo es lo único que un APK viejo
// entiende: HERO_BALANCE ahora encabeza con el rango del período y ALERTS se pinta como «Para
// revisar». Un teléfono sin actualizar sigue viendo su hero y sus alertas de siempre, y las dos
// secciones nuevas simplemente no le aparecen (ver `renderableSections`).
// Generación 7: entra DISPONIBLE_DEL_PERIODO, justo debajo de «Falta por pagar». El dueño: «en
// inicio sería genial algo tipo: Disponible en el periodo · por semana · por día». Va después del
// checklist porque lo que resta como «fijos» son exactamente esas filas: quien lee de arriba abajo
// ya vio qué es lo fijo antes de ver cuánto queda.
//
// Sube la generación porque es una sección nueva en la lista, y eso solo lo propaga el seed (ver
// Generación 5). Un APK viejo recibe la fila nueva y no pasa nada: el tipo no está en su
// `SECTION_TYPES`, `renderableSections` lo descarta y su `when` del renderer ni lo ve.
//
// Generación 8: el Inicio de un vistazo (entrega B de `2026-09-23-movi-de-un-vistazo-design.md`).
// El dueño: «cuando yo entre a la app pueda ver toda mi información financiera … de una manera muy
// simple, minimalista, clara … habilitando luego poder compartir … o hacer preguntas vía chat de
// IA». Con sus datos reales el Inicio mostraba doce cifras arriba del pliegue y dos veredictos
// opuestos («Te pasaste» en rojo y «Vas bien» un renglón abajo). Ahora contesta tres preguntas —¿cómo
// estoy?, ¿qué viene?, ¿en qué se va?— y entran dos tipos nuevos:
//
// - PREGUNTALE_A_MOVI, **segundo**, pegado al hero: tres preguntas armadas con los datos (sin
//   modelo, ver `preguntasSugeridas`) y el campo para escribir. Antes Movi AI era un banner al final.
// - PATRIMONIO, después de las categorías: lo que tienes contra lo que debes, con sus tramos. El
//   hero NUEVO deja de pintar el patrimonio; el viejo lo sigue pintando (el renderer viaja en el
//   binario), así que ningún cliente se queda sin él.
//
// **El BANNER de Movi AI se queda al final de la lista, a propósito.** Es lo que ve un APK
// anterior a esta generación: los dos tipos nuevos no están en su `SECTION_TYPES` y
// `renderableSections` los descarta, así que su Inicio queda EXACTAMENTE como el de la generación
// 7 —hero, falta por pagar, disponible, categorías, para revisar y el banner—. Sin el banner, ese
// teléfono se habría quedado sin ningún acceso a Movi AI desde el Inicio: peor que hoy. El cliente
// nuevo no lo pinta cuando la misma definición trae PREGUNTALE_A_MOVI (ver `visibleSections`):
// serían dos puertas al mismo chat, una arriba y otra abajo.
//
// El orden de la lista es el del teléfono. En escritorio el cliente la reparte en dos columnas por
// tipo (ver `columnasDelInicio`), conservando el orden dentro de cada una; eso NO viaja en la fila
// porque es disposición, no contenido.
const val DASHBOARD_LAYOUT_VERSION = 8

fun defaultDashboardDefinition(): ScreenDefinition = ScreenDefinition(
    slug = "dashboard",
    version = DASHBOARD_LAYOUT_VERSION,
    sections = listOf(
        ScreenSection(type = "HERO_BALANCE", title = "Balance neto"),  // rótulo inerte: el renderer usa HERO_BALANCE_TITLE
        ScreenSection(type = "PREGUNTALE_A_MOVI", title = "Pregúntale a Movi"),
        ScreenSection(type = "CHECKLIST_DEL_PERIODO", title = "Pagos del período"),
        ScreenSection(type = "DISPONIBLE_DEL_PERIODO", title = "Disponible"),
        ScreenSection(type = "GASTO_POR_CATEGORIA", title = "En qué se va"),
        ScreenSection(type = "PATRIMONIO", title = "Tu patrimonio"),
        ScreenSection(type = "ALERTS", title = "Para revisar"),
        // Solo para los APK anteriores a la generación 8: el cliente nuevo lo esconde cuando hay
        // PREGUNTALE_A_MOVI. Ver el comentario de la generación 8, arriba.
        // Sin el "✦" que llevaba antes: en la web salía como ▯ (la fuente no tiene el glifo),
        // mismo problema que la Ola 2 arregló en los íconos de texto.
        ScreenSection(
            type = "BANNER",
            text = "Pregúntale a Movi AI",
            cards = listOf(ScreenCard(title = "Pregúntale a Movi AI", action = ScreenAction("NAVIGATE", "aichat"))),
        ),
    ),
)
