package com.jvillada.movi.shared.model

/*
 * # «Por confirmar $103.600»: cuánto dicen los avisos que esperan (vuelta 44)
 *
 * El dueño veía «Por confirmar · 4 avisos» en el Inicio y no sabía cuánto iba a bajar «Te quedan»
 * hasta confirmarlos. Esto suma **lo que dicen** los avisos pendientes, uno por pago: no es una
 * regla de plata nueva —nada de esto cuenta en ningún total hasta que el dueño confirma—, es la suma
 * de los montos que el banco escribió. La usan el server (`DashboardSummary.porConfirmarSuman`, para
 * la franja del Inicio) y la app (la tarjeta de avisos de Actividad), así que las dos dicen lo mismo.
 */

/** Lo que dice un aviso por confirmar: su monto, sin signo, y su moneda. */
data class MontoDelAviso(val monto: Long, val moneda: String)

/**
 * **La suma de lo que dicen los avisos por confirmar**, en pesos, o `null` si no se puede decir
 * entera: sin avisos, si alguno no trae un monto legible (`null` en la lista, o cero) o si alguno no
 * es en pesos. **Nunca una suma a medias**: «Por confirmar $80.000» cuando uno de los tres no se leyó
 * sería una cifra que no es; mejor solo el conteo.
 */
fun sumaDeLoPorConfirmar(avisos: List<MontoDelAviso?>): Long? {
    if (avisos.isEmpty()) return null
    if (avisos.any { it == null || it.monto <= 0L || it.moneda != "COP" }) return null
    return avisos.sumOf { it!!.monto }
}
