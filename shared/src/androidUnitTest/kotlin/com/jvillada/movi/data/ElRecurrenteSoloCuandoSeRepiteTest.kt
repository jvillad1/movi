package com.jvillada.movi.data

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ComoSeGuardo
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * # «¿Se repite todos los meses?» en la app (vuelta 44)
 *
 * El gate de después de guardar ya no ofrece el recurrente por cualquier gasto: pide la historia, las
 * cuentas y el período y pasa por `seRepiteParaProponer`. Aparece con el gimnasio de los dos meses
 * anteriores; no aparece con un gasto nuevo, con $15.000 en efectivo, con el pago de una factura ni si
 * la historia no se pudo leer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ElRecurrenteSoloCuandoSeRepiteTest {

    private fun el(dia: String): Long =
        LocalDate.parse(dia).atTime(LocalTime(12, 0)).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private fun gasto(id: String, dia: String, monto: Long, cuenta: String = "tarjeta", titulo: String = "Gimnasio Bodytech") =
        FinancialEvent(
            id = id, accountId = cuenta, type = TransactionType.EXPENSE, amount = monto,
            category = "Deporte", description = titulo, timestamp = el(dia),
        )

    private val cuentas = listOf(
        Account("tarjeta", "Nu", AccountType.SAVINGS, 0L),
        Account("efectivo", "Efectivo", AccountType.CASH, 0L),
    )
    private var historia: List<FinancialEvent>? = emptyList()

    @Before
    fun montar() {
        RecurringOfferGate.clear()
        Repositories.sustitutoDePrueba = object : RepositorioDePrueba() {
            override suspend fun getRecurringRules(): List<RecurringRule> = emptyList()
            override suspend fun getSubscriptions(): SubscriptionsResult = SubscriptionsResult(subscriptions = emptyList(), monthlyTotalCop = 0L)
            override suspend fun getAccounts(): List<Account> = cuentas
            override suspend fun getEventsByDay(): List<EventDay> =
                historia?.let { listOf(EventDay(date = "2026-10-28", total = 0L, items = it)) } ?: throw IllegalStateException("sin red")
            override suspend fun getUserProfile(): UserProfile =
                UserProfile(id = "u1", email = "juan@movi.test", name = "Juan", avatarColor = "#4F7CFF")
        }
    }

    @After
    fun limpiar() {
        Repositories.sustitutoDePrueba = null
        RecurringOfferGate.clear()
    }

    private val hoy = gasto("nuevo", "2026-10-28", 120_000)

    @Test
    fun `aparece con el mismo gasto en dos periodos anteriores`() = runBlocking<Unit> {
        historia = listOf(gasto("a", "2026-08-20", 118_000), gasto("b", "2026-09-20", 121_000), hoy)
        assertNotNull(RecurringOfferGate.ofrecerPara(hoy))
    }

    @Test
    fun `no aparece con un gasto que Movi no habia visto`() = runBlocking<Unit> {
        historia = listOf(hoy)
        assertNull(RecurringOfferGate.ofrecerPara(hoy))
    }

    @Test
    fun `no aparece con 15 mil en efectivo`() = runBlocking<Unit> {
        val almuerzo = gasto("nuevo", "2026-10-28", 15_000, cuenta = "efectivo", titulo = "Restaurante")
        historia = listOf(
            gasto("a", "2026-08-20", 15_000, cuenta = "efectivo", titulo = "Restaurante"),
            gasto("b", "2026-09-20", 15_000, cuenta = "efectivo", titulo = "Restaurante"),
        )
        assertNull(RecurringOfferGate.ofrecerPara(almuerzo))
    }

    @Test
    fun `no aparece con el pago de algo que se espera`() = runBlocking<Unit> {
        historia = listOf(gasto("a", "2026-08-20", 120_000), gasto("b", "2026-09-20", 120_000))
        assertNull(RecurringOfferGate.ofrecerPara(hoy, ComoSeGuardo(saldoUnaOcurrencia = true)))
    }

    @Test
    fun `sin la historia no se ofrece`() = runBlocking<Unit> {
        historia = null
        assertNull(RecurringOfferGate.ofrecerPara(hoy))
    }
}
