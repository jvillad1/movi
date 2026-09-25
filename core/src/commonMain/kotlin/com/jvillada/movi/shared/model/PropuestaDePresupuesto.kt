package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **Un presupuesto que Movi propone** a quien todavía no tiene ninguno, armado con lo que ya gastó.
 *
 * Presupuestos vacío pedía inventarse una cifra desde cero; con esto el vacío ofrece las categorías
 * donde de verdad se fue la plata el período pasado, cada una con un tope que ya la cubre. Lo
 * calcula el server (`GET /api/budgets/propuestas`) porque es el único que ve todos los movimientos
 * —todos los aparatos, SMS, importaciones— con los anulados fuera.
 *
 * [desde] y [hasta] son los días (ISO `yyyy-MM-dd`, los dos incluidos) del período del que salió el
 * gasto, para que la pantalla pueda decir de dónde sale la cifra en vez de pedir que se le crea.
 */
@Serializable
data class PropuestaDePresupuesto(
    val category: String,
    val amount: Double,
    val gastado: Double,
    val desde: String,
    val hasta: String,
)

/** Cuántas categorías se proponen como mucho: más que eso ya no es una sugerencia, es una lista. */
const val MAXIMO_DE_PROPUESTAS_DE_PRESUPUESTO = 4

/**
 * **El tope que se propone para un gasto**: el gasto redondeado hacia ARRIBA, a múltiplos de
 * $10.000 bajo el millón y de $50.000 desde el millón.
 *
 * Hacia arriba porque un tope por debajo de lo que ya se gastó nace sobrepasado: el primer día del
 * período siguiente, repitiendo lo mismo, el presupuesto ya estaría en rojo. Y redondo porque una
 * cifra como $99.001 no es un tope que alguien se ponga — es una cuenta.
 */
fun topeSugeridoPara(gasto: Long): Long {
    val paso = if (gasto < 1_000_000L) 10_000L else 50_000L
    return ((gasto + paso - 1) / paso) * paso
}

/**
 * **Las propuestas, a partir del gasto por categoría de un período** que ya viene filtrado por la
 * regla de «Gastos» (flujo de caja, sin anulados, sin «Por confirmar», solo pesos).
 *
 * Además de eso quedan fuera:
 * - la cuota de un crédito ([CUOTA_CATEGORY]): cuenta como gasto del mes, pero no es algo a lo que
 *   uno le ponga un tope — la cuota es la que es;
 * - las categorías reservadas ([isReservedCategory]), que nadie puede escribir a mano y por lo
 *   tanto tampoco presupuestar (el POST de crear las aceptaría y quedaría un presupuesto imposible);
 * - las que ya tienen presupuesto, comparadas sin mayúsculas: proponer «comida» a quien ya tiene
 *   «Comida» terminaría en el 409 del server;
 * - los nombres en blanco, que el POST de crear rechaza.
 *
 * Empates por gasto se ordenan por nombre, para que la misma base proponga siempre lo mismo.
 */
fun propuestasDePresupuesto(
    gastoPorCategoria: Map<String, Long>,
    conPresupuesto: Set<String>,
    desde: String,
    hasta: String,
): List<PropuestaDePresupuesto> {
    val yaTienen = conPresupuesto.map { it.trim().lowercase() }.toSet()
    return gastoPorCategoria
        .filter { (categoria, gasto) ->
            gasto > 0L &&
                categoria.isNotBlank() &&
                categoria != CUOTA_CATEGORY &&
                !isReservedCategory(categoria) &&
                categoria.trim().lowercase() !in yaTienen
        }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        .take(MAXIMO_DE_PROPUESTAS_DE_PRESUPUESTO)
        .map { (categoria, gasto) ->
            PropuestaDePresupuesto(
                category = categoria,
                amount = topeSugeridoPara(gasto).toDouble(),
                gastado = gasto.toDouble(),
                desde = desde,
                hasta = hasta,
            )
        }
}
