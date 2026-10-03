package com.jvillada.movi.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.CajaProyectada
import com.jvillada.movi.shared.model.GastoDelDiaADia
import com.jvillada.movi.shared.model.cajaProyectada
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.patrimonioDe
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.checklistDelPeriodoDe
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.ui.recurrentes.ActionChip
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * # «Tu plata, día a día»: la caja proyectada en Plan (Ola 4)
 *
 * Debajo de «Cuánto puedes gastar», una línea con lo que vas a tener cada día hasta el cierre del
 * período, el día más justo marcado y, si alguna fecha queda en negativo, cuál y por qué pago.
 *
 * Nada de esto calcula plata nuevo: la cuenta es [cajaProyectada] (en `:core`), sobre **la lista
 * «Pagos del período» que Plan tiene en pantalla** ([checklistDelPeriodoDe]) y sobre «Tu plata» de
 * [patrimonioDe]. Lo único que se pide aparte es el supuesto del gasto del día a día, que necesita
 * la historia del server — y se dice como supuesto, con de qué períodos sale.
 */

/** El título de la sección. */
const val TITULO_CAJA_PROYECTADA = "Tu plata, día a día"

const val TAG_CAJA_PROYECTADA = "caja-proyectada"
const val TAG_GRAFICA_DE_LA_CAJA = "grafica-de-la-caja"

/**
 * Lo que la sección necesita de [DashboardData] para proyectar, o `null` si todavía falta algo: el
 * período, la lista del período (vencimientos y ocurrencias) y Tu plata.
 */
internal fun cajaDelPeriodoDe(
    data: DashboardData,
    gasto: GastoDelDiaADia,
    hoy: LocalDate,
): CajaProyectada? {
    val periodo = data.periodoActual ?: return null
    if (data.upcoming == null || data.ocurrencias == null) return null
    val tuPlata = (data.accounts?.let { patrimonioDe(it) } ?: data.patrimonio)?.tuPlata ?: return null
    val ultimoDia = inicioDelPeriodo(periodoSiguiente(periodo), data.ajustesDePeriodo).minus(1, DateTimeUnit.DAY)
    return cajaProyectada(
        tuPlataHoy = tuPlata,
        pagos = checklistDelPeriodoDe(data),
        gastoDiario = gasto.porDia,
        hoy = hoy.toString(),
        ultimoDia = ultimoDia.toString(),
    )
}

// ── Lo que dice ─────────────────────────────────────────────────────────────
//
// Puro y aparte para que las pruebas lean las frases exactas. Todo en tuteo.

/**
 * **El día más justo**, con el porqué cuando hay uno que se pueda nombrar sin inventar: el pago de
 * ese mismo día, el ingreso del día siguiente (el día más bajo suele ser la víspera del sueldo) o el
 * cierre del período.
 */
internal fun fraseDelDiaMasBajo(caja: CajaProyectada, hoy: LocalDate): String {
    val bajo = caja.diaMasBajo
    val cifra = formatMoneyCompact(bajo.saldo)
    if (bajo.fecha == hoy.toString()) return "Hoy es tu día más justo: tienes $cifra"
    val cuando = "El ${fechaEnPalabras(bajo.fecha, hoy)}"
    val porque = bajo.pagoMasGrande?.let { "el día de ${it.nombre}" }
        ?: siguienteIngreso(caja, bajo.fecha)?.let { "un día antes de que llegue $it" }
        ?: if (bajo.fecha == caja.alCierre.fecha) "el último día del período" else null
    return if (porque != null) "$cuando llegas a $cifra, $porque" else "$cuando llegas a $cifra, tu día más justo"
}

private fun siguienteIngreso(caja: CajaProyectada, fecha: String): String? {
    val i = caja.dias.indexOfFirst { it.fecha == fecha }
    return caja.dias.getOrNull(i + 1)?.movimientos?.filter { it.monto > 0 }?.maxByOrNull { it.monto }?.nombre
}

/** **El primer día en rojo**, con la fecha y lo que lo causa; `null` si ninguno queda en negativo. */
internal fun fraseDelRojo(caja: CajaProyectada, hoy: LocalDate): String? {
    val rojo = caja.primerDiaEnRojo ?: return null
    val cuando = if (rojo.fecha == hoy.toString()) "Hoy" else "El ${fechaEnPalabras(rojo.fecha, hoy)}"
    val porque = rojo.pagoMasGrande?.let { "por ${it.nombre} (${formatCOP(-it.monto)})" } ?: "por tu gasto del día a día"
    return "$cuando quedarías en ${formatMoneyCompact(rojo.saldo)} $porque"
}

/** **El supuesto**, dicho como supuesto, con de dónde sale. */
internal fun fraseDelSupuesto(gasto: GastoDelDiaADia): String {
    val porDia = gasto.porDia
        ?: return "Todavía no hay un período cerrado para estimar tu gasto del día a día: la línea solo resta tus pagos del período."
    val deDonde = when (gasto.periodos.size) {
        1 -> "tu promedio del período de ${nombreDelPeriodo(gasto.periodos.single().periodo)}"
        else -> "tu promedio de los últimos ${gasto.periodos.size} períodos"
    }
    return "Suponiendo que gastas como siempre, unos ${formatMoneyCompact(porDia)} por día ($deDonde)."
}

/** «2026-09» → «septiembre», o el prefijo tal cual si no se entiende. */
private fun nombreDelPeriodo(prefijo: String): String =
    periodoDelPrefijo(prefijo)?.let { MESES_DEL_ANIO[it.month - 1] } ?: prefijo

/** De dónde sale la línea, en dos renglones cortos. */
internal fun deDondeSaleLaCaja(caja: CajaProyectada): List<String> = listOf(
    "Parte de Tu plata hoy (${formatMoneyCompact(caja.tuPlataHoy)}). Resta lo que falta de «Pagos del período» el día que " +
        "vence (una tarjeta, su pago mínimo) y suma lo que falta por cobrar.",
    "Tu gasto del día a día se resta desde mañana, como si saliera de tu plata ese mismo día aunque lo pagues con tarjeta.",
)

/** Lo que no entró a la cuenta, en una frase, o `null` si entró todo. */
internal fun fraseDeLoQueFalta(caja: CajaProyectada): String? =
    caja.sinContar.takeIf { it.isNotEmpty() }?.let { "No entra a la cuenta: ${it.joinToString(" · ")}." }

// ── La sección ──────────────────────────────────────────────────────────────

/**
 * @param recarga sube con cada acción del tablero de pagos (igual que la tarjeta de arriba).
 */
@Composable
internal fun SeccionCajaProyectada(
    data: DashboardData,
    cargando: Boolean,
    recarga: Int,
    onNavigate: (Screen) -> Unit,
) {
    var reintento by remember { mutableStateOf(0) }
    val gastoLeido = rememberLectura(ClaveDeLectura.GastoDelDiaADia, reintento = recarga + reintento) {
        Repositories.wallets.getGastoDelDiaADia()
    }
    var verDeDondeSale by rememberSaveable { mutableStateOf(false) }
    val hoy = remember { epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()) }
    val gasto = gastoLeido.valor
    val caja = gasto?.let { cajaDelPeriodoDe(data, it, hoy) }

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio).testTag(TAG_CAJA_PROYECTADA)) {
        Spacer(Modifier.height(Movi.espacios.seccion))
        MinSectionHeader(
            title = TITULO_CAJA_PROYECTADA,
            action = if (verDeDondeSale) "Ocultar" else "¿De dónde sale?",
            onAction = { if (caja != null) verDeDondeSale = !verDeDondeSale },
        )
        when {
            caja != null -> MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(Movi.espacios.amplio),
            ) {
                CuerpoDeLaCaja(caja, gasto, hoy, verDeDondeSale, onNavigate)
            }
            gastoLeido.valor == null && gastoLeido.terminada && !gastoLeido.actualizando ->
                NoSePudoLeer("No pudimos proyectar tu plata", onReintentar = { reintento++ })
            gastoLeido.actualizando || cargando -> MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(Movi.espacios.amplio),
            ) {
                LineaEsqueleto(fraccionDelAncho = 0.8f, estilo = Movi.textos.cuerpo)
                Spacer(Modifier.height(Movi.espacios.medio))
                Spacer(Modifier.height(ALTO_DE_LA_GRAFICA))
            }
            // Sin período, sin lista o sin cuentas no hay nada honesto que proyectar: se calla.
            else -> Unit
        }
    }
}

private val ALTO_DE_LA_GRAFICA = 120.dp

@Composable
private fun CuerpoDeLaCaja(
    caja: CajaProyectada,
    gasto: GastoDelDiaADia,
    hoy: LocalDate,
    verDeDondeSale: Boolean,
    onNavigate: (Screen) -> Unit,
) {
    val rojo = fraseDelRojo(caja, hoy)
    if (rojo != null) {
        Text(rojo, style = Movi.textos.cuerpo, fontWeight = FontWeight.Medium, color = Movi.colores.sale)
        Spacer(Modifier.height(Movi.espacios.minimo))
    }
    Text(fraseDelDiaMasBajo(caja, hoy), style = Movi.textos.cuerpo, color = Movi.colores.texto)
    Spacer(Modifier.height(Movi.espacios.medio))
    GraficaDeLaCaja(caja)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fechaCorta(caja.dias.first().fecha), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        Text(
            "Al cierre: ${formatMoneyCompact(caja.alCierre.saldo)}",
            style = Movi.textos.apoyo,
            color = if (caja.alCierre.saldo < 0) Movi.colores.sale else Movi.colores.textoMedio,
        )
        Text(fechaCorta(caja.dias.last().fecha), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
    }
    Spacer(Modifier.height(Movi.espacios.medio))
    Text(fraseDelSupuesto(gasto), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
    fraseDeLoQueFalta(caja)?.let { falta ->
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(falta, style = Movi.textos.apoyo, color = Movi.colores.aviso)
        Spacer(Modifier.height(Movi.espacios.corto))
        ActionChip(label = "Cargar en Créditos", primary = false) { onNavigate(Screen.Credits) }
    }
    if (verDeDondeSale) {
        Spacer(Modifier.height(Movi.espacios.corto))
        deDondeSaleLaCaja(caja).forEach {
            Text(it, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            Spacer(Modifier.height(Movi.espacios.minimo))
        }
    }
}

/** «17 oct». */
private fun fechaCorta(iso: String): String {
    val fecha = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return "${fecha.dayOfMonth} ${MESES_DEL_ANIO[fecha.monthNumber - 1].take(3)}"
}

/**
 * La línea: un punto por día, el cero punteado si la línea lo cruza, el tramo bajo cero en rojo y
 * el día más justo con un punto.
 */
@Composable
private fun GraficaDeLaCaja(caja: CajaProyectada) {
    val linea = Movi.colores.textoMedio
    val rojo = Movi.colores.sale
    val cero = Movi.colores.hilo
    val punto = if (caja.diaMasBajo.saldo < 0) rojo else Movi.colores.aviso
    val saldos = caja.dias.map { it.saldo }
    val descripcion = "Gráfica de tu plata día a día: de ${formatMoneyCompact(saldos.first())} hoy a " +
        "${formatMoneyCompact(saldos.last())} al cierre, el punto más bajo ${formatMoneyCompact(caja.diaMasBajo.saldo)}"
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTO_DE_LA_GRAFICA)
            .testTag(TAG_GRAFICA_DE_LA_CAJA)
            .semantics { contentDescription = descripcion },
    ) {
        val maximo = maxOf(saldos.max(), 0L).toFloat()
        val minimo = minOf(saldos.min(), 0L).toFloat()
        val rango = (maximo - minimo).takeIf { it > 0f } ?: 1f
        val margen = 6.dp.toPx()
        val alto = size.height - 2 * margen
        fun x(i: Int) = if (saldos.size == 1) size.width / 2 else size.width * i / (saldos.size - 1)
        fun y(v: Long) = margen + alto * (maximo - v) / rango
        if (minimo < 0f) {
            drawLine(
                color = cero,
                start = Offset(0f, y(0L)),
                end = Offset(size.width, y(0L)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }
        val trazo = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Cada tramo con el color de su punta derecha: lo que baja de cero se ve rojo desde el día
        // en que cruza.
        saldos.indices.drop(1).forEach { i ->
            val path = Path().apply {
                moveTo(x(i - 1), y(saldos[i - 1]))
                lineTo(x(i), y(saldos[i]))
            }
            drawPath(path, color = if (saldos[i] < 0) rojo else linea, style = trazo)
        }
        val iBajo = caja.dias.indexOf(caja.diaMasBajo)
        drawCircle(color = punto, radius = 4.dp.toPx(), center = Offset(x(iBajo), y(saldos[iBajo])))
    }
}
