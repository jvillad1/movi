package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.Holding
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.PasswordResetRequest
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.VoidEvent
import com.jvillada.movi.shared.model.isCashFlow
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
        val types = accountTypes(uid)
        return if (accountId != null)
            db.financialEventQueries.selectByAccount(accountId, uid).executeAsList().map { it.toModel(types) }
        else
            db.financialEventQueries.selectAll(uid).executeAsList().map { it.toModel(types) }
    }

    override suspend fun getEventsByDay(): List<EventDay> =
        getEvents()
            .groupBy { epochMillisToDate(it.timestamp) }
            .map { (date, items) ->
                EventDay(
                    // Mismo criterio que el server (ver EventRoutes /by-day): el total del día
                    // es flujo de caja, así que los movimientos de cuentas de deuda no entran.
                    // El renglón se sigue listando; solo no encabeza el día.
                    date = date,
                    total = items.filter { it.countsAsCashFlow }.sumOf {
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

    /**
     * Cambia la categoría contra el server y **espeja el resultado en SQLDelight**.
     *
     * Mismo problema que [adjustCreditBalance]: en Android, Movimientos/Análisis/Presupuestos
     * leen de acá, no del server, y el `SyncEngine` solo empuja — nunca trae. Sin este espejo,
     * recategorizar un pago de tarjeta como [com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY]
     * quedaría bien en el server pero la fila local seguiría con la categoría vieja, y el gasto
     * duplicado que esta feature existe para arreglar seguiría duplicado en el teléfono.
     *
     * A diferencia de [adjustCreditBalance], acá **no hay saldo que copiar**: recategorizar no es
     * un movimiento de plata, así que el espejo es un UPDATE puntual de `category` — no un
     * insert, y `account` no se toca. Se escribe `updated.id`/`updated.category` (lo que devolvió
     * el server, ya recortado/validado) en vez de los parámetros crudos, mismo criterio que
     * [adjustCreditBalance] usa para el evento de ajuste.
     *
     * Hay un segundo caso, el inverso del anterior: el evento **todavía no llegó al server**
     * (`postEvent` escribe solo local, `syncedAt == null` hasta que el `SyncEngine` lo empuje en
     * su ciclo de 30s). Ahí llamar a `remote` primero es al revés — el server ni sabe que el
     * evento existe, así que responde 404 antes de que la categoría se corrija en ningún lado.
     * Para ese caso el UPDATE es **solo local**: el `SyncEngine` va a subir el evento en su
     * próximo ciclo y `syncEvents` ya manda `row.category`, así que la categoría corregida viaja
     * sola, sin necesidad de tocar el server acá.
     */
    override suspend fun updateEventCategory(id: String, category: String): FinancialEvent {
        val uid = userId()
        val local = db.financialEventQueries.selectById(id, uid).executeAsOneOrNull()
        if (local != null && local.syncedAt == null) {
            db.financialEventQueries.updateCategory(category, id, uid)
            return local.toModel(accountTypes(uid)).copy(category = category)
        }
        val updated = remote.updateEventCategory(id, category)
        db.financialEventQueries.updateCategory(updated.category, updated.id, uid)
        return updated
    }

    // ── Delegate everything else to remote ────────────────────────────────────

    override suspend fun getHoldings(): List<Holding> = remote.getHoldings()
    override suspend fun getCredits(): List<CreditSummary> = remote.getCredits()
    override suspend fun createCredit(request: CreateCreditRequest): CreditSummary = remote.createCredit(request)
    override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary = remote.putCreditTerms(terms)
    override suspend fun deleteCreditTerms(accountId: String) = remote.deleteCreditTerms(accountId)
    /**
     * Ajusta contra el server y **espeja el resultado en la DB local**.
     *
     * El resto de las operaciones de crédito delegan y ya: se leen siempre desde el server. El
     * ajuste no puede, porque su efecto secundario —un movimiento en la cuenta— se lee desde acá:
     * [getEvents]/[getEventsByDay] y [getAccounts] van a SQLDelight, y [com.jvillada.movi.shared.SyncEngine]
     * solo empuja, nunca trae. Sin este espejo, en Android el ajuste no aparecía en Movimientos,
     * ni en Análisis, ni en Presupuestos, ni en el detalle de la cuenta, y la pantalla de Cuentas
     * seguía mostrando la deuda vieja para siempre — mientras la hoja prometía por escrito que
     * "queda como un movimiento visible en la cuenta".
     *
     * Se escribe el evento **exacto** que devolvió el server (`adjustmentEvent`), no uno
     * reconstruido: mismo id, mismo monto, misma marca de tiempo. Va ya marcado como sincronizado
     * para que el SyncEngine no lo vuelva a subir y duplique el ajuste.
     *
     * El saldo de la cuenta se copia del server en vez de sumarle un delta calculado acá: el
     * server lo deriva de todos los eventos con el signo correcto por tipo de cuenta, y para una
     * cuenta LOAN el delta local de [postEvent] tiene el signo al revés.
     */
    override suspend fun adjustCreditBalance(accountId: String, targetBalance: Long): CreditSummary {
        val summary = remote.adjustCreditBalance(accountId, targetBalance)
        val uid = userId()
        val event = summary.adjustmentEvent
        db.transaction {
            if (event != null) {
                db.financialEventQueries.insert(
                    event.id, event.accountId, event.type.name, event.amount,
                    event.category, event.description, event.merchant,
                    event.timestamp, event.source.name, event.rawPayload,
                    event.reconciliationStatus.name,
                    event.syncedAt ?: Clock.System.now().toEpochMilliseconds(),
                    uid,
                )
            }
            // Upsert (INSERT OR REPLACE): si el crédito se creó desde el server la fila puede no
            // existir localmente todavía, y en ese caso la pantalla de Cuentas ni siquiera lo veía.
            db.accountQueries.insert(
                summary.account.id, summary.account.name, summary.account.type.name,
                summary.account.balance, summary.account.currency, uid,
            )
        }
        return summary
    }
    override suspend fun getSubscriptions(): SubscriptionsResult = remote.getSubscriptions()
    override suspend fun detectSubscriptions(): SubscriptionsResult = remote.detectSubscriptions()
    override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription = remote.updateSubscription(id, subscription)
    override suspend fun deleteSubscription(id: String) = remote.deleteSubscription(id)
    override suspend fun getGoals(): List<Goal> = remote.getGoals()
    override suspend fun getSmsMessages(): List<SmsMessage> = remote.getSmsMessages()
    override suspend fun getSms(id: String): SmsMessage = remote.getSms(id)
    override suspend fun parseSms(id: String): ParsedSms = remote.parseSms(id)
    override suspend fun confirmSms(id: String) = remote.confirmSms(id)
    override suspend fun ignoreSms(id: String) = remote.ignoreSms(id)
    override suspend fun syncSms(messages: List<SmsMessage>) = remote.syncSms(messages)
    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary = remote.getFinanceSummary(scope)
    override suspend fun getBudgets(): List<Budget> = remote.getBudgets()
    override suspend fun createBudget(budget: Budget): Budget = remote.createBudget(budget)
    override suspend fun updateBudget(category: String, budget: Budget): Budget = remote.updateBudget(category, budget)
    override suspend fun deleteBudget(category: String) = remote.deleteBudget(category)
    override suspend fun getRecurringRules(): List<RecurringRule> = remote.getRecurringRules()
    override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule = remote.createRecurringRule(rule)
    override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule = remote.updateRecurringRule(id, rule)
    override suspend fun deleteRecurringRule(id: String) = remote.deleteRecurringRule(id)
    override suspend fun getUpcomingPayments(): List<UpcomingPayment> = remote.getUpcomingPayments()
    override suspend fun chatAi(request: AiChatRequest): AiChatResponse = remote.chatAi(request)
    override suspend fun register(request: RegisterRequest): AuthResponse = remote.register(request)
    override suspend fun login(request: LoginRequest): AuthResponse = remote.login(request)
    override suspend fun requestPasswordReset(request: PasswordResetRequest): Int = remote.requestPasswordReset(request)
    override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult =
        remote.uploadStatement(fileName, bytes, mimeType)
    override suspend fun importStatement(decision: ImportDecision) =
        remote.importStatement(decision)
    override suspend fun getStatementImports(): List<StatementImport> =
        remote.getStatementImports()
    override suspend fun getStatementImportDetail(id: String): StatementImportDetail =
        remote.getStatementImportDetail(id)
    override suspend fun getScreen(slug: String, cachedVersion: Int?): ScreenDefinition? =
        remote.getScreen(slug, cachedVersion)
    override suspend fun putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition =
        remote.putScreen(slug, sections)
    override suspend fun restoreScreen(slug: String): ScreenDefinition =
        remote.restoreScreen(slug)
    override suspend fun isScreenAdmin(): Boolean =
        remote.isScreenAdmin()

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Tipo de cuenta por id. `countsAsCashFlow` no está en la tabla local —es derivado, igual que
     * en el server— así que hay que resolverlo contra `account` en cada lectura.
     */
    private fun accountTypes(uid: String): Map<String, AccountType> =
        db.accountQueries.selectAll(uid).executeAsList()
            .mapNotNull { row ->
                runCatching { AccountType.valueOf(row.type) }.getOrNull()?.let { row.id to it }
            }
            .toMap()

    private fun com.jvillada.movi.Financial_event.toModel(
        typeByAccount: Map<String, AccountType> = emptyMap(),
    ) = FinancialEvent(
        id = id, accountId = accountId,
        type = TransactionType.valueOf(type),
        amount = amount, category = category,
        description = description, merchant = merchant,
        timestamp = timestamp,
        source = EventSource.valueOf(source),
        rawPayload = rawPayload,
        reconciliationStatus = ReconciliationStatus.valueOf(reconciliationStatus),
        syncedAt = syncedAt,
        countsAsCashFlow = typeByAccount[accountId]
            ?.let { isCashFlow(it, TransactionType.valueOf(type), category) }
            ?: true,
    )
}

private fun epochMillisToDate(millis: Long): String =
    Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date.toString()
