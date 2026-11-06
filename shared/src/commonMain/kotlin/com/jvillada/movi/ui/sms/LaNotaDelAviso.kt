package com.jvillada.movi.ui.sms

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jvillada.movi.ui.components.ds.Campo

/** El campo de la nota de Revisar aviso (el texto editable). */
const val TAG_NOTA_DEL_AVISO: String = "nota-del-aviso"

/** Lo más largo que puede ser la nota de un movimiento: va después de « · » en su descripción. */
const val LARGO_MAXIMO_DE_LA_NOTA: Int = 80

/**
 * «Nota»: para qué fue el pago («parqueadero del centro»). Se guarda en la descripción del
 * movimiento después de « · » (`descripcionConLaNota`); el comercio no cambia, así que la memoria
 * sigue reconociendo el número.
 */
@Composable
fun FilaDeLaNota(
    valor: String,
    habilitada: Boolean,
    onCambio: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Campo(
        valor = valor,
        alCambiar = { onCambio(it.take(LARGO_MAXIMO_DE_LA_NOTA)) },
        modifier = modifier,
        rotulo = "Nota",
        guia = "¿Para qué fue? (opcional)",
        habilitado = habilitada,
        tag = TAG_NOTA_DEL_AVISO,
    )
}
