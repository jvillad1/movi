package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # «Compartir con Movi»: lo que Movi leyó de un papel
 *
 * Ola 2 · Movi lee papeles. El 93 % de los movimientos del dueño se anotaban a mano y casi todos los
 * arreglos por `psql` eran cifras que ya estaban escritas en un papel del banco —un pantallazo de una
 * transferencia, un recibo, un extracto—. La puerta nueva: el dueño comparte la imagen o el PDF con
 * Movi (o lo sube en la web), el server lo lee con Claude y lo deja **donde ya revisa todo**.
 *
 * ## Por qué un comprobante vive en `sms_messages`
 *
 * La propuesta de un comprobante es una fila más de la bandeja de mensajes del banco, con
 * `bank = «Comprobante · <archivo>»` y el texto que leyó Claude — igual que hizo el correo entrante
 * (`CorreoEntranteRoutes`). Así hereda **todo** el camino que ya existe y que costó once olas dejar
 * bien: «Revisar» abre la misma pantalla, la memoria de categorías (`conLoQueMoviRecuerda`), las
 * cuentas de otros, «¿Ya lo anotaste?» con su «Es este», la cuenta por el número, Confirmar e
 * Ignorar. Una tabla propia habría pedido una bandeja, un detalle, un parseo y un confirmar nuevos:
 * cuatro copias de reglas que ya están probadas.
 *
 * Lo que la distingue es el **id**: `cmp_<id del documento>` ([idDeComprobante]). Con eso el server
 * sabe que su `/parse` no se lee con el parser de SMS sino con la lectura guardada, y la captura no
 * la cuenta como un mensaje del banco que llegó (ver [soloLoQueLlegoSolo]): un comprobante lo
 * mandó el dueño, y contarlo taparía una captura muda.
 */

/** El prefijo del id de una propuesta que salió de un comprobante. */
const val PREFIJO_DE_COMPROBANTE: String = "cmp_"

/** El rótulo de origen de un comprobante, antes del nombre del archivo. */
const val ORIGEN_COMPROBANTE: String = "Comprobante"

/** El id en la bandeja de la propuesta que salió del documento [documentoId]. */
fun idDeComprobante(documentoId: String): String = PREFIJO_DE_COMPROBANTE + documentoId

/** ¿Este id de la bandeja es la propuesta de un comprobante? */
fun esIdDeComprobante(id: String): Boolean = id.startsWith(PREFIJO_DE_COMPROBANTE)

/** ¿Esta fila de la bandeja la dejó un comprobante y no la captura del banco? */
fun esUnComprobante(sms: SmsMessage): Boolean = esIdDeComprobante(sms.id)

/** El documento del que salió la propuesta [smsId], o `null` si no es un comprobante. */
fun documentoDelComprobante(smsId: String): String? =
    smsId.takeIf { esIdDeComprobante(it) }?.removePrefix(PREFIJO_DE_COMPROBANTE)?.takeIf { it.isNotBlank() }

/**
 * «Comprobante · transferencia.jpg». La columna `bank` es `varchar(100)`: se recorta el NOMBRE, no
 * el rótulo, para que siempre se lea qué es.
 */
fun rotuloDeComprobante(nombreDelArchivo: String): String {
    val prefijo = "$ORIGEN_COMPROBANTE · "
    val nombre = nombreDelArchivo.trim().ifBlank { "archivo" }
    return prefijo + nombre.take(100 - prefijo.length)
}

/**
 * **Lo que llegó solo**: los mensajes del banco sin los comprobantes. Todo lo que mide la captura
 * —«nunca llegó nada», el último mensaje, el banco mudo— mira esto y no la bandeja entera: un
 * comprobante que el dueño compartió hoy no prueba que el teléfono siga capturando.
 */
fun soloLoQueLlegoSolo(mensajes: List<SmsMessage>): List<SmsMessage> = mensajes.filterNot { esUnComprobante(it) }

/** Qué resultó ser el papel. */
@Serializable
enum class QueEsElPapel {
    /** Un solo movimiento: un pantallazo de transferencia, un recibo, un pago PSE, una factura. */
    COMPROBANTE,

    /** Varios movimientos: va por el importador de extractos de siempre. */
    EXTRACTO,
}

/**
 * La respuesta de `POST /api/documents/{id}/leer`.
 *
 * - [QueEsElPapel.COMPROBANTE]: [porRevisarId] es la fila que quedó en «Por revisar» (o la que ya
 *   estaba, con [yaEstabaEnLaBandeja]); [leido] es lo que Movi entendió, para decirlo en la hoja. Si
 *   [yaAnotado] no está vacío, el movimiento ya existía y **no** se creó la propuesta
 *   ([porRevisarId] en `null`): la hoja dice «Ya lo tienes anotado» y ofrece anotarlo igual.
 * - [QueEsElPapel.EXTRACTO]: [extracto] es lo mismo que devuelve el importador, para abrir su
 *   pantalla de revisión.
 */
@Serializable
data class LecturaDelPapel(
    val documentoId: String,
    val que: QueEsElPapel,
    val porRevisarId: String? = null,
    val yaEstabaEnLaBandeja: Boolean = false,
    /** El estado de esa fila ([SMS_STATE_PENDING], confirmada o ignorada) cuando ya estaba. */
    val estadoEnLaBandeja: String? = null,
    val leido: ParsedSms? = null,
    /** Cuándo pasó, como lo guarda la bandeja (`yyyy-MM-dd HH:mm`). */
    val cuando: String? = null,
    val yaAnotado: List<FinancialEvent> = emptyList(),
    val extracto: StatementParseResult? = null,
)

/** La nota con que queda guardado en Documentos un papel compartido con Movi. */
const val NOTA_DEL_PAPEL_COMPARTIDO: String = "Compartido con Movi"

/** Lo que dice la app cuando el server no pudo leer el papel. El archivo nunca se pierde. */
const val NO_SE_PUDO_LEER_EL_PAPEL: String = "No pudimos leer el archivo; queda guardado en Documentos."
