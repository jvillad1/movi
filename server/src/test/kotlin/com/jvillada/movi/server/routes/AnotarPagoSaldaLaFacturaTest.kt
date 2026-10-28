package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AnomaliasDescartadas
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.reminders.diasDelPeriodo
import com.jvillada.movi.server.reminders.loadEventsBetween
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.AnotarElPagoDeLaOcurrencia
import com.jvillada.movi.shared.model.EstadoDeLaFila
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.PlataDelDisponible
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.checklistDelPeriodo
import com.jvillada.movi.shared.model.disponibleAlDia
import com.jvillada.movi.shared.model.gastoVariablePorDia
import com.jvillada.movi.shared.model.periodoDe
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.http.HttpStatusCode

/**
 * # «Anotar pago» salda la factura (vuelta 43)
 *
 * El día del dueño (28-oct, demo): «Pago vencido · hace 23 días» → «Anotar pago» → Guardar. El
 * movimiento quedó, pero la fila siguió en «Movi no encontró el movimiento de este pago» y «Te quedan»
 * bajó $1.800.000 **otra vez**: el pendiente seguía restado como fijo y el pago entró además como
 * gasto variable.
 *
 * Por qué: la hoja nunca supo de qué ocurrencia era el pago —se confiaba en que el emparejamiento
 * automático lo reconociera en la lectura siguiente— y el emparejador no lo podía reconocer: el pago
 * es de HOY, 23 días después del vencimiento, fuera de la ventana de 10 días (`OCCURRENCE_WINDOW_DAYS`)
 * en la que busca candidatos. Ni siquiera salía como «¿Es este?».
 *
 * Acá, de punta a punta del lado del server y con un «hoy» fijo: la fila del período (la misma
 * `checklistDelPeriodo` que pinta Pagos e Inicio) y «Te quedan» (la misma `disponibleDelPeriodo`), antes
 * y después de anotar el pago desde la fila.
 */
class AnotarPagoSaldaLaFacturaTest {

    private val uid = "user-anotar-pago"
    private val otro = "user-otro-pago"
    private val cuentaDelOtro = "acc-del-otro"
    private val nu = "acc-nu"
    private val bancolombia = "acc-bancolombia"
    private val arriendo = "rr-arriendo"
    private val montoDelArriendo = 1_800_000L

    /** 28-oct: el arriendo venció el 5, hace 23 días. Corte 1 (el de la demo). */
    private val hoy: LocalDate = LocalDate.of(2026, 10, 28)

    /** Lo que había al empezar el período: fijo, para que lo único que mueva «Te quedan» sea el pago. */
    private val plata = PlataDelDisponible(saldoAlInicio = 10_000_000L, entradas = 0L, guardado = 0L)

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:anotar_pago_salda_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, Events, VoidEvents, Credits, Cards, RecurringRules, RecurringOccurrences, OccurrenceRejections,
                Subscriptions, AnomaliasDescartadas,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            listOf(uid, otro).forEach { u ->
                Users.insert {
                    it[id] = u
                    it[email] = "$u@pago.test"
                    it[name] = u
                    it[passwordHash] = "hash"
                }
            }
            Accounts.insert { it[id] = cuentaDelOtro; it[userId] = otro; it[name] = "Ahorros del otro"; it[type] = "SAVINGS" }
            Accounts.insert { it[id] = nu; it[userId] = uid; it[name] = "Nu"; it[type] = "CREDIT_CARD" }
            Accounts.insert { it[id] = bancolombia; it[userId] = uid; it[name] = "Bancolombia"; it[type] = "SAVINGS" }
            // La regla de la demo: en «Otros» y sin cuenta.
            RecurringRules.insert {
                it[RecurringRules.id] = arriendo
                it[userId] = uid
                it[name] = "Arriendo"
                it[category] = "Otros"
                it[amount] = montoDelArriendo
                it[dayOfMonth] = 5
                it[type] = "EXPENSE"
                it[accountId] = null
            }
        }
    }

    // ── Lo que pinta la pantalla ─────────────────────────────────────────────

    private fun eventosDelPeriodo(): List<FinancialEvent> = transaction {
        val dias = diasDelPeriodo(hoy, ajustesDelPeriodoSinSuspender(uid))
        loadEventsBetween(uid, appDateToEpochMillis(dias.start), appDateToEpochMillis(dias.endInclusive.plusDays(1)))
    }

    /** La lista «Tus facturas del período», armada como la arma el cliente. */
    private fun checklist(): List<PagoDelPeriodo> {
        val ajustes = transaction { ajustesDelPeriodoSinSuspender(uid) }
        val upcoming = runBlocking { proximosPagos(uid, hoy) }
        val estados = transaction { estadosDeLasOcurrenciasReales(uid, hoy, ajustes) }
        val periodo = periodoDe(appDateToEpochMillis(hoy) + 12 * 3_600_000L, ajustes)
        return checklistDelPeriodo(upcoming, estados, periodo, ajustes)
    }

    private fun filaDelArriendo(): PagoDelPeriodo = checklist().single { it.ruleId == arriendo }

    /** «Te quedan» del Inicio: lo libre del período menos el gasto variable. */
    private fun teQuedan(): Long {
        val ajustes = transaction { ajustesDelPeriodoSinSuspender(uid) }
        val eventos = eventosDelPeriodo()
        val parte = transaction { parteFijaDelDisponible(uid, hoy, ajustes, eventos) }
        val variable = gastoVariablePorDia(eventos, parte) { epochMillisToAppDateString(it) }
        val dias = diasDelPeriodo(hoy, ajustes)
        // `disponibleAlDia`: la misma `disponibleDelPeriodo` que pinta Hoy, con las fechas en texto.
        val d = disponibleAlDia(
            ingresosRecibidos = 0L,
            pagos = checklist(),
            gastoVariablePorDia = variable,
            desde = dias.start.toString(),
            hasta = dias.endInclusive.toString(),
            hoy = hoy.toString(),
            plata = plata,
        )
        return assertNotNull(d).let { it.disponible - it.periodo.gastado }
    }

    /** El gasto que arma la hoja de Agregar en modo pago: el nombre y la categoría de la regla, hoy. */
    private fun elPago(id: String, monto: Long, cuenta: String = bancolombia) = FinancialEvent(
        id = id,
        accountId = cuenta,
        type = TransactionType.EXPENSE,
        amount = monto,
        category = "Otros",
        description = "Arriendo",
        source = EventSource.MANUAL,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        timestamp = appDateToEpochMillis(hoy) + 10 * 3_600_000L,
    )

    /**
     * **«Anotar pago» desde la fila**, por el mismo camino que la app: el movimiento y la ocurrencia
     * que paga (`POST /api/recurring-rules/{id}/occurrence/pago`). Antes de la vuelta 43 era un
     * `POST /api/events` suelto —el movimiento se guardaba y nadie decía a qué ocurrencia pagaba—, y
     * con eso estas pruebas fallaban: la fila seguía `SIN_MOVIMIENTO`.
     */
    private fun anotarDesdeLaFila(evento: FinancialEvent, fila: PagoDelPeriodo, quien: String = uid): PagoDeLaOcurrencia =
        runBlocking {
            anotarElPagoDeLaOcurrencia(
                quien, fila.ruleId, AnotarElPagoDeLaOcurrencia(periodo = fila.periodoDelSello, movimiento = evento),
                mandoLaMoneda = false, hoy = hoy,
            )
        }

    // ── Las pruebas ──────────────────────────────────────────────────────────

    @Test
    fun `antes de anotar, la fila esta vencida y sin movimiento`() {
        val fila = filaDelArriendo()
        assertEquals(EstadoDeLaFila.SIN_MOVIMIENTO, fila.estado)
        assertEquals(-23, fila.diasParaVencer)
    }

    @Test
    fun `anotar el pago con el mismo monto deja la fila pagada y Te quedan igual`() {
        val antes = teQuedan()
        anotarDesdeLaFila(elPago("ev-arriendo", montoDelArriendo), filaDelArriendo())

        val fila = filaDelArriendo()
        assertEquals(EstadoDeLaFila.LISTO, fila.estado, "Pagado ✓")
        assertEquals("ev-arriendo", fila.eventId, "con su enlace al movimiento")
        assertEquals(antes, teQuedan(), "el pendiente se reemplaza por el gasto real: no baja dos veces")
    }

    @Test
    fun `pagar de mas solo baja Te quedan por la diferencia`() {
        val antes = teQuedan()
        anotarDesdeLaFila(elPago("ev-arriendo", 2_000_000L), filaDelArriendo())

        assertEquals(EstadoDeLaFila.LISTO, filaDelArriendo().estado)
        assertEquals(antes - 200_000L, teQuedan())
    }

    @Test
    fun `pagar de menos sube Te quedan por la diferencia`() {
        val antes = teQuedan()
        anotarDesdeLaFila(elPago("ev-arriendo", 1_500_000L), filaDelArriendo())

        assertEquals(EstadoDeLaFila.LISTO, filaDelArriendo().estado)
        assertEquals(antes + 300_000L, teQuedan())
    }

    // ── La operación: idempotencia, usuario ajeno, ocurrencia ya saldada ─────

    private fun movimientosDelDueno(): List<FinancialEvent> = transaction { loadEventsBetween(uid, 0L, Long.MAX_VALUE) }

    @Test
    fun `el reintento con el mismo movimiento no anota dos veces`() {
        val fila = filaDelArriendo()
        val primero = anotarDesdeLaFila(elPago("ev-arriendo", montoDelArriendo), fila)
        val segundo = anotarDesdeLaFila(elPago("ev-arriendo", montoDelArriendo), fila)

        assertTrue(primero is PagoDeLaOcurrencia.Hecho && primero.nuevo)
        assertTrue(segundo is PagoDeLaOcurrencia.Hecho && !segundo.nuevo, "el reintento contesta lo que quedó")
        assertEquals(1, movimientosDelDueno().count { it.id == "ev-arriendo" })
        assertEquals("ev-arriendo", filaDelArriendo().eventId)
    }

    @Test
    fun `una ocurrencia ya saldada no acepta otro pago, y no escribe nada`() {
        val fila = filaDelArriendo()
        anotarDesdeLaFila(elPago("ev-arriendo", montoDelArriendo), fila)
        val otroPago = anotarDesdeLaFila(elPago("ev-arriendo-2", montoDelArriendo), fila)

        assertTrue(otroPago is PagoDeLaOcurrencia.Rechazado)
        assertEquals(HttpStatusCode.Conflict, otroPago.codigo)
        assertTrue(movimientosDelDueno().none { it.id == "ev-arriendo-2" }, "ni el gasto suelto queda")
        assertEquals("ev-arriendo", filaDelArriendo().eventId)
    }

    @Test
    fun `otro usuario no puede pagar la factura del dueno`() {
        val fila = filaDelArriendo()
        val ajeno = anotarDesdeLaFila(elPago("ev-ajeno", montoDelArriendo, cuenta = cuentaDelOtro), fila, quien = otro)

        assertTrue(ajeno is PagoDeLaOcurrencia.Rechazado)
        assertEquals(HttpStatusCode.NotFound, ajeno.codigo)
        assertTrue(transaction { loadEventsBetween(otro, 0L, Long.MAX_VALUE) }.isEmpty(), "nada escrito a su nombre")
        assertEquals(EstadoDeLaFila.SIN_MOVIMIENTO, filaDelArriendo().estado)
    }

    @Test
    fun `con la cuenta de otro usuario no se anota`() {
        val resultado = anotarDesdeLaFila(elPago("ev-arriendo", montoDelArriendo, cuenta = cuentaDelOtro), filaDelArriendo())

        assertTrue(resultado is PagoDeLaOcurrencia.Rechazado)
        assertEquals(HttpStatusCode.NotFound, resultado.codigo)
        assertTrue(movimientosDelDueno().isEmpty())
    }

    @Test
    fun `la cuota de un credito no se anota como gasto suelto`() {
        val resultado = runBlocking {
            anotarElPagoDeLaOcurrencia(
                uid, "credit_acc-carro", AnotarElPagoDeLaOcurrencia(movimiento = elPago("ev-cuota", 500_000L)),
                mandoLaMoneda = false, hoy = hoy,
            )
        }
        assertTrue(resultado is PagoDeLaOcurrencia.Rechazado)
        assertEquals(HttpStatusCode.BadRequest, resultado.codigo)
        assertTrue(movimientosDelDueno().isEmpty())
    }

    /**
     * Una factura que todavía no vence no trae período (`periodoDelSello` en `null`): el server sabe
     * cuál es la ocurrencia en juego y la sella con el pago de hoy, que la prueba.
     */
    @Test
    fun `pagar antes del vencimiento tambien salda la fila`() {
        transaction {
            RecurringRules.insert {
                it[RecurringRules.id] = "rr-internet"
                it[userId] = uid
                it[name] = "Internet"
                it[category] = "Servicios"
                it[amount] = 120_000L
                it[dayOfMonth] = 30
                it[type] = "EXPENSE"
                it[accountId] = bancolombia
            }
        }
        val fila = checklist().single { it.ruleId == "rr-internet" }
        assertEquals(EstadoDeLaFila.AUN_NO_VENCE, fila.estado)
        assertNull(fila.periodoDelSello)
        val antes = teQuedan()

        val pago = elPago("ev-internet", 120_000L).copy(category = "Servicios", description = "Internet")
        assertTrue(anotarDesdeLaFila(pago, fila) is PagoDeLaOcurrencia.Hecho)

        val despues = checklist().single { it.ruleId == "rr-internet" }
        assertEquals(EstadoDeLaFila.LISTO, despues.estado)
        assertEquals("ev-internet", despues.eventId)
        assertEquals(antes, teQuedan())
    }

    // ── «Movi propone» no lo cuenta como gasto nuevo ─────────────────────────

    /**
     * El día del dueño: después de anotar el arriendo, «Movi propone hoy» sacó «Gasto nuevo en Otros
     * $1.800.000». El arriendo es un fijo del período, ya contado como fijo: lo ligado a su ocurrencia
     * no es consumo nuevo. El control: el mismo monto en «Otros», suelto, sí avisa.
     */
    @Test
    fun `el pago ligado a la factura no dispara Gasto nuevo`() {
        // Historia: Movi ya cubría septiembre entero (sin nada en «Otros»).
        transaction {
            com.jvillada.movi.server.db.insertEventRow(
                uid,
                elPago("ev-cafe", 12_000L).copy(category = "Comida", description = "Café", timestamp = appDateToEpochMillis(LocalDate.of(2026, 8, 20))),
            )
        }
        anotarDesdeLaFila(elPago("ev-arriendo", montoDelArriendo), filaDelArriendo())
        val ajustes = transaction { ajustesDelPeriodoSinSuspender(uid) }
        val conElPago = transaction { loQueSeSaleDeLoNormalDe(uid, hoy, ajustes) }
        assertTrue(conElPago.none { it.titulo.startsWith("Gasto nuevo") }, conElPago.joinToString { it.titulo })
        assertTrue(conElPago.none { it.titulo.startsWith("Primera vez en Arriendo") }, conElPago.joinToString { it.titulo })

        // El control: un gasto suelto igual de grande en «Otros» sí es noticia.
        transaction { com.jvillada.movi.server.db.insertEventRow(uid, elPago("ev-suelto", montoDelArriendo).copy(description = "Muebles")) }
        val conElSuelto = transaction { loQueSeSaleDeLoNormalDe(uid, hoy, ajustes) }
        assertTrue(conElSuelto.any { it.titulo == "Gasto nuevo en Otros" }, conElSuelto.joinToString { it.titulo })
    }
}
