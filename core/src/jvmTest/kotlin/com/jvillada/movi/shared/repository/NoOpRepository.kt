package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.model.EnlaceDeDescarga
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.EdicionDeDocumento
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
     * Deja el evento en [eventosDelServer], reemplazando el que tuviera ese id.
     *
     * Todo lo que este stub "escribe" tiene que quedar visible en el `GET` siguiente: desde que
     * `LocalRepository.getEvents` pregunta al server y la respuesta remota gana, un stub que
     * devolviera la versión vieja le pisaría al espejo local el cambio que el server sí aplicó.
     */
    protected fun recordarEnElServer(event: FinancialEvent) {
        val i = eventosDelServer.indexOfFirst { it.id == event.id }
        if (i >= 0) eventosDelServer[i] = event else eventosDelServer += event
    }


    /**
     * Imita `POST /api/transfers`: construye las dos patas con la MISMA función que usa el
     * server ([transferLegsFor]), sobre cuentas de nombre igual a su id — el stub no tiene un
     * catálogo de cuentas y lo que se ejercita del otro lado es el espejo local, no el texto.
     */
    override suspend fun createTransfer(request: CreateTransferRequest): TransferResult {
        fun stub(id: String) = Account(id = id, name = id, type = AccountType.SAVINGS, balance = 0L)
        val (from, to) = transferLegsFor(request, stub(request.fromAccountId), stub(request.toAccountId))
        eventosDelServer += from
        eventosDelServer += to
        return TransferResult(from = from, to = to)
    }

    override suspend fun getCredits() = emptyList<CreditSummary>()

    /**
     * Imita `POST /api/credits`, **incluido el desembolso**: cuando el pedido lo trae, el server
     * escribe las dos patas y devuelve la cuenta del crédito con la deuda **ya completa** (la
     * apertura de los costos financiados más el desembolso, o sea el capital). Ese detalle es todo
     * lo que este stub existe para reproducir: si devolviera la deuda sin el desembolso adentro, el
     * test del espejo local no podría distinguir el doble conteo de lo correcto.
     */
    override suspend fun createCredit(request: CreateCreditRequest): CreditSummary {
        val base = putCreditTerms(request.terms)
        val desembolso = request.disbursement
            ?: return base.also { cuentasDelServer += it.account }
        val cuentaDelCredito = base.account.copy(
            name = request.name,
            balance = request.terms.principal,
        )
        fun stub(id: String) = Account(id = id, name = id, type = AccountType.SAVINGS, balance = 0L)
        val (from, to) = transferLegsFor(
            CreateTransferRequest(
                transferId = "tr-desembolso", fromEventId = "ev-desembolso-from",
                toEventId = "ev-desembolso-to", fromAccountId = cuentaDelCredito.id,
                toAccountId = desembolso.toAccountId, amount = desembolso.amount,
                timestamp = 1_700_000_000_000L,
            ),
            from = cuentaDelCredito.copy(balance = 0L),
            to = stub(desembolso.toAccountId),
        )
        eventosDelServer += from
        eventosDelServer += to
        return base.copy(
            account = cuentaDelCredito,
            disbursement = TransferResult(from = from, to = to),
        ).also { cuentasDelServer += it.account }
    }
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
    override suspend fun adjustCreditBalance(accountId: String, targetBalance: Long): CreditSummary {
        val ajuste = FinancialEvent(
            id                   = "ev-ajuste-$accountId",
            accountId            = accountId,
            type                 = TransactionType.INCOME,
            amount               = 60_000_000L,
            category             = "Ajuste de saldo",
            description          = "Ajuste al saldo del banco",
            timestamp            = 1_700_000_000_000L,
            source               = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
            // El server calcula esta bandera y la manda (ver EventQueries.withCashFlowFlag): la
            // cuenta es LOAN, así que un ajuste de deuda NO es flujo de caja. El stub tiene que
            // decir lo mismo — desde que la respuesta remota gana, un `true` por defecto acá se
            // vería como "+$60.000.000 de ingresos" en la pantalla del dueño.
            countsAsCashFlow     = false,
        )
        // El server que acaba de registrar el ajuste lo devuelve en el GET siguiente: sin esto,
        // la fila local sellada del espejo sería un fantasma que en la realidad no existe.
        eventosDelServer += ajuste
        return CreditSummary(
            account = Account(id = accountId, name = "Crédito", type = AccountType.LOAN, balance = targetBalance),
            terms = null,
            paidPct = null,
            adjustmentEvent = ajuste,
        )
    }
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
    /**
     * Imita `DELETE /api/accounts/{id}`: borra los movimientos de esa cuenta y **desenlaza la
     * pata hermana** que vive en otra cuenta (ver `desenlazarPatasHermanas` en AccountRoutes).
     *
     * Antes era un no-op, y eso alcanzaba mientras nada bajaba del server. Desde que
     * `LocalRepository.getEvents` pregunta y la respuesta remota gana, un stub que siguiera
     * devolviendo la pata enlazada le pisaría al espejo local el desenlace que el server sí hizo
     * — y el test fallaría por una contradicción del doble, no por un defecto del código.
     */
    override suspend fun registerPayrollDeduction(accountId: String): CreditSummary =
        CreditSummary(
            account = Account(accountId, "Libranza", AccountType.LOAN, 0L),
            terms = null,
            paidPct = null,
        )

    override suspend fun renameAccount(id: String, name: String): Account {
        val i = cuentasDelServer.indexOfFirst { it.id == id }
        val renombrada = (if (i >= 0) cuentasDelServer[i] else Account(id, name, AccountType.SAVINGS, 0L)).copy(name = name)
        if (i >= 0) cuentasDelServer[i] = renombrada else cuentasDelServer += renombrada
        return renombrada
    }

    override suspend fun deleteAccount(id: String) {
        val huerfanas = eventosDelServer
            .filter { it.accountId == id && it.transferId != null }
            .mapNotNull { it.transferId }
            .toSet()
        eventosDelServer.removeAll { it.accountId == id }
        huerfanas.forEach { tid ->
            val i = eventosDelServer.indexOfFirst { it.transferId == tid }
            if (i >= 0) {
                eventosDelServer[i] = eventosDelServer[i].copy(
                    transferId = null,
                    category = ORPHANED_LEG_CATEGORY,
                    description = orphanedLegDescription(eventosDelServer[i].description),
                )
            }
        }
        cuentasDelServer.removeAll { it.id == id }
    }
    /**
     * Los movimientos que este "server" tiene, y que por lo tanto devuelve en [getEvents].
     *
     * Existe por la misma razón que [cuentasDelServer], y la razón se volvió obligatoria desde
     * que `LocalRepository.getEvents` pregunta al server y **deja de mostrar** la fila sellada
     * que el server ya no devuelve: un stub que aceptara un `postEvent` y en el GET siguiente
     * jurara que no tiene ningún movimiento no imita a **ningún** server posible, y los tests
     * fallarían por un fantasma que en la realidad no existe.
     *
     * Un test que quiera imitar "el server tiene cosas que este teléfono nunca vio" —el caso del
     * dueño: base local vacía, 18 eventos arriba— escribe directo en esta lista.
     */
    val eventosDelServer = mutableListOf<FinancialEvent>()

    override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
        eventosDelServer += event
        return event
    }

    override suspend fun getEvents(accountId: String?): List<FinancialEvent> =
        eventosDelServer.filter { accountId == null || it.accountId == accountId }

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
        // La cuenta se CONSERVA: ningún server real mueve un evento de cuenta al recategorizarlo,
        // y desde que el espejo escribe lo que el server devuelve, un accountId inventado se
        // llevaba la fila a una cuenta que no existe. Lo que prueba que pasó por remoto es la
        // descripción «stub», que el camino local no produce.
        return FinancialEvent(
            id = id,
            accountId = eventosDelServer.firstOrNull { it.id == id }?.accountId ?: "acc-stub",
            type = TransactionType.EXPENSE,
            amount = 50_000L,
            category = category,
            description = "stub",
            timestamp = 1_700_000_000_000L,
            source = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
            // El server calcula la bandera y la manda: una categoría reservada nunca es flujo del
            // mes. Sin esto, el default `true` del wire se copiaba al espejo y «Pago de tarjeta»
            // aparecía como gasto.
            countsAsCashFlow = isCashFlow(AccountType.SAVINGS, TransactionType.EXPENSE, category),
        ).also { recordarEnElServer(it) }
    }
    /**
     * Mismo criterio que [updateEventCategory]: 404 para un evento que no está en [knownEventIds]
     * —así el test del camino "todavía sin sincronizar" falla si [LocalRepository] llama al
     * server cuando no debe— y echo del [timestamp] recibido para el que sí conoce.
     */
    override suspend fun updateEventTimestamp(id: String, timestamp: Long): FinancialEvent {
        if (id !in knownEventIds) throw ApiException(404)
        return FinancialEvent(
            id = id,
            accountId = eventosDelServer.firstOrNull { it.id == id }?.accountId ?: "acc-stub",
            type = TransactionType.EXPENSE,
            amount = 50_000L,
            category = "Comida",
            description = "stub",
            timestamp = timestamp,
            source = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        ).also { recordarEnElServer(it) }
    }

    /**
     * Mismo criterio que [updateEventCategory] y [updateEventTimestamp]: 404 para un evento que no
     * está en [knownEventIds] —así el test del camino «todavía sin sincronizar» falla si
     * [LocalRepository] llama al server cuando no debe— y, para el que sí conoce, el evento que ya
     * tenía con los campos pedidos aplicados encima.
     */
    override suspend fun updateEvent(id: String, cambios: EdicionDeMovimiento): FinancialEvent {
        if (id !in knownEventIds) throw ApiException(404)
        val previo = eventosDelServer.firstOrNull { it.id == id }
        val base = previo ?: FinancialEvent(
            id = id,
            accountId = "acc-stub",
            type = TransactionType.EXPENSE,
            amount = 50_000L,
            category = "Comida",
            description = "stub",
            timestamp = 1_700_000_000_000L,
            source = EventSource.MANUAL,
            reconciliationStatus = ReconciliationStatus.RECONCILED,
        )
        return base.copy(
            amount = cambios.amount ?: base.amount,
            accountId = cambios.accountId ?: base.accountId,
            description = cambios.description ?: base.description,
        ).also { recordarEnElServer(it) }
    }

    /** Sin sello: el stub no modela recurrentes, y el aviso opcional simplemente no aparece. */
    override suspend fun getEventOccurrenceMark(id: String): com.jvillada.movi.shared.model.EventOccurrenceMark? = null

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
    override suspend fun payInstallment(request: CreatePagoDeCuotaRequest) =
        PagoDeCuotaResult(deudaRestante = 0L, patas = emptyList())
    override suspend fun getDocuments() = emptyList<Documento>()
    override suspend fun uploadDocument(
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        tipo: TipoDeDocumento,
        accountId: String?,
        periodo: String?,
        notas: String?,
    ) = Documento(
        id = "doc-stub",
        nombre = fileName,
        tipo = tipo,
        mimeType = mimeType,
        bytes = bytes.size.toLong(),
        subidoEn = 0L,
        accountId = accountId,
        periodo = periodo,
        notas = notas,
    )
    override suspend fun getDocumentLink(id: String) = EnlaceDeDescarga(url = "", expiraEn = 0L)
    override suspend fun updateDocument(id: String, cambios: EdicionDeDocumento) = Documento(
        id = id,
        nombre = cambios.nombre ?: "doc",
        tipo = cambios.tipo ?: TipoDeDocumento.OTRO,
        mimeType = "application/pdf",
        bytes = 0,
        subidoEn = 0L,
        periodo = cambios.periodo,
        notas = cambios.notas,
    )
    override suspend fun deleteDocument(id: String) {}
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
