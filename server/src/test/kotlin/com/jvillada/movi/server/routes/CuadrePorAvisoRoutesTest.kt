package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PropuestaDeCuadrePorAviso
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * # El saldo del aviso → la propuesta de cuadrar
 *
 * Un aviso confirmado que trae «Saldo disponible» se compara con el saldo de Movi **en el instante
 * de su movimiento** (sin anulados). Si no coincide, se propone el ajuste; **nada se ajusta solo**.
 * Los textos de los avisos son sintéticos: no hay uno real con saldo (ver `SaldoDelAvisoTest`).
 */
class CuadrePorAvisoRoutesTest {

    private val testSecret = "test-secret-for-cuadre-por-aviso-min-32-chars"
    private val duenoId = "user-dueno-cuadre-aviso"
    private val otroId = "user-otro-cuadre-aviso"
    private val json = Json { ignoreUnknownKeys = true }
    private val ahorros = "acc_ahorros"
    private val dia = 1_790_000_000_000L
    private var n = 0

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:cuadre_por_aviso;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(AnomaliasDescartadas, SmsMessages, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, SmsMessages, AnomaliasDescartadas)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@cuadre.test"; it[name] = uid; it[passwordHash] = "hash" }
            }
            Accounts.insert { it[id] = ahorros; it[userId] = duenoId; it[name] = "Bancolombia Ahorros 8133"; it[type] = "SAVINGS" }
            Accounts.insert { it[id] = "acc_tarjeta"; it[userId] = duenoId; it[name] = "Master 3684"; it[type] = "CREDIT_CARD" }
        }
    }

    private fun anotar(
        cuenta: String,
        tipo: String,
        monto: Long,
        cuando: Long,
        categoria: String = "Comida",
        uid: String = duenoId,
    ): String {
        val id = "ev_${n++}"
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = uid
                it[accountId] = cuenta
                it[type] = tipo
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = "Movimiento $id"
                it[timestamp] = cuando
                it[reconciliationStatus] = "RECONCILED"
            }
        }
        return id
    }

    private fun aviso(texto: String, eventoId: String?, estado: String = SMS_STATE_CONFIRMED, uid: String = duenoId): String {
        val id = "sms_${n++}"
        transaction {
            SmsMessages.insert {
                it[SmsMessages.id] = id
                it[userId] = uid
                it[time] = "2026-10-12 10:00"
                it[bank] = "85540"
                it[text] = texto
                it[state] = estado
                it[det] = ""
                it[SmsMessages.eventoId] = eventoId
            }
        }
        return id
    }

    private fun anular(eventoId: String) = transaction {
        VoidEvents.insert {
            it[id] = "void_$eventoId"; it[userId] = duenoId; it[originalEventId] = eventoId; it[timestamp] = dia
        }
    }

    /** $200.000 de apertura y una compra de $46.000 confirmada con un aviso que dice [saldo]. */
    private fun compraConSaldo(saldo: String): Pair<String, String> {
        anotar(ahorros, "INCOME", 200_000L, dia - 86_400_000L, categoria = "Saldo inicial")
        val compra = anotar(ahorros, "EXPENSE", 46_000L, dia)
        val sms = aviso("Bancolombia: Compraste \$46.000 en TIENDA. Saldo disponible $saldo", compra)
        return sms to compra
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@cuadre.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private suspend fun ApplicationTestBuilder.propuestas(uid: String = duenoId): List<PropuestaDeCuadrePorAviso> {
        val res = client.get("/api/cuadres-por-aviso") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(ListSerializer(PropuestaDeCuadrePorAviso.serializer()), res.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.postear(ruta: String, uid: String = duenoId) =
        client.post(ruta) { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }

    private fun ajustesDeLaCuenta(): List<Long> = transaction {
        Events.selectAll()
            .where { (Events.accountId eq ahorros) and (Events.category eq ADJUSTMENT_CATEGORY) }
            .map { it[Events.timestamp] }
    }

    @Test
    fun `con diferencia se propone el ajuste, sin escribir nada`() = testApplication {
        application { testModule() }
        val (sms, _) = compraConSaldo("\$154.046")
        val p = propuestas().single()
        assertEquals(sms, p.avisoId)
        assertEquals(ahorros, p.cuentaId)
        assertEquals(154_046L, p.saldoDelBanco)
        assertEquals(154_000L, p.saldoEnMovi)
        assertEquals(46L, p.diferencia)
        assertEquals(dia, p.momento)
        assertTrue(ajustesDeLaCuenta().isEmpty(), "proponer no ajusta nada")
    }

    @Test
    fun `sin diferencia no hay propuesta, y los centavos no cuentan`() = testApplication {
        application { testModule() }
        compraConSaldo("\$154.000")
        assertEquals(emptyList(), propuestas())
    }

    @Test
    fun `una diferencia de centavos se redondea y no molesta`() = testApplication {
        application { testModule() }
        compraConSaldo("\$154.000,60")
        assertEquals(emptyList(), propuestas())
    }

    @Test
    fun `el saldo de Movi no cuenta los anulados`() = testApplication {
        application { testModule() }
        compraConSaldo("\$154.000")
        // Un gasto de $30.000 antes del aviso, anulado: sin excluirlo Movi diría $124.000.
        val anulado = anotar(ahorros, "EXPENSE", 30_000L, dia - 3_600_000L)
        anular(anulado)
        assertEquals(emptyList(), propuestas(), "el anulado no se suma")
        // Y uno vivo sí desajusta.
        anotar(ahorros, "EXPENSE", 10_000L, dia - 3_600_000L)
        assertEquals(10_000L, propuestas().single().diferencia)
    }

    @Test
    fun `se compara en el instante del aviso, no hoy`() = testApplication {
        application { testModule() }
        compraConSaldo("\$154.000")
        anotar(ahorros, "EXPENSE", 99_000L, dia + 86_400_000L)
        assertEquals(emptyList(), propuestas(), "lo anotado después no cambia lo que había entonces")
    }

    @Test
    fun `ajustar escribe el ajuste en el instante del aviso y la propuesta se va`() = testApplication {
        application { testModule() }
        val (sms, _) = compraConSaldo("\$154.046")
        val res = postear("/api/cuadres-por-aviso/$sms/ajustar")
        assertEquals(HttpStatusCode.OK, res.status)
        val ajuste = json.decodeFromString(FinancialEvent.serializer(), res.bodyAsText())
        assertEquals(46L, ajuste.amount)
        assertEquals(ADJUSTMENT_CATEGORY, ajuste.category)
        assertEquals("INCOME", ajuste.type.name, "subir el saldo de una cuenta de ahorros es un ingreso")
        assertEquals(listOf(dia), ajustesDeLaCuenta())
        assertEquals(emptyList(), propuestas())
        // Un segundo toque no escribe otro ajuste.
        assertEquals(HttpStatusCode.NotFound, postear("/api/cuadres-por-aviso/$sms/ajustar").status)
        assertEquals(1, ajustesDeLaCuenta().size)
    }

    @Test
    fun `no ajustar la saca sin tocar el saldo`() = testApplication {
        application { testModule() }
        val (sms, _) = compraConSaldo("\$154.046")
        assertEquals(HttpStatusCode.NoContent, postear("/api/cuadres-por-aviso/$sms/descartar").status)
        assertEquals(emptyList(), propuestas())
        assertTrue(ajustesDeLaCuenta().isEmpty())
    }

    @Test
    fun `un cuadre posterior ya dijo la ultima palabra`() = testApplication {
        application { testModule() }
        compraConSaldo("\$154.046")
        anotar(ahorros, "INCOME", 46L, dia + 3_600_000L, categoria = ADJUSTMENT_CATEGORY)
        assertEquals(emptyList(), propuestas())
    }

    @Test
    fun `solo cuenta el aviso mas reciente de cada cuenta`() = testApplication {
        application { testModule() }
        compraConSaldo("\$999.999")
        val otra = anotar(ahorros, "EXPENSE", 4_000L, dia + 60_000L)
        val nuevo = aviso("Compraste \$4.000. Saldo disponible \$149.000", otra)
        val p = propuestas().single()
        assertEquals(nuevo, p.avisoId, "el viejo no se propone: el saldo de después borra al de antes")
        assertEquals(-1_000L, p.diferencia)
    }

    @Test
    fun `sin confirmar, sin movimiento, anulado o en una tarjeta no se propone`() = testApplication {
        application { testModule() }
        anotar(ahorros, "INCOME", 200_000L, dia - 86_400_000L, categoria = "Saldo inicial")
        val compra = anotar(ahorros, "EXPENSE", 46_000L, dia)
        aviso("Compraste \$46.000. Saldo disponible \$1.000", compra, estado = SMS_STATE_PENDING)
        aviso("Compraste \$46.000. Saldo disponible \$1.000", eventoId = null)
        val anulada = anotar(ahorros, "EXPENSE", 1_000L, dia + 1)
        aviso("Compraste \$1.000. Saldo disponible \$1.000", anulada)
        anular(anulada)
        // En una tarjeta, «disponible» es el cupo, no la deuda.
        val enLaTarjeta = anotar("acc_tarjeta", "EXPENSE", 50_000L, dia)
        aviso("Compraste \$50.000 con tu T.Cred *3684. Saldo disponible \$2.950.000", enLaTarjeta)
        assertEquals(emptyList(), propuestas())
    }

    @Test
    fun `cada duenio ve y ajusta solo lo suyo`() = testApplication {
        application { testModule() }
        val (sms, _) = compraConSaldo("\$154.046")
        assertEquals(emptyList(), propuestas(otroId))
        assertEquals(HttpStatusCode.NotFound, postear("/api/cuadres-por-aviso/$sms/ajustar", otroId).status)
        assertTrue(ajustesDeLaCuenta().isEmpty())
        // El descarte del otro no le saca la propuesta al dueño.
        postear("/api/cuadres-por-aviso/$sms/descartar", otroId)
        assertEquals(1, propuestas().size)
    }
}
