package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.Users
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * # Lo que el dueño preguntó queda guardado, y guardarlo no puede romper nada
 *
 * Esto existe porque «el asistente no supo» era imposible de diagnosticar: el chat vivía solo en el
 * teléfono y del lado del server quedaban las fichas gastadas y nada más. Él pidió guardarlo.
 *
 * Es la tabla más sensible de Movi, así que lo que se fija acá no es solo que guarde: es **qué no
 * guarda** (la foto), **cuánto guarda** (las últimas, no todas) y sobre todo que un fallo al
 * guardar **no se lleve puesta la respuesta** — una función de diagnóstico que tumba lo que
 * diagnostica es peor que no tenerla.
 */
class GuardarLaConversacionTest {

    private val dueno = "user-conversaciones"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:guardar_conversacion;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(AiTurns, Users)
            SchemaUtils.create(Users, AiTurns)
            Users.insert {
                it[id] = dueno; it[email] = "dueno@conversaciones.test"; it[name] = "Camilo"
                it[passwordHash] = "hash"
            }
        }
    }

    private fun guardar(
        pregunta: String = "¿cuánto gasté en Comida en agosto?",
        respuesta: String = "En agosto de calendario gastaste \$1.161.535.",
        consultas: List<ConsultaHecha> = emptyList(),
        hayImagen: Boolean = false,
        uid: String = dueno,
        cuando: Long = 1_000,
    ) = runBlocking {
        guardarLaConversacion(
            uid = uid, pregunta = pregunta, respuesta = respuesta, consultas = consultas,
            modelo = MODELO_DE_TODOS_LOS_DIAS, criterio = false,
            fichasEntrada = 333, fichasCache = 4_417, fichasSalida = 83,
            hayImagen = hayImagen, ahora = cuando,
        )
    }

    private fun guardadas(uid: String = dueno) = transaction {
        AiTurns.selectAll().where { AiTurns.userId eq uid }
            .orderBy(AiTurns.creadoEn to SortOrder.DESC)
            .map { it }
    }

    @Test
    fun `guarda la pregunta, la respuesta y lo que consulto`() {
        assertTrue(
            guardar(
                consultas = listOf(
                    ConsultaHecha(
                        TOTALES_POR_CATEGORIA,
                        mapOf("desde" to "2026-08-01", "hasta" to "2026-08-31"),
                        "Comida: 1161535",
                    ),
                ),
            ),
        )

        val fila = guardadas().single()
        assertEquals("¿cuánto gasté en Comida en agosto?", fila[AiTurns.pregunta])
        assertTrue(fila[AiTurns.respuesta].contains("1.161.535"))
        // Lo que se consultó y lo que devolvió: sin esto no se puede decir por qué contestó eso.
        assertTrue(fila[AiTurns.consultas].contains(TOTALES_POR_CATEGORIA))
        assertTrue(fila[AiTurns.consultas].contains("2026-08-01"))
        assertTrue(fila[AiTurns.consultas].contains("Comida: 1161535"))
        assertEquals(2, fila[AiTurns.vueltas], "una consulta son dos vueltas: la que pidió y la que contestó")
        assertEquals(4_417, fila[AiTurns.fichasCache])
    }

    @Test
    fun `una conversacion sin consultas lo dice, y eso es la mitad del diagnostico`() {
        guardar()
        val fila = guardadas().single()
        assertEquals("[]", fila[AiTurns.consultas])
        assertEquals(1, fila[AiTurns.vueltas])
    }

    /**
     * **Lo que vuelve medible el «sin inventar».** Una respuesta limpia deja las dos columnas en
     * NULL, así que `count(*) where cifras_sin_respaldo is not null` cuenta exactamente las que
     * llegaron al dueño con una cifra que Movi no pudo respaldar.
     */
    @Test
    fun `guarda las cifras sin respaldo y las que se corrigieron, y NULL si no hubo`() {
        guardar(cuando = 1)
        runBlocking {
            guardarLaConversacion(
                uid = dueno, pregunta = "¿por qué el 2334 no baja?", respuesta = "…", consultas = emptyList(),
                modelo = MODELO_PARA_CONSEJOS, criterio = true,
                fichasEntrada = 1, fichasCache = 1, fichasSalida = 1, hayImagen = false, ahora = 2,
                cifrasSinRespaldo = listOf("\$1.234.567"),
                cifrasCorregidas = listOf("\$185.831", "12 %"),
            )
        }

        val (corregida, limpia) = guardadas()
        assertEquals("\$1.234.567", corregida[AiTurns.cifrasSinRespaldo])
        assertEquals("\$185.831 · 12 %", corregida[AiTurns.cifrasCorregidas])
        assertEquals(2, corregida[AiTurns.vueltas], "el reintento del verificador es una vuelta más")
        assertEquals(null, limpia[AiTurns.cifrasSinRespaldo])
        assertEquals(null, limpia[AiTurns.cifrasCorregidas])
        assertEquals(1, limpia[AiTurns.vueltas])
    }

    /** Una foto de un recibo es justo lo que no hay que duplicar: queda que la hubo, no la foto. */
    @Test
    fun `la foto no se guarda, solo que la hubo`() {
        guardar(pregunta = "mira este recibo", hayImagen = true)

        val fila = guardadas().single()
        assertTrue(fila[AiTurns.imagen])
        assertFalse(fila[AiTurns.pregunta].contains("base64"))
        assertEquals("mira este recibo", fila[AiTurns.pregunta])
    }

    @Test
    fun `de cada dueno quedan las ultimas, no todas`() {
        repeat(CUANTAS_CONVERSACIONES_SE_GUARDAN + 5) { i ->
            guardar(pregunta = "pregunta $i", cuando = 1_000L + i)
        }

        val filas = guardadas()
        assertEquals(CUANTAS_CONVERSACIONES_SE_GUARDAN, filas.size)
        assertEquals("pregunta ${CUANTAS_CONVERSACIONES_SE_GUARDAN + 4}", filas.first()[AiTurns.pregunta])
        assertFalse(filas.any { it[AiTurns.pregunta] == "pregunta 0" }, "lo más viejo es lo que se cae")
    }

    /** Podarle a uno no puede tocar a otro: la poda va por dueño, como todo en Movi. */
    @Test
    fun `podar lo de un dueno no borra lo de otro`() {
        transaction {
            Users.insert {
                it[id] = "otro-dueno"; it[email] = "otro@conversaciones.test"; it[name] = "Otro"
                it[passwordHash] = "hash"
            }
        }
        guardar(uid = "otro-dueno", pregunta = "la del otro")
        repeat(CUANTAS_CONVERSACIONES_SE_GUARDAN + 3) { i -> guardar(pregunta = "mía $i", cuando = 2_000L + i) }

        assertEquals(1, guardadas("otro-dueno").size)
        assertEquals("la del otro", guardadas("otro-dueno").single()[AiTurns.pregunta])
    }

    /**
     * **La garantía que importa.** Si guardar falla —la tabla todavía no existe, la base está
     * lenta—, el dueño igual recibe su respuesta: quien llama solo mira el `false` para un log.
     */
    @Test
    fun `si no se puede guardar, no lanza`() {
        transaction { SchemaUtils.drop(AiTurns) }

        assertFalse(guardar(), "sin tabla devuelve false; lo que no puede es tirar una excepción")
    }
}
