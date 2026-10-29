package com.jvillada.movi.shared.model

/**
 * # Un solo interés del mes (vuelta 59)
 *
 * Créditos decía «Intereses este mes $466.775» —solo los créditos— y «Cómo salir de tus deudas»
 * «$499.677», que sumaba la tarjeta al 28 %; la sugerencia de Movi AI repetía la primera. Tres
 * pantallas, dos cifras para la misma pregunta.
 *
 * Ahora la cuenta es esta, una vez, y la usan las tres. **Entran créditos y tarjetas**: una tarjeta con
 * deuda y tasa cobra interés igual que un préstamo, y la pantalla de Créditos las lista juntas debajo
 * de la misma «Deuda total». La separación que sí se conserva es la de quién paga: lo que paga la
 * nómina o un tercero va aparte ([ajeno]), como siempre ([saleDeTuBolsillo]). Una tarjeta la pagas tú.
 *
 * - **Un crédito** aporta el interés de su plan ([PlanDelCredito.interes]): el del extracto cuando lo
 *   hay ([CreditTerms.interesRealVigente]), si no `saldo × tasa mensual`. Sin plan (sin saldo, en otra
 *   moneda, sin términos) no aporta nada.
 * - **Una tarjeta** aporta [DeudaParaSalir.interesDelMes]: su deuda en pesos por la tasa mensual de su
 *   E.A. ([interesDelPeriodo], la misma aritmética que cada cuota). Sin tasa, sin deuda o en otra
 *   moneda, nada.
 */
data class InteresDelMes(
    /** El de los créditos cuya cuota sale de tu cuenta. */
    val creditosPropios: Long,
    /** El de los créditos que paga la nómina o un tercero. */
    val creditosAjenos: Long,
    /** El de las tarjetas con deuda y tasa. */
    val tarjetas: Long,
) {
    /** **Lo que pagas tú este mes en intereses**: tus créditos y tus tarjetas. La cifra de las tres pantallas. */
    val propio: Long get() = creditosPropios + tarjetas

    /** Lo que paga la nómina o un tercero. */
    val ajeno: Long get() = creditosAjenos
}

/**
 * El interés del mes de [planes] (uno por crédito, ver [planDelCredito]) y [tarjetas]. Ver
 * [InteresDelMes].
 */
fun interesDelMes(planes: List<PlanDelCredito>, tarjetas: List<CardSummary>): InteresDelMes =
    interesDelMesDe(planes, deudasParaSalir(emptyList(), tarjetas))

/**
 * Lo mismo que [interesDelMes], con las tarjetas ya pasadas a [DeudaParaSalir] (las que no son tarjeta
 * se ignoran: un crédito entra por su plan).
 */
fun interesDelMesDe(planes: List<PlanDelCredito>, tarjetas: List<DeudaParaSalir>): InteresDelMes = InteresDelMes(
    creditosPropios = planes.filter { it.saleDeTuBolsillo }.sumOf { it.interes },
    creditosAjenos = planes.filterNot { it.saleDeTuBolsillo }.sumOf { it.interes },
    tarjetas = tarjetas
        .filter { it.tipo == TipoDeDeuda.TARJETA && it.moneda == "COP" && it.saldo > 0L }
        .sumOf { it.interesDelMes ?: 0L },
)
