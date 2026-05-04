package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.Credit
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.Transaction
import com.jvillada.movi.shared.model.TransactionDay
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.VoidEvent
import com.jvillada.movi.shared.model.Wallet
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class LocalRepository(
    private val db: MoviDatabase,
    private val remote: WalletRepository,
    private val userId: () -> String,
) : WalletRepository {

    // ── Accounts ──────────────────────────────────────────────────────────────

    override suspend fun getAccounts(): List<Account> =
        db.accountQueries.selectAll(userId()).executeAsList().map { row ->
            Account(id = row.id, name = row.name,
                type = AccountType.valueOf(row.type),
                balance = row.balance, currency = row.currency)
        }

    override suspend fun getAccount(id: String): Account =
        db.accountQueries.selectById(id).executeAsOne().let { row ->
            Account(id = row.id, name = row.name,
                type = AccountType.valueOf(row.type),
                balance = row.balance, currency = row.currency)
        }

    override suspend fun createAccount(account: Account): Account {
        db.accountQueries.insert(
            account.id, account.name, account.type.name,
            account.balance, account.currency, userId()
        )
        return account
    }

    // ── Events ────────────────────────────────────────────────────────────────

    override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
        db.transaction {
            db.financialEventQueries.insert(
                event.id, event.accountId, event.type.name, event.amount,
                event.category, event.description, event.merchant,
                event.timestamp, event.source.name, event.rawPayload,
                event.reconciliationStatus.name, event.syncedAt, userId()
            )
            val acct = db.accountQueries.selectById(event.accountId).executeAsOneOrNull()
            if (acct != null) {
                val delta = if (event.type == TransactionType.INCOME) event.amount else -event.amount
                db.accountQueries.updateBalance(acct.balance + delta, acct.id)
            }
        }
        return event
    }

    override suspend fun getEvents(accountId: String?): List<FinancialEvent> {
        val uid = userId()
        return if (accountId != null)
            db.financialEventQueries.selectByAccount(accountId, uid).executeAsList().map { it.toModel() }
        else
            db.financialEventQueries.selectAll(uid).executeAsList().map { it.toModel() }
    }

    override suspend fun getEventsByDay(): List<EventDay> =
        getEvents()
            .groupBy { epochMillisToDate(it.timestamp) }
            .map { (date, items) ->
                EventDay(
                    date = date,
                    total = items.sumOf {
                        if (it.type == TransactionType.INCOME) it.amount else -it.amount
                    },
                    items = items,
                )
            }
            .sortedByDescending { it.date }

    override suspend fun voidEvent(id: String, reason: String?): VoidEvent {
        val now = Clock.System.now().toEpochMilliseconds()
        val voidId = "${now}_${id.take(8)}"
        db.transaction {
            db.voidEventQueries.insert(voidId, id, reason, now, null)
            val event = db.financialEventQueries.selectById(id, userId()).executeAsOneOrNull()
            if (event != null) {
                val acct = db.accountQueries.selectById(event.accountId).executeAsOneOrNull()
                if (acct != null) {
                    val delta = if (event.type == "INCOME") -event.amount else event.amount
                    db.accountQueries.updateBalance(acct.balance + delta, acct.id)
                }
            }
        }
        return VoidEvent(id = voidId, originalEventId = id, reason = reason, timestamp = now)
    }

    // ── Delegate everything else to remote ────────────────────────────────────

    override suspend fun getWallets(): List<Wallet> = remote.getWallets()
    override suspend fun getWallet(id: String): Wallet = remote.getWallet(id)
    override suspend fun getTransactions(walletId: String): List<Transaction> = remote.getTransactions(walletId)
    override suspend fun getTransactionsByDay(): List<TransactionDay> = remote.getTransactionsByDay()
    override suspend fun addTransaction(transaction: Transaction): Transaction = remote.addTransaction(transaction)
    override suspend fun getHoldings(): List<Holding> = remote.getHoldings()
    override suspend fun getCredits(): List<Credit> = remote.getCredits()
    override suspend fun getGoals(): List<Goal> = remote.getGoals()
    override suspend fun getSmsMessages(): List<SmsMessage> = remote.getSmsMessages()
    override suspend fun getSms(id: String): SmsMessage = remote.getSms(id)
    override suspend fun parseSms(id: String): ParsedSms = remote.parseSms(id)
    override suspend fun confirmSms(id: String) = remote.confirmSms(id)
    override suspend fun ignoreSms(id: String) = remote.ignoreSms(id)
    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary = remote.getFinanceSummary(scope)
    override suspend fun getBudgets(): List<Budget> = remote.getBudgets()
    override suspend fun createBudget(budget: Budget): Budget = remote.createBudget(budget)
    override suspend fun updateBudget(category: String, budget: Budget): Budget = remote.updateBudget(category, budget)
    override suspend fun deleteBudget(category: String) = remote.deleteBudget(category)
    override suspend fun getRecurringRules(): List<RecurringRule> = remote.getRecurringRules()
    override suspend fun chatAi(request: AiChatRequest): AiChatResponse = remote.chatAi(request)
    override suspend fun register(request: RegisterRequest): AuthResponse = remote.register(request)
    override suspend fun login(request: LoginRequest): AuthResponse = remote.login(request)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun com.jvillada.movi.Financial_event.toModel() = FinancialEvent(
        id = id, accountId = accountId,
        type = TransactionType.valueOf(type),
        amount = amount, category = category,
        description = description, merchant = merchant,
        timestamp = timestamp,
        source = EventSource.valueOf(source),
        rawPayload = rawPayload,
        reconciliationStatus = ReconciliationStatus.valueOf(reconciliationStatus),
        syncedAt = syncedAt,
    )
}

private fun epochMillisToDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date.toString()
