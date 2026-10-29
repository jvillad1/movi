package com.jvillada.movi.shared.model

/**
 * # Una sola manera de decir «gastado» (vuelta 60)
 *
 * La revisión del 29-oct encontró tres cifras de «gastado» en pantallas vecinas (a 1.280 se ven lado a
 * lado): Presupuestos decía «Gastado en octubre $810.550 de $2.150.000», «Cuánto puedes gastar»
 * «Período $2,1M · gastaste» y «En qué se va» «Llevas gastado $4.888.850». Las tres eran correctas, y
 * las tres se llamaban igual. **No hay reglas de plata nuevas**: el gasto del período es el de
 * siempre ([esGastoDelPeriodo], el mismo `spentByCategory` del server) y el día a día es el gasto
 * variable del Disponible ([gastoVariablePorDia]). Lo que cambia es que cada cifra dice qué parte es:
 *
 * - **Presupuestos**: «En tus N presupuestos: $X de $Y» y «Fuera de presupuesto: $Z»
 *   ([gastoEnPresupuestos]); X + Z es el gasto del período.
 * - **«En qué se va»**: «día a día $X + fijos $Y» ([desgloseDelGastado]); X es lo que la columna
 *   «Período» de «Cuánto puedes gastar» dice, y X + Y es el total de la pantalla.
 */

/**
 * Lo gastado del período repartido entre lo que tiene presupuesto y lo que no.
 *
 * @property presupuestos cuántos presupuestos hay (las categorías distintas).
 * @property enPresupuestos lo gastado en las categorías con presupuesto.
 * @property limite la suma de los topes.
 * @property fueraDePresupuesto lo gastado en las demás categorías.
 */
data class GastoEnPresupuestos(
    val presupuestos: Int,
    val enPresupuestos: Long,
    val limite: Long,
    val fueraDePresupuesto: Long,
) {
    /** El gasto del período entero: el mismo de «En qué se va». */
    val gastadoDelPeriodo: Long get() = enPresupuestos + fueraDePresupuesto
}

/**
 * **Lo gastado en los presupuestos y lo de afuera**, sobre el gasto del período por categoría (el
 * `spentByCategory` del server, o su respaldo local con la misma regla). Una categoría con dos
 * presupuestos (no debería pasar) se cuenta una vez; una categoría con gasto negativo (un reembolso
 * más grande que lo gastado) no resta.
 */
fun gastoEnPresupuestos(presupuestos: List<Budget>, gastoPorCategoria: Map<String, Long>): GastoEnPresupuestos {
    val categorias = presupuestos.map { it.category }.toSet()
    val gasto = gastoPorCategoria.mapValues { (_, v) -> v.coerceAtLeast(0L) }
    return GastoEnPresupuestos(
        presupuestos = categorias.size,
        enPresupuestos = gasto.filterKeys { it in categorias }.values.sum(),
        limite = presupuestos.distinctBy { it.category }.sumOf { it.monthlyLimit },
        fueraDePresupuesto = gasto.filterKeys { it !in categorias }.values.sum(),
    )
}

/**
 * El total de «En qué se va» partido en dos: **el día a día** (lo que mide «Cuánto puedes gastar»)
 * y **los fijos** (lo que pagó facturas, cuotas y recurrentes del período, que el Disponible ya restó
 * aparte). `diaADia + fijos` es siempre el total.
 */
data class DesgloseDelGastado(val diaADia: Long, val fijos: Long) {
    val total: Long get() = diaADia + fijos
}

/**
 * **El desglose del gasto del período**, o `null` si no se puede afirmar.
 *
 * El día a día es el gasto variable del Disponible ([gastoVariablePorDia]: lo que cuenta en «Gastos»
 * menos la parte de cada movimiento que paga un ítem del checklist, y las cuotas), sumado sobre los
 * días de `[desde, hasta]` (ISO). Los fijos son el resto del gasto del período: la parte que ese mismo
 * cálculo sacó como fija. Así es **la misma regla** que separa los fijos en el Disponible, sin
 * contarlos dos veces ni perderlos.
 *
 * `null` sin el gasto variable (un server viejo, o un período que no es el en curso: el server solo lo
 * manda del período en curso) o si el día a día pasa el total: la historia que leyó la pantalla no es
 * la misma que la del server (un teléfono con movimientos sin subir), y un «fijos −$X» sería inventado.
 *
 * @param gastado el total de la pantalla ([AnalisisDelPeriodo.total]).
 */
fun desgloseDelGastado(
    gastado: Long,
    gastoVariablePorDia: Map<String, Long>?,
    desde: String,
    hasta: String,
): DesgloseDelGastado? {
    val porDia = gastoVariablePorDia ?: return null
    val diaADia = porDia.filterKeys { it in desde..hasta }.values.sum()
    if (diaADia < 0L || diaADia > gastado) return null
    return DesgloseDelGastado(diaADia = diaADia, fijos = gastado - diaADia)
}
