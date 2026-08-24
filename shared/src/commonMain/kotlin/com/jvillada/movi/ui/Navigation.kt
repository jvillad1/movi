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
    data object Transactions : Screen()
    // F10: cuando se abre "para registrar el primero" desde el detalle de una cuenta puntual,
    // esa cuenta viene preseleccionada — sin esto QuickAdd caía siempre en la primera cuenta de
    // la lista, sin importar desde dónde se entró.
    data class QuickAdd(val presetAccountId: String? = null) : Screen()
    data object Profile : Screen()
    data object AIChat : Screen()
    data object Credits : Screen()
    data object Goals : Screen()
    data object Budgets : Screen()
    // Ola 8: Suscripciones dejó de ser pantalla — una suscripción es un recurrente y vive
    // adentro de [Recurrentes], en su propio grupo (ver RecurrentesScreen).
    data object Recurrentes : Screen()
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
 * Presupuestos, Créditos y Recurrentes tienen destino propio (en el teléfono se resaltan
 * como Más, que es por donde se llega a ellos ahí).
 * F61: Inversiones dejó de ser pantalla — las cuentas de inversión se ven en Cuentas.
 * Ola 8: ídem Suscripciones — viven adentro de Recurrentes, así que todo lo que antes marcaba
 * la pestaña Más por ser "una suscripción" ahora marca RECURRING, que es donde de verdad está.
 */
fun navTabFor(screen: Screen): NavTab? = when (screen) {
    Screen.Dashboard -> NavTab.HOME
    Screen.Transactions -> NavTab.TRANSACTIONS
    Screen.Accounts -> NavTab.ACCOUNTS
    // El detalle hereda la pestaña de la pantalla donde vive la cuenta — así resaltar y
    // «volver» no pueden contradecirse (una tarjeta abierta desde Créditos marca Créditos).
    is Screen.AccountDetail -> navTabFor(homeScreenFor(screen.group))
    Screen.Credits -> NavTab.CREDITS
    Screen.Budgets -> NavTab.BUDGETS
    // Recurrentes salió de Más y pasó a destino propio: en pantalla ancha es una entrada del
    // rail; en el teléfono la barra ya está llena y `asBottomBarTab()` lo funde en Más.
    Screen.Recurrentes -> NavTab.RECURRING
    Screen.Mas, Screen.Profile, Screen.Goals,
    Screen.Extractos, Screen.AIChat, Screen.SMSInbox, is Screen.SMSReconcile -> NavTab.MORE
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
    NavTab.TRANSACTIONS -> Screen.Transactions
    NavTab.ADD -> Screen.QuickAdd()
    NavTab.ACCOUNTS -> Screen.Accounts
    NavTab.CREDITS -> Screen.Credits
    NavTab.BUDGETS -> Screen.Budgets
    NavTab.RECURRING -> Screen.Recurrentes
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
