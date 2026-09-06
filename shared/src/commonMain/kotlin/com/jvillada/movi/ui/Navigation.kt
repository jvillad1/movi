package com.jvillada.movi.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.jvillada.movi.shared.model.AccountGroup
import com.jvillada.movi.ui.components.NavTab

sealed class Screen {
    data object Login            : Screen()
    data object Register         : Screen()
    data object OnboardingWelcome : Screen()
    data object OnboardingProfile : Screen()
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
     */
    data class Transactions(val chipInicial: Int? = null) : Screen()
    // F10: cuando se abre "para registrar el primero" desde el detalle de una cuenta puntual,
    // esa cuenta viene preseleccionada — sin esto QuickAdd caía siempre en la primera cuenta de
    // la lista, sin importar desde dónde se entró.
    data class QuickAdd(val presetAccountId: String? = null) : Screen()
    data object Profile : Screen()
    data object AIChat : Screen()
    data object Credits : Screen()
    data object Goals : Screen()
    data object Budgets : Screen()
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
 * A qué destino de la navegación principal pertenece cada pantalla; null = sin chrome de
 * navegación (auth, onboarding, flujos a pantalla completa). App.kt lo usa para resaltar el
 * ítem activo en la barra (teléfono) y en el rail (pantalla ancha), que son los únicos
 * lugares donde se pinta la navegación — ninguna pantalla arma su propia barra.
 *
 * Ola 4: Cuentas marca la pestaña Cuentas (antes, Inicio); el detalle de una cuenta marca la
 * pestaña de donde vive esa cuenta (Créditos si es deuda, ver [homeScreenFor]);
 * Presupuestos y Créditos tienen destino propio (en el teléfono se resaltan como Más, que es
 * por donde se llega a ellos ahí).
 * F61: Inversiones dejó de ser pantalla — las cuentas de inversión se ven en Cuentas.
 * PR 4 del rediseño de Recurrentes (2026-09): ídem Recurrentes (y las suscripciones, que ya
 * vivían adentro) — todo eso es hoy Movimientos con el chip «Recurrentes» puesto, así que marca
 * TRANSACTIONS como cualquier otra entrada a Movimientos.
 */
fun navTabFor(screen: Screen): NavTab? = when (screen) {
    Screen.Dashboard -> NavTab.HOME
    is Screen.Transactions -> NavTab.TRANSACTIONS
    Screen.Accounts -> NavTab.ACCOUNTS
    // El detalle hereda la pestaña de la pantalla donde vive la cuenta — así resaltar y
    // «volver» no pueden contradecirse (una tarjeta abierta desde Créditos marca Créditos).
    is Screen.AccountDetail -> navTabFor(homeScreenFor(screen.group))
    Screen.Credits -> NavTab.CREDITS
    Screen.Budgets -> NavTab.BUDGETS
    Screen.Mas, Screen.Profile, Screen.Goals,
    Screen.Extractos, Screen.AIChat, Screen.SMSInbox, is Screen.SMSReconcile,
    // Ola 10: Categorías vive en Más y no tiene destino propio — es una pantalla de
    // mantenimiento, no un lugar al que se vuelva todos los días.
    // Ola 14: la guía de arranque se abre desde Más y se vuelve a Más — no es un destino de
    // todos los días, es un sitio al que se va a mirar si quedó algo pendiente.
    Screen.Categorias, Screen.PrimerosPasos, Screen.Documentos -> NavTab.MORE
    else -> null
}

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
    NavTab.HOME -> Screen.Dashboard
    NavTab.TRANSACTIONS -> Screen.Transactions()
    NavTab.ADD -> Screen.QuickAdd()
    NavTab.ACCOUNTS -> Screen.Accounts
    NavTab.CREDITS -> Screen.Credits
    NavTab.BUDGETS -> Screen.Budgets
    NavTab.MORE -> Screen.Mas
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
