package com.jvillada.movi.ui.analisis

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AnalisisDelPeriodo
import com.jvillada.movi.shared.model.FilaDelAnalisis
import com.jvillada.movi.shared.model.FiltroDeMovimientos
import com.jvillada.movi.shared.model.PeriodSettings
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.Tendencia
import com.jvillada.movi.shared.model.TramoDelAnalisis
import com.jvillada.movi.shared.model.VistaDelAnalisis
import com.jvillada.movi.shared.model.ajustesDelPeriodo
import com.jvillada.movi.shared.model.analisisDelPeriodo
import com.jvillada.movi.shared.model.contraElAnterior
import com.jvillada.movi.shared.model.filasDelAnalisis
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.rangoLegibleDe
import com.jvillada.movi.shared.model.tituloDelPeriodo
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.AvatarDeLaCuenta
import com.jvillada.movi.ui.components.AvatarDelComercio
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.PanelDeTablero
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.ui.components.cabenDosColumnasIguales
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.components.graficaQueSeToca
import com.jvillada.movi.ui.dashboard.descripcionDeLaFlecha
import com.jvillada.movi.ui.dashboard.textoDeLaFlecha
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import com.jvillada.movi.ui.fecha.hoyEnAppZone
import com.jvillada.movi.ui.transactions.CHIP_GASTOS
import kotlinx.datetime.LocalDate

/**
 * # «En qué se va»: el período, día por día (Ola 4 · Ver la plata)
 *
 * Se abre desde la sección del mismo nombre en Hoy. Arriba, cuánto llevas gastado contra el período
 * pasado a la misma altura; en el medio, el gráfico del gasto por día (o por semana, si el período
 * es largo) con la línea del período pasado; abajo, la lista por categoría, comercio o cuenta.
 *
 * **Todo sale de `analisisDelPeriodo` y `filasDelAnalisis`** (`:core`, puros y probados) sobre la
 * historia que Movimientos ya lee (`/api/events/by-day`, recordada en [ClaveDeLectura.EventosPorDia]):
 * no hay una lectura nueva ni una regla nueva de qué es gasto. Acá solo hay disposición y dibujo.
 *
 * Tocar una barra o una fila abre Movimientos con ese filtro ([FiltroDeMovimientos]); deslizar el
 * dedo (o el mouse) sobre el gráfico dice el valor de cada día. Las cifras respetan «ocultar
 * saldos»: salen de [formatCOP] y [formatMoneyCompact], que ya las tapan.
 */

const val TITULO_EN_QUE_SE_VA = "En qué se va"
const val TAG_GRAFICA_DEL_ANALISIS = "grafica-del-analisis"
const val TAG_FILA_DEL_ANALISIS = "fila-del-analisis"
const val TAG_LEYENDA_DEL_ANALISIS = "leyenda-del-analisis"
const val TAG_TOTAL_DEL_ANALISIS = "total-del-analisis"

/** Lo que mide como mínimo cada columna en pantalla ancha: lo de un teléfono grande. */
private val ANCHO_MINIMO_DE_COLUMNA = 400.dp

private val ALTO_DEL_GRAFICO = 168.dp

@Composable
fun EnQueSeVaScreen(
    onNavigate: (Screen) -> Unit,
    periodoInicial: String? = null,
    hoy: LocalDate = remember { hoyEnAppZone() },
) {
    var reintento by remember { mutableIntStateOf(0) }
    val perfil = rememberLectura(ClaveDeLectura.Perfil, reintento = reintento) { Repositories.wallets.getUserProfile() }
    val eventos = rememberLectura(ClaveDeLectura.EventosPorDia, reintento = reintento) { Repositories.wallets.getEventsByDay() }
    val cuentas = rememberLectura(ClaveDeLectura.Cuentas, reintento = reintento) { Repositories.wallets.getAccounts() }

    // Sin el perfil no se sabe dónde empieza el período. Si su lectura falló, el mes de calendario
    // (como Movimientos): es lo único honesto que se puede mostrar sin él.
    val ajustes: PeriodSettings? = perfil.valor?.ajustesDelPeriodo() ?: if (perfil.terminada) PeriodSettings() else null
    val periodoDeHoy = remember(ajustes, hoy) { ajustes?.let { periodoDeLaFecha(hoy.toString(), it) } }
    var periodo by remember(periodoDeHoy, periodoInicial) {
        mutableStateOf(periodoInicial?.let { periodoDelPrefijo(it) } ?: periodoDeHoy)
    }
    var vista by rememberSaveable { mutableStateOf(VistaDelAnalisis.CATEGORIA.ordinal) }
    var acumulado by rememberSaveable { mutableStateOf(false) }

    val lasCuentas = cuentas.valor.orEmpty()
    val tipos = remember(lasCuentas) { lasCuentas.associate { it.id to it.type } }
    val nombres = remember(lasCuentas) { lasCuentas.associate { it.id to it.name } }
    val dias = eventos.valor
    val p = periodo
    val analisis = remember(dias, p, ajustes, hoy, tipos) {
        if (dias == null || p == null || ajustes == null) null
        else analisisDelPeriodo(dias.flatMap { it.items }, p, ajustes, hoy, tipos)
    }
    val laVista = VistaDelAnalisis.entries[vista]
    val filas = remember(analisis, laVista, nombres) { analisis?.let { filasDelAnalisis(it, laVista, nombres) }.orEmpty() }

    fun abrirMovimientos(filtro: FiltroDeMovimientos) {
        val periodoVisible = analisis?.periodo ?: return
        onNavigate(Screen.Transactions(chipInicial = CHIP_GASTOS, periodoInicial = periodoVisible.prefijo, filtro = filtro))
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        PanelDeTablero(enDosColumnas = { cabenDosColumnasIguales(it, ANCHO_MINIMO_DE_COLUMNA) }) { dosColumnas ->
            Column(modifier = Modifier.fillMaxSize()) {
                MinScreenHeader(title = TITULO_EN_QUE_SE_VA, leading = HeaderLeading.Back(Screen.Dashboard))
                if (p != null && ajustes != null && periodoDeHoy != null) {
                    SelectorDelPeriodo(
                        periodo = p,
                        ajustes = ajustes,
                        puedeAvanzar = p.prefijo < periodoDeHoy.prefijo,
                        onAnterior = { periodo = periodoAnterior(p) },
                        onSiguiente = { periodo = periodoSiguiente(p) },
                    )
                }

                fun LazyListScope.resumenYGrafico() {
                    item {
                        when {
                            analisis != null -> {
                                ResumenDelAnalisis(analisis, hoy)
                                Spacer(Modifier.height(Movi.espacios.amplio))
                                TarjetaDelGrafico(
                                    analisis = analisis,
                                    acumulado = acumulado,
                                    onCambiarModo = { acumulado = it },
                                    onTocarTramo = { tramo -> abrirMovimientos(FiltroDeMovimientos.delTramo(tramo, rotuloDelTramo(tramo, analisis.porSemana))) },
                                )
                            }
                            dias == null && eventos.fallo ->
                                NoSePudoLeer("No pudimos leer tus gastos", onReintentar = { reintento++ })
                            else -> EsqueletoDelAnalisis()
                        }
                    }
                }

                fun LazyListScope.laLista() {
                    item {
                        SelectorSegmentado(
                            labels = VistaDelAnalisis.entries.map { it.rotulo },
                            selected = vista,
                            onSelect = { vista = it },
                        )
                        Spacer(Modifier.height(Movi.espacios.medio))
                    }
                    if (analisis != null) {
                        if (filas.isEmpty()) {
                            item {
                                Text(
                                    "No hay gastos en este período.",
                                    style = Movi.textos.cuerpo,
                                    color = Movi.colores.textoMedio,
                                    modifier = Modifier.padding(vertical = Movi.espacios.medio),
                                )
                            }
                        } else {
                            item {
                                MinCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    variant = MinCardVariant.Elevated,
                                    padding = PaddingValues(vertical = Movi.espacios.corto),
                                ) {
                                    filas.forEach { fila ->
                                        FilaDeLaLista(
                                            fila = fila,
                                            vista = laVista,
                                            tipoDeCuenta = tipos[fila.clave],
                                            onClick = { abrirMovimientos(FiltroDeMovimientos.deLaFila(fila, laVista)) },
                                        )
                                    }
                                }
                            }
                            if (analisis.conComparacion) item {
                                Spacer(Modifier.height(Movi.espacios.corto))
                                Text(
                                    pieDeLasFlechas(analisis),
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoApagado,
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(Movi.espacios.seccion)) }
                }

                val relleno = PaddingValues(horizontal = Movi.espacios.amplio)
                if (dosColumnas) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Movi.espacios.amplio),
                        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.amplio),
                    ) {
                        LazyColumn(modifier = Modifier.weight(1f)) { resumenYGrafico() }
                        LazyColumn(modifier = Modifier.weight(1f)) { laLista() }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = relleno) {
                        resumenYGrafico()
                        item { Spacer(Modifier.height(Movi.espacios.margen)) }
                        laLista()
                    }
                }
            }
        }
    }
}

// ── El período ──────────────────────────────────────────────────────────────

@Composable
private fun SelectorDelPeriodo(
    periodo: PeriodoFinanciero,
    ajustes: PeriodSettings,
    puedeAvanzar: Boolean,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Movi.espacios.amplio)
            .padding(bottom = Movi.espacios.medio),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowLeft,
            contentDescription = "Período anterior",
            tint = Movi.colores.textoMedio,
            modifier = Modifier.size(22.dp).clickable(onClick = onAnterior),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = tituloDelPeriodo(periodo),
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Medium,
                color = Movi.colores.texto,
            )
            rangoLegibleDe(periodo, ajustes)?.let { rango ->
                Text(rango, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            }
        }
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowRight,
            contentDescription = "Período siguiente",
            tint = if (puedeAvanzar) Movi.colores.textoMedio else Movi.colores.textoApagado,
            modifier = Modifier.size(22.dp).then(if (puedeAvanzar) Modifier.clickable(onClick = onSiguiente) else Modifier),
        )
    }
}

// ── Cuánto llevas ───────────────────────────────────────────────────────────

/** «Llevas gastado» en el período en curso; «Gastaste» en uno que ya cerró. */
internal fun rotuloDelTotal(analisis: AnalisisDelPeriodo, hoy: LocalDate): String =
    if (hoy <= analisis.hasta) "LLEVAS GASTADO" else "GASTASTE"

/**
 * La frase de la comparación: «$120.000 más que a esta altura del período pasado ($1,2M)», o por qué
 * no hay comparación.
 */
internal fun fraseDeLaComparacion(analisis: AnalisisDelPeriodo): String {
    val anterior = analisis.anteriorAMismaAltura
        ?: return "Todavía no hay un período pasado completo para comparar."
    val flecha = contraElAnterior(analisis.total, anterior)
        ?: return "Todavía no hay un período pasado completo para comparar."
    val cifra = formatMoneyCompact(anterior)
    return when (flecha.tendencia) {
        Tendencia.SUBE -> "${formatMoneyCompact(flecha.diferencia)} más que a esta altura del período pasado ($cifra)"
        Tendencia.BAJA -> "${formatMoneyCompact(flecha.diferencia)} menos que a esta altura del período pasado ($cifra)"
        Tendencia.IGUAL -> "Lo mismo que a esta altura del período pasado ($cifra)"
    }
}

@Composable
private fun ResumenDelAnalisis(analisis: AnalisisDelPeriodo, hoy: LocalDate) {
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(Movi.espacios.margen),
    ) {
        Text(
            text = rotuloDelTotal(analisis, hoy),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(Movi.espacios.minimo))
        Box(Modifier.testTag(TAG_TOTAL_DEL_ANALISIS)) {
            Cifra(formatCOP(analisis.total), Movi.textos.titular, color = Movi.colores.texto, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(Movi.espacios.minimo))
        val tendencia = analisis.anteriorAMismaAltura?.let { contraElAnterior(analisis.total, it)?.tendencia }
        Text(
            text = fraseDeLaComparacion(analisis),
            style = Movi.textos.apoyo,
            // Gastar menos es la buena noticia (`entra`); gastar más se dice en el color del texto:
            // no es una alerta (Ola 2a), no hay un presupuesto vencido.
            color = if (tendencia == Tendencia.BAJA) Movi.colores.entra else Movi.colores.textoMedio,
        )
    }
}

// ── El gráfico ──────────────────────────────────────────────────────────────

/** «12 oct». */
internal fun fechaCorta(fecha: LocalDate): String = "${fecha.dayOfMonth} ${MESES_DEL_ANIO[fecha.monthNumber - 1].take(3)}"

/** Lo que dice el chip de Movimientos al tocar una barra: el día, o la semana. */
internal fun rotuloDelTramo(tramo: TramoDelAnalisis, porSemana: Boolean): String =
    if (porSemana || tramo.desde != tramo.hasta) "${fechaCorta(tramo.desde)} al ${fechaCorta(tramo.hasta)}" else fechaCorta(tramo.desde)

/**
 * **La leyenda de una barra elegida**: el día, lo gastado y el período pasado. En el modo acumulado,
 * lo que se llevaba a ese día.
 */
internal fun leyendaDelTramo(tramo: TramoDelAnalisis, porSemana: Boolean, acumulado: Boolean): String {
    val cuando = rotuloDelTramo(tramo, porSemana)
    if (tramo.porVenir) return "$cuando · todavía no llega"
    val este = if (acumulado) tramo.acumulado else tramo.gasto
    val antes = if (acumulado) tramo.acumuladoAnterior else tramo.anterior
    val loDeEste = if (acumulado) "llevabas ${formatCOP(este)}" else formatCOP(este)
    return if (antes != null) "$cuando · $loDeEste · período pasado ${formatCOP(antes)}" else "$cuando · $loDeEste"
}

/** Lo que dice la leyenda sin nada elegido: cómo se usa el gráfico. */
internal fun leyendaSinElegir(analisis: AnalisisDelPeriodo): String =
    if (analisis.conComparacion) "La línea es el período pasado. Desliza para ver cada día."
    else "Desliza para ver cada día; toca una barra para ver sus movimientos."

@Composable
private fun TarjetaDelGrafico(
    analisis: AnalisisDelPeriodo,
    acumulado: Boolean,
    onCambiarModo: (Boolean) -> Unit,
    onTocarTramo: (TramoDelAnalisis) -> Unit,
) {
    var elegido by remember(analisis) { mutableStateOf<Int?>(null) }
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(Movi.espacios.amplio),
    ) {
        SelectorSegmentado(
            labels = listOf(if (analisis.porSemana) "Por semana" else "Por día", "Acumulado"),
            selected = if (acumulado) 1 else 0,
            onSelect = { onCambiarModo(it == 1) },
        )
        Spacer(Modifier.height(Movi.espacios.medio))
        // Un renglón de alto fijo: elegir una barra cambia el texto, nunca empuja el gráfico.
        Text(
            text = elegido?.let { analisis.tramos.getOrNull(it) }?.let { leyendaDelTramo(it, analisis.porSemana, acumulado) }
                ?: leyendaSinElegir(analisis),
            style = Movi.textos.apoyo,
            color = if (elegido != null) Movi.colores.texto else Movi.colores.textoMedio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag(TAG_LEYENDA_DEL_ANALISIS),
        )
        Spacer(Modifier.height(Movi.espacios.corto))
        GraficoDelAnalisis(
            analisis = analisis,
            acumulado = acumulado,
            elegido = elegido,
            alElegir = { elegido = it },
            alTocar = { i -> analisis.tramos.getOrNull(i)?.takeUnless { it.porVenir }?.let(onTocarTramo) },
        )
        Spacer(Modifier.height(Movi.espacios.minimo))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fechaCorta(analisis.desde), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            Text(fechaCorta(analisis.hasta), style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
        }
    }
}

@Composable
private fun GraficoDelAnalisis(
    analisis: AnalisisDelPeriodo,
    acumulado: Boolean,
    elegido: Int?,
    alElegir: (Int?) -> Unit,
    alTocar: (Int) -> Unit,
) {
    val barra = Movi.colores.neutro
    val barraElegida = Movi.colores.textoMedio
    val linea = Movi.colores.marca
    val pista = Movi.colores.hilo
    val reducido = LocalMovimientoReducido.current
    // Las barras crecen al llegar y al cambiar de período o de modo, salvo con movimiento reducido.
    val entrada = remember(analisis.periodo, acumulado) { Animatable(if (reducido) 1f else 0f) }
    LaunchedEffect(analisis.periodo, acumulado) { entrada.animateTo(1f, Movimiento.alEntrar(reducido)) }

    val tramos = analisis.tramos
    val barras = tramos.map { if (it.porVenir) null else if (acumulado) it.acumulado else it.gasto }
    val anteriores = tramos.map { if (acumulado) it.acumuladoAnterior else it.anterior }
    val descripcion = "Gráfico del gasto ${if (analisis.porSemana) "por semana" else "por día"}" +
        (if (acumulado) ", acumulado" else "") + ": ${formatMoneyCompact(analisis.total)} en el período" +
        (analisis.anteriorAMismaAltura?.let { ", el período pasado a esta altura ${formatMoneyCompact(it)}" } ?: "")
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTO_DEL_GRAFICO)
            .testTag(TAG_GRAFICA_DEL_ANALISIS)
            .semantics { contentDescription = descripcion }
            .graficaQueSeToca(cuantos = tramos.size, datos = analisis, alElegir = alElegir, alTocar = alTocar),
    ) {
        val maximo = (barras.filterNotNull() + anteriores.filterNotNull()).maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val arriba = 8.dp.toPx()
        val alto = size.height - arriba
        val franja = size.width / tramos.size
        val ancho = (franja * 0.62f).coerceAtLeast(1f)
        fun y(v: Long): Float = arriba + alto * (1f - v.toFloat() / (maximo * 1.08f))
        // La base: dónde está el cero.
        drawLine(pista, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        barras.forEachIndexed { i, v ->
            if (v == null || v <= 0L) return@forEachIndexed
            val tope = y(v)
            val altoDeLaBarra = (size.height - tope) * entrada.value
            drawRoundRect(
                color = if (i == elegido) barraElegida else barra,
                topLeft = Offset(franja * i + (franja - ancho) / 2, size.height - altoDeLaBarra),
                size = Size(ancho, altoDeLaBarra),
                cornerRadius = CornerRadius(minOf(ancho / 2, 3.dp.toPx())),
            )
        }
        // La línea del período pasado, por los centros de las franjas que tuvo.
        val path = Path()
        var empezada = false
        anteriores.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val x = franja * i + franja / 2
            val yv = size.height - (size.height - y(v)) * entrada.value
            if (!empezada) path.moveTo(x, yv) else path.lineTo(x, yv)
            empezada = true
        }
        if (empezada) {
            drawPath(
                path,
                color = linea,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                ),
            )
        }
        // La guía de lo elegido.
        elegido?.let { i ->
            val x = franja * i + franja / 2
            drawLine(barraElegida, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx(), alpha = 0.35f)
        }
    }
}

// ── La lista ────────────────────────────────────────────────────────────────

/** Qué dicen las flechas de la lista. */
internal fun pieDeLasFlechas(analisis: AnalisisDelPeriodo): String = when (val d = analisis.diasComparados) {
    0, 1 -> "Las flechas comparan con el primer día del período pasado."
    else -> "Las flechas comparan con los primeros $d días del período pasado."
}

@Composable
private fun FilaDeLaLista(
    fila: FilaDelAnalisis,
    vista: VistaDelAnalisis,
    tipoDeCuenta: AccountType?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Ver los movimientos", onClick = onClick)
            .testTag(TAG_FILA_DEL_ANALISIS)
            .padding(horizontal = Movi.espacios.amplio, vertical = Movi.espacios.corto),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (vista) {
            VistaDelAnalisis.CATEGORIA -> IconoDeCategoria(fila.nombre)
            VistaDelAnalisis.COMERCIO -> AvatarDelComercio(fila.muestra)
            VistaDelAnalisis.CUENTA -> AvatarDeLaCuenta(nombre = fila.nombre, tipo = tipoDeCuenta ?: AccountType.CHECKING)
        }
        Spacer(Modifier.size(Movi.espacios.medio))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fila.nombre,
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${(fila.fraccion * 100).toInt()} % · ${if (fila.veces == 1) "1 movimiento" else "${fila.veces} movimientos"}",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
                maxLines = 1,
            )
        }
        Spacer(Modifier.size(Movi.espacios.corto))
        Column(horizontalAlignment = Alignment.End) {
            Cifra(formatMoneyCompact(fila.gasto), Movi.textos.monto, color = Movi.colores.texto)
            fila.contraElAnterior?.let { flecha ->
                val color = when (flecha.tendencia) {
                    Tendencia.SUBE -> Movi.colores.texto
                    Tendencia.BAJA -> Movi.colores.entra
                    Tendencia.IGUAL -> Movi.colores.textoApagado
                }
                val descripcion = descripcionDeLaFlecha(flecha)
                Text(
                    text = textoDeLaFlecha(flecha),
                    style = Movi.textos.apoyo,
                    color = color,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 64.dp).semantics { contentDescription = descripcion },
                )
            }
        }
    }
}

@Composable
private fun EsqueletoDelAnalisis() {
    MinCard(
        modifier = Modifier.fillMaxWidth(),
        variant = MinCardVariant.Elevated,
        padding = PaddingValues(Movi.espacios.margen),
    ) {
        LineaEsqueleto(fraccionDelAncho = 0.3f, estilo = Movi.textos.apoyo)
        Spacer(Modifier.height(Movi.espacios.corto))
        LineaEsqueleto(fraccionDelAncho = 0.55f, estilo = Movi.textos.titular)
        Spacer(Modifier.height(Movi.espacios.amplio))
        Spacer(Modifier.height(ALTO_DEL_GRAFICO))
    }
}
