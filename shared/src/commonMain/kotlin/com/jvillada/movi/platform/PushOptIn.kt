package com.jvillada.movi.platform

/** Opt-in de notificaciones push. Solo la web (wasmJs) lo soporta hoy. */
expect object PushOptIn {
    val supported: Boolean

    /**
     * **Safari en un iPhone, en una pestaña común.** Apple solo deja recibir avisos web a la app
     * agregada a la pantalla de inicio (iOS 16.4+): en una pestaña no existe `PushManager`, así
     * que [supported] da `false` y el interruptor desaparecía sin decir por qué. Con esto la
     * pantalla puede explicar el paso que falta en vez de callar. `false` en todo lo demás.
     */
    val necesitaInstalar: Boolean
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
     * solo conoce ese navegador. Producción **sí** tiene claves VAPID (desde el 24-jul-2026,
     * `/api/push/vapid-key` contesta 200), así que esto corre de verdad en cada navegador que
     * activó las notificaciones.
     *
     * Tiene que ser lo PRIMERO de `clear()`: la web manda el DELETE con el token guardado, y
     * `clear()` lo borra. Y no puede lanzar nunca — en la web cruza a JS, donde cualquier cosa
     * puede faltar; que un logout no termine sería peor que no soltar la suscripción.
     */
    fun disableForLogout()
}
