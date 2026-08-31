package com.jvillada.movi.shared.repository

import com.jvillada.movi.shared.db.MoviDatabase
import com.jvillada.movi.shared.model.isReservedCategory
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TipoDeDocumento
import com.jvillada.movi.shared.model.EnlaceDeDescarga
import com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest
import com.jvillada.movi.shared.model.PagoDeCuotaResult
import com.jvillada.movi.shared.model.Documento
import com.jvillada.movi.shared.model.EdicionDeDocumento
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.normalizarCondicion
import com.jvillada.movi.shared.model.AiChatRequest
import com.jvillada.movi.shared.model.AiChatResponse
import com.jvillada.movi.shared.model.AuthResponse
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.CategoryRewriteResult
import com.jvillada.movi.shared.model.CategoryUsage
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CardTerms
import com.jvillada.movi.shared.model.CreateCardRequest
import com.jvillada.movi.shared.model.CreateCreditRequest
import com.jvillada.movi.shared.model.CreateSubscriptionRequest
import com.jvillada.movi.shared.model.CreateTransferRequest
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY_RESERVED
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_NOT_MANUAL
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.OPENING_CATEGORY_RESERVED
import com.jvillada.movi.shared.model.OPENING_RECATEGORIZE_BLOCKED
import com.jvillada.movi.shared.model.orphanedLegDescription
import com.jvillada.movi.shared.model.TRANSFER_LEG_NOT_STANDALONE
import com.jvillada.movi.shared.model.TRANSFER_RECATEGORIZE_BLOCKED
import com.jvillada.movi.shared.model.TransferResult
import com.jvillada.movi.shared.model.transferLegsFor
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.shared.model.EventDay
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.masRecientePrimero
import com.jvillada.movi.shared.model.FinanceSummary
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.EVENT_DATE_IN_FUTURE
import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.model.Goal
import com.jvillada.movi.shared.model.ImportDecision
import com.jvillada.movi.shared.model.LoginRequest
import com.jvillada.movi.shared.model.newId
import com.jvillada.movi.shared.model.PasswordResetRequest
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.ReminderChannels
import com.jvillada.movi.shared.model.RegisterRequest
import com.jvillada.movi.shared.model.ScreenDefinition
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.RecurringOccurrence
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.StatementImport
import com.jvillada.movi.shared.model.StatementImportDetail
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.shared.model.Subscription
import com.jvillada.movi.shared.model.SubscriptionsResult
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.ChangePasswordRequest
import com.jvillada.movi.shared.model.UpdateProfileRequest
import com.jvillada.movi.shared.model.UserProfile
import com.jvillada.movi.shared.model.VoidEvent
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.signedDelta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.serialization.json.Json

/**
 * Cuánto se le da al server para contestar la lista de cuentas **cuando ya hay algo local que
 * mostrar**.
 *
 * El engine HTTP tiene `connectTimeoutMillis = 30_000` (ver `Platform.android.kt`), pensado para
 * un POST que no se puede perder. Para esta lectura ese número es al revés de lo que la app
 * promete: en una zona muerta o detrás de un portal cautivo, media pantalla quedaba media hora
 * de reloj esperando —y en la hoja de Agregar, sin cuenta seleccionada, «Falta la cuenta»— donde
 * master resolvía en microsegundos contra SQLite. Cinco segundos es la frontera entre «el server
 * está lento» y «no hay server»: pasado eso, lo local es la mejor respuesta que existe y se
 * contesta con eso. La lectura no se cancela por gusto en el otro caso: sin NADA local no hay
 * respuesta mejor que esperar.
 */
private const val PRESUPUESTO_DE_RED_MS = 5_000L

/** Fila de `account` (SQLDelight) → [Account] del modelo. */
private fun com.jvillada.movi.Account.toAccountModel() = Account(
    id = id, name = name,
    type = AccountType.valueOf(type),
    balance = balance, currency = currency,
    // Se normaliza también acá —y no solo al escribir— porque la fila local puede venir de una
    // base vieja migrada (columna NULL) o de una escritura de otra versión del cliente. Ver
    // [normalizarCondicion]: es la MISMA función que usan el server y la UI.
    condicionadaA = normalizarCondicion(conditionedTo),
)

class LocalRepository(
    private val db: MoviDatabase,
    private val remote: WalletRepository,
    private val userId: () -> String,
) : WalletRepository {

    // ── Accounts ──────────────────────────────────────────────────────────────

    /**
     * **Las cuentas del server también, no solo las que nacieron en este teléfono.**
     *
     * Antes esto leía SOLO SQLDelight, mientras el resto de la misma pantalla venía del server —
     * y el [com.jvillada.movi.shared.SyncEngine] solo empuja, nunca trae. Resultado: una cuenta
     * nacida en el server (creada en la web, sembrada por API, o anterior a esta instalación)
     * **nunca llegaba al teléfono**. De ahí salían tres pantallas mintiendo a la vez: Cuentas
     * decía «Sin cuentas aún» —invitando a crear un duplicado de una cuenta que ya existe—, el
     * Inicio decía «Sin cuentas aún» y «Balance neto $0», y la hoja de un recurrente decía «Sin
     * cuentas todavía» y al guardar borraba la cuenta de la regla (eso se arregla aparte, y por
     * separado, en [com.jvillada.movi.ui.recurrentes.cuentaParaElWire]).
     *
     * **Por qué acá y no en el SyncEngine.** Bajar cuentas en el ciclo de 30s deja la primera
     * pintada de cada pantalla igual de mentirosa que antes (hasta el próximo tick), y obliga a
     * inventar reglas de conflicto para nombre y saldo. Este camino es el patrón que el archivo
     * ya usa en la escritura —remoto primero, espejo local; ver [createAccount], [createCredit],
     * [createCard], [adjustCreditBalance] y [mirrorAccountLocally]— aplicado a la lectura: se
     * pregunta al server, se espeja lo que contestó, y lo local queda como respaldo.
     *
     * **La lista dice exactamente lo que el server tiene, más lo que este teléfono todavía no
     * pudo subir.** Espejar sin más dejaba un agujero peor que el original: una cuenta borrada
     * desde la web sobrevivía como fila local **para siempre** —con red perfecta y con un GET
     * exitoso que no la devolvía—, seguía sumando al «Balance neto», el selector del recurrente
     * la ofrecía, y elegirla ahí volvía a perder la cuenta de la regla (el server nulea un id que
     * no conoce). Un fantasma así ni siquiera se podía sacar: «Eliminar cuenta» es remote-first y
     * el 404 del server dejaba la fila local en pie.
     *
     * La regla que lo cierra es de una línea: **se oculta la cuenta que ya estaba sellada ANTES
     * de preguntar y que el server no devolvió.** Sellada (`syncedAt != null`) significa «el
     * server la conoce»; si preguntamos y no vino, ya no existe allá. Nada creado o sellado
     * DESPUÉS del GET se filtra —por eso el fotograma se toma antes de la llamada y no después—,
     * así que ni la cuenta que el dueño acaba de crear ni la que el `SyncEngine` selló mientras
     * el GET estaba en vuelo pueden parpadear fuera de la lista.
     *
     * **No se borra ningún dato**, solo se deja de mostrar: la fila sobrevive con sus eventos
     * locales, y esa es toda la diferencia con [deleteAccount] —que sí cascadea patas hermanas,
     * voids y eventos, y que no tendría por qué correr desde un camino de LECTURA, donde una
     * respuesta corta se volvería pérdida de datos—. Lo que sí se agregó del lado del borrado es
     * que un 404 se trate como éxito (ver [deleteAccount]): así, si alguna vez un fantasma queda
     * a la vista, el dueño puede sacarlo.
     *
     * **Sin red, y con red mala.** Si `remote.getAccounts()` falla se devuelve lo local tal cual
     * —incluye lo espejado en la última lectura con red, así que el teléfono sigue mostrando las
     * cuentas del server en modo avión—. Y como el engine HTTP tiene 30 s de `connectTimeout`
     * (ver `Platform.android.kt`), una zona muerta o un portal cautivo bloqueaban media pantalla
     * medio minuto donde antes se resolvía en microsegundos: con algo local ya en la base, la
     * espera se corta en [PRESUPUESTO_DE_RED_MS] y se contesta con lo que hay. Solo cuando no hay
     * NADA local se espera lo que haga falta, porque ahí no existe una respuesta mejor.
     *
     * Lo único que no se hace es afirmar «no tienes cuentas» cuando no se pudo preguntar Y no hay
     * nada local: ahí la excepción se propaga para que la pantalla muestre su reintento (ver
     * `AccountsScreen`, que además ya no pinta el estado vacío mientras no haya una lista de
     * verdad).
     *
     * **No duplica.** El espejo es el `INSERT OR REPLACE` de [mirrorAccountLocally] y la PK es el
     * id, que es el mismo de los dos lados (los ids los genera el cliente con `newId`, y los que
     * nacieron en el server vienen en la respuesta). Una cuenta que ya estaba local se pisa, no
     * se agrega.
     *
     * **El saldo sigue teniendo una sola fuente.** Para las cuentas que el server conoce se
     * devuelve el objeto que el server mandó —saldo derivado de los eventos, más
     * `balancesByCurrency`/`estimatedTotalCop`, que la columna local no sabe guardar—, igual que
     * ya hacen Inicio y Presupuestos. La columna local `accounts.balance` se refresca con ese
     * valor y queda solo como respaldo sin red. Precio conocido: un movimiento anotado offline
     * mueve el saldo local, y apenas hay red este GET muestra el del server —que todavía no lo
     * tiene— hasta que el `SyncEngine` lo empuje y una lectura posterior lo refleje. Con red
     * intermitente eso puede tardar varios ciclos de 30 s, no uno; lo que no puede es quedarse
     * así para siempre, que es lo que hacía la columna local de master (nunca se reconciliaba
     * con nada). La alternativa —mezclar el delta local con el saldo del server— sería la segunda
     * fuente de verdad que no queremos.
     *
     * **El orden lo sigue poniendo SQLDelight** (`ORDER BY lower(name), id`, ver `Account.sq`),
     * que es el mismo criterio que `GET /api/accounts` — el emparejamiento entre el teléfono y
     * la web se mantiene y no se reordena nada en Kotlin.
     */
    override suspend fun getAccounts(): List<Account> {
        val uid = userId()
        // El fotograma va ANTES de preguntar: es lo que hace que la regla de abajo solo pueda
        // ocultar cuentas por las que el server ya fue consultado. Ver el KDoc.
        val filasAntesDePreguntar = db.accountQueries.selectAll(uid).executeAsList()
        val selladasAntesDePreguntar = filasAntesDePreguntar
            .filter { it.syncedAt != null }
            .mapTo(mutableSetOf()) { it.id }
        val habiaAlgoLocal = filasAntesDePreguntar.isNotEmpty()

        val remotas = try {
            if (habiaAlgoLocal) {
                withTimeoutOrNull(PRESUPUESTO_DE_RED_MS) { remote.getAccounts() }
                    ?: return leerCuentasLocales(uid)
            } else {
                remote.getAccounts()
            }
        } catch (e: CancellationException) {
            // La pantalla se fue mientras el request estaba en vuelo. Tragarlo haría que el
            // `runCatching` del llamador lo leyera como un éxito con lista vacía.
            throw e
        } catch (e: Exception) {
            val locales = leerCuentasLocales(uid)
            // Vacío + no se pudo preguntar = no se sabe, y «no tienes cuentas» sería una
            // afirmación sin respaldo. Con algo local, eso es la mejor respuesta que hay.
            if (locales.isEmpty()) throw e
            return locales
        }

        db.transaction { remotas.forEach { mirrorAccountLocally(it) } }
        val porId = remotas.associateBy { it.id }
        val fantasmas = selladasAntesDePreguntar - porId.keys
        // El orden sale de la DB; el contenido de cada cuenta que el server conoce, del server;
        // y las que el server ya no tiene se dejan de mostrar (sin borrarles nada).
        return leerCuentasLocales(uid)
            .filterNot { it.id in fantasmas }
            .map { porId[it.id] ?: it }
    }

    /** Las cuentas de la DB local, en el orden de `Account.sq`. Respaldo sin red de [getAccounts]. */
    private fun leerCuentasLocales(uid: String): List<Account> =
        db.accountQueries.selectAll(uid).executeAsList().map { it.toAccountModel() }

    /**
     * Mismo criterio que [getAccounts]: el server primero, la fila local como respaldo.
     *
     * Antes esto era un `executeAsOne()` sobre SQLDelight, o sea que abrir el detalle de una
     * cuenta que nació en el server **tiraba una excepción** — y era alcanzable de verdad:
     * `CreditosScreen` navega al detalle de la cuenta de un crédito, y un crédito creado en la
     * web no tenía fila local. Hoy [getAccounts] la espeja apenas se lista, pero el detalle
     * también se alcanza sin pasar por la lista, así que la garantía tiene que estar acá y no
     * depender del orden de las pantallas. Se espeja lo que contesta el server por lo mismo que
     * en [getAccounts]: el saldo derivado de eventos es el bueno.
     *
     * El respaldo local es a propósito **más permisivo** que el de [getAccounts]: acá el dueño
     * pidió una cuenta puntual y mostrarle lo último que se supo de ella es mejor que un error.
     * Un 404 no la esconde —para eso está el filtro de la lista, que es donde el fantasma hacía
     * daño (sumaba al patrimonio y se podía elegir en un recurrente)—.
     */
    override suspend fun getAccount(id: String): Account =
        try {
            remote.getAccount(id).also { mirrorAccountLocally(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            db.accountQueries.selectById(id).executeAsOneOrNull()?.toAccountModel() ?: throw e
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
     * `account.balance` acá es lo que el llamador mande — esta función no decide si la cuenta
     * "arranca con plata". Esa decisión, y quién crea el movimiento de apertura, es de
     * [com.jvillada.movi.ui.accounts.CreateAccountSheet] (único call site en la UI, Ola 1b): crea
     * la cuenta en $0 y después postea el evento de apertura aparte, vía [postEvent], si el dueño
     * declaró un saldo/deuda inicial. El server (`AccountRoutes.kt` POST) YA NO fabrica ese
     * evento a partir del balance — antes lo hacía, y era la mitad server de un doble conteo: una
     * cuenta offline con un ingreso real anotado antes del primer sync sincroniza acá con
     * `row.balance` ya movido por ese ingreso ([com.jvillada.movi.shared.SyncEngine.syncAccounts]
     * manda ese valor tal cual); si el server lo hubiera vuelto a convertir en un evento de
     * apertura, el ingreso real que `syncEvents` empuja justo después se sumaba ENCIMA de esa
     * apertura fabricada — el doble del saldo real, silencioso y permanente. Ver el KDoc de
     * [com.jvillada.movi.shared.model.openingEventFor] para el detalle completo del escenario.
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
                created.condicionadaA,
            )
            created
        } catch (e: Exception) {
            db.accountQueries.insert(
                resolved.id, resolved.name, resolved.type.name,
                resolved.balance, resolved.currency, uid,
                null,
                resolved.condicionadaA,
            )
            resolved
        }
    }

    /**
     * F55: a diferencia de [createAccount] arriba, ACÁ no hay fallback local silencioso ante un
     * fallo de red. Un borrado que solo pasara local sería un borrado a medias: la fila local
     * quedaría con `syncedAt` no-nulo (nunca "pendiente de subir", porque no es una creación),
     * así que [com.jvillada.movi.shared.SyncEngine] jamás la volvería a intentar — y la próxima
     * vez que el server devolviera esa cuenta en un GET (porque en el server nunca se borró),
     * la app la resucitaría con todos sus movimientos, como si el borrado nunca hubiera pasado.
     * Eso es peor que no borrar nada: el dueño cree que borró y en algún momento la cuenta
     * vuelve a aparecer sola.
     *
     * Por eso: `remote.deleteAccount` primero, y si falla (sin red o el server rechaza) la
     * excepción se propaga tal cual — no se atrapa acá — para que la UI la traduzca a un
     * mensaje claro ("No se pudo eliminar — revisa tu conexión", ver `AccountDetailScreen`) en
     * vez de fingir que el borrado ocurrió.
     *
     * **Con una excepción: el 404 se sella como éxito**, igual que el 409 en
     * [com.jvillada.movi.shared.SyncEngine.syncVoids]. «Esa cuenta no existe en el server» es
     * exactamente el estado al que este borrado quería llegar, y tratarlo como falla dejaba al
     * dueño encerrado: una cuenta borrada desde la web y todavía espejada acá contestaba 404, la
     * app decía «revisa tu conexión» con la conexión perfecta, y la fila local no había forma de
     * sacarla salvo borrando los datos de la app. Hoy [getAccounts] ni siquiera la muestra, pero
     * la salida tiene que existir igual: es la que hace que un fantasma no pueda ser permanente.
     */
    override suspend fun deleteAccount(id: String) {
        try {
            remote.deleteAccount(id)
        } catch (e: ApiException) {
            if (e.status != 404) throw e
        }
        val uid = userId()
        db.transaction {
            // Lo mismo que acaba de hacer el server, con las mismas palabras (ver
            // `desenlazarPatasHermanas` en AccountRoutes.kt): la pata hermana de cada traspaso
            // vive en OTRA cuenta, sobrevive al borrado y deja de ser media pareja. Se espeja acá
            // porque el SyncEngine solo empuja —nada baja del server— y todas las pantallas del
            // teléfono leen de esta base: sin esto, el celular se quedaría para siempre con un
            // «Traspaso a Nequi» enlazado a una hermana que ya no existe, mientras el server
            // muestra otra cosa.
            db.financialEventQueries.selectByAccount(id, uid).executeAsList()
                .mapNotNull { it.transferId }
                .toSet()
                .forEach { transferId ->
                    db.financialEventQueries.selectByTransferId(transferId, uid).executeAsList()
                        .filter { it.accountId != id }
                        .forEach { hermana ->
                            db.financialEventQueries.detachOrphanedLeg(
                                category    = ORPHANED_LEG_CATEGORY,
                                description = orphanedLegDescription(hermana.description),
                                id          = hermana.id,
                                userId      = uid,
                            )
                        }
                }
            // Los voids ANTES que los eventos: la subconsulta de deleteByAccount los encuentra
            // por el accountId de sus eventos, que después de la línea siguiente ya no existen.
            db.voidEventQueries.deleteByAccount(id, uid)
            db.financialEventQueries.deleteByAccount(id, uid)
            db.accountQueries.deleteById(id)
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    override suspend fun postEvent(event: FinancialEvent): FinancialEvent {
        // La guarda simétrica de la del server (`POST /api/events` rechaza esto con 422), y hace
        // falta acá porque el espejo local escribe PRIMERO y pregunta después. Sin ella el daño
        // era silencioso y permanente: `isCashFlow` deja fuera del mes a cualquier evento con la
        // categoría reservada —o sea, el gasto REAL del dueño desaparecía de Movimientos y de
        // Presupuestos en el teléfono—, el `SyncEngine` lo empujaba, el server contestaba 422, el
        // catch se lo tragaba y la fila se reintentaba cada 30 segundos para siempre.
        //
        // Y era alcanzable sin mala fe: Movimientos y Presupuestos metían TODAS las categorías
        // que veían en el caché de sugerencias, patas de traspaso incluidas, así que «Traspaso»
        // se le ofrecía al dueño para escribirla (eso también se corrigió, en `UsedCategoriesCache`).
        if (event.category == TRANSFER_CATEGORY || event.transferId != null) {
            throw ApiException(422, TRANSFER_LEG_NOT_STANDALONE)
        }
        // Red de seguridad, no la vía principal: la UI ya manda `id = newId("ev")` en los tres
        // call sites (QuickAddScreen, SMSScreens; CreateAccountSheet es para cuentas, no
        // eventos). Nunca insertar con PK "" — con INSERT OR REPLACE, un segundo evento sin id
        // reemplazaría al primero en vez de agregarse, y si el segundo llegaba a sincronizarse
        // antes que el ciclo de 30s levantara al primero, este último nunca subía: el teléfono y
        // el server terminaban mostrando movimientos distintos.
        val conId = if (event.id.isBlank()) event.copy(id = newId("ev")) else event
        // **Acá nace el sello de creación**, y no cuando el evento llegue al server: este es el
        // instante real en que el dueño escribió el movimiento. El teléfono puede estar sin señal
        // y el `SyncEngine` empujarlo dos días después — si lo sellara el server al recibir, el
        // movimiento saltaría al tope de su día por haber viajado tarde. Ver
        // FinancialEvent.createdAt. Si el llamador ya trajo uno (un evento que vino del server,
        // por ejemplo), se respeta.
        val resolved = conId.copy(createdAt = conId.createdAt ?: Clock.System.now().toEpochMilliseconds())
        db.transaction {
            db.financialEventQueries.insert(
                resolved.id, resolved.accountId, resolved.type.name, resolved.amount,
                resolved.category, resolved.description, resolved.merchant,
                resolved.timestamp, resolved.source.name, resolved.rawPayload,
                resolved.reconciliationStatus.name, resolved.syncedAt, userId(),
                // Siempre null por esta puerta: un traspaso entra por [createTransfer], nunca
                // como evento suelto (el server rechaza un POST /api/events con transferId).
                resolved.transferId,
                resolved.createdAt,
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

    /**
     * Espejo local de `GET /api/events`, **con su mismo orden**.
     *
     * El `ORDER BY timestamp DESC` de `selectByAccount`/`selectAll` ya traía lo más nuevo
     * arriba, pero **no desempata**: dos movimientos del mismo milisegundo —las dos patas de un
     * traspaso, o un lote de SMS— salían de SQLite en un orden que nadie promete, así que podían
     * intercambiarse entre lecturas. El comparador compartido cierra ese hueco con el mismo
     * criterio que el server, y no en la consulta a propósito: ver [masRecientePrimero].
     */
    /**
     * **Server primero, espejo local después** — mismo trato que [getAccounts], y por el mismo
     * motivo, que hasta ahora los movimientos no tenían.
     *
     * El dueño anotó un movimiento desde el teléfono y en Movimientos le figuró **ese solo
     * renglón**: preguntó si había perdido su salario y sus gastos del mes. No había perdido
     * nada — estaban los 18 en el server. Lo que pasaba es que esta función leía **solo** la
     * base local, y el [com.jvillada.movi.shared.SyncEngine] **solo empuja**: nada de lo que él
     * escribía en la web bajaba nunca al teléfono, ni iba a bajar.
     *
     * Media app preguntaba y la otra media no: `getAccounts` ya era server-primero (por eso sus
     * cuentas sí se veían bien) y esto no. Varias piezas del repo existen para tapar ese hueco de
     * a una —`detachOrphanedLeg`, `renameCategory`, y sus KDoc dicen textualmente «hace falta
     * porque el SyncEngine solo EMPUJA»—; esta es la causa que las hacía necesarias.
     *
     * Tres reglas, y ninguna es opcional:
     *
     * 1. **Lo que este teléfono escribió y todavía no subió se sigue viendo.** Un gasto anotado
     *    sin señal no puede desaparecer de la pantalla porque la lista ahora salga del server.
     *    Son las filas sin sellar, y salen del espejo local, no de la respuesta remota.
     * 2. **Lo anulado acá no se muestra, aunque el server todavía lo devuelva.** Ver
     *    `selectAllVoidedIds`: entre la anulación y su empuje hay una ventana en la que el server
     *    sigue conociendo el evento. Sin este filtro el movimiento reaparecería solo y se iría de
     *    nuevo al rato.
     * 3. **Sin red se contesta con lo local**, que es el modo normal en el bus — no un caso raro.
     *    Solo cuando no hay NADA local se propaga el error: contestar «no tienes movimientos» sin
     *    haber podido preguntar es una afirmación sin respaldo.
     *
     * La regla de los fantasmas es la de [getAccounts], con la misma foto previa: una fila que
     * estaba **sellada** y que el server ya no tiene se dejó de mostrar, porque se borró en otro
     * lado. Una fila sin sellar nunca es fantasma — es algo que falta subir.
     */
    /**
     * Renombrar pasa por el server y se espeja local, igual que el resto de las escrituras de
     * cuenta: sin el espejo, el nombre viejo se seguiría viendo en el teléfono hasta la próxima
     * lectura con red.
     */
    /** El descuento nace en el server (es idempotente allá) y se espeja como cualquier ajuste. */
    override suspend fun registerPayrollDeduction(accountId: String): CreditSummary {
        val summary = remote.registerPayrollDeduction(accountId)
        mirrorAccountLocally(summary.account)
        summary.adjustmentEvent?.let { evento ->
            db.transaction { mirrorEventLocally(evento, userId()) }
        }
        return summary
    }

    override suspend fun renameAccount(id: String, name: String): Account {
        val actualizada = remote.renameAccount(id, name)
        db.transaction { mirrorAccountLocally(actualizada) }
        return actualizada
    }

    /**
     * Igual que [renameAccount]: el server manda y la fila local se pisa con lo que contestó.
     *
     * **El espejo no es cosmético acá**, es la mitad del arreglo. Sin él —y sin la columna
     * `account.conditionedTo`— el Inicio volvía a sumar la plata condicionada apenas `getAccounts`
     * contestaba con lo local, que pasa en modo avión y también cuando la red tarda más que
     * `PRESUPUESTO_DE_RED_MS`. Sin red no hay fallback local: marcar una condición solo en el
     * teléfono sería un dato que el `SyncEngine` nunca empujaría (no es una creación, así que no
     * entra en `selectUnsynced`) y que la próxima lectura con red borraría en silencio.
     */
    override suspend fun updateAccountCondition(id: String, condicionadaA: String?): Account {
        val actualizada = remote.updateAccountCondition(id, condicionadaA)
        db.transaction { mirrorAccountLocally(actualizada) }
        return actualizada
    }

    override suspend fun getEvents(accountId: String?): List<FinancialEvent> {
        val uid = userId()
        // La foto va ANTES de preguntar, igual que en getAccounts: es lo que hace que la regla
        // de los fantasmas solo pueda ocultar filas por las que el server ya fue consultado.
        // La foto va sobre TODAS las filas, no solo las de la cuenta pedida: la respuesta remota
        // ahora es la lista completa, así que comparar contra un subconjunto marcaría como
        // fantasma a cualquier fila de otra cuenta.
        val filasAntesDePreguntar = leerFilasLocales(uid, null)
        val selladasAntesDePreguntar = filasAntesDePreguntar
            .filter { it.syncedAt != null }
            .mapTo(mutableSetOf()) { it.id }
        val habiaAlgoLocal = filasAntesDePreguntar.isNotEmpty()

        // **Siempre se pide la lista COMPLETA**, incluso cuando el llamador quiere una sola
        // cuenta. Antes se pedía filtrada y eso obligaba a apagar la regla anti-fantasma en ese
        // caso —«no vino» podía significar «está en otra cuenta»—, con la consecuencia de que un
        // movimiento borrado en la web desaparecía de Movimientos y **seguía viéndose para
        // siempre en el detalle de la cuenta**. Dos pantallas de la misma app contando cosas
        // distintas.
        //
        // Pidiendo todo y filtrando acá, la regla vale igual en las dos y no hay caso especial
        // que recordar. No cuesta un viaje extra: el server devuelve el conjunto entero de todos
        // modos (ver loadNonVoidedEvents), así que la respuesta filtrada nunca fue más barata.
        val remotos = try {
            if (habiaAlgoLocal) {
                withTimeoutOrNull(PRESUPUESTO_DE_RED_MS) { remote.getEvents(null) }
                    ?: return leerEventosLocales(uid, accountId)
            } else {
                remote.getEvents(null)
            }
        } catch (e: CancellationException) {
            // La pantalla se fue mientras el request estaba en vuelo (mismo caso que getAccounts):
            // tragarlo haría que el `runCatching` del llamador lo leyera como éxito con lista vacía.
            throw e
        } catch (e: Exception) {
            val locales = leerEventosLocales(uid, accountId)
            if (locales.isEmpty()) throw e
            return locales
        }

        // **Solo se escribe lo que cambió.** Antes se reescribían las N filas en cada lectura,
        // aunque nada se hubiera movido: medido, 5.000 eventos costaban ~272 ms y la segunda
        // lectura costaba lo mismo que la primera, porque el trabajo no dependía de si algo había
        // cambiado. Y son TRES pantallas las que disparan esto (Movimientos, Presupuestos y el
        // detalle de cuenta), así que el costo se paga varias veces por visita.
        //
        // Con 23 movimientos no se nota; con dos años de SMS del banco, sí. Comparar contra la
        // foto que ya se leyó es gratis: las filas están en memoria desde el arranque de esta
        // misma función.
        val localesPorId = filasAntesDePreguntar.associateBy { it.id }
        val cambiados = remotos.filter { remoto -> difiereDeLoGuardado(remoto, localesPorId[remoto.id]) }
        if (cambiados.isNotEmpty()) {
            db.transaction { cambiados.forEach { mirrorEventLocally(it, uid) } }
        }
        val porId = remotos.associateBy { it.id }
        // La regla de los fantasmas ya corre igual pidan una cuenta o todas, porque arriba
        // siempre se pide el conjunto entero: «no vino» solo puede significar «se anuló o se
        // borró en otro lado».
        //
        // Lo que sigue sin aplicarse es sobre una respuesta VACÍA: si el server contesta 200 sin
        // nada mientras el teléfono tiene filas selladas, la explicación más probable no es que el
        // dueño haya borrado su historia entera, sino un filtro nuevo, un `uid` mal resuelto o un
        // cambio de alcance del endpoint. Ante la duda se muestra de más — una lista vacía no es
        // evidencia suficiente para hacer desaparecerle el mes a alguien.
        val fantasmas = if (remotos.isEmpty()) emptySet() else selladasAntesDePreguntar - porId.keys
        // El contenido de lo que el server conoce sale del server; lo que solo existe acá (todavía
        // sin subir) sale del espejo; y lo que el server ya no tiene se deja de mostrar.
        return leerEventosLocales(uid, accountId)
            .filterNot { it.id in fantasmas }
            // Misma regla que en el espejo, y por el mismo motivo: **una fila sin sellar gana**.
            // Sin sello significa que el teléfono tiene algo que el server todavía no sabe —una
            // recategorización, una fecha corregida—, así que devolver la versión remota le
            // mostraría al dueño el valor viejo que él acaba de cambiar.
            //
            // Para las selladas manda el server, que es la autoridad, salvo `syncedAt`: ese es un
            // dato del espejo —«¿esta fila ya se subió?»— que la respuesta remota no siempre trae.
            // Dejar que el nulo del server pisara el sello local haría que el SyncEngine volviera
            // a empujar todo lo que acaba de bajar, en un ciclo eterno de 30 segundos.
            .map { local ->
                if (local.syncedAt == null) local
                else porId[local.id]?.copy(syncedAt = local.syncedAt) ?: local
            }
            .masRecientePrimero()
    }


    /**
     * ¿Vale la pena reescribir esta fila?
     *
     * Compara solo lo que el server manda y el espejo guarda. `syncedAt` queda fuera a propósito:
     * es un dato local —«¿ya se subió?»— que la respuesta remota no siempre trae, así que
     * incluirlo marcaría como distinta a cada fila en cada lectura y anularía el ahorro.
     *
     * Una fila que no está localmente siempre «difiere»: hay que escribirla.
     */
    private fun difiereDeLoGuardado(
        remoto: FinancialEvent,
        local: com.jvillada.movi.Financial_event?,
    ): Boolean {
        if (local == null) return true
        return remoto.accountId != local.accountId ||
            remoto.type.name != local.type ||
            remoto.amount != local.amount ||
            remoto.category != local.category ||
            remoto.description != local.description ||
            remoto.merchant != local.merchant ||
            remoto.timestamp != local.timestamp ||
            remoto.source.name != local.source ||
            remoto.rawPayload != local.rawPayload ||
            remoto.reconciliationStatus.name != local.reconciliationStatus ||
            remoto.transferId != local.transferId ||
            remoto.createdAt != local.createdAt
    }

    /** Las filas crudas del espejo, sin mapear — para la foto previa a preguntar. */
    private fun leerFilasLocales(uid: String, accountId: String?) =
        if (accountId != null) db.financialEventQueries.selectByAccount(accountId, uid).executeAsList()
        else db.financialEventQueries.selectAll(uid).executeAsList()

    /**
     * El espejo local ya ordenado y **sin lo anulado en este dispositivo**.
     *
     * Ese filtro arregla además un defecto propio del modo local, anterior a este cambio:
     * [voidEvent] ajusta el saldo pero **no borra la fila**, así que un movimiento anulado en el
     * teléfono se seguía listando.
     */
    private fun leerEventosLocales(uid: String, accountId: String?): List<FinancialEvent> {
        val types = accountTypes(uid)
        val anulados = db.voidEventQueries.selectAllVoidedIds().executeAsList().toSet()
        return leerFilasLocales(uid, accountId)
            .filterNot { it.id in anulados }
            .map { it.toModel(types) }
            .masRecientePrimero()
    }

    override suspend fun getEventsByDay(): List<EventDay> =
        getEvents()
            // Explícito, aunque [getEvents] ya venga ordenado: es el mismo lugar donde lo hace
            // `GET /api/events/by-day`, y el día que alguien cambie de dónde salen los eventos
            // el orden de la pantalla no se cae con el cambio. `groupBy` conserva el orden de
            // llegada dentro de cada grupo, así que esto es lo que ordena cada día por dentro.
            .masRecientePrimero()
            .groupBy { epochMillisToDate(it.timestamp) }
            .map { (date, items) ->
                EventDay(
                    // Mismo criterio que el server (ver EventRoutes /by-day): el total del día
                    // es flujo de caja, así que los movimientos de cuentas de deuda no entran.
                    // El renglón se sigue listando; solo no encabeza el día.
                    date = date,
                    // `currency == "COP"` NO es de adorno: es la mitad del criterio que usa el
                    // server (ver `/api/events/by-day`) y acá faltaba. Mientras el espejo local
                    // solo tenía lo que este teléfono había escrito no se notaba; desde que baja
                    // lo del server, un gasto en dólares entraba al total como si fueran pesos —
                    // con un COP de $10.000 y un USD de 100, el server decía −10.000 y el
                    // teléfono −10.100. La columna `currency` ni siquiera existe en el espejo
                    // (todo se lee como COP), así que sin este filtro la diferencia es invisible.
                    total = items.filter { it.currency == "COP" && it.countsAsCashFlow }.sumOf {
                        if (it.type == TransactionType.INCOME) it.amount else -it.amount
                    },
                    items = items,
                )
            }
            .sortedByDescending { it.date }

    /**
     * Anula un movimiento en el espejo local y deja la anulación encolada para el `SyncEngine`.
     *
     * **Si es una pata de traspaso, anula también la otra.** Un traspaso anulado a medias deja el
     * saldo de una de las dos cuentas mintiendo: la plata saldría de Ahorros sin volver del CDT
     * hasta el próximo arranque de la app.
     *
     * De las dos anulaciones locales, **solo una se encola** (`syncedAt = null`) — la del evento
     * que el dueño tocó; la de la hermana se escribe ya sellada. El server cascadea por su cuenta
     * (ver `POST /api/events/{id}/void`), así que empujar las dos haría que la segunda chocara
     * contra un 409 "Already voided" eterno: quedaría sin sellar y el ciclo de 30s la reintentaría
     * para siempre, ensuciando el log con un error que no significa nada.
     */
    override suspend fun voidEvent(id: String, reason: String?): VoidEvent {
        val now = Clock.System.now().toEpochMilliseconds()
        val voidId = "${now}_${id.take(8)}"
        val uid = userId()
        db.transaction {
            val event = db.financialEventQueries.selectById(id, uid).executeAsOneOrNull()
            val hermanas = event?.transferId
                ?.let { db.financialEventQueries.selectByTransferId(it, uid).executeAsList() }
                ?.filter { it.id != id }
                .orEmpty()

            db.voidEventQueries.insert(voidId, id, reason, now, null)
            hermanas.forEach { hermana ->
                // syncedAt = now: esta anulación NO se empuja, el server la deduce del transferId.
                db.voidEventQueries.insert("${now}_${hermana.id.take(8)}", hermana.id, reason, now, now)
            }

            (listOfNotNull(event) + hermanas).forEach { fila ->
                val acct = db.accountQueries.selectById(fila.accountId).executeAsOneOrNull() ?: return@forEach
                // Reversa exacta de signedDelta (mismo hallazgo que postEvent, arriba):
                // anular un evento en una cuenta LOAN/CREDIT_CARD tiene que deshacer el
                // efecto con la convención de deuda, no con la de cuenta de activo.
                val accountType = AccountType.valueOf(acct.type)
                val originalDelta = signedDelta(accountType, TransactionType.valueOf(fila.type), fila.amount)
                db.accountQueries.updateBalance(acct.balance - originalDelta, acct.id)
            }
        }
        return VoidEvent(id = voidId, originalEventId = id, reason = reason, timestamp = now)
    }

    /**
     * Crea el traspaso **contra el server** y espeja las dos patas en la DB local, ya selladas.
     *
     * Remote-first sin respaldo offline, a diferencia de [postEvent] y [createAccount] — y es una
     * decisión, no un olvido. La atomicidad de las dos patas vive en la transacción de
     * `POST /api/transfers`, y el [com.jvillada.movi.shared.SyncEngine] empuja eventos **de a
     * uno**: un traspaso anotado sin red podía llegar por mitades al server (una pata sí, la otra
     * en el próximo ciclo o nunca), que es exactamente el saldo mintiendo que esta feature vino a
     * evitar. Mismo criterio que [deleteAccount]: la excepción se propaga tal cual para que la UI
     * la traduzca a un mensaje claro en vez de fingir que el traspaso ocurrió.
     *
     * El espejo escribe **lo que devolvió el server** (`result.from`/`result.to`), no las patas
     * reconstruidas acá: mismo criterio que [adjustCreditBalance]. Y las escribe con
     * `syncedAt = ahora` — ya están en el server, no hay nada pendiente de empujar; además
     * `selectUnsynced` deja fuera cualquier fila con `transferId` justamente para que este ciclo
     * no pueda subir una pata suelta.
     */
    override suspend fun createTransfer(request: CreateTransferRequest): TransferResult {
        val result = try {
            remote.createTransfer(request)
        } catch (e: ApiException) {
            if (e.status != 409) throw e
            // 409 = «ese traspaso ya está registrado». Este server ya no lo usa (relee y devuelve
            // 200 con las patas reales), pero un server anterior todavía desplegado sí, y significa
            // lo mismo: las dos patas existen. Sin este rescate, la excepción salía ANTES del
            // espejo y el traspaso quedaba invisible en el teléfono
            // **para siempre** — el SyncEngine solo empuja, nunca trae, y Movimientos/Cuentas/el
            // detalle leen de acá. La app decía «guardado», refrescaba, y no había nada; solo
            // Inicio (que lee remoto) lo contaba. El teléfono contradiciéndose a sí mismo, y el
            // dueño rehaciendo el traspaso con ids nuevos: el duplicado que todo esto evita.
            //
            // Las patas se reconstruyen con `transferLegsFor`, la MISMA función que usó el server
            // (vive en :core justamente para eso): mismos ids, mismo monto, misma marca de tiempo,
            // misma descripción. No es una adivinanza — es la única cosa que el server pudo haber
            // guardado con este request. Si alguna de las dos cuentas no está local, no hay con
            // qué construirlas y el error sale tal cual.
            val from = localAccount(request.fromAccountId) ?: throw e
            val to = localAccount(request.toAccountId) ?: throw e
            val (fromLeg, toLeg) = transferLegsFor(request, from, to)
            TransferResult(from = fromLeg, to = toLeg)
        }
        mirrorTransferLocally(result, request.transferId)
        return result
    }

    /** Una cuenta de la DB local como modelo, o null si este dispositivo todavía no la conoce. */
    private fun localAccount(id: String): Account? =
        db.accountQueries.selectById(id).executeAsOneOrNull()?.let { row ->
            Account(
                id = row.id, name = row.name,
                type = AccountType.valueOf(row.type),
                balance = row.balance, currency = row.currency,
            )
        }

    /**
     * Espeja las dos patas y mueve los dos saldos, **saltándose la pata que ya esté**.
     *
     * La guarda de idempotencia no es decorativa: este espejo corre también en el camino del 409,
     * o sea después de un reintento, y `INSERT OR REPLACE` no duplicaría la fila pero
     * `updateBalance` sí volvería a descontar. Sin el salto, guardar dos veces dejaba una sola
     * fila (bien) y el saldo movido dos veces (mal, y en silencio).
     */
    private fun mirrorTransferLocally(result: TransferResult, transferId: String) {
        val uid = userId()
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            listOf(result.from, result.to).forEach { leg ->
                val yaEstaba = db.financialEventQueries.selectById(leg.id, uid).executeAsOneOrNull() != null
                if (yaEstaba) return@forEach
                db.financialEventQueries.insert(
                    leg.id, leg.accountId, leg.type.name, leg.amount,
                    leg.category, leg.description, leg.merchant,
                    leg.timestamp, leg.source.name, leg.rawPayload,
                    leg.reconciliationStatus.name, leg.syncedAt ?: now, uid,
                    leg.transferId ?: transferId,
                    // Las dos patas se anotaron en el mismo acto: el sello del server viaja en la
                    // respuesta y se copia tal cual; `now` es solo el respaldo por si un server
                    // viejo no lo mandara.
                    leg.createdAt ?: now,
                )
                val acct = db.accountQueries.selectById(leg.accountId).executeAsOneOrNull() ?: return@forEach
                val accountType = AccountType.valueOf(acct.type)
                db.accountQueries.updateBalance(
                    acct.balance + signedDelta(accountType, leg.type, leg.amount), acct.id,
                )
            }
        }
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
        // Nadie sale de la categoría reservada, y nadie entra tampoco. Las guardas son las mismas
        // que las del server (ver PUT /api/events/{id}/category) —aunque no en el mismo orden,
        // ver la nota de abajo— y hacen falta acá
        // porque el camino "local, todavía sin sincronizar" de más abajo escribe sin preguntarle
        // a nadie — así que sin esto la fila local divergía en silencio del server.
        //
        // Cortar acá además le da la explicación al dueño incluso sin red, en vez de un error de
        // conexión que no dice nada.
        val fila = db.financialEventQueries.selectById(id, uid).executeAsOneOrNull()
        val esPataDeTraspaso = fila?.transferId != null || fila?.category == TRANSFER_CATEGORY
        if (esPataDeTraspaso) throw ApiException(422, TRANSFER_RECATEGORIZE_BLOCKED)
        // Y hacia la categoría reservada tampoco: sería fabricar media pata — un movimiento que
        // se deja de contar en el mes sin ninguna pata del otro lado que explique adónde fue.
        if (category == TRANSFER_CATEGORY) throw ApiException(422, TRANSFER_CATEGORY_RESERVED)
        // Y a «Cuenta eliminada» tampoco (ola 15): la escribe el borrado de una cuenta y nadie
        // más. Desde que queda fuera del flujo de caja, escribirla a mano sacaría un gasto REAL
        // del mes en el teléfono, el `SyncEngine` lo empujaría, el server contestaría 422 y la
        // fila se reintentaría cada 30 segundos para siempre — el mismo modo de falla que ya
        // documenta la guarda de `postEvent` acá arriba.
        //
        // **El ORDEN de estas dos guardas no es el del server, y hay que saberlo antes de tocar
        // acá.** El server pregunta primero por la categoría de destino y después si el evento es
        // una pata viva; acá es al revés, y es preexistente. La consecuencia es un solo caso —
        // poner «Cuenta eliminada» sobre una pata de traspaso VIVA— que responde
        // ORPHANED_LEG_NOT_MANUAL con red y TRANSFER_RECATEGORIZE_BLOCKED sin ella. Los dos son
        // 422, los dos rechazan, y ninguna pantalla ofrece ese camino (la hoja de una pata viva
        // ni siquiera muestra la lista de categorías). Se deja igualar el orden para cuando se
        // toque el server, en vez de mover una guarda de plata por un texto de error.
        if (category == ORPHANED_LEG_CATEGORY) throw ApiException(422, ORPHANED_LEG_NOT_MANUAL)
        // Ola 16 · las dos guardas del saldo inicial, y **este par sí está en el mismo orden que
        // el server** (a diferencia del de arriba): primero la categoría de destino, después la
        // del evento. Es a propósito y vale escribir por qué. Con este orden, los tres cruces
        // posibles entre una apertura y las otras reservadas dan el MISMO mensaje con red y sin
        // ella —«Saldo inicial»→«Traspaso» contesta TRANSFER_CATEGORY_RESERVED en los dos lados,
        // «Saldo inicial»→«Cuenta eliminada» contesta ORPHANED_LEG_NOT_MANUAL en los dos—, así que
        // no se agrega una divergencia nueva a la que ya está documentada acá arriba.
        //
        // El porqué de cada una está en :core, con la medición: [OPENING_CATEGORY_RESERVED] (un
        // gasto real que desaparece de «Gastos del mes») y [OPENING_RECATEGORIZE_BLOCKED] (una
        // apertura que se convierte en ingreso del mes).
        if (category == OPENING_CATEGORY) throw ApiException(422, OPENING_CATEGORY_RESERVED)
        if (fila?.category == OPENING_CATEGORY) throw ApiException(422, OPENING_RECATEGORIZE_BLOCKED)
        // Ola 18 · las dos que faltaban, y **la red que evita que vuelva a faltar una**.
        //
        // El KDoc de acá arriba dice «las guardas son las mismas que las del server», y hacía
        // dos olas que había dejado de ser cierto: la 17 agregó «Descuento de nómina» al server
        // y no acá, y la 18 repitió el olvido con «Pago de un tercero». Lo encontró la revisión.
        //
        // Por eso esta última no nombra categorías una por una: recorre RESERVED_CATEGORIES y
        // deja pasar solo la excepción declarada. Así la próxima reservada queda cerrada acá el
        // día que nazca, sin depender de que alguien se acuerde de agregar un `if`.
        //
        // La excepción es [CARD_PAYMENT_CATEGORY], y es la misma del server: confirmar un pago de
        // tarjeta la escribe a propósito y es el camino correcto (ver la guarda del `PUT`).
        if (category != CARD_PAYMENT_CATEGORY && isReservedCategory(category)) {
            throw ApiException(422, "«$category» la escribe Movi sola: no se puede poner a mano")
        }

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


    /**
     * Corrige la fecha de un movimiento. **Mismo esquema de dos caminos que
     * [updateEventCategory]**, y por los mismos motivos, así que acá solo se anota lo que cambia.
     *
     * - Si el evento **ya está sincronizado**: manda el `PUT` y espeja lo que devolvió el server
     *   (que es quien valida la fecha; ver la guarda de futuro en `PUT /api/events/{id}/timestamp`).
     * - Si el evento **todavía no llegó al server** (`syncedAt == null`, la ventana normal de los
     *   30 s del `SyncEngine`): el UPDATE es **solo local**. Llamar a `remote` ahí devolvería 404
     *   —el server ni sabe que el evento existe— y dejaría al dueño sin poder corregir la fecha
     *   de lo que acaba de anotar, que es justo cuando más se corrige. El `SyncEngine` sube el
     *   evento después con `row.timestamp`, o sea con la fecha ya corregida.
     *
     * La carrera con el `SyncEngine` se cierra igual que en [updateEventCategory] y con las
     * mismas dos mitades: acá se relee `syncedAt` fresco adentro de la transacción, y del otro
     * lado `markSyncedIfUnchanged` ahora compara **también el timestamp** — sin eso, corregir la
     * fecha mientras el POST estaba en vuelo dejaba la fila sellada con la fecha vieja en el
     * server y la nueva solo en local, para siempre.
     *
     * **Las dos patas de un traspaso se mueven juntas**, igual que se anulan juntas: es UN hecho
     * con una sola fecha. El server cascadea por `transferId` y acá se espeja lo mismo. (Por
     * diseño una pata nunca está pendiente de sincronizar —`createTransfer` es remote-first—, así
     * que el camino local de la cascada es la red de seguridad, no el habitual.)
     */
    override suspend fun updateEventTimestamp(id: String, timestamp: Long): FinancialEvent {
        val uid = userId()
        val types = accountTypes(uid)
        // **Las mismas guardas que el server, ANTES de decidir por qué camino se resuelve.**
        //
        // No es cortesía: el camino local de más abajo (evento todavía sin sincronizar) NUNCA
        // llama a `remote`, así que sin esto la guarda de futuro del server no corre para el
        // movimiento que el dueño acaba de anotar en el teléfono — y el `SyncEngine` después lo
        // sube por `POST /api/events`, que no valida fecha a propósito (por ahí entran los SMS y
        // los extractos, que traen la suya). Es el mismo motivo por el que
        // [updateEventCategory] repite acá las guardas de la categoría reservada.
        //
        // Hoy no se llega desde la UI —el selector no ofrece días futuros— pero «hoy no se llega»
        // es exactamente lo que dejó de ser cierto todas las veces que esto salió mal.
        //
        // `epochMillisToAppDate` es el de `:core` (AppTimeZone), no el de `:shared`: acá no se
        // puede importar UI. Las dos caras miran la misma zona, así que no pueden discrepar.
        val fecha = epochMillisToAppDate(timestamp)
        val hoy = epochMillisToAppDate(Clock.System.now().toEpochMilliseconds())
        if (fecha.year !in 2000..2100) throw ApiException(400, "Esa fecha no es de este siglo.")
        if (fecha > hoy) throw ApiException(422, EVENT_DATE_IN_FUTURE)

        val resolvedLocally = db.transactionWithResult {
            val local = db.financialEventQueries.selectById(id, uid).executeAsOneOrNull()
            if (local != null && local.syncedAt == null) {
                val transferId = local.transferId
                if (transferId != null) {
                    db.financialEventQueries.updateTimestampByTransferId(timestamp, transferId, uid)
                } else {
                    db.financialEventQueries.updateTimestamp(timestamp, id, uid)
                }
                local.toModel(types).copy(timestamp = timestamp)
            } else {
                null
            }
        }
        if (resolvedLocally != null) return resolvedLocally

        // **El sello de recurrente lo suelta el server** (ver `PUT /api/events/{id}/timestamp`),
        // así que el camino local de arriba no tiene qué soltar: `recurring_occurrences` es una
        // tabla que solo existe del lado del server —no hay espejo local— y un evento que todavía
        // no llegó al server no puede estar sellado por ninguna regla. El sello se pone eligiendo
        // un candidato, y los candidatos salen de un endpoint.
        val updated = remote.updateEventTimestamp(id, timestamp)
        val transferId = updated.transferId
        if (transferId != null) {
            db.financialEventQueries.updateTimestampByTransferId(updated.timestamp, transferId, uid)
        } else {
            db.financialEventQueries.updateTimestamp(updated.timestamp, updated.id, uid)
        }
        return updated
    }

    /**
     * Delega: `recurring_occurrences` vive solo en el server (no hay espejo local), así que sin
     * red no hay nada que responder. Devolver `null` es lo correcto y no una degradación
     * escondida: quien llama lo usa para decidir si MUESTRA un aviso de más, no para decidir si
     * deja guardar.
     */
    override suspend fun getEventOccurrenceMark(id: String): EventOccurrenceMark? =
        runCatching { remote.getEventOccurrenceMark(id) }.getOrNull()

    // ── Delegate everything else to remote ────────────────────────────────────

    /** Con caché de última respuesta buena — ver [leerConCache]. Sin señal, Créditos no queda en blanco. */
    override suspend fun getCredits(): List<CreditSummary> =
        leerConCache("credits") { remote.getCredits() }

    /**
     * Crea contra el server y **espeja la cuenta devuelta en la DB local** (F20, Ola 5).
     *
     * Antes delegaba y ya, y la cuenta LOAN que `POST /api/credits` creaba solo existía en el
     * server: la pantalla de Cuentas en Android leía [getAccounts] → SQLDelight y nada más, y el
     * [com.jvillada.movi.shared.SyncEngine] solo empuja, nunca trae — así que un crédito creado
     * desde la app nunca aparecía en Cuentas del teléfono (sí en Créditos, que lee remoto).
     * Desde que [getAccounts] pregunta al server, esa cuenta aparecería igual en la próxima
     * lectura con red; el espejo se queda porque es lo que la deja visible **ya** (sin esperar
     * otro viaje) y sin red.
     * Mismo espejo que ya hacía [adjustCreditBalance] para el caso "el crédito nació en el
     * server": la fila local se escribe con lo que el server devolvió, `syncedAt = ahora` para
     * que el SyncEngine no la vuelva a subir.
     *
     * El evento de apertura ("Deuda inicial") NO se espeja: el server no lo devuelve en el
     * summary (solo lo insertó en su transacción), así que en Movimientos del teléfono no se ve
     * — pero el saldo espejado ya lo incluye, que es lo que Cuentas muestra. Traerlo requeriría
     * ampliar el wire (como `adjustmentEvent`); queda anotado como deferido, no como olvido.
     */
    override suspend fun createCredit(request: CreateCreditRequest): CreditSummary {
        val summary = remote.createCredit(request)
        mirrorAccountLocally(summary.account)
        // Ola 16: si el alta trajo desembolso, sus DOS patas se espejan acá. Sin esto, en el
        // teléfono el crédito aparecía con su deuda pero la plata no llegaba nunca a la cuenta
        // corriente: Movimientos y Cuentas leen de SQLDelight y el SyncEngine solo empuja.
        summary.disbursement?.let { mirrorDisbursementLocally(it, summary.account.id) }
        return summary
    }

    /**
     * Espeja las dos patas del desembolso que `POST /api/credits` acabó de escribir.
     *
     * Es [mirrorTransferLocally] con **una** diferencia, y es la que importa: el saldo de la
     * cuenta del crédito NO se toca. [mirrorAccountLocally] lo acaba de escribir con lo que el
     * server derivó de todos sus eventos —la apertura de los costos financiados **más** la pata
     * del desembolso—, así que volver a aplicarle el delta de esa pata dejaría la deuda del
     * crédito al doble en el teléfono: el mismo número inflado que toda esta rama vino a matar,
     * entrando por la puerta de atrás.
     *
     * La otra cuenta sí se mueve acá: al server no se le pidió su fila (esta respuesta solo trae
     * la del crédito) y su saldo local está viejo hasta el próximo [getAccounts].
     *
     * La guarda de "ya estaba" se conserva del original: es barata y deja el espejo idempotente
     * si alguna vez se llama dos veces sobre la misma respuesta.
     */
    private fun mirrorDisbursementLocally(legs: TransferResult, loanAccountId: String) {
        val uid = userId()
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            listOf(legs.from, legs.to).forEach { leg ->
                val yaEstaba = db.financialEventQueries.selectById(leg.id, uid).executeAsOneOrNull() != null
                if (yaEstaba) return@forEach
                db.financialEventQueries.insert(
                    leg.id, leg.accountId, leg.type.name, leg.amount,
                    leg.category, leg.description, leg.merchant,
                    leg.timestamp, leg.source.name, leg.rawPayload,
                    leg.reconciliationStatus.name, leg.syncedAt ?: now, uid,
                    leg.transferId,
                    leg.createdAt ?: now,
                )
                if (leg.accountId == loanAccountId) return@forEach
                val acct = db.accountQueries.selectById(leg.accountId).executeAsOneOrNull() ?: return@forEach
                db.accountQueries.updateBalance(
                    acct.balance + signedDelta(AccountType.valueOf(acct.type), leg.type, leg.amount), acct.id,
                )
            }
        }
    }

    override suspend fun putCreditTerms(terms: CreditTerms): CreditSummary = remote.putCreditTerms(terms)
    override suspend fun deleteCreditTerms(accountId: String) = remote.deleteCreditTerms(accountId)

    /** Con caché, por el mismo motivo que [getCredits]. */
    override suspend fun getCards(): List<CardSummary> =
        leerConCache("cards") { remote.getCards() }
    /** Mismo espejo (y mismo porqué) que [createCredit]: sin él la tarjeta no aparece en Cuentas de Android. */
    override suspend fun createCard(request: CreateCardRequest): CardSummary =
        remote.createCard(request).also { mirrorAccountLocally(it.account) }
    // Los términos de tarjeta no viven en la DB local: se leen siempre del server, como los créditos.
    override suspend fun putCardTerms(terms: CardTerms): CardSummary = remote.putCardTerms(terms)
    override suspend fun deleteCardTerms(accountId: String) = remote.deleteCardTerms(accountId)

    /**
     * Upsert local (INSERT OR REPLACE) de una cuenta que nació en el server, marcada como
     * sincronizada. Lo usan las escrituras ([createCredit], [createCard]) y también las lecturas
     * ([getAccounts], [getAccount]) — la PK es el id, el mismo de los dos lados, así que espejar
     * una cuenta que ya estaba la pisa en vez de duplicarla.
     */
    private fun mirrorAccountLocally(account: Account) {
        db.accountQueries.insert(
            account.id, account.name, account.type.name,
            account.balance, account.currency, userId(),
            Clock.System.now().toEpochMilliseconds(),
            account.condicionadaA,
        )
    }

    /**
     * Copia local de un movimiento que vino del server, para que [getEvents] tenga qué contestar
     * sin red la próxima vez.
     *
     * **Se escribe sellado** (`syncedAt` = el del server, o el instante de la copia si el server
     * no lo mandó). Es lo que impide que el [com.jvillada.movi.shared.SyncEngine] lo vuelva a
     * subir: sin sello entraría en `selectUnsynced` y el teléfono empezaría a empujar de vuelta
     * al server cada cosa que acaba de bajar.
     *
     * **Una fila local SIN SELLAR no se pisa nunca.** La primera versión de esto decía que «lo
     * que el teléfono escribió y todavía no subió no pasa por acá, porque no está en la respuesta
     * remota», y era **falso**: una fila puede estar en el server *y* sin sellar en local, que es
     * exactamente la ventana que `markSyncedIfUnchanged` existe para cubrir. El dueño anota un
     * gasto, el ciclo lo empuja como «Comida», él lo recategoriza a «Mercado» —la fila queda sin
     * sellar a propósito— y la siguiente lectura le escribía «Comida» encima **y la sellaba**:
     * `selectUnsynced` pasaba de 1 a 0 y ningún ciclo futuro volvía a empujarla. Silencioso y
     * permanente. Lo mismo con una fecha corregida.
     *
     * Eso reabría por la puerta de la LECTURA los dos agujeros que el repo ya cerró dos veces
     * (ver los KDoc de `SyncEngine.syncEvents` y `updateEventCategory`). Y no hacía falta una
     * carrera fina: alcanza con un POST que el server confirma y cuya respuesta se pierde.
     *
     * Para el resto, `INSERT OR REPLACE`: si la fila ya estaba **y ya estaba sellada**, gana la
     * del server, que es la autoridad.
     */
    private fun mirrorEventLocally(event: FinancialEvent, uid: String) {
        val local = db.financialEventQueries.selectById(event.id, uid).executeAsOneOrNull()
        if (local != null && local.syncedAt == null) return
        val ahora = Clock.System.now().toEpochMilliseconds()
        db.financialEventQueries.insert(
            event.id, event.accountId, event.type.name, event.amount,
            event.category, event.description, event.merchant,
            event.timestamp, event.source.name, event.rawPayload,
            event.reconciliationStatus.name, event.syncedAt ?: ahora, uid,
            event.transferId, event.createdAt,
        )
    }
    /**
     * Ajusta contra el server y **espeja el resultado en la DB local**.
     *
     * El resto de las operaciones de crédito delegan y ya: se leen siempre desde el server. El
     * ajuste no puede, porque su efecto secundario —un movimiento en la cuenta— se lee desde acá:
     * [getEvents]/[getEventsByDay] van a SQLDelight (y [getAccounts] cae ahí cuando no hay red),
     * mientras [com.jvillada.movi.shared.SyncEngine] solo empuja, nunca trae. Los eventos siguen
     * sin bajar del server por ningún camino, así que este espejo sigue siendo la única forma de
     * que el ajuste se vea acá. Sin él, en Android el ajuste no aparecía en Movimientos,
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
                    event.transferId,
                    // El ajuste lo creó el server; se copia su sello, no uno nuevo de acá.
                    event.createdAt ?: Clock.System.now().toEpochMilliseconds(),
                )
            }
            // Upsert (INSERT OR REPLACE): si el crédito se creó desde el server la fila puede no
            // existir localmente todavía, y en ese caso la pantalla de Cuentas ni siquiera lo veía.
            // syncedAt = ahora: esto vino del server, no hay nada pendiente de empujar.
            db.accountQueries.insert(
                summary.account.id, summary.account.name, summary.account.type.name,
                summary.account.balance, summary.account.currency, uid,
                Clock.System.now().toEpochMilliseconds(),
                summary.account.condicionadaA,
            )
        }
        return summary
    }
    override suspend fun getSubscriptions(): SubscriptionsResult = remote.getSubscriptions()
    override suspend fun detectSubscriptions(): SubscriptionsResult = remote.detectSubscriptions()
    override suspend fun updateSubscription(id: String, subscription: Subscription): Subscription = remote.updateSubscription(id, subscription)
    override suspend fun deleteSubscription(id: String) = remote.deleteSubscription(id)
    // F38: alta manual — sin espejo local, misma razón que el resto de esta sección.
    override suspend fun createSubscription(request: CreateSubscriptionRequest): Subscription = remote.createSubscription(request)
    // Propuesta de solo lectura, sin efecto secundario que espejar (a diferencia de
    // updateEventCategory/adjustCreditBalance arriba): delega directo, igual que el resto de
    // esta sección.
    override suspend fun getCardPaymentCandidates(): List<FinancialEvent> = remote.getCardPaymentCandidates()
    // Igual que getCardPaymentCandidates arriba: no hay nada que espejar localmente — "No es" no
    // toca la categoría del evento, así que no hay ninguna fila local que quedaría desactualizada.
    override suspend fun dismissCardPaymentCandidate(id: String) = remote.dismissCardPaymentCandidate(id)
    /** Con caché: una meta sin señal es igual de útil que con señal — no cambia sola. */
    override suspend fun getGoals(): List<Goal> =
        leerConCache("goals") { remote.getGoals() }
    // F26: metas remote-only, igual que presupuestos/recurrentes — no hay flujo offline que
    // las necesite todavía.
    override suspend fun createGoal(goal: Goal): Goal = remote.createGoal(goal)
    override suspend fun updateGoal(id: String, goal: Goal): Goal = remote.updateGoal(id, goal)
    override suspend fun deleteGoal(id: String) = remote.deleteGoal(id)
    override suspend fun getSmsMessages(): List<SmsMessage> = remote.getSmsMessages()
    override suspend fun getSms(id: String): SmsMessage = remote.getSms(id)
    override suspend fun parseSms(id: String): ParsedSms = remote.parseSms(id)
    override suspend fun confirmSms(id: String) = remote.confirmSms(id)
    override suspend fun ignoreSms(id: String) = remote.ignoreSms(id)
    override suspend fun getFinanceSummary(scope: Scope): FinanceSummary = remote.getFinanceSummary(scope)
    // Igual que getFinanceSummary: es un agregado que solo el server puede calcular con todo lo
    // que sabe (SMS, descartes de candidatos, eventos de todos los dispositivos). Sin red falla
    // y el Inicio conserva lo último que tenía en DashboardDataCache.
    //
    // Ojo con lo que reemplaza: getSmsMessages y getCardPaymentCandidates eran remotas, pero
    // el gasto del mes salía de [getEventsByDay], que acá es LOCAL (SQLDelight: solo los
    // eventos de este dispositivo, sin excluir anulados, con la zona del sistema). La cifra del
    // server es más correcta — todos los dispositivos, SMS e importaciones, anulados fuera — y
    // Presupuestos usa esta misma fuente para que las dos pantallas coincidan; el cálculo local
    // queda solo como fallback sin red.
    override suspend fun getDashboardSummary(scope: Scope): DashboardSummary = remote.getDashboardSummary(scope)
    /** Con caché. El GASTO contra el presupuesto sale de los eventos, que ya tienen espejo propio. */
    override suspend fun getBudgets(): List<Budget> =
        leerConCache("budgets") { remote.getBudgets() }
    override suspend fun createBudget(budget: Budget): Budget = remote.createBudget(budget)
    override suspend fun updateBudget(category: String, budget: Budget): Budget = remote.updateBudget(category, budget)
    override suspend fun deleteBudget(category: String) = remote.deleteBudget(category)
    // Los presupuestos no tienen tabla local (a diferencia de accounts/financial_event) — se
    // leen siempre del server, así que renombrar es delegar y ya, igual que create/update/delete.
    override suspend fun renameBudget(category: String, newCategory: String): Budget =
        remote.renameBudget(category, newCategory)

    // ── Categorías (Ola 10) ───────────────────────────────────────────────────

    /** La lista con uso real la arma el server sobre TODA la historia — acá no hay nada que espejar. */
    override suspend fun getCategories(): List<CategoryUsage> = remote.getCategories()

    /**
     * Renombrar contra el server y **espejar la reescritura en SQLDelight** — mismo patrón que
     * [createAccount] y [updateEventCategory]: `remote` primero, lo local después y solo con lo
     * que el server confirmó.
     *
     * El espejo hace falta porque el `SyncEngine` **solo empuja**: nada baja del server. Sin este
     * UPDATE, la reescritura quedaría hecha en la base del server y la web mostraría «Transporte»
     * mientras este teléfono —que lee sus movimientos de la tabla local, ver [getEvents]— seguiría
     * diciendo «Trasnporte» para siempre. Y no sería un desfase pasajero: no hay ningún ciclo que
     * lo corrija después.
     *
     * **Lo que este espejo NO cubre, dicho sin suavizar.** Una categoría renombrada desde OTRO
     * dispositivo no llega a este, y para el dueño —que usa la web Y el APK— eso no es un desfase
     * cosmético: **el teléfono vuelve a ofrecer el tipeo que él acaba de corregir, y puede volver
     * a sembrarlo.** El camino completo:
     *
     * 1. Renombra «Trasnporte» → «Transporte» desde la web. El server reescribe las tres tablas;
     *    la tabla local del teléfono se queda con el nombre viejo.
     * 2. Abre Movimientos en el teléfono. Esa pantalla lee de SQLDelight, así que ve «Trasnporte»
     *    — y de paso lo vuelve a meter en `UsedCategoriesCache` (ver `TransactionsScreen`).
     * 3. Va a «Agregar» y la app le **sugiere «Trasnporte»**. Si la toca, crea un movimiento nuevo
     *    con el nombre viejo, y ahora sí queda historia partida en dos **en el server**.
     *
     * Nada lo corrige después salvo reinstalar. Cerrarlo de verdad pide que el `SyncEngine`
     * aprenda a bajar cambios (hoy solo empuja), que es un cambio de arquitectura y no un detalle
     * de esta pantalla — pero queda escrito acá, con el escenario, en vez de resumido como una
     * divergencia menor.
     *
     * Lo que sí está descartado es el peor caso **para las filas ya sincronizadas**: el teléfono
     * no las repisa hacia el server, porque `selectUnsynced` filtra por `syncedAt IS NULL` y esas
     * filas ya están selladas. La afirmación **no cubre** una fila creada sin señal y todavía
     * pendiente: esa sí se empuja tal como está, con el nombre viejo, y el server la acepta. Es un
     * caso acotado (un movimiento anotado offline entre el rename y el próximo sync) pero no es
     * cero, y decirlo redondo sería el tipo de tranquilidad falsa que este KDoc vino a corregir.
     */
    override suspend fun renameCategory(from: String, to: String): CategoryRewriteResult {
        val result = remote.renameCategory(from, to)
        db.financialEventQueries.renameCategory(newCategory = result.name, oldCategory = from, userId = userId())
        return result
    }

    /** Igual que [renameCategory]: el server reescribe, y acá se espeja lo que confirmó. */
    override suspend fun mergeCategory(from: String, into: String): CategoryRewriteResult {
        val result = remote.mergeCategory(from, into)
        db.financialEventQueries.renameCategory(newCategory = result.name, oldCategory = from, userId = userId())
        return result
    }


    /**
     * Lee del server y guarda la respuesta; si el server no contesta, devuelve la última que sí
     * contestó.
     *
     * Es un caché de **lectura**, no un espejo: no participa de la cola de subida ni resuelve
     * conflictos. Existe para las pantallas que se piden siempre al server y que sin señal
     * quedaban en blanco — y de esas, los recurrentes son las más graves, porque son avisos de
     * vencimiento.
     *
     * Tres decisiones que importan:
     *
     * - **Una respuesta buena SIEMPRE pisa el caché**, aunque venga vacía: si el dueño borró todos
     *   sus recurrentes, la lista vacía es la verdad y no puede quedar tapada por la anterior.
     * - **Sin caché se propaga el error.** Contestar «no tienes nada» sin haber podido preguntar
     *   es una afirmación sin respaldo — el mismo criterio que [getAccounts].
     * - **`CancellationException` no se traga.** La pantalla que se fue mientras el request estaba
     *   en vuelo no es un fallo de red, y tragarla haría que el llamador lo leyera como un éxito.
     */
    private suspend inline fun <reified T> leerConCache(
        clave: String,
        traer: () -> List<T>,
    ): List<T> {
        val serializador = kotlinx.serialization.serializer<List<T>>()
        val uid = userId()
        return try {
            val fresco = traer()
            db.remoteCacheQueries.put(
                cacheKey = clave,
                userId = uid,
                payload = Json.encodeToString(serializador, fresco),
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            )
            fresco
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val guardado = db.remoteCacheQueries.get(clave, uid).executeAsOneOrNull() ?: throw e
            runCatching { Json.decodeFromString(serializador, guardado.payload) }.getOrElse { throw e }
        }
    }

    /** Preferencias puras: no tocan ni un movimiento, así que no hay nada que espejar. */
    override suspend fun setCategoryPrefs(name: String, hidden: Boolean, pinnedType: String?): CategoryUsage =
        remote.setCategoryPrefs(name, hidden, pinnedType)
    /**
     * **Con caché de última respuesta buena.** Ver [leerConCache].
     *
     * Los recurrentes son avisos de vencimiento: sin señal el dueño veía una pantalla vacía justo
     * cuando menos sirve. Ahora ve lo último que Movi supo.
     */
    override suspend fun getRecurringRules(): List<RecurringRule> =
        leerConCache("recurring_rules") { remote.getRecurringRules() }
    override suspend fun createRecurringRule(rule: RecurringRule): RecurringRule = remote.createRecurringRule(rule)
    override suspend fun updateRecurringRule(id: String, rule: RecurringRule): RecurringRule = remote.updateRecurringRule(id, rule)
    override suspend fun deleteRecurringRule(id: String) = remote.deleteRecurringRule(id)
    /** Con caché, por el mismo motivo que [getRecurringRules]: es lo que vence. */
    override suspend fun getUpcomingPayments(): List<UpcomingPayment> =
        leerConCache("upcoming_payments") { remote.getUpcomingPayments() }

    // Los canales de aviso también van derecho al server, por el mismo motivo que los
    // vencimientos: la respuesta depende de la configuración del server (variables de entorno) y
    // el espejo local no la conoce. Sin conexión no se contesta, y el cliente trata «no sé» como
    // «no afirmo nada» — que es justo lo correcto para un aviso que promete o niega una entrega.
    override suspend fun getReminderChannels(): ReminderChannels = remote.getReminderChannels()

    // «Ya ocurrió» va derecho al server, igual que los vencimientos: el espejo local no tiene
    // tabla de ocurrencias ni sabe calcular candidatos (el emparejamiento necesita TODOS los
    // movimientos vivos y las ocurrencias ya usadas). Consecuencia honesta: sin conexión, la
    // pregunta «¿esto ya ocurrió?» no se puede contestar ni marcar. Es lo mismo que ya pasa con
    // «Próximos», y el `SyncEngine` de este proyecto solo empuja — no sabría traer esto de vuelta.
    override suspend fun getOccurrenceStates(): List<OccurrenceState> = remote.getOccurrenceStates()
    override suspend fun markOccurrence(ruleId: String, period: String, eventId: String?): RecurringOccurrence =
        remote.markOccurrence(ruleId, period, eventId)
    override suspend fun unmarkOccurrence(ruleId: String, period: String) = remote.unmarkOccurrence(ruleId, period)
    override suspend fun chatAi(request: AiChatRequest): AiChatResponse = remote.chatAi(request)
    override suspend fun register(request: RegisterRequest): AuthResponse = remote.register(request)
    override suspend fun login(request: LoginRequest): AuthResponse = remote.login(request)
    override suspend fun requestPasswordReset(request: PasswordResetRequest): Int = remote.requestPasswordReset(request)
    override suspend fun uploadStatement(fileName: String, bytes: ByteArray, mimeType: String): StatementParseResult =
        remote.uploadStatement(fileName, bytes, mimeType)
    override suspend fun importStatement(decision: ImportDecision) =
        remote.importStatement(decision)

    // Los documentos NO se espejan localmente, y es una decisión, no un olvido: son archivos de
    // megas y el espejo de SQLDelight existe para que las CIFRAS estén sin señal, no para tener
    // una copia offline de cada PDF del banco. Sin red, la pantalla de documentos no lista —que
    // es honesto— en vez de mostrar una lista de papeles que no se pueden abrir.
    // Va directo al server, como el traspaso: son DOS eventos enlazados y una deuda que baja,
    // así que fabricarlos localmente y sincronizarlos después abriría la ventana en que la app
    // muestra un pago que el server todavía no aceptó.
    override suspend fun payInstallment(request: CreatePagoDeCuotaRequest): PagoDeCuotaResult =
        remote.payInstallment(request)

    override suspend fun getDocuments(): List<Documento> = remote.getDocuments()
    override suspend fun uploadDocument(
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        tipo: TipoDeDocumento,
        accountId: String?,
        periodo: String?,
        notas: String?,
    ): Documento = remote.uploadDocument(fileName, bytes, mimeType, tipo, accountId, periodo, notas)
    override suspend fun getDocumentLink(id: String): EnlaceDeDescarga = remote.getDocumentLink(id)
    override suspend fun updateDocument(id: String, cambios: EdicionDeDocumento): Documento =
        remote.updateDocument(id, cambios)
    override suspend fun deleteDocument(id: String) = remote.deleteDocument(id)
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

    // F42 · F46: sin espejo, a propósito — el perfil se lee siempre del server, igual que
    // register/login arriba. No hay tabla local de usuario ni razón para tenerla: es una fila
    // por persona, no datos que necesiten funcionar offline.
    override suspend fun getUserProfile(): UserProfile = remote.getUserProfile()
    override suspend fun updateUserProfile(request: UpdateProfileRequest): UserProfile = remote.updateUserProfile(request)
    override suspend fun changePassword(request: ChangePasswordRequest) = remote.changePassword(request)

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
        transferId = transferId,
        createdAt = createdAt,
        countsAsCashFlow = typeByAccount[accountId]
            ?.let { isCashFlow(it, TransactionType.valueOf(type), category) }
            ?: true,
    )
}

// Misma zona civil que el server (/api/events/by-day) — ver AppTimeZone. Offline y online
// tienen que agrupar por el mismo día.
private fun epochMillisToDate(millis: Long): String = epochMillisToAppDate(millis).toString()
