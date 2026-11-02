package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.admin.AdminConfig
import com.jvillada.movi.server.db.AlertasDelServidor
import com.jvillada.movi.server.db.CorridasDeJobs
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * # Las dos alertas mínimas (vuelta 102)
 *
 * 1. **Errores del servidor**: si en los últimos [VENTANA_MS] (5 minutos cerrados) hubo al menos
 *    [MINIMO_DE_PEDIDOS] pedidos y más del [UMBRAL_5XX] de ellos fueron 5xx. **Se cuenta desde la
 *    memoria** (`Metricas.ventanaDe5xx`), no desde la base: cuando hay 5xx, la base es lo primero que
 *    suele estar caído. No cuentan `/health` ni los 503 (en este server, siempre un estado esperado).
 * 2. **Un job que falla dos veces seguidas**: la racha en memoria de este proceso, o las dos últimas
 *    corridas de `corridas_de_jobs` (para que un reinicio entre las dos no la esconda).
 *
 * El **candado** (la misma alerta no se repite antes de [CANDADO_MS]) vive en `alertas_del_servidor`, y
 * si la base no contesta, en la memoria del proceso: una vez por hora por proceso. Las alertas que pasan
 * su candado se **juntan**: [despachar] (al final de cada minuto del volcado) manda **un** correo con
 * todas a los admins (`ADMIN_USER_IDS`; sus correos se recuerdan de la última lectura buena, para poder
 * avisar con la base caída). Sin `RESEND_API_KEY` solo queda el log. Todo texto pasa por [sinDatos].
 * Se apagan con `ALERTAS_DEL_SERVIDOR=apagadas` (también `off`, `0`, `no`, `false`).
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

    /** Una alerta que pasó su candado y espera el próximo [despachar]. */
    data class Pendiente(val clave: String, val asunto: String, val texto: String)

    private val pendientes = ConcurrentLinkedQueue<Pendiente>()
    private val candadoEnMemoria = ConcurrentHashMap<String, Long>()
    private val rachaDeFallas = ConcurrentHashMap<String, Int>()

    /** Los correos de los admins de la última lectura buena: con la base caída también hay a quién avisar. */
    @Volatile
    private var correosConocidos: List<String> = emptyList()

    fun encendidas(valor: String? = System.getenv("ALERTAS_DEL_SERVIDOR")): Boolean =
        valor?.trim()?.lowercase() !in setOf("apagadas", "apagada", "off", "0", "no", "false")

    /** La alerta 1, desde la memoria: los 5 minutos cerrados antes del minuto de [ahora]. */
    suspend fun revisarLosErrores(ahora: Long = Metricas.reloj()): Boolean {
        if (!encendidas()) return false
        val hasta = Metricas.minutoDe(ahora)
        val (pedidos, e5xx) = Metricas.ventanaDe5xx(hasta - VENTANA_MS, hasta)
        if (!pasaElUmbral(pedidos, e5xx)) return false
        val porcentaje = (e5xx * 100.0 / pedidos).let { "%.0f".format(it) }
        return alertar(
            clave = "errores-5xx",
            asunto = "$porcentaje % de errores en el servidor",
            texto = "En los últimos 5 minutos el servidor contestó $e5xx errores 5xx de $pedidos pedidos ($porcentaje %). " +
                "Mira «Fallos y uso» → Servidor para ver qué rutas.",
            ahora = ahora,
        )
    }

    fun pasaElUmbral(pedidos: Long, e5xx: Long): Boolean =
        pedidos >= MINIMO_DE_PEDIDOS && e5xx.toDouble() / pedidos > UMBRAL_5XX

    /** Anota cómo salió una corrida de [nombre] en la racha de este proceso. */
    fun anotarCorrida(nombre: String, bien: Boolean) {
        if (bien) rachaDeFallas.remove(nombre) else rachaDeFallas.merge(nombre, 1, Int::plus)
    }

    /** La alerta 2: si [nombre] falló dos veces seguidas. [ultimoError] ya viene sin datos. */
    suspend fun revisarElJob(nombre: String, ultimoError: String? = null, ahora: Long = Metricas.reloj()): Boolean {
        if (!encendidas()) return false
        val enMemoria = (rachaDeFallas[nombre] ?: 0) >= 2
        val enLaBase = !enMemoria && runCatching {
            dbQuery {
                CorridasDeJobs.selectAll().where { CorridasDeJobs.nombre eq nombre }
                    .orderBy(CorridasDeJobs.inicio, SortOrder.DESC).limit(2)
                    .map { it[CorridasDeJobs.bien] }
            }
        }.getOrNull()?.let { it.size == 2 && it.none { bien -> bien } } == true
        if (!enMemoria && !enLaBase) return false
        return alertar(
            clave = "job:$nombre",
            asunto = "el job «$nombre» falló dos veces seguidas",
            texto = "Las dos últimas corridas de «$nombre» salieron mal. La última dijo: ${ultimoError ?: "(sin mensaje)"}",
            ahora = ahora,
        )
    }

    /**
     * Encola la alerta [clave] si su candado está libre, y toma el candado **antes**: dos revisiones a
     * la vez no la mandan dos veces. Si la base no contesta, el candado es el de la memoria.
     */
    internal suspend fun alertar(clave: String, asunto: String, texto: String, ahora: Long): Boolean {
        val tomado = runCatching { tomarElCandadoEnLaBase(clave, ahora) }.getOrElse {
            log.warn("alerta del servidor: la base no contesta, el candado de «{}» queda en memoria", clave)
            tomarElCandadoEnMemoria(clave, ahora)
        }
        if (!tomado) return false
        val limpia = Pendiente(clave, sinDatos(asunto), sinDatos(texto))
        log.warn("alerta del servidor: {} — {}", limpia.asunto, limpia.texto)
        pendientes += limpia
        return true
    }

    private suspend fun tomarElCandadoEnLaBase(clave: String, ahora: Long): Boolean = dbQuery {
        val ultima = AlertasDelServidor.selectAll().where { AlertasDelServidor.clave eq clave }.firstOrNull()?.get(AlertasDelServidor.enviadaEn)
        when {
            ultima == null -> { AlertasDelServidor.insert { it[AlertasDelServidor.clave] = clave; it[enviadaEn] = ahora }; true }
            ahora - ultima < CANDADO_MS -> false
            else -> AlertasDelServidor.update({ (AlertasDelServidor.clave eq clave) and (AlertasDelServidor.enviadaEn eq ultima) }) {
                it[enviadaEn] = ahora
            } == 1
        }
    }.also { if (it) candadoEnMemoria[clave] = ahora }

    private fun tomarElCandadoEnMemoria(clave: String, ahora: Long): Boolean {
        var tomado = false
        candadoEnMemoria.compute(clave) { _, ultima ->
            if (ultima == null || ahora - ultima >= CANDADO_MS) { tomado = true; ahora } else ultima
        }
        return tomado
    }

    /**
     * Manda **un** correo con todas las alertas encoladas desde el último despacho. Devuelve cuántas
     * fueron. Sin admins conocidos o sin Resend, quedan solo en el log (ya escritas al encolarse).
     */
    suspend fun despachar(): Int {
        val juntas = generateSequence { pendientes.poll() }.toList()
        if (juntas.isEmpty()) return 0
        val para = correosDeLosAdmins()
        if (para.isEmpty()) {
            log.warn("alerta del servidor: no hay admins con correo (ADMIN_USER_IDS) — queda solo en el log")
            return juntas.size
        }
        val asunto = "Movi: " + if (juntas.size == 1) juntas.single().asunto else "${juntas.size} alertas del servidor"
        val texto = juntas.joinToString("\n\n") { "${it.asunto}. ${it.texto}" }
        runCatching { mandar(para, asunto, texto) }
            .onFailure { log.warn("alerta del servidor: el correo falló: {}", it.javaClass.simpleName) }
        return juntas.size
    }

    private suspend fun correosDeLosAdmins(): List<String> {
        val ids = AdminConfig.adminIds()
        if (ids.isEmpty()) return emptyList()
        return runCatching { dbQuery { Users.selectAll().where { Users.id inList ids }.map { it[Users.email] } } }
            .onSuccess { correosConocidos = it }
            .getOrElse { correosConocidos }
    }

    private suspend fun mandarPorResend(para: List<String>, asunto: String, texto: String): Boolean {
        val clave = ReminderConfig.resendApiKey()
        if (clave.isNullOrBlank()) {
            log.warn("alerta del servidor: sin RESEND_API_KEY, no se manda correo")
            return false
        }
        val html = "<p>" + texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</p>"
        return para.map { ResendClient.sendEmail(to = it, subject = asunto, html = html, apiKey = clave, from = ReminderConfig.from()) }.all { it }
    }

    /** Solo pruebas. */
    fun reiniciar() {
        mandar = ::mandarPorResend
        pendientes.clear(); candadoEnMemoria.clear(); rachaDeFallas.clear(); correosConocidos = emptyList()
    }
}
