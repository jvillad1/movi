package com.jvillada.movi.ui.transactions

import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.conElPerfil
import com.jvillada.movi.ui.dashboard.conResumenDelInicio
import com.jvillada.movi.ui.dashboard.disponibleDelInicio
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

/**
 * # «Día a día»: lo que gastaste ese día contra lo que podías gastar
 *
 * El dueño: *«En Movimientos, Flujo del día, me parecería genial que de acuerdo al disponible
 * mensual por día vayas además de anotando el valor del flujo del día contando cuánto podías gastar
 * por día, para ir viendo si te pasaste o no»*.
 *
 * Se compara el **gasto variable del día** (lo que sale del bolsillo sin contar los pagos fijos del
 * checklist, las tarjetas ni los traspasos) con la **meta diaria** de la tarjeta «Disponible» de
 * Plan: la misma cifra, fija para todo el período. No el «Flujo del día», que incluye sueldos y
 * pagos fijos y no sirve para saber si te pasaste.
 *
 * Todo puro y aparte de la pantalla, para que las pruebas lean las frases exactas.
 */
data class LineaDelDiaADia(val gastado: Long, val meta: Long) {

    /** Gastó más de lo que podía. Gastar justo la meta no es pasarse. */
    val pasada: Boolean get() = gastado > meta

    /** «Día a día: $6.700 de $41.924». */
    val base: String get() = "Día a día: ${formatCOP(gastado)} de ${formatCOP(meta)}"

    /** «te pasaste $2.076», o `null` si el día quedó dentro de la meta. */
    val aviso: String? get() = if (pasada) "te pasaste ${formatCOP(gastado - meta)}" else null

    /** La línea entera, tal como la lee TalkBack. */
    val texto: String get() = aviso?.let { "$base · $it" } ?: base
}

/**
 * La línea de un día, o `null` si no hay nada honesto que decir: sin meta (no hay disponible que
 * dividir) no se dibuja ni «$0» ni relleno.
 *
 * @param gastoDelDia el gasto variable de ese día. Un reembolso que lo dejaría negativo cuenta 0.
 */
fun lineaDelDiaADia(gastoDelDia: Long, metaPorDia: Long?): LineaDelDiaADia? {
    if (metaPorDia == null || metaPorDia <= 0L) return null
    return LineaDelDiaADia(gastado = gastoDelDia.coerceAtLeast(0L), meta = metaPorDia)
}

/**
 * Lee lo que la tarjeta «Disponible» necesita, puesto en un [DashboardData] con las MISMAS
 * traducciones del Inicio y de Plan ([conResumenDelInicio], [conElPerfil]): una sola forma de armar
 * la cuenta, y la meta de acá no puede diferir de la de allá. Los vencimientos y las ocurrencias
 * son los mismos dos pedidos del tablero de Plan.
 *
 * Es todo o nada: si una de las cuatro lecturas cae, la cuenta no se afirma y la lectura falla —el
 * «Día a día» simplemente no se dibuja—. El perfil ya lo leyó la pantalla.
 */
internal suspend fun leerDatosDelDiaADia(perfil: UserProfile, ahora: Long): DashboardData = coroutineScope {
    val resumen = async { Repositories.wallets.getFinanceSummary(Scope.SELF) }
    val tablero = async { Repositories.wallets.getDashboardSummary(Scope.SELF) }
    val vencimientos = async { Repositories.wallets.getUpcomingPayments() }
    val ocurrencias = async { Repositories.wallets.getOccurrenceStates() }
    DashboardData(
        summary = resumen.await(),
        upcoming = vencimientos.await(),
        ocurrencias = ocurrencias.await(),
    ).conResumenDelInicio(tablero.await()).conElPerfil(perfil, ahora)
}

/**
 * La meta diaria del período en curso y el gasto de cada día, lo único que el encabezado de un día
 * necesita para decir su [LineaDelDiaADia].
 *
 * @property inicio primer día del período en curso.
 * @property hoy el último día que tiene línea: los días futuros no tienen qué comparar.
 */
class DiaADia internal constructor(
    private val meta: Long,
    private val inicio: LocalDate,
    private val hoy: LocalDate,
    private val gastoPorDia: Map<String, Long>,
) {
    /** ¿Este día (ISO) es de los que llevan línea? Del período en curso, sin pasarse de hoy. */
    fun aplicaA(iso: String): Boolean {
        val dia = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return false
        return dia in inicio..hoy
    }

    /** La línea de [iso], o `null` si ese día no la lleva. */
    fun linea(iso: String): LineaDelDiaADia? =
        if (!aplicaA(iso)) null else lineaDelDiaADia(gastoPorDia[iso] ?: 0L, meta)
}

/**
 * El [DiaADia] de [data], o `null` si no hay meta que decir: falta una lectura, no hay ingresos ni
 * plata, los fijos se llevan todo, o [hoy] cae fuera del período (ver [disponibleDelInicio]).
 */
internal fun diaADiaDe(
    data: DashboardData,
    hoy: LocalDate = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()),
): DiaADia? {
    val disponible = disponibleDelInicio(data, hoy) ?: return null
    val meta = disponible.metaPorDia.takeIf { it > 0L } ?: return null
    val periodo = data.periodoActual ?: return null
    return DiaADia(
        meta = meta,
        inicio = inicioDelPeriodo(periodo, data.ajustesDePeriodo),
        hoy = hoy,
        gastoPorDia = data.gastoVariablePorDia.orEmpty(),
    )
}
