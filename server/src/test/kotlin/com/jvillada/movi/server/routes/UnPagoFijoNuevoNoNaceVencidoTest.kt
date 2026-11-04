package com.jvillada.movi.server.routes

import com.jvillada.movi.server.conectarBaseDePrueba
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.DebitosDescartados
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.reminders.queAvisarle
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.pagosPendientes
import com.jvillada.movi.shared.model.periodoDe
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
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 121 · un pago fijo nuevo no nace vencido del período anterior
 *
 * Claudia (probadora) creó el 2-nov-2026 un pago fijo del día 28, con el mes de calendario como período, y
 * Movi le dijo al instante «Del período de octubre · Venció hace 5 días»: la regla escrita a mano corría
 * «desde siempre», y la ocurrencia del 28 de octubre seguía en su gracia.
 *
 * Ahora una regla sin arranque guardado corre desde el período en que se creó (`created_at`), y el alta
 * acepta «¿Ya pagaste el de este período?» para que arranque en el siguiente. Las reglas con arranque
 * (desde un movimiento, el salario de la guía) y las de antes de `created_at` no cambian.
 *
 * «Hoy» fijo el 2 de noviembre de 2026 donde la función lo recibe; el alta lee el reloj de verdad
 * (`AppClock`), así que esas pruebas eligen el día de la regla a partir de hoy.
 */
class UnPagoFijoNuevoNoNaceVencidoTest {

    private val testSecret = "test-secret-for-pago-fijo-nuevo-min-32-chars!"
    private val duenoId = "user-dueno-pago-nuevo"
    private val ahorros = "acc_ahorros_pago_nuevo"
    private val json = Json { ignoreUnknownKeys = true }
    private val dosDeNoviembre: LocalDate = LocalDate.of(2026, 11, 2)

    @BeforeTest
    fun setUp() {
        conectarBaseDePrueba(
            url = "jdbc:h2:mem:pago_fijo_nuevo_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Apartados, Users, Accounts, Events, VoidEvents, Budgets, Documents, RecurringRules,
                RecurringOccurrences, OccurrenceRejections, Credits, Cards, Subscriptions, Goals, SmsMessages,
                KnownDestinations, DebitosDescartados,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            Users.insert { it[id] = duenoId; it[email] = "$duenoId@nuevo.test"; it[name] = "Claudia"; it[passwordHash] = "h" }
            Accounts.insert {
                it[id] = ahorros; it[userId] = duenoId; it[name] = "Bancolombia Ahorros"; it[type] = "SAVINGS"
                it[currency] = "COP"; it[balance] = 0L
            }
        }
    }

    // ── Armado ──────────────────────────────────────────────────────────────

    private fun mediodia(dia: LocalDate) = appDateToEpochMillis(dia) + 12 * 3_600_000L

    /** Una regla escrita a mano, como la deja el alta: sin arranque y con su fecha de creación. */
    private fun regla(id: String, nombre: String, dia: Int, creada: LocalDate?, desde: String? = null) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id; it[userId] = duenoId; it[name] = nombre; it[category] = nombre
            it[amount] = 120_000L; it[dayOfMonth] = dia; it[type] = "EXPENSE"; it[accountId] = ahorros
            it[activeFrom] = desde
            it[createdAt] = creada?.let(::mediodia)
        }
    }

    private fun gasto(id: String, monto: Long, fecha: LocalDate, descripcion: String) = transaction {
        Events.insert {
            it[Events.id] = id; it[userId] = duenoId; it[accountId] = ahorros; it[type] = "EXPENSE"; it[amount] = monto
            it[currency] = "COP"; it[category] = descripcion; it[description] = descripcion
            it[timestamp] = mediodia(fecha)
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun sello(regla: String, periodo: String, evento: String?) = transaction {
        RecurringOccurrences.insert {
            it[userId] = duenoId; it[ruleId] = regla; it[period] = periodo; it[eventId] = evento
            it[confirmedAt] = 1L
        }
    }

    private fun proximos(dia: LocalDate) = runBlocking { proximosPagos(duenoId, dia) }
    private fun ocurrencias(dia: LocalDate) = runBlocking { ocurrenciasDelPeriodo(duenoId, dia) }

    private fun checklist(dia: LocalDate, ajustes: PeriodSettings = PeriodSettings()) = checklistDelPeriodo(
        upcoming = proximos(dia),
        ocurrencias = ocurrencias(dia),
        periodo = periodoDe(mediodia(dia), ajustes),
        settings = ajustes,
    )

    private fun token() = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", duenoId).withClaim("email", "$duenoId@nuevo.test")
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

    private suspend fun ApplicationTestBuilder.enviar(ruta: String, cuerpo: String, metodo: String = "POST"): HttpResponse {
        val armar: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
        return if (metodo == "PUT") client.put(ruta, armar) else client.post(ruta, armar)
    }

    private fun cuerpo(dia: Int, extra: String = "", nombre: String = "Gimnasio", tipo: String = "EXPENSE") =
        """{"id":"","name":"$nombre","category":"$nombre","amount":120000,"dayOfMonth":$dia,"type":"$tipo","accountId":"$ahorros"$extra}"""

    private fun guardada(id: String) = transaction { RecurringRules.selectAll().where { RecurringRules.id eq id }.single() }

    // ── El caso de Claudia ──────────────────────────────────────────────────

    /**
     * Creada el 2-nov, del día 28, con el corte el 1: el 28 de octubre no existe. Ni en `/upcoming` (el
     * vencimiento vigente es el 28 de noviembre), ni en `/occurrences`, ni en la lista de Pagos (de donde
     * salen «Próximo pago», «Te quedan», la cola y la campana), ni en el barrido de recordatorios.
     */
    @Test
    fun `creada el 2 de noviembre no aparece en el periodo de octubre ni como vencida`() {
        regla("rr_claudia", "Gimnasio", dia = 28, creada = dosDeNoviembre)

        val vigente = proximos(dosDeNoviembre).single { it.rule.id == "rr_claudia" }
        assertEquals("2026-11-28", vigente.dueDate, "el vencimiento vigente es el de noviembre")
        assertEquals("2026-11-02", vigente.rule.activeFrom, "sin arranque guardado, el de la creación")

        val estados = ocurrencias(dosDeNoviembre).filter { it.ruleId == "rr_claudia" }
        assertTrue(estados.none { it.dueDate < "2026-11-01" }, "nada del período de octubre: $estados")

        val lista = checklist(dosDeNoviembre)
        assertTrue(lista.none { it.ruleId == "rr_claudia" && it.vence < "2026-11-01" }, "nada de octubre en Pagos: $lista")
        assertTrue(pagosPendientes(lista).none { it.ruleId == "rr_claudia" && it.vencido }, "nada vencido")

        val avisos = runBlocking { queAvisarle(duenoId, dosDeNoviembre, leadDays = 3) }
        assertTrue(avisos.reglas.none { it.id == "rr_claudia" }, "no avisa un vencimiento que no existe")
    }

    /**
     * El mismo caso con el corte el 25: creada el 27-oct (período 25-oct → 24-nov), el 24-oct —tres días
     * antes, todavía en su gracia— es del período anterior y no existe; el 26-oct sí es de este período.
     */
    @Test
    fun `con el corte el 25 el piso es el arranque del periodo de la creacion`() {
        transaction { Users.update({ Users.id eq duenoId }) { it[periodCutoffDay] = 25 } }
        val hoy = LocalDate.of(2026, 10, 27)
        regla("rr_corte", "Celular", dia = 24, creada = hoy)
        regla("rr_mismo_periodo", "Internet", dia = 26, creada = hoy)

        assertEquals("2026-11-24", proximos(hoy).single { it.rule.id == "rr_corte" }.dueDate)
        assertTrue(ocurrencias(hoy).none { it.ruleId == "rr_corte" && it.dueDate < "2026-10-25" })
        // El 26 de octubre sí es de este período (arrancó el 25): existe, y vencido, porque es real.
        val estado = ocurrencias(hoy).single { it.ruleId == "rr_mismo_periodo" }
        assertEquals("2026-10-26", estado.dueDate)
        assertEquals(false, estado.occurred)
    }

    /**
     * «No» (o un APK viejo, sin el campo): la regla corre desde el período en que se creó, y la de este
     * período existe y sale vencida, porque es real.
     */
    @Test
    fun `sin ya pague la ocurrencia de este periodo existe y sale vencida`() {
        regla("rr_arriendo", "Arriendo", dia = 1, creada = dosDeNoviembre)

        val estado = ocurrencias(dosDeNoviembre).single { it.ruleId == "rr_arriendo" }
        assertEquals("2026-11-01", estado.dueDate)
        assertEquals(false, estado.occurred)
        val lista = checklist(dosDeNoviembre)
        assertTrue(pagosPendientes(lista).any { it.ruleId == "rr_arriendo" && it.vencido }, "vencida: $lista")
    }

    // ── El alta ─────────────────────────────────────────────────────────────

    /**
     * Con «ya pagué» y la fecha de este período pasada (hoy, con el corte el 1: el día de hoy siempre lo
     * cumple), la regla arranca en el período siguiente: nada en el período en curso, y sí en el siguiente.
     */
    @Test
    fun `con ya pague arranca en el periodo siguiente`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()
        val primeroDelSiguiente = hoy.withDayOfMonth(1).plusMonths(1)

        val alta = enviar("/api/recurring-rules", cuerpo(hoy.dayOfMonth, extra = ""","yaPagoElDeEstePeriodo":true"""))
        assertEquals(HttpStatusCode.Created, alta.status, alta.bodyAsText())
        val creada = json.decodeFromString(RecurringRule.serializer(), alta.bodyAsText())
        assertEquals(primeroDelSiguiente.toString(), creada.activeFrom)
        assertNull(creada.yaPagoElDeEstePeriodo, "es de ida: la respuesta no lo trae")
        assertEquals(primeroDelSiguiente.toString(), guardada(creada.id)[RecurringRules.activeFrom])

        val estados = ocurrencias(hoy).filter { it.ruleId == creada.id }
        assertTrue(estados.none { it.dueDate < primeroDelSiguiente.toString() }, "nada en el período en curso: $estados")
        val lista = checklist(hoy)
        assertTrue(lista.none { it.ruleId == creada.id && it.vence < primeroDelSiguiente.toString() }, "$lista")
        val vigente = proximos(hoy).single { it.rule.id == creada.id }
        assertEquals(YearMonthDe(primeroDelSiguiente), YearMonthDe(LocalDate.parse(vigente.dueDate)), "la del siguiente sí")
    }

    /** Con «No»: nada en la fila, y la ocurrencia de este período existe (vence hoy o venció). */
    @Test
    fun `con no la ocurrencia de este periodo existe`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()

        val alta = enviar("/api/recurring-rules", cuerpo(hoy.dayOfMonth, extra = ""","yaPagoElDeEstePeriodo":false"""))
        assertEquals(HttpStatusCode.Created, alta.status, alta.bodyAsText())
        val creada = json.decodeFromString(RecurringRule.serializer(), alta.bodyAsText())
        assertNull(guardada(creada.id)[RecurringRules.activeFrom], "la fila no guarda un arranque")
        assertNotNull(guardada(creada.id)[RecurringRules.createdAt])
        assertEquals(hoy.toString(), creada.activeFrom, "la respuesta dice el arranque como lo lee todo: la creación")

        val estado = ocurrencias(hoy).single { it.ruleId == creada.id }
        assertEquals(hoy.toString(), estado.dueDate)
        assertEquals(false, estado.occurred)
    }

    /** El server no acepta «ya pagué» donde no aplica: un ingreso, o una regla que viene de un movimiento. */
    @Test
    fun `ya pague se ignora en un ingreso y en una regla desde un movimiento`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()

        val ingreso = enviar(
            "/api/recurring-rules",
            cuerpo(hoy.dayOfMonth, extra = ""","yaPagoElDeEstePeriodo":true""", nombre = "Salario", tipo = "INCOME"),
        )
        assertEquals(HttpStatusCode.Created, ingreso.status, ingreso.bodyAsText())
        val idIngreso = json.decodeFromString(RecurringRule.serializer(), ingreso.bodyAsText()).id
        assertNull(guardada(idIngreso)[RecurringRules.activeFrom], "un ingreso no se pregunta")

        val desde = hoy.minusDays(3).toString()
        val delMovimiento = enviar(
            "/api/recurring-rules",
            cuerpo(hoy.dayOfMonth, extra = ""","activeFrom":"$desde","yaPagoElDeEstePeriodo":true""", nombre = "Netflix"),
        )
        assertEquals(HttpStatusCode.Created, delMovimiento.status, delMovimiento.bodyAsText())
        val idMovimiento = json.decodeFromString(RecurringRule.serializer(), delMovimiento.bodyAsText()).id
        assertEquals(desde, guardada(idMovimiento)[RecurringRules.activeFrom], "manda el movimiento")
    }

    // ── Lo que no cambia ────────────────────────────────────────────────────

    /** Editar una regla no mueve su piso: ni la fila ni `created_at` cambian, y octubre sigue sin existir. */
    @Test
    fun `editar no mueve el piso`() = testApplication {
        application { testModule() }
        regla("rr_claudia", "Gimnasio", dia = 28, creada = dosDeNoviembre)
        val creadaAntes = guardada("rr_claudia")[RecurringRules.createdAt]

        val editar = enviar(
            "/api/recurring-rules/rr_claudia",
            """{"id":"rr_claudia","name":"Gimnasio","category":"Gimnasio","amount":150000,"dayOfMonth":28,"type":"EXPENSE"}""",
            metodo = "PUT",
        )
        assertEquals(HttpStatusCode.OK, editar.status, editar.bodyAsText())
        assertEquals("2026-11-02", json.decodeFromString(RecurringRule.serializer(), editar.bodyAsText()).activeFrom)
        assertNull(guardada("rr_claudia")[RecurringRules.activeFrom])
        assertEquals(creadaAntes, guardada("rr_claudia")[RecurringRules.createdAt])
        assertEquals("2026-11-28", proximos(dosDeNoviembre).single { it.rule.id == "rr_claudia" }.dueDate)

        // La lista dice el mismo arranque que usa todo lo demás.
        val lista = client.get("/api/recurring-rules") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        val reglas = json.decodeFromString(ListSerializer(RecurringRule.serializer()), lista.bodyAsText())
        assertEquals("2026-11-02", reglas.single().activeFrom)
    }

    /**
     * Una regla de antes de `created_at` (sin fecha) corre desde siempre y conserva su historia sellada; una
     * con fecha conserva lo sellado desde su período de creación.
     */
    @Test
    fun `una regla vieja con pagos sellados conserva su historia`() {
        regla("rr_vieja", "Administración", dia = 10, creada = null)
        gasto("ev_admin_sep", 120_000L, LocalDate.of(2026, 9, 10), "Administración")
        sello("rr_vieja", "2026-09", "ev_admin_sep")
        regla("rr_con_fecha", "Seguro", dia = 10, creada = LocalDate.of(2026, 9, 26))
        gasto("ev_seguro_oct", 120_000L, LocalDate.of(2026, 10, 10), "Seguro")
        sello("rr_con_fecha", "2026-10", "ev_seguro_oct")

        val septiembre = ocurrencias(LocalDate.of(2026, 9, 12)).single { it.ruleId == "rr_vieja" }
        assertEquals(true, septiembre.occurred)
        assertEquals("ev_admin_sep", septiembre.eventId)
        // Agosto también existe: sin fecha de creación no se inventa un piso.
        assertTrue(ocurrencias(LocalDate.of(2026, 8, 12)).any { it.ruleId == "rr_vieja" })

        val octubre = ocurrencias(LocalDate.of(2026, 10, 12)).single { it.ruleId == "rr_con_fecha" }
        assertEquals(true, octubre.occurred)
        assertEquals("ev_seguro_oct", octubre.eventId)
    }

    /**
     * **Lo pagado nunca desaparece.** Los probadores tocaron «Ya lo pagué» sobre el octubre falso: la regla
     * creada el 2-nov con un sello de octubre sigue mostrando ese octubre pagado (el primer sello baja el
     * piso), y nada queda vencido. Lo mismo un septiembre sellado de una regla creada en octubre.
     */
    @Test
    fun `lo sellado antes de la creacion se sigue viendo pagado`() {
        regla("rr_probador", "Gimnasio", dia = 28, creada = dosDeNoviembre)
        gasto("ev_gym_oct", 120_000L, LocalDate.of(2026, 10, 28), "Gimnasio")
        sello("rr_probador", "2026-10", "ev_gym_oct")

        val octubre = ocurrencias(LocalDate.of(2026, 10, 29)).single { it.ruleId == "rr_probador" }
        assertEquals("2026-10-28", octubre.dueDate)
        assertEquals(true, octubre.occurred, "pagado, no vencido")
        assertEquals("ev_gym_oct", octubre.eventId)
        assertTrue(pagosPendientes(checklist(dosDeNoviembre)).none { it.ruleId == "rr_probador" && it.vencido })
        assertEquals("2026-11-28", proximos(dosDeNoviembre).single { it.rule.id == "rr_probador" }.dueDate)

        // Varias reglas a la vez (la caché de sellos es por transacción y usuario): cada una con lo suyo.
        regla("rr_sin_sello", "Internet", dia = 28, creada = dosDeNoviembre)
        val lista = proximos(dosDeNoviembre)
        assertEquals("2026-10-28", lista.single { it.rule.id == "rr_probador" }.rule.activeFrom)
        assertEquals("2026-11-02", lista.single { it.rule.id == "rr_sin_sello" }.rule.activeFrom)
    }

    /**
     * El PUT de un cliente que reenvía la regla tal cual la leyó (con el arranque derivado) no lo escribe
     * en la fila: sigue NULL. Un arranque distinto sí se guarda.
     */
    @Test
    fun `el put no guarda el arranque derivado`() = testApplication {
        application { testModule() }
        regla("rr_claudia", "Gimnasio", dia = 28, creada = dosDeNoviembre)

        val igual = enviar(
            "/api/recurring-rules/rr_claudia",
            """{"id":"rr_claudia","name":"Gimnasio","category":"Gimnasio","amount":150000,"dayOfMonth":28,"type":"EXPENSE","activeFrom":"2026-11-02"}""",
            metodo = "PUT",
        )
        assertEquals(HttpStatusCode.OK, igual.status, igual.bodyAsText())
        assertNull(guardada("rr_claudia")[RecurringRules.activeFrom], "el derivado no se escribe")
        assertEquals("2026-11-02", json.decodeFromString(RecurringRule.serializer(), igual.bodyAsText()).activeFrom)

        val otro = enviar(
            "/api/recurring-rules/rr_claudia",
            """{"id":"rr_claudia","name":"Gimnasio","category":"Gimnasio","amount":150000,"dayOfMonth":28,"type":"EXPENSE","activeFrom":"2026-12-01"}""",
            metodo = "PUT",
        )
        assertEquals(HttpStatusCode.OK, otro.status, otro.bodyAsText())
        assertEquals("2026-12-01", guardada("rr_claudia")[RecurringRules.activeFrom])
    }

    /**
     * Vuelta 125: una copia leída ANTES de que el dueño sellara octubre trae el derivado de entonces (la
     * creación). La 121 solo reconocía el eco del derivado de hoy (el sello, 28-oct), así que guardaba el
     * 2-nov como arranque explícito: el piso subía por encima del sello y el octubre pagado desaparecía.
     */
    @Test
    fun `el put con una copia vieja no congela el piso`() = testApplication {
        application { testModule() }
        regla("rr_vieja", "Gimnasio", dia = 28, creada = dosDeNoviembre)
        gasto("ev_gym_oct_vieja", 120_000L, LocalDate.of(2026, 10, 28), "Gimnasio")
        sello("rr_vieja", "2026-10", "ev_gym_oct_vieja")

        val res = enviar(
            "/api/recurring-rules/rr_vieja",
            """{"id":"rr_vieja","name":"Gimnasio","category":"Gimnasio","amount":150000,"dayOfMonth":28,"type":"EXPENSE","activeFrom":"2026-11-02"}""",
            metodo = "PUT",
        )
        assertEquals(HttpStatusCode.OK, res.status, res.bodyAsText())
        assertNull(guardada("rr_vieja")[RecurringRules.activeFrom], "un derivado viejo no se escribe")
        assertEquals("2026-10-28", json.decodeFromString(RecurringRule.serializer(), res.bodyAsText()).activeFrom)
        val octubre = ocurrencias(LocalDate.of(2026, 10, 29)).single { it.ruleId == "rr_vieja" }
        assertEquals(true, octubre.occurred, "lo pagado sigue viéndose")
    }

    /**
     * Una regla creada hoy desde un movimiento del período ANTERIOR conserva esa ocurrencia, saldada por ese
     * movimiento: el piso es el período del movimiento, no el de la creación.
     */
    @Test
    fun `una regla desde un movimiento conserva su ocurrencia saldada`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()
        val fecha = hoy.withDayOfMonth(1).minusMonths(1).withDayOfMonth(15)
        gasto("ev_seguro", 98_500L, fecha, "Seguro Sura")

        val alta = enviar(
            "/api/recurring-rules",
            """{"id":"","name":"Seguro Sura","category":"Seguro Sura","amount":98500,"dayOfMonth":15,"type":"EXPENSE",""" +
                """"accountId":"$ahorros","activeFrom":"$fecha","eventoDeOrigen":"ev_seguro"}""",
        )
        assertEquals(HttpStatusCode.Created, alta.status, alta.bodyAsText())
        val id = json.decodeFromString(RecurringRule.serializer(), alta.bodyAsText()).id

        val estado = ocurrencias(fecha).single { it.ruleId == id }
        assertEquals(fecha.toString(), estado.dueDate)
        assertEquals(true, estado.occurred, "la salda el movimiento que la originó")
        assertEquals("ev_seguro", estado.eventId)
    }

    @Suppress("FunctionName")
    private fun YearMonthDe(fecha: LocalDate) = java.time.YearMonth.from(fecha)
}
