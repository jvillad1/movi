package com.jvillada.movi.shared.model

/**
 * # Cuadrar saldos: cuándo una cuenta lleva demasiado sin mirarse
 *
 * Las reglas nacieron en `:shared` (`ui/cuadre/CuadreDeSaldosLogic.kt`, que las sigue nombrando igual
 * y explica el porqué de cada una) y se mudaron acá sin reescribirse cuando Movi AI empezó a contestar
 * «¿Qué me recomiendas revisar primero?» sin el modelo: el chat y la fila de «Para revisar» de Hoy
 * tienen que nombrar las MISMAS cuentas, así que las eligen con la misma función.
 */

/** Un día en milisegundos. */
private const val UN_DIA_MS = 24L * 60L * 60L * 1000L

/**
 * **Cuántos días puede pasar una cuenta sin cuadrarse antes de que Movi lo mencione**: un período
 * entero más medio de gracia. Ver el KDoc homónimo en `:shared`.
 */
const val DIAS_PARA_VOLVER_A_CUADRAR = 45

/** **¿Esta cuenta se puede cuadrar contra el banco?** Solo Dinero e Inversión, y nunca un bien. */
fun sePuedeCuadrar(cuenta: Account): Boolean = cuenta.type.group != AccountGroup.DEUDA && !cuenta.esBien

/**
 * **Hace cuántos días que esta cuenta no se cuadra**: desde el último ajuste o, si nunca hubo, desde
 * su movimiento más viejo. `null` sin ningún movimiento: no hay nada que cuadrar ahí.
 */
fun diasSinCuadrar(cuenta: Account, ahora: Long): Long? {
    val desde = cuenta.lastAdjustmentAt ?: cuenta.firstEventAt ?: return null
    return ((ahora - desde) / UN_DIA_MS).coerceAtLeast(0L)
}

/** Ver [DIAS_PARA_VOLVER_A_CUADRAR]. Una cuenta que no se puede cuadrar nunca está atrasada. */
fun estaSinCuadrar(cuenta: Account, ahora: Long): Boolean =
    sePuedeCuadrar(cuenta) && (diasSinCuadrar(cuenta, ahora) ?: 0L) >= DIAS_PARA_VOLVER_A_CUADRAR

/** Las cuentas que el aviso menciona. Vacío = no se dice nada. */
fun cuentasSinCuadrar(cuentas: List<Account>, ahora: Long): List<Account> =
    cuentas.filter { estaSinCuadrar(it, ahora) }

/** **El aviso, en una línea**, o `null` cuando no hay nada que decir. Sin cifras: no se saben. */
fun textoDelAvisoDeCuadre(atrasadas: List<Account>): String? = when {
    atrasadas.isEmpty() -> null
    atrasadas.size == 1 -> "Hace más de un mes que no cuadras ${atrasadas.first().name}"
    else -> "${atrasadas.size} cuentas llevan más de un mes sin cuadrar"
}

/**
 * La fila de «Para revisar» cuando hay propuestas de cuadre por aviso, o `null` sin ninguna. Con una
 * sola se nombra la cuenta, como el aviso de cuadre de siempre ([textoDelAvisoDeCuadre]).
 */
fun textoDeLasPropuestasPorAviso(propuestas: List<PropuestaDeCuadrePorAviso>): String? = when {
    propuestas.isEmpty() -> null
    propuestas.size == 1 -> "El saldo de ${propuestas.first().cuentaNombre} no coincide con el del banco"
    else -> "${propuestas.size} cuentas no coinciden con el saldo del banco"
}
