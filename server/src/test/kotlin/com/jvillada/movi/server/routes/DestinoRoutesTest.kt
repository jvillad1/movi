package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
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
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.long
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **`/api/destinos` — las cuentas de otros**, a nivel HTTP.
 *
 * Mismo andamio que `GoalRoutesTest`: H2 en memoria con compatibilidad Postgres, un secreto de JWT
 * local al test y la cadena completa de plugins.
 *
 * Lo que se prueba, y por qué cada cosa:
 *
 * - **El aislamiento por usuario.** Un destino de B es invisible para A, y no «prohibido»: 404,
 *   como en todo este server.
 * - **El rechazo del número propio** (422). Es la guarda que evita que Movi le ponga el nombre de
 *   otra persona a un movimiento suyo, y el mensaje tiene que nombrar la cuenta que chocó.
 * - **Que un SMS que nombra un número registrado se proponga con el nombre del destino.** Es la
 *   otra mitad de la feature, y se prueba por la ruta de verdad (`/api/sms/{id}/parse`), no
 *   llamando a la función pura — que ya tiene sus propias pruebas en `:core`.
 */
class DestinoRoutesTest {

    private val testSecret = "test-secret-for-destino-routes-tests-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"

    private val userAId = "user-a-destinos"
    private val userBId = "user-b-destinos"
    private val userAEmail = "a@destinos.test"
    private val userBEmail = "b@destinos.test"

    /** La cuenta de la que sale la plata de A. Lleva el 8133 en el nombre, como las reales. */
    private val cuentaDeA = "acc-bancolombia-a"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:destino_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                KnownDestinations, Goals, Credits, SmsMessages, RecurringRules, VoidEvents, Events,
                StatementImports, Budgets, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, StatementImports, Events, VoidEvents,
                Budgets, RecurringRules, SmsMessages, Credits, Goals, KnownDestinations,
            )
            Users.insert {
                it[id] = userAId; it[email] = userAEmail; it[name] = "User A"; it[passwordHash] = "hash-a"
            }
            Users.insert {
                it[id] = userBId; it[email] = userBEmail; it[name] = "User B"; it[passwordHash] = "hash-b"
            }
            Accounts.insert {
                it[id] = cuentaDeA
                it[userId] = userAId
                it[name] = "Bancolombia Ahorros 8133"
                it[type] = "SAVINGS"
                it[currency] = "COP"
            }
        }
    }

    private fun tokenFor(userId: String): String {
        val algorithm = Algorithm.HMAC256(testSecret)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", if (userId == userAId) userAEmail else userBEmail)
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(algorithm)
    }

    private fun Application.testModule() {
        configureSerialization()
        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()
        authentication {
            jwt("jwt") {
                this.verifier(verifier)
                validate { credential ->
                    if (credential.payload.getClaim("userId").asString() != null) JWTPrincipal(credential.payload)
                    else null
                }
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.wireApp() = application { testModule() }

    private val caroJson = """{"nombre":"Caro","numero":"*31973270756","deQuien":"esposa"}"""

    private suspend fun ApplicationTestBuilder.crearCaro(uid: String = userAId) =
        client.post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(uid)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(caroJson)
        }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Test
    fun `el alta normaliza el numero a solo digitos y GET lo devuelve`() = testApplication {
        wireApp()
        val post = crearCaro()
        assertEquals(HttpStatusCode.Created, post.status)
        val creado = Json.parseToJsonElement(post.bodyAsText()).jsonObject
        assertEquals("31973270756", creado["numero"]!!.jsonPrimitive.content, "el asterisco no se guarda")
        assertTrue(creado["id"]!!.jsonPrimitive.content.startsWith("dst_"))

        val res = client.get("/api/destinos") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val arr = Json.parseToJsonElement(res.bodyAsText()).jsonArray
        assertEquals(1, arr.size)
        assertEquals("Caro", arr[0].jsonObject["nombre"]!!.jsonPrimitive.content)
        assertEquals("esposa", arr[0].jsonObject["deQuien"]!!.jsonPrimitive.content)
        // `cuantos` y `totales` son derivados y valen su default cuando no hay envíos, así que la
        // serialización los omite del JSON — que es exactamente lo correcto: un cliente los lee de
        // vuelta como 0 y vacío. Se lee con `?:` y no con `!!` por eso.
        assertEquals(0, arr[0].jsonObject["cuantos"]?.jsonPrimitive?.int ?: 0, "sin movimientos todavía")
    }

    @Test
    fun `renombrar y borrar funcionan`() = testApplication {
        wireApp()
        val id = Json.parseToJsonElement(crearCaro().bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

        val put = client.put("/api/destinos/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Caro Restrepo","numero":"31973270756","deQuien":"esposa"}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)
        assertEquals(
            "Caro Restrepo",
            Json.parseToJsonElement(put.bodyAsText()).jsonObject["nombre"]!!.jsonPrimitive.content,
        )

        val del = client.delete("/api/destinos/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(HttpStatusCode.NoContent, del.status)
        val res = client.get("/api/destinos") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertTrue(Json.parseToJsonElement(res.bodyAsText()).jsonArray.isEmpty())
    }

    /**
     * Ola V — **borrar un destino suelta las reglas recurrentes que lo tenían asociado**, no las
     * borra: «Tía Caro, día 1, $100.000» sigue siendo un plan real aunque el dueño borre el
     * registro de «Caro» (ver el KDoc de `RecurringRule.destinoConocidoId`). Se verifica leyendo
     * de vuelta con `GET /api/recurring-rules`, el mismo camino que usa la hoja de edición.
     */
    @Test
    fun `borrar un destino suelta las reglas recurrentes que lo tenian asociado`() = testApplication {
        wireApp()
        val tipado = createClient { install(ContentNegotiation) { json() } }
        val id = Json.parseToJsonElement(crearCaro().bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

        val reglaCreada = tipado.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            contentType(ContentType.Application.Json)
            setBody(
                RecurringRule(
                    "ignored", "Tía Caro", "Familia", 100_000, 1,
                    TransactionType.EXPENSE, destinoConocidoId = id,
                ),
            )
        }.body<RecurringRule>()
        assertEquals(id, reglaCreada.destinoConocidoId, "la regla guardó el destino antes de borrarlo")

        val del = client.delete("/api/destinos/$id") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertEquals(HttpStatusCode.NoContent, del.status)

        val reglas = tipado.get("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }.body<List<RecurringRule>>()
        val reglaLeida = reglas.single { it.id == reglaCreada.id }
        // La regla SIGUE existiendo — solo se soltó la referencia, no queda apuntando a un id que
        // ya no existe.
        assertEquals(null, reglaLeida.destinoConocidoId)
    }

    @Test
    fun `un destino sin nombre o sin cuatro digitos se rechaza con el motivo`() = testApplication {
        wireApp()
        val sinNombre = client.post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"   ","numero":"31973270756"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, sinNombre.status)
        assertTrue(sinNombre.bodyAsText().contains("nombre"))

        val corto = client.post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Caro","numero":"756"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, corto.status)
    }

    // ── Aislamiento ───────────────────────────────────────────────────────────

    /**
     * El destino de B **no existe** para A: no se lista, no se puede leer, no se puede editar y no
     * se puede borrar. Y el código es 404 y no 403, como en todo este server: decir «no puedes»
     * confirmaría que el recurso existe.
     */
    @Test
    fun `el destino de otro usuario es invisible`() = testApplication {
        wireApp()
        val idDeB = Json.parseToJsonElement(crearCaro(userBId).bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content

        val listaDeA = client.get("/api/destinos") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        assertTrue(
            Json.parseToJsonElement(listaDeA.bodyAsText()).jsonArray.isEmpty(),
            "A no puede ver la cuenta de otro que registró B",
        )

        val movimientos = client.get("/api/destinos/$idDeB/movimientos") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.NotFound, movimientos.status)

        val put = client.put("/api/destinos/$idDeB") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Mío ahora","numero":"31973270756"}""")
        }
        assertEquals(HttpStatusCode.NotFound, put.status)

        val del = client.delete("/api/destinos/$idDeB") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.NotFound, del.status)

        // Y sigue siendo de B, intacto.
        val listaDeB = client.get("/api/destinos") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userBId)}") }
        val deB = Json.parseToJsonElement(listaDeB.bodyAsText()).jsonArray
        assertEquals(1, deB.size)
        assertEquals("Caro", deB[0].jsonObject["nombre"]!!.jsonPrimitive.content)
    }

    // ── La guarda del número propio ───────────────────────────────────────────

    /**
     * **Un número que es de una cuenta suya no se registra como ajeno.** Si entrara, un SMS de un
     * retiro de su propia cuenta se propondría con el nombre de otra persona y después se contaría
     * en «lo que le mandé». El mensaje nombra la cuenta que chocó, para que se entienda el porqué.
     */
    @Test
    fun `el numero de una cuenta suya se rechaza nombrando la cuenta`() = testApplication {
        wireApp()
        val res = client.post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Yo mismo","numero":"*8133"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(
            res.bodyAsText().contains("Bancolombia Ahorros 8133"),
            "el rechazo tiene que decir CUÁL cuenta chocó: «${res.bodyAsText()}»",
        )
    }

    /** Y tampoco por la puerta de la edición: es la misma guarda en las dos rutas. */
    @Test
    fun `tampoco se puede editar un destino hacia el numero de una cuenta suya`() = testApplication {
        wireApp()
        val id = Json.parseToJsonElement(crearCaro().bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
        val res = client.put("/api/destinos/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Caro","numero":"43087518133"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
    }

    /**
     * Dos destinos con la misma cola de cuatro dígitos serían un empate que `destinoQueNombra` no
     * resuelve: los dos se llevarían los mismos movimientos y ninguno nombraría nada.
     */
    @Test
    fun `dos destinos no pueden compartir los ultimos cuatro digitos`() = testApplication {
        wireApp()
        crearCaro()
        val res = client.post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Vecino","numero":"99990756"}""")
        }
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertTrue(res.bodyAsText().contains("Caro"), "el rechazo dice con quién choca")

        // Pero guardar el MISMO destino sin cambiarle el número no choca consigo mismo.
        val id = Json.parseToJsonElement(
            client.get("/api/destinos") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText(),
        ).jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content
        val put = client.put("/api/destinos/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Caro","numero":"31973270756","deQuien":"esposa"}""")
        }
        assertEquals(HttpStatusCode.OK, put.status)
    }

    // ── El total de lo que se le mandó ────────────────────────────────────────

    private fun anotarGasto(
        id: String,
        descripcion: String,
        monto: Long,
        crudo: String?,
        cuando: Long = System.currentTimeMillis(),
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = userAId
            it[accountId] = cuentaDeA
            it[type] = "EXPENSE"
            it[amount] = monto
            it[currency] = "COP"
            it[category] = "Otros"
            it[Events.description] = descripcion
            it[merchant] = descripcion
            it[timestamp] = cuando
            it[eventSource] = "SMS"
            it[rawPayload] = crudo
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    /**
     * **Los envíos reales del dueño cuentan aunque él les haya cambiado el nombre**, porque el
     * texto del banco viaja con el movimiento. Es el caso que decidió el diseño de la feature.
     */
    @Test
    fun `el GET suma lo enviado, y los movimientos renombrados siguen contando`() = testApplication {
        wireApp()
        crearCaro()
        val dia = 86_400_000L
        val ahora = System.currentTimeMillis()
        anotarGasto(
            "ev-mercado", "Mercado", 2_000_000L,
            "Transferiste \$2.000.000 a la cuenta *31973270756 desde tu cuenta *8133",
            cuando = ahora - 3 * dia,
        )
        anotarGasto(
            "ev-colegio", "Colegio Hija · parte desde Bancolombia", 1_000_000L,
            "Transferiste \$1.000.000 a la cuenta *31973270756 desde tu cuenta *8133",
            cuando = ahora - 2 * dia,
        )
        // Anotado a mano: sin texto del banco, lo engancha el nombre en el concepto.
        anotarGasto("ev-cotrafa", "Cuota de Cotrafa 5413 · transferida a Caro", 1_931_488L, null, cuando = ahora - dia)
        // Y algo que no tiene nada que ver, para que el total no sea «todos los gastos».
        anotarGasto("ev-uber", "Uber", 28_500L, "Compra aprobada \$28.500 en Uber BV.")

        val res = client.get("/api/destinos") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val destino = Json.parseToJsonElement(res.bodyAsText()).jsonArray[0].jsonObject
        assertEquals(3, destino["cuantos"]!!.jsonPrimitive.int)
        assertEquals(
            4_931_488L,
            destino["totales"]!!.jsonObject["COP"]!!.jsonPrimitive.long,
            "los tres envíos, y NO el Uber",
        )
        // 30-sep: y el último envío, para la ficha — el más reciente de los tres, no el Uber.
        val ultimo = destino["ultimo"]!!.jsonObject
        assertEquals("Cuota de Cotrafa 5413 · transferida a Caro", ultimo["descripcion"]!!.jsonPrimitive.content)
        assertEquals(1_931_488L, ultimo["monto"]!!.jsonPrimitive.long)

        val id = destino["id"]!!.jsonPrimitive.content
        val detalle = client.get("/api/destinos/$id/movimientos") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, detalle.status)
        val cuerpo = Json.parseToJsonElement(detalle.bodyAsText()).jsonObject
        val ids = cuerpo["movimientos"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }
        assertEquals(setOf("ev-mercado", "ev-colegio", "ev-cotrafa"), ids.toSet())
    }

    // ── El nombre que se le propone a un SMS ──────────────────────────────────

    private fun guardarSms(id: String, texto: String) = transaction {
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = userAId
            it[time] = "2026-09-18 10:30"
            it[bank] = "87400"
            it[text] = texto
            it[state] = "pending"
            it[det] = ""
        }
    }

    /**
     * **La otra mitad de la feature, por la ruta de verdad.** Sin un destino registrado, el mensaje
     * se propone con el número crudo —ilegible—; con él registrado, con el nombre. Se prueban los
     * dos lados en el mismo test a propósito: si solo se probara el segundo, un cambio que hiciera
     * que TODO se llame «Transferencia a Caro» pasaría en verde.
     */
    @Test
    fun `un SMS que nombra un numero registrado se propone con el nombre del destino`() = testApplication {
        wireApp()
        guardarSms(
            "sms-cotrafa",
            "Bancolombia: Transferiste \$1.931.488 a la cuenta *31973270756 desde tu cuenta *8133 el 18/09/26",
        )

        val sinRegistrar = client.get("/api/sms/sms-cotrafa/parse") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(HttpStatusCode.OK, sinRegistrar.status)
        assertEquals(
            "Transferencia a la cuenta *31973270756",
            Json.parseToJsonElement(sinRegistrar.bodyAsText()).jsonObject["merchant"]!!.jsonPrimitive.content,
        )

        crearCaro()

        val conDestino = client.get("/api/sms/sms-cotrafa/parse") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(
            "Transferencia a Caro",
            Json.parseToJsonElement(conDestino.bodyAsText()).jsonObject["merchant"]!!.jsonPrimitive.content,
        )
    }

    /**
     * **Y un destino de OTRO usuario no le pone nombre a un mensaje suyo.** El mismo aislamiento del
     * CRUD, pero por la puerta donde más se notaría: el nombre de una persona ajena apareciendo en
     * un movimiento propio.
     */
    @Test
    fun `el destino de otro usuario no nombra un SMS mio`() = testApplication {
        wireApp()
        guardarSms("sms-mio", "Bancolombia: Transferiste \$50.000 a la cuenta *31973270756 desde tu cuenta *8133")
        crearCaro(userBId)

        val res = client.get("/api/sms/sms-mio/parse") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
        }
        assertEquals(
            "Transferencia a la cuenta *31973270756",
            Json.parseToJsonElement(res.bodyAsText()).jsonObject["merchant"]!!.jsonPrimitive.content,
        )
    }

    // ── Llaves (29-sep) ───────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.postDestino(json: String, uid: String = userAId) =
        client.post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(uid)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody(json)
        }

    private suspend fun ApplicationTestBuilder.listar() =
        Json.parseToJsonElement(
            client.get("/api/destinos") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText(),
        ).jsonArray

    /**
     * **Un destino que solo se conoce por su llave** se crea (número vacío), se lista, empareja sus
     * envíos y le pone nombre al SMS que la nombra. Es el caso de Nu y del pago por QR de
     * Bancolombia, que no escriben ningún número de cuenta.
     */
    @Test
    fun `un destino con llave se crea, se lista, empareja y nombra el SMS`() = testApplication {
        wireApp()
        val post = postDestino("""{"nombre":"Panadería","numero":"","llave":" 0092184713 "}""")
        assertEquals(HttpStatusCode.Created, post.status, post.bodyAsText())
        val creado = Json.parseToJsonElement(post.bodyAsText()).jsonObject
        assertEquals("0092184713", creado["llave"]!!.jsonPrimitive.content, "sin espacios")
        assertEquals("", creado["numero"]!!.jsonPrimitive.content)

        anotarGasto(
            "ev-qr", "Pan del domingo", 18_500L,
            "Bancolombia: pagaste \$18,500.00 por codigo QR desde tu cuenta *8133 a la llave 0092184713 el 09/09/2026",
        )
        // Una cuenta que TERMINA en 4713: con la llave comparada exacta no se confunde.
        anotarGasto("ev-otra", "Transferencia", 10_000L, "Transferiste \$10.000 a la cuenta *55554713 desde tu cuenta *8133")

        val lista = listar()
        assertEquals(1, lista.size)
        assertEquals("0092184713", lista[0].jsonObject["llave"]!!.jsonPrimitive.content)
        assertEquals(1, lista[0].jsonObject["cuantos"]!!.jsonPrimitive.int, "el pago por QR y no la cuenta que termina igual")
        assertEquals(18_500L, lista[0].jsonObject["totales"]!!.jsonObject["COP"]!!.jsonPrimitive.long)
        // Es de este período (se anotó ahora), así que la tarjeta de Patrimonio lo ve.
        assertEquals(18_500L, lista[0].jsonObject["totalesDelPeriodo"]!!.jsonObject["COP"]!!.jsonPrimitive.long)

        guardarSms("sms-qr", "Bancolombia: pagaste \$18,500.00 por codigo QR desde tu cuenta *8133 a la llave 0092184713 el 09/09/2026")
        val parse = client.get("/api/sms/sms-qr/parse") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }
        val leido = Json.parseToJsonElement(parse.bodyAsText()).jsonObject
        assertEquals("Transferencia a Panadería", leido["merchant"]!!.jsonPrimitive.content)
        assertEquals("0092184713", leido["identificadorDelDestino"]!!.jsonPrimitive.content)
        assertEquals(true, leido["identificadorEsLlave"]!!.jsonPrimitive.content.toBoolean())
    }

    private suspend fun ApplicationTestBuilder.leer(id: String) = Json.parseToJsonElement(
        client.get("/api/sms/$id/parse") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText(),
    ).jsonObject

    /**
     * **Punto 5 (30-sep): Movimientos y Hoy dicen «Transferencia a Caro» también con llaves.** El
     * nombre de un movimiento se decide al leer el aviso (`/parse` → `conElDestinoConocido`) y es
     * el que queda guardado y se pinta en Movimientos y Hoy. Con la llave en `@usuario` y con un
     * correo, lo que el banco escribe es la llave sola, así que se reemplaza por el nombre.
     */
    @Test
    fun `con una llave arroba o un correo el aviso se propone con el nombre del destino`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, postDestino("""{"nombre":"Caro","numero":"","llave":"@caro"}""").status)
        assertEquals(HttpStatusCode.Created, postDestino("""{"nombre":"Papá","numero":"","llave":"papa@correo.com"}""").status)

        guardarSms("sms-arroba", "Bancolombia: pagaste \$50,000.00 por codigo QR desde tu cuenta *8133 a la llave @caro el 29/09/2026 a las 10:02.")
        val arroba = leer("sms-arroba")
        assertEquals("Transferencia a Caro", arroba["merchant"]!!.jsonPrimitive.content)
        assertEquals("@caro", arroba["identificadorDelDestino"]!!.jsonPrimitive.content)

        guardarSms("sms-correo", "Bancolombia: pagaste \$80,000.00 por codigo QR desde tu cuenta *8133 a la llave papa@correo.com el 29/09/2026.")
        assertEquals("Transferencia a Papá", leer("sms-correo")["merchant"]!!.jsonPrimitive.content)
    }

    /**
     * **Cuando el banco trae el nombre real, ese nombre manda** — decisión documentada en
     * `sePuedeRenombrar` (`:core`): «a la llave @pedro … a PEDRO GOMEZ» sigue diciendo «PEDRO
     * GOMEZ», no «Transferencia a Pedro». Lo que SÍ cambia con la llave guardada: el envío cuenta en
     * «Cuentas de otros» (lo engancha la llave del texto del banco), y Reconciliar ya no ofrece
     * guardarla porque el identificador es conocido.
     */
    @Test
    fun `con el nombre real del banco no se renombra, pero el envio cuenta para el destino`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, postDestino("""{"nombre":"Pedro","numero":"","llave":"@pedro"}""").status)
        val texto = "Bancolombia: ANA, transferiste \$25,910.00 a la llave @pedro desde tu cuenta *8133 a PEDRO GOMEZ el 10/09/26 a las 08:50."
        guardarSms("sms-pedro", texto)
        val leido = leer("sms-pedro")
        assertEquals("PEDRO GOMEZ", leido["merchant"]!!.jsonPrimitive.content)
        assertEquals("@pedro", leido["identificadorDelDestino"]!!.jsonPrimitive.content)

        // Confirmado con el nombre del banco, cuenta igual: el texto crudo viaja con el movimiento.
        anotarGasto("ev-pedro", "PEDRO GOMEZ", 25_910L, texto)
        val lista = listar()
        assertEquals(1, lista[0].jsonObject["cuantos"]!!.jsonPrimitive.int)
        assertEquals("PEDRO GOMEZ", lista[0].jsonObject["ultimo"]!!.jsonObject["descripcion"]!!.jsonPrimitive.content)
    }

    /**
     * **La plata que llega de alguien guardado** también se nombra: Nu dice «Te llegó dinero de
     * CAROLINA RESTREPO SALAZAR con tu llave», y si ese nombre está guardado en Caro, se propone
     * «Transferencia de Caro».
     */
    @Test
    fun `lo que llega de un remitente guardado se propone con su nombre`() = testApplication {
        wireApp()
        val texto = "Recibiste 300.000,00 en tu cuenta: Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave."
        transaction {
            SmsMessages.insert {
                it[SmsMessages.id] = "sms-nu"
                it[userId] = userAId
                it[time] = "2026-09-20 09:00"
                it[bank] = "Notificación · Nu"
                it[SmsMessages.text] = texto
                it[state] = "pending"
                it[det] = ""
            }
        }
        val antes = Json.parseToJsonElement(
            client.get("/api/sms/sms-nu/parse") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText(),
        ).jsonObject
        assertEquals("carolina restrepo salazar", antes["identificadorDelDestino"]!!.jsonPrimitive.content)
        assertEquals("CAROLINA RESTREPO SALAZAR", antes["merchant"]!!.jsonPrimitive.content, "sin guardar, el nombre del banco")

        assertEquals(
            HttpStatusCode.Created,
            postDestino("""{"nombre":"Caro","numero":"31973270756","llave":"carolina restrepo salazar"}""").status,
        )
        val despues = Json.parseToJsonElement(
            client.get("/api/sms/sms-nu/parse") { header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}") }.bodyAsText(),
        ).jsonObject
        assertEquals("Transferencia de Caro", despues["merchant"]!!.jsonPrimitive.content)
    }

    /**
     * **El APK instalado no sabe de llaves**: su cuerpo no trae el campo. El alta sigue funcionando
     * como siempre, y al editar desde ese APK la llave que se guardó desde la web **no se pierde**.
     */
    @Test
    fun `un cliente viejo sin los campos nuevos sigue funcionando y no borra la llave`() = testApplication {
        wireApp()
        // El cuerpo exacto que manda el APK 1.60: sin `llave`, sin `totalesDelPeriodo`.
        assertEquals(HttpStatusCode.Created, crearCaro().status)
        val id = listar()[0].jsonObject["id"]!!.jsonPrimitive.content

        // La web le agrega la llave…
        val conLlave = client.put("/api/destinos/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Caro","numero":"31973270756","deQuien":"esposa","llave":"@CaroR"}""")
        }
        assertEquals(HttpStatusCode.OK, conLlave.status)
        assertEquals("@caror", Json.parseToJsonElement(conLlave.bodyAsText()).jsonObject["llave"]!!.jsonPrimitive.content)

        // …y el APK viejo la renombra, sin saber que existe.
        val viejo = client.put("/api/destinos/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Caro Restrepo","numero":"31973270756","deQuien":"esposa"}""")
        }
        assertEquals(HttpStatusCode.OK, viejo.status)
        val guardado = listar()[0].jsonObject
        assertEquals("Caro Restrepo", guardado["nombre"]!!.jsonPrimitive.content)
        assertEquals("@caror", guardado["llave"]!!.jsonPrimitive.content, "la llave sigue ahí")

        // Y solo un `""` explícito la borra.
        client.put("/api/destinos/$id") {
            header(HttpHeaders.Authorization, "Bearer ${tokenFor(userAId)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Caro Restrepo","numero":"31973270756","llave":""}""")
        }
        assertEquals(null, listar()[0].jsonObject["llave"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `dos destinos no pueden tener la misma llave, y uno sin numero ni llave no entra`() = testApplication {
        wireApp()
        assertEquals(HttpStatusCode.Created, postDestino("""{"nombre":"Caro","numero":"","llave":"@caro"}""").status)
        val repetida = postDestino("""{"nombre":"Otra","numero":"","llave":" @CARO "}""")
        assertEquals(HttpStatusCode.Conflict, repetida.status)
        assertTrue(repetida.bodyAsText().contains("Caro"), repetida.bodyAsText())

        val vacio = postDestino("""{"nombre":"Nadie","numero":""}""")
        assertEquals(HttpStatusCode.BadRequest, vacio.status)
        assertEquals(com.jvillada.movi.shared.model.FALTA_NUMERO_O_LLAVE, vacio.bodyAsText())
    }
}
