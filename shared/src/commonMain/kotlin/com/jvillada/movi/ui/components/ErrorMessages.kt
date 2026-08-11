package com.jvillada.movi.ui.components

import com.jvillada.movi.shared.repository.ApiException

fun Throwable.toUserMessage(): String {
    // Cuando el server explicó el rechazo en el cuerpo, eso gana: es más específico que
    // cualquier cosa que se pueda adivinar del código. Antes se perdía —`.body()` explotaba
    // deserializando un cuerpo de texto— y el usuario leía "Algo salió mal" justo en los casos
    // donde había una razón concreta que leer.
    // Solo 4xx de validación: ahí el cuerpo lo escribió una ruta nuestra, a mano y en español.
    // Un 5xx no — el server no tiene StatusPages, así que el cuerpo de un 500 es un stack trace
    // (en developmentMode) o el HTML de error del proxy de Railway, y ninguno de los dos mejora
    // en nada por recortarlo a 200 caracteres frente al usuario.
    if (this is ApiException && status in 400..422) {
        serverMessage?.takeIf { it.isNotBlank() && it.length <= 200 }?.let { return it }
    }
    val msg = message ?: ""
    return when {
        msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("No address associated", ignoreCase = true) ||
        msg.contains("UnknownHostException", ignoreCase = true)
            -> "Sin conexión. Verifica tu internet e intenta de nuevo."

        msg.contains("Connection refused", ignoreCase = true) ||
        msg.contains("ConnectException", ignoreCase = true) ||
        msg.contains("Failed to connect", ignoreCase = true)
            -> "No se pudo conectar al servidor. Intenta más tarde."

        msg.contains("timeout", ignoreCase = true) ||
        msg.contains("SocketTimeoutException", ignoreCase = true)
            -> "La conexión tardó demasiado. Intenta de nuevo."

        msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true)
            -> "Sesión expirada. Inicia sesión de nuevo."

        msg.contains("403") || msg.contains("Forbidden", ignoreCase = true)
            -> "No tienes permiso para hacer esto."

        msg.contains("404") || msg.contains("Not Found", ignoreCase = true)
            -> "Recurso no encontrado."

        msg.contains("5") && (msg.contains("500") || msg.contains("502") ||
        msg.contains("503") || msg.contains("504"))
            -> "Error en el servidor. Intenta en unos minutos."

        else -> "Algo salió mal. Intenta de nuevo."
    }
}
