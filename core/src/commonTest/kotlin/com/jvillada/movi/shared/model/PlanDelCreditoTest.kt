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
     * Le falta **$2** al mes sobre una cuota de $1.300.000. Tratarlo igual que al ·2334 sería
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
     * límite. Comparten el signo del capital y **no** son lo mismo: $2 contra $21.894, sobre
     * cuotas del mismo orden de magnitud.
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
     * El límite es **relativo a la cuota**, no una cifra en pesos: el Crediágil ·3090 tiene una
     * cuota de $26.485 y la hipoteca de $9.147.408, cuatro órdenes de magnitud de diferencia.
     * Un umbral fijo de, digamos, $1.000 declararía «no se mueve» un crédito chico que sí amortiza.
     */
    @Test
    fun `el margen es una milesima de la cuota, no una cifra fija`() {
        // Capital de +$500 sobre una cuota de $26.485: margen 26, así que amortiza.
        val chico = planDeUnaDeuda(saldoDeLaDeuda = 0L, rateEa = 10.0, cuota = 26_485L, seguroMensual = 25_985L, otrosCargosMensuales = null, saleDeTuBolsillo = true)
        assertEquals(ComoVaLaDeuda.AMORTIZA, chico.comoVa)
        assertEquals(500L, chico.capital)

        // El mismo capital de +$500 sobre una cuota de $9.147.408: margen 9.147, así que no se mueve.
        val grande = planDeUnaDeuda(saldoDeLaDeuda = 0L, rateEa = 10.0, cuota = 9_147_408L, seguroMensual = 9_146_908L, otrosCargosMensuales = null, saleDeTuBolsillo = true)
        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, grande.comoVa)
        assertEquals(500L, grande.capital)
    }

    /**
     * El margen es **simétrico**. Un capital de +$370 sobre una cuota de $1.300.000 no es
     * «amortiza»: es la misma deuda quieta vista desde el otro lado, y proyectarle una fecha daría
     * 270.000 meses. Sin esta rama la pantalla contestaría «termina en el año 24.500».
     */
    @Test
    fun `un capital positivo pero minusculo tampoco es amortizar`() {
        val plan = planDeUnaDeuda(
            saldoDeLaDeuda = 0L, rateEa = 10.0, cuota = 1_300_000L, seguroMensual = 1_299_630L,
            otrosCargosMensuales = null,
            saleDeTuBolsillo = true,
        )

        assertEquals(370L, plan.capital)
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

    /** Una deuda ya pagada no causa intereses ni tiene cuotas por delante. */
    @Test
    fun `una deuda en cero no causa intereses`() {
        val plan = planDeUnaDeuda(0L, 15.0, 1_000_000L, null, otrosCargosMensuales = null, saleDeTuBolsillo = true)

        assertEquals(0L, plan.interes)
        assertEquals(0, plan.mesesHastaLaUltimaCuota)
        assertEquals(0L, plan.interesPorPagar)
    }

    /**
     * Una deuda pagada de más (saldo negativo) se trata como cero, igual que en [desglosarCuota]:
     * no causa intereses **negativos**, que sería un crédito que le paga a él.
     */
    @Test
    fun `una deuda negativa no genera intereses negativos`() {
        val plan = planDeUnaDeuda(-1_500_000L, 15.0, 1_000_000L, null, otrosCargosMensuales = null, saleDeTuBolsillo = true)

        assertEquals(0L, plan.interes)
        assertEquals(1_000_000L, plan.capital)
    }

    /**
     * Más allá de [MAX_MESES_PROYECTADOS] la respuesta honesta deja de ser una fecha. Una deuda
     * que amortiza $1 al mes sobre una cuota de $1.000 pasa el margen (margen = 1, capital = 1 no
     * es > 1... así que se le da 2) y aun así no se termina en un siglo.
     */
    @Test
    fun `una deuda que tardaria mas de cien anos no promete una fecha`() {
        // Sin tasa no sirve para este caso, así que se usa una tasa mínima y una cuota que apenas
        // supera el interés: el capital pasa el margen pero el horizonte no cabe en 1.200 meses.
        val plan = planDeUnaDeuda(
            saldoDeLaDeuda = 100_000_000L, rateEa = 0.0001, cuota = 50_000L, seguroMensual = null,
            otrosCargosMensuales = null,
            saleDeTuBolsillo = true,
        )

        assertEquals(ComoVaLaDeuda.AMORTIZA, plan.comoVa, "sí amortiza, solo que tardísimo")
        assertNull(plan.mesesHastaLaUltimaCuota, "más de ${MAX_MESES_PROYECTADOS} meses: no se contesta con una fecha")
        assertNull(plan.interesPorPagar)
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
     * La última cuota de toda su cartera cae 232 meses después de hoy: la hipoteca del papá.
     * Cuenta **todas** las deudas y no solo las que paga él — la pregunta es cuándo deja de deber,
     * no cuándo deja de girar.
     */
    @Test
    fun `la ultima cuota de la cartera es la de la deuda mas larga`() {
        val resumen = resumirDeudas(losDoceCreditosReales())

        assertEquals(232, resumen.mesesHastaLaUltimaCuota)
        assertEquals("enero de 2046", nombreDe(PeriodoFinanciero(2026, 9).mas(232)))
    }

    /** Sin créditos el resumen da ceros, no `null`: una cartera vacía debe $0 de intereses. */
    @Test
    fun `sin creditos el resumen da ceros`() {
        val resumen = resumirDeudas(emptyList())

        assertEquals(0L, resumen.interesMensualPropio)
        assertEquals(0L, resumen.interesPorPagarPropio)
        assertNull(resumen.mesesHastaLaUltimaCuota)
        assertEquals(0, resumen.creditosQueCrecen)
    }

    /**
     * Un crédito sin tasa no se cuenta como «no se termina»: no se sabe si se termina, y afirmar
     * que no sería inventar en la otra dirección. El Techo Gardenera es un solo pago que sí se
     * salda.
     */
    @Test
    fun `un credito sin tasa no cuenta como que no se termina`() {
        val sinTasa = planDeUnaDeuda(10_000_000L, 0.0, 10_000_000L, null, otrosCargosMensuales = null, saleDeTuBolsillo = true)

        assertEquals(0, resumirDeudas(listOf(sinTasa)).creditosQueNoSeTerminan)
    }

    // ------------------------------------------------------------------------------ la fecha final

    @Test
    fun `sumar meses a un periodo cruza el fin de ano`() {
        assertEquals(PeriodoFinanciero(2026, 12), PeriodoFinanciero(2026, 9).mas(3))
        assertEquals(PeriodoFinanciero(2027, 1), PeriodoFinanciero(2026, 9).mas(4))
        assertEquals(PeriodoFinanciero(2026, 9), PeriodoFinanciero(2026, 9).mas(0))
        assertEquals(PeriodoFinanciero(2046, 1), PeriodoFinanciero(2026, 9).mas(232))
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
        planDeUnaDeuda(177_052_715L, 18.16, 4_101_123L, 89_100L, 25_000L, true), // Vehículo 8761

        planDeUnaDeuda(507_553L, 29.64, 26_485L, 1_960L, null, true),              // Crediágil 3090
        planDeUnaDeuda(86_799_906L, 16.6, 1_931_488L, 58_656L, null, true),        // Cotrafa 5413
        planDeUnaDeuda(100_000_000L, 16.7652, 1_300_000L, null, null, true),       // Crédito Mamá
        planDeUnaDeuda(198_223_917L, 12.89, 2_228_152L, null, null, true),         // Hipoteca Papá
        planDeUnaDeuda(97_718_920L, 15.85, 2_051_848L, 370_954L, null, true),      // Libranza Papá (BBVA)
    )
}
