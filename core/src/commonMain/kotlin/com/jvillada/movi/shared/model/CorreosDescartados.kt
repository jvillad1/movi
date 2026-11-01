package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/*
 * # Vuelta 90 · el correo que no se pudo guardar no se pierde en silencio
 *
 * El correo del banco entra por un webhook (Resend). Hasta acá, un correo sin el código de Movi en la
 * dirección, uno demasiado grande o uno que no se podía leer se contestaba 202 y **no dejaba rastro**:
 * Resend lo daba por entregado y nadie se enteraba. Ahora cada uno deja una fila en
 * `correos_descartados` (fecha, motivo, dominio del remitente, tamaño e id del proveedor; **nunca el
 * cuerpo**), y la pantalla de la captura lo dice en una línea.
 */

/**
 * Por qué no se guardó un correo. Viaja como texto ([GrupoDeCorreosDescartados.motivo]) para que un
 * motivo nuevo no rompa a un cliente viejo: uno que no conoce lo dice sin el detalle.
 */
enum class MotivoDeCorreoDescartado(val texto: String) {
    /** La dirección a la que llegó no trae el código de nadie (`alertas+<código>@…`). */
    SIN_CODIGO("la dirección no trae el código de Movi"),
    /** Trae un código, pero no es de ninguna cuenta. */
    CODIGO_DE_NADIE("la dirección no corresponde a ninguna cuenta"),
    /** Más grande que lo que el server acepta, y sin nada que guardar en su lugar. */
    DEMASIADO_GRANDE("era demasiado grande"),
    /** No traía texto que leer (ni asunto ni cuerpo), o no se entendía. */
    ILEGIBLE("no se pudo leer"),
    /** El aviso del proveedor no decía qué correo era. */
    SIN_ID("el aviso del proveedor no decía qué correo era"),
    /**
     * Ya estaba en la bandeja (otra copia del mismo pago). **No se muestra**: no se perdió nada. Existe
     * para que el reproceso diario sepa que ese id del proveedor ya se miró.
     */
    DUPLICADO("ya estaba en tu bandeja"),
}

/** El motivo de [codigo], o `null` si es de una versión más nueva del server. */
fun motivoDeCorreoDescartado(codigo: String): MotivoDeCorreoDescartado? =
    MotivoDeCorreoDescartado.entries.firstOrNull { it.name == codigo }

/** Cuántos correos se descartaron por un mismo [motivo] (el nombre del enum), y el más reciente. */
@Serializable
data class GrupoDeCorreosDescartados(
    val motivo: String,
    val cuantos: Int,
    val ultimo: Long,
)

/**
 * `GET /api/correo-entrante/descartados`: lo descartado en los últimos [dias] días.
 *
 * - [propios]: los correos que se le atribuyeron a quien pregunta y aun así no se guardaron.
 * - [sinDueno]: los que no son de nadie (sin código, o de un código que no es de ninguna cuenta). **Solo
 *   para un admin y solo contados**: no se sabe de quién son, así que nadie ve el remitente ni el id;
 *   `null` para todos los demás.
 *
 * Sin [MotivoDeCorreoDescartado.DUPLICADO]: no es una pérdida.
 */
@Serializable
data class CorreosDescartados(
    val dias: Int = DIAS_DE_CORREOS_DESCARTADOS,
    val propios: List<GrupoDeCorreosDescartados> = emptyList(),
    val sinDueno: List<GrupoDeCorreosDescartados>? = null,
)

/** La ventana que se cuenta: los últimos 30 días. */
const val DIAS_DE_CORREOS_DESCARTADOS: Int = 30

private fun motivoDelGrupo(grupos: List<GrupoDeCorreosDescartados>): String {
    val principal = grupos.maxWithOrNull(compareBy<GrupoDeCorreosDescartados> { it.cuantos }.thenBy { it.ultimo })
    return principal?.let { motivoDeCorreoDescartado(it.motivo)?.texto } ?: "no se pudieron leer"
}

private fun correos(n: Int): String = if (n == 1) "1 correo" else "$n correos"

/**
 * **La línea de la pantalla de la captura**, o `null` si no hay nada que decir:
 *
 * - «2 correos no se pudieron atribuir en los últimos 30 días: la dirección no trae el código de Movi.»
 *   (solo un admin ve los que no son de nadie);
 * - «1 correo tuyo no se pudo guardar en los últimos 30 días: no se pudo leer.»
 *
 * El motivo es el del grupo más numeroso.
 */
fun textoDeCorreosDescartados(descartados: CorreosDescartados): String? {
    val propios = descartados.propios.filter { it.motivo != MotivoDeCorreoDescartado.DUPLICADO.name && it.cuantos > 0 }
    val sinDueno = descartados.sinDueno.orEmpty().filter { it.motivo != MotivoDeCorreoDescartado.DUPLICADO.name && it.cuantos > 0 }
    val dias = descartados.dias
    val frases = buildList {
        val nSinDueno = sinDueno.sumOf { it.cuantos }
        if (nSinDueno > 0) {
            val verbo = if (nSinDueno == 1) "no se pudo atribuir" else "no se pudieron atribuir"
            add("${correos(nSinDueno)} $verbo en los últimos $dias días: ${motivoDelGrupo(sinDueno)}.")
        }
        val nPropios = propios.sumOf { it.cuantos }
        if (nPropios > 0) {
            val quien = if (nPropios == 1) "1 correo tuyo no se pudo guardar" else "$nPropios correos tuyos no se pudieron guardar"
            add("$quien en los últimos $dias días: ${motivoDelGrupo(propios)}.")
        }
    }
    return frases.joinToString(" ").ifEmpty { null }
}
