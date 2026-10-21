package com.jvillada.movi.server.documentos

import org.slf4j.Logger
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * # Los papeles del dueño se guardan cifrados
 *
 * `documents.content` (los extractos, comprobantes, escrituras y certificados) y
 * `lecturas_de_papeles.datos` (lo que Claude leyó de ellos) se guardaban en claro: cualquiera con
 * una copia de la base —un respaldo, un dump, el panel de Railway— los leía. Desde acá se guardan
 * con **AES-256-GCM**, con la clave de la env var `DOCUMENTS_ENCRYPTION_KEY` (32 bytes en base64:
 * `openssl rand -base64 32`).
 *
 * **Todo cifrado y descifrado de esas dos columnas pasa por este objeto.** Ninguna ruta llama a
 * `javax.crypto` a mano.
 *
 * ## El formato
 *
 * - Binario (`documents.content`): `MOVI1` (5 bytes ASCII) + IV aleatorio de 12 + texto cifrado +
 *   tag de 16. Ocupa 33 bytes más que el original (`size_bytes` sigue siendo el tamaño en claro).
 * - Texto (`lecturas_de_papeles.datos`): `MOVI1:` + base64 de (IV + texto cifrado + tag), con el
 *   texto en UTF-8 adentro.
 *
 * La cabecera es la que distingue una fila cifrada de una vieja en claro, sin columna nueva: un PDF
 * empieza con `%PDF`, una imagen con su firma y una lectura con `{`. Leer una fila sin cabecera la
 * devuelve tal cual, así que las filas de antes siguen funcionando hasta que la migración del
 * arranque las cifre (`Migrations.cifrarLosPapelesEnClaro`).
 *
 * ## Sin clave
 *
 * Todo funciona como antes: se guarda en claro y se avisa una vez en el log al arrancar
 * ([avisarAlArrancar]). Una clave mal formada (no es base64, o no da 32 bytes) **no** cae a «sin
 * clave»: el server no arranca, porque guardar en claro mientras el dueño cree que cifra es peor
 * que un despliegue que falla (Railway sigue sirviendo la versión anterior).
 *
 * ## Una fila que no se puede abrir
 *
 * Una fila cifrada con otra clave (la clave cambió) o con la clave ausente lanza [PapelIlegible]:
 * las rutas contestan 503 con [PAPEL_CIFRADO_ILEGIBLE], nunca un 500 genérico ni bytes basura. GCM
 * comprueba el tag, así que una clave equivocada no puede devolver datos inventados.
 *
 * ## Lo que NO cambia
 *
 * La huella SHA-256 de `lecturas_de_papeles` y el `reusar` de `POST /api/documents` se calculan
 * sobre el contenido **en claro** (después de [descifrarBytes]): el IV es aleatorio, así que dos
 * cifrados del mismo archivo nunca son iguales.
 */
object Cifrado {
    private val MAGIA: ByteArray = "MOVI1".toByteArray(Charsets.US_ASCII)
    private const val PREFIJO_DE_TEXTO = "MOVI1:"
    private const val LARGO_IV = 12
    private const val LARGO_TAG_BITS = 128
    private const val ENV = "DOCUMENTS_ENCRYPTION_KEY"
    private const val PROPIEDAD = "movi.documents.key"

    /** Cuántos bytes le agrega el cifrado a un archivo: cabecera + IV + tag. */
    const val BYTES_DE_MAS: Int = 5 + LARGO_IV + LARGO_TAG_BITS / 8

    private val azar = SecureRandom()

    /** La clave de la configuración, resuelta una vez. `null` = sin clave. */
    private val deLaConfiguracion: SecretKey? by lazy { claveDesdeBase64(leerConfiguracion()) }

    /** Marca de «las pruebas no fijaron nada»: se usa la de la configuración. */
    private object SinFijar

    @Volatile private var fijada: Any? = SinFijar

    /**
     * **Solo para pruebas**: fija la clave (base64) o la quita (`null`), sin depender del `.env` ni
     * del orden de las clases en el fork. [usarLaDeLaConfiguracion] vuelve a la de siempre.
     */
    internal fun usarClaveParaPruebas(base64: String?) {
        fijada = claveDesdeBase64(base64)
    }

    internal fun usarLaDeLaConfiguracion() {
        fijada = SinFijar
    }

    private fun clave(): SecretKey? = fijada.let { if (it === SinFijar) deLaConfiguracion else it as SecretKey? }

    /** ¿Hay clave? Sin ella se guarda en claro, como antes. */
    fun hayClave(): Boolean = clave() != null

    /**
     * Valida la clave (lanza si está mal formada: el server no arranca) y, si no hay, lo dice una
     * vez en el log. Se llama al arrancar, antes de la migración.
     */
    fun avisarAlArrancar(log: Logger) {
        if (hayClave()) {
            log.info("Documentos: se guardan cifrados (AES-256-GCM, $ENV presente)")
        } else {
            log.warn(
                "Documentos: $ENV no está definida — los documentos y sus lecturas se guardan EN CLARO. " +
                    "Genera una con `openssl rand -base64 32` y ponla en la configuración del server.",
            )
        }
    }

    // ── Binario: documents.content ─────────────────────────────────────────────────────────────

    /** Lo que se escribe en `documents.content`: cifrado si hay clave, en claro si no. */
    fun cifrarBytes(claro: ByteArray): ByteArray {
        val k = clave() ?: return claro
        return MAGIA + sellar(k, claro)
    }

    /** ¿Estos bytes ya están cifrados (traen la cabecera)? */
    fun estaCifrado(guardado: ByteArray): Boolean =
        guardado.size >= MAGIA.size && MAGIA.indices.all { guardado[it] == MAGIA[it] }

    /** Lo que se lee de `documents.content`, en claro. Lanza [PapelIlegible] si no se puede abrir. */
    fun descifrarBytes(guardado: ByteArray): ByteArray {
        if (!estaCifrado(guardado)) return guardado
        return abrir(guardado.copyOfRange(MAGIA.size, guardado.size))
    }

    // ── Texto: lecturas_de_papeles.datos ───────────────────────────────────────────────────────

    /** Lo que se escribe en `lecturas_de_papeles.datos`: cifrado si hay clave, en claro si no. */
    fun cifrarTexto(claro: String): String {
        val k = clave() ?: return claro
        return PREFIJO_DE_TEXTO + Base64.getEncoder().encodeToString(sellar(k, claro.toByteArray(Charsets.UTF_8)))
    }

    fun estaCifrado(guardado: String): Boolean = guardado.startsWith(PREFIJO_DE_TEXTO)

    /** Lo que se lee de `lecturas_de_papeles.datos`, en claro. Lanza [PapelIlegible] si no se puede abrir. */
    fun descifrarTexto(guardado: String): String {
        if (!estaCifrado(guardado)) return guardado
        val sellado = try {
            Base64.getDecoder().decode(guardado.substring(PREFIJO_DE_TEXTO.length))
        } catch (e: IllegalArgumentException) {
            throw PapelIlegible("la lectura cifrada no es base64 válido", e)
        }
        return abrir(sellado).toString(Charsets.UTF_8)
    }

    // ── Adentro ────────────────────────────────────────────────────────────────────────────────

    /** IV + texto cifrado + tag. */
    private fun sellar(k: SecretKey, claro: ByteArray): ByteArray {
        val iv = ByteArray(LARGO_IV).also { azar.nextBytes(it) }
        val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
        cifrador.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(LARGO_TAG_BITS, iv))
        return iv + cifrador.doFinal(claro)
    }

    private fun abrir(sellado: ByteArray): ByteArray {
        val k = clave() ?: throw PapelIlegible("la fila está cifrada y el server no tiene $ENV")
        if (sellado.size < LARGO_IV + LARGO_TAG_BITS / 8) throw PapelIlegible("la fila cifrada está truncada")
        return try {
            val cifrador = Cipher.getInstance("AES/GCM/NoPadding")
            cifrador.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(LARGO_TAG_BITS, sellado, 0, LARGO_IV))
            cifrador.doFinal(sellado, LARGO_IV, sellado.size - LARGO_IV)
        } catch (e: AEADBadTagException) {
            throw PapelIlegible("la fila se cifró con otra clave (¿cambió $ENV?) o está dañada", e)
        }
    }

    private fun claveDesdeBase64(base64: String?): SecretKey? {
        val limpio = base64?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val bytes = try {
            Base64.getDecoder().decode(limpio)
        } catch (e: IllegalArgumentException) {
            error("$ENV no es base64 válido. Genérala con `openssl rand -base64 32`.")
        }
        check(bytes.size == 32) {
            "$ENV tiene que ser de 32 bytes en base64 (vino de ${bytes.size}). Genérala con `openssl rand -base64 32`."
        }
        return SecretKeySpec(bytes, "AES")
    }

    /** Propiedad del sistema, env, `server/.env`, `.env` — el mismo orden que el resto del server. */
    private fun leerConfiguracion(): String? {
        System.getProperty(PROPIEDAD)?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv(ENV)?.takeIf { it.isNotBlank() }?.let { return it }
        val archivos = listOf(
            File(System.getProperty("user.dir"), "server/.env"),
            File(System.getProperty("user.dir"), ".env"),
        )
        return archivos.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$ENV=") }
                ?.substringAfter("=")?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}

/**
 * Una fila cifrada que el server no puede abrir: la clave cambió, falta, o la fila está dañada. El
 * mensaje es para el log; al cliente se le dice [PAPEL_CIFRADO_ILEGIBLE].
 */
class PapelIlegible(detalle: String, causa: Throwable? = null) : RuntimeException(detalle, causa)

/** Lo que se le dice al dueño cuando su archivo está cifrado con una clave que el server ya no tiene. */
const val PAPEL_CIFRADO_ILEGIBLE: String =
    "Este archivo está guardado cifrado y el servidor no tiene la clave para abrirlo. Avísale a quien " +
        "administra Movi."
