package com.jvillada.movi.shared.model

/*
 * # Vuelta 138 · una categoría del catálogo con el nombre del dueño
 *
 * Las categorías del catálogo ([PREDEFINED_CATEGORIES]) son una constante compartida por todos los
 * usuarios. Desde esta vuelta el dueño puede **renombrar** una («Vivienda» → «Hogar») y la reescritura
 * es la de siempre (`rewriteCategory` en el server: movimientos, presupuesto, pagos fijos, apartados y
 * preferencias). El costo de la constante sigue ahí: todo lo que Movi propone por su cuenta —el parser
 * de los avisos, las palabras clave, el importador de extractos, Movi AI, la lista del selector—
 * conoce el nombre viejo. Por eso el server deja, en `category_prefs`, la fila del nombre del catálogo
 * escondida y con el nombre al que pasó (`renombrada_a`; al unificar, `unificada_en`), y **todo lo que
 * propone un nombre lo pasa por [nombreVigente]**: «Vivienda» sale «Hogar».
 *
 * El mapa es nombre del catálogo → nombre que usa el dueño. Lo arma el server con una sola lectura
 * (`aliasDeCategorias`) y el cliente con [aliasDeLasPreferencias], desde lo que trae el Inicio.
 */

/**
 * Cuántos saltos sigue [nombreVigente] como mucho. El server mueve el alias al renombrar o unificar
 * el destino, así que en la práctica es un salto; el tope existe para que un dato raro (dos alias que
 * se apuntan) no cuelgue nada.
 */
private const val SALTOS_DEL_ALIAS = 8

/**
 * **El nombre con que el dueño llama hoy a [nombre]**: si [nombre] es una categoría del catálogo que
 * renombró o unificó en otra, el nombre nuevo; si no, [nombre] tal cual.
 *
 * Compara sin distinguir mayúsculas (el modelo de un extracto puede escribir «vivienda»), pero **sí
 * distingue tildes**: «Educacion» escrita a mano y «Educación» del catálogo son dos categorías en los
 * datos del dueño (ver `suggestCategoryMatches`), y aplastarlas mandaría la propia al alias de la otra.
 *
 * No cambia ninguna cifra: es el nombre que se PROPONE, y cada movimiento se sigue anotando con lo que
 * el dueño confirme.
 */
fun nombreVigente(nombre: String, alias: Map<String, String>): String {
    if (alias.isEmpty()) return nombre
    var actual = nombre
    val vistos = mutableSetOf<String>()
    repeat(SALTOS_DEL_ALIAS) {
        val clave = actual.trim().lowercase()
        if (clave.isEmpty() || !vistos.add(clave)) return actual
        val siguiente = alias.entries
            .firstOrNull { it.key.trim().lowercase() == clave }
            ?.value?.trim()?.takeIf { it.isNotEmpty() }
            ?: return actual
        actual = siguiente
    }
    return actual
}

/**
 * El alias del cliente, desde las preferencias que trae el Inicio (`UsedCategoriesCache.prefs`): las
 * filas con [CategoryPref.renombradaA].
 */
fun aliasDeLasPreferencias(prefs: Map<String, CategoryPref>): Map<String, String> =
    prefs.mapNotNull { (nombre, pref) ->
        pref.renombradaA?.trim()?.takeIf { it.isNotEmpty() }?.let { nombre.trim() to it }
    }.toMap()

/**
 * ¿[nombre] es una categoría del catálogo de Movi? Exacto, como la guarda de siempre del server
 * (`PREDEFINED_CATEGORIES.any { it.name == from }`).
 */
fun esDelCatalogo(nombre: String): Boolean = PREDEFINED_CATEGORIES.any { it.name == nombre.trim() }

/**
 * **El tipo que viaja al renombrar una del catálogo.** «Vivienda» es de gasto porque el catálogo lo
 * dice; «Hogar» ya no está en el catálogo y, sin uso, quedaría «Sin usar» y se ofrecería en los dos
 * lados. Si el dueño no había fijado otro, el nombre nuevo queda con el tipo del catálogo fijado.
 * `null` si [nombre] no es del catálogo.
 */
fun tipoDelCatalogo(nombre: String): String? = PREDEFINED_CATEGORIES.firstOrNull { it.name == nombre.trim() }?.type

/** La línea de la hoja de renombrar una del catálogo: el cambio es solo de este dueño. */
fun lineaDeRenombrarUnaDelCatalogo(nombreNuevo: String): String =
    "Solo cambia para ti: tus movimientos, presupuestos y pagos fijos pasan a «$nombreNuevo»."
