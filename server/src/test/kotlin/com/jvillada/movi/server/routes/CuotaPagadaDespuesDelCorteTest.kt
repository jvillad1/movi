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
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **El Disponible del Inicio no resta dos veces la cuota pagada después del corte**, de punta a
 * punta sobre la base: lo que arma [disponibleDelServidor] con los movimientos del período MÁS el
 * historial de pagos de deuda que ahora carga.
 *
 * El caso del dueño (27-sep-2026, corte 25): Crediágil (día 15) pagada el 5-sep y otra vez el
 * 27-sep. El período de octubre va del 25-sep al 24-oct, así que el pago del 5 no está entre sus
 * movimientos; sin cargarlo aparte, el pago del 27 se leía como el de septiembre, la cuota del
 * 15-oct no lo reclamaba y el pago contaba como «otro pago de deuda» además de como fijo.
 */
class CuotaPagadaDespuesDelCorteTest {

    private val uid = "user-cuota-corte"
    private val ahorros = "acc-ahorros"
    private val credito = "acc-crediagil"
    private val hoy: LocalDate = LocalDate.of(2026, 9, 27)

    private val regla = RecurringRule(
        id = "$CREDIT_RULE_PREFIX$credito",
        name = "Cuota Crediágil 3090",
        category = CUOTA_CATEGORY,
        amount = 26_485,
        dayOfMonth = 15,
        type = TransactionType.EXPENSE,
    )

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:cuota_despues_del_corte_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
                it[email] = "cuota@corte.test"
                it[name] = "Dueño"
                it[passwordHash] = "hash"
                it[periodCutoffDay] = 25
            }
            Accounts.insert {
                it[id] = ahorros
                it[userId] = uid
                it[name] = "Bancolombia Ahorros"
                it[type] = "SAVINGS"
            }
            Accounts.insert {
                it[id] = credito
                it[userId] = uid
                it[name] = "Crediágil 3090"
                it[type] = "LOAN"
            }
        }
    }

    /** Las dos patas de una cuota, como las escribe `pagoDeCuotaLegs`: la cuota sale, el capital entra. */
    private fun cuota(id: String, fecha: LocalDate) = transaction {
        val ts = appDateToEpochMillis(fecha) + 15 * 3_600_000L
        listOf(
            Triple("$id-sale", ahorros, TransactionType.EXPENSE to 26_485L),
            Triple("$id-entra", credito, TransactionType.INCOME to 12_157L),
        ).forEach { (eventId, cuenta, tipoYMonto) ->
            Events.insert {
                it[Events.id] = eventId
                it[userId] = uid
                it[accountId] = cuenta
                it[type] = tipoYMonto.first.name
                it[amount] = tipoYMonto.second
                it[category] = CUOTA_CATEGORY
                it[description] = "Pago cuota Crediágil"
                it[timestamp] = ts
                it[transferId] = "t-$id"
                it[reconciliationStatus] = "RECONCILED"
            }
        }
    }

    private fun otrosPagosDeDeuda(): Long = transaction {
        val periodo = ajustesDelPeriodoSinSuspender(uid)
        val dias = diasDelPeriodo(hoy, periodo)
        disponibleDelServidor(
            uid = uid,
            hoy = hoy,
            periodo = periodo,
            monthStart = appDateToEpochMillis(dias.start),
            monthEnd = appDateToEpochMillis(dias.endInclusive.plusDays(1)),
            voidedIds = emptySet(),
            reglasDeCredito = listOf(regla),
        ).pagosDeDeudaFueraDelChecklist
    }

    @Test
    fun `con septiembre ya pagado, el pago del 27 es la cuota de octubre y no se resta otra vez`() {
        cuota("c-0905", LocalDate.of(2026, 9, 5))
        cuota("c-0927", LocalDate.of(2026, 9, 27))
        assertEquals(0L, otrosPagosDeDeuda())
    }

    /**
     * El control: **sin** el pago del 5, el del 27 es ambiguo como el de AMEX (¿septiembre tarde u
     * octubre adelantado?) y se queda en septiembre, el lado barato. La cuota del 15-oct no lo
     * reclama, y cuenta como otro pago de deuda. Si esto diera 0, la prueba de arriba no probaría que
     * el historial se está cargando.
     */
    @Test
    fun `sin el pago de septiembre, el del 27 sigue siendo el de septiembre`() {
        cuota("c-0927", LocalDate.of(2026, 9, 27))
        assertEquals(26_485L, otrosPagosDeDeuda())
    }
}
