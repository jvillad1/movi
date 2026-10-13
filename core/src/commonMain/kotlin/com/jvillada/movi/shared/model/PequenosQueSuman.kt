package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # «Pequeños que suman»: los gastos hormiga de un período
 *
 * Un tinto de $6.000 no se ve en «Los más grandes» ni mueve una barra de «En qué se fue», pero
 * veintisiete en un período son $186.000. Esta lista junta **los gastos chicos que se repiten**, con
 * cuántas veces y cuánto suman, para que se vean en el detalle del período.
 *
 * ## Cómo se agrupan
 *
 * 1. **Por comercio primero** ([claveDeComercio], la misma clave de las suscripciones y de los avisos
 *    de lo que se sale de lo normal): «27 compras en Juan Valdez». Es lo más útil de leer, porque
 *    nombra exactamente dónde se va.
 * 2. **Lo que no armó un grupo por comercio, por categoría**: tres panaderías distintas no suman como
 *    comercio, pero «12 gastos chicos de Comida» sí dice algo. Un gasto entra en un solo grupo: el de
 *    su comercio si ese grupo existe, si no el de su categoría — nunca se cuenta dos veces.
 *
 * ## Los umbrales, elegidos y no medidos
 *
 * Igual que los de «lo que se sale de lo normal» y el 20 % del semáforo de presupuestos, son una
 * elección para que la lista diga algo, no una medición:
 *
 * - **Chico** = hasta [GASTO_CHICO_HASTA] ($50.000): un almuerzo, un taxi, un tinto, una tienda.
 * - **Se repite** = al menos [VECES_PARA_SUMAR] veces (3) en el período: dos veces es casualidad.
 * - **Suma algo que importe** = al menos [TOTAL_QUE_IMPORTA] ($100.000) en el grupo. Por debajo, el
 *   grupo no aparece; sin ningún grupo, la sección no se pinta.
 *
 * Fuera, siempre: lo que no es consumo (la cuota de un crédito, un desembolso: [CUOTA_CATEGORY],
 * [DESEMBOLSO_CATEGORY]), lo que no está en pesos (sumar dólares con pesos no da ninguna cifra) y lo
 * que no es un gasto.
 *
 * Puro: recibe los gastos del período ya filtrados con la regla de «Gastos» (vivos, flujo de caja,
 * fuera de «Por confirmar»), los mismos que suman «Salió».
 */

/** Hasta este monto, inclusive, un gasto es chico. */
const val GASTO_CHICO_HASTA: Long = 50_000L

/** Cuántos gastos chicos hacen falta para que un grupo sume. */
const val VECES_PARA_SUMAR: Int = 3

/** Desde cuánto un grupo de gastos chicos aparece. */
const val TOTAL_QUE_IMPORTA: Long = 100_000L

/** Cuántos grupos se muestran como mucho: los que más suman. */
const val MAX_PEQUENOS_QUE_SUMAN: Int = 5

/**
 * Un grupo de gastos chicos.
 *
 * @property nombre el comercio («Juan Valdez») o la categoría («Comida»).
 * @property porComercio `true` si se agrupó por comercio, `false` si por categoría.
 * @property veces cuántos gastos.
 * @property total cuánto suman, en pesos.
 */
@Serializable
data class PequenosQueSuman(
    val nombre: String,
    val porComercio: Boolean,
    val veces: Int,
    val total: Long,
)

private val QUE_NO_ES_CONSUMO = setOf(CUOTA_CATEGORY, DESEMBOLSO_CATEGORY)

/**
 * Los grupos de gastos chicos de [gastos], del que más suma al que menos, como mucho
 * [MAX_PEQUENOS_QUE_SUMAN]. Ver el KDoc del archivo para cómo se agrupa y los umbrales.
 */
fun pequenosQueSuman(gastos: List<FinancialEvent>): List<PequenosQueSuman> {
    val chicos = gastos.filter {
        it.type == TransactionType.EXPENSE &&
            it.currency == "COP" &&
            it.amount in 1..GASTO_CHICO_HASTA &&
            it.category !in QUE_NO_ES_CONSUMO
    }
    if (chicos.size < VECES_PARA_SUMAR) return emptyList()

    val porComercio = chicos.groupBy { claveDeComercio(it) }
    val gruposDeComercio = porComercio
        .filterKeys { it != null }
        .filterValues { it.size >= VECES_PARA_SUMAR }
    val enUnComercio = gruposDeComercio.values.flatten().map { it.id }.toSet()

    val deComercio = gruposDeComercio.values.map { grupo ->
        PequenosQueSuman(nombreDelComercio(grupo), porComercio = true, veces = grupo.size, total = grupo.sumOf { it.amount })
    }
    val deCategoria = chicos
        .filterNot { it.id in enUnComercio }
        .filter { it.category.isNotBlank() }
        .groupBy { it.category }
        .filterValues { it.size >= VECES_PARA_SUMAR }
        .map { (categoria, grupo) ->
            PequenosQueSuman(categoria, porComercio = false, veces = grupo.size, total = grupo.sumOf { it.amount })
        }

    return (deComercio + deCategoria)
        .filter { it.total >= TOTAL_QUE_IMPORTA }
        // A igual total, por nombre: que no cambien de lugar entre lecturas sin que nada cambie.
        .sortedWith(compareByDescending<PequenosQueSuman> { it.total }.thenBy { it.nombre.lowercase() })
        .take(MAX_PEQUENOS_QUE_SUMAN)
}

/**
 * Cómo se llama el comercio de un grupo: el nombre presentable si es un servicio conocido
 * («Netflix»), si no la descripción que más se repite, tal como la escribió el banco o el dueño. A
 * igual cantidad, la primera en orden alfabético, para que no cambie entre lecturas.
 */
private fun nombreDelComercio(grupo: List<FinancialEvent>): String {
    val textos = grupo.map { (it.merchant?.takeIf { m -> m.isNotBlank() } ?: it.description).trim() }
    textos.firstNotNullOfOrNull { normalizeMerchant(it)?.takeIf { m -> m.known }?.displayName }?.let { return it }
    return textos.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
        .first().key
}
