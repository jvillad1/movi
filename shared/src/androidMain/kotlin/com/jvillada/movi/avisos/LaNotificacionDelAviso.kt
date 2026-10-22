package com.jvillada.movi.avisos

import com.jvillada.movi.data.apiBaseUrl
import com.jvillada.movi.data.createHttpClient
import com.jvillada.movi.shared.repository.WalletRepositoryImpl
import com.jvillada.movi.ui.porrevisar.LoQueMoviEntendio

/**
 * **Lo que Movi entendió de un aviso recién llegado**, para decidir los botones de su notificación
 * (Ola 5). Contra el server directo ([WalletRepositoryImpl]) por lo mismo que [leerPagosDelPeriodo]:
 * lo llama un Worker que puede correr sin la base local abierta. `null` si el aviso no está. Lanza si
 * una lectura falla; quien llama publica entonces la notificación sin «Confirmar».
 */
suspend fun leerLoQueMoviEntendioParaLaNotificacion(avisoId: String, ahora: Long = System.currentTimeMillis()): LoQueMoviEntendio? {
    val cliente = createHttpClient()
    try {
        return leerLoQueMoviEntendioDelAviso(WalletRepositoryImpl(cliente, apiBaseUrl), avisoId, ahora)?.second
    } finally {
        cliente.close()
    }
}

/**
 * **«Confirmar» desde la notificación** contra el server directo, por el camino de siempre (ver
 * [confirmarDesdeLaNotificacion], donde está por qué no pasa por la huella). Nunca lanza.
 */
suspend fun confirmarElAvisoDesdeLaNotificacion(
    avisoId: String,
    mostro: LoQueSeMostro,
    ahora: Long = System.currentTimeMillis(),
): ResultadoDeConfirmar {
    val cliente = runCatching { createHttpClient() }.getOrElse { return ResultadoDeConfirmar.NoSePudo("sin sesión") }
    try {
        return confirmarDesdeLaNotificacion(WalletRepositoryImpl(cliente, apiBaseUrl), avisoId, mostro, ahora)
    } finally {
        cliente.close()
    }
}
