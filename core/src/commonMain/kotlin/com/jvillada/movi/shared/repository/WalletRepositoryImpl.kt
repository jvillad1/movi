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
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.RegisterRequest
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

class WalletRepositoryImpl(
    private val client: HttpClient,
    private val baseUrl: String,
) : WalletRepository {

    override suspend fun getHoldings(): List<Holding> =
        client.get("$baseUrl/api/holdings").body()

    override suspend fun getCredits(): List<CreditSummary> =
        client.get("$baseUrl/api/credits").body()

    override suspend fun createCredit(request: CreateCreditRequest): CreditSummary =
        client.post("$baseUrl/api/credits") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary =
        client.put("$baseUrl/api/credits/${terms.accountId}") {
            contentType(ContentType.Application.Json)
            setBody(terms)
        }.body()

    override suspend fun deleteCreditTerms(accountId: String) {
        client.delete("$baseUrl/api/credits/$accountId")
    }

    override suspend fun getSubscriptions(): SubscriptionsResult =
        client.get("$baseUrl/api/subscriptions").body()

    override suspend fun detectSubscriptions(): SubscriptionsResult =
        client.post("$baseUrl/api/subscriptions/detect").body()

    override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription =
        client.put("$baseUrl/api/subscriptions/$id") {
            contentType(ContentType.Application.Json)
            setBody(subscription)
        }.body()

    override suspend fun deleteSubscription(id: String) {
        client.delete("$baseUrl/api/subscriptions/$id")
    }

    override suspend fun getGoals(): List<Goal> =
        client.get("$baseUrl/api/goals").body()

    override suspend fun getSmsMessages(): List<SmsMessage> =
        client.get("$baseUrl/api/sms").body()

    override suspend fun getSms(id: String): SmsMessage =
        client.get("$baseUrl/api/sms/$id").body()

    override suspend fun parseSms(id: String): ParsedSms =
        client.get("$baseUrl/api/sms/$id/parse").body()

    override suspend fun confirmSms(id: String) {
        client.post("$baseUrl/api/sms/$id/confirm")
    }

    override suspend fun ignoreSms(id: String) {
        client.post("$baseUrl/api/sms/$id/ignore")
    }

    override suspend fun syncSms(messages: List<SmsMessage>) {
        client.post("$baseUrl/api/sms/sync") {
            contentType(ContentType.Application.Json)
            setBody(messages)
        }
    }

    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
        client.get("$baseUrl/api/finance-summary?scope=${scope.name}").body()

    override suspend fun getBudgets(): List<Budget> =
        client.get("$baseUrl/api/budgets").body()

    override suspend fun createBudget(budget: Budget): Budget =
        client.post("$baseUrl/api/budgets") {
            contentType(ContentType.Application.Json)
            setBody(budget)
        }.body()

    override suspend fun updateBudget(category: String, budget: Budget): Budget =
        client.put("$baseUrl/api/budgets/$category") {
            contentType(ContentType.Application.Json)
            setBody(budget)
        }.body()

    override suspend fun deleteBudget(category: String) {
        client.delete("$baseUrl/api/budgets/$category")
    }

    override suspend fun getRecurringRules(): List<RecurringRule> =
        client.get("$baseUrl/api/recurring-rules").body()

    override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule =
        client.post("$baseUrl/api/recurring-rules") {
            contentType(ContentType.Application.Json)
            setBody(rule)
        }.body()

    override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule =
        client.put("$baseUrl/api/recurring-rules/$id") {
            contentType(ContentType.Application.Json)
            setBody(rule)
        }.body()

    override suspend fun deleteRecurringRule(id: String) {
        client.delete("$baseUrl/api/recurring-rules/$id")
    }

    override suspend fun getUpcomingPayments(): List<UpcomingPayment> =
        client.get("$baseUrl/api/payments/upcoming").body()

    override suspend fun chatAi(request: AiChatRequest): AiChatResponse =
        client.post("$baseUrl/api/ai/chat") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun getAccounts(): List<Account> =
        client.get("$baseUrl/api/accounts").body()

    override suspend fun getAccount(id: String): Account =
        client.get("$baseUrl/api/accounts/$id").body()

    override suspend fun createAccount(account: Account): Account =
        client.post("$baseUrl/api/accounts") {
            contentType(ContentType.Application.Json)
            setBody(account)
        }.body()

    override suspend fun postEvent(event: FinancialEvent): FinancialEvent =
        client.post("$baseUrl/api/events") {
            contentType(ContentType.Application.Json)
            setBody(event)
        }.body()

    override suspend fun getEvents(accountId: String?): List<FinancialEvent> {
        val url = if (accountId != null) "$baseUrl/api/events?accountId=$accountId"
                  else "$baseUrl/api/events"
        return client.get(url).body()
    }

    override suspend fun getEventsByDay(): List<EventDay> =
        client.get("$baseUrl/api/events/by-day").body()

    override suspend fun voidEvent(id: String, reason: String?): VoidEvent {
        val url = if (reason != null) "$baseUrl/api/events/$id/void?reason=$reason"
                  else "$baseUrl/api/events/$id/void"
        return client.post(url).body()
    }

    override suspend fun register(request: RegisterRequest): AuthResponse =
        client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun login(request: LoginRequest): AuthResponse =
        client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult =
        client.post("$baseUrl/api/statements/upload") {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    append(HttpHeaders.ContentType, mimeType)
                })
            }))
        }.body()

    override suspend fun importStatement(decision: ImportDecision) {
        client.post("$baseUrl/api/statements/import") {
            contentType(ContentType.Application.Json)
            setBody(decision)
        }
    }

    override suspend fun getStatementImports(): List<StatementImport> =
        client.get("$baseUrl/api/statements/imports").body()

    override suspend fun getStatementImportDetail(id: String): StatementImportDetail =
        client.get("$baseUrl/api/statements/imports/$id").body()
}
