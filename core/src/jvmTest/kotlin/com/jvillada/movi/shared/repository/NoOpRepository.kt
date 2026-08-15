package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.*
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.StatementParseResult

class NoOpRepository(
    /**
     * Ids que el "server" ya conoce. Cualquier otro id dispara el mismo 404 que daría el server
     * real para un evento que no existe (o que es de otro usuario) — ver [updateEventCategory].
     */
    private val knownEventIds: Set<String> = emptySet(),
) : WalletRepository {
    /** Ids que pasaron por [dismissCardPaymentCandidate] — lo que un test de delegación verifica. */
    val dismissedCandidateIds = mutableListOf<String>()

    override suspend fun getHoldings() = emptyList<Holding>()
    override suspend fun getCredits() = emptyList<CreditSummary>()
    override suspend fun createCredit(request: CreateCreditRequest) = putCreditTerms(request.terms)
    override suspend fun putCreditTerms(terms: CreditTerms) = CreditSummary(
        account = Account(id = terms.accountId, name = "", type = AccountType.LOAN, balance = 0),
        terms = terms,
        paidPct = null,
    )
    override suspend fun deleteCreditTerms(accountId: String) {}
    /**
     * Imita al server: devuelve la cuenta con la deuda ya en el objetivo **y** el evento que
     * registró para llegar ahí. Ese `adjustmentEvent` es lo que [LocalRepository] tiene que
     * espejar en la DB local, así que sin él el stub no ejercitaría nada.
     */
    override suspend fun adjustCreditBalance(accountId: String, targetBalance: Long) = CreditSummary(
        account = Account(id = accountId, name = "Crédito", type = AccountType.LOAN, balance = targetBalance),
        terms = null,
        paidPct = null,
        adjustmentEvent = FinancialEvent(
            id                   = "ev-ajuste-$accountId",
            accountId            = accountId,
            type                 = TransactionType.INCOME,
            amount               = 60_000_000L,
            category             = "Ajuste de saldo",
            description          = "Ajuste al saldo del banco",
            timestamp            = 1_700_000_000_000L,
            source               = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        ),
    )
    override suspend fun getSubscriptions() = SubscriptionsResult(emptyList(), 0)
    override suspend fun detectSubscriptions() = SubscriptionsResult(emptyList(), 0)
    override suspend fun updateSubscription(id: String, subscription: Subscription) = subscription
    override suspend fun deleteSubscription(id: String) {}
    override suspend fun getGoals() = emptyList<Goal>()
    override suspend fun getSmsMessages() = emptyList<SmsMessage>()
    override suspend fun getSms(id: String) = error("stub")
    override suspend fun parseSms(id: String) = error("stub")
    override suspend fun confirmSms(id: String) {}
    override suspend fun ignoreSms(id: String) {}
    override suspend fun syncSms(messages: List<SmsMessage>) {}
    override suspend fun getFinanceSummary(scope: Scope) = error("stub")
    override suspend fun getBudgets() = emptyList<Budget>()
    override suspend fun createBudget(budget: Budget) = budget
    override suspend fun updateBudget(category: String, budget: Budget) = budget
    override suspend fun deleteBudget(category: String) {}
    override suspend fun getRecurringRules() = emptyList<RecurringRule>()
    override suspend fun createRecurringRule(rule: RecurringRule) = rule
    override suspend fun updateRecurringRule(id: String, rule: RecurringRule) = rule
    override suspend fun deleteRecurringRule(id: String) {}
    override suspend fun getUpcomingPayments() = emptyList<com.jvillada.movi.shared.model.UpcomingPayment>()
    override suspend fun chatAi(request: AiChatRequest) = error("stub")
    override suspend fun getAccounts() = emptyList<Account>()
    override suspend fun getAccount(id: String) = error("stub")
    override suspend fun createAccount(account: Account) = account
    override suspend fun postEvent(event: FinancialEvent) = event
    override suspend fun getEvents(accountId: String?) = emptyList<FinancialEvent>()
    override suspend fun getEventsByDay() = emptyList<EventDay>()
    override suspend fun voidEvent(id: String, reason: String?) = error("stub")
    /**
     * Imita al server: 404 para un evento que no está en [knownEventIds] — igual que el
     * `PUT /api/events/{id}/category` real cuando el evento no existe o es de otro usuario —, y
     * para uno que sí conoce, echoa el mismo [id] y la [category] que le mandaron, como haría el
     * update real. [LocalRepository] espeja `updated.id`/`updated.category` (no los parámetros
     * crudos), así que sin este echo el test del espejo local apuntaría a una fila equivocada.
     *
     * Antes este stub hacía echo incondicional sin importar el id, así que el test del espejo
     * local pasaba aunque `LocalRepository` llamara al server para un evento todavía sin
     * sincronizar: el 404 real que daría el server ahí nunca se ejercitaba.
     */
    override suspend fun updateEventCategory(id: String, category: String): FinancialEvent {
        if (id !in knownEventIds) throw ApiException(404)
        return FinancialEvent(
            id = id,
            accountId = "acc-stub",
            type = TransactionType.EXPENSE,
            amount = 50_000L,
            category = category,
            description = "stub",
            timestamp = 1_700_000_000_000L,
            source = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        )
    }
    override suspend fun getCardPaymentCandidates() = emptyList<FinancialEvent>()
    override suspend fun dismissCardPaymentCandidate(id: String) {
        dismissedCandidateIds += id
    }
    override suspend fun register(request: RegisterRequest) = error("stub")
    override suspend fun login(request: LoginRequest) = error("stub")
    override suspend fun requestPasswordReset(request: PasswordResetRequest) = 202
    override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String) =
        StatementParseResult("", "", "", emptyList(), emptyList())
    override suspend fun importStatement(decision: ImportDecision) {}
    override suspend fun getStatementImports() = emptyList<StatementImport>()
    override suspend fun getStatementImportDetail(id: String) =
        StatementImportDetail(StatementImport("", "", "", "", 0L, 0, 0), emptyList())
    override suspend fun getScreen(slug: String, cachedVersion: Int?) = null
    // La versión del body se ignora — la asigna el server.
    override suspend fun putScreen(slug: String, sections: List<ScreenSection>) =
        ScreenDefinition(slug = slug, version = 1, sections = sections)
    override suspend fun restoreScreen(slug: String) =
        ScreenDefinition(slug = slug, version = 1, sections = emptyList())
    override suspend fun isScreenAdmin() = false
}
