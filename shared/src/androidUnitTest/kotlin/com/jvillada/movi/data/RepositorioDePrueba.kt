package com.jvillada.movi.data

import com.jvillada.movi.shared.model.*
import com.jvillada.movi.shared.repository.WalletRepository

/**
 * # Un [WalletRepository] que no contesta nada, para poder montar una pantalla de verdad
 *
 * Las pantallas de Movi leen su repositorio de [Repositories], así que sin una costura no había
 * forma de montar ninguna **con datos**: abajo hay un cliente HTTP apuntando a producción y una
 * base de SQLDelight. Por eso `HojaAgregarGeometriaTest` mide la hoja de «Agregar» con la lista de
 * cuentas siempre vacía, y por eso el cableado del selector de cuentas de la Ola 15 quedó
 * sostenido solo por el compilador.
 *
 * Cada método explota con el nombre del que se llamó. Eso es a propósito y es la mitad del valor:
 * una prueba que monta una pantalla y **no** sabía que esa pantalla iba a pedir el resumen del
 * Inicio se entera acá, con el nombre, en vez de quedarse en verde midiendo una pantalla a medio
 * cargar. Una prueba abre solo las puertas que necesita, sobrescribiéndolas.
 *
 * **Generado del `interface WalletRepository`, no escrito a mano.** Si mañana aparece un método
 * más, esto no compila hasta que alguien lo agregue — que es exactamente lo que queremos de un
 * doble de prueba.
 */
open class RepositorioDePrueba : WalletRepository {

    private fun noUsado(que: String): Nothing =
        error("Esta prueba no esperaba que la pantalla llamara a $que(). Sobrescribilo si lo necesita.")

    override suspend fun getCredits(): List<CreditSummary> = noUsado("getCredits")
    override suspend fun createCredit(request: CreateCreditRequest): CreditSummary = noUsado("createCredit")
    override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary = noUsado("putCreditTerms")
    override suspend fun deleteCreditTerms(accountId: String) = noUsado("deleteCreditTerms")
    override suspend fun registerPayrollDeduction(accountId: String): CreditSummary = noUsado("registerPayrollDeduction")
    override suspend fun adjustCreditBalance(accountId: String, targetBalance: Long): CreditSummary = noUsado("adjustCreditBalance")
    override suspend fun getCards(): List<CardSummary> = noUsado("getCards")
    override suspend fun createCard(request: CreateCardRequest): CardSummary = noUsado("createCard")
    override suspend fun putCardTerms(terms: CardTerms): CardSummary = noUsado("putCardTerms")
    override suspend fun deleteCardTerms(accountId: String) = noUsado("deleteCardTerms")
    override suspend fun getSubscriptions(): SubscriptionsResult = noUsado("getSubscriptions")
    override suspend fun detectSubscriptions(): SubscriptionsResult = noUsado("detectSubscriptions")
    override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription = noUsado("updateSubscription")
    override suspend fun deleteSubscription(id: String) = noUsado("deleteSubscription")
    override suspend fun createSubscription(request: CreateSubscriptionRequest): Subscription = noUsado("createSubscription")
    override suspend fun getGoals(): List<Goal> = noUsado("getGoals")
    override suspend fun createGoal(goal: Goal): Goal = noUsado("createGoal")
    override suspend fun updateGoal(id: String, goal: Goal): Goal = noUsado("updateGoal")
    override suspend fun deleteGoal(id: String) = noUsado("deleteGoal")
    override suspend fun getSmsMessages(): List<SmsMessage> = noUsado("getSmsMessages")
    override suspend fun getSms(id: String): SmsMessage = noUsado("getSms")
    override suspend fun parseSms(id: String): ParsedSms = noUsado("parseSms")
    override suspend fun confirmSms(id: String) = noUsado("confirmSms")
    override suspend fun ignoreSms(id: String) = noUsado("ignoreSms")
    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary = noUsado("getFinanceSummary")
    override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = noUsado("getDashboardSummary")
    override suspend fun getBudgets(): List<Budget> = noUsado("getBudgets")
    override suspend fun createBudget(budget: Budget): Budget = noUsado("createBudget")
    override suspend fun updateBudget(category: String, budget: Budget): Budget = noUsado("updateBudget")
    override suspend fun deleteBudget(category: String) = noUsado("deleteBudget")
    override suspend fun renameBudget(category: String, newCategory: String): Budget = noUsado("renameBudget")
    override suspend fun getCategories(): List<CategoryUsage> = noUsado("getCategories")
    override suspend fun renameCategory(from: String, to: String): CategoryRewriteResult = noUsado("renameCategory")
    override suspend fun mergeCategory(from: String, into: String): CategoryRewriteResult = noUsado("mergeCategory")
    override suspend fun setCategoryPrefs(name: String, hidden: Boolean, pinnedType: String?): CategoryUsage = noUsado("setCategoryPrefs")
    override suspend fun getRecurringRules(): List<RecurringRule> = noUsado("getRecurringRules")
    override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule = noUsado("createRecurringRule")
    override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule = noUsado("updateRecurringRule")
    override suspend fun deleteRecurringRule(id: String) = noUsado("deleteRecurringRule")
    override suspend fun getUpcomingPayments(): List<UpcomingPayment> = noUsado("getUpcomingPayments")
    override suspend fun getReminderChannels(): ReminderChannels = noUsado("getReminderChannels")
    override suspend fun getOccurrenceStates(): List<OccurrenceState> = noUsado("getOccurrenceStates")
    override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence = noUsado("markOccurrence")
    override suspend fun unmarkOccurrence(ruleId: String, period: String) = noUsado("unmarkOccurrence")
    override suspend fun chatAi(request: AiChatRequest): AiChatResponse = noUsado("chatAi")
    override suspend fun getAccounts(): List<Account> = noUsado("getAccounts")
    override suspend fun getAccount(id: String): Account = noUsado("getAccount")
    override suspend fun createAccount(account: Account): Account = noUsado("createAccount")
    override suspend fun deleteAccount(id: String) = noUsado("deleteAccount")
    override suspend fun postEvent(event: FinancialEvent): FinancialEvent = noUsado("postEvent")
    override suspend fun getEvents(accountId: String?): List<FinancialEvent> = noUsado("getEvents")
    override suspend fun getEventsByDay(): List<EventDay> = noUsado("getEventsByDay")
    override suspend fun voidEvent(id: String, reason: String?): VoidEvent = noUsado("voidEvent")
    override suspend fun createTransfer(request: CreateTransferRequest): TransferResult = noUsado("createTransfer")
    override suspend fun payInstallment(request: CreatePagoDeCuotaRequest): PagoDeCuotaResult = noUsado("payInstallment")
    override suspend fun updateEventCategory(id: String, category: String): FinancialEvent = noUsado("updateEventCategory")
    override suspend fun updateEventTimestamp(id: String, timestamp: Long): FinancialEvent = noUsado("updateEventTimestamp")
    override suspend fun updateEvent(id: String, cambios: EdicionDeMovimiento): FinancialEvent = noUsado("updateEvent")
    override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? = noUsado("getEventOccurrenceMark")
    override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = noUsado("getCardPaymentCandidates")
    override suspend fun dismissCardPaymentCandidate(id: String) = noUsado("dismissCardPaymentCandidate")
    override suspend fun register(request: RegisterRequest): AuthResponse = noUsado("register")
    override suspend fun login(request: LoginRequest): AuthResponse = noUsado("login")
    override suspend fun renameAccount(id: String, name: String): Account = noUsado("renameAccount")
    override suspend fun updateAccountCondition(id: String, condicionadaA: String?): Account = noUsado("updateAccountCondition")
    override suspend fun getUserProfile(): UserProfile = noUsado("getUserProfile")
    override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile = noUsado("updateUserProfile")
    override suspend fun changePassword(request: ChangePasswordRequest) = noUsado("changePassword")
    override suspend fun requestPasswordReset(request: PasswordResetRequest): Int = noUsado("requestPasswordReset")
    override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult = noUsado("uploadStatement")
    override suspend fun getDocuments(): List<Documento> = noUsado("getDocuments")
    override suspend fun uploadDocument(fileName: String, bytes: ByteArray, mimeType: String, tipo: TipoDeDocumento, accountId: String?, periodo: String?, notas: String?): Documento = noUsado("uploadDocument")
    override suspend fun getDocumentLink(id: String): EnlaceDeDescarga = noUsado("getDocumentLink")
    override suspend fun updateDocument(id: String, cambios: EdicionDeDocumento): Documento = noUsado("updateDocument")
    override suspend fun deleteDocument(id: String) = noUsado("deleteDocument")
    override suspend fun importStatement(decision: ImportDecision) = noUsado("importStatement")
    override suspend fun getStatementImports(): List<StatementImport> = noUsado("getStatementImports")
    override suspend fun getStatementImportDetail(id: String): StatementImportDetail = noUsado("getStatementImportDetail")
    override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? = noUsado("getScreen")
    override suspend fun putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition = noUsado("putScreen")
    override suspend fun restoreScreen(slug: String): ScreenDefinition = noUsado("restoreScreen")
    override suspend fun isScreenAdmin(): Boolean = noUsado("isScreenAdmin")
}
