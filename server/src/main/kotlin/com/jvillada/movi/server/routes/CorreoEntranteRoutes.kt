package com.jvillada.movi.server.routes

import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.ConfigDeCorreoEntrante
import com.jvillada.movi.server.correo.ContenidoDeResend
import com.jvillada.movi.server.correo.CorreoEntrante
import com.jvillada.movi.server.correo.EVENTO_CORREO_RECIBIDO
import com.jvillada.movi.server.correo.FirmaDeSvix
import com.jvillada.movi.server.correo.LectorDeCorreosRecibidos
import com.jvillada.movi.server.correo.MAX_CUERPO_DE_CORREO_BYTES
import com.jvillada.movi.server.correo.EventoDeResend
import com.jvillada.movi.server.correo.correoDeResend
import com.jvillada.movi.server.correo.correoSoloConElEvento
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
// La respuesta de `/direccion` vive en :core: la app la lee (la guía «Primer uso», el paso del correo).
import com.jvillada.movi.shared.model.DireccionDeCorreo
import com.jvillada.movi.shared.model.MotivoDeCorreoDescartado
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
import org.jetbrains.exposed.sql.update

/**
 * Lo que contesta el webhook. Siempre JSON y siempre con [motivo] cuando no se guardó: el dueño va
 * a mirar el log de entregas del proveedor cuando esto no ande, y «202 sin cuerpo» no le dice si el
 * problema es la dirección, el filtro de Gmail o el secreto.
 *
 * Nunca lleva el texto del correo ni el secreto — ese log es de un tercero.
 */
@Serializable
internal data class RespuestaDelCorreo(
    val guardado: Boolean,
    val motivo: String? = null,
    val id: String? = null,
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
            // Vuelta 90: sin leerlo no se sabe de quién es; queda contado como de nadie.
            registrarCorreoDescartado(null, MotivoDeCorreoDescartado.DEMASIADO_GRANDE, tamano = anunciado.toInt())
            return@post call.respond(
                HttpStatusCode.PayloadTooLarge,
                RespuestaDelCorreo(guardado = false, motivo = "El correo es demasiado grande."),
            )
        }
        val crudo = call.receiveText()
        val tamano = crudo.toByteArray().size
        if (tamano > MAX_CUERPO_DE_CORREO_BYTES) {
            registrarCorreoDescartado(null, MotivoDeCorreoDescartado.DEMASIADO_GRANDE, tamano = tamano)
            return@post call.respond(
                HttpStatusCode.PayloadTooLarge,
                RespuestaDelCorreo(guardado = false, motivo = "El correo es demasiado grande."),
            )
        }

        val correo = leerCorreoEntrante(crudo)
        if (correo == null) {
            registrarCorreoDescartado(null, MotivoDeCorreoDescartado.ILEGIBLE, tamano = tamano)
            return@post call.respond(
                HttpStatusCode.BadRequest,
                RespuestaDelCorreo(guardado = false, motivo = "No pude leer el correo."),
            )
        }

        val (estado, respuesta) = guardarCorreoEntrante(correo, tamano = tamano)
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
    /**
     * Vuelta 90 · **los correos que llegaron y no se guardaron** en los últimos 30 días, contados por
     * motivo. Los propios, a cada uno. Los que no son de nadie (sin código, o de un código que no es de
     * ninguna cuenta) **solo a un admin y solo contados**: no se sabe de quién son, así que ni el dominio
     * ni el id salen de la base. Sin las copias (`DUPLICADO`): esas no son una pérdida.
     */
    get("/api/correo-entrante/descartados") {
        call.respond(correosDescartadosDe(call.userId(), System.currentTimeMillis()))
    }

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
 * **De quién es un correo**: el primer token de [destinatarios] (en su orden de confianza) que sea de
 * un usuario. La tabla de usuarios de Movi tiene un puñado de filas y el token se deriva del id, así
 * que se resuelve recorriéndolas. Si algún día son miles, esto pide una columna indexada — no una tabla
 * nueva.
 */
internal suspend fun duenoDelCorreo(destinatarios: List<String>): String? {
    val tokens = tokensDeLosDestinatarios(destinatarios)
    if (tokens.isEmpty()) return null
    return dbQuery {
        val porToken = Users.selectAll().map { it[Users.id] }.associateBy { tokenDeCorreoDe(it) }
        tokens.firstNotNullOfOrNull { porToken[it] }
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
 *
 * **Vuelta 90 · nada se pierde en silencio.** Lo que no se guarda deja su fila en `correos_descartados`
 * ([registrarCorreoDescartado]): sin código, código de nadie. [idDelProveedor] (el `email_id` de Resend)
 * se guarda con la fila —o con su descarte— para que el reproceso diario sepa qué ya se miró; una copia
 * que ya estaba se anota como `DUPLICADO` (no se muestra) por lo mismo. Idempotente por ese id.
 */
internal suspend fun guardarCorreoEntrante(
    correo: CorreoEntrante,
    idDelProveedor: String? = null,
    tamano: Int? = null,
): Pair<HttpStatusCode, RespuestaDelCorreo> {
    val tokens = tokensDeLosDestinatarios(correo.destinatarios)
    if (tokens.isEmpty()) {
        registrarCorreoDescartado(null, MotivoDeCorreoDescartado.SIN_CODIGO, correo.remitente, tamano, idDelProveedor)
        return HttpStatusCode.Accepted to
            RespuestaDelCorreo(guardado = false, motivo = "La dirección de destino no trae el token de ningún usuario.")
    }

    // Gana el primer token (en orden de confianza) que sea de alguien.
    val uid = duenoDelCorreo(correo.destinatarios)
    if (uid == null) {
        registrarCorreoDescartado(null, MotivoDeCorreoDescartado.CODIGO_DE_NADIE, correo.remitente, tamano, idDelProveedor)
        return HttpStatusCode.Accepted to
            RespuestaDelCorreo(guardado = false, motivo = "Esa dirección no corresponde a ninguna cuenta.")
    }

    val texto = textoDelCorreo(correo.asunto, correo.cuerpo)
    val tiempo = momentoDelCorreo(correo.fecha, ahora = System.currentTimeMillis())
    // El CUS de PSE identifica el pago: dos copias del mismo correo dan el mismo id (ver [idDeCorreo]).
    val id = idDeCorreo(correo.idDelMensaje, texto, tiempo, idExterno = cusDelCorreoDePse(texto)?.let { "pse-cus:$it" })
    val marca = marcaDeOrigenDelCorreo(correo.nombreDelRemitente, correo.remitente)

    val guardado = dbQuery {
        val existente = SmsMessages.selectAll()
            .where { (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }
            .firstOrNull()
        if (existente != null) {
            // El mismo correo otra vez (Resend reintentó, o el reproceso lo volvió a traer): si la fila
            // todavía no sabe su id del proveedor, lo aprende; si ya tiene otro, esta copia queda anotada.
            if (idDelProveedor != null && existente[SmsMessages.idDelProveedor] == null) {
                SmsMessages.update({ (SmsMessages.id eq id) and (SmsMessages.userId eq uid) }) {
                    it[SmsMessages.idDelProveedor] = idDelProveedor.take(100)
                }
            }
            return@dbQuery false
        }

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
            it[SmsMessages.idDelProveedor] = idDelProveedor?.take(100)
        }
        true
    }

    if (!guardado && idDelProveedor != null && !yaSeVioElCorreo(idDelProveedor)) {
        // Una copia de algo que ya estaba con otro id del proveedor: no se perdió nada, pero el reproceso
        // tiene que saber que este id ya se miró.
        registrarCorreoDescartado(uid, MotivoDeCorreoDescartado.DUPLICADO, correo.remitente, tamano, idDelProveedor)
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
    if (evento == null) {
        registrarCorreoDescartado(null, MotivoDeCorreoDescartado.ILEGIBLE, tamano = crudo.size)
        return call.respond(
            HttpStatusCode.Accepted,
            RespuestaDelCorreo(guardado = false, motivo = "No pude leer el evento."),
        )
    }
    if (evento.tipo != EVENTO_CORREO_RECIBIDO) {
        return call.respond(HttpStatusCode.OK, RespuestaDelCorreo(guardado = false, motivo = "Evento ignorado."))
    }
    val (estado, respuesta) = procesarCorreoDeResend(evento, LectorDeCorreosRecibidos.actual)
    call.respond(estado, respuesta)
}

/**
 * **Del evento de Resend a la bandeja**: trae el contenido con [lector] y sigue por
 * [guardarCorreoEntrante]. Lo usan el webhook y el reproceso diario (`ReprocesoDeCorreos.kt`), así que
 * los dos deciden igual.
 *
 * Vuelta 90 · lo que antes se perdía con un 202:
 * - **sin id** o **ilegible** → una fila en `correos_descartados` (con su dueño si la dirección lo dice);
 * - **demasiado grande** → se guarda **lo que cabe**: el asunto y los metadatos del evento
 *   ([correoSoloConElEvento]); si ni eso hay, se registra como descartado.
 */
internal suspend fun procesarCorreoDeResend(
    evento: EventoDeResend,
    lector: LectorDeCorreosRecibidos,
): Pair<HttpStatusCode, RespuestaDelCorreo> {
    val idDelCorreo = evento.idDelCorreo
    if (idDelCorreo == null) {
        registrarCorreoDescartado(duenoDelCorreo(evento.destinatarios), MotivoDeCorreoDescartado.SIN_ID, evento.remitente)
        return HttpStatusCode.Accepted to RespuestaDelCorreo(guardado = false, motivo = "El evento no trae el id del correo.")
    }

    val correo = when (val contenido = lector.traer(idDelCorreo)) {
        is ContenidoDeResend.Encontrado -> correoDeResend(contenido.json, evento)
            ?: run {
                registrarCorreoDescartado(
                    duenoDelCorreo(evento.destinatarios), MotivoDeCorreoDescartado.ILEGIBLE, evento.remitente,
                    tamano = contenido.json.length, idDelProveedor = idDelCorreo,
                )
                return HttpStatusCode.Accepted to RespuestaDelCorreo(guardado = false, motivo = "No pude leer el correo.")
            }
        ContenidoDeResend.SinClave -> return HttpStatusCode.ServiceUnavailable to
            RespuestaDelCorreo(guardado = false, motivo = "Falta la clave de la API de Resend en el servidor.")
        ContenidoDeResend.DemasiadoGrande -> correoSoloConElEvento(evento)
            ?: run {
                registrarCorreoDescartado(
                    duenoDelCorreo(evento.destinatarios), MotivoDeCorreoDescartado.DEMASIADO_GRANDE, evento.remitente,
                    idDelProveedor = idDelCorreo,
                )
                return HttpStatusCode.Accepted to RespuestaDelCorreo(guardado = false, motivo = "El correo es demasiado grande.")
            }
        ContenidoDeResend.NoEncontrado -> return HttpStatusCode.BadGateway to
            RespuestaDelCorreo(guardado = false, motivo = "Resend no encontró ese correo.")
        is ContenidoDeResend.Fallo -> return HttpStatusCode.BadGateway to
            RespuestaDelCorreo(guardado = false, motivo = "No pude traer el correo de Resend.")
    }

    return guardarCorreoEntrante(correo, idDelProveedor = idDelCorreo)
}
