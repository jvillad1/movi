package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.Cifra
import com.jvillada.movi.ui.components.Hairline
import com.jvillada.movi.ui.components.MinCard
import com.jvillada.movi.ui.components.MinCardVariant
import com.jvillada.movi.ui.components.MinSectionHeader
import com.jvillada.movi.ui.components.NoSePudoLeer
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.dashboard.EstadoDeLaFila
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import com.jvillada.movi.ui.dashboard.faltaPorCobrar
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.ingresosPendientes
import com.jvillada.movi.ui.dashboard.ingresosRecibidos
import com.jvillada.movi.ui.dashboard.lineaDeLoQueFalta
import com.jvillada.movi.ui.dashboard.pagosHechos
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.yaPagadoEnElPeriodo
import com.jvillada.movi.ui.dashboard.yaRecibidoEnElPeriodo

/**
 * # Los pagos del período: UNA lista, con cada pago fijo una sola vez
 *
 * El dueño, el 27-sep, mirando Plan: *«Siento que estas listas varias que tienes en Plan se pueden
 * ver en 1 sola con iconos o textos agrupando los items, esto está bastante inconsistente»*, y
 * *«Lo de ya ocurrieron genera una enorme confusión … que sea muy claro qué me falta por pagar y
 * qué ya pagué por cada período»*.
 *
 * ## Qué lo confundió, y por qué no era un error de datos
 *
 * Debajo del checklist había tres secciones más: «Próximos» (lo que urge), «Sin confirmar»
 * (ocurrencias abiertas que ya no urgían) y «Ya ocurrieron» (los sellos, con su «Deshacer»). Las
 * tres hablaban de las mismas reglas con otra forma, y la última mezclaba meses: en la pantalla del
 * período de OCTUBRE decía «Celular — Ya ocurrió en septiembre» al lado de «Ya ocurrió en octubre».
 * Leyó que Movi daba por pagado un Celular que todavía debía. Los datos estaban bien —septiembre sí
 * se pagó—, pero la pantalla ponía un hecho de otro período dentro de este.
 *
 * ## Cómo quedó
 *
 * Una sola tarjeta, con el resumen arriba (cuánto falta, cuánto ya pagaste) y estos grupos:
 *
 * - **Falta por pagar** — vence en el período y no tiene pago. Lo vencido primero.
 * - **Ya pagaste** — tiene pago en el período. Cada fila dice con qué se sabe
 *   ([origenDeLoOcurrido], la misma media frase de siempre).
 * - **Por cobrar** / **Ya recibiste** — los ingresos, si hay.
 * - **Del período de septiembre** — lo que un período ANTERIOR dejó sin confirmar (lo que era «Sin
 *   confirmar»). Aparte y al final, diciendo de qué período es: nunca mezclado con «Ya pagaste».
 *
 * Todo lo que ofrecían las secciones que se fueron sigue en la fila: «Sí, fue este» / «No fue
 * este» con su movimiento propuesto (y el aviso de monto distinto que antes solo decía
 * «Próximos»), «Anotar este pago», «Quitar la marca» de un sello a mano, y el toque sobre la fila
 * que abre la regla para editarla (o Créditos, si es una cuota o una tarjeta).
 *
 * ## La casilla sigue sin ser un control
 *
 * El ícono de la izquierda es un reflejo de lo que dice la evidencia; tocar la fila abre la REGLA,
 * no marca nada. El dueño lo pidió en la ola anterior —*«no me debería dejar hacer check sin que el
 * movimiento asociado exista»*— y sigue valiendo: ninguna acción de esta lista sella un período sin
 * un movimiento detrás. Ver [com.jvillada.movi.ui.dashboard.EstadoDeLaFila].
 *
 * ## Las mismas piezas en «Tus períodos»
 *
 * El detalle de un período pasado ([com.jvillada.movi.ui.periodos.DetalleDePeriodoScreen]) dibuja
 * sus pagos fijos con [GrupoDePagos] y [FilaDePagoDelPeriodo], y con los mismos títulos: «Falta por
 * pagar» / «Ya pagaste», o «No se pagó» en un período cerrado. Una tercera forma de decir lo mismo
 * era justo lo que había que sacar.
 */

/** El rótulo de la sección. Constante para que la pantalla y sus pruebas nombren lo mismo. */
const val TITULO_CHECKLIST_DEL_PERIODO = "Pagos del período"

/** Los títulos de los grupos: los comparten Plan y el detalle de un período. */
const val TITULO_FALTA_POR_PAGAR = "Falta por pagar"
const val TITULO_YA_PAGASTE = "Ya pagaste"
const val TITULO_POR_COBRAR = "Por cobrar"
const val TITULO_YA_RECIBISTE = "Ya recibiste"
/** En un período cerrado lo que falta ya no «falta»: no se pagó. */
const val TITULO_NO_SE_PAGO = "No se pagó"
const val TITULO_NO_LLEGO = "No llegó"

/**
 * **La lista del período, entera y de solo lectura.**
 *
 * @param checklist ya armado por `checklistDelPeriodo` — esta función no decide qué entra.
 * @param anteriores lo que un período anterior dejó abierto, ya armado por
 *   `pendientesDePeriodosAnteriores`. Va en su propio grupo, al final.
 * @param cargando todavía no contestaron los vencimientos: no se pinta nada. Una tarjeta vacía que
 *   diga «no hay pagos» mientras la lista viaja es una afirmación sin respaldo.
 * @param pudoLeer `false` cuando la lectura falló. Ahí se dice que no se pudo leer y se ofrece
 *   reintentar, en vez de mostrar una lista a medias que parecería completa.
 * @param marcando reglas con una escritura en vuelo: sus acciones no aceptan otro toque hasta que
 *   vuelva.
 * @param descartadas los «no fue este» de ESTA sesión de pantalla, para que la propuesta siguiente
 *   aparezca sin esperar el viaje de red (el rechazo igual se persiste, ver `rechazarOcurrencia`).
 *   Ver [claveDescartada]: la clave es (regla, movimiento).
 * @param onConfirmar «Sí, fue este»: sella el período **anclado a ese movimiento**.
 * @param onNoFueEste «No fue este»: guarda el rechazo y, si había un sello, lo borra.
 * @param onAnotarMovimiento «Anotar este pago»: abre la hoja de Agregar con lo que el
 *   recurrente ya sabe. Quien llama decide a dónde lleva — la cuota de un crédito se anota en
 *   Créditos, no como un gasto suelto.
 * @param onQuitarLaMarca la única salida de un sello viejo hecho a mano, sin movimiento detrás.
 * @param onAbrir tocar la fila: abrir la regla para editarla (lo que ofrecía «Próximos»). `null` la
 *   deja sin toque.
 * @param planesDeCuotas el plan de cada crédito, por id de regla (ver [planesDeLasCuotas]). Vacío
 *   deja las filas sin estimación.
 */
@Composable
fun SeccionChecklistDelPeriodo(
    checklist: List<PagoDelPeriodo>,
    cargando: Boolean,
    pudoLeer: Boolean,
    marcando: Set<String>,
    onConfirmar: (pago: PagoDelPeriodo, eventId: String) -> Unit,
    onNoFueEste: (pago: PagoDelPeriodo, eventId: String) -> Unit,
    onAnotarMovimiento: (pago: PagoDelPeriodo) -> Unit,
    onQuitarLaMarca: (pago: PagoDelPeriodo) -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
    anteriores: List<PagoDelPeriodo> = emptyList(),
    onAbrir: ((PagoDelPeriodo) -> Unit)? = null,
    planesDeCuotas: Map<String, PlanDelCredito> = emptyMap(),
    descartadas: Set<String> = emptySet(),
) {
    val acciones = AccionesDelChecklist(onConfirmar, onNoFueEste, onAnotarMovimiento, onQuitarLaMarca)

    Column(modifier = modifier) {
        MinSectionHeader(
            title = TITULO_CHECKLIST_DEL_PERIODO,
            // Cuántos pagos fijos tiene ESTE período — lo de períodos anteriores no cuenta acá.
            count = if (pudoLeer && !cargando && checklist.isNotEmpty()) checklist.size else null,
        )
        when {
            cargando -> Unit
            !pudoLeer -> NoSePudoLeer(
                texto = "No se pudieron leer los pagos de este período",
                onReintentar = onReintentar,
            )
            checklist.isEmpty() && anteriores.isEmpty() -> MinCard(
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
                padding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            ) {
                val filas = FilasDelChecklist(marcando, descartadas, acciones, planesDeCuotas, onAbrir)
                if (checklist.isNotEmpty()) {
                    ResumenDeLaLista(
                        izquierda = TITULO_FALTA_POR_PAGAR to faltaPorPagar(checklist),
                        derecha = TITULO_YA_PAGASTE to yaPagadoEnElPeriodo(checklist),
                    )
                    Spacer(Modifier.height(Movi.espacios.amplio))
                    GrupoDePagos(
                        titulo = TITULO_FALTA_POR_PAGAR,
                        icono = Icons.Rounded.Schedule,
                        colorDelIcono = Movi.colores.textoMedio,
                        total = faltaPorPagar(checklist),
                        // Sin nada pendiente el grupo no desaparece: dice que ya salió todo, que es
                        // la respuesta a la pregunta que el dueño vino a hacer.
                        vacio = lineaDeLoQueFalta(checklist),
                        filas = pagosPendientes(checklist),
                        // De dónde salen las estimaciones de las cuotas: una vez por grupo, debajo de
                        // las filas que las muestran, y solo si alguna la muestra.
                        pie = ESTIMADO_SOBRE_LA_DEUDA_DE_HOY.takeIf {
                            pagosPendientes(checklist).any { estimacionDeLaFila(it, planesDeCuotas) != null }
                        },
                    ) { filas.Fila(it) }
                    OtroGrupo(TITULO_YA_PAGASTE, Icons.Rounded.CheckCircle, Movi.colores.entra, yaPagadoEnElPeriodo(checklist), pagosHechos(checklist)) { filas.Fila(it) }
                    // Un sueldo no se paga: llega. Mismo criterio que el título de la propuesta
                    // («¿Ya te llegó?»).
                    OtroGrupo(TITULO_POR_COBRAR, Icons.Rounded.Schedule, Movi.colores.textoMedio, faltaPorCobrar(checklist), ingresosPendientes(checklist)) { filas.Fila(it) }
                    OtroGrupo(TITULO_YA_RECIBISTE, Icons.Rounded.CheckCircle, Movi.colores.entra, yaRecibidoEnElPeriodo(checklist), ingresosRecibidos(checklist)) { filas.Fila(it) }
                } else {
                    Text(
                        text = "Este período no tiene pagos anotados",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                    )
                }
                if (anteriores.isNotEmpty()) {
                    Spacer(Modifier.height(Movi.espacios.amplio))
                    Hairline()
                    Spacer(Modifier.height(Movi.espacios.amplio))
                    GrupoDePagos(
                        titulo = tituloDeLosAnteriores(anteriores),
                        icono = Icons.Rounded.History,
                        colorDelIcono = Movi.colores.aviso,
                        // Sin total: no es plata de este período, y sumarla al lado de las de
                        // arriba invitaría a leerla como parte de lo que falta.
                        total = null,
                        explicacion = EXPLICACION_DE_LOS_ANTERIORES,
                        filas = anteriores,
                    ) { filas.Fila(it) }
                }
            }
        }
    }
}

/**
 * «Del período de septiembre», o «De períodos anteriores» si lo que quedó abierto es de más de uno.
 * El mes sale de `periodoDelDueno` (el nombre del período del dueño, con su corte), no del
 * vencimiento: con corte 25, lo que venció el 28 de agosto es del período de septiembre.
 */
internal fun tituloDeLosAnteriores(anteriores: List<PagoDelPeriodo>): String {
    val meses = anteriores.map { nombreDelMes(it.periodoDelDueno ?: it.vence) }.distinct()
    val mes = meses.singleOrNull()
    return if (mes.isNullOrEmpty()) "De períodos anteriores" else "Del período de $mes"
}

/** Lo que dice debajo del título de los anteriores: por qué están acá y qué se espera. */
const val EXPLICACION_DE_LOS_ANTERIORES =
    "No son de este período: Movi todavía no sabe si los pagaste. Confírmalo o anota el pago."

/**
 * Lo que una fila del checklist necesita además de su pago, en un solo paquete: así [GrupoDePagos]
 * no repite seis parámetros en cada llamada.
 */
private class FilasDelChecklist(
    val marcando: Set<String>,
    val descartadas: Set<String>,
    val acciones: AccionesDelChecklist,
    val planesDeCuotas: Map<String, PlanDelCredito>,
    val onAbrir: ((PagoDelPeriodo) -> Unit)?,
) {
    @Composable
    fun Fila(pago: PagoDelPeriodo) {
        FilaDelChecklistCompleto(
            pago = pago,
            enVuelo = pago.ruleId in marcando,
            estimacion = estimacionDeLaFila(pago, planesDeCuotas),
            propuesta = propuestaDeLaFila(pago, descartadas),
            acciones = acciones,
            onAbrir = onAbrir?.let { abrir -> { abrir(pago) } },
        )
    }
}

/** Un grupo que solo aparece si tiene filas, precedido de su aire. */
@Composable
private fun OtroGrupo(
    titulo: String,
    icono: ImageVector,
    colorDelIcono: Color,
    total: Long,
    filas: List<PagoDelPeriodo>,
    fila: @Composable (PagoDelPeriodo) -> Unit,
) {
    if (filas.isEmpty()) return
    Spacer(Modifier.height(Movi.espacios.amplio))
    GrupoDePagos(titulo = titulo, icono = icono, colorDelIcono = colorDelIcono, total = total, filas = filas, fila = fila)
}

/**
 * **Lo que el dueño vino a preguntar, arriba de todo**: cuánto falta y cuánto ya pagó en el
 * período, en pesos. Las dos cifras son la suma de los grupos de abajo — ni más ni menos.
 */
@Composable
internal fun ResumenDeLaLista(izquierda: Pair<String, Long>, derecha: Pair<String, Long>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(izquierda.first, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Cifra(formatCOP(izquierda.second), Movi.textos.titulo, color = Movi.colores.texto)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(derecha.first, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Cifra(formatCOP(derecha.second), Movi.textos.titulo, color = Movi.colores.texto)
        }
    }
}

/**
 * **Un grupo de la lista**: ícono, título y total, y debajo sus filas separadas por un hilo.
 *
 * Genérico en la fila a propósito: lo usan Plan (con [PagoDelPeriodo] y sus acciones) y el detalle
 * de un período (con `PagoFijoDelPeriodo`, de solo lectura). Los dos tienen que verse igual.
 *
 * @param total la suma de las filas, en pesos; `null` o cero no se pinta.
 * @param vacio lo que se dice si no hay filas; `null` esconde el grupo entero.
 * @param explicacion una línea debajo del título, para un grupo que necesita decir por qué está.
 * @param pie una línea debajo de las filas (de dónde salen las cifras estimadas), o `null`.
 */
@Composable
internal fun <T> GrupoDePagos(
    titulo: String,
    icono: ImageVector,
    colorDelIcono: Color,
    total: Long?,
    filas: List<T>,
    vacio: String? = null,
    explicacion: String? = null,
    pie: String? = null,
    fila: @Composable (T) -> Unit,
) {
    if (filas.isEmpty() && vacio == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Movi.espacios.corto),
    ) {
        Icon(icono, contentDescription = null, tint = colorDelIcono, modifier = Modifier.size(16.dp))
        Text(
            text = if (filas.isEmpty()) titulo else "$titulo · ${filas.size}",
            style = Movi.textos.cuerpo,
            fontWeight = FontWeight.SemiBold,
            color = Movi.colores.texto,
            modifier = Modifier.weight(1f),
        )
        if (total != null && total > 0) {
            Cifra(formatCOP(total), Movi.textos.monto, color = Movi.colores.texto)
        }
    }
    if (explicacion != null) {
        Spacer(Modifier.height(2.dp))
        Text(explicacion, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, lineHeight = 16.sp)
    }
    if (filas.isEmpty()) {
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(text = vacio.orEmpty(), style = Movi.textos.cuerpo, color = Movi.colores.textoMedio)
        return
    }
    filas.forEachIndexed { i, item ->
        fila(item)
        if (i < filas.lastIndex) Hairline(insetStart = 32.dp)
    }
    if (pie != null) {
        Spacer(Modifier.height(Movi.espacios.corto))
        Text(text = pie, style = Movi.textos.apoyo, color = Movi.colores.textoApagado, lineHeight = 16.sp)
    }
}

/** Lo que el ícono de una fila dice de ella. Tres estados en Plan; el cuarto es de un período cerrado. */
enum class EstadoVisualDelPago { FALTA, VENCIDO, PAGADO, NO_SE_PAGO }

/** El estado visual de una fila de Plan: pagado, vencido sin pago, o todavía no. */
internal fun estadoVisualDe(pago: PagoDelPeriodo): EstadoVisualDelPago = when {
    pago.pagado -> EstadoVisualDelPago.PAGADO
    pago.vencido -> EstadoVisualDelPago.VENCIDO
    else -> EstadoVisualDelPago.FALTA
}

/**
 * **Una fila de pago, dibujada**: ícono de estado, nombre, fecha, monto a la derecha y una línea
 * chica de evidencia. [debajo] es lo que la fila ofrece (las acciones), si ofrece algo.
 *
 * El ícono no es un control. Si [onAbrir] no es `null`, tocar el renglón abre la regla —lo que hacía
 * «Próximos»—, con [etiquetaDeAbrir] para que un lector de pantalla diga qué pasa.
 */
@Composable
internal fun FilaDePagoDelPeriodo(
    estado: EstadoVisualDelPago,
    esIngreso: Boolean,
    nombre: String,
    fecha: String,
    monto: String,
    colorDelMonto: Color,
    evidencia: String?,
    montoChico: Boolean = false,
    onAbrir: (() -> Unit)? = null,
    etiquetaDeAbrir: String? = null,
    debajo: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onAbrir != null) Modifier.clickable(onClickLabel = etiquetaDeAbrir, onClick = onAbrir)
                    else Modifier,
                ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
        ) {
            IconoDeEstadoDelPago(estado, esIngreso)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nombre,
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    // Lo pagado baja de tono en vez de tacharse: un texto tachado en una lista de
                    // plata se lee como «anulado», que en Movi significa otra cosa.
                    color = if (estado == EstadoVisualDelPago.PAGADO) Movi.colores.textoMedio else Movi.colores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fecha.isNotEmpty()) {
                    Text(
                        text = fecha,
                        style = Movi.textos.apoyo,
                        color = when (estado) {
                            EstadoVisualDelPago.VENCIDO, EstadoVisualDelPago.NO_SE_PAGO -> Movi.colores.sale
                            else -> Movi.colores.textoApagado
                        },
                        lineHeight = 16.sp,
                    )
                }
                if (evidencia != null) {
                    Text(
                        text = evidencia,
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        lineHeight = 16.sp,
                    )
                }
            }
            Cifra(
                monto,
                if (montoChico) Movi.textos.apoyo else Movi.textos.monto,
                color = colorDelMonto,
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 32.dp), content = debajo)
    }
}

/**
 * El ícono de estado: círculo vacío = falta, check = pagado, alerta en rojo = vencido (o, en un
 * período cerrado, no se pagó). Con descripción: para un lector de pantalla el color no existe.
 */
@Composable
private fun IconoDeEstadoDelPago(estado: EstadoVisualDelPago, esIngreso: Boolean) {
    val (icono, color, descripcion) = when (estado) {
        EstadoVisualDelPago.FALTA -> Triple(Icons.Rounded.RadioButtonUnchecked, Movi.colores.textoApagado, if (esIngreso) "Por cobrar" else "Falta")
        EstadoVisualDelPago.VENCIDO -> Triple(Icons.Rounded.ErrorOutline, Movi.colores.sale, "Vencido")
        EstadoVisualDelPago.PAGADO -> Triple(Icons.Rounded.CheckCircle, Movi.colores.entra, if (esIngreso) "Recibido" else "Pagado")
        EstadoVisualDelPago.NO_SE_PAGO -> Triple(Icons.Rounded.HighlightOff, Movi.colores.sale, if (esIngreso) "No llegó" else "No se pagó")
    }
    Icon(icono, contentDescription = descripcion, tint = color, modifier = Modifier.padding(top = 1.dp).size(20.dp))
}

/**
 * Las cuatro cosas que una fila puede pedirle a la pantalla, en un solo paquete.
 *
 * Viajan juntas porque viajan siempre juntas: la sección no elige cuál ofrecer —lo elige el estado
 * de cada fila— así que pasarlas sueltas por tres niveles de composable era repetir cuatro
 * parámetros en cada firma y abrir la puerta a que un grupo se quedara con tres.
 */
internal data class AccionesDelChecklist(
    val onConfirmar: (pago: PagoDelPeriodo, eventId: String) -> Unit,
    val onNoFueEste: (pago: PagoDelPeriodo, eventId: String) -> Unit,
    val onAnotarMovimiento: (pago: PagoDelPeriodo) -> Unit,
    val onQuitarLaMarca: (pago: PagoDelPeriodo) -> Unit,
)

/**
 * **La propuesta que toca mostrar en esta fila**, o `null` si no queda ninguna.
 *
 * La fila ya trae sus candidatos ([PagoDelPeriodo.candidatos]). [descartadas] es la capa optimista
 * de «no fue este» —el rechazo de verdad lo guarda el server— y por eso la clave es (regla,
 * movimiento): decir que no en «Agua» no puede quitarle su candidato bueno a «Gas».
 *
 * Si todas quedaron descartadas, la fila cae al estado «sin movimiento» y ofrece anotarlo. Es lo
 * correcto: el dueño acaba de decir que ninguno de los que Movi propone es este.
 */
internal fun propuestaDeLaFila(pago: PagoDelPeriodo, descartadas: Set<String>): FinancialEvent? =
    pago.candidatos.firstOrNull { claveDescartada(pago.ruleId, it.id) !in descartadas }

/** Qué dice el toque sobre una fila: una cuota o una tarjeta se gestionan en Créditos. */
internal fun etiquetaDeAbrir(ruleId: String): String =
    if (ruleId.startsWith(CREDIT_RULE_PREFIX) || ruleId.startsWith(CARD_RULE_PREFIX)) "Ver en Créditos"
    else "Editar el pago fijo"

/**
 * Una fila de Plan: [FilaDePagoDelPeriodo] con lo que [PagoDelPeriodo] sabe, y debajo lo único que
 * esa fila puede ofrecer sin mentir.
 *
 * @param estimacion el reparto que Movi estima para esta cuota (ver [estimacionDeLaFila]). Va
 *   debajo, no al lado del monto: el monto es la cuota registrada y la estimación es otra cosa.
 */
@Composable
private fun FilaDelChecklistCompleto(
    pago: PagoDelPeriodo,
    enVuelo: Boolean,
    acciones: AccionesDelChecklist,
    propuesta: FinancialEvent? = null,
    estimacion: String? = null,
    onAbrir: (() -> Unit)? = null,
) {
    val estado = estadoVisualDe(pago)
    FilaDePagoDelPeriodo(
        estado = estado,
        esIngreso = pago.esIngreso,
        nombre = pago.nombre,
        fecha = if (enVuelo) "Guardando…" else fechaDeLaFila(pago),
        monto = textoDelMontoDelChecklist(pago),
        montoChico = pago.montoEsSaldo && !pago.pagado,
        colorDelMonto = when {
            pago.pagado -> Movi.colores.textoMedio
            pago.montoEsSaldo -> Movi.colores.textoApagado
            pago.esIngreso -> Movi.colores.entra
            pago.vencido -> Movi.colores.sale
            else -> Movi.colores.texto
        },
        evidencia = evidenciaDeLaFila(pago),
        onAbrir = onAbrir,
        etiquetaDeAbrir = etiquetaDeAbrir(pago.ruleId),
    ) {
        // **Lo que Movi estima que trae esta cuota.** No va con el color de vencido: es una
        // cuenta sobre el crédito, no un aviso sobre la fecha.
        if (estimacion != null && !enVuelo) {
            Spacer(Modifier.height(4.dp))
            Text(text = estimacion, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, lineHeight = 16.sp)
        }
        LoQueOfreceLaFila(pago, propuesta, enVuelo, acciones)
    }
}

/**
 * **Lo único que esta fila puede ofrecer**, según lo que la evidencia diga de ella. Sangrado para
 * que se lea como algo que cuelga de la fila y no como una fila más.
 */
@Composable
private fun LoQueOfreceLaFila(
    pago: PagoDelPeriodo,
    propuesta: FinancialEvent?,
    enVuelo: Boolean,
    acciones: AccionesDelChecklist,
) {
    // Una fila sin nada que ofrecer no deja ni un hueco: la mayoría son filas que todavía no vencen
    // o cuotas que el movimiento ya probó.
    val hayQueOfrecer = when (pago.estado) {
        EstadoDeLaFila.AUN_NO_VENCE -> false
        // La cuota de un crédito y el pago de una tarjeta no discuten: ahí el movimiento MOVIÓ la
        // deuda, y lo único que revierte eso es borrarlo.
        //
        // **`automatica` va primero**: lo que Movi empareja solo viaja también con `derivado = true`
        // (no hay sello que borrar), así que mirar solo `derivado` le quitaba el «No fue este» justo
        // a la fila que Movi dedujo y puede haber deducido mal. Y un sello del dueño con movimiento
        // (`eventId`, no derivado) también lo ofrece: es el «Deshacer» de lo que era «Ya
        // ocurrieron», que además guarda el rechazo para que no se vuelva a proponer.
        EstadoDeLaFila.LISTO -> pago.automatica || (pago.eventId != null && !pago.derivado)
        else -> true
    }
    if (!hayQueOfrecer) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        when (pago.estado) {
            EstadoDeLaFila.LISTO -> AccionesDeLaFila(
                acciones = listOf(
                    AccionDeOcurrencia(ETIQUETA_NO_FUE_ESTE, primary = false) {
                        pago.eventId?.let { acciones.onNoFueEste(pago, it) }
                    },
                ),
                enVuelo = enVuelo,
            )
            EstadoDeLaFila.MARCADA_A_MANO -> AccionesDeLaFila(
                acciones = listOf(
                    AccionDeOcurrencia(ETIQUETA_QUITAR_LA_MARCA, primary = false) {
                        acciones.onQuitarLaMarca(pago)
                    },
                ),
                enVuelo = enVuelo,
            )
            // Con candidatos se pregunta; sin ninguno (o con todos rechazados en esta sesión) la
            // fila cae a la misma salida que una que nunca tuvo: anotar el movimiento que falta.
            EstadoDeLaFila.CON_DUDAS -> if (propuesta != null) {
                Text(
                    text = tituloPropuesta(
                        if (pago.esIngreso) TransactionType.INCOME else TransactionType.EXPENSE,
                        pago.periodoDelDueno ?: pago.periodoDelSello.orEmpty(),
                    ),
                    style = Movi.textos.apoyo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(4.dp))
                PropuestaDeMovimiento(
                    propuesta = propuesta,
                    // Se mudó de «Próximos» con la propuesta: sin él, un movimiento de otro monto
                    // se confirmaba a ciegas. En una tarjeta (saldo) o en otra moneda no se compara.
                    aviso = avisoDeMontoDistinto(pago.monto, pago.montoEsSaldo, propuesta),
                    enVuelo = enVuelo,
                    onConfirmar = { acciones.onConfirmar(pago, propuesta.id) },
                    onDescartar = { acciones.onNoFueEste(pago, propuesta.id) },
                )
            } else {
                SinMovimiento(pago, enVuelo, acciones)
            }
            EstadoDeLaFila.SIN_MOVIMIENTO -> SinMovimiento(pago, enVuelo, acciones)
            EstadoDeLaFila.AUN_NO_VENCE -> Unit
        }
    }
}

/** Lo dice y ofrece la única salida honesta: que el movimiento exista. */
@Composable
private fun SinMovimiento(pago: PagoDelPeriodo, enVuelo: Boolean, acciones: AccionesDelChecklist) {
    Text(
        text = if (pago.esIngreso) TEXTO_SIN_MOVIMIENTO_DE_INGRESO else TEXTO_SIN_MOVIMIENTO,
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(8.dp))
    AccionesDeLaFila(
        acciones = listOf(
            AccionDeOcurrencia(etiquetaDeAnotar(pago.esIngreso), primary = true) { acciones.onAnotarMovimiento(pago) },
        ),
        enVuelo = enVuelo,
    )
}

/**
 * Lo que dice una fila abierta a la que Movi no le encontró **nada**.
 *
 * Nombra a Movi a propósito, en vez de un «sin movimiento» pelado: la fila está afirmando que
 * BUSCÓ y no encontró, que es distinto de no haber mirado.
 */
const val TEXTO_SIN_MOVIMIENTO = "Movi no encontró el movimiento de este pago."

/** Lo mismo para una fila de ingreso: un sueldo no se paga, llega. */
const val TEXTO_SIN_MOVIMIENTO_DE_INGRESO = "Movi no encontró el movimiento de este ingreso."

/**
 * La casilla del checklist de la tarjeta del Inicio. **No es un control: es un reflejo.** La lista de
 * Plan usa el ícono de estado de [FilaDePagoDelPeriodo]; esta queda para el Inicio, que esta ola no
 * toca.
 *
 * [apagada] baja el borde a un hilo: un borde de control sobre algo que no se puede tocar es una
 * promesa que no se cumple.
 */
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
 * **La fecha de una fila, en palabras**: «vence el 22 de octubre», «vence mañana», «venció hace 2
 * días», «pagado el 27 de septiembre».
 *
 * En lo pagado va el día en que salió la plata cuando se sabe ([PagoDelPeriodo.pagadoEl]); si no
 * —un sello del dueño, que se fecha cuando tocó el botón y no cuando pagó— va el vencimiento, dicho
 * como tal. Nunca «ya ocurrió en <mes>»: todo lo que está en la lista es de este período, y el mes
 * solo servía para confundir.
 */
internal fun fechaDeLaFila(pago: PagoDelPeriodo): String {
    val vence = fechaLegibleDelChecklist(pago.vence)
    if (pago.pagado) {
        val pagadoEl = pago.pagadoEl?.let { fechaLegibleDelChecklist(it) }.orEmpty()
        return when {
            pagadoEl.isNotEmpty() -> (if (pago.esIngreso) "recibido el " else "pagado el ") + pagadoEl
            vence.isEmpty() -> ""
            pago.esIngreso -> "se esperaba el $vence"
            else -> "vencía el $vence"
        }
    }
    val dias = pago.diasParaVencer
    return if (pago.esIngreso) when {
        dias < -1 -> "debía llegar hace ${-dias} días"
        dias == -1 -> "debía llegar ayer"
        dias == 0 -> "llega hoy"
        dias == 1 -> "llega mañana"
        vence.isEmpty() -> "llega en $dias días"
        else -> "llega el $vence"
    } else when {
        dias < -1 -> "venció hace ${-dias} días"
        dias == -1 -> "venció ayer"
        dias == 0 -> "vence hoy"
        dias == 1 -> "vence mañana"
        vence.isEmpty() -> "vence en $dias días"
        else -> "vence el $vence"
    }
}

/**
 * **Con qué se sabe que está pagado**, o `null` en una fila que todavía no lo está (ahí lo que la
 * fila dice lo dicen sus acciones: la propuesta, o que Movi no encontró el movimiento).
 *
 * La media frase es [origenDeLoOcurrido], la de siempre: lo emparejó Movi, lo prueba el pago que
 * bajó la deuda, lo confirmaste con un movimiento, o es un sello viejo sin nada detrás. Cuatro
 * certezas distintas que no deberían sonar igual.
 */
internal fun evidenciaDeLaFila(pago: PagoDelPeriodo): String? {
    if (!pago.pagado) return null
    return origenDeLoOcurrido(
        automatica = pago.automatica,
        derivada = pago.derivado,
        hayMovimiento = pago.eventId != null,
        // `montoPagado` ya viene filtrado por moneda: si el movimiento fue en otra, llega `null` y
        // la frase se queda sin cifra en vez de decir una que no es.
        monto = pago.montoPagado,
        moneda = pago.moneda,
    ).replaceFirstChar { it.uppercase() }
}

/** «12 de septiembre», o cadena vacía si la fecha viene con una forma que no se entiende. */
internal fun fechaLegibleDelChecklist(vence: String): String {
    val partes = vence.split("-")
    if (partes.size != 3) return ""
    val dia = partes[2].toIntOrNull() ?: return ""
    val mes = nombreDelMes(vence)
    return if (mes.isEmpty()) "" else "$dia de $mes"
}

/**
 * El monto de una fila, **en su moneda**. En lo ya pagado, lo que de verdad salió cuando se sabe:
 * el total de «Ya pagaste» se arma con eso, y la fila tiene que sumar lo mismo que el encabezado.
 *
 * Acá no se abrevia: es la lista donde el dueño compara lo que debe con lo que tiene. Un saldo de
 * tarjeta en dólares se dice con su prefijo.
 */
internal fun textoDelMontoDelChecklist(pago: PagoDelPeriodo): String {
    val monto = if (pago.pagado) pago.montoPagado ?: pago.monto else pago.monto
    return if (pago.moneda != "COP") formatMoney(monto, pago.moneda) else formatCOP(monto)
}
