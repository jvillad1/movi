package com.jvillada.movi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.theme.MinBg
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.auth.LoginScreen
import com.jvillada.movi.ui.auth.RegisterScreen
import com.jvillada.movi.ui.ai.AIChatScreen
import com.jvillada.movi.ui.analisis.AnalisisScreen
import com.jvillada.movi.ui.budgets.PresupuestosScreen
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.dashboard.DashboardScreen
import com.jvillada.movi.ui.goals.MetasScreen
import com.jvillada.movi.ui.investments.InversionesScreen
import com.jvillada.movi.ui.extractos.ExtractosScreen
import com.jvillada.movi.ui.mas.MasScreen
import com.jvillada.movi.ui.ocr.OCRCaptureScreen
import com.jvillada.movi.ui.ocr.OCRConfirmScreen
import com.jvillada.movi.ui.onboarding.OnboardingProfileScreen
import com.jvillada.movi.ui.onboarding.WelcomeScreen
import com.jvillada.movi.ui.profile.PerfilScreen
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.recurrentes.RecurrentesScreen
import com.jvillada.movi.ui.sms.SMSInboxScreen
import com.jvillada.movi.ui.sms.SMSReconcileScreen
import com.jvillada.movi.ui.transactions.TransactionsScreen
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.accounts.AccountDetailScreen

@Composable
fun App() {
    MoviTheme {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * 1.12f)
        ) {
            val backStack = remember {
                mutableStateListOf<Screen>(if (SessionManager.isLoggedIn) Screen.Dashboard else Screen.Login)
            }
            val currentScreen = backStack.last()

            val navigate: (Screen) -> Unit = { screen ->
                if (backStack.last() != screen) backStack.add(screen)
            }
            val goBack: () -> Unit = {
                if (backStack.size > 1) backStack.removeLast()
            }

            BackHandlerEffect(enabled = backStack.size > 1, onBack = goBack)

            LaunchedEffect(SessionManager.loggedIn) {
                if (!SessionManager.loggedIn) {
                    backStack.clear()
                    backStack.add(Screen.Login)
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(MinBg).statusBarsPadding()) {
                when (currentScreen) {
                    Screen.Login             -> LoginScreen(navigate)
                    Screen.Register          -> RegisterScreen(navigate)
                    Screen.OnboardingWelcome -> WelcomeScreen(navigate)
                    Screen.OnboardingProfile -> OnboardingProfileScreen(navigate)
                    Screen.Dashboard         -> DashboardScreen(navigate)
                    Screen.Transactions      -> TransactionsScreen(navigate)
                    Screen.QuickAdd          -> QuickAddScreen(onDismiss = goBack, onNavigate = navigate)
                    Screen.Profile           -> PerfilScreen(
                        onNavigate = navigate,
                        onLogout = {
                            SessionManager.clear()
                            backStack.clear()
                            backStack.add(Screen.Login)
                        },
                    )
                    Screen.AIChat            -> AIChatScreen(navigate)
                    Screen.Analisis          -> AnalisisScreen(navigate)
                    Screen.Investments       -> InversionesScreen(navigate)
                    Screen.Credits           -> CreditosScreen(navigate)
                    Screen.Goals             -> MetasScreen(navigate)
                    Screen.Budgets           -> PresupuestosScreen(navigate)
                    Screen.Recurrentes       -> RecurrentesScreen(navigate)
                    Screen.OCRCapture        -> OCRCaptureScreen(navigate)
                    Screen.OCRConfirm        -> OCRConfirmScreen(navigate)
                    Screen.SMSInbox          -> SMSInboxScreen(navigate)
                    is Screen.SMSReconcile   -> SMSReconcileScreen(navigate, currentScreen.smsId)
                    Screen.Mas               -> MasScreen(navigate)
                    Screen.Extractos         -> ExtractosScreen(navigate)
                    Screen.Accounts         -> AccountsScreen(navigate)
                    is Screen.AccountDetail -> { /* wired in Task 3 */ }
                }
            }
        }
    }
}
