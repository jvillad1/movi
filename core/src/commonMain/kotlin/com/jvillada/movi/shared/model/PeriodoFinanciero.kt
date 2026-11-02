package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * El **período financiero** del dueño: la ventana sobre la que Movi cuenta ingresos, gastos y
 * presupuestos.
 *
 * ### De dónde sale
 *
 * El dueño pidió que la app tenga «una fecha de inicio y corte periódicas» en vez del mes de
 * calendario, y la evidencia estaba en sus propios datos: su salario está registrado el **26 de
 * agosto** y se llama **«Salario Septiembre 2026»**. Para él septiembre ya empezó mientras la app
 * le sumaba «gastos de agosto». Con meses de calendario esa cuenta no cuadra nunca — el mes que
 * él vive arranca el día que le pagan.
 *
 * ### Cómo se nombra
 *
 * Un período **se llama por el mes en el que termina**, que es el mes que el dueño está viviendo.
 * Con corte 26, la ventana del 26 de agosto al 25 de septiembre es **«septiembre»** — igual que su
 * «Salario Septiembre» cobrado en agosto.
 *
 * Con corte 1 (el valor por defecto) la ventana va del 1 al último día del mes y se llama por ese
 * mismo mes: **es exactamente el mes de calendario**. Eso no es casualidad, es la garantía de
 * compatibilidad: mientras nadie cambie el corte, todas las cifras de la app siguen dando lo
 * mismo que antes.
 */
@Serializable
data class PeriodSettings(
    /**
     * Día del mes en que arranca cada período, de 1 a 31.
     *
     * Se guarda **por usuario en el server** para que el teléfono y la web digan el mismo mes: si
     * viviera solo en el dispositivo, el dueño vería un corte en su celular y otro en el
     * navegador, que es la clase de contradicción que Movi viene eliminando.
     *
     * Por defecto **1** — o sea, mes de calendario, el comportamiento de siempre. Un usuario que
     * no lo toque no ve ningún cambio.
     */
    val cutoffDay: Int = 1,
    /**
     * **Los períodos que arrancaron otro día**, por excepción y uno por uno: `"2026-09"` → la
     * fecha ISO en que ese período empezó de verdad.
     *
     * ### Por qué un día fijo no alcanza
     *
     * El dueño lo pidió con todas las letras: *«puede indicar el comienzo de un nuevo periodo que
     * implícitamente indica el cierre del anterior en cualquier momento, esto porque no siempre
     * los pagos suceden misma fecha y puede que un mes dure más o menos el periodo»*.
     *
     * Y es cierto para él: [cutoffDay] vale 25 porque ahí le suele caer el salario, pero un 25 que
     * cae domingo se paga el 24 o el 26. Con un día fijo, el mes que se corrió arrastra el error a
     * todas las cifras de dos períodos — el salario queda del lado equivocado del corte, que es
     * exactamente el problema que el corte vino a resolver.
     *
     * ### Es una excepción, no un modo
     *
     * El mapa está **vacío casi siempre**, y lo que no está en él sale de [cutoffDay] como antes.
     * Eso importa: no hay dos maneras de calcular un período compitiendo, hay una regla y una
     * lista de excepciones declaradas a mano. Un período con inicio propio **mueve también el
     * final del anterior**, porque el final de uno es el arranque del que sigue — que es
     * literalmente lo que el dueño describió: «el comienzo de un nuevo periodo implícitamente
     * indica el cierre del anterior».
     *
     * ### Qué pasa con un valor imposible
     *
     * Una fecha que no se entiende, o que caería fuera del mes que le da nombre, **se ignora** y
     * ese período vuelve a la regla del corte. Ver [inicioDelPeriodo]. Es a propósito: un dato
     * roto no puede hacer desaparecer movimientos de la vista ni partir la línea de tiempo en dos.
     */
    val iniciosPropios: Map<String, String> = emptyMap(),
    /**
     * **Qué pasa cuando el día de corte cae en fin de semana.** Ver [SiElCorteCaeEnFinDeSemana].
     *
     * El dueño (25-oct-2026): *«El pago siempre me lo hacen el 25 de cada mes, pero cuando el 25
     * cae en fin de semana me lo hacen el viernes antes del 25»*. Antes eso se declaraba a mano mes
     * por mes en [iniciosPropios]; con esta regla el período lo sabe solo, y [iniciosPropios] queda
     * para lo que de verdad es una excepción (el salario que llegó un jueves sin razón).
     *
     * Por defecto [SiElCorteCaeEnFinDeSemana.ESE_DIA]: el comportamiento de siempre. Un usuario que
     * no lo toque no ve ningún cambio.
     */
    val siCaeEnFinDeSemana: SiElCorteCaeEnFinDeSemana = SiElCorteCaeEnFinDeSemana.ESE_DIA,
    /**
     * Vuelta 105 · **cada cuánto arranca un período**: cada mes (lo de siempre) o cada quincena.
     * Diseño: `docs/superpowers/specs/2026-11-02-periodo-quincenal-semanal-design.md`.
     *
     * Con [FrecuenciaDelPeriodo.QUINCENAL], [cutoffDay] es el primer día de la quincena (A) y
     * [segundoDia] el segundo (B). Un ajuste quincenal incompleto o imposible (sin [segundoDia], o con
     * dos días que no dejan dos quincenas sanas, ver [esQuincenaValida]) **se lee como mensual**: un
     * dato roto nunca deja la app sin período. Por eso toda la lógica pregunta [esQuincenal] y no
     * mira este campo suelto.
     *
     * Va al final del constructor para que ninguna construcción posicional de antes cambie de sentido.
     */
    val frecuencia: FrecuenciaDelPeriodo = FrecuenciaDelPeriodo.MENSUAL,
    /** QUINCENAL: el segundo día de la quincena (B), con A < B. «El último día del mes» es 31. */
    val segundoDia: Int? = null,
) {
    init {
        require(cutoffDay in 1..31) { "El día de corte va de 1 a 31" }
    }

    /**
     * `true` solo con una quincena **completa y sana**: [frecuencia] quincenal, [segundoDia] dicho y
     * los dos días a unas dos semanas en cualquier mes ([esQuincenaValida]). Cualquier otra cosa se
     * comporta como el mes de siempre.
     */
    val esQuincenal: Boolean
        get() = frecuencia == FrecuenciaDelPeriodo.QUINCENAL && segundoDia != null &&
            esQuincenaValida(cutoffDay, segundoDia)

    /**
     * `true` cuando el corte es el default **y nadie movió ningún período a mano**: ahí todo se
     * comporta exactamente como el mes de calendario.
     *
     * El segundo término no es cosmético. Con un inicio propio declarado, la ventana ya no es la
     * del mes civil aunque el corte siga siendo 1, y quien lea solo `cutoffDay` para decidir
     * «esto es el mes de siempre» se saltearía la excepción. Una quincena nunca es el mes.
     */
    val esMesDeCalendario: Boolean
        get() = !esQuincenal && cutoffDay == 1 && iniciosPropios.isEmpty() &&
            siCaeEnFinDeSemana == SiElCorteCaeEnFinDeSemana.ESE_DIA
}

/**
 * Vuelta 105 · **cada cuánto arranca el período.** Viaja como el nombre (`UserProfile.periodFrequency`,
 * un `String`): un valor que no se conozca se lee como [MENSUAL] ([frecuenciaDelPeriodoDe]). La semana
 * queda para después (el diseño la deja para cuando haya recurrentes semanales, D9).
 */
@Serializable
enum class FrecuenciaDelPeriodo {
    /** Un período por mes, con el día de corte. Lo de siempre y el default. */
    MENSUAL,
    /** Dos períodos por mes: del día A al día antes del B, y del B al día antes del A del mes siguiente. */
    QUINCENAL,
}

/** El valor guardado, o [FrecuenciaDelPeriodo.MENSUAL] si falta o no se entiende. */
fun frecuenciaDelPeriodoDe(nombre: String?): FrecuenciaDelPeriodo =
    FrecuenciaDelPeriodo.entries.firstOrNull { it.name == nombre } ?: FrecuenciaDelPeriodo.MENSUAL

/** «El 15 y el último día del mes»: la quincena que se propone cuando alguien elige «Cada quincena». */
const val PRIMER_DIA_DE_LA_QUINCENA_POR_DEFECTO: Int = 15
const val SEGUNDO_DIA_DE_LA_QUINCENA_POR_DEFECTO: Int = 31

/**
 * Cuántos días como mínimo separan dos arranques de quincena, en cualquier mes (también febrero, donde
 * el 31 se recorta al 28). Con 13 días, ni el corrimiento por festivo (4 días como mucho, Semana Santa)
 * ni una excepción a mano ([MARGEN_DE_LA_EXCEPCION_QUINCENAL]) pueden cruzar dos arranques: no queda
 * nunca un día sin período ni una quincena vacía (13 > 5 + 5 y 13 > 5 + 4).
 */
const val DIAS_MINIMOS_ENTRE_QUINCENAS: Int = 13

/**
 * **¿Los días [a] y [b] arman dos quincenas sanas en cualquier mes?** A < B, y tanto del A al B como
 * del B al A del mes siguiente pasan al menos [DIAS_MINIMOS_ENTRE_QUINCENAS] días, contando el
 * recorte de un día que no existe en ese mes (el 31 en febrero es el 28). Pura; el server la usa para
 * rechazar y la app para no ofrecer lo que se rechazaría.
 *
 * «El 15 y el último» (15, 31), «el 1 y el 16», «el 5 y el 20», «el 10 y el 25» pasan; «el 16 y el
 * último» no (en febrero quedan 12 días del 16 al 28).
 */
fun esQuincenaValida(a: Int, b: Int): Boolean {
    if (a !in 1..31 || b !in 1..31 || a >= b) return false
    return (28..31).all { largo ->
        val diaA = minOf(a, largo)
        val diaB = minOf(b, largo)
        diaB - diaA >= DIAS_MINIMOS_ENTRE_QUINCENAS && largo - diaB + a >= DIAS_MINIMOS_ENTRE_QUINCENAS
    }
}

/**
 * Vuelta 105 · **qué tiene de malo una quincena con los días [a] y [b]**, o `null` si está bien. La
 * misma frase en el server (`PUT /api/users/me` la devuelve con un 400) y en la hoja, que apaga
 * «Guardar» con ella.
 */
fun problemaDeLaQuincena(a: Int, b: Int?): String? = when {
    b == null -> "Falta el segundo día de la quincena"
    a !in 1..31 || b !in 1..31 -> "Los días de la quincena van de 1 a 31"
    a >= b -> "El primer día de la quincena va antes que el segundo"
    !esQuincenaValida(a, b) -> "Los dos días tienen que quedar a unas dos semanas uno del otro, también en febrero"
    else -> null
}

/**
 * Cuántos días puede correrse a mano el arranque de una quincena (una excepción de
 * [PeriodSettings.iniciosPropios]): hasta 5 antes o después del día nominal. En el mensual sigue siendo
 * «dentro del mismo mes», como siempre.
 */
const val MARGEN_DE_LA_EXCEPCION_QUINCENAL: Int = 5

/**
 * Vuelta 105 · **el mes de referencia** (diseño §2.6): los ajustes con los que se cuentan las
 * obligaciones MENSUALES —recurrentes, cuotas, tarjetas, sellos, emparejamiento—, que siguen
 * venciendo un día del mes aunque el período sea una quincena.
 *
 * - Mensual → **los mismos ajustes** (identidad: nada cambia para quien no eligió quincena).
 * - Quincenal → un mes con corte en el día A, la misma regla del fin de semana y sin excepciones:
 *   las dos quincenas de un mes caen enteras dentro de él.
 *
 * Idempotente: el mes de referencia de un mes de referencia es él mismo. Por eso las funciones de
 * obligaciones la aplican al entrar y quien llama les pasa siempre los ajustes reales.
 */
fun PeriodSettings.mesDeReferencia(): PeriodSettings =
    if (!esQuincenal) this else PeriodSettings(cutoffDay = cutoffDay, siCaeEnFinDeSemana = siCaeEnFinDeSemana)

/**
 * **El límite de un presupuesto mensual medido en [periodo]** (D2 del diseño: se escribe al mes y se
 * mide prorrateado). Mensual: el mismo límite. Quincenal: la mitad, y la quincena del día A se lleva
 * el peso impar, así las dos quincenas de un mes suman exacto el mensual.
 */
fun limiteDelPeriodo(mensual: Long, periodo: PeriodoFinanciero, settings: PeriodSettings): Long {
    if (!settings.esQuincenal) return mensual
    val mitad = mensual / 2
    return if (esLaQuincenaDelDiaA(periodo, settings)) mensual - mitad else mitad
}

/**
 * Vuelta 105 · **los presupuestos mensuales medidos en [periodo]** ([limiteDelPeriodo] a cada uno). Sin
 * período o con un mes, la misma lista: nada cambia para quien cuenta por mes.
 */
fun presupuestosDelPeriodo(presupuestos: List<Budget>, periodo: PeriodoFinanciero?, settings: PeriodSettings): List<Budget> =
    if (periodo == null || !settings.esQuincenal) presupuestos
    else presupuestos.map { it.copy(monthlyLimit = limiteDelPeriodo(it.monthlyLimit, periodo, settings)) }

/** ¿[periodo] es la quincena que arranca el día A (la primera del mes)? Falso fuera de la quincena. */
fun esLaQuincenaDelDiaA(periodo: PeriodoFinanciero, settings: PeriodSettings): Boolean {
    if (!settings.esQuincenal) return false
    val nominal = nominalDeLaQuincena(periodo, settings)
    return nominal.dayOfMonth == minOf(settings.cutoffDay, diasDelMes(nominal.year, nominal.monthNumber))
}

/**
 * **Cuándo arranca el período si el día de corte cae sábado, domingo o festivo.** El banco no paga
 * esos días: el salario del 25 que cae domingo entra el viernes antes (así le pasa al dueño) o el
 * lunes después (así le pasa a otros). Un festivo de Colombia cuenta igual: el 25 de diciembre se
 * paga el 24 (el dueño lo confirmó el 25-oct-2026). Ver [esDiaHabilEnColombia].
 *
 * Viaja como el nombre del valor (`UserProfile.periodWeekendRule`); un valor que no se conozca se lee
 * como [ESE_DIA] (ver [siElCorteCaeEnFinDeSemanaDe]).
 */
@Serializable
enum class SiElCorteCaeEnFinDeSemana {
    /** El período arranca el día de corte aunque sea sábado o domingo. Lo de siempre. */
    ESE_DIA,
    /**
     * El período arranca el día hábil anterior: un corte 25 que cae domingo arranca el viernes 23, y
     * el 25 de diciembre (festivo) el 24.
     */
    DIA_HABIL_ANTERIOR,
    /** El período arranca el día hábil siguiente: un corte 25 que cae sábado arranca el lunes 27. */
    DIA_HABIL_SIGUIENTE,
}

/** El valor guardado, o [SiElCorteCaeEnFinDeSemana.ESE_DIA] si falta o no se entiende. */
fun siElCorteCaeEnFinDeSemanaDe(nombre: String?): SiElCorteCaeEnFinDeSemana =
    SiElCorteCaeEnFinDeSemana.entries.firstOrNull { it.name == nombre } ?: SiElCorteCaeEnFinDeSemana.ESE_DIA

/**
 * **El día de corte con la regla del fin de semana aplicada**: [fecha] si es día hábil o si la regla
 * es [SiElCorteCaeEnFinDeSemana.ESE_DIA]; si no, el día hábil anterior o el siguiente, saltando
 * sábados, domingos y festivos de Colombia (el lunes festivo después de un fin de semana corre el
 * pago hasta el viernes).
 *
 * El corrimiento es de unos pocos días (cuatro en el peor caso, Semana Santa), y eso lo vuelve
 * seguro: los arranques de dos períodos seguidos siguen a casi un mes de distancia, así que no
 * puede quedar un día sin período ni uno en dos.
 */
fun corteCorrido(fecha: LocalDate, regla: SiElCorteCaeEnFinDeSemana): LocalDate {
    val paso = when (regla) {
        SiElCorteCaeEnFinDeSemana.ESE_DIA -> return fecha
        SiElCorteCaeEnFinDeSemana.DIA_HABIL_ANTERIOR -> -1
        SiElCorteCaeEnFinDeSemana.DIA_HABIL_SIGUIENTE -> 1
    }
    var dia = fecha
    while (!esDiaHabilEnColombia(dia)) dia = dia.plus(paso, DateTimeUnit.DAY)
    return dia
}

/**
 * Un período concreto, identificado por el mes que le da nombre (el mes en que **termina**).
 *
 * `year`/`month` son los del nombre, no los del arranque: con corte 26, la ventana que empieza el
 * 26 de agosto de 2026 es `PeriodoFinanciero(2026, 9)`.
 */
@Serializable
data class PeriodoFinanciero(
    val year: Int,
    val month: Int,
    /**
     * Vuelta 105 · `0` = un período **mensual** (o un mes a secas: créditos, apartados y patrimonio usan
     * este tipo como mes). Una quincena se identifica por el día de su **arranque nominal** —antes de
     * festivos y excepciones—, porque el id no puede moverse: es la clave de las excepciones y de los
     * cierres (diseño §2.3). Con el default no viaja (`encodeDefaults` apagado) y la igualdad de todo
     * lo mensual queda como estaba.
     */
    val dia: Int = 0,
) : Comparable<PeriodoFinanciero> {
    init {
        require(month in 1..12) { "Mes fuera de rango" }
        require(dia in 0..31) { "Día fuera de rango" }
    }

    /**
     * «2026-09» — la misma forma que ya usaban las funciones de mes, para poder convivir. Una quincena:
     * «2026-10-15», el día de su arranque nominal.
     */
    val prefijo: String
        get() = "$year-" + month.toString().padStart(2, '0') +
            (if (dia == 0) "" else "-" + dia.toString().padStart(2, '0'))

    /** Por año, mes y día: nunca por el [prefijo] (dentro de una frecuencia coinciden, pero no hay que depender de eso). */
    override fun compareTo(other: PeriodoFinanciero): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.dia })
}

/** Cuántos días tiene un mes, contando bisiestos. */
internal fun diasDelMes(year: Int, month: Int): Int {
    val siguiente = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    return siguiente.toEpochDays() - LocalDate(year, month, 1).toEpochDays()
}

/**
 * El día en que arranca el período `(year, month)`, con el corte **recortado al último día del mes**
 * cuando no existe.
 *
 * Un corte 31 en febrero no puede ser el 31: se usa el 28 (o el 29). Sin este recorte, el dueño
 * que elige 31 se queda sin período en cuatro meses del año.
 */
private fun inicioDe(year: Int, month: Int, cutoffDay: Int): LocalDate =
    LocalDate(year, month, minOf(cutoffDay, diasDelMes(year, month)))

/**
 * **El día en que arrancó un período**: el del corte —corrido si cae en fin de semana, ver
 * [PeriodSettings.siCaeEnFinDeSemana]—, salvo que el dueño haya dicho otro.
 *
 * Es el único lugar donde la excepción de [PeriodSettings.iniciosPropios] y la regla del fin de
 * semana se miran, y por eso todo lo demás —la ventana, en qué período cae una fecha, el rango que
 * se lee en pantalla, el día en que se espera el salario— las respeta sin saber que existen.
 *
 * **La excepción gana sobre la regla**: el salario de septiembre de 2026 entró el jueves 24 sin que
 * el 25 fuera fin de semana, y ese arranque declarado a mano tiene que seguir valiendo.
 *
 * **Un inicio propio que no se entiende se ignora**, y son dos casos: una cadena que no es fecha, y
 * una fecha que cae fuera del mes en que ese período podría arrancar. Lo segundo importa más de lo
 * que parece: con corte 25, «septiembre» arranca en agosto, así que declararle un inicio en octubre
 * no es correr un borde, es partir la línea de tiempo — dejaría días sin período y días en dos.
 * Ante un dato así se vuelve a la regla del corte, que siempre da una ventana sana.
 */
fun inicioDelPeriodo(periodo: PeriodoFinanciero, settings: PeriodSettings): LocalDate {
    if (settings.esQuincenal) return inicioDeLaQuincena(periodo, settings)
    val periodo = comoMes(periodo, settings)
    val porCorte = inicioPorCorte(periodo, settings.cutoffDay)
    val porLaRegla = corteCorrido(porCorte, settings.siCaeEnFinDeSemana)
    val declarado = settings.iniciosPropios[periodo.prefijo]
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return porLaRegla
    // El mes en el que ese período puede arrancar es el del corte sin correr y ningún otro: la
    // regla del fin de semana no cambia qué excepciones son válidas.
    return if (declarado.year == porCorte.year && declarado.month == porCorte.month) declarado
    else porLaRegla
}

/**
 * Un id de quincena («2026-10-15») leído con ajustes mensuales: el período mensual que contiene ese
 * día. Solo pasa si alguien cambió de frecuencia y quedó un id viejo (en una URL, un cierre guardado):
 * se responde algo sano en vez de reventar. Un id mensual vuelve tal cual.
 */
private fun comoMes(periodo: PeriodoFinanciero, settings: PeriodSettings): PeriodoFinanciero {
    if (periodo.dia == 0) return periodo
    val fecha = LocalDate(periodo.year, periodo.month, minOf(periodo.dia, diasDelMes(periodo.year, periodo.month)))
    return periodoPorElCorte(fecha, settings.cutoffDay)
}

// ── La quincena ────────────────────────────────────────────────────────────────────────────────

/** Los dos arranques nominales de un mes: el día A y el B, cada uno recortado al largo del mes. */
private fun arranquesDelMes(year: Int, month: Int, settings: PeriodSettings): List<LocalDate> {
    val largo = diasDelMes(year, month)
    return listOf(
        LocalDate(year, month, minOf(settings.cutoffDay, largo)),
        LocalDate(year, month, minOf(settings.segundoDia ?: settings.cutoffDay, largo)),
    )
}

private fun mesAntesDe(year: Int, month: Int): Pair<Int, Int> = if (month == 1) (year - 1) to 12 else year to (month - 1)
private fun mesDespuesDe(year: Int, month: Int): Pair<Int, Int> = if (month == 12) (year + 1) to 1 else year to (month + 1)

/** El último arranque nominal en [fecha] o antes: el de la quincena que la contiene por la regla de los días. */
private fun arranqueNominalHasta(fecha: LocalDate, settings: PeriodSettings): LocalDate {
    val (a, b) = arranquesDelMes(fecha.year, fecha.monthNumber, settings)
    if (fecha >= b) return b
    if (fecha >= a) return a
    val (y, m) = mesAntesDe(fecha.year, fecha.monthNumber)
    return arranquesDelMes(y, m, settings)[1]
}

/** El primer arranque nominal estrictamente después de [fecha]. */
private fun arranqueNominalDespuesDe(fecha: LocalDate, settings: PeriodSettings): LocalDate {
    arranquesDelMes(fecha.year, fecha.monthNumber, settings).firstOrNull { it > fecha }?.let { return it }
    val (y, m) = mesDespuesDe(fecha.year, fecha.monthNumber)
    return arranquesDelMes(y, m, settings)[0]
}

/** El último arranque nominal estrictamente antes de [fecha]. */
private fun arranqueNominalAntesDe(fecha: LocalDate, settings: PeriodSettings): LocalDate {
    arranquesDelMes(fecha.year, fecha.monthNumber, settings).lastOrNull { it < fecha }?.let { return it }
    val (y, m) = mesAntesDe(fecha.year, fecha.monthNumber)
    return arranquesDelMes(y, m, settings)[1]
}

private fun quincenaQueArranca(nominal: LocalDate): PeriodoFinanciero =
    PeriodoFinanciero(nominal.year, nominal.monthNumber, nominal.dayOfMonth)

/**
 * **El arranque nominal de la quincena [periodo]**: su día, si es uno de los dos del mes. Un id que no
 * corresponde a estos ajustes (un id mensual, o una quincena de otros días tras cambiar los ajustes)
 * se lleva a la quincena que contiene ese día: nunca se revienta por un id viejo.
 */
internal fun nominalDeLaQuincena(periodo: PeriodoFinanciero, settings: PeriodSettings): LocalDate {
    val largo = diasDelMes(periodo.year, periodo.month)
    val dia = if (periodo.dia == 0) minOf(settings.cutoffDay, largo) else minOf(periodo.dia, largo)
    return arranqueNominalHasta(LocalDate(periodo.year, periodo.month, dia), settings)
}

/**
 * El arranque efectivo de una quincena: la excepción a mano si cae a ±[MARGEN_DE_LA_EXCEPCION_QUINCENAL]
 * días del nominal, si no el nominal corrido por fin de semana o festivo ([corteCorrido]). Es la misma
 * regla que el mes, con el rango de la excepción acotado a la quincena (diseño §2.4).
 */
private fun inicioDeLaQuincena(periodo: PeriodoFinanciero, settings: PeriodSettings): LocalDate {
    val nominal = nominalDeLaQuincena(periodo, settings)
    val porLaRegla = corteCorrido(nominal, settings.siCaeEnFinDeSemana)
    val declarado = settings.iniciosPropios[quincenaQueArranca(nominal).prefijo]
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return porLaRegla
    val distancia = declarado.toEpochDays() - nominal.toEpochDays()
    return if (distancia in -MARGEN_DE_LA_EXCEPCION_QUINCENAL..MARGEN_DE_LA_EXCEPCION_QUINCENAL) declarado else porLaRegla
}

/** El arranque que le tocaría por la regla del corte, sin mirar excepciones. */
private fun inicioPorCorte(periodo: PeriodoFinanciero, cutoffDay: Int): LocalDate {
    // Con corte 1 el período se llama por su propio mes; con cualquier otro arranca en el mes
    // ANTERIOR al que le da nombre (26 de agosto → «septiembre»).
    val (y, m) = if (cutoffDay == 1) {
        periodo.year to periodo.month
    } else {
        if (periodo.month == 1) (periodo.year - 1) to 12 else periodo.year to (periodo.month - 1)
    }
    return inicioDe(y, m, cutoffDay)
}

/**
 * La ventana `[inicio, fin)` del período, en instantes epoch-ms.
 *
 * El fin es **exclusivo** a propósito: el instante que abre el período siguiente no pertenece a
 * este. Con un fin inclusivo, un movimiento del último milisegundo del día de corte caería en los
 * dos períodos y se contaría dos veces.
 */
fun ventanaDe(periodo: PeriodoFinanciero, settings: PeriodSettings): LongRange {
    val zona = AppTimeZone.zone
    // **El final de un período ES el arranque del siguiente.** Escrito así, un inicio propio mueve
    // los dos bordes a la vez y no hay forma de que queden días sin período o días en dos — que es
    // lo que pasaba cuando cada borde se calculaba por su cuenta. Es también, literalmente, lo que
    // pidió el dueño: «el comienzo de un nuevo periodo implícitamente indica el cierre del
    // anterior».
    val inicio = inicioDelPeriodo(periodo, settings).atStartOfDayIn(zona)
    val fin = inicioDelPeriodo(periodoSiguiente(periodo, settings), settings).atStartOfDayIn(zona)
    return inicio.toEpochMilliseconds() until fin.toEpochMilliseconds()
}

/**
 * En qué período cae un instante.
 *
 * Es la operación que usa todo lo que hoy pregunta «¿esto es de este mes?».
 */
fun periodoDe(epochMillis: Long, settings: PeriodSettings): PeriodoFinanciero {
    val fecha = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(AppTimeZone.zone).date
    if (settings.esMesDeCalendario) return PeriodoFinanciero(fecha.year, fecha.month.number)

    val porElCorte = if (settings.esQuincenal) quincenaQueArranca(arranqueNominalHasta(fecha, settings))
    else periodoPorElCorte(fecha, settings.cutoffDay)
    if (settings.iniciosPropios.isEmpty() && settings.siCaeEnFinDeSemana == SiElCorteCaeEnFinDeSemana.ESE_DIA) {
        return porElCorte
    }

    // **Con inicios propios o con el fin de semana corrido, el candidato del corte puede estar
    // corrido.** Un período que arrancó antes se lleva días del anterior; uno que arrancó después
    // se los devuelve. El desplazamiento no puede pasar de un mes —un inicio propio vive en el mismo
    // mes que su arranque natural, ver [inicioDelPeriodo], y el fin de semana o el festivo corre
    // unos pocos días— así que alcanza con mirar al vecino de cada lado y quedarse con el que de verdad
    // contiene la fecha.
    //
    // Se pregunta por la VENTANA en vez de recalcular la regla: así esta función y [ventanaDe]
    // nunca pueden discrepar sobre dónde está un borde.
    val millis = fecha.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()
    val candidatos = listOf(periodoAnterior(porElCorte, settings), porElCorte, periodoSiguiente(porElCorte, settings))
    return candidatos.firstOrNull { millis in ventanaDe(it, settings) } ?: porElCorte
}

/** En qué período cae [fecha] por la regla del corte, sin mirar excepciones. */
private fun periodoPorElCorte(fecha: LocalDate, cutoffDay: Int): PeriodoFinanciero {
    if (cutoffDay == 1) return PeriodoFinanciero(fecha.year, fecha.month.number)
    // Antes del corte todavía se está en el período que arrancó el mes pasado, y ese período se
    // llama por el mes en curso. A partir del corte empieza el que se llama por el mes siguiente.
    val corteDeEsteMes = inicioDe(fecha.year, fecha.month.number, cutoffDay)
    return if (fecha < corteDeEsteMes) {
        PeriodoFinanciero(fecha.year, fecha.month.number)
    } else {
        if (fecha.month.number == 12) PeriodoFinanciero(fecha.year + 1, 1)
        else PeriodoFinanciero(fecha.year, fecha.month.number + 1)
    }
}

/** El período en curso. */
fun periodoActual(ahoraMillis: Long, settings: PeriodSettings): PeriodoFinanciero =
    periodoDe(ahoraMillis, settings)

/**
 * En qué período cae un día civil `"AAAA-MM-DD"` — la forma en que Movimientos tiene sus fechas.
 *
 * Se resuelve pasando por el **arranque de ese día en la zona de la app** y delegando en
 * [periodoDe], en vez de repetir acá la regla del corte. Es a propósito: la regla —«antes del corte
 * seguís en el período que arrancó el mes pasado»— ya está escrita y probada en un solo lugar, y
 * dos copias de una regla de fechas es exactamente cómo nacen los desacuerdos de un día.
 *
 * Devuelve `null` si la cadena no es una fecha. Quien llama decide qué hacer con eso; acá no se
 * inventa un período para un dato que no se entiende.
 */
fun periodoDeLaFecha(iso: String, settings: PeriodSettings): PeriodoFinanciero? {
    val fecha = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return null
    val millis = fecha.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()
    return periodoDe(millis, settings)
}

/**
 * Cuántos períodos con arranque propio acepta el server (`PUT /api/users/me`): diez años de
 * excepciones mensuales. Nadie declara más, y el tope evita que la columna crezca sola. Vive acá
 * y no en la ruta para que la app sepa de antemano qué escritura sería rechazada.
 */
const val MAX_INICIOS_PROPIOS: Int = 120

/** `"2026-09"` → el período; cualquier otra cosa → `null`. La forma de [PeriodoFinanciero.prefijo] de un mes. */
fun periodoDelPrefijo(prefijo: String): PeriodoFinanciero? {
    val partes = PREFIJO_DE_PERIODO.matchEntire(prefijo)?.groupValues ?: return null
    return PeriodoFinanciero(partes[1].toInt(), partes[2].toInt())
}

/**
 * Vuelta 105 · **el id de un período, de cualquier frecuencia**: `"2026-09"` → el mes, `"2026-10-15"`
 * → la quincena que arranca ese día; cualquier otra cosa → `null`. [periodoDelPrefijo] sigue leyendo
 * solo meses (lo usan las cosas que de verdad son un mes).
 *
 * Sin ajustes no sabe si «2026-10-15» es un arranque de quincena de este usuario: eso lo dice
 * [esPeriodoDe]. Quien recibe un id de afuera (una URL) pregunta las dos cosas.
 */
fun periodoDelId(id: String): PeriodoFinanciero? {
    periodoDelPrefijo(id)?.let { return it }
    val fecha = PREFIJO_DE_QUINCENA.matchEntire(id)?.let { runCatching { LocalDate.parse(id) }.getOrNull() }
        ?: return null
    return PeriodoFinanciero(fecha.year, fecha.monthNumber, fecha.dayOfMonth)
}

/**
 * ¿[periodo] es un período de verdad con estos ajustes? Un id mensual con ajustes mensuales; una
 * quincena cuyo día es uno de los dos arranques de su mes con ajustes quincenales. Lo usan las rutas
 * que reciben un id en la URL: uno de otra frecuencia contesta como uno que no existe.
 */
fun esPeriodoDe(periodo: PeriodoFinanciero, settings: PeriodSettings): Boolean =
    if (settings.esQuincenal) periodo.dia != 0 && nominalDeLaQuincena(periodo, settings).dayOfMonth == periodo.dia
    else periodo.dia == 0

private val PREFIJO_DE_PERIODO = Regex("""^(\d{4})-(0[1-9]|1[0-2])$""")
private val PREFIJO_DE_QUINCENA = Regex("""^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$""")

/**
 * El corte y los arranques propios de este perfil, como los usa toda cuenta de períodos. Una sola
 * construcción para que dos lugares que leen el mismo perfil no puedan terminar con ajustes
 * distintos (el corte se acota a 1..31 igual que en todas las pantallas).
 */
fun UserProfile.ajustesDelPeriodo(): PeriodSettings =
    PeriodSettings(
        cutoffDay = periodCutoffDay.coerceIn(1, 31),
        iniciosPropios = periodStarts,
        siCaeEnFinDeSemana = siElCorteCaeEnFinDeSemanaDe(periodWeekendRule),
        frecuencia = frecuenciaDelPeriodoDe(periodFrequency),
        segundoDia = periodSecondDay?.takeIf { it in 1..31 },
    )

/**
 * **El día en que se espera un ingreso recurrente que cae el día de corte**: el arranque del
 * período que ese ingreso abre, con la regla del fin de semana y las excepciones a mano; cualquier
 * otro vencimiento sale tal cual.
 *
 * Es el salario: el período arranca el día que entra, así que si el 25 cae domingo y el período
 * arranca el viernes 23, el salario del 25 también se espera el viernes 23. Sin esto Movi
 * preguntaría todo el fin de semana por un salario que ya llegó, o lo daría por atrasado un día
 * que el banco no paga. No se pregunta por el nombre de la regla: un ingreso que cae justo el día
 * del corte es el que abre el período, se llame como se llame.
 *
 * [vencimiento] es la ocurrencia por el día de la regla (el 25), la que se usa para sellarla; lo
 * que devuelve es solo cuándo se espera.
 */
fun fechaEsperadaDelIngreso(
    tipo: TransactionType,
    diaDeLaRegla: Int,
    vencimiento: LocalDate,
    settings: PeriodSettings,
): LocalDate {
    if (tipo != TransactionType.INCOME) return vencimiento
    if (settings.esQuincenal) {
        // Las DOS reglas del salario quincenal: la del día A y la del B. Cada una abre su quincena.
        if (diaDeLaRegla != settings.cutoffDay && diaDeLaRegla != settings.segundoDia) return vencimiento
        val nominal = arranqueNominalHasta(vencimiento, settings)
        if (nominal != vencimiento) return vencimiento
        return inicioDelPeriodo(quincenaQueArranca(nominal), settings)
    }
    if (diaDeLaRegla != settings.cutoffDay) return vencimiento
    val periodo = periodoPorElCorte(vencimiento, settings.cutoffDay)
    // Solo si el vencimiento ES el arranque natural de ese período (con un corte 31 recortado al
    // largo del mes, el 30 de abril también lo es); si no, la regla no abre ningún período.
    if (inicioPorCorte(periodo, settings.cutoffDay) != vencimiento) return vencimiento
    return inicioDelPeriodo(periodo, settings)
}

/**
 * Lo mismo con fechas `"AAAA-MM-DD"`, para el server, que trabaja con `java.time` y no ve los tipos
 * de `kotlinx-datetime`. Una cadena que no es fecha vuelve tal cual.
 */
fun fechaEsperadaDelIngreso(
    tipo: TransactionType,
    diaDeLaRegla: Int,
    vencimientoIso: String,
    settings: PeriodSettings,
): String {
    val vencimiento = runCatching { LocalDate.parse(vencimientoIso) }.getOrNull() ?: return vencimientoIso
    return fechaEsperadaDelIngreso(tipo, diaDeLaRegla, vencimiento, settings).toString()
}

/**
 * Estos ajustes con el arranque de [periodo] declarado en [inicio] (ISO), o **quitado** si
 * [inicio] es `null` — el resto de las excepciones queda como estaba. Es el mapa que viaja entero
 * en `UpdateProfileRequest.periodStarts`.
 */
fun PeriodSettings.conInicioPropio(periodo: PeriodoFinanciero, inicio: String?): PeriodSettings =
    copy(
        iniciosPropios = if (inicio == null) iniciosPropios - periodo.prefijo
        else iniciosPropios + (periodo.prefijo to inicio),
    )

/**
 * **«Empezar un período nuevo hoy»**: los ajustes con el período siguiente a [enCurso] arrancando
 * [hoy], o `null` cuando eso no se puede decir.
 *
 * Es cerrar el período en curso antes de tiempo —el sueldo del mes nuevo entró antes del corte—
 * y se escribe como lo que es: una excepción más en [PeriodSettings.iniciosPropios], la del
 * SIGUIENTE (el final de uno es el arranque del otro, ver [ventanaDe]).
 *
 * `null` en cuatro casos, y en ninguno se ofrece la acción:
 * - [hoy] no cae en [enCurso] con estos ajustes (el reloj del server y el del aparato no
 *   coinciden, o ya es otro período);
 * - hoy **ya es** el arranque efectivo del siguiente (con sus excepciones). Solo cuenta el
 *   efectivo: si el dueño había corrido el siguiente más tarde y el sueldo igual llegó el día del
 *   corte, empezar hoy sí cambia algo;
 * - hoy no es un arranque válido para el siguiente: [inicioDelPeriodo] lo ignoraría por caer fuera
 *   del mes permitido (con corte 1, por ejemplo, el siguiente nunca puede arrancar dentro del mes
 *   en curso);
 * - el mapa resultante pasaría [MAX_INICIOS_PROPIOS] y el server lo rechazaría.
 */
fun empezarElSiguienteHoy(enCurso: PeriodoFinanciero, hoy: LocalDate, settings: PeriodSettings): PeriodSettings? {
    if (periodoDeLaFecha(hoy.toString(), settings) != enCurso) return null
    val siguiente = periodoSiguiente(enCurso, settings)
    if (inicioDelPeriodo(siguiente, settings) == hoy) return null
    val nuevos = settings.conInicioPropio(siguiente, hoy.toString())
    // Se le pregunta a la misma regla que después calcula las ventanas: si ignoraría el valor,
    // guardarlo sería escribir una excepción que no hace nada.
    if (inicioDelPeriodo(siguiente, nuevos) != hoy) return null
    if (nuevos.iniciosPropios.size > MAX_INICIOS_PROPIOS) return null
    return nuevos
}

/**
 * El período anterior a [periodo] con estos ajustes: el mes anterior, o la quincena anterior.
 *
 * Vuelta 105 · antes no recibía ajustes porque un período era siempre un mes. Con un mensual da
 * exactamente [mesAnterior].
 */
fun periodoAnterior(periodo: PeriodoFinanciero, settings: PeriodSettings): PeriodoFinanciero =
    if (settings.esQuincenal) quincenaQueArranca(arranqueNominalAntesDe(nominalDeLaQuincena(periodo, settings), settings))
    else mesAnterior(comoMes(periodo, settings))

/** El período siguiente a [periodo] con estos ajustes. Ver [periodoAnterior]. */
fun periodoSiguiente(periodo: PeriodoFinanciero, settings: PeriodSettings): PeriodoFinanciero =
    if (settings.esQuincenal) quincenaQueArranca(arranqueNominalDespuesDe(nominalDeLaQuincena(periodo, settings), settings))
    else mesSiguiente(comoMes(periodo, settings))

/**
 * **El período con el que se compara [periodo]** («vs. período pasado», la línea del pasado en «En qué
 * se va»; D6 del diseño): el anterior en el mensual; en la quincena, **la misma quincena del mes
 * anterior** (la del 15 contra la del 15), porque los fijos grandes caen siempre en la misma y comparar
 * con la inmediata marcaría el arriendo, no la forma de gastar.
 */
fun periodoHomologo(periodo: PeriodoFinanciero, settings: PeriodSettings): PeriodoFinanciero =
    if (settings.esQuincenal) periodoAnterior(periodoAnterior(periodo, settings), settings)
    else periodoAnterior(periodo, settings)

/**
 * El **mes** anterior: para quien usa [PeriodoFinanciero] como mes (la cuota de un crédito, un
 * apartado, la curva del patrimonio). No es «el período anterior» si el período es una quincena.
 */
fun mesAnterior(mes: PeriodoFinanciero): PeriodoFinanciero =
    if (mes.month == 1) PeriodoFinanciero(mes.year - 1, 12)
    else PeriodoFinanciero(mes.year, mes.month - 1)

/** El **mes** siguiente. Ver [mesAnterior]. */
fun mesSiguiente(mes: PeriodoFinanciero): PeriodoFinanciero =
    if (mes.month == 12) PeriodoFinanciero(mes.year + 1, 1)
    else PeriodoFinanciero(mes.year, mes.month + 1)

/** El mes anterior. Vuelta 105: un período ya no es siempre un mes; usa la versión con ajustes o [mesAnterior]. */
@Deprecated("Un período puede ser una quincena", ReplaceWith("periodoAnterior(periodo, settings)"))
fun periodoAnterior(periodo: PeriodoFinanciero): PeriodoFinanciero = mesAnterior(periodo)

/** El mes siguiente. Ver [periodoAnterior]. */
@Deprecated("Un período puede ser una quincena", ReplaceWith("periodoSiguiente(periodo, settings)"))
fun periodoSiguiente(periodo: PeriodoFinanciero): PeriodoFinanciero = mesSiguiente(periodo)

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/**
 * «septiembre»: el nombre del mes que le da nombre a [periodo] (vuelta 60: «como en septiembre»).
 * Una quincena (vuelta 105) no tiene un mes que la nombre: «la quincena del 15 de octubre».
 */
fun nombreDelMesDe(periodo: PeriodoFinanciero): String =
    if (periodo.dia != 0) "la quincena del ${periodo.dia} de ${MESES[periodo.month - 1]}" else MESES[periodo.month - 1]

/** «septiembre de 2026» — para encabezados. Una quincena: «la quincena del 15 de octubre de 2026». */
fun nombreDe(periodo: PeriodoFinanciero): String =
    "${nombreDelMesDe(periodo)} de ${periodo.year}"

/**
 * «Octubre 2026» — el título de un período como se lee en una lista de períodos: el mes que le da
 * nombre con mayúscula inicial, y el año. Es la única forma de escribirlo, para que el server (que
 * lo manda en `ResumenDePeriodo.nombre`) y cualquier pantalla que lo arme a mano digan lo mismo.
 *
 * Una quincena se nombra por sus fechas con [tituloDelPeriodo] con ajustes; sin ellos, por su arranque.
 */
fun tituloDelPeriodo(periodo: PeriodoFinanciero): String =
    if (periodo.dia != 0) "Quincena del ${periodo.dia} ${MESES_CORTOS[periodo.month - 1]} ${periodo.year}"
    else MESES[periodo.month - 1].replaceFirstChar { it.uppercase() } + " " + periodo.year

/**
 * Vuelta 105 · el título con ajustes: el mensual, [tituloDelPeriodo] de siempre; una quincena, sus
 * fechas con el año («15 – 30 oct 2026»), la regla única de nombre (D8 del diseño).
 */
fun tituloDelPeriodo(periodo: PeriodoFinanciero, settings: PeriodSettings): String =
    if (!settings.esQuincenal) tituloDelPeriodo(periodo)
    else nombreDelPeriodo(periodo, settings, anioDeHoy = null) + " " + periodo.year

/**
 * «Del 26 de agosto al 25 de septiembre» — la explicación que hace entendible un corte que no es
 * el 1. Con corte 1 devuelve `null`: no hay nada que aclarar sobre un mes de calendario.
 */
fun rangoLegibleDe(periodo: PeriodoFinanciero, settings: PeriodSettings): String? {
    if (settings.esMesDeCalendario) return null
    return rangoDelPeriodo(periodo, settings)
}

/**
 * «Del 1 al 31 de octubre» — el rango de un período **siempre**, también con el mes de
 * calendario (corte 1). Lo pide Pagos (vuelta 40): la opción 3 escribe el período debajo del
 * título, y sin él la pantalla no decía de qué fechas son las facturas. [rangoLegibleDe] sigue
 * callándose con corte 1 donde el mes ya está escrito (el selector de Actividad).
 */
fun rangoDelPeriodo(periodo: PeriodoFinanciero, settings: PeriodSettings): String {
    val zona = AppTimeZone.zone
    val v = ventanaDe(periodo, settings)
    val inicio = Instant.fromEpochMilliseconds(v.first).toLocalDateTime(zona).date
    // El último día incluido es el anterior al arranque del siguiente.
    val ultimo = Instant.fromEpochMilliseconds(v.last).toLocalDateTime(zona).date
    return rangoEntre(inicio, ultimo)
}

/**
 * «Del 25 de octubre al 24 de noviembre»; dentro del mismo mes, el mes una vez: «Del 1 al 31 de
 * octubre» (revisión de la vuelta 40: «Del 1 de octubre al 31 de octubre» lo repetía). Pura.
 */
fun rangoEntre(inicio: LocalDate, ultimo: LocalDate): String =
    if (inicio.year == ultimo.year && inicio.monthNumber == ultimo.monthNumber) {
        "Del ${inicio.dayOfMonth} al ${diaLegible(ultimo)}"
    } else {
        "Del ${diaLegible(inicio)} al ${diaLegible(ultimo)}"
    }

private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/**
 * **El rango corto de un período**, para el encabezado del Inicio (vuelta 54): «23 oct – 24 nov»; dentro
 * del mismo mes, el mes una vez: «1 – 31 oct». Con un corte a mitad de mes el nombre solo («Noviembre
 * 2026») confundía: el 28 de octubre ya decía noviembre. Entra en un renglón a 360 dp, que el rango largo
 * no (vuelta de diseño 11). Pura.
 */
fun rangoCortoDelPeriodo(periodo: PeriodoFinanciero, settings: PeriodSettings): String {
    val zona = AppTimeZone.zone
    val v = ventanaDe(periodo, settings)
    val inicio = Instant.fromEpochMilliseconds(v.first).toLocalDateTime(zona).date
    val ultimo = Instant.fromEpochMilliseconds(v.last).toLocalDateTime(zona).date
    return rangoCortoEntre(inicio, ultimo)
}

/**
 * # Vuelta 75 · **el nombre del período**, uno solo en toda la app
 *
 * Para alguien con corte el 25, Inicio decía «23 oct – 24 nov», Actividad «Noviembre de 2026» y Pagos
 * «Del 23 de octubre al 24 de noviembre»: tres nombres para la misma cosa. Ahora Inicio, Actividad,
 * Pagos, Tus períodos y En qué se va lo nombran con esta función:
 *
 * - **«Octubre»** cuando el período ES el mes de calendario (arranca el 1 y termina el último día del
 *   mismo mes): ahí el mes dice todo;
 * - **«23 oct – 24 nov»** en cualquier otro caso ([rangoCortoEntre]): con un corte a mitad de mes el
 *   nombre del mes solo confunde (el 28 de octubre ya decía «noviembre»). También un período excepcional
 *   (un inicio a mano) se nombra por sus fechas;
 * - con el año al final («Octubre 2025», «25 nov – 24 dic 2025») solo si el período no es de [anioDeHoy]:
 *   en una lista de períodos, noviembre de este año y el del pasado no pueden llamarse igual.
 *
 * El año de hoy entra por parámetro (por defecto, el de la zona de la app; `null` = no se dice nunca):
 * las pruebas lo fijan.
 */
fun nombreDelPeriodo(
    periodo: PeriodoFinanciero,
    settings: PeriodSettings,
    anioDeHoy: Int? = Clock.System.now().toLocalDateTime(AppTimeZone.zone).year,
): String {
    val zona = AppTimeZone.zone
    val v = ventanaDe(periodo, settings)
    val inicio = Instant.fromEpochMilliseconds(v.first).toLocalDateTime(zona).date
    val ultimo = Instant.fromEpochMilliseconds(v.last).toLocalDateTime(zona).date
    return nombreEntre(inicio, ultimo, anio = periodo.year.takeIf { anioDeHoy != null && it != anioDeHoy })
}

/** [nombreDelPeriodo] con los dos bordes ya sabidos (el último incluido). Pura. */
fun nombreEntre(inicio: LocalDate, ultimo: LocalDate, anio: Int? = null): String {
    val esElMes = inicio.dayOfMonth == 1 && inicio.year == ultimo.year && inicio.monthNumber == ultimo.monthNumber &&
        ultimo.dayOfMonth == diasDelMes(ultimo.year, ultimo.monthNumber)
    val base = if (esElMes) MESES[inicio.monthNumber - 1].replaceFirstChar { it.uppercase() } else rangoCortoEntre(inicio, ultimo)
    return if (anio != null) "$base $anio" else base
}

/** «23 oct – 24 nov», o «1 – 31 oct» dentro del mismo mes. Pura. */
fun rangoCortoEntre(inicio: LocalDate, ultimo: LocalDate): String {
    val mesFinal = MESES_CORTOS[ultimo.month.number - 1]
    return if (inicio.year == ultimo.year && inicio.monthNumber == ultimo.monthNumber) {
        "${inicio.dayOfMonth} – ${ultimo.dayOfMonth} $mesFinal"
    } else {
        "${inicio.dayOfMonth} ${MESES_CORTOS[inicio.month.number - 1]} – ${ultimo.dayOfMonth} $mesFinal"
    }
}

/**
 * «25 de julio»: un día como se dice, sin año. Es la mitad de [rangoLegibleDe], suelta para quien
 * tiene los dos bordes por separado (las propuestas de presupuesto traen `desde` y `hasta`) y
 * quiere que las fechas se lean igual que el rango del período.
 */
fun diaLegible(fecha: LocalDate): String = "${fecha.dayOfMonth} de ${MESES[fecha.month.number - 1]}"

/** Lo mismo desde un `"AAAA-MM-DD"`; `null` si la cadena no es una fecha. */
fun diaLegible(iso: String): String? = runCatching { LocalDate.parse(iso) }.getOrNull()?.let { diaLegible(it) }

/**
 * **Desde cuándo recupera el teléfono los SMS del banco**: el arranque del período ANTERIOR al que
 * contiene [ahoraMillis], o sea el período en curso entero y el último completo.
 *
 * Antes eran 30 días fijos, y el dueño lo pidió por período: *«que puedan escanear el período entero
 * y no una cantidad de días fija»*. Solo el período en curso no alcanza: el 26 de septiembre, con
 * corte 25, sería un día de historial. Con el anterior incluido siempre se recupera al menos un
 * período completo, y el que se está cerrando llega entero.
 */
fun desdeDondeRecuperarSms(ahoraMillis: Long, settings: PeriodSettings): Long {
    val desde = ventanaDe(periodoAnterior(periodoDe(ahoraMillis, settings), settings), settings).first
    // Vuelta 105 · con quincenas, «el período anterior» son 15-30 días: se recupera al menos lo de
    // siempre (unos 35 días). Solo en la quincena: el mensual queda exactamente igual.
    if (!settings.esQuincenal) return desde
    return minOf(desde, ahoraMillis - DIAS_MINIMOS_DE_SMS_EN_QUINCENA * 86_400_000L)
}

/** Vuelta 105 · el piso de días que recupera el teléfono cuando el período es una quincena. */
const val DIAS_MINIMOS_DE_SMS_EN_QUINCENA: Int = 35
