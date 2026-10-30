package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.ai.textoDeLaCaja
import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.DiaDeLaCaja
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.GastoVariableDeUnPeriodo
import com.jvillada.movi.shared.model.MovimientoDeLaCaja
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
 * # La caja proyectada en el server (Ola 4)
 *
 * La aritmética está en `:core` (`CajaProyectadaTest`). Acá: el supuesto del gasto del día a día
 * (de qué períodos sale, y que un período que Movi no cubrió entero no cuenta), que cada dueño ve
 * solo lo suyo, que Movi AI arma la caja con la misma lista del período, y que su texto dice de
 * dónde sale cada cifra. Ninguna prueba llama a Anthropic.
 *
 * Las fechas salen del reloj de verdad (corte 1 = mes de calendario): la prueba corre cualquier día.
 */
class CajaProyectadaRoutesTest {

    private val testSecret = "test-secret-for-caja-proyectada-min-32-chars"
    private val duenoId = "user-dueno-caja"
    private val otroId = "user-otro-caja"
    private val json = Json { ignoreUnknownKeys = true }
    private val hoy: LocalDate = AppClock.today()
    private val inicioDelMesPasado: LocalDate = hoy.withDayOfMonth(1).minusMonths(1)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:caja_proyectada_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(
                Apartados, KnownDestinations, OccurrenceRejections, RecurringOccurrences, RecurringRules, Cards, Credits,
                VoidEvents, Events, Accounts, Users,
            )
            SchemaUtils.create(
                Apartados, Users, Accounts, Events, VoidEvents, Credits, Cards, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, KnownDestinations,
            )
            listOf(duenoId, otroId).forEach { uid ->
                Users.insert {
                    it[id] = uid; it[email] = "$uid@caja.test"; it[name] = uid; it[passwordHash] = "hash"
                }
                Accounts.insert {
                    it[id] = "acc_$uid"; it[userId] = uid; it[name] = "Bancolombia"; it[type] = "SAVINGS"; it[balance] = 0L
                }
            }
        }
    }

    private fun anotar(uid: String, id: String, monto: Long, fecha: LocalDate, categoria: String, tipo: String = "EXPENSE") = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = "acc_$uid"
            it[type] = tipo
            it[amount] = monto
            it[currency] = "COP"
            it[category] = categoria
            it[description] = id
            it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun token(uid: String) = JWT.create()
        .withIssuer("movi").withAudience("movi-client")
        .withClaim("userId", uid).withClaim("email", "$uid@caja.test")
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

    private suspend fun ApplicationTestBuilder.gasto(uid: String): GastoDelDiaADia {
        val res = client.get("/api/caja-proyectada/gasto-del-dia-a-dia") { header(HttpHeaders.Authorization, "Bearer ${token(uid)}") }
        assertEquals(HttpStatusCode.OK, res.status)
        return json.decodeFromString(GastoDelDiaADia.serializer(), res.bodyAsText())
    }

    /** El dueño lleva Movi desde antes del mes pasado: ese mes cerrado cuenta entero. */
    private fun duenoConUnMesCerrado() {
        anotar(duenoId, "apertura", 10_000_000L, inicioDelMesPasado.minusDays(3), OPENING_CATEGORY, tipo = "INCOME")
        anotar(duenoId, "mercado", 600_000L, inicioDelMesPasado.plusDays(4), "Comida")
        anotar(duenoId, "taxi", 300_000L, inicioDelMesPasado.plusDays(9), "Transporte")
    }

    @Test
    fun `el gasto del dia a dia es el del mes cerrado, sobre sus dias`() = testApplication {
        application { testModule() }
        duenoConUnMesCerrado()
        val gasto = gasto(duenoId)
        val dias = inicioDelMesPasado.lengthOfMonth()
        assertEquals(900_000L / dias, gasto.porDia)
        assertEquals(1, gasto.periodos.size)
        assertEquals(dias, gasto.periodos.single().dias)
        assertEquals(900_000L, gasto.periodos.single().total)
    }

    @Test
    fun `un mes que Movi no cubrio entero no cuenta`() = testApplication {
        application { testModule() }
        // Empezó a usar Movi a mitad del mes pasado: dos semanas anotadas de cuatro no son su gasto.
        anotar(duenoId, "apertura", 10_000_000L, inicioDelMesPasado.plusDays(10), OPENING_CATEGORY, tipo = "INCOME")
        anotar(duenoId, "mercado", 600_000L, inicioDelMesPasado.plusDays(12), "Comida")
        assertNull(gasto(duenoId).porDia)
    }

    @Test
    fun `cada duenio ve solo su gasto`() = testApplication {
        application { testModule() }
        duenoConUnMesCerrado()
        assertNull(gasto(otroId).porDia, "el otro no tiene ni un movimiento: no hereda el supuesto del dueño")
        assertTrue(gasto(duenoId).porDia != null)
    }

    @Test
    fun `sin token no hay caja`() = testApplication {
        application { testModule() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/caja-proyectada/gasto-del-dia-a-dia").status)
    }

    /** Movi AI arma la caja con la lista del período: un pago de hoy, pendiente, sale hoy. */
    @Test
    fun `la caja de movi ai resta lo pendiente de la lista del periodo`() {
        anotar(duenoId, "apertura", 5_000_000L, hoy, OPENING_CATEGORY, tipo = "INCOME")
        transaction {
            RecurringRules.insert {
                it[id] = "rr_gym"; it[userId] = duenoId; it[name] = "Gimnasio"; it[category] = "Salud"
                it[amount] = 180_000L; it[dayOfMonth] = hoy.dayOfMonth; it[type] = "EXPENSE"
            }
            // La del otro dueño no puede colarse en la caja de este.
            RecurringRules.insert {
                it[id] = "rr_otro"; it[userId] = otroId; it[name] = "Arriendo del otro"; it[category] = "Vivienda"
                it[amount] = 2_000_000L; it[dayOfMonth] = hoy.dayOfMonth; it[type] = "EXPENSE"
            }
        }
        val d = runBlocking { cajaProyectadaDe(duenoId, hoy) }
        val caja = assertNotNull(d.caja)
        assertEquals(5_000_000L, caja.tuPlataHoy)
        assertEquals(hoy.toString(), caja.dias.first().fecha)
        // Vuelta 70: sin un ingreso recurrente la caja mira 30 días (nunca menos que el cierre del mes).
        assertEquals(hoy.plusDays(30).toString(), caja.alCierre.fecha)
        assertTrue(!caja.hastaElSalario)
        assertEquals(listOf("Gimnasio"), caja.dias.first().movimientos.map { it.nombre })
        assertEquals(4_820_000L, caja.alCierre.saldo, "sin período cerrado no hay gasto del día a día que restar")
        assertNull(d.gasto.porDia)
    }

    @Test
    fun `el texto para movi ai dice de donde sale cada cifra y lo que falta`() {
        val texto = textoDeLaCaja(
            CajaDelDueno(
                caja = CajaProyectada(
                    tuPlataHoy = 1_000_000L,
                    gastoDiario = 100_000L,
                    dias = listOf(
                        DiaDeLaCaja("2026-10-02", 1_000_000L),
                        DiaDeLaCaja("2026-10-17", -3_201_123L, listOf(MovimientoDeLaCaja("Cuota Vehiculo 8761", -4_101_123L))),
                    ),
                    sinContar = listOf("Pago tarjeta AMEX: falta el pago mínimo"),
                ),
                pagos = emptyList(),
                gasto = GastoDelDiaADia(100_000L, listOf(GastoVariableDeUnPeriodo("2026-09", 3_100_000L, 31))),
            ),
        )
        assertTrue("En sus cuentas hoy: 1000000" in texto, texto)
        assertTrue("Libre hoy: 1000000" in texto, texto)
        assertTrue("SUPUESTO: gasta como siempre, 100000 por día" in texto, texto)
        assertTrue("2026-09: 3100000 en 31 días" in texto, texto)
        assertTrue("NO ENTRAN" in texto && "AMEX: falta el pago mínimo" in texto, texto)
        assertTrue("PRIMER DÍA EN NEGATIVO: 2026-10-17, lo libre queda en -3201123 (saldo -3201123) por Cuota Vehiculo 8761 (4101123)" in texto, texto)
        assertTrue("30 días desde hoy" in texto, texto)
    }

    /**
     * Vuelta 70: Movi AI arma la caja con la MISMA función de Pagos: hasta el salario, con el pago del
     * período siguiente que vence antes, y lo libre sin el ahorro del período (que no cambia el saldo).
     * Fecha fija (revisión de la 70): martes 20-oct-2026, mes de calendario. El salario y el internet del
     * 1 ya se saldaron en octubre, así que los dos vencen el 1-nov, en el período siguiente.
     */
    @Test
    fun `la caja de movi ai llega al salario, cuenta el pago del siguiente y lo libre sin el ahorro`() {
        val dia = LocalDate.of(2026, 10, 20)
        anotar(duenoId, "apertura", 5_000_000L, dia, OPENING_CATEGORY, tipo = "INCOME")
        transaction {
            RecurringRules.insert {
                it[id] = "rr_salario"; it[userId] = duenoId; it[name] = "Salario"; it[category] = "Ingreso"
                it[amount] = 7_000_000L; it[dayOfMonth] = 1; it[type] = "INCOME"
            }
            RecurringRules.insert {
                it[id] = "rr_internet"; it[userId] = duenoId; it[name] = "Internet"; it[category] = "Servicios"
                it[amount] = 100_000L; it[dayOfMonth] = 1; it[type] = "EXPENSE"
            }
            listOf("rr_salario", "rr_internet").forEach { regla ->
                RecurringOccurrences.insert {
                    it[userId] = duenoId; it[ruleId] = regla; it[period] = "2026-10"; it[confirmedAt] = 1L
                }
            }
            Users.update({ Users.id eq duenoId }) {
                it[ahorroDelPeriodoTipo] = "MONTO"; it[ahorroDelPeriodoValor] = 2_000_000L
            }
        }
        val d = runBlocking { cajaProyectadaDe(duenoId, dia) }
        val caja = assertNotNull(d.caja)
        assertEquals("2026-11-01", caja.alCierre.fecha)
        assertTrue(caja.hastaElSalario)
        assertEquals(
            setOf(MovimientoDeLaCaja("Llega tu salario (esperado)", 7_000_000L), MovimientoDeLaCaja("Internet", -100_000L)),
            caja.alCierre.movimientos.toSet(),
            "el pago del período siguiente entra el día que vence",
        )
        assertEquals(5_000_000L, caja.tuPlataHoy, "el ahorro no mueve plata")
        assertEquals(2_000_000L, caja.ahorro)
        assertEquals(3_000_000L, caja.libreHoy)
        assertTrue(caja.dias.all { it.saldo - it.libre == 2_000_000L })
        assertTrue("NO ES LIBRE: 2000000 de ahorro" in textoDeLaCaja(d), textoDeLaCaja(d))
    }

    /**
     * Revisión de la 70 · **el día del salario sin emparejar**, con el corte del dueño (25, día hábil
     * anterior): el viernes 23-oct arranca el período y el salario no llegó todavía. Su vencimiento
     * vigente se queda hoy, y el del período siguiente llega por `siguienteVencimiento`: la caja cierra
     * en el salario del 25-nov, no a 30 días. El Carro del 26 entra una vez (el de octubre).
     */
    @Test
    fun `el dia del salario sin emparejar la caja cierra en el salario siguiente`() {
        val dia = LocalDate.of(2026, 10, 23)
        anotar(duenoId, "apertura", 5_000_000L, dia, OPENING_CATEGORY, tipo = "INCOME")
        transaction {
            Users.update({ Users.id eq duenoId }) {
                it[periodCutoffDay] = 25; it[periodWeekendRule] = "DIA_HABIL_ANTERIOR"
            }
            RecurringRules.insert {
                it[id] = "rr_salario"; it[userId] = duenoId; it[name] = "Salario"; it[category] = "Ingreso"
                it[amount] = 9_000_000L; it[dayOfMonth] = 25; it[type] = "INCOME"
            }
            RecurringRules.insert {
                it[id] = "rr_carro"; it[userId] = duenoId; it[name] = "Carro"; it[category] = "Créditos"
                it[amount] = 980_000L; it[dayOfMonth] = 26; it[type] = "EXPENSE"
            }
        }
        val d = runBlocking { cajaProyectadaDe(duenoId, dia) }
        val caja = assertNotNull(d.caja)
        assertEquals("2026-11-25", caja.alCierre.fecha)
        assertTrue(caja.hastaElSalario)
        assertEquals(MovimientoDeLaCaja("Llega tu salario (esperado)", 9_000_000L), caja.salario)
        val movimientos = caja.dias.flatMap { it.movimientos }
        assertEquals(1, movimientos.count { it.nombre == "Carro" }, "el Carro de noviembre (26) queda pasado el horizonte")
        assertEquals(1, movimientos.count { it.nombre == "Llega tu salario (esperado)" })
    }
    /**
     * Vuelta 75 · **sin salario, con el período elegido**: el dueño dijo «25 · es el día que me pagan» y
     * no anotó ningún ingreso. La caja de Movi AI llega al arranque del período siguiente (el 25-nov,
     * miércoles), no a 30 días, y el texto lo dice.
     */
    @Test
    fun `sin salario y con el corte elegido la caja de movi ai llega al periodo siguiente`() {
        val dia = LocalDate.of(2026, 10, 29)
        anotar(duenoId, "apertura", 5_000_000L, dia, OPENING_CATEGORY, tipo = "INCOME")
        transaction {
            Users.update({ Users.id eq duenoId }) {
                it[periodCutoffDay] = 25; it[periodWeekendRule] = "DIA_HABIL_ANTERIOR"
            }
        }
        val d = runBlocking { cajaProyectadaDe(duenoId, dia) }
        val caja = assertNotNull(d.caja)
        assertEquals("2026-11-25", caja.alCierre.fecha)
        assertTrue(caja.hastaElPeriodoSiguiente && !caja.hastaElSalario)
        assertTrue("el día que arranca su período siguiente" in textoDeLaCaja(d), textoDeLaCaja(d))
    }

    /**
     * Revisión de la 75 · **el salario que la guía crea cuando «ya te pagaron este período»**: la regla
     * nace con `activeFrom` en el arranque del período siguiente (el 25-nov, con corte 25), así que la
     * ocurrencia de este período (la del 25-oct, esperada el viernes 23) **no existe**: ni vencida en
     * `/upcoming`, ni por cobrar en la lista del período —de donde salen «Te quedan», «Listo» y la caja—;
     * y la del siguiente sí, la que cierra la caja.
     */
    @Test
    fun `el salario que arranca en el periodo siguiente no cuenta en este y si en el siguiente`() {
        val dia = LocalDate.of(2026, 10, 30)
        anotar(duenoId, "apertura", 2_000_000L, dia, OPENING_CATEGORY, tipo = "INCOME")
        transaction {
            Users.update({ Users.id eq duenoId }) {
                it[periodCutoffDay] = 25; it[periodWeekendRule] = "DIA_HABIL_ANTERIOR"
            }
            RecurringRules.insert {
                it[id] = "rr_salario"; it[userId] = duenoId; it[name] = "Salario"; it[category] = "Salario"
                it[amount] = 6_500_000L; it[dayOfMonth] = 25; it[type] = "INCOME"; it[activeFrom] = "2026-11-25"
            }
        }
        val upcoming = runBlocking { proximosPagos(duenoId, dia) }
        val delSalario = upcoming.filter { it.rule.id == "rr_salario" }
        assertTrue(delSalario.none { it.status == com.jvillada.movi.shared.model.PaymentStatus.OVERDUE }, "$delSalario")
        assertTrue(delSalario.all { it.dueDate >= "2026-11-25" }, "$delSalario")

        val ajustes = runBlocking { com.jvillada.movi.server.time.ajustesDePeriodoDe(duenoId) }
        val periodo = com.jvillada.movi.shared.model.periodoDe(appDateToEpochMillis(dia) + 12L * 3_600_000L, ajustes)
        val ocurrencias = runBlocking { ocurrenciasDelPeriodo(duenoId, dia) }
        val delPeriodo = com.jvillada.movi.shared.model.checklistDelPeriodo(upcoming, ocurrencias, periodo, ajustes)
        assertTrue(delPeriodo.none { it.ruleId == "rr_salario" }, "el salario de este período ya está en el saldo: $delPeriodo")
        assertEquals(0L, com.jvillada.movi.shared.model.ingresosPorRecibirDelPeriodo(delPeriodo), "nada por cobrar en «Te quedan»")

        val caja = assertNotNull(runBlocking { cajaProyectadaDe(duenoId, dia) }.caja)
        assertEquals("2026-11-25", caja.alCierre.fecha, "la del período siguiente sí: cierra la caja")
        assertTrue(caja.hastaElSalario)
        assertEquals(MovimientoDeLaCaja("Llega tu salario (esperado)", 6_500_000L), caja.salario)
        assertEquals(2_000_000L, caja.dias.first().saldo, "hoy no sube nada: lo de las cuentas, tal cual")
        assertEquals(1, caja.dias.flatMap { it.movimientos }.count { it.monto > 0 }, "una sola subida, la del 25-nov")
    }

    /**
     * Revisión de la 70 · **las compras con tarjeta sin pagar** que manda el resumen del Inicio: el gasto
     * contado en la tarjeta en el período menos lo que entró a la tarjeta en el período. Un traspaso a la
     * tarjeta la baja, un ajuste de saldo no; lo de otro período no cuenta.
     */
    @Test
    fun `las compras con tarjeta sin pagar son las del periodo menos lo pagado en el periodo`() {
        val desde = LocalDate.of(2026, 10, 1)
        val hasta = LocalDate.of(2026, 11, 1)
        transaction {
            Accounts.insert {
                it[id] = "tc_$duenoId"; it[userId] = duenoId; it[name] = "Master Black"; it[type] = "CREDIT_CARD"; it[balance] = 0L
            }
        }
        fun enLaTarjeta(id: String, monto: Long, fecha: LocalDate, categoria: String, tipo: String) = transaction {
            Events.insert {
                it[Events.id] = id; it[userId] = duenoId; it[accountId] = "tc_$duenoId"; it[type] = tipo; it[amount] = monto
                it[currency] = "COP"; it[category] = categoria; it[description] = id
                it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L; it[reconciliationStatus] = "RECONCILED"
            }
        }
        enLaTarjeta("mercado", 400_000L, LocalDate.of(2026, 10, 5), "Comida", "EXPENSE")
        enLaTarjeta("viaje", 900_000L, LocalDate.of(2026, 10, 12), "Viajes", "EXPENSE")
        enLaTarjeta("de-septiembre", 2_000_000L, LocalDate.of(2026, 9, 20), "Viajes", "EXPENSE")
        enLaTarjeta("pago", 300_000L, LocalDate.of(2026, 10, 20), "Traspaso", "INCOME")
        enLaTarjeta("ajuste", 5_000_000L, LocalDate.of(2026, 10, 21), "Ajuste de saldo", "INCOME")
        // Un gasto de la cuenta de ahorros no es una compra con tarjeta.
        anotar(duenoId, "almuerzo", 50_000L, LocalDate.of(2026, 10, 6), "Comida")
        val compras = transaction {
            val tipos = accountTypesFor(duenoId)
            val inicio = appDateToEpochMillis(desde)
            val fin = appDateToEpochMillis(hasta)
            comprasConTarjetaSinPagarDe(duenoId, inicio, fin, emptySet(), tipos, movimientosDeFlujo(duenoId, inicio, fin, emptySet(), tipos))
        }
        assertEquals(400_000L + 900_000L - 300_000L, compras)
    }
}
