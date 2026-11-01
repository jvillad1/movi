package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.db.FallosDeLaApp
import com.jvillada.movi.server.db.UsoDeLaApp
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.ActivosDelDia
import com.jvillada.movi.shared.model.AdopcionDeFuncion
import com.jvillada.movi.shared.model.CohorteSemanal
import com.jvillada.movi.shared.model.Conteo
import com.jvillada.movi.shared.model.EventoDeUso
import com.jvillada.movi.shared.model.EventoDeUsoEnviado
import com.jvillada.movi.shared.model.FUNCIONES_CLAVE
import com.jvillada.movi.shared.model.FalloDeLaApp
import com.jvillada.movi.shared.model.GrupoDeFallos
import com.jvillada.movi.shared.model.MAX_EVENTOS_POR_LOTE
import com.jvillada.movi.shared.model.PLATAFORMAS_DE_USO
import com.jvillada.movi.shared.model.ClaveDeUso
import com.jvillada.movi.shared.model.EventoDelRecorrido
import com.jvillada.movi.shared.model.SesionDeUso
import com.jvillada.movi.shared.model.UsuarioConUso
import com.jvillada.movi.shared.model.ResumenDeFallos
import com.jvillada.movi.shared.model.ResumenDeUso
import com.jvillada.movi.shared.model.RespuestaDeUso
import com.jvillada.movi.shared.model.huellaDelFallo
import com.jvillada.movi.shared.model.limpio
import com.jvillada.movi.shared.model.problemaDelEvento
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * # Observabilidad en el server (vuelta 100)
 *
 * Guarda los fallos y el uso que mandan las apps, y arma lo que ve la pantalla de admin «Fallos y
 * uso». Todo en el Postgres de Movi: nada sale a un tercero.
 *
 * El server **vuelve a limpiar** lo que llega aunque el cliente ya lo haya hecho: un APK viejo, una
 * versión con un descuido o alguien escribiendo a mano a la ruta pública no pueden meter un monto en
 * la tabla. Un fallo pasa otra vez por `limpio()`; un evento de uso que no está en la lista cerrada se
 * descarta entero ([problemaDelEvento]).
 */
object Observabilidad {

    /** Lo de más de esto lo borra el barrido diario ([borrarLoViejo]). */
    const val DIAS_DE_RETENCION = 90L
    private const val DIA_MS = 86_400_000L

    /** Cohortes que se muestran: menos semanas que la retención, para que ninguna esté recortada por el borrado. */
    private const val SEMANAS_DE_COHORTES = 12

    private val json = Json

    // ── Días y semanas como enteros ──────────────────────────────────────────────────────────

    /** Días desde 1970 en la zona de la app (Bogotá). */
    internal fun diaDe(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(AppClock.zone).toLocalDate().toEpochDay().toInt()

    /** Semanas desde 1970, de lunes a domingo: el 1-ene-1970 fue jueves, así que el lunes 5 abre la semana 1. */
    internal fun semanaDe(dia: Int): Int = Math.floorDiv(dia + 3, 7)

    /** El lunes que abre la semana [semana]. */
    internal fun lunesDe(semana: Int): LocalDate = LocalDate.ofEpochDay(semana * 7L - 3)

    /**
     * La hora con que se cuenta un evento: la del aparato, salvo que diga algo imposible (más de 7
     * días atrás o en el futuro), y ahí la de llegada. Un reloj mal puesto no puede crear días.
     */
    internal fun horaCreible(ocurrido: Long, recibido: Long): Long =
        if (ocurrido < recibido - 7 * DIA_MS || ocurrido > recibido + 5 * 60_000L) recibido else ocurrido

    // ── Fallos ───────────────────────────────────────────────────────────────────────────────

    /** Guarda un fallo (otra vez limpio). Idempotente por el id del cliente: `false` si ya estaba. */
    suspend fun guardarFallo(fallo: FalloDeLaApp, uid: String?, ahora: Long): Boolean {
        val f = fallo.limpio()
        if (f.id.isBlank()) return false
        return dbQuery {
            val ya = FallosDeLaApp.selectAll().where { FallosDeLaApp.id eq f.id }.limit(1).any()
            if (ya) return@dbQuery false
            FallosDeLaApp.insert {
                it[id] = f.id
                it[userId] = uid.orEmpty().take(50)
                it[plataforma] = f.plataforma.ifBlank { "?" }
                it[version] = f.version
                it[tipo] = f.tipo.ifBlank { "?" }
                it[mensaje] = f.mensaje
                it[stack] = f.stack
                it[hilo] = f.hilo
                it[componente] = f.componente
                it[modelo] = f.modelo
                it[sistema] = f.sistema
                it[huella] = huellaDelFallo(f.tipo, f.stack).ifBlank { f.tipo }
                it[atrapado] = f.atrapado
                it[ocurridoEn] = horaCreible(f.ocurridoEn, ahora)
                it[recibidoEn] = ahora
            }
            true
        }
    }

    /** Los fallos de los últimos [dias] días agrupados por huella, el grupo más numeroso primero. */
    suspend fun resumenDeFallos(ahora: Long, dias: Int = 7): ResumenDeFallos {
        val desde = ahora - dias * DIA_MS
        val filas = dbQuery {
            FallosDeLaApp.selectAll()
                .where { FallosDeLaApp.recibidoEn greaterEq desde }
                .orderBy(FallosDeLaApp.recibidoEn, SortOrder.DESC)
                .limit(5_000)
                .map { r ->
                    r[FallosDeLaApp.huella] to (r[FallosDeLaApp.userId] to FalloDeLaApp(
                        id = r[FallosDeLaApp.id],
                        plataforma = r[FallosDeLaApp.plataforma],
                        version = r[FallosDeLaApp.version],
                        tipo = r[FallosDeLaApp.tipo],
                        mensaje = r[FallosDeLaApp.mensaje],
                        stack = r[FallosDeLaApp.stack],
                        hilo = r[FallosDeLaApp.hilo],
                        componente = r[FallosDeLaApp.componente],
                        modelo = r[FallosDeLaApp.modelo],
                        sistema = r[FallosDeLaApp.sistema],
                        ocurridoEn = r[FallosDeLaApp.ocurridoEn],
                        atrapado = r[FallosDeLaApp.atrapado],
                    ))
                }
        }
        val grupos = filas.groupBy({ it.first }, { it.second }).map { (huella, deLaHuella) ->
            val ejemplo = deLaHuella.first().second
            GrupoDeFallos(
                huella = huella,
                tipo = ejemplo.tipo,
                componente = ejemplo.componente,
                plataformas = deLaHuella.map { it.second.plataforma }.distinct().sorted(),
                cuantos = deLaHuella.size,
                usuarios = deLaHuella.map { it.first }.filter { it.isNotEmpty() }.distinct().size,
                versiones = deLaHuella.map { it.second.version }.filter { it.isNotEmpty() }.distinct().sortedDescending(),
                ultimaVez = deLaHuella.maxOf { it.second.ocurridoEn },
                ejemplo = ejemplo,
            )
        }.sortedWith(compareByDescending<GrupoDeFallos> { it.cuantos }.thenByDescending { it.ultimaVez })
        return ResumenDeFallos(dias = dias, grupos = grupos)
    }

    // ── Uso ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Guarda un lote de eventos de [uid]. Lo que no está en la lista cerrada, de una plataforma que no
     * existe o repetido se descarta; el resto entra. Idempotente por `(user_id, id)`.
     */
    suspend fun guardarUso(uid: String, eventos: List<EventoDeUsoEnviado>, ahora: Long): RespuestaDeUso {
        val validos = eventos.take(MAX_EVENTOS_POR_LOTE).filter { e ->
            e.id.isNotBlank() && e.id.length <= 80 &&
                e.plataforma in PLATAFORMAS_DE_USO && e.plataforma != "server" &&
                problemaDelEvento(e.nombre, e.propiedades, e.ruta) == null
        }.distinctBy { it.id }
        if (validos.isEmpty()) return RespuestaDeUso(guardados = 0, descartados = eventos.size)
        val guardados = dbQuery {
            val ids = validos.map { it.id }
            val ya = UsoDeLaApp.select(UsoDeLaApp.id)
                .where { (UsoDeLaApp.userId eq uid) and (UsoDeLaApp.id inList ids) }
                .map { it[UsoDeLaApp.id] }
                .toSet()
            val nuevos = validos.filter { it.id !in ya }
            insertarUso(uid, nuevos, ahora)
            nuevos.size
        }
        return RespuestaDeUso(guardados = guardados, descartados = eventos.size - validos.size)
    }

    private fun Transaction.insertarUso(uid: String, eventos: List<EventoDeUsoEnviado>, ahora: Long) {
        if (eventos.isEmpty()) return
        UsoDeLaApp.batchInsert(eventos, shouldReturnGeneratedValues = false) { e ->
            val evento = EventoDeUso.deNombre(e.nombre)!!
            val hora = horaCreible(e.ocurridoEn, ahora)
            val dia = diaDe(hora)
            val principal = evento.principal?.let { e.propiedades[it.clave] }
            this[UsoDeLaApp.id] = e.id
            this[UsoDeLaApp.userId] = uid
            this[UsoDeLaApp.tipo] = evento.tipo.name
            this[UsoDeLaApp.nombre] = evento.name
            this[UsoDeLaApp.ruta] = e.ruta
            this[UsoDeLaApp.sesionId] = e.sesionId.take(80)
            this[UsoDeLaApp.secuencia] = e.secuencia.coerceAtLeast(0)
            this[UsoDeLaApp.propiedades] = json.encodeToString(JsonObject.serializer(), JsonObject(e.propiedades.mapValues { JsonPrimitive(it.value) }))
            this[UsoDeLaApp.detalle] = principal?.take(60)
            this[UsoDeLaApp.valor] = if (evento.principal == ClaveDeUso.SEGUNDOS) principal?.toLongOrNull() else null
            this[UsoDeLaApp.plataforma] = e.plataforma
            this[UsoDeLaApp.version] = e.version.take(30)
            this[UsoDeLaApp.ocurridoEn] = hora
            this[UsoDeLaApp.dia] = dia
            this[UsoDeLaApp.semana] = semanaDe(dia)
            this[UsoDeLaApp.recibidoEn] = ahora
        }
    }

    /**
     * **La captura funcionó hoy** para [uid] por [origen] (`SMS`, `NOTIFICACION`, `CORREO`): una fila
     * por usuario, origen y día, con id determinista (la segunda captura del día no escribe nada). La
     * escribe el server porque es el único que ve llegar las tres; no cuenta como uso activo
     * (plataforma `server`). Nunca lanza: una captura no puede fallar por esto.
     */
    suspend fun registrarCapturaActiva(uid: String, origen: String, ahora: Long) {
        if (uid.isBlank() || !ClaveDeUso.ORIGEN.admite(origen)) return
        runCatching {
            val dia = diaDe(ahora)
            val id = "captura:$origen:$dia"
            dbQuery {
                val ya = UsoDeLaApp.selectAll().where { (UsoDeLaApp.userId eq uid) and (UsoDeLaApp.id eq id) }.limit(1).any()
                if (!ya) insertarUso(
                    uid,
                    listOf(
                        EventoDeUsoEnviado(
                            id = id, nombre = EventoDeUso.CAPTURA_ACTIVA.name, propiedades = mapOf("origen" to origen),
                            plataforma = "server", ocurridoEn = ahora,
                        ),
                    ),
                    ahora,
                )
            }
        }
    }

    /** El origen de una captura según su `bank`: «Notificación · Nu» → NOTIFICACION; si no, SMS. */
    fun origenDeLaCaptura(bank: String): String = when {
        bank.startsWith("Notificación") -> "NOTIFICACION"
        bank.startsWith("Correo") -> "CORREO"
        else -> "SMS"
    }

    // ── Retención ────────────────────────────────────────────────────────────────────────────

    /** Borra los fallos y el uso de hace más de [DIAS_DE_RETENCION] días. Devuelve cuántas filas se fueron. */
    suspend fun borrarLoViejo(ahora: Long): Int {
        val limite = ahora - DIAS_DE_RETENCION * DIA_MS
        return dbQuery {
            FallosDeLaApp.deleteWhere { recibidoEn less limite } + UsoDeLaApp.deleteWhere { recibidoEn less limite }
        }
    }

    // ── Lo que ve la pantalla de admin ───────────────────────────────────────────────────────

    /**
     * **El uso, contado con SQL sobre `uso_de_la_app`.** Todo con enteros (`dia`, `semana`) para que
     * valga igual en Postgres y en H2. Los activos no cuentan lo que escribe el server (la captura).
     */
    suspend fun resumenDeUso(ahora: Long): ResumenDeUso {
        val inicio = System.nanoTime()
        val hoy = diaDe(ahora)
        val semanaHoy = semanaDe(hoy)
        val resumen = dbQuery {
            val activosPorDia = mutableMapOf<Int, Int>()
            consultar(
                "SELECT dia, COUNT(DISTINCT user_id) FROM uso_de_la_app " +
                    "WHERE dia > ${hoy - 30} AND plataforma <> 'server' GROUP BY dia",
            ) { activosPorDia[it.getInt(1)] = it.getInt(2) }

            fun activosDesde(dias: Int): Int {
                var n = 0
                consultar(
                    "SELECT COUNT(DISTINCT user_id) FROM uso_de_la_app WHERE dia > ${hoy - dias} AND plataforma <> 'server'",
                ) { n = it.getInt(1) }
                return n
            }
            val activos7 = activosDesde(7)
            val activos30 = activosDesde(30)

            val pantallas = mutableListOf<Conteo>()
            consultar(
                "SELECT detalle, COUNT(*), COUNT(DISTINCT user_id) FROM uso_de_la_app " +
                    "WHERE nombre = 'PANTALLA' AND dia > ${hoy - 30} AND detalle IS NOT NULL " +
                    "GROUP BY detalle ORDER BY COUNT(*) DESC LIMIT 15",
            ) { pantallas += Conteo(it.getString(1), it.getInt(2), it.getInt(3)) }

            val porPaso = mutableMapOf<String, Int>()
            consultar(
                "SELECT detalle, COUNT(DISTINCT user_id) FROM uso_de_la_app " +
                    "WHERE nombre = 'GUIA_PASO' AND dia > ${hoy - 90} GROUP BY detalle",
            ) { porPaso[it.getString(1)] = it.getInt(2) }
            val embudo = ClaveDeUso.PASO.valores.map { paso -> Conteo(paso, 0, porPaso[paso] ?: 0) }

            val porFuncion = mutableMapOf<String, Int>()
            val nombres = FUNCIONES_CLAVE.joinToString(",") { "'${it.name}'" }
            consultar(
                "SELECT nombre, COUNT(DISTINCT user_id) FROM uso_de_la_app " +
                    "WHERE dia > ${hoy - 30} AND nombre IN ($nombres) " +
                    "AND (nombre <> 'GUIA_SALIDA' OR detalle = 'TERMINADA') " +
                    "AND user_id IN (SELECT user_id FROM uso_de_la_app WHERE dia > ${hoy - 30} AND plataforma <> 'server') " +
                    "GROUP BY nombre",
            ) { porFuncion[it.getString(1)] = it.getInt(2) }
            val adopcion = FUNCIONES_CLAVE.map { f ->
                val usuarios = porFuncion[f.name] ?: 0
                AdopcionDeFuncion(f.name, usuarios, if (activos30 == 0) 0 else (usuarios * 100.0 / activos30).roundToInt())
            }

            val cohortes = mutableListOf<CohorteSemanal>()
            consultar(
                """
                WITH primera AS (
                    SELECT user_id, MIN(semana) AS s0 FROM uso_de_la_app WHERE plataforma <> 'server' GROUP BY user_id
                ), activas AS (
                    SELECT DISTINCT user_id, semana FROM uso_de_la_app WHERE plataforma <> 'server'
                )
                SELECT p.s0, COUNT(DISTINCT p.user_id),
                    COUNT(DISTINCT CASE WHEN a.semana = p.s0 + 1 THEN a.user_id END),
                    COUNT(DISTINCT CASE WHEN a.semana = p.s0 + 2 THEN a.user_id END),
                    COUNT(DISTINCT CASE WHEN a.semana = p.s0 + 4 THEN a.user_id END),
                    COUNT(DISTINCT CASE WHEN a.semana = p.s0 + 8 THEN a.user_id END)
                FROM primera p LEFT JOIN activas a ON a.user_id = p.user_id
                WHERE p.s0 > ${semanaHoy - SEMANAS_DE_COHORTES}
                GROUP BY p.s0 ORDER BY p.s0 DESC
                """.trimIndent(),
            ) { rs ->
                val s0 = rs.getInt(1)
                val tam = rs.getInt(2)
                fun pct(k: Int, columna: Int): Int? =
                    if (s0 + k > semanaHoy || tam == 0) null else (rs.getInt(columna) * 100.0 / tam).roundToInt()
                cohortes += CohorteSemanal(lunesDe(s0).toString(), tam, pct(1, 3), pct(2, 4), pct(4, 5), pct(8, 6))
            }

            // Las sesiones salen de los eventos: una por `sesion_id`, del primero al último.
            val porSesion = "SELECT user_id, sesion_id, MIN(plataforma) AS plataforma, MIN(version) AS version, " +
                "(MAX(ocurrido_en) - MIN(ocurrido_en)) / 1000 AS dur FROM uso_de_la_app " +
                "WHERE dia > ${hoy - 30} AND plataforma <> 'server' AND sesion_id <> '' GROUP BY user_id, sesion_id"
            var sesiones = 0
            var conSesiones = 0
            var mediana: Long? = null
            consultar(
                "SELECT COUNT(*), COUNT(DISTINCT user_id), PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY dur) FROM ($porSesion) s",
            ) { rs ->
                sesiones = rs.getInt(1)
                conSesiones = rs.getInt(2)
                mediana = rs.getObject(3)?.let { (it as Number).toDouble().roundToInt().toLong() }
            }
            val porPlataforma = mutableListOf<Conteo>()
            consultar(
                "SELECT plataforma, version, COUNT(*), COUNT(DISTINCT user_id) FROM ($porSesion) s " +
                    "GROUP BY plataforma, version ORDER BY COUNT(*) DESC LIMIT 10",
            ) { porPlataforma += Conteo("${it.getString(1)} ${it.getString(2)}".trim(), it.getInt(3), it.getInt(4)) }

            ResumenDeUso(
                activosHoy = activosPorDia[hoy] ?: 0,
                activos7 = activos7,
                activos30 = activos30,
                activosPorDia = ((hoy - 29)..hoy).map { d -> ActivosDelDia(LocalDate.ofEpochDay(d.toLong()).toString(), activosPorDia[d] ?: 0) },
                pantallas = pantallas,
                embudo = embudo,
                adopcion = adopcion,
                cohortes = cohortes,
                sesionesPorUsuario = if (conSesiones == 0) 0.0 else (sesiones * 10.0 / conSesiones).roundToInt() / 10.0,
                duracionMedianaSegundos = mediana,
                sesionesPorPlataforma = porPlataforma,
            )
        }
        return resumen.copy(milisegundos = (System.nanoTime() - inicio) / 1_000_000)
    }

    // ── El visor del recorrido ───────────────────────────────────────────────────────────────

    /** Los usuarios con uso en los últimos 90 días, el más reciente primero, con su correo. */
    suspend fun usuariosConUso(limite: Int = 200): List<UsuarioConUso> = dbQuery {
        val filas = mutableListOf<UsuarioConUso>()
        consultar(
            "SELECT u.user_id, COALESCE(MAX(us.email), ''), COUNT(DISTINCT u.sesion_id), COUNT(*), MAX(u.ocurrido_en) " +
                "FROM uso_de_la_app u LEFT JOIN users us ON us.id = u.user_id " +
                "WHERE u.plataforma <> 'server' GROUP BY u.user_id ORDER BY MAX(u.ocurrido_en) DESC LIMIT $limite",
        ) { filas += UsuarioConUso(it.getString(1), it.getString(2), it.getInt(3), it.getInt(4), it.getLong(5)) }
        val plataformas = mutableMapOf<String, MutableSet<String>>()
        consultar("SELECT DISTINCT user_id, plataforma FROM uso_de_la_app WHERE plataforma <> 'server'") {
            plataformas.getOrPut(it.getString(1)) { sortedSetOf() } += it.getString(2)
        }
        filas.map { it.copy(plataformas = plataformas[it.id].orEmpty().toList()) }
    }

    /** Las sesiones de [uid], la más reciente primero. */
    suspend fun sesionesDe(uid: String, limite: Int = 100): List<SesionDeUso> = dbQuery {
        val filas = mutableListOf<SesionDeUso>()
        val consulta = "SELECT sesion_id, MIN(ocurrido_en), MAX(ocurrido_en), MIN(plataforma), MIN(version), COUNT(*) " +
            "FROM uso_de_la_app WHERE user_id = ? AND plataforma <> 'server' " +
            "GROUP BY sesion_id ORDER BY MIN(ocurrido_en) DESC LIMIT $limite"
        conParametro(consulta, uid) { rs ->
            val inicio = rs.getLong(2)
            val fin = rs.getLong(3)
            filas += SesionDeUso(rs.getString(1), inicio, fin, (fin - inicio) / 1000, rs.getString(4), rs.getString(5), rs.getInt(6))
        }
        filas
    }

    /** La línea de tiempo de una sesión, en orden de `secuencia` (y por la hora si dos empatan). */
    suspend fun eventosDeLaSesion(sesionId: String, limite: Int = 2_000): List<EventoDelRecorrido> = dbQuery {
        UsoDeLaApp.selectAll()
            .where { UsoDeLaApp.sesionId eq sesionId }
            .orderBy(UsoDeLaApp.secuencia to SortOrder.ASC, UsoDeLaApp.ocurridoEn to SortOrder.ASC)
            .limit(limite)
            .map { r ->
                EventoDelRecorrido(
                    secuencia = r[UsoDeLaApp.secuencia],
                    hora = r[UsoDeLaApp.ocurridoEn],
                    tipo = r[UsoDeLaApp.tipo],
                    nombre = r[UsoDeLaApp.nombre],
                    ruta = r[UsoDeLaApp.ruta],
                    datos = runCatching {
                        json.decodeFromString(JsonObject.serializer(), r[UsoDeLaApp.propiedades])
                            .mapValues { (it.value as? JsonPrimitive)?.content.orEmpty() }
                    }.getOrDefault(emptyMap()),
                )
            }
    }

    /** Una consulta con un único parámetro de texto, ligado (nunca pegado al SQL). */
    private fun Transaction.conParametro(sql: String, parametro: String, fila: (java.sql.ResultSet) -> Unit) {
        val ps = (connection.connection as java.sql.Connection).prepareStatement(sql)
        ps.use {
            it.setString(1, parametro)
            it.executeQuery().use { rs -> while (rs.next()) fila(rs) }
        }
    }

    /** Una consulta de solo lectura con SQL armado acá (solo enteros calculados por el server, nunca texto del pedido). */
    private fun Transaction.consultar(sql: String, fila: (java.sql.ResultSet) -> Unit) {
        // Explícito: Exposed adivina el tipo por la primera palabra, y un `WITH …` lo tomaría por una escritura.
        exec(sql, explicitStatementType = org.jetbrains.exposed.sql.statements.StatementType.SELECT) { rs -> while (rs.next()) fila(rs) }
    }
}
