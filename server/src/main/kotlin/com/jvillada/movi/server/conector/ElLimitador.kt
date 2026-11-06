package com.jvillada.movi.server.conector

/**
 * # Vuelta 135 (N1 de la re-revisión) · El limitador del conector, con la memoria acotada
 *
 * El `RateLimiter` de la app guarda cada clave una hora y sin tope de cantidad: está bien detrás de un balde por IP,
 * pero el conector recibe claves de afuera (un `client_id`, una IP de un `/64`). Este limitador es del conector y
 * **nunca guarda más de [maxLlaves] claves**: al pasarse, desaloja la que se usó hace más tiempo (LRU). Desalojar el
 * balde de alguien solo puede dejarle pasar unos pedidos de más, y delante siempre está el balde por IP.
 */
internal class LimitadorAcotado(private val maxLlaves: Int) {
    private val baldes = object : LinkedHashMap<String, ArrayDeque<Long>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayDeque<Long>>?): Boolean = size > maxLlaves
    }

    /** `true` si [clave] tiene menos de [maximo] pedidos en los últimos [ventanaMs] (y anota este). */
    @Synchronized
    fun permitir(clave: String, maximo: Int, ventanaMs: Long, ahora: Long = System.currentTimeMillis()): Boolean {
        val balde = baldes.getOrPut(clave.take(200)) { ArrayDeque() }
        while (balde.isNotEmpty() && balde.first() < ahora - ventanaMs) balde.removeFirst()
        if (balde.size >= maximo) return false
        balde.addLast(ahora)
        return true
    }

    @Synchronized
    fun llaves(): Int = baldes.size

    @Synchronized
    fun llavesQueEmpiezanCon(prefijo: String): Int = baldes.keys.count { it.startsWith(prefijo) }

    @Synchronized
    fun olvidar() = baldes.clear()
}

/** Diez mil claves: unos pocos MB como mucho, pase lo que pase. */
internal const val MAX_LLAVES_DEL_LIMITADOR = 10_000

internal val limitadorDelConector = LimitadorAcotado(MAX_LLAVES_DEL_LIMITADOR)

/**
 * **La clave de una IP para un balde**: la dirección tal cual en IPv4 y su `/64` en IPv6 (un host con IPv6 tiene
 * 2⁶⁴ direcciones; contarlas una por una no frena a nadie). Solo se mira una IP literal: nunca se resuelve un nombre.
 */
internal fun claveDeIp(ip: String): String {
    val limpia = ip.trim().removePrefix("[").substringBefore(']')
    if (':' !in limpia || !limpia.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }) return limpia
    val bytes = runCatching { java.net.InetAddress.getByName(limpia).address }.getOrNull() ?: return limpia
    if (bytes.size != 16) return limpia
    return (0 until 8 step 2).joinToString(":") { i -> "%x".format(((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)) } + "::/64"
}
