package com.jvillada.movi.server.sms

import com.jvillada.movi.server.balance.loadNonVoidedEventsIn
import com.jvillada.movi.server.balance.toAccount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Credits
import com.jvillada.movi.server.db.RecurringRules
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
import kotlin.math.roundToLong
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll

/** Un crédito del dueño, con lo que hace falta para reconocer a quién se le paga. */
data class DeudaDelDueno(
    val cuenta: Account,
    /** El banco que escribió el dueño en las condiciones del crédito («Banco de Occidente»). */
    val banco: String? = null,
    /** La cuota mensual, para desempatar dos créditos del mismo banco. */
    val cuota: Long? = null,
    /** La paga otro (`paidBy`) o es una libranza: no sale por PSE de una cuenta suya. */
    val noLaPagaEl: Boolean = false,
)

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
): ParsedSms {
    val marca = marcaDe(parsed.merchant)
    if (marca.isEmpty()) return parsed
    val porId = cuentas.associateBy { it.id }

    val deuda: Account? = when (parsed.category) {
        CUOTA_CATEGORY -> unaSola(
            deudas.filter { !it.noLaPagaEl && it.cuenta.type == AccountType.LOAN && it.cuenta.currency == parsed.currency }
                .filter { nombra("${it.cuenta.name} ${it.banco.orEmpty()}", marca) },
        ) { it.cuota == parsed.amount.roundToLong() }?.cuenta
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
        ?: reglas.filter { it.tipo == TransactionType.EXPENSE && it.cuentaId in origenes && reglaNombraALaEmpresa(it.nombre, marca) }
            .map { it.cuentaId to it.nombre }
            .distinctBy { it.first }
            .singleOrNull()
            ?.let { (id, nombre) -> id to "La de tu recurrente «$nombre»" }
        ?: eventos.asSequence()
            .filter { it.type == TransactionType.EXPENSE && it.accountId in origenes }
            .filter { nombra("${it.merchant.orEmpty()} ${it.description}", marca) }
            .maxByOrNull { it.timestamp }
            ?.let { it.accountId to "La que usaste la última vez para ${parsed.merchant}" }
        ?: (null to null)

    return parsed.copy(
        cuentaSugeridaId = cuentaId?.takeIf { it in porId },
        cuentaSugeridaPor = por.takeIf { cuentaId != null },
        deudaSugeridaId = deuda?.id,
        traspasoHaciaId = hacia?.id,
    )
}

/** Uno solo; con varios, el único que cumple [desempate]; si no, ninguno. */
private fun <T> unaSola(candidatas: List<T>, desempate: (T) -> Boolean): T? =
    candidatas.singleOrNull() ?: candidatas.filter(desempate).singleOrNull()

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
    val conCondiciones = Credits.selectAll().where { Credits.userId eq uid }.mapNotNull { fila ->
        val cuenta = porId[fila[Credits.accountId]] ?: return@mapNotNull null
        DeudaDelDueno(
            cuenta = cuenta,
            banco = fila[Credits.bank],
            cuota = fila[Credits.installment],
            noLaPagaEl = fila[Credits.paidBy] != null || fila[Credits.payrollDeduction] == true,
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
    return loQueMoviSabeDelPago(parsed, cuentas, conCondiciones + sinCondiciones, reglas, loadNonVoidedEventsIn(uid))
}
