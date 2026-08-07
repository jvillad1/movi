package com.jvillada.movi.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Window width class provided by App from the real container width.
 * Compact (< 840dp): phone layout — per-screen MinBottomNav.
 * Expanded: desktop/web-wide layout — root-level MinNavRail; MinBottomNav
 * renders nothing so the 14 screens that embed it need no changes.
 */
enum class WindowWidthClass { Compact, Expanded }

val LocalWindowWidthClass = compositionLocalOf { WindowWidthClass.Compact }
