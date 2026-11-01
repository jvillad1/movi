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
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.GrupoDeAvisos
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.NoSePudoLeerElAviso
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.cuantosPagosPorRevisar
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.momentoDelSms
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 91 · el traspaso de Bancolombia a Nu del 31-oct, de punta a punta
 *
 * Con los textos reales de ese día (sin el nombre del dueño): el dueño pasó $1.500.000 de Bancolombia
 * Ahorros 8133 a su cuenta Nu por PSE, y llegaron tres avisos del mismo movimiento —el SMS de
 * Bancolombia, el correo de PSE y la notificación de Nu del depósito—, más los rechazos de PSE y de Nu
 * y el pago de la tarjeta Nu desde la cuenta Nu. Se exige que:
 *
 *  1. los tres avisos sean **un** pago, propuesto desde el SMS como traspaso 8133 → Nu;
 *  2. confirmarlo escriba las dos patas **una vez** (dos toques, un traspaso), deje los tres
 *     confirmados y enlazados, y no sume en «Entró» ni en «Salió»;
 *  3. solo el depósito de Nu se proponga como traspaso que entra (origen a elegir), nunca un ingreso;
 *  4. el SMS solo, sin el correo ni el depósito, proponga el traspaso con el destino a elegir entre la
 *     cuenta Nu y la Nu Tarjeta (y con una sola cuenta Nu sin tarjeta, esa);
 *  5. el pago de la tarjeta Nu se proponga desde la cuenta Nu hacia la Nu Tarjeta;
 *  6. los rechazos sigan sin leerse ni juntarse.
 */
class ElTraspasoANuDePuntaAPuntaTest {
    private val testSecret = "test-secret-for-traspaso-a-nu-tests-minimum-32"
    private val uid = "user-traspaso-nu"
    private val ayer = AppClock.today().minusDays(1)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:traspaso_a_nu_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            Users.insert { it[id] = uid; it[email] = "nu@movi.test"; it[name] = "Dueño"; it[passwordHash] = "hash" }
            // Las cuentas del dueño, con los nombres de producción.
            cuenta(B0031, "Bancolombia Ahorros 0031", "SAVINGS")
            cuenta(B8133, "Bancolombia Ahorros 8133 · 4057", "SAVINGS")
            cuenta("acc-glim", "Glim Alimentación 3037", "SAVINGS")
            cuenta(NU, "Nu", "SAVINGS")
            cuenta(NU_TARJETA, "Nu Tarjeta", "CREDIT_CARD")
            cuenta("acc-amex", "AMEX 9208", "CREDIT_CARD")
        }
    }

    private fun cuenta(id: String, nombre: String, tipo: String) {
        Accounts.insert { it[Accounts.id] = id; it[userId] = uid; it[name] = nombre; it[type] = tipo }
    }

    private fun aviso(id: String, banco: String, hora: String, texto: String, estado: String = SMS_STATE_PENDING) {
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = uid
            it[time] = "$ayer $hora"
            it[bank] = banco
            it[text] = texto
            it[state] = estado
            it[det] = ""
        }
    }

    /** Los tres avisos del traspaso, como llegaron (a las 11:12 los tres). */
    private fun losTresAvisos() = transaction {
        aviso(SMS, "85784", "11:12", SMS_DE_SALIDA)
        aviso(DEPOSITO, "Notificación · Nu", "11:12", DEPOSITO_DE_NU)
        aviso(CORREO, "Correo · achcolombia.com.co", "11:12", CORREO_DE_PSE)
    }

    private fun token(): String = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "nu@movi.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
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

    private fun ApplicationTestBuilder.cliente() = createClient { install(ContentNegotiation) { json() } }

    private suspend inline fun <reified T> ApplicationTestBuilder.leer(ruta: String): T {
        val r = cliente().get(ruta) { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, r.status, "$ruta: ${r.bodyAsText()}")
        return r.body()
    }

    private suspend fun ApplicationTestBuilder.confirmar(grupoId: String, pedido: ConfirmarElMismoPago) =
        cliente().post("/api/sms/grupo/$grupoId/confirmar") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(pedido)
        }

    private fun traspaso8133ANu(sufijo: String = "") = DosPatasDelAviso(
        operacion = OperacionDelAviso.TRASPASO,
        origenId = B8133,
        destinoId = NU,
        monto = 1_500_000L,
        timestamp = momentoDelSms("$ayer 11:12", ahora = Long.MAX_VALUE),
        transferId = "tr-traspaso-nu$sufijo",
        origenEventId = "ev-traspaso-nu-sale$sufijo",
        destinoEventId = "ev-traspaso-nu-entra$sufijo",
        nota = "Depósito a tu cuenta NU",
    )

    @Test
    fun `los tres avisos del 31-oct son un solo traspaso de 8133 a Nu`() = testApplication {
        application { testModule() }
        losTresAvisos()
        val bandeja = leer<List<SmsMessage>>("/api/sms")
        val grupoId = assertNotNull(bandeja.first { it.id == SMS }.grupoId, "el SMS es parte de un pago")
        assertEquals(setOf(SMS, DEPOSITO, CORREO), bandeja.first { it.id == SMS }.miembrosDelGrupo.toSet(), "los tres son el mismo pago")
        assertEquals(1, cuantosPagosPorRevisar(bandeja), "Por revisar cuenta un pago, no tres")

        val grupo = leer<GrupoDeAvisos>("/api/sms/grupo/$grupoId")
        assertEquals(SMS, grupo.propuestaDe, "la propuesta sale del SMS, que dice de qué cuenta salió; nunca del depósito")

        val p = leer<ParsedSms>("/api/sms/$SMS/parse")
        assertEquals(1_500_000.0, p.amount)
        assertEquals(TransactionType.EXPENSE, p.type)
        assertEquals(TRANSFER_CATEGORY, p.category, "un traspaso entre cuentas suyas, no un gasto")
        assertEquals(NU, p.traspasoHaciaId, "a la cuenta Nu (el correo de PSE dice «Depósito a tu cuenta NU»)")
        assertNull(p.deudaSugeridaId)
        assertNull(p.identificadorDelDestino, "su cuenta Nu no es «una cuenta de otros»")

        // La bandeja propone lo mismo para ese pago, una vez.
        val propuestas = leer<PropuestasDeLaBandeja>("/api/sms/propuestas").propuestas
        val delPago = propuestas.filter { it.grupoId == grupoId }
        assertEquals(1, delPago.size)
        assertEquals(NU, delPago.single().leido?.traspasoHaciaId)
        assertTrue(propuestas.none { it.avisoId == DEPOSITO || it.avisoId == CORREO }, "el depósito y el correo no son tarjetas aparte")
    }

    @Test
    fun `confirmar el traspaso escribe las dos patas una vez, cierra los tres avisos y no cuenta en Entro ni en Salio`() = testApplication {
        application { testModule() }
        losTresAvisos()
        val grupoId = assertNotNull(leer<List<SmsMessage>>("/api/sms").first { it.id == SMS }.grupoId)
        val miembros = leer<GrupoDeAvisos>("/api/sms/grupo/$grupoId").miembros.map { it.id }

        val primero = confirmar(grupoId, ConfirmarElMismoPago(miembros, patas = traspaso8133ANu()))
        assertEquals(HttpStatusCode.OK, primero.status, primero.bodyAsText())
        val hecho = primero.body<MismoPagoConfirmado>()
        assertTrue(hecho.creado)
        assertEquals(listOf("ev-traspaso-nu-sale", "ev-traspaso-nu-entra"), hecho.patas)
        assertEquals(setOf(SMS, DEPOSITO, CORREO), hecho.cerrados.toSet())

        // Dos toques (o dos teléfonos): el segundo pedido, con OTROS ids, no escribe nada más.
        val segundo = confirmar(grupoId, ConfirmarElMismoPago(miembros, patas = traspaso8133ANu("-otro")))
        assertEquals(HttpStatusCode.OK, segundo.status, segundo.bodyAsText())
        val otraVez = segundo.body<MismoPagoConfirmado>()
        assertFalse(otraVez.creado)
        assertEquals("ev-traspaso-nu-sale", otraVez.eventoId)

        val (patas, avisos) = transaction {
            Events.selectAll().where { Events.userId eq uid }.map {
                listOf(it[Events.id], it[Events.accountId], it[Events.type], it[Events.amount].toString(), it[Events.category], it[Events.transferId].orEmpty())
            } to SmsMessages.selectAll().where { SmsMessages.userId eq uid }.associate { it[SmsMessages.id] to (it[SmsMessages.state] to it[SmsMessages.eventoId]) }
        }
        assertEquals(2, patas.size, "un traspaso: dos patas, y nada más")
        val sale = patas.single { it[2] == "EXPENSE" }
        val entra = patas.single { it[2] == "INCOME" }
        assertEquals(listOf(B8133, "1500000", TRANSFER_CATEGORY, "tr-traspaso-nu"), listOf(sale[1], sale[3], sale[4], sale[5]))
        assertEquals(listOf(NU, "1500000", TRANSFER_CATEGORY, "tr-traspaso-nu"), listOf(entra[1], entra[3], entra[4], entra[5]))
        // Ni «Entró» ni «Salió»: la plata no salió del dueño ni le llegó de nadie.
        assertFalse(isCashFlow(AccountType.SAVINGS, TransactionType.EXPENSE, sale[4]))
        assertFalse(isCashFlow(AccountType.SAVINGS, TransactionType.INCOME, entra[4]))
        // Los tres avisos quedan confirmados y enlazados a la pata del dinero.
        listOf(SMS, DEPOSITO, CORREO).forEach { id ->
            assertEquals(SMS_STATE_CONFIRMED to "ev-traspaso-nu-sale", avisos[id], id)
        }
        assertEquals(0, cuantosPagosPorRevisar(leer("/api/sms")), "Por revisar quedó vacío")
    }

    @Test
    fun `solo el deposito de Nu es un traspaso que entra con el origen a elegir, nunca un ingreso`() = testApplication {
        application { testModule() }
        transaction { aviso(DEPOSITO, "Notificación · Nu", "11:12", DEPOSITO_DE_NU) }
        val r = cliente().get("/api/sms/$DEPOSITO/parse?anotarloIgual=1") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, r.status, "Movi lo entiende: ya no es «Anotarlo igual» (${r.bodyAsText()})")
        val p = r.body<ParsedSms>()
        assertEquals(1_500_000.0, p.amount, "el monto con coma decimal: 1.500.000,00")
        assertEquals(TransactionType.INCOME, p.type)
        assertEquals(TRANSFER_CATEGORY, p.category, "el lado que entra de un traspaso, no un ingreso en «Entró»")
        assertNull(p.traspasoDesdeId, "de qué cuenta vino lo elige el dueño")
        assertNull(p.identificadorDelDestino)
    }

    @Test
    fun `el SMS solo propone el traspaso con el destino a elegir entre la cuenta Nu y la Nu Tarjeta`() = testApplication {
        application { testModule() }
        transaction { aviso(SMS, "85784", "11:12", SMS_DE_SALIDA) }
        val p = leer<ParsedSms>("/api/sms/$SMS/parse")
        assertEquals(TRANSFER_CATEGORY, p.category)
        assertNull(p.traspasoHaciaId, "¿depósito a la cuenta Nu o abono a la Nu Tarjeta? Lo dice el dueño")
        assertEquals(listOf(NU, NU_TARJETA), p.destinosPosibles)
    }

    @Test
    fun `con una sola cuenta Nu y sin tarjeta Nu, el SMS solo ya propone esa cuenta`() = testApplication {
        application { testModule() }
        transaction {
            Accounts.deleteWhere { Accounts.id eq NU_TARJETA }
            aviso(SMS, "85784", "11:12", SMS_DE_SALIDA)
        }
        val p = leer<ParsedSms>("/api/sms/$SMS/parse")
        assertEquals(TRANSFER_CATEGORY, p.category)
        assertEquals(NU, p.traspasoHaciaId)
    }

    @Test
    fun `el SMS y el deposito sin el correo tambien son un traspaso a Nu`() = testApplication {
        application { testModule() }
        transaction {
            aviso(SMS, "85784", "11:12", SMS_DE_SALIDA)
            aviso(DEPOSITO, "Notificación · Nu", "11:13", DEPOSITO_DE_NU)
        }
        val bandeja = leer<List<SmsMessage>>("/api/sms")
        assertEquals(setOf(SMS, DEPOSITO), bandeja.first { it.id == SMS }.miembrosDelGrupo.toSet())
        val p = leer<ParsedSms>("/api/sms/$SMS/parse")
        assertEquals(TRANSFER_CATEGORY, p.category)
        assertEquals(NU, p.traspasoHaciaId, "el depósito de Nu del mismo pago dice a qué cuenta entró")
    }

    @Test
    fun `el pago de la tarjeta Nu se propone desde la cuenta Nu y con dos patas baja la deuda`() = testApplication {
        application { testModule() }
        transaction {
            aviso(PAGO_NU, "Notificación · Nu", "12:13", PAGO_TARJETA_NU)
            aviso("notif_1d32a54c1d6afb23", "Notificación · Nu", "12:13", PAGO_TARJETA_NU_150)
        }
        val bandeja = leer<List<SmsMessage>>("/api/sms")
        assertNull(bandeja.first { it.id == PAGO_NU }.grupoId, "dos pagos del mismo origen no se juntan")
        val p = leer<ParsedSms>("/api/sms/$PAGO_NU/parse")
        assertEquals(1_306_937.91, p.amount)
        assertEquals(CARD_PAYMENT_CATEGORY, p.category)
        assertEquals(NU_TARJETA, p.deudaSugeridaId, "la tarjeta que baja")
        assertEquals(NU, p.cuentaSugeridaId, "la cuenta de la que sale")

        val pedido = DosPatasDelAviso(
            operacion = OperacionDelAviso.PAGO_DE_TARJETA, origenId = NU, destinoId = NU_TARJETA, monto = 1_306_938L,
            timestamp = momentoDelSms("$ayer 12:13", ahora = Long.MAX_VALUE),
            transferId = "tr-pago-nu", origenEventId = "ev-pago-nu-sale", destinoEventId = "ev-pago-nu-entra",
        )
        val r = cliente().post("/api/sms/$PAGO_NU/confirm") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(DosPatasDelAviso.serializer(), pedido))
        }
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val patas = transaction {
            Events.selectAll().where { Events.userId eq uid }.associate { it[Events.accountId] to (it[Events.type] to it[Events.category]) }
        }
        assertEquals("EXPENSE" to CARD_PAYMENT_CATEGORY, patas[NU], "sale de la cuenta Nu")
        assertEquals("INCOME", patas[NU_TARJETA]?.first, "entra a la tarjeta: le baja la deuda (nunca un gasto en la tarjeta)")
    }

    @Test
    fun `los rechazos de PSE y de Nu siguen sin leerse y no se juntan con el traspaso`() = testApplication {
        application { testModule() }
        losTresAvisos()
        transaction {
            aviso("correo_604b8e9f6c490531", "Correo · achcolombia.com.co", "11:17", CORREO_RECHAZADO)
            aviso("notif_9688c4127249c00e", "Notificación · Nu", "11:12", NU_NO_APROBADO)
        }
        assertNull(parseSms(CORREO_RECHAZADO, "Correo · achcolombia.com.co"))
        assertNull(parseSms(NU_NO_APROBADO, "Notificación · Nu"))
        val bandeja = leer<List<SmsMessage>>("/api/sms")
        assertEquals(setOf(SMS, DEPOSITO, CORREO), bandeja.first { it.id == SMS }.miembrosDelGrupo.toSet())
        assertNull(bandeja.first { it.id == "notif_9688c4127249c00e" }.grupoId)
        // Y no se ofrece anotarlos igual: lo que no pasó no se anota.
        val r = cliente().get("/api/sms/notif_9688c4127249c00e/parse?anotarloIgual=1") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.UnprocessableEntity, r.status)
        assertNull(Json { ignoreUnknownKeys = true }.decodeFromString(NoSePudoLeerElAviso.serializer(), r.bodyAsText()).sinLeer)
        assertEquals(SMS_STATE_PENDING, transaction { SmsMessages.selectAll().where { SmsMessages.id eq SMS }.single()[SmsMessages.state] })
    }

    companion object {
        private const val B0031 = "acc-0031"
        private const val B8133 = "acc-8133"
        private const val NU = "acc-nu"
        private const val NU_TARJETA = "acc-nu-tarjeta"
        private const val SMS = "sms_rt_e55d9f956c1cf678"
        private const val DEPOSITO = "notif_c776966e525d9eb8"
        private const val CORREO = "correo_86bbaeea25e16200"
        private const val PAGO_NU = "notif_0be8df4ec3619a4e"

        // Los textos reales del 31-oct-2026 (sms_messages de producción), sin el nombre del dueño.
        const val SMS_DE_SALIDA = "Bancolombia: Pagaste \$1,500,000.00 a NU Compania de Financiamiento desde tu producto 8133 el 31/10/2026 11:12:19. ¿Dudas? Llamanos al 6045109095. Estamos cerca"
        const val DEPOSITO_DE_NU = "Recibimos tu depósito por 1.500.000,00: ¡No te preocupes! En unas horas podrás revisar la actualización en tu app."
        const val PAGO_TARJETA_NU = "¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por \$1.306.937,91. En un rato podrás verlo en tu app."
        const val PAGO_TARJETA_NU_150 = "¡Bravo! Pagaste tu tarjeta de crédito Nu: Recibimos tu pago por \$150.000,00. En un rato podrás verlo en tu app."
        const val NU_NO_APROBADO = "¡Uy! Algo salió mal con tu pago: Tu pago por \$1.456.937,91 no fue aprobado. Vuelve a tu app e inténtalo de nuevo."
        const val CORREO_DE_PSE = "PSE - Transacción Aprobada ✅ CUS 787758754: [https://ui.pse.com.co/ui/images/email/Header_tran.png]\n\n" +
            "¡Hola, Dueño de Prueba!\n[https://ui.pse.com.co/ui/images/email/estado_aprobada.png]\n" +
            "[https://ui.pse.com.co/ui/images/email/aprobada1-1_tran.png]\n Los siguientes son los datos de tu transacción:\n\n" +
            "Valor: \$ 1.500.000,00 Empresa: NU Compañía de Financiamiento Descripción:\n" +
            "Depósito a tu cuenta NU Fecha de la transacción: 31/10/2026 CUS: 787758754\n" +
            "Gracias por utilizar nuestro servicio.\n\n      Ten encuenta estos tips de seguridad:\n" +
            "[https://ui.pse.com.co/ui/images/email/tips1.png] Usa dispositivos personales o\nde confianza para realizar tus pagos."
        const val CORREO_RECHAZADO = "PSE - Transacción Rechazada ❌ CUS 787564735: [https://ui.pse.com.co/ui/images/email/Header_tran.png]\n\n" +
            "¡Hola, Dueño de Prueba!\n[https://ui.pse.com.co/ui/images/email/estado_rechazada.png]\n" +
            "[https://ui.pse.com.co/ui/images/email/rechazada1-1_tran.png]\n Los siguientes son los datos de tu transacción:\n\n" +
            "Valor: \$ 1.456.937,91 Empresa: NU Compañía de Financiamiento Descripción: Pago\n" +
            "tarjeta Nu Fecha de la transacción: 31/10/2026 CUS: 787564735 Gracias por\nutilizar nuestro servicio.\n"
    }
}
