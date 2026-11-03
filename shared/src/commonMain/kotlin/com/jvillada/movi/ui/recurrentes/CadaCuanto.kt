package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.EN_DOLARES_SOLO_MES_O_ANIO
import com.jvillada.movi.shared.model.FRECUENCIAS_DEL_PAGO
import com.jvillada.movi.shared.model.FrecuenciaDelPago
import com.jvillada.movi.shared.model.PagoDelPeriodo
import com.jvillada.movi.shared.model.RecurringRule
import com.jvillada.movi.shared.model.UpcomingPayment
import com.jvillada.movi.shared.model.cadenciaDelPago
import com.jvillada.movi.shared.model.epochDeFecha
import com.jvillada.movi.shared.model.esMensual
import com.jvillada.movi.shared.model.nombreDeLaFrecuencia
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.ds.RotuloDeCampo
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.fecha.SelectorDeFecha
import androidx.compose.runtime.saveable.rememberSaveable
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.ui.components.ds.Fila
import com.jvillada.movi.ui.components.ds.RellenoDeSuperficie
import com.jvillada.movi.ui.components.ds.SangriaDeSeparador
import com.jvillada.movi.ui.components.ds.Seccion
import com.jvillada.movi.ui.components.ds.Separador
import com.jvillada.movi.ui.components.ds.Superficie
import com.jvillada.movi.ui.components.formatMoney
import kotlinx.datetime.LocalDate

/*
 * # Vuelta 126 · «¿Cada cuánto?» en la hoja de un pago fijo, y «Otros pagos fijos» en Pagos
 *
 * La hoja pregunta cada cuánto con seis pastillas (Cada mes · Cada 2 meses · Cada 3 meses · Cada 6 meses ·
 * Cada año · Una sola vez) y, si no es cada mes, la fecha del próximo pago con el selector de fecha de la
 * app; el día del mes sale de esa fecha. Las reglas de cuándo vence viven en `:core`
 * (`FrecuenciaDelPago.kt`); acá solo se pinta.
 */

/** El rótulo de la pregunta. */
const val CADA_CUANTO: String = "¿Cada cuánto?"

/** El tag de la pastilla de cada frecuencia. */
fun tagDeLaFrecuencia(frecuencia: FrecuenciaDelPago): String = "regla:frecuencia:${frecuencia.name}"

/** El tag de la fila de la fecha y el de la línea de dólares. */
const val TAG_FECHA_DEL_PAGO: String = "regla:fecha-del-pago"
const val TAG_SOLO_MES_O_ANIO: String = "regla:solo-mes-o-anio"

/** «¿Cuándo es el próximo?», o «¿Cuándo es?» para una sola vez. Pura. */
fun rotuloDeLaFechaDelPago(frecuencia: FrecuenciaDelPago): String =
    if (frecuencia == FrecuenciaDelPago.UNA_VEZ) "¿Cuándo es?" else "¿Cuándo es el próximo?"

private val MESES_LARGOS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** «15 de diciembre de 2026», o «Elige la fecha» sin una. Pura. */
fun textoDeLaFechaDelPago(fecha: LocalDate?): String =
    fecha?.let { "${it.dayOfMonth} de ${MESES_LARGOS[it.monthNumber - 1]} de ${it.year}" } ?: "Elige la fecha"

/**
 * **Las seis pastillas de «¿Cada cuánto?»** ([opciones]: editando una suscripción, solo cada mes y cada año).
 * Bajan a renglones cuando no caben (a 360 dp con la letra grande): una pastilla nunca se corta. En dólares
 * ([soloMesOAnio] con otra elegida) lo dice en una línea debajo: la suscripción no lo modela.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectorDeCadaCuanto(
    frecuencia: FrecuenciaDelPago,
    onElegir: (FrecuenciaDelPago) -> Unit,
    habilitado: Boolean,
    opciones: List<FrecuenciaDelPago> = FRECUENCIAS_DEL_PAGO,
    soloMesOAnio: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        RotuloDeCampo(CADA_CUANTO)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            verticalArrangement = Arrangement.spacedBy(Espacio.xs),
        ) {
            opciones.forEach { f ->
                PildoraDeFiltro(
                    nombreDeLaFrecuencia(f),
                    seleccionada = frecuencia == f,
                    onClick = { if (habilitado && frecuencia != f) onElegir(f) },
                    modifier = Modifier.testTag(tagDeLaFrecuencia(f)),
                )
            }
        }
        if (soloMesOAnio && frecuencia != FrecuenciaDelPago.MENSUAL && frecuencia != FrecuenciaDelPago.ANUAL) {
            Spacer(Modifier.height(Espacio.xs))
            Text(
                EN_DOLARES_SOLO_MES_O_ANIO,
                style = Movi.textos.apoyoMedio,
                color = Movi.colores.aviso,
                modifier = Modifier.testTag(TAG_SOLO_MES_O_ANIO),
            )
        }
    }
}

/**
 * **La fecha del próximo pago** («¿Cuándo es el próximo?» / «¿Cuándo es?»): la elegida y «Elegir», que abre
 * el selector de fecha de la app hacia adelante, desde [desde] (el arranque del período en curso: una fecha
 * anterior no tendría vencimiento que contar).
 */
@Composable
internal fun CampoDeLaFechaDelPago(
    frecuencia: FrecuenciaDelPago,
    fecha: LocalDate?,
    hoy: LocalDate,
    desde: LocalDate,
    onElegir: (LocalDate) -> Unit,
    habilitado: Boolean,
) {
    var eligiendo by remember { mutableStateOf(fecha == null) }
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_FECHA_DEL_PAGO)) {
        RotuloDeCampo(rotuloDeLaFechaDelPago(frecuencia))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                textoDeLaFechaDelPago(fecha),
                style = Movi.textos.cuerpo,
                color = if (fecha == null) Movi.colores.textoMedio else Movi.colores.texto,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Espacio.xs))
            Boton(
                if (eligiendo) "Listo" else "Elegir",
                onClick = { eligiendo = !eligiendo },
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
                habilitado = habilitado,
            )
        }
        if (eligiendo) {
            Spacer(Modifier.height(Espacio.s))
            SelectorDeFecha(
                seleccionada = fecha ?: maxOf(hoy, desde),
                hoy = hoy,
                onPick = { onElegir(it); eligiendo = false },
                enabled = habilitado,
                haciaAdelante = true,
                desde = desde,
            )
        }
    }
}

// ── En Pagos ──────────────────────────────────────────────────────────────────

/** La cadencia de una factura no mensual del período: «Cada 3 meses». `null` en una mensual. Pura. */
fun cadenciaDeLaFila(pago: PagoDelPeriodo): String? =
    pago.frecuencia.takeIf { it != FrecuenciaDelPago.MENSUAL }?.let(::nombreDeLaFrecuencia)

/** El título de la sección plegada del final de Pagos. */
const val TITULO_DE_OTROS_PAGOS_FIJOS: String = "Otros pagos fijos"
const val TAG_SECCION_DE_OTROS_PAGOS_FIJOS: String = "pagos:otros-pagos-fijos"
const val TAG_FILA_DE_OTRO_PAGO_FIJO: String = "pagos:otro-pago-fijo"

/**
 * **Lo que va en «Otros pagos fijos · N»**: las reglas no mensuales cuyo próximo vencimiento cae **después**
 * de este período ([finDelPeriodo], epoch-ms exclusivo) — las que este período no cobra, para que el dueño
 * sepa que existen. Lo que vence en este período está en la lista; lo de una sola vez vencido y sin pagar,
 * en «Del período de …». Por fecha. Pura.
 */
fun otrosPagosFijos(upcoming: List<UpcomingPayment>, finDelPeriodo: Long): List<UpcomingPayment> =
    upcoming
        .filter { !it.rule.esMensual && epochDeFecha(it.dueDate) >= finDelPeriodo }
        .sortedWith(compareBy<UpcomingPayment> { it.dueDate }.thenBy { it.rule.name.lowercase() })

/** El subtítulo de una fila de «Otros pagos fijos»: «Cada 3 meses · el próximo, 15 dic · $450.000». Pura. */
fun subtituloDeOtroPagoFijo(pago: UpcomingPayment): String =
    listOfNotNull(
        cadenciaDelPago(pago.rule, pago.dueDate),
        formatMoney(pago.rule.amount, pago.rule.currency),
    ).joinToString(" · ")

/** La regla de una fila de «Otros pagos fijos», para abrir su hoja. */
internal fun reglaDeOtroPagoFijo(pago: UpcomingPayment): RecurringRule = pago.rule

/**
 * **«Otros pagos fijos · N»** (vuelta 126): al final de Pagos, **plegada** como «Pausados · N». Las reglas que
 * no vencen en este período, cada una con su cadencia y su próximo vencimiento; tocarla abre su hoja. No suman
 * en nada de este período.
 */
@Composable
internal fun SeccionDeOtrosPagosFijos(
    pagos: List<UpcomingPayment>,
    onAbrir: ((RecurringRule) -> Unit)?,
) {
    var abiertos by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_SECCION_DE_OTROS_PAGOS_FIJOS)) {
        Spacer(Modifier.height(Medida.entreSecciones))
        Seccion(
            titulo = TITULO_DE_OTROS_PAGOS_FIJOS,
            conteo = pagos.size,
            accion = if (abiertos) "Ocultar" else "Ver",
            onAccion = { abiertos = !abiertos },
        )
        if (abiertos) {
            Superficie(
                modifier = Modifier.fillMaxWidth(),
                relleno = RellenoDeSuperficie.Lista,
            ) {
                pagos.forEachIndexed { i, pago ->
                    if (i > 0) Separador(SangriaDeSeparador.Ninguna)
                    Fila(
                        titulo = pago.rule.name,
                        subtitulo = subtituloDeOtroPagoFijo(pago),
                        lineasDelSubtitulo = 2,
                        modifier = Modifier.testTag(TAG_FILA_DE_OTRO_PAGO_FIJO),
                        onClick = onAbrir?.let { abrir -> { abrir(reglaDeOtroPagoFijo(pago)) } },
                        onClickLabel = "Abrir ${pago.rule.name}",
                    )
                }
            }
        }
    }
}
