package com.jvillada.movi.ui.porrevisar

import com.jvillada.movi.shared.model.LoPorConfirmarPorSigno
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.dashboard.DashboardData

/*
 * # «Por confirmar» con signo (vuelta 85)
 *
 * La revisión 9 vio «$1.632.025 en 6 avisos» en el Inicio, con dos ingresos sumados a los gastos: ni
 * decía cuánto iba a bajar «Te quedan» ni cuánto iba a entrar. Ahora lo que sale y lo que entra van
 * cada uno con su signo y su conteo: «−$262.400 en 2 salidas · +$480.000 en 1 entrada».
 *
 * **Por qué «salidas» y «entradas» y no «gastos» e «ingresos»**: un aviso que espera puede ser el pago
 * de una tarjeta o un traspaso, que en Movi no son gasto (no cuentan en «Salió»); «salida» dice lo que
 * dice el aviso —plata que se fue de la cuenta— sin adelantar cómo se va a contar. Y van las dos cifras,
 * no el neto: el neto de una compra de $262.400 y un giro de $480.000 que llega es «+$217.600», una
 * cifra que no está en ningún aviso.
 *
 * La cifra es de `:core` ([com.jvillada.movi.shared.model.loPorConfirmarPorSigno]): la misma en el
 * Inicio (la manda el server) y en Actividad (la arma la app con las mismas propuestas).
 */

/** El signo menos de las cifras de Movi (el de `formatCOP`), no el guion. */
private const val MENOS = "−"

/**
 * **«−$262.400 en 2 salidas · +$480.000 en 1 entrada»**: lo que sale con su signo y lo que entra con el
 * suyo, cada uno solo si hay; `null` sin ninguno. Pura.
 */
fun textoDeLoPorConfirmarConSigno(porSigno: LoPorConfirmarPorSigno): String? {
    val salen = porSigno.avisosQueSalen.takeIf { it > 0 }?.let { n ->
        "$MENOS${formatCOP(porSigno.salen)} en ${if (n == 1) "1 salida" else "$n salidas"}"
    }
    val entran = porSigno.avisosQueEntran.takeIf { it > 0 }?.let { n ->
        "+${formatCOP(porSigno.entran)} en ${if (n == 1) "1 entrada" else "$n entradas"}"
    }
    return listOfNotNull(salen, entran).joinToString(" · ").ifEmpty { null }
}

/**
 * Lo que el Inicio tiene de los avisos por confirmar, partido por signo; `null` si el server no lo
 * mandó (un server viejo) o si se calló al no saber de qué lado era un aviso cerrado acá.
 */
fun loPorConfirmarPorSignoDe(datos: DashboardData): LoPorConfirmarPorSigno? {
    if (datos.porConfirmarSalen == null && datos.porConfirmarEntran == null) return null
    return LoPorConfirmarPorSigno(
        salen = datos.porConfirmarSalen ?: 0L,
        avisosQueSalen = datos.avisosQueSalen,
        entran = datos.porConfirmarEntran ?: 0L,
        avisosQueEntran = datos.avisosQueEntran,
    )
}
