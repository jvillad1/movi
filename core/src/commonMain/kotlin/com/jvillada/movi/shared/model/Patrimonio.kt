package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # El patrimonio, en UN solo lugar
 *
 * «Tu plata», «uso condicionado» y «patrimonio neto» se calculaban en cuatro sitios: el hero del
 * Inicio (`heroBalance`), la tarjeta de la pantalla Cuentas (que llamaba a la misma), el
 * `assetsDebtsNet` de `:shared` y —en el server, por su lado— el `netWorth` de `Balances.kt` y el
 * contexto de Movi AI, que listaba cuentas y dejaba la suma al modelo. Cuatro copias de la misma
 * regla, y este repo ya se comió tres veces el defecto de que dos copias se desalineen (Créditos
 * vs. Inicio en la Ola 4, los presupuestos en la Ola 16, la fila «Cuentas» contra el hero cuando
 * apareció [Account.condicionadaA]).
 *
 * Los bienes ([Account.bien]) hacían la cuarta: sin un lugar único, cada superficie iba a decidir
 * por su cuenta si la casa suma a «Tu plata». Así que la regla vive acá, en `:core`, y la usan el
 * cliente (Inicio y Cuentas), el server (`/api/dashboard/summary`, `/api/finance-summary`) y el
 * contexto de Movi AI.
 *
 * ## Los cuatro baldes
 *
 * Cada cuenta cae en UNO, y el orden de las preguntas es la regla:
 *
 * 1. **¿Es un bien?** → [Patrimonio.bienes], con [Bien.valor]. Se pregunta primero porque un bien
 *    viaja como `INVESTMENT` (ver [Bien]) y, si no, caería en «invertido».
 * 2. **¿Es deuda?** → [Patrimonio.deudas].
 * 3. **¿Está condicionada?** → [Patrimonio.condicionado]: suya, pero con un solo uso.
 * 4. **Lo demás** → [Patrimonio.tuPlata], partido en disponible e invertido.
 *
 * Y `neto = tuPlata + condicionado + bienes − deudas`. Sin bienes da exactamente lo que daba
 * `assetsDebtsNet` (activos no-deuda − deudas): tuPlata + condicionado SON los activos de antes.
 */
@Serializable
data class Patrimonio(
    /** Lo que puedes usar: ni deuda, ni condicionada, ni un bien. Es la cifra grande del Inicio. */
    val tuPlata: Long = 0L,
    /** La parte de [tuPlata] en el grupo Dinero — efectivo, corriente, ahorros. */
    val disponible: Long = 0L,
    /** La parte de [tuPlata] en el grupo Inversión. */
    val invertido: Long = 0L,
    /** Plata suya que solo sirve para algo: la pensión voluntaria, una AFC, cesantías. */
    val condicionado: Long = 0L,
    /**
     * Para qué. Con una sola condición distinta es esa («Vivienda»); con varias, `null` — inventar
     * una condición común sería decir algo que ninguna cuenta dice.
     */
    val condicionadoA: String? = null,
    /** Lo que vale lo que tienes y no es plata: la casa, el carro. Ver [Bien]. */
    val bienes: Long = 0L,
    /**
     * Lo que debes: tarjetas y préstamos, en pesos (estimado cuando hay saldo en otra moneda).
     * Puede ser negativo: una tarjeta sobrepagada queda a favor.
     */
    val deudas: Long = 0L,
) {
    /** Todo lo que tienes: la plata, la plata con destino y los bienes. */
    val loQueTienes: Long get() = tuPlata + condicionado + bienes

    /** Lo que tienes menos lo que debes. Con hipotecas puede ser negativo, y es la verdad. */
    val neto: Long get() = loQueTienes - deudas
}

/**
 * **Lo que vale una cuenta en pesos**, para sumarla al patrimonio.
 *
 * - Un bien vale su [Bien.valor]: no tiene movimientos, y su `balance` (que el server manda en 0,
 *   ver [Account.bien]) no dice nada de él.
 * - Una cuenta con plata en otra moneda vale su estimado con la TRM (`estimatedTotalCop`).
 * - Las demás, su saldo en pesos.
 *
 * `balance` es SOLO la parte en pesos (el server deja los dólares en `balancesByCurrency` /
 * `estimatedTotalCop`), así que sumar `balance` a secas dejaba fuera los dólares de una cuenta de
 * ahorros o de inversión. Vivía en `:shared` (`valorEnPesos`, que ahora llama acá); se mudó para
 * que el server sume con la misma regla.
 */
fun valorEnPesosDe(account: Account): Long =
    account.bien?.valor ?: (account.estimatedTotalCop ?: account.balance)

/**
 * Parte [accounts] en los cuatro baldes de [Patrimonio]. **La regla del patrimonio**, y el único
 * sitio donde está escrita: ver el KDoc de arriba.
 */
fun patrimonioDe(accounts: List<Account>): Patrimonio {
    val bienes = accounts.filter { it.esBien }
    val resto = accounts.filterNot { it.esBien }
    val deudas = resto.filter { esCuentaDeDeuda(it.type) }
    val condicionadas = resto.filter { !esCuentaDeDeuda(it.type) && !it.condicionadaA.isNullOrBlank() }
    // `esDeTuPlata` y no un tercer filtro escrito a mano: es el mismo predicado con el que el
    // server suma «lo que tenías al empezar el período» (ver `PlataDelPeriodo.kt`).
    val libres = accounts.filter { it.esDeTuPlata() }
    val invertido = libres.filter { it.type.group == AccountGroup.INVERSION }.sumOf { valorEnPesosDe(it) }
    val disponible = libres.filter { it.type.group != AccountGroup.INVERSION }.sumOf { valorEnPesosDe(it) }
    return Patrimonio(
        tuPlata = disponible + invertido,
        disponible = disponible,
        invertido = invertido,
        condicionado = condicionadas.sumOf { valorEnPesosDe(it) },
        // Una sola condición se nombra; varias distintas no se resumen en una inventada.
        condicionadoA = condicionadas
            .mapNotNull { it.condicionadaA?.trim()?.takeIf { c -> c.isNotEmpty() } }
            .distinct()
            .singleOrNull(),
        bienes = bienes.sumOf { valorEnPesosDe(it) },
        deudas = deudas.sumOf { valorEnPesosDe(it) },
    )
}
