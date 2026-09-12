package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
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
) {
    init {
        require(cutoffDay in 1..31) { "El día de corte va de 1 a 31" }
    }

    /**
     * `true` cuando el corte es el default **y nadie movió ningún período a mano**: ahí todo se
     * comporta exactamente como el mes de calendario.
     *
     * El segundo término no es cosmético. Con un inicio propio declarado, la ventana ya no es la
     * del mes civil aunque el corte siga siendo 1, y quien lea solo `cutoffDay` para decidir
     * «esto es el mes de siempre» se saltearía la excepción.
     */
    val esMesDeCalendario: Boolean get() = cutoffDay == 1 && iniciosPropios.isEmpty()
}

/**
 * Un período concreto, identificado por el mes que le da nombre (el mes en que **termina**).
 *
 * `year`/`month` son los del nombre, no los del arranque: con corte 26, la ventana que empieza el
 * 26 de agosto de 2026 es `PeriodoFinanciero(2026, 9)`.
 */
@Serializable
data class PeriodoFinanciero(val year: Int, val month: Int) {
    init {
        require(month in 1..12) { "Mes fuera de rango" }
    }

    /** «2026-09» — la misma forma que ya usaban las funciones de mes, para poder convivir. */
    val prefijo: String get() = "$year-" + month.toString().padStart(2, '0')
}

/** Cuántos días tiene un mes, contando bisiestos. */
private fun diasDelMes(year: Int, month: Int): Int {
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
 * **El día en que arrancó un período**: el del corte, salvo que el dueño haya dicho otro.
 *
 * Es el único lugar donde la excepción de [PeriodSettings.iniciosPropios] se mira, y por eso todo
 * lo demás —la ventana, en qué período cae una fecha, el rango que se lee en pantalla— la respeta
 * sin saber que existe.
 *
 * **Un inicio propio que no se entiende se ignora**, y son dos casos: una cadena que no es fecha, y
 * una fecha que cae fuera del mes en que ese período podría arrancar. Lo segundo importa más de lo
 * que parece: con corte 25, «septiembre» arranca en agosto, así que declararle un inicio en octubre
 * no es correr un borde, es partir la línea de tiempo — dejaría días sin período y días en dos.
 * Ante un dato así se vuelve a la regla del corte, que siempre da una ventana sana.
 */
fun inicioDelPeriodo(periodo: PeriodoFinanciero, settings: PeriodSettings): LocalDate {
    val porCorte = inicioPorCorte(periodo, settings.cutoffDay)
    val declarado = settings.iniciosPropios[periodo.prefijo]
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return porCorte
    // El mes en el que ese período puede arrancar es el de `porCorte` y ningún otro.
    return if (declarado.year == porCorte.year && declarado.month == porCorte.month) declarado
    else porCorte
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
    val fin = inicioDelPeriodo(periodoSiguiente(periodo), settings).atStartOfDayIn(zona)
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

    val porElCorte = periodoPorElCorte(fecha, settings.cutoffDay)
    if (settings.iniciosPropios.isEmpty()) return porElCorte

    // **Con inicios propios, el candidato del corte puede estar corrido.** Un período que arrancó
    // antes se lleva días del anterior; uno que arrancó después se los devuelve. El desplazamiento
    // no puede pasar de un mes —un inicio propio vive en el mismo mes que su arranque natural, ver
    // [inicioDelPeriodo]— así que alcanza con mirar al vecino de cada lado y quedarse con el que de
    // verdad contiene la fecha.
    //
    // Se pregunta por la VENTANA en vez de recalcular la regla: así esta función y [ventanaDe]
    // nunca pueden discrepar sobre dónde está un borde.
    val millis = fecha.atStartOfDayIn(AppTimeZone.zone).toEpochMilliseconds()
    val candidatos = listOf(periodoAnterior(porElCorte), porElCorte, periodoSiguiente(porElCorte))
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

/** El período anterior a [periodo]. */
fun periodoAnterior(periodo: PeriodoFinanciero): PeriodoFinanciero =
    if (periodo.month == 1) PeriodoFinanciero(periodo.year - 1, 12)
    else PeriodoFinanciero(periodo.year, periodo.month - 1)

/** El período siguiente a [periodo]. */
fun periodoSiguiente(periodo: PeriodoFinanciero): PeriodoFinanciero =
    if (periodo.month == 12) PeriodoFinanciero(periodo.year + 1, 1)
    else PeriodoFinanciero(periodo.year, periodo.month + 1)

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** «septiembre de 2026» — para encabezados. */
fun nombreDe(periodo: PeriodoFinanciero): String =
    "${MESES[periodo.month - 1]} de ${periodo.year}"

/**
 * «Del 26 de agosto al 25 de septiembre» — la explicación que hace entendible un corte que no es
 * el 1. Con corte 1 devuelve `null`: no hay nada que aclarar sobre un mes de calendario.
 */
fun rangoLegibleDe(periodo: PeriodoFinanciero, settings: PeriodSettings): String? {
    if (settings.esMesDeCalendario) return null
    val zona = AppTimeZone.zone
    val v = ventanaDe(periodo, settings)
    val inicio = Instant.fromEpochMilliseconds(v.first).toLocalDateTime(zona).date
    // El último día incluido es el anterior al arranque del siguiente.
    val ultimo = Instant.fromEpochMilliseconds(v.last).toLocalDateTime(zona).date
    return "Del ${inicio.dayOfMonth} de ${MESES[inicio.month.number - 1]} " +
        "al ${ultimo.dayOfMonth} de ${MESES[ultimo.month.number - 1]}"
}
