package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DestinosDescartados
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
import com.jvillada.movi.shared.model.AnotacionPasada
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.MemoriaDeCategorias
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # Vuelta 85 · los avisos que nombran a alguien
 *
 * El caso de la revisión 9, con datos de prueba: Andrés Rojas guardado con la cuenta \*31234567890.
 * Su giro («Recibiste $539.625 de la cuenta \*31234567890 en tu cuenta \*4057 el 30/10/2026 a las
 * 13:05») salía titulado con la cola del aviso, y al confirmarlo la memoria aprendía ese texto: el envío
 * a Andrés pasaba a llamarse igual y con «Transferencia». Acá:
 *
 * 1. el ingreso de un tercero conocido se titula con su nombre, y el de uno desconocido con los últimos
 *    cuatro, nunca con «… en tu cuenta …»;
 * 2. lo ya confirmado con ese texto no contamina la salida a Andrés;
 * 3. la tarjeta del aviso dice antes de confirmar qué grupo saldaría.
 */
class LosAvisosQueNombranAAlguienTest {

    private val secreto = "test-secret-for-los-avisos-que-nombran-min-32"
    private val uid = "user-nombran"
    private val ahorros = "acc-ahorros-4057"

    private val andres = DestinoConocido(id = "dst_andres", nombre = "Andrés Rojas", numero = "31234567890")

    private fun haceHoras(horas: Long): String =
        ZonedDateTime.now(ZoneId.of("America/Bogota")).minusHours(horas).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private val textoDeAndres =
        "Bancolombia: Recibiste \$539.625 de la cuenta *31234567890 en tu cuenta *4057 el 30/10/2026 a las 13:05."
    private val textoDelDesconocido =
        "Bancolombia: Recibiste \$480.000 de la cuenta *77700012345 en tu cuenta *4057 el 28/10/2026 a las 10:00."
    private val textoAAndres =
        "Bancolombia: Transferiste \$350.000 desde tu cuenta *4057 a la cuenta *31234567890 el 29/10/2026 a las 18:00."
    private val textoDelCafe =
        "Bancolombia: Compraste \$32.000,00 en JUAN VALDEZ CAFE con tu T.Deb *4057, el 30/10/2026 a las 15:10."

    /** El texto que se aprendió en la revisión: la cola entera del aviso de Andrés. */
    private val colaDelAviso = "la cuenta *31234567890 en tu cuenta *4057 el 30/10/2026 a las 13:05"

    private val tablas = arrayOf(
        Users, Accounts, StatementImports, Events, VoidEvents,
        Budgets, RecurringRules, SmsMessages, Credits, Cards, Goals, CardPaymentDismissals, PushSubscriptions,
        CategoryPrefs, RecurringOccurrences, OccurrenceRejections, Apartados,
        KnownDestinations, DestinosDescartados, Espacios, GastosDelEspacio, LiquidacionesDelEspacio,
        Documents, Subscriptions, AnomaliasDescartadas,
    )

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:los_avisos_que_nombran_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(*tablas)
            SchemaUtils.create(*tablas)
            Users.insert { it[id] = uid; it[email] = "nombran@movi.test"; it[name] = "Laura"; it[passwordHash] = "hash" }
            Accounts.insert { it[id] = ahorros; it[userId] = uid; it[name] = "Bancolombia Ahorros 4057"; it[type] = "SAVINGS"; it[currency] = "COP" }
            KnownDestinations.insert {
                it[id] = andres.id; it[userId] = uid; it[nombre] = andres.nombre; it[numero] = andres.numero; it[createdAt] = 0L
            }
        }
    }

    private fun aviso(id: String, texto: String, hora: String) = transaction {
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = uid
            it[time] = hora
            it[bank] = "85540"
            it[text] = texto
            it[state] = "pending"
            it[det] = ""
        }
    }

    private fun movimiento(id: String, tipo: String, monto: Long, categoria: String, descripcion: String, comercio: String?, haceDias: Long) =
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = uid
                it[accountId] = ahorros
                it[type] = tipo
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = descripcion
                it[merchant] = comercio
                it[timestamp] = System.currentTimeMillis() - haceDias * 86_400_000L
                it[eventSource] = "SMS"
                it[reconciliationStatus] = "RECONCILED"
            }
        }

    private fun token(): String = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "nombran@movi.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
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

    private fun ApplicationTestBuilder.cliente() = createClient { install(ContentNegotiation) { json() } }

    private suspend fun ApplicationTestBuilder.propuestas(): PropuestasDeLaBandeja =
        cliente().get("/api/sms/propuestas") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body()

    private suspend fun ApplicationTestBuilder.parse(id: String): ParsedSms =
        cliente().get("/api/sms/$id/parse") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body()

    private suspend fun ApplicationTestBuilder.post(ruta: String, cuerpo: String): HttpStatusCode =
        cliente().post(ruta) {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }.status

    // ── Lo que lee el parser ────────────────────────────────────────────────────

    @Test
    fun `el ingreso que nombra la cuenta de origen se titula con sus ultimos cuatro`() {
        val leido = assertNotNull(parseSms(textoDeAndres, "85540"))
        assertEquals(TransactionType.INCOME, leido.type)
        assertEquals(539_625.0, leido.amount)
        assertEquals("Transferencia de *7890", leido.merchant)
        assertEquals("Transferencia de *2345", parseSms(textoDelDesconocido, "85540")?.merchant)
    }

    @Test
    fun `el ingreso que nombra a una persona se corta antes de en tu cuenta y de la fecha`() {
        val qr = "Bancolombia: Recibiste \$45,000.00 por QR de PERSONA DE PRUEBA en tu cuenta *3333 el 2026/10/01 a las 13:41."
        assertEquals("PERSONA DE PRUEBA", parseSms(qr, "85540")?.merchant)
        // Lo que ya se leía bien sigue igual.
        assertEquals(
            "CAROLINA RESTREPO SALAZAR",
            parseSms("Recibiste 300.000,00 en tu cuenta: Te llegó dinero de CAROLINA RESTREPO SALAZAR con tu llave.", "Notificación · Nu")?.merchant,
        )
    }

    @Test
    fun `con Andres guardado el ingreso dice Transferencia de Andres Rojas, sin el no`() {
        val leido = assertNotNull(parseSms(textoDeAndres, "85540"))
        assertEquals(
            "Transferencia de Andrés Rojas",
            conLaMemoriaYLosTerceros(leido, textoDeAndres, MemoriaDeCategorias.vacia, listOf(andres)).merchant,
        )
        val desconocido = assertNotNull(parseSms(textoDelDesconocido, "85540"))
        assertEquals(
            "Transferencia de *2345",
            conLaMemoriaYLosTerceros(desconocido, textoDelDesconocido, MemoriaDeCategorias.vacia, listOf(andres)).merchant,
        )
    }

    @Test
    fun `lo que se aprendio de un ingreso no contamina el envio al mismo numero`() {
        val memoria = MemoriaDeCategorias.de(
            listOf(
                // Lo que se confirmó en la revisión: la cola del aviso, como ingreso, con «Transferencia».
                AnotacionPasada(colaDelAviso, colaDelAviso, "Transferencia", cuando = 3, tipo = TransactionType.INCOME),
                // Y un giro de Andrés ya con su nombre.
                AnotacionPasada("Transferencia de Andrés Rojas", "Transferencia de Andrés Rojas", "Transferencia", 4, TransactionType.INCOME),
            ),
        )
        val salida = assertNotNull(parseSms(textoAAndres, "85540"))
        val propuesta = conLaMemoriaYLosTerceros(salida, textoAAndres, memoria, listOf(andres))
        assertEquals("Transferencia a Andrés Rojas", propuesta.merchant)
        assertNotEquals("Transferencia", propuesta.category)
        assertNull(propuesta.aprendidoDe, "«Así lo anotaste la última vez» no sale de un ingreso")
    }

    // ── La bandeja, de punta a punta ────────────────────────────────────────────

    @Test
    fun `tras confirmar el ingreso de Andres, la salida a Andres sigue siendo suya`() = testApplication {
        application { modulo() }
        // El ingreso ya confirmado en la revisión, con el texto roto como nombre (lo que hay en la base).
        movimiento("ev-ingreso-andres", "INCOME", 539_625, "Transferencia", colaDelAviso, colaDelAviso, haceDias = 1)
        aviso("sms_a_andres", textoAAndres, haceHoras(20))
        aviso("sms_desconocido", textoDelDesconocido, haceHoras(30))
        aviso("sms_cafe", textoDelCafe, haceHoras(2))

        val bandeja = propuestas().propuestas.associateBy { it.avisoId }
        val aAndres = assertNotNull(bandeja["sms_a_andres"]?.leido)
        assertEquals("Transferencia a Andrés Rojas", aAndres.merchant)
        assertNotEquals("Transferencia", aAndres.category)
        assertNull(aAndres.aprendidoDe)
        assertEquals("Transferencia de *2345", bandeja["sms_desconocido"]?.leido?.merchant)

        // Y el próximo giro de Andrés llega con su nombre.
        aviso("sms_de_andres", textoDeAndres, haceHoras(1))
        assertEquals("Transferencia de Andrés Rojas", parse("sms_de_andres").merchant)
    }

    @Test
    fun `la tarjeta del aviso de Andres dice que saldaria Casa con Andres, y sin grupos no dice nada`() = testApplication {
        application { modulo() }
        aviso("sms_de_andres", textoDeAndres, haceHoras(1))
        assertNull(propuestas().propuestas.single { it.avisoId == "sms_de_andres" }.saldaUnGrupo, "sin grupos")

        // Casa con Andrés: pagué el arriendo, él debe la mitad.
        movimiento("ev-arriendo", "EXPENSE", 1_079_250, "Vivienda", "Arriendo", null, haceDias = 3)
        assertEquals(
            HttpStatusCode.Created,
            post("/api/espacios", """{"id":"esp_casa","nombre":"Casa con Andrés","miembros":[{"id":"dst_andres"}]}"""),
        )
        assertEquals(HttpStatusCode.Created, post("/api/espacios/esp_casa/compartir", """{"eventoId":"ev-arriendo"}"""))

        val salda = assertNotNull(propuestas().propuestas.single { it.avisoId == "sms_de_andres" }.saldaUnGrupo)
        assertEquals("esp_casa", salda.espacioId)
        assertEquals("Casa con Andrés", salda.nombreDelEspacio)
        assertEquals("Andrés Rojas", salda.quien)
        assertEquals(539_625L, salda.debe)
        // Nada se saldó solo: el grupo no tiene ningún saldado.
        assertEquals(0L, transaction { LiquidacionesDelEspacio.selectAll().count() })
    }

    // ── La marca, de la revisión ────────────────────────────────────────────────

    private val textoDeRappi =
        "Bancolombia: Compraste \$42.800,00 en RAPPI COLOMBIA con tu T.Deb *4057, el 30/10/2026 a las 19:20."

    private val rappis = MemoriaDeCategorias.de((1..25).map { AnotacionPasada("RAPPI", "Rappi", "Restaurantes", it.toLong(), TransactionType.EXPENSE) })

    @Test
    fun `una compra en RAPPI COLOMBIA toma la categoria de los Rappi, dicha como pista`() {
        val leido = assertNotNull(parseSms(textoDeRappi, "85540"))
        val propuesta = conLaMemoriaYLosTerceros(leido, textoDeRappi, rappis, emptyList())
        assertEquals("Restaurantes", propuesta.category)
        assertEquals("Como tus 25 de Rappi", propuesta.aprendidoDe)
        kotlin.test.assertTrue(propuesta.porMarca, "no cuenta como entendida: no va de un toque")
        assertEquals(leido.merchant, propuesta.merchant, "el nombre del aviso no cambia")
        // Sin el texto del aviso (no se sabe si es una compra) la marca no habla.
        assertNull(conLoQueMoviRecuerda(leido, rappis).aprendidoDe)
    }

    @Test
    fun `un envio a una persona que se llama como una tienda no toma la categoria de la tienda`() {
        val zaras = MemoriaDeCategorias.de((1..4).map { AnotacionPasada("ZARA ANDINO", "Zara", "Ropa", it.toLong(), TransactionType.EXPENSE) })
        val envio = "Le enviaste \$50.000 a ZARA RESTREPO en su cuenta Bancolombia."
        val leido = ParsedSms(50_000.0, "ZARA RESTREPO", TransactionType.EXPENSE, "Otros")
        val propuesta = conLaMemoriaYLosTerceros(leido, envio, zaras, emptyList())
        assertNotEquals("Ropa", propuesta.category)
        kotlin.test.assertFalse(propuesta.porMarca)
    }

    @Test
    fun `en la bandeja, la compra por marca llega como pista y no entendida`() = testApplication {
        application { modulo() }
        (1..3).forEach { movimiento("ev-rappi-$it", "EXPENSE", 30_000L + it, "Restaurantes", "Rappi", "RAPPI", haceDias = it.toLong()) }
        aviso("sms_rappi", textoDeRappi, haceHoras(1))
        val leido = assertNotNull(propuestas().propuestas.single { it.avisoId == "sms_rappi" }.leido)
        assertEquals("Restaurantes", leido.category)
        assertEquals("Como tus 3 de Rappi", leido.aprendidoDe)
        kotlin.test.assertTrue(leido.porMarca)
        kotlin.test.assertFalse(com.jvillada.movi.shared.model.categoriaEntendida(leido, listOf("Restaurantes")))
    }
}
