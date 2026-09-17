package com.jvillada.movi.ui.goals

import com.jvillada.movi.shared.model.Goal

/**
 * Lo que dice el encabezado de Metas, calculado aparte de la pantalla para poder probarlo.
 *
 * El `saved` de cada meta **no es plata apartada para ella**: es el saldo COMPLETO de la cuenta
 * que eligió, derivado en cada lectura por el server (ver `GoalRoutes.kt`). Y nada impide que
 * dos metas apunten a la misma cuenta. Sumar los `saved` a ciegas contaba esa plata una vez por
 * meta: con $10.000.000 en «Ahorros» y dos metas encima, el encabezado decía «Total ahorrado
 * $20.000.000». Por eso acá cada CUENTA entra una sola vez.
 */
data class ResumenDeMetas(
    /** Plata real detrás de las metas: cada cuenta contada una sola vez. */
    val ahorrado: Long,
    /** Lo que quiere juntar: acá sí se suman todas las metas, una por una. */
    val objetivo: Long,
    /** `ahorrado / objetivo`, recortado a 0..1. Cero si no hay objetivo. */
    val porcentaje: Float,
    val cantidadDeMetas: Int,
    /**
     * Cuentas con más de una meta encima. Ese saldo le alcanza a una a la vez, así que ninguna
     * de esas metas puede declararse «completada» sola: la plata ya está prometida al lado.
     */
    val cuentasCompartidas: Set<String>,
) {
    /** «1 meta» / «3 metas» — el encabezado decía «1 metas». */
    val rotuloDeCantidad: String
        get() = if (cantidadDeMetas == 1) "1 meta" else "$cantidadDeMetas metas"

    /** Cierto si alguna meta comparte la cuenta con otra. */
    val hayCuentasCompartidas: Boolean get() = cuentasCompartidas.isNotEmpty()

    /** Cierto si esta meta comparte su cuenta con otra. */
    fun comparteCuenta(meta: Goal): Boolean = meta.accountId in cuentasCompartidas
}

fun resumenDeMetas(metas: List<Goal>): ResumenDeMetas {
    // `distinctBy` se queda con la primera meta de cada cuenta; el `saved` de las otras es el
    // mismo número (el saldo de esa cuenta), así que cuál se elija da igual.
    val ahorrado = metas.distinctBy { it.accountId }.sumOf { it.saved }
    val objetivo = metas.sumOf { it.target }
    return ResumenDeMetas(
        ahorrado = ahorrado,
        objetivo = objetivo,
        porcentaje = if (objetivo > 0) (ahorrado.toFloat() / objetivo.toFloat()).coerceIn(0f, 1f) else 0f,
        cantidadDeMetas = metas.size,
        cuentasCompartidas = metas.groupingBy { it.accountId }.eachCount()
            .filterValues { it > 1 }
            .keys,
    )
}
