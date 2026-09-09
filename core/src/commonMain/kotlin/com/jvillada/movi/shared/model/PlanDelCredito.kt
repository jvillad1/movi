package com.jvillada.movi.shared.model

import kotlin.math.absoluteValue
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
    /**
     * El saldo con el que se calculó todo esto. Está guardado —y no solo consumido— porque el
     * resumen necesita poder decir **cuánta plata** hay en los créditos que no se terminan: contar
     * «2 créditos» sin los $304.183.376 que hay adentro es la mitad de la noticia.
     */
    val saldo: Long,
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
     * describe. Un capital de −$21.894 es exactamente el hecho que el dueño necesita ver, y
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
     * tasa registrada, sin cuota, o sin deuda).
     *
     * Solo el interés: el seguro **no** entra. Son dos cosas distintas —una es el precio de la
     * plata, la otra una póliza— y sumarlas en un solo porcentaje haría que dos créditos con el
     * mismo costo financiero se vieran distintos por tener o no seguro cargado. En los números
     * reales del dueño va del **29,8 %** (Libre inversión ·9695) al **100 %** (Crédito Mamá).
     */
    val fraccionDeInteres: Double?
        get() = when {
            comoVa == ComoVaLaDeuda.SIN_TASA || comoVa == ComoVaLaDeuda.SIN_DEUDA -> null
            cuota <= 0L -> null
            else -> interes.toDouble() / cuota.toDouble()
        }

    /**
     * **¿Esta deuda se queda afuera de cualquier fecha de fin?**
     *
     * `true` solo cuando se sabe lo suficiente para afirmarlo: con todo cargado, la proyección no
     * llega a cero. Sin tasa, sin cuota o sin deuda no se sabe si termina, y afirmar que no sería
     * inventar en la otra dirección — ver [ComoVaLaDeuda.SIN_TASA].
     */
    val noSeTermina: Boolean
        get() = comoVa.seProyecta && mesesHastaLaUltimaCuota == null
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
     * La primera versión de este archivo los separaba con **una milésima de la cuota**, y ese
     * umbral estaba mal medido de dos formas a la vez:
     *
     * - **Medía contra la cosa equivocada.** Lo que decide la clasificación es cuánto se mueve el
     *   **saldo**; la cuota solo aparece adentro del cálculo. Una milésima de la cuota de $1.300.000
     *   son $1.300 al mes, que sobre un saldo de $100.000.000 al 1,3 % mensual equivalen a una
     *   tolerancia de **$100.000 de saldo**: el 0,1 % del préstamo. Un abono de $150.000 al
     *   préstamo de su mamá —una operación que la app ya soporta— lo sacaba de esta categoría y le
     *   contestaba «te faltan 504 cuotas · la última en agosto de 2068».
     * - **Medía un solo mes.** Un mes de un préstamo a veinte años no dice si algo se mueve.
     *
     * ### El criterio: contra el saldo, y a lo largo de veinte años
     *
     * Una deuda **no se mueve** cuando, a este ritmo, en **veinte años** no habría cambiado ni el
     * **1 %** de lo que se debe hoy: `|capital| × 240 ≤ saldo / 100`, escrito sin dividir para no
     * perder pesos por el camino (ver [MESES_DE_LA_MATERIALIDAD] y [PARTE_DEL_SALDO_QUE_SE_NOTA]).
     * Sobre los dos casos reales que conviven en su base:
     *
     * | | saldo | capital/mes | en un año | contra el saldo |
     * |---|---:|---:|---:|---:|
     * | **Crédito Mamá** | $100.000.000 | **−$2** | −$24 | 0,000024 % |
     * | **Hipotecario ·2334** | $204.183.376 | **−$21.894** | −$262.728 | 0,129 % |
     *
     * Hay cuatro órdenes de magnitud entre los dos, y el umbral (0,05 % al año) cae en el medio
     * geométrico: el ·2334 lo pasa 2,6 veces y el Mamá se queda 2,1 veces por debajo **incluso
     * después de un abono de $150.000**. Es el margen más ancho que se puede dar sin dejar de
     * gritar por el ·2334, que es la alerta que este archivo vino a dar.
     *
     * El margen es **simétrico**: un capital positivo pero minúsculo tampoco es «amortiza», es la
     * misma deuda quieta vista desde el otro lado, y proyectarle una fecha daría siglos. Ver
     * [proyectarLaDeuda].
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

    /**
     * **Con tasa pero sin cuota registrada.** El interés del mes sí se sabe —salió del saldo y la
     * tasa— pero no hay con qué proyectar nada.
     *
     * Existe porque antes caía en [SIN_TASA] y la tarjeta decía «Sin tasa registrada» tres líneas
     * debajo de la tasa, que estaba ahí y se veía. Un rótulo que contradice lo que está en la
     * misma tarjeta le enseña al dueño a no creerle a la pantalla.
     */
    SIN_CUOTA,

    /**
     * **No hay deuda registrada hoy**: el saldo es cero o negativo.
     *
     * Es el paso 1 del flujo de dos pasos que crea un crédito (crearlo en $0, después registrar el
     * desembolso), así que el estado existe de verdad y va a existir seguido. Antes no tenía
     * nombre: un saldo en cero daba interés $0 → capital = la cuota entera → «amortiza» → cero
     * meses por delante → **«Ya está pagada»**, sobre la misma tarjeta que decía «Falta registrar
     * el desembolso». Dos frases opuestas a cuatro líneas de distancia.
     *
     * Con saldo negativo era peor: «Deuda en negativo — revísala» y «Ya está pagada» juntas.
     */
    SIN_DEUDA;

    /**
     * ¿De este estado se puede afirmar si la deuda termina o no? Solo de los dos que tienen todo
     * cargado. Ver [PlanDelCredito.noSeTermina].
     */
    val seProyecta: Boolean
        get() = this == AMORTIZA || this == SOLO_INTERESES || this == LA_DEUDA_CRECE
}

/**
 * Hasta dónde se proyecta antes de contestar «no se termina». 1.200 meses = **100 años**.
 *
 * No es un límite de rendimiento —el préstamo más largo del dueño se resuelve en 232 vueltas— sino
 * una guarda contra el caso patológico: un capital positivo pero minúsculo (justo afuera del margen
 * de [ComoVaLaDeuda.LA_DEUDA_CRECE]) da un horizonte de décadas, y a partir de cierto punto la
 * respuesta honesta ya no es una fecha.
 */
const val MAX_MESES_PROYECTADOS: Int = 1_200

/**
 * El horizonte contra el que se mide si una deuda se mueve: **veinte años**, no el mes que viene.
 * Ver [ComoVaLaDeuda.LA_DEUDA_CRECE].
 */
const val MESES_DE_LA_MATERIALIDAD: Int = 240

/**
 * Cuánto del **saldo** tiene que moverse en ese horizonte para que cuente: un **1 %**, o sea el
 * saldo dividido entre esto. Ver [ComoVaLaDeuda.LA_DEUDA_CRECE].
 */
const val PARTE_DEL_SALDO_QUE_SE_NOTA: Long = 100L

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
 * ¿Este préstamo debe plata que **no** está en el componente COP de su saldo?
 *
 * `account.balance` es el componente **COP** del saldo (ver `enrichWith`), así que un préstamo en
 * dólares tiene `balance = 0` con la deuda intacta. `balancesByCurrency` viene derivado del server
 * junto con el saldo, así que la pregunta se responde con lo que ya llegó. Un mapa vacío —lo que
 * manda un server viejo, o una cuenta sin eventos— responde `false` y todo se comporta como
 * siempre.
 *
 * Vive acá y no en la pantalla porque la usan las **dos** capas: la barra de progreso (que ya la
 * miraba) y [planDelCredito] (que no la heredó y por eso proyectaba sobre un saldo COP de $0).
 */
fun deudaEnOtraMoneda(credit: CreditSummary): Boolean =
    credit.account.balancesByCurrency.any { (moneda, saldo) -> moneda != "COP" && saldo != 0L }

/**
 * El plan de una deuda a partir de sus piezas sueltas. Ver [PlanDelCredito].
 *
 * @param saldoDeLaDeuda capital vigente hoy. Un saldo que no es positivo da
 *   [ComoVaLaDeuda.SIN_DEUDA] y **ninguna** proyección: no hay nada que amortizar, y contestar
 *   «cero cuotas por delante» sobre un crédito al que le falta el desembolso se leía «Ya está
 *   pagada».
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
    fun mudo(comoVa: ComoVaLaDeuda, interes: Long = 0L) = PlanDelCredito(
        saldo = saldoDeLaDeuda,
        cuota = cuota,
        interes = interes,
        seguro = seguro,
        otrosCargos = otros,
        // Sin uno de los tres insumos el capital tampoco se calcula: `cuota − seguro − otros`
        // tendría pinta de deducido y no lo está. Ver [ComoVaLaDeuda.SIN_TASA].
        capital = 0L,
        comoVa = comoVa,
        mesesHastaLaUltimaCuota = null,
        interesPorPagar = null,
        saleDeTuBolsillo = saleDeTuBolsillo,
    )

    // **Primero el saldo**: sin deuda no hay interés que estimar ni plazo que proyectar, y el
    // motivo es más informativo que «sin tasa». Ver [ComoVaLaDeuda.SIN_DEUDA].
    if (saldoDeLaDeuda <= 0L) return mudo(ComoVaLaDeuda.SIN_DEUDA)
    val sinTasa = rateEa == null || rateEa <= 0.0 || !rateEa.isFinite()
    if (sinTasa) return mudo(ComoVaLaDeuda.SIN_TASA)

    val tasaMensual = tasaMensualDeUnaEA(rateEa)
    val saldo = saldoDeLaDeuda
    val interes = interesDelPeriodo(saldo, tasaMensual)
    // Con tasa pero sin cuota el interés del mes SÍ se sabe, y decirlo no cuesta nada. Lo que no
    // hay es con qué proyectar. Ver [ComoVaLaDeuda.SIN_CUOTA].
    if (cuota <= 0L) return mudo(ComoVaLaDeuda.SIN_CUOTA, interes = interes)

    val capital = cuota - interes - seguro - otros
    // **La materialidad se mide contra el saldo y a lo largo de veinte años**, no contra la cuota
    // de un mes: en un mes cualquier deuda parece quieta, y contra la cuota un abono normal cambia
    // de categoría un préstamo de $100.000.000. Ver [ComoVaLaDeuda.LA_DEUDA_CRECE].
    val loQueSeMoveriaEnVeinteAnos = capital.absoluteValue * MESES_DE_LA_MATERIALIDAD
    val comoVa = when {
        loQueSeMoveriaEnVeinteAnos * PARTE_DEL_SALDO_QUE_SE_NOTA <= saldo -> ComoVaLaDeuda.SOLO_INTERESES
        capital > 0L -> ComoVaLaDeuda.AMORTIZA
        else -> ComoVaLaDeuda.LA_DEUDA_CRECE
    }
    val proyeccion =
        if (comoVa == ComoVaLaDeuda.AMORTIZA) proyectarLaDeuda(saldo, tasaMensual, cuota, seguro + otros) else null
    return PlanDelCredito(
        saldo = saldo,
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
 * El plan de un crédito ya cargado, o `null` cuando **no hay nada honesto que proyectar**.
 *
 * Devuelve `null` en tres casos, y los tres son «el saldo que llegó no es la deuda»:
 *
 * - **Sin términos registrados**: sin cuota ni tasa no hay nada que calcular, y la tarjeta ya dice
 *   «Sin términos registrados».
 * - **Sin un solo movimiento vivo**: el crédito se creó en $0 y todavía no se registró el
 *   desembolso. La tarjeta ya dice «Falta registrar el desembolso» (ver `progresoDeCredito`), y
 *   sumarle un plan calculado sobre ese $0 era lo que producía **«Ya está pagada»** en la misma
 *   tarjeta.
 * - **Deuda en otra moneda**: `account.balance` es el componente COP, así que un préstamo en
 *   dólares proyectaba sobre $0. `progresoDeCredito` ya tenía esta guarda; esto es heredarla.
 *
 * Un saldo en cero o negativo **con** movimientos sí produce plan —[ComoVaLaDeuda.SIN_DEUDA]—
 * porque ahí el $0 es un hecho y no un «no sé»; simplemente no dice ni interés ni fecha.
 */
fun planDelCredito(credit: CreditSummary): PlanDelCredito? {
    val terms = credit.terms ?: return null
    if (!credit.hasMovements) return null
    if (deudaEnOtraMoneda(credit)) return null
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
 * **Ninguna cifra de acá vale para toda la cartera**, y por eso cada una viene con su alcance en el
 * nombre: hay dos cortes cruzados —de quién sale la plata, y si la deuda se termina— y una cifra
 * sin alcance le hace creer al dueño que le sobra plata que no tiene. La separación entre lo que
 * sale de su bolsillo y lo que no **no se colapsa en un solo número**: las dos cifras se muestran.
 * Quedarse solo con el total le cobraría $15,8 millones al mes que no salen de su cuenta; quedarse
 * solo con lo suyo escondería que existen. Ver [saleDeTuBolsillo].
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
     * [creditosQueNoSeTerminan] y [deudaQueNoSeTermina].
     */
    val interesPorPagarPropio: Long,
    /** Lo mismo para los que paga la nómina o un tercero, y que se terminan. */
    val interesPorPagarAjeno: Long,
    /**
     * Cuándo cae la última cuota de **toda** su deuda, o `null` si no hay una fecha que dar.
     *
     * La pregunta es cuándo deja de deber, no cuándo deja de girar, así que **una deuda que no se
     * termina le gana a cualquier fecha**: mientras haya una sola, esto es `null`. Antes se tomaba
     * el máximo de las que sí terminan y se descartaban las otras, que es contestar lo contrario de
     * lo que se preguntó — con los doce créditos reales la pantalla decía «diciembre de 2045»
     * habiendo $304.183.376 que a este ritmo no se acaban nunca. En el caso extremo, con el único
     * crédito que amortiza en $0, decía «septiembre de 2026».
     */
    val mesesHastaLaUltimaCuota: Int?,
    /** Cuántos créditos no se terminan a este ritmo (no amortizan, o tardarían más de un siglo). */
    val creditosQueNoSeTerminan: Int,
    /**
     * Cuánta plata hay adentro de esos créditos. Contar «2 créditos» sin decir que son
     * $304.183.376 es la mitad de la noticia, y es la mitad que no duele.
     */
    val deudaQueNoSeTermina: Long,
    /** Cuántos créditos tienen amortización negativa: la deuda les crece sola. */
    val creditosQueCrecen: Int,
)

/** Suma los planes en el resumen de arriba. Sin planes da todo en cero, no `null`. */
fun resumirDeudas(planes: List<PlanDelCredito>): ResumenDeDeudas {
    val propios = planes.filter { it.saleDeTuBolsillo }
    val ajenos = planes.filterNot { it.saleDeTuBolsillo }
    // Sin tasa, sin cuota o sin deuda no se sabe si se termina, así que no se afirma que no: solo
    // se cuentan los que tienen todo cargado y aun así no llegan a cero. Ver [PlanDelCredito.noSeTermina].
    val noSeTerminan = planes.filter { it.noSeTermina }
    return ResumenDeDeudas(
        interesMensualPropio = propios.sumOf { it.interes },
        interesMensualAjeno = ajenos.sumOf { it.interes },
        interesPorPagarPropio = propios.sumOf { it.interesPorPagar ?: 0L },
        interesPorPagarAjeno = ajenos.sumOf { it.interesPorPagar ?: 0L },
        // Una deuda que no se termina le gana a cualquier fecha. Ver el campo.
        mesesHastaLaUltimaCuota =
            if (noSeTerminan.isNotEmpty()) null else planes.mapNotNull { it.mesesHastaLaUltimaCuota }.maxOrNull(),
        creditosQueNoSeTerminan = noSeTerminan.size,
        deudaQueNoSeTermina = noSeTerminan.sumOf { it.saldo },
        creditosQueCrecen = planes.count { it.comoVa == ComoVaLaDeuda.LA_DEUDA_CRECE },
    )
}

/**
 * El período que cae `meses` después de este. Sirve para convertir
 * [PlanDelCredito.mesesHastaLaUltimaCuota] en la fecha que el dueño lee («junio de 2030») con
 * [nombreDe], en vez de en un número de meses que hay que dividir entre doce a mano.
 */
fun PeriodoFinanciero.mas(meses: Int): PeriodoFinanciero {
    val total = (year * 12 + (month - 1)) + meses
    return PeriodoFinanciero(total / 12, total % 12 + 1)
}
