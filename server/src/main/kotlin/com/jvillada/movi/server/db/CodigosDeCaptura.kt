package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

/**
 * # `codigos_de_captura` — el código que el dueño pega en sus atajos del iPhone (vuelta 111)
 *
 * Ver `CapturaPorAtajo.kt` en `:core` para qué es, y `routes/CapturaPorAtajoRoutes.kt` para cómo se usa.
 *
 * **Como `enlaces_compartidos`, la columna que NO está es el código.** Acá vive [tokenHash], su SHA-256,
 * y nada más: quien tiene el código puede llenarle la bandeja a alguien, así que un respaldo filtrado de
 * la base no puede traerlo en claro. El código tiene 256 bits de `SecureRandom`; un SHA-256 a secas basta
 * (no es una contraseña que se adivine por diccionario) y deja buscarlo por índice.
 *
 * Un dueño tiene **a lo sumo un código vigente** ([revocadoEn] en `NULL`): crear otro revoca el anterior
 * en la misma transacción. **Revocar no borra la fila**: queda cuándo se creó, cuándo se cortó y cuándo se
 * usó por última vez ([ultimoUso]).
 *
 * Tabla NUEVA: entra por `SchemaUtils.create` del arranque, con sus índices adentro del CREATE TABLE.
 * Tiene `user_id`, así que está en `TABLAS_DEL_USUARIO` (se va con la cuenta).
 */
object CodigosDeCaptura : Table("codigos_de_captura") {
    val id         = varchar("id", 50)
    val userId     = varchar("user_id", 50)
    /** SHA-256 del código, en hex: 64 caracteres. */
    val tokenHash  = varchar("token_hash", 64).uniqueIndex("uq_codigos_de_captura_token_hash")
    val creadoEn   = long("creado_en")
    /** NULL = vigente. */
    val revocadoEn = long("revocado_en").nullable()
    /** Cuándo llegó el último aviso con este código; NULL si todavía ninguno. */
    val ultimoUso  = long("ultimo_uso").nullable()
    override val primaryKey = PrimaryKey(id)
    init { index("idx_codigos_de_captura_user_id", false, userId) }
}

/**
 * # `atajos_descartados` — lo que mandó un atajo y no se guardó (vuelta 111)
 *
 * Un atajo mal configurado (una automatización «Mensaje» sin filtro) puede mandar **todos** los SMS del
 * iPhone. Lo que no pasa el filtro de bancos no se guarda —ni el texto, ni el remitente, ni una huella—:
 * acá queda **solo cuántos** llegaron ese día y por qué, para que «nada se pierda en silencio» sin guardar
 * nada personal. Una fila por dueño, día (`AAAA-MM-DD` de la zona de la app) y motivo.
 */
object AtajosDescartados : Table("atajos_descartados") {
    val userId  = varchar("user_id", 50)
    val dia     = varchar("dia", 10)
    val motivo  = varchar("motivo", 40)
    val cuantos = integer("cuantos").default(0)
    override val primaryKey = PrimaryKey(userId, dia, motivo)
}
