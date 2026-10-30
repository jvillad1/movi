package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.ui.components.ds.TonoDeIcono
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import kotlinx.datetime.LocalDate

/*
 * # Vuelta 72 · pausar un pago fijo
 *
 * La revisión 4 del 29-oct: una regla solo se podía eliminar. Pausar la saca de todo lo que mira adelante
 * —la lista del período, «Falta por pagar», la caja, los fijos del Disponible, los avisos, los débitos y
 * Movi AI— desde hoy, sin borrar nada: lo ya pagado queda y «Reanudar» la devuelve como estaba. La
 * regla de qué cuenta vive en `:core` (`pausadaParaElVencimiento`); acá solo se dice.
 */

const val ETIQUETA_PAUSAR: String = "Pausar"
const val ETIQUETA_REANUDAR: String = "Reanudar"
const val TAG_PAUSAR_LA_REGLA: String = "regla:pausar"
const val TAG_REANUDAR_LA_REGLA: String = "regla:reanudar"

/** Lo que dice la hoja antes de pausar. */
const val QUE_HACE_PAUSAR: String =
    "¿No lo pagas por ahora? Pausado deja de contar y de avisarte desde hoy. Lo que ya pagaste queda."

/** «Pausado desde el 29 oct»; sin una fecha legible, «Pausado». Pura. */
fun textoDePausadaDesde(regla: RecurringRule): String {
    val fecha = regla.pausadaDesde?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
        ?: return "Pausado"
    return "Pausado desde el ${fechaCortaDeFactura(fecha.toString())}"
}

/** Lo que dice la hoja de una regla pausada. Pura. */
fun queHaceReanudar(regla: RecurringRule): String =
    "${textoDePausadaDesde(regla)}: no cuenta en tus pagos ni te avisa. Reanudar lo devuelve como estaba."

/**
 * **La sección «Pausar» de la hoja de una regla**: una frase y el botón (`Secundario`, `S`). Pausada, la
 * frase dice desde cuándo y el botón es «Reanudar». No es destructiva —no se pregunta antes—: no borra
 * nada y se deshace con un toque. **Con algo vencido sin pagar** ([vencido], revisión de la vuelta 72),
 * «Pausar» primero pregunta qué hacer con eso ([PreguntaDeLoVencidoAlPausar]).
 *
 * @param onCambiar pausar (con su fecha: `null` = hoy) o reanudar; [despues] corre al terminar bien.
 */
@Composable
internal fun LaPausaDeLaRegla(
    regla: RecurringRule,
    ocupado: Boolean,
    onCambiar: (pausar: Boolean, desde: String?, despues: (() -> Unit)?) -> Unit,
    vencido: PagoDelPeriodo? = null,
    onYaLoPague: ((PagoDelPeriodo) -> Unit)? = null,
) {
    val pausada = !regla.pausadaDesde.isNullOrBlank()
    var preguntando by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        if (preguntando && vencido != null && !pausada) {
            PreguntaDeLoVencidoAlPausar(
                vencido = vencido,
                ocupado = ocupado,
                onDejarPendiente = { onCambiar(true, null, null) },
                onYaLoPague = { onCambiar(true, null) { onYaLoPague?.invoke(vencido) } },
                onNoSeDebe = { onCambiar(true, vencido.vence, null) },
                pendienteEsPrincipal = false,
            )
            return@Column
        }
        Text(
            if (pausada) queHaceReanudar(regla) else QUE_HACE_PAUSAR,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Boton(
            if (pausada) ETIQUETA_REANUDAR else ETIQUETA_PAUSAR,
            onClick = {
                when {
                    pausada -> onCambiar(false, null, null)
                    vencido != null -> preguntando = true
                    else -> onCambiar(true, null, null)
                }
            },
            variante = VarianteDeBoton.Secundario,
            tamano = TamanoDeBoton.S,
            habilitado = !ocupado,
            modifier = Modifier.testTag(if (pausada) TAG_REANUDAR_LA_REGLA else TAG_PAUSAR_LA_REGLA),
        )
    }
}

// ── Lo vencido sin pagar al pausar (revisión de la vuelta 72) ─────────────────

/** «Pausar «Carro»»: el título de la hoja que pregunta desde el «⋯» de la fila. */
fun tituloDePausar(nombre: String): String = "Pausar «$nombre»"

/** «Carro de octubre ($9.000) no aparece pagado». Pura. */
fun preguntaDeLoVencido(vencido: PagoDelPeriodo): String {
    val mes = nombreDelMes(vencido.periodoDelDueno ?: vencido.vence)
    val nombre = if (mes.isBlank()) vencido.nombre else "${vencido.nombre} de $mes"
    val monto = if (vencido.montoEsSaldo) null else formatMoney(vencido.monto, vencido.moneda)
    return if (monto == null) "$nombre no aparece pagado" else "$nombre ($monto) no aparece pagado"
}

const val DEJARLO_PENDIENTE: String = "Dejarlo pendiente"
const val YA_LO_PAGUE: String = "Ya lo pagué"
const val NO_SE_DEBE: String = "No se debe"
const val TAG_PREGUNTA_DE_LO_VENCIDO: String = "regla:pregunta-de-lo-vencido"

/** Lo que dice debajo de la pregunta: qué hace cada salida. */
const val QUE_HACE_CADA_SALIDA: String =
    "La pausa rige desde hoy. «Dejarlo pendiente» lo deja en Pagos hasta que lo anotes; «No se debe» lo saca."

/**
 * **Lo vencido sin pagar, al pausar** (revisión de la vuelta 72): en un hogar, esconderlo puede tapar una
 * deuda real, así que la pausa rige desde el próximo vencimiento y esto pregunta: «Dejarlo pendiente»
 * (`Principal`, lo de por defecto: sigue en Pagos), «Ya lo pagué» (pausa y abre «Anotar pago», que salda
 * esa fila) y «No se debe» (la pausa arranca en ese vencimiento: sale de la lista).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreguntaDeLoVencidoAlPausar(
    vencido: PagoDelPeriodo,
    ocupado: Boolean,
    onDejarPendiente: () -> Unit,
    onYaLoPague: () -> Unit,
    onNoSeDebe: () -> Unit,
    /** En una hoja que ya tiene su botón principal (la de la regla), «Dejarlo pendiente» va con borde. */
    pendienteEsPrincipal: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TAG_PREGUNTA_DE_LO_VENCIDO),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Text(preguntaDeLoVencido(vencido), style = Movi.textos.cuerpoFuerte, color = Movi.colores.texto)
        Text(QUE_HACE_CADA_SALIDA, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Espacio.xs), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Boton(
                DEJARLO_PENDIENTE, onClick = onDejarPendiente,
                variante = if (pendienteEsPrincipal) VarianteDeBoton.Principal else VarianteDeBoton.Secundario,
                tamano = TamanoDeBoton.S, habilitado = !ocupado,
            )
            Boton(YA_LO_PAGUE, onClick = onYaLoPague, variante = VarianteDeBoton.Secundario, tamano = TamanoDeBoton.S, habilitado = !ocupado)
            Boton(NO_SE_DEBE, onClick = onNoSeDebe, variante = VarianteDeBoton.Secundario, tamano = TamanoDeBoton.S, habilitado = !ocupado)
        }
    }
}

// ── «+ Agregar» y «Pausados · N» en Pagos ─────────────────────────────────────

/** Vuelta 72 · la acción del encabezado de «Tus facturas del período». */
const val AGREGAR_UN_PAGO: String = "+ Agregar"

/** El título de la sección plegada; el conteo va como en el resto del sistema («Pausados · 2»). */
const val TITULO_DE_LOS_PAUSADOS: String = "Pausados"

const val TAG_SECCION_DE_PAUSADOS: String = "pagos:pausados"
const val TAG_FILA_PAUSADA: String = "pagos:fila-pausada"

/** El subtítulo de una regla pausada en la lista: «Pausado desde el 29 oct · $180.000». Pura. */
fun subtituloDeLaPausada(regla: RecurringRule): String =
    "${textoDePausadaDesde(regla)} · ${formatMoney(regla.amount, regla.currency)}"

/**
 * **«Pausados · N»** (vuelta 72): al final de Pagos, **plegada** —lo pausado no compite con lo que hay
 * que pagar—, con «Ver» / «Ocultar» en el encabezado. Cada regla dice desde cuándo está pausada y ofrece
 * «Reanudar»; tocarla abre su hoja.
 */
@Composable
internal fun SeccionDePausados(
    pausadas: List<RecurringRule>,
    enVuelo: Set<String>,
    onReanudar: ((RecurringRule) -> Unit)?,
    onAbrir: ((RecurringRule) -> Unit)?,
) {
    var abiertos by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_SECCION_DE_PAUSADOS)) {
        Spacer(Modifier.height(Medida.entreSecciones))
        Seccion(
            titulo = TITULO_DE_LOS_PAUSADOS,
            conteo = pausadas.size,
            accion = if (abiertos) "Ocultar" else "Ver",
            onAccion = { abiertos = !abiertos },
        )
        if (abiertos) {
            Superficie(
                modifier = Modifier.fillMaxWidth(),
                relleno = RellenoDeSuperficie.Lista,
            ) {
                pausadas.forEachIndexed { i, regla ->
                    if (i > 0) Separador(SangriaDeSeparador.Ninguna)
                    Fila(
                        titulo = regla.name,
                        subtitulo = subtituloDeLaPausada(regla),
                        modifier = Modifier.testTag(TAG_FILA_PAUSADA),
                        final = onReanudar?.let { reanudar ->
                            {
                                Boton(
                                    ETIQUETA_REANUDAR,
                                    onClick = { reanudar(regla) },
                                    variante = VarianteDeBoton.Secundario,
                                    tamano = TamanoDeBoton.S,
                                    habilitado = regla.id !in enVuelo,
                                )
                            }
                        },
                        onClick = onAbrir?.let { abrir -> { abrir(regla) } },
                        onClickLabel = "Abrir ${regla.name}",
                    )
                }
            }
        }
    }
}

// ── «¿Usar siempre Nu para Carro?» ───────────────────────────────────────────

/**
 * Vuelta 72 · **la cuenta que se propone guardar en la regla** de una factura pagada, o `null`: la del
 * movimiento que la pagó ([PagoDelPeriodo.cuentaDelPago]), solo si la regla no tiene cuenta, es una regla
 * de verdad (ni la cuota de un crédito, ni el pago de una tarjeta, ni un abono), no está pausada y la cuenta se conoce
 * ([nombres]). Es una propuesta: nunca se guarda sola. Pura.
 */
fun cuentaParaGuardarEnLaRegla(pago: PagoDelPeriodo, nombres: Map<String, String>): String? {
    if (!pago.pagado || pago.pausada || !pago.cuentaId.isNullOrBlank() || pago.esAbono || pago.montoEsSaldo) return null
    if (deudaDeLaRegla(pago.ruleId) != null) return null
    return pago.cuentaDelPago?.takeIf { it.isNotBlank() && it in nombres }
}

/** «¿Usar siempre Nu para Carro?». Pura. */
fun preguntaDeLaCuentaDeLaRegla(cuenta: String, regla: String): String = "¿Usar siempre $cuenta para $regla?"

/** La clave con que se recuerda un «Ahora no» de esa propuesta: por regla y cuenta. */
fun claveDeLaCuentaDeLaRegla(ruleId: String, cuentaId: String): String = "cuenta-de-la-regla:$ruleId:$cuentaId"

const val GUARDAR_EN_LA_REGLA: String = "Guardar en la regla"
const val TAG_CUENTA_DE_LA_REGLA: String = "pagos:cuenta-de-la-regla"

/** Una propuesta «¿Usar siempre Nu para Carro?», lista para pintar (vuelta 80). */
internal data class PropuestaDeCuentaDeLaRegla(
    val pregunta: String,
    val enVuelo: Boolean,
    val onGuardar: () -> Unit,
    val onAhoraNo: () -> Unit,
)

/**
 * **Las propuestas de cuenta, arriba de la lista** (vuelta 80): cada una es una [TarjetaDePropuesta]
 * —«¿Usar siempre Bancolombia Ahorros 4057 para Arriendo?» con «Guardar en la regla» y «Ahora no»—.
 * Iba metida dentro de la fila pagada, con dos botones (`r7-pagos-390-a.png`, `-b.png`): una fila
 * resuelta se leía como una pendiente. Nunca se guarda sola.
 */
@Composable
internal fun PropuestasDeLaCuentaDeLaRegla(propuestas: List<PropuestaDeCuentaDeLaRegla>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Medida.dentroDeSeccion),
    ) {
        propuestas.forEach { p ->
            Column(modifier = Modifier.fillMaxWidth().testTag(TAG_CUENTA_DE_LA_REGLA)) {
                TarjetaDePropuesta(
                    icono = Icons.Rounded.AccountBalanceWallet,
                    titulo = p.pregunta,
                    accion = GUARDAR_EN_LA_REGLA,
                    onAccion = { if (!p.enVuelo) p.onGuardar() },
                    // Vuelta 82: el default pasó a secundario; Pagos queda como estaba (lo decide la vuelta 83).
                    varianteDeLaAccion = com.jvillada.movi.ui.components.ds.VarianteDeBoton.Principal,
                    // Neutro: el círculo amarillo es solo de las dos propuestas de la maqueta.
                    tono = TonoDeIcono.Neutro,
                    accionSecundaria = "Ahora no",
                    onAccionSecundaria = { if (!p.enVuelo) p.onAhoraNo() },
                )
            }
        }
    }
}
