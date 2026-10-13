package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.CierreDelPeriodo
import com.jvillada.movi.shared.model.esHoraDeAvisarElCierre
import com.jvillada.movi.shared.model.fraseDelCierre
import com.jvillada.movi.shared.model.lineasDelCierre
import com.jvillada.movi.shared.model.periodoDelPrefijo
import com.jvillada.movi.shared.model.tituloDelCierre
import com.jvillada.movi.ui.Screen

/*
 * # «Cerró tu período», en el teléfono
 *
 * Un Worker diario (`AvisoDeCierreWorker`, en `:androidApp`) lee el mismo resumen que la tarjeta de
 * Hoy (`GET /api/cierre-del-periodo`) y, el día siguiente al corte a las 9:00, avisa. Acá vive lo que
 * decide y lo que dice, puro y probado, como el resto de `AvisosDelTelefono.kt`.
 */

/** La hora del aviso, en Bogotá: la mañana siguiente al corte. */
const val HORA_DEL_AVISO_DE_CIERRE: Int = 9

/**
 * **¿Se avisa este cierre?** Una sola vez por período —[yaAvisado] es el período que este teléfono ya
 * avisó—, y solo los primeros días del período nuevo ([esHoraDeAvisarElCierre]): si el teléfono estuvo
 * apagado el día del corte, avisa al prenderse; una semana después ya no sirve. `null` = no se avisa.
 */
fun cierreParaAvisar(cierre: CierreDelPeriodo?, yaAvisado: String?): CierreDelPeriodo? = cierre
    ?.takeIf { it.periodo != yaAvisado }
    ?.takeIf { esHoraDeAvisarElCierre(it.diaDelPeriodoNuevo) }
    ?.takeIf { !it.descartado }

/**
 * **El texto del aviso**: «Cerró tu período de septiembre», la frase de la tarjeta de Hoy y, al
 * desplegarlo, un renglón por cosa (contra el anterior, en qué se fue, los pagos, lo raro, lo que
 * quedó). El mismo texto que la tarjeta y el correo: sale de `:core`.
 */
fun textoDelAvisoDeCierre(c: CierreDelPeriodo): TextoDeAviso = TextoDeAviso(
    titulo = tituloDelCierre(c),
    texto = fraseDelCierre(c),
    // El primer renglón («Entró · salió») ya es la frase: no se repite.
    lineas = lineasDelCierre(c).drop(1),
)

/** Lo que el aviso guarda para saber a qué período abrir: `"periodo:2026-09"`. */
fun abrirElPeriodo(periodo: String): String = "$PREFIJO_ABRIR_PERIODO$periodo"

/** El prefijo de [abrirElPeriodo]. */
const val PREFIJO_ABRIR_PERIODO: String = "periodo:"

/** El detalle del período de un `"periodo:2026-09"`, o `null` si no es un período. */
internal fun detalleDelAviso(valor: String): Screen? =
    valor.removePrefix(PREFIJO_ABRIR_PERIODO)
        .takeIf { valor.startsWith(PREFIJO_ABRIR_PERIODO) && periodoDelPrefijo(it) != null }
        ?.let { Screen.DetalleDePeriodo(it) }
