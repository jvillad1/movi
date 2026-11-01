package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * # «Lo que Movi sabe de ti», de un vistazo (vuelta 95)
 *
 * Cuatro conteos que dicen cuánto sabe Movi del dueño, para que la confianza no dependa de adivinar:
 * cuántos movimientos tiene y desde cuándo, cuántas cuentas, cuántas reglas de categoría aprendió de
 * cómo las anotas ([MemoriaDeCategorias.cuantasHuellas]) y cuántos recuerdos le pediste guardar. **No
 * hay datos nuevos**: son las mismas tablas de siempre contadas por `GET /api/asistente/resumen` (tres son agregados de la base; las reglas aprendidas salen de
 * la memoria de categorías, que mira hasta 2.000 movimientos).
 *
 * - [movimientos]: los no anulados, sin el «Saldo inicial» (no es algo que el dueño anotó); un traspaso, que
 *   son dos patas, cuenta como uno.
 * - [desde]: la fecha ISO (día de la app) del primero; `null` sin movimientos.
 */
@Serializable
data class ResumenDeLoQueMoviSabe(
    val movimientos: Int = 0,
    val desde: String? = null,
    val cuentas: Int = 0,
    val reglasAprendidas: Int = 0,
    val recuerdos: Int = 0,
)

private val MESES_DEL_RESUMEN = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** «12 de agosto de 2026», de una fecha ISO; `null` si no se entiende. */
fun fechaDelResumen(iso: String?): String? {
    val fecha = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return "${fecha.dayOfMonth} de ${MESES_DEL_RESUMEN[fecha.monthNumber - 1]} de ${fecha.year}"
}

private fun cuantos(n: Int, uno: String, varios: String): String = if (n == 1) "1 $uno" else "$n $varios"

/**
 * **La frase del resumen**: «Movi tiene 133 movimientos desde el 12 de agosto de 2026, 3 cuentas, 24
 * reglas de categoría aprendidas y 2 recuerdos.» Sin nada todavía: «Movi todavía no sabe nada de ti.»
 */
fun fraseDeLoQueMoviSabe(r: ResumenDeLoQueMoviSabe): String {
    if (r.movimientos == 0 && r.cuentas == 0 && r.reglasAprendidas == 0 && r.recuerdos == 0) {
        return "Movi todavía no sabe nada de ti."
    }
    val fecha = fechaDelResumen(r.desde)?.takeIf { r.movimientos > 0 }
    val movimientos = cuantos(r.movimientos, "movimiento", "movimientos") + (fecha?.let { " desde el $it" } ?: "")
    val partes = listOf(
        movimientos,
        cuantos(r.cuentas, "cuenta", "cuentas"),
        if (r.reglasAprendidas == 1) "1 regla de categoría aprendida" else "${r.reglasAprendidas} reglas de categoría aprendidas",
        cuantos(r.recuerdos, "recuerdo", "recuerdos"),
    )
    return "Movi tiene ${partes.dropLast(1).joinToString(", ")} y ${partes.last()}."
}
