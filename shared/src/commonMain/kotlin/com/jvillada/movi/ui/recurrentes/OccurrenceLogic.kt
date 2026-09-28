package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoney

/**
 * Las reglas puras de «¿esto ya ocurrió?» en la pantalla de Recurrentes — sin Compose, para poder
 * testearlas.
 *
 * El server hace el trabajo pesado (qué periodo está en juego, qué movimientos podrían ser la
 * ocurrencia y en qué orden: ver `OccurrenceMatching.kt`). Acá vive lo que la pantalla necesita
 * decidir: **qué se muestra, con qué palabras, y cuál de las propuestas va arriba** después de
 * que el dueño dijo «no fue este».
 */

/**
 * La clave de un «no fue este»: **la regla Y el movimiento**, nunca el movimiento solo.
 *
 * El conjunto estaba indexado por id de evento, y eso hacía que rechazar una propuesta en una
 * regla se la quitara a todas. Con «Agua», «Gas» e «Internet» —las tres en «Servicios»— el mismo
 * pago del gas era la primera propuesta de las tres; decir «no fue este» en Agua (correcto, no era
 * el agua) le borraba a **Gas** su candidato bueno, y Gas pasaba a proponer el pago de la energía.
 * O sea: la única salida para rechazar una propuesta equivocada rompía la propuesta correcta de
 * otra regla — justo el mecanismo del que depende que las categorías compartidas sean tolerables.
 */
fun claveDescartada(ruleId: String, eventId: String): String = "$ruleId|$eventId"

/**
 * Los meses en palabras. La app nunca muestra `"2026-08"` ni `"08"`: eso es una clave, no algo que
 * alguien quiera leer (mismo criterio que los encabezados de Movimientos).
 */
private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/**
 * El nombre del mes de un periodo `"YYYY-MM"`, o cadena vacía si viene con una forma que no
 * entendemos — nunca un número crudo ni un `"?"`.
 */
fun nombreDelMes(period: String): String {
    // Sirve igual para un periodo `"2026-09"` y para una fecha ISO `"2026-09-01"`: los dos textos
    // que la pantalla tiene a mano (el periodo de la ocurrencia y el `dueDate` de un vencimiento)
    // llevan el mes en el mismo lugar, y no vale la pena dos funciones para eso.
    val mes = period.take(7).substringAfter('-', "").toIntOrNull() ?: return ""
    return MESES.getOrElse(mes - 1) { "" }
}

/**
 * El encabezado de la propuesta, en el idioma del hecho **y diciendo de qué mes habla**.
 *
 * Un sueldo **llega**, un arriendo **se paga**: decirle «¿ya lo pagaste?» a su nómina sería la
 * clase de detalle que hace sentir que la app no entiende lo que uno anotó.
 *
 * Y el mes no es decorativo. Sin él, la pregunta («¿Ya lo pagaste?»), el renglón de arriba
 * («Vence el 1 · en 5 días») y la propuesta («Movimiento del 25») no decían ninguno de qué mes
 * hablaban — y cuando la app se equivocaba de mes, no quedaba un solo texto en pantalla donde el
 * dueño pudiera notarlo. El bug se arregló en el server; el texto se arregla igual, porque un
 * texto que solo es cierto mientras el cálculo no falle es un texto que no sirve para revisar.
 */
fun tituloPropuesta(tipo: TransactionType, period: String): String {
    val mes = nombreDelMes(period)
    val deMes = if (mes.isEmpty()) "" else " el de $mes"
    return if (tipo == TransactionType.INCOME) "¿Ya te llegó$deMes?" else "¿Ya pagaste$deMes?"
}

/**
 * **De dónde sale un «ya ocurrió»**, en media frase y sin el «cuándo» adelante.
 *
 * Es la línea de evidencia de cada fila de «Ya pagaste» en la lista del período, y la del detalle de
 * un período pasado: las dos tienen que decir exactamente lo mismo, porque acá el texto **es** la
 * diferencia entre cuatro certezas distintas. (Vivía también en «Ya ocurrieron», que armaba «Ya
 * ocurrió en septiembre · …»; esa sección se fue en la ola «una sola lista», con su mes adelante.)
 *
 * «Con un movimiento» lleva el monto cuando se sabe: el detalle de un período lo manda para lo que
 * el dueño confirmó, y una fila que dice «con un movimiento» sin decir de cuánto no deja notar un
 * abono parcial.
 *
 * El último caso es el único sin movimiento detrás, y lo dice: un sello viejo hecho a mano. La app
 * ya no puede crear ninguno (ver [com.jvillada.movi.ui.dashboard.EstadoDeLaFila]), pero en la base
 * del dueño hay varios, y una fila tildada que no explique por qué está tildada es exactamente lo
 * que esta ola vino a terminar.
 */
internal fun origenDeLoOcurrido(
    automatica: Boolean,
    derivada: Boolean,
    hayMovimiento: Boolean,
    monto: Long?,
    moneda: String?,
): String = when {
    // Lo automático va ANTES que lo derivado, porque toda automática es además derivada (ver
    // el KDoc de `OccurrenceState.automatica`) y con el orden al revés nunca se leería.
    automatica && monto != null ->
        "Movi lo emparejó con un movimiento de ${formatMoney(monto, moneda ?: "COP")}"
    automatica -> "lo emparejó Movi"
    derivada && monto != null -> "lo prueba un pago de ${formatMoney(monto, moneda ?: "COP")}"
    derivada -> "lo prueba un movimiento"
    hayMovimiento && monto != null -> "con un movimiento de ${formatMoney(monto, moneda ?: "COP")}"
    hayMovimiento -> "con un movimiento"
    else -> "marcado a mano, sin movimiento"
}

/**
 * Una línea que describa la propuesta sin obligar a abrirla: el día **con su mes** y **qué fue** —
 * la nota que escribió el dueño o, si no escribió ninguna, el comercio que dijo el banco. El monto
 * se pinta aparte, con su formato de plata.
 *
 * ## Por qué NO cae a la categoría
 *
 * Caía, y era peor que no decir nada. Con «Agua», «Gas» e «Internet» todas en «Servicios» y las
 * notas vacías —lo normal en algo importado de un extracto— las tres tarjetas quedaban idénticas:
 *
 * ```
 * Agua       ¿Ya pagaste el de agosto?   Movimiento del 24 de agosto · Servicios   $58.000
 * Gas        ¿Ya pagaste el de agosto?   Movimiento del 24 de agosto · Servicios   $58.000
 * Internet   ¿Ya pagaste el de agosto?   Movimiento del 24 de agosto · Servicios   $58.000
 * ```
 *
 * Un solo movimiento ofrecido como respuesta a tres deudas impagas distintas, sin nada en pantalla
 * que las separe. La categoría es exactamente lo que todos los candidatos comparten —por eso son
 * candidatos—, así que como texto identificador vale cero y encima *aparenta* identificar.
 *
 * El comercio sí distingue, y el dato estaba ahí sin usarse: `occurrenceCandidatesFor` ya empareja
 * contra `merchant`. Cuando no hay ni nota ni comercio, la línea se queda en el día y el monto —
 * poco, pero honesto: no finge saber qué fue.
 *
 * El mes va siempre, aunque sea el mismo del vencimiento: «Movimiento del 25» a secas era
 * indistinguible entre el 25 de este mes y el del anterior, que es precisamente lo que había que
 * poder distinguir.
 */
fun descripcionPropuesta(event: FinancialEvent): String {
    val fecha = epochMillisToAppDate(event.timestamp)
    val mes = MESES.getOrElse(fecha.monthNumber - 1) { "" }
    val cuando = if (mes.isEmpty()) "Movimiento del ${fecha.dayOfMonth}" else "Movimiento del ${fecha.dayOfMonth} de $mes"
    // La nota del dueño primero; si no escribió ninguna, el comercio que dijo el banco. **Nunca la
    // categoría**: es justo la palabra que todos los candidatos comparten —por eso son candidatos—
    // así que ponerla ahí no distingue nada y encima *parece* que identifica.
    val que = event.description.trim().ifEmpty { event.merchant.orEmpty().trim() }
    return if (que.isEmpty()) cuando else "$cuando · $que"
}

/**
 * ¿El monto de la propuesta difiere del esperado? La pantalla lo dice en vez de disimularlo.
 *
 * Nace de una aclaración del dueño: «hay meses que mi salario es tal cual lo escribí en la base de
 * datos pero otros meses puede ser menos o más dependiendo de retenciones y cosas similares». El
 * emparejamiento **no** exige que el monto coincida, justamente por eso. Pero entonces confirmar
 * a ciegas podría sellar el mes con un movimiento de otra cosa, así que la diferencia se muestra:
 * es la información que hace que el «sí, fue este» sea una decisión y no un reflejo.
 */
fun difiereDelEsperado(esperado: Long, real: Long): Boolean = esperado != real

/**
 * ¿Vale la pena avisar que el monto no coincide? **Con una tarjeta, nunca.**
 *
 * [difiereDelEsperado] compara contra `rule.amount`, y en una regla sintética de tarjeta ese
 * número no es un pago esperado: es el SALDO de la deuda (ver [RecurringRule.montoEsSaldo]). El
 * pago real de una tarjeta —el mínimo, el total, o algo en el medio— casi nunca es igual al
 * saldo, así que la comparación daba `true` todos los meses y el dueño leía «No es el monto que
 * anotaste ($27.501.150). Puede ser: revísalo antes de confirmar.» sobre un pago perfectamente
 * normal. Peor: la advertencia le repetía como «lo esperado» justamente la cifra que esta rama
 * dejó de mostrar como su pago.
 *
 * No hay un monto esperado contra el cual comparar, así que no se afirma nada. Es la misma regla
 * que el resto de esta pantalla desde que el «Flujo libre» mintió dos veces: sin el dato, no se
 * dice.
 */
fun avisaMontoDistinto(esperado: Long, montoEsSaldo: Boolean, real: Long, monedaReal: String = "COP"): Boolean =
    // Lo esperado es en pesos. Comparar 20 dólares contra $80.000 daba «no es el monto que
    // anotaste» sobre un pago en dólares normal — mismo criterio que la tarjeta: sin un esperado
    // comparable, no se afirma nada.
    monedaReal == "COP" && !montoEsSaldo && difiereDelEsperado(esperado, real)

/**
 * El aviso de que la propuesta no vale lo que el dueño anotó, **ya escrito**, o `null` si no hay
 * nada que advertir.
 *
 * Es [avisaMontoDistinto] con su texto pegado: la decisión (cuándo advertir) y la redacción (qué
 * decir) viajan juntas a propósito. Vivía en «Próximos»; desde la ola «una sola lista» lo dice la
 * propuesta de la fila del período, que es donde quedó la pregunta.
 *
 * @param esperado el monto de la regla; [montoEsSaldo] si ese monto es el saldo de una deuda.
 */
fun avisoDeMontoDistinto(esperado: Long, montoEsSaldo: Boolean, event: FinancialEvent): String? =
    if (!avisaMontoDistinto(esperado, montoEsSaldo, event.amount, event.currency)) null
    else "No es el monto que anotaste (${formatCOP(esperado)}). Puede ser: revísalo antes de confirmar."

/**
 * **A dónde lleva «Anotar este pago»**, con todo lo que el recurrente ya sabe puesto.
 *
 * Es la salida que el dueño eligió para la fila sin ninguna evidencia —pagó en efectivo, pagó desde
 * una cuenta que Movi no lleva, o el banco nunca avisó—: en vez de dejarlo tildar sin movimiento,
 * la app lo lleva a **crear el movimiento que falta**. Al guardarlo, el emparejamiento automático
 * del server lo reconoce en la siguiente lectura y la fila se tilda sola; por eso los datos van
 * prellenados y no es comodidad: la categoría y la cuenta son justo lo que `esConcluyente` compara.
 *
 * ## La cuota de un crédito y el pago de una tarjeta van a Créditos
 *
 * Esas reglas son SINTÉTICAS: no se sellan, se derivan del movimiento que bajó la deuda (ver
 * `OccurrenceState.derivadaDeUnMovimiento`). Un gasto suelto anotado en la hoja de Agregar **no**
 * baja ninguna deuda, así que la fila se quedaría sin tildar igual y encima habría quedado un gasto
 * duplicado. El movimiento que esa fila necesita se registra en Créditos, y ahí es donde lleva —
 * mismo criterio que el toque sobre la fila (ver `etiquetaDeAbrir`).
 *
 * @param venceIso la fecha del vencimiento, que es la que hace que el movimiento caiga en el
 *   período correcto. Vacía o ilegible se deja pasar tal cual: la hoja se cae a hoy sola.
 */
fun hojaParaAnotar(
    ruleId: String,
    nombre: String,
    monto: Long,
    montoEsSaldo: Boolean,
    categoria: String,
    cuentaId: String?,
    venceIso: String,
    esIngreso: Boolean,
): Screen {
    if (ruleId.startsWith(CREDIT_RULE_PREFIX) || ruleId.startsWith(CARD_RULE_PREFIX)) {
        return Screen.Credits
    }
    return Screen.QuickAdd(
        presetAccountId = cuentaId,
        presetNota = nombre,
        // El «monto» de una tarjeta es su SALDO, no lo que se va a pagar (ver
        // `RecurringRule.montoEsSaldo`): prellenarlo sería escribirle $27.501.150 en la caja del
        // monto a alguien que va a pagar el mínimo. Sin dato, campo vacío.
        presetMonto = monto.takeIf { !montoEsSaldo && it > 0 },
        presetCategoria = categoria,
        presetFecha = venceIso,
        presetEsIngreso = esIngreso,
    )
}

/** [hojaParaAnotar] desde una fila del checklist, que ya trae los ocho datos. */
fun hojaParaAnotar(pago: PagoDelPeriodo): Screen = hojaParaAnotar(
    ruleId = pago.ruleId,
    nombre = pago.nombre,
    monto = pago.monto,
    montoEsSaldo = pago.montoEsSaldo,
    categoria = pago.categoria,
    cuentaId = pago.cuentaId,
    venceIso = pago.vence,
    esIngreso = pago.esIngreso,
)
