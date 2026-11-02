package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.db.ActividadDelServidor
import com.jvillada.movi.server.db.IaPorDia
import com.jvillada.movi.server.db.MetricasDeRutas
import com.jvillada.movi.server.db.MetricasDeServicios
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.time.AppClock
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate

/** La escala de una fila: un minuto o una hora. */
const val ESCALA_MINUTO = "m"
const val ESCALA_HORA = "h"

/**
 * # El volcado de cada minuto (vuelta 102)
 *
 * Cada minuto, lo de [Metricas] de los minutos ya cerrados va a la base y se suelta de la memoria. Es
 * **idempotente**: cada fila se escribe con `upsert` reemplazando (no sumando), así que volcar dos
 * veces el mismo minuto —un reintento después de una falla a medias— deja la misma fila. Un minuto
 * cerrado no cambia más: un pedido se anota con la hora en que terminó, que nunca es del pasado.
 *
 * Después de escribir los minutos, rehace **las horas que tocó** (sumando sus minutos, cubetas
 * incluidas) y el día de la IA. También es reemplazo: rehacer una hora con los mismos minutos da lo
 * mismo.
 */
object VolcadoDeMetricas {

    /** Vuelca lo cerrado antes del minuto de [ahora]. Si la base falla, lo deja en memoria para la próxima. */
    suspend fun volcar(ahora: Long = Metricas.reloj()) {
        val corte = Metricas.minutoDe(ahora)
        val cerrado = Metricas.cerrado(corte)
        if (cerrado.minutos.isEmpty()) return
        dbQuery { escribir(cerrado) }
        Metricas.olvidar(corte)
    }

    /** Escribe [cerrado] y rehace sus horas y sus días. Reemplaza: escribirlo dos veces es escribirlo una. */
    fun Transaction.escribir(cerrado: Metricas.LoCerrado) {
        cerrado.rutas.forEach { (clave, a) ->
            MetricasDeRutas.upsert {
                it[escala] = ESCALA_MINUTO; it[inicio] = clave.minuto; it[metodo] = clave.metodo.take(10)
                it[ruta] = clave.ruta.take(200)
                it[pedidos] = a.total; it[errores4xx] = a.errores4xx; it[errores5xx] = a.errores5xx
                it[p50Ms] = Cubetas.percentil(a.cubetas, 0.50, a.maximoMs) ?: 0
                it[p95Ms] = Cubetas.percentil(a.cubetas, 0.95, a.maximoMs) ?: 0
                it[p99Ms] = Cubetas.percentil(a.cubetas, 0.99, a.maximoMs) ?: 0
                it[maximoMs] = a.maximoMs; it[cubetas] = Cubetas.aTexto(a.cubetas)
            }
        }
        cerrado.servicios.forEach { (clave, a) ->
            MetricasDeServicios.upsert {
                it[escala] = ESCALA_MINUTO; it[inicio] = clave.minuto; it[servicio] = clave.servicio.take(20)
                it[operacion] = clave.operacion.take(60)
                it[llamadas] = a.llamadas; it[sinCredito] = a.sinCredito; it[limite] = a.limite
                it[servidor] = a.servidor; it[otros] = a.otros
                it[fichasEntrada] = a.fichasEntrada; it[fichasSalida] = a.fichasSalida; it[fichasCache] = a.fichasCache
                it[p95Ms] = Cubetas.percentil(a.cubetas, 0.95, a.maximoMs) ?: 0
                it[maximoMs] = a.maximoMs; it[cubetas] = Cubetas.aTexto(a.cubetas)
            }
        }
        cerrado.actividad.forEach { (minuto, a) ->
            ActividadDelServidor.upsert {
                it[escala] = ESCALA_MINUTO; it[inicio] = minuto; it[usuarios] = a.usuarios
                it[consultasLentas] = a.consultasLentas; it[poolActivasMax] = a.poolActivasMax
                it[poolEsperandoMax] = a.poolEsperandoMax
            }
        }
        val horas = cerrado.minutos.map(Metricas::horaDe).toSet()
        horas.forEach { resumirLaHora(it) }
        val dias = cerrado.servicios.keys.filter { it.servicio == Servicio.ANTHROPIC }.map { Metricas.diaDe(it.minuto) }.toSet()
        dias.forEach { rehacerElDiaDeLaIa(it) }
    }

    /** La hora que empieza en [hora], armada con sus minutos. Reemplaza la que hubiera. */
    fun Transaction.resumirLaHora(hora: Long) {
        val fin = hora + Metricas.UNA_HORA

        data class K(val metodo: String, val ruta: String)
        class Suma { var pedidos = 0L; var e4 = 0L; var e5 = 0L; var max = 0L; val cub = LongArray(Cubetas.CUANTAS) }
        val rutas = HashMap<K, Suma>()
        MetricasDeRutas.selectAll()
            .where { (MetricasDeRutas.escala eq ESCALA_MINUTO) and (MetricasDeRutas.inicio greaterEq hora) and (MetricasDeRutas.inicio less fin) }
            .forEach { r ->
                val s = rutas.getOrPut(K(r[MetricasDeRutas.metodo], r[MetricasDeRutas.ruta])) { Suma() }
                s.pedidos += r[MetricasDeRutas.pedidos]; s.e4 += r[MetricasDeRutas.errores4xx]; s.e5 += r[MetricasDeRutas.errores5xx]
                s.max = maxOf(s.max, r[MetricasDeRutas.maximoMs]); Cubetas.sumar(s.cub, Cubetas.deTexto(r[MetricasDeRutas.cubetas]))
            }
        rutas.forEach { (k, s) ->
            MetricasDeRutas.upsert {
                it[escala] = ESCALA_HORA; it[inicio] = hora; it[metodo] = k.metodo; it[ruta] = k.ruta
                it[pedidos] = s.pedidos; it[errores4xx] = s.e4; it[errores5xx] = s.e5
                it[p50Ms] = Cubetas.percentil(s.cub, 0.50, s.max) ?: 0
                it[p95Ms] = Cubetas.percentil(s.cub, 0.95, s.max) ?: 0
                it[p99Ms] = Cubetas.percentil(s.cub, 0.99, s.max) ?: 0
                it[maximoMs] = s.max; it[cubetas] = Cubetas.aTexto(s.cub)
            }
        }

        data class KS(val servicio: String, val operacion: String)
        class SumaS {
            var llamadas = 0L; var sc = 0L; var li = 0L; var se = 0L; var ot = 0L
            var fe = 0L; var fs = 0L; var fc = 0L; var max = 0L; val cub = LongArray(Cubetas.CUANTAS)
        }
        val servicios = HashMap<KS, SumaS>()
        MetricasDeServicios.selectAll()
            .where { (MetricasDeServicios.escala eq ESCALA_MINUTO) and (MetricasDeServicios.inicio greaterEq hora) and (MetricasDeServicios.inicio less fin) }
            .forEach { r ->
                val s = servicios.getOrPut(KS(r[MetricasDeServicios.servicio], r[MetricasDeServicios.operacion])) { SumaS() }
                s.llamadas += r[MetricasDeServicios.llamadas]; s.sc += r[MetricasDeServicios.sinCredito]
                s.li += r[MetricasDeServicios.limite]; s.se += r[MetricasDeServicios.servidor]; s.ot += r[MetricasDeServicios.otros]
                s.fe += r[MetricasDeServicios.fichasEntrada]; s.fs += r[MetricasDeServicios.fichasSalida]; s.fc += r[MetricasDeServicios.fichasCache]
                s.max = maxOf(s.max, r[MetricasDeServicios.maximoMs]); Cubetas.sumar(s.cub, Cubetas.deTexto(r[MetricasDeServicios.cubetas]))
            }
        servicios.forEach { (k, s) ->
            MetricasDeServicios.upsert {
                it[escala] = ESCALA_HORA; it[inicio] = hora; it[servicio] = k.servicio; it[operacion] = k.operacion
                it[llamadas] = s.llamadas; it[sinCredito] = s.sc; it[limite] = s.li; it[servidor] = s.se; it[otros] = s.ot
                it[fichasEntrada] = s.fe; it[fichasSalida] = s.fs; it[fichasCache] = s.fc
                it[p95Ms] = Cubetas.percentil(s.cub, 0.95, s.max) ?: 0
                it[maximoMs] = s.max; it[cubetas] = Cubetas.aTexto(s.cub)
            }
        }

        var hayActividad = false
        var usuarios = 0; var lentas = 0L; var activas = 0; var esperando = 0
        ActividadDelServidor.selectAll()
            .where { (ActividadDelServidor.escala eq ESCALA_MINUTO) and (ActividadDelServidor.inicio greaterEq hora) and (ActividadDelServidor.inicio less fin) }
            .forEach { r ->
                hayActividad = true
                // Por hora queda **el pico** de usuarios distintos en un minuto: los distintos de la hora
                // no salen de sumar minutos, y la memoria con quiénes no se guarda.
                usuarios = maxOf(usuarios, r[ActividadDelServidor.usuarios])
                lentas += r[ActividadDelServidor.consultasLentas]
                activas = maxOf(activas, r[ActividadDelServidor.poolActivasMax])
                esperando = maxOf(esperando, r[ActividadDelServidor.poolEsperandoMax])
            }
        if (hayActividad) {
            ActividadDelServidor.upsert {
                it[escala] = ESCALA_HORA; it[inicio] = hora; it[ActividadDelServidor.usuarios] = usuarios
                it[consultasLentas] = lentas; it[poolActivasMax] = activas; it[poolEsperandoMax] = esperando
            }
        }
    }

    /**
     * El día [dia] de la IA: llamadas y fichas sumadas de sus minutos, y los usuarios distintos que la
     * usaron. Los usuarios solo viven en la memoria de este proceso; si el server se reinició a mitad
     * del día se queda **el mayor** entre lo guardado y lo de ahora (puede quedarse corto, nunca largo).
     */
    fun Transaction.rehacerElDiaDeLaIa(dia: String) {
        val fecha = LocalDate.parse(dia)
        val desde = fecha.atStartOfDay(AppClock.zone).toInstant().toEpochMilli()
        val hasta = fecha.plusDays(1).atStartOfDay(AppClock.zone).toInstant().toEpochMilli()
        var llamadas = 0L; var errores = 0L; var fe = 0L; var fs = 0L; var fc = 0L
        MetricasDeServicios.selectAll()
            .where {
                (MetricasDeServicios.escala eq ESCALA_MINUTO) and (MetricasDeServicios.servicio eq Servicio.ANTHROPIC) and
                    (MetricasDeServicios.inicio greaterEq desde) and (MetricasDeServicios.inicio less hasta)
            }
            .forEach { r ->
                llamadas += r[MetricasDeServicios.llamadas]
                errores += r[MetricasDeServicios.sinCredito] + r[MetricasDeServicios.limite] + r[MetricasDeServicios.servidor] + r[MetricasDeServicios.otros]
                fe += r[MetricasDeServicios.fichasEntrada]; fs += r[MetricasDeServicios.fichasSalida]; fc += r[MetricasDeServicios.fichasCache]
            }
        val antes = IaPorDia.selectAll().where { IaPorDia.dia eq dia }.firstOrNull()?.get(IaPorDia.usuarios) ?: 0
        IaPorDia.upsert {
            it[IaPorDia.dia] = dia; it[IaPorDia.llamadas] = llamadas; it[IaPorDia.errores] = errores
            it[fichasEntrada] = fe; it[fichasSalida] = fs; it[fichasCache] = fc
            it[usuarios] = maxOf(antes, Metricas.usuariosDeIaEn(dia))
        }
    }
}

/** Cuánto se guarda en memoria si la base no deja volcar: lo de más de una hora se suelta. */
const val MEMORIA_MAXIMA_SIN_VOLCAR_MS: Long = 60 * Metricas.UN_MINUTO

/**
 * El bucle del volcado: un poco después de cada minuto, vuelca, revisa la alerta de errores y despacha
 * las alertas juntas. Vive aparte del scheduler de recordatorios —que se apaga sin Resend ni VAPID—
 * porque medir no depende de mandar correos. Si la base no deja volcar, lo de más de 60 minutos se
 * suelta de la memoria (mejor perder una hora de métricas que crecer sin tope).
 *
 * Al apagarse vuelca **solo lo cerrado**, con tope de 5 s: el minuto en curso se pierde (escribirlo
 * entero y reemplazarlo después dejaría una cifra a medias; sumarlo no sería idempotente).
 */
fun Application.startVolcadoDeMetricas() {
    launch {
        while (true) {
            val ahora = Metricas.reloj()
            // Dos segundos pasado el minuto: lo que terminó en el segundo 59 ya se anotó.
            delay(Metricas.UN_MINUTO - Math.floorMod(ahora, Metricas.UN_MINUTO) + 2_000)
            runCatching { VolcadoDeMetricas.volcar() }
                .onFailure {
                    log.warn("métricas: el volcado falló (queda en memoria hasta una hora): ${it.javaClass.simpleName}")
                    Metricas.olvidar(Metricas.minutoDe(Metricas.reloj()) - MEMORIA_MAXIMA_SIN_VOLCAR_MS)
                }
            runCatching { Alertas.revisarLosErrores() }
                .onFailure { log.warn("métricas: la revisión de errores falló: ${it.javaClass.simpleName}") }
            runCatching { Alertas.despachar() }
                .onFailure { log.warn("métricas: el despacho de alertas falló: ${it.javaClass.simpleName}") }
        }
    }
    // La retención en su propio bucle (no colgada de los recordatorios, que se apagan sin Resend ni
    // VAPID): a los 10 minutos de arrancar y después cada 12 horas.
    launch {
        delay(10 * Metricas.UN_MINUTO)
        while (true) {
            runCatching { correrJob("retencion-de-metricas") { RetencionDeMetricas.correr() } }
                .onFailure { log.warn("métricas: la retención falló: ${it.javaClass.simpleName}") }
            delay(12 * Metricas.UNA_HORA)
        }
    }
    monitor.subscribe(ApplicationStopping) {
        runCatching { runBlocking { withTimeoutOrNull(5_000) { VolcadoDeMetricas.volcar() } } }
    }
}
