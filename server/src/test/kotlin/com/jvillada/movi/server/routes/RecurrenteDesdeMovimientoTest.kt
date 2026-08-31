package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **«Esto se repite todos los meses», dicho desde un movimiento que YA ocurrió.**
 *
 * El dueño lo pidió así: *«si no marqué algo recurrente pero lo es, poder hacerlo desde el
 * movimiento luego, y que se agregue el recurrente»*.
 *
 * Lo delicado no es crear la regla: es que **ese pago no se cuente dos veces**. El movimiento que
 * origina la regla ya está en «Gastos del mes» y ya pasó; si la regla naciera venciendo en ese
 * mismo período, Movi le preguntaría «¿ya pagaste el arriendo de agosto?» sobre el arriendo de
 * agosto que acaba de anotar, y se lo ofrecería como respuesta a sí mismo.
 *
 * La pieza que lo evita es `recurring_rules.active_from` —la fecha del movimiento— más el rodado
 * que `dueDateFor` ya hacía (`while (!due.isAfter(inicio))`). Estas pruebas fijan las dos mitades
 * **y su contrafactual**: la misma regla sin esa fecha SÍ propone el movimiento, que es lo que
 * demuestra que la guarda es la que trabaja y no una casualidad del calendario.
 */
class RecurrenteDesdeMovimientoTest {

    private val testSecret = "test-secret-for-recurrente-desde-movimiento-32"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val duenoId = "user-dueno-repite"
    private val cuentaId = "acc-repite"

    /** El día del movimiento que origina la regla: hoy, en la zona de la app. */
    private val hoy: LocalDate = AppClock.today()

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:recurrente_desde_movimiento_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                Goals, Subscriptions, CardPaymentDismissals, Cards, Credits, SmsMessages,
                RecurringOccurrences, RecurringRules, VoidEvents, Events, StatementImports,
                Budgets, Accounts, Users, CategoryPrefs,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules,
                RecurringOccurrences, SmsMessages, Credits, Cards, CardPaymentDismissals,
                Subscriptions, Goals, CategoryPrefs,
            )
            Users.insert {
                it[id] = duenoId; it[email] = "dueno@repite.test"; it[name] = duenoId
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = cuentaId; it[userId] = duenoId; it[name] = "Bancolombia"
                it[type] = "SAVINGS"; it[currency] = "COP"
            }
            // El movimiento que origina la regla: el arriendo de este mes, ya pagado.
            gasto("ev-arriendo", "Arriendo", 1_800_000L, hoy)
        }
    }

    private fun gasto(id: String, texto: String, monto: Long, fecha: LocalDate, categoria: String = "Vivienda") {
        Events.insert {
            it[Events.id] = id
            it[userId] = duenoId
            it[accountId] = cuentaId
            it[type] = "EXPENSE"
            it[amount] = monto
            it[currency] = "COP"
            it[category] = categoria
            it[description] = texto
            it[timestamp] = appDateToEpochMillis(fecha)
            it[eventSource] = "MANUAL"
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun token(): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", duenoId).withClaim("email", "dueno@repite.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 86_400_000L))
        .sign(Algorithm.HMAC256(testSecret))

    private fun Application.testModule() {
        configureSerialization()
        val verifier = JWT.require(Algorithm.HMAC256(testSecret))
            .withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { c -> if (c.payload.getClaim("userId").asString() != null) JWTPrincipal(c.payload) else null }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() = application { testModule() }

    /** El cuerpo que manda la hoja prellenada desde el movimiento. */
    private fun cuerpoDeLaRegla(activeFrom: String?, nombre: String = "Arriendo") = buildString {
        append("""{"id":"","name":"$nombre","category":"Vivienda","amount":1800000,""")
        append(""""dayOfMonth":${hoy.dayOfMonth},"type":"EXPENSE","accountId":"$cuentaId"""")
        if (activeFrom != null) append(""","activeFrom":"$activeFrom"""")
        append("}")
    }

    private suspend fun ApplicationTestBuilder.crearRegla(body: String) =
        client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.proximos(): String =
        client.get("/api/payments/upcoming") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.bodyAsText()

    private suspend fun ApplicationTestBuilder.ocurrencias(): String =
        client.get("/api/payments/occurrences") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.bodyAsText()

    private fun activeFromGuardado(): String? = transaction {
        RecurringRules.selectAll().where { RecurringRules.userId eq duenoId }
            .single()[RecurringRules.activeFrom]
    }

    // ── La fecha de arranque se guarda y se devuelve ───────────────────────────

    @Test
    fun `la regla creada desde un movimiento guarda la fecha de ese movimiento`() = testApplication {
        wireApp()
        val res = crearRegla(cuerpoDeLaRegla(hoy.toString()))
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())

        assertEquals(hoy.toString(), activeFromGuardado(), "sin columna, la fecha se perdía en silencio")
        val leidas = client.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }.bodyAsText()
        assertTrue(leidas.contains(hoy.toString()), leidas)
    }

    @Test
    fun `una fecha que no es una fecha se guarda como nula en vez de mentir`() = testApplication {
        wireApp()
        val res = crearRegla(cuerpoDeLaRegla("mañana"))
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        // Guardarla igual habría dejado una regla que dice arrancar en algún lado y que
        // `dueDateFor` ignora: peor que decir «desde siempre», que al menos es verdad.
        assertEquals(null, activeFromGuardado())
    }

    // ── El primer vencimiento cae en el período SIGUIENTE ──────────────────────

    @Test
    fun `el primer vencimiento es el del mes que viene, no el del movimiento`() = testApplication {
        wireApp()
        crearRegla(cuerpoDeLaRegla(hoy.toString()))

        val texto = proximos()
        val vencimiento = Regex("\"dueDate\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").find(texto)!!.groupValues[1]
        assertEquals(
            YearMonth.from(hoy).plusMonths(1),
            YearMonth.from(LocalDate.parse(vencimiento)),
            "el pago que originó la regla ya se hizo: el recordatorio arranca el mes que viene ($texto)",
        )
    }

    /**
     * El contrafactual. Sin la fecha de arranque, la MISMA regla vence este mes — o sea que lo que
     * separa «un recordatorio útil» de «que te pregunten por algo que acabas de pagar» es
     * exactamente `activeFrom`, y no una casualidad del calendario.
     */
    @Test
    fun `sin fecha de arranque la misma regla vence este mes`() = testApplication {
        wireApp()
        crearRegla(cuerpoDeLaRegla(null))

        val texto = proximos()
        val vencimiento = Regex("\"dueDate\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").find(texto)!!.groupValues[1]
        assertEquals(YearMonth.from(hoy), YearMonth.from(LocalDate.parse(vencimiento)), texto)
    }

    // ── Y el movimiento no queda ofrecido como ocurrencia pendiente ────────────

    @Test
    fun `el movimiento que origino la regla no se ofrece como su ocurrencia de este mes`() = testApplication {
        wireApp()
        crearRegla(cuerpoDeLaRegla(hoy.toString()))

        val texto = ocurrencias()
        assertFalse(
            texto.contains("ev-arriendo"),
            "no se le puede ofrecer al dueño cerrar el mes con el pago que ORIGINÓ la regla: $texto",
        )
        // Y la regla directamente no tiene ocurrencia este mes: todavía no arrancó.
        assertEquals("[]", texto.replace(Regex("\\s"), ""), texto)
    }

    /** El contrafactual del anterior: sin `activeFrom`, ese mismo movimiento SÍ se propone. */
    @Test
    fun `sin fecha de arranque el mismo movimiento si se propone como ocurrencia`() = testApplication {
        wireApp()
        crearRegla(cuerpoDeLaRegla(null))

        val texto = ocurrencias()
        assertTrue(texto.contains("ev-arriendo"), texto)
    }

    // ── Editar la regla no puede borrarle la fecha de arranque ─────────────────

    @Test
    fun `un PUT que no habla de la fecha de arranque la conserva`() = testApplication {
        wireApp()
        crearRegla(cuerpoDeLaRegla(hoy.toString()))
        val ruleId = transaction {
            RecurringRules.selectAll().where { RecurringRules.userId eq duenoId }.single()[RecurringRules.id]
        }

        // Un cliente que solo corrige el monto: su body no trae `activeFrom`, igual que el APK
        // instalado. Sin la regla de «null = no lo toques», el vencimiento volvería al mes que
        // ya estaba pagado.
        val res = client.put("/api/recurring-rules/$ruleId") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"$ruleId","name":"Arriendo","category":"Vivienda","amount":1900000,
                   "dayOfMonth":${hoy.dayOfMonth},"type":"EXPENSE"}""".trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertEquals(hoy.toString(), activeFromGuardado())

        val texto = proximos()
        val vencimiento = Regex("\"dueDate\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").find(texto)!!.groupValues[1]
        assertEquals(YearMonth.from(hoy).plusMonths(1), YearMonth.from(LocalDate.parse(vencimiento)), texto)
    }

    // ── Y el detector no fabrica una segunda copia de lo que ya es una regla ───

    @Test
    fun `el detector no crea una suscripcion que ya existe como regla`() = testApplication {
        // Dos cargos de YouTube en meses seguidos: la heurística entera se cumple y sin la
        // guarda esto crea la suscripción «YouTube». Con la regla del mismo nombre ya anotada,
        // dos filas para el mismo cobro le duplicarían el gasto en «Gastos recurrentes».
        transaction {
            gasto("ev-yt-1", "Google YOUTUBE Mmbrshp", 26_900L, hoy.minusMonths(2), "Entretenimiento")
            gasto("ev-yt-2", "Google YOUTUBE Mmbrshp", 26_900L, hoy.minusMonths(1), "Entretenimiento")
        }
        wireApp()
        crearRegla(cuerpoDeLaRegla(hoy.toString(), nombre = "YouTube"))

        val res = client.post("/api/subscriptions/detect") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())

        val filas = transaction {
            Subscriptions.selectAll().where { Subscriptions.userId eq duenoId }
                .map { it[Subscriptions.displayName] }
        }
        assertFalse(filas.any { it.equals("YouTube", ignoreCase = true) }, "quedaron: $filas")
    }

    /** El contrafactual: sin la regla, el mismo barrido SÍ la descubre. */
    @Test
    fun `sin la regla, el detector si descubre la suscripcion`() = testApplication {
        transaction {
            gasto("ev-yt-1", "Google YOUTUBE Mmbrshp", 26_900L, hoy.minusMonths(2), "Entretenimiento")
            gasto("ev-yt-2", "Google YOUTUBE Mmbrshp", 26_900L, hoy.minusMonths(1), "Entretenimiento")
        }
        wireApp()

        val res = client.post("/api/subscriptions/detect") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())

        val filas = transaction {
            Subscriptions.selectAll().where { Subscriptions.userId eq duenoId }
                .map { it[Subscriptions.displayName] }
        }
        assertTrue(filas.any { it.equals("YouTube", ignoreCase = true) }, "quedaron: $filas")
    }
}
