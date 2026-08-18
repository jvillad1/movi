package com.jvillada.movi.ui

sealed class Screen {
    data object Login            : Screen()
    data object Register         : Screen()
    data object OnboardingWelcome : Screen()
    data object OnboardingProfile : Screen()
    data object Dashboard : Screen()
    data object Transactions : Screen()
    // F10: cuando se abre "para registrar el primero" desde el detalle de una cuenta puntual,
    // esa cuenta viene preseleccionada — sin esto QuickAdd caía siempre en la primera cuenta de
    // la lista, sin importar desde dónde se entró.
    data class QuickAdd(val presetAccountId: String? = null) : Screen()
    data object Profile : Screen()
    data object AIChat : Screen()
    data object Analisis : Screen()
    data object Investments : Screen()
    data object Credits : Screen()
    data object Goals : Screen()
    data object Budgets : Screen()
    data object Recurrentes : Screen()
    data object Subscriptions : Screen()
    data object OCRCapture : Screen()
    data object OCRConfirm : Screen()
    data object SMSInbox : Screen()
    data class SMSReconcile(val smsId: String) : Screen()
    data object Mas : Screen()
    data object Extractos : Screen()
    data object Accounts : Screen()
    data class AccountDetail(val accountId: String) : Screen()
    data class StatementReview(val resultJson: String) : Screen()
    data class ImportDetail(val importId: String) : Screen()
    data object ScreenEditor : Screen()
}
