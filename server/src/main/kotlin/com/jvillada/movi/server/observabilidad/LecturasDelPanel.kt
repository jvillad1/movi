package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.db.ActividadDelServidor
import com.jvillada.movi.server.db.CorridasDeJobs
import com.jvillada.movi.server.db.DatabaseFactory
import com.jvillada.movi.server.db.IaPorDia
import com.jvillada.movi.server.db.MetricasDeRutas
import com.jvillada.movi.server.db.MetricasDeServicios
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.version.DeployedCommit
import com.jvillada.movi.shared.model.CorridaDeJob
import com.jvillada.movi.shared.model.IaDelDia
import com.jvillada.movi.shared.model.JobsDelServidor
import com.jvillada.movi.shared.model.PuntoDelServidor
import com.jvillada.movi.shared.model.ResumenDelServidor
import com.jvillada.movi.shared.model.RutaMedida
import com.jvillada.movi.shared.model.SaludDelServidor
import com.jvillada.movi.shared.model.ServicioMedido
import com.jvillada.movi.shared.model.ServiciosDelServidor
import com.jvillada.movi.shared.model.VariableDeEntorno
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.io.File
import java.lang.management.ManagementFactory

/**
 * # Lo que lee el panel «Servidor» (vuelta 102)
 *
 * Todo sale de lo resumido **por hora**: el volcado rehace la hora en curso cada minuto, así que una
 * hora llega hasta el último minuto cerrado. El p95 de un lapso se calcula sumando las cubetas de sus
 * horas, no promediando p95 (un promedio de percentiles no es un percentil).
 */
object LecturasDelPanel {

    /** Cuántas filas trae cada «top». */
    const val TOP = 10

    private class Suma {
        var pedidos = 0L; var e4 = 0L; var e5 = 0L; var max = 0L
        val cubetas = LongArray(Cubetas.CUANTAS)
        fun sumar(r: ResultRow) {
            pedidos += r[MetricasDeRutas.pedidos]; e4 += r[MetricasDeRutas.errores4xx]; e5 += r[MetricasDeRutas.errores5xx]
            max = maxOf(max, r[MetricasDeRutas.maximoMs]); Cubetas.sumar(cubetas, Cubetas.deTexto(r[MetricasDeRutas.cubetas]))
        }
        val p95: Long? get() = Cubetas.percentil(cubetas, 0.95, max)
    }

    suspend fun resumen(horas: Int, ahora: Long = Metricas.reloj()): ResumenDelServidor = dbQuery {
        val hasta = Metricas.horaDe(ahora) + Metricas.UNA_HORA
        val desde = hasta - horas * Metricas.UNA_HORA
        val porHora = sortedMapOf<Long, Suma>()
        val porRuta = HashMap<Pair<String, String>, Suma>()
        val total = Suma()
        MetricasDeRutas.selectAll()
            .where { (MetricasDeRutas.escala eq ESCALA_HORA) and (MetricasDeRutas.inicio greaterEq desde) and (MetricasDeRutas.inicio less hasta) }
            .forEach { r ->
                porHora.getOrPut(r[MetricasDeRutas.inicio]) { Suma() }.sumar(r)
                porRuta.getOrPut(r[MetricasDeRutas.metodo] to r[MetricasDeRutas.ruta]) { Suma() }.sumar(r)
                total.sumar(r)
            }
        var usuariosPico = 0
        var lentas = 0L
        ActividadDelServidor.selectAll()
            .where { (ActividadDelServidor.escala eq ESCALA_HORA) and (ActividadDelServidor.inicio greaterEq desde) and (ActividadDelServidor.inicio less hasta) }
            .forEach { usuariosPico = maxOf(usuariosPico, it[ActividadDelServidor.usuarios]); lentas += it[ActividadDelServidor.consultasLentas] }

        val rutas = porRuta.map { (k, s) -> RutaMedida(k.first, k.second, s.pedidos, s.e4, s.e5, s.p95, s.max) }
        ResumenDelServidor(
            desde = desde,
            hasta = hasta,
            horas = horas,
            pedidos = total.pedidos,
            errores4xx = total.e4,
            errores5xx = total.e5,
            p95Ms = total.p95,
            // Una hora sin pedidos también es un punto (en cero): la gráfica no se salta el hueco.
            serie = (0 until horas).map { i ->
                val inicio = desde + i * Metricas.UNA_HORA
                val s = porHora[inicio]
                PuntoDelServidor(inicio, s?.pedidos ?: 0, s?.e4 ?: 0, s?.e5 ?: 0, s?.p95)
            },
            masLentas = rutas.sortedWith(compareByDescending<RutaMedida> { it.p95Ms ?: 0 }.thenByDescending { it.pedidos }).take(TOP),
            conMas5xx = rutas.filter { it.errores5xx > 0 }.sortedByDescending { it.errores5xx }.take(TOP),
            usuariosPicoPorMinuto = usuariosPico,
            consultasLentas = lentas,
        )
    }

    suspend fun servicios(dias: Int, ahora: Long = Metricas.reloj()): ServiciosDelServidor = dbQuery {
        val hasta = Metricas.horaDe(ahora) + Metricas.UNA_HORA
        val desde = hasta - dias * 24 * Metricas.UNA_HORA
        class S {
            var llamadas = 0L; var sc = 0L; var li = 0L; var se = 0L; var ot = 0L
            var fe = 0L; var fs = 0L; var fc = 0L; var max = 0L; val cub = LongArray(Cubetas.CUANTAS)
        }
        val porServicio = sortedMapOf<String, S>()
        MetricasDeServicios.selectAll()
            .where { (MetricasDeServicios.escala eq ESCALA_HORA) and (MetricasDeServicios.inicio greaterEq desde) and (MetricasDeServicios.inicio less hasta) }
            .forEach { r ->
                val s = porServicio.getOrPut(r[MetricasDeServicios.servicio] + "\u0000" + r[MetricasDeServicios.operacion]) { S() }
                s.llamadas += r[MetricasDeServicios.llamadas]; s.sc += r[MetricasDeServicios.sinCredito]; s.li += r[MetricasDeServicios.limite]
                s.se += r[MetricasDeServicios.servidor]; s.ot += r[MetricasDeServicios.otros]
                s.fe += r[MetricasDeServicios.fichasEntrada]; s.fs += r[MetricasDeServicios.fichasSalida]; s.fc += r[MetricasDeServicios.fichasCache]
                s.max = maxOf(s.max, r[MetricasDeServicios.maximoMs]); Cubetas.sumar(s.cub, Cubetas.deTexto(r[MetricasDeServicios.cubetas]))
            }
        val primerDia = Metricas.diaDe(desde)
        ServiciosDelServidor(
            desde = desde,
            hasta = hasta,
            servicios = porServicio.map { (k, s) ->
                val (servicio, operacion) = k.split("\u0000", limit = 2)
                ServicioMedido(servicio, operacion, s.llamadas, s.sc, s.li, s.se, s.ot, Cubetas.percentil(s.cub, 0.95, s.max), s.max, s.fe, s.fs, s.fc)
            },
            iaPorDia = IaPorDia.selectAll().where { IaPorDia.dia greaterEq primerDia }.orderBy(IaPorDia.dia, SortOrder.DESC).map {
                IaDelDia(
                    it[IaPorDia.dia], it[IaPorDia.llamadas], it[IaPorDia.errores],
                    it[IaPorDia.fichasEntrada], it[IaPorDia.fichasSalida], it[IaPorDia.fichasCache], it[IaPorDia.usuarios],
                )
            },
        )
    }

    private fun ResultRow.aCorrida() = CorridaDeJob(
        nombre = this[CorridasDeJobs.nombre],
        inicio = this[CorridasDeJobs.inicio],
        duracionMs = this[CorridasDeJobs.duracionMs],
        bien = this[CorridasDeJobs.bien],
        error = this[CorridasDeJobs.error],
    )

    suspend fun jobs(): JobsDelServidor = dbQuery {
        // Pocas corridas por día (el barrido es cada 12 h): 500 cubren de sobra la última de cada job.
        val recientes = CorridasDeJobs.selectAll().orderBy(CorridasDeJobs.inicio, SortOrder.DESC).limit(500).map { it.aCorrida() }
        JobsDelServidor(
            ultimas = recientes.groupBy { it.nombre }.map { it.value.first() }.sortedBy { it.nombre },
            fallidas = CorridasDeJobs.selectAll().where { CorridasDeJobs.bien eq false }
                .orderBy(CorridasDeJobs.inicio, SortOrder.DESC).limit(20).map { it.aCorrida() },
        )
    }

    /**
     * Las variables que el server necesita para funcionar entero. Se dice **si están**, nunca qué
     * dicen: el panel lo ve un admin, pero una captura de pantalla no puede llevarse un secreto.
     */
    val VARIABLES_CRITICAS = listOf(
        "DATABASE_URL", "JWT_SECRET", "ANTHROPIC_API_KEY", "RESEND_API_KEY", "REMINDER_FROM",
        "RESEND_WEBHOOK_SECRET", "RESEND_RECEIVING_API_KEY", "INBOUND_EMAIL_SECRET", "INBOUND_EMAIL_ADDRESS",
        "DOCUMENTS_ENCRYPTION_KEY", "ADMIN_USER_IDS", "VAPID_PUBLIC_KEY", "VAPID_PRIVATE_KEY",
        "ALLOWED_ORIGINS", "APP_TIMEZONE",
    )

    /** ¿[nombre] tiene valor? En el entorno o en `server/.env` (desarrollo). Devuelve solo el sí o el no. */
    fun estaDefinida(nombre: String): Boolean {
        if (!System.getenv(nombre).isNullOrBlank()) return true
        val archivos = listOf(File(System.getProperty("user.dir"), "server/.env"), File(System.getProperty("user.dir"), ".env"))
        return archivos.any { f ->
            f.exists() && f.readLines().any { it.startsWith("$nombre=") && it.substringAfter("=").isNotBlank() }
        }
    }

    suspend fun salud(estaDefinida: (String) -> Boolean = ::estaDefinida): SaludDelServidor {
        val t0 = System.nanoTime()
        val baseOk = runCatching { dbQuery { exec("SELECT 1") { it.next() } } }.isSuccess
        val baseMs = (System.nanoTime() - t0) / 1_000_000
        val runtime = Runtime.getRuntime()
        val pool = runCatching { DatabaseFactory.pool?.hikariPoolMXBean }.getOrNull()
        return SaludDelServidor(
            baseOk = baseOk,
            baseMs = if (baseOk) baseMs else null,
            version = DeployedCommit.sha(),
            uptimeMs = ManagementFactory.getRuntimeMXBean().uptime,
            memoriaUsadaMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
            memoriaMaximaMb = runtime.maxMemory() / (1024 * 1024),
            poolActivas = pool?.activeConnections,
            poolInactivas = pool?.idleConnections,
            poolTotal = pool?.totalConnections,
            poolEsperando = pool?.threadsAwaitingConnection,
            variables = VARIABLES_CRITICAS.map { VariableDeEntorno(it, estaDefinida(it)) },
            ultimasCorridas = runCatching { jobs().ultimas }.getOrDefault(emptyList()),
        )
    }
}
