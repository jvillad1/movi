package com.jvillada.movi.ui.fecha

import com.jvillada.movi.shared.model.EventOccurrenceMark
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * # La fecha de un movimiento
 *
 * Todo lo que hace falta para **elegir** y **mostrar** el día en que ocurrió un movimiento, sin
 * nada de Compose adentro: las conversiones (fecha civil ↔ epoch-ms), las etiquetas («Hoy»,
 * «Ayer», «23 de agosto») y el aviso que se le muestra al dueño **antes** de mover un movimiento
 * de mes. Es lógica con consecuencias sobre la plata, así que se fija por test en vez de quedar
 * escondida adentro de un `@Composable` que ningún test puede llamar.
 *
 * ## Zona horaria: todo pasa por [AppTimeZone], nunca por la del sistema
 *
 * Movi guarda epoch-ms y cada pantalla los vuelve a fechar en la zona de la app (Bogotá). Elegir
 * «23 de agosto» y sellarlo con la zona del dispositivo haría que el mismo movimiento cayera en
 * días distintos según dónde se lo mire. [AppTimeZone.zone] ya resuelve eso —y **nunca lanza**:
 * en wasmJs no existe la tabla de zonas IANA y cae al offset fijo UTC-5, que para Colombia es
 * exacto—, así que todo lo de acá la recibe como parámetro con ese default.
 */

/** Los meses en español, en minúscula: es como se leen adentro de una frase («23 de agosto»). */
internal val MESES_DEL_ANIO = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** Hoy, en la zona de la app. El valor por defecto de toda fecha que se anota. */
fun hoyEnAppZone(clock: Clock = Clock.System, zone: TimeZone = AppTimeZone.zone): LocalDate =
    clock.now().toLocalDateTime(zone).date

/** La fecha civil de un movimiento ya guardado, en la zona de la app. */
fun fechaDeEpoch(millis: Long, zone: TimeZone = AppTimeZone.zone): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date

/**
 * La fecha elegida → el instante del **mediodía** de ese día en la zona de la app.
 *
 * Mediodía y no medianoche, por el mismo motivo que ya estaba escrito en `transferTimestampFor`
 * (y que ahora vive acá, en un solo lugar, para las tres pantallas que fechan algo): un
 * movimiento sellado a las 00:00 de Bogotá se ve, en UTC, a las 05:00 del mismo día — pero uno
 * sellado a las 00:00 UTC, que es lo que da una conversión descuidada, se lee como las 7 pm del
 * día ANTERIOR en Bogotá y el movimiento aparece un día antes de cuando pasó. El mediodía deja
 * doce horas de margen para los dos lados: ninguna zona razonable lo corre de día.
 */
fun epochAlMediodia(fecha: LocalDate, zone: TimeZone = AppTimeZone.zone): Long =
    fecha.atTime(12, 0).toInstant(zone).toEpochMilliseconds()

/**
 * El epoch-ms con el que se sella un movimiento fechado en [fecha].
 *
 * **Si la fecha es hoy, se usa la hora real**, no el mediodía. No es un detalle: dentro de un día
 * la lista de Movimientos ordena por timestamp, así que sellar todo a las 12:00 dejaría los cinco
 * gastos de una misma tarde empatados y en un orden arbitrario que cambia entre pantallas. Con la
 * hora real, el caso por defecto —anotar en el momento— se comporta **exactamente igual que
 * antes** de que esta pantalla tuviera selector de fecha: es el mismo `Clock.System.now()` que ya
 * había.
 *
 * Para cualquier otro día no hay hora que preservar (nadie recuerda a qué hora fue el café de
 * ayer), y ahí el mediodía es la elección segura: ver [epochAlMediodia].
 */
fun timestampParaFecha(
    fecha: LocalDate,
    hoy: LocalDate,
    clock: Clock = Clock.System,
    zone: TimeZone = AppTimeZone.zone,
): Long = if (fecha == hoy) clock.now().toEpochMilliseconds() else epochAlMediodia(fecha, zone)

/**
 * Cómo se lee una fecha en una fila: «Hoy», «Ayer», «23 de agosto», o con año si es de otro.
 *
 * Es a propósito la misma forma que los encabezados de Movimientos (`formatDayHeading`): el dueño
 * elige «Ayer» acá y después busca su gasto bajo el encabezado «AYER». Si las dos pantallas
 * nombraran el mismo día de maneras distintas, el movimiento parecería haberse guardado en otro
 * lado.
 */
fun etiquetaDeFecha(fecha: LocalDate, hoy: LocalDate): String = when {
    fecha == hoy -> "Hoy"
    fecha == hoy.minus(DatePeriod(days = 1)) -> "Ayer"
    // El año solo se dice cuando NO es el corriente: repetir «de 2026» en 2026 es ruido.
    fecha.year != hoy.year ->
        "${fecha.dayOfMonth} de ${MESES_DEL_ANIO[fecha.monthNumber - 1]} de ${fecha.year}"
    else -> "${fecha.dayOfMonth} de ${MESES_DEL_ANIO[fecha.monthNumber - 1]}"
}

/** El nombre del mes de una fecha, para el encabezado del calendario: «agosto 2026». */
fun etiquetaDeMes(fecha: LocalDate): String =
    "${MESES_DEL_ANIO[fecha.monthNumber - 1]} ${fecha.year}"

/**
 * **No se anotan movimientos en el futuro.**
 *
 * No es un capricho: un movimiento es plata que YA se movió. Uno fechado mañana infla el saldo y
 * las cifras del mes con algo que no pasó, y encima puede engancharse como la ocurrencia de un
 * recurrente cuyo vencimiento todavía no llegó — justo lo que el server ya se niega a hacer por
 * el otro lado (ver el techo de `POST /api/recurring-rules/{id}/occurrence`: «Ese vencimiento
 * todavía no llegó: no se puede dar por ocurrido»). Esta es la misma regla, del lado del
 * movimiento. Lo que se planea y todavía no pasó ya tiene su lugar en Movi, y es Recurrentes.
 *
 * El corte es **por día civil de Bogotá**, no por instante: así un reloj adelantado unos minutos
 * —o un teléfono en otra zona— no vuelve inanotable el gasto de hoy.
 */
fun esFutura(fecha: LocalDate, hoy: LocalDate): Boolean = fecha > hoy

/**
 * Lo que hay que decirle **antes** de guardar un cambio de fecha, o `null` si no hay nada que
 * decir. Mismo criterio que `avisoDeUnificacion` en Categorías: cuando una operación mueve
 * números que el dueño puso a propósito, se avisa antes y con las palabras exactas, no después.
 *
 * Lo único que hace falta avisar es el **cambio de mes**, porque es lo único que no se ve en la
 * pantalla donde se hace: dentro del mismo mes, mover un gasto del 23 al 22 cambia dos
 * encabezados de Movimientos y nada más.
 *
 * ## Por qué el aviso solo habla del mes que el dueño PUEDE mirar
 *
 * Movi no tiene pantalla de un mes pasado: Inicio, Presupuestos y Categorías son siempre el mes
 * en curso, y no hay selector de mes en ninguna parte. Así que prometer «y empieza a contar en
 * las cifras de julio» sería cierto en la base y **invisible en la app**: lo que el dueño va a
 * observar es que la plata se fue de todos los números que tiene a la vista.
 *
 * Por eso el aviso dice lo que se ve, y dice adónde va la plata sin prometer que se pueda mirar
 * ahí. Cuando el movimiento viene **hacia** el mes en curso, en cambio, la llegada sí es
 * observable y se dice con todas las letras — por eso [enCurso] entra como parámetro y no se
 * asume.
 *
 * A diferencia de la unificación de categorías, esto **sí se puede deshacer**: se vuelve a editar
 * la fecha y todo vuelve a donde estaba. Por eso el aviso no dice «no se puede deshacer» —decirlo
 * sería mentir— y por eso alcanza con un renglón y no hace falta una confirmación aparte.
 *
 * @param enCurso el mes que la app muestra hoy (cualquier día de ese mes). Se recibe y no se lee
 *   acá para poder fijarlo por test.
 */
fun avisoDeCambioDeMes(anterior: LocalDate, nueva: LocalDate, enCurso: LocalDate): String? {
    if (mismoMes(anterior, nueva)) return null
    val desde = etiquetaDeMesEnAviso(anterior, nueva)
    val hasta = etiquetaDeMesEnAviso(nueva, anterior)
    val salia = mismoMes(anterior, enCurso)
    val llega = mismoMes(nueva, enCurso)
    return when {
        // Se va del mes que la app muestra: la plata desaparece de Inicio, Análisis y del
        // presupuesto de su categoría, y no hay ninguna pantalla donde volver a verla sumada.
        salia -> "Ojo: este movimiento pasa de $desde a $hasta. Deja de contar en las cifras y " +
            "el presupuesto de $desde, que es el mes que ves en la app."
        // Llega al mes en curso: acá sí se puede observar el efecto.
        llega -> "Ojo: este movimiento pasa de $desde a $hasta, así que empieza a contar en las " +
            "cifras y el presupuesto de $hasta, el mes que ves en la app."
        // Entre dos meses pasados: nada de lo que el dueño ve cambia, pero el movimiento se muda.
        else -> "Ojo: este movimiento pasa de $desde a $hasta. Ninguno de los dos es el mes que " +
            "ves en la app, así que las cifras de la pantalla no cambian."
    }
}

/**
 * Lo que hay que decirle **además** cuando la fecha nueva le suelta el sello de «esto ya ocurrió»
 * a un recurrente, o `null` si no lo suelta (o si no hay sello).
 *
 * El sello vive del lado del server y la ventana que lo sostiene también ([EventOccurrenceMark]):
 * acá solo se compara la fecha elegida contra los dos días que el server mandó. Eso es a
 * propósito — la regla de qué movimiento puede ser la ocurrencia de qué periodo es del
 * emparejador y no puede tener una segunda versión en el cliente.
 *
 * Es el aviso que más importa de los dos, porque es el único cuyo costo es **plata**: un periodo
 * que se queda sellado con un movimiento que no le corresponde deja de recordarse. Se suelta a
 * propósito (ver `PUT /api/events/{id}/timestamp`), y esto es lo que lo anuncia antes.
 */
fun avisoDeSelloSuelto(mark: EventOccurrenceMark?, nueva: LocalDate): String? {
    if (mark == null) return null
    val desde = runCatching { LocalDate.parse(mark.validFrom) }.getOrNull() ?: return null
    val hasta = runCatching { LocalDate.parse(mark.validTo) }.getOrNull() ?: return null
    if (nueva in desde..hasta) return null
    val mes = etiquetaDePeriodo(mark.period) ?: return null
    return "Este movimiento es el que marcaste como «ya ocurrió» de «${mark.ruleName}» en $mes. " +
        "Con esta fecha deja de servir para ese mes: la marca se suelta y Movi te lo vuelve a " +
        "recordar hasta que lo marques otra vez."
}

/** ¿Las dos fechas caen en el mismo mes civil? */
private fun mismoMes(a: LocalDate, b: LocalDate): Boolean =
    a.year == b.year && a.monthNumber == b.monthNumber

/** «2026-08» → «agosto de 2026», o `null` si el periodo no se puede leer. */
fun etiquetaDePeriodo(period: String): String? {
    val partes = period.split("-")
    if (partes.size != 2) return null
    val anio = partes[0].toIntOrNull() ?: return null
    val mes = partes[1].toIntOrNull() ?: return null
    if (mes !in 1..12) return null
    return "${MESES_DEL_ANIO[mes - 1]} de $anio"
}

/**
 * El nombre del mes tal como entra en el aviso: con año solo si los dos meses del cambio son de
 * años distintos («de diciembre de 2025 a enero de 2026»). Dentro del mismo año, «de agosto a
 * julio» se lee mejor y no pierde nada.
 */
private fun etiquetaDeMesEnAviso(mes: LocalDate, otro: LocalDate): String =
    if (mes.year == otro.year) MESES_DEL_ANIO[mes.monthNumber - 1]
    else "${MESES_DEL_ANIO[mes.monthNumber - 1]} de ${mes.year}"

/**
 * Las 42 casillas de la grilla de un mes (6 semanas × 7 días), con `null` en los huecos de antes
 * del día 1 y de después del último.
 *
 * Seis semanas **fijas** y no «las que hagan falta»: la grilla es lo más alto del selector, y una
 * que cambiara de alto al pasar de mes movería todo lo de abajo bajo el dedo — que es el pecado
 * capital de la hoja de Agregar (ver el bloque «Ola 8 · V2» en `QuickAddScreen`). Cuesta como
 * mucho una fila vacía.
 *
 * La semana empieza en **lunes**, como en Colombia.
 */
fun casillasDelMes(mes: LocalDate): List<LocalDate?> {
    val primero = LocalDate(mes.year, mes.monthNumber, 1)
    val diasDelMes = primero.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
    val huecosAdelante = primero.dayOfWeek.isoDayNumber - 1
    return List(42) { i ->
        val dia = i - huecosAdelante + 1
        if (dia in 1..diasDelMes) LocalDate(mes.year, mes.monthNumber, dia) else null
    }
}

/** El mes anterior, para la flecha izquierda del calendario. Siempre día 1. */
fun mesAnterior(mes: LocalDate): LocalDate =
    LocalDate(mes.year, mes.monthNumber, 1).minus(DatePeriod(months = 1))

/** El mes siguiente, para la flecha derecha del calendario. Siempre día 1. */
fun mesSiguiente(mes: LocalDate): LocalDate =
    LocalDate(mes.year, mes.monthNumber, 1).plus(DatePeriod(months = 1))

/**
 * El año más viejo que el calendario deja alcanzar.
 *
 * Sin piso, la flecha de «mes anterior» navega para siempre: con suficientes toques se llega a
 * 1999, y ahí el server rechaza la fecha por su guarda de cordura de año. Un control que te deja
 * llegar a un lugar donde nada funciona es un bug aunque nadie llegue nunca. El piso es el mismo
 * 2000 que rechaza el server, así que **la UI no puede producir una fecha que el server rechace**.
 */
private const val ANIO_MINIMO = 2000

/** ¿Tiene sentido ofrecer la flecha «mes anterior» estando en [mes]? Ver [ANIO_MINIMO]. */
fun puedeRetrocederMes(mes: LocalDate): Boolean = mesAnterior(mes).year >= ANIO_MINIMO

/**
 * ¿Tiene sentido ofrecer la flecha «mes siguiente» estando en [mes]?
 *
 * No, si el mes que viene es entero futuro: sería llevar al dueño a una pantalla donde todos los
 * días están apagados. El mes que contiene a hoy sí se ofrece — ahí los días de más adelante
 * quedan apagados de a uno (ver [esFutura]).
 */
fun puedeAvanzarMes(mes: LocalDate, hoy: LocalDate): Boolean =
    mesSiguiente(mes) <= LocalDate(hoy.year, hoy.monthNumber, 1)
