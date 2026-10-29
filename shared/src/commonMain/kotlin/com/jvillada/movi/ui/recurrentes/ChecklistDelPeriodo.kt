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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.shared.model.candidatoMuyDistinto
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.CARD_RULE_PREFIX
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.pagosEnOrdenDeUrgencia
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.theme.Radio
import com.jvillada.movi.ui.components.ds.Cifra
import com.jvillada.movi.ui.components.ds.TonoDeCifra
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.IconoEnCirculo
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jvillada.movi.ui.components.Celebracion
import com.jvillada.movi.ui.components.MomentoDeCelebracion
import com.jvillada.movi.ui.components.rememberVibrador
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
 * - **Por pagar** — vence en el período y no tiene pago. En el orden de la cola de lo pendiente
 *   (vuelta de diseño 22): lo vencido primero, y dentro de cada grupo el monto más grande primero.
 * - **Ya pagaste** — tiene pago en el período. Cada fila dice con qué se sabe
 *   ([origenDeLoOcurrido], la misma media frase de siempre).
 * - **Por cobrar** / **Ya recibiste** — los ingresos, si hay.
 *
 * **Una cifra, una vez** (vuelta de diseño 1): «Por pagar» y «Ya pagaste» van sin total, porque
 * sus dos sumas son justo las del resumen de arriba. Antes la tarjeta decía «Falta por pagar
 * $1.812.000» y, un renglón abajo, «⏱ Falta por pagar · 3 … $1.812.000». Por eso también el grupo
 * se llama «Por pagar» y no repite el rótulo del resumen. Los ingresos sí llevan su total: el
 * resumen no los suma.
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

/** Lo que dice la celebración cuando el dueño completa los pagos del período. */
const val FRASE_DEL_CHECKLIST_COMPLETO = "¡Todo pagado! Ya salieron los pagos de este período."

/** Los títulos de los grupos: los comparten Plan y el detalle de un período. */
const val TITULO_FALTA_POR_PAGAR = "Falta por pagar"

/**
 * El título del grupo de lo que falta, debajo del resumen que ya dice «Falta por pagar $X»: sin
 * repetir el rótulo ni la cifra, solo cuántos son («Por pagar · 3»).
 */
const val TITULO_GRUPO_POR_PAGAR = "Por pagar"
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
 *   recurrente ya sabe. Quien llama decide a dónde lleva — la cuota de un crédito o el pago de una
 *   tarjeta se anotan en la pestaña «Cuota», no como un gasto suelto (ver `hojaParaAnotar`). Se
 *   ofrece en toda fila pendiente, también antes del vencimiento: el dueño paga antes.
 * @param onQuitarLaMarca la única salida de un sello viejo hecho a mano, sin movimiento detrás.
 * @param onAbrir tocar la fila: abrir la regla para editarla (lo que ofrecía «Próximos»). `null` la
 *   deja sin toque.
 * @param planesDeCuotas el plan de cada crédito, por id de regla (ver [planesDeLasCuotas]). Vacío
 *   deja las filas sin estimación.
 * @param debajoDelResumen una fila que va justo debajo del resumen («Falta por pagar · Ya pagaste»),
 *   dentro de la lista: en Plan, «Tus recordatorios no te van a llegar» (vuelta de diseño 28). Era una
 *   tarjeta de propuesta encima de toda la sección, la segunda más grande de la pantalla.
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
    debajoDelResumen: (@Composable () -> Unit)? = null,
    /** Vuelta 48: tocar una factura pagada (compacta) abre el movimiento que la pagó. */
    onVerMovimiento: ((eventId: String) -> Unit)? = null,
) {
    // ── El momento de celebrar (Ola 1 · «Que Movi se sienta viva») ──────────────────────────────
    // Cuando el dueño completa la lista —pasa de «falta 1» a «todo pagado» por algo que hizo acá:
    // tildar con «Sí, fue este» o anotar el pago—, una vez y en la transición (ver [Celebracion]).
    // Abrir Plan con todo ya pagado no celebra. `rememberSaveable` porque la sección es un ítem de
    // una lista perezosa: salir de la vista y volver no puede olvidar lo último que se supo.
    var ultimoEstadoDeLaLista by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var huboAccion by rememberSaveable { mutableStateOf(false) }
    var celebrar by rememberSaveable { mutableStateOf(false) }
    val listaCompleta: Boolean? = when {
        cargando || !pudoLeer || checklist.none { !it.esIngreso } -> null
        else -> pagosPendientes(checklist).isEmpty()
    }
    LaunchedEffect(listaCompleta) {
        val celebracion = Celebracion(ultimoEstadoDeLaLista)
        if (celebracion.observar(listaCompleta, huboAccion)) {
            celebrar = true
            huboAccion = false
        }
        if (listaCompleta == false) celebrar = false
        ultimoEstadoDeLaLista = celebracion.ultimoConocido
    }
    // Tildar vibra (ver [Vibrador]); anotar vibra al guardar, en «Agregar».
    val vibrador = rememberVibrador()
    val acciones = AccionesDelChecklist(
        onConfirmar = { pago, eventId ->
            huboAccion = true
            vibrador.confirmar()
            onConfirmar(pago, eventId)
        },
        onNoFueEste = onNoFueEste,
        onAnotarMovimiento = { pago ->
            huboAccion = true
            onAnotarMovimiento(pago)
        },
        onQuitarLaMarca = onQuitarLaMarca,
        onVerMovimiento = onVerMovimiento,
    )

    // Vuelta 40: cuántas facturas se ven antes de «Ver los N pagos del período».
    var todasALaVista by rememberSaveable { mutableStateOf(false) }
    val facturas = enOrdenDeFacturas(checklist.filter { !it.esIngreso })
    val ingresos = enOrdenDeFacturas(checklist.filter { it.esIngreso })
    val urgente = laMasUrgente(facturas)
    @Composable
    fun FilaDe(pago: PagoDelPeriodo, conAnotar: Boolean = true) {
        val propuesta = propuestaDeLaFila(pago, descartadas)
        // Vuelta 51: una propuesta muy distinta del monto no es «Por confirmar»: la fila ofrece «Anotar pago».
        val conPropuesta = pago.estado == EstadoDeLaFila.CON_DUDAS && propuesta != null && !esPropuestaLejana(pago, propuesta)
        val enVuelo = pago.ruleId in marcando
        val estimacion = estimacionDeLaFila(pago, planesDeCuotas)?.takeIf { !enVuelo && !pago.pagado }
        FilaDeFactura(
            pago = pago,
            principal = pago == urgente,
            enVuelo = enVuelo,
            conPropuesta = conPropuesta,
            onAnotar = if (conAnotar) ({ acciones.onAnotarMovimiento(pago) }) else null,
            onVerMovimiento = onVerMovimiento,
            onAbrir = onAbrir?.let { abrir -> { abrir(pago) } },
            etiquetaDeAbrir = etiquetaDeAbrir(pago.ruleId),
            evidencia = evidenciaDeLaFila(pago),
            // Vuelta 48: lo de una fila pagada va en su «⋯»; tocarla abre el movimiento.
            accionesDelMenu = accionesDelMenuDeLaFilaPagada(pago).map { accion ->
                when (accion) {
                    AccionDeLaFilaPagada.NO_FUE_ESTE -> ETIQUETA_NO_FUE_ESTE to { pago.eventId?.let { acciones.onNoFueEste(pago, it) }; Unit }
                    AccionDeLaFilaPagada.QUITAR_LA_MARCA -> ETIQUETA_QUITAR_LA_MARCA to { acciones.onQuitarLaMarca(pago) }
                    // No llega: ver un movimiento es tocar la fila.
                    AccionDeLaFilaPagada.VER_MOVIMIENTO -> ETIQUETA_VER_MOVIMIENTO to { pago.eventId?.let { onVerMovimiento?.invoke(it) }; Unit }
                }
            },
            debajo = if (!pago.pagado && (estimacion != null || ofreceAlgoDebajo(pago, conPropuesta))) {
                {
                    if (estimacion != null) Text(text = estimacion, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
                    LoQueOfreceLaFila(pago, propuesta, enVuelo, acciones, conBotonDeAnotar = false)
                }
            } else null,
        )
    }

    Column(modifier = modifier) {
        when {
            cargando -> Seccion(titulo = TITULO_TUS_FACTURAS)
            !pudoLeer -> {
                Seccion(titulo = TITULO_TUS_FACTURAS)
                NoSePudoLeer(
                    texto = "No se pudieron leer los pagos de este período",
                    onReintentar = onReintentar,
                )
            }
            checklist.isEmpty() && anteriores.isEmpty() -> {
                Seccion(titulo = TITULO_TUS_FACTURAS)
                Superficie(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Este período no tiene pagos anotados",
                        style = Movi.textos.cuerpo,
                        color = Movi.colores.textoMedio,
                    )
                    debajoDelResumen?.invoke()
                }
            }
            else -> {
                // ── La tarjeta amarilla: cuánto falta, de cuánto, y cuánto llevas ──────────────
                if (facturas.isNotEmpty()) {
                    TarjetaFaltaPorPagar(resumenDeFacturas(checklist))
                    Spacer(Modifier.height(Medida.entreSecciones))
                }
                // «Tus recordatorios no llegan» (vuelta de diseño 28): un aviso propio, compacto y en
                // tono de aviso, entre la tarjeta amarilla y la lista. No es una factura: va afuera
                // de la lista (revisión de la vuelta 40).
                if (debajoDelResumen != null) {
                    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                        debajoDelResumen()
                    }
                    Spacer(Modifier.height(Medida.entreSecciones))
                }
                // ── Tus facturas del período ─────────────────────────────────────────────────
                Seccion(titulo = TITULO_TUS_FACTURAS)
                Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                    if (celebrar) {
                        Box(Modifier.padding(horizontal = Medida.margenDePantalla).padding(top = Espacio.s)) {
                            MomentoDeCelebracion(FRASE_DEL_CHECKLIST_COMPLETO)
                        }
                    }
                    val aLaVista = if (todasALaVista) facturas else facturas.take(FACTURAS_A_LA_VISTA)
                    if (facturas.isEmpty()) {
                        Text(
                            text = lineaDeLoQueFalta(checklist) ?: "Este período no tiene pagos anotados",
                            style = Movi.textos.cuerpo,
                            color = Movi.colores.textoMedio,
                            modifier = Modifier.padding(Medida.rellenoDeSuperficie),
                        )
                    }
                    aLaVista.forEachIndexed { i, pago ->
                        if (i > 0) Separador(SangriaDeSeparador.ConInicio)
                        FilaDe(pago)
                    }
                    // De dónde salen las estimaciones de las cuotas: una vez, si alguna fila la muestra.
                    if (aLaVista.any { !it.pagado && estimacionDeLaFila(it, planesDeCuotas) != null }) {
                        Text(
                            text = ESTIMADO_SOBRE_LA_DEUDA_DE_HOY,
                            style = Movi.textos.apoyo,
                            color = Movi.colores.textoApagado,
                            modifier = Modifier.padding(horizontal = Medida.margenDePantalla).padding(bottom = Espacio.s),
                        )
                    }
                }
                if (facturas.size > FACTURAS_A_LA_VISTA) {
                    Boton(
                        if (todasALaVista) "Ver menos" else textoDeVerTodas(facturas.size),
                        onClick = { todasALaVista = !todasALaVista },
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag(TAG_VER_TODAS_LAS_FACTURAS),
                        variante = VarianteDeBoton.Fantasma,
                        icono = if (todasALaVista) null else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    )
                }
                // ── Los ingresos fijos (el sueldo): no se pagan, llegan ───────────────────────
                if (ingresos.isNotEmpty()) {
                    Spacer(Modifier.height(Medida.entreSecciones))
                    Seccion(titulo = TITULO_TUS_INGRESOS)
                    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                        ingresos.forEachIndexed { i, pago ->
                            if (i > 0) Separador(SangriaDeSeparador.ConInicio)
                            FilaDe(pago)
                        }
                    }
                }
                // ── Lo que un período anterior dejó abierto: aparte y al final ────────────────
                if (anteriores.isNotEmpty()) {
                    Spacer(Modifier.height(Medida.entreSecciones))
                    Seccion(titulo = tituloDeLosAnteriores(anteriores), conteo = anteriores.size)
                    Text(
                        EXPLICACION_DE_LOS_ANTERIORES,
                        style = Movi.textos.apoyo,
                        color = Movi.colores.textoMedio,
                        modifier = Modifier.padding(bottom = Espacio.xs),
                    )
                    Superficie(modifier = Modifier.fillMaxWidth(), relleno = RellenoDeSuperficie.Lista) {
                        anteriores.forEachIndexed { i, pago ->
                            if (i > 0) Separador(SangriaDeSeparador.ConInicio)
                            FilaDe(pago)
                        }
                    }
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
 * **Lo que el dueño vino a preguntar, arriba de todo**: cuánto falta y cuánto ya pagó en el
 * período, en pesos. Las dos cifras son la suma de los grupos de abajo — ni más ni menos.
 *
 * **Una cifra en cero va apagada** (vuelta de diseño 28): «Ya pagaste $0» en la letra grande pesaba lo
 * mismo que lo que falta, y no dice nada que haya que mirar ([tonoDelResumen]).
 */
@Composable
internal fun ResumenDeLaLista(izquierda: Pair<String, Long>, derecha: Pair<String, Long>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla)
            .padding(top = Espacio.s, bottom = Espacio.xs),
        horizontalArrangement = Arrangement.spacedBy(Espacio.s),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(izquierda.first, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Cifra(izquierda.second, VarianteDeCifra.Grande, tono = tonoDelResumen(izquierda.second))
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(derecha.first, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            Cifra(derecha.second, VarianteDeCifra.Grande, tono = tonoDelResumen(derecha.second))
        }
    }
}

/** El tono de una cifra del resumen: apagada en cero, en el color del texto si no (vuelta de diseño 28). */
internal fun tonoDelResumen(monto: Long): TonoDeCifra = if (monto == 0L) TonoDeCifra.Apagado else TonoDeCifra.Neutro

/**
 * **Un grupo de la lista**: ícono, título y total, y debajo sus filas separadas por un hilo.
 *
 * Genérico en la fila a propósito: lo usan Plan (con [PagoDelPeriodo] y sus acciones) y el detalle
 * de un período (con `PagoFijoDelPeriodo`, de solo lectura). Los dos tienen que verse igual.
 *
 * @param total la suma de las filas, en pesos; `null` o cero no se pinta. `null` cuando la suma ya
 *   está en el [ResumenDeLaLista] de arriba: una cifra, una vez.
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
    // El rótulo del grupo: ícono, «Por pagar · 3» y el total (si el resumen no lo dice ya). Va con el margen de la pantalla
    // porque las filas de abajo son [Fila]s, que traen el suyo.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Medida.margenDePantalla)
            .padding(top = Espacio.s, bottom = Espacio.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Icon(icono, contentDescription = null, tint = colorDelIcono, modifier = Modifier.size(Icono.s))
        Text(
            text = if (filas.isEmpty()) titulo else "$titulo · ${filas.size}",
            style = Movi.textos.cuerpoFuerte,
            color = Movi.colores.texto,
            modifier = Modifier.weight(1f),
        )
        if (total != null && total > 0) Cifra(total)
    }
    if (explicacion != null) {
        Text(
            explicacion,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(horizontal = Medida.margenDePantalla),
        )
    }
    if (filas.isEmpty()) {
        Text(
            text = vacio.orEmpty(),
            style = Movi.textos.cuerpo,
            color = Movi.colores.textoMedio,
            modifier = Modifier.padding(horizontal = Medida.margenDePantalla).padding(top = Espacio.xs, bottom = Espacio.s),
        )
        return
    }
    filas.forEachIndexed { i, item ->
        fila(item)
        if (i < filas.lastIndex) Separador(SangriaDeSeparador.ConInicio)
    }
    if (pie != null) {
        Text(
            text = pie,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoApagado,
            modifier = Modifier.padding(horizontal = Medida.margenDePantalla).padding(bottom = Espacio.s),
        )
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
    pildora: Pair<String, TonoDePildora>? = null,
    debajo: (@Composable ColumnScope.() -> Unit)? = null,
) {
    // Ola R4: la fila del sistema. El ícono de estado en el inicio, el nombre y la fecha, y al final
    // el monto con su píldora de estado («Vencido», «Pagado», «Por confirmar»): el color nunca es la
    // única pista. La evidencia y las acciones cuelgan debajo ([BajoLaFila]).
    Fila(
        titulo = nombre,
        subtitulo = fecha.takeIf { it.isNotEmpty() },
        inicio = { IconoDeEstadoDelPago(estado, esIngreso) },
        final = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                MontoDeFila(
                    monto,
                    color = colorDelMonto,
                    variante = if (montoChico) VarianteDeCifra.Chica else VarianteDeCifra.Fila,
                )
                if (pildora != null) Pildora(pildora.first, pildora.second)
            }
        },
        onClick = onAbrir,
        onClickLabel = etiquetaDeAbrir,
    )
    if (evidencia != null || debajo != null) {
        BajoLaFila {
            if (evidencia != null) {
                Text(text = evidencia, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
            }
            debajo?.invoke(this)
        }
    }
}

/**
 * El ícono de estado, en un círculo: vacío = falta, check = pagado, alerta = vencido (o, en un
 * período cerrado, no se pagó). Con descripción: para un lector de pantalla el color no existe.
 */
@Composable
private fun IconoDeEstadoDelPago(estado: EstadoVisualDelPago, esIngreso: Boolean) {
    val (icono, tono, descripcion) = when (estado) {
        EstadoVisualDelPago.FALTA -> Triple(Icons.Rounded.RadioButtonUnchecked, TonoDeIcono.Neutro, if (esIngreso) "Por cobrar" else "Falta")
        EstadoVisualDelPago.VENCIDO -> Triple(Icons.Rounded.ErrorOutline, TonoDeIcono.Peligro, "Vencido")
        EstadoVisualDelPago.PAGADO -> Triple(Icons.Rounded.CheckCircle, TonoDeIcono.Exito, if (esIngreso) "Recibido" else "Pagado")
        EstadoVisualDelPago.NO_SE_PAGO -> Triple(Icons.Rounded.HighlightOff, TonoDeIcono.Peligro, if (esIngreso) "No llegó" else "No se pagó")
    }
    IconoEnCirculo(icono, tono = tono, modifier = Modifier.semantics { contentDescription = descripcion })
}

/**
 * **La píldora de estado de una fila del checklist** (ola R4): lo que el dueño mira primero —si ya
 * está, si se pasó, si Movi pregunta— en palabras y con el tono del sistema. Pura, para probarla.
 *
 * @param conPropuesta si Movi tiene un movimiento para preguntar «¿fue este?».
 */
internal fun pildoraDeLaFila(pago: PagoDelPeriodo, conPropuesta: Boolean): Pair<String, TonoDePildora> = when {
    pago.pagado -> (if (pago.esIngreso) "Recibido" else "Pagado") to TonoDePildora.Exito
    conPropuesta -> "Por confirmar" to TonoDePildora.Aviso
    // Vuelta 49: lo que el banco cobra solo no «vence»: se pregunta si se cobró.
    pago.seCobraSola && pago.diasParaVencer <= 0 -> PILDORA_SE_COBRO to TonoDePildora.Aviso
    pago.vencido -> (if (pago.esIngreso) "Atrasado" else "Vencido") to TonoDePildora.Peligro
    pago.diasParaVencer == 0 -> "Hoy" to TonoDePildora.Aviso
    pago.diasParaVencer == 1 -> "Mañana" to TonoDePildora.Aviso
    pago.diasParaVencer in 2..3 -> "En ${pago.diasParaVencer} días" to TonoDePildora.Aviso
    else -> (if (pago.esIngreso) "Por cobrar" else "Falta") to TonoDePildora.Neutra
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
    /** Vuelta 45: «Ver movimiento» de una factura que saldó el dueño. `null` no lo ofrece. */
    val onVerMovimiento: ((eventId: String) -> Unit)? = null,
)

/** Lo que puede ofrecer una fila pagada (vuelta 45). */
enum class AccionDeLaFilaPagada { NO_FUE_ESTE, VER_MOVIMIENTO, QUITAR_LA_MARCA }

/**
 * **Lo que ofrece una factura pagada, según QUIÉN la dio por pagada** (vuelta 45).
 *
 * - **La emparejó Movi sola** (`automatica`): «No fue este» —la salida de cuando Movi dedujo mal—.
 * - **La selló el dueño con un movimiento** —desde «Anotar pago» o con «Sí, fue este»—: «Ver
 *   movimiento» y «Quitar la marca». Antes ofrecía «No fue este», como si Movi la hubiera emparejado:
 *   el dueño no tiene a quién decirle «no fue este» de un pago que él mismo anotó.
 * - Lo derivado de una deuda (la cuota que bajó el crédito) y lo que no está pagado: nada.
 *
 * El sello viejo sin movimiento ([EstadoDeLaFila.MARCADA_A_MANO]) sigue con su «Quitar la marca».
 */
fun accionesDeLaFilaPagada(pago: PagoDelPeriodo): List<AccionDeLaFilaPagada> = when {
    pago.estado != EstadoDeLaFila.LISTO -> emptyList()
    pago.automatica -> listOf(AccionDeLaFilaPagada.NO_FUE_ESTE)
    pago.eventId != null && !pago.derivado -> listOf(AccionDeLaFilaPagada.VER_MOVIMIENTO, AccionDeLaFilaPagada.QUITAR_LA_MARCA)
    else -> emptyList()
}

/**
 * **Lo que va en el «⋯» de una factura pagada** (vuelta 48). La fila pagada es compacta —ícono, nombre,
 * «Pagado ✓ · $1.800.000 · 27 oct»— y **tocarla abre el movimiento**, así que «Ver movimiento» deja de
 * ser un botón. Lo demás se guarda en el menú: «Quitar la marca» (un sello del dueño, con movimiento o
 * el viejo sin él) y «No fue este» (solo lo que Movi emparejó solo). Lo que prueba el pago de una deuda
 * no ofrece nada: ahí lo que revierte es borrar el pago.
 */
fun accionesDelMenuDeLaFilaPagada(pago: PagoDelPeriodo): List<AccionDeLaFilaPagada> = when (pago.estado) {
    EstadoDeLaFila.LISTO -> accionesDeLaFilaPagada(pago) - AccionDeLaFilaPagada.VER_MOVIMIENTO
    EstadoDeLaFila.MARCADA_A_MANO -> listOf(AccionDeLaFilaPagada.QUITAR_LA_MARCA)
    else -> emptyList()
}

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
internal fun propuestaDeLaFila(pago: PagoDelPeriodo, descartadas: Set<String>): FinancialEvent? {
    val vivos = pago.candidatos.filter { claveDescartada(pago.ruleId, it.id) !in descartadas }
    // Vuelta 51: primero uno que se parezca al monto; uno muy distinto solo si no queda otro, y entonces
    // la fila no lo propone de principal (ver [esPropuestaLejana]).
    return vivos.firstOrNull { !pago.candidatoMuyDistinto(it) } ?: vivos.firstOrNull()
}

/**
 * **¿La propuesta de la fila está muy lejos del monto?** (vuelta 51; [candidatoMuyDistinto] en `:core`,
 * más del 50 %). Entonces la fila no pregunta «¿Ya pagaste…?» de principal: «Anotar pago» queda como
 * acción y el candidato como enlace, «Hay un movimiento de $260.000 · ¿Es este?». Nunca se tilda solo.
 */
internal fun esPropuestaLejana(pago: PagoDelPeriodo, propuesta: FinancialEvent?): Boolean =
    propuesta != null && pago.candidatoMuyDistinto(propuesta)

/** «Hay un movimiento de $260.000 · ¿Es este?»: el enlace de una propuesta lejana (vuelta 51). */
fun enlaceDeLaPropuestaLejana(propuesta: FinancialEvent): String =
    "Hay un movimiento de ${formatMoney(propuesta.amount, propuesta.currency)} · ¿Es este?"

/** El tag del enlace de una propuesta lejana. */
const val TAG_PROPUESTA_LEJANA: String = "checklist:propuesta-lejana"

/** Qué dice el toque sobre una fila: una cuota o una tarjeta se gestionan en Créditos. */
internal fun etiquetaDeAbrir(ruleId: String): String =
    if (ruleId.startsWith(CREDIT_RULE_PREFIX) || ruleId.startsWith(CARD_RULE_PREFIX)) "Ver en Créditos"
    else "Editar el pago fijo"

/**
 * Si la fila tiene algo que ofrecer debajo. Una fila sin nada no deja ni un hueco: la mayoría son
 * cuotas que el movimiento ya probó.
 */
private fun hayQueOfrecer(pago: PagoDelPeriodo): Boolean = when (pago.estado) {
    // Una fila que todavía no vence SÍ ofrece algo: anotar el pago. Ver [LoQueOfreceLaFila].
    EstadoDeLaFila.AUN_NO_VENCE -> true
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
    /**
     * Vuelta 40: en una [FilaDeFactura] «Anotar pago» ya está a la derecha de la fila; debajo no se
     * repite (ni «Movi no encontró…»: la pastilla ya dice que venció).
     */
    conBotonDeAnotar: Boolean = true,
) {
    if (!hayQueOfrecer(pago)) return
    if (!conBotonDeAnotar && pago.estado == EstadoDeLaFila.AUN_NO_VENCE) return
    // Vuelta 51: el candidato que se aleja más de la mitad del monto va de enlace, no de pregunta.
    if (pago.estado == EstadoDeLaFila.CON_DUDAS && propuesta != null && esPropuestaLejana(pago, propuesta)) {
        PropuestaLejana(pago, propuesta, enVuelo, acciones, conBotonDeAnotar)
        return
    }
    if (!conBotonDeAnotar && (pago.estado == EstadoDeLaFila.SIN_MOVIMIENTO || (pago.estado == EstadoDeLaFila.CON_DUDAS && propuesta == null))) {
        // Movi buscó y no encontró: se dice (el botón ya está a la derecha de la fila).
        Text(
            text = textoSinMovimiento(pago),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        when (pago.estado) {
            // Vuelta 45: lo que ofrece depende de quién la dio por pagada (ver [accionesDeLaFilaPagada]).
            EstadoDeLaFila.LISTO -> AccionesDeLaFila(
                acciones = accionesDeLaFilaPagada(pago).mapNotNull { accion ->
                    when (accion) {
                        AccionDeLaFilaPagada.NO_FUE_ESTE -> AccionDeOcurrencia(ETIQUETA_NO_FUE_ESTE, primary = false) {
                            pago.eventId?.let { acciones.onNoFueEste(pago, it) }
                        }
                        AccionDeLaFilaPagada.VER_MOVIMIENTO -> {
                            val ver = acciones.onVerMovimiento
                            val id = pago.eventId
                            if (ver != null && id != null) AccionDeOcurrencia(ETIQUETA_VER_MOVIMIENTO, primary = false) { ver(id) } else null
                        }
                        AccionDeLaFilaPagada.QUITAR_LA_MARCA -> AccionDeOcurrencia(ETIQUETA_QUITAR_LA_MARCA, primary = false) {
                            acciones.onQuitarLaMarca(pago)
                        }
                    }
                },
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
            // La pregunta es una propuesta de Movi: va en la superficie de las propuestas (la de
            // «Movi propone hoy» del Inicio), con su píldora y sus dos botones del sistema.
            EstadoDeLaFila.CON_DUDAS -> if (propuesta != null) {
                // Vuelta 40 (revisión): dentro de la lista de facturas, un bloque plano en la capa 2
                // —sin sombra—: una tarjeta con sombra metida en una lista es tarjeta dentro de tarjeta.
                Superficie(
                    modifier = Modifier.fillMaxWidth(),
                    nivel = if (conBotonDeAnotar) NivelDeSuperficie.Propuesta else NivelDeSuperficie.Capa2,
                ) {
                    Text(text = preguntaDeLaFila(pago), style = Movi.textos.titulo, color = Movi.colores.texto)
                    Spacer(Modifier.height(Espacio.xs))
                    PropuestaDeMovimiento(
                        propuesta = propuesta,
                        // Se mudó de «Próximos» con la propuesta: sin él, un movimiento de otro monto
                        // se confirmaba a ciegas. En una tarjeta (saldo) o en otra moneda no se compara.
                        aviso = avisoDeMontoDistinto(pago.monto, pago.montoEsSaldo, propuesta),
                        enVuelo = enVuelo,
                        onConfirmar = { acciones.onConfirmar(pago, propuesta.id) },
                        onDescartar = { acciones.onNoFueEste(pago, propuesta.id) },
                    )
                }
            } else {
                SinMovimiento(pago, enVuelo, acciones)
            }
            EstadoDeLaFila.SIN_MOVIMIENTO -> SinMovimiento(pago, enVuelo, acciones)
            // **El dueño paga ANTES del vencimiento** (Master Black vence el 2 y la pagó el 27):
            // sin esta salida, «Anotar este pago» —que abre Agregar ya lleno, en «Cuota» si es una
            // deuda— solo aparecía cuando el pago ya estaba vencido, o sea casi nunca. Va como
            // acción secundaria y sin el «Movi no encontró…»: todavía no tenía por qué encontrarlo.
            // Solo el botón, para que la fila crezca lo mínimo.
            EstadoDeLaFila.AUN_NO_VENCE -> AccionesDeLaFila(
                acciones = listOf(
                    AccionDeOcurrencia(etiquetaDeAnotar(pago.esIngreso), primary = false) {
                        acciones.onAnotarMovimiento(pago)
                    },
                ),
                enVuelo = enVuelo,
            )
        }
    }
}

/**
 * **La propuesta lejana** (vuelta 51): «Anotar pago» sigue siendo la acción —a la derecha de la fila, o
 * acá si la fila no lo tiene ([conBotonDeAnotar])— y el candidato queda como enlace, «Hay un movimiento
 * de $260.000 · ¿Es este?». Tocarlo abre la pregunta de siempre («Sí, fue este» / «No fue este»): el
 * dueño decide; nada se tilda solo.
 */
@Composable
private fun PropuestaLejana(
    pago: PagoDelPeriodo,
    propuesta: FinancialEvent,
    enVuelo: Boolean,
    acciones: AccionesDelChecklist,
    conBotonDeAnotar: Boolean,
) {
    var abierta by rememberSaveable(pago.ruleId, propuesta.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (conBotonDeAnotar) {
            AccionesDeLaFila(
                acciones = listOf(
                    AccionDeOcurrencia(etiquetaDeAnotar(pago.esIngreso), primary = true) { acciones.onAnotarMovimiento(pago) },
                ),
                enVuelo = enVuelo,
            )
        }
        Boton(
            enlaceDeLaPropuestaLejana(propuesta),
            onClick = { abierta = !abierta },
            variante = VarianteDeBoton.Fantasma,
            tamano = TamanoDeBoton.S,
            habilitado = !enVuelo,
            modifier = Modifier.testTag(TAG_PROPUESTA_LEJANA),
        )
        if (abierta) {
            Superficie(modifier = Modifier.fillMaxWidth(), nivel = NivelDeSuperficie.Capa2) {
                PropuestaDeMovimiento(
                    propuesta = propuesta,
                    aviso = avisoDeMontoDistinto(pago.monto, pago.montoEsSaldo, propuesta),
                    enVuelo = enVuelo,
                    onConfirmar = { acciones.onConfirmar(pago, propuesta.id) },
                    onDescartar = { acciones.onNoFueEste(pago, propuesta.id) },
                )
            }
        }
    }
}

/** Lo dice y ofrece la única salida honesta: que el movimiento exista. */
@Composable
private fun SinMovimiento(pago: PagoDelPeriodo, enVuelo: Boolean, acciones: AccionesDelChecklist) {
    Text(
        text = textoSinMovimiento(pago),
        style = Movi.textos.apoyo,
        color = Movi.colores.textoMedio,
    )
    Spacer(Modifier.height(Espacio.xs))
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

/**
 * Vuelta 49: lo que dice una cuota que el banco cobra sola y todavía no tiene movimiento. No es que
 * Movi no la encontró: el banco no avisa cuando la debita.
 */
const val TEXTO_SIN_MOVIMIENTO_DEL_DEBITO = "El banco no avisa cuando la cobra: si ya se cobró, anótalo."

/** Qué dice una fila sin movimiento: un ingreso, una cuota que se cobra sola, o un pago. Pura. */
fun textoSinMovimiento(pago: PagoDelPeriodo): String = when {
    pago.esIngreso -> TEXTO_SIN_MOVIMIENTO_DE_INGRESO
    pago.seCobraSola -> TEXTO_SIN_MOVIMIENTO_DEL_DEBITO
    else -> TEXTO_SIN_MOVIMIENTO
}

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
            .size(Icono.m)
            .clip(RoundedCornerShape(Radio.s))
            .background(if (marcada) Movi.colores.lavado(Movi.colores.entra) else Movi.colores.tarjeta)
            .then(
                if (marcada) Modifier
                else Modifier.border(
                    Medida.trazo,
                    if (apagada) Movi.colores.hilo else Movi.colores.borde,
                    RoundedCornerShape(Radio.s),
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
                modifier = Modifier.size(Icono.s),
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
