package com.jvillada.movi.ui.accounts

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.DeudaDelBien
import com.jvillada.movi.shared.model.claseDeBien
import com.jvillada.movi.shared.model.esBien
import com.jvillada.movi.shared.model.esCuentaDeDeuda
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import kotlinx.datetime.LocalDate

/**
 * # Lo que la pantalla dice de un bien
 *
 * Funciones puras —sin Compose— para que las frases se puedan fijar en una prueba de `commonTest`
 * sin levantar una pantalla. La regla de qué es un bien y cuánto suma vive en `:core` (`Bien`,
 * `patrimonioDe`); acá solo está cómo se nombra.
 */

/**
 * **De cuándo es el valor**, dicho como lo diría el dueño: «avalúo del 28 de agosto».
 *
 * Existe porque un valor sin fecha no se puede juzgar: el avalúo de la casa es del 28 de agosto
 * de 2026, y dentro de tres años esa misma cifra va a seguir en pantalla. Que la línea diga de
 * cuándo es le deja al que lee decidir cuánto confiar en ella, que es lo honesto — Movi no tiene
 * forma de saber cuánto vale hoy una casa.
 *
 * El año solo se dice cuando no es el corriente, igual que `etiquetaDeFecha` en Movimientos:
 * repetir «de 2026» en 2026 es ruido. «Avalúo» y no «valor» porque es la palabra que el dueño usa
 * (y la que trae el papel del banco); para un carro el número suele salir de la tabla de Fasecolda,
 * que en Colombia también se llama avalúo.
 *
 * `null` si no hay fecha, o si la guardada no se puede leer (una fila vieja, un cliente que mandó
 * otra forma): no se inventa una.
 */
fun textoDelAvaluo(valorAl: String?, hoy: LocalDate): String? {
    val fecha = valorAl?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (fecha == hoy) return "avalúo de hoy"
    val mes = MESES_DEL_ANIO[fecha.monthNumber - 1]
    return if (fecha.year == hoy.year) "avalúo del ${fecha.dayOfMonth} de $mes"
    else "avalúo del ${fecha.dayOfMonth} de $mes de ${fecha.year}"
}

/**
 * El renglón de abajo del nombre en la lista de Bienes: la clase y de cuándo es el valor.
 * «Inmueble · avalúo del 28 de agosto». Sin fecha, solo la clase: decir «sin fecha» en cada
 * renglón sería un reproche, no un dato.
 */
fun subtituloDelBien(cuenta: Account, hoy: LocalDate): String {
    val bien = cuenta.bien ?: return ""
    val clase = claseDeBien(bien.clase).nombre
    return textoDelAvaluo(bien.valorAl, hoy)?.let { "$clase · $it" } ?: clase
}

/**
 * **Lo que es tuyo de verdad**, en una línea: «Debes $1.030,6M en Hipoteca 1254 · tuyo $381,3M».
 *
 * Es la razón de asociar un bien a su deuda. El patrimonio no cambia con la asociación (la deuda
 * ya restaba y el bien ya sumaba); lo que cambia es que el dueño ve, al lado de la casa, cuánto de
 * ella es suyo — que es la pregunta que se hace cuando mira una hipoteca.
 *
 * Cuando lo que se debe supera el valor —pasa con los carros, que se deprecian más rápido de lo
 * que se amortizan—, la frase lo dice en vez de escribir «tuyo −$5M», que se lee como un error.
 */
fun lineaDeLoQueEsTuyo(deuda: DeudaDelBien): String {
    val debes = "Debes ${formatMoneyCompact(deuda.debes)} en ${deuda.deuda.name}"
    return if (deuda.tuyo >= 0L) "$debes · tuyo ${formatMoneyCompact(deuda.tuyo)}"
    else "$debes · debes ${formatMoneyCompact(-deuda.tuyo)} más de lo que vale"
}

/** Los bienes de la lista, en el orden en que vienen (el de `GET /api/accounts`: por nombre). */
fun bienesDe(accounts: List<Account>): List<Account> = accounts.filter { it.esBien }

/**
 * Las deudas que se le pueden asociar a un bien: préstamos y tarjetas —en ese orden, porque lo
 * normal es que a una casa o a un carro los financie un préstamo— y nunca un bien.
 */
fun deudasParaAsociar(accounts: List<Account>): List<Account> =
    accounts.filter { esCuentaDeDeuda(it.type) && !it.esBien }
        .sortedBy { if (it.type == com.jvillada.movi.shared.model.AccountType.LOAN) 0 else 1 }
