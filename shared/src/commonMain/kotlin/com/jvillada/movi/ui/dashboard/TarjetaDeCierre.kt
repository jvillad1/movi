package com.jvillada.movi.ui.dashboard

import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.comparacionDelCierre
import com.jvillada.movi.shared.model.disponibleDelCierre
import com.jvillada.movi.shared.model.pagosDelCierre
import com.jvillada.movi.shared.model.seMuestraEnHoy

/**
 * # «Cerró tu período» en el Inicio
 *
 * Los primeros días del período nuevo ([seMuestraEnHoy]): «Cerró tu período: entró $X, salió $Y».
 * Desde la ola R1 es una tarjeta más de «Movi propone hoy» (ver `propuestasDelInicio`): «Ver el
 * período» abre el detalle del período cerrado; «Ya lo vi» la cierra en todos los aparatos
 * (`POST /api/cierre-del-periodo/descartar`).
 *
 * El resumen lo arma el server ([CierreDelPeriodo]) y el texto sale de `:core`: la tarjeta, el aviso
 * del teléfono y el correo dicen lo mismo con las mismas palabras.
 */

/**
 * Los renglones de apoyo del cierre, como mucho dos (la propuesta del Inicio lleva el primero): lo que quedó del disponible, contra el período
 * anterior y, si falta lugar, los pagos. El resto del resumen está en el detalle.
 */
fun renglonesDeLaTarjetaDeCierre(c: CierreDelPeriodo): List<String> =
    listOfNotNull(disponibleDelCierre(c), comparacionDelCierre(c), pagosDelCierre(c)).take(2)

/** El cierre que el Inicio propone, o `null`: el que llegó, mientras se muestre (ver [seMuestraEnHoy]). */
fun cierreParaHoy(data: DashboardData): CierreDelPeriodo? = data.cierre?.takeIf { seMuestraEnHoy(it) }
