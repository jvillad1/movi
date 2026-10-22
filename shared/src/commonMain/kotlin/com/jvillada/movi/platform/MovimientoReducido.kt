package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * **¿El sistema pidió reducir el movimiento?** Con `true`, las transiciones de Movi pasan en el
 * acto (ver `com.jvillada.movi.theme.Movimiento`).
 *
 * Cada plataforma lo dice a su manera, y donde no hay cómo saberlo barato se contesta `false`:
 * - **Android**: «Escala de duración de animaciones» en 0 (Opciones de desarrollador o
 *   Accesibilidad → «Quitar animaciones»), `Settings.Global.ANIMATOR_DURATION_SCALE`.
 * - **Web**: la consulta `prefers-reduced-motion: reduce` del navegador.
 * - **iOS**: «Reducir movimiento» de Accesibilidad.
 *
 * Se lee al componer la app; cambiar el ajuste con Movi abierta se ve la próxima vez que se abre.
 */
@Composable
expect fun movimientoReducidoDelSistema(): Boolean
