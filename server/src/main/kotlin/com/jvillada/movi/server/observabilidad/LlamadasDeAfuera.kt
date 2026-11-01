package com.jvillada.movi.server.observabilidad

import com.anthropic.client.AnthropicClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.jvillada.movi.server.ai.FallaDeLaIa
import com.jvillada.movi.server.ai.fallaDeLaIa
import kotlin.coroutines.cancellation.CancellationException

/**
 * # Los servicios de afuera, medidos en un solo lugar (vuelta 102)
 *
 * Anthropic, Resend (envío y lectura de recibidos) y la TRM pasan por [medirLlamada]: cuántas
 * llamadas, cuánto tardaron, qué error dieron (sin crédito, 429, 5xx, otro 4xx, sin red) y, de la IA,
 * cuántas fichas. Nada del contenido: ni el prompt, ni el correo, ni la respuesta.
 *
 * Es un envoltorio y no una medición copiada en cada lugar: el que llama dice **qué servicio** y **qué
 * operación** (el modelo, «envio», «lectura»…), y cómo leer el desenlace de lo que devolvió. Una
 * excepción se clasifica sola ([tipoDeErrorDe]) y se vuelve a lanzar tal cual: medir no cambia lo que
 * hace la llamada.
 */
object Servicio {
    const val ANTHROPIC = "anthropic"
    const val RESEND = "resend"
    const val TRM = "trm"
}

/** Cómo salió una llamada que sí devolvió algo: con error (un 4xx/5xx que no lanzó) o con fichas. */
data class Desenlace(val error: TipoDeError? = null, val fichas: Fichas? = null)

/**
 * Corre [bloque] y lo anota en [Metricas] bajo ([servicio], [operacion]). [desenlace] lee el resultado
 * (un status HTTP, las fichas de un `Message`); si lanza, se clasifica la excepción.
 *
 * `inline` para que sirva igual desde código suspendido y desde código bloqueante: la llamada adentro
 * puede suspender si quien llama puede.
 */
inline fun <T> medirLlamada(
    servicio: String,
    operacion: String,
    desenlace: (T) -> Desenlace = { Desenlace() },
    bloque: () -> T,
): T {
    val inicio = System.nanoTime()
    val resultado = try {
        bloque()
    } catch (e: Throwable) {
        // Una cancelación es que el pedido se fue, no que el servicio fallara.
        if (e !is CancellationException) anotarLlamada(servicio, operacion, inicio, Desenlace(error = tipoDeErrorDe(e)))
        throw e
    }
    anotarLlamada(servicio, operacion, inicio, runCatching { desenlace(resultado) }.getOrDefault(Desenlace()))
    return resultado
}

@PublishedApi
internal fun anotarLlamada(servicio: String, operacion: String, inicioNanos: Long, desenlace: Desenlace) {
    val ms = (System.nanoTime() - inicioNanos) / 1_000_000
    Metricas.registrarServicio(servicio, operacion, ms, desenlace.error, desenlace.fichas)
    if (servicio == Servicio.ANTHROPIC) ElPedidoEnCurso.actual()?.usoLaIa = true
}

/** El desenlace de una respuesta HTTP por su status. */
fun desenlaceHttp(status: Int): Desenlace = Desenlace(error = tipoDeErrorDeStatus(status))

fun tipoDeErrorDeStatus(status: Int): TipoDeError? = when {
    status in 200..399 -> null
    status == 429 || status == 529 -> TipoDeError.LIMITE
    status >= 500 -> TipoDeError.SERVIDOR
    else -> TipoDeError.CLIENTE
}

/**
 * La clase de error de una excepción. Para Anthropic usa la misma lectura que ya decide qué decirle al
 * dueño ([fallaDeLaIa]): el 400 «credit balance» es sin crédito aunque su status sea un 400 cualquiera.
 */
fun tipoDeErrorDe(e: Throwable): TipoDeError {
    when (fallaDeLaIa(e)) {
        FallaDeLaIa.SIN_CREDITO -> return TipoDeError.SIN_CREDITO
        FallaDeLaIa.SATURADA -> return TipoDeError.LIMITE
        FallaDeLaIa.SIN_PERMISO -> return TipoDeError.CLIENTE
        null -> Unit
    }
    val causas = generateSequence(e) { it.cause }.take(8).toList()
    causas.filterIsInstance<AnthropicServiceException>().firstOrNull()?.let {
        return tipoDeErrorDeStatus(it.statusCode()) ?: TipoDeError.CLIENTE
    }
    return if (causas.any { it is AnthropicIoException || it is java.io.IOException }) TipoDeError.RED else TipoDeError.CLIENTE
}

/** Las fichas de una respuesta de Anthropic. */
fun fichasDe(m: Message): Fichas = m.usage().let { uso ->
    Fichas(
        entrada = uso.inputTokens() + uso.cacheCreationInputTokens().orElse(0L),
        salida = uso.outputTokens(),
        cache = uso.cacheReadInputTokens().orElse(0L),
    )
}

/**
 * **La llamada a Anthropic, medida.** Todo `client.messages().create(...)` del server pasa por acá: la
 * operación es el modelo que se pidió, y del `Message` salen las fichas.
 */
fun crearMensaje(client: AnthropicClient, params: MessageCreateParams): Message =
    medirLlamada(Servicio.ANTHROPIC, params.model().asString(), { m: Message -> Desenlace(fichas = fichasDe(m)) }) {
        client.messages().create(params)
    }
