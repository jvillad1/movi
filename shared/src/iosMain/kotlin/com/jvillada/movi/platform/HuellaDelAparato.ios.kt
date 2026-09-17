package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * iOS no ofrece «Entrar con huella» todavía. Devolver `null` deja la app exactamente como
 * estaba: ni el ofrecimiento tras entrar, ni el interruptor en Perfil, ni nada que cambie en
 * cómo se guarda la sesión.
 */
@Composable
internal actual fun huellaDeLaPlataforma(): HuellaDelAparato? = null
