package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * **Los festivos de Colombia**, calculados y no escritos en una lista: el banco no paga en festivo,
 * y el período del dueño arranca el día que le pagan (ver [corteCorrido]). El dueño (25-oct-2026):
 * un festivo corre el pago igual que un fin de semana — el 25 de diciembre se paga el 24.
 *
 * Son los de la Ley 51 de 1983 («Ley Emiliani»):
 *  - fijos: 1 de enero, 1 de mayo, 20 de julio, 7 de agosto, 8 y 25 de diciembre;
 *  - los que se pasan al lunes siguiente si no caen lunes: 6 de enero, 19 de marzo, 29 de junio,
 *    15 de agosto, 12 de octubre, 1 y 11 de noviembre;
 *  - los de Semana Santa: jueves y viernes santos, y —pasados a lunes— Ascensión (pascua + 43),
 *    Corpus Christi (pascua + 64) y Sagrado Corazón (pascua + 71).
 */
fun esFestivoEnColombia(fecha: LocalDate): Boolean = fecha in festivosDe(fecha.year)

/** Un día en que el banco paga: ni sábado, ni domingo, ni festivo. */
fun esDiaHabilEnColombia(fecha: LocalDate): Boolean =
    fecha.dayOfWeek != DayOfWeek.SATURDAY && fecha.dayOfWeek != DayOfWeek.SUNDAY && !esFestivoEnColombia(fecha)

/**
 * Los festivos de [year]. Se calculan cada vez, sin caché: son 18 fechas y una caché compartida
 * entre los hilos del server sería un mapa mutable más que cuidar.
 */
fun festivosDe(year: Int): Set<LocalDate> {
    val pascua = domingoDePascua(year)
    fun dia(mes: Int, d: Int) = LocalDate(year, mes, d)
    fun desdePascua(dias: Int) = pascua.plus(dias, DateTimeUnit.DAY)
    return setOf(
        dia(1, 1), dia(5, 1), dia(7, 20), dia(8, 7), dia(12, 8), dia(12, 25),
        alLunes(dia(1, 6)), alLunes(dia(3, 19)), alLunes(dia(6, 29)), alLunes(dia(8, 15)),
        alLunes(dia(10, 12)), alLunes(dia(11, 1)), alLunes(dia(11, 11)),
        desdePascua(-3), desdePascua(-2), desdePascua(43), desdePascua(64), desdePascua(71),
    )
}

/** El mismo día si es lunes; si no, el lunes siguiente. */
private fun alLunes(fecha: LocalDate): LocalDate {
    val faltan = (DayOfWeek.MONDAY.ordinal - fecha.dayOfWeek.ordinal + 7) % 7
    return fecha.plus(faltan, DateTimeUnit.DAY)
}

/** El domingo de Pascua del calendario gregoriano (algoritmo de Meeus/Jones/Butcher). */
internal fun domingoDePascua(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val mes = (h + l - 7 * m + 114) / 31
    val dia = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate(year, mes, dia)
}

/** «sábado», «domingo» o «festivo»: por qué ese día no se paga. `null` si es día hábil. */
fun porQueNoEsHabil(fecha: LocalDate): String? = when {
    esFestivoEnColombia(fecha) -> "festivo"
    fecha.dayOfWeek == DayOfWeek.SATURDAY -> "sábado"
    fecha.dayOfWeek == DayOfWeek.SUNDAY -> "domingo"
    else -> null
}
