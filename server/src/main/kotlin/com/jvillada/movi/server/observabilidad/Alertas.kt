package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.admin.AdminConfig
import com.jvillada.movi.server.db.AlertasDelServidor
import com.jvillada.movi.server.db.CorridasDeJobs
import com.jvillada.movi.server.db.MetricasDeRutas
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.reminders.ReminderConfig
import com.jvillada.movi.server.reminders.ResendClient
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

/**
 * # Las dos alertas mínimas (vuelta 102)
 *
 * 1. **Errores del servidor**: si en los últimos [VENTANA_MS] (5 minutos cerrados) hubo al menos
 *    [MINIMO_DE_PEDIDOS] pedidos y más del [UMBRAL_5XX] de ellos fueron 5xx.
 * 2. **Un job que falla dos veces seguidas**: las dos últimas corridas de ese job salieron mal.
 *
 * Cada una manda **un** correo a los admins (`ADMIN_USER_IDS`) por Resend, con un candado en
 * `alertas_del_servidor`: la misma alerta no se repite antes de [CANDADO_MS]. Sin `RESEND_API_KEY`
 * solo queda en el log (y el candado igual se toma, para no llenar el log cada minuto). Se apagan con
 * `ALERTAS_DEL_SERVIDOR=apagadas` (también `off`, `0`, `no`, `false`).
 *
 * **Los umbrales son elegidos, no medidos**: 10 % con un mínimo de 20 pedidos es lo que separa «se
 * cayó algo» de «un pedido de dos falló» con el tráfico de hoy (una familia). Si el tráfico crece se
 * revisan con lo que diga `metricas_de_rutas`.
 */
object Alertas {
    const val UMBRAL_5XX: Double = 0.10
    const val MINIMO_DE_PEDIDOS: Long = 20
    const val VENTANA_MS: Long = 5 * Metricas.UN_MINUTO
    const val CANDADO_MS: Long = Metricas.UNA_HORA

    private val log = LoggerFactory.getLogger("com.jvillada.movi.server.observabilidad.Alertas")

    /** Cómo se manda: (destinatarios, asunto, texto) → si salió. Cambiable desde una prueba. */
    @Volatile
    var mandar: suspend (List<String>, String, String) -> Boolean = ::mandarPorResend

    fun encendidas(valor: String? = System.getenv("ALERTAS_DEL_SERVIDOR")): Boolean =
        valor?.trim()?.lowercase() !in setOf("apagadas", "apagada", "off", "0", "no", "false")

    /** La alerta 1, con lo ya volcado: los 5 minutos cerrados antes del minuto de [ahora]. */
    suspend fun revisarLosErrores(ahora: Long = Metricas.reloj()): Boolean {
        if (!encendidas()) return false
        val hasta = Metricas.minutoDe(ahora)
        val desde = hasta - VENTANA_MS
        val (pedidos, e5xx) = dbQuery {
            var p = 0L; var e = 0L
            MetricasDeRutas.selectAll()
                .where { (MetricasDeRutas.escala eq ESCALA_MINUTO) and (MetricasDeRutas.inicio greaterEq desde) and (MetricasDeRutas.inicio less hasta) }
                .forEach { p += it[MetricasDeRutas.pedidos]; e += it[MetricasDeRutas.errores5xx] }
            p to e
        }
        if (!pasaElUmbral(pedidos, e5xx)) return false
        val porcentaje = (e5xx * 100.0 / pedidos).let { "%.0f".format(it) }
        return alertar(
            clave = "errores-5xx",
            asunto = "Movi: $porcentaje % de errores en el servidor",
            texto = "En los últimos 5 minutos el servidor contestó $e5xx errores 5xx de $pedidos pedidos ($porcentaje %). " +
                "Mira «Fallos y uso» → Servidor para ver qué rutas.",
            ahora = ahora,
        )
    }

    fun pasaElUmbral(pedidos: Long, e5xx: Long): Boolean =
        pedidos >= MINIMO_DE_PEDIDOS && e5xx.toDouble() / pedidos > UMBRAL_5XX

    /** La alerta 2: si las dos últimas corridas de [nombre] fallaron. */
    suspend fun revisarElJob(nombre: String, ahora: Long = Metricas.reloj()): Boolean {
        if (!encendidas()) return false
        val ultimas = dbQuery {
            CorridasDeJobs.selectAll().where { CorridasDeJobs.nombre eq nombre }
                .orderBy(CorridasDeJobs.inicio, SortOrder.DESC).limit(2)
                .map { it[CorridasDeJobs.bien] to it[CorridasDeJobs.error] }
        }
        if (ultimas.size < 2 || ultimas.any { it.first }) return false
        return alertar(
            clave = "job:$nombre",
            asunto = "Movi: el job «$nombre» falló dos veces seguidas",
            texto = "Las dos últimas corridas de «$nombre» salieron mal. La última dijo: ${ultimas.first().second ?: "(sin mensaje)"}",
            ahora = ahora,
        )
    }

    /**
     * Manda la alerta [clave] si su candado está libre. Toma el candado **antes** de mandar: dos
     * revisiones a la vez no mandan dos correos. Devuelve si se mandó (o se dejó en el log).
     */
    internal suspend fun alertar(clave: String, asunto: String, texto: String, ahora: Long): Boolean {
        val tomado = dbQuery {
            val ultima = AlertasDelServidor.selectAll().where { AlertasDelServidor.clave eq clave }.firstOrNull()?.get(AlertasDelServidor.enviadaEn)
            when {
                ultima == null -> { AlertasDelServidor.insert { it[AlertasDelServidor.clave] = clave; it[enviadaEn] = ahora }; true }
                ahora - ultima < CANDADO_MS -> false
                else -> AlertasDelServidor.update({ (AlertasDelServidor.clave eq clave) and (AlertasDelServidor.enviadaEn eq ultima) }) {
                    it[enviadaEn] = ahora
                } == 1
            }
        }
        if (!tomado) return false
        log.warn("alerta del servidor: {} — {}", asunto, texto)
        val para = correosDeLosAdmins()
        if (para.isEmpty()) {
            log.warn("alerta del servidor: no hay admins con correo (ADMIN_USER_IDS) — queda solo en el log")
            return true
        }
        runCatching { mandar(para, asunto, texto) }
            .onFailure { log.warn("alerta del servidor: el correo falló: {}", it.javaClass.simpleName) }
        return true
    }

    private suspend fun correosDeLosAdmins(): List<String> {
        val ids = AdminConfig.adminIds()
        if (ids.isEmpty()) return emptyList()
        return dbQuery { Users.selectAll().where { Users.id inList ids }.map { it[Users.email] } }
    }

    private suspend fun mandarPorResend(para: List<String>, asunto: String, texto: String): Boolean {
        val clave = ReminderConfig.resendApiKey()
        if (clave.isNullOrBlank()) {
            log.warn("alerta del servidor: sin RESEND_API_KEY, no se manda correo")
            return false
        }
        val html = "<p>" + texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>"
        return para.map { ResendClient.sendEmail(to = it, subject = asunto, html = html, apiKey = clave, from = ReminderConfig.from()) }.all { it }
    }

    /** Solo pruebas. */
    fun reiniciar() { mandar = ::mandarPorResend }
}
