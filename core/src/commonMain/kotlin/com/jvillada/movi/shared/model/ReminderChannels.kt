package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **Por dónde le pueden llegar los recordatorios a ESTE usuario**, según el server.
 *
 * Existe porque el cliente afirmaba lo contrario sin tener con qué. El aviso ámbar
 * («Este recordatorio no te va a llegar — las notificaciones están apagadas y no hay otro canal
 * activo») miraba **solo** el permiso de notificaciones del navegador y de ahí concluía que no
 * había ningún canal. En producción esa conclusión era falsa: `RESEND_API_KEY` está puesta, así
 * que `ReminderScheduler` arranca y el correo sale igual. Un aviso que se equivoca del lado
 * alarmista se termina ignorando, y entonces no sirve el día que sí haga falta.
 *
 * Quien sabe esto es el server —es el que lee las variables de entorno y el que manda—, así que
 * el server lo dice y el cliente lo pregunta. Mismo camino que ya tiene el push con
 * `GET /api/push/vapid-key`, solo que este endpoint está autenticado porque la respuesta incluye
 * a **qué dirección** sale el correo.
 *
 * Todos los campos tienen default para que un cliente que hable con un server viejo (o al revés)
 * no reviente al deserializar.
 */
@Serializable
data class ReminderChannels(
    /**
     * ¿Hay canal de correo? Es exactamente la condición con la que `startReminderScheduler`
     * decide mandar correos: `RESEND_API_KEY` presente y no vacía.
     */
    val email: Boolean = false,
    /**
     * A qué dirección sale ese correo: la del usuario autenticado, que es la que usa el barrido
     * (`ReminderScheduler.sweep` manda a `Users.email`). `null` si no hay canal.
     */
    val emailTo: String? = null,
    /**
     * El remitente (`REMINDER_FROM`) es uno de prueba de Resend (`@resend.dev`), que **solo
     * entrega a la dirección dueña de la cuenta de Resend**. Para esa persona el correo llega;
     * para cualquier otra, no.
     *
     * El server no puede saber cuál es esa dirección —Resend no la dice y no hay variable que la
     * declare—, así que tampoco puede afirmar «a ti no te va a llegar». Lo que sí puede es no
     * ocultarlo: el cliente lo cuenta como canal (nunca dice que el aviso no va a llegar) y
     * agrega una línea diciendo a quién alcanza. Ver `reminderDeliveryLines`.
     */
    val emailSandbox: Boolean = false,
    /** ¿El server tiene claves VAPID? Sin ellas no puede empujar ninguna notificación. */
    val push: Boolean = false,
    /**
     * Cuántos días antes avisa el barrido (`REMINDER_LEAD_DAYS`). El cliente lo tenía cableado en
     * [DEFAULT_REMINDER_LEAD_DAYS] y prometía «te avisamos 3 días antes» aunque el server
     * estuviera configurado con otro número — la misma clase de afirmación sin respaldo, en chico.
     */
    val leadDays: Int = DEFAULT_REMINDER_LEAD_DAYS,
)
