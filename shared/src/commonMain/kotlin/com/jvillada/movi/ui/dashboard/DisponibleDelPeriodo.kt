package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.ui.components.formatMoneyCompact
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * # «Disponible»: cuánto queda para gastar en el período, esta semana y hoy
 *
 * El dueño: *«En inicio sería genial algo tipo: Disponible en el periodo · Disponible por semana ·
 * Disponible por día. Que puedes ir viendo cómo van tus movimientos respecto a ese disponible en
 * cada uno de estos universos temporales»*. De las definiciones posibles eligió **«ingresos menos
 * fijos»**:
 *
 * - **Ingresos** = lo que ya entró en el período (la misma cifra «Ingresos» del Inicio) más los
 *   ingresos del checklist que todavía no llegan.
 * - **Fijos** = todo lo que el checklist del período pide pagar, pagado o no — las mismas filas de
 *   «Falta por pagar», sin una segunda cuenta.
 * - **Disponible** = ingresos − fijos.
 *
 * Contra eso se mide el **gasto variable** (ver `gastoVariablePorDia` en `:core`): lo que cuenta en
 * «Gastos» menos los pagos del checklist, que ya están restados como fijos.
 *
 * Después lo precisó: *«dividir el disponible en metas por unidades de tiempo y con eso irme
 * organizando. El del periodo debe ir sumando todo. El de la semana debe ir sumando el movimiento
 * de la semana. El del día debe ir sumando el movimiento del día»*. Así que cada ventana tiene una
 * meta fija (ver [DisponibleDelPeriodo]) y una frase de cómo viene contra ella.
 *
 * Todo es puro: el «hoy» y los bordes del período entran por parámetro.
 */

/**
 * Cómo va una ventana contra su meta. Decide el color de la barra y de la frase «cómo viene»:
 * normal, ámbar cuando va por encima del ritmo o pasa del 85 % de la meta, rojo al pasarse.
 */
enum class NivelDelGasto { BIEN, CERCA, PASADO }

/** Desde qué parte de la meta gastada la barra avisa: el 85 %. */
private const val PORCENTAJE_DE_AVISO = 85L

/**
 * Una de las tres ventanas —el período, esta semana, hoy—: lo gastado en ella y su meta.
 *
 * @param meta lo que se puede gastar en la ventana entera. Es fija: sale del disponible y de los
 *   días, no de lo gastado. Cero cuando no hay disponible que dividir (ver [fraccion]).
 * @param esperadoAHoy lo que, al ritmo de la meta, se esperaba llevar gastado al cerrar hoy. En
 *   «hoy» es la meta misma: el día entero ya es «a hoy».
 */
data class VentanaDelDisponible(val gastado: Long, val meta: Long, val esperadoAHoy: Long) {
    /** Lo que sobra de la meta; negativo = se pasó. */
    val teQuedan: Long get() = meta - gastado

    /** Cuánto va por encima (positivo) o por debajo (negativo) del ritmo a hoy. */
    val contraElRitmo: Long get() = gastado - esperadoAHoy

    /**
     * El largo de la barra, de 0 a 1, o `null` cuando no hay meta contra la cual medir.
     * Nunca divide por cero ni da un largo negativo.
     */
    val fraccion: Float? get() =
        if (meta <= 0L) null else (gastado.toDouble() / meta).toFloat().coerceIn(0f, 1f)

    val nivel: NivelDelGasto get() = when {
        meta <= 0L -> if (gastado > 0L) NivelDelGasto.PASADO else NivelDelGasto.BIEN
        gastado > meta -> NivelDelGasto.PASADO
        gastado > esperadoAHoy -> NivelDelGasto.CERCA
        gastado * 100 >= meta * PORCENTAJE_DE_AVISO -> NivelDelGasto.CERCA
        else -> NivelDelGasto.BIEN
    }
}

/**
 * Todo lo que pinta la tarjeta «Disponible».
 *
 * **Las metas son fijas para todo el período** (el dueño: *«dividir el disponible en metas por
 * unidades de tiempo y con eso irme organizando»*): la del período es el disponible, la diaria es
 * el disponible entre los días del período y la semanal es la diaria por 7. No se recalculan con
 * lo gastado: un período pasado sigue mostrando la meta de la semana y la de hoy, porque son la
 * vara con la que se organiza el resto del mes.
 */
data class DisponibleDelPeriodo(
    val ingresosRecibidos: Long,
    val ingresosPorRecibir: Long,
    val fijos: Long,
    /** Días del período, contando el primero y el último. */
    val diasDelPeriodo: Int,
    /** Días que quedan, **contando hoy**. En el último día del período vale 1. */
    val diasQueQuedan: Int,
    /** Días de esta semana (lunes a domingo) que caen dentro del período. */
    val diasDeLaSemana: Int,
    /** Días de [diasDeLaSemana] que ya pasaron, **contando hoy**. */
    val diasCorridosDeLaSemana: Int,
    val periodo: VentanaDelDisponible,
    val semana: VentanaDelDisponible,
    val hoy: VentanaDelDisponible,
) {
    val ingresos: Long get() = ingresosRecibidos + ingresosPorRecibir
    val disponible: Long get() = ingresos - fijos
    val hayMargen: Boolean get() = disponible > 0L

    /** La meta de un día cualquiera del período. Cero sin margen. */
    val metaPorDia: Long get() = if (hayMargen) disponible / diasDelPeriodo else 0L

    /** La meta de una semana entera: la diaria por 7. La de esta semana puede ser corta. */
    val metaPorSemana: Long get() = metaPorDia * 7

    /** Esta semana tiene menos de 7 días en el período: empezó en el anterior o sigue en el próximo. */
    val semanaCorta: Boolean get() = diasDeLaSemana < 7

    /**
     * Lo que falta gastar del disponible, repartido entre los días que quedan (hoy incluido).
     * `null` cuando ya no queda nada que repartir.
     */
    val porDiaParaLoQueQueda: Long? get() {
        val resto = disponible - periodo.gastado
        if (resto <= 0L || diasQueQuedan <= 0) return null
        return resto / diasQueQuedan
    }
}

/**
 * **Los fijos del período**: cada pago del checklist, pagado o pendiente, por lo que de verdad
 * salió si se sabe ([PagoDelPeriodo.montoPagado]) y si no por lo esperado.
 *
 * Afuera: los ingresos (no se pagan), el SALDO de una tarjeta (es la deuda entera, no lo de este
 * mes — el mismo criterio que [faltaPorPagar]) y lo que no está en pesos, porque el resto de la
 * cuenta está en pesos. Una compra con tarjeta ya entra como gasto variable el día que se hace.
 */
fun fijosDelPeriodo(checklist: List<PagoDelPeriodo>): Long =
    checklist
        .filter { !it.esIngreso && !it.montoEsSaldo && it.moneda == "COP" }
        .sumOf { if (it.pagado) it.montoPagado ?: it.monto else it.monto }

/** Los ingresos del checklist que todavía no llegan: el sueldo que falta, un arriendo que se cobra. */
fun ingresosPorRecibirDelPeriodo(checklist: List<PagoDelPeriodo>): Long =
    checklist
        .filter { it.esIngreso && !it.pagado && !it.montoEsSaldo && it.moneda == "COP" }
        .sumOf { it.monto }

/**
 * La tarjeta entera, o `null` si no hay nada honesto que decir: sin ingresos (un usuario que recién
 * empieza, o que todavía no anotó su sueldo) «ingresos menos fijos» no significa nada, y un
 * «disponible −$1.850.000» a quien solo anotó el arriendo lo asustaría sin razón.
 *
 * @param ingresosRecibidos la cifra «Ingresos» del Inicio: lo que ya entró en el período, con la
 *   regla de siempre (sin traspasos, sin «Por confirmar», sin anulados).
 * @param gastoVariablePorDia `"YYYY-MM-DD"` → pesos, como lo manda el server.
 * @param inicio primer día del período.
 * @param finExclusivo primer día del período siguiente.
 * @param hoy la fecha de hoy en Bogotá. Fuera del período devuelve `null`: la tarjeta habla del
 *   período en curso y nada más.
 */
fun disponibleDelPeriodo(
    ingresosRecibidos: Long,
    checklist: List<PagoDelPeriodo>,
    gastoVariablePorDia: Map<String, Long>,
    inicio: LocalDate,
    finExclusivo: LocalDate,
    hoy: LocalDate,
): DisponibleDelPeriodo? {
    val diasDelPeriodo = inicio.daysUntil(finExclusivo)
    if (diasDelPeriodo <= 0 || hoy < inicio || hoy >= finExclusivo) return null

    val porRecibir = ingresosPorRecibirDelPeriodo(checklist)
    val recibidos = ingresosRecibidos.coerceAtLeast(0L)
    if (recibidos + porRecibir <= 0L) return null
    val fijos = fijosDelPeriodo(checklist)
    val disponible = recibidos + porRecibir - fijos

    val ultimoDia = finExclusivo.minus(1, DateTimeUnit.DAY)
    val gastoPorFecha: Map<LocalDate, Long> = gastoVariablePorDia.mapNotNull { (dia, monto) ->
        runCatching { LocalDate.parse(dia) }.getOrNull()?.let { it to monto }
    }.groupBy({ it.first }, { it.second }).mapValues { (_, montos) -> montos.sum() }
    fun gastadoEntre(desde: LocalDate, hasta: LocalDate): Long =
        gastoPorFecha.filterKeys { it in desde..hasta }.values.sum()

    // La semana va de lunes a domingo, recortada a los bordes del período: una semana que empezó
    // en el período anterior solo cuenta sus días de este, y su parte del disponible también.
    val lunes = hoy.minus(hoy.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber, DateTimeUnit.DAY)
    val domingo = lunes.plus(6, DateTimeUnit.DAY)
    val desdeSemana = maxOf(lunes, inicio)
    val hastaSemana = minOf(domingo, ultimoDia)
    val diasDeLaSemana = desdeSemana.daysUntil(hastaSemana) + 1

    val diasCorridosDeLaSemana = desdeSemana.daysUntil(hoy) + 1
    val diasCorridosDelPeriodo = inicio.daysUntil(hoy) + 1

    // Las metas. Sin margen no hay nada que dividir: van en cero y la tarjeta no dibuja barras.
    val metaDelPeriodo = disponible.coerceAtLeast(0L)
    val metaPorDia = metaDelPeriodo / diasDelPeriodo

    return DisponibleDelPeriodo(
        ingresosRecibidos = recibidos,
        ingresosPorRecibir = porRecibir,
        fijos = fijos,
        diasDelPeriodo = diasDelPeriodo,
        diasQueQuedan = hoy.daysUntil(finExclusivo),
        diasDeLaSemana = diasDeLaSemana,
        diasCorridosDeLaSemana = diasCorridosDeLaSemana,
        periodo = VentanaDelDisponible(
            gastado = gastadoEntre(inicio, ultimoDia),
            meta = metaDelPeriodo,
            esperadoAHoy = metaDelPeriodo * diasCorridosDelPeriodo / diasDelPeriodo,
        ),
        // Una semana corta (en un borde del período) tiene de meta la diaria por sus días, y su
        // ritmo se mide igual: la diaria por los días que ya corrieron.
        semana = VentanaDelDisponible(
            gastado = gastadoEntre(desdeSemana, hastaSemana),
            meta = metaPorDia * diasDeLaSemana,
            esperadoAHoy = metaPorDia * diasCorridosDeLaSemana,
        ),
        hoy = VentanaDelDisponible(gastado = gastadoEntre(hoy, hoy), meta = metaPorDia, esperadoAHoy = metaPorDia),
    )
}

// ── Lo que dice cada fila ────────────────────────────────────────────────────
//
// Puro y aparte de la pantalla para que las pruebas lean las frases exactas. Todo en tuteo.

/**
 * El rótulo de la fila del período, con los días que quedan dichos como en «Tu plata», que cuenta
 * los días DESPUÉS de hoy: con las dos tarjetas una encima de la otra, «quedan 2 días» arriba y
 * «quedan 3 días» abajo parecía un error. La cuenta por día sí incluye hoy (`diasQueQuedan`).
 */
internal fun rotuloDelPeriodo(d: DisponibleDelPeriodo): String {
    val dias = when (val despuesDeHoy = d.diasQueQuedan - 1) {
        0 -> "último día"
        1 -> "queda 1 día"
        else -> "quedan $despuesDeHoy días"
    }
    return "Este período · $dias"
}

/** «Esta semana», o con la aclaración cuando la semana se recorta en un borde del período. */
internal fun rotuloDeLaSemana(d: DisponibleDelPeriodo): String =
    if (d.semanaCorta) "Esta semana · semana corta: ${d.diasDeLaSemana} ${if (d.diasDeLaSemana == 1) "día" else "días"}"
    else "Esta semana"

/**
 * **Cómo viene el período**: lo gastado contra lo previsto a hoy (la meta por los días corridos,
 * hoy incluido, entre los días del período), y lo que queda repartido por día.
 */
internal fun comoVieneElPeriodo(d: DisponibleDelPeriodo): String {
    val v = d.periodo
    if (v.teQuedan < 0L) return "Te pasaste por ${formatMoneyCompact(-v.teQuedan)}"
    if (v.teQuedan == 0L) return "Ya usaste todo el disponible del período"
    // El último día lo previsto a hoy ES la meta: «vas $X por debajo» y «te quedan $X» serían la
    // misma cifra dicha dos veces.
    if (d.diasQueQuedan <= 1) return "Te quedan ${formatMoneyCompact(v.teQuedan)} para cerrar el período"
    val ritmo = when {
        v.contraElRitmo < 0L -> "Vas ${formatMoneyCompact(-v.contraElRitmo)} por debajo de lo previsto a hoy"
        v.contraElRitmo > 0L -> "Vas ${formatMoneyCompact(v.contraElRitmo)} por encima de lo previsto a hoy"
        else -> "Vas justo en lo previsto a hoy"
    }
    val porDia = d.porDiaParaLoQueQueda ?: 0L
    return "$ritmo · te quedan ${formatMoneyCompact(v.teQuedan)}, unos ${formatMoneyCompact(porDia)} por día"
}

/** **Cómo viene la semana**: lo mismo que el período, dentro de los días de esta semana. */
internal fun comoVieneLaSemana(d: DisponibleDelPeriodo): String {
    val v = d.semana
    return when {
        v.teQuedan < 0L -> "Te pasaste de la meta de la semana por ${formatMoneyCompact(-v.teQuedan)}"
        v.contraElRitmo > 0L -> "Vas ${formatMoneyCompact(v.contraElRitmo)} por encima del ritmo de la semana"
        // Dentro del ritmo pero ya cerca de la meta (los últimos días): sin el «vas bien».
        v.nivel == NivelDelGasto.CERCA -> "Te quedan ${formatMoneyCompact(v.teQuedan)} para esta semana"
        else -> "Vas bien: te quedan ${formatMoneyCompact(v.teQuedan)} para esta semana"
    }
}

/** **Cómo viene hoy**. */
internal fun comoVieneHoy(d: DisponibleDelPeriodo): String {
    val v = d.hoy
    return if (v.teQuedan < 0L) "Te pasaste de la meta de hoy por ${formatMoneyCompact(-v.teQuedan)}"
    else "Te quedan ${formatMoneyCompact(v.teQuedan)} para hoy"
}
