package com.jvillada.movi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.NavStack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.navTabFor
import com.jvillada.movi.ui.opensAsOverlay
import com.jvillada.movi.ui.screenForTab
import com.jvillada.movi.ui.auth.LoginScreen
import com.jvillada.movi.ui.auth.RegisterScreen
import com.jvillada.movi.ui.ai.AIChatScreen
import com.jvillada.movi.ui.budgets.PresupuestosScreen
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.dashboard.DashboardScreen
import com.jvillada.movi.ui.goals.MetasScreen
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
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.MinNavRail
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.WindowWidthClass
import kotlinx.serialization.json.Json

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

            // «Agregar» es una ventana modal encima de la pantalla actual, no un destino que la
            // reemplaza (ver [opensAsOverlay]). Vive en su propio estado y NO en `backStack`:
            // apilarla dejaba la pila en una pantalla sin pestaña (`navTabFor` → null) y App.kt
            // escondía el rail y la barra, así que la hoja quedaba flotando sobre un vacío negro.
            var quickAdd by remember { mutableStateOf<Screen.QuickAdd?>(null) }
            // Sube cada vez que se guarda algo desde la hoja. Las pantallas que leen datos lo
            // usan como key de su LaunchedEffect — sin esto, la de atrás (que ahora nunca sale de
            // la composición) seguiría mostrando la lista de antes de guardar. Ver [LocalRefreshTick].
            var refreshTick by remember { mutableStateOf(0) }

            val navigate: (Screen) -> Unit = { screen ->
                if (opensAsOverlay(screen)) {
                    quickAdd = screen as Screen.QuickAdd
                } else {
                    // Navegar a otra parte cierra la hoja: desde adentro de Agregar se puede
                    // saltar a Escanear recibo, y dejarla abierta encima del destino nuevo sería
                    // una hoja huérfana sobre una pantalla que no la pidió.
                    quickAdd = null
                    if (NavStack.shouldPush(backStack, screen)) backStack.add(screen)
                }
            }
            val goBack: () -> Unit = {
                // La hoja primero: el botón «atrás» del teléfono tiene que cerrar la modal antes
                // de tocar la pila, igual que haría con cualquier diálogo.
                if (quickAdd != null) quickAdd = null
                else if (backStack.size > 1) backStack.removeLast()
            }
            // F22: «volver» real para las flechas ‹ de cada pantalla. Si hay
            // historial, saca el tope de la pila (vuelve a donde de verdad
            // estabas); si la pila tiene un solo elemento (deep link, recarga de
            // la web en una pantalla que no es Inicio), cae al destino de reserva
            // que pasa cada pantalla.
            // Ola 2 #8: `remember` — sin esto la lambda se creaba de nuevo en CADA
            // recomposición y, como LocalGoBack es un staticCompositionLocalOf, un valor
            // "nuevo" (aunque haga lo mismo) invalida TODO el subárbol que lo consume, no
            // solo lo que de verdad cambió. `backStack` es estable (viene de un `remember`
            // de arriba), así que la lambda puede vivir una sola vez por toda la composición.
            val goBackTo: (Screen) -> Unit = remember {
                { fallback ->
                    when (val result = NavStack.back(backStack, fallback)) {
                        NavStack.BackResult.Pop -> backStack.removeLast()
                        is NavStack.BackResult.Fallback ->
                            if (backStack.last() != result.screen) backStack[backStack.lastIndex] = result.screen
                    }
                }
            }

            BackHandlerEffect(enabled = quickAdd != null || backStack.size > 1, onBack = goBack)

            LaunchedEffect(SessionManager.loggedIn) {
                if (!SessionManager.loggedIn) {
                    backStack.clear()
                    backStack.add(Screen.Login)
                }
            }

            val saveableStateHolder = rememberSaveableStateHolder()
            // Outer Box paints the background full-bleed across desktop.
            // Compact (< 840dp): the classic centered "phone column" capped at
            // 600dp, with ONE MinBottomNav drawn here under the active screen
            // (Ola 4: the screens no longer draw their own). Expanded: a root
            // MinNavRail on the left with the same capped column centered in the
            // remaining space. Both read the active tab from navTabFor().
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().background(MinBg),
            ) {
                val widthClass = if (maxWidth < 840.dp) WindowWidthClass.Compact else WindowWidthClass.Expanded
                val activeTab = navTabFor(currentScreen)
                val showRail = widthClass == WindowWidthClass.Expanded && activeTab != null
                val showBottomNav = widthClass == WindowWidthClass.Compact && activeTab != null
                val onTabSelected: (NavTab) -> Unit = { tab -> navigate(screenForTab(tab)) }

                CompositionLocalProvider(
                    LocalWindowWidthClass provides widthClass,
                    LocalRefreshTick provides refreshTick,
                ) {
                Row(modifier = Modifier.fillMaxSize()) {
                if (showRail) {
                    MinNavRail(active = activeTab, onTabSelected = onTabSelected)
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxSize().statusBarsPadding()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                saveableStateHolder.SaveableStateProvider(key = currentScreen.toString()) {
                CompositionLocalProvider(LocalGoBack provides goBackTo) {
                when (currentScreen) {
                Screen.Login             -> LoginScreen(navigate)
                Screen.Register          -> RegisterScreen(navigate)
                Screen.OnboardingWelcome -> WelcomeScreen(navigate)
                Screen.OnboardingProfile -> OnboardingProfileScreen(navigate)
                Screen.Dashboard         -> DashboardScreen(navigate)
                Screen.Transactions      -> TransactionsScreen(navigate)
                // Inalcanzable: `navigate` desvía QuickAdd al overlay de más abajo antes de que
                // llegue a la pila (ver [opensAsOverlay]). La rama existe para que el `when` siga
                // siendo exhaustivo sobre `Screen` — con un `else` perdería el chequeo que avisa
                // cuando alguien agrega una pantalla nueva y se olvida de enrutarla.
                is Screen.QuickAdd       -> Unit
                Screen.Profile           -> PerfilScreen(
                    onNavigate = navigate,
                    onLogout = {
                        SessionManager.clear()
                        backStack.clear()
                        backStack.add(Screen.Login)
                    },
                )
                Screen.AIChat            -> AIChatScreen(navigate)
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
                is Screen.AccountDetail -> AccountDetailScreen(
                    onNavigate = navigate,
                    accountId = currentScreen.accountId,
                    group = currentScreen.group,
                )
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
                } // CompositionLocalProvider(LocalGoBack)
                } // SaveableStateProvider

                // La hoja de Agregar, DENTRO del área de contenido y encima de la pantalla que
                // ya estaba: su propio fondo oscuro atenúa lo de atrás, y como este Box es el
                // hueco de la pantalla (arriba de la barra inferior, a la derecha del rail), la
                // hoja queda centrada respecto del contenido en vez de pegada a un borde.
                // La pantalla de atrás no se descompone —sigue en `SaveableStateProvider`— así
                // que vuelve intacta al cerrar la hoja.
                quickAdd?.let { request ->
                    CompositionLocalProvider(LocalGoBack provides goBackTo) {
                        QuickAddScreen(
                            onDismiss = { quickAdd = null },
                            // Guardar cierra la hoja Y avisa: la pantalla de atrás nunca salió de
                            // la composición, así que sin este aviso sigue mostrando la lista de
                            // antes y la app parece decir que no se guardó nada.
                            onSaved = { refreshTick++; quickAdd = null },
                            onNavigate = navigate,
                            presetAccountId = request.presetAccountId,
                        )
                    }
                }
                } // screen slot
                if (showBottomNav) {
                    MinBottomNav(active = activeTab, onTabSelected = onTabSelected)
                }
                } // inner Column (max-width container + bottom nav)
                } // content area
                } // Row (rail + content)
                } // CompositionLocalProvider
            }
        }
    }
}
