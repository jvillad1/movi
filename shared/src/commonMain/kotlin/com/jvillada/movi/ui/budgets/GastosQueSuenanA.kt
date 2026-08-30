package com.jvillada.movi.ui.budgets

import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isReservedCategory

/**
 * Cómo se pareció el movimiento a la categoría — para que el texto que ve el dueño diga la
 * verdad sobre por qué se lo estamos proponiendo, en vez de un «se parece» sin explicación.
 */
enum class Coincidencia {
    /** La descripción **es** el nombre de la categoría: «Mercado» en un presupuesto de «Mercado». */
    EXACTA,

    /** La descripción **menciona** la categoría como palabra: «Mercado Éxito», «mercado del mes». */
    MENCION,
}

data class GastoQueSuena(val evento: FinancialEvent, val coincidencia: Coincidencia)

/**
 * Movimientos del período que **se llaman como la categoría** del presupuesto pero están
 * archivados en otra.
 *
 * ### De dónde sale
 *
 * El dueño creó un presupuesto de $2.000.000 en «Mercado» y tenía, ese mismo período, un gasto de
 * exactamente $2.000.000 cuya **descripción** era «Mercado» y cuya **categoría** era «Comida».
 * Pidió: *«tengo un gasto del período exactamente por el monto de todo el presupuesto del mercado,
 * debería poder permitirme asociarlo a dicha categoría de gastos»*.
 *
 * ### Por qué la descripción y no el monto
 *
 * El monto coincidía, y era tentador usarlo. Pero un gasto que casualmente vale lo mismo que el
 * límite no tiene nada que ver con él: proponer por monto acertaría en este caso y sería ruido en
 * todos los demás. El **nombre** sí es una intención — alguien que escribe «Mercado» está diciendo
 * qué es ese gasto.
 *
 * ### Por qué también la mención, y por qué NO más que eso
 *
 * La igualdad exacta era frágil de un modo que se nota enseguida al usar la app: «Mercado Éxito»,
 * «mercado del mes» o «Mercado agosto» son el mismo gasto para cualquiera que lo escribió, y
 * quedaban afuera. Por eso entra la **mención como palabra completa**, normalizando tildes y
 * mayúsculas.
 *
 * Lo que deliberadamente NO se hace es parecido difuso (distancia de edición, prefijos, raíces).
 * Acá no se propone un ícono: se propone **mover plata de una categoría a otra**, que es lo que
 * decide si un presupuesto se ve cumplido o excedido. Un falso positivo no es un ruidito, es una
 * cifra equivocada en la pantalla que el dueño usa para decidir. «Palabra completa» tiene una
 * regla que se puede explicar en una línea —y por eso también se puede desconfiar de ella— y
 * nunca dispara sola: la asociación la confirma él tocando la fila.
 *
 * `mercado` dentro de `supermercado` NO cuenta: es un sufijo, no una mención. Esa es exactamente
 * la clase de coincidencia que un `contains` pelado habría tomado por buena.
 */
fun gastosQueSuenanA(
    categoria: String,
    dias: List<EventDay>,
    ventana: LongRange,
): List<GastoQueSuena> {
    val buscada = normalizar(categoria)
    if (buscada.isEmpty() || isReservedCategory(categoria.trim())) return emptyList()

    return dias.flatMap { it.items }
        .filter { it.timestamp in ventana }
        .filter { it.type == TransactionType.EXPENSE && it.currency == "COP" }
        // Ya está donde debería: no hay nada que proponer.
        .filterNot { normalizar(it.category) == buscada }
        // Y no se toca lo que Movi gobierna: mover un «Saldo inicial» a una categoría normal lo
        // convertiría en gasto del mes.
        .filterNot { isReservedCategory(it.category) }
        .mapNotNull { ev ->
            val desc = normalizar(ev.description)
            when {
                desc == buscada -> GastoQueSuena(ev, Coincidencia.EXACTA)
                mencionaComoPalabra(desc, buscada) -> GastoQueSuena(ev, Coincidencia.MENCION)
                else -> null
            }
        }
        // Las exactas primero: son las que el dueño reconoce sin pensar.
        .sortedBy { it.coincidencia.ordinal }
}

/**
 * Minúsculas, sin tildes y con los espacios colapsados. Sin esto «Mercado» y «mercado» ya
 * coincidían (se comparaba con `ignoreCase`), pero «Cafetería» y «cafeteria» no — y la tilde la
 * pone o no la pone el teclado del teléfono, no una decisión de quien escribe.
 */
private fun normalizar(texto: String): String {
    val sb = StringBuilder()
    for (c in texto.trim().lowercase()) {
        val sinTilde = when (c) {
            'á', 'à', 'ä', 'â' -> 'a'
            'é', 'è', 'ë', 'ê' -> 'e'
            'í', 'ì', 'ï', 'î' -> 'i'
            'ó', 'ò', 'ö', 'ô' -> 'o'
            'ú', 'ù', 'ü', 'û' -> 'u'
            else -> c
        }
        // «ñ» se deja como está a propósito: no es una vocal acentuada sino otra letra, y
        // convertirla en «n» juntaría «año» con «ano».
        if (sinTilde.isWhitespace()) {
            if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
        } else {
            sb.append(sinTilde)
        }
    }
    return sb.toString().trim()
}

/**
 * Si [texto] contiene [palabra] delimitada por bordes que no son letra ni dígito. Ambos llegan ya
 * normalizados. Se acepta una categoría de varias palabras («cuota carro») buscándola entera.
 */
private fun mencionaComoPalabra(texto: String, palabra: String): Boolean {
    var desde = 0
    while (true) {
        val i = texto.indexOf(palabra, desde)
        if (i < 0) return false
        val antes = texto.getOrNull(i - 1)
        val despues = texto.getOrNull(i + palabra.length)
        if (!esParteDePalabra(antes) && !esParteDePalabra(despues)) return true
        desde = i + 1
    }
}

private fun esParteDePalabra(c: Char?): Boolean = c != null && (c.isLetter() || c.isDigit())
