package com.jvillada.movi.shared.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/*
 * # «¿Ya bajaste el extracto de Nu de octubre?» (vuelta 94)
 *
 * Los cargos de una tarjeta —los intereses, la cuota de manejo, la comisión por cambio de moneda— no
 * los avisa ningún SMS: solo los dice el extracto, y el dueño se olvida de subirlo. Cuando pasaron
 * [DIAS_PARA_PEDIR_EL_EXTRACTO] días desde el corte de una tarjeta y no se subió un extracto de ella,
 * Movi lo pide: Hoy lo propone en «Movi propone hoy» («Subir el extracto» / «Ya no») y el teléfono lo
 * avisa una vez por tarjeta y corte.
 *
 * Se **deriva en cada lectura** (`GET /api/extractos-por-subir`), como los débitos automáticos: no se
 * escribe nada hasta que el dueño contesta, y desaparece sola cuando el extracto llega. «Ya no» guarda
 * la [ExtractoPorSubir.huella] en la misma tabla de descartes de «lo que se sale de lo normal»
 * (`anomalias_descartadas`, `POST /api/anomalias/descartar`): una huella por tarjeta y corte, así que el
 * corte siguiente vuelve a pedir.
 */

/**
 * **Cuántos días después del corte se pide el extracto.** Elegido, no medido: el banco lo publica el
 * día del corte o al siguiente, y tres días le dan al dueño tiempo de subirlo antes de que Movi insista.
 */
const val DIAS_PARA_PEDIR_EL_EXTRACTO: Int = 3

/** Lo que dice la propuesta debajo de la pregunta. */
const val CUERPO_DEL_EXTRACTO_POR_SUBIR: String = "Súbelo y Movi anota los intereses y la cuota de manejo."

/**
 * **Un extracto que falta**: el de la tarjeta [cuentaId] que cerró el [corte] (ISO). [banco] es el de
 * sus condiciones (`CardTerms.bank`), o el nombre de la cuenta si no tiene.
 */
@Serializable
data class ExtractoPorSubir(
    val cuentaId: String,
    val cuentaNombre: String,
    val banco: String,
    val corte: String,
) {
    /** La llave de «Ya no» y de «ya lo avisó este teléfono»: una por tarjeta y corte. */
    val huella: String get() = huellaDelExtractoPorSubir(cuentaId, corte)
}

/** `extracto:<cuenta>:<corte>`. */
fun huellaDelExtractoPorSubir(cuentaId: String, corte: String): String = "extracto:$cuentaId:$corte"

/** El prefijo de [huellaDelExtractoPorSubir]: así el server lee solo estos descartes. */
const val PREFIJO_DE_LA_HUELLA_DEL_EXTRACTO: String = "extracto:"

/**
 * **El corte cuyo extracto se pide hoy**, o `null`: el corte más reciente de la tarjeta, solo si ya
 * pasaron [dias] días desde él. Antes, nada. Sin día de corte cargado (o uno imposible), nada.
 */
fun corteDelExtractoPorPedir(diaDeCorte: Int?, hoy: LocalDate, dias: Int = DIAS_PARA_PEDIR_EL_EXTRACTO): LocalDate? {
    val dia = diaDeCorte?.takeIf { it in 1..31 } ?: return null
    val corte = corteMasReciente(dia, hoy)
    return corte.takeIf { hoy >= it.plus(DatePeriod(days = dias)) }
}

/**
 * **¿Se pide el extracto de esta tarjeta hoy?** La regla entera, pura:
 *
 * - hay un corte por pedir ([corteDelExtractoPorPedir]);
 * - no se subió (ni importó) un extracto de esta cuenta el día del corte o después ([subidos]: las
 *   fechas en que se hizo);
 * - el dueño no dijo «Ya no» a ese corte ([descartadas]);
 * - y la tarjeta se usó: [conActividad] dice si tuvo movimientos o deuda en ese corte (una tarjeta
 *   guardada en un cajón no tiene nada que contar).
 */
fun extractoPorSubir(
    cuentaId: String,
    cuentaNombre: String,
    banco: String,
    diaDeCorte: Int?,
    hoy: LocalDate,
    subidos: List<LocalDate>,
    descartadas: Set<String>,
    conActividad: (corte: LocalDate) -> Boolean = { true },
): ExtractoPorSubir? {
    val corte = corteDelExtractoPorPedir(diaDeCorte, hoy) ?: return null
    if (subidos.any { it >= corte }) return null
    val propuesta = ExtractoPorSubir(cuentaId, cuentaNombre, banco.trim().ifEmpty { cuentaNombre }, corte.toString())
    if (propuesta.huella in descartadas) return null
    if (!conActividad(corte)) return null
    return propuesta
}

/**
 * Cómo se nombra la tarjeta en la pregunta: por su banco («Nu»), salvo que otra de [todos] sea del
 * mismo banco —dos de Bancolombia—, y ahí por el nombre de la cuenta.
 */
fun nombreDelExtractoPorSubir(extracto: ExtractoPorSubir, todos: List<ExtractoPorSubir> = listOf(extracto)): String {
    val mismoBanco = todos.count { claveComparableDeNombre(it.banco) == claveComparableDeNombre(extracto.banco) }
    return if (mismoBanco > 1) extracto.cuentaNombre else extracto.banco
}

/** El mes del corte, «octubre»; `null` si el corte no es una fecha. */
fun mesDelExtractoPorSubir(extracto: ExtractoPorSubir): String? =
    runCatching { LocalDate.parse(extracto.corte) }.getOrNull()
        ?.let { nombreDelMesDe(PeriodoFinanciero(it.year, it.monthNumber)) }

/** «¿Ya bajaste el extracto de Nu de octubre?». */
fun preguntaDelExtractoPorSubir(extracto: ExtractoPorSubir, todos: List<ExtractoPorSubir> = listOf(extracto)): String {
    val mes = mesDelExtractoPorSubir(extracto)?.let { " de $it" }.orEmpty()
    return "¿Ya bajaste el extracto de ${nombreDelExtractoPorSubir(extracto, todos)}$mes?"
}

/**
 * [extractoPorSubir] con las fechas en ISO: la forma que usa el server, que no ve `kotlinx-datetime`
 * (usa `java.time`). [conActividad] recibe el corte en ISO.
 */
fun extractoPorSubirAl(
    cuentaId: String,
    cuentaNombre: String,
    banco: String,
    diaDeCorte: Int?,
    hoy: String,
    subidos: List<String>,
    descartadas: Set<String>,
    conActividad: (corte: String) -> Boolean = { true },
): ExtractoPorSubir? = extractoPorSubir(
    cuentaId = cuentaId,
    cuentaNombre = cuentaNombre,
    banco = banco,
    diaDeCorte = diaDeCorte,
    hoy = LocalDate.parse(hoy),
    subidos = subidos.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() },
    descartadas = descartadas,
    conActividad = { conActividad(it.toString()) },
)
