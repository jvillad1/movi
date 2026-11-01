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
 *
 * Vuelta 85 · [entra]: si esos avisos eran de plata que llega. Con él se mueve también la suma partida
 * por signo (`porConfirmarSalen`/`porConfirmarEntran`); sin él (`null`) no se sabe de qué lado restar, y
 * la suma entera se calla hasta la próxima lectura: mejor «Por confirmar» con el conteo que una cifra
 * de un lado que incluye lo del otro.
 */
internal fun elInicioCuentaOtrosAvisos(cuantos: Int, monto: Long?, moneda: String, entra: Boolean? = null) {
    val datos = DashboardDataCache.data ?: return
    val conteo = (datos.pendingSms + cuantos).coerceAtLeast(0)
    val montoEnPesos = monto?.takeIf { it > 0L && moneda == "COP" && conteo > 0 }
    val delta = montoEnPesos?.let { if (cuantos < 0) -it else it }
    val teniaSigno = datos.porConfirmarSalen != null || datos.porConfirmarEntran != null
    // Vuelta 85: la partición por signo, si se sabe de qué lado era; si no, nada (y la suma tampoco).
    val conSigno = if (!teniaSigno || entra == null || delta == null) null else {
        val salen = (datos.porConfirmarSalen ?: 0L) + if (entra) 0L else delta
        val entran = (datos.porConfirmarEntran ?: 0L) + if (entra) delta else 0L
        val avisosQueSalen = datos.avisosQueSalen + if (entra) 0 else cuantos
        val avisosQueEntran = datos.avisosQueEntran + if (entra) cuantos else 0
        if (salen < 0 || entran < 0 || avisosQueSalen < 0 || avisosQueEntran < 0) null
        else datos.copy(porConfirmarSalen = salen, porConfirmarEntran = entran, avisosQueSalen = avisosQueSalen, avisosQueEntran = avisosQueEntran)
    }
    val suma = datos.porConfirmarSuman?.let { antes ->
        if (delta == null || (teniaSigno && conSigno == null)) null else (antes + delta).takeIf { it > 0L }
    }
    val base = conSigno ?: datos.copy(porConfirmarSalen = null, porConfirmarEntran = null, avisosQueSalen = 0, avisosQueEntran = 0)
    DashboardDataCache.data = base.copy(pendingSms = conteo, porConfirmarSuman = suma)
}
