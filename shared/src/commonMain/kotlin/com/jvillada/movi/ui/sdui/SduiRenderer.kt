package com.jvillada.movi.ui.sdui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.LinkFigure
import com.jvillada.movi.ui.dashboard.cuentasDelHero
import com.jvillada.movi.ui.dashboard.dashboardAlerts
import com.jvillada.movi.ui.dashboard.dueLabel
import com.jvillada.movi.ui.dashboard.heroBalance
import com.jvillada.movi.ui.dashboard.heroBalanceTitle
import com.jvillada.movi.ui.dashboard.patrimonioExplicacion
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

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (header != null) {
            item {
                header()
                Spacer(Modifier.height(20.dp))
            }
        }
        sections.forEachIndexed { index, section ->
            item {
                if (index > 0) Spacer(Modifier.height(20.dp))
                SduiSection(section, data, onNavigate, uriHandler)
            }
        }
    }
}

@Composable
private fun SduiSection(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    uriHandler: UriHandler,
) {
    when (section.type) {
        "HERO_BALANCE" -> HeroBalanceSection(section, data, onNavigate)
        "UPCOMING_PAYMENTS" -> UpcomingPaymentsSection(section, data, onNavigate)
        "ALERTS" -> AlertsSection(section, data, onNavigate)
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
 */
private fun screenForTarget(target: String): Screen? = when (target) {
    "dashboard" -> Screen.Dashboard
    "transactions" -> Screen.Transactions()
    "quickadd" -> Screen.QuickAdd()
    "budgets" -> Screen.Budgets
    "mas" -> Screen.Mas
    "accounts" -> Screen.Accounts
    "credits" -> Screen.Credits
    "goals" -> Screen.Goals
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
    "extractos" -> Screen.Extractos
    "aichat" -> Screen.AIChat
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

// ── HERO_BALANCE — tu plata arriba, el patrimonio debajo, y el flujo del mes ───────

/**
 * El número grande es **lo que tienes** ([HeroBalance.tuPlata]); el **patrimonio neto** queda
 * debajo, secundario pero visible.
 *
 * Antes el número grande era el patrimonio bajo el rótulo «Balance neto». El dueño cargó su
 * primer crédito y reportó «me descontó de la cuenta todo el saldo del crédito»: no había tal
 * descuento —la deuda vive en su propia cuenta y no entra al flujo de caja—, pero la cifra de
 * portada saltó de +$20,3M a −$28,7M sin nada que lo explicara. El dato era correcto y aun así
 * ilegible. Ver [heroBalance] para qué cuenta como «tu plata» y por qué.
 *
 * El patrimonio **no se esconde**: con los cinco créditos del dueño (~$1.505M) es la foto
 * honesta de su situación. Se muestra con tres cuidados para que se entienda en vez de asustar:
 * - solo cuando de verdad difiere de «tu plata» — o sea con deudas **o** con plata condicionada
 *   (ver [HeroBalance.muestraPatrimonio]); si coincide repetiría el número de arriba, y esta
 *   tarjeta ya compite con las filas «Cuentas» y «Créditos» de EXPLORA;
 * - **en gris, no en rojo** — el rojo de esta tarjeta está reservado al «Flujo del mes», que es
 *   el resultado del mes y algo sobre lo que se puede actuar hoy; un patrimonio negativo por
 *   hipotecas es una estructura de largo plazo, no una pérdida de este mes. La pantalla de
 *   Cuentas pinta ESTE MISMO número y sigue la misma regla, porque tocar la línea lleva ahí:
 *   ver gris acá y rojo a 28 sp un toque después se leería como que algo empeoró en el camino;
 * - con la resta escrita debajo («Tu plata menos $1.505,1M en deudas»), que es justamente lo
 *   que faltaba el día del reporte.
 *
 * Tocar esa línea abre Cuentas, cuyo hero es el mismo «PATRIMONIO NETO» desglosado en Activos
 * y Deudas.
 */
@Composable
private fun HeroBalanceSection(section: ScreenSection, data: DashboardData, onNavigate: (Screen) -> Unit) {
    val balance = heroBalance(data.accounts.orEmpty())
    val ingresos = data.summary?.ingresos ?: 0L
    val egresos = data.summary?.egresos ?: 0L
    val flujo = ingresos - egresos

    MinCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Movi.espacios.amplio),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(22.dp),
    ) {
        // `section.title` NO se lee acá: el rótulo del hero es [HERO_BALANCE_TITLE], que viaja
        // en el binario. Ver su KDoc — es la única forma de que cada cliente rotule lo que él
        // mismo calcula, sin ventana de desalineación con la fila del server.
        Text(
            text = heroBalanceTitle(section),
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(10.dp))
        // Antes de que las cuentas contesten, un «$0» de 44 sp es la afirmación más fuerte que
        // hace esta pantalla, y es falsa mientras carga: en la web (sin caché que sobreviva a
        // recargar) el dueño veía «Tu plata $0» durante segundos. Un guion no miente.
        // **El número más visible de la app**, y hasta acá el que peor se veía: 44 sp de
        // monoespaciada, que a ese tamaño no es un dato, es una terminal. `Movi.textos.cifra`
        // son 42 sp semibold con cifras tabulares — el mismo alineado, sin el disfraz.
        Text(
            text = if (data.accounts == null) "—" else formatCOP(balance.tuPlata), // formatCOP ya trae el signo (F36) — no duplicarlo acá
            style = Movi.textos.cifra,
            // Una cuenta en descubierto SÍ es una alarma del día: eso se queda en rojo.
            color = if (balance.tuPlata < 0) Movi.colores.sale else Movi.colores.texto,
        )
        // La plata condicionada, dicha con su condición.
        //
        // El dueño: «esa plata no la tengo disponible; la de Skandia es dinero que deberías
        // referenciar en patrimonio pero no mostrarle como disponible en mi balance, sino como un
        // dinero disponible CONDICIONADO a uso en Vivienda». Tenía razón: «Tu plata» decía
        // $137.625.167 cuando podía disponer de $31.625.167.
        //
        // Va debajo de la cifra grande y antes del patrimonio, porque es lo que explica la resta
        // entre las dos: sale de «Tu plata» pero sigue contando en lo que vale.
        if (balance.condicionado > 0L) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = balance.condicionadoA
                    ?.let { "Además ${formatMoneyCompact(balance.condicionado)} solo para $it" }
                    ?: "Además ${formatMoneyCompact(balance.condicionado)} de uso condicionado",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
            )
        }
        // El dueño, viendo esta tarjeta: «realmente me gustaría ver no el total sino el
        // disponible en cada cuenta allí listado» — no el agregado por grupo que había acá
        // antes («Dinero $X · Inversión $Y»). La cifra grande de arriba no cambia: sigue siendo
        // la suma; esto es el desglose que explica de qué está hecha.
        //
        // `null` = las cuentas todavía no contestaron: no se afirma una lista (mismo criterio
        // que el «—» de la cifra grande, unas líneas arriba). Vacía tampoco pinta nada — no hay
        // nada que desglosar.
        val cuentas = cuentasDelHero(data.accounts)
        if (!cuentas.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                cuentas.forEach { cuenta ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = cuenta.nombre,
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(Movi.espacios.corto))
                        Cifra(cuenta.monto, 11.5f, color = Movi.colores.textoMedio)
                    }
                }
            }
        }
        if (balance.muestraPatrimonio) {
            Spacer(Modifier.height(16.dp))
            Hairline()
            // La explicación va DEBAJO de la fila, a ancho completo, y no como sub-línea de la
            // etiqueta: en un teléfono de 375 px compartir el renglón con la cifra la partía en
            // «Tu plata menos $1.505,1M en / deudas», y la resta —que es todo el punto de esta
            // línea— dejaba de leerse de un vistazo. Verificado a ojo en 375×812.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(Screen.Accounts) }
                    .padding(top = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Patrimonio neto",
                        modifier = Modifier.weight(1f),
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                    )
                    Cifra(
                        formatMoneyCompact(balance.patrimonio),
                        15f,
                        color = Movi.colores.texto,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(Movi.espacios.minimo))
                Text(
                    text = patrimonioExplicacion(balance),
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoApagado,
                )
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Spacer(Modifier.height(18.dp))
        }
        Hairline()
        Spacer(Modifier.height(16.dp))
        // Las tres cifras chicas salen del MISMO resumen que el número grande de arriba, así que
        // siguen su misma regla: sin respuesta, un guion. Antes decían «$0 / $0 / $0» al lado del
        // «—» recién puesto arriba, que es la peor combinación posible — parece que la app sabe.
        val sinResumen = data.summary == null
        fun cifra(v: Long) = if (sinResumen) "—" else formatMoneyCompact(v)
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                // **Ingresos en verde, gastos en coral.** Antes las tres cifras eran del mismo
                // gris y el color de plata quedaba reservado al caso malo: un mes en rojo. O sea
                // el color solo aparecía como alarma. Ahora cada cifra dice de qué lado está,
                // que es lo que los colores del sistema significan.
                Triple("Ingresos", cifra(ingresos), Movi.colores.entra),
                Triple("Gastos", cifra(egresos), Movi.colores.sale),
                // F36: un mes en rojo se ve en rojo. Y uno en verde, en verde.
                Triple(
                    "Flujo del mes",
                    cifra(flujo),
                    when {
                        sinResumen -> Movi.colores.texto
                        flujo < 0 -> Movi.colores.sale
                        else -> Movi.colores.entra
                    },
                ),
            ).forEach { (label, value, color) ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                    Spacer(Modifier.height(Movi.espacios.minimo + 2.dp))
                    Cifra(value, 14.5f, color = color)
                }
            }
        }
    }
}

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
                            Cifra(textoDelMonto(p.rule), 12.5f, color = Movi.colores.textoApagado)
                        } else {
                            Cifra(textoDelMonto(p.rule), 14.5f, color = if (urgent) Movi.colores.sale else Movi.colores.texto)
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
                        { Cifra(value, 14.5f, color = if (figure.isAlert) Movi.colores.sale else Movi.colores.texto) }
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
