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
// Generación 9: el Inicio del rediseño (ola R1, 26-oct-2026: el camino 1 «Noche premium», con la
// cuadrícula del camino 3 y el feed del camino 5). Cada bloque contesta una sola pregunta y una cifra
// que vive en otra pantalla no se repite acá:
//
// - HERO_BALANCE: «Te quedan», para cuántos días y cuánto da por día. La barra Entró/Salió y el desglose
//   de «Tu plata» salen (viven en Plan y en Patrimonio).
// - ACCIONES: la cuadrícula de lo que se hace desde el Inicio (anotar, traspasar, pagar, subir un
//   comprobante, revisar avisos, preguntarle a Movi). Reemplaza a PREGUNTALE_A_MOVI: preguntar es una
//   acción más.
// - PROPUESTAS: «Movi propone hoy», una tarjeta por cosa que pide decisión. Junta lo que estaba en
//   «Para revisar», las alertas y la tarjeta del cierre.
// - CUENTAS: el carrusel de las cuentas con el color de su banco. Lleva a Patrimonio.
// - CHECKLIST_DEL_PERIODO y GASTO_POR_CATEGORIA, compactos, lado a lado («Falta por pagar», «En qué
//   se va»): los widgets que el dueño elige en «Editar inicio».
//
// DISPONIBLE_DEL_PERIODO y PATRIMONIO **salen de la lista**: repetían Plan y Patrimonio. Siguen
// disponibles en «Editar inicio» como secciones opcionales (ver `SECCIONES_OPCIONALES`): el server las
// agrega a la definición del dueño que las prende.
//
// ALERTS y BANNER se quedan al final **solo para los APK viejos**: un APK de la generación 8 no conoce
// ACCIONES, PROPUESTAS ni CUENTAS (`renderableSections` los descarta en silencio) y ve hero, falta por
// pagar, en qué se va, «Para revisar» y el acceso a Movi AI —su Inicio de siempre, sin Disponible ni
// patrimonio, que siguen a un toque en Plan y Patrimonio—. Este cliente no pinta ALERTS cuando la misma
// definición trae PROPUESTAS, ni el BANNER de Movi AI cuando trae ACCIONES (ver `visibleSections`).
//
// Generación 10: el Inicio de la opción 3 (vuelta 39, 28-oct-2026: el dueño eligió la opción 3 «Acción
// primero» y las 8 acciones). Entran dos tipos:
//
// - PROXIMO_PAGO, debajo de la cuadrícula: el pago más urgente del período (vencido primero, luego el
//   que vence antes: `pagosEnOrdenDeUrgencia`, la misma regla del widget «Falta por pagar» y de la
//   cola) con «Anotar pago». «Movi propone hoy» deja de repetirlo cuando la definición lo trae (ver
//   `visibleSections` en el cliente).
// - ULTIMOS_MOVIMIENTOS, debajo de «Movi propone hoy»: las tres filas más recientes de Actividad, con
//   «Ver toda la actividad».
//
// Un APK de la generación 9 no los conoce y los descarta en silencio: ve el Inicio de siempre, con el
// próximo pago dentro de su «Movi propone hoy» (su binario lo sigue proponiendo). La cuadrícula de 8 no
// cambia la definición: los rótulos y los destinos de ACCIONES viajan en el binario.
const val DASHBOARD_LAYOUT_VERSION = 10

fun defaultDashboardDefinition(): ScreenDefinition = ScreenDefinition(
    slug = "dashboard",
    version = DASHBOARD_LAYOUT_VERSION,
    sections = listOf(
        ScreenSection(type = "HERO_BALANCE", title = "Balance neto"),  // rótulo inerte: el renderer usa HERO_BALANCE_TITLE
        ScreenSection(type = "ACCIONES", title = "Acciones"),
        // Generación 10 (vuelta 39): el próximo pago y los últimos movimientos.
        ScreenSection(type = "PROXIMO_PAGO", title = "Próximo pago"),
        ScreenSection(type = "PROPUESTAS", title = "Movi propone hoy"),
        ScreenSection(type = "ULTIMOS_MOVIMIENTOS", title = "Últimos movimientos"),
        ScreenSection(type = "CUENTAS", title = "Tus cuentas"),
        ScreenSection(type = "CHECKLIST_DEL_PERIODO", title = "Falta por pagar"),
        ScreenSection(type = "GASTO_POR_CATEGORIA", title = "En qué se va"),
        // Solo para los APK anteriores a la generación 9: el cliente nuevo no lo pinta cuando hay
        // PROPUESTAS. Ver el comentario de la generación 9, arriba.
        ScreenSection(type = "ALERTS", title = "Para revisar"),
        // Solo para los APK anteriores a la generación 8 (y a la 9, que ya no traen PREGUNTALE_A_MOVI):
        // el cliente nuevo lo esconde cuando hay ACCIONES, que ya tiene «Preguntar a Movi».
        // Sin el "✦" que llevaba antes: en la web salía como ▯ (la fuente no tiene el glifo),
        // mismo problema que la Ola 2 arregló en los íconos de texto.
        ScreenSection(
            type = "BANNER",
            text = "Pregúntale a Movi AI",
            cards = listOf(ScreenCard(title = "Pregúntale a Movi AI", action = ScreenAction("NAVIGATE", "aichat"))),
        ),
    ),
)
