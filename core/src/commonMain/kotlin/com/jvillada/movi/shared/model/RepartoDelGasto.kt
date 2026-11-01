package com.jvillada.movi.shared.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/*
 * # Partes distintas por gasto (vuelta 97)
 *
 * Hasta la vuelta 96 el reparto era **uno por grupo** ([Espacio.reparto]: mitad y mitad, o los
 * porcentajes del grupo). Ahora cada gasto compartido puede llevar el suyo: una cena donde Caro no
 * comió, algo que pagaste por ella («Solo de Caro»), un viaje donde se consumió distinto.
 *
 * **Nada de esto toca [montoContado].** La parte de los demás sigue saliendo de
 * [GastoDelEspacio.partes] (`conLoCompartido` → `parteAjena`), y las partes las arma SIEMPRE
 * [partesDelGasto], que ahora recibe el reparto del gasto. La validación vive acá, una sola, y la
 * usan la hoja (para no dejar guardar) y el server (422 sin tocar nada).
 *
 * **Compatibilidad**: el reparto viaja opcional ([GastoDelEspacio.reparto],
 * [CompartirMovimientoRequest.reparto]). Un APK viejo no lo manda y el server usa la regla del grupo,
 * como siempre; un gasto guardado antes de esta vuelta no lo tiene y se lee como la regla del grupo.
 */

/** De qué forma se repartió un gasto. */
@Serializable
enum class FormaDeRepartir {
    /** La regla del grupo ([Espacio.reparto]): «Partes iguales» en un grupo mitad y mitad. La de siempre. */
    DEL_GRUPO,

    /** Un porcentaje por miembro, que suman 100. */
    PORCENTAJES,

    /** Un monto por miembro, que suman exacto el gasto. */
    MONTOS,

    /** Todo le toca a uno ([RepartoDelGasto.soloDe]): el gasto queda en el grupo, pero es de esa persona. */
    SOLO_DE,
}

/**
 * **El reparto de un gasto**, tal como lo eligió el dueño. Solo se usa el campo de su [forma]:
 * [porcentajes] con [FormaDeRepartir.PORCENTAJES], [montos] con [FormaDeRepartir.MONTOS] y [soloDe]
 * con [FormaDeRepartir.SOLO_DE]. Las llaves son ids de miembro ([MIEMBRO_YO] o el del tercero).
 *
 * Con [FormaDeRepartir.MONTOS] los montos son los de cuando se guardó: si después cambia el monto
 * del movimiento, [partesDelGasto] los escala en proporción (ver ahí).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RepartoDelGasto(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val forma: FormaDeRepartir = FormaDeRepartir.DEL_GRUPO,
    val porcentajes: Map<String, Int> = emptyMap(),
    val montos: Map<String, Long> = emptyMap(),
    val soloDe: String? = null,
) {
    companion object {
        /** La regla del grupo: lo que se elige de entrada. */
        val DEL_GRUPO: RepartoDelGasto = RepartoDelGasto()
    }
}

/** ¿[reparto] es la regla del grupo (o no hay reparto propio)? */
fun esDelGrupo(reparto: RepartoDelGasto?): Boolean = reparto == null || reparto.forma == FormaDeRepartir.DEL_GRUPO

/**
 * **Por qué [reparto] no se puede guardar para un gasto de [monto] en [espacio]**, o `null` si se
 * puede. La única validación del reparto: la hoja no deja guardar con un texto acá, y el server
 * contesta 422 con este mismo texto.
 *
 * - Los porcentajes van de 0 a 100 y suman exacto 100.
 * - Los montos no son negativos y suman exacto el total.
 * - «Solo de …» nombra a un miembro del grupo.
 * - En todos, solo se nombran miembros del grupo.
 */
fun problemaDelReparto(reparto: RepartoDelGasto?, monto: Long, espacio: Espacio): String? {
    if (esDelGrupo(reparto)) return null
    val r = reparto!!
    val miembros = idsDeMiembros(espacio).toSet()
    return when (r.forma) {
        FormaDeRepartir.DEL_GRUPO -> null
        FormaDeRepartir.PORCENTAJES -> when {
            r.porcentajes.keys.any { it !in miembros } -> NOMBRA_A_ALGUIEN_DE_AFUERA
            r.porcentajes.values.any { it !in 0..100 } -> "Cada porcentaje va de 0 a 100"
            else -> faltaParaCien(r.porcentajes.values.sum())
        }
        FormaDeRepartir.MONTOS -> when {
            r.montos.keys.any { it !in miembros } -> NOMBRA_A_ALGUIEN_DE_AFUERA
            r.montos.values.any { it < 0L } -> "Ningún monto puede ser negativo"
            else -> faltaParaElTotal(r.montos.values.sum(), monto)
        }
        FormaDeRepartir.SOLO_DE -> if (r.soloDe == null || r.soloDe !in miembros) "Elige de quién es el gasto" else null
    }
}

private const val NOMBRA_A_ALGUIEN_DE_AFUERA = "Ese reparto nombra a alguien que no está en el grupo"

/** «Faltan 10 %» / «Te pasaste por 5 %», o `null` si suman 100. */
fun faltaParaCien(suma: Int): String? = when {
    suma < 100 -> "Faltan ${100 - suma} %"
    suma > 100 -> "Te pasaste por ${suma - 100} %"
    else -> null
}

/** «Faltan $10.000» / «Te pasaste por $5.000», o `null` si [suma] es exacto [total]. */
fun faltaParaElTotal(suma: Long, total: Long): String? = when {
    suma < total -> "Faltan $${conPuntosDeMiles(total - suma)}"
    suma > total -> "Te pasaste por $${conPuntosDeMiles(suma - total)}"
    else -> null
}

/**
 * **Las partes con un reparto propio**: lo que [partesDelGasto] hace cuando el gasto trae uno que no
 * es la regla del grupo. `null` si el reparto no sirve para armar partes (un «Solo de» de alguien que
 * ya no está, montos que suman cero): entonces vale la regla del grupo.
 *
 * - **Porcentajes**: cada uno su porcentaje, redondeado hacia abajo.
 * - **Montos**: tal cual si suman el monto; si el monto del movimiento cambió después de guardarlos,
 *   **en proporción** a los montos guardados (el porcentaje implícito), redondeado hacia abajo.
 * - **Solo de**: todo a esa persona; los demás en cero.
 *
 * El peso que sobra del redondeo lo pone **quien pagó**, como siempre. Si quien pagó no lleva parte
 * (un 0 %), lo pone el de la parte más grande: un 0 % sigue siendo 0.
 */
internal fun partesConElReparto(
    monto: Long,
    ids: List<String>,
    pagadoPor: String,
    reparto: RepartoDelGasto,
): Map<String, Long>? {
    val partes = LinkedHashMap<String, Long>()
    when (reparto.forma) {
        FormaDeRepartir.DEL_GRUPO -> return null
        FormaDeRepartir.SOLO_DE -> {
            val de = reparto.soloDe?.takeIf { it in ids } ?: return null
            ids.forEach { partes[it] = if (it == de) monto else 0L }
            return partes
        }
        FormaDeRepartir.PORCENTAJES -> {
            if (ids.sumOf { reparto.porcentajes[it] ?: 0 } <= 0) return null
            ids.forEach { partes[it] = monto * ((reparto.porcentajes[it] ?: 0).coerceIn(0, 100)) / 100 }
        }
        FormaDeRepartir.MONTOS -> {
            val suma = ids.sumOf { (reparto.montos[it] ?: 0L).coerceAtLeast(0L) }
            if (suma <= 0L) return null
            ids.forEach {
                val guardado = (reparto.montos[it] ?: 0L).coerceAtLeast(0L)
                partes[it] = if (suma == monto) guardado else proporcion(monto, guardado, suma)
            }
        }
    }
    val sobra = monto - partes.values.sum()
    if (sobra != 0L) {
        val quienPone = pagadoPor.takeIf { (partes[it] ?: 0L) > 0L } ?: partes.maxByOrNull { it.value }!!.key
        partes[quienPone] = (partes[quienPone] ?: 0L) + sobra
    }
    return partes
}

/** `monto × parte ÷ total`, hacia abajo, sin desbordar con montos grandes. */
private fun proporcion(monto: Long, parte: Long, total: Long): Long =
    (monto.toDouble() * parte.toDouble() / total.toDouble()).toLong().coerceIn(0L, monto)

/**
 * **El reparto de la última vez, para un gasto de otro monto** (la propuesta de repartir): unos montos
 * exactos de $2.400.000 no cuadran con un arriendo de $2.500.000, así que se llevan al monto nuevo en
 * proporción ([partesDelGasto]). Las demás formas no dependen del monto y quedan igual.
 */
fun repartoParaOtroMonto(reparto: RepartoDelGasto?, monto: Long, espacio: Espacio, pagadoPor: String = MIEMBRO_YO): RepartoDelGasto? {
    if (reparto == null || reparto.forma != FormaDeRepartir.MONTOS) return reparto
    if (reparto.montos.values.sum() == monto) return reparto
    val partes = partesDelGasto(monto, espacio, pagadoPor, reparto)
    if (partes.isEmpty()) return null
    return reparto.copy(montos = partes)
}
