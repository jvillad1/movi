package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.routes.FabricaDeModelos
import com.jvillada.movi.server.routes.PERSONA
import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «¿Cuánto me he gastado en …?», de punta a punta (vuelta 142, issue #697)
 *
 * Con un modelo de mentira (ninguna prueba llama a Anthropic): el modelo pide [TOTALES_POR_COMERCIO],
 * contesta con el total que le devolvió, y el verificador de cifras lo acepta sin reintento. Es lo
 * que el 6-nov no podía pasar: con 43 pagos y una lista cortada en 40, no había total que decir.
 */
class ElTotalDeUnComercioEnElChatTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val montos = (0 until 43).map { 4_000L + it * 389L }

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("total_de_un_comercio")
        transaction {
            montos.forEachIndexed { i, monto ->
                Events.insert {
                    it[id] = "e-peaje-$i"; it[userId] = a.duenoA; it[accountId] = a.cuentaDeA
                    it[type] = TransactionType.EXPENSE.name; it[amount] = monto; it[currency] = "COP"
                    it[category] = "Transporte"; it[description] = "PAGO ELECTRONICO PEAJEPASS"
                    it[timestamp] = appDateToEpochMillis(LocalDate.parse("2026-07-01").plusDays(i * 2L)) + 12 * 3_600_000L
                    it[reconciliationStatus] = "RECONCILED"
                }
            }
        }
    }

    /** Pide el total y contesta con la cifra que le devolvió la herramienta, como lo haría el modelo. */
    private class ModeloQueContestaConElTotal : ElModeloQueSeCorrige {
        var vueltas = 0
        var correcciones = 0
        var loQueDevolvio = ""
        override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean): RespuestaDelModelo {
            vueltas++
            if (vueltas == 1) {
                return RespuestaDelModelo.PideHerramientas(
                    listOf(LlamadaDeHerramienta("tu_1", TOTALES_POR_COMERCIO, mapOf("comercio" to "peajepass"))),
                )
            }
            val total = Regex("""TOTAL EN PESOS: (\d+)""").find(loQueDevolvio)!!.groupValues[1].toLong()
            return RespuestaDelModelo.Texto("Llevas \$${miles(total)} en Peajepass en 43 pagos, desde el 1 de julio.")
        }
        override fun anotarResultados(resultados: List<Pair<String, String>>) {
            loQueDevolvio = resultados.single().second
        }
        override fun anotarCorreccion(correccion: String) {
            correcciones++
        }
        private fun miles(n: Long) = n.toString().reversed().chunked(3).joinToString(".").reversed()
    }

    @Test
    fun `la herramienta se ofrece y las instrucciones la nombran`() {
        assertTrue(LAS_HERRAMIENTAS.any { it.name() == TOTALES_POR_COMERCIO })
        assertTrue(TOTALES_POR_COMERCIO in PERSONA)
        assertTrue("¿Cuánto me he gastado en Flypass?" in PERSONA, "la pregunta del issue tiene que estar como ejemplo")
    }

    @Test
    fun `contesta con el total de la herramienta y el verificador lo acepta sin reintento`() = testApplication {
        val modelo = ModeloQueContestaConElTotal()
        val fabrica: FabricaDeModelos = { modelo }
        with(a) { application { modulo { aiRoutes(fabrica) } } }

        val res = client.post("/api/ai/chat") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    AiChatRequest.serializer(),
                    AiChatRequest(listOf(ChatMessage(ChatRole.USER, "cuánto me he gastado en peajepass"))),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val respuesta = json.decodeFromString(AiChatResponse.serializer(), res.bodyAsText())

        assertTrue("TOTAL EN PESOS: ${montos.sum()} en 43 pagos" in modelo.loQueDevolvio, modelo.loQueDevolvio)
        assertEquals(2, modelo.vueltas, "una consulta y la respuesta: ni una vuelta de más")
        assertEquals(0, modelo.correcciones, "el verificador no pidió corregir")
        assertFalse(NO_PUDE_VERIFICAR in respuesta.text, respuesta.text)
        val turno = transaction { AiTurns.selectAll().where { AiTurns.userId eq a.duenoA }.single() }
        assertNull(turno[AiTurns.cifrasCorregidas])
        assertNull(turno[AiTurns.cifrasSinRespaldo])
        assertTrue(TOTALES_POR_COMERCIO in turno[AiTurns.consultas])
    }
}
