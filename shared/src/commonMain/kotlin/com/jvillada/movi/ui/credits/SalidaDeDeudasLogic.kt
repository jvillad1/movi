package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.CardSummary
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.DeudaParaSalir
import com.jvillada.movi.shared.model.InteresDelMes
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuota
import com.jvillada.movi.shared.model.mesDeLaPrimeraCuotaSinPagar
import com.jvillada.movi.shared.model.mesDeLaUltimaCuota
import com.jvillada.movi.shared.model.mesesEntre
import kotlinx.datetime.LocalDate
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

/**
 * El ejemplo del campo del abono extra (vuelta 42): el campo lo pinta «Ej: 500.000» después del «$».
 * Era la guía «Abono extra al mes, por ejemplo $500.000», que a 390 dp se cortaba.
 */
const val EJEMPLO_DEL_ABONO_EXTRA = "500.000"

/** El campo del abono extra y la nota del final, para las pruebas. */
const val TAG_ABONO_EXTRA = "salida:abono-extra"
const val TAG_NOTA_DEL_PLAN_DE_SALIDA = "salida:nota"

/**
 * **La nota del final** (vuelta 42): los supuestos del cálculo y que no es asesoría, juntos. Los
 * supuestos iban dentro del simulador, un párrafo gris debajo de la frase de apoyo.
 */
fun notaDelPlanDeSalida(): String = "$SUPUESTOS_DEL_PLAN_DE_SALIDA $PIE_DEL_PLAN_DE_SALIDA"

/** Los supuestos, dichos una vez, en la nota del final. */
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

/**
 * **El mes de la primera cuota sin pagar de cada deuda** (vuelta 59), por id: de ahí se cuentan sus
 * cuotas, no del mes en curso («10 cuotas, hasta agosto de 2027», no julio). Un crédito usa sus cuotas
 * pagadas ([mesDeLaPrimeraCuota]); una tarjeta, su próximo día de pago. Sin datos no está y quien lee
 * usa el mes en curso.
 */
fun primerasCuotasDe(
    creditos: List<CreditSummary>,
    tarjetas: List<CardSummary>,
    hoy: LocalDate,
): Map<String, PeriodoFinanciero> = buildMap {
    creditos.forEach { c -> mesDeLaPrimeraCuota(c, hoy)?.let { put(c.account.id, it) } }
    tarjetas.forEach { t ->
        t.terms?.let { put(t.account.id, mesDeLaPrimeraCuotaSinPagar(it.paymentDay, hoy, pagadas = null)) }
    }
}

/**
 * La línea de debajo de «Tus deudas te cobran $X de interés este mes» (vuelta 59): la cifra es
 * [interesDelMes], la misma de Créditos y de Movi AI, y acá se desglosa.
 */
fun desgloseDelInteresDelMes(interes: InteresDelMes): String = when {
    interes.tarjetas > 0L && interes.creditosPropios > 0L ->
        "${formatCOP(interes.creditosPropios)} de tus créditos y ${formatCOP(interes.tarjetas)} de tus tarjetas. La misma cifra de Créditos."
    interes.tarjetas > 0L -> "De tus tarjetas. La misma cifra de Créditos."
    else -> "De los créditos que salen de tu bolsillo. La misma cifra de Créditos."
}

/** Cómo sale una deuda con y sin el abono. Sin abono (o si no cambia nada) solo dice cuándo termina. */
fun textoDeLaSalida(s: SalidaDeUnaDeuda, abono: Long, periodoActual: PeriodoFinanciero): String {
    val sin = cuandoTermina(s.mesesSinAbono, periodoActual)
    if (abono <= 0L || s.mesesConAbono == s.mesesSinAbono) return "Termina en $sin"
    val con = cuandoTermina(s.mesesConAbono, periodoActual)
    val ahorro = s.interesAhorrado?.takeIf { it > 0L }?.let { " · ahorras ${formatCOP(it)} de interés" }.orEmpty()
    return "Con tu abono: $con$ahorro. Sin abono: $sin."
}

/**
 * El mes en que sale la última cuota de las deudas del cálculo, cada una contada desde su primera cuota
 * sin pagar ([primeras], o [periodoActual] si no está). `null` si alguna no se termina.
 */
private fun ultimoMes(
    plan: PlanDeSalida,
    periodoActual: PeriodoFinanciero,
    primeras: Map<String, PeriodoFinanciero>,
    meses: (SalidaDeUnaDeuda) -> Int?,
): PeriodoFinanciero? {
    if (plan.enElCalculo.isEmpty()) return null
    val fechas = plan.enElCalculo.map { s ->
        val n = meses(s) ?: return null
        mesDeLaUltimaCuota(primeras[s.deuda.id] ?: periodoActual, n)
    }
    return fechas.maxByOrNull { it.prefijo }
}

/** La cifra grande del simulador y su línea de apoyo. */
fun resumenDelSimulador(
    plan: PlanDeSalida,
    periodoActual: PeriodoFinanciero,
    /** Vuelta 59: la primera cuota sin pagar de cada deuda (ver [primerasCuotasDe]). */
    primeras: Map<String, PeriodoFinanciero> = emptyMap(),
): List<String> {
    if (plan.enElCalculo.isEmpty()) return listOf("Ninguna de tus deudas tiene todavía la tasa y la cuota para calcularlo.")
    if (plan.abonoMensual <= 0L) {
        return listOf("Escribe cuánto podrías abonar al mes para ver cuándo sales y cuánto interés te ahorras.")
    }
    return buildList {
        add("Te ahorras ${formatCOP(plan.interesAhorrado)} de interés")
        // Vuelta 59: cada deuda desde su primera cuota sin pagar, no todas desde el mes en curso.
        val con = ultimoMes(plan, periodoActual, primeras) { it.mesesConAbono }
        val sin = ultimoMes(plan, periodoActual, primeras) { it.mesesSinAbono }
        val antes = if (con != null && sin != null) mesesEntre(con, sin) else 0
        when {
            con != null && antes > 0 ->
                add(
                    "Sales de estas deudas en ${nombreDe(con)}, " +
                        "$antes ${if (antes == 1) "mes" else "meses"} antes que sin abono.",
                )
            con != null -> add("Sales de estas deudas en ${nombreDe(con)}.")
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

/**
 * El total de interés de este mes de las deudas del cálculo. **Ya no es la cifra de la pantalla**
 * (vuelta 59): esa es [interesDelMes], la misma de Créditos. Queda para quien quiera solo las del cálculo.
 */
fun interesDelMesPropio(plan: PlanDeSalida): Long = plan.enElCalculo.sumOf { it.deuda.interesDelMes ?: 0L }
