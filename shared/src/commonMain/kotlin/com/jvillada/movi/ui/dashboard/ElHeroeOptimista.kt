package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.claveComparableDeNombre
import com.jvillada.movi.shared.model.esDeTuPlata
import com.jvillada.movi.shared.model.nombreDeMovimientoPegaConRegla
import com.jvillada.movi.shared.model.cuentaEnGastosEIngresos
import com.jvillada.movi.shared.model.esGastoDelPeriodo
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.components.ds.CambioDelDueno
import com.jvillada.movi.ui.components.ds.VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/*
 * # Vuelta 82 · «Te quedan» y «Tu plata» cambian juntas
 *
 * La revisión 8 (`r8-deshecho-tira`): al anotar un gasto y al deshacerlo, «Tu plata» cambiaba a los ~500 ms
 * (llegan las cuentas) y «Te quedan» a los ~1,5 s (llega el resumen), mientras el encabezado decía
 * «Actualizando…». Dos cifras de la misma tarjeta se contradecían justo cuando el dueño miraba.
 *
 * Dos arreglos, los dos sin regla de plata nueva:
 *
 * 1. **El optimista**: cuando el cambio lo hizo el dueño (anotar, anular o deshacer), el héroe lo aplica
 *    **en el acto** sobre sus propios datos —el gasto se suma al gasto del día ([DashboardData.gastoVariablePorDia])
 *    y se resta del saldo de su cuenta— y las dos cifras salen de las funciones de siempre
 *    ([disponibleDelInicio], [heroBalance]). Un gasto de una cuenta de Tu plata resta en las dos; uno con
 *    tarjeta, solo en «Te quedan» (la tarjeta no es Tu plata). Lo que cuenta lo decide [esGastoDelPeriodo] y
 *    [cuentaEnGastosEIngresos], con [montoContado] (tu parte, si es de un grupo). **Si es dudoso, nada**: un
 *    ingreso, otra moneda, un traspaso, otro período, una cuenta que no es de dinero ni tarjeta, una
 *    cuenta que no es de Tu plata (Nu ahorro, la AFC: el server lo compensa con «pagado desde fuera»), la
 *    categoría de un apartado de esa cuenta (baja el apartado, no las cifras), o algo que se parezca a un
 *    pago del período —por categoría, por nombre o comercio, por su destino, o ya sellado a uno— (puede
 *    ser el fijo, que ya está restado) → se espera al server.
 * 2. **Mientras la carga está en vuelo, el héroe no cambia a medias** (también sin optimista): antes, un
 *    cambio del dueño se saltaba la congelación de la vuelta 75 y las cuentas pintaban «Tu plata» un
 *    segundo antes que «Te quedan». Ahora las dos esperan juntas el final de la carga.
 *
 * El server confirma después: cuando termina **bien** una carga que **empezó** después de que el server
 * tuviera el cambio ([EstadoDelOptimista.cargaBuenaEmpezadaEn] ≥ [MovimientoDelDueno.listoEn]), el héroe deja el
 * optimista y muestra lo que leyó; si difiere, la cifra cuenta hasta la del server
 * ([com.jvillada.movi.ui.components.ds.rememberCifraQueCuenta]). En el teléfono, «el server lo tiene» es
 * cuando la fila subió (ver [MovimientosDelDueno]).
 */

/**
 * Un movimiento que el dueño acaba de anotar ([signo] +1) o de anular o deshacer (−1), registrado en [en]
 * (epoch ms). [listoEn] es desde cuándo **el server ya lo tiene**: `null` mientras no (la escritura del
 * dueño no terminó, o en el teléfono la fila todavía no subió). Solo una carga buena que **empezó** desde
 * [listoEn] lo trae.
 */
data class MovimientoDelDueno(val id: Long, val evento: FinancialEvent, val signo: Int, val en: Long, val listoEn: Long? = null)

/**
 * **Lo que el héroe adelanta**, todo en un valor (re-revisión de la vuelta 82):
 *
 * - [base]: los datos del Inicio **de antes** del primer movimiento, fijados al pasar de ninguno a alguno
 *   (lo último que leyó el Inicio, `DashboardDataCache.data`). No se vuelve a tomar al volver al Inicio ni
 *   al soltarse un movimiento: sin señal, las cuentas que se leen después ya traen el gasto del espejo local,
 *   y retomar la base lo restaba dos veces.
 * - [movimientos]: **todos** los que el dueño hizo desde entonces; el héroe muestra [base] con todos.
 * - [cargaBuenaEmpezadaEn]: cuándo empezó la última carga buena del Inicio.
 *
 * Mientras alguno siga [pendiente], se adelanta todo sobre [base]; cuando ninguno, se suelta todo (y la
 * [base]) y el héroe muestra lo que leyó.
 */
data class EstadoDelOptimista(
    val base: DashboardData? = null,
    val movimientos: List<MovimientoDelDueno> = emptyList(),
    val cargaBuenaEmpezadaEn: Long? = null,
    val siguienteId: Long = 1L,
)

/**
 * Cuánto puede quedar adelantado algo que nunca llegó al server (sin señal, o rechazado): pasado esto se
 * suelta igual y el héroe vuelve a lo que diga la última carga.
 */
const val VIGENCIA_SIN_SUBIR_MS: Long = 10 * 60_000L

/** Cada cuánto, como mucho, se vuelve a mirar si lo que no subió ya subió (el ciclo del `SyncEngine`). */
const val ESPERA_ENTRE_SUBIDAS_MS: Long = 30_000L

/**
 * ¿[m] sigue sin llegar a lo que lee el Inicio? Sin [MovimientoDelDueno.listoEn], o sin una carga buena
 * que haya empezado desde entonces. Pura.
 */
fun pendiente(m: MovimientoDelDueno, cargaBuenaEmpezadaEn: Long?): Boolean {
    val listo = m.listoEn ?: return true
    return cargaBuenaEmpezadaEn == null || cargaBuenaEmpezadaEn < listo
}

/** Cuándo vence [m]: 15 s después de estar listo; sin estar listo, [VIGENCIA_SIN_SUBIR_MS] después de hecho. */
fun venceEn(m: MovimientoDelDueno): Long = m.listoEn?.let { it + VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS } ?: (m.en + VIGENCIA_SIN_SUBIR_MS)

/**
 * [e] sin lo que ya no se adelanta: si alguno venció, o si ya ninguno está [pendiente], se suelta todo (los
 * movimientos y la base). Pura.
 */
fun EstadoDelOptimista.podado(ahora: Long): EstadoDelOptimista = when {
    movimientos.isEmpty() -> copy(base = null)
    movimientos.any { ahora > venceEn(it) } -> copy(base = null, movimientos = emptyList())
    movimientos.none { pendiente(it, cargaBuenaEmpezadaEn) } -> copy(base = null, movimientos = emptyList())
    else -> this
}

/**
 * **Lo que el héroe adelanta ahora**: la base y todos los movimientos, o `null` si no hay nada pendiente
 * (o falta la base: el Inicio nunca leyó nada, y el héroe espera al server). Pura.
 */
fun EstadoDelOptimista.adelanto(ahora: Long): Pair<DashboardData, List<MovimientoDelDueno>>? {
    val vigente = podado(ahora)
    val base = vigente.base ?: return null
    return if (vigente.movimientos.isEmpty()) null else base to vigente.movimientos
}

/** En cuántos ms vence el primero de los movimientos de [e] (para recomponer entonces), o `null`. */
fun EstadoDelOptimista.proximoVencimiento(ahora: Long): Long? =
    movimientos.minOfOrNull { venceEn(it) - ahora }?.coerceAtLeast(0L)

/**
 * **Los movimientos que el dueño acaba de hacer**, para el optimista del héroe. Los registra el aviso de
 * abajo ([com.jvillada.movi.ui.espacios.mostrarLoAnotado], la anulación de la hoja del movimiento y sus
 * «Deshacer»): [registrar] cuando empieza, [listo] cuando su escritura terminó. Cada registro también
 * marca [CambioDelDueno]: la cifra cuenta. Lo limpia `SessionManager.clear`.
 *
 * **El teléfono.** Allá `postEvent` y `voidEvent` escriben en el espejo local y el `SyncEngine` sube hasta
 * 30 s después, pero el Inicio lee del server. [listo] pregunta
 * [com.jvillada.movi.shared.repository.WalletRepository.desdeCuandoLoTieneElServer] (el `syncedAt` real de
 * la fila); si todavía no, pide una vuelta del `SyncEngine` (`subirLoPendienteAhora`) y espera
 * [ESPERA_ENTRE_SUBIDAS_MS], o menos si [despertar] avisa que algo subió (`alSubirAlgo`). Cuando sube, anota
 * [MovimientoDelDueno.listoEn] y pide recargar la pantalla a la vista. El bucle se corta si el movimiento
 * venció o salió. En la web el server ya lo tiene al volver la escritura.
 *
 * **Todo el estado es un [MutableStateFlow]** que se cambia con `update {}`: [listo] escribe desde otro hilo,
 * y un `mutableStateOf` leído y escrito desde dos hilos podía perder un registro o revivir uno después de
 * [limpiar].
 */
object MovimientosDelDueno {
    private val _estado = MutableStateFlow(EstadoDelOptimista())

    /** Lo que el héroe lee (`collectAsState`). */
    val estado: StateFlow<EstadoDelOptimista> = _estado.asStateFlow()

    private val despertador = MutableStateFlow(0)
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Cómo se habla con el repositorio y con la pantalla. Las pruebas lo cambian por uno sin red. */
    class Costuras(
        val desdeCuando: suspend (eventoId: String) -> Long? = { id ->
            runCatching { com.jvillada.movi.data.Repositories.wallets.desdeCuandoLoTieneElServer(id) }.getOrNull()
        },
        val subirAhora: suspend () -> Unit = { runCatching { com.jvillada.movi.data.Repositories.wallets.subirLoPendienteAhora() } },
        val pedirRecarga: () -> Unit = { com.jvillada.movi.data.actualizarAMano() },
        /** La base al registrar el primero: lo último que leyó el Inicio. */
        val base: () -> DashboardData? = { DashboardDataCache.data },
        val ahora: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    )

    /** Ver [Costuras]. `null`: las de la app. */
    var costurasDePrueba: Costuras? = null
    private val costuras: Costuras get() = costurasDePrueba ?: COSTURAS_DE_LA_APP
    private val COSTURAS_DE_LA_APP = Costuras()

    /** El dueño empezó a anotar ([signo] +1) o anular (−1) [evento]. Devuelve su id, para [listo] o [quitar]. */
    fun registrar(evento: FinancialEvent, signo: Int, ahora: Long = costuras.ahora()): Long {
        CambioDelDueno.marcar(ahora)
        val base = costuras.base
        var id = 0L
        _estado.update { antes ->
            val e = antes.podado(ahora)
            id = e.siguienteId
            e.copy(
                // La base se fija al pasar de ninguno a alguno, y no se vuelve a tomar hasta soltarlos todos.
                base = if (e.movimientos.isEmpty()) base() else e.base,
                movimientos = e.movimientos + MovimientoDelDueno(id, evento, signo, ahora),
                siguienteId = id + 1,
            )
        }
        return id
    }

    /**
     * La escritura de [id] terminó: listo desde que el server lo tiene (nunca antes de ahora, cuando la
     * escritura volvió). Si todavía no lo tiene (el teléfono), se sube y se espera.
     */
    fun listo(id: Long) {
        val c = costuras
        val llamada = c.ahora()
        val evento = _estado.value.movimientos.firstOrNull { it.id == id }?.evento ?: return
        alcance.launch {
            var vuelta = 0
            while (true) {
                val m = _estado.value.movimientos.firstOrNull { it.id == id } ?: return@launch
                if (m.listoEn != null) return@launch
                if (c.ahora() > venceEn(m)) {
                    podar(c.ahora())
                    return@launch
                }
                if (vuelta > 0) c.subirAhora()
                val desde = c.desdeCuando(evento.id)
                if (desde != null) {
                    marcarListo(id, maxOf(desde, llamada))
                    if (vuelta > 0) c.pedirRecarga()
                    return@launch
                }
                val visto = despertador.value
                if (vuelta > 0) withTimeoutOrNull(ESPERA_ENTRE_SUBIDAS_MS) { despertador.first { it != visto } }
                vuelta++
            }
        }
    }

    /** Algo subió (`alSubirAlgo` del `SyncEngine`): lo que espera vuelve a mirar ya. */
    fun despertar() {
        despertador.update { it + 1 }
    }

    /** Anota que el server tiene [id] desde [en]. */
    fun marcarListo(id: Long, en: Long) {
        _estado.update { e -> e.copy(movimientos = e.movimientos.map { if (it.id == id && it.listoEn == null) it.copy(listoEn = en) else it }) }
    }

    /** Lo que se registró con [id] no pasó (deshacer falló): sale. */
    fun quitar(id: Long) {
        _estado.update { e -> e.copy(movimientos = e.movimientos.filterNot { it.id == id }).let { if (it.movimientos.isEmpty()) it.copy(base = null) else it } }
    }

    /** Terminó bien una carga del Inicio que empezó en [en]: lo que ya trae se suelta. */
    fun cargaBuenaEmpezo(en: Long, ahora: Long = costuras.ahora()) {
        _estado.update { it.copy(cargaBuenaEmpezadaEn = en).podado(ahora) }
    }

    /** Suelta lo vencido (el héroe lo pide al vencer). */
    fun podar(ahora: Long = costuras.ahora()) {
        _estado.update { it.podado(ahora) }
    }

    fun limpiar() {
        _estado.value = EstadoDelOptimista()
        costurasDePrueba = null
    }
}

/**
 * **¿La carga que empezó en [empezo] fue buena?** Si trajo el resumen ([resumenLlego]) y las cuentas
 * ([cuentasLlegaron]) y nada de lo que sostiene el héroe falló ([fallo]); entonces cuenta desde [empezo],
 * si no, queda el [anterior]. Pura.
 */
fun cargaBuenaEmpezadaEn(anterior: Long?, empezo: Long, resumenLlego: Boolean, cuentasLlegaron: Boolean, fallo: Boolean): Long? =
    if (resumenLlego && cuentasLlegaron && !fallo) empezo else anterior

/**
 * **[data] con los [movimientos] del dueño adelantados**, o `null` si alguno es dudoso (ver el KDoc del
 * archivo): entonces el héroe espera al server con todo. Pura.
 */
fun datosConLosMovimientosDelDueno(data: DashboardData, movimientos: List<MovimientoDelDueno>, hoy: LocalDate): DashboardData? =
    movimientos.fold(data as DashboardData?) { d, m -> d?.let { datosConElMovimiento(it, m.evento, m.signo, hoy) } }

/**
 * **[data] con [evento] sumado ([signo] +1) o quitado (−1)**: su [montoContado] en el gasto del día y su
 * monto en el saldo de su cuenta si es de dinero. `null` si es dudoso. Pura.
 */
fun datosConElMovimiento(data: DashboardData, evento: FinancialEvent, signo: Int, hoy: LocalDate): DashboardData? {
    if (evento.type != TransactionType.EXPENSE || evento.currency != "COP" || evento.transferId != null) return null
    val cuentas = data.accounts ?: return null
    val cuenta = cuentas.firstOrNull { it.id == evento.accountId } ?: return null
    if (!esGastoDelPeriodo(evento, cuenta.type) || !cuentaEnGastosEIngresos(evento)) return null
    // Revisión de la vuelta 82: una cuenta que no es de Tu plata (Nu ahorro, la AFC) no mueve «Te quedan»
    // en el server: el gasto variable sube y «pagado desde fuera» lo compensa (`PlataDelPeriodo`).
    if (cuenta.type != AccountType.CREDIT_CARD && !cuenta.esDeTuPlata()) return null
    val periodo = data.periodoActual ?: return null
    val inicio = inicioDelPeriodo(periodo, data.ajustesDePeriodo)
    val fin = inicioDelPeriodo(periodoSiguiente(periodo), data.ajustesDePeriodo)
    val dia = epochMillisToAppDate(evento.timestamp)
    if (dia < inicio || dia >= fin || dia > hoy) return null
    // Algo que se parece a un pago del período puede ser ese fijo (ya restado): no se sabe sin el server.
    val pagos = data.upcoming ?: return null
    if (pareceUnPagoDelPeriodo(evento, pagos.map { it.rule }, data)) return null
    // Un gasto en la categoría de un apartado de su misma cuenta baja el apartado, no las cifras.
    val clave = claveComparableDeNombre(evento.category)
    if (data.apartados.orEmpty().any { a ->
            a.accountId == evento.accountId && a.desde <= evento.timestamp &&
                a.categoria?.let { claveComparableDeNombre(it) == clave && clave.isNotEmpty() } == true
        }
    ) return null
    val gasto = data.gastoVariablePorDia ?: return null
    val diaClave = dia.toString()
    val contado = montoContado(evento) * signo
    val delDia = (gasto[diaClave] ?: 0L) + contado
    // Quitar lo que nunca estuvo en el gasto del día (lo absorbió un fijo que no se vio acá) lo dejaría en
    // negativo e inflaría «Te quedan»: dudoso.
    if (delDia < 0L) return null
    val nuevasCuentas = when (cuenta.type) {
        AccountType.SAVINGS, AccountType.CHECKING, AccountType.CASH ->
            cuentas.map { if (it.id == cuenta.id) it.conSaldoMovido(-signo * evento.amount) else it }
        // La tarjeta no es Tu plata: su deuda crece, pero el héroe no la muestra.
        AccountType.CREDIT_CARD -> cuentas
        else -> return null
    }
    return data.copy(
        accounts = nuevasCuentas,
        gastoVariablePorDia = gasto + (diaClave to delDia),
    )
}

/**
 * **¿[evento] se parece a un pago del período?** Con las señas del emparejador del checklist
 * (`candidatosPuntuados` del server): la categoría ([claveComparableDeNombre]), el nombre o el comercio
 * ([nombreDeMovimientoPegaConRegla] contra el nombre de la regla), un destino asociado a la regla con el
 * mismo monto, o un
 * sello que ya lo nombra en [DashboardData.ocurrencias] (también como candidato). Las suscripciones, por su
 * nombre. Ante cualquier seña, sí: el optimista espera al server. Pura.
 */
fun pareceUnPagoDelPeriodo(evento: FinancialEvent, reglas: List<RecurringRule>, data: DashboardData): Boolean {
    val clave = claveComparableDeNombre(evento.category)
    val textos = listOfNotNull(evento.description, evento.merchant).filter { it.isNotBlank() }
    fun pegaElNombre(nombre: String) = textos.any { nombreDeMovimientoPegaConRegla(nombre, it) }
    val porRegla = reglas.filter { it.type == TransactionType.EXPENSE }.any { r ->
        (clave.isNotEmpty() && claveComparableDeNombre(r.category) == clave) || pegaElNombre(r.name) ||
            // El destino absorbe el fijo con el monto exacto (identidad fuerte del emparejador): sin los
            // destinos a mano, un destino asociado y el mismo monto ya es dudoso.
            (r.destinoConocidoId != null && r.amount == evento.amount)
    }
    val sellado = data.ocurrencias.orEmpty().any { o -> o.eventId == evento.id || o.candidates.any { it.id == evento.id } }
    val suscripcion = data.subscriptions?.subscriptions.orEmpty().any { pegaElNombre(it.displayName) }
    return porRegla || sellado || suscripcion
}

/** La cuenta con [delta] pesos más en su saldo (y en su total estimado, si lo trae). */
private fun Account.conSaldoMovido(delta: Long): Account = copy(
    balance = balance + delta,
    balancesByCurrency = if ("COP" in balancesByCurrency) balancesByCurrency + ("COP" to (balancesByCurrency.getValue("COP") + delta)) else balancesByCurrency,
    estimatedTotalCop = estimatedTotalCop?.plus(delta),
)
