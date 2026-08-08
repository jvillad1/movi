package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.PasswordResetRequest
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.VoidEvent

interface WalletRepository {
    suspend fun getHoldings(): List<Holding>
    suspend fun getCredits(): List<CreditSummary>
    suspend fun createCredit(request: CreateCreditRequest): CreditSummary
    suspend fun putCreditTerms(terms: CreditTerms): CreditSummary
    suspend fun deleteCreditTerms(accountId: String)
    /** Deja la deuda del crédito en [targetBalance] registrando el movimiento de ajuste server-side. */
    suspend fun adjustCreditBalance(accountId: String, targetBalance: Long): CreditSummary
    suspend fun getSubscriptions(): SubscriptionsResult
    suspend fun detectSubscriptions(): SubscriptionsResult
    suspend fun updateSubscription(id: String, subscription: Subscription): Subscription
    suspend fun deleteSubscription(id: String)
    suspend fun getGoals(): List<Goal>
    suspend fun getSmsMessages(): List<SmsMessage>
    suspend fun getSms(id: String): SmsMessage
    suspend fun parseSms(id: String): ParsedSms
    suspend fun confirmSms(id: String)
    suspend fun ignoreSms(id: String)
    suspend fun syncSms(messages: List<SmsMessage>)
    suspend fun getFinanceSummary(scope: Scope): FinanceSummary
    suspend fun getBudgets(): List<Budget>
    suspend fun createBudget(budget: Budget): Budget
    suspend fun updateBudget(category: String, budget: Budget): Budget
    suspend fun deleteBudget(category: String)
    suspend fun getRecurringRules(): List<RecurringRule>
    suspend fun createRecurringRule(rule: RecurringRule): RecurringRule
    suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule
    suspend fun deleteRecurringRule(id: String)
    suspend fun getUpcomingPayments(): List<UpcomingPayment>
    suspend fun chatAi(request: AiChatRequest): AiChatResponse
    suspend fun getAccounts(): List<Account>
    suspend fun getAccount(id: String): Account
    suspend fun createAccount(account: Account): Account
    suspend fun postEvent(event: FinancialEvent): FinancialEvent
    suspend fun getEvents(accountId: String? = null): List<FinancialEvent>
    suspend fun getEventsByDay(): List<EventDay>
    suspend fun voidEvent(id: String, reason: String? = null): VoidEvent
    suspend fun register(request: RegisterRequest): AuthResponse
    suspend fun login(request: LoginRequest): AuthResponse

    /**
     * Pide un enlace de recuperación por correo. Devuelve el CÓDIGO HTTP crudo en vez de un
     * cuerpo tipado a propósito: el servidor responde 202 idéntico exista o no el correo
     * (anti-enumeración) y 503 cuando el envío de correo no está configurado en el servidor,
     * y la UI necesita distinguir esos dos casos para no prometer un correo que nunca llega.
     */
    suspend fun requestPasswordReset(request: PasswordResetRequest): Int
    suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult
    suspend fun importStatement(decision: ImportDecision)
    suspend fun getStatementImports(): List<StatementImport>
    suspend fun getStatementImportDetail(id: String): StatementImportDetail
    suspend fun getScreen(slug: String, cachedVersion: Int? = null): ScreenDefinition?
    suspend fun putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition
    suspend fun restoreScreen(slug: String): ScreenDefinition
    suspend fun isScreenAdmin(): Boolean
}
