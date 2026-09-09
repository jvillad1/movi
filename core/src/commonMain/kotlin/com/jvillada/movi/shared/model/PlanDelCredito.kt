package com.jvillada.movi.shared.model

import kotlin.math.round
import kotlinx.serialization.Serializable

/**
 * **Qué le queda por delante a una deuda**: cuánto de la cuota es interés, si la deuda de verdad
 * baja, y cuándo se termina.
 *
 * ### El problema que resuelve
 *
 * La pantalla de Créditos mostraba saldo, cuota, tasa, plazo y una barra de «% pagado». Con eso el
 * dueño no podía contestar ninguna de las tres preguntas que importan —cuánto de lo que paga es
 * alquiler de la plata, si hay algún crédito en el que va para atrás, y cuándo termina cada uno—
 * aunque Movi ya tuviera **todos los insumos cargados**: [desglosarCuota] calcula interés, seguro
 * y capital cada vez que se registra un pago, usa el resultado para mover el saldo, y lo tira.
 *
 * Esto es ese mismo desglose convertido en **propiedad del crédito**, no en subproducto de un pago,
 * más la proyección hacia adelante.
 *
 * ### Por qué vive en `:core` y no en la pantalla
 *
 * Por lo mismo que [desglosarCuota], que está en el archivo de al lado: el server y los tres
 * clientes tienen que ver el mismo número. Una regla sobre plata duplicada en dos pantallas ya
 * sobrevivió tres rondas de arreglos en este proyecto.
 *
 * ### Lo que esto NO es
 *
 * Una proyección **a cuota y tasa constantes**. En Colombia la tasa de un hipotecario se
 * recalcula, y ninguno de los créditos del dueño promete que no. Por eso la fecha que sale de acá
 * es «si nada cambia», y la pantalla lo dice con una frase entera y no con letra chica: inventar
 * precisión es la forma más cara de mentir con números que se ven bien.
 */
@Serializable
data class PlanDelCredito(
    /** La cuota mensual pactada, tal como está en `credit_terms.installment`. */
    val cuota: Long,
    /**
     * Interés del próximo período, estimado igual que en [desglosarCuota]:
     * `saldo × tasaMensualDeUnaEA(rateEa)`, redondeado.
     *
     * Es **el saldo de hoy**, con la misma limitación que documenta [desglosarCuota]: la deuda de
     * una cuenta en Movi no tiene fecha, es la suma de sus eventos vivos. Acá esa limitación
     * molesta menos —la pregunta es «¿cómo viene esto ahora?», no «¿cuánto se causó en julio?»—
     * pero sigue siendo el saldo de hoy y no el de la fecha de corte del banco.
     */
    val interes: Long,
    /** Seguro de vida deudor: plata dentro de la cuota que no amortiza. */
    val seguro: Long,
    /**
     * Los otros cargos fijos de la cuota ([CreditTerms.otrosCargosMensuales]), que tampoco
     * amortizan. En el Vehículo ·8761 del dueño son los $25.000 de «otros conceptos» que el Banco
     * de Occidente cobra adentro de la cuota y que ningún otro campo puede nombrar sin mentir.
     */
    val otrosCargos: Long,
    /**
     * Lo que la cuota le baja a la deuda cada mes: `cuota − interés − seguro − otros cargos`.
     *
     * **Firmado, y ese es el punto.** [desglosarCuota] lo clampa a cero porque está escribiendo un
     * movimiento y una cuota no puede *subir* la deuda por esa puerta. Acá no se escribe nada: se
     * describe. Un capital de −$21.629 es exactamente el hecho que el dueño necesita ver, y
     * clamparlo a cero lo escondería igual que lo escondía la barra en 0 %.
     */
    val capital: Long,
    val comoVa: ComoVaLaDeuda,
    /**
     * Cuántas cuotas faltan a este ritmo, o `null` si la deuda no se termina —porque no baja, o
     * porque tardaría más que [MAX_MESES_PROYECTADOS].
     *
     * Se cuenta **iterando mes a mes con la misma aritmética Long de [desglosarCuota]**, no con la
     * fórmula cerrada de una anualidad: así la proyección es literalmente «qué pasa si registro
     * esta cuota todos los meses», que es lo que la app hace de verdad, y no una función distinta
     * que puede divergir del comportamiento real por redondeo.
     */
    val mesesHastaLaUltimaCuota: Int?,
    /**
     * Los intereses que faltan por pagar hasta la última cuota, o `null` si no se termina.
     *
     * **Solo intereses: el seguro no está adentro.** Es la cifra que responde «cuánto me va a
     * costar esta plata además de devolverla», y el seguro es un cargo aparte que el dueño ya ve
     * declarado en las condiciones. Meterlos juntos daría un número más grande y menos explicable.
     */
    val interesPorPagar: Long?,
    /**
     * ¿Esta cuota sale de la cuenta del dueño?
     *
     * `false` para una libranza (la retiene el empleador) y para una cuota que paga un tercero
     * (las dos hipotecas que gira Skandia). Ver [saleDeTuBolsillo] para el criterio y su
     * precedente.
     */
    val saleDeTuBolsillo: Boolean,
) {
    /**
     * Qué fracción de la cuota es **interés**, de 0 a 1, o `null` cuando no se puede saber (sin
     * tasa registrada, o sin cuota).
     *
     * Solo el interés: el seguro **no** entra. Son dos cosas distintas —una es el precio de la
     * plata, la otra una póliza— y sumarlas en un solo porcentaje haría que dos créditos con el
     * mismo costo financiero se vieran distintos por tener o no seguro cargado. En los números
     * reales del dueño va del **29,8 %** (Libre inversión ·9695) al **100 %** (Crédito Mamá).
     */
    val fraccionDeInteres: Double?
        get() = if (comoVa == ComoVaLaDeuda.SIN_TASA || cuota <= 0L) null else interes.toDouble() / cuota.toDouble()
}

/** Cómo le va a la deuda con la cuota que se está pagando. Lo lee la pantalla para saber qué decir. */
@Serializable
enum class ComoVaLaDeuda {
    /** La cuota cubre interés y seguro y todavía le baja algo a la deuda. El caso normal. */
    AMORTIZA,

    /**
     * **La cuota se va entera en intereses: la deuda se queda donde está.** No es una alerta, es
     * una forma de préstamo — y en los datos del dueño es un acuerdo explícito, no un accidente.
     *
     * El **Crédito Mamá** es exactamente esto: $100.000.000 al 16,7652 % E.A. con cuota de
     * $1.300.000, y esa tasa está calibrada a cuatro decimales *para que* el interés dé la cuota.
     * Su nota lo dice con todas las letras: «sin plazo ni condiciones pactadas. La cuota no baja
     * la deuda: es lo que le das por tener la plata». Es un préstamo familiar sin plazo; tratarlo
     * como una emergencia sería avisarle de algo que él decidió.
     *
     * Ver [ComoVaLaDeuda.LA_DEUDA_CRECE] para dónde queda el límite entre esto y una alerta de
     * verdad, que es la decisión delicada de todo este archivo.
     */
    SOLO_INTERESES,

    /**
     * **Amortización negativa: la cuota no alcanza y la deuda crece sola.** Esto sí es la alerta.
     *
     * ### Dónde está el límite con [SOLO_INTERESES], y por qué ahí
     *
     * Los dos casos comparten la aritmética —`capital ≤ 0`— así que el signo solo no los separa.
     * En los datos reales del dueño los dos existen a la vez:
     *
     * | | cuota | capital/mes | contra la cuota |
     * |---|---:|---:|---:|
     * | **Crédito Mamá** | $1.300.000 | **−$2** | 0,00015 % |
     * | **Hipotecario ·2334** | $2.613.714 | **−$21.629** | 0,83 % |
     *
     * El límite es **una milésima de la cuota** ([MARGEN_DE_LA_CUOTA]): por debajo de eso la
     * cuota ni sube ni baja la deuda de una forma que el dueño pueda notar, y decir «tu deuda
     * crece sola» sobre $2 al mes sería una alarma inventada por el redondeo de un decimal de la
     * tasa. Por encima es un hecho: al ·2334 le faltan **$21.629 todos los meses**, ocho veces
     * ese margen y creciendo, y su propia nota ya lo admitía («la cuota no alcanza a cubrir
     * intereses más seguros, así que la deuda crece sola») donde nadie lo leía.
     *
     * Es un margen **relativo y no una cifra en pesos** a propósito: una cifra fija tendría que
     * elegir una moneda y una escala, y el Crediágil ·3090 —cuota de $26.485— vive cuatro órdenes
     * de magnitud por debajo de la hipoteca. Contra la cuota, los dos se miden igual.
     *
     * El margen es **simétrico**: un capital de +$370 sobre una cuota de $1.300.000 tampoco es
     * «amortiza», es la misma deuda quieta vista desde el otro lado, y proyectarle una fecha daría
     * 270.000 meses. Ver [proyectarLaDeuda].
     */
    LA_DEUDA_CRECE,

    /**
     * **Sin tasa registrada** (sin condiciones, o con `rateEa` en 0): no hay nada que separar y no
     * se inventa.
     *
     * Misma postura que [MotivoDelDesglose.SIN_TASA], y por el mismo motivo, con el signo
     * invertido: allá el riesgo era inventar un interés plausible, acá sería inventar que **no hay**
     * interés y proyectar `saldo / cuota` meses. El Crédito Techo Gardenera del dueño está así
     * (tasa 0, un solo pago de $10.000.000), y es la diferencia entre «no cobra intereses» y «no
     * sabemos cuánto cobra».
     */
    SIN_TASA,
}

/**
 * Hasta dónde se proyecta antes de contestar «no se termina». 1.200 meses = **100 años**.
 *
 * No es un límite de rendimiento —el préstamo más largo del dueño se resuelve en 232 vueltas— sino
 * una guarda contra el caso patológico: un capital positivo pero minúsculo (justo afuera de
 * [MARGEN_DE_LA_CUOTA]) da un horizonte de décadas, y a partir de cierto punto la respuesta honesta
 * ya no es una fecha.
 */
const val MAX_MESES_PROYECTADOS: Int = 1_200

/**
 * El divisor del margen que separa «la deuda no se mueve» de «la deuda crece»: la milésima parte
 * de la cuota. Ver [ComoVaLaDeuda.LA_DEUDA_CRECE] para el porqué del número.
 */
const val MARGEN_DE_LA_CUOTA: Long = 1_000L

/**
 * **¿La cuota de este crédito sale de la cuenta del dueño?**
 *
 * `false` en dos casos, que son los mismos dos que [isCashFlow] ya deja fuera del mes por
 * categoría ([PAYROLL_DEDUCTION_CATEGORY] y [THIRD_PARTY_PAYMENT_CATEGORY]):
 *
 * - **Libranza** ([CreditTerms.payrollDeduction]): la retiene el empleador antes de depositar el
 *   sueldo. El salario que llega a la cuenta ya viene neto.
 * - **La paga otro** ([CreditTerms.paidBy]): las dos hipotecas del dueño las gira su pensión
 *   voluntaria de Skandia.
 *
 * Acá se decide sobre las **condiciones** y allá sobre un movimiento ya escrito, así que no puede
 * ser la misma función; es la misma regla mirada desde el contrato en vez de desde la fila. Cuatro
 * de los doce créditos del dueño caen en alguno de los dos, y sin esta distinción la pantalla le
 * cobraría a su bolsillo intereses que no salen de ahí.
 *
 * **Lo que NO cambia**, igual que en [THIRD_PARTY_PAYMENT_CATEGORY]: la deuda **es del dueño** y
 * cuenta entera en su deuda total. Quién paga la cuota no cambia de quién es el pasivo. Esto
 * separa el costo mensual, no el balance.
 */
fun saleDeTuBolsillo(terms: CreditTerms): Boolean =
    !terms.payrollDeduction && terms.paidBy.isNullOrBlank()

/**
 * El plan de una deuda a partir de sus piezas sueltas. Ver [PlanDelCredito].
 *
 * @param saldoDeLaDeuda capital vigente hoy. Un saldo que no es positivo (deuda pagada, o pagada
 *   de más) no causa intereses: da un plan que amortiza con cero meses por delante.
 */
fun planDeUnaDeuda(
    saldoDeLaDeuda: Long,
    rateEa: Double?,
    cuota: Long,
    seguroMensual: Long?,
    /**
     * Los otros cargos fijos de la cuota. **Sin valor por defecto a propósito**, igual que en
     * [desglosarCuota] y por el mismo motivo: es plata que decide cuánto baja la deuda, y un
     * default la habría dejado pasar en silencio en el próximo call site que alguien agregue.
     */
    otrosCargosMensuales: Long?,
    saleDeTuBolsillo: Boolean,
): PlanDelCredito {
    val seguro = (seguroMensual ?: 0L).coerceAtLeast(0L)
    val otros = (otrosCargosMensuales ?: 0L).coerceAtLeast(0L)
    val sinTasa = rateEa == null || rateEa <= 0.0 || !rateEa.isFinite()
    if (sinTasa || cuota <= 0L) {
        return PlanDelCredito(
            cuota = cuota,
            interes = 0L,
            seguro = seguro,
            otrosCargos = otros,
            // Sin tasa el capital tampoco se calcula: `cuota − seguro − otros` tendría pinta de
            // deducido y no lo está. Ver [ComoVaLaDeuda.SIN_TASA].
            capital = 0L,
            comoVa = ComoVaLaDeuda.SIN_TASA,
            mesesHastaLaUltimaCuota = null,
            interesPorPagar = null,
            saleDeTuBolsillo = saleDeTuBolsillo,
        )
    }
    val tasaMensual = tasaMensualDeUnaEA(rateEa)
    val saldo = saldoDeLaDeuda.coerceAtLeast(0L)
    val interes = interesDelPeriodo(saldo, tasaMensual)
    val capital = cuota - interes - seguro - otros
    val margen = cuota / MARGEN_DE_LA_CUOTA
    val comoVa = when {
        capital > margen -> ComoVaLaDeuda.AMORTIZA
        capital < -margen -> ComoVaLaDeuda.LA_DEUDA_CRECE
        else -> ComoVaLaDeuda.SOLO_INTERESES
    }
    val proyeccion =
        if (comoVa == ComoVaLaDeuda.AMORTIZA) proyectarLaDeuda(saldo, tasaMensual, cuota, seguro + otros) else null
    return PlanDelCredito(
        cuota = cuota,
        interes = interes,
        seguro = seguro,
        otrosCargos = otros,
        capital = capital,
        comoVa = comoVa,
        mesesHastaLaUltimaCuota = proyeccion?.meses,
        interesPorPagar = proyeccion?.interes,
        saleDeTuBolsillo = saleDeTuBolsillo,
    )
}

/**
 * El plan de un crédito ya cargado, o `null` si todavía no tiene condiciones registradas —sin
 * cuota ni tasa no hay nada que proyectar, y la tarjeta ya dice «Sin términos registrados».
 */
fun planDelCredito(credit: CreditSummary): PlanDelCredito? {
    val terms = credit.terms ?: return null
    return planDeUnaDeuda(
        saldoDeLaDeuda = credit.account.balance,
        rateEa = terms.rateEa,
        cuota = terms.installment,
        seguroMensual = terms.insuranceMonthly,
        otrosCargosMensuales = terms.otrosCargosMensuales,
        saleDeTuBolsillo = saleDeTuBolsillo(terms),
    )
}

/**
 * El interés de un mes, con la **misma aritmética que [desglosarCuota]**: el único `Double` es la
 * tasa, se redondea a Long apenas se calcula, y todo lo demás es Long. Está factorizado para que
 * la proyección de acá no pueda desviarse por redondeo de lo que el pago real va a hacer mes a mes.
 */
private fun interesDelPeriodo(saldo: Long, tasaMensual: Double): Long =
    round(saldo.toDouble() * tasaMensual).toLong().coerceAtLeast(0L)

private data class Proyeccion(val meses: Int, val interes: Long)

/**
 * Cuántas cuotas faltan y cuánto interés queda, iterando mes a mes hasta que el saldo llega a
 * cero. `null` si la deuda deja de amortizar en el camino o si no se termina en
 * [MAX_MESES_PROYECTADOS].
 *
 * Mes a mes y no con la fórmula de una anualidad: es lo que la app hace de verdad cada vez que se
 * registra una cuota, así que la proyección y la realidad no pueden divergir por redondeo. La
 * hipoteca más larga del dueño se resuelve en 232 vueltas.
 */
private fun proyectarLaDeuda(saldo: Long, tasaMensual: Double, cuota: Long, loQueNoAmortiza: Long): Proyeccion? {
    var restante = saldo
    var meses = 0
    var intereses = 0L
    while (restante > 0L && meses < MAX_MESES_PROYECTADOS) {
        val interes = interesDelPeriodo(restante, tasaMensual)
        val capital = cuota - interes - loQueNoAmortiza
        // Defensa, no caso esperado: quien llama ya comprobó que amortiza HOY, y como el saldo solo
        // baja el interés solo baja con él. Si algún día deja de ser cierto, se contesta «no se
        // termina» en vez de girar mil doscientas veces.
        if (capital <= 0L) return null
        intereses += interes
        restante -= capital
        meses++
    }
    return if (restante > 0L) null else Proyeccion(meses, intereses)
}

/**
 * Lo que la pantalla de Créditos dice **arriba**, sobre todas las deudas juntas.
 *
 * La separación entre lo que sale del bolsillo del dueño y lo que no **no se colapsa en un solo
 * número**: las dos cifras se muestran. Quedarse solo con el total le cobraría $15,8 millones al
 * mes que no salen de su cuenta; quedarse solo con lo suyo escondería que existen. Ver
 * [saleDeTuBolsillo].
 */
@Serializable
data class ResumenDeDeudas(
    /** Interés mensual de los créditos cuya cuota sale de la cuenta del dueño. */
    val interesMensualPropio: Long,
    /** Interés mensual de los que paga la nómina o un tercero. */
    val interesMensualAjeno: Long,
    /**
     * Intereses que faltan por pagar en los créditos propios **que se terminan**. Los que no se
     * terminan no aportan una cifra —serían infinitos— y se cuentan aparte en
     * [creditosQueNoSeTerminan].
     */
    val interesPorPagarPropio: Long,
    /**
     * Cuántos meses faltan para la última cuota de la deuda más larga, contando **todas** las
     * deudas del dueño y no solo las que paga él: la pregunta es cuándo deja de deber, no cuándo
     * deja de girar. `null` si ninguna se termina.
     */
    val mesesHastaLaUltimaCuota: Int?,
    /** Cuántos créditos no se terminan a este ritmo (no amortizan, o tardarían más de un siglo). */
    val creditosQueNoSeTerminan: Int,
    /** Cuántos créditos tienen amortización negativa: la deuda les crece sola. */
    val creditosQueCrecen: Int,
)

/** Suma los planes en el resumen de arriba. Sin planes da todo en cero, no `null`. */
fun resumirDeudas(planes: List<PlanDelCredito>): ResumenDeDeudas = ResumenDeDeudas(
    interesMensualPropio = planes.filter { it.saleDeTuBolsillo }.sumOf { it.interes },
    interesMensualAjeno = planes.filterNot { it.saleDeTuBolsillo }.sumOf { it.interes },
    interesPorPagarPropio = planes.filter { it.saleDeTuBolsillo }.sumOf { it.interesPorPagar ?: 0L },
    mesesHastaLaUltimaCuota = planes.mapNotNull { it.mesesHastaLaUltimaCuota }.maxOrNull(),
    // Sin tasa no se sabe si se termina, así que no se afirma que no: solo se cuentan los que
    // tienen todo cargado y aun así no llegan a cero.
    creditosQueNoSeTerminan = planes.count { it.comoVa != ComoVaLaDeuda.SIN_TASA && it.mesesHastaLaUltimaCuota == null },
    creditosQueCrecen = planes.count { it.comoVa == ComoVaLaDeuda.LA_DEUDA_CRECE },
)

/**
 * El período que cae `meses` después de este. Sirve para convertir
 * [PlanDelCredito.mesesHastaLaUltimaCuota] en la fecha que el dueño lee («enero de 2046») con
 * [nombreDe], en vez de en un número de meses que hay que dividir entre doce a mano.
 */
fun PeriodoFinanciero.mas(meses: Int): PeriodoFinanciero {
    val total = (year * 12 + (month - 1)) + meses
    return PeriodoFinanciero(total / 12, total % 12 + 1)
}
