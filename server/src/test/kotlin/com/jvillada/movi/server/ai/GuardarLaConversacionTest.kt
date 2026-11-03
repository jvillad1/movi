package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AccionesPropuestas
import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.CONVERSACION_BORRADA
import com.jvillada.movi.server.db.ConversacionesConElAsistente
import com.jvillada.movi.server.db.ConversacionesDelAsistente
import com.jvillada.movi.shared.model.CONVERSACIONES_QUE_SE_GUARDAN
import com.jvillada.movi.shared.model.TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION
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
            SchemaUtils.drop(AccionesPropuestas, ConversacionesConElAsistente, ConversacionesDelAsistente, AiTurns, Users)
            SchemaUtils.create(Users, AiTurns, ConversacionesDelAsistente, ConversacionesConElAsistente, AccionesPropuestas)
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
        conversacion: String? = null,
        modelo: String = MODELO_DE_TODOS_LOS_DIAS,
    ) = runBlocking {
        guardarLaConversacion(
            uid = uid, pregunta = pregunta, respuesta = respuesta, consultas = consultas,
            modelo = modelo, criterio = false,
            fichasEntrada = 333, fichasCache = 4_417, fichasSalida = 83,
            hayImagen = hayImagen, ahora = cuando, conversacionId = conversacion,
        )
    }

    /** Una conversación nueva de [uid] con una pregunta, en [cuando]. Devuelve su id. */
    private fun conversacionCon(pregunta: String, cuando: Long, uid: String = dueno, modelo: String = MODELO_DE_TODOS_LOS_DIAS): String {
        val id = runBlocking { conversacionNueva(uid, cuando).id }
        guardar(pregunta = pregunta, uid = uid, cuando = cuando, conversacion = id, modelo = modelo)
        return id
    }

    private fun conversaciones(uid: String = dueno) = transaction {
        ConversacionesConElAsistente.selectAll().where { ConversacionesConElAsistente.userId eq uid }.map { it[ConversacionesConElAsistente.titulo] }
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

    /**
     * Vuelta 117 · **la retención es por conversaciones**: quedan las últimas 50. Al empezar la 51
     * (su primera pregunta), se va entera la que lleva más tiempo sin usarse. Antes eran los últimos
     * 200 turnos, que con la lista a la vista le habrían cortado chats por la mitad sin decirle.
     */
    @Test
    fun `de cada dueno quedan las ultimas 50 conversaciones, enteras`() {
        // Una por día, todas de hace más de un mes: la poda puede borrarlas enteras.
        repeat(CONVERSACIONES_QUE_SE_GUARDAN + 3) { i ->
            val id = conversacionCon("pregunta $i", cuando = 1_000L + i * UN_DIA)
            guardar(pregunta = "y otra $i", cuando = 1_000L + i * UN_DIA + 1, conversacion = id)
        }

        val titulos = conversaciones()
        assertEquals(CONVERSACIONES_QUE_SE_GUARDAN, titulos.size)
        assertFalse("pregunta 0" in titulos || "pregunta 2" in titulos, "las más viejas se van")
        assertTrue("pregunta 3" in titulos && "pregunta ${CONVERSACIONES_QUE_SE_GUARDAN + 2}" in titulos)
        // Enteras: ninguna queda a medias, y de las borradas no queda ni el texto.
        assertEquals(CONVERSACIONES_QUE_SE_GUARDAN * 2, guardadas().size)
        assertFalse(guardadas().any { it[AiTurns.pregunta].endsWith(" 0") })
    }

    /**
     * Revisión de la vuelta 117 · **el tope por conversación**: de cada una quedan sus últimos 200
     * turnos (el tope por dueño de antes). Los más viejos se van; la conversación sigue.
     */
    @Test
    fun `de cada conversacion quedan sus ultimos 200 turnos`() {
        val id = conversacionCon("pregunta 0", cuando = 1_000L)
        (1 until TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION + 5).forEach { i ->
            guardar(pregunta = "pregunta $i", cuando = 1_000L + i * UN_DIA, conversacion = id)
        }

        val preguntas = guardadas().map { it[AiTurns.pregunta] }
        assertEquals(TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION, preguntas.size)
        assertFalse((0..4).any { "pregunta $it" in preguntas }, "los cinco más viejos se van")
        assertTrue("pregunta 5" in preguntas && "pregunta ${TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION + 4}" in preguntas)
        assertEquals(listOf("pregunta 0"), conversaciones(), "la conversación sigue, con su título")
    }

    /**
     * Revisión de la vuelta 117 · **agrupar lo viejo nunca se lleva el turno.** Si agrupar los turnos
     * sueltos falla (acá: falta la tabla de la marca), el turno nuevo se guarda igual.
     */
    @Test
    fun `si agrupar lo viejo falla, el turno del chat se guarda igual`() {
        transaction {
            AiTurns.insert {
                it[id] = "ai_suelto"; it[userId] = dueno; it[creadoEn] = 500L
                it[pregunta] = "suelta"; it[respuesta] = "r"; it[consultas] = "[]"
                it[modelo] = MODELO_DE_TODOS_LOS_DIAS; it[criterio] = false; it[vueltas] = 1
                it[fichasEntrada] = 1; it[fichasCache] = 1; it[fichasSalida] = 1; it[imagen] = false
            }
            SchemaUtils.drop(ConversacionesDelAsistente)
        }

        assertTrue(guardar(pregunta = "la nueva", cuando = 2_000L), "el turno se guardó")
        assertTrue("la nueva" in guardadas().map { it[AiTurns.pregunta] })
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
        repeat(CONVERSACIONES_QUE_SE_GUARDAN + 3) { i -> conversacionCon("mía $i", cuando = 2_000L + i * UN_DIA) }

        assertEquals(1, guardadas("otro-dueno").size)
        assertEquals("la del otro", guardadas("otro-dueno").single()[AiTurns.pregunta])
        assertEquals(listOf("la del otro"), conversaciones("otro-dueno"))
    }

    /**
     * Vuelta 106 · **lo del mes sigue contando**: con esos turnos se cuenta el tope de preguntas y lo
     * que gastó cada uno. Desde la vuelta 117, una conversación del mes que se va (por la retención o
     * porque el dueño la borró) deja de cada pregunta que fue al modelo solo la cifra: fecha, modelo y
     * fichas, **sin texto**. Lo viejo y lo que contestó Movi sin la IA se van enteros.
     */
    @Test
    fun `lo que se borra dentro del mes queda como cifra sin texto, y cuenta en el tope`() {
        val ahora = java.time.LocalDate.of(2026, 11, 28).atStartOfDay(com.jvillada.movi.server.time.AppClock.zone).toInstant().toEpochMilli()
        val vieja = conversacionCon("vieja", cuando = ahora - 60 * UN_DIA)
        val delMes = conversacionCon("del mes", cuando = ahora - 20 * UN_DIA)
        guardar(pregunta = "sin la IA", cuando = ahora - 20 * UN_DIA + 1, conversacion = delMes, modelo = MODELO_SIN_IA)

        runBlocking {
            assertTrue(borrarLaConversacion(dueno, vieja, ahora))
            assertTrue(borrarLaConversacion(dueno, delMes, ahora))
        }

        val quedan = guardadas()
        assertEquals(1, quedan.size, "solo la que fue al modelo dentro del mes")
        val cifra = quedan.single()
        assertEquals("", cifra[AiTurns.pregunta])
        assertEquals("", cifra[AiTurns.respuesta])
        assertEquals("[]", cifra[AiTurns.consultas])
        assertEquals(CONVERSACION_BORRADA, cifra[AiTurns.conversacionId])
        assertEquals(83, cifra[AiTurns.fichasSalida], "las fichas quedan para el costo")
        assertEquals(emptyList(), conversaciones())
        assertEquals(1, runBlocking { ElTopeDelMes.usadasEsteMes(dueno, ahora) }, "borrar no devuelve preguntas del tope")

        // Cuando sale de la ventana, la poda se la lleva.
        conversacionCon("mucho después", cuando = ahora + 70 * UN_DIA)
        assertEquals(listOf("mucho después"), guardadas().map { it[AiTurns.pregunta] })
    }

    private companion object {
        const val UN_DIA = 24L * 60 * 60 * 1000
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
