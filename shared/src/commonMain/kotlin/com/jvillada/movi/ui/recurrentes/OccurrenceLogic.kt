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
 * El encabezado de la propuesta, en el idioma del hecho: un sueldo **llega**, un arriendo **se
 * paga**. Decirle «¿ya lo pagaste?» a su nómina sería la clase de detalle que hace sentir que la
 * app no entiende lo que uno anotó.
 */
fun tituloPropuesta(tipo: TransactionType): String =
    if (tipo == TransactionType.INCOME) "¿Ya te llegó?" else "¿Ya lo pagaste?"

/** El cierre sin movimiento que emparejar: «lo pagué en efectivo», «me llegó y no lo anoté». */
fun etiquetaCierreManual(tipo: TransactionType): String =
    if (tipo == TransactionType.INCOME) "Ya me llegó" else "Ya lo pagué"

/**
 * Cómo se lee una fila ya cerrada.
 *
 * Se distingue si quedó **respaldada por un movimiento** o si el dueño la cerró a mano: son dos
 * grados de certeza distintos y la fila no debería sonar igual en los dos casos. Un sello a mano
 * es una palabra suya; uno con movimiento está anclado a una plata que se puede ver.
 */
fun textoYaOcurrio(estado: OccurrenceState): String =
    if (estado.eventId != null) "Ya ocurrió este mes · con un movimiento" else "Ya ocurrió este mes"

/**
 * Una línea que describa la propuesta sin obligar a abrirla: el día, la nota (o la categoría, que
 * es lo que la app guarda como descripción cuando la nota va vacía) y nada más. El monto se pinta
 * aparte, con su formato de plata.
 */
fun descripcionPropuesta(event: FinancialEvent): String {
    val dia = epochMillisToAppDate(event.timestamp).dayOfMonth
    val que = event.description.trim().ifEmpty { event.category.trim() }
    return if (que.isEmpty()) "Movimiento del $dia" else "Movimiento del $dia · $que"
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
