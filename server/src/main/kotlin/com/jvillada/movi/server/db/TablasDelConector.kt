package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

/*
 * # Vuelta 135 · Las tablas del conector de Movi para Claude
 *
 * Claude (web, escritorio, móvil y Claude Code) lee las cifras del dueño por un servidor MCP en `/mcp`,
 * después de que el dueño entra con su cuenta de Movi y lo permite (OAuth 2.1 con PKCE; ver
 * `conector/` y `docs/conector-claude.md`). Estas son las filas que eso deja.
 *
 * **Ningún token ni código se guarda en claro**: como los enlaces compartidos (`EnlacesCompartidos`), se
 * guarda el SHA-256 en hex y nada más. Son 256 bits de `SecureRandom`, así que un hash rápido alcanza y deja
 * buscar por índice; lo que se filtre de la base no abre nada.
 *
 * Las cuatro con `user_id` entran a `TABLAS_DEL_USUARIO` (borrar la cuenta se las lleva). Las otras dos no son
 * de nadie todavía: un cliente registrado por DCR y una solicitud de autorización antes de que alguien entre.
 *
 * Tablas NUEVAS: entran por el `SchemaUtils.create` del arranque, con sus índices dentro del CREATE TABLE.
 */

/**
 * `conector_clientes` · **los clientes registrados por DCR** (RFC 7591, `POST /oauth/register`). Claude
 * registra uno nuevo en cada conexión fresca. Los de CIMD (el `client_id` es una URL https) no se guardan: se
 * leen de su documento.
 */
object ClientesDelConector : Table("conector_clientes") {
    val id = varchar("id", 80)
    /** El `client_name` que declaró, ya limpio y recortado. Dato del cliente: no se confía en él. */
    val nombre = varchar("nombre", 100)
    /** Las `redirect_uris` que declaró, en JSON. */
    val redirectUris = text("redirect_uris")
    val creadoEn = long("creado_en")
    override val primaryKey = PrimaryKey(id)
}

/**
 * `conector_solicitudes` · **una autorización pedida y todavía no resuelta**: lo que `GET /oauth/authorize`
 * validó, guardado del lado del server para que la pantalla de consentimiento solo lleve un identificador de un
 * solo uso (su hash es [id]). Así nadie puede cambiar el `redirect_uri` ni el `code_challenge` entre la pantalla
 * y el «Permitir», y una misma pantalla no sirve dos veces.
 */
object SolicitudesDelConector : Table("conector_solicitudes") {
    /** SHA-256 del identificador que viaja en el formulario. */
    val id = varchar("id", 64)
    val clientId = varchar("client_id", 500)
    val nombreDelCliente = varchar("nombre_del_cliente", 100)
    val redirectUri = varchar("redirect_uri", 500)
    val codeChallenge = varchar("code_challenge", 128)
    /** El `state` del cliente, que vuelve tal cual. `null` = no mandó. */
    val state = varchar("state", 500).nullable()
    val scope = varchar("scope", 100)
    val venceEn = long("vence_en")
    /** Cuándo se usó (permitida o cancelada). `null` = todavía sirve. */
    val usadaEn = long("usada_en").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * `conector_concesiones` · **«Claude puede leer mis datos»**: lo que el dueño permitió, a qué cliente, y si sigue
 * vigente. «Desconectar» (Tú → Movi en Claude) pone [revocadaEn] y revoca todos sus tokens en la misma
 * transacción. Revocar no borra: la fila dice cuándo se conectó y cuándo se cortó.
 */
object ConcesionesDelConector : Table("conector_concesiones") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50)
    val clientId = varchar("client_id", 500)
    /** «Claude», «Claude Code» o el nombre limpio del cliente. Ver `nombreDelCliente`. */
    val nombreDelCliente = varchar("nombre_del_cliente", 100)
    /** El host al que se devolvió el código (`claude.ai`, `localhost`): lo que la lista dice debajo del nombre. */
    val hostDelRetorno = varchar("host_del_retorno", 255)
    val scope = varchar("scope", 100)
    /**
     * `users.token_version` cuando se concedió. «Cerrar sesión en todos los aparatos» y restablecer la contraseña
     * la suben, y con eso también Claude queda afuera: un token del conector deja de servir si no coincide.
     */
    val versionDeSesion = integer("version_de_sesion")
    val creadaEn = long("creada_en")
    val ultimoUso = long("ultimo_uso").nullable()
    val revocadaEn = long("revocada_en").nullable()
    override val primaryKey = PrimaryKey(id)
    init { index("idx_conector_concesiones_user_id", false, userId) }
}

/** `conector_codigos` · **el código de autorización**: de un solo uso, cinco minutos, atado al PKCE y al retorno. */
object CodigosDelConector : Table("conector_codigos") {
    /** SHA-256 del código. */
    val hash = varchar("hash", 64)
    val userId = varchar("user_id", 50)
    val concesionId = varchar("concesion_id", 50)
    val clientId = varchar("client_id", 500)
    val redirectUri = varchar("redirect_uri", 500)
    val codeChallenge = varchar("code_challenge", 128)
    val venceEn = long("vence_en")
    val usadoEn = long("usado_en").nullable()
    override val primaryKey = PrimaryKey(hash)
    init { index("idx_conector_codigos_user_id", false, userId) }
}

/**
 * `conector_tokens` · **los tokens de acceso y de refresco** (solo su hash). Un token de acceso dura una hora; uno
 * de refresco, 30 días, y se cambia por otro cada vez que se usa ([revocadoEn] = cuándo dejó de servir).
 * [audiencia] es la URL del MCP para la que se emitió: `/mcp` no acepta uno de otra.
 */
object TokensDelConector : Table("conector_tokens") {
    val hash = varchar("hash", 64)
    val userId = varchar("user_id", 50)
    val concesionId = varchar("concesion_id", 50)
    /** `acceso` o `refresco`. */
    val tipo = varchar("tipo", 10)
    val audiencia = varchar("audiencia", 300)
    val creadoEn = long("creado_en")
    val venceEn = long("vence_en")
    val revocadoEn = long("revocado_en").nullable()
    override val primaryKey = PrimaryKey(hash)
    init {
        index("idx_conector_tokens_user_id", false, userId)
        index("idx_conector_tokens_concesion_id", false, concesionId)
    }
}

/**
 * `conector_llamadas` · **el registro de cada herramienta que Claude llamó**: quién, cuál, cuándo, cuánto pesó la
 * respuesta y si salió bien. **Sin los argumentos ni el contenido**: lo que Claude preguntó y lo que Movi le
 * contestó no se guarda.
 */
object LlamadasDelConector : Table("conector_llamadas") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50)
    val concesionId = varchar("concesion_id", 50)
    val herramienta = varchar("herramienta", 64)
    val en = long("en")
    val bytes = integer("bytes")
    val ok = bool("ok")
    override val primaryKey = PrimaryKey(id)
    init { index("idx_conector_llamadas_user_id", false, userId) }
}

/** Las seis, para el arranque y las pruebas. */
val TABLAS_DEL_CONECTOR: Array<Table> = arrayOf(
    ClientesDelConector, SolicitudesDelConector, ConcesionesDelConector, CodigosDelConector, TokensDelConector, LlamadasDelConector,
)
