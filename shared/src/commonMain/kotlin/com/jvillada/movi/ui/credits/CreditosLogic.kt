package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary

/**
 * F20 — deuda total en COP: préstamos + tarjetas, **una sola función** para que la pantalla de
 * Créditos y el acceso «Créditos» del Inicio sumen exactamente igual (en la Ola 4 se encontró
 * que daban números distintos; una sola fuente hace imposible que diverjan de nuevo).
 *
 * Por cuenta se usa `estimatedTotalCop ?: balance` — el mismo criterio que `assetsDebtsNet`
 * (Cuentas): una tarjeta en USD aporta su deuda convertida a TRM, no solo su componente COP
 * (que sería $0 y mentiría el total).
 */
fun totalDebtCop(credits: List<CreditSummary>, cards: List<CardSummary>): Long =
    credits.sumOf { debtCopOf(it.account) } + cards.sumOf { debtCopOf(it.account) }

private fun debtCopOf(account: Account): Long = account.estimatedTotalCop ?: account.balance

/**
 * Lo que dice la esquina derecha de la tarjeta de un préstamo, y cuánto llena su barra.
 *
 * @property etiqueta el texto que se lee: «18% pagado», o el aviso de que falta el desembolso.
 * @property fraccion cuánto de la barra se pinta, de 0 a 1.
 * @property esAviso si [etiqueta] es una frase y no una cifra. Lo decide esta función y no la
 *   pantalla: dibujar una frase con la fuente monoespaciada —que está para que los porcentajes se
 *   alineen entre tarjetas— solo la ensancha. Deducirlo allá de `hasMovements` daba mal el caso
 *   de un crédito sin términos, que no tiene movimientos y sin embargo muestra «0% pagado».
 */
data class ProgresoDeCredito(val etiqueta: String, val fraccion: Float, val esAviso: Boolean)

/**
 * **Un crédito sin un solo movimiento no está pagado: está sin registrar.**
 *
 * `paidPct` es `1 - deuda/principal`, así que un crédito con capital original de $257.000.000 y
 * deuda derivada $0 da 1.0 — y la tarjeta anunciaba **«100% pagado»**, con la barra llena, sobre
 * un crédito que el dueño acababa de crear y al que todavía no le había registrado el desembolso.
 *
 * Eso es peor que el error que esta ola vino a evitar, no mejor. La deuda contada dos veces es
 * ruidosa —la hoja de Traspaso muestra la aritmética antes de guardar, y $514.000.000 saltan a la
 * vista—; esta era **callada y optimista**: la deuda total subestimada en el monto entero del
 * crédito y el patrimonio sobreestimado por lo mismo, sin que nada en la pantalla pidiera
 * completar nada. Y el flujo tiene dos pasos (crear el crédito en $0, después registrar el
 * desembolso), así que el estado intermedio es normal, va a existir, y hay que decirlo.
 *
 * **El patrimonio neto no delata ninguno de los dos errores**, y por eso las dos defensas tienen
 * que estar en la pantalla donde se comete cada uno: si la deuda se cuenta dos veces, deuda y
 * efectivo se inflan a la par y el neto queda igual; si el desembolso falta, faltan los dos y el
 * neto también queda igual. Lo único que se mueve son las cifras por cuenta.
 *
 * La distinción no puede salir de la deuda sola —$0 es «pagado» y «sin registrar» a la vez— y por
 * eso sale de [CreditSummary.hasMovements]: un crédito de verdad pagado llegó a $0 **con
 * eventos** (su apertura, sus cuotas, sus abonos) y sigue diciendo «100% pagado», que ahí sí es
 * cierto.
 *
 * ## Las otras dos formas de que «100% pagado» sea mentira
 *
 * La primera versión de esta función tapaba un solo caso —cero movimientos— y la re-revisión
 * encontró que la misma familia entraba por otras dos puertas. Todas comparten la forma: `paidPct`
 * clampa a `[0, 1]`, así que **cualquier** deuda que no sea positiva sale como 1.0.
 *
 * - **Deuda negativa.** Reproducido: crédito creado en $0 y después un abono extraordinario —la
 *   operación que esta ola estrena— deja la deuda en −$1.500.000 sobre un crédito de
 *   $60.000.000 que nunca recibió su desembolso. Ahí `hasMovements` vale `true` (hubo un
 *   movimiento) y la guarda de arriba no dispara. Es el mismo escenario del bloqueante anterior
 *   —el dueño interrumpido entre el paso 1 y el paso 2— con los dos pasos en orden invertido, y
 *   esta rama lo vuelve alcanzable: antes un crédito nacía siempre con deuda y ningún traspaso
 *   podía tocarlo, así que pasarse de la deuda entera era inverosímil.
 *
 * - **Deuda en otra moneda** (preexistente, no la trae esta rama). `account.balance` es el
 *   **componente COP** del saldo (ver `enrichWith`), así que un préstamo cuyos movimientos son
 *   todos en dólares tiene `balance = 0` con `hasMovements = true`, y daba «100% pagado» sobre
 *   una deuda intacta. El porcentaje compara contra un `principal` en COP: sobre un saldo que no
 *   está en COP no hay nada que comparar, y decirlo es más honesto que calcularlo.
 *
 * En los dos casos se **suprime el porcentaje** en vez de inventarle un número: la tarjeta pasa a
 * pedir que se revise, que es lo único cierto que se puede decir.
 *
 * Lo que NO se toca es `totalDebtCop`: una deuda negativa le resta al total, y eso es la suma
 * honesta de lo que hay registrado. Corregirla ahí sería tapar la anomalía justo en la cifra que
 * el dueño usa para confiar; el lugar donde se señala es la tarjeta del crédito que la causó.
 *
 * Solo aplica con términos y capital original cargados: sin eso no hay porcentaje que calcular ni
 * desembolso que reclamar, y la tarjeta se comporta como siempre.
 */
fun progresoDeCredito(credit: CreditSummary): ProgresoDeCredito {
    val capital = credit.terms?.principal ?: 0L
    if (capital <= 0L) return porcentajePagado(credit)

    if (!credit.hasMovements) return aviso("Falta registrar el desembolso")
    // Antes que el signo: con saldo en otra moneda el componente COP es 0 y nunca sería negativo,
    // así que este es el motivo real y el que se le debe explicar.
    if (deudaEnOtraMoneda(credit)) return aviso("Deuda en otra moneda")
    if (credit.account.balance < 0L) return aviso("Deuda en negativo — revísala")

    return porcentajePagado(credit)
}

private fun aviso(texto: String) = ProgresoDeCredito(texto, fraccion = 0f, esAviso = true)

private fun porcentajePagado(credit: CreditSummary): ProgresoDeCredito {
    val pct = (credit.paidPct ?: 0.0).toFloat()
    return ProgresoDeCredito("${(pct * 100).toInt()}% pagado", fraccion = pct, esAviso = false)
}

/**
 * ¿Este préstamo debe plata que **no** está en el componente COP de su saldo?
 *
 * `balancesByCurrency` viene derivado del server junto con el saldo (ver `enrichWith`), así que la
 * pregunta se responde con lo que ya llegó. Un mapa vacío —lo que manda un server viejo, o una
 * cuenta sin eventos— responde `false` y la tarjeta se comporta como siempre.
 */
private fun deudaEnOtraMoneda(credit: CreditSummary): Boolean =
    credit.account.balancesByCurrency.any { (moneda, saldo) -> moneda != "COP" && saldo != 0L }
