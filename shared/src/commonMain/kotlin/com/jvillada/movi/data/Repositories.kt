package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.WalletRepository

object Repositories {
    /**
     * Envuelto en [InvalidaElInicioAlEscribir]: cualquier escritura marca la caché del Inicio como
     * vieja, así el TTL de esa pantalla no puede esconder plata que acaba de cambiar.
     */
    val wallets: WalletRepository by lazy { InvalidaElInicioAlEscribir(createRepository()) }
}
