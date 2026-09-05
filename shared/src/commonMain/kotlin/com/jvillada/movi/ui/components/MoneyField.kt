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
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
 * expone el resultado como [Long]?.
 *
 * **El cursor NO queda siempre al final** —eso decía este KDoc y era justamente lo que había que
 * dejar de hacer: reanclarlo al final en cada composición es lo que hacía que corregir un monto
 * concatenara en vez de reemplazar (ver el comentario largo dentro de [MoneyField])—. El cursor
 * queda donde lo dejó la edición, y al reagrupar se lo vuelve a poner **después del mismo dígito**
 * que tenía a la izquierda, contando dígitos y no caracteres. Eso es, en la práctica, el mapeo de
 * offsets de un `VisualTransformation`, hecho en [nextMoneyField] en vez de en la capa de pintado:
 * acá el texto agrupado ES el texto del campo, así que un `VisualTransformation` agregaría una
 * segunda representación —y una segunda tabla de offsets— sin resolver nada más.
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

/**
 * El estado inicial (y el de resincronización desde afuera) del campo: el número ya agrupado, con
 * el cursor al final **del texto agrupado**.
 *
 * Existe como función porque tenerlo escrito dos veces —en el `remember` y en el `LaunchedEffect`
 * de [MoneyField]— ya produjo el bug: una de las dos copias medía el largo con los dígitos CRUDOS
 * ("1800000", 7) y lo aplicaba sobre el texto AGRUPADO ("1.800.000", 9). Un recurrente de
 * $1.800.000 abierto solo para mirarlo pintaba `1.800.0|00`, y una sola tecla lo dejaba en
 * $18.000.900 con «Guardar cambios» habilitado.
 */
fun moneyFieldFromDigits(digits: String): TextFieldValue {
    val agrupado = groupDigitsForDisplay(digits)
    return TextFieldValue(agrupado, TextRange(agrupado.length))
}

/**
 * **La máquina de estados del campo de monto, pura y testeable sin Compose.**
 *
 * Está separada del `@Composable` a propósito: esto es lo único de este archivo que ya se
 * equivocó dos veces guardando una cifra distinta de la que el dueño escribió, y mientras vivía
 * como lambda inline dentro de [MoneyField] no había forma de que CI detectara una regresión.
 * Sus casos están en `MoneyFieldTest`.
 *
 * Dada la edición que reporta el sistema ([entrante]), devuelve lo que el campo debe mostrar:
 *
 * 1. Si el texto que entró YA es la forma agrupada, se devuelve **tal cual**, selección incluida.
 *    Este es el caso de todo evento que no cambia el texto —seleccionar todo, un triple-click,
 *    mover el cursor— y respetarlo es lo que hace que "seleccionar y reescribir" reemplace el
 *    monto en vez de concatenarse a él.
 * 2. Si hay que reformatear, se reagrupa y se dice explícitamente dónde queda el cursor:
 *    **después del mismo dígito que tenía a la izquierda**. Contar dígitos y no caracteres es lo
 *    que hace que reagrupar en el medio del número no mueva el cursor de lugar; sin eso, escribir
 *    un dígito con el cursor en el medio dejaba `1.8050.000` sin reagrupar hasta volver al final.
 *
 * Lo que no es dígito se descarta (letras, símbolos, un pegado con comas) y se recorta a
 * [maxDigits].
 *
 * Nota deliberada: borrar un separador («1.|800.000» + Backspace) no borra nada — el punto vuelve
 * a aparecer porque lo pone el agrupado. Los dígitos siempre están al final del número, así que el
 * Backspace normal (cursor al final) nunca cae ahí.
 */
fun nextMoneyField(entrante: TextFieldValue, maxDigits: Int = 12): TextFieldValue {
    val digitos = entrante.text.filter { it.isDigit() }.take(maxDigits)
    val agrupado = groupDigitsForDisplay(digitos)
    if (agrupado == entrante.text) return entrante
    val corte = entrante.selection.end.coerceIn(0, entrante.text.length)
    val digitosALaIzquierda = entrante.text.take(corte).count { it.isDigit() }.coerceAtMost(digitos.length)
    return TextFieldValue(agrupado, TextRange(offsetTrasDigitos(agrupado, digitosALaIzquierda)))
}

/** Posición en [agrupado] justo después de su dígito número [n] (1-based). `n = 0` -> el arranque. */
private fun offsetTrasDigitos(agrupado: String, n: Int): Int {
    if (n <= 0) return 0
    var vistos = 0
    for (i in agrupado.indices) {
        if (agrupado[i].isDigit()) {
            vistos++
            if (vistos == n) return i + 1
        }
    }
    return agrupado.length
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
        // nada avisara.
        //
        // El arreglo NO es una limitación de la plataforma —fue un diagnóstico equivocado en la
        // primera versión de esta ola—: [CategoryField], en la misma hoja y a diez líneas de
        // acá, ya lo hacía bien. Se copia su patrón, que son dos reglas:
        //
        //  1. El estado del texto vive ACÁ y solo se resincroniza cuando el cambio viene de
        //     AFUERA (prellenado, reset, otra pantalla) — nunca por recomponer.
        //  2. Lo que manda el sistema se respeta TAL CUAL, selección incluida. Solo se reescribe
        //     cuando de verdad hay que reformatear, y ahí se dice explícitamente dónde queda el
        //     cursor (ver [nextMoneyField], que es toda la lógica y vive afuera para poder
        //     testearla).
        //
        // **Qué NO arregla esto, dicho como es.** En Compose-wasm el `<input>` oculto que recibe
        // el teclado y lo que el canvas pinta siguen pudiendo discrepar: tras tipear 1800000,
        // Compose pinta "1.800.000" y `document.activeElement.value` dice "1800000"; tras un
        // triple-click el canvas muestra todo resaltado y el `<input>` reporta la selección
        // colapsada. Lo que se logró —y alcanza para que el campo sea usable— es que Compose
        // **resincronice el `<input>` en la siguiente tecla real**, así que la divergencia se
        // cura sola en vez de acumularse. Es una propiedad más débil que "ya no discrepan".
        //
        // **⌘A: RESUELTO, y confirmado en producción con un teclado real (2026-09-03).** Este
        // bloque decía que el atajo «no llega a Compose» y que alguien tenía que teclearlo a mano.
        // Se tecleó, y después se midió con una sonda escuchando en las dos fases: Compose-wasm
        // **sí** recibe la tecla, le hace `preventDefault` —matando el «seleccionar todo» nativo
        // del navegador— y no implementa el suyo. Por eso la selección nunca ocurría (el `<input>`
        // quedaba en `sel=8..8`) y la tecla siguiente llegaba como un tipeo al final. No era «el
        // arnés de automatización»: era la plataforma. Ahora lo implementa esta app — ver
        // [esAtajoDeSeleccionarTodo] y el `onPreviewKeyEvent` de abajo. Verificado sobre el
        // artefacto desplegado: $18.000.009 + ⌘A + «7000» = $7.000.
        //
        // **Sigue abierto, y sin diagnosticar: la pérdida de teclas.** Escribiendo en ráfaga
        // inmediatamente después de un triple-click, «Bancolombia» + triple-click + «Nequi» daba
        // «BancolombiaNequi». Aparece igual en el campo NOMBRE común y en [CategoryField], así que
        // no es de este archivo. Nadie lo midió todavía — y conviene no darlo por entendido solo
        // porque se parece al de ⌘A: ese resultó ser algo distinto de lo que este bloque afirmaba.
        //
        // **Y el atajo sigue roto en los demás campos de texto de la web** (NOMBRE, categoría):
        // se arregló primero donde el error cuesta plata. Las piezas viven sueltas en
        // `SeleccionarTodo.kt` justamente para que los otros lo adopten llamando a lo mismo.
        var field by remember { mutableStateOf(moneyFieldFromDigits(digits)) }
        LaunchedEffect(digits) {
            if (field.text.filter { it.isDigit() } != digits) {
                field = moneyFieldFromDigits(digits)
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
                            val siguiente = nextMoneyField(entrante, maxDigits)
                            field = siguiente
                            // Se avisa hacia afuera lo MISMO que quedó pintado, no lo que entró:
                            // si las dos cifras pudieran separarse, el dueño estaría mirando una
                            // y guardando la otra — que es exactamente el bug que este archivo
                            // tuvo.
                            onValueChange(parseMoneyDigits(siguiente.text))
                        },
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = MinText,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(MinText),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        // ⌘A: lo hace esta app porque Compose-wasm no lo hace. Ver
                        // [esAtajoDeSeleccionarTodo], que trae la medición completa. Sin esto,
                        // seleccionar todo con el teclado y reescribir CONCATENABA: sobre
                        // $18.000.009, ⌘A + «7000» dejaba $180.000.097.000, guardable y sin aviso.
                        //
                        // Con el campo vacío no se consume la tecla: no hay nada que seleccionar y
                        // quedarse con el atajo sería robarle al navegador algo que sí podría usar.
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { evento ->
                                if (esAtajoDeSeleccionarTodo(evento) && field.text.isNotEmpty()) {
                                    field = conTodoSeleccionado(field)
                                    true
                                } else {
                                    false
                                }
                            },
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
