package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.correo.alertaPostmark
import com.jvillada.movi.server.correo.tokenDeCorreoDe
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DestinosDescartados
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LecturasDePapeles
import com.jvillada.movi.server.db.Migrations
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.parsing.ComprobanteLeido
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.shared.model.AgregarIdentificador
import com.jvillada.movi.shared.model.DescartarSugerido
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.DestinoSugerido
import com.jvillada.movi.shared.model.DestinosDescartados as Descartados
import com.jvillada.movi.shared.model.IdentificadorDelDestino
import com.jvillada.movi.shared.model.MotivoDeDescarte
import com.jvillada.movi.shared.model.MovimientosDelDestino
import com.jvillada.movi.shared.model.MovimientosParaRenombrar
import com.jvillada.movi.shared.model.OrigenDelNombre
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.RenombradosDelDestino
import com.jvillada.movi.shared.model.RenombrarMovimientos
import com.jvillada.movi.shared.model.RespuestaDelSync
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TipoDeIdentificador
import com.jvillada.movi.shared.model.TipoDeTercero
import com.jvillada.movi.shared.model.idDeComprobante
import com.jvillada.movi.shared.model.numeros
import com.jvillada.movi.shared.model.todosLosIdentificadores
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Base64
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Cuentas de terceros a la mano** (4-oct-2026): varios identificadores por tercero, la plata que
 * entra, lo que Movi encuentra solo (`/sugeridos`), unir, renombrar hacia atrás, y que las cuatro
 * fuentes —SMS, notificación, correo y comprobante— propongan «Transferencia a <nombre>».
 *
 * Todo con textos sintéticos con la forma de los avisos de Bancolombia y Nu: ningún número ni nombre
 * es del dueño.
 */
class TercerosALaManoTest {

    private val testSecret = "test-secret-for-terceros-a-la-mano-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val userA = "user-a-terceros"
    private val userB = "user-b-terceros"
    private val ahorrosDeA = "acc-ahorros-a"
    private val secretoDelCorreo = "un-secreto-de-correo-para-terceros"

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        System.setProperty("movi.correo.secreto", secretoDelCorreo)
        System.setProperty("movi.correo.direccion", "base@inbound.test")
        RateLimiter.reset()
        Database.connect(
            url = "jdbc:h2:mem:terceros_a_la_mano_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules, SmsMessages,
                Credits, Goals, KnownDestinations, DestinosDescartados, PushSubscriptions, Documents, LecturasDePapeles,
            )
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            listOf(userA, userB).forEach { uid ->
                Users.insert {
                    it[id] = uid; it[email] = "$uid@terceros.test"; it[name] = uid; it[passwordHash] = "hash"
                }
            }
            Accounts.insert {
                it[id] = ahorrosDeA; it[userId] = userA; it[name] = "Ahorros 9999"; it[type] = "SAVINGS"; it[currency] = "COP"
            }
        }
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("movi.correo.secreto")
        System.clearProperty("movi.correo.direccion")
        RateLimiter.reset()
    }

    private fun token(uid: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", uid).withClaim("email", "$uid@terceros.test")
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

    private fun ApplicationTestBuilder.tipado(): HttpClient {
        application { testModule() }
        return createClient { install(ContentNegotiation) { json(json) } }
    }

    private suspend fun HttpClient.crear(destino: DestinoConocido, uid: String = userA): DestinoConocido {
        val r = post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(destino)
        }
        assertEquals(HttpStatusCode.Created, r.status, r.bodyAsText())
        return r.body()
    }

    private suspend fun HttpClient.agregar(id: String, identificador: IdentificadorDelDestino, mover: Boolean = false, uid: String = userA): HttpResponse =
        post("/api/destinos/$id/identificadores") {
            header(HttpHeaders.Authorization, "Bearer ${token(uid)}")
            contentType(ContentType.Application.Json)
            setBody(AgregarIdentificador(identificador, mover))
        }

    private suspend fun HttpClient.destinos(uid: String = userA): List<DestinoConocido> =
        get("/api/destinos") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    private suspend fun HttpClient.sugeridos(uid: String = userA): List<DestinoSugerido> =
        get("/api/destinos/sugeridos") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    private suspend fun HttpClient.parse(smsId: String, uid: String = userA): ParsedSms =
        get("/api/sms/$smsId/parse") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }.body()

    private fun numero(v: String) = IdentificadorDelDestino(TipoDeIdentificador.NUMERO, v)
    private fun llave(v: String) = IdentificadorDelDestino(TipoDeIdentificador.LLAVE, v)

    private fun caro() = DestinoConocido(nombre = "Caro", numero = "55500001111", deQuien = "esposa")

    /** Hace [dias] días, en el formato de la columna `time`: lo reciente no depende del reloj de CI. */
    private fun haceDias(dias: Long): String =
        java.time.LocalDateTime.now().minusDays(dias).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun insertarAviso(id: String, texto: String, banco: String = "85540", uid: String = userA, hora: String = haceDias(1)) =
        transaction {
            SmsMessages.insert {
                it[SmsMessages.id] = id; it[userId] = uid; it[time] = hora; it[bank] = banco
                it[text] = texto; it[state] = "pending"; it[det] = ""
            }
        }

    private fun insertarMovimiento(id: String, descripcion: String, monto: Long, raw: String?, uid: String = userA, cuando: Long = 1_790_000_000_000L) =
        transaction {
            Events.insert {
                it[Events.id] = id; it[userId] = uid; it[accountId] = ahorrosDeA; it[type] = "EXPENSE"
                it[amount] = monto; it[category] = "Otros"; it[description] = descripcion; it[merchant] = descripcion
                it[timestamp] = cuando; it[eventSource] = "SMS"; it[rawPayload] = raw
            }
        }

    // ── Migración ───────────────────────────────────────────────────────────────

    @Test
    fun `la migracion pasa numero y llave a la lista, sin tocarlos, y es idempotente`() {
        transaction {
            KnownDestinations.insert {
                it[id] = "dst_viejo"; it[userId] = userA; it[nombre] = "Viejo"; it[numero] = "55500001111"
                it[llave] = "@viejo"; it[createdAt] = 1L
            }
            KnownDestinations.insert {
                it[id] = "dst_solo_llave"; it[userId] = userA; it[nombre] = "Llave"; it[numero] = ""
                it[llave] = "carolina prueba salazar"; it[createdAt] = 1L
            }
        }
        fun lista() = transaction {
            KnownDestinations.selectAll().associate { it[KnownDestinations.id] to it[KnownDestinations.identificadores] }
        }
        transaction { with(Migrations) { pasarLosIdentificadoresALaLista() } }
        val primera = lista()
        assertEquals(
            """[{"tipo":"NUMERO","valor":"55500001111"},{"tipo":"LLAVE","valor":"@viejo"}]""",
            primera["dst_viejo"],
        )
        assertEquals("""[{"tipo":"LLAVE","valor":"carolina prueba salazar"}]""", primera["dst_solo_llave"])
        transaction { with(Migrations) { pasarLosIdentificadoresALaLista() } }
        assertEquals(primera, lista(), "correrla otra vez no cambia nada")
        transaction {
            val fila = KnownDestinations.selectAll().where { KnownDestinations.id eq "dst_viejo" }.single()
            assertEquals("55500001111", fila[KnownDestinations.numero], "el APK instalado sigue leyendo esto")
            assertEquals("@viejo", fila[KnownDestinations.llave])
        }
    }

    // ── APK viejo ──────────────────────────────────────────────────────────────

    @Test
    fun `un APK viejo crea y edita sin los campos nuevos, y no pierde los identificadores de mas`() = testApplication {
        val c = tipado()
        val crudo = client.post("/api/destinos") {
            header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Caro","numero":"*55500001111","deQuien":"esposa"}""")
        }
        assertEquals(HttpStatusCode.Created, crudo.status)
        val id = json.parseToJsonElement(crudo.bodyAsText()).jsonObject["id"]!!.toString().trim('"')
        assertEquals(HttpStatusCode.OK, c.agregar(id, llave("@caro.prueba")).status)

        // El APK instalado edita el número: manda su cuerpo de siempre, sin lista ni llave.
        val put = client.put("/api/destinos/$id") {
            header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"nombre":"Caro R","numero":"55500002222","deQuien":"esposa"}""")
        }
        assertEquals(HttpStatusCode.OK, put.status, put.bodyAsText())
        val leido = c.destinos().single()
        assertEquals("Caro R", leido.nombre)
        assertEquals("55500002222", leido.numero, "el número nuevo reemplaza al viejo")
        assertEquals(listOf("55500002222"), leido.numeros())
        assertEquals("@caro.prueba", leido.llave, "la llave que no conocía queda")
    }

    // ── Unir, sumar, mover, quitar ──────────────────────────────────────────────

    @Test
    fun `unir a un tercero existente suma el identificador sin duplicar al tercero`() = testApplication {
        val c = tipado()
        val caro = c.crear(caro())
        val r = c.agregar(caro.id, llave("CAROLINA PRUEBA SALAZAR"))
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertEquals(HttpStatusCode.OK, c.agregar(caro.id, llave("Carolina Prueba Salazar")).status, "dos veces es idempotente")
        val todos = c.destinos()
        assertEquals(1, todos.size, "no se creó otro tercero")
        assertEquals(
            listOf(numero("55500001111"), llave("carolina prueba salazar")),
            todos.single().todosLosIdentificadores(),
        )
    }

    @Test
    fun `un identificador de otro tercero choca, salvo que se mueva, y nunca se queda sin ninguno`() = testApplication {
        val c = tipado()
        val caro = c.crear(caro())
        val papa = c.crear(DestinoConocido(nombre = "Papá", numero = "77700003333", llave = "@papa.prueba"))
        assertEquals(HttpStatusCode.Conflict, c.agregar(caro.id, llave("@papa.prueba")).status)
        assertEquals(HttpStatusCode.OK, c.agregar(caro.id, llave("@papa.prueba"), mover = true).status)
        val porNombre = c.destinos().associateBy { it.nombre }
        assertTrue(porNombre.getValue("Caro").todosLosIdentificadores().contains(llave("@papa.prueba")))
        assertEquals(listOf(numero("77700003333")), porNombre.getValue("Papá").todosLosIdentificadores())
        // Ahora a Papá le queda uno solo: moverlo lo dejaría sin nada.
        assertEquals(HttpStatusCode.Conflict, c.agregar(caro.id, numero("77700003333"), mover = true).status)
        // Y quitarle el último tampoco.
        val quitar = c.post("/api/destinos/${papa.id}/identificadores/quitar") {
            header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
            contentType(ContentType.Application.Json)
            setBody(numero("77700003333"))
        }
        assertEquals(HttpStatusCode.BadRequest, quitar.status)
    }

    @Test
    fun `un numero de una cuenta propia no se le puede sumar a nadie`() = testApplication {
        val c = tipado()
        val caro = c.crear(caro())
        val r = c.agregar(caro.id, numero("12349999"))
        assertEquals(HttpStatusCode.UnprocessableEntity, r.status)
        assertTrue("Ahorros 9999" in r.bodyAsText())
    }

    // ── Reconocer en el momento: las cuatro fuentes ─────────────────────────────

    @Test
    fun `SMS y notificacion proponen el nombre del tercero por cualquier identificador, ida y vuelta`() = testApplication {
        val c = tipado()
        val caro = c.crear(caro())
        c.agregar(caro.id, llave("@caro.prueba"))
        c.agregar(caro.id, llave("carolina prueba salazar"))
        val sms = SmsMessage(
            id = "sms-1", time = "2026-10-01 10:00", bank = "85540", state = "", det = "",
            text = "Bancolombia: JUAN, transferiste \$45.000 a la llave @caro.prueba desde tu cuenta *9999 a CAROLINA PRUEBA SALAZAR el 01/10/26 a las 10:00.",
        )
        val notificacion = SmsMessage(
            id = "nu-1", time = "2026-10-01 11:00", bank = "Notificación · Nu", state = "", det = "",
            text = "Recibiste 200.000,00 en tu cuenta: Te llegó dinero de CAROLINA PRUEBA SALAZAR con tu llave.",
        )
        val sync = c.post("/api/sms/sync") {
            header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
            contentType(ContentType.Application.Json)
            setBody(listOf(sms, notificacion))
        }.body<RespuestaDelSync>()
        // La notificación de la Ola 1 («Movi anotó $X · …») ya usa el nombre del tercero.
        assertEquals(
            setOf("Transferencia a Caro", "Transferencia de Caro"),
            sync.porRevisar.map { it.descripcion }.toSet(),
        )
        assertEquals("Transferencia a Caro", c.parse("sms-1").merchant)
        assertEquals("Transferencia de Caro", c.parse("nu-1").merchant)
    }

    @Test
    fun `recibiste una transferencia de X se atribuye al tercero y la ficha dice lo que te envio`() = testApplication {
        val c = tipado()
        val papa = c.crear(DestinoConocido(nombre = "Papá", numero = "77700003333"))
        c.agregar(papa.id, llave("JOSE PRUEBA ARANGO"))
        val texto = "Bancolombia: JUAN, recibiste una transferencia de JOSE PRUEBA ARANGO por \$1.500.000 en tu cuenta *9999 conectada a la llave @JUANPRUEBA el 28/08/26 a las 19:22."
        insertarAviso("in-1", texto)
        assertEquals("Transferencia de Papá", c.parse("in-1").merchant)

        transaction {
            Events.insert {
                it[id] = "ev-in"; it[userId] = userA; it[accountId] = ahorrosDeA; it[type] = "INCOME"
                it[amount] = 1_500_000; it[category] = "Transferencia"; it[description] = "JOSE PRUEBA ARANGO"
                it[timestamp] = 1_790_000_000_000L; it[eventSource] = "SMS"; it[rawPayload] = texto
            }
        }
        val ficha = c.get("/api/destinos/${papa.id}/movimientos") {
            header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
        }.body<MovimientosDelDestino>()
        assertEquals(listOf("ev-in"), ficha.recibidos.map { it.id })
        assertTrue(ficha.movimientos.isEmpty(), "lo que entró no es lo que le enviaste")
        assertEquals(mapOf("COP" to 1_500_000L), c.destinos().single().recibidos)
    }

    @Test
    fun `un correo del banco tambien propone el nombre del tercero`() = testApplication {
        val c = tipado()
        val caro = c.crear(caro())
        c.agregar(caro.id, numero("55500004444"))
        val cuerpo = "Bancolombia: Transferiste \$75.000 desde tu cuenta *9999 a la cuenta *55500004444 el 01/10/2026 a las 09:00."
        val r = client.post("/api/correo-entrante") {
            header(HttpHeaders.Authorization, "Basic " + Base64.getEncoder().encodeToString("movi:$secretoDelCorreo".toByteArray()))
            contentType(ContentType.Application.Json)
            setBody(alertaPostmark("base+${tokenDeCorreoDe(userA)}@inbound.test", asunto = "Alertas y Notificaciones", cuerpo = cuerpo))
        }
        assertEquals(HttpStatusCode.Accepted, r.status, r.bodyAsText())
        val id = transaction { SmsMessages.selectAll().single { it[SmsMessages.bank].startsWith("Correo") }[SmsMessages.id] }
        assertEquals("Transferencia a Caro", c.parse(id).merchant)
    }

    @Test
    fun `un comprobante, leido desde la lectura guardada, tambien propone el nombre del tercero`() = testApplication {
        val c = tipado()
        val caro = c.crear(caro())
        c.agregar(caro.id, llave("@caro.prueba"))
        c.agregar(caro.id, llave("carolina prueba salazar"))
        fun comprobante(doc: String, leido: ComprobanteLeido) {
            val smsId = idDeComprobante(doc)
            insertarAviso(smsId, textoDelComprobante(leido), banco = "Comprobante · $doc.jpg")
            transaction {
                LecturasDePapeles.insert {
                    it[userId] = userA; it[huella] = "h-$doc"; it[documentoId] = doc; it[tipo] = "COMPROBANTE"
                    it[datos] = json.encodeToString(ComprobanteLeido.serializer(), leido)
                    it[modelo] = "prueba"; it[leidoEn] = 1L; it[porRevisarId] = smsId
                }
            }
        }
        comprobante("doc1", ComprobanteLeido(monto = 60_000.0, llave = "@caro.prueba", cuentaPropia = "9999"))
        comprobante(
            "doc2",
            ComprobanteLeido(monto = 90_000.0, tipo = com.jvillada.movi.shared.model.TransactionType.INCOME, comercio = "CAROLINA PRUEBA SALAZAR", cuentaPropia = "9999"),
        )
        assertEquals("Transferencia a Caro", c.parse(idDeComprobante("doc1")).merchant)
        assertEquals("Transferencia de Caro", c.parse(idDeComprobante("doc2")).merchant)
    }

    // ── Sugeridos ───────────────────────────────────────────────────────────────

    private fun qr(llave: String, dia: Int) =
        "Bancolombia: JUAN PRUEBA RAMIREZ pagaste \$20.000 por codigo QR desde tu cuenta *9999 a la llave $llave el ${"%02d".format(dia)}/09/2026 a las 12:00."

    private fun transferencia(cuenta: String, dia: Int, monto: String = "\$100.000") =
        "Bancolombia: Transferiste $monto desde tu cuenta *9999 a la cuenta *$cuenta el ${"%02d".format(dia)}/09/2026 a las 10:00."

    @Test
    fun `sugeridos excluye guardados, cuentas propias, el nombre del dueño, ignorados y es mia`() = testApplication {
        val c = tipado()
        c.crear(caro())
        insertarAviso("s1", transferencia("55500001111", 1), hora = "2026-09-01 10:00") // Caro, guardada
        insertarAviso("s2", transferencia("88800009999", 2), hora = "2026-09-02 10:00") // cola 9999: suya
        insertarAviso("s3", qr("0011223344", 3), hora = "2026-09-03 12:00")
        insertarAviso("s4", qr("0011223344", 4), hora = "2026-09-04 12:00")
        insertarAviso("s5", transferencia("66600005555", 5), hora = "2026-09-05 10:00")
        insertarAviso("s6", transferencia("66600005555", 6), hora = "2026-09-06 10:00")
        insertarAviso("s7", transferencia("44400007777", 7), hora = "2026-09-07 10:00")
        insertarAviso("s8", transferencia("44400007777", 8), hora = "2026-09-08 10:00")
        insertarAviso(
            "s9",
            "Bancolombia: JUAN, recibiste una transferencia de Juan Prueba Ramirez por \$300.000 en tu cuenta *9999 el 09/09/26 a las 16:49.",
            hora = "2026-09-09 16:49",
        )
        // De B: nunca aparece en lo de A.
        insertarAviso("b1", transferencia("33300001234", 1), uid = userB, hora = "2026-09-01 10:00")
        insertarAviso("b2", transferencia("33300001234", 2), uid = userB, hora = "2026-09-02 10:00")

        val antes = c.sugeridos().map { it.identificador }
        assertEquals(setOf(llave("0011223344"), numero("66600005555"), numero("44400007777")), antes.toSet())

        listOf(numero("66600005555") to MotivoDeDescarte.IGNORADO, numero("44400007777") to MotivoDeDescarte.ES_MIA)
            .forEach { (id, motivo) ->
                val r = c.post("/api/destinos/sugeridos/descartar") {
                    header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
                    contentType(ContentType.Application.Json)
                    setBody(DescartarSugerido(id, motivo))
                }
                assertEquals(HttpStatusCode.NoContent, r.status)
            }
        assertEquals(listOf(llave("0011223344")), c.sugeridos().map { it.identificador })
        assertEquals(listOf(numero("33300001234")), c.sugeridos(userB).map { it.identificador }, "B ve lo suyo y nada de A")

        // Lo descartado y lo del dueño tampoco lo pregunta la fila «¿De quién es…?».
        val descartados = c.get("/api/destinos/descartados") { header(HttpHeaders.Authorization, "Bearer ${token(userA)}") }
            .body<Descartados>().claves
        assertTrue(numero("66600005555").let { "NUMERO:${it.valor}" } in descartados)
        assertTrue("LLAVE:juan prueba ramirez" in descartados, "su nombre, como lo escribe su banco")
        assertTrue("COLA:9999" in descartados)
    }

    @Test
    fun `el nombre propuesto sale del banco o del nombre que el dueño le puso`() = testApplication {
        val c = tipado()
        insertarAviso(
            "h1",
            "Bancolombia: JUAN, transferiste \$40.000 a la llave marta.r@correo.com desde tu cuenta *9999 a MARTA RUIZ ORTIZ el 14/09/26 a las 21:18.",
            hora = haceDias(3),
        )
        insertarAviso("q1", qr("0099887766", 10), hora = haceDias(10))
        insertarMovimiento("e1", "Empanadas - Pago QR (llave 0099887766)", 25_000, raw = null)
        val porLlave = c.sugeridos().associateBy { it.identificador.valor }
        val marta = porLlave.getValue("marta.r@correo.com")
        assertEquals("Marta Ruiz Ortiz", marta.nombrePropuesto)
        assertEquals(OrigenDelNombre.BANCO, marta.origenDelNombre)
        assertEquals(TipoDeTercero.PERSONA, marta.tipo)
        val empanadas = porLlave.getValue("0099887766")
        assertEquals("Empanadas", empanadas.nombrePropuesto)
        assertEquals(OrigenDelNombre.TUYO, empanadas.origenDelNombre)
        assertEquals(TipoDeTercero.COMERCIO, empanadas.tipo)
    }

    @Test
    fun `lo que llega de alguien parecido a un guardado se ofrece unir, y unido deja de sugerirse`() = testApplication {
        val c = tipado()
        val caro = c.crear(caro())
        insertarAviso("n1", "Recibiste 200.000,00 en tu cuenta: Te llegó dinero de CAROLINA PRUEBA SALAZAR con tu llave.", banco = "Notificación · Nu")
        val s = c.sugeridos().single()
        assertEquals(caro.id, s.pareceDe)
        assertEquals("Caro", s.pareceDeNombre)
        assertEquals(mapOf("COP" to 200_000L), s.recibido)
        assertEquals(HttpStatusCode.OK, c.agregar(caro.id, s.identificador).status)
        assertTrue(c.sugeridos().isEmpty())
        assertEquals(1, c.destinos().size)
    }

    // ── Renombrar hacia atrás ───────────────────────────────────────────────────

    @Test
    fun `renombrar hacia atras solo con confirmacion, y nunca un nombre que escribio el dueño`() = testApplication {
        val c = tipado()
        val texto1 = transferencia("66600005555", 1)
        val texto2 = transferencia("66600005555", 2)
        insertarMovimiento("ilegible", "Transferencia a la cuenta *66600005555", 100_000, raw = texto1)
        insertarMovimiento("suyo", "Arriendo de septiembre", 100_000, raw = texto2)
        insertarMovimiento("de-b", "Transferencia a la cuenta *66600005555", 100_000, raw = texto1, uid = userB)
        val vecino = c.crear(DestinoConocido(nombre = "Vecino", numero = "66600005555"))

        val ofrecidos = c.get("/api/destinos/${vecino.id}/renombrables") {
            header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
        }.body<MovimientosParaRenombrar>()
        assertEquals(listOf("ilegible"), ofrecidos.movimientos.map { it.id })
        assertEquals("Transferencia a Vecino", ofrecidos.nombreNuevoDelGasto)
        fun descripcion(id: String) = transaction { Events.selectAll().where { Events.id eq id }.single()[Events.description] }
        assertEquals("Transferencia a la cuenta *66600005555", descripcion("ilegible"), "mirar no cambia nada")

        // Aunque la app pida los tres, el server solo renombra el ilegible y suyo.
        val r = c.post("/api/destinos/${vecino.id}/renombrar") {
            header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
            contentType(ContentType.Application.Json)
            setBody(RenombrarMovimientos(listOf("ilegible", "suyo", "de-b")))
        }.body<RenombradosDelDestino>()
        assertEquals(RenombradosDelDestino(renombrados = 1, omitidos = 2), r)
        assertEquals("Transferencia a Vecino", descripcion("ilegible"))
        assertEquals("Arriendo de septiembre", descripcion("suyo"))
        assertEquals("Transferencia a la cuenta *66600005555", descripcion("de-b"))
        assertNotNull(transaction { Events.selectAll().where { Events.id eq "ilegible" }.single()[Events.lastEditedAt] })
        // Y el que el dueño nombró cuenta igual en la ficha: el texto del banco lo nombra.
        assertEquals(2, c.destinos().single().cuantos)
    }

    // ── Aislamiento ─────────────────────────────────────────────────────────────

    @Test
    fun `nadie le suma ni le quita identificadores a un tercero ajeno ni ve sus renombrables`() = testApplication {
        val c = tipado()
        val deB = c.crear(caro(), uid = userB)
        assertEquals(HttpStatusCode.NotFound, c.agregar(deB.id, llave("@intruso")).status)
        val quitar = c.post("/api/destinos/${deB.id}/identificadores/quitar") {
            header(HttpHeaders.Authorization, "Bearer ${token(userA)}")
            contentType(ContentType.Application.Json)
            setBody(numero("55500001111"))
        }
        assertEquals(HttpStatusCode.NotFound, quitar.status)
        assertEquals(
            HttpStatusCode.NotFound,
            c.get("/api/destinos/${deB.id}/renombrables") { header(HttpHeaders.Authorization, "Bearer ${token(userA)}") }.status,
        )
        assertNull(c.destinos().firstOrNull())
        assertEquals(1, c.destinos(userB).size)
    }
}
