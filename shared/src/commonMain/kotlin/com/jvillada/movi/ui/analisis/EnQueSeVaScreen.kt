package com.jvillada.movi.ui.analisis

import com.jvillada.movi.shared.model.periodoDelId
import com.jvillada.movi.ui.components.ds.IconoDeMovimiento
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.data.ClaveDeLectura
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.data.rememberLectura
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.AnalisisDelPeriodo
import com.jvillada.movi.shared.model.DesgloseDelGastado
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.shared.model.desgloseDelGastado
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
import com.jvillada.movi.shared.model.movimientosDelAnalisis
import com.jvillada.movi.shared.model.periodoAnterior
import com.jvillada.movi.shared.model.periodoDeLaFecha
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.periodoSiguiente
import com.jvillada.movi.shared.model.nombreDelPeriodo
import com.jvillada.movi.shared.model.ACCION_DEL_HUECO
import com.jvillada.movi.shared.model.RangoDelAnalisis
import com.jvillada.movi.shared.model.accionDelPeriodoQueVaEmpezando
import com.jvillada.movi.shared.model.analisisDelRango
import com.jvillada.movi.shared.model.huecosEnLaHistoria
import com.jvillada.movi.shared.model.inicioDelPeriodo
import com.jvillada.movi.shared.model.montoContado
import com.jvillada.movi.shared.model.nombreDelRango
import com.jvillada.movi.shared.model.periodoQueVaEmpezando
import com.jvillada.movi.shared.model.periodosDelRango
import com.jvillada.movi.shared.model.pieDeLasFlechasDelRango
import com.jvillada.movi.shared.model.pieSinFlechasDelPeriodo
import com.jvillada.movi.shared.model.textoDelHueco
import com.jvillada.movi.shared.model.textoDelPeriodoQueVaEmpezando
import com.jvillada.movi.ui.components.LocalAtajosDeAgregar
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.quickadd.AtajoDeAgregar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import com.jvillada.movi.theme.LocalMovimientoReducido
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Movimiento
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.components.AvatarDeLaCuenta
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.LineaEsqueleto
import com.jvillada.movi.ui.components.MinScreenHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.PanelDeTablero
import com.jvillada.movi.ui.components.SelectorSegmentado
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.BotonDeIcono
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.Vacio
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.Cifra as DsCifra
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
import com.jvillada.movi.ui.components.rememberListaConElTeclado

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
const val TAG_SELECTOR_DEL_RANGO = "selector-del-rango-del-analisis"
const val TAG_NOMBRE_DEL_PERIODO_DEL_ANALISIS = "nombre-del-periodo-del-analisis"
const val TAG_VA_EMPEZANDO = "analisis-va-empezando"
const val TAG_HUECO_EN_LA_HISTORIA = "analisis-hueco-en-la-historia"

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
    // R3 · tu parte de lo que pagaron otros («Caro pagó el mercado»): es gasto tuyo el día del gasto,
    // igual que en «Salió». No está en la lista de movimientos (no es de tus cuentas).
    var tuParteDeOtros by remember { mutableStateOf<List<com.jvillada.movi.shared.model.FinancialEvent>>(emptyList()) }
    LaunchedEffect(reintento) {
        tuParteDeOtros = runCatching { Repositories.wallets.getTuParteDeOtros() }.getOrDefault(emptyList())
    }

    // Sin el perfil no se sabe dónde empieza el período. Si su lectura falló, el mes de calendario
    // (como Movimientos): es lo único honesto que se puede mostrar sin él.
    val ajustes: PeriodSettings? = perfil.valor?.ajustesDelPeriodo() ?: if (perfil.terminada) PeriodSettings() else null
    val periodoDeHoy = remember(ajustes, hoy) { ajustes?.let { periodoDeLaFecha(hoy.toString(), it) } }
    // Vuelta 60: el gasto del día a día del período en curso, el MISMO que mide «Cuánto puedes gastar»
    // (la misma lectura que el Inicio, recordada). Con él, el total se parte en día a día + fijos. La
    // clave depende del período: sin decirle cuál, `rememberLectura` no lee nada.
    val resumen = rememberLectura(
        ClaveDeLectura.ResumenDelTablero(Scope.SELF),
        reintento = reintento,
        periodoVigente = periodoDeHoy?.prefijo,
        activa = periodoDeHoy != null,
    ) {
        Repositories.wallets.getDashboardSummary(Scope.SELF)
    }
    var periodo by remember(periodoDeHoy, periodoInicial) {
        mutableStateOf(periodoInicial?.let { periodoDelId(it) } ?: periodoDeHoy)
    }
    var vista by rememberSaveable { mutableStateOf(VistaDelAnalisis.CATEGORIA.ordinal) }
    var acumulado by rememberSaveable { mutableStateOf(false) }
    // Vuelta 140: «Este período · 3 meses · 12 meses», recordado en la sesión (ver [RangoDeEnQueSeVa]).
    val rango = RangoDeEnQueSeVa.elegido

    val lasCuentas = cuentas.valor.orEmpty()
    val tipos = remember(lasCuentas) { lasCuentas.associate { it.id to it.type } }
    val nombres = remember(lasCuentas) { lasCuentas.associate { it.id to it.name } }
    val dias = eventos.valor
    val p = periodo
    val movimientos = remember(dias, tuParteDeOtros) { dias?.let { movimientosDelAnalisis(it, tuParteDeOtros) } }
    // Un rango termina siempre en el período en curso: «los últimos 3 períodos, incluido el en curso».
    val ultimo = if (rango == RangoDelAnalisis.PERIODO) p else periodoDeHoy
    val analisis = remember(movimientos, ultimo, rango, ajustes, hoy, tipos) {
        if (movimientos == null || ultimo == null || ajustes == null) null
        else analisisDelRango(movimientos, ultimo, rango, ajustes, hoy, tipos)
    }
    // El período que se ve con «Este período» (el en curso, o uno pasado con ‹), para saber si su vista
    // tiene el renglón del desglose.
    val delPeriodoElegido = remember(movimientos, p, ajustes, hoy, tipos) {
        if (movimientos == null || p == null || ajustes == null) null
        else analisisDelPeriodo(movimientos, p, ajustes, hoy, tipos)
    }
    val gastoVariable = resumen.valor?.gastoVariablePorDia
    val desglose = remember(analisis, gastoVariable, hoy) {
        analisis?.takeUnless { it.esRango }?.let { desgloseDelAnalisis(it, gastoVariable, hoy) }
    }
    // Vuelta 140: en un rango, el renglón del desglose (que es solo del período en curso) dice cuántos
    // gastos y categorías junta el rango. Se pinta solo si la vista de período que se deja —la del
    // período elegido con ‹ ›, que puede ser uno pasado sin desglose— lo tenía: así cambiar de rango no
    // corre el gráfico (el renglón está o no está en los dos).
    val renglonDelRango = remember(analisis, delPeriodoElegido, gastoVariable, hoy) {
        if (analisis == null || !analisis.esRango || delPeriodoElegido == null) null
        else if (desgloseDelAnalisis(delPeriodoElegido, gastoVariable, hoy) == null) null
        else textoDelRenglonDelRango(analisis)
    }
    val laVista = VistaDelAnalisis.entries[vista]
    val filas = remember(analisis, laVista, nombres) { analisis?.let { filasDelAnalisis(it, laVista, nombres) }.orEmpty() }
    val vaEmpezando = remember(analisis, movimientos, ajustes, hoy, tipos) {
        if (analisis == null || movimientos == null || ajustes == null) null
        else periodoQueVaEmpezando(analisis, movimientos, ajustes, hoy, tipos)
    }
    val hueco = remember(analisis, movimientos, ajustes, rango) {
        if (analisis == null || movimientos == null || ajustes == null || !analisis.esRango) null
        else huecosEnLaHistoria(movimientos, periodosDelRango(analisis.periodo, rango, ajustes), ajustes).lastOrNull()
    }
    val atajos = LocalAtajosDeAgregar.current

    fun abrirMovimientos(filtro: FiltroDeMovimientos, periodoDeLaLista: PeriodoFinanciero? = null) {
        val periodoVisible = periodoDeLaLista ?: analisis?.periodo ?: return
        onNavigate(Screen.Transactions(chipInicial = CHIP_GASTOS, periodoInicial = periodoVisible.prefijo, filtro = filtro))
    }

    /**
     * Una barra: sus días. En un rango, si la barra cae entera en un período, Movimientos abre en ese
     * período; si cruza dos (una semana del borde), el filtro lleva sus días y Movimientos no recorta.
     */
    fun abrirElTramo(tramo: TramoDelAnalisis, a: AnalisisDelPeriodo) {
        val filtro = FiltroDeMovimientos.delTramo(tramo, rotuloDelTramo(tramo, a.porSemana || a.porMes))
        val suPeriodo = ajustes?.let { aj ->
            val d = periodoDeLaFecha(tramo.desde.toString(), aj)
            d.takeIf { it != null && it == periodoDeLaFecha(tramo.hasta.toString(), aj) }
        }
        abrirMovimientos(filtro, suPeriodo)
    }

    Box(modifier = Modifier.fillMaxSize().background(Movi.colores.fondo)) {
        PanelDeTablero(enDosColumnas = { cabenDosColumnasIguales(it, ANCHO_MINIMO_DE_COLUMNA) }) { dosColumnas ->
            Column(modifier = Modifier.fillMaxSize()) {
                MinScreenHeader(title = TITULO_EN_QUE_SE_VA, leading = HeaderLeading.Back(Screen.Dashboard))
                if (p != null && ajustes != null && periodoDeHoy != null) {
                    if (rango == RangoDelAnalisis.PERIODO) {
                        SelectorDelPeriodo(
                            nombre = nombreDelPeriodo(p, ajustes),
                            puedeRetroceder = true,
                            puedeAvanzar = p.prefijo < periodoDeHoy.prefijo,
                            onAnterior = { periodo = periodoAnterior(p, ajustes) },
                            onSiguiente = { periodo = periodoSiguiente(p, ajustes) },
                        )
                    } else {
                        // Vuelta 140: un rango no se corre con ‹ ›; las flechas se apagan en su lugar
                        // (no se van: el nombre no salta).
                        val periodos = periodosDelRango(periodoDeHoy, rango, ajustes)
                        SelectorDelPeriodo(
                            nombre = nombreDelRango(
                                inicioDelPeriodo(periodos.first(), ajustes),
                                inicioDelPeriodo(periodoSiguiente(periodos.last(), ajustes), ajustes).minus(1, DateTimeUnit.DAY),
                            ),
                            puedeRetroceder = false,
                            puedeAvanzar = false,
                            onAnterior = {},
                            onSiguiente = {},
                        )
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = Medida.margenDePantalla).testTag(TAG_SELECTOR_DEL_RANGO)) {
                        SelectorSegmentado(
                            labels = RangoDelAnalisis.entries.map { it.rotulo },
                            selected = rango.ordinal,
                            onSelect = { RangoDeEnQueSeVa.elegido = RangoDelAnalisis.entries[it] },
                        )
                    }
                    Spacer(Modifier.height(Medida.dentroDeSeccion))
                }

                fun LazyListScope.resumenYGrafico() {
                    item {
                        when {
                            analisis != null -> {
                                ResumenDelAnalisis(analisis, hoy, desglose, renglonDelRango, rango)
                                Spacer(Modifier.height(Medida.dentroDeSeccion))
                                TarjetaDelGrafico(
                                    analisis = analisis,
                                    rango = rango,
                                    acumulado = acumulado,
                                    onCambiarModo = { acumulado = it },
                                    onTocarTramo = { tramo -> abrirElTramo(tramo, analisis) },
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
                        Seccion(titulo = "Por dónde se fue")
                        SelectorSegmentado(
                            labels = VistaDelAnalisis.entries.map { it.rotulo },
                            selected = vista,
                            onSelect = { vista = it },
                        )
                        Spacer(Modifier.height(Medida.dentroDeSeccion))
                    }
                    // Vuelta 140: encima de la lista, lo que la pantalla sabe y la lista no dice: que el
                    // período va empezando y hay historia antes, o que en el rango falta un período entero.
                    vaEmpezando?.let { v ->
                        item {
                            LineaConAccion(
                                texto = textoDelPeriodoQueVaEmpezando(v, hoy.year),
                                accion = accionDelPeriodoQueVaEmpezando(v),
                                onAccion = { RangoDeEnQueSeVa.elegido = v.rango },
                                tag = TAG_VA_EMPEZANDO,
                            )
                        }
                    }
                    hueco?.let { h ->
                        item {
                            LineaConAccion(
                                texto = textoDelHueco(h),
                                accion = ACCION_DEL_HUECO.takeIf { atajos != null },
                                onAccion = { atajos?.invoke(AtajoDeAgregar.SUBIR_COMPROBANTE) },
                                tag = TAG_HUECO_EN_LA_HISTORIA,
                            )
                        }
                    }
                    if (analisis != null) {
                        if (filas.isEmpty()) {
                            item {
                                Vacio(
                                    titulo = if (analisis.esRango) "No hay gastos en ${rango.rotulo}" else "No hay gastos en este período",
                                    detalle = "Lo que gastes aparece aquí, por categoría, comercio o cuenta.",
                                )
                            }
                        } else {
                            item {
                                Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                                    filas.forEach { fila ->
                                        FilaDeLaLista(
                                            fila = fila,
                                            vista = laVista,
                                            tipoDeCuenta = tipos[fila.clave],
                                            onClick = {
                                                // Vuelta 140: en un rango, el filtro lleva sus días y
                                                // Movimientos muestra el rango entero (no lo recorta al período).
                                                abrirMovimientos(
                                                    if (analisis.esRango) FiltroDeMovimientos.deLaFilaEnElRango(fila, laVista, analisis)
                                                    else FiltroDeMovimientos.deLaFila(fila, laVista),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                            // Vuelta 140: sin flechas porque el período pasado es un hueco, el pie dice por qué.
                            val pie = when {
                                analisis.esRango -> pieDeLasFlechasDelRango(analisis, rango)
                                analisis.conComparacion -> pieDeLasFlechas(analisis)
                                else -> pieSinFlechasDelPeriodo(analisis)
                            }
                            if (pie != null) item {
                                Spacer(Modifier.height(Espacio.xs))
                                Text(
                                    pie,
                                    style = Movi.textos.apoyo,
                                    color = Movi.colores.textoMedio,
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(Medida.entreSecciones)) }
                }

                val relleno = PaddingValues(horizontal = Medida.margenDePantalla)
                if (dosColumnas) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Medida.margenDePantalla),
                        horizontalArrangement = Arrangement.spacedBy(Medida.entreSecciones),
                    ) {
                        LazyColumn(modifier = Modifier.weight(1f)) { resumenYGrafico() }
                        // Vuelta 118: la lista (la columna larga) es la que mueve el teclado.
                        LazyColumn(state = rememberListaConElTeclado(), modifier = Modifier.weight(1f)) { laLista() }
                    }
                } else {
                    LazyColumn(state = rememberListaConElTeclado(), modifier = Modifier.fillMaxSize(), contentPadding = relleno) {
                        resumenYGrafico()
                        item { Spacer(Modifier.height(Medida.entreSecciones)) }
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
    nombre: String,
    puedeRetroceder: Boolean,
    puedeAvanzar: Boolean,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
) {
    // Las flechas en su área de toque de 48; el dibujo queda alineado con el margen de la pantalla.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla - (Medida.objetivoTactil - Icono.l) / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (puedeRetroceder) {
            BotonDeIcono(Icons.Rounded.KeyboardArrowLeft, "Período anterior", onClick = onAnterior)
        } else {
            FlechaApagada(Icons.Rounded.KeyboardArrowLeft, "Período anterior: $NO_DISPONIBLE_EN_UN_RANGO")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            // Vuelta 75: un solo nombre del período, el de toda la app. Vuelta 140: o el del rango.
            Text(
                text = nombre,
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                modifier = Modifier.testTag(TAG_NOMBRE_DEL_PERIODO_DEL_ANALISIS),
            )
        }
        if (puedeAvanzar) {
            BotonDeIcono(Icons.Rounded.KeyboardArrowRight, "Período siguiente", onClick = onSiguiente)
        } else {
            FlechaApagada(
                Icons.Rounded.KeyboardArrowRight,
                if (puedeRetroceder) "Período siguiente: todavía no llega" else "Período siguiente: $NO_DISPONIBLE_EN_UN_RANGO",
            )
        }
    }
}

/** Lo que dice el lector de pantalla de una flecha apagada en un rango (vuelta 140). */
const val NO_DISPONIBLE_EN_UN_RANGO = "no disponible en un rango"

/** Una flecha que no se puede tocar, en el mismo lugar y del mismo tamaño que la que sí. */
@Composable
private fun FlechaApagada(icono: androidx.compose.ui.graphics.vector.ImageVector, descripcion: String) {
    // Se anuncia apagada (no es solo gris): el lector dice por qué no se puede tocar.
    Box(
        Modifier.size(Medida.objetivoTactil).semantics(mergeDescendants = true) {
            contentDescription = descripcion
            disabled()
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = Movi.colores.textoApagado,
            modifier = Modifier.size(Icono.l),
        )
    }
}

/**
 * **Una línea con su acción** encima de la lista (vuelta 140): «Este período va empezando… · Ver 3
 * meses», «No hay movimientos del 30 sep al 29 oct… · Subir extracto». El texto a lo ancho y la acción
 * debajo, a la derecha, en un botón fantasma: nunca el amarillo (el de la pantalla es otro).
 */
@Composable
private fun LineaConAccion(texto: String, accion: String?, onAccion: () -> Unit, tag: String) {
    Superficie(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text(texto, style = Movi.textos.apoyo, color = Movi.colores.texto)
        if (accion != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Boton(
                    texto = "$accion ›",
                    onClick = onAccion,
                    variante = VarianteDeBoton.Fantasma,
                    tamano = TamanoDeBoton.S,
                )
            }
        }
    }
    Spacer(Modifier.height(Medida.dentroDeSeccion))
}

// ── Cuánto llevas ───────────────────────────────────────────────────────────

/** «Llevas gastado» en el período en curso; «Gastaste» en uno que ya cerró. */
internal fun rotuloDelTotal(analisis: AnalisisDelPeriodo, hoy: LocalDate): String =
    if (hoy <= analisis.hasta) "Llevas gastado" else "Gastaste"

/**
 * La frase de la comparación: «$120.000 más que a esta altura del período pasado ($1,2M)», o por qué
 * no hay comparación.
 */
internal fun fraseDeLaComparacion(analisis: AnalisisDelPeriodo, rango: RangoDelAnalisis = RangoDelAnalisis.PERIODO): String {
    // Vuelta 140: en un rango, contra los mismos tantos meses de antes.
    val elPasado = if (analisis.esRango) "los ${rango.rotulo} anteriores" else "el período pasado"
    val sinComparar = if (analisis.esRango) "Todavía no están completos los ${rango.rotulo} anteriores para comparar."
    else "Todavía no hay un período pasado completo para comparar."
    if (analisis.anteriorConHueco && !analisis.esRango) return "El período pasado no tiene movimientos: no hay con qué comparar."
    val anterior = analisis.anteriorAMismaAltura ?: return sinComparar
    val flecha = contraElAnterior(analisis.total, anterior) ?: return sinComparar
    val cifra = formatMoneyCompact(anterior)
    val altura = if (analisis.esRango) "que a esta altura en $elPasado" else "que a esta altura del período pasado"
    return when (flecha.tendencia) {
        Tendencia.SUBE -> "${formatMoneyCompact(flecha.diferencia)} más $altura ($cifra)"
        Tendencia.BAJA -> "${formatMoneyCompact(flecha.diferencia)} menos $altura ($cifra)"
        Tendencia.IGUAL -> "Lo mismo $altura ($cifra)"
    }
}

/**
 * El renglón de un rango, en el lugar del desglose (vuelta 140): «58 gastos en 9 categorías». Cuenta
 * como la lista: un gasto que no te toca nada no es una vez tuya.
 */
internal fun textoDelRenglonDelRango(analisis: AnalisisDelPeriodo): String {
    val propios = analisis.gastos.filter { montoContado(it) > 0L }
    val categorias = propios.map { it.category }.distinct().size
    val gastos = if (propios.size == 1) "1 gasto" else "${propios.size} gastos"
    return "$gastos en ${if (categorias == 1) "1 categoría" else "$categorias categorías"}"
}

/**
 * **El total partido en día a día + fijos** (vuelta 60), o `null` si no se puede afirmar: solo en el
 * período en curso (el server manda el gasto del día a día de ese período y de ningún otro) y solo si
 * cuadra con el total (ver [desgloseDelGastado]).
 */
internal fun desgloseDelAnalisis(
    analisis: AnalisisDelPeriodo,
    gastoVariablePorDia: Map<String, Long>?,
    hoy: LocalDate,
): DesgloseDelGastado? {
    if (hoy < analisis.desde || hoy > analisis.hasta) return null
    return desgloseDelGastado(analisis.total, gastoVariablePorDia, analisis.desde.toString(), analisis.hasta.toString())
}

/**
 * «Día a día $2,1M + fijos pagados $2,8M»: el día a día es la cifra de la columna «Período» de «Cuánto
 * puedes gastar»; los fijos, lo que ya pagó facturas, cuotas y recurrentes del período («pagados»: en
 * «¿De dónde sale?», «Fijos del período» cuenta también los que faltan por pagar).
 */
internal fun textoDelDesglose(desglose: DesgloseDelGastado): String =
    "Día a día ${formatMoneyCompact(desglose.diaADia)} + fijos pagados ${formatMoneyCompact(desglose.fijos)}"

/** El renglón del desglose. */
const val TAG_DESGLOSE_DEL_ANALISIS = "desglose-del-analisis"

@Composable
private fun ResumenDelAnalisis(
    analisis: AnalisisDelPeriodo,
    hoy: LocalDate,
    desglose: DesgloseDelGastado?,
    renglonDelRango: String?,
    rango: RangoDelAnalisis,
) {
    // La cifra de esta pantalla: lo gastado en el período, la héroe (una por pantalla).
    Superficie(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = rotuloDelTotal(analisis, hoy),
            style = Movi.textos.apoyoMedio,
            color = Movi.colores.textoMedio,
        )
        Box(Modifier.padding(vertical = Espacio.xxs).testTag(TAG_TOTAL_DEL_ANALISIS)) {
            DsCifra(analisis.total, VarianteDeCifra.Heroe)
        }
        // Vuelta 60: de qué está hecho el total. «Cuánto puedes gastar» dice solo el día a día y lo
        // llamaba «gastaste»; acá se ve que el resto son los fijos.
        if (desglose != null) {
            Text(
                text = textoDelDesglose(desglose),
                style = Movi.textos.apoyo,
                color = Movi.colores.texto,
                modifier = Modifier.testTag(TAG_DESGLOSE_DEL_ANALISIS),
            )
            Spacer(Modifier.height(Espacio.xxs))
        } else if (renglonDelRango != null) {
            Text(
                text = renglonDelRango,
                style = Movi.textos.apoyo,
                color = Movi.colores.texto,
                modifier = Modifier.testTag(TAG_DESGLOSE_DEL_ANALISIS),
            )
            Spacer(Modifier.height(Espacio.xxs))
        }
        val tendencia = analisis.anteriorAMismaAltura?.let { contraElAnterior(analisis.total, it)?.tendencia }
        Text(
            text = fraseDeLaComparacion(analisis, rango),
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
internal fun leyendaDelTramo(tramo: TramoDelAnalisis, porSemana: Boolean, acumulado: Boolean, enUnRango: Boolean = false): String {
    val cuando = rotuloDelTramo(tramo, porSemana)
    if (tramo.porVenir) return "$cuando · todavía no llega"
    val este = if (acumulado) tramo.acumulado else tramo.gasto
    val antes = if (acumulado) tramo.acumuladoAnterior else tramo.anterior
    val loDeEste = if (acumulado) "llevabas ${formatCOP(este)}" else formatCOP(este)
    // Vuelta 140: en un rango la línea es el rango anterior; «antes» cabe en el renglón.
    val elPasado = if (enUnRango) "antes" else "período pasado"
    return if (antes != null) "$cuando · $loDeEste · $elPasado ${formatCOP(antes)}" else "$cuando · $loDeEste"
}

/** «Por día», «Por semana» o «Por mes»: cómo van las barras (vuelta 140: un año va por mes). */
internal fun textoDeLosTramos(analisis: AnalisisDelPeriodo): String = when {
    analisis.porMes -> "Por mes"
    analisis.porSemana -> "Por semana"
    else -> "Por día"
}

/** Lo que dice la leyenda sin nada elegido: cómo se usa el gráfico. */
internal fun leyendaSinElegir(analisis: AnalisisDelPeriodo): String {
    val cada = when {
        analisis.porMes -> "cada mes"
        analisis.porSemana -> "cada semana"
        else -> "cada día"
    }
    return when {
        analisis.conComparacion && analisis.esRango -> "La línea es el tiempo de antes. Desliza para ver $cada."
        analisis.conComparacion -> "La línea es el período pasado. Desliza para ver $cada."
        else -> "Desliza para ver $cada; toca una barra para ver sus movimientos."
    }
}

@Composable
private fun TarjetaDelGrafico(
    analisis: AnalisisDelPeriodo,
    rango: RangoDelAnalisis,
    acumulado: Boolean,
    onCambiarModo: (Boolean) -> Unit,
    onTocarTramo: (TramoDelAnalisis) -> Unit,
) {
    var elegido by remember(analisis) { mutableStateOf<Int?>(null) }
    Superficie(modifier = Modifier.fillMaxWidth()) {
        SelectorSegmentado(
            labels = listOf(textoDeLosTramos(analisis), "Acumulado"),
            selected = if (acumulado) 1 else 0,
            onSelect = { onCambiarModo(it == 1) },
        )
        Spacer(Modifier.height(Medida.dentroDeSeccion))
        // Un renglón de alto fijo: elegir una barra cambia el texto, nunca empuja el gráfico.
        Text(
            text = elegido?.let { analisis.tramos.getOrNull(it) }?.let { leyendaDelTramo(it, analisis.porSemana || analisis.porMes, acumulado, analisis.esRango) }
                ?: leyendaSinElegir(analisis),
            style = Movi.textos.apoyo,
            color = if (elegido != null) Movi.colores.texto else Movi.colores.textoMedio,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().testTag(TAG_LEYENDA_DEL_ANALISIS),
        )
        Spacer(Modifier.height(Espacio.xs))
        GraficoDelAnalisis(
            analisis = analisis,
            rango = rango,
            acumulado = acumulado,
            elegido = elegido,
            alElegir = { elegido = it },
            alTocar = { i -> analisis.tramos.getOrNull(i)?.takeUnless { it.porVenir }?.let(onTocarTramo) },
        )
        Spacer(Modifier.height(Espacio.xxs))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fechaCorta(analisis.desde), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Text(fechaCorta(analisis.hasta), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

@Composable
private fun GraficoDelAnalisis(
    analisis: AnalisisDelPeriodo,
    rango: RangoDelAnalisis,
    acumulado: Boolean,
    elegido: Int?,
    alElegir: (Int?) -> Unit,
    alTocar: (Int) -> Unit,
) {
    val barra = Movi.colores.neutro
    val barraElegida = Movi.colores.textoMedio
    val linea = Movi.colores.enlace
    val pista = Movi.colores.hilo
    val reducido = LocalMovimientoReducido.current
    // Las barras crecen al llegar y al cambiar de período o de modo, salvo con movimiento reducido.
    val entrada = remember(analisis.periodo, analisis.cuantosPeriodos, acumulado) { Animatable(if (reducido) 1f else 0f) }
    LaunchedEffect(analisis.periodo, analisis.cuantosPeriodos, acumulado) { entrada.animateTo(1f, Movimiento.alEntrar(reducido)) }

    val tramos = analisis.tramos
    val barras = tramos.map { if (it.porVenir) null else if (acumulado) it.acumulado else it.gasto }
    val anteriores = tramos.map { if (acumulado) it.acumuladoAnterior else it.anterior }
    val descripcion = descripcionDelGrafico(analisis, rango, acumulado)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTO_DEL_GRAFICO)
            .testTag(TAG_GRAFICA_DEL_ANALISIS)
            .semantics { contentDescription = descripcion }
            .graficaQueSeToca(cuantos = tramos.size, datos = analisis, alElegir = alElegir, alTocar = alTocar),
    ) {
        val maximo = (barras.filterNotNull() + anteriores.filterNotNull()).maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val arriba = Espacio.xs.toPx()
        val alto = size.height - arriba
        val franja = size.width / tramos.size
        val ancho = (franja * 0.62f).coerceAtLeast(1f)
        fun y(v: Long): Float = arriba + alto * (1f - v.toFloat() / (maximo * 1.08f))
        // La base: dónde está el cero.
        drawLine(pista, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = Medida.trazo.toPx())
        barras.forEachIndexed { i, v ->
            if (v == null || v <= 0L) return@forEachIndexed
            val tope = y(v)
            val altoDeLaBarra = (size.height - tope) * entrada.value
            drawRoundRect(
                color = if (i == elegido) barraElegida else barra,
                topLeft = Offset(franja * i + (franja - ancho) / 2, size.height - altoDeLaBarra),
                size = Size(ancho, altoDeLaBarra),
                cornerRadius = CornerRadius(minOf(ancho / 2, Espacio.xxs.toPx())),
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
                    width = Medida.trazoDeCarga.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                ),
            )
        }
        // La guía de lo elegido.
        elegido?.let { i ->
            val x = franja * i + franja / 2
            drawLine(barraElegida, Offset(x, 0f), Offset(x, size.height), strokeWidth = Medida.trazo.toPx())
        }
    }
}

/**
 * Lo que dice el lector de pantalla del gráfico: cómo van las barras, el total y, con comparación, contra
 * qué se compara —en un rango, los mismos tantos meses de antes, no «el período pasado» (vuelta 140)—.
 */
internal fun descripcionDelGrafico(analisis: AnalisisDelPeriodo, rango: RangoDelAnalisis, acumulado: Boolean): String =
    "Gráfico del gasto ${textoDeLosTramos(analisis).lowercase()}" +
        (if (acumulado) ", acumulado" else "") + ": ${formatMoneyCompact(analisis.total)} en ${if (analisis.esRango) "el rango" else "el período"}" +
        (analisis.anteriorAMismaAltura?.let {
            val pasado = if (analisis.esRango) "los ${rango.rotulo} anteriores" else "el período pasado"
            ", $pasado a esta altura ${formatMoneyCompact(it)}"
        } ?: "")

// ── La lista ────────────────────────────────────────────────────────────────

/** Qué dicen las flechas de la lista. */
internal fun pieDeLasFlechas(analisis: AnalisisDelPeriodo): String = when (val d = analisis.diasComparados) {
    0, 1 -> "Las flechas comparan con el primer día del período pasado."
    else -> "Las flechas comparan con los primeros $d días del período pasado."
}

/**
 * Una fila de la lista: una [Fila] del sistema con el avatar de lo que es (categoría, comercio o
 * cuenta), el nombre, «32 % · 4 movimientos» y, al final, lo gastado con la flecha contra el período
 * pasado. Tocarla abre esos movimientos.
 */
@Composable
private fun FilaDeLaLista(
    fila: FilaDelAnalisis,
    vista: VistaDelAnalisis,
    tipoDeCuenta: AccountType?,
    onClick: () -> Unit,
) {
    Fila(
        titulo = fila.nombre,
        subtitulo = "${(fila.fraccion * 100).toInt()} % · ${if (fila.veces == 1) "1 movimiento" else "${fila.veces} movimientos"}",
        modifier = Modifier.testTag(TAG_FILA_DEL_ANALISIS),
        inicio = {
            when (vista) {
                VistaDelAnalisis.CATEGORIA -> IconoDeCategoria(fila.nombre)
                VistaDelAnalisis.COMERCIO -> IconoDeMovimiento(fila.muestra)
                VistaDelAnalisis.CUENTA -> AvatarDeLaCuenta(nombre = fila.nombre, tipo = tipoDeCuenta ?: AccountType.CHECKING)
            }
        },
        final = {
            Column(horizontalAlignment = Alignment.End) {
                DsCifra(fila.gasto)
                fila.contraElAnterior?.let { flecha ->
                    val color = when (flecha.tendencia) {
                        Tendencia.SUBE -> Movi.colores.texto
                        Tendencia.BAJA -> Movi.colores.entra
                        Tendencia.IGUAL -> Movi.colores.textoMedio
                    }
                    val descripcion = descripcionDeLaFlecha(flecha)
                    Text(
                        text = textoDeLaFlecha(flecha),
                        style = Movi.textos.apoyo,
                        color = color,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier.semantics { contentDescription = descripcion },
                    )
                }
            }
        },
        onClick = onClick,
        onClickLabel = "Ver los movimientos",
    )
}

@Composable
private fun EsqueletoDelAnalisis() {
    Superficie(modifier = Modifier.fillMaxWidth()) {
        LineaEsqueleto(fraccionDelAncho = 0.3f, estilo = Movi.textos.apoyo)
        Spacer(Modifier.height(Espacio.xs))
        LineaEsqueleto(fraccionDelAncho = 0.55f, estilo = Movi.textos.cifra)
        Spacer(Modifier.height(Espacio.m))
        Spacer(Modifier.height(ALTO_DEL_GRAFICO))
    }
}

