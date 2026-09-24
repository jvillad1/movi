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
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.ScreenAction
import com.jvillada.movi.shared.model.ScreenCard
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.renderableSections
import com.jvillada.movi.ui.components.ScrollDesdeLosMargenes
import com.jvillada.movi.ui.dashboard.ANCHO_DE_UNA_COLUMNA
import com.jvillada.movi.ui.dashboard.columnasDelInicio
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.transactions.CHIP_RECURRENTES
import com.jvillada.movi.ui.components.CardRow
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.Cifra
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
 * Generación 8. El ancho decide ([columnasDelInicio]): por debajo de ~900 dp, una columna en el
 * orden de la definición —el del teléfono—, con el ancho de siempre (600 dp); desde ahí, dos
 * columnas repartidas por tipo. En escritorio el Inicio era una tira de 500 px en el medio de un
 * lienzo de 1.400.
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
) {
    val uriHandler = LocalUriHandler.current
    val sections = visibleSections(definition, data)
    // Si la definición no trae la tarjeta del patrimonio (una fila guardada antes de la generación
    // 8), el hero lo dice en una línea: el patrimonio no puede desaparecer del Inicio en esa ventana.
    val conPatrimonioEnElHero = renderableSections(definition).none { it.type == "PATRIMONIO" }
    val scroll = rememberScrollState()
    // La rueda del mouse sobre los márgenes mueve esta pantalla (ver [ScrollDesdeLosMargenes]).
    ScrollDesdeLosMargenes(scroll)

    BoxWithConstraints(modifier = modifier) {
        val columnas = columnasDelInicio(sections, maxWidth)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(bottom = Movi.espacios.seccion),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (header != null) {
                Box(Modifier.widthIn(max = ANCHO_DE_UNA_COLUMNA)) { header() }
                Spacer(Modifier.height(Movi.espacios.margen))
            }
            if (columnas.sonDos) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    ColumnaDeSecciones(columnas.izquierda, data, conPatrimonioEnElHero, onNavigate, uriHandler, Modifier.weight(1f))
                    ColumnaDeSecciones(columnas.derecha, data, conPatrimonioEnElHero, onNavigate, uriHandler, Modifier.weight(1f))
                }
            } else {
                ColumnaDeSecciones(
                    columnas.izquierda, data, conPatrimonioEnElHero, onNavigate, uriHandler,
                    Modifier.fillMaxWidth().widthIn(max = ANCHO_DE_UNA_COLUMNA),
                )
            }
        }
    }
}

/** Una columna de secciones, con aire entre bloques: el Inicio se lee de a una pregunta por vez. */
@Composable
private fun ColumnaDeSecciones(
    sections: List<ScreenSection>,
    data: DashboardData,
    conPatrimonioEnElHero: Boolean,
    onNavigate: (Screen) -> Unit,
    uriHandler: UriHandler,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(Movi.espacios.seccion))
            SduiSection(section, data, conPatrimonioEnElHero, onNavigate, uriHandler)
        }
    }
}

@Composable
private fun SduiSection(
    section: ScreenSection,
    data: DashboardData,
    conPatrimonioEnElHero: Boolean,
    onNavigate: (Screen) -> Unit,
    uriHandler: UriHandler,
) {
    when (section.type) {
        // Generación 8: el hero contesta «¿cómo estoy?» y nada más (ver `HeroDeUnVistazo`). El tipo
        // no cambia: es lo único que un APK viejo entiende, y él sigue pintando su hero de siempre.
        "HERO_BALANCE" -> HeroDeUnVistazo(section, data, conPatrimonioEnElHero, onNavigate)
        "PREGUNTALE_A_MOVI" -> PreguntaleAMoviSection(section, data, onNavigate)
        "PATRIMONIO" -> PatrimonioSection(section, data, onNavigate)
        "UPCOMING_PAYMENTS" -> UpcomingPaymentsSection(section, data, onNavigate)
        // El tipo sigue llamándose ALERTS para que un APK viejo pinte algo, pero lo que muestra es
        // «Para revisar»: además de lo que está mal, dice qué hacer y lleva a donde se hace.
        "ALERTS" -> ParaRevisarSection(section, data, onNavigate)
        "CHECKLIST_DEL_PERIODO" -> ChecklistDelPeriodoSection(section, data, onNavigate)
        "DISPONIBLE_DEL_PERIODO" -> DisponibleDelPeriodoSection(section, data)
        "GASTO_POR_CATEGORIA" -> GastoPorCategoriaSection(section, data, onNavigate)
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
    "budgets" -> Screen.Budgets
    "mas" -> Screen.Mas
    "accounts" -> Screen.Accounts
    "credits" -> Screen.Credits
    // Ola B, tarea 7: Metas salió de la navegación (fuera de Más, fuera de Perfil) y no tiene
    // reemplazo con forma propia — a diferencia de Suscripciones/Recurrentes, acá no quedó un
    // «donde ahora viven las metas». Mismo trato que "investments" un poco más abajo: el target
    // sobrevive (una definición guardada puede seguir trayéndolo) y se manda a Cuentas en vez de
    // crashear con un destino desconocido. `MetasScreen` sigue existiendo, solo sin puerta.
    "goals" -> Screen.Accounts
    // F61: Inversiones dejó de ser pantalla. Una definición guardada (o el Editor) puede seguir
    // trayendo este target — se manda a Cuentas, que es donde ahora viven las cuentas de
    // inversión. Nunca un crash por destino desconocido.
    "investments" -> Screen.Accounts
    // Ola 8: Suscripciones dejó de ser pantalla, igual que Inversiones. Mismo trato: el target
    // sobrevive (el acceso «Suscripciones» del Inicio ya está guardado en la DB de cada
    // instalación) y se manda a donde ahora viven las suscripciones.
    //
    // PR 3 del rediseño de Recurrentes (2026-09): ese «donde» ya no es una pantalla aparte sino
    // Movimientos con el chip «Recurrentes» puesto. Los dos targets **siguen existiendo** —el
    // editor de pantallas los ofrece y hay definiciones guardadas que los usan— y por eso se
    // remapean en vez de borrarse: un target que deja de resolver es un acceso del Inicio que no
    // hace nada al tocarlo.
    "subscriptions" -> Screen.Transactions(CHIP_RECURRENTES)
    "recurrentes" -> Screen.Transactions(CHIP_RECURRENTES)
    "categorias" -> Screen.Categorias
    // Ola B, tarea 7: Extractos se unió a Documentos («Importar movimientos» vive en cada fila
    // de PDF o imagen). Mismo trato que "goals": el target sobrevive y se manda a donde el papel
    // ahora vive.
    "extractos" -> Screen.Documentos
    "aichat" -> Screen.AIChat()
    "profile" -> Screen.Profile
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
            // PR 3: «todos» ahora son los de Movimientos bajo el chip «Recurrentes» — con el
            // filtro puesto, no la lista completa: quien toca esto viene de mirar un pago que
            // vence y tiene que aterrizar en algo que hable de eso.
            onAction = { onNavigate(Screen.Transactions(CHIP_RECURRENTES)) },
        )
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
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
                            Cifra(textoDelMonto(p.rule), Movi.textos.apoyo, color = Movi.colores.textoApagado)
                        } else {
                            Cifra(textoDelMonto(p.rule), Movi.textos.monto, color = if (urgent) Movi.colores.sale else Movi.colores.texto)
                        }
                    },
                    isLast = i == rows.lastIndex,
                    // Una cuota de crédito se gestiona en Créditos; una regla, en Movimientos con
                    // el chip «Recurrentes» — que es donde ahora vive su «¿ya ocurrió?».
                    onClick = {
                        onNavigate(if (isCredit) Screen.Credits else Screen.Transactions(CHIP_RECURRENTES))
                    },
                )
            }
        }
    }
}

// ── ALERTS — solo cuando hay algo; cada fila lleva a donde se resuelve ─────────────

@Composable
private fun AlertsSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit) {
    val alerts = dashboardAlerts(
        overBudgetCategories(data.budgets, data.spentByCategory), data.cardCandidates, data.pendingSms,
        data.captura, data.capturaSilenciada,
    )
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(title = section.title ?: "Alertas", count = alerts.size)
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
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

// ── QUICK_LINKS_WITH_TOTALS — accesos con la cifra de su destino (lo que era Análisis) ─

@Composable
private fun QuickLinksSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        section.title?.let { MinSectionHeader(title = it) }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            section.cards.forEachIndexed { i, card ->
                val target = card.action?.takeIf { it.type == "NAVIGATE" }?.target
                val figure = target?.let { quickLinkFigure(it, data) } ?: LinkFigure()
                CardRow(
                    left = { Text(card.title, style = Movi.textos.titulo, color = Movi.colores.texto) },
                    // El subtítulo escrito en el Editor manda sobre el calculado.
                    sub = card.subtitle ?: figure.sub,
                    right = figure.value?.let { value ->
                        { Cifra(value, Movi.textos.monto, color = if (figure.isAlert) Movi.colores.sale else Movi.colores.texto) }
                    },
                    showChevron = card.action != null,
                    isLast = i == section.cards.lastIndex,
                    onClick = clickHandler(card.action, onNavigate, uriHandler),
                )
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
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.cards) { card -> SduiCardTile(card, clickHandler(card.action, onNavigate, uriHandler)) }
        }
    }
}

@Composable
private fun CardListSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        section.title?.let { MinSectionHeader(title = it) }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
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

@Composable
private fun LinkListSection(section: ScreenSection, onNavigate: (Screen) -> Unit, uriHandler: UriHandler) {
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        section.title?.let {
            MinSectionHeader(title = it)
        }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
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
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            onClick = clickHandler(action, onNavigate, uriHandler),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
    MinCard(
        modifier = Modifier.width(150.dp).then(clickModifier),
        variant = MinCardVariant.Default,
        padding = PaddingValues(14.dp),
    ) {
        Text(card.title, style = Movi.textos.cuerpo, color = Movi.colores.texto, maxLines = 2)
        card.subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, maxLines = 2)
        }
        card.badge?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = Movi.textos.rotulo, color = Movi.colores.marca)
        }
    }
}
