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
import com.jvillada.movi.shared.model.PuntoDelPatrimonio
import com.jvillada.movi.shared.model.cambioDelPatrimonio
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

/**
 * Lo que dice la leyenda con un punto elegido: «Al cierre de mar 2026: $1.234.567». Vuelta 60: si en
 * ese mes hubo ajustes ([ajustesDelMes]), lo dice al lado, compacto para que quepa en el renglón:
 * «Al cierre de oct 2026: $36,5M · ajustes −$35,5M».
 */
internal fun leyendaDelPunto(punto: PuntoDelPatrimonio, ajustesDelMes: Long = 0L): String {
    val donde = if (punto.esHoy) "Hoy" else "Al cierre de ${nombreDelPunto(punto)}"
    if (ajustesDelMes == 0L) return "$donde: ${formatCOP(punto.neto)}"
    return "$donde: ${formatMoneyCompact(punto.neto)} · ajustes ${conSigno(ajustesDelMes)}"
}

/**
 * **Los ajustes de cada punto** (vuelta 60): lo que cambió por ajustes de saldo y aperturas desde el
 * punto anterior. El primero es cero: no se sabe qué hubo antes de él.
 */
internal fun ajustesDelMes(puntos: List<PuntoDelPatrimonio>): List<Long> =
    puntos.mapIndexed { i, p -> if (i == 0) 0L else p.ajustes - puntos[i - 1].ajustes }

private fun conSigno(monto: Long): String = if (monto > 0L) "+${formatMoneyCompact(monto)}" else formatMoneyCompact(monto)

private fun haciaDonde(cambio: Long): String = when {
    cambio > 0 -> "subió ${formatMoneyCompact(cambio)}"
    cambio < 0 -> "bajó ${formatMoneyCompact(-cambio)}"
    else -> "sigue igual"
}

/**
 * **La nota de los ajustes** (vuelta 60), o `null` si no hubo. Registrar una deuda que ya existía con
 * «Ajustar saldo» hacía decir «Desde ago 2026, bajó $34,6M» sin que se hubiera gastado nada. La cifra
 * no cambia —es el neto de siempre—; la nota dice cuánto de ese cambio son correcciones y cuánto queda
 * sin ellas (una resta, ninguna cifra inventada): «Los ajustes suman −$35,5M: deudas y saldos que ya
 * tenías y anotaste después. Sin ellos, subió $900.000.»
 */
internal fun notaDeLosAjustes(puntos: List<PuntoDelPatrimonio>): String? {
    val cambio = cambioDelPatrimonio(puntos) ?: return null
    if (cambio.porAjustes == 0L) return null
    return "Los ajustes suman ${conSigno(cambio.porAjustes)}: $QUE_SON_LOS_AJUSTES. " +
        "Sin ellos, ${haciaDonde(cambio.sinAjustes)}."
}

/** Qué es un ajuste, en las palabras del dueño. */
const val QUE_SON_LOS_AJUSTES = "deudas y saldos que ya tenías y anotaste después"

/** Vuelta 60: la nota de los bienes, que la historia cuenta con su valor de hoy. */
const val LOS_BIENES_CON_SU_VALOR_DE_HOY = "Los bienes cuentan con su valor de hoy en todos los meses."

/** La nota de los ajustes, para medirla y buscarla. */
const val TAG_NOTA_DE_LOS_AJUSTES = "nota-de-los-ajustes"

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
 * ¿La curva arranca cuando el dueño empezó a usar Movi?
 *
 * Vuelta 60: **sale del primer movimiento real del dueño** ([HistoriaDelPatrimonio.primerMovimiento],
 * el mismo arranque de «Tus períodos»): sí solo si el primer punto de la curva es el mes de ese
 * movimiento. Antes se deducía de la curva —un arranque plano descartado, o menos cierres que la
 * ventana— y decía «Desde que usas Movi (ago 2026)» a quien tenía movimientos desde agosto de 2025: lo
 * que se descartaba eran meses con saldos sin cargar, no meses sin Movi. Sin el dato (un server viejo)
 * no se afirma.
 */
internal fun arrancaConElDueno(historia: HistoriaDelPatrimonio, puntos: List<PuntoDelPatrimonio>): Boolean {
    val primerMes = historia.primerMovimiento?.take(7) ?: return false
    return puntos.firstOrNull()?.mes == primerMes
}

/**
 * Lo que dice sin nada elegido: de dónde a dónde. «Desde que usas Movi (ago 2026), subió $5M» si la
 * curva arranca con el dueño ([arrancaConElDueno]); si no, «Desde oct 2025, subió $5M».
 */
internal fun leyendaDeLaCurva(puntos: List<PuntoDelPatrimonio>, desdeQueUsasMovi: Boolean = false): String {
    val primero = puntos.first()
    val hacia = haciaDonde(puntos.last().neto - primero.neto)
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
    val ajustes = remember(puntos) { ajustesDelMes(puntos) }
    val nota = remember(puntos) { notaDeLosAjustes(puntos) }
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
                        "hoy: ${formatMoneyCompact(puntos.last().neto)}." + (nota?.let { " $it" } ?: "")
                }
                .graficaQueSeToca(cuantos = puntos.size, datos = historia, alElegir = { elegido = it }, enPuntos = true),
        ) { dibujarLaCurva(puntos, elegido, colores, entrada.value, ajustes) }
        Spacer(Modifier.height(Movi.espacios.corto))
        // Un renglón de alto fijo: elegir un punto cambia el texto, no mueve la tarjeta.
        Text(
            text = elegido?.let { i -> puntos.getOrNull(i)?.let { leyendaDelPunto(it, ajustes.getOrElse(i) { 0L }) } } ?: leyenda,
            style = Movi.textos.apoyo,
            color = if (elegido != null) Movi.colores.texto else Movi.colores.textoMedio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(TAG_LEYENDA_DE_LA_CURVA),
        )
        // Vuelta 60: lo que la curva no es. Los ajustes no son ganancia ni pérdida (los puntos con
        // ajustes van marcados con un aro), y los bienes van con su valor de hoy en todos los meses.
        if (nota != null) {
            Text(
                nota,
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                modifier = Modifier.testTag(TAG_NOTA_DE_LOS_AJUSTES),
            )
        }
        if (historia.conBienes) {
            Text(LOS_BIENES_CON_SU_VALOR_DE_HOY, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        }
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
    ajustes: List<Long>,
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
    // Vuelta 60: el mes que trae ajustes va marcado con un aro: ahí la curva se movió por una
    // corrección, no por plata.
    if (progreso >= 1f) {
        ajustes.forEachIndexed { i, ajuste ->
            if (ajuste == 0L || i == valores.lastIndex) return@forEachIndexed
            val centro = Offset(x(i), y(valores[i]))
            drawCircle(colores.fondo, radius = radio, center = centro)
            drawCircle(colores.linea, radius = radio, center = centro, style = Stroke(width = Medida.trazo.toPx()))
        }
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
