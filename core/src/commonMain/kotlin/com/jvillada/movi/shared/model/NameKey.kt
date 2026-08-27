package com.jvillada.movi.shared.model

/**
 * Nombre comparable: sin mayúsculas, sin acentos y sin nada que no sea letra o número, para que
 * «Netflix», «netflix» y «NETFLIX  Premium.» no se lean como cosas distintas.
 *
 * A propósito NO intenta ser inteligente (nada de distancias de edición ni de subcadenas):
 * cuanto más suelta la comparación, más fácil es emparejar dos cosas que no son la misma — y en
 * esta app equivocarse hacia «sí, es lo mismo» es lo caro (ver `occurrenceCandidatesFor` en el
 * server: dar por ocurrido un recurrente que no ocurrió apaga el aviso de una deuda real).
 *
 * Vive en `:core` y no en la pantalla de Recurrentes porque ahora la usan los dos lados: la UI
 * para no duplicar filas, y el server para proponer qué movimiento fue la ocurrencia de un
 * recurrente. `com.jvillada.movi.ui.recurrentes.claveDeNombre` delega acá — una sola definición,
 * porque si el cliente y el server normalizaran distinto, el server propondría emparejamientos
 * que la pantalla no sabría explicar.
 */
fun claveComparableDeNombre(nombre: String): String {
    val sinAcentos = nombre.map { c ->
        when (c.lowercaseChar()) {
            'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú' -> 'u'; 'ü' -> 'u'; 'ñ' -> 'n'
            else -> c.lowercaseChar()
        }
    }
    return sinAcentos.filter { it.isLetterOrDigit() }.joinToString("")
}
