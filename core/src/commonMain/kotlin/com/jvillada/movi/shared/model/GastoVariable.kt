package com.jvillada.movi.shared.model

/**
 * # El gasto **variable**: lo que sale del bolsillo y no es un pago fijo del período
 *
 * El dueño pidió ver, en el Inicio, cuánto le queda para gastar en el período, en la semana y hoy
 * («Disponible por período · por semana · por día»), y eligió la definición «ingresos menos
 * fijos». Contra ese disponible se mide lo que se va gastando — pero no TODO lo que se gasta: el
 * arriendo o la cuota del carro ya están restados del disponible como fijos, y contarlos otra vez
 * como gasto los descontaría dos veces.
 *
 * Así que el gasto variable es **lo que cuenta como «Gastos» en el Inicio**
 * ([cuentaEnGastosEIngresos], solo pesos, solo egresos) **menos la parte de cada movimiento que
 * paga un ítem del checklist del período**:
 *
 * - **la categoría [CUOTA_CATEGORY]** sale entera: es la que escribe el pago de una cuota (ver
 *   `pagoDeCuotaLegs`), y la misma con la que el server deduce que una cuota quedó pagada
 *   (`PagosDeDeuda.kt`). El pago de tarjeta no hace falta nombrarlo: ya no es flujo de caja.
 * - **lo que paga un recurrente del checklist** sale en la parte que diga [parteFija], que arma
 *   el server (`PagosDelChecklist.kt`) con el mismo emparejador que la pantalla usa para el «¿Es
 *   este?» — pero solo con lo que **dice el nombre** del recurrente (o lo que Movi emparejó solo):
 *   un gasto que comparte apenas la categoría no es evidencia de que sea su pago, y si saliera
 *   del variable, «Mercado» ($2.000.000, Comida) se comería todo el gasto de Comida y dejaría el
 *   disponible en $0 mientras el fijo sigue restando su monto entero. Ese gasto cuenta como
 *   variable hasta que el dueño confirme que fue el pago. Es una parte y no un sí/no porque la regla tiene un monto y el movimiento otro: un
 *   colegio de $4.000.000 pagado con $3.000.000 + $1.000.000 saca los dos; un gimnasio de
 *   $180.000 pagado con un movimiento de $200.000 saca $180.000 y deja $20.000 como variable. Así
 *   fijos + variable suman siempre lo que de verdad salió, sin contar nada dos veces ni perderlo.
 *   Lo que Movi emparejó solo sale por el monto entero del movimiento: esa fila resta como fijo
 *   lo que de verdad se pagó (`montoPagado`), no el monto de la regla.
 *
 * **Fijos contra variable, la regla de oro:** los fijos del período son el monto de cada ítem del
 * checklist, pagado o pendiente (ver `fijosDelPeriodo` en la UI). Todo lo que acá se saca del
 * variable tiene que estar sumado allá; lo contrario haría ver el disponible mejor de lo que es.
 */

/**
 * ¿[evento] suma al gasto variable, en principio? Egreso en pesos que cuenta en «Gastos» (sin
 * traspasos, sin «Por confirmar»; los anulados ya no llegan) y que no es la cuota de un crédito.
 * Cuánto suma lo decide [parteVariable].
 */
fun cuentaComoGastoVariable(evento: FinancialEvent): Boolean =
    evento.type == TransactionType.EXPENSE &&
        evento.currency == "COP" &&
        cuentaEnGastosEIngresos(evento) &&
        evento.category != CUOTA_CATEGORY

/**
 * Cuánto de [evento] es gasto variable: su monto menos la parte que paga un fijo del checklist,
 * nunca menos de cero.
 */
fun parteVariable(evento: FinancialEvent, parteFija: Map<String, Long>): Long =
    if (!cuentaComoGastoVariable(evento)) 0L
    else (montoContado(evento) - (parteFija[evento.id] ?: 0L)).coerceAtLeast(0L)

/**
 * **El gasto variable de cada día**, `"YYYY-MM-DD"` → pesos.
 *
 * Viaja por día y no ya sumado por ventana porque las ventanas (el período, la semana, hoy) las
 * arma el cliente con su propio «hoy», y así el server no tiene que saber qué semana mira nadie.
 * Un día cuyo gasto quedó entero como fijo no aparece.
 *
 * @param eventos los movimientos vivos (no anulados) de la ventana del período, con
 *   `countsAsCashFlow` ya derivado.
 * @param parteFija id de movimiento → la parte de su monto que paga un ítem del checklist.
 * @param diaDe la fecha civil de un instante, en la zona de la app. Entra por parámetro para que
 *   el server use su propia zona configurada y esto no toque ninguna tabla de zonas.
 */
fun gastoVariablePorDia(
    eventos: List<FinancialEvent>,
    parteFija: Map<String, Long>,
    diaDe: (Long) -> String,
): Map<String, Long> =
    eventos
        .map { it to parteVariable(it, parteFija) }
        .filter { (_, monto) -> monto > 0L }
        .groupBy({ diaDe(it.first.timestamp) }, { it.second })
        .mapValues { (_, montos) -> montos.sum() }

// ── Vuelta 108 · el gasto de un día normal ────────────────────────────────────

/**
 * **Desde cuánto un solo gasto es «de una vez» y no del día a día**: $1.000.000, por movimiento y sobre
 * su parte variable (lo que no pagó un fijo del checklist). **Elegido, no medido** (vuelta 108): con los
 * datos del dueño, lo que pasaba de ahí era la obra de la casa (Gardenera, $6,4M + $3,5M + $6,2M + $1M) y
 * un arreglo de tubería de $1,2M; lo que quedaba por debajo (el contador, $450.000) sí es un gasto que
 * vuelve. Un umbral fijo en pesos y no un múltiplo del promedio, porque un promedio inflado por la obra
 * subiría también el umbral. **Y solo si no se repite** ([esUnaCostumbre]): un mercado grande de todos los
 * períodos sigue contando. Quien no se reconozca en el estimado escribe el suyo en Pagos («Gasto de un día
 * normal», `UserProfile.gastoDeUnDiaNormal`), que reemplaza toda esta cuenta.
 */
const val UMBRAL_DE_UN_GASTO_DE_UNA_VEZ: Long = 1_000_000L

/**
 * Desde cuánto un gasto sirve de comparación para decidir si uno grande se repite: el 75 % del umbral, para
 * que el mercado de $1,1M de un período encuentre su par de $950.000 en el otro.
 */
const val PISO_PARA_COMPARAR_UN_GASTO_GRANDE: Long = UMBRAL_DE_UN_GASTO_DE_UNA_VEZ * 3 / 4

/** Cuánto pueden diferir dos montos para ser «parecidos»: un 25 % del más grande. Elegido. */
const val PORCENTAJE_DE_MONTO_PARECIDO: Long = 25L

/**
 * Cuántos **otros** períodos promediados tienen que repetir un gasto grande para que sea una costumbre:
 * dos (o sea, tres períodos en total). Con uno solo no alcanza: la obra de la casa duró dos períodos, con
 * pagos parecidos en los dos ($6,4M y $6,2M), y no es el gasto de un día cualquiera. Elegido.
 */
const val OTROS_PERIODOS_PARA_UNA_COSTUMBRE: Int = 2

/**
 * **Lo que entra a los supuestos del gasto de un día normal** de un período cerrado, y lo que se dejó fuera.
 *
 * Es el gasto variable de siempre ([parteVariable], con la misma [parteFija]), partido en tres:
 *
 * 1. **los gastos grandes** —un movimiento cuya parte variable llega a [UMBRAL_DE_UN_GASTO_DE_UNA_VEZ], con
 *    tarjeta o sin ella—, que van a [GastoVariableDeUnPeriodo.deUnaVez] como candidatos: [gastoDelDiaADia]
 *    devuelve al día a día los que se repiten en los otros períodos ([esUnaCostumbre]);
 * 2. **las compras con tarjeta** ([cuentasDeTarjeta]), aparte en [GastoVariableDeUnPeriodo.conTarjeta]:
 *    entran al supuesto del héroe (lo que se gasta, se pague como se pague) y no al de la caja, que cuenta
 *    efectivo (su pago mínimo ya es una fila de la lista);
 * 3. lo demás, el día a día en efectivo ([GastoVariableDeUnPeriodo.total]).
 *
 * Puro. Lo usan solo los supuestos ([gastoDelDiaADia]): el Disponible, «Te quedan» y «En qué se va» siguen
 * contando todo lo que de verdad se gastó.
 *
 * @param cuentasDeTarjeta los ids de las cuentas `CREDIT_CARD` del dueño.
 */
fun gastoDeUnDiaNormalDelPeriodo(
    periodo: String,
    dias: Int,
    eventos: List<FinancialEvent>,
    parteFija: Map<String, Long>,
    cuentasDeTarjeta: Set<String>,
    diaDe: (Long) -> String,
): GastoVariableDeUnPeriodo {
    var total = 0L
    var conTarjeta = 0L
    val deUnaVez = mutableListOf<GastoDeUnaVez>()
    val paraComparar = mutableListOf<GastoDeUnaVez>()
    eventos.forEach { evento ->
        val parte = parteVariable(evento, parteFija)
        if (parte <= 0L) return@forEach
        val esConTarjeta = evento.accountId in cuentasDeTarjeta
        if (parte >= PISO_PARA_COMPARAR_UN_GASTO_GRANDE) {
            val grande = GastoDeUnaVez(
                nombre = evento.merchant?.takeIf { it.isNotBlank() } ?: evento.description,
                monto = parte,
                fecha = diaDe(evento.timestamp),
                clave = claveDeComercio(evento),
                categoria = evento.category,
                conTarjeta = esConTarjeta,
            )
            paraComparar += grande
            if (parte >= UMBRAL_DE_UN_GASTO_DE_UNA_VEZ) {
                deUnaVez += grande
                return@forEach
            }
        }
        if (esConTarjeta) conTarjeta += parte else total += parte
    }
    return GastoVariableDeUnPeriodo(
        periodo = periodo,
        total = total,
        dias = dias,
        conTarjeta = conTarjeta,
        deUnaVez = deUnaVez.sortedByDescending { it.monto },
        paraComparar = paraComparar,
    )
}

/**
 * **¿Este gasto grande es una costumbre?** Sí si en cada uno de los [otros] períodos que se promedian hay
 * uno parecido —el mismo comercio o la misma categoría, con un monto a no más de
 * [PORCENTAJE_DE_MONTO_PARECIDO] %— y esos otros son al menos [OTROS_PERIODOS_PARA_UNA_COSTUMBRE]. Puro.
 */
fun esUnaCostumbre(gasto: GastoDeUnaVez, otros: List<GastoVariableDeUnPeriodo>): Boolean =
    otros.size >= OTROS_PERIODOS_PARA_UNA_COSTUMBRE &&
        otros.all { otro -> (otro.paraComparar + otro.deUnaVez).any { sonParecidos(gasto, it) } }

private fun sonParecidos(a: GastoDeUnaVez, b: GastoDeUnaVez): Boolean {
    val mismo = (a.clave != null && a.clave == b.clave) || (a.categoria.isNotBlank() && a.categoria == b.categoria)
    val mayor = maxOf(a.monto, b.monto)
    return mismo && mayor > 0L && kotlin.math.abs(a.monto - b.monto) * 100 <= mayor * PORCENTAJE_DE_MONTO_PARECIDO
}
