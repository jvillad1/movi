package com.jvillada.movi.server.routes

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.PeriodSettings
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * **«vs. período pasado» en el server**: el gasto del período anterior a la misma altura, con la regla
 * de «Gastos». La franja exacta (bordes, zona, inicios propios) la prueba `ContraElPeriodoPasadoTest`
 * de `:core`; acá, que la suma sea la de siempre, que sea de este dueño y nada más, y que sin un
 * período pasado entero no haya comparación.
 *
 * Corte 25. «Hoy» es el 27-oct a mediodía de Bogotá: día 3 de noviembre (25-oct → 24-nov), así que se
 * compara contra el 25, 26 y 27 de septiembre.
 */
class ContraElPeriodoPasadoServerTest {

    private val uid = "user-contra-el-pasado"
    private val otro = "user-otro-contra-el-pasado"
    private val corte25 = PeriodSettings(cutoffDay = 25)
    private val ahora = appDateToEpochMillis(LocalDate.of(2026, 10, 27)) + 12 * 3_600_000L
    private var n = 0

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:contra_el_pasado;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(VoidEvents, Events, Accounts, Users)
            SchemaUtils.create(Users, Accounts, Events, VoidEvents)
            listOf(uid, otro).forEach { u ->
                Users.insert { it[id] = u; it[email] = "$u@test"; it[name] = u; it[passwordHash] = "h"; it[periodCutoffDay] = 25 }
                Accounts.insert { it[id] = "acc_$u"; it[userId] = u; it[name] = "Ahorros"; it[type] = "SAVINGS"; it[balance] = 0L }
            }
        }
    }

    private fun gasto(fecha: LocalDate, monto: Long, categoria: String = "Comida", usuario: String = uid, estado: String = "RECONCILED") =
        transaction {
            Events.insert {
                it[id] = "ev_${n++}"
                it[userId] = usuario
                it[accountId] = "acc_$usuario"
                it[type] = "EXPENSE"
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = "gasto"
                it[timestamp] = appDateToEpochMillis(fecha) + 12 * 3_600_000L
                it[reconciliationStatus] = estado
            }
        }

    private fun leer(usuario: String = uid) = transaction {
        gastoDelAnteriorAMismaAltura(usuario, ahora, corte25, emptySet(), accountTypesFor(usuario))
    }

    @Test
    fun `suma solo los primeros dias del periodo pasado, con la regla de Gastos`() {
        // Movi lleva las cuentas desde antes de octubre (25-sep → 24-oct), el período pasado.
        gasto(LocalDate.of(2026, 8, 20), 1_000)
        gasto(LocalDate.of(2026, 9, 25), 50_000)            // día 1 de octubre: entra
        gasto(LocalDate.of(2026, 9, 27), 30_000)            // día 3: entra
        gasto(LocalDate.of(2026, 9, 28), 900_000)           // día 4: ya no
        gasto(LocalDate.of(2026, 9, 26), 70_000, "Mercado") // otra categoría, día 2: entra
        gasto(LocalDate.of(2026, 9, 26), 40_000, estado = "UNCONFIRMED") // «Por confirmar»: no suma
        gasto(LocalDate.of(2026, 9, 26), 500_000, usuario = otro)        // de otro dueño: no suma

        val (porCategoria, dias) = leer()!!
        assertEquals(3, dias)
        assertEquals(mapOf("Comida" to 80_000L, "Mercado" to 70_000L), porCategoria)
    }

    @Test
    fun `sin el periodo pasado entero no hay comparacion`() {
        // El primer movimiento es del 26-sep: el período pasado arrancó el 25 y Movi no lo vio entero.
        gasto(LocalDate.of(2026, 9, 26), 70_000)
        assertNull(leer())
    }

    @Test
    fun `sin ningun movimiento tampoco`() {
        assertNull(leer())
    }
}
