package com.jvillada.movi.server.db

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id           = varchar("id", 50)
    val email        = varchar("email", 255).uniqueIndex()
    val name         = varchar("name", 100)
    val passwordHash = varchar("password_hash", 255)
    // F42 · F46: color del avatar de iniciales, uno de AvatarPalette.COLORS (:core). Nullable
    // porque toda cuenta existente hoy no tiene ninguno — `UserRoutes.kt` cae a
    // AvatarPalette.DEFAULT en la lectura, así que el cliente nunca ve `null`.
    val avatarColor  = varchar("avatar_color", 7).nullable()
    /**
     * Día del mes en que arranca el período financiero del usuario (ver [PeriodSettings] en
     * :core). Vive acá y no en el dispositivo para que el teléfono y la web digan el mismo mes:
     * un corte distinto en cada lado sería la clase de contradicción que Movi viene eliminando.
     *
     * **Nullable a propósito**, y se lee como 1 (mes de calendario) cuando falta: las cuentas que
     * ya existen no lo tienen, y una columna nullable es lo único que `createMissingTablesAndColumns`
     * puede agregar sin riesgo dentro de la transacción de arranque. Un `NOT NULL DEFAULT` sobre
     * una tabla con filas es justo el DDL que deja el server sin arrancar.
     */
    val periodCutoffDay = integer("period_cutoff_day").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Tokens de recuperación de contraseña.
 *
 * `token_hash` guarda `sha256(token)` — **el token en claro no se persiste nunca**. Un dump de
 * esta tabla no sirve para canjear ningún reset.
 *
 * `used_at` implementa el uso único: se sella al consumir el token y también al invalidar los
 * hermanos del mismo usuario. Una fila con `used_at` no nulo o con `expires_at` en el pasado
 * ya no vale — el confirm falla cerrado ante cualquiera de las dos condiciones.
 */
object PasswordResetTokens : Table("password_reset_tokens") {
    val id        = varchar("id", 50)
    val userId    = varchar("user_id", 50)
    val tokenHash = varchar("token_hash", 64)   // sha256 hex — jamás el token
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val usedAt    = long("used_at").nullable()
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex("uq_password_reset_token_hash", tokenHash)
        index("idx_password_reset_user_id", false, userId)
    }
}

object Accounts : Table("accounts") {
    val id       = varchar("id", 50)
    val userId   = varchar("user_id", 50)
    val name     = varchar("name", 100)
    val type     = varchar("type", 30)
    val balance  = long("balance").default(0)
    val currency = varchar("currency", 10).default("COP")
    override val primaryKey = PrimaryKey(id)
}

object StatementImports : Table("statement_imports") {
    val id              = varchar("id", 50)
    val userId          = varchar("user_id", 50)
    val accountId       = varchar("account_id", 50)
    val bankName        = varchar("bank_name", 100)
    val period          = varchar("period", 50)
    val importedAt      = long("imported_at")
    val importedCount   = integer("imported_count")
    val reconciledCount = integer("reconciled_count")
    override val primaryKey = PrimaryKey(id)
    init { index("idx_statement_imports_user_id", false, userId) }
}

object Events : Table("financial_events") {
    val id                   = varchar("id", 50)
    val userId               = varchar("user_id", 50)
    val accountId            = varchar("account_id", 50)
    val type                 = varchar("type", 20)
    val amount               = long("amount")
    val currency             = varchar("currency", 10).default("COP")
    val category             = varchar("category", 100)
    val description          = varchar("description", 255)
    val merchant             = varchar("merchant", 255).nullable()
    val timestamp            = long("timestamp")
    val eventSource          = varchar("source", 20).default("MANUAL")
    val rawPayload           = text("raw_payload").nullable()
    val reconciliationStatus = varchar("reconciliation_status", 20).default("UNCONFIRMED")
    val syncedAt             = long("synced_at").nullable()
    val statementImportId    = varchar("statement_import_id", 50).nullable()
    /**
     * Enlace entre las dos patas de un traspaso (ver `TransferRoutes.kt` y
     * [com.jvillada.movi.shared.model.transferLegsFor]). Nullable porque lo es para todo lo
     * demás — no hay traspasos viejos que migrar, y `createMissingTablesAndColumns(Events)` la
     * agrega sola al arrancar sobre una base ya desplegada.
     */
    val transferId           = varchar("transfer_id", 50).nullable()
    /**
     * **Cuándo se anotó** el movimiento, que no es cuándo ocurrió ([timestamp]). Ver
     * [com.jvillada.movi.shared.model.FinancialEvent.createdAt] para el porqué completo; el
     * resumen es que una fecha elegida a mano se guarda al mediodía, así que `timestamp` empata
     * entre todos los movimientos que el dueño anota para un mismo día pasado y no alcanza para
     * ordenarlos.
     *
     * Nullable, y por el mismo motivo que [transferId]: la agrega sola
     * `createMissingTablesAndColumns(Events)` al arrancar sobre la base ya desplegada. **Una
     * columna nullable por esa vía es segura sobre una tabla con datos** — lo que sí puede dejar
     * el server sin levantar es un `CREATE INDEX` que falle, y acá no se crea ninguno: esta
     * columna solo desempata en memoria, nunca se filtra ni se ordena por ella en SQL.
     *
     * Las filas que ya existen quedan en NULL a propósito: no hay de dónde sacar cuándo se
     * crearon, y el comparador las hace caer a su `timestamp` en vez de inventarles una fecha.
     */
    val createdAt            = long("created_at").nullable()
    override val primaryKey  = PrimaryKey(id)
    init {
        index("idx_events_statement_import_id", false, statementImportId)
        // La anulación en cascada busca la pata hermana por acá (ver POST /api/events/{id}/void).
        index("idx_events_transfer_id", false, transferId)
        // Y la regla "una sola pata por traspaso y por lado" la impone un índice ÚNICO sobre
        // (user_id, transfer_id, type) que NO se declara acá a propósito: `createMissingTablesAndColumns`
        // crearía el índice al arrancar, dentro de la transacción del esquema, y sobre una base
        // que ya tuviera datos en conflicto eso deja el server sin levantar. Se crea en
        // `Migrations.createUniqueTransferLegIndex`, que primero pregunta y solo después crea —
        // ver ahí el porqué de la forma compuesta.

        // Todo lo que lee el Inicio y el resumen del mes filtra por usuario y rango de fechas
        // (GET /api/dashboard/summary, finance-summary). Se crea solo al arrancar vía
        // createMissingTablesAndColumns (DatabaseFactory) — sin migración manual.
        index("idx_events_user_ts", false, userId, timestamp)
    }
}

object VoidEvents : Table("void_events") {
    val id              = varchar("id", 50)
    val userId          = varchar("user_id", 50)
    val originalEventId = varchar("original_event_id", 50)
    val reason          = varchar("reason", 500).nullable()
    val timestamp       = long("timestamp")
    override val primaryKey = PrimaryKey(id)
    init { uniqueIndex("uq_void_events_original_user", originalEventId, userId) }
}

/**
 * Candidatos de `card-payment-candidates` que el dueño marcó explícitamente como "No es" (ver
 * `POST /api/events/{id}/not-card-payment` en `EventRoutes`). Clave compuesta porque lo único
 * que hace falta guardar es "este usuario descartó este evento" — no hay nada más que decir de
 * la fila, ni un motivo, ni una fecha que alguna pantalla necesite mostrar.
 *
 * No hay "restaurar" para esta tabla: si el descarte fue un error, el movimiento sigue en
 * Movimientos y se recategoriza a mano desde ahí (ver `ChangeCategorySheet`), incluso a
 * "Pago de tarjeta" si en verdad lo era. La fila de acá se queda — inofensiva, porque el filtro
 * del GET solo la usa para excluir, y un evento ya recategorizado no vuelve a matchear
 * `looksLikeCardPayment` de todas formas.
 */
object CardPaymentDismissals : Table("card_payment_dismissals") {
    val userId  = varchar("user_id", 50)
    val eventId = varchar("event_id", 50)
    override val primaryKey = PrimaryKey(userId, eventId)
}

object Budgets : Table("budgets") {
    val userId       = varchar("user_id", 50)
    val category     = varchar("category", 100)
    val monthlyLimit = long("monthly_limit")
    override val primaryKey = PrimaryKey(userId, category)
}

/**
 * Ola 10 — lo que el dueño decidió sobre una categoría en «Más → Categorías».
 *
 * **Solo preferencias, nunca la categoría en sí.** La categoría no tiene tabla propia y este
 * cambio no se la inventa: sigue siendo texto copiado en cada fila de `financial_events`,
 * `budgets` y `recurring_rules`. Renombrar y unificar son reescrituras de esas tres tablas (ver
 * `CategoryRoutes.rewriteCategory`), no un UPDATE acá. Lo único que vive en esta tabla es lo que
 * NO está en ningún movimiento: si la categoría se sigue ofreciendo al escribir ([hidden]) y de
 * qué tipo la considera el dueño ([pinnedType]), que es lo que le permite decir que «Otros» sirve
 * para gastos y para ingresos aunque el catálogo la tenga clavada en EXPENSE.
 *
 * Por usuario, y con el **nombre** como clave — no un id: es la misma clave con la que se cruzan
 * hoy presupuestos y gastos. La consecuencia es que renombrar tiene que mover también la fila de
 * acá, y por eso la reescritura la incluye en su transacción.
 *
 * Una fila con `hidden = false` y `pinned_type = NULL` no dice nada que el default no diga: el
 * PUT la borra en vez de guardarla, así que esta tabla solo tiene lo que el dueño de verdad
 * cambió. Tabla nueva → `SchemaUtils.create` la crea sola al arrancar (CREATE TABLE IF NOT
 * EXISTS), sin migración ni DDL que pueda tumbar el arranque.
 */
object CategoryPrefs : Table("category_prefs") {
    val userId     = varchar("user_id", 50)
    val name       = varchar("name", 100)
    val hidden     = bool("hidden").default(false)
    /** "EXPENSE" | "INCOME" | "BOTH", o NULL = sin fijar (manda el catálogo, o el uso). */
    val pinnedType = varchar("pinned_type", 10).nullable()
    override val primaryKey = PrimaryKey(userId, name)
}

object RecurringRules : Table("recurring_rules") {
    val id                 = varchar("id", 50)
    val userId             = varchar("user_id", 50)
    val name               = varchar("name", 100)
    val category           = varchar("category", 100)
    val amount             = long("amount")
    val dayOfMonth         = integer("day_of_month")
    val type               = varchar("type", 20)
    val lastRemindedPeriod = varchar("last_reminded_period", 7).nullable()  // "YYYY-MM", server-only
    /**
     * ¿Este pago entra al barrido de recordatorios? `.default(true)` no es cosmético: es lo que
     * hace que `createMissingTablesAndColumns` emita `ADD COLUMN remind_me BOOLEAN DEFAULT TRUE
     * NOT NULL` y las filas que ya existían queden avisando, igual que antes del cambio.
     */
    val remindMe           = bool("remind_me").default(true)
    /**
     * Ola 9 · D: a qué cuenta entra o de cuál sale este pago. **Nullable, y así se queda**: las
     * reglas que ya existen nacieron sin cuenta y no se les puede exigir un dato que nadie pidió,
     * así que `createMissingTablesAndColumns` emite `ADD COLUMN account_id VARCHAR(50) NULL` y
     * las filas viejas quedan en NULL — que es la verdad: no se sabe.
     *
     * Sin FK a `accounts` por la misma razón que el resto de esta base no las usa; la integridad
     * la mantiene el DELETE de la cuenta, que pone esta columna en NULL en vez de borrar la
     * regla (ver `AccountRoutes`).
     */
    val accountId          = varchar("account_id", 50).nullable()
    override val primaryKey = PrimaryKey(id)
    init { index("idx_recurring_rules_user_id", false, userId) }
}

/**
 * «Este recurrente ya ocurrió en este periodo» — ver
 * [com.jvillada.movi.shared.model.RecurringOccurrence] para el porqué y para la unidad de periodo.
 *
 * **Clave primaria compuesta `(user_id, rule_id, period)`**: una sola ocurrencia por regla y por
 * periodo. Es la misma unidad con la que `reminderKeyFor` deduplica los avisos, así que las dos
 * mitades del sistema hablan del mismo mes; y como es la PK, la impone el `CREATE TABLE` — no
 * hace falta ningún `CREATE INDEX` posterior, que es justo el DDL que en esta base puede dejar el
 * server sin levantar.
 *
 * Tabla NUEVA: entra por `SchemaUtils.create` (CREATE TABLE IF NOT EXISTS) y no por
 * `createMissingTablesAndColumns`. Sobre la base del dueño, que no la tiene, se crea vacía; sobre
 * una que ya la tenga no se emite nada. No hay datos viejos que migrar: antes de esto no existía
 * ningún vínculo entre un movimiento y la regla que lo originó, así que no hay nada que rellenar
 * — todos los recurrentes arrancan sin ninguna ocurrencia sellada, o sea exactamente como se
 * comportaban ayer.
 *
 * `event_id` es NULLABLE y sin FK (como el resto de esta base): NULL significa «el dueño cerró el
 * periodo sin emparejar ningún movimiento». Que no haya FK no deja la fila mintiendo: la lectura
 * solo honra una ocurrencia cuyo movimiento siga vivo y sin anular (ver `loadOccurredBy`).
 */
object RecurringOccurrences : Table("recurring_occurrences") {
    val userId      = varchar("user_id", 50)
    val ruleId      = varchar("rule_id", 50)
    val period      = varchar("period", 7)              // "YYYY-MM" del VENCIMIENTO
    val eventId     = varchar("event_id", 50).nullable()
    val confirmedAt = long("confirmed_at")
    override val primaryKey = PrimaryKey(userId, ruleId, period)
}

object SmsMessages : Table("sms_messages") {
    val id     = varchar("id", 50)
    val userId = varchar("user_id", 50)
    val time   = varchar("time", 50)
    val bank   = varchar("bank", 100)
    val text   = text("text")
    val state  = varchar("state", 20)
    val det    = varchar("det", 255)
    override val primaryKey = PrimaryKey(id, userId)  // per-user: the same SMS id may exist for different users
    init { index("idx_sms_messages_user_id", false, userId) }
}

object Credits : Table("credit_terms") {
    /**
     * Libranza: la cuota se descuenta de la nómina. Nullable y se lee como `false` — las filas
     * que ya existen no la tienen, y una columna nullable es lo único que
     * `createMissingTablesAndColumns` puede agregar sin riesgo dentro de la transacción de
     * arranque.
     */
    val payrollDeduction = bool("payroll_deduction").nullable()
    val accountId          = varchar("account_id", 50)   // 1:1 con cuenta LOAN
    val userId             = varchar("user_id", 50)
    val bank               = varchar("bank", 80)
    val principal          = long("principal")            // capital original (COP)
    val rateEa             = double("rate_ea")            // % EA
    val termMonths         = integer("term_months")
    val installment        = long("installment")          // cuota mensual total
    val dayOfMonth         = integer("day_of_month")
    val startDate          = varchar("start_date", 10)    // ISO desembolso
    val notes              = varchar("notes", 300).nullable()
    val lastRemindedPeriod = varchar("last_reminded_period", 7).nullable() // "YYYY-MM", server-only
    /** Ver `RecurringRules.remindMe`. */
    val remindMe           = bool("remind_me").default(true)
    override val primaryKey = PrimaryKey(accountId)
    init { index("idx_credit_terms_user_id", false, userId) }
}

/**
 * F20 — términos de tarjeta de crédito, 1:1 con su cuenta CREDIT_CARD. Tabla aparte de
 * `credit_terms` a propósito: una tarjeta no tiene capital, tasa ni plazo — tiene cupo, corte y
 * día de pago. `credit_limit` y `cutoff_day` nullable (no todo el mundo se los sabe);
 * `payment_day` obligatorio porque alimenta el recordatorio de pago.
 */
object Cards : Table("card_terms") {
    val accountId          = varchar("account_id", 50)   // 1:1 con cuenta CREDIT_CARD
    val userId             = varchar("user_id", 50)
    val bank               = varchar("bank", 80)
    val creditLimit        = long("credit_limit").nullable()   // cupo (moneda de la cuenta)
    val cutoffDay          = integer("cutoff_day").nullable()
    val paymentDay         = integer("payment_day")
    val notes              = varchar("notes", 300).nullable()
    val lastRemindedPeriod = varchar("last_reminded_period", 7).nullable() // "YYYY-MM", server-only
    /** Ver `RecurringRules.remindMe`. */
    val remindMe           = bool("remind_me").default(true)
    override val primaryKey = PrimaryKey(accountId)
    init { index("idx_card_terms_user_id", false, userId) }
}

object Subscriptions : Table("subscriptions") {
    val id          = varchar("id", 50)
    val userId      = varchar("user_id", 50)
    val merchantKey = varchar("merchant_key", 80)
    val displayName = varchar("display_name", 100)
    val amount      = long("amount")                 // gasto mensual típico (moneda nativa)
    val currency    = varchar("currency", 10)
    val dayOfMonth  = integer("day_of_month")
    val status      = varchar("status", 20)          // AUTO | CANDIDATE | CONFIRMED | DISMISSED
    val confidence  = varchar("confidence", 10)      // HIGH | MEDIUM | LOW
    val firstSeen   = long("first_seen")
    val lastSeen    = long("last_seen")
    val occurrences = integer("occurrences")
    val accountId   = varchar("account_id", 50).nullable()
    override val primaryKey = PrimaryKey(id)
    init {
        index("idx_subscriptions_user_id", false, userId)
        uniqueIndex("uq_subscriptions_user_merchant_currency", userId, merchantKey, currency)
    }
}

/**
 * F26 — metas de ahorro. `saved` NO vive acá: se deriva siempre del saldo de `account_id` (ver
 * `GoalRoutes.kt` GET, que usa el mismo `accountCopValue` que ya calcula el balance de cuentas).
 * Guardar un "ahorrado" aparte habría permitido que se desincronizara del saldo real — justo lo
 * que el plan pidió evitar ("nada de aportes manuales: si la plata está en la cuenta, cuenta").
 */
object Goals : Table("goals") {
    val id         = varchar("id", 50)
    val userId     = varchar("user_id", 50)
    val name       = varchar("name", 100)
    val target     = long("target")
    val accountId  = varchar("account_id", 50)
    val targetDate = varchar("target_date", 10).nullable()   // ISO "2027-01-01"
    val createdAt  = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init { index("idx_goals_user_id", false, userId) }
}

object Screens : Table("screen_definitions") {
    val slug         = varchar("slug", 64)
    val version      = integer("version")
    val sectionsJson = text("sections_json")
    val active       = bool("active").default(true)
    val updatedAt    = long("updated_at")
    // Generación del seed que esta fila ya incorporó (ver seedScreens). Distinta de `version`:
    // `version` sube con cada edición del Editor y sirve para el If-None-Match del cliente;
    // `seed_version` solo sube cuando el server reemplaza el contenido por un seed más nuevo.
    // default(0) = "nunca recibió un seed con generación" → la primera vez se actualiza.
    val seedVersion  = integer("seed_version").default(0)
    override val primaryKey = PrimaryKey(slug)
}

object PushSubscriptions : Table("push_subscriptions") {
    val endpoint  = varchar("endpoint", 500)   // PK: único por navegador/dispositivo
    val userId    = varchar("user_id", 50)
    val p256dh    = varchar("p256dh", 200)     // clave pública del cliente (base64url)
    val auth      = varchar("auth", 50)        // auth secret (base64url)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(endpoint)
    init { index("idx_push_subscriptions_user_id", false, userId) }
}
