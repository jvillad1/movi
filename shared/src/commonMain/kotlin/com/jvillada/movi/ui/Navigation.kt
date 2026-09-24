package com.jvillada.movi.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.ui.components.NavTab
import com.jvillada.movi.ui.plan.SEGMENTO_PAGOS
import com.jvillada.movi.ui.transactions.CHIP_RECURRENTES

sealed class Screen {
    data object Login            : Screen()
    data object Register         : Screen()
    data object Dashboard : Screen()
    /**
     * Movimientos. [chipInicial] es el índice del chip con el que arranca (ver
     * `CHIPS_DE_MOVIMIENTOS` y [com.jvillada.movi.ui.transactions.chipInicialDeMovimientos]);
     * `null` —lo normal, entrar por la pestaña— es «Todo».
     *
     * PR 3 del rediseño de Recurrentes (2026-09): existe porque los enlaces que llevaban a la
     * pantalla de Recurrentes ahora llevan acá con el chip «Recurrentes» puesto. Sin el
     * parámetro, tocar «Ver todos» sobre un pago que vence aterrizaba en la lista completa de
     * movimientos, sin ninguna relación visible con lo que se acababa de tocar.
     *
     * Es un `data class` y no un `data object` por eso, con el mismo precedente que
     * [QuickAdd]: el valor viaja en la pila, así que dos entradas con chips distintos son
     * pantallas distintas para [NavStack.shouldPush] y para el `SaveableStateProvider` de
     * App.kt — volver desde Recurrentes-filtrado a la pestaña deja Movimientos sin filtro, que
     * es lo correcto.
     *
     * Ola C: el chip «Recurrentes» ya no existe en Movimientos, y pedir
     * `Transactions(CHIP_RECURRENTES)` lleva a [Plan] (ver [destinoVigente]). El parámetro sigue
     * sirviendo para los modos sin chip («Por confirmar», «Entre cuentas»).
     */
    data class Transactions(val chipInicial: Int? = null) : Screen()
    /**
     * La hoja de «Agregar», opcionalmente **prellenada**.
     *
     * F10 abrió esta puerta con un solo dato: cuando se entra «para registrar el primero» desde el
     * detalle de una cuenta puntual, esa cuenta viene preseleccionada — sin [presetAccountId],
     * QuickAdd caía siempre en la primera cuenta de la lista, sin importar desde dónde se entró.
     *
     * El resto de los `preset*` son la misma idea llevada hasta el final, y nacen del checklist del
     * período: ahí una fila sin movimiento ofrece **«Anotar el movimiento»**, y lo que hace es
     * abrir esta hoja con lo que el recurrente ya sabe (el nombre, el monto, la categoría, la
     * cuenta y la fecha en que vencía). Sin ellos, «anotar el movimiento» significaba teclear a
     * mano cinco datos que la app tenía en pantalla — y cualquiera de los cinco escrito distinto
     * rompe el emparejamiento automático que después tendría que tildar la fila sola.
     *
     * **Todos son sugerencias, no imposiciones.** La hoja los pone en sus campos y el dueño los
     * corrige si hacen falta; nada se guarda sin que toque «Guardar movimiento».
     *
     * @param presetMonto en la moneda de la cuenta que se elija, igual que si lo hubiera tecleado.
     *   `null` cuando no hay un monto que sugerir con honestidad — el «monto» de una tarjeta es su
     *   SALDO, no lo que se va a pagar (ver `RecurringRule.montoEsSaldo`).
     * @param presetEsIngreso abre la hoja en la pestaña «Ingreso» en vez de «Gasto». Un sueldo no
     *   se paga: llega.
     * @param presetFecha ISO `"2026-09-05"`. La fecha en que ese recurrente vencía, que es la que
     *   hace que el movimiento caiga en el período correcto — el default de la hoja es hoy, y con
     *   una fila vencida hace dos semanas ese default sella el mes equivocado.
     */
    data class QuickAdd(
        val presetAccountId: String? = null,
        val presetNota: String? = null,
        val presetMonto: Long? = null,
        val presetCategoria: String? = null,
        val presetFecha: String? = null,
        val presetEsIngreso: Boolean = false,
    ) : Screen()
    data object Profile : Screen()
    /**
     * Movi AI, opcionalmente **con una pregunta ya lista**.
     *
     * [preguntaInicial] existe para la entrega B de «Movi de un vistazo»: el Inicio muestra las tres
     * preguntas sugeridas (ver `preguntasSugeridas`) y tocar una tiene que llevar al chat con esa
     * pregunta ya hecha, no a un chat en blanco donde el dueño tenga que volver a escribirla. La
     * pantalla la **manda sola, una vez**, apenas abre: tocar el chip ya fue la decisión de
     * preguntar, y pedirle un segundo toque sería hacerle confirmar lo que acaba de hacer.
     *
     * `null` —lo normal, entrar por Más— abre el chat vacío con sus sugerencias.
     *
     * `data class` y no `data object` por el mismo precedente que [QuickAdd] y [Transactions]: el
     * valor viaja en la pila, así que dos entradas con preguntas distintas son pantallas distintas
     * para [NavStack.shouldPush] y para el `SaveableStateProvider` de App.kt.
     */
    data class AIChat(val preguntaInicial: String? = null) : Screen()
    data object Credits : Screen()
    data object Goals : Screen()
    /**
     * Presupuestos suelto. Ola C: su cuerpo es el mismo que el segmento «Presupuestos» de [Plan]
     * (ver `rememberEstadoDePresupuestos`), y todas las puertas que llevaban acá —el destino SDUI
     * `"budgets"`, las alertas de presupuesto superado, la revisión del período— llevan ahora a
     * `Plan(SEGMENTO_PRESUPUESTOS)`. Se conserva para una pila que todavía la traiga; marca la
     * pestaña Plan y su flecha vuelve ahí.
     */
    data object Budgets : Screen()
    /**
     * **Plan** (ola C): «¿cuánto puedo gastar y qué me falta pagar?» — el disponible del período y,
     * debajo, dos segmentos: «Pagos del mes» (el tablero de Recurrentes) y «Presupuestos».
     *
     * [segmento] es con cuál arranca (`SEGMENTO_PAGOS` o `SEGMENTO_PRESUPUESTOS`, en
     * `ui/plan/PlanScreen.kt`). `data class` por el mismo precedente que [Transactions]: el valor
     * viaja en la pila, así que entrar a Presupuestos desde un acceso y a Pagos desde la pestaña
     * son dos pantallas para [NavStack.shouldPush] y para el `SaveableStateProvider` de App.kt.
     */
    data class Plan(val segmento: Int = SEGMENTO_PAGOS) : Screen()
    /**
     * Ola 10 — «Más → Categorías»: ver, renombrar, unificar, esconder y fijar el tipo.
     *
     * Se llega SOLO desde Más (ver [MasScreen]), y esa es toda la puerta que tiene. Precedente a
     * no repetir: al plegar Inversiones dentro de Cuentas, el historial de cada tarjeta quedó
     * inalcanzable porque su único `onNavigate` vivía en la pantalla que se borró. Acá la entrada
     * es una ficha de Más —una lista que nadie está borrando— y no un enlace escondido dentro de
     * otra pantalla.
     */
    data object Categorias : Screen()

    /**
     * **«Cuentas de otros»** — el registro de cuentas ajenas: verlas, registrarlas, renombrarlas
     * y borrarlas, y ver qué se le mandó a cada una.
     *
     * No vive dentro de `Screen.Accounts` **a propósito**, y no es una decisión de dibujo:
     * `Screen.Accounts` lista la plata del dueño, y una cuenta de otra persona no es su plata (ver
     * `DestinoConocido`). Ponerlas en la misma pantalla invitaría exactamente a la confusión que
     * el modelo evita.
     *
     * Ola C, tarea 4: la puerta es la tarjeta **«Te deben»** de Patrimonio (ver `SeccionDeTeDeben`
     * en `AccountsScreen.kt`) — antes de esta tarea no tenía ninguna, «Más» dejó de ser pestaña
     * (Task 3) y esta pantalla se quedó sin como llegar. Marca la pestaña Patrimonio (ver
     * [navTabFor]) y su flecha cae ahí, no en Ajustes.
     */
    data object Destinos : Screen()

    /**
     * «Documentos» — los papeles del dueño guardados en Movi (extractos, nóminas, contratos).
     * Ficha de Más, junto a «Extractos»: el importador archiva ahí lo que pasa por él.
     */
    data object Documentos : Screen()
    /**
     * Ola 14 — «Más → Primeros pasos»: la guía de arranque, que hasta acá solo existía como
     * tarjeta del Inicio y se apagaba sola sin ninguna forma de volver a verla. Misma puerta que
     * [Categorias]: una ficha de Más, no un enlace escondido adentro de otra pantalla.
     */
    data object PrimerosPasos : Screen()
    /**
     * **«Compartir»** — el enlace de solo lectura para un tercero (Caro, un asesor): crearlo,
     * mandarlo y revocarlo. Se llega desde **Más** (la puerta que no depende de nadie, como
     * [Destinos]) y desde el ícono de compartir del encabezado del **Inicio**, que es donde uno está
     * mirando su plata cuando se le ocurre mostrársela a alguien.
     */
    data object Compartir : Screen()
    /**
     * **«Cuadre de saldos»** — comparar de una sentada lo que Movi cree con lo que dice el banco,
     * y anotar la diferencia de cada cuenta como un ajuste.
     *
     * Se llega desde **Cuentas** (es la pantalla donde se leen esos saldos, así que el error y su
     * arreglo quedan a un toque), desde **Más** (la puerta que no depende de que ninguna otra
     * pantalla conserve su enlace — precedente de [Categorias] y [Destinos]) y desde el aviso del
     * Inicio cuando alguna cuenta lleva demasiado sin cuadrarse.
     */
    data object CuadreDeSaldos : Screen()

    data object OCRCapture : Screen()
    data object OCRConfirm : Screen()
    data object SMSInbox : Screen()
    data class SMSReconcile(val smsId: String) : Screen()
    data object Mas : Screen()
    data object Extractos : Screen()
    data object Accounts : Screen()
    /**
     * Detalle de una cuenta. Lleva el grupo de la cuenta además del id porque la navegación
     * necesita saber DÓNDE vive esa cuenta antes de cargarla del repositorio: una tarjeta o un
     * préstamo se abren desde Créditos (Ola 7) y una cuenta de dinero desde Cuentas, y de eso
     * dependen tanto la pestaña resaltada ([navTabFor]) como el destino de reserva de la flecha
     * ‹ ([homeScreenFor]). El grupo es obligatorio a propósito: quien navega acá ya tiene la
     * cuenta en la mano, y así ningún llamador nuevo puede olvidarse del dato.
     */
    data class AccountDetail(val accountId: String, val group: AccountGroup) : Screen()
    data class StatementReview(val resultJson: String) : Screen()
    data class ImportDetail(val importId: String) : Screen()
    data object ScreenEditor : Screen()
}

/**
 * A qué pestaña pertenece cada pantalla; `null` = ninguna pestaña marcada. App.kt lo usa para
 * resaltar el ítem activo en la barra (teléfono) y en el rail (pantalla ancha), que son los únicos
 * lugares donde se pinta la navegación — ninguna pantalla arma su propia barra.
 *
 * Ola C (2026-09): cuatro lugares, los mismos en el teléfono y en la web — **Hoy** (¿cómo estoy?),
 * **Movimientos** (¿qué pasó?), **Plan** (¿cuánto puedo gastar y qué me falta pagar?) y
 * **Patrimonio** (¿cuánto tengo y cuánto debo?). Cada pantalla marca la pestaña de la pregunta que
 * contesta: Presupuestos es Plan; Créditos, el cuadre, «Cuentas de otros» y el detalle de cualquier
 * cuenta son Patrimonio.
 *
 * «Más» dejó de ser pestaña: Ajustes y lo que se abre desde ahí (Perfil, Categorías, Documentos,
 * Compartir, Movi AI, los mensajes del banco…) se alcanzan tocando el avatar, así que no marcan
 * ninguna — pero **la barra sigue pintada** (ver [muestraLaNavegacion]): ninguna pestaña marcada
 * no es lo mismo que sin navegación.
 */
fun navTabFor(screen: Screen): NavTab? = when (screen) {
    Screen.Dashboard -> NavTab.HOY
    is Screen.Transactions -> NavTab.MOVIMIENTOS
    is Screen.Plan, Screen.Budgets -> NavTab.PLAN
    Screen.Accounts, Screen.Credits, Screen.CuadreDeSaldos, Screen.Destinos -> NavTab.PATRIMONIO
    // El detalle hereda la pestaña de la pantalla donde vive la cuenta — así resaltar y
    // «volver» no pueden contradecirse. Hoy las dos (Cuentas y Créditos) son Patrimonio.
    is Screen.AccountDetail -> navTabFor(homeScreenFor(screen.group))
    else -> null
}

/**
 * **Ajustes y lo que se abre desde ahí** — las pantallas a las que se llega por el avatar.
 *
 * Ola C: son las que hasta acá marcaban la pestaña «Más». No marcan ninguna pestaña (ver
 * [navTabFor]), pero siguen siendo pantallas de todos los días con la barra abajo: esconderla al
 * entrar a Perfil dejaría al dueño sin forma de saltar a Movimientos que no sea volver dos veces.
 */
fun esDeAjustes(screen: Screen): Boolean = when (screen) {
    Screen.Mas, Screen.Profile, Screen.Goals, Screen.Extractos, is Screen.AIChat,
    Screen.SMSInbox, is Screen.SMSReconcile, Screen.Categorias, Screen.PrimerosPasos,
    Screen.Documentos, Screen.Compartir -> true
    else -> false
}

/**
 * ¿Se pinta la barra (o el rail) debajo de esta pantalla? Sí en las cuatro pestañas y en Ajustes
 * ([esDeAjustes]); no en la autenticación ni en los flujos a pantalla completa (escanear un recibo,
 * revisar un extracto, el editor de pantallas).
 */
fun muestraLaNavegacion(screen: Screen): Boolean = navTabFor(screen) != null || esDeAjustes(screen)

/**
 * ¿Esta pantalla se abre como **ventana modal encima** de la actual, en vez de reemplazarla?
 *
 * Hoy solo [Screen.QuickAdd]. «Agregar» siempre fue una hoja —fondo oscuro clickeable y un panel
 * pegado abajo— pero se apilaba como una pantalla más, y ahí estaba el problema: [navTabFor] no
 * le da pestaña (no es un destino de la navegación), y App.kt solo pinta el rail y la barra
 * cuando hay pestaña activa. Resultado: abrir Agregar hacía desaparecer TODO el chrome. En el
 * teléfono se disimulaba (la hoja tapa casi toda la pantalla); en escritorio el rail se esfumaba
 * y la hoja quedaba flotando sobre un vacío negro, corrida hacia un borde.
 *
 * La corrección es de una línea conceptual: App.kt desvía a un estado de overlay cualquier
 * pantalla que devuelva `true` acá, en vez de apilarla. La pila no se toca, así que la pestaña
 * activa sigue siendo la de la pantalla de atrás —el rail y la barra siguen pintados— y la hoja
 * se dibuja adentro del área de contenido, centrada como el resto del contenido y atenuando lo
 * que hay debajo. Se resolvió así, y no sacando `QuickAdd` de [Screen], para no tocar los cinco
 * llamadores que ya navegan con `navigate(Screen.QuickAdd(...))`: el tipo sigue siendo la forma
 * de PEDIR la hoja; lo que cambió es cómo App.kt la atiende.
 */
fun opensAsOverlay(screen: Screen): Boolean = screen is Screen.QuickAdd

/**
 * Ola 7 (F61): dónde «vive» una cuenta en la navegación — la pantalla que la lista. Las deudas
 * (tarjetas y préstamos) se listan en Créditos; el resto, en Cuentas. Es la única regla: la usan
 * el destino de reserva del «volver» del detalle y [navTabFor] para la pestaña resaltada.
 */
fun homeScreenFor(group: AccountGroup): Screen =
    if (group == AccountGroup.DEUDA) Screen.Credits else Screen.Accounts

/** Pantalla principal de cada destino de la barra/rail (inversa de [navTabFor]). */
fun screenForTab(tab: NavTab): Screen = when (tab) {
    NavTab.HOY -> Screen.Dashboard
    NavTab.MOVIMIENTOS -> Screen.Transactions()
    NavTab.ADD -> Screen.QuickAdd()
    NavTab.PLAN -> Screen.Plan()
    NavTab.PATRIMONIO -> Screen.Accounts
}

/**
 * **Adónde se va de verdad** cuando se pide [screen].
 *
 * Ola C: el tablero de Recurrentes salió de Movimientos a Plan. `Screen.Transactions(CHIP_RECURRENTES)`
 * sigue pudiendo pedirse —el índice del chip no se renumera ni se reusa, y una pila vieja, un aviso
 * o un enlace que nadie actualizó lo pueden traer— y cae en **Plan · Pagos del mes**, que es donde
 * vive ahora lo que ese chip mostraba. Se resuelve acá, una vez, y no en cada llamador: [NavStack.navegar]
 * pasa todo destino por esta función antes de apilarlo.
 */
fun destinoVigente(screen: Screen): Screen =
    if (screen is Screen.Transactions && screen.chipInicial == CHIP_RECURRENTES) Screen.Plan(SEGMENTO_PAGOS)
    else screen

/**
 * Reglas puras de la pila de navegación (sin Compose), extraídas para poder
 * testearlas en :shared:commonTest (App.kt las aplica sobre un
 * SnapshotStateList<Screen> para que Compose observe los cambios).
 */
object NavStack {
    /** true si `screen` debe apilarse — evita duplicar la pantalla de arriba. */
    fun shouldPush(stack: List<Screen>, screen: Screen): Boolean =
        stack.isEmpty() || stack.last() != screen

    /** Las pantallas de antes de tener sesión: entrar y crear cuenta. */
    fun esDeAutenticacion(screen: Screen): Boolean =
        screen == Screen.Login || screen == Screen.Register

    /**
     * ¿Ir a [screen] tiene que **reemplazar la pila entera** en vez de apilar encima?
     *
     * Sí cuando se está saliendo de la autenticación: la pila arranca en [Screen.Login] y
     * entrar apilaba el Inicio encima, dejando `[Login, Dashboard]`. Nada la limpiaba, así que
     * el botón «atrás» del teléfono devolvía al formulario de entrada —con la contraseña en
     * blanco— a alguien que acababa de entrar. Con «Entrar con huella» prendido era peor:
     * aterrizar de nuevo en `LoginScreen` re-dispara su efecto de arranque y el prompt del
     * lector aparece solo, sin que nadie lo haya pedido. Por Registro la pila era
     * `[Login, Register, Dashboard]` y «atrás» mostraba un «Crear cuenta» a quien recién creaba
     * una.
     *
     * La regla mira el FONDO de la pila, no el tope: así cubre por igual la entrada directa y la
     * que pasa por Registro, y no necesita que cada pantalla de auth se acuerde de pedir nada.
     * Moverse entre Login y Register sigue apilando — ahí volver sí tiene sentido.
     */
    fun shouldReplaceAll(stack: List<Screen>, screen: Screen): Boolean =
        stack.isNotEmpty() && esDeAutenticacion(stack.first()) && !esDeAutenticacion(screen)

    /**
     * Aplica una navegación sobre la pila: reemplazar todo (ver [shouldReplaceAll]), apilar
     * (ver [shouldPush]) o no hacer nada. App.kt la llama sobre su `SnapshotStateList`, que es
     * un `MutableList` como cualquier otro — y así la regla se puede probar sin Compose.
     */
    fun navegar(stack: MutableList<Screen>, pedido: Screen) {
        // Un destino que se mudó se resuelve ANTES de mirar la pila: si no, `shouldPush`
        // compararía contra lo pedido y no contra lo que de verdad se apila. Ver [destinoVigente].
        val screen = destinoVigente(pedido)
        when {
            shouldReplaceAll(stack, screen) -> {
                stack.clear()
                stack.add(screen)
            }
            shouldPush(stack, screen) -> stack.add(screen)
        }
    }

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
 * Cuántas veces se guardó algo desde una **ventana modal** en esta sesión.
 *
 * Existe por lo que el overlay de «Agregar» rompió (ver [opensAsOverlay]): antes, abrir Agregar
 * apilaba una pantalla y la de atrás salía de la composición, así que al volver reejecutaba su
 * `LaunchedEffect(refreshKey)` y recargaba sola. Ahora la pantalla de atrás **nunca sale** —esa
 * es justamente la mejora: conserva su estado— pero eso significa que nadie le avisa que sus
 * datos quedaron viejos. El síntoma: registrás un movimiento o un traspaso, la hoja se cierra y
 * Movimientos / Inicio / el detalle de la cuenta siguen mostrando la lista de antes. Peor todavía,
 * la app parece decir que no pasó nada, y el reflejo es volver a guardar — el mismo reintento a
 * ciegas que duplicaba traspasos.
 *
 * Cada pantalla que lee datos lo usa como una key más de su `LaunchedEffect`. Un `Int` que sube
 * y nada más: no hay evento, ni payload, ni quién escucha a quién — la pantalla ya sabe cómo
 * recargarse, lo único que le faltaba era enterarse.
 *
 * `compositionLocalOf` y NO `staticCompositionLocalOf`: este valor cambia. Con la variante
 * estática, Compose no rastrea lecturas y cada guardado invalidaría TODO el subárbol bajo el
 * provider en vez de solo las pantallas que lo leen — el mismo anti-patrón que el `remember`
 * de `goBackTo` documenta unas líneas más abajo, pero al revés: aquello es estático porque
 * nunca cambia, esto cambia y por eso no puede serlo.
 */
val LocalRefreshTick = compositionLocalOf { 0 }

/**
 * «Volver» real, expuesto a las pantallas: recibe el destino de reserva (F22) y
 * decide adentro si hay historial al que volver o si hay que caer a ese destino.
 * App.kt provee la implementación de verdad alrededor del `when(currentScreen)`;
 * el default es un no-op para que un preview o test aislado no explote.
 */
val LocalGoBack = staticCompositionLocalOf<(Screen) -> Unit> { {} }

/**
 * Navegar a una pantalla desde donde no llega el `onNavigate` de nadie.
 *
 * Existe por un pedido concreto del dueño: *«Necesito un editor de categorías en las diferentes
 * secciones»*. El editor ya existía —«Más → Categorías», con renombrar, unificar, esconder y
 * fijar el tipo— pero solo se llegaba saliendo a «Más», o sea abandonando lo que uno estaba
 * haciendo. El campo de categoría, en cambio, aparece en las cuatro secciones donde la pregunta
 * se hace ([com.jvillada.movi.ui.components.CategoryField]: Movimientos, Agregar, Presupuestos y
 * Recurrentes), y es un componente compartido: ponerle el acceso ahí lo resuelve en los cuatro
 * lados de una vez.
 *
 * Se agrega un local en vez de enhebrar un `onNavigate` por cuatro pantallas y sus hojas porque
 * tres de esos cuatro puntos están dentro de hojas modales que ya reciben media docena de
 * callbacks. Es `staticCompositionLocalOf` por el mismo motivo que [LocalGoBack]: la función que
 * se provee no cambia nunca.
 *
 * El default es un no-op para que un preview o un test aislado no explote.
 */
val LocalNavigate = staticCompositionLocalOf<(Screen) -> Unit> { {} }
