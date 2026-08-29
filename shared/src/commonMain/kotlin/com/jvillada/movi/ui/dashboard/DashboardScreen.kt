package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.ScreenDefCache
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.renderableSections
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.notifications.NotificationsPanel
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.LocalRefreshTick
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Último [DashboardData] cargado, en memoria y por proceso: al volver al Inicio se pinta
 * al instante con lo que ya había mientras llega lo nuevo, en vez de arrancar en blanco
 * cada vez. Misma idea (y mismas limitaciones) que [ScreenDefCache].
 */
object DashboardDataCache {
    var data: DashboardData? = null
    /** Al cerrar sesión: lo cacheado es del usuario que se va (ver SessionManager.clear). */
    fun clear() { data = null }
}

@Composable
fun DashboardScreen(
    onNavigate: (Screen) -> Unit,
) {
    // F8: el selector Individual/Familiar se ocultó porque "familiar" no existe todavía — no
    // hay cuentas compartidas ni una segunda persona con acceso. `scope` queda fijo en SELF; el
    // modelo `Scope` y el parámetro que recibe el servidor NO se tocan, para que el día que
    // exista familia esto vuelva a tener un selector con significado real.
    val scope = Scope.SELF

    var data by remember { mutableStateOf(DashboardDataCache.data ?: DashboardData()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var screenDef by remember { mutableStateOf<ScreenDefinition?>(ScreenDefCache.dashboard) }
    val snackbarHostState = remember { SnackbarHostState() }
    // F5: la campana vuelve — vista derivada de lo que el Inicio ya carga, sin fetch propio.
    val notifications = notificationRows(data)

    // Además de `refreshKey` (el reintento propio de esta pantalla), la señal de que se guardó
    // algo desde la hoja de Agregar: es una modal y esta pantalla nunca sale de la composición,
    // así que sin esto seguiría mostrando la lista de antes. Ver [LocalRefreshTick].
    val refreshTick = LocalRefreshTick.current
    // TODO(ola-8, V13): el Inicio repite sus ~10 llamadas CADA VEZ que se entra — se contaron
    //  4 rondas completas en pocos minutos de uso normal. En el teléfono con datos móviles eso
    //  es plata del dueño.
    //
    //  Diagnóstico (confirmado en la revisión de la Ola 8, no una sospecha): App.kt envuelve
    //  cada pantalla en `saveableStateHolder.SaveableStateProvider(key = currentScreen)`, así
    //  que al navegar a otra pantalla DashboardScreen sale de la composición y al volver
    //  entra de nuevo — con lo cual este `LaunchedEffect` se reejecuta entero. No es un bug
    //  de esta pantalla: es cómo está montada la navegación.
    //
    //  **Deliberadamente NO se arregló en la rama `fix/web-primera-prueba`**, que era una ola
    //  de presentación. Las salidas posibles —una caché con TTL, subir el estado fuera del
    //  holder, o un endpoint que traiga el Inicio de una sola vez— cambian cuándo se refrescan
    //  las cifras del dinero, y eso no se toca de pasada junto con arreglos visuales.
    //  Mientras tanto [DashboardDataCache] tapa lo peor: al volver se pinta al instante lo
    //  último que había, en vez de arrancar en blanco.
    //
    //  Ya existe una rama para esto: `origin/perf/inicio-endpoints-livianos`.
    LaunchedEffect(refreshKey, refreshTick) {
        loading = true
        error = null
        // SDUI: la definición del server se pide PRIMERO, así el Inicio ya está en su lugar
        // antes de que se pinte el fallback — evita el parpadeo fallback→SDUI en cada arranque
        // frío. Silencioso si falla: capa 2 (ScreenDefCache) conserva la última válida; capa 3
        // (defaultDashboardDefinition, idéntica al seed) cubre un arranque sin caché.
        runCatching { Repositories.wallets.getScreen("dashboard", screenDef?.version) }
            .onSuccess {
                // Capa 4: una definición que no renderiza nada equivale a no tener definición —
                // evita un Inicio en blanco por typos en los tipos de sección.
                it?.takeIf { d -> renderableSections(d).isNotEmpty() }?.let { d -> screenDef = d; ScreenDefCache.dashboard = d }
            }
        // El resto va en paralelo. Solo resumen y cuentas (el Balance) avisan con snackbar si
        // fallan; lo demás alimenta secciones secundarias (próximos pagos, alertas, cifras de
        // los accesos, guía) y si falla simplemente no se pinta esta vez — un snackbar de
        // reintento por un dato secundario sería más ruido que ayuda.
        coroutineScope {
            launch {
                runCatching { Repositories.wallets.getFinanceSummary(scope) }
                    .onSuccess { s -> data = data.copy(summary = s) }
                    .onFailure { e -> error = e.toUserMessage() }
            }
            launch {
                runCatching { Repositories.wallets.getAccounts() }
                    .onSuccess { a -> data = data.copy(accounts = a) }
                    .onFailure { e -> if (error == null) error = e.toUserMessage() }
            }
            launch { runCatching { Repositories.wallets.getCredits() }.onSuccess { c -> data = data.copy(credits = c) } }
            // F20: la cifra del acceso «Créditos» suma préstamos + tarjetas.
            launch { runCatching { Repositories.wallets.getCards() }.onSuccess { c -> data = data.copy(cards = c) } }
            launch { runCatching { Repositories.wallets.getUpcomingPayments() }.onSuccess { u -> data = data.copy(upcoming = u) } }
            launch { runCatching { Repositories.wallets.getBudgets() }.onSuccess { b -> data = data.copy(budgets = b) } }
            // Gasto del mes por categoría, candidatos a pago de tarjeta y SMS pendientes vienen ya
            // reducidos del server (GET /api/dashboard/summary) en vez de bajar todos los eventos,
            // todos los candidatos y todos los SMS para sacar tres números — con meses de uso
            // real eso crecía lineal y hacía lenta la pantalla más usada, sobre todo en el
            // teléfono. Mismas reglas del lado del server (isCashFlow, looksLikeCardPayment,
            // estado "pending"); si falla, quedan las cifras de la última carga (caché).
            launch {
                runCatching { Repositories.wallets.getDashboardSummary(scope) }
                    .onSuccess { s ->
                        data = data.copy(
                            spentByCategory = s.spentByCategory,
                            cardCandidates = s.cardPaymentCandidates,
                            pendingSms = s.pendingSms,
                        )
                        // Ola 9 · A2: las categorías propias del dueño quedan disponibles en
                        // «Agregar» aunque entre directo desde acá, sin haber pasado por
                        // Movimientos ni Presupuestos. **No es una llamada nueva**: viene en
                        // esta misma respuesta, que esta pantalla ya pedía.
                        UsedCategoriesCache.recordFromServer(s.usedCategories)
                        // Y por si el server todavía es viejo y no manda ese campo: lo que se
                        // gastó este mes también dice qué categorías existen, y son gastos por
                        // definición. Cuesta cero y evita que un despliegue a medias deje el
                        // campo sin sugerencias.
                        UsedCategoriesCache.recordAll(
                            s.spentByCategory.keys.map { c -> c to TransactionType.EXPENSE },
                        )
                    }
            }
            launch { runCatching { Repositories.wallets.getGoals() }.onSuccess { g -> data = data.copy(goals = g) } }
            // F50: la cifra de "investments" ahora sale de `data.accounts` (cuentas tipo
            // INVESTMENT) — ya no hace falta este fetch aparte de holdings.
            launch { runCatching { Repositories.wallets.getSubscriptions() }.onSuccess { s -> data = data.copy(subscriptions = s) } }
        }
        DashboardDataCache.data = data
        loading = false
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) refreshKey++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // F60: encabezado único — Inicio es raíz: avatar + el rótulo del menú + la campana.
            // F5: la campana tiene contenido real — el punto solo aparece cuando `notifications`
            // no está vacío.
            MinScreenHeader(
                title = "Inicio",
                leading = HeaderLeading.Avatar(onClick = { onNavigate(Screen.Profile) }),
                action = {
                    Box {
                        Icon(
                            Icons.Rounded.Notifications,
                            contentDescription = "Notificaciones",
                            tint = MinText,
                            modifier = Modifier.size(22.dp).clickable { showNotifications = true },
                        )
                        if (notifications.isNotEmpty()) {
                            StatusDot(
                                color = MinExpense,
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            // Guía "Primeros pasos": chrome nativo, fuera de la definición SDUI a propósito —
            // así existe siempre, sin depender de tocar `screen_definitions` en producción.
            // Se apaga sola cuando ya hay cuenta y movimiento (los datos son el estado).
            // F7: va como primer ítem del scroll, no pegada arriba — antes tapaba el resto.
            val showGuide = !(data.hasAccount && data.hasMovement)
            // SDUI: la definición del server si la hay; si no, la misma lista que el server
            // siembra (anti-rotura capa 3) — una sola fuente en :core, idéntica por construcción.
            SduiRenderer(
                definition = screenDef ?: defaultDashboardDefinition(),
                data = data,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onNavigate = onNavigate,
                header = if (showGuide) {
                    {
                        PrimerosPasosCard(
                            data = data,
                            onNavigate = onNavigate,
                            onShowCreateSheet = { showCreateSheet = true },
                        )
                    }
                } else null,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )

        if (showCreateSheet) {
            CreateAccountSheet(
                onDismiss = { showCreateSheet = false },
                onAccountCreated = { showCreateSheet = false; refreshKey++ },
            )
        }

        if (showNotifications) {
            NotificationsPanel(
                rows = notifications,
                onDismiss = { showNotifications = false },
                onRowClick = onNavigate,
            )
        }
    }
}

/**
 * Guía de arranque compacta (F6 · F7): una línea por paso, sin subtítulos largos, y el
 * paso 2 es el que faltaba — "Anota tus gastos recurrentes" (colegio, arriendo, gimnasio,
 * cuotas),
 * que es donde van las obligaciones que no son ni préstamo ni tarjeta (F6 preguntaba dónde
 * cargar el colegio o el gimnasio: en Recurrentes).
 *
 * Se apaga sola sin flag ni columna nueva: cada vez que el Inicio carga, recalcula el estado
 * a partir de los datos reales. El día que haya cuenta Y movimiento, la tarjeta entera deja
 * de renderizarse — y si el dueño vacía la instancia de nuevo, vuelve a aparecer sola.
 *
 * Gastos recurrentes (paso 2) y créditos (paso 3) NO condicionan el apagado — a propósito. Son
 * pasos *ofrecidos*, no *requeridos*: se tildan si existe algo, pero alguien sin préstamos
 * ni cuotas no tiene por qué ver esta guía para siempre esperando un casillero que jamás se
 * cumple. Cuando la tarjeta se apaga (cuenta + movimiento), se apaga entera.
 *
 * El pie ("Deja que la app se llene sola") no tiene "hecho" propio: no es una acción puntual
 * sino un hábito (subir extractos / dejar el SMS corriendo). Se muestra como acceso puro.
 *
 * Ola 14 — **la misma tarjeta la usa [PrimerosPasosScreen]**, que es la puerta de vuelta desde
 * «Más» (el dueño: «no veo el onboarding o FTU que tenía ciertas tareas, quisiera poder verlo si
 * aún me faltan tareas»). Por eso pasó de `private` a `internal`: una sola tarjeta, no dos que
 * se van separando. Lo que NO cambió es cuándo aparece sola en el Inicio — se sigue apagando con
 * cuenta + movimiento, y no vuelve a asomarse por su cuenta.
 */
@Composable
internal fun PrimerosPasosCard(
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
    onShowCreateSheet: () -> Unit,
) {
    val steps = listOf(data.hasAccount, data.hasRecurringRule, data.hasCredit, data.hasMovement)
    MinCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Primeros pasos", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MinText, modifier = Modifier.weight(1f))
            Text("${steps.count { it }} de ${steps.size}", fontSize = 12.sp, color = MinTextMute)
        }
        Spacer(Modifier.height(2.dp))

        PasoRow(done = data.hasAccount, title = "Crea tu primera cuenta", onClick = onShowCreateSheet)
        Hairline()
        PasoRow(
            done = data.hasRecurringRule,
            title = "Anota tus gastos recurrentes",
            subtitle = "Colegio, arriendo, gimnasio, cuotas",
            onClick = { onNavigate(Screen.Recurrentes) },
        )
        Hairline()
        PasoRow(done = data.hasCredit, title = "Si tienes préstamos o tarjetas, cárgalos", onClick = { onNavigate(Screen.Credits) })
        Hairline()
        PasoRow(done = data.hasMovement, title = "Registra un movimiento", onClick = { onNavigate(Screen.QuickAdd()) })
        Hairline()

        // Pie — sin tilde a propósito (ver KDoc). Extractos en todas las plataformas; el SMS
        // del banco solo en Android (no existe en iOS/web).
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Deja que la app se llene sola", fontSize = 12.5.sp, color = MinTextMute, modifier = Modifier.weight(1f))
            AccesoLink("Extractos") { onNavigate(Screen.Extractos) }
            if (isAndroid) AccesoLink("SMS del banco") { onNavigate(Screen.SMSInbox) }
        }
    }
}

@Composable
private fun PasoRow(
    done: Boolean,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (done) "Hecho" else "Pendiente",
            tint = if (done) MinIncome else MinTextFaint,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (done) MinTextMute else MinText,
            )
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 12.sp, color = MinTextMute, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (!done) ChevronRight()
    }
}

@Composable
private fun AccesoLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
        color = MinPrimary,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}
