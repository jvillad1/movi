package com.jvillada.movi.platform

import androidx.compose.runtime.Composable

/**
 * La web no ofrece «Entrar con huella». Devolver `null` deja la app exactamente como estaba: el
 * login del navegador sigue siendo el overlay de `index.html`, con su gestor de contraseñas.
 */
@Composable
internal actual fun huellaDeLaPlataforma(): HuellaDelAparato? = null
