package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Espacios
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.GastosDelEspacio
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LiquidacionesDelEspacio
import com.jvillada.movi.server.routes.FabricaDeModelos
import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.server.routes.espacioRoutes
import com.jvillada.movi.server.routes.financeRoutes
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.model.pesosDelCierre
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # «¿Cómo voy?» dice las mismas cifras que el chip que la pregunta (vuelta de diseño 30)
 *
 * La revisión vio el chip «¿Por qué salieron $902.250 de más?» y, en el chat, «entró $850.000 y salió
 * $1.667.250» (−$817.250). El chip sale de `/api/finance-summary` (`ingresos`/`egresos`, en la app
 * `senalesDe`) y la respuesta de `datosDeLasSugeridas`; **las dos van por `movimientosDeFlujo`** —solo
 * tu parte de un gasto repartido, tu parte de lo que pagó otro, sin la transferencia que salda— desde
 * el #497. La diferencia era de tiempo: la respuesta era un turno de un día anterior, recargado sin
 * fecha (lo arregla el chat: ver `LoQueEraDeOtroDia.kt` en `:shared`). Esto fija que, el mismo día y
 * con un gasto repartido, las dos cuentas son la misma.
 */
class LasCifrasDeComoVoySonLasDeHoyTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var n = 0

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("cifras_de_como_voy", tablasDeMas = arrayOf(Espacios, GastosDelEspacio, LiquidacionesDelEspacio))
        transaction {
            KnownDestinations.insert {
                it[id] = "dst_caro"
                it[userId] = a.duenoA
                it[nombre] = "Caro"
                it[numero] = ""
                it[createdAt] = 0L
            }
        }
    }

    private val fabricaQueNoSeUsa: FabricaDeModelos = { throw AssertionError("«¿Cómo voy?» no llama al modelo") }

    private val hoy = System.currentTimeMillis() - 60_000

    private fun evento(id: String, monto: Long, tipo: String = "EXPENSE", categoria: String = "Mercado") = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = a.duenoA
            it[accountId] = a.cuentaDeA
            it[type] = tipo
            it[amount] = monto
            it[currency] = "COP"
            it[category] = categoria
            it[description] = categoria
            it[timestamp] = hoy - n++
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private suspend fun ApplicationTestBuilder.post(ruta: String, cuerpo: String) =
        client.post(ruta) {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }.also { assertTrue(it.status.value in 200..299, "$ruta: ${it.bodyAsText()}") }

    @Test
    fun `con un gasto repartido, lo que dice Como voy es lo que dice el resumen del que sale el chip`() = testApplication {
        with(a) { application { modulo { aiRoutes(fabricaQueNoSeUsa); financeRoutes(); espacioRoutes() } } }
        evento("e_sueldo", 850_000, tipo = "INCOME", categoria = "Salario")
        evento("e_mercado", 177_200) // repartido 50/50: tu parte, 88.600
        evento("e_almuerzo", 1_000_000, categoria = "Comida")
        evento("e_de_caro", 500_000, tipo = "INCOME", categoria = "Ingreso")
        post("/api/espacios", """{"id":"esp_casa","nombre":"Casa con Caro","miembros":[{"id":"dst_caro"}]}""")
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        // Lo que pagó Caro: tu parte, 50.000, el día del gasto.
        post("/api/espacios/esp_casa/gastos", """{"id":"g_caro","pagadoPor":"dst_caro","monto":100000,"categoria":"Mercado","fecha":$hoy}""")
        // Caro salda con una transferencia: no es ingreso.
        post("/api/espacios/esp_casa/liquidaciones", """{"id":"liq","de":"dst_caro","a":"yo","monto":500000,"eventoId":"e_de_caro"}""")

        val resumen = json.decodeFromString(
            FinanceSummary.serializer(),
            client.get("/api/finance-summary") { with(a) { como(a.duenoA) } }.bodyAsText(),
        )
        assertEquals(850_000L, resumen.ingresos)
        assertEquals(88_600L + 1_000_000L + 50_000L, resumen.egresos)

        val res = client.post("/api/ai/chat") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    AiChatRequest.serializer(),
                    AiChatRequest(listOf(ChatMessage(ChatRole.USER, PreguntaSugerida.COMO_VOY.texto, preguntaSugerida = PreguntaSugerida.COMO_VOY.id))),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        val texto = json.decodeFromString(AiChatResponse.serializer(), res.bodyAsText()).text
        assertTrue(
            "entró ${pesosDelCierre(resumen.ingresos)} y salió ${pesosDelCierre(resumen.egresos)}" in texto,
            "la respuesta y el chip («salieron \$X de más» = egresos − ingresos) cuentan lo mismo:\n$texto",
        )
    }
}
