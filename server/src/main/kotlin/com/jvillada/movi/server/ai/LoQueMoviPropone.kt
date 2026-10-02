package com.jvillada.movi.server.ai

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Tool
import com.jvillada.movi.server.balance.formatAmount
import com.jvillada.movi.server.db.Accounts
import com.jvillada.movi.server.db.AccionesPropuestas
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.server.routes.MAXIMO_DE_PARECIDOS
import com.jvillada.movi.server.routes.categoriaMalEscrita
import com.jvillada.movi.server.routes.categoriaQueMoviEscribeSola
import com.jvillada.movi.server.routes.categoryUsage
import com.jvillada.movi.server.routes.estadosDeLasOcurrenciasReales
import com.jvillada.movi.server.time.AppClock
import com.jvillada.movi.server.time.ajustesDelPeriodoSinSuspender
import com.jvillada.movi.server.time.appDateToEpochMillis
import com.jvillada.movi.server.time.epochMillisToAppDate
import com.jvillada.movi.shared.model.AccionPropuesta
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.EstadoDePropuesta
import com.jvillada.movi.shared.model.EventSource
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.ReconciliationStatus
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.TipoDeAccion
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.huellaDeUnMovimiento
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.rechazoDeLosTextos
import com.jvillada.movi.shared.model.rechazoDelMonto
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * # Movi propone; el dueño decide (Ola 3 · «Movi actúa»)
 *
 * Las herramientas de este archivo son las primeras del asistente que **no** son de lectura, y por
 * eso tienen una sola regla que no se negocia: **no escriben nada**. Cada una valida lo que el
 * modelo pidió contra los datos del dueño y, si todo cuadra, arma una [AccionPropuesta] que viaja en
 * la respuesta del chat. La app la pinta como tarjeta; «Hacerlo» llama al endpoint de siempre, con
 * su validación de siempre. Ver el KDoc de [AccionPropuesta] en `:core`.
 *
 * ### Lo que se valida ANTES de mostrar
 *
 * Una tarjeta que el dueño confirma de un toque es una tarjeta que casi nadie lee con lupa, así que
 * lo que llega a la pantalla tiene que poder hacerse tal cual:
 *
 * - **La cuenta existe y es del dueño** —se busca solo entre las suyas, por nombre—, no es un bien
 *   ni la cuenta de un crédito. Si el nombre pega con dos cuentas, no se elige: se pregunta.
 * - **El monto es mayor que 0** (la misma `rechazoDelMonto` que `POST /api/events`).
 * - **La categoría existe** (se usa su nombre tal cual lo tiene el dueño) **o se marca como nueva**,
 *   y nunca es una de las que Movi escribe sola (`categoriaQueMoviEscribeSola`).
 * - **Un pago se marca solo con un movimiento que existe**, y tiene que ser uno de los candidatos que
 *   el mismo checklist ofrecería. Es la regla del dueño: «el checklist lo tilda el movimiento».
 *
 * Lo que no pasa no llega a la app: el modelo recibe el motivo y tiene que corregir o preguntar.
 */

const val PROPONER_MOVIMIENTO = "proponer_movimiento"
const val PROPONER_CAMBIO_DE_CATEGORIA = "proponer_cambio_de_categoria"
const val PROPONER_RECURRENTE = "proponer_recurrente"
const val PROPONER_PAGO_HECHO = "proponer_pago_hecho"

/** Las herramientas que proponen (y no leen). Ver [esHerramientaQuePropone]. */
val HERRAMIENTAS_QUE_PROPONEN_NOMBRES: Set<String> =
    setOf(PROPONER_MOVIMIENTO, PROPONER_CAMBIO_DE_CATEGORIA, PROPONER_RECURRENTE, PROPONER_PAGO_HECHO, RECORDAR)

fun esHerramientaQuePropone(nombre: String): Boolean = nombre in HERRAMIENTAS_QUE_PROPONEN_NOMBRES

/**
 * **Cuántas propuestas por turno.** Una tarjeta es una pregunta al dueño; cinco tarjetas de una son
 * una lista que nadie lee antes de tocar «Hacerlo».
 */
internal const val PROPUESTAS_POR_TURNO = 3

/** Lo que salió de pedir una propuesta: o una tarjeta lista, o el motivo por el que no. */
sealed interface ResultadoDePropuesta {
    /** Lo que se le contesta al modelo como resultado de la herramienta. */
    val paraElModelo: String

    data class Lista(val accion: AccionPropuesta) : ResultadoDePropuesta {
        override val paraElModelo: String =
            "Propuesta lista, PERO NO ESTÁ HECHA: el dueño ve una tarjeta que dice «${accion.frase}» y " +
                "decide con «Hacerlo» o «No». No digas que ya quedó hecho ni lo repitas con otras palabras."
    }

    data class Invalida(val motivo: String) : ResultadoDePropuesta {
        override val paraElModelo: String =
            "No pude proponer eso y el dueño NO ve ninguna tarjeta: $motivo Corrige los datos o pregúntale al dueño."
    }
}

/**
 * **Las propuestas de UN turno.** El bucle de herramientas no sabe nada de tarjetas: se le pasa
 * [ejecutar] y esto junta lo que salió bien, en orden, para ponerlo en la respuesta.
 */
class PropuestasDelTurno(private val uid: String, private val hoy: LocalDate = AppClock.today()) {
    private val listas = linkedMapOf<String, AccionPropuesta>()

    /** Las llamadas (por id) que terminaron en una tarjeta. Ver [cierraElTurno]. */
    private val llamadasListas = mutableSetOf<String>()

    val propuestas: List<AccionPropuesta> get() = listas.values.toList()

    suspend fun ejecutar(llamada: LlamadaDeHerramienta): String {
        val resultado = if (listas.size >= PROPUESTAS_POR_TURNO) {
            ResultadoDePropuesta.Invalida("Ya hay $PROPUESTAS_POR_TURNO propuestas en este turno; espera a que el dueño las conteste.")
        } else {
            proponer(uid, llamada, hoy)
        }
        if (resultado is ResultadoDePropuesta.Lista) {
            // La misma propuesta dos veces (el modelo repitió la llamada) es UNA tarjeta.
            val repetida = listas.values.firstOrNull { it.frase == resultado.accion.frase }
            if (repetida == null) listas[resultado.accion.id] = resultado.accion
            llamadasListas += llamada.id
        }
        return resultado.paraElModelo
    }

    /**
     * ¿Se puede cerrar el turno sin otra vuelta al modelo? Sí cuando **todo** lo que pidió en esa
     * vuelta fueron propuestas y **todas** salieron bien: lo que diría después es «te dejé la
     * propuesta abajo», y esa vuelta es una llamada entera con el prefijo encima para decir eso.
     * Si alguna falló, hay que dejarlo contestar: tiene que explicar o preguntar.
     */
    fun cierraElTurno(llamadas: List<LlamadaDeHerramienta>): Boolean =
        llamadas.isNotEmpty() && llamadas.all { esHerramientaQuePropone(it.nombre) && it.id in llamadasListas }
}

/**
 * Lo que se le dice al dueño cuando el turno se cerró en las tarjetas (ver [PropuestasDelTurno.cierraElTurno]).
 * Sin cifras a propósito: las cifras están en la tarjeta, que el server armó con los datos validados.
 */
internal fun textoAlCerrarConPropuestas(textoDelModelo: String, cuantas: Int): String {
    val limpio = textoDelModelo.trim()
    // Lo que el modelo escribió antes de pedir la herramienta se usa solo si no trae cifras: no hay
    // una vuelta más para verificarlas, y una cifra suya que no coincida con la tarjeta confunde.
    if (limpio.isNotEmpty() && limpio.none { it.isDigit() }) return limpio
    return if (cuantas == 1) {
        "Te dejé la propuesta abajo. Si está bien, toca «Hacerlo»; si no, «No»."
    } else {
        "Te dejé las propuestas abajo. Revisa cada una: «Hacerlo» si está bien, «No» si no."
    }
}

// ── La validación ────────────────────────────────────────────────────────────

private class PropuestaInvalida(val motivo: String) : Exception(motivo)

private fun no(motivo: String): Nothing = throw PropuestaInvalida(motivo)

/** Valida y arma. Nunca lanza: lo que falla vuelve como [ResultadoDePropuesta.Invalida]. */
suspend fun proponer(uid: String, llamada: LlamadaDeHerramienta, hoy: LocalDate = AppClock.today()): ResultadoDePropuesta = try {
    val args = llamada.argumentos
    val accion = when (llamada.nombre) {
        PROPONER_MOVIMIENTO -> proponerMovimiento(uid, args, hoy)
        PROPONER_CAMBIO_DE_CATEGORIA -> proponerCambioDeCategoria(uid, args, hoy)
        PROPONER_RECURRENTE -> proponerRecurrente(uid, args)
        PROPONER_PAGO_HECHO -> proponerPagoHecho(uid, args, hoy)
        // Ola 3 · 2: recordar algo también es una propuesta. Ver `LoQueMoviSabeDeTi.kt`.
        RECORDAR -> proponerRecuerdo(uid, args)
        else -> no("No existe una herramienta que se llame «${llamada.nombre}».")
    }
    ResultadoDePropuesta.Lista(accion)
} catch (e: PropuestaInvalida) {
    ResultadoDePropuesta.Invalida(e.motivo)
} catch (e: PropuestaInvalidaPublica) {
    ResultadoDePropuesta.Invalida(e.motivo)
}

private fun nuevoIdDePropuesta() = "ap_" + UUID.randomUUID().toString().replace("-", "").take(20)

/** Una cuenta del dueño, como la necesita la validación. */
private data class CuentaDelDueno(val id: String, val nombre: String, val tipo: AccountType?, val moneda: String, val esBien: Boolean)

private fun Transaction.cuentasDelDueno(uid: String): List<CuentaDelDueno> =
    Accounts.selectAll().where { Accounts.userId eq uid }.map {
        CuentaDelDueno(
            id = it[Accounts.id],
            nombre = it[Accounts.name],
            tipo = runCatching { AccountType.valueOf(it[Accounts.type]) }.getOrNull(),
            moneda = it[Accounts.currency],
            esBien = it[Accounts.assetKind] != null,
        )
    }

/**
 * **La cuenta, por nombre y solo entre las del dueño.** Primero el nombre igual (sin tildes ni
 * mayúsculas); si no, la que lo contiene — él dice «la Nu» y la cuenta se llama «Nu Ahorros». Si
 * pega con dos, no se adivina: un movimiento en la cuenta equivocada es un saldo equivocado.
 */
private fun cuentaPorNombre(cuentas: List<CuentaDelDueno>, pedida: String?, paraQue: String): CuentaDelDueno {
    val buscada = pedida?.trim()?.removePrefix("la ")?.removePrefix("el ")?.takeIf { it.isNotBlank() }
        ?: no("Falta la cuenta $paraQue. Sus cuentas son: ${nombres(cuentas)}.")
    val aguja = normalizarParaBuscar(buscada)
    val usables = cuentas.filterNot { it.esBien }
    val iguales = usables.filter { normalizarParaBuscar(it.nombre) == aguja }
    val candidatas = iguales.ifEmpty { usables.filter { aguja in normalizarParaBuscar(it.nombre) } }
    return when (candidatas.size) {
        1 -> candidatas.single()
        0 -> no("El dueño no tiene una cuenta que se llame «$buscada». Sus cuentas son: ${nombres(cuentas)}.")
        else -> no("«$buscada» puede ser ${candidatas.joinToString(" o ") { "«${it.nombre}»" }}: pregúntale cuál.")
    }
}

private fun nombres(cuentas: List<CuentaDelDueno>) =
    cuentas.filterNot { it.esBien }.joinToString(", ") { "«${it.nombre}»" }.ifEmpty { "(ninguna)" }

/**
 * **La categoría, con el nombre que ya tiene el dueño.** «comida» se vuelve «Comida» si existe —si
 * no, la propuesta crearía una categoría casi igual a la que ya usa—. Una que no existe se acepta
 * pero se marca ([Categoria.nueva]) para que la tarjeta lo diga. Las que Movi escribe sola
 * (traspaso, saldo inicial, pago de tarjeta…) no se proponen nunca: sacarían el movimiento del mes.
 */
private data class Categoria(val nombre: String, val nueva: Boolean)

private fun Transaction.categoriaDelDueno(uid: String, pedida: String?): Categoria {
    val limpia = pedida?.trim()?.takeIf { it.isNotBlank() } ?: no("Falta la categoría.")
    categoriaMalEscrita(limpia)?.let { no(it) }
    val existentes = categoryUsage(uid, 0L, 0L).map { it.name }
    val existente = existentes.firstOrNull { normalizarParaBuscar(it) == normalizarParaBuscar(limpia) }
    val nombre = existente ?: limpia
    categoriaQueMoviEscribeSola(nombre)?.let { no(it) }
    if (nombre == CARD_PAYMENT_CATEGORY) no("«$CARD_PAYMENT_CATEGORY» no se propone desde el chat: se registra en Créditos, como un pago de la tarjeta.")
    return Categoria(nombre, nueva = existente == null)
}

/**
 * El monto, como lo escriba el modelo: `45000`, `45000.0`, `"45.000"`, `"$45.000"`. **Mayor que
 * cero**: un negativo o un cero no se convierte en otra cosa, se rechaza.
 */
internal fun montoDe(crudo: String?): Long {
    val texto = crudo?.replace("$", "")?.replace("COP", "", ignoreCase = true)?.replace(" ", "")?.trim()
        ?.takeIf { it.isNotEmpty() } ?: no("Falta el monto.")
    val negativo = texto.startsWith("-")
    val sinSigno = texto.removePrefix("-").removePrefix("+")
    val valor: Long? = when {
        Regex("""\d{1,3}(\.\d{3})+""").matches(sinSigno) -> sinSigno.replace(".", "").toLongOrNull()
        Regex("""\d{1,3}(,\d{3})+""").matches(sinSigno) -> sinSigno.replace(",", "").toLongOrNull()
        Regex("""\d+([.,]\d+)?""").matches(sinSigno) -> sinSigno.replace(",", ".").toDoubleOrNull()?.let { Math.round(it) }
        else -> null
    }
    valor ?: no("No entendí el monto «$crudo». Mándalo como número, por ejemplo 45000.")
    val monto = if (negativo) -valor else valor
    if (monto <= 0) no("El monto tiene que ser mayor que 0 (vino «$crudo»).")
    rechazoDelMonto(monto)?.let { no(it) }
    return monto
}

private fun fechaDe(crudo: String?, hoy: LocalDate): LocalDate {
    val texto = crudo?.trim()?.takeIf { it.isNotEmpty() } ?: return hoy
    val fecha = try {
        LocalDate.parse(texto)
    } catch (_: DateTimeParseException) {
        no("No entendí la fecha «$texto». Usa AAAA-MM-DD.")
    }
    if (fecha.isAfter(hoy)) no("La fecha ${fecha} todavía no llega: solo se anota lo que ya pasó.")
    if (fecha.year !in 2000..2100) no("Esa fecha no es de este siglo.")
    return fecha
}

private fun esIngreso(tipo: String?): Boolean = tipo?.trim()?.lowercase()?.startsWith("ingres") == true

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
    "noviembre", "diciembre",
)

internal fun fechaEnPalabras(fecha: LocalDate, hoy: LocalDate): String = when (fecha) {
    hoy -> "hoy"
    hoy.minusDays(1) -> "ayer"
    else -> "el ${fecha.dayOfMonth} de ${MESES[fecha.monthValue - 1]}" + if (fecha.year != hoy.year) " de ${fecha.year}" else ""
}

// ── Cada tipo ────────────────────────────────────────────────────────────────

private suspend fun proponerMovimiento(uid: String, args: Map<String, String>, hoy: LocalDate): AccionPropuesta {
    val monto = montoDe(args["monto"])
    val ingreso = esIngreso(args["tipo"])
    val fecha = fechaDe(args["fecha"], hoy)
    val (cuenta, categoria) = dbQuery {
        val cuenta = cuentaPorNombre(cuentasDelDueno(uid), args["cuenta"], if (ingreso) "a la que entró" else "de la que salió")
        if (cuenta.tipo == AccountType.LOAN) {
            no("«${cuenta.nombre}» es un crédito: el movimiento va en la cuenta de donde salió (o a donde entró) la plata.")
        }
        cuenta to categoriaDelDueno(uid, args["categoria"])
    }
    val nota = args["nota"]?.trim()?.takeIf { it.isNotBlank() }
    val descripcion = nota ?: categoria.nombre
    rechazoDeLosTextos(categoria.nombre, descripcion)?.let { no(it) }
    val ahora = System.currentTimeMillis()
    val movimiento = FinancialEvent(
        // **El id se fija acá, no al confirmar.** Si el dueño toca «Hacerlo» dos veces —o el
        // teléfono reintenta—, el mismo id llega dos veces y `POST /api/events` lo toma como el
        // mismo movimiento que vuelve, no como uno nuevo.
        id = "ev_${UUID.randomUUID()}",
        accountId = cuenta.id,
        type = if (ingreso) TransactionType.INCOME else TransactionType.EXPENSE,
        amount = monto,
        currency = cuenta.moneda,
        category = categoria.nombre,
        description = descripcion,
        // Mediodía del día: el mismo criterio que la hoja de «Agregar» cuando se elige otra fecha
        // (lejos de los dos bordes del día, así ningún huso lo cambia de fecha).
        timestamp = if (fecha == hoy) ahora else appDateToEpochMillis(fecha) + 12 * 3_600_000L,
        source = EventSource.MANUAL,
        reconciliationStatus = ReconciliationStatus.RECONCILED,
        createdAt = ahora,
    )
    val que = if (ingreso) "un ingreso" else "un gasto"
    val desde = if (ingreso) "a" else "desde"
    val frase = buildString {
        append("Anotar $que de ${formatAmount(monto, cuenta.moneda)} en ${categoria.nombre}")
        if (categoria.nueva) append(" (categoría nueva)")
        append(" $desde ${cuenta.nombre}, ${fechaEnPalabras(fecha, hoy)}")
        nota?.let { append(" · «$it»") }
    }
    return AccionPropuesta(
        id = nuevoIdDePropuesta(),
        tipo = TipoDeAccion.ANOTAR_MOVIMIENTO,
        frase = frase,
        movimiento = movimiento,
        categoria = categoria.nombre,
        categoriaNueva = categoria.nueva,
    )
}

/**
 * **Cambiar la categoría de un movimiento o de varios parecidos.** El modelo no conoce ids —no los
 * ve, y está bien que no—: dice un texto («Rappi») y Movi busca los movimientos del dueño que lo
 * dicen, o que tienen la misma huella (el mismo destinatario, ver `huellaDeUnMovimiento`, que es lo
 * que usa el lote de parecidos de la Ola 23). Quedan afuera los mismos que la ruta del lote no
 * puede mover: anulados, patas de traspaso, aperturas y lo que Movi escribe solo. Y los que ya
 * están en esa categoría, que no cambiarían nada.
 */
private suspend fun proponerCambioDeCategoria(uid: String, args: Map<String, String>, hoy: LocalDate): AccionPropuesta {
    val texto = args["texto"]?.trim()?.takeIf { it.length >= 2 }
        ?: no("Falta el texto que identifica los movimientos (por ejemplo «Rappi»).")
    val desde = args["desde"]?.takeIf { it.isNotBlank() }?.let { fechaDe(it, hoy) } ?: hoy.minusMonths(MESES_HACIA_ATRAS_POR_DEFECTO)
    val hasta = args["hasta"]?.takeIf { it.isNotBlank() }?.let { fechaDe(it, hoy) } ?: hoy
    val aguja = normalizarParaBuscar(texto)
    val huella = huellaDeUnMovimiento(texto)
    val (categoria, encontrados) = dbQuery {
        val categoria = categoriaDelDueno(uid, args["categoria"])
        val anulados = VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()
        val inicio = appDateToEpochMillis(desde)
        val fin = appDateToEpochMillis(hasta.plusDays(1))
        val filas = Events.selectAll()
            .where { (Events.userId eq uid) and (Events.timestamp greaterEq inicio) and (Events.timestamp less fin) }
            .orderBy(Events.timestamp to SortOrder.DESC)
            .filterNot { it[Events.id] in anulados }
            .filter { it[Events.transferId] == null }
            .filter { categoriaQueMoviEscribeSola(it[Events.category]) == null && it[Events.category] != CARD_PAYMENT_CATEGORY }
            .filter { normalizarParaBuscar(it[Events.category]) != normalizarParaBuscar(categoria.nombre) }
            .filter { fila ->
                val nombre = fila[Events.merchant]?.takeIf { it.isNotBlank() } ?: fila[Events.description]
                aguja in normalizarParaBuscar(fila[Events.description]) ||
                    aguja in normalizarParaBuscar(fila[Events.merchant].orEmpty()) ||
                    (huella != null && huellaDeUnMovimiento(nombre) == huella)
            }
        categoria to filas
    }
    if (encontrados.isEmpty()) {
        no("No encontré movimientos que digan «$texto» entre $desde y $hasta fuera de «${categoria.nombre}».")
    }
    if (encontrados.size > MAXIMO_DE_PARECIDOS) {
        no("Son ${encontrados.size} movimientos que dicen «$texto»: demasiados de una vez. Acota con fechas.")
    }
    val deDonde = encontrados.map { it[Events.category] }.distinct()
    val frase = buildString {
        if (encontrados.size == 1) {
            val f = encontrados.single()
            append(
                "Pasar «${f[Events.description]}» de ${formatAmount(f[Events.amount], f[Events.currency])} " +
                    "(${fechaEnPalabras(epochMillisToAppDate(f[Events.timestamp]), hoy)}) de ${deDonde.single()} a ${categoria.nombre}",
            )
        } else {
            append("Pasar ${encontrados.size} movimientos de «$texto» a ${categoria.nombre}")
            append(" (hoy están en ${deDonde.take(3).joinToString(", ")}${if (deDonde.size > 3) "…" else ""})")
        }
        if (categoria.nueva) append(" · categoría nueva")
    }
    return AccionPropuesta(
        id = nuevoIdDePropuesta(),
        tipo = TipoDeAccion.CAMBIAR_CATEGORIA,
        frase = frase,
        idsDeMovimientos = encontrados.map { it[Events.id] },
        categoria = categoria.nombre,
        categoriaNueva = categoria.nueva,
    )
}

private suspend fun proponerRecurrente(uid: String, args: Map<String, String>): AccionPropuesta {
    val nombre = args["nombre"]?.trim()?.takeIf { it.isNotBlank() } ?: no("Falta el nombre del recurrente.")
    if (nombre.length > 100) no("El nombre es muy largo.")
    val monto = montoDe(args["monto"])
    val dia = args["dia"]?.trim()?.toDoubleOrNull()?.toInt()?.takeIf { it in 1..31 }
        ?: no("Falta el día del mes (de 1 a 31) en que se paga o llega.")
    val ingreso = esIngreso(args["tipo"])
    val (cuenta, categoria) = dbQuery {
        val yaExiste = RecurringRules.selectAll().where { RecurringRules.userId eq uid }
            .map { it[RecurringRules.name] }
            .firstOrNull { normalizarParaBuscar(it) == normalizarParaBuscar(nombre) }
        if (yaExiste != null) no("Ya existe un recurrente que se llama «$yaExiste».")
        val cuenta = args["cuenta"]?.takeIf { it.isNotBlank() }?.let {
            cuentaPorNombre(cuentasDelDueno(uid), it, if (ingreso) "a la que llega" else "de la que sale")
        }
        cuenta to categoriaDelDueno(uid, args["categoria"])
    }
    val regla = RecurringRule(
        id = "",
        name = nombre,
        category = categoria.nombre,
        amount = monto,
        dayOfMonth = dia,
        type = if (ingreso) TransactionType.INCOME else TransactionType.EXPENSE,
        accountId = cuenta?.id,
    )
    val frase = buildString {
        append("Crear el ${if (ingreso) "ingreso" else "pago"} recurrente «$nombre» de ${formatAmount(monto, "COP")}, ")
        append("el día $dia de cada mes, en ${categoria.nombre}")
        if (categoria.nueva) append(" (categoría nueva)")
        cuenta?.let { append(if (ingreso) ", a ${it.nombre}" else ", desde ${it.nombre}") }
    }
    return AccionPropuesta(
        id = nuevoIdDePropuesta(),
        tipo = TipoDeAccion.CREAR_RECURRENTE,
        frase = frase,
        recurrente = regla,
        categoria = categoria.nombre,
        categoriaNueva = categoria.nueva,
    )
}

/**
 * **Marcar un pago del período con un movimiento que YA existe.** Es el «Es este» del checklist, y
 * por eso sale de la misma lectura que el checklist ([estadosDeLasOcurrenciasReales]): la regla
 * tiene que tener la ocurrencia abierta, y el movimiento tiene que estar entre los candidatos que
 * esa misma lectura ofrece. Sin movimiento no hay propuesta — nunca.
 */
private suspend fun proponerPagoHecho(uid: String, args: Map<String, String>, hoy: LocalDate): AccionPropuesta {
    val pedido = args["pago"]?.trim()?.takeIf { it.isNotBlank() } ?: no("Falta el nombre del pago recurrente.")
    val aguja = normalizarParaBuscar(pedido)
    val texto = args["texto"]?.trim()?.takeIf { it.isNotBlank() }?.let(::normalizarParaBuscar)
    val monto = args["monto"]?.takeIf { it.isNotBlank() }?.let { montoDe(it) }
    return dbQuery {
        val reglas = RecurringRules.selectAll().where { RecurringRules.userId eq uid }
            .associate { it[RecurringRules.id] to it[RecurringRules.name] }
            .filterKeys { !it.startsWith(CREDIT_RULE_PREFIX) && !it.startsWith(CARD_RULE_PREFIX) }
        val iguales = reglas.filterValues { normalizarParaBuscar(it) == aguja }
        val elegidas = iguales.ifEmpty { reglas.filterValues { aguja in normalizarParaBuscar(it) } }
        val (reglaId, nombreDeLaRegla) = when (elegidas.size) {
            1 -> elegidas.entries.single().toPair()
            0 -> no(
                "No hay un pago recurrente que se llame «$pedido». La cuota de un crédito y el pago de una tarjeta " +
                    "no se marcan aquí: se registran en Créditos.",
            )
            else -> no("«$pedido» puede ser ${elegidas.values.joinToString(" o ") { "«$it»" }}: pregúntale cuál.")
        }
        val estado = estadosDeLasOcurrenciasReales(uid, hoy, ajustesDelPeriodoSinSuspender(uid))
            .firstOrNull { it.ruleId == reglaId }
            ?: no("«$nombreDeLaRegla» no tiene un pago por confirmar en este período.")
        if (estado.occurred) no("«$nombreDeLaRegla» ya está marcado como hecho en este período.")
        val candidatos = estado.candidates
            .filter { c -> texto == null || texto in normalizarParaBuscar(c.description) || texto in normalizarParaBuscar(c.merchant.orEmpty()) }
            .filter { c -> monto == null || c.amount == monto }
        val elegido = when {
            estado.candidates.isEmpty() -> no(
                "No hay ningún movimiento que pueda ser el pago de «$nombreDeLaRegla». Un pago se marca SOLO con su " +
                    "movimiento: si ya lo pagó, primero hay que anotar el movimiento ($PROPONER_MOVIMIENTO).",
            )
            candidatos.size == 1 -> candidatos.single()
            candidatos.isEmpty() -> no(
                "Ningún candidato coincide. Los movimientos que pueden ser ese pago son: " +
                    estado.candidates.joinToString("; ") { "${it.description} ${formatAmount(it.amount, it.currency)} (${epochMillisToAppDate(it.timestamp)})" } + ".",
            )
            else -> no(
                "Hay más de un movimiento que puede ser ese pago: " +
                    candidatos.joinToString("; ") { "${it.description} ${formatAmount(it.amount, it.currency)} (${epochMillisToAppDate(it.timestamp)})" } +
                    ". Pregúntale cuál fue.",
            )
        }
        AccionPropuesta(
            id = nuevoIdDePropuesta(),
            tipo = TipoDeAccion.MARCAR_PAGO_HECHO,
            frase = "Marcar «$nombreDeLaRegla» de este período como hecho con «${elegido.description}» de " +
                "${formatAmount(elegido.amount, elegido.currency)} (${fechaEnPalabras(epochMillisToAppDate(elegido.timestamp), hoy)})",
            reglaId = reglaId,
            periodo = estado.period,
            eventId = elegido.id,
        )
    }
}

// ── Guardarlas y leerlas ─────────────────────────────────────────────────────

private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/** Guarda las propuestas de un turno. Nunca lanza: si falla, las tarjetas igual llegan (sin poder resolverse). */
suspend fun guardarPropuestas(uid: String, turnoId: String, propuestas: List<AccionPropuesta>, ahora: Long = System.currentTimeMillis()): Boolean =
    if (propuestas.isEmpty()) true else runCatching {
        dbQuery {
            AccionesPropuestas.batchInsert(propuestas) { p ->
                this[AccionesPropuestas.id] = p.id
                this[AccionesPropuestas.userId] = uid
                this[AccionesPropuestas.turnoId] = turnoId
                this[AccionesPropuestas.tipo] = p.tipo.name
                this[AccionesPropuestas.datos] = json.encodeToString(AccionPropuesta.serializer(), p)
                this[AccionesPropuestas.estado] = p.estado.name
                this[AccionesPropuestas.creadaEn] = ahora
            }
        }
        true
    }.getOrDefault(false)

/**
 * Lo que el dueño decidió. `null` si la propuesta no existe o es de otro (la ruta contesta 404 —
 * igual en los dos casos, como en todo este server—); `false` si ya estaba resuelta de otra forma.
 */
suspend fun resolverPropuesta(uid: String, id: String, estado: EstadoDePropuesta, ahora: Long = System.currentTimeMillis()): Boolean? = dbQuery {
    val fila = AccionesPropuestas.selectAll()
        .where { (AccionesPropuestas.id eq id) and (AccionesPropuestas.userId eq uid) }
        .firstOrNull() ?: return@dbQuery null
    val actual = EstadoDePropuesta.valueOf(fila[AccionesPropuestas.estado])
    when {
        actual == estado -> true
        actual != EstadoDePropuesta.PENDIENTE -> false
        else -> {
            AccionesPropuestas.update({ (AccionesPropuestas.id eq id) and (AccionesPropuestas.userId eq uid) }) {
                it[AccionesPropuestas.estado] = estado.name
                it[resueltaEn] = ahora
            }
            true
        }
    }
}

/** Las propuestas de esos turnos, con su estado de hoy, agrupadas por turno. */
internal suspend fun propuestasDeLosTurnos(uid: String, turnos: List<String>): Map<String, List<AccionPropuesta>> =
    if (turnos.isEmpty()) emptyMap() else dbQuery {
        AccionesPropuestas.selectAll()
            .where { (AccionesPropuestas.userId eq uid) and (AccionesPropuestas.turnoId inList turnos) }
            .orderBy(AccionesPropuestas.creadaEn to SortOrder.ASC)
            .mapNotNull { fila ->
                val p = runCatching { json.decodeFromString(AccionPropuesta.serializer(), fila[AccionesPropuestas.datos]) }.getOrNull()
                    ?: return@mapNotNull null
                fila[AccionesPropuestas.turnoId] to p.copy(estado = EstadoDePropuesta.valueOf(fila[AccionesPropuestas.estado]))
            }
            .groupBy({ it.first }, { it.second })
    }

/** Cuántas propuestas recientes se le recuerdan al modelo. */
internal const val PROPUESTAS_QUE_SE_RECUERDAN = 6

/**
 * **Lo que el dueño hizo con lo que se le propuso**, para el turno siguiente: «Si el dueño dice
 * "No", el asistente lo sabe». Va pegado a la pregunta (después de todo lo cacheado), solo con las
 * últimas de la conversación en curso, y `null` si no hubo ninguna — que es casi siempre.
 */
internal suspend fun loQueElDuenoDecidio(uid: String, desde: Long): String? {
    val recientes = dbQuery {
        AccionesPropuestas.selectAll()
            .where { (AccionesPropuestas.userId eq uid) and (AccionesPropuestas.creadaEn greater desde) }
            .orderBy(AccionesPropuestas.creadaEn to SortOrder.DESC)
            .limit(PROPUESTAS_QUE_SE_RECUERDAN)
            .mapNotNull { fila ->
                runCatching { json.decodeFromString(AccionPropuesta.serializer(), fila[AccionesPropuestas.datos]) }.getOrNull()
                    ?.copy(estado = EstadoDePropuesta.valueOf(fila[AccionesPropuestas.estado]))
            }
            .reversed()
    }
    if (recientes.isEmpty()) return null
    return buildString {
        appendLine("LO QUE EL DUEÑO HIZO CON TUS PROPUESTAS EN ESTA CONVERSACIÓN:")
        recientes.forEach { p ->
            val que = when (p.estado) {
                EstadoDePropuesta.HECHA -> "la HIZO"
                EstadoDePropuesta.RECHAZADA -> "dijo NO: no la vuelvas a proponer igual salvo que te lo pida"
                EstadoDePropuesta.PENDIENTE -> "todavía no la contesta: no la propongas otra vez"
            }
            appendLine("- «${p.frase}»: $que.")
        }
    }.trim()
}

// ── Lo que el modelo ve ──────────────────────────────────────────────────────

private fun texto(descripcion: String) = JsonValue.from(mapOf("type" to "string", "description" to descripcion))
private fun numero(descripcion: String) = JsonValue.from(mapOf("type" to "number", "description" to descripcion))

private fun herramienta(nombre: String, descripcion: String, propiedades: Map<String, JsonValue>, obligatorias: List<String>): Tool =
    Tool.builder()
        .name(nombre)
        .description(descripcion)
        .inputSchema(
            Tool.InputSchema.builder()
                .properties(
                    Tool.InputSchema.Properties.builder()
                        .apply { propiedades.forEach { (k, v) -> putAdditionalProperty(k, v) } }
                        .build(),
                )
                .required(obligatorias)
                .build(),
        )
        .build()

private const val NO_ESCRIBE =
    " NO hace nada: le muestra al dueño una tarjeta con «Hacerlo» / «No» y él decide. Úsala solo cuando " +
        "el dueño te pida hacerlo, nunca por iniciativa propia."

/**
 * **Las herramientas que proponen**, para la lista que se le ofrece al modelo (ver `LAS_HERRAMIENTAS`).
 * Van en el prefijo cacheado igual que las de lectura: se pagan enteras una vez cada cinco minutos y
 * después a la décima parte.
 */
internal val HERRAMIENTAS_QUE_PROPONEN: List<Tool> = listOf(
    herramienta(
        PROPONER_MOVIMIENTO,
        "Propone anotar un gasto o un ingreso que YA pasó («hoy gasté 45 mil en almuerzo con la Nu»).$NO_ESCRIBE " +
            "Si falta el monto o la cuenta y no se deduce de lo que dijo, pregúntale antes de proponer.",
        mapOf(
            "monto" to numero("En pesos (o en la moneda de la cuenta), mayor que 0. «45 mil» es 45000."),
            "cuenta" to texto("Nombre de una cuenta suya, como aparece en «Cuentas» del bloque («Nu», «Bancolombia Ahorros»)."),
            "categoria" to texto("Una categoría que ya use, con su nombre («Comida»). Si no existe se creará y la tarjeta lo dirá."),
            "tipo" to texto("«gasto» o «ingreso». Por defecto, gasto."),
            "fecha" to texto("AAAA-MM-DD. Sin esto es hoy. No puede ser futura."),
            "nota" to texto("Qué fue, corto («Almuerzo con Caro»)."),
        ),
        listOf("monto", "cuenta", "categoria"),
    ),
    herramienta(
        PROPONER_CAMBIO_DE_CATEGORIA,
        "Propone cambiar la categoría de los movimientos que dicen un texto (todos los parecidos, del mismo " +
            "comercio o destinatario).$NO_ESCRIBE",
        mapOf(
            "texto" to texto("Lo que dicen los movimientos («Rappi», «Gimnasio»)."),
            "categoria" to texto("La categoría nueva («Comida»)."),
            "desde" to texto("AAAA-MM-DD, opcional. Sin esto mira los últimos 12 meses."),
            "hasta" to texto("AAAA-MM-DD, opcional."),
        ),
        listOf("texto", "categoria"),
    ),
    herramienta(
        PROPONER_RECURRENTE,
        "Propone crear un pago (o un ingreso) que se repite cada mes: el colegio, el arriendo, el sueldo.$NO_ESCRIBE",
        mapOf(
            "nombre" to texto("Cómo se llama («Colegio de mi hija»)."),
            "monto" to numero("Cuánto, mayor que 0."),
            "dia" to numero("Día del mes en que vence o llega, de 1 a 31."),
            "categoria" to texto("Su categoría («Educación»)."),
            "cuenta" to texto("Opcional: la cuenta de la que sale o a la que llega."),
            "tipo" to texto("«gasto» o «ingreso». Por defecto, gasto."),
        ),
        listOf("nombre", "monto", "dia", "categoria"),
    ),
    herramienta(
        PROPONER_PAGO_HECHO,
        "Propone marcar que un pago recurrente de este período ya se hizo, CON UN MOVIMIENTO QUE YA EXISTE " +
            "(«el gimnasio ya lo pagué, es el de 180 mil del 3»). Sin movimiento no se puede: si no está " +
            "anotado, propón primero el movimiento.$NO_ESCRIBE",
        mapOf(
            "pago" to texto("El nombre del pago recurrente («Gimnasio Cami»)."),
            "texto" to texto("Opcional: lo que dice el movimiento que lo pagó."),
            "monto" to numero("Opcional: el monto exacto de ese movimiento."),
        ),
        listOf("pago"),
    ),
    herramienta(
        RECORDAR,
        "Propone guardar algo DURABLE que el usuario te contó y que te va a servir en otras conversaciones: " +
            "quién es alguien («Caro es mi esposa»), algo que no es lo que parece («el bono de Glim de 55.500 " +
            "no es mensual»), una costumbre («pago el colegio de mi hija el 25»). NO guarda nada: él lo confirma. " +
            "Nada de cifras del mes ni cosas que ya están en sus datos. Una frase, en tercera persona o como él la dijo.",
        mapOf("texto" to texto("La frase a recordar, corta y completa.")),
        listOf("texto"),
    ),
)
