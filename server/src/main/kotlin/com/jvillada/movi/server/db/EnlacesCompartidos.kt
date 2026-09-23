package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

/**
 * # `enlaces_compartidos` — los enlaces de solo lectura que el dueño le pasa a un tercero
 *
 * Ver `EnlaceCompartido` en `:core` para qué son, y `compartir/TokenDeEnlace.kt` para cómo se
 * arma el token.
 *
 * **La columna que NO está es la importante: el token.** Acá vive [tokenHash], el SHA-256 del
 * token, y nada más. Un token es una capacidad —quien lo tiene ve la plata del dueño—, así que
 * guardarlo en claro convertiría cualquier filtración de la base (un respaldo mal guardado, un
 * `pg_dump` pegado en un chat) en una lista de enlaces vivos. Con el hash, lo que se filtra no abre
 * nada: el token tiene 256 bits de azar y no hay diccionario que lo encuentre.
 *
 * SHA-256 a secas y no bcrypt/argon2 **a propósito**: esos existen para contraseñas, que las
 * elige una persona y se adivinan por diccionario. Un token de 256 bits de `SecureRandom` no se
 * adivina, y un hash lento solo le sumaría milisegundos a cada vista de la página. Y un hash
 * determinístico es lo que permite buscar la fila por índice en vez de recorrer la tabla.
 *
 * **Revocar no borra la fila**: pone [revocadoEn]. Así el dueño —o quien investigue algo raro—
 * puede ver después cuándo se creó, cuántas veces se abrió y cuándo se cortó.
 *
 * Vive en su propio archivo y no al final de `Tables.kt` para no pisarse con otras ramas que
 * agregan tablas al mismo tiempo; para Exposed da igual dónde esté el `object`.
 *
 * Tabla NUEVA: entra por `SchemaUtils.create` del arranque (`CREATE TABLE IF NOT EXISTS`) y no
 * por `createMissingTablesAndColumns`. Sus índices se crean junto con ella la primera vez, sobre
 * una tabla vacía, donde no pueden fallar; en los arranques siguientes `create` ve que la tabla
 * existe y no emite nada.
 */
object EnlacesCompartidos : Table("enlaces_compartidos") {
    val id         = varchar("id", 50)
    val userId     = varchar("user_id", 50)
    /** SHA-256 del token, en hex: 64 caracteres. Ver el KDoc del objeto. */
    val tokenHash  = varchar("token_hash", 64).uniqueIndex("uq_enlaces_compartidos_token_hash")
    /** `resumen` hoy. Texto y no enum: ver `EnlaceCompartido.alcance` en `:core`. */
    val alcance    = varchar("alcance", 30)
    val creadoEn   = long("creado_en")
    val venceEn    = long("vence_en")
    /** NULL = vigente. Ver el KDoc del objeto: revocar no borra. */
    val revocadoEn = long("revocado_en").nullable()
    val ultimaVista = long("ultima_vista").nullable()
    val vistas     = integer("vistas").default(0)
    override val primaryKey = PrimaryKey(id)
    init { index("idx_enlaces_compartidos_user_id", false, userId) }
}
