package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.routes.buildUserContext
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **Movi AI tiene que saber de la plata del dueño lo mismo que sabe la pantalla.**
 *
 * Él lo pidió así: *«el asistente de IA debería poder tener el contexto de todos los datos de la
 * app, no ser un chat de IA y ya»*. Antes llevaba tres cifras del mes, las cuentas, los
 * presupuestos y los documentos; con eso no podía contestar en qué se fue la plata, qué recurrente
 * le falta pagar ni a qué tasa está endeudado.
 *
 * Estas pruebas fijan lo que entra Y lo que NO: un anulado o algo en «Por confirmar» no cuentan,
 * porque tampoco cuentan en el Inicio, y un asistente que diga otra cifra que la pantalla es peor
 * que uno que no sepa.
 */
class ContextoDelPeriodoTest {

    private val dueno = "user-contexto-periodo"
    private val ahora = AppClock.now().toInstant().toEpochMilli()

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:contexto_periodo_ai;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            // La base en memoria sobrevive entre métodos (`DB_CLOSE_DELAY=-1`), así que cada uno
            // arranca borrando: si no, el segundo test choca contra el usuario que insertó el
            // primero. Solo las tablas de esta clase, en su propia base.
            SchemaUtils.drop(
                Documents, VoidEvents, Events, Budgets, Goals, Subscriptions, SmsMessages,
                RecurringOccurrences, RecurringRules, Credits, Accounts, Users,
            )
            SchemaUtils.create(
                Users, Accounts, Events, VoidEvents, Budgets, Documents, RecurringRules,
                RecurringOccurrences, Credits, Subscriptions, Goals, SmsMessages,
            )
            Users.insert {
                it[id] = dueno
                it[email] = "dueno@contexto.test"
                it[name] = "Camilo"
                it[passwordHash] = "hash"
            }
            Accounts.insert {
                it[id] = "a1"
                it[userId] = dueno
                it[name] = "Ahorros Nómina"
                it[type] = "SAVINGS"
                it[balance] = 5_000_000L
            }
        }
    }

    private fun gasto(categoria: String, monto: Long, estado: String = "RECONCILED", id: String = "e-$categoria-$monto") =
        transaction {
            Events.insert {
                it[Events.id] = id
                it[userId] = dueno
                it[accountId] = "a1"
                it[type] = TransactionType.EXPENSE.name
                it[amount] = monto
                it[currency] = "COP"
                it[category] = categoria
                it[description] = categoria
                it[timestamp] = ahora
                it[reconciliationStatus] = estado
            }
        }

    private fun contexto() = runBlocking { buildUserContext(dueno) }

    @Test
    fun `dice en que se fue la plata del periodo, por categoria`() {
        gasto("Mercado", 612_300)
        gasto("Vivienda", 1_850_000)

        val texto = contexto()

        assertTrue("En qué se fue la plata este período" in texto)
        assertTrue("Vivienda: \$1850000" in texto, "falta la categoría más pesada:\n$texto")
        assertTrue("Mercado: \$612300" in texto)
    }

    /** Un movimiento anulado no cuenta en el Inicio; tampoco puede contar acá. */
    @Test
    fun `lo anulado no entra en el contexto`() {
        gasto("Mercado", 500_000, id = "e-anulado")
        transaction {
            VoidEvents.insert {
                it[id] = "v1"
                it[userId] = dueno
                it[originalEventId] = "e-anulado"
                it[timestamp] = ahora
            }
        }

        assertFalse("Mercado" in contexto(), "un anulado no puede aparecer como gasto")
    }

    /** «Por confirmar» tampoco: la pantalla lo deja afuera de las cifras hasta que el dueño decide. */
    @Test
    fun `lo que espera en Por confirmar no entra`() {
        gasto("Restaurantes", 42_500, estado = "UNCONFIRMED")
        assertFalse("Restaurantes: " in contexto())
    }

    @Test
    fun `los recurrentes van con su dia y con si ya ocurrieron en este periodo`() {
        transaction {
            RecurringRules.insert {
                it[id] = "r1"; it[userId] = dueno; it[name] = "Arriendo"; it[category] = "Vivienda"
                it[amount] = 1_850_000L; it[dayOfMonth] = 5; it[type] = TransactionType.EXPENSE.name
            }
            RecurringRules.insert {
                it[id] = "r2"; it[userId] = dueno; it[name] = "Gimnasio"; it[category] = "Salud"
                it[amount] = 139_900L; it[dayOfMonth] = 20; it[type] = TransactionType.EXPENSE.name
            }
            val periodo = "%04d-%02d".format(AppClock.now().year, AppClock.now().monthValue)
            RecurringOccurrences.insert {
                it[userId] = dueno; it[ruleId] = "r1"; it[period] = periodo
                it[eventId] = null; it[confirmedAt] = ahora
            }
        }

        val texto = contexto()

        assertTrue("Arriendo" in texto && "YA ocurrió en este período" in texto)
        assertTrue("Gimnasio" in texto && "TODAVÍA no ocurrió en este período" in texto)
    }

    @Test
    fun `un credito viaja con su tasa, su cuota y su plazo`() {
        transaction {
            Accounts.insert {
                it[id] = "a2"; it[userId] = dueno; it[name] = "Vehículo 8761"; it[type] = "LOAN"; it[balance] = 40_000_000L
            }
            Credits.insert {
                it[accountId] = "a2"; it[userId] = dueno; it[bank] = "Banco de ejemplo"
                it[principal] = 60_000_000L; it[rateEa] = 18.5; it[termMonths] = 60
                it[installment] = 4_101_123L; it[dayOfMonth] = 15; it[startDate] = "2024-01-15"
                it[insuranceMonthly] = 52_000L
            }
        }

        val texto = contexto()

        assertTrue("Vehículo 8761" in texto)
        assertTrue("18.5 % EA" in texto, "sin la tasa no se puede opinar de una deuda:\n$texto")
        assertTrue("cuota \$4101123" in texto)
        assertTrue("seguro de vida \$52000" in texto)
    }

    @Test
    fun `el periodo del dueno se nombra, no el mes de calendario`() {
        val texto = contexto()
        assertTrue("El período en curso" in texto)
        assertTrue("cierra su mes el día" in texto)
        assertTrue("Quedan" in texto)
    }

    @Test
    fun `lo que espera al dueno se cuenta aparte`() {
        gasto("Mercado", 10_000, estado = "UNCONFIRMED", id = "e-pendiente")
        transaction {
            SmsMessages.insert {
                it[id] = "s1"; it[userId] = dueno; it[time] = "2026-09-14 10:00"; it[bank] = "Bancolombia"
                it[text] = "Compra por \$10.000"; it[state] = "pending"; it[det] = ""
            }
        }

        val texto = contexto()

        assertTrue("Lo que está esperando al dueño" in texto)
        assertTrue("1 mensajes del banco sin confirmar" in texto)
        assertTrue("1 movimientos en «Por confirmar»" in texto)
    }

    @Test
    fun `sin datos el contexto no inventa nada`() {
        val texto = contexto()
        assertTrue("(todavía sin gastos registrados en este período)" in texto)
        assertTrue("(sin recurrentes cargados)" in texto)
        assertFalse("Créditos, con sus condiciones" in texto, "sin créditos, la sección no va")
    }
}
