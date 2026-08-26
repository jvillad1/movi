package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
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

        // ── Ola 9 · C — CÓMO SE EDITA UN MONTO (y por qué antes se guardaba otra cifra) ──
        //
        // Este campo armaba un `TextFieldValue(display, selection = TextRange(display.length))`
        // NUEVO en cada composición: le tiraba a la basura la selección que mandaba el sistema y
        // reanclaba el cursor al final, siempre. En el navegador eso no "perdía teclas": las
        // **concatenaba**. Escribir 50000, seleccionar todo con ⌘A y escribir 7000 dejaba
        // $500.007.000 — quinientos millones donde el dueño quiso siete mil, guardables sin que
        // nada avisara. El `<input>` oculto y lo que Compose pintaba eran textos distintos, así
        // que toda edición posterior operaba sobre algo que el dueño no veía.
        //
        // El arreglo NO es una limitación de la plataforma —fue un diagnóstico equivocado en la
        // primera versión de esta ola—: [CategoryField], en la misma hoja y a diez líneas de
        // acá, ya lo hacía bien. Se copia su patrón, que son dos reglas:
        //
        //  1. El estado del texto vive ACÁ y solo se resincroniza cuando el cambio viene de
        //     AFUERA (prellenado, reset, otra pantalla) — nunca por recomponer.
        //  2. Lo que manda el sistema se respeta TAL CUAL, selección incluida. Solo se reescribe
        //     cuando de verdad hay que reformatear, y ahí se dice explícitamente dónde queda el
        //     cursor.
        //
        // Reformatear (agrupar de a miles mientras se escribe, F14) se hace solo cuando el
        // cursor está al final y no hay nada seleccionado — que es el caso de tipear normal. Con
        // una selección viva o el cursor en el medio se deja pasar el texto sin tocarlo: agrupar
        // ahí movería el cursor de lugar y es exactamente lo que rompía el campo.
        var field by remember { mutableStateOf(TextFieldValue(groupDigitsForDisplay(digits), TextRange(digits.length))) }
        LaunchedEffect(digits) {
            if (field.text.filter { it.isDigit() } != digits) {
                val fromOutside = groupDigitsForDisplay(digits)
                field = TextFieldValue(fromOutside, TextRange(fromOutside.length))
            }
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
                // El símbolo es una ETIQUETA, no parte del texto editable: adentro del campo
                // ("$12.345") el cursor podía pararse antes del "$" y el borrado tenía que
                // pelearse con un carácter que el usuario nunca escribió. Afuera, lo que se
                // edita son solo dígitos, y el campo se anuncia como monto esté vacío o lleno.
                Text(
                    text = prefix,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (digits.isEmpty()) MinTextFaint else MinTextMute,
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (digits.isEmpty()) {
                        // Una sola línea SIEMPRE: este campo también se usa a media fila (la
                        // cuota de un crédito), y un placeholder largo partido en tres renglones
                        // estiraba la caja al triple y empujaba al campo de al lado (visto a ojo
                        // a 360dp en «Términos del crédito»).
                        Text(
                            text = placeholder.removePrefix(prefix).trim().ifEmpty { "0" },
                            fontSize = 14.sp,
                            color = MinTextFaint,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = field,
                        onValueChange = { entrante ->
                            val soloDigitosYPuntos = entrante.text.all { it.isDigit() || it == '.' }
                            val nuevosDigitos = entrante.text.filter { it.isDigit() }.take(maxDigits)
                            val agrupado = groupDigitsForDisplay(nuevosDigitos)
                            val cursorAlFinal = entrante.selection.collapsed &&
                                entrante.selection.start == entrante.text.length
                            field = when {
                                // Entró algo que no es un dígito (una letra, un símbolo, un
                                // pegado con comas) o se pasó del largo: se limpia y se dice
                                // dónde queda el cursor, porque el texto cambió de forma.
                                !soloDigitosYPuntos || nuevosDigitos.length != entrante.text.count { it.isDigit() } ->
                                    TextFieldValue(agrupado, TextRange(agrupado.length))
                                // Tipeo normal (cursor al final): se agrupa de a miles.
                                cursorAlFinal ->
                                    if (agrupado == entrante.text) entrante
                                    else TextFieldValue(agrupado, TextRange(agrupado.length))
                                // Selección viva o cursor en el medio: NO se toca nada.
                                else -> entrante
                            }
                            onValueChange(parseMoneyDigits(nuevosDigitos))
                        },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = MinText,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(MinText),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // NO hay un botón de «borrar todo», y es a propósito: se probó y **no funciona
                // en el navegador**. Vaciar el campo desde el estado de la app deja el buffer
                // del `<input>` oculto con lo de antes, así que el siguiente dígito se pega a lo
                // que había: borrar sobre «7» y escribir 1800000 daba $71.800.000. Un botón que
                // a veces suma un dígito fantasma a una cifra de plata es peor que no tenerlo,
                // y con la selección arreglada ya no hace falta: seleccionar y reescribir —el
                // gesto de cualquier campo de texto— reemplaza el monto entero.
            }
        }
    }
}
