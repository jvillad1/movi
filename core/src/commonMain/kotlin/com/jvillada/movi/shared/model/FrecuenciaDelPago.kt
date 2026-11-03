package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/*
 * # Vuelta 126 · cada cuánto: pagos fijos cada 2, 3 o 6 meses, cada año y de una sola vez
 *
 * Lo pidió un probador (el papá del dueño): «En pagos aparecen solo dos opciones: mensualmente y
 * anualmente. Podrían ser más: cada dos meses, cada tres meses, cada seis meses o por una sola vez».
 * Las facturas bimestrales de servicios, la administración trimestral, la matrícula semestral, el
 * SOAT del año, la cuota inicial de algo.
 *
 * Hasta acá [RecurringRule] era mensual por modelo (`dayOfMonth` y nada más) y todo el motor de
 * vencimientos del server camina mes a mes. Esta vuelta no reescribe ese motor: le agrega **una sola
 * pregunta**, [venceEnElMes], que dice si una regla vence en un mes de calendario dado. El server la hace
 * cada vez que el motor elige o rueda un vencimiento (`dueDateFor`, `ocurrenciaPorPreguntar`, la
 * ocurrencia anterior, «Tus períodos», el sello), y en una regla mensual siempre contesta que sí — así
 * **toda regla mensual da exactamente las mismas cifras que antes** (lo prueba `FrecuenciaDelPagoTest`).
 *
 * Las reglas de plata, decididas por el dueño en el pedido:
 *  - **Una no mensual cuenta su monto entero solo en el período en que vence**, en «Te quedan», «Falta
 *    por pagar», la caja, la cola, la campana, los recordatorios y lo esperado. En los demás períodos no
 *    existe. **No se prorratea**: eso sería un ahorro, y para apartar ya están los apartados.
 *  - **Una sola vez**: vence en la fecha de su ancla. Si no se paga, sigue pendiente (vencida) después
 *    —el server la sigue preguntando, y Pagos la muestra en «Del período de …»— hasta que se pague, se
 *    pause o se borre. Pagada, no vuelve a aparecer.
 *  - **Las especiales no cambian**: la cuota de un crédito, el pago de una tarjeta y el abono a una deuda
 *    son mensuales siempre ([frecuenciaEfectiva]).
 */

/**
 * **Cada cuánto vence un pago fijo** ([RecurringRule.frecuencia]). Un enum y no un entero de meses, por
 * lo mismo que [PeriodicidadDeCobro]: un entero invita a un 0, a un 7 que nadie sabe pintar y a un −1;
 * estos seis son los que existen en el mundo del dueño. [meses] es la distancia entre dos vencimientos;
 * `null` en [UNA_VEZ], que no se repite.
 */
@Serializable
enum class FrecuenciaDelPago(val meses: Int?) {
    MENSUAL(1),
    BIMESTRAL(2),
    TRIMESTRAL(3),
    SEMESTRAL(6),
    ANUAL(12),
    UNA_VEZ(null),
}

/** Las seis, en el orden de las pastillas de la hoja («Cada mes · Cada 2 meses · … · Una sola vez»). */
val FRECUENCIAS_DEL_PAGO: List<FrecuenciaDelPago> = FrecuenciaDelPago.entries.toList()

/** El nombre de cada una en la app: «Cada mes», «Cada 3 meses», «Cada año», «Una sola vez». */
fun nombreDeLaFrecuencia(frecuencia: FrecuenciaDelPago): String = when (frecuencia) {
    FrecuenciaDelPago.MENSUAL -> "Cada mes"
    FrecuenciaDelPago.BIMESTRAL -> "Cada 2 meses"
    FrecuenciaDelPago.TRIMESTRAL -> "Cada 3 meses"
    FrecuenciaDelPago.SEMESTRAL -> "Cada 6 meses"
    FrecuenciaDelPago.ANUAL -> "Cada año"
    FrecuenciaDelPago.UNA_VEZ -> "Una sola vez"
}

/** El ancla de [rule] ([RecurringRule.primerVencimiento]) como fecha, o `null` si no la tiene o no se entiende. */
val RecurringRule.anclaDelVencimiento: LocalDate?
    get() = primerVencimiento?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * **La frecuencia con la que cuenta la regla**: la guardada, salvo que no aplique, y entonces [MENSUAL]:
 *  - sin frecuencia (`null`, toda regla de antes de esta vuelta) o [MENSUAL];
 *  - una regla especial: la cuota de un crédito, el pago de una tarjeta (sintéticas: nunca la traen,
 *    se mira igual), un pago único de crédito o el abono planeado a una deuda — siguen mensuales;
 *  - una no mensual **sin un ancla legible**: el server no la guarda así (la valida), pero si llegara,
 *    contarla todos los meses es avisar de más, nunca perder un pago.
 */
val RecurringRule.frecuenciaEfectiva: FrecuenciaDelPago
    get() {
        val f = frecuencia ?: return FrecuenciaDelPago.MENSUAL
        if (f == FrecuenciaDelPago.MENSUAL) return FrecuenciaDelPago.MENSUAL
        if (esAbonoADeuda || montoEsSaldo || esPagoUnico) return FrecuenciaDelPago.MENSUAL
        if (id.startsWith(CREDIT_RULE_PREFIX) || id.startsWith(CARD_RULE_PREFIX)) return FrecuenciaDelPago.MENSUAL
        if (anclaDelVencimiento == null) return FrecuenciaDelPago.MENSUAL
        return f
    }

/** ¿La regla vence todos los meses? Lo de siempre: toda regla de antes de la vuelta 126. */
val RecurringRule.esMensual: Boolean get() = frecuenciaEfectiva == FrecuenciaDelPago.MENSUAL

/**
 * **¿[rule] vence en el mes de calendario [anio]-[mes]?** La única pregunta que el motor de vencimientos
 * le hace a la frecuencia.
 *
 * - Mensual: siempre.
 * - Cada N meses: en el mes del ancla y en los que le siguen de a N (`ancla + k·N`, k ≥ 0). Antes del
 *   ancla, nunca: el ancla es el **primer** vencimiento.
 * - Una sola vez: solo en el mes del ancla.
 *
 * Es por mes de calendario a propósito: el sello de una ocurrencia es el mes de su vencimiento
 * (`periodOf` en el server), así que el período del dueño —con su corte— decide **cuál** ocurrencia está
 * en juego, y esto decide **si existe**. Pura.
 */
fun RecurringRule.venceEnElMes(anio: Int, mes: Int): Boolean {
    val frecuencia = frecuenciaEfectiva
    if (frecuencia == FrecuenciaDelPago.MENSUAL) return true
    val ancla = anclaDelVencimiento ?: return true
    val distancia = (anio * 12 + (mes - 1)) - (ancla.year * 12 + (ancla.monthNumber - 1))
    if (distancia < 0) return false
    val cada = frecuencia.meses ?: return distancia == 0
    return distancia % cada == 0
}

/** [venceEnElMes] con la fecha de un vencimiento (ISO `"2026-12-15"`, o el sello `"2026-12"`). Ilegible: `true`. */
fun RecurringRule.venceEnElMesDe(iso: String): Boolean {
    val partes = iso.trim().split('-')
    val anio = partes.getOrNull(0)?.toIntOrNull() ?: return true
    val mes = partes.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return true
    return venceEnElMes(anio, mes)
}

/**
 * **El vencimiento de [rule] dentro de [periodo]**, o `null` si en ese período no vence: el día de la
 * regla (recortado al largo del mes, como siempre) en el primer mes del período en que la regla vence.
 * Es la regla pura del pedido; una mensual da exactamente lo de
 * [vencimientoEnElPeriodo] por día (lo prueba `FrecuenciaDelPagoTest`). No mira el piso de la regla
 * (`activeFrom`) ni sus sellos: eso lo pone el server encima. Pura.
 */
fun vencimientoEnElPeriodo(rule: RecurringRule, periodo: PeriodoFinanciero, ajustes: PeriodSettings): LocalDate? {
    val inicio = inicioDelPeriodo(periodo, ajustes)
    val siguiente = inicioDelPeriodo(periodoSiguiente(periodo, ajustes), ajustes)
    var mes = LocalDate(inicio.year, inicio.monthNumber, 1)
    var vueltas = 0
    while (mes < siguiente && vueltas < 3) {
        if (rule.venceEnElMes(mes.year, mes.monthNumber)) {
            val vence = vencimientoDelMes(mes.year, mes.monthNumber, rule.dayOfMonth)
            if (vence >= inicio && vence < siguiente) return vence
        }
        mes = mes.plus(1, DateTimeUnit.MONTH)
        vueltas++
    }
    return null
}

/**
 * **El primer vencimiento de [rule] en [desde] o después**, o `null` si ya no tiene ninguno (una sola vez
 * con el ancla antes de [desde]). Lo que la hoja muestra como «el próximo» al editar. Pura.
 */
fun primerVencimientoDesde(rule: RecurringRule, desde: LocalDate): LocalDate? {
    var mes = LocalDate(desde.year, desde.monthNumber, 1)
    // 12 meses alcanzan para cualquier frecuencia que se repite; uno más por el recorte del día.
    repeat(13) {
        if (rule.venceEnElMes(mes.year, mes.monthNumber)) {
            val vence = vencimientoDelMes(mes.year, mes.monthNumber, rule.dayOfMonth)
            if (vence >= desde) return vence
        }
        mes = mes.plus(1, DateTimeUnit.MONTH)
    }
    return null
}

private val MESES_CORTOS_DE_LA_FRECUENCIA = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** «15 dic». */
private fun fechaCortaDeLaFrecuencia(fecha: LocalDate): String = "${fecha.dayOfMonth} ${MESES_CORTOS_DE_LA_FRECUENCIA[fecha.monthNumber - 1]}"

/**
 * **La cadencia de una regla no mensual, en una línea**: «Cada 3 meses · el próximo, 15 dic» o «Una sola
 * vez · 15 dic» ([proximo], ISO: el vencimiento vigente). `null` en una mensual (no dice nada nuevo). Sin
 * fecha legible, solo la cadencia. Pura.
 */
fun cadenciaDelPago(rule: RecurringRule, proximo: String?): String? {
    val frecuencia = rule.frecuenciaEfectiva
    if (frecuencia == FrecuenciaDelPago.MENSUAL) return null
    val nombre = nombreDeLaFrecuencia(frecuencia)
    val fecha = proximo?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: return nombre
    return if (frecuencia == FrecuenciaDelPago.UNA_VEZ) "$nombre · ${fechaCortaDeLaFrecuencia(fecha)}"
    else "$nombre · el próximo, ${fechaCortaDeLaFrecuencia(fecha)}"
}

/**
 * **El problema de la frecuencia de un alta o una edición**, o `null` si se puede guardar (la hoja y el
 * server, la misma regla):
 *  - mensual o sin frecuencia: nada que validar;
 *  - un abono a una deuda: siempre mensual;
 *  - otra: necesita el ancla, una fecha legible ([primerVencimiento]).
 */
fun problemaDeLaFrecuencia(frecuencia: FrecuenciaDelPago?, primerVencimiento: String?, esAbono: Boolean): String? {
    if (frecuencia == null || frecuencia == FrecuenciaDelPago.MENSUAL) return null
    if (esAbono) return "Un abono a una deuda es de cada mes."
    val fecha = primerVencimiento?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (fecha == null) {
        return if (frecuencia == FrecuenciaDelPago.UNA_VEZ) "Falta la fecha del pago" else "Falta la fecha del próximo pago"
    }
    return null
}

/**
 * **¿Una suscripción en dólares puede tener esta frecuencia?** Solo cada mes o cada año: el modelo de
 * suscripciones ([PeriodicidadDeCobro]) no sabe de otra. La hoja lo dice en una línea y no deja guardar.
 */
fun frecuenciaSirveEnDolares(frecuencia: FrecuenciaDelPago): Boolean =
    frecuencia == FrecuenciaDelPago.MENSUAL || frecuencia == FrecuenciaDelPago.ANUAL

/** La línea de la hoja con dólares y otra frecuencia. */
const val EN_DOLARES_SOLO_MES_O_ANIO: String = "En dólares solo puede ser cada mes o cada año."
