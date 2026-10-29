package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.MontoDelAviso
import com.jvillada.movi.shared.model.sumaDeLoPorConfirmar
import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PropuestasDeLaBandeja
import com.jvillada.movi.shared.model.SmsMessage
import com.jvillada.movi.shared.model.PresentacionDelMovimiento
import com.jvillada.movi.shared.model.comoSePresenta
import com.jvillada.movi.shared.model.esGastoDelPeriodo
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.nombreParaMostrar
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.porrevisar.AnotacionEntendida
import com.jvillada.movi.ui.porrevisar.ResultadoDelLote
import com.jvillada.movi.ui.porrevisar.loQueMoviEntendio
import com.jvillada.movi.ui.porrevisar.pagosRecientesPorRevisar
import com.jvillada.movi.ui.porrevisar.propuestaDelPago
import com.jvillada.movi.ui.porrevisar.tituloDelLote
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.roundToLong

/*
 * # Actividad por dentro (vuelta 40, la opción 3)
 *
 * Lo que la pestaña Actividad (antes Movimientos) decide sin dibujar: el encabezado de cada día
 * («Hoy · martes 28» y «Gastaste $X»), el chip «Compartidos» y la tarjeta de los avisos del banco con
 * «Confirmar los N». Todo puro, para probarlo sin Compose.
 *
 * **Sin reglas de plata nuevas.** «Gastaste» es la regla de «Gastos» de siempre ([esGastoDelPeriodo]
 * con tu parte, [montoContado]); «Entró», la de la franja Entró · Salió · Neto ([resumenDelPanel]); y
 * confirmar en lote es la confirmación de la fila ([loQueMoviEntendio] + `confirmarLoEntendido`).
 */

// ── El encabezado de un día ──────────────────────────────────────────────────────────────────────

private val MESES_DEL_DIA = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** «martes»: el nombre del día de la semana, en minúscula. */
fun nombreDelDiaDeLaSemana(dia: DayOfWeek): String = when (dia) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    else -> "domingo"
}

/**
 * **El encabezado de un día en Actividad** (vuelta 40): «Hoy · martes 28», «Ayer · lunes 27» y, más
 * atrás, «Domingo 26 de octubre» —con el año solo si no es el de [hoy]—. El día de la semana es lo
 * que la opción 3 agrega: «el sábado fui a mercar» se busca por el día, no por la fecha.
 *
 * [formatDayHeading] (el de siempre, sin el día de la semana) sigue para el detalle de una cuenta y
 * el selector de fecha. Un ISO ilegible vuelve tal cual: mejor un encabezado feo que una lista que
 * no se pinta.
 */
fun encabezadoDelDiaDeActividad(iso: String, hoy: String): String {
    val fecha = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val semana = nombreDelDiaDeLaSemana(fecha.dayOfWeek)
    val hoyFecha = runCatching { LocalDate.parse(hoy) }.getOrNull()
    if (hoyFecha != null) {
        if (fecha == hoyFecha) return "Hoy · $semana ${fecha.dayOfMonth}"
        if (fecha == hoyFecha.minus(DatePeriod(days = 1))) return "Ayer · $semana ${fecha.dayOfMonth}"
    }
    val base = "${semana.replaceFirstChar { it.uppercase() }} ${fecha.dayOfMonth} de ${MESES_DEL_DIA[fecha.monthNumber - 1]}"
    return if (hoyFecha != null && fecha.year != hoyFecha.year) "$base de ${fecha.year}" else base
}

/**
 * **Lo que dice la derecha del encabezado de un día**: lo gastado y lo que entró, con las reglas de
 * siempre.
 *
 * @property gastaste lo gastado ese día: la regla de «Gastos» ([esGastoDelPeriodo]: gasto en pesos,
 *   confirmado, de plata del bolsillo —con la cuota de un crédito; sin traspasos, pagos de tarjeta,
 *   cuentas de deuda ni ajustes—) y **tu parte** de un gasto repartido ([montoContado]), la que suma «En
 *   qué se va». Es la misma función que pinta el «−» de cada fila ([comoSePresenta]): el «Gastaste» es
 *   la suma de las filas con «−» (en pesos y confirmadas).
 * @property entro lo que entró: la regla de «Entró» de la franja del período ([resumenDelPanel]).
 */
data class ResumenDelDia(val gastaste: Long, val entro: Long)

/** [ResumenDelDia] sobre los movimientos de un día (los que la lista pinta, con su chip). Pura. */
fun resumenDelDia(items: List<FinancialEvent>, tiposDeCuenta: Map<String, AccountType>): ResumenDelDia {
    val gastaste = items
        .filter { esGastoDelPeriodo(it, tiposDeCuenta[it.accountId]) }
        .sumOf { montoContado(it) }
    // Vuelta 50: la misma función que pinta el «+» de la fila ([comoSePresenta]), con la moneda y «Por
    // confirmar» de la regla de «Entró».
    val entro = items
        .filter { comoSePresenta(it, tiposDeCuenta[it.accountId]) == PresentacionDelMovimiento.INGRESO }
        .filter { it.currency == "COP" && !esperaEnPorConfirmar(it.reconciliationStatus) }
        .sumOf { montoContado(it) }
    return ResumenDelDia(gastaste = gastaste, entro = entro)
}

/** Qué cifra va a la derecha del día: «Gastaste», «Entró» o nada. */
enum class RotuloDelDia(val texto: String) { GASTASTE("Gastaste"), ENTRO("Entró") }

/**
 * **La cifra del día**: lo gastado si hubo gasto; si ese día solo entró plata, lo que entró (en
 * verde); si no hubo nada que cuente —un traspaso, un pago de tarjeta, un ajuste—, nada. Pura.
 */
fun cifraDelDia(resumen: ResumenDelDia): Pair<RotuloDelDia, Long>? = when {
    resumen.gastaste > 0 -> RotuloDelDia.GASTASTE to resumen.gastaste
    resumen.entro > 0 -> RotuloDelDia.ENTRO to resumen.entro
    else -> null
}

// ── El chip «Compartidos» ────────────────────────────────────────────────────────────────────────

/**
 * **¿Este movimiento es con otras personas?** Un gasto repartido en un espacio (`espacioId`, el
 * mismo dato que pinta «de $88.600 · con Caro» debajo del monto). Es lo que filtra el chip
 * «Compartidos» ([CHIP_PERSONAS]). Una transferencia a un tercero guardado no entra: es plata que se
 * le mandó, no un gasto que se comparte con él.
 */
fun esConOtrasPersonas(evento: FinancialEvent): Boolean = evento.espacioId != null

// ── La tarjeta de los avisos del banco ───────────────────────────────────────────────────────────

/**
 * **Los avisos del banco que esperan en Actividad** (vuelta 40), uno por pago —el mismo conteo de
 * «Por revisar» ([pagosPorRevisar])—.
 *
 * @property cuantos los pagos avisados por confirmar.
 * @property suman la suma de lo que Movi leyó, en pesos; `null` si algún aviso no se pudo leer o no
 *   es en pesos (una suma a medias sería una cifra que no es).
 * @property comercios «Rappi, D1 y Uber», o `null` si Movi no leyó ninguno.
 * @property confirmables **solo** los que la fila de «Por revisar» confirmaría con un toque
 *   ([loQueMoviEntendio]: monto, cuenta y categoría entendidos, sin dudas). Los demás se quedan.
 */
data class AvisosPorConfirmar(
    val cuantos: Int,
    val suman: Long?,
    val comercios: String?,
    val confirmables: List<AnotacionEntendida>,
)

/**
 * Arma [AvisosPorConfirmar] con la bandeja ([mensajes]), lo que Movi propone para cada pago
 * ([propuestas], `null` si todavía no se leyó: nada se confirma) y las cuentas. Los avisos de
 * [fallaron] —se intentaron confirmar acá y no se pudo— no se vuelven a ofrecer en el lote.
 */
fun avisosPorConfirmar(
    mensajes: List<SmsMessage>,
    propuestas: PropuestasDeLaBandeja?,
    cuentas: List<Account>,
    ahora: Long,
    fallaron: Set<String> = emptySet(),
    /** Vuelta 46: cabeza del pago → la categoría que el dueño eligió en su tarjeta de Por revisar. */
    categoriasElegidas: Map<String, String> = emptyMap(),
): AvisosPorConfirmar {
    // Vuelta 51: los de los últimos 30 días. Los antiguos esperan aparte («Más antiguos»), con su «Ignorar».
    val pagos = pagosRecientesPorRevisar(mensajes, ahora)
    val leidos = pagos.map { propuestaDelPago(propuestas, it)?.leido }
    // La misma suma que la franja «Por confirmar» del Inicio (`sumaDeLoPorConfirmar`, vuelta 44).
    val suman = sumaDeLoPorConfirmar(leidos.map { leido -> leido?.let { MontoDelAviso(it.amount.roundToLong(), it.currency) } })
    val nombres = leidos.mapNotNull { leido -> leido?.merchant?.takeIf { it.isNotBlank() }?.let(::nombreParaMostrar) }.distinct()
    val confirmables = if (propuestas == null) emptyList() else pagos
        .filter { it.id !in fallaron }
        .mapNotNull { loQueMoviEntendio(it, mensajes, propuestas, cuentas, ahora, categoriasElegidas[it.id]).anotacion }
    return AvisosPorConfirmar(
        cuantos = pagos.size,
        suman = suman,
        comercios = listaDeComercios(nombres),
        confirmables = confirmables,
    )
}

/** «Rappi», «Rappi y D1», «Rappi, D1 y Uber», «Rappi, D1 y 3 más»; `null` sin ninguno. */
fun listaDeComercios(nombres: List<String>): String? = when {
    nombres.isEmpty() -> null
    nombres.size == 1 -> nombres[0]
    nombres.size == 2 -> "${nombres[0]} y ${nombres[1]}"
    nombres.size == 3 -> "${nombres[0]}, ${nombres[1]} y ${nombres[2]}"
    else -> "${nombres[0]}, ${nombres[1]} y ${nombres.size - 2} más"
}

/** «3 avisos del banco por confirmar». */
fun tituloDeLosAvisos(cuantos: Int): String =
    if (cuantos == 1) "1 aviso del banco por confirmar" else "$cuantos avisos del banco por confirmar"

/** «Suman $186.400 · Rappi, D1 y Uber»; sin suma o sin comercios, lo que haya. */
fun detalleDeLosAvisos(avisos: AvisosPorConfirmar): String? {
    val suma = avisos.suman?.let { "Suman ${formatCOP(it)}" }
    return listOfNotNull(suma, avisos.comercios).joinToString(" · ").ifEmpty { null }
}

/**
 * **El botón de confirmar**: «Confirmar los 3» cuando Movi entendió todos; «Confirmar los 2 que Movi
 * entendió» cuando solo algunos (el resto queda para «Revisar uno por uno»); `null` sin ninguno.
 */
fun botonDeConfirmarLosAvisos(avisos: AvisosPorConfirmar): String? {
    val m = avisos.confirmables.size
    return when {
        m == 0 -> null
        m == avisos.cuantos -> if (m == 1) "Confirmar el aviso" else "Confirmar los $m"
        else -> tituloDelLote(m)
    }
}

/**
 * Lo que dice la hoja antes de confirmar: cada movimiento en una línea («Rappi: Comida · Nu ·
 * $27.500») y el total. Lo que no se anota también se dice.
 */
fun detalleDeLaConfirmacion(avisos: AvisosPorConfirmar): String {
    val enPesos = avisos.confirmables.filter { it.leido.currency == "COP" }
    val total = if (enPesos.size == avisos.confirmables.size) "Total ${formatCOP(enPesos.sumOf { it.monto })}" else null
    val quedan = (avisos.cuantos - avisos.confirmables.size).takeIf { it > 0 }?.let {
        if (it == 1) "1 aviso queda para revisar uno por uno" else "$it avisos quedan para revisar uno por uno"
    }
    // Vuelta 46: los avisos van como filas en la hoja (ver `HojaDeConfirmarLosAvisos`); acá, el total
    // y lo que queda. Vuelta 47: con su separador —«Total $57.100 · 2 avisos quedan…»—; iban pegados.
    return listOfNotNull(total, quedan).joinToString(" · ")
}

/** «Comida · Bancolombia Ahorros»: lo que dice cada fila de la hoja de «Confirmar los N», sin el monto. */
fun categoriaYCuentaDe(anotacion: AnotacionEntendida): String = "${anotacion.categoria} · ${anotacion.cuenta.name}"

/**
 * **El botón que abre «Por revisar»**: «Revisar uno por uno» solo cuando al lado hay un «Confirmar»
 * de varios; con un solo aviso, o sin ninguno que Movi entienda, «Revisar» a secas (revisión de la
 * vuelta 40: «uno por uno» de uno no dice nada).
 */
fun botonDeRevisarLosAvisos(avisos: AvisosPorConfirmar): String =
    if (avisos.cuantos <= 1 || avisos.confirmables.isEmpty()) "Revisar" else REVISAR_UNO_POR_UNO

/** «¿Confirmar estos 3 avisos?». */
fun preguntaDeConfirmarLosAvisos(cuantos: Int): String =
    if (cuantos == 1) "¿Confirmar este aviso?" else "¿Confirmar estos $cuantos avisos?"

/**
 * Lo que dice el aviso de abajo al terminar: «3 confirmados»; si alguno falló, cuántos quedaron. No
 * hay «Deshacer»: confirmar un aviso no tiene vuelta atrás en Movi (se anula el movimiento, como
 * siempre).
 */
fun textoDeLosConfirmados(resultado: ResultadoDelLote): String {
    val n = resultado.anotados.size
    val hechos = if (n == 1) "1 confirmado" else "$n confirmados"
    val quedaron = resultado.quedaron.size
    if (quedaron == 0) return hechos
    return if (quedaron == 1) "$hechos. 1 no se pudo: quedó en Por revisar." else "$hechos. $quedaron no se pudieron: quedaron en Por revisar."
}
