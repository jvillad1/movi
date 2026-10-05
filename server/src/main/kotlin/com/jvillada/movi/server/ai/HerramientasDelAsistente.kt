package com.jvillada.movi.server.ai

import com.jvillada.movi.server.balance.accountTypesFor
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.routes.CajaDelDueno
import com.jvillada.movi.server.routes.cajaProyectadaDe
import com.jvillada.movi.server.routes.creditosDe
import com.jvillada.movi.server.routes.paraCompletar
import com.jvillada.movi.server.routes.tercerosDe
import com.jvillada.movi.shared.model.PERSONAS_Y_COMERCIOS
import com.jvillada.movi.shared.model.comoSeDice
import com.jvillada.movi.shared.model.loDeCadaPeriodo
import com.jvillada.movi.shared.model.movimientosDesdeElDestino
import com.jvillada.movi.shared.model.movimientosHaciaElDestino
import com.jvillada.movi.shared.model.terceroQueNombra
import com.jvillada.movi.shared.model.totalesHaciaElDestino
import com.jvillada.movi.server.routes.tarjetasDe
import com.jvillada.movi.server.time.ajustesDePeriodoDe
import com.jvillada.movi.shared.model.EstrategiaDeSalida
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PlanDeSalida
import com.jvillada.movi.shared.model.PorQueNoEntraAlPlan
import com.jvillada.movi.shared.model.deudasParaSalir
import com.jvillada.movi.shared.model.mas
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.shared.model.periodoDe
import com.jvillada.movi.shared.model.planDeSalida
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDateString
import com.jvillada.movi.shared.model.ADJUSTMENT_CATEGORY
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.OPENING_CATEGORY
import com.jvillada.movi.shared.model.ORPHANED_LEG_CATEGORY
import com.jvillada.movi.shared.model.PAYROLL_DEDUCTION_CATEGORY
import com.jvillada.movi.shared.model.THIRD_PARTY_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
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
 *
 *    Ola 3 separó dos preguntas que hasta acá eran una: **qué cuenta como gasto** y **qué se puede
 *    consultar**. Los pagos de tarjeta y los traspasos siguen sin sumar en ningún total —es la
 *    regla de plata del dueño—, pero ya no son invisibles: «¿cuánto le pagué a la Master Black este
 *    mes?» tiene respuesta. [buscarMovimientos] los lista rotulados («no cuenta como gasto: pago de
 *    tarjeta») y [totalesPorCategoria] los pone en un bloque aparte que dice que NO suma arriba.
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

/** Ola 4: la caja proyectada día a día hasta el cierre del período. Ver `CajaRoutes.kt`. */
const val PROYECTAR_CAJA = "proyectar_caja"

/** Ola 4: el plan de salida de deudas con un abono extra al mes. Ver `PlanDeSalida.kt` en :core. */
const val SIMULAR_ABONO = "simular_abono"

/**
 * **Lo de una persona o comercio guardado** (4-oct-2026): «¿cuánto le he mandado a Caro este año?».
 * Antes solo se podía contestar con [BUSCAR_MOVIMIENTOS] por texto, que pierde lo que el dueño
 * renombró («Cuota de Cotrafa» que fue a Caro) y se lleva cualquier palabra que contenga «caro».
 * Esta usa el mismo reconocimiento que la ficha (`vaHaciaElDestino`/`vieneDelDestino`).
 */
const val CONSULTAR_PERSONA_O_COMERCIO = "consultar_persona_o_comercio"

/** Cuántos movimientos lista [CONSULTAR_PERSONA_O_COMERCIO] de un tercero. */
internal const val TOPE_DE_MOVIMIENTOS_DE_UN_TERCERO = 20

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
    /**
     * `null` si cuenta como gasto o ingreso del mes (lo normal); si no, **por qué no** —«pago de
     * tarjeta», «traspaso entre tus cuentas»—. Ver [porQueNoCuentaEnElMes].
     */
    val noCuenta: String? = null,
) {
    val cuentaEnElMes: Boolean get() = noCuenta == null
}

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
        PROYECTAR_CAJA -> textoDeLaCaja(cajaProyectadaDe(uid))
        SIMULAR_ABONO -> simularAbono(uid, llamada.argumentos)
        CONSULTAR_PERSONA_O_COMERCIO -> consultarPersonaOComercio(uid, llamada.argumentos)
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
    val fueraDelMes = todas.count { !it.cuentaEnElMes }
    return buildString {
        append("${todas.size} movimientos entre $desde y $hasta")
        // El rótulo va también arriba, no solo en cada renglón: es lo que le recuerda al modelo,
        // antes de leer la lista, que esos no se suman a un gasto.
        if (fueraDelMes > 0) {
            append(
                " ($fueraDelMes NO cuentan como gasto ni como ingreso —pagos de tarjeta, traspasos " +
                    "entre sus cuentas—: se pueden consultar, pero no se suman a lo que gastó)",
            )
        }
        appendLine(":")
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

    val todas = filasDe(uid, desde, hasta)
    if (todas.isEmpty()) return "Sin movimientos entre $desde y $hasta."
    // **Los totales son los de siempre**: solo lo que cuenta en el mes. Lo demás va en su propio
    // bloque, abajo, rotulado — nunca mezclado con «Salió».
    val filas = todas.filter { it.cuentaEnElMes }
    val fuera = todas.filterNot { it.cuentaEnElMes }

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
        if (fuera.isNotEmpty()) {
            appendLine(
                bloqueDeLoQueNoCuenta(
                    fuera.map { FilaDeMovimientoParaElBloque(it.noCuenta ?: "otro", it.cuenta, it.esIngreso, it.monto, it.moneda) },
                ),
            )
        }
    }.trim()
}

/**
 * **Lo que se movió sin ser gasto ni ingreso**, para poder contestar «¿cuánto le pagué a la tarjeta?»
 * sin tocar «Salió». Por motivo y por cuenta, con el sentido de la plata dicho en palabras: de una
 * cuenta SALIÓ y a la tarjeta ENTRÓ, y las dos patas son el mismo pago — sumarlas sería contarlo
 * dos veces, y el encabezado lo advierte.
 */
internal fun bloqueDeLoQueNoCuenta(fuera: List<FilaDeMovimientoParaElBloque>): String = buildString {
    appendLine(
        "== Fuera de los gastos y de los ingresos (NO suman en Entró ni en Salió; un traspaso o " +
            "un pago de tarjeta tiene dos patas, la que sale y la que entra, y son el mismo pago) ==",
    )
    fuera.groupBy { it.moneda }.toSortedMap().forEach { (moneda, deEsaMoneda) ->
        deEsaMoneda.groupBy { it.motivo }.forEach { (motivo, filas) ->
            val partes = filas.groupBy { it.cuenta to it.esIngreso }
                .map { (clave, delGrupo) ->
                    val (cuenta, entro) = clave
                    "${if (entro) "entró a" else "salió de"} $cuenta ${delGrupo.sumOf { it.monto }}"
                }
            appendLine("- ${motivo.replaceFirstChar { it.uppercase() }} ($moneda, ${filas.size} movimientos): ${partes.joinToString("; ")}")
        }
    }
}.trim()

/** Lo mínimo que necesita [bloqueDeLoQueNoCuenta]; aparte para poder probarlo sin base. */
internal data class FilaDeMovimientoParaElBloque(
    val motivo: String,
    val cuenta: String,
    val esIngreso: Boolean,
    val monto: Long,
    val moneda: String,
)


/**
 * **Por qué un movimiento no cuenta en el mes**, en las palabras del dueño; `null` si cuenta. Usa
 * [isCashFlow] para decidir —la misma regla del Inicio, sin copiarla— y solo pone el rótulo.
 */
internal fun porQueNoCuentaEnElMes(tipoDeCuenta: AccountType?, tipo: TransactionType, categoria: String): String? {
    if (tipoDeCuenta == null || isCashFlow(tipoDeCuenta, tipo, categoria)) return null
    return when (categoria) {
        CARD_PAYMENT_CATEGORY -> "pago de tarjeta"
        TRANSFER_CATEGORY -> "traspaso entre sus cuentas"
        OPENING_CATEGORY -> "saldo inicial de una cuenta"
        ADJUSTMENT_CATEGORY -> "ajuste de saldo"
        PAYROLL_DEDUCTION_CATEGORY -> "descuento de nómina"
        THIRD_PARTY_PAYMENT_CATEGORY -> "cuota que paga un tercero"
        ORPHANED_LEG_CATEGORY -> "pata de un traspaso con una cuenta borrada"
        else -> when (tipoDeCuenta) {
            AccountType.LOAN -> "movimiento de la cuenta de un crédito"
            AccountType.CREDIT_CARD -> "abono a la tarjeta"
            else -> "no cuenta en el mes"
        }
    }
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

// ── Personas y comercios ─────────────────────────────────────────────────────

/**
 * **Lo enviado y lo recibido de una persona o comercio guardado**, entre dos fechas de calendario
 * (sin fechas, los últimos [MESES_HACIA_ATRAS_POR_DEFECTO] meses). Sin `nombre`, la lista de todos
 * con lo de cada uno en el rango. Las cifras son las de su ficha: el mismo reconocimiento por número,
 * llave o nombre ([movimientosHaciaElDestino]/[movimientosDesdeElDestino]) y los mismos movimientos.
 * Lo enviado y lo recibido van siempre aparte, por moneda.
 */
private suspend fun consultarPersonaOComercio(uid: String, args: Map<String, String>): String {
    val desde = fechaDe(args["desde"]) ?: AppClock.today().minusMonths(MESES_HACIA_ATRAS_POR_DEFECTO)
    val hasta = fechaDe(args["hasta"]) ?: AppClock.today()
    val terceros = tercerosDe(uid)
    if (terceros.isEmpty()) {
        return "El usuario no tiene a nadie guardado en «$PERSONAS_Y_COMERCIOS». Se guardan desde ahí o desde el " +
            "aviso del banco de una transferencia («¿De quién es la cuenta …?»)."
    }
    val inicio = appDateToEpochMillis(desde)
    val finExclusivo = appDateToEpochMillis(hasta.plusDays(1))
    val completar = paraCompletar(uid)
    val enRango = completar.eventos.filter { it.timestamp >= inicio && it.timestamp < finExclusivo }
    fun cifras(m: Map<String, Long>) = if (m.isEmpty()) "0" else m.entries.sortedBy { it.key }.joinToString(" y ") { "${it.value} ${it.key}" }

    val nombre = args["nombre"]?.takeIf { it.isNotBlank() }
    if (nombre == null) {
        return buildString {
            appendLine("Personas y comercios guardados, con lo de entre $desde y $hasta (enviado y recibido van aparte, nunca se suman):")
            terceros.map(completar::completar).forEach { d ->
                val ida = totalesHaciaElDestino(movimientosHaciaElDestino(d, enRango))
                val vuelta = totalesHaciaElDestino(movimientosDesdeElDestino(d, enRango))
                val tipo = d.tipo?.comoSeDice()?.lowercase() ?: "persona"
                val nota = d.deQuien?.let { ", «$it»" }.orEmpty()
                appendLine("- ${d.nombre} ($tipo$nota): le envió ${cifras(ida)}; recibió de ahí ${cifras(vuelta)}")
            }
        }.trim()
    }
    val tercero = terceroQueNombra(nombre, terceros)
        ?: return "Ninguno de sus guardados se reconoce como «$nombre» (o se parece a más de uno). Los guardados son: " +
            terceros.joinToString(", ") { it.nombre } + ". Vuelve a consultar con uno de esos nombres."
    val d = completar.completar(tercero)
    val enviados = movimientosHaciaElDestino(d, enRango)
    val recibidos = movimientosDesdeElDestino(d, enRango)
    val ajustes = ajustesDePeriodoDe(uid)
    return buildString {
        val tipo = d.tipo?.comoSeDice()?.lowercase() ?: "persona"
        appendLine("${d.nombre} ($tipo${d.deQuien?.let { ", «$it»" }.orEmpty()}), entre $desde y $hasta:")
        appendLine("- Le envió: ${cifras(totalesHaciaElDestino(enviados))} en ${enviados.size} movimientos")
        appendLine("- Recibió de ahí: ${cifras(totalesHaciaElDestino(recibidos))} en ${recibidos.size} movimientos")
        appendLine("(Enviado y recibido van aparte: no se suman ni se restan entre sí.)")
        val porPeriodo = loDeCadaPeriodo(enviados, recibidos, ajustes)
        if (porPeriodo.size > 1) {
            appendLine("Por período del usuario:")
            porPeriodo.forEach { p ->
                appendLine("- ${nombreDe(p.periodo)}: envió ${cifras(p.enviado)}; recibió ${cifras(p.recibido)}")
            }
        }
        val todos = (enviados + recibidos).sortedByDescending { it.timestamp }
        if (todos.isNotEmpty()) {
            appendLine("Movimientos:")
            todos.take(TOPE_DE_MOVIMIENTOS_DE_UN_TERCERO).forEach { ev ->
                val signo = if (ev.type == TransactionType.INCOME) "+" else "-"
                appendLine("- ${epochMillisToAppDateString(ev.timestamp)} · ${ev.description}: $signo${ev.amount} ${ev.currency}")
            }
            if (todos.size > TOPE_DE_MOVIMIENTOS_DE_UN_TERCERO) {
                appendLine("(y ${todos.size - TOPE_DE_MOVIMIENTOS_DE_UN_TERCERO} más que no se listan; los totales de arriba los suman todos)")
            }
        }
    }.trim()
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
            // Ola 3: lo que no cuenta como flujo YA NO se descarta acá —se rotula—. Descartarlo
            // dejaba al asistente sin poder contestar «¿cuánto le pagué a la Master Black?»; los
            // totales lo siguen dejando afuera (ver [totalesPorCategoria]).
            .map { fila ->
                FilaDeMovimiento(
                    fecha = epochMillisToAppDateString(fila[Events.timestamp]),
                    nombre = fila[Events.description],
                    categoria = fila[Events.category],
                    monto = fila[Events.amount],
                    moneda = fila[Events.currency],
                    esIngreso = fila[Events.type] == TransactionType.INCOME.name,
                    cuenta = nombreDeCuenta[fila[Events.accountId]] ?: "otra cuenta",
                    noCuenta = porQueNoCuentaEnElMes(
                        tipoDeCuenta[fila[Events.accountId]],
                        TransactionType.valueOf(fila[Events.type]),
                        fila[Events.category],
                    ),
                )
            }
            .sortedByDescending { it.fecha }
    }
}

private fun renglon(f: FilaDeMovimiento): String {
    val signo = if (f.esIngreso) "+" else "-"
    val rotulo = f.noCuenta?.let { " [NO cuenta como gasto ni ingreso: $it]" }.orEmpty()
    return "${f.fecha} · ${f.nombre} (${f.categoria}, ${f.cuenta}): $signo${f.monto} ${f.moneda}$rotulo"
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

// ── Ola 4: lo que viene ──────────────────────────────────────────────────────

/**
 * **La caja proyectada, en texto para el modelo**: cada cifra con de dónde sale y qué supone, y lo
 * que NO entró con su porqué. Es la misma cuenta que la gráfica de Plan ([cajaProyectada] en
 * `:core`, sobre la misma lista del período), así que lo que diga el asistente y lo que diga la
 * pantalla no pueden separarse.
 */
internal fun textoDeLaCaja(d: CajaDelDueno): String {
    val caja = d.caja ?: return "No hay días que proyectar: el período del usuario no está en curso."
    val desde = caja.dias.first().fecha
    val hasta = caja.dias.last().fecha
    return buildString {
        appendLine("Caja proyectada día a día del $desde al $hasta (cierre del período del usuario), en pesos.")
        appendLine("DE DÓNDE SALE:")
        appendLine("- Tu plata hoy: ${caja.tuPlataHoy} (la misma cifra de «Tu plata» de la app).")
        val gasto = d.gasto
        if (gasto.porDia != null) {
            val cuales = gasto.periodos.joinToString("; ") { "${it.periodo}: ${it.total} en ${it.dias} días" }
            appendLine(
                "- SUPUESTO: gasta como siempre, ${gasto.porDia} por día desde mañana. Es el promedio diario " +
                    "de su gasto variable en ${gasto.periodos.size} período(s) cerrado(s) ($cuales). Cuenta el " +
                    "gasto con tarjeta como si saliera de su plata el mismo día.",
            )
        } else {
            appendLine(
                "- SIN gasto del día a día: todavía no hay un período cerrado completo con qué estimarlo, así " +
                    "que la proyección solo resta los pagos fijos y es optimista. Dilo.",
            )
        }
        val pendientes = d.pagos.filter { !it.pagado }
        if (pendientes.isEmpty()) appendLine("- No quedan pagos ni ingresos pendientes en la lista del período.")
        else {
            appendLine("- Pagos e ingresos pendientes de la lista «Pagos del período» (lo ya pagado no se vuelve a restar):")
            caja.dias.forEach { dia ->
                dia.movimientos.forEach { m ->
                    appendLine("  ${dia.fecha}: ${m.nombre} ${if (m.monto >= 0) "+" else "-"}${kotlin.math.abs(m.monto)}")
                }
            }
        }
        if (caja.sinContar.isNotEmpty()) {
            appendLine("- NO ENTRAN a la cuenta (falta un dato; no lo estimes): ${caja.sinContar.joinToString("; ")}.")
        }
        appendLine("RESULTADO:")
        val bajo = caja.diaMasBajo
        appendLine("- Día más justo: ${bajo.fecha}, con ${bajo.saldo}" + (bajo.pagoMasGrande?.let { " (ese día sale ${it.nombre})" } ?: "") + ".")
        val rojo = caja.primerDiaEnRojo
        if (rojo == null) appendLine("- Ningún día queda en negativo.")
        else appendLine(
            "- PRIMER DÍA EN NEGATIVO: ${rojo.fecha}, queda en ${rojo.saldo}" +
                (rojo.pagoMasGrande?.let { " por ${it.nombre} (${-it.monto})" } ?: " por el gasto del día a día") + ".",
        )
        appendLine("- Al cierre ($hasta): ${caja.alCierre.saldo}.")
        appendLine("Saldo al cerrar cada día: " + caja.dias.joinToString("; ") { "${it.fecha} ${it.saldo}" } + ".")
    }.trim()
}

/**
 * **El plan de salida de deudas**, con el abono que pida el modelo (o $0) y la estrategia que pida
 * (avalancha si no dice). Las deudas son las mismas de las pantallas de Créditos (`creditosDe`,
 * `tarjetasDe`) y la cuenta es la misma de la pantalla «Cómo salir de tus deudas» ([planDeSalida]).
 */
private suspend fun simularAbono(uid: String, args: Map<String, String>): String {
    val abono = args["abono_mensual"]?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
    val estrategia = if (args["estrategia"]?.lowercase()?.contains("bola") == true) EstrategiaDeSalida.BOLA_DE_NIEVE
    else EstrategiaDeSalida.AVALANCHA
    val deudas = deudasParaSalir(creditosDe(uid), tarjetasDe(uid))
    val plan = planDeSalida(deudas, abono, estrategia)
    val otra = planDeSalida(
        deudas,
        abono,
        if (estrategia == EstrategiaDeSalida.AVALANCHA) EstrategiaDeSalida.BOLA_DE_NIEVE else EstrategiaDeSalida.AVALANCHA,
    )
    val periodo = periodoDe(System.currentTimeMillis(), ajustesDePeriodoDe(uid))
    return textoDelPlanDeSalida(plan, otra, periodo)
}

/** Fuera de [simularAbono] para poder probarlo sin base. Ver el KDoc de [planDeSalida]. */
internal fun textoDelPlanDeSalida(plan: PlanDeSalida, otra: PlanDeSalida, periodoActual: PeriodoFinanciero): String = buildString {
    fun cuando(meses: Int?) = meses?.let { "$it cuotas, la última en ${nombreDe(periodoActual.mas((it - 1).coerceAtLeast(0)))}" }
        ?: "no se termina a este ritmo"
    fun nombreDeLa(e: EstrategiaDeSalida) = if (e == EstrategiaDeSalida.AVALANCHA) "avalancha (mayor tasa primero)" else "bola de nieve (menor saldo primero)"
    appendLine(
        "Plan de salida de deudas, estrategia ${nombreDeLa(plan.estrategia)}, con ${plan.abonoMensual} pesos extra al mes. " +
            "Supuestos: tasa y cuota constantes; el abono va entero a capital de la primera deuda del orden y, cuando " +
            "esa termina, pasa a la siguiente; la cuota que se libera NO se suma al abono.",
    )
    if (plan.enElCalculo.isEmpty()) {
        appendLine("Ninguna deuda que salga de su bolsillo tiene los datos para calcular (tasa y cuota).")
    } else {
        appendLine("ORDEN PARA EL ABONO (deudas que salen de su bolsillo y tienen tasa y cuota):")
        plan.enElCalculo.forEach { s ->
            val d = s.deuda
            appendLine(
                "${s.orden}. ${d.nombre}: saldo ${d.saldo}, tasa ${d.tasaEa ?: 0.0} % EA" +
                    (if (d.sinIntereses) " (no cobra intereses)" else "") +
                    ", ${if (d.tipo.name == "TARJETA") "pago mínimo" else "cuota"} ${d.cuota}, interés de este mes ${d.interesDelMes}. " +
                    "Sin abono: ${cuando(s.mesesSinAbono)}${s.interesSinAbono?.let { ", $it de interés por pagar" } ?: ""}. " +
                    "Con el abono: ${cuando(s.mesesConAbono)}${s.interesConAbono?.let { ", $it de interés por pagar" } ?: ""}." +
                    (s.interesAhorrado?.let { " Ahorra $it de interés." } ?: ""),
            )
        }
        appendLine("Interés total que ahorra el abono (en las deudas que terminan con y sin él): ${plan.interesAhorrado}.")
        if (plan.seTerminanSoloConAbono.isNotEmpty()) {
            appendLine("Solo se terminan CON el abono: ${plan.seTerminanSoloConAbono.joinToString { it.deuda.nombre }}.")
        }
        plan.mesesHastaSalir?.let { appendLine("Con el abono sale de todas estas en ${cuando(it)}.") }
        appendLine(
            "Con la otra estrategia, ${nombreDeLa(otra.estrategia)}, el orden sería " +
                otra.enElCalculo.joinToString(" → ") { it.deuda.nombre } + " y ahorraría ${otra.interesAhorrado} de interés.",
        )
    }
    if (plan.faltanDatos.isNotEmpty()) {
        appendLine("NO ENTRAN porque falta un dato (no lo estimes; dile que lo cargue en Créditos):")
        plan.faltanDatos.forEach { d ->
            val motivo = when (d.porQueNoEntra) {
                PorQueNoEntraAlPlan.FALTA_LA_TASA -> "falta la tasa"
                PorQueNoEntraAlPlan.FALTA_EL_PAGO_MINIMO -> "falta el pago mínimo"
                PorQueNoEntraAlPlan.FALTA_LA_CUOTA -> "falta la cuota"
                PorQueNoEntraAlPlan.EN_OTRA_MONEDA -> "está en ${d.moneda}"
                null -> ""
            }
            appendLine("- ${d.nombre} (saldo ${d.saldo} ${d.moneda}): $motivo.")
        }
    }
    if (plan.ajenas.isNotEmpty()) {
        appendLine("APARTE, no compiten por el abono porque la cuota no sale de su bolsillo:")
        plan.ajenas.forEach { d -> appendLine("- ${d.nombre} (saldo ${d.saldo}): la paga ${d.quienLaPaga}.") }
    }
    append("Es un cálculo con sus datos, no una recomendación financiera: dilo así.")
}
