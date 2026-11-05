package com.jvillada.movi.server.routes

import com.jvillada.movi.server.ai.MODELO_DE_EXTRACTOS
import com.jvillada.movi.server.ai.conLaIa
import com.jvillada.movi.server.ai.MODELO_DE_TODOS_LOS_DIAS
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.LecturasDePapeles
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.documentos.Cifrado
import com.jvillada.movi.server.documentos.FallaDeLaClaveDelPdf
import com.jvillada.movi.server.documentos.abrirElPapelGuardado
import com.jvillada.movi.server.documentos.PAPEL_CIFRADO_ILEGIBLE
import com.jvillada.movi.server.documentos.PapelIlegible
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.ComprobanteLeido
import com.jvillada.movi.server.parsing.ContenidoDelPapel
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.QueDiceElPapel
import com.jvillada.movi.server.parsing.StatementParser
import com.jvillada.movi.server.parsing.leerElExtractoDeNu
import com.jvillada.movi.server.parsing.leerLosMovimientosDeBancolombia
import com.jvillada.movi.server.parsing.leerUnExtractoDeSaldo
import com.jvillada.movi.server.parsing.pareceUnExtracto
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.sms.destinosDelDueno
import com.jvillada.movi.server.sms.memoriaDe
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.PedidoDeLoQueSeSabe
import com.jvillada.movi.shared.model.PistasDelPapel
import com.jvillada.movi.shared.model.ProductoDelPapel
import io.ktor.server.request.receive
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.QueEsElPapel
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.categoriaProbablePorElNombre
import com.jvillada.movi.shared.model.documentoDelComprobante
import com.jvillada.movi.shared.model.esCategoriaDelDesembolso
import com.jvillada.movi.shared.model.idDeComprobante
import com.jvillada.movi.shared.model.identificadorDelDestinoEn
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.momentoDelSms
import com.jvillada.movi.shared.model.rotuloDeComprobante
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/*
 * # `POST /api/documents/{id}/leer` — «Compartir con Movi» (Ola 2 · Movi lee papeles)
 *
 * El dueño comparte una captura, un recibo o un PDF con Movi (o lo sube en la web). El cliente lo
 * guarda primero como documento —el mismo `POST /api/documents` de siempre, así el archivo nunca se
 * pierde— y después pide esto, que decide qué es el papel:
 *
 * - **Un comprobante de un solo movimiento**: una propuesta en «Por revisar», como una fila más de
 *   la bandeja de mensajes del banco (ver el KDoc de `Papeles.kt` en :core para por qué ahí). Si el
 *   movimiento ya estaba anotado —el mismo criterio que «¿Ya lo anotaste?» de un SMS
 *   ([coincidenciasDelSms])— no se crea nada y se dice: `yaAnotado`.
 * - **Un extracto**: el importador de siempre ([leerElExtracto] + [conciliarYArchivar]), y la app
 *   abre su pantalla de revisión.
 *
 * **El mismo archivo no se manda dos veces a Claude**: la lectura se guarda por la huella de los
 * bytes (`lecturas_de_papeles`) y la segunda vez se reusa.
 */

/** Sin clave de Anthropic: no se leyó nada, y no es culpa del archivo. */
const val PAPEL_SIN_LLAVE: String =
    "La lectura automática está apagada: al servidor le falta la clave de Anthropic. Tu archivo quedó " +
        "guardado en Documentos; avísale a quien administra Movi."

/**
 * Un PDF con clave de apertura, dicho a una app que no sabe pedirla (sin `?conClave=1`). La que sí
 * sabe recibe `PDF_CON_CLAVE` y pide la clave en la misma fila (vuelta 103, `PdfConClave.kt`).
 */
const val PAPEL_CON_CLAVE: String = com.jvillada.movi.shared.model.FRASE_PAPEL_CON_CLAVE

/** Un PDF escaneado: sin texto adentro, y mandarlo entero a Claude costaría como un extracto. */
// Revisión de la vuelta 130: el texto vive en `:core`, para que la app ofrezca la guía debajo.
const val PAPEL_SIN_TEXTO: String = com.jvillada.movi.shared.model.FRASE_PAPEL_SIN_TEXTO

const val PAPEL_SIN_MOVIMIENTO: String = com.jvillada.movi.shared.model.FRASE_PAPEL_SIN_MOVIMIENTO

const val PAPEL_ILEGIBLE: String =
    "No pudimos entender este archivo. Quedó guardado en Documentos; puedes anotar el movimiento a mano."

const val PAPEL_DE_OTRO_FORMATO: String =
    "Movi lee fotos, capturas de pantalla, PDF y hojas de cálculo del banco. Este archivo quedó " +
        "guardado en Documentos."

const val PAPEL_IMAGEN_NO_SOPORTADA: String =
    "Formato de imagen no soportado. Comparte PNG, JPG, GIF o WEBP (HEIC no se puede leer). El " +
        "archivo quedó guardado en Documentos."

/** Una falla de [leerElPapel], con su status: la ruta la traduce 1:1, como [FallaAlProcesarExtracto]. */
internal class FallaAlLeerElPapel(val status: HttpStatusCode, val mensaje: String) : Exception(mensaje)

/** Lo que hace falta de un documento guardado para leerlo. */
internal class PapelGuardado(val id: String, val nombre: String, val mime: String, val bytes: ByteArray)

fun Route.papelesRoutes() {
    /**
     * Vuelta 129 · **lo que Movi ya sabe de un archivo, por su huella**, antes de subirlo: la hoja de
     * subida de Documentos calcula la SHA-256 del archivo elegido y pregunta; si Movi ya lo leyó (una
     * fila de `lecturas_de_papeles` del dueño con esa huella), contesta el banco, los números y el
     * producto ([PistasDelPapel]) para proponer la cuenta. Si no, 204: la hoja se queda con lo que
     * dice el nombre del archivo.
     *
     * La huella va en el cuerpo (la URL la imprime `CallLogging`). Solo lee lo del dueño: la huella
     * de un archivo de otro no dice nada.
     */
    post("/api/documents/lo-que-se-sabe") {
        val uid = call.userId()
        val huella = runCatching { call.receive<PedidoDeLoQueSeSabe>() }.getOrNull()?.huella?.lowercase()
            ?.takeIf { HUELLA.matches(it) }
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta la huella del archivo")
        val pistas = dbQuery {
            LecturasDePapeles.selectAll()
                .where { (LecturasDePapeles.userId eq uid) and (LecturasDePapeles.huella eq huella) }
                .firstOrNull()
                ?.let { runCatching { pistasDeLaLectura(it[LecturasDePapeles.tipo], Cifrado.descifrarTexto(it[LecturasDePapeles.datos])) }.getOrNull() }
        }
        if (pistas == null) call.respond(HttpStatusCode.NoContent) else call.respond(pistas)
    }

    /**
     * Lee el documento `{id}`. `?anotarAunque=true` crea la propuesta aunque el movimiento parezca
     * ya anotado (el «Anotarlo de todas formas» de la hoja); la lectura guardada se reusa, así que
     * pedirlo no vuelve a llamar a Claude.
     *
     * 404 si el documento no existe o es de otro: el `where` filtra por dueño, igual que el resto
     * de Documentos, y un documento ajeno ni se lee.
     */
    post("/api/documents/{id}/leer") {
        val uid = call.userId()
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Falta el id")
        val anotarAunque = call.request.queryParameters["anotarAunque"] == "true"
        // La app sabe mostrar la propuesta del saldo de un extracto (Skandia, la AFC): ver
        // `leerElExtracto`. Un APK viejo no lo pide y su camino no cambia.
        val conSaldos = call.request.queryParameters["conSaldos"] == "1"
        // Vuelta 129: la app sabe qué hacer con el extracto de una tarjeta (ver `conciliarYArchivar`).
        val conTarjeta = call.request.queryParameters["conTarjeta"] == "1"
        // Revisión de la vuelta 130: la app sabe mostrar la propuesta del extracto de un crédito (ver
        // `leerElExtracto`). Sin esto, el de un crédito se rechaza como siempre.
        val conCreditos = call.request.queryParameters["conCreditos"] == "1"
        // Vuelta 103: la app sabe pedir la clave de un PDF (`PDF_CON_CLAVE`, `CLAVE_INCORRECTA`). Un
        // APK viejo no lo pide y recibe la frase de siempre.
        val conClave = call.request.queryParameters["conClave"] == "1"
        val fila = dbQuery {
            Documents.selectAll()
                .where { (Documents.id eq id) and (Documents.userId eq uid) }
                .firstOrNull()
        } ?: return@post call.respond(HttpStatusCode.NotFound)
        val inicio = System.currentTimeMillis()
        try {
            // La clave del PDF, si es un reintento: en el CUERPO, nunca en la URL (ver `PdfConClave.kt`).
            val pedida = call.claveDelReintento()
            // En claro: la huella (y con ella «el mismo archivo no va dos veces a Claude») se
            // calcula sobre esto, nunca sobre los bytes cifrados, que cambian con cada IV. Un PDF con
            // clave se abre acá y lo que se lee —y se huellea— es el contenido descifrado.
            val bytes = abrirElPapelGuardado(uid, fila[Documents.id], Cifrado.descifrarBytes(fila[Documents.content]), pedida)
            val papel = PapelGuardado(fila[Documents.id], fila[Documents.name], fila[Documents.mimeType], bytes)
            call.respond(
                leerElPapel(uid, papel, anotarAunque, ahora = System.currentTimeMillis(), conSaldos = conSaldos, conTarjeta = conTarjeta, conCreditos = conCreditos) { msg, t ->
                    call.application.log.warn(msg, t)
                },
            )
        } catch (e: FallaDeLaClaveDelPdf) {
            // Vuelta 130: en el tracking, por banco (el del nombre del archivo), sin nada más.
            if (e.codigo == com.jvillada.movi.shared.model.PDF_CON_CLAVE) registrarElExtractoConClave(uid, fila[Documents.name], inicio)
            call.contestarLaFallaDeLaClave(e, conClave, viejo = PAPEL_CON_CLAVE)
        } catch (e: FallaAlLeerElPapel) {
            call.respond(e.status, e.mensaje)
        } catch (e: PapelIlegible) {
            call.application.log.error("[papeles] no se pudo abrir $id: ${e.message}")
            call.respond(HttpStatusCode.ServiceUnavailable, PAPEL_CIFRADO_ILEGIBLE)
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private val papelesLog = org.slf4j.LoggerFactory.getLogger("Papeles")

/**
 * La huella de los bytes: SHA-256 en hex. Es lo que identifica «el mismo archivo». Siempre sobre
 * los bytes **en claro** (ver `Cifrado`): sobre los cifrados, cada copia daría otra huella.
 */
internal fun huellaDelPapel(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Una huella SHA-256 en hexadecimal. */
private val HUELLA = Regex("^[0-9a-f]{64}$")

/**
 * Vuelta 129 · **lo que dice una lectura guardada**, como pistas para proponer la cuenta (ver
 * `POST /api/documents/lo-que-se-sabe`). Un extracto dice su banco, sus números y, si se sabe, qué
 * producto es; un comprobante, su banco y la cuenta propia que nombra.
 */
internal fun pistasDeLaLectura(tipo: String, datos: String): PistasDelPapel? = when (tipo) {
    QueEsElPapel.EXTRACTO.name -> json.decodeFromString(ExtractoLeido.serializer(), datos).let { leido ->
        PistasDelPapel(
            banco = leido.bankName,
            numerosDeCuenta = leido.numerosDeCuenta,
            producto = when {
                leido.tarjeta != null -> ProductoDelPapel.TARJETA
                leido.resumenDeCredito != null -> ProductoDelPapel.CREDITO
                leido.resumenDeSaldo != null -> ProductoDelPapel.CUENTA
                else -> null
            },
        )
    }
    QueEsElPapel.COMPROBANTE.name -> json.decodeFromString(ComprobanteLeido.serializer(), datos).let { leido ->
        PistasDelPapel(
            banco = leido.banco.orEmpty(),
            numerosDeCuenta = listOfNotNull(leido.cuentaPropia?.filter { it.isDigit() }?.takeIf { it.length >= 4 }?.takeLast(4)),
        )
    }
    else -> null
}

private class LecturaGuardada(val documentoId: String, val tipo: String, val datos: String)

/** Ver el comentario del archivo. Lanza [FallaAlLeerElPapel]. */
internal suspend fun leerElPapel(
    uid: String,
    papel: PapelGuardado,
    anotarAunque: Boolean,
    ahora: Long,
    /** La app sabe mostrar la propuesta del saldo de un extracto (ver `leerElExtracto`). */
    conSaldos: Boolean = false,
    /** Vuelta 129: la app sabe qué hacer con el extracto de una tarjeta (ver `conciliarYArchivar`). */
    conTarjeta: Boolean = false,
    /** Revisión de la vuelta 130: la app sabe mostrar la propuesta del extracto de un crédito. */
    conCreditos: Boolean = false,
    log: (String, Throwable) -> Unit,
): LecturaDelPapel {
    val huella = huellaDelPapel(papel.bytes)
    val guardada = dbQuery {
        LecturasDePapeles.selectAll()
            .where { (LecturasDePapeles.userId eq uid) and (LecturasDePapeles.huella eq huella) }
            .firstOrNull()
            ?.let {
                LecturaGuardada(it[LecturasDePapeles.documentoId], it[LecturasDePapeles.tipo], Cifrado.descifrarTexto(it[LecturasDePapeles.datos]))
            }
    }

    if (guardada != null) {
        // La segunda vez: nada viaja a Claude.
        return when (guardada.tipo) {
            QueEsElPapel.COMPROBANTE.name -> propuestaDelComprobante(
                uid, guardada.documentoId, papel.nombre,
                json.decodeFromString(ComprobanteLeido.serializer(), guardada.datos), anotarAunque, ahora,
            )
            else -> {
                val guardado = json.decodeFromString(ExtractoLeido.serializer(), guardada.datos)
                // Vuelta 129: una lectura de Nu guardada antes de que el lector dijera la tarjeta no
                // la trae. Se relee sin IA (es barato) y se guarda la nueva; si no es el extracto de
                // la tarjeta de Nu, queda la de siempre.
                val leido = if (guardado.tarjeta == null && guardado.bankName == "Nu" && !guardado.esFamirios) {
                    releerElExtractoDeNu(uid, huella, papel, conSaldos) ?: guardado
                } else guardado
                // La lectura guardada es de un saldo y quien pregunta no lo sabe mostrar: lo de
                // antes, un papel sin movimientos (y no una revisión vacía).
                if (leido.resumenDeSaldo != null && !conSaldos) {
                    throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_SIN_MOVIMIENTO)
                }
                // Lo mismo con la de un crédito y quien no la sabe mostrar.
                if (leido.resumenDeCredito != null && !conCreditos) {
                    throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_SIN_MOVIMIENTO)
                }
                LecturaDelPapel(
                    documentoId = papel.id,
                    que = QueEsElPapel.EXTRACTO,
                    extracto = conciliarYArchivar(uid, papel.nombre, papel.mime, papel.bytes, leido, log, documentoLeido = papel.id, conTarjeta = conTarjeta),
                )
            }
        }
    }

    return when (val clasificado = clasificar(papel, conSaldos, conCreditos)) {
        is Clasificado.Comprobante -> {
            dbQuery { guardarLectura(uid, huella, papel.id, QueEsElPapel.COMPROBANTE, json.encodeToString(ComprobanteLeido.serializer(), clasificado.leido), MODELO_DE_TODOS_LOS_DIAS) }
            propuestaDelComprobante(uid, papel.id, papel.nombre, clasificado.leido, anotarAunque, ahora)
        }
        Clasificado.Extracto -> {
            val leido = try {
                leerElExtracto(uid, papel.nombre, papel.mime, papel.bytes, mimeConfiable = true, conCreditos = conCreditos, conSaldos = conSaldos)
            } catch (e: FallaAlProcesarExtracto) {
                // «Extracto sin movimientos» está escrito para quien SUBIÓ un extracto («revisa que
                // sea el del detalle»). Acá el dueño compartió un papel y fue Movi quien decidió que
                // era un extracto: lo honesto es decir que el papel no muestra movimientos.
                if (e.mensaje == EXTRACTO_SIN_MOVIMIENTOS) {
                    throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_SIN_MOVIMIENTO)
                }
                throw FallaAlLeerElPapel(e.status, e.mensaje)
            }
            // **Un extracto vacío no abre la revisión.** Una revisión en «0 nuevas · 0
            // coincidencias» se lee como «este mes ya estaba conciliado». Pasaba con el extracto del
            // crédito del vehículo: cuota, saldo y tasa, sin lista de movimientos, que ni
            // `detectDocumentType` ni el clasificador reconocían. `leerElExtracto` ya lo frena hoy
            // (ver `fallaDeLaLectura`); esto es la red por si ese camino cambia, y además no deja
            // guardada una lectura vacía que tape un reintento.
            if (leido.filas.isEmpty() && !leido.esFamirios && leido.resumenDeSaldo == null && leido.resumenDeCredito == null) {
                throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_SIN_MOVIMIENTO)
            }
            dbQuery {
                guardarLectura(
                    uid, huella, papel.id, QueEsElPapel.EXTRACTO, json.encodeToString(ExtractoLeido.serializer(), leido),
                    when {
                        leido.esFamirios -> "famirios"
                        // Leído con reglas: ningún modelo lo vio.
                        leido.resumenDeSaldo != null || leido.resumenDeCredito != null -> "sin-ia"
                        else -> MODELO_DE_EXTRACTOS
                    },
                )
            }
            LecturaDelPapel(
                documentoId = papel.id,
                que = QueEsElPapel.EXTRACTO,
                extracto = conciliarYArchivar(uid, papel.nombre, papel.mime, papel.bytes, leido, log, documentoLeido = papel.id, conTarjeta = conTarjeta),
            )
        }
    }
}

/**
 * Vuelta 129 · **relee sin IA** el extracto de la tarjeta de Nu de [papel] y reemplaza la lectura
 * guardada con [huella]. `null` si el papel no es ese extracto (o no se pudo leer): nunca llama a
 * Claude, porque solo sigue si `leerElExtractoDeNu` lo reconoce, y ese lector va antes que la IA.
 */
private suspend fun releerElExtractoDeNu(uid: String, huella: String, papel: PapelGuardado, conSaldos: Boolean): ExtractoLeido? {
    val texto = runCatching {
        StatementParser.extractText(papel.bytes, nombreParaExtraerTexto(papel.nombre, papel.mime, mimeEsConfiable = true))
    }.getOrNull() ?: return null
    if (leerElExtractoDeNu(texto) == null) return null
    val nuevo = try {
        // Revisión de la vuelta 130: releer no es una lectura nueva del dueño, no se cuenta otra vez.
        leerElExtracto(uid, papel.nombre, papel.mime, papel.bytes, mimeConfiable = true, conSaldos = conSaldos, registrar = false)
    } catch (e: FallaAlProcesarExtracto) {
        return null
    }
    if (nuevo.tarjeta == null) return null
    dbQuery {
        LecturasDePapeles.update({ (LecturasDePapeles.userId eq uid) and (LecturasDePapeles.huella eq huella) }) {
            it[datos] = Cifrado.cifrarTexto(json.encodeToString(ExtractoLeido.serializer(), nuevo))
            it[modelo] = "sin-ia"
            it[leidoEn] = System.currentTimeMillis()
        }
    }
    return nuevo
}

private fun Transaction.guardarLectura(uid: String, huella: String, documentoId: String, tipo: QueEsElPapel, datos: String, modelo: String) {
    // Dos lecturas del mismo archivo a la vez: la segunda encuentra la fila y no la pisa.
    val yaEsta = LecturasDePapeles.selectAll()
        .where { (LecturasDePapeles.userId eq uid) and (LecturasDePapeles.huella eq huella) }
        .any()
    if (yaEsta) return
    LecturasDePapeles.insert {
        it[userId] = uid
        it[LecturasDePapeles.huella] = huella
        it[LecturasDePapeles.documentoId] = documentoId
        it[LecturasDePapeles.tipo] = tipo.name
        // La única puerta de escritura de `lecturas_de_papeles.datos`: cifrado si hay clave.
        it[LecturasDePapeles.datos] = Cifrado.cifrarTexto(datos)
        it[LecturasDePapeles.modelo] = modelo
        it[leidoEn] = System.currentTimeMillis()
    }
}

private sealed interface Clasificado {
    class Comprobante(val leido: ComprobanteLeido) : Clasificado
    data object Extracto : Clasificado
}

/**
 * **Qué es el papel**, gastando lo menos posible:
 *
 * - una imagen va a Haiku (una llamada barata que decide y, si es un comprobante, ya lo lee);
 * - un PDF se lee primero con PDFBox: si ya tiene la forma de una lista de movimientos
 *   ([pareceUnExtracto]) va derecho al importador sin preguntarle a nadie; si no, su TEXTO va a
 *   Haiku — nunca el PDF entero;
 * - una hoja de cálculo o un CSV es un extracto (o un Famirios), sin preguntar;
 * - el extracto de Skandia o de la AFC de Davibank (si la app sabe mostrarlo) es un extracto, sin
 *   preguntar: se lee sin IA (ver `leerUnExtractoDeSaldo`); lo mismo la lista de movimientos de una
 *   cuenta de Bancolombia (ver `leerLosMovimientosDeBancolombia`) y el extracto de la tarjeta de Nu
 *   (ver `leerElExtractoDeNu`).
 */
private suspend fun clasificar(papel: PapelGuardado, conSaldos: Boolean = false, conCreditos: Boolean = false): Clasificado {
    if (esImagenParaExtraer(papel.nombre, papel.mime, mimeEsConfiable = true)) {
        val mime = ClaudeStatementParser.supportedImageMime(papel.mime, papel.nombre)
            ?: throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_IMAGEN_NO_SOPORTADA)
        return deLoQueDijo(preguntarQueEs(ContenidoDelPapel.Imagen(papel.bytes, mime)))
    }
    val paraExtraer = nombreParaExtraerTexto(papel.nombre, papel.mime, mimeEsConfiable = true)
    when (paraExtraer.substringAfterLast('.', "").lowercase()) {
        "csv", "xls", "xlsx" -> return Clasificado.Extracto
        "pdf" -> Unit
        else -> throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_DE_OTRO_FORMATO)
    }
    val texto = try {
        StatementParser.extractText(papel.bytes, paraExtraer)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw FallaAlLeerElPapel(
            HttpStatusCode.UnprocessableEntity,
            if (tieneContrasena(e)) PAPEL_CON_CLAVE else "$LECTURA_FALLO El archivo quedó guardado en Documentos.",
        )
    }
    if (texto.isBlank()) throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_SIN_TEXTO)
    // El extracto de Skandia o de la AFC se reconoce por su texto, sin preguntarle a nadie: no
    // muestra movimientos, y Haiku diría «nada» de un papel que sí dice cuánto hay.
    if (conSaldos && leerUnExtractoDeSaldo(texto) != null) return Clasificado.Extracto
    // Revisión de la vuelta 130: el extracto de un crédito de Bancolombia (libre inversión, libranza)
    // también, sin preguntarle a Haiku (que diría «nada»: no muestra movimientos). Solo si la app sabe
    // mostrar la propuesta; `leerElExtracto` lo lee con el mismo lector.
    if (conCreditos && StatementParser.detectDocumentType(texto) == com.jvillada.movi.server.parsing.StatementDocumentType.LOAN_SUMMARY &&
        LectorDePapeles.actual.leerElExtractoDeUnCredito(texto) != null
    ) return Clasificado.Extracto
    // Los movimientos de una cuenta de Bancolombia también: se leen sin IA (ver
    // `leerLosMovimientosDeBancolombia`), y no hace falta preguntarle a Haiku qué son.
    if (leerLosMovimientosDeBancolombia(texto) != null) return Clasificado.Extracto
    // Vuelta 94: el extracto de la tarjeta de Nu, igual (ver `leerElExtractoDeNu`).
    if (leerElExtractoDeNu(texto) != null) return Clasificado.Extracto
    if (pareceUnExtracto(texto)) return Clasificado.Extracto
    return deLoQueDijo(preguntarQueEs(ContenidoDelPapel.Texto(texto)))
}

/**
 * La clasificación, con la falla de la cuenta de Anthropic dicha como tal: 503 con el código
 * (`IA_SIN_CREDITO` / `IA_NO_DISPONIBLE`), que la app traduce a «La lectura con IA no está
 * disponible ahora…». El documento ya quedó guardado antes de esto, y no se guarda ninguna lectura.
 */
private suspend fun preguntarQueEs(contenido: ContenidoDelPapel): QueDiceElPapel =
    conLaIa("clasificar un papel", { throw FallaAlLeerElPapel(HttpStatusCode.ServiceUnavailable, it.codigo) }) {
        LectorDePapeles.actual.queEs(contenido)
    }

/** ¿La falla al abrir el PDF es su contraseña? PDFBox lanza [InvalidPasswordException]. */
private fun tieneContrasena(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.take(5).any { it is InvalidPasswordException }

private fun deLoQueDijo(dijo: QueDiceElPapel): Clasificado = when (dijo) {
    is QueDiceElPapel.Comprobante -> Clasificado.Comprobante(dijo.leido)
    QueDiceElPapel.Extracto -> Clasificado.Extracto
    QueDiceElPapel.SinMovimiento -> throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_SIN_MOVIMIENTO)
    QueDiceElPapel.SinLlave -> throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_SIN_LLAVE)
    QueDiceElPapel.Ilegible -> throw FallaAlLeerElPapel(HttpStatusCode.UnprocessableEntity, PAPEL_ILEGIBLE)
}

/**
 * **La propuesta de un comprobante en «Por revisar».** Una fila de `sms_messages` con el id
 * `cmp_<documento>` —idempotente: compartir dos veces el mismo papel no deja dos propuestas— salvo
 * que el movimiento ya esté anotado y no se haya pedido [anotarAunque].
 */
private suspend fun propuestaDelComprobante(
    uid: String,
    documentoId: String,
    nombreDelArchivo: String,
    leido: ComprobanteLeido,
    anotarAunque: Boolean,
    ahora: Long,
): LecturaDelPapel {
    val texto = textoDelComprobante(leido)
    val cuando = cuandoDelComprobante(leido, ahora)
    val smsId = idDeComprobante(documentoId)
    val parsed = dbQuery { leidoConLoQueMoviSabe(uid, leido, texto) }

    val ya = dbQuery {
        SmsMessages.selectAll()
            .where { (SmsMessages.id eq smsId) and (SmsMessages.userId eq uid) }
            .firstOrNull()
            ?.let { it[SmsMessages.state] to it[SmsMessages.time] }
    }
    if (ya != null) {
        return LecturaDelPapel(
            documentoId = documentoId, que = QueEsElPapel.COMPROBANTE, porRevisarId = smsId,
            yaEstabaEnLaBandeja = true, estadoEnLaBandeja = ya.first, leido = parsed, cuando = ya.second,
        )
    }

    if (!anotarAunque) {
        val momento = momentoDelSms(cuando, ahora)
        val margen = DIAS_PARA_COINCIDIR * 86_400_000L
        val eventos = dbQuery { loadEventsBetween(uid, momento - margen, momento + margen + 1) }
        val iguales = coincidenciasDelSms(parsed, momento, eventos)
        if (iguales.isNotEmpty()) {
            return LecturaDelPapel(
                documentoId = documentoId, que = QueEsElPapel.COMPROBANTE, leido = parsed, cuando = cuando, yaAnotado = iguales,
            )
        }
    }

    dbQuery {
        val existe = SmsMessages.selectAll()
            .where { (SmsMessages.id eq smsId) and (SmsMessages.userId eq uid) }
            .any()
        if (!existe) {
            SmsMessages.insert {
                it[id] = smsId
                it[userId] = uid
                it[time] = cuando
                it[bank] = rotuloDeComprobante(nombreDelArchivo)
                it[text] = texto
                // Como el sync y el correo: llega «por confirmar»; /confirm y /ignore lo mueven.
                it[state] = SMS_STATE_PENDING
                it[det] = ""
            }
        }
        LecturasDePapeles.update({ (LecturasDePapeles.userId eq uid) and (LecturasDePapeles.documentoId eq documentoId) }) {
            it[porRevisarId] = smsId
        }
    }
    return LecturaDelPapel(documentoId = documentoId, que = QueEsElPapel.COMPROBANTE, porRevisarId = smsId, leido = parsed, cuando = cuando)
}

/**
 * **Lo que Movi propone para un comprobante**, con la misma memoria y las mismas cuentas de otros que
 * un SMS: primero lo que el dueño anotó antes para ese destinatario, después el nombre de una cuenta
 * guardada (ver el orden en `/api/sms/{id}/parse`).
 */
internal fun Transaction.leidoConLoQueMoviSabe(uid: String, leido: ComprobanteLeido, texto: String): ParsedSms =
    conLaMemoriaYLosTerceros(parsedDelComprobante(leido, texto), texto, memoriaDe(uid), destinosDelDueno(uid))

/**
 * La propuesta de la bandeja para el comprobante [smsId], leída de la lectura guardada. `null` si no
 * es un comprobante o su lectura no está (entonces la ruta cae al parser de SMS sobre el texto).
 */
internal fun Transaction.parsedDeUnComprobante(uid: String, smsId: String, texto: String): ParsedSms? {
    documentoDelComprobante(smsId) ?: return null
    val datos = LecturasDePapeles.select(LecturasDePapeles.datos)
        .where { (LecturasDePapeles.userId eq uid) and (LecturasDePapeles.porRevisarId eq smsId) }
        .firstOrNull()?.get(LecturasDePapeles.datos) ?: return null
    // Una lectura que no se puede abrir (cifrada con otra clave) cae al parser de SMS sobre el
    // texto, igual que una lectura que no está: la bandeja sigue funcionando.
    val claro = try {
        Cifrado.descifrarTexto(datos)
    } catch (e: PapelIlegible) {
        papelesLog.error("[papeles] no se pudo abrir la lectura de $smsId: ${e.message}")
        return null
    }
    val leido = runCatching { json.decodeFromString(ComprobanteLeido.serializer(), claro) }.getOrNull() ?: return null
    return leidoConLoQueMoviSabe(uid, leido, texto)
}

/**
 * **El enlace entre el papel y el movimiento**, al confirmar la propuesta: la lectura recuerda con
 * qué movimiento se confirmó, y el documento queda colgado de la cuenta de ese movimiento (como un
 * extracto queda colgado de la cuenta contra la que se importó). Un [eventoId] ajeno no toca nada:
 * se busca filtrando por dueño.
 */
internal fun Transaction.enlazarElComprobante(uid: String, smsId: String, eventoId: String) {
    val documentoId = documentoDelComprobante(smsId) ?: return
    val cuenta = Events.select(Events.accountId)
        .where { (Events.id eq eventoId) and (Events.userId eq uid) }
        .firstOrNull()?.get(Events.accountId) ?: return
    LecturasDePapeles.update({ (LecturasDePapeles.userId eq uid) and (LecturasDePapeles.porRevisarId eq smsId) }) {
        it[LecturasDePapeles.eventoId] = eventoId
    }
    Documents.update({ (Documents.id eq documentoId) and (Documents.userId eq uid) }) {
        it[Documents.accountId] = cuenta
    }
}

/** El nombre del movimiento: a quién, o el número o la llave si es lo único que dice el papel. */
internal fun nombreDelComprobante(leido: ComprobanteLeido): String =
    leido.comercio.trim().takeIf { it.isNotEmpty() }
        ?: leido.cuentaDestino?.let { "Transferencia a la cuenta *$it" }
        ?: leido.llave?.let { "Transferencia · llave $it" }
        ?: leido.concepto?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (leido.tipo == TransactionType.INCOME) "Transferencia recibida" else "Movimiento"

/**
 * La categoría que Movi propone. La del modelo solo si es una del catálogo y no una reservada —«Pago
 * de tarjeta» sí, sobre un gasto, porque es una regla de plata que el modelo sabe reconocer—; si no,
 * la misma red de seguridad que un SMS.
 */
internal fun categoriaDelComprobante(leido: ComprobanteLeido, nombre: String): String {
    val delModelo = leido.categoria?.trim().orEmpty()
    if (delModelo.equals(CARD_PAYMENT_CATEGORY, ignoreCase = true) && leido.tipo == TransactionType.EXPENSE) {
        return CARD_PAYMENT_CATEGORY
    }
    val delCatalogo = PREDEFINED_CATEGORIES.firstOrNull { it.name.equals(delModelo, ignoreCase = true) }?.name
        ?.takeIf { !isReservedCategory(it) && !esCategoriaDelDesembolso(it) }
    if (delCatalogo != null) return delCatalogo
    if (leido.tipo == TransactionType.INCOME) return "Transferencia"
    return categoriaProbablePorElNombre(nombre) ?: SIN_CATEGORIA
}

/** Lo leído, en la forma de una propuesta de la bandeja. Sin memoria ni cuentas de otros todavía. */
internal fun parsedDelComprobante(leido: ComprobanteLeido, texto: String): ParsedSms {
    val nombre = nombreDelComprobante(leido)
    val categoria = categoriaDelComprobante(leido, nombre)
    val identificador = if (categoria == CARD_PAYMENT_CATEGORY) null else identificadorDelDestinoEn(texto)
    return ParsedSms(
        amount = leido.monto,
        merchant = nombre,
        type = leido.tipo,
        category = categoria,
        currency = leido.moneda,
        identificadorDelDestino = identificador?.valor,
        identificadorEsLlave = identificador?.tipo == TipoDeIdentificador.LLAVE,
    )
}

/** «$138.600», «USD 20,50». Como lo escribe el banco, para que el texto se lea igual que un SMS. */
internal fun montoDelComprobante(monto: Double, moneda: String): String =
    if (moneda == "USD") {
        val centavos = (monto * 100).roundToLong()
        val resto = centavos % 100
        "USD " + miles(centavos / 100) + if (resto == 0L) "" else "," + resto.toString().padStart(2, '0')
    } else {
        "$" + miles(monto.roundToLong())
    }

private fun miles(n: Long): String =
    n.toString().reversed().chunked(3).joinToString(".").reversed()

/**
 * **El texto de la propuesta**, en la forma de un aviso del banco: «Bancolombia: Pagaste $138.600 a
 * Coomeva desde tu cuenta *8133 el 30/09/2026 a las 14:05. Pago PSE.»
 *
 * Es lo que el dueño lee en la tarjeta, y está escrito así a propósito: las piezas que la app ya
 * sabe leer de un SMS —la cuenta propia (`*8133`, para elegir la cuenta), la cuenta de destino («a
 * la cuenta *3197…», para las cuentas de otros), la llave— quedan en la forma que reconocen. El
 * concepto pierde los números largos: una referencia de pago no puede leerse como una cuenta.
 */
internal fun textoDelComprobante(leido: ComprobanteLeido): String {
    val monto = montoDelComprobante(leido.monto, leido.moneda)
    val banco = leido.banco?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it: " }.orEmpty()
    val comercio = leido.comercio.trim()
    val fecha = leido.fecha?.let { f ->
        val dia = runCatching { LocalDate.parse(f).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrNull()
        dia?.let { " el $it" + (leido.hora?.let { h -> " a las $h" } ?: "") }
    }.orEmpty()
    val concepto = leido.concepto?.replace(Regex("""\d{4,}"""), "")?.trim()?.trimEnd('.')
        ?.takeIf { it.isNotEmpty() }?.let { " $it." }.orEmpty()
    return if (leido.tipo == TransactionType.INCOME) {
        val deQuien = comercio.takeIf { it.isNotEmpty() }?.let { " de $it" }.orEmpty()
        val en = leido.cuentaPropia?.let { " en tu cuenta *$it" }.orEmpty()
        "${banco}Recibiste $monto$deQuien$en$fecha.$concepto"
    } else {
        val entre = comercio.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
        val aQuien = when {
            leido.cuentaDestino != null -> " a la cuenta *${leido.cuentaDestino}$entre"
            leido.llave != null -> " a la llave ${leido.llave}$entre"
            comercio.isNotEmpty() -> " a $comercio"
            else -> ""
        }
        val desde = leido.cuentaPropia?.let { " desde tu cuenta *$it" }.orEmpty()
        "${banco}Pagaste $monto$aQuien$desde$fecha.$concepto"
    }
}

/**
 * **Cuándo pasó**, en el formato de la bandeja (`yyyy-MM-dd HH:mm`, hora de Bogotá). Sin hora, el
 * mediodía —como una fecha elegida a mano—; sin fecha, ahora: el papel no dice cuándo y el dueño lo
 * acaba de compartir.
 */
internal fun cuandoDelComprobante(leido: ComprobanteLeido, ahora: Long): String {
    val fecha = leido.fecha?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (fecha != null) return "$fecha ${leido.hora ?: "12:00"}"
    return Instant.ofEpochMilli(ahora).atZone(AppClock.zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
