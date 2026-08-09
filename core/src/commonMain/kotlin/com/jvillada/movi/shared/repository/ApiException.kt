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
) : Exception(
    buildString {
        append("HTTP ")
        append(status)
        serverMessage?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
    },
)
