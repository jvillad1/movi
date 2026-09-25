package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.reminders.diasDelPeriodo
import com.jvillada.movi.server.reminders.emparejadasComoSellos
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.reminders.loadOccurredBy
import com.jvillada.movi.server.reminders.loadOccurrenceRows
import com.jvillada.movi.server.reminders.parteFijaDelChecklist
import com.jvillada.movi.server.reminders.periodosPorRegla
import com.jvillada.movi.server.reminders.unirOcurridos
import com.jvillada.movi.server.reminders.vencimientoEnElChecklist
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.gastoVariablePorDia
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **«Disponible» ve lo que Movi emparejó solo**, igual que el checklist y que «Próximos».
 *
 * El gasto variable de la tarjeta saca el pago de un fijo del checklist (`PagosDelChecklist.kt`),
 * pero armaba sus períodos ocurridos solo con los sellos a mano. Un pago que el checklist da por
 * hecho sin sello —emparejado solo, derivado en cada lectura— no rodaba el vencimiento del lado del
 * server: en los días de gracia el server decía «esta regla no está en el checklist» mientras el
 * cliente, con `/upcoming` ya rodado, listaba la ocurrencia siguiente como fijo. Emparejado solo o
 * sellado a mano, el pago de una ocurrencia del período ANTERIOR sigue siendo gasto variable de
 * este: no es un fijo suyo (ver `PagoTardioPasadaLaGraciaTest`).
 *
 * Con un «hoy» fijo, sobre [parteFijaDelDisponible], que es lo que llama la ruta del Inicio.
 */
class DisponibleVeLoEmparejadoTest {

    private val uid = "user-disponible-emparejado"
    private val cuenta = "acc-bancolombia"
    private val arriendo = "rr-arriendo"
    private val celular = "rr-celular"

    /** Con corte 25, el 27-sep ya es el período de octubre (25-sep..24-oct); el 24-sep sigue en gracia. */
    private val hoy: LocalDate = LocalDate.of(2026, 9, 27)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:disponible_emparejado_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences, OccurrenceRejections)
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            Users.insert {
                it[id] = uid
                it[email] = "disponible@emparejado.test"
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

    private fun regla(id: String, nombre: String, categoria: String, monto: Long, dia: Int) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[userId] = uid
            it[name] = nombre
            it[category] = categoria
            it[amount] = monto
            it[dayOfMonth] = dia
            it[type] = "EXPENSE"
            it[accountId] = cuenta
        }
    }

    private fun reglaArriendo() = regla(arriendo, "Arriendo", "Vivienda", 1_000_000L, dia = 24)

    private fun gasto(id: String, descripcion: String, categoria: String, monto: Long, fecha: LocalDate) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = "EXPENSE"
            it[amount] = monto
            it[category] = categoria
            it[description] = descripcion
            it[timestamp] = appDateToEpochMillis(fecha) + 15 * 3_600_000L
            // Fuera de «Por confirmar» (el default de la tabla): solo así cuenta como gasto variable,
            // que es lo que estas pruebas miden.
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

    /** Los movimientos que la ruta del Inicio lee: los vivos del período que contiene [dia]. */
    private fun eventosDelPeriodo(dia: LocalDate): List<FinancialEvent> = transaction {
        val dias = diasDelPeriodo(dia, ajustesDelPeriodoSinSuspender(uid))
        loadEventsBetween(uid, appDateToEpochMillis(dias.start), appDateToEpochMillis(dias.endInclusive.plusDays(1)))
    }

    private fun parteFija(dia: LocalDate): Map<String, Long> {
        val eventos = eventosDelPeriodo(dia)
        return transaction { parteFijaDelDisponible(uid, dia, ajustesDelPeriodoSinSuspender(uid), eventos) }
    }

    /** Lo que la ruta calculaba antes: solo con los sellos a mano. */
    private fun parteFijaSoloConSellos(dia: LocalDate): Map<String, Long> {
        val eventos = eventosDelPeriodo(dia)
        return transaction {
            val sellos = loadOccurrenceRows(uid)
            parteFijaDelChecklist(
                reglas = RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() },
                sellos = sellos,
                ocurridos = loadOccurredBy(uid, sellos),
                eventos = eventos,
                hoy = dia,
                settings = ajustesDelPeriodoSinSuspender(uid),
            )
        }
    }

    private fun checklist(ruleId: String, dia: LocalDate): OccurrenceState? = transaction {
        estadosDeLasOcurrenciasReales(uid, dia, ajustesDelPeriodoSinSuspender(uid))
    }.firstOrNull { it.ruleId == ruleId }

    // ── El pago tardío ───────────────────────────────────────────────────────

    /**
     * El arriendo del 24-sep pagado el 26, ya en el período de octubre, con el nombre calcado y sin
     * sello: en la gracia el checklist lo empareja solo, y eso rueda el vencimiento al 24-oct. Pero
     * el 24-sep no es un fijo de ESTE período —el cliente solo resta lo que vence adentro—, y el
     * Disponible parte de un saldo al inicio que todavía tenía esa plata: el pago tiene que seguir
     * contando como gasto variable, o no se contaría en ningún lado. La identidad entera, con números,
     * está en `PagoTardioPasadaLaGraciaTest`.
     */
    @Test
    fun `un pago tardio emparejado solo sigue siendo variable`() {
        reglaArriendo()
        gasto("ev-0926", "Arriendo", "Vivienda", 1_000_000L, LocalDate.of(2026, 9, 26))

        val estado = checklist(arriendo, hoy)
        assertTrue(estado != null && estado.occurred && estado.automatica, "El checklist lo empareja solo")
        assertEquals("ev-0926", estado.eventId)

        val parte = parteFija(hoy)
        assertEquals(null, parte["ev-0926"], "El 24-sep no está en los fijos de este período")
        val variable = gastoVariablePorDia(eventosDelPeriodo(hoy), parte) { epochMillisToAppDateString(it) }
        assertEquals(1_000_000L, variable["2026-09-26"], "El pago cuenta una vez, como variable")
    }

    /** El control: el mismo pago sellado a mano da exactamente el mismo mapa. */
    @Test
    fun `el mismo pago sellado a mano da lo mismo`() {
        reglaArriendo()
        gasto("ev-0926", "Arriendo", "Vivienda", 1_000_000L, LocalDate.of(2026, 9, 26))
        sellar(arriendo, "2026-09", "ev-0926")

        val estado = checklist(arriendo, hoy)
        assertTrue(estado != null && estado.occurred && !estado.automatica, "Sellado a mano, no emparejado")
        assertEquals(emptyMap(), parteFija(hoy))
    }

    /**
     * **Con dudas no hay sello virtual.** Dos «Arriendo» alrededor del vencimiento: el checklist
     * pregunta cuál es, y la tarjeta cuenta lo mismo que contaba solo con los sellos.
     */
    @Test
    fun `con dos candidatos concluyentes no hay sello virtual`() {
        reglaArriendo()
        gasto("ev-0926", "Arriendo", "Vivienda", 1_000_000L, LocalDate.of(2026, 9, 26))
        gasto("ev-0920", "Arriendo", "Vivienda", 1_000_000L, LocalDate.of(2026, 9, 20))

        val estado = checklist(arriendo, hoy)
        assertTrue(estado != null && !estado.occurred, "Con dos concluyentes Movi pregunta")
        assertEquals(emptyList(), transaction { emparejadasComoSellos(uid, hoy, ajustesDelPeriodoSinSuspender(uid)) })
        assertEquals(parteFijaSoloConSellos(hoy), parteFija(hoy))
    }

    // ── La gracia, del lado del server y del cliente ─────────────────────────

    /**
     * El período del dueño: corte 25, octubre arrancando el 24-sep por excepción, hoy 25-sep. El
     * «Celular» del 22-sep está emparejado solo y en gracia, así que `/upcoming` —y con él el
     * checklist del cliente— ya lista el del 22-oct como fijo. El server tiene que ver el mismo
     * vencimiento: si no, un pago de ese Celular de octubre anotado en el período se cuenta dos
     * veces, como fijo en el cliente y como variable acá.
     *
     * El pago de octubre va fechado el 14-oct, anotado por adelantado: el emparejador no mira más de
     * 10 días antes de un vencimiento, así que un pago anterior al 12-oct nunca es candidato del
     * 22-oct; y queda fuera de la ventana del 22-sep (hasta el 2-oct), así que el de septiembre
     * sigue concluyente.
     */
    @Test
    fun `en la gracia el server y el cliente ven el mismo vencimiento`() {
        transaction {
            Users.update({ Users.id eq uid }) { it[Users.periodStarts] = """{"2026-10":"2026-09-24"}""" }
        }
        val elDiaDelDueno = LocalDate.of(2026, 9, 25)
        regla(celular, "Celular", "Celular", 53_077L, dia = 22)
        gasto("ev-0922", "Celular", "Celular", 52_990L, LocalDate.of(2026, 9, 22))
        gasto("ev-1014", "Celular", "Celular", 53_077L, LocalDate.of(2026, 10, 14))

        val estado = checklist(celular, elDiaDelDueno)
        assertTrue(estado != null && estado.occurred && estado.automatica, "El de septiembre está emparejado solo")
        assertEquals("ev-0922", estado.eventId)

        val vence = transaction {
            val periodo = ajustesDelPeriodoSinSuspender(uid)
            val rule = RecurringRules.selectAll().where { RecurringRules.id eq celular }.single().toRule()
            val ocurridos = unirOcurridos(loadOccurredBy(uid), emparejadasComoSellos(uid, elDiaDelDueno, periodo).periodosPorRegla())
            vencimientoEnElChecklist(rule, elDiaDelDueno, periodo, ocurridos[celular].orEmpty())
        }
        assertEquals(LocalDate.of(2026, 10, 22), vence, "El mismo vencimiento que `/upcoming`")
        assertEquals(53_077L, parteFija(elDiaDelDueno)["ev-1014"], "El pago de octubre es el fijo que el cliente ya resta")
    }
}
