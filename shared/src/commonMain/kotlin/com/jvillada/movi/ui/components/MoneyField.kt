package com.jvillada.movi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
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
        val display = if (digits.isEmpty()) "" else prefix + groupDigitsForDisplay(digits)
        val fieldValue = TextFieldValue(text = display, selection = TextRange(display.length))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MinSurfaceContainerLow)
                .border(1.dp, MinBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            if (digits.isEmpty()) Text(placeholder, fontSize = 14.sp, color = MinTextFaint)
            BasicTextField(
                value = fieldValue,
                onValueChange = { new ->
                    // No importa dónde haya caído el cursor de la escritura entrante: nos
                    // quedamos solo con los dígitos y el campo siempre reancla al final —
                    // así un paste con comas o puntos de mil también entra limpio.
                    val newDigits = new.text.filter { it.isDigit() }.take(maxDigits)
                    onValueChange(parseMoneyDigits(newDigits))
                },
                textStyle = TextStyle(fontSize = 14.sp, color = MinText),
                cursorBrush = SolidColor(MinText),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
