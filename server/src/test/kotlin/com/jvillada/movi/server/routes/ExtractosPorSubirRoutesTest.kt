package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.ExtractoPorSubir
import com.jvillada.movi.shared.model.huellaDelExtractoPorSubir
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Vuelta 94 · «¿Ya bajaste el extracto de Nu de octubre?», del lado del server: qué tarjetas piden su
 * extracto, qué cuenta como «ya se subió» (un documento de extracto de esa cuenta o un importe contra
 * ella, desde el corte), «Ya no» por la tabla de descartes de siempre, y que una tarjeta sin uso, sin
 * corte o en dólares no pide nada.
 */
class ExtractosPorSubirRoutesTest {

    private val testSecret = "test-secret-for-extractos-por-subir-min-32-chars"
    private val duenoId = "user-dueno-ext"
    private val otroId = "user-otro-ext"
    private val nu = "acc_nu"
    private val json = Json { ignoreUnknownKeys = true }

    /** El corte de la tarjeta de Nu es el 12; el 15 de octubre ya pasaron tres días. */
    private val dia15 = LocalDate.of(2026, 10, 15)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:extractos_por_subir_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(AnomaliasDescartadas, StatementImports, Documents, Cards, VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents, Cards, Documents, StatementImports, AnomaliasDescartadas)
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert { it[id] = uid; it[email] = "$uid@ext.test"; it[name] = uid; it[passwordHash] = "h" }
            }
            tarjeta(nu, duenoId, "Tarjeta Nu", banco = "Nu", corte = 12)
            compra("ev_cafe", nu, duenoId, 50_000L, LocalDate.of(2026, 10, 3))
        }
    }

    private fun tarjeta(id: String, uid: String, nombre: String, banco: String, corte: Int?, moneda: String = "COP") {
        Accounts.insert {
            it[Accounts.id] = id; it[userId] = uid; it[name] = nombre; it[type] = "CREDIT_CARD"; it[currency] = moneda; it[balance] = 0L
        }
        Cards.insert {
            it[accountId] = id; it[userId] = uid; it[bank] = banco; it[cutoffDay] = corte; it[paymentDay] = 3
        }
    }

    private fun compra(id: String, cuenta: String, uid: String, monto: Long, fecha: LocalDate, tipo: String = "EXPENSE") = Events.insert {
        it[Events.id] = id; it[userId] = uid; it[accountId] = cuenta; it[type] = tipo; it[amount] = monto
        it[currency] = "COP"; it[category] = "Comida"; it[description] = id
        it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
        it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
    }

    private fun documento(id: String, cuenta: String?, fecha: LocalDate, tipo: String = "EXTRACTO") = transaction {
        Documents.insert {
            it[Documents.id] = id; it[userId] = duenoId; it[name] = "$id.pdf"; it[kind] = tipo; it[mimeType] = "application/pdf"
            it[sizeBytes] = 3L; it[uploadedAt] = appDateToEpochMillis(fecha) + 9 * 3_600_000L; it[accountId] = cuenta
            it[content] = byteArrayOf(1, 2, 3)
        }
    }

    private fun propuestas(hoy: LocalDate, uid: String = duenoId): List<ExtractoPorSubir> =
        runBlocking { extractosPorSubirDe(uid, hoy) }

    @Test
    fun `antes de tres dias nada, despues la propuesta del corte`() {
        assertTrue(propuestas(LocalDate.of(2026, 10, 14)).isEmpty())
        val p = propuestas(dia15).single()
        assertEquals(nu, p.cuentaId)
        assertEquals("Nu", p.banco)
        assertEquals("2026-10-12", p.corte)
    }

    @Test
    fun `un extracto de esa tarjeta subido desde el corte la saca`() {
        documento("doc_viejo", nu, LocalDate.of(2026, 9, 13))
        assertEquals(1, propuestas(dia15).size, "el del corte anterior no cuenta")
        documento("doc_nuevo", nu, LocalDate.of(2026, 10, 13))
        assertTrue(propuestas(dia15).isEmpty())
    }

    @Test
    fun `un documento que no es un extracto, o de otra cuenta, no cuenta`() {
        documento("doc_recibo", nu, LocalDate.of(2026, 10, 13), tipo = "OTRO")
        documento("doc_otra", "acc_otra", LocalDate.of(2026, 10, 13))
        assertEquals(1, propuestas(dia15).size)
    }

    @Test
    fun `un extracto importado contra la tarjeta la saca`() {
        transaction {
            StatementImports.insert {
                it[id] = "imp_1"; it[userId] = duenoId; it[accountId] = nu; it[bankName] = "Nu"; it[period] = "2026-10"
                it[importedAt] = appDateToEpochMillis(LocalDate.of(2026, 10, 14)); it[importedCount] = 3; it[reconciledCount] = 0
            }
        }
        assertTrue(propuestas(dia15).isEmpty())
    }

    @Test
    fun `ya no saca ese corte, y el siguiente vuelve a pedir`() {
        transaction {
            AnomaliasDescartadas.insert {
                it[userId] = duenoId; it[huella] = huellaDelExtractoPorSubir(nu, "2026-10-12"); it[descartadaEn] = 1L
            }
        }
        assertTrue(propuestas(dia15).isEmpty())
        transaction { compra("ev_nov", nu, duenoId, 10_000L, LocalDate.of(2026, 11, 2)) }
        assertEquals("2026-11-12", propuestas(LocalDate.of(2026, 11, 16)).single().corte)
    }

    @Test
    fun `sin corte, sin uso o en dolares no pide nada`() {
        transaction {
            tarjeta("acc_sin_corte", duenoId, "Sin corte", banco = "Banco", corte = null)
            compra("ev_sin_corte", "acc_sin_corte", duenoId, 1_000L, LocalDate.of(2026, 10, 3))
            tarjeta("acc_guardada", duenoId, "Guardada", banco = "Otro", corte = 12)
            tarjeta("acc_usd", duenoId, "Tarjeta USD", banco = "Nu", corte = 12, moneda = "USD")
            compra("ev_usd", "acc_usd", duenoId, 20L, LocalDate.of(2026, 10, 3))
        }
        assertEquals(listOf(nu), propuestas(dia15).map { it.cuentaId })
    }

    @Test
    fun `la deuda al corte cuenta como uso, sin los anulados`() {
        transaction {
            tarjeta("acc_vieja", duenoId, "Vieja", banco = "Otro", corte = 12)
            compra("ev_vieja", "acc_vieja", duenoId, 300_000L, LocalDate.of(2026, 6, 1))
        }
        assertEquals(setOf(nu, "acc_vieja"), propuestas(dia15).map { it.cuentaId }.toSet())
        transaction { VoidEvents.insert { it[id] = "void_vieja"; it[userId] = duenoId; it[originalEventId] = "ev_vieja"; it[timestamp] = 1L } }
        assertEquals(listOf(nu), propuestas(dia15).map { it.cuentaId })
    }

    @Test
    fun `cada duenio ve solo lo suyo, por la ruta autenticada`() = testApplication {
        application { testModule() }
        val res = client.get("/api/extractos-por-subir") { header(HttpHeaders.Authorization, "Bearer ${token(otroId)}") }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertTrue(json.decodeFromString(ListSerializer(ExtractoPorSubir.serializer()), res.bodyAsText()).isEmpty())
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/extractos-por-subir").status)
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@ext.test")
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
}
