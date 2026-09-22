package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.model.Budget
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.OccurrenceState
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.ventanaDe
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

/**
 * # El Inicio contesta «¿cómo voy en este período?»
 *
 * Todo lo de este archivo es **puro**: recibe lo que el Inicio ya tenía cargado y devuelve lo que
 * hay que pintar. Sin red, sin reloj propio, sin Compose — para que las decisiones sobre la plata
 * del dueño se puedan probar sin abrir una pantalla.
 *
 * El pedido, con sus palabras: *«que la home sea tipo un resumen del periodo … en qué categorías
 * hice movimientos y en dónde se me fue la plata, qué me falta por pagar y qué ya pagué tipo
 * checklist … que también tire insights de qué cosas debería revisar»*.
 *
 * Las tres respuestas viven acá: [categoriasDelPeriodo], [checklistDelPeriodo] y
 * [cosasParaRevisar].
 */

// ── En qué se fue la plata ───────────────────────────────────────────────────

/**
 * Una categoría del período, con lo que pesa dentro del gasto total.
 *
 * @param fraccion de 0 a 1 sobre el gasto TOTAL del período, no sobre la categoría más grande. Una
 *   barra que llena el ancho porque es la mayor de tres categorías chicas diría algo falso.
 */
data class CategoriaDelPeriodo(
    val nombre: String,
    val gastado: Long,
    val fraccion: Float,
    val limite: Long? = null,
) {
    /** ¿Se pasó del presupuesto que el dueño le puso? Sin presupuesto no hay nada que decir. */
    val superada: Boolean get() = limite != null && limite > 0 && gastado > limite
}

/**
 * Las categorías del período, de mayor a menor, con el resto agrupado.
 *
 * **Agrupar la cola importa más que mostrarla.** Con veinte categorías, las quince últimas son una
 * lista que nadie lee y que empuja el resto del Inicio fuera de la pantalla; pero borrarlas haría
 * que las barras no sumen el gasto del período y el dueño no podría cuadrar la cifra de arriba con
 * lo de abajo. Van juntas en «Otras N categorías».
 *
 * Los montos llegan ya acotados al período: los calcula el server en `GET /api/dashboard/summary`,
 * con todo lo que sabe (todos los dispositivos, SMS, importaciones, anulados afuera).
 *
 * @param cuantas cuántas se muestran sueltas antes de agrupar.
 */
fun categoriasDelPeriodo(
    gastoPorCategoria: Map<String, Long>,
    presupuestos: List<Budget> = emptyList(),
    cuantas: Int = 5,
): List<CategoriaDelPeriodo> {
    val positivas = gastoPorCategoria.filterValues { it > 0 }
    val total = positivas.values.sum()
    if (total <= 0L) return emptyList()
    val limitePorCategoria = presupuestos.associate { it.category to it.monthlyLimit }

    val ordenadas = positivas.entries.sortedWith(
        // Por monto, y a igual monto por nombre: sin el desempate, dos categorías con la misma
        // cifra se intercambian de lugar entre recargas sin que nada haya cambiado.
        compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key.lowercase() },
    )
    val sueltas = ordenadas.take(cuantas).map { (nombre, gastado) ->
        CategoriaDelPeriodo(
            nombre = nombre,
            gastado = gastado,
            fraccion = gastado.toFloat() / total,
            limite = limitePorCategoria[nombre],
        )
    }
    val cola = ordenadas.drop(cuantas)
    if (cola.isEmpty()) return sueltas
    val restante = cola.sumOf { it.value }
    return sueltas + CategoriaDelPeriodo(
        nombre = if (cola.size == 1) cola.first().key else "Otras ${cola.size} categorías",
        gastado = restante,
        fraccion = restante.toFloat() / total,
        limite = if (cola.size == 1) limitePorCategoria[cola.first().key] else null,
    )
}

// ── Qué pagué y qué me falta ─────────────────────────────────────────────────

/** Una obligación del período, ya pagada o todavía pendiente. */
data class PagoDelPeriodo(
    val ruleId: String,
    val nombre: String,
    val monto: Long,
    /** `true` = el dueño ya lo dio por ocurrido en ESTE período. */
    val pagado: Boolean,
    /** Días para el vencimiento; negativo = ya venció y sigue sin pagarse. */
    val diasParaVencer: Int,
    /** Su monto es el SALDO de una deuda, no lo que va a salir de la cuenta. */
    val montoEsSaldo: Boolean = false,
    /**
     * La moneda de [monto]. Solo es distinta de `"COP"` en el saldo de una tarjeta en dólares —ver
     * [com.jvillada.movi.shared.model.RecurringRule.currency]—, y viaja hasta acá porque esta lista
     * también lo PINTA: sin el dato, una deuda de US$1.200 salía en el checklist como «$1.200».
     *
     * No entra a [faltaPorPagar] ni a ningún total: esos ya excluyen todo lo que sea un saldo.
     */
    val moneda: String = "COP",
    /**
     * **Cuándo vence, dentro de ESTE período**, en ISO (`"2026-09-12"`). Vacío solo si la fecha
     * llegó con una forma que no se entiende.
     *
     * Es el dato que el dueño pidió con todas las letras: *«con valor y fecha para ser
     * realizado»*. Y no siempre es el `dueDate` de `/api/payments/upcoming`: ese rueda al período
     * siguiente apenas algo se marca o se pasan los días de gracia (ver `dueDateFor`), así que
     * para lo ya pagado la fecha buena es la de su ocurrencia. Ver [checklistDelPeriodo].
     */
    val vence: String = "",
    /**
     * El `"YYYY-MM"` con el que se sella y se desella este pago, o `null` si todavía no se puede
     * marcar —el vencimiento no llegó, o la lectura de ocurrencias no contestó—.
     *
     * Viaja en la fila porque marcar es exactamente lo que el checklist ofrece, y el endpoint pide
     * el período del VENCIMIENTO, no el del dueño (ver `OccurrenceState.period`). Sin este dato la
     * pantalla tendría que recalcularlo, que es la forma conocida de sellar el mes equivocado.
     */
    val periodoDelSello: String? = null,
    /**
     * Está pagado porque hay un MOVIMIENTO que lo prueba —la cuota de un crédito, el pago de una
     * tarjeta—, no porque alguien lo haya marcado. No se puede destildar: se revierte borrando ese
     * movimiento. Ver [com.jvillada.movi.shared.model.OccurrenceState.derivadaDeUnMovimiento].
     */
    val derivado: Boolean = false,
    /** Un sueldo no se paga: llega. No suma en [faltaPorPagar] ni cuenta como un pago del período. */
    val esIngreso: Boolean = false,
    /**
     * **La plata que de verdad salió** por este pago, cuando se sabe: la cuota de un crédito ya
     * pagada trae el monto del movimiento que la prueba (`OccurrenceState.montoDelPago`). `null` en
     * lo sellado a mano y en lo pendiente: ahí solo está el monto esperado ([monto]).
     *
     * Lo lee la tarjeta «Disponible» para restar lo pagado y no lo pactado. Solo viaja si está en
     * la misma moneda que [monto]: mezclar dólares con pesos acá sería peor que no saberlo.
     */
    val montoPagado: Long? = null,
    /**
     * **Lo emparejó Movi sola**, no lo marcó nadie. Ver
     * [com.jvillada.movi.shared.model.OccurrenceState.automatica]: el server encontró un único
     * movimiento concluyente en la ventana del vencimiento y lo dio por hecho.
     *
     * Viaja hasta la fila por dos motivos, y los dos son de honestidad: el subtítulo lo **dice**
     * («Movi lo emparejó con…», no «lo marcaste»), y es lo único que habilita el «No fue este» —
     * la única salida que tiene el dueño cuando Movi dedujo mal.
     */
    val automatica: Boolean = false,
    /**
     * **El movimiento que hay detrás de esta fila**, cuando lo hay: el que Movi emparejó solo, el
     * que el dueño confirmó, o el que prueba una cuota. `null` en lo pendiente y en un sello viejo
     * hecho a mano.
     *
     * Es el dato que el «No fue este» necesita mandar: el rechazo se guarda por el par
     * (regla, movimiento), nunca por el movimiento solo.
     */
    val eventId: String? = null,
    /**
     * **Lo que Movi propone como esta ocurrencia**, del más probable al menos, cuando NO estuvo
     * seguro. Vacío significa dos cosas muy distintas según [pagado]: en una fila ya lista, que no
     * hay nada que proponer porque ya está resuelta; en una pendiente, que el server miró y **no
     * encontró ningún movimiento** — y ahí la fila ofrece anotarlo.
     *
     * Que la lista NO esté vacía es, en sí, la señal de que Movi tuvo dudas: con un único
     * movimiento concluyente empareja solo y no propone nada (ver `ocurrenciaConcluyente`).
     */
    val candidatos: List<FinancialEvent> = emptyList(),
    /**
     * La categoría de la regla y la cuenta de la que sale (o a la que entra), si la tiene.
     *
     * Existen para **«Anotar el movimiento»**: el checklist abre la hoja de Agregar con estos dos
     * ya puestos. No es comodidad — son justo los dos datos que `esConcluyente` compara en el
     * server, así que un movimiento anotado con otra categoría o en otra cuenta no vuelve a
     * emparejarse solo, y la fila que se venía a tildar se queda sin tildar.
     */
    val categoria: String = "",
    val cuentaId: String? = null,
) {
    val vencido: Boolean get() = !pagado && diasParaVencer < 0

    /**
     * **Qué le pasa a esta fila**, que es lo único que decide qué dice y qué ofrece.
     *
     * Desde esta ola la casilla del checklist **no es un control**: es un reflejo. El dueño lo
     * pidió con todas las letras —*«no me debería dejar hacer check sin que el movimiento asociado
     * exista, y esto debería ser read only, que sea inteligente tipo, se detecta este movimiento
     * asociado … o que pregunte si ya sucedió si la app tiene dudas»*— y el motivo es el de
     * siempre acá adentro: tildar sin evidencia apagaba el aviso de una deuda que podía seguir
     * viva. Ver [EstadoDeLaFila].
     */
    val estado: EstadoDeLaFila get() = when {
        // Lo derivado y lo automático primero: los dos vienen con `pagado`, y los dos tienen un
        // movimiento detrás aunque nadie haya sellado nada.
        pagado && (derivado || automatica || eventId != null) -> EstadoDeLaFila.LISTO
        // Un sello viejo, de cuando la casilla SÍ marcaba sin movimiento. No se pueden crear más;
        // los que ya están en la base no se pueden dejar sin salida.
        pagado -> EstadoDeLaFila.MARCADA_A_MANO
        // Sin período que sellar el server ni siquiera está preguntando: el vencimiento no llegó.
        periodoDelSello == null -> EstadoDeLaFila.AUN_NO_VENCE
        candidatos.isNotEmpty() -> EstadoDeLaFila.CON_DUDAS
        else -> EstadoDeLaFila.SIN_MOVIMIENTO
    }
}

/**
 * Los cinco estados en que puede estar una fila del checklist — y, sobre todo, **lo que cada uno
 * puede ofrecer sin mentir**.
 *
 * Hasta esta ola había dos: tildada y sin tildar, con una casilla que sellaba el período **sin
 * ninguna evidencia**. Eso se fue entero. Lo que queda es una lista de solo lectura donde el estado
 * lo decide el movimiento, no el dedo.
 */
enum class EstadoDeLaFila {
    /**
     * Hay un movimiento detrás. El subtítulo dice **cuál de los tres grados** es —lo emparejó Movi,
     * lo confirmó el dueño, o lo prueba el movimiento que bajó la deuda— porque son certezas
     * distintas y no deberían sonar igual.
     */
    LISTO,

    /**
     * Sellada a mano, sin movimiento, **antes de esta ola**. Se muestra como lista y lo dice; lo
     * único que ofrece es «Quitar la marca», que es la única forma de deshacer algo que no tiene
     * evidencia detrás. No se pueden crear nuevas.
     */
    MARCADA_A_MANO,

    /** El período está abierto y hay candidatos: la fila **pregunta**, con el mejor a la vista. */
    CON_DUDAS,

    /**
     * Abierto y sin un solo candidato: pagó en efectivo, desde una cuenta que Movi no lleva, o el
     * banco nunca avisó. La fila lo dice y ofrece **anotar el movimiento**, con los datos del
     * recurrente ya puestos. Deliberadamente **no** ofrece «marcar sin movimiento»: esa era la
     * puerta que esta ola vino a cerrar.
     */
    SIN_MOVIMIENTO,

    /**
     * Todavía no vence, así que el server no emitió ocurrencia y no hay nada que preguntar ni nada
     * que anotar. Se lista con su fecha y su monto, y nada más.
     */
    AUN_NO_VENCE,
}

/**
 * El checklist del período: **todo lo que se paga en este período**, con lo hecho tildado.
 *
 * Tres decisiones:
 *
 * 1. **Es del PERÍODO, no de los próximos siete días.** «Próximos pagos» contestaba «¿qué se viene
 *    ya?»; esto contesta «¿cuánto me falta de lo de este mes?», que es lo que el dueño pidió. Una
 *    cuota que vence pasado el corte no entra: es del período siguiente.
 * 2. **Lo pagado no desaparece.** Un checklist sin lo tildado no deja ver el avance, que es la
 *    mitad de para qué sirve.
 * 3. **Primero lo que falta, y dentro de eso lo vencido.** Lo que ya está hecho no compite por la
 *    atención, así que va al final aunque venza antes.
 *
 * ## La ocurrencia manda sobre el vencimiento vigente
 *
 * `/api/payments/upcoming` contesta «¿cuál es el PRÓXIMO vencimiento de esta regla?», y esa fecha
 * **rueda**: apenas el dueño marca el arriendo de septiembre, el vencimiento vigente pasa a ser el
 * de octubre; y si pasaron los días de gracia sin marcar nada, también. Leer solo esa fecha tenía
 * dos consecuencias, las dos vistas en la pantalla del dueño:
 *
 * - **lo pagado se caía del checklist** —su fecha ya era del período siguiente—, así que la tarjeta
 *   del Inicio decía «0 de 3 pagados» para siempre y solo listaba lo que faltaba. Es el reclamo que
 *   abrió este trabajo: *«solo muestra los faltantes, no muestra todos»*;
 * - **lo vencido hace rato también**: el gimnasio del día 5, sin marcar, desaparecía del período
 *   apenas se le pasaba la gracia, aunque siguiera debiéndose.
 *
 * Por eso manda la OCURRENCIA cuando su vencimiento cae en la ventana (ver `OccurrenceState`, que
 * habla del mes en curso y no rueda nunca): de ahí salen la fecha, el tilde y el período del sello.
 * El `dueDate` de [upcoming] solo se usa cuando no hay ocurrencia que mirar — el caso de un pago
 * que todavía no vence, que es justamente el que tampoco se puede marcar.
 *
 * El sello de «ya ocurrió» lo pone el dueño; acá solo se lee. Una regla sin ocurrencia conocida
 * cuenta como pendiente: es el lado seguro de equivocarse —recuerda algo que quizá ya pagó— contra
 * dar por pagado algo que no.
 */
fun checklistDelPeriodo(
    upcoming: List<UpcomingPayment>,
    ocurrencias: List<OccurrenceState>,
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
): List<PagoDelPeriodo> {
    val ventana = ventanaDe(periodo, settings)
    val hoy = hoySegunLosVencimientos(upcoming)
    // Una ocurrencia por regla: el endpoint emite la del período en juego, así que dos en la misma
    // ventana no debería pasar — y si pasara, gana la última, que es la más cercana al presente.
    val delPeriodo = ocurrencias.filter { epochDeFecha(it.dueDate) in ventana }.associateBy { it.ruleId }
    return upcoming
        .mapNotNull { pago ->
            val ocurrencia = delPeriodo[pago.rule.id]
            val vence = ocurrencia?.dueDate
                ?: pago.dueDate.takeIf { epochDeFecha(it) in ventana }
                ?: return@mapNotNull null
            PagoDelPeriodo(
                ruleId = pago.rule.id,
                nombre = pago.rule.name,
                monto = pago.rule.amount,
                pagado = ocurrencia?.occurred == true,
                // Con la fecha de la ocurrencia, el `daysUntil` que mandó el server habla de OTRO
                // vencimiento: se recalcula contra el mismo «hoy» del que salió esa respuesta.
                diasParaVencer = if (ocurrencia == null) pago.daysUntil
                else diasEntre(hoy, vence) ?: pago.daysUntil,
                montoEsSaldo = pago.rule.montoEsSaldo,
                moneda = pago.rule.currency,
                vence = vence,
                periodoDelSello = ocurrencia?.period,
                derivado = ocurrencia?.derivadaDeUnMovimiento == true,
                esIngreso = pago.rule.type == TransactionType.INCOME,
                montoPagado = ocurrencia
                    ?.takeIf { it.occurred }
                    ?.takeIf { (it.monedaDelPago ?: pago.rule.currency) == pago.rule.currency }
                    ?.montoDelPago,
                // Lo que la fila necesita para ser de SOLO LECTURA y aun así ofrecer algo útil:
                // de dónde salió el tilde (o si no salió de ningún lado), qué movimiento hay
                // detrás para poder decir «no fue este», y qué propone Movi cuando tuvo dudas.
                automatica = ocurrencia?.automatica == true,
                eventId = ocurrencia?.eventId,
                candidatos = ocurrencia?.candidates.orEmpty(),
                categoria = pago.rule.category,
                cuentaId = pago.rule.accountId,
            )
        }
        .sortedWith(
            compareBy<PagoDelPeriodo> { it.pagado }
                .thenBy { it.diasParaVencer }
                .thenBy { it.nombre.lowercase() },
        )
}

/**
 * Qué día es hoy **según la misma respuesta** que trajo los vencimientos.
 *
 * `daysUntil` ya es la distancia a hoy medida por el server, con su reloj y su zona: restarla del
 * vencimiento devuelve esa fecha sin que este archivo consulte ningún reloj —y así sigue siendo
 * puro, que es la condición de todo lo que decide sobre la plata del dueño acá adentro—. `null` si
 * la lista viene vacía o con fechas que no se entienden.
 */
private fun hoySegunLosVencimientos(upcoming: List<UpcomingPayment>): LocalDate? =
    upcoming.firstNotNullOfOrNull { pago ->
        fechaDe(pago.dueDate)?.minus(pago.daysUntil, DateTimeUnit.DAY)
    }

/** Días de [hoy] a [vence], o `null` si alguna de las dos no se entiende. */
private fun diasEntre(hoy: LocalDate?, vence: String): Int? {
    val destino = fechaDe(vence) ?: return null
    return hoy?.daysUntil(destino)
}

private fun fechaDe(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()

/**
 * El vencimiento de un pago, en epoch ms, para poder preguntarle si cae en la ventana del período.
 */
internal fun UpcomingPayment.epochDelVencimiento(): Long = epochDeFecha(dueDate)

/**
 * Una fecha ISO en epoch ms — la de un vencimiento o la de una ocurrencia.
 *
 * Se arma al mediodía de Bogotá, igual que el resto de la app: la medianoche exacta cae justo en el
 * borde de la ventana y un pago del día del corte podía quedar afuera por un milisegundo.
 */
internal fun epochDeFecha(iso: String): Long {
    val partes = iso.split("-")
    if (partes.size != 3) return 0L
    val anio = partes[0].toIntOrNull() ?: return 0L
    val mes = partes[1].toIntOrNull() ?: return 0L
    val dia = partes[2].toIntOrNull() ?: return 0L
    return LocalDateTime(anio, mes, dia, 12, 0)
        // `AppTimeZone.zone` y NO `TimeZone.of("America/Bogota")`: en la web (wasm) no viene la
        // base de zonas IANA y ese `of` LANZA. Pasó el 19-sep: el Inicio del dueño se congelaba en
        // la web —se veía pero no respondía un clic— con `IllegalTimeZoneException` en consola.
        .toInstant(AppTimeZone.zone)
        .toEpochMilliseconds()
}

/**
 * Cuánto falta por pagar de este período.
 *
 * Fuera quedan dos cosas que no son plata que vaya a salir: el SALDO de una tarjeta (es una deuda,
 * no una cuota) y todo INGRESO. Lo segundo hacía que el «Falta $X» del Inicio incluyera el sueldo
 * del dueño mientras no lo marcara — la cifra afirmaba que le faltaba pagar su propio salario.
 */
fun faltaPorPagar(checklist: List<PagoDelPeriodo>): Long =
    checklist.filter { !it.pagado && !it.montoEsSaldo && !it.esIngreso }.sumOf { it.monto }

// ── Cómo se agrupa y qué dice la tarjeta ─────────────────────────────────────

/** Lo que falta pagar, en el orden en que sale del checklist. Un ingreso no se paga: no va acá. */
fun pagosPendientes(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> =
    checklist.filter { !it.pagado && !it.esIngreso }

/** Lo que falta que LLEGUE: el sueldo, un arriendo que cobra. Se tilda igual, pero no se «paga». */
fun ingresosPendientes(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> =
    checklist.filter { !it.pagado && it.esIngreso }

/** Lo ya tildado, pagos e ingresos juntos: la mitad del checklist que prueba el avance. */
fun yaMarcados(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> = checklist.filter { it.pagado }

/**
 * El avance **sobre los pagos**: cuántos están tildados de cuántos hay.
 *
 * Los ingresos quedan afuera del conteo por la misma razón por la que quedan afuera de
 * [faltaPorPagar]: «te faltan 2 de 5 pagos» tiene que hablar de plata que sale. Los ingresos siguen
 * estando en el checklist y se tildan igual — no se cuentan, que es distinto de esconderlos.
 */
fun avanceDelChecklist(checklist: List<PagoDelPeriodo>): Pair<Int, Int> {
    val pagos = checklist.filter { !it.esIngreso }
    return pagos.count { it.pagado } to pagos.size
}

/**
 * **La línea que hace honesta a la tarjeta del Inicio**: dice que lo listado es lo que FALTA, y de
 * cuántos pagos del período se trata.
 *
 * El reclamo del dueño, textual: *«solo muestra los faltantes, no muestra todos; debería indicar
 * que esos son los faltantes nada más»*. La tarjeta no pasa a listarlo todo —para eso está el
 * checklist completo, a un toque— pero deja de presentar una parte como si fuera el total.
 */
fun lineaDeLoQueFalta(checklist: List<PagoDelPeriodo>): String {
    val (pagados, total) = avanceDelChecklist(checklist)
    val faltan = total - pagados
    val pagos = if (total == 1) "pago" else "pagos"
    return when {
        total == 0 -> "Este período no tiene pagos anotados"
        faltan == 0 && total == 1 -> "Marcaste el único pago de este período"
        faltan == 0 -> "Marcaste los $total $pagos de este período"
        else -> "Te ${if (faltan == 1) "falta" else "faltan"} $faltan de $total $pagos de este período"
    }
}

/**
 * El pie que dice dónde está lo que esta tarjeta NO muestra, o `null` si no falta nada por contar.
 *
 * Sin él, «te faltan 3 de 7» deja al dueño con la pregunta de dónde quedaron los otros cuatro.
 */
fun pieDeLoYaPagado(checklist: List<PagoDelPeriodo>): String? {
    val (pagados, total) = avanceDelChecklist(checklist)
    if (pagados == 0 || pagados == total) return null
    return "Ya marcaste $pagados. El checklist completo está en «Ver todos»."
}

// ── Qué debería revisar ──────────────────────────────────────────────────────

/** Algo que el Inicio sugiere mirar, con la pantalla donde se resuelve. */
data class CosaParaRevisar(
    val texto: String,
    val detalle: String,
    val destino: DestinoDeRevision,
    /** Las urgentes van primero y en ámbar; el resto es una sugerencia, no una alarma. */
    val urgente: Boolean = false,
)

/** A dónde lleva tocar una sugerencia. Un enum y no una `Screen` para que esto siga siendo puro. */
enum class DestinoDeRevision { MOVIMIENTOS, RECURRENTES, PRESUPUESTOS, CREDITOS, SMS, SUSCRIPCIONES, CUADRE }

/**
 * **Lo que el Inicio recomienda mirar hoy**, de lo más urgente a lo más opcional.
 *
 * La regla que ordena todo esto: **cada sugerencia tiene que poder accionarse**. «Gastaste más que
 * el mes pasado» no es una sugerencia, es una observación; «la cuota del vehículo venció y sigue
 * sin marcarse» sí lo es, porque hay algo que hacer y una pantalla donde hacerlo.
 *
 * Por eso ninguna sale de una corazonada: todas salen de un dato que ya está cargado y llevan a la
 * pantalla donde se resuelve. Y son **como mucho [cuantas]**: una lista larga de consejos es ruido,
 * y el ruido enseña a ignorar la sección entera.
 */
fun cosasParaRevisar(
    checklist: List<PagoDelPeriodo>,
    categorias: List<CategoriaDelPeriodo>,
    flujoDelPeriodo: Long,
    smsPorConfirmar: Int,
    candidatosAPagoDeTarjeta: Int,
    gastoSinCategoria: Long = 0,
    /**
     * El aviso de las cuentas que llevan más de un período sin cuadrarse contra el banco, ya
     * escrito (ver `textoDelAvisoDeCuadre`); `null` = no hay ninguna y no se dice nada.
     *
     * Llega hecho en vez de calcularse acá para que este archivo siga sin saber de cuentas ni de
     * relojes: la regla vive en `ui/cuadre`, que es donde se resuelve.
     */
    avisoDeCuadre: String? = null,
    cuantas: Int = 4,
): List<CosaParaRevisar> {
    val todas = buildList {
        val vencidos = checklist.filter { it.vencido }
        if (vencidos.isNotEmpty()) {
            add(
                CosaParaRevisar(
                    texto = if (vencidos.size == 1) "«${vencidos.first().nombre}» venció y no está marcado"
                    else "${vencidos.size} pagos vencidos sin marcar",
                    detalle = "Si ya lo pagaste, márcalo para que deje de avisarte.",
                    destino = DestinoDeRevision.RECURRENTES,
                    urgente = true,
                ),
            )
        }
        if (smsPorConfirmar > 0) {
            add(
                CosaParaRevisar(
                    texto = "$smsPorConfirmar ${if (smsPorConfirmar == 1) "mensaje" else "mensajes"} del banco sin confirmar",
                    detalle = "Hasta confirmarlos no cuentan en el gasto del período.",
                    destino = DestinoDeRevision.SMS,
                    urgente = smsPorConfirmar >= 10,
                ),
            )
        }
        val superadas = categorias.filter { it.superada }
        if (superadas.isNotEmpty()) {
            add(
                CosaParaRevisar(
                    texto = if (superadas.size == 1) "Te pasaste del presupuesto de ${superadas.first().nombre}"
                    else "Te pasaste en ${superadas.size} presupuestos",
                    detalle = "Todavía estás en el período: puedes ajustar el límite o frenar el gasto.",
                    destino = DestinoDeRevision.PRESUPUESTOS,
                ),
            )
        }
        if (candidatosAPagoDeTarjeta > 0) {
            add(
                CosaParaRevisar(
                    texto = "$candidatosAPagoDeTarjeta ${if (candidatosAPagoDeTarjeta == 1) "movimiento parece" else "movimientos parecen"} pago de tarjeta",
                    detalle = "Marcarlos evita contarlos dos veces: como gasto y como menos deuda.",
                    destino = DestinoDeRevision.MOVIMIENTOS,
                ),
            )
        }
        if (gastoSinCategoria > 0) {
            add(
                CosaParaRevisar(
                    texto = "Hay gastos sin categoría este período",
                    // El «y la próxima vez» no es una promesa de marketing: es literalmente lo que
                    // hace `MemoriaDeCategorias`. Ponerle categoría a uno le enseña a Movi el
                    // destinatario entero, y por eso vale la pena decirle que el rato invertido
                    // rinde más de una vez.
                    detalle = "Ponles una y Movi reconoce sola a ese mismo destinatario la próxima vez.",
                    destino = DestinoDeRevision.MOVIMIENTOS,
                ),
            )
        }
        // No es urgente y va abajo de lo que vence: nadie pierde plata hoy por no haber cuadrado.
        // Lo que sí pasa —y por eso está— es que la diferencia se compone en silencio: los
        // rendimientos de una cuenta de ahorros no llegan por SMS, así que si nadie los anota, el
        // saldo de Movi se va quedando corto mes a mes.
        if (avisoDeCuadre != null) {
            add(
                CosaParaRevisar(
                    texto = avisoDeCuadre,
                    detalle = "Compara con el saldo del banco: los rendimientos y las cuotas de manejo no avisan.",
                    destino = DestinoDeRevision.CUADRE,
                ),
            )
        }
        if (flujoDelPeriodo < 0) {
            add(
                CosaParaRevisar(
                    texto = "Vas gastando más de lo que entró este período",
                    detalle = "La categoría más pesada es ${categorias.firstOrNull()?.nombre ?: "la primera de la lista"}.",
                    destino = DestinoDeRevision.MOVIMIENTOS,
                ),
            )
        }
    }
    return todas.sortedByDescending { it.urgente }.take(cuantas)
}

/**
 * **Cuánta plata del período quedó sin categoría.** Los dos nombres, porque durante un tiempo Movi
 * escribió «Otro» en singular al confirmar un SMS mientras el resto de la app decía «Otros»: hay
 * movimientos viejos con cada uno, y los dos significan lo mismo — que nadie decidió todavía.
 *
 * No se confunde con el renglón «Otras N categorías» que arma [categoriasDelPeriodo] para la cola
 * del gráfico: eso es un agrupado de categorías que sí existen, y se compara contra el mapa crudo.
 */
fun gastoSinCategoriaDe(gastoPorCategoria: Map<String, Long>): Long =
    gastoPorCategoria.entries
        .filter { it.key.trim().equals("Otros", ignoreCase = true) || it.key.trim().equals("Otro", ignoreCase = true) }
        .sumOf { it.value }
        .coerceAtLeast(0)

/** El monto que el checklist dice que ya se pagó, para el rótulo de avance. */
fun yaPagado(checklist: List<PagoDelPeriodo>): Long =
    checklist.filter { it.pagado && !it.montoEsSaldo && !it.esIngreso }.sumOf { it.monto }

/** Un pago de [checklist] que sirva de ejemplo de lo que urge, o `null` si no falta nada. */
fun loQueUrge(checklist: List<PagoDelPeriodo>): PagoDelPeriodo? =
    checklist.firstOrNull { !it.pagado && !it.esIngreso }
