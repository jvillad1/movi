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
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.DosPatasDelAviso
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.MismoPagoConfirmado
import com.jvillada.movi.shared.model.OperacionDelAviso
import com.jvillada.movi.shared.model.ParteDelReparto
import com.jvillada.movi.shared.model.PartesDelAviso
import com.jvillada.movi.shared.model.PropuestaDeReparto
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.QueFueLaParte
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.SMS_STATE_CONFIRMED
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.repartoDeEntrada
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
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * # Un aviso que pagó varias cosas (vuelta 27)
 *
 * El caso del dueño, con cifras de prueba: un giro de $4.273.333 a la cuenta del papá cubre la cuota
 * de la Libranza ($1.900.000) y la de la Hipoteca ($2.350.000), y sobran $23.333 que van al papá.
 * Se confirma con `POST /api/sms/{id}/confirm` y una lista de partes; la forma de una sola parte sigue
 * igual. Y un giro que ya se anotó como un solo gasto se reparte después.
 */
class ElAvisoEnPartesTest {

    private val testSecret = "test-secret-for-el-aviso-en-partes-min-32-chars"
    private val issuer = "movi"
    private val audience = "movi-client"
    private val uid = "user-partes"
    private val otro = "user-otro-partes"

    private val ahorros = "acc-ahorros-8133"
    private val nu = "acc-nu"
    private val libranza = "acc_libranza_prueba"
    private val hipoteca = "acc_hipoteca_prueba"
    private val creditoDelOtro = "acc-credito-del-otro"
    private val papa = "dst_papa"

    private val hoy: LocalDate = AppClock.today()
    private val diaDelTexto = hoy.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    private val hora: String = java.time.LocalTime.now(AppClock.zone).let { ahora ->
        if (ahora.isAfter(java.time.LocalTime.of(0, 20))) ahora.minusMinutes(10) else java.time.LocalTime.MIDNIGHT
    }.format(DateTimeFormatter.ofPattern("HH:mm"))
    private val cuotaLibranza = 1_900_000L
    private val cuotaHipoteca = 2_350_000L
    private val giro = 4_273_333L
    private val sobra = 23_333L
    private val saldoInicial = 50_000_000L

    private val alPapa = "Bancolombia: Transferiste \$4.273.333 desde tu cuenta *8133 a la cuenta *036200170136 el $diaDelTexto a las 10:00."
    private val alPapaConSaldo = "Bancolombia: Transferiste \$4.273.333 desde tu cuenta *8133 a la cuenta *036200170136 el $diaDelTexto a las 11:00. Saldo disponible \$45.726.667"
    private val momento = appDateToEpochMillis(hoy) + 10 * 3_600_000L

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:el_aviso_en_partes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        val tablas = arrayOf(
            Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, OccurrenceRejections,
            Credits, Cards, SmsMessages, PushSubscriptions, KnownDestinations, DestinosDescartados,
            com.jvillada.movi.server.db.AnomaliasDescartadas,
        )
        transaction {
            SchemaUtils.create(*tablas)
            SchemaUtils.drop(*tablas.reversedArray())
            SchemaUtils.create(*tablas)
            Users.insert { it[id] = uid; it[email] = "partes@movi.test"; it[name] = "Dueño"; it[passwordHash] = "hash" }
            Users.insert { it[id] = otro; it[email] = "otro-partes@movi.test"; it[name] = "Otro"; it[passwordHash] = "hash" }
            cuenta(ahorros, "Bancolombia Ahorros 8133", "SAVINGS")
            cuenta(nu, "Nu", "SAVINGS")
            cuenta(libranza, "Libranza Papá", "LOAN")
            cuenta(hipoteca, "Hipoteca Papá", "LOAN")
            cuenta(creditoDelOtro, "Crédito del otro", "LOAN", dueno = otro)
            credito(libranza, "Banco A", cuotaLibranza, 15.0, destino = papa)
            credito(hipoteca, "Banco B", cuotaHipoteca, 12.0, destino = papa)
            credito(creditoDelOtro, "Otro banco", 1_000_000L, 12.0, dueno = otro)
            evento("ev-saldo", ahorros, "INCOME", saldoInicial, "Saldo inicial", hoy.minusMonths(6))
            evento("ev-deuda-libranza", libranza, "EXPENSE", 90_000_000L, "Saldo inicial", hoy.minusMonths(6))
            evento("ev-deuda-hipoteca", hipoteca, "EXPENSE", 190_000_000L, "Saldo inicial", hoy.minusMonths(6))
            KnownDestinations.insert {
                it[KnownDestinations.id] = papa
                it[userId] = uid
                it[nombre] = "Papá"
                it[numero] = "036200170136"
                it[createdAt] = 0L
            }
            aviso("sms_papa", alPapa)
            aviso("sms_del_otro", alPapa, dueno = otro)
        }
    }

    private fun cuenta(id: String, nombre: String, tipo: String, dueno: String = uid) {
        Accounts.insert { it[Accounts.id] = id; it[userId] = dueno; it[name] = nombre; it[type] = tipo }
    }

    private fun credito(cuenta: String, banco: String, cuota: Long, tasa: Double, destino: String? = null, dueno: String = uid) {
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
            it[destinoConocidoId] = destino
        }
    }

    private fun evento(id: String, cuenta: String, tipo: String, monto: Long, categoria: String, fecha: LocalDate, raw: String? = null) {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[category] = categoria
            it[description] = categoria
            it[timestamp] = appDateToEpochMillis(fecha) + 10 * 3_600_000L
            it[eventSource] = if (raw != null) "SMS" else "MANUAL"
            it[rawPayload] = raw
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun aviso(id: String, texto: String, dueno: String = uid, estado: String = SMS_STATE_PENDING, eventoId: String? = null) {
        SmsMessages.insert {
            it[SmsMessages.id] = id
            it[userId] = dueno
            it[time] = "$hoy $hora"
            it[bank] = "85540"
            it[text] = texto
            it[state] = estado
            it[det] = ""
            it[SmsMessages.eventoId] = eventoId
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

    private suspend fun ApplicationTestBuilder.confirmar(id: String, cuerpo: Any, quien: String = uid): HttpResponse =
        cliente().post("/api/sms/$id/confirm") {
            header(HttpHeaders.Authorization, "Bearer ${token(quien)}")
            contentType(ContentType.Application.Json)
            when (cuerpo) {
                is PartesDelAviso -> setBody(cuerpo)
                is DosPatasDelAviso -> setBody(cuerpo)
                else -> error("cuerpo desconocido")
            }
        }

    private suspend fun ApplicationTestBuilder.reparto(id: String): PropuestaDeReparto =
        cliente().get("/api/sms/$id/reparto") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body()

    private fun patas(op: OperacionDelAviso, destino: String, monto: Long, clave: String, origen: String = ahorros) = DosPatasDelAviso(
        operacion = op, origenId = origen, destinoId = destino, monto = monto, timestamp = momento,
        transferId = "tr-$clave", origenEventId = "ev-$clave-sale", destinoEventId = "ev-$clave-entra", nota = clave,
    )

    private fun cuota(deuda: String, monto: Long, clave: String, conPago: Boolean = false, origen: String = ahorros) = ParteDelReparto(
        ruleId = if (conPago) "$CREDIT_RULE_PREFIX$deuda" else "",
        periodo = if (conPago) hoy.toString().take(7) else "",
        monto = monto,
        tipo = QueFueLaParte.CUOTA,
        patas = patas(OperacionDelAviso.CUOTA, deuda, monto, clave, origen),
    )

    private fun alTercero(monto: Long, clave: String, texto: String = alPapa, cuenta: String = ahorros) = ParteDelReparto(
        monto = monto,
        tipo = QueFueLaParte.A_UN_TERCERO,
        evento = FinancialEvent(
            id = "ev-$clave", accountId = cuenta, type = TransactionType.EXPENSE, amount = monto,
            category = "Familia", description = "Transferencia a Papá", merchant = "Transferencia a Papá",
            timestamp = momento, source = EventSource.SMS, rawPayload = texto, reconciliationStatus = ReconciliationStatus.RECONCILED,
        ),
    )

    private fun lasTres(texto: String = alPapa) = PartesDelAviso(
        listOf(cuota(libranza, cuotaLibranza, "libranza"), cuota(hipoteca, cuotaHipoteca, "hipoteca"), alTercero(sobra, "papa", texto)),
    )

    private fun movimientos(): List<Triple<String, String, Long>> = transaction {
        Events.selectAll().where { Events.userId eq uid }
            .map { Triple(it[Events.id], it[Events.accountId], it[Events.amount]) }
            .filterNot { it.first.startsWith("ev-saldo") || it.first.startsWith("ev-deuda") }
    }

    private fun anulados(): Set<String> = transaction {
        VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
    }

    /** El saldo de Ahorros: lo que entró menos lo que salió, sin anulados. */
    private fun saldoDeAhorros(): Long = transaction {
        val fuera = anulados()
        Events.selectAll().where { (Events.userId eq uid) and (Events.accountId eq ahorros) }
            .filter { it[Events.id] !in fuera }
            .sumOf { if (it[Events.type] == "INCOME") it[Events.amount] else -it[Events.amount] }
    }

    private fun estado(id: String): Pair<String, String?> = transaction {
        SmsMessages.selectAll().where { SmsMessages.id eq id }.first().let { it[SmsMessages.state] to it[SmsMessages.eventoId] }
    }

    @Test
    fun `Movi propone las dos cuotas y lo que sobra, y la fila no lo anota sola`() = testApplication {
        application { testModule() }
        val propuesta = reparto("sms_papa")
        assertEquals(giro, propuesta.monto)
        val entrada = assertNotNull(repartoDeEntrada(propuesta, ahorros, "Transferencia a Papá", "Familia"))
        assertEquals(sobra, entrada.sobrante)
        assertEquals(setOf(libranza, hipoteca), entrada.partes.filter { it.tipo == QueFueLaParte.CUOTA }.map { it.destinoId }.toSet())
        // Nada se confirma solo: la bandeja marca el aviso para repartir en Reconciliar.
        val bandeja = cliente().get("/api/sms/propuestas") { header(HttpHeaders.Authorization, "Bearer ${token()}") }.body<PropuestasDeLaBandeja>()
        assertTrue(bandeja.propuestas.single { it.avisoId == "sms_papa" }.seReparte)
        assertEquals(SMS_STATE_PENDING to null, estado("sms_papa"))
        assertTrue(movimientos().isEmpty())
    }

    @Test
    fun `dos cuotas y un sobrante crean sus patas, tildan las cuotas y el origen baja exactamente el giro`() = testApplication {
        application { testModule() }
        val respuesta = confirmar("sms_papa", lasTres())
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val hecho = respuesta.body<MismoPagoConfirmado>()
        assertTrue(hecho.creado)
        assertEquals(
            listOf("ev-libranza-sale", "ev-libranza-entra", "ev-hipoteca-sale", "ev-hipoteca-entra", "ev-papa"),
            hecho.patas,
        )
        // evento_id: la pata del dinero de la primera parte. Todas en partes_de_avisos.
        assertEquals(SMS_STATE_CONFIRMED to "ev-libranza-sale", estado("sms_papa"))
        val enlazadas = transaction { partesEnlazadas(uid, "sms_papa") }
        assertEquals(hecho.patas, enlazadas)

        // Cada cuota: sale de Ahorros por la cuota entera y entra a su crédito (por el capital: el
        // interés lo separa `pagoDeCuotaLegs`, como en una cuota sola). Lo que sobra, al papá.
        val porId = movimientos().associateBy { it.first }
        assertEquals(ahorros to cuotaLibranza, porId.getValue("ev-libranza-sale").let { it.second to it.third })
        assertEquals(ahorros to cuotaHipoteca, porId.getValue("ev-hipoteca-sale").let { it.second to it.third })
        assertEquals(libranza, porId.getValue("ev-libranza-entra").second)
        assertEquals(hipoteca, porId.getValue("ev-hipoteca-entra").second)
        assertEquals(ahorros to sobra, porId.getValue("ev-papa").let { it.second to it.third })
        // Del lado de Ahorros: tres salidas, misma fecha, y suman el giro.
        val salidas = transaction {
            Events.selectAll().where { (Events.userId eq uid) and (Events.accountId eq ahorros) and (Events.type eq "EXPENSE") }
                .map { it[Events.amount] to it[Events.timestamp] }
        }
        assertEquals(3, salidas.size)
        assertEquals(giro, salidas.sumOf { it.first })
        assertEquals(setOf(momento), salidas.map { it.second }.toSet())
        assertEquals(saldoInicial - giro, saldoDeAhorros())
        val categorias = transaction {
            Events.selectAll().where { (Events.userId eq uid) and (Events.id eq "ev-libranza-sale") }.single()[Events.category]
        }
        assertEquals(CUOTA_CATEGORY, categorias)

        // Las dos cuotas quedaron saldadas en el checklist del período: ya no se proponen.
        transaction { aviso("sms_papa_otra_vez", alPapa.replace("10:00", "12:00")) }
        val despues = reparto("sms_papa_otra_vez")
        assertTrue(despues.pagos.none { it.ruleId == "$CREDIT_RULE_PREFIX$libranza" || it.ruleId == "$CREDIT_RULE_PREFIX$hipoteca" })
    }

    @Test
    fun `una suma distinta da 422 y no escribe nada`() = testApplication {
        application { testModule() }
        val corta = PartesDelAviso(listOf(cuota(libranza, cuotaLibranza, "libranza"), cuota(hipoteca, cuotaHipoteca, "hipoteca")))
        val respuesta = confirmar("sms_papa", corta)
        assertEquals(HttpStatusCode.UnprocessableEntity, respuesta.status)
        assertTrue(movimientos().isEmpty())
        assertEquals(SMS_STATE_PENDING to null, estado("sms_papa"))
        // Y de más, tampoco.
        val larga = PartesDelAviso(lasTres().partes.dropLast(1) + alTercero(sobra + 1, "papa"))
        assertEquals(HttpStatusCode.UnprocessableEntity, confirmar("sms_papa", larga).status)
        assertTrue(movimientos().isEmpty())
    }

    @Test
    fun `cada parte se valida como una sola - misma cuenta, lo que dice ser, y un avance no`() = testApplication {
        application { testModule() }
        // Una parte que sale de otra cuenta.
        val deNu = PartesDelAviso(listOf(cuota(libranza, cuotaLibranza, "libranza"), cuota(hipoteca, cuotaHipoteca, "hipoteca"), alTercero(sobra, "papa", cuenta = nu)))
        assertEquals(HttpStatusCode.UnprocessableEntity, confirmar("sms_papa", deNu).status)
        // Una parte que dice ser un gasto y trae dos patas.
        val mal = PartesDelAviso(listOf(cuota(libranza, cuotaLibranza, "libranza").copy(tipo = QueFueLaParte.GASTO), cuota(hipoteca, cuotaHipoteca, "hipoteca"), alTercero(sobra, "papa")))
        assertEquals(HttpStatusCode.UnprocessableEntity, confirmar("sms_papa", mal).status)
        // Un traspaso a un crédito no encaja (es una cuota): lo dice validarDosPatas.
        val traspasoAlCredito = PartesDelAviso(
            listOf(
                ParteDelReparto(monto = cuotaLibranza, patas = patas(OperacionDelAviso.TRASPASO, libranza, cuotaLibranza, "t")),
                cuota(hipoteca, cuotaHipoteca, "hipoteca"), alTercero(sobra, "papa"),
            ),
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, confirmar("sms_papa", traspasoAlCredito).status)
        assertTrue(movimientos().isEmpty())
        assertEquals(SMS_STATE_PENDING to null, estado("sms_papa"))
    }

    @Test
    fun `reintentar el mismo confirm no duplica nada y contesta con todas las partes`() = testApplication {
        application { testModule() }
        val primero = confirmar("sms_papa", lasTres()).body<MismoPagoConfirmado>()
        val antes = movimientos()
        val segundo = confirmar("sms_papa", lasTres())
        assertEquals(HttpStatusCode.OK, segundo.status)
        val otraVez = segundo.body<MismoPagoConfirmado>()
        assertEquals(false, otraVez.creado)
        assertEquals(primero.eventoId, otraVez.eventoId)
        assertEquals(primero.patas, otraVez.patas)
        assertEquals(antes, movimientos())
        assertEquals(saldoInicial - giro, saldoDeAhorros())
    }

    @Test
    fun `una sola parte con la forma vieja sigue funcionando`() = testApplication {
        application { testModule() }
        transaction { aviso("sms_cuota_sola", "Bancolombia: Transferiste \$1.900.000 desde tu cuenta *8133 a la cuenta *036200170136 el $diaDelTexto a las 09:00.") }
        val respuesta = confirmar("sms_cuota_sola", patas(OperacionDelAviso.CUOTA, libranza, cuotaLibranza, "sola"))
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val hecho = respuesta.body<MismoPagoConfirmado>()
        assertEquals("ev-sola-sale", hecho.eventoId)
        assertEquals(setOf("ev-sola-sale", "ev-sola-entra"), hecho.patas.toSet())
        assertEquals(SMS_STATE_CONFIRMED to "ev-sola-sale", estado("sms_cuota_sola"))
        assertTrue(transaction { partesEnlazadas(uid, "sms_cuota_sola") }.isEmpty())
    }

    @Test
    fun `el cuadre por aviso sigue funcionando con las partes`() = testApplication {
        application { testModule() }
        transaction {
            SmsMessages.deleteWhere { SmsMessages.id eq "sms_papa" }
            aviso("sms_papa_saldo", alPapaConSaldo)
        }
        // El aviso dice el saldo después del giro: con las tres partes, Movi dice lo mismo que el banco.
        assertEquals(HttpStatusCode.OK, confirmar("sms_papa_saldo", lasTres(alPapaConSaldo)).status)
        val cuadres = transaction { cuadresPorAvisoDe(uid) }
        assertTrue(cuadres.none { it.cuentaId == ahorros }, "no hay nada que cuadrar: $cuadres")
    }

    @Test
    fun `el reparto se recuerda y el mes que viene se propone el mismo`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.OK, confirmar("sms_papa", lasTres()).status)
        // La memoria es el aviso mismo: guarda cómo se repartió.
        val guardado = transaction { SmsMessages.selectAll().where { SmsMessages.id eq "sms_papa" }.single()[SmsMessages.reparto] }
        assertTrue(guardado != null && ahorros in guardado)

        // Otro giro parecido al papá: viene con el reparto de la última vez.
        transaction { aviso("sms_papa_siguiente", "Bancolombia: Transferiste \$4.280.000 desde tu cuenta *8133 a la cuenta *036200170136 el $diaDelTexto a las 15:00.") }
        val propuesta = reparto("sms_papa_siguiente")
        val recordado = assertNotNull(propuesta.recordado)
        assertEquals(listOf(QueFueLaParte.CUOTA, QueFueLaParte.CUOTA, QueFueLaParte.A_UN_TERCERO), recordado.partes.map { it.tipo })
        val entrada = assertNotNull(repartoDeEntrada(propuesta, ahorros, null, null))
        assertEquals(listOf(cuotaLibranza, cuotaHipoteca, 4_280_000L - cuotaLibranza - cuotaHipoteca), entrada.partes.map { it.monto })
        // Nada se confirmó solo.
        assertEquals(SMS_STATE_PENDING to null, estado("sms_papa_siguiente"))
    }

    @Test
    fun `aislamiento - ni el aviso ni el credito de otro usuario`() = testApplication {
        application { testModule() }
        // El aviso de otro usuario no se reparte.
        assertEquals(HttpStatusCode.NotFound, confirmar("sms_del_otro", lasTres()).status)
        // Una parte hacia el crédito de otro usuario no se escribe, y deshace lo que ya se había escrito.
        val ajena = PartesDelAviso(listOf(cuota(libranza, cuotaLibranza, "libranza"), cuota(creditoDelOtro, cuotaHipoteca, "ajena"), alTercero(sobra, "papa")))
        assertEquals(HttpStatusCode.NotFound, confirmar("sms_papa", ajena).status)
        assertTrue(movimientos().isEmpty())
        assertEquals(SMS_STATE_PENDING to null, estado("sms_papa"))
        // Y el otro no ve el reparto de este.
        confirmar("sms_papa", lasTres())
        val respuesta = cliente().get("/api/sms/sms_del_otro/reparto") { header(HttpHeaders.Authorization, "Bearer ${token(otro)}") }.body<PropuestaDeReparto>()
        assertEquals(null, respuesta.recordado)
    }

    // ── Punto 3: un movimiento ya anotado ────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.repartirElMovimiento(id: String, partes: PartesDelAviso, quien: String = uid): HttpResponse =
        cliente().post("/api/events/$id/repartir") {
            header(HttpHeaders.Authorization, "Bearer ${token(quien)}")
            contentType(ContentType.Application.Json)
            setBody(partes)
        }

    @Test
    fun `un giro ya anotado como un gasto se reparte - se anula y quedan las partes`() = testApplication {
        application { testModule() }
        transaction {
            evento("ev-giro-anotado", ahorros, "EXPENSE", giro, "Familia", hoy, raw = alPapa)
            aviso("sms_ya_confirmado", alPapa.replace("10:00", "08:00"), estado = SMS_STATE_CONFIRMED, eventoId = "ev-giro-anotado")
        }
        assertEquals(saldoInicial - giro, saldoDeAhorros())

        // Si no cuadra, nada cambia (tampoco la anulación).
        val corta = PartesDelAviso(lasTres().partes.dropLast(1))
        assertEquals(HttpStatusCode.UnprocessableEntity, repartirElMovimiento("ev-giro-anotado", corta).status)
        assertTrue("ev-giro-anotado" !in anulados())

        val respuesta = repartirElMovimiento("ev-giro-anotado", lasTres())
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val hecho = respuesta.body<MismoPagoConfirmado>()
        assertEquals("ev-libranza-sale", hecho.eventoId)
        assertTrue("ev-giro-anotado" in anulados())
        assertEquals(saldoInicial - giro, saldoDeAhorros(), "el saldo no cambia: el giro ahora está en partes")
        // El aviso queda con la primera parte, y con todas.
        assertEquals(SMS_STATE_CONFIRMED to "ev-libranza-sale", estado("sms_ya_confirmado"))
        assertEquals(hecho.patas, transaction { partesEnlazadas(uid, "sms_ya_confirmado") })

        // Reintentar no duplica.
        val antes = movimientos()
        assertEquals(HttpStatusCode.OK, repartirElMovimiento("ev-giro-anotado", lasTres()).status)
        assertEquals(antes, movimientos())
    }

    @Test
    fun `un movimiento de otro usuario no se reparte`() = testApplication {
        application { testModule() }
        transaction { evento("ev-giro-anotado", ahorros, "EXPENSE", giro, "Familia", hoy, raw = alPapa) }
        assertEquals(HttpStatusCode.NotFound, repartirElMovimiento("ev-giro-anotado", lasTres(), quien = otro).status)
        assertTrue(anulados().isEmpty())
    }
}
