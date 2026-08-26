package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.theme.MinBorder
import com.jvillada.movi.theme.MinSurfaceContainerLow
import com.jvillada.movi.theme.MinText
import com.jvillada.movi.theme.MinTextFaint
import com.jvillada.movi.theme.MinTextMute

/**
 * F14 · F23 · F34 · F53: un solo campo de monto para toda la app — antes cada formulario
 * (CreateAccountSheet, CreditTermsSheet, CreditBalanceSheet, CreateRecurringRuleSheet) lo
 * armaba a mano con un `BasicTextField` que mostraba los dígitos crudos ("$2000000") y recién
 * agrupaba en miles al guardar.
 *
 * Agrupa por miles "$2.000.000" ya mientras se escribe, filtra todo lo que no sea dígito y
 * expone el resultado como [Long]?. El cursor queda siempre al final del número — es la misma
 * simplificación que ya usan los teclados numéricos propios de Presupuestos y Agregar
 * movimiento (que solo pueden agregar o borrar el último dígito): no hace falta un
 * `VisualTransformation` con mapeo de offsets para algo que en la práctica se escribe de
 * izquierda a derecha.
 */
fun groupDigitsForDisplay(digits: String): String {
    if (digits.isEmpty()) return ""
    return digits.reversed().chunked(3).joinToString(".").reversed()
}

/**
 * Como [groupDigitsForDisplay] pero para los teclados numéricos propios (Presupuestos, Agregar
 * movimiento): vacío se ve como "0" (no como campo en blanco, ahí no hay placeholder aparte) y
 * respeta un "." decimal opcional sin agruparlo — solo la parte entera se agrupa.
 */
fun formatAmountKeypadDisplay(raw: String): String {
    if (raw.isEmpty()) return "0"
    val dot = raw.indexOf('.')
    return if (dot == -1) {
        groupDigitsForDisplay(raw)
    } else {
        groupDigitsForDisplay(raw.substring(0, dot)) + raw.substring(dot)
    }
}

/** "2.000.000", "$2000000" o "2000000" -> 2000000L. Ignora todo lo que no sea dígito. Vacío -> null. */
fun parseMoneyDigits(raw: String): Long? {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return null
    return digits.toLongOrNull()
}

@Composable
fun MoneyField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "$ 0",
    maxDigits: Int = 12,
    /**
     * Símbolo que se antepone mientras se escribe. Por defecto el peso, que es lo que llenan
     * casi todos los formularios; se pasa "US$" donde el monto NO es en pesos — en Colombia
     * "$20" se lee veinte pesos, así que un cobro de veinte dólares mostrado con "$" miente.
     */
    prefix: String = "$",
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MinTextMute,
                letterSpacing = 0.4.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
        }
        val digits = value?.toString() ?: ""
        val display = groupDigitsForDisplay(digits)

        // Ola 9 · C — EL ESTADO DEL TEXTO VIVE ACÁ, no se reconstruye en cada recomposición.
        //
        // Antes este campo armaba un `TextFieldValue(display, selection = TextRange(display.length))`
        // nuevo en cada paso de la composición. Eso reancla el cursor al final SIEMPRE, también
        // cuando el usuario acababa de seleccionar el texto — y en el navegador (wasm) el buffer
        // del sistema y el de Compose quedaban en desacuerdo: **seleccionar todo y volver a
        // escribir el monto no hacía nada**, las teclas se perdían. Con teclado físico esa es la
        // forma natural de corregir una cifra, así que el campo se sentía roto. (Verificado a
        // ojo en el navegador, en «Nuevo recurrente» y en «Nueva cuenta»: se comportaban igual
        // de mal, que es lo que el reporte del dueño insinuaba sin poder nombrar.)
        //
        // Ahora el estado es propio y solo se resincroniza cuando los dígitos que manda el
        // padre difieren de los que tenemos (prellenado, reset del formulario, otra pantalla) —
        // nunca por el simple hecho de recomponer, así que la selección del usuario sobrevive.
        var field by remember { mutableStateOf(TextFieldValue(display, TextRange(display.length))) }
        if (field.text.filter { it.isDigit() } != digits) {
            field = TextFieldValue(display, TextRange(display.length))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MinSurfaceContainerLow)
                .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ola 9 · C: el símbolo es una ETIQUETA, no parte del texto editable. Antes
                // viajaba adentro del campo ("$12.345"), así que el cursor podía pararse antes
                // del "$" y el borrado tenía que pelearse con un carácter que el usuario nunca
                // escribió. Afuera, lo que se edita son solo dígitos — y el campo se anuncia
                // como monto de una: es lo primero que se ve, esté vacío o lleno.
                Text(
                    text = prefix,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (digits.isEmpty()) MinTextFaint else MinTextMute,
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (digits.isEmpty()) {
                        Text(
                            text = placeholder.removePrefix(prefix).trim().ifEmpty { "0" },
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MinTextFaint,
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { new ->
                            // Nos quedamos solo con los dígitos: un paste con puntos o comas de
                            // mil entra limpio, y una letra sencillamente no entra. El texto que
                            // se muestra es el agrupado, no lo que llegó.
                            val newDigits = new.text.filter { it.isDigit() }.take(maxDigits)
                            val grouped = groupDigitsForDisplay(newDigits)
                            // Reanclar el cursor al final es la misma simplificación de siempre
                            // (los teclados propios de Agregar y Presupuestos solo agregan o
                            // borran el último dígito), pero ahora se hace UNA vez por edición
                            // real y no en cada recomposición.
                            field = TextFieldValue(grouped, TextRange(grouped.length))
                            onValueChange(parseMoneyDigits(newDigits))
                        },
                        // Monoespaciada y un poco más grande: la misma cara que tiene el monto en
                        // «Agregar», para que las tres pantallas que el dueño nombró (agregar,
                        // cuenta, recurrente) se lean como el mismo campo de plata.
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = MinText,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(MinText),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Ola 9 · C — CÓMO SE CORRIGE UN MONTO YA ESCRITO.
                //
                // Con teclado físico, lo natural es seleccionar todo y volver a escribir. En el
                // navegador eso NO funciona: Compose/wasm se come las teclas que llegan con el
                // texto seleccionado (verificado a ojo, y sigue pasando con el estado del campo
                // acá adentro — no es algo que este componente pueda arreglar). Sin esta salida
                // quedaba solo el borrado tecla por tecla, y de ahí el «el monto está mal».
                //
                // Esto no depende del teclado ni de la selección: cambia el estado de la app, que
                // es el camino que sí funciona siempre. Es el mismo gesto que el ⌫ del teclado de
                // «Agregar», llevado a un campo de texto.
                if (digits.isNotEmpty()) {
                    Text(
                        text = "Borrar",
                        fontSize = 12.sp,
                        color = MinTextMute,
                        modifier = Modifier
                            .clickable { onValueChange(null) }
                            .padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}
