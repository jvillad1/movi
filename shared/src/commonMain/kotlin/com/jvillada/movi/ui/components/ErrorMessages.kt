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
    // Si hay código, manda el código — no el texto.
    //
    // Abajo se clasifica buscando subcadenas dentro de `message`, que para un [ApiException] es
    // "HTTP <código>: <cuerpo>". O sea que **el cuerpo entra en la búsqueda**: el HTML de error de
    // un proxy que en alguna parte diga "Not Found" hacía que un 500 se leyera «Recurso no
    // encontrado.». Teniendo el número al lado no hay por qué adivinarlo leyendo prosa ajena.
    //
    // Las subcadenas siguen mandando para todo lo demás, que es de donde salen: excepciones de red
    // de la plataforma, sin código HTTP ninguno.
    if (this is ApiException) {
        return when {
            status == 401 -> "Sesión expirada. Inicia sesión de nuevo."
            status == 403 -> "No tienes permiso para hacer esto."
            status == 404 -> "Recurso no encontrado."
            status == 429 -> "Demasiados intentos. Espera unos minutos."
            status >= 500 -> "Error en el servidor. Intenta en unos minutos."
            else -> "Algo salió mal. Intenta de nuevo."
        }
    }
    val msg = message ?: ""
    return when {
        msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("No address associated", ignoreCase = true) ||
        msg.contains("UnknownHostException", ignoreCase = true)
            -> "Sin conexión. Verifica tu internet e intenta de nuevo."

        msg.contains("Connection refused", ignoreCase = true) ||
        msg.contains("ConnectException", ignoreCase = true) ||
        msg.contains("Failed to connect", ignoreCase = true) ||
        // Lo que dice el navegador cuando `fetch` no llega a ningún lado (wasm). Sin estos, un
        // servidor caído en la web caía en «Algo salió mal», que en la pantalla de entrada era
        // justo lo que dejaba al dueño sospechando de su contraseña. Chrome dice "Failed to
        // fetch", Safari "Load failed", Firefox "NetworkError when attempting to fetch"; Ktor/JS
        // los reenvía como "Fail to fetch".
        msg.contains("Fail to fetch", ignoreCase = true) ||
        msg.contains("Failed to fetch", ignoreCase = true) ||
        msg.contains("NetworkError", ignoreCase = true) ||
        msg.contains("Load failed", ignoreCase = true)
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
