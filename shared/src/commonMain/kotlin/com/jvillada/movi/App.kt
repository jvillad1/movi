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
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.MinBg
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.documentos.DocumentosScreen
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.LocalNavigate
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
import com.jvillada.movi.ui.categorias.CategoriasScreen
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.dashboard.DashboardScreen
import com.jvillada.movi.ui.dashboard.PrimerosPasosScreen
import com.jvillada.movi.ui.goals.MetasScreen
import com.jvillada.movi.ui.extractos.ExtractosScreen
import com.jvillada.movi.ui.mas.MasScreen
import com.jvillada.movi.ui.ocr.OCRCaptureScreen
import com.jvillada.movi.ui.ocr.OCRConfirmScreen
import com.jvillada.movi.ui.onboarding.OnboardingProfileScreen
import com.jvillada.movi.ui.onboarding.WelcomeScreen
import com.jvillada.movi.ui.profile.PerfilScreen
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet
import com.jvillada.movi.ui.recurrentes.RecurrentesScreen
import com.jvillada.movi.ui.recurrentes.RecurringOfferBar
import com.jvillada.movi.ui.recurrentes.RecurringPrefill
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
import kotlinx.coroutines.delay
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
            // Ola 9 · B — «¿esto se repite todos los meses?», ofrecido DESPUÉS de guardar.
            //
            // Vive acá, y no en la hoja de Agregar, por una razón concreta: guardar cierra esa
            // hoja, así que cualquier cosa que ella misma mostrara se iría con ella. Acá el
            // movimiento ya está guardado y el ofrecimiento es independiente de todo lo demás:
            // ignorarlo, cerrarlo o irse a otra pantalla no pierde nada.
            var movimientoRecienGuardado by remember { mutableStateOf<FinancialEvent?>(null) }
            var ofrecimientoRecurrente by remember { mutableStateOf<RecurringPrefill?>(null) }
            var hojaRecurrentePrellenada by remember { mutableStateOf<RecurringPrefill?>(null) }

            // Keyed por el ID del movimiento: dos guardados distintos vuelven a evaluar, y una
            // recomposición con el mismo movimiento no. Y el resultado solo se ASIGNA si hay algo
            // que ofrecer: si esto pudiera escribir `null`, una segunda pasada sobre el mismo
            // movimiento (que el gate ya descarta por "ya se ofreció") apagaría la barra que la
            // primera acababa de encender. Se vio pasar.
            LaunchedEffect(movimientoRecienGuardado?.id) {
                val evento = movimientoRecienGuardado ?: return@LaunchedEffect
                // Las guardas (traspaso, ya existe, ya se ofreció esta cosa) viven en el gate.
                RecurringOfferGate.ofrecerPara(evento)?.let { ofrecimientoRecurrente = it }
            }
            // La barra se esconde mientras haya una hoja abierta encima (ver más abajo).
            val ofrecimientoALaVista = ofrecimientoRecurrente != null &&
                quickAdd == null && hojaRecurrentePrellenada == null
            // Se va sola. Es la mitad del diseño: si la barra se quedara hasta que alguien la
            // cierre, "ignorarla" costaría un toque y anotar el almuerzo de todos los días
            // sería una molestia diaria. Así, no contestar ES la respuesta.
            //
            // La cuenta corre solo mientras la barra se VE: si el dueño abrió otra hoja encima,
            // el ofrecimiento lo espera en vez de vencerse a espaldas suyas.
            LaunchedEffect(ofrecimientoRecurrente, ofrecimientoALaVista) {
                if (!ofrecimientoALaVista) return@LaunchedEffect
                delay(12_000)
                ofrecimientoRecurrente = null
            }

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
                // Las hojas primero: el botón «atrás» del teléfono tiene que cerrar la modal
                // antes de tocar la pila, igual que haría con cualquier diálogo. La de crear el
                // recurrente va ANTES que la de Agregar porque, cuando las dos existen, es la
                // que está encima — y sin ella acá el «atrás» desde Inicio se salía de la app
                // con el formulario a medio llenar (Ola 9 · B).
                if (hojaRecurrentePrellenada != null) hojaRecurrentePrellenada = null
                else if (quickAdd != null) quickAdd = null
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

            BackHandlerEffect(
                enabled = quickAdd != null || hojaRecurrentePrellenada != null || backStack.size > 1,
                onBack = goBack,
            )

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
                CompositionLocalProvider(LocalGoBack provides goBackTo, LocalNavigate provides navigate) {
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
                Screen.Categorias        -> CategoriasScreen(navigate)
                Screen.Documentos        -> DocumentosScreen(navigate)
                Screen.PrimerosPasos     -> PrimerosPasosScreen(navigate)
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
                    CompositionLocalProvider(LocalGoBack provides goBackTo, LocalNavigate provides navigate) {
                        QuickAddScreen(
                            onDismiss = { quickAdd = null },
                            // Guardar cierra la hoja Y avisa: la pantalla de atrás nunca salió de
                            // la composición, así que sin este aviso sigue mostrando la lista de
                            // antes y la app parece decir que no se guardó nada.
                            onSaved = { refreshTick++; quickAdd = null },
                            onNavigate = navigate,
                            presetAccountId = request.presetAccountId,
                            // Ola 9 · B: el movimiento ya se guardó; recién ahora se evalúa si
                            // vale la pena ofrecer el recurrente.
                            onSavedEvent = { movimientoRecienGuardado = it },
                        )
                    }
                }
                // La barra del ofrecimiento: encima del contenido y siempre por dentro del
                // ancho de la columna — en angosto ocupa el ancho completo y en laptop queda
                // alineada con el contenido, no pegada al borde de la ventana.
                //
                // **Se esconde mientras haya una hoja abierta.** Normalmente para cuando
                // aparece ya se cerró la de Agregar, pero el dueño puede volver a abrirla dentro
                // de los 12 segundos: en un `Box` los hijos posteriores se pintan ENCIMA, así
                // que sin esta condición la barra le quedaba flotando sobre el teclado numérico.
                // No se pierde nada: el ofrecimiento sigue vivo y vuelve a verse al cerrar.
                ofrecimientoRecurrente?.takeIf { ofrecimientoALaVista }?.let { propuesta ->
                    RecurringOfferBar(
                        prefill = propuesta,
                        onAccept = {
                            hojaRecurrentePrellenada = propuesta
                            ofrecimientoRecurrente = null
                            // Le sirvió: esta barra no cuenta como insistencia perdida, así que
                            // el techo por categoría no se le gasta a quien está justamente
                            // usando la función (ver la guarda 3 en `RecurringOffer.kt`).
                            RecurringOfferGate.seTomo(propuesta)
                        },
                        onDismiss = { ofrecimientoRecurrente = null },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                hojaRecurrentePrellenada?.let { propuesta ->
                    CreateRecurringRuleSheet(
                        onDismiss = { hojaRecurrentePrellenada = null },
                        onSaved = {
                            hojaRecurrentePrellenada = null
                            // Lo que el gate tenía cacheado quedó viejo: sin esto, anotar el
                            // arriendo del mes que viene volvería a ofrecer crear el recurrente
                            // que se acaba de crear.
                            RecurringOfferGate.olvidarLoCacheado()
                            refreshTick++
                        },
                        prefill = propuesta,
                    )
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
