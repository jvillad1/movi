package com.jvillada.movi.server.observabilidad

import com.jvillada.movi.server.db.ActividadDelServidor
import com.jvillada.movi.server.db.CorridasDeJobs
import com.jvillada.movi.server.db.IaPorDia
import com.jvillada.movi.server.db.MetricasDeRutas
import com.jvillada.movi.server.db.MetricasDeServicios
import com.jvillada.movi.server.db.TABLAS_DE_OBSERVABILIDAD
import com.jvillada.movi.server.observabilidad.VolcadoDeMetricas.escribir
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El volcado de cada minuto es idempotente, arma las horas y el día de la IA; y la retención borra lo viejo. */
class VolcadoYRetencionTest {

    /** Un lunes cualquiera a las 10:00 UTC, alineado a la hora. */
    private val diezEnPunto = 1_793_786_400_000L - Math.floorMod(1_793_786_400_000L, Metricas.UNA_HORA)

    @BeforeTest
    fun conectar() {
        Metricas.reiniciar()
        Database.connect(
            url = "jdbc:h2:mem:volcado_de_metricas;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*TABLAS_DE_OBSERVABILIDAD)
            SchemaUtils.create(*TABLAS_DE_OBSERVABILIDAD)
        }
    }

    @AfterTest fun soltar() = Metricas.reiniciar()

    private fun filasDeRutas(escala: String) = transaction {
        MetricasDeRutas.selectAll().where { MetricasDeRutas.escala eq escala }
            .map { listOf(it[MetricasDeRutas.inicio], it[MetricasDeRutas.ruta], it[MetricasDeRutas.pedidos], it[MetricasDeRutas.errores5xx], it[MetricasDeRutas.p95Ms], it[MetricasDeRutas.cubetas]) }
            .sortedBy { it.toString() }
    }

    private fun anotarDosMinutos() {
        Metricas.reloj = { diezEnPunto + 5_000 }
        Metricas.registrarPedido("GET", "/api/accounts", 200, 40, usuario = "usr_1")
        Metricas.registrarPedido("GET", "/api/accounts", 500, 900, usuario = "usr_2")
        Metricas.registrarServicio(Servicio.ANTHROPIC, "claude-haiku", 1_200, null, Fichas(1_000, 200, 50))
        Metricas.reloj = { diezEnPunto + Metricas.UN_MINUTO + 5_000 }
        Metricas.registrarPedido("GET", "/api/accounts", 200, 60, usuario = "usr_1")
        Metricas.registrarServicio(Servicio.ANTHROPIC, "claude-haiku", 800, TipoDeError.LIMITE, null)
    }

    @Test
    fun `volcar dos veces el mismo minuto deja las mismas filas`() = runBlocking {
        anotarDosMinutos()
        val ahora = diezEnPunto + 2 * Metricas.UN_MINUTO + 1_000
        val foto = Metricas.cerrado(Metricas.minutoDe(ahora))

        VolcadoDeMetricas.volcar(ahora)
        val minutos = filasDeRutas(ESCALA_MINUTO)
        val horas = filasDeRutas(ESCALA_HORA)
        assertEquals(2, minutos.size)
        assertEquals(1, horas.size)
        assertEquals(3L, horas.single()[2], "la hora suma los pedidos de sus dos minutos")
        assertEquals(1L, horas.single()[3])

        // Lo mismo otra vez (un reintento después de una falla a medias) y un volcado sin nada nuevo.
        transaction { escribir(foto) }
        transaction { escribir(foto) }
        VolcadoDeMetricas.volcar(ahora)

        assertEquals(minutos, filasDeRutas(ESCALA_MINUTO))
        assertEquals(horas, filasDeRutas(ESCALA_HORA))
        val ia = transaction { IaPorDia.selectAll().single() }
        assertEquals(2L, ia[IaPorDia.llamadas])
        assertEquals(1L, ia[IaPorDia.errores])
        assertEquals(1_000L, ia[IaPorDia.fichasEntrada])
        assertEquals(200L, ia[IaPorDia.fichasSalida])
        assertEquals(50L, ia[IaPorDia.fichasCache])
    }

    @Test
    fun `el minuto en curso no se vuelca hasta que cierra`() = runBlocking {
        Metricas.reloj = { diezEnPunto + 10_000 }
        Metricas.registrarPedido("GET", "/api/accounts", 200, 40, usuario = null)
        VolcadoDeMetricas.volcar(diezEnPunto + 30_000)
        assertTrue(filasDeRutas(ESCALA_MINUTO).isEmpty())
        VolcadoDeMetricas.volcar(diezEnPunto + Metricas.UN_MINUTO + 2_000)
        assertEquals(1, filasDeRutas(ESCALA_MINUTO).size)
    }

    @Test
    fun `el p95 de la hora sale de sumar cubetas, no de promediar p95`() = runBlocking {
        // Minuto 1: 19 rápidos. Minuto 2: 1 lento. El p95 de cada minuto sería 5 y 2000; el de la hora, 5.
        Metricas.reloj = { diezEnPunto + 1_000 }
        repeat(19) { Metricas.registrarPedido("GET", "/api/x", 200, 5, usuario = null) }
        Metricas.reloj = { diezEnPunto + Metricas.UN_MINUTO + 1_000 }
        Metricas.registrarPedido("GET", "/api/x", 200, 2_000, usuario = null)
        VolcadoDeMetricas.volcar(diezEnPunto + 3 * Metricas.UN_MINUTO)
        val hora = filasDeRutas(ESCALA_HORA).single()
        assertEquals(20L, hora[2])
        assertEquals(5L, hora[4])
    }

    @Test
    fun `la retencion borra minutos de mas de 30 dias, horas de mas de 12 meses y corridas de mas de 90`() = runBlocking {
        val dia = 86_400_000L
        val ahora = diezEnPunto
        transaction {
            fun minutoDeRuta(inicio: Long, escala: String) = MetricasDeRutas.insert {
                it[MetricasDeRutas.escala] = escala; it[MetricasDeRutas.inicio] = inicio; it[metodo] = "GET"; it[ruta] = "/api/x"
                it[pedidos] = 1; it[errores4xx] = 0; it[errores5xx] = 0; it[p50Ms] = 1; it[p95Ms] = 1; it[p99Ms] = 1
                it[maximoMs] = 1; it[cubetas] = Cubetas.aTexto(LongArray(Cubetas.CUANTAS).also { c -> c[0] = 1 })
            }
            minutoDeRuta(ahora - 31 * dia, ESCALA_MINUTO)          // se va
            minutoDeRuta(ahora - 29 * dia, ESCALA_MINUTO)          // se queda
            minutoDeRuta(Metricas.horaDe(ahora - 366 * dia), ESCALA_HORA)  // se va
            minutoDeRuta(Metricas.horaDe(ahora - 200 * dia), ESCALA_HORA)  // se queda
            fun corrida(id: String, inicio: Long) = CorridasDeJobs.insert {
                it[CorridasDeJobs.id] = id; it[nombre] = "recordatorios"; it[CorridasDeJobs.inicio] = inicio
                it[duracionMs] = 5; it[bien] = true
            }
            corrida("vieja", ahora - 91 * dia)
            corrida("nueva", ahora - 89 * dia)
            IaPorDia.insert { it[IaPorDia.dia] = "2020-01-01"; it[llamadas] = 1; it[errores] = 0; it[fichasEntrada] = 0; it[fichasSalida] = 0; it[fichasCache] = 0; it[usuarios] = 1 }
        }

        val borrado = RetencionDeMetricas.correr(ahora)

        assertEquals(1, borrado.minutos)
        assertEquals(1, borrado.corridas)
        assertEquals(1, borrado.dias)
        val quedan = transaction {
            MetricasDeRutas.selectAll().map { it[MetricasDeRutas.escala] to it[MetricasDeRutas.inicio] }.toSet()
        }
        assertTrue((ESCALA_MINUTO to ahora - 29 * dia) in quedan)
        assertTrue((ESCALA_HORA to Metricas.horaDe(ahora - 200 * dia)) in quedan)
        assertTrue(quedan.none { it.first == ESCALA_MINUTO && it.second < ahora - 30 * dia })
        assertTrue(quedan.none { it.first == ESCALA_HORA && it.second < ahora - 365 * dia })
        // El minuto de hace 31 días tenía su hora rehecha antes de irse: lo resumido sigue.
        assertTrue((ESCALA_HORA to Metricas.horaDe(ahora - 31 * dia)) in quedan)
        assertEquals(listOf("nueva"), transaction { CorridasDeJobs.selectAll().map { it[CorridasDeJobs.id] } })
        assertEquals(0L, transaction { MetricasDeServicios.selectAll().count() + ActividadDelServidor.selectAll().count() })
    }
}
