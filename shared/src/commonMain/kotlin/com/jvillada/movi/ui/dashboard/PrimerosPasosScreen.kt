package com.jvillada.movi.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvillada.movi.data.Repositories
import com.jvillada.movi.shared.model.Scope
import com.jvillada.movi.theme.MinBg
import com.jvillada.movi.theme.MinPrimary
import com.jvillada.movi.theme.MinTextMute
import com.jvillada.movi.ui.LocalRefreshTick
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.accounts.CreateAccountSheet
import com.jvillada.movi.ui.components.HeaderLeading
import com.jvillada.movi.ui.components.MinScreenHeader
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * # «Primeros pasos», como pantalla — la puerta de vuelta a la guía
 *
 * El dueño, textual: «no veo el onboarding o FTU que tenía ciertas tareas, quisiera poder verlo
 * si aún me faltan tareas o algo así».
 *
 * ## Qué estaba mal
 *
 * La guía del Inicio se apaga sola en cuanto hay cuenta **y** movimiento
 * (`DashboardScreen`: `showGuide = !(hasAccount && hasMovement)`), y esa era **toda** su
 * historia: no había ninguna forma de volver a abrirla. El dueño la cruzó hace rato, así que
 * para él la guía desapareció para siempre — y sus pasos son cuatro, de los cuales solo dos
 * gobiernan el apagado, así que la tarjeta podía irse con tareas pendientes adentro.
 *
 * ## Qué se hizo, y qué NO
 *
 * Una entrada en «Más» que abre esta pantalla, con la misma tarjeta de siempre
 * ([PrimerosPasosCard]) y sus mismos pasos tocables. Y nada más:
 *
 * - **No se cambió cuándo aparece sola en el Inicio.** Se sigue apagando con cuenta +
 *   movimiento. Él pidió *poder volver*, no que la guía le reaparezca: se apagó sola por una
 *   razón y ya la superó.
 * - **No se la ató a completar los cuatro pasos.** «Cárgalos si tienes préstamos o tarjetas» es
 *   un paso *ofrecido*: atar el apagado a los cuatro dejaría la tarjeta clavada para siempre en
 *   el Inicio de cualquiera que no tenga deudas (ver el KDoc de [PrimerosPasosCard]).
 * - **No hay aviso ni insignia en el Inicio.** Se miró el dato antes de decidir: en producción,
 *   al 2026-08-28, el dueño tiene 2 cuentas, 15 movimientos, 5 recurrentes y 1 crédito — o sea
 *   **4 de 4**. No le falta ninguna tarea, así que un recordatorio en el Inicio sería ruido
 *   sobre algo que ya terminó. Lo que necesitaba era poder ir a mirar, y eso es esto.
 *
 * ## De dónde salen los datos
 *
 * De los mismos endpoints que ya alimentan la guía en el Inicio, y arrancando de
 * [DashboardDataCache] para que la pantalla se pinte llena desde el primer cuadro (a esta
 * pantalla se llega desde «Más», o sea después de haber pasado por el Inicio). Las cinco
 * llamadas se hacen igual y en paralelo, porque una caché puede estar vieja y esta pantalla es
 * justamente la que contesta «¿me falta algo?». Si alguna falla, no se pinta un error: queda el
 * dato de la caché, que es la misma política del Inicio para las secciones secundarias.
 *
 * Y se vuelven a hacer con cada `LocalRefreshTick`, no solo al entrar — ver la nota junto al
 * `LaunchedEffect`: sin eso la lista de tareas se quedaba vieja justo después de que el dueño
 * completara una.
 */
@Composable
fun PrimerosPasosScreen(onNavigate: (Screen) -> Unit) {
    var data by remember { mutableStateOf(DashboardDataCache.data ?: DashboardData()) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    // Con la guarda de `puedeAfirmarVacio`, una lectura que falla ya no deja una tarjeta con
    // todo sin tildar: deja una barra de progreso. Sin este contador esa barra giraba PARA
    // SIEMPRE, sin decir qué pasó y sin manera de reintentar — el único `refreshKey++` que había
    // vivía dentro de la hoja de crear cuenta, o sea justo dentro de lo que la guarda esconde.
    // La salida era salir de la pantalla y volver a entrar, y nada se lo indicaba al dueño.
    var fallo by remember { mutableStateOf(false) }

    // **Sin esto la pantalla miente sobre lo que el dueño acaba de hacer.** «Agregar» es una
    // MODAL (ver `opensAsOverlay`): se dibuja encima y esta pantalla nunca sale de la
    // composición, así que al guardar el movimiento no hay ningún reingreso que dispare la
    // recarga — el paso «Registra un movimiento» seguía sin tildar y el contador seguía diciendo
    // «1 de 4» con el evento ya guardado en el server. [LocalRefreshTick] es la señal que la hoja
    // emite justo para eso, y la leen las otras seis pantallas que pueden quedar viejas por
    // debajo de ella. Acá duele más que en ninguna: esta pantalla existe para contestar «¿me
    // falta algo?».
    val refreshTick = LocalRefreshTick.current

    LaunchedEffect(refreshKey, refreshTick) {
        fallo = false
        coroutineScope {
            launch {
                runCatching { Repositories.wallets.getAccounts() }
                    .onSuccess { a -> data = data.copy(accounts = a) }
                    .onFailure { fallo = true }
            }
            launch {
                runCatching { Repositories.wallets.getFinanceSummary(Scope.SELF) }
                    .onSuccess { s -> data = data.copy(summary = s) }
                    .onFailure { fallo = true }
            }
            launch {
                runCatching { Repositories.wallets.getCredits() }
                    .onSuccess { c -> data = data.copy(credits = c) }
                    .onFailure { fallo = true }
            }
            launch {
                runCatching { Repositories.wallets.getCards() }
                    .onSuccess { c -> data = data.copy(cards = c) }
                    .onFailure { fallo = true }
            }
            launch {
                runCatching { Repositories.wallets.getUpcomingPayments() }
                    .onSuccess { u -> data = data.copy(upcoming = u) }
                    .onFailure { fallo = true }
            }
        }
        // Lo recién traído también sirve para el Inicio: es exactamente el mismo modelo.
        DashboardDataCache.data = data
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MinBg),
    ) {
        MinScreenHeader(
            title = "Primeros pasos",
            leading = HeaderLeading.Back(fallback = Screen.Mas),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Lo que conviene tener listo para que las cifras de Movi digan la verdad. " +
                    "Puedes volver aquí cuando quieras.",
                fontSize = 13.sp,
                color = MinTextMute,
                modifier = Modifier.padding(horizontal = 18.dp).padding(top = 4.dp, bottom = 14.dp),
            )
            // Esta pantalla existe para contestar «¿me falta algo?», así que no puede contestar
            // «te falta todo» mientras todavía está preguntando. La caché suele llegar llena
            // (se entra desde «Más», o sea después del Inicio), pero un arranque en frío de la
            // web sí puede caer acá sin nada: ahí se espera, no se inventa.
            if (!data.puedeAfirmarVacio && fallo) {
                // Se dice qué pasó y se ofrece la salida, en vez de girar sin fin.
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                    Text(
                        text = "No pudimos revisar qué te falta. Puede ser la conexión.",
                        fontSize = 13.sp,
                        color = MinTextMute,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Reintentar",
                        fontSize = 13.sp,
                        color = MinPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { refreshKey++ }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            } else if (!data.puedeAfirmarVacio) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp))
            } else {
                PrimerosPasosCard(
                    data = data,
                    onNavigate = onNavigate,
                    onShowCreateSheet = { showCreateSheet = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCreateSheet) {
        CreateAccountSheet(
            onDismiss = { showCreateSheet = false },
            // Crear la cuenta desde acá tiene que tildar el paso al instante: sin este
            // `refreshKey++` la pantalla seguiría mostrando «pendiente» lo que el dueño acaba
            // de hacer, que es el modo de falla más molesto de una lista de tareas.
            onAccountCreated = { showCreateSheet = false; refreshKey++ },
        )
    }
}
