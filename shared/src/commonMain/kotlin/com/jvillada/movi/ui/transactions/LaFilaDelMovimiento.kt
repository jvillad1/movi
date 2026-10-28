package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.enFormaDeTitulo
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.ui.components.formatMoney

/**
 * # Lo que dice la fila de un movimiento (vuelta de diseño 30)
 *
 * Dos arreglos de presentación, sin tocar lo guardado ni ninguna cuenta:
 *
 * 1. **En un gasto repartido, la cifra grande es tu parte.** La fila decía «−$88.600» en grande y
 *    «Tu parte $44.300» chico, mientras el total del día sumaba −$44.300: la cifra que se lee
 *    primero no era la que cuenta. Ahora la principal es [montoPrincipalDelMovimiento] (tu parte,
 *    `montoContado`) y debajo va [lineaDelRepartido]: «de $88.600 · con Caro» (o el nombre del
 *    espacio, si son más). La plata que salió de la cuenta sigue siendo el total: el saldo no cambia,
 *    y el detalle lo dice («Salieron $88.600 de Nu», [salieronDeLaCuenta]).
 * 2. **«Mercado / Mercado».** Si la descripción es la misma categoría, el título repite lo que dice
 *    el subtítulo. Ahora el título es el comercio o la cuenta, y el subtítulo no repite la
 *    categoría ([textosDeLaFila]).
 */

/** Título y subtítulo de la fila de un movimiento. */
data class TextosDeLaFila(val titulo: String, val subtitulo: String?)

/** ¿La descripción de [tx] es su categoría, escrita igual o casi (mayúsculas, tildes, espacios)? */
fun laDescripcionEsLaCategoria(tx: FinancialEvent): Boolean {
    val categoria = normalizarParaBuscar(tx.category)
    return categoria.isNotEmpty() && normalizarParaBuscar(tx.description) == categoria
}

/**
 * **Título y subtítulo** de la fila de [tx], con [nombreDeLaCuenta] si se conoce.
 *
 * - Lo de siempre: la descripción, y «categoría · cuenta».
 * - Si la descripción es la categoría: el comercio («Éxito», con «Mercado · Nu» debajo); sin
 *   comercio, la cuenta («Nu», con «Mercado» debajo); sin ninguno de los dos, la categoría sola.
 */
fun textosDeLaFila(tx: FinancialEvent, nombreDeLaCuenta: String?): TextosDeLaFila {
    val cuenta = nombreDeLaCuenta?.takeIf { it.isNotBlank() }
    // Vuelta de diseño 32: lo que el banco mandó en mayúsculas se lee en forma de título.
    val descripcion = descripcionParaMostrar(tx)
    val deSiempre = TextosDeLaFila(descripcion, cuenta?.let { "${tx.category} · $it" } ?: tx.category)
    if (!laDescripcionEsLaCategoria(tx)) return deSiempre
    val comercio = tx.merchant?.trim()?.takeIf {
        it.isNotEmpty() && normalizarParaBuscar(it) != normalizarParaBuscar(tx.category)
    }
    return when {
        comercio != null -> TextosDeLaFila(enFormaDeTitulo(comercio), deSiempre.subtitulo)
        cuenta != null -> TextosDeLaFila(cuenta, tx.category)
        else -> TextosDeLaFila(descripcion, null)
    }
}

/**
 * **La descripción de [tx] como se pinta** (vuelta de diseño 32): [enFormaDeTitulo] de `:core`, que
 * pasa a forma de título lo que el banco mandó todo en MAYÚSCULAS («EXITO SUBA» → «Éxito Suba») y
 * deja quieto lo que trae minúsculas. La usan la fila, el detalle, Buscar y el detalle de la cuenta.
 * Lo guardado no cambia.
 */
fun descripcionParaMostrar(tx: FinancialEvent): String = enFormaDeTitulo(tx.description)

/** ¿[tx] es un gasto repartido en un espacio, con una parte de otros? */
fun esGastoRepartido(tx: FinancialEvent): Boolean = tx.type == TransactionType.EXPENSE && tx.parteAjena > 0L

/** La cifra principal de la fila y del detalle: tu parte en un gasto repartido; si no, el monto. */
fun montoPrincipalDelMovimiento(tx: FinancialEvent): Long = if (esGastoRepartido(tx)) montoContado(tx) else tx.amount

/**
 * **Con quién se repartió**: «con Caro» si el espacio tiene una sola persona más; si no, el nombre
 * del espacio («Casa»). `null` si no se conoce el espacio.
 */
fun conQuienSeRepartio(espacio: Espacio?): String? {
    if (espacio == null) return null
    val otros = espacio.miembros.filter { it.id != MIEMBRO_YO && it.nombre.isNotBlank() }
    return if (otros.size == 1) "con ${otros.single().nombre.trim()}" else espacio.nombre.trim().takeIf { it.isNotEmpty() }
}

/** «de $88.600 · con Caro» debajo de tu parte; sin el espacio, «de $88.600». `null` si no está repartido. */
fun lineaDelRepartido(tx: FinancialEvent, espacio: Espacio?): String? {
    if (!esGastoRepartido(tx)) return null
    val total = "de ${formatMoney(tx.amount, tx.currency)}"
    return conQuienSeRepartio(espacio)?.let { "$total · $it" } ?: total
}

/** «Salieron $88.600 de Nu»: lo que salió de la cuenta en un gasto repartido. `null` si no está repartido. */
fun salieronDeLaCuenta(tx: FinancialEvent, nombreDeLaCuenta: String?): String? {
    if (!esGastoRepartido(tx)) return null
    val monto = formatMoney(tx.amount, tx.currency)
    return nombreDeLaCuenta?.takeIf { it.isNotBlank() }?.let { "Salieron $monto de $it" } ?: "Salieron $monto de tu cuenta"
}
