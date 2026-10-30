package com.jvillada.movi.ui.recurrentes

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
 * nada y se deshace con un toque.
 */
@Composable
internal fun LaPausaDeLaRegla(regla: RecurringRule, ocupado: Boolean, onCambiar: (pausar: Boolean) -> Unit) {
    val pausada = !regla.pausadaDesde.isNullOrBlank()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Espacio.xs)) {
        Text(
            if (pausada) queHaceReanudar(regla) else QUE_HACE_PAUSAR,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
        )
        Boton(
            if (pausada) ETIQUETA_REANUDAR else ETIQUETA_PAUSAR,
            onClick = { onCambiar(!pausada) },
            variante = VarianteDeBoton.Secundario,
            tamano = TamanoDeBoton.S,
            habilitado = !ocupado,
            modifier = Modifier.testTag(if (pausada) TAG_REANUDAR_LA_REGLA else TAG_PAUSAR_LA_REGLA),
        )
    }
}

// ── «+ Agregar» y «Pausados (N)» en Pagos ─────────────────────────────────────

/** Vuelta 72 · la acción del encabezado de «Tus facturas del período». */
const val AGREGAR_UN_PAGO: String = "+ Agregar"

/** «Pausados (2)»: el título de la sección plegada. Pura. */
fun tituloDeLosPausados(cuantos: Int): String = "Pausados ($cuantos)"

const val TAG_SECCION_DE_PAUSADOS: String = "pagos:pausados"
const val TAG_FILA_PAUSADA: String = "pagos:fila-pausada"

/** El subtítulo de una regla pausada en la lista: «Pausado desde el 29 oct · $180.000». Pura. */
fun subtituloDeLaPausada(regla: RecurringRule): String =
    "${textoDePausadaDesde(regla)} · ${formatMoney(regla.amount, regla.currency)}"

/**
 * **«Pausados (N)»** (vuelta 72): al final de Pagos, **plegada** —lo pausado no compite con lo que hay
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
            titulo = tituloDeLosPausados(pausadas.size),
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
 * de verdad (ni la cuota de un crédito, ni el pago de una tarjeta, ni un abono) y la cuenta se conoce
 * ([nombres]). Es una propuesta: nunca se guarda sola. Pura.
 */
fun cuentaParaGuardarEnLaRegla(pago: PagoDelPeriodo, nombres: Map<String, String>): String? {
    if (!pago.pagado || !pago.cuentaId.isNullOrBlank() || pago.esAbono || pago.montoEsSaldo) return null
    if (deudaDeLaRegla(pago.ruleId) != null) return null
    return pago.cuentaDelPago?.takeIf { it.isNotBlank() && it in nombres }
}

/** «¿Usar siempre Nu para Carro?». Pura. */
fun preguntaDeLaCuentaDeLaRegla(cuenta: String, regla: String): String = "¿Usar siempre $cuenta para $regla?"

/** La clave con que se recuerda un «Ahora no» de esa propuesta: por regla y cuenta. */
fun claveDeLaCuentaDeLaRegla(ruleId: String, cuentaId: String): String = "cuenta-de-la-regla:$ruleId:$cuentaId"

const val GUARDAR_EN_LA_REGLA: String = "Guardar en la regla"
const val TAG_CUENTA_DE_LA_REGLA: String = "pagos:cuenta-de-la-regla"

/**
 * La propuesta, colgando de la fila pagada: la pregunta y dos botones chicos —«Guardar en la regla»
 * (`Secundario`) y «Ahora no» (`Fantasma`)—. Sin amarillo: no es lo que la pantalla pide hacer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PropuestaDeLaCuentaDeLaRegla(
    pregunta: String,
    enVuelo: Boolean,
    onGuardar: () -> Unit,
    onAhoraNo: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TAG_CUENTA_DE_LA_REGLA),
        verticalArrangement = Arrangement.spacedBy(Espacio.xs),
    ) {
        Text(pregunta, style = Movi.textos.apoyo, color = Movi.colores.textoMedio)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Espacio.xs)) {
            Boton(GUARDAR_EN_LA_REGLA, onClick = onGuardar, variante = VarianteDeBoton.Secundario, tamano = TamanoDeBoton.S, habilitado = !enVuelo)
            Boton("Ahora no", onClick = onAhoraNo, variante = VarianteDeBoton.Fantasma, tamano = TamanoDeBoton.S, habilitado = !enVuelo)
        }
    }
}
