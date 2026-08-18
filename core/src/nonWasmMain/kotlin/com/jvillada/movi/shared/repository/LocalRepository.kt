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
import com.jvillada.movi.shared.model.newId
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
import com.jvillada.movi.shared.model.signedDelta
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

    /**
     * Crea la cuenta contra el server y **espeja el resultado en SQLDelight** — mismo patrón que
     * [adjustCreditBalance]/[updateEventCategory] abajo: `remote` primero, la fila local se
     * escribe con lo que el server devolvió.
     *
     * Antes esto escribía SOLO local, con lo que una cuenta creada en el teléfono nunca llegaba
     * al server (el `SyncEngine` no sincronizaba cuentas), y como los eventos que se le anotaran
     * llevaban ese `accountId`, `SyncEngine.syncEvents` los empujaba contra una cuenta que el
     * server no conocía — 404 "Account not found" (ver `EventRoutes.kt` POST), tragado en
     * silencio por el catch de [com.jvillada.movi.shared.SyncEngine].
     *
     * Sin red (o cualquier otra falla de `remote.createAccount`): la cuenta se escribe igual,
     * local, con `syncedAt = null` — **pendiente**, no perdida. [com.jvillada.movi.shared.SyncEngine.syncAccounts]
     * la recoge en su próximo ciclo y la empuja, siempre ANTES de `syncEvents` (una cuenta tiene
     * que existir en el server antes que sus eventos). Se decide no distinguir acá "sin red" de
     * "el server rechazó la cuenta" — la alternativa (perder la cuenta que el dueño acaba de
     * crear) es peor que reintentarla cada 30s; ver el KDoc de `SyncEngine.syncAccounts` para el
     * mismo trade-off en la otra punta.
     */
    override suspend fun createAccount(account: Account): Account {
        // Red de seguridad, no la vía principal: la UI ya manda `id = newId("acc")` (ver
        // com.jvillada.movi.ui.accounts.CreateAccountSheet). Nunca insertar con PK "" — con
        // INSERT OR REPLACE, una segunda cuenta creada antes de que la primera tuviera id
        // reemplazaría a la primera en vez de agregarse.
        val resolved = if (account.id.isBlank()) account.copy(id = newId("acc")) else account
        val uid = userId()
        return try {
            val created = remote.createAccount(resolved)
            db.accountQueries.insert(
                created.id, created.name, created.type.name,
                created.balance, created.currency, uid,
                Clock.System.now().toEpochMilliseconds(),
            )
            created
        } catch (e: Exception) {
            db.accountQueries.insert(
                resolved.id, resolved.name, resolved.type.name,
                resolved.balance, resolved.currency, uid,
                null,
            )
            resolved
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
        // Red de seguridad, no la vía principal: la UI ya manda `id = newId("ev")` en los tres
        // call sites (QuickAddScreen, SMSScreens; CreateAccountSheet es para cuentas, no
        // eventos). Nunca insertar con PK "" — con INSERT OR REPLACE, un segundo evento sin id
        // reemplazaría al primero en vez de agregarse, y si el segundo llegaba a sincronizarse
        // antes que el ciclo de 30s levantara al primero, este último nunca subía: el teléfono y
        // el server terminaban mostrando movimientos distintos.
        val resolved = if (event.id.isBlank()) event.copy(id = newId("ev")) else event
        db.transaction {
            db.financialEventQueries.insert(
                resolved.id, resolved.accountId, resolved.type.name, resolved.amount,
                resolved.category, resolved.description, resolved.merchant,
                resolved.timestamp, resolved.source.name, resolved.rawPayload,
                resolved.reconciliationStatus.name, resolved.syncedAt, userId()
            )
            val acct = db.accountQueries.selectById(resolved.accountId).executeAsOneOrNull()
            if (acct != null) {
                // signedDelta, no un `if INCOME suma` a secas (Hallazgo bloqueante 2 de la
                // revisión de esta rama): en una cuenta LOAN/CREDIT_CARD un INCOME es un abono
                // que BAJA la deuda. Antes de este fix, un abono desde QuickAdd a una libranza
                // ya ajustada la subía en vez de bajarla — el teléfono y el server divergían.
                val accountType = AccountType.valueOf(acct.type)
                val delta = signedDelta(accountType, resolved.type, resolved.amount)
                db.accountQueries.updateBalance(acct.balance + delta, acct.id)
            }
        }
        return resolved
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
                    // Reversa exacta de signedDelta (mismo hallazgo que postEvent, arriba):
                    // anular un evento en una cuenta LOAN/CREDIT_CARD tiene que deshacer el
                    // efecto con la convención de deuda, no con la de cuenta de activo.
                    val accountType = AccountType.valueOf(acct.type)
                    val originalDelta = signedDelta(accountType, TransactionType.valueOf(event.type), event.amount)
                    db.accountQueries.updateBalance(acct.balance - originalDelta, acct.id)
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
        val types = accountTypes(uid)
        // Leer y escribir en una transacción, y **revalidar** adentro: esto cierra SOLO LA MITAD
        // de la carrera con el `SyncEngine` (hallazgo de revisión: un comentario que prometía
        // cerrarla entera estaba mal). La mitad que sí cierra: si el `SyncEngine` YA marcó la
        // fila como sincronizada antes de que esta transacción arrancara, acá se ve
        // `local.syncedAt != null` (se relee fresco, no se confía en un snapshot de afuera) y se
        // cae al camino de `remote`, que es el correcto para una fila ya sincronizada.
        //
        // La otra mitad — el `SyncEngine` terminando su `postEvent` (en vuelo, sin ningún lock
        // sobre la fila) y sellando la fila con `markSynced` DESPUÉS de que esta transacción ya
        // commiteó la categoría nueva — NO se cierra acá: para cuando el `SyncEngine` intenta
        // sellar, esta transacción ya terminó y no hay nada que revalidar. Esa mitad se cierra
        // del otro lado, en `SyncEngine.syncEvents`/`markSyncedIfUnchanged`: el sello solo aplica
        // si la categoría no cambió desde el snapshot que efectivamente se empujó. Sin ese fix,
        // la fila quedaba sincronizada con la categoría vieja en el server y la nueva solo en
        // local — y como ya no sale en `selectUnsynced`, ningún ciclo futuro la volvía a
        // empujar. La divergencia era silenciosa y permanente.
        val resolvedLocally = db.transactionWithResult {
            val local = db.financialEventQueries.selectById(id, uid).executeAsOneOrNull()
            if (local != null && local.syncedAt == null) {
                db.financialEventQueries.updateCategory(category, id, uid)
                // La categoría se aplica ANTES de derivar la bandera: al revés, countsAsCashFlow
                // saldría calculado contra la categoría vieja y el objeto devuelto diría que un
                // pago de tarjeta sí es flujo de caja — el mismo doble conteo que esto arregla.
                val model = local.toModel(types).copy(category = category)
                val accountType = types[model.accountId]
                if (accountType == null) model
                else model.copy(countsAsCashFlow = isCashFlow(accountType, model.type, category))
            } else {
                null
            }
        }
        if (resolvedLocally != null) return resolvedLocally

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
            // syncedAt = ahora: esto vino del server, no hay nada pendiente de empujar.
            db.accountQueries.insert(
                summary.account.id, summary.account.name, summary.account.type.name,
                summary.account.balance, summary.account.currency, uid,
                Clock.System.now().toEpochMilliseconds(),
            )
        }
        return summary
    }
    override suspend fun getSubscriptions(): SubscriptionsResult = remote.getSubscriptions()
    override suspend fun detectSubscriptions(): SubscriptionsResult = remote.detectSubscriptions()
    override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription = remote.updateSubscription(id, subscription)
    override suspend fun deleteSubscription(id: String) = remote.deleteSubscription(id)
    // Propuesta de solo lectura, sin efecto secundario que espejar (a diferencia de
    // updateEventCategory/adjustCreditBalance arriba): delega directo, igual que el resto de
    // esta sección.
    override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = remote.getCardPaymentCandidates()
    // Igual que getCardPaymentCandidates arriba: no hay nada que espejar localmente — "No es" no
    // toca la categoría del evento, así que no hay ninguna fila local que quedaría desactualizada.
    override suspend fun dismissCardPaymentCandidate(id: String) = remote.dismissCardPaymentCandidate(id)
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
