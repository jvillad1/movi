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
    /**
     * **Los períodos que arrancaron otro día**, como JSON: `{"2026-09":"2026-08-24"}`. Ver
     * `PeriodSettings.iniciosPropios` en :core.
     *
     * Una columna de texto y no una tabla aparte: son excepciones que se cuentan con los dedos, se
     * leen siempre enteras junto con el resto del perfil y nunca se filtran ni se ordenan por
     * ellas. Una tabla costaría un JOIN en cada lectura de perfil para no ganar nada.
     *
     * Nullable, por el mismo motivo que [periodCutoffDay]: es lo único que
     * `createMissingTablesAndColumns` puede agregar sin riesgo sobre una tabla con filas, dentro de
     * la transacción de arranque. Se lee como mapa vacío cuando falta.
     */
    val periodStarts = text("period_starts").nullable()
    /**
     * **Qué hace el período cuando el día de corte cae en fin de semana**: el nombre de un
     * `SiElCorteCaeEnFinDeSemana` (:core). Nullable por el mismo motivo que [periodCutoffDay]; se lee
     * como `ESE_DIA` —lo de siempre— cuando falta o no se entiende.
     */
    val periodWeekendRule = varchar("period_weekend_rule", 32).nullable()
    /**
     * Con cuántos días de anticipación avisar un vencimiento.
     *
     * Antes solo existía como variable de entorno del server (`REMINDER_LEAD_DAYS`): global para
     * todos y fuera del alcance del usuario. Es la deuda que el dueño nombró como regla — «otros
     * usuarios no van a poder gestionar desde Claude cambios en la app».
     *
     * Nullable y se lee como el default: las cuentas que ya existen no la tienen.
     */
    val reminderLeadDays = integer("reminder_lead_days").nullable()
    /**
     * El dueño pidió que el Inicio deje de recordarle que la captura de SMS nunca recibió nada
     * (ver `CapturaDeSms` en :core). Silencia el recordatorio del Inicio, **no** el hecho: la
     * pantalla «Mensajes del banco» lo sigue diciendo.
     *
     * Es una preferencia de la cuenta y no del dispositivo a propósito: el dueño mira el Inicio
     * desde el navegador y desde el teléfono, y un silencio que valiera solo en un navegador
     * volvería a aparecer en el otro. Por eso vive acá y no en `localStorage`.
     *
     * Nullable y se lee como `false`, por lo mismo que las dos de arriba: las cuentas que ya
     * existen no la tienen, y una columna nullable es el único DDL que
     * `createMissingTablesAndColumns` puede agregar sin riesgo dentro de la transacción de
     * arranque.
     */
    val smsAlertMuted = bool("sms_alert_muted").nullable()
    /**
     * **La versión de las sesiones abiertas.** Cada token lleva la versión vigente cuando se
     * firmó (claim `tv`, ver [com.jvillada.movi.server.auth.JwtConfig.CLAIM_VERSION]) y
     * `configureAuth` lo rechaza si ya no coincide con esta columna. Sube en tres lugares: cambiar
     * la contraseña, restablecerla por correo, y «Cerrar sesión en todos los aparatos». Subirla es
     * la única forma de echar a un teléfono perdido: el JWT dura 30 días y no hay lista de tokens.
     *
     * `NOT NULL DEFAULT 0` y no nullable como sus vecinas: un `ADD COLUMN … DEFAULT 0 NOT NULL` en
     * Postgres llena las filas existentes con el default sin reescribir nada, y 0 es exactamente
     * lo que un token viejo sin claim representa. Así las sesiones abiertas antes del despliegue
     * siguen valiendo. Lo cubre `SchemaDeArranqueTest` y `TokenVersionColumnTest`.
     */
    val tokenVersion = integer("token_version").default(0)
    /**
     * **Cuántos días sin capturas de un origen regular avisan** («banco mudo», Ola 2; ver
     * `origenesMudos` en :core). `0` apaga el aviso. Se elige desde la app (Captura del banco),
     * regla del dueño: nada que solo se cambie tocando código.
     *
     * Nullable y se lee como `DIAS_PARA_BANCO_MUDO_POR_DEFECTO`, por lo mismo que sus vecinas: es el
     * único DDL que `createMissingTablesAndColumns` puede agregar sin riesgo sobre una tabla con filas.
     */
    val diasParaBancoMudo = integer("dias_para_banco_mudo").nullable()
    /**
     * **¿Le llega por correo el resumen de cierre del período?** («Cerró tu período», ver
     * `CierreDelPeriodo` en :core). Se apaga desde la app (Perfil → Avisos): regla del dueño, nada
     * que solo se cambie tocando código.
     *
     * Nullable y se lee como `true`, por lo mismo que sus vecinas: es el único DDL que
     * `createMissingTablesAndColumns` puede agregar sin riesgo sobre una tabla con filas.
     */
    val cierrePorCorreo = bool("cierre_por_correo").nullable()
    /**
     * **Cuándo terminó (o descartó) la guía «Primer uso»**, en milisegundos; `null` = nunca. Por
     * usuario y en el server, para que la guía no reaparezca en otro aparato. La escribe
     * `POST /api/users/me/primer-uso` (la primera vez; después no se mueve).
     *
     * Las cuentas que ya existían quedan en `null` **sin migración**: la guía solo se abre para quien
     * además no tiene ninguna cuenta de dinero (`debeAbrirseLaGuiaDePrimerUso` en :core), así que el
     * dueño —con sus cuentas— no la ve. Nullable por lo mismo que sus vecinas: es el único DDL que
     * `createMissingTablesAndColumns` puede agregar sin riesgo sobre una tabla con filas.
     */
    val primerUsoEn = long("primer_uso_en").nullable()
    /**
     * **El ahorro de cada período** (27-oct-2026, ver `AhorroDelPeriodo.kt` en :core): el nombre de un
     * `TipoDeAhorro` —`NINGUNO`, `MONTO` o `PORCENTAJE`— y su valor (pesos, o un porcentaje entero de
     * 1 a 90). «Te quedan» lo resta; Tu plata no. Nullable por lo mismo que sus vecinas, y se leen como
     * `NINGUNO` / 0: nadie ve un cambio hasta elegirlo. Son columnas de `users`, no una tabla con
     * `user_id`: no van a `TABLAS_DEL_USUARIO` (borrar la cuenta borra la fila entera).
     */
    val ahorroDelPeriodoTipo = varchar("ahorro_del_periodo_tipo", 16).nullable()
    val ahorroDelPeriodoValor = long("ahorro_del_periodo_valor").nullable()
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
    /**
     * Para qué —y solo para qué— se puede usar esta plata sin castigo. NULL = libre.
     *
     * NULLABLE a propósito: el ALTER que emite `createMissingTablesAndColumns` no puede fallar
     * sobre una tabla con datos, y toda cuenta existente queda en NULL, que es la verdad — nacieron
     * sin condición porque el campo no existía. Ver `Account.condicionadaA` en :core.
     */
    val conditionedTo = varchar("conditioned_to", 60).nullable()
    /**
     * **La edad de esta versión de la cuenta.** NULL = nadie la editó, que es la verdad de toda
     * cuenta que ya existe — nacieron antes de que la columna existiera.
     *
     * Es lo que le deja al `POST /api/accounts` (un upsert por id) hacer perder al reenvío del
     * teléfono contra un renombre más nuevo hecho en la web. Ver
     * [com.jvillada.movi.shared.model.Account.lastEditedAt] y `pisaElReenvio` en `EventRoutes.kt`,
     * que es la misma función que decide para los movimientos.
     *
     * NULLABLE y sin backfill, por el mismo motivo que la de arriba y que `last_edited_at` de
     * `financial_events`: el ALTER que emite `createMissingTablesAndColumns` corre DENTRO de la
     * transacción de arranque y no puede fallar sobre una tabla con datos.
     */
    val lastEditedAt = long("last_edited_at").nullable()
    /**
     * **Esta cuenta es un bien** (la casa, el carro), y de qué clase: `INMUEBLE`, `VEHICULO`,
     * `OTRO`. NULL = no es un bien, que es la verdad de toda cuenta que ya existe. Ver `Bien` y
     * `Account.bien` en :core, donde está escrito por qué es una columna y no un valor nuevo de
     * `type`, y qué ve un APK viejo.
     *
     * Las cuatro son NULLABLE y sin backfill por el motivo de siempre: el ALTER que emite
     * `createMissingTablesAndColumns` corre DENTRO de la transacción de arranque y no puede fallar
     * sobre una tabla con datos (`accounts` ya está en esa lista).
     */
    val assetKind = varchar("asset_kind", 20).nullable()
    /** Lo que vale el bien, en pesos. El saldo de la cuenta no se usa para nada en un bien. */
    val assetValue = long("asset_value").nullable()
    /** De cuándo es ese valor (la fecha del avalúo), ISO `yyyy-MM-dd`. */
    val assetValuedOn = varchar("asset_valued_on", 10).nullable()
    /** La cuenta de deuda que lo financia, si hay una. Solo explica; no mueve ningún total. */
    val assetDebtId = varchar("asset_debt_id", 50).nullable()
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

/**
 * **Un importe que concilió un movimiento que ya pertenecía a OTRO importe.**
 *
 * Reimportar el mismo extracto deja `Events.statementImportId` apuntando al primer importe (así el
 * detalle del primero no pierde filas), y sin esta tabla ese vínculo no quedaba en ningún lado:
 * deshacer el primero anulaba todas las compras aunque el segundo todavía las reclamara. Con esto,
 * deshacer un importe le pasa el movimiento al que sigue vivo en vez de anularlo.
 *
 * Tabla NUEVA, con la clave primaria compuesta dentro del CREATE TABLE: ningún CREATE INDEX suelto
 * que pueda fallar sobre una base con datos.
 */
object StatementImportMatches : Table("statement_import_matches") {
    val importId = varchar("import_id", 50)
    val eventId  = varchar("event_id", 50)
    val userId   = varchar("user_id", 50)
    override val primaryKey = PrimaryKey(importId, eventId)
}

/**
 * Los archivos que el dueño guarda en Movi: extractos, nóminas, contratos.
 *
 * El contenido va en la MISMA tabla y no en un bucket aparte, a propósito. Movi corre en un
 * contenedor de Railway con disco efímero —lo que se escribe ahí se pierde en cada despliegue—
 * y agregar un S3 significaría una credencial más que el dueño tendría que crear y rotar, para
 * un volumen que hoy son unos pocos PDF por mes. Postgres ya está, ya se respalda y ya sabe
 * borrar las filas de un usuario cuando el usuario se va.
 *
 * `content` es `bytea`. El tope de tamaño lo pone la ruta (ver MAX_DOCUMENTO_BYTES), no la
 * columna: un límite en el esquema fallaría con un error de base de datos en vez de con un
 * mensaje que el dueño pueda entender.
 */
object Documents : Table("documents") {
    val id         = varchar("id", 50)
    val userId     = varchar("user_id", 50)
    val name       = varchar("name", 255)
    val kind       = varchar("kind", 20)
    val mimeType   = varchar("mime_type", 120)
    val sizeBytes  = long("size_bytes")
    val uploadedAt = long("uploaded_at")
    val accountId  = varchar("account_id", 50).nullable()
    val period     = varchar("period", 50).nullable()
    val notes      = varchar("notes", 500).nullable()
    val content    = binary("content")
    override val primaryKey = PrimaryKey(id)
    init { index("idx_documents_user_id", false, userId) }
}

/**
 * **Lo que Movi ya leyó de un papel** (Ola 2 · «Compartir con Movi»): la regla del costo es que
 * **el mismo archivo no se manda dos veces a Claude**. Se identifica por la [huella] de sus bytes
 * (SHA-256), no por el documento: compartir dos veces la misma captura crea dos filas en Documentos
 * pero una sola lectura.
 *
 * - [tipo] es `COMPROBANTE` o `EXTRACTO` (ver `QueEsElPapel` en :core), y [datos] lo que se leyó
 *   en JSON (`ComprobanteLeido` o `ExtractoLeido`).
 * - [porRevisarId] es la fila que el comprobante dejó en `sms_messages` (`cmp_<documento>`), y
 *   [eventoId] el movimiento con el que se confirmó: el enlace entre el papel y el movimiento.
 *
 * Tabla NUEVA: entra por `SchemaUtils.create`, con su clave primaria compuesta adentro del CREATE
 * TABLE y sin índices sueltos que puedan fallar en el arranque. No va al «Descargar tus datos»: es
 * memoria interna de Movi, como las conversaciones con Movi AI — el papel y el movimiento sí van.
 */
object LecturasDePapeles : Table("lecturas_de_papeles") {
    val userId = varchar("user_id", 50)
    val huella = varchar("huella", 64)
    val documentoId = varchar("documento_id", 50)
    val tipo = varchar("tipo", 20)
    val datos = text("datos")
    val modelo = varchar("modelo", 60)
    val leidoEn = long("leido_en")
    val porRevisarId = varchar("por_revisar_id", 60).nullable()
    val eventoId = varchar("evento_id", 50).nullable()
    override val primaryKey = PrimaryKey(userId, huella)
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
    /**
     * **Cuánto de esa cuota NO bajaba la deuda** — interés del período + seguro de vida deudor.
     * Solo la pata de la deuda de un pago de cuota sobre un crédito que amortiza; NULL en todo lo
     * demás, y ese NULL significa «par simétrico». Ver
     * [com.jvillada.movi.shared.model.FinancialEvent.noAmortiza] para el porqué completo: sin esta
     * columna, corregir el monto de una cuota cuyo capital se había clampado a cero borraba deuda
     * en silencio.
     *
     * Nullable, y por el mismo motivo que [transferId] y [createdAt]: la agrega sola
     * `createMissingTablesAndColumns(Events)` al arrancar sobre la base ya desplegada, y una
     * columna nullable por esa vía es segura sobre una tabla con datos. **Sin índice**: nunca se
     * filtra ni se ordena por ella — solo se lee junto con su propia fila. Un `CREATE INDEX` que
     * falle es lo que puede dejar el server sin levantar, porque estas migraciones corren dentro
     * de la transacción de arranque (ver `NoAmortizaColumnTest`).
     *
     * Las filas que ya existen quedan en NULL a propósito: los pagos de cuota anteriores a esta
     * ola tienen las dos patas iguales, así que son simétricos de verdad y NULL los describe bien.
     */
    val noAmortiza           = long("no_amortiza").nullable()
    /**
     * **«Este no se repite»**, dicho por el dueño sobre este movimiento. Ver
     * [com.jvillada.movi.shared.model.FinancialEvent.noSeRepite] para el porqué completo: el
     * reconocimiento de recurrentes es por NOMBRE, y sin esta columna un gasto suelto que se llama
     * igual que una suscripción quedaba marcado como recurrente para siempre.
     *
     * `.default(false)` y NO nullable, a diferencia de [transferId], [createdAt] y [noAmortiza]:
     * acá el default SÍ describe bien lo que ya existe —nadie marcó nada— y el ALTER que emite
     * `createMissingTablesAndColumns(Events)` deja en FALSE las filas que ya estaban, o sea que
     * todo lo que hoy se reconoce se sigue reconociendo. Es el mismo mecanismo que ya usó
     * `remind_me` sobre tablas con datos.
     *
     * **Sin índice**: nunca se filtra ni se ordena por ella en SQL — el filtro «Recurrentes» se
     * arma en el cliente, sobre eventos ya leídos. Un `CREATE INDEX` que falle es lo que puede
     * dejar el server sin levantar, porque estas migraciones corren dentro de la transacción de
     * arranque.
     */
    val noSeRepite           = bool("no_se_repite").default(false)
    /**
     * **Cuándo se corrigió por última vez** esta fila, o NULL si nadie la tocó desde que nació. Ver
     * [com.jvillada.movi.shared.model.FinancialEvent.lastEditedAt] para el porqué completo; el
     * resumen es que `POST /api/events` es un upsert por id y sin esta columna el reenvío de una
     * copia vieja del teléfono pisaba en silencio la corrección que el dueño acababa de hacer en
     * la web.
     *
     * Nullable, y por el mismo motivo que [transferId], [createdAt] y [noAmortiza]: la agrega sola
     * `createMissingTablesAndColumns(Events)` al arrancar sobre la base ya desplegada, y una
     * columna nullable por esa vía es segura sobre una tabla con datos. Las filas que ya existen
     * quedan en NULL, que es exactamente lo que significa: nadie las editó después de crearlas —
     * o al menos no desde que esto se guarda—, así que el reenvío de un APK viejo las sigue
     * pisando igual que hasta hoy y no se pierde nada de lo que ya está.
     *
     * **Sin índice**: nunca se filtra ni se ordena por ella — solo se lee junto con su propia fila,
     * y un `CREATE INDEX` que falle es lo que puede dejar el server sin levantar (estas migraciones
     * corren dentro de la transacción de arranque).
     */
    val lastEditedAt         = long("last_edited_at").nullable()
    /**
     * Vuelta 63 · a cuántas cuotas se difirió la compra, cuando el extracto de movimientos de la tarjeta
     * lo dijo (ver [com.jvillada.movi.shared.model.FinancialEvent.cuotas]). Nullable y sin índice: el
     * `ADD COLUMN cuotas INT NULL` que emite `createMissingTablesAndColumns(Events)` es seguro sobre la
     * tabla con datos, y lo viejo queda en NULL («no se sabe»), sin migración retroactiva. Su caso está
     * en `SchemaDeArranqueTest`.
     */
    val cuotas               = integer("cuotas").nullable()
    /**
     * Vuelta 67 · **de qué compra es parte** esta fila: el id del movimiento original cuando se dividió
     * en categorías (ver [com.jvillada.movi.shared.model.FinancialEvent.repartoDe]). Nullable y sin
     * índice, como [cuotas]: el `ADD COLUMN reparto_de VARCHAR(50) NULL` del arranque es seguro sobre la
     * tabla con datos, y lo de antes queda en NULL (las partes viejas no tienen cómo deshacerse). Se
     * filtra siempre junto con `user_id`, que ya tiene índice. Su caso está en `SchemaDeArranqueTest`.
     */
    val repartoDe            = varchar("reparto_de", 50).nullable()
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

/**
 * **Los resúmenes de cierre que ya salieron por correo**, uno por usuario y período («Cerró tu
 * período», ver `CierreDelPeriodo` en :core). El barrido corre cada pocas horas y los primeros días del
 * período nuevo vuelve a mirar: esta fila es lo que hace que el correo salga UNA vez. Se escribe
 * ANTES de mandar (la clave primaria es el candado) y se borra si el envío falla, para reintentar en
 * el barrido siguiente. Tabla nueva: entra solo al `SchemaUtils.create` del arranque.
 */
object CierresEnviados : Table("cierres_enviados") {
    val userId    = varchar("user_id", 50)
    val periodo   = varchar("periodo", 7)
    val enviadoEn = long("enviado_en")
    override val primaryKey = PrimaryKey(userId, periodo)
}

/**
 * **Las tarjetas «Cerró tu período» que el dueño cerró en Hoy**, por período. En el server y no en el
 * aparato: cerrarla en el teléfono la cierra también en la web. Tabla nueva: entra solo al
 * `SchemaUtils.create` del arranque.
 */
object CierresDescartados : Table("cierres_descartados") {
    val userId       = varchar("user_id", 50)
    val periodo      = varchar("periodo", 7)
    val descartadoEn = long("descartado_en")
    override val primaryKey = PrimaryKey(userId, periodo)
}

/**
 * **«Editar inicio»: qué secciones de Hoy ve el dueño y en qué orden** (ver `PreferenciasDelInicio`
 * en `:core` y `routes/InicioRoutes.kt`). Una fila por usuario, solo si eligió algo: «Volver al
 * orden de Movi» la borra. Las dos listas van como JSON de tipos de sección. En el server y no en
 * el aparato: lo que esconde en el teléfono tampoco lo ve en la web. Tabla nueva: entra solo al
 * `SchemaUtils.create` del arranque, con la clave primaria dentro del CREATE.
 */
object InicioPersonalizado : Table("inicio_personalizado") {
    val userId        = varchar("user_id", 50)
    val orden         = text("orden")
    val ocultas       = text("ocultas")
    /**
     * Ola R1: las secciones opcionales que el dueño prendió (`PreferenciasDelInicio.visibles`), en
     * JSON. Nullable porque la tabla ya tiene filas: es lo único que `createMissingTablesAndColumns`
     * puede agregar sin riesgo. `null` = ninguna.
     */
    val visibles      = text("visibles").nullable()
    val actualizadoEn = long("actualizado_en")
    override val primaryKey = PrimaryKey(userId)
}

/**
 * **Las reglas de comercio que el importador de extractos aprende** («RAPPI» → «Comida»): cuando el
 * dueño cambia la categoría de una fila al importar, se guarda acá y viaja en el prompt de la
 * próxima lectura (ver `MerchantRulesStore`).
 *
 * Vivían en un archivo JSON del contenedor (`movi-data/merchant-rules-<usuario>.json`), y Railway
 * arma un contenedor nuevo en cada despliegue: **cada despliegue las borraba**. Tabla nueva: entra
 * solo al `SchemaUtils.create` del arranque. El contenido del archivo, si todavía existe, se pasa
 * acá al arrancar (`Migrations.pasarLasReglasDeComercioALaBase`).
 */
object ReglasDeComercio : Table("reglas_de_comercio") {
    val userId = varchar("user_id", 50)
    val patron = varchar("patron", 255)
    val categoria = varchar("categoria", 100)
    val actualizadaEn = long("actualizada_en")
    override val primaryKey = PrimaryKey(userId, patron)
}

/**
 * Ola 4: **los avisos de «lo que se sale de lo normal» que el dueño descartó** con «Está bien», por
 * su huella (`Anomalia.huella`). La misma situación da la misma huella en cada lectura, así que una
 * fila acá la saca para siempre; una situación nueva (otro cobro, otro período) trae otra huella y
 * vuelve a avisar. Tabla nueva: entra solo al `SchemaUtils.create` del arranque.
 */
object AnomaliasDescartadas : Table("anomalias_descartadas") {
    val userId       = varchar("user_id", 50)
    val huella       = varchar("huella", 300)
    val descartadaEn = long("descartada_en")
    override val primaryKey = PrimaryKey(userId, huella)
}

/**
 * **Los débitos automáticos que el dueño dijo que no se cobraron** («No se cobró»), por regla y
 * período del vencimiento. La propuesta se deriva en cada lectura (ver `DebitosAutomaticosRoutes`),
 * así que el «no» tiene que quedar escrito o volvería en el próximo F5. El período siguiente trae
 * otro vencimiento y vuelve a proponer. Tabla nueva: entra solo al `SchemaUtils.create` del arranque.
 */
object DebitosDescartados : Table("debitos_automaticos_descartados") {
    val userId       = varchar("user_id", 50)
    val ruleId       = varchar("rule_id", 80)
    val periodo      = varchar("periodo", 7)
    val descartadoEn = long("descartado_en")
    override val primaryKey = PrimaryKey(userId, ruleId, periodo)
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
 *
 * Ola B: [icono] y [color], por el mismo motivo que [pinnedType] — no son datos, son cómo el
 * dueño quiere VER la categoría. Columnas nuevas → van también en `createMissingTablesAndColumns`
 * (`DatabaseFactory.kt`), porque esta tabla ya existe en producción desde Ola 10 y un
 * `SchemaUtils.create` sobre una tabla que ya está no emite ningún `ALTER`.
 */
object CategoryPrefs : Table("category_prefs") {
    val userId     = varchar("user_id", 50)
    val name       = varchar("name", 100)
    val hidden     = bool("hidden").default(false)
    /** "EXPENSE" | "INCOME" | "BOTH", o NULL = sin fijar (manda el catálogo, o el uso). */
    val pinnedType = varchar("pinned_type", 10).nullable()
    /** Clave de texto (p. ej. "restaurante"), o NULL = el ícono que Movi asigna por defecto. */
    val icono      = varchar("icono", 40).nullable()
    /** Clave de texto (p. ej. "naranja"), o NULL = el color que Movi asigna por defecto. */
    val color      = varchar("color", 20).nullable()
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
    /**
     * «El banco la debita sola»; ver `RecurringRule.seDebitaSolo`. Nullable y se lee como `false`:
     * `ADD COLUMN … NULL` es el único DDL que `createMissingTablesAndColumns` puede correr sin riesgo
     * dentro de la transacción de arranque, y `RecurringRules` ya está en esa lista.
     */
    val seDebitaSolo       = bool("se_debita_solo").nullable()
    val accountId          = varchar("account_id", 50).nullable()
    /**
     * **Desde cuándo corre la regla** (ISO `"2026-08-15"`), o NULL = desde siempre. Ver
     * [com.jvillada.movi.shared.model.RecurringRule.activeFrom] y `arranqueDeLaRegla`, que es
     * quien la interpreta: las ocurrencias anteriores al arranque no existen.
     *
     * **Lo que guarda esta columna es siempre la fecha del MOVIMIENTO que originó la regla**, y
     * por eso `arranqueEsDesembolso` no tiene columna ni la necesita: acá es siempre `false`. La
     * otra mitad del campo —la fecha de DESEMBOLSO de un crédito, que se lee estricta porque un
     * desembolso no es una cuota— solo vive en las reglas sintéticas de `CreditReminders`, que se
     * arman en memoria y nunca se guardan.
     *
     * Hasta esta ola el campo existía en el modelo pero no tenía columna: solo lo llenaban esas
     * reglas sintéticas. Lo trajo acá «esto se repite» desde un movimiento, para que una regla
     * nacida hoy no se inventara vencimientos de los períodos de antes.
     *
     * Nullable, y por eso es DDL seguro: `createMissingTablesAndColumns` emite
     * `ADD COLUMN active_from VARCHAR(10) NULL` sobre la tabla que ya tiene filas, y todas las
     * reglas que el dueño ya escribió a mano quedan en NULL — que es la verdad: corren desde
     * siempre, exactamente como se comportaban ayer.
     */
    val activeFrom         = varchar("active_from", 10).nullable()
    /**
     * **Cuándo se creó la regla** (epoch ms), solo del server: no viaja en `RecurringRule` ni en
     * ninguna respuesta.
     *
     * La lee «Tus períodos»: un período que cerró antes de que la regla existiera no puede decir
     * «pendiente» sobre ella, porque en ese período no había nada que pagar. [activeFrom] no
     * sirve para esto — es el arranque del PLAN (y casi siempre NULL, «desde siempre»), no el día
     * en que el dueño lo escribió.
     *
     * Nullable, y por eso es DDL seguro: `createMissingTablesAndColumns` emite `ADD COLUMN
     * created_at BIGINT NULL` y las reglas que ya existían quedan en NULL — no se sabe cuándo
     * nacieron, y «Tus períodos» lo deduce de su evidencia (ver `PeriodosRoutes`).
     */
    val createdAt          = long("created_at").nullable()
    /**
     * Ola V: a qué [com.jvillada.movi.server.db.KnownDestinations] va este traspaso todos los
     * meses — ver [com.jvillada.movi.shared.model.RecurringRule.destinoConocidoId], que es quien
     * explica el porqué. Nullable, y por eso es DDL seguro: `createMissingTablesAndColumns` emite
     * `ADD COLUMN destino_conocido_id VARCHAR(50) NULL` sobre la tabla con filas, y toda regla que
     * ya existe queda en NULL — la verdad: hasta hoy no había ningún destino que asociarle. Sin FK
     * a `known_destinations`, mismo motivo que [accountId] no la tiene con `accounts`: si el
     * destino se borra, `DestinoRoutes` suelta la referencia en vez de borrar la regla.
     */
    val destinoConocidoId  = varchar("destino_conocido_id", 50).nullable()
    /**
     * Vuelta 63 · la deuda a la que va el abono mensual (ver
     * [com.jvillada.movi.shared.model.RecurringRule.haciaDeuda]). Nullable, y por eso DDL seguro:
     * `createMissingTablesAndColumns` emite `ADD COLUMN hacia_deuda VARCHAR(50) NULL` y toda regla que
     * ya existe queda en NULL, que es la verdad. Sin FK: si la deuda se borra, `AccountRoutes` borra
     * la regla del abono (sin la deuda no hay a dónde abonar). Su caso está en `SchemaDeArranqueTest`.
     */
    val haciaDeuda         = varchar("hacia_deuda", 50).nullable()
    /**
     * Vuelta 72 · desde qué día está en pausa la regla (ISO), o NULL = activa (ver
     * [com.jvillada.movi.shared.model.RecurringRule.pausadaDesde]). Nullable: `createMissingTablesAndColumns`
     * emite `ADD COLUMN pausada_desde VARCHAR(10) NULL` y toda regla que ya existe queda activa, como
     * estaba. Solo la escriben `POST …/pausar` y `…/reanudar`. Su caso está en `SchemaDeArranqueTest`.
     */
    val pausadaDesde       = varchar("pausada_desde", 10).nullable()
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

/**
 * **«No, ese movimiento no es esto»** — el rechazo de un emparejamiento, guardado.
 *
 * Es la contracara de [RecurringOccurrences]: esa tabla guarda los «sí», esta los «no». Nació
 * cuando Movi empezó a emparejar solo (`ocurrenciaConcluyente`): hasta entonces el rechazo vivía
 * en un `var` de la pantalla y estaba bien que así fuera —rechazar una propuesta no es un hecho
 * sobre la plata de nadie, y al recargar se volvía a ofrecer sin costo—. Con emparejamiento
 * automático eso deja de alcanzar: si Movi empareja mal, un «no» que se pierde al recargar
 * significa que la siguiente lectura vuelve a dar el periodo por ocurrido, para siempre.
 *
 * **La clave es (usuario, regla, movimiento), no el movimiento solo**, y esto ya costó un bug en
 * la pantalla: con «Agua», «Gas» e «Internet» todas en «Servicios», el pago del gas se propone en
 * las tres. Rechazarlo en la regla del agua —correcto, no era el agua— no puede quitárselo a la
 * del gas, que es donde sí era el bueno.
 *
 * Y **no lleva periodo**: un movimiento tiene una sola fecha, así que solo puede ser la ocurrencia
 * de un periodo; decir «este movimiento no es esta regla» lo dice para siempre, sin que haya que
 * repetirlo mes a mes.
 *
 * La unicidad viaja en la clave primaria compuesta y por lo tanto DENTRO del `CREATE TABLE`: no
 * queda ningún `CREATE INDEX` suelto que pueda fallar sobre una base con datos y dejar el server
 * sin arrancar (ver `DatabaseFactory.crearYActualizarSchema`). Mismo criterio que
 * [RecurringOccurrences].
 */
object OccurrenceRejections : Table("occurrence_rejections") {
    val userId     = varchar("user_id", 50)
    val ruleId     = varchar("rule_id", 50)
    val eventId    = varchar("event_id", 50)
    val rejectedAt = long("rejected_at")
    override val primaryKey = PrimaryKey(userId, ruleId, eventId)
}

object SmsMessages : Table("sms_messages") {
    val id     = varchar("id", 50)
    val userId = varchar("user_id", 50)
    val time   = varchar("time", 50)
    val bank   = varchar("bank", 100)
    val text   = text("text")
    val state  = varchar("state", 20)
    val det    = varchar("det", 255)
    /**
     * **Por qué Movi apartó este mensaje** (3-oct-2026): el nombre de un `MotivoDeApartado` cuando
     * entró —o quedó— `ignored` porque no es un movimiento, o `DEVUELTO` cuando el dueño dijo «Era
     * un movimiento» y no se vuelve a apartar. `NULL` en todo lo demás. NULLABLE a propósito: la
     * tabla existe en producción con los mensajes del dueño, y el ALTER que emite
     * `createMissingTablesAndColumns` no puede fallar sobre filas que ya están (ver
     * `SmsMessagesMotivoApartadoColumnTest`).
     */
    val motivoApartado = varchar("motivo_apartado", 40).nullable()
    /**
     * **«No es el mismo pago»** (4-oct-2026): el dueño dijo que este aviso es un pago aparte, y
     * nunca más se junta con otros avisos (ver `AvisosDelMismoPago.kt`). `TRUE` solo ahí; `NULL` en
     * todo lo demás. NULLABLE por lo mismo que [motivoApartado].
     */
    val noEsElMismoPago = bool("no_es_el_mismo_pago").nullable()
    /**
     * **El movimiento con que se confirmó** este aviso, cuando se sabe. Lo escriben las tres
     * confirmaciones: `POST /api/sms/{id}/confirm` (el movimiento recién creado, o el que ya estaba
     * —«Es este»—; si un APK viejo no lo manda, el server lo deduce cuando puede, ver
     * `eventoDeLaConfirmacion`), el confirmar de los avisos de un mismo pago (`POST
     * /api/sms/grupo/{id}/confirmar`) y «Agregar» cuando el dueño dice que lo que anota es un aviso
     * pendiente. `NULL` en lo confirmado antes de esto, y en lo que no está confirmado. NULLABLE por
     * lo mismo que [motivoApartado].
     */
    val eventoId = varchar("evento_id", 64).nullable()
    /**
     * **Cuándo se confirmó**, en epoch ms (4-oct-2026, la auditoría de la ingesta). Con esto se puede
     * medir cuánto tarda el dueño entre que llega un aviso y lo confirma, y fechar un aviso confirmado
     * que se quedó sin movimiento. Se escribe una sola vez: una segunda confirmación no lo mueve.
     * `NULL` en lo confirmado antes de esta columna. NULLABLE por lo mismo que [motivoApartado].
     */
    val confirmadoEn = long("confirmado_en").nullable()
    /**
     * **Todos los movimientos de un aviso repartido en partes** (vuelta 27, ver `ElAvisoEnPartes.kt`
     * en `:core`), como JSON de una lista de ids, en orden. [eventoId] sigue apuntando a UNO —la pata
     * del dinero de la primera parte—, así que lo que lo lee (el cuadre por aviso, «ya anotado», la
     * memoria de categorías) no cambia; esto los guarda **todos**, para que un reintento conteste lo
     * mismo que el primer toque. `NULL` en un aviso que no se repartió. NULLABLE por lo mismo que
     * [motivoApartado].
     */
    val partesEventos = text("partes_eventos").nullable()
    /**
     * **Cómo se repartió**, para proponerlo la próxima vez (vuelta 27): JSON de `RepartoRecordado` —la
     * cuenta de la que salió, el monto y las partes como las eligió el dueño—. La memoria del reparto
     * es el aviso mismo: el último aviso repartido a la misma persona (`claveDelReparto`) es «la última
     * vez». Sin tabla nueva. `NULL` en un aviso que no se repartió.
     */
    val reparto = text("reparto").nullable()
    override val primaryKey = PrimaryKey(id, userId)  // per-user: the same SMS id may exist for different users
    init { index("idx_sms_messages_user_id", false, userId) }
}

/**
 * **Lo que el dueño le preguntó al asistente y lo que este le contestó.**
 *
 * Nació de un diagnóstico imposible: el chat vivía solo en el teléfono, así que cuando una
 * respuesta salía mal lo único que quedaba del lado del server eran las fichas gastadas. «El
 * asistente no supo» no se puede arreglar sin saber qué se preguntó, qué consultó y qué contestó.
 *
 * **Es la tabla más sensible de Movi**, y por eso tiene reglas propias, todas a propósito:
 *
 * - **No hay endpoint que la lea.** Se consulta por `psql`, como todo lo demás que es de
 *   diagnóstico. Una tabla con las preguntas de alguien sobre su plata no necesita además una
 *   puerta HTTP que alguien tenga que proteger bien para siempre.
 * - **Las imágenes no entran.** Una foto de un recibo es justo lo que no hay que duplicar: en
 *   [imagen] queda que la hubo, no la foto.
 * - **Se recorta y se caduca.** Cada campo tiene tope y de cada dueño se guardan las últimas
 *   [CUANTAS_CONVERSACIONES_SE_GUARDAN]: lo que sirve para diagnosticar es lo de ayer, no lo del
 *   año pasado, y un historial infinito es un riesgo que crece solo.
 * - **Guardar nunca puede romper una respuesta.** Ver `guardarLaConversacion`.
 */
object AiTurns : Table("ai_turns") {
    val id        = varchar("id", 50)
    val userId    = varchar("user_id", 50)
    val creadoEn  = long("created_at")
    /** Lo último que escribió el dueño: es lo que hay que reproducir cuando algo sale mal. */
    val pregunta  = text("pregunta")
    val respuesta = text("respuesta")
    /** Qué consultó y qué le devolvió cada consulta, en JSON. Vacío cuando contestó de memoria. */
    val consultas = text("consultas")
    val modelo    = varchar("modelo", 60)
    /** Si se escaló al modelo caro (ver `laPreguntaPideCriterio`). */
    val criterio  = bool("criterio")
    val vueltas   = integer("vueltas")
    val fichasEntrada = long("fichas_entrada")
    val fichasCache   = long("fichas_cache")
    val fichasSalida  = long("fichas_salida")
    /** Hubo una foto adjunta. La foto NO se guarda. */
    val imagen    = bool("imagen")
    /**
     * **Las cifras que llegaron al dueño sin respaldo en sus datos**, tal como las escribió el
     * modelo («$185.831 · 12 %»), o NULL si todas estaban respaldadas. Es lo que vuelve medible el
     * «sin inventar»: `count(*) where cifras_sin_respaldo is not null`. Ver `responderSinInventar`.
     *
     * Nullable por la regla de siempre: la tabla ya existe en producción, y
     * `createMissingTablesAndColumns` solo puede agregar sin riesgo una columna NULL.
     */
    val cifrasSinRespaldo = text("cifras_sin_respaldo").nullable()
    /**
     * Las cifras sin respaldo de la PRIMERA respuesta, las que dispararon el único reintento; NULL si
     * no hubo reintento. Separada de la de arriba para poder contar las dos cosas: cuántas veces el
     * modelo inventó, y cuántas el reintento no alcanzó a arreglarlo.
     */
    val cifrasCorregidas = text("cifras_corregidas").nullable()
    override val primaryKey = PrimaryKey(id)
    init { index("idx_ai_turns_user", false, userId) }
}

/**
 * **Desde cuándo corre la conversación en curso con el asistente** (Ola 3). Una fila por dueño:
 * «Nueva conversación» la mueve a ahora, y `GET /api/ai/conversacion` devuelve solo los turnos de
 * [AiTurns] posteriores. No borra nada: los turnos viejos siguen ahí para diagnosticar (y se podan
 * solos, ver `CUANTAS_CONVERSACIONES_SE_GUARDAN`).
 *
 * Tabla nueva y no una columna en `users`: entra por `SchemaUtils.create`, que no puede fallar
 * sobre filas existentes.
 */
object ConversacionesDelAsistente : Table("asistente_conversaciones") {
    val userId     = varchar("user_id", 50)
    val empezadaEn = long("empezada_en")
    override val primaryKey = PrimaryKey(userId)
}

/**
 * **Lo que el asistente propuso hacer, y lo que el dueño decidió** (Ola 3 · «Movi actúa»). Una fila
 * por tarjeta. Nada de acá escribe en los datos del dueño: la acción la hace el endpoint de siempre
 * cuando él toca «Hacerlo»; esto solo recuerda que se propuso y qué dijo, para volver a pintar la
 * tarjeta al recargar la conversación y para que el asistente sepa en el turno siguiente que le
 * dijeron «No».
 *
 * [datos] es la `AccionPropuesta` entera en JSON: lo que el dueño vio es lo que se guarda.
 */
/**
 * **«Lo que Movi sabe de ti»** (Ola 3): lo que el dueño le contó al asistente y confirmó guardar.
 * Entra al contexto de cada conversación con tope (ver `memoriaParaElContexto`). Tabla nueva:
 * entra por `SchemaUtils.create`.
 */
object MemoriaDelAsistente : Table("memoria_del_asistente") {
    val id          = varchar("id", 50)
    val userId      = varchar("user_id", 50)
    val texto       = varchar("texto", 400)
    /** `CONVERSACION` (lo propuso el asistente y él confirmó) o `A_MANO`. */
    val origen      = varchar("origen", 20)
    /** La propuesta que lo originó, si vino de una conversación. */
    val propuestaId = varchar("propuesta_id", 50).nullable()
    val creadoEn    = long("creado_en")
    val editadoEn   = long("editado_en").nullable()
    override val primaryKey = PrimaryKey(id)
    init { index("idx_memoria_del_asistente_user", false, userId) }
}

object AccionesPropuestas : Table("asistente_propuestas") {
    val id         = varchar("id", 50)
    val userId     = varchar("user_id", 50)
    /** El turno de `ai_turns` que la propuso. */
    val turnoId    = varchar("turno_id", 50)
    val tipo       = varchar("tipo", 30)
    val datos      = text("datos")
    /** `PENDIENTE`, `HECHA` o `RECHAZADA`. */
    val estado     = varchar("estado", 20)
    val creadaEn   = long("creada_en")
    val resueltaEn = long("resuelta_en").nullable()
    override val primaryKey = PrimaryKey(id)
    init { index("idx_asistente_propuestas_user", false, userId) }
}

object Credits : Table("credit_terms") {
    /**
     * Libranza: la cuota se descuenta de la nómina. Nullable y se lee como `false` — las filas
     * que ya existen no la tienen, y una columna nullable es lo único que
     * `createMissingTablesAndColumns` puede agregar sin riesgo dentro de la transacción de
     * arranque.
     */
    val payrollDeduction = bool("payroll_deduction").nullable()
    // Nullable a propósito: `createMissingTablesAndColumns` corre DENTRO de la transacción de
    // arranque, y una columna nullable es DDL que no puede fallar sobre filas existentes.
    val paidBy = varchar("paid_by", 60).nullable()
    /**
     * Seguro de vida deudor incluido en la cuota; ver `CreditTerms.insuranceMonthly`. Nullable por
     * lo mismo que las dos de arriba: `createMissingTablesAndColumns` corre DENTRO de la
     * transacción de arranque, y `ALTER TABLE … ADD COLUMN … NULL` es el único DDL que no puede
     * fallar sobre filas que ya existen. Un DDL caído acá deja el server sin arrancar y al dueño
     * sin app.
     */
    val insuranceMonthly = long("insurance_monthly").nullable()
    /**
     * El cuarto renglón de la cuota: ni interés, ni seguro, ni capital. Ver
     * `CreditTerms.otrosCargosMensuales` — los $25.000 de «otros conceptos» que el Banco de
     * Occidente le cobra al Vehículo 8761 y que Movi contaba como abono a capital.
     *
     * Nullable por lo mismo que `insurance_monthly` y las dos de arriba:
     * `createMissingTablesAndColumns` corre DENTRO de la transacción de arranque, y
     * `ALTER TABLE … ADD COLUMN … NULL` es el único DDL que no puede fallar sobre las filas que ya
     * están. **Y `Credits` tiene que seguir en esa lista**, o esta columna se queda en el código y
     * cada consulta de créditos revienta en producción con «column does not exist»: fijado por
     * `SchemaDeArranqueTest` y por `OtrosCargosColumnTest`.
     */
    val otrosCargosMensuales = long("otros_cargos_mensuales").nullable()
    /**
     * «Este crédito no cobra intereses»; ver `CreditTerms.sinIntereses`. Nullable y se lee como
     * `false`, por lo mismo que `payroll_deduction`: `createMissingTablesAndColumns` corre DENTRO de
     * la transacción de arranque, y `ADD COLUMN … NULL` es el único DDL que no puede fallar sobre
     * las filas que ya están. `Credits` ya está en esa lista.
     */
    val sinIntereses = bool("sin_intereses").nullable()
    /**
     * La cuenta de la que el banco debita la cuota solo; ver `CreditTerms.debitoAutomaticoDesde`.
     * Nullable por lo mismo que las de arriba (`ADD COLUMN … NULL` dentro de la transacción de
     * arranque no puede fallar), y sin FK a `accounts` por lo mismo que `recurring_rules.account_id`:
     * si la cuenta se borra, el crédito no se borra con ella — se suelta el débito (ver
     * `AccountRoutes`) y la propuesta deja de salir.
     */
    val debitoAutomaticoDesde = varchar("debito_automatico_desde", 50).nullable()
    /**
     * El tercero al que el dueño le gira la cuota; ver `CreditTerms.destinoConocidoId`. Nullable por
     * lo mismo que las de arriba (`ADD COLUMN … NULL` dentro de la transacción de arranque no puede
     * fallar), y sin FK a `known_destinations` por lo mismo que `recurring_rules.destino_conocido_id`:
     * si el tercero se borra, el crédito suelta la referencia (ver `DestinoRoutes`).
     */
    val destinoConocidoId = varchar("destino_conocido_id", 50).nullable()

    /**
     * El interés de una cuota según el extracto, y de qué cuota; ver `CreditTerms.interesReal`.
     * Nullable por lo mismo que las de arriba: `ADD COLUMN … NULL` dentro de la transacción de
     * arranque no puede fallar sobre los créditos que ya están (`SchemaDeArranqueTest`).
     */
    val interesReal = long("interes_real").nullable()
    val interesRealCuota = varchar("interes_real_cuota", 7).nullable()

    /**
     * Vuelta 77 · desde cuándo corren las cuotas (ISO, el arranque de un período) cuando el alta dijo
     * «ya pagué la cuota de este período»; ver `CreditTerms.cuotasDesde`. Nullable por lo mismo que las
     * de arriba: `ADD COLUMN … NULL` no puede fallar sobre los créditos que ya están.
     */
    val cuotasDesde = varchar("cuotas_desde", 10).nullable()
    val accountId          = varchar("account_id", 50)   // 1:1 con cuenta LOAN
    val userId             = varchar("user_id", 50)
    val bank               = varchar("bank", 80)
    val principal          = long("principal")            // capital original (COP)
    val rateEa             = double("rate_ea")            // % EA
    val termMonths         = integer("term_months")
    val installment        = long("installment")          // cuota mensual total
    val dayOfMonth         = integer("day_of_month")
    val startDate          = varchar("start_date", 10)    // ISO desembolso
    /**
     * **500 y no 300, porque acá vive el *porqué* de un número que no cuadra con el banco.**
     *
     * Con 300 se cortó tres veces en un solo día cargando datos reales: no cupo explicar que la
     * cuota del Vehículo 8761 trae $25.000 de «otros conceptos» que Movi termina contando como
     * capital, ni el detalle del Crediexpress que está detrás del Techo Gardenera. Una nota
     * mutilada es peor que ninguna: deja media explicación y ninguna pista de que falta la otra.
     *
     * 500 es el techo que este repo ya usa para prosa que escribe una persona sobre un registro
     * —`documents.notes` y `void_events.reason`, que son el mismo tipo de campo—. Un `text()` sin
     * tope (como `sms_messages.text`) se descartó a propósito: eso es para lo que llega de afuera,
     * y un campo de notas sin límite invita a pegar un extracto entero adentro de una fila que la
     * app pinta en una tarjeta.
     *
     * **Ensanchar SÍ llega a producción.** `credit_terms` ya existe allá, así que el
     * `SchemaUtils.create` del arranque no hace nada; pero `Credits` está en la lista de
     * `createMissingTablesAndColumns`, y Exposed 0.55 emite el `ALTER TABLE … ALTER COLUMN notes
     * TYPE VARCHAR(500)` también cuando lo único que cambió es el largo — no solo cuando falta la
     * columna. Verificado contra Postgres 16 de verdad y fijado por `SchemaDeArranqueTest`.
     */
    val notes              = varchar("notes", 500).nullable()
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
    /**
     * El pago mínimo del extracto, en la moneda de la cuenta. Ver `CardTerms.pagoMinimo` — es la
     * primera cifra de una tarjeta que Movi no puede derivar de nada que ya tenga, y por eso se
     * teclea en vez de estimarse.
     *
     * Nullable por lo mismo que `credit_limit` y que `credit_terms.otros_cargos_mensuales`:
     * `createMissingTablesAndColumns` corre DENTRO de la transacción de arranque, y
     * `ALTER TABLE … ADD COLUMN … NULL` es el único DDL que no puede fallar sobre las filas que ya
     * están. **Y `Cards` tiene que seguir en esa lista** (ya está), o esta columna se queda en el
     * código y cada consulta de tarjetas revienta en producción con «column does not exist»:
     * fijado por `SchemaDeArranqueTest` y por `PagoMinimoColumnTest`.
     *
     * Y nullable también en el sentido que importa: `null` no es 0. Un mínimo en 0 diría que esa
     * tarjeta no le exige nada este mes; `null` dice que no se sabe, que es la verdad para las
     * cinco tarjetas que ya están en producción.
     */
    val pagoMinimo         = long("pago_minimo").nullable()
    /**
     * **500 y no 300**, igual que `credit_terms.notes` — y por la misma razón, descubierta del
     * mismo modo: escribiendo una nota real.
     *
     * La de una tarjeta tiene que caber el corte, el pago, el cupo, la tasa, de qué extracto
     * salieron y qué le falta todavía. Con 300 caracteres eso se corta a la mitad, y lo que se
     * corta es justo la parte que explica de cuándo es el dato — o sea, lo único que evita leer
     * el mínimo de un corte viejo como si fuera el de este mes. Se descubrió cargando los mínimos
     * de la Master Black (2026-09-12): el `UPDATE` reventó con «value too long».
     *
     * 500 y no ilimitado por lo mismo que allá: un campo sin tope invita a pegar un extracto
     * entero adentro de una fila que la app pinta en una tarjeta.
     *
     * **Ensanchar SÍ llega a producción.** `card_terms` ya existe allá, así que el
     * `SchemaUtils.create` del arranque no hace nada; pero `Cards` está en la lista de
     * `createMissingTablesAndColumns`, y Exposed 0.55 emite el `ALTER TABLE … ALTER COLUMN notes
     * TYPE VARCHAR(500)` también cuando lo único que cambió es el largo. Es el mismo camino que ya
     * recorrió `credit_terms.notes`, verificado contra Postgres 16.
     */
    val notes              = varchar("notes", 500).nullable()
    val lastRemindedPeriod = varchar("last_reminded_period", 7).nullable() // "YYYY-MM", server-only
    /** Ver `RecurringRules.remindMe`. */
    val remindMe           = bool("remind_me").default(true)
    /**
     * Ola 4: la tasa E.A. de la tarjeta (`29.64`), tecleada. Ver `CardTerms.tasaEa`. Nullable por lo
     * mismo que `pago_minimo`: `createMissingTablesAndColumns` la agrega sobre las filas que ya están
     * (y `Cards` está en esa lista), y `null` dice «no se cargó», que es la verdad de las cinco
     * tarjetas del dueño. Sin tasa, el plan de salida no la mete al cálculo: lo dice.
     */
    val tasaEa             = double("tasa_ea").nullable()
    override val primaryKey = PrimaryKey(accountId)
    init { index("idx_card_terms_user_id", false, userId) }
}

object Subscriptions : Table("subscriptions") {
    val id          = varchar("id", 50)
    val userId      = varchar("user_id", 50)
    val merchantKey = varchar("merchant_key", 80)
    val displayName = varchar("display_name", 100)
    val amount      = long("amount")                 // el cobro REAL, en moneda nativa (ver periodicidad)
    val currency    = varchar("currency", 10)
    val dayOfMonth  = integer("day_of_month")
    val status      = varchar("status", 20)          // AUTO | CANDIDATE | CONFIRMED | DISMISSED
    val confidence  = varchar("confidence", 10)      // HIGH | MEDIUM | LOW
    val firstSeen   = long("first_seen")
    val lastSeen    = long("last_seen")
    val occurrences = integer("occurrences")
    val accountId   = varchar("account_id", 50).nullable()
    /**
     * Ola 16 — MENSUAL | ANUAL (ver [PeriodicidadDeCobro]). Decide qué significa [amount]: el
     * cobro de cada mes, o el de una vez al año que hay que prorratear para el total.
     *
     * `.default("MENSUAL")` no es cosmético, es la migración: este proyecto no tiene archivos de
     * migración —`DatabaseFactory.init()` corre `createMissingTablesAndColumns` en cada arranque—
     * y el default es lo que hace que la sentencia emitida sea `ADD COLUMN periodicidad
     * VARCHAR(10) DEFAULT 'MENSUAL' NOT NULL`, o sea que las filas que ya están en producción
     * queden diciendo lo único que pueden decir con verdad: son mensuales. Mismo mecanismo que
     * `remind_me` en [RecurringRules].
     */
    val periodicidad = varchar("periodicidad", 10).default("MENSUAL")
    /**
     * Ola 19 — **¿el [amount] de esta fila lo escribió el dueño, o lo dedujo el barrido?**
     *
     * El barrido reescribe el monto de toda fila CONFIRMED/AUTO que vuelva a detectar (ver
     * `applyExisting`), y eso es lo correcto mientras nadie lo haya tocado: si el servicio sube
     * de precio, el monto se actualiza solo. Pero desde que la app deja editar una suscripción,
     * ese mismo update le pisaba al dueño la corrección que acababa de hacer — en el próximo
     * «Buscar cobros», o en la próxima importación de extracto, que también dispara el barrido.
     *
     * Con esta marca el barrido puede respetar lo corregido **sin** dejar de actualizar todo lo
     * demás. La alternativa —no volver a tocar ningún monto— habría congelado también las filas
     * que nadie editó nunca, o sea cambiar un monto que se pisa por uno que envejece callado.
     *
     * La escribe SOLO el server, comparando el monto que llega contra el guardado (ver el `PUT`
     * de `SubscriptionRoutes`). Lo que mande el cliente en este campo se ignora: es un hecho
     * sobre lo que pasó, no una preferencia que se pueda pedir.
     *
     * `.default(false)` es la migración, igual que en [periodicidad]: hace que la sentencia sea
     * `ADD COLUMN ... DEFAULT FALSE NOT NULL`, y las filas que ya están en producción quedan
     * diciendo lo único cierto de ellas — a ninguna le corrigieron el monto todavía.
     */
    val montoCorregidoAMano = bool("monto_corregido_a_mano").default(false)
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

/**
 * **Apartados** — plata que sigue en una cuenta propia pero ya tiene dueño («estos $8M son del
 * colegio»). `restante` NO vive acá: se deriva en cada lectura de `monto` menos lo gastado en
 * `categoria` desde `desde` (ver `apartadosDelUsuario`), por lo mismo que `goals` no guarda su
 * «ahorrado» — un número guardado aparte se desincroniza del que está bien.
 */
object Apartados : Table("apartados") {
    val id           = varchar("id", 50)
    val userId       = varchar("user_id", 50)
    val accountId    = varchar("account_id", 50)
    val proposito    = varchar("proposito", 60)
    val monto        = long("monto")
    val categoria    = varchar("categoria", 100).nullable()
    val desde        = long("desde")
    val createdAt    = long("created_at")
    val lastEditedAt = long("last_edited_at").nullable()
    /**
     * Vuelta 64 — el gasto de un mes puntual para el que se aparta (el SOAT, el predial): cuánto
     * cuesta y en qué mes llega (`"AAAA-MM"`). Las dos NULLABLE —un apartado de siempre no tiene— y
     * la tabla ya existe en producción, así que entra a `createMissingTablesAndColumns` (ver
     * `SchemaDeArranqueTest`). Solo alimentan la sugerencia del aporte mensual: ninguna cifra de
     * plata las lee.
     */
    val objetivo     = long("objetivo").nullable()
    val mesDelGasto  = varchar("mes_del_gasto", 7).nullable()
    override val primaryKey = PrimaryKey(id)
    init { index("idx_apartados_user_id", false, userId) }
}

/**
 * **Las cuentas de otros** — el registro de números de cuenta ajenos, con nombre y de quién son.
 * Ver `DestinoConocido` en `:core` para el porqué de que esto NO sea una `Account`.
 *
 * No guarda totales: `totales` y `cuantos` se derivan en cada lectura cruzando los movimientos del
 * dueño (ver `DestinoRoutes.kt`), por el mismo motivo que `goals` no guarda su «ahorrado» — un
 * número guardado aparte se desincroniza del que está bien.
 *
 * `numero` son **solo dígitos** (lo normaliza `soloLosDigitos` antes de escribir): el dueño va a
 * pegar el número como se lo mandó el banco y dos formas del mismo número no pueden verse como dos
 * destinos.
 */
object KnownDestinations : Table("known_destinations") {
    val id        = varchar("id", 50)
    val userId    = varchar("user_id", 50)
    val nombre    = varchar("nombre", 60)
    val numero    = varchar("numero", 30)
    val deQuien   = varchar("de_quien", 40).nullable()
    val createdAt = long("created_at")
    /**
     * **La llave** (29-sep) — `@usuario`, celular, correo o código, normalizada con
     * `normalizarLlave`. NULLABLE a propósito: la tabla ya existe en producción con los destinos
     * del dueño adentro, y `createMissingTablesAndColumns` emite un `ADD COLUMN llave VARCHAR(80)
     * NULL` que no puede fallar sobre esas filas — todas quedan en NULL, que es la verdad: ninguna
     * tenía llave. Un destino que solo se conoce por su llave guarda `numero = ''` (la columna
     * `numero` sigue como estaba). Ver `KnownDestinationsLlaveColumnTest`.
     */
    val llave     = varchar("llave", 80).nullable()
    /**
     * **Todos sus identificadores** (4-oct-2026) — números, llaves y el nombre con que lo nombra el
     * banco —, como JSON de `List<IdentificadorDelDestino>`. Una columna y no una tabla aparte a
     * propósito: lo que menos rompe. Cada consulta que lee destinos ya lee esta fila, y una tabla
     * nueva obligaría a cada prueba que crea `known_destinations` a crear también la otra.
     *
     * NULLABLE: las filas que ya existen la reciben en NULL con el `ADD COLUMN`, y la migración
     * `pasarLosIdentificadoresALaLista` la llena desde [numero] y [llave] al arrancar. Mientras sea
     * NULL, leerla igual devuelve el número y la llave de siempre (ver `aDestino`).
     */
    val identificadores = text("identificadores").nullable()
    /** `PERSONA` o `COMERCIO` (4-oct-2026). NULLABLE: los destinos viejos no lo dijeron. */
    val tipo      = varchar("tipo", 16).nullable()
    /**
     * **Los nombres que tuvo antes** (20-oct-2026), como JSON de `List<String>`: los llena el
     * server al renombrarlo o al unirle otra ficha. Sirven para reconocer «Transferencia a
     * NombreViejo» como un nombre de Movi (ver `NombreDelTercero.kt`). NULLABLE, mismo motivo que
     * `identificadores`: la tabla existe en producción y el `ADD COLUMN` no puede fallar.
     */
    val nombresAnteriores = text("nombres_anteriores").nullable()
    override val primaryKey = PrimaryKey(id)
    init { index("idx_known_destinations_user_id", false, userId) }
}

/**
 * **Los sugeridos que el dueño descartó** (4-oct-2026): «Ignorar» o «Es mía» sobre una cuenta que
 * Movi encontró sola en sus avisos. Sin esto el mismo sugerido volvería en cada lectura. La clave
 * es la de `IdentificadorDelDestino.clave` (`NUMERO:41279033068`, `LLAVE:@juan`).
 */
object DestinosDescartados : Table("destinos_descartados") {
    val userId    = varchar("user_id", 50)
    val clave     = varchar("clave", 120)
    val motivo    = varchar("motivo", 16)
    val creadoEn  = long("creado_en")
    override val primaryKey = PrimaryKey(userId, clave)
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

/**
 * **Espacios** (R3, 26-oct-2026) — un grupo de gasto compartido, «Casa con Caro», 50/50. Ver
 * `Espacio.kt` en `:core`. `miembros` es JSON de `List<MiembroDelEspacio>` sin nombres (el nombre
 * se resuelve en cada lectura desde `known_destinations`, así un tercero renombrado se ve igual en
 * todas partes). Tablas nuevas: entran solo al `SchemaUtils.create` del arranque.
 */
object Espacios : Table("espacios") {
    val id        = varchar("id", 60)
    val userId    = varchar("user_id", 50)
    val nombre    = varchar("nombre", 60)
    val miembros  = text("miembros")
    val reparto   = varchar("reparto", 20)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init { index("idx_espacios_user_id", false, userId) }
}

/**
 * **Los gastos de un espacio.** Una tabla aparte y no una columna en `financial_events`, a propósito:
 * la tabla de movimientos viaja al teléfono (espejo SQLDelight, sync, «fantasmas», reenvíos con
 * `lastEditedAt`) y una columna nueva ahí habría que llevarla por todo ese camino. Acá el movimiento
 * no se toca: el reparto es un dato del espacio que apunta a él (`evento_id`), y su parte ajena se
 * deriva al leer, como `countsAsCashFlow`. Borrar el espacio o «Quitar del espacio» borra la fila y
 * el movimiento vuelve a contar entero, sin haber cambiado nunca.
 *
 * - `evento_id` no nulo: un movimiento propio repartido (`pagado_por` = «yo»). Único por usuario:
 *   un movimiento está en un espacio o en ninguno.
 * - `evento_id` nulo: un gasto que pagó otro miembro; no toca ningún saldo.
 *
 * `partes` es JSON de `Map<miembro, pesos>` (suma `monto`).
 */
object GastosDelEspacio : Table("gastos_del_espacio") {
    val id          = varchar("id", 60)
    val userId      = varchar("user_id", 50)
    val espacioId   = varchar("espacio_id", 60)
    val eventoId    = varchar("evento_id", 60).nullable()
    val pagadoPor   = varchar("pagado_por", 60)
    val monto       = long("monto")
    val categoria   = varchar("categoria", 100)
    val descripcion = varchar("descripcion", 200)
    val fecha       = long("fecha")
    val partes      = text("partes")
    val createdAt   = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init { index("idx_gastos_del_espacio_user_id", false, userId) }
}

/**
 * **«Marcar como saldado»**: quién le pagó a quién, cuánto y cuándo. `evento_id` es la
 * transferencia real si el dueño la vinculó; ese movimiento deja de contar como ingreso o gasto. Un
 * POST con el mismo id es la misma liquidación (saldar dos veces = una).
 */
object LiquidacionesDelEspacio : Table("liquidaciones_del_espacio") {
    val id        = varchar("id", 60)
    val userId    = varchar("user_id", 50)
    val espacioId = varchar("espacio_id", 60)
    val de        = varchar("de", 60)
    val a         = varchar("a", 60)
    val monto     = long("monto")
    val fecha     = long("fecha")
    val eventoId  = varchar("evento_id", 60).nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
    init { index("idx_liquidaciones_del_espacio_user_id", false, userId) }
}
