package com.jvillada.movi.ui.credits

import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DeudaParaSalir
import com.jvillada.movi.shared.model.OrigenDelRecuerdo
import com.jvillada.movi.shared.model.TipoDeDeuda
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.quickadd.PestanaDeAgregar

/*
 * # El abono que se puede hacer (vuelta 62)
 *
 * La revisión del 29-oct: en «¿Y si abonas de más?» y en «Cómo salir de tus deudas» la única acción
 * era «Cerrar». Ahora el monto simulado se anota con el camino de siempre —Agregar → Traspaso, ya
 * lleno: desde la cuenta del débito automático (si la tiene), hacia el crédito, por ese monto—, y en
 * el plan se puede pedir que Movi lo recuerde cada mes.
 *
 * ## Por qué «Recordármelo cada mes» no crea una regla recurrente
 *
 * Una regla recurrente es un gasto o un ingreso de UNA cuenta: no tiene destino, así que no puede ser
 * un traspaso. Y el server cierra la puerta a propósito: «un traspaso no puede ser la ocurrencia de
 * un recurrente». Una regla de gasto por el abono sería además un fijo más que «Te quedan» y la caja
 * restarían —un abono es un traspaso puro y no cuenta en el mes— y el traspaso nunca la tildaría: se
 * quedaría «vencida» cada mes. Eso es una regla de plata nueva, y esta vuelta no la trae.
 *
 * Así que usa los dos recordatorios que ya existen: **el aviso de la deuda** (`remindMe` de sus
 * condiciones: el día de pago de cada mes, por correo y en el teléfono), que se prende si estaba
 * apagado, y **lo que Movi sabe de ti** (la memoria de Movi AI), donde queda el plan con su monto.
 */

/** El botón principal del simulador con un monto. */
const val ANOTAR_ESTE_ABONO: String = "Anotar este abono"

/** El botón del plan de salida que pide recordar el abono. */
const val RECORDARMELO_CADA_MES: String = "Recordármelo cada mes"

/** Lo que el plan dice debajo de los botones: el abono no es un fijo. */
const val EL_ABONO_NO_ES_UN_FIJO: String =
    "El abono es un traspaso: no queda como un pago fijo ni resta de «Te quedan»."

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

/** Lo que queda en la memoria de Movi: «Abonar $500.000 extra al mes a Libre inversión 9695, el día 5…». */
fun recuerdoDelAbono(nombre: String, monto: Long, dia: Int?): String =
    "Abonar ${formatCOPSinOcultar(monto)} extra al mes a $nombre" +
        (dia?.let { ", el día $it" } ?: "") +
        ", según el plan para salir de las deudas."

/** La confirmación debajo del botón, después de guardarlo. */
fun confirmacionDelRecordatorio(nombre: String, monto: Long, dia: Int?): String =
    "Listo: Movi te avisa " + (dia?.let { "el día $it de cada mes" } ?: "cada mes") +
        " por $nombre y recuerda tu plan de abonar ${formatCOPSinOcultar(monto)}."

/** El día de pago de la deuda, de sus condiciones. */
fun diaDePagoDe(deuda: DeudaParaSalir, creditos: List<CreditSummary>, tarjetas: List<CardSummary>): Int? =
    if (deuda.tipo == TipoDeDeuda.TARJETA) tarjetas.firstOrNull { it.account.id == deuda.id }?.terms?.paymentDay
    else creditos.firstOrNull { it.account.id == deuda.id }?.terms?.dayOfMonth

/**
 * **«Recordármelo cada mes»**: deja el plan en la memoria de Movi y prende el aviso de la deuda si estaba
 * apagado. No crea ninguna regla (ver el encabezado del archivo). Devuelve la confirmación.
 */
suspend fun recordarElAbono(
    deuda: DeudaParaSalir,
    monto: Long,
    creditos: List<CreditSummary>,
    tarjetas: List<CardSummary>,
): String {
    val dia = diaDePagoDe(deuda, creditos, tarjetas)
    val repo = Repositories.wallets
    repo.guardarRecuerdo(recuerdoDelAbono(deuda.nombre, monto, dia), OrigenDelRecuerdo.A_MANO)
    if (deuda.tipo == TipoDeDeuda.TARJETA) {
        tarjetas.firstOrNull { it.account.id == deuda.id }?.terms?.takeIf { !it.remindMe }
            ?.let { repo.putCardTerms(it.copy(remindMe = true)) }
    } else {
        creditos.firstOrNull { it.account.id == deuda.id }?.terms?.takeIf { !it.remindMe }
            ?.let { repo.putCreditTerms(it.copy(remindMe = true)) }
    }
    return confirmacionDelRecordatorio(deuda.nombre, monto, dia)
}
