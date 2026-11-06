package com.jvillada.movi.ui.sms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.jvillada.movi.shared.model.rotuloDeLasCuotas
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.Boton
import com.jvillada.movi.ui.components.ds.TamanoDeBoton
import com.jvillada.movi.ui.components.ds.VarianteDeBoton
import com.jvillada.movi.ui.quickadd.FilaDeCuotas

/*
 * # Vuelta 133 · «¿A cuántas cuotas?» al confirmar un aviso de una compra con tarjeta
 *
 * El SMS, la notificación y el correo no dicen a cuántas cuotas fue la compra. Antes, la única forma de
 * decirlo era anotarla a mano en Agregar; una compra que entraba por un aviso quedaba siempre con las
 * cuotas desconocidas y el extracto la contaba entera.
 *
 * **Dónde va en la tarjeta de Por revisar, y por qué así**: un renglón fijo debajo del texto del banco,
 * «¿A cuántas cuotas? · Elegir» (o «A 12 cuotas · Cambiar»), que al tocarlo abre la MISMA fila de
 * Agregar ([FilaDeCuotas]) debajo. El renglón está desde que la tarjeta se pinta —lo decide lo que Movi
 * ya entendió, que llega junto con la tarjeta—, así que no salta nada solo; lo único que cambia de alto
 * es abrir la fila, y eso lo pide el dueño con el dedo (como «Cambiar» abre las categorías). Las
 * pastillas abiertas de entrada habrían sumado un renglón de 8 pastillas a cada compra con tarjeta de
 * la bandeja, que casi siempre se confirma tal cual.
 *
 * **No bloquea «Confirmar»**: sin elegir, la compra se guarda como hoy (cuotas desconocidas). Elegido,
 * viaja en el movimiento (`POST /api/events`, o la confirmación del pago entero), que valida 1..72.
 */

/** Lo que la tarjeta necesita para preguntar las cuotas: las elegidas (o `null`), el monto y qué hacer al elegir. */
class CuotasDelAviso(
    val elegidas: Int?,
    val monto: Long,
    val moneda: String,
    val onElegir: (Int) -> Unit,
)

/** El tag del renglón «¿A cuántas cuotas? · Elegir» de la tarjeta. */
const val TAG_CUOTAS_EN_LA_TARJETA: String = "por-revisar:cuotas"

/** El prefijo de los tags de la fila de cuotas abierta en la tarjeta. */
const val TAG_FILA_DE_CUOTAS_EN_LA_TARJETA: String = "por-revisar:cuotas:fila"

/** El tag del botón del renglón («Elegir», «Cambiar» o «Cerrar»). */
const val TAG_ELEGIR_CUOTAS_EN_LA_TARJETA: String = "por-revisar:cuotas:elegir"

/** «Elegir» sin cuotas elegidas, «Cambiar» con ellas, «Cerrar» con la fila abierta. Pura. */
fun accionDeLasCuotas(elegidas: Int?, abierta: Boolean): String = when {
    abierta -> "Cerrar"
    elegidas == null -> "Elegir"
    else -> "Cambiar"
}

/**
 * El renglón de las cuotas y, al tocarlo, la fila. Elegir una pastilla la cierra: el renglón ya dice
 * «A 12 cuotas». [margen] es el de los lados (`null` dentro de una tarjeta que ya tiene el suyo).
 */
@Composable
fun RenglonDeLasCuotas(
    cuotas: CuotasDelAviso,
    habilitado: Boolean,
    tag: String = TAG_CUOTAS_EN_LA_TARJETA,
    base: String = TAG_FILA_DE_CUOTAS_EN_LA_TARJETA,
    margen: androidx.compose.ui.unit.Dp? = null,
) {
    var abierta by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Medida.altoDeFilaDePastillas),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rotuloDeLasCuotas(cuotas.elegidas),
                style = Movi.textos.apoyo,
                color = Movi.colores.textoMedio,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).then(if (margen != null) Modifier.padding(start = margen) else Modifier),
            )
            Boton(
                accionDeLasCuotas(cuotas.elegidas, abierta),
                onClick = { abierta = !abierta },
                variante = VarianteDeBoton.Fantasma,
                tamano = TamanoDeBoton.S,
                habilitado = habilitado,
                modifier = Modifier.testTag(TAG_ELEGIR_CUOTAS_EN_LA_TARJETA),
            )
        }
        if (abierta) {
            FilaDeCuotas(
                cuotas = cuotas.elegidas,
                onCuotas = { n ->
                    cuotas.onElegir(n)
                    // «Otra» deja la fila abierta para correr el número con «−» y «+».
                    if (n in com.jvillada.movi.shared.model.CUOTAS_FRECUENTES) abierta = false
                },
                monto = cuotas.monto,
                moneda = cuotas.moneda,
                habilitada = habilitado,
                base = base,
                margen = margen,
                conPregunta = false,
            )
        }
    }
}
