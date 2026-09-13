package com.jvillada.movi.shared.model

/**
 * **Cómo se compara un texto cuando alguien busca**, en un solo lugar.
 *
 * ### Por qué existe
 *
 * Había tres copias privadas de esta función —en la búsqueda de Movimientos, en el campo de
 * categoría y en la pantalla de categorías— y ya se habían separado: **la de Movimientos no pasaba
 * la `ü` a `u`**, y las otras dos sí. O sea que buscar «pinguino» encontraba «Pingüino» al elegir
 * una categoría y no lo encontraba en la lista de movimientos. Tres copias de una regla chica es
 * exactamente cómo eso pasa: nadie cambia las tres a la vez.
 *
 * ### Qué hace
 *
 * - **Minúsculas**, y **sin tildes** en ninguna de sus formas (`á à â ä` → `a`, igual las demás).
 * - **`ñ` → `n`.** En una búsqueda, la colisión que eso produce —«año» y «ano»— cuesta un
 *   resultado de más, y a cambio encuentra lo que se escribió desde un teclado sin `ñ`, que es la
 *   mitad de los teclados.
 * - **Los espacios se aprietan**: `«Carnes  y Legumbres»` con dos espacios y `«carnes y»` tienen
 *   que encontrarse. Y se recortan los bordes.
 *
 * ### Lo que NO es
 *
 * **No es una clave de identidad.** Para decidir si dos nombres son la misma cosa está
 * [claveComparableDeNombre], que además tira todo lo que no sea letra o dígito — y que no debe
 * cambiar, porque con ella se guardan claves.
 *
 * **Y no reemplaza a `GastosQueSuenanA`**, en Presupuestos, que deja la `ñ` como está a
 * propósito: ahí no se busca, se **agrupan** gastos que parecen de una misma categoría, y juntar
 * «año» con «ano» sería un error y no un resultado de más. Esa diferencia es una decisión, no un
 * descuido; está anotada allá para que nadie la «arregle».
 */
fun normalizarParaBuscar(texto: String): String = buildString(texto.length) {
    for (c in texto.trim().lowercase()) {
        val simple = when (c) {
            'á', 'à', 'â', 'ä' -> 'a'
            'é', 'è', 'ê', 'ë' -> 'e'
            'í', 'ì', 'î', 'ï' -> 'i'
            'ó', 'ò', 'ô', 'ö' -> 'o'
            'ú', 'ù', 'û', 'ü' -> 'u'
            'ñ' -> 'n'
            else -> c
        }
        if (simple.isWhitespace()) {
            if (isNotEmpty() && last() != ' ') append(' ')
        } else {
            append(simple)
        }
    }
}
