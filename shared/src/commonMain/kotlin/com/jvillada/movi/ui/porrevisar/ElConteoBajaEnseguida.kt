package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.ui.dashboard.DashboardDataCache

/*
 * # Vuelta 74 · el conteo del Inicio baja enseguida
 *
 * Cerrar un aviso en la bandeja («Es este», «Confirmar») es una escritura, y toda escritura deja el
 * Inicio para releer (`InvalidaElInicioAlEscribir`). Pero al volver, el Inicio pinta primero lo que
 * tenía —con el aviso todavía contado en «Por revisar» y en la campana— hasta que llega la lectura.
 * Esto le descuenta a lo que tiene en memoria los pagos que se acaban de cerrar acá (y se los
 * devuelve si se deshacen), así el primer cuadro ya dice el número nuevo. La lectura de verdad viene
 * igual y manda.
 */

/** Suma [cuantos] (negativo: resta) a los avisos por confirmar que el Inicio tiene en memoria. Nunca baja de 0. */
internal fun elInicioCuentaOtrosAvisos(cuantos: Int) {
    val datos = DashboardDataCache.data ?: return
    DashboardDataCache.data = datos.copy(pendingSms = (datos.pendingSms + cuantos).coerceAtLeast(0))
}
