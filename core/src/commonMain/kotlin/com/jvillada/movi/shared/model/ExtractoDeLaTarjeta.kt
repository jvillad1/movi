package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * # El extracto de la tarjeta, por fechas (vuelta 62)
 *
 * La revisión del 29-oct: con el corte el 20 ya registrado, «Pago tarjeta Nu» del 5 de noviembre
 * pedía el saldo entero ($1.583.000) y la ficha solo decía el mínimo. Con los movimientos se sabe
 * más: hasta el corte había $1.392.700, después del corte entraron $290.300 que van al próximo
 * extracto, y ya se pagaron $100.000. **Esto no es una estimación: es una suma por fecha** de
 * movimientos que Movi ya tiene. No hay regla de plata nueva.
 *
 * ## Qué cuenta
 *
 * - **Facturado al corte** ([facturado]): la deuda de la tarjeta al final del día del corte —las
 *   compras netas hasta ese día, con lo que se pagó antes ya restado— menos los pagos y abonos que
 *   entraron entre el corte y hoy. Esos pagos van contra este extracto primero: es lo que el banco
 *   exige antes de la fecha de pago.
 * - **Mínimo** ([minimo]): el `CardTerms.pagoMinimo` tecleado, si está. No se deduce.
 * - **Después del corte** ([despuesDelCorte]): las compras posteriores al corte, que van al
 *   próximo extracto (menos lo que se haya pagado de más contra el extracto).
 * - **Fecha de pago** ([fechaDePago]): el `paymentDay` siguiente al corte.
 *
 * Con eso, `facturado + despuesDelCorte` es la deuda de hoy (salvo un saldo a favor).
 *
 * ## Las compras diferidas
 *
 * Una compra a 36 cuotas no se factura entera: el extracto trae una cuota. Si Movi **conoce** las
 * cuotas de una compra ([DiferidoConocido]) al corte cuenta solo lo que ya se facturó de ella. Si no
 * las conoce, no inventa: [puedeIncluirDiferidos] lo dice y la pantalla escribe «puede incluir
 * compras diferidas». Desde la vuelta 63 el movimiento guarda sus cuotas ([FinancialEvent.cuotas]) cuando
 * vino del extracto de movimientos de la tarjeta de Bancolombia: con eso una compra de $1.200.000 a 12
 * cuotas cuenta $100.000 en el corte que la facturó. La división es entera, como la de la cuota de
 * cualquier compra acá (el peso que sobra va a la última). Lo anotado a mano o por SMS sigue sin
 * cuotas, y la frase sale si hay algo facturado así.
 *
 * ## Moneda
 *
 * Solo la de la cuenta. Una tarjeta con su hermana en dólares («Master Black 3684» y «… 3684 USD»)
 * son dos cuentas, y cada una tiene su extracto: no se mezclan pesos con dólares.
 */
@Serializable
data class ExtractoDeLaTarjeta(
    /** El corte más reciente que ya llegó, en ISO (`"2026-10-20"`). */
    val corte: String,
    /** El día de pago de ese extracto, en ISO. */
    val fechaDePago: String,
    /** Lo que falta pagar de ese extracto (nunca negativo). */
    val facturado: Long,
    /** Lo que se pagó desde el corte y fue contra ese extracto. */
    val pagadoDesdeElCorte: Long = 0L,
    /** El mínimo tecleado del extracto, o `null` si no está cargado. */
    val minimo: Long? = null,
    /** Las compras posteriores al corte: van al próximo extracto. */
    val despuesDelCorte: Long = 0L,
    val moneda: String = "COP",
    /** Hay algo facturado cuyas cuotas Movi no conoce: puede ser una compra diferida. */
    val puedeIncluirDiferidos: Boolean = false,
)

/**
 * Re-revisión de la vuelta 72 · **el mínimo que de verdad se puede pagar**: el tecleado, pero nunca más que lo
 * facturado al corte ([ExtractoDeLaTarjeta.facturado] + lo ya pagado contra él). Sin ese tope, un mínimo de
 * $180.000 sobre $60.000 facturados (o sobre una tarjeta pagada entera antes del corte) no se saldaba nunca:
 * [ExtractoDeLaTarjeta.pagadoDesdeElCorte] ya está limitado a lo facturado. `null` sin mínimo cargado.
 */
val ExtractoDeLaTarjeta.minimoEfectivo: Long?
    get() = minimo?.let { minOf(it, facturado + pagadoDesdeElCorte) }

/** Lo que le falta al [minimoEfectivo], nunca negativo; `null` sin mínimo cargado. */
val ExtractoDeLaTarjeta.minimoQueFalta: Long?
    get() = minimoEfectivo?.let { (it - pagadoDesdeElCorte).coerceAtLeast(0L) }

/** Una compra de la tarjeta cuyas cuotas Movi conoce. */
data class DiferidoConocido(val eventoId: String, val cuotas: Int)

/**
 * El extracto del corte más reciente de la tarjeta ([terms]), en [moneda], con sus [eventos] sin
 * anulados (los de la cuenta; los de otra moneda se ignoran). `null` sin día de corte: sin él no hay
 * fechas que sumar. Pura: «hoy» entra por parámetro.
 */
fun extractoDeLaTarjeta(
    terms: CardTerms,
    moneda: String,
    eventos: List<FinancialEvent>,
    hoy: LocalDate,
    diferidos: List<DiferidoConocido> = emptyList(),
    zone: TimeZone = AppTimeZone.zone,
): ExtractoDeLaTarjeta? {
    val diaDeCorte = terms.cutoffDay?.takeIf { it in 1..31 } ?: return null
    val corte = corteMasReciente(diaDeCorte, hoy)
    val pago = fechaDePagoDespuesDe(corte, terms.paymentDay)
    // Vuelta 63: las cuotas que el banco dijo (el importador las guarda en el movimiento) más las que
    // lleguen aparte. Una compra con sus cuotas conocidas —aunque sea una sola— ya no es «desconocida»;
    // solo las de más de una cuota se reparten.
    val conocidas: Map<String, Int> = eventos.mapNotNull { e -> e.cuotas?.let { e.id to it.coerceAtLeast(1) } }.toMap() +
        diferidos.associate { it.eventoId to it.cuotas.coerceAtLeast(1) }
    val cuotasPorEvento = conocidas.filterValues { it > 1 }

    val enSuMoneda = eventos
        .filter { it.currency == moneda }
        .map { it to epochMillisToAppDate(it.timestamp, zone) }
        .sortedBy { it.first.timestamp }
    val hastaElCorte = enSuMoneda.filter { (_, fecha) -> fecha <= corte }
    val desdeElCorte = enSuMoneda.filter { (_, fecha) -> fecha > corte }

    val deudaAlCorte = hastaElCorte.sumOf { (e, _) -> signedDelta(AccountType.CREDIT_CARD, e.type, e.amount) }
    // Lo que de una compra diferida conocida todavía no se facturó: las cuotas que faltan.
    val sinFacturar = hastaElCorte.sumOf { (e, fecha) ->
        val cuotas = cuotasPorEvento[e.id]
        if (e.type != TransactionType.EXPENSE || cuotas == null) 0L
        else e.amount - e.amount * minOf(cuotas, cortesFacturados(fecha, corte, diaDeCorte)) / cuotas
    }
    val alCorte = deudaAlCorte - sinFacturar
    val facturadoBruto = alCorte.coerceAtLeast(0L)
    val saldoAFavor = (-alCorte).coerceAtLeast(0L)

    val pagosDesde = desdeElCorte.filter { it.first.type == TransactionType.INCOME }.sumOf { it.first.amount }
    // Una compra diferida conocida va al próximo extracto con su primera cuota.
    val comprasDesde = desdeElCorte.filter { it.first.type == TransactionType.EXPENSE }.sumOf { (e, _) ->
        val cuotas = cuotasPorEvento[e.id]
        if (cuotas == null) e.amount else e.amount / cuotas
    }
    val contraElExtracto = minOf(pagosDesde, facturadoBruto)
    val sobrante = pagosDesde - contraElExtracto
    val facturado = facturadoBruto - contraElExtracto

    return ExtractoDeLaTarjeta(
        corte = corte.toString(),
        fechaDePago = pago.toString(),
        facturado = facturado,
        pagadoDesdeElCorte = contraElExtracto,
        minimo = terms.pagoMinimo?.takeIf { it > 0L },
        despuesDelCorte = (comprasDesde - sobrante - saldoAFavor).coerceAtLeast(0L),
        moneda = moneda,
        puedeIncluirDiferidos = facturadoBruto > 0L && hayComprasSinCuotasConocidas(hastaElCorte, conocidas.keys),
    )
}

/**
 * ¿Lo que se debía al corte puede traer una compra cuyas cuotas no se conocen? Se mira desde la
 * última vez que la tarjeta quedó en cero (o a favor) antes del corte: lo de antes ya se pagó entero.
 * Un saldo inicial o un ajuste de origen desconocido también cuenta: no se sabe qué tiene adentro.
 */
private fun hayComprasSinCuotasConocidas(
    hastaElCorte: List<Pair<FinancialEvent, LocalDate>>,
    conCuotasConocidas: Set<String>,
): Boolean {
    var deuda = 0L
    var desde = 0
    hastaElCorte.forEachIndexed { i, (e, _) ->
        deuda += signedDelta(AccountType.CREDIT_CARD, e.type, e.amount)
        if (deuda <= 0L) desde = i + 1
    }
    return hastaElCorte.drop(desde).any { (e, _) -> e.type == TransactionType.EXPENSE && e.id !in conCuotasConocidas }
}

/** El día [dia] del mes de [fecha], o el último del mes si ese mes no lo tiene (el 31 en febrero). */
private fun diaDelMes(fecha: LocalDate, dia: Int): LocalDate {
    val primero = LocalDate(fecha.year, fecha.monthNumber, 1)
    val ultimo = primero.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
    return LocalDate(fecha.year, fecha.monthNumber, minOf(dia, ultimo))
}

/** El corte más reciente que ya llegó: el de este mes si ya pasó (o es hoy), si no el del anterior. */
fun corteMasReciente(diaDeCorte: Int, hoy: LocalDate): LocalDate {
    val deEsteMes = diaDelMes(hoy, diaDeCorte)
    return if (deEsteMes <= hoy) deEsteMes else diaDelMes(hoy.minus(DatePeriod(months = 1)), diaDeCorte)
}

/** El día de pago del extracto que cerró en [corte]: el primer [diaDePago] después del corte. */
fun fechaDePagoDespuesDe(corte: LocalDate, diaDePago: Int): LocalDate {
    val dia = diaDePago.coerceIn(1, 31)
    val deEseMes = diaDelMes(corte, dia)
    return if (deEseMes > corte) deEseMes else diaDelMes(corte.plus(DatePeriod(months = 1)), dia)
}

/** Cuántos cortes, desde la compra del [fecha] hasta [corte] inclusive, ya facturaron una cuota. */
private fun cortesFacturados(fecha: LocalDate, corte: LocalDate, diaDeCorte: Int): Int {
    var primerCorte = diaDelMes(fecha, diaDeCorte)
    if (primerCorte < fecha) primerCorte = diaDelMes(fecha.plus(DatePeriod(months = 1)), diaDeCorte)
    val meses = (corte.year - primerCorte.year) * 12 + (corte.monthNumber - primerCorte.monthNumber)
    return (meses + 1).coerceAtLeast(0)
}

/**
 * [extractoDeLaTarjeta] con «hoy» en ISO y la zona por su id: la forma que usa el server, que no ve
 * `kotlinx-datetime` (usa `java.time`).
 */
fun extractoDeLaTarjetaAl(
    terms: CardTerms,
    moneda: String,
    eventos: List<FinancialEvent>,
    hoyIso: String,
    zonaId: String? = null,
): ExtractoDeLaTarjeta? =
    extractoDeLaTarjeta(terms, moneda, eventos, LocalDate.parse(hoyIso), zone = AppTimeZone.resolve(zonaId))
