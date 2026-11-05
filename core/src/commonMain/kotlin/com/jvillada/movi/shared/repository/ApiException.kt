package com.jvillada.movi.shared.repository

/**
 * Respuesta HTTP de error del server, con su cuerpo intacto.
 *
 * Sin esto, un `.body<T>()` sobre un 400 explota deserializando y el mensaje resultante no
 * trae ni el código ni el texto del server: el usuario terminaba viendo "Algo salió mal"
 * justo en los casos donde el server sí había explicado qué pasó.
 */
class ApiException(
    val status: Int,
    val serverMessage: String? = null,
    /**
     * Revisión 2 de la vuelta 131 · el código del rechazo, de la cabecera `X-Movi-Codigo`
     * ([com.jvillada.movi.shared.model.CABECERA_DEL_CODIGO]): el cuerpo es la frase para cualquier cliente
     * y el código es lo que lee el cliente nuevo. `null` si el server no lo mandó.
     */
    val codigo: String? = null,
) : Exception(
    buildString {
        append("HTTP ")
        append(status)
        serverMessage?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
    },
)
