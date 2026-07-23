package com.jvillada.movi.server.push

import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.dbQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                    service.send(Notification(endpoint, p256dh, auth, payloadJson.toByteArray())).statusLine.statusCode
                }.getOrElse { e ->
                    logger.warn("push a $endpoint falló: ${e.message}")
                    -1
                }
            }
            when (status) {
                in 200..299 -> anyDelivered = true
                404, 410 -> {
                    logger.info("push endpoint muerto ($status), borrando: $endpoint")
                    dbQuery { PushSubscriptions.deleteWhere { PushSubscriptions.endpoint eq endpoint } }
                }
                -1 -> Unit  // ya logueado
                else -> logger.warn("push a $endpoint devolvió $status")
            }
        }
        return anyDelivered
    }
}
