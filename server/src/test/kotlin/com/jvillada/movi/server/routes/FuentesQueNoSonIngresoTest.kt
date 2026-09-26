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
import com.jvillada.movi.shared.model.FUENTE_CREDITO
import com.jvillada.movi.shared.model.FUENTE_SALDO_INICIAL
import com.jvillada.movi.shared.model.FuenteDePlata
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.PeriodSettings
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
 * El caso del dueño en septiembre (25-ago → 23-sep): el crédito Techo Gardenera se desembolsó a
 * Bancolombia como traspaso, y Movi conoció a mitad del período el saldo de Nu y del AFC. Nada de
 * eso es ingreso —y las entradas y salidas del período no cambian—, pero pagó gastos que sí lo son.
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

    /** El septiembre del dueño: un desembolso y dos saldos iniciales, con sus nombres. */
    @Test
    fun `el desembolso de un credito y los saldos iniciales son las fuentes del periodo`() {
        // Ancla la lista de períodos en julio, para que septiembre tenga detalle.
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        traspaso("tr-gardenera", dia(9, 1), 10_000_000, desde = credito, hacia = ahorros)
        saldoInicial("si-nu", dia(8, 31), 15_300_000, nu)
        saldoInicial("si-afc", dia(9, 8), 6_900_000, afc)
        movimiento("ev-obra", dia(9, 2), 9_960_000, ahorros, categoria = "Gardenera")

        val septiembre = detalle("2026-09")

        assertEquals(
            listOf(
                FuenteDePlata(FUENTE_CREDITO, 10_000_000, listOf("Crédito Techo Gardenera")),
                FuenteDePlata(FUENTE_SALDO_INICIAL, 22_200_000, listOf("Nu", "AFC Davibank")),
            ),
            septiembre.fuentesQueNoSonIngreso,
        )
        // Lo demás no cambia: ni el crédito ni los saldos iniciales son entradas, la obra sí es gasto.
        assertEquals(0, septiembre.resumen.entradas)
        assertEquals(9_960_000, septiembre.resumen.salidas)
    }

    /** Lo anulado no suma: ni un desembolso ni un saldo inicial. */
    @Test
    fun `los anulados no cuentan`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        traspaso("tr-anulado", dia(9, 1), 10_000_000, desde = credito, hacia = ahorros)
        anular("tr-anulado-entra")
        anular("tr-anulado-sale")
        saldoInicial("si-nu", dia(8, 31), 15_300_000, nu)
        saldoInicial("si-anulado", dia(9, 8), 6_900_000, afc)
        anular("si-anulado")

        assertEquals(
            listOf(FuenteDePlata(FUENTE_SALDO_INICIAL, 15_300_000, listOf("Nu"))),
            detalle("2026-09").fuentesQueNoSonIngreso,
        )
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
        traspaso("tr-del-otro", dia(9, 1), 7_000_000, desde = creditoDelOtro, hacia = cuentaDelOtro, usuario = otro)
        saldoInicial("si-del-otro", dia(9, 2), 3_000_000, cuentaDelOtro, usuario = otro)

        assertEquals(emptyList(), detalle("2026-09").fuentesQueNoSonIngreso)
        assertEquals(emptyList(), detalle("2026-08").fuentesQueNoSonIngreso)
        assertEquals(
            listOf(FuenteDePlata(FUENTE_SALDO_INICIAL, 211, listOf("Bancolombia Ahorros"))),
            detalle("2026-10").fuentesQueNoSonIngreso,
            "El período en curso también las calcula",
        )
    }

    /** Dos desembolsos del mismo crédito: una fuente, el nombre una vez. */
    @Test
    fun `dos desembolsos del mismo credito nombran el credito una vez`() {
        movimiento("ev-julio", dia(7, 10), 10_000, ahorros)
        traspaso("tr-1", dia(9, 1), 4_000_000, desde = credito, hacia = ahorros)
        traspaso("tr-2", dia(9, 15), 6_000_000, desde = credito, hacia = nu)

        val fuente = detalle("2026-09").fuentesQueNoSonIngreso.single()
        assertEquals(FUENTE_CREDITO, fuente.tipo)
        assertEquals(10_000_000, fuente.monto)
        assertEquals(listOf("Crédito Techo Gardenera"), fuente.detalle)
        assertTrue(detalle("2026-08").fuentesQueNoSonIngreso.isEmpty())
    }
}
