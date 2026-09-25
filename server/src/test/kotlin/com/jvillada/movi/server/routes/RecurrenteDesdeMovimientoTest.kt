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
import com.jvillada.movi.server.db.OccurrenceRejections
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
 * Lo delicado no es crear la regla: es que Movi no le **vuelva a preguntar** por ese pago. El
 * movimiento que origina la regla ya pasó y ya está en «Gastos del mes»; si la regla naciera
 * venciendo abierta en ese mismo período, Movi le preguntaría «¿ya pagaste el arriendo de agosto?»
 * sobre el arriendo que acaba de anotar, y se lo ofrecería como respuesta a sí mismo.
 *
 * **Eso se cierra marcándolo, no escondiéndolo.** El alta manda el id del movimiento
 * (`eventoDeOrigen`) y el server sella con él el período de ese movimiento, por el mismo camino y
 * con las mismas guardas que «Ya lo pagué». El período se ve, tildado y con su evidencia al lado,
 * y `dueDateFor` rueda al siguiente porque el período está sellado — no porque no exista.
 *
 * `recurring_rules.active_from` sigue puesto y sigue siendo necesario, pero para lo otro: marca el
 * piso hacia atrás (nada de cuotas de julio). Antes se comía además el período del propio
 * movimiento, y eso borraba del checklist reglas reales del dueño; ver `PrimeraCuotaTest`.
 *
 * Estas pruebas fijan las dos mitades **y sus contrafactuales**: sin la fecha y sin el id, la
 * misma regla vence abierta este mes y propone el movimiento — que es lo que demuestra que trabajan
 * las guardas y no una casualidad del calendario.
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
                RecurringOccurrences, OccurrenceRejections, RecurringRules, VoidEvents, Events, StatementImports,
                Budgets, Accounts, Users, CategoryPrefs,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules,
                RecurringOccurrences, OccurrenceRejections, SmsMessages, Credits, Cards, CardPaymentDismissals,
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

    /**
     * El cuerpo que manda la hoja prellenada desde el movimiento: la fecha de ESE movimiento y,
     * desde esta ola, también su id — los dos salen del mismo `RecurringPrefill`.
     */
    private fun cuerpoDeLaRegla(
        activeFrom: String?,
        nombre: String = "Arriendo",
        eventoDeOrigen: String? = null,
    ) = buildString {
        append("""{"id":"","name":"$nombre","category":"Vivienda","amount":1800000,""")
        append(""""dayOfMonth":${hoy.dayOfMonth},"type":"EXPENSE","accountId":"$cuentaId"""")
        if (activeFrom != null) append(""","activeFrom":"$activeFrom"""")
        if (eventoDeOrigen != null) append(""","eventoDeOrigen":"$eventoDeOrigen"""")
        append("}")
    }

    /** Lo que manda de verdad la hoja: la fecha del movimiento Y su id. */
    private fun cuerpoDesdeElMovimiento(nombre: String = "Arriendo", eventId: String = "ev-arriendo") =
        cuerpoDeLaRegla(hoy.toString(), nombre = nombre, eventoDeOrigen = eventId)

    private fun sellosGuardados(): List<Pair<String, String?>> = transaction {
        RecurringOccurrences.selectAll()
            .where { RecurringOccurrences.userId eq duenoId }
            .map { it[RecurringOccurrences.period] to it[RecurringOccurrences.eventId] }
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
        crearRegla(cuerpoDesdeElMovimiento())

        val texto = proximos()
        val vencimiento = Regex("\"dueDate\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").find(texto)!!.groupValues[1]
        assertEquals(
            YearMonth.from(hoy).plusMonths(1),
            YearMonth.from(LocalDate.parse(vencimiento)),
            "el pago que originó la regla ya se hizo y quedó sellado: el recordatorio arranca el mes que viene ($texto)",
        )
    }

    // ── El movimiento que la originó queda como su EVIDENCIA ───────────────────

    @Test
    fun `el alta desde un movimiento sella el periodo de ese movimiento con ese movimiento`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, crearRegla(cuerpoDesdeElMovimiento()).status)

        assertEquals(
            listOf(YearMonth.from(hoy).toString() to "ev-arriendo"),
            sellosGuardados(),
            "el período del movimiento tiene que quedar cerrado, y con el movimiento como prueba",
        )
    }

    @Test
    fun `sin el id del movimiento no se sella nada`() = testApplication {
        wireApp()
        crearRegla(cuerpoDeLaRegla(hoy.toString()))

        assertEquals(emptyList(), sellosGuardados(), "el sello sale del id que manda el cliente, de nada más")
    }

    /**
     * **Un movimiento que ya usa otra regla no puede sellar esta.** Es una de las guardas de
     * `POST /api/recurring-rules/{id}/occurrence`, y vale acá porque el sellado pasa por ahí: una
     * sola entrada de plata cerrando dos recurrentes es «marcar de más», que es lo caro.
     *
     * Y la regla **se crea igual**: perder el alta por no poder poner un sello sería perder lo que
     * el dueño pidió.
     */
    @Test
    fun `un movimiento ya usado por otra regla no sella, pero la regla se crea`() = testApplication {
        wireApp()
        crearRegla(cuerpoDesdeElMovimiento(nombre = "Arriendo"))
        val sellosDeLaPrimera = sellosGuardados()

        val res = crearRegla(cuerpoDesdeElMovimiento(nombre = "Administración"))

        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        assertEquals(2, transaction {
            RecurringRules.selectAll().where { RecurringRules.userId eq duenoId }.count().toInt()
        })
        assertEquals(sellosDeLaPrimera, sellosGuardados(), "el movimiento ya estaba usado: no se sella de nuevo")
    }

    @Test
    fun `un id de movimiento inventado no rompe el alta`() = testApplication {
        wireApp()
        val res = crearRegla(cuerpoDeLaRegla(hoy.toString(), eventoDeOrigen = "no-existe"))

        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        assertEquals(emptyList(), sellosGuardados())
    }

    /**
     * El contrafactual. Sin la fecha de arranque, la MISMA regla vence este mes — o sea que lo que
     * separa «un recordatorio útil» de «que te pregunten por algo que acabas de pagar» es
     * exactamente `activeFrom`, y no una casualidad del calendario.
     *
     * El movimiento se rechaza antes de mirar: «Arriendo» contra «Arriendo» es un emparejamiento
     * concluyente, y lo que Movi empareja solo también rueda «Próximos» (el checklist lo da por
     * pagado, y las dos pantallas tienen que decir lo mismo — ver `ProximosVeLoEmparejadoTest`).
     * Sin sello, sin fecha de arranque y sin emparejamiento, queda lo que esta prueba quiere ver.
     */
    @Test
    fun `sin fecha de arranque la misma regla vence este mes`() = testApplication {
        wireApp()
        crearRegla(cuerpoDeLaRegla(null))
        val ruleId = transaction {
            RecurringRules.selectAll().where { RecurringRules.userId eq duenoId }.single()[RecurringRules.id]
        }
        val rechazo = client.post("/api/recurring-rules/$ruleId/occurrence/rechazo") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody("""{"eventId":"ev-arriendo"}""")
        }
        assertEquals(HttpStatusCode.NoContent, rechazo.status, rechazo.bodyAsText())

        val texto = proximos()
        val vencimiento = Regex("\"dueDate\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"").find(texto)!!.groupValues[1]
        assertEquals(YearMonth.from(hoy), YearMonth.from(LocalDate.parse(vencimiento)), texto)
    }

    // ── Y el movimiento no queda ofrecido como ocurrencia pendiente ────────────

    /**
     * El movimiento que originó la regla **no se ofrece como pregunta: se muestra como respuesta**.
     *
     * Antes este período no existía —`activeFrom` lo escondía— y la prueba fijaba que la respuesta
     * viniera vacía. Ahora el período está ahí, cerrado, con el movimiento como evidencia: es lo
     * que el dueño esperaba ver en el checklist y lo que no veía.
     */
    @Test
    fun `el movimiento que origino la regla queda como su evidencia, no como una pregunta`() = testApplication {
        wireApp()
        crearRegla(cuerpoDesdeElMovimiento())

        val texto = ocurrencias()
        // El `ContentNegotiation` del server sale con `prettyPrint`: se compara sin espacios.
        val apretado = texto.replace(Regex("\\s"), "")
        assertTrue(apretado.contains(""""occurred":true"""), texto)
        assertTrue(apretado.contains(""""eventId":"ev-arriendo""""), texto)
        assertFalse(
            apretado.contains(""""candidates""""),
            "no se le puede PREGUNTAR al dueño por el pago que ORIGINÓ la regla: $texto",
        )
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
        crearRegla(cuerpoDesdeElMovimiento())
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
