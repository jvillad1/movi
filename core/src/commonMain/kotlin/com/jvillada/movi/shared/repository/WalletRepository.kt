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
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.VoidEvent

interface WalletRepository {
    suspend fun getCredits(): List<CreditSummary>
    suspend fun createCredit(request: CreateCreditRequest): CreditSummary
    suspend fun putCreditTerms(terms: CreditTerms): CreditSummary
    suspend fun deleteCreditTerms(accountId: String)
    /** Deja la deuda del crédito en [targetBalance] registrando el movimiento de ajuste server-side. */
    suspend fun adjustCreditBalance(accountId: String, targetBalance: Long): CreditSummary
    // F20 — tarjetas de crédito: mismas reglas que los créditos (se leen siempre del server).
    suspend fun getCards(): List<CardSummary>
    suspend fun createCard(request: CreateCardRequest): CardSummary
    suspend fun putCardTerms(terms: CardTerms): CardSummary
    suspend fun deleteCardTerms(accountId: String)
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

    /**
     * F17: la categoría es la PK de un presupuesto, así que renombrarlo no es un campo más de
     * [updateBudget] — es su propia operación server-side (borra e inserta conservando el
     * límite, ver `PUT /api/budgets/{category}/rename`). El gasto se cruza por NOMBRE de
     * categoría con los movimientos (`spentByCategoryForMonth`), así que renombrar acá deja de
     * contar los movimientos que llevaban el nombre viejo — la hoja de edición avisa esto antes
     * de guardar.
     */
    suspend fun renameBudget(category: String, newCategory: String): Budget
    suspend fun getRecurringRules(): List<RecurringRule>
    suspend fun createRecurringRule(rule: RecurringRule): RecurringRule
    suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule
    suspend fun deleteRecurringRule(id: String)
    suspend fun getUpcomingPayments(): List<UpcomingPayment>
    suspend fun chatAi(request: AiChatRequest): AiChatResponse
    suspend fun getAccounts(): List<Account>
    suspend fun getAccount(id: String): Account
    suspend fun createAccount(account: Account): Account

    /**
     * F55: borra la cuenta y TODO lo que le pertenece (sus movimientos, anulaciones, dismissals
     * de pago de tarjeta y términos de crédito si es un LOAN) — el server lo hace en una sola
     * transacción (ver `AccountRoutes.kt` DELETE). No hay deshacer: es responsabilidad de la UI
     * mostrar la consecuencia real antes de llamar esto (ver `AccountDetailScreen`).
     */
    suspend fun deleteAccount(id: String)
    suspend fun postEvent(event: FinancialEvent): FinancialEvent
    suspend fun getEvents(accountId: String? = null): List<FinancialEvent>
    suspend fun getEventsByDay(): List<EventDay>
    suspend fun voidEvent(id: String, reason: String? = null): VoidEvent

    /**
     * Cambia la categoría de un movimiento ya registrado. Devuelve el [FinancialEvent]
     * actualizado con `countsAsCashFlow` derivado — el server lo recalcula, nunca lo toma del
     * cliente.
     */
    suspend fun updateEventCategory(id: String, category: String): FinancialEvent

    /**
     * Movimientos que **parecen** el pago del extracto de una tarjeta pero todavía no están
     * categorizados como [com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY] — candidatos
     * a confirmar con [updateEventCategory], nunca aplicados solos. El server solo propone
     * (ver `looksLikeCardPayment`); esta llamada no muta nada.
     */
    suspend fun getCardPaymentCandidates(): List<FinancialEvent>

    /**
     * Descarta un candidato de [getCardPaymentCandidates] de forma persistente ("No es") — el
     * server deja de proponerlo, pero **no toca su categoría**: el gasto sigue contando como
     * flujo de caja del mes, que es justo lo que hay que preservar en un falso positivo. Idempotente.
     *
     * No hay forma de deshacer este descarte: si fue un error, el movimiento sigue en Movimientos
     * y se recategoriza a mano con [updateEventCategory] (p. ej. desde
     * [com.jvillada.movi.ui.transactions.ChangeCategorySheet]), incluso a
     * [com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY] si en verdad lo era.
     */
    suspend fun dismissCardPaymentCandidate(id: String)
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
