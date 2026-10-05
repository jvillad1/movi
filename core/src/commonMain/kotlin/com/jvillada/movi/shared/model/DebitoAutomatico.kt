package com.jvillada.movi.shared.model

/*
 * # Lo que el banco cobra solo
 *
 * Algunas cuotas de crédito (y algunos recurrentes: un seguro, una suscripción) **se debitan solas**
 * de una cuenta del dueño, y el banco no manda ningún aviso cuando las cobra. La auditoría de la
 * ingesta (4-oct-2026, causa D4) contó cinco cargadas a mano en 45 días: Libre inversión 9695
 * ($1.204.064), Crediágil 3090 ($26.485) y Techo Gardenera ($474.240).
 *
 * El dueño marca qué se debita solo y de qué cuenta ([CreditTerms.debitoAutomaticoDesde]); el día del
 * vencimiento, si ningún movimiento lo prueba, Movi lo propone armado en «Por revisar». **Nunca lo
 * anota solo**: el checklist lo tilda el movimiento, y el movimiento lo confirma el dueño.
 */

/** Ver [validarDebitoAutomatico]. Las tres respuestas a «¿esta cuota sale de tu cuenta?» se excluyen. */
const val DEBITO_CON_LIBRANZA: String =
    "Una cuota que se descuenta de tu nómina no se debita de una cuenta. Desmarca una de las dos."

/** Ver [validarDebitoAutomatico]. */
const val DEBITO_CON_TERCERO: String =
    "Una cuota que paga otra persona no se debita de tu cuenta. Borra quién la paga o quita el débito."

/** Ver [validarDebitoAutomatico]. */
const val DEBITO_SIN_CUENTA: String = "Elige de qué cuenta la cobra el banco."

/** Ver [validarDebitoAutomatico]. */
const val DEBITO_DESDE_UNA_DEUDA: String =
    "El banco cobra la cuota de una cuenta de dinero, no de otra deuda."

/** Ver [validarDebitoAutomatico]. */
const val DEBITO_EN_OTRA_MONEDA: String =
    "La cuenta del débito tiene que estar en la misma moneda que el crédito."

/**
 * ¿Se puede guardar este débito automático? `null` si sí (o si no hay débito); si no, **el motivo en
 * español**, que es lo que dicen la hoja del crédito y el 400 del server.
 *
 * Mismo idioma que [validarTasaDelCredito]: una sola función para la hoja y para `POST`/`PUT
 * /api/credits`, así crear y editar validan igual y un cliente viejo o un cuerpo escrito a mano
 * tropiezan con la misma frase.
 *
 * - **Libranza o «la paga otro»**: las dos dicen que la cuota no sale de una cuenta del dueño; el
 *   débito dice de cuál sale. Juntas son dos respuestas a la misma pregunta.
 * - **La cuenta no existe o no es suya** ([cuenta] en `null`).
 * - **La cuenta es otra deuda**: el banco no debita una cuota de una tarjeta ni de otro crédito, y
 *   `POST /api/payments/installment` —el camino con que se confirma— lo rechazaría igual.
 * - **Otra moneda**: el pago de una cuota no cruza monedas (ver [validarPagoDeCuota]).
 */
fun validarDebitoAutomatico(terms: CreditTerms, cuenta: Account?, monedaDelCredito: String): String? {
    if (terms.debitoAutomaticoDesde.isNullOrBlank()) return null
    return when {
        terms.payrollDeduction -> DEBITO_CON_LIBRANZA
        !terms.paidBy.isNullOrBlank() -> DEBITO_CON_TERCERO
        cuenta == null -> DEBITO_SIN_CUENTA
        cuenta.type.group == AccountGroup.DEUDA -> DEBITO_DESDE_UNA_DEUDA
        cuenta.currency != monedaDelCredito -> DEBITO_EN_OTRA_MONEDA
        else -> null
    }
}

/**
 * Las cuentas de las que el banco puede debitar una cuota en [moneda]: las que no son deuda, en esa
 * moneda. Es la lista que ofrece la hoja del crédito; la misma regla que [validarDebitoAutomatico],
 * para no ofrecer una cuenta y después decir que no se puede.
 */
fun cuentasParaElDebito(cuentas: List<Account>, moneda: String = "COP"): List<Account> =
    cuentas.filter { it.type.group != AccountGroup.DEUDA && it.currency == moneda }
