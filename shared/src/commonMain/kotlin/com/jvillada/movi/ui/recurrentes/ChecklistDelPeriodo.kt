package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.avanceDelChecklist
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.ingresosPendientes
import com.jvillada.movi.ui.dashboard.lineaDeLoQueFalta
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.yaMarcados

/**
 * # El checklist del período: todo lo que se paga este mes, con lo hecho tildado
 *
 * El dueño, mirando el Inicio: *«En Inicio veo donde dice Pagos del período y solo muestra los
 * faltantes, no muestra todos … Cuando entro a verlos todos me lleva a Recurrentes, pero quiero ver
 * los recurrentes también tipo checklist del mes, con valor y fecha para ser realizado»*.
 *
 * ## Por qué vive acá y no en una pantalla propia
 *
 * «Ver todos» ya aterriza en Movimientos con el chip «Recurrentes» puesto: el problema no era el
 * destino, era que ahí no estaba el checklist. Una pantalla nueva habría dejado el mismo chip
 * mostrando las mismas obligaciones repartidas en tres tarjetas, y un cuarto lugar donde marcar un
 * pago — con su propia copia de «Ya lo pagué», que es exactamente la clase de duplicado que
 * `ProximosPagosSection.kt` documenta haber evitado a propósito.
 *
 * Así que el checklist es lo PRIMERO del chip, y debajo siguen las tres secciones que ya estaban,
 * que contestan otra cosa: «Próximos» propone qué movimiento fue cada pago («Sí, fue este»),
 * «Sin confirmar» junta lo que dejó de urgir y «Ya ocurrieron» explica de dónde salió cada sello.
 * El checklist no propone ni explica nada: enumera el período, dice cuánto y cuándo, y se tilda.
 *
 * Y marca por el MISMO camino: `onMarcar`/`onDeshacer` son los `marcarOcurrio`/`deshacerOcurrio` de
 * la pantalla, con el período que la fila trae del server (ver [PagoDelPeriodo.periodoDelSello]).
 *
 * ## Y desde esta ola: cuánto hay que pagar, no solo cuánto se pactó
 *
 * La fila de una cuota mostraba el monto de la regla —la cuota registrada— y nada más. El dueño
 * paga su crédito del carro **de memoria** porque el banco no le publica un valor a pagar, y en
 * septiembre giró $77.040 de más sin poder saberlo. Debajo de cada cuota pendiente va ahora el
 * reparto que Movi estima para este período, rotulado como estimación. Ver [estimacionDeLaFila].
 */

/** El rótulo de la sección. Constante para que la pantalla y sus pruebas nombren lo mismo. */
const val TITULO_CHECKLIST_DEL_PERIODO = "Checklist del período"

/**
 * **Lo que se paga en este período, entero y tildable.**
 *
 * @param checklist ya armado por `checklistDelPeriodo` — esta función no decide qué entra.
 * @param cargando todavía no contestaron los vencimientos: no se pinta nada. Una tarjeta vacía que
 *   diga «no hay pagos» mientras la lista viaja es una afirmación sin respaldo.
 * @param pudoLeer `false` cuando la lectura falló. Ahí se dice que no se pudo leer y se ofrece
 *   reintentar, en vez de mostrar un checklist a medias que parecería completo.
 * @param marcando reglas con una marca en vuelo: su casilla no acepta otro toque hasta que vuelva.
 * @param planesDeCuotas el plan de cada crédito, por id de regla (ver [planesDeLasCuotas]). Vacío
 *   —porque la lectura no llegó, o falló— deja las filas como estaban: sin estimación. Una cuota
 *   sin su reparto se ve igual que antes de esta ola; una cuota con un reparto que no se pudo
 *   calcular sería una cifra sin respaldo.
 */
@Composable
fun SeccionChecklistDelPeriodo(
    checklist: List<PagoDelPeriodo>,
    cargando: Boolean,
    pudoLeer: Boolean,
    marcando: Set<String>,
    onMarcar: (ruleId: String, period: String) -> Unit,
    onDeshacer: (ruleId: String, period: String) -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
    planesDeCuotas: Map<String, PlanDelCredito> = emptyMap(),
) {
    val pendientes = pagosPendientes(checklist)
    val porCobrar = ingresosPendientes(checklist)
    val marcados = yaMarcados(checklist)
    val (pagados, total) = avanceDelChecklist(checklist)

    Column(modifier = modifier) {
        MinSectionHeader(
            title = TITULO_CHECKLIST_DEL_PERIODO,
            count = if (pudoLeer && !cargando && total > 0) total else null,
        )
        when {
            cargando -> Unit
            !pudoLeer -> NoSePudoLeer(
                texto = "No se pudo leer el checklist de este período",
                onReintentar = onReintentar,
            )
            checklist.isEmpty() -> MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "Este período no tiene pagos anotados",
                    style = Movi.textos.cuerpo,
                    color = Movi.colores.textoMedio,
                )
            }
            else -> MinCard(
                modifier = Modifier.fillMaxWidth(),
                variant = MinCardVariant.Elevated,
                padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            ) {
                GrupoDelChecklist(
                    titulo = "Falta por pagar",
                    // El total solo acompaña al grupo que lo explica: es la suma de ESTAS filas.
                    total = faltaPorPagar(checklist),
                    filas = pendientes,
                    vacio = lineaDeLoQueFalta(checklist),
                    marcando = marcando,
                    onMarcar = onMarcar,
                    onDeshacer = onDeshacer,
                    planesDeCuotas = planesDeCuotas,
                )
                if (porCobrar.isNotEmpty()) {
                    Spacer(Modifier.height(Movi.espacios.medio))
                    GrupoDelChecklist(
                        // Un sueldo no se paga: llega. Mismo criterio que «Ya me llegó» en la
                        // propuesta de ocurrencia — decirle «¿ya lo pagaste?» a su nómina es la
                        // clase de detalle que hace sentir que la app no entiende lo que uno anotó.
                        titulo = "Por cobrar",
                        total = null,
                        filas = porCobrar,
                        vacio = null,
                        marcando = marcando,
                        onMarcar = onMarcar,
                        onDeshacer = onDeshacer,
                    )
                }
                if (marcados.isNotEmpty()) {
                    Spacer(Modifier.height(Movi.espacios.medio))
                    GrupoDelChecklist(
                        titulo = "Ya marcados · $pagados de $total",
                        total = null,
                        filas = marcados,
                        vacio = null,
                        marcando = marcando,
                        onMarcar = onMarcar,
                        onDeshacer = onDeshacer,
                    )
                }
            }
        }
    }
}

@Composable
private fun GrupoDelChecklist(
    titulo: String,
    total: Long?,
    filas: List<PagoDelPeriodo>,
    vacio: String?,
    marcando: Set<String>,
    onMarcar: (ruleId: String, period: String) -> Unit,
    onDeshacer: (ruleId: String, period: String) -> Unit,
    planesDeCuotas: Map<String, PlanDelCredito> = emptyMap(),
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = titulo,
            style = Movi.textos.apoyo,
            fontWeight = FontWeight.Medium,
            color = Movi.colores.textoMedio,
            modifier = Modifier.weight(1f),
        )
        if (total != null && total > 0) {
            Cifra(formatCOP(total), 13.5f, color = Movi.colores.texto)
        }
    }
    if (filas.isEmpty()) {
        if (vacio != null) {
            Spacer(Modifier.height(Movi.espacios.corto))
            Text(text = vacio, style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        }
        return
    }
    filas.forEachIndexed { i, pago ->
        FilaDelChecklistCompleto(
            pago = pago,
            enVuelo = pago.ruleId in marcando,
            estimacion = estimacionDeLaFila(pago, planesDeCuotas),
            onTildar = {
                val periodo = pago.periodoDelSello ?: return@FilaDelChecklistCompleto
                if (pago.pagado) onDeshacer(pago.ruleId, periodo) else onMarcar(pago.ruleId, periodo)
            },
        )
        if (i < filas.lastIndex) Hairline()
    }
    // **De dónde salen esas cifras, dicho debajo de las filas que las muestran.** Una sola vez por
    // grupo: el rótulo «Movi estima» ya viaja pegado a cada cifra (ver [ETIQUETA_ESTIMADO]), y lo
    // que esto agrega es el cómo, que repetido en seis filas se vuelve decoración. Y solo si hay
    // alguna estimación: un pie que explica algo que no está en pantalla es ruido.
    if (filas.any { estimacionDeLaFila(it, planesDeCuotas) != null }) {
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(
            text = ESTIMADO_SOBRE_LA_DEUDA_DE_HOY,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
            lineHeight = 16.sp,
        )
    }
}

/**
 * Una fila del checklist: la casilla, el nombre, **cuándo vence y cuánto**, en su moneda.
 *
 * La casilla es un control de verdad —tildar sella el período y destildar lo borra, por el mismo
 * endpoint que el «Ya lo pagué» de siempre— salvo en las dos filas donde no puede serlo
 * ([PagoDelPeriodo.seMarca]): la que todavía no vence, que el server rechaza, y la que quedó pagada
 * por un movimiento, que solo se revierte borrándolo. Esas se dibujan sin toque y lo dicen.
 *
 * @param estimacion el reparto que Movi estima para esta cuota, o `null` si no hay ninguno que dar
 *   (ver [estimacionDeLaFila]). Va **debajo** de la fecha y no al lado del monto: el monto es la
 *   cuota registrada —un dato del contrato— y la estimación es otra cosa; pegarlas en el mismo
 *   renglón las dejaría pareciendo dos versiones de la misma cifra.
 */
@Composable
private fun FilaDelChecklistCompleto(
    pago: PagoDelPeriodo,
    enVuelo: Boolean,
    onTildar: () -> Unit,
    estimacion: String? = null,
) {
    val fila = Modifier
        .fillMaxWidth()
        .then(if (pago.seMarca) Modifier.clickable { if (!enVuelo) onTildar() } else Modifier)
        .padding(vertical = 12.dp)
    Row(
        modifier = fila,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
    ) {
        CasillaDeChecklist(marcada = pago.pagado, apagada = !pago.seMarca)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pago.nombre,
                style = Movi.textos.cuerpo,
                fontWeight = FontWeight.Medium,
                // Lo pagado baja de tono en vez de tacharse: un texto tachado en una lista de plata
                // se lee como «anulado», que en Movi significa otra cosa.
                color = if (pago.pagado) Movi.colores.textoMedio else Movi.colores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (enVuelo) "Guardando…" else subtituloDeLaFila(pago),
                style = Movi.textos.apoyo,
                color = if (pago.vencido) Movi.colores.sale else Movi.colores.textoApagado,
            )
            // **Lo que Movi estima que trae esta cuota.** No va con el color de vencido: es una
            // cuenta sobre el crédito, no un aviso sobre la fecha.
            if (estimacion != null && !enVuelo) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = estimacion,
                    style = Movi.textos.apoyo,
                    color = Movi.colores.textoMedio,
                    lineHeight = 16.sp,
                )
            }
        }
        Cifra(
            textoDelMontoDelChecklist(pago),
            if (pago.montoEsSaldo) 12.5f else 13.5f,
            color = when {
                pago.montoEsSaldo -> Movi.colores.textoApagado
                pago.pagado -> Movi.colores.textoApagado
                pago.esIngreso -> Movi.colores.entra
                pago.vencido -> Movi.colores.sale
                else -> Movi.colores.texto
            },
        )
    }
}

/** La casilla del checklist. [apagada] = esta fila no se puede tildar (ver [PagoDelPeriodo.seMarca]). */
@Composable
fun CasillaDeChecklist(marcada: Boolean, apagada: Boolean = false) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(Movi.formas.minima))
            .background(if (marcada) Movi.colores.entra.copy(alpha = 0.18f) else Movi.colores.tarjeta)
            .then(
                if (marcada) Modifier
                else Modifier.border(
                    1.dp,
                    if (apagada) Movi.colores.hilo else Movi.colores.borde,
                    RoundedCornerShape(Movi.formas.minima),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) {
            // Ícono y no el carácter «✓»: la fuente del canvas de la web no lo trae y salía como un
            // cuadradito vacío (ver la casilla de ReminderWarning, mismo motivo).
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Pagado",
                tint = Movi.colores.entra,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * **La fecha y el estado, juntos**: «12 de septiembre · vence en 2 días».
 *
 * La fecha va siempre, también en lo ya marcado: el dueño pidió *«valor y fecha»*, y un «Pagado»
 * pelado no deja comprobar que se tildó el mes que uno creía.
 */
internal fun subtituloDeLaFila(pago: PagoDelPeriodo): String {
    val fecha = fechaLegibleDelChecklist(pago.vence)
    val estado = estadoDelChecklist(pago)
    return if (fecha.isEmpty()) estado else "$fecha · $estado"
}

/** «12 de septiembre», o cadena vacía si la fecha viene con una forma que no se entiende. */
internal fun fechaLegibleDelChecklist(vence: String): String {
    val partes = vence.split("-")
    if (partes.size != 3) return ""
    val dia = partes[2].toIntOrNull() ?: return ""
    val mes = nombreDelMes(vence)
    return if (mes.isEmpty()) "" else "$dia de $mes"
}

/** «pagado», «recibido», «vence hoy», «venció hace 2 días». En minúscula: va después de la fecha. */
internal fun estadoDelChecklist(pago: PagoDelPeriodo): String = when {
    pago.pagado && pago.esIngreso -> "recibido"
    pago.pagado -> "pagado"
    pago.diasParaVencer < 0 -> {
        val dias = -pago.diasParaVencer
        if (dias == 1) "venció ayer" else "venció hace $dias días"
    }
    pago.diasParaVencer == 0 -> "vence hoy"
    pago.diasParaVencer == 1 -> "vence mañana"
    else -> "vence en ${pago.diasParaVencer} días"
}

/**
 * El monto de una fila, **en su moneda**.
 *
 * Acá no se abrevia: el checklist es la lista donde el dueño compara lo que debe con lo que tiene,
 * y «$1,2M» no sirve para eso. Un saldo de tarjeta en dólares se dice con su prefijo, porque
 * `formatCOP` sobre US$1.200 escribiría «$1.200» — la misma cifra, la moneda equivocada.
 */
internal fun textoDelMontoDelChecklist(pago: PagoDelPeriodo): String =
    if (pago.moneda != "COP") formatMoney(pago.monto, pago.moneda) else formatCOP(pago.monto)
