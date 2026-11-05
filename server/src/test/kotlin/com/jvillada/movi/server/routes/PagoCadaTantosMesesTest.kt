package com.jvillada.movi.server.routes

import com.jvillada.movi.server.conectarBaseDePrueba
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.ai.render
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
import com.jvillada.movi.server.reminders.DEFAULT_GRACE_DAYS
import com.jvillada.movi.server.reminders.arranqueDeLaRegla
import com.jvillada.movi.server.reminders.diasDelPeriodo
import com.jvillada.movi.server.reminders.dueDateFor
import com.jvillada.movi.server.reminders.occurrenceInMonth
import com.jvillada.movi.server.reminders.occurrenceWindow
import com.jvillada.movi.server.reminders.ocurrenciaAnteriorQuePisaElPeriodo
import com.jvillada.movi.server.reminders.ocurrenciaEnGracia
import com.jvillada.movi.server.reminders.ocurrenciaEnJuego
import com.jvillada.movi.server.reminders.ocurrenciaPorPreguntar
import com.jvillada.movi.server.reminders.periodOf
import com.jvillada.movi.server.reminders.primeraOcurrenciaDesde
import com.jvillada.movi.server.reminders.queAvisarle
import com.jvillada.movi.server.reminders.ruleIsActiveOn
import com.jvillada.movi.server.reminders.ultimaOcurrenciaHasta
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.FrecuenciaDelPago
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SiElCorteCaeEnFinDeSemana
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.faltaPorPagar
import com.jvillada.movi.shared.model.fijosDelPeriodo
import com.jvillada.movi.shared.model.mesDeReferencia
import com.jvillada.movi.shared.model.pendientesDePeriodosAnteriores
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
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # Vuelta 126 · pagos fijos cada 2, 3 o 6 meses, cada año y de una sola vez
 *
 * Una regla no mensual cuenta su monto entero **solo en el período en que vence** (checklist, «Falta por
 * pagar», los fijos que restan de «Te quedan», la caja, los recordatorios); en los demás no existe. Una de
 * una sola vez sin pagar sigue pendiente después; pagada, no vuelve. Un APK viejo no las ve (sin
 * `?conFrecuencias=1`) y su PUT no les borra la frecuencia. **Y toda regla mensual da exactamente lo de
 * antes**: el motor de vencimientos se compara, día por día, contra una copia del de la vuelta 125.
 *
 * Sin el cambio no compila (no existían ni las columnas ni el campo); con las columnas y sin la regla, la
 * trimestral salía en cada mes.
 */
class PagoCadaTantosMesesTest {

    private val testSecret = "test-secret-for-cada-tantos-meses-32-chars!"
    private val duenoId = "user-dueno-frecuencia"
    private val ahorros = "acc_ahorros_frecuencia"
    private val json = Json { ignoreUnknownKeys = true }
    private val calendario = PeriodSettings()

    @BeforeTest
    fun setUp() {
        conectarBaseDePrueba(
            url = "jdbc:h2:mem:cada_tantos_meses_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            Users.insert { it[id] = duenoId; it[email] = "$duenoId@frecuencia.test"; it[name] = duenoId; it[passwordHash] = "h" }
            Accounts.insert {
                it[id] = ahorros; it[userId] = duenoId; it[name] = "Bancolombia Ahorros"; it[type] = "SAVINGS"
                it[currency] = "COP"; it[balance] = 0L
            }
        }
    }

    // ── Armado ──────────────────────────────────────────────────────────────

    private fun regla(
        id: String,
        nombre: String,
        dia: Int,
        monto: Long,
        frecuencia: FrecuenciaDelPago? = null,
        ancla: String? = null,
        desde: String? = null,
        tipo: String = "EXPENSE",
    ) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id; it[userId] = duenoId; it[name] = nombre; it[category] = nombre
            it[amount] = monto; it[dayOfMonth] = dia; it[type] = tipo; it[accountId] = ahorros
            it[RecurringRules.frecuencia] = frecuencia?.name
            it[primerVencimiento] = ancla
            it[activeFrom] = desde
        }
    }

    private fun sello(ruleId: String, periodo: String, eventId: String? = null) = transaction {
        RecurringOccurrences.insert {
            it[userId] = duenoId; it[RecurringOccurrences.ruleId] = ruleId; it[period] = periodo
            it[RecurringOccurrences.eventId] = eventId; it[confirmedAt] = 1L
        }
    }

    private fun gasto(id: String, monto: Long, fecha: LocalDate, descripcion: String) = transaction {
        Events.insert {
            it[Events.id] = id; it[userId] = duenoId; it[accountId] = ahorros; it[type] = "EXPENSE"; it[amount] = monto
            it[currency] = "COP"; it[category] = descripcion; it[description] = descripcion
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[eventSource] = "MANUAL"; it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun proximos(dia: LocalDate) = runBlocking { proximosPagos(duenoId, dia) }
    private fun ocurrencias(dia: LocalDate) = runBlocking { ocurrenciasDelPeriodo(duenoId, dia) }
    private fun periodoDeHoy(dia: LocalDate) = periodoDe(appDateToEpochMillis(dia) + 12 * 3_600_000L, calendario)
    private fun checklist(dia: LocalDate) = checklistDelPeriodo(proximos(dia), ocurrencias(dia), periodoDeHoy(dia), calendario)

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@frecuencia.test")
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
            header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}")
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }
        return if (metodo == "PUT") client.put(ruta, armar) else client.post(ruta, armar)
    }

    private suspend fun ApplicationTestBuilder.leer(ruta: String): String =
        client.get(ruta) { header(HttpHeaders.Authorization, "Bearer ${token(duenoId)}") }.bodyAsText()

    private fun filaGuardada(id: String) = transaction {
        RecurringRules.selectAll().where { RecurringRules.id eq id }.single()
            .let { Triple(it[RecurringRules.frecuencia], it[RecurringRules.primerVencimiento], it[RecurringRules.dayOfMonth]) }
    }

    // ── Cuenta solo en el período en que vence ─────────────────────────────────

    @Test
    fun `una trimestral que no vence este periodo no esta en el checklist ni en ocurrencias y no baja Te quedan`() {
        regla("rr_admin", "Administración", 15, 450_000, FrecuenciaDelPago.TRIMESTRAL, "2026-12-15")
        regla("rr_arriendo", "Arriendo", 15, 1_800_000)
        val hoy = LocalDate.of(2026, 11, 20)

        // `/upcoming` la trae con su vencimiento verdadero —el 15 de diciembre—, no con uno de noviembre.
        assertEquals("2026-12-15", proximos(hoy).single { it.rule.id == "rr_admin" }.dueDate)
        assertTrue(ocurrencias(hoy).none { it.ruleId == "rr_admin" })
        val filas = checklist(hoy)
        assertEquals(listOf("rr_arriendo"), filas.map { it.ruleId })
        assertEquals(1_800_000L, fijosDelPeriodo(filas), "los fijos que restan de «Te quedan»: solo el arriendo")
        assertEquals(1_800_000L, faltaPorPagar(filas))

        // En enero y febrero tampoco existe; en marzo vuelve.
        listOf(LocalDate.of(2027, 1, 20), LocalDate.of(2027, 2, 20)).forEach { dia ->
            assertTrue(checklist(dia).none { it.ruleId == "rr_admin" }, "$dia")
            assertTrue(ocurrencias(dia).none { it.ruleId == "rr_admin" }, "$dia")
        }
        assertTrue(checklist(LocalDate.of(2027, 3, 20)).any { it.ruleId == "rr_admin" })
    }

    @Test
    fun `el periodo en que vence cuenta su monto entero, una vez`() {
        regla("rr_admin", "Administración", 15, 450_000, FrecuenciaDelPago.TRIMESTRAL, "2026-12-15")
        regla("rr_arriendo", "Arriendo", 15, 1_800_000)
        val hoy = LocalDate.of(2026, 12, 20)

        val estado = ocurrencias(hoy).single { it.ruleId == "rr_admin" }
        assertEquals("2026-12-15", estado.dueDate)
        assertFalse(estado.occurred)
        val filas = checklist(hoy)
        assertEquals(1, filas.count { it.ruleId == "rr_admin" })
        assertEquals(FrecuenciaDelPago.TRIMESTRAL, filas.single { it.ruleId == "rr_admin" }.frecuencia)
        assertEquals(2_250_000L, fijosDelPeriodo(filas))
        assertEquals(2_250_000L, faltaPorPagar(filas))

        // Pagada, sale del «Falta por pagar» y el siguiente vencimiento es el de marzo.
        gasto("ev_admin", 450_000, LocalDate.of(2026, 12, 15), "Administración")
        assertTrue(ocurrencias(hoy).single { it.ruleId == "rr_admin" }.occurred)
        assertEquals(1_800_000L, faltaPorPagar(checklist(hoy)))
        assertEquals("2027-03-15", proximos(hoy).single { it.rule.id == "rr_admin" }.dueDate)
        assertEquals("2027-06-15", proximos(hoy).single { it.rule.id == "rr_admin" }.siguienteVencimiento)
    }

    @Test
    fun `un pago fuera de su mes no tilda otro mes`() = testApplication {
        application { testModule() }
        regla("rr_admin", "Administración", 15, 450_000, FrecuenciaDelPago.TRIMESTRAL, "2026-12-15")
        gasto("ev_admin_nov", 450_000, LocalDate.of(2026, 11, 15), "Administración")
        // Noviembre no es un vencimiento: ni se empareja solo ni se deja sellar.
        assertTrue(ocurrencias(LocalDate.of(2026, 11, 20)).none { it.ruleId == "rr_admin" })
        val sellar = enviar("/api/recurring-rules/rr_admin/occurrence", """{"period":"2026-11","eventId":"ev_admin_nov"}""")
        assertEquals(HttpStatusCode.BadRequest, sellar.status, sellar.bodyAsText())
        assertTrue(transaction { RecurringOccurrences.selectAll().toList() }.isEmpty())
    }

    @Test
    fun `la caja y los recordatorios la cuentan solo cuando vence`() {
        regla("rr_admin", "Administración", 15, 450_000, FrecuenciaDelPago.TRIMESTRAL, "2026-12-15")
        regla("rr_arriendo", "Arriendo", 15, 1_800_000)
        // Del 1 de diciembre, la caja llega hasta el 31: la administración del 15 está.
        assertTrue(runBlocking { cajaProyectadaDe(duenoId, LocalDate.of(2026, 12, 1)) }.pagos.any { it.ruleId == "rr_admin" })
        // Del 1 de enero, no: en enero no vence.
        assertTrue(runBlocking { cajaProyectadaDe(duenoId, LocalDate.of(2027, 1, 1)) }.pagos.none { it.ruleId == "rr_admin" })

        val avisaDic = runBlocking { queAvisarle(duenoId, LocalDate.of(2026, 12, 13), leadDays = 3) }.reglas.map { it.id }
        assertEquals(setOf("rr_admin", "rr_arriendo"), avisaDic.toSet())
        val avisaEne = runBlocking { queAvisarle(duenoId, LocalDate.of(2027, 1, 13), leadDays = 3) }.reglas.map { it.id }
        assertEquals(listOf("rr_arriendo"), avisaEne)
    }

    @Test
    fun `Tus periodos solo la lista en el periodo en que vence`() {
        regla("rr_admin", "Administración", 15, 450_000, FrecuenciaDelPago.TRIMESTRAL, "2026-12-15")
        fun fijos(dia: LocalDate) = transaction {
            pagosFijosDelPeriodo(duenoId, periodoDeHoy(dia), dia, calendario, inicioDeLaHistoria = LocalDate.of(2026, 1, 1))
        }.map { it.ruleId }
        assertEquals(emptyList(), fijos(LocalDate.of(2026, 11, 20)))
        assertEquals(listOf("rr_admin"), fijos(LocalDate.of(2026, 12, 20)))
        assertEquals(emptyList(), fijos(LocalDate.of(2027, 1, 20)))
    }

    @Test
    fun `Movi AI rotula la cadencia y no la suma como un gasto del mes`() {
        val ancla = AppClock.today().plusMonths(2).withDayOfMonth(10)
        regla("rr_soat", "SOAT", 10, 980_000, FrecuenciaDelPago.ANUAL, ancla.toString())
        regla("rr_arriendo", "Arriendo", 28, 1_800_000)
        val contexto = runBlocking { com.jvillada.movi.server.ai.contextoDelPeriodoDe(duenoId) }
        val soat = contexto.recurrentes.single { it.nombre == "SOAT" }
        assertFalse(soat.venceEnEstePeriodo)
        assertTrue(soat.cadencia!!.startsWith("Cada año · el próximo, 10 "), soat.cadencia)
        val texto = contexto.render()
        assertTrue(texto.contains("SOAT (SOAT): \$980000 sale cada año · el próximo"), texto)
        assertTrue(texto.contains("NO vence en este período"), texto)
        assertTrue(texto.contains("TODAVÍA no ocurrieron en este período: \$1800000"), texto)
    }

    // ── Una sola vez ───────────────────────────────────────────────────────────

    @Test
    fun `una sola vez sin pagar sigue pendiente en los periodos siguientes`() {
        regla("rr_matricula", "Matrícula", 20, 2_500_000, FrecuenciaDelPago.UNA_VEZ, "2027-01-20")
        // Antes de su período: solo como próximo vencimiento.
        val noviembre = LocalDate.of(2026, 11, 20)
        assertEquals("2027-01-20", proximos(noviembre).single().dueDate)
        assertNull(proximos(noviembre).single().siguienteVencimiento)
        assertTrue(ocurrencias(noviembre).isEmpty())
        assertTrue(checklist(noviembre).isEmpty())

        // Su período: cuenta.
        val enero = LocalDate.of(2027, 1, 25)
        assertEquals(2_500_000L, faltaPorPagar(checklist(enero)))

        // Dos meses después, sin pagar: no se perdió. La ocurrencia sigue abierta, `/upcoming` la da vencida y
        // Pagos la muestra en «Del período de enero» (no rueda a otro mes ni se cuenta en el de marzo).
        val marzo = LocalDate.of(2027, 3, 10)
        val abierta = ocurrencias(marzo).single()
        assertFalse(abierta.occurred)
        assertEquals("2027-01-20", abierta.dueDate)
        assertEquals("2027-01-20", proximos(marzo).single().dueDate)
        assertTrue(checklist(marzo).isEmpty())
        val anteriores = pendientesDePeriodosAnteriores(proximos(marzo), ocurrencias(marzo), periodoDeHoy(marzo), calendario)
        assertEquals(listOf("rr_matricula"), anteriores.map { it.ruleId })
        // Y avisa (una vez: la clave es su vencimiento).
        assertTrue(runBlocking { queAvisarle(duenoId, marzo, leadDays = 3) }.reglas.any { it.id == "rr_matricula" })
    }

    @Test
    fun `una sola vez pagada no vuelve a aparecer`() {
        regla("rr_matricula", "Matrícula", 20, 2_500_000, FrecuenciaDelPago.UNA_VEZ, "2027-01-20")
        gasto("ev_matricula", 2_500_000, LocalDate.of(2027, 3, 2), "Matrícula")
        sello("rr_matricula", "2027-01", "ev_matricula")
        val marzo = LocalDate.of(2027, 3, 10)
        assertTrue(proximos(marzo).isEmpty(), "sin próximo vencimiento")
        assertTrue(ocurrencias(marzo).none { !it.occurred })
        assertTrue(checklist(marzo).isEmpty())
        assertTrue(pendientesDePeriodosAnteriores(proximos(marzo), ocurrencias(marzo), periodoDeHoy(marzo), calendario).isEmpty())
        assertTrue(runBlocking { queAvisarle(duenoId, marzo, leadDays = 3) }.reglas.isEmpty())
        // Un año después tampoco.
        assertTrue(checklist(LocalDate.of(2028, 1, 25)).isEmpty())
    }

    // ── Revisión: una sola vez pagada en su período (C1) ──────────────────────

    /** El variable que el server resta y los fijos que resta el cliente: lo que mueve «Te quedan». */
    private fun teQuedanSuma(dia: LocalDate, conFrecuencias: Boolean = true): Pair<Long, Long> {
        val filas = checklist(dia)
        val variable = transaction {
            val dias = diasDelPeriodo(dia, calendario)
            disponibleDelServidor(
                uid = duenoId, hoy = dia, periodo = calendario,
                monthStart = appDateToEpochMillis(dias.start),
                monthEnd = appDateToEpochMillis(dias.endInclusive.plusDays(1)),
                voidedIds = emptySet(), reglasDeCredito = emptyList(), conFrecuencias = conFrecuencias,
            ).gastoVariablePorDia.values.sum()
        }
        return fijosDelPeriodo(filas) to variable
    }

    @Test
    fun `una sola vez pagada despues de su fecha sigue en su periodo y cuenta una vez`() {
        regla("rr_matricula", "Matrícula", 20, 2_500_000, FrecuenciaDelPago.UNA_VEZ, "2027-01-20")
        gasto("ev_matricula", 2_500_000, LocalDate.of(2027, 1, 22), "Matrícula")
        val hoy = LocalDate.of(2027, 1, 25)
        val fila = checklist(hoy).single { it.ruleId == "rr_matricula" }
        assertTrue(fila.pagado, "su fila pagada sigue en enero")
        // Los fijos la restan y el variable no la repite: el pago cuenta exactamente una vez.
        assertEquals(2_500_000L to 0L, teQuedanSuma(hoy))
        // El cierre de enero (la lista del último día) y «Tus períodos» la tienen.
        assertTrue(checklist(LocalDate.of(2027, 1, 31)).single { it.ruleId == "rr_matricula" }.pagado)
        assertEquals(listOf("rr_matricula"), transaction {
            pagosFijosDelPeriodo(duenoId, periodoDeHoy(hoy), hoy, calendario, inicioDeLaHistoria = LocalDate.of(2026, 1, 1))
        }.map { it.ruleId })
        // En febrero ya no está en ningún lado.
        assertTrue(proximos(LocalDate.of(2027, 2, 3)).isEmpty())
        assertTrue(checklist(LocalDate.of(2027, 2, 3)).isEmpty())
    }

    @Test
    fun `una sola vez pagada antes de su fecha, en el mismo periodo, tambien`() {
        regla("rr_matricula", "Matrícula", 20, 2_500_000, FrecuenciaDelPago.UNA_VEZ, "2027-01-20")
        // Dentro de la ventana en que Movi empareja sola (10 días antes de la fecha).
        gasto("ev_matricula", 2_500_000, LocalDate.of(2027, 1, 12), "Matrícula")
        val hoy = LocalDate.of(2027, 1, 14)
        val fila = checklist(hoy).single { it.ruleId == "rr_matricula" }
        assertTrue(fila.pagado)
        assertEquals(2_500_000L to 0L, teQuedanSuma(hoy))
        assertTrue(checklist(LocalDate.of(2027, 1, 31)).single { it.ruleId == "rr_matricula" }.pagado)
    }

    // ── Revisión: un APK viejo y el resumen del Inicio (I1) ───────────────────

    @Test
    fun `sin frecuencias el resumen no saca del variable el pago de una no mensual`() {
        regla("rr_admin", "Administración", 15, 450_000, FrecuenciaDelPago.TRIMESTRAL, "2026-12-15")
        gasto("ev_admin", 450_000, LocalDate.of(2026, 12, 15), "Administración")
        val hoy = LocalDate.of(2026, 12, 20)
        // El cliente nuevo: la fila la resta como fijo y el variable no la repite.
        assertEquals(450_000L to 0L, teQuedanSuma(hoy))
        // Un APK viejo no tiene la fila: el server la deja en el variable, así se resta una vez.
        val (_, variableViejo) = teQuedanSuma(hoy, conFrecuencias = false)
        assertEquals(450_000L, variableViejo)
    }

    // ── Revisión: Movi AI con una sola vez vencida o antes de su piso (I2) ────

    @Test
    fun `Movi AI no suma una sola vez vencida de antes ni una que ya pagaste al crearla`() {
        val hoy = AppClock.today()
        regla("rr_vieja", "Matrícula", 20, 2_500_000, FrecuenciaDelPago.UNA_VEZ, hoy.minusMonths(2).withDayOfMonth(20).toString())
        // Creada con «¿Ya pagaste?» en sí: corre desde el período siguiente, así que su fecha de hoy no existe.
        val inicioDelSiguiente = diasDelPeriodo(hoy, calendario).endInclusive.plusDays(1)
        regla("rr_pagada", "Cuota inicial", hoy.dayOfMonth, 5_000_000, FrecuenciaDelPago.UNA_VEZ, hoy.toString(), desde = inicioDelSiguiente.toString())
        regla("rr_arriendo", "Arriendo", 28, 1_800_000)
        val contexto = runBlocking { com.jvillada.movi.server.ai.contextoDelPeriodoDe(duenoId) }
        val vieja = contexto.recurrentes.single { it.nombre == "Matrícula" }
        assertFalse(vieja.venceEnEstePeriodo)
        assertTrue(vieja.vencidaDeAntes)
        assertFalse(contexto.recurrentes.single { it.nombre == "Cuota inicial" }.venceEnEstePeriodo)
        val texto = contexto.render()
        assertTrue(texto.contains("TODAVÍA no ocurrieron en este período: \$1800000"), texto)
        assertTrue(texto.contains("VENCIÓ en un período anterior"), texto)
    }

    // ── Revisión: «¿Ya pagaste?» no aplica a una sola vez (I3) ────────────────

    @Test
    fun `el server ignora ya pague en una de una sola vez`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()
        val alta = enviar(
            "/api/recurring-rules",
            """{"id":"","name":"Cuota inicial","category":"Carro","amount":5000000,"dayOfMonth":1,"type":"EXPENSE","frecuencia":"UNA_VEZ","primerVencimiento":"$hoy","yaPagoElDeEstePeriodo":true}""",
        )
        assertEquals(HttpStatusCode.Created, alta.status, alta.bodyAsText())
        val id = json.decodeFromString(RecurringRule.serializer(), alta.bodyAsText()).id
        assertNull(transaction { RecurringRules.selectAll().where { RecurringRules.id eq id }.single()[RecurringRules.activeFrom] })
        assertEquals(hoy.toString(), proximos(hoy).single { it.rule.id == id }.dueDate, "su único vencimiento existe")
    }

    // ── Revisión: corte 25 con el día hábil anterior (I4) ─────────────────────

    @Test
    fun `con el corte 25 y el dia habil anterior la trimestral cuenta en el periodo que contiene su fecha`() {
        transaction {
            Users.update({ Users.id eq duenoId }) {
                it[periodCutoffDay] = 25; it[periodWeekendRule] = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR.name
            }
        }
        val delDueno = PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
        regla("rr_admin", "Administración", 28, 450_000, FrecuenciaDelPago.TRIMESTRAL, "2026-12-28")
        fun lista(dia: LocalDate) = checklistDelPeriodo(proximos(dia), ocurrencias(dia), periodoDe(appDateToEpochMillis(dia) + 12 * 3_600_000L, delDueno), delDueno)
        // El período «diciembre» (25-nov al 23-dic) no la trae; el de «enero» arranca el jueves 24 de
        // diciembre (el 25 es Navidad) y sí.
        assertTrue(lista(LocalDate.of(2026, 12, 20)).none { it.ruleId == "rr_admin" })
        assertEquals("2026-12-28", proximos(LocalDate.of(2026, 12, 20)).single().dueDate)
        assertEquals(450_000L, fijosDelPeriodo(lista(LocalDate.of(2026, 12, 24))))
        assertEquals("2026-12-28", ocurrencias(LocalDate.of(2026, 12, 29)).single().dueDate)
        // Febrero y marzo («febrero» y «marzo» del dueño) no; el 28 de marzo cae en «abril».
        assertTrue(lista(LocalDate.of(2027, 2, 10)).isEmpty())
        assertTrue(lista(LocalDate.of(2027, 3, 10)).isEmpty())
        assertEquals("2027-03-28", lista(LocalDate.of(2027, 3, 26)).single().vence)
    }

    // ── Revisión: un período estirado a mano (M1) ─────────────────────────────

    @Test
    fun `en un periodo de mas de un mes la no mensual toma el vencimiento del mes en que vence`() {
        // Enero estirado hasta el 19 de febrero: trae el 10 de enero (no vence) y el 10 de febrero (sí).
        val estirado = PeriodSettings(cutoffDay = 1, iniciosPropios = mapOf("2027-02" to "2027-02-20"))
        val bimestral = RecurringRule(
            "rr_luz", "Luz", "Luz", 90_000L, 10, TransactionType.EXPENSE,
            frecuencia = FrecuenciaDelPago.BIMESTRAL, primerVencimiento = "2026-12-10",
        )
        // El 18, pasada la gracia del 10: solo la regla única del período lo encuentra.
        assertEquals(LocalDate.of(2027, 2, 10), ocurrenciaPorPreguntar(LocalDate.of(2027, 2, 18), bimestral, estirado))
    }

    // ── Revisión: un ingreso no mensual no es el salario (M5) ─────────────────

    @Test
    fun `un ingreso no mensual del dia del corte se espera en su fecha`() {
        val delDueno = PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR)
        // El 25 de octubre de 2026 es domingo: el salario de cada mes se espera el viernes 23; una prima
        // semestral del mismo día, el 25.
        val salario = RecurringRule("rr_sal", "Salario", "Salario", 5_000_000L, 25, TransactionType.INCOME)
        val prima = salario.copy(id = "rr_prima", name = "Prima", frecuencia = FrecuenciaDelPago.SEMESTRAL, primerVencimiento = "2026-10-25")
        assertEquals(LocalDate.of(2026, 10, 23), com.jvillada.movi.server.reminders.fechaEsperada(salario, LocalDate.of(2026, 10, 25), delDueno))
        assertEquals(LocalDate.of(2026, 10, 25), com.jvillada.movi.server.reminders.fechaEsperada(prima, LocalDate.of(2026, 10, 25), delDueno))
    }

    // ── El wire: APKs viejos, alta y edición ──────────────────────────────────

    @Test
    fun `sin conFrecuencias una no mensual no viaja`() = testApplication {
        application { testModule() }
        val hoy = AppClock.today()
        regla("rr_trim", "Administración", hoy.dayOfMonth, 450_000, FrecuenciaDelPago.TRIMESTRAL, hoy.toString())
        regla("rr_unica", "Matrícula", hoy.dayOfMonth, 900_000, FrecuenciaDelPago.UNA_VEZ, hoy.toString())
        regla("rr_arriendo", "Arriendo", hoy.dayOfMonth, 1_800_000)
        val reglas = ListSerializer(RecurringRule.serializer())
        val proximos = ListSerializer(UpcomingPayment.serializer())
        val estados = ListSerializer(OccurrenceState.serializer())

        val viejo = json.decodeFromString(reglas, leer("/api/recurring-rules?conAbonos=1"))
        assertEquals(listOf("rr_arriendo"), viejo.map { it.id })
        assertEquals(listOf("rr_arriendo"), json.decodeFromString(proximos, leer("/api/payments/upcoming?conAbonos=1")).map { it.rule.id })
        assertEquals(listOf("rr_arriendo"), json.decodeFromString(estados, leer("/api/payments/occurrences?conAbonos=1")).map { it.ruleId })
        // Ni siquiera la clave: lo que un APK viejo lee es exactamente lo de antes.
        val crudo = leer("/api/recurring-rules")
        assertFalse(crudo.contains("\"frecuencia\"") || crudo.contains("\"primerVencimiento\""), crudo)

        val nuevo = json.decodeFromString(reglas, leer("/api/recurring-rules?conAbonos=1&conFrecuencias=1"))
        assertEquals(setOf("rr_trim", "rr_unica", "rr_arriendo"), nuevo.map { it.id }.toSet())
        assertEquals(FrecuenciaDelPago.TRIMESTRAL, nuevo.single { it.id == "rr_trim" }.frecuencia)
        assertEquals(hoy.toString(), nuevo.single { it.id == "rr_trim" }.primerVencimiento)
        assertEquals(3, json.decodeFromString(proximos, leer("/api/payments/upcoming?conAbonos=1&conFrecuencias=1")).size)
        assertEquals(
            setOf("rr_trim", "rr_unica", "rr_arriendo"),
            json.decodeFromString(estados, leer("/api/payments/occurrences?conAbonos=1&conFrecuencias=1")).map { it.ruleId }.toSet(),
        )
    }

    @Test
    fun `un PUT sin el campo no borra la frecuencia, y MENSUAL explicito la quita`() = testApplication {
        application { testModule() }
        regla("rr_admin", "Administración", 15, 450_000, FrecuenciaDelPago.TRIMESTRAL, "2026-12-15")
        // El cuerpo de un APK viejo: sin frecuencia ni ancla, y con otro día.
        val viejo = enviar(
            "/api/recurring-rules/rr_admin",
            """{"id":"rr_admin","name":"Administración","category":"Vivienda","amount":480000,"dayOfMonth":3,"type":"EXPENSE"}""",
            metodo = "PUT",
        )
        assertEquals(HttpStatusCode.OK, viejo.status, viejo.bodyAsText())
        assertEquals(Triple("TRIMESTRAL", "2026-12-15", 15), filaGuardada("rr_admin"))

        // Una copia que reenvía el ancla guardada (aunque ya esté atrás) se acepta tal cual.
        val copia = enviar(
            "/api/recurring-rules/rr_admin",
            """{"id":"rr_admin","name":"Administración","category":"Vivienda","amount":480000,"dayOfMonth":15,"type":"EXPENSE","frecuencia":"TRIMESTRAL","primerVencimiento":"2026-12-15"}""",
            metodo = "PUT",
        )
        assertEquals(HttpStatusCode.OK, copia.status, copia.bodyAsText())

        val mensual = enviar(
            "/api/recurring-rules/rr_admin",
            """{"id":"rr_admin","name":"Administración","category":"Vivienda","amount":480000,"dayOfMonth":15,"type":"EXPENSE","frecuencia":"MENSUAL"}""",
            metodo = "PUT",
        )
        assertEquals(HttpStatusCode.OK, mensual.status, mensual.bodyAsText())
        assertEquals(Triple(null, null, 15), filaGuardada("rr_admin"))
    }

    @Test
    fun `el alta guarda la frecuencia con el dia de su ancla y rechaza lo que no se sostiene`() = testApplication {
        application { testModule() }
        val manana = AppClock.today().plusDays(1)
        val alta = enviar(
            "/api/recurring-rules",
            """{"id":"","name":"SOAT","category":"Carro","amount":980000,"dayOfMonth":1,"type":"EXPENSE","frecuencia":"ANUAL","primerVencimiento":"$manana"}""",
        )
        assertEquals(HttpStatusCode.Created, alta.status, alta.bodyAsText())
        val creada = json.decodeFromString(RecurringRule.serializer(), alta.bodyAsText())
        assertEquals(FrecuenciaDelPago.ANUAL, creada.frecuencia)
        assertEquals(manana.toString(), creada.primerVencimiento)
        assertEquals(manana.dayOfMonth, creada.dayOfMonth)
        assertEquals(Triple("ANUAL", manana.toString(), manana.dayOfMonth), filaGuardada(creada.id))

        // Sin la fecha, no.
        val sinFecha = enviar("/api/recurring-rules", """{"id":"","name":"Luz","category":"Luz","amount":90000,"dayOfMonth":5,"type":"EXPENSE","frecuencia":"BIMESTRAL"}""")
        assertEquals(HttpStatusCode.BadRequest, sinFecha.status)
        // Una sola vez con una fecha de un período que ya pasó nunca existiría: no.
        val pasada = AppClock.today().minusMonths(2)
        val vieja = enviar("/api/recurring-rules", """{"id":"","name":"Cuota inicial","category":"Carro","amount":5000000,"dayOfMonth":1,"type":"EXPENSE","frecuencia":"UNA_VEZ","primerVencimiento":"$pasada"}""")
        assertEquals(HttpStatusCode.BadRequest, vieja.status)
        // Mensual explícito: como siempre, sin columnas.
        val mensual = enviar("/api/recurring-rules", """{"id":"","name":"Gimnasio","category":"Gimnasio","amount":120000,"dayOfMonth":5,"type":"EXPENSE","frecuencia":"MENSUAL"}""")
        assertEquals(HttpStatusCode.Created, mensual.status, mensual.bodyAsText())
        assertEquals(Triple(null, null, 5), filaGuardada(json.decodeFromString(RecurringRule.serializer(), mensual.bodyAsText()).id))
    }

    // ── Toda regla mensual da lo de antes ─────────────────────────────────────

    @Test
    fun `toda regla mensual da exactamente lo de antes`() {
        // Los ajustes de tres usuarios y reglas de todos los días, con y sin sellos, con piso por desembolso
        // y por movimiento; cada día de dos años contra una copia del motor de la vuelta 125.
        val ajustes = listOf(
            calendario,
            PeriodSettings(cutoffDay = 25, siCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR),
            PeriodSettings(cutoffDay = 15, iniciosPropios = mapOf("2027-03" to "2027-02-12")),
            // Revisión (M1): un período estirado a mano y una quincena (las obligaciones van por el mes de referencia).
            PeriodSettings(cutoffDay = 1, iniciosPropios = mapOf("2027-02" to "2027-02-20")),
            PeriodSettings(cutoffDay = 15, segundoDia = 30, frecuencia = com.jvillada.movi.shared.model.FrecuenciaDelPeriodo.QUINCENAL),
        )
        val reglas = (1..31).flatMap { dia ->
            listOf(
                RecurringRule("rr_$dia", "R$dia", "Otros", 100L, dia, TransactionType.EXPENSE),
                RecurringRule("rr_m_$dia", "M$dia", "Otros", 100L, dia, TransactionType.EXPENSE, frecuencia = FrecuenciaDelPago.MENSUAL),
                RecurringRule("credit_$dia", "C$dia", "Otros", 100L, dia, TransactionType.EXPENSE, activeFrom = "2026-11-${"%02d".format(minOf(dia, 30))}", arranqueEsDesembolso = true),
                RecurringRule("rr_o_$dia", "O$dia", "Otros", 100L, dia, TransactionType.EXPENSE, activeFrom = "2026-12-03"),
            )
        }
        val sellos = setOf("2026-12", "2027-01", "2027-05")
        ajustes.forEach { a ->
            var hoy = LocalDate.of(2026, 10, 1)
            while (hoy <= LocalDate.of(2028, 1, 31)) {
                reglas.forEach { r ->
                    listOf(emptySet(), sellos).forEach { s ->
                        assertEquals(dueDateForAntes(r, hoy, s, a), dueDateFor(r, hoy, DEFAULT_GRACE_DAYS, s, a), "dueDateFor ${r.id} $hoy $a")
                    }
                    assertEquals(ocurrenciaPorPreguntarAntes(hoy, r, a), ocurrenciaPorPreguntar(hoy, r, a), "porPreguntar ${r.id} $hoy $a")
                    assertEquals(anteriorAntes(hoy, r, a), ocurrenciaAnteriorQuePisaElPeriodo(hoy, r, a), "anterior ${r.id} $hoy $a")
                }
                hoy = hoy.plusDays(3)
            }
        }
    }

    // ── El motor de la vuelta 125, copiado tal cual (el «antes» de la prueba de arriba) ──────────

    private fun dueDateForAntes(rule: RecurringRule, today: LocalDate, occurredPeriods: Set<String>, settings0: PeriodSettings): LocalDate {
        val graceDays = DEFAULT_GRACE_DAYS
        val settings = settings0.mesDeReferencia()
        val natural = if (settings.esMesDeCalendario) {
            occurrenceInMonth(YearMonth.from(today), rule.dayOfMonth)
        } else {
            primeraOcurrenciaDesde(diasDelPeriodo(today, settings).start, rule.dayOfMonth)
        }
        val piso = arranqueDeLaRegla(rule, settings)
        val enGracia = ocurrenciaEnGracia(today, rule.dayOfMonth, graceDays)
        if (enGracia != null && enGracia.isBefore(natural) && periodOf(enGracia) !in occurredPeriods && (piso == null || !enGracia.isBefore(piso))) {
            return enGracia
        }
        var due = if (ChronoUnit.DAYS.between(natural, today) > graceDays) {
            occurrenceInMonth(YearMonth.from(natural).plusMonths(1), rule.dayOfMonth)
        } else {
            natural
        }
        var sinArrancar = 0
        while (piso != null && due.isBefore(piso) && sinArrancar < 24) {
            due = occurrenceInMonth(YearMonth.from(due).plusMonths(1), rule.dayOfMonth)
            sinArrancar++
        }
        var rodadas = 0
        while (periodOf(due) in occurredPeriods && rodadas < 24) {
            due = occurrenceInMonth(YearMonth.from(due).plusMonths(1), rule.dayOfMonth)
            rodadas++
        }
        return due
    }

    private fun ocurrenciaPorPreguntarAntes(hoy: LocalDate, rule: RecurringRule, settings0: PeriodSettings): LocalDate? {
        val settings = settings0.mesDeReferencia()
        val enJuego = ocurrenciaEnJuego(hoy, rule.dayOfMonth, settings)
        val enGracia = ocurrenciaEnGracia(hoy, rule.dayOfMonth, DEFAULT_GRACE_DAYS)
        val usarLaDeGracia = enGracia != null &&
            ruleIsActiveOn(rule, enGracia, settings) &&
            (enJuego == null || (enGracia.isBefore(enJuego) && enJuego.isAfter(hoy)))
        return if (usarLaDeGracia) enGracia else enJuego
    }

    private fun anteriorAntes(hoy: LocalDate, rule: RecurringRule, settings0: PeriodSettings): LocalDate? {
        val settings = settings0.mesDeReferencia()
        val inicio = diasDelPeriodo(hoy, settings).start
        val anterior = ultimaOcurrenciaHasta(inicio.minusDays(1), rule.dayOfMonth)
        if (occurrenceWindow(anterior, settings = settings).endInclusive.isBefore(inicio)) return null
        return anterior.takeIf { ruleIsActiveOn(rule, it, settings) }
    }
}
