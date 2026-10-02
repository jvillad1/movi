package com.jvillada.movi.ui.papeles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.LecturaDelPapel
import com.jvillada.movi.shared.model.MAX_DOCUMENTO_BYTES
import com.jvillada.movi.shared.model.NO_SE_PUDO_LEER_EL_PAPEL
import com.jvillada.movi.shared.model.QueEsElPapel
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_IGNORED
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.ui.Screen
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

    /** Para las pruebas y al cerrar sesión: lo compartido por uno no lo lee el siguiente. */
    fun olvidar() {
        pendientes = emptyList()
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

    /** No se pudo. [guardado] dice si al menos quedó en Documentos. */
    data class NoSePudo(override val nombre: String, val motivo: String, val guardado: Boolean) : ResultadoDelPapel
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
    val documento = try {
        repo.subirPapel(archivo.nombre, archivo.bytes, archivo.mime)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        return ResultadoDelPapel.NoSePudo(archivo.nombre, "No pudimos guardarlo: ${e.toUserMessage()}", guardado = false)
    }
    return try {
        resultadoDe(archivo.nombre, repo.leerPapel(documento.id))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ResultadoDelPapel.NoSePudo(archivo.nombre, e.toUserMessage(), guardado = true)
    }
}

/** El título de la hoja: mientras lee, y cuando terminó. */
fun tituloDeLaHoja(resultados: List<ResultadoDelPapel>): String {
    val leyendo = resultados.count { it is ResultadoDelPapel.Leyendo }
    return when {
        leyendo > 0 && resultados.size == 1 -> "Movi está leyendo tu comprobante…"
        leyendo > 0 -> "Movi está leyendo tus ${resultados.size} archivos…"
        resultados.size == 1 -> "Listo"
        else -> "Listo: ${resultados.size} archivos"
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
    is ResultadoDelPapel.Extracto -> {
        val nuevos = resultado.lectura.extracto?.newTransactions?.size ?: 0
        val iguales = resultado.lectura.extracto?.matches?.size ?: 0
        "Es un extracto: $nuevos ${if (nuevos == 1) "movimiento nuevo" else "movimientos nuevos"}" +
            if (iguales > 0) " y $iguales que ya tenías" else ""
    }
    is ResultadoDelPapel.NoSePudo -> if (resultado.guardado) NO_SE_PUDO_LEER_EL_PAPEL else "No pudimos guardar el archivo"
}
