package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.serialization.Serializable

/**
 * # «Banco mudo»: un origen de captura que se calló
 *
 * Ola 2. La captura (SMS, notificaciones, correos) se puede callar sin que nadie se entere: Android
 * mata el listener, el banco cambia el formato del mensaje, el filtro de Gmail se rompe. La alerta
 * de siempre ([alertaDeCapturaEnInicio]) solo grita cuando **nunca** llegó nada; esto cubre el caso
 * más traicionero, el de una captura que andaba y dejó de andar.
 *
 * La regla vive acá, en `:core`, porque la usan dos lados con los mismos datos: el server (para el
 * resumen del Inicio y para el aviso del teléfono) y las pruebas. Es pura: no lee la hora del
 * sistema ni la base.
 *
 * ## Cuándo avisa, y por qué cada freno
 *
 * Un origen (los SMS de Bancolombia, las notificaciones de Nu, los correos de Bancolombia) está
 * **mudo** si se cumplen las tres:
 *
 * 1. **Pasaron [diasDeSilencio] días o más** desde su última captura. Configurable desde la app
 *    (Captura del banco), con [DIAS_PARA_BANCO_MUDO_POR_DEFECTO]; `0` lo apaga.
 * 2. **Antes era regular**: en los [VENTANA_DE_REGULARIDAD_DIAS] días que terminan en su última
 *    captura llegaron al menos [MIN_CAPTURAS_REGULARES], en al menos [MIN_DIAS_CON_CAPTURA] días
 *    distintos. Un origen que mandó dos avisos en su vida no estaba «andando»: su silencio no dice
 *    nada.
 * 3. **El silencio es más largo que cualquier hueco de esa misma ventana.** Un banco que nunca
 *    escribe los fines de semana no está mudo un lunes por la mañana; uno que escribía todos los
 *    días y lleva cuatro sin escribir, sí.
 *
 * Un origen sin ninguna captura no aparece (no hay historia que comparar): ese caso ya lo dice
 * «Movi nunca ha recibido un mensaje de tu banco».
 */

/** Cuántos días de silencio avisan, si el dueño no eligió otra cosa. */
const val DIAS_PARA_BANCO_MUDO_POR_DEFECTO: Int = 3

/** El tope de lo que se puede elegir. `0` apaga el aviso. */
const val MAX_DIAS_PARA_BANCO_MUDO: Int = 30

/** La ventana, hacia atrás desde la última captura, en la que se mide si el origen era regular. */
const val VENTANA_DE_REGULARIDAD_DIAS: Int = 30

/** Cuántas capturas hacen falta en esa ventana para llamarlo regular. */
const val MIN_CAPTURAS_REGULARES: Int = 5

/** Y en cuántos días distintos: cinco SMS de una misma tarde no son una costumbre. */
const val MIN_DIAS_CON_CAPTURA: Int = 3

private const val DIA_MS: Long = 86_400_000L

/** Por dónde llegó una captura. Sale del rótulo `bank` de la fila ([canalDeCaptura]). */
@Serializable
enum class CanalDeCaptura { SMS, NOTIFICACION, CORREO }

/** Una captura: el rótulo de origen tal como quedó en `sms_messages.bank`, y cuándo pasó. */
data class Captura(val origen: String, val momento: Long)

/**
 * Un origen que se calló. [clave] identifica el origen (canal + nombre) y [ultima] es su última
 * captura: juntos son la huella del episodio, para no avisar dos veces el mismo silencio.
 */
@Serializable
data class OrigenMudo(
    val clave: String,
    val canal: CanalDeCaptura,
    /** «Bancolombia», «Nu», «tu banco». */
    val nombre: String,
    val diasSinCaptura: Int,
    val ultima: Long,
)

/**
 * Los códigos con los que Bancolombia manda sus SMS (los mismos del piso de `BankSenderFilter`, en
 * el `androidMain` de `:shared`). Un SMS trae de origen el código y no el nombre.
 */
private val CODIGOS_DE_BANCOLOMBIA = setOf("85540", "891333", "87400")

/**
 * **De dónde vino, como lo diría una persona**, o `null` si no hay nada legible que decir.
 *
 * «Notificación · Nu» → «Nu», «Correo · Bancolombia» → «Bancolombia», «85540» → «Bancolombia». Un
 * código de remitente que no se conoce no se dice: «· 899776» no le explica nada a nadie.
 *
 * Vivía en el `commonMain` de `:shared` (los avisos del teléfono); bajó a `:core` en la Ola 2
 * porque el server la necesita para nombrar el banco mudo, y dos copias del mismo criterio terminan
 * nombrando distinto el mismo banco.
 */
fun nombreDelOrigenDeCaptura(origen: String): String? {
    val limpio = origen.trim()
    if (limpio.isEmpty()) return null
    if ('·' in limpio) return limpio.substringAfter('·').trim().ifBlank { null }
    if (CODIGOS_DE_BANCOLOMBIA.any { it in limpio }) return "Bancolombia"
    if (limpio.all { it.isDigit() || it == '+' }) return null
    if (limpio.equals("SMS", ignoreCase = true)) return null
    return limpio
}

/** El canal de un rótulo de origen: «Notificación · …», «Correo · …», y todo lo demás es un SMS. */
fun canalDeCaptura(origen: String): CanalDeCaptura {
    val limpio = origen.trimStart()
    return when {
        limpio.startsWith("Notificación", ignoreCase = true) || limpio.startsWith("Notificacion", ignoreCase = true) ->
            CanalDeCaptura.NOTIFICACION
        limpio.startsWith("Correo", ignoreCase = true) -> CanalDeCaptura.CORREO
        else -> CanalDeCaptura.SMS
    }
}

/**
 * Qué origen es, para agrupar: el canal y el nombre. Los tres códigos de Bancolombia son un solo
 * origen; un código desconocido es el suyo propio; los SMS sin remitente (el barrido los guarda con
 * `""`, el tiempo real con `"SMS"`) son «tu banco».
 */
private fun claveYNombre(origen: String): Pair<String, String> {
    val canal = canalDeCaptura(origen)
    val nombre = nombreDelOrigenDeCaptura(origen)
        ?: origen.trim().takeIf { it.isNotEmpty() && !it.equals("SMS", ignoreCase = true) }
        ?: "tu banco"
    return "${canal.name}|${nombre.lowercase()}" to nombre
}

/**
 * **Los orígenes que se callaron**, del más callado al menos. Ver el KDoc del archivo para la regla
 * y sus tres frenos. [diasDeSilencio] `<= 0` apaga el aviso: devuelve vacío.
 */
fun origenesMudos(capturas: List<Captura>, ahora: Long, diasDeSilencio: Int): List<OrigenMudo> {
    if (diasDeSilencio <= 0) return emptyList()
    return capturas
        .filter { it.momento <= ahora }
        .groupBy { claveYNombre(it.origen).first }
        .mapNotNull { (clave, deEsteOrigen) ->
            val nombre = claveYNombre(deEsteOrigen.first().origen).second
            val momentos = deEsteOrigen.map { it.momento }.sorted()
            val ultima = momentos.last()
            val silencio = ahora - ultima
            if (silencio < diasDeSilencio * DIA_MS) return@mapNotNull null

            val ventana = momentos.filter { it >= ultima - VENTANA_DE_REGULARIDAD_DIAS * DIA_MS }
            if (ventana.size < MIN_CAPTURAS_REGULARES) return@mapNotNull null
            val dias = ventana.map { epochMillisToAppDate(it) }.distinct().size
            if (dias < MIN_DIAS_CON_CAPTURA) return@mapNotNull null
            val mayorHueco = ventana.zipWithNext { a, b -> b - a }.maxOrNull() ?: 0L
            if (silencio <= mayorHueco) return@mapNotNull null

            OrigenMudo(
                clave = clave,
                canal = canalDeCaptura(deEsteOrigen.first().origen),
                nombre = nombre,
                diasSinCaptura = (silencio / DIA_MS).toInt(),
                ultima = ultima,
            )
        }
        .sortedByDescending { it.diasSinCaptura }
}

/** «los SMS de Bancolombia», «las notificaciones de Nu», «los correos de Bancolombia». */
fun queNoLlega(origen: OrigenMudo): String = when (origen.canal) {
    CanalDeCaptura.SMS -> "los SMS de ${origen.nombre}"
    CanalDeCaptura.NOTIFICACION -> "las notificaciones de ${origen.nombre}"
    CanalDeCaptura.CORREO -> "los correos de ${origen.nombre}"
}

/** «Hace 4 días no llegan los SMS de Bancolombia. Revisa la captura». */
fun textoDeOrigenMudo(origen: OrigenMudo): String =
    "Hace ${origen.diasSinCaptura} días no llegan ${queNoLlega(origen)}. Revisa la captura"
