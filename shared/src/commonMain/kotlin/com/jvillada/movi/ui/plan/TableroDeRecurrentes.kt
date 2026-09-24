package com.jvillada.movi.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.ReminderChannelsCache
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.intentar
import com.jvillada.movi.platform.PushOptIn
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubStatus
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.periodoActual
import com.jvillada.movi.theme.*
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.*
import com.jvillada.movi.ui.dashboard.checklistDelPeriodo
import com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet
import com.jvillada.movi.ui.recurrentes.ETIQUETA_MINIMOS_DE_TARJETA
import com.jvillada.movi.ui.recurrentes.OrigenDeSuscripcion
import com.jvillada.movi.ui.recurrentes.Recurrente
import com.jvillada.movi.ui.recurrentes.ReminderWarningBanner
import com.jvillada.movi.ui.recurrentes.ResumenRecurrentes
import com.jvillada.movi.ui.recurrentes.SeccionChecklistDelPeriodo
import com.jvillada.movi.ui.recurrentes.SeccionProximosPagos
import com.jvillada.movi.ui.recurrentes.SeccionSinConfirmar
import com.jvillada.movi.ui.recurrentes.SeccionYaOcurrieron
import com.jvillada.movi.ui.recurrentes.avisoDeCandidataDuplicada
import com.jvillada.movi.ui.recurrentes.avisoDeMinimosQueFaltan
import com.jvillada.movi.ui.recurrentes.candidatasSinConfirmar
import com.jvillada.movi.ui.recurrentes.claveDeNombre
import com.jvillada.movi.ui.recurrentes.claveDescartada
import com.jvillada.movi.ui.recurrentes.contextoDeCandidata
import com.jvillada.movi.ui.recurrentes.contextoDeSuscripcionActiva
import com.jvillada.movi.ui.recurrentes.hayRecordatoriosPedidos
import com.jvillada.movi.ui.recurrentes.hojaParaAnotar
import com.jvillada.movi.ui.recurrentes.nombresDeSuscripcionesQueYaSuman
import com.jvillada.movi.ui.recurrentes.notaDeProrrateo
import com.jvillada.movi.ui.recurrentes.ocurrenciasAbiertasSinUrgencia
import com.jvillada.movi.ui.recurrentes.ocurrenciasSelladas
import com.jvillada.movi.ui.recurrentes.planesDeLasCuotas
import com.jvillada.movi.ui.recurrentes.proximosQueUrgen
import com.jvillada.movi.ui.recurrentes.quitarBorraLaSuscripcion
import com.jvillada.movi.ui.recurrentes.resumenRecurrentes
import com.jvillada.movi.ui.recurrentes.shouldShowReminderWarning
import com.jvillada.movi.ui.recurrentes.subtituloDelFlujoLibre
import com.jvillada.movi.ui.recurrentes.suscripcionesActivas
import com.jvillada.movi.ui.recurrentes.textoDelMontoDeSuscripcion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/*
 * # El tablero de Recurrentes
 *
 * Qué vence, qué ya ocurrió, qué falta confirmar y cuánto suma lo que se repite. Nació como el chip
 * «Recurrentes» de Movimientos (el rediseño de 2026-09 disolvió ahí la pantalla vieja) y se sacó a
 * este archivo en la ola C para que la pestaña «Plan» lo pueda mostrar como «Pagos del mes» sin
 * copiar una línea: la misma carga, las mismas reglas de «no afirmar vacío», las mismas acciones.
 *
 * Se arma con tres piezas porque tiene que poder vivir en dos lugares con formas distintas:
 *
 * - [EstadoDelTableroDeRecurrentes] (con [rememberEstadoDelTableroDeRecurrentes]): lo que el tablero
 *   lee del server y lo que el dueño le hace. Carga lo suyo.
 * - [tableroDeRecurrentes]: sus renglones, como extensión de `LazyListScope`. Movimientos lo pinta
 *   adentro de SU `LazyColumn` —con la búsqueda escrita, el tablero y la lista de días comparten un
 *   solo scroll, y eso no puede cambiar—.
 * - [HojasDelTableroDeRecurrentes]: las hojas que abren sus filas (editar un recurrente o una
 *   suscripción, confirmar «Quitar»). Van afuera de la lista, como toda hoja.
 *
 * [TableroDeRecurrentes] junta las tres con su propio scroll, su propio aviso de error y su propia
 * lectura de cuentas: es el tablero entero, listo para montarse solo.
 */

/**
 * **Lo que el tablero lee y lo que el dueño le hace.** Estaba suelto adentro de `TransactionsScreen`
 * como dos docenas de `remember`; se juntó acá sin cambiar ni una clave ni un orden de lectura.
 *
 * Los errores no viven acá: se escriben en el [MutableState] que pasa quien lo monta. Movimientos le
 * pasa el suyo porque así fue siempre —un solo aviso con un solo «Reintentar» para toda la
 * pantalla— y porque dos lecturas se miran entre sí (`if (error == null)`, y «Buscar cobros» limpia
 * el aviso antes de empezar). Con dos estados separados esas dos decisiones cambiarían en silencio.
 */
@Stable
class EstadoDelTableroDeRecurrentes internal constructor(
    private val alcance: CoroutineScope,
    private val errorDeLaPantalla: MutableState<String?>,
) {
    private var error: String?
        get() = errorDeLaPantalla.value
        set(valor) { errorDeLaPantalla.value = valor }

    // PR 1 del rediseño de Recurrentes: lo que hace falta para el chip «Recurrentes» y la marca
    // en cada fila (ver [nombreRecurrenteDe]). Sale del MISMO cache que ya usa la barra de
    // «¿esto se repite?» de después de guardar — ver [RecurringOfferGate.listasParaMovimientos]:
    // si esta pantalla es la primera en pedirlas esta sesión, las carga UNA vez; si ya las cargó
    // otra pantalla (o esta misma en una visita anterior), no hay ningún viaje de red de más.
    //
    // Ola C: viven acá y no en Movimientos porque las acciones del tablero son las que las
    // invalidan (ver [recargas]), pero Movimientos las sigue leyendo para su filtro y sus marcas.
    var reglasRecurrentes by mutableStateOf<List<RecurringRule>>(emptyList())
        internal set
    var nombresDeSuscripcionesActivas by mutableStateOf<List<String>>(emptyList())
        internal set

    // Sube tras cada Confirmar / No es / Buscar cobros, para volver a traer las listas sin
    // esperar a `refreshKey` (que dispararía además una recarga innecesaria de los movimientos).
    // (Era `recurrentesReloadKey` en Movimientos.)
    internal var recargas by mutableStateOf(0)

    // PR 2 del rediseño de Recurrentes: el «Flujo libre» y las candidatas «por confirmar» que
    // vivían solo en la pantalla vieja. A diferencia de `reglasRecurrentes` de arriba —que solo
    // necesita reconocer un nombre, y ahí una lista de hace un rato no hace daño— acá el dueño
    // viene a hacer algo con lo que ve (confirmar o descartar una candidata), así que se trae
    // FRESCO cada vez que el chip se activa, sin pasar por el cache de `RecurringOfferGate`: una
    // candidata que el detector ya encontró en otra sesión, o que otro dispositivo ya resolvió,
    // tiene que verse tal cual está, no la última que ese cache recuerde. Es el mismo endpoint
    // que `listasParaMovimientos` ya usa; la diferencia es que acá SÍ se repite la llamada.
    internal var subsParaRecurrentes by mutableStateOf(SubscriptionsResult(emptyList(), 0))
    internal var subsParaRecurrentesOk by mutableStateOf(false)
    // Ids con una acción en vuelo, para no dejar tocar dos veces la misma suscripción mientras se
    // guarda — mismo motivo que `marcando` en la pantalla vieja. Sirve a las dos acciones que hay
    // sobre una suscripción (Confirmar/No es de una candidata, y Quitar de una activa): son
    // conjuntos disjuntos de filas, y el id es el id.
    internal var suscripcionesEnVuelo by mutableStateOf<Set<String>>(emptySet())
    internal var buscandoCobros by mutableStateOf(false)

    // PR 3 del rediseño de Recurrentes: los vencimientos y «¿esto ya ocurrió?», la última pieza
    // que solo vivía en la pantalla vieja. Misma disciplina que las candidatas de arriba: se
    // traen FRESCAS con el chip activo (acá el dueño viene a sellar un periodo, no a mirar) y se
    // vuelven a traer tras cada marca con `recargas`.
    internal var upcomingRecurrentes by mutableStateOf<List<UpcomingPayment>>(emptyList())
    internal var vencimientosOk by mutableStateOf(false)
    internal var ocurrencias by mutableStateOf<List<OccurrenceState>>(emptyList())
    internal var ocurrenciasOk by mutableStateOf(false)
    /**
     * **Alguna de esas dos lecturas falló.** `vencimientosOk`/`ocurrenciasOk` solo distinguen «ya
     * contestó» de «todavía no», y el checklist necesita la tercera: mientras viaja no dice nada, y
     * si no llegó tiene que decir que no pudo leerlo (ver [SeccionChecklistDelPeriodo]). Sin este
     * dato, una lectura caída se veía igual que una lenta — para siempre.
     */
    internal var recurrentesNoSePudieronLeer by mutableStateOf(false)

    /**
     * Hay una lectura de vencimientos y ocurrencias en vuelo (la primera o una recarga). La tarjeta
     * del disponible de Plan, que usa estas mismas listas, dice «Actualizando…» mientras tanto.
     */
    internal var leyendoVencimientos by mutableStateOf(false)

    /**
     * La primera lectura de lo que el tablero enumera —vencimientos y ocurrencias— todavía no
     * contestó, ni bien ni mal. Es la misma condición con la que el checklist no se pinta (ver
     * [SeccionChecklistDelPeriodo]); quien monta el tablero con esqueleto (la pestaña Plan) la usa
     * para poner la forma en su lugar en vez de dos rótulos sueltos sin nada abajo.
     */
    val primeraLecturaEnCurso: Boolean
        get() = !recurrentesNoSePudieronLeer && !(vencimientosOk && ocurrenciasOk)
    /**
     * Lo que el dueño rechazó con «no fue este», **mientras dure esta pantalla**. Las claves son
     * (regla, movimiento) — ver [claveDescartada].
     *
     * Ya no es la única memoria del rechazo: desde que Movi empareja solo, el «no» se guarda en el
     * server (ver `rechazarOcurrencia`), porque uno que se olvida al recargar dejaría al
     * emparejamiento automático volviendo a dar por ocurrido lo mismo en la siguiente lectura. Esto
     * que queda acá es la capa optimista: la propuesta siguiente aparece en el cuadro siguiente, sin
     * esperar el viaje de red. Las dos usan la misma clave a propósito.
     */
    internal var descartadas by mutableStateOf<Set<String>>(emptySet())
    // Reglas con una escritura en vuelo. Un conjunto y no un id: confirmar el salario no puede
    // congelar la pregunta del arriendo — son dos hechos independientes.
    internal var marcando by mutableStateOf<Set<String>>(emptySet())
    // Para el aviso ámbar de «pediste que te recordemos y no tenemos por dónde». Ver
    // [shouldShowReminderWarning]: `canales == null` es «todavía no se sabe» y ahí NO se avisa.
    internal var pushStatus by mutableStateOf(PushOptIn.status())
    internal var pushRefreshTick by mutableStateOf(0)
    /**
     * La regla que el dueño pidió editar tocando su renglón en «Próximos».
     *
     * En la pantalla «Recurrentes» —la que el rediseño de 2026-09 disolvió acá adentro— ese toque
     * abría la hoja de editar, y esa es la única acción que la fila prometía: relocalizarla como
     * «no hace nada» habría sido perder función, y mandarla a la pantalla vieja habría sido justo
     * lo que el rediseño venía a terminar. Es la misma hoja, en modo edición, que ya abre
     * `HojaDelMovimiento` desde el detalle de un movimiento (PR 1).
     *
     * La regla que se pasa sale de `upcomingRecurrentes`, que se recarga al activar el chip y tras
     * cada cambio (`recargas`) — la precaución que esa pantalla documentaba: prellenar el
     * formulario con una fila vieja hace que «Guardar cambios» reescriba lo que el dueño ya había
     * corregido.
     */
    internal var reglaRecurrenteAEditar by mutableStateOf<RecurringRule?>(null)
    // Ola 18: la suscripción abierta para editar. Hasta acá la fila solo ofrecía «Quitar», así
    // que corregirle el monto a un cobro era quitarlo y volver a escribirlo entero — y en una
    // detectada eso ni siquiera funcionaba: «Quitar» la marca DISMISSED, no la borra.
    internal var suscripcionAEditar by mutableStateOf<Subscription?>(null)
    /** La suscripción que el dueño tocó «Quitar» y todavía no confirmó. */
    internal var suscripcionPorQuitar by mutableStateOf<Subscription?>(null)
    /**
     * ¿Está abierta la lista de suscripciones activas? **Arranca cerrada**, y por lo mismo que el
     * grupo de ajustes de Movimientos es transitorio: abrir el inventario es un vistazo, no una
     * preferencia. La cifra que se mira de reojo —cuánto suman al mes— sigue a la vista plegada,
     * así que cerrar no esconde información, solo filas. Ver [resumenPlegadoDeSuscripciones].
     */
    internal var suscripcionesAbiertas by mutableStateOf(false)

    /**
     * **El plan de cada crédito, para poder decir cuánto trae la cuota de este período.**
     *
     * El dueño paga su crédito del carro de memoria —el banco no le publica un valor a pagar— y en
     * septiembre giró $77.040 de más. Con esto, la fila de la cuota en el checklist muestra el
     * reparto que Movi estima (ver [planesDeLasCuotas] y
     * [com.jvillada.movi.ui.recurrentes.estimacionDeLaFila]).
     *
     * Se pide con el chip activo, igual que los vencimientos y las candidatas, y por la misma caché
     * del repositorio que ya usa la pestaña «Cuota» de Agregar, así que en el teléfono no cuesta un
     * viaje cada vez.
     *
     * **Un fallo acá NO es un error de la pantalla**, a diferencia de los vencimientos: el mapa se
     * queda vacío, las filas se ven como antes de esta ola, y el checklist sigue siendo utilizable.
     * Un cartel rojo por una estimación que no llegó le taparía lo que vino a hacer, que es tildar
     * lo que ya pagó.
     */
    internal var planesDeCuotas by mutableStateOf<Map<String, PlanDelCredito>>(emptyMap())

    // Las CIFRAS solo se pintan con la fuente fresca ya cargada — un total a medias es peor que
    // ningún total, mismo criterio que la pantalla vieja (`reglasOk && cobrosOk`).
    //
    // **A las reglas del dueño se les suman las sintéticas** (las cuotas de sus créditos, que no
    // son filas de ninguna tabla y solo llegan por `/api/payments/upcoming`), porque desde este
    // cambio entran al «Flujo libre». Quién de ellas suma lo decide `cuentaComoCompromisoMensual`
    // adentro de `resumenRecurrentes` —la de una tarjeta no, la de un crédito de pago único
    // tampoco—, no este llamado. Ver [reglasSinteticas].
    //
    // **Y las del dueño también salen de ahí**, no de `reglasRecurrentes`. Esa lista viene del
    // cache de sesión de `RecurringOfferGate`: no se refresca si la regla cambió en otro dispositivo
    // y, si su lectura falló, queda VACÍA en silencio — el chip perdía el sueldo y mostraba «libre al
    // mes» en −$4.398.426 mientras el Inicio decía +$601.574. `/api/payments/upcoming` trae todas las
    // reglas (reales y sintéticas), fresco cada vez que se activa el chip, y es exactamente lo que
    // usa el Inicio (`quickLinkFigure("recurrentes")`): misma fuente, misma cifra. La cifra ya espera
    // a `vencimientosOk`, así que sin esa respuesta no se pinta.
    private val reglasParaElResumen by derivedStateOf { upcomingRecurrentes.map { it.rule } }
    internal val resumen: ResumenRecurrentes? by derivedStateOf {
        if (subsParaRecurrentesOk) resumenRecurrentes(reglasParaElResumen, subsParaRecurrentes) else null
    }
    internal val candidatas: List<Subscription> by derivedStateOf {
        candidatasSinConfirmar(subsParaRecurrentes.subscriptions)
    }
    // Las ACTIVAS (AUTO + CONFIRMED), que entre el PR 2 y el PR 4 se quedaron sin ninguna
    // superficie: sumaban en «Gastos recurrentes» y no había dónde verlas ni cómo sacar una. Sale
    // del resumen y no de un filtro propio — ver [suscripcionesActivas].
    internal val activas: List<Recurrente.Suscripcion> by derivedStateOf {
        resumen?.let { suscripcionesActivas(it) } ?: emptyList()
    }
    // Para avisar en una candidata que el dueño ya la tiene anotada a mano, antes de confirmarla.
    internal val clavesDeReglas: Set<String> by derivedStateOf {
        reglasRecurrentes.map { claveDeNombre(it.name) }.toSet()
    }

    // «Próximos» muestra lo que URGE, no todas las reglas: el server manda una entrada por regla
    // (ver [proximosQueUrgen]). Y lo ya sellado va aparte, con su «Deshacer» — apenas se sella, el
    // recurrente desaparece de «Próximos», así que sin esa sección marcar por error no tendría
    // vuelta atrás hasta el mes siguiente (ver [SeccionYaOcurrieron]).
    internal val proximos by derivedStateOf { proximosQueUrgen(upcomingRecurrentes) }
    internal val selladas by derivedStateOf {
        if (ocurrenciasOk) ocurrenciasSelladas(upcomingRecurrentes, ocurrencias) else emptyList()
    }
    // Y lo que quedó abierto pero ya no urge: una regla sale de «Próximos» apenas pasan los días
    // de gracia aunque nadie la haya confirmado, y su periodo sigue abierto igual. Sin esto, un
    // recurrente de principio de mes no tenía dónde confirmarse hasta el mes siguiente — ver
    // [ocurrenciasAbiertasSinUrgencia].
    internal val abiertas by derivedStateOf {
        if (ocurrenciasOk) {
            ocurrenciasAbiertasSinUrgencia(upcomingRecurrentes, ocurrencias, proximos)
        } else emptyList()
    }
    // El aviso ámbar mira lo que se PIDIÓ, no lo que existe: promete una promesa rota, y sin
    // promesa no hay nada que anunciar. Ver [hayRecordatoriosPedidos].
    internal val pidieronRecordatorios by derivedStateOf { hayRecordatoriosPedidos(upcomingRecurrentes) }

    /** Vuelve a traer todo lo del tablero, sin tocar lo de quien lo monta. */
    internal fun recargar() {
        recargas++
    }

    /**
     * Sellar «esto ya ocurrió», **anclado al movimiento que el dueño confirmó**.
     *
     * Después de esto el recurrente deja de leerse como vencido y deja de avisar **ese mes**: su
     * vencimiento vigente pasa a ser el del mes que viene (lo decide el server, ver `dueDateFor`).
     * Al mes siguiente vuelve a estar pendiente solo.
     *
     * **[eventId] ya no puede ser `null`.** El server lo sigue aceptando —hay sellos viejos hechos
     * así en la base del dueño y romperlos sería peor— pero la app no lo manda desde ningún lado:
     * era la puerta de «marcar sin que el movimiento exista» que esta ola vino a cerrar, y dejarla
     * abierta en la firma es dejarla abierta.
     */
    internal fun marcarOcurrio(ruleId: String, period: String, eventId: String) {
        if (ruleId in marcando) return
        marcando = marcando + ruleId
        alcance.launch {
            runCatching { Repositories.wallets.markOccurrence(ruleId, period, eventId) }
                .onSuccess { recargas++ }
                .onFailure { error = it.toUserMessage() }
            marcando = marcando - ruleId
        }
    }

    /**
     * **«No fue este»** — el movimiento que Movi propuso (o emparejó solo) no es esta ocurrencia.
     *
     * Dos escrituras, y el orden importa: primero el rechazo, que es el hecho que tiene que
     * sobrevivir a un F5; después, **si había un sello guardado**, el DELETE que lo borra. Al revés,
     * un corte entre las dos dejaría el período abierto y el emparejamiento automático volviendo a
     * proponer —o a dar por hecho— exactamente lo que se acaba de rechazar.
     *
     * [periodoDelSello] es `null` cuando no hay nada que borrar, que es el caso normal: lo que Movi
     * empareja solo se deriva en cada lectura y no escribe ninguna fila (ver
     * `OccurrenceState.automatica`). Solo lo que el dueño confirmó a mano tiene sello.
     */
    internal fun noFueEste(ruleId: String, eventId: String, periodoDelSello: String?) {
        if (ruleId in marcando) return
        marcando = marcando + ruleId
        descartadas = descartadas + claveDescartada(ruleId, eventId)
        alcance.launch {
            runCatching {
                Repositories.wallets.rechazarOcurrencia(ruleId, eventId)
                if (periodoDelSello != null) {
                    Repositories.wallets.unmarkOccurrence(ruleId, periodoDelSello)
                }
            }
                .onSuccess { recargas++ }
                .onFailure { error = it.toUserMessage() }
            marcando = marcando - ruleId
        }
    }

    /** Deshacer: marcar por error tiene que poder revertirse sin ceremonia. */
    internal fun deshacerOcurrio(ruleId: String, period: String) {
        if (ruleId in marcando) return
        marcando = marcando + ruleId
        alcance.launch {
            runCatching { Repositories.wallets.unmarkOccurrence(ruleId, period) }
                .onSuccess { recargas++ }
                .onFailure { error = it.toUserMessage() }
            marcando = marcando - ruleId
        }
    }

    /**
     * **Volver a barrer los movimientos buscando cobros que se repiten.**
     *
     * Se mudó acá con el resto de Recurrentes, y no era opcional: el barrido automático corre en
     * UN solo lugar del server —después de importar un extracto (`StatementRoutes`)— y el día a
     * día del dueño entra por SMS, que nunca lo dispara. Sin este botón, sacar la pantalla vieja
     * del menú dejaba el detector sin ninguna forma de correr para el camino que él más usa.
     *
     * Vive junto al resumen y no dentro de «Detectadas · por confirmar»: esa sección solo existe
     * cuando YA hay candidatas, y buscar cobros es justamente lo que se hace cuando no hay
     * ninguna todavía.
     */
    internal fun buscarCobros() {
        if (buscandoCobros) return
        buscandoCobros = true
        error = null
        alcance.launch {
            runCatching { Repositories.wallets.detectSubscriptions() }
                .onSuccess {
                    subsParaRecurrentes = it
                    subsParaRecurrentesOk = true
                    // Un barrido puede DESCUBRIR cobros: el gate tiene que enterarse, o la barra
                    // de «¿esto se repite?» ofrecería una regla que duplica uno recién detectado.
                    RecurringOfferGate.recordarLoQueYaHay(reglas = null, suscripciones = it.subscriptions)
                    // Y las listas del chip también, que es lo que decide qué filas se reconocen.
                    recargas++
                }
                .onFailure { error = it.toUserMessage() }
            buscandoCobros = false
        }
    }

    internal fun confirmarCandidata(sub: Subscription, status: SubStatus) {
        if (sub.id in suscripcionesEnVuelo) return
        suscripcionesEnVuelo = suscripcionesEnVuelo + sub.id
        alcance.launch {
            runCatching { Repositories.wallets.updateSubscription(sub.id, sub.copy(status = status)) }
                .onSuccess { RecurringOfferGate.olvidarLoCacheado(); recargas++ }
                .onFailure { error = it.toUserMessage() }
            suscripcionesEnVuelo = suscripcionesEnVuelo - sub.id
        }
    }

    /**
     * **«Quitar» una suscripción activa.** Qué significa quitar depende de quién la puso, y esa
     * decisión no se toma acá: la toma [quitarBorraLaSuscripcion], que es la misma función que
     * decide la etiqueta de origen de la fila. Así la fila no puede decir «la encontró Movi»
     * sobre algo que se va a borrar de verdad.
     *
     * Después de escribir, lo mismo que hace `confirmarCandidata`: el gate se olvida de lo
     * cacheado y `recargas` vuelve a traer las listas. Sin eso, la marca de cada fila de
     * Movimientos, el filtro del chip y el «Flujo libre» se quedan mostrando una suscripción que
     * ya no está.
     */
    internal fun quitarSuscripcion(sub: Subscription) {
        if (sub.id in suscripcionesEnVuelo) return
        suscripcionesEnVuelo = suscripcionesEnVuelo + sub.id
        alcance.launch {
            val resultado = if (quitarBorraLaSuscripcion(sub)) {
                runCatching { Repositories.wallets.deleteSubscription(sub.id) }
            } else {
                runCatching {
                    Repositories.wallets.updateSubscription(sub.id, sub.copy(status = SubStatus.DISMISSED))
                }
            }
            resultado
                .onSuccess { RecurringOfferGate.olvidarLoCacheado(); recargas++ }
                .onFailure { error = it.toUserMessage() }
            suscripcionesEnVuelo = suscripcionesEnVuelo - sub.id
        }
    }
}

/**
 * El estado del tablero, atado a la composición, **con sus lecturas corriendo**.
 *
 * @param activo si el tablero se está mostrando. Todo lo que es SOLO del tablero —suscripciones,
 *   vencimientos, ocurrencias, planes de crédito, el sondeo del permiso de push— se pide únicamente
 *   con esto en `true`: en Movimientos era `activeFilter == CHIP_RECURRENTES`, y en «Todo» o
 *   «Gastos» ninguna de esas lecturas tiene a quién servir. Las reglas y los nombres de las
 *   suscripciones activas se leen siempre, porque Movimientos las usa para marcar filas en todos
 *   los chips.
 * @param recarga la clave de «volver a leer todo» de quien lo monta (el `refreshKey` de
 *   Movimientos: su «Reintentar», anular o editar un movimiento). Además de esta, el tablero
 *   escucha [LocalRefreshTick] y su propia [EstadoDelTableroDeRecurrentes.recargas].
 * @param error dónde escribir lo que falló. Ver [EstadoDelTableroDeRecurrentes].
 * @param vencimientosSiempre lee los vencimientos y las ocurrencias aunque el tablero no se vea.
 *   Ola C: en Plan la tarjeta del disponible saca sus fijos de esas MISMAS dos listas (ver
 *   `rememberDisponibleDelPlan`), con cualquiera de los dos segmentos a la vista; leerlas acá una
 *   sola vez evita pedirlas dos veces y que la tarjeta y el checklist digan cosas distintas del
 *   mismo período. Movimientos lo deja en `false`: ahí nadie más las usa.
 */
@Composable
fun rememberEstadoDelTableroDeRecurrentes(
    activo: Boolean,
    recarga: Int,
    error: MutableState<String?>,
    vencimientosSiempre: Boolean = false,
): EstadoDelTableroDeRecurrentes {
    val alcance = rememberCoroutineScope()
    val estado = remember(alcance, error) { EstadoDelTableroDeRecurrentes(alcance, error) }
    val refreshTick = LocalRefreshTick.current

    // `recargas` también es clave acá, y no solo de las candidatas: confirmar una candidata la
    // vuelve un cobro ACTIVO, y de eso dependen el filtro del chip y la marca de cada fila (ver
    // [nombreRecurrenteDe]). Sin esta clave, el dueño confirmaba «Netflix» y sus movimientos
    // seguían sin reconocerse hasta salir de la pantalla y volver a entrar.
    LaunchedEffect(recarga, refreshTick, estado.recargas) {
        val (reglas, cobros) = RecurringOfferGate.listasParaMovimientos()
        estado.reglasRecurrentes = reglas
        estado.nombresDeSuscripcionesActivas = nombresDeSuscripcionesQueYaSuman(cobros)
    }

    // `recarga` también: es la clave del «Reintentar» del snackbar y de anular/editar un
    // movimiento desde su hoja. Sin ella, reintentar después de un fallo acá no volvía a pedir
    // nada y «Próximos» quedaba cargando para siempre.
    LaunchedEffect(activo, estado.recargas, refreshTick, recarga) {
        if (!activo) return@LaunchedEffect
        intentar { Repositories.wallets.getSubscriptions() }
            .onSuccess {
                estado.subsParaRecurrentes = it
                estado.subsParaRecurrentesOk = true
                // El gate queda con lo mismo que se acaba de traer — igual que hace el
                // re-escaneo de la pantalla vieja (`rescan()`), para que la barra de «¿esto se
                // repite?» de después de guardar no vuelva a proponer una candidata recién
                // confirmada acá.
                RecurringOfferGate.recordarLoQueYaHay(reglas = null, suscripciones = it.subscriptions)
            }
            .onFailure { error.value = it.toUserMessage() }
    }

    // Con `vencimientosSiempre` la clave es la misma con cualquier `activo`: cambiar de segmento en
    // Plan no vuelve a pedir estas dos.
    val leeVencimientos = activo || vencimientosSiempre
    LaunchedEffect(leeVencimientos, estado.recargas, refreshTick, recarga) {
        if (!leeVencimientos) return@LaunchedEffect
        estado.leyendoVencimientos = true
        try {
            ReminderChannelsCache.cargar()
            // En paralelo, como las hace la pantalla vieja: en serie son dos viajes encadenados y
            // la sección se queda a medias el doble de tiempo.
            coroutineScope {
                val porVencer = async { intentar { Repositories.wallets.getUpcomingPayments() } }
                val porOcurrir = async { intentar { Repositories.wallets.getOccurrenceStates() } }
                estado.recurrentesNoSePudieronLeer = false
                porVencer.await()
                    .onSuccess { estado.upcomingRecurrentes = it; estado.vencimientosOk = true }
                    .onFailure { error.value = it.toUserMessage(); estado.recurrentesNoSePudieronLeer = true }
                // Si esta falla no se pinta ninguna propuesta ni ninguna marca: la sección se ve como
                // antes de que existiera. Un «ya ocurrió» que en realidad no se pudo leer sería una
                // afirmación sin respaldo, que es lo único que esta pieza no puede permitirse.
                porOcurrir.await()
                    .onSuccess { estado.ocurrencias = it; estado.ocurrenciasOk = true }
                    .onFailure {
                        if (error.value == null) error.value = it.toUserMessage()
                        estado.recurrentesNoSePudieronLeer = true
                    }
            }
        } finally {
            estado.leyendoVencimientos = false
        }
    }

    // Ver [EstadoDelTableroDeRecurrentes.planesDeCuotas].
    LaunchedEffect(activo, estado.recargas, refreshTick, recarga) {
        if (!activo) return@LaunchedEffect
        intentar { Repositories.wallets.getCredits() }
            .onSuccess { estado.planesDeCuotas = planesDeLasCuotas(it) }
            // Sin plan no hay estimación, y eso ya lo dice la ausencia de la línea.
            .onFailure { estado.planesDeCuotas = emptyMap() }
    }

    // El flujo de permisos del navegador es async (moviPush.js): tras pedirlo se refresca unas
    // veces para que el aviso desaparezca sin reabrir la app. Solo donde el push existe Y con el
    // tablero a la vista — en Android/iOS `status()` es una constante, y en el resto de
    // Movimientos este bucle no tendría a quién servir.
    if (PushOptIn.supported) {
        LaunchedEffect(estado.pushRefreshTick, activo) {
            if (!activo) return@LaunchedEffect
            repeat(20) {
                kotlinx.coroutines.delay(600)
                estado.pushStatus = PushOptIn.status()
            }
        }
    }

    return estado
}

/**
 * **Los renglones del tablero**, para pintarlos dentro de una `LazyColumn` ajena.
 *
 * Es una extensión de `LazyListScope` y no un `@Composable` con su propio scroll porque en
 * Movimientos, con una búsqueda escrita, el tablero y la lista de días van en el MISMO scroll (ver
 * `mostrarLaListaDeDias`): dos listas anidadas no se pueden, y dos scrolls apilados partirían la
 * pantalla en dos.
 *
 * ── El orden: primero lo que pide algo, después lo que solo informa ──────
 *
 * El dueño abre este tablero para responder «¿qué se repite, y qué necesita algo de mí?». Así que
 * arriba va lo accionable —el aviso de que sus recordatorios no van a llegar, los pagos que urgen
 * con su «¿ya ocurrió?», las candidatas por confirmar— y el resumen pasivo queda abajo. En el orden
 * anterior (PR 2) el «Flujo libre» era lo único que había y por eso encabezaba; con la mudanza del
 * PR 3, dejar una cifra que no pide nada por encima de una propuesta abierta sería enterrar lo
 * urgente bajo lo bonito.
 *
 * El PR 5 agrega «Suscripciones activas» al FINAL, debajo del «Flujo libre»: es el desglose de ese
 * total, no una decisión pendiente. Ver [SeccionSuscripcionesActivas].
 *
 * @param periodoVisible el período que se está mirando; el checklist solo se pinta si es
 *   [periodoDeHoy] (ver adentro).
 * @param accountNames los nombres de las cuentas, para decir con qué se paga cada cobro. En
 *   Movimientos es el mismo mapa que usan sus filas, no una lectura nueva.
 */
fun LazyListScope.tableroDeRecurrentes(
    estado: EstadoDelTableroDeRecurrentes,
    periodoVisible: PeriodoFinanciero,
    periodoDeHoy: PeriodoFinanciero,
    ajustesDelPeriodo: PeriodSettings,
    accountNames: Map<String, String>,
    onNavigate: (Screen) -> Unit,
) {
    // ── Aviso: pediste recordatorios y no hay por dónde mandártelos ──────────
    // Se muda con el resto: era la única pantalla que lo mostraba, y sacarla del menú
    // lo habría dejado sin ningún lugar donde salir. No es hipotético — hoy no hay
    // ninguna suscripción de push activa. Ver [shouldShowReminderWarning]: con
    // `canales == null` («todavía no se sabe») NO se avisa nada.
    if (shouldShowReminderWarning(estado.pushStatus, estado.pidieronRecordatorios, ReminderChannelsCache.canales)) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                ReminderWarningBanner(
                    pushStatus = estado.pushStatus,
                    onEnable = {
                        PushOptIn.enable()
                        estado.pushRefreshTick++
                    },
                )
            }
        }
    }

    // ── El checklist del período ────────────────────────────────────────────
    //
    // Va PRIMERO, y es el destino del «Ver todos» del Inicio: el dueño llegaba acá
    // buscando «los recurrentes tipo checklist del mes, con valor y fecha» y se
    // encontraba las mismas obligaciones repartidas en tres tarjetas, ninguna de las
    // cuales enumera el período entero. Las tres siguen abajo, porque contestan otra
    // cosa (qué movimiento fue cada pago, qué quedó sin confirmar, de dónde salió cada
    // sello). Ver [SeccionChecklistDelPeriodo].
    //
    // **Solo para el período en curso.** Movimientos deja navegar a meses anteriores, pero
    // `/api/payments/upcoming` y `/api/payments/occurrences` contestan sobre HOY: pintar sus filas
    // bajo el rótulo de agosto sería afirmar sobre un mes cerrado con los datos de otro. En un
    // período que no es el de hoy, el checklist no se pinta.
    if (periodoVisible == periodoDeHoy) {
        item {
            /**
             * **El checklist del período**, la lista que el dueño pidió ver al tocar «Ver todos» en
             * el Inicio: todo lo que se paga este período —lo tildado incluido—, con su monto y su
             * fecha.
             *
             * Sale de la MISMA función pura que la tarjeta del Inicio (`checklistDelPeriodo`), con
             * las dos lecturas que este tablero ya trae frescas. Una segunda cuenta acá habría
             * vuelto a poner al Inicio y a Movimientos a decir cosas distintas del mismo mes, que
             * es un error que este repo ya cometió y arregló.
             */
            val checklist = remember(
                estado.upcomingRecurrentes, estado.ocurrencias, estado.ocurrenciasOk, periodoVisible, ajustesDelPeriodo,
            ) {
                checklistDelPeriodo(
                    upcoming = estado.upcomingRecurrentes,
                    // Con la lectura de ocurrencias a medias no se tilda nada: un «ya pagado» que en
                    // realidad no se pudo leer sería una afirmación sin respaldo — la misma regla que
                    // gobierna las propuestas de «Próximos».
                    ocurrencias = if (estado.ocurrenciasOk) estado.ocurrencias else emptyList(),
                    periodo = periodoVisible,
                    settings = ajustesDelPeriodo,
                )
            }
            SeccionChecklistDelPeriodo(
                checklist = checklist,
                cargando = !estado.recurrentesNoSePudieronLeer && !(estado.vencimientosOk && estado.ocurrenciasOk),
                pudoLeer = !estado.recurrentesNoSePudieronLeer,
                marcando = estado.marcando,
                descartadas = estado.descartadas,
                // Las cuatro acciones que reemplazaron a la casilla. Ninguna sella nada
                // sin un movimiento detrás — ver el KDoc de [SeccionChecklistDelPeriodo].
                onConfirmar = { pago, eventId ->
                    pago.periodoDelSello?.let { estado.marcarOcurrio(pago.ruleId, it, eventId) }
                },
                // El sello solo existe si lo puso el dueño: lo que Movi empareja solo se
                // deriva en cada lectura y no escribe ninguna fila que borrar.
                onNoFueEste = { pago, eventId ->
                    val sello = pago.periodoDelSello?.takeIf { pago.pagado && !pago.automatica }
                    estado.noFueEste(pago.ruleId, eventId, sello)
                },
                onAnotarMovimiento = { pago -> onNavigate(hojaParaAnotar(pago)) },
                onQuitarLaMarca = { pago ->
                    pago.periodoDelSello?.let { estado.deshacerOcurrio(pago.ruleId, it) }
                },
                onReintentar = { estado.recargar() },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                planesDeCuotas = estado.planesDeCuotas,
            )
        }
    }

    // ── Próximos + «¿esto ya ocurrió?» ──────────────────────────────────────
    item {
        SeccionProximosPagos(
            proximos = estado.proximos,
            ocurrencias = estado.ocurrencias,
            ocurrenciasOk = estado.ocurrenciasOk,
            descartadas = estado.descartadas,
            marcando = estado.marcando,
            // «Todavía no llegó la lista», no «la pantalla está cargando»: mientras
            // no haya respuesta no se dibuja una tarjeta vacía que diga que no hay
            // nada por vencer, porque eso no se sabe todavía.
            cargando = !estado.vencimientosOk,
            conteoVisible = estado.vencimientosOk,
            onAbrirPago = { payment ->
                // F20: la cuota de un crédito y el pago de una tarjeta son reglas
                // sintéticas del server, no algo que se edite acá — se gestionan en
                // Créditos. Misma distinción que hacía la pantalla vieja.
                if (payment.rule.id.startsWith(CREDIT_RULE_PREFIX) ||
                    payment.rule.id.startsWith(CARD_RULE_PREFIX)
                ) {
                    onNavigate(Screen.Credits)
                } else {
                    estado.reglaRecurrenteAEditar = payment.rule
                }
            },
            onMarcar = { ruleId, period, eventId -> estado.marcarOcurrio(ruleId, period, eventId) },
            // El «no» ahora se guarda: sin eso, el emparejamiento automático volvería a
            // proponer lo mismo en la siguiente lectura. Ver [EstadoDelTableroDeRecurrentes.noFueEste].
            onDescartarPropuesta = { ruleId, eventId -> estado.noFueEste(ruleId, eventId, null) },
            onAnotarMovimiento = { pago -> onNavigate(hojaParaAnotar(pago.rule, pago.dueDate)) },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
        )
    }

    // ── Sin confirmar · abiertos que ya no urgen ────────────────────────────
    val abiertas = estado.abiertas
    if (abiertas.isNotEmpty()) {
        item {
            SeccionSinConfirmar(
                abiertas = abiertas,
                descartadas = estado.descartadas,
                marcando = estado.marcando,
                onMarcar = { ruleId, period, eventId -> estado.marcarOcurrio(ruleId, period, eventId) },
                onDescartarPropuesta = { ruleId, eventId -> estado.noFueEste(ruleId, eventId, null) },
                onAnotarMovimiento = { rule ->
                    // La fecha sale de la ocurrencia abierta de ESA regla, que es la que
                    // esta sección está preguntando; el `dueDate` de «Próximos» ya rodó.
                    val vence = abiertas.firstOrNull { it.first.id == rule.id }
                        ?.second?.dueDate.orEmpty()
                    onNavigate(hojaParaAnotar(rule, vence))
                },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )
        }
    }

    // ── Detectadas · por confirmar ──────────────────────────────────────────
    val candidatas = estado.candidatas
    if (candidatas.isNotEmpty()) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                MinSectionHeader(title = "Detectadas · por confirmar", count = candidatas.size)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    candidatas.forEach { s ->
                        CandidataSuscripcionCard(
                            sub = s,
                            accountNames = accountNames,
                            // Contra las reglas Y contra las suscripciones que ya
                            // suman: confirmar un duplicado cuenta el cobro dos veces.
                            // Ver [avisoDeCandidataDuplicada].
                            aviso = avisoDeCandidataDuplicada(
                                candidata = s,
                                clavesDeReglas = estado.clavesDeReglas,
                                activas = estado.subsParaRecurrentes.subscriptions,
                            ),
                            enVuelo = s.id in estado.suscripcionesEnVuelo,
                            onConfirmar = { estado.confirmarCandidata(s, SubStatus.CONFIRMED) },
                            onDescartar = { estado.confirmarCandidata(s, SubStatus.DISMISSED) },
                        )
                    }
                }
            }
        }
    }

    // ── Ya ocurrieron · con su «Deshacer» ───────────────────────────────────
    val selladas = estado.selladas
    if (selladas.isNotEmpty()) {
        item {
            SeccionYaOcurrieron(
                selladas = selladas,
                marcando = estado.marcando,
                onDeshacer = { ruleId, period -> estado.deshacerOcurrio(ruleId, period) },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )
        }
    }

    // ── El resumen, abajo, pegado a lo que resume ───────────────────────────
    item {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            // «Buscar cobros» va en este encabezado —que se pinta siempre con el tablero
            // a la vista— y no en el de las candidatas, que solo existe cuando ya hay
            // alguna. Ver [EstadoDelTableroDeRecurrentes.buscarCobros].
            MinSectionHeader(
                title = "Recurrentes",
                action = if (estado.buscandoCobros) "Buscando…" else "Buscar cobros",
                onAction = { estado.buscarCobros() },
            )
            ResumenFlujoLibreCard(
                // **Sin los vencimientos no hay cifra.** Las cuotas de los créditos
                // llegan solo por ahí y son la mitad del mes del dueño: si esa
                // llamada falló, el total saldría plausible y $5.445.772 más alto
                // que la verdad, sin nada que lo delate. Un guion se entiende; un
                // número optimista, no. Mismo criterio que `subsParaRecurrentesOk`.
                cifras = estado.resumen?.takeIf { estado.vencimientosOk },
            )
        }
    }

    // ── Suscripciones activas · el desglose de «Gastos recurrentes» ─────────
    // Pegado al card de arriba a propósito: es lo que ese total tiene adentro, con
    // la fila marcada «no se suma dos veces» incluida. Ver [SeccionSuscripcionesActivas].
    //
    // **Con `vencimientosOk`, igual que la cifra de arriba**, y no solo por coherencia
    // visual: `resumenRecurrentes` decide qué suscripción está tapada por una regla
    // comparándola contra las reglas del resumen, que INCLUYEN las sintéticas de
    // `/api/payments/upcoming`. Si esa llamada falló, la lista de reglas llega corta,
    // una duplicada puede dejar de marcarse «no se suma dos veces» y el total del pie
    // sale alto. Sería el mismo número plausible y sin nada que lo delate que el card
    // de arriba ya se niega a pintar — y encima debajo de un guion.
    val activas = estado.activas
    estado.resumen
        ?.takeIf { estado.vencimientosOk && activas.isNotEmpty() }
        ?.let { resumen ->
            item {
                SeccionSuscripcionesActivas(
                    activas = activas,
                    // El total sale del resumen, no de una suma sobre `activas`: es el
                    // mismo reparto que ya prorrateó, convirtió y salteó duplicadas.
                    // Ver [ResumenRecurrentes.gastosDeSuscripciones].
                    totalMensual = resumen.gastosDeSuscripciones,
                    sinConvertir = resumen.sinConvertir,
                    // La misma tasa con la que se armó el total de arriba: es lo único
                    // que le permite a [notaDeProrrateo] saber si una fila anual en
                    // dólares de verdad entró a ese total o quedó afuera sin convertir.
                    usdToCop = estado.subsParaRecurrentes.usdToCop,
                    accountNames = accountNames,
                    enVuelo = estado.suscripcionesEnVuelo,
                    // Pregunta antes (ver [ConfirmarEnHoja]); quitar se decide en
                    // [HojasDelTableroDeRecurrentes].
                    onQuitar = { estado.suscripcionPorQuitar = it },
                    onEditar = { estado.suscripcionAEditar = it },
                    abierta = estado.suscripcionesAbiertas,
                    onAlternar = { estado.suscripcionesAbiertas = !estado.suscripcionesAbiertas },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }
        }
}

/**
 * **Las hojas que abren las filas del tablero.** Van afuera de la `LazyColumn` —en el `Box` de la
 * pantalla, junto a las demás hojas— porque una hoja no es un renglón.
 */
@Composable
fun HojasDelTableroDeRecurrentes(estado: EstadoDelTableroDeRecurrentes) {
    estado.suscripcionPorQuitar?.let { sub ->
        ConfirmarEnHoja(
            pregunta = "¿Quitar «${sub.displayName}»?",
            detalle = "Deja de contar en tus recurrentes y en el flujo libre. Los cobros que ya anotaste no se tocan.",
            textoConfirmar = "Quitar",
            ocupado = sub.id in estado.suscripcionesEnVuelo,
            onConfirmar = { estado.quitarSuscripcion(sub); estado.suscripcionPorQuitar = null },
            onCancelar = { estado.suscripcionPorQuitar = null },
        )
    }

    // Editar un recurrente desde su renglón de «Próximos» — la misma hoja, en modo edición, que
    // abre el detalle de un movimiento. Al guardar se invalida el cache del gate y se recarga la
    // sección: el monto o el día nuevos tienen que verse en el mismo renglón que se acaba de
    // tocar, no en la próxima visita.
    estado.reglaRecurrenteAEditar?.let { regla ->
        CreateRecurringRuleSheet(
            onDismiss = { estado.reglaRecurrenteAEditar = null },
            onSaved = {
                estado.reglaRecurrenteAEditar = null
                RecurringOfferGate.olvidarLoCacheado()
                estado.recargar()
            },
            existing = regla,
        )
    }

    // La misma hoja, en modo suscripción. Al guardar se recarga la sección por el mismo camino
    // que usa la edición de una regla: el monto nuevo tiene que verse —y sumar distinto en el
    // «Total al mes»— en la fila que se acaba de tocar.
    estado.suscripcionAEditar?.let { suscripcion ->
        CreateRecurringRuleSheet(
            onDismiss = { estado.suscripcionAEditar = null },
            onSaved = {
                estado.suscripcionAEditar = null
                RecurringOfferGate.olvidarLoCacheado()
                estado.recargar()
            },
            existingSub = suscripcion,
        )
    }
}

/**
 * **Lo que el tablero necesita cuando no lo monta Movimientos**: su propio aviso de error con
 * «Reintentar», su propia recarga y los nombres de las cuentas. Movimientos le da los suyos (un
 * solo aviso para toda la pantalla, las cuentas que ya lee para sus filas); montado solo —en
 * [TableroDeRecurrentes] o en la pestaña Plan— los pone esto, una vez, para que ninguno de los dos
 * lo copie.
 *
 * El aviso va en [aviso]: quien lo monta pone un `SnackbarHost` con ese estado donde le quede bien.
 */
@Stable
class TableroMontadoSolo internal constructor(
    val estado: EstadoDelTableroDeRecurrentes,
    val aviso: SnackbarHostState,
    cuentas: State<List<Account>>,
) {
    /** Para decir con qué se paga cada cobro. Vacío si la lectura falla: las filas no nombran la cuenta. */
    val accountNames: Map<String, String> by derivedStateOf { cuentas.value.associate { it.id to it.name } }
}

/**
 * Ver [TableroMontadoSolo].
 *
 * @param activo lo mismo que en [rememberEstadoDelTableroDeRecurrentes]: en Plan, si el segmento
 *   «Pagos del mes» es el que se ve. Las cuentas tampoco se piden sin él.
 * @param vencimientosSiempre ver [rememberEstadoDelTableroDeRecurrentes].
 */
@Composable
fun rememberTableroMontadoSolo(activo: Boolean = true, vencimientosSiempre: Boolean = false): TableroMontadoSolo {
    val error = remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableStateOf(0) }
    val estado = rememberEstadoDelTableroDeRecurrentes(
        activo = activo,
        recarga = recarga,
        error = error,
        vencimientosSiempre = vencimientosSiempre,
    )
    val refreshTick = LocalRefreshTick.current
    val cuentas = remember { mutableStateOf<List<Account>>(emptyList()) }
    LaunchedEffect(recarga, refreshTick, activo) {
        if (!activo) return@LaunchedEffect
        intentar { Repositories.wallets.getAccounts() }.onSuccess { cuentas.value = it }
    }
    val aviso = remember { SnackbarHostState() }
    LaunchedEffect(error.value) {
        val msg = error.value ?: return@LaunchedEffect
        val result = aviso.showSnackbar(msg, actionLabel = "Reintentar")
        error.value = null
        if (result == SnackbarResult.ActionPerformed) recarga++
    }
    return remember(estado, aviso) { TableroMontadoSolo(estado, aviso, cuentas) }
}

/** Cada fila esqueleto del tablero, para contarlas sin depender de ningún texto. */
const val TAG_ESQUELETO_DEL_TABLERO: String = "esqueleto-del-tablero"

/**
 * **El tablero mientras su primera lectura viaja** (ver
 * [EstadoDelTableroDeRecurrentes.primeraLecturaEnCurso]): el rótulo y la tarjeta del checklist con
 * cuatro filas, y los de «Próximos» con dos. Sin ningún título real: «Checklist del período» sobre
 * una tarjeta vacía ya diría que hay (o que no hay) pagos.
 *
 * Mismos rellenos que las secciones reales (16 dp a los lados y abajo) y la fila de siempre
 * ([FilaDeListaEsqueleto]). Lo pinta Plan; Movimientos sigue con su barra de carga.
 */
fun LazyListScope.tableroDeRecurrentesEsqueleto() {
    listOf(4, 2).forEach { filas ->
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                RotuloDeSeccionEsqueleto()
                MinCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = MinCardVariant.Elevated,
                    padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    repeat(filas) { i ->
                        Box(Modifier.testTag(TAG_ESQUELETO_DEL_TABLERO)) {
                            FilaDeListaEsqueleto(isLast = i == filas - 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * **El tablero entero, montado solo**: su scroll, su aviso de error con «Reintentar», sus hojas y
 * su propia lectura de cuentas. Siempre sobre el período en curso — el único del que
 * `/api/payments/upcoming` y `/api/payments/occurrences` saben contestar.
 *
 * Carga lo mismo que carga dentro de Movimientos, salvo los movimientos: el tablero no los
 * necesita (lo que cada pago tiene detrás llega adentro de las ocurrencias). Las cuentas sí, para
 * decir con qué se paga cada cobro; si esa lectura falla, las filas simplemente no nombran la
 * cuenta, igual que en Movimientos.
 *
 * @param ajustesDelPeriodo el corte del dueño, para que el checklist enumere SU mes y no el del
 *   calendario. Lo lee quien lo monta (del perfil), porque también lo necesita para su encabezado.
 */
@Composable
fun TableroDeRecurrentes(
    ajustesDelPeriodo: PeriodSettings,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val solo = rememberTableroMontadoSolo()
    val periodoDeHoy = remember(ajustesDelPeriodo) {
        periodoActual(Clock.System.now().toEpochMilliseconds(), ajustesDelPeriodo)
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 60.dp),
        ) {
            tableroDeRecurrentes(
                estado = solo.estado,
                periodoVisible = periodoDeHoy,
                periodoDeHoy = periodoDeHoy,
                ajustesDelPeriodo = ajustesDelPeriodo,
                accountNames = solo.accountNames,
                onNavigate = onNavigate,
            )
        }
        SnackbarHost(
            hostState = solo.aviso,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
        HojasDelTableroDeRecurrentes(solo.estado)
    }
}

/**
 * **Lo que dice «Suscripciones activas» cuando está plegada.**
 *
 * El dueño, mirando Recurrentes: *«Debemos dejar que suscripciones sea una opción de filtro o de
 * menú colapsable dentro de recurrentes»*. Con nueve cobros activos la sección medía más que todo
 * lo demás junto, y la enorme mayoría de las veces que se abre esa pantalla no es para revisar el
 * inventario: es para ver qué vence y qué falta confirmar.
 *
 * Plegada, entonces, tiene que seguir diciendo **lo que se mira de reojo** —cuánto suman al mes— y
 * dejar la lista a un toque. Sin esto, plegar escondería la cifra junto con las filas y la sección
 * dejaría de informar en vez de ocupar menos.
 *
 * El total llega calculado desde `ResumenRecurrentes.gastosDeSuscripciones`, que es el mismo que
 * alimenta el «Flujo libre» de arriba: acá no se suma nada, para que las dos cifras no puedan
 * discrepar.
 */
fun resumenPlegadoDeSuscripciones(cuantas: Int, totalMensual: Long): String {
    val plural = if (cuantas == 1) "1 cobro" else "$cuantas cobros"
    return "$plural · ${formatCOP(totalMensual)} al mes"
}

/**
 * PR 2 del rediseño de Recurrentes (2026-09): el card de «Flujo libre», mudado de la pantalla
 * «Recurrentes» (ya borrada) a Movimientos —solo visible con el chip «Recurrentes» activo, ver
 * [com.jvillada.movi.ui.transactions.mostrarResumenDeRecurrentes]— y de ahí, con el resto del
 * tablero, a este archivo. Las cifras salen de [resumenRecurrentes], la misma función
 * pura que ya usaba esa pantalla y el acceso «Recurrentes» del Inicio: mudar DÓNDE se muestra
 * no puede hacer que el número discrepe de los demás lugares que cuentan lo mismo.
 *
 * `cifras == null` mientras las fuentes frescas todavía no llegaron (ver los `LaunchedEffect` que
 * las cargan en [rememberEstadoDelTableroDeRecurrentes]) — un total a medias es peor que un guion. Desde que las
 * cuotas de los créditos entran a este total, «las fuentes» son **dos**: las suscripciones y los
 * vencimientos, que es por donde llegan esas cuotas.
 *
 * ## Las líneas de abajo, y por qué ninguna puede contradecir a otra
 *
 * Todas salen de lo que ENTRÓ al total, nunca de lo que existe en otra parte de la pantalla, así
 * que cualquier combinación de las cuatro sigue siendo cierta al mismo tiempo: dos hablan de una
 * transformación que sufre una fila entre la lista y el total (la TRM y el prorrateo anual), una
 * de lo que el total sí incluye (las cuotas, con su cifra) y otra de lo que deja afuera a
 * propósito (el crédito de pago único). La quinta —el aviso ámbar de lo que no se pudo convertir—
 * es la única que reemplaza a otra: con un cobro sin convertir, prometer que la TRM se aplicó
 * sería falso.
 */
@Composable
private fun ResumenFlujoLibreCard(
    cifras: ResumenRecurrentes?,
) {
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(20.dp),
    ) {
        Text("Flujo libre", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        // **La cifra grande es [ResumenRecurrentes.disponible], no `flujoLibre`.** El mínimo de una
        // tarjeta no es un gasto del mes —eso sigue igual, ver `cuentaComoCompromisoMensual`— pero
        // es plata que hay que pagar sí o sí, y mientras el número grande la ignoraba el dueño leía
        // $601.574 libres sin saber que el mínimo de su Master Black son $1.843.014. Las dos
        // cifras se muestran; la que manda es la que ya descontó lo comprometido.
        Text(
            text = cifras?.let { formatCOP(it.disponible) } ?: "—",
            // Sin estilo a propósito: cifra de una tarjeta, no de la pantalla; `cifra` (42) la infla y `titular` (19) la achica.
            fontSize = 28.sp,
            style = Movi.textos.monto,
            color = Movi.colores.texto,
            letterSpacing = (-1.1).sp,
            lineHeight = 28.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = cifras?.let { subtituloDelFlujoLibre(it) } ?: "Ingresos recurrentes − Gastos recurrentes",
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ingresos recurrentes", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = cifras?.let { formatCOP(it.ingresos) } ?: "—",
                    style = Movi.textos.monto,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.entra,
                    letterSpacing = (-0.3).sp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Gastos recurrentes", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = cifras?.let { formatCOP(it.gastos) } ?: "—",
                    style = Movi.textos.monto,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                    letterSpacing = (-0.3).sp,
                )
            }
        }
        // **Los mínimos, en su propia fila y no adentro de «Gastos recurrentes».** Es la tensión
        // que esta feature tuvo que resolver: el pago de una tarjeta NO es gasto del mes (las
        // compras ya contaron), así que sumarlo ahí contaría la misma plata dos veces; pero sí es
        // plata comprometida, así que ignorarlo deja al dueño con una cifra optimista. La salida
        // es restarlo del disponible **con rótulo propio**, que además es lo único que le permite
        // verificar la resta contra su extracto.
        if (cifras != null && cifras.minimosDeTarjeta > 0L) {
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(ETIQUETA_MINIMOS_DE_TARJETA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        // El signo lo trae el formato, no un prefijo pegado afuera (F36): `formatCOP` ya sabe
                        // escribir un negativo, y duplicarlo daría «− −$…» el día que alguien pase otra cifra.
                        text = formatCOP(-cifras.minimosDeTarjeta),
                        style = Movi.textos.monto,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.texto,
                        letterSpacing = (-0.3).sp,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Libre sin las tarjetas", style = Movi.textos.apoyo, color = Movi.colores.textoMedio, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = formatCOP(cifras.flujoLibre),
                        style = Movi.textos.monto,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.textoMedio,
                        letterSpacing = (-0.3).sp,
                    )
                }
            }
        }
        // Y cuando el mínimo no está cargado, la cifra grande deja de ser un hecho y se dice.
        // Ver [avisoDeMinimosQueFaltan].
        cifras?.let { avisoDeMinimosQueFaltan(it) }?.let { aviso ->
            Spacer(Modifier.height(12.dp))
            Text(aviso, style = Movi.textos.apoyo, color = Movi.colores.aviso, lineHeight = 15.sp)
        }
        // Mismo criterio que la pantalla vieja: un total al que le faltan filas se dice, no se
        // disimula. Ver el KDoc de [ResumenRecurrentes.sinConvertir].
        if (cifras != null && cifras.sinConvertir > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (cifras.sinConvertir == 1) {
                    "Este total no incluye 1 cobro en otra moneda: no pudimos convertirlo a pesos."
                } else {
                    "Este total no incluye ${cifras.sinConvertir} cobros en otra moneda: no pudimos " +
                        "convertirlos a pesos."
                },
                style = Movi.textos.apoyo,
                color = Movi.colores.aviso,
                lineHeight = 15.sp,
            )
        } else if (cifras != null && cifras.hayMonedaExtranjera) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Lo que te cobran en dólares entra al total convertido a pesos con la tasa " +
                    "de cambio más reciente que pudimos consultar.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                lineHeight = 15.sp,
            )
        }
        // Ola 16: la otra transformación que sufre una fila entre la lista de abajo y este total.
        // Va aparte del aviso de la TRM —y no en el mismo `else if`— porque las dos pueden pasar
        // a la vez sobre la misma suscripción, y callar una de ellas dejaría un número sin
        // explicar igual. Sin esta línea, «Gastos recurrentes» cuenta $30.825 de algo que la
        // lista de abajo dice que cuesta $369.900, y no hay forma de saber cuál de los dos está
        // mal. Ver [ResumenRecurrentes.hayCobrosAnuales]: solo aparece si un cobro anual de
        // verdad entró al total.
        if (cifras != null && cifras.hayCobrosAnuales) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Lo que te cobran una vez al año entra repartido: dividimos el cobro en 12 " +
                    "para que este total sea lo que te cuesta cada mes.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                lineHeight = 15.sp,
            )
        }
        // La tercera diferencia entre la lista de abajo y este total, y la más cara: las cuotas
        // de los créditos. El PR anterior las hizo visibles en la lista y puso acá una línea que
        // admitía que el total no las contaba; el dueño decidió que **sí deben contar**, así que
        // esa línea ya sería mentira y en su lugar va la cifra.
        //
        // **Se dice con número y no con un «ya se cuentan».** «Gastos recurrentes» le crece
        // $5.445.772 de un día para el otro; sin decir cuánto de ese total son cuotas, el dueño
        // no tiene cómo verificar el número nuevo contra sus créditos.
        //
        // Y se nombra lo que queda afuera, que es lo que más se nota en su caso: de sus ocho
        // créditos, cuatro los paga alguien más (dos libranzas, dos hipotecas que gira Skandia) y
        // esas cuotas ni siquiera llegan al cliente —el server las filtra con
        // `entraAlBarridoDeAvisos`, porque su salario ya viene neto y contarlas restaría dos
        // veces—. Sin esta frase, la suma de sus cuotas no le va a dar y no va a saber por qué.
        //
        // En `Movi.colores.textoMedio` y no en `Movi.colores.aviso`: no hay nada roto ni nada que reintentar (que es lo
        // que distingue al aviso de la moneda sin convertir); es el alcance del total, como el
        // aviso del prorrateo de acá arriba.
        if (cifras != null && cifras.cuotasDeCredito > 0L) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Las cuotas de tus créditos entran en este total: ${formatCOP(cifras.cuotasDeCredito)} " +
                    "al mes. No contamos las que te descuentan de la nómina ni las que paga otra " +
                    "persona, porque esa plata no sale de tu bolsillo.",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                lineHeight = 15.sp,
            )
        }
        // Y la cuota que se paga una sola vez, que es la otra mitad de decir la verdad sobre las
        // cuotas: entró la del carro, no entró la del «Techo Gardenera» —$10.000.000 a un mes—
        // porque no es un gasto de todos los meses. Se cuenta y se dice por el mismo motivo que
        // `sinConvertir`: es una fila que existe, vence y sale en «Próximos», y este total no la
        // suma a propósito. Ver [ResumenRecurrentes.pagosUnicosFuera].
        if (cifras != null && cifras.pagosUnicosFuera > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                // Sin prometer dónde se ve: «Próximos» muestra lo que URGE (ver
                // `proximosQueUrgen`), así que un pago único con fecha lejana no está ahí todavía,
                // y el aviso depende de que el dueño lo haya pedido. Una frase que no se pueda
                // desmentir en la misma pantalla vale más que una que ayude a buscarlo.
                text = if (cifras.pagosUnicosFuera == 1) {
                    "Un crédito tuyo se paga de una sola vez, así que su cuota no entra en este " +
                        "total: no es un gasto de todos los meses."
                } else {
                    "${cifras.pagosUnicosFuera} créditos tuyos se pagan de una sola vez, así que sus " +
                        "cuotas no entran en este total: no son un gasto de todos los meses."
                },
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                lineHeight = 15.sp,
            )
        }
    }
}

/**
 * **Las suscripciones que hoy están activas** — el inventario que el rediseño de Recurrentes se
 * llevó por delante sin reponer.
 *
 * Vale la pena decir qué se había perdido, porque no era solo una etiqueta: entre el PR 2 y el
 * PR 4 las suscripciones **activas** dejaron de tener cualquier superficie. Seguían sumando en
 * «Gastos recurrentes» (ver [resumenRecurrentes]) y seguían marcando filas en la lista de abajo,
 * pero no había dónde verlas ni cómo sacar una. Eso pesa sobre todo en las
 * [OrigenDeSuscripcion.LA_ENCONTRO_MOVI_Y_LA_ACTIVO_SOLA]: están sumando plata todos los meses
 * sin que el dueño las haya aprobado nunca.
 *
 * Va **debajo del card de «Flujo libre»** y no arriba con lo accionable, porque esto es el
 * desglose de la línea «Gastos recurrentes» de ese card — incluida la fila marcada «no se suma
 * dos veces», que es lo que explica por qué el total no es la suma ingenua de la lista. Separar
 * el total de su desglose es justo la duda que la pantalla vieja documentaba querer evitar.
 * «Quitar» es una corrección, no una decisión pendiente: no compite con las candidatas por
 * confirmar ni con un «¿esto ya ocurrió?».
 *
 * @param enVuelo ids con una acción guardándose, para no dejar tocar «Quitar» dos veces.
 */
@Composable
private fun SeccionSuscripcionesActivas(
    activas: List<Recurrente.Suscripcion>,
    /**
     * Lo que suman estas filas en UN mes, ya en pesos. Llega calculado desde
     * [ResumenRecurrentes.gastosDeSuscripciones] — no se suma acá, ver ahí el porqué.
     */
    totalMensual: Long,
    /**
     * Cuántos cobros quedaron FUERA de [totalMensual] por no poder pasarlos a pesos. Se dice al
     * pie, corto: la explicación larga ya está en el card de «Flujo libre», justo encima.
     */
    sinConvertir: Int,
    /** La tasa con la que se armó el total de arriba. Ver [notaDeProrrateo]. */
    usdToCop: Double,
    /**
     * Los nombres de las cuentas, para poder decir con qué se paga cada cobro. Es el mismo mapa
     * que ya usan las filas de movimientos de esta pantalla, no una lectura nueva: si todavía no
     * llegó, la fila simplemente no nombra la cuenta (ver [contextoDeSuscripcionActiva]).
     */
    accountNames: Map<String, String>,
    enVuelo: Set<String>,
    onQuitar: (Subscription) -> Unit,
    onEditar: (Subscription) -> Unit,
    /** ¿Se ven las filas, o solo el resumen? Ver [resumenPlegadoDeSuscripciones]. */
    abierta: Boolean,
    onAlternar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (activas.isEmpty()) return
    Column(modifier = modifier) {
        MinSectionHeader(
            title = "Suscripciones activas",
            count = activas.size,
            action = if (abierta) "Ocultar" else "Ver",
            onAction = onAlternar,
        )
        if (!abierta) {
            MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                onClick = onAlternar,
            ) {
                Text(
                    text = resumenPlegadoDeSuscripciones(activas.size, totalMensual),
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                )
            }
            return@Column
        }
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            // Hairline en TODAS, la última incluida: ahora hay un pie que separar.
            activas.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Movi.colores.tarjeta),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${item.dayOfMonth}",
                            style = Movi.textos.monto,
                            fontWeight = FontWeight.Medium,
                            color = Movi.colores.texto,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.sub.displayName,
                            style = Movi.textos.cuerpo,
                            fontWeight = FontWeight.Medium,
                            color = Movi.colores.texto,
                            letterSpacing = (-0.1).sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        // Con qué se paga y de dónde salió, en una sola línea de dos segmentos y
                        // decidido en un solo lugar — ver [contextoDeSuscripcionActiva], que es
                        // también quien se calla la cuenta cuando no hay ninguna que nombrar.
                        Text(
                            contextoDeSuscripcionActiva(item, accountNames),
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                        )
                        // Y, solo en un cobro anual que SÍ suma, cuánto de él entra al total de
                        // este mes: es lo que explica por qué el «Flujo libre» de arriba no es la
                        // suma de los montos que se ven acá. Ver [notaDeProrrateo], que devuelve
                        // null en todos los casos donde no hay nada que aclarar.
                        notaDeProrrateo(item, usdToCop)?.let { nota ->
                            Spacer(Modifier.height(2.dp))
                            Text(nota, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            // En SU moneda, sin convertir, y con la periodicidad puesta: una
                            // suscripción en dólares se lee "−US$12" y una anual "−$369.900 al
                            // año". Solo el total de arriba pasa por la TRM, y lo dice. Ver
                            // [textoDelMontoDeSuscripcion].
                            text = textoDelMontoDeSuscripcion(item.sub, conSigno = true),
                            style = Movi.textos.monto,
                            fontWeight = FontWeight.Medium,
                            color = Movi.colores.texto,
                            letterSpacing = (-0.3).sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        val guardando = item.sub.id in enVuelo
                        // «Editar» antes que «Quitar», y en ese orden: es la acción que el dueño
                        // va a querer casi siempre —un precio que subió, un día que se corrió— y
                        // la que no destruye nada. Mientras hay una operación en vuelo las dos se
                        // apagan: tocar «Editar» sobre una fila que se está quitando abriría una
                        // hoja sobre algo que quizá ya no exista.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Editar",
                                style = Movi.textos.apoyo,
                                color = if (guardando) Movi.colores.textoMedio else Movi.colores.marca,
                                modifier = Modifier.clickable { if (!guardando) onEditar(item.sub) },
                            )
                            Text(
                                text = if (guardando) "Quitando…" else "Quitar",
                                style = Movi.textos.apoyo,
                                color = if (guardando) Movi.colores.textoMedio else Movi.colores.sale,
                                modifier = Modifier.clickable { if (!guardando) onQuitar(item.sub) },
                            )
                        }
                    }
                }
                Hairline()
            }
            // ── El total, cerrando la lista que resume ──────────────────────────────
            // Va al pie y no en el encabezado de sección: es la consecuencia de las filas de
            // arriba, y leerlo después de verlas es lo que hace evidente que $369.900 al año no
            // aportan $369.900 al mes. El encabezado además ya lleva el contador.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // «al mes» no es decoración: es lo único que distingue este número de la
                        // suma de los montos que se ven arriba, que da otra cosa.
                        text = "Total al mes",
                        style = Movi.textos.monto,
                        fontWeight = FontWeight.Medium,
                        color = Movi.colores.texto,
                    )
                    if (sinConvertir > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (sinConvertir == 1) {
                                "No incluye 1 cobro que no pudimos pasar a pesos."
                            } else {
                                "No incluye $sinConvertir cobros que no pudimos pasar a pesos."
                            },
                            style = Movi.textos.apoyo,
                            color = Movi.colores.aviso,
                            lineHeight = 15.sp,
                        )
                    }
                }
                Text(
                    // Sin signo, igual que «Gastos recurrentes» en el card de arriba: los dos son
                    // totales de gasto y se leen en la misma pantalla, uno debajo del otro.
                    text = formatCOP(totalMensual),
                    style = Movi.textos.monto,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                    letterSpacing = (-0.3).sp,
                )
            }
        }
    }
}

/**
 * Una candidata «detectada · por confirmar», en su nuevo hogar dentro de Movimientos.
 *
 * Mismo contenido que la fila que tenía la pantalla «Recurrentes» (nombre, monto en su moneda,
 * cuántos meses la vio el detector y su día de cobro, el aviso de «ya la tienes anotada» cuando
 * corresponde) pero con el lenguaje visual de [RecurringOfferBar] —un card compacto, no una hoja
 * modal— que es lo que esta pantalla ya usa para ofrecimientos de esta misma familia.
 */
@Composable
private fun CandidataSuscripcionCard(
    sub: Subscription,
    /** Para poder decir en qué tarjeta vio Movi el cobro. Ver [contextoDeCandidata]. */
    accountNames: Map<String, String>,
    /** «Ya lo tienes como…», o `null` si es nueva de verdad. Ver [avisoDeCandidataDuplicada]. */
    aviso: String?,
    enVuelo: Boolean,
    onConfirmar: () -> Unit,
    onDescartar: () -> Unit,
) {
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sub.displayName, style = Movi.textos.titulo, fontWeight = FontWeight.Medium, color = Movi.colores.texto)
            Text(
                // Con su periodicidad, igual que la fila de una activa. Hoy el detector solo
                // produce cobros mensuales (agrupa por mes, ver `detectSubscriptions`), así que
                // acá esto no cambia nada — se usa la misma función igual, para que el día que
                // una candidata pueda ser anual no haya un renderer al que se le olvidó.
                text = textoDelMontoDeSuscripcion(sub),
                style = Movi.textos.monto,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
            )
        }
        Text(
            // Cuántos meses la vio, qué día cobra y —si Movi la pudo resolver— en qué cuenta vio
            // el cargo. Esa última parte es la que vuelve reconocible un comercio cuyo nombre
            // normalizado no le dice nada al dueño. Ver [contextoDeCandidata].
            text = contextoDeCandidata(sub, accountNames),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (aviso != null) {
            Text(
                text = aviso,
                style = Movi.textos.apoyo,
                color = Movi.colores.aviso,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccionCandidataChip(
                label = when {
                    enVuelo -> "Guardando…"
                    aviso != null -> "Confirmar igual"
                    else -> "Confirmar"
                },
                primary = true,
                habilitado = !enVuelo,
                onClick = onConfirmar,
            )
            AccionCandidataChip(label = "No es", primary = false, habilitado = !enVuelo, onClick = onDescartar)
        }
    }
}

/** Los botones «Confirmar» / «No es» de una candidata — mismo lenguaje que el de la hoja vieja. */
@Composable
private fun AccionCandidataChip(label: String, primary: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) Movi.colores.texto else Movi.colores.tarjeta)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = if (primary) Movi.colores.fondo else Movi.colores.texto)
    }
}
