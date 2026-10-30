package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.SideEffect
import com.jvillada.movi.ui.components.ds.desplazadoDe
import com.jvillada.movi.ui.components.ds.separadoAlDesplazar
import com.jvillada.movi.avisos.AvisosEnHoy
import com.jvillada.movi.avisos.TarjetaDeAvisos
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.datetime.Clock
import com.jvillada.movi.data.CuentaMasUsadaCache
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.olvidarLoFresco
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
import com.jvillada.movi.ui.primeruso.GuiaDePrimerUso
import com.jvillada.movi.ui.primeruso.FilaDePrimerosPasos
import com.jvillada.movi.ui.primeruso.PrimerosPasosDescartados
import com.jvillada.movi.ui.primeruso.modoDeCapturaDe
import com.jvillada.movi.ui.primeruso.rememberPrimerosPasos
import com.jvillada.movi.shared.model.pasosQueFaltan
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.notifications.NotificationsPanel
import com.jvillada.movi.ui.sdui.SduiRenderer
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.TamanoDelTitulo
import com.jvillada.movi.ui.components.ds.Chevron
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.Encabezado
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

/**
 * El rótulo de la pestaña del Inicio. Ola C la llamó «Hoy»; la ola R1 del rediseño la vuelve a llamar
 * **«Inicio»**, como en las maquetas del camino 1: el encabezado ya no es un título sino el saludo
 * («Hola, Juan») con el período.
 */
const val TITULO_DEL_HOY: String = "Inicio"

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
    // Vuelta 68: cuándo se leyeron las cifras que están a la vista (la caché de este proceso o la
    // instantánea del aparato), y si la última carga falló: con las dos, el encabezado dice «Sin
    // conexión · cifras de las 9:06» en vez de hacer pasar lo guardado por lo de ahora.
    var cifrasDe by remember {
        mutableStateOf(
            if (DashboardDataCache.data != null) DashboardDataCache.cargadoEn.takeIf { it > 0L }
            else InstantaneaDelInicio.delAparato.selladaEn(SessionManager.userId),
        )
    }
    var falloLaCarga by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    // Vuelta 78: si la app vuelve del segundo plano con el Inicio a la vista, se vuelve a preguntar (la
    // vuelta ya olvidó lo fresco y marcó vieja esta carga: ver `LoFresco`). No es un reintento: con el
    // Inicio recién cargado y nada olvidado, no sale nada.
    var vueltasDelSegundoPlano by remember { mutableStateOf(0) }
    com.jvillada.movi.ui.auth.AlVolverDelSegundoPlano { _, _ -> vueltasDelSegundoPlano++ }
    // Vuelta 79: el ↻ del rail y la vuelta del foco a la ventana (la web) vuelven a preguntar.
    com.jvillada.movi.ui.components.AlPedirseUnaRecarga { refreshKey++ }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    // Vuelta 41: el nombre del saludo y de las iniciales, el mismo que dice «Tú». Arranca con el del
    // login y se pone al día con el perfil (que pudo cambiarse en otro aparato).
    var nombreDelDueno by remember { mutableStateOf(SessionManager.userName) }
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
    // Vuelta 68: la carga terminó caída y lo que se ve es lo guardado (ver [lineaSinConexion]).
    val sinConexion = !loading && falloLaCarga && data.puedeAfirmarVacio && cifrasDe != null
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
    LaunchedEffect(refreshKey, refreshTick, vueltasDelSegundoPlano) {
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
        falloLaCarga = false
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
                    .onFailure { e -> falloLaCarga = true; error = e.toUserMessage() }
            }
            launch {
                runCatching { Repositories.wallets.getAccounts() }
                    .onSuccess { a -> data = data.copy(accounts = a); llegado = llegado.copy(accounts = a) }
                    .onFailure { e -> falloLaCarga = true; if (error == null) error = e.toUserMessage() }
            }
            // Lo apartado sale de «Tu plata». Secundario: si falla, se toma como ninguno.
            launch {
                runCatching { Repositories.wallets.getApartados() }
                    .onSuccess { a -> data = data.copy(apartados = a); llegado = llegado.copy(apartados = a) }
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
                        // La traducción de la respuesta vive en [conResumenDelInicio]: la pestaña
                        // Plan lee la misma para su tarjeta «Disponible».
                        data = data.conResumenDelInicio(s)
                        llegado = llegado.conResumenDelInicio(s)
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
                    if (perfil.name.isNotBlank() && SessionManager.userId == usuario) {
                        SessionManager.userName = perfil.name
                        nombreDelDueno = perfil.name
                    }
                    val ahora = Clock.System.now().toEpochMilliseconds()
                    data = data.conElPerfil(perfil, ahora)
                    llegado = llegado.conElPerfil(perfil, ahora)
                    perfilLlego = true
                }
            }
            // F50: la cifra de "investments" ahora sale de `data.accounts` (cuentas tipo
            // INVESTMENT) — ya no hace falta este fetch aparte de holdings.
            launch {
                runCatching { Repositories.wallets.getSubscriptions() }
                    .onSuccess { s -> data = data.copy(subscriptions = s); llegado = llegado.copy(subscriptions = s) }
            }
            // Vuelta 78 (revisión): el supuesto del gasto del día a día, para que «¿Me alcanza?» llegue con el
            // resto del héroe y no 66 px después. Secundario: si falla, el renglón lo pide por su cuenta.
            launch {
                runCatching { Repositories.wallets.getGastoDelDiaADia() }
                    .onSuccess { g -> data = data.copy(gastoDelDiaADia = g); llegado = llegado.copy(gastoDelDiaADia = g) }
            }
            // Ola 4: lo que se sale de lo normal. Secundario: si falla, «Para revisar» sigue con lo
            // demás y no avisa nada de esto esta vez.
            launch {
                runCatching { Repositories.wallets.getAnomalias() }
                    .onSuccess { a -> data = data.copy(anomalias = a); llegado = llegado.copy(anomalias = a) }
            }
            // Lo que el banco cobra solo (#440): el día del débito, «Para revisar» lleva a confirmarlo.
            // Aparte y secundario, como las anomalías: si falla, no se dice nada de esto esta vez.
            launch {
                runCatching { Repositories.wallets.getDebitosAutomaticos() }
                    .onSuccess { d -> data = data.copy(debitosPorConfirmar = d); llegado = llegado.copy(debitosPorConfirmar = d) }
            }
            // Vuelta 66: las transferencias que saldan un grupo, para la campana. Secundaria y solo en
            // `data`, como el cierre.
            launch {
                runCatching { Repositories.wallets.getPropuestasDeSaldar() }
                    .onSuccess { p -> data = data.copy(propuestasDeSaldar = p) }
            }
            // «Cerró tu período»: la tarjeta de los primeros días. Secundaria, como las anomalías, y
            // solo en `data`: no va a la instantánea (ver `DashboardData.cierre`).
            launch {
                runCatching { Repositories.wallets.getCierreDelPeriodo() }
                    .onSuccess { c -> data = data.copy(cierre = c) }
            }
            // Vuelta 39 · «Últimos movimientos»: las primeras filas de Actividad. Solo los días de los
            // últimos movimientos (`/by-day?ultimos=N`), no la historia entera en cada carga de la web;
            // en el teléfono sale de lo local. Secundario: si falla, la sección no se pinta esta vez.
            launch {
                runCatching { Repositories.wallets.getUltimosDias(FILAS_DE_ULTIMOS_MOVIMIENTOS) }.onSuccess { dias ->
                    val ultimos = ultimosMovimientosDe(dias)
                    data = data.copy(ultimosMovimientos = ultimos)
                    llegado = llegado.copy(ultimosMovimientos = ultimos)
                }
            }
            // El saldo que dijo un aviso del banco y no coincide: secundario, como los débitos.
            launch {
                runCatching { Repositories.wallets.getCuadresPorAviso() }
                    .onSuccess { c -> data = data.copy(cuadresPorAviso = c); llegado = llegado.copy(cuadresPorAviso = c) }
            }
            // 4-oct-2026: las cuentas a las que les envía plata sin nombre. Secundario, como las
            // anomalías: si falla, «Para revisar» sigue sin este aviso.
            launch {
                runCatching { Repositories.wallets.getDestinosSugeridos() }.onSuccess { s ->
                    val n = s.count { it.enviado.isNotEmpty() }
                    data = data.copy(cuentasDeOtrosSinNombre = n)
                    llegado = llegado.copy(cuentasDeOtrosSinNombre = n)
                }
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
            cifrasDe = DashboardDataCache.cargadoEn
            DashboardDataCache.tickDeLaCarga = refreshTick
            val instantanea = if (perfilLlego) {
                llegado
            } else {
                llegado.copy(ajustesDePeriodo = data.ajustesDePeriodo, periodoActual = data.periodoActual, primerUsoEn = data.primerUsoEn)
            }
            if (SessionManager.userId == usuario) InstantaneaDelInicio.delAparato.guardarDatos(usuario, instantanea)
        }
        loading = false
    }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        // Vuelta 68 · un solo error por pantalla: con cifras guardadas a la vista lo dice el
        // encabezado («Sin conexión · cifras de las 9:06», que al tocarlo reintenta); el aviso de
        // abajo queda para cuando no hay nada que mostrar.
        if (data.puedeAfirmarVacio && cifrasDe != null) {
            error = null
            return@LaunchedEffect
        }
        val result = snackbarHostState.showSnackbar(msg, actionLabel = "Reintentar")
        error = null
        if (result == SnackbarResult.ActionPerformed) { olvidarLoFresco(); refreshKey++ }
    }

    // Vuelta 79: el scroll del Inicio. Con el contenido desplazado, la franja amarilla de arriba (el
    // encabezado y, en el teléfono, lo de detrás de la barra del sistema) se vuelve crema con el hilo
    // abajo: el amarillo es de arriba, no del chrome.
    val scrollDelInicio = rememberScrollState()
    val desplazado = desplazadoDe(scrollDelInicio)
    val bandaDesplazada = LocalBandaDelInicioDesplazada.current
    SideEffect { bandaDesplazada?.value = desplazado }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Movi.colores.fondo)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Vuelta 39 (la opción 3): el encabezado va sobre la banda amarilla —el avatar con las
            // iniciales, «Hola, Juan» con el período y los dos botones redondos (ocultar los saldos y la
            // campana)—. El amarillo sigue en el scroll de abajo hasta la mitad de la tarjeta héroe
            // (`SduiRenderer(conBanda = true)`) y arriba, detrás de la barra del sistema, lo pone la
            // cáscara (`BarraDelSistemaDelInicio`). Recargando con cifras ya pintadas, el período cede su
            // lugar a «Actualizando…»: el alto no cambia y nada de lo de abajo se mueve.
            EncabezadoDelInicio(
                saludo = saludoDelInicio(nombreDelDueno),
                subtitulo = subtituloDelEncabezadoDelInicio(
                    data = data,
                    actualizandoConDatos = actualizandoConDatos,
                    falloLaCarga = falloLaCarga && !loading,
                    cifrasDe = cifrasDe,
                    ahora = Clock.System.now().toEpochMilliseconds(),
                ),
                descripcionDelSubtitulo = if (actualizandoConDatos || sinConexion) null else descripcionDelPeriodoDelEncabezado(data),
                onSubtitulo = if (sinConexion) ({ olvidarLoFresco(); refreshKey++ }) else null,
                iniciales = inicialesDelUsuario(nombreDelDueno),
                conPunto = campanaConPunto(notifications),
                onAvatar = { onNavigate(Screen.Tu) },
                onCampana = { showNotifications = true },
                desplazado = desplazado,
                modifier = Modifier.separadoAlDesplazar(desplazado, arriba = Movi.colores.marca),
            )

            // Sin nada pintado todavía (`loading && !actualizandoConDatos`), YA NO va la barra de
            // siempre: el hero y «Pregúntale a Movi» (Task 7) pintan su propio esqueleto con la
            // forma de lo que viene, y una barra de ancho completo arriba de un bloque que además
            // pulsa es la misma señal dicha dos veces. Las secciones SDUI que no tienen esqueleto
            // propio (patrimonio, categorías) ya se apagaban solas sin datos —con o sin barra no
            // mostraban nada— así que sacarla no les quita información. Con algo ya pintado
            // (`actualizandoConDatos`), la cabecera sigue diciendo «Actualizando…»: ver más abajo.

            // «Primeros pasos» (vuelta 54): **una sola fila debajo de las acciones** —«Te faltan 3 pasos ›»—,
            // con la lista única de `:core` (la misma de la guía y de Tú → «Primeros pasos»). Antes era
            // una tarjeta de cinco renglones arriba de «Te quedan», con otra lista que no cuadraba con la
            // guía. Chrome nativo, fuera de la definición SDUI: un APK viejo sigue con su tarjeta. No se
            // pinta mientras no se puede afirmar (`primerosPasosDe` es `null`), con todo hecho, con la
            // guía abierta ni después de «×».
            val modoDeCaptura = modoDeCapturaDe(isAndroid)
            val primerosPasos = rememberPrimerosPasos(data, modoDeCaptura)
            var pasosDescartados by remember { mutableStateOf(PrimerosPasosDescartados.estan()) }
            val filaDePasos: (@Composable () -> Unit)? = primerosPasos
                ?.takeIf { pasosQueFaltan(it) > 0 && !GuiaDePrimerUso.enCurso && !pasosDescartados }
                ?.let { lista ->
                    {
                        FilaDePrimerosPasos(
                            lista = lista,
                            modo = modoDeCaptura,
                            onAbrir = { paso -> onNavigate(Screen.PrimerUso(desde = paso)) },
                            onDescartar = {
                                PrimerosPasosDescartados.descartar()
                                pasosDescartados = true
                            },
                        )
                    }
                }
            // Ola 1 · Movi avisa: la tarjeta que ofrece los avisos del teléfono, una sola vez y
            // solo en Android 13+ sin el permiso (ver [AvisosEnHoy]). Solo cuando no faltan primeros
            // pasos: una cuenta recién creada tiene cosas más urgentes que hacer.
            val ofertaDeAvisos = AvisosEnHoy.oferta()
            // «Cerró tu período»: desde la ola R1 es una tarjeta más de «Movi propone hoy» (ver
            // `propuestasDelInicio`), no una tarjeta suelta arriba de todo. «Ya lo vi» llega acá.
            val alcanceDelCierre = rememberCoroutineScope()
            val descartarCierre: (String) -> Unit = { periodo ->
                // Se va en el mismo toque; si el server no lo guarda, vuelve en la próxima carga, que
                // es lo honesto.
                data = data.copy(cierre = null)
                DashboardDataCache.data = DashboardDataCache.data?.copy(cierre = null)
                alcanceDelCierre.launch { runCatching { Repositories.wallets.descartarCierre(periodo) } }
            }
            // SDUI: la definición del server si la hay; si no, la misma lista que el server
            // siembra (anti-rotura capa 3) — una sola fuente en :core, idéntica por construcción.
            // `LocalCargandoElInicio`: el hero y «Pregúntale a Movi» solo reciben `data`, no
            // `loading` — ver su KDoc para el porqué (Task 7, fix round 1).
            // Tirar hacia abajo pide lo mismo que el «Reintentar» del aviso de error: `refreshKey`
            // (ver [TirarParaActualizar]). No es otra recarga.
            CompositionLocalProvider(LocalCargandoElInicio provides loading) {
                TirarParaActualizar(
                    cargando = loading,
                    onActualizar = { refreshKey++ }, // TirarParaActualizar ya olvidó lo fresco
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                SduiRenderer(
                    definition = screenDef ?: defaultDashboardDefinition(),
                    data = data,
                    modifier = Modifier.fillMaxSize(),
                    onNavigate = onNavigate,
                    header = if (filaDePasos == null && ofertaDeAvisos != null) {
                        { TarjetaDeAvisos(ofertaDeAvisos) }
                    } else null,
                    despuesDeLasAcciones = filaDePasos,
                    // El hero vacío abre la MISMA hoja que «Primeros pasos» —
                    // mismo estado, un solo `CreateAccountSheet` en esta pantalla.
                    onShowCreateSheet = { showCreateSheet = true },
                    // Ola 3: qué secciones se ven y en qué orden lo elige el dueño (ver
                    // [EditarInicioScreen]). Al final, siempre, para no competir con lo que Hoy dice.
                    footer = { BotonEditarInicio { onNavigate(Screen.EditarInicio) } },
                    onDescartarCierre = descartarCierre,
                    // Un movimiento de «Últimos movimientos» se editó o se anuló en su hoja: las cifras
                    // de arriba ya no son las de antes.
                    onRecargar = {
                        DashboardDataCache.invalidar()
                        refreshKey++
                    },
                    conBanda = true,
                    scroll = scrollDelInicio,
                )
                } // TirarParaActualizar
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Espacio.m),
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
