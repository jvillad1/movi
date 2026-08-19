package com.jvillada.movi.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Window width class provided by App from the real container width.
 * Compact (< 840dp): phone layout — App draws one MinBottomNav under the screen.
 * Expanded: desktop/web-wide layout — App draws the MinNavRail on the left instead.
 */
enum class WindowWidthClass { Compact, Expanded }

val LocalWindowWidthClass = compositionLocalOf { WindowWidthClass.Compact }
