package com.jvillada.movi.platform

/** Opt-in de notificaciones push. Solo la web (wasmJs) lo soporta hoy. */
expect object PushOptIn {
    val supported: Boolean
    fun status(): String   // "enabled" | "disabled" | "denied" | "unsupported"
    fun enable()
    fun disable()
}
