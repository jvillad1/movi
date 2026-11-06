package com.jvillada.movi.server.conector

/**
 * # Vuelta 135 (N1 de la re-revisión) · El limitador del conector, con la memoria acotada
 *
 * El `RateLimiter` de la app guarda cada clave una hora y sin tope de cantidad: está bien detrás de un balde por IP,
 * pero el conector recibe claves de afuera (un `client_id`, una IP de un `/64`). Este limitador es del conector y
 * **nunca guarda más de [maxLlaves] claves** (del orden de 25–30 MB en el peor caso): al pasarse, desaloja la que se
 * usó hace más tiempo (LRU). Desalojar el
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

    /** ¿[clave] ya tiene [maximo] pedidos en los últimos [ventanaMs]? Sin anotar nada (ver [anotar]). */
    @Synchronized
    fun lleno(clave: String, maximo: Int, ventanaMs: Long, ahora: Long = System.currentTimeMillis()): Boolean {
        val balde = baldes[clave.take(200)] ?: return false
        while (balde.isNotEmpty() && balde.first() < ahora - ventanaMs) balde.removeFirst()
        return balde.size >= maximo
    }

    /** Anota un pedido en [clave] sin mirar el tope: para un balde que solo cuenta algunos pedidos (los 401). */
    @Synchronized
    fun anotar(clave: String, ahora: Long = System.currentTimeMillis()) {
        baldes.getOrPut(clave.take(200)) { ArrayDeque() }.addLast(ahora)
    }

    @Synchronized
    fun llaves(): Int = baldes.size

    @Synchronized
    fun llavesQueEmpiezanCon(prefijo: String): Int = baldes.keys.count { it.startsWith(prefijo) }

    @Synchronized
    fun olvidar() = baldes.clear()
}

/**
 * Diez mil claves. El peor caso son 10.000 claves con hasta 1.200 marcas cada una (el tope más alto, el de las IP de
 * Anthropic) —unas 120 por clave en la práctica—: del orden de 25–30 MB. Acotado, pase lo que pase.
 */
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

/**
 * **Las IP de salida de Anthropic** (claude.com/docs/connectors/building/authentication, «Network reference»):
 * de ahí llegan `/oauth/token`, `/oauth/revoke` y `/oauth/register` de TODOS los usuarios de Claude, así que esas IP
 * tienen un tope más alto ([LIMITE_DE_TOKEN_DE_ANTHROPIC], [LIMITE_DE_REGISTRO_DE_ANTHROPIC]) y un 429 a una de ellas
 * deja un WARN. No una exención total: si Claude reintentara en bucle, el balde es lo único que cuida la base.
 * `CONECTOR_RANGOS_DE_ANTHROPIC` (CIDR separados por comas) agrega otros, por ejemplo uno de IPv6 si Anthropic lo
 * publica.
 */
internal val RANGOS_DE_ANTHROPIC: List<String> = listOf("160.79.104.0/21")

internal const val LIMITE_DE_TOKEN_DE_ANTHROPIC = 1_200
internal const val LIMITE_DE_REGISTRO_DE_ANTHROPIC = 300

private val rangosDeAnthropic: List<Pair<ByteArray, Int>> by lazy {
    (RANGOS_DE_ANTHROPIC + System.getenv("CONECTOR_RANGOS_DE_ANTHROPIC").orEmpty().split(',').map { it.trim() })
        .mapNotNull { cidr ->
            val red = ipLiteral(cidr.substringBefore('/')) ?: return@mapNotNull null
            val bits = cidr.substringAfter('/', "").toIntOrNull()?.takeIf { it in 0..red.size * 8 } ?: return@mapNotNull null
            red to bits
        }
}

/** Los bytes de una IP literal, o `null` si no lo es. Nunca resuelve un nombre. */
private fun ipLiteral(ip: String): ByteArray? {
    val limpia = ip.trim().removePrefix("[").substringBefore(']')
    if (limpia.isEmpty() || !limpia.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }) return null
    if (':' !in limpia && limpia.count { it == '.' } != 3) return null
    return runCatching { java.net.InetAddress.getByName(limpia).address }.getOrNull()
}

/** ¿[ip] está en uno de los rangos de Anthropic? */
internal fun esIpDeAnthropic(ip: String): Boolean {
    val b = ipLiteral(ip) ?: return false
    return rangosDeAnthropic.any { (red, bits) ->
        red.size == b.size && (0 until bits).all { i ->
            val mascara = 0x80 ushr (i % 8)
            (red[i / 8].toInt() and mascara) == (b[i / 8].toInt() and mascara)
        }
    }
}
