package com.jvillada.movi.ui.sdui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * Cuántas categorías sueltas muestra el Inicio antes de agrupar el resto: cuatro. Con más, la tarjeta
 * empuja todo lo demás fuera de la pantalla; «Ver todas» las despliega ahí mismo.
 */
internal const val CATEGORIAS_EN_EL_INICIO = 4

/**
 * **¿En qué se va?** Las categorías del período, con una barra que dice cuánto pesa cada una.
 *
 * **La barra es sobre el gasto total del período, no sobre la categoría mayor.** Es la diferencia
 * entre «Vivienda es el 70 % de lo que gastaste» y «Vivienda es la más grande de la lista», y solo
 * la primera sirve para decidir algo.
 *
 * Generación 8: las primeras [CATEGORIAS_EN_EL_INICIO] y el resto agrupado; «Ver todas» las
 * despliega en el lugar (sin salir del Inicio) y «Ver menos» las vuelve a juntar. Con pocas
 * categorías no hay nada que desplegar y la acción lleva a Movimientos, como antes. Las barras crecen
 * al cargar, una sola vez (ver `rememberProgresoDeEntrada`).
 */
@Composable
internal fun GastoPorCategoriaSection(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
) {
    var todas by rememberSaveable { mutableStateOf(false) }
    val gasto = data.spentByCategory.orEmpty()
    val presupuestos = data.budgets.orEmpty()
    val categorias = categoriasDelPeriodo(
        gastoPorCategoria = gasto,
        presupuestos = presupuestos,
        cuantas = if (todas) Int.MAX_VALUE else CATEGORIAS_EN_EL_INICIO,
    )
    // Sin gasto no se pinta nada: una sección vacía que dice «$0» ocupa el mismo lugar que una con
    // información y no dice nada. Mismo criterio que ALERTS.
    if (categorias.isEmpty()) return
    val hayMas = gasto.count { it.value > 0 } > CATEGORIAS_EN_EL_INICIO + 1
    val entrada = rememberProgresoDeEntrada("categorias")

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(
            title = section.title ?: "En qué se va",
            action = when {
                !hayMas -> "Ver movimientos"
                todas -> "Ver menos"
                else -> "Ver todas"
            },
            onAction = { if (hayMas) todas = !todas else onNavigate(Screen.Transactions()) },
        )
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(Movi.espacios.margen),
        ) {
            categorias.forEachIndexed { i, categoria ->
                FilaDeCategoria(categoria, entrada)
                if (i < categorias.lastIndex) Spacer(Modifier.height(Movi.espacios.medio))
            }
        }
    }
}

@Composable
private fun FilaDeCategoria(categoria: CategoriaDelPeriodo, entrada: Float) {
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
        }
    }
}

// ── El checklist del período ─────────────────────────────────────────────────

/**
 * **El rótulo de esta sección se cablea acá, no se lee de la fila del SDUI.**
 *
 * Decía «Pagos del período» sobre una lista que solo tiene los que FALTAN, y el dueño lo dijo con
 * todas las letras: *«solo muestra los faltantes, no muestra todos; debería indicar que esos son
 * los faltantes nada más»*. El rótulo es la mitad del arreglo.
 *
 * Y va cableado por lo mismo que `HERO_BALANCE_TITLE` (ver `DashboardDefaults.kt`): la fila de
 * `screen_definitions` llega a todos los clientes en el instante del deploy, pero el renderer viaja
 * en el binario. Cambiar el título en el seed obligaría a subir la generación del layout y a que el
 * rótulo nuevo aterrice sobre APKs viejos; cambiarlo acá lo ata al mismo binario que decide qué
 * filas se pintan, que es de lo que el rótulo habla.
 */
private const val TITULO_FALTA_POR_PAGAR = "Falta por pagar"

/**
 * **Lo que falta pagar de este período, y de cuántos pagos es eso.**
 *
 * La tarjeta lista SOLO lo pendiente —no cambió: lo pagado del período nunca llegaba acá, por el
 * rodado del vencimiento que `checklistDelPeriodo` ahora corrige— y lo que cambió es que lo dice.
 * El rótulo, la línea de avance («te faltan 3 de 7 pagos de este período») y el pie («ya salieron
 * 4…») son las tres piezas de la misma frase.
 *
 * No pasa a listarlo todo porque el Inicio es un resumen y la lista entera tiene su lugar: «Ver
 * todos» aterriza en el checklist completo del chip «Recurrentes», que es donde además se tilda.
 *
 * Los INGRESOS quedan fuera de esta tarjeta y de sus cuentas: un sueldo pendiente no es algo que
 * falte pagar. Se ven —y se tildan— en el checklist completo, bajo «Por cobrar».
 */
@Composable
internal fun ChecklistDelPeriodoSection(
    section: ScreenSection,
    data: DashboardData,
    onNavigate: (Screen) -> Unit,
) {
    val periodo = data.periodoActual ?: return
    val checklist = checklistDelPeriodo(
        upcoming = data.upcoming.orEmpty(),
        ocurrencias = data.ocurrencias.orEmpty(),
        periodo = periodo,
        settings = data.ajustesDePeriodo,
    )
    if (checklist.isEmpty()) return

    val pendientes = pagosPendientes(checklist)
    val falta = faltaPorPagar(checklist)
    val pie = pieDeLoYaPagado(checklist)

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(
            title = TITULO_FALTA_POR_PAGAR,
            action = "Ver todos",
            // Ola C: «todos» son los del checklist de Plan · Pagos del mes.
            onAction = { onNavigate(Screen.Plan(SEGMENTO_PAGOS)) },
        )
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lineaDeLoQueFalta(checklist),
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.texto,
                    modifier = Modifier.weight(1f),
                )
                if (falta > 0) {
                    Text(
                        text = "Falta ${formatMoneyCompact(falta)}",
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                    )
                }
            }
            if (pendientes.isNotEmpty()) {
                Spacer(Modifier.height(Movi.espacios.medio))
                pendientes.forEachIndexed { i, pago ->
                    FilaDelChecklist(pago) { onNavigate(Screen.Plan(SEGMENTO_PAGOS)) }
                    if (i < pendientes.lastIndex) Hairline()
                }
            }
            if (pie != null) {
                Spacer(Modifier.height(Movi.espacios.corto))
                Text(text = pie, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
            }
        }
    }
}

@Composable
private fun FilaDelChecklist(pago: PagoDelPeriodo, onClick: () -> Unit) {
    val colorDelMonto = when {
        pago.pagado -> Movi.colores.textoApagado
        pago.vencido -> Movi.colores.sale
        else -> Movi.colores.texto
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        CasillaDeChecklist(marcada = pago.pagado)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pago.nombre,
                style = Movi.textos.cuerpo,
                // Lo pagado baja de tono en vez de tacharse: un texto tachado en una lista de
                // plata se lee como «anulado», que en Movi significa otra cosa.
                color = if (pago.pagado) Movi.colores.textoMedio else Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = textoDelEstado(pago),
                style = Movi.textos.apoyo,
                color = if (pago.vencido) Movi.colores.sale else Movi.colores.textoApagado,
            )
        }
        Cifra(
            // El monto de una tarjeta es su saldo, no lo que va a salir: se dice más chico y en
            // gris, igual que en «Próximos pagos» (ver RecurringRule.montoEsSaldo).
            //
            // `formatMoneyCompact` abrevia en millones, que es una escala de pesos: aplicada a
            // dólares diría «$1.200» por una deuda de US$1.200. Una moneda que no es la de la casa
            // se dice entera y con su prefijo — son cifras cortas de por sí.
            when {
                pago.montoEsSaldo && pago.moneda != "COP" -> formatMoney(pago.monto, pago.moneda)
                pago.montoEsSaldo -> formatMoneyCompact(pago.monto)
                else -> formatCOP(pago.monto)
            },
            if (pago.montoEsSaldo) Movi.textos.apoyo else Movi.textos.monto,
            color = if (pago.montoEsSaldo) Movi.colores.textoApagado else colorDelMonto,
        )
    }
}

/** «Pagado», «Vence en 3 días», «Venció hace 2 días». */
private fun textoDelEstado(pago: PagoDelPeriodo): String = when {
    pago.pagado -> "Pagado"
    pago.diasParaVencer < 0 -> {
        val dias = -pago.diasParaVencer
        if (dias == 1) "Venció ayer" else "Venció hace $dias días"
    }
    pago.diasParaVencer == 0 -> "Vence hoy"
    pago.diasParaVencer == 1 -> "Vence mañana"
    else -> "Vence en ${pago.diasParaVencer} días"
}

// La casilla se mudó a `ui/recurrentes/ChecklistDelPeriodo.kt` (CasillaDeChecklist): la pintan esta
// tarjeta y el checklist completo, y dos copias de un mismo control terminan divergiendo — el
// argumento de siempre en esta parte del código.

// ── Para revisar ─────────────────────────────────────────────────────────────

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
    if (cosas.isEmpty()) return

    Column(modifier = Modifier.padding(horizontal = Movi.espacios.amplio)) {
        MinSectionHeader(title = section.title ?: "Para revisar", count = cosas.size)
        MinCard(
            modifier = Modifier.fillMaxWidth(),
            variant = MinCardVariant.Elevated,
            padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        ) {
            cosas.forEachIndexed { i, cosa ->
                FilaParaRevisar(cosa) { onNavigate(pantallaDe(cosa.destino)) }
                if (i < cosas.lastIndex) Hairline()
            }
        }
    }
}

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
                text = cosa.texto,
                style = Movi.textos.cuerpo,
                color = if (cosa.urgente) Movi.colores.aviso else Movi.colores.texto,
            )
            Text(text = cosa.detalle, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
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
}
