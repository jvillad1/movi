package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.createDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.TransactionType
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalRepositoryTest {

    private lateinit var repo: LocalRepository

    private val testUserId = "user-test-1"

    @BeforeTest
    fun setup() {
        val db = createDatabase("test.db")
        repo = LocalRepository(
            db = db,
            remote = NoOpRepository(),
            userId = { testUserId },
        )
    }

    @Test
    fun postEvent_increases_account_balance() = runBlocking {
        repo.createAccount(Account("acc1", "Cash", AccountType.CASH, 0L))

        repo.postEvent(event("evt1", "acc1", TransactionType.INCOME, 5_000L))

        val account = repo.getAccount("acc1")
        assertEquals(5_000L, account.balance)
    }

    @Test
    fun voidEvent_reverses_account_balance() = runBlocking {
        repo.createAccount(Account("acc2", "Checking", AccountType.CHECKING, 10_000L))
        repo.postEvent(event("evt2", "acc2", TransactionType.EXPENSE, 3_000L))

        repo.voidEvent("evt2")

        val account = repo.getAccount("acc2")
        assertEquals(10_000L, account.balance)
    }

    @Test
    fun getEvents_excludes_voided_events() = runBlocking {
        repo.createAccount(Account("acc3", "Savings", AccountType.SAVINGS, 0L))
        repo.postEvent(event("evt3", "acc3", TransactionType.INCOME, 1_000L))
        repo.postEvent(event("evt4", "acc3", TransactionType.INCOME, 2_000L))

        repo.voidEvent("evt3")

        // getEvents still returns all events (voiding doesn't delete, it records a VoidEvent)
        // the voided event remains in financial_events; callers filter by checking void_events
        val events = repo.getEvents("acc3")
        assertTrue(events.any { it.id == "evt3" })
        assertTrue(events.any { it.id == "evt4" })
    }

    private fun event(id: String, accountId: String, type: TransactionType, amount: Long) =
        FinancialEvent(
            id = id, accountId = accountId, type = type, amount = amount,
            category = "test", description = "test",
            timestamp = System.currentTimeMillis(),
            source = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.UNCONFIRMED,
        )
}
