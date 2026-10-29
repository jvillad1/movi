package com.jvillada.movi.ui.credits

import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DeudaParaSalir
import com.jvillada.movi.shared.model.reglaDelAbono
import com.jvillada.movi.shared.model.TipoDeDeuda
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar

/*
 * # El abono que se puede hacer (vuelta 62) y que se recuerda (vuelta 63)
 *
 * La revisión del 29-oct: en «¿Y si abonas de más?» y en «Cómo salir de tus deudas» la única acción
 * era «Cerrar». Ahora el monto simulado se anota con el camino de siempre —Agregar → Traspaso, ya
 * lleno: desde la cuenta del débito automático (si la tiene), hacia el crédito, por ese monto—, y se
 * puede pedir que Movi lo recuerde cada mes.
 *
 * ## «Recordármelo cada mes» crea una regla recurrente (vuelta 63)
 *
 * Decisión del dueño (29-oct): **un recordatorio que se tilda**. La regla lleva la deuda en
 * `RecurringRule.haciaDeuda` («Abono extra a Vehículo 4083 · $500.000», el día de pago de la deuda) y
 * aparece en Pagos. **No es un fijo**: mientras está pendiente no resta de «Te quedan» ni de la caja;
 * el mes que se hace, el traspaso baja «Te quedan» por la regla de siempre
 * (`pagosDeDeudaFueraDelChecklist`) y tilda la fila (ver `AbonoADeuda.kt`). Pedirlo otra vez actualiza
 * la regla de esa deuda en vez de crear otra. Ya no usa la memoria de Movi AI; lo que la vuelta 62 dejó
 * ahí se queda.
 */

/** El botón principal del simulador con un monto. */
const val ANOTAR_ESTE_ABONO: String = "Anotar este abono"

/** El botón del plan de salida que pide recordar el abono. */
const val RECORDARMELO_CADA_MES: String = "Recordármelo cada mes"

/** Lo que el plan dice debajo de los botones: el abono no es un fijo. */
/**
 * Lo que el plan dice debajo de los botones, según a qué va el abono: a un crédito
 * [EL_ABONO_NO_ES_UN_FIJO]; a una tarjeta, que no cambia «Te quedan» (lo comprado ya contó).
 */
fun elAbonoNoEsUnFijo(aTarjeta: Boolean): String =
    if (aTarjeta) com.jvillada.movi.shared.model.EL_ABONO_A_UNA_TARJETA_NO_CAMBIA_TE_QUEDAN else EL_ABONO_NO_ES_UN_FIJO

const val EL_ABONO_NO_ES_UN_FIJO: String =
    "No queda como un pago fijo. El mes que lo hagas, sale de tu plata y baja «Te quedan»."

/**
 * **Agregar, listo para anotar el abono a [credit]** por [monto]: la pestaña Traspaso con el crédito en
 * «Hacia» y, si el crédito se debita solo de una cuenta, esa en «Desde». Es el traspaso de siempre
 * (`transferLegsFor`): baja el capital y no salda la cuota del período.
 */
fun pantallaDelAbono(credit: CreditSummary, monto: Long): Screen.QuickAdd = Screen.QuickAdd(
    presetAccountId = credit.terms?.debitoAutomaticoDesde,
    presetMonto = monto,
    presetDeudaId = credit.account.id,
    presetPestana = PestanaDeAgregar.TRASPASO,
)

/**
 * Lo mismo desde el plan de salida, donde la deuda puede ser una tarjeta: a una tarjeta no se le hace
 * un traspaso, se le paga (la pestaña Cuota con la tarjeta en «Pagas»).
 */
fun pantallaDelAbono(deuda: DeudaParaSalir, monto: Long, creditos: List<CreditSummary>): Screen.QuickAdd =
    if (deuda.tipo == TipoDeDeuda.TARJETA) {
        Screen.QuickAdd(presetMonto = monto, presetDeudaId = deuda.id)
    } else {
        creditos.firstOrNull { it.account.id == deuda.id }?.let { pantallaDelAbono(it, monto) }
            ?: Screen.QuickAdd(presetMonto = monto, presetDeudaId = deuda.id, presetPestana = PestanaDeAgregar.TRASPASO)
    }

/**
 * La confirmación debajo del botón: «Listo: «Abono extra a Nu» queda en Pagos el día 5 de cada mes. Se
 * marca solo cuando anotes el traspaso.»
 */
fun confirmacionDelRecordatorio(nombre: String, monto: Long, dia: Int?): String =
    "Listo: el abono de ${formatCOPSinOcultar(monto)} a $nombre queda en Pagos" +
        (dia?.let { " el día $it de cada mes" } ?: " cada mes") +
        ". Se marca solo cuando anotes el traspaso."

/** El aviso del campo del plan cuando el abono es más que todo lo que se debe. */
fun avisoDeAbonoMayorQueLaDeuda(total: Long): String =
    "Es más que todo lo que debes en el plan (${formatCOPSinOcultar(total)})."

/** Lo que el plan prellena al anotar: nunca más que el saldo de la deuda a la que va. */
fun montoParaAnotar(monto: Long, saldoDeLaDeuda: Long): Long = if (saldoDeLaDeuda > 0L) minOf(monto, saldoDeLaDeuda) else monto

/** El día de pago de la deuda, de sus condiciones. */
fun diaDePagoDe(deuda: DeudaParaSalir, creditos: List<CreditSummary>, tarjetas: List<CardSummary>): Int? =
    if (deuda.tipo == TipoDeDeuda.TARJETA) tarjetas.firstOrNull { it.account.id == deuda.id }?.terms?.paymentDay
    else creditos.firstOrNull { it.account.id == deuda.id }?.terms?.dayOfMonth

/**
 * **«Recordármelo cada mes»** desde el plan de salida: crea —o actualiza, si ya hay una para esa deuda—
 * la regla del abono mensual ([recordarElAbonoA]). Devuelve la confirmación.
 */
suspend fun recordarElAbono(
    deuda: DeudaParaSalir,
    monto: Long,
    creditos: List<CreditSummary>,
    tarjetas: List<CardSummary>,
): String = recordarElAbonoA(
    deudaId = deuda.id,
    nombre = deuda.nombre,
    monto = monto,
    dia = diaDePagoDe(deuda, creditos, tarjetas),
    desde = creditos.firstOrNull { it.account.id == deuda.id }?.terms?.debitoAutomaticoDesde,
)

/** Lo mismo desde la ficha del crédito («¿Y si abonas de más?»). */
suspend fun recordarElAbono(credit: CreditSummary, monto: Long): String = recordarElAbonoA(
    deudaId = credit.account.id,
    nombre = credit.account.name,
    monto = monto,
    dia = credit.terms?.dayOfMonth,
    desde = credit.terms?.debitoAutomaticoDesde,
)

/**
 * **La regla del abono mensual a [deudaId]** (`RecurringRule.haciaDeuda`): la crea, o actualiza la que
 * ya existe para esa deuda —pedirlo otra vez cambia el monto, no duplica la fila de Pagos—. El día es
 * el de pago de la deuda; sin él, el de la regla que ya estaba, o el 1.
 */
suspend fun recordarElAbonoA(deudaId: String, nombre: String, monto: Long, dia: Int?, desde: String?): String {
    val repo = Repositories.wallets
    val existente = repo.getRecurringRules().firstOrNull { it.haciaDeuda == deudaId }
    val elDia = dia ?: existente?.dayOfMonth ?: 1
    val nueva = reglaDelAbono(
        id = existente?.id.orEmpty(),
        deudaId = deudaId,
        nombreDeLaDeuda = nombre,
        monto = monto,
        dia = elDia,
        desdeCuentaId = desde ?: existente?.accountId,
    )
    if (existente != null) repo.updateRecurringRule(existente.id, existente.copy(
        name = nueva.name, amount = monto, dayOfMonth = nueva.dayOfMonth, category = nueva.category,
        type = nueva.type, accountId = nueva.accountId, haciaDeuda = deudaId,
    ))
    else repo.createRecurringRule(nueva)
    return confirmacionDelRecordatorio(nombre, monto, elDia)
}
