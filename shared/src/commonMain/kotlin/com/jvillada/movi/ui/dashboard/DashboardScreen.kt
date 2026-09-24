package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
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
import kotlinx.datetime.Clock
import com.jvillada.movi.data.CuentaMasUsadaCache
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.ScreenDefCache
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.UsedCategoriesCache
import com.jvillada.movi.data.isAndroid
import com.jvillada.movi.shared.model.CapturaDeSms
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.defaultDashboardDefinition
import com.jvillada.movi.shared.model.renderableSections
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.transactions.CHIP_RECURRENTES
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
 *
 * Esto se pierde al cerrar la app o recargar la web; lo que sobrevive a eso es
 * [InstantaneaDelInicio], que el Inicio lee solo cuando acá no hay nada.
 */
object DashboardDataCache {
    var data: DashboardData? = null

    /**
     * Cuándo terminó la última carga completa, en epoch ms. `0` = nunca.
     *
     * Lo escribe SOLO el Inicio, y a propósito: «Primeros pasos» también deja su lectura en
     * `data` —es el mismo modelo— pero no trae las diez llamadas, así que dejar su marca de
     * tiempo haría que el Inicio se saltara una carga que nunca hizo. Sin sello, el Inicio
     * recarga, que es el lado seguro de equivocarse.
     */
    var cargadoEn: Long = 0L

    /**
     * El valor de `LocalRefreshTick` en esa carga. Si cambió, algo se guardó desde entonces y las
     * cifras están viejas pase lo que pase con el reloj.
     */
    var tickDeLaCarga: Int = 0

    /**
     * «La plata cambió»: la próxima entrada al Inicio recarga sí o sí.
     *
     * Existe porque **`LocalRefreshTick` no alcanza**, y la primera versión de este archivo
     * afirmaba lo contrario. El tick es un `Int` sin función para subirlo, así que ninguna
     * pantalla puede moverlo: sus dos únicos productores viven en `App.kt` (la hoja de «Agregar»
     * y la de recurrentes de la barra de ofrecimiento). Lo midió la revisión, contando los
     * caminos.
     *
     * Todo lo demás que mueve plata —anular un movimiento, cambiar su categoría o su fecha,
     * ajustar el saldo de un crédito, registrar un descuento de nómina, importar un extracto,
     * crear o borrar una cuenta, dar de alta un crédito o una tarjeta— pasa por acá.
     *
     * Importa más de lo que parece: **no hay «deslizar para recargar» en ninguna pantalla**, así
     * que salir y volver era el único gesto manual de refresco que tenía el dueño. Un TTL sin
     * esto se lo quitaba.
     *
     * Lo que queda afuera, y con 30 s de retraso máximo: un SMS que llega en segundo plano, algo
     * hecho desde otro dispositivo o desde la web, y el barrido de recordatorios del server.
     * Ninguno es una acción del dueño en este aparato, que es la que no puede quedar sin verse.
     */
    fun invalidar() {
        cargadoEn = 0L
    }

    /**
     * Qué bloques del Inicio ya hicieron su entrada animada (la cifra que cuenta, las barras que
     * crecen) en este proceso. Ver [rememberProgresoDeEntrada]: la animación es **una vez**, no cada
     * vez que se vuelve al Inicio ni cada vez que un bloque sale y entra de la pantalla.
     */
    val entradasHechas: MutableSet<String> = mutableSetOf()

    /** Al cerrar sesión: lo cacheado es del usuario que se va (ver SessionManager.clear). */
    fun clear() {
        data = null
        cargadoEn = 0L
        tickDeLaCarga = 0
        // El próximo que entre ve su Inicio llegar, como la primera vez.
        entradasHechas.clear()
    }
}

/**
 * Cuánto vale una carga del Inicio antes de volver a pedirla. Treinta segundos.
 *
 * No es un número mágico: es el tiempo en que se puede ir a Movimientos, mirar algo y volver. Ese
 * viaje de ida y vuelta es el que hoy cuesta diez llamadas de red por cada vuelta.
 *
 * Elegido corto a propósito. Lo más viejo que el dueño puede llegar a ver es medio minuto, y solo
 * si en ese medio minuto la plata cambió **desde otro lado** —otro dispositivo, un SMS, el
 * barrido de recordatorios—, porque cualquier cosa que haga él mismo mueve `LocalRefreshTick` y
 * fuerza la recarga igual.
 */
const val TTL_DEL_INICIO_MS = 30_000L

/**
 * ¿Hay que volver a pedir las diez llamadas del Inicio?
 *
 * Función pura y aparte del `@Composable` para poder probarla: es una decisión sobre **cuándo se
 * refrescan las cifras del dinero del dueño**, que es exactamente la clase de cosa que no
 * conviene tener enterrada adentro de un `LaunchedEffect`.
 *
 * ### De dónde sale
 *
 * `App.kt` envuelve cada pantalla en un `SaveableStateProvider` con la pantalla actual como
 * clave, así que al navegar a otra el Inicio **sale de la composición**, y al volver entra de
 * nuevo y su `LaunchedEffect` se reejecuta entero. Se contaron cuatro rondas completas de diez
 * llamadas en pocos minutos de uso normal. En el teléfono con datos móviles, eso es plata del
 * dueño.
 *
 * ### Las tres puertas que SIEMPRE recargan
 *
 * - **No hay nada cacheado** ([hayDatos] en false): un arranque en frío, o la primera vez después
 *   de entrar. La web además pierde la caché en cada recarga de página, así que ahí es lo normal.
 * - **El tick cambió**: alguien guardó algo desde que se cargó. Es la señal que emite la hoja de
 *   «Agregar», y llega aunque el Inicio nunca haya salido de la composición.
 * - **Un reintento explícito** ([reintento]): el dueño tocó «Reintentar» en el snackbar de error.
 *   Pedir de nuevo es literalmente lo que pidió.
 *
 * Recién si ninguna aplica se mira el reloj. Nótese el orden: **el tiempo es la última palabra,
 * no la primera**.
 */
fun debeRecargarElInicio(
    hayDatos: Boolean,
    cargadoEn: Long,
    tickDeLaCarga: Int,
    tickActual: Int,
    reintento: Boolean,
    ahora: Long,
): Boolean = when {
    !hayDatos -> true
    tickActual != tickDeLaCarga -> true
    reintento -> true
    // Un reloj que va para atrás (cambio de zona, ajuste del sistema) da una diferencia negativa.
    // Recargar es el lado seguro: mostrar cifras viejas por un reloj mal puesto sería peor que
    // gastar diez llamadas.
    else -> (ahora - cargadoEn) !in 0..TTL_DEL_INICIO_MS
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

    // Lo que se pinta al montar, en este orden: lo que quedó en memoria de este proceso (volver
    // desde Movimientos), la instantánea que quedó en el aparato de la última carga buena (el
    // arranque en frío: ver [InstantaneaDelInicio]), o nada. La instantánea NO pasa a
    // [DashboardDataCache]: así `hayDatos` sigue en false y [debeRecargarElInicio] pide las cifras
    // nuevas igual — lo que se pinta de la instantánea es lo último que se supo, no lo de ahora.
    var data by remember {
        mutableStateOf(
            DashboardDataCache.data
                ?: InstantaneaDelInicio.delAparato.datos(SessionManager.userId)
                    ?.conElPeriodoDe(Clock.System.now().toEpochMilliseconds())
                ?: DashboardData(),
        )
    }
    // Además de `refreshKey` (el reintento propio de esta pantalla), la señal de que se guardó
    // algo desde la hoja de Agregar: es una modal y esta pantalla nunca sale de la composición,
    // así que sin esto seguiría mostrando la lista de antes. Ver [LocalRefreshTick].
    val refreshTick = LocalRefreshTick.current
    // **Revisión final: `loading` nace con la misma decisión que va a tomar el efecto de abajo.**
    // Nacía en `false` y el efecto lo prendía recién al correr: el primer cuadro de un arranque
    // en frío pintaba «Tu plata —» y las tres preguntas genéricas de «Pregúntale a Movi» —
    // justo lo que los esqueletos (Task 7) vinieron a sacar—, y con la instantánea en pantalla
    // ese cuadro salía sin «Actualizando…», como si lo de ayer fuera de hoy. Se pregunta a
    // [debeRecargarElInicio] con los mismos datos que va a usar el efecto (sin reintento: al
    // montar `refreshKey` es 0), así el primer cuadro ya dice lo que va a pasar.
    var loading by remember {
        mutableStateOf(
            debeRecargarElInicio(
                hayDatos = DashboardDataCache.data != null,
                cargadoEn = DashboardDataCache.cargadoEn,
                tickDeLaCarga = DashboardDataCache.tickDeLaCarga,
                tickActual = refreshTick,
                reintento = false,
                ahora = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var screenDef by remember {
        mutableStateOf<ScreenDefinition?>(
            ScreenDefCache.dashboard
                ?: InstantaneaDelInicio.delAparato.definicion(SessionManager.userId)
                    ?.takeIf { renderableSections(it).isNotEmpty() },
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }
    // Recarga en curso con algo que vale la pena ya en pantalla: la caché de este proceso o la
    // instantánea del aparato. «Que vale la pena» es la misma vara del resto del Inicio
    // (`puedeAfirmarVacio`): la cabecera dice «Actualizando…» en vez de la barra de progreso.
    val actualizandoConDatos = loading && data.puedeAfirmarVacio
    // F5: la campana vuelve — vista derivada de lo que el Inicio ya carga, sin fetch propio.
    val notifications = notificationRows(data)

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
        // ¿Hace falta pedir las diez otra vez? Ver [debeRecargarElInicio] — el TODO de la Ola 8
        // que documentaba este derroche queda cerrado acá.
        //
        // `refreshKey > 0` cubre el reintento explícito. **También** queda en 1 después de crear
        // una cuenta desde el Inicio (`onAccountCreated`), y eso es inofensivo: el efecto solo se
        // reejecuta cuando `refreshKey` o `refreshTick` CAMBIAN, y los dos cambios ya fuerzan la
        // recarga por su cuenta. Un `reintento = true` viejo nunca provoca una llamada de más.
        // Se reinicia a 0 al remontar, que es justamente el caso que este arreglo quiere saltear.
        if (!debeRecargarElInicio(
                hayDatos = DashboardDataCache.data != null,
                cargadoEn = DashboardDataCache.cargadoEn,
                tickDeLaCarga = DashboardDataCache.tickDeLaCarga,
                tickActual = refreshTick,
                reintento = refreshKey > 0,
                ahora = Clock.System.now().toEpochMilliseconds(),
            )
        ) {
            // Ya se pintó lo cacheado en el `remember` de arriba; no hay nada más que hacer.
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        // De quién es esta carga. Se vuelve a mirar antes de guardar la instantánea: si en el medio
        // se cerró la sesión, `clear()` ya borró la de este usuario y escribirla de nuevo dejaría su
        // plata en el aparato.
        val usuario = SessionManager.userId
        // Lo que contestó ESTA carga, aparte de `data`: `data` arranca con lo que ya estaba pintado
        // (la caché o la instantánea), así que con las diez caídas seguiría pudiendo «afirmar» con
        // las cifras de ayer. El sello de abajo mira esto, no `data`.
        var llegado = DashboardData()
        // Si contestó `/api/dashboard/summary`. Aparte de `llegado` porque ninguno de sus campos
        // distingue por sí solo «llegó vacío» de «no llegó» (`pendingSms = 0`, un mapa vacío).
        // Ver el sello de abajo.
        var resumenDelInicioLlego = false
        // Lo mismo para el perfil: sin él, `llegado` tendría el corte por defecto (mes de
        // calendario) y la instantánea perdería el del dueño — ver la escritura de abajo.
        var perfilLlego = false
        // SDUI. Silenciosa si falla: capa 2 (ScreenDefCache, y su copia en el aparato) conserva la
        // última válida; capa 3 (defaultDashboardDefinition, idéntica al seed) cubre un arranque
        // sin ninguna de las dos.
        suspend fun pedirDefinicion() {
            runCatching { Repositories.wallets.getScreen("dashboard", screenDef?.version) }
                .onSuccess {
                    // Capa 4: una definición que no renderiza nada equivale a no tener definición —
                    // evita un Inicio en blanco por typos en los tipos de sección.
                    it?.takeIf { d -> renderableSections(d).isNotEmpty() }?.let { d -> screenDef = d; ScreenDefCache.dashboard = d }
                    // `null` es el 304 («la que tienes sigue vigente»): se guarda igual, porque la
                    // que está en memoria puede no haber llegado nunca al aparato.
                    val d = screenDef
                    if (d != null && SessionManager.userId == usuario) {
                        InstantaneaDelInicio.delAparato.guardarDefinicion(usuario, d)
                    }
                }
        }
        // Sin definición a mano, se pide PRIMERO, así el Inicio ya está en su lugar antes de que
        // se pinte el fallback — evita el parpadeo fallback→SDUI. Con una (de la memoria o del
        // aparato) ya no hay parpadeo que evitar, y esperarla antes de los datos era medio arranque
        // en frío perdido en serie: va en paralelo con el resto.
        val definicionEnParalelo = screenDef != null
        if (!definicionEnParalelo) pedirDefinicion()
        // El resto va en paralelo. Solo resumen y cuentas (el Balance) avisan con snackbar si
        // fallan; lo demás alimenta secciones secundarias (próximos pagos, alertas, cifras de
        // los accesos, guía) y si falla simplemente no se pinta esta vez — un snackbar de
        // reintento por un dato secundario sería más ruido que ayuda.
        coroutineScope {
            if (definicionEnParalelo) launch { pedirDefinicion() }
            // Las cinco que sostienen `puedeAfirmarVacio` también se anotan en `llegado`.
            launch {
                runCatching { Repositories.wallets.getFinanceSummary(scope) }
                    .onSuccess { s -> data = data.copy(summary = s); llegado = llegado.copy(summary = s) }
                    .onFailure { e -> error = e.toUserMessage() }
            }
            launch {
                runCatching { Repositories.wallets.getAccounts() }
                    .onSuccess { a -> data = data.copy(accounts = a); llegado = llegado.copy(accounts = a) }
                    .onFailure { e -> if (error == null) error = e.toUserMessage() }
            }
            launch {
                runCatching { Repositories.wallets.getCredits() }
                    .onSuccess { c -> data = data.copy(credits = c); llegado = llegado.copy(credits = c) }
            }
            // F20: la cifra del acceso «Créditos» suma préstamos + tarjetas.
            launch {
                runCatching { Repositories.wallets.getCards() }
                    .onSuccess { c -> data = data.copy(cards = c); llegado = llegado.copy(cards = c) }
            }
            launch {
                runCatching { Repositories.wallets.getUpcomingPayments() }
                    .onSuccess { u -> data = data.copy(upcoming = u); llegado = llegado.copy(upcoming = u) }
            }
            launch {
                runCatching { Repositories.wallets.getBudgets() }
                    .onSuccess { b -> data = data.copy(budgets = b); llegado = llegado.copy(budgets = b) }
            }
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
                            // Lo que se sabe de la captura de SMS. Viene en esta MISMA respuesta
                            // —no es una llamada nueva— y es lo que le permite al Inicio decir
                            // «Movi nunca ha recibido un mensaje de tu banco». Ver CapturaDeSms
                            // en :core: la captura estuvo muda semanas y el único lugar que
                            // podía delatarlo era una pantalla de Android que el dueño no abre.
                            captura = CapturaDeSms(total = s.smsTotal, ultimo = s.smsLastAt),
                            capturaSilenciada = s.smsAlertMuted,
                            // La tarjeta «Disponible». Misma respuesta, ninguna llamada nueva.
                            gastoVariablePorDia = s.gastoVariablePorDia,
                            // Lo que tenías al empezar el período y lo que entró. Un server viejo
                            // no lo manda y la tarjeta vuelve a «ingresos menos fijos».
                            plataDelDisponible = plataDelDisponibleDe(s),
                            // El patrimonio ya partido (entrega A). La tarjeta lo usa solo si las
                            // cuentas no llegaron: ver `patrimonioDelInicio`.
                            patrimonio = s.patrimonio,
                        )
                        llegado = llegado.copy(
                            spentByCategory = data.spentByCategory,
                            cardCandidates = data.cardCandidates,
                            pendingSms = data.pendingSms,
                            captura = data.captura,
                            capturaSilenciada = data.capturaSilenciada,
                            gastoVariablePorDia = data.gastoVariablePorDia,
                            plataDelDisponible = data.plataDelDisponible,
                            patrimonio = data.patrimonio,
                        )
                        resumenDelInicioLlego = true
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
                        // Ola A: misma respuesta, misma lógica — la cuenta con más gastos de los
                        // últimos 30 días queda disponible para que «Agregar» arranque ahí.
                        CuentaMasUsadaCache.recordFromServer(s.cuentaMasUsada)
                    }
            }
            // Ola B, tarea 7: acá pedía `getGoals()`. Metas salió de la navegación y su acceso
            // con cifra ya no se pinta (`renderableSections` lo saca de cualquier definición que
            // todavía lo traiga) — nada visible en el Inicio usa `data.goals`, así que pedirlo
            // era una llamada de más en cada carga. `DashboardData.goals` se queda (no hay nada
            // que migrar) pero ya no lo llena nadie.
            // Los sellos de «ya ocurrió», para poder tildar el checklist del período. Si falla, el
            // checklist muestra todo como pendiente: recordar algo ya pagado molesta; dar por
            // pagado algo que no, cuesta plata.
            launch {
                runCatching { Repositories.wallets.getOccurrenceStates() }
                    .onSuccess { o -> data = data.copy(ocurrencias = o); llegado = llegado.copy(ocurrencias = o) }
            }
            // El período del dueño (su día de corte y los inicios que movió a mano). Sin esto el
            // Inicio hablaría del mes de calendario, que es justo lo que dejó de hacer el resto de
            // la app.
            launch {
                runCatching { Repositories.wallets.getUserProfile() }.onSuccess { perfil ->
                    val ajustes = PeriodSettings(perfil.periodCutoffDay, perfil.periodStarts)
                    data = data.copy(
                        ajustesDePeriodo = ajustes,
                        periodoActual = periodoDe(Clock.System.now().toEpochMilliseconds(), ajustes),
                    )
                    llegado = llegado.copy(ajustesDePeriodo = data.ajustesDePeriodo, periodoActual = data.periodoActual)
                    perfilLlego = true
                }
            }
            // F50: la cifra de "investments" ahora sale de `data.accounts` (cuentas tipo
            // INVESTMENT) — ya no hace falta este fetch aparte de holdings.
            launch {
                runCatching { Repositories.wallets.getSubscriptions() }
                    .onSuccess { s -> data = data.copy(subscriptions = s); llegado = llegado.copy(subscriptions = s) }
            }
        }
        // Con la misma guarda que la instantánea (ver `usuario`): una carga que termina después
        // del logout no puede dejarle al próximo usuario la plata del anterior en memoria.
        if (SessionManager.userId == usuario) DashboardDataCache.data = data
        // **Solo se sella una carga que SALIÓ BIEN.**
        //
        // La primera versión sellaba siempre, y «las diez terminaron» no es lo mismo que «las
        // diez salieron bien». Escenario que encontró la revisión: arranque en frío sin señal →
        // las diez fallan → `data` queda vacío pero NO nulo → se sellaba igual. El dueño iba a
        // Movimientos, volvía dentro de los 30 s, el Inicio se salteaba la carga y quedaba con
        // «Tu plata —» y las tres cifras en guion: **sin snackbar de error, sin «Reintentar» y
        // sin barra de progreso**. Antes de este PR, volver reintentaba.
        //
        // Se mira `puedeAfirmarVacio` y no `error == null` porque es la misma condición que ya
        // gobierna si el Inicio puede opinar sobre la plata del dueño (ver DashboardLogic): si no
        // alcanza para afirmar, tampoco alcanza para saltearse la próxima carga.
        //
        // Y se mira sobre `llegado`, no sobre `data`: con la instantánea del aparato, un arranque
        // en frío sin señal pinta cifras de ayer que SÍ alcanzan para afirmar, y las diez caídas
        // sellaban igual. Por lo mismo, la instantánea solo se reescribe con una carga buena.
        //
        // **Revisión final — y el resumen del Inicio tiene que haber llegado.** Con solo
        // `/api/dashboard/summary` caído, `llegado` alcanzaba para afirmar y se sellaba; `data`
        // traía todavía de la caché o de la instantánea el gasto por categoría, el gasto por día,
        // «Tu plata» al empezar, la captura de SMS y los pendientes — y se escribían al aparato
        // como si fueran de esta carga. Sin sello, volver al Inicio reintenta.
        //
        // Y lo que se escribe es `llegado`, no `data`: la instantánea guarda SOLO lo que contestó
        // esta carga. Una lectura secundaria caída (metas, presupuestos, sellos) no se pinta en el
        // próximo arranque en frío, que es mejor que pintarla vieja como si fuera la última que se
        // supo. El corte del período es la excepción: sin perfil se conserva el que ya había (ver
        // `conElPeriodoDe`: el corte se le puede confiar a la instantánea, la fecha no).
        if (llegado.puedeAfirmarVacio && resumenDelInicioLlego) {
            DashboardDataCache.cargadoEn = Clock.System.now().toEpochMilliseconds()
            DashboardDataCache.tickDeLaCarga = refreshTick
            val instantanea = if (perfilLlego) {
                llegado
            } else {
                llegado.copy(ajustesDePeriodo = data.ajustesDePeriodo, periodoActual = data.periodoActual)
            }
            if (SessionManager.userId == usuario) InstantaneaDelInicio.delAparato.guardarDatos(usuario, instantanea)
        }
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
            .background(Movi.colores.fondo)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // F60: encabezado único — Inicio es raíz: avatar + el rótulo del menú + la campana.
            // F5: la campana tiene contenido real — el punto solo aparece cuando `notifications`
            // no está vacío.
            MinScreenHeader(
                title = "Inicio",
                leading = HeaderLeading.Avatar(onClick = { onNavigate(Screen.Profile) }),
                action = {
                    // Recargando con cifras ya pintadas (la caché o la instantánea): una línea
                    // discreta en vez de la barra de ancho completo. Va en la cabecera, cuyo alto
                    // lo fija el avatar de 32 dp, así que aparecer y desaparecer no mueve nada de
                    // lo de abajo — que es justo lo que no podía pasar mientras el dueño lee.
                    if (actualizandoConDatos) {
                        Text(
                            "Actualizando…",
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoApagado,
                            maxLines = 1,
                        )
                    }
                    // Compartir con un tercero, al lado de la campana: el Inicio es donde uno está
                    // mirando su plata cuando se le ocurre mostrársela a alguien.
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = "Compartir un resumen",
                        tint = Movi.colores.texto,
                        modifier = Modifier.size(22.dp).clickable { onNavigate(Screen.Compartir) },
                    )
                    Box {
                        Icon(
                            Icons.Rounded.Notifications,
                            contentDescription = "Notificaciones",
                            tint = Movi.colores.texto,
                            modifier = Modifier.size(22.dp).clickable { showNotifications = true },
                        )
                        if (notifications.isNotEmpty()) {
                            StatusDot(
                                color = Movi.colores.sale,
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            // Sin nada pintado todavía (`loading && !actualizandoConDatos`), YA NO va la barra de
            // siempre: el hero y «Pregúntale a Movi» (Task 7) pintan su propio esqueleto con la
            // forma de lo que viene, y una barra de ancho completo arriba de un bloque que además
            // pulsa es la misma señal dicha dos veces. Las secciones SDUI que no tienen esqueleto
            // propio (patrimonio, categorías) ya se apagaban solas sin datos —con o sin barra no
            // mostraban nada— así que sacarla no les quita información. Con algo ya pintado
            // (`actualizandoConDatos`), la cabecera sigue diciendo «Actualizando…»: ver más abajo.

            // Guía "Primeros pasos": chrome nativo, fuera de la definición SDUI a propósito —
            // así existe siempre, sin depender de tocar `screen_definitions` en producción.
            // Se apaga sola cuando ya hay cuenta y movimiento (los datos son el estado).
            // F7: va como primer ítem del scroll, no pegada arriba — antes tapaba el resto.
            // `puedeAfirmarVacio`: la guía dice «Crea tu primera cuenta» y «Registra un
            // movimiento» sin tildar, o sea afirma que el dueño no tiene ni una cosa ni la otra.
            // Eso solo se puede decir cuando cuentas y resumen YA contestaron. Sin esta guarda,
            // cada carga en frío de la web —donde la caché en memoria se pierde al recargar—
            // saludaba con una lista de tareas ya hechas hace meses.
            val showGuide = data.guiaIncompleta
            // SDUI: la definición del server si la hay; si no, la misma lista que el server
            // siembra (anti-rotura capa 3) — una sola fuente en :core, idéntica por construcción.
            // `LocalCargandoElInicio`: el hero y «Pregúntale a Movi» solo reciben `data`, no
            // `loading` — ver su KDoc para el porqué (Task 7, fix round 1).
            CompositionLocalProvider(LocalCargandoElInicio provides loading) {
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
            Text("Primeros pasos", style = Movi.textos.titulo, color = Movi.colores.texto, modifier = Modifier.weight(1f))
            Text("${steps.count { it }} de ${steps.size}", style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
        Spacer(Modifier.height(2.dp))

        PasoRow(done = data.hasAccount, title = "Crea tu primera cuenta", onClick = onShowCreateSheet)
        Hairline()
        PasoRow(
            done = data.hasRecurringRule,
            title = "Anota tus gastos recurrentes",
            subtitle = "Colegio, arriendo, gimnasio, cuotas",
            // PR 3 del rediseño de Recurrentes: los recurrentes se anotan y se revisan en
            // Movimientos, con su chip puesto. La pantalla aparte dejó de tener entradas.
            onClick = { onNavigate(Screen.Transactions(CHIP_RECURRENTES)) },
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
            Text("Deja que la app se llene sola", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.weight(1f))
            // Ola B, tarea 7: era «Extractos» → Screen.Extractos; esa pantalla salió de la
            // navegación y «Importar movimientos» vive ahora en Documentos.
            AccesoLink("Documentos") { onNavigate(Screen.Documentos) }
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
            tint = if (done) Movi.colores.entra else Movi.colores.textoApagado,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Movi.textos.cuerpo,
                color = if (done) Movi.colores.textoMedio else Movi.colores.texto,
            )
            if (subtitle != null) {
                Text(text = subtitle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (!done) ChevronRight()
    }
}

@Composable
private fun AccesoLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = Movi.textos.apoyo,
        fontWeight = FontWeight.Medium,
        color = Movi.colores.marca,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}
