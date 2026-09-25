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
import com.jvillada.movi.server.reminders.loadOccurredBy
import com.jvillada.movi.server.reminders.periodosPorRegla
import com.jvillada.movi.server.reminders.unirOcurridos
import com.jvillada.movi.server.reminders.vencimientoEnElChecklist
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.PaymentStatus
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertTrue

/**
 * **Cada peso cuenta exactamente una vez en el Disponible, también el del pago tardío.**
 *
 * El arriendo vence el 23; con corte 25 el dueño lo paga el 26, ya en el período que arrancó el 25.
 * El Disponible parte de lo que había en Tu plata al empezar el período —que todavía tenía esa
 * plata—, y el checklist del período solo resta como fijo lo que vence adentro: el 23-oct, no el
 * 23-sep. Así que el pago del 26 es plata que salió en este período sin ser un fijo suyo, y tiene
 * que contar como gasto variable, una vez: en la gracia, pasada la gracia, emparejado solo o
 * sellado a mano.
 *
 * Cada prueba verifica la identidad entera con los números que manda el server
 * ([disponibleDelServidor]) y los fijos del checklist: lo que queda = B + E − F − V − R, con B lo
 * que había al empezar, E lo que entró, F los fijos de ESTE período, V el resto del gasto variable
 * y R el arriendo pagado tarde.
 */
class PagoTardioPasadaLaGraciaTest {

    private val uid = "user-pago-tardio"
    private val cuenta = "acc-bancolombia"
    private val arriendo = "rr-arriendo"
    private val internet = "rr-internet"

    /** Período de octubre con corte 25: 25-sep..24-oct. El 23-sep lleva 7 días vencido. */
    private val pasadaLaGracia: LocalDate = LocalDate.of(2026, 9, 30)

    /** El 23-sep lleva 4 días: el checklist todavía pregunta por él. */
    private val enLaGracia: LocalDate = LocalDate.of(2026, 9, 27)

    // Las cifras del escenario.
    private val b = 10_000_000L // lo que había al empezar: el sueldo de agosto, entero
    private val e = 5_000_000L // un bono que entró el 25-sep
    private val r = 1_000_000L // el arriendo de septiembre, pagado el 26
    private val v = 300_000L // el mercado del 27
    private val cuotaInternet = 100_000L // fijo del período, pagado el 26

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

    private fun regla(
        id: String,
        nombre: String,
        dia: Int,
        monto: Long = 1_000_000L,
        categoria: String = "Vivienda",
        conCuenta: Boolean = true,
    ) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[userId] = uid
            it[name] = nombre
            it[category] = categoria
            it[amount] = monto
            it[dayOfMonth] = dia
            it[type] = "EXPENSE"
            it[accountId] = if (conCuenta) cuenta else null
        }
    }

    private fun movimiento(
        id: String,
        descripcion: String,
        fecha: LocalDate,
        monto: Long,
        categoria: String = "Vivienda",
        tipo: TransactionType = TransactionType.EXPENSE,
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = uid
            it[accountId] = cuenta
            it[type] = tipo.name
            it[amount] = monto
            it[category] = categoria
            it[description] = descripcion
            it[timestamp] = appDateToEpochMillis(fecha) + 15 * 3_600_000L
            // Fuera de «Por confirmar»: solo así suma en «Gastos» e «Ingresos».
            it[reconciliationStatus] = "RECONCILED"
        }
    }

    /**
     * B, E, el Internet (fijo del período, pagado), R y V. El arriendo tiene su regla; el resto de las
     * reglas las pone cada prueba.
     */
    private fun escenario() {
        regla(arriendo, "Arriendo", dia = 23)
        regla(internet, "Internet", dia = 26, monto = cuotaInternet, categoria = "Servicios")
        movimiento("ev-sueldo", "Salario agosto", LocalDate.of(2026, 9, 1), b, "Salario", TransactionType.INCOME)
        movimiento("ev-bono", "Bono", LocalDate.of(2026, 9, 25), e, "Otros ingresos", TransactionType.INCOME)
        movimiento("ev-internet", "Internet", LocalDate.of(2026, 9, 26), cuotaInternet, "Servicios")
        movimiento("ev-0926", "Arriendo", LocalDate.of(2026, 9, 26), r)
        movimiento("ev-mercado", "Mercado", LocalDate.of(2026, 9, 27), v, "Mercado")
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

    private fun emparejadas(hoy: LocalDate): List<RecurringOccurrence> =
        transaction { emparejadasComoSellos(uid, hoy, ajustesDelPeriodoSinSuspender(uid)) }

    /**
     * El checklist de gastos del período, regla → vencimiento: las filas que el cliente resta como
     * fijos. Sale de [vencimientoEnElChecklist], que replica `checklistDelPeriodo` del cliente sobre
     * `/upcoming` y `/occurrences`; cada prueba además lo compara con el que se espera a mano.
     */
    private fun checklistDeGastos(hoy: LocalDate): Map<String, LocalDate> = transaction {
        val periodo = ajustesDelPeriodoSinSuspender(uid)
        val ocurridos = unirOcurridos(loadOccurredBy(uid), emparejadasComoSellos(uid, hoy, periodo).periodosPorRegla())
        RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map { it.toRule() }
            .filter { it.type == TransactionType.EXPENSE }
            .mapNotNull { regla -> vencimientoEnElChecklist(regla, hoy, periodo, ocurridos[regla.id].orEmpty())?.let { regla.id to it } }
            .toMap()
    }

    /** Lo que queda según el server y el checklist: B + E − guardado − otros pagos de deuda − F − variable. */
    private fun loQueQueda(hoy: LocalDate, fijos: Long): Long = transaction {
        val periodo = ajustesDelPeriodoSinSuspender(uid)
        val dias = diasDelPeriodo(hoy, periodo)
        val d = disponibleDelServidor(
            uid = uid,
            hoy = hoy,
            periodo = periodo,
            monthStart = appDateToEpochMillis(dias.start),
            monthEnd = appDateToEpochMillis(dias.endInclusive.plusDays(1)),
            voidedIds = emptySet(),
            reglasDeCredito = emptyList(),
        )
        d.plata.saldoAlInicio + d.plata.entradas - d.plata.guardado - d.pagosDeDeudaFueraDelChecklist -
            fijos - d.gastoVariablePorDia.values.sum()
    }

    /** Los fijos del período, sumados a mano: cada ítem de [esperado] por el monto de su regla. */
    private fun fijos(hoy: LocalDate, esperado: Map<String, Pair<LocalDate, Long>>): Long {
        assertEquals(esperado.mapValues { it.value.first }, checklistDeGastos(hoy), "El checklist de gastos del período")
        return esperado.values.sumOf { it.second }
    }

    /** El checklist de siempre del escenario: el arriendo de OCTUBRE (pendiente) y el Internet (pagado). */
    private val checklistDelEscenario = mapOf(
        arriendo to (LocalDate.of(2026, 10, 23) to 1_000_000L),
        internet to (LocalDate.of(2026, 9, 26) to cuotaInternet),
    )

    // ── El pago tardío cuenta una vez ────────────────────────────────────────

    /**
     * Pasada la gracia: el pago del 26 se empareja con el 23-sep (lo reserva), pero sigue siendo
     * gasto variable, porque el 23-sep no está en los fijos de este período.
     */
    @Test
    fun `pasada la gracia el pago tardio cuenta una vez`() {
        escenario()

        assertEquals(
            listOf(RecurringOccurrence(arriendo, "2026-09", "ev-0926", confirmedAt = appDateToEpochMillis(LocalDate.of(2026, 9, 26)) + 15 * 3_600_000L)),
            emparejadas(pasadaLaGracia).filter { it.ruleId == arriendo },
        )
        val f = fijos(pasadaLaGracia, checklistDelEscenario)
        assertEquals(b + e - f - v - r, loQueQueda(pasadaLaGracia, f))
    }

    /** En la gracia el checklist todavía empareja el 23-sep —y así rueda al 23-oct—, y el pago igual cuenta una vez. */
    @Test
    fun `en la gracia el pago tardio cuenta una vez`() {
        escenario()

        val estado = transaction { estadosDeLasOcurrenciasReales(uid, enLaGracia, ajustesDelPeriodoSinSuspender(uid)) }
            .single { it.ruleId == arriendo }
        assertTrue(estado.occurred && estado.automatica && estado.period == "2026-09", "En la gracia el checklist lo empareja")
        val f = fijos(enLaGracia, checklistDelEscenario)
        assertEquals(b + e - f - v - r, loQueQueda(enLaGracia, f))
    }

    /** El control: sellado a mano da la misma cuenta, en la gracia y después. */
    @Test
    fun `sellado a mano cuenta una vez igual`() {
        escenario()
        sellar(arriendo, "2026-09", "ev-0926")

        for (hoy in listOf(enLaGracia, pasadaLaGracia)) {
            val f = fijos(hoy, checklistDelEscenario)
            assertEquals(b + e - f - v - r, loQueQueda(hoy, f), "hoy $hoy")
        }
        assertEquals(emptyList(), emparejadas(pasadaLaGracia).filter { it.ruleId == arriendo }, "Lo sellado no se vuelve a derivar")
    }

    /** Con el período real del dueño —octubre arrancando el 24-sep por excepción— la cuenta es la misma. */
    @Test
    fun `con el periodo real del duenho cuenta una vez`() {
        transaction { Users.update({ Users.id eq uid }) { it[periodStarts] = """{"2026-10":"2026-09-24"}""" } }
        escenario()

        for (hoy in listOf(enLaGracia, pasadaLaGracia)) {
            val f = fijos(hoy, checklistDelEscenario)
            assertEquals(b + e - f - v - r, loQueQueda(hoy, f), "hoy $hoy")
        }
    }

    // ── Las guardas del emparejamiento de la ocurrencia anterior ─────────────

    /**
     * Dos pagos concluyentes para el 23-sep: no se empareja ninguno. Uno cayó el 20-sep, antes del
     * período, así que ya salió de B.
     */
    @Test
    fun `con dos concluyentes no se empareja y la cuenta no cambia`() {
        escenario()
        movimiento("ev-0920", "Arriendo", LocalDate.of(2026, 9, 20), 1_000_000L)

        assertEquals(emptyList(), emparejadas(pasadaLaGracia).filter { it.ruleId == arriendo })
        val f = fijos(pasadaLaGracia, checklistDelEscenario)
        assertEquals((b - 1_000_000L) + e - f - v - r, loQueQueda(pasadaLaGracia, f))
    }

    /** Un «no fue este» sobre el pago lo deja sin emparejar. */
    @Test
    fun `un rechazo se respeta`() {
        escenario()
        rechazar(arriendo, "ev-0926")

        assertEquals(emptyList(), emparejadas(pasadaLaGracia).filter { it.ruleId == arriendo })
        val f = fijos(pasadaLaGracia, checklistDelEscenario)
        assertEquals(b + e - f - v - r, loQueQueda(pasadaLaGracia, f))
    }

    /**
     * **Lo que el emparejamiento de la anterior sí hace: reservar.** La «Administración» (día 28,
     * $800.000, Vivienda, sin cuenta) está pendiente en el checklist y el arriendo del 26 es su
     * candidato por categoría. Sin la reserva, el Disponible le daría ese pago como el suyo y lo
     * sacaría del variable mientras su fijo de $800.000 sigue restado entero: $800.000 que no se
     * cuentan en ningún lado.
     */
    @Test
    fun `el pago tardio no pasa por el pago de otro item pendiente`() {
        escenario()
        regla("rr-administracion", "Administración", dia = 28, monto = 800_000L, conCuenta = false)

        val f = fijos(
            pasadaLaGracia,
            checklistDelEscenario + ("rr-administracion" to (LocalDate.of(2026, 9, 28) to 800_000L)),
        )
        assertEquals(b + e - f - v - r, loQueQueda(pasadaLaGracia, f))
    }

    /**
     * **Un movimiento, una ocurrencia: el checklist contra la anterior.** La «Administración» (día 28)
     * comparte categoría, cuenta y monto exacto con el arriendo, así que el pago del 26 sin nombre es
     * concluyente para el 28-sep —que el checklist pregunta hoy— y para el 23-sep del arriendo. Lo usa
     * el checklist; el arriendo no lo toma además. Acá el pago SÍ es un fijo del período (el de la
     * administración), así que no es R: lo que queda = B + E − F − V.
     */
    @Test
    fun `el mismo movimiento del checklist y de la anterior se usa una sola vez`() {
        regla(arriendo, "Arriendo", dia = 23)
        regla("rr-administracion", "Administración", dia = 28)
        movimiento("ev-sueldo", "Salario agosto", LocalDate.of(2026, 9, 1), b, "Salario", TransactionType.INCOME)
        movimiento("ev-0926", "Pago", LocalDate.of(2026, 9, 26), 1_000_000L)
        movimiento("ev-mercado", "Mercado", LocalDate.of(2026, 9, 27), v, "Mercado")

        val estado = transaction { estadosDeLasOcurrenciasReales(uid, pasadaLaGracia, ajustesDelPeriodoSinSuspender(uid)) }
            .single { it.ruleId == "rr-administracion" }
        assertTrue(estado.occurred && estado.eventId == "ev-0926", "El checklist lo empareja con la administración")
        assertEquals(listOf("rr-administracion"), emparejadas(pasadaLaGracia).filter { it.eventId == "ev-0926" }.map { it.ruleId })

        val f = fijos(
            pasadaLaGracia,
            mapOf(
                arriendo to (LocalDate.of(2026, 10, 23) to 1_000_000L),
                "rr-administracion" to (LocalDate.of(2026, 9, 28) to 1_000_000L),
            ),
        )
        assertEquals(b - f - v, loQueQueda(pasadaLaGracia, f))
    }

    /**
     * **Un movimiento, una ocurrencia: dos anteriores.** El «Parqueadero» (día 22) y el arriendo (día
     * 23) comparten categoría, cuenta y monto exacto; pasada la gracia de los dos, el pago del 26 sin
     * nombre es concluyente para el 22-sep y para el 23-sep. Se empareja con una sola.
     */
    @Test
    fun `dos ocurrencias anteriores no se reparten el mismo movimiento`() {
        regla(arriendo, "Arriendo", dia = 23)
        regla("rr-parqueadero", "Parqueadero", dia = 22)
        movimiento("ev-0926", "Pago", LocalDate.of(2026, 9, 26), 1_000_000L)

        val conEse = emparejadas(pasadaLaGracia).filter { it.eventId == "ev-0926" }
        assertEquals(1, conEse.size, "Un movimiento cierra una sola ocurrencia; llegó $conEse")
    }

    // ── «Próximos» no retrocede ──────────────────────────────────────────────

    /**
     * El emparejamiento del 23-sep no vuelve a mostrar vencido el arriendo ni le mueve el vencimiento
     * hacia atrás: sigue el 23-oct, igual que sin el pago (la gracia ya lo había rodado).
     */
    @Test
    fun `proximos no retrocede`() {
        regla(arriendo, "Arriendo", dia = 23)
        val sinPago = runBlocking { proximosPagos(uid, pasadaLaGracia) }.single { it.rule.id == arriendo }
        movimiento("ev-0926", "Arriendo", LocalDate.of(2026, 9, 26), r)
        val conPago = runBlocking { proximosPagos(uid, pasadaLaGracia) }.single { it.rule.id == arriendo }

        assertEquals("2026-10-23", sinPago.dueDate)
        assertEquals("2026-10-23", conPago.dueDate, "El emparejamiento anterior no mueve el vencimiento")
        assertEquals(PaymentStatus.UPCOMING, conPago.status)
    }
}
