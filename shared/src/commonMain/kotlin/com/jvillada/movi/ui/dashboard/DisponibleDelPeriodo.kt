package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.DashboardSummary
import com.jvillada.movi.ui.components.formatMoneyCompact
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

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
 * **Y después lo corrigió** (ver `PlataDelPeriodo.kt` en `:core`): vio «te pasaste por $11M» con
 * plata en su cuenta principal, porque los gastos grandes se pagaron con un préstamo y con un
 * ahorro, y lo que tenía el primer día no contaba. Decidió que **lo que tenías al empezar el
 * período cuenta** y que **la plata que entra de un préstamo o de un ahorro cuenta**. Cuando el
 * server manda esas cifras ([PlataDelDisponible]):
 *
 * - **Disponible** = lo que tenías en «Tu plata» al empezar + lo que entró (ingresos en Tu plata,
 *   traspasos desde un préstamo o un ahorro, lo que un ahorro pagó directo) − lo que guardaste en
 *   un ahorro de afuera + los ingresos del checklist que faltan − fijos − otros pagos de deuda
 *   (cuotas que ningún ítem del checklist reclama y pagos de tarjeta que pagan deuda de antes del
 *   período: plata que sale de Tu plata sin que los fijos ni el gasto variable la cuenten).
 *
 * Así cierra la identidad: lo que queda (Disponible − gasto variable) = Tu plata hoy + ingresos por
 * recibir − fijos pendientes − compras con tarjeta sin pagar.
 *
 * Con un server anterior a esos campos se sigue usando «ingresos menos fijos».
 *
 * Todo es puro: el «hoy» y los bordes del período entran por parámetro.
 */

/**
 * Lo que el server manda de «Tu plata» para el Disponible (ver `DashboardSummary`): lo que había
 * al empezar el período, lo que entró y lo que se guardó afuera. Todo en pesos.
 *
 * `@Serializable` porque viaja dentro de la instantánea del Inicio (ver `InstantaneaDelInicio`).
 */
@Serializable
data class PlataDelDisponible(
    val saldoAlInicio: Long,
    val entradas: Long,
    val guardado: Long,
    /**
     * Lo que salió de Tu plata a una deuda sin que los fijos ni el gasto variable lo cuenten: una
     * cuota que ningún ítem del checklist reclama, o un pago de tarjeta que paga deuda de antes del
     * período. Ver `pagosDeDeudaFueraDelChecklist` en `:core`. Cero con un server que no lo manda.
     */
    val otrosPagosDeDeuda: Long = 0L,
) {
    /** Lo que hubo para el período antes de los fijos y de lo que falta por recibir. */
    val neto: Long get() = saldoAlInicio + entradas - guardado - otrosPagosDeDeuda
}

/** [PlataDelDisponible] de la respuesta del server, o `null` si el server es anterior a los campos. */
fun plataDelDisponibleDe(summary: DashboardSummary): PlataDelDisponible? {
    val saldo = summary.saldoTuPlataAlInicio ?: return null
    val entradas = summary.entradasDelPeriodo ?: return null
    return PlataDelDisponible(
        saldoAlInicio = saldo,
        entradas = entradas,
        guardado = summary.guardadoDelPeriodo ?: 0L,
        otrosPagosDeDeuda = summary.pagosDeDeudaFueraDelChecklist ?: 0L,
    )
}

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
    /** El día del mes en que empezó el período: el «25» de «Tenías $X el 25». */
    val diaDeInicio: Int = 1,
    /**
     * Lo que tenías al empezar y lo que entró, si el server lo manda. `null` = server viejo: el
     * disponible vuelve a ser «ingresos menos fijos».
     */
    val plata: PlataDelDisponible? = null,
) {
    val ingresos: Long get() = ingresosRecibidos + ingresosPorRecibir

    /** Lo que hubo para el período antes de los fijos. */
    val recursos: Long get() =
        if (plata == null) ingresos
        else plata.neto + ingresosPorRecibir

    val disponible: Long get() = recursos - fijos
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
 * La tarjeta entera, o `null` si no hay nada honesto que decir: sin ingresos ni plata (un usuario
 * que recién empieza, o que todavía no anotó su sueldo ni sus cuentas) el disponible no significa
 * nada, y un «disponible −$1.850.000» a quien solo anotó el arriendo lo asustaría sin razón.
 *
 * @param ingresosRecibidos la cifra «Ingresos» del Inicio: lo que ya entró en el período, con la
 *   regla de siempre (sin traspasos, sin «Por confirmar», sin anulados).
 * @param gastoVariablePorDia `"YYYY-MM-DD"` → pesos, como lo manda el server.
 * @param inicio primer día del período.
 * @param finExclusivo primer día del período siguiente.
 * @param hoy la fecha de hoy en Bogotá. Fuera del período devuelve `null`: la tarjeta habla del
 *   período en curso y nada más.
 * @param plata lo que tenías al empezar y lo que entró (ver [PlataDelDisponible]); `null` con un
 *   server viejo, y entonces el disponible es «ingresos menos fijos».
 */
fun disponibleDelPeriodo(
    ingresosRecibidos: Long,
    checklist: List<PagoDelPeriodo>,
    gastoVariablePorDia: Map<String, Long>,
    inicio: LocalDate,
    finExclusivo: LocalDate,
    hoy: LocalDate,
    plata: PlataDelDisponible? = null,
): DisponibleDelPeriodo? {
    val diasDelPeriodo = inicio.daysUntil(finExclusivo)
    if (diasDelPeriodo <= 0 || hoy < inicio || hoy >= finExclusivo) return null

    val porRecibir = ingresosPorRecibirDelPeriodo(checklist)
    val recibidos = ingresosRecibidos.coerceAtLeast(0L)
    val recursos = if (plata == null) recibidos + porRecibir
    else plata.neto + porRecibir
    if (recursos <= 0L) return null
    val fijos = fijosDelPeriodo(checklist)
    val disponible = recursos - fijos

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
        diaDeInicio = inicio.dayOfMonth,
        plata = plata,
    )
}

// ── Lo que dice cada fila ────────────────────────────────────────────────────
//
// Puro y aparte de la pantalla para que las pruebas lean las frases exactas. Todo en tuteo.

/**
 * **De dónde sale el disponible**, en una o dos líneas que caben a 390 px.
 *
 * Con lo que manda el server: «Tenías $X el 25 · entraron $Y» y abajo los fijos, precedidos de lo
 * que falta por recibir y lo que guardaste cuando los hay, y seguidos de los otros pagos de deuda
 * («Por recibir $P · guardaste $W · fijos $Z · otros pagos de deuda $D»). Con un server viejo, la
 * línea de siempre: «Ingresos $A menos fijos $B».
 */
internal fun desgloseDelDisponible(d: DisponibleDelPeriodo): List<String> {
    val plata = d.plata ?: return listOf("Ingresos ${formatMoneyCompact(d.ingresos)} menos fijos ${formatMoneyCompact(d.fijos)}")
    val tenias = "Tenías ${formatMoneyCompact(plata.saldoAlInicio)} el ${d.diaDeInicio}"
    val primera = "$tenias · entraron ${formatMoneyCompact(plata.entradas)}"
    val segunda = listOfNotNull(
        if (d.ingresosPorRecibir > 0L) "por recibir ${formatMoneyCompact(d.ingresosPorRecibir)}" else null,
        if (plata.guardado > 0L) "guardaste ${formatMoneyCompact(plata.guardado)}" else null,
        "fijos ${formatMoneyCompact(d.fijos)}",
        if (plata.otrosPagosDeDeuda > 0L) "otros pagos de deuda ${formatMoneyCompact(plata.otrosPagosDeDeuda)}" else null,
    ).joinToString(" · ").replaceFirstChar { it.uppercase() }
    return listOf(primera, segunda)
}

/** La frase cuando no hay margen: los fijos se llevan todo, o más de todo. */
internal fun sinMargen(d: DisponibleDelPeriodo): String {
    if (d.plata == null) {
        return if (d.disponible < 0L) "Los fijos del período superan tus ingresos por ${formatMoneyCompact(-d.disponible)}"
        else "Los fijos del período se llevan todos tus ingresos"
    }
    // Con otros pagos de deuda, los fijos solos pueden no ser los que se llevan todo: se nombran los dos.
    val quienes = if (d.plata.otrosPagosDeDeuda > 0L) "Los fijos y los otros pagos de deuda" else "Los fijos del período"
    return if (d.disponible < 0L) "$quienes superan lo que tenías y lo que entró por ${formatMoneyCompact(-d.disponible)}"
    else "$quienes se llevan todo lo que tenías y lo que entró"
}

/**
 * El rótulo corto de la columna de la semana: «Esta semana», o con sus días cuando la semana se
 * recorta en un borde del período («Esta semana · 4 días»). Sin la aclaración, una meta de la semana
 * más chica que la de siempre parecería un error.
 */
internal fun rotuloDeLaSemana(d: DisponibleDelPeriodo): String =
    if (d.semanaCorta) "Semana · ${d.diasDeLaSemana} ${if (d.diasDeLaSemana == 1) "día" else "días"}"
    else "Semana"

// La frase de cómo viene la tarjeta ya no se arma fila por fila: cada fila decía la suya y con el
// período pasado convivían «Te pasaste por $563.456» y «Vas bien» a un renglón de distancia. Ahora
// hay UNA frase por tarjeta y manda la peor ventana — ver `fraseDelDisponible` en
// `InicioDeUnVistazo.kt`.
