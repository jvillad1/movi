package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DebitosDescartados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.ConfirmarDebitoAutomatico
import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GRACIA_DE_LO_ESPERADO_DIAS
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.OrigenDelDebito
import com.jvillada.movi.shared.model.SMS_STATE_PENDING
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.confirmacionDelDebito
import com.jvillada.movi.shared.model.preguntaDeLoEsperado
import com.jvillada.movi.shared.model.textoDeDebitosEnHoy
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 90 · preguntar por lo esperado que no llegó
 *
 * Una suscripción en la Master, la nómina: recurrentes **con cuenta** que no están marcados «se debita
 * solo». Si ya debieron pasar, llevan [GRACIA_DE_LO_ESPERADO_DIAS] días sin movimiento que los salde
 * (el emparejador de siempre) y ningún aviso pendiente los cubre, `GET /api/debitos-automaticos
 * ?conEsperados=1` los propone en el mismo bloque, con la misma escritura y los mismos ids. Sin el
 * parámetro —un APK viejo— no cambia nada.
 */
class LoEsperadoQueNoLlegoTest {

    private val testSecret = "test-secret-for-lo-esperado-que-no-llego-32"
    private val duenoId = "user-dueno-esp"
    private val json = Json { ignoreUnknownKeys = true }
    private val ahorros = "acc_ahorros_esp"
    private val master = "acc_master_esp"
    private val credito = "acc_loan_esp"
    private val netflix = 49_900L
    private val sueldo = 8_500_000L

    /** Netflix vence el 5 de octubre de 2026 (lunes). */
    private val dia5: LocalDate = LocalDate.of(2026, 10, 5)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:lo_esperado_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                DebitosDescartados, SmsMessages, KnownDestinations, OccurrenceRejections, RecurringOccurrences,
                RecurringRules, Cards, Credits, VoidEvents, Events, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, Events, VoidEvents, Credits, Cards, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, KnownDestinations, SmsMessages, DebitosDescartados,
            )
            Users.insert { it[id] = duenoId; it[email] = "$duenoId@esp.test"; it[name] = "Dueño"; it[passwordHash] = "h" }
            cuenta(ahorros, "Bancolombia Ahorros", "SAVINGS")
            cuenta(master, "Master Black", "CREDIT_CARD")
            cuenta(credito, "Libre inversión", "LOAN")
            // Plata en la cuenta, y una deuda viva para la cuota.
            evento("ev_apertura", ahorros, "INCOME", 20_000_000L, "Saldo inicial", LocalDate.of(2026, 1, 2), "Apertura")
            evento("ev_deuda", credito, "EXPENSE", 30_000_000L, "Deuda inicial", LocalDate.of(2026, 1, 2), "Deuda")
        }
    }

    private fun cuenta(id: String, nombre: String, tipo: String) = Accounts.insert {
        it[Accounts.id] = id; it[userId] = duenoId; it[name] = nombre; it[type] = tipo; it[currency] = "COP"; it[balance] = 0L
    }

    private fun evento(
        id: String,
        cuenta: String,
        tipo: String,
        monto: Long,
        categoria: String,
        fecha: LocalDate,
        descripcion: String,
    ) = Events.insert {
        it[Events.id] = id; it[userId] = duenoId; it[accountId] = cuenta; it[type] = tipo; it[amount] = monto
        it[currency] = "COP"; it[category] = categoria; it[description] = descripcion
        it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
        it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
    }

    private fun regla(
        id: String = "rr_netflix",
        nombre: String = "Netflix",
        categoria: String = "Suscripciones",
        monto: Long = netflix,
        dia: Int = 5,
        tipo: String = "EXPENSE",
        cuenta: String? = master,
        debita: Boolean? = false,
        haciaDeuda: String? = null,
    ) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id; it[userId] = duenoId; it[name] = nombre; it[category] = categoria
            it[amount] = monto; it[dayOfMonth] = dia; it[type] = tipo; it[accountId] = cuenta
            it[seDebitaSolo] = debita; it[RecurringRules.haciaDeuda] = haciaDeuda
        }
    }

    private fun propuestas(hoy: LocalDate, conEsperados: Boolean = true) =
        runBlocking { debitosPorConfirmar(duenoId, hoy, appDateToEpochMillis(hoy) + 12 * 3_600_000L, conEsperados) }

    private fun token() = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", duenoId).withClaim("email", "$duenoId@esp.test")
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

    private suspend fun ApplicationTestBuilder.confirmar(pedido: ConfirmarDebitoAutomatico): HttpResponse =
        client.post("/api/debitos-automaticos/confirmar") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ConfirmarDebitoAutomatico.serializer(), pedido))
        }

    private suspend fun ApplicationTestBuilder.descartar(d: DebitoAutomaticoPorConfirmar): HttpResponse =
        client.post("/api/debitos-automaticos/descartar") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody("""{"ruleId":"${d.ruleId}","periodo":"${d.periodo}"}""")
        }

    private fun eventos(): List<FinancialEvent> = transaction { loadEventsBetween(duenoId, 0L, Long.MAX_VALUE) }

    private fun ocurrenciasEn(hoy: LocalDate): List<OccurrenceState> = transaction {
        estadosDeLasOcurrenciasReales(duenoId, hoy, com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender(duenoId))
    }

    // ── Cuándo se pregunta ──────────────────────────────────────────────────────

    @Test
    fun `una suscripcion vencida sin movimiento se propone pasada la gracia, y no antes`() {
        regla()
        assertTrue(propuestas(dia5).isEmpty(), "el día que vence todavía no: el pago tarda en verse")
        assertTrue(propuestas(dia5.plusDays(GRACIA_DE_LO_ESPERADO_DIAS - 1L)).isEmpty(), "dentro de la gracia, nada")
        val d = propuestas(dia5.plusDays(GRACIA_DE_LO_ESPERADO_DIAS.toLong())).single()
        assertTrue(d.esperado)
        assertEquals(TransactionType.EXPENSE, d.tipo)
        assertEquals(OrigenDelDebito.RECURRENTE, d.origen)
        assertEquals("rr_netflix", d.ruleId)
        assertEquals("2026-10", d.periodo)
        assertEquals(netflix, d.monto)
        assertEquals(master, d.cuentaId, "un gasto esperado sale también de una tarjeta de crédito")
        assertEquals(dia5.toString(), d.vence)
        assertNull(d.ultimoMonto, "sin historia no se inventa una cifra")
        assertEquals("¿Se pagó Netflix?", preguntaDeLoEsperado(d))
        assertEquals("¿Se pagó Netflix?", textoDeDebitosEnHoy(listOf(d)))
    }

    @Test
    fun `sin conEsperados la lista es la de siempre`() {
        regla()
        assertTrue(propuestas(dia5.plusDays(3), conEsperados = false).isEmpty(), "un APK viejo no lo pide y no lo recibe")
    }

    @Test
    fun `con un movimiento que la salda por el nombre no se propone`() {
        regla()
        transaction { evento("ev_netflix", master, "EXPENSE", netflix, "Entretenimiento", dia5, "Netflix") }
        assertTrue(propuestas(dia5.plusDays(3)).isEmpty())
    }

    @Test
    fun `con un movimiento que la salda por categoria, cuenta y monto no se propone`() {
        regla()
        transaction { evento("ev_cobro", master, "EXPENSE", netflix, "Suscripciones", dia5, "NFLX*COM 866") }
        assertTrue(propuestas(dia5.plusDays(3)).isEmpty())
    }

    @Test
    fun `con el periodo sellado no se propone`() {
        regla()
        transaction {
            evento("ev_otro", ahorros, "EXPENSE", 50_000L, "Varios", dia5, "Pago raro")
            RecurringOccurrences.insert {
                it[userId] = duenoId; it[ruleId] = "rr_netflix"; it[period] = "2026-10"; it[eventId] = "ev_otro"
                it[confirmedAt] = 1L
            }
        }
        assertTrue(propuestas(dia5.plusDays(3)).isEmpty(), "el sello del dueño gana")
    }

    @Test
    fun `con un candidato que no alcanza, pregunta el checklist y no la propuesta`() {
        regla()
        // Misma cuenta y categoría, otro monto y otro nombre: candidato, no concluyente.
        transaction { evento("ev_raro", master, "EXPENSE", 61_900L, "Suscripciones", dia5, "Cobro internacional") }
        assertTrue(propuestas(dia5.plusDays(3)).isEmpty())
    }

    @Test
    fun `con un aviso pendiente que la cubre no se propone`() {
        regla()
        val hoy = dia5.plusDays(3)
        transaction {
            SmsMessages.insert {
                it[id] = "sms_netflix"; it[userId] = duenoId; it[time] = "2026-10-08 06:10"; it[bank] = "85540"
                it[text] = "Bancolombia: Compraste COP49.900,00 en NETFLIX con tu T.Cred *3684, el 08/10/2026 a las 06:10."
                it[state] = SMS_STATE_PENDING; it[det] = "NETFLIX"
            }
        }
        assertTrue(propuestas(hoy).isEmpty(), "el aviso es mejor evidencia: dos tarjetas para un pago no")
    }

    @Test
    fun `lo que no tiene cuenta, va a un credito o es un abono no se pregunta`() {
        regla(id = "rr_sin_cuenta", nombre = "Gimnasio", cuenta = null)
        regla(id = "rr_en_credito", nombre = "Seguro", cuenta = credito)
        regla(id = "rr_abono", nombre = "Abono extra", cuenta = ahorros, haciaDeuda = credito)
        // Un pago de tarjeta es un traspaso: no se anota como gasto.
        regla(id = "rr_pago_tc", nombre = "Pago Master", categoria = CARD_PAYMENT_CATEGORY, cuenta = ahorros)
        assertTrue(propuestas(dia5.plusDays(3)).isEmpty())
    }

    @Test
    fun `un debito marcado sigue sin gracia y no se marca como esperado`() {
        regla(id = "rr_seguro", nombre = "Seguro Sura", cuenta = ahorros, debita = true)
        val d = propuestas(dia5).single()
        assertEquals(false, d.esperado)
        assertEquals("¿Se cobró Seguro Sura?", preguntaDeLoEsperado(d))
    }

    // ── La nómina ───────────────────────────────────────────────────────────────

    @Test
    fun `la nomina se espera el dia que arranca su periodo, y se pregunta pasada la gracia`() {
        // Corte 25 con «es el día que me pagan»: el 25-oct-2026 es domingo, el período arranca el viernes 23.
        transaction {
            Users.update({ Users.id eq duenoId }) {
                it[periodCutoffDay] = 25
                it[periodWeekendRule] = "DIA_HABIL_ANTERIOR"
            }
        }
        regla(id = "rr_salario", nombre = "Salario", categoria = "Salario", monto = sueldo, dia = 25, tipo = "INCOME", cuenta = ahorros)
        assertTrue(propuestas(LocalDate.of(2026, 10, 24)).isEmpty(), "el 24 todavía está en la gracia del 23")
        val d = propuestas(LocalDate.of(2026, 10, 25)).single()
        assertEquals(TransactionType.INCOME, d.tipo)
        assertTrue(d.esperado)
        assertEquals("2026-10-23", d.vence, "se espera el viernes, no el domingo")
        assertEquals(ahorros, d.cuentaId)
        assertEquals("¿Te llegó Salario?", preguntaDeLoEsperado(d))
    }

    @Test
    fun `si la nomina ya llego por el emparejador no se pregunta`() {
        regla(id = "rr_salario", nombre = "Salario", categoria = "Salario", monto = sueldo, dia = 5, tipo = "INCOME", cuenta = ahorros)
        transaction { evento("ev_nomina", ahorros, "INCOME", 8_700_000L, "Salario", dia5, "Salario octubre") }
        assertTrue(propuestas(dia5.plusDays(3)).isEmpty())
    }

    @Test
    fun `con un monto que cambia, la propuesta trae el de la ultima vez`() {
        regla(id = "rr_salario", nombre = "Salario", categoria = "Salario", monto = sueldo, dia = 5, tipo = "INCOME", cuenta = ahorros)
        transaction {
            evento("ev_nomina_sep", ahorros, "INCOME", 8_731_000L, "Salario", LocalDate.of(2026, 9, 5), "Pago nómina")
            RecurringOccurrences.insert {
                it[userId] = duenoId; it[ruleId] = "rr_salario"; it[period] = "2026-09"; it[eventId] = "ev_nomina_sep"
                it[confirmedAt] = 1L
            }
        }
        val d = propuestas(dia5.plusDays(3)).single()
        assertEquals(sueldo, d.monto, "la propuesta sigue diciendo el de la regla")
        assertEquals(8_731_000L, d.ultimoMonto, "«Cambiar monto» abre con el de la última vez")
    }

    @Test
    fun `una regla vieja sin monto propone el de la ultima vez`() {
        regla(id = "rr_viejo", nombre = "Salario", categoria = "Salario", monto = 0L, dia = 5, tipo = "INCOME", cuenta = ahorros)
        transaction {
            evento("ev_sep", ahorros, "INCOME", 8_731_000L, "Salario", LocalDate.of(2026, 9, 5), "Pago nómina")
            RecurringOccurrences.insert {
                it[userId] = duenoId; it[ruleId] = "rr_viejo"; it[period] = "2026-09"; it[eventId] = "ev_sep"; it[confirmedAt] = 1L
            }
        }
        assertEquals(8_731_000L, propuestas(dia5.plusDays(3)).single().monto)
    }

    // ── Confirmar ───────────────────────────────────────────────────────────────

    @Test
    fun `si se pago anota el gasto una vez, tilda el periodo y no toca nada mas`() = testApplication {
        application { testModule() }
        regla()
        regla(id = "rr_arriendo", nombre = "Arriendo", categoria = "Vivienda", monto = 1_800_000L, dia = 20, cuenta = ahorros)
        val hoy = dia5.plusDays(3)
        val d = propuestas(hoy).single()
        val antes = eventos()
        val arriendoAntes = ocurrenciasEn(LocalDate.of(2026, 10, 20)).single { it.ruleId == "rr_arriendo" }

        val res = confirmar(confirmacionDelDebito(d, d.monto))
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        // Dos toques (o una respuesta perdida): los mismos ids, el mismo movimiento.
        assertEquals(HttpStatusCode.OK, confirmar(confirmacionDelDebito(d, d.monto)).status)

        val despues = eventos()
        val nuevos = despues.filter { e -> antes.none { it.id == e.id } }
        val gasto = nuevos.single()
        assertEquals(d.pataDelDineroId, gasto.id)
        assertEquals(TransactionType.EXPENSE, gasto.type)
        assertEquals(netflix, gasto.amount)
        assertEquals(master, gasto.accountId)
        assertEquals("Suscripciones", gasto.category)
        assertEquals(appDateToEpochMillis(dia5) + 12 * 3_600_000L, gasto.timestamp, "fechado el día que se esperaba")
        assertEquals(antes, despues.filter { it.id != gasto.id }, "ningún otro movimiento cambió")

        val fila = ocurrenciasEn(hoy).single { it.ruleId == "rr_netflix" }
        assertTrue(fila.occurred, "la fila de Pagos queda tildada por el mismo movimiento")
        assertEquals(gasto.id, fila.eventId)
        assertEquals(arriendoAntes, ocurrenciasEn(LocalDate.of(2026, 10, 20)).single { it.ruleId == "rr_arriendo" })
        assertTrue(propuestas(hoy).isEmpty())
    }

    @Test
    fun `si me llego la nomina anota el ingreso con el monto que digo`() = testApplication {
        application { testModule() }
        regla(id = "rr_salario", nombre = "Salario", categoria = "Salario", monto = sueldo, dia = 5, tipo = "INCOME", cuenta = ahorros)
        val d = propuestas(dia5.plusDays(3)).single()
        assertEquals(HttpStatusCode.Created, confirmar(confirmacionDelDebito(d, 8_640_000L)).status)
        val ingreso = eventos().single { it.id == d.pataDelDineroId }
        assertEquals(TransactionType.INCOME, ingreso.type)
        assertEquals(8_640_000L, ingreso.amount)
        assertEquals(ahorros, ingreso.accountId)
        assertTrue(ocurrenciasEn(dia5.plusDays(3)).single { it.ruleId == "rr_salario" }.occurred)
        assertTrue(propuestas(dia5.plusDays(3)).isEmpty())
    }

    @Test
    fun `no se pago no vuelve en ese periodo, y el siguiente si`() = testApplication {
        application { testModule() }
        regla()
        val d = propuestas(dia5.plusDays(3)).single()
        assertEquals(HttpStatusCode.NoContent, descartar(d).status)
        assertTrue(propuestas(dia5.plusDays(3)).isEmpty())
        assertTrue(propuestas(dia5.plusDays(4)).isEmpty())
        val noviembre = propuestas(LocalDate.of(2026, 11, 8)).single()
        assertEquals("2026-11", noviembre.periodo)
        assertTrue(eventos().none { it.id.startsWith("ev_deb_") }, "descartar no anota nada")
    }

    @Test
    fun `no se confirma lo que no se propone`() = testApplication {
        application { testModule() }
        regla(id = "rr_abono", nombre = "Abono extra", cuenta = ahorros, haciaDeuda = credito)
        val res = confirmar(
            ConfirmarDebitoAutomatico(ruleId = "rr_abono", periodo = "2026-10", monto = 500_000L, eventoId = "ev_deb_abc_s"),
        )
        assertEquals(HttpStatusCode.UnprocessableEntity, res.status)
        assertTrue(eventos().none { it.id == "ev_deb_abc_s" })
    }

    @Test
    fun `la cuota de un credito con debito sigue saliendo una vez y con dos patas`() = testApplication {
        application { testModule() }
        transaction {
            Credits.insert {
                it[accountId] = credito; it[userId] = duenoId; it[bank] = "Bancolombia"; it[principal] = 40_000_000L
                it[rateEa] = 24.5; it[termMonths] = 60; it[installment] = 1_204_064L; it[dayOfMonth] = 5
                it[startDate] = "2026-01-02"; it[remindMe] = true; it[debitoAutomaticoDesde] = ahorros
            }
        }
        val d = propuestas(dia5).single()
        assertEquals(OrigenDelDebito.CUOTA_DE_CREDITO, d.origen)
        assertEquals(false, d.esperado)
        assertEquals(HttpStatusCode.Created, confirmar(confirmacionDelDebito(d, d.monto)).status)
        val patas = eventos().filter { it.transferId == d.transferId }
        assertEquals(2, patas.size)
        assertEquals(setOf(ahorros, credito), patas.map { it.accountId }.toSet())
    }

    // ── El wire ─────────────────────────────────────────────────────────────────

    /** La forma de la propuesta que conoce un APK anterior a la vuelta 90. */
    @Serializable
    private data class PropuestaDeUnApkViejo(
        val ruleId: String,
        val periodo: String,
        val origen: OrigenDelDebito,
        val nombre: String,
        val monto: Long,
        val moneda: String = "COP",
        val vence: String,
        val cuentaId: String,
        val cuentaNombre: String,
        val categoria: String,
        val pataDelDineroId: String,
        val deudaId: String? = null,
        val pataDeLaDeudaId: String? = null,
        val transferId: String? = null,
    )

    @Test
    fun `un APK viejo sigue leyendo la lista, y sin el parametro no recibe lo esperado`() = testApplication {
        application { testModule() }
        regla(dia = LocalDate.now(com.jvillada.movi.server.time.AppClock.zone).minusDays(3).dayOfMonth)
        val viejo = client.get("/api/debitos-automaticos") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, viejo.status)
        assertTrue(json.decodeFromString(ListSerializer(PropuestaDeUnApkViejo.serializer()), viejo.bodyAsText()).isEmpty())

        val nuevo = client.get("/api/debitos-automaticos?conEsperados=1") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        val crudo = nuevo.bodyAsText()
        val comoNuevo = json.decodeFromString(ListSerializer(DebitoAutomaticoPorConfirmar.serializer()), crudo).single()
        assertTrue(comoNuevo.esperado)
        // Y aunque un APK viejo llegara a recibirla, la deserializa: los campos nuevos son opcionales.
        val comoViejo = json.decodeFromString(ListSerializer(PropuestaDeUnApkViejo.serializer()), crudo).single()
        assertEquals(comoNuevo.ruleId, comoViejo.ruleId)
        assertNotNull(comoViejo.pataDelDineroId)
    }

    // ── Revisión de la vuelta 90 ────────────────────────────────────────────────

    @Test
    fun `una tarjeta vieja no anota dos veces lo que ya se anoto a mano`() = testApplication {
        application { testModule() }
        // Relativo a hoy: la ruta confirma contra el checklist de hoy.
        val hoy = com.jvillada.movi.server.time.AppClock.today()
        val vence = hoy.minusDays(3)
        regla(dia = vence.dayOfMonth)
        val d = propuestas(hoy).single()
        // Mientras la tarjeta seguía abierta en el teléfono, el dueño lo anotó a mano.
        transaction { evento("ev_a_mano", master, "EXPENSE", netflix, "Suscripciones", vence, "Netflix") }
        val res = confirmar(confirmacionDelDebito(d, d.monto))
        assertEquals(HttpStatusCode.Conflict, res.status, res.bodyAsText())
        assertEquals(YA_ESTA_ANOTADO, res.bodyAsText())
        assertTrue(eventos().none { it.id == d.pataDelDineroId }, "un solo gasto")
    }

    @Test
    fun `una tarjeta vieja no pisa un sello con otro movimiento ni un candidato`() = testApplication {
        application { testModule() }
        val hoy = com.jvillada.movi.server.time.AppClock.today()
        val vence = hoy.minusDays(3)
        regla(dia = vence.dayOfMonth)
        val d = propuestas(hoy).single()
        transaction {
            evento("ev_sellado", ahorros, "EXPENSE", 52_000L, "Varios", vence, "Pago raro")
            RecurringOccurrences.insert {
                it[userId] = duenoId; it[ruleId] = "rr_netflix"; it[period] = d.periodo; it[eventId] = "ev_sellado"; it[confirmedAt] = 1L
            }
        }
        assertEquals(HttpStatusCode.Conflict, confirmar(confirmacionDelDebito(d, d.monto)).status)
        assertTrue(eventos().none { it.id == d.pataDelDineroId })
        assertEquals("ev_sellado", ocurrenciasEn(hoy).single { it.ruleId == "rr_netflix" }.eventId, "el sello sigue en el suyo")
    }

    @Test
    fun `una tarjeta vieja no anota lo que se descarto`() = testApplication {
        application { testModule() }
        regla()
        val d = propuestas(dia5.plusDays(3)).single()
        descartar(d)
        val res = confirmar(confirmacionDelDebito(d, d.monto))
        assertEquals(HttpStatusCode.Conflict, res.status)
        assertEquals(YA_SE_DESCARTO, res.bodyAsText())
        assertTrue(eventos().none { it.id == d.pataDelDineroId })
    }

    @Test
    fun `un aviso pendiente con otro monto cercano tambien apaga la pregunta`() {
        regla()
        transaction {
            SmsMessages.insert {
                // Subió de $49.900 a $52.900 (6 %), y el aviso llegó el día del cobro, hace tres días.
                it[id] = "sms_netflix_subio"; it[userId] = duenoId; it[time] = "2026-10-05 06:10"; it[bank] = "85540"
                it[text] = "Bancolombia: Compraste COP52.900,00 en NETFLIX con tu T.Cred *3684, el 05/10/2026 a las 06:10."
                it[state] = SMS_STATE_PENDING; it[det] = "NETFLIX"
            }
        }
        assertTrue(propuestas(dia5.plusDays(3)).isEmpty())
    }

    @Test
    fun `un aviso pendiente muy distinto no la apaga`() {
        regla()
        transaction {
            SmsMessages.insert {
                it[id] = "sms_otra_cosa"; it[userId] = duenoId; it[time] = "2026-10-05 06:10"; it[bank] = "85540"
                it[text] = "Bancolombia: Compraste COP186.900,00 en ALMACEN con tu T.Cred *3684, el 05/10/2026 a las 06:10."
                it[state] = SMS_STATE_PENDING; it[det] = "ALMACEN"
            }
        }
        assertEquals(1, propuestas(dia5.plusDays(3)).size)
    }
}
