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

/**
 * Lo que dice un aviso por confirmar: su monto, sin signo, y su moneda. [entra] (vuelta 85): si es
 * plata que llega (un ingreso) y no que sale.
 */
data class MontoDelAviso(val monto: Long, val moneda: String, val entra: Boolean = false)

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

/**
 * **Lo que dicen los avisos por confirmar, separado por signo** (vuelta 85). La revisión 9 vio «$1.632.025
 * en 6 avisos» con dos ingresos (+$480.000 y +$539.625) sumados a los gastos como si fueran lo mismo:
 * esa cifra no decía ni cuánto iba a bajar «Te quedan» ni cuánto iba a entrar.
 *
 * @property salen lo que suman los avisos de plata que sale, en pesos y sin signo.
 * @property avisosQueSalen cuántos son.
 * @property entran lo que suman los de plata que llega.
 * @property avisosQueEntran cuántos son.
 */
data class LoPorConfirmarPorSigno(
    val salen: Long,
    val avisosQueSalen: Int,
    val entran: Long,
    val avisosQueEntran: Int,
)

/**
 * **[sumaDeLoPorConfirmar], partida en lo que sale y lo que entra** (vuelta 85), con la misma regla:
 * entera o nada (`null` sin avisos, o si alguno no trae un monto legible o no es en pesos). La usan el
 * server (`DashboardSummary.porConfirmarSalen`/`porConfirmarEntran`, la franja del Inicio) y la app (la
 * tarjeta de avisos de Actividad), así que las dos dicen lo mismo.
 */
fun loPorConfirmarPorSigno(avisos: List<MontoDelAviso?>): LoPorConfirmarPorSigno? {
    if (sumaDeLoPorConfirmar(avisos) == null) return null
    val (entran, salen) = avisos.filterNotNull().partition { it.entra }
    return LoPorConfirmarPorSigno(
        salen = salen.sumOf { it.monto },
        avisosQueSalen = salen.size,
        entran = entran.sumOf { it.monto },
        avisosQueEntran = entran.size,
    )
}
