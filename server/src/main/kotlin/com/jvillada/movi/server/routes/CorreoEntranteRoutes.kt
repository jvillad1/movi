package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.ConfigDeCorreoEntrante
import com.jvillada.movi.server.correo.ContenidoDeResend
import com.jvillada.movi.server.correo.CorreoEntrante
import com.jvillada.movi.server.correo.EVENTO_CORREO_RECIBIDO
import com.jvillada.movi.server.correo.FirmaDeSvix
import com.jvillada.movi.server.correo.LectorDeCorreosRecibidos
import com.jvillada.movi.server.correo.MAX_CUERPO_DE_CORREO_BYTES
import com.jvillada.movi.server.correo.correoDeResend
import com.jvillada.movi.server.correo.direccionDeReenvio
import com.jvillada.movi.server.correo.esElMismoSecreto
import com.jvillada.movi.server.correo.idDeCorreo
import com.jvillada.movi.server.correo.leerCorreoEntrante
import com.jvillada.movi.server.correo.leerEventoDeResend
import com.jvillada.movi.server.correo.marcaDeOrigenDelCorreo
import com.jvillada.movi.server.correo.momentoDelCorreo
import com.jvillada.movi.server.correo.secretoPresentado
import com.jvillada.movi.server.correo.textoDelCorreo
import com.jvillada.movi.server.correo.tokenDeCorreoDe
import com.jvillada.movi.server.correo.tokensDeLosDestinatarios
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.plugins.userId
import com.jvillada.movi.server.sms.SmsDedupeIndex
import com.jvillada.movi.server.sms.SmsKey
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

/**
 * Lo que contesta el webhook. Siempre JSON y siempre con [motivo] cuando no se guardó: el dueño va
 * a mirar el log de entregas del proveedor cuando esto no ande, y «202 sin cuerpo» no le dice si el
 * problema es la dirección, el filtro de Gmail o el secreto.
 *
 * Nunca lleva el texto del correo ni el secreto — ese log es de un tercero.
 */
@Serializable
private data class RespuestaDelCorreo(
    val guardado: Boolean,
    val motivo: String? = null,
    val id: String? = null,
)

/** La dirección de reenvío de quien pregunta. Ver [direccionDeCorreoRoutes]. */
@Serializable
private data class DireccionDeCorreo(
    val token: String,
    val direccion: String? = null,
    val configurado: Boolean,
)

/**
 * **Cuántos correos por minuto se aceptan con un mismo secreto.** Una bandeja bancaria manda unos
 * pocos avisos por hora; 120 por minuto es tres órdenes de magnitud por encima de lo normal y sigue
 * cortando en seco a quien consiga el secreto e intente llenar la bandeja.
 */
private const val MAX_CORREOS_POR_MINUTO = 120
private const val VENTANA_DE_CORREO_MS = 60_000L

/**
 * # `POST /api/correo-entrante` — el webhook del proveedor de correo
 *
 * **Pública porque tiene que serlo**: Postmark o Mailgun no tienen —ni pueden tener— una sesión de
 * Movi. Lo que la protege es un secreto compartido (`INBOUND_EMAIL_SECRET`), y lo que decide de
 * quién es cada correo es el token de la dirección a la que llegó (ver [tokenDelDestinatario]).
 *
 * Las respuestas, y por qué cada una:
 *
 * | Caso | Código | Por qué |
 * |---|---|---|
 * | sin `INBOUND_EMAIL_SECRET` | 503 | la función está apagada; nada entra, y el dueño ve por qué |
 * | secreto ausente o distinto | 401 | |
 * | más de [MAX_CORREOS_POR_MINUTO] | 429 | |
 * | cuerpo > [MAX_CUERPO_DE_CORREO_BYTES] | 413 | ver ese KDoc |
 * | cuerpo ilegible | 400 | ni JSON ni formulario |
 * | sin token, o token de nadie | 202 | **no se escribe nada** — reintentar no lo va a arreglar |
 * | ya estaba (dedupe) | 202 | |
 * | guardado | 202 | |
 *
 * Los casos «no pude» contestan 2xx a propósito: un proveedor reintenta con backoff ante un 5xx, y
 * un correo mal dirigido no mejora por reintentarlo veinte veces.
 */
fun Route.correoEntranteRoutes() {
    post("/api/correo-entrante") {
        val secreto = ConfigDeCorreoEntrante.secreto()
        if (secreto.isNullOrBlank()) {
            return@post call.respond(
                HttpStatusCode.ServiceUnavailable,
                RespuestaDelCorreo(guardado = false, motivo = "El correo entrante no está configurado en este servidor."),
            )
        }

        val presentado = secretoPresentado(
            authorization = call.request.headers[HttpHeaders.Authorization],
            cabeceraPropia = call.request.headers["X-Movi-Correo-Secreto"],
        )
        if (presentado == null || !esElMismoSecreto(presentado, secreto)) {
            // Sin decir cuál de las dos cosas falló, y sin imprimir NADA de lo que llegó.
            call.application.log.warn("correo entrante rechazado: secreto ausente o distinto")
            return@post call.respond(
                HttpStatusCode.Unauthorized,
                RespuestaDelCorreo(guardado = false, motivo = "No autorizado."),
            )
        }

        if (!RateLimiter.allow(
                "correo-entrante:${ConfigDeCorreoEntrante.baldeDelSecreto(secreto)}",
                maxAttempts = MAX_CORREOS_POR_MINUTO,
                windowMs = VENTANA_DE_CORREO_MS,
            )
        ) {
            return@post call.respond(
                HttpStatusCode.TooManyRequests,
                RespuestaDelCorreo(guardado = false, motivo = "Demasiados correos seguidos."),
            )
        }

        // Dos veces: el `Content-Length` corta ANTES de leer nada (que es de lo que se trata), y el
        // largo real cubre al cliente que no lo manda o que miente.
        val anunciado = call.request.contentLength()
        if (anunciado != null && anunciado > MAX_CUERPO_DE_CORREO_BYTES) {
            return@post call.respond(
                HttpStatusCode.PayloadTooLarge,
                RespuestaDelCorreo(guardado = false, motivo = "El correo es demasiado grande."),
            )
        }
        val crudo = call.receiveText()
        if (crudo.toByteArray().size > MAX_CUERPO_DE_CORREO_BYTES) {
            return@post call.respond(
                HttpStatusCode.PayloadTooLarge,
                RespuestaDelCorreo(guardado = false, motivo = "El correo es demasiado grande."),
            )
        }

        val correo = leerCorreoEntrante(crudo)
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                RespuestaDelCorreo(guardado = false, motivo = "No pude leer el correo."),
            )

        val (estado, respuesta) = guardarCorreoEntrante(correo)
        call.respond(estado, respuesta)
    }

    post("/api/correo-entrante/resend") { correoDeResendRoute(call) }
}

/**
 * `GET /api/correo-entrante/direccion` — **a qué dirección tiene que reenviar quien pregunta.**
 *
 * Autenticada y de solo lectura. Existe porque el token se deriva del id de usuario (ver
 * [tokenDeCorreoDe]) y si no hay forma de leerlo, configurar el reenvío exige entrar por `psql` —
 * que es justo lo que este repo decidió no volver a pedirle al dueño. No hay pantalla nueva: es un
 * dato que se lee una vez, con `curl` y el token de sesión, el día que se arma el filtro de Gmail.
 */
fun Route.direccionDeCorreoRoutes() {
    get("/api/correo-entrante/direccion") {
        val uid = call.userId()
        val token = tokenDeCorreoDe(uid)
        val base = ConfigDeCorreoEntrante.direccionBase()
        call.respond(
            DireccionDeCorreo(
                token = token,
                direccion = base?.let { direccionDeReenvio(it, token) },
                configurado = ConfigDeCorreoEntrante.estaConfigurado(),
            ),
        )
    }
}

/**
 * **El camino único de un correo ya leído**, venga del proveedor que venga: atribución por el token
 * de la dirección, el MISMO dedupe que `/api/sms/sync`, la MISMA decisión de si es un movimiento
 * (`comoLlega` → `queEsEsteMensaje`) y la MISMA tabla `sms_messages`. Postmark/Mailgun y Resend
 * solo difieren en cómo llegan hasta un [CorreoEntrante]; de acá en adelante no hay dos versiones
 * de nada.
 *
 * Devuelve el código y el cuerpo que se contestan: 202 en todos los casos (guardado, ya estaba, o
 * sin dueño — reintentar no arregla una dirección mal armada).
 */
private suspend fun guardarCorreoEntrante(correo: CorreoEntrante): Pair<HttpStatusCode, RespuestaDelCorreo> {
    val tokens = tokensDeLosDestinatarios(correo.destinatarios)
    if (tokens.isEmpty()) {
        return HttpStatusCode.Accepted to
            RespuestaDelCorreo(guardado = false, motivo = "La dirección de destino no trae el token de ningún usuario.")
    }

    // La tabla de usuarios de Movi tiene un puñado de filas y el token se deriva del id, así que
    // se resuelve recorriéndolas. Si algún día son miles, esto pide una columna indexada — no
    // una tabla nueva. Gana el primer token (en orden de confianza) que sea de alguien.
    val uid = dbQuery {
        val porToken = Users.selectAll().map { it[Users.id] }.associateBy { tokenDeCorreoDe(it) }
        tokens.firstNotNullOfOrNull { porToken[it] }
    } ?: return HttpStatusCode.Accepted to
        RespuestaDelCorreo(guardado = false, motivo = "Esa dirección no corresponde a ninguna cuenta.")

    val texto = textoDelCorreo(correo.asunto, correo.cuerpo)
    val tiempo = momentoDelCorreo(correo.fecha, ahora = System.currentTimeMillis())
    val id = idDeCorreo(correo.idDelMensaje, texto, tiempo)
    val marca = marcaDeOrigenDelCorreo(correo.nombreDelRemitente, correo.remitente)

    val guardado = dbQuery {
        val yaEstaPorId = SmsMessages.selectAll()
            .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
            .any()
        if (yaEstaPorId) return@dbQuery false

        // El MISMO dedupe que `/api/sms/sync`, con la misma clave (texto + tiempo): una alerta
        // que ya llegó por SMS o por notificación no vuelve a entrar por correo. Ver `SmsDedupe`
        // para por qué el texto solo no alcanza y por qué el `bank` queda fuera de la clave.
        val existentes = SmsMessages.selectAll()
            .where { SmsMessages.userId eq uid }
            .map { SmsKey(it[SmsMessages.text], it[SmsMessages.time]) }
        if (SmsDedupeIndex(existentes).isDuplicate(SmsKey(texto, tiempo))) return@dbQuery false

        // El server es dueño del estado, igual que en el sync: llega «por confirmar» y son
        // /confirm y /ignore los que lo mueven — salvo lo que no es un movimiento (el «tu
        // extracto está listo», una promoción, la confirmación de reenvío de Gmail), que entra
        // apartado con su motivo.
        val (estado, motivo) = comoLlega(texto, marca)
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = uid
            it[time] = tiempo
            it[bank] = marca
            it[text] = texto
            it[state] = estado
            it[motivoApartado] = motivo?.name
            it[det] = ""
        }
        true
    }

    return HttpStatusCode.Accepted to
        if (guardado) RespuestaDelCorreo(guardado = true, id = id)
        else RespuestaDelCorreo(guardado = false, motivo = "Ese correo ya estaba en la bandeja.")
}

/**
 * # `POST /api/correo-entrante/resend` — el webhook `email.received` de Resend
 *
 * Pública como la de arriba, pero con otra puerta: **la firma Svix** del webhook
 * (`RESEND_WEBHOOK_SECRET`, ver [FirmaDeSvix]). Resend no deja poner una contraseña en la URL ni
 * una cabecera propia; firma cada entrega, y eso es mejor: la firma cubre el cuerpo, y el
 * timestamp impide reenviar una entrega vieja.
 *
 * El webhook no trae el cuerpo del correo: con su `email_id` se pide el contenido a la API
 * ([LectorDeCorreosRecibidos]) y se arma un [CorreoEntrante] ([correoDeResend]), que sigue por
 * [guardarCorreoEntrante] como cualquier otro.
 *
 * | Caso | Código | Por qué |
 * |---|---|---|
 * | sin `RESEND_WEBHOOK_SECRET` | 503 | apagado; nada entra |
 * | firma ausente, inválida o timestamp a más de 5 min | 401 | no se lee nada más |
 * | más de [MAX_CORREOS_POR_MINUTO] | 429 | |
 * | cuerpo > [MAX_CUERPO_DE_CORREO_BYTES] | 413 | |
 * | otro evento (`email.sent`, …) | 200 | no es asunto de esta ruta; reintentarlo no sirve |
 * | sin clave de API en el server | 503 | Resend reintenta; cuando la clave esté, entra |
 * | la API falla o no encuentra el correo | 502 | **Resend reintenta** (5 s, 5 min, 30 min, 2 h…) |
 * | sin token, ya estaba, guardado | 202 | igual que la de arriba |
 */
private suspend fun correoDeResendRoute(call: ApplicationCall) {
    val secreto = ConfigDeCorreoEntrante.secretoDeResend()
    if (secreto.isNullOrBlank()) {
        return call.respond(
            HttpStatusCode.ServiceUnavailable,
            RespuestaDelCorreo(guardado = false, motivo = "El correo entrante por Resend no está configurado en este servidor."),
        )
    }

    val anunciado = call.request.contentLength()
    if (anunciado != null && anunciado > MAX_CUERPO_DE_CORREO_BYTES) {
        return call.respond(
            HttpStatusCode.PayloadTooLarge,
            RespuestaDelCorreo(guardado = false, motivo = "El evento es demasiado grande."),
        )
    }
    // Los bytes crudos, no el texto: la firma se calcula sobre el cuerpo exacto que mandó Resend.
    val crudo = call.receive<ByteArray>()
    if (crudo.size > MAX_CUERPO_DE_CORREO_BYTES) {
        return call.respond(
            HttpStatusCode.PayloadTooLarge,
            RespuestaDelCorreo(guardado = false, motivo = "El evento es demasiado grande."),
        )
    }

    val cabeceras = call.request.headers
    val firmaValida = FirmaDeSvix.esValida(
        secreto = secreto,
        id = cabeceras["svix-id"] ?: cabeceras["webhook-id"],
        timestamp = cabeceras["svix-timestamp"] ?: cabeceras["webhook-timestamp"],
        firmas = cabeceras["svix-signature"] ?: cabeceras["webhook-signature"],
        cuerpo = crudo,
        ahoraSegundos = System.currentTimeMillis() / 1000,
    )
    if (!firmaValida) {
        call.application.log.warn("correo entrante (Resend) rechazado: firma ausente, inválida o vieja")
        return call.respond(HttpStatusCode.Unauthorized, RespuestaDelCorreo(guardado = false, motivo = "No autorizado."))
    }

    if (!RateLimiter.allow(
            "correo-entrante-resend:${ConfigDeCorreoEntrante.baldeDelSecreto(secreto)}",
            maxAttempts = MAX_CORREOS_POR_MINUTO,
            windowMs = VENTANA_DE_CORREO_MS,
        )
    ) {
        return call.respond(
            HttpStatusCode.TooManyRequests,
            RespuestaDelCorreo(guardado = false, motivo = "Demasiados correos seguidos."),
        )
    }

    val evento = leerEventoDeResend(String(crudo, Charsets.UTF_8))
        ?: return call.respond(
            HttpStatusCode.Accepted,
            RespuestaDelCorreo(guardado = false, motivo = "No pude leer el evento."),
        )
    if (evento.tipo != EVENTO_CORREO_RECIBIDO) {
        return call.respond(HttpStatusCode.OK, RespuestaDelCorreo(guardado = false, motivo = "Evento ignorado."))
    }
    val idDelCorreo = evento.idDelCorreo
        ?: return call.respond(
            HttpStatusCode.Accepted,
            RespuestaDelCorreo(guardado = false, motivo = "El evento no trae el id del correo."),
        )

    val correo = when (val contenido = LectorDeCorreosRecibidos.actual.traer(idDelCorreo)) {
        is ContenidoDeResend.Encontrado -> correoDeResend(contenido.json, evento)
            ?: return call.respond(
                HttpStatusCode.Accepted,
                RespuestaDelCorreo(guardado = false, motivo = "No pude leer el correo."),
            )
        ContenidoDeResend.SinClave -> return call.respond(
            HttpStatusCode.ServiceUnavailable,
            RespuestaDelCorreo(guardado = false, motivo = "Falta la clave de la API de Resend en el servidor."),
        )
        ContenidoDeResend.DemasiadoGrande -> return call.respond(
            HttpStatusCode.Accepted,
            RespuestaDelCorreo(guardado = false, motivo = "El correo es demasiado grande."),
        )
        ContenidoDeResend.NoEncontrado -> return call.respond(
            HttpStatusCode.BadGateway,
            RespuestaDelCorreo(guardado = false, motivo = "Resend no encontró ese correo."),
        )
        is ContenidoDeResend.Fallo -> return call.respond(
            HttpStatusCode.BadGateway,
            RespuestaDelCorreo(guardado = false, motivo = "No pude traer el correo de Resend."),
        )
    }

    val (estado, respuesta) = guardarCorreoEntrante(correo)
    call.respond(estado, respuesta)
}
