package com.jvillada.movi.ui.dashboard

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.cuentaEnGastosEIngresos
import com.jvillada.movi.shared.model.esGastoDelPeriodo
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.components.ds.CambioDelDueno
import com.jvillada.movi.ui.components.ds.VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS
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
 *    ingreso, otra moneda, un traspaso, otro período, una cuenta que no es de dinero ni tarjeta, o una
 *    categoría de un pago del período (puede ser el fijo, que ya está restado) → se espera al server.
 * 2. **Mientras la carga está en vuelo, el héroe no cambia a medias** (también sin optimista): antes, un
 *    cambio del dueño se saltaba la congelación de la vuelta 75 y las cuentas pintaban «Tu plata» un
 *    segundo antes que «Te quedan». Ahora las dos esperan juntas el final de la carga.
 *
 * El server confirma después: cuando termina una carga que empezó después del cambio
 * ([LocalCargaDelInicioTerminadaEn] posterior a la marca), el héroe deja el optimista y muestra lo que leyó;
 * si difiere, la cifra cuenta hasta la del server ([com.jvillada.movi.ui.components.ds.rememberCifraQueCuenta]).
 */

/** Un movimiento que el dueño acaba de anotar ([signo] +1) o de anular o deshacer (−1), en [en] (epoch ms). */
data class MovimientoDelDueno(val id: Long, val evento: FinancialEvent, val signo: Int, val en: Long)

/**
 * **Los movimientos que el dueño acaba de hacer**, para el optimista del héroe. Los registra el aviso de
 * abajo ([com.jvillada.movi.ui.espacios.mostrarLoAnotado], la anulación de la hoja del movimiento y sus
 * «Deshacer»). Cada registro también marca [CambioDelDueno]: la cifra cuenta. Lo limpia `SessionManager.clear`.
 */
object MovimientosDelDueno {
    private var siguiente = 0L
    private val lista = mutableStateOf<List<MovimientoDelDueno>>(emptyList())

    /** El dueño anotó ([signo] +1) o anuló (−1) [evento]. Devuelve su id, para [quitar] si falla. */
    fun registrar(evento: FinancialEvent, signo: Int, ahora: Long = Clock.System.now().toEpochMilliseconds()): Long {
        CambioDelDueno.marcar(ahora)
        val m = MovimientoDelDueno(++siguiente, evento, signo, ahora)
        lista.value = lista.value.filter { ahora - it.en in 0..VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS } + m
        return m.id
    }

    /** Lo que se registró con [id] no pasó (deshacer falló): sale. */
    fun quitar(id: Long) {
        lista.value = lista.value.filterNot { it.id == id }
    }

    /**
     * Los que el héroe todavía tiene que adelantar: los de después de [cargaTerminadaEn] (lo que leyó una
     * carga que terminó después ya los trae) y de hace menos de [VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS].
     */
    fun pendientes(cargaTerminadaEn: Long?, ahora: Long = Clock.System.now().toEpochMilliseconds()): List<MovimientoDelDueno> =
        lista.value.filter { (cargaTerminadaEn == null || it.en > cargaTerminadaEn) && ahora - it.en in 0..VIGENCIA_DEL_CAMBIO_DEL_DUENO_MS }

    fun limpiar() {
        lista.value = emptyList()
    }
}

/**
 * Cuándo terminó la última carga del Inicio (epoch ms), o `null` si ninguna en esta sesión. La provee la
 * pantalla del Inicio; el héroe la mira para saber si lo que lee ya trae lo que hizo el dueño.
 */
val LocalCargaDelInicioTerminadaEn = staticCompositionLocalOf<Long?> { null }

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
    val periodo = data.periodoActual ?: return null
    val inicio = inicioDelPeriodo(periodo, data.ajustesDePeriodo)
    val fin = inicioDelPeriodo(periodoSiguiente(periodo), data.ajustesDePeriodo)
    val dia = epochMillisToAppDate(evento.timestamp)
    if (dia < inicio || dia >= fin || dia > hoy) return null
    // Una categoría de un pago del período puede ser ese fijo (ya restado): no se sabe sin el server.
    val pagos = data.upcoming ?: return null
    val categoria = evento.category.trim()
    if (pagos.any { it.rule.type == TransactionType.EXPENSE && it.rule.category.trim().equals(categoria, ignoreCase = true) }) return null
    val gasto = data.gastoVariablePorDia ?: return null
    val clave = dia.toString()
    val contado = montoContado(evento) * signo
    val nuevasCuentas = when (cuenta.type) {
        AccountType.SAVINGS, AccountType.CHECKING, AccountType.CASH ->
            cuentas.map { if (it.id == cuenta.id) it.conSaldoMovido(-signo * evento.amount) else it }
        // La tarjeta no es Tu plata: su deuda crece, pero el héroe no la muestra.
        AccountType.CREDIT_CARD -> cuentas
        else -> return null
    }
    return data.copy(
        accounts = nuevasCuentas,
        gastoVariablePorDia = gasto + (clave to ((gasto[clave] ?: 0L) + contado)),
    )
}

/** La cuenta con [delta] pesos más en su saldo (y en su total estimado, si lo trae). */
private fun Account.conSaldoMovido(delta: Long): Account = copy(
    balance = balance + delta,
    balancesByCurrency = if ("COP" in balancesByCurrency) balancesByCurrency + ("COP" to (balancesByCurrency.getValue("COP") + delta)) else balancesByCurrency,
    estimatedTotalCop = estimatedTotalCop?.plus(delta),
)
