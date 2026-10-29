package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.data.Repositories
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo

/*
 * # Vuelta 63 · el abono mensual, un recordatorio que se tilda
 *
 * La fila «Abono extra a Vehículo 4083 · $500.000 · el día 15» de Pagos no es un fijo (ver
 * `AbonoADeuda.kt` en `:core`): pendiente no resta de «Te quedan» ni de «Falta por pagar». Su botón es
 * «Anotar este abono», que abre el Traspaso de la vuelta 62 ya lleno —desde la cuenta de la regla,
 * hacia la deuda, por el monto— y, al guardar, sella la ocurrencia con la pata que salió. Si el monto
 * cambió, igual queda sellado (lo decidió el dueño); si el sello no se puede poner, Movi lo empareja
 * sola a ±5 %.
 */

/** La ocurrencia del abono mensual que un guardado en Agregar salda. */
data class AbonoDeLaRegla(
    val ruleId: String,
    /** El `"YYYY-MM"` del sello: el mes del vencimiento (`PagoDelPeriodo.periodoDelSello`). */
    val periodo: String,
)

/**
 * La ocurrencia que la fila [pago] del abono sella: su `periodoDelSello`, o —si todavía no vence y el
 * server no la emitió— el mes de su vencimiento, que es la misma clave. `null` si no hay fecha.
 */
fun abonoDeLaFila(pago: PagoDelPeriodo): AbonoDeLaRegla? {
    val periodo = pago.periodoDelSello ?: pago.vence.take(7).takeIf { it.length == 7 } ?: return null
    return AbonoDeLaRegla(ruleId = pago.ruleId, periodo = periodo)
}

/**
 * **Agregar, listo para anotar el abono de [pago]**: Traspaso con la deuda en «Hacia» —o Cuota si la
 * deuda es una tarjeta ([esTarjeta]): a una tarjeta no se le hace un traspaso, se le paga—, la cuenta
 * de la regla en «Desde» y el monto planeado. Al guardar, sella la fila ([Screen.QuickAdd.presetAbono]).
 */
fun hojaDelAbono(pago: PagoDelPeriodo, esTarjeta: Boolean): Screen.QuickAdd = Screen.QuickAdd(
    presetAccountId = pago.cuentaId,
    presetMonto = pago.monto.takeIf { it > 0L },
    presetDeudaId = pago.haciaDeuda,
    presetPestana = if (esTarjeta) null else PestanaDeAgregar.TRASPASO,
    presetAbono = abonoDeLaFila(pago),
)

/**
 * Sella [abono] con la pata [eventId] que salió hacia la deuda. Un fallo no se le muestra a nadie: el
 * traspaso ya quedó guardado, y Movi lo empareja sola si el monto está a ±5 %.
 */
suspend fun sellarElAbono(abono: AbonoDeLaRegla, eventId: String) {
    runCatching { Repositories.wallets.markOccurrence(abono.ruleId, abono.periodo, eventId) }
}

/** Lo que dice debajo la fila del abono pendiente a un crédito. */
const val EL_ABONO_PENDIENTE_NO_RESTA: String =
    "No es un pago fijo: no baja «Te quedan» hasta que hagas el traspaso."

/**
 * Lo que dice la fila del abono pendiente: a un crédito, [EL_ABONO_PENDIENTE_NO_RESTA]; a una tarjeta,
 * que pagarle de más no cambia «Te quedan» (lo comprado ya contó).
 */
fun textoDelAbonoPendiente(pago: PagoDelPeriodo): String =
    if (pago.abonoATarjeta) com.jvillada.movi.shared.model.EL_ABONO_A_UNA_TARJETA_NO_CAMBIA_TE_QUEDAN
    else EL_ABONO_PENDIENTE_NO_RESTA

/** Marca las filas de abono cuya deuda está en [tarjetas] (ids de cuentas de tarjeta). */
fun conLosAbonosATarjeta(checklist: List<PagoDelPeriodo>, tarjetas: Set<String>): List<PagoDelPeriodo> =
    if (tarjetas.isEmpty()) checklist
    else checklist.map { if (it.esAbono && it.haciaDeuda in tarjetas) it.copy(abonoATarjeta = true) else it }

/** La pastilla del abono: «El día 15», neutra (un abono planeado no vence). */
fun pastillaDelAbono(pago: PagoDelPeriodo): String {
    val dia = pago.vence.drop(8).take(2).toIntOrNull()
    return if (dia != null) "El día $dia" else "Planeado"
}

/** La línea aparte de la tarjeta «Falta por pagar»: «Aparte, un abono planeado de $500.000». */
fun lineaDelAbonoPlaneado(monto: Long): String = "Aparte, un abono planeado de ${formatCOP(monto)}"
