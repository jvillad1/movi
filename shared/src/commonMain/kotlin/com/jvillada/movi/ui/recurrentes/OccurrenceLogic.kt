package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.time.epochMillisToAppDate

/**
 * Las reglas puras de «¿esto ya ocurrió?» en la pantalla de Recurrentes — sin Compose, para poder
 * testearlas.
 *
 * El server hace el trabajo pesado (qué periodo está en juego, qué movimientos podrían ser la
 * ocurrencia y en qué orden: ver `OccurrenceMatching.kt`). Acá vive lo que la pantalla necesita
 * decidir: **qué se muestra, con qué palabras, y cuál de las propuestas va arriba** después de
 * que el dueño dijo «no fue este».
 */

/** El estado del periodo en juego de [ruleId], si el server mandó uno. */
fun ocurrenciaDe(estados: List<OccurrenceState>, ruleId: String): OccurrenceState? =
    estados.firstOrNull { it.ruleId == ruleId }

/**
 * La propuesta que toca mostrar, o `null` si no queda ninguna.
 *
 * [descartadas] son las que el dueño ya rechazó con «no fue este» **en esta sesión de pantalla**.
 * No se persisten a propósito: rechazar una propuesta no es un hecho sobre su plata (a diferencia
 * de confirmarla, que sí lo es y por eso sí va a la base). Guardar cada «no» obligaría a una tabla
 * más para evitar un ruido que se va solo apenas confirma o apenas cambia el mes.
 */
fun propuestaActual(estado: OccurrenceState, descartadas: Set<String> = emptySet()): FinancialEvent? =
    estado.candidates.firstOrNull { it.id !in descartadas }

/**
 * ¿Hay algo que preguntar para este recurrente?
 *
 * Solo cuando el periodo está abierto. Si ya se cerró no se pregunta nada: lo que corresponde ahí
 * es poder deshacerlo, no volver a ofrecer.
 */
fun hayQuePreguntar(estado: OccurrenceState?): Boolean = estado != null && !estado.occurred

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
    val mes = period.substringAfter('-', "").toIntOrNull() ?: return ""
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

/** El cierre sin movimiento que emparejar: «lo pagué en efectivo», «me llegó y no lo anoté». */
fun etiquetaCierreManual(tipo: TransactionType): String =
    if (tipo == TransactionType.INCOME) "Ya me llegó" else "Ya lo pagué"

/**
 * Cómo se lee una fila ya cerrada.
 *
 * **Dice el mes, no «este mes».** El texto anterior era «Ya ocurrió este mes», que en el mejor de
 * los casos repetía lo obvio y en el peor mentía: cuando la app cerraba el periodo equivocado, ese
 * renglón era el único rastro que quedaba del error y estaba escrito de la única forma que lo
 * volvía indetectable. El cálculo ya no puede equivocarse de mes, y el texto tampoco lo tapa.
 *
 * Se distingue además si quedó **respaldada por un movimiento** o si el dueño la cerró a mano: son
 * dos grados de certeza distintos y la fila no debería sonar igual en los dos casos. Un sello a
 * mano es una palabra suya; uno con movimiento está anclado a una plata que se puede ver.
 */
fun textoYaOcurrio(estado: OccurrenceState): String {
    val mes = nombreDelMes(estado.period)
    val cuando = if (mes.isEmpty()) "Ya ocurrió" else "Ya ocurrió en $mes"
    return if (estado.eventId != null) "$cuando · con un movimiento" else cuando
}

/**
 * Una línea que describa la propuesta sin obligar a abrirla: el día **con su mes**, la nota (o la
 * categoría, que es lo que la app guarda como descripción cuando la nota va vacía) y nada más. El
 * monto se pinta aparte, con su formato de plata.
 *
 * El mes va siempre, aunque sea el mismo del vencimiento: «Movimiento del 25» a secas era
 * indistinguible entre el 25 de este mes y el del anterior, que es precisamente lo que había que
 * poder distinguir.
 */
fun descripcionPropuesta(event: FinancialEvent): String {
    val fecha = epochMillisToAppDate(event.timestamp)
    val mes = MESES.getOrElse(fecha.monthNumber - 1) { "" }
    val cuando = if (mes.isEmpty()) "Movimiento del ${fecha.dayOfMonth}" else "Movimiento del ${fecha.dayOfMonth} de $mes"
    val que = event.description.trim().ifEmpty { event.category.trim() }
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
