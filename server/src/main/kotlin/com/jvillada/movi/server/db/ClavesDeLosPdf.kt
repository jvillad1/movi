package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

/**
 * # `claves_de_extractos` — la clave de apertura de los extractos, si el dueño pidió recordarla
 *
 * Vuelta 103 (ver `documentos/PdfConClave.kt`). Una fila por usuario, **solo** si marcó «Recordarla
 * para mis próximos extractos» al abrir un PDF con clave.
 *
 * - [clave] va **siempre cifrada** con `DOCUMENTS_ENCRYPTION_KEY` (`Cifrado`, AES-256-GCM). Sin esa
 *   variable no se escribe ninguna fila: Movi no guarda claves en claro, jamás. Lo cifrado lleva
 *   adentro el id del dueño, así que una fila copiada a otro usuario no abre.
 * - **Nunca sale del server**: ninguna ruta la devuelve (`GET /api/extractos/clave` dice solo si
 *   hay una), no va al «Descargar tus datos» (no está en `TABLAS_EXPORTADAS`) y no se loguea.
 * - «Olvidarla» (Ajustes → Tus datos) borra la fila; borrar la cuenta también (`TABLAS_DEL_USUARIO`).
 *
 * Tabla NUEVA: entra por `SchemaUtils.create`, con la clave primaria adentro del CREATE TABLE.
 */
object ClavesDeExtractos : Table("claves_de_extractos") {
    val userId = varchar("user_id", 50)
    val clave = text("clave")
    val guardadaEn = long("guardada_en")
    override val primaryKey = PrimaryKey(userId)
}

/**
 * # `pdfs_abiertos` — qué documento quedó de un PDF con clave que Movi abrió
 *
 * Al abrir un PDF con clave, Movi guarda el contenido **descifrado** en el lugar del original (así la
 * descarga, el lector y un reintento no vuelven a pedir clave). Desde ahí los bytes guardados ya no son
 * los que el dueño tiene en su teléfono, y el `reusar` de `POST /api/documents` —que compara contra lo
 * guardado— no reconocería el mismo archivo cifrado subido otra vez: lo duplicaría.
 *
 * Esta tabla es esa memoria: la [huellaOriginal] (SHA-256 de los bytes **tal como se subieron**, con
 * su clave) y el [documentoId] donde quedó abierto. La huella de la lectura (`lecturas_de_papeles`)
 * sigue siendo la del contenido que se leyó, el descifrado.
 *
 * Memoria interna de Movi: no va al «Descargar tus datos». Tabla NUEVA, por `SchemaUtils.create`.
 */
object PdfsAbiertos : Table("pdfs_abiertos") {
    val userId = varchar("user_id", 50)
    val huellaOriginal = varchar("huella_original", 64)
    val documentoId = varchar("documento_id", 50)
    val abiertoEn = long("abierto_en")
    override val primaryKey = PrimaryKey(userId, huellaOriginal)
}
