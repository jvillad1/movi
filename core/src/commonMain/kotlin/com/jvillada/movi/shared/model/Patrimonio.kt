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
 * Y `neto = tuPlata + apartado + condicionado + bienes − deudas`. Sin bienes da exactamente lo que daba
 * `assetsDebtsNet` (activos no-deuda − deudas): tuPlata + condicionado SON los activos de antes.
 *
 * ## Un préstamo sin saldo cuenta cero (vuelta 77)
 *
 * Cada deuda suma con su signo, **salvo un préstamo (`LOAN`) en negativo** ([prestamoSinSaldo]): ese
 * cuenta **cero** mientras no tenga saldo. Decisión de la vuelta 77, la única regla de plata nueva de
 * esa vuelta. Un préstamo en negativo no es plata a favor: es un crédito cargado con sus condiciones y
 * sin su deuda (la primera cuota lo deja en −$980.000; ver `creditoSinSaldo`), y sumarlo con su signo
 * le subía $980.000 al neto, hacía que «Deudas» dijera $603.000 al lado de «Créditos y tarjetas»
 * $1.583.000 y metía esos $980.000 en el «subió» de la curva. Lo que cambia es solo eso:
 *
 * - **Una tarjeta** con saldo a favor (pagó de más) **sigue con su signo**: es plata real que el banco
 *   le debe.
 * - **Un préstamo con saldo** (lo sano) suma igual que siempre: nada de lo que el dueño ya ve se mueve.
 * - Un préstamo en cero ya sumaba cero.
 *
 * La historia del patrimonio usa esta misma función punto por punto, así que un préstamo sin saldo
 * tampoco entra al «subió/bajó» de la curva.
 *
 * ## Lo apartado
 *
 * Un [Apartado] no es un quinto balde: es una parte de «Tu plata» que ya tiene dueño. Se resta de
 * [Patrimonio.tuPlata] (y de su grupo, disponible o invertido) pero queda en [Patrimonio.apartado],
 * así que el neto es el mismo con o sin apartados. Lo que se aparta nunca pasa del saldo de la
 * cuenta: el sobrante no resta nada.
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
     * Puede ser negativo: una tarjeta sobrepagada queda a favor. Un préstamo sin saldo cuenta cero
     * (vuelta 77, [prestamoSinSaldo]).
     */
    val deudas: Long = 0L,
    /**
     * Plata que sigue en tus cuentas pero ya tiene dueño (ver [Apartado]). **Ya está restada de
     * [tuPlata]**, pero sigue siendo tuya: suma a [loQueTienes], así que [neto] no se mueve.
     */
    val apartado: Long = 0L,
    /** Para qué, con un solo propósito distinto; con varios, `null` (mismo criterio que [condicionadoA]). */
    val apartadoPara: String? = null,
) {
    /** Todo lo que tienes: la plata, lo apartado, la plata con destino y los bienes. */
    val loQueTienes: Long get() = tuPlata + apartado + condicionado + bienes

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
 * **Vuelta 77 · ¿este préstamo cuenta cero en el patrimonio?** Un `LOAN` (que no es un bien) con la
 * deuda en pesos en negativo: Movi no tiene su saldo. Toda cuenta que `creditoSinSaldo` marca y pesa
 * algo cae acá (la otra mitad de esa regla, un préstamo en cero sin movimientos, ya suma cero). Una
 * tarjeta nunca: su saldo a favor es plata real. Pura.
 */
fun prestamoSinSaldo(cuenta: Account): Boolean =
    cuenta.type == AccountType.LOAN && !cuenta.esBien && valorEnPesosDe(cuenta) < 0L

/** Los ids de los préstamos de [accounts] que cuentan cero en el patrimonio ([prestamoSinSaldo]). */
fun prestamosSinSaldoDe(accounts: List<Account>): Set<String> =
    accounts.filter(::prestamoSinSaldo).mapTo(mutableSetOf()) { it.id }

/**
 * Parte [accounts] en los cuatro baldes de [Patrimonio]. **La regla del patrimonio**, y el único
 * sitio donde está escrita: ver el KDoc de arriba.
 *
 * [prestamosEnCero] son los préstamos que cuentan cero (vuelta 77): por defecto, los que hoy están sin
 * saldo en [accounts]. Solo la historia del patrimonio pasa otros: el neto «sin ajustes» de un punto
 * tiene que dejar en cero los mismos préstamos que el neto con todo, para que la resta entre los dos
 * diga cuánto fueron los ajustes y nada más.
 */
fun patrimonioDe(
    accounts: List<Account>,
    apartados: List<Apartado> = emptyList(),
    prestamosEnCero: Set<String> = prestamosSinSaldoDe(accounts),
): Patrimonio {
    val bienes = accounts.filter { it.esBien }
    val resto = accounts.filterNot { it.esBien }
    val deudas = resto.filter { esCuentaDeDeuda(it.type) }
    val condicionadas = resto.filter { !esCuentaDeDeuda(it.type) && !it.condicionadaA.isNullOrBlank() }
    // `esDeTuPlata` y no un tercer filtro escrito a mano: es el mismo predicado con el que el
    // server suma «lo que tenías al empezar el período» (ver `PlataDelPeriodo.kt`).
    val libres = accounts.filter { it.esDeTuPlata() }
    val efectivoPorCuenta = apartadoEfectivoPorCuenta(accounts, apartados)
    val invertido = libres.filter { it.type.group == AccountGroup.INVERSION }
        .sumOf { valorEnPesosDe(it) - (efectivoPorCuenta[it.id] ?: 0L) }
    val disponible = libres.filter { it.type.group != AccountGroup.INVERSION }
        .sumOf { valorEnPesosDe(it) - (efectivoPorCuenta[it.id] ?: 0L) }
    val conEfecto = apartados.filter { a ->
        a.proposito.isNotBlank() && a.restante > 0L && (efectivoPorCuenta[a.accountId] ?: 0L) > 0L
    }
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
        // Vuelta 77: un préstamo sin saldo cuenta cero; todo lo demás, con su signo.
        deudas = deudas.sumOf { if (it.id in prestamosEnCero) 0L else valorEnPesosDe(it) },
        apartado = efectivoPorCuenta.values.sum(),
        apartadoPara = conEfecto.map { it.proposito.trim() }.distinct().singleOrNull(),
    )
}

/**
 * **Lo apartado que de verdad pesa en cada cuenta**: la suma de lo que queda de sus apartados, con
 * tope en su saldo (una cuenta vaciada no queda debiendo). Solo cuentas de tu plata ([esDeTuPlata]);
 * las demás no aparecen. La usan [patrimonioDe] y el detalle de la cuenta («Libre»): una sola regla.
 */
fun apartadoEfectivoPorCuenta(accounts: List<Account>, apartados: List<Apartado>): Map<String, Long> {
    val porCuenta = apartadoPorCuenta(apartados)
    return accounts.filter { it.esDeTuPlata() }
        .associate { it.id to (porCuenta[it.id] ?: 0L).coerceAtMost(valorEnPesosDe(it).coerceAtLeast(0L)) }
}
