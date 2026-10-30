package com.jvillada.movi.ui.dashboard

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
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
import kotlinx.coroutines.delay
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
 * tuviera el cambio ([LocalCargaBuenaDelInicioEmpezadaEn] ≥ [MovimientoDelDueno.listoEn]), el héroe deja el
 * optimista y muestra lo que leyó; si difiere, la cifra cuenta hasta la del server
 * ([com.jvillada.movi.ui.components.ds.rememberCifraQueCuenta]). En el teléfono, «el server lo tiene» es
 * cuando la fila subió (ver [MovimientosDelDueno]).
 */

/**
 * Un movimiento que el dueño acaba de anotar ([signo] +1) o de anular o deshacer (−1), registrado en [en]
 * (epoch ms). [listoEn] es desde cuándo **el server ya lo tiene**: `null` mientras no (la escritura del
 * dueño no terminó, o en el teléfono la fila todavía no subió). Solo una carga buena que **empezó** desde
 * [listoEn] puede traerlo, así que solo esa lo suelta.
 */
data class MovimientoDelDueno(val id: Long, val evento: FinancialEvent, val signo: Int, val en: Long, val listoEn: Long? = null)

/**
 * Cuánto puede quedar adelantado algo que nunca llegó al server (sin señal, o rechazado): pasado esto se
 * suelta igual y el héroe vuelve a lo que diga la última carga.
 */
const val VIGENCIA_SIN_SUBIR_MS: Long = 10 * 60_000L

/** Cada cuánto se vuelve a mirar si lo que no subió ya subió (el ciclo del `SyncEngine`). */
const val ESPERA_ENTRE_SUBIDAS_MS: Long = 30_000L

/**
 * **Los movimientos que el dueño acaba de hacer**, para el optimista del héroe. Los registra el aviso de
 * abajo ([com.jvillada.movi.ui.espacios.mostrarLoAnotado], la anulación de la hoja del movimiento y sus
 * «Deshacer»): [registrar] cuando empieza, [listo] cuando su escritura terminó. Cada registro también
 * marca [CambioDelDueno]: la cifra cuenta. Lo limpia `SessionManager.clear`.
 *
 * **Revisión de la vuelta 82 · el teléfono.** Allá `postEvent` y `voidEvent` escriben en el espejo local y
 * el `SyncEngine` sube hasta 30 s después, pero el Inicio lee del server: la carga que arranca al guardar
 * leía el server **sin** el gasto, y si soltaba el optimista la cifra volvía a la vieja. Ahora [listo]
 * pregunta si el server ya lo tiene ([com.jvillada.movi.shared.repository.WalletRepository.elServerYaTieneElMovimiento]);
 * si no, pide una vuelta del `SyncEngine` ya (`subirLoPendienteAhora`), y cuando sube anota [MovimientoDelDueno.listoEn]
 * y pide recargar la pantalla a la vista. Sin señal, sigue intentando cada [ESPERA_ENTRE_SUBIDAS_MS] y el
 * optimista se queda (hasta [VIGENCIA_SIN_SUBIR_MS]). En la web el server ya lo tiene al volver la escritura.
 */
object MovimientosDelDueno {
    private var siguiente = 0L
    private val lista = mutableStateOf<List<MovimientoDelDueno>>(emptyList())
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Cómo se habla con el repositorio y con la pantalla. Las pruebas lo cambian por uno sin red. */
    class Costuras(
        val elServerLoTiene: suspend (eventoId: String) -> Boolean = { id ->
            runCatching { com.jvillada.movi.data.Repositories.wallets.elServerYaTieneElMovimiento(id) }.getOrDefault(false)
        },
        val subirAhora: suspend () -> Unit = { runCatching { com.jvillada.movi.data.Repositories.wallets.subirLoPendienteAhora() } },
        val pedirRecarga: () -> Unit = { com.jvillada.movi.data.actualizarAMano() },
        val esperar: suspend (Long) -> Unit = { delay(it) },
    )

    /** Ver [Costuras]. `null`: las de la app. */
    var costurasDePrueba: Costuras? = null
    private val costuras: Costuras get() = costurasDePrueba ?: COSTURAS_DE_LA_APP
    private val COSTURAS_DE_LA_APP = Costuras()

    /** El dueño empezó a anotar ([signo] +1) o anular (−1) [evento]. Devuelve su id, para [listo] o [quitar]. */
    fun registrar(evento: FinancialEvent, signo: Int, ahora: Long = Clock.System.now().toEpochMilliseconds()): Long {
        CambioDelDueno.marcar(ahora)
        val m = MovimientoDelDueno(++siguiente, evento, signo, ahora)
        lista.value = lista.value.filter { vigente(it, ahora) } + m
        return m.id
    }

    /**
     * La escritura de [id] terminó (en [ahora]): si el server ya la tiene, queda lista desde [ahora]; si no
     * (el teléfono), se sube y queda lista cuando subió, y la pantalla a la vista recarga.
     */
    fun listo(id: Long, ahora: Long = Clock.System.now().toEpochMilliseconds()) {
        val m = lista.value.firstOrNull { it.id == id } ?: return
        val c = costuras
        alcance.launch {
            if (c.elServerLoTiene(m.evento.id)) {
                marcarListo(id, ahora)
                return@launch
            }
            while (lista.value.any { it.id == id && it.listoEn == null }) {
                c.subirAhora()
                if (c.elServerLoTiene(m.evento.id)) {
                    marcarListo(id, Clock.System.now().toEpochMilliseconds())
                    c.pedirRecarga()
                    return@launch
                }
                c.esperar(ESPERA_ENTRE_SUBIDAS_MS)
            }
        }
    }

    /** Anota que el server tiene [id] desde [en]. */
    fun marcarListo(id: Long, en: Long) {
        lista.value = lista.value.map { if (it.id == id && it.listoEn == null) it.copy(listoEn = en) else it }
    }

    /** Lo que se registró con [id] no pasó (deshacer falló): sale. */
    fun quitar(id: Long) {
        lista.value = lista.value.filterNot { it.id == id }
    }

    /**
     * Los que el héroe todavía tiene que adelantar: los que el server no tiene ([MovimientoDelDueno.listoEn]
     * en `null`) y los que tiene pero ninguna carga buena **empezada** después ([cargaBuenaEmpezadaEn])
     * trajo todavía. Una carga caída, o una que empezó antes, no suelta nada.
     */
    fun pendientes(cargaBuenaEmpezadaEn: Long?, ahora: Long = Clock.System.now().toEpochMilliseconds()): List<MovimientoDelDueno> =
        lista.value.filter { m ->
            val listo = m.listoEn
            vigente(m, ahora) && (listo == null || cargaBuenaEmpezadaEn == null || cargaBuenaEmpezadaEn < listo)
        }

    /** En cuántos ms vence el primero de [pendientes] (para recomponer entonces), o `null`. */
    fun proximoVencimiento(pendientes: List<MovimientoDelDueno>, ahora: Long = Clock.System.now().toEpochMilliseconds()): Long? =
        pendientes.minOfOrNull { venceEn(it) - ahora }?.coerceAtLeast(0L)

    private fun venceEn(m: MovimientoDelDueno): Long =
        m.listoEn?.let { it + VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS } ?: (m.en + VIGENCIA_SIN_SUBIR_MS)

    private fun vigente(m: MovimientoDelDueno, ahora: Long): Boolean = ahora <= venceEn(m) && ahora >= m.en

    fun limpiar() {
        lista.value = emptyList()
        costurasDePrueba = null
    }
}

/**
 * Cuándo **empezó** la última carga **buena** del Inicio (epoch ms): una que trajo el resumen y las cuentas
 * del server sin fallar. `null` si ninguna en esta sesión. La provee la pantalla del Inicio; el héroe la
 * compara con [MovimientoDelDueno.listoEn] para saber si lo que lee ya trae lo que hizo el dueño.
 */
val LocalCargaBuenaDelInicioEmpezadaEn = staticCompositionLocalOf<Long?> { null }

/**
 * **El sello de la última carga buena** (revisión de la vuelta 82): la carga que empezó en [empezo] lo
 * reemplaza solo si trajo el resumen ([resumenLlego]) y las cuentas ([cuentasLlegaron]) y nada de lo que
 * sostiene el héroe falló ([fallo]); si no, queda el [anterior]. Pura.
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
