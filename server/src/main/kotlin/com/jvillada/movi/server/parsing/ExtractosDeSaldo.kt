package com.jvillada.movi.server.parsing

import com.jvillada.movi.server.routes.montoDelSms
import com.jvillada.movi.shared.model.ResumenDelExtractoDeSaldo
import java.text.Normalizer
import kotlin.math.roundToLong

/**
 * **¿Este texto es un extracto que solo dice cuánto hay?** El fondo voluntario de Skandia
 * ([leerElExtractoDeSkandia]) o una cuenta de ahorros de Davibank, como la AFC
 * ([leerElExtractoAfcDavibank]). Sin IA: se reconocen por su texto y se leen con reglas, **antes**
 * de cualquier llamada a Claude, igual que el extracto de un crédito de Bancolombia. `null` si no
 * es ninguno de los dos o no tiene la forma esperada: el flujo sigue como siempre.
 */
fun leerUnExtractoDeSaldo(texto: String): ResumenDelExtractoDeSaldo? =
    leerElExtractoDeSkandia(texto) ?: leerElExtractoAfcDavibank(texto)

/** «Pensión» → «pension»: para reconocer rótulos sin depender de tildes ni mayúsculas. */
internal fun sinTildes(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "").lowercase()

/**
 * Un monto del extracto en **centavos**: `106,787,542.87` y `106.787.542,87` dan lo mismo (la
 * lectura de los separadores es la de los SMS, [montoDelSms]). En centavos para que una suma de
 * contratos se compare exacta contra el total.
 */
internal fun centavosDelExtracto(raw: String): Long? {
    val negativo = raw.trim().startsWith("-")
    val valor = montoDelSms(raw.trim().trimStart('-', '$').trim()) ?: return null
    val centavos = (valor * 100).roundToLong()
    return if (negativo) -centavos else centavos
}

/** Centavos → pesos enteros, como los lleva Movi (redondeo al peso más cercano). */
internal fun pesosDe(centavos: Long): Long = Math.floorDiv(centavos + 50, 100)

internal val MESES_DEL_EXTRACTO: Map<String, Int> = mapOf(
    "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4, "mayo" to 5, "junio" to 6,
    "julio" to 7, "agosto" to 8, "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
    "noviembre" to 11, "diciembre" to 12,
)

/** «agosto», «AGO», «Sept» → 8, 8, 9. `null` si no es un mes. */
internal fun mesDelExtracto(nombre: String): Int? {
    val n = sinTildes(nombre).trimEnd('.')
    MESES_DEL_EXTRACTO[n]?.let { return it }
    if (n.length < 3) return null
    return MESES_DEL_EXTRACTO.entries.firstOrNull { it.key.startsWith(n.take(3)) }?.value
}
