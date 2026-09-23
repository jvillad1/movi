package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.model.MovimientoRechazado
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.model.EnlaceDeDescarga
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.EdicionDeDocumento
import com.jvillada.movi.shared.model.EdicionDeMovimiento
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AdjustAccountBalanceResponse
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreateSubscriptionRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.TransferResult
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.MovimientosDelDestino
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
import com.jvillada.movi.shared.model.RecategorizarEnLoteResponse

interface WalletRepository {
    suspend fun getCredits(): List<CreditSummary>
    suspend fun createCredit(request: CreateCreditRequest): CreditSummary
    suspend fun putCreditTerms(terms: CreditTerms): CreditSummary
    suspend fun deleteCreditTerms(accountId: String)
    /** Deja la deuda del crédito en [targetBalance] registrando el movimiento de ajuste server-side. */
    /**
     * Registra el descuento de nómina del mes de una libranza. Idempotente por período: dos
     * llamadas en el mismo mes no bajan la deuda dos veces.
     */
    suspend fun registerPayrollDeduction(accountId: String): CreditSummary

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

    // ── Cuentas de otros (destinos conocidos) ─────────────────────────────────

    /**
     * **Las cuentas ajenas que el dueño registró**, con lo que le mandó a cada una ya calculado
     * (ver [DestinoConocido.totales], derivado en cada lectura).
     *
     * **Solo en línea, a propósito**: esto no tiene espejo local. Es un registro que se edita una
     * vez —un número de cuenta no cambia— y los totales salen de cruzar TODOS los movimientos del
     * dueño, que es una cuenta que el teléfono haría distinta de la del server mientras tenga filas
     * sin sincronizar. Un total de plata que da dos números según dónde se mire es peor que un total
     * que no se puede ver sin señal.
     */
    suspend fun getDestinos(): List<DestinoConocido>

    /**
     * Registra una cuenta ajena. 400 si el nombre o el número no sirven (ver `rechazoDelDestino`),
     * 409 si ya hay otro destino con esos últimos cuatro dígitos, y **422 si ese número es de una
     * cuenta suya** — el rechazo que evita que Movi le ponga el nombre de otra persona a un
     * movimiento propio (ver `cuentaPropiaConEseNumero`).
     */
    suspend fun createDestino(destino: DestinoConocido): DestinoConocido
    suspend fun updateDestino(id: String, destino: DestinoConocido): DestinoConocido
    suspend fun deleteDestino(id: String)

    /** Los movimientos que fueron a ese destino, del más reciente al más viejo. 404 si no es suyo. */
    suspend fun getMovimientosDelDestino(id: String): MovimientosDelDestino

    suspend fun getSmsMessages(): List<SmsMessage>
    suspend fun getSms(id: String): SmsMessage
    suspend fun parseSms(id: String): ParsedSms

    /**
     * **Los movimientos que ya están anotados y parecen ser este SMS** (`GET /api/sms/{id}/coincidencias`):
     * mismo monto, misma moneda, mismo tipo y a pocos días del mensaje. Vacía si no hay ninguno.
     *
     * Existe para no duplicar: confirmar un SMS siempre creaba un movimiento nuevo, aunque el dueño
     * ya lo hubiera anotado a mano. Con esto la pantalla pregunta «¿es este?» antes.
     */
    suspend fun getSmsCoincidencias(id: String): List<FinancialEvent>
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
     * categoría con los movimientos (`spentByCategoryForPeriod`), así que renombrar acá deja de
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
     * **Por dónde le pueden llegar los recordatorios a este usuario**, según el server.
     *
     * Lo pregunta el cliente antes de afirmar que un aviso NO va a llegar. Ver
     * [com.jvillada.movi.shared.model.ReminderChannels]: sin esto, la app miraba solo el permiso
     * de notificaciones del navegador y concluía «no hay ningún canal» — falso donde el correo
     * está configurado, que es el caso de producción.
     */
    suspend fun getReminderChannels(): ReminderChannels

    /**
     * «¿Esto ya ocurrió?» — el estado del periodo en juego de cada recurrente, con lo que la app
     * **propone** como su ocurrencia. Ver [com.jvillada.movi.shared.model.OccurrenceState].
     */
    suspend fun getOccurrenceStates(): List<OccurrenceState>

    /**
     * Sella un periodo como ocurrido, **anclado al movimiento que el dueño confirmó**. Volver a
     * llamarlo con otro movimiento reemplaza el sello.
     *
     * **[eventId] `null` sigue existiendo, pero la app ya no lo manda.** Era el «ya lo pagué / ya
     * me llegó»: cerraba el periodo sin ninguna evidencia, y el dueño pidió cerrar esa puerta —*«no
     * me debería dejar hacer check sin que el movimiento asociado exista»*—. El server lo sigue
     * aceptando porque en su base hay sellos viejos hechos así y romperlos sería peor; la app los
     * muestra como «marcado a mano, sin movimiento» y ofrece quitarlos, y no crea ninguno nuevo.
     * Si vas a agregar un llamador con `null`, ese es el debate que estás reabriendo.
     */
    suspend fun markOccurrence(ruleId: String, period: String, eventId: String? = null): RecurringOccurrence

    /** Deshacer el sello: el recurrente vuelve a estar pendiente en ese periodo. */
    suspend fun unmarkOccurrence(ruleId: String, period: String)

    /**
     * **«No, ese movimiento no es esto»** — y que se acuerde.
     *
     * El rechazo vivía solo en la pantalla (`descartadas`), y mientras la app apenas PROPONÍA eso
     * alcanzaba: la propuesta volvía a ofrecerse y el dueño volvía a ignorarla, gratis. Desde que
     * Movi **empareja solo** cuando está seguro, un «no» que se olvida al recargar deja el periodo
     * dándose por ocurrido otra vez en la siguiente lectura, sin que nadie lo toque. Por eso ahora
     * el «no» viaja al server (ver [com.jvillada.movi.shared.model.RechazarOcurrenciaRequest]).
     *
     * La clave es **(regla, movimiento)**, nunca el movimiento solo: el pago del gas se propone en
     * «Agua», «Gas» e «Internet» —las tres en «Servicios»—, y decir que no en Agua no puede
     * quitárselo a Gas, que es donde sí era el bueno.
     *
     * Idempotente: repetirlo no falla ni cambia nada. 204 sin cuerpo.
     */
    suspend fun rechazarOcurrencia(ruleId: String, eventId: String)
    suspend fun chatAi(request: AiChatRequest): AiChatResponse
    suspend fun getAccounts(): List<Account>
    suspend fun getAccount(id: String): Account
    suspend fun createAccount(account: Account): Account

    /**
     * **Cuadra una cuenta con lo que dice el banco**: deja su saldo en [targetBalance] registrando
     * el movimiento de ajuste del lado del server.
     *
     * Es la hermana de [adjustCreditBalance] —misma mecánica, mismo constructor de eventos y misma
     * categoría reservada— para las cuentas de Dinero e Inversión. No hay dos formas de ajustar un
     * saldo: hay una, con dos puertas, porque una deuda se cuadra mirando su plan de pagos y una
     * cuenta de ahorros no.
     *
     * Manda el saldo OBJETIVO y no la diferencia: el saldo que tiene el cliente en pantalla puede
     * llegar viejo. Si ya coincidía, no se escribe nada y `adjustmentEvent` vuelve en null.
     */
    suspend fun adjustAccountBalance(accountId: String, targetBalance: Long): AdjustAccountBalanceResponse

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
     * Pagar la cuota de un crédito o el extracto de una tarjeta: **una acción, dos patas**. La
     * plata sale de una cuenta y la deuda baja. Ver [CreatePagoDeCuotaRequest] para por qué la
     * cuota de un crédito cuenta en los gastos del mes y el pago de una tarjeta no.
     */
    suspend fun payInstallment(request: CreatePagoDeCuotaRequest): PagoDeCuotaResult

    /**
     * Cambia la categoría de un movimiento ya registrado. Devuelve el [FinancialEvent]
     * actualizado con `countsAsCashFlow` derivado — el server lo recalcula, nunca lo toma del
     * cliente.
     */
    suspend fun updateEventCategory(id: String, category: String): FinancialEvent

    /**
     * **Corrige la fecha de un movimiento ya registrado** (`PUT /api/events/{id}/timestamp`).
     *
     * [timestamp] es epoch-ms, y quien llama tiene que armarlo con
     * `com.jvillada.movi.ui.fecha.epochAlMediodia` — el mediodía de Bogotá — y no con una
     * medianoche cualquiera: la hora del día decide en qué día cae el movimiento visto desde otra
     * zona (ver el KDoc de esa función).
     *
     * Lanza [ApiException] con 422 si la fecha todavía no llegó
     * ([com.jvillada.movi.shared.model.EVENT_DATE_IN_FUTURE]) y con 404 si el movimiento no
     * existe, es de otro usuario o está anulado. Si el movimiento es una pata de un traspaso, el
     * server mueve **las dos**: la fecha de un traspaso es un solo hecho.
     */
    suspend fun updateEventTimestamp(id: String, timestamp: Long): FinancialEvent

    /**
     * **«Este se repite» / «este no»**, sobre un movimiento concreto
     * (`PUT /api/events/{id}/repeats`).
     *
     * Movimientos reconoce recurrentes **por nombre**, así que un gasto suelto que se llama igual
     * que una suscripción quedaba marcado como recurrente sin puerta de vuelta. Esto es esa
     * puerta, y sirve en las dos direcciones. Ver
     * [com.jvillada.movi.shared.model.FinancialEvent.noSeRepite].
     *
     * **No toca la regla ni la suscripción**: el cobro mensual sigue existiendo. Es una anotación
     * sobre UNA fila.
     *
     * Lanza [ApiException] con 404 si el movimiento no existe, es de otro usuario o está anulado.
     */
    suspend fun updateEventRepeats(id: String, repeats: Boolean): FinancialEvent

    /**
     * **Confirmar un movimiento que entró solo** (`PUT /api/events/{id}/confirm`): pasa de
     * «Por confirmar» a confirmado.
     *
     * Hasta esto no había ninguna forma de hacerlo: lo que llegaba como `UNCONFIRMED` (un extracto,
     * una carga del banco) quedaba en «Por confirmar» para siempre, fuera de «Gastos» e
     * «Ingresos», y la hoja solo dejaba mirarlo. Idempotente: confirmar uno ya confirmado lo
     * devuelve igual. Una pata de traspaso confirma también a su par.
     *
     * Lanza [ApiException] con 404 si el movimiento no existe, es de otro usuario o está anulado.
     */
    suspend fun confirmEvent(id: String): FinancialEvent

    /**
     * **Los movimientos anotados en este teléfono que el server rechazó** y por qué. En la web no hay
     * nada que subir: lista vacía. Ver migración 9.sqm.
     */
    suspend fun getMovimientosRechazados(): List<MovimientoRechazado>

    /**
     * **Corrige el monto, la cuenta y el concepto de un movimiento ya registrado**
     * (`PUT /api/events/{id}`).
     *
     * Cada campo de [cambios] es nullable y `null` significa «no toques este» — ver
     * [EdicionDeMovimiento]. Devuelve el evento actualizado con `countsAsCashFlow` ya derivado:
     * mover un gasto a una cuenta de deuda lo saca de las cifras del mes, y quien llama tiene que
     * poder repintarlo sin volver a preguntar.
     *
     * Lanza [ApiException] con 400 (monto o concepto mal formados), 404 (el movimiento no existe,
     * es de otro usuario, está anulado, o la cuenta destino no es suya) y 422 (una pata de un par
     * intentando cambiar de cuenta, o una cuenta de otra moneda). El texto del error es el que se
     * le muestra al dueño tal cual.
     *
     * **Si el movimiento es una pata de un par** (traspaso, pago de cuota, pago de tarjeta), el
     * **monto se cambia en las dos mitades** — es un solo hecho con dos anotaciones — y la
     * **cuenta no se puede cambiar** (ver [PATA_NO_CAMBIA_DE_CUENTA]).
     */
    suspend fun updateEvent(id: String, cambios: EdicionDeMovimiento): FinancialEvent

    /**
     * La marca de «esto ya ocurrió» que algún recurrente puso sobre este movimiento, o `null` si
     * no hay ninguna (`GET /api/events/{id}/occurrence`, que responde 204 en ese caso).
     *
     * Lo usa la hoja que corrige la fecha para **avisar antes**: si la fecha nueva se sale de la
     * ventana que sostiene el sello, ese periodo vuelve a quedar pendiente, y eso hay que decirlo
     * antes y no después. No lanza por falta de red — un aviso que no se pudo cargar no puede
     * impedir corregir una fecha —, devuelve `null`.
     */
    suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark?

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

    /**
     * **Los otros movimientos del mismo destinatario que están en otra categoría**
     * (`GET /api/events/{id}/parecidos`), para poder arreglarlos todos de una en vez de uno por
     * uno — que es justo lo que nadie hace, y por eso «Otros» se queda ahí.
     *
     * Quién es «el mismo destinatario» lo decide el server con
     * [com.jvillada.movi.shared.model.huellaDeUnMovimiento]: la llave del pago QR, el número de
     * cuenta, el nombre del comercio sin el arranque del banco. Un movimiento cuyo texto no
     * identifica a nadie («Pago QR» a secas) **no tiene parecidos**, y la lista vuelve vacía.
     *
     * Lista vacía es la respuesta normal, no un error: quiere decir que no hay nada que ofrecer.
     */
    suspend fun getParecidos(id: String): List<FinancialEvent>

    /**
     * Pone la misma categoría a varios movimientos de una
     * (`PUT /api/events/category-en-lote`), normalmente los que propuso [getParecidos] y el dueño
     * confirmó.
     *
     * El server revalida cada id con las mismas reglas que [updateEventCategory] —un lote no es
     * una puerta de atrás a las categorías reservadas— y **omite** lo que no se puede mover (una
     * pata de traspaso, una apertura, un anulado) en vez de tumbar el lote entero. Por eso la
     * respuesta trae los dos números.
     */
    suspend fun recategorizarEnLote(ids: List<String>, category: String): RecategorizarEnLoteResponse
    suspend fun register(request: RegisterRequest): AuthResponse
    suspend fun login(request: LoginRequest): AuthResponse

    /** F42 · F46: perfil editable — `GET /api/users/me`. `avatarColor` nunca llega `null`. */
    /** Renombra una cuenta. Ver [RenameAccountRequest]. */
    suspend fun renameAccount(id: String, name: String): Account

    /**
     * Marca (o desmarca) para qué se puede usar la plata de una cuenta que **ya existe**.
     *
     * Sin esto, `Account.condicionadaA` solo se podía escribir al crear la cuenta, y la cuenta
     * que motivó el campo —la pensión voluntaria del dueño— ya estaba creada. Ver
     * [com.jvillada.movi.shared.model.UpdateAccountConditionRequest].
     *
     * `null` o vacío = sin condición: esa plata vuelve a «Tu plata».
     */
    suspend fun updateAccountCondition(id: String, condicionadaA: String?): Account

    /**
     * Actualiza el valor (y la clase, la fecha del valor o la deuda asociada) de un **bien** que
     * ya existe — `PUT /api/accounts/{id}/bien`. Ver [com.jvillada.movi.shared.model.Bien].
     *
     * Se crea por [createAccount] con el bien adentro; esto es para cuando llega el avalúo nuevo.
     */
    suspend fun updateBien(id: String, bien: com.jvillada.movi.shared.model.Bien): Account

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

    // ── Documentos ─────────────────────────────────────────────────────────────

    /** Los papeles guardados, lo más reciente primero. **Metadatos, nunca bytes** — ver [Documento]. */
    suspend fun getDocuments(): List<Documento>

    /** Guarda un archivo. Devuelve sus metadatos ya con id. */
    suspend fun uploadDocument(
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        tipo: TipoDeDocumento,
        accountId: String? = null,
        periodo: String? = null,
        notas: String? = null,
    ): Documento

    /**
     * Pide el permiso para abrir un documento: una URL con vida corta.
     *
     * Devuelve la URL **absoluta**, lista para dársela al navegador o al sistema. El server
     * responde una ruta relativa porque no sabe con qué origen lo llamaron; componerla es
     * trabajo del cliente, que sí conoce su `baseUrl`.
     */
    suspend fun getDocumentLink(id: String): EnlaceDeDescarga

    /**
     * Corrige nombre, tipo, período o notas de un documento ya subido. Lo que vaya en `null` **no
     * se toca**; la cadena vacía sí borra. Ver [EdicionDeDocumento].
     */
    suspend fun updateDocument(id: String, cambios: EdicionDeDocumento): Documento

    suspend fun deleteDocument(id: String)
    suspend fun importStatement(decision: ImportDecision)
    suspend fun getStatementImports(): List<StatementImport>
    suspend fun getStatementImportDetail(id: String): StatementImportDetail

    /**
     * **Deshacer un importe** (`DELETE /api/statements/imports/{id}`): anula lo que el importe creó,
     * suelta lo que solo concilió y borra el registro. 404 si no existe.
     */
    suspend fun deleteStatementImport(id: String)
    suspend fun getScreen(slug: String, cachedVersion: Int? = null): ScreenDefinition?
    suspend fun putScreen(slug: String, sections: List<ScreenSection>): ScreenDefinition
    suspend fun restoreScreen(slug: String): ScreenDefinition
    suspend fun isScreenAdmin(): Boolean
}
