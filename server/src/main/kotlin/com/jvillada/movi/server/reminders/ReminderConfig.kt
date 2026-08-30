package com.jvillada.movi.server.reminders

import com.jvillada.movi.shared.model.DEFAULT_REMINDER_LEAD_DAYS
import java.io.File
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/**
 * La configuración del barrido de recordatorios, en UN solo lugar.
 *
 * Antes vivía suelta dentro de `startReminderScheduler` (con su propio `readEnv` privado), y eso
 * alcanzaba mientras nadie más necesitara saberla. Ahora sí hace falta: `GET /api/reminders/channels`
 * le contesta al cliente si hay canal de correo, y esa respuesta **tiene que ser la misma condición
 * que decide si el correo sale**. Con dos lecturas paralelas de las mismas variables, la respuesta
 * y el comportamiento se pueden desincronizar — que es exactamente el bug que este cambio arregla.
 *
 * Resolución: system property primero (tests), luego env / `server/.env` / `.env` — mismo orden e
 * idéntica forma que [com.jvillada.movi.server.push.VapidConfig] y
 * [com.jvillada.movi.server.auth.PasswordResetConfig]. Sí, es la misma `resolve` otra vez;
 * consolidar las cuatro sigue siendo deuda pre-existente y sacarla acá agrandaría este cambio.
 * Lo que sí se hizo es **borrar** la copia que tenía `ReminderScheduler`: ese archivo ahora lee
 * de acá, así que las copias no crecieron.
 */
object ReminderConfig {

    /** `null` (o vacía) = no hay canal de correo: el barrido no manda ningún mail. */
    fun resendApiKey(): String? = resolve("movi.resend.apiKey", "RESEND_API_KEY")

    fun emailEnabled(): Boolean = !resendApiKey().isNullOrBlank()

    fun from(): String = resolve("movi.reminder.from", "REMINDER_FROM") ?: "movi <reminders@movi.app>"

    /**
     * ¿El remitente es el de pruebas de Resend?
     *
     * `onboarding@resend.dev` (y cualquier `@resend.dev`) es el remitente que Resend da sin
     * dominio verificado, y **solo entrega a la dirección dueña de la cuenta**. Distinguirlo
     * importa: con él el correo sale de verdad, pero no para cualquiera. Ver
     * [com.jvillada.movi.shared.model.ReminderChannels.emailSandbox].
     */
    fun senderIsSandbox(): Boolean = from().substringAfterLast('<').substringBefore('>')
        .trim()
        .endsWith("@resend.dev", ignoreCase = true)

    fun leadDays(): Int = resolve("movi.reminder.leadDays", "REMINDER_LEAD_DAYS")?.toIntOrNull()
        ?: DEFAULT_REMINDER_LEAD_DAYS

    fun sweepHours(): Long = resolve("movi.reminder.sweepHours", "REMINDER_SWEEP_HOURS")?.toLongOrNull() ?: 12L

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


/**
 * Los días de aviso **de este usuario**, con el valor del server como respaldo.
 *
 * `ReminderConfig.leadDays()` es global —una variable de entorno— y por eso no servía: dos
 * personas en la misma instancia querrían números distintos, y ninguna de las dos puede tocar una
 * variable de entorno. Este es el que usan las lecturas por usuario; el barrido global sigue
 * usando el suyo para los que no eligieron.
 */
suspend fun leadDaysOf(uid: String): Int = dbQuery {
    Users.selectAll().where { Users.id eq uid }.firstOrNull()?.get(Users.reminderLeadDays)
} ?: ReminderConfig.leadDays()
