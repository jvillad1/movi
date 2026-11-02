package com.jvillada.movi.server.ai

import com.jvillada.movi.server.admin.AdminConfig
import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.AjustesDeLaIa
import com.jvillada.movi.server.db.TopesDeLaIaPorUsuario
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.AVISAR_CUANDO_QUEDEN
import com.jvillada.movi.shared.model.TOPE_DE_PREGUNTAS_POR_DEFECTO
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.time.ZoneId

/**
 * # El tope de preguntas libres por mes (vuelta 106)
 *
 * **Cuánto:** N por mes calendario (en la zona de la app), el global que eligió el admin o
 * [TOPE_DE_PREGUNTAS_POR_DEFECTO], salvo que el usuario tenga una excepción. **Qué cuenta:** solo lo
 * que fue al modelo — un turno de `ai_turns` con `modelo` distinto de [MODELO_SIN_IA]; las tres
 * sugeridas se guardan con ese modelo y no cuentan. **Quién no tiene:** el admin (`ADMIN_USER_IDS`).
 *
 * Se cuenta en `ai_turns` y no en una tabla aparte: es donde ya queda cada turno contestado, con su
 * fecha y su modelo. Un turno que falló (sin crédito, la red) no se guarda y tampoco cuenta: no se
 * le cobra al usuario una pregunta que no se le contestó.
 *
 * **El tope no bloquea nada que no sea IA:** si leer la configuración falla, la pregunta pasa (y queda
 * en el log). Un tope que tumba el chat por un problema de la base sería peor que no tenerlo.
 */
object ElTopeDelMes {

    /** La clave del valor global en `ajustes_de_la_ia`. */
    const val CLAVE_DEL_GLOBAL = "tope_global"

    private val log = LoggerFactory.getLogger("ElTopeDelMes")

    /** Cómo está un usuario frente a su tope este mes. [tope] `null` = sin tope (admin). */
    data class Estado(val tope: Int?, val usadas: Int) {
        val llego: Boolean get() = tope != null && usadas >= tope

        /**
         * Lo que le queda **después** de la pregunta que se está contestando, solo si ya hay que avisar
         * (quedan [AVISAR_CUANDO_QUEDEN] o menos); `null` si no hay tope o falta para avisar.
         */
        val quedanParaAvisar: Int?
            get() = tope?.let { (it - usadas - 1).coerceAtLeast(0) }?.takeIf { it <= AVISAR_CUANDO_QUEDEN }

        companion object {
            val SIN_TOPE = Estado(tope = null, usadas = 0)
        }
    }

    /** El primer milisegundo del mes calendario de [ahora], en [zona]. */
    fun inicioDelMes(ahora: Long, zona: ZoneId = AppClock.zone): Long =
        Instant.ofEpochMilli(ahora).atZone(zona).toLocalDate().withDayOfMonth(1)
            .atStartOfDay(zona).toInstant().toEpochMilli()

    /** Las preguntas libres de [uid] en el mes de [ahora]: las que fueron al modelo. */
    suspend fun usadasEsteMes(uid: String, ahora: Long = System.currentTimeMillis()): Int = dbQuery {
        val desde = inicioDelMes(ahora)
        AiTurns.selectAll()
            .where { (AiTurns.userId eq uid) and (AiTurns.creadoEn greaterEq desde) and (AiTurns.modelo neq MODELO_SIN_IA) }
            .count().toInt()
    }

    /** El global que eligió el admin, o `null` si nunca eligió. */
    suspend fun globalElegido(): Int? = dbQuery {
        AjustesDeLaIa.selectAll().where { AjustesDeLaIa.clave eq CLAVE_DEL_GLOBAL }
            .firstOrNull()?.get(AjustesDeLaIa.valor)?.toIntOrNull()
    }

    suspend fun global(): Int = globalElegido() ?: TOPE_DE_PREGUNTAS_POR_DEFECTO

    /** La excepción de [uid], o `null` si le toca el global. */
    suspend fun excepcionDe(uid: String): Int? = dbQuery {
        TopesDeLaIaPorUsuario.selectAll().where { TopesDeLaIaPorUsuario.userId eq uid }.firstOrNull()?.get(TopesDeLaIaPorUsuario.tope)
    }

    /** El tope de [uid]: `null` si es admin; si no, su excepción o el global. */
    suspend fun topeDe(uid: String): Int? {
        if (AdminConfig.isAdmin(uid)) return null
        return excepcionDe(uid) ?: global()
    }

    /**
     * **Las preguntas que están yendo al modelo ahora**, por usuario (revisión de la vuelta 106). Una
     * pregunta cuenta en `ai_turns` recién cuando se guarda, segundos después; sin esto, dos preguntas
     * simultáneas en el borde del tope pasaban las dos. En memoria: con un solo proceso alcanza, y un
     * reinicio solo pierde lo que estaba en vuelo (que igual no se guarda).
     */
    private val enCurso = ConcurrentHashMap<String, Int>()

    /** Lo que ya contó y lo que se reserva de un usuario no se lee a medias: un candado por usuario. */
    private val candados = ConcurrentHashMap<String, Mutex>()

    private fun candadoDe(uid: String) = candados.computeIfAbsent(uid) { Mutex() }

    /** Cuántas preguntas de [uid] van al modelo en este momento. */
    fun enCursoDe(uid: String): Int = enCurso[uid] ?: 0

    /** Cómo está [uid] este mes, con lo que está en vuelo. Si la base falla, sin tope (ver el KDoc del objeto). */
    suspend fun estadoDe(uid: String, ahora: Long = System.currentTimeMillis()): Estado = runCatching {
        val tope = topeDe(uid) ?: return@runCatching Estado.SIN_TOPE
        Estado(tope = tope, usadas = usadasEsteMes(uid, ahora) + enCursoDe(uid))
    }.getOrElse {
        log.warn("movi-ai: no pude leer el tope de {}, la pregunta pasa: {}", uid, it.javaClass.simpleName)
        Estado.SIN_TOPE
    }

    /**
     * **Mira el tope y, si queda lugar, aparta la pregunta**, todo bajo el candado de [uid]: así dos
     * preguntas simultáneas no ven las dos el mismo lugar libre. Si [Estado.llego], no apartó nada; si
     * no, quien llama **tiene** que llamar [soltar] cuando termina (con `finally`), después de guardar
     * el turno.
     */
    suspend fun apartar(uid: String, ahora: Long = System.currentTimeMillis()): Estado = candadoDe(uid).withLock {
        val estado = estadoDe(uid, ahora)
        if (!estado.llego) enCurso.merge(uid, 1, Int::plus)
        estado
    }

    /** Suelta la pregunta que [apartar] apartó (ya está en `ai_turns`, o falló y no cuenta). */
    suspend fun soltar(uid: String) = candadoDe(uid).withLock {
        enCurso.computeIfPresent(uid) { _, n -> (n - 1).takeIf { it > 0 } }
        Unit
    }

    suspend fun guardarGlobal(tope: Int, ahora: Long = System.currentTimeMillis()) = dbQuery {
        AjustesDeLaIa.upsert {
            it[clave] = CLAVE_DEL_GLOBAL; it[valor] = tope.toString(); it[actualizadoEn] = ahora
        }
    }

    suspend fun guardarExcepcion(uid: String, tope: Int, ahora: Long = System.currentTimeMillis()) = dbQuery {
        TopesDeLaIaPorUsuario.upsert {
            it[userId] = uid; it[TopesDeLaIaPorUsuario.tope] = tope; it[actualizadoEn] = ahora
        }
    }

    /** `true` si había una excepción y se quitó. */
    suspend fun quitarExcepcion(uid: String): Boolean = dbQuery {
        TopesDeLaIaPorUsuario.deleteWhere { TopesDeLaIaPorUsuario.userId eq uid } > 0
    }
}
