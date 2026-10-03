package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.DeudaParaSalir
import com.jvillada.movi.shared.model.EstrategiaDeSalida
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.PlanDeSalida
import com.jvillada.movi.shared.model.PorQueNoEntraAlPlan
import com.jvillada.movi.shared.model.SalidaDeUnaDeuda
import com.jvillada.movi.shared.model.TipoDeDeuda
import com.jvillada.movi.shared.model.mas
import com.jvillada.movi.shared.model.nombreDe
import com.jvillada.movi.ui.components.formatCOP
import com.jvillada.movi.ui.components.formatMoney

/**
 * # Lo que dice «Cómo salir de tus deudas» (Ola 4)
 *
 * Las frases, puras y aparte de la pantalla para que las pruebas las lean exactas. La cuenta es
 * [com.jvillada.movi.shared.model.planDeSalida] en `:core`; acá no se calcula plata, se dice.
 */

const val TITULO_SALIDA_DE_DEUDAS = "Cómo salir de tus deudas"

/** El pie de la pantalla: es información, no asesoría. */
const val PIE_DEL_PLAN_DE_SALIDA = "Es un cálculo con tus datos, no una recomendación financiera."

/** Los supuestos, dichos una vez, debajo del simulador. */
const val SUPUESTOS_DEL_PLAN_DE_SALIDA =
    "Supone que la tasa y la cuota no cambian (una tarjeta paga su mínimo). El abono va entero a capital de la " +
        "primera deuda del orden; cuando la terminas pasa a la siguiente, y su cuota te queda libre."

val ROTULOS_DE_LAS_ESTRATEGIAS = listOf("Avalancha", "Bola de nieve")

fun estrategiaDelIndice(i: Int): EstrategiaDeSalida =
    if (i == 1) EstrategiaDeSalida.BOLA_DE_NIEVE else EstrategiaDeSalida.AVALANCHA

/** Qué hace cada estrategia, en una línea. */
fun explicacionDe(estrategia: EstrategiaDeSalida): String = when (estrategia) {
    EstrategiaDeSalida.AVALANCHA -> "Primero la de mayor tasa: es la que menos interés te cobra en total."
    EstrategiaDeSalida.BOLA_DE_NIEVE -> "Primero la de menor saldo: terminas una antes y ves el avance."
}

/** «29,64 %» — la tasa como la escribe el dueño, con coma. */
fun tasaEnTexto(tasa: Double): String {
    val texto = tasa.toString().removeSuffix(".0").replace('.', ',')
    return "$texto %"
}

/** «Saldo $507.553 · 29,64 % E.A. · cuota $60.000 · $12.380 de interés este mes». */
fun textoDeLaDeuda(d: DeudaParaSalir): String = listOfNotNull(
    "Saldo ${formatMoney(d.saldo, d.moneda)}",
    when {
        d.sinIntereses -> "no cobra intereses"
        d.tasaEa != null -> "${tasaEnTexto(d.tasaEa!!)} E.A."
        else -> null
    },
    d.cuota?.let { (if (d.tipo == TipoDeDeuda.TARJETA) "mínimo " else "cuota ") + formatMoney(it, d.moneda) },
    d.interesDelMes?.takeIf { !d.sinIntereses && d.moneda == "COP" }?.let { "${formatCOP(it)} de interés este mes" },
).joinToString(" · ")

/** «18 cuotas, hasta marzo de 2028», o que no se termina. */
fun cuandoTermina(meses: Int?, periodoActual: PeriodoFinanciero): String =
    if (meses == null) "no se termina a este ritmo"
    else "${if (meses == 1) "1 cuota" else "$meses cuotas"}, hasta ${nombreDe(periodoActual.mas((meses - 1).coerceAtLeast(0)))}"

/** Cómo sale una deuda con y sin el abono. Sin abono (o si no cambia nada) solo dice cuándo termina. */
fun textoDeLaSalida(s: SalidaDeUnaDeuda, abono: Long, periodoActual: PeriodoFinanciero): String {
    val sin = cuandoTermina(s.mesesSinAbono, periodoActual)
    if (abono <= 0L || s.mesesConAbono == s.mesesSinAbono) return "Termina en $sin"
    val con = cuandoTermina(s.mesesConAbono, periodoActual)
    val ahorro = s.interesAhorrado?.takeIf { it > 0L }?.let { " · ahorras ${formatCOP(it)} de interés" }.orEmpty()
    return "Con tu abono: $con$ahorro. Sin abono: $sin."
}

/** La cifra grande del simulador y su línea de apoyo. */
fun resumenDelSimulador(plan: PlanDeSalida, periodoActual: PeriodoFinanciero): List<String> {
    if (plan.enElCalculo.isEmpty()) return listOf("Ninguna de tus deudas tiene todavía la tasa y la cuota para calcularlo.")
    if (plan.abonoMensual <= 0L) {
        return listOf("Escribe cuánto podrías abonar al mes para ver cuándo sales y cuánto interés te ahorras.")
    }
    return buildList {
        add("Te ahorras ${formatCOP(plan.interesAhorrado)} de interés")
        val con = plan.mesesHastaSalir
        val sin = plan.mesesHastaSalirSinAbono
        when {
            con != null && sin != null && con < sin ->
                add(
                    "Sales de estas deudas en ${nombreDe(periodoActual.mas(con - 1))}, " +
                        "${sin - con} ${if (sin - con == 1) "mes" else "meses"} antes que sin abono.",
                )
            con != null -> add("Sales de estas deudas en ${nombreDe(periodoActual.mas(con - 1))}.")
            else -> add("Alguna de estas deudas no se termina ni con este abono.")
        }
        plan.seTerminanSoloConAbono.takeIf { it.isNotEmpty() }?.let { solas ->
            add("${solas.joinToString(" y ") { it.deuda.nombre }} solo se termina con el abono: sin él, no baja.")
        }
    }
}

/** Por qué una deuda no entra, con lo que hay que hacer. */
fun motivoDeFuera(d: DeudaParaSalir): String = when (d.porQueNoEntra) {
    PorQueNoEntraAlPlan.FALTA_LA_TASA -> "Falta la tasa: cárgala para incluirla"
    PorQueNoEntraAlPlan.FALTA_EL_PAGO_MINIMO -> "Falta el pago mínimo: cárgalo para incluirla"
    PorQueNoEntraAlPlan.FALTA_LA_CUOTA -> "Falta la cuota: cárgala para incluirla"
    PorQueNoEntraAlPlan.EN_OTRA_MONEDA -> "Está en ${d.moneda}: no entra a una cuenta en pesos"
    null -> ""
}

/** «La paga tu nómina» / «La paga Skandia». */
fun quienPagaEnTexto(d: DeudaParaSalir): String = "La paga ${d.quienLaPaga}"

/** El total de interés de este mes de las deudas que salen de tu bolsillo y tienen tasa. */
fun interesDelMesPropio(plan: PlanDeSalida): Long = plan.enElCalculo.sumOf { it.deuda.interesDelMes ?: 0L }
