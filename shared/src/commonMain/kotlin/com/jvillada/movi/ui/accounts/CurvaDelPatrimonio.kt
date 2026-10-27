package com.jvillada.movi.ui.accounts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.HistoriaDelPatrimonio
import com.jvillada.movi.shared.model.MESES_DE_HISTORIA
import com.jvillada.movi.shared.model.PuntoDelPatrimonio
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.components.graficaQueSeToca
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import com.jvillada.movi.theme.Medida

/**
 * # El patrimonio en el tiempo (Ola 4 · Ver la plata)
 *
 * Debajo de la cifra de «Patrimonio neto»: el neto al cierre de cada mes de los últimos doce (o desde
 * el primer movimiento) y el punto de hoy, resaltado. Una línea con un área suave debajo, sin ejes ni
 * cifras sueltas: la cifra grande ya está arriba, y tocar o deslizar sobre la curva dice el mes y su
 * valor en el renglón de abajo.
 *
 * Los puntos los arma el server (`GET /api/patrimonio/historia`) con `patrimonioDe`, la única regla
 * del patrimonio. Lo que la historia no sabe lo dice debajo, en una línea chica.
 */

const val TAG_CURVA_DEL_PATRIMONIO = "curva-del-patrimonio"
const val TAG_LEYENDA_DE_LA_CURVA = "leyenda-de-la-curva"

/** La línea de las cuentas en dólares. */
const val CON_LA_TRM_DE_HOY = "Con la TRM de hoy"

private val ALTO_DE_LA_CURVA = 72.dp

/** «oct 2026»; el punto de hoy, «Hoy». */
internal fun nombreDelPunto(punto: PuntoDelPatrimonio): String {
    if (punto.esHoy) return "Hoy"
    val periodo = periodoDelPrefijo(punto.mes) ?: return punto.mes
    return "${MESES_DEL_ANIO[periodo.month - 1].take(3)} ${periodo.year}"
}

/** Lo que dice la leyenda con un punto elegido: «Al cierre de mar 2026: $1.234.567». */
internal fun leyendaDelPunto(punto: PuntoDelPatrimonio): String =
    if (punto.esHoy) "Hoy: ${formatCOP(punto.neto)}"
    else "Al cierre de ${nombreDelPunto(punto)}: ${formatCOP(punto.neto)}"

/**
 * **La curva arranca donde hay saldos de verdad** (vuelta de diseño 23). En la cuenta demo decía
 * «Desde oct 2025, subió $10,9M» con una línea plana que saltaba de golpe: los meses de antes eran
 * movimientos viejos sin las cuentas cargadas, y el salto era el día que se cargaron los saldos, no una
 * ganancia. El server ya omite los meses antes del primer movimiento, pero ahí sí había movimientos.
 *
 * Se descartan los puntos del principio que son cero o que siguen planos —un mes que cierra igual que
 * el anterior no tuvo ningún movimiento— hasta el primero que cambia. Un primer punto distinto de cero
 * y seguido de otro distinto se queda: ya era un saldo que se movía. No es una regla de plata: cada
 * punto sigue siendo el que armó el server con `patrimonioDe`; solo cambia desde dónde se mira.
 */
internal fun puntosConSaldos(puntos: List<PuntoDelPatrimonio>): List<PuntoDelPatrimonio> {
    val primero = puntos.firstOrNull()?.neto ?: return puntos
    val cambia = puntos.indexOfFirst { it.neto != primero }
    if (cambia <= 0) return puntos // todo igual (o vacío): no hay arranque que descartar
    return if (primero == 0L || cambia >= 2) puntos.drop(cambia) else puntos
}

/**
 * ¿La curva arranca cuando el dueño empezó a usar Movi? Sí si se descartó un arranque plano, o si el
 * server trajo menos cierres que la ventana (empezó a cortar en su primer movimiento). Si trajo la
 * ventana entera, la curva solo muestra los últimos meses.
 */
internal fun arrancaConElDueno(historia: HistoriaDelPatrimonio, puntos: List<PuntoDelPatrimonio>): Boolean =
    puntos.size < historia.puntos.size || historia.puntos.size < MESES_DE_HISTORIA + 1

/**
 * Lo que dice sin nada elegido: de dónde a dónde. «Desde que usas Movi (ago 2026), subió $5M» si la
 * curva arranca con el dueño ([arrancaConElDueno]); si no, «Desde oct 2025, subió $5M».
 */
internal fun leyendaDeLaCurva(puntos: List<PuntoDelPatrimonio>, desdeQueUsasMovi: Boolean = false): String {
    val primero = puntos.first()
    val cambio = puntos.last().neto - primero.neto
    val hacia = when {
        cambio > 0 -> "subió ${formatMoneyCompact(cambio)}"
        cambio < 0 -> "bajó ${formatMoneyCompact(-cambio)}"
        else -> "sigue igual"
    }
    val desde = if (desdeQueUsasMovi) "Desde que usas Movi (${nombreDelPunto(primero)})" else "Desde ${nombreDelPunto(primero)}"
    return "$desde, $hacia"
}

/** ¿Hay curva que dibujar? Con un solo punto con saldos (hoy) no hay historia. */
internal fun hayCurva(historia: HistoriaDelPatrimonio?): Boolean = puntosConSaldos(historia?.puntos.orEmpty()).size >= 2

/**
 * La curva y su leyenda. Sin historia ([hayCurva] falso) no pinta nada.
 */
@Composable
internal fun CurvaDelPatrimonio(historia: HistoriaDelPatrimonio, modifier: Modifier = Modifier) {
    if (!hayCurva(historia)) return
    val puntos = remember(historia) { puntosConSaldos(historia.puntos) }
    val leyenda = leyendaDeLaCurva(puntos, desdeQueUsasMovi = arrancaConElDueno(historia, puntos))
    var elegido by remember(historia) { mutableStateOf<Int?>(null) }
    // Los colores se leen acá: una lambda de dibujo no puede leer un CompositionLocal. La curva va en
    // el color del texto (el patrimonio es neutro en los dos signos, como su cifra de arriba).
    val colores = ColoresDeLaCurva(
        linea = Movi.colores.textoMedio,
        area = Movi.colores.neutro,
        punto = Movi.colores.texto,
        fondo = Movi.colores.tarjeta,
        guia = Movi.colores.hilo,
    )
    val reducido = LocalMovimientoReducido.current
    // La curva se dibuja de izquierda a derecha al llegar, salvo con movimiento reducido.
    val entrada = remember(historia) { Animatable(if (reducido) 1f else 0f) }
    LaunchedEffect(historia) { entrada.animateTo(1f, Movimiento.alEntrar(reducido)) }
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Movi.espacios.medio))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTO_DE_LA_CURVA)
                .testTag(TAG_CURVA_DEL_PATRIMONIO)
                .semantics {
                    contentDescription = "Tu patrimonio neto en el tiempo: $leyenda. " +
                        "${nombreDelPunto(puntos.first())}: ${formatMoneyCompact(puntos.first().neto)}; " +
                        "hoy: ${formatMoneyCompact(puntos.last().neto)}."
                }
                .graficaQueSeToca(cuantos = puntos.size, datos = historia, alElegir = { elegido = it }, enPuntos = true),
        ) { dibujarLaCurva(puntos, elegido, colores, entrada.value) }
        Spacer(Modifier.height(Movi.espacios.corto))
        // Un renglón de alto fijo: elegir un punto cambia el texto, no mueve la tarjeta.
        Text(
            text = elegido?.let { puntos.getOrNull(it) }?.let(::leyendaDelPunto) ?: leyenda,
            style = Movi.textos.apoyo,
            color = if (elegido != null) Movi.colores.texto else Movi.colores.textoMedio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(TAG_LEYENDA_DE_LA_CURVA),
        )
        if (historia.conLaTrmDeHoy) {
            Text(CON_LA_TRM_DE_HOY, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        }
    }
}

/**
 * El lugar de la curva mientras su lectura viaja sin nada recordado: el mismo alto, para que la
 * tarjeta no crezca de golpe cuando la historia llega.
 */
@Composable
internal fun ReservaDeLaCurva() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Movi.espacios.medio))
        Spacer(Modifier.height(ALTO_DE_LA_CURVA))
        Spacer(Modifier.height(Movi.espacios.corto))
        com.jvillada.movi.ui.components.LineaEsqueleto(fraccionDelAncho = 0.4f, estilo = Movi.textos.apoyo)
    }
}

private class ColoresDeLaCurva(
    val linea: androidx.compose.ui.graphics.Color,
    val area: androidx.compose.ui.graphics.Color,
    val punto: androidx.compose.ui.graphics.Color,
    val fondo: androidx.compose.ui.graphics.Color,
    val guia: androidx.compose.ui.graphics.Color,
)

/**
 * La línea, el área suave debajo, el punto de hoy y la guía de lo elegido.
 *
 * **La escala se ajusta al rango de los datos** con un margen, no a cero: un patrimonio de −$600M que
 * se mueve $20M al mes dibujado desde cero sería una raya plana.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.dibujarLaCurva(
    puntos: List<PuntoDelPatrimonio>,
    elegido: Int?,
    colores: ColoresDeLaCurva,
    progreso: Float,
) {
    val valores = puntos.map { it.neto }
    val maximo = valores.max()
    val minimo = valores.min()
    val margen = ((maximo - minimo) / 8).coerceAtLeast(1L)
    val tope = (maximo + margen).toFloat()
    val piso = (minimo - margen).toFloat()
    val rango = (tope - piso).takeIf { it > 0f } ?: 1f
    val radio = 4.dp.toPx()
    fun x(i: Int) = radio + (size.width - 2 * radio) * i / (puntos.size - 1)
    fun y(v: Long) = radio + (size.height - 2 * radio) * (tope - v) / rango
    val linea = Path().apply {
        moveTo(x(0), y(valores[0]))
        valores.indices.drop(1).forEach { lineTo(x(it), y(valores[it])) }
    }
    val area = Path().apply {
        addPath(linea)
        lineTo(x(valores.lastIndex), size.height)
        lineTo(x(0), size.height)
        close()
    }
    // Lo que todavía no se dibujó queda tapado: la curva entra de izquierda a derecha.
    clipRect(right = size.width * progreso) {
        drawPath(
            area,
            brush = Brush.verticalGradient(listOf(colores.area.copy(alpha = 0.55f), colores.area.copy(alpha = 0f))),
        )
        drawPath(
            linea,
            color = colores.linea,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    elegido?.let { i ->
        drawLine(colores.guia, Offset(x(i), 0f), Offset(x(i), size.height), strokeWidth = Medida.trazo.toPx())
        drawCircle(colores.linea, radius = radio * 0.8f, center = Offset(x(i), y(valores[i])))
    }
    // Hoy, resaltado: un punto lleno con aro del color de la tarjeta.
    if (progreso >= 1f) {
        val hoy = Offset(x(valores.lastIndex), y(valores.last()))
        drawCircle(colores.fondo, radius = radio + 2.dp.toPx(), center = hoy)
        drawCircle(colores.punto, radius = radio, center = hoy)
    }
}
