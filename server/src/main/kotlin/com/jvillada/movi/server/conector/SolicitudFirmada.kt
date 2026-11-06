package com.jvillada.movi.server.conector

import com.jvillada.movi.server.auth.JwtConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * # Vuelta 135 (N2 de la re-revisión) · La solicitud de autorización, firmada y sin guardar
 *
 * `GET /oauth/authorize` ya no escribe una fila por pedido (un tope global de filas pendientes se llenaba desde
 * cuatro IP y dejaba sin conexiones nuevas a todos). Lo que validó —el cliente, el retorno, el desafío PKCE, el
 * `state`, el permiso y el vencimiento de 10 minutos— viaja en la pantalla como un valor firmado con HMAC-SHA256, y
 * `POST /oauth/authorize` lo verifica antes de mirar la contraseña. Nadie puede cambiar el retorno ni el desafío
 * entre la pantalla y el «Permitir» sin romper la firma.
 *
 * **Reenviar una solicitud firmada solo vuelve a pedir la contraseña**: cada «Permitir» con la contraseña correcta
 * da un código nuevo de un solo uso, atado al mismo desafío PKCE, que solo el cliente que lo armó puede canjear.
 *
 * La clave sale de `CONECTOR_SECRETO_DE_SOLICITUDES` (32 caracteres o más) o, si no está, se deriva de
 * `JWT_SECRET` con su propia etiqueta (`JwtConfig.pimienta`). Rotar `JWT_SECRET` deja sin servir las pantallas
 * abiertas en ese momento (vencen a los 10 minutos de todos modos).
 */
internal object SolicitudFirmada {
    private const val PREFIJO = "mvc_s1."

    /** Lo más largo que se acepta de un valor firmado (el `state` y el `client_id` caben de sobra). */
    private const val LARGO_MAXIMO = 3_000

    @Serializable
    private data class Contenido(val c: String, val r: String, val p: String, val s: String? = null, val sc: String, val v: Long)

    private val json = Json { ignoreUnknownKeys = false }

    /** Para las pruebas: una clave fija sin tocar `JwtConfig`. */
    @Volatile
    internal var claveParaPruebas: ByteArray? = null

    private val claveDelEntorno: ByteArray by lazy {
        System.getenv("CONECTOR_SECRETO_DE_SOLICITUDES")?.takeIf { it.length >= 32 }?.toByteArray(Charsets.UTF_8)
            ?: JwtConfig.pimienta("conector:solicitud")
    }

    private fun clave(): ByteArray = claveParaPruebas ?: claveDelEntorno

    private fun firma(contenido: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(clave(), "HmacSHA256")) }
            .doFinal("conector-solicitud:v1.$contenido".toByteArray(Charsets.UTF_8))

    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val deB64 = Base64.getUrlDecoder()

    fun firmar(s: SolicitudDelConector): String {
        val contenido = b64.encodeToString(
            json.encodeToString(Contenido.serializer(), Contenido(s.clientId, s.redirectUri, s.codeChallenge, s.state, s.scope, s.venceEn))
                .toByteArray(Charsets.UTF_8),
        )
        return PREFIJO + contenido + "." + b64.encodeToString(firma(contenido))
    }

    /** La solicitud si la firma es nuestra y no venció; `null` si no. */
    fun leer(valor: String, ahora: Long): SolicitudDelConector? {
        if (valor.length > LARGO_MAXIMO || !valor.startsWith(PREFIJO)) return null
        val partes = valor.removePrefix(PREFIJO).split('.')
        if (partes.size != 2) return null
        val (contenido, firmaDada) = partes
        val dada = runCatching { deB64.decode(firmaDada) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(firma(contenido), dada)) return null
        val c = runCatching {
            json.decodeFromString(Contenido.serializer(), deB64.decode(contenido).decodeToString())
        }.getOrNull() ?: return null
        if (c.v <= ahora) return null
        return SolicitudDelConector(clientId = c.c, redirectUri = c.r, codeChallenge = c.p, state = c.s, scope = c.sc, venceEn = c.v)
    }
}
