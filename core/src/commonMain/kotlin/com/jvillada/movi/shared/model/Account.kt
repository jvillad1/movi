package com.jvillada.movi.shared.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, LOAN, INVESTMENT }

// `@EncodeDefault` sobre `lastEditedAt`: ver el porqué en su KDoc.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val balance: Long,      // COP component (derived on read)
    val currency: String = "COP",
    val balancesByCurrency: Map<String, Long> = emptyMap(),  // derived: per-currency balance
    val estimatedTotalCop: Long? = null,                     // derived: COP + foreign × TRM

    /**
     * **Para qué —y solo para qué— se puede usar esta plata sin castigo.** `null` = libre.
     *
     * Sale de la pensión voluntaria del dueño en Skandia. Son $106.000.000 suyos, cuentan en su
     * patrimonio, y aun así no son plata disponible: solo puede retirarlos **para vivienda** sin
     * perder el beneficio tributario. Cualquier otro retiro le pega la retención en la fuente que
     * se ahorró al aportar.
     *
     * Él lo dijo así: *«esa plata no la tengo disponible; la de Skandia es dinero que deberías
     * referenciar en patrimonio para el cálculo pero no mostrarle como disponible en mi balance,
     * sino como un dinero disponible condicionado a uso en Vivienda»*.
     *
     * Con el campo vacío, «Tu plata» sumaba los $106M y decía $137.625.167 — un número que él no
     * puede gastar. Ahora esa cuenta sale de «Tu plata» y entra en su propio renglón; el
     * patrimonio neto **no cambia**, porque para eso sí es suya.
     *
     * Es texto libre y no un enum: en Colombia el mismo caso son las cesantías, una AFC, un fondo
     * de pensiones voluntarias. La condición cambia con el producto y quien la escribe es el
     * dueño, que es el que la conoce.
     */
    val condicionadaA: String? = null,

    /**
     * **La edad de esta versión de la cuenta**, en milisegundos epoch. `null` = nadie la editó.
     *
     * Es el gemelo de [FinancialEvent.lastEditedAt], y existe por el mismo agujero, esta vez sobre
     * el nombre: `POST /api/accounts` es un upsert por id desde que se volvió idempotente, y
     * [com.jvillada.movi.shared.SyncEngine.syncAccounts] reenvía cada 30 segundos toda cuenta que
     * el teléfono no logró sellar. Si ese POST había LLEGADO y solo se perdió la respuesta —se
     * cortó la señal a mitad, se murió el proceso—, la fila local se queda pendiente aunque el
     * server ya tenga la cuenta. Y si en esa ventana el dueño la renombra **en la web** (el caso
     * real: «Libranza 4817» donde iba «4818», un dígito que identifica la obligación contra el
     * extracto), el reenvío del teléfono pisaba ese nombre sin decir nada.
     *
     * Con este campo el reenvío **pierde contra una edición más nueva**: el server compara lo que
     * llega contra lo guardado y solo pisa si la versión que entra no es más vieja (`pisaElReenvio`,
     * en `EventRoutes.kt` — es la MISMA función para los dos, no una segunda regla parecida). Los
     * empates van para el que llega, así que el reenvío idéntico —el caso normal— sigue siendo
     * inofensivo.
     *
     * ### Quién lo escribe
     *
     * **Los dos lados, cada uno con su reloj, y solo al EDITAR.** El server lo sella en cada ruta
     * que cambia la cuenta (`PUT /{id}/name` y `PUT /{id}/conditioned-to`, que hoy son todas); el
     * teléfono lo sella cuando resuelve una edición sin señal sobre una cuenta que todavía no
     * subió (ver `LocalRepository.renameAccount`). Crearla no lo escribe: una cuenta recién creada
     * no tiene ninguna versión anterior a la que ganarle.
     *
     * Los relojes no se sincronizan, y está acotado a propósito: esto no entra en ningún total ni
     * decide ningún saldo. Solo desempata entre dos versiones del mismo id.
     *
     * ### Por qué viaja siempre, incluso en `null`
     *
     * Por lo mismo que [FinancialEvent.lastEditedAt]: con `encodeDefaults = false` un `null` no se
     * serializaría y el cuerpo saldría **sin la clave** — indistinguible del que manda un APK
     * viejo, que no conoce el campo (el del dueño es el 1.31). Y los dos significan cosas opuestas:
     * «sé de ediciones y esta copia no se editó» (pierde contra lo guardado) contra «no sé nada de
     * esto» (se atiende como antes, pisando). Lo fija `CuentaEditadaEnElWireTest`.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val lastEditedAt: Long? = null,

    /**
     * **Cuándo se cuadró esta cuenta contra el banco por última vez**, en epoch ms. `null` = nunca.
     *
     * Es el sello del último movimiento con categoría [ADJUSTMENT_CATEGORY] que sigue vivo (los
     * anulados no cuentan: el ajuste que se anuló no cuadró nada). Lo **deriva el server** en
     * `enrichWith`, igual que [balancesByCurrency] y [estimatedTotalCop] — no hay ninguna columna
     * nueva, y no puede separarse de los movimientos porque sale de ellos.
     *
     * Existe por lo que el dueño midió el 21-sep: Movi deriva cada saldo de los movimientos, así
     * que **todo lo que mueve plata sin emitir un movimiento se desvía para siempre**. Sus
     * rendimientos de Nu habían crecido $745.856 y los de la Fiducuenta $1.637 sin un solo SMS
     * que capturar. El ajuste ya existía; lo que no existía era saber **cuáles cuentas hace rato
     * que nadie mira** (ver `cuentasSinCuadrar` en `:shared`).
     */
    val lastAdjustmentAt: Long? = null,

    /**
     * **El movimiento más viejo de esta cuenta**, en epoch ms. `null` = no tiene ninguno.
     *
     * Derivado igual que [lastAdjustmentAt], y está por una sola razón: una cuenta que **nunca**
     * se cuadró no puede medirse contra la nada. Es la edad de la cuenta dentro de Movi —su
     * apertura es su primer movimiento— y hace que el aviso de «hace rato que no la cuadras»
     * cuente desde que la cuenta existe, en vez de reclamarle el primer día a una cuenta que se
     * acaba de crear.
     */
    val firstEventAt: Long? = null,

    /**
     * **Esta cuenta es un bien** —la casa, el carro— y esto es lo que vale. `null` = una cuenta de
     * plata o de deuda, como todas las que existían antes de este campo.
     *
     * Ver [Bien] para qué es y por qué no es un valor nuevo de [AccountType]. Acá va el contrato
     * del cable, que es lo que tiene que sostenerse con el APK viejo del dueño instalado.
     *
     * ### Lo que el server manda por un bien
     *
     * `type = INVESTMENT`, `balance = 0`, `balancesByCurrency` vacío, sin `estimatedTotalCop`, y el
     * valor **solo** adentro de este objeto. Lo fuerza el server (ver `AccountRoutes.kt` al crear
     * y `enrichWith` al leer), no el cliente: la garantía de abajo no puede depender de que quien
     * crea el bien se porte bien.
     *
     * ### Lo que ve un APK viejo (el que el dueño ya tiene instalado), con la casa cargada
     *
     * Ignora este campo (`ignoreUnknownKeys`) y ve **una inversión llamada «Casa Almendros» con
     * $0**. Concretamente:
     *
     * - **«Tu plata» no cambia.** Suma el `balance` de sus cuentas libres, y el de la casa es 0. Es
     *   la degradación que importa: la alternativa —mandar el valor en `balance`— le habría dicho
     *   «Tu plata $1.412 millones», que es exactamente el error más caro que puede cometer esta app.
     * - **El patrimonio neto tampoco cambia**: sigue diciendo lo que decía (−$2.074M), sin la casa.
     *   Es la media foto de antes, no una foto falsa. La actualización la corrige.
     * - La casa aparece listada en Inversión con $0, y en el desglose de «Tu plata» del Inicio como
     *   un renglón en $0. Raro, pero cierto en lo que dice (no suma nada) y sin plata inventada.
     * - Si alguien intentara cuadrarla desde ese APK, el server contesta 422: un bien no se ajusta
     *   con un movimiento (ver `POST /{id}/balance-adjustment`). Si le anotara un movimiento, el
     *   server lo guarda pero lo deja fuera de todo saldo del bien (el `balance` sigue en 0).
     *
     * ### Por qué nunca se manda en `null` a propósito
     *
     * Con `encodeDefaults = false` un `null` no viaja, y el `POST` de reenvío del teléfono
     * ([com.jvillada.movi.shared.SyncEngine.syncAccounts]) sale sin la clave. El server solo toca
     * las columnas del bien **si la clave vino y no es `null`**: un APK viejo que reenvía una
     * cuenta suya no puede, sin querer, deshacer un bien.
     */
    val bien: Bien? = null,
)

/**
 * `POST /api/accounts/{id}/balance-adjustment` — **cuadrar una cuenta contra el banco**.
 *
 * Viaja el saldo **objetivo** (lo que dice el banco), no la diferencia: el saldo que ve el cliente
 * puede llegar viejo, y una diferencia calculada sobre una foto vieja se anota mal. El server resta
 * contra los movimientos vigentes y registra un movimiento real — el saldo se sigue derivando de
 * los movimientos (ver `computeBalances`), nunca se sobrescribe.
 *
 * Es la MISMA mecánica que `POST /api/credits/{id}/balance-adjustment` y comparte su constructor
 * de eventos (`balanceAdjustmentEventFor`): una sola forma de ajustar un saldo en toda la app, con
 * una sola categoría reservada ([ADJUSTMENT_CATEGORY]). Lo que cambia es de qué cuentas habla cada
 * ruta y qué contesta: la de créditos devuelve el `CreditSummary` con su plan de pagos; esta
 * devuelve la cuenta.
 *
 * En la **moneda de la cuenta**, igual que el saldo que muestra la pantalla.
 */
@Serializable
data class AdjustAccountBalanceRequest(val targetBalance: Long)

/**
 * Lo que contesta el ajuste: la cuenta ya enriquecida (su saldo NUEVO, derivado) y el movimiento
 * que se escribió — `null` cuando el saldo del banco ya coincidía y no había nada que anotar.
 *
 * El evento viaja para que el espejo local del teléfono pueda escribir **exactamente** el que
 * guardó el server (mismo id, mismo sello) en vez de reconstruirlo: es el mismo contrato que
 * `CreditSummary.adjustmentEvent`, y el motivo está en `LocalRepository.adjustCreditBalance`.
 */
@Serializable
data class AdjustAccountBalanceResponse(
    val account: Account,
    val adjustmentEvent: FinancialEvent? = null,
)

/**
 * Techo defensivo para el saldo objetivo de una cuenta. **Es el mismo número que
 * [MAX_CREDIT_DEBT_COP], y es el mismo por construcción**: se define como un alias en vez de
 * copiar la cifra para que no puedan separarse. No es un límite de negocio — atrapa el dedazo de
 * teclear dígitos de más al copiar el saldo de la banca en línea.
 */
const val MAX_ACCOUNT_BALANCE_COP = MAX_CREDIT_DEBT_COP

/**
 * F56 — [AccountType] se queda igual (compat de DB y wire: filas viejas, eventos guardados,
 * el `POST /api/accounts` del server), pero la UI ya no distingue entre CASH/CHECKING/SAVINGS
 * (verificado: se tratan idéntico en todos los cálculos de balance) ni promete que una cuenta
 * es un lugar distinto de una deuda cuando en realidad es lo mismo con otro nombre. Este agrupador
 * es la superficie que la UI muestra: **Dinero** (plata disponible), **Inversión** (plata
 * guardada) y **Deuda** (tarjetas y préstamos — ya no se crean como cuenta, viven en Créditos,
 * pero el grupo existe para lo que ya haya en la base).
 */
enum class AccountGroup { DINERO, INVERSION, DEUDA }

val AccountType.group: AccountGroup
    get() = when (this) {
        AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS -> AccountGroup.DINERO
        AccountType.INVESTMENT -> AccountGroup.INVERSION
        AccountType.CREDIT_CARD, AccountType.LOAN -> AccountGroup.DEUDA
    }

val AccountType.groupLabel: String
    get() = when (group) {
        AccountGroup.DINERO -> "Dinero"
        AccountGroup.INVERSION -> "Inversión"
        AccountGroup.DEUDA -> "Deuda"
    }

/**
 * `PUT /api/accounts/{id}/name`.
 *
 * Renombrar una cuenta es seguro: los movimientos apuntan por `accountId` y el saldo se deriva de
 * ellos, así que nada se despega. Es distinto de renombrar una **categoría**, donde el cruce con
 * el gasto es por nombre y sí corta la relación con lo viejo.
 */
@Serializable
data class RenameAccountRequest(val name: String)

/** Largo de la columna `accounts.name`. */
const val MAX_ACCOUNT_NAME_LENGTH = 100

/**
 * `PUT /api/accounts/{id}/conditioned-to` — marcar (o desmarcar) para qué sirve esta plata.
 *
 * **Existe porque el campo nacía muerto.** [Account.condicionadaA] solo se podía escribir en el
 * `POST` de creación, y la cuenta de Skandia del dueño ya existía en producción: sin esta ruta,
 * la única forma de marcarla era tocar la base de datos a mano. Un ajuste que solo un
 * desarrollador puede cambiar no es un ajuste del usuario.
 *
 * `null` y `""` significan lo mismo acá —quitar la condición— a diferencia del `accountId` de
 * `RecurringRule`, donde `null` significa «no lo toques»: allá el problema era un cliente viejo
 * que no conocía el campo y lo borraba sin querer al editar otra cosa. Esta ruta hace **una sola
 * cosa**, así que quien la llama siempre está hablando de la condición.
 */
@Serializable
data class UpdateAccountConditionRequest(val condicionadaA: String? = null)

/** Largo de la columna `accounts.conditioned_to`. Ver [Account.condicionadaA]. */
const val MAX_ACCOUNT_CONDITION_LENGTH = 60

/**
 * La condición tal como se guarda: recortada, sin exceder la columna, y `null` cuando queda vacía.
 *
 * Es UNA función y no tres copias del mismo `trim().take().takeIf()` porque hay tres lugares que
 * tienen que coincidir —el `POST` de creación, el `PUT` de esta ruta y la UI que la escribe— y ya
 * hay dos defectos en la historia de este repo que nacieron de que dos capas normalizaran el
 * mismo texto distinto.
 */
fun normalizarCondicion(raw: String?): String? =
    raw?.trim()?.take(MAX_ACCOUNT_CONDITION_LENGTH)?.takeIf { it.isNotEmpty() }
