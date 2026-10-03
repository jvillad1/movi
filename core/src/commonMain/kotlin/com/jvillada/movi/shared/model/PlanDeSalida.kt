package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # El plan de salida de deudas (Ola 4 · «Movi mira adelante»)
 *
 * La pregunta: *si le pongo $X extra al mes a mis deudas, ¿en qué orden, cuándo salgo de cada una y
 * cuánto interés me ahorro?* El dueño tiene 12 créditos y 5 tarjetas; la cuenta a mano no la hace
 * nadie, y la pregunta sugerida «¿qué deuda me conviene abonar primero?» la contestaba el modelo
 * mirando tasas, sin cuenta detrás.
 *
 * ## Cómo se calcula (y lo que se supone)
 *
 * - **Mes a mes, con la misma aritmética que el resto de la app**: el interés del mes es
 *   `saldo × tasaMensualDeUnaEA(tasa)`, redondeado a pesos ([interesDelPeriodo], el mismo de
 *   [planDeUnaDeuda] y de cada pago de cuota). La cuota baja la deuda por `cuota − interés − lo que
 *   no amortiza` (seguros y otros cargos). Sin abono, el resultado es exactamente el de la pantalla
 *   de Créditos (cuántas cuotas faltan, cuánto interés queda).
 * - **El abono extra va entero a capital** de la primera deuda del orden; cuando esa se termina, el
 *   abono pasa a la siguiente. **La cuota que se libera NO se suma al abono**: queda libre en tu
 *   bolsillo. Así todo lo que se ahorra sale del abono, y con $0 de abono el ahorro es $0.
 * - **A tasa y cuota constantes.** Una tarjeta paga su mínimo cargado, fijo, aunque el banco lo
 *   recalcule cada mes. Se dice en la pantalla.
 *
 * ## Lo que queda afuera, y se dice
 *
 * - **Sin tasa** (las tarjetas, que hasta esta ola no tenían el campo): no se adivina una; se lista
 *   con «Falta la tasa». Igual una tarjeta sin mínimo o un crédito sin cuota.
 * - **Lo que no sale de tu bolsillo**: la libranza que descuenta la nómina y la cuota que paga otro
 *   (Skandia, Caro). Se muestran aparte y **no compiten por el abono** (ver [saleDeTuBolsillo]).
 * - **En otra moneda**: no hay una cifra en pesos honesta para mezclarla.
 *
 * Es información, no asesoría: la pantalla lo dice al pie.
 */

@Serializable
enum class TipoDeDeuda { CREDITO, TARJETA }

/** **Avalancha**: mayor tasa primero (paga menos interés). **Bola de nieve**: menor saldo primero. */
@Serializable
enum class EstrategiaDeSalida { AVALANCHA, BOLA_DE_NIEVE }

/** Por qué una deuda no entra al cálculo. */
@Serializable
enum class PorQueNoEntraAlPlan { FALTA_LA_TASA, FALTA_EL_PAGO_MINIMO, FALTA_LA_CUOTA, EN_OTRA_MONEDA }

/**
 * Una deuda con lo que el plan necesita de ella.
 *
 * @property cuota lo que sale cada mes: la cuota de un crédito o el pago mínimo de una tarjeta.
 * @property noAmortiza la parte de la cuota que no baja la deuda (seguros y otros cargos).
 * @property quienLaPaga `null` si sale de tu bolsillo; si no, en palabras («tu nómina», «Skandia»).
 */
@Serializable
data class DeudaParaSalir(
    val id: String,
    val nombre: String,
    val tipo: TipoDeDeuda,
    val saldo: Long,
    val tasaEa: Double?,
    val sinIntereses: Boolean = false,
    val cuota: Long?,
    val noAmortiza: Long = 0L,
    val quienLaPaga: String? = null,
    val moneda: String = "COP",
) {
    val saleDeTuBolsillo: Boolean get() = quienLaPaga == null

    private val tasaMensual: Double? get() = when {
        sinIntereses -> 0.0
        tasaEa == null || tasaEa <= 0.0 || !tasaEa.isFinite() -> null
        else -> tasaMensualDeUnaEA(tasaEa)
    }

    /** El interés de este mes sobre el saldo de hoy, o `null` sin tasa. */
    val interesDelMes: Long? get() = tasaMensual?.let { interesDelPeriodo(saldo, it) }

    /** Por qué no entra al cálculo, o `null` si entra (o si no sale de tu bolsillo: esas van aparte). */
    val porQueNoEntra: PorQueNoEntraAlPlan? get() = when {
        moneda != "COP" -> PorQueNoEntraAlPlan.EN_OTRA_MONEDA
        tasaMensual == null -> PorQueNoEntraAlPlan.FALTA_LA_TASA
        cuota == null || cuota <= 0L ->
            if (tipo == TipoDeDeuda.TARJETA) PorQueNoEntraAlPlan.FALTA_EL_PAGO_MINIMO else PorQueNoEntraAlPlan.FALTA_LA_CUOTA
        else -> null
    }

    internal val tasaMensualParaElPlan: Double get() = tasaMensual ?: 0.0
}

/**
 * Las deudas del dueño como las necesita el plan: sus créditos y sus tarjetas con deuda.
 *
 * Una deuda sin saldo (pagada, o un crédito que todavía no registra su desembolso) no está: no hay
 * nada que planear. El saldo es el de la lista de Créditos (`account.balance`, la parte en pesos).
 */
fun deudasParaSalir(creditos: List<CreditSummary>, tarjetas: List<CardSummary>): List<DeudaParaSalir> {
    val deCreditos = creditos.mapNotNull { c ->
        if (!c.hasMovements) return@mapNotNull null
        val enOtraMoneda = deudaEnOtraMoneda(c) || c.account.currency != "COP"
        val saldo = c.account.balance
        if (saldo <= 0L && !enOtraMoneda) return@mapNotNull null
        val t = c.terms
        DeudaParaSalir(
            id = c.account.id,
            nombre = c.account.name,
            tipo = TipoDeDeuda.CREDITO,
            saldo = saldo,
            tasaEa = t?.rateEa?.takeIf { it > 0.0 },
            sinIntereses = t?.sinIntereses == true,
            cuota = t?.installment?.takeIf { it > 0L },
            noAmortiza = (t?.insuranceMonthly ?: 0L).coerceAtLeast(0L) + (t?.otrosCargosMensuales ?: 0L).coerceAtLeast(0L),
            quienLaPaga = when {
                t == null -> null
                t.payrollDeduction -> "tu nómina"
                !t.paidBy.isNullOrBlank() -> t.paidBy.trim()
                else -> null
            },
            moneda = if (enOtraMoneda) (c.account.balancesByCurrency.keys.firstOrNull { it != "COP" } ?: c.account.currency) else "COP",
        )
    }
    val deTarjetas = tarjetas.mapNotNull { t ->
        val saldo = t.account.balancesByCurrency[t.account.currency] ?: t.account.balance
        if (saldo <= 0L) return@mapNotNull null
        DeudaParaSalir(
            id = t.account.id,
            nombre = t.account.name,
            tipo = TipoDeDeuda.TARJETA,
            saldo = saldo,
            tasaEa = t.terms?.tasaEa?.takeIf { it > 0.0 },
            cuota = t.terms?.pagoMinimo?.takeIf { it > 0L },
            moneda = t.account.currency,
        )
    }
    return deCreditos + deTarjetas
}

/** El orden de [estrategia]. Los empates se rompen con el otro criterio y, al final, por nombre. */
fun ordenarParaSalir(deudas: List<DeudaParaSalir>, estrategia: EstrategiaDeSalida): List<DeudaParaSalir> {
    val tasa = { d: DeudaParaSalir -> if (d.sinIntereses) 0.0 else d.tasaEa ?: 0.0 }
    val comparador = when (estrategia) {
        EstrategiaDeSalida.AVALANCHA -> compareByDescending(tasa).thenBy { it.saldo }
        EstrategiaDeSalida.BOLA_DE_NIEVE -> compareBy<DeudaParaSalir> { it.saldo }.thenByDescending(tasa)
    }
    return deudas.sortedWith(comparador.thenBy { it.nombre.lowercase() })
}

/**
 * Cómo sale una deuda, sin abono y con él.
 *
 * @property orden su lugar en el orden de la estrategia (1 = la primera que recibe el abono).
 * @property mesesSinAbono cuántas cuotas faltan pagando solo la cuota, o `null` si así no se termina.
 * @property mesesConAbono lo mismo con el abono, o `null` si ni así se termina en 100 años.
 * @property interesSinAbono / [interesConAbono] el interés que falta pagar en cada caso (`null` si no
 *   se termina: sería infinito).
 */
@Serializable
data class SalidaDeUnaDeuda(
    val deuda: DeudaParaSalir,
    val orden: Int,
    val mesesSinAbono: Int?,
    val mesesConAbono: Int?,
    val interesSinAbono: Long?,
    val interesConAbono: Long?,
) {
    /** Cuánto interés le ahorra el abono a esta deuda, si las dos terminan. */
    val interesAhorrado: Long? get() =
        if (interesSinAbono != null && interesConAbono != null) interesSinAbono - interesConAbono else null
}

/**
 * El plan entero.
 *
 * @property enElCalculo las deudas que entran, en el orden de la estrategia.
 * @property faltanDatos las que no entran por un dato que falta (tasa, mínimo, cuota) o por la
 *   moneda. Con su motivo en [DeudaParaSalir.porQueNoEntra].
 * @property ajenas las que no salen de tu bolsillo: aparte, sin competir por el abono.
 * @property interesAhorrado el interés que el abono ahorra **en las deudas que se terminan con y
 *   sin abono**. Las que solo se terminan con el abono no suman acá (sin abono el interés no tiene
 *   fin): van en [seTerminanSoloConAbono].
 * @property mesesHastaSalir cuándo cae la última cuota de las deudas del cálculo con el abono, o
 *   `null` si alguna no se termina.
 */
@Serializable
data class PlanDeSalida(
    val estrategia: EstrategiaDeSalida,
    val abonoMensual: Long,
    val enElCalculo: List<SalidaDeUnaDeuda>,
    val faltanDatos: List<DeudaParaSalir>,
    val ajenas: List<DeudaParaSalir>,
) {
    val interesAhorrado: Long get() = enElCalculo.sumOf { it.interesAhorrado ?: 0L }
    val seTerminanSoloConAbono: List<SalidaDeUnaDeuda>
        get() = enElCalculo.filter { it.mesesSinAbono == null && it.mesesConAbono != null }
    val mesesHastaSalir: Int?
        get() = if (enElCalculo.isEmpty() || enElCalculo.any { it.mesesConAbono == null }) null
        else enElCalculo.maxOf { it.mesesConAbono!! }
    val mesesHastaSalirSinAbono: Int?
        get() = if (enElCalculo.isEmpty() || enElCalculo.any { it.mesesSinAbono == null }) null
        else enElCalculo.maxOf { it.mesesSinAbono!! }
}

/**
 * **El plan de salida**: [deudas] separadas en las que entran, las que no tienen los datos y las
 * ajenas; las que entran, ordenadas por [estrategia] y simuladas mes a mes sin abono y con
 * [abonoMensual] extra. Ver el KDoc del archivo.
 */
fun planDeSalida(
    deudas: List<DeudaParaSalir>,
    abonoMensual: Long,
    estrategia: EstrategiaDeSalida,
): PlanDeSalida {
    val propias = deudas.filter { it.saleDeTuBolsillo }
    val ajenas = deudas.filterNot { it.saleDeTuBolsillo }
    val faltan = propias.filter { it.porQueNoEntra != null }
    val entran = ordenarParaSalir(propias.filter { it.porQueNoEntra == null }, estrategia)
    val abono = abonoMensual.coerceAtLeast(0L)

    val sin = simular(entran, 0L)
    val con = simular(entran, abono)
    return PlanDeSalida(
        estrategia = estrategia,
        abonoMensual = abono,
        enElCalculo = entran.mapIndexed { i, d ->
            // Sin abono manda la pantalla de Créditos: una deuda que [planDeUnaDeuda] da por quieta
            // (solo intereses, o que crece) «no se termina», aunque la cuenta mes a mes llegara a
            // cero en ochenta años. Dos pantallas no pueden decir cosas distintas de la misma deuda.
            val seTerminaSinAbono = planDeUnaDeuda(
                saldoDeLaDeuda = d.saldo,
                rateEa = d.tasaEa,
                cuota = d.cuota ?: 0L,
                seguroMensual = d.noAmortiza,
                otrosCargosMensuales = 0L,
                saleDeTuBolsillo = true,
                sinIntereses = d.sinIntereses,
            ).comoVa == ComoVaLaDeuda.AMORTIZA
            val mesesSin = sin[i].meses.takeIf { seTerminaSinAbono }
            SalidaDeUnaDeuda(
                deuda = d,
                orden = i + 1,
                mesesSinAbono = mesesSin,
                mesesConAbono = con[i].meses,
                interesSinAbono = sin[i].interes.takeIf { mesesSin != null },
                interesConAbono = con[i].interes.takeIf { con[i].meses != null },
            )
        },
        faltanDatos = faltan,
        ajenas = ajenas,
    )
}

private class Recorrido(var saldo: Long) {
    var meses: Int? = null
    var interes: Long = 0L
}

/**
 * Mes a mes, todas a la vez: cada deuda paga su cuota; después el abono va, en orden, a la primera
 * que siga viva, y lo que sobre de él (porque la terminó) a la siguiente, el mismo mes.
 */
private fun simular(deudas: List<DeudaParaSalir>, abonoMensual: Long): List<Recorrido> {
    val recorridos = deudas.map { Recorrido(it.saldo) }
    var mes = 0
    while (mes < MAX_MESES_PROYECTADOS && recorridos.any { it.meses == null }) {
        mes++
        deudas.forEachIndexed { i, d ->
            val r = recorridos[i]
            if (r.meses != null) return@forEachIndexed
            val interes = interesDelPeriodo(r.saldo, d.tasaMensualParaElPlan)
            r.interes += interes
            val capital = (d.cuota ?: 0L) - interes - d.noAmortiza
            r.saldo -= capital
            if (r.saldo <= 0L) {
                r.saldo = 0L
                r.meses = mes
            }
        }
        var abono = abonoMensual
        deudas.indices.forEach { i ->
            val r = recorridos[i]
            if (abono <= 0L || r.meses != null) return@forEach
            val paga = minOf(abono, r.saldo)
            r.saldo -= paga
            abono -= paga
            if (r.saldo <= 0L) {
                r.saldo = 0L
                r.meses = mes
            }
        }
    }
    return recorridos
}
