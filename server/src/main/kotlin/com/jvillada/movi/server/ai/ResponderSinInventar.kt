package com.jvillada.movi.server.ai

/**
 * # Responder sin inventar: el bucle de siempre, más una revisión de las cifras
 *
 * [conversarConHerramientas] sigue decidiendo cuándo consultar y cuándo contestar. Esto le agrega
 * una sola cosa al final: la respuesta pasa por [cifrasSinRespaldo] contra todo lo que el modelo
 * tenía delante —las [fuentes] fijas del turno más lo que devolvieron las herramientas—.
 *
 * - **Caso normal (todo respaldado): no pasa nada más.** Ni una llamada extra. Es la condición del
 *   dueño —«lo más económico posible»— y la prueba `el caso normal hace UNA sola llamada` la fija.
 * - **Cifras sin respaldo: UN reintento**, al mismo modelo, con un mensaje que las nombra y le pide
 *   reescribir con cifras de los datos o decir que no las sabe. Sin herramientas (`tool_choice:
 *   none`, que no toca el prefijo cacheado): es una llamada, no otra conversación.
 * - **Si el reintento sigue con cifras sin respaldo**, se entrega igual —la segunda, que es la que
 *   ya fue advertida— con una línea honesta al final que las nombra. No se borra nada a ciegas: un
 *   recorte automático puede dejar una frase que diga lo contrario de lo que decía.
 *
 * Y todo queda contado ([RespuestaVerificada.corregidas] y [RespuestaVerificada.sinRespaldo] van a
 * `ai_turns`), que es lo que vuelve medible el «sin inventar»: cuántas respuestas tuvieron que
 * corregirse, y cuántas llegaron al dueño con una cifra que Movi no pudo respaldar.
 */

/** Un modelo al que, además, se le puede pedir que corrija su última respuesta. */
interface ElModeloQueSeCorrige : ElModeloConHerramientas {
    /**
     * Deja anotada la última respuesta como turno del asistente y [correccion] como turno del
     * usuario, para que la próxima [siguienteVuelta] la reescriba.
     */
    fun anotarCorreccion(correccion: String)
}

/** Lo que salió del turno, ya revisado. */
data class RespuestaVerificada(
    /** Lo que se le muestra al dueño (con la línea honesta si hizo falta). */
    val texto: String,
    val consultas: List<ConsultaHecha>,
    /** Las cifras sin respaldo de la PRIMERA respuesta, las que dispararon el reintento. Vacío = no hubo. */
    val corregidas: List<String> = emptyList(),
    /** Las que siguieron sin respaldo después del reintento: las que el dueño vio marcadas. */
    val sinRespaldo: List<String> = emptyList(),
) {
    val huboReintento: Boolean get() = corregidas.isNotEmpty()
}

/**
 * El turno completo. [verificar] en `false` saltea la revisión: es para cuando el dueño manda una
 * foto, porque los montos de un recibo o de una oferta del banco salen de la imagen —que el
 * verificador no puede leer— y marcarlos sería reintentar, y ensuciar, justo las respuestas que
 * están bien.
 */
suspend fun responderSinInventar(
    modelo: ElModeloQueSeCorrige,
    ejecutar: suspend (LlamadaDeHerramienta) -> String,
    fuentes: List<String>,
    trampas: Map<Long, String> = emptyMap(),
    verificar: Boolean = true,
    vueltasMaximas: Int = VUELTAS_MAXIMAS,
): RespuestaVerificada {
    val paso = conversarConHerramientas(modelo, ejecutar, vueltasMaximas)
    if (!verificar) return RespuestaVerificada(paso.texto, paso.consultas)

    val todas = fuentes + paso.consultas.map { it.devolvio }
    val primera = cifrasSinRespaldo(paso.texto, todas, trampas)
    if (primera.isEmpty()) return RespuestaVerificada(paso.texto, paso.consultas)

    modelo.anotarCorreccion(mensajeDeCorreccion(primera))
    val segunda = (modelo.siguienteVuelta(puedeUsarHerramientas = false) as? RespuestaDelModelo.Texto)
        ?.texto
        // Si el reintento vino vacío (o, contra toda regla, pidió herramientas), la primera
        // respuesta con su advertencia es mejor que «no alcancé a terminar».
        ?.takeUnless { it.isBlank() || it == NO_ALCANCE_A_TERMINAR }
    val final = segunda ?: paso.texto
    val quedan = if (segunda == null) primera else cifrasSinRespaldo(segunda, todas, trampas)
    return RespuestaVerificada(
        texto = conLineaHonesta(final, quedan),
        consultas = paso.consultas,
        corregidas = primera,
        sinRespaldo = quedan,
    )
}
