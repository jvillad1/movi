package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.JwtConfig
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.EnlaceDeDescarga
import com.jvillada.movi.shared.model.EdicionDeDocumento
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
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.selectAll

/**
 * Los tipos que Movi se anima a servir **`inline`**, o sea a dejar que el navegador los renderice
 * en su propio origen.
 *
 * Es una lista blanca corta y aburrida a propósito. Todo lo demás se baja como adjunto.
 *
 * ### Por qué existe: era una toma de cuenta completa
 *
 * Sin esta lista, el `Content-Type` que servía Movi era **el que mandaba quien subió el archivo**.
 * El registro es público, así que la cadena era: alguien se registra, sube un `extracto.html` (o
 * un `.svg` con un `<script>` adentro) declarándolo `text/html`, pide su enlace de descarga y se
 * lo manda al dueño por WhatsApp — «mirá tu extracto». El dueño lo abre y el script corre **en el
 * origen de Movi**, que es el mismo que sirve la PWA y guarda su JWT de sesión de 30 días en
 * `localStorage`. De ahí a la cuenta entera hay una línea de JavaScript.
 *
 * Lo encontró la revisión, con la sonda corrida contra la ruta: `Content-Type: text/html`,
 * `Content-Disposition: inline`, y ningún `nosniff`.
 *
 * `text/plain` entra porque un `.txt` no ejecuta nada. `text/html` y `image/svg+xml` NO entran, y
 * son justamente los dos que uno pensaría en agregar.
 */
private val MIMES_QUE_SE_MUESTRAN = setOf(
    "application/pdf",
    "image/png",
    "image/jpeg",
    "image/gif",
    "image/webp",
    "text/plain",
)

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
        var demasiadoGrande = false
        var accountId: String? = null
        var periodo: String? = null
        var notas: String? = null

        call.receiveMultipart().forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    nombre = part.originalFileName?.takeIf { it.isNotBlank() } ?: "documento"
                    mime = part.contentType?.toString() ?: "application/octet-stream"
                    // Se lee CON TOPE, no entero y después se mide.
                    //
                    // La primera versión hacía `readBytes()` y comprobaba el tamaño después. La
                    // revisión lo midió subiendo 40 MB: el server contestaba «el máximo es 10 MB»
                    // *después* de haber materializado los 40 en el heap. Con 2 GB tumba el
                    // proceso — y `railway.toml` reintenta solo 3 veces, así que tres subidas
                    // grandes dejan Movi caído hasta que alguien redespliegue a mano. El registro
                    // es público, o sea que no hace falta ser el dueño para provocarlo.
                    // `forEachPart` no admite un `return` de la ruta, así que se marca y se
                    // decide al salir del bucle — pero la lectura ya se cortó, que es el punto.
                    val leido = part.leerConTope(MAX_DOCUMENTO_BYTES)
                    if (leido == null) demasiadoGrande = true else bytes = leido
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

        if (demasiadoGrande) {
            return@post call.respond(
                HttpStatusCode.PayloadTooLarge,
                "El archivo pesa más de ${MAX_DOCUMENTO_BYTES / (1024 * 1024)} MB",
            )
        }
        if (bytes.isEmpty()) {
            return@post call.respond(HttpStatusCode.BadRequest, "No llegó ningún archivo")
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

    /**
     * Corrige nombre, tipo, período o notas. Lo que no venga en el cuerpo **no se toca**.
     *
     * La cadena vacía sí borra: es la única forma de sacar una nota escrita por error, y es
     * distinguible de «no lo mandes» sin tener que mirar las claves del JSON.
     */
    patch("/api/documents/{id}") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@patch call.respond(HttpStatusCode.BadRequest, "Falta el id")
        val cambios = call.receive<EdicionDeDocumento>()

        // ¿Hay algo que escribir? Exposed lanza `Can't prepare UPDATE statement without fields to
        // update` si el bloque no asigna ninguna columna, y eso salía como un 500 sin mensaje. Pasa
        // con un cuerpo vacío (`{}`) y también con un nombre en blanco, que no borra —un documento
        // sin nombre sería una fila que no se puede reconocer— así que no asigna nada.
        //
        // Un PATCH que no cambia nada no es un error: se contesta el documento tal como está.
        val nombreNuevo = cambios.nombre?.trim()?.takeIf { it.isNotBlank() }?.take(255)
        val hayCambios = nombreNuevo != null || cambios.tipo != null ||
            cambios.periodo != null || cambios.notas != null

        if (hayCambios) {
            val actualizados = dbQuery {
                Documents.update({ (Documents.id eq id) and (Documents.userId eq uid) }) {
                    nombreNuevo?.let { n -> it[Documents.name] = n }
                    cambios.tipo?.let { t -> it[Documents.kind] = t.name }
                    cambios.periodo?.let { pe -> it[Documents.period] = pe.trim().take(50).takeIf { v -> v.isNotBlank() } }
                    cambios.notas?.let { no -> it[Documents.notes] = no.trim().take(500).takeIf { v -> v.isNotBlank() } }
                }
            }
            if (actualizados == 0) return@patch call.respond(HttpStatusCode.NotFound)
        }

        val fila = dbQuery {
            Documents.select(COLUMNAS_SIN_CONTENIDO)
                .where { (Documents.id eq id) and (Documents.userId eq uid) }
                .firstOrNull()
        } ?: return@patch call.respond(HttpStatusCode.NotFound)
        call.respond(fila.toDocumento())
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

        // El mime GUARDADO no manda: se compara contra la lista blanca y lo que no está en ella
        // se sirve como binario opaco. Ver [MIMES_QUE_SE_MUESTRAN] — esto era una toma de cuenta.
        val declarado = fila[Documents.mimeType].substringBefore(';').trim().lowercase()
        val seMuestra = declarado in MIMES_QUE_SE_MUESTRAN
        val mime = if (seMuestra) {
            runCatching { ContentType.parse(declarado) }.getOrDefault(ContentType.Application.OctetStream)
        } else {
            ContentType.Application.OctetStream
        }

        // Y aunque el tipo esté en la lista, el navegador no puede adivinar OTRO mirando los
        // bytes: sin `nosniff`, un archivo declarado `text/plain` que empieza con `<html>` se
        // renderiza como HTML en algunos navegadores, y la lista blanca no habría servido de nada.
        call.response.header("X-Content-Type-Options", "nosniff")

        // `Inline` solo para lo que se mira (el visor de PDF del navegador abre sin bajar);
        // `Attachment` para todo lo demás, que es la forma de decir «esto no se renderiza acá».
        //
        // El nombre se sanea antes de entrar al header: lo eligió quien subió el archivo y una
        // comilla rompe el `Content-Disposition`. Netty ya rechaza CR/LF —así que no hay
        // response splitting— pero no hay motivo para depender de eso.
        val nombreSeguro = fila[Documents.name]
            .replace(Regex("[\\r\\n\"\\\\]"), "_")
            .take(200)
            .ifBlank { "documento" }
        val disposicion = if (seMuestra) ContentDisposition.Inline else ContentDisposition.Attachment
        call.response.header(
            HttpHeaders.ContentDisposition,
            disposicion.withParameter(ContentDisposition.Parameters.FileName, nombreSeguro).toString(),
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

/**
 * Lee la parte hasta [tope] bytes. Devuelve `null` apenas se pasa, **sin** terminar de leer.
 *
 * La diferencia con `readBytes()` + comprobar después no es de estilo: es cuánta memoria del
 * server puede reservar un desconocido con una sola petición. Acá el peor caso son [tope] bytes
 * más un bloque, pase lo que pase del otro lado.
 *
 * Se usa `provider()` y no `streamProvider()`, que además está deprecado en Ktor 3.
 */
private suspend fun PartData.FileItem.leerConTope(tope: Long): ByteArray? {
    // Se piden `tope + 1` bytes: si vuelven más de `tope`, el archivo se pasa y no hizo falta
    // leerlo entero para saberlo. El canal se descarta y el resto del cuerpo nunca se materializa.
    val leidos = provider().readRemaining(tope + 1).readByteArray()
    return if (leidos.size > tope) null else leidos
}
