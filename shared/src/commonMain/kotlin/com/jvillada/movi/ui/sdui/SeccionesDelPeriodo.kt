package com.jvillada.movi.ui.sdui

import com.jvillada.movi.ui.components.conCifrasOcultas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.shared.model.ContraElAnterior
import com.jvillada.movi.shared.model.Tendencia
import com.jvillada.movi.ui.dashboard.descripcionDeLaFlecha
import com.jvillada.movi.ui.dashboard.pieDeLaComparacion
import com.jvillada.movi.ui.dashboard.textoDeLaFlecha
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Anomalia
import com.jvillada.movi.shared.model.EvidenciaDeAnomalia
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.dashboard.AnomaliasDescartadasEnLaSesion
import com.jvillada.movi.ui.dashboard.anomaliasVisiblesDe
import com.jvillada.movi.ui.fecha.fechaEnPalabras
import com.jvillada.movi.ui.recurrentes.ActionChip
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvillada.movi.shared.model.ScreenSection
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.components.ChevronRight
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.formatMoneyCompact
import com.jvillada.movi.ui.dashboard.CategoriaDelPeriodo
import com.jvillada.movi.ui.dashboard.CosaParaRevisar
import com.jvillada.movi.ui.dashboard.DashboardData
import com.jvillada.movi.ui.dashboard.DestinoDeRevision
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.categoriasDelPeriodo
import com.jvillada.movi.ui.dashboard.checklistDelPeriodo
import com.jvillada.movi.ui.dashboard.cosasParaRevisarDe
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.lineaDeLoQueFalta
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.pieDeLoYaPagado
import com.jvillada.movi.ui.dashboard.rememberProgresoDeEntrada
import com.jvillada.movi.ui.recurrentes.CasillaDeChecklist
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.plan.SEGMENTO_PRESUPUESTOS
import com.jvillada.movi.ui.categorias.IconoDeCategoria
import com.jvillada.movi.ui.categorias.TamanoDeIconoDeCategoria
import com.jvillada.movi.ui.categorias.colorDeCategoria

/**
 * # Las secciones que convierten el Inicio en el resumen del período
 *
 * Lo que pintan sale entero de `ResumenDelPeriodo.kt`, que es puro y está probado. Acá solo hay
 * disposición y color: si una decisión sobre la plata se escribe en este archivo, está en el lugar
 * equivocado.
 */

// ── En qué se fue la plata ───────────────────────────────────────────────────

/** El tag del widget «En qué se va» del Inicio, que abre el análisis del período (ver `WidgetEnQueSeVa`). */
const val TAG_TARJETA_EN_QUE_SE_VA: String = "tarjeta-en-que-se-va"

/** Una categoría con su barra. `internal` porque el detalle de un período la reusa tal cual. */
@Composable
internal fun FilaDeCategoria(categoria: CategoriaDelPeriodo, entrada: Float) {
    // El color se decide una vez acá y no dentro del Canvas de la barra: una lambda de dibujo no
    // puede leer un CompositionLocal.
    //
    // Task 3 (Ola B): la barra pasa del `marca` único de siempre al color propio de la categoría
    // —el mismo que ya pinta su ícono— para que «Comida» se lea naranja acá igual que en
    // Movimientos. Sobrepasada sigue siendo `sale`: ese rojo es un estado de alerta, no la
    // identidad de la categoría, y no puede competir con ella. Los tonos de `CATEGORIAS_CLARAS`
    // ya están medidos a 3:1 contra `tarjeta` y `fondo` (`ContrasteDeLosTokensTest`); `hilo`, la
    // pista de la barra, es apenas un tono más oscuro que `fondo` en los dos temas, así que la
    // misma medición vale para la pista sin agregar una nueva.
    val colorDeLaBarra = if (categoria.superada) Movi.colores.sale else colorDeCategoria(categoria.nombre)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconoDeCategoria(categoria.nombre, tamano = TamanoDeIconoDeCategoria.Chico)
            Spacer(Modifier.size(Movi.espacios.minimo))
            Text(
                text = categoria.nombre,
                style = Movi.textos.cuerpo,
                color = Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(Movi.espacios.corto))
            Cifra(
                formatMoneyCompact(categoria.gastado),
                Movi.textos.monto,
                color = if (categoria.superada) Movi.colores.sale else Movi.colores.texto,
            )
        }
        Spacer(Modifier.height(Movi.espacios.minimo + 2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(Movi.formas.pleno))
                    .background(Movi.colores.hilo),
            ) {
                Box(
                    modifier = Modifier
                        // Un mínimo del 2 % para que una categoría chica se vea: una barra de cero
                        // píxeles dice «no gastaste acá», y el renglón está justamente porque sí.
                        .fillMaxWidth(categoria.fraccion.coerceIn(0.02f, 1f) * entrada.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(Movi.formas.pleno))
                        .background(colorDeLaBarra),
                )
            }
            Spacer(Modifier.size(Movi.espacios.corto))
            Text(
                text = "${(categoria.fraccion * 100).toInt()} %",
                style = Movi.textos.apoyo,
                color = Movi.colores.textoApagado,
            )
            categoria.contraElAnterior?.let { flecha ->
                Spacer(Modifier.size(Movi.espacios.corto))
                FlechaContraElAnterior(flecha)
            }
        }
    }
}

/** Lo que mide como mínimo la flecha: «↑ $160.000» a la escala de letra de la app. */
private val ANCHO_DE_LA_FLECHA = 84.dp

/** El tag de la flecha «vs. período pasado» de una fila, para las pruebas. */
const val TAG_FLECHA_CONTRA_EL_ANTERIOR: String = "flecha-contra-el-anterior"

/**
 * «↑ $120 mil» en rojo si se gastó más que a esta altura del período pasado, «↓ $45 mil» en verde si
 * menos, «= igual» en gris. Rojo y verde son las señales de plata de siempre (`sale` / `entra`): más
 * gasto es plata que sale. Para quien no ve el color, la frase entera va en la descripción.
 *
 * **No es un sparkline, a propósito.** El último que había (`SimpleSparkline`) dibujaba una curva
 * fija que no salía de ningún dato y se borró; además la comparación tiene dos puntos (el pasado y el
 * actual): una línea entre dos puntos es una flecha con más tinta.
 */
@Composable
private fun FlechaContraElAnterior(flecha: ContraElAnterior) {
    val color = when (flecha.tendencia) {
        // Ola 2a: gastar más que el período pasado no es una alerta (no hay presupuesto pasado ni
        // nada vencido): la flecha y la frase lo dicen, en el color del texto.
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
        modifier = Modifier
            // Un ancho mínimo, alineado a la derecha: sin él cada barra terminaba donde su flecha la
            // dejaba («= igual» es más corta que «↑ $160.000») y la columna se leía despareja (visto
            // en la web a 390).
            .widthIn(min = ANCHO_DE_LA_FLECHA)
            .testTag(TAG_FLECHA_CONTRA_EL_ANTERIOR)
            .semantics { contentDescription = descripcion },
    )
}

/**
 * **Lo que conviene mirar hoy.** Ocupa el lugar que tenía «Alertas» —mismo tipo de sección, para
 * que un APK viejo siga pintando algo— pero dice más: además de lo que está mal, propone qué
 * hacer, y cada fila lleva a la pantalla donde se hace.
 */
@Composable
internal fun ParaRevisarSection(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
) {
    // La misma cuenta que decide si esta sección se pinta (ver `visibleSections`): una sola
    // definición, o la pantalla y su regla de visibilidad terminan opinando distinto.
    val cosas = cosasParaRevisarDe(data)
    // Ola 4: lo que se sale de lo normal va en la misma tarjeta, debajo, con su evidencia.
    val anomalias = anomaliasVisiblesDe(data)
    if (cosas.isEmpty() && anomalias.isEmpty()) return
    val coroutine = rememberCoroutineScope()

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(title = section.title ?: "Para revisar", count = cosas.size + anomalias.size)
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        ) {
            cosas.forEachIndexed { i, cosa ->
                FilaParaRevisar(cosa) { onNavigate(pantallaDe(cosa.destino)) }
                if (i < cosas.lastIndex || anomalias.isNotEmpty()) Hairline()
            }
            anomalias.forEachIndexed { i, anomalia ->
                FilaDeAnomalia(anomalia) {
                    // «Está bien»: se va en el mismo toque; si el server no lo guarda, vuelve.
                    AnomaliasDescartadasEnLaSesion.descartar(anomalia.huella)
                    coroutine.launch {
                        runCatching { Repositories.wallets.descartarAnomalia(anomalia.huella) }
                            .onFailure { AnomaliasDescartadasEnLaSesion.deshacer(anomalia.huella) }
                    }
                }
                if (i < anomalias.lastIndex) Hairline()
            }
        }
    }
}

/** La línea compacta de «Para revisar» escondida. */
const val TAG_PARA_REVISAR_COMPACTO: String = "para-revisar-compacto"

/** «1 cosa para revisar» / «3 cosas para revisar». */
internal fun textoDeParaRevisarCompacto(cuantas: Int): String =
    if (cuantas == 1) "1 cosa para revisar" else "$cuantas cosas para revisar"

/**
 * **«Para revisar» escondida, con algo urgente: una sola línea** (Ola 3 · «Editar inicio»).
 *
 * El dueño puede esconder «Para revisar», pero lo urgente nunca desaparece del todo: el server deja
 * en su lugar una sección [com.jvillada.movi.shared.model.PARA_REVISAR_COMPACTO], y acá se pinta
 * «2 cosas para revisar ›» **solo si alguna es urgente** (`visibleSections` la descarta si no).
 * Cuenta lo mismo que contaría la sección entera —lo sugerido y lo que se sale de lo normal—, y
 * tocarla la abre ahí mismo, sin salir de Hoy: cada cosa lleva a un lugar distinto, y la línea no
 * puede adivinar cuál.
 */
@Composable
internal fun ParaRevisarCompactoSection(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
) {
    val cosas = cosasParaRevisarDe(data)
    if (cosas.none { it.urgente }) return
    var abierta by rememberSaveable { mutableStateOf(false) }
    if (abierta) {
        ParaRevisarSection(section.copy(type = "ALERTS"), data, onNavigate)
        return
    }
    val cuantas = cosas.size + anomaliasVisiblesDe(data).size
    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinCard(
            modifier = Modifier.fillMaxWidth().testTag(TAG_PARA_REVISAR_COMPACTO),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            onClick = { abierta = true },
            onClickLabel = "Ver lo que hay para revisar",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Movi.colores.aviso))
                Text(
                    text = textoDeParaRevisarCompacto(cuantas),
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.aviso,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ChevronRight()
            }
        }
    }
}

/** El tag de una fila de «lo que se sale de lo normal», para las pruebas. */
const val TAG_FILA_DE_ANOMALIA: String = "fila-de-anomalia"

/**
 * Un aviso de lo que se sale de lo normal: la noticia, de dónde sale y dos salidas — «Ver los
 * movimientos» despliega la evidencia ahí mismo (fecha, nombre, cuenta y monto de cada uno) y
 * «Está bien» lo descarta para siempre.
 */
@Composable
private fun FilaDeAnomalia(anomalia: Anomalia, onDescartar: () -> Unit) {
    var verEvidencia by rememberSaveable(anomalia.huella) { mutableStateOf(false) }
    val hoy = remember { epochMillisToAppDate(Clock.System.now().toEpochMilliseconds()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(TAG_FILA_DE_ANOMALIA),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(Movi.colores.aviso),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = conCifrasOcultas(anomalia.titulo), style = Movi.textos.cuerpo, color = Movi.colores.texto)
            Text(text = conCifrasOcultas(anomalia.detalle), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            if (verEvidencia) {
                Spacer(Modifier.height(Movi.espacios.corto))
                anomalia.evidencia.forEach { e ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            text = renglonDeEvidencia(e, hoy),
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatMoney(e.monto, e.moneda), style = Movi.textos.apoyo, color = Movi.colores.texto)
                    }
                }
            }
            Spacer(Modifier.height(Movi.espacios.corto))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(
                    label = if (verEvidencia) "Ocultar los movimientos" else "Ver los movimientos",
                    primary = false,
                ) { verEvidencia = !verEvidencia }
                ActionChip(label = "Está bien", primary = false, onClick = onDescartar)
            }
        }
    }
}

/** «28 de septiembre · RAPPI COLOMBIA · Bancolombia». */
internal fun renglonDeEvidencia(e: EvidenciaDeAnomalia, hoy: kotlinx.datetime.LocalDate): String =
    listOfNotNull(fechaEnPalabras(e.fecha, hoy), e.nombre, e.cuenta).joinToString(" · ")

@Composable
private fun FilaParaRevisar(cosa: CosaParaRevisar, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(if (cosa.urgente) Movi.colores.aviso else Movi.colores.marca),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conCifrasOcultas(cosa.texto),
                style = Movi.textos.cuerpo,
                color = if (cosa.urgente) Movi.colores.aviso else Movi.colores.texto,
            )
            Text(text = conCifrasOcultas(cosa.detalle), style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        }
    }
}

private fun pantallaDe(destino: DestinoDeRevision): Screen = when (destino) {
    DestinoDeRevision.MOVIMIENTOS -> Screen.Transactions()
    DestinoDeRevision.RECURRENTES -> Screen.Plan(SEGMENTO_PAGOS)
    DestinoDeRevision.PRESUPUESTOS -> Screen.Plan(SEGMENTO_PRESUPUESTOS)
    DestinoDeRevision.CREDITOS -> Screen.Credits
    DestinoDeRevision.POR_REVISAR -> Screen.PorRevisar
    DestinoDeRevision.SUSCRIPCIONES -> Screen.Plan(SEGMENTO_PAGOS)
    DestinoDeRevision.CUADRE -> Screen.CuadreDeSaldos
    DestinoDeRevision.CAPTURA -> Screen.CapturaDelBanco
    DestinoDeRevision.CUENTAS_DE_OTROS -> Screen.Destinos(sugeridos = true)
}
