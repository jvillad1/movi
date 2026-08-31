package com.jvillada.movi.data

import com.jvillada.movi.shared.model.*
import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.ui.dashboard.DashboardDataCache

/**
 * Envuelve el repositorio para que **cualquier escritura invalide la caché del Inicio**.
 *
 * ### Por qué existe
 *
 * El Inicio guarda su última carga y se saltea las diez llamadas si son recientes (ver
 * `debeRecargarElInicio`). Para que eso sea honesto, algo tiene que avisarle cuando la plata
 * cambió. La primera versión confiaba en `LocalRefreshTick` y **eso era falso**: el tick es un
 * `Int` sin función para subirlo, así que ninguna pantalla puede moverlo — sus dos únicos
 * productores viven en `App.kt`. Lo contó la revisión, camino por camino.
 *
 * Anular un movimiento, cambiar su categoría o su fecha, ajustar el saldo de un crédito,
 * registrar un descuento de nómina, importar un extracto, crear una cuenta o un crédito: nada de
 * eso movía el tick, y todo eso mueve plata.
 *
 * ### Por qué acá y no en cada pantalla
 *
 * La alternativa era llamar a `invalidar()` en los quince sitios que escriben. Es exactamente la
 * clase de invariante que se rompe la primera vez que alguien agrega una pantalla y no se acuerda
 * — y este archivo ya tuvo un caso así (`cargadoEn` escrito solo por el Inicio, sostenido por un
 * comentario). Acá hay **un** punto por el que pasa todo, porque toda la app lee
 * `Repositories.wallets`.
 *
 * ### La regla: cualquier escritura, sin excepciones
 *
 * No se juzga cuáles escrituras «afectan al Inicio». Cambiar la contraseña no mueve ninguna
 * cifra, y aun así invalida — el costo de equivocarse por ese lado es una carga de más, y por el
 * otro es que el dueño vea plata que ya no es la suya. Una regla que no pide criterio es una
 * regla que no se aplica mal.
 *
 * Se invalida **después** de que la escritura salió bien: si el server rechaza, no cambió nada y
 * no hay nada que refrescar.
 *
 * Las lecturas (`get*`) pasan derecho por la delegación `by delegado` y no se listan acá.
 */
internal class InvalidaElInicioAlEscribir(
    private val delegado: WalletRepository,
) : WalletRepository by delegado {

    private inline fun <T> trasEscribir(bloque: () -> T): T =
        bloque().also { DashboardDataCache.invalidar() }

    override suspend fun createCredit(request: CreateCreditRequest): CreditSummary = trasEscribir { delegado.createCredit(request) }
    override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary = trasEscribir { delegado.putCreditTerms(terms) }
    override suspend fun deleteCreditTerms(accountId: String): Unit = trasEscribir { delegado.deleteCreditTerms(accountId) }
    override suspend fun registerPayrollDeduction(accountId: String): CreditSummary = trasEscribir { delegado.registerPayrollDeduction(accountId) }
    override suspend fun adjustCreditBalance(accountId: String, targetBalance: Long): CreditSummary = trasEscribir { delegado.adjustCreditBalance(accountId, targetBalance) }
    override suspend fun createCard(request: CreateCardRequest): CardSummary = trasEscribir { delegado.createCard(request) }
    override suspend fun putCardTerms(terms: CardTerms): CardSummary = trasEscribir { delegado.putCardTerms(terms) }
    override suspend fun deleteCardTerms(accountId: String): Unit = trasEscribir { delegado.deleteCardTerms(accountId) }
    override suspend fun detectSubscriptions(): SubscriptionsResult = trasEscribir { delegado.detectSubscriptions() }
    override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription = trasEscribir { delegado.updateSubscription(id, subscription) }
    override suspend fun deleteSubscription(id: String): Unit = trasEscribir { delegado.deleteSubscription(id) }
    override suspend fun createSubscription(request: CreateSubscriptionRequest): Subscription = trasEscribir { delegado.createSubscription(request) }
    override suspend fun createGoal(goal: Goal): Goal = trasEscribir { delegado.createGoal(goal) }
    override suspend fun updateGoal(id: String, goal: Goal): Goal = trasEscribir { delegado.updateGoal(id, goal) }
    override suspend fun deleteGoal(id: String): Unit = trasEscribir { delegado.deleteGoal(id) }
    override suspend fun parseSms(id: String): ParsedSms = trasEscribir { delegado.parseSms(id) }
    override suspend fun confirmSms(id: String): Unit = trasEscribir { delegado.confirmSms(id) }
    override suspend fun ignoreSms(id: String): Unit = trasEscribir { delegado.ignoreSms(id) }
    override suspend fun createBudget(budget: Budget): Budget = trasEscribir { delegado.createBudget(budget) }
    override suspend fun updateBudget(category: String, budget: Budget): Budget = trasEscribir { delegado.updateBudget(category, budget) }
    override suspend fun deleteBudget(category: String): Unit = trasEscribir { delegado.deleteBudget(category) }
    override suspend fun renameBudget(category: String, newCategory: String): Budget = trasEscribir { delegado.renameBudget(category, newCategory) }
    override suspend fun renameCategory(from: String, to: String): CategoryRewriteResult = trasEscribir { delegado.renameCategory(from, to) }
    override suspend fun mergeCategory(from: String, into: String): CategoryRewriteResult = trasEscribir { delegado.mergeCategory(from, into) }
    override suspend fun setCategoryPrefs(name: String, hidden: Boolean, pinnedType: String?): CategoryUsage = trasEscribir { delegado.setCategoryPrefs(name, hidden, pinnedType) }
    override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule = trasEscribir { delegado.createRecurringRule(rule) }
    override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule = trasEscribir { delegado.updateRecurringRule(id, rule) }
    override suspend fun deleteRecurringRule(id: String): Unit = trasEscribir { delegado.deleteRecurringRule(id) }
    override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence = trasEscribir { delegado.markOccurrence(ruleId, period, eventId) }
    override suspend fun unmarkOccurrence(ruleId: String, period: String): Unit = trasEscribir { delegado.unmarkOccurrence(ruleId, period) }
    override suspend fun chatAi(request: AiChatRequest): AiChatResponse = trasEscribir { delegado.chatAi(request) }
    override suspend fun createAccount(account: Account): Account = trasEscribir { delegado.createAccount(account) }
    override suspend fun deleteAccount(id: String): Unit = trasEscribir { delegado.deleteAccount(id) }
    override suspend fun postEvent(event: FinancialEvent): FinancialEvent = trasEscribir { delegado.postEvent(event) }
    override suspend fun voidEvent(id: String, reason: String?): VoidEvent = trasEscribir { delegado.voidEvent(id, reason) }
    override suspend fun createTransfer(request: CreateTransferRequest): TransferResult = trasEscribir { delegado.createTransfer(request) }
    override suspend fun payInstallment(request: CreatePagoDeCuotaRequest): PagoDeCuotaResult = trasEscribir { delegado.payInstallment(request) }
    override suspend fun updateEventCategory(id: String, category: String): FinancialEvent = trasEscribir { delegado.updateEventCategory(id, category) }
    override suspend fun updateEventTimestamp(id: String, timestamp: Long): FinancialEvent = trasEscribir { delegado.updateEventTimestamp(id, timestamp) }
    override suspend fun dismissCardPaymentCandidate(id: String): Unit = trasEscribir { delegado.dismissCardPaymentCandidate(id) }
    override suspend fun register(request: RegisterRequest): AuthResponse = trasEscribir { delegado.register(request) }
    override suspend fun login(request: LoginRequest): AuthResponse = trasEscribir { delegado.login(request) }
    override suspend fun renameAccount(id: String, name: String): Account = trasEscribir { delegado.renameAccount(id, name) }
    // Marcar la condición de una cuenta cambia «Tu plata» del Inicio: sin invalidar el caché, el
    // dueño marcaba Skandia y volvía al Inicio con la cifra vieja.
    override suspend fun updateAccountCondition(id: String, condicionadaA: String?): Account = trasEscribir { delegado.updateAccountCondition(id, condicionadaA) }
    override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile = trasEscribir { delegado.updateUserProfile(request) }
    override suspend fun changePassword(request: ChangePasswordRequest): Unit = trasEscribir { delegado.changePassword(request) }
    override suspend fun requestPasswordReset(request: PasswordResetRequest): Int = trasEscribir { delegado.requestPasswordReset(request) }
    override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult = trasEscribir { delegado.uploadStatement(fileName, bytes, mimeType) }
    override suspend fun uploadDocument(fileName: String, bytes: ByteArray, mimeType: String, tipo: TipoDeDocumento, accountId: String?, periodo: String?, notas: String?): Documento = trasEscribir { delegado.uploadDocument(fileName, bytes, mimeType, tipo, accountId, periodo, notas) }
    override suspend fun updateDocument(id: String, cambios: EdicionDeDocumento): Documento = trasEscribir { delegado.updateDocument(id, cambios) }
    override suspend fun deleteDocument(id: String): Unit = trasEscribir { delegado.deleteDocument(id) }
    override suspend fun importStatement(decision: ImportDecision): Unit = trasEscribir { delegado.importStatement(decision) }
    override suspend fun putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition = trasEscribir { delegado.putScreen(slug, sections) }
    override suspend fun restoreScreen(slug: String): ScreenDefinition = trasEscribir { delegado.restoreScreen(slug) }
}
