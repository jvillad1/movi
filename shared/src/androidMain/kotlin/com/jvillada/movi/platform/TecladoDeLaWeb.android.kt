package com.jvillada.movi.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** En Android el teclado llega como `WindowInsets.ime` (ver el `expect`). */
@Composable
actual fun altoDelTecladoDeLaWeb(): Dp = 0.dp
