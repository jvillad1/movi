package com.jvillada.movi.server.ai

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.esperaEnPorConfirmar
import com.jvillada.movi.shared.model.isCashFlow
import com.jvillada.movi.shared.model.normalizarParaBuscar
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * # Movi AI consulta tus datos, no solo lee un resumen
 *
 * El contexto que ya se le pasa al asistente (ver [ContextoDelPeriodo]) cuenta el período en curso
 * completo. Con eso contesta «¿en qué se me fue la plata?» pero no «¿y en julio?», ni «¿cuánto le
 * he pagado a Coomeva este año?», ni «¿esto que compré hoy ya lo había comprado?». El dueño lo
 * pidió así: *«o poder consultarlo de alguna forma al menos»*.
 *
 * Acá viven las tres preguntas que puede hacerle a la base, y **solo esas tres**:
 *
 * | Herramienta | Contesta |
 * |---|---|
 * | [BUSCAR_MOVIMIENTOS] | «¿qué compré en X?», «¿qué hubo entre estas dos fechas?» |
 * | [TOTALES_POR_CATEGORIA] | «¿cuánto gasté en Comida en agosto?», «¿gasté más que el mes pasado?» |
 * | [BUSCAR_DOCUMENTOS] | «¿qué dice la póliza del 2334?», «¿tengo el extracto de agosto?» |
 *
 * ### Tres reglas que no se negocian
 *
 * 1. **Solo lectura, y solo del dueño.** Ninguna herramienta escribe nada, y el `uid` sale del
 *    token —nunca de lo que el modelo mande—. Una herramienta que aceptara el usuario por
 *    parámetro sería una fuga de datos a un texto que escribe un modelo.
 * 2. **Las mismas cifras que la pantalla.** Anulados afuera, «Por confirmar» afuera, el pago de
 *    tarjeta no cuenta como gasto (`isCashFlow`). Un asistente que conteste otra cosa que el
 *    Inicio es peor que uno que no sepa.
 * 3. **Acotado y dicho.** Toda respuesta tiene tope, y cuando el tope corta, el texto lo dice.
 *    Igual que en el contexto: un recorte callado hace que el modelo sume lo que ve y conteste una
 *    cifra que no coincide con la pantalla.
 */

/** Lo que el modelo pidió: el nombre de la herramienta y sus argumentos ya leídos. */
data class LlamadaDeHerramienta(
    val id: String,
    val nombre: String,
    val argumentos: Map<String, String>,
)

const val BUSCAR_MOVIMIENTOS = "buscar_movimientos"
const val TOTALES_POR_CATEGORIA = "totales_por_categoria"
const val BUSCAR_DOCUMENTOS = "buscar_documentos"

/**
 * **Cuánto se le manda cuando pide los documentos sin filtrar.** La lista entera de los 33 papeles
 * del dueño son casi ocho mil caracteres: tanto como costaba el contexto viejo completo, en una
 * sola consulta. Con este techo entra un tercio, el bloque dice que hay más, y el modelo puede
 * volver a pedir con un filtro — que es lo que debería haber hecho de entrada.
 */
internal const val PRESUPUESTO_SIN_FILTRO = 2_500

/**
 * **Cuántos movimientos devuelve una búsqueda.** No es una cota de rendimiento: es que una lista
 * más larga que esto no la lee nadie, ni el modelo ni el dueño en la respuesta, y lo que sí hace
 * es empujar el resto de la conversación fuera de la ventana.
 */
internal const val TOPE_DE_RESULTADOS = 40

/**
 * **Hasta dónde mira hacia atrás una consulta sin fechas.** Un año es lo que hace falta para
 * cualquier pregunta sobre «este año» sin traer la historia entera de una cuenta vieja.
 */
internal const val MESES_HACIA_ATRAS_POR_DEFECTO = 12L

/** Una fila, como la lee el modelo. */
private data class FilaDeMovimiento(
    val fecha: String,
    val nombre: String,
    val categoria: String,
    val monto: Long,
    val moneda: String,
    val esIngreso: Boolean,
    val cuenta: String,
)

/**
 * **Ejecuta lo que el modelo pidió.** Devuelve texto, siempre: un error no se lanza, se le
 * contesta —«esa fecha no se entiende»— para que el modelo pueda corregir y volver a preguntar en
 * vez de tumbar la conversación entera.
 */
suspend fun ejecutarHerramienta(uid: String, llamada: LlamadaDeHerramienta): String = try {
    when (llamada.nombre) {
        BUSCAR_MOVIMIENTOS -> buscarMovimientos(uid, llamada.argumentos)
        TOTALES_POR_CATEGORIA -> totalesPorCategoria(uid, llamada.argumentos)
        BUSCAR_DOCUMENTOS -> buscarDocumentos(uid, llamada.argumentos)
        else -> "No existe una herramienta que se llame «${llamada.nombre}»."
    }
} catch (e: FechaIlegible) {
    "No pude entender la fecha «${e.loQueVino}». Usa el formato AAAA-MM-DD, por ejemplo 2026-08-01."
}

internal class FechaIlegible(val loQueVino: String) : Exception()

// ── Las consultas ────────────────────────────────────────────────────────────

private suspend fun buscarMovimientos(uid: String, args: Map<String, String>): String {
    val desde = fechaDe(args["desde"]) ?: AppClock.today().minusMonths(MESES_HACIA_ATRAS_POR_DEFECTO)
    val hasta = fechaDe(args["hasta"]) ?: AppClock.today()
    val categoria = args["categoria"]?.takeIf { it.isNotBlank() }
    val cuenta = args["cuenta"]?.takeIf { it.isNotBlank() }
    val texto = args["texto"]?.takeIf { it.isNotBlank() }
    val soloGastos = args["tipo"]?.lowercase()?.startsWith("gast") == true
    val soloIngresos = args["tipo"]?.lowercase()?.startsWith("ingres") == true
    val tope = (args["limite"]?.toIntOrNull() ?: TOPE_DE_RESULTADOS).coerceIn(1, TOPE_DE_RESULTADOS)

    val todas = filasDe(uid, desde, hasta)
        .filter { categoria == null || normalizarParaBuscar(it.categoria) == normalizarParaBuscar(categoria) }
        // La cuenta se compara CONTENIDA y no igual: él dice «Bancolombia» y la cuenta se llama
        // «Bancolombia Ahorros». Exigir el nombre exacto sería pedirle que lo copie de la pantalla.
        .filter { cuenta == null || normalizarParaBuscar(cuenta) in normalizarParaBuscar(it.cuenta) }
        .filter { texto == null || normalizarParaBuscar(texto) in normalizarParaBuscar(it.nombre) }
        .filter { !soloGastos || !it.esIngreso }
        .filter { !soloIngresos || it.esIngreso }

    if (todas.isEmpty()) {
        return "Sin movimientos entre $desde y $hasta" +
            (categoria?.let { " en la categoría «$it»" } ?: "") +
            (cuenta?.let { " en cuentas que digan «$it»" } ?: "") +
            (texto?.let { " que digan «$it»" } ?: "") + "."
    }

    val muestra = todas.take(tope)
    return buildString {
        appendLine("${todas.size} movimientos entre $desde y $hasta:")
        muestra.forEach { appendLine("- ${renglon(it)}") }
        if (todas.size > muestra.size) {
            appendLine(
                "(y ${todas.size - muestra.size} más que no se listan; para una cifra total usa " +
                    "$TOTALES_POR_CATEGORIA con las mismas fechas, que suma TODOS)",
            )
        }
    }.trim()
}

private suspend fun totalesPorCategoria(uid: String, args: Map<String, String>): String {
    val desde = fechaDe(args["desde"]) ?: AppClock.today().minusMonths(1)
    val hasta = fechaDe(args["hasta"]) ?: AppClock.today()

    val filas = filasDe(uid, desde, hasta)
    if (filas.isEmpty()) return "Sin movimientos entre $desde y $hasta."

    return buildString {
        appendLine("Entre $desde y $hasta:")
        filas.groupBy { it.moneda }.toSortedMap().forEach { (moneda, deEsaMoneda) ->
            val gastos = deEsaMoneda.filter { !it.esIngreso }
            val ingresos = deEsaMoneda.filter { it.esIngreso }
            appendLine("== En $moneda ==")
            appendLine("- Entró: ${ingresos.sumOf { it.monto }}")
            appendLine("- Salió: ${gastos.sumOf { it.monto }}")
            gastos.groupBy { it.categoria }
                .mapValues { (_, dela) -> dela.sumOf { it.monto } }
                .entries.sortedByDescending { it.value }
                .forEach { (categoria, monto) ->
                    appendLine("- $categoria: $monto")
                }
            // **Y de qué cuenta salió.** Esto entró por una pregunta que el asistente no supo
            // contestar: «gastos desde Bancolombia del período». Es una pregunta obvia —de qué
            // bolsillo salió la plata— y no había forma de responderla: el contexto trae los
            // saldos de cada cuenta y el gasto por categoría, pero nada que cruce las dos cosas.
            if (gastos.isNotEmpty()) {
                appendLine("De qué cuenta salió:")
                gastos.groupBy { it.cuenta }
                    .mapValues { (_, dela) -> dela.sumOf { it.monto } }
                    .entries.sortedByDescending { it.value }
                    .forEach { (cuenta, monto) -> appendLine("- $cuenta: $monto") }
            }
        }
    }.trim()
}

/**
 * **Los papeles del dueño, cuando hacen falta.** Este bloque vivía en el contexto de cada mensaje
 * —33 documentos con sus notas, casi seis mil caracteres— para contestar una pregunta cada tantas.
 * El texto que devuelve es exactamente el mismo de antes (ver [renderizarDocumentos]): lo único
 * que cambió es CUÁNDO se paga.
 *
 * Con [texto] filtra por nombre o por notas; sin él los trae todos, **con un presupuesto más
 * corto**. Eso último salió de medir: la lista entera son 7.967 caracteres, o sea que una consulta
 * sin filtro cuesta en entrada lo mismo que costaba el contexto viejo completo. Con filtro devuelve
 * unos cientos. El recorte se anuncia solo (ver [renderizarDocumentos]), así que el modelo sabe que
 * hay más y puede volver a preguntar con un filtro — que es justo lo que conviene que haga.
 */
private suspend fun buscarDocumentos(uid: String, args: Map<String, String>): String {
    val texto = args["texto"]?.takeIf { it.isNotBlank() }
    val todos = cargarDocumentosParaContexto(uid)
    val elegidos = if (texto == null) todos else todos.filter { doc ->
        val aguja = normalizarParaBuscar(texto)
        aguja in normalizarParaBuscar(doc.nombre) || aguja in normalizarParaBuscar(doc.notas.orEmpty())
    }
    if (elegidos.isEmpty()) {
        return if (texto == null) "El dueño no tiene documentos guardados."
        else "Ninguno de los ${todos.size} documentos guardados dice «$texto»."
    }
    val nombresDeCuenta = dbQuery {
        Accounts.selectAll().where { Accounts.userId eq uid }
            .associate { it[Accounts.id] to it[Accounts.name] }
    }
    val presupuesto = if (texto == null) PRESUPUESTO_SIN_FILTRO else PRESUPUESTO_DE_DOCUMENTOS
    return renderizarDocumentos(elegidos, nombresDeCuenta, presupuesto).trim()
}

// ── La lectura, una sola y con las reglas de plata ───────────────────────────

/**
 * **El único lugar donde estas herramientas leen movimientos**, para que las dos apliquen
 * exactamente los mismos filtros: anulados afuera, «Por confirmar» afuera, y lo que no cuenta
 * como flujo de caja afuera (un abono a la tarjeta no es un gasto; ver `isCashFlow`).
 *
 * Al revés de [ContextoDelPeriodo], acá **no se filtra por moneda**: una búsqueda de «Anthropic»
 * que no encuentra nada porque el cobro fue en dólares es un hueco silencioso. Cada fila dice su
 * moneda y los totales van separados por moneda — nunca sumadas entre sí.
 */
private suspend fun filasDe(uid: String, desde: LocalDate, hasta: LocalDate): List<FilaDeMovimiento> {
    val inicio = appDateToEpochMillis(desde)
    val finExclusivo = appDateToEpochMillis(hasta.plusDays(1))
    return dbQuery {
        val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }
            .map { it[VoidEvents.originalEventId] }.toSet()
        val tipoDeCuenta = accountTypesFor(uid)
        val nombreDeCuenta = Accounts.selectAll().where { Accounts.userId eq uid }
            .associate { it[Accounts.id] to it[Accounts.name] }

        Events.selectAll()
            .where {
                (Events.userId eq uid) and
                    (Events.timestamp greaterEq inicio) and
                    (Events.timestamp less finExclusivo)
            }
            .filterNot { it[Events.id] in anulados }
            .filterNot { esperaEnPorConfirmar(it[Events.reconciliationStatus]) }
            .filter { fila ->
                val tipo = tipoDeCuenta[fila[Events.accountId]]
                tipo == null || isCashFlow(tipo, TransactionType.valueOf(fila[Events.type]), fila[Events.category])
            }
            .map { fila ->
                FilaDeMovimiento(
                    fecha = epochMillisToAppDateString(fila[Events.timestamp]),
                    nombre = fila[Events.description],
                    categoria = fila[Events.category],
                    monto = fila[Events.amount],
                    moneda = fila[Events.currency],
                    esIngreso = fila[Events.type] == TransactionType.INCOME.name,
                    cuenta = nombreDeCuenta[fila[Events.accountId]] ?: "otra cuenta",
                )
            }
            .sortedByDescending { it.fecha }
    }
}

private fun renglon(f: FilaDeMovimiento): String {
    val signo = if (f.esIngreso) "+" else "-"
    return "${f.fecha} · ${f.nombre} (${f.categoria}, ${f.cuenta}): $signo${f.monto} ${f.moneda}"
}

/** `null` cuando no vino nada; excepción cuando vino algo que no es una fecha. */
private fun fechaDe(valor: String?): LocalDate? {
    val limpio = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        LocalDate.parse(limpio)
    } catch (_: DateTimeParseException) {
        throw FechaIlegible(limpio)
    }
}
