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

private val MESES = setOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
    "septiembre", "setiembre", "octubre", "noviembre", "diciembre",
)
private val ABREVIATURAS_DE_MES = setOf(
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic",
)
private val CONECTORES_DE_FECHA = setOf("de", "del")

private fun esPalabraDeMes(palabra: String) = palabra in MESES || palabra in ABREVIATURAS_DE_MES

private fun esAnio(palabra: String) =
    palabra.length == 4 && palabra.all { it.isDigit() } && (palabra.startsWith("19") || palabra.startsWith("20"))

private fun esPalabraDeFecha(palabra: String) = esPalabraDeMes(palabra) || esAnio(palabra)

/** Palabras de [nombre], cada una pasada por [claveComparableDeNombre] — sin tildes, minúsculas. */
private fun palabrasClave(nombre: String): List<String> =
    nombre.split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotBlank() }.map(::claveComparableDeNombre)

/**
 * **El nombre de una regla pega con el de un movimiento aunque el movimiento diga cuándo.**
 *
 * El dueño nombra su sueldo «Salario Octubre 2026» y la regla se llama «Salario»: hoy
 * [claveComparableDeNombre] exige igualdad exacta y eso no pega. Esta función es más floja, pero
 * solo en una dirección muy angosta: [nombreMovimiento] tiene que empezar, palabra por palabra,
 * con [nombreRegla] — y lo que sobre después de eso tiene que ser puro «cuándo»: nombres de mes en
 * español (también «setiembre»), sus abreviaturas de tres letras **como palabra completa** (para
 * no comerse «septiembre» por "sep"), años de 4 dígitos (19xx/20xx), y los conectores «de»/«del»
 * cuando quedan sueltos junto a esos — nunca solos.
 *
 * Por qué el prefijo y no «quitarle el mes a cualquiera de los dos nombres y comparar»: una regla
 * puede llamarse ella misma con un mes adentro («Prima de junio», el aguinaldo de mitad de año).
 * Si se le quitara «junio» a esa regla para comparar, «Prima de junio» y un movimiento suelto
 * «Prima» (sin decir cuál) pasarían por la misma clave — y esa regla sí necesita que el
 * movimiento diga «junio». Por eso el nombre de la regla nunca se toca: solo se mira si el
 * movimiento lo dice completo y punto, y lo único que se le perdona es la cola con la fecha.
 *
 * Antes que todo eso, la igualdad de [claveComparableDeNombre] sigue valiendo sola: los bancos pegan
 * las palabras del comercio (`SMARTFIT` contra la regla «Smart Fit», o al revés), y comparar
 * palabra por palabra no vería que son el mismo nombre. La cola de fecha se suma a esa igualdad;
 * no la reemplaza.
 *
 * Vacío no pega con nada: una regla sin ninguna palabra de identidad no puede ganarle a un
 * movimiento por descarte.
 */
fun nombreDeMovimientoPegaConRegla(nombreRegla: String, nombreMovimiento: String): Boolean {
    val claveRegla = claveComparableDeNombre(nombreRegla)
    if (claveRegla.isNotEmpty() && claveRegla == claveComparableDeNombre(nombreMovimiento)) return true
    val clavesRegla = palabrasClave(nombreRegla)
    if (clavesRegla.isEmpty()) return false
    val clavesMovimiento = palabrasClave(nombreMovimiento)
    if (clavesMovimiento.size < clavesRegla.size) return false
    if (clavesMovimiento.subList(0, clavesRegla.size) != clavesRegla) return false
    val resto = clavesMovimiento.subList(clavesRegla.size, clavesMovimiento.size)
    if (resto.isEmpty()) return true
    if (resto.none(::esPalabraDeFecha)) return false
    return resto.withIndex().all { (i, palabra) ->
        esPalabraDeFecha(palabra) ||
            (
                palabra in CONECTORES_DE_FECHA &&
                    ((i > 0 && esPalabraDeFecha(resto[i - 1])) || (i < resto.lastIndex && esPalabraDeFecha(resto[i + 1])))
                )
    }
}
