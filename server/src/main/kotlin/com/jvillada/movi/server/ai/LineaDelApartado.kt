package com.jvillada.movi.server.ai

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.Apartado
import com.jvillada.movi.shared.model.valorEnPesosDe

/**
 * **Lo apartado en una cuenta, dicho para el asistente**: cada apartado con su propósito y su monto,
 * y lo que queda libre. Sin esto el modelo lee «Nu: saldo $8.217.464» y contesta que hay $8M para
 * gastar cuando $8M ya tienen dueño. El tope es el saldo, igual que en `patrimonioDe`: una cuenta
 * vaciada no queda con «libre» negativo. `null` si la cuenta no tiene apartados.
 */
internal fun lineaDelApartadoDeLaCuenta(cuenta: Account, apartados: List<Apartado>, pesos: (Long) -> String): String? {
    val propios = apartados.filter { it.accountId == cuenta.id && it.restante > 0L }
    if (propios.isEmpty()) return null
    val saldo = valorEnPesosDe(cuenta).coerceAtLeast(0L)
    val apartado = propios.sumOf { it.restante }.coerceAtMost(saldo)
    val detalle = propios.joinToString("; ") { "«${it.proposito.trim()}» ${pesos(it.restante)}" }
    return "apartado $detalle — plata que ya tiene dueño, NO disponible; libre en esta cuenta: ${pesos(saldo - apartado)}"
}
