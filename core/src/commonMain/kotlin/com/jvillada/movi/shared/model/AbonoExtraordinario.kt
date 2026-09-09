package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * **«¿Y si le abono de más?»** — la única pregunta accionable que le queda al dueño después de
 * [PlanDelCredito].
 *
 * ### El problema que resuelve
 *
 * Créditos ya dice cuánto debe, cuánto de la cuota es interés y cuándo termina cada deuda. Con doce
 * créditos —del 11,27 % del Libre inversión ·9695 al 29,64 % del Crediágil ·3090— la pregunta que
 * sigue no es «cuánto debo» sino **cuál mato primero**, y esa no se contesta mirando la tasa: el
 * ahorro de un abono depende del saldo, de la tasa y de cuánto plazo queda, y los tres se mueven
 * juntos. $5.000.000 al Vehículo ·8761 ahorran $8.009.748 de interés; los mismos $5.000.000 al
 * Crediágil ·3090 no ahorran nada, porque ese crédito solo debe $507.553.
 *
 * ### Un abono único, no uno mensual
 *
 * Esto simula **un solo abono, hoy**, y no un aumento permanente de la cuota. Por dos motivos:
 *
 * - Es el caso real del dueño («me sobró plata este mes»): la plata que aparece es una prima, un
 *   reintegro o un mes sin viaje, no un compromiso nuevo.
 * - Un abono mensual sería una proyección apoyada en **dos** supuestos —que ni la tasa ni la cuota
 *   se mueven, que ya está declarado en [SUPUESTO_DE_LA_PROYECCION], **y** que el dueño va a
 *   poder poner esa plata todos los meses durante los próximos veinte años— y el resultado se vería
 *   exactamente igual de preciso que este. Sumar un supuesto sobre el futuro de sus ingresos a uno
 *   sobre el futuro de las tasas es la forma barata de que una pantalla se vea más útil y sea menos
 *   cierta.
 *
 * ### Lo que el banco hace con un abono, y que esto supone
 *
 * Un abono a capital se puede aplicar de dos formas, y **el banco elige la que uno le pida**:
 * bajando el plazo (la cuota sigue igual y se acaba antes) o bajando la cuota (el plazo sigue igual
 * y se paga menos por mes). Acá se supone **lo primero**, porque es lo que hace que la cuota
 * proyectada siga siendo la misma que está en `credit_terms`. Si el banco baja la cuota en vez del
 * plazo, ni la fecha ni el ahorro de acá valen — y eso se dice en la pantalla, no en un comentario:
 * ver `SUPUESTO_DEL_ABONO`.
 */
@Serializable
data class SimulacionDeAbono(
    /** Lo que se abonaría hoy, tal como se pidió (sin recortar al saldo: ver [sobrante]). */
    val abono: Long,
    /** El plan de hoy, sin el abono. Es el mismo que ya muestra la tarjeta. */
    val antes: PlanDelCredito,
    /**
     * El plan con el saldo ya bajado. **Es [planDeUnaDeuda] otra vez, con otro saldo**, y no una
     * fórmula paralela: la clasificación, el margen de materialidad y la iteración mes a mes son
     * literalmente los mismos, así que la simulación no puede contestar algo que la tarjeta de
     * al lado contradiga.
     */
    val despues: PlanDelCredito,
    val logro: QueLograElAbono,
    /**
     * Cuántas cuotas menos, o `null` cuando no hay dos fechas que restar — porque hoy la deuda no
     * se termina ([QueLograElAbono.LE_PONE_FECHA], [QueLograElAbono.NO_ALCANZA]) o porque el abono
     * la salda entera y hoy tampoco se terminaba.
     */
    val cuotasQueSeAhorra: Int?,
    /**
     * Cuánto interés se deja de pagar, o `null` por los mismos motivos que [cuotasQueSeAhorra].
     *
     * **Solo intereses**, igual que [PlanDelCredito.interesPorPagar]: el seguro se sigue pagando
     * mientras el crédito viva, así que acortar el plazo también lo ahorra, pero mezclarlo acá
     * daría un número más grande y menos explicable — y el seguro no es el precio de la plata, que
     * es lo que esta pantalla está comparando entre créditos.
     */
    val interesQueSeAhorra: Long?,
    /**
     * Lo que sobraría del abono después de saldar la deuda, o 0.
     *
     * Existe para no decir «te ahorras todo» sobre una plata que en parte no hacía falta: con
     * $1.000.000 sobre el Crediágil ·3090 —que debe $507.553— la deuda se salda y **sobran
     * $492.447** que no ahorraron un peso de interés.
     */
    val sobrante: Long,
)

/** Qué consigue el abono. Lo lee la pantalla para saber qué frase decir. */
@Serializable
enum class QueLograElAbono {
    /** El abono cubre el saldo entero: la deuda se acaba hoy. */
    SALDA_LA_DEUDA,

    /** Hoy se termina y con el abono se termina antes. El caso normal. */
    ACORTA_EL_PLAZO,

    /**
     * **Hoy la deuda no se termina, y con el abono sí.** Es el caso del Hipotecario ·2334 (que
     * crece $21.894 al mes) y del Crédito Mamá (cuya cuota es interés puro): un abono cambia la
     * respuesta de «nunca» a una fecha, y esa es la información más valiosa que puede dar esta
     * pantalla — la que dice que esas dos deudas no son inmóviles, solo caras.
     */
    LE_PONE_FECHA,

    /**
     * Hoy no se termina y con este abono tampoco. No es un error: en el ·2334, $1.000.000 bajan la
     * amortización negativa de $21.894 a $10.011 al mes y la deuda **sigue creciendo**. Decirlo es
     * el punto; ver [abonoMinimoParaQueSeTermine] para lo que la pantalla ofrece a continuación.
     */
    NO_ALCANZA,

    /**
     * No hay nada que simular: un abono que no es positivo, o un crédito sin tasa, sin cuota o sin
     * deuda ([ComoVaLaDeuda.seProyecta]). La pantalla ni siquiera ofrece el simulador en ese caso;
     * esto existe para que el `when` de arriba no tenga que inventar un resultado.
     */
    NO_SE_PUEDE_SIMULAR,
}

/**
 * Qué pasaría con esta deuda si hoy se le abonaran [abono] pesos de más. Ver [SimulacionDeAbono].
 *
 * Los parámetros del crédito son **los mismos y en el mismo orden** que [planDeUnaDeuda], a
 * propósito: esto es esa función llamada dos veces, y cualquier campo que se le agregue allá tiene
 * que llegar acá o la simulación empieza a proyectar sobre un crédito distinto del que muestra la
 * tarjeta.
 */
fun simularAbonoUnico(
    saldoDeLaDeuda: Long,
    rateEa: Double?,
    cuota: Long,
    seguroMensual: Long?,
    otrosCargosMensuales: Long?,
    saleDeTuBolsillo: Boolean,
    abono: Long,
): SimulacionDeAbono {
    val antes = planDeUnaDeuda(saldoDeLaDeuda, rateEa, cuota, seguroMensual, otrosCargosMensuales, saleDeTuBolsillo)
    // El abono no puede llevarse la deuda a negativo: lo que pase del saldo no ahorra intereses,
    // sobra. Ver [SimulacionDeAbono.sobrante].
    val aplicado = abono.coerceIn(0L, saldoDeLaDeuda.coerceAtLeast(0L))
    val despues = planDeUnaDeuda(
        saldoDeLaDeuda - aplicado,
        rateEa,
        cuota,
        seguroMensual,
        otrosCargosMensuales,
        saleDeTuBolsillo,
    )
    val logro = when {
        abono <= 0L || !antes.comoVa.seProyecta -> QueLograElAbono.NO_SE_PUEDE_SIMULAR
        aplicado >= saldoDeLaDeuda -> QueLograElAbono.SALDA_LA_DEUDA
        despues.mesesHastaLaUltimaCuota == null -> QueLograElAbono.NO_ALCANZA
        antes.mesesHastaLaUltimaCuota == null -> QueLograElAbono.LE_PONE_FECHA
        else -> QueLograElAbono.ACORTA_EL_PLAZO
    }
    // Restar dos fechas solo tiene sentido cuando hay dos. Cuando hoy la deuda no se termina, el
    // ahorro no es «grande»: no es un número, y ponerle uno sería la mentira más fácil de esta
    // pantalla. Con la deuda saldada el ahorro sí es todo lo que faltaba, cuando eso era finito.
    val faltabanAntes = antes.mesesHastaLaUltimaCuota
    val interesDeAntes = antes.interesPorPagar
    val cuotasQueSeAhorra: Int?
    val interesQueSeAhorra: Long?
    when (logro) {
        QueLograElAbono.SALDA_LA_DEUDA -> {
            cuotasQueSeAhorra = faltabanAntes
            interesQueSeAhorra = interesDeAntes
        }
        QueLograElAbono.ACORTA_EL_PLAZO -> {
            cuotasQueSeAhorra = faltabanAntes!! - despues.mesesHastaLaUltimaCuota!!
            interesQueSeAhorra = interesDeAntes!! - despues.interesPorPagar!!
        }
        else -> {
            cuotasQueSeAhorra = null
            interesQueSeAhorra = null
        }
    }
    return SimulacionDeAbono(
        abono = abono,
        antes = antes,
        despues = despues,
        logro = logro,
        cuotasQueSeAhorra = cuotasQueSeAhorra,
        interesQueSeAhorra = interesQueSeAhorra,
        sobrante = (abono - aplicado).coerceAtLeast(0L),
    )
}

/** La simulación de un crédito ya cargado, o `null` por los mismos motivos que [planDelCredito]. */
fun simularAbonoUnico(credit: CreditSummary, abono: Long): SimulacionDeAbono? {
    val terms = credit.terms ?: return null
    if (!credit.hasMovements) return null
    if (deudaEnOtraMoneda(credit)) return null
    return simularAbonoUnico(
        saldoDeLaDeuda = credit.account.balance,
        rateEa = terms.rateEa,
        cuota = terms.installment,
        seguroMensual = terms.insuranceMonthly,
        otrosCargosMensuales = terms.otrosCargosMensuales,
        saleDeTuBolsillo = saleDeTuBolsillo(terms),
        abono = abono,
    )
}

/**
 * **Cuánto haría falta abonar, hoy, para que esta deuda deje de ser eterna.** `null` cuando ya se
 * termina sin abono, o cuando ningún abono alcanza.
 *
 * ### Por qué es la cifra que sigue
 *
 * Sin esto, un dueño que simula $1.000.000 sobre el Hipotecario ·2334 lee «la deuda sigue
 * creciendo» y se queda sin saber si le faltó poco o le faltó todo. Le faltaba poco: con
 * **$2.549.401** —el 1,2 % de esa deuda— el ·2334 pasa de crecer para siempre a terminarse. En el
 * Crédito Mamá bastan **$319.625**.
 *
 * ### Y por qué la fecha que sale de ahí hay que mostrarla al lado
 *
 * Porque el mínimo es exactamente eso, el mínimo, y da fechas absurdas: los $2.549.401 del ·2334
 * lo terminan en **479 cuotas** (cuarenta años) y los $319.625 del Mamá en **445**. La cifra no
 * miente —esa deuda sí pasa a tener final— pero leída sola invita a creer que un abono chico la
 * resuelve. La pantalla muestra siempre las dos juntas, y entonces dice lo que de verdad pasa:
 * estas dos deudas no se mueven con abonos chicos.
 *
 * ### Búsqueda binaria contra [planDeUnaDeuda], no una fórmula
 *
 * Se podría despejar: el capital se vuelve positivo cuando el interés baja de `cuota − seguro −
 * otros`, y de ahí sale el saldo. Pero «que la deuda se termine» no es solo `capital > 0` —está
 * además el margen de materialidad de [ComoVaLaDeuda.LA_DEUDA_CRECE] y el techo de
 * [MAX_MESES_PROYECTADOS]—, así que una fórmula tendría que reimplementar la clasificación y
 * podría contestar un monto con el que la tarjeta de al lado sigue diciendo «no se termina». Acá se
 * pregunta **por la misma función que decide**, así que eso es imposible por construcción.
 *
 * Es monótono y por eso la búsqueda es válida: a más abono, menos saldo; a menos saldo, menos
 * interés y más capital; y una deuda que se termina con un abono se termina con uno más grande.
 * Son ~35 vueltas para cualquier deuda de este país.
 */
fun abonoMinimoParaQueSeTermine(
    saldoDeLaDeuda: Long,
    rateEa: Double?,
    cuota: Long,
    seguroMensual: Long?,
    otrosCargosMensuales: Long?,
): Long? {
    fun seTermina(abono: Long): Boolean =
        planDeUnaDeuda(
            saldoDeLaDeuda - abono,
            rateEa,
            cuota,
            seguroMensual,
            otrosCargosMensuales,
            // Da igual quién pague: no entra en la aritmética, solo rotula el resultado.
            saleDeTuBolsillo = true,
        ).mesesHastaLaUltimaCuota != null

    val hoy = planDeUnaDeuda(saldoDeLaDeuda, rateEa, cuota, seguroMensual, otrosCargosMensuales, true)
    // Ya se termina, o no hay nada cargado con qué preguntarlo. En los dos casos la pregunta no
    // aplica, y contestar un monto sería contestar otra cosa.
    if (!hoy.comoVa.seProyecta || hoy.mesesHastaLaUltimaCuota != null) return null
    // Saldar la deuda entera no cuenta como «que se termine»: eso no es abonar, es pagarla. Y con
    // $1 de saldo, si la cuota no cubre ni el seguro, no hay abono que sirva.
    if (saldoDeLaDeuda <= 1L || !seTermina(saldoDeLaDeuda - 1L)) return null

    var bajo = 1L
    var alto = saldoDeLaDeuda - 1L
    while (bajo < alto) {
        val medio = bajo + (alto - bajo) / 2
        if (seTermina(medio)) alto = medio else bajo = medio + 1
    }
    return bajo
}

/** El mínimo de un crédito ya cargado, o `null` por los mismos motivos que [planDelCredito]. */
fun abonoMinimoParaQueSeTermine(credit: CreditSummary): Long? {
    val terms = credit.terms ?: return null
    if (!credit.hasMovements) return null
    if (deudaEnOtraMoneda(credit)) return null
    return abonoMinimoParaQueSeTermine(
        saldoDeLaDeuda = credit.account.balance,
        rateEa = terms.rateEa,
        cuota = terms.installment,
        seguroMensual = terms.insuranceMonthly,
        otrosCargosMensuales = terms.otrosCargosMensuales,
    )
}
