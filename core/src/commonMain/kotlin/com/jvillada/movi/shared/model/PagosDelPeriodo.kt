package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

/*
 * # Los pagos del período, en `:core`
 *
 * Esto vivía en `:shared` (`ui/dashboard/ResumenDelPeriodo.kt`) y se mudó entero, sin reescribirse,
 * en la Ola 4: la caja proyectada día a día resta **los mismos pagos de la lista «Pagos del
 * período» de Plan, con su estado**, y Movi AI necesita la misma lista del lado del server para
 * contestar «¿cuánto voy a tener el 17?». Dos copias de la regla que decide qué está pagado ya se
 * separaron antes en este proyecto; una sola función, en el módulo que comparten los dos, no puede.
 *
 * `:shared` la sigue nombrando igual (`com.jvillada.movi.ui.dashboard.PagoDelPeriodo` es un
 * `typealias` de esta), así que ninguna pantalla cambió de import.
 */

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
     * Ola 4: **lo que de esta tarjeta SÍ hay que pagar este período**, en pesos, o `null` si no se
     * sabe. Es [com.jvillada.movi.shared.model.RecurringRule.pagoMinimoCop] tal cual: el mínimo que
     * el dueño tecleó del extracto. Solo lo lleva una fila con [montoEsSaldo]; lo lee la caja
     * proyectada (ver `cajaProyectada`), que con la tarjeta usa la misma regla que el «Flujo libre»
     * de Plan: el mínimo si está cargado, y si no, decir que falta.
     */
    val pagoMinimo: Long? = null,
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
     * Existen para **«Anotar este pago»**: el checklist abre la hoja de Agregar con estos dos
     * ya puestos. No es comodidad — son justo los dos datos que `esConcluyente` compara en el
     * server, así que un movimiento anotado con otra categoría o en otra cuenta no vuelve a
     * emparejarse solo, y la fila que se venía a tildar se queda sin tildar.
     */
    val categoria: String = "",
    val cuentaId: String? = null,
    /**
     * **El día en que salió la plata**, ISO, cuando se sabe: solo en lo que prueba un movimiento sin
     * que nadie lo sellara —lo que Movi emparejó sola y la cuota que bajó una deuda—. Ahí el server
     * manda en `confirmedAt` la fecha del movimiento (no hubo confirmación que fechar). En un sello
     * del dueño `confirmedAt` es cuándo tocó el botón, no cuándo pagó, así que ahí va `null` y la
     * fila dice el vencimiento en vez de inventar un día de pago.
     */
    val pagadoEl: String? = null,
    /**
     * El `"YYYY-MM"` del período del dueño al que pertenece este pago, tal como lo nombra el server
     * (`OccurrenceState.periodoDelDueno`). Solo lo lleva una fila de [pendientesDePeriodosAnteriores]:
     * es lo que le permite decir «Del período de septiembre» sin recalcular el corte.
     */
    val periodoDelDueno: String? = null,
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
     * Todavía no vence, así que el server no emitió ocurrencia y no hay nada que preguntar. Se
     * lista con su fecha y su monto, y ofrece **anotar el pago** como acción secundaria, sin decir
     * que Movi no lo encontró (todavía no tenía por qué).
     *
     * Antes no ofrecía nada, y era el caso más común: el dueño paga antes del vencimiento (la
     * Master Black vence el 2 y la pagó el 27). La cuota o la tarjeta pendiente se quedaba sin
     * forma de anotar su pago hasta vencerse.
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
            filaDelPago(pago, ocurrencia, vence, hoy)
        }
        .sortedWith(
            compareBy<PagoDelPeriodo> { it.pagado }
                .thenBy { it.diasParaVencer }
                .thenBy { it.nombre.lowercase() },
        )
}

/**
 * **Lo que quedó abierto de períodos ANTERIORES** al que se está mirando: la ocurrencia que el server
 * todavía pregunta (`occurred = false`) y cuyo vencimiento cae antes de que empiece [periodo].
 *
 * Es lo que antes vivía en «Sin confirmar». No es de este período, y por eso la lista del período no
 * lo mezcla con nada: va en un grupo propio, al final, que dice de qué período es. Lo que de un
 * período anterior SÍ se pagó no aparece en ningún lado —pertenece a ese período, y se ve en «Tus
 * períodos»—: mostrarlo acá era lo que hacía leer «Celular · ya ocurrió» en la pantalla de octubre
 * cuando lo pagado era septiembre.
 *
 * Una ocurrencia sin regla conocida se descarta en silencio, igual que en el checklist.
 */
fun pendientesDePeriodosAnteriores(
    upcoming: List<UpcomingPayment>,
    ocurrencias: List<OccurrenceState>,
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
): List<PagoDelPeriodo> {
    val empieza = ventanaDe(periodo, settings).first
    val hoy = hoySegunLosVencimientos(upcoming)
    val reglas = upcoming.associateBy { it.rule.id }
    return ocurrencias
        .filter { !it.occurred && epochDeFecha(it.dueDate) < empieza }
        .mapNotNull { ocurrencia ->
            val pago = reglas[ocurrencia.ruleId] ?: return@mapNotNull null
            filaDelPago(pago, ocurrencia, ocurrencia.dueDate, hoy)
        }
        .sortedWith(compareBy<PagoDelPeriodo> { it.vence }.thenBy { it.nombre.lowercase() })
}

/** Una fila, sea del período o de uno anterior: la regla, su ocurrencia (si hay) y su vencimiento. */
private fun filaDelPago(
    pago: UpcomingPayment,
    ocurrencia: OccurrenceState?,
    vence: String,
    hoy: LocalDate?,
): PagoDelPeriodo = PagoDelPeriodo(
    ruleId = pago.rule.id,
    nombre = pago.rule.name,
    monto = pago.rule.amount,
    pagado = ocurrencia?.occurred == true,
    // Con la fecha de la ocurrencia, el `daysUntil` que mandó el server habla de OTRO
    // vencimiento: se recalcula contra el mismo «hoy» del que salió esa respuesta.
    diasParaVencer = if (ocurrencia == null) pago.daysUntil
    else diasEntre(hoy, vence) ?: pago.daysUntil,
    montoEsSaldo = pago.rule.montoEsSaldo,
    pagoMinimo = pago.rule.pagoMinimoCop.takeIf { pago.rule.montoEsSaldo },
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
    // Solo cuando `confirmedAt` ES la fecha del movimiento: ver [PagoDelPeriodo.pagadoEl].
    pagadoEl = ocurrencia
        ?.takeIf { it.occurred && it.derivadaDeUnMovimiento && it.confirmedAt > 0L }
        ?.let { epochMillisToAppDate(it.confirmedAt).toString() },
    periodoDelDueno = ocurrencia?.periodoDelDueno ?: ocurrencia?.period,
)

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
 * Una fecha ISO en epoch ms — la de un vencimiento o la de una ocurrencia.
 *
 * Se arma al mediodía de Bogotá, igual que el resto de la app: la medianoche exacta cae justo en el
 * borde de la ventana y un pago del día del corte podía quedar afuera por un milisegundo.
 */
fun epochDeFecha(iso: String): Long {
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
