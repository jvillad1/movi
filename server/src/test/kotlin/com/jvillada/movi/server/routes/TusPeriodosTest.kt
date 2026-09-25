package com.jvillada.movi.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.plugins.configureRouting
import com.jvillada.movi.server.plugins.configureSerialization
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.PAGO_FIJO_CON_DUDAS
import com.jvillada.movi.shared.model.PAGO_FIJO_LISTO
import com.jvillada.movi.shared.model.PAGO_FIJO_PENDIENTE
import com.jvillada.movi.shared.model.PagoFijoDelPeriodo
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.periodoDe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.server.application.Application
import com.jvillada.movi.shared.model.RecurringRule
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
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
 * **«Tus períodos»: el server cuenta cualquier período** con las reglas de siempre.
 *
 * Todo con el período real del dueño: corte 25 y octubre arrancando por excepción el 24-sep, así
 * que septiembre va del 25-ago al 23-sep y octubre del 24-sep al 24-oct. Las funciones se llaman
 * con un «ahora» fijo (`AppClock` no se mueve desde una prueba); la ruta HTTP se prueba aparte para
 * la autenticación y el 404.
 */
class TusPeriodosTest {

    private val uid = "user-tus-periodos"
    private val otro = "user-otro-periodos"
    private val ahorros = "acc-ahorros"
    private val tarjeta = "acc-tarjeta"
    private val pension = "acc-pension"
    private val dolares = "acc-dolares"
    private val cuentaDelOtro = "acc-del-otro"

    private val delDueno = PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-10" to "2026-09-24"))

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:tus_periodos_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            val tablas = arrayOf(
                Users, Accounts, Events, VoidEvents, RecurringRules, RecurringOccurrences,
                OccurrenceRejections, Budgets,
            )
            SchemaUtils.create(tables = tablas)
            SchemaUtils.drop(tables = tablas.reversedArray())
            SchemaUtils.create(tables = tablas)
            listOf(uid to "duenio@periodos.test", otro to "otro@periodos.test").forEach { (id, correo) ->
                Users.insert {
                    it[Users.id] = id
                    it[email] = correo
                    it[name] = id
                    it[passwordHash] = "hash"
                    it[periodCutoffDay] = 25
                    it[periodStarts] = """{"2026-10":"2026-09-24"}"""
                }
            }
            cuenta(ahorros, "SAVINGS")
            cuenta(tarjeta, "CREDIT_CARD")
            cuenta(pension, "SAVINGS", condicionadaA = "Pensión")
            cuenta(dolares, "SAVINGS", moneda = "USD")
            cuenta(cuentaDelOtro, "SAVINGS", usuario = otro)
        }
    }

    // ── Armado ────────────────────────────────────────────────────────────────

    private fun cuenta(id: String, tipo: String, condicionadaA: String? = null, moneda: String = "COP", usuario: String = uid) {
        Accounts.insert {
            it[Accounts.id] = id
            it[userId] = usuario
            it[name] = id
            it[type] = tipo
            it[currency] = moneda
            it[conditionedTo] = condicionadaA
        }
    }

    /** Mediodía de Bogotá: un borde de zona no puede correr el día. */
    private fun instante(fecha: LocalDate): Long = appDateToEpochMillis(fecha) + 12 * 3_600_000L

    private fun dia(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    private fun movimiento(
        id: String,
        fecha: LocalDate,
        monto: Long,
        tipo: String = "EXPENSE",
        categoria: String = "Comida",
        descripcion: String = "",
        cuenta: String = ahorros,
        moneda: String = "COP",
        // Confirmado: el default de la columna es «Por confirmar», que no suma en «Gastos».
        estado: String = "RECONCILED",
        traspaso: String? = null,
        usuario: String = uid,
    ) = transaction {
        Events.insert {
            it[Events.id] = id
            it[userId] = usuario
            it[accountId] = cuenta
            it[type] = tipo
            it[amount] = monto
            it[currency] = moneda
            it[category] = categoria
            it[description] = descripcion
            it[timestamp] = instante(fecha)
            it[reconciliationStatus] = estado
            it[transferId] = traspaso
        }
    }

    private fun anular(id: String) = transaction {
        VoidEvents.insert {
            it[VoidEvents.id] = "void-$id"
            it[userId] = uid
            it[originalEventId] = id
            it[VoidEvents.timestamp] = 0L
        }
    }

    private fun regla(
        id: String,
        nombre: String,
        dia: Int,
        monto: Long,
        categoria: String,
        tipo: String = "EXPENSE",
        cuenta: String? = ahorros,
        desde: String? = null,
        creada: LocalDate? = null,
    ) = transaction {
        RecurringRules.insert {
            it[RecurringRules.id] = id
            it[userId] = uid
            it[name] = nombre
            it[category] = categoria
            it[amount] = monto
            it[dayOfMonth] = dia
            it[type] = tipo
            it[accountId] = cuenta
            it[activeFrom] = desde
            it[createdAt] = creada?.let { instante(it) }
        }
    }

    private fun sello(regla: String, periodo: String, evento: String?) = transaction {
        RecurringOccurrences.insert {
            it[userId] = uid
            it[ruleId] = regla
            it[period] = periodo
            it[eventId] = evento
            it[confirmedAt] = 1L
        }
    }

    private fun lista(ahora: LocalDate): List<ResumenDePeriodo> =
        transaction { resumenesDePeriodos(uid, instante(ahora), delDueno) }

    private fun detalle(id: String, ahora: LocalDate): DetalleDePeriodo? =
        transaction { detalleDePeriodo(uid, id, instante(ahora), delDueno) }

    /** El pago de [ruleId]; con [vencimiento] cuando la regla vence dos veces en el período. */
    private fun List<PagoFijoDelPeriodo>.de(ruleId: String, vencimiento: String? = null) =
        single { it.ruleId == ruleId && (vencimiento == null || it.vencimiento == vencimiento) }

    // ── La lista ─────────────────────────────────────────────────────────────

    /**
     * Del en curso al más viejo, con las ventanas del dueño: el 23-sep es de septiembre y el 24-sep
     * ya es de octubre, que arrancó por excepción. Agosto sin movimientos sale igual, en cero: el
     * hueco también es un período.
     */
    @Test
    fun `la lista va del en curso al mas viejo con el corte 25 y la excepcion de octubre`() {
        movimiento("ev-julio", dia(2026, 7, 10), 10_000)
        movimiento("ev-23-sep", dia(2026, 9, 23), 40_000)
        movimiento("ev-24-sep", dia(2026, 9, 24), 70_000)

        val periodos = lista(ahora = dia(2026, 10, 5))

        assertEquals(listOf("2026-10", "2026-09", "2026-08", "2026-07"), periodos.map { it.id })
        val octubre = periodos[0]
        assertEquals("Octubre 2026", octubre.nombre)
        assertEquals("2026-09-24", octubre.desde)
        assertEquals("2026-10-24", octubre.hasta, "El último día incluido, no el arranque de noviembre")
        assertTrue(octubre.inicioPropio, "Octubre arrancó el 24, no el 25")
        assertTrue(octubre.enCurso)
        assertEquals(70_000, octubre.salidas)

        val septiembre = periodos[1]
        assertEquals("Septiembre 2026", septiembre.nombre)
        assertEquals("2026-08-25", septiembre.desde)
        assertEquals("2026-09-23", septiembre.hasta, "Septiembre termina el 23: octubre se llevó el 24")
        assertEquals(false, septiembre.inicioPropio, "Que el siguiente arranque antes no hace propio a este")
        assertEquals(false, septiembre.enCurso)
        assertEquals(40_000, septiembre.salidas)

        assertEquals(0, periodos[2].salidas)
        assertEquals(0, periodos[2].movimientos)
        assertEquals(10_000, periodos[3].salidas)
    }

    /**
     * Las entradas y salidas son las de «Gastos» e «Ingresos»: ni anulados, ni traspasos, ni pagos
     * de tarjeta, ni «Por confirmar», ni otra moneda — y nada de otro usuario. Una compra con la
     * tarjeta sí es gasto (el pago de la tarjeta es el que no).
     */
    @Test
    fun `las cifras de un periodo son las de gastos e ingresos`() {
        val d = dia(2026, 9, 10)
        movimiento("ev-mercado", d, 100_000)
        movimiento("ev-compra-tarjeta", d, 30_000, cuenta = tarjeta)
        movimiento("ev-futbol", d, 60_000, categoria = "Fútbol")
        movimiento("ev-salario", d, 2_000_000, tipo = "INCOME", categoria = "Salario")
        movimiento("ev-anulado", d, 50_000)
        anular("ev-anulado")
        movimiento("ev-traspaso-sale", d, 300_000, categoria = TRANSFER_CATEGORY, traspaso = "tr-1")
        movimiento("ev-traspaso-entra", d, 300_000, tipo = "INCOME", categoria = TRANSFER_CATEGORY, cuenta = pension, traspaso = "tr-1")
        movimiento("ev-pago-tarjeta", d, 400_000, categoria = CARD_PAYMENT_CATEGORY)
        movimiento("ev-por-confirmar", d, 70_000, estado = "UNCONFIRMED")
        movimiento("ev-en-dolares", d, 20, cuenta = dolares, moneda = "USD")
        movimiento("ev-del-otro", d, 999_000, cuenta = cuentaDelOtro, usuario = otro)

        val septiembre = lista(ahora = dia(2026, 10, 5)).single { it.id == "2026-09" }
        assertEquals(2_000_000, septiembre.entradas)
        assertEquals(190_000, septiembre.salidas)
        assertEquals(4, septiembre.movimientos)

        val detalle = assertNotNull(detalle("2026-09", ahora = dia(2026, 10, 5)))
        assertEquals(septiembre, detalle.resumen, "El resumen del detalle es la fila de la lista")
        assertEquals(
            listOf("Comida" to 130_000L, "Fútbol" to 60_000L),
            detalle.porCategoria.map { it.category to it.monto },
            "De mayor a menor",
        )
    }

    /** Sin movimientos, solo el en curso; lo del otro usuario no alarga mi lista. */
    @Test
    fun `sin movimientos solo esta el en curso`() {
        movimiento("ev-del-otro", dia(2025, 1, 10), 5_000, cuenta = cuentaDelOtro, usuario = otro)

        val periodos = lista(ahora = dia(2026, 10, 5))

        assertEquals(listOf("2026-10"), periodos.map { it.id })
        assertTrue(periodos.single().enCurso)
        assertEquals(0, periodos.single().salidas)
    }

    /** El movimiento más viejo que cuenta es uno vivo: uno anulado no estira la lista. */
    @Test
    fun `un movimiento anulado no estira la lista`() {
        movimiento("ev-viejo-anulado", dia(2026, 1, 10), 5_000)
        anular("ev-viejo-anulado")
        movimiento("ev-agosto", dia(2026, 8, 10), 5_000)

        assertEquals(listOf("2026-10", "2026-09", "2026-08"), lista(ahora = dia(2026, 10, 5)).map { it.id })
    }

    /** Tres años como mucho: con un movimiento de 2020, la lista para en noviembre de 2023. */
    @Test
    fun `la lista tiene tope`() {
        movimiento("ev-2020", dia(2020, 1, 10), 5_000)

        val periodos = lista(ahora = dia(2026, 10, 5))

        assertEquals(MAX_PERIODOS, periodos.size)
        assertEquals("2026-10", periodos.first().id)
        assertEquals("2023-11", periodos.last().id)
    }

    // ── El detalle ───────────────────────────────────────────────────────────

    /** Mal escrito, en el futuro o más viejo que el primer movimiento: no hay detalle (404). */
    @Test
    fun `un id que no esta en la lista no tiene detalle`() {
        movimiento("ev-julio", dia(2026, 7, 10), 10_000)
        val ahora = dia(2026, 10, 5)

        listOf("2026-13", "2026-9", "abc", "2026-11", "2026-06").forEach { id ->
            assertNull(detalle(id, ahora), "«$id» no es un período de la lista")
        }
        assertNotNull(detalle("2026-07", ahora))
        assertNotNull(detalle("2026-10", ahora))
    }

    /**
     * Los cinco gastos de flujo más grandes, del más grande al más chico, y los presupuestos de hoy
     * con lo gastado en la ventana (lo que no se gastó dice cero, no desaparece).
     */
    @Test
    fun `los mas grandes y los presupuestos del periodo`() {
        val d = dia(2026, 9, 10)
        listOf(10_000L, 90_000L, 50_000L, 70_000L, 30_000L, 60_000L).forEachIndexed { i, monto ->
            movimiento("ev-gasto-$i", d, monto)
        }
        movimiento("ev-pago-tarjeta", d, 900_000, categoria = CARD_PAYMENT_CATEGORY)
        movimiento("ev-por-confirmar", d, 800_000, estado = "UNCONFIRMED")
        movimiento("ev-anulado", d, 700_000)
        anular("ev-anulado")
        movimiento("ev-ingreso", d, 5_000_000, tipo = "INCOME", categoria = "Salario")
        transaction {
            listOf("Comida" to 500_000L, "Viaje" to 100_000L).forEach { (cat, limite) ->
                Budgets.insert {
                    it[userId] = uid
                    it[category] = cat
                    it[monthlyLimit] = limite
                }
            }
        }

        val detalle = assertNotNull(detalle("2026-09", ahora = dia(2026, 10, 5)))

        assertEquals(listOf(90_000L, 70_000L, 60_000L, 50_000L, 30_000L), detalle.masGrandes.map { it.amount })
        assertEquals(
            listOf(Triple("Comida", 500_000L, 310_000L), Triple("Viaje", 100_000L, 0L)),
            detalle.presupuestos.map { Triple(it.category, it.limite, it.gastado) },
        )
    }

    /**
     * «Tu plata» al empezar y al cerrar: las cuentas que suma el Inicio (ni la tarjeta ni la pensión
     * condicionada), sin anulados, con lo que espera en «Por confirmar» (el saldo lo incluye), y lo
     * del período siguiente afuera. Una cuenta de Tu plata con movimientos en otra moneda vuelve la
     * cifra `null` desde ese movimiento en adelante — antes, el saldo en pesos sigue siendo honesto.
     */
    @Test
    fun `tu plata al empezar y al cerrar`() {
        movimiento("ev-apertura", dia(2026, 8, 1), 1_000_000, tipo = "INCOME", categoria = OPENING_CATEGORY)
        movimiento("ev-antes", dia(2026, 8, 20), 100_000)
        movimiento("ev-durante-gasto", dia(2026, 9, 10), 200_000)
        movimiento("ev-durante-ingreso", dia(2026, 9, 15), 500_000, tipo = "INCOME", categoria = "Salario")
        movimiento("ev-por-confirmar", dia(2026, 9, 12), 10_000, estado = "UNCONFIRMED")
        movimiento("ev-anulado", dia(2026, 9, 11), 1_000)
        anular("ev-anulado")
        movimiento("ev-tarjeta", dia(2026, 9, 5), 30_000, cuenta = tarjeta)
        movimiento("ev-pension", dia(2026, 9, 5), 300_000, tipo = "INCOME", cuenta = pension)
        movimiento("ev-del-otro", dia(2026, 8, 1), 5_000_000, tipo = "INCOME", cuenta = cuentaDelOtro, usuario = otro)
        movimiento("ev-despues", dia(2026, 10, 1), 50_000)
        // Una compra en dólares con la tarjeta débito de la misma cuenta.
        movimiento("ev-dolares", dia(2026, 10, 2), 12, cuenta = ahorros, moneda = "USD")

        val septiembre = assertNotNull(detalle("2026-09", ahora = dia(2026, 10, 5)))
        assertEquals(900_000, septiembre.tuPlataAlEmpezar)
        assertEquals(1_190_000, septiembre.tuPlataAlCerrar)

        val octubre = assertNotNull(detalle("2026-10", ahora = dia(2026, 10, 5)))
        assertEquals(1_190_000, octubre.tuPlataAlEmpezar, "Octubre empieza con lo que septiembre cerró")
        assertNull(octubre.tuPlataAlCerrar, "Con dólares en Tu plata, una suma en pesos mentiría")
    }

    // ── Los pagos fijos ──────────────────────────────────────────────────────

    /**
     * Un período pasado: LISTO por sello (con lo que de verdad se pagó), LISTO porque el nombre pega
     * aunque el movimiento diga el mes, CON DUDAS con dos pagos iguales, PENDIENTE sin nada. Una
     * regla que arrancó después no le debe nada a septiembre.
     */
    @Test
    fun `los pagos fijos de un periodo pasado`() {
        regla("rr-arriendo", "Arriendo", dia = 5, monto = 1_800_000, categoria = "Vivienda")
        movimiento("ev-arriendo", dia(2026, 9, 5), 1_750_000, categoria = "Vivienda", descripcion = "Pago casa")
        sello("rr-arriendo", "2026-09", "ev-arriendo")

        regla("rr-salario", "Salario", dia = 15, monto = 20_000_000, categoria = "Salario", tipo = "INCOME")
        movimiento("ev-salario", dia(2026, 9, 14), 20_308_659, tipo = "INCOME", categoria = "Salario", descripcion = "Salario Septiembre 2026")

        regla("rr-gimnasio", "Gimnasio", dia = 10, monto = 180_000, categoria = "Deporte")
        movimiento("ev-gym-1", dia(2026, 9, 9), 180_000, categoria = "Deporte", descripcion = "Gimnasio")
        movimiento("ev-gym-2", dia(2026, 9, 11), 180_000, categoria = "Deporte", descripcion = "Gimnasio")

        regla("rr-celular", "Celular", dia = 20, monto = 53_077, categoria = "Celular", creada = dia(2026, 8, 1))
        regla("rr-nuevo", "Seguro nuevo", dia = 1, monto = 90_000, categoria = "Seguros", desde = "2026-10-01")

        val pagos = assertNotNull(detalle("2026-09", ahora = dia(2026, 10, 5))).pagosFijos

        assertEquals(listOf("rr-arriendo", "rr-gimnasio", "rr-salario", "rr-celular"), pagos.map { it.ruleId }, "Por vencimiento")
        pagos.de("rr-arriendo").let {
            assertEquals(PAGO_FIJO_LISTO, it.estado)
            assertEquals("2026-09-05", it.vencimiento)
            assertEquals("ev-arriendo", it.eventId)
            assertEquals(1_750_000, it.montoReal)
            assertEquals(1_800_000, it.monto)
        }
        pagos.de("rr-salario").let {
            assertEquals(PAGO_FIJO_LISTO, it.estado, "«Salario Septiembre 2026» pega con «Salario»")
            assertEquals("ev-salario", it.eventId)
            assertEquals(20_308_659, it.montoReal)
            assertTrue(it.esIngreso)
        }
        assertEquals(PAGO_FIJO_CON_DUDAS, pagos.de("rr-gimnasio").estado)
        assertNull(pagos.de("rr-gimnasio").eventId)
        assertEquals(PAGO_FIJO_PENDIENTE, pagos.de("rr-celular").estado)
    }

    /**
     * **En el período en curso los pagos fijos dicen lo mismo que el checklist**
     * (`/api/payments/occurrences`), incluida la reserva de movimientos entre reglas.
     *
     * El 26-sep octubre ya arrancó (el 24) y el arriendo del 23-sep sigue en gracia: el checklist
     * pregunta por ESE y lo empareja con el pago del 25. La administración vence el 25, en la misma
     * categoría y cuenta, por el mismo monto exacto: si «Tus períodos» emparejara octubre por su
     * cuenta, sin la reserva del checklist, le daría a la administración el pago del arriendo.
     */
    @Test
    fun `en el periodo en curso los pagos fijos coinciden con el checklist`() {
        val hoy = dia(2026, 9, 26)
        regla("rr-a-arriendo", "Arriendo", dia = 23, monto = 1_800_000, categoria = "Vivienda")
        movimiento("ev-arriendo", dia(2026, 9, 25), 1_800_000, categoria = "Vivienda", descripcion = "Arriendo")
        regla("rr-b-administracion", "Administración", dia = 25, monto = 1_800_000, categoria = "Vivienda")
        regla("rr-c-celular", "Celular", dia = 24, monto = 53_077, categoria = "Celular")
        movimiento("ev-celular", dia(2026, 9, 24), 52_990, categoria = "Celular", descripcion = "Celular")
        regla("rr-d-gimnasio", "Gimnasio", dia = 26, monto = 180_000, categoria = "Deporte")
        movimiento("ev-gym-1", dia(2026, 9, 25), 180_000, categoria = "Deporte", descripcion = "Gimnasio")
        movimiento("ev-gym-2", dia(2026, 9, 26), 180_000, categoria = "Deporte", descripcion = "Gimnasio")
        regla("rr-e-seguro", "Seguro", dia = 24, monto = 90_000, categoria = "Seguros")
        sello("rr-e-seguro", "2026-09", evento = null)
        regla("rr-f-luz", "Luz", dia = 3, monto = 120_000, categoria = "Servicios")

        val checklist = transaction { estadosDeLasOcurrenciasReales(uid, hoy, delDueno) }
        val detalle = assertNotNull(detalle("2026-10", ahora = hoy))
        val pagos = detalle.pagosFijos
        val diasDeOctubre = LocalDate.parse(detalle.resumen.desde)..LocalDate.parse(detalle.resumen.hasta)

        val delPeriodo = checklist.filter { LocalDate.parse(it.dueDate) in diasDeOctubre }
        assertEquals(4, delPeriodo.size, "Administración, celular, gimnasio y seguro vencen en octubre y ya llegaron")
        delPeriodo.forEach { estado ->
            val pago = pagos.de(estado.ruleId, estado.dueDate)
            assertEquals(estado.dueDate, pago.vencimiento, estado.ruleId)
            assertEquals(estado.occurred, pago.estado == PAGO_FIJO_LISTO, estado.ruleId)
            if (estado.occurred) assertEquals(estado.eventId, pago.eventId, estado.ruleId)
        }

        assertEquals(PAGO_FIJO_PENDIENTE, pagos.de("rr-b-administracion").estado, "El pago del arriendo es del arriendo")
        assertEquals(PAGO_FIJO_LISTO, pagos.de("rr-c-celular", "2026-09-24").estado)
        assertEquals(52_990, pagos.de("rr-c-celular", "2026-09-24").montoReal)
        // La regla de día 24 vence dos veces en este octubre (24-sep y 24-oct): la segunda también
        // está, todavía por llegar.
        assertEquals(PAGO_FIJO_PENDIENTE, pagos.de("rr-c-celular", "2026-10-24").estado)
        assertEquals(PAGO_FIJO_CON_DUDAS, pagos.de("rr-d-gimnasio").estado)
        assertEquals(PAGO_FIJO_LISTO, pagos.de("rr-e-seguro", "2026-09-24").estado)
        assertNull(pagos.de("rr-e-seguro", "2026-09-24").eventId, "«Ya lo pagué» sin movimiento")
        assertEquals(PAGO_FIJO_PENDIENTE, pagos.de("rr-e-seguro", "2026-10-24").estado)
        // El arriendo de octubre (23-oct) y la luz (3-oct) todavía no llegan; el checklist no los
        // pregunta, y acá están pendientes.
        assertEquals("2026-10-23", pagos.de("rr-a-arriendo").vencimiento)
        assertEquals(PAGO_FIJO_PENDIENTE, pagos.de("rr-a-arriendo").estado)
        assertEquals(PAGO_FIJO_PENDIENTE, pagos.de("rr-f-luz").estado)
        // Y el arriendo del 23-sep, que el checklist dio por pagado, es de septiembre.
        assertTrue(checklist.single { it.ruleId == "rr-a-arriendo" }.let { it.occurred && it.eventId == "ev-arriendo" })
    }

    /**
     * **Cada ocurrencia cae en exactamente un período.** Con octubre del 24-sep al 24-oct, una regla
     * de día 24 vence dos veces adentro; cerrado octubre, las dos salen, cada una con su pago.
     */
    @Test
    fun `una regla que vence dos veces en el periodo sale dos veces`() {
        regla("rr-celular", "Celular", dia = 24, monto = 53_077, categoria = "Celular")
        movimiento("ev-agosto", dia(2026, 8, 10), 10_000)
        movimiento("ev-celular-sep", dia(2026, 9, 24), 52_990, categoria = "Celular", descripcion = "Celular")
        movimiento("ev-celular-oct", dia(2026, 10, 24), 53_500, categoria = "Celular", descripcion = "Celular")

        val pagos = assertNotNull(detalle("2026-10", ahora = dia(2026, 11, 5))).pagosFijos

        assertEquals(listOf("2026-09-24", "2026-10-24"), pagos.map { it.vencimiento })
        assertEquals("ev-celular-sep", pagos.de("rr-celular", "2026-09-24").eventId)
        assertEquals("ev-celular-oct", pagos.de("rr-celular", "2026-10-24").eventId)
        assertTrue(pagos.all { it.estado == PAGO_FIJO_LISTO })
        // Y ni septiembre (termina el 23) ni noviembre (empieza el 25-oct) las repiten.
        assertTrue(assertNotNull(detalle("2026-09", ahora = dia(2026, 11, 5))).pagosFijos.none { it.ruleId == "rr-celular" })
    }

    /**
     * **En un período cerrado, el pago tardío de la ocurrencia anterior es de ella.** El arriendo del
     * 23-sep pagado el 25-sep (ya en octubre): cerrado octubre, la administración del 25 —misma
     * categoría, cuenta y monto exacto— no se da por pagada con ese movimiento, que septiembre ya le
     * dio al arriendo.
     */
    @Test
    fun `en un periodo cerrado el pago tardio de la ocurrencia anterior no paga otra regla`() {
        val ahora = dia(2026, 11, 5)
        regla("rr-a-arriendo", "Arriendo", dia = 23, monto = 1_800_000, categoria = "Vivienda", creada = dia(2026, 8, 1))
        regla("rr-b-administracion", "Administración", dia = 25, monto = 1_800_000, categoria = "Vivienda", creada = dia(2026, 8, 1))
        movimiento("ev-arriendo", dia(2026, 9, 25), 1_800_000, categoria = "Vivienda", descripcion = "Arriendo")
        movimiento("ev-agosto", dia(2026, 8, 10), 10_000)

        val septiembre = assertNotNull(detalle("2026-09", ahora)).pagosFijos
        assertEquals("ev-arriendo", septiembre.de("rr-a-arriendo").eventId)

        val octubre = assertNotNull(detalle("2026-10", ahora)).pagosFijos
        assertEquals(PAGO_FIJO_PENDIENTE, octubre.de("rr-b-administracion").estado)
        assertNull(octubre.de("rr-b-administracion").eventId)
    }

    /**
     * **En un período cerrado una regla no aparece antes de existir**; en el en curso, siempre.
     * Septiembre cerrado (hoy 5-oct), con historia desde julio:
     * - creada después de que septiembre terminó → no sale; creada antes → sale pendiente;
     * - sin fecha de creación: sale con un arranque declarado, con un sello de antes, o con un pago
     *   que Movi emparejó solo en agosto; sin nada de eso, no sale.
     */
    @Test
    fun `en un periodo cerrado una regla no aparece antes de existir`() {
        val ahora = dia(2026, 10, 5)
        movimiento("ev-julio", dia(2026, 7, 10), 10_000)
        regla("rr-creada-despues", "Creada después", dia = 10, monto = 1_000, categoria = "A", creada = dia(2026, 9, 30))
        regla("rr-creada-antes", "Creada antes", dia = 10, monto = 1_000, categoria = "B", creada = dia(2026, 9, 1))
        regla("rr-con-arranque", "Con arranque", dia = 10, monto = 1_000, categoria = "C", desde = "2026-08-26")
        regla("rr-con-sello", "Con sello", dia = 10, monto = 1_000, categoria = "D")
        sello("rr-con-sello", "2026-08", evento = null)
        regla("rr-pagada-en-agosto", "Internet", dia = 10, monto = 90_000, categoria = "Servicios")
        movimiento("ev-internet-agosto", dia(2026, 8, 10), 90_000, categoria = "Servicios", descripcion = "Internet")
        regla("rr-sin-nada", "Sin nada", dia = 10, monto = 1_000, categoria = "E")

        val septiembre = assertNotNull(detalle("2026-09", ahora)).pagosFijos
        assertEquals(
            setOf("rr-creada-antes", "rr-con-arranque", "rr-con-sello", "rr-pagada-en-agosto"),
            septiembre.map { it.ruleId }.toSet(),
        )
        assertTrue(septiembre.all { it.estado == PAGO_FIJO_PENDIENTE })

        // Agosto: el pago de internet es su evidencia, y el sello de agosto la del sellado.
        val agosto = assertNotNull(detalle("2026-08", ahora)).pagosFijos
        assertEquals(PAGO_FIJO_LISTO, agosto.de("rr-pagada-en-agosto").estado)
        assertTrue(agosto.none { it.ruleId == "rr-sin-nada" || it.ruleId == "rr-creada-antes" })

        // El período en curso no se filtra: ahí el checklist pregunta por todas.
        val octubre = assertNotNull(detalle("2026-10", ahora)).pagosFijos
        assertTrue(octubre.any { it.ruleId == "rr-sin-nada" } && octubre.any { it.ruleId == "rr-creada-despues" })
    }

    /**
     * **La fecha de creación se mira por ocurrencia, no por período.**
     * - Una regla creada el 20-sep que vence el 10: su 10-sep es anterior a ella y nadie lo pagó;
     *   decir «pendiente» sería el «no lo pagaste» falso que este filtro existe para evitar.
     * - Una regla creada el 2-oct a partir del sueldo de septiembre: ese sueldo es su ocurrencia de
     *   septiembre, LISTO con evidencia, y sale aunque la regla haya nacido después.
     */
    @Test
    fun `en un periodo cerrado la fecha de creacion se mira por ocurrencia`() {
        val ahora = dia(2026, 10, 5)
        regla("rr-creada-el-20", "Gimnasio", dia = 10, monto = 180_000, categoria = "Deporte", creada = dia(2026, 9, 20))
        regla("rr-salario", "Salario", dia = 15, monto = 20_000_000, categoria = "Salario", tipo = "INCOME", creada = dia(2026, 10, 2))
        movimiento("ev-salario", dia(2026, 9, 14), 20_308_659, tipo = "INCOME", categoria = "Salario", descripcion = "Salario Septiembre 2026")

        val septiembre = assertNotNull(detalle("2026-09", ahora)).pagosFijos

        assertTrue(septiembre.none { it.ruleId == "rr-creada-el-20" }, "El 10-sep es de antes de que la regla existiera")
        septiembre.de("rr-salario").let {
            assertEquals(PAGO_FIJO_LISTO, it.estado)
            assertEquals("ev-salario", it.eventId)
        }
    }

    /**
     * **«Tu plata» no se afirma antes de que Movi conozca el saldo de todas sus cuentas**, con la
     * forma real de producción: Glim abrió el 13-ago, Bancolombia Ahorros no tiene «Saldo inicial» y
     * su primer movimiento es del 25-ago, Nu abrió el 31-ago (con un gasto anterior traído de un
     * SMS), la AFC el 8-sep y Ahorros 0031 el 21-sep. Septiembre arranca el 25-ago: `null`. Octubre
     * arranca el 24-sep, con todas conocidas: un número.
     */
    @Test
    fun `tu plata es null antes de conocer el saldo inicial de todas las cuentas`() {
        transaction {
            listOf("acc-glim", "acc-bancolombia", "acc-nu", "acc-afc", "acc-0031").forEach { cuenta(it, "SAVINGS") }
        }
        movimiento("ev-glim", dia(2026, 8, 13), 100_000, tipo = "INCOME", categoria = OPENING_CATEGORY, cuenta = "acc-glim")
        movimiento("ev-banco-1", dia(2026, 8, 25), 50_000, cuenta = "acc-bancolombia")
        movimiento("ev-banco-2", dia(2026, 8, 26), 1_000_000, tipo = "INCOME", categoria = "Salario", cuenta = "acc-bancolombia")
        movimiento("ev-nu-sms", dia(2026, 8, 20), 10_000, cuenta = "acc-nu")
        movimiento("ev-nu", dia(2026, 8, 31), 200_000, tipo = "INCOME", categoria = OPENING_CATEGORY, cuenta = "acc-nu")
        movimiento("ev-afc", dia(2026, 9, 8), 300_000, tipo = "INCOME", categoria = OPENING_CATEGORY, cuenta = "acc-afc")
        movimiento("ev-0031", dia(2026, 9, 21), 400_000, tipo = "INCOME", categoria = OPENING_CATEGORY, cuenta = "acc-0031")

        val septiembre = assertNotNull(detalle("2026-09", ahora = dia(2026, 10, 5)))
        assertNull(septiembre.tuPlataAlEmpezar, "El 25-ago Movi no conocía Bancolombia, Nu, la AFC ni la 0031")
        assertEquals(1_940_000, septiembre.tuPlataAlCerrar)

        val octubre = assertNotNull(detalle("2026-10", ahora = dia(2026, 10, 5)))
        assertEquals(1_940_000, octubre.tuPlataAlEmpezar)
    }

    /** Una fila con un tipo que no se entiende se salta: no tumba la lista ni el detalle. */
    @Test
    fun `una fila con un tipo roto no tumba la cuenta`() {
        movimiento("ev-bueno", dia(2026, 9, 10), 40_000)
        movimiento("ev-roto", dia(2026, 9, 10), 99_000, tipo = "RARO")

        val septiembre = lista(ahora = dia(2026, 10, 5)).single { it.id == "2026-09" }
        assertEquals(40_000, septiembre.salidas)
        assertEquals(1, septiembre.movimientos)
    }

    // ── La ruta ──────────────────────────────────────────────────────────────

    private val secreto = "test-secret-for-tus-periodos-tests-min-32-chars"

    private fun token(): String = JWT.create()
        .withIssuer("movi")
        .withAudience("movi-client")
        .withClaim("userId", uid)
        .withClaim("email", "duenio@periodos.test")
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC256(secreto))

    private fun Application.modulo() {
        configureSerialization()
        val verificador = JWT.require(Algorithm.HMAC256(secreto)).withIssuer("movi").withAudience("movi-client").build()
        authentication {
            jwt("jwt") {
                verifier(verificador)
                validate { JWTPrincipal(it.payload) }
            }
        }
        configureRouting()
    }

    /**
     * La fecha de creación la pone el alta y se queda en el server: la respuesta no la trae (el
     * modelo del cable no cambia) y un PUT no la toca.
     */
    @Test
    fun `el alta de una regla guarda cuando nacio sin mandarlo al cable`() = testApplication {
        application { modulo() }
        val antes = System.currentTimeMillis()
        val alta = client.post("/api/recurring-rules") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":"","name":"Arriendo","category":"Vivienda","amount":1800000,"dayOfMonth":5,"type":"EXPENSE"}""")
        }
        assertEquals(HttpStatusCode.Created, alta.status)
        assertTrue("created" !in alta.bodyAsText().lowercase(), alta.bodyAsText())
        val id = Json.decodeFromString<RecurringRule>(alta.bodyAsText()).id
        val creada = transaction {
            RecurringRules.selectAll().where { RecurringRules.id eq id }.single()[RecurringRules.createdAt]
        }
        assertTrue(creada != null && creada >= antes, "Se guarda al crearla")

        client.put("/api/recurring-rules/$id") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"id":"$id","name":"Arriendo","category":"Vivienda","amount":1900000,"dayOfMonth":5,"type":"EXPENSE"}""")
        }
        val despues = transaction {
            RecurringRules.selectAll().where { RecurringRules.id eq id }.single()[RecurringRules.createdAt]
        }
        assertEquals(creada, despues, "Corregir el monto no la cambia")
    }

    /** Autenticada, con el período del usuario leído de su fila, y 404 para lo que no es un período suyo. */
    @Test
    fun `la ruta pide sesion y contesta 404 fuera de la lista`() = testApplication {
        application { modulo() }
        val enCurso = periodoDe(System.currentTimeMillis(), delDueno).prefijo

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/periodos").status)

        val respuesta = client.get("/api/periodos") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, respuesta.status)
        val periodos = Json.decodeFromString<List<ResumenDePeriodo>>(respuesta.bodyAsText())
        assertEquals(listOf(enCurso), periodos.map { it.id }, "Sin movimientos, solo el en curso")

        listOf("2026-13", "nada", "1999-01").forEach { id ->
            val r = client.get("/api/periodos/$id") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
            assertEquals(HttpStatusCode.NotFound, r.status, id)
        }
        val r = client.get("/api/periodos/$enCurso") { header(HttpHeaders.Authorization, "Bearer ${token()}") }
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(enCurso, Json.decodeFromString<DetalleDePeriodo>(r.bodyAsText()).resumen.id)
    }
}
