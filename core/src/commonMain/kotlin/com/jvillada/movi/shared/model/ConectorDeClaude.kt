package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.epochMillisToAppDate
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

private val MESES_DE_LA_CONEXION = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/**
 * **La línea de una conexión**: «claude.ai · conectado el 6 nov · último uso hace 2 h», o «… · todavía no leyó
 * nada». Pura (la pantalla y las pruebas la llaman con el mismo [ahora]).
 */
fun lineaDeLaConexion(c: ConexionDeClaude, ahora: Long): String {
    val dia = epochMillisToAppDate(c.conectadaEn)
    val conectado = "conectado el ${dia.dayOfMonth} ${MESES_DE_LA_CONEXION[dia.monthNumber - 1]}"
    val uso = c.ultimoUso?.let { "último uso ${haceCuantoLlegoElAviso(it, ahora)}" } ?: "todavía no leyó nada"
    return listOf(c.host.takeIf { it.isNotBlank() }, conectado, uso).filterNotNull().joinToString(" · ")
}
