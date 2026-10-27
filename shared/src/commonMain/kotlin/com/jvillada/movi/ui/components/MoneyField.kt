package com.jvillada.movi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import com.jvillada.movi.theme.Espacio
import com.jvillada.movi.theme.Medida
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.ui.components.ds.CampoDeMonto

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

/**
 * **El alto de [MoneyField] con rótulo**, calculado desde los MISMOS tokens que arma el campo
 * real, no eyeballeado. Ola B, tarea 9 (fix round 1): el esqueleto de Cuadre de saldos —el primer
 * lugar que necesitó imitar este campo sin poder abrir un teclado de verdad— tenía un
 * `BloqueEsqueleto(alto = 48.dp)` puesto a ojo.
 *
 * Vuelta de diseño 6: el campo es el [CampoDeMonto] del sistema, así que suma lo que arma ese: el
 * rótulo ([altoDeUnRenglon] de `Movi.textos.apoyo`, más [Espacio.xs] de aire) y la caja (el renglón
 * de `Movi.textos.monto` más [Espacio.s] arriba y abajo, nunca menos que [Medida.objetivoTactil]).
 * `MoneyFieldAltoConRotuloTest` mide un `MoneyField` real al lado y confirma que estos dos números
 * no se separan más de 2 dp.
 */
@Composable
fun altoDeMoneyFieldConRotulo(): Dp {
    val altoDelRotulo = altoDeUnRenglon(Movi.textos.apoyo) + Espacio.xs
    val altoDeLaCaja = maxOf(Medida.objetivoTactil, altoDeUnRenglon(Movi.textos.monto) + Espacio.s * 2)
    return altoDelRotulo + altoDeLaCaja
}

/**
 * El campo de monto de siempre, con su API de siempre. **Vuelta de diseño 6: delega en
 * [CampoDeMonto]**, el del sistema; para lo nuevo, úsalo directo (rótulo, guía, ayuda, error).
 *
 * [placeholder] llega como se escribía antes («$ 0», «Deuda actual (COP, opcional)»): el símbolo
 * del principio se quita (lo pinta el campo aparte) y lo que queda es el texto guía, en la letra de
 * cuerpo. «$ 0» o vacío es «sin guía»: el campo muestra un «0» en la letra de las cifras.
 *
 * Por qué el cursor no se reancla al final, por qué no hay botón de «borrar todo» y cómo se
 * arregló ⌘A en la web: ver [nextMoneyField], [esAtajoDeSeleccionarTodo] y el comentario largo
 * dentro de [CampoDeMonto], que vivía acá. La lógica vive en las funciones puras de arriba.
 */
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
    CampoDeMonto(
        valor = value,
        alCambiar = onValueChange,
        modifier = modifier,
        rotulo = label,
        guia = guiaDelPlaceholder(placeholder, prefix),
        moneda = prefix,
        maxDigitos = maxDigits,
    )
}

/** «$ 0» → sin guía; «$ Capital original» → «Capital original». */
internal fun guiaDelPlaceholder(placeholder: String, prefix: String): String? =
    placeholder.trim().removePrefix(prefix).trim().takeIf { it.isNotEmpty() && it != "0" }
