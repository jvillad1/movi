package com.jvillada.movi.server.compartir

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * # El token de un enlace compartido
 *
 * **Un enlace compartido es una capacidad**: no hay usuario ni contraseña detrás, quien tiene el
 * token ve la plata del dueño. Todo lo de este archivo sale de tomarse eso en serio.
 *
 * - **Impredecible.** 32 bytes (256 bits) de [SecureRandom] —el generador del sistema operativo—,
 *   no un UUID: un UUID v4 trae 122 bits y está pensado para ser *único*, no *secreto*; nada en su
 *   contrato promete que no se pueda adivinar. El pedido era «128 bits o más»; 256 no cuesta nada
 *   y deja el margen del otro lado.
 * - **Corto de escribir en una URL.** Base64 URL-safe sin relleno: 43 caracteres de
 *   `[A-Za-z0-9_-]`, que no hay que escapar en ningún lado.
 * - **Guardado como hash.** La base guarda [hashDelToken], nunca el token (ver `EnlacesCompartidos`).
 * - **Comparado en tiempo constante.** La búsqueda es por el hash, que ya no filtra nada útil por
 *   tiempo; aun así la fila encontrada se vuelve a comparar con [esElMismoHash] antes de servir
 *   nada. No cuesta nada y no hay que volver a pensarlo — mismo criterio que `esElMismoSecreto`
 *   del correo entrante.
 */
object TokenDeEnlace {
    private const val BYTES = 32

    /** 32 bytes en base64 URL-safe sin relleno son siempre 43 caracteres. */
    const val LARGO: Int = 43

    private val azar = SecureRandom()
    private val FORMA = Regex("^[A-Za-z0-9_-]{$LARGO}$")

    fun nuevo(): String {
        val bytes = ByteArray(BYTES).also { azar.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * ¿Tiene la forma de un token nuestro? Lo que no la tiene se descarta **antes** de tocar la
     * base, y con la MISMA respuesta que un token que no existe (ver la ruta pública).
     */
    fun tieneForma(candidato: String): Boolean = FORMA.matches(candidato)

    /** SHA-256 en hex minúscula: lo único del token que se guarda. */
    fun hashDelToken(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun esElMismoHash(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
