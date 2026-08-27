package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryUsage
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
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RecurringOccurrence
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
import com.jvillada.movi.shared.model.ChangePasswordRequest
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
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

    /** F38: alta manual — nace CONFIRMED, ver KDoc de [CreateSubscriptionRequest]. */
    suspend fun createSubscription(request: CreateSubscriptionRequest): Subscription
    suspend fun getGoals(): List<Goal>

    /**
     * F26: alta de una meta de ahorro. `saved` viaja en 0 y se ignora — el server lo deriva
     * siempre del saldo de la cuenta elegida. 404 si la cuenta no es del usuario, 422 si es una
     * cuenta de deuda (solo Dinero/Inversión pueden ahorrar).
     */
    suspend fun createGoal(goal: Goal): Goal
    suspend fun updateGoal(id: String, goal: Goal): Goal
    suspend fun deleteGoal(id: String)
    suspend fun getSmsMessages(): List<SmsMessage>
    suspend fun getSms(id: String): SmsMessage
    suspend fun parseSms(id: String): ParsedSms
    suspend fun confirmSms(id: String)
    suspend fun ignoreSms(id: String)
    suspend fun getFinanceSummary(scope: Scope): FinanceSummary
    /**
     * Cifras del Inicio ya reducidas en el server (`GET /api/dashboard/summary`): gasto del mes
     * por categoría, candidatos a pago de tarjeta y SMS pendientes — sin bajar las colecciones
     * enteras. Ver [DashboardSummary].
     */
    suspend fun getDashboardSummary(scope: Scope): DashboardSummary
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

    // ── Categorías (Ola 10 · «Más → Categorías») ──────────────────────────────

    /**
     * La lista completa de categorías **con su uso real** — del catálogo y propias, en una sola
     * lista. Ver [CategoryUsage] y `GET /api/categories`.
     */
    suspend fun getCategories(): List<CategoryUsage>

    /**
     * Renombrar una categoría **propia**: el arreglo del error de tipeo. Reescribe los
     * movimientos, el presupuesto y las reglas recurrentes que la llevan, **en una sola
     * transacción del server**.
     *
     * Rechaza (422) las reservadas y las del catálogo — ver `CategoryRoutes`. Si el nombre
     * destino ya existe devuelve 409: eso ya no es renombrar sino unificar, y lo decide el dueño
     * ([mergeCategory]).
     */
    suspend fun renameCategory(from: String, to: String): CategoryRewriteResult

    /**
     * Unificar una categoría dentro de otra: el arreglo de los duplicados. Misma reescritura
     * atómica que [renameCategory], y además esconde la de origen si venía del catálogo (es el
     * camino para juntar «Otros ingresos» dentro de «Otros»).
     */
    suspend fun mergeCategory(from: String, into: String): CategoryRewriteResult

    /**
     * Esconder / mostrar una categoría y fijarle el tipo («EXPENSE», «INCOME», «BOTH» o `null`
     * para volver a lo que diga el catálogo o el uso).
     *
     * **Esconder no borra nada**: los movimientos viejos la siguen diciendo y siguen contando
     * donde contaban; lo único que cambia es que deja de ofrecerse al escribir.
     */
    suspend fun setCategoryPrefs(name: String, hidden: Boolean, pinnedType: String?): CategoryUsage
    suspend fun getRecurringRules(): List<RecurringRule>
    suspend fun createRecurringRule(rule: RecurringRule): RecurringRule
    suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule
    suspend fun deleteRecurringRule(id: String)
    suspend fun getUpcomingPayments(): List<UpcomingPayment>

    /**
     * «¿Esto ya ocurrió?» — el estado del periodo en juego de cada recurrente, con lo que la app
     * **propone** como su ocurrencia. Ver [com.jvillada.movi.shared.model.OccurrenceState].
     */
    suspend fun getOccurrenceStates(): List<OccurrenceState>

    /**
     * Sella un periodo como ocurrido. [eventId] `null` = «ya lo pagué / ya me llegó», sin
     * movimiento que emparejar. Volver a llamarlo con otro movimiento reemplaza el sello.
     */
    suspend fun markOccurrence(ruleId: String, period: String, eventId: String? = null): RecurringOccurrence

    /** Deshacer el sello: el recurrente vuelve a estar pendiente en ese periodo. */
    suspend fun unmarkOccurrence(ruleId: String, period: String)
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
    /**
     * Anula un movimiento. Si es una **pata de traspaso**, anula también la otra — un traspaso
     * anulado a medias deja el saldo de una de las dos cuentas mintiendo. El server lo resuelve
     * por `transferId` dentro de la misma transacción; el espejo local hace lo propio.
     */
    suspend fun voidEvent(id: String, reason: String? = null): VoidEvent

    /**
     * Crea un **traspaso**: mueve plata entre dos cuentas propias en un solo POST, que el server
     * convierte en las dos patas enlazadas (ver [com.jvillada.movi.shared.model.transferLegsFor]).
     *
     * A diferencia de [postEvent], esto **exige conexión**: no hay camino offline. La atomicidad
     * de las dos patas vive en la transacción del server, y el `SyncEngine` empuja eventos de a
     * uno — un traspaso anotado sin red podía llegar por mitades. Si el POST falla, la excepción
     * se propaga para que la UI lo diga con todas las letras, igual que [deleteAccount].
     */
    suspend fun createTransfer(request: CreateTransferRequest): TransferResult

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

    /** F42 · F46: perfil editable — `GET /api/users/me`. `avatarColor` nunca llega `null`. */
    suspend fun getUserProfile(): UserProfile

    /** `PUT /api/users/me`. Campos opcionales — solo se toca lo que viene en [request]. */
    suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile

    /**
     * `PUT /api/users/me/password`. Lanza [ApiException] con 403 si [ChangePasswordRequest.current]
     * no coincide, o 400 si [ChangePasswordRequest.new] no cumple [com.jvillada.movi.shared.model.PasswordPolicy]
     * — el servidor es la autoridad, la validación del cliente es solo cortesía.
     */
    suspend fun changePassword(request: ChangePasswordRequest)

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
