package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DestinosDescartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTAS_FUERA_DE_RANGO
import com.jvillada.movi.shared.model.CUOTAS_SOLO_EN_UNA_COMPRA_CON_TARJETA
import com.jvillada.movi.shared.model.DE_UNA_TARJETA_SOLO_GASTOS
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MAXIMO_DE_CUOTAS
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ParteDelReparto
import com.jvillada.movi.shared.model.PartesDelAviso
import com.jvillada.movi.shared.model.QueFueLaParte
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.isCashFlow
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 71 · la compra con tarjeta queda completa
 *
 * 1. **`POST /api/events` acepta `cuotas`** («¿A cuántas cuotas?» de Agregar) como campo opcional: se
 *    guarda en un gasto de una tarjeta de crédito, se ignora en lo demás, un fuera de rango es 400, y
 *    sin el campo (un APK viejo) nada cambia. La compra no cambia de monto: cuenta entera.
 * 2. **Una compra con tarjeta se divide en gastos con su categoría** (`POST /api/events/{id}/repartir`):
 *    el saldo de la tarjeta y el total de gastos quedan exactos, cada parte hereda las cuotas, una
 *    parte que no es un gasto se rechaza, y «Deshacer» deja todo como estaba.
 */
class LaCompraConTarjetaTest {

    private val testSecret = "test-secret-for-la-compra-con-tarjeta-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val uid = "user-compra-tarjeta"

    private val nu = "acc-nu-tarjeta"
    private val ahorros = "acc-ahorros-compra"
    private val libranza = "acc-libranza-compra"

    private val hoy = AppClock.today()
    private val momento = appDateToEpochMillis(hoy) + 10 * 3_600_000L
    private val deudaInicial = 2_000_000L
    private val compra = 1_200_000L

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:la_compra_con_tarjeta_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        val tablas = arrayOf(
            Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, OccurrenceRejections,
            Credits, Cards, SmsMessages, PushSubscriptions, KnownDestinations, DestinosDescartados,
        )
        transaction {
            SchemaUtils.create(*tablas)
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            Users.insert { it[id] = uid; it[email] = "compra@movi.test"; it[name] = "Dueño"; it[passwordHash] = "hash" }
            cuenta(nu, "Nu", "CREDIT_CARD")
            cuenta(ahorros, "Bancolombia Ahorros", "SAVINGS")
            cuenta(libranza, "Libranza", "LOAN")
            evento("ev-deuda-nu", nu, "EXPENSE", deudaInicial, "Saldo inicial")
        }
    }

    private fun cuenta(id: String, nombre: String, tipo: String) {
        Accounts.insert { it[Accounts.id] = id; it[userId] = uid; it[name] = nombre; it[type] = tipo }
    }

    private fun evento(id: String, cuenta: String, tipo: String, monto: Long, categoria: String, cuotas: Int? = null) {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[category] = categoria
            it[description] = categoria
            it[timestamp] = momento
            it[eventSource] = "MANUAL"
            it[reconciliationStatus] = "RECONCILED"
            it[Events.cuotas] = cuotas
        }
    }

    private fun token(): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", uid).withClaim("email", "$uid@movi.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.cliente() = createClient { install(ContentNegotiation) { json() } }

    /** `POST /api/events` con el JSON tal cual (para poder mandar o no la clave `cuotas`). */
    private suspend fun ApplicationTestBuilder.anotar(json: String): HttpResponse =
        cliente().post("/api/events") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json)
        }

    private fun gasto(id: String, cuenta: String, monto: Long = compra, cuotas: String? = null, tipo: String = "EXPENSE"): String =
        """{"id":"$id","accountId":"$cuenta","type":"$tipo","amount":$monto,"category":"Mercado","description":"Mercado",""" +
            """"timestamp":$momento,"source":"MANUAL","reconciliationStatus":"RECONCILED"""" +
            (cuotas?.let { ""","cuotas":$it""" } ?: "") + "}"

    private fun cuotasDe(id: String): Int? = transaction {
        Events.selectAll().where { Events.id eq id }.first()[Events.cuotas]
    }

    private fun anulados(): Set<String> = transaction {
        VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
    }

    /** La deuda de la tarjeta: lo que se gastó menos lo que se pagó, sin anulados. */
    private fun deudaDeNu(): Long = transaction {
        val fuera = anulados()
        Events.selectAll().where { (Events.userId eq uid) and (Events.accountId eq nu) }
            .filter { it[Events.id] !in fuera }
            .sumOf { if (it[Events.type] == "EXPENSE") it[Events.amount] else -it[Events.amount] }
    }

    /** Lo que cuenta en «Gastos», por categoría (la regla de siempre: `isCashFlow`), sin anulados. */
    private fun gastosPorCategoria(): Map<String, Long> = transaction {
        val fuera = anulados()
        Events.selectAll().where { Events.userId eq uid }
            .filter { it[Events.id] !in fuera && it[Events.type] == "EXPENSE" }
            .filter { isCashFlow(AccountType.CREDIT_CARD, TransactionType.EXPENSE, it[Events.category]) && it[Events.accountId] == nu }
            .groupBy { it[Events.category] }
            .mapValues { (_, filas) -> filas.sumOf { it[Events.amount] } }
    }

    // ── 1. «¿A cuántas cuotas?» en POST /api/events ─────────────────────────────────────────────

    @Test
    fun `un gasto de una tarjeta guarda sus cuotas y no cambia de monto`() = testApplication {
        application { testModule() }
        val respuesta = anotar(gasto("ev-nu-12", nu, cuotas = "12"))
        assertEquals(HttpStatusCode.Created, respuesta.status)
        assertEquals(12, cuotasDe("ev-nu-12"))
        assertEquals(deudaInicial + compra, deudaDeNu(), "la compra cuenta entera: la deuda sube por el total")
        assertEquals(mapOf("Mercado" to compra), gastosPorCategoria(), "y entra entera a los gastos")
    }

    @Test
    fun `sin la clave cuotas, como un APK viejo, nada cambia`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.Created, anotar(gasto("ev-viejo", nu)).status)
        assertNull(cuotasDe("ev-viejo"))
        // Y un reenvío del mismo id sin cuotas no borra las que ya están.
        assertEquals(HttpStatusCode.Created, anotar(gasto("ev-con", nu, cuotas = "6")).status)
        assertEquals(HttpStatusCode.OK, anotar(gasto("ev-con", nu)).status)
        assertEquals(6, cuotasDe("ev-con"))
    }

    @Test
    fun `cuotas fuera de rango es 400 y no escribe nada`() = testApplication {
        application { testModule() }
        val cero = anotar(gasto("ev-cero", nu, cuotas = "0"))
        assertEquals(HttpStatusCode.BadRequest, cero.status)
        assertEquals(CUOTAS_FUERA_DE_RANGO, cero.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, anotar(gasto("ev-mucho", nu, cuotas = "${MAXIMO_DE_CUOTAS + 1}")).status)
        assertEquals(0L, transaction { Events.selectAll().where { Events.id eq "ev-cero" }.count() + Events.selectAll().where { Events.id eq "ev-mucho" }.count() })
    }

    @Test
    fun `fuera de un gasto de una tarjeta las cuotas se ignoran`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.Created, anotar(gasto("ev-ahorros", ahorros, cuotas = "12")).status)
        assertNull(cuotasDe("ev-ahorros"), "una cuenta de ahorros no difiere")
        assertEquals(HttpStatusCode.Created, anotar(gasto("ev-abono", nu, cuotas = "3", tipo = "INCOME")).status)
        assertNull(cuotasDe("ev-abono"), "un abono a la tarjeta no es una compra")
    }

    // ── 2. Dividir una compra con tarjeta ────────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.dividir(id: String, partes: PartesDelAviso): HttpResponse =
        cliente().post("/api/events/$id/repartir") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(partes)
        }

    private suspend fun ApplicationTestBuilder.deshacer(id: String): HttpResponse =
        cliente().post("/api/events/$id/deshacer-reparto") { header(HttpHeaders.Authorization, "Bearer ${token()}") }

    private fun parteDeGasto(id: String, monto: Long, categoria: String, cuenta: String = nu) = ParteDelReparto(
        monto = monto,
        tipo = QueFueLaParte.GASTO,
        evento = FinancialEvent(
            id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = monto, category = categoria,
            description = "Éxito", merchant = "Éxito", timestamp = momento, source = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        ),
    )

    private val enDos = PartesDelAviso(
        listOf(parteDeGasto("ev-parte-mercado", 900_000L, "Mercado"), parteDeGasto("ev-parte-farmacia", 300_000L, "Salud")),
    )

    @Test
    fun `una compra con tarjeta se divide en gastos, la deuda y los totales quedan exactos, y Deshacer los restaura`() = testApplication {
        application { testModule() }
        transaction { evento("ev-compra", nu, "EXPENSE", compra, "Mercado", cuotas = 3) }
        val deudaAntes = deudaDeNu()
        assertEquals(mapOf("Mercado" to compra), gastosPorCategoria())

        assertEquals(HttpStatusCode.OK, dividir("ev-compra", enDos).status)
        assertTrue("ev-compra" in anulados())
        assertEquals(deudaAntes, deudaDeNu(), "la tarjeta debe lo mismo: la compra ahora está en partes")
        assertEquals(mapOf("Mercado" to 900_000L, "Salud" to 300_000L), gastosPorCategoria())
        assertEquals(compra, gastosPorCategoria().values.sum(), "el total de gastos no cambia")
        // Cada parte hereda las cuotas: el extracto sigue contando la cuota del mes, no el total.
        assertEquals(3, cuotasDe("ev-parte-mercado"))
        assertEquals(3, cuotasDe("ev-parte-farmacia"))

        assertEquals(HttpStatusCode.OK, deshacer("ev-parte-farmacia").status)
        assertTrue("ev-compra" !in anulados())
        assertTrue(setOf("ev-parte-mercado", "ev-parte-farmacia").all { it in anulados() })
        assertEquals(deudaAntes, deudaDeNu())
        assertEquals(mapOf("Mercado" to compra), gastosPorCategoria(), "Deshacer deja la compra como estaba")
        assertEquals(3, cuotasDe("ev-compra"))
    }

    @Test
    fun `de una tarjeta, una parte que no es un gasto se rechaza y no cambia nada`() = testApplication {
        application { testModule() }
        transaction { evento("ev-compra", nu, "EXPENSE", compra, "Mercado") }
        val conUnaCuota = PartesDelAviso(
            listOf(
                parteDeGasto("ev-parte-mercado", 900_000L, "Mercado"),
                ParteDelReparto(
                    monto = 300_000L,
                    tipo = QueFueLaParte.CUOTA,
                    patas = DosPatasDelAviso(
                        operacion = OperacionDelAviso.CUOTA, origenId = nu, destinoId = libranza, monto = 300_000L,
                        timestamp = momento, transferId = "tr-x", origenEventId = "ev-x-sale", destinoEventId = "ev-x-entra",
                    ),
                ),
            ),
        )
        val respuesta = dividir("ev-compra", conUnaCuota)
        assertEquals(HttpStatusCode.UnprocessableEntity, respuesta.status)
        assertEquals(DE_UNA_TARJETA_SOLO_GASTOS, respuesta.bodyAsText())
        assertTrue(anulados().isEmpty())
        assertEquals(mapOf("Mercado" to compra), gastosPorCategoria())
    }

    // ── Revisión de la vuelta 71 · de una deuda solo se divide una compra con tarjeta ──────────

    /** Las dos patas de un par (`transferId`): la que sale de [deuda] y la que entra a ahorros. */
    private fun par(tr: String, deuda: String, monto: Long, categoria: String) = transaction {
        listOf("$tr-deuda" to (deuda to "EXPENSE"), "$tr-ahorros" to (ahorros to "INCOME")).forEach { (id, cuentaYTipo) ->
            Events.insert {
                it[Events.id] = id
                it[userId] = uid
                it[accountId] = cuentaYTipo.first
                it[type] = cuentaYTipo.second
                it[amount] = monto
                it[category] = categoria
                it[description] = categoria
                it[timestamp] = momento
                it[eventSource] = "MANUAL"
                it[reconciliationStatus] = "RECONCILED"
                it[transferId] = tr
            }
        }
    }

    @Test
    fun `de una deuda no se divide un desembolso, un avance ni un ajuste, y nada desaparece`() = testApplication {
        application { testModule() }
        // El desembolso de un crédito: la pata del crédito sale, la de ahorros entra.
        par("tr-desembolso", libranza, 5_000_000L, "Desembolso de crédito")
        val desembolso = PartesDelAviso(
            listOf(parteDeGasto("p-d1", 3_000_000L, "Mercado", libranza), parteDeGasto("p-d2", 2_000_000L, "Hogar", libranza)),
        )
        val r1 = dividir("tr-desembolso-deuda", desembolso)
        assertEquals(HttpStatusCode.UnprocessableEntity, r1.status)
        assertEquals(com.jvillada.movi.shared.model.DE_UNA_DEUDA_SOLO_COMPRAS_CON_TARJETA, r1.bodyAsText())

        // Un avance de la tarjeta: la pata de la tarjeta lleva `transferId`.
        par("tr-avance", nu, 400_000L, "Traspaso")
        val avance = PartesDelAviso(listOf(parteDeGasto("p-a1", 300_000L, "Mercado"), parteDeGasto("p-a2", 100_000L, "Salud")))
        assertEquals(HttpStatusCode.UnprocessableEntity, dividir("tr-avance-deuda", avance).status)

        // Un ajuste de saldo de la tarjeta.
        transaction { evento("ev-ajuste", nu, "EXPENSE", 50_000L, "Ajuste de saldo") }
        val ajuste = PartesDelAviso(listOf(parteDeGasto("p-j1", 30_000L, "Mercado"), parteDeGasto("p-j2", 20_000L, "Salud")))
        assertEquals(HttpStatusCode.UnprocessableEntity, dividir("ev-ajuste", ajuste).status)

        assertTrue(anulados().isEmpty(), "nada se anuló: la plata sigue donde estaba")
        assertEquals(deudaInicial + 400_000L + 50_000L, deudaDeNu())
    }

    // ── Vuelta 133 · cambiar las cuotas de una compra ya anotada (`PUT /api/events/{id}/cuotas`) ──────

    private suspend fun ApplicationTestBuilder.cambiarCuotas(id: String, cuotas: Int, comoQuien: String = token()): HttpResponse =
        cliente().put("/api/events/$id/cuotas") {
            header(HttpHeaders.Authorization, "Bearer $comoQuien")
            contentType(ContentType.Application.Json)
            setBody("""{"cuotas":$cuotas}""")
        }

    private fun otroUsuario(): String {
        val otro = "user-otro-cuotas"
        transaction {
            Users.insert { it[id] = otro; it[email] = "otro-cuotas@movi.test"; it[name] = "Otro"; it[passwordHash] = "hash" }
            Accounts.insert { it[Accounts.id] = "acc-otro-tarjeta"; it[userId] = otro; it[name] = "Otra tarjeta"; it[type] = "CREDIT_CARD" }
        }
        return JWT.create()
            .withIssuer(issuer).withAudience(audience)
            .withClaim("userId", otro).withClaim("email", "$otro@movi.test")
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
            .sign(Algorithm.HMAC256(testSecret))
    }

    @Test
    fun `vuelta 133 · las cuotas de una compra anotada se cambian sin tocar monto, fecha ni categoria`() = testApplication {
        application { testModule() }
        transaction { evento("ev-nevera", nu, "EXPENSE", 6_000_000L, "Hogar") }
        val respuesta = cambiarCuotas("ev-nevera", 60)
        assertEquals(HttpStatusCode.OK, respuesta.status, respuesta.bodyAsText())
        assertEquals(60, cuotasDe("ev-nevera"))
        val fila = transaction { Events.selectAll().where { Events.id eq "ev-nevera" }.first() }
        assertEquals(6_000_000L, fila[Events.amount])
        assertEquals("Hogar", fila[Events.category])
        assertEquals(momento, fila[Events.timestamp])
        // Idempotente: el mismo número otra vez contesta lo mismo y no cambia nada.
        assertEquals(HttpStatusCode.OK, cambiarCuotas("ev-nevera", 60).status)
        assertEquals(60, cuotasDe("ev-nevera"))
        // Y se puede corregir.
        assertEquals(HttpStatusCode.OK, cambiarCuotas("ev-nevera", 48).status)
        assertEquals(48, cuotasDe("ev-nevera"))
    }

    @Test
    fun `vuelta 133 · las cuotas van de 1 a 72, solo en un gasto de una tarjeta del dueno`() = testApplication {
        application { testModule() }
        transaction {
            evento("ev-tarjeta", nu, "EXPENSE", 300_000L, "Mercado")
            evento("ev-ahorros", ahorros, "EXPENSE", 300_000L, "Mercado")
            evento("ev-pago", nu, "INCOME", 300_000L, "Pago de tarjeta")
            evento("ev-anulado", nu, "EXPENSE", 300_000L, "Mercado")
            VoidEvents.insert { it[id] = "void-1"; it[userId] = uid; it[originalEventId] = "ev-anulado"; it[VoidEvents.timestamp] = momento }
        }
        assertEquals(HttpStatusCode.BadRequest, cambiarCuotas("ev-tarjeta", 0).status)
        assertEquals(HttpStatusCode.BadRequest, cambiarCuotas("ev-tarjeta", MAXIMO_DE_CUOTAS + 1).status)
        val deAhorros = cambiarCuotas("ev-ahorros", 12)
        assertEquals(HttpStatusCode.UnprocessableEntity, deAhorros.status)
        assertEquals(CUOTAS_SOLO_EN_UNA_COMPRA_CON_TARJETA, deAhorros.bodyAsText())
        assertEquals(HttpStatusCode.UnprocessableEntity, cambiarCuotas("ev-pago", 12).status, "un abono a la tarjeta no tiene cuotas")
        assertEquals(HttpStatusCode.NotFound, cambiarCuotas("ev-anulado", 12).status)
        assertEquals(HttpStatusCode.NotFound, cambiarCuotas("no-existe", 12).status)
        assertEquals(HttpStatusCode.NotFound, cambiarCuotas("ev-tarjeta", 12, comoQuien = otroUsuario()).status, "de otro: 404")
        assertNull(cuotasDe("ev-tarjeta"), "nada de lo rechazado escribió")
        assertNull(cuotasDe("ev-ahorros"))
    }

    @Test
    fun `vuelta 133 revision 2 · el POST y el PUT de las cuotas aceptan lo mismo`() = testApplication {
        application { testModule() }
        // Un predial pagado con la tarjeta, en «Impuestos»: el POST guarda sus cuotas y el PUT las cambia.
        val predial = gasto("ev-predial", nu, cuotas = "12").replace("\"category\":\"Mercado\",\"description\":\"Mercado\"", "\"category\":\"Impuestos\",\"description\":\"Predial Medellín\"")
        assertEquals(HttpStatusCode.Created, anotar(predial).status)
        assertEquals(12, cuotasDe("ev-predial"))
        assertEquals(HttpStatusCode.OK, cambiarCuotas("ev-predial", 24).status)
        assertEquals(24, cuotasDe("ev-predial"))
        // Un cargo del banco (la cuota de manejo): el POST las ignora y el PUT las rechaza.
        val manejo = gasto("ev-manejo", nu, monto = 45_000L, cuotas = "3").replace("\"category\":\"Mercado\",\"description\":\"Mercado\"", "\"category\":\"Otros\",\"description\":\"CUOTA DE MANEJO\"")
        assertEquals(HttpStatusCode.Created, anotar(manejo).status)
        assertNull(cuotasDe("ev-manejo"))
        assertEquals(HttpStatusCode.UnprocessableEntity, cambiarCuotas("ev-manejo", 3).status)
    }
}
