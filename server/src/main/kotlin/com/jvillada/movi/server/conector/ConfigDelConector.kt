package com.jvillada.movi.server.conector

import org.slf4j.LoggerFactory

/**
 * # Vuelta 135 · Dónde vive el conector y cuánto dura cada cosa
 *
 * La URL pública sale de `PUBLIC_BASE_URL` (por defecto, la de producción). Es la que va en los documentos
 * `.well-known`, el `iss` de la redirección y la **audiencia** de cada token: un token emitido para otra URL no
 * entra a `/mcp`. Por eso no se arma con el `Host` del pedido —que lo escribe quien llama—, sino con esta.
 */
object ConfigDelConector {
    const val PRODUCCION = "https://movi-project-production.up.railway.app"

    /** El único permiso que existe: leer. No hay ninguno para escribir (ver `HerramientasDelConector`). */
    const val SCOPE_LEER = "movi:leer"

    /** `offline_access` se acepta (Claude lo pide si está listado) y no agrega nada: el refresco ya va. */
    const val SCOPE_OFFLINE = "offline_access"

    /** El token de acceso: una hora. Claude lo refresca solo antes de que venza. */
    const val VIDA_DEL_ACCESO_MS = 60L * 60 * 1000

    /** El de refresco: 30 días desde la última vez que se usó (se cambia por otro en cada uso). */
    const val VIDA_DEL_REFRESCO_MS = 30L * 24 * 60 * 60 * 1000

    /** El código de autorización: cinco minutos (la spec pide diez como mucho). */
    const val VIDA_DEL_CODIGO_MS = 5L * 60 * 1000

    /** La pantalla de consentimiento: diez minutos para entrar y permitir. */
    const val VIDA_DE_LA_SOLICITUD_MS = 10L * 60 * 1000

    /**
     * Cuántas llamadas a `/mcp` por minuto acepta una conexión. Elegido, no medido: una conversación normal hace
     * unas pocas; esto frena un bucle sin molestar a nadie.
     */
    const val LLAMADAS_POR_MINUTO = 60

    private val log = LoggerFactory.getLogger("ConectorDeClaude")

    /** Para las pruebas: fija la base sin tocar el entorno. */
    @Volatile
    internal var baseParaPruebas: String? = null

    private val delEntorno: String by lazy {
        val crudo = System.getenv("PUBLIC_BASE_URL")?.trim()?.trimEnd('/').orEmpty()
        when {
            crudo.isEmpty() -> PRODUCCION
            esBaseValida(crudo) -> crudo
            else -> {
                log.warn("PUBLIC_BASE_URL no es una URL https (ni http://localhost): se usa la de producción")
                PRODUCCION
            }
        }
    }

    /** `https://movi…railway.app`, sin barra final. */
    val base: String get() = baseParaPruebas?.trimEnd('/') ?: delEntorno

    /** La URL del servidor MCP: la que el dueño pega en Claude y la audiencia de los tokens. */
    val urlDelMcp: String get() = "$base/mcp"

    /** El emisor (RFC 8414 / RFC 9207): el mismo server de Movi. */
    val emisor: String get() = base

    val metadataDelRecurso: String get() = "$base/.well-known/oauth-protected-resource"

    internal fun esBaseValida(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (uri.host.isNullOrBlank() || uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) return false
        return uri.scheme == "https" || (uri.scheme == "http" && esLoopback(uri.host))
    }
}

/** `localhost`, `127.0.0.1` o `[::1]`: lo único que se acepta por http (RFC 8252 §7.3). */
internal fun esLoopback(host: String?): Boolean =
    host != null && host.lowercase() in setOf("localhost", "127.0.0.1", "[::1]", "::1")
