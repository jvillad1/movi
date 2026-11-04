package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.serialization.Serializable

/*
 * # Vuelta 111 · el iPhone le avisa a Movi con Atajos
 *
 * Android lee solo los SMS y las notificaciones del banco; un iPhone no deja que ninguna app los lea.
 * Lo que sí existe es la app **Atajos**: una automatización personal «Mensaje» se dispara al llegar un
 * SMS, y una «Transacción» (iOS 17+) al pagar con Apple Pay. Las dos pueden mandar lo que recibieron a
 * una URL con «Obtener contenido de URL». Movi les da esa URL (`POST /api/captura/atajo`) y un **código
 * de captura** para que el server sepa de quién es lo que llega.
 *
 * El código **no es la sesión**: no sirve para leer nada, solo para mandar avisos, y se revoca sin
 * cerrar la sesión. El server guarda solo su SHA-256 (como los enlaces compartidos) y lo muestra en
 * claro **una sola vez**, al crearlo.
 *
 * Lo que llega sigue el camino de los correos del banco: el MISMO filtro de bancos del teléfono, la
 * misma tabla `sms_messages`, el mismo dedupe y la misma bandeja «Por revisar». Nada se anota solo.
 */

/** La ruta pública a la que el atajo manda lo que recibió. */
const val RUTA_DE_LA_CAPTURA_POR_ATAJO: String = "/api/captura/atajo"

/**
 * Vuelta 114 · **el nombre del atajo es el nombre del archivo.** Al abrir un `.shortcut`, Atajos lo instala
 * con el nombre del archivo sin la extensión: en el iPhone de Caro (iOS 27.0.1, 2-nov-2026) el atajo bajado de
 * `/atajos/movi-mensaje.shortcut` quedó como «movi-mensaje». El archivo firmado no guarda ningún nombre (por
 * dentro es un Apple Archive con un solo `Shortcut.wflow`), así que el nombre visible se fija sirviendo el
 * archivo con ese nombre: en la ruta y en `Content-Disposition`. Va con un guion y no con «·»: un nombre de
 * archivo con caracteres fuera de ASCII depende de cómo cada navegador decodifique la cabecera.
 */
const val NOMBRE_DEL_ATAJO_DE_MENSAJES: String = "Movi - Avisar un mensaje"

/** El nombre del atajo que avisa un pago con Apple Pay (se arma a mano: es el nombre que la guía pide escribir). */
const val NOMBRE_DEL_ATAJO_DE_APPLE_PAY: String = "Movi - Pago con Apple Pay"

/** El archivo firmado del atajo de mensajes: en los recursos del server y en la descarga. */
const val ARCHIVO_DEL_ATAJO_DE_MENSAJES: String = "$NOMBRE_DEL_ATAJO_DE_MENSAJES.shortcut"

/** El archivo del atajo de Apple Pay (si algún día se firma). */
const val ARCHIVO_DEL_ATAJO_DE_APPLE_PAY: String = "$NOMBRE_DEL_ATAJO_DE_APPLE_PAY.shortcut"

/** Donde el server sirve el atajo de mensajes ya firmado (si la firma salió): el archivo, con los espacios como `%20`. */
const val RUTA_DEL_ATAJO_DE_MENSAJES: String = "/atajos/Movi%20-%20Avisar%20un%20mensaje.shortcut"

/** Donde el server sirve el atajo de Apple Pay ya firmado (si la firma salió). */
const val RUTA_DEL_ATAJO_DE_APPLE_PAY: String = "/atajos/Movi%20-%20Pago%20con%20Apple%20Pay.shortcut"

/**
 * Las rutas de la vuelta 111, que ya se dieron en guías y enlaces: siguen sirviendo el mismo archivo (y con el
 * nombre nuevo en `Content-Disposition`).
 */
const val RUTA_VIEJA_DEL_ATAJO_DE_MENSAJES: String = "/atajos/movi-mensaje.shortcut"
const val RUTA_VIEJA_DEL_ATAJO_DE_APPLE_PAY: String = "/atajos/movi-apple-pay.shortcut"

/** La ruta de un archivo de atajo: «/atajos/» y el nombre con cada espacio como `%20` (los nombres son ASCII). */
fun rutaDelArchivoDeAtajo(archivo: String): String = "/atajos/" + archivo.replace(" ", "%20")

/**
 * **Cómo empieza el rótulo `bank` de lo que llegó por un atajo**: «Atajo · Bancolombia» (un SMS),
 * «Atajo · Apple Pay» (un pago). Como «Notificación · …» y «Correo · …»: el canal y, detrás del punto,
 * de quién es.
 */
const val ORIGEN_DEL_ATAJO: String = "Atajo"

/** El nombre del origen de un pago con Apple Pay, detrás de «Atajo · ». */
const val APPLE_PAY: String = "Apple Pay"

/** El rótulo completo de un pago con Apple Pay. */
const val ORIGEN_DE_APPLE_PAY: String = "$ORIGEN_DEL_ATAJO · $APPLE_PAY"

/** ¿El rótulo `bank` es de algo que llegó por un atajo del iPhone? */
fun esUnAtajo(origen: String): Boolean = origen.trimStart().startsWith(ORIGEN_DEL_ATAJO, ignoreCase = true)

/** ¿El rótulo `bank` es el de un pago con Apple Pay que mandó el atajo? */
fun esElAtajoDeApplePay(origen: String): Boolean =
    esUnAtajo(origen) && origen.substringAfter('·', "").trim().equals(APPLE_PAY, ignoreCase = true)

/**
 * Vuelta 127 · **el rótulo de una notificación de Nu que mandó el atajo** (ver `notificacionDeNuDelAtajo`):
 * el canal del atajo y, detrás, el mismo rótulo con que el teléfono Android sube la notificación de Nu.
 * Así se sabe que llegó por el iPhone (`esUnAtajo`, el tracking) y, quitándole el atajo
 * ([origenSinElAtajo]), es la notificación de Nu de siempre para el parser, el mismo pago y el banco mudo.
 */
const val ORIGEN_DE_NU_POR_ATAJO: String = "$ORIGEN_DEL_ATAJO · Notificación · Nu"

/** ¿El rótulo `bank` es el de una notificación que mandó el atajo («Atajo · Notificación · Nu»)? */
fun esUnaNotificacionPorAtajo(origen: String): Boolean =
    esUnAtajo(origen) && origen.substringAfter('·', "").trim().let {
        it.startsWith("Notificación", ignoreCase = true) || it.startsWith("Notificacion", ignoreCase = true)
    }

/**
 * **El rótulo sin el atajo delante, si lo que llegó por el atajo es una notificación**: «Atajo ·
 * Notificación · Nu» → «Notificación · Nu». Cualquier otro rótulo queda como está («Atajo · Bancolombia»
 * es un SMS y «Atajo · Apple Pay», el aviso de la billetera: cada uno con su regla).
 */
fun origenSinElAtajo(origen: String): String =
    if (esUnaNotificacionPorAtajo(origen)) origen.substringAfter('·').trim() else origen

/**
 * `GET /api/captura/codigo`: si hay un código vigente, desde cuándo y cuándo llegó el último aviso con
 * él. **Nunca el código**: ese se ve una sola vez, en [CodigoDeCapturaNuevo].
 *
 * [atajoDeMensajes] y [atajoDeApplePay] dicen si el server tiene el atajo firmado para instalarlo con un
 * toque (en [RUTA_DEL_ATAJO_DE_MENSAJES] y [RUTA_DEL_ATAJO_DE_APPLE_PAY]); si no, la guía muestra cómo
 * armarlo a mano.
 */
@Serializable
data class CodigoDeCaptura(
    val vigente: Boolean = false,
    val creadoEn: Long? = null,
    val ultimoUso: Long? = null,
    val atajoDeMensajes: Boolean = false,
    val atajoDeApplePay: Boolean = false,
)

/** `POST /api/captura/codigo`: el código nuevo, **en claro y una sola vez**. El anterior ya no sirve. */
@Serializable
data class CodigoDeCapturaNuevo(
    val codigo: String,
    val creadoEn: Long,
)

private val MESES_DEL_CODIGO = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private const val MINUTO_MS = 60_000L
private const val HORA_MS = 60 * MINUTO_MS
private const val DIA_MS = 24 * HORA_MS

/** «hace un momento», «hace 5 min», «hace 2 h», «hace 3 días». */
fun haceCuantoLlegoElAviso(momento: Long, ahora: Long): String {
    val paso = (ahora - momento).coerceAtLeast(0L)
    return when {
        paso < MINUTO_MS -> "hace un momento"
        paso < HORA_MS -> "hace ${paso / MINUTO_MS} min"
        paso < DIA_MS -> "hace ${paso / HORA_MS} h"
        else -> (paso / DIA_MS).let { if (it == 1L) "hace 1 día" else "hace $it días" }
    }
}

/**
 * **La línea del código vigente**: «Código creado el 2 nov · último aviso hace 2 h», o «… · todavía no
 * llega ningún aviso». `null` si no hay código vigente.
 */
fun lineaDelCodigoDeCaptura(codigo: CodigoDeCaptura, ahora: Long): String? {
    if (!codigo.vigente) return null
    val creado = codigo.creadoEn?.let { epochMillisToAppDate(it) }
        ?.let { "Código creado el ${it.dayOfMonth} ${MESES_DEL_CODIGO[it.monthNumber - 1]}" }
        ?: "Código creado"
    val ultimo = codigo.ultimoUso?.let { "último aviso ${haceCuantoLlegoElAviso(it, ahora)}" } ?: "todavía no llega ningún aviso"
    return "$creado · $ultimo"
}

/**
 * **Las palabras con que cada banco firma sus SMS**, las que la guía sugiere para «El mensaje contiene».
 * Son las mismas del filtro de bancos del server (`PALABRAS_DE_BANCOS`): una palabra que el filtro no
 * conoce haría que el server descartara lo que llega. **Con y sin tilde** donde el banco puede escribirla de
 * las dos formas («Banco de Bogotá» / «Banco de Bogota», «Itaú» / «Itau»): no se sabe si «El mensaje
 * contiene» de Atajos distingue tildes, y una automatización de más no cuesta nada. Scotiabank Colpatria hoy
 * firma como **Davibank** (la app y el banco cambiaron de nombre), y Davibank va con su propio nombre,
 * aunque [grupoDelBanco] lo junte con Davivienda. Nu no está: sus movimientos llegan por la app, no por SMS,
 * y «Nu» suelta dispararía la automatización con casi cualquier mensaje. Los productos de un banco (la
 * AMEX, la Fiducuenta) avisan con el SMS de su banco.
 */
private val PALABRAS_DEL_SMS_DE_CADA_BANCO: Map<String, List<String>> = mapOf(
    "bancolombia" to listOf("Bancolombia"),
    // Vuelta 114: como firma su SMS («DAVIVIENDA Descuento en internet, …»).
    "davivienda" to listOf("DAVIVIENDA"),
    "davibank" to listOf("Davibank"),
    "scotiabank" to listOf("Davibank"),
    "banco_de_bogota" to listOf("Banco de Bogotá", "Banco de Bogota"),
    "bbva" to listOf("BBVA"),
    "occidente" to listOf("Banco de Occidente"),
    "av_villas" to listOf("AV Villas"),
    "itau" to listOf("Itaú", "Itau"),
    "banco_falabella" to listOf("Falabella"),
    "lulo" to listOf("Lulo Bank"),
    "rappipay" to listOf("RappiPay"),
)

private val BILLETERAS_SIN_SMS = setOf("nequi", "daviplata")

/**
 * Vuelta 114 · **las dos tarjetas «Mensaje» que la guía recomienda siempre**: los dos bancos cuyos SMS lee
 * Movi tal como llegan (`Bancolombia: Compraste…`, `DAVIVIENDA Descuento…`), escritas como los firman.
 */
val PALABRAS_DE_LAS_AUTOMATIZACIONES_BASE: List<String> = listOf("Bancolombia", "DAVIVIENDA")

/**
 * **Las palabras que la guía sugiere para «El mensaje contiene»**: una automatización (iOS 17 a 26) o una tarjeta
 * «Mensaje» (iOS 27) por palabra. Desde la vuelta 114 empiezan siempre por [PALABRAS_DE_LAS_AUTOMATIZACIONES_BASE]
 * y siguen con la de cada banco de las cuentas del dueño, en el orden de sus cuentas y sin repetir (sin
 * distinguir mayúsculas: «Davivienda» ya está como «DAVIVIENDA»).
 */
fun palabrasParaLaAutomatizacion(cuentas: List<Account>): List<String> =
    (
        PALABRAS_DE_LAS_AUTOMATIZACIONES_BASE + cuentas.asSequence()
            .filter { !it.esBien }
            .mapNotNull { marcaDeLaCuenta(it) }
            // Las billeteras avisan por su app, no con el SMS del banco dueño.
            .filter { it.id !in BILLETERAS_SIN_SMS }
            .mapNotNull { PALABRAS_DEL_SMS_DE_CADA_BANCO[it.id] ?: PALABRAS_DEL_SMS_DE_CADA_BANCO[grupoDelBanco(it.id)] }
            .flatten()
    ).distinctBy { it.lowercase() }

/**
 * **Por qué no se guardó lo que mandó un atajo** (la columna `motivo` de `atajos_descartados`). Nunca se
 * guarda el texto: solo cuántos por día y motivo.
 */
enum class MotivoDelAtajoDescartado {
    /** Un mensaje que no pasó el filtro de bancos (un SMS personal). */
    NO_ES_DEL_BANCO,
    /**
     * Vuelta 114 · un mensaje sin remitente que nombra un banco pero no tiene la forma de un aviso: no empieza
     * por el banco o no trae monto (una promoción, una clave dinámica). Ver `porQueNoSeGuardaElSmsDelAtajo`.
     */
    SIN_FORMA_DE_AVISO,
    /** Llegó sin texto, o un pago sin ningún dato. */
    VACIO,
    /** Pasó el límite de avisos por minuto de un código (en el server): un atajo en bucle. */
    DEMASIADOS,
}

/** Cuántos atajos se descartaron por un mismo [motivo] (el nombre del enum) en la ventana. */
@Serializable
data class GrupoDeAtajosDescartados(val motivo: String, val cuantos: Int)

/** `GET /api/captura/descartados`: lo que mandaron los atajos del dueño y no se guardó, en los últimos [dias] días. */
@Serializable
data class ResumenDeAtajosDescartados(
    val dias: Int = DIAS_DE_ATAJOS_DESCARTADOS,
    val grupos: List<GrupoDeAtajosDescartados> = emptyList(),
)

/** La ventana que se cuenta, como la de los correos: los últimos 30 días. */
const val DIAS_DE_ATAJOS_DESCARTADOS: Int = 30

/**
 * **La línea de «Desde tu iPhone»**, como la de los correos descartados, o `null` si no hay nada que decir:
 * «12 mensajes no eran de un banco en los últimos 30 días.» y, si pasó, «3 avisos llegaron demasiado
 * seguidos y no se guardaron.» y «2 avisos llegaron vacíos.».
 *
 * Vuelta 114 · lo que nombraba un banco sin la forma de un aviso ([MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO])
 * va aparte: «3 mensajes nombraban un banco pero no tenían la forma de un aviso en los últimos 30 días.», o
 * con los dos, «En los últimos 30 días, 12 mensajes no eran de un banco y 3 nombraban un banco pero no tenían
 * la forma de un aviso.».
 */
fun textoDeAtajosDescartados(resumen: ResumenDeAtajosDescartados): String? {
    fun cuantos(motivo: MotivoDelAtajoDescartado) = resumen.grupos.filter { it.motivo == motivo.name }.sumOf { it.cuantos }
    val noDelBanco = cuantos(MotivoDelAtajoDescartado.NO_ES_DEL_BANCO)
    val sinForma = cuantos(MotivoDelAtajoDescartado.SIN_FORMA_DE_AVISO)
    val demasiados = cuantos(MotivoDelAtajoDescartado.DEMASIADOS)
    val vacios = cuantos(MotivoDelAtajoDescartado.VACIO)
    val ventana = "en los últimos ${resumen.dias} días"
    fun noEran(n: Int) = if (n == 1) "1 mensaje no era de un banco" else "$n mensajes no eran de un banco"
    fun sinFormaDeAviso(n: Int, conMensajes: Boolean = true) =
        (if (n == 1) "1${if (conMensajes) " mensaje" else ""} nombraba un banco pero no tenía la forma de un aviso"
        else "$n${if (conMensajes) " mensajes" else ""} nombraban un banco pero no tenían la forma de un aviso")
    val frases = buildList {
        when {
            noDelBanco > 0 && sinForma > 0 ->
                add("${ventana.replaceFirstChar { it.uppercase() }}, ${noEran(noDelBanco)} y ${sinFormaDeAviso(sinForma, conMensajes = false)}.")
            noDelBanco > 0 -> add("${noEran(noDelBanco)} $ventana.")
            sinForma > 0 -> add("${sinFormaDeAviso(sinForma)} $ventana.")
        }
        if (demasiados > 0) {
            add(if (demasiados == 1) "1 aviso llegó demasiado seguido y no se guardó." else "$demasiados avisos llegaron demasiado seguidos y no se guardaron.")
        }
        if (vacios > 0) add(if (vacios == 1) "1 aviso llegó vacío." else "$vacios avisos llegaron vacíos.")
    }
    return frases.takeIf { it.isNotEmpty() }?.joinToString(" ")
}
