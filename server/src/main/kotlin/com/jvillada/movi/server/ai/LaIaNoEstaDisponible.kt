package com.jvillada.movi.server.ai

import com.anthropic.errors.AnthropicServiceException
import com.jvillada.movi.shared.model.IA_NO_DISPONIBLE
import com.jvillada.movi.shared.model.IA_SIN_CREDITO
import org.slf4j.LoggerFactory

/**
 * # Por qué la API de Anthropic no contestó, cuando la culpa es de la cuenta
 *
 * Una falla de la API que **no es del pedido** sino de la cuenta o del momento: el archivo está bien,
 * la pregunta está bien, y reintentar en un minuto no lo arregla (o sí, pero el dueño no lo sabe).
 * Ver el KDoc de `LaIaNoEstaDisponible.kt` en `:core` para cómo llega a la app.
 *
 * Lo que no está acá —un 400 por un pedido mal armado, un 404 de un modelo, un 500— sigue siendo un
 * error de verdad y sale como siempre: esconderlo detrás de «no está disponible» lo haría invisible.
 */
enum class FallaDeLaIa(val codigo: String) {
    /** Sin saldo: el 400 «Your credit balance is too low…», o un 402 `billing_error`. */
    SIN_CREDITO(IA_SIN_CREDITO),

    /** La clave no sirve (401) o no tiene permiso (403): otro modelo con la misma clave falla igual. */
    SIN_PERMISO(IA_NO_DISPONIBLE),

    /** Límite de uso (429) o la API saturada (529): es del momento, no de la cuenta. */
    SATURADA(IA_NO_DISPONIBLE),
    ;

    /**
     * ¿Probar con otro modelo puede servir? No cuando la falla es de la cuenta: con la misma clave y
     * sin saldo, el respaldo falla igual y la espera se duplica para nada.
     */
    val sirveOtroModelo: Boolean get() = this == SATURADA
}

private val log = LoggerFactory.getLogger("com.jvillada.movi.server.ai.LaIaNoEstaDisponible")

/**
 * ¿[e] es una [FallaDeLaIa]? Mira la cadena de causas: el SDK a veces llega envuelto.
 *
 * El 400 sin saldo es un `invalid_request_error` como cualquier otro 400 —el tipo no lo distingue—,
 * así que para ese caso, y solo para ese, se lee el mensaje: es lo único que la API ofrece.
 */
fun fallaDeLaIa(e: Throwable): FallaDeLaIa? {
    val deLaApi = generateSequence(e) { it.cause }.take(8)
        .filterIsInstance<AnthropicServiceException>().firstOrNull() ?: return null
    return when (deLaApi.statusCode()) {
        402 -> FallaDeLaIa.SIN_CREDITO
        400 -> if (hablaDeSaldo(deLaApi)) FallaDeLaIa.SIN_CREDITO else null
        401, 403 -> FallaDeLaIa.SIN_PERMISO
        429, 529 -> FallaDeLaIa.SATURADA
        else -> null
    }
}

private fun hablaDeSaldo(e: AnthropicServiceException): Boolean {
    val cuerpo = runCatching { e.body().toString() }.getOrDefault("")
    return listOf(e.message.orEmpty(), cuerpo).any { it.contains("credit balance", ignoreCase = true) }
}

/**
 * Corre [bloque] y, si falla por una [FallaDeLaIa], la deja en el log y llama a [alFallar] (que
 * lanza la falla con el status de la ruta). Cualquier otra falla sale tal cual.
 */
internal inline fun <T> conLaIa(donde: String, alFallar: (FallaDeLaIa) -> Nothing, bloque: () -> T): T =
    try {
        bloque()
    } catch (e: Exception) {
        val falla = fallaDeLaIa(e) ?: throw e
        avisarQueLaIaNoEstaDisponible(donde, falla, e)
        alFallar(falla)
    }

/** Una línea en el log por cada vez: es lo que le dice al administrador que hay que recargar. */
fun avisarQueLaIaNoEstaDisponible(donde: String, falla: FallaDeLaIa, e: Throwable) {
    log.warn("IA no disponible en {}: {} ({})", donde, falla.codigo, e.message)
}
