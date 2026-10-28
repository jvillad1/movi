package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import com.jvillada.movi.shared.model.renderableSections
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.PARA_REVISAR_COMPACTO
import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.ui.components.ScrollDesdeLosMargenes
import com.jvillada.movi.ui.components.SubirAlRetocarLaPestana
import com.jvillada.movi.ui.components.ANCHO_DE_UNA_COLUMNA
import com.jvillada.movi.ui.dashboard.inicioEnDosColumnas
import com.jvillada.movi.ui.dashboard.repartoEnDosColumnas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import com.jvillada.movi.ui.components.CardRow
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.estiloDeLaCifra
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.LinkFigure
import com.jvillada.movi.ui.dashboard.dashboardAlerts
import com.jvillada.movi.ui.dashboard.dueLabel
import com.jvillada.movi.ui.dashboard.overBudgetCategories
import com.jvillada.movi.ui.dashboard.quickLinkFigure
import com.jvillada.movi.ui.recurrentes.textoDelMonto
import com.jvillada.movi.ui.dashboard.upcomingPaymentsWithin
import com.jvillada.movi.ui.dashboard.visibleSections
import com.jvillada.movi.ui.dashboard.TIPOS_DE_WIDGET
import com.jvillada.movi.ui.dashboard.bloquesDelInicio
import com.jvillada.movi.ui.dashboard.elHeroePideLaCuenta
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie

/** La guía de primeros pasos cuando el Inicio va en dos columnas: arriba de la izquierda. */
const val TAG_GUIA_EN_LA_COLUMNA_IZQUIERDA: String = "inicio-guia-en-la-columna-izquierda"

/**
 * Pinta una [ScreenDefinition] del Inicio. Consume `visibleSections(definition, data)`:
 * los tipos desconocidos y las acciones inválidas ya vienen filtrados de
 * `renderableSections` (core), y las secciones de datos que no tienen nada que mostrar
 * (Próximos pagos, Alertas) ya vienen descartadas — acá no se vuelve a validar nada.
 *
 * [header] es el chrome nativo que va arriba del todo y scrollea con el resto (la guía de
 * primeros pasos); no viaja en el schema a propósito — así existe siempre, sin depender de
 * `screen_definitions`.
 *
 * ### Una columna en el teléfono, dos en escritorio
 *
 * Generación 8. El ancho decide ([inicioEnDosColumnas]): por debajo de ~900 dp, una columna en el
 * orden de la definición —el del teléfono—, con el ancho de la columna de lectura (720 dp desde la
 * Ola W1, el mismo que cualquier otra pantalla); desde ahí, dos columnas parejas (vuelta 39: cada bloque a la más corta, ver [repartoEnDosColumnas]). En
 * escritorio el Inicio era una tira de 500 px en el medio de un lienzo de 1.400. El umbral mira el
 * ancho que le deja la cáscara (ya sin el rail), y el tope de ese ancho es el de un
 * `Disposicion.Tablero`: 840 dp en una ventana mediana (una columna) y 1.280 en escritorio.
 *
 * **Ya no es una `LazyColumn`**: dos columnas que scrollean juntas no caben en una lista perezosa, y
 * el Inicio son a lo sumo ocho bloques —no una lista de trescientos movimientos—, así que componerlo
 * entero no cuesta nada. Tiene además una ventaja buscada: un bloque ya no sale y entra de la
 * composición al hacer scroll, y su entrada animada no tiene cómo repetirse.
 */
@Composable
fun SduiRenderer(
    definition: ScreenDefinition,
    data: DashboardData,
    modifier: Modifier = Modifier,
    onNavigate: (Screen) -> Unit,
    header: (@Composable () -> Unit)? = null,
    // El hero, vacío, abre la MISMA hoja de crear cuenta que «Primeros pasos» —
    // ese estado (`showCreateSheet`) vive en `DashboardScreen`, no acá, así que solo se pasa el
    // callback. Default sin operación: el resto de las pantallas que montan `SduiRenderer` en las
    // pruebas no necesitan wirearlo.
    onShowCreateSheet: () -> Unit = {},
    // Lo que va al final del scroll, debajo de la última sección: «Editar inicio» (Ola 3). Chrome
    // nativo como [header], fuera del schema a propósito: así existe en cualquier definición.
    footer: (@Composable () -> Unit)? = null,
    // «Ya lo vi» del cierre del período, que ahora es una tarjeta de «Movi propone hoy»: el estado
    // (`data`) vive en `DashboardScreen`.
    onDescartarCierre: (String) -> Unit = {},
    // Vuelta 39: algo cambió desde el Inicio mismo (un movimiento de «Últimos movimientos» editado o
    // anulado en su hoja): `DashboardScreen` vuelve a pedir las cifras.
    onRecargar: () -> Unit = {},
    // Vuelta 39: la banda amarilla detrás de lo de arriba (el Inicio de la opción 3). La pinta el
    // scroll, así que se va con él; el encabezado, fijo arriba, lleva el mismo amarillo.
    conBanda: Boolean = false,
) {
    val uriHandler = LocalUriHandler.current
    val sections = visibleSections(definition, data)
    val acciones = AccionesDelRenderer(
        onNavigate, onShowCreateSheet, onDescartarCierre, uriHandler, onRecargar,
        // Con «Próximo pago» en la definición, «Movi propone hoy» no repite el pago.
        conElPagoEnLasPropuestas = renderableSections(definition).none { it.type == "PROXIMO_PAGO" },
    )
    val colorDeLaBanda = Movi.colores.marca
    val scroll = rememberScrollState()
    // La rueda del mouse sobre los márgenes mueve esta pantalla (ver [ScrollDesdeLosMargenes]).
    ScrollDesdeLosMargenes(scroll)
    // Re-tocar «Hoy» estando en Hoy vuelve arriba (ver [SubirAlRetocarLaPestana]).
    SubirAlRetocarLaPestana(scroll)

    // Vuelta de diseño 11: sin cuentas, el héroe ES el llamado a crear la primera («Aquí vas a ver tu
    // plata»), así que va primero y la guía va debajo de él (sin ese paso, ver `PrimerosPasosCard`).
    // Con la guía arriba, el Inicio de alguien nuevo decía «Crea tu primera cuenta» dos veces seguidas.
    val guiaDebajoDelHeroe = header != null && elHeroePideLaCuenta(data) && sections.any { it.type == "HERO_BALANCE" }
    val guiaArriba = header.takeUnless { guiaDebajoDelHeroe }
    val guiaDespuesDelHeroe = header.takeIf { guiaDebajoDelHeroe }

    BoxWithConstraints(modifier = modifier) {
        val bloques = bloquesDelInicio(sections)
        val sonDos = inicioEnDosColumnas(bloques.size, maxWidth)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                // Después del scroll: la banda se dibuja en el contenido y sube con él.
                .then(if (conBanda) Modifier.bandaDelInicio(colorDeLaBanda) else Modifier)
                .padding(bottom = Movi.espacios.seccion),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (sonDos) {
                // Vuelta 39: dos columnas parejas. El héroe (y la guía, si va arriba) a la izquierda; los
                // demás bloques, a la columna más corta (`repartoEnDosColumnas`), y la derecha arranca
                // debajo de la banda: la cuadrícula nunca queda sobre el amarillo.
                val fijos = buildSet {
                    if (guiaArriba != null) add(0)
                    val heroe = bloques.indexOfFirst { b -> b.any { it.type == "HERO_BALANCE" } }
                    if (heroe >= 0) add(heroe + if (guiaArriba != null) 1 else 0)
                }
                DosColumnasParejas(
                    fijosALaIzquierda = fijos,
                    arribaDeLaDerecha = if (conBanda) ALTO_DE_LA_BANDA_EN_EL_SCROLL + Medida.dentroDeSeccion else 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (guiaArriba != null) {
                        Box(Modifier.fillMaxWidth().testTag(TAG_GUIA_EN_LA_COLUMNA_IZQUIERDA)) { guiaArriba() }
                    }
                    bloques.forEach { bloque ->
                        Box(Modifier.fillMaxWidth()) {
                            BloqueDelInicio(bloque, data, acciones, guiaDespuesDelHeroe, conBanda = false)
                        }
                    }
                }
            } else {
                // En una columna, la guía va arriba con el ancho de la columna.
                if (guiaArriba != null) {
                    Box(Modifier.widthIn(max = ANCHO_DE_UNA_COLUMNA)) { guiaArriba() }
                    Spacer(Modifier.height(Movi.espacios.margen))
                }
                ColumnaDeSecciones(
                    bloques, data, acciones,
                    Modifier.fillMaxWidth().widthIn(max = ANCHO_DE_UNA_COLUMNA),
                    guiaDespuesDelHeroe,
                    // Con la guía arriba, el héroe ya no arranca en lo alto de la banda.
                    conBanda = conBanda && guiaArriba == null,
                )
            }
            if (footer != null) {
                Spacer(Modifier.height(Movi.espacios.seccion))
                Box(Modifier.fillMaxWidth().widthIn(max = ANCHO_DE_UNA_COLUMNA)) { footer() }
            }
        }
    }
}

/**
 * Lo que la banda amarilla ocupa dentro del scroll: [Medida.altoDeLaBandaDelInicio] menos el encabezado,
 * que va fijo arriba en el mismo amarillo. Hasta la mitad de la tarjeta héroe, más o menos.
 */
val ALTO_DE_LA_BANDA_EN_EL_SCROLL: Dp = Medida.altoDeLaBandaDelInicio - Medida.altoDeEncabezado

/**
 * **Dos columnas parejas** (vuelta 39): cada hijo de [contenido] es un bloque; se miden todos al ancho
 * de una columna y [repartoEnDosColumnas] decide de qué lado va cada uno. Entre bloques
 * [Medida.entreSecciones]; entre columnas [Medida.margenDePantalla] (las secciones ya traen su margen).
 */
@Composable
private fun DosColumnasParejas(
    fijosALaIzquierda: Set<Int>,
    arribaDeLaDerecha: Dp,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    Layout(content = contenido, modifier = modifier.testTag(TAG_DOS_COLUMNAS_DEL_INICIO)) { medibles, limites ->
        val ancho = limites.maxWidth / 2
        val medidos = medibles.map { it.measure(Constraints(minWidth = ancho, maxWidth = ancho)) }
        val separacion = Medida.entreSecciones.roundToPx()
        val lados = repartoEnDosColumnas(
            altos = medidos.map { it.height },
            fijosALaIzquierda = fijosALaIzquierda,
            arribaDeLaDerecha = arribaDeLaDerecha.roundToPx(),
            separacion = separacion,
        )
        var yIzquierda = 0
        var yDerecha = arribaDeLaDerecha.roundToPx()
        var hayIzquierda = false
        var hayDerecha = false
        val posiciones = medidos.mapIndexed { i, medido ->
            if (lados[i]) {
                if (medido.height > 0 && hayIzquierda) yIzquierda += separacion
                val y = yIzquierda
                if (medido.height > 0) { yIzquierda += medido.height; hayIzquierda = true }
                0 to y
            } else {
                if (medido.height > 0 && hayDerecha) yDerecha += separacion
                val y = yDerecha
                if (medido.height > 0) { yDerecha += medido.height; hayDerecha = true }
                ancho to y
            }
        }
        layout(limites.maxWidth, maxOf(yIzquierda, if (hayDerecha) yDerecha else 0)) {
            medidos.forEachIndexed { i, medido -> medido.place(posiciones[i].first, posiciones[i].second) }
        }
    }
}

/** Las dos columnas del Inicio en pantalla ancha. */
const val TAG_DOS_COLUMNAS_DEL_INICIO: String = "inicio-dos-columnas"

/** Lo que una sección puede pedirle a la pantalla, junto, para no pasarlo de a uno por cada nivel. */
private class AccionesDelRenderer(
    val onNavigate: (Screen) -> Unit,
    val onShowCreateSheet: () -> Unit,
    val onDescartarCierre: (String) -> Unit,
    val uriHandler: UriHandler,
    val onRecargar: () -> Unit,
    val conElPagoEnLasPropuestas: Boolean,
)

/** El tag del scroll del Inicio cuando lleva la banda amarilla (vuelta 39). */
const val TAG_BANDA_DEL_INICIO: String = "inicio-banda"

/**
 * **La banda amarilla del Inicio** (vuelta 39, la opción 3), dibujada detrás del contenido del scroll:
 * lo que le falta de [Medida.altoDeLaBandaDelInicio] al encabezado (que va fijo arriba, en el mismo
 * amarillo), con [Radio.xxl] en las esquinas de abajo. La tarjeta héroe se monta encima: la banda le
 * llega a la mitad. Va a todo el ancho del Inicio, nunca detrás del rail.
 */
private fun Modifier.bandaDelInicio(color: androidx.compose.ui.graphics.Color): Modifier =
    this.testTag(TAG_BANDA_DEL_INICIO).drawBehind {
        val alto = (Medida.altoDeLaBandaDelInicio - Medida.altoDeEncabezado).toPx()
        val radio = Radio.xxl.toPx().coerceAtMost(alto)
        drawRect(color, size = Size(size.width, alto - radio))
        drawRoundRect(color, size = Size(size.width, alto), cornerRadius = CornerRadius(radio, radio))
    }

/**
 * Una columna de secciones, con [com.jvillada.movi.theme.Medida.entreSecciones] entre bloques: el
 * Inicio se lee de a una pregunta por vez. Los widgets seguidos («Falta por pagar», «En qué se va») van
 * juntos en un bloque, lado a lado (ver [bloquesDelInicio]).
 *
 * [despuesDelHeroe] va justo debajo del héroe, como un bloque más (la guía de primeros pasos sin
 * cuentas); en una columna sin héroe no se pinta.
 */
@Composable
private fun ColumnaDeSecciones(
    bloques: List<List<ScreenSection>>,
    data: DashboardData,
    acciones: AccionesDelRenderer,
    modifier: Modifier,
    despuesDelHeroe: (@Composable () -> Unit)? = null,
    conBanda: Boolean = false,
) {
    Column(modifier = modifier) {
        bloques.forEachIndexed { index, bloque ->
            if (index > 0) Spacer(Modifier.height(Medida.entreSecciones))
            BloqueDelInicio(bloque, data, acciones, despuesDelHeroe, conBanda)
        }
    }
}

/**
 * Un bloque del Inicio: una sección, o dos widgets lado a lado. El del héroe lleva debajo
 * [despuesDelHeroe] (la guía sin cuentas) y, con [conBanda], **mide al menos lo que la banda**: lo que
 * viene después —la cuadrícula, con su círculo amarillo— arranca siempre sobre el crema (vuelta 39).
 */
@Composable
private fun BloqueDelInicio(
    bloque: List<ScreenSection>,
    data: DashboardData,
    acciones: AccionesDelRenderer,
    despuesDelHeroe: (@Composable () -> Unit)?,
    conBanda: Boolean,
) {
    val esElHeroe = bloque.any { it.type == "HERO_BALANCE" }
    Column(modifier = if (esElHeroe && conBanda) Modifier.heightIn(min = ALTO_DE_LA_BANDA_EN_EL_SCROLL) else Modifier) {
        if (bloque.all { it.type in TIPOS_DE_WIDGET }) {
            WidgetsDelInicio(bloque.map { it.type }, data, acciones.onNavigate)
        } else {
            SduiSection(bloque.single(), data, acciones)
        }
        if (despuesDelHeroe != null && esElHeroe) {
            Spacer(Modifier.height(Medida.entreSecciones))
            despuesDelHeroe()
        }
    }
}

@Composable
private fun SduiSection(
    section: ScreenSection,
    data: DashboardData,
    acciones: AccionesDelRenderer,
) {
    val onNavigate = acciones.onNavigate
    val uriHandler = acciones.uriHandler
    when (section.type) {
        // El héroe contesta «¿cuánto me queda?» y nada más (ola R1, ver `HeroDeUnVistazo`). El tipo no
        // cambia: es lo único que un APK viejo entiende, y él sigue pintando su héroe de siempre.
        "HERO_BALANCE" -> HeroDeUnVistazo(section, data, onNavigate, acciones.onShowCreateSheet)
        // Generación 9 (ola R1): la cuadrícula, «Movi propone hoy» y el carrusel de cuentas.
        "ACCIONES" -> AccionesDelInicioSection(data, onNavigate)
        "PROPUESTAS" -> PropuestasDelInicioSection(data, onNavigate, acciones.onDescartarCierre, acciones.conElPagoEnLasPropuestas)
        // Generación 10 (vuelta 39): el próximo pago y los últimos movimientos.
        "PROXIMO_PAGO" -> ProximoPagoSection(data, onNavigate)
        "ULTIMOS_MOVIMIENTOS" -> UltimosMovimientosSection(data, onNavigate, acciones.onRecargar)
        "CUENTAS" -> CuentasDelInicioSection(data, onNavigate)
        "PREGUNTALE_A_MOVI" -> PreguntaleAMoviSection(section, data, onNavigate)
        "PATRIMONIO" -> PatrimonioSection(section, data, onNavigate)
        "UPCOMING_PAYMENTS" -> UpcomingPaymentsSection(section, data, onNavigate)
        // El tipo sigue llamándose ALERTS para que un APK viejo pinte algo, pero lo que muestra es
        // «Para revisar». Con PROPUESTAS en la definición, este cliente no lo pinta (`visibleSections`).
        "ALERTS" -> ParaRevisarSection(section, data, onNavigate)
        PARA_REVISAR_COMPACTO -> ParaRevisarCompactoSection(section, data, onNavigate)
        // Sueltos, sin el otro al lado (el Editor de pantallas los separó): el widget a todo el ancho.
        "CHECKLIST_DEL_PERIODO", "GASTO_POR_CATEGORIA" -> WidgetsDelInicio(listOf(section.type), data, onNavigate)
        "DISPONIBLE_DEL_PERIODO" -> DisponibleDelPeriodoSection(section, data, onNavigate = onNavigate)
        "QUICK_LINKS_WITH_TOTALS" -> QuickLinksSection(section, data, onNavigate, uriHandler)
        "CARD_ROW" -> CardRowSection(section, onNavigate, uriHandler)
        "CARD_LIST" -> CardListSection(section, onNavigate, uriHandler)
        "LINK_LIST" -> LinkListSection(section, onNavigate, uriHandler)
        "BANNER" -> BannerSection(section, onNavigate, uriHandler)
    }
}

// ── Action dispatch ─────────────────────────────────────────────────────────────────

/**
 * NAVIGATE target → [Screen], cubriendo los targets de `ScreenTaxonomy.NAVIGATE_TARGETS`.
 * `renderableSections` ya strippeó cualquier NAVIGATE fuera de esa lista, así que el `else`
 * es defensivo — `target` es un String, no un sealed type, y el `when` no puede ser exhaustivo.
 *
 * `internal` y no `private`: es una función pura sin nada de Compose, y los remapeos (`"goals"`,
 * `"investments"`, `"subscriptions"`, `"extractos"`, …) se prueban directo, sin montar una
 * pantalla — ver `SduiRendererTest`.
 */
internal fun screenForTarget(target: String): Screen? = when (target) {
    "dashboard" -> Screen.Dashboard
    "transactions" -> Screen.Transactions()
    "quickadd" -> Screen.QuickAdd()
    // Ola C: Presupuestos es un segmento de Plan.
    "budgets" -> Screen.Plan(SEGMENTO_PRESUPUESTOS)
    "mas" -> Screen.Mas
    "accounts" -> Screen.Accounts
    "credits" -> Screen.Credits
    // Ola B, tarea 7: Metas salió de la navegación (fuera de Más, fuera de Perfil) y no tiene
    // reemplazo con forma propia — a diferencia de Suscripciones/Recurrentes, acá no quedó un
    // «donde ahora viven las metas». Mismo trato que "investments" un poco más abajo: el target
    // sobrevive (una definición guardada puede seguir trayéndolo) y se manda a Cuentas en vez de
    // crashear con un destino desconocido. `MetasScreen` se borró en la R4.
    "goals" -> Screen.Accounts
    // F61: Inversiones dejó de ser pantalla. Una definición guardada (o el Editor) puede seguir
    // trayendo este target — se manda a Cuentas, que es donde ahora viven las cuentas de
    // inversión. Nunca un crash por destino desconocido.
    "investments" -> Screen.Accounts
    // Ola 8: Suscripciones dejó de ser pantalla, igual que Inversiones. Mismo trato: el target
    // sobrevive (el acceso «Suscripciones» del Inicio ya está guardado en la DB de cada
    // instalación) y se manda a donde ahora viven las suscripciones.
    //
    // Ola C: ese «donde» es Plan · Pagos del mes (antes Movimientos con el chip «Recurrentes»).
    // Los dos targets **siguen existiendo** —el editor de pantallas los ofrece y hay definiciones
    // guardadas que los usan— y por eso se remapean en vez de borrarse: un target que deja de
    // resolver es un acceso del Inicio que no hace nada al tocarlo.
    "subscriptions" -> Screen.Plan(SEGMENTO_PAGOS)
    "recurrentes" -> Screen.Plan(SEGMENTO_PAGOS)
    "categorias" -> Screen.Categorias
    // Ola B, tarea 7: Extractos se unió a Documentos («Importar movimientos» vive en cada fila
    // de PDF o imagen). Mismo trato que "goals": el target sobrevive y se manda a donde el papel
    // ahora vive.
    "extractos" -> Screen.Documentos
    "aichat" -> Screen.AIChat()
    // Vuelta de diseño 3: «Perfil» se mudó dentro de Ajustes. El target sobrevive (una definición
    // guardada puede traerlo) y lleva a Ajustes, donde ahora vive todo lo que tenía.
    "profile" -> Screen.Mas
    else -> null
}

private fun performAction(action: ScreenAction, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    when (action.type) {
        "NAVIGATE" -> screenForTarget(action.target)?.let(onNavigate)
        "OPEN_URL" -> uriHandler.openUri(action.target)
    }
}

private fun clickHandler(
    action: ScreenAction?,
    onNavigate: (Screen) -> Unit,
    uriHandler: UriHandler,
): (() -> Unit)? = action?.let { { performAction(it, onNavigate, uriHandler) } }

// ── UPCOMING_PAYMENTS — lo que vence en los próximos 7 días (reglas + cuotas) ──────

@Composable
private fun UpcomingPaymentsSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit) {
    val rows = upcomingPaymentsWithin(data.upcoming.orEmpty())
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(
            title = section.title ?: "Próximos pagos",
            action = "Ver todos",
            // Ola C: «todos» son los de Plan · Pagos del mes — no la lista completa de
            // movimientos: quien toca esto viene de mirar un pago que vence y tiene que aterrizar
            // en algo que hable de eso.
            onAction = { onNavigate(Screen.Plan(SEGMENTO_PAGOS)) },
        )
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Column(Modifier.padding(horizontal = Medida.rellenoDeSuperficie)) {
                rows.forEachIndexed { i, p ->
                    // F20: la regla sintética de una tarjeta (card_) también se gestiona en Créditos.
                    val isCredit = p.rule.id.startsWith(CREDIT_RULE_PREFIX) || p.rule.id.startsWith(CARD_RULE_PREFIX)
                    val urgent = p.daysUntil <= 0
                    CardRow(
                        left = { Text(p.rule.name, style = Movi.textos.titulo, color = Movi.colores.texto) },
                        sub = dueLabel(p.daysUntil),
                        // Una tarjeta no tiene cuota: su monto es el SALDO. Mostrarlo bajo «Próximos
                        // pagos» anunciaba $27.501.150 como el próximo pago del dueño cuando el mínimo
                        // ronda el 5 %. Se muestra el saldo, dicho con su nombre y en gris — no como
                        // la cifra que va a salir de su cuenta. Ver `RecurringRule.montoEsSaldo`.
                        right = {
                            // El TEXTO lo decide `textoDelMonto` (una sola función para los cuatro
                            // renderers); el estilo lo elige cada pantalla: un saldo va en gris y más
                            // chico porque no es una cifra que vaya a salir de la cuenta.
                            if (p.rule.montoEsSaldo) {
                                Cifra(textoDelMonto(p.rule), Movi.textos.apoyoMedio, color = Movi.colores.textoApagado)
                            } else {
                                Cifra(textoDelMonto(p.rule), estiloDeLaCifra(VarianteDeCifra.Fila, Movi.textos), color = if (urgent) Movi.colores.sale else Movi.colores.texto)
                            }
                        },
                        isLast = i == rows.lastIndex,
                        // Una cuota de crédito se gestiona en Créditos; una regla, en Plan · Pagos del
                        // mes — que es donde ahora vive su «¿ya ocurrió?».
                        onClick = {
                            onNavigate(if (isCredit) Screen.Credits else Screen.Plan(SEGMENTO_PAGOS))
                        },
                    )
                }
            }
        }
    }
}

// ── ALERTS — solo cuando hay algo; cada fila lleva a donde se resuelve ─────────────

@Composable
private fun AlertsSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit) {
    val alerts = dashboardAlerts(
        overBudgetCategories(data.budgets, data.spentByCategory), data.cardCandidates, data.pendingSms,
        data.captura, data.capturaSilenciada, data.bancosMudos,
    )
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(title = section.title ?: "Alertas", count = alerts.size)
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Column(Modifier.padding(horizontal = Medida.rellenoDeSuperficie)) {
                alerts.forEachIndexed { i, alert ->
                    CardRow(
                        left = { Text(alert.text, style = Movi.textos.titulo, color = Movi.colores.aviso) },
                        showChevron = true,
                        isLast = i == alerts.lastIndex,
                        onClick = { onNavigate(alert.target) },
                    )
                }
            }
        }
    }
}

// ── QUICK_LINKS_WITH_TOTALS — accesos con la cifra de su destino (lo que era Análisis) ─

@Composable
private fun QuickLinksSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        section.title?.let { MinSectionHeader(title = it) }
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Column(Modifier.padding(horizontal = Medida.rellenoDeSuperficie)) {
                section.cards.forEachIndexed { i, card ->
                    val target = card.action?.takeIf { it.type == "NAVIGATE" }?.target
                    val figure = target?.let { quickLinkFigure(it, data) } ?: LinkFigure()
                    CardRow(
                        left = { Text(card.title, style = Movi.textos.titulo, color = Movi.colores.texto) },
                        // El subtítulo escrito en el Editor manda sobre el calculado.
                        sub = card.subtitle ?: figure.sub,
                        right = figure.value?.let { value ->
                            { Cifra(value, estiloDeLaCifra(VarianteDeCifra.Fila, Movi.textos), color = if (figure.isAlert) Movi.colores.sale else Movi.colores.texto) }
                        },
                        showChevron = card.action != null,
                        isLast = i == section.cards.lastIndex,
                        onClick = clickHandler(card.action, onNavigate, uriHandler),
                    )
                }
            }
        }
    }
}

// ── Generic sections: CARD_ROW / CARD_LIST / LINK_LIST / BANNER ────────────────────
// `imageUrl` is intentionally ignored in v1 — movi has no coil/kamel image-loader
// dependency (verified via grep), so cards render title/subtitle/badge only.

@Composable
private fun CardRowSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column {
        section.title?.let {
            Box(Modifier.padding(horizontal = Movi.espacios.amplio)) { MinSectionHeader(title = it) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Medida.margenDePantalla),
            horizontalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
        ) {
            items(section.cards) { card -> SduiCardTile(card, clickHandler(card.action, onNavigate, uriHandler)) }
        }
    }
}

@Composable
private fun CardListSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        section.title?.let { MinSectionHeader(title = it) }
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Column(Modifier.padding(horizontal = Medida.rellenoDeSuperficie)) {
                section.cards.forEachIndexed { i, card ->
                    CardRow(
                        left = { Text(card.title, style = Movi.textos.titulo, color = Movi.colores.texto) },
                        sub = card.subtitle,
                        right = card.badge?.let { badge -> { Text(badge, style = Movi.textos.apoyo, color = Movi.colores.textoMedio) } },
                        showChevron = card.action != null,
                        isLast = i == section.cards.size - 1,
                        onClick = clickHandler(card.action, onNavigate, uriHandler),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkListSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        section.title?.let {
            MinSectionHeader(title = it)
        }
        Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
            Column(Modifier.padding(horizontal = Medida.rellenoDeSuperficie)) {
                section.cards.forEachIndexed { i, card ->
                    CardRow(
                        left = { Text(card.title, style = Movi.textos.titulo, color = Movi.colores.texto) },
                        showChevron = true,
                        isLast = i == section.cards.size - 1,
                        onClick = clickHandler(card.action, onNavigate, uriHandler),
                    )
                }
            }
        }
    }
}

/**
 * CRITICAL: `ScreenSection` has no `action` field. The seed puts the AI banner's NAVIGATE
 * action in `cards[0].action` (see DashboardDefaults.kt), so a BANNER is clickable exactly
 * when it has a card carrying an action — read from there, not from any section-level field.
 */
@Composable
private fun BannerSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    val action = section.cards.firstOrNull()?.action
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        section.title?.let { MinSectionHeader(title = it) }
        Superficie(
            modifier = Modifier.fillMaxWidth(),
            relleno = RellenoDeSuperficie.Ninguno,
            onClick = clickHandler(action, onNavigate, uriHandler),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Medida.rellenoDeSuperficie, vertical = Espacio.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    section.text?.let { Text(it, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio) }
                }
                if (action != null) ChevronRight()
            }
        }
    }
}

@Composable
private fun SduiCardTile(card: ScreenCard, onClick: (() -> Unit)?) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Superficie(modifier = Modifier.width(150.dp).then(clickModifier)) {
        Text(card.title, style = Movi.textos.cuerpo, color = Movi.colores.texto, maxLines = 2)
        card.subtitle?.let {
            Spacer(Modifier.height(Espacio.xxs))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 2)
        }
        card.badge?.let {
            Spacer(Modifier.height(Espacio.xs))
            Text(it, style = Movi.textos.rotulo, color = Movi.colores.enlace)
        }
    }
}
