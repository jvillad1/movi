package com.jvillada.movi.server.push

import java.io.File

/**
 * Claves VAPID para Web Push. Resolución (locked en el spec): system property primero
 * (tests), luego env / server/.env / .env (mismo orden que DatabaseFactory.readEnv —
 * duplicación consciente; consolidar readEnv es deuda pre-existente de 3 archivos).
 */
object VapidConfig {
    fun publicKey(): String?  = resolve("movi.vapid.public", "VAPID_PUBLIC_KEY")
    fun privateKey(): String? = resolve("movi.vapid.private", "VAPID_PRIVATE_KEY")
    fun subject(): String     = resolve("movi.vapid.subject", "VAPID_SUBJECT") ?: "mailto:jvillad1@gmail.com"
    fun isConfigured(): Boolean = !publicKey().isNullOrBlank() && !privateKey().isNullOrBlank()

    private fun resolve(prop: String, envKey: String): String? {
        System.getProperty(prop)?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv(envKey)?.takeIf { it.isNotBlank() }?.let { return it }
        val files = listOf(
            File(System.getProperty("user.dir"), "server/.env"),
            File(System.getProperty("user.dir"), ".env"),
        )
        return files.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$envKey=") }?.substringAfter("=")?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
