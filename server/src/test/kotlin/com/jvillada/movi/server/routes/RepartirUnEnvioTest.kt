package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.correo.textoDePse
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
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.ConfirmarElMismoPago
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.PagoParaRepartir
import com.jvillada.movi.shared.model.ParteDelReparto
import com.jvillada.movi.shared.model.PropuestaDeReparto
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.TransactionType
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Repartir un envío entre varios pagos fijos
 *
 * El caso del papá: un giro de $4.280.000 a su cuenta es exactamente la cuota de la Libranza Papá
 * (BBVA, $2.051.848) más la de la Hipoteca Papá (Davibank, $2.228.152), créditos suyos que él paga y
 * que asoció al papá. Y el de Caro con dos recurrentes comunes (el mercado y la mesada de la tía).
 * Los SMS llevan su propia fecha en el texto (el dedupe de #444).
 */
class RepartirUnEnvioTest {

    private val testSecret = "test-secret-for-repartir-un-envio-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val uid = "user-reparto"
    private val otro = "user-otro-reparto"

    private val ahorros = "acc-ahorros-8133"
    private val libranza = "acc_libranza_papa_bbva"
    private val hipoteca = "acc_hipoteca_papa_davibank"
    private val skandia = "acc_hipoteca_1254"
    private val creditoDelOtro = "acc-credito-del-otro"
    private val papa = "dst_papa"
    private val caro = "dst_caro"
    private val papaDelOtro = "dst_papa_del_otro"

    private val hoy: LocalDate = AppClock.today()
    private val diaDelTexto = hoy.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    /**
     * Una hora de hoy que ya pasó: un aviso con la hora en el futuro no se junta con otro (la bandeja
     * lo fecha «ahora»), y el caso del SMS con el correo de PSE necesita que se junten.
     */
    private val hora: String = java.time.LocalTime.now(AppClock.zone).let { ahora ->
        if (ahora.isAfter(java.time.LocalTime.of(0, 20))) ahora.minusMinutes(10) else java.time.LocalTime.MIDNIGHT
    }.format(DateTimeFormatter.ofPattern("HH:mm"))
    private val cuotaBbva = 2_051_848L
    private val cuotaDavibank = 2_228_152L

    private val alPapa = "Bancolombia: Transferiste \$4.280.000 desde tu cuenta *8133 a la cuenta *036200170136 el $diaDelTexto a las 10:00."
    private val alPapaDeMas = "Bancolombia: Transferiste \$4.300.000 desde tu cuenta *8133 a la cuenta *036200170136 el $diaDelTexto a las 18:00."
    private val aCaro = "Bancolombia: Transferiste \$2.500.000 desde tu cuenta *8133 a la cuenta *31973270756 el $diaDelTexto a las 12:00."

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:repartir_un_envio_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            Users.insert { it[id] = uid; it[email] = "reparto@movi.test"; it[name] = "Dueño"; it[passwordHash] = "hash" }
            Users.insert { it[id] = otro; it[email] = "otro@movi.test"; it[name] = "Otro"; it[passwordHash] = "hash" }
            cuenta(ahorros, "Bancolombia Ahorros 8133 · 4057", "SAVINGS")
            cuenta(libranza, "Libranza Papá (BBVA)", "LOAN")
            cuenta(hipoteca, "Hipoteca Papá (Davibank)", "LOAN")
            cuenta(skandia, "Hipoteca 1254", "LOAN")
            cuenta(creditoDelOtro, "Crédito del otro", "LOAN", dueno = otro)
            credito(libranza, "BBVA", cuotaBbva, 15.85, destino = papa)
            credito(hipoteca, "Davibank", cuotaDavibank, 12.89, destino = papa)
            // La paga Skandia: la regla de plata de «¿Quién paga esta cuota?». No es un pago del dueño.
            credito(skandia, "Davibank", 9_148_284L, 10.99, destino = papa, pagaOtro = "Skandia")
            credito(creditoDelOtro, "Otro banco", 1_000_000L, 12.0, dueno = otro)
            evento("ev-saldo", ahorros, "INCOME", 50_000_000L, "Saldo inicial", hoy.minusMonths(6))
            evento("ev-deuda-bbva", libranza, "EXPENSE", 97_718_920L, "Saldo inicial", hoy.minusMonths(6))
            evento("ev-deuda-davibank", hipoteca, "EXPENSE", 198_223_917L, "Saldo inicial", hoy.minusMonths(6))
            evento("ev-deuda-1254", skandia, "EXPENSE", 300_000_000L, "Saldo inicial", hoy.minusMonths(6))
            destino(papa, "Papá", "036200170136")
            destino(caro, "Caro", "31973270756")
            destino(papaDelOtro, "Papá", "036200170136", dueno = otro)
            regla("rr-mercado-caro", "Mercado Caro", 2_000_000L, "Mercado", caro)
            regla("rr-tia", "Mesada tía", 500_000L, "Tía", caro)

            aviso("sms_papa", alPapa, "$hoy $hora")
            aviso("sms_papa_de_mas", alPapaDeMas, "$hoy $hora")
            aviso("sms_caro", aCaro, "$hoy $hora")
            aviso("sms_del_otro", alPapa, "$hoy $hora", dueno = otro)
        }
    }

    private fun cuenta(id: String, nombre: String, tipo: String, dueno: String = uid) {
        Accounts.insert { it[Accounts.id] = id; it[userId] = dueno; it[name] = nombre; it[type] = tipo }
    }

    private fun credito(cuenta: String, banco: String, cuota: Long, tasa: Double, destino: String? = null, pagaOtro: String? = null, dueno: String = uid) {
        Credits.insert {
            it[accountId] = cuenta
            it[userId] = dueno
            it[bank] = banco
            it[principal] = cuota * 100
            it[rateEa] = tasa
            it[termMonths] = 120
            it[installment] = cuota
            it[dayOfMonth] = hoy.dayOfMonth
            it[startDate] = hoy.minusYears(1).toString()
            it[paidBy] = pagaOtro
            it[destinoConocidoId] = destino
        }
    }

    private fun evento(id: String, cuenta: String, tipo: String, monto: Long, categoria: String, fecha: LocalDate) {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[category] = categoria
            it[description] = categoria
            it[timestamp] = appDateToEpochMillis(fecha)
            it[eventSource] = "MANUAL"
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun destino(id: String, nombre: String, numero: String, dueno: String = uid) {
        KnownDestinations.insert {
            it[KnownDestinations.id] = id
            it[userId] = dueno
            it[KnownDestinations.nombre] = nombre
            it[KnownDestinations.numero] = numero
            it[createdAt] = 0L
        }
    }

    private fun regla(id: String, nombre: String, monto: Long, categoria: String, destino: String) {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[userId] = uid
            it[name] = nombre
            it[amount] = monto
            it[category] = categoria
            it[dayOfMonth] = hoy.dayOfMonth
            it[type] = "EXPENSE"
            it[accountId] = ahorros
            it[destinoConocidoId] = destino
            it[activeFrom] = hoy.minusMonths(6).toString()
        }
    }

    private fun aviso(id: String, texto: String, hora: String, banco: String = "85540", dueno: String = uid) {
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = dueno
            it[time] = hora
            it[bank] = banco
            it[text] = texto
            it[state] = SMS_STATE_PENDING
            it[det] = ""
        }
    }

    private fun token(quien: String = uid): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", quien).withClaim("email", "$quien@movi.test")
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

    private suspend fun ApplicationTestBuilder.reparto(id: String, quien: String = uid): HttpResponse =
        cliente().get("/api/sms/$id/reparto") { header(HttpHeaders.Authorization, "Bearer ${token(quien)}") }

    private suspend fun ApplicationTestBuilder.repartir(
        grupo: String,
        miembros: List<String>,
        partes: List<ParteDelReparto>,
        recordar: String? = null,
    ): HttpResponse = cliente().post("/api/sms/grupo/$grupo/confirmar") {
        header(HttpHeaders.Authorization, "Bearer ${token()}")
        contentType(ContentType.Application.Json)
        setBody(ConfirmarElMismoPago(miembros, reparto = partes, recordarDestinoId = recordar))
    }

    private fun cuota(deuda: String, monto: Long, clave: String) = ParteDelReparto(
        ruleId = "$CREDIT_RULE_PREFIX$deuda",
        periodo = hoy.toString().take(7),
        monto = monto,
        patas = DosPatasDelAviso(
            operacion = OperacionDelAviso.CUOTA,
            origenId = ahorros,
            destinoId = deuda,
            monto = monto,
            timestamp = appDateToEpochMillis(hoy) + 10 * 3_600_000L,
            transferId = "tr-$clave",
            origenEventId = "ev-$clave-sale",
            destinoEventId = "ev-$clave-entra",
            nota = clave,
        ),
    )

    private fun recurrente(regla: String, monto: Long, categoria: String, nombre: String, texto: String) = ParteDelReparto(
        ruleId = regla,
        periodo = hoy.toString().take(7),
        monto = monto,
        evento = FinancialEvent(
            id = "ev-$regla", accountId = ahorros, type = TransactionType.EXPENSE, amount = monto,
            category = categoria, description = nombre, merchant = "Transferencia a Caro",
            timestamp = appDateToEpochMillis(hoy) + 12 * 3_600_000L, source = EventSource.SMS,
            rawPayload = texto, reconciliationStatus = ReconciliationStatus.RECONCILED,
        ),
    )

    private fun movimientosDeUid(): List<Triple<String, String, Long>> = transaction {
        Events.selectAll().where { Events.userId eq uid }
            .map { Triple(it[Events.id], it[Events.category], it[Events.amount]) }
            .filterNot { it.first.startsWith("ev-saldo") || it.first.startsWith("ev-deuda") }
    }

    private fun estado(id: String): Pair<String, String?> = transaction {
        SmsMessages.selectAll().where { SmsMessages.id eq id }.first().let { it[SmsMessages.state] to it[SmsMessages.eventoId] }
    }

    @Test
    fun `el giro al papa se propone solo como sus dos cuotas, y la que paga Skandia no esta`() = testApplication {
        application { testModule() }
        val propuesta = reparto("sms_papa").body<PropuestaDeReparto>()

        assertEquals(4_280_000L, propuesta.monto)
        assertEquals(papa, propuesta.destinoId)
        assertEquals(setOf("$CREDIT_RULE_PREFIX$libranza", "$CREDIT_RULE_PREFIX$hipoteca"), propuesta.exacto.toSet())
        val cuotas = propuesta.pagos.filter { it.ruleId in propuesta.exacto }
        assertTrue(cuotas.all { it.asociado && it.categoria == CUOTA_CATEGORY && it.cuentaDeLaDeudaId != null })
        // La hipoteca 1254 la paga Skandia: no es un pago del dueño y no se puede repartir ahí.
        assertTrue(propuesta.pagos.none { it.ruleId == "$CREDIT_RULE_PREFIX$skandia" })
        // Y la bandeja no ofrece «Confirmar» en la fila: se reparte en Reconciliar.
        val bandeja = cliente().get("/api/sms/propuestas") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body<PropuestasDeLaBandeja>()
        assertTrue(bandeja.propuestas.single { it.avisoId == "sms_papa" }.seReparte)
        assertTrue(!bandeja.propuestas.single { it.avisoId == "sms_papa_de_mas" }.seReparte)
    }

    @Test
    fun `una suma que no es exacta no se propone sola, pero se puede repartir a mano`() = testApplication {
        application { testModule() }
        val propuesta = reparto("sms_papa_de_mas").body<PropuestaDeReparto>()
        assertTrue(propuesta.exacto.isEmpty())
        assertTrue(propuesta.pagos.size >= 2)

        // A mano: la libranza toma su cuota y la hipoteca, la última, el resto.
        val respuesta = repartir(
            "sms_papa_de_mas", listOf("sms_papa_de_mas"),
            listOf(cuota(libranza, cuotaBbva, "bbva-mano"), cuota(hipoteca, 4_300_000L - cuotaBbva, "davibank-mano")),
        )
        assertEquals(HttpStatusCode.OK, respuesta.status)
        assertEquals(SMS_STATE_CONFIRMED to "ev-bbva-mano-sale", estado("sms_papa_de_mas"))
    }

    @Test
    fun `repartir el giro del papa crea una cuota de dos patas por credito y marca los dos pagos`() = testApplication {
        application { testModule() }
        val partes = listOf(cuota(libranza, cuotaBbva, "bbva"), cuota(hipoteca, cuotaDavibank, "davibank"))
        val primero = repartir("sms_papa", listOf("sms_papa"), partes)
        assertEquals(HttpStatusCode.OK, primero.status)
        val hecho = primero.body<MismoPagoConfirmado>()
        assertTrue(hecho.creado)
        assertEquals(setOf("ev-bbva-sale", "ev-bbva-entra", "ev-davibank-sale", "ev-davibank-entra"), hecho.patas.toSet())

        val movimientos = movimientosDeUid()
        assertEquals(4, movimientos.size)
        // La plata que salió de Ahorros: dos cuotas, cada una por lo suyo, y suman el giro.
        val salidas = transaction {
            Events.selectAll().where { (Events.userId eq uid) and (Events.accountId eq ahorros) and (Events.category eq CUOTA_CATEGORY) }
                .map { it[Events.amount] }
        }
        assertEquals(listOf(cuotaBbva, cuotaDavibank).sorted(), salidas.sorted())
        assertEquals(SMS_STATE_CONFIRMED to "ev-bbva-sale", estado("sms_papa"))

        // Los dos pagos del período quedaron saldados: ya no se proponen.
        val despues = reparto("sms_papa_de_mas").body<PropuestaDeReparto>()
        assertTrue(despues.pagos.none { it.ruleId == "$CREDIT_RULE_PREFIX$libranza" || it.ruleId == "$CREDIT_RULE_PREFIX$hipoteca" })

        // Un doble toque no crea nada más.
        val segundo = repartir("sms_papa", listOf("sms_papa"), partes).body<MismoPagoConfirmado>()
        assertEquals(false, segundo.creado)
        assertEquals(4, movimientosDeUid().size)
    }

    @Test
    fun `repartir entre recurrentes comunes crea un movimiento por pago con su categoria y sella cada uno`() = testApplication {
        application { testModule() }
        val propuesta = reparto("sms_caro").body<PropuestaDeReparto>()
        assertEquals(setOf("rr-mercado-caro", "rr-tia"), propuesta.exacto.toSet())

        val respuesta = repartir(
            "sms_caro", listOf("sms_caro"),
            listOf(
                recurrente("rr-mercado-caro", 2_000_000L, "Mercado", "Mercado Caro", aCaro),
                recurrente("rr-tia", 500_000L, "Tía", "Mesada tía", aCaro),
            ),
        )
        assertEquals(HttpStatusCode.OK, respuesta.status)
        assertEquals(
            setOf(Triple("ev-rr-mercado-caro", "Mercado", 2_000_000L), Triple("ev-rr-tia", "Tía", 500_000L)),
            movimientosDeUid().toSet(),
        )
        val sellos = transaction {
            RecurringOccurrences.selectAll().where { RecurringOccurrences.userId eq uid }
                .associate { it[RecurringOccurrences.ruleId] to it[RecurringOccurrences.eventId] }
        }
        assertEquals(mapOf("rr-mercado-caro" to "ev-rr-mercado-caro", "rr-tia" to "ev-rr-tia"), sellos)
    }

    @Test
    fun `si las partes no suman el aviso no se escribe nada`() = testApplication {
        application { testModule() }
        val respuesta = repartir("sms_papa", listOf("sms_papa"), listOf(cuota(libranza, cuotaBbva, "bbva"), cuota(hipoteca, 1_000L, "poco")))
        assertEquals(HttpStatusCode.UnprocessableEntity, respuesta.status)
        assertTrue(movimientosDeUid().isEmpty())
        assertEquals(SMS_STATE_PENDING to null, estado("sms_papa"))
    }

    @Test
    fun `la cuota que paga otro no sale del envio`() = testApplication {
        application { testModule() }
        val respuesta = repartir(
            "sms_papa", listOf("sms_papa"),
            listOf(cuota(libranza, cuotaBbva, "bbva"), cuota(skandia, 4_280_000L - cuotaBbva, "skandia")),
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, respuesta.status)
        assertTrue(movimientosDeUid().isEmpty())
    }

    @Test
    fun `aislamiento - ni el aviso, ni el credito, ni el tercero de otro usuario`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.NotFound, reparto("sms_del_otro").status)
        // Una parte hacia el crédito de otro usuario no se escribe, y deshace lo que ya se había escrito.
        val ajena = repartir(
            "sms_papa", listOf("sms_papa"),
            listOf(cuota(libranza, cuotaBbva, "bbva"), cuota(creditoDelOtro, cuotaDavibank, "ajeno")),
        )
        assertEquals(HttpStatusCode.NotFound, ajena.status)
        assertTrue(movimientosDeUid().isEmpty())
        // El aviso de otro usuario no se reparte.
        val avisoAjeno = repartir("sms_del_otro", listOf("sms_del_otro"), listOf(cuota(libranza, cuotaBbva, "x"), cuota(hipoteca, cuotaDavibank, "y")))
        assertEquals(HttpStatusCode.NotFound, avisoAjeno.status)
        // Recordar un tercero de otro usuario no lo guarda.
        repartir(
            "sms_caro", listOf("sms_caro"),
            listOf(recurrente("rr-mercado-caro", 2_000_000L, "Mercado", "Mercado Caro", aCaro), recurrente("rr-tia", 500_000L, "Tía", "Mesada tía", aCaro)),
            recordar = papaDelOtro,
        )
        val destinos = transaction { RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it[RecurringRules.destinoConocidoId] }.toSet() }
        assertEquals(setOf(caro), destinos)
    }

    @Test
    fun `repartir a mano puede recordar el tercero de los pagos que no lo tenian`() = testApplication {
        application { testModule() }
        transaction {
            Credits.update({ Credits.accountId eq hipoteca }) { it[destinoConocidoId] = null }
        }
        val antes = reparto("sms_papa").body<PropuestaDeReparto>()
        assertTrue(antes.exacto.isEmpty(), "sin la hipoteca asociada no hay suma exacta")
        repartir("sms_papa", listOf("sms_papa"), listOf(cuota(libranza, cuotaBbva, "bbva"), cuota(hipoteca, cuotaDavibank, "davibank")), recordar = papa)
        val asociado = transaction { Credits.selectAll().where { Credits.accountId eq hipoteca }.first()[Credits.destinoConocidoId] }
        assertEquals(papa, asociado)
    }

    @Test
    fun `el mismo giro avisado por SMS y por correo de PSE se reparte como un pago`() = testApplication {
        application { testModule() }
        transaction {
            aviso(
                "correo_pse_papa", textoDePse(valor = "\$ 4.280.000,00", empresa = "Bancolombia", descripcion = "Transferencia", fecha = diaDelTexto),
                "$hoy $hora", banco = "Correo · PSE",
            )
        }
        val bandeja = cliente().get("/api/sms/propuestas") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body<PropuestasDeLaBandeja>()
        val delGiro = bandeja.propuestas.single { it.avisoId in setOf("sms_papa", "correo_pse_papa") }
        val grupo = assertNotNull(delGiro.grupoId, "el SMS y el correo son el mismo pago")
        assertTrue(delGiro.seReparte)

        val propuesta = reparto("correo_pse_papa").body<PropuestaDeReparto>()
        assertEquals(4_280_000L, propuesta.monto)
        assertEquals(papa, propuesta.destinoId, "el tercero sale del SMS, el correo no dice la cuenta")
        assertEquals(2, propuesta.exacto.size)

        val hecho = repartir(grupo, listOf("sms_papa", "correo_pse_papa"), listOf(cuota(libranza, cuotaBbva, "bbva"), cuota(hipoteca, cuotaDavibank, "davibank")))
        assertEquals(HttpStatusCode.OK, hecho.status)
        assertEquals(SMS_STATE_CONFIRMED, estado("sms_papa").first)
        assertEquals(SMS_STATE_CONFIRMED, estado("correo_pse_papa").first)
        assertEquals(4, movimientosDeUid().size)
    }

    @Test
    fun `la suma exacta - dos o tres asociados, una sola combinacion`() {
        fun pago(id: String, monto: Long, asociado: Boolean = true) =
            PagoParaRepartir(id, id, monto, "x", "2026-10", "2026-10-27", asociado = asociado)
        val bbva = pago("bbva", cuotaBbva)
        val davibank = pago("davibank", cuotaDavibank)
        assertEquals(listOf(bbva, davibank), com.jvillada.movi.shared.model.repartoExacto(4_280_000L, listOf(bbva, davibank)))
        assertNull(com.jvillada.movi.shared.model.repartoExacto(4_300_000L, listOf(bbva, davibank)))
        // Uno solo no es un reparto.
        assertNull(com.jvillada.movi.shared.model.repartoExacto(cuotaBbva, listOf(bbva, davibank)))
        // Sin asociar al tercero, no se propone solo.
        assertNull(com.jvillada.movi.shared.model.repartoExacto(4_280_000L, listOf(bbva, davibank.copy(asociado = false))))
        // Tres.
        val tia = pago("tia", 100_000L)
        assertEquals(listOf(bbva, davibank, tia), com.jvillada.movi.shared.model.repartoExacto(4_380_000L, listOf(bbva, davibank, tia)))
        // Dos combinaciones que suman lo mismo: no adivina.
        assertNull(com.jvillada.movi.shared.model.repartoExacto(300L, listOf(pago("a", 100L), pago("b", 200L), pago("c", 200L))))
        // A mano, el último toma el resto.
        assertEquals(
            listOf(bbva to cuotaBbva, davibank to 4_300_000L - cuotaBbva),
            com.jvillada.movi.shared.model.repartoAMano(4_300_000L, listOf(bbva, davibank)),
        )
        assertNull(com.jvillada.movi.shared.model.repartoAMano(1_000_000L, listOf(bbva, davibank)))
    }
}
