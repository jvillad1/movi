package com.jvillada.movi.ui.quickadd

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CUOTAS_FRECUENTES
import com.jvillada.movi.shared.model.MAXIMO_DE_CUOTAS
import com.jvillada.movi.shared.model.cuotaDeLaCompra
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.AltoDeFiltro
import com.jvillada.movi.ui.components.ds.AltoDePildora
import com.jvillada.movi.ui.components.ds.PildoraDeAccion
import com.jvillada.movi.ui.components.ds.PildoraDeFiltro
import com.jvillada.movi.ui.components.formatMoneySinOcultar

/*
 * # Vuelta 71 · «¿A cuántas cuotas?» en Agregar
 *
 * Con una tarjeta de crédito elegida en «Gasto», debajo de la fila «Cuenta» aparece la fila de las
 * cuotas: `1 · 3 · 6 · 12 · 24 · 36 · Otra`, con 1 por defecto. Se guarda en `FinancialEvent.cuotas`.
 *
 * **No cambia cómo cuenta la compra** (sin regla nueva): entra entera hoy en los gastos, y el extracto
 * de la tarjeta (`extractoDeLaTarjeta`) cuenta la cuota del mes. La línea de ayuda lo dice.
 */

/** El rótulo de la fila. */
const val PREGUNTA_DE_LAS_CUOTAS: String = "¿A cuántas cuotas?"

/** La pastilla que deja elegir un número que no está en [CUOTAS_FRECUENTES]. */
const val OTRA_CANTIDAD_DE_CUOTAS: String = "Otra"

/** Con «Otra», el número con que arranca: el primero que no está entre las frecuentes. */
internal const val CUOTAS_AL_ELEGIR_OTRA: Int = 2

/** El `testTag` de la fila de cuotas. */
const val TAG_FILA_DE_CUOTAS: String = "agregar:cuotas"

/** El `testTag` de la línea de ayuda de las cuotas. */
const val TAG_AYUDA_DE_LAS_CUOTAS: String = "agregar:cuotas:ayuda"

/** El `testTag` de la pastilla de [cuotas] (o de «Otra» con `null`). */
fun tagDeLaPastillaDeCuotas(cuotas: Int?): String = "agregar:cuotas:${cuotas ?: "otra"}"

/** ¿La fila de cuotas va en la hoja? Solo en «Gasto» (no en modo pago) y con una tarjeta de crédito. */
internal fun preguntaLasCuotas(tipoDeLaCuenta: AccountType?, esGasto: Boolean, enModoPago: Boolean): Boolean =
    esGasto && !enModoPago && tipoDeLaCuenta == AccountType.CREDIT_CARD

/** «≈ $100.000 al mes, sin intereses», o `null` con una sola cuota o sin monto. */
internal fun lineaDeLaCuotaDelMes(monto: Long, cuotas: Int, moneda: String): String? =
    if (cuotas <= 1 || monto <= 0L) null
    else "≈ ${formatMoneySinOcultar(cuotaDeLaCompra(monto, cuotas), moneda)} al mes, sin intereses"

/**
 * **La ayuda de las cuotas**: que la compra cuenta entera hoy y qué va al extracto. Es la regla de
 * siempre dicha en voz alta, no una nueva.
 */
internal fun ayudaDeLasCuotas(monto: Long, cuotas: Int, moneda: String): String = when {
    cuotas <= 1 -> "Cuenta completa hoy en tus gastos y en el extracto de la tarjeta."
    monto <= 0L -> "Cuenta completa hoy en tus gastos; en el extracto de la tarjeta va una cuota por mes."
    else -> "Cuenta completa hoy en tus gastos; en el extracto de la tarjeta va " +
        "${formatMoneySinOcultar(cuotaDeLaCompra(monto, cuotas), moneda)} por mes."
}

/**
 * La fila «¿A cuántas cuotas?» de Agregar, dentro de la tarjeta de la hoja (después de «Cuenta»). Las
 * pastillas van en un renglón que se desliza, y con «Otra» elegida se corre con «−» y «+» en el mismo
 * renglón: elegir no cambia el alto de la hoja. La ayuda reserva dos renglones por lo mismo.
 */
@Composable
internal fun FilaDeCuotas(
    cuotas: Int,
    onCuotas: (Int) -> Unit,
    monto: Long,
    moneda: String,
    habilitada: Boolean = true,
) {
    val esOtra = cuotas !in CUOTAS_FRECUENTES
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_FILA_DE_CUOTAS)) {
        Text(
            PREGUNTA_DE_LAS_CUOTAS,
            style = Movi.textos.cuerpo,
            color = Movi.colores.texto,
            modifier = Modifier.padding(horizontal = Medida.margenDePantalla, vertical = Espacio.xs),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Medida.altoDeFilaDePastillas)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Medida.margenDePantalla),
            horizontalArrangement = Arrangement.spacedBy(Espacio.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CUOTAS_FRECUENTES.forEach { n ->
                PildoraDeFiltro(
                    texto = n.toString(),
                    seleccionada = cuotas == n,
                    onClick = { if (habilitada) onCuotas(n) },
                    alto = AltoDeFiltro.Compacto,
                    modifier = Modifier.testTag(tagDeLaPastillaDeCuotas(n)),
                )
            }
            PildoraDeFiltro(
                texto = if (esOtra) "$OTRA_CANTIDAD_DE_CUOTAS · $cuotas" else OTRA_CANTIDAD_DE_CUOTAS,
                seleccionada = esOtra,
                onClick = { if (habilitada && !esOtra) onCuotas(CUOTAS_AL_ELEGIR_OTRA) },
                alto = AltoDeFiltro.Compacto,
                modifier = Modifier.testTag(tagDeLaPastillaDeCuotas(null)),
            )
            if (esOtra) {
                PildoraDeAccion(
                    texto = "−",
                    descripcion = "Una cuota menos",
                    onClick = { onCuotas((cuotas - 1).coerceAtLeast(CUOTAS_AL_ELEGIR_OTRA)) },
                    habilitada = habilitada && cuotas > CUOTAS_AL_ELEGIR_OTRA,
                    alto = AltoDePildora.Compacta,
                )
                PildoraDeAccion(
                    texto = "+",
                    descripcion = "Una cuota más",
                    onClick = { onCuotas((cuotas + 1).coerceAtMost(MAXIMO_DE_CUOTAS)) },
                    habilitada = habilitada && cuotas < MAXIMO_DE_CUOTAS,
                    alto = AltoDePildora.Compacta,
                )
            }
        }
        val delMes = lineaDeLaCuotaDelMes(monto, cuotas, moneda)
        Text(
            text = listOfNotNull(delMes, ayudaDeLasCuotas(monto, cuotas, moneda)).joinToString("\n"),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            minLines = 3,
            maxLines = 3,
            modifier = Modifier
                .padding(start = Medida.margenDePantalla, end = Medida.margenDePantalla, top = Espacio.xxs, bottom = Espacio.xs)
                .testTag(TAG_AYUDA_DE_LAS_CUOTAS),
        )
    }
}
