package com.jvillada.movi.server.routes

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.DetalleDePeriodo
import com.jvillada.movi.shared.model.DESEMBOLSO_CATEGORY
import com.jvillada.movi.shared.model.FUENTE_SALDO_INICIAL
import com.jvillada.movi.shared.model.FuenteDePlata
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.ResumenDePeriodo
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **De dónde salió lo que faltó**: la plata que entró a las cuentas en un período y no es ingreso.
 *
 * El caso del dueño en septiembre (25-ago → 23-sep): Movi conoció a mitad del período el saldo de Nu
 * y del AFC. Eso no es ingreso, pero pagó gastos que sí lo son. El crédito Techo Gardenera, en
 * cambio, se desembolsó a Bancolombia y SÍ es plata que entró: suma en «Entró», y
 * `creditosRecibidos` dice cuánto de eso es deuda.
 */
class FuentesQueNoSonIngresoTest {

    private val uid = "user-fuentes"
    private val otro = "user-otro-fuentes"
    private val ahorros = "acc-bancolombia"
    private val nu = "acc-nu"
    private val afc = "acc-afc"
    private val credito = "acc-gardenera"
    private val skandia = "acc-skandia"
    private val cdt = "acc-cdt"
    private val cuentaDelOtro = "acc-del-otro"
    private val creditoDelOtro = "acc-credito-del-otro"

    private val delDueno = PeriodSettings(cutoffDay = 25, iniciosPropios = mapOf("2026-10" to "2026-09-24"))

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:fuentes_no_ingreso_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
            listOf(uid to "duenio@fuentes.test", otro to "otro@fuentes.test").forEach { (id, correo) ->
                Users.insert {
                    it[Users.id] = id
                    it[email] = correo
                    it[name] = id
                    it[passwordHash] = "hash"
                    it[periodCutoffDay] = 25
                    it[periodStarts] = """{"2026-10":"2026-09-24"}"""
                }
            }
            cuenta(ahorros, "Bancolombia Ahorros", "SAVINGS")
            cuenta(nu, "Nu", "SAVINGS")
            cuenta(afc, "AFC Davibank", "SAVINGS", condicionadaA = "Vivienda")
            cuenta(credito, "Crédito Techo Gardenera", "LOAN")
            cuenta(skandia, "Skandia", "INVESTMENT")
            cuenta(cdt, "CDT", "INVESTMENT")
            cuenta(cuentaDelOtro, "Ahorros del otro", "SAVINGS", usuario = otro)
            cuenta(creditoDelOtro, "Crédito del otro", "LOAN", usuario = otro)
        }
    }

    private fun cuenta(id: String, nombre: String, tipo: String, condicionadaA: String? = null, usuario: String = uid) =
        Accounts.insert {
            it[Accounts.id] = id
            it[userId] = usuario
            it[name] = nombre
            it[type] = tipo
            it[conditionedTo] = condicionadaA
        }

    /** Mediodía de Bogotá: un borde de zona no puede correr el día. */
    private fun dia(m: Int, d: Int): Long = appDateToEpochMillis(LocalDate.of(2026, m, d)) + 12 * 3_600_000L

    private fun movimiento(
        id: String,
        cuando: Long,
        monto: Long,
        cuenta: String,
        tipo: String = "EXPENSE",
        categoria: String = "Comida",
        traspaso: String? = null,
        moneda: String = "COP",
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
            it[description] = ""
            it[timestamp] = cuando
            it[reconciliationStatus] = "RECONCILED"
            it[transferId] = traspaso
        }
    }

    /** Las dos patas de un desembolso, como las escribe `POST /api/transfers` desde un crédito. */
    private fun desembolso(id: String, cuando: Long, monto: Long, desde: String, hacia: String, usuario: String = uid) {
        movimiento("$id-sale", cuando, monto, desde, categoria = DESEMBOLSO_CATEGORY, traspaso = id, usuario = usuario)
        movimiento("$id-entra", cuando, monto, hacia, tipo = "INCOME", categoria = DESEMBOLSO_CATEGORY, traspaso = id, usuario = usuario)
    }

    /** Las dos patas de un traspaso, como las escribe `POST /api/transfers`. */
    private fun traspaso(id: String, cuando: Long, monto: Long, desde: String, hacia: String, usuario: String = uid) {
        movimiento("$id-sale", cuando, monto, desde, categoria = TRANSFER_CATEGORY, traspaso = id, usuario = usuario)
        movimiento("$id-entra", cuando, monto, hacia, tipo = "INCOME", categoria = TRANSFER_CATEGORY, traspaso = id, usuario = usuario)
    }

    private fun saldoInicial(id: String, cuando: Long, monto: Long, cuenta: String, usuario: String = uid) =
        movimiento(id, cuando, monto, cuenta, tipo = "INCOME", categoria = OPENING_CATEGORY, usuario = usuario)

    private fun anular(id: String) = transaction {
        VoidEvents.insert {
            it[VoidEvents.id] = "void-$id"
            it[userId] = uid
            it[originalEventId] = id
            it[VoidEvents.timestamp] = 0L
        }
    }

    private fun detalle(id: String): DetalleDePeriodo =
        assertNotNull(transaction { detalleDePeriodo(uid, id, dia(10, 5), delDueno) })

    private fun lista(): List<ResumenDePeriodo> =
        transaction { resumenesDePeriodos(uid, dia(10, 5), delDueno) }

    /** El septiembre del dueño: un desembolso que entró y dos saldos iniciales que no son ingreso. */
    @Test
    fun `el desembolso entra y solo los saldos iniciales son fuente que no es ingreso`() {
        // Ancla la lista de períodos en julio, para que septiembre tenga detalle.
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        desembolso("tr-gardenera", dia(9, 1), 10_000_000, desde = credito, hacia = ahorros)
        saldoInicial("si-nu", dia(8, 31), 15_300_000, nu)
        saldoInicial("si-afc", dia(9, 8), 6_900_000, afc)
        movimiento("ev-obra", dia(9, 2), 9_960_000, ahorros, categoria = "Gardenera")

        val septiembre = detalle("2026-09")

        assertEquals(
            listOf(FuenteDePlata(FUENTE_SALDO_INICIAL, 22_200_000, listOf("Nu", "AFC Davibank"))),
            septiembre.fuentesQueNoSonIngreso,
        )
        // El desembolso es plata que entró (la pata del crédito no suma otra vez); los saldos
        // iniciales no; la obra sí es gasto.
        assertEquals(10_000_000, septiembre.resumen.entradas)
        assertEquals(10_000_000, septiembre.resumen.creditosRecibidos)
        assertEquals(9_960_000, septiembre.resumen.salidas)
    }

    /** Lo anulado no suma: ni un desembolso ni un saldo inicial. */
    @Test
    fun `los anulados no cuentan`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        desembolso("tr-anulado", dia(9, 1), 10_000_000, desde = credito, hacia = ahorros)
        anular("tr-anulado-entra")
        anular("tr-anulado-sale")
        saldoInicial("si-nu", dia(8, 31), 15_300_000, nu)
        saldoInicial("si-anulado", dia(9, 8), 6_900_000, afc)
        anular("si-anulado")

        assertEquals(
            listOf(FuenteDePlata(FUENTE_SALDO_INICIAL, 15_300_000, listOf("Nu"))),
            detalle("2026-09").fuentesQueNoSonIngreso,
        )
        assertEquals(0, detalle("2026-09").resumen.creditosRecibidos)
        assertEquals(0, detalle("2026-09").resumen.entradas)
    }

    /**
     * Mover plata entre cuentas propias no es un crédito, y el saldo inicial de una inversión no es
     * «tu plata» (Skandia taparía todo lo demás): ninguno de los dos es una fuente.
     */
    @Test
    fun `un traspaso entre cuentas propias y el saldo de una inversion no son fuentes`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        traspaso("tr-al-cdt", dia(9, 3), 2_000_000, desde = ahorros, hacia = cdt)
        traspaso("tr-del-cdt", dia(9, 4), 1_000_000, desde = cdt, hacia = ahorros)
        saldoInicial("si-skandia", dia(9, 5), 106_000_000, skandia)
        // Pagarle al crédito (abono extraordinario) tampoco: la plata sale, no entra.
        traspaso("tr-abono", dia(9, 6), 500_000, desde = ahorros, hacia = credito)
        // La «Deuda inicial» del crédito es su propia apertura, no plata que entró a tus cuentas.
        saldoInicial("si-credito", dia(9, 1), 10_000_000, credito)

        assertEquals(emptyList(), detalle("2026-09").fuentesQueNoSonIngreso)
    }

    /** Lo de otro período, en otra moneda o de otro usuario no se mezcla; sin nada, vacía. */
    @Test
    fun `un periodo sin fuentes tiene la lista vacia y nada ajeno se mezcla`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        // Octubre (arrancó el 24-sep): no es de septiembre.
        saldoInicial("si-octubre", dia(9, 24), 211, ahorros)
        // En dólares: una suma en pesos no puede leerlo como pesos.
        movimiento("si-usd", dia(9, 10), 700, nu, tipo = "INCOME", categoria = OPENING_CATEGORY, moneda = "USD")
        desembolso("tr-del-otro", dia(9, 1), 7_000_000, desde = creditoDelOtro, hacia = cuentaDelOtro, usuario = otro)
        saldoInicial("si-del-otro", dia(9, 2), 3_000_000, cuentaDelOtro, usuario = otro)

        assertEquals(emptyList(), detalle("2026-09").fuentesQueNoSonIngreso)
        assertEquals(0, detalle("2026-09").resumen.creditosRecibidos)
        assertEquals(emptyList(), detalle("2026-08").fuentesQueNoSonIngreso)
        assertEquals(
            listOf(FuenteDePlata(FUENTE_SALDO_INICIAL, 211, listOf("Bancolombia Ahorros"))),
            detalle("2026-10").fuentesQueNoSonIngreso,
            "El período en curso también las calcula",
        )
    }

    /** Dos desembolsos del mismo crédito, a cuentas distintas: los dos suman, y ninguno es «fuente». */
    @Test
    fun `dos desembolsos del mismo credito suman en lo que entro y en los creditos recibidos`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        desembolso("tr-1", dia(9, 1), 4_000_000, desde = credito, hacia = ahorros)
        desembolso("tr-2", dia(9, 15), 6_000_000, desde = credito, hacia = nu)

        val septiembre = detalle("2026-09")
        assertTrue(septiembre.fuentesQueNoSonIngreso.isEmpty())
        assertEquals(10_000_000, septiembre.resumen.creditosRecibidos)
        assertEquals(10_000_000, septiembre.resumen.entradas)
        assertTrue(detalle("2026-08").fuentesQueNoSonIngreso.isEmpty())
    }

    /**
     * Un desembolso guardado ANTES de la regla —«Traspaso» desde el crédito— todavía no está en
     * «Entró», así que tampoco puede aparecer en «Incluye…»: `creditosRecibidos` es siempre parte de
     * `entradas`. La migración de datos lo pasa a «Desembolso de crédito» y desde ahí suma y se dice.
     */
    @Test
    fun `un desembolso viejo anotado como traspaso no cuenta en creditos recibidos`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        traspaso("tr-viejo", dia(9, 1), 10_000_000, desde = credito, hacia = ahorros)
        // Entre cuentas propias y un abono al crédito tampoco.
        traspaso("tr-al-cdt", dia(9, 3), 2_000_000, desde = ahorros, hacia = cdt)
        traspaso("tr-abono", dia(9, 6), 500_000, desde = ahorros, hacia = credito)

        val septiembre = detalle("2026-09").resumen
        assertEquals(0, septiembre.creditosRecibidos)
        assertEquals(0, septiembre.entradas)
    }

    /** Lo invariante: lo desembolsado nunca es más que lo que entró. */
    @Test
    fun `los creditos recibidos nunca pasan de lo que entro`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        desembolso("tr-nuevo", dia(9, 1), 4_000_000, desde = credito, hacia = ahorros)
        traspaso("tr-viejo", dia(9, 2), 10_000_000, desde = credito, hacia = nu)
        movimiento("ev-sueldo", dia(9, 3), 3_000_000, ahorros, tipo = "INCOME", categoria = "Salario")
        lista().forEach { assertTrue(it.creditosRecibidos <= it.entradas, it.id) }
        assertEquals(4_000_000, lista().single { it.id == "2026-09" }.creditosRecibidos)
    }

    /** La lista dice lo mismo que el detalle: un desembolso y dos saldos iniciales en septiembre. */
    @Test
    fun `la lista trae los creditos y los saldos iniciales de cada periodo`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        desembolso("tr-gardenera", dia(9, 1), 10_000_000, desde = credito, hacia = ahorros)
        saldoInicial("si-nu", dia(8, 31), 15_300_000, nu)
        saldoInicial("si-afc", dia(9, 8), 6_900_000, afc)
        movimiento("ev-obra", dia(9, 2), 9_960_000, ahorros, categoria = "Gardenera")
        // Octubre (arrancó el 24-sep) tiene el suyo: no se mezcla con septiembre.
        saldoInicial("si-octubre", dia(9, 24), 211, ahorros)

        val periodos = lista().associateBy { it.id }
        assertEquals(10_000_000, periodos.getValue("2026-09").creditosRecibidos)
        assertEquals(22_200_000, periodos.getValue("2026-09").saldosIniciales)
        assertEquals(0, periodos.getValue("2026-10").creditosRecibidos)
        assertEquals(211, periodos.getValue("2026-10").saldosIniciales)
        // Sin fuentes: cero, también en el hueco.
        assertEquals(0, periodos.getValue("2026-08").creditosRecibidos)
        assertEquals(0, periodos.getValue("2026-08").saldosIniciales)
        // El desembolso es plata que entró.
        assertEquals(10_000_000, periodos.getValue("2026-09").entradas)
        assertEquals(9_960_000, periodos.getValue("2026-09").salidas)
    }

    @Test
    fun `la lista no cuenta anulados ni traspasos entre cuentas propias ni dolares`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        desembolso("tr-anulado", dia(9, 1), 10_000_000, desde = credito, hacia = ahorros)
        anular("tr-anulado-entra")
        anular("tr-anulado-sale")
        saldoInicial("si-anulado", dia(9, 8), 6_900_000, afc)
        anular("si-anulado")
        traspaso("tr-al-cdt", dia(9, 3), 2_000_000, desde = ahorros, hacia = cdt)
        saldoInicial("si-skandia", dia(9, 5), 106_000_000, skandia)
        movimiento("si-usd", dia(9, 10), 700, nu, tipo = "INCOME", categoria = OPENING_CATEGORY, moneda = "USD")

        val septiembre = lista().single { it.id == "2026-09" }
        assertEquals(0, septiembre.creditosRecibidos)
        assertEquals(0, septiembre.saldosIniciales)
    }

    /** Una sola regla: para cada período de la lista, el detalle da las mismas cifras. */
    @Test
    fun `la lista y el detalle dan los mismos numeros para el mismo periodo`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        desembolso("tr-1", dia(9, 1), 4_000_000, desde = credito, hacia = ahorros)
        desembolso("tr-2", dia(9, 24), 6_000_000, desde = credito, hacia = nu)
        saldoInicial("si-nu", dia(8, 31), 15_300_000, nu)
        saldoInicial("si-afc", dia(10, 2), 6_900_000, afc)

        lista().forEach { fila ->
            val d = detalle(fila.id).resumen
            assertEquals(d.creditosRecibidos, fila.creditosRecibidos, fila.id)
            assertEquals(d.saldosIniciales, fila.saldosIniciales, fila.id)
            val fuentes = detalle(fila.id).fuentesQueNoSonIngreso
            assertEquals(d.entradas, fila.entradas, fila.id)
            assertEquals(fuentes.filter { it.tipo == FUENTE_SALDO_INICIAL }.sumOf { it.monto }, fila.saldosIniciales, fila.id)
        }
        assertEquals(4_000_000, lista().single { it.id == "2026-09" }.creditosRecibidos)
        assertEquals(6_000_000, lista().single { it.id == "2026-10" }.creditosRecibidos)
    }
}
