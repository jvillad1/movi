package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AccionesPropuestas
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.routes.FabricaDeModelos
import com.jvillada.movi.server.routes.PedidoDelModelo
import com.jvillada.movi.server.routes.aiRoutes
import com.jvillada.movi.server.routes.eventRoutes
import com.jvillada.movi.server.routes.reminderRoutes
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.AccionPropuesta
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.EstadoDePropuesta
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MarkOccurrenceRequest
import com.jvillada.movi.shared.model.RecategorizarEnLoteRequest
import com.jvillada.movi.shared.model.ResolverPropuestaRequest
import com.jvillada.movi.shared.model.TipoDeAccion
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Movi actúa: propone, el dueño confirma (Ola 3 · 1)
 *
 * La regla de toda la ola es que **el asistente nunca escribe solo**. Lo que se fija acá, de punta a
 * punta y con un modelo de mentira (ninguna prueba llama a Anthropic):
 *
 * - una propuesta inválida —cuenta ajena, monto negativo, categoría reservada— **no llega a la app**;
 * - proponer no escribe nada: después del chat la base está igual;
 * - **confirmar es el endpoint de siempre** (`POST /api/events`, el lote, `POST /api/recurring-rules`,
 *   `POST /occurrence`) con lo que trae la tarjeta, y crea lo que dice;
 * - «No» no crea nada, y el asistente lo sabe en el turno siguiente;
 * - un pago se marca solo con un movimiento que existe.
 */
class MoviActuaTest {

    private lateinit var a: AndamioDelAsistente
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val hoy: LocalDate = AppClock.today()

    @BeforeTest
    fun setUp() {
        a = AndamioDelAsistente("movi_actua")
        transaction {
            // Una cuenta de B con nombre propio, para probar que A no puede proponer sobre ella.
            Accounts.insert {
                it[id] = "acc-davivienda-b"; it[userId] = a.duenoB; it[name] = "Davivienda de B"
                it[type] = "SAVINGS"; it[currency] = "COP"
            }
            Accounts.insert {
                it[id] = "acc-credito-a"; it[userId] = a.duenoA; it[name] = "Crédito vehículo"
                it[type] = "LOAN"; it[currency] = "COP"
            }
        }
        anotar("e-comida", "Almuerzo", "Comida", 30_000, hoy.minusDays(2))
    }

    // ── Andamio ──────────────────────────────────────────────────────────────

    /** Un modelo de guion que además guarda lo que le pidieron y lo que le contestaron. */
    private class ModeloDeGuion(private val guion: List<RespuestaDelModelo>) : ElModeloQueSeCorrige {
        var llamadas = 0
        val resultados = mutableListOf<String>()
        override suspend fun siguienteVuelta(puedeUsarHerramientas: Boolean) = guion[llamadas++.coerceAtMost(guion.lastIndex)]
        override fun anotarResultados(resultados: List<Pair<String, String>>) {
            this.resultados += resultados.map { it.second }
        }
        override fun anotarCorreccion(correccion: String) = Unit
    }

    private val pedidos = mutableListOf<PedidoDelModelo>()
    private var modelos = ArrayDeque<ModeloDeGuion>()

    private val fabrica: FabricaDeModelos = { pedido ->
        pedidos += pedido
        modelos.removeFirst()
    }

    private fun ApplicationTestBuilder.montar() = with(a) {
        application { modulo { aiRoutes(fabrica); eventRoutes(); reminderRoutes() } }
    }

    private fun pide(nombre: String, vararg args: Pair<String, String>, texto: String = "") =
        RespuestaDelModelo.PideHerramientas(listOf(LlamadaDeHerramienta("tu_${nombre}_1", nombre, args.toMap())), texto)

    private suspend fun ApplicationTestBuilder.preguntar(pregunta: String, uid: String = a.duenoA): AiChatResponse {
        val res = client.post("/api/ai/chat") {
            with(a) { como(uid) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AiChatRequest.serializer(), AiChatRequest(listOf(ChatMessage(ChatRole.USER, pregunta)))))
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        return json.decodeFromString(AiChatResponse.serializer(), res.bodyAsText())
    }

    private fun anotar(id: String, nombre: String, categoria: String, monto: Long, fecha: LocalDate, uid: String = a.duenoA, cuenta: String = a.cuentaDeA) =
        transaction {
            Events.insert {
                it[Events.id] = id; it[userId] = uid; it[accountId] = cuenta; it[type] = TransactionType.EXPENSE.name
                it[amount] = monto; it[currency] = "COP"; it[category] = categoria; it[description] = nombre
                it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L; it[reconciliationStatus] = "RECONCILED"
            }
        }

    private fun movimientosDe(uid: String) = transaction { Events.selectAll().where { Events.userId eq uid }.count() }

    private fun propuestasGuardadas() = transaction { AccionesPropuestas.selectAll().count() }

    private fun proponerDirecto(nombre: String, vararg args: Pair<String, String>, uid: String = a.duenoA) =
        runBlocking { proponer(uid, LlamadaDeHerramienta("x", nombre, args.toMap()), hoy) }

    // ── El camino entero: proponer, confirmar con el endpoint de siempre ─────

    @Test
    fun `la propuesta llega a la app, no escribe nada, y confirmarla con POST api events crea el movimiento`() = testApplication {
        montar()
        val modelo = ModeloDeGuion(listOf(pide(PROPONER_MOVIMIENTO, "monto" to "45000", "cuenta" to "la Nu", "categoria" to "comida", "nota" to "Almuerzo con Caro")))
        modelos += modelo
        val antes = movimientosDe(a.duenoA)

        val r = preguntar("hoy gasté 45 mil en almuerzo con la Nu")

        val p = r.propuestas.single()
        assertEquals(TipoDeAccion.ANOTAR_MOVIMIENTO, p.tipo)
        assertEquals("Anotar un gasto de \$45.000 en Comida desde Nu, hoy · «Almuerzo con Caro»", p.frase)
        assertFalse(p.categoriaNueva, "«comida» es su «Comida» de siempre")
        assertEquals(antes, movimientosDe(a.duenoA), "proponer no escribe nada")
        assertTrue(r.text.none { it.isDigit() }, "el turno cerró en la tarjeta, sin cifras propias: ${r.text}")
        assertEquals(1, modelo.llamadas, "una sola vuelta: no hizo falta otra llamada para decir «te la dejé abajo»")

        // «Hacerlo»: el endpoint de siempre, con lo que trae la tarjeta tal cual.
        val confirmar = client.post("/api/events") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(FinancialEvent.serializer(), p.movimiento!!))
        }
        assertEquals(HttpStatusCode.Created, confirmar.status, confirmar.bodyAsText())
        assertEquals(antes + 1, movimientosDe(a.duenoA))
        val creado = transaction { Events.selectAll().where { Events.id eq p.movimiento!!.id }.single() }
        assertEquals(45_000L, creado[Events.amount])
        assertEquals("Comida", creado[Events.category])
        assertEquals(a.cuentaDeA, creado[Events.accountId])

        // Un doble toque no duplica: el id viene fijado en la propuesta.
        client.post("/api/events") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(FinancialEvent.serializer(), p.movimiento!!))
        }
        assertEquals(antes + 1, movimientosDe(a.duenoA))
    }

    @Test
    fun `una propuesta sobre la cuenta de otro no llega a la app, y el modelo recibe el motivo`() = testApplication {
        montar()
        val modelo = ModeloDeGuion(
            listOf(
                pide(PROPONER_MOVIMIENTO, "monto" to "45000", "cuenta" to "Davivienda de B", "categoria" to "Comida"),
                RespuestaDelModelo.Texto("No encuentro esa cuenta entre las tuyas. ¿Desde cuál lo pagaste?"),
            ),
        )
        modelos += modelo

        val r = preguntar("anota 45 mil desde Davivienda de B")

        assertTrue(r.propuestas.isEmpty(), "una cuenta ajena no se propone")
        assertEquals(0L, propuestasGuardadas())
        assertTrue("no tiene una cuenta que se llame «Davivienda de B»" in modelo.resultados.single(), modelo.resultados.single())
        assertFalse("Davivienda de B" in modelo.resultados.single().substringAfter("Sus cuentas son:"), "no le lista las cuentas de otro")
        assertEquals(2, modelo.llamadas, "con una propuesta inválida el modelo sí vuelve a hablar")
    }

    @Test
    fun `un monto negativo o cero no se propone`() {
        listOf("-45000", "0", "-45.000").forEach { monto ->
            val r = proponerDirecto(PROPONER_MOVIMIENTO, "monto" to monto, "cuenta" to "Nu", "categoria" to "Comida")
            assertTrue(r is ResultadoDePropuesta.Invalida, "$monto pasó")
            assertTrue("mayor que 0" in r.paraElModelo, r.paraElModelo)
        }
    }

    @Test
    fun `lo que el server no aceptaria tampoco se propone`() {
        // Una categoría que Movi escribe sola sacaría el gasto del mes.
        val reservada = proponerDirecto(PROPONER_MOVIMIENTO, "monto" to "45000", "cuenta" to "Nu", "categoria" to "Traspaso")
        assertTrue(reservada is ResultadoDePropuesta.Invalida, reservada.paraElModelo)
        // Lo que todavía no pasó no se anota.
        val futura = proponerDirecto(PROPONER_MOVIMIENTO, "monto" to "45000", "cuenta" to "Nu", "categoria" to "Comida", "fecha" to hoy.plusDays(3).toString())
        assertTrue(futura is ResultadoDePropuesta.Invalida, futura.paraElModelo)
        // Un gasto no va en la cuenta de un crédito.
        val enElCredito = proponerDirecto(PROPONER_MOVIMIENTO, "monto" to "45000", "cuenta" to "Crédito vehículo", "categoria" to "Comida")
        assertTrue(enElCredito is ResultadoDePropuesta.Invalida, enElCredito.paraElModelo)
    }

    @Test
    fun `una categoria que no existe se marca como nueva, y la tarjeta lo dice`() {
        val r = proponerDirecto(PROPONER_MOVIMIENTO, "monto" to "45.000", "cuenta" to "nu", "categoria" to "Mascotas")
        val p = (r as ResultadoDePropuesta.Lista).accion
        assertTrue(p.categoriaNueva)
        assertTrue("categoría nueva" in p.frase, p.frase)
        assertEquals(45_000L, p.movimiento!!.amount, "«45.000» con punto de miles")
    }

    // ── «No» ─────────────────────────────────────────────────────────────────

    @Test
    fun `No no crea nada, y el asistente lo sabe en el turno siguiente`() = testApplication {
        montar()
        modelos += ModeloDeGuion(listOf(pide(PROPONER_MOVIMIENTO, "monto" to "45000", "cuenta" to "Nu", "categoria" to "Comida")))
        val p = preguntar("anota 45 mil de comida con la Nu").propuestas.single()
        val antes = movimientosDe(a.duenoA)

        val no = client.post("/api/ai/propuestas/${p.id}/estado") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ResolverPropuestaRequest.serializer(), ResolverPropuestaRequest(EstadoDePropuesta.RECHAZADA)))
        }
        assertEquals(HttpStatusCode.NoContent, no.status)
        assertEquals(antes, movimientosDe(a.duenoA), "«No» no crea nada")

        modelos += ModeloDeGuion(listOf(RespuestaDelModelo.Texto("Entendido.")))
        preguntar("bueno, ¿y cuánto llevo en Comida?")
        val loQueVio = textoDe(pedidos.last())
        assertTrue("dijo NO" in loQueVio && p.frase in loQueVio, loQueVio)

        // Y al recargar, la tarjeta vuelve descartada, no con «Hacerlo».
        val conversacion = json.decodeFromString(
            ConversacionDelAsistente.serializer(),
            client.get("/api/ai/conversacion") { with(a) { como(a.duenoA) } }.bodyAsText(),
        )
        val recargada = conversacion.mensajes.flatMap { it.propuestas }.single()
        assertEquals(EstadoDePropuesta.RECHAZADA, recargada.estado)
    }

    @Test
    fun `nadie resuelve la propuesta de otro`() = testApplication {
        montar()
        modelos += ModeloDeGuion(listOf(pide(PROPONER_MOVIMIENTO, "monto" to "45000", "cuenta" to "Nu", "categoria" to "Comida")))
        val p = preguntar("anota 45 mil").propuestas.single()

        val deB = client.post("/api/ai/propuestas/${p.id}/estado") {
            with(a) { como(a.duenoB) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ResolverPropuestaRequest.serializer(), ResolverPropuestaRequest(EstadoDePropuesta.HECHA)))
        }
        assertEquals(HttpStatusCode.NotFound, deB.status)
        val conversacionDeB = client.get("/api/ai/conversacion") { with(a) { como(a.duenoB) } }.bodyAsText()
        assertFalse(p.id in conversacionDeB, "B no ve las propuestas de A")
    }

    // ── Los otros tres tipos ─────────────────────────────────────────────────

    @Test
    fun `cambiar la categoria de los parecidos se confirma con el lote de siempre`() = testApplication {
        montar()
        anotar("r1", "RAPPI COLOMBIA", "Otros", 25_000, hoy.minusDays(5))
        anotar("r2", "Rappi*Restaurante", "Otros", 31_000, hoy.minusDays(9))
        anotar("r-otro", "Rappi de B", "Otros", 10_000, hoy.minusDays(3), uid = a.duenoB, cuenta = a.cuentaDeB)

        val r = proponerDirecto(PROPONER_CAMBIO_DE_CATEGORIA, "texto" to "rappi", "categoria" to "comida")
        val p = (r as ResultadoDePropuesta.Lista).accion
        assertEquals(setOf("r1", "r2"), p.idsDeMovimientos.toSet(), "solo los de A")
        assertEquals("Comida", p.categoria)

        val lote = client.put("/api/events/category-en-lote") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RecategorizarEnLoteRequest.serializer(), RecategorizarEnLoteRequest(p.idsDeMovimientos, p.categoria!!)))
        }
        assertEquals(HttpStatusCode.OK, lote.status, lote.bodyAsText())
        val categorias = transaction { Events.selectAll().where { Events.id inList listOf("r1", "r2") }.map { it[Events.category] } }
        assertEquals(listOf("Comida", "Comida"), categorias)
    }

    @Test
    fun `un recurrente propuesto se crea con POST api recurring-rules, y no se propone uno repetido`() = testApplication {
        montar()
        val p = (proponerDirecto(PROPONER_RECURRENTE, "nombre" to "Colegio", "monto" to "1200000", "dia" to "25", "categoria" to "Educación", "cuenta" to "Nu") as ResultadoDePropuesta.Lista).accion
        assertEquals(25, p.recurrente!!.dayOfMonth)
        assertEquals(a.cuentaDeA, p.recurrente!!.accountId)

        val creada = client.post("/api/recurring-rules") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(com.jvillada.movi.shared.model.RecurringRule.serializer(), p.recurrente!!))
        }
        assertEquals(HttpStatusCode.Created, creada.status, creada.bodyAsText())
        assertEquals(1L, transaction { RecurringRules.selectAll().where { RecurringRules.userId eq a.duenoA }.count() })

        val repetida = proponerDirecto(PROPONER_RECURRENTE, "nombre" to "colegio", "monto" to "1200000", "dia" to "25", "categoria" to "Educación")
        assertTrue(repetida is ResultadoDePropuesta.Invalida, repetida.paraElModelo)
    }

    /** «El checklist lo tilda el movimiento»: sin movimiento no hay propuesta, con uno se marca con él. */
    @Test
    fun `un pago se marca solo con un movimiento que existe, con el endpoint del checklist`() = testApplication {
        montar()
        transaction {
            RecurringRules.insert {
                it[id] = "rr-gym"; it[userId] = a.duenoA; it[name] = "Gimnasio Cami"; it[category] = "Deporte"
                it[amount] = 180_000; it[dayOfMonth] = hoy.dayOfMonth; it[type] = TransactionType.EXPENSE.name
            }
        }
        // Sin movimiento: no hay propuesta, y el motivo manda a anotarlo primero.
        val sinMovimiento = proponerDirecto(PROPONER_PAGO_HECHO, "pago" to "gimnasio")
        assertTrue(sinMovimiento is ResultadoDePropuesta.Invalida, sinMovimiento.paraElModelo)

        // Dos pagos de gimnasio el mismo mes (el caso real que hace que Movi pregunte): con el monto, uno.
        anotar("g1", "Gimnasio Cami", "Deporte", 180_000, hoy)
        anotar("g2", "Gimnasio Cami", "Deporte", 60_000, hoy)
        val ambiguo = proponerDirecto(PROPONER_PAGO_HECHO, "pago" to "gimnasio")
        assertTrue(ambiguo is ResultadoDePropuesta.Invalida, "con dos posibles no se adivina")
        assertTrue("más de un movimiento" in ambiguo.paraElModelo, ambiguo.paraElModelo)
        val p = (proponerDirecto(PROPONER_PAGO_HECHO, "pago" to "gimnasio", "monto" to "180000") as ResultadoDePropuesta.Lista).accion
        assertEquals("g1", p.eventId)
        assertEquals("rr-gym", p.reglaId)
        assertNotNull(p.eventId, "nunca sin movimiento")

        val marca = client.post("/api/recurring-rules/rr-gym/occurrence") {
            with(a) { como(a.duenoA) }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(MarkOccurrenceRequest.serializer(), MarkOccurrenceRequest(period = p.periodo!!, eventId = p.eventId)))
        }
        assertEquals(HttpStatusCode.Created, marca.status, marca.bodyAsText())
        val sello = transaction {
            RecurringOccurrences.selectAll().where { (RecurringOccurrences.ruleId eq "rr-gym") and (RecurringOccurrences.userId eq a.duenoA) }.single()
        }
        assertEquals(p.eventId, sello[RecurringOccurrences.eventId])
    }

    @Test
    fun `mas de tres propuestas en un turno no llegan`() {
        val turno = PropuestasDelTurno(a.duenoA, hoy)
        val resultados = (1..5).map { i ->
            runBlocking {
                turno.ejecutar(LlamadaDeHerramienta("t$i", PROPONER_MOVIMIENTO, mapOf("monto" to "${i}000", "cuenta" to "Nu", "categoria" to "Comida")))
            }
        }
        assertEquals(PROPUESTAS_POR_TURNO, turno.propuestas.size)
        assertTrue("Ya hay" in resultados.last(), resultados.last())
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    /** Todo el texto que el modelo recibió en los mensajes de ese turno. */
    private fun textoDe(pedido: PedidoDelModelo): String = pedido.mensajes.joinToString("\n") { m ->
        m.content().string().orElse(null)
            ?: m.content().blockParams().orElse(emptyList()).mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n")
    }

}
