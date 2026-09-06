package com.jvillada.movi.theme

import androidx.compose.ui.graphics.Color

// Movi minimal — M3 dark tonal surfaces
val MinBg                      = Color(0xFF0F1115)
val MinSurface                 = Color(0xFF15171C)
val MinSurfaceContainerLow     = Color(0xFF191B20)
val MinSurfaceContainer        = Color(0xFF1D1F25)
val MinSurfaceContainerHigh    = Color(0xFF22252B)
val MinSurfaceContainerHighest = Color(0xFF2A2D34)

val MinBorder                  = Color(0x0FFFFFFF)
val MinBorderStrong            = Color(0x24FFFFFF)
val MinHairline                = Color(0x12FFFFFF)

val MinPrimary                 = Color(0xFFC7BCFF)
val MinPrimaryContainer        = Color(0xFF3F2F87)
val MinOnPrimaryContainer      = Color(0xFFE7DFFF)

val MinIncome                  = Color(0xFF7DDDB0)
val MinIncomeContainer         = Color(0x247DDDB0)
val MinExpense                 = Color(0xFFFFB4AB)
val MinExpenseContainer        = Color(0x1FFFB4AB)
val MinWarn                    = Color(0xFFFFD479)

// Traspasos, cuotas y pagos de tarjeta: plata que fue de una cuenta suya a otra, ni gasto ni
// ingreso. Azul frío, deliberadamente distinto de MinPrimary (el lavanda de marca/interactivo,
// usado en botones y links) para no mezclar "esto es plata que se movió" con "esto es un botón
// para tocar". Ver [colorDelTono] en TransactionsScreen.kt.
val MinTransfer                = Color(0xFF8AB4F8)
val MinTransferContainer       = Color(0x248AB4F8)

val MinText                    = Color(0xFFE6E1E9)
val MinTextDim                 = Color(0xBCE6E1E9)
val MinTextMute                = Color(0x8AE6E1E9)
val MinTextFaint               = Color(0x52E6E1E9)
