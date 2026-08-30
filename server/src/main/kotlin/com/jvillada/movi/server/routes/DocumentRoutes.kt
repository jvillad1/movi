package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.EnlaceDeDescarga
import com.jvillada.movi.shared.model.MAX_DOCUMENTO_BYTES
import com.jvillada.movi.shared.model.TipoDeDocumento
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

private fun ResultRow.toDocumento() = Documento(
    id = this[Documents.id],
    nombre = this[Documents.name],
    tipo = runCatching { TipoDeDocumento.valueOf(this[Documents.kind]) }.getOrDefault(TipoDeDocumento.OTRO),
    mimeType = this[Documents.mimeType],
    bytes = this[Documents.sizeBytes],
    subidoEn = this[Documents.uploadedAt],
    accountId = this[Documents.accountId],
    periodo = this[Documents.period],
    notas = this[Documents.notes],
)

/**
 * Las columnas de metadatos, **sin `content`**.
 *
 * `selectAll()` sobre esta tabla traería los bytes de cada archivo para listar sus nombres: con
 * veinte extractos guardados, abrir la pantalla bajaría decenas de megas para pintar veinte
 * renglones. Exposed no lo hace obvio —una lista de columnas explícita es la única forma— así
 * que la lista vive acá una sola vez y no repetida en cada consulta.
 */
private val COLUMNAS_SIN_CONTENIDO = listOf(
    Documents.id, Documents.userId, Documents.name, Documents.kind, Documents.mimeType,
    Documents.sizeBytes, Documents.uploadedAt, Documents.accountId, Documents.period, Documents.notes,
)

/**
 * Guardar, listar, abrir y borrar los papeles del dueño.
 *
 * El pedido: *«me gustaría que guardemos en Movi extractos y documentos en algún lugar y los
 * podamos listar y acceder desde el sitio y la app»*. Hasta acá el importador de extractos
 * recibía el PDF, lo parseaba y **tiraba el archivo**: quedaban los movimientos y se perdía el
 * papel del que salieron — que es justo lo que hace falta el día que una cifra no cuadra con el
 * banco.
 */
fun Route.documentRoutes() {

    /**
     * Sube un archivo. `multipart/form-data`, mismo formato que ya usa el importador de
     * extractos, así el cliente reusa su código de subida.
     *
     * Los campos de texto (`tipo`, `accountId`, `periodo`, `notas`) van en el mismo multipart y
     * **antes** del archivo por convención del cliente; se leen igual en cualquier orden.
     */
    post("/api/documents") {
        val uid = call.userId()
        var nombre = ""
        var mime = ""
        var bytes = ByteArray(0)
        var tipo = TipoDeDocumento.OTRO
        var accountId: String? = null
        var periodo: String? = null
        var notas: String? = null

        call.receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    nombre = part.originalFileName?.takeIf { it.isNotBlank() } ?: "documento"
                    mime = part.contentType?.toString() ?: "application/octet-stream"
                    bytes = part.streamProvider().readBytes()
                }
                is PartData.FormItem -> when (part.name) {
                    "tipo" -> tipo = runCatching { TipoDeDocumento.valueOf(part.value) }.getOrDefault(TipoDeDocumento.OTRO)
                    "accountId" -> accountId = part.value.takeIf { it.isNotBlank() }
                    "periodo" -> periodo = part.value.takeIf { it.isNotBlank() }
                    "notas" -> notas = part.value.takeIf { it.isNotBlank() }
                }
                else -> Unit
            }
            part.dispose()
        }

        if (bytes.isEmpty()) {
            return@post call.respond(HttpStatusCode.BadRequest, "No llegó ningún archivo")
        }
        // El tope se comprueba acá y no en el esquema: un límite en la columna fallaría con un
        // error de base de datos, y el dueño vería «error del servidor» en vez de saber que su
        // archivo pesa de más.
        if (bytes.size > MAX_DOCUMENTO_BYTES) {
            return@post call.respond(
                HttpStatusCode.PayloadTooLarge,
                "El archivo pesa ${bytes.size / (1024 * 1024)} MB y el máximo es ${MAX_DOCUMENTO_BYTES / (1024 * 1024)} MB",
            )
        }

        val doc = Documento(
            id = "doc_${java.util.UUID.randomUUID()}",
            nombre = nombre.take(255),
            tipo = tipo,
            mimeType = mime.take(120),
            bytes = bytes.size.toLong(),
            subidoEn = System.currentTimeMillis(),
            accountId = accountId,
            periodo = periodo?.take(50),
            notas = notas?.take(500),
        )
        dbQuery { guardarDocumento(uid, doc, bytes) }
        call.respond(HttpStatusCode.Created, doc)
    }

    /** La lista, sin los bytes. Lo más reciente primero. */
    get("/api/documents") {
        val uid = call.userId()
        val docs = dbQuery {
            Documents.select(COLUMNAS_SIN_CONTENIDO)
                .where { Documents.userId eq uid }
                .orderBy(Documents.uploadedAt, SortOrder.DESC)
                .map { it.toDocumento() }
        }
        call.respond(docs)
    }

    /**
     * Pide el permiso de descarga. Se comprueba acá —con el token de sesión, que sí viaja en el
     * encabezado— que el documento exista y sea de quien lo pide; la ruta de contenido después
     * solo verifica el permiso.
     */
    post("/api/documents/{id}/link") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el id")
        val existe = dbQuery {
            Documents.select(COLUMNAS_SIN_CONTENIDO)
                .where { (Documents.id eq id) and (Documents.userId eq uid) }
                .any()
        }
        if (!existe) return@post call.respond(HttpStatusCode.NotFound)
        val token = JwtConfig.makeDownloadToken(uid, id)
        call.respond(
            EnlaceDeDescarga(
                url = "/api/documents/$id/content?t=$token",
                expiraEn = System.currentTimeMillis() + JwtConfig.DOWNLOAD_VALIDITY_MS,
            ),
        )
    }

    delete("/api/documents/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest, "Falta el id")
        val borrados = dbQuery {
            Documents.deleteWhere { (Documents.id eq id) and (Documents.userId eq uid) }
        }
        if (borrados == 0) call.respond(HttpStatusCode.NotFound) else call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * El contenido del archivo. **Fuera** del bloque autenticado: se abre desde el navegador, donde
 * no hay forma de mandar el encabezado `Authorization`.
 *
 * No es una puerta abierta: el token de la query es de audiencia distinta a la de la sesión, vale
 * para **este** documento y dura cinco minutos. Y la consulta filtra igual por el `userId` que
 * viene firmado adentro — un token válido para el documento de otro no alcanza para leerlo,
 * porque el `where` no lo encuentra.
 */
fun Route.documentContentRoutes() {
    get("/api/documents/{id}/content") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta el id")
        val token = call.request.queryParameters["t"]
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "Falta el permiso de descarga")
        val uid = JwtConfig.verifyDownloadToken(token, id)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "El enlace venció. Vuelve a abrirlo desde Movi.")

        val fila = dbQuery {
            Documents.selectAll()
                .where { (Documents.id eq id) and (Documents.userId eq uid) }
                .firstOrNull()
        } ?: return@get call.respond(HttpStatusCode.NotFound)

        val mime = runCatching { ContentType.parse(fila[Documents.mimeType]) }
            .getOrDefault(ContentType.Application.OctetStream)
        // `Inline` y no `Attachment`: el camino normal es MIRAR el extracto, y el visor de PDF
        // del navegador lo abre sin bajarlo. El nombre viaja igual, así que «guardar como» sigue
        // proponiendo el nombre original.
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Inline.withParameter(
                ContentDisposition.Parameters.FileName,
                fila[Documents.name],
            ).toString(),
        )
        call.respondBytes(fila[Documents.content], mime)
    }
}

/** Guarda la fila con su contenido. Fuera de las rutas para que el importador también la use. */
fun guardarDocumento(uid: String, doc: Documento, bytes: ByteArray) {
    Documents.insert {
        it[id] = doc.id
        it[userId] = uid
        it[name] = doc.nombre
        it[kind] = doc.tipo.name
        it[mimeType] = doc.mimeType
        it[sizeBytes] = doc.bytes
        it[uploadedAt] = doc.subidoEn
        it[accountId] = doc.accountId
        it[period] = doc.periodo
        it[notes] = doc.notas
        it[content] = bytes
    }
}
