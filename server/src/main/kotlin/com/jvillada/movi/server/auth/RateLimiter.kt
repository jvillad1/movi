package com.jvillada.movi.server.auth

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory per-key sliding-window rate limiter.
 * Thread-safe; no external dependencies.
 *
 * Each call to [allow] atomically prunes timestamps older than [windowMs],
 * checks the count, and — if under the limit — records the current timestamp.
 *
 * **Sobre las claves.** Quien llama arma la clave. Desde que hay baldes por correo
 * (`login:email:…`), parte de la clave viene del cuerpo de la petición, o sea de afuera: sin
 * limpieza, el mapa crecería una entrada por dirección jamás vista. Por eso [allow] barre cada
 * tanto las claves que ya no tienen intentos vivos ([RETENTION_MS]). El crecimiento igual está
 * acotado por los baldes globales, pero no hace falta apostar a eso.
 */
object RateLimiter {

    private val attempts = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Una entrada sin ningún intento más nuevo que esto ya no puede cambiar ninguna decisión.
     * Tiene que ser >= la ventana más larga en uso (hoy 15 min en las rutas de reset); una hora
     * deja margen de sobra para que nadie tenga que acordarse de tocar esto al agregar una.
     */
    private const val RETENTION_MS = 60 * 60_000L

    /** Cada cuántas llamadas se barre. Barato: el barrido es O(claves) y no bloquea el mapa. */
    private const val SWEEP_EVERY = 512
    private val callsSinceSweep = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * **Tope del largo de la clave.** El mapa retiene cada clave hasta una hora ([RETENTION_MS]),
     * y desde que hay baldes por correo parte de la clave viene del cuerpo de la petición. Sin
     * este corte, un `email` de megabytes POSTeado sin autenticar a `/api/auth/login` se guardaba
     * entero, una vez por valor distinto: unos pocos pedidos y el proceso —el mismo por el que el
     * teléfono sincroniza— se queda sin memoria. El barrido no ayuda, porque durante esa hora las
     * claves están vivas.
     *
     * 320 es holgado para cualquier clave legítima (el correo más largo que las rutas aceptan son
     * 255 caracteres, y los otros baldes usan un id o una IP). Las rutas además rechazan el correo
     * largo con un 400 antes de llegar acá; esto es el corte estructural, el que cubre a cualquier
     * llamador futuro que arme una clave con algo de afuera y se olvide de validarlo.
     *
     * Lo que se paga: dos claves que solo se diferencien después del carácter 320 comparten balde.
     * Es imposible por accidente con los prefijos que se usan hoy, y compartir de más nunca afloja
     * un límite.
     */
    private const val MAX_KEY_LENGTH = 320

    /**
     * Returns `true` if the caller identified by [key] is allowed to proceed
     * (fewer than [maxAttempts] calls in the last [windowMs] milliseconds).
     * Returns `false` when the limit is exceeded — the attempt is NOT recorded.
     */
    fun allow(key: String, maxAttempts: Int, windowMs: Long): Boolean {
        // Lo que se guarda es siempre la clave recortada — ver [MAX_KEY_LENGTH].
        val clave = key.take(MAX_KEY_LENGTH)
        val now = System.currentTimeMillis()
        val cutoff = now - windowMs

        if (callsSinceSweep.incrementAndGet() >= SWEEP_EVERY) {
            callsSinceSweep.set(0)
            sweep(now)
        }

        // getOrPut is not atomic across threads, but the synchronized block below
        // ensures correctness for the list itself.
        val list = attempts.getOrPut(clave) { mutableListOf() }

        synchronized(list) {
            list.removeAll { it < cutoff }
            if (list.size >= maxAttempts) return false
            list.add(now)
        }
        return true
    }

    /**
     * Saca las claves sin intentos vivos. Carrera aceptada: si otro hilo tiene la lista de una
     * clave recién sacada y le agrega un intento, ese intento se pierde — o sea, alguien que
     * estuvo una hora sin aparecer consigue un intento de más. Irrelevante frente a límites de
     * decenas por ventana.
     */
    private fun sweep(now: Long) {
        val cutoff = now - RETENTION_MS
        attempts.entries.removeIf { (_, list) ->
            synchronized(list) { list.none { it >= cutoff } }
        }
    }

    /** Clears all state — useful in tests. */
    fun reset() {
        attempts.clear()
        callsSinceSweep.set(0)
    }
}
