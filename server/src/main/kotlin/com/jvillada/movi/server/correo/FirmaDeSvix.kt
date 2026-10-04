package com.jvillada.movi.server.correo

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * # La firma de los webhooks de Resend (Svix)
 *
 * Resend firma cada webhook con Svix (https://resend.com/docs/webhooks/verify-webhooks-requests).
 * No hay biblioteca de Svix en el server y no hace falta una: la verificación son diez líneas de
 * HMAC, tal como las describe https://docs.svix.com/receiving/verifying-payloads/how-manual:
 *
 * 1. Tres cabeceras: `svix-id` (el id del mensaje, el mismo en cada reintento), `svix-timestamp`
 *    (segundos desde el epoch) y `svix-signature` (una lista separada por espacios de `v1,<base64>`).
 * 2. Lo firmado es `"<svix-id>.<svix-timestamp>.<cuerpo crudo>"`. **El cuerpo crudo, byte por byte**:
 *    parsearlo y volverlo a serializar cambia la firma.
 * 3. La clave es el secreto `whsec_…` **sin el prefijo y decodificado de base64**.
 * 4. HMAC-SHA256, en base64; vale si coincide con CUALQUIERA de las firmas `v1` de la lista (Svix
 *    manda varias mientras se rota el secreto). Comparación de tiempo constante.
 * 5. El timestamp tiene que estar a menos de [TOLERANCIA_SEGUNDOS] del reloj del server, para que
 *    un webhook interceptado no se pueda reenviar mañana. Cinco minutos es la tolerancia que usan
 *    las bibliotecas oficiales de Svix.
 *
 * Las cabeceras con prefijo `webhook-` son el mismo esquema con otro nombre (el «marca blanca» de
 * Svix); la ruta acepta las dos.
 */
object FirmaDeSvix {

    const val TOLERANCIA_SEGUNDOS: Long = 5 * 60

    /**
     * ¿La petición viene de Resend? `false` ante cualquier cosa que falte o no cuadre — nunca lanza.
     *
     * @param ahoraSegundos el reloj del server, inyectable para las pruebas.
     */
    fun esValida(
        secreto: String,
        id: String?,
        timestamp: String?,
        firmas: String?,
        cuerpo: ByteArray,
        ahoraSegundos: Long,
    ): Boolean {
        if (id.isNullOrBlank() || timestamp.isNullOrBlank() || firmas.isNullOrBlank()) return false
        val segundos = timestamp.trim().toLongOrNull() ?: return false
        if (abs(ahoraSegundos - segundos) > TOLERANCIA_SEGUNDOS) return false
        val clave = claveDe(secreto) ?: return false

        val esperada = runCatching {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(clave, "HmacSHA256"))
            mac.update("${id.trim()}.${timestamp.trim()}.".toByteArray(Charsets.UTF_8))
            mac.doFinal(cuerpo)
        }.getOrNull() ?: return false

        return firmas.trim().split(' ').filter { it.isNotBlank() }.any { firma ->
            val version = firma.substringBefore(',', missingDelimiterValue = "")
            val valor = firma.substringAfter(',', missingDelimiterValue = "")
            version == "v1" && valor.isNotBlank() &&
                runCatching { Base64.getDecoder().decode(valor) }.getOrNull()
                    ?.let { MessageDigest.isEqual(it, esperada) } == true
        }
    }

    /** `whsec_MfKQ…` → los bytes de `MfKQ…` decodificado. `null` si no es base64. */
    private fun claveDe(secreto: String): ByteArray? {
        val base = secreto.trim().removePrefix("whsec_")
        if (base.isBlank()) return null
        return runCatching { Base64.getDecoder().decode(base) }.getOrNull()
    }
}
