package com.jvillada.movi.server.observabilidad

/**
 * # Las cubetas de duración (vuelta 102)
 *
 * Un histograma por cubetas fijas, en milisegundos. Es lo que se guarda por minuto en vez de cada
 * duración: dos minutos (o sesenta) se juntan sumando cubeta por cubeta, así que el p95 de una hora o
 * de siete días se calcula **igual de bien** que el de un minuto, sin volver a ver un solo pedido.
 *
 * Los bordes van de 1 ms a 60 s, con pasos de ~1,5×: el percentil que sale es **el borde de arriba de
 * su cubeta**, así que nunca dice menos de lo que fue, y se pasa como mucho al borde siguiente (un
 * pedido de 110 ms cae en la de 150). Para «qué ruta está lenta» alcanza de sobra; para medir
 * microsegundos no sirve, ni hace falta. Lo de más de 60 s cae en la última, que se reporta con el
 * máximo real.
 */
object Cubetas {
    /** El borde de arriba (incluido) de cada cubeta, en ms. La última, sin borde, es «más de 60 s». */
    val BORDES: LongArray = longArrayOf(
        1, 2, 3, 5, 7, 10, 15, 20, 30, 50, 75, 100, 150, 200, 300, 500, 750,
        1_000, 1_500, 2_000, 3_000, 5_000, 7_500, 10_000, 15_000, 30_000, 60_000,
    )

    /** Cuántas cubetas hay: una por borde y la de más de 60 s. */
    val CUANTAS: Int = BORDES.size + 1

    /** En qué cubeta cae una duración de [ms]. */
    fun indiceDe(ms: Long): Int {
        // Búsqueda binaria sobre 27 bordes: unas 5 comparaciones.
        var bajo = 0
        var alto = BORDES.size
        while (bajo < alto) {
            val medio = (bajo + alto) ushr 1
            if (BORDES[medio] < ms) bajo = medio + 1 else alto = medio
        }
        return bajo
    }

    /**
     * El percentil [p] (0 a 1) de un histograma, o `null` si está vacío.
     *
     * Es el borde de arriba de la cubeta donde la cuenta acumulada alcanza `ceil(p · n)`, recortado al
     * [maximo] real: si el más lento fue de 12 ms, el p99 no puede decir 15.
     */
    fun percentil(cubetas: LongArray, p: Double, maximo: Long): Long? {
        val n = cubetas.sum()
        if (n == 0L) return null
        val objetivo = kotlin.math.ceil(p * n).toLong().coerceIn(1L, n)
        var acumulado = 0L
        for (i in cubetas.indices) {
            acumulado += cubetas[i]
            if (acumulado >= objetivo) {
                val borde = if (i < BORDES.size) BORDES[i] else maximo
                return minOf(borde, maximo)
            }
        }
        return maximo
    }

    /** Las cubetas como texto para la base: `"0,3,5,…"`. */
    fun aTexto(cubetas: LongArray): String = cubetas.joinToString(",")

    /** De vuelta desde la base. Un texto roto o de otro largo da cubetas en cero, nunca una excepción. */
    fun deTexto(texto: String?): LongArray {
        val partes = texto?.split(",")?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()
        return if (partes.size == CUANTAS) partes.toLongArray() else LongArray(CUANTAS)
    }

    /** Suma [otra] dentro de [destino], cubeta por cubeta. */
    fun sumar(destino: LongArray, otra: LongArray) {
        for (i in 0 until minOf(destino.size, otra.size)) destino[i] += otra[i]
    }
}
