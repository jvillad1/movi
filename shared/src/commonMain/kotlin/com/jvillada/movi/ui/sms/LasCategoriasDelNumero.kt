package com.jvillada.movi.ui.sms

import com.jvillada.movi.shared.model.CategoriaDelNumero
import com.jvillada.movi.shared.model.queSeOfrecen

/** La línea que explica por qué las pastillas arrancan con las del número (Revisar aviso y la tarjeta). */
const val TAG_LINEA_DEL_NUMERO: String = "linea-del-numero"

/** Cuántas categorías nombra la línea antes de decir «y N más». */
private const val CATEGORIAS_EN_LA_LINEA = 3

/**
 * Las categorías del número que sí se pueden ofrecer: sin las que ningún selector ofrece («Avance de
 * tarjeta», «Descuento de nómina», «Pago de un tercero»…). Con menos de dos no queda nada que ofrecer.
 */
fun lasDelNumero(categorias: List<CategoriaDelNumero>): List<CategoriaDelNumero> =
    categorias.queSeOfrecen()

/**
 * **«A esta llave le pusiste Parqueadero 3 veces y Gasolina 2»** (categorías del número): lo que el
 * dueño ya anotó a esa llave o cuenta, dicho antes de las pastillas. `null` con menos de dos
 * categorías: con una, la propuesta de siempre ya lo explica («Así lo anotaste 3 veces»). Con más de
 * tres, las tres primeras y «y N más». La palabra «veces» va en la primera; «vez» cierra una
 * categoría con un solo uso si es la última que se nombra.
 */
fun lineaDelNumero(todas: List<CategoriaDelNumero>, esLlave: Boolean): String? {
    val categorias = lasDelNumero(todas)
    if (categorias.isEmpty()) return null
    val cuales = categorias.take(CATEGORIAS_EN_LA_LINEA)
    val faltan = categorias.size - cuales.size
    val partes = cuales.mapIndexed { i, c ->
        val ultima = i == cuales.lastIndex && faltan == 0
        val palabra = when {
            i == 0 -> if (c.veces == 1) " vez" else " veces"
            ultima && c.veces == 1 -> " vez"
            else -> ""
        }
        "${c.categoria} ${c.veces}$palabra"
    } + listOfNotNull(if (faltan > 0) "$faltan más" else null)
    val lista = partes.dropLast(1).joinToString(", ") + " y " + partes.last()
    return "A esta ${if (esLlave) "llave" else "cuenta"} le pusiste $lista"
}

/** Las categorías del número en su orden y, después, [resto] sin repetir. */
fun primeroLasDelNumero(todas: List<CategoriaDelNumero>, resto: List<String>): List<String> {
    val delNumero = lasDelNumero(todas).map { it.categoria }
    return delNumero + resto.filterNot { it in delNumero }
}
