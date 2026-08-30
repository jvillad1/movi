package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AdjustCreditBalanceRequest
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CategoryPrefsRequest
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.MergeCategoryRequest
import com.jvillada.movi.shared.model.RenameCategoryRequest
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreateSubscriptionRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.TransferResult
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.PasswordResetRequest
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ReminderChannels
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.shared.model.RenameBudgetRequest
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.MarkOccurrenceRequest
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.UpdateEventCategoryRequest
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.UpdateEventTimestampRequest
import com.jvillada.movi.shared.model.ChangePasswordRequest
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.VoidEvent
import com.jvillada.movi.shared.model.RenameAccountRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class WalletRepositoryImpl(
    private val client: HttpClient,
    private val baseUrl: String,
) : WalletRepository {

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

    // Único call site que mira el status antes de deserializar: es el que tiene rechazos
    // legibles del server (400 fuera de rango, 404, 422 no-LOAN / no-COP) y son justo los que
    // el usuario necesita leer. Sin esto, `.body()` sobre el 400 fallaba deserializando y el
    // texto del server se perdía.
    override suspend fun registerPayrollDeduction(accountId: String): CreditSummary {
        val response = client.post("$baseUrl/api/credits/$accountId/payroll-deduction")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun adjustCreditBalance(accountId: String, targetBalance: Long): CreditSummary {
        val response = client.post("$baseUrl/api/credits/$accountId/balance-adjustment") {
            contentType(ContentType.Application.Json)
            setBody(AdjustCreditBalanceRequest(targetBalance))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun getCards(): List<CardSummary> =
        client.get("$baseUrl/api/cards").body()

    // Mismo idioma que adjustCreditBalance: estos dos son los que tienen rechazos legibles del
    // server (400 nombre/deuda/moneda, 404 ajena, 422 no-tarjeta) y el usuario necesita leerlos
    // en la hoja — `.body()` directo sobre un 4xx de texto perdía el mensaje deserializando.
    override suspend fun createCard(request: CreateCardRequest): CardSummary {
        val response = client.post("$baseUrl/api/cards") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun putCardTerms(terms: CardTerms): CardSummary {
        val response = client.put("$baseUrl/api/cards/${terms.accountId}") {
            contentType(ContentType.Application.Json)
            setBody(terms)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun deleteCardTerms(accountId: String) {
        client.delete("$baseUrl/api/cards/$accountId")
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

    // Mismo idioma que createCard: un nombre repetido da 409 con texto del server — se pierde
    // si se deserializa a ciegas sobre el 4xx.
    override suspend fun createSubscription(request: CreateSubscriptionRequest): Subscription {
        val response = client.post("$baseUrl/api/subscriptions") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun getGoals(): List<Goal> =
        client.get("$baseUrl/api/goals").body()

    // Mismo idioma que createCard/createSubscription: 404 (cuenta ajena) y 422 (cuenta de
    // deuda) traen su propio texto del server.
    override suspend fun createGoal(goal: Goal): Goal {
        val response = client.post("$baseUrl/api/goals") {
            contentType(ContentType.Application.Json)
            setBody(goal)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun updateGoal(id: String, goal: Goal): Goal {
        val response = client.put("$baseUrl/api/goals/$id") {
            contentType(ContentType.Application.Json)
            setBody(goal)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun deleteGoal(id: String) {
        client.delete("$baseUrl/api/goals/$id")
    }

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

    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary =
        client.get("$baseUrl/api/finance-summary?scope=${scope.name}").body()

    override suspend fun getDashboardSummary(scope: Scope): DashboardSummary =
        client.get("$baseUrl/api/dashboard/summary?scope=${scope.name}").body()

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

    // Mismo idioma que adjustCreditBalance/updateEventCategory: 404 (no existe) y 409 (el
    // nombre nuevo ya está en uso) traen su propio texto del server y se pierden si se
    // deserializa a ciegas.
    override suspend fun renameBudget(category: String, newCategory: String): Budget {
        val response = client.put("$baseUrl/api/budgets/$category/rename") {
            contentType(ContentType.Application.Json)
            setBody(RenameBudgetRequest(newCategory))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // ── Categorías (Ola 10) ───────────────────────────────────────────────────
    // Los nombres viajan SIEMPRE en el cuerpo, nunca en la ruta: una categoría es texto libre
    // del dueño («Salud & bienestar», «Otros ingresos») y meter eso en un path segment obliga a
    // codificar/decodificar bien en cuatro clientes distintos para nada.

    override suspend fun getCategories(): List<CategoryUsage> =
        client.get("$baseUrl/api/categories").body()

    // Mismo idioma que renameBudget: 409 (el nombre ya existe) y 422 (reservada / del catálogo)
    // traen su propio texto del server, y ese texto es LO QUE LA PANTALLA MUESTRA — deserializar
    // a ciegas lo perdería y dejaría al dueño con un error mudo.
    override suspend fun renameCategory(from: String, to: String): CategoryRewriteResult {
        val response = client.post("$baseUrl/api/categories/rename") {
            contentType(ContentType.Application.Json)
            setBody(RenameCategoryRequest(from = from, to = to))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun mergeCategory(from: String, into: String): CategoryRewriteResult {
        val response = client.post("$baseUrl/api/categories/merge") {
            contentType(ContentType.Application.Json)
            setBody(MergeCategoryRequest(from = from, into = into))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun setCategoryPrefs(name: String, hidden: Boolean, pinnedType: String?): CategoryUsage {
        val response = client.put("$baseUrl/api/categories/prefs") {
            contentType(ContentType.Application.Json)
            setBody(CategoryPrefsRequest(name = name, hidden = hidden, pinnedType = pinnedType))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
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

    override suspend fun getReminderChannels(): ReminderChannels =
        client.get("$baseUrl/api/reminders/channels").body()

    override suspend fun getOccurrenceStates(): List<OccurrenceState> =
        client.get("$baseUrl/api/payments/occurrences").body()

    // Mismo idioma que createCard: los rechazos de acá son legibles y el dueño los necesita leer
    // («ese movimiento está anulado», «ya está marcado como la ocurrencia de otro periodo»).
    // `.body()` a ciegas sobre un 4xx de texto plano pierde el mensaje deserializando.
    override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence {
        val response = client.post("$baseUrl/api/recurring-rules/$ruleId/occurrence") {
            contentType(ContentType.Application.Json)
            setBody(MarkOccurrenceRequest(period = period, eventId = eventId))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Se mira el status, igual que `markOccurrence`: sin esto un 404 o un 500 se leían como
    // éxito, la pantalla recargaba y el «Deshacer» seguía ahí — el dueño tocando un botón que no
    // hace nada y sin un solo mensaje que se lo diga.
    override suspend fun unmarkOccurrence(ruleId: String, period: String) {
        val response = client.delete("$baseUrl/api/recurring-rules/$ruleId/occurrence/$period")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

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

    // Mismo idioma que updateEventCategory/adjustCreditBalance: 404 (cuenta inexistente o de
    // otro usuario) trae su propio texto y se pierde si se deserializa a ciegas — acá además
    // no hay body que deserializar (204), así que sin esto un 404 pasaría desapercibido.
    override suspend fun deleteAccount(id: String) {
        val response = client.delete("$baseUrl/api/accounts/$id")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

    override suspend fun renameAccount(id: String, name: String): Account {
        val response = client.put("$baseUrl/api/accounts/$id/name") {
            contentType(ContentType.Application.Json)
            setBody(RenameAccountRequest(name))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

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

    // Mismo idioma que updateEventCategory/adjustCreditBalance: el server puede rechazar con
    // 422 (una validación de validateTransfer, con su texto en español ya listo para mostrar),
    // 404 (cuenta inexistente o de otro usuario) o 409 (traspaso repetido), y ese texto se
    // pierde si se deserializa a ciegas — que es justo el mensaje que la hoja necesita.
    override suspend fun createTransfer(request: CreateTransferRequest): TransferResult {
        val response = client.post("$baseUrl/api/transfers") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Mismo idioma que updateEventCategory: sin mirar el status, un 409 «Already voided» se
    // deserializaba a ciegas y explotaba como un error de parseo cualquiera — sin código, sin
    // texto. Eso importa acá más que en otras rutas: el `SyncEngine` no puede distinguir «no
    // llegó» de «ya estaba anulado», así que reintentaba esa fila cada 30 segundos para siempre.
    // Con el código a la vista, `syncVoids` sella la fila y deja de insistir.
    override suspend fun voidEvent(id: String, reason: String?): VoidEvent {
        val url = if (reason != null) "$baseUrl/api/events/$id/void?reason=$reason"
                  else "$baseUrl/api/events/$id/void"
        val response = client.post(url)
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Mismo idioma que adjustCreditBalance: el server puede rechazar con 404 (otro usuario) o
    // 400 (categoría vacía o demasiado larga) y ese texto se pierde si se deserializa a ciegas.
    override suspend fun updateEventCategory(id: String, category: String): FinancialEvent {
        val response = client.put("$baseUrl/api/events/$id/category") {
            contentType(ContentType.Application.Json)
            setBody(UpdateEventCategoryRequest(category))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // Mismo idioma que updateEventCategory: el server puede rechazar con 404 (evento
    // inexistente, de otro usuario o anulado) o 422 (fecha futura) y ese texto es lo único que
    // le explica al dueño por qué no se guardó — se pierde si se deserializa a ciegas.
    override suspend fun updateEventTimestamp(id: String, timestamp: Long): FinancialEvent {
        val response = client.put("$baseUrl/api/events/$id/timestamp") {
            contentType(ContentType.Application.Json)
            setBody(UpdateEventTimestampRequest(timestamp))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    // 204 = «no hay marca», que es la respuesta normal y no un error. Cualquier otro fallo
    // (sin red, 500) también cae en null: este dato solo alimenta un aviso, y un aviso que no
    // se pudo cargar no puede impedir que el dueño corrija una fecha.
    override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? {
        val response = runCatching { client.get("$baseUrl/api/events/$id/occurrence") }.getOrNull()
            ?: return null
        if (response.status != HttpStatusCode.OK) return null
        return runCatching { response.body<EventOccurrenceMark>() }.getOrNull()
    }

    override suspend fun getCardPaymentCandidates(): List<FinancialEvent> =
        client.get("$baseUrl/api/events/card-payment-candidates").body()

    // Mismo idioma que updateEventCategory: 404 (evento inexistente o de otro usuario) trae su
    // propio texto y se pierde si se deserializa a ciegas.
    override suspend fun dismissCardPaymentCandidate(id: String) {
        val response = client.post("$baseUrl/api/events/$id/not-card-payment")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }

    // ── Por qué acá NO alcanza con `.body()` ──────────────────────────────────────────────
    //
    // El cliente no tiene `expectSuccess`, así que un 401 no lanza nada por sí solo: lo que
    // explotaba era `.body()`, intentando deserializar `AuthResponse` de un cuerpo que el
    // servidor mandó como `text/plain` ("Invalid credentials"). El error resultante no dice
    // "401" en ninguna parte — y es EXACTAMENTE el mismo error que sale de un 500 cuyo cuerpo
    // es el HTML del proxy. Con la red caída sale un tercer error distinto, pero el `onFailure`
    // de la pantalla los trataba a los tres igual y acusaba a la contraseña.
    //
    // Mismo idioma que `dismissCardPaymentCandidate` y compañía: si no fue 2xx, se lanza
    // [ApiException] con el código, que es el dato que la pantalla necesita para distinguir
    // "te rechazaron las credenciales" de "no se pudo hablar con el servidor".
    override suspend fun register(request: RegisterRequest): AuthResponse =
        client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.bodyOrApiException()

    override suspend fun login(request: LoginRequest): AuthResponse =
        client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.bodyOrApiException()

    private suspend fun HttpResponse.bodyOrApiException(): AuthResponse {
        if (!status.isSuccess()) {
            throw ApiException(status.value, runCatching { bodyAsText() }.getOrNull())
        }
        return body()
    }

    // No se usa .body(): la respuesta puede ser 202 o 503 y lo que la UI necesita es el código.
    override suspend fun requestPasswordReset(request: PasswordResetRequest): Int =
        client.post("$baseUrl/api/auth/password-reset/request") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.status.value

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

    override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? {
        val response = client.get("$baseUrl/api/screens/$slug") {
            cachedVersion?.let { header("If-None-Match", it.toString()) }
        }
        // No safeCall en este repo: chequear 304 ANTES de .body() (lección NeoVita).
        if (response.status == HttpStatusCode.NotModified) return null
        return response.body()
    }

    override suspend fun putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition =
        client.put("$baseUrl/api/screens/$slug") {
            contentType(ContentType.Application.Json)
            setBody(ScreenDefinition(slug = slug, version = 0, sections = sections))
        }.body()

    override suspend fun restoreScreen(slug: String): ScreenDefinition =
        client.post("$baseUrl/api/screens/$slug/restore").body()

    override suspend fun isScreenAdmin(): Boolean =
        runCatching {
            client.get("$baseUrl/api/screens/admin/status").body<Map<String, Boolean>>()["isAdmin"] == true
        }.getOrDefault(false)

    override suspend fun getUserProfile(): UserProfile =
        client.get("$baseUrl/api/users/me").body()

    override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile {
        val response = client.put("$baseUrl/api/users/me") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
        return response.body()
    }

    override suspend fun changePassword(request: ChangePasswordRequest) {
        val response = client.put("$baseUrl/api/users/me/password") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
        }
    }
}
