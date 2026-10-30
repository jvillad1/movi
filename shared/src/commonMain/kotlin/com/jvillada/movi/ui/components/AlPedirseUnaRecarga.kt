package com.jvillada.movi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.jvillada.movi.data.LoFresco

/**
 * **La pantalla a la vista recarga cuando se pide a mano** (vuelta 79): el ↻ del rail o la vuelta del foco
 * a la ventana en la web (`actualizarAMano`, que ya olvidó lo fresco). Corre [recargar] cada vez que
 * [LoFresco.recargasPedidas] sube **mientras la pantalla está compuesta**; al entrar no hace nada (la
 * pantalla ya carga al entrar).
 */
@Composable
fun AlPedirseUnaRecarga(recargar: () -> Unit) {
    val vigente by rememberUpdatedState(recargar)
    val alEntrar = remember { LoFresco.recargasPedidas }
    val pedidas = LoFresco.recargasPedidas
    LaunchedEffect(pedidas) {
        if (pedidas != alEntrar) vigente()
    }
}
