package com.jvillada.movi.ui.sms

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.DeDondeSaleElReparto
import com.jvillada.movi.shared.model.QueFueLaParte
import com.jvillada.movi.shared.model.RepartoDeEntrada
import com.jvillada.movi.shared.model.enumerar
import com.jvillada.movi.shared.model.nombreSinCuota
import com.jvillada.movi.ui.components.ds.TarjetaDePropuesta
import com.jvillada.movi.ui.components.formatMoney

/*
 * # Repartir un envío en Reconciliar: lo que Movi propone de entrada
 *
 * Ver `ElAvisoEnPartes.kt` y `RepartirUnEnvio.kt` en `:core`. Cuando el giro va a alguien y Movi
 * reconoce cómo se reparte —el reparto de la última vez a esa persona, la suma exacta de pagos del
 * período (#447), o las cuotas pendientes que cubre con lo que sobra—, Reconciliar lo dice arriba como
 * una propuesta: «¿Pagaste las cuotas de Libranza Papá y Hipoteca Papá? Suman $4.250.000 · sobran
 * $23.333». «Repartir así» **no confirma**: abre «Repartir en partes» ya lleno (ver
 * `RepartirEnPartes.kt`), y el dueño lo revisa, lo ajusta y toca «Confirmar las N partes».
 *
 * Todo viaja por `POST /api/sms/{id}/confirm` con `partes` (un aviso suelto) o por
 * `POST /api/sms/grupo/{id}/confirmar` con `reparto` (un pago avisado varias veces): la misma escritura
 * en el server, con el mismo bloqueo y la misma idempotencia.
 */

const val TAG_REPARTO_EN_RECONCILIAR: String = "sms:reparto"
const val REPARTIR_ASI: String = "Repartir así"

/**
 * **La pregunta de la propuesta**:
 *
 * - las cuotas con sobrante: «¿Pagaste las cuotas de Libranza Papá y Hipoteca Papá? Suman $4.250.000 · sobran $23.333»;
 * - la suma exacta: «¿Pagaste las cuotas de A y B? Suman $X» (o «¿Pagaste A y B?» si no son todas cuotas);
 * - la última vez: «¿Lo repartes como la última vez?».
 */
fun preguntaDelReparto(entrada: RepartoDeEntrada, moneda: String): String {
    if (entrada.deDonde == DeDondeSaleElReparto.LA_ULTIMA_VEZ) return "¿Lo repartes como la última vez?"
    val todasCuotas = entrada.pagos.isNotEmpty() && entrada.pagos.all { it.cuentaDeLaDeudaId != null }
    val nombres = enumerar(entrada.pagos.map { if (todasCuotas) nombreSinCuota(it.nombre) else it.nombre })
    val suman = entrada.pagos.sumOf { it.monto }
    return buildString {
        append(if (todasCuotas) "¿Pagaste las cuotas de $nombres?" else "¿Pagaste $nombres?")
        append(" Suman ${formatMoney(suman, moneda)}")
        if (entrada.sobrante > 0L) append(" · sobran ${formatMoney(entrada.sobrante, moneda)}")
    }
}

/** Lo que dice debajo: qué se va a anotar, y que nada se confirma solo. */
fun cuerpoDelReparto(entrada: RepartoDeEntrada, moneda: String): String {
    val partes = entrada.partes
    val resto = partes.lastOrNull()?.takeIf { entrada.sobrante > 0L || entrada.deDonde == DeDondeSaleElReparto.LA_ULTIMA_VEZ }
    return when (entrada.deDonde) {
        DeDondeSaleElReparto.LA_ULTIMA_VEZ ->
            "${partes.size} partes, como el reparto anterior: " +
                partes.joinToString(" · ") { "${nombreCortoDeLaParte(it.tipo, it.nombre)} ${formatMoney(it.monto, moneda)}" } +
                ". Lo revisas antes de confirmar."
        else -> buildString {
            append("Cada cuota se anota con lo suyo y su período queda marcado")
            if (entrada.sobrante > 0L && resto != null) {
                append("; lo que sobra va ")
                append(if (resto.tipo == QueFueLaParte.A_UN_TERCERO) "como ${resto.nombre}" else "como un gasto")
            }
            append(". Lo revisas antes de confirmar.")
        }
    }
}

private fun nombreCortoDeLaParte(tipo: QueFueLaParte, nombre: String?): String =
    nombre?.let { if (tipo == QueFueLaParte.CUOTA) nombreSinCuota(it) else it }?.takeIf { it.isNotBlank() }
        ?: nombreDelTipoDeParte(tipo)

/**
 * La propuesta de reparto en Reconciliar. [onRepartir] abre el editor con las partes propuestas: el
 * dueño confirma o ajusta ahí.
 */
@Composable
internal fun TarjetaDelReparto(
    entrada: RepartoDeEntrada,
    moneda: String,
    habilitado: Boolean,
    onRepartir: () -> Unit,
) {
    TarjetaDePropuesta(
        icono = Icons.AutoMirrored.Rounded.CallSplit,
        titulo = preguntaDelReparto(entrada, moneda),
        cuerpo = cuerpoDelReparto(entrada, moneda),
        accion = REPARTIR_ASI,
        onAccion = { if (habilitado) onRepartir() },
        modifier = Modifier.testTag(TAG_REPARTO_EN_RECONCILIAR),
    )
}
