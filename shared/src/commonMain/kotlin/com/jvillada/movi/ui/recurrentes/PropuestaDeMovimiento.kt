package com.jvillada.movi.ui.recurrentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.formatMoney

/**
 * # La propuesta de «¿fue este?» y sus botones
 *
 * Lo que queda de `ProximosPagosSection.kt`. Ese archivo tenía tres secciones —«Próximos», «Sin
 * confirmar» y «Ya ocurrieron»— que la ola «una sola lista» (27-sep) sacó de Plan: repetían las
 * reglas del checklist con otra forma y mezclaban meses. Lo que se queda es lo que la lista del
 * período sigue usando: la propuesta de un movimiento con «Sí, fue este» / «No fue este», y la fila
 * de botones con su única regla de «en vuelo».
 */

/**
 * **Una propuesta dibujada: qué fue, cuánto, el aviso si el monto no cuadra, y las dos salidas.**
 *
 * Es LA pieza que dibuja una propuesta, y es una sola a propósito. Nació al llevar la pregunta al
 * checklist del período: la alternativa era una segunda copia del mismo bloque, y este archivo ya
 * documenta —en su encabezado— cómo termina eso («la decisión de si una tarjeta muestra saldo o
 * cuota faltaba en uno de los cuatro renderers de un monto»). Acá el riesgo es peor, porque los
 * botones sellan un periodo con la plata del dueño adentro.
 *
 * @param aviso el texto de «no es el monto que anotaste», ya resuelto por [avisoDeMontoDistinto], o
 *   `null` si no hay nada que advertir. Lo decide quien llama porque depende de la REGLA (en una
 *   tarjeta el monto es el saldo y la comparación no significa nada) y acá solo llega el
 *   movimiento.
 */
@Composable
internal fun PropuestaDeMovimiento(
    propuesta: FinancialEvent,
    aviso: String?,
    enVuelo: Boolean,
    onConfirmar: () -> Unit,
    onDescartar: () -> Unit,
) {
    // Alineado arriba y con aire entre las dos columnas: en un teléfono angosto (390 px)
    // la descripción se envuelve en dos líneas, y con `CenterVertically` y sin separación
    // el monto quedaba pegado al texto — dos datos distintos leyéndose como uno.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = descripcionPropuesta(propuesta),
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMoney(propuesta.amount, propuesta.currency),
            style = Movi.textos.monto,
            color = Movi.colores.texto,
            lineHeight = 16.sp,
        )
    }
    if (aviso != null) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = aviso,
            style = Movi.textos.apoyo,
            color = Movi.colores.textoMedio,
            lineHeight = 15.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    AccionesDeLaFila(
        acciones = listOf(
            AccionDeOcurrencia(ETIQUETA_SI_FUE_ESTE, primary = true, onClick = onConfirmar),
            AccionDeOcurrencia(ETIQUETA_NO_FUE_ESTE, primary = false, onClick = onDescartar),
        ),
        enVuelo = enVuelo,
    )
}

/** Los rótulos que la pantalla y sus pruebas tienen que nombrar igual. */
const val ETIQUETA_SI_FUE_ESTE = "Sí, fue este"
const val ETIQUETA_NO_FUE_ESTE = "No fue este"
const val ETIQUETA_ANOTAR = "Anotar este pago"
/** Un ingreso no se paga: llega (mismo criterio que `tituloPropuesta`). */
const val ETIQUETA_ANOTAR_INGRESO = "Anotar este ingreso"

/** El rótulo de la salida «Anotar…» según sea un pago o un ingreso. */
internal fun etiquetaDeAnotar(esIngreso: Boolean): String = if (esIngreso) ETIQUETA_ANOTAR_INGRESO else ETIQUETA_ANOTAR
const val ETIQUETA_QUITAR_LA_MARCA = "Quitar la marca"

/** Una acción ofrecida sobre una ocurrencia: qué dice, si es la principal, y qué hace. */
internal data class AccionDeOcurrencia(
    val label: String,
    val primary: Boolean,
    val onClick: () -> Unit,
)

/**
 * La fila de botones de una ocurrencia, con **una sola** regla de «en vuelo» para todos.
 *
 * Mientras una escritura viaja, el botón principal dice «Guardando…» y ninguno acepta un toque. Se
 * dice una vez acá y no en cada fila: si una se olvidara de mirarlo, un doble toque mandaría dos
 * veces el mismo sello.
 */
@Composable
internal fun AccionesDeLaFila(acciones: List<AccionDeOcurrencia>, enVuelo: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        acciones.forEachIndexed { i, accion ->
            val etiqueta = if (enVuelo && i == 0) "Guardando…" else accion.label
            ActionChip(label = etiqueta, primary = accion.primary) {
                if (!enVuelo) accion.onClick()
            }
        }
    }
}

/** Los botones chicos de esta familia — «Sí, fue este», «Confirmar», «No es». */
@Composable
internal fun ActionChip(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) Movi.colores.texto else Movi.colores.tarjeta)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = Movi.textos.apoyo, fontWeight = FontWeight.Medium, color = if (primary) Movi.colores.fondo else Movi.colores.texto)
    }
}
