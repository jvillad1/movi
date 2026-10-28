package com.jvillada.movi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jvillada.movi.platform.tirarParaActualizarDisponible
import com.jvillada.movi.theme.Movi
import kotlinx.coroutines.delay

/**
 * ¿Se ofrece el gesto de tirar para actualizar? Un `CompositionLocal` (y no la constante pelada)
 * para que una prueba lo pueda apagar o prender.
 */
val LocalTirarParaActualizar = staticCompositionLocalOf { tirarParaActualizarDisponible }

/**
 * # Tirar hacia abajo para actualizar (Ola 1 · «Que Movi se sienta viva»)
 *
 * Hoy y Movimientos. **No inventa una recarga**: [onActualizar] es la misma que ya tenía la pantalla
 * (su «Reintentar»), y [cargando] es lo que la pantalla ya sabía de esa lectura. El indicador gira
 * solo cuando la recarga la pidió el dedo; una recarga por otra razón (un guardado desde Agregar,
 * volver a la pantalla) se sigue diciendo como antes, con «Actualizando…» en la cabecera.
 *
 * Fuera del teléfono ([LocalTirarParaActualizar]) no hay gesto y esto es un `Box` sin más.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TirarParaActualizar(
    cargando: Boolean,
    onActualizar: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val disponible = LocalTirarParaActualizar.current
    val estado = rememberPullToRefreshState()
    // ¿Esta recarga la pidió el dedo? Se apaga cuando la lectura termina —`cargando` pasa a
    // `false`— y, por las dudas, a los 15 s: un indicador que gira para siempre es peor que ninguno.
    var porElDedo by remember { mutableStateOf(false) }
    LaunchedEffect(cargando) { if (!cargando) porElDedo = false }
    LaunchedEffect(porElDedo) {
        if (porElDedo) {
            delay(15_000)
            porElDedo = false
        }
    }
    Box(
        modifier = modifier.pullToRefresh(
            isRefreshing = porElDedo,
            state = estado,
            enabled = disponible,
            onRefresh = {
                porElDedo = true
                onActualizar()
            },
        ),
    ) {
        content()
        if (disponible) {
            PullToRefreshDefaults.Indicator(
                state = estado,
                isRefreshing = porElDedo,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Movi.colores.tarjeta,
                color = Movi.colores.enlace,
            )
        }
    }
}
