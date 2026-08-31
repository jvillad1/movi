package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class AccountType { CASH, CHECKING, SAVINGS, CREDIT_CARD, LOAN, INVESTMENT }

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val balance: Long,      // COP component (derived on read)
    val currency: String = "COP",
    val balancesByCurrency: Map<String, Long> = emptyMap(),  // derived: per-currency balance
    val estimatedTotalCop: Long? = null,                     // derived: COP + foreign × TRM

    /**
     * **Para qué —y solo para qué— se puede usar esta plata sin castigo.** `null` = libre.
     *
     * Sale de la pensión voluntaria del dueño en Skandia. Son $106.000.000 suyos, cuentan en su
     * patrimonio, y aun así no son plata disponible: solo puede retirarlos **para vivienda** sin
     * perder el beneficio tributario. Cualquier otro retiro le pega la retención en la fuente que
     * se ahorró al aportar.
     *
     * Él lo dijo así: *«esa plata no la tengo disponible; la de Skandia es dinero que deberías
     * referenciar en patrimonio para el cálculo pero no mostrarle como disponible en mi balance,
     * sino como un dinero disponible condicionado a uso en Vivienda»*.
     *
     * Con el campo vacío, «Tu plata» sumaba los $106M y decía $137.625.167 — un número que él no
     * puede gastar. Ahora esa cuenta sale de «Tu plata» y entra en su propio renglón; el
     * patrimonio neto **no cambia**, porque para eso sí es suya.
     *
     * Es texto libre y no un enum: en Colombia el mismo caso son las cesantías, una AFC, un fondo
     * de pensiones voluntarias. La condición cambia con el producto y quien la escribe es el
     * dueño, que es el que la conoce.
     */
    val condicionadaA: String? = null,
)

/**
 * F56 — [AccountType] se queda igual (compat de DB y wire: filas viejas, eventos guardados,
 * el `POST /api/accounts` del server), pero la UI ya no distingue entre CASH/CHECKING/SAVINGS
 * (verificado: se tratan idéntico en todos los cálculos de balance) ni promete que una cuenta
 * es un lugar distinto de una deuda cuando en realidad es lo mismo con otro nombre. Este agrupador
 * es la superficie que la UI muestra: **Dinero** (plata disponible), **Inversión** (plata
 * guardada) y **Deuda** (tarjetas y préstamos — ya no se crean como cuenta, viven en Créditos,
 * pero el grupo existe para lo que ya haya en la base).
 */
enum class AccountGroup { DINERO, INVERSION, DEUDA }

val AccountType.group: AccountGroup
    get() = when (this) {
        AccountType.CASH, AccountType.CHECKING, AccountType.SAVINGS -> AccountGroup.DINERO
        AccountType.INVESTMENT -> AccountGroup.INVERSION
        AccountType.CREDIT_CARD, AccountType.LOAN -> AccountGroup.DEUDA
    }

val AccountType.groupLabel: String
    get() = when (group) {
        AccountGroup.DINERO -> "Dinero"
        AccountGroup.INVERSION -> "Inversión"
        AccountGroup.DEUDA -> "Deuda"
    }

/**
 * `PUT /api/accounts/{id}/name`.
 *
 * Renombrar una cuenta es seguro: los movimientos apuntan por `accountId` y el saldo se deriva de
 * ellos, así que nada se despega. Es distinto de renombrar una **categoría**, donde el cruce con
 * el gasto es por nombre y sí corta la relación con lo viejo.
 */
@Serializable
data class RenameAccountRequest(val name: String)

/** Largo de la columna `accounts.name`. */
const val MAX_ACCOUNT_NAME_LENGTH = 100
