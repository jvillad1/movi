package com.jvillada.movi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.shared.model.UsoDeCuenta
import com.jvillada.movi.theme.MinTextFaint
import com.jvillada.movi.theme.MinTextMute

/**
 * **El pie de todo selector de cuentas: «Ver todas las cuentas».**
 *
 * `cuentasPara` parte la lista en las que sirven y el resto; esta es la fila que destraba el resto.
 * Explica por qué esas no estaban arriba, y lo explica **en función de lo que se está haciendo** —
 * de un gasto la plata sale, a un ingreso entra— porque «no aplica» no le dice nada a nadie. No
 * bloquea: se abre y se elige.
 *
 * ## Por qué es un componente y no dos copias
 *
 * Nació dentro del selector de la hoja de «Agregar», y la hoja del recurrente se escribió su
 * propia versión: una fila que **no se podía volver a plegar**, sin la línea que explica el
 * porqué, y sin el caso de «arriba no quedó ninguna». O sea el mismo defecto que esta ola vino a
 * cerrar —una regla copiada en vez de compartida— un nivel más arriba, en el pie de la regla.
 * Ahora las dos pantallas dicen lo mismo porque es el mismo código.
 *
 * @param expandido si las «otras» ya están a la vista.
 * @param cuantas cuántas hay del otro lado. Va en el texto a propósito: dice cuánto hay antes de
 *   abrirlo.
 * @param uso para qué se está eligiendo la cuenta; decide cuál de las dos explicaciones va.
 * @param modifier lo pone cada pantalla: la hoja de «Agregar» dibuja sus filas a ras del borde,
 *   la del recurrente las sangra 14 dp. Es lo único que las diferencia.
 */
@Composable
fun VerTodasLasCuentas(
    expandido: Boolean,
    cuantas: Int,
    uso: UsoDeCuenta,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expandido) "Ver solo las de siempre" else "Ver todas las cuentas ($cuantas más)",
                fontSize = 14.sp,
                color = MinTextMute,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expandido) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MinTextMute,
                modifier = Modifier.size(18.dp),
            )
        }
        if (expandido) {
            Text(
                text = explicacionDeLasOtrasCuentas(uso),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = MinTextFaint,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

/**
 * Por qué estas cuentas no estaban arriba, dicho para [uso].
 *
 * `when` exhaustivo y sin `else`, por lo mismo que `sirvePara`: el día que aparezca un uso más,
 * esto no compila hasta que alguien escriba su frase. Un `else` la habría contestado en silencio
 * —con la frase del gasto— en el archivo que predica en contra de los `else`.
 */
private fun explicacionDeLasOtrasCuentas(uso: UsoDeCuenta): String = when (uso) {
    UsoDeCuenta.DESTINO_DE_INGRESO ->
        "Estas no suelen recibir un ingreso: lo que entra a una tarjeta o a un crédito es un " +
            "pago o un desembolso. Puedes elegirlas igual."
    UsoDeCuenta.ORIGEN_DE_GASTO ->
        "De estas no suele salir un gasto: un crédito ya desembolsado se mueve con su cuota, de " +
            "la inversión se saca con un traspaso, y la plata condicionada solo sale sin castigo " +
            "para lo suyo. Puedes elegirlas igual."
    UsoDeCuenta.DINERO_PROPIO ->
        "Estas no son plata disponible: una deuda no paga otra, y la plata condicionada solo " +
            "sale sin castigo para lo suyo. Puedes elegirlas igual."
    UsoDeCuenta.PUNTA_DE_TRASPASO ->
        "A una tarjeta de crédito no se le traspasa plata: pagar el extracto es una cuota. " +
            "Puedes elegirla igual."
    UsoDeCuenta.DEUDA_QUE_SE_PAGA ->
        "Estas no son una deuda que se pague con una cuota. Puedes elegirlas igual."
}
