package com.jvillada.movi.shared.model

/*
 * # Vuelta 57 · el detalle de una cuota dice lo que es
 *
 * La revisión de la vuelta 56 abrió una cuota de crédito y leyó «Un traspaso no se puede recategorizar:
 * es plata que se movió entre tus cuentas, no un gasto ni un ingreso». Desde la vuelta 50 la cuota
 * **cuenta como gasto** ([comoSePresenta]), así que la frase contradecía a la fila y a los totales. El
 * texto de «traspaso» queda para lo que sí lo es: un traspaso, el pago de una tarjeta y un abono extra.
 */

/**
 * **El crédito de una cuota**: el de la pata de la deuda (la cuenta del movimiento, si es un crédito) o el
 * que nombra la pata del dinero —`pagoDeCuotaLegs` la describe «Cuota de Vehículo 4083»—, el de nombre más
 * largo si nombra a varios. `null` si no se sabe (el dueño le cambió el concepto): el texto lo dice sin nombre.
 */
fun creditoDeLaCuota(evento: FinancialEvent, cuentas: List<Account>): Account? {
    val creditos = cuentas.filter { it.type == AccountType.LOAN }
    creditos.firstOrNull { it.id == evento.accountId }?.let { return it }
    return creditos
        .filter { it.name.isNotBlank() && evento.description.contains(it.name, ignoreCase = true) }
        .maxByOrNull { it.name.length }
}

/** «Es la cuota de Vehículo 4083: cuenta como gasto. Para cambiarla, anúlala y vuelve a anotarla.» */
fun textoDeLaCuotaQueNoSeRecategoriza(credito: String?): String =
    "Es la cuota de ${credito?.takeIf { it.isNotBlank() } ?: "un crédito"}: cuenta como gasto. " +
        "Para cambiarla, anúlala y vuelve a anotarla."

/**
 * **Por qué esta pata no se recategoriza**, dicho según lo que es: la cuota de un crédito (cuenta como
 * gasto, [textoDeLaCuotaQueNoSeRecategoriza]), un desembolso ([DESEMBOLSO_RECATEGORIZE_BLOCKED]: plata
 * que entró) y, para lo demás —un traspaso, el pago de una tarjeta, un abono extra—,
 * [TRANSFER_RECATEGORIZE_BLOCKED]. Pura.
 */
fun porQueLaPataNoSeRecategoriza(evento: FinancialEvent, cuentas: List<Account>): String = when {
    esCategoriaDelDesembolso(evento.category) -> DESEMBOLSO_RECATEGORIZE_BLOCKED
    esCategoriaDeCuota(evento.category) -> textoDeLaCuotaQueNoSeRecategoriza(creditoDeLaCuota(evento, cuentas)?.name)
    else -> TRANSFER_RECATEGORIZE_BLOCKED
}
