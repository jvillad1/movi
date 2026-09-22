package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.PlanDelCredito
import com.jvillada.movi.shared.model.planDelCredito
import com.jvillada.movi.ui.components.formatMoney
import com.jvillada.movi.ui.credits.NO_COBRA_INTERESES
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo

/**
 * # «¿Cuánto debo pagar?» — lo que la cuota de este período trae adentro
 *
 * El dueño, sobre la cuota del carro: *«no me sale un extracto en el sitio ni valor a pagar, tenía
 * ese valor en mi cabeza y por eso lo puse; imagino pagué de más»*. Y pagó de más: el 19 de
 * septiembre giró **$4.178.163** contra una cuota registrada de **$4.101.123** —$77.040 que se
 * fueron a capital— porque no tiene ninguna fuente que le diga qué pagar.
 *
 * Movi sí puede ser esa fuente: tiene el saldo, la tasa, el seguro y los otros cargos de cada
 * crédito, y [planDelCredito] ya reparte la cuota con la **misma** aritmética que usa el server al
 * registrar el pago. Lo único que faltaba era decirlo en el lugar donde él mira qué le falta pagar
 * este mes: el checklist del período.
 *
 * ## Lo que esto es, y lo que no
 *
 * Es **una estimación de Movi**, y se rotula como tal en la misma línea que la cifra. No es el
 * valor del banco: el interés sale del saldo de HOY —en Movi la deuda es la suma de sus eventos,
 * sin fecha de corte (ver [com.jvillada.movi.shared.model.desglosarCuota])— y de la tasa que el
 * dueño registró, que en un crédito colombiano se recalcula.
 *
 * Por eso **desaparece en vez de adivinar**: sin tasa, sin cuota o sin deuda registrada no hay
 * estimación que dar, y el proyecto ya distingue «no sabemos la tasa»
 * ([com.jvillada.movi.shared.model.ComoVaLaDeuda.SIN_TASA]) de «no cobra intereses»
 * ([com.jvillada.movi.shared.model.CreditTerms.sinIntereses]) — las dos salen distinto de acá.
 *
 * ## Por qué no hay estimación para una tarjeta
 *
 * Porque la cuota de una tarjeta no existe: se paga el mínimo, el total, o algo en el medio, y eso
 * lo decide el extracto de cada mes. Es la misma decisión que documenta
 * [com.jvillada.movi.shared.model.RecurringRule.montoEsSaldo] —Movi se niega a estimar un mínimo
 * que el dueño no pueda verificar— y estimarlo por esta puerta la desharía. La fila de una tarjeta
 * sigue mostrando su saldo, rotulado como saldo.
 */

/** El rótulo que viaja **pegado a la cifra**: esto lo calculó Movi, no lo dijo el banco. */
const val ETIQUETA_ESTIMADO = "Movi estima"

/**
 * El pie que explica de dónde sale la estimación, debajo de las filas que la muestran.
 *
 * Va una sola vez por grupo y no en cada fila: con seis créditos pendientes, repetir la frase
 * entera seis veces la volvería decoración. Lo que **sí** viaja en cada fila es el rótulo
 * ([ETIQUETA_ESTIMADO]), que es la parte sin la cual una cifra se lee como un hecho.
 */
const val ESTIMADO_SOBRE_LA_DEUDA_DE_HOY: String =
    "Lo estimamos con tu deuda de hoy y la tasa que registraste. No es el valor que te va a cobrar " +
        "el banco."

/**
 * El plan de cada crédito, **indexado por el id de la regla del checklist**.
 *
 * La regla sintética de una cuota es `credit_<accountId>` (ver [CREDIT_RULE_PREFIX]), así que la
 * fila del checklist y el crédito se encuentran sin endpoint nuevo: el id que ya trae la fila es la
 * llave. Un crédito sin plan —sin términos, sin movimientos, o con la deuda en otra moneda— no
 * entra al mapa, y su fila se queda sin estimación, que es exactamente lo que corresponde.
 */
fun planesDeLasCuotas(credits: List<CreditSummary>): Map<String, PlanDelCredito> =
    credits.mapNotNull { credito ->
        planDelCredito(credito)?.let { plan -> (CREDIT_RULE_PREFIX + credito.account.id) to plan }
    }.toMap()

/**
 * La estimación de la fila [pago], o `null` si no hay ninguna que dar.
 *
 * Devuelve `null` en tres casos, y los tres son «no hay nada que estimar»: la fila ya está marcada
 * (la pregunta es qué pagar, no qué pagué), su monto es un SALDO (una tarjeta, ver el KDoc del
 * archivo), o no hay plan para su crédito.
 */
fun estimacionDeLaFila(pago: PagoDelPeriodo, planes: Map<String, PlanDelCredito>): String? {
    if (pago.pagado || pago.montoEsSaldo || pago.esIngreso) return null
    val plan = planes[pago.ruleId] ?: return null
    return textoDeLaCuotaEstimada(plan, pago.moneda)
}

/**
 * **«Movi estima: $2.479.256 de interés · $89.100 el seguro · $1.532.767 a capital».**
 *
 * Cada renglón se nombra con la palabra del extracto y **por separado**, igual que
 * [com.jvillada.movi.ui.quickadd.textoDelDesglose] y por el mismo motivo: sumar el seguro con los
 * otros cargos daría una cifra que no cuadra contra ninguna línea del papel del banco.
 *
 * `null` cuando no se puede estimar sin inventar: sin tasa registrada, sin cuota o sin deuda
 * (ver [com.jvillada.movi.shared.model.ComoVaLaDeuda.seProyecta], que son justamente los tres
 * estados a los que les falta un insumo).
 */
fun textoDeLaCuotaEstimada(plan: PlanDelCredito, moneda: String = "COP"): String? {
    if (!plan.comoVa.seProyecta) return null
    fun plata(valor: Long) = formatMoney(valor, moneda)
    val partes = buildList {
        // Con «No cobra intereses» declarado no se dice «$0 de interés»: se leería como una
        // estimación de cero, que es otra afirmación. Misma postura que [textoDelInteres].
        if (!plan.sinIntereses) add(plata(plan.interes) + " de interés")
        if (plan.seguro > 0L) add(plata(plan.seguro) + " el seguro")
        if (plan.otrosCargos > 0L) add(plata(plan.otrosCargos) + " otros cargos")
        // **El capital solo cuando es positivo, y su ausencia se dice.** Sin el renglón final y
        // sin la frase, la lista de arriba se leería como el reparto completo de la cuota cuando
        // en realidad no alcanza a cubrirla — el caso del Hipotecario ·2334.
        if (plan.capital > 0L) add(plata(plan.capital) + " a capital") else add("nada baja la deuda")
    }
    val cabeza = if (plan.sinIntereses) NO_COBRA_INTERESES else ETIQUETA_ESTIMADO
    return cabeza + ": " + partes.joinToString(" · ")
}
