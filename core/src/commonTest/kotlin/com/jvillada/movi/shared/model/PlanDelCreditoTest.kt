package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Las tres preguntas que la pantalla de Créditos no podía contestar**, sobre los doce créditos
 * reales del dueño.
 *
 * Los saldos, tasas, cuotas y seguros de acá salen de `credit_terms` y de la suma de los eventos
 * vivos de cada cuenta LOAN al 2026-09-08. No son cifras de ejemplo: son las que hacían que la
 * barra de «% pagado» dijera 0 % en cuatro créditos, dos de ellos porque la deuda **crecía**.
 */
class PlanDelCreditoTest {

    // ---------------------------------------------------------------- cuánto de la cuota es interés

    /**
     * El extremo bajo de su cartera. Es el crédito donde más plata de la cuota le queda a él.
     */
    @Test
    fun `el libre inversion 9695 paga 29,8 por ciento de la cuota en intereses`() {
        val plan = planDeUnaDeuda(
            saldoDeLaDeuda = 40_104_518L,
            rateEa = 11.27,
            cuota = 1_204_064L,
            seguroMensual = 124_800L,
            otrosCargosMensuales = null,
            saleDeTuBolsillo = true,
        )

        assertEquals(358_488L, plan.interes)
        assertEquals(124_800L, plan.seguro)
        assertEquals(720_776L, plan.capital, "cuota − interés − seguro")
        assertEquals(ComoVaLaDeuda.AMORTIZA, plan.comoVa)
        val fraccion = assertNotNull(plan.fraccionDeInteres)
        assertEquals(29.8, kotlin.math.round(fraccion * 1000) / 10.0, "29,8 % de la cuota es interés")
    }

    /**
     * El seguro **no** entra en el porcentaje de interés, y se ve justo en este crédito: con los
     * $124.800 adentro daría 40,1 % en vez de 29,8 %. Son dos cosas distintas —el precio de la
     * plata y una póliza— y mezclarlas haría que dos créditos con el mismo costo financiero se
     * vieran distintos por tener o no seguro cargado.
     */
    @Test
    fun `el seguro no infla el porcentaje de interes`() {
        val conSeguro = planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, 124_800L, otrosCargosMensuales = null, saleDeTuBolsillo = true)
        val sinSeguro = planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, null, otrosCargosMensuales = null, saleDeTuBolsillo = true)

        assertEquals(sinSeguro.fraccionDeInteres, conSeguro.fraccionDeInteres)
        // Pero el capital SÍ cambia: el seguro no amortiza.
        assertEquals(124_800L, sinSeguro.capital - conSeguro.capital)
    }

    /**
     * El desglose de acá tiene que dar **lo mismo** que [desglosarCuota], que es el que de verdad
     * escribe la pata de la deuda cuando se registra el pago. Si divergen, la pantalla anuncia un
     * abono a capital distinto del que la app va a hacer.
     */
    @Test
    fun `el interes coincide con el que desglosarCuota le cobra a la cuota registrada`() {
        // El Vehículo ·8761, que es el único con las cuatro partes: capital, interés, seguro de
        // vida y los $25.000 de «otros conceptos» del Banco de Occidente.
        val saldo = 177_052_715L
        val plan = planDeUnaDeuda(saldo, 18.16, 4_101_123L, 89_100L, 25_000L, saleDeTuBolsillo = true)
        val desglose = desglosarCuota(4_101_123L, AccountType.LOAN, saldo, 18.16, 89_100L, 25_000L)

        assertEquals(desglose.interes, plan.interes)
        assertEquals(desglose.seguro, plan.seguro)
        assertEquals(desglose.otrosCargos, plan.otrosCargos)
        assertEquals(desglose.capital, plan.capital)
        assertEquals(2_479_256L, plan.interes)
        assertEquals(1_507_767L, plan.capital, "los \$25.000 de otros cargos tampoco amortizan")
    }

    // ------------------------------------------------------- la alerta, y el crédito que no lo es

    /**
     * **El aviso más caro que la app no daba.** La cuota del Hipotecario ·2334 no alcanza a cubrir
     * intereses más seguros: le faltan $21.894 todos los meses, y la barra decía «0 % pagado»,
     * que se lee como «todavía no empezaste» cuando en realidad es «vas para atrás».
     */
    @Test
    fun `el hipotecario 2334 no amortiza, la deuda le crece sola`() {
        val plan = planDeUnaDeuda(
            saldoDeLaDeuda = 204_183_376L,
            rateEa = 15.23,
            cuota = 2_613_714L,
            seguroMensual = 209_219L,
            otrosCargosMensuales = null,
            saleDeTuBolsillo = false, // la gira Skandia
        )

        assertEquals(ComoVaLaDeuda.LA_DEUDA_CRECE, plan.comoVa)
        assertEquals(-21_894L, plan.capital, "el capital va firmado: negativo es que la deuda sube")
        assertNull(plan.mesesHastaLaUltimaCuota, "no hay fecha que inventar: a este ritmo no se termina")
        assertNull(plan.interesPorPagar)
    }

    /**
     * **Y el que NO es una alerta, aunque la aritmética se le parezca.** El Crédito Mamá tiene la
     * tasa calibrada a cuatro decimales para que el interés dé exactamente la cuota: es un
     * préstamo familiar sin plazo pactado, y eso es el acuerdo, no un accidente.
     *
     * Le falta **$2** al mes sobre un saldo de $100.000.000. Tratarlo igual que al ·2334 sería
     * avisarle de algo que él decidió, con la misma tinta roja que el crédito que sí se le está
     * yendo de las manos.
     */
    @Test
    fun `el credito mama es solo intereses, no una alerta`() {
        val plan = planDeUnaDeuda(
            saldoDeLaDeuda = 100_000_000L,
            rateEa = 16.7652,
            cuota = 1_300_000L,
            seguroMensual = null,
            otrosCargosMensuales = null,
            saleDeTuBolsillo = true,
        )

        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, plan.comoVa)
        assertEquals(-2L, plan.capital, "le falta \$2 al mes: eso es la deuda quieta, no creciendo")
        assertNull(plan.mesesHastaLaUltimaCuota, "sigue sin terminarse — eso sí hay que decirlo")
        assertEquals(100, ((plan.fraccionDeInteres ?: 0.0) * 100).toInt(), "la cuota entera es interés")
    }

    /**
     * Los dos casos de arriba, uno al lado del otro, que es la comparación que decide dónde va el
     * límite. Comparten el signo del capital y **no** son lo mismo: $24 contra $262.728 al año.
     */
    @Test
    fun `dos capitales negativos y solo uno es alerta`() {
        val mama = planDeUnaDeuda(100_000_000L, 16.7652, 1_300_000L, null, otrosCargosMensuales = null, saleDeTuBolsillo = true)
        val hipotecario = planDeUnaDeuda(204_183_376L, 15.23, 2_613_714L, 209_219L, otrosCargosMensuales = null, saleDeTuBolsillo = false)

        assertTrue(mama.capital < 0L && hipotecario.capital < 0L, "los dos tienen capital negativo")
        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, mama.comoVa)
        assertEquals(ComoVaLaDeuda.LA_DEUDA_CRECE, hipotecario.comoVa)
    }

    /**
     * **El defecto que este archivo vino a arreglar en la segunda vuelta.**
     *
     * El margen viejo era una milésima de la **cuota**, pero lo que decide la clasificación es
     * cuánto se mueve el **saldo**: sobre el Crédito Mamá esos $1.300 al mes equivalían a una
     * tolerancia de $100.000 de saldo —el 0,1 % del préstamo—, así que **un abono de $150.000 lo
     * sacaba de categoría** y la app le contestaba «te faltan 504 cuotas · la última en agosto de
     * 2068», con el «Te falta en intereses» del resumen saltando de $549 a $1.104 millones.
     *
     * Los tres saldos de la mesa de trabajo, con el criterio nuevo: el préstamo de su mamá sigue
     * siendo el préstamo de su mamá.
     */
    @Test
    fun `un abono normal al credito mama no lo cambia de categoria`() {
        fun mamaCon(saldo: Long) = planDeUnaDeuda(saldo, 16.7652, 1_300_000L, null, null, saleDeTuBolsillo = true)

        val hoy = mamaCon(100_000_000L)
        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, hoy.comoVa)
        assertEquals(-2L, hoy.capital)

        // Un abono extraordinario de $150.000 — una operación que la app ya soporta.
        val conAbono = mamaCon(99_850_000L)
        assertEquals(1_948L, conAbono.capital, "sí baja \$1.948 al mes: \$23.376 al año sobre \$99.850.000")
        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, conAbono.comoVa, "0,02 % del saldo al año no es amortizar")
        assertNull(conAbono.mesesHastaLaUltimaCuota, "504 cuotas era una fecha inventada por el margen")

        // Y un ajuste de saldo de +$100.001 tampoco dispara la alerta roja sobre su mamá.
        val conAjuste = mamaCon(100_099_876L)
        assertTrue(conAjuste.capital < 0L, "la deuda sube, pero \$15.600 al año sobre \$100.000.000")
        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, conAjuste.comoVa, "no es la alerta roja sobre el préstamo de su mamá")
    }

    /**
     * El límite se mide **contra el saldo**, no contra la cuota: en un préstamo de solo intereses
     * el saldo es cien veces la cuota, y en uno que amortiza rápido apenas treinta. Un umbral
     * relativo a la cuota mide una cosa distinta en cada uno.
     *
     * Este es el contracaso del de arriba: un crédito chiquito que **sí** amortiza no puede caer en
     * «la deuda no se mueve» por tener una cuota chica. El Crediágil ·3090 debe $507.553 y paga
     * $26.485 al mes.
     */
    @Test
    fun `el margen se mide contra el saldo y no deja quieto un credito que si baja`() {
        val crediagil = planDeUnaDeuda(507_553L, 29.64, 26_485L, 1_960L, null, saleDeTuBolsillo = true)

        assertEquals(ComoVaLaDeuda.AMORTIZA, crediagil.comoVa)
        assertEquals(28, crediagil.mesesHastaLaUltimaCuota)
    }

    /**
     * El margen es **simétrico**: un capital positivo pero minúsculo tampoco es «amortiza», es la
     * misma deuda quieta vista desde el otro lado. Sobre $100.000.000, bajar $500 al mes son
     * $6.000 al año: 0,006 % del saldo, y una proyección que se iría a siglos.
     */
    @Test
    fun `un capital positivo pero minusculo tampoco es amortizar`() {
        // El Crédito Mamá con $502 más de cuota: interés $1.300.002, capital +$500.
        val plan = planDeUnaDeuda(100_000_000L, 16.7652, 1_300_502L, null, null, saleDeTuBolsillo = true)

        assertEquals(500L, plan.capital)
        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, plan.comoVa)
        assertNull(plan.mesesHastaLaUltimaCuota)
    }

    // ------------------------------------------------------------------- cuándo termina cada deuda

    /**
     * La hipoteca que su papá sacó y que él paga completa: 232 cuotas por delante. Es la deuda más
     * larga de las doce, la que fija la fecha de «tu última cuota».
     */
    @Test
    fun `la hipoteca del papa termina en 232 cuotas`() {
        val plan = planDeUnaDeuda(198_223_917L, 12.89, 2_228_152L, null, otrosCargosMensuales = null, saleDeTuBolsillo = true)

        assertEquals(232, plan.mesesHastaLaUltimaCuota)
        assertEquals(317_218_598L, plan.interesPorPagar)
    }

    /**
     * La proyección se hace **mes a mes con la misma aritmética Long** que usa un pago real, no
     * con la fórmula cerrada de una anualidad: registrar las 46 cuotas del ·9695 una por una tiene
     * que dejar el saldo en cero exactamente cuando la proyección dijo que lo dejaría, o la
     * pantalla estaría prometiendo una fecha que la app no va a cumplir.
     */
    @Test
    fun `la proyeccion coincide con registrar las cuotas una por una`() {
        val plan = planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, 124_800L, otrosCargosMensuales = null, saleDeTuBolsillo = true)
        val meses = assertNotNull(plan.mesesHastaLaUltimaCuota)

        var saldo = 40_104_518L
        var interesAcumulado = 0L
        var cuotas = 0
        while (saldo > 0L) {
            val desglose = desglosarCuota(1_204_064L, AccountType.LOAN, saldo, 11.27, 124_800L, null)
            interesAcumulado += desglose.interes
            saldo -= desglose.capital
            cuotas++
        }

        assertEquals(cuotas, meses)
        assertEquals(interesAcumulado, plan.interesPorPagar)
        assertTrue(saldo <= 0L)
    }

    /**
     * Sin tasa registrada **no se proyecta nada**, que es la postura de [MotivoDelDesglose.SIN_TASA]
     * con el signo invertido: allá el riesgo era inventar un interés plausible, acá sería inventar
     * que no hay interés y contestar `saldo / cuota` meses. El Crédito Techo Gardenera del dueño
     * está así — tasa 0, un solo pago de $10.000.000 — y «no cobra intereses» no es lo mismo que
     * «no sabemos cuánto cobra».
     */
    @Test
    fun `sin tasa no se inventa una fecha ni un cero por ciento de interes`() {
        val plan = planDeUnaDeuda(10_000_000L, rateEa = 0.0, cuota = 10_000_000L, seguroMensual = null, otrosCargosMensuales = null, saleDeTuBolsillo = true)

        assertEquals(ComoVaLaDeuda.SIN_TASA, plan.comoVa)
        assertNull(plan.fraccionDeInteres, "no es 0 %: es que no se sabe")
        assertNull(plan.mesesHastaLaUltimaCuota)
        assertEquals(0L, plan.capital, "cuota − seguro tendría pinta de deducido y no lo está")
    }

    /**
     * **Con tasa y sin cuota no es «sin tasa».** El interés del mes se sabe —sale del saldo y de la
     * tasa— y lo que falta es con qué proyectar. Rotularlo «Sin tasa registrada» ponía a la tarjeta
     * a contradecir la tasa que ella misma muestra arriba.
     */
    @Test
    fun `con tasa y sin cuota se dice el interes y no se dice sin tasa`() {
        val plan = planDeUnaDeuda(100_000_000L, rateEa = 15.23, cuota = 0L, seguroMensual = null, otrosCargosMensuales = null, saleDeTuBolsillo = true)

        assertEquals(ComoVaLaDeuda.SIN_CUOTA, plan.comoVa)
        assertTrue(plan.interes > 0L, "la tasa está: el interés del mes se sabe")
        assertNull(plan.fraccionDeInteres, "sin cuota no hay fracción de la cuota")
        assertNull(plan.mesesHastaLaUltimaCuota)
    }

    /**
     * **Una deuda en cero no es una deuda pagada: es una deuda sin registrar.**
     *
     * Antes daba interés $0 → capital = la cuota entera → «amortiza» → cero meses por delante →
     * **«Ya está pagada»**, en la misma tarjeta que decía «Falta registrar el desembolso». Es el
     * paso 1 del flujo de dos pasos que crea un crédito, así que el estado existe de verdad.
     */
    @Test
    fun `una deuda en cero no dice que amortiza ni que ya esta pagada`() {
        val plan = planDeUnaDeuda(0L, 15.0, 1_000_000L, null, otrosCargosMensuales = null, saleDeTuBolsillo = true)

        assertEquals(ComoVaLaDeuda.SIN_DEUDA, plan.comoVa)
        assertEquals(0L, plan.interes)
        assertEquals(0L, plan.capital, "la cuota entera no es «capital»: no hay deuda que bajar")
        assertNull(plan.mesesHastaLaUltimaCuota, "cero meses se leía «Ya está pagada»")
        assertNull(plan.interesPorPagar)
        assertNull(plan.fraccionDeInteres)
    }

    /**
     * Una deuda pagada de más (saldo negativo) es el mismo caso con peor cara: la tarjeta decía
     * «Deuda en negativo — revísala» y «Ya está pagada» a la vez.
     */
    @Test
    fun `una deuda negativa tampoco genera un plan`() {
        val plan = planDeUnaDeuda(-1_500_000L, 15.0, 1_000_000L, null, otrosCargosMensuales = null, saleDeTuBolsillo = true)

        assertEquals(ComoVaLaDeuda.SIN_DEUDA, plan.comoVa)
        assertEquals(0L, plan.interes)
        assertEquals(0L, plan.capital)
        assertNull(plan.mesesHastaLaUltimaCuota)
    }

    /**
     * Más allá de [MAX_MESES_PROYECTADOS] la respuesta honesta deja de ser una fecha. Una deuda de
     * $100.000.000 que baja $49.992 al mes pasa el margen de sobra y aun así tarda dos mil meses.
     */
    @Test
    fun `una deuda que tardaria mas de cien anos no promete una fecha`() {
        val plan = planDeUnaDeuda(
            saldoDeLaDeuda = 100_000_000L, rateEa = 0.0001, cuota = 50_000L, seguroMensual = null,
            otrosCargosMensuales = null,
            saleDeTuBolsillo = true,
        )

        assertEquals(ComoVaLaDeuda.AMORTIZA, plan.comoVa, "sí amortiza, solo que tardísimo")
        assertNull(plan.mesesHastaLaUltimaCuota, "más de $MAX_MESES_PROYECTADOS meses: no se contesta con una fecha")
        assertNull(plan.interesPorPagar)
        assertTrue(plan.noSeTermina, "y cuenta como una deuda que no se termina")
    }

    // --------------------------------------------------- el plan de un crédito ya cargado

    private val terminosDelMama = CreditTerms(
        accountId = "acc_mama", bank = "Mamá", principal = 100_000_000L, rateEa = 16.7652,
        termMonths = 240, installment = 1_300_000L, dayOfMonth = 27, startDate = "2020-01-01",
    )

    private fun credito(
        saldo: Long,
        hasMovements: Boolean = true,
        porMoneda: Map<String, Long> = emptyMap(),
        terms: CreditTerms? = terminosDelMama,
    ) = CreditSummary(
        account = Account("acc_mama", "Crédito Mamá", AccountType.LOAN, balance = saldo, balancesByCurrency = porMoneda),
        terms = terms,
        paidPct = null,
        hasMovements = hasMovements,
    )

    /**
     * **Un crédito recién creado no tiene plan.** Es el paso 1 del flujo de dos pasos (crearlo en
     * $0, después registrar el desembolso): la tarjeta ya dice «Falta registrar el desembolso», y
     * calcularle un plan sobre ese $0 le agregaba «$0 de interés» y «Ya está pagada» al lado.
     */
    @Test
    fun `un credito sin movimientos no produce plan`() {
        assertNull(planDelCredito(credito(saldo = 0L, hasMovements = false)))
    }

    /**
     * **La guarda de moneda que `progresoDeCredito` tenía y esto no heredó.** `account.balance` es
     * el componente COP, así que un préstamo en dólares proyectaba sobre $0 y la tarjeta decía
     * «Deuda en otra moneda» arriba y «Ya está pagada» tres líneas abajo.
     */
    @Test
    fun `un credito en otra moneda no produce plan`() {
        val enDolares = credito(saldo = 0L, porMoneda = mapOf("COP" to 0L, "USD" to 12_000L))

        assertNull(planDelCredito(enDolares))
        assertTrue(deudaEnOtraMoneda(enDolares))
    }

    /** Sin términos no hay ni cuota ni tasa: no hay nada que proyectar. */
    @Test
    fun `un credito sin terminos no produce plan`() {
        assertNull(planDelCredito(credito(saldo = 100_000_000L, terms = null)))
    }

    /** Y el contracaso: un crédito cargado de verdad sí produce su plan. */
    @Test
    fun `un credito cargado produce su plan`() {
        val plan = assertNotNull(planDelCredito(credito(saldo = 100_000_000L)))

        assertEquals(100_000_000L, plan.saldo)
        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, plan.comoVa)
    }

    // ------------------------------------------------------------- de quién sale la plata, y el total

    /**
     * Cuatro de sus doce créditos no los paga él: dos por libranza y dos que gira Skandia. Es la
     * misma distinción que [isCashFlow] hace por categoría, mirada desde el contrato.
     */
    @Test
    fun `una libranza y una cuota que paga otro no salen del bolsillo del dueno`() {
        val base = CreditTerms(
            accountId = "acc", bank = "Bancolombia", principal = 1L, rateEa = 10.0, termMonths = 12,
            installment = 1L, dayOfMonth = 5, startDate = "2026-01-01",
        )

        assertTrue(saleDeTuBolsillo(base))
        assertTrue(!saleDeTuBolsillo(base.copy(payrollDeduction = true)), "la retiene el empleador")
        assertTrue(!saleDeTuBolsillo(base.copy(paidBy = "Skandia")), "la gira un tercero")
        assertTrue(saleDeTuBolsillo(base.copy(paidBy = "   ")), "un paidBy en blanco no es un tercero")
    }

    /**
     * **El resumen de arriba no colapsa las dos cifras en una.** Sobre los doce créditos reales:
     * $8.485.265 al mes de intereses salen de su cuenta y $15.841.775 no. Quedarse solo con el
     * total le cobraría al bolsillo casi dieciséis millones que no salen de ahí; quedarse solo con
     * lo suyo escondería que existen.
     */
    @Test
    fun `el resumen separa lo que sale de su cuenta de lo que paga otro`() {
        val resumen = resumirDeudas(losDoceCreditosReales())

        assertEquals(8_485_265L, resumen.interesMensualPropio)
        assertEquals(15_841_775L, resumen.interesMensualAjeno)
        assertEquals(
            24_327_040L,
            resumen.interesMensualPropio + resumen.interesMensualAjeno,
            "las dos partes suman el interés mensual de toda la cartera",
        )
    }

    /**
     * **Y tampoco las colapsa en el interés que falta**, que es la cifra donde el corte era más
     * caro: los $549.605.074 «suyos y que se terminan» son el 36 % de los $1.511.826.418 que le
     * faltan a la cartera que sí termina. Sin la otra mitad, la fila titular parecía toda la
     * respuesta.
     */
    @Test
    fun `el interes que falta tambien se separa por quien lo paga`() {
        val resumen = resumirDeudas(losDoceCreditosReales())

        assertEquals(549_605_074L, resumen.interesPorPagarPropio)
        assertEquals(962_221_344L, resumen.interesPorPagarAjeno)
        assertEquals(
            1_511_826_418L,
            resumen.interesPorPagarPropio + resumen.interesPorPagarAjeno,
            "lo que falta en TODA la cartera que se termina",
        )
    }

    /**
     * Dos de los doce no se terminan nunca a este ritmo, y **solo uno** de esos dos es una alerta.
     * Es el resultado que resume todo este archivo.
     */
    @Test
    fun `de los doce creditos dos no se terminan y solo uno crece`() {
        val resumen = resumirDeudas(losDoceCreditosReales())

        assertEquals(2, resumen.creditosQueNoSeTerminan, "el Mamá y el Hipotecario ·2334")
        assertEquals(1, resumen.creditosQueCrecen, "solo el ·2334: el Mamá es el acuerdo con su mamá")
    }

    /**
     * **Y cuánta plata hay adentro de esos dos**, que es la mitad de la noticia que faltaba:
     * $100.000.000 del Crédito Mamá más $204.183.376 del Hipotecario ·2334.
     */
    @Test
    fun `el resumen dice cuanta plata hay en lo que no se termina`() {
        val resumen = resumirDeudas(losDoceCreditosReales())

        assertEquals(304_183_376L, resumen.deudaQueNoSeTermina)
    }

    /**
     * **Una deuda que no se termina le gana a cualquier fecha.** Con los doce créditos reales, el
     * resumen decía «diciembre de 2045» tomando el máximo de las que sí terminan y **descartando**
     * las que no: contestaba cuándo deja de girar, no cuándo deja de deber, mientras
     * $304.183.376 no se acaban nunca a este ritmo.
     */
    @Test
    fun `con una deuda que no se termina no hay fecha final`() {
        val resumen = resumirDeudas(losDoceCreditosReales())

        assertNull(resumen.mesesHastaLaUltimaCuota, "hay \$304.183.376 afuera de cualquier fecha")
    }

    /**
     * **El caso extremo, y el que muestra que no era un redondeo.** Si el único crédito que
     * amortiza está en $0, el máximo de las que terminan es 0 meses: la fila decía «Tu última
     * cuota — septiembre de 2026» debiendo $304.183.376.
     */
    @Test
    fun `el credito mas corto no puede fijar la fecha final de una deuda que no baja`() {
        val cartera = listOf(
            // Un crédito ya pagado (saldo $0) y el préstamo de su mamá, que no baja.
            planDeUnaDeuda(0L, 12.0, 500_000L, null, null, saleDeTuBolsillo = true),
            planDeUnaDeuda(100_000_000L, 16.7652, 1_300_000L, null, null, saleDeTuBolsillo = true),
        )

        val resumen = resumirDeudas(cartera)

        assertNull(resumen.mesesHastaLaUltimaCuota, "«septiembre de 2026» debiendo \$100.000.000")
        assertEquals(1, resumen.creditosQueNoSeTerminan)
        assertEquals(100_000_000L, resumen.deudaQueNoSeTermina)
    }

    /**
     * El contracaso: cuando **todas** las deudas se terminan, sí hay fecha, y es la de la más
     * larga. La cuenta de meses arranca en el mes en curso —la cuota de este mes es la primera de
     * las que faltan—, así que 232 cuotas desde septiembre de 2026 caen en **diciembre de 2045**.
     */
    @Test
    fun `si todas se terminan la fecha final es la de la deuda mas larga`() {
        val resumen = resumirDeudas(losDoceCreditosReales().filterNot { it.noSeTermina })

        assertEquals(232, resumen.mesesHastaLaUltimaCuota)
        assertEquals(0, resumen.creditosQueNoSeTerminan)
        assertEquals("diciembre de 2045", nombreDe(PeriodoFinanciero(2026, 9).mas(232 - 1)))
    }

    /** Sin créditos el resumen da ceros, no `null`: una cartera vacía debe $0 de intereses. */
    @Test
    fun `sin creditos el resumen da ceros`() {
        val resumen = resumirDeudas(emptyList())

        assertEquals(0L, resumen.interesMensualPropio)
        assertEquals(0L, resumen.interesPorPagarPropio)
        assertEquals(0L, resumen.deudaQueNoSeTermina)
        assertNull(resumen.mesesHastaLaUltimaCuota)
        assertEquals(0, resumen.creditosQueCrecen)
    }

    /**
     * Un crédito sin tasa no se cuenta como «no se termina»: no se sabe si se termina, y afirmar
     * que no sería inventar en la otra dirección. Tampoco puede bloquear la fecha final de los
     * demás — el Techo Gardenera es un solo pago que sí se salda.
     */
    @Test
    fun `un credito sin tasa no cuenta como que no se termina`() {
        val sinTasa = planDeUnaDeuda(10_000_000L, 0.0, 10_000_000L, null, otrosCargosMensuales = null, saleDeTuBolsillo = true)
        val queTermina = planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, 124_800L, null, saleDeTuBolsillo = true)

        val resumen = resumirDeudas(listOf(sinTasa, queTermina))

        assertEquals(0, resumen.creditosQueNoSeTerminan)
        assertEquals(0L, resumen.deudaQueNoSeTermina)
        assertEquals(46, resumen.mesesHastaLaUltimaCuota)
    }

    // ------------------------------------------------------------------------------ la fecha final

    @Test
    fun `sumar meses a un periodo cruza el fin de ano`() {
        assertEquals(PeriodoFinanciero(2026, 12), PeriodoFinanciero(2026, 9).mas(3))
        assertEquals(PeriodoFinanciero(2027, 1), PeriodoFinanciero(2026, 9).mas(4))
        assertEquals(PeriodoFinanciero(2026, 9), PeriodoFinanciero(2026, 9).mas(0))
        assertEquals(PeriodoFinanciero(2045, 12), PeriodoFinanciero(2026, 9).mas(231))
    }

    // --------------------------------------------------------------------------------------- datos

    /**
     * Los doce créditos de la base del dueño al 2026-09-08: saldo derivado de sus eventos vivos,
     * y tasa, cuota y seguro de `credit_terms`.
     */
    private fun losDoceCreditosReales(): List<PlanDelCredito> = listOf(
        // saldo, tasa E.A., cuota, seguro, otros cargos, ¿la paga él?
        planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, 124_800L, null, true),      // Libre inversión 9695
        planDeUnaDeuda(10_000_000L, 0.0, 10_000_000L, null, null, true),           // Techo Gardenera (sin tasa)
        planDeUnaDeuda(262_386_162L, 18.01, 6_040_259L, null, null, false),        // Libranza 4818 (nómina)
        planDeUnaDeuda(204_183_376L, 15.23, 2_613_714L, 209_219L, null, false),    // Hipotecario 2334 (Skandia)
        planDeUnaDeuda(767_800_000L, 10.99, 9_147_408L, 435_495L, null, false),    // Hipoteca 1254 (Skandia)
        planDeUnaDeuda(227_300_000L, 17.46, 5_223_385L, null, null, false),        // Libranza 4608 (nómina)
        planDeUnaDeuda(177_052_715L, 18.16, 4_101_123L, 89_100L, 25_000L, true),   // Vehículo 8761

        planDeUnaDeuda(507_553L, 29.64, 26_485L, 1_960L, null, true),              // Crediágil 3090
        planDeUnaDeuda(86_799_906L, 16.6, 1_931_488L, 58_656L, null, true),        // Cotrafa 5413
        planDeUnaDeuda(100_000_000L, 16.7652, 1_300_000L, null, null, true),       // Crédito Mamá
        planDeUnaDeuda(198_223_917L, 12.89, 2_228_152L, null, null, true),         // Hipoteca Papá
        planDeUnaDeuda(97_718_920L, 15.85, 2_051_848L, 370_954L, null, true),      // Libranza Papá (BBVA)
    )
}
