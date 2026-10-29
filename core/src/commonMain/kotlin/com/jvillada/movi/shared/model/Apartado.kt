package com.jvillada.movi.shared.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Largo máximo del propósito de un [Apartado] («Colegio», «Impuesto predial»). */
const val MAX_PROPOSITO_APARTADO = 60

/**
 * **Un apartado**: plata que sigue en una cuenta tuya pero que ya tiene dueño — «estos $8M son del
 * colegio». No mueve dinero ni crea un movimiento; solo la quita de «Tu plata» (ver [patrimonioDe]).
 *
 * @param accountId la cuenta donde está la plata.
 * @param monto lo que se apartó, en pesos.
 * @param categoria la categoría de gasto que lo consume (null = ninguna lo consume solo).
 * @param desde desde cuándo cuentan los gastos que lo consumen (epoch millis; 0 = sin límite).
 * @param restante lo que queda por gastar: [monto] menos lo consumido ([restanteDelApartado]).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Apartado(
    val id: String = "",
    val accountId: String,
    val proposito: String,
    val monto: Long,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val categoria: String? = null,
    val desde: Long = 0L,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val restante: Long = monto,
)

/** Lo que queda de un apartado de [monto] tras gastar [consumido]; nunca baja de cero. */
fun restanteDelApartado(monto: Long, consumido: Long): Long = (monto - consumido).coerceAtLeast(0L)

/** Cuánto hay apartado en cada cuenta: la suma de [Apartado.restante] por `accountId`. */
fun apartadoPorCuenta(apartados: List<Apartado>): Map<String, Long> =
    apartados.groupBy { it.accountId }.mapValues { (_, v) -> v.sumOf { it.restante } }

/**
 * **Lo apartado que un pago pendiente del período ya resta como fijo.** El colegio es a la vez un
 * apartado («estos $8M son del colegio») y una fila del checklist («Colegio Hija, $4M el 25»): los
 * fijos ya restan los $4M que faltan pagar ([fijosDelPeriodo]), así que «Te quedan» y la caja
 * proyectada no pueden restarlos otra vez como apartado.
 *
 * Se cruzan una fila **pendiente** (gasto en pesos, no un saldo de tarjeta) y un apartado con la
 * misma cuenta ([PagoDelPeriodo.cuentaId] = [Apartado.accountId]) y la misma categoría. Por cada par
 * (cuenta, categoría) cuenta lo menor entre lo que queda de sus apartados y lo que piden sus filas;
 * por cuenta, nunca más que lo apartado que de verdad pesa ([apartadoEfectivoPorCuenta]). Una fila
 * ya pagada no se cruza: su gasto ya bajó el `restante`. Sin cuenta o sin categoría, tampoco.
 *
 * «Tu plata» (el patrimonio) sigue restando el apartado entero: este cruce es solo para lo que mira
 * hacia adelante en el período. Ver [apartadoFueraDeLosFijos].
 */
fun apartadoCubiertoPorLosFijos(
    accounts: List<Account>,
    apartados: List<Apartado>,
    checklist: List<PagoDelPeriodo>,
): Long {
    val pendientes = checklist
        .filter { !it.pagado && !it.esIngreso && !it.montoEsSaldo && !it.esAbono && it.moneda == "COP" }
        .filter { it.cuentaId != null && it.categoria.isNotBlank() }
        .groupBy { it.cuentaId!! to it.categoria }
        .mapValues { (_, filas) -> filas.sumOf { it.monto } }
    if (pendientes.isEmpty()) return 0L
    val efectivo = apartadoEfectivoPorCuenta(accounts, apartados)
    return apartados
        .filter { !it.categoria.isNullOrBlank() }
        .groupBy { it.accountId to it.categoria!! }
        .map { (clave, delPar) -> clave.first to minOf(delPar.sumOf { it.restante }, pendientes[clave] ?: 0L) }
        .groupBy({ it.first }, { it.second })
        .entries.sumOf { (cuenta, cubiertos) -> minOf(cubiertos.sum(), efectivo[cuenta] ?: 0L) }
}

/**
 * **Lo apartado que «Te quedan» resta**: lo que pesa en Tu plata ([apartadoEfectivoPorCuenta]) menos
 * lo que los fijos del período ya restan ([apartadoCubiertoPorLosFijos]). La caja proyectada hace lo
 * mismo al revés: arranca de Tu plata y le devuelve lo cubierto, porque el pago pendiente lo resta
 * el día que vence.
 */
fun apartadoFueraDeLosFijos(
    accounts: List<Account>,
    apartados: List<Apartado>,
    checklist: List<PagoDelPeriodo>,
): Long = apartadoEfectivoPorCuenta(accounts, apartados).values.sum() -
    apartadoCubiertoPorLosFijos(accounts, apartados, checklist)
