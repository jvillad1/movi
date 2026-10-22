package com.jvillada.movi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * # La vibración de Movi, en un solo lugar
 *
 * Ola 1 · «Que Movi se sienta viva». Movi no vibraba nunca. Ahora vibra **solo** en los momentos en
 * que el dueño cierra algo con plata de por medio, y en el teclado del monto:
 *
 * - guardar un movimiento en «Agregar» ([confirmar]);
 * - confirmar un aviso del banco, en Reconciliar o desde «Por revisar» ([confirmar]);
 * - tildar un pago del checklist del período ([confirmar]);
 * - cada tecla del teclado numérico de «Agregar» ([tecla], el toque liviano de un teclado).
 *
 * Nada más, a propósito: una app que vibra en cada toque deja de decir algo con la vibración.
 * Agregar un lugar nuevo es agregarlo a esta lista.
 *
 * En la web `LocalHapticFeedback` no hace nada, y en un teléfono con la respuesta táctil apagada
 * tampoco: la vibración nunca es la única señal de que algo pasó. Si la plataforma lanza, se
 * ignora —un adorno no puede tumbar un guardado—.
 */
class Vibrador(private val haptica: HapticFeedback) {
    /** Algo quedó hecho: un guardado, una confirmación. */
    fun confirmar() = vibrar(HapticFeedbackType.Confirm)

    /** Una tecla del teclado numérico: el toque más liviano que hay. */
    fun tecla() = vibrar(HapticFeedbackType.VirtualKey)

    private fun vibrar(tipo: HapticFeedbackType) {
        runCatching { haptica.performHapticFeedback(tipo) }
    }
}

/** El [Vibrador] de esta pantalla. Se puede guardar y usar desde una corrutina (después de guardar). */
@Composable
fun rememberVibrador(): Vibrador {
    val haptica = LocalHapticFeedback.current
    return remember(haptica) { Vibrador(haptica) }
}
