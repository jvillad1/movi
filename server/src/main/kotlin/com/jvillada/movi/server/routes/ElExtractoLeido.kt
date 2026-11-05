package com.jvillada.movi.server.routes

import com.jvillada.movi.server.observabilidad.Observabilidad
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.ProductoDelPapel
import com.jvillada.movi.shared.model.bancoDelExtractoParaElUso
import com.jvillada.movi.shared.model.palabrasDelNombreDelArchivo
import com.jvillada.movi.shared.model.productoQueNombraElArchivo
import io.ktor.http.HttpStatusCode
import java.util.UUID

/*
 * # Vuelta 130 · aprender de lo que se sube, sin datos de nadie
 *
 * La guía «Cómo bajar tu extracto» dice qué archivo lee mejor Movi en cada banco con lo que sabemos hoy
 * (los lectores sin IA y la investigación). Para que la próxima vuelta lo diga con lo que de verdad
 * funciona, cada lectura de un extracto deja un `EXTRACTO_LEIDO` en el tracking propio
 * (`uso_de_la_app`, con `Observabilidad.registrarSistema`: respeta «Mandar datos de uso» y nunca lanza)
 * con **solo** esto: el banco (de una lista cerrada), el producto, el lector (`SIN_IA` o `IA`), el
 * resultado, cuántas filas y cuántos milisegundos. **Nunca** el texto, los montos ni el nombre del
 * archivo: ninguna clave de `ClaveDeUso` los deja pasar, y `ElExtractoLeidoTest` lo vigila.
 *
 * Esta vuelta solo registra. La línea INFO «Lectura de extracto» de la vuelta 128 sigue igual: es el
 * diagnóstico de la IA (pedazos, pedidos, fichas); esto es lo que se cuenta por banco.
 */

internal const val LECTOR_SIN_IA: String = "SIN_IA"
internal const val LECTOR_IA: String = "IA"

/** Lo que [leerElExtracto] va sabiendo mientras lee: quién lo leyó, qué producto y qué banco. */
internal class MedidaDeLaLectura {
    var lector: String = LECTOR_SIN_IA
    /** Lo que dijo un lector sin IA (`CUENTA`, `TARJETA`, `CREDITO`, `FONDO`); con IA, `null`. */
    var producto: String? = null
    /** El `bankName` que se detectó antes de leer (lo que se sabe si la lectura falla). */
    var banco: String? = null
}

/** El resultado de una lectura que salió: `SIN_FILAS` si no trajo movimientos ni un saldo o un crédito. */
internal fun resultadoDelExtractoLeido(leido: ExtractoLeido): String =
    if (leido.filas.isEmpty() && leido.resumenDeCredito == null && leido.resumenDeSaldo == null) "SIN_FILAS" else "OK"

/**
 * El resultado de una lectura que falló, o `null` si la falla no es del archivo (la IA no estaba, pesa
 * de más, no llegó nada): eso no dice nada de qué archivo funciona en un banco, y no se cuenta.
 */
internal fun resultadoDeLaFallaDelExtracto(falla: FallaAlProcesarExtracto): String? = when {
    falla.mensaje == EXTRACTO_INCOMPLETO || falla.mensaje == IMAGEN_INCOMPLETA -> "INCOMPLETA"
    falla.mensaje == EXTRACTO_SIN_MOVIMIENTOS || falla.mensaje.startsWith("El archivo parece un Famirios") -> "SIN_FILAS"
    falla.status == HttpStatusCode.UnprocessableEntity -> "ERROR"
    else -> null
}

/** El producto que nombra el archivo, para la IA (que no lo dice): sin nombre que lo diga, `OTRO`. */
private fun productoDelArchivo(fileName: String): String = when (productoQueNombraElArchivo(fileName)) {
    ProductoDelPapel.TARJETA -> "TARJETA"
    ProductoDelPapel.CUENTA -> "CUENTA"
    ProductoDelPapel.CREDITO -> "CREDITO"
    null -> "OTRO"
}

/**
 * **Los datos del evento**, puros: banco, producto, lector, resultado, filas y milisegundos. El banco
 * sale del nombre detectado ([nombreDelBanco], el de la lectura) o, si no se detectó, del nombre del
 * archivo; pero lo que viaja es el id de la lista cerrada (`BANCOLOMBIA`, `OTRO`), nunca lo escrito.
 * Sin [lector] (un PDF con clave: nadie lo leyó), sin esa clave.
 */
internal fun datosDelExtractoLeido(
    nombreDelBanco: String?,
    fileName: String,
    producto: String?,
    lector: String?,
    resultado: String,
    filas: Int?,
    ms: Long,
): Map<ClaveDeUso, String> = buildMap {
    val banco = bancoDelExtractoParaElUso(nombreDelBanco).takeIf { it != "OTRO" }
        ?: bancoDelExtractoParaElUso(palabrasDelNombreDelArchivo(fileName))
    put(ClaveDeUso.BANCO, banco)
    put(ClaveDeUso.PRODUCTO, producto?.takeIf { ClaveDeUso.PRODUCTO.admite(it) } ?: productoDelArchivo(fileName))
    lector?.takeIf { ClaveDeUso.LECTOR.admite(it) }?.let { put(ClaveDeUso.LECTOR, it) }
    put(ClaveDeUso.RESULTADO, resultado)
    filas?.let { put(ClaveDeUso.CUANTOS, it.coerceIn(0, 999_999).toString()) }
    put(ClaveDeUso.MS, ms.coerceIn(0, 999_999_999).toString())
}

/** Escribe el `EXTRACTO_LEIDO` de una lectura. Nunca lanza (lo hace `registrarSistema`). */
internal suspend fun registrarElExtractoLeido(
    uid: String,
    medida: MedidaDeLaLectura,
    fileName: String,
    nombreDelBanco: String?,
    resultado: String,
    filas: Int?,
    inicio: Long,
) {
    val ahora = System.currentTimeMillis()
    Observabilidad.registrarSistema(
        uid,
        // Cada lectura es un hecho aparte: el id no se repite.
        Observabilidad.idEstable("extracto-leido", UUID.randomUUID().toString()),
        EventoDeUso.EXTRACTO_LEIDO,
        datosDelExtractoLeido(nombreDelBanco ?: medida.banco, fileName, medida.producto, medida.lector, resultado, filas, ahora - inicio),
        ahora,
    )
}

/**
 * **Un PDF con clave que Movi no pudo abrir** (`PDF_CON_CLAVE`, en `/leer` y en `/leer-extracto`): nadie
 * lo leyó, así que va sin lector ni filas, con el banco y el producto que dice el nombre del archivo.
 */
internal suspend fun registrarElExtractoConClave(uid: String, fileName: String, inicio: Long) {
    val ahora = System.currentTimeMillis()
    Observabilidad.registrarSistema(
        uid,
        Observabilidad.idEstable("extracto-leido", UUID.randomUUID().toString()),
        EventoDeUso.EXTRACTO_LEIDO,
        datosDelExtractoLeido(null, fileName, producto = null, lector = null, resultado = "CON_CLAVE", filas = null, ms = ahora - inicio),
        ahora,
    )
}
