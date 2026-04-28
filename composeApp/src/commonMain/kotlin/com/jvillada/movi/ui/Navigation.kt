package com.jvillada.movi.ui

sealed class Screen {
    data object OnboardingWelcome : Screen()
    data object OnboardingProfile : Screen()
    data object Dashboard : Screen()
    data object Transactions : Screen()
    data object QuickAdd : Screen()
    data object Profile : Screen()
    data object AIChat : Screen()
    data object Investments : Screen()
    data object Credits : Screen()
    data object Goals : Screen()
    data object OCRCapture : Screen()
    data object OCRConfirm : Screen()
    data object SMSInbox : Screen()
    data object SMSReconcile : Screen()
}
