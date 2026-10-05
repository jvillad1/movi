package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/*
 * # Los cargos y abonos del banco, en la revisión de un extracto
 *
 * Una fila del extracto que es un cargo o un abono del banco ([cargoDelBanco]) entra con la categoría
 * del dueño y se agrupa aparte en la revisión, para anotarlas todas de un toque.
 *
 * **El cuidado es el duplicado sumado.** El dueño a veces ya las anotó a mano, juntas: «4x1000 del 27
 * al 30 de septiembre» por $17.819, mientras el extracto trae cuatro líneas diarias. Ninguna línea
 * tiene ese monto, así que el emparejador de siempre (mismo monto, ±2 días) no las encuentra y se
 * anotarían dos veces. [sumasYaAnotadas] busca ese movimiento: misma categoría, tipo y moneda, con un
 * rango (o una fecha) que cubre las líneas. Si la suma de las líneas cubiertas **coincide exacto**,
 * están anotadas; si no, puede que lo estén, y se avisa. No se calcula nada: se suman las cifras que
 * dijo el banco y se comparan con la que escribió el dueño.
 */

/**
 * **Un movimiento ya anotado que puede ser la suma de esta fila con otras.** Viaja con su cuenta
 * porque la cuenta del extracto se elige después, en la pantalla de revisión: la fila solo está
 * anotada si ese movimiento es de la cuenta contra la que se importa (ver [estadoDelCargo]).
 */
@Serializable
data class SumaYaAnotada(
    val eventoId: String,
    val cuentaId: String,
    /** Cómo lo escribió el dueño: «4x1000 del 27 al 30 de septiembre». */
    val nombre: String,
    /** La cifra que anotó el dueño, tal cual. */
    val monto: Long,
    val moneda: String = "COP",
    /** ¿Las líneas del extracto que cubre suman exactamente [monto]? */
    val exacta: Boolean,
)

/**
 * **Una fila del extracto que es un cargo o un abono del banco.** Va aparte de la fila
 * ([ParsedTransaction]) para que un APK viejo, que no la conoce, siga leyendo la respuesta igual.
 */
@Serializable
data class CargoEnElExtracto(
    val parsedId: String,
    val clase: ClaseDeCargo,
    /** Los movimientos ya anotados que cubren esta fila (ver [sumasYaAnotadas]). */
    val anotadoSumado: List<SumaYaAnotada> = emptyList(),
)

/** Lo que la revisión hace con un cargo, según la cuenta contra la que se importa. */
sealed interface EstadoDelCargo {
    /** Nada lo cubre: se propone tildado. */
    data object Nuevo : EstadoDelCargo

    /** Un movimiento de esa cuenta lo cubre con la suma exacta: no se propone. */
    data class YaAnotado(val en: SumaYaAnotada) : EstadoDelCargo

    /** Un movimiento de esa cuenta lo cubre pero la suma no da: se propone y se avisa. */
    data class PuedeEstarSumado(val en: SumaYaAnotada) : EstadoDelCargo
}

/**
 * El estado de [cargo] si el extracto se importa contra [cuentaId]. Sin cuenta elegida, nada está
 * anotado: el duplicado es «en la misma cuenta».
 */
fun estadoDelCargo(cargo: CargoEnElExtracto, cuentaId: String?): EstadoDelCargo {
    if (cuentaId == null) return EstadoDelCargo.Nuevo
    val deLaCuenta = cargo.anotadoSumado.filter { it.cuentaId == cuentaId }
    deLaCuenta.firstOrNull { it.exacta }?.let { return EstadoDelCargo.YaAnotado(it) }
    deLaCuenta.firstOrNull()?.let { return EstadoDelCargo.PuedeEstarSumado(it) }
    return EstadoDelCargo.Nuevo
}

/**
 * **El cargo del banco de una fila del extracto**, mirando el comercio, la descripción y el texto
 * crudo, con el tipo que dijo el banco (ver [cargoDelBanco]).
 */
fun cargoDeLaFila(fila: ParsedTransaction): CargoDelBanco? =
    listOf(fila.merchant, fila.description, fila.rawText)
        .filter { it.isNotBlank() }
        .firstNotNullOfOrNull { cargoDelBanco(it, fila.type) }

/**
 * **La fila con la categoría del cargo.** La memoria del dueño gana: si él ya anotó este mismo rótulo
 * con otra categoría, va esa — salvo que la memoria proponga la categoría de OTRA clase de cargo
 * («Ingreso» para unos intereses que salen, por un rótulo parecido), que sería cambiarle el sentido.
 * Si no hay memoria, la categoría del dueño para ese cargo; y si no la tiene, la fila se queda con la
 * que traía (no se crea ninguna).
 */
fun conLaCategoriaDelCargo(fila: ParsedTransaction, cargo: CargoDelBanco, memoria: MemoriaDeCategorias): ParsedTransaction {
    val recordada = memoria.recuerdoDe(fila.merchant)?.categoria
        ?.takeUnless { esDeOtraClaseDeCargo(it, cargo.clase) }
    val categoria = recordada ?: categoriaDelCargo(cargo, memoria.categoriasDelDueno) ?: return fila
    return fila.copy(category = categoria)
}

private fun esDeOtraClaseDeCargo(categoria: String, clase: ClaseDeCargo): Boolean =
    ClaseDeCargo.entries.filter { it != clase }.any { otra ->
        categoriaDelCargo(CargoDelBanco(otra), setOf(categoria)) != null
    }

/** Más largo que esto no es «del 27 al 30»: es otra cosa, o un error al escribir. */
private const val DIAS_MAXIMOS_DE_UN_RANGO = 62

private val RANGO = Regex("""\b(\d{1,2})(?:\s+de\s+([a-z]+))?\s+al\s+(\d{1,2})\s+de\s+([a-z]+)""")

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private fun mesDe(palabra: String): Int? {
    val tres = palabra.take(3)
    if (tres == "set") return 9
    return MESES.indexOf(tres).takeIf { it >= 0 }?.plus(1)
}

/**
 * **Los días que cubre un movimiento anotado**: el rango que el dueño escribió en el nombre («del 27
 * al 30 de septiembre», «(15 al 19 de sept)», «(28 de septiembre al 2 de octubre)») o, si no escribió
 * ninguno, el día del movimiento. El año es el del movimiento (y el anterior para el comienzo si el
 * rango cruza el año).
 */
fun diasQueCubre(evento: FinancialEvent): ClosedRange<LocalDate> {
    val dia = epochMillisToAppDate(evento.timestamp)
    val texto = normalizarParaBuscar(listOfNotNull(evento.description, evento.merchant).joinToString(" "))
    val m = RANGO.find(texto) ?: return dia..dia
    return runCatching {
        val mesHasta = mesDe(m.groupValues[4]) ?: return dia..dia
        val mesDesde = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { mesDe(it) ?: return dia..dia } ?: mesHasta
        var hasta = LocalDate(dia.year, mesHasta, m.groupValues[3].toInt())
        var desde = LocalDate(dia.year, mesDesde, m.groupValues[1].toInt())
        // Anotado en enero por lo de diciembre: el rango es del año anterior.
        if (hasta > dia.plus(DatePeriod(days = 31))) {
            hasta = hasta.minus(DatePeriod(years = 1))
            desde = desde.minus(DatePeriod(years = 1))
        }
        if (desde > hasta) desde = desde.minus(DatePeriod(years = 1))
        if (desde.daysUntil(hasta) > DIAS_MAXIMOS_DE_UN_RANGO) dia..dia else desde..hasta
    }.getOrDefault(dia..dia)
}

/**
 * **La fecha de una fila del extracto**, en los formatos que de verdad llegan: el ISO que pide el
 * lector (`2026-09-27`), el ISO sin ceros (`2026-9-27`) y el colombiano (`27/09/2026`). `null` si no
 * es ninguno — la misma regla que `fechaDelExtracto` en el server, que no ve kotlinx-datetime.
 */
fun fechaDeLaFila(texto: String): LocalDate? {
    val t = texto.trim()
    Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""").matchEntire(t)?.let { m ->
        return runCatching { LocalDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
    }
    Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""").matchEntire(t)?.let { m ->
        return runCatching { LocalDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }
    return null
}

/**
 * **¿Cuáles de estas filas ya anotó el dueño, sumadas?** Para cada movimiento vivo de [eventos] (los
 * que el emparejador de siempre no usó), las [filas] de cargos con su misma categoría, tipo y moneda
 * cuya fecha cae en [diasQueCubre]. Si hay alguna, cada una lleva una [SumaYaAnotada] con ese
 * movimiento, exacta si la suma de todas las cubiertas es su monto.
 *
 * [fechaDe] lee la fecha de la fila ([fechaDeLaFila]); sin fecha, la fila no está cubierta por nada.
 */
fun sumasYaAnotadas(
    filas: List<ParsedTransaction>,
    eventos: List<FinancialEvent>,
    fechaDe: (ParsedTransaction) -> LocalDate? = { fechaDeLaFila(it.date) },
): Map<String, List<SumaYaAnotada>> {
    val conFecha = filas.mapNotNull { fila -> fechaDe(fila)?.let { fila to it } }
    if (conFecha.isEmpty()) return emptyMap()
    val categorias = conFecha.map { claveComparableDeNombre(it.first.category) }.toSet()
    val resultado = mutableMapOf<String, MutableList<SumaYaAnotada>>()
    eventos
        .filter { claveComparableDeNombre(it.category) in categorias }
        .forEach { evento ->
            val dias = diasQueCubre(evento)
            val cubiertas = conFecha.filter { (fila, fecha) ->
                claveComparableDeNombre(fila.category) == claveComparableDeNombre(evento.category) &&
                    fila.type == evento.type && fila.currency == evento.currency && fecha in dias
            }
            if (cubiertas.isEmpty()) return@forEach
            val suma = SumaYaAnotada(
                eventoId = evento.id,
                cuentaId = evento.accountId,
                nombre = evento.description.ifBlank { evento.merchant.orEmpty() },
                monto = evento.amount,
                moneda = evento.currency,
                exacta = cubiertas.sumOf { it.first.amount } == evento.amount,
            )
            cubiertas.forEach { (fila, _) -> resultado.getOrPut(fila.id) { mutableListOf() } += suma }
        }
    return resultado
}

/**
 * **Las filas nuevas de un extracto, con sus cargos del banco reconocidos.** Devuelve las filas (las
 * de los cargos ya con su categoría, ver [conLaCategoriaDelCargo]) y, aparte, qué filas son cargos y
 * qué movimientos ya anotados las cubren ([sumasYaAnotadas]).
 */
fun cargosDelExtracto(
    nuevas: List<ParsedTransaction>,
    eventos: List<FinancialEvent>,
    memoria: MemoriaDeCategorias,
): Pair<List<ParsedTransaction>, List<CargoEnElExtracto>> {
    val clases = mutableMapOf<String, ClaseDeCargo>()
    val filas = nuevas.map { fila ->
        val cargo = cargoDeLaFila(fila) ?: return@map fila
        clases[fila.id] = cargo.clase
        conLaCategoriaDelCargo(fila, cargo, memoria)
    }
    val sumas = sumasYaAnotadas(filas.filter { it.id in clases }, eventos)
    val cargos = filas.filter { it.id in clases }.map { fila ->
        CargoEnElExtracto(fila.id, clases.getValue(fila.id), sumas[fila.id].orEmpty())
    }
    return filas to cargos
}
