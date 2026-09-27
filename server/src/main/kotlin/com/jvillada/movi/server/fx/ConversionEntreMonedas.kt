package com.jvillada.movi.server.fx

import kotlin.math.roundToLong

/**
 * Convierte [monto] de [monedaOrigen] a [monedaDestino] con [tasa], o `null` si no se puede.
 *
 * Existe para `VincularPagoDeDeudaRoutes.kt`: pagar una tarjeta en dólares desde una cuenta en
 * pesos (el caso real de la Master Black, 2026-09-27) sin pedirle al dueño una segunda cifra —a
 * diferencia de [com.jvillada.movi.shared.model.CreatePagoDeCuotaRequest.montoEnLaMonedaDeLaDeuda],
 * que existe justo para cuando el dueño SÍ sabe el tipo de cambio que aplicó el banco. Acá el
 * movimiento ya está anotado (vino de un SMS o de una edición) y nadie le va a preguntar nada más.
 *
 * Función pura y separada de la ruta para poder probarla sin red: la usan las mismas pruebas que
 * ya construyen un [TasaUsdCop] a mano (ver `TarjetaEnDolaresTest`), en vez de pegarle a
 * `datos.gov.co` desde un test.
 *
 * **Una tasa de respaldo cuenta como no poder convertir**, igual que [minimoEnPesos]: convertir con
 * el `$4.000` inventado de [FxRateService] escribiría una deuda equivocada con pinta de exacta, y
 * eso es peor que rechazar y dejarle el pago como gasto suelto por ahora.
 *
 * Mismas monedas, sin pasos: se devuelve [monto] tal cual y ni siquiera hace falta una tasa.
 */
fun convertirEntreMonedas(monto: Long, monedaOrigen: String, monedaDestino: String, tasa: TasaUsdCop?): Long? {
    if (monedaOrigen == monedaDestino) return monto
    if (tasa == null || tasa.esRespaldo) return null
    return when {
        monedaOrigen == "COP" && monedaDestino == "USD" -> (monto / tasa.valor).roundToLong()
        monedaOrigen == "USD" && monedaDestino == "COP" -> (monto * tasa.valor).roundToLong()
        // Solo se sabe convertir COP<->USD, que es lo único que trae `FxRateService`. Cualquier
        // otro par (una tercera moneda que Movi no maneja) no se inventa.
        else -> null
    }
}
