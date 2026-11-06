package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.ExtractoDeLaTarjeta
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoParecidoALaCuota
import com.jvillada.movi.shared.model.SI_ES_LA_CUOTA
import com.jvillada.movi.shared.model.preguntaDeLaCuotaQueSeParece
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Icono
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.MarcoDeHoja
import com.jvillada.movi.ui.components.desplazableConElTeclado
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.EncabezadoDeHoja
import com.jvillada.movi.ui.components.ds.NivelDeSuperficie
import com.jvillada.movi.ui.components.ds.Pildora
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.TonoDePildora
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeCifra
import com.jvillada.movi.ui.components.ds.colorDelTonoDeSubtitulo
import com.jvillada.movi.ui.credits.ANOTAR_ESTE_ABONO
import com.jvillada.movi.ui.credits.PUEDE_INCLUIR_DIFERIDOS
import com.jvillada.movi.ui.credits.alternativaDelMinimo
import com.jvillada.movi.ui.credits.extractoDeLaFila
import com.jvillada.movi.ui.credits.loQueFaltaDelExtracto
import com.jvillada.movi.ui.dashboard.EstadoDeLaFila
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo

/*
 * # Vuelta 136 · la hoja de una factura
 *
 * El dueño, mirando Pagos en su Pixel: «El formato de lo por pagar y pagado debería ser consistente, es
 * decir, misma altura de la card de la row y componentes». Cada fila de «Tus facturas del período» es ahora
 * la misma ([FilaDeFactura]: ícono, nombre, estado, cifra y fecha) y **todo lo que antes colgaba de la fila
 * o se escondía en su «⋯» vive acá**: la estimación de una cuota, el candidato con «Sí, fue este» / «No fue
 * este», «Anotar pago», «Ver movimiento», «Quitar la marca» con la frase de dónde salió el «Pagado»,
 * «Pausar», «Editar el pago fijo» (o «Ver en Créditos») y el extracto de una tarjeta. **Ninguna acción
 * cambió**: son las mismas funciones, con otra casa. Cada una cierra la hoja antes de hacer lo suyo (la fila
 * dice «Guardando…» mientras viaja).
 *
 * **Un solo amarillo**: «Anotar pago» si falta; «Sí, fue este» (o «Sí, es la cuota») si Movi tiene un
 * candidato cerca del monto; ninguno si está pagada.
 */

/**
 * **Lo que una factura sabe de sí** (vuelta 136): lo mismo para su fila, su hoja y la tarjeta «Falta por
 * pagar». Sale de las funciones de siempre ([propuestaDeLaFila], [gastoParecidoDeLaFila],
 * [estimacionDeLaFila], [extractoDeLaFila], [porQueNoSuma], [evidenciaDeLaFila]).
 *
 * @property propuesta el movimiento que el server propone, cerca o lejos del monto; solo con dudas.
 * @property conPropuesta la propuesta está cerca del monto (vuelta 51): «Sí, fue este» es lo principal y
 *   «Anotar pago» no se ofrece (duplicaría el gasto).
 * @property parecido el gasto suelto que se parece a la cuota de un crédito (vuelta 57).
 */
internal data class LoDeLaFactura(
    val pago: PagoDelPeriodo,
    val propuesta: FinancialEvent?,
    val conPropuesta: Boolean,
    val parecido: GastoParecidoALaCuota?,
    val enVuelo: Boolean,
    val estimacion: String?,
    val extracto: ExtractoDeLaTarjeta?,
    val noSuma: PorQueNoSuma?,
    val evidencia: String?,
) {
    /** El movimiento por el que Movi pregunta, si hay uno. */
    val candidato: FinancialEvent? get() = propuesta ?: parecido?.gasto

    /** El renglón de estado de la fila. */
    val estado: EstadoDeLaFactura get() = estadoDeLaFactura(pago, candidato, enVuelo, noSuma, extracto)
}

/** Arma [LoDeLaFactura]. [enElTotal]: la fila es del período (las de antes no suman en nada). Pura. */
internal fun loDeLaFactura(
    pago: PagoDelPeriodo,
    descartadas: Set<String>,
    enVuelo: Boolean,
    planesDeCuotas: PlanesDeCuotas,
    cuotasQueSeParecen: Map<String, GastoParecidoALaCuota>,
    extractosDeTarjetas: Map<String, ExtractoDeLaTarjeta>,
    enElTotal: Boolean,
): LoDeLaFactura {
    val propuesta = propuestaDeLaFila(pago, descartadas)?.takeIf { pago.estado == EstadoDeLaFila.CON_DUDAS && !pago.hecho }
    return LoDeLaFactura(
        pago = pago,
        propuesta = propuesta,
        // Vuelta 51: una propuesta muy distinta del monto no es «Por confirmar»: se ofrece «Anotar pago».
        conPropuesta = propuesta != null && !esPropuestaLejana(pago, propuesta),
        parecido = gastoParecidoDeLaFila(pago, cuotasQueSeParecen),
        enVuelo = enVuelo,
        estimacion = estimacionDeLaFila(pago, planesDeCuotas)?.takeIf { !pago.pagado },
        extracto = extractoDeLaFila(pago, extractosDeTarjetas),
        // Vuelta 86: solo las filas del período (las de «Falta por pagar»); lo de antes no suma en nada.
        noSuma = if (enElTotal) porQueNoSuma(pago) else null,
        evidencia = evidenciaDeLaFila(pago),
    )
}

/** La clave de una factura en la lista: su regla y su vencimiento (una regla puede salir en dos períodos). */
internal fun claveDeLaFactura(pago: PagoDelPeriodo): String = "${pago.ruleId}@${pago.vence}"

/** El tag de la hoja de una factura y el de su bloque del candidato. */
const val TAG_HOJA_DE_LA_FACTURA: String = "pagos:hoja-de-la-factura"
const val TAG_CANDIDATO_EN_LA_HOJA: String = "pagos:candidato-en-la-hoja"

/** La acción de anotar de una factura: «Anotar pago», «Anotar ingreso» o «Anotar este abono». Pura. */
internal fun etiquetaDeAnotarLaFactura(pago: PagoDelPeriodo): String = when {
    pago.esIngreso -> ETIQUETA_ANOTAR_INGRESO
    // Vuelta 63: abre el Traspaso (o Cuota) hacia la deuda, ya lleno, y al guardar la tilda.
    pago.esAbono -> ANOTAR_ESTE_ABONO
    else -> ETIQUETA_ANOTAR
}

/** Qué es lo amarillo en la hoja de una factura (vuelta 136). */
internal enum class PrincipalDeLaHoja { ANOTAR, EL_CANDIDATO, NINGUNA }

/**
 * **El único amarillo de la hoja** (vuelta 136): ninguno si está pagada; «Sí, fue este» con una propuesta
 * cerca del monto o «Sí, es la cuota» con un gasto que se le parece; si no, «Anotar pago». Pura.
 */
internal fun principalDeLaHoja(lo: LoDeLaFactura): PrincipalDeLaHoja = when {
    lo.pago.hecho -> PrincipalDeLaHoja.NINGUNA
    lo.conPropuesta || lo.parecido != null -> PrincipalDeLaHoja.EL_CANDIDATO
    else -> PrincipalDeLaHoja.ANOTAR
}

/**
 * **Lo que ofrece la hoja de una factura pagada**: «Ver movimiento» si hay uno (desde la vuelta 136 tocar la
 * fila abre la hoja, no el movimiento), y lo de siempre según quién la dio por pagada
 * ([accionesDelMenuDeLaFilaPagada]: «Quitar la marca», «No fue este»). Pura.
 */
fun accionesDeLaHojaPagada(pago: PagoDelPeriodo): List<AccionDeLaFilaPagada> {
    if (!pago.hecho) return emptyList()
    val delMenu = accionesDelMenuDeLaFilaPagada(pago)
    val ver = if (pago.eventId != null) listOf(AccionDeLaFilaPagada.VER_MOVIMIENTO) else emptyList()
    return (ver + delMenu).distinct()
}

/**
 * **La hoja de una factura** (vuelta 136). Cada acción cierra la hoja ([onCerrar]) y hace lo de siempre.
 * Fuera del teléfono es una ficha de [Medida.anchoDeFicha] (como el detalle de un movimiento, vuelta 81).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HojaDeLaFactura(
    lo: LoDeLaFactura,
    onCerrar: () -> Unit,
    onAnotar: () -> Unit,
    onConfirmar: (eventId: String) -> Unit,
    onNoFueEste: (eventId: String) -> Unit,
    onEsLaCuota: (() -> Unit)?,
    onNoEsLaCuota: (() -> Unit)?,
    onVerMovimiento: ((String) -> Unit)?,
    onQuitarLaMarca: () -> Unit,
    onPausar: (() -> Unit)?,
    onAbrirLaRegla: (() -> Unit)?,
    etiquetaDeAbrir: String,
    onCargarMinimo: (() -> Unit)?,
) {
    val pago = lo.pago
    fun cerrarY(accion: () -> Unit): () -> Unit = { onCerrar(); accion() }
    val principal = principalDeLaHoja(lo)
    MarcoDeHoja(onDismiss = onCerrar, anchoMaximo = Medida.anchoDeFicha) {
        Column(
            modifier = Modifier.desplazableConElTeclado().weight(1f, fill = false).testTag(TAG_HOJA_DE_LA_FACTURA),
        ) {
            EncabezadoDeHoja(pago.nombre)
            Spacer(Modifier.height(Medida.dentroDeSeccion))
            CifraConRotulosDeLaFactura(cifraDeLaFactura(pago, lo.extracto), VarianteDeCifra.Grande)
            Spacer(Modifier.height(Espacio.xxs))
            EstadoEnLaHoja(lo.estado)

            // ── Lo que la fila dice de más, entero ─────────────────────────────────────────────
            Column(modifier = Modifier.padding(top = Espacio.s), verticalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
                lineaDeLaCuotaAnterior(pago)?.let { Detalle(it, Modifier.testTag(TAG_CUOTA_ANTERIOR_DE_LA_FILA)) }
                // Vuelta 126: una factura que no es de cada mes lo dice («Cada 3 meses»).
                cadenciaDeLaFila(pago)?.let { Detalle(it, Modifier.testTag(TAG_CADENCIA_DE_LA_FILA)) }
                if (!pago.hecho) {
                    // Vuelta 72: la tarjeta con un pago por debajo del mínimo dice cuánto falta, entero.
                    lineaDelMinimo(pago)?.let {
                        Text(it, style = Movi.textos.apoyoMedio, color = Movi.colores.aviso, modifier = Modifier.testTag(TAG_FALTA_DEL_MINIMO))
                    }
                    lo.extracto?.let { e ->
                        alternativaDelMinimo(e)?.let { Detalle(it) }
                        if (e.puedeIncluirDiferidos) Text(PUEDE_INCLUIR_DIFERIDOS, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                    }
                } else {
                    // Vuelta 62 (revisión): un abono menor que el extracto sella la fila; el extracto sigue abierto.
                    lo.extracto?.let { Text(loQueFaltaDelExtracto(it), style = Movi.textos.apoyo, color = Movi.colores.aviso) }
                    if (!pago.pagado) Detalle(resumenDeLaFacturaPagada(pago))
                }
                lo.estimacion?.let {
                    Detalle(it)
                    Text(ESTIMADO_SOBRE_LA_DEUDA_DE_HOY, style = Movi.textos.apoyo, color = Movi.colores.textoApagado)
                }
                // Vuelta 63: el abono planeado dice que no es un fijo.
                if (pago.esAbono && !pago.hecho) Detalle(com.jvillada.movi.ui.quickadd.textoDelAbonoPendiente(pago))
                // De dónde salió el «Pagado» (lo emparejado, el sello viejo) o cómo deshacer la cuota.
                if (pago.hecho) (notaDelMenuDeLaFilaPagada(pago, lo.evidencia) ?: lo.evidencia)?.let { Detalle(it) }
            }
            // Vuelta 86: lo que no suma lo dice, con cómo arreglarlo.
            lo.noSuma?.let { razon ->
                FlowRow(
                    modifier = Modifier.padding(top = Espacio.s),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                    verticalArrangement = Arrangement.spacedBy(Espacio.xs),
                ) {
                    Box(Modifier.align(Alignment.CenterVertically).testTag(TAG_NO_SUMA)) { Pildora(pildoraDeLoQueNoSuma(razon), TonoDePildora.Aviso) }
                    if (onCargarMinimo != null) {
                        Boton(
                            accionDeLoQueNoSuma(razon),
                            onClick = cerrarY(onCargarMinimo),
                            variante = VarianteDeBoton.Fantasma,
                            tamano = TamanoDeBoton.S,
                            modifier = Modifier.align(Alignment.CenterVertically).testTag(TAG_CARGAR_MINIMO),
                        )
                    }
                }
            }

            // ── El candidato: «Sí, fue este» / «No fue este» ──────────────────────────────────
            CandidatoEnLaHoja(lo, onConfirmar = { id -> cerrarY { onConfirmar(id) }() }, onNoFueEste = { id -> cerrarY { onNoFueEste(id) }() },
                onEsLaCuota = onEsLaCuota?.let(::cerrarY), onNoEsLaCuota = onNoEsLaCuota?.let(::cerrarY))
            if (!pago.hecho && lo.propuesta == null && lo.parecido == null &&
                (pago.estado == EstadoDeLaFila.SIN_MOVIMIENTO || pago.estado == EstadoDeLaFila.CON_DUDAS)
            ) {
                // Movi buscó y no encontró: se dice.
                Detalle(textoSinMovimiento(pago), Modifier.padding(top = Espacio.s))
            }

            // ── «Anotar pago» (nunca con una propuesta cerca del monto: duplicaría el gasto) ─────
            if (!pago.hecho && !lo.conPropuesta) {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                Boton(
                    etiquetaDeAnotarLaFactura(pago),
                    onClick = cerrarY(onAnotar),
                    variante = if (principal == PrincipalDeLaHoja.ANOTAR) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
                    anchoCompleto = true,
                    habilitado = !lo.enVuelo,
                )
            }

            // ── Una pagada: ver su movimiento, quitar la marca, «No fue este» ─────────────────────
            val delPagado = accionesDeLaHojaPagada(pago).mapNotNull { accion ->
                when (accion) {
                    AccionDeLaFilaPagada.VER_MOVIMIENTO -> onVerMovimiento?.let { ver ->
                        pago.eventId?.let { id -> AccionDeOcurrencia(ETIQUETA_VER_MOVIMIENTO, primary = false, onClick = cerrarY { ver(id) }) }
                    }
                    AccionDeLaFilaPagada.QUITAR_LA_MARCA -> AccionDeOcurrencia(ETIQUETA_QUITAR_LA_MARCA, primary = false, onClick = cerrarY(onQuitarLaMarca))
                    AccionDeLaFilaPagada.NO_FUE_ESTE -> pago.eventId?.let { id ->
                        AccionDeOcurrencia(ETIQUETA_NO_FUE_ESTE, primary = false, onClick = cerrarY { onNoFueEste(id) })
                    }
                }
            }
            if (delPagado.isNotEmpty()) {
                Spacer(Modifier.height(Medida.dentroDeSeccion))
                AccionesDeLaFila(acciones = delPagado, enVuelo = lo.enVuelo)
            }

            // ── La regla: pausarla o editarla (o verla en Créditos) ───────────────────────────────
            if (onPausar != null || onAbrirLaRegla != null) {
                FlowRow(
                    modifier = Modifier.padding(top = Espacio.s),
                    horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
                    verticalArrangement = Arrangement.spacedBy(Espacio.xs),
                ) {
                    onAbrirLaRegla?.let {
                        Boton(etiquetaDeAbrir, onClick = cerrarY(it), variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S)
                    }
                    onPausar?.let {
                        Boton(ETIQUETA_PAUSAR, onClick = cerrarY(it), variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S, habilitado = !lo.enVuelo)
                    }
                }
            }
            Spacer(Modifier.height(Medida.entreSecciones))
        }
    }
}

/** Un dato de la hoja, en `apoyo` y `textoMedio`. */
@Composable
private fun Detalle(texto: String, modifier: Modifier = Modifier) {
    Text(texto, style = Movi.textos.apoyo, color = Movi.colores.textoMedio, modifier = modifier)
}

/** El renglón de estado, entero y con su tono (el mismo de la fila, sin cortar). */
@Composable
private fun EstadoEnLaHoja(estado: EstadoDeLaFactura) {
    val color = colorDelTonoDeSubtitulo(estado.tono, Movi.colores)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espacio.xxs)) {
        if (estado.conCheck) Icon(Icons.Rounded.Check, contentDescription = null, tint = color, modifier = Modifier.size(Icono.s))
        Text(estado.texto, style = Movi.textos.apoyoMedio, color = color)
    }
}

/**
 * **El candidato, en la hoja**: la propuesta del server cerca del monto («¿Ya pagaste el de octubre?», con
 * «Sí, fue este» en amarillo), la lejana («¿Es el de $260.000?», con borde: «Anotar pago» es lo principal) o
 * el gasto que se parece a la cuota («¿Es la cuota de Vehículo 4083?», con «Sí, es la cuota»).
 */
@Composable
private fun CandidatoEnLaHoja(
    lo: LoDeLaFactura,
    onConfirmar: (String) -> Unit,
    onNoFueEste: (String) -> Unit,
    onEsLaCuota: (() -> Unit)?,
    onNoEsLaCuota: (() -> Unit)?,
) {
    val pago = lo.pago
    val propuesta = lo.propuesta
    val parecido = lo.parecido
    when {
        propuesta != null -> BloqueDelCandidato(
            pregunta = if (lo.conPropuesta) preguntaDeLaFila(pago) else enlaceDeLaPropuestaLejana(propuesta),
            tag = if (lo.conPropuesta) TAG_CANDIDATO_EN_LA_HOJA else TAG_PROPUESTA_LEJANA,
        ) {
            PropuestaDeMovimiento(
                propuesta = propuesta,
                // Sin él, un movimiento de otro monto se confirmaba a ciegas.
                aviso = avisoDeMontoDistinto(pago.monto, pago.montoEsSaldo, propuesta),
                enVuelo = lo.enVuelo,
                onConfirmar = { onConfirmar(propuesta.id) },
                onDescartar = { onNoFueEste(propuesta.id) },
                confirmarEsPrincipal = lo.conPropuesta,
            )
        }
        parecido != null && onEsLaCuota != null -> BloqueDelCandidato(
            pregunta = preguntaDeLaCuotaQueSeParece(parecido.cuota),
            tag = TAG_CUOTA_QUE_SE_PARECE_EN_PAGOS,
        ) {
            PropuestaDeMovimiento(
                propuesta = parecido.gasto,
                aviso = null,
                enVuelo = lo.enVuelo,
                onConfirmar = onEsLaCuota,
                onDescartar = { onNoEsLaCuota?.invoke() },
                textoConfirmar = SI_ES_LA_CUOTA,
                confirmarEsPrincipal = true,
            )
        }
    }
}

@Composable
private fun BloqueDelCandidato(pregunta: String, tag: String, contenido: @Composable () -> Unit) {
    Spacer(Modifier.height(Medida.dentroDeSeccion))
    Superficie(modifier = Modifier.fillMaxWidth().testTag(tag), nivel = NivelDeSuperficie.Capa2) {
        Text(text = pregunta, style = Movi.textos.titulo, color = Movi.colores.texto)
        Spacer(Modifier.height(Espacio.xs))
        contenido()
    }
}
