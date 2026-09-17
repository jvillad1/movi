package com.jvillada.movi.server.push

import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.dbQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.martijndwars.webpush.Encoding
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.PushService
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.security.Security

/**
 * Envío Web Push (VAPID + aes128gcm) vía nl.martijndwars:web-push.
 * 404/410 del push service = endpoint muerto → se borra la suscripción.
 */
object WebPushSender {
    private val logger = LoggerFactory.getLogger("WebPushSender")

    /**
     * **Cómo se nombra una suscripción en el log, sin escribirla.**
     *
     * El `endpoint` no es un identificador: es la URL de capacidad del dispositivo. Quien la
     * tenga, junto con las claves, puede mandarle notificaciones a esa pantalla de bloqueo. En un
     * log rotan, se copian a un agregador y las lee cualquiera con acceso al panel del hosting —
     * bastante más gente que la que debería poder escribirle al teléfono del dueño.
     *
     * Así que al log va un SHA-256 recortado: alcanza para seguir una suscripción a lo largo de
     * varias líneas (que es para lo único que se usaba la URL entera) y no sirve para mandar
     * nada. No lleva sal a propósito: la gracia es que el mismo endpoint dé siempre la misma
     * huella entre reinicios.
     */
    internal fun huella(endpoint: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(endpoint.toByteArray())
        return "sub:" + digest.take(6).joinToString("") { "%02x".format(it) }
    }

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun isConfigured(): Boolean = VapidConfig.isConfigured()

    /** true si AL MENOS una suscripción del usuario recibió la notificación. */
    suspend fun sendToUser(uid: String, payloadJson: String): Boolean {
        if (!isConfigured()) return false
        val subs = dbQuery {
            PushSubscriptions.selectAll()
                .where { PushSubscriptions.userId eq uid }
                .map { Triple(it[PushSubscriptions.endpoint], it[PushSubscriptions.p256dh], it[PushSubscriptions.auth]) }
        }
        if (subs.isEmpty()) return false

        val service = PushService(VapidConfig.publicKey(), VapidConfig.privateKey(), VapidConfig.subject())
        var anyDelivered = false
        for ((endpoint, p256dh, auth) in subs) {
            val status = withContext(Dispatchers.IO) {
                runCatching {
                    // aes128gcm explícito (RFC 8291): el default de la lib es el draft legacy aesgcm,
                    // que Apple (web.push.apple.com) rechaza — sin esto, push nunca llega al iPhone PWA.
                    service.send(Notification(endpoint, p256dh, auth, payloadJson.toByteArray()), Encoding.AES128GCM).statusLine.statusCode
                }.getOrElse { e ->
                    // El mensaje de la excepción viene de la librería HTTP y suele traer la URL
                    // entera adentro: se tacha igual que el resto.
                    logger.warn("push a ${huella(endpoint)} falló: ${e.message?.replace(endpoint, huella(endpoint))}")
                    -1
                }
            }
            when (status) {
                in 200..299 -> anyDelivered = true
                404, 410 -> {
                    logger.info("push endpoint muerto ($status), borrando ${huella(endpoint)}")
                    dbQuery { PushSubscriptions.deleteWhere { PushSubscriptions.endpoint eq endpoint } }
                }
                -1 -> Unit  // ya logueado
                else -> logger.warn("push a ${huella(endpoint)} devolvió $status")
            }
        }
        return anyDelivered
    }
}
