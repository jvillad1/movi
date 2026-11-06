package com.jvillada.movi.server.sms

import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.SmsMessages
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.group
import com.jvillada.movi.shared.model.palabrasClave
import com.jvillada.movi.server.credits.toCreditTerms
import com.jvillada.movi.server.reminders.ultimasCuotasPagadasDe
import com.jvillada.movi.shared.model.cuotaCambia
import kotlin.math.roundToLong
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll

/** Un crédito del dueño, con lo que hace falta para reconocer a quién se le paga. */
data class DeudaDelDueno(
    val cuenta: Account,
    /** El banco que escribió el dueño en las condiciones del crédito («Banco de Occidente»). */
    val banco: String? = null,
    /**
     * La cuota de este mes, para desempatar dos créditos del mismo banco. Vuelta 134 (M-8): la de
     * [com.jvillada.movi.shared.model.cuotaDeEsteMes] —en un crédito que cambia, la última pagada de verdad—,
     * no la pactada: con abono constante la pactada se aleja cada mes y el aviso dejaba de reconocerse.
     */
    val cuota: Long? = null,
    /** Vuelta 134 (M-8): [cuota] es una estimación (el crédito cambia): se reconoce de cerca, no exacta. */
    val cuotaEstimada: Boolean = false,
    /** La paga otro (`paidBy`) o es una libranza: no sale por PSE de una cuenta suya. */
    val noLaPagaEl: Boolean = false,
)

/**
 * Vuelta 134 (M-8) · **el crédito cuya cuota es [monto]**, entre [candidatas] del mismo banco, en dos pasos:
 *
 * 1. **Exacto, como siempre**: si alguna cuota es [monto] al peso, decide eso (una o ninguna si son dos). Un
 *    crédito de cuota fija se reconoce exactamente como antes de la vuelta 133, aunque al lado haya uno que
 *    cambia con una cuota parecida (revisión 134, I-1: Vehículo 4083 en $980.000 fijo y Libre inversión 9695
 *    en unos $1.080.500; un aviso de $980.000 sigue siendo el Vehículo).
 * 2. **De cerca, solo si nadie calzó exacto y solo entre los que cambian**: a
 *    ±[com.jvillada.movi.shared.model.MARGEN_DE_LA_CUOTA_QUE_SE_PARECE] % de la de este mes, la cercanía de
 *    «¿Es la cuota de X?» (`montoSeAcercaALaCuota`): la estimación es la del mes pasado y la de este casi
 *    nunca da igual al peso.
 */
internal fun cuotaQueReconoce(candidatas: List<DeudaDelDueno>, monto: Long): DeudaDelDueno? {
    val exactas = candidatas.filter { it.cuota == monto }
    if (exactas.isNotEmpty()) return exactas.singleOrNull()
    return candidatas
        .filter { d -> d.cuotaEstimada && d.cuota?.let { com.jvillada.movi.shared.model.montoSeAcercaALaCuota(monto, it) } == true }
        .singleOrNull()
}

/** Una regla recurrente del dueño, reducida a lo que sirve para saber de qué cuenta sale. */
data class ReglaDelDueno(val nombre: String, val cuentaId: String?, val tipo: TransactionType)

/**
 * Las palabras de un nombre que **no dicen quién es**: «Banco», «Compañía de Financiamiento»,
 * «S.A.». Sin sacarlas, «Banco de Occidente» y «Banco de Bogotá» compartirían «banco» y «de».
 */
private val PALABRAS_QUE_NO_NOMBRAN = setOf(
    "banco", "bancos", "comercial", "compania", "de", "del", "la", "las", "el", "los", "y", "s", "a", "sa",
    "sas", "ath", "ltda", "esp", "colombia", "financiamiento", "financiera", "corporacion", "sociedad",
    "credito", "creditos", "pago", "pagos", "tarjeta", "cuenta", "prestamo",
)

/** Las palabras que nombran a quién: «Banco de Occidente S A ATH» → [occidente]. */
internal fun marcaDe(nombre: String): List<String> =
    palabrasClave(nombre).filter { it.isNotEmpty() && it !in PALABRAS_QUE_NO_NOMBRAN }

/** ¿[texto] nombra a [marca], con todas sus palabras? Una marca vacía no nombra nada. */
private fun nombra(texto: String, marca: List<String>): Boolean {
    if (marca.isEmpty()) return false
    val palabras = palabrasClave(texto).toSet()
    return marca.all { it in palabras }
}

/** De dónde puede salir un pago por PSE: una cuenta de plata que no sea efectivo. */
private fun puedePagarPorPse(cuenta: Account): Boolean =
    cuenta.type.group == AccountGroup.DINERO && cuenta.type != AccountType.CASH

/**
 * # Lo que Movi sabe de un pago que el aviso no cuenta entero
 *
 * El correo de PSE dice a quién se le pagó y cuánto, pero **no desde qué cuenta**, ni que «PAGO
 * Banco de Occidente - Prestamo» es la cuota del «Vehículo 8761». Esto lo completa con lo que el
 * dueño ya tiene en Movi, sin adivinar: cada dato se propone solo si **una sola** cosa encaja, y
 * con la razón al lado para que la pantalla la diga.
 *
 * - **La deuda** ([ParsedSms.deudaSugeridaId]): para una cuota, el crédito cuyo nombre o banco nombra
 *   a la empresa (Banco de Occidente ↔ el crédito con banco «Banco de Occidente»); para un pago de
 *   tarjeta, la tarjeta de esa marca (NU ↔ «Nu Tarjeta»). Con dos que encajan desempata la cuota
 *   exacta; si sigue el empate, ninguna. Confirmar con ella arma el traspaso de dos patas
 *   (`vincular-deuda`), que es lo que marca la fila del período.
 * - **A dónde va un depósito** ([ParsedSms.traspasoHaciaId]): «Depósito a tu cuenta NU» va a la
 *   cuenta suya de esa marca que no es tarjeta.
 * - **De qué cuenta salió** ([ParsedSms.cuentaSugeridaId]), en orden: la de los pagos anteriores a
 *   esa misma deuda, la de la regla recurrente que nombra a la empresa, y la que usó la última vez
 *   para esa empresa. Si el pago llegó también por SMS, la app usa la cuenta que dice el SMS antes
 *   que esta (es un dato, esto es memoria).
 */
fun loQueMoviSabeDelPago(
    parsed: ParsedSms,
    cuentas: List<Account>,
    deudas: List<DeudaDelDueno>,
    reglas: List<ReglaDelDueno>,
    /** Los movimientos vivos del dueño, en cualquier orden. */
    eventos: List<FinancialEvent>,
    /**
     * Vuelta 107 · **cuántos pagos por PSE salieron de cada cuenta**: los movimientos vivos (distintos)
     * de los avisos de PSE ya confirmados, por cuenta. Ver [cuentaQueMasUsaPse].
     */
    pagosPorPse: Map<String, Int> = emptyMap(),
): ParsedSms {
    val marca = marcaDe(parsed.merchant)
    if (marca.isEmpty()) return parsed
    val marcaConLaNota = (marca + marcaDe(parsed.nota.orEmpty())).distinct()
    val porId = cuentas.associateBy { it.id }

    val deuda: Account? = when (parsed.category) {
        CUOTA_CATEGORY -> deudas
            .filter { !it.noLaPagaEl && it.cuenta.type == AccountType.LOAN && it.cuenta.currency == parsed.currency }
            .filter { nombra("${it.cuenta.name} ${it.banco.orEmpty()}", marca) }
            .let { candidatas -> candidatas.singleOrNull() ?: cuotaQueReconoce(candidatas, parsed.amount.roundToLong()) }
            ?.cuenta
        CARD_PAYMENT_CATEGORY -> cuentas
            .filter { it.type == AccountType.CREDIT_CARD && it.currency == parsed.currency && nombra(it.name, marca) }
            .singleOrNull()
        else -> null
    }

    val hacia: Account? = if (parsed.category == TRANSFER_CATEGORY) {
        cuentas.filter { puedePagarPorPse(it) && nombra(it.name, marca) }.singleOrNull()
    } else null

    val origenes = cuentas.filter { puedePagarPorPse(it) && it.id != hacia?.id }.map { it.id }.toSet()

    val (cuentaId, por) = deuda?.let { origenDeLosPagosA(it, eventos, origenes) }?.let { it to "La de tus pagos a ${deuda.name}" }
        // Vuelta 107: la regla se reconoce también por la nota del pago («Coomeva Familiar» ↔ «Coomeva Pago de
        // saldo plan familiar»): el nombre de la empresa solo («Coomeva Medicina Prepagada») no lo nombra.
        ?: reglas.filter { it.tipo == TransactionType.EXPENSE && it.cuentaId in origenes && reglaNombraALaEmpresa(it.nombre, marcaConLaNota) }
            .map { it.cuentaId to it.nombre }
            .distinctBy { it.first }
            .singleOrNull()
            ?.let { (id, nombre) -> id to "La de tu recurrente «$nombre»" }
        // Vuelta 107: la última vez, salvo en una cuenta casi sin pagos por PSE cuando se sabe de cuáles sale
        // (el pago a Coomeva anotado el 25-oct en la 0031 no puede volver a proponer la 0031).
        ?: eventos.asSequence()
            .filter { it.type == TransactionType.EXPENSE && it.accountId in origenes }
            .filter { pagosPorPse.isEmpty() || (pagosPorPse[it.accountId] ?: 0) >= MINIMO_DE_PAGOS_POR_PSE }
            .filter { nombra("${it.merchant.orEmpty()} ${it.description}", marca) }
            .maxByOrNull { it.timestamp }
            ?.let { it.accountId to "La que usaste la última vez para ${parsed.merchant}" }
        // Vuelta 107: sin nada de esa empresa, la cuenta de la que más sale PSE. Nunca la primera del
        // abecedario: el 25-oct el pago a Coomeva cayó así en la «Bancolombia Ahorros 0031», que casi no
        // se usa (ver [cuentaQueMasUsaPse]).
        ?: cuentaQueMasUsaPse(pagosPorPse, origenes)?.let { it to CUENTA_QUE_MAS_USA_PSE }
        ?: (null to null)

    return parsed.copy(
        cuentaSugeridaId = cuentaId?.takeIf { it in porId },
        cuentaSugeridaPor = por.takeIf { cuentaId != null },
        deudaSugeridaId = deuda?.id,
        traspasoHaciaId = hacia?.id,
    )
}

/** La razón de la cuenta que propone [cuentaQueMasUsaPse]. */
const val CUENTA_QUE_MAS_USA_PSE: String = "La que más usas para pagar por PSE"

/**
 * **Cuántos pagos por PSE hacen falta para proponer una cuenta por costumbre.** Elegido, no medido: con
 * uno solo, un pago anotado en la cuenta equivocada (el de Coomeva del 25-oct) se repetiría.
 */
const val MINIMO_DE_PAGOS_POR_PSE: Int = 2

/** Revisión de la vuelta 107 · qué parte de los pagos por PSE tiene que salir de esa cuenta. Elegido. */
const val PROPORCION_MINIMA_DE_PAGOS_POR_PSE: Double = 0.6

/**
 * Vuelta 107 · **la cuenta de la que más sale PSE**: la de más pagos por PSE en [pagosPorPse] entre
 * [origenes], solo si tiene al menos [MINIMO_DE_PAGOS_POR_PSE], le gana sola a la segunda y suma al menos el
 * [PROPORCION_MINIMA_DE_PAGOS_POR_PSE] de esos pagos. Con un empate
 * o con muy poca historia, ninguna: la app pide elegir («Elígela tú»), nunca pone una cuenta poco usada.
 */
fun cuentaQueMasUsaPse(pagosPorPse: Map<String, Int>, origenes: Set<String>): String? {
    val porCuenta = pagosPorPse.filterKeys { it in origenes }.entries.sortedByDescending { it.value }
    val primera = porCuenta.firstOrNull() ?: return null
    if (primera.value < MINIMO_DE_PAGOS_POR_PSE) return null
    if (porCuenta.getOrNull(1)?.value == primera.value) return null
    // Revisión: además, la mayoría clara de los pagos por PSE ([PROPORCION_MINIMA_DE_PAGOS_POR_PSE]).
    if (primera.value < PROPORCION_MINIMA_DE_PAGOS_POR_PSE * porCuenta.sumOf { it.value }) return null
    return primera.key
}

/**
 * El pago por PSE que avisa Bancolombia por SMS: «Pagaste $138,600.00 a Coomeva Medicina Prepagada S A
 * desde tu producto 8133». El «desde tu producto» solo lo escribe en los pagos por PSE.
 */
private val PAGO_POR_PSE_DEL_SMS = Regex("""\bpagaste\s+\$?\s*[\d.,]+\s+a\s+.+?\bdesde\s+tu\s+producto\b""", RegexOption.IGNORE_CASE)

/** ¿[texto] es un aviso de un pago por PSE (el correo de PSE o el SMS de Bancolombia)? */
internal fun esUnAvisoDePagoPorPse(texto: String): Boolean =
    com.jvillada.movi.server.routes.esUnCorreoDePse(texto) || PAGO_POR_PSE_DEL_SMS.containsMatchIn(texto)

/**
 * [pagosPorPse] a partir de los avisos confirmados ([avisos]: el texto y el movimiento que lo anotó) y
 * los movimientos vivos ([eventos]): un movimiento cuenta una vez aunque lo hayan avisado el SMS y el
 * correo, y lo anulado no cuenta (no está en [eventos]). Pura.
 */
fun pagosPorPseDe(avisos: List<Pair<String, String>>, eventos: List<FinancialEvent>): Map<String, Int> {
    val cuentaDe = eventos.associate { it.id to it.accountId }
    return avisos.asSequence()
        .filter { (texto, _) -> esUnAvisoDePagoPorPse(texto) }
        .mapNotNull { (_, eventoId) -> cuentaDe[eventoId]?.let { eventoId to it } }
        .distinctBy { it.first }
        .groupingBy { it.second }
        .eachCount()
}

/** «Coomeva» nombra a «Coomeva Medicina Prepagada»; «Celular» no nombra a «Colombia Telecomunicaciones». */
private fun reglaNombraALaEmpresa(nombreDeLaRegla: String, marcaDeLaEmpresa: List<String>): Boolean {
    val marcaDeLaRegla = marcaDe(nombreDeLaRegla)
    return marcaDeLaRegla.isNotEmpty() && marcaDeLaRegla.all { it in marcaDeLaEmpresa }
}

/**
 * La cuenta de la que salió el último pago a [deuda]: la otra pata del traspaso más reciente que
 * ENTRÓ a esa deuda. Así se pagó la cuota la vez pasada, y así se propone esta.
 */
private fun origenDeLosPagosA(deuda: Account, eventos: List<FinancialEvent>, origenes: Set<String>): String? {
    val porTraspaso = eventos.filter { it.transferId != null }.groupBy { it.transferId }
    return eventos
        .filter { it.accountId == deuda.id && it.type == TransactionType.INCOME && it.transferId != null }
        .sortedByDescending { it.timestamp }
        .firstNotNullOfOrNull { pata ->
            porTraspaso[pata.transferId].orEmpty()
                .firstOrNull { it.id != pata.id && it.type == TransactionType.EXPENSE && it.accountId in origenes }
                ?.accountId
        }
}

/** [loQueMoviSabeDelPago] con lo que el dueño [uid] tiene en la base. Dentro de una transacción. */
fun Transaction.loQueMoviSabeDelPagoDe(uid: String, parsed: ParsedSms): ParsedSms {
    val cuentas = Accounts.selectAll().where { Accounts.userId eq uid }.map { it.toAccount() }
    val porId = cuentas.associateBy { it.id }
    // Vuelta 134 (M-8): la cuota de este mes, de la misma función que Pagos (en uno que cambia, la última pagada).
    val condiciones = Credits.selectAll().where { Credits.userId eq uid }
        .mapNotNull { fila -> porId[fila[Credits.accountId]]?.let { it to fila.toCreditTerms() } }
    val ultimas = ultimasCuotasPagadasDe(uid, condiciones.map { (cuenta, terms) -> terms to cuenta.name })
    val conCondiciones = condiciones.map { (cuenta, terms) ->
        DeudaDelDueno(
            cuenta = cuenta,
            banco = terms.bank,
            cuota = com.jvillada.movi.shared.model.cuotaDeEsteMes(terms, ultimas[terms.accountId]),
            cuotaEstimada = terms.cuotaCambia(),
            noLaPagaEl = terms.paidBy != null || terms.payrollDeduction,
        )
    }
    // Un crédito sin condiciones cargadas igual se reconoce por su nombre.
    val sinCondiciones = cuentas
        .filter { it.type == AccountType.LOAN && conCondiciones.none { d -> d.cuenta.id == it.id } }
        .map { DeudaDelDueno(cuenta = it) }
    val reglas = RecurringRules.selectAll().where { RecurringRules.userId eq uid }.map {
        ReglaDelDueno(
            nombre = it[RecurringRules.name],
            cuentaId = it[RecurringRules.accountId],
            tipo = runCatching { TransactionType.valueOf(it[RecurringRules.type]) }.getOrDefault(TransactionType.EXPENSE),
        )
    }
    val eventos = loadNonVoidedEventsIn(uid)
    // Vuelta 107: los avisos confirmados con su movimiento, para saber de qué cuenta sale PSE.
    val confirmados = SmsMessages.selectAll()
        .where { (SmsMessages.userId eq uid) and SmsMessages.eventoId.isNotNull() }
        .mapNotNull { fila -> fila[SmsMessages.eventoId]?.let { fila[SmsMessages.text] to it } }
    return loQueMoviSabeDelPago(
        parsed, cuentas, conCondiciones + sinCondiciones, reglas, eventos,
        pagosPorPse = pagosPorPseDe(confirmados, eventos),
    )
}
