package com.jvillada.movi.server.compartir

import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Apartados
import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CardPaymentDismissals
import com.jvillada.movi.server.db.Cards
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.Documents
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Goals
import com.jvillada.movi.server.db.OccurrenceRejections
import com.jvillada.movi.server.db.PushSubscriptions
import com.jvillada.movi.server.db.RecurringOccurrences
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.server.db.StatementImports
import com.jvillada.movi.server.db.Subscriptions
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.server.sms.loQueMoviSabeDelPagoDe
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * # Vuelta 134 · la cuota de este mes, también fuera de Pagos
 *
 * Dos lugares leían `installment` de la base en vez de la cuota de este mes de un crédito que cambia:
 *
 * - **M-2 · la página compartida** decía «Cuota $1.200.000 al mes» de un crédito de tasa variable cuya
 *   última cuota fue $1.262.300. Ahora dice la cuota de este mes, la misma de Pagos y «Te quedan»
 *   (`cuotaDeEsteMes` con la última pagada de verdad), con «unos».
 * - **M-8 · reconocer un aviso como cuota**: con dos créditos del mismo banco, el aviso se desempataba por
 *   la cuota pactada exacta; la de un crédito que cambia ya no es la pactada.
 *
 * Un crédito de cuota fija sigue con su cuota de siempre en los dos.
 */
class LaCuotaDeEsteMesFueraDePagosTest {

    private val uid = "user-compartido-cuota"
    private val ahorros = "acc-ahorros-compartido"
    private val libre = "acc-libre-compartido"
    private val carro = "acc-carro-compartido"

    private val tablas = arrayOf(
        Users, Accounts, StatementImports, Events, VoidEvents, Budgets, RecurringRules, SmsMessages,
        Credits, CardPaymentDismissals, Cards, Goals, Apartados, Documents, RecurringOccurrences,
        OccurrenceRejections, Subscriptions, CategoryPrefs, PushSubscriptions,
    )

    @BeforeTest
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:cuota_compartida_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            driver = "org.h2.Driver",
        )
        val hoy = AppClock.today()
        transaction {
            SchemaUtils.drop(*tablas)
            SchemaUtils.create(*tablas)
            Users.insert { it[id] = uid; it[email] = "cuota@movi.test"; it[name] = "Juan"; it[passwordHash] = "hash" }
            Accounts.insert { it[id] = ahorros; it[userId] = uid; it[name] = "Ahorros 8133"; it[type] = "SAVINGS"; it[currency] = "COP" }
            Accounts.insert { it[id] = libre; it[userId] = uid; it[name] = "Libre inversión 9695"; it[type] = "LOAN"; it[currency] = "COP" }
            Credits.insert {
                it[accountId] = libre; it[userId] = uid
                it[bank] = "Bancolombia"; it[principal] = 50_000_000
                it[rateEa] = 18.0; it[termMonths] = 72
                it[installment] = 1_200_000; it[dayOfMonth] = hoy.minusDays(15).dayOfMonth.coerceAtMost(28)
                it[startDate] = "2024-01-10"; it[tipoDeCuota] = "TASA_VARIABLE"
            }
            fun evento(id: String, cuenta: String, tipo: String, monto: Long, categoria: String, cuando: Long, traspaso: String? = null) =
                Events.insert {
                    it[Events.id] = id; it[Events.userId] = uid; it[Events.accountId] = cuenta
                    it[Events.type] = tipo; it[Events.amount] = monto; it[Events.category] = categoria
                    it[Events.description] = "Cuota"; it[Events.timestamp] = cuando; it[Events.transferId] = traspaso
                }
            // La deuda: lo que se debe (un gasto en la cuenta del crédito sube la deuda).
            evento("ev-deuda", libre, "EXPENSE", 40_000_000, "Ajuste de saldo", appDateToEpochMillis(hoy.minusMonths(6)))
            // La cuota de este mes, pagada: $1.262.300 salieron de ahorros; la deuda bajó por el capital.
            val pago = appDateToEpochMillis(hoy.minusDays(15))
            evento("ev-sale", ahorros, "EXPENSE", 1_262_300, CUOTA_CATEGORY, pago, "tr-cuota")
            evento("ev-baja", libre, "INCOME", 700_000, CUOTA_CATEGORY, pago, "tr-cuota")
        }
    }

    private fun detalleDelCredito(): String {
        val r = runBlocking { resumenCompartidoDe(uid, venceEn = 0L) }
        return PaginaCompartida.detalleDeLaDeuda(r.deudasConSaldo.single())
    }

    @Test
    fun `un credito de tasa variable dice la cuota de este mes con unos`() {
        val detalle = detalleDelCredito()
        assertEquals(true, "Cuota de unos \$1.262.300 al mes" in detalle, detalle)
    }

    /** Otro crédito de Bancolombia, de cuota fija, para que el aviso tenga que desempatar por la cuota. */
    private fun otroCreditoDelMismoBanco() = transaction {
        Accounts.insert { it[id] = carro; it[userId] = uid; it[name] = "Vehículo 4083"; it[type] = "LOAN"; it[currency] = "COP" }
        Credits.insert {
            it[accountId] = carro; it[userId] = uid
            it[bank] = "Bancolombia"; it[principal] = 30_000_000
            it[rateEa] = 14.0; it[termMonths] = 60
            it[installment] = 900_000; it[dayOfMonth] = 10
            it[startDate] = "2024-01-10"
        }
    }

    private fun deudaDelAviso(monto: Double): String? = transaction {
        loQueMoviSabeDelPagoDe(uid, ParsedSms(monto, "Bancolombia", TransactionType.EXPENSE, CUOTA_CATEGORY, nota = "Pago de crédito")).deudaSugeridaId
    }

    @Test
    fun `el aviso de la cuota de un credito que cambia se reconoce con la cuota de este mes`() {
        otroCreditoDelMismoBanco()
        // La pactada era $1.200.000; la de este mes es la última pagada, $1.262.300, y la de hoy sale cerca.
        assertEquals(libre, deudaDelAviso(1_262_300.0))
        assertEquals(libre, deudaDelAviso(1_250_000.0), "una estimación se reconoce de cerca (±10 %), no al peso")
        // El de cuota fija sigue exacto, como siempre.
        assertEquals(carro, deudaDelAviso(900_000.0))
        assertNull(deudaDelAviso(950_000.0), "sin la cuota exacta, ninguno: no se adivina")
    }

    @Test
    fun `un credito de cuota fija sigue con su cuota de siempre`() {
        transaction { Credits.update({ Credits.accountId eq libre }) { it[tipoDeCuota] = null } }
        val detalle = detalleDelCredito()
        assertEquals(true, "Cuota \$1.200.000 al mes" in detalle, detalle)
    }
}
