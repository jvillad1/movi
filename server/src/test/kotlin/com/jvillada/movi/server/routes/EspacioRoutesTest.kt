package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.balance.loadNonVoidedEvents
import com.jvillada.movi.server.ai.LlamadaDeHerramienta
import com.jvillada.movi.server.ai.TOTALES_POR_CATEGORIA
import com.jvillada.movi.server.ai.ejecutarHerramienta
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.cuentaEnGastosEIngresos
import com.jvillada.movi.shared.model.esGastoDelPeriodo
import com.jvillada.movi.shared.model.montoContado
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Espacios
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.GastosDelEspacio
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LiquidacionesDelEspacio
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.netoDe
import com.jvillada.movi.shared.model.PropuestaDeSaldar
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # Espacios en el server (R3)
 *
 * El CRUD filtrado por usuario, los upserts por id (el teléfono reintenta), saldar dos veces = una,
 * y la regla de plata vista desde las cifras que el server manda: «Salió» cuenta solo tu parte y
 * tu parte de lo que pagó Caro, y la transferencia de Caro vinculada no suma en «Entró».
 */
class EspacioRoutesTest {

    private val secreto = "test-secret-for-espacio-routes-tests-min-32-chars"
    private val json = Json { ignoreUnknownKeys = true }
    private val a = "user-a-esp"
    private val b = "user-b-esp"
    private val ahorros = "acc-ahorros-a"
    private val ahorrosB = "acc-ahorros-b"

    private val tablas = arrayOf(
        Users, Accounts, StatementImports, Events, VoidEvents,
        Budgets, RecurringRules, SmsMessages, Credits, Cards, Goals, CardPaymentDismissals, PushSubscriptions,
        CategoryPrefs, RecurringOccurrences, OccurrenceRejections, Apartados,
        KnownDestinations, Espacios, GastosDelEspacio, LiquidacionesDelEspacio,
        // Las lee el contexto de Movi AI (`cargarDatosDelUsuario`): los papeles y las suscripciones.
        Documents, Subscriptions,
        // Vuelta 66: el «No» de una propuesta de saldar se guarda como huella descartada.
        AnomaliasDescartadas,
    )

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:espacio_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*tablas)
            SchemaUtils.create(*tablas)
            listOf(a to "Juan Villada", b to "Otra").forEach { (id, nombre) ->
                Users.insert {
                    it[Users.id] = id
                    it[email] = "$id@esp.test"
                    it[name] = nombre
                    it[passwordHash] = "hash"
                }
            }
            listOf(ahorros to a, ahorrosB to b).forEach { (id, uid) ->
                Accounts.insert {
                    it[Accounts.id] = id
                    it[userId] = uid
                    it[name] = "Ahorros"
                    it[type] = "SAVINGS"
                    it[currency] = "COP"
                }
            }
            listOf("dst_caro" to a, "dst_otro_b" to b).forEach { (id, uid) ->
                KnownDestinations.insert {
                    it[KnownDestinations.id] = id
                    it[userId] = uid
                    it[nombre] = if (uid == a) "Caro" else "Otro"
                    it[numero] = ""
                    it[createdAt] = 0L
                }
            }
        }
    }

    private val hoy = System.currentTimeMillis() - 60_000

    private fun evento(id: String, monto: Long, tipo: String = "EXPENSE", categoria: String = "Mercado", descripcion: String = "Mercado", uid: String = a, cuenta: String = ahorros, estado: String = "RECONCILED") =
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = uid
                it[accountId] = cuenta
                it[type] = tipo
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = descripcion
                it[timestamp] = hoy
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = estado
            }
        }

    private fun token(uid: String): String = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@esp.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000))
        .sign(Algorithm.HMAC256(secreto))

    private fun Application.modulo() {
        configureSerialization()
        val verificador = JWT.require(Algorithm.HMAC256(secreto)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                verifier(verificador)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun conApp(bloque: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { modulo() }
        bloque()
    }

    private suspend fun ApplicationTestBuilder.post(ruta: String, cuerpo: String, uid: String = a): HttpResponse =
        client.post(ruta) {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(cuerpo)
        }

    private suspend fun ApplicationTestBuilder.get(ruta: String, uid: String = a): HttpResponse =
        client.get(ruta) { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }

    private val casa = """{"id":"esp_casa","nombre":"Casa con Caro","miembros":[{"id":"dst_caro"}]}"""

    private suspend fun ApplicationTestBuilder.detalle(uid: String = a): DetalleDelEspacio =
        json.decodeFromString(get("/api/espacios/esp_casa", uid).bodyAsText())

    // ── CRUD y aislamiento ────────────────────────────────────────────────────

    @Test
    fun `crear la casa con Caro la lista con el duenno primero y los nombres resueltos`() = conApp {
        assertEquals(HttpStatusCode.Created, post("/api/espacios", casa).status)
        // Reenviar el mismo id no duplica: lo actualiza.
        assertEquals(HttpStatusCode.OK, post("/api/espacios", casa.replace("Casa con Caro", "La casa")).status)

        val lista: List<Espacio> = json.decodeFromString(get("/api/espacios").bodyAsText())
        assertEquals(1, lista.size)
        assertEquals("La casa", lista.single().nombre)
        assertEquals(listOf(MIEMBRO_YO to "Juan", "dst_caro" to "Caro"), lista.single().miembros.map { it.id to it.nombre })
    }

    @Test
    fun `otro usuario no ve, no usa ni pisa el espacio, y no se puede sumar a un tercero ajeno`() = conApp {
        post("/api/espacios", casa)

        val deB: List<Espacio> = json.decodeFromString(get("/api/espacios", b).bodyAsText())
        assertTrue(deB.isEmpty())
        assertEquals(HttpStatusCode.NotFound, get("/api/espacios/esp_casa", b).status)
        assertEquals(HttpStatusCode.Conflict, post("/api/espacios", casa.replace("dst_caro", "dst_otro_b"), b).status)
        assertEquals(HttpStatusCode.NotFound, post("/api/espacios/esp_casa/gastos", """{"pagadoPor":"dst_caro","monto":1000,"categoria":"Mercado"}""", b).status)
        assertEquals(HttpStatusCode.NotFound, post("/api/espacios", """{"nombre":"X","miembros":[{"id":"dst_otro_b"}]}""").status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/espacios/esp_casa") { header(HttpHeaders.Authorization, "Bearer ${token(b)}") }.status)
    }

    @Test
    fun `repartir un movimiento ajeno o de otro usuario se rechaza`() = conApp {
        post("/api/espacios", casa)
        evento("e_de_b", 100_000, uid = b, cuenta = ahorrosB)
        evento("e_ingreso", 100_000, tipo = "INCOME")

        assertEquals(HttpStatusCode.NotFound, post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_de_b"}""").status)
        assertEquals(HttpStatusCode.BadRequest, post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_ingreso"}""").status)
    }

    // ── Idempotencia y saldo ──────────────────────────────────────────────────

    @Test
    fun `repartir dos veces el mismo movimiento es repartirlo una`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 240_000)

        assertEquals(HttpStatusCode.Created, post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado","gastoId":"g1"}""").status)
        val otra = post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado","gastoId":"g2"}""")
        assertEquals(HttpStatusCode.OK, otra.status)
        assertEquals("g1", json.decodeFromString<GastoDelEspacio>(otra.bodyAsText()).id)
        assertEquals(1L, transaction { GastosDelEspacio.selectAll().count() })

        val d = detalle()
        assertEquals(120_000, d.saldo.netoDe(MIEMBRO_YO), "Caro te debe su mitad")
        assertEquals(240_000, d.delPeriodo.total)
    }

    @Test
    fun `liquidar dos veces con el mismo id es liquidar una`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 780_000)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        val liquidacion = """{"id":"liq_1","de":"dst_caro","a":"yo","monto":390000}"""

        assertEquals(HttpStatusCode.Created, post("/api/espacios/esp_casa/liquidaciones", liquidacion).status)
        assertEquals(HttpStatusCode.OK, post("/api/espacios/esp_casa/liquidaciones", liquidacion).status)

        val d = detalle()
        assertEquals(1, d.liquidaciones.size)
        assertEquals(0L, d.saldo.netoDe(MIEMBRO_YO))
        assertTrue(d.saldo.reembolsos.isEmpty())
    }

    @Test
    fun `un gasto que pago Caro deja tu deuda y no toca ningun saldo`() = conApp {
        post("/api/espacios", casa)
        val cuerpo = """{"id":"g_caro","pagadoPor":"dst_caro","monto":240000,"categoria":"Mercado","descripcion":"Mercado del sábado"}"""
        assertEquals(HttpStatusCode.Created, post("/api/espacios/esp_casa/gastos", cuerpo).status)
        assertEquals(HttpStatusCode.OK, post("/api/espacios/esp_casa/gastos", cuerpo).status)

        val d = detalle()
        assertEquals(1, d.gastos.size)
        assertEquals(-120_000, d.saldo.netoDe(MIEMBRO_YO))
        assertEquals(0L, transaction { Events.selectAll().count() }, "no se crea ningún movimiento")
    }

    // ── Solo tu parte, en las cifras del server ──────────────────────────────

    @Test
    fun `Salio cuenta solo tu parte y tu parte de lo de Caro, y el reembolso vinculado no suma en Entro`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 240_000)
        evento("e_almuerzo", 30_000, categoria = "Comida")
        evento("e_de_caro", 390_000, tipo = "INCOME", categoria = "Ingreso", descripcion = "Transferencia de Caro")
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        post("/api/espacios/esp_casa/gastos", """{"id":"g_caro","pagadoPor":"dst_caro","monto":100000,"categoria":"Mercado","fecha":$hoy}""")
        post("/api/espacios/esp_casa/liquidaciones", """{"id":"liq","de":"dst_caro","a":"yo","monto":390000,"eventoId":"e_de_caro"}""")

        val resumen = Json.parseToJsonElement(get("/api/dashboard/summary").bodyAsText()).jsonObject
        assertEquals(120_000L + 30_000L + 50_000L, resumen["monthSpent"]!!.jsonPrimitive.long)
        assertEquals(0L, resumen["monthIncome"]?.jsonPrimitive?.long ?: 0L, "el reembolso de Caro no es ingreso")
        assertEquals(170_000L, resumen["spentByCategory"]!!.jsonObject["Mercado"]!!.jsonPrimitive.long)

        // Los movimientos que bajan a la app: el monto entero (el saldo es real), su parte ajena y el
        // reembolso fuera del flujo.
        val eventos = runBlocking { loadNonVoidedEvents(a) }.associateBy { it.id }
        assertEquals(240_000, eventos.getValue("e_mercado").amount)
        assertEquals(120_000, eventos.getValue("e_mercado").parteAjena)
        assertEquals(false, eventos.getValue("e_de_caro").countsAsCashFlow)
        assertEquals("esp_casa", eventos.getValue("e_de_caro").saldaEspacioId)
    }

    @Test
    fun `vincular una transferencia en la direccion equivocada o ya usada se rechaza`() = conApp {
        post("/api/espacios", casa)
        evento("e_gasto", 50_000)
        evento("e_entra", 50_000, tipo = "INCOME")

        assertEquals(
            HttpStatusCode.BadRequest,
            post("/api/espacios/esp_casa/liquidaciones", """{"id":"l1","de":"dst_caro","a":"yo","monto":50000,"eventoId":"e_gasto"}""").status,
        )
        assertEquals(
            HttpStatusCode.Created,
            post("/api/espacios/esp_casa/liquidaciones", """{"id":"l2","de":"dst_caro","a":"yo","monto":50000,"eventoId":"e_entra"}""").status,
        )
        assertEquals(
            HttpStatusCode.Conflict,
            post("/api/espacios/esp_casa/liquidaciones", """{"id":"l3","de":"dst_caro","a":"yo","monto":50000,"eventoId":"e_entra"}""").status,
        )
    }

    @Test
    fun `quitar del espacio devuelve el movimiento a contar entero, y borrar el espacio se lleva sus gastos`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 240_000)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")

        val quitar = client.delete("/api/espacios/esp_casa/compartir/e_mercado") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }
        assertEquals(HttpStatusCode.NoContent, quitar.status)
        assertEquals(0L, runBlocking { loadNonVoidedEvents(a) }.single().parteAjena)

        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        val borrar = client.delete("/api/espacios/esp_casa") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }
        assertEquals(HttpStatusCode.NoContent, borrar.status)
        assertEquals(0L, transaction { GastosDelEspacio.selectAll().count() })
    }

    @Test
    fun `Movi propone la transferencia de Caro que salda la casa`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 780_000)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        evento("e_de_caro", 390_000, tipo = "INCOME", categoria = "Ingreso", descripcion = "Transferencia de Caro")

        val d = detalle()
        assertEquals(listOf("e_de_caro"), d.propuestas.map { it.eventoId })
        val liq = json.decodeFromString<LiquidacionDelEspacio>(
            post("/api/espacios/esp_casa/liquidaciones", """{"id":"l","de":"dst_caro","a":"yo","monto":390000,"eventoId":"e_de_caro"}""").bodyAsText(),
        )
        assertEquals("e_de_caro", liq.eventoId)
        assertTrue(detalle().propuestas.isEmpty())
    }

    /**
     * **Ninguna cifra de «entró / salió / neto» difiere entre pantallas** (vuelta 10). Un período con
     * (1) un gasto tuyo repartido 50/50, (2) un gasto que pagó Caro con tu parte y (3) la
     * transferencia de Caro que salda: `/api/finance-summary` (la segunda línea del héroe de Hoy, el
     * Disponible de Plan, el Día a día) tiene que dar lo mismo que `/api/dashboard/summary`
     * (`movimientosDeFlujo`), que «En qué se va» (la regla de `:core` sobre lo que baja a la app más
     * tu parte de otros), que el contexto de Movi AI y que su herramienta de totales. Antes
     * `finance-summary` sumaba el monto entero: Salió $510.000 en vez de $200.000, y los $390.000 de
     * Caro entraban como ingreso.
     */
    @Test
    fun `finance-summary cuenta solo tu parte, igual que Salio, En que se va y Movi AI`() = conApp {
        post("/api/espacios", casa)
        evento("e_sueldo", 1_000_000, tipo = "INCOME", categoria = "Salario", descripcion = "Nómina")
        evento("e_mercado", 240_000) // (1) lo pagaste tú y se repartió 50/50: tu parte, 120.000
        evento("e_almuerzo", 30_000, categoria = "Comida")
        evento("e_de_caro", 390_000, tipo = "INCOME", categoria = "Ingreso", descripcion = "Transferencia de Caro")
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        // (2) lo pagó Caro: tu parte, 50.000, cuenta el día del gasto
        post("/api/espacios/esp_casa/gastos", """{"id":"g_caro","pagadoPor":"dst_caro","monto":100000,"categoria":"Mercado","fecha":$hoy}""")
        // (3) Caro salda con una transferencia real: no es ingreso
        post("/api/espacios/esp_casa/liquidaciones", """{"id":"liq","de":"dst_caro","a":"yo","monto":390000,"eventoId":"e_de_caro"}""")

        val entro = 1_000_000L
        val salio = 120_000L + 30_000L + 50_000L

        val resumen = Json.parseToJsonElement(get("/api/finance-summary").bodyAsText()).jsonObject
        assertEquals(entro, resumen["ingresos"]!!.jsonPrimitive.long, "la transferencia que salda no es ingreso")
        assertEquals(salio, resumen["egresos"]!!.jsonPrimitive.long, "solo tu parte, y tu parte de lo que pagó Caro")

        // Lo mismo que `movimientosDeFlujo` (Salió, categorías, presupuestos).
        val tablero = Json.parseToJsonElement(get("/api/dashboard/summary").bodyAsText()).jsonObject
        assertEquals(entro, tablero["monthIncome"]?.jsonPrimitive?.long ?: 0L)
        assertEquals(salio, tablero["monthSpent"]!!.jsonPrimitive.long)

        // Lo mismo que «En qué se va» y el panel de Movimientos: la regla de :core sobre los movimientos
        // que bajan a la app (`/api/events/by-day` sale de `loadNonVoidedEvents`) y tu parte de otros.
        val eventos = runBlocking { loadNonVoidedEvents(a) }
        val tuParte: List<FinancialEvent> = json.decodeFromString(get("/api/espacios/tu-parte").bodyAsText())
        val tipos = mapOf(ahorros to AccountType.SAVINGS)
        assertEquals(salio, (eventos + tuParte).filter { esGastoDelPeriodo(it, tipos[it.accountId]) }.sumOf { montoContado(it) })
        assertEquals(
            entro,
            eventos.filter { it.type == TransactionType.INCOME && it.currency == "COP" && cuentaEnGastosEIngresos(it) }.sumOf { it.amount },
        )
        assertEquals(entro - salio, resumen["ingresos"]!!.jsonPrimitive.long - resumen["egresos"]!!.jsonPrimitive.long)

        // Movi AI: el «Resumen del mes en curso» del contexto, el contexto del período y la herramienta.
        val datos = runBlocking { cargarDatosDelUsuario(a) }
        assertEquals(entro to salio, datos.ingresos to datos.egresos)
        assertEquals(entro to salio, datos.periodo.ingresos to datos.periodo.gastoPorCategoria.values.sum())
        val hoyEnBogota = AppClock.today()
        val totales = runBlocking {
            ejecutarHerramienta(
                a,
                LlamadaDeHerramienta("t1", TOTALES_POR_CATEGORIA, mapOf("desde" to hoyEnBogota.minusDays(1).toString(), "hasta" to hoyEnBogota.toString())),
            )
        }
        assertTrue("- Entró: $entro" in totales, totales)
        assertTrue("- Salió: $salio" in totales, totales)
        assertTrue("Salda un gasto compartido" in totales, "la transferencia de Caro va rotulada aparte:\n$totales")

        // Los saldos NO cambian: la plata salió entera de la cuenta.
        assertEquals(240_000, eventos.single { it.id == "e_mercado" }.amount)
    }

    /**
     * **Vuelta de diseño 15: Mercado dice lo mismo en Categorías que en «En qué se va».** Ajustes →
     * Categorías sumaba el monto entero de cada movimiento: el mercado repartido 50/50 contaba
     * $240.000 en vez de tu parte, lo que pagó Caro no aparecía y lo que espera en «Por confirmar»
     * ya sumaba. Ahora su cifra del período sale de la misma regla.
     */
    @Test
    fun `la cifra de Mercado en Categorias es la de En que se va, con un gasto repartido y uno que pago otro`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 240_000) // lo pagaste tú, repartido 50/50: tu parte, 120.000
        evento("e_pendiente", 80_000, estado = "UNCONFIRMED") // espera en «Por confirmar»: no suma todavía
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        // Lo pagó Caro: tu parte, 50.000, cuenta el día del gasto.
        post("/api/espacios/esp_casa/gastos", """{"id":"g_caro","pagadoPor":"dst_caro","monto":100000,"categoria":"Mercado","fecha":$hoy}""")

        val categorias = Json.parseToJsonElement(get("/api/categories").bodyAsText()).jsonArray.map { it.jsonObject }
        val mercado = categorias.first { it["name"]!!.jsonPrimitive.content == "Mercado" }
        val enCategorias = mercado["monthTotal"]!!.jsonPrimitive.long

        // «En qué se va», como lo arma la app: la regla de :core (`esGastoDelPeriodo` + `montoContado`,
        // las que usa `analisisDelPeriodo`) sobre lo que baja a la app (`/api/events/by-day` sale de
        // `loadNonVoidedEvents`) más tu parte de lo que pagó otro. Todo cae en el período en curso.
        val eventos = runBlocking { loadNonVoidedEvents(a) }
        val tuParte: List<FinancialEvent> = json.decodeFromString(get("/api/espacios/tu-parte").bodyAsText())
        val tipos = mapOf(ahorros to AccountType.SAVINGS)
        val enQueSeVa = (eventos + tuParte)
            .filter { esGastoDelPeriodo(it, tipos[it.accountId]) && it.category == "Mercado" }
            .sumOf { montoContado(it) }

        assertEquals(120_000L + 50_000L, enQueSeVa)
        assertEquals(enQueSeVa, enCategorias, "Categorías y «En qué se va» dicen lo mismo de Mercado")
        // Y el widget del Inicio, que sale de `movimientosDeFlujo`.
        val tablero = Json.parseToJsonElement(get("/api/dashboard/summary").bodyAsText()).jsonObject
        assertEquals(enCategorias, tablero["spentByCategory"]!!.jsonObject["Mercado"]!!.jsonPrimitive.long)
        // El conteo sigue contando los usos tuyos del período (los dos movimientos), no la plata.
        assertEquals(2, mercado["monthMovements"]!!.jsonPrimitive.int)
    }

    /**
     * **Un apartado se consume con tu parte, no con el monto entero** (misma regla del dueño): el
     * mercado de $240.000 repartido 50/50 baja el apartado «Casa» en $120.000.
     */
    @Test
    fun `un gasto repartido consume del apartado solo tu parte`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 240_000)
        evento("e_almuerzo", 30_000)
        assertEquals(
            HttpStatusCode.Created,
            post("/api/apartados", """{"id":"apt_casa","accountId":"$ahorros","proposito":"Casa","monto":1000000,"categoria":"Mercado","desde":1}""").status,
        )
        suspend fun restante(): Long = Json.parseToJsonElement(get("/api/apartados").bodyAsText()).let { lista ->
            (lista as kotlinx.serialization.json.JsonArray).single().jsonObject["restante"]!!.jsonPrimitive.long
        }
        assertEquals(1_000_000L - 270_000L, restante(), "sin repartir, cuenta entero")

        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")

        assertEquals(1_000_000L - 120_000L - 30_000L, restante())
    }

    // ── Vuelta 66 · el saldo del grupo espera la transferencia ──────────────

    private suspend fun ApplicationTestBuilder.monthIncome(): Long =
        Json.parseToJsonElement(get("/api/dashboard/summary").bodyAsText()).jsonObject["monthIncome"]?.jsonPrimitive?.long ?: 0L

    /**
     * El caso de la revisión: «Marcar como saldado» antes de que llegue la plata. Ya no queda deuda, así
     * que la propuesta de siempre no salía y la transferencia de Caro quedaba contada como ingreso para
     * siempre. Ahora el saldado espera: se propone (en el grupo y en `/propuestas`), se vincula a ESE
     * saldado y `monthIncome` deja de contarla. Sin reglas nuevas: es la vinculación de siempre.
     */
    @Test
    fun `saldar primero, que llegue la transferencia, que se proponga, vincular, y monthIncome no la cuenta`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 336_100)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        // Se marca saldado sin movimiento: el grupo queda a mano.
        assertEquals(
            HttpStatusCode.Created,
            post("/api/espacios/esp_casa/liquidaciones", """{"id":"liq_ya","de":"dst_caro","a":"yo","monto":168050}""").status,
        )
        assertEquals(0L, detalle().saldo.netoDe(MIEMBRO_YO))
        // Llega la plata de Caro (±1 %): por ahora es ingreso.
        evento("e_de_caro", 168_500, tipo = "INCOME", categoria = "Ingreso", descripcion = "Transferencia de Caro")
        assertEquals(168_500L, monthIncome())

        val propuesta = detalle().propuestas.single()
        assertEquals("e_de_caro", propuesta.eventoId)
        assertEquals("liq_ya", propuesta.liquidacionId)
        // También fuera del grupo (Por revisar y la campana), con el grupo y el nombre.
        val todas = json.decodeFromString<List<PropuestaDeSaldar>>(get("/api/espacios/propuestas").bodyAsText())
        assertEquals(listOf("e_de_caro"), todas.map { it.eventoId })
        assertEquals("esp_casa", todas.single().espacioId)
        assertEquals("Casa con Caro", todas.single().nombreDelEspacio)
        assertEquals("Caro", todas.single().quien)

        // «Sí, saldar»: el mismo id del saldado, con la transferencia. Se vincula; no crea otro.
        val vinculada = post("/api/espacios/esp_casa/liquidaciones", """{"id":"liq_ya","de":"dst_caro","a":"yo","monto":168500,"eventoId":"e_de_caro"}""")
        assertEquals(HttpStatusCode.OK, vinculada.status)
        val liq = json.decodeFromString<LiquidacionDelEspacio>(vinculada.bodyAsText())
        assertEquals("e_de_caro", liq.eventoId)
        assertEquals(168_050L, liq.monto, "el monto del saldado no cambia")
        assertEquals(1L, transaction { LiquidacionesDelEspacio.selectAll().count() })

        assertEquals(0L, monthIncome(), "la transferencia vinculada no es ingreso")
        val d = detalle()
        assertTrue(d.propuestas.isEmpty())
        assertEquals(0L, d.saldo.netoDe(MIEMBRO_YO))
        assertEquals("Transferencia de Caro", d.liquidaciones.single().movimiento, "el historial dice con qué se pagó")
        assertTrue(json.decodeFromString<List<PropuestaDeSaldar>>(get("/api/espacios/propuestas").bodyAsText()).isEmpty())
    }

    @Test
    fun `deshacer un saldado devuelve la deuda y la transferencia vuelve a ser ingreso`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 336_100)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        evento("e_de_caro", 168_050, tipo = "INCOME", categoria = "Ingreso", descripcion = "Transferencia de Caro")
        post("/api/espacios/esp_casa/liquidaciones", """{"id":"liq","de":"dst_caro","a":"yo","monto":168050,"eventoId":"e_de_caro"}""")
        assertEquals(0L, monthIncome())

        val deshacer = client.delete("/api/espacios/esp_casa/liquidaciones/liq") { header(HttpHeaders.Authorization, "Bearer ${token(a)}") }
        assertEquals(HttpStatusCode.NoContent, deshacer.status)
        assertEquals(168_050L, monthIncome())
        assertEquals(168_050L, detalle().saldo.netoDe(MIEMBRO_YO))
        assertEquals(listOf("e_de_caro"), detalle().propuestas.map { it.eventoId }, "vuelve a proponerse")
    }

    @Test
    fun `No descarta la propuesta para siempre, y un saldado que espera no se vincula a una transferencia ajena`() = conApp {
        post("/api/espacios", casa)
        evento("e_mercado", 336_100)
        post("/api/espacios/esp_casa/compartir", """{"eventoId":"e_mercado"}""")
        post("/api/espacios/esp_casa/liquidaciones", """{"id":"liq_ya","de":"dst_caro","a":"yo","monto":168050}""")
        evento("e_de_caro", 168_050, tipo = "INCOME", categoria = "Ingreso", descripcion = "Transferencia de Caro")
        evento("e_gasto", 168_050)
        // La dirección se valida igual que siempre.
        assertEquals(
            HttpStatusCode.BadRequest,
            post("/api/espacios/esp_casa/liquidaciones", """{"id":"liq_ya","de":"dst_caro","a":"yo","monto":168050,"eventoId":"e_gasto"}""").status,
        )
        assertEquals(1, detalle().propuestas.size)
        assertEquals(HttpStatusCode.NoContent, post("/api/anomalias/descartar", """{"huella":"saldar:e_de_caro"}""").status)
        assertTrue(detalle().propuestas.isEmpty())
        assertEquals(168_050L, monthIncome(), "descartarla no la vincula: sigue siendo ingreso")
    }
}
