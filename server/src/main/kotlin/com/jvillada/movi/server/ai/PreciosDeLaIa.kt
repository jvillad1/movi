package com.jvillada.movi.server.ai

/**
 * # Cuánto cuesta cada ficha, por modelo (vuelta 106)
 *
 * **La única tabla de precios de Movi.** Antes no había ninguna a propósito («escribirlos de memoria
 * sería inventarlos»); ahora salen de la página oficial, copiada el día que se escribió esto:
 *
 * - Fuente: https://platform.claude.com/docs/en/about-claude/pricing
 * - Fecha: 2026-11-02 (la copia está en el brief de la vuelta 106).
 *
 * Precios en **dólares por millón de fichas** (MTok), que es lo mismo que **microdólares por ficha**:
 * multiplicar fichas × precio da microdólares sin más cuentas. La escritura de caché es la de 5
 * minutos (1,25× la entrada): es la única que usa Movi (`CacheControlEphemeral` sin `ttl`).
 *
 * Si Anthropic cambia un precio, se cambia acá y en ningún otro lado. Un modelo que no esté en la
 * tabla no tiene costo ([precioDe] da `null`): el panel lo dice aparte en vez de contarlo como cero
 * en silencio.
 */
data class PrecioDelModelo(
    /** Fichas de entrada que no vienen de la caché. */
    val entrada: Double,
    /** Fichas que se escriben en la caché de 5 minutos. */
    val escrituraDeCache: Double,
    /** Fichas que se leen de la caché (un acierto, o un refresco). */
    val lecturaDeCache: Double,
    val salida: Double,
    /**
     * El otro tramo, para los prompts de más de [FICHAS_DEL_TRAMO_LARGO] fichas (solo Haiku 5.5 lo
     * tiene: $0,50 / $2,50 en vez de $0,10 / $0,50). `null` = el mismo precio a cualquier largo.
     */
    val largo: PrecioDelModelo? = null,
)

object PreciosDeLaIa {
    const val FUENTE = "https://platform.claude.com/docs/en/about-claude/pricing"
    const val FECHA = "2026-11-02"

    /** Un prompt de más de esto (entrada + caché escrita + caché leída) va al tramo largo de Haiku 5.5. */
    const val FICHAS_DEL_TRAMO_LARGO = 100_000L

    /**
     * Por id de modelo, **sin fecha**: `claude-haiku-4-5-20251001` encuentra `claude-haiku-4-5` por
     * prefijo (el más largo gana, así `claude-sonnet-5-5` no cae en `claude-sonnet-5`).
     */
    val TABLA: Map<String, PrecioDelModelo> = mapOf(
        // El chat de todos los días y la clasificación de papeles (vuelta 106).
        "claude-haiku-5-5" to PrecioDelModelo(
            entrada = 0.10, escrituraDeCache = 0.125, lecturaDeCache = 0.01, salida = 0.50,
            largo = PrecioDelModelo(entrada = 0.50, escrituraDeCache = 0.625, lecturaDeCache = 0.05, salida = 2.50),
        ),
        // Los extractos (`MODELO_DE_EXTRACTOS`) y, si hiciera falta, el criterio. La lectura de
        // caché de Sonnet 5.5 es 0,05× la entrada, no 0,1×.
        "claude-sonnet-5-5" to PrecioDelModelo(entrada = 2.0, escrituraDeCache = 2.50, lecturaDeCache = 0.10, salida = 10.0),
        // Lo que había antes de la vuelta 106: los turnos viejos de `ai_turns` se cobran con esto.
        "claude-haiku-4-5" to PrecioDelModelo(entrada = 1.0, escrituraDeCache = 1.25, lecturaDeCache = 0.10, salida = 5.0),
        "claude-sonnet-5" to PrecioDelModelo(entrada = 2.0, escrituraDeCache = 2.50, lecturaDeCache = 0.20, salida = 10.0),
        // El respaldo (`MODELO_DE_RESPALDO`), que solo contesta si el primero falla.
        "claude-opus-4-7" to PrecioDelModelo(entrada = 5.0, escrituraDeCache = 6.25, lecturaDeCache = 0.50, salida = 25.0),
    )

    /** El precio de [modelo], o `null` si no está en la tabla. */
    fun precioDe(modelo: String): PrecioDelModelo? =
        TABLA.entries.filter { modelo == it.key || modelo.startsWith(it.key + "-") }.maxByOrNull { it.key.length }?.value

    /**
     * **Lo que costó una llamada**, en microdólares, o `null` si el modelo no tiene precio.
     *
     * [entrada] son las fichas de entrada que **no** vienen de la caché (el `input_tokens` de la
     * API); [escritas] y [leidas], las de la caché. El tramo se decide por el prompt entero, que es
     * la suma de las tres.
     */
    fun costoDeUnaLlamada(modelo: String, entrada: Long, escritas: Long, leidas: Long, salida: Long): Double? {
        val base = precioDe(modelo) ?: return null
        val prompt = entrada + escritas + leidas
        val p = if (prompt > FICHAS_DEL_TRAMO_LARGO) base.largo ?: base else base
        return entrada * p.entrada + escritas * p.escrituraDeCache + leidas * p.lecturaDeCache + salida * p.salida
    }

    /**
     * **Lo que costó un turno del chat** (una fila de `ai_turns`), en microdólares. Un turno son
     * [llamadas] llamadas y la tabla guarda solo la suma de sus fichas, así que el tramo se decide
     * con el prompt **promedio** por llamada: con prompts de ~10.000 fichas no cambia nada, y si un
     * día una conversación pasara de 100.000 por llamada, el promedio lo ve.
     */
    fun costoDeUnTurno(modelo: String, entrada: Long, escritas: Long, leidas: Long, salida: Long, llamadas: Int): Double? {
        val base = precioDe(modelo) ?: return null
        val n = llamadas.coerceAtLeast(1)
        val promptPorLlamada = (entrada + escritas + leidas) / n
        val p = if (promptPorLlamada > FICHAS_DEL_TRAMO_LARGO) base.largo ?: base else base
        return entrada * p.entrada + escritas * p.escrituraDeCache + leidas * p.lecturaDeCache + salida * p.salida
    }
}

/** Microdólares → dólares. */
fun microdolaresADolares(micro: Double): Double = micro / 1_000_000.0
