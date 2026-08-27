package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.*
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.StatementParseResult

// `open`: SyncEngineTest y FailingCreateAccountRepository extienden este stub para sobrescribir
// solo un puñado de métodos (createAccount/postEvent) sin tener que reimplementar los ~40
// restantes de WalletRepository.
open class NoOpRepository(
    /**
     * Ids que el "server" ya conoce. Cualquier otro id dispara el mismo 404 que daría el server
     * real para un evento que no existe (o que es de otro usuario) — ver [updateEventCategory].
     */
    private val knownEventIds: Set<String> = emptySet(),
) : WalletRepository {
    /** Ids que pasaron por [dismissCardPaymentCandidate] — lo que un test de delegación verifica. */
    val dismissedCandidateIds = mutableListOf<String>()

    /**
     * Las cuentas que este "server" tiene, y que por lo tanto devuelve en [getAccounts].
     *
     * Arranca vacío (como antes), pero **todo lo que crea una cuenta la agrega acá**:
     * `createAccount`, `createCredit` y `createCard`. Un stub que aceptara crear un crédito y
     * después jurara que no tiene ninguna cuenta no imita a ningún server posible — y esa
     * inconsistencia importa desde que `LocalRepository.getAccounts` **deja de mostrar** la
     * cuenta sellada que el server ya no devuelve (la regla anti-fantasma). Con el stub
     * contradictorio, los tests del espejo de crédito/tarjeta fallaban por un fantasma que en la
     * realidad no existe: el server que acaba de crear la cuenta sí la devuelve en el GET
     * siguiente.
     */
    val cuentasDelServer = mutableListOf<Account>()

    /**
     * Imita `POST /api/transfers`: construye las dos patas con la MISMA función que usa el
     * server ([transferLegsFor]), sobre cuentas de nombre igual a su id — el stub no tiene un
     * catálogo de cuentas y lo que se ejercita del otro lado es el espejo local, no el texto.
     */
    override suspend fun createTransfer(request: CreateTransferRequest): TransferResult {
        fun stub(id: String) = Account(id = id, name = id, type = AccountType.SAVINGS, balance = 0L)
        val (from, to) = transferLegsFor(request, stub(request.fromAccountId), stub(request.toAccountId))
        return TransferResult(from = from, to = to)
    }

    override suspend fun getCredits() = emptyList<CreditSummary>()
    override suspend fun createCredit(request: CreateCreditRequest) =
        putCreditTerms(request.terms).also { cuentasDelServer += it.account }
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
    override suspend fun getCards() = emptyList<CardSummary>()
    override suspend fun createCard(request: CreateCardRequest) = CardSummary(
        account = Account(id = "acc-card-stub", name = request.name, type = AccountType.CREDIT_CARD, balance = request.initialDebt, currency = request.currency),
        terms = request.terms.copy(accountId = "acc-card-stub"),
        available = request.terms.creditLimit?.let { it - request.initialDebt },
    ).also { cuentasDelServer += it.account }
    override suspend fun putCardTerms(terms: CardTerms) = CardSummary(
        account = Account(id = terms.accountId, name = "", type = AccountType.CREDIT_CARD, balance = 0),
        terms = terms,
        available = terms.creditLimit,
    )
    override suspend fun deleteCardTerms(accountId: String) {}
    override suspend fun getSubscriptions() = SubscriptionsResult(emptyList(), 0)
    override suspend fun detectSubscriptions() = SubscriptionsResult(emptyList(), 0)
    override suspend fun updateSubscription(id: String, subscription: Subscription) = subscription
    override suspend fun deleteSubscription(id: String) {}
    override suspend fun createSubscription(request: CreateSubscriptionRequest) = Subscription(
        id = "sub-stub",
        merchantKey = "manual_stub",
        displayName = request.displayName,
        amount = request.amount,
        currency = request.currency,
        dayOfMonth = request.dayOfMonth,
        status = SubStatus.CONFIRMED,
        confidence = SubConfidence.HIGH,
        firstSeen = 0,
        lastSeen = 0,
        occurrences = 0,
    )
    override suspend fun getGoals() = emptyList<Goal>()
    override suspend fun createGoal(goal: Goal) = goal.copy(id = goal.id.ifBlank { "goal-stub" })
    override suspend fun updateGoal(id: String, goal: Goal) = goal.copy(id = id)
    override suspend fun deleteGoal(id: String) {}
    override suspend fun getSmsMessages() = emptyList<SmsMessage>()
    override suspend fun getSms(id: String) = error("stub")
    override suspend fun parseSms(id: String) = error("stub")
    override suspend fun confirmSms(id: String) {}
    override suspend fun ignoreSms(id: String) {}
    override suspend fun getFinanceSummary(scope: Scope) = error("stub")
    override suspend fun getDashboardSummary(scope: Scope) = error("stub")
    override suspend fun getBudgets() = emptyList<Budget>()
    override suspend fun createBudget(budget: Budget) = budget
    override suspend fun updateBudget(category: String, budget: Budget) = budget
    override suspend fun deleteBudget(category: String) {}
    override suspend fun renameBudget(category: String, newCategory: String) = Budget(newCategory, 0)
    override suspend fun getCategories() = emptyList<com.jvillada.movi.shared.model.CategoryUsage>()
    override suspend fun renameCategory(from: String, to: String) =
        com.jvillada.movi.shared.model.CategoryRewriteResult(name = to)
    override suspend fun mergeCategory(from: String, into: String) =
        com.jvillada.movi.shared.model.CategoryRewriteResult(name = into)
    override suspend fun setCategoryPrefs(name: String, hidden: Boolean, pinnedType: String?) =
        com.jvillada.movi.shared.model.CategoryUsage(name = name, hidden = hidden, pinnedType = pinnedType)
    override suspend fun getRecurringRules() = emptyList<RecurringRule>()
    override suspend fun createRecurringRule(rule: RecurringRule) = rule
    override suspend fun updateRecurringRule(id: String, rule: RecurringRule) = rule
    override suspend fun deleteRecurringRule(id: String) {}
    override suspend fun getUpcomingPayments() = emptyList<com.jvillada.movi.shared.model.UpcomingPayment>()
    override suspend fun getReminderChannels() = com.jvillada.movi.shared.model.ReminderChannels()
    override suspend fun getOccurrenceStates() = emptyList<com.jvillada.movi.shared.model.OccurrenceState>()
    override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?) =
        com.jvillada.movi.shared.model.RecurringOccurrence(ruleId, period, eventId)
    override suspend fun unmarkOccurrence(ruleId: String, period: String) {}
    override suspend fun chatAi(request: AiChatRequest) = error("stub")
    override suspend fun getAccounts(): List<Account> = cuentasDelServer.toList()
    // Tipo de retorno explícito (y no el `Nothing` que infiere `error(...)`): así una subclase
    // puede sobrescribirlo para devolver una cuenta de verdad — ver [ServerAccountsRepository].
    override suspend fun getAccount(id: String): Account = error("stub")
    override suspend fun createAccount(account: Account) = account.also { cuentasDelServer += it }
    override suspend fun deleteAccount(id: String) {}
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
    override suspend fun getUserProfile() = UserProfile(id = "usr-stub", email = "stub@movi.test", name = "Stub", avatarColor = AvatarPalette.DEFAULT)
    override suspend fun updateUserProfile(request: UpdateProfileRequest) = UserProfile(
        id = "usr-stub",
        email = "stub@movi.test",
        name = request.name ?: "Stub",
        avatarColor = request.avatarColor ?: AvatarPalette.DEFAULT,
    )
    override suspend fun changePassword(request: ChangePasswordRequest) {}
}
