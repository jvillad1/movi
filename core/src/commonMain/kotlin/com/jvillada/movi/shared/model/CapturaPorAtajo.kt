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

/** Donde el server sirve el atajo de mensajes ya firmado (si la firma salió). */
const val RUTA_DEL_ATAJO_DE_MENSAJES: String = "/atajos/movi-mensaje.shortcut"

/** Donde el server sirve el atajo de Apple Pay ya firmado (si la firma salió). */
const val RUTA_DEL_ATAJO_DE_APPLE_PAY: String = "/atajos/movi-apple-pay.shortcut"

/** El nombre del atajo que avisa un SMS, tal como queda en la app Atajos. */
const val NOMBRE_DEL_ATAJO_DE_MENSAJES: String = "Movi · Avisar un mensaje"

/** El nombre del atajo que avisa un pago con Apple Pay. */
const val NOMBRE_DEL_ATAJO_DE_APPLE_PAY: String = "Movi · Pago con Apple Pay"

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
 * **La palabra con que cada banco firma sus SMS**, la que la guía sugiere para «El mensaje contiene».
 * Son las mismas del filtro de bancos del server (`PALABRAS_DE_BANCOS`): una palabra que el filtro no
 * conoce haría que el server descartara lo que llega. Nu no está: sus movimientos llegan por la app, no
 * por SMS, y «Nu» suelta dispararía la automatización con casi cualquier mensaje. Los productos de un
 * banco (la AMEX, la Fiducuenta, Nequi) avisan con el SMS de su banco ([grupoDelBanco]).
 */
private val PALABRA_DEL_SMS_DE_CADA_BANCO: Map<String, String> = mapOf(
    "bancolombia" to "Bancolombia",
    "davivienda" to "Davivienda",
    "banco_de_bogota" to "Banco de Bogotá",
    "bbva" to "BBVA",
    "occidente" to "Banco de Occidente",
    "av_villas" to "AV Villas",
    "itau" to "Itaú",
    "scotiabank" to "Colpatria",
    "banco_falabella" to "Falabella",
    "lulo" to "Lulo Bank",
    "rappipay" to "RappiPay",
)

private val BILLETERAS_SIN_SMS = setOf("nequi", "daviplata")

/**
 * **Las palabras que la guía sugiere para «El mensaje contiene»**: la de cada banco de las cuentas del
 * dueño (una automatización por banco), en el orden de sus cuentas y sin repetir. Sin ninguna, Bancolombia,
 * que es el que más SMS manda.
 */
fun palabrasParaLaAutomatizacion(cuentas: List<Account>): List<String> =
    cuentas.asSequence()
        .filter { !it.esBien }
        .mapNotNull { marcaDeLaCuenta(it) }
        // Las billeteras avisan por su app, no con el SMS del banco dueño.
        .filter { it.id !in BILLETERAS_SIN_SMS }
        .mapNotNull { PALABRA_DEL_SMS_DE_CADA_BANCO[grupoDelBanco(it.id)] ?: PALABRA_DEL_SMS_DE_CADA_BANCO[it.id] }
        .distinct()
        .toList()
        .ifEmpty { listOf("Bancolombia") }
