package com.jvillada.movi.shared.model

/*
 * # La cola de lo pendiente: una sola, para todo Hoy y Plan (vuelta de diseño 22)
 *
 * Un diseñador recorrió la app con la cuenta demo y encontró que se contradecía: el Arriendo de
 * $1,8M, vencido hace 22 días, no estaba en la campana —que sí mostraba Internet $3.000 y Carro
 * $9.000—; en Hoy salía dos veces («Movi propone hoy» y el widget «Falta por pagar») y otra vez en
 * Plan, y cada lugar lo ponía en un orden distinto.
 *
 * **Por qué el Arriendo no entraba a la campana.** La campana leía `/api/payments/upcoming` con su
 * `daysUntil`: el PRÓXIMO vencimiento de cada regla. Esa fecha **rueda** apenas pasan los días de
 * gracia sin marcar nada (`dueDateFor` en el server): a los 22 días el Arriendo ya figuraba como
 * «vence en 8 días» —el del período siguiente— y la ventana de 7 días de la campana lo dejaba
 * afuera. El checklist del período (`checklistDelPeriodo`) sí lo veía, porque manda la OCURRENCIA,
 * que no rueda. Ahora las tres superficies leen la misma lista, esta, y la armada sobre el
 * checklist.
 *
 * **No cambia qué es pendiente**: lo que falta pagar es lo de [pagosPendientes] (el checklist sin
 * lo pagado ni los ingresos), lo que el banco cobró solo es lo de `GET /api/debitos-automaticos`, y
 * lo que entró solo es lo que cuenta la bandeja «Por revisar». Esto solo los junta y los ordena.
 */

/**
 * Cuántos días hacia adelante entra a la cola un pago que todavía no vence: la semana, la ventana que
 * la campana usó siempre. Lo vencido entra siempre, sin importar hace cuánto.
 */
const val DIAS_POR_VENCER_EN_LA_COLA: Int = 7

/**
 * **Qué tan urgente es algo pendiente**, del más urgente al menos (el orden del `enum` ES el orden de
 * la cola):
 *
 * 1. un pago que ya venció;
 * 2. un pago que vence hoy;
 * 3. lo que el banco cobró solo y falta confirmar (la plata ya salió);
 * 4. lo que entró solo y espera en «Por revisar» (avisos del banco, movimientos, pagos de tarjeta);
 * 5. un pago que vence en los próximos [DIAS_POR_VENCER_EN_LA_COLA] días;
 * 6. un pago del período que vence más adelante (solo en las listas enteras: la cola no lo trae).
 */
enum class UrgenciaDelPendiente { VENCIDO, VENCE_HOY, DEBITO_POR_CONFIRMAR, POR_REVISAR, POR_VENCER, MAS_ADELANTE }

/** Una cosa de la cola. Cada superficie decide cómo se ve; la cola decide qué es y en qué orden va. */
sealed interface Pendiente {
    val urgencia: UrgenciaDelPendiente

    /** Para que una superficie diga «esto ya está arriba» sin comparar textos. */
    val clave: String

    /** La cifra por la que se ordena dentro de su urgencia, en pesos; `null` va al final. */
    val montoParaOrdenar: Long?

    /** Un pago del checklist del período, vencido o por vencer. */
    data class Pago(val pago: PagoDelPeriodo, override val urgencia: UrgenciaDelPendiente) : Pendiente {
        override val clave: String get() = clavePendienteDelPago(pago)
        override val montoParaOrdenar: Long? get() = montoParaOrdenar(pago)

        /** La cifra que se muestra: el mínimo de una tarjeta (su «monto» es el saldo), o el monto. */
        val cifra: Long? get() = cifraDelPago(pago)
    }

    /** Un cobro que el banco hizo solo y Movi propone confirmar. */
    data class Debito(val debito: DebitoAutomaticoPorConfirmar) : Pendiente {
        override val urgencia: UrgenciaDelPendiente get() = UrgenciaDelPendiente.DEBITO_POR_CONFIRMAR
        override val clave: String get() = "debito:${debito.clave}"
        override val montoParaOrdenar: Long? get() = debito.monto.takeIf { debito.moneda == "COP" }
    }

    /**
     * Lo que entró solo y espera en «Por revisar», contado UNA vez —avisos, movimientos y pagos de
     * tarjeta—: es una sola bandeja, así que es una sola entrada de la cola.
     */
    data class PorRevisar(val cuantos: Int) : Pendiente {
        override val urgencia: UrgenciaDelPendiente get() = UrgenciaDelPendiente.POR_REVISAR
        override val clave: String get() = CLAVE_POR_REVISAR
        override val montoParaOrdenar: Long? get() = null
    }
}

/** La clave de la entrada de «Por revisar». */
const val CLAVE_POR_REVISAR: String = "por-revisar"

/** La clave de un pago en la cola: su regla (un pago del período es uno por regla). */
fun clavePendienteDelPago(pago: PagoDelPeriodo): String = "pago:${pago.ruleId}"

/**
 * La cifra que se dice de un pago: una tarjeta no tiene cuota, su «monto» es el saldo, así que va su
 * mínimo si se sabe (y si no, ninguna). La misma regla que ya usaban «Movi propone hoy» y el widget.
 */
fun cifraDelPago(pago: PagoDelPeriodo): Long? =
    // Vuelta 72: en la fila del extracto, `pagoMinimo` ya es lo que le falta al mínimo.
    if (pago.montoEsSaldo) pago.faltaDelMinimo ?: pago.pagoMinimo else pago.monto

/**
 * Por cuánto se ordena un pago, en pesos. El mínimo de una tarjeta ya viene en pesos
 * (`pagoMinimoCop`); un monto en otra moneda no se compara con pesos y va al final de su grupo.
 */
private fun montoParaOrdenar(pago: PagoDelPeriodo): Long? = when {
    pago.montoEsSaldo -> pago.faltaDelMinimo ?: pago.pagoMinimo
    pago.moneda == "COP" -> pago.monto
    else -> null
}

/**
 * La urgencia de un pago pendiente, o `null` si no es un pendiente (ya se pagó o es un ingreso).
 * [diasPorVencer] separa lo de esta semana ([UrgenciaDelPendiente.POR_VENCER]) de lo que vence más
 * adelante en el período ([UrgenciaDelPendiente.MAS_ADELANTE]).
 */
fun urgenciaDelPago(pago: PagoDelPeriodo, diasPorVencer: Int = DIAS_POR_VENCER_EN_LA_COLA): UrgenciaDelPendiente? = when {
    // Vuelta 63: un abono planeado no vence ni apura: es un recordatorio que vive en Pagos.
    // Vuelta 72: una tarjeta pagada por debajo de su mínimo sigue en la cola (no está hecha).
    pago.hecho || pago.esIngreso || pago.esAbono -> null
    pago.diasParaVencer < 0 -> UrgenciaDelPendiente.VENCIDO
    pago.diasParaVencer == 0 -> UrgenciaDelPendiente.VENCE_HOY
    pago.diasParaVencer <= diasPorVencer -> UrgenciaDelPendiente.POR_VENCER
    else -> UrgenciaDelPendiente.MAS_ADELANTE
}

/**
 * **El orden de la cola**: primero la urgencia; dentro de cada grupo, **el monto más grande primero**
 * (lo que más pesa es lo que más importa no olvidar), y a igual monto lo que vence antes y después el
 * nombre, para que el orden no cambie entre recargas sin que nada haya cambiado.
 *
 * Lo que vence más adelante ([UrgenciaDelPendiente.MAS_ADELANTE]) es la excepción: ahí manda la fecha,
 * porque un pago de dentro de tres semanas no compite por la atención de hoy.
 */
val ORDEN_DE_LA_COLA: Comparator<Pendiente> = Comparator { a, b ->
    val porUrgencia = a.urgencia.compareTo(b.urgencia)
    if (porUrgencia != 0) return@Comparator porUrgencia
    val diasA = (a as? Pendiente.Pago)?.pago?.diasParaVencer
    val diasB = (b as? Pendiente.Pago)?.pago?.diasParaVencer
    val porMonto = compareValues(b.montoParaOrdenar ?: Long.MIN_VALUE, a.montoParaOrdenar ?: Long.MIN_VALUE)
    val porDias = compareValues(diasA ?: Int.MAX_VALUE, diasB ?: Int.MAX_VALUE)
    val primero = if (a.urgencia == UrgenciaDelPendiente.MAS_ADELANTE) porDias else porMonto
    val segundo = if (a.urgencia == UrgenciaDelPendiente.MAS_ADELANTE) porMonto else porDias
    when {
        primero != 0 -> primero
        segundo != 0 -> segundo
        else -> nombreParaOrdenar(a).compareTo(nombreParaOrdenar(b))
    }
}

private fun nombreParaOrdenar(p: Pendiente): String = when (p) {
    is Pendiente.Pago -> p.pago.nombre.lowercase()
    is Pendiente.Debito -> p.debito.nombre.lowercase()
    is Pendiente.PorRevisar -> ""
}

/**
 * **La cola de lo pendiente**, ordenada con [ORDEN_DE_LA_COLA]: los pagos vencidos y los que vencen en
 * los próximos [diasPorVencer] días (del checklist del período, nunca del vencimiento que rueda), lo
 * que el banco cobró solo, y lo que espera en «Por revisar» como una sola entrada.
 *
 * La consumen la campana, «Movi propone hoy» y el resumen de Plan; el widget «Falta por pagar» y la
 * lista de Plan ordenan sus pagos con [pagosEnOrdenDeUrgencia], la misma regla.
 *
 * @param checklist el checklist del período (`checklistDelPeriodo`); lo pagado y los ingresos no entran.
 * @param porRevisar cuántas cosas esperan en «Por revisar», sin los débitos (que van uno por uno).
 *
 * **Una cuota que el banco cobra sola entra una vez** (vuelta 49): vencida y sin movimiento, está en
 * el checklist (es un fijo pendiente) y además en [debitos] («¿Se cobró…?»). Son la misma cuota: en la
 * cola va la pregunta del débito, que es lo que se resuelve, y no además «Pago vencido».
 */
fun colaDePendientes(
    checklist: List<PagoDelPeriodo>,
    debitos: List<DebitoAutomaticoPorConfirmar> = emptyList(),
    porRevisar: Int = 0,
    diasPorVencer: Int = DIAS_POR_VENCER_EN_LA_COLA,
): List<Pendiente> = buildList<Pendiente> {
    val preguntadas = debitos.map { it.ruleId }.toSet()
    checklist.forEach { pago ->
        if (pago.ruleId in preguntadas) return@forEach
        val urgencia = urgenciaDelPago(pago, diasPorVencer)
        if (urgencia != null && urgencia != UrgenciaDelPendiente.MAS_ADELANTE) add(Pendiente.Pago(pago, urgencia))
    }
    debitos.forEach { add(Pendiente.Debito(it)) }
    if (porRevisar > 0) add(Pendiente.PorRevisar(porRevisar))
}.sortedWith(ORDEN_DE_LA_COLA)

/**
 * **Los pagos pendientes del período, en el orden de la cola** —vencidos, de hoy y de la semana por
 * monto; lo de más adelante por fecha—. Es el orden de la lista «Por pagar» de Plan y de las filas del
 * widget «Falta por pagar»: el mismo pago no puede ir primero en un lado y tercero en otro.
 */
fun pagosEnOrdenDeUrgencia(
    checklist: List<PagoDelPeriodo>,
    diasPorVencer: Int = DIAS_POR_VENCER_EN_LA_COLA,
): List<PagoDelPeriodo> =
    checklist.mapNotNull { pago -> urgenciaDelPago(pago, diasPorVencer)?.let { Pendiente.Pago(pago, it) } }
        .sortedWith(ORDEN_DE_LA_COLA)
        .map { it.pago }

// ── Los movimientos que entraron solos, contados como se ven ─────────────────

/**
 * Lo que hace falta de un movimiento por confirmar para contarlo como renglón: un traspaso son dos
 * movimientos (la salida y la entrada) y se ve como uno.
 */
data class PataPorConfirmar(val id: String, val transferId: String?, val tipo: TransactionType)

/**
 * **Cuántos renglones por confirmar hay**, con la regla de la bandeja: las dos patas de un traspaso
 * (una salida y una entrada con el mismo `transferId`) son un renglón; una pata suelta o cualquier otro
 * movimiento, uno cada uno. Es la cuenta de `renglonesQueEntraronSolos` en la app, y la que el server
 * manda en `DashboardSummary.movimientosPorConfirmar` para que Hoy diga el mismo número que
 * Movimientos sin bajarse la historia.
 */
fun renglonesPorConfirmar(patas: List<PataPorConfirmar>): Int {
    val traspasosEnteros = patas.filter { it.transferId != null }
        .groupBy { it.transferId!! }
        .filterValues { grupo -> grupo.any { it.tipo == TransactionType.EXPENSE } && grupo.any { it.tipo == TransactionType.INCOME } }
    // Cada traspaso entero es un renglón; todo lo demás, uno por movimiento.
    return traspasosEnteros.size + patas.count { it.transferId == null || it.transferId !in traspasosEnteros }
}
