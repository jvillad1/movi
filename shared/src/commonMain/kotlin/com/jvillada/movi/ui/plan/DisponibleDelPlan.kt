package com.jvillada.movi.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import com.jvillada.movi.ui.dashboard.conElPerfil
import com.jvillada.movi.ui.dashboard.conElPeriodoDe
import com.jvillada.movi.ui.dashboard.conResumenDelInicio
import com.jvillada.movi.ui.dashboard.debeRecargarElInicio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * # El disponible de la pestaña Plan: lo que ya sabe el Inicio, o lo mínimo para saberlo
 *
 * «¿Cuánto puedo gastar?» es la primera pregunta de Plan, y la tarjeta que la contesta es la del
 * Inicio ([com.jvillada.movi.ui.sdui.TarjetaDelDisponible]) con las mismas cuentas
 * ([com.jvillada.movi.ui.dashboard.disponibleDelInicio]). Lo que cambia es de dónde salen los datos:
 *
 * 1. **Lo que el Inicio ya tiene**: [DashboardDataCache] (volver del Inicio) o, en un arranque en
 *    frío, su instantánea en el aparato ([InstantaneaDelInicio], con el período recalculado). Se
 *    pinta en el primer cuadro.
 * 2. **Si eso no alcanza** —no hay nada, o está viejo con la MISMA vara que usa el Inicio
 *    ([debeRecargarElInicio]: el tick de «se guardó algo», los 30 s)—, se piden solo las cinco
 *    lecturas de las que sale la tarjeta: el resumen, el resumen del Inicio (gasto variable y
 *    plata), los vencimientos, las ocurrencias y el perfil (el período). No las diez del Inicio.
 *    Cada respuesta se traduce con la misma función que usa el Inicio ([conResumenDelInicio],
 *    [conElPerfil]).
 *
 * **Nada de esto escribe en [DashboardDataCache]** ni en la instantánea: lo que se lee acá es la
 * mitad de un Inicio, y dejarlo ahí haría que el Inicio pintara como suya una carga que no hizo (el
 * mismo motivo por el que solo el Inicio sella `cargadoEn`). La contracara: volver a Plan con el
 * caché del Inicio viejo vuelve a pedir las cinco. Son cinco lecturas livianas.
 */
@Stable
internal class DisponibleDelPlan(inicial: DashboardData, cargandoAlNacer: Boolean) {
    /** Lo que se sabe: del Inicio al nacer, y pisado por cada lectura propia que contesta bien. */
    var data by mutableStateOf(inicial)
        internal set

    /**
     * Hay una carga en vuelo. Nace con la misma decisión que va a tomar la carga —como `loading` en
     * el Inicio—: sin eso el primer cuadro de un arranque en frío no sabía si esperar o rendirse.
     */
    var cargando by mutableStateOf(cargandoAlNacer)
        internal set

    /** La primera carga de esta composición no es un reintento: puede usar lo del Inicio si está fresco. */
    internal var yaCargoUnaVez = false
}

/**
 * Ver [DisponibleDelPlan].
 *
 * @param recarga sube con el «Reintentar» de la tarjeta y con cada acción del tablero de pagos
 *   (marcar que algo ocurrió cambia los fijos): cualquier cambio fuerza la lectura, con el Inicio
 *   fresco o no.
 */
@Composable
internal fun rememberDisponibleDelPlan(recarga: Int): DisponibleDelPlan {
    val refreshTick = LocalRefreshTick.current
    val estado = remember {
        val ahora = Clock.System.now().toEpochMilliseconds()
        DisponibleDelPlan(
            inicial = DashboardDataCache.data
                ?: InstantaneaDelInicio.delAparato.datos(SessionManager.userId)?.conElPeriodoDe(ahora)
                ?: DashboardData(),
            cargandoAlNacer = debeRecargar(refreshTick, reintento = false, ahora = ahora),
        )
    }
    LaunchedEffect(recarga, refreshTick) {
        val reintento = estado.yaCargoUnaVez
        estado.yaCargoUnaVez = true
        if (!debeRecargar(refreshTick, reintento, Clock.System.now().toEpochMilliseconds())) {
            estado.cargando = false
            return@LaunchedEffect
        }
        estado.cargando = true
        val usuario = SessionManager.userId
        coroutineScope {
            launch {
                leer { Repositories.wallets.getFinanceSummary(Scope.SELF) }
                    ?.let { s -> estado.data = estado.data.copy(summary = s) }
            }
            launch {
                leer { Repositories.wallets.getDashboardSummary(Scope.SELF) }
                    ?.let { s -> estado.data = estado.data.conResumenDelInicio(s) }
            }
            launch {
                leer { Repositories.wallets.getUpcomingPayments() }
                    ?.let { u -> estado.data = estado.data.copy(upcoming = u) }
            }
            launch {
                leer { Repositories.wallets.getOccurrenceStates() }
                    ?.let { o -> estado.data = estado.data.copy(ocurrencias = o) }
            }
            launch {
                leer { Repositories.wallets.getUserProfile() }
                    ?.let { perfil ->
                        estado.data = estado.data.conElPerfil(perfil, Clock.System.now().toEpochMilliseconds())
                        // La línea del rango del encabezado: si va o no, para reservarla (o no)
                        // la próxima vez antes de que el perfil conteste. Solo con un perfil que
                        // contestó bien, y solo si la sesión sigue siendo la misma.
                        val periodo = estado.data.periodoActual
                        if (periodo != null && SessionManager.userId == usuario) {
                            FormaRecordada.delAparato.recordarLineaDePeriodo(usuario, periodo, estado.data.ajustesDePeriodo)
                        }
                    }
            }
        }
        estado.cargando = false
    }
    return estado
}

/**
 * Una lectura que, si falla, no dice nada: la tarjeta se queda con lo que ya sabía y, si con eso no
 * alcanza, Plan dice que no pudo calcular (ver `alcanzaParaElDisponible`). La cancelación sí sigue
 * de largo — salir de Plan a mitad de la carga no es una lectura fallida.
 */
private inline fun <T> leer(bloque: () -> T): T? =
    try {
        bloque()
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        null
    }

/** [debeRecargarElInicio] con lo que el Inicio dejó en [DashboardDataCache]. */
private fun debeRecargar(tick: Int, reintento: Boolean, ahora: Long): Boolean = debeRecargarElInicio(
    hayDatos = DashboardDataCache.data != null,
    cargadoEn = DashboardDataCache.cargadoEn,
    tickDeLaCarga = DashboardDataCache.tickDeLaCarga,
    tickActual = tick,
    reintento = reintento,
    ahora = ahora,
)
