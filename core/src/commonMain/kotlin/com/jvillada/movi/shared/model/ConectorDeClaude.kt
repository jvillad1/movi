package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # Vuelta 135 · «Movi en Claude»: lo que la app sabe del conector
 *
 * `GET /api/conector` (con la sesión de la app): la dirección del servidor MCP que el dueño pega en Claude y las
 * conexiones que tiene activas. `DELETE /api/conector/conexiones/{id}` desconecta una (revoca su acceso y sus
 * tokens de inmediato). Nada de esto lleva un token: los tokens del conector no salen nunca del server.
 */
@Serializable
data class ConectorDeClaude(
    /** `https://…/mcp`: la que se pega en Claude → Ajustes → Conectores. */
    val url: String,
    val conexiones: List<ConexionDeClaude> = emptyList(),
)

/** Una conexión activa: qué cliente, a qué host vuelve, cuándo se conectó y cuándo leyó por última vez. */
@Serializable
data class ConexionDeClaude(
    val id: String,
    /** «Claude», «Claude Code» o el nombre que declaró el cliente. */
    val cliente: String,
    /** `claude.ai`, `localhost`… */
    val host: String = "",
    /** Epoch ms. */
    val conectadaEn: Long,
    /** Epoch ms; `null` = todavía no leyó nada. */
    val ultimoUso: Long? = null,
)

/** El comando de Claude Code para agregar el conector. */
fun comandoDeClaudeCode(url: String): String = "claude mcp add --transport http movi $url"
