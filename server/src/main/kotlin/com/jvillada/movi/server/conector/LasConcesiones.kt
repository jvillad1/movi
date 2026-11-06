package com.jvillada.movi.server.conector

import com.jvillada.movi.server.compartir.TokenDeEnlace
import com.jvillada.movi.server.db.CodigosDelConector
import com.jvillada.movi.server.db.LlamadasDelConector
import com.jvillada.movi.server.db.ConcesionesDelConector
import com.jvillada.movi.server.db.TokensDelConector
import com.jvillada.movi.server.db.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/**
 * # Vuelta 135 · Lo que el dueño concedió, y los tokens que salen de ahí
 *
 * **Las reglas, en un solo lugar:**
 *
 * - **Tokens propios, opacos y con audiencia.** Un token del conector son 256 bits de azar con un prefijo
 *   (`mvc_a_`, `mvc_r_`); la base guarda su SHA-256 y la URL del MCP para la que se emitió. No es un JWT a
 *   propósito: el JWT de la sesión de la app nunca coincide con ningún hash de acá, y un token del conector no es
 *   un JWT que pase el verificador de `/api/…`. Los dos mundos no se tocan (MCP, «token passthrough»).
 * - **El `user_id` sale del token y de nada más.** Ninguna herramienta recibe un usuario por parámetro.
 * - **Corto y rotado.** Acceso, una hora; refresco, 30 días, cambiado por otro en cada uso. Usar un refresco ya
 *   cambiado es la señal de que alguien lo copió: se corta la concesión entera (OAuth 2.1 §4.3.1). La excepción
 *   es una carrera inocente o un reintento —dos refrescos del mismo cliente casi a la vez, o una respuesta que se
 *   perdió y Claude vuelve a pedir—: dentro de [GRACIA_DEL_REFRESCO_MS] (2 minutos) se contesta `invalid_grant`
 *   sin cortar nada; Claude vuelve a pedir permiso y no se pierde la conexión entera por un tropiezo de red.
 *   Pasados los 2 minutos, reusar un refresco viejo corta la conexión: si alguien dice «se desconectó solo»,
 *   esta es la causa probable (un refresco reintentado muy tarde).
 *   Dentro de la gracia se revocan los tokens que salieron de esa rotación (la que quedó huérfana: nadie la va a
 *   usar, y si fue un ladrón que se adelantó, pierde lo que sacó), sin cortar la concesión, y queda un WARN. Si la
 *   rotación sí le había llegado a Claude, Claude vuelve a conectar: es el precio de no dejar viva una rama robada.
 * - **Vida máxima: un año** ([VIDA_MAXIMA_MS]) desde que se concedió, se use o no; después hay que volver a entrar
 *   desde Claude (N5 de la re-revisión). Sin uso, la concesión ya se acaba sola a los 30 días (el refresco vence),
 *   así que no hay una regla aparte de «sin uso»; el barrido diario borra las que llevan 90 días sin uso
 *   ([RETENCION_MS], [limpiarElConector]).
 * - **Ata la sesión de Movi.** La concesión guarda `users.token_version`; «Cerrar sesión en todos los aparatos» o
 *   restablecer la contraseña la suben y Claude queda afuera también.
 * - **Desconectar es inmediato.** Revoca la concesión y todos sus tokens en una transacción; el pedido siguiente
 *   de Claude ya recibe 401.
 */
private val logDeConcesiones = org.slf4j.LoggerFactory.getLogger("ConectorDeClaude")

internal object LasConcesiones {
    const val PREFIJO_ACCESO = "mvc_a_"
    const val PREFIJO_REFRESCO = "mvc_r_"
    const val PREFIJO_CODIGO = "mvc_c_"
    const val PREFIJO_SOLICITUD = "mvc_s_"

    const val TIPO_ACCESO = "acceso"
    const val TIPO_REFRESCO = "refresco"

    /** Ver el KDoc del objeto: un refresco repetido dentro de esto no corta la concesión. */
    const val GRACIA_DEL_REFRESCO_MS = 2 * 60_000L

    /** Ver el KDoc del objeto: un año desde que se concedió y la concesión se acaba, se use o no. */
    const val VIDA_MAXIMA_MS = 365L * 24 * 60 * 60 * 1000

    /** Cuánto se guardan las llamadas y una concesión sin uso o revocada antes de borrarlas (el barrido diario). */
    const val RETENCION_MS = 90L * 24 * 60 * 60 * 1000

    private val FORMA = Regex("^mvc_[arcs]_[A-Za-z0-9_-]{${TokenDeEnlace.LARGO}}$")

    fun nuevo(prefijo: String): String = prefijo + TokenDeEnlace.nuevo()

    fun tieneForma(token: String, prefijo: String): Boolean = token.startsWith(prefijo) && FORMA.matches(token)

    fun hash(token: String): String = TokenDeEnlace.hashDelToken(token)
}

/** Lo que un token de acceso válido dice: de quién es y de qué concesión. */
internal data class AccesoDelConector(val userId: String, val concesionId: String)

/**
 * **Una autorización pedida**, ya validada por `GET /oauth/authorize`. No se guarda: viaja en la pantalla de
 * consentimiento firmada con HMAC (ver `SolicitudFirmada`), así que pedir permiso no escribe nada en la base.
 */
internal data class SolicitudDelConector(
    val clientId: String,
    val redirectUri: String,
    val codeChallenge: String,
    val state: String?,
    val scope: String,
    val venceEn: Long,
) {
    /** El nombre sale del retorno, nunca de lo que dice el cliente (ver `nombreDelCliente`). */
    val nombreDelCliente: String get() = nombreDelCliente(redirectUri)
}

/** Lo que devuelve `/oauth/token`: el par nuevo, en claro, una sola vez. */
internal data class ParDeTokens(val acceso: String, val refresco: String, val scope: String)

/** Por qué no salió un token. Hoy una sola razón, que se contesta `invalid_grant`. */
internal enum class FallaDelToken { INVALIDO }

// ── Concesión y código ─────────────────────────────────────────────────────────────────────────

/**
 * **El dueño dijo que sí**: la concesión y su código de un solo uso. Devuelve el código en claro, que va en la
 * redirección y en ningún otro lado.
 */
internal fun Transaction.concederYEmitirCodigo(uid: String, s: SolicitudDelConector, ahora: Long): String {
    val version = Users.select(Users.tokenVersion).where { Users.id eq uid }.first()[Users.tokenVersion]
    val concesionId = "cnc_" + UUID.randomUUID()
    ConcesionesDelConector.insert {
        it[id] = concesionId
        it[userId] = uid
        it[clientId] = s.clientId
        it[nombreDelCliente] = s.nombreDelCliente.take(100)
        it[hostDelRetorno] = runCatching { java.net.URI(s.redirectUri).host }.getOrNull().orEmpty().take(255)
        it[scope] = s.scope
        it[versionDeSesion] = version
        it[creadaEn] = ahora
    }
    val codigo = LasConcesiones.nuevo(LasConcesiones.PREFIJO_CODIGO)
    CodigosDelConector.insert {
        it[hash] = LasConcesiones.hash(codigo)
        it[userId] = uid
        it[CodigosDelConector.concesionId] = concesionId
        it[clientId] = s.clientId
        it[redirectUri] = s.redirectUri
        it[codeChallenge] = s.codeChallenge
        it[venceEn] = ahora + ConfigDelConector.VIDA_DEL_CODIGO_MS
    }
    return codigo
}

/** PKCE S256: `BASE64URL(SHA256(code_verifier)) == code_challenge`, en tiempo constante. */
internal fun pkceCoincide(verificador: String, desafio: String): Boolean {
    if (!FORMA_DEL_VERIFICADOR.matches(verificador)) return false
    val calculado = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verificador.toByteArray(Charsets.US_ASCII)))
    return MessageDigest.isEqual(calculado.toByteArray(), desafio.toByteArray())
}

/** RFC 7636 §4.1: 43 a 128 caracteres «unreserved». El desafío S256 tiene la misma forma (43). */
internal val FORMA_DEL_VERIFICADOR = Regex("^[A-Za-z0-9._~-]{43,128}$")

/**
 * **`grant_type=authorization_code`.** El código se gasta antes de mirar nada más (así dos pedidos con el mismo
 * código no pueden ganar los dos), y si ya estaba gastado se corta la concesión que salió de él: alguien lo está
 * reusando (OAuth 2.1 §4.1.3).
 */
internal fun Transaction.canjearCodigo(
    codigo: String,
    clientId: String,
    redirectUri: String,
    verificador: String,
    ahora: Long,
): Result<ParDeTokens> {
    if (!LasConcesiones.tieneForma(codigo, LasConcesiones.PREFIJO_CODIGO)) return falla(FallaDelToken.INVALIDO)
    val hash = LasConcesiones.hash(codigo)
    val fila = CodigosDelConector.selectAll().where { CodigosDelConector.hash eq hash }.firstOrNull()
        ?: return falla(FallaDelToken.INVALIDO)
    val concesionId = fila[CodigosDelConector.concesionId]
    val gastado = CodigosDelConector.update({ (CodigosDelConector.hash eq hash) and CodigosDelConector.usadoEn.isNull() }) {
        it[usadoEn] = ahora
    } == 1
    if (!gastado) {
        revocarConcesion(concesionId, ahora)
        return falla(FallaDelToken.INVALIDO)
    }
    if (fila[CodigosDelConector.venceEn] <= ahora) return falla(FallaDelToken.INVALIDO)
    if (fila[CodigosDelConector.clientId] != clientId) return falla(FallaDelToken.INVALIDO)
    if (fila[CodigosDelConector.redirectUri] != redirectUri) return falla(FallaDelToken.INVALIDO)
    if (!pkceCoincide(verificador, fila[CodigosDelConector.codeChallenge])) return falla(FallaDelToken.INVALIDO)
    val concesion = concesionVigente(concesionId, ahora) ?: return falla(FallaDelToken.INVALIDO)
    return Result.success(emitirPar(concesion.userId, concesionId, concesion.scope, ahora))
}

/**
 * **`grant_type=refresh_token`**, con rotación. Ver el KDoc de [LasConcesiones] para la regla del reuso.
 */
internal fun Transaction.refrescar(refresco: String, clientId: String, ahora: Long): Result<ParDeTokens> {
    if (!LasConcesiones.tieneForma(refresco, LasConcesiones.PREFIJO_REFRESCO)) return falla(FallaDelToken.INVALIDO)
    val hash = LasConcesiones.hash(refresco)
    val fila = TokensDelConector.selectAll()
        .where { (TokensDelConector.hash eq hash) and (TokensDelConector.tipo eq LasConcesiones.TIPO_REFRESCO) }
        .firstOrNull() ?: return falla(FallaDelToken.INVALIDO)
    val concesionId = fila[TokensDelConector.concesionId]
    val revocado = fila[TokensDelConector.revocadoEn]
    if (revocado != null) {
        if (ahora - revocado > LasConcesiones.GRACIA_DEL_REFRESCO_MS) {
            revocarConcesion(concesionId, ahora)
            logDeConcesiones.warn("Conector: un refresco ya cambiado se volvió a usar pasada la gracia; se cortó la concesión $concesionId")
        } else {
            // N5 · la rotación que salió de este refresco (los tokens creados en el mismo instante en que se cambió)
            // queda huérfana: se revoca, sin cortar la concesión.
            val huerfanos = TokensDelConector.update({
                (TokensDelConector.concesionId eq concesionId) and (TokensDelConector.creadoEn eq revocado) and TokensDelConector.revocadoEn.isNull()
            }) { it[revocadoEn] = ahora }
            logDeConcesiones.warn("Conector: un refresco repetido dentro de la gracia; se revocaron $huerfanos tokens de esa rotación (concesión $concesionId)")
        }
        return falla(FallaDelToken.INVALIDO)
    }
    if (fila[TokensDelConector.venceEn] <= ahora) return falla(FallaDelToken.INVALIDO)
    if (fila[TokensDelConector.audiencia] != ConfigDelConector.urlDelMcp) return falla(FallaDelToken.INVALIDO)
    val concesion = concesionVigente(concesionId, ahora) ?: return falla(FallaDelToken.INVALIDO)
    if (concesion.clientId != clientId) return falla(FallaDelToken.INVALIDO)
    val cambiado = TokensDelConector.update({ (TokensDelConector.hash eq hash) and TokensDelConector.revocadoEn.isNull() }) {
        it[revocadoEn] = ahora
    } == 1
    if (!cambiado) return falla(FallaDelToken.INVALIDO)
    return Result.success(emitirPar(concesion.userId, concesionId, concesion.scope, ahora))
}

private fun <T> falla(f: FallaDelToken): Result<T> = Result.failure(FallaDelTokenException(f))

internal class FallaDelTokenException(val falla: FallaDelToken) : Exception(falla.name)

private const val UN_DIA_MS = 24L * 60 * 60 * 1000

private data class ConcesionVigente(val userId: String, val clientId: String, val scope: String)

/**
 * La concesión si sigue vigente: no revocada, de menos de [LasConcesiones.VIDA_MAXIMA_MS] y con la misma versión de
 * sesiones que su dueño tiene hoy.
 */
private fun Transaction.concesionVigente(id: String, ahora: Long): ConcesionVigente? {
    val fila = ConcesionesDelConector.selectAll()
        .where { (ConcesionesDelConector.id eq id) and ConcesionesDelConector.revocadaEn.isNull() }
        .firstOrNull() ?: return null
    if (ahora - fila[ConcesionesDelConector.creadaEn] > LasConcesiones.VIDA_MAXIMA_MS) return null
    val uid = fila[ConcesionesDelConector.userId]
    val vigente = Users.select(Users.tokenVersion).where { Users.id eq uid }.firstOrNull()?.get(Users.tokenVersion) ?: return null
    if (vigente != fila[ConcesionesDelConector.versionDeSesion]) return null
    return ConcesionVigente(uid, fila[ConcesionesDelConector.clientId], fila[ConcesionesDelConector.scope])
}

private fun Transaction.emitirPar(uid: String, concesionId: String, scope: String, ahora: Long): ParDeTokens {
    // Lo que venció hace más de un día ya no dice nada (un refresco cambiado sigue hasta su vencimiento: es lo
    // que deja ver que alguien reusa uno viejo). Sin esto, cada hora de uso dejaba dos filas para siempre.
    TokensDelConector.deleteWhere { (TokensDelConector.concesionId eq concesionId) and (TokensDelConector.venceEn less ahora - UN_DIA_MS) }
    val acceso = LasConcesiones.nuevo(LasConcesiones.PREFIJO_ACCESO)
    val refresco = LasConcesiones.nuevo(LasConcesiones.PREFIJO_REFRESCO)
    listOf(
        Triple(acceso, LasConcesiones.TIPO_ACCESO, ConfigDelConector.VIDA_DEL_ACCESO_MS),
        Triple(refresco, LasConcesiones.TIPO_REFRESCO, ConfigDelConector.VIDA_DEL_REFRESCO_MS),
    ).forEach { (token, tipo, vida) ->
        TokensDelConector.insert {
            it[hash] = LasConcesiones.hash(token)
            it[userId] = uid
            it[TokensDelConector.concesionId] = concesionId
            it[TokensDelConector.tipo] = tipo
            it[audiencia] = ConfigDelConector.urlDelMcp
            it[creadoEn] = ahora
            it[venceEn] = ahora + vida
        }
    }
    return ParDeTokens(acceso, refresco, scope)
}

// ── Verificar en /mcp ──────────────────────────────────────────────────────────────────────────

/**
 * **¿Este `Authorization: Bearer …` abre `/mcp`?** Un token de acceso del conector, con la forma, sin revocar, sin
 * vencer, emitido para ESTA URL del MCP, de una concesión vigente. Cualquier otra cosa —el JWT de la app, un
 * token de refresco, uno de otra audiencia— da `null`, y la ruta contesta 401.
 */
internal fun Transaction.accesoDelToken(token: String, ahora: Long): AccesoDelConector? {
    if (!LasConcesiones.tieneForma(token, LasConcesiones.PREFIJO_ACCESO)) return null
    val hash = LasConcesiones.hash(token)
    val fila = TokensDelConector.selectAll()
        .where { (TokensDelConector.hash eq hash) and (TokensDelConector.tipo eq LasConcesiones.TIPO_ACCESO) }
        .firstOrNull() ?: return null
    if (!TokenDeEnlace.esElMismoHash(fila[TokensDelConector.hash], hash)) return null
    if (fila[TokensDelConector.revocadoEn] != null || fila[TokensDelConector.venceEn] <= ahora) return null
    if (fila[TokensDelConector.audiencia] != ConfigDelConector.urlDelMcp) return null
    val concesionId = fila[TokensDelConector.concesionId]
    val concesion = concesionVigente(concesionId, ahora) ?: return null
    if (concesion.userId != fila[TokensDelConector.userId]) return null
    return AccesoDelConector(concesion.userId, concesionId)
}

/** Anota el último uso de la concesión (lo que dice la lista de «Movi en Claude»). */
internal fun Transaction.anotarUso(concesionId: String, ahora: Long) {
    ConcesionesDelConector.update({ ConcesionesDelConector.id eq concesionId }) { it[ultimoUso] = ahora }
}

// ── Revocar ────────────────────────────────────────────────────────────────────────────────────

/** Revoca la concesión y todo lo que salió de ella. Idempotente. */
internal fun Transaction.revocarConcesion(id: String, ahora: Long) {
    ConcesionesDelConector.update({ (ConcesionesDelConector.id eq id) and ConcesionesDelConector.revocadaEn.isNull() }) {
        it[revocadaEn] = ahora
    }
    TokensDelConector.update({ (TokensDelConector.concesionId eq id) and TokensDelConector.revocadoEn.isNull() }) {
        it[revocadoEn] = ahora
    }
    CodigosDelConector.update({ (CodigosDelConector.concesionId eq id) and CodigosDelConector.usadoEn.isNull() }) {
        it[usadoEn] = ahora
    }
}

/** **«Desconectar»** desde la app: solo una concesión de [uid]. `false` = no es suya o no existe. */
internal fun Transaction.desconectar(uid: String, id: String, ahora: Long): Boolean {
    val suya = !ConcesionesDelConector.selectAll()
        .where { (ConcesionesDelConector.id eq id) and (ConcesionesDelConector.userId eq uid) }
        .empty()
    if (!suya) return false
    revocarConcesion(id, ahora)
    return true
}

/** Una conexión como la lista la muestra. */
internal data class ConexionDelConector(
    val id: String,
    val cliente: String,
    val host: String,
    val conectadaEn: Long,
    val ultimoUso: Long?,
)

/**
 * **Las conexiones activas de [uid]**: concesiones sin revocar, de la versión de sesiones vigente, con un token de
 * refresco que todavía sirve. Una concesión cuyo refresco venció (Claude no la usó en 30 días) ya no conecta
 * nada y no se lista.
 */
internal fun Transaction.conexionesActivas(uid: String, ahora: Long): List<ConexionDelConector> {
    val version = Users.select(Users.tokenVersion).where { Users.id eq uid }.firstOrNull()?.get(Users.tokenVersion) ?: return emptyList()
    val conRefresco = TokensDelConector.select(TokensDelConector.concesionId)
        .where {
            (TokensDelConector.userId eq uid) and (TokensDelConector.tipo eq LasConcesiones.TIPO_REFRESCO) and
                TokensDelConector.revocadoEn.isNull() and (TokensDelConector.venceEn greater ahora)
        }
        .map { it[TokensDelConector.concesionId] }
        .toSet()
    return ConcesionesDelConector.selectAll()
        .where {
            (ConcesionesDelConector.userId eq uid) and ConcesionesDelConector.revocadaEn.isNull() and
                (ConcesionesDelConector.versionDeSesion eq version)
        }
        .filter { it[ConcesionesDelConector.id] in conRefresco }
        .map {
            ConexionDelConector(
                id = it[ConcesionesDelConector.id],
                cliente = it[ConcesionesDelConector.nombreDelCliente],
                host = it[ConcesionesDelConector.hostDelRetorno],
                conectadaEn = it[ConcesionesDelConector.creadaEn],
                ultimoUso = it[ConcesionesDelConector.ultimoUso],
            )
        }
        .sortedByDescending { it.ultimoUso ?: it.conectadaEn }
}

/**
 * **El barrido diario del conector** (M6 de la revisión), en el mismo bucle que la retención de la observabilidad
 * (`startReminderScheduler`): códigos y tokens vencidos, llamadas de más de 90 días, y las concesiones que nadie
 * usó en 90 días, que pasaron su vida máxima o que se revocaron hace más de 90 días (con sus tokens y códigos).
 * Devuelve cuántas filas se fueron. Ya no hay solicitudes guardadas (van firmadas) ni clientes DCR que limpiar
 * (son a lo sumo siete, ver `idDelClienteDcr`).
 */
internal fun Transaction.limpiarElConector(ahora: Long): Int {
    var filas = 0
    filas += CodigosDelConector.deleteWhere { CodigosDelConector.venceEn less ahora }
    filas += TokensDelConector.deleteWhere { TokensDelConector.venceEn less ahora }
    val corte = ahora - LasConcesiones.RETENCION_MS
    filas += LlamadasDelConector.deleteWhere { LlamadasDelConector.en less corte }
    val viejas = ConcesionesDelConector.selectAll()
        .filter { fila ->
            val revocada = fila[ConcesionesDelConector.revocadaEn]
            val usada = fila[ConcesionesDelConector.ultimoUso] ?: fila[ConcesionesDelConector.creadaEn]
            (revocada != null && revocada < corte) || usada < corte
        }
        .map { it[ConcesionesDelConector.id] }
    viejas.chunked(500).forEach { lote ->
        filas += TokensDelConector.deleteWhere { TokensDelConector.concesionId inList lote }
        filas += CodigosDelConector.deleteWhere { CodigosDelConector.concesionId inList lote }
        filas += ConcesionesDelConector.deleteWhere { ConcesionesDelConector.id inList lote }
    }
    return filas
}

/**
 * **`POST /oauth/revoke`** (RFC 7009): el cliente avisa que ya no usará un token (quitar el conector en Claude).
 * Revoca la concesión entera del token —acceso o refresco— si es de ese [clientId]. Lo que no se encuentra no se
 * dice: la ruta contesta 200 igual.
 */
internal fun Transaction.revocarPorToken(token: String, clientId: String, ahora: Long) {
    if (!LasConcesiones.tieneForma(token, LasConcesiones.PREFIJO_ACCESO) && !LasConcesiones.tieneForma(token, LasConcesiones.PREFIJO_REFRESCO)) return
    val concesionId = TokensDelConector.select(TokensDelConector.concesionId)
        .where { TokensDelConector.hash eq LasConcesiones.hash(token) }
        .firstOrNull()?.get(TokensDelConector.concesionId) ?: return
    val deEseCliente = !ConcesionesDelConector.selectAll()
        .where { (ConcesionesDelConector.id eq concesionId) and (ConcesionesDelConector.clientId eq clientId) }
        .empty()
    if (deEseCliente) revocarConcesion(concesionId, ahora)
}

/**
 * **De qué concesión y de qué cliente es una credencial VIGENTE** (para el balde por concesión de `/oauth/token` y
 * `/oauth/revoke`), sin gastarla: un código sin usar y sin vencer, o un token sin revocar y sin vencer, de una
 * concesión sin revocar. `null` = no existe o ya no sirve (N1-bis de la tercera revisión): un refresco ya rotado
 * guarda su fila 30 días para detectar el reuso, y no puede servirle a nadie para gastar un balde.
 */
internal fun Transaction.credencialDe(credencial: String, ahora: Long = System.currentTimeMillis()): Pair<String, String>? {
    val concesionId = when {
        LasConcesiones.tieneForma(credencial, LasConcesiones.PREFIJO_CODIGO) ->
            CodigosDelConector.select(CodigosDelConector.concesionId)
                .where {
                    (CodigosDelConector.hash eq LasConcesiones.hash(credencial)) and CodigosDelConector.usadoEn.isNull() and
                        (CodigosDelConector.venceEn greater ahora)
                }
                .firstOrNull()?.get(CodigosDelConector.concesionId)
        LasConcesiones.tieneForma(credencial, LasConcesiones.PREFIJO_REFRESCO) ||
            LasConcesiones.tieneForma(credencial, LasConcesiones.PREFIJO_ACCESO) ->
            TokensDelConector.select(TokensDelConector.concesionId)
                .where {
                    (TokensDelConector.hash eq LasConcesiones.hash(credencial)) and TokensDelConector.revocadoEn.isNull() and
                        (TokensDelConector.venceEn greater ahora)
                }
                .firstOrNull()?.get(TokensDelConector.concesionId)
        else -> null
    } ?: return null
    val clientId = ConcesionesDelConector.select(ConcesionesDelConector.clientId)
        .where { (ConcesionesDelConector.id eq concesionId) and ConcesionesDelConector.revocadaEn.isNull() }
        .firstOrNull()?.get(ConcesionesDelConector.clientId) ?: return null
    return concesionId to clientId
}
