package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.jvillada.movi.ui.dashboard.avanceDelChecklist
import com.jvillada.movi.ui.dashboard.faltaPorPagar
import com.jvillada.movi.ui.dashboard.ingresosPendientes
import com.jvillada.movi.ui.dashboard.lineaDeLoQueFalta
import com.jvillada.movi.ui.dashboard.pagosPendientes
import com.jvillada.movi.ui.dashboard.yaMarcados
import com.jvillada.movi.ui.dashboard.tituloDeLoYaOcurrido

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
 * que contestan otra cosa: «Próximos» ordena por urgencia, «Sin confirmar» junta lo que dejó de
 * urgir y «Ya ocurrieron» lista los sellos. El checklist enumera el período entero, dice cuánto y
 * cuándo, y **refleja** lo que la evidencia dice de cada fila.
 *
 * ## La casilla dejó de ser un control, y esa es la ola
 *
 * Hasta acá tildaba: `onMarcar(ruleId, period, null)` sellaba el período **sin ninguna evidencia**.
 * El dueño lo cortó de raíz: *«ahí tengo la lista tipo checklist pero no me debería dejar hacer
 * check sin que el movimiento asociado exista, y esto debería ser read only, que sea inteligente
 * tipo, se detecta este movimiento asociado a uno de estos items de recurrentes y que lo vaya
 * marcando como listo o que pregunte si ya sucedió si la app tiene dudas o encuentra ambigüedad»*.
 *
 * Y tenía razón por donde más duele: un tilde sin movimiento apaga el aviso de una deuda que puede
 * seguir viva, y después no queda nada en pantalla que permita notarlo. Ahora el estado de cada
 * fila lo decide el movimiento (ver [com.jvillada.movi.ui.dashboard.EstadoDeLaFila]) y lo único
 * que la fila ofrece son acciones sobre esa evidencia: confirmar cuál fue, decir que no fue esa, o
 * **anotar el movimiento que falta** — que es la salida que el dueño eligió para lo que pagó en
 * efectivo, desde una cuenta que Movi no lleva, o que el banco nunca avisó. Pidió explícitamente
 * que NO quedara un «marcar sin movimiento» como último recurso.
 *
 * El «Ya lo pagué» de «Próximos» se fue por lo mismo y en la misma ola: dejarlo vivo ahí habría
 * movido el agujero un toque más allá en vez de cerrarlo.
 *
 * ## Una sola pieza que dibuja una propuesta
 *
 * La pregunta de «¿fue este?» ya existía en `ProximosPagosSection.kt`. Traerla acá se hizo
 * **reusando** [PropuestaDeMovimiento] y [AccionesDeLaFila], no copiándolas: el archivo de al lado
 * documenta cómo termina copiar un renderer de plata (la decisión «saldo o cuota» faltaba en uno
 * de cuatro), y acá lo copiado serían los botones que sellan un período.
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
 * **Lo que se paga en este período, entero y de solo lectura.**
 *
 * @param checklist ya armado por `checklistDelPeriodo` — esta función no decide qué entra.
 * @param cargando todavía no contestaron los vencimientos: no se pinta nada. Una tarjeta vacía que
 *   diga «no hay pagos» mientras la lista viaja es una afirmación sin respaldo.
 * @param pudoLeer `false` cuando la lectura falló. Ahí se dice que no se pudo leer y se ofrece
 *   reintentar, en vez de mostrar un checklist a medias que parecería completo.
 * @param marcando reglas con una escritura en vuelo: sus acciones no aceptan otro toque hasta que
 *   vuelva.
 * @param descartadas los «no fue este» de ESTA sesión de pantalla, para que la propuesta siguiente
 *   aparezca sin esperar el viaje de red (el rechazo igual se persiste, ver `rechazarOcurrencia`).
 *   Ver [claveDescartada]: la clave es (regla, movimiento).
 * @param onConfirmar «Sí, fue este»: sella el período **anclado a ese movimiento**.
 * @param onNoFueEste «No fue este»: guarda el rechazo y, si había un sello, lo borra.
 * @param onAnotarMovimiento «Anotar el movimiento»: abre la hoja de Agregar con lo que el
 *   recurrente ya sabe. Quien llama decide a dónde lleva — la cuota de un crédito se anota en
 *   Créditos, no como un gasto suelto.
 * @param onQuitarLaMarca la única salida de un sello viejo hecho a mano, sin movimiento detrás.
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
    onConfirmar: (pago: PagoDelPeriodo, eventId: String) -> Unit,
    onNoFueEste: (pago: PagoDelPeriodo, eventId: String) -> Unit,
    onAnotarMovimiento: (pago: PagoDelPeriodo) -> Unit,
    onQuitarLaMarca: (pago: PagoDelPeriodo) -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
    planesDeCuotas: Map<String, PlanDelCredito> = emptyMap(),
    descartadas: Set<String> = emptySet(),
) {
    val pendientes = pagosPendientes(checklist)
    val porCobrar = ingresosPendientes(checklist)
    val marcados = yaMarcados(checklist)
    val (_, total) = avanceDelChecklist(checklist)
    val acciones = AccionesDelChecklist(onConfirmar, onNoFueEste, onAnotarMovimiento, onQuitarLaMarca)

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
                    descartadas = descartadas,
                    acciones = acciones,
                    planesDeCuotas = planesDeCuotas,
                )
                if (porCobrar.isNotEmpty()) {
                    Spacer(Modifier.height(Movi.espacios.medio))
                    GrupoDelChecklist(
                        // Un sueldo no se paga: llega. Mismo criterio que el título de la propuesta
                        // («¿Ya te llegó?») — decirle «¿ya lo pagaste?» a su nómina es la clase de
                        // detalle que hace sentir que la app no entiende lo que uno anotó.
                        titulo = "Por cobrar",
                        total = null,
                        filas = porCobrar,
                        vacio = null,
                        marcando = marcando,
                        descartadas = descartadas,
                        acciones = acciones,
                    )
                }
                if (marcados.isNotEmpty()) {
                    Spacer(Modifier.height(Movi.espacios.medio))
                    GrupoDelChecklist(
                        // No "Ya marcados": la mayoría de estas filas las empareja Movi sola
                        // (ver `PagoDelPeriodo.automatica`), no el dueño. Y no "Ya salieron":
                        // el grupo también lista los ingresos recibidos — ver
                        // [tituloDeLoYaOcurrido].
                        titulo = tituloDeLoYaOcurrido(checklist),
                        total = null,
                        filas = marcados,
                        vacio = null,
                        marcando = marcando,
                        descartadas = descartadas,
                        acciones = acciones,
                    )
                }
            }
        }
    }
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

@Composable
private fun GrupoDelChecklist(
    titulo: String,
    total: Long?,
    filas: List<PagoDelPeriodo>,
    vacio: String?,
    marcando: Set<String>,
    descartadas: Set<String>,
    acciones: AccionesDelChecklist,
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
            Cifra(formatCOP(total), Movi.textos.monto, color = Movi.colores.texto)
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
            propuesta = propuestaDeLaFila(pago, descartadas),
            acciones = acciones,
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
 * **La propuesta que toca mostrar en esta fila**, o `null` si no queda ninguna.
 *
 * Gemela de [propuestaActual], que hace lo mismo sobre un `OccurrenceState`; acá la fila ya trae
 * sus candidatos ([PagoDelPeriodo.candidatos]) y no hay estado que buscar. [descartadas] es la capa
 * optimista de «no fue este» —el rechazo de verdad lo guarda el server— y por eso la clave es la
 * misma, (regla, movimiento): decir que no en «Agua» no puede quitarle su candidato bueno a «Gas».
 *
 * Si todas quedaron descartadas, la fila cae al estado «sin movimiento» y ofrece anotarlo. Es lo
 * correcto: el dueño acaba de decir que ninguno de los que Movi propone es este.
 */
internal fun propuestaDeLaFila(pago: PagoDelPeriodo, descartadas: Set<String>): FinancialEvent? =
    pago.candidatos.firstOrNull { claveDescartada(pago.ruleId, it.id) !in descartadas }

/**
 * Una fila del checklist: la casilla, el nombre, **cuándo vence y cuánto**, en su moneda — y
 * debajo, lo único que esa fila puede ofrecer sin mentir.
 *
 * **La casilla no se toca.** No es un control disfrazado de reflejo ni al revés: es un reflejo, y
 * la fila entera dejó de ser clickeable con él. Lo que se puede hacer está escrito con todas las
 * letras abajo (ver [AccionesDeLaFila]), donde se puede leer antes de tocar.
 *
 * @param propuesta el mejor candidato que queda, ya resuelto por [propuestaDeLaFila]. `null` en
 *   toda fila que no esté preguntando — y también en una que preguntaba y se quedó sin candidatos
 *   porque el dueño los rechazó a todos.
 * @param estimacion el reparto que Movi estima para esta cuota, o `null` si no hay ninguno que dar
 *   (ver [estimacionDeLaFila]). Va **debajo** de la fecha y no al lado del monto: el monto es la
 *   cuota registrada —un dato del contrato— y la estimación es otra cosa; pegarlas en el mismo
 *   renglón las dejaría pareciendo dos versiones de la misma cifra.
 */
@Composable
private fun FilaDelChecklistCompleto(
    pago: PagoDelPeriodo,
    enVuelo: Boolean,
    acciones: AccionesDelChecklist,
    propuesta: FinancialEvent? = null,
    estimacion: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Movi.espacios.medio),
        ) {
            CasillaDeChecklist(marcada = pago.pagado, apagada = !pago.pagado)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pago.nombre,
                    style = Movi.textos.cuerpo,
                    fontWeight = FontWeight.Medium,
                    // Lo pagado baja de tono en vez de tacharse: un texto tachado en una lista de
                    // plata se lee como «anulado», que en Movi significa otra cosa.
                    color = if (pago.pagado) Movi.colores.textoMedio else Movi.colores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (enVuelo) "Guardando…" else subtituloDeLaFila(pago),
                    style = Movi.textos.apoyo,
                    color = if (pago.vencido) Movi.colores.sale else Movi.colores.textoApagado,
                    lineHeight = 16.sp,
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
                if (pago.montoEsSaldo) Movi.textos.apoyo else Movi.textos.monto,
                color = when {
                    pago.montoEsSaldo -> Movi.colores.textoApagado
                    pago.pagado -> Movi.colores.textoApagado
                    pago.esIngreso -> Movi.colores.entra
                    pago.vencido -> Movi.colores.sale
                    else -> Movi.colores.texto
                },
            )
        }
        LoQueOfreceLaFila(pago, propuesta, enVuelo, acciones)
    }
}

/**
 * **Lo único que esta fila puede ofrecer**, según lo que la evidencia diga de ella.
 *
 * Sangrado para que se lea como algo que cuelga de la fila y no como una fila más — el mismo
 * recurso que usa la propuesta de «Próximos».
 */
@Composable
private fun LoQueOfreceLaFila(
    pago: PagoDelPeriodo,
    propuesta: FinancialEvent?,
    enVuelo: Boolean,
    acciones: AccionesDelChecklist,
) {
    // Una fila sin nada que ofrecer no deja ni un hueco: la mayoría del checklist son filas que
    // todavía no vencen o cuotas que el movimiento ya probó.
    val hayQueOfrecer = when (pago.estado) {
        EstadoDeLaFila.AUN_NO_VENCE -> false
        // La cuota de un crédito y el pago de una tarjeta no discuten: ahí el movimiento MOVIÓ la
        // deuda, y lo único que revierte eso es borrarlo. Mismo criterio que el «Deshacer» que esa
        // fila tampoco tiene (ver [sePuedeDeshacer]).
        //
        // **`automatica` va primero**, y no es un detalle: lo que Movi empareja solo viaja también
        // con `derivado = true` (no hay sello que borrar, ver `OccurrenceState.automatica`), así
        // que mirar solo `derivado` le quitaba el «No fue este» justo a la única fila que lo
        // necesita — la que Movi dedujo y puede haber deducido mal.
        EstadoDeLaFila.LISTO -> pago.automatica || (pago.eventId != null && !pago.derivado)
        else -> true
    }
    if (!hayQueOfrecer) return
    Column(modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 8.dp)) {
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
                        pago.periodoDelSello.orEmpty(),
                    ),
                    style = Movi.textos.apoyo,
                    fontWeight = FontWeight.Medium,
                    color = Movi.colores.texto,
                )
                Spacer(Modifier.height(4.dp))
                PropuestaDeMovimiento(
                    propuesta = propuesta,
                    // El aviso de «no es el monto que anotaste» vive en «Próximos», que tiene la
                    // regla entera a mano para saber si comparar significa algo (en una tarjeta el
                    // monto es el SALDO). Acá la fila no la tiene — y tampoco hace falta: el monto
                    // esperado está a la derecha del nombre, dos renglones arriba, y la propuesta
                    // muestra el suyo, que es exactamente la comparación que el aviso escribía.
                    aviso = null,
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
        text = TEXTO_SIN_MOVIMIENTO,
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(8.dp))
    AccionesDeLaFila(
        acciones = listOf(
            AccionDeOcurrencia(ETIQUETA_ANOTAR, primary = true) { acciones.onAnotarMovimiento(pago) },
        ),
        enVuelo = enVuelo,
    )
}

/**
 * Lo que dice una fila abierta a la que Movi no le encontró **nada**.
 *
 * Nombra a Movi a propósito, en vez de un «sin movimiento» pelado: la fila está afirmando que
 * BUSCÓ y no encontró, que es distinto de no haber mirado. Si se leyera como «no hay movimiento» a
 * secas, el dueño que sí pagó —en efectivo, o desde una cuenta que Movi no lleva— entendería que la
 * app le está diciendo que no pagó.
 */
const val TEXTO_SIN_MOVIMIENTO = "Sin movimiento: Movi no encontró ninguno"

/**
 * La casilla del checklist. **No es un control: es un reflejo** — nada de lo que la dibuja acepta
 * un toque, y lo que hay que hacer con la fila está escrito debajo (ver [LoQueOfreceLaFila]).
 *
 * [apagada] baja el borde a un hilo. Lo llevan todas las filas sin tildar desde que la casilla dejó
 * de marcar: un borde de control sobre algo que no se puede tocar es una promesa que no se cumple.
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
 * **La fecha y el estado, juntos**: «12 de septiembre · vence en 2 días».
 *
 * La fecha va siempre, también en lo ya marcado: el dueño pidió *«valor y fecha»*, y un «Pagado»
 * pelado no deja comprobar que se tildó el mes que uno creía.
 *
 * **Y en lo ya marcado el estado dice de DÓNDE salió**, no «pagado». Son cuatro certezas distintas
 * —lo emparejó Movi, lo confirmó él, lo prueba el movimiento que bajó la deuda, o es un sello viejo
 * hecho a mano sin nada detrás— y desde que la casilla dejó de ser un control, ese renglón es lo
 * único que las separa. Un «pagado» parejo para las cuatro era justamente lo que dejaba que un
 * tilde sin evidencia se leyera igual que uno anclado a plata que se puede mirar. La media frase la
 * escribe [origenDeLoOcurrido], que es la misma que usa «Ya ocurrieron».
 */
internal fun subtituloDeLaFila(pago: PagoDelPeriodo): String {
    val fecha = fechaLegibleDelChecklist(pago.vence)
    val estado = if (!pago.pagado) estadoDelChecklist(pago) else origenDeLoOcurrido(
        automatica = pago.automatica,
        derivada = pago.derivado,
        hayMovimiento = pago.eventId != null,
        // `montoPagado` ya viene filtrado por moneda: si el movimiento fue en otra, llega `null` y
        // la frase se queda sin cifra en vez de decir una que no es (ver [PagoDelPeriodo]).
        monto = pago.montoPagado,
        moneda = pago.moneda,
    )
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

/**
 * «vence hoy», «vence mañana», «venció hace 2 días». En minúscula: va después de la fecha.
 *
 * **Solo habla de lo PENDIENTE.** Tenía además un «pagado» / «recibido», y se fue con la casilla:
 * una fila ya lista dice ahora de dónde salió el tilde (ver [subtituloDeLaFila] y
 * [origenDeLoOcurrido]), que es lo que separa un emparejamiento automático de un sello viejo hecho
 * a mano. Dejar acá un «pagado» de respaldo habría sido dejar el texto que borraba esa diferencia
 * esperando a que alguien lo volviera a enchufar.
 */
internal fun estadoDelChecklist(pago: PagoDelPeriodo): String = when {
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
