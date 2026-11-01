package com.jvillada.movi.server.routes

import com.jvillada.movi.server.admin.AdminConfig
import com.jvillada.movi.server.correo.LectorDeCorreosRecibidos
import com.jvillada.movi.server.correo.ListadoDeResend
import com.jvillada.movi.server.db.CorreosDescartados
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.CorreosDescartados as CorreosDescartadosDelWire
import com.jvillada.movi.shared.model.DIAS_DE_CORREOS_DESCARTADOS
import com.jvillada.movi.shared.model.GrupoDeCorreosDescartados
import com.jvillada.movi.shared.model.MotivoDeCorreoDescartado
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.security.MessageDigest

/*
 * # Vuelta 90 · el correo que no se pierde en silencio
 *
 * Tres piezas, todas alrededor de `correos_descartados`:
 *
 * 1. [registrarCorreoDescartado]: lo que el webhook del correo contestaba con un 202 sin escribir nada
 *    deja una fila (sin el cuerpo).
 * 2. [reprocesarCorreosDeResend]: una vez al día, el barrido de recordatorios lista lo que Resend
 *    recibió en los últimos tres días y pasa por el camino de siempre ([procesarCorreoDeResend]) cada
 *    correo que no está ni en la bandeja ni entre los descartados. Resend reintenta un webhook caído
 *    unas 28 horas y después puede deshabilitarlo; esto cubre ese hueco.
 * 3. [correosDescartadosDe]: lo que lee la pantalla de la captura.
 */

private val log = LoggerFactory.getLogger("ReprocesoDeCorreos")

/** Cuántos días hacia atrás mira el reproceso: Resend reintenta ~28 h; tres días cubren un fin de semana. */
internal const val DIAS_DEL_REPROCESO: Long = 3

/**
 * **Anota un correo que no se guardó.** [uid] `null` = de nadie (se guarda `""`). Del remitente solo
 * queda el **dominio**: un correo que no es de nadie puede ser de cualquiera, y la dirección completa
 * sería un dato personal de más. Con [idDelProveedor] la fila es única por ese id (un reintento no la
 * duplica). **Nunca falla hacia afuera**: anotar el descarte no puede tumbar la respuesta del webhook.
 */
internal suspend fun registrarCorreoDescartado(
    uid: String?,
    motivo: MotivoDeCorreoDescartado,
    remitente: String? = null,
    tamano: Int? = null,
    idDelProveedor: String? = null,
    ahora: Long = System.currentTimeMillis(),
) {
    runCatching {
        dbQuery {
            // Sin id del proveedor (Postmark, un evento ilegible), una huella determinista: el mismo correo
            // reintentado en el mismo minuto no suma filas (revisión de la vuelta 90).
            val id = idDelProveedor?.let { "resend_" + it.take(57) }
                ?: ("desc_" + huellaDelDescarte(uid, motivo, remitente, tamano, ahora))
            val ya = CorreosDescartados.selectAll().where { CorreosDescartados.id eq id }.any()
            if (!ya) {
                CorreosDescartados.insert {
                    it[CorreosDescartados.id] = id
                    it[userId] = uid.orEmpty()
                    it[recibidoEn] = ahora
                    it[CorreosDescartados.motivo] = motivo.name
                    it[dominio] = remitente?.substringAfter('@', missingDelimiterValue = "")
                        ?.trim()?.lowercase()?.takeIf { d -> d.isNotEmpty() }?.take(100)
                    it[CorreosDescartados.tamano] = tamano
                    it[CorreosDescartados.idDelProveedor] = idDelProveedor?.take(100)
                }
            }
        }
    }.onFailure { log.warn("no pude anotar un correo descartado (${motivo.name}): ${it.javaClass.simpleName}") }
}

/** Motivo, dueño, dominio del remitente, tamaño y el minuto: lo que identifica un reintento. */
private fun huellaDelDescarte(uid: String?, motivo: MotivoDeCorreoDescartado, remitente: String?, tamano: Int?, ahora: Long): String =
    MessageDigest.getInstance("SHA-256")
        .digest("${uid.orEmpty()}|${motivo.name}|${remitente?.substringAfter('@')?.lowercase().orEmpty()}|${tamano ?: -1}|${ahora / 60_000L}".toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)

/**
 * Las filas **sin dueño** de más de [DIAS_DE_CORREOS_DESCARTADOS] días se borran: no son de nadie, nadie
 * las puede borrar con su cuenta y la pantalla solo cuenta 30 días. Las de un dueño se van con su cuenta.
 */
internal suspend fun purgarDescartadosSinDueno(ahora: Long): Int = dbQuery {
    val limite = ahora - DIAS_DE_CORREOS_DESCARTADOS * 86_400_000L
    CorreosDescartados.deleteWhere { (CorreosDescartados.userId eq "") and (CorreosDescartados.recibidoEn less limite) }
}

/** ¿Ese id del proveedor ya está en la bandeja o entre los descartados? */
internal suspend fun yaSeVioElCorreo(idDelProveedor: String): Boolean = idsYaVistos(listOf(idDelProveedor)).isNotEmpty()

private suspend fun idsYaVistos(ids: List<String>): Set<String> {
    if (ids.isEmpty()) return emptySet()
    return dbQuery {
        val enLaBandeja = SmsMessages.select(SmsMessages.idDelProveedor)
            .where { SmsMessages.idDelProveedor inList ids }
            .mapNotNull { it[SmsMessages.idDelProveedor] }
        val descartados = CorreosDescartados.select(CorreosDescartados.idDelProveedor)
            .where { CorreosDescartados.idDelProveedor inList ids }
            .mapNotNull { it[CorreosDescartados.idDelProveedor] }
        (enLaBandeja + descartados).toSet()
    }
}

/**
 * **El reproceso**: los correos que Resend recibió desde hace [DIAS_DEL_REPROCESO] días y Movi no tiene
 * ni en la bandeja ni entre los descartados, pasados uno por uno por [procesarCorreoDeResend] —el mismo
 * camino del webhook, idempotente por el `email_id`—. Devuelve cuántos se procesaron.
 *
 * Sin la clave de la API ([ListadoDeResend.SinClave]) o si Resend falla, no hace nada: el próximo
 * barrido lo vuelve a intentar. Un correo que Resend todavía no encuentra (502) queda sin anotar y se
 * intenta otra vez mañana.
 *
 * Devuelve `null` si Resend falló (el día no se da por hecho: el próximo barrido reintenta).
 */
internal suspend fun reprocesarCorreosDeResend(
    ahora: Long,
    lector: LectorDeCorreosRecibidos = LectorDeCorreosRecibidos.actual,
): Int? {
    val desde = Instant.ofEpochMilli(ahora).minusSeconds(DIAS_DEL_REPROCESO * 86_400L)
    val recibidos = when (val listado = lector.listarRecibidos(desde)) {
        is ListadoDeResend.Recibidos -> listado.correos
        ListadoDeResend.SinClave -> return 0
        is ListadoDeResend.Fallo -> return null
    }
    val vistos = idsYaVistos(recibidos.map { it.id })
    var procesados = 0
    for (recibido in recibidos.filter { it.id !in vistos }.distinctBy { it.id }) {
        val estado: HttpStatusCode? = try {
            procesarCorreoDeResend(recibido.comoEvento(), lector).first
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            log.warn("reproceso: falló un correo de Resend: ${e.javaClass.simpleName}")
            null
        }
        if (estado != null && estado.value in 200..299) procesados++
    }
    if (procesados > 0) log.info("reproceso: $procesados correo(s) de Resend que no habían llegado por el webhook")
    return procesados
}

@Volatile
private var ultimoDiaDelReproceso: LocalDate? = null

/**
 * El reproceso **una vez por día** (el barrido de recordatorios corre cada 12 h), y la purga de lo
 * descartado sin dueño. El día se da por hecho **solo si el reproceso terminó** (sin clave también
 * cuenta: no hay nada que hacer); si Resend falló o algo lanzó, el próximo barrido lo vuelve a intentar.
 * En memoria: un reinicio lo vuelve a correr, y como es idempotente no pasa nada.
 */
internal suspend fun reprocesarUnaVezAlDia(
    hoy: LocalDate = AppClock.today(),
    ahora: Long = System.currentTimeMillis(),
    lector: LectorDeCorreosRecibidos = LectorDeCorreosRecibidos.actual,
) {
    if (ultimoDiaDelReproceso == hoy) return
    runCatching { purgarDescartadosSinDueno(ahora) }
        .onFailure { log.warn("no pude purgar los correos descartados sin dueño: ${it.javaClass.simpleName}") }
    if (reprocesarCorreosDeResend(ahora, lector) != null) ultimoDiaDelReproceso = hoy
}

/** Solo para pruebas: olvida el último día del reproceso. */
internal fun olvidarElDiaDelReproceso() {
    ultimoDiaDelReproceso = null
}

/**
 * **Lo que contesta `GET /api/correo-entrante/descartados`** para [uid]: los últimos
 * [DIAS_DE_CORREOS_DESCARTADOS] días, por motivo, sin las copias. Los de nadie, solo a un admin.
 */
internal suspend fun correosDescartadosDe(
    uid: String,
    ahora: Long,
    esAdmin: Boolean = AdminConfig.isAdmin(uid),
): CorreosDescartadosDelWire {
    val desde = ahora - DIAS_DE_CORREOS_DESCARTADOS * 86_400_000L
    val filas = dbQuery {
        CorreosDescartados.select(CorreosDescartados.userId, CorreosDescartados.motivo, CorreosDescartados.recibidoEn)
            .where {
                (CorreosDescartados.recibidoEn greaterEq desde) and
                    (if (esAdmin) (CorreosDescartados.userId inList listOf(uid, "")) else (CorreosDescartados.userId eq uid))
            }
            .map { Triple(it[CorreosDescartados.userId], it[CorreosDescartados.motivo], it[CorreosDescartados.recibidoEn]) }
            .filter { it.second != MotivoDeCorreoDescartado.DUPLICADO.name }
    }
    fun agrupar(de: List<Triple<String, String, Long>>) = de.groupBy { it.second }
        .map { (motivo, lista) -> GrupoDeCorreosDescartados(motivo, lista.size, lista.maxOf { it.third }) }
        .sortedByDescending { it.cuantos }
    return CorreosDescartadosDelWire(
        dias = DIAS_DE_CORREOS_DESCARTADOS,
        propios = agrupar(filas.filter { it.first == uid }),
        sinDueno = if (esAdmin) agrupar(filas.filter { it.first.isEmpty() }) else null,
    )
}
