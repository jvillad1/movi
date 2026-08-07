package com.jvillada.movi.server.auth

import com.jvillada.movi.server.reminders.ResendClient

/**
 * Envío del correo de recuperación. Reusa [ResendClient] (mismo transporte que los
 * recordatorios, cero dependencias nuevas).
 *
 * [sender] es una costura de test: los tests la reemplazan por un grabador para no salir a la
 * red y para poder leer el token que se mandó. En producción nadie la toca.
 */
object PasswordResetMailer {

    /**
     * **Costura de test, `internal` a propósito.** Es un `var` mutable sobre un singleton de
     * producción: quien pueda escribirlo redirige TODOS los correos de recuperación —o sea, los
     * enlaces de reset— a donde quiera. `internal` lo deja al alcance del módulo `:server` (y
     * de sus tests, que son una compilación asociada) y fuera del alcance de cualquier otro
     * módulo. No convertirlo en `public` "por comodidad": si algún día hace falta cambiar el
     * transporte desde afuera, el camino correcto es un parámetro de configuración, no esto.
     */
    internal var sender: suspend (to: String, subject: String, html: String, apiKey: String, from: String) -> Boolean =
        { to, subject, html, apiKey, from -> ResendClient.sendEmail(to, subject, html, apiKey, from) }

    suspend fun sendResetLink(to: String, link: String, apiKey: String, from: String): Boolean =
        sender(to, "Restablecer tu contraseña de movi", buildHtml(link), apiKey, from)

    internal fun buildHtml(link: String): String = """<!DOCTYPE html>
<html lang="es">
<head><meta charset="UTF-8"><title>Restablecer tu contraseña de movi</title></head>
<body style="font-family:sans-serif;max-width:560px;margin:0 auto;padding:20px;color:#1f2937">
  <h2 style="margin-bottom:4px">🔑 Restablecer tu contraseña</h2>
  <p style="color:#4b5563">Pediste restablecer la contraseña de tu cuenta de movi. Abrí este enlace para elegir una nueva:</p>
  <p style="margin:24px 0">
    <a href="$link" style="background:#C9B8FF;color:#1a1226;padding:14px 22px;border-radius:999px;text-decoration:none;font-weight:600">Elegir contraseña nueva</a>
  </p>
  <p style="color:#6b7280;font-size:0.9em">El enlace vence en 1 hora y sirve una sola vez.</p>
  <p style="color:#9ca3af;font-size:0.8em;margin-top:24px">Si no pediste esto, ignorá este correo: tu contraseña actual sigue funcionando.</p>
</body>
</html>"""
}
