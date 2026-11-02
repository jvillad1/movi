package com.jvillada.movi.ui.papeles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.CLAVE_INCORRECTA
import com.jvillada.movi.shared.model.ClaveDelPdf
import com.jvillada.movi.shared.model.LA_CLAVE_NO_ABRE_EL_PDF
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.PDF_CON_CLAVE
import com.jvillada.movi.shared.model.esCodigoDeClaveDelPdf
import com.jvillada.movi.shared.model.fraseDeLaClaveDelPdf
import com.jvillada.movi.shared.repository.ApiException
import com.jvillada.movi.shared.model.MAX_DOCUMENTO_BYTES
import com.jvillada.movi.shared.model.NO_SE_PUDO_LEER_EL_PAPEL
import com.jvillada.movi.shared.model.QueEsElPapel
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.model.corteLegible
import com.jvillada.movi.shared.model.nombreDelExtractoDeSaldo
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.esIaNoDisponible
import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.CancellationException

/**
 * # «Compartir con Movi» del lado de la app (Ola 2)
 *
 * Tres puertas llegan acá: la hoja de compartir de Android (`MainActivity` → [PapelesCompartidos]),
 * «Subir comprobante o extracto» en Por revisar y en Agregar, y soltar un archivo sobre la web. Las
 * tres hacen lo mismo: dejan los archivos en [PapelesCompartidos] y `App()` abre la hoja «Movi está
 * leyendo…» ([HojaLeyendoElPapel]), que los guarda en Documentos, pide la lectura y dice qué pasó.
 *
 * Este archivo es la parte sin dibujo, para poder probarla sin Compose.
 */

/**
 * Un archivo que llegó para leer. [problema] cuando ni siquiera se pudo abrir del lado del aparato
 * (Android no dejó leer el archivo, pesa de más): se dice en la hoja y no viaja.
 */
class ArchivoCompartido(
    val nombre: String,
    val bytes: ByteArray,
    val mime: String,
    val problema: String? = null,
    /**
     * Vuelta 103: el archivo **ya está guardado** en Documentos con este id (Documentos → «Importar
     * movimientos» de un PDF con clave, «Escribir la clave» o «Intentar de nuevo» en la guía). No se
     * vuelve a subir: se pide la lectura de ese documento.
     */
    val documentoId: String? = null,
)

/**
 * **Los archivos que esperan ser leídos.** Lo llenan las puertas y lo vacía la hoja. Vive afuera de
 * la composición —como `DestinoDesdeAfuera`— porque Android entrega lo compartido en `onCreate`, antes
 * de que exista `App()`, y porque la puerta de la huella decide **cuándo** se procesa (ver
 * [sePuedeLeerLoCompartido]).
 */
object PapelesCompartidos {
    var pendientes: List<ArchivoCompartido> by mutableStateOf(emptyList())
        private set

    /** Agrega [archivos] a la cola. Varios a la vez es «Compartir» con varias fotos elegidas. */
    fun recibir(archivos: List<ArchivoCompartido>) {
        if (archivos.isEmpty()) return
        pendientes = pendientes + archivos
    }

    /** Se lleva todo lo pendiente: lo que tome la hoja ya no lo toma otra. */
    fun tomarTodos(): List<ArchivoCompartido> {
        val todos = pendientes
        pendientes = emptyList()
        return todos
    }

    /**
     * Vuelta 103 · **lo último que pasó con cada archivo**, por nombre. Lo escribe la hoja a medida
     * que lee, y lo lee la guía de primer uso para que su fila diga la verdad (antes decía «Listo»
     * apenas se elegía el archivo, aunque la hoja dijera que no se pudo leer).
     */
    var resultados: Map<String, ResultadoDelPapel> by mutableStateOf(emptyMap())
        private set

    fun anotar(resultado: ResultadoDelPapel) {
        resultados = resultados + (resultado.nombre to resultado)
    }

    /** Para las pruebas y al cerrar sesión: lo compartido por uno no lo lee el siguiente. */
    fun olvidar() {
        pendientes = emptyList()
        resultados = emptyMap()
    }
}

/**
 * **¿Ya se puede leer lo compartido?** Solo con sesión y **fuera** del login. Con «Entrar con
 * huella» la app arranca en el login aunque haya sesión: subir el archivo ahí sería saltarse la
 * puerta. Es la misma condición con la que se cumple el destino de un aviso del teléfono.
 */
fun sePuedeLeerLoCompartido(conSesion: Boolean, pantalla: Screen): Boolean =
    conSesion && pantalla != Screen.Login && pantalla != Screen.Register

/** Lo que pasó con un archivo, para pintarlo en la hoja. */
sealed interface ResultadoDelPapel {
    val nombre: String

    data class Leyendo(override val nombre: String) : ResultadoDelPapel

    /** Quedó en «Por revisar» (o ya estaba ahí). */
    data class EnLaBandeja(override val nombre: String, val lectura: LecturaDelPapel) : ResultadoDelPapel

    /** El movimiento ya estaba anotado: no se creó nada. */
    data class YaAnotado(override val nombre: String, val lectura: LecturaDelPapel) : ResultadoDelPapel

    /** Un extracto: su pantalla de revisión de siempre. */
    data class Extracto(override val nombre: String, val lectura: LecturaDelPapel) : ResultadoDelPapel

    /**
     * No se pudo. [guardado] dice si al menos quedó en Documentos. [documentoId] y [reintentable]
     * (vuelta 103): con los dos, la fila ofrece «Intentar de nuevo» sobre el documento ya guardado
     * —la IA no estaba disponible, se cayó la conexión—, sin volver a subirlo.
     */
    data class NoSePudo(
        override val nombre: String,
        val motivo: String,
        val guardado: Boolean,
        val documentoId: String? = null,
        val reintentable: Boolean = false,
    ) : ResultadoDelPapel

    /**
     * Vuelta 103 · **un PDF con clave**: quedó guardado y la fila pide la clave en línea («Clave del
     * PDF», la pista, «Recordarla…» y «Abrir»). [error]: la última clave no lo abrió.
     */
    data class ConClave(override val nombre: String, val documentoId: String, val error: String? = null) : ResultadoDelPapel
}

/**
 * La API de Anthropic no contestó por la cuenta (sin saldo, clave, saturada). El documento sí se
 * guardó —eso pasa antes de leerlo—, así que se puede prometer.
 */
const val LECTURA_CON_IA_NO_DISPONIBLE: String =
    "La lectura con IA no está disponible ahora. Tu archivo quedó guardado en Documentos."

/** Por qué no se pudo leer un papel que ya quedó guardado, en las palabras de la app. */
fun motivoAlLeerElPapel(e: Throwable): String = when {
    e.esIaNoDisponible() -> LECTURA_CON_IA_NO_DISPONIBLE
    e is ApiException && esCodigoDeClaveDelPdf(e.serverMessage) -> fraseDeLaClaveDelPdf(e.serverMessage.orEmpty())
    else -> e.toUserMessage()
}

/** El código de la clave que contestó el server (`PDF_CON_CLAVE`…), o `null` si la falla es otra. */
fun codigoDeLaClave(e: Throwable): String? =
    (e as? ApiException)?.serverMessage?.trim()?.takeIf { esCodigoDeClaveDelPdf(it) }

/**
 * ¿Vale la pena ofrecer «Intentar de nuevo»? Cuando la falla no es del archivo: la IA no estaba, el
 * server no contestó, no había red. Un 4xx explica algo del papel, y reintentar daría lo mismo.
 */
fun esReintentable(e: Throwable): Boolean =
    e.esIaNoDisponible() || e !is ApiException || e.status >= 500

/**
 * Lo que pasó al pedir la lectura de un documento ya guardado: el resultado, o la falla dicha en las
 * palabras de la app —un PDF con clave se vuelve [ResultadoDelPapel.ConClave]—.
 */
fun resultadoDeLaFalla(nombre: String, documentoId: String, e: Throwable): ResultadoDelPapel =
    when (codigoDeLaClave(e)) {
        PDF_CON_CLAVE -> ResultadoDelPapel.ConClave(nombre, documentoId)
        CLAVE_INCORRECTA -> ResultadoDelPapel.ConClave(nombre, documentoId, error = LA_CLAVE_NO_ABRE_EL_PDF)
        else -> ResultadoDelPapel.NoSePudo(
            nombre, motivoAlLeerElPapel(e), guardado = true, documentoId = documentoId, reintentable = esReintentable(e),
        )
    }

/** Qué resultado es una lectura del server. */
fun resultadoDe(nombre: String, lectura: LecturaDelPapel): ResultadoDelPapel = when {
    lectura.que == QueEsElPapel.EXTRACTO -> ResultadoDelPapel.Extracto(nombre, lectura)
    lectura.porRevisarId == null && lectura.yaAnotado.isNotEmpty() -> ResultadoDelPapel.YaAnotado(nombre, lectura)
    lectura.porRevisarId != null -> ResultadoDelPapel.EnLaBandeja(nombre, lectura)
    else -> ResultadoDelPapel.NoSePudo(nombre, NO_SE_PUDO_LEER_EL_PAPEL, guardado = true)
}

/**
 * **Un archivo, de punta a punta**: lo guarda en Documentos y pide la lectura. Nunca lanza: lo que
 * falle se vuelve un [ResultadoDelPapel.NoSePudo] con el motivo, y dice si el archivo alcanzó a
 * guardarse —la promesa «queda guardado en Documentos» solo se hace si es verdad—.
 */
suspend fun leerUnPapel(repo: WalletRepository, archivo: ArchivoCompartido): ResultadoDelPapel {
    archivo.problema?.let { return ResultadoDelPapel.NoSePudo(archivo.nombre, it, guardado = false) }
    if (archivo.bytes.size > MAX_DOCUMENTO_BYTES) {
        return ResultadoDelPapel.NoSePudo(
            archivo.nombre,
            "Pesa más de ${MAX_DOCUMENTO_BYTES / (1024 * 1024)} MB, el máximo que Movi guarda.",
            guardado = false,
        )
    }
    // Ya guardado (vuelta 103): no se sube de nuevo.
    val documentoId = archivo.documentoId ?: try {
        repo.subirPapel(archivo.nombre, archivo.bytes, archivo.mime).id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        return ResultadoDelPapel.NoSePudo(archivo.nombre, "No pudimos guardarlo: ${e.toUserMessage()}", guardado = false)
    }
    return try {
        resultadoDe(archivo.nombre, repo.leerPapel(documentoId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        resultadoDeLaFalla(archivo.nombre, documentoId, e)
    }
}

/**
 * Vuelta 103 · **«Abrir» con la clave**: el reintento sobre el documento ya guardado, con la clave en
 * el cuerpo. Nunca lanza. Una clave que no abre vuelve a [ResultadoDelPapel.ConClave] con el error.
 */
suspend fun abrirConLaClave(
    repo: WalletRepository,
    pendiente: ResultadoDelPapel.ConClave,
    clave: String,
    recordar: Boolean,
): ResultadoDelPapel = try {
    resultadoDe(pendiente.nombre, repo.abrirPapelConClave(pendiente.documentoId, ClaveDelPdf(clave, recordar)))
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    resultadoDeLaFalla(pendiente.nombre, pendiente.documentoId, e)
}

/** ¿Algo de [resultados] no salió? Un papel que no se leyó o que espera su clave. */
private fun conProblema(r: ResultadoDelPapel): Boolean =
    r is ResultadoDelPapel.NoSePudo || r is ResultadoDelPapel.ConClave

/** El título de la hoja: mientras lee, y cuando terminó. */
fun tituloDeLaHoja(resultados: List<ResultadoDelPapel>): String {
    val leyendo = resultados.count { it is ResultadoDelPapel.Leyendo }
    return when {
        leyendo > 0 && resultados.size == 1 -> "Movi está leyendo tu archivo…"
        leyendo > 0 -> "Movi está leyendo tus ${resultados.size} archivos…"
        // Vuelta 103: «Listo» solo si de verdad salió. Un fallo nunca se ve como «Listo».
        resultados.size == 1 -> when (resultados.single()) {
            is ResultadoDelPapel.ConClave -> "Este PDF tiene clave"
            is ResultadoDelPapel.NoSePudo -> "No se pudo leer"
            else -> "Listo"
        }
        resultados.none(::conProblema) -> "Listo: ${resultados.size} archivos"
        else -> "Leímos ${resultados.count { !conProblema(it) }} de ${resultados.size} archivos"
    }
}

/** La primera línea de un resultado terminado. */
fun tituloDelResultado(resultado: ResultadoDelPapel): String = when (resultado) {
    is ResultadoDelPapel.Leyendo -> "Leyendo…"
    is ResultadoDelPapel.EnLaBandeja -> when {
        !resultado.lectura.yaEstabaEnLaBandeja -> "Quedó en Por revisar"
        resultado.lectura.estadoEnLaBandeja == SMS_STATE_CONFIRMED -> "Ya lo habías confirmado"
        resultado.lectura.estadoEnLaBandeja == SMS_STATE_IGNORED -> "Lo habías ignorado"
        else -> "Ya estaba en Por revisar"
    }
    is ResultadoDelPapel.YaAnotado -> "Ya lo tienes anotado"
    is ResultadoDelPapel.Extracto -> resultado.lectura.extracto?.extractoDeSaldo?.let { saldo ->
        // El extracto de Skandia o de la AFC no trae movimientos: dice cuánto hay al corte.
        "${nombreDelExtractoDeSaldo(saldo.resumen)}: el saldo al ${corteLegible(saldo.resumen)}"
    } ?: run {
        val nuevos = resultado.lectura.extracto?.newTransactions?.size ?: 0
        val iguales = resultado.lectura.extracto?.matches?.size ?: 0
        "Es un extracto: $nuevos ${if (nuevos == 1) "movimiento nuevo" else "movimientos nuevos"}" +
            if (iguales > 0) " y $iguales que ya tenías" else ""
    }
    is ResultadoDelPapel.NoSePudo -> if (resultado.guardado) NO_SE_PUDO_LEER else "No pudimos guardar el archivo"
    is ResultadoDelPapel.ConClave -> ESTE_PDF_TIENE_CLAVE
}

/** El título de una fila que no se pudo leer (vuelta 103): corto, y el motivo debajo. */
const val NO_SE_PUDO_LEER: String = "No se pudo leer"

/** El título de la fila de un PDF con clave. */
const val ESTE_PDF_TIENE_CLAVE: String = "Este PDF tiene clave"

/** ¿La fila de este resultado va en tono de aviso (ícono y título)? */
fun esAvisoDelPapel(resultado: ResultadoDelPapel?): Boolean =
    resultado is ResultadoDelPapel.NoSePudo || resultado is ResultadoDelPapel.ConClave

/** Cómo se ve la fila de un papel fuera de la hoja (la guía): qué dice, la píldora y si es aviso. */
data class FilaDelPapel(val titulo: String, val pildora: String, val aviso: Boolean, val leyendo: Boolean = false)

/**
 * **La fila del extracto en la guía de primer uso** (vuelta 103), según lo que pasó de verdad. Cada
 * estado dice la verdad: leyendo, listo (extracto, en Por revisar, ya anotado), con clave, sin
 * crédito de IA y cualquier otro fallo. `null` = la hoja todavía no lo tomó: se está leyendo.
 */
fun filaDelPapelEnLaGuia(resultado: ResultadoDelPapel?): FilaDelPapel = when (resultado) {
    null, is ResultadoDelPapel.Leyendo -> FilaDelPapel("Movi está leyendo tu extracto", "Leyendo", aviso = false, leyendo = true)
    is ResultadoDelPapel.Extracto -> FilaDelPapel("Movi leyó tu extracto", "Listo", aviso = false)
    is ResultadoDelPapel.EnLaBandeja -> FilaDelPapel("Quedó en Por revisar", "Listo", aviso = false)
    is ResultadoDelPapel.YaAnotado -> FilaDelPapel("Ya lo tienes anotado", "Listo", aviso = false)
    is ResultadoDelPapel.ConClave -> FilaDelPapel(ESTE_PDF_TIENE_CLAVE, "Falta la clave", aviso = true)
    is ResultadoDelPapel.NoSePudo ->
        if (resultado.motivo == LECTURA_CON_IA_NO_DISPONIBLE) FilaDelPapel(NO_SE_PUDO_LEER, "IA no disponible", aviso = true)
        else FilaDelPapel(NO_SE_PUDO_LEER, "No se pudo", aviso = true)
}

/** El documento sobre el que la guía puede reintentar (escribir la clave, intentar de nuevo), o `null`. */
fun documentoParaReintentar(resultado: ResultadoDelPapel?): String? = when (resultado) {
    is ResultadoDelPapel.ConClave -> resultado.documentoId
    is ResultadoDelPapel.NoSePudo -> resultado.documentoId?.takeIf { resultado.reintentable }
    else -> null
}
