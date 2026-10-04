package com.jvillada.movi.server.correo

/**
 * **Dos alertas de Bancolombia por correo, en las dos formas que llegan.**
 *
 * Están escritas a partir de las frases que el banco ya usa en los SMS reales de
 * `SmsParseTest` —los números de producto son los mismos enmascarados de ahí (`*1111`, `*3333`)—
 * envueltas en el saludo, el pie legal y la firma que trae un correo y no trae un SMS. Eso es
 * justo lo que estas muestras tienen que ejercitar: que la envoltura no le cambie la lectura al
 * parser.
 *
 * La cuota de manejo del cupo rotativo es el caso que motivó todo esto: un cobro que el banco **se
 * hace solo** y que no manda por SMS ni publica como notificación.
 */

/** El texto que el MISMO movimiento tendría si el banco lo hubiera mandado por SMS. */
const val SMS_EQUIVALENTE_CUOTA_DE_MANEJO: String =
    "Bancolombia: Pago cuota de manejo cupo rotativo por \$21.640 en tu tarjeta de credito *1111, el 16/09/2026."

const val SMS_EQUIVALENTE_DEBITO_AUTOMATICO: String =
    "Bancolombia: Pagaste \$138,600.00 a Salud Total S A desde tu producto 3333 el 05/09/2026 17:16:47."

const val ASUNTO_CUOTA_DE_MANEJO = "Bancolombia te informa"
const val ASUNTO_DEBITO_AUTOMATICO = "Alerta Bancolombia"

/**
 * El cuerpo de texto plano tal como lo manda el banco: el aviso en una línea y, alrededor, lo que
 * un correo siempre trae de más.
 */
val CUERPO_CUOTA_DE_MANEJO: String = """
    Hola Juan,

    $SMS_EQUIVALENTE_CUOTA_DE_MANEJO

    Si no reconoces este movimiento, comunicate con nosotros.

    --
    Bancolombia S.A. Vigilado Superintendencia Financiera.
""".trimIndent()

val CUERPO_DEBITO_AUTOMATICO: String = """
    Hola Juan,

    $SMS_EQUIVALENTE_DEBITO_AUTOMATICO

    Este correo es informativo.
""".trimIndent()

/** Escapa un texto de varias líneas para meterlo dentro de un string JSON. */
private fun aJson(texto: String): String =
    texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

/**
 * El payload de **Postmark** (`application/json`), con los nombres de campo que documenta:
 * `OriginalRecipient` es el destinatario de SOBRE, que es el único que el reenvío de Gmail no
 * reescribe — ver `tokenDelDestinatario`.
 */
fun alertaPostmark(
    destinatario: String,
    asunto: String = ASUNTO_CUOTA_DE_MANEJO,
    cuerpo: String = CUERPO_CUOTA_DE_MANEJO,
    messageId: String = "8d1c4f2a-postmark@bancolombia.com.co",
    fecha: String = "Wed, 16 Sep 2026 03:12:41 -0500",
): String = """
{
  "From": "Bancolombia <alertasynotificaciones@notificacionesbancolombia.com>",
  "FromName": "Bancolombia",
  "FromFull": { "Email": "alertasynotificaciones@notificacionesbancolombia.com", "Name": "Bancolombia" },
  "To": "juan@gmail.com",
  "ToFull": [ { "Email": "juan@gmail.com", "Name": "", "MailboxHash": "" } ],
  "OriginalRecipient": "$destinatario",
  "Subject": "${aJson(asunto)}",
  "MessageID": "$messageId",
  "Date": "$fecha",
  "TextBody": "${aJson(cuerpo)}",
  "HtmlBody": "<html><body><p>${aJson(cuerpo)}</p></body></html>",
  "Attachments": []
}
""".trimIndent()

/**
 * El payload de **Mailgun**. Otros nombres (`body-plain`, `recipient`), un epoch en segundos en vez
 * de una fecha RFC 1123, y el cuerpo posteado como formulario y no como JSON: los tres son motivos
 * por los que `leerCorreoEntrante` no puede ser un `@Serializable` por proveedor.
 */
fun alertaMailgunFormulario(
    destinatario: String,
    asunto: String = ASUNTO_DEBITO_AUTOMATICO,
    cuerpo: String = CUERPO_DEBITO_AUTOMATICO,
    messageId: String = "<20260905171647.1.abc@bancolombia.com.co>",
    epochSegundos: Long = 1_788_646_607L,
): String = listOf(
    "recipient" to destinatario,
    "sender" to "alertasynotificaciones@notificacionesbancolombia.com",
    "from" to "Bancolombia <alertasynotificaciones@notificacionesbancolombia.com>",
    "To" to "juan@gmail.com",
    "subject" to asunto,
    "body-plain" to cuerpo,
    "stripped-text" to cuerpo,
    "Message-Id" to messageId,
    "timestamp" to epochSegundos.toString(),
).joinToString("&") { (clave, valor) -> "${urlEncode(clave)}=${urlEncode(valor)}" }

private fun urlEncode(texto: String): String =
    java.net.URLEncoder.encode(texto, Charsets.UTF_8)

/** La misma alerta de Mailgun, pero en JSON (algunos reenvíos y los tests de Resend la mandan así). */
fun alertaMailgunJson(
    destinatario: String,
    asunto: String = ASUNTO_DEBITO_AUTOMATICO,
    cuerpo: String = CUERPO_DEBITO_AUTOMATICO,
    messageId: String = "<20260905171647.2.abc@bancolombia.com.co>",
    epochSegundos: Long = 1_788_646_607L,
): String = """
{
  "recipient": "$destinatario",
  "sender": "alertasynotificaciones@notificacionesbancolombia.com",
  "from": "Bancolombia <alertasynotificaciones@notificacionesbancolombia.com>",
  "To": "juan@gmail.com",
  "subject": "${aJson(asunto)}",
  "body-plain": "${aJson(cuerpo)}",
  "body-html": "<html><body>${aJson(cuerpo)}</body></html>",
  "Message-Id": "${aJson(messageId)}",
  "timestamp": $epochSegundos
}
""".trimIndent()

/**
 * El webhook `email.received` de **Resend**, con la forma que documenta
 * https://resend.com/docs/webhooks/emails/received: solo metadatos, sin cuerpo. El `to` es la
 * cabecera del correo reenviado por Gmail (el Gmail del dueño); la dirección de Movi viene en
 * `received_for`.
 */
fun eventoDeResend(
    idDelCorreo: String = "56761188-7520-42d8-8898-ff6fc54ce618",
    tipo: String = "email.received",
    recibidoPara: List<String> = emptyList(),
    para: List<String> = listOf("juan@gmail.com"),
): String = """
{
  "type": "$tipo",
  "created_at": "2026-09-16T08:12:43.126Z",
  "data": {
    "email_id": "$idDelCorreo",
    "created_at": "2026-09-16T08:12:42.894Z",
    "from": "alertasynotificaciones@notificacionesbancolombia.com",
    "to": [${para.joinToString(", ") { "\"$it\"" }}],
    "bcc": [],
    "cc": [],
    "received_for": [${recibidoPara.joinToString(", ") { "\"$it\"" }}],
    "message_id": "<8d1c4f2a-resend@bancolombia.com.co>",
    "subject": "${aJson(ASUNTO_CUOTA_DE_MANEJO)}",
    "attachments": []
  }
}
""".trimIndent()

/**
 * Lo que contesta `GET /emails/receiving/{id}` para esa alerta
 * (https://resend.com/docs/api-reference/emails/retrieve-received-email): `text`, `html` y
 * `headers` (objeto, nombres en minúsculas). Un reenvío automático de Gmail conserva `From:` y
 * `To:` del banco y agrega `X-Forwarded-To` con la dirección de destino.
 */
fun contenidoDeResend(
    recibidoPara: List<String> = emptyList(),
    reenviadoA: String? = null,
    para: List<String> = listOf("juan@gmail.com"),
    asunto: String = ASUNTO_CUOTA_DE_MANEJO,
    cuerpo: String? = CUERPO_CUOTA_DE_MANEJO,
    html: String? = null,
    remitente: String = "alertasynotificaciones@notificacionesbancolombia.com",
    nombreDelRemitente: String = "Bancolombia",
    messageId: String = "<8d1c4f2a-resend@bancolombia.com.co>",
    fecha: String = "Wed, 16 Sep 2026 03:12:41 -0500",
): String {
    val cabeceras = buildList {
        add("\"from\": \"${aJson(nombreDelRemitente)} <$remitente>\"")
        add("\"to\": \"${para.joinToString(", ")}\"")
        add("\"date\": \"$fecha\"")
        add("\"message-id\": \"$messageId\"")
        if (reenviadoA != null) {
            add("\"x-forwarded-to\": \"$reenviadoA\"")
            add("\"x-forwarded-for\": \"juan@gmail.com $reenviadoA\"")
        }
        add("\"delivered-to\": \"juan@gmail.com\"")
    }
    return """
{
  "object": "email",
  "id": "56761188-7520-42d8-8898-ff6fc54ce618",
  "to": [${para.joinToString(", ") { "\"$it\"" }}],
  "from": "$remitente",
  "created_at": "2026-09-16T08:12:42.894Z",
  "subject": "${aJson(asunto)}",
  "html": ${html?.let { "\"${aJson(it)}\"" } ?: "null"},
  "html_format": "cid",
  "text": ${cuerpo?.let { "\"${aJson(it)}\"" } ?: "null"},
  "headers": { ${cabeceras.joinToString(", ")} },
  "bcc": [],
  "cc": [],
  "reply_to": [],
  "received_for": [${recibidoPara.joinToString(", ") { "\"$it\"" }}],
  "authentication": { "spf": "pass", "dkim": "pass", "dmarc": "pass" },
  "message_id": "$messageId",
  "raw": null,
  "attachments": []
}
""".trimIndent()
}
