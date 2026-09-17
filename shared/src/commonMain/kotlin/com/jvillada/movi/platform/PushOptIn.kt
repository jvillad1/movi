package com.jvillada.movi.platform

/** Opt-in de notificaciones push. Solo la web (wasmJs) lo soporta hoy. */
expect object PushOptIn {
    val supported: Boolean
    fun status(): String   // "enabled" | "disabled" | "denied" | "unsupported"
    fun enable()
    fun disable()

    /**
     * **Soltar la suscripción porque la sesión se está cerrando.**
     *
     * Distinto de [disable], que es el interruptor de Perfil: esto lo llama
     * [com.jvillada.movi.data.SessionManager.clear], o sea también el cierre forzado tras una
     * racha de 401. Hasta que existió, salir de la sesión no soltaba nada: el navegador quedaba
     * suscrito y el servidor con su fila, así que una portátil prestada seguía recibiendo en la
     * pantalla de bloqueo el nombre de la tarjeta y el monto de cada vencimiento del dueño,
     * **sin forma de revocarlo desde la cuenta** — el DELETE del server pide el endpoint, que
     * solo conoce ese navegador. Hoy está latente porque en producción no hay claves VAPID; se
     * arma solo con prenderlas.
     *
     * Tiene que ser lo PRIMERO de `clear()`: la web manda el DELETE con el token guardado, y
     * `clear()` lo borra. Y no puede lanzar nunca — en la web cruza a JS, donde cualquier cosa
     * puede faltar; que un logout no termine sería peor que no soltar la suscripción.
     */
    fun disableForLogout()
}
