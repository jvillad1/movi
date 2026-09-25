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
import com.jvillada.movi.server.reminders.diasDelPeriodo
import com.jvillada.movi.server.reminders.emparejadasComoSellos
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.gastoVariablePorDia
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **El pago tardío no vuelve al gasto variable pasada la gracia.**
 *
 * El arriendo vence el 23; con corte 25 el dueño lo paga el 26, ya en el período siguiente. Mientras
 * dura la gracia (hasta el 28) el checklist pregunta por el del 23-sep y lo empareja solo con ese
 * pago. El 29 la gracia se acaba, el checklist pasa a preguntar por el 23-oct y el emparejamiento
 * del 23-sep dejaba de salir: el pago del 26 volvía a sumar como gasto variable y el «Disponible»
 * bajaba un arriendo entero por el resto del período.
 *
 * Con un «hoy» fijo (el 30, gracia vencida), sobre lo que llaman la ruta del Inicio
 * ([parteFijaDelDisponible]) y «Próximos» ([proximosPagos]).
 */
class PagoTardioPasadaLaGraciaTest {

    private val uid = "user-pago-tardio"
    private val cuenta = "acc-bancolombia"
    private val arriendo = "rr-arriendo"

    /** Período de octubre con corte 25: 25-sep..24-oct. El 23-sep lleva 7 días vencido. */
    private val hoy: LocalDate = LocalDate.of(2026, 9, 30)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:pago_tardio_gracia_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            Users.insert {
                it[id] = uid
                it[email] = "pago@tardio.test"
                it[name] = "Dueño"
                it[passwordHash] = "hash"
                it[periodCutoffDay] = 25
            }
            Accounts.insert {
                it[id] = cuenta
                it[userId] = uid
                it[name] = "Bancolombia Ahorros"
                it[type] = "SAVINGS"
            }
        }
    }

    // ── Armado ────────────────────────────────────────────────────────────────

    private fun regla(id: String, nombre: String, dia: Int, monto: Long = 1_000_000L) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[userId] = uid
            it[name] = nombre
            it[category] = "Vivienda"
            it[amount] = monto
            it[dayOfMonth] = dia
            it[type] = "EXPENSE"
            it[accountId] = cuenta
        }
    }

    private fun reglaArriendo() = regla(arriendo, "Arriendo", dia = 23)

    private fun gasto(id: String, descripcion: String, fecha: LocalDate, monto: Long = 1_000_000L) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = "EXPENSE"
            it[amount] = monto
            it[category] = "Vivienda"
            it[description] = descripcion
            it[timestamp] = appDateToEpochMillis(fecha) + 15 * 3_600_000L
            // Fuera de «Por confirmar»: solo así cuenta como gasto variable, que es lo que se mide.
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    private fun sellar(ruleId: String, periodo: String, eventId: String?) = transaction {
        RecurringOccurrences.insert {
            it[userId] = uid
            it[RecurringOccurrences.ruleId] = ruleId
            it[period] = periodo
            it[RecurringOccurrences.eventId] = eventId
            it[confirmedAt] = 1L
        }
    }

    private fun rechazar(ruleId: String, eventId: String) = transaction {
        OccurrenceRejections.insert {
            it[userId] = uid
            it[OccurrenceRejections.ruleId] = ruleId
            it[OccurrenceRejections.eventId] = eventId
            it[rejectedAt] = 1L
        }
    }

    private fun eventosDelPeriodo(): List<FinancialEvent> = transaction {
        val dias = diasDelPeriodo(hoy, ajustesDelPeriodoSinSuspender(uid))
        loadEventsBetween(uid, appDateToEpochMillis(dias.start), appDateToEpochMillis(dias.endInclusive.plusDays(1)))
    }

    private fun parteFija(): Map<String, Long> {
        val eventos = eventosDelPeriodo()
        return transaction { parteFijaDelDisponible(uid, hoy, ajustesDelPeriodoSinSuspender(uid), eventos) }
    }

    private fun variable(parte: Map<String, Long>): Map<String, Long> =
        gastoVariablePorDia(eventosDelPeriodo(), parte) { epochMillisToAppDateString(it) }

    private fun emparejadas(): List<RecurringOccurrence> =
        transaction { emparejadasComoSellos(uid, hoy, ajustesDelPeriodoSinSuspender(uid)) }

    // ── El pago tardío ───────────────────────────────────────────────────────

    /**
     * El caso: pagado el 26 con el nombre calcado, hoy 30. El checklist ya no pregunta por el
     * 23-sep, pero el pago sigue siendo el de ese vencimiento y sale del variable.
     */
    @Test
    fun `pasada la gracia el pago tardio sigue fuera del variable`() {
        reglaArriendo()
        gasto("ev-0926", "Arriendo", LocalDate.of(2026, 9, 26))

        // El checklist ya no pregunta por el 23-sep (y el 23-oct todavía no llegó): esa respuesta no
        // cambia, lo que cambia es que el emparejamiento del 23-sep se sigue derivando aparte.
        val estado = transaction { estadosDeLasOcurrenciasReales(uid, hoy, ajustesDelPeriodoSinSuspender(uid)) }
            .firstOrNull { it.ruleId == arriendo }
        assertNull(estado, "El checklist ya no pregunta por el de septiembre")

        assertEquals(
            listOf(RecurringOccurrence(arriendo, "2026-09", "ev-0926", confirmedAt = appDateToEpochMillis(LocalDate.of(2026, 9, 26)) + 15 * 3_600_000L)),
            emparejadas(),
        )
        val parte = parteFija()
        assertEquals(1_000_000L, parte["ev-0926"], "El pago del arriendo de septiembre no es gasto variable")
        assertFalse(variable(parte).containsKey("2026-09-26"), "El 26 no tiene gasto variable")
    }

    /** El control: sellado a mano da lo mismo, y no sale además como emparejado. */
    @Test
    fun `sellado a mano da lo mismo que antes`() {
        reglaArriendo()
        gasto("ev-0926", "Arriendo", LocalDate.of(2026, 9, 26))
        sellar(arriendo, "2026-09", "ev-0926")

        assertEquals(emptyList(), emparejadas(), "Lo sellado no se vuelve a derivar")
        assertEquals(mapOf("ev-0926" to 1_000_000L), parteFija())
    }

    /** Con dos pagos concluyentes Movi no sabe cuál es: ninguno sale del variable por su cuenta. */
    @Test
    fun `con dos concluyentes no cuenta`() {
        reglaArriendo()
        gasto("ev-0926", "Arriendo", LocalDate.of(2026, 9, 26))
        gasto("ev-0920", "Arriendo", LocalDate.of(2026, 9, 20))

        assertEquals(emptyList(), emparejadas())
        assertNull(parteFija()["ev-0926"])
        assertEquals(1_000_000L, variable(parteFija())["2026-09-26"], "Con dudas sigue siendo variable")
    }

    /** Un «no fue este» sobre el pago lo deja fuera, igual que en el checklist. */
    @Test
    fun `un rechazo se respeta`() {
        reglaArriendo()
        gasto("ev-0926", "Arriendo", LocalDate.of(2026, 9, 26))
        rechazar(arriendo, "ev-0926")

        assertEquals(emptyList(), emparejadas())
        assertNull(parteFija()["ev-0926"])
    }

    /**
     * **Un movimiento, una ocurrencia.** «Administración» (día 28) y el arriendo comparten
     * categoría, cuenta y monto exacto, así que el pago del 26 sin nombre es concluyente para las
     * dos: para la del 28-sep, que el checklist pregunta hoy, y para el 23-sep del arriendo. Lo usa
     * el checklist —la respuesta que el dueño ve— y el arriendo no lo puede tomar además.
     */
    @Test
    fun `el mismo movimiento candidato de dos ocurrencias se usa una sola vez`() {
        reglaArriendo()
        regla("rr-administracion", "Administración", dia = 28)
        gasto("ev-0926", "Pago", LocalDate.of(2026, 9, 26))

        val estado = transaction { estadosDeLasOcurrenciasReales(uid, hoy, ajustesDelPeriodoSinSuspender(uid)) }
            .single { it.ruleId == "rr-administracion" }
        assertTrue(estado.occurred && estado.eventId == "ev-0926", "El checklist lo empareja con la administración")

        val sellos = emparejadas()
        assertEquals(listOf("rr-administracion"), sellos.filter { it.eventId == "ev-0926" }.map { it.ruleId })
        assertEquals(mapOf("ev-0926" to 1_000_000L), parteFija())
    }

    // ── «Próximos» no retrocede ──────────────────────────────────────────────

    /**
     * Lo emparejado del 23-sep no vuelve a mostrar vencido el arriendo ni le mueve el vencimiento
     * hacia atrás: sigue el 23-oct, igual que sin el pago (la gracia ya lo había rodado).
     */
    @Test
    fun `proximos no retrocede`() {
        reglaArriendo()
        val sinPago = runBlocking { proximosPagos(uid, hoy) }.single { it.rule.id == arriendo }
        gasto("ev-0926", "Arriendo", LocalDate.of(2026, 9, 26))
        val conPago = runBlocking { proximosPagos(uid, hoy) }.single { it.rule.id == arriendo }

        assertEquals("2026-10-23", sinPago.dueDate)
        assertEquals("2026-10-23", conPago.dueDate, "El emparejamiento anterior no mueve el vencimiento")
        assertEquals(PaymentStatus.UPCOMING, conPago.status)
    }
}
