package com.jvillada.movi.shared.model

/*
 * # Vuelta 141 · la cuota que se paga desde una cuenta que no es «Tu plata» (issue #701)
 *
 * La Hipoteca 1254 de Davibank se paga desde la **AFC Davibank 9497**, una cuenta de ahorros
 * condicionada a vivienda que se llena con retiros de Skandia. Para que no bajara «Te quedan» se le
 * había puesto «la paga Skandia» (`CreditTerms.paidBy`), y con eso Movi la trataba como la cuota de
 * otra persona: no salía en Pagos ni en «Anotar pago».
 *
 * La decisión del dueño (7-nov), tal cual: *«Aparece en Pagos con «Anotar pago» y el pago se anota
 * saliendo de la AFC. No baja «Te quedan» ni «Falta por pagar», porque esa plata no es de tu sueldo.
 * La fila dice «Sale de la AFC: no toca Tu plata».»*
 *
 * Así que el crédito dice **de qué cuenta sale la cuota** ([CreditTerms.sePagaDesde]) y, si esa
 * cuenta está fuera de «Tu plata» —la misma regla del patrimonio, [esDeTuPlata]: condicionada, una
 * deuda o un bien—, la fila de Pagos lleva [PagoDelPeriodo.saleDeFueraDeTuPlata]: se lista y se
 * anota, pero no suma en ninguna cifra de lo que te queda (los fijos, «Falta por pagar», la cola, la
 * caja). Hecho el pago, sus dos patas son las de siempre (`pagoDeCuotaLegs`): la deuda baja igual y
 * «Te quedan» no se mueve, porque la plata salió de afuera (`PlataDelPeriodo.pagadoDesdeFuera` la
 * suma y `pagosDeDeudaFueraDelChecklist` la resta: se compensan).
 *
 * **No es un débito automático** ([CreditTerms.debitoAutomaticoDesde]): ese dice que el banco la
 * cobra solo y dispara «¿Se cobró?». Esta la paga el dueño.
 */

/** Ver [validarCuentaDeLaCuota]. */
const val CUOTA_DESDE_CON_LIBRANZA: String =
    "Una cuota que se descuenta de tu nómina no sale de una cuenta. Desmarca una de las dos."

/** Ver [validarCuentaDeLaCuota]. */
const val CUOTA_DESDE_CON_TERCERO: String =
    "Una cuota que paga otra persona no sale de tu cuenta. Borra quién la paga o quita la cuenta."

/** Ver [validarCuentaDeLaCuota]. */
const val CUOTA_DESDE_CON_DEBITO: String =
    "El débito automático ya dice de qué cuenta sale la cuota. Quita uno de los dos."

/** Ver [validarCuentaDeLaCuota]. */
const val CUOTA_DESDE_SIN_CUENTA: String = "Esa cuenta no existe o no es tuya."

/** Ver [validarCuentaDeLaCuota]. */
const val CUOTA_DESDE_UNA_DEUDA: String =
    "La cuota sale de una cuenta de dinero o de inversión, no de otra deuda."

/** Ver [validarCuentaDeLaCuota]. */
const val CUOTA_DESDE_OTRA_MONEDA: String =
    "La cuenta de la que sale la cuota tiene que estar en la misma moneda que el crédito."

/** Lo que dice la hoja del crédito cuando la cuenta elegida no es «Tu plata». */
const val CUENTA_DE_LA_CUOTA_FUERA_DE_TU_PLATA: String =
    "Esta cuenta no es Tu plata: la cuota no bajará «Te quedan»."

/**
 * ¿Se puede guardar [CreditTerms.sePagaDesde]? `null` si sí (o si no hay cuenta); si no, **el motivo en
 * español**, que es lo que dicen la hoja del crédito y el 422 del server. Mismo idioma que
 * [validarDebitoAutomatico].
 *
 * - **Libranza, «la paga otro» o débito automático**: las tres ya contestan de dónde sale la cuota.
 * - **La cuenta no existe o no es del dueño** ([cuenta] en `null`).
 * - **La cuenta es una deuda o un bien**: solo una cuenta de dinero o de inversión paga una cuota.
 * - **Otra moneda**: el pago de una cuota no cruza monedas (ver [validarPagoDeCuota]).
 */
fun validarCuentaDeLaCuota(terms: CreditTerms, cuenta: Account?, monedaDelCredito: String): String? {
    if (terms.sePagaDesde.isNullOrBlank()) return null
    return when {
        terms.payrollDeduction -> CUOTA_DESDE_CON_LIBRANZA
        !terms.paidBy.isNullOrBlank() -> CUOTA_DESDE_CON_TERCERO
        !terms.debitoAutomaticoDesde.isNullOrBlank() -> CUOTA_DESDE_CON_DEBITO
        cuenta == null -> CUOTA_DESDE_SIN_CUENTA
        !sirveParaPagarLaCuota(cuenta) -> CUOTA_DESDE_UNA_DEUDA
        cuenta.currency != monedaDelCredito -> CUOTA_DESDE_OTRA_MONEDA
        else -> null
    }
}

/** Una cuenta de dinero o de inversión (no una deuda, no un bien): de ahí puede salir una cuota. */
fun sirveParaPagarLaCuota(cuenta: Account): Boolean = !cuenta.esBien && cuenta.type.group != AccountGroup.DEUDA

/**
 * Las cuentas que ofrece «¿De qué cuenta sale la cuota?» en la hoja del crédito: las de dinero o de
 * inversión en [moneda], **también las condicionadas** (la AFC es justo el caso). La misma regla que
 * [validarCuentaDeLaCuota].
 */
fun cuentasParaLaCuota(cuentas: List<Account>, moneda: String = "COP"): List<Account> =
    cuentas.filter { sirveParaPagarLaCuota(it) && it.currency == moneda }

/**
 * **¿La cuota que sale de esta cuenta queda fuera de «Tu plata»?** Es la negación de [esDeTuPlata], la
 * regla del patrimonio: una cuenta condicionada (la AFC, la pensión voluntaria), una deuda o un bien.
 * La usan el server (para marcar la regla de la cuota, `virtualRuleFor`) y la hoja del crédito (para
 * avisarlo antes de guardar): una sola regla.
 */
fun laCuotaSaleDeFueraDeTuPlata(tipo: AccountType, condicionadaA: String?, esBien: Boolean = false): Boolean =
    !esDeTuPlata(tipo, condicionadaA, esBien)

/** Lo mismo que la otra, para una cuenta ya armada. */
fun laCuotaSaleDeFueraDeTuPlata(cuenta: Account): Boolean = !cuenta.esDeTuPlata()

/** La línea de la fila de Pagos: «Sale de AFC Davibank 9497: no toca Tu plata». */
fun lineaDeLaCuotaFueraDeTuPlata(nombreDeLaCuenta: String?): String =
    nombreDeLaCuenta?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { "Sale de $it: no toca Tu plata" }
        ?: "Sale de una cuenta que no es Tu plata: no la toca"
