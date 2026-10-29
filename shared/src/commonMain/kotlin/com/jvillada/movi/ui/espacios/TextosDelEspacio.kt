package com.jvillada.movi.ui.espacios

import com.jvillada.movi.shared.model.enFormaDeTitulo
import com.jvillada.movi.shared.model.normalizarParaBuscar
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.RepartoDelEspacio
import com.jvillada.movi.shared.model.Reembolso
import com.jvillada.movi.shared.model.SaldoDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.idsDeMiembros
import com.jvillada.movi.shared.model.netoDe
import com.jvillada.movi.shared.model.DetalleDelEspacio
import com.jvillada.movi.shared.model.DestinoConocido
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.PropuestaDeSaldar
import com.jvillada.movi.shared.model.esperaLaTransferencia
import com.jvillada.movi.ui.analisis.fechaCorta
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.fecha.fechaDeEpoch
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.TimeZone
import io.ktor.http.encodeURLParameter

/**
 * # Lo que dice la pantalla de un espacio (R3)
 *
 * Funciones puras, para que las frases se prueben sin montar la pantalla. Español neutro, tuteo,
 * frases cortas.
 */

/**
 * El saldo dicho para el dueño: «Caro te debe» $390.000 «para quedar 50/50».
 *
 * @property titulo quién le debe a quién.
 * @property monto cuánto (0 si están a mano).
 * @property apoyo «para quedar 50/50» o «para quedar a mano»; `null` si no hay deuda.
 * @property reembolso el pago que lo salda, si el dueño paga o cobra; `null` si están a mano.
 */
data class FraseDelSaldo(
    val titulo: String,
    val monto: Long,
    val apoyo: String?,
    val reembolso: Reembolso?,
)

/** El nombre de [miembroId] en [espacio], o «Alguien». El dueño es «Tú». */
fun nombreEnElEspacio(espacio: Espacio, miembroId: String): String =
    if (miembroId == MIEMBRO_YO) "Tú" else espacio.miembros.firstOrNull { it.id == miembroId }?.nombre?.ifBlank { null } ?: "Alguien"

/** «50/50» con dos miembros en partes iguales; si no, «a mano». */
fun aQuedar(espacio: Espacio): String =
    if (espacio.reparto == RepartoDelEspacio.PARTES_IGUALES && idsDeMiembros(espacio).size == 2) "para quedar 50/50"
    else "para quedar a mano"

/** Ver [FraseDelSaldo]. */
fun fraseDelSaldo(espacio: Espacio, saldo: SaldoDelEspacio): FraseDelSaldo {
    val neto = saldo.netoDe(MIEMBRO_YO)
    val cobras = saldo.reembolsos.filter { it.a == MIEMBRO_YO }
    val pagas = saldo.reembolsos.filter { it.de == MIEMBRO_YO }
    return when {
        neto > 0L && cobras.size == 1 ->
            FraseDelSaldo("${nombreEnElEspacio(espacio, cobras.single().de)} te debe", neto, aQuedar(espacio), cobras.single())
        neto > 0L -> FraseDelSaldo("Te deben", neto, aQuedar(espacio), cobras.firstOrNull())
        neto < 0L && pagas.size == 1 ->
            FraseDelSaldo("Le debes a ${nombreEnElEspacio(espacio, pagas.single().a)}", -neto, aQuedar(espacio), pagas.single())
        neto < 0L -> FraseDelSaldo("Debes", -neto, aQuedar(espacio), pagas.firstOrNull())
        else -> FraseDelSaldo("Están a mano", 0L, null, null)
    }
}

/** «Compartido · Juan y Caro». */
fun quienesComparten(espacio: Espacio): String {
    val nombres = espacio.miembros.map { it.nombre.ifBlank { nombreEnElEspacio(espacio, it.id) } }
    val lista = when (nombres.size) {
        0 -> ""
        1 -> nombres.single()
        else -> nombres.dropLast(1).joinToString(", ") + " y " + nombres.last()
    }
    return "Compartido · $lista"
}

/** «Mitad» en un 50/50, «Te toca» en cualquier otro reparto. */
fun rotuloDeTuParte(espacio: Espacio): String =
    if (espacio.reparto == RepartoDelEspacio.PARTES_IGUALES && idsDeMiembros(espacio).size == 2) "Mitad" else "Te toca"

/**
 * El título de la fila de un gasto del espacio: su descripción o, sin ella, su categoría. Vuelta de
 * diseño 32: lo que el banco mandó en mayúsculas, en forma de título ([enFormaDeTitulo]).
 */
fun tituloDelGasto(gasto: GastoDelEspacio): String = enFormaDeTitulo(gasto.descripcion.ifBlank { gasto.categoria })

/**
 * El subtítulo de un gasto del espacio: «18 oct · Tú · Mercado» o «18 oct · Caro · Mercado». La fecha
 * la pidió la revisión de la vuelta de diseño 26 (sin ella, dos mercados del mismo monto no se
 * distinguían) y **va primero** desde la vuelta 31: al final se cortaba y era justo lo que los
 * distinguía.
 *
 * Vuelta de diseño 32: **cabe en un renglón a 360 dp**. Quién pagó es solo el nombre («Tú», «Caro»):
 * el avatar de la fila ya dice que es quien pagó, y «Pagaste tú» / «Pagó Caro» empujaban la categoría
 * a un segundo renglón, sola. La categoría no va si el título ya la dice («Mercado del sábado»), y va
 * al final: si aun así no cabe, la fila la corta con «…» y la fecha queda entera. Sin fecha (un gasto
 * viejo con 0), sin ese tramo.
 */
fun quienPagoElGasto(espacio: Espacio, gasto: GastoDelEspacio, zona: TimeZone = AppTimeZone.zone): String {
    val quien = nombreEnElEspacio(espacio, gasto.pagadoPor)
    val categoria = gasto.categoria.takeIf { it.isNotBlank() && !elTituloDiceLaCategoria(tituloDelGasto(gasto), it) }
    return listOfNotNull(fechaDelGasto(gasto, zona), quien, categoria).joinToString(" · ")
}

/**
 * ¿[titulo] ya dice [categoria]? Por palabras enteras, sin mayúsculas ni tildes: «Mercado del sábado»
 * dice «Mercado»; «Supermercado» no. Pura.
 */
internal fun elTituloDiceLaCategoria(titulo: String, categoria: String): Boolean {
    val enElTitulo = " ${normalizarParaBuscar(titulo)} "
    val buscada = normalizarParaBuscar(categoria)
    return buscada.isNotEmpty() && enElTitulo.contains(" $buscada ")
}

/** «18 oct»: el día de [gasto] en la zona de la app, o `null` si no tiene fecha. */
internal fun fechaDelGasto(gasto: GastoDelEspacio, zona: TimeZone = AppTimeZone.zone): String? =
    gasto.fecha.takeIf { it > 0L }?.let { fechaCorta(fechaDeEpoch(it, zona)) }

// ── «Pedirle a Caro» (vuelta de diseño 26) ───────────────────────────────────

/**
 * El botón principal de la tarjeta del saldo, según quién debe (decisión del dueño, «Pedirle a
 * Caro»):
 * - **El otro te debe** → [Pedir]: «Pedirle a Caro» arma un mensaje para mandarlo por WhatsApp, y
 *   «Marcar como saldado» queda de secundario, con confirmación.
 * - **Tú le debes** → [MarcarPagado]: no hay nada que pedir; el principal sigue siendo marcar que
 *   pagaste.
 * - **A mano** → [Nada].
 */
sealed interface PrincipalDelSaldo {
    data class Pedir(val aQuien: String) : PrincipalDelSaldo {
        val texto: String get() = "Pedirle a $aQuien"
    }
    data object MarcarPagado : PrincipalDelSaldo
    data object Nada : PrincipalDelSaldo
}

/** Ver [PrincipalDelSaldo]. */
fun principalDelSaldo(espacio: Espacio, saldo: SaldoDelEspacio): PrincipalDelSaldo {
    val reembolso = fraseDelSaldo(espacio, saldo).reembolso ?: return PrincipalDelSaldo.Nada
    return if (reembolso.a == MIEMBRO_YO) PrincipalDelSaldo.Pedir(nombreEnElEspacio(espacio, reembolso.de))
    else PrincipalDelSaldo.MarcarPagado
}

/** Cuántos gastos nombra el mensaje antes de «y N más». */
const val GASTOS_EN_EL_MENSAJE: Int = 3

/**
 * Los gastos que componen lo que [deudor] le debe al dueño: los que **pagó el dueño** y en los que a
 * [deudor] le tocaba algo, **desde el último saldado** (una liquidación deja el espacio a mano; lo de
 * antes ya se pagó), del más reciente al más viejo.
 */
internal fun gastosQueLoComponen(detalle: DetalleDelEspacio, deudor: String): List<GastoDelEspacio> {
    val desde = detalle.liquidaciones.filter { it.monto > 0L }.maxOfOrNull { it.fecha } ?: Long.MIN_VALUE
    return detalle.gastos
        .filter { it.pagadoPor == MIEMBRO_YO && (it.partes[deudor] ?: 0L) > 0L && it.fecha > desde }
        .sortedByDescending { it.fecha }
}

/**
 * **El mensaje de «Pedirle a Caro»**: corto, amable, en tuteo, para mandarlo por WhatsApp.
 *
 * ```
 * Hola, Caro. Me debes $76.050 de Casa con Caro:
 * • Mercado Éxito · 18 oct · tu parte $40.000
 * • Gas · 15 oct · tu parte $20.000
 * • Internet · 10 oct · tu parte $16.050
 * y 2 más.
 * ¿Me lo pasas cuando puedas? ¡Gracias!
 * ```
 *
 * El monto es el del pago que deja el espacio a mano (el mismo de «Marcar como saldado»); los gastos,
 * los [GASTOS_EN_EL_MENSAJE] más recientes de [gastosQueLoComponen] con la parte de quien lee. Las
 * cifras van **siempre** enteras, aunque el dueño tenga los saldos ocultos: el mensaje es para otra
 * persona. [enlace] es el de solo lectura del espacio, si existe (hoy los espacios no tienen uno: el
 * enlace de «Compartir» es del resumen del dueño, y su token no se puede volver a leer). `null` si
 * nadie le debe al dueño.
 */
fun mensajeParaPedir(
    detalle: DetalleDelEspacio,
    enlace: String? = null,
    zona: TimeZone = AppTimeZone.zone,
): String? {
    val espacio = detalle.espacio
    val reembolso = fraseDelSaldo(espacio, detalle.saldo).reembolso?.takeIf { it.a == MIEMBRO_YO } ?: return null
    val nombre = nombreEnElEspacio(espacio, reembolso.de)
    val gastos = gastosQueLoComponen(detalle, reembolso.de)
    return buildString {
        append("Hola, $nombre. Me debes ${formatCOPSinOcultar(reembolso.monto)} de ${espacio.nombre}")
        append(if (gastos.isEmpty()) "." else ":")
        gastos.take(GASTOS_EN_EL_MENSAJE).forEach { g ->
            val renglon = listOfNotNull(
                g.descripcion.ifBlank { g.categoria }.ifBlank { null },
                fechaDelGasto(g, zona),
                "tu parte ${formatCOPSinOcultar(g.partes[reembolso.de] ?: 0L)}",
            ).joinToString(" · ")
            append("\n• ").append(renglon)
        }
        val mas = gastos.size - GASTOS_EN_EL_MENSAJE
        if (mas > 0) append("\ny ").append(mas).append(" más.")
        enlace?.takeIf { it.isNotBlank() }?.let { append("\nEl detalle: ").append(it) }
        append("\n¿Me lo pasas cuando puedas? ¡Gracias!")
    }
}

/**
 * La confirmación antes de «Marcar como saldado» cuando el otro te debe: «¿Caro ya te pagó
 * $76.050?» y «Esto deja el espacio en cero.». Con más de un pago pendiente (tres o más miembros),
 * saldar uno no deja el espacio en cero: lo dice.
 */
fun confirmacionDeSaldado(espacio: Espacio, saldo: SaldoDelEspacio): Pair<String, String>? {
    val reembolso = fraseDelSaldo(espacio, saldo).reembolso?.takeIf { it.a == MIEMBRO_YO } ?: return null
    val nombre = nombreEnElEspacio(espacio, reembolso.de)
    val pregunta = "¿$nombre ya te pagó ${formatCOPSinOcultar(reembolso.monto)}?"
    val detalle = if (saldo.reembolsos.size == 1) "Esto deja el grupo en cero." else "Esto salda lo de $nombre."
    return pregunta to detalle
}

/** El enlace de WhatsApp que abre un chat con [mensaje] escrito (sin destinatario: se elige allá). */
fun enlaceDeWhatsApp(mensaje: String): String = "https://wa.me/?text=" + mensaje.encodeURLParameter()

/** La propuesta de vincular una transferencia: «¿Esta transferencia de Caro es para saldar la casa?». */
fun preguntaDeSaldar(espacio: Espacio, deOtro: String, entra: Boolean): String {
    val quien = nombreEnElEspacio(espacio, deOtro)
    return if (entra) "¿Esta transferencia de $quien salda ${espacio.nombre}?"
    else "¿Esta transferencia a $quien salda ${espacio.nombre}?"
}

/** ¿Se le ofrece «Repartir» a [evento]? Un gasto en pesos, que no es un traspaso ni salda nada. */
fun sePuedeRepartir(evento: FinancialEvent): Boolean =
    evento.type == TransactionType.EXPENSE &&
        evento.currency == "COP" &&
        evento.transferId == null &&
        evento.saldaEspacioId == null &&
        evento.countsAsCashFlow

/** El texto del error de una escritura en un espacio: lo que dijo el server, o que falta conexión. */
const val ESPACIOS_SIN_CONEXION: String = "Para esto necesitas conexión"

/** Lo más largo que guarda la nota de «Pagó otro» (la descripción del gasto en el server). */
const val MAX_NOTA_PAGO_OTRO: Int = 200

/** La descripción de un gasto que pagó otro: la nota escrita, o la categoría si no hay nota. */
fun descripcionDelPagoOtro(nota: String, categoria: String): String =
    nota.trim().ifBlank { categoria.trim() }.take(MAX_NOTA_PAGO_OTRO)

// ── Vuelta 66 · el saldado que espera su transferencia, y el historial ───────────────────────

/**
 * La pregunta de una propuesta de saldar, dentro o fuera del grupo. Si el saldado ya se marcó y espera
 * la plata ([PropuestaDeSaldar.liquidacionId]): «¿Es la transferencia de Caro que marcaste como
 * saldada?»; si salda una deuda abierta, la de siempre ([preguntaDeSaldar]).
 */
fun preguntaDeLaPropuesta(p: PropuestaDeSaldar, quien: String, nombreDelGrupo: String): String {
    val entra = p.a == MIEMBRO_YO
    val laTransferencia = if (entra) "la transferencia de $quien" else "tu transferencia a $quien"
    return if (p.liquidacionId != null) "¿Es $laTransferencia que marcaste como saldada?"
    else if (entra) "¿Esta transferencia de $quien salda $nombreDelGrupo?"
    else "¿Esta transferencia a $quien salda $nombreDelGrupo?"
}

/**
 * **La propuesta en la campana** (vuelta 67): el título corto —«Transferencia de Caro», «Tu
 * transferencia a Caro»— y la pregunta debajo, con el grupo: «¿Es la que marcaste como saldada? ·
 * Casa con Caro» o «¿Salda Casa con Caro?».
 */
fun laPropuestaEnLaCampana(p: PropuestaDeSaldar, quien: String): Pair<String, String> {
    val titulo = if (p.a == MIEMBRO_YO) "Transferencia de $quien" else "Tu transferencia a $quien"
    val grupo = p.nombreDelEspacio.trim()
    val pregunta = if (p.liquidacionId != null) {
        listOfNotNull("¿Es la que marcaste como saldada?", grupo.ifEmpty { null }).joinToString(" · ")
    } else {
        if (grupo.isEmpty()) "¿Salda tu grupo?" else "¿Salda $grupo?"
    }
    return titulo to pregunta
}

/** Lo que sigue a la pregunta: el concepto del movimiento y, fuera del grupo, de qué grupo es. */
fun cuerpoDeLaPropuesta(p: PropuestaDeSaldar, conElGrupo: Boolean): String =
    listOfNotNull(
        p.descripcion.trim().ifBlank { null },
        p.nombreDelEspacio.trim().takeIf { conElGrupo && it.isNotEmpty() },
    ).joinToString(" · ")

/** El título de la sección del historial de un grupo. */
const val PAGOS_ENTRE_USTEDES: String = "Pagos entre ustedes"

/** Lo que dice un saldado sin movimiento mientras espera la plata. */
const val ESPERANDO_LA_TRANSFERENCIA: String = "Esperando la transferencia"

/** Lo que dice un saldado viejo que nunca se vinculó (en efectivo, o por fuera de tus cuentas). */
const val SIN_TRANSFERENCIA: String = "Sin transferencia vinculada"

/** «Caro te pagó $168.050», «Le pagaste $90.000 a Caro», «Caro le pagó $50.000 a Ana». */
fun tituloDelPago(espacio: Espacio, l: LiquidacionDelEspacio): String {
    val monto = formatCOP(l.monto)
    return when (MIEMBRO_YO) {
        l.a -> "${nombreEnElEspacio(espacio, l.de)} te pagó $monto"
        l.de -> "Le pagaste $monto a ${nombreEnElEspacio(espacio, l.a)}"
        else -> "${nombreEnElEspacio(espacio, l.de)} le pagó $monto a ${nombreEnElEspacio(espacio, l.a)}"
    }
}

/**
 * «29 oct» y, en el renglón de abajo, «Transferencia de Caro», «Esperando la transferencia» o «Sin
 * transferencia vinculada» (ver [esperaLaTransferencia]). **Vuelta 67: dos renglones** —la fila los
 * pinta con `lineasDelSubtitulo = 2`—: en uno solo, a 390 y a 360 dp se cortaba en «Esperando la
 * transfere…», que es justo lo que había que leer.
 */
fun detalleDelPago(l: LiquidacionDelEspacio, ahora: Long, zona: TimeZone = AppTimeZone.zone): String {
    val como = when {
        l.eventoId != null -> l.movimiento?.trim()?.ifBlank { null }?.let { enFormaDeTitulo(it) } ?: "Transferencia vinculada"
        esperaLaTransferencia(l, ahora) -> ESPERANDO_LA_TRANSFERENCIA
        else -> SIN_TRANSFERENCIA
    }
    val fecha = l.fecha.takeIf { it > 0L }?.let { fechaCorta(fechaDeEpoch(it, zona)) }
    return listOfNotNull(fecha, como).joinToString("\n")
}

/** Los renglones de [detalleDelPago]. */
const val RENGLONES_DEL_DETALLE_DEL_PAGO: Int = 2

/**
 * **Vuelta 67 · lo que espera el grupo**, para la tarjeta del saldo: «esperando $168.050 de Caro» (o
 * «esperando tu transferencia de $90.000 a Caro») por cada saldado marcado sin movimiento que todavía
 * espera su plata ([esperaLaTransferencia]). `null` si no espera nada. Antes la tarjeta decía solo
 * «Están a mano» y la espera quedaba en un renglón cortado del historial.
 */
fun loQueEspera(detalle: DetalleDelEspacio, ahora: Long): String? {
    val espacio = detalle.espacio
    val esperando = detalle.liquidaciones.filter { esperaLaTransferencia(it, ahora) }.sortedByDescending { it.fecha }
    if (esperando.isEmpty()) return null
    return esperando.joinToString(" y ") { l ->
        if (l.a == MIEMBRO_YO) "esperando ${formatCOP(l.monto)} de ${nombreEnElEspacio(espacio, l.de)}"
        else "esperando tu transferencia de ${formatCOP(l.monto)} a ${nombreEnElEspacio(espacio, l.a)}"
    }
}

/**
 * **El saldo en la lista de grupos** (vuelta 67, revisión): «Caro te debe $76.050» o «Están a mano», y
 * lo que espera: «Están a mano · esperando $168.050 de Caro».
 */
fun saldoEnLaListaDeGrupos(detalle: DetalleDelEspacio, ahora: Long): String {
    val frase = fraseDelSaldo(detalle.espacio, detalle.saldo)
    val base = if (frase.monto > 0L) "${frase.titulo} ${formatCOP(frase.monto)}" else frase.titulo
    return loQueEspera(detalle, ahora)?.let { "$base · $it" } ?: base
}

/**
 * **El título de la tarjeta del saldo** (vuelta 67): la frase de siempre ([fraseDelSaldo]) y, si el
 * grupo espera una transferencia, «Están a mano · esperando $168.050 de Caro».
 */
fun tituloDeLaTarjetaDelSaldo(detalle: DetalleDelEspacio, ahora: Long): String {
    val frase = fraseDelSaldo(detalle.espacio, detalle.saldo)
    return loQueEspera(detalle, ahora)?.let { "${frase.titulo} · $it" } ?: frase.titulo
}

/** La marca de corte en la lista de gastos: «Saldaron $168.050 · 29 oct». */
fun corteDelSaldado(l: LiquidacionDelEspacio, zona: TimeZone = AppTimeZone.zone): String =
    listOfNotNull(
        "Saldaron ${formatCOP(l.monto)}",
        l.fecha.takeIf { it > 0L }?.let { fechaCorta(fechaDeEpoch(it, zona)) },
    ).joinToString(" · ")

/** Un renglón de la lista de gastos: un gasto, o el corte de un saldado. */
sealed interface RenglonDeLosGastos {
    val fecha: Long
    data class Gasto(val gasto: GastoDelEspacio) : RenglonDeLosGastos { override val fecha get() = gasto.fecha }
    data class Corte(val liquidacion: LiquidacionDelEspacio) : RenglonDeLosGastos { override val fecha get() = liquidacion.fecha }
}

/**
 * **Los gastos con un corte en cada saldado** (vuelta 66), del más reciente al más viejo: lo de arriba
 * de un corte es lo que se debe desde ese pago. Solo los cortes entre gastos que se muestran (ninguno
 * debajo del último).
 */
fun gastosConSusCortes(gastos: List<GastoDelEspacio>, liquidaciones: List<LiquidacionDelEspacio>): List<RenglonDeLosGastos> {
    if (gastos.isEmpty()) return emptyList()
    val masViejo = gastos.minOf { it.fecha }
    val renglones = gastos.map { RenglonDeLosGastos.Gasto(it) } +
        liquidaciones.filter { it.monto > 0L && it.fecha >= masViejo }.map { RenglonDeLosGastos.Corte(it) }
    // A la misma hora, el corte va arriba del gasto: saldar es lo que pasó después.
    return renglones.sortedWith(compareByDescending<RenglonDeLosGastos> { it.fecha }.thenBy { if (it is RenglonDeLosGastos.Corte) 0 else 1 })
}

/**
 * **Lo que pasa al borrar un grupo** (vuelta 66), dicho antes de hacerlo: la pregunta y el detalle.
 * A mano, solo que se van sus gastos y pagos y que tus movimientos quedan. Con un saldo pendiente, lo
 * que se pierde: «Caro te debe $76.050: al borrarlo, Movi deja de llevar esa cuenta», y que tus gastos
 * repartidos vuelven a contar enteros, tu parte de lo que pagó el otro deja de contar y las
 * transferencias vinculadas vuelven a ser ingreso o gasto.
 */
fun confirmacionDeBorrarElGrupo(detalle: DetalleDelEspacio): Pair<String, String> {
    val espacio = detalle.espacio
    val frase = fraseDelSaldo(espacio, detalle.saldo)
    val conGastos = detalle.gastos.isNotEmpty() || detalle.liquidaciones.isNotEmpty()
    val pregunta = "¿Borrar «${espacio.nombre}»?"
    val partes = buildList {
        if (frase.monto > 0L) add("${frase.titulo} ${formatCOP(frase.monto)}: al borrarlo, Movi deja de llevar esa cuenta.")
        if (conGastos) {
            add("Se borran sus gastos y pagos. Tus movimientos quedan en tus cuentas y vuelven a contar enteros; lo que pagó otra persona deja de contar.")
            if (detalle.liquidaciones.any { it.eventoId != null }) add("Las transferencias que vinculaste vuelven a contar como ingreso o gasto.")
        } else {
            add("No tiene gastos ni pagos.")
        }
    }
    return pregunta to partes.joinToString(" ")
}

/** El texto del botón de borrar: más fuerte si queda un saldo pendiente. */
fun botonDeBorrarElGrupo(detalle: DetalleDelEspacio): String =
    if (fraseDelSaldo(detalle.espacio, detalle.saldo).monto > 0L) "Borrar de todas formas" else "Borrar grupo"

/**
 * **¿Ya hay un grupo así?** (vuelta 66) El mismo nombre (sin mayúsculas ni tildes) con las mismas
 * personas: «Ya tienes «Casa con Caro» con Caro.». `null` si no.
 */
fun avisoDeGrupoRepetido(nombre: String, personas: List<DestinoConocido>, existentes: List<Espacio>): String? =
    grupoIgual(nombre, personas.map { it.id }.toSet(), existentes, excepto = null)
        ?.let { igual -> "Ya tienes «${igual.nombre}» con ${personas.joinToString(", ") { it.nombre }}." }

/**
 * **Vuelta 67 · renombrar con la misma comprobación que al crear**: si [nombre] es el de otro grupo
 * con las mismas personas que [espacio], «Ya tienes «Casa con Caro» con Caro.». `null` si no (o si
 * el único igual es el mismo grupo).
 */
fun avisoDeNombreRepetido(nombre: String, espacio: Espacio, existentes: List<Espacio>): String? {
    val otros = espacio.miembros.filter { it.id != MIEMBRO_YO }
    return grupoIgual(nombre, otros.map { it.id }.toSet(), existentes, excepto = espacio.id)
        ?.let { igual -> "Ya tienes «${igual.nombre}» con ${otros.joinToString(", ") { nombreEnElEspacio(espacio, it.id) }}." }
}

/** El grupo de [existentes] con el mismo nombre (sin mayúsculas ni tildes) y las mismas personas. */
private fun grupoIgual(nombre: String, ids: Set<String>, existentes: List<Espacio>, excepto: String?): Espacio? {
    val buscado = normalizarParaBuscar(nombre.trim())
    if (buscado.isEmpty() || ids.isEmpty()) return null
    return existentes.firstOrNull { e ->
        e.id != excepto && normalizarParaBuscar(e.nombre.trim()) == buscado &&
            e.miembros.filter { it.id != MIEMBRO_YO }.map { it.id }.toSet() == ids
    }
}

/**
 * **Lo que dice el «No» de una propuesta de saldar** (vuelta 67), antes de hacerlo: el descarte es
 * por la transferencia, así que tampoco vuelve para un saldo futuro.
 */
const val PREGUNTA_DEL_NO_DE_SALDAR: String = "¿Esta transferencia no salda el grupo?"
const val DETALLE_DEL_NO_DE_SALDAR: String =
    "Movi no te la vuelve a proponer: tampoco para un saldo futuro de este grupo. Sigue contando como ingreso o gasto."
const val CONFIRMAR_EL_NO_DE_SALDAR: String = "No la vincules"

/** El nombre que Movi sugiere para un grupo nuevo: «Casa con Caro», «Casa con Caro y Ana». */
fun nombreSugeridoDelGrupo(personas: List<DestinoConocido>): String {
    val nombres = personas.map { it.nombre.trim() }.filter { it.isNotEmpty() }
    val lista = when (nombres.size) {
        0 -> return ""
        1 -> nombres.single()
        else -> nombres.dropLast(1).joinToString(", ") + " y " + nombres.last()
    }
    return "Casa con $lista".take(com.jvillada.movi.shared.model.MAX_NOMBRE_DE_ESPACIO)
}
