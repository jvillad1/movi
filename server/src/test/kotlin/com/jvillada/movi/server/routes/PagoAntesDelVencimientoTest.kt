package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RecurringRule
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **Pagar ANTES del vencimiento se puede confirmar** (25-oct-2026, Plan → Pagos del período).
 *
 * El caso real, con datos inventados: un recurrente del día 30, período del dueño con corte 25. El
 * 25 de octubre el dueño pagó la cuota que vence el 30 de octubre. Plan preguntaba «¿Ya pagaste…?»
 * con ese movimiento a la vista —`resolverOcurrencias` ofrece lo que llegó antes del vencimiento—,
 * pero «Sí, fue este» contestaba 400 «Ese vencimiento todavía no llegó»: el techo de
 * [sellarOcurrencia] rechazaba todo vencimiento posterior a hoy, viniera o no con su movimiento.
 *
 * El server no puede ofrecer una pregunta que después rechaza. Lo que sigue rechazándose: tildar
 * sin movimiento lo que no llegó, y el vencimiento de un período que todavía no empezó.
 */
class PagoAntesDelVencimientoTest {

    private val uid = "user-pago-adelantado"
    private val otro = "user-otro-adelantado"
    private val bancolombia = "acc-bancolombia"
    private val nu = "acc-nu"
    private val cuentaDelOtro = "acc-del-otro"

    /** Domingo 25-oct-2026: el período del dueño (corte 25) va del 25-oct al 24-nov. */
    private val hoy: LocalDate = LocalDate.of(2026, 10, 25)

    private val prepagada = "rr-prepagada"

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:pago_adelantado_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, Credits, Cards,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            for ((id, correo) in listOf(uid to "adelantado@movi.test", otro to "otro@movi.test")) {
                Users.insert {
                    it[Users.id] = id
                    it[email] = correo
                    it[name] = "Dueño"
                    it[passwordHash] = "hash"
                    it[periodCutoffDay] = 25
                }
            }
            for ((id, dueno) in listOf(bancolombia to uid, nu to uid, cuentaDelOtro to otro)) {
                Accounts.insert {
                    it[Accounts.id] = id
                    it[userId] = dueno
                    it[name] = id
                    it[type] = "SAVINGS"
                }
            }
        }
    }

    // ── Armado ────────────────────────────────────────────────────────────────

    private fun regla(
        id: String,
        nombre: String,
        dia: Int,
        monto: Long,
        categoria: String = "Salud",
        dueno: String = uid,
        cuenta: String = bancolombia,
    ) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[userId] = dueno
            it[name] = nombre
            it[category] = categoria
            it[amount] = monto
            it[dayOfMonth] = dia
            it[type] = "EXPENSE"
            it[accountId] = cuenta
        }
    }

    private fun movimiento(
        id: String,
        nota: String,
        fecha: LocalDate,
        monto: Long,
        categoria: String = "Salud",
        cuenta: String = bancolombia,
        dueno: String = uid,
        hora: LocalTime = LocalTime.NOON,
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = dueno
            it[accountId] = cuenta
            it[type] = "EXPENSE"
            it[amount] = monto
            it[Events.category] = categoria
            it[description] = nota
            it[timestamp] = appDateToEpochMillis(fecha) + hora.toSecondOfDay() * 1_000L
        }
    }

    private fun sello(ruleId: String, period: String, eventId: String?) = transaction {
        RecurringOccurrences.insert {
            it[RecurringOccurrences.userId] = uid
            it[RecurringOccurrences.ruleId] = ruleId
            it[RecurringOccurrences.period] = period
            it[RecurringOccurrences.eventId] = eventId
            it[confirmedAt] = 1L
        }
    }

    private fun reglaDe(id: String, dueno: String = uid): RecurringRule = transaction {
        RecurringRules.selectAll()
            .where { (RecurringRules.id eq id) and (RecurringRules.userId eq dueno) }
            .first().toRule()
    }

    private fun sellar(ruleId: String, period: String, eventId: String?, dueno: String = uid): MarcaResult =
        transaction { sellarOcurrencia(dueno, reglaDe(ruleId, dueno), period, eventId, hoy) }

    private fun checklist(dueno: String = uid): List<OccurrenceState> = transaction {
        estadosDeLasOcurrenciasReales(dueno, hoy, ajustesDelPeriodoSinSuspender(dueno))
    }

    /**
     * El escenario del 25-oct: la cuota del 30, el sello de septiembre puesto con el pago tardío del
     * 3-oct, y el pago de hoy a las 23:47. El pago de hoy sale de OTRA cuenta: por eso Movi no lo
     * empareja solo (categoría + monto, pero no la cuenta) y pregunta.
     */
    private fun elCasoReal() {
        regla(id = prepagada, nombre = "Prepagada Familiar", dia = 30, monto = 138_600L)
        movimiento(id = "ev-3-oct", nota = "Prepagada Familiar", fecha = LocalDate.of(2026, 10, 3), monto = 138_600L)
        sello(prepagada, "2026-09", "ev-3-oct")
        movimiento(
            id = "ev-25-oct", nota = "Medicina Prepagada S A", fecha = hoy, monto = 138_600L,
            cuenta = nu, hora = LocalTime.of(23, 47),
        )
    }

    // ── El caso ───────────────────────────────────────────────────────────────

    @Test
    fun `el pago del 25 confirma la cuota que vence el 30 y el checklist queda pagado`() {
        elCasoReal()

        val antes = checklist().single { it.ruleId == prepagada }
        assertFalse(antes.occurred, "Precondición: Movi pregunta, no decide")
        assertEquals("2026-10", antes.period, "La pregunta es por la cuota del 30 de octubre")
        assertEquals(listOf("ev-25-oct"), antes.candidates.map { it.id })

        val resultado = sellar(prepagada, "2026-10", "ev-25-oct")

        val ok = assertIs<MarcaResult.Ok>(resultado, "«Sí, fue este» sobre un pago real hecho antes del vencimiento")
        assertEquals("2026-10", ok.occurrence.period, "El sello va al mes del VENCIMIENTO")
        val despues = checklist().single { it.ruleId == prepagada }
        assertTrue(despues.occurred, "El checklist queda pagado")
        assertEquals("ev-25-oct", despues.eventId)
        assertEquals("2026-10", despues.period)
    }

    @Test
    fun `sin movimiento, lo que no llego sigue sin poder tildarse`() {
        elCasoReal()

        val resultado = sellar(prepagada, "2026-10", eventId = null)

        val error = assertIs<MarcaResult.Error>(resultado)
        assertEquals(HttpStatusCode.BadRequest, error.code)
    }

    @Test
    fun `el vencimiento del periodo siguiente sigue en 400 aun con movimiento`() {
        elCasoReal()

        // El 30 de noviembre cae en el período que arranca el 25 de noviembre: no es el en curso.
        val resultado = sellar(prepagada, "2026-11", "ev-25-oct")

        val error = assertIs<MarcaResult.Error>(resultado)
        assertEquals(HttpStatusCode.BadRequest, error.code)
    }

    @Test
    fun `un movimiento fechado despues de hoy no adelanta nada`() {
        regla(id = prepagada, nombre = "Prepagada Familiar", dia = 30, monto = 138_600L)
        // De otra cuenta y con otro nombre: no es concluyente, así que hoy se ofrecía como candidato.
        movimiento(id = "ev-27-oct", nota = "Medicina Prepagada S A", fecha = hoy.plusDays(2), monto = 138_600L, cuenta = nu)

        val resultado = sellar(prepagada, "2026-10", "ev-27-oct")

        val error = assertIs<MarcaResult.Error>(resultado)
        assertEquals(HttpStatusCode.BadRequest, error.code)
        assertTrue(
            checklist().none { it.ruleId == prepagada && "ev-27-oct" in it.candidates.map { c -> c.id } },
            "Y tampoco se ofrece: lo que no se puede sellar no se pregunta",
        )
    }

    @Test
    fun `el mismo movimiento no salda dos ocurrencias`() {
        elCasoReal()
        regla(id = "rr-otra", nombre = "Otra cuota", dia = 28, monto = 138_600L)
        assertIs<MarcaResult.Ok>(sellar(prepagada, "2026-10", "ev-25-oct"))

        val segunda = sellar("rr-otra", "2026-10", "ev-25-oct")

        val error = assertIs<MarcaResult.Error>(segunda)
        assertEquals(HttpStatusCode.Conflict, error.code)
        // Y repetir el mismo sello es idempotente.
        assertIs<MarcaResult.Ok>(sellar(prepagada, "2026-10", "ev-25-oct"))
    }

    // ── Aislamiento ───────────────────────────────────────────────────────────

    @Test
    fun `el movimiento de otro usuario no adelanta el pago de nadie`() {
        elCasoReal()
        movimiento(id = "ev-ajeno", nota = "Prepagada Familiar", fecha = hoy, monto = 138_600L, cuenta = cuentaDelOtro, dueno = otro)
        regla(id = "rr-del-otro", nombre = "Prepagada Familiar", dia = 30, monto = 138_600L, dueno = otro, cuenta = cuentaDelOtro)

        val conElAjeno = sellar(prepagada, "2026-10", "ev-ajeno")
        assertEquals(HttpStatusCode.BadRequest, assertIs<MarcaResult.Error>(conElAjeno).code)

        // Y sellar el del dueño no toca al otro.
        assertIs<MarcaResult.Ok>(sellar(prepagada, "2026-10", "ev-25-oct"))
        val delOtro = transaction {
            RecurringOccurrences.selectAll().where { RecurringOccurrences.userId eq otro }.count()
        }
        assertEquals(0L, delOtro)
        assertTrue(checklist(otro).single { it.ruleId == "rr-del-otro" }.occurred, "El otro empareja solo lo suyo")
    }

    // ── Coherencia ────────────────────────────────────────────────────────────

    /**
     * **Lo que se ofrece se puede sellar.** Por cada candidato del checklist —vencido o por llegar,
     * en gracia o en el período—, sellar con ese `period` y ese `eventId` sale bien. Cada sello se
     * deshace antes del siguiente para que la reserva de uno no le quite el lugar a otro.
     */
    @Test
    fun `todo candidato que el checklist ofrece se puede sellar con su periodo`() {
        elCasoReal()
        // Vencido dentro del período: el del 25 (hoy), con dos candidatos.
        regla(id = "rr-hoy", nombre = "Gimnasio", dia = 25, monto = 180_000L, categoria = "Deporte")
        movimiento(id = "ev-gym-1", nota = "Pago 1", fecha = hoy, monto = 180_000L, categoria = "Deporte", cuenta = nu)
        movimiento(id = "ev-gym-2", nota = "Pago 2", fecha = hoy, monto = 170_000L, categoria = "Deporte")
        // En gracia del período anterior: el del 22-oct, todavía abierto.
        regla(id = "rr-gracia", nombre = "Internet", dia = 22, monto = 90_000L, categoria = "Servicios")
        movimiento(id = "ev-internet", nota = "ETB", fecha = LocalDate.of(2026, 10, 21), monto = 95_000L, categoria = "Servicios")
        // Por llegar, con candidato de antes y uno fechado después de hoy (no se ofrece).
        regla(id = "rr-futuro", nombre = "Colegio", dia = 1, monto = 900_000L, categoria = "Educación")
        movimiento(id = "ev-colegio", nota = "Pensión", fecha = hoy, monto = 850_000L, categoria = "Educación")
        movimiento(id = "ev-colegio-futuro", nota = "Pensión", fecha = hoy.plusDays(3), monto = 900_000L, categoria = "Educación", cuenta = nu)

        val ofrecidos = checklist().flatMap { estado -> estado.candidates.map { estado to it.id } }
        assertTrue(ofrecidos.size >= 5, "Precondición: el escenario ofrece candidatos de todas las formas ($ofrecidos)")

        for ((estado, eventId) in ofrecidos) {
            transaction {
                val resultado = sellarOcurrencia(uid, reglaDe(estado.ruleId), estado.period, eventId, hoy)
                assertIs<MarcaResult.Ok>(
                    resultado,
                    "Se ofreció $eventId para ${estado.ruleId} ${estado.period} y el sello lo rechazó: $resultado",
                )
                rollback()
            }
        }
        assertNotNull(ofrecidos.firstOrNull { it.first.ruleId == "rr-futuro" }, "Lo que llegó antes se ofrece")
        assertTrue(ofrecidos.none { it.second == "ev-colegio-futuro" }, "Lo fechado después de hoy no se ofrece")
    }
}
