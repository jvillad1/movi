package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.AvisoPorRevisar
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.nombreDelOrigenDeCaptura
import com.jvillada.movi.shared.model.OrigenMudo
import com.jvillada.movi.shared.model.queNoLlega
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.formatCOPSinOcultar
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.shared.time.AppTimeZone
import com.jvillada.movi.shared.time.epochMillisToAppDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * # Movi avisa: lo que dicen las notificaciones del teléfono, y cuándo
 *
 * Ola 1 (revisión «el mejor asistente», 29-sep): las cifras de Movi son verdad, pero **le falta
 * iniciativa** — espera a que el dueño abra la app. Hasta acá el APK no mandaba ni una
 * notificación. Esta ola agrega dos:
 *
 * - **«Llegó un movimiento»**, cuando la captura (un SMS o una notificación del banco) deja algo en
 *   «Por revisar». Agrupada: si llegan varios seguidos, una sola notificación.
 * - **«Vence mañana / vence hoy»**, una vez al día, con los pagos del período que vencen y no están
 *   pagados.
 *
 * Acá vive **lo que deciden y lo que dicen**, puro y probado. Quién las pinta (canales,
 * `NotificationCompat`, WorkManager) vive en `:androidApp`; el permiso y los interruptores, en el
 * `androidMain` de `:shared`. Web e iOS no hacen nada con esto en esta ola (la web ya tiene su push
 * por VAPID aparte), pero compilan contra lo mismo.
 */

/** Lo que dice una notificación: el título, el texto de una línea y, si se despliega, sus líneas. */
data class TextoDeAviso(val titulo: String, val texto: String, val lineas: List<String> = emptyList())

// ── Llegó un movimiento ──────────────────────────────────────────────────────

/**
 * **De dónde vino, como lo diría una persona**, o `null` si no hay nada legible que decir.
 *
 * «Notificación · Nu» → «Nu», «Correo · Bancolombia» → «Bancolombia», «85540» → «Bancolombia». La
 * regla vive en `:core` ([nombreDelOrigenDeCaptura]) desde la Ola 2: el server la usa para nombrar
 * el banco mudo, y el aviso del teléfono tiene que decir el mismo nombre.
 */
fun origenParaElAviso(origen: String): String? = nombreDelOrigenDeCaptura(origen)

/**
 * Un monto como lo dice la app: pesos enteros con puntos («$180.000»), y los dólares con su signo
 * y sus centavos solo si los hay («US$20», «US$19,99»). Nunca «$20» para veinte dólares.
 */
fun montoParaElAviso(monto: Double, moneda: String): String = when (moneda) {
    "USD" -> {
        val centavos = (abs(monto) * 100).roundToLong()
        val enteros = formatCOPSinOcultar(centavos / 100).removePrefix("$")
        val resto = centavos % 100
        "US$" + enteros + if (resto == 0L) "" else "," + resto.toString().padStart(2, '0')
    }
    "COP" -> formatCOPSinOcultar(abs(monto).roundToLong())
    else -> "$moneda ${formatCOPSinOcultar(abs(monto).roundToLong()).removePrefix("$")}"
}

private fun lineaDe(aviso: AvisoPorRevisar, ocultos: Boolean = false): String =
    "${montoVisibleEnElAviso(aviso.monto, aviso.moneda, ocultos)} · ${aviso.descripcion}"

/** El cierre que dice qué pasa al tocar: la bandeja de «Por revisar». */
const val TOCA_PARA_REVISAR: String = "toca para revisar"

/**
 * **El texto de la notificación de «Por revisar»**, para uno o para varios.
 *
 * - Uno: «Movi anotó $180.000» y «Transferencia a Caro · Bancolombia — toca para revisar». Un
 *   ingreso dice «Te llegaron $X»: «Movi anotó» sobre plata que entró se leería como un gasto.
 * - Varios: «3 movimientos por revisar», los dos primeros en el texto («y 1 más») y todos, hasta
 *   cinco, en las líneas que se ven al desplegar.
 *
 * `null` con la lista vacía: no hay notificación sin nada que decir. Con [ocultos] («Ocultar los
 * saldos», Ola 5) ningún monto sale en claro.
 */
fun textoDeMovimientos(avisos: List<AvisoPorRevisar>, ocultos: Boolean = false): TextoDeAviso? {
    if (avisos.isEmpty()) return null
    if (avisos.size == 1) {
        val aviso = avisos.single()
        val monto = montoVisibleEnElAviso(aviso.monto, aviso.moneda, ocultos)
        val titulo = if (aviso.tipo == TransactionType.INCOME) "Te llegaron $monto" else "Movi anotó $monto"
        val quien = listOfNotNull(aviso.descripcion.trim().ifBlank { null }, origenParaElAviso(aviso.origen))
            .joinToString(" · ")
        val texto = if (quien.isBlank()) "Tienes un movimiento por revisar — $TOCA_PARA_REVISAR"
        else "$quien — $TOCA_PARA_REVISAR"
        return TextoDeAviso(titulo = titulo, texto = texto)
    }
    val primeros = avisos.take(2).joinToString(" · ") { lineaDe(it, ocultos) }
    val mas = if (avisos.size > 2) " y ${avisos.size - 2} más" else ""
    return TextoDeAviso(
        titulo = "${avisos.size} movimientos por revisar",
        texto = "$primeros$mas — $TOCA_PARA_REVISAR",
        lineas = avisos.take(5).map { lineaDe(it, ocultos) } + if (avisos.size > 5) listOf("y ${avisos.size - 5} más") else emptyList(),
    )
}

/** Cuántos movimientos recuerda, como mucho, una notificación agrupada. */
const val TOPE_DE_MOVIMIENTOS_AGRUPADOS: Int = 20

/**
 * **Lo que dice la notificación después de que llegan [nuevos].**
 *
 * Si la notificación anterior [sigueVisible] (no se tocó ni se descartó), los nuevos se suman a los
 * que ya decía: «3 movimientos por revisar», no tres notificaciones. Si no, empieza de cero —lo
 * anterior el dueño ya lo vio, o lo descartó—. Sin repetidos por id: el Worker reintenta, y un
 * reintento no es un movimiento más.
 */
fun acumularMovimientos(
    previos: List<AvisoPorRevisar>,
    nuevos: List<AvisoPorRevisar>,
    sigueVisible: Boolean,
): List<AvisoPorRevisar> =
    ((if (sigueVisible) previos else emptyList()) + nuevos)
        .distinctBy { it.id }
        .takeLast(TOPE_DE_MOVIMIENTOS_AGRUPADOS)

// ── Vence mañana / vence hoy ─────────────────────────────────────────────────

/**
 * **Qué pagos del período avisar hoy**: los que vencen hoy o mañana y **no están pagados**.
 *
 * No recalcula nada: [checklist] es `checklistDelPeriodo`, la MISMA lista que pinta «Pagos del
 * período» en Plan, con su `pagado` (sellado, emparejado por Movi o probado por un movimiento) y su
 * `diasParaVencer` contado contra el «hoy» del server. Si Plan lo muestra pagado, no se avisa.
 *
 * Afuera también lo que no se paga (un sueldo llega, no vence) y lo ya vencido: esto avisa ANTES;
 * lo vencido lo dice Plan con su rojo. Primero lo de hoy; dentro de cada día, por nombre.
 */
fun vencimientosParaAvisar(checklist: List<PagoDelPeriodo>): List<PagoDelPeriodo> =
    checklist
        .filter { !it.pagado && !it.esIngreso && it.diasParaVencer in 0..1 }
        .sortedWith(compareBy<PagoDelPeriodo> { it.diasParaVencer }.thenBy { it.nombre.lowercase() })

/**
 * El monto de un pago para el aviso, o `null` si no hay un monto honesto que decir: **el de una
 * tarjeta es su SALDO** (`montoEsSaldo`), la deuda entera, no lo que hay que pagar este mes. Decir
 * «Mañana vence Pago tarjeta AMEX $27.501.150» sería la versión más ruidosa del número que ya se
 * corrigió en el correo y en la push del server.
 */
fun montoDelVencimiento(pago: PagoDelPeriodo): String? =
    if (pago.montoEsSaldo) null else montoParaElAviso(pago.monto.toDouble(), pago.moneda)

private fun cuandoVence(pago: PagoDelPeriodo): String = if (pago.diasParaVencer <= 0) "Hoy" else "Mañana"

/**
 * **El texto del aviso de vencimientos**: una sola notificación con el resumen.
 *
 * Título: «Mañana vence Coomeva Familiar $138.600 y 1 más» (el primero es el más urgente). Las
 * líneas, uno por renglón: «Hoy · Celular · $53.000», y una tarjeta sin el monto —«Mañana · Pago
 * tarjeta AMEX · revisa cuánto pagar»—. `null` sin nada que avisar.
 */
fun textoDeVencimientos(pagos: List<PagoDelPeriodo>): TextoDeAviso? {
    if (pagos.isEmpty()) return null
    val primero = pagos.first()
    val monto = montoDelVencimiento(primero)?.let { " $it" }.orEmpty()
    val mas = if (pagos.size > 1) " y ${pagos.size - 1} más" else ""
    val titulo = "${cuandoVence(primero)} vence ${primero.nombre}$monto$mas"
    val lineas = pagos.map { pago ->
        val cuanto = montoDelVencimiento(pago) ?: "revisa cuánto pagar"
        "${cuandoVence(pago)} · ${pago.nombre} · $cuanto"
    }
    val texto = if (pagos.size == 1) {
        if (primero.montoEsSaldo) "Revisa cuánto pagar — toca para ver tus pagos del período"
        else "Toca para ver tus pagos del período"
    } else {
        lineas.joinToString(" · ")
    }
    return TextoDeAviso(titulo = titulo, texto = texto, lineas = if (pagos.size > 1) lineas else emptyList())
}

/**
 * La huella de un aviso de vencimientos: el día y qué se avisó. Si el Worker corre dos veces el
 * mismo día con lo mismo pendiente, la segunda no suena de nuevo; si mañana cambia algo, sí.
 */
fun huellaDeVencimientos(ahora: Long, pagos: List<PagoDelPeriodo>): String =
    epochMillisToAppDate(ahora).toString() + "|" + pagos.joinToString(",") { "${it.ruleId}:${it.diasParaVencer}" }

/** La hora del aviso de vencimientos, en Bogotá: antes de salir de la casa, con tiempo de pagar. */
const val HORA_DEL_AVISO_DE_VENCIMIENTOS: Int = 8

/**
 * **Cuánto falta, en milisegundos, para la próxima [hora] en punto en Bogotá** (`AppTimeZone`, la
 * misma zona con la que el server decide qué «vence hoy»). Si ya pasó hoy, la de mañana. Es la
 * demora inicial del Worker diario: WorkManager no sabe de horas del día, solo de intervalos.
 */
fun milisHastaLaProxima(hora: Int, ahora: Long): Long {
    val zona = AppTimeZone.zone
    val hoy = Instant.fromEpochMilliseconds(ahora).toLocalDateTime(zona).date
    val deHoy = LocalDateTime(hoy, LocalTime(hora, 0)).toInstant(zona).toEpochMilliseconds()
    val objetivo = if (deHoy > ahora) deHoy
    else LocalDateTime(hoy.plus(1, DateTimeUnit.DAY), LocalTime(hora, 0)).toInstant(zona).toEpochMilliseconds()
    return objetivo - ahora
}

// ── Banco mudo (Ola 2) ───────────────────────────────────────────────────────

/**
 * **El aviso de «banco mudo»**: un origen de captura que llegaba con regularidad y se calló (la
 * regla es `origenesMudos`, en :core, y la corre el server). Uno: «Hace 4 días no llega nada de
 * Bancolombia»; varios: «2 bancos dejaron de avisar», con uno por renglón. `null` sin nada que decir.
 */
fun textoDeBancosMudos(origenes: List<OrigenMudo>): TextoDeAviso? {
    if (origenes.isEmpty()) return null
    if (origenes.size == 1) {
        val mudo = origenes.single()
        return TextoDeAviso(
            titulo = "Hace ${mudo.diasSinCaptura} días no llega nada de ${mudo.nombre}",
            texto = "No llegan ${queNoLlega(mudo)}: revisa la captura — toca para verla",
        )
    }
    return TextoDeAviso(
        titulo = "${origenes.size} bancos dejaron de avisar",
        texto = "Revisa la captura — toca para verla",
        lineas = origenes.map { "Hace ${it.diasSinCaptura} días: ${queNoLlega(it)}" },
    )
}

/**
 * **La huella de un silencio**: qué orígenes y desde qué última captura. El mismo silencio no suena
 * dos veces —el Worker corre todos los días—; uno nuevo (otro banco que se calla, o el mismo que
 * volvió y se volvió a callar) sí.
 */
fun huellaDeBancosMudos(origenes: List<OrigenMudo>): String =
    origenes.sortedBy { it.clave }.joinToString(",") { "${it.clave}@${it.ultima}" }

/**
 * **El server cerró la sesión del teléfono** (un 401 de Movi, ver `laSesionTras`). Lo que el dueño
 * pierde no es la app —esa la ve al abrirla—, es la captura: los SMS y las notificaciones del banco
 * dejan de subir. El 26-oct-2026 eso pasó en silencio; ahora suena una vez, y lo que llega mientras
 * tanto se guarda en el teléfono y sube al entrar.
 */
val TEXTO_DE_LA_SESION_CERRADA = TextoDeAviso(
    titulo = "Movi dejó de recibir los mensajes del banco",
    texto = "Tu sesión se cerró. Entra de nuevo y lo que llegó mientras tanto sube solo.",
)

/** La hora del aviso de banco mudo, en Bogotá: después del de vencimientos, para no sonar juntos. */
const val HORA_DEL_AVISO_DE_BANCO_MUDO: Int = 9

// ── A dónde lleva tocar ──────────────────────────────────────────────────────

/** La clave del `Intent` que dice a qué pantalla abrir al tocar un aviso. */
const val EXTRA_ABRIR: String = "movi.abrir"
const val ABRIR_POR_REVISAR: String = "por_revisar"
const val ABRIR_PLAN: String = "plan"
const val ABRIR_CAPTURA: String = "captura"

/**
 * Ola 5 · los atajos del ícono de la app en Android (`res/xml/shortcuts.xml`, mantener presionado el
 * ícono): entran por el MISMO camino que tocar un aviso —el extra [EXTRA_ABRIR] y
 * [DestinoDesdeAfuera]—, así que también esperan la puerta de la huella. Los valores están escritos
 * tal cual en ese XML: cambiarlos acá sin cambiarlos allá deja el atajo sin destino.
 */
const val ABRIR_AGREGAR_GASTO: String = "agregar:gasto"
const val ABRIR_AGREGAR_TRASPASO: String = "agregar:traspaso"

/**
 * La pantalla que abre tocar un aviso: «Por revisar» para los movimientos, Plan (en «Pagos del
 * período», su segmento por defecto) para los vencimientos. `null` para cualquier otra cosa: un
 * `Intent` ajeno no navega a ningún lado.
 */
fun destinoDeAviso(valor: String?): Screen? = when (valor) {
    ABRIR_POR_REVISAR -> Screen.PorRevisar
    ABRIR_PLAN -> Screen.Plan()
    ABRIR_CAPTURA -> Screen.CapturaDelBanco
    ABRIR_AGREGAR_GASTO -> com.jvillada.movi.ui.quickadd.pantallaDelAtajo(com.jvillada.movi.ui.quickadd.AtajoDeAgregar.GASTO)
    ABRIR_AGREGAR_TRASPASO -> com.jvillada.movi.ui.quickadd.pantallaDelAtajo(com.jvillada.movi.ui.quickadd.AtajoDeAgregar.TRASPASO)
    // «Cerró tu período»: el detalle del período que cerró (ver `abrirElPeriodo`). Ola 5: «Revisar»
    // y «Otra categoría» de la notificación de un aviso abren Reconciliar de ese aviso.
    // Vuelta 68: el resumen de la mañana abre el Inicio; el semanal, el grupo (o la lista de grupos).
    else -> valor?.let { detalleDelAviso(it) ?: reconciliarDelAviso(it) ?: inicioDelAviso(it) ?: grupoDelAviso(it) }
}

/** Reconciliar de un `"aviso:<id>"` o `"aviso-categoria:<id>"` (Ola 5), o `null` si no es eso. */
internal fun reconciliarDelAviso(valor: String): Screen? = when {
    valor.startsWith(PREFIJO_ABRIR_AVISO_CATEGORIA) ->
        valor.removePrefix(PREFIJO_ABRIR_AVISO_CATEGORIA).takeIf { it.isNotBlank() }
            ?.let { Screen.SMSReconcile(it, abrirCategoria = true) }
    valor.startsWith(PREFIJO_ABRIR_AVISO) ->
        valor.removePrefix(PREFIJO_ABRIR_AVISO).takeIf { it.isNotBlank() }?.let { Screen.SMSReconcile(it) }
    else -> null
}
