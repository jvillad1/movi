package com.jvillada.movi.server.admin

import java.io.File

/**
 * Quién puede editar pantallas. Movi no tiene roles: la capacidad se habilita por
 * configuración, igual que RESEND/VAPID. Sin config → nadie es admin (403 en escrituras).
 */
object AdminConfig {
    fun adminIds(): Set<String> =
        resolve("movi.admin.userIds", "ADMIN_USER_IDS")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: emptySet()

    fun isAdmin(uid: String): Boolean = uid in adminIds()

    private fun resolve(prop: String, envKey: String): String? {
        System.getProperty(prop)?.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv(envKey)?.takeIf { it.isNotBlank() }?.let { return it }
        val files = listOf(
            File(System.getProperty("user.dir"), "server/.env"),
            File(System.getProperty("user.dir"), ".env"),
        )
        return files.firstNotNullOfOrNull { f ->
            if (!f.exists()) null
            else f.readLines().firstOrNull { it.startsWith("$envKey=") }
                ?.substringAfter("=")?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
