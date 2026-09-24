package com.jvillada.movi.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jvillada.movi.data.FormaRecordada
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.intentar
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DashboardDataCache
import com.jvillada.movi.ui.dashboard.InstantaneaDelInicio
import com.jvillada.movi.ui.dashboard.conElPerfil
import com.jvillada.movi.ui.dashboard.conElPeriodoDe
import com.jvillada.movi.ui.dashboard.conResumenDelInicio
import com.jvillada.movi.ui.dashboard.debeRecargarElInicio
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
 *    ([debeRecargarElInicio]: el tick de «se guardó algo», los 30 s)—, se piden el resumen, el
 *    resumen del Inicio (gasto variable y plata) y el perfil (el período), traducidos con las
 *    mismas funciones del Inicio ([conResumenDelInicio], [conElPerfil]).
 * 3. **Los vencimientos y las ocurrencias** —de donde salen los fijos— no se piden acá: son los del
 *    tablero de pagos de la misma pantalla ([EstadoDelTableroDeRecurrentes], montado con
 *    `vencimientosSiempre`), apenas los tiene. Una sola lectura, y la tarjeta y el checklist no
 *    pueden contar distinto el mismo período.
 *
 * **Lo viejo no se hace pasar por actual** (fix round 1): mientras cualquiera de esas lecturas está
 * en vuelo con cifras ya pintadas, [cargando] está prendido y Plan dice «Actualizando…», como el
 * Inicio; si la carga termina y alguna de ESTA vez no contestó —aunque haya cifras de antes—,
 * [fallo] se prende y Plan cambia la tarjeta por «no pudimos calcular» con «Reintentar». Lo que
 * cuenta es lo que llegó en esta carga, no lo que hay en `data` (que arranca con lo del Inicio): la
 * misma distinción que `llegado` en el Inicio.
 *
 * **Nada de esto escribe en [DashboardDataCache]** ni en la instantánea: lo que se lee acá es la
 * mitad de un Inicio, y dejarlo ahí haría que el Inicio pintara como suya una carga que no hizo (el
 * mismo motivo por el que solo el Inicio sella `cargadoEn`). La contracara: volver a Plan con el
 * caché del Inicio viejo vuelve a pedir las tres. Son lecturas livianas.
 */
@Stable
internal class DisponibleDelPlan(
    inicial: DashboardData,
    cargandoAlNacer: Boolean,
    private val tablero: EstadoDelTableroDeRecurrentes,
) {
    /** Lo del Inicio al nacer, pisado por cada lectura propia que contesta bien. */
    internal var propio by mutableStateOf(inicial)

    /**
     * Lo que pinta la tarjeta: [propio] con los vencimientos y las ocurrencias del tablero en cuanto
     * el tablero los tiene; hasta entonces, los que traía lo del Inicio.
     */
    val data: DashboardData by derivedStateOf {
        if (tablero.vencimientosOk && tablero.ocurrenciasOk) {
            propio.copy(upcoming = tablero.upcomingRecurrentes, ocurrencias = tablero.ocurrencias)
        } else {
            propio
        }
    }

    /**
     * Las lecturas propias están en vuelo. Nace con la misma decisión que va a tomar la carga —como
     * `loading` en el Inicio—: sin eso el primer cuadro de un arranque en frío no sabía si esperar
     * o rendirse.
     */
    internal var cargandoPropio by mutableStateOf(cargandoAlNacer)

    /** Alguna de las lecturas propias de la ÚLTIMA carga no contestó. */
    internal var falloPropio by mutableStateOf(false)

    /** Hay algo en vuelo de lo que sale la tarjeta: lo propio o los vencimientos del tablero. */
    val cargando: Boolean get() = cargandoPropio || tablero.leyendoVencimientos

    /** Algo de lo que sale la tarjeta no contestó en su última lectura. */
    val fallo: Boolean get() = falloPropio || tablero.recurrentesNoSePudieronLeer

    /** La primera carga de esta composición no es un reintento: puede usar lo del Inicio si está fresco. */
    internal var yaCargoUnaVez = false
}

/**
 * Ver [DisponibleDelPlan].
 *
 * @param recarga sube con el «Reintentar» de la tarjeta y con cada acción del tablero de pagos
 *   (marcar que algo ocurrió cambia los fijos): cualquier cambio fuerza la lectura, con el Inicio
 *   fresco o no.
 * @param tablero el de la misma pantalla, que trae los vencimientos y las ocurrencias.
 */
@Composable
internal fun rememberDisponibleDelPlan(recarga: Int, tablero: EstadoDelTableroDeRecurrentes): DisponibleDelPlan {
    val refreshTick = LocalRefreshTick.current
    val estado = remember(tablero) {
        val ahora = Clock.System.now().toEpochMilliseconds()
        DisponibleDelPlan(
            inicial = DashboardDataCache.data
                ?: InstantaneaDelInicio.delAparato.datos(SessionManager.userId)?.conElPeriodoDe(ahora)
                ?: DashboardData(),
            cargandoAlNacer = debeRecargar(refreshTick, reintento = false, ahora = ahora),
            tablero = tablero,
        )
    }
    LaunchedEffect(recarga, refreshTick) {
        val reintento = estado.yaCargoUnaVez
        estado.yaCargoUnaVez = true
        if (!debeRecargar(refreshTick, reintento, Clock.System.now().toEpochMilliseconds())) {
            estado.falloPropio = false
            estado.cargandoPropio = false
            return@LaunchedEffect
        }
        estado.cargandoPropio = true
        estado.falloPropio = false
        val usuario = SessionManager.userId
        // Cuántas de las tres contestaron en ESTA carga (ver el KDoc de [DisponibleDelPlan]).
        var llegaron = 0
        coroutineScope {
            launch {
                intentar { Repositories.wallets.getFinanceSummary(Scope.SELF) }.onSuccess { s ->
                    estado.propio = estado.propio.copy(summary = s)
                    llegaron++
                }
            }
            launch {
                intentar { Repositories.wallets.getDashboardSummary(Scope.SELF) }.onSuccess { s ->
                    estado.propio = estado.propio.conResumenDelInicio(s)
                    llegaron++
                }
            }
            launch {
                intentar { Repositories.wallets.getUserProfile() }.onSuccess { perfil ->
                    estado.propio = estado.propio.conElPerfil(perfil, Clock.System.now().toEpochMilliseconds())
                    llegaron++
                    // La línea del rango del encabezado: si va o no, para reservarla (o no) la
                    // próxima vez antes de que el perfil conteste. Solo con un perfil que contestó
                    // bien, y solo si la sesión sigue siendo la misma.
                    val periodo = estado.propio.periodoActual
                    if (periodo != null && SessionManager.userId == usuario) {
                        FormaRecordada.delAparato.recordarLineaDePeriodo(usuario, periodo, estado.propio.ajustesDePeriodo)
                    }
                }
            }
        }
        // Se llega acá solo si la carga no se canceló (`intentar` deja pasar la cancelación).
        estado.falloPropio = llegaron < LECTURAS_PROPIAS
        estado.cargandoPropio = false
    }
    return estado
}

/** Las lecturas de [rememberDisponibleDelPlan]: el resumen, el resumen del Inicio y el perfil. */
private const val LECTURAS_PROPIAS = 3

/** [debeRecargarElInicio] con lo que el Inicio dejó en [DashboardDataCache]. */
private fun debeRecargar(tick: Int, reintento: Boolean, ahora: Long): Boolean = debeRecargarElInicio(
    hayDatos = DashboardDataCache.data != null,
    cargadoEn = DashboardDataCache.cargadoEn,
    tickDeLaCarga = DashboardDataCache.tickDeLaCarga,
    tickActual = tick,
    reintento = reintento,
    ahora = ahora,
)
