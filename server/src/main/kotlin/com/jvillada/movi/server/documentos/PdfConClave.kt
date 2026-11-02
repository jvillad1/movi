package com.jvillada.movi.server.documentos

import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.db.ClavesDeExtractos
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.PdfsAbiertos
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.APERTURAS_DE_PDF_CON_CLAVE_POR_HORA
import com.jvillada.movi.shared.model.CLAVE_INCORRECTA
import com.jvillada.movi.shared.model.ClaveDelPdf
import com.jvillada.movi.shared.model.DEMASIADOS_INTENTOS_DE_CLAVE
import com.jvillada.movi.shared.model.INTENTOS_DE_CLAVE_POR_HORA
import com.jvillada.movi.shared.model.MAX_LARGO_DE_LA_CLAVE_DEL_PDF
import com.jvillada.movi.shared.model.PDF_CON_CLAVE
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdfparser.PDFParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.PDEncryption
import org.apache.pdfbox.pdmodel.encryption.StandardDecryptionMaterial
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/*
 * # El PDF con clave se abre en Movi (vuelta 103)
 *
 * Los extractos colombianos casi siempre traen clave de apertura (suele ser la cédula). Movi la pide
 * en la app y abre el PDF acá:
 *
 * 1. **¿Pide clave?** Solo un PDF con clave de **usuario** (la de apertura). Uno con clave de
 *    propietario sola (permisos de imprimir o copiar) se abre sin pedir nada, como siempre: PDFBox lo
 *    carga y el lector de texto lo lee.
 * 2. **Con la clave del cuerpo** (el reintento de la app, `ClaveDelPdf`): se prueba —con un tope de
 *    [INTENTOS_DE_CLAVE_POR_HORA] por documento y otro de [APERTURAS_DE_PDF_CON_CLAVE_POR_HORA] formas
 *    probadas por usuario, que cuenta también las de la clave recordada— y, si abre, el contenido **descifrado** (sin ninguna
 *    protección: `setAllSecurityToBeRemoved`) reemplaza al original en `documents.content`, cifrado en
 *    reposo con la clave de Movi como todo papel ([Cifrado]). Así la descarga, el lector y un
 *    reintento no vuelven a pedir clave.
 * 3. **Sin clave en el cuerpo pero con una recordada**: se prueba sola. Si ninguna variante abre, se
 *    pide como en el paso 2. **Sin nada recordado no se prueba nada a ciegas.**
 *
 * ## Las variantes de una clave
 *
 * Tal cual (y sin espacios a los lados), solo los dígitos, y los últimos 4 y los últimos 6 de la parte
 * numérica: «1.037.585.071» abre un PDF cuya clave es «1037585071», «5071» o «585071». Se usan para la
 * recordada y también para la que se acaba de escribir: no es probar a ciegas, son formas de lo mismo
 * que el dueño escribió.
 *
 * ## La huella (la decisión)
 *
 * - **La de la lectura** (`lecturas_de_papeles`) es la del contenido **descifrado**, que es el que se
 *   lee y el que queda guardado: un reintento, la descarga y el `reusar` ven siempre esos bytes.
 * - **La del original** (los bytes con clave, como los tiene el dueño) se guarda aparte, en
 *   `pdfs_abiertos`, junto al documento donde quedó abierto. La usa el `reusar` de
 *   `POST /api/documents`: el mismo archivo cifrado subido otra vez devuelve ese documento, ya
 *   abierto, en vez de un duplicado que pediría la clave de nuevo.
 *
 * ## La clave es un secreto del dueño
 *
 * Nunca se loguea (ni la clave ni el cuerpo que la trae: [claveDelCuerpo] no repite el error de
 * parseo, que de kotlinx incluye el JSON), nunca se devuelve y solo se guarda si el dueño marcó
 * «Recordarla», siempre cifrada y con su id adentro ([recordarLaClave]). Sin
 * `DOCUMENTS_ENCRYPTION_KEY` no se guarda: [sePuedeRecordar] es `false` y la app no ofrece la casilla.
 */

private val log = LoggerFactory.getLogger("PdfConClave")

/**
 * Una falla al abrir el PDF, con su status y el código de `:core` (`PDF_CON_CLAVE`…). [frase]: una
 * falla que no es de la clave (el PDF abrió pero no se pudo reescribir) y se dice igual a toda app.
 */
internal class FallaDeLaClaveDelPdf(val status: HttpStatusCode, val codigo: String, val frase: String? = null) : Exception(codigo)

/** La ventana de los topes de intentos: una hora. */
private const val VENTANA_DE_INTENTOS_MS = 60 * 60_000L

/** Hasta dónde se busca la firma `%PDF-`: la norma tolera basura antes, y los lectores miran 1 KB. */
private const val MARGEN_DE_LA_FIRMA = 1024

/** Un cuerpo con la clave no pesa más que esto; lo demás no se lee. */
internal const val MAX_BYTES_DEL_CUERPO_CON_CLAVE: Int = 4 * 1024

/** La clave abrió el PDF pero no se pudo reescribir sin protección: el archivo sigue como estaba. */
internal const val PDF_ABIERTO_SIN_GUARDAR: String =
    "No se pudo leer este PDF. El archivo quedó guardado en Documentos."

private val FIRMA_PDF = "%PDF-".toByteArray(Charsets.US_ASCII)
private val MARCA_DE_CIFRADO = "/Encrypt".toByteArray(Charsets.US_ASCII)

private val json = Json { ignoreUnknownKeys = true }

/**
 * **El PDF leído una sola vez** (revisión de seguridad de la vuelta 103): el diccionario de cifrado y
 * el `/ID` del trailer. Con eso cada clave se comprueba contra el manejador de cifrado de PDFBox
 * ([abre]) sin volver a cargar el documento; solo la clave que abre lo carga de verdad, una vez, para
 * reescribirlo sin protección.
 */
internal class SondaDelPdf(private val cifrado: PDEncryption, private val id: COSArray?) {
    private val manejador = cifrado.securityHandler

    /** ¿[clave] abre el PDF (como clave de usuario o de propietario)? Sin cargar nada más. */
    fun abre(clave: String): Boolean = try {
        manejador.prepareForDecryption(cifrado, id, StandardDecryptionMaterial(clave))
        true
    } catch (e: InvalidPasswordException) {
        false
    }

    /** ¿Hace falta la clave de apertura? Uno con clave de propietario sola abre con la clave vacía. */
    val pideClave: Boolean by lazy { !abre("") }
}

/** El parser de PDFBox, cortado justo donde descifraría: solo toma el diccionario de cifrado y el `/ID`. */
private class LectorDelCifrado(bytes: ByteArray) : PDFParser(RandomAccessReadBuffer(bytes), "") {
    var cifrado: PDEncryption? = null
    var id: COSArray? = null

    override fun prepareDecryption() {
        val dic = document.encryptionDictionary ?: return
        cifrado = PDEncryption(dic)
        id = document.documentID
    }

    fun leer() {
        try {
            parsePDFHeader()
            retrieveTrailer()
        } finally {
            document.close()
        }
    }
}

internal object PdfConClave {
    /** ¿Son los bytes de un PDF? Por la firma, no por el nombre (que el dueño edita). */
    fun esPdf(bytes: ByteArray): Boolean = indice(bytes, FIRMA_PDF, hasta = MARGEN_DE_LA_FIRMA) >= 0

    /**
     * ¿Puede estar cifrado? Un PDF cifrado nombra `/Encrypt` en su trailer (o en el diccionario de su
     * tabla de referencias, que no se comprime). Es el filtro barato: sin la marca el PDF no se lee
     * una vez de más, y una marca de más solo lleva a la comprobación de verdad ([sondear]).
     */
    fun puedeEstarCifrado(bytes: ByteArray): Boolean = esPdf(bytes) && indice(bytes, MARCA_DE_CIFRADO) >= 0

    /**
     * Lee el PDF **una vez** y devuelve con qué comprobar claves, o `null` si no está cifrado, si su
     * cifrado no es el de clave (`/Standard`) o si ni siquiera se deja leer: ese sigue el camino de
     * siempre, que explica que no se pudo leer.
     */
    fun sondear(bytes: ByteArray): SondaDelPdf? {
        if (!puedeEstarCifrado(bytes)) return null
        return try {
            val lector = LectorDelCifrado(bytes)
            lector.leer()
            val cifrado = lector.cifrado ?: return null
            if (cifrado.filter != "Standard") return null
            SondaDelPdf(cifrado, lector.id)
        } catch (e: Exception) {
            null
        }
    }

    /** ¿Hace falta la clave de apertura? Ver [SondaDelPdf.pideClave]. */
    fun pideClave(bytes: ByteArray): Boolean = sondear(bytes)?.pideClave == true

    /**
     * Cómo se escribe el PDF ya abierto. Una costura para las pruebas: así se prueba que una falla al
     * reescribir contesta «no se pudo leer» y no un 500.
     */
    internal var escribir: (PDDocument) -> ByteArray = { doc -> ByteArrayOutputStream().also { doc.save(it) }.toByteArray() }

    /**
     * El PDF abierto con [clave] (que [SondaDelPdf.abre] ya aceptó) y sin ninguna protección: la única
     * carga completa del documento. Si algo falla acá —la clave sirvió pero el PDF no se deja
     * reescribir— es [PDF_ABIERTO_SIN_GUARDAR] (422), nunca un 500, y nada se guarda.
     */
    fun abrirCon(bytes: ByteArray, clave: String): ByteArray = try {
        Loader.loadPDF(bytes, clave).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            escribir(doc)
        }
    } catch (e: Exception) {
        log.warn("[pdf-con-clave] la clave abrió el PDF pero no se pudo reescribir: ${e.javaClass.simpleName}")
        throw FallaDeLaClaveDelPdf(HttpStatusCode.UnprocessableEntity, PDF_ABIERTO_SIN_GUARDAR, frase = PDF_ABIERTO_SIN_GUARDAR)
    }

    /** Las formas de [clave] que se prueban, en orden y sin repetir. Ver el comentario del archivo. */
    fun variantes(clave: String): List<String> {
        val digitos = clave.filter { it.isDigit() }
        return listOfNotNull(
            clave,
            clave.trim(),
            digitos,
            digitos.takeIf { it.length > 4 }?.takeLast(4),
            digitos.takeIf { it.length > 6 }?.takeLast(6),
        ).filter { it.isNotEmpty() }.distinct()
    }

    /** ¿Se puede ofrecer «Recordarla»? Solo si Movi cifra: no se guardan claves en claro, jamás. */
    fun sePuedeRecordar(): Boolean = Cifrado.hayClave()

    private fun indice(bytes: ByteArray, aguja: ByteArray, hasta: Int = Int.MAX_VALUE): Int {
        val fin = minOf(bytes.size - aguja.size, hasta)
        var i = 0
        while (i <= fin) {
            var j = 0
            while (j < aguja.size && bytes[i + j] == aguja[j]) j++
            if (j == aguja.size) return i
            i++
        }
        return -1
    }
}

/** SHA-256 en hex, la misma forma que `huellaDelPapel`. */
internal fun huellaDeLosBytes(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * La clave del cuerpo de un reintento, o `null` si no vino cuerpo. Lee hasta
 * [MAX_BYTES_DEL_CUERPO_CON_CLAVE]. Un cuerpo que no se entiende es un 400 **sin** el detalle del
 * parseo (traería el JSON, o sea la clave). Lanza [FallaDeLaClaveDelPdf].
 */
internal fun claveDelCuerpo(cuerpo: String): ClaveDelPdf? {
    if (cuerpo.isBlank()) return null
    return try {
        json.decodeFromString(ClaveDelPdf.serializer(), cuerpo)
    } catch (e: Exception) {
        throw FallaDeLaClaveDelPdf(HttpStatusCode.BadRequest, "No entendimos la clave que llegó.")
    }
}

/**
 * La primera forma de [clave] que abre el PDF, o `null`. **Cada forma probada gasta una del tope por
 * usuario** ([APERTURAS_DE_PDF_CON_CLAVE_POR_HORA]), la escriba el dueño o la pruebe Movi con la
 * recordada: así nadie adivina una cédula subiendo PDFs. Pasado el tope, 429 sin probar más.
 */
private fun formaQueAbre(uid: String, sonda: SondaDelPdf, clave: String): String? {
    for (forma in PdfConClave.variantes(clave)) {
        if (!RateLimiter.allow("clave-del-pdf-usuario:$uid", APERTURAS_DE_PDF_CON_CLAVE_POR_HORA, VENTANA_DE_INTENTOS_MS)) {
            throw FallaDeLaClaveDelPdf(HttpStatusCode.TooManyRequests, DEMASIADOS_INTENTOS_DE_CLAVE)
        }
        if (sonda.abre(forma)) return forma
    }
    return null
}

/**
 * **Los bytes que se van a leer** del documento [documentoId], abriéndolo si tiene clave. Ver el
 * comentario del archivo. Lanza [FallaDeLaClaveDelPdf] con `PDF_CON_CLAVE`, `CLAVE_INCORRECTA`,
 * `DEMASIADOS_INTENTOS_DE_CLAVE` o [PDF_ABIERTO_SIN_GUARDAR]. Lo que no es un PDF con clave de apertura
 * sale tal cual, sin tocar la base. El PDF se lee una vez para comprobar las claves ([SondaDelPdf]) y
 * se carga entero una sola vez más, con la que abrió.
 */
internal suspend fun abrirElPapelGuardado(
    uid: String,
    documentoId: String,
    bytes: ByteArray,
    pedida: ClaveDelPdf?,
): ByteArray {
    val sonda = PdfConClave.sondear(bytes) ?: return bytes
    if (!sonda.pideClave) return bytes

    if (pedida != null) {
        val clave = pedida.clave
        if (clave.isBlank() || clave.length > MAX_LARGO_DE_LA_CLAVE_DEL_PDF) {
            throw FallaDeLaClaveDelPdf(HttpStatusCode.UnprocessableEntity, CLAVE_INCORRECTA)
        }
        // Dos topes: por documento (cada reintento con clave) y por usuario (cada forma probada).
        if (!RateLimiter.allow("clave-del-pdf:$uid:$documentoId", INTENTOS_DE_CLAVE_POR_HORA, VENTANA_DE_INTENTOS_MS)) {
            throw FallaDeLaClaveDelPdf(HttpStatusCode.TooManyRequests, DEMASIADOS_INTENTOS_DE_CLAVE)
        }
        val forma = formaQueAbre(uid, sonda, clave)
            ?: throw FallaDeLaClaveDelPdf(HttpStatusCode.UnprocessableEntity, CLAVE_INCORRECTA)
        val abierto = PdfConClave.abrirCon(bytes, forma)
        dbQuery {
            guardarElPdfAbierto(uid, documentoId, original = bytes, abierto = abierto)
            if (pedida.recordar && PdfConClave.sePuedeRecordar()) recordarLaClave(uid, clave.trim())
        }
        return abierto
    }

    val recordada = dbQuery { claveRecordada(uid) }
    if (recordada != null) {
        val forma = formaQueAbre(uid, sonda, recordada)
        if (forma != null) {
            val abierto = PdfConClave.abrirCon(bytes, forma)
            dbQuery { guardarElPdfAbierto(uid, documentoId, original = bytes, abierto = abierto) }
            return abierto
        }
    }
    throw FallaDeLaClaveDelPdf(HttpStatusCode.UnprocessableEntity, PDF_CON_CLAVE)
}

/**
 * El PDF abierto queda en el lugar del original —**la segunda puerta de escritura de
 * `documents.content`**, cifrada como la primera (`guardarDocumento`)— con `size_bytes` en el tamaño
 * nuevo (el archivado de extractos y la migración de cifrado lo comparan), y la huella del original
 * queda en `pdfs_abiertos` para el `reusar`.
 */
private fun Transaction.guardarElPdfAbierto(uid: String, documentoId: String, original: ByteArray, abierto: ByteArray) {
    Documents.update({ (Documents.id eq documentoId) and (Documents.userId eq uid) }) {
        it[content] = Cifrado.cifrarBytes(abierto)
        it[sizeBytes] = abierto.size.toLong()
    }
    val huella = huellaDeLosBytes(original)
    val yaEsta = PdfsAbiertos.selectAll()
        .where { (PdfsAbiertos.userId eq uid) and (PdfsAbiertos.huellaOriginal eq huella) }
        .any()
    if (yaEsta) {
        PdfsAbiertos.update({ (PdfsAbiertos.userId eq uid) and (PdfsAbiertos.huellaOriginal eq huella) }) {
            it[PdfsAbiertos.documentoId] = documentoId
            it[abiertoEn] = System.currentTimeMillis()
        }
    } else {
        PdfsAbiertos.insert {
            it[userId] = uid
            it[huellaOriginal] = huella
            it[PdfsAbiertos.documentoId] = documentoId
            it[abiertoEn] = System.currentTimeMillis()
        }
    }
}

/**
 * El documento donde quedó abierto este PDF con clave, si ya se abrió antes (ver «La huella»). Solo
 * mira `pdfs_abiertos` si los bytes pueden ser un PDF cifrado: el resto de las subidas no pagan la
 * consulta. Un documento que ya no existe no cuenta.
 */
internal fun Transaction.documentoDelPdfYaAbierto(uid: String, bytes: ByteArray): String? {
    if (!PdfConClave.puedeEstarCifrado(bytes)) return null
    val huella = huellaDeLosBytes(bytes)
    val doc = PdfsAbiertos.selectAll()
        .where { (PdfsAbiertos.userId eq uid) and (PdfsAbiertos.huellaOriginal eq huella) }
        .firstOrNull()?.get(PdfsAbiertos.documentoId) ?: return null
    val existe = Documents.select(listOf(Documents.id))
        .where { (Documents.id eq doc) and (Documents.userId eq uid) }
        .any()
    if (!existe) {
        PdfsAbiertos.deleteWhere { (PdfsAbiertos.userId eq uid) and (PdfsAbiertos.huellaOriginal eq huella) }
        return null
    }
    return doc
}

/** Separa el id del dueño de la clave adentro de lo cifrado: una fila movida a otro usuario no abre. */
private const val SEPARADOR = '\u0000'

/**
 * Guarda la clave **cifrada**, con el id del dueño adentro. Sin `DOCUMENTS_ENCRYPTION_KEY` no guarda
 * nada (y lo comprueba dos veces: [Cifrado.cifrarTexto] sin clave devolvería el texto en claro).
 */
internal fun Transaction.recordarLaClave(uid: String, clave: String) {
    if (!Cifrado.hayClave()) return
    val sellada = Cifrado.cifrarTexto("$uid$SEPARADOR$clave")
    check(Cifrado.estaCifrado(sellada)) { "la clave de los extractos no quedó cifrada" }
    val ahora = System.currentTimeMillis()
    val hay = ClavesDeExtractos.selectAll().where { ClavesDeExtractos.userId eq uid }.any()
    if (hay) {
        ClavesDeExtractos.update({ ClavesDeExtractos.userId eq uid }) {
            it[ClavesDeExtractos.clave] = sellada
            it[guardadaEn] = ahora
        }
    } else {
        ClavesDeExtractos.insert {
            it[userId] = uid
            it[ClavesDeExtractos.clave] = sellada
            it[guardadaEn] = ahora
        }
    }
}

/**
 * La clave recordada de [uid], en claro y solo en memoria, o `null`. Una fila que no está cifrada, que
 * no se puede abrir (la clave de Movi cambió) o que es de otro usuario no se usa —y se dice en el log
 * sin su contenido—.
 */
internal fun Transaction.claveRecordada(uid: String): String? {
    val guardada = ClavesDeExtractos.selectAll().where { ClavesDeExtractos.userId eq uid }
        .firstOrNull()?.get(ClavesDeExtractos.clave) ?: return null
    if (!Cifrado.estaCifrado(guardada)) {
        log.error("[pdf-con-clave] la clave recordada de un usuario no está cifrada: no se usa")
        return null
    }
    val claro = try {
        Cifrado.descifrarTexto(guardada)
    } catch (e: PapelIlegible) {
        log.error("[pdf-con-clave] no se pudo abrir la clave recordada de un usuario")
        return null
    }
    val prefijo = "$uid$SEPARADOR"
    if (!claro.startsWith(prefijo)) {
        log.error("[pdf-con-clave] la clave recordada no es de este usuario: no se usa")
        return null
    }
    return claro.removePrefix(prefijo).takeIf { it.isNotEmpty() }
}

/** ¿Hay una clave recordada? Sin abrirla. */
internal fun Transaction.hayClaveRecordada(uid: String): Boolean =
    ClavesDeExtractos.selectAll().where { ClavesDeExtractos.userId eq uid }.any()

/** «Olvidarla»: borra la fila. */
internal fun Transaction.olvidarLaClave(uid: String): Int =
    ClavesDeExtractos.deleteWhere { ClavesDeExtractos.userId eq uid }
