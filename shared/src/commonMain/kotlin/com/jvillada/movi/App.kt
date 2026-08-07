package com.jvillada.movi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import com.jvillada.movi.ui.subscriptions.SuscripcionesScreen
import com.jvillada.movi.ui.sms.SMSInboxScreen
import com.jvillada.movi.ui.sms.SMSReconcileScreen
import com.jvillada.movi.ui.transactions.TransactionsScreen
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.accounts.AccountDetailScreen
import com.jvillada.movi.ui.extractos.StatementReviewScreen
import com.jvillada.movi.ui.extractos.ImportDetailScreen
import com.jvillada.movi.ui.sdui.editor.ScreenEditorScreen
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MinNavRail
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.WindowWidthClass
import kotlinx.serialization.json.Json

// Which tab (if any) a screen belongs to; null = no navigation chrome
// (auth, onboarding, full-screen flows). Mirrors the per-screen MinBottomNav
// call sites.
private fun screenNavTab(screen: Screen): NavTab? = when (screen) {
    Screen.Dashboard, Screen.Accounts, is Screen.AccountDetail -> NavTab.HOME
    Screen.Transactions -> NavTab.TRANSACTIONS
    Screen.Budgets -> NavTab.BUDGETS
    Screen.Mas, Screen.Analisis, Screen.Profile, Screen.Goals, Screen.Credits,
    Screen.Investments, Screen.Subscriptions, Screen.Recurrentes, Screen.Extractos -> NavTab.MORE
    else -> null
}

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

            val saveableStateHolder = rememberSaveableStateHolder()
            // Outer Box paints the background full-bleed across desktop.
            // Compact (< 840dp): the classic centered "phone column" capped at
            // 600dp; screens draw their own MinBottomNav. Expanded: a root
            // MinNavRail on the left (per-screen bottom navs render nothing)
            // with the same capped column centered in the remaining space.
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().background(MinBg),
            ) {
                val widthClass = if (maxWidth < 840.dp) WindowWidthClass.Compact else WindowWidthClass.Expanded
                val showRail = widthClass == WindowWidthClass.Expanded && screenNavTab(currentScreen) != null

                CompositionLocalProvider(LocalWindowWidthClass provides widthClass) {
                Row(modifier = Modifier.fillMaxSize()) {
                if (showRail) {
                    MinNavRail(
                        active = screenNavTab(currentScreen),
                        onTabSelected = { tab ->
                            navigate(
                                when (tab) {
                                    NavTab.HOME -> Screen.Dashboard
                                    NavTab.TRANSACTIONS -> Screen.Transactions
                                    NavTab.ADD -> Screen.QuickAdd
                                    NavTab.BUDGETS -> Screen.Budgets
                                    NavTab.MORE -> Screen.Mas
                                }
                            )
                        },
                    )
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                Box(modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().statusBarsPadding()) {
                saveableStateHolder.SaveableStateProvider(key = currentScreen.toString()) {
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
                    Screen.Subscriptions     -> SuscripcionesScreen(navigate)
                    Screen.OCRCapture        -> OCRCaptureScreen(navigate)
                    Screen.OCRConfirm        -> OCRConfirmScreen(navigate)
                    Screen.SMSInbox          -> SMSInboxScreen(navigate)
                    is Screen.SMSReconcile   -> SMSReconcileScreen(navigate, currentScreen.smsId)
                    Screen.Mas               -> MasScreen(navigate)
                    Screen.Extractos         -> ExtractosScreen(navigate)
                    Screen.Accounts         -> AccountsScreen(navigate)
                    is Screen.AccountDetail -> AccountDetailScreen(navigate, currentScreen.accountId)
                    is Screen.StatementReview -> StatementReviewScreen(
                        onNavigate = navigate,
                        result = Json.decodeFromString(currentScreen.resultJson),
                    )
                    is Screen.ImportDetail -> ImportDetailScreen(
                        onNavigate = navigate,
                        importId = currentScreen.importId,
                    )
                    Screen.ScreenEditor      -> ScreenEditorScreen(navigate)
                }
                } // SaveableStateProvider
                } // inner Box (max-width container)
                } // content area
                } // Row (rail + content)
                } // CompositionLocalProvider
            }
        }
    }
}
