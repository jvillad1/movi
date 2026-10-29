package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** Largo máximo del propósito de un [Apartado] («Colegio», «Impuesto predial»). */
const val MAX_PROPOSITO_APARTADO = 60

/** El tope del costo de un gasto para el que se aparta: lo que cabe en el campo de monto (12 dígitos). */
const val MAX_OBJETIVO_APARTADO = 999_999_999_999L

/**
 * **Un apartado**: plata que sigue en una cuenta tuya pero que ya tiene dueño — «estos $8M son del
 * colegio». No mueve dinero ni crea un movimiento; solo la quita de «Tu plata» (ver [patrimonioDe]).
 *
 * @param accountId la cuenta donde está la plata.
 * @param monto lo que se apartó, en pesos.
 * @param categoria la categoría de gasto que lo consume (null = ninguna lo consume solo).
 * @param desde desde cuándo cuentan los gastos que lo consumen (epoch millis; 0 = sin límite).
 * @param restante lo que queda por gastar: [monto] menos lo consumido ([restanteDelApartado]).
 * @param objetivo **cuánto cuesta el gasto** para el que se aparta (vuelta 64: el SOAT, el predial, la
 *   matrícula, una prima), o `null` si el apartado no es para un gasto de un mes puntual. Solo sirve
 *   para sugerir cuánto apartar cada mes ([aporteMensualSugerido]): no es un fijo, no crea movimientos
 *   ni cambia ninguna cifra de plata.
 * @param mesDelGasto el mes en que llega ese gasto, `"AAAA-MM"` ([PeriodoFinanciero.prefijo]); va
 *   junto con [objetivo]: los dos o ninguno.
 *
 * Los dos viajan **siempre** ([EncodeDefault]), también en `null`: el server distingue «el cliente lo
 * quitó» de «el cliente no conoce el campo» (un APK anterior a la vuelta 64) mirando si la clave
 * vino, igual que con `CardTerms.pagoMinimo`. Sin eso, editar el monto desde un APK viejo borraría
 * el objetivo.
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
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val objetivo: Long? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val mesDelGasto: String? = null,
)

// ── Apartado para un gasto de un mes puntual (vuelta 64) ─────────────────────────────────────────

/**
 * El mes de [Apartado.mesDelGasto] leído, o `null` si no tiene (o no se entiende): `"2027-03"` →
 * marzo de 2027.
 */
fun mesDelGastoDe(apartado: Apartado): PeriodoFinanciero? = mesDeTexto(apartado.mesDelGasto)

/** `"AAAA-MM"` → el mes, o `null` si no tiene esa forma. */
fun mesDeTexto(texto: String?): PeriodoFinanciero? {
    val t = texto?.trim() ?: return null
    if (t.length != 7 || t[4] != '-') return null
    val anio = t.substring(0, 4).toIntOrNull() ?: return null
    val mes = t.substring(5, 7).toIntOrNull() ?: return null
    if (mes !in 1..12 || anio < 2000) return null
    return PeriodoFinanciero(anio, mes)
}

/**
 * **Cuántos meses faltan para el gasto**, contados en meses del calendario: en octubre, para marzo
 * faltan 5 (noviembre, diciembre, enero, febrero y marzo). El mismo mes da 0, y uno que ya pasó, un
 * número negativo.
 */
fun mesesHastaElGasto(mes: PeriodoFinanciero, hoy: LocalDate): Int =
    (mes.year * 12 + mes.month) - (hoy.year * 12 + hoy.monthNumber)

/**
 * **El aporte mensual sugerido**: (objetivo − lo apartado) ÷ los meses que faltan, redondeado hacia
 * arriba al peso para que la suma alcance.
 *
 * - Lo apartado ya cubre el objetivo → 0: no hace falta apartar más.
 * - El gasto es este mes → todo lo que falta, ahora (se divide entre 1).
 * - El mes ya pasó → `null`: no hay nada honesto que sugerir.
 *
 * **Es una sugerencia y nada más** (regla del encargo): no es un fijo del período, no se resta de
 * «Te quedan» y no mueve plata. El dueño decide si sube el apartado.
 */
fun aporteMensualSugerido(objetivo: Long, apartado: Long, meses: Int): Long? {
    if (meses < 0) return null
    val falta = objetivo - apartado.coerceAtLeast(0L)
    if (falta <= 0L) return 0L
    val divisor = meses.coerceAtLeast(1).toLong()
    return (falta + divisor - 1) / divisor
}

/**
 * ¿Se puede guardar este gasto del apartado? `null` si sí (o si no hay ninguno: los dos en `null`);
 * si no, el porqué en una frase. La comparten el server (que es la autoridad) y la hoja.
 *
 * Con [hoy], además, el mes no puede haber pasado: lo usa la hoja al elegirlo. El server lo llama sin
 * [hoy], a propósito: un apartado cuyo mes ya pasó tiene que poder editarse (cambiarle el monto) sin
 * que el server lo rechace por un dato que el dueño no está tocando.
 */
fun problemaDelGastoDelApartado(objetivo: Long?, mesDelGasto: String?, hoy: LocalDate? = null): String? {
    if (objetivo == null && mesDelGasto == null) return null
    if (objetivo == null || objetivo <= 0L) return "Escribe cuánto cuesta el gasto"
    if (objetivo > MAX_OBJETIVO_APARTADO) return "Ese monto es demasiado grande"
    val mes = mesDeTexto(mesDelGasto) ?: return "Elige el mes en que llega el gasto"
    if (hoy != null && mesesHastaElGasto(mes, hoy) < 0) return "Ese mes ya pasó: elige uno que venga"
    return null
}

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
