package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.IaPorDia
import com.jvillada.movi.server.db.UsoDeLaApp
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.fx.TasaUsdCop
import com.jvillada.movi.server.time.AppClock
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Lo que cuesta cada turno, con fichas conocidas (vuelta 106)
 *
 * Las cuentas se hacen a mano en cada prueba, con los precios de la página oficial del 2-nov-2026:
 * si alguien cambia un precio de [PreciosDeLaIa] sin querer, se cae acá.
 */
class PreciosDeLaIaTest {

    private fun cerca(esperado: Double, real: Double?, msg: String = "") =
        assertTrue(real != null && abs(esperado - real) < 1e-6, "$msg: esperaba $esperado y dio $real")

    @Test
    fun `haiku 5_5 cuesta lo de la pagina oficial`() {
        // 1.700 de entrada, 6.000 leídas de caché, 330 de salida (el promedio real de un turno de Haiku 4.5):
        // 1.700 × 0,10 + 6.000 × 0,01 + 330 × 0,50 = 170 + 60 + 165 = 395 microdólares.
        cerca(395.0, PreciosDeLaIa.costoDeUnaLlamada("claude-haiku-5-5", entrada = 1_700, escritas = 0, leidas = 6_000, salida = 330), "Haiku 5.5")
        // Lo mismo en Haiku 4.5: 1.700 + 600 + 1.650 = 3.950: diez veces más.
        cerca(3_950.0, PreciosDeLaIa.costoDeUnaLlamada("claude-haiku-4-5-20251001", entrada = 1_700, escritas = 0, leidas = 6_000, salida = 330), "Haiku 4.5 con fecha")
    }

    @Test
    fun `la escritura de cache se cobra a 1_25 la entrada`() {
        // Haiku 5.5: 4.000 escritas × 0,125 = 500.
        cerca(500.0, PreciosDeLaIa.costoDeUnaLlamada("claude-haiku-5-5", entrada = 0, escritas = 4_000, leidas = 0, salida = 0))
        // Sonnet 5.5: 4.000 × 2,50 = 10.000.
        cerca(10_000.0, PreciosDeLaIa.costoDeUnaLlamada("claude-sonnet-5-5", entrada = 0, escritas = 4_000, leidas = 0, salida = 0))
    }

    @Test
    fun `sonnet 5_5 lee la cache a la mitad que sonnet 5`() {
        // 500 de entrada, 3.800 leídas, 690 de salida (el promedio real de Sonnet 5).
        // Sonnet 5:   1.000 + 760 + 6.900 = 8.660.  Sonnet 5.5: 1.000 + 380 + 6.900 = 8.280.
        cerca(8_660.0, PreciosDeLaIa.costoDeUnaLlamada("claude-sonnet-5", 500, 0, 3_800, 690), "Sonnet 5")
        cerca(8_280.0, PreciosDeLaIa.costoDeUnaLlamada("claude-sonnet-5-5", 500, 0, 3_800, 690), "Sonnet 5.5")
    }

    @Test
    fun `el modelo de los extractos y el de respaldo tienen precio`() {
        assertTrue(PreciosDeLaIa.precioDe(MODELO_DE_EXTRACTOS) != null)
        assertTrue(PreciosDeLaIa.precioDe(MODELO_DE_RESPALDO) != null)
        assertTrue(PreciosDeLaIa.precioDe(MODELO_DE_TODOS_LOS_DIAS) != null)
        assertTrue(PreciosDeLaIa.precioDe(MODELO_PARA_CONSEJOS) != null)
        // Opus 4.7: 1.000 × 5 + 100 × 25 = 7.500.
        cerca(7_500.0, PreciosDeLaIa.costoDeUnaLlamada(MODELO_DE_RESPALDO, 1_000, 0, 0, 100))
    }

    @Test
    fun `un prompt de mas de 100 mil fichas va al tramo largo de haiku 5_5`() {
        // 90.000 de entrada + 20.000 leídas = 110.000 de prompt → tramo largo:
        // 90.000 × 0,50 + 20.000 × 0,05 + 1.000 × 2,50 = 45.000 + 1.000 + 2.500 = 48.500.
        cerca(48_500.0, PreciosDeLaIa.costoDeUnaLlamada("claude-haiku-5-5", 90_000, 0, 20_000, 1_000), "tramo largo")
        // Justo 100.000 todavía es el corto: 100.000 × 0,10 = 10.000.
        cerca(10_000.0, PreciosDeLaIa.costoDeUnaLlamada("claude-haiku-5-5", 100_000, 0, 0, 0), "tramo corto")
        // Un turno de tres llamadas de 40.000 cada una (120.000 en suma) sigue en el corto: el tramo es por llamada.
        cerca(12_000.0, PreciosDeLaIa.costoDeUnTurno("claude-haiku-5-5", 120_000, 0, 0, 0, llamadas = 3), "por llamada")
    }

    @Test
    fun `sonnet 5_5 no cae en el precio de sonnet 5 y un modelo desconocido no tiene precio`() {
        assertEquals(0.10, PreciosDeLaIa.precioDe("claude-sonnet-5-5")!!.lecturaDeCache)
        assertEquals(0.20, PreciosDeLaIa.precioDe("claude-sonnet-5")!!.lecturaDeCache)
        assertNull(PreciosDeLaIa.precioDe("claude-que-no-existe"))
        assertNull(PreciosDeLaIa.costoDeUnaLlamada("claude-que-no-existe", 1, 1, 1, 1))
    }
}

/**
 * # El costo en pesos para el admin: la suma por usuario, el mes y su proyección (vuelta 106)
 */
class ElCostoDeLaIaTest {

    private val trm = TasaUsdCop(4_000.0, esRespaldo = false)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:costo_de_la_ia;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(AiTurns, IaPorDia, UsoDeLaApp, Users)
            SchemaUtils.create(Users, AiTurns, IaPorDia, UsoDeLaApp)
            listOf("u-caro" to "caro@costo.test", "u-papa" to "papa@costo.test").forEach { (id, correo) ->
                Users.insert { it[Users.id] = id; it[email] = correo; it[name] = id; it[passwordHash] = "h" }
            }
        }
    }

    private fun turno(uid: String, modelo: String, entrada: Long, leidas: Long, salida: Long, escritas: Long? = null, haceDias: Int = 1, i: Int = 0) =
        transaction {
            AiTurns.insert {
                it[id] = "t-$uid-$modelo-$haceDias-$i"; it[userId] = uid
                it[creadoEn] = System.currentTimeMillis() - haceDias * 86_400_000L
                it[pregunta] = "p"; it[respuesta] = "r"; it[consultas] = "[]"; it[AiTurns.modelo] = modelo
                it[criterio] = false; it[vueltas] = 1; it[fichasEntrada] = entrada; it[fichasCache] = leidas; it[fichasSalida] = salida
                it[imagen] = false; it[fichasCacheEscritas] = escritas
            }
        }

    @Test
    fun `suma por usuario con fichas conocidas, ordenado por lo que gasto`() = runBlocking {
        // Caro: dos turnos de Haiku 5.5 de 395 µUSD (ver PreciosDeLaIaTest) y uno de Sonnet 5.5 de 8.280.
        turno("u-caro", "claude-haiku-5-5", 1_700, 6_000, 330, i = 1)
        turno("u-caro", "claude-haiku-5-5", 1_700, 6_000, 330, i = 2)
        turno("u-caro", "claude-sonnet-5-5", 500, 3_800, 690, i = 3)
        // El papá: un turno de Haiku 5.5 que escribió la caché: 300 × 0,10 + 4.000 × 0,125 + 100 × 0,50 = 580.
        turno("u-papa", "claude-haiku-5-5", 300, 0, 100, escritas = 4_000)
        // Lo que contestó Movi sin la IA no cuesta ni cuenta como pregunta.
        turno("u-papa", MODELO_SIN_IA, 0, 0, 0, i = 9)
        // Lo de hace 40 días no entra en los 30.
        turno("u-papa", "claude-haiku-5-5", 1_000_000, 0, 0, haceDias = 40)

        val c = ElCostoDeLaIa.resumen(tasa = trm)

        assertEquals(listOf("caro@costo.test", "papa@costo.test"), c.quienesMasGastan.map { it.correo })
        val caro = c.quienesMasGastan[0]
        assertEquals(3, caro.preguntas)
        assertEquals(395.0 * 2 + 8_280.0, caro.dolares * 1_000_000, 1e-6)
        // 9.070 µUSD × 4.000 = 36,28 pesos → 36.
        assertEquals(36L, caro.pesos)
        val papa = c.quienesMasGastan[1]
        assertEquals(1, papa.preguntas)
        assertEquals(580.0, papa.dolares * 1_000_000, 1e-6)
        assertEquals(4, c.preguntas30)
    }

    @Test
    fun `los totales, el costo por usuario activo y la proyeccion salen de ia_por_dia`() = runBlocking {
        val hoy = AppClock.today()
        transaction {
            // 2 dólares hoy y 1 hace 10 días: 7 días = 2 US$, 30 días = 3 US$. Un día viejo sin costo no suma.
            listOf(hoy to 2_000_000L, hoy.minusDays(10) to 1_000_000L).forEach { (dia, micro) ->
                IaPorDia.insert {
                    it[IaPorDia.dia] = dia.toString(); it[llamadas] = 10; it[errores] = 0; it[fichasEntrada] = 1
                    it[fichasSalida] = 1; it[fichasCache] = 0; it[usuarios] = 1; it[costoMicrodolares] = micro
                }
            }
            IaPorDia.insert {
                it[dia] = hoy.minusDays(3).toString(); it[llamadas] = 10; it[errores] = 0; it[fichasEntrada] = 1
                it[fichasSalida] = 1; it[fichasCache] = 0; it[usuarios] = 1; it[costoMicrodolares] = null
            }
            // Dos activos en la app (lo que escribe el server no cuenta).
            listOf("u-caro" to "android", "u-papa" to "web", "u-papa" to "server").forEachIndexed { i, (uid, plataforma) ->
                UsoDeLaApp.insert {
                    it[id] = "ev$i"; it[userId] = uid; it[tipo] = "PANTALLA"; it[nombre] = "PANTALLA"; it[ruta] = ""
                    it[sesionId] = "s"; it[secuencia] = i; it[propiedades] = "{}"; it[UsoDeLaApp.plataforma] = plataforma
                    it[version] = "1"; it[ocurridoEn] = System.currentTimeMillis(); it[dia] = hoy.toEpochDay().toInt()
                    it[semana] = 0; it[recibidoEn] = System.currentTimeMillis()
                }
            }
        }

        val c = ElCostoDeLaIa.resumen(tasa = trm)

        assertEquals(2.0, c.dolares7, 1e-9)
        assertEquals(3.0, c.dolares30, 1e-9)
        assertEquals(8_000L, c.pesos7)
        assertEquals(12_000L, c.pesos30)
        assertEquals(2, c.activos30)
        assertEquals(6_000L, c.pesosPorActivo30)
        assertEquals(hoy.minusDays(10).toString(), c.medidoDesde)
        // El mes: lo de hoy (y lo de hace 10 días si es del mismo mes), llevado al mes entero.
        val delMes = (if (hoy.minusDays(10).monthValue == hoy.monthValue) 3.0 else 2.0) * 4_000
        assertEquals(delMes.toLong(), c.pesosDelMes)
        assertEquals(ElCostoDeLaIa.proyeccionDelMes(c.pesosDelMes, hoy.dayOfMonth, hoy.lengthOfMonth()), c.proyeccionDelMes)
    }

    @Test
    fun `la proyeccion lleva lo que va del mes al mes entero`() {
        assertEquals(30_000L, ElCostoDeLaIa.proyeccionDelMes(10_000L, dia = 10, diasDelMes = 30))
        assertEquals(31_000L, ElCostoDeLaIa.proyeccionDelMes(1_000L, dia = 1, diasDelMes = 31))
    }
}
