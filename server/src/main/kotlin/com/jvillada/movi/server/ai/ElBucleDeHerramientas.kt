package com.jvillada.movi.server.ai

/**
 * # El ida y vuelta con el modelo, sin saber nada del SDK
 *
 * Cuando el asistente puede consultar la base, una respuesta deja de ser una llamada: el modelo
 * pide una herramienta, se le contesta, y con eso puede pedir otra o ya responder. Eso es un
 * bucle, y un bucle contra un servicio externo es justo el tipo de código donde un error se
 * descubre en producción.
 *
 * Por eso vive acá, hablando de [RespuestaDelModelo] y no de `MessageCreateParams`: así se puede
 * probar entero —con un modelo de mentira— sin una sola llamada de red. Lo que queda del lado del
 * SDK es el traductor ([ElModeloConHerramientas] y su implementación), que no toma ninguna
 * decisión.
 */

/** Lo que el modelo contestó en una vuelta: o habló, o pidió datos. */
sealed interface RespuestaDelModelo {
    data class Texto(val texto: String) : RespuestaDelModelo
    data class PideHerramientas(val llamadas: List<LlamadaDeHerramienta>) : RespuestaDelModelo
}

/**
 * El modelo, visto por el bucle. La implementación de verdad acumula la conversación adentro (los
 * turnos del asistente con sus bloques originales, que el SDK necesita tal cual); el bucle solo
 * decide **cuándo** preguntar y **qué** contestarle.
 */
interface ElModeloConHerramientas {
    /**
     * Una vuelta más. Con [puedeUsarHerramientas] en `false` el modelo no puede pedir datos y
     * tiene que contestar con lo que ya tiene — así es como el bucle garantiza que termina. La
     * implementación lo hace prohibiéndolas, no quitándolas: ver el KDoc de `LAS_HERRAMIENTAS`.
     */
    suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo

    /** Lo que devolvieron las herramientas, para la próxima vuelta: `id de la llamada` → texto. */
    fun anotarResultados(resultados: List<Pair<String, String>>)
}

/**
 * **Cuántas veces puede consultar antes de tener que contestar.**
 *
 * Tres, y no cuatro como al principio: cada vuelta es una llamada al modelo con todo el prefijo
 * encima. «Cuánto gasté en Comida en agosto y en septiembre» **no** necesita cuatro — son dos
 * consultas, y dos consultas pedidas en el mismo turno cuestan una sola vuelta (el PERSONA se lo
 * pide explícitamente). Lo que la cuarta compraba era una corrección de fecha de vez en cuando; lo
 * que costaba era una llamada más en cada conversación que se enredara.
 */
const val VUELTAS_MAXIMAS = 3

/**
 * **El bucle.** Termina siempre, y termina con texto:
 *
 * - Si el modelo contesta, se devuelve eso.
 * - Si pide herramientas, se ejecutan **todas** las que pidió en esa vuelta y se le contestan
 *   juntas — pedir dos consultas a la vez es una sola vuelta, no dos.
 * - En la última vuelta se le **prohíbe** usarlas (`tool_choice: none`), así que no puede volver a
 *   pedir: o habla o se queda sin decir nada, y para ese caso está el texto de abajo. Se prohíben
 *   en vez de quitarlas porque las herramientas son parte del prefijo que se cachea, y sacarlas
 *   tiraría la caché de la llamada más larga de la conversación.
 *
 * [ejecutar] no lanza: una herramienta que falla le contesta al modelo qué pasó (ver
 * [ejecutarHerramienta]) para que pueda corregir y volver a preguntar. Si igual lanzara, el error
 * viaja como resultado en vez de tumbar la conversación — el dueño prefiere una respuesta
 * incompleta a una pantalla de error.
 */
suspend fun conversarConHerramientas(
    modelo: ElModeloConHerramientas,
    ejecutar: suspend (LlamadaDeHerramienta) -> String,
    vueltasMaximas: Int = VUELTAS_MAXIMAS,
): String {
    repeat(vueltasMaximas) { vuelta ->
        val esLaUltima = vuelta == vueltasMaximas - 1
        when (val respuesta = modelo.siguienteVuelta(puedeUsarHerramientas = !esLaUltima)) {
            is RespuestaDelModelo.Texto -> return respuesta.texto
            is RespuestaDelModelo.PideHerramientas -> {
                if (esLaUltima) return SIN_RESPUESTA
                val resultados = respuesta.llamadas.map { llamada ->
                    llamada.id to runCatching { ejecutar(llamada) }.getOrElse { falla ->
                        "No pude consultar eso: ${falla.message ?: "error desconocido"}"
                    }
                }
                modelo.anotarResultados(resultados)
            }
        }
    }
    return SIN_RESPUESTA
}

/**
 * Lo que se dice cuando el modelo se quedó consultando y nunca contestó. Es un caso que no
 * debería pasar —en la última vuelta no tiene herramientas—, y por eso el texto no promete nada
 * ni culpa al dueño: dice qué pasó y qué hacer.
 */
internal const val SIN_RESPUESTA =
    "Me quedé buscando en tus datos y no alcancé a responder. Pregúntamelo otra vez, " +
        "si puedes con fechas o con un nombre concreto."
