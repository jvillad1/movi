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

/**
 * Suma [cuantos] (negativo: resta) a los avisos por confirmar que el Inicio tiene en memoria, y [monto]
 * en [moneda] (con el mismo signo) a lo que suman (`porConfirmarSuman`, revisión de la vuelta 74: el
 * conteo bajaba y la suma no). Nunca baja de 0. Si el monto no se sabe en pesos, la suma se calla
 * (`null`: el Inicio dice solo el conteo) hasta que llegue la lectura: nunca una suma a medias.
 */
internal fun elInicioCuentaOtrosAvisos(cuantos: Int, monto: Long?, moneda: String) {
    val datos = DashboardDataCache.data ?: return
    val conteo = (datos.pendingSms + cuantos).coerceAtLeast(0)
    val suma = datos.porConfirmarSuman?.let { antes ->
        if (monto == null || monto <= 0L || moneda != "COP" || conteo == 0) null
        else (antes + if (cuantos < 0) -monto else monto).takeIf { it > 0L }
    }
    DashboardDataCache.data = datos.copy(pendingSms = conteo, porConfirmarSuman = suma)
}
