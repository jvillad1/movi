package com.jvillada.movi.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.jvillada.movi.ui.components.NavTab

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

/**
 * A qué destino de la navegación principal pertenece cada pantalla; null = sin chrome de
 * navegación (auth, onboarding, flujos a pantalla completa). App.kt lo usa para resaltar el
 * ítem activo en la barra (teléfono) y en el rail (pantalla ancha), que son los únicos
 * lugares donde se pinta la navegación — ninguna pantalla arma su propia barra.
 *
 * Ola 4: Cuentas y el detalle de una cuenta marcan la pestaña Cuentas (antes, Inicio);
 * Presupuestos y Créditos tienen destino propio (en el teléfono se resaltan como Más).
 */
fun navTabFor(screen: Screen): NavTab? = when (screen) {
    Screen.Dashboard -> NavTab.HOME
    Screen.Transactions -> NavTab.TRANSACTIONS
    Screen.Accounts, is Screen.AccountDetail -> NavTab.ACCOUNTS
    Screen.Credits -> NavTab.CREDITS
    Screen.Budgets -> NavTab.BUDGETS
    Screen.Mas, Screen.Profile, Screen.Goals, Screen.Investments, Screen.Subscriptions,
    Screen.Recurrentes, Screen.Extractos, Screen.AIChat, Screen.SMSInbox, is Screen.SMSReconcile -> NavTab.MORE
    else -> null
}

/**
 * Reglas puras de la pila de navegación (sin Compose), extraídas para poder
 * testearlas en :shared:commonTest (App.kt las aplica sobre un
 * SnapshotStateList<Screen> para que Compose observe los cambios).
 */
object NavStack {
    /** true si `screen` debe apilarse — evita duplicar la pantalla de arriba. */
    fun shouldPush(stack: List<Screen>, screen: Screen): Boolean =
        stack.isEmpty() || stack.last() != screen

    /**
     * Resultado de pedir "volver": si hay historial, se saca el tope (Pop); si la
     * pila tiene un solo elemento hace falta un destino de reserva (Fallback) —
     * por ejemplo, entraste directo por deep link o recargaste la web en una
     * pantalla que no es Inicio.
     */
    sealed class BackResult {
        data object Pop : BackResult()
        data class Fallback(val screen: Screen) : BackResult()
    }

    fun back(stack: List<Screen>, fallback: Screen): BackResult =
        if (stack.size > 1) BackResult.Pop else BackResult.Fallback(fallback)
}

/**
 * «Volver» real, expuesto a las pantallas: recibe el destino de reserva (F22) y
 * decide adentro si hay historial al que volver o si hay que caer a ese destino.
 * App.kt provee la implementación de verdad alrededor del `when(currentScreen)`;
 * el default es un no-op para que un preview o test aislado no explote.
 */
val LocalGoBack = staticCompositionLocalOf<(Screen) -> Unit> { {} }
