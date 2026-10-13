package com.jvillada.movi.server.reminders

import com.jvillada.movi.server.auth.PasswordResetConfig
import com.jvillada.movi.server.db.CierresEnviados
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.routes.cierreDelPeriodoDe
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.esHoraDeAvisarElCierre
import com.jvillada.movi.shared.model.fraseDelCierre
import com.jvillada.movi.shared.model.lineasDelCierre
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.tituloDelCierre
import com.jvillada.movi.shared.model.ventanaDe
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * # El resumen de cierre por correo («Cerró tu período»)
 *
 * Lo manda el barrido de recordatorios (`ReminderScheduler`), con el mismo envío de Resend. Reglas:
 *
 * - **Una vez por usuario y período**: la fila de [CierresEnviados] es el candado. Se escribe ANTES de
 *   mandar —si dos barridos se cruzan, el segundo no la puede insertar y no manda— y se borra si el
 *   envío falla, para que el barrido siguiente reintente.
 * - **Solo los primeros días del período nuevo** ([esHoraDeAvisarElCierre]): el día siguiente al corte,
 *   o un par de días después si el server estuvo caído. Pasado eso, llega tarde para servir.
 * - **Se apaga desde la app** (Perfil → Avisos, `users.cierre_por_correo`), nunca solo desde el código.
 * - **Sin `RESEND_API_KEY` no hace nada**: ni calcula ni deja filas.
 * - **Cada usuario por separado**: lo que falla con uno no frena a los demás, y cada correo lleva solo
 *   las cifras de su dueño ([cierreDelPeriodoDe] con su id).
 *
 * El texto es el MISMO de la tarjeta de Hoy y del aviso del teléfono ([lineasDelCierre], en `:core`).
 */

private val log = LoggerFactory.getLogger("CorreoDeCierre")

/** Cómo se manda un correo: (para, asunto, html) → si salió. En producción, [ResendClient.sendEmail]. */
internal typealias EnviarCorreo = suspend (para: String, asunto: String, html: String) -> Boolean

/**
 * **Manda los resúmenes de cierre que tocan [hoy]** y devuelve cuántos salieron.
 *
 * @param apiKey la de Resend; vacía o `null` = no hay canal de correo y no se hace nada.
 */
internal suspend fun mandarLosCierres(hoy: LocalDate, apiKey: String?, enviar: EnviarCorreo): Int {
    if (apiKey.isNullOrBlank()) return 0
    val usuarios = dbQuery {
        Users.selectAll().map { Triple(it[Users.id], it[Users.email], it[Users.cierrePorCorreo] ?: true) }
    }
    var enviados = 0
    for ((uid, correo, quiere) in usuarios) {
        if (!quiere) continue
        runCatching { if (mandarElCierreDe(uid, correo, hoy, enviar)) enviados++ }
            .onFailure { log.warn("CorreoDeCierre: falló con el usuario $uid: ${it.message}", it) }
    }
    return enviados
}

/** El de un usuario: `true` si salió. */
private suspend fun mandarElCierreDe(uid: String, correo: String, hoy: LocalDate, enviar: EnviarCorreo): Boolean {
    // Primero lo barato: ¿es hora y no salió todavía? El resumen se calcula solo si hace falta.
    val ajustes = ajustesDePeriodoDe(uid)
    val enCurso = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, ajustes)
    val dia = ChronoUnit.DAYS.between(epochMillisToAppDate(ventanaDe(enCurso, ajustes).first), hoy).toInt() + 1
    if (!esHoraDeAvisarElCierre(dia)) return false
    val periodo = periodoAnterior(enCurso).prefijo
    val yaSalio = dbQuery {
        CierresEnviados.selectAll()
            .where { (CierresEnviados.userId eq uid) and (CierresEnviados.periodo eq periodo) }
            .any()
    }
    if (yaSalio) return false

    val cierre = cierreDelPeriodoDe(uid, hoy) ?: return false
    // El candado, antes de mandar.
    val tomado = dbQuery {
        CierresEnviados.insertIgnore {
            it[userId] = uid
            it[CierresEnviados.periodo] = cierre.periodo
            it[enviadoEn] = System.currentTimeMillis()
        }.insertedCount > 0
    }
    if (!tomado) return false
    val salio = runCatching { enviar(correo, tituloDelCierre(cierre), htmlDelCierre(cierre)) }.getOrDefault(false)
    if (!salio) {
        dbQuery {
            CierresEnviados.deleteWhere { (CierresEnviados.userId eq uid) and (CierresEnviados.periodo eq cierre.periodo) }
        }
        return false
    }
    log.info("CorreoDeCierre: resumen de ${cierre.periodo} enviado a $uid")
    return true
}

/** Escapa lo que va dentro del HTML: nombres de categorías y pagos los escribe el dueño. */
private fun html(texto: String): String = texto
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

/**
 * **El correo**: el título, la frase de la tarjeta y un renglón por cosa ([lineasDelCierre]), con un
 * enlace al detalle del período en la app. Liviano y con los colores claros de siempre de los correos
 * de Movi: la mayoría de los clientes de correo no respetan el tema oscuro.
 */
internal fun htmlDelCierre(c: CierreDelPeriodo, baseUrl: String = PasswordResetConfig.appBaseUrl()): String {
    val renglones = lineasDelCierre(c).drop(1).joinToString("") { linea ->
        """<li style="margin:0 0 10px 0;line-height:1.45">${html(linea)}</li>"""
    }
    return """<!DOCTYPE html>
<html lang="es">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${html(tituloDelCierre(c))}</title></head>
<body style="font-family:-apple-system,'Segoe UI',Roboto,sans-serif;max-width:560px;margin:0 auto;padding:24px 20px;color:#1f2937;background:#ffffff">
  <p style="margin:0 0 4px 0;font-size:13px;color:#6b7280;text-transform:uppercase;letter-spacing:.04em">${html(c.nombre)}</p>
  <h2 style="margin:0 0 8px 0;font-size:22px">${html(tituloDelCierre(c))}</h2>
  <p style="margin:0 0 20px 0;font-size:16px;color:#374151">${html(fraseDelCierre(c).substringAfter(": ").replaceFirstChar { it.uppercase() })}.</p>
  <ul style="padding-left:18px;margin:0 0 24px 0;font-size:15px;color:#1f2937">$renglones</ul>
  <p style="margin:0 0 28px 0"><a href="${html(baseUrl.trimEnd('/'))}" style="display:inline-block;background:#6d5bd0;color:#ffffff;text-decoration:none;padding:10px 16px;border-radius:10px;font-size:15px">Ver el período en Movi</a></p>
  <p style="margin:0;font-size:12px;color:#9ca3af">Este resumen te lo manda Movi el día siguiente al cierre de tu período. Para dejar de recibirlo, apágalo en la app: Perfil → Avisos → «Resumen del período por correo».</p>
</body>
</html>"""
}
