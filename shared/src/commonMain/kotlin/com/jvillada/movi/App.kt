package com.jvillada.movi

import com.jvillada.movi.ui.ai.LoQueMoviSabeScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.jvillada.movi.theme.Movi
import com.jvillada.movi.data.TemaStore
import com.jvillada.movi.avisos.DestinoDesdeAfuera
import com.jvillada.movi.ui.papeles.ArchivoCompartido
import com.jvillada.movi.ui.papeles.EscucharArchivosSoltados
import com.jvillada.movi.ui.papeles.HojaLeyendoElPapel
import com.jvillada.movi.ui.papeles.PapelesCompartidos
import com.jvillada.movi.ui.papeles.sePuedeLeerLoCompartido
import com.jvillada.movi.data.RecurringOfferGate
import com.jvillada.movi.platform.AjustarBarrasDelSistema
import com.jvillada.movi.platform.BackHandlerEffect
import com.jvillada.movi.data.ArranqueDeSesion
import com.jvillada.movi.data.SessionManager
import com.jvillada.movi.data.decidirArranque
import com.jvillada.movi.platform.Huella
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.theme.MoviTheme
import com.jvillada.movi.ui.documentos.DocumentosScreen
import com.jvillada.movi.ui.compartir.CompartirScreen
import com.jvillada.movi.ui.LocalGoBack
import com.jvillada.movi.ui.LocalNavigate
import com.jvillada.movi.ui.LocalPilaDeHojas
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.AtrasCierraEstaHoja
import com.jvillada.movi.ui.PilaDeHojas
import com.jvillada.movi.ui.atras
import com.jvillada.movi.ui.hayAdondeVolver
import com.jvillada.movi.ui.NavStack
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.navTabFor
import com.jvillada.movi.ui.muestraLaNavegacion
import com.jvillada.movi.ui.opensAsOverlay
import com.jvillada.movi.ui.screenForTab
import com.jvillada.movi.ui.auth.LoginScreen
import com.jvillada.movi.ui.auth.RegisterScreen
import com.jvillada.movi.ui.ai.AIChatScreen
import com.jvillada.movi.ui.plan.PlanScreen
import com.jvillada.movi.ui.budgets.PresupuestosScreen
import com.jvillada.movi.ui.periodos.PeriodosListaYDetalle
import com.jvillada.movi.ui.categorias.CategoriasScreen
import com.jvillada.movi.ui.destinos.DestinosScreen
import com.jvillada.movi.ui.credits.CreditosScreen
import com.jvillada.movi.ui.credits.SalidaDeDeudasScreen
import com.jvillada.movi.ui.dashboard.DashboardScreen
import com.jvillada.movi.ui.dashboard.PrimerosPasosScreen
import com.jvillada.movi.ui.goals.MetasScreen
import com.jvillada.movi.ui.extractos.ExtractosScreen
import com.jvillada.movi.ui.mas.MasScreen
import com.jvillada.movi.ui.profile.PerfilScreen
import com.jvillada.movi.ui.quickadd.QuickAddScreen
import com.jvillada.movi.ui.recurrentes.CreateRecurringRuleSheet
import com.jvillada.movi.ui.recurrentes.RecurringOfferBar
import com.jvillada.movi.ui.recurrentes.RecurringPrefill
import com.jvillada.movi.ui.sms.CapturaDelBancoScreen
import com.jvillada.movi.ui.porrevisar.PorRevisarScreen
import com.jvillada.movi.ui.sms.SMSReconcileScreen
import com.jvillada.movi.ui.transactions.TransactionsScreen
import com.jvillada.movi.ui.accounts.AccountsScreen
import com.jvillada.movi.ui.accounts.AccountDetailScreen
import com.jvillada.movi.ui.cuadre.CuadreDeSaldosScreen
import com.jvillada.movi.ui.extractos.StatementReviewScreen
import com.jvillada.movi.ui.extractos.ImportDetailScreen
import com.jvillada.movi.ui.sdui.editor.ScreenEditorScreen
import com.jvillada.movi.shared.model.StatementParseResult
import com.jvillada.movi.ui.components.LocalRelevoDeScroll
import com.jvillada.movi.ui.components.LocalWindowWidthClass
import com.jvillada.movi.ui.components.MinBottomNav
import com.jvillada.movi.ui.components.MinNavRail
import com.jvillada.movi.ui.components.anchoMaximoDeLaPantalla
import com.jvillada.movi.ui.components.CascaraDeAncho
import com.jvillada.movi.ui.components.AnfitrionDeHojas
import com.jvillada.movi.ui.components.LocalAnfitrionDeHojas
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.components.RelevoDeScroll
import com.jvillada.movi.ui.components.WindowWidthClass
import com.jvillada.movi.ui.components.elRelevoDeScrollAplica
import com.jvillada.movi.ui.components.recibeElScrollDeLosMargenes
import com.jvillada.movi.ui.components.elTecladoEstaALaVista
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

@Composable
fun App() {
    // El único lugar de la app que lee el tema elegido. Es `mutableStateOf`, así que tocar el
    // interruptor de Perfil recompone desde acá para abajo — o sea, todo.
    MoviTheme(oscuro = TemaStore.oscuro) {
        // La barra de estado la pinta el sistema, no Movi: hay que decirle de qué color va.
        AjustarBarrasDelSistema(TemaStore.oscuro)
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * 1.12f)
        ) {
            // **La puerta de «Entrar con huella».**
            //
            // Con el interruptor prendido, la app arranca en [Screen.Login] **aunque la sesión
            // esté viva**: es esa pantalla la que muestra el prompt del sistema y, con el dedo
            // aceptado, navega al Inicio sin pedir que se escriba nada. Sin interruptor —o en la
            // web y iOS, donde `Huella.deEsteAparato()` es null— esto vale [ArranqueDeSesion.SIN_PUERTA]
            // y el arranque es exactamente el de siempre.
            //
            // Se decide UNA vez, acá, y no en un efecto: un `LaunchedEffect` corre después de la
            // primera composición, o sea después de dibujar el Inicio con su plata a la vista, que
            // es justo lo que la puerta existe para evitar.
            val huella = Huella.deEsteAparato()
            val puerta = remember(huella) {
                if (huella == null) ArranqueDeSesion.SIN_PUERTA
                else decidirArranque(
                    haySesion = SessionManager.isLoggedIn,
                    huellaActivada = SessionManager.huellaActivada,
                    estado = huella.estado(),
                )
            }
            val backStack = remember {
                mutableStateListOf<Screen>(
                    if (SessionManager.isLoggedIn && puerta == ArranqueDeSesion.SIN_PUERTA) {
                        Screen.Dashboard
                    } else {
                        Screen.Login
                    }
                )
            }
            val currentScreen = backStack.last()

            // «Agregar» es una ventana modal encima de la pantalla actual, no un destino que la
            // reemplaza (ver [opensAsOverlay]). Vive en su propio estado y NO en `backStack`:
            // apilarla dejaba la pila en una pantalla sin pestaña (`navTabFor` → null) y App.kt
            // escondía el rail y la barra, así que la hoja quedaba flotando sobre un vacío negro.
            var quickAdd by remember { mutableStateOf<Screen.QuickAdd?>(null) }
            // Qué hojas modales hay abiertas AHORA, las de este archivo y las que dibuja cada
            // pantalla por su cuenta (Perfil y sus cuatro). Ver [PilaDeHojas].
            val pilaDeHojas = remember { PilaDeHojas() }
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
                    // saltar a crear una cuenta o a otra pantalla, y dejarla abierta encima del destino nuevo sería
                    // una hoja huérfana sobre una pantalla que no la pidió.
                    quickAdd = null
                    // Entrar (o crear la cuenta) REEMPLAZA la pila en vez de apilar encima: ver
                    // [NavStack.shouldReplaceAll] para el «atrás» que devolvía al formulario de
                    // entrada, y para el prompt de la huella que se abría solo al aterrizar ahí.
                    NavStack.navegar(backStack, screen)
                }
            }
            val goBack: () -> Unit = {
                // Las hojas primero: el botón «atrás» del teléfono tiene que cerrar la modal
                // antes de tocar la pila, igual que haría con cualquier diálogo. Quién está
                // encima de quién ya no se escribe acá — lo sabe [PilaDeHojas], donde cada hoja
                // se anota sola con `AtrasCierraEstaHoja`. Antes esta lista conocía DOS hojas
                // (las que viven en este archivo) y las cuatro de Perfil no existían para el
                // «atrás»: apretarlo sacaba el tope de la pila y se perdía lo tipeado.
                atras(pilaDeHojas, backStack)
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

            // Ola 1 · Movi avisa: tocar un aviso del teléfono abre su pantalla («Por revisar» o
            // Plan). La `MainActivity` lo deja en [DestinoDesdeAfuera] y acá se cumple **solo con
            // sesión y fuera del login**: con «Entrar con huella» la app arranca en el login aunque
            // haya sesión, y abrir la bandeja encima sería saltarse la puerta. Cuando el dedo pasa y
            // la pila llega al Inicio, el efecto vuelve a correr y navega.
            val destinoPendiente = DestinoDesdeAfuera.pendiente
            LaunchedEffect(destinoPendiente, currentScreen, SessionManager.loggedIn) {
                val destino = destinoPendiente ?: return@LaunchedEffect
                if (!sePuedeLeerLoCompartido(SessionManager.loggedIn, currentScreen)) return@LaunchedEffect
                DestinoDesdeAfuera.pendiente = null
                if (currentScreen != destino) navigate(destino)
            }

            // Ola 2 · «Compartir con Movi»: lo compartido desde otra app (o subido en Por revisar /
            // Agregar, o soltado sobre la web) espera en [PapelesCompartidos] y se lee **después** de
            // la puerta, con la misma condición que el destino de un aviso: con «Entrar con huella»
            // la app arranca en el login, y subir el archivo ahí sería saltársela.
            var papelesALeer by remember { mutableStateOf<List<ArchivoCompartido>?>(null) }
            // En la web, además, se puede soltar el archivo sobre la ventana.
            EscucharArchivosSoltados()
            val hayPapeles = PapelesCompartidos.pendientes.isNotEmpty()
            LaunchedEffect(hayPapeles, papelesALeer, currentScreen, SessionManager.loggedIn) {
                if (!hayPapeles || papelesALeer != null) return@LaunchedEffect
                if (!sePuedeLeerLoCompartido(SessionManager.loggedIn, currentScreen)) return@LaunchedEffect
                // Agregar se cierra: la hoja de lectura va encima de la pantalla, no de otra hoja.
                quickAdd = null
                papelesALeer = PapelesCompartidos.tomarTodos()
            }

            BackHandlerEffect(
                enabled = hayAdondeVolver(pilaDeHojas, backStack),
                onBack = goBack,
            )

            LaunchedEffect(SessionManager.loggedIn) {
                if (!SessionManager.loggedIn) {
                    // Las hojas ANTES que la pila. Sin esto, si el token vencía con «Agregar»
                    // abierta y un monto escrito, la pila se reseteaba a Login y la hoja seguía
                    // pintada encima: se podía seguir tipeando y tocar Guardar contra una sesión
                    // que ya no existía.
                    quickAdd = null
                    hojaRecurrentePrellenada = null
                    ofrecimientoRecurrente = null
                    movimientoRecienGuardado = null
                    papelesALeer = null
                    backStack.clear()
                    backStack.add(Screen.Login)
                }
            }

            val saveableStateHolder = rememberSaveableStateHolder()
            // El fondo va de borde a borde en toda ventana. La clase de ancho se decide UNA vez,
            // acá ([CascaraDeAncho]), y le llega a toda la app por `LocalWindowWidthClass`; el
            // esqueleto —rail, columna topada, barra inferior— lo arma [EsqueletoDeLaCascara].
            CascaraDeAncho(
                modifier = Modifier.fillMaxSize().background(Movi.colores.fondo),
            ) {
                val activeTab = navTabFor(currentScreen)
                // Ola C: Ajustes y lo que cuelga de ahí no marcan pestaña pero sí llevan la
                // barra — ver [muestraLaNavegacion].
                val conNavegacion = muestraLaNavegacion(currentScreen)
                val onTabSelected: (NavTab) -> Unit = { tab -> navigate(screenForTab(tab)) }

                // Los márgenes a los lados de la columna reenvían la rueda del mouse a la lista de
                // la pantalla activa. Ver [RelevoDeScroll] para el bug y el porqué.
                val relevoDeScroll = remember { RelevoDeScroll() }
                CompositionLocalProvider(
                    LocalRefreshTick provides refreshTick,
                    LocalRelevoDeScroll provides relevoDeScroll,
                    // Para que cualquier pantalla pueda anotar su hoja sin que App.kt tenga que
                    // conocerla (Perfil y sus cuatro overlays). Ver [PilaDeHojas].
                    LocalPilaDeHojas provides pilaDeHojas,
                ) {
                EsqueletoDeLaCascara(
                    pantalla = currentScreen,
                    activeTab = activeTab,
                    conNavegacion = conNavegacion,
                    onTabSelected = onTabSelected,
                    relevoDeScroll = relevoDeScroll,
                ) {
                saveableStateHolder.SaveableStateProvider(key = currentScreen.toString()) {
                CompositionLocalProvider(LocalGoBack provides goBackTo, LocalNavigate provides navigate) {
                when (currentScreen) {
                Screen.Login             -> LoginScreen(navigate)
                Screen.Register          -> RegisterScreen(navigate)
                Screen.Dashboard         -> DashboardScreen(navigate)
                is Screen.Transactions   -> TransactionsScreen(
                    navigate,
                    chipInicial = currentScreen.chipInicial,
                    periodoInicial = currentScreen.periodoInicial,
                )
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
                is Screen.AIChat         -> AIChatScreen(navigate, preguntaInicial = currentScreen.preguntaInicial)
                Screen.Credits           -> CreditosScreen(navigate)
                Screen.SalidaDeDeudas    -> SalidaDeDeudasScreen(navigate)
                Screen.Goals             -> MetasScreen(navigate)
                Screen.Budgets           -> PresupuestosScreen(navigate)
                is Screen.Plan           -> PlanScreen(navigate, segmento = currentScreen.segmento)
                Screen.Categorias        -> CategoriasScreen(navigate)
                is Screen.Destinos       -> DestinosScreen(navigate, abrir = currentScreen.abrir)
                Screen.Documentos        -> DocumentosScreen(navigate)
                Screen.Compartir         -> CompartirScreen(navigate)
                Screen.LoQueMoviSabe     -> LoQueMoviSabeScreen(navigate)
                Screen.PrimerosPasos     -> PrimerosPasosScreen(navigate)
                Screen.PorRevisar        -> PorRevisarScreen(navigate)
                Screen.CapturaDelBanco   -> CapturaDelBancoScreen(navigate)
                is Screen.SMSReconcile   -> SMSReconcileScreen(navigate, currentScreen.smsId)
                Screen.Mas               -> MasScreen(navigate)
                Screen.Extractos         -> ExtractosScreen(navigate)
                Screen.Accounts         -> AccountsScreen(navigate)
                Screen.CuadreDeSaldos   -> CuadreDeSaldosScreen(navigate)
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
                // Ola W2: en el teléfono son las dos pantallas de siempre; en la web, una lista con
                // el detalle del período elegido al lado (ver [PeriodosListaYDetalle]).
                Screen.Periodos          -> PeriodosListaYDetalle(navigate, idPedido = null)
                is Screen.DetalleDePeriodo -> PeriodosListaYDetalle(navigate, idPedido = currentScreen.id)
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
                    // La línea que pone esta hoja bajo el «atrás» del sistema. Ver [PilaDeHojas].
                    AtrasCierraEstaHoja { quickAdd = null }
                    CompositionLocalProvider(LocalGoBack provides goBackTo, LocalNavigate provides navigate) {
                        QuickAddScreen(
                            onDismiss = { quickAdd = null },
                            // Guardar cierra la hoja Y avisa: la pantalla de atrás nunca salió de
                            // la composición, así que sin este aviso sigue mostrando la lista de
                            // antes y la app parece decir que no se guardó nada.
                            onSaved = { refreshTick++; quickAdd = null },
                            onNavigate = navigate,
                            presetAccountId = request.presetAccountId,
                            // El resto del prellenado: lo manda el checklist del período cuando
                            // una fila sin movimiento ofrece «Anotar este pago». Ver
                            // [Screen.QuickAdd].
                            presetNota = request.presetNota,
                            presetMonto = request.presetMonto,
                            presetCategoria = request.presetCategoria,
                            presetFecha = request.presetFecha,
                            presetEsIngreso = request.presetEsIngreso,
                            presetDeudaId = request.presetDeudaId,
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
                // «Movi está leyendo tu comprobante…». Encima de la pantalla, como Agregar.
                papelesALeer?.let { archivos ->
                    CompositionLocalProvider(LocalGoBack provides goBackTo, LocalNavigate provides navigate) {
                        HojaLeyendoElPapel(
                            archivos = archivos,
                            onCerrar = { papelesALeer = null },
                            onNavigate = navigate,
                            // Una propuesta nueva en Por revisar: la pantalla de atrás se vuelve a leer.
                            onCambio = { refreshTick++ },
                        )
                    }
                }
                hojaRecurrentePrellenada?.let { propuesta ->
                    // Se compone DESPUÉS de la de Agregar, así que se anota después: cuando las
                    // dos están abiertas es la de arriba, y el «atrás» la cierra primero.
                    AtrasCierraEstaHoja { hojaRecurrentePrellenada = null }
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
                } // EsqueletoDeLaCascara (el hueco de la pantalla)
                } // CompositionLocalProvider
            }
        }
    }
}

/**
 * # El esqueleto de la cáscara: rail, columna topada y barra inferior
 *
 * Lee la clase de ancho que decidió [CascaraDeAncho] y arma alrededor de [contenido] (la pantalla
 * activa y las hojas que App.kt dibuja encima):
 *
 * - **Compacto** (< 600 dp): la columna va llena y [MinBottomNav] debajo.
 * - **Mediano** (600–999 dp): [MinNavRail] compacto a la izquierda, sin barra inferior.
 * - **Expandido** (≥ 1000 dp): el rail ancho de siempre.
 *
 * En los dos anchos la columna se topa según la [disposicionDe] de la pantalla (ver
 * [anchoMaximoDeLaPantalla]) y se centra en lo que deja el rail. Vive en este archivo y no en
 * `ui/components` porque dos pruebas de fuente (`ElTecladoNoTapaLoQueEscribisTest`) leen App.kt
 * buscando las dos líneas que protegen el teclado.
 */
@Composable
internal fun EsqueletoDeLaCascara(
    pantalla: Screen,
    activeTab: NavTab?,
    conNavegacion: Boolean,
    onTabSelected: (NavTab) -> Unit,
    relevoDeScroll: RelevoDeScroll,
    contenido: @Composable BoxScope.() -> Unit,
) {
    val widthClass = LocalWindowWidthClass.current
    val showRail = widthClass != WindowWidthClass.Compact && conNavegacion
    val showBottomNav = widthClass == WindowWidthClass.Compact && conNavegacion
    val tecladoALaVista = elTecladoEstaALaVista()
    // Las hojas centradas (mediano y expandido) se dibujan acá, encima de todo — ver
    // [AnfitrionDeHojas]. Así su velo tapa también el rail y los márgenes.
    val anfitrionDeHojas = remember { AnfitrionDeHojas() }
    CompositionLocalProvider(LocalAnfitrionDeHojas provides anfitrionDeHojas) {
    Box(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (showRail) {
            MinNavRail(
                active = activeTab,
                onTabSelected = onTabSelected,
                compacto = widthClass == WindowWidthClass.Medium,
            )
        }
        Box(
            // Solo donde hay márgenes: en el teléfono el relevo era un segundo `scrollable` sobre
            // toda la pantalla y se sentía como que la lista «se pegaba» al llegar al final. Ver
            // [elRelevoDeScrollAplica].
            modifier = Modifier.weight(1f).fillMaxHeight().let {
                if (elRelevoDeScrollAplica(widthClass)) it.recibeElScrollDeLosMargenes(relevoDeScroll) else it
            },
            contentAlignment = Alignment.TopCenter,
        ) {
            // **`imePadding()` es lo que hace que el teclado no tape lo que estás
            // escribiendo.** Movi dibuja de borde a borde (`enableEdgeToEdge`), y con eso el
            // `adjustResize` del manifiesto deja de encoger la ventana: Android manda el alto
            // del teclado como un inset y la app tiene que descontarlo. Nadie lo descontaba, y
            // el chat de Movi AI se abría con el campo de texto debajo del teclado.
            //
            // Va acá, en la columna raíz, y no en cada pantalla: el agujero era de TODAS las
            // que tienen un campo abajo, y una sola línea las cubre a todas — incluidas las
            // hojas, que se dibujan adentro de este mismo hueco.
            // El tope depende de la pantalla y del ancho (ver `anchoMaximoDeLaPantalla`).
            // Desde 600 dp no hay barra inferior que descuente la barra de navegación del sistema
            // (la de Android, abajo o al costado en horizontal): la descuenta la columna.
            val sinBarraInferior = if (widthClass == WindowWidthClass.Compact) Modifier else Modifier.navigationBarsPadding()
            Column(modifier = Modifier.widthIn(max = anchoMaximoDeLaPantalla(pantalla, widthClass)).fillMaxSize().statusBarsPadding().then(sinBarraInferior).imePadding()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), content = contenido)

                // Y con el teclado arriba la barra se esconde: no sirve para nada mientras se
                // escribe, y son 60 dp de los pocos que quedan — con ella puesta, en un teléfono
                // chico el campo de texto queda a un renglón del borde.
                if (showBottomNav && !tecladoALaVista) {
                    MinBottomNav(active = activeTab, onTabSelected = onTabSelected)
                }
            }
        }
    }
    anfitrionDeHojas.Hojas()
    } // Box (esqueleto + hojas centradas)
    } // CompositionLocalProvider(LocalAnfitrionDeHojas)
}
