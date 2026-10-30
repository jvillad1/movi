package com.jvillada.movi.shared.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * # La caja proyectada día a día (Ola 4 · «Movi mira adelante»)
 *
 * La pregunta: *¿cuánta plata voy a tener cada día hasta el cierre del período, y cuál es el día
 * más justo?* Hasta acá Movi decía bien cómo iba el período, pero no lo que venía.
 *
 * ## De dónde sale cada peso (y nada más)
 *
 * - **El punto de partida es «Tu plata» de hoy**, la de [patrimonioDe] — nunca una suma a mano.
 * - **Lo que entra y sale con fecha son los pagos del período de Plan**, la lista de
 *   [checklistDelPeriodo], **con su estado de pagado tal cual**: lo pagado ya está adentro de Tu
 *   plata y no se vuelve a restar; lo pendiente sale el día que vence (o hoy, si ya venció). Los
 *   ingresos que faltan entran el día que se esperan.
 * - **Una tarjeta paga su mínimo**, con la misma regla que el «Flujo libre» de Plan: su monto es el
 *   SALDO ([RecurringRule.montoEsSaldo]) y lo que hay que pagar es [PagoDelPeriodo.pagoMinimo]. Sin
 *   el mínimo cargado no se inventa un porcentaje: la tarjeta queda en [CajaProyectada.sinContar]
 *   y la pantalla dice que falta.
 * - **El gasto del día a día es un supuesto, y se dice**: el promedio diario de gasto variable de
 *   los últimos períodos cerrados (ver [gastoDelDiaADia]), restado desde mañana hasta el cierre.
 *   Sin períodos con qué estimarlo no se resta nada y [CajaProyectada.gastoDiario] viene en `null`.
 *
 * Lo que NO está y por qué: los pagos en otra moneda (no hay una cifra en pesos honesta), las
 * cuotas que paga la nómina o un tercero (no salen de tu plata: ya no están en la lista), y las
 * compras con tarjeta de este período (se pagan en el próximo; su mínimo de ESTE período sí está).
 *
 * Puro: el «hoy», el cierre y las cifras entran por parámetro.
 *
 * ## Vuelta 70 · «¿Me alcanza hasta que me paguen?»
 *
 * - **El horizonte** ya no es el cierre del período: es el día del **próximo ingreso esperado** (el
 *   salario, que el server ya pone en el día en que de verdad se espera con `fechaEsperadaDelIngreso`:
 *   el arranque del período siguiente), ese día incluido; sin un ingreso recurrente, 30 días (y nunca
 *   menos que el cierre del período). Ver [horizonteDeLaCaja].
 * - **Los pagos del período siguiente** que vencen dentro del horizonte entran con la MISMA regla de
 *   siempre (mínimo de tarjeta o [CajaProyectada.sinContar], lo vencido hoy, lo saldado no cuenta):
 *   son las filas de [checklistDelPeriodo] del período siguiente, sobre las mismas respuestas. El
 *   salario de ese horizonte entra como subida, rotulado [LLEGA_TU_SALARIO].
 * - **Dos cifras por día**: [DiaDeLaCaja.saldo], lo que habrá en las cuentas (el saldo bruto, con lo
 *   apartado adentro: un apartado no mueve plata), y [DiaDeLaCaja.libre], el titular: el saldo menos
 *   el ahorro del período y lo apartado fuera de los fijos ([apartadoFueraDeLosFijos], la única regla
 *   del cruce, sobre las filas que la caja resta). El día más justo y el rojo se miden en lo libre.
 *
 * La entrada para las pantallas y Movi AI es [cajaHastaQueTePaguen]: una sola función para todos.
 */

/** Un movimiento con fecha dentro de la proyección. [monto] con signo: positivo entra, negativo sale. */
@Serializable
data class MovimientoDeLaCaja(
    val nombre: String,
    val monto: Long,
)

/** Un día de la proyección: cómo queda Tu plata al cerrar ese día, y lo que pasó en él. */
@Serializable
data class DiaDeLaCaja(
    /** ISO, `"2026-10-17"`. */
    val fecha: String,
    /**
     * Lo que habrá en las cuentas al cerrar el día (el saldo bruto), con lo que vence ese día y el gasto
     * estimado del día ya restados. El ahorro no lo cambia: no mueve plata.
     */
    val saldo: Long,
    /** Los pagos e ingresos de ese día (sin el gasto del día a día, que es igual todos los días). */
    val movimientos: List<MovimientoDeLaCaja> = emptyList(),
    /**
     * Vuelta 70 · **lo libre ese día**: [saldo] menos el ahorro del período y lo apartado fuera de los
     * fijos ([CajaProyectada.reservado]). Es la cifra titular. Sin ahorro ni apartados, igual a [saldo].
     */
    val libre: Long = saldo,
) {
    /** El pago más grande del día, o `null` si ese día no sale ningún pago. */
    val pagoMasGrande: MovimientoDeLaCaja? get() = movimientos.filter { it.monto < 0 }.minByOrNull { it.monto }
}

/**
 * Toda la proyección.
 *
 * @property sinContar lo que estaba en la lista y NO entró a la cuenta, con el porqué en palabras
 *   («Master Black: falta el pago mínimo»). Vacía cuando entró todo.
 */
@Serializable
data class CajaProyectada(
    /**
     * De dónde parte: lo que hay hoy en las cuentas de Tu plata. Con [cajaHastaQueTePaguen], el saldo
     * bruto (Tu plata + lo apartado que de verdad pesa); quien llama a [cajaProyectada] a secas decide.
     */
    val tuPlataHoy: Long,
    /** El gasto variable que se resta cada día desde mañana; `null` = no hay con qué estimarlo. */
    val gastoDiario: Long?,
    val dias: List<DiaDeLaCaja>,
    val sinContar: List<String> = emptyList(),
    /** Vuelta 70 · el ahorro del período (`PlataDelDisponible.ahorroDelPeriodo`): no es libre. */
    val ahorro: Long = 0L,
    /** Vuelta 70 · lo apartado fuera de los fijos ([apartadoFueraDeLosFijos]): tampoco es libre. */
    val apartado: Long = 0L,
    /** Vuelta 70 · el horizonte termina el día que llega el salario (y no a 30 días, sin él). */
    val hastaElSalario: Boolean = false,
    /**
     * Sin salario, ¿es porque el ingreso fijo que hay está en otra moneda o sin monto? Solo cambia lo que
     * se dice («Sin un salario en pesos anotado»), no la cuenta.
     */
    val salarioQueNoCuenta: Boolean = false,
    /**
     * Vuelta 75 · sin salario, el horizonte es **el arranque del período siguiente** (el dueño eligió su
     * período: ese día es el que dijo que le pagan) y no 30 días. Solo cambia lo que se dice («hasta el
     * 25 nov»), no la cuenta de cada día.
     */
    val hastaElPeriodoSiguiente: Boolean = false,
) {
    /** Lo que de las cuentas no es para gastar: el ahorro y lo apartado. Igual todos los días. */
    val reservado: Long get() = ahorro + apartado

    /** **Lo libre hoy**, antes de lo que vence hoy: lo de las cuentas menos [reservado]. */
    val libreHoy: Long get() = tuPlataHoy - reservado

    /** El día en que lo libre llega más abajo; ante un empate, el primero. */
    val diaMasBajo: DiaDeLaCaja get() = dias.minWith(compareBy<DiaDeLaCaja> { it.libre }.thenBy { it.fecha })

    /** El primer día en que lo libre queda en negativo (no alcanza), o `null` si ninguno. */
    val primerDiaEnRojo: DiaDeLaCaja? get() = dias.firstOrNull { it.libre < 0L }

    /** El último día del horizonte (el del salario, si lo hay). */
    val alCierre: DiaDeLaCaja get() = dias.last()

    /** El salario esperado que cierra el horizonte, o `null` (sin él, o sin horizonte por el salario). */
    val salario: MovimientoDeLaCaja? get() =
        if (!hastaElSalario) null else alCierre.movimientos.firstOrNull { it.nombre == LLEGA_TU_SALARIO }
}

/** El rótulo de la subida del salario en la caja (vuelta 70). */
const val LLEGA_TU_SALARIO: String = "Llega tu salario (esperado)"

/** Sin un ingreso recurrente, hasta dónde mira la caja: 30 días desde hoy. */
const val DIAS_DE_LA_CAJA_SIN_SALARIO: Int = 30

/** Por qué una fila de la lista no entra a la cuenta, en palabras. `null` = entra. */
private fun porQueNoEntra(pago: PagoDelPeriodo): String? = when {
    pago.hecho -> null
    pago.montoEsSaldo && pago.pagoMinimo == null -> "${pago.nombre}: falta el pago mínimo"
    pago.montoEsSaldo -> null // el mínimo ya está en pesos (ver RecurringRule.pagoMinimoCop)
    pago.moneda != "COP" -> "${pago.nombre}: está en ${pago.moneda}"
    else -> null
}

/**
 * **Lo que va a salir de las cuentas por [filas]**, con la MISMA regla de la caja: un fijo, su monto;
 * una tarjeta, su pago mínimo (sin él, nada: va a «No entra a la cuenta»); lo pagado, un ingreso, un
 * abono planeado o algo en otra moneda, nada. Es el total de «Lo que viene» y de «Lo que vencerá»
 * (vuelta 70): la misma cifra que la caja resta.
 */
fun loQueSaleDe(filas: List<PagoDelPeriodo>): Long = filas.sumOf { fila -> (montoDeLaFila(fila) ?: 0L).let { if (it < 0L) -it else 0L } }

/** Lo que mueve esta fila, con signo, o `null` si no mueve nada (pagada, o no entra). */
private fun montoDeLaFila(pago: PagoDelPeriodo): Long? {
    // Vuelta 72: lo hecho no sale. La fila del extracto de una tarjeta está hecha con el mínimo cubierto;
    // si no, su `pagoMinimo` ya es lo que le falta a ese mínimo (una vez, en su fecha).
    if (pago.hecho || porQueNoEntra(pago) != null) return null
    // Vuelta 63: el abono planeado a una deuda no es una salida que la caja dé por hecha: es una
    // intención. El día que se hace, el traspaso baja Tu plata y la caja arranca de ahí.
    if (pago.esAbono) return null
    // Re-revisión: una tarjeta sin extracto pagada por debajo de su mínimo sale por lo que le falta.
    val monto = if (pago.montoEsSaldo) pago.faltaDelMinimo ?: pago.pagoMinimo ?: return null else pago.monto
    if (monto <= 0L) return null
    return if (pago.esIngreso) monto else -monto
}

/**
 * La caja proyectada de [hoy] a [ultimoDia] (los dos incluidos), o `null` si no hay días que
 * proyectar o las fechas no se entienden.
 *
 * @param tuPlataHoy «Tu plata» de [patrimonioDe], ahora.
 * @param pagos la lista «Pagos del período» de Plan, tal cual ([checklistDelPeriodo]).
 * @param gastoDiario el gasto variable diario estimado, o `null` si no se pudo estimar.
 * @param hoy ISO, la fecha de hoy en la zona de la app.
 * @param ultimoDia ISO, el último día que se proyecta (el cierre del período, o el horizonte de
 *   [horizonteDeLaCaja]). Una fila que vence después no entra.
 * @param ahorro el ahorro del período, en pesos: se resta de lo libre, no del saldo.
 * @param apartado lo apartado fuera de los fijos: se resta de lo libre, no del saldo.
 * @param salario la fila del ingreso que cierra el horizonte: entra rotulada [LLEGA_TU_SALARIO].
 */
fun cajaProyectada(
    tuPlataHoy: Long,
    pagos: List<PagoDelPeriodo>,
    gastoDiario: Long?,
    hoy: String,
    ultimoDia: String,
    ahorro: Long = 0L,
    apartado: Long = 0L,
    salario: PagoDelPeriodo? = null,
): CajaProyectada? {
    val desde = runCatching { LocalDate.parse(hoy) }.getOrNull() ?: return null
    val hasta = runCatching { LocalDate.parse(ultimoDia) }.getOrNull() ?: return null
    if (hasta < desde) return null

    // Cada fila que mueve plata, en el día en que la mueve: lo vencido y sin pagar sale HOY (se
    // sigue debiendo), lo demás el día de su vencimiento. Una fila sin fecha legible también hoy:
    // es el lado prudente.
    val porDia = mutableMapOf<LocalDate, MutableList<MovimientoDeLaCaja>>()
    pagos.forEach { pago ->
        val monto = montoDeLaFila(pago) ?: return@forEach
        val vence = runCatching { LocalDate.parse(pago.vence) }.getOrNull() ?: desde
        val dia = if (vence < desde) desde else vence
        // Una fila que vence pasado el horizonte no entra.
        if (dia > hasta) return@forEach
        val nombre = if (salario != null && pago.ruleId == salario.ruleId && pago.vence == salario.vence) LLEGA_TU_SALARIO else pago.nombre
        porDia.getOrPut(dia) { mutableListOf() } += MovimientoDeLaCaja(nombre, monto)
    }

    val gasto = gastoDiario?.coerceAtLeast(0L)
    val reservado = ahorro.coerceAtLeast(0L) + apartado.coerceAtLeast(0L)
    var saldo = tuPlataHoy
    val dias = (0..desde.daysUntil(hasta)).map { i ->
        val dia = desde.plus(i, DateTimeUnit.DAY)
        val movimientos = porDia[dia].orEmpty().sortedBy { it.monto }
        saldo += movimientos.sumOf { it.monto }
        // El gasto del día a día desde MAÑANA: lo de hoy ya está, en parte, adentro de Tu plata, y
        // restarle un día entero encima lo contaría dos veces.
        if (i > 0 && gasto != null) saldo -= gasto
        DiaDeLaCaja(fecha = dia.toString(), saldo = saldo, movimientos = movimientos, libre = saldo - reservado)
    }
    return CajaProyectada(
        tuPlataHoy = tuPlataHoy,
        gastoDiario = gasto,
        dias = dias,
        // Solo lo que cae dentro de lo que se proyecta; la misma tarjeta de dos períodos, una vez.
        sinContar = pagos
            .filter { pago -> runCatching { LocalDate.parse(pago.vence) }.getOrNull()?.let { it <= hasta } ?: true }
            .mapNotNull { porQueNoEntra(it) }
            .distinct(),
        ahorro = ahorro.coerceAtLeast(0L),
        apartado = apartado.coerceAtLeast(0L),
        hastaElSalario = salario != null,
    )
}

// ── Vuelta 70 · hasta que te paguen ──────────────────────────────────────────

/**
 * **Hasta dónde mira la caja.**
 *
 * @property hasta el último día que se proyecta, incluido.
 * @property salario la fila del ingreso que lo cierra, o `null` si no hay (entonces son 30 días).
 */
data class HorizonteDeLaCaja(
    val hasta: LocalDate,
    val salario: PagoDelPeriodo?,
    /** Sin salario: hay un ingreso fijo en el período siguiente, pero en otra moneda o sin monto. */
    val salarioQueNoCuenta: Boolean = false,
    /** Vuelta 75 · sin salario y con el período elegido: [hasta] es el arranque del período siguiente. */
    val alPeriodoSiguiente: Boolean = false,
)

/**
 * **El horizonte de la caja**: el día del próximo ingreso esperado, ese día incluido.
 *
 * El ingreso es **el primer ingreso recurrente pendiente del período siguiente**, en pesos y con
 * monto: el salario del día de corte, que `/api/payments/upcoming` ya trae en el día en que se espera
 * (`fechaEsperadaDelIngreso`: el arranque del período siguiente, corrido si el corte cae en fin de
 * semana o festivo). Sin uno, [DIAS_DE_LA_CAJA_SIN_SALARIO] días desde hoy, y nunca antes del cierre
 * del período (la caja de siempre llegaba hasta ahí).
 *
 * **Vuelta 75 · sin salario pero con el período elegido** ([conElPeriodoElegido]: el corte no es el mes
 * de calendario que tiene quien nunca lo tocó), el horizonte es **el arranque del período siguiente**,
 * el día después de [ultimoDiaDelPeriodo]: el día que el dueño dijo que empieza su mes es el que la
 * caja con salario ya miraba (`fechaEsperadaDelIngreso`). Es la única regla nueva de la vuelta: sin
 * ella, quien eligió «25 · es el día que me pagan» veía la caja «hasta el 28 de nov».
 *
 * @param delSiguiente las filas de [checklistDelPeriodo] del período siguiente.
 * @param conElPeriodoElegido el dueño eligió su período (`!PeriodSettings.esMesDeCalendario`).
 * @return `null` si las fechas no se entienden.
 */
fun horizonteDeLaCaja(
    hoy: String,
    ultimoDiaDelPeriodo: String,
    delSiguiente: List<PagoDelPeriodo>,
    conElPeriodoElegido: Boolean = false,
): HorizonteDeLaCaja? {
    val desde = runCatching { LocalDate.parse(hoy) }.getOrNull() ?: return null
    val cierre = runCatching { LocalDate.parse(ultimoDiaDelPeriodo) }.getOrNull() ?: return null
    val salario = delSiguiente
        .filter { esIngresoEsperado(it) }
        .mapNotNull { fila -> runCatching { LocalDate.parse(fila.vence) }.getOrNull()?.let { fila to it } }
        .filter { (_, fecha) -> fecha > cierre && fecha >= desde }
        .minByOrNull { (_, fecha) -> fecha }
    if (salario != null) return HorizonteDeLaCaja(salario.second, salario.first)
    val queNoCuenta = delSiguiente.any { it.esIngreso && !it.pagado && !it.esAbono && !esIngresoEsperado(it) }
    if (conElPeriodoElegido) {
        return HorizonteDeLaCaja(cierre.plus(1, DateTimeUnit.DAY), null, salarioQueNoCuenta = queNoCuenta, alPeriodoSiguiente = true)
    }
    val treinta = desde.plus(DIAS_DE_LA_CAJA_SIN_SALARIO, DateTimeUnit.DAY)
    return HorizonteDeLaCaja(if (treinta > cierre) treinta else cierre, null, salarioQueNoCuenta = queNoCuenta)
}

/** Un ingreso recurrente que todavía no llega, en pesos y con monto: el que puede cerrar el horizonte. */
private fun esIngresoEsperado(fila: PagoDelPeriodo): Boolean =
    fila.esIngreso && !fila.pagado && !fila.montoEsSaldo && !fila.esAbono && fila.moneda == "COP" && fila.monto > 0L

/**
 * **Las filas que la caja proyecta**: las del período, enteras (la caja de siempre), y las del
 * siguiente que vencen dentro del horizonte (sus pagos y su salario). Una fila sin fecha legible del
 * período siguiente no entra: no hay cómo saber si cae adentro.
 */
fun filasDeLaCaja(delPeriodo: List<PagoDelPeriodo>, delSiguiente: List<PagoDelPeriodo>, hasta: String): List<PagoDelPeriodo> {
    // ISO y no `LocalDate`: el server la llama y no ve los tipos de `kotlinx-datetime`.
    val limite = runCatching { LocalDate.parse(hasta) }.getOrNull() ?: return delPeriodo
    return delPeriodo + delSiguiente.filter { fila ->
        runCatching { LocalDate.parse(fila.vence) }.getOrNull()?.let { it <= limite } ?: false
    }
}

/**
 * **La caja hasta que te paguen**: la función que usan Pagos, «Tu día más justo» de Inicio y Movi AI
 * (`proyectar_caja`). Junta [horizonteDeLaCaja], [filasDeLaCaja] y [cajaProyectada].
 *
 * @param enLasCuentasHoy lo que hay hoy en las cuentas de Tu plata, **sin restar los apartados**:
 *   `patrimonioDe(…).tuPlata + patrimonioDe(…).apartado`. Un apartado no mueve plata.
 * @param delPeriodo las filas de [checklistDelPeriodo] del período en curso.
 * @param delSiguiente las del período siguiente, sobre las MISMAS respuestas.
 * @param ahorro el ahorro del período en pesos (`PlataDelDisponible.ahorroDelPeriodo`).
 * @param apartadoFueraDe lo apartado que no cubre ningún fijo de las filas que se le pasan: en la app
 *   y en el server es [apartadoFueraDeLosFijos] con las cuentas y los apartados del dueño. Recibe las
 *   filas que la caja de verdad resta (las del período y las del siguiente dentro del horizonte), así
 *   que un apartado que es un fijo nunca se resta dos veces, tampoco el del mes que viene.
 *
 * **Invariantes** (las prueba `CajaHastaQueTePaguenTest`): hoy, lo libre = lo de las cuentas − el
 * ahorro − lo apartado fuera de los fijos, que es Tu plata + lo apartado que cubre un fijo − el
 * ahorro; el ahorro no cambia el saldo de ningún día.
 *
 * @param conElPeriodoElegido vuelta 75: sin salario, llegar al arranque del período siguiente (ver
 *   [horizonteDeLaCaja]). La app y el server pasan `!PeriodSettings.esMesDeCalendario`.
 */
fun cajaHastaQueTePaguen(
    enLasCuentasHoy: Long,
    delPeriodo: List<PagoDelPeriodo>,
    delSiguiente: List<PagoDelPeriodo>,
    gastoDiario: Long?,
    hoy: String,
    ultimoDiaDelPeriodo: String,
    ahorro: Long = 0L,
    conElPeriodoElegido: Boolean = false,
    apartadoFueraDe: (List<PagoDelPeriodo>) -> Long = { 0L },
): CajaProyectada? {
    val horizonte = horizonteDeLaCaja(hoy, ultimoDiaDelPeriodo, delSiguiente, conElPeriodoElegido) ?: return null
    val filas = filasDeLaCaja(delPeriodo, delSiguiente, horizonte.hasta.toString())
    return cajaProyectada(
        tuPlataHoy = enLasCuentasHoy,
        pagos = filas,
        gastoDiario = gastoDiario,
        hoy = hoy,
        ultimoDia = horizonte.hasta.toString(),
        ahorro = ahorro,
        apartado = apartadoFueraDe(filas),
        salario = horizonte.salario,
    )?.copy(salarioQueNoCuenta = horizonte.salarioQueNoCuenta, hastaElPeriodoSiguiente = horizonte.alPeriodoSiguiente)
}

// ── El gasto del día a día ───────────────────────────────────────────────────

/**
 * El gasto variable de un período cerrado (ver [gastoVariablePorDia]): cuánto fue y en cuántos días.
 *
 * @property periodo el prefijo del período, `"2026-09"` (ver [PeriodoFinanciero.prefijo]).
 */
@Serializable
data class GastoVariableDeUnPeriodo(
    val periodo: String,
    val total: Long,
    val dias: Int,
)

/**
 * **El supuesto del gasto del día a día**, con lo que lo respalda.
 *
 * @property porDia el promedio diario, o `null` si no hay ni un período cerrado con qué estimarlo.
 * @property periodos los períodos con los que se calculó, del más reciente al más viejo.
 */
@Serializable
data class GastoDelDiaADia(
    val porDia: Long? = null,
    val periodos: List<GastoVariableDeUnPeriodo> = emptyList(),
)

/** Cuántos períodos cerrados se promedian como mucho: los últimos tres. */
const val PERIODOS_PARA_EL_GASTO_DIARIO = 3

/**
 * El promedio diario del gasto variable de los últimos períodos cerrados.
 *
 * **Total sobre días, no promedio de promedios**: un período de 31 días pesa más que uno de 28,
 * que es lo que pasó en la vida real. Solo cuentan los que [cerrados] trae —el server deja afuera
 * un período en el que Movi todavía no llevaba las cuentas del dueño, porque su gasto saldría
 * bajísimo sin serlo—, como mucho [PERIODOS_PARA_EL_GASTO_DIARIO], los más recientes primero.
 *
 * @param cerrados del más reciente al más viejo.
 */
fun gastoDelDiaADia(cerrados: List<GastoVariableDeUnPeriodo>): GastoDelDiaADia {
    val usados = cerrados.filter { it.dias > 0 }.take(PERIODOS_PARA_EL_GASTO_DIARIO)
    val dias = usados.sumOf { it.dias }
    if (dias <= 0) return GastoDelDiaADia()
    return GastoDelDiaADia(porDia = usados.sumOf { it.total.coerceAtLeast(0L) } / dias, periodos = usados)
}
