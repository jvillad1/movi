package com.jvillada.movi.shared.model

/**
 * Vuelta 129 · **la huella SHA-256 de unos bytes**, en hexadecimal y minúsculas: la misma que calcula
 * el server (`huellaDelPapel`, con `MessageDigest`) para reconocer «el mismo archivo».
 *
 * La hoja de subida de Documentos la calcula sobre el archivo elegido, antes de subirlo, para
 * preguntarle al server si ya lo leyó (`POST /api/documents/lo-que-se-sabe`): así propone la cuenta
 * con lo que dice el contenido, sin mandar el archivo dos veces. Escrita a mano porque `:core` no tiene
 * una librería de criptografía común a Android, iOS y la web; la vigila `HuellaDeLosBytesTest` contra
 * `MessageDigest`.
 */
fun huellaSha256(bytes: ByteArray): String {
    val sha = Sha256(bytes)
    for (bloque in 0 until sha.bloques) sha.procesar(bloque)
    return sha.hex()
}

/**
 * Vuelta 129 · **la misma huella sin trabar la pantalla**: cede el hilo (`yield`) cada [cadaBloques]
 * bloques de 64 bytes (por defecto, cada 64 KB). En la web todo corre en un solo hilo, y un PDF de
 * 10 MB calculado de un tirón congelaba la hoja de subida.
 */
suspend fun huellaSha256SinTrabar(bytes: ByteArray, cadaBloques: Int = 1024): String {
    val sha = Sha256(bytes)
    for (bloque in 0 until sha.bloques) {
        sha.procesar(bloque)
        if ((bloque + 1) % cadaBloques == 0) kotlinx.coroutines.yield()
    }
    return sha.hex()
}

/** El estado de una huella SHA-256 (FIPS 180-4), de a un bloque de 64 bytes. */
private class Sha256(bytes: ByteArray) {
    private val h = intArrayOf(
        0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
        0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19,
    )
    private val mensaje: ByteArray
    private val w = IntArray(64)
    val bloques: Int

    init {
        val largoEnBits = bytes.size.toLong() * 8
        val relleno = ((56 - (bytes.size + 1) % 64) + 64) % 64
        mensaje = ByteArray(bytes.size + 1 + relleno + 8)
        bytes.copyInto(mensaje)
        mensaje[bytes.size] = 0x80.toByte()
        for (i in 0 until 8) mensaje[mensaje.size - 1 - i] = (largoEnBits ushr (8 * i)).toByte()
        bloques = mensaje.size / 64
    }

    fun procesar(bloque: Int) {
        val base = bloque * 64
        for (t in 0 until 16) {
            val j = base + t * 4
            w[t] = ((mensaje[j].toInt() and 0xff) shl 24) or ((mensaje[j + 1].toInt() and 0xff) shl 16) or
                ((mensaje[j + 2].toInt() and 0xff) shl 8) or (mensaje[j + 3].toInt() and 0xff)
        }
        for (t in 16 until 64) {
            val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (t in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + K[t] + w[t]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }

    fun hex(): String = buildString(64) {
        for (v in h) for (i in 3 downTo 0) {
            val byte = (v ushr (8 * i)) and 0xff
            append(HEX[byte ushr 4]); append(HEX[byte and 0x0f])
        }
    }
}

private const val HEX = "0123456789abcdef"

private val K = intArrayOf(
    0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
    -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
    -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
    -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
)
