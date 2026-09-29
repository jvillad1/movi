package com.jvillada.movi.ui.quickadd

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

/**
 * Lo que una tecla física le pide a la hoja de «Agregar» (revisión del 29-sep: en escritorio los
 * dígitos no escribían el monto, Backspace no borraba y Enter no guardaba).
 */
internal sealed interface AccionDeTecla {
    /** Un dígito del monto, igual que la tecla del teclado en pantalla. */
    data class Digito(val digito: String) : AccionDeTecla

    /** Borrar el último dígito (la «⌫» del teclado en pantalla). */
    data object Borrar : AccionDeTecla

    /** Guardar, si el botón está habilitado — la hoja lo decide. */
    data object Guardar : AccionDeTecla
}

/**
 * **El mapeo de teclas de «Agregar», sin Compose adentro** para poder probarlo solo.
 *
 * Solo al bajar la tecla (no al soltarla: sería escribir dos veces) y **sin modificadores**: ⌘R,
 * Ctrl+1 o Shift+1 («!») son del navegador o de otra cosa, no dígitos del monto. Los dígitos de la
 * fila de arriba y los del teclado numérico valen igual; Enter también el del teclado numérico.
 *
 * `null` = esta tecla no es de la hoja; que siga su camino.
 */
internal fun accionDeTecla(tecla: Key, tipo: KeyEventType, conModificador: Boolean): AccionDeTecla? {
    if (tipo != KeyEventType.KeyDown || conModificador) return null
    DIGITOS[tecla]?.let { return AccionDeTecla.Digito(it) }
    return when (tecla) {
        Key.Backspace -> AccionDeTecla.Borrar
        Key.Enter, Key.NumPadEnter -> AccionDeTecla.Guardar
        else -> null
    }
}

private val DIGITOS: Map<Key, String> = mapOf(
    Key.Zero to "0", Key.One to "1", Key.Two to "2", Key.Three to "3", Key.Four to "4",
    Key.Five to "5", Key.Six to "6", Key.Seven to "7", Key.Eight to "8", Key.Nine to "9",
    Key.NumPad0 to "0", Key.NumPad1 to "1", Key.NumPad2 to "2", Key.NumPad3 to "3", Key.NumPad4 to "4",
    Key.NumPad5 to "5", Key.NumPad6 to "6", Key.NumPad7 to "7", Key.NumPad8 to "8", Key.NumPad9 to "9",
)
