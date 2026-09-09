package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **«¿Y si le abono de más?»** sobre los créditos reales del dueño.
 *
 * Saldos, tasas, cuotas y seguros salen de `credit_terms` y de la suma de los eventos vivos de cada
 * cuenta LOAN al 2026-09-08 — los mismos que usa `PlanDelCreditoTest`, para que los dos archivos
 * hablen de la misma cartera. Lo que se prueba acá es lo que ese otro no puede: que el ahorro y la
 * fecha nueva sean **la misma aritmética con otro saldo**, y que las tres deudas que hoy no se
 * terminan contesten algo distinto de «no se sabe».
 */
class AbonoExtraordinarioTest {

    // ------------------------------------------------------------------ el caso normal: acorta

    /**
     * Libre inversión ·9695: $40.104.518 al 11,27 %, cuota $1.204.064 con $124.800 de seguro. Le
     * quedan 46 cuotas; con $2.000.000 encima le quedan 43.
     */
    @Test
    fun `dos millones al libre inversion 9695 ahorran tres cuotas y 971 mil de intereses`() {
        val sim = simularAbonoUnico(
            saldoDeLaDeuda = 40_104_518L,
            rateEa = 11.27,
            cuota = 1_204_064L,
            seguroMensual = 124_800L,
            otrosCargosMensuales = null,
            saleDeTuBolsillo = true,
            abono = 2_000_000L,
        )

        assertEquals(QueLograElAbono.ACORTA_EL_PLAZO, sim.logro)
        assertEquals(46, sim.antes.mesesHastaLaUltimaCuota)
        assertEquals(43, sim.despues.mesesHastaLaUltimaCuota)
        assertEquals(3, sim.cuotasQueSeAhorra)
        assertEquals(971_366L, sim.interesQueSeAhorra)
        assertEquals(0L, sim.sobrante)
    }

    /**
     * **El mismo abono no vale lo mismo en dos créditos**, que es la pregunta que esta feature vino
     * a contestar. $5.000.000 al Vehículo ·8761 (18,16 %, 70 cuotas por delante) ahorran ocho veces
     * más interés que $2.000.000 al ·9695, y el dueño no tiene forma de deducirlo de las tasas.
     */
    @Test
    fun `cinco millones al vehiculo 8761 ahorran ocho millones de intereses`() {
        val sim = simularAbonoUnico(
            saldoDeLaDeuda = 177_052_715L,
            rateEa = 18.16,
            cuota = 4_101_123L,
            seguroMensual = 89_100L,
            otrosCargosMensuales = 25_000L,
            saleDeTuBolsillo = true,
            abono = 5_000_000L,
        )

        assertEquals(QueLograElAbono.ACORTA_EL_PLAZO, sim.logro)
        assertEquals(3, sim.cuotasQueSeAhorra)
        assertEquals(8_009_748L, sim.interesQueSeAhorra)
    }

    /**
     * **La simulación es [planDeUnaDeuda] con otro saldo, no una fórmula paralela.** Es lo único
     * que garantiza que la hoja no conteste una fecha que la tarjeta de atrás contradiga: si algún
     * día alguien escribe acá una anualidad cerrada «porque es más rápida», esta prueba cae.
     */
    @Test
    fun `el plan de despues es exactamente el plan del saldo ya bajado`() {
        val sim = simularAbonoUnico(177_052_715L, 18.16, 4_101_123L, 89_100L, 25_000L, true, 5_000_000L)

        val aMano = planDeUnaDeuda(177_052_715L - 5_000_000L, 18.16, 4_101_123L, 89_100L, 25_000L, true)
        assertEquals(aMano, sim.despues)
        assertEquals(planDeUnaDeuda(177_052_715L, 18.16, 4_101_123L, 89_100L, 25_000L, true), sim.antes)
    }

    // ------------------------------------------------------------------ saldarla, y lo que sobra

    /**
     * Crediágil ·3090: debe $507.553. Un millón la salda **y sobran $492.447**, que no ahorraron un
     * peso de interés. Sin [SimulacionDeAbono.sobrante] la hoja diría «con esto la saldas» sobre un
     * monto del que la mitad no hacía falta — y ese es justo el crédito que el dueño mira cuando
     * pregunta cuál matar primero.
     */
    @Test
    fun `un millon al crediagil 3090 la salda y sobra medio millon`() {
        val sim = simularAbonoUnico(507_553L, 29.64, 26_485L, null, null, true, 1_000_000L)

        assertEquals(QueLograElAbono.SALDA_LA_DEUDA, sim.logro)
        assertEquals(492_447L, sim.sobrante)
        assertEquals(26, sim.cuotasQueSeAhorra)
        assertEquals(157_449L, sim.interesQueSeAhorra)
        assertEquals(ComoVaLaDeuda.SIN_DEUDA, sim.despues.comoVa)
    }

    /**
     * Saldar una deuda que **hoy no se termina** no tiene ahorro que decir: lo que se ahorra es un
     * plazo infinito, y ponerle una cifra sería la mentira más fácil de esta pantalla. El
     * Hipotecario ·2334 crece $21.894 al mes; saldarlo entero se cuenta, pero sin número.
     */
    @Test
    fun `saldar una deuda que no se termina no promete un ahorro`() {
        val sim = simularAbonoUnico(204_183_376L, 15.23, 2_613_714L, 209_219L, null, false, 204_183_376L)

        assertEquals(QueLograElAbono.SALDA_LA_DEUDA, sim.logro)
        assertNull(sim.cuotasQueSeAhorra)
        assertNull(sim.interesQueSeAhorra)
        assertEquals(0L, sim.sobrante)
    }

    // -------------------------------------------------- las deudas que hoy no se terminan

    /**
     * **De «nunca» a una fecha.** El Hipotecario ·2334 tiene amortización negativa: la cuota no
     * cubre interés más seguro y la deuda crece sola. Con $5.000.000 encima empieza a bajar y pasa
     * a tener final — 353 cuotas. Esconder eso dejaría la deuda más grande del dueño sin ninguna
     * pregunta que hacerle.
     */
    @Test
    fun `cinco millones al hipotecario 2334 le ponen fecha a una deuda que crecia sola`() {
        val sim = simularAbonoUnico(204_183_376L, 15.23, 2_613_714L, 209_219L, null, false, 5_000_000L)

        assertEquals(ComoVaLaDeuda.LA_DEUDA_CRECE, sim.antes.comoVa)
        assertEquals(QueLograElAbono.LE_PONE_FECHA, sim.logro)
        assertEquals(353, sim.despues.mesesHastaLaUltimaCuota)
        // No hay ahorro que restar contra un plazo infinito, y no se inventa uno.
        assertNull(sim.cuotasQueSeAhorra)
        assertNull(sim.interesQueSeAhorra)
    }

    /** El Crédito Mamá es el otro que no se termina, y por el otro motivo: la cuota es interés puro. */
    @Test
    fun `diez millones al credito mama le ponen fecha a una cuota que era interes puro`() {
        val sim = simularAbonoUnico(100_000_000L, 16.7652, 1_300_000L, null, null, true, 10_000_000L)

        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, sim.antes.comoVa)
        assertEquals(QueLograElAbono.LE_PONE_FECHA, sim.logro)
        assertEquals(179, sim.despues.mesesHastaLaUltimaCuota)
    }

    /**
     * **Y cuando no alcanza, se dice.** $1.000.000 al ·2334 bajan la amortización negativa de
     * $21.894 a $10.011 al mes: la deuda sigue creciendo. Contestar «te ahorras…» acá sería peor
     * que no contestar nada.
     */
    @Test
    fun `un millon al hipotecario 2334 no alcanza y la deuda sigue creciendo`() {
        val sim = simularAbonoUnico(204_183_376L, 15.23, 2_613_714L, 209_219L, null, false, 1_000_000L)

        assertEquals(QueLograElAbono.NO_ALCANZA, sim.logro)
        assertEquals(ComoVaLaDeuda.LA_DEUDA_CRECE, sim.despues.comoVa)
        assertEquals(-10_011L, sim.despues.capital)
        assertNull(sim.cuotasQueSeAhorra)
        assertNull(sim.interesQueSeAhorra)
    }

    // ------------------------------------------------------------------ cuánto haría falta

    /**
     * **$2.549.401 sobre $204 millones** — el 1,2 % de la deuda— y el ·2334 pasa de crecer para
     * siempre a terminarse. Es la cifra que el dueño no tiene forma de estimar, y la razón de que
     * [abonoMinimoParaQueSeTermine] exista.
     *
     * El límite se prueba **por los dos lados**: un peso menos y la deuda vuelve a quedarse quieta.
     * Sin la mitad de abajo, cualquier monto grande pasaría la prueba.
     */
    @Test
    fun `el minimo que le pone fecha al hipotecario 2334 son 2 millones y medio`() {
        val minimo = abonoMinimoParaQueSeTermine(204_183_376L, 15.23, 2_613_714L, 209_219L, null)

        assertEquals(2_549_401L, minimo)
        assertNotNull(planDeUnaDeuda(204_183_376L - 2_549_401L, 15.23, 2_613_714L, 209_219L, null, true).mesesHastaLaUltimaCuota)
        assertNull(planDeUnaDeuda(204_183_376L - 2_549_400L, 15.23, 2_613_714L, 209_219L, null, true).mesesHastaLaUltimaCuota)
    }

    /** Lo mismo en el Crédito Mamá: bastan $319.625 de los $100.000.000. */
    @Test
    fun `el minimo que le pone fecha al credito mama son 319 mil`() {
        val minimo = abonoMinimoParaQueSeTermine(100_000_000L, 16.7652, 1_300_000L, null, null)

        assertEquals(319_625L, minimo)
        assertNull(planDeUnaDeuda(100_000_000L - 319_624L, 16.7652, 1_300_000L, null, null, true).mesesHastaLaUltimaCuota)
    }

    /**
     * **El mínimo no promete nada que la fecha no aguante.** Con esos $2.549.401 la deuda se
     * termina… en 479 cuotas, cuarenta años. La cifra es cierta y leída sola invita a creer que un
     * abono chico resuelve el ·2334, así que la pantalla la muestra siempre con su fecha. Esta
     * prueba fija el número que hace que eso sea necesario.
     */
    @Test
    fun `el minimo da una fecha lejisima y por eso no se muestra solo`() {
        val sim = simularAbonoUnico(204_183_376L, 15.23, 2_613_714L, 209_219L, null, false, 2_549_401L)

        assertEquals(QueLograElAbono.LE_PONE_FECHA, sim.logro)
        assertEquals(479, sim.despues.mesesHastaLaUltimaCuota)
    }

    /** Una deuda que ya se termina no tiene mínimo que buscar: la pregunta no aplica. */
    @Test
    fun `un credito que ya se termina no tiene minimo`() {
        assertNull(abonoMinimoParaQueSeTermine(40_104_518L, 11.27, 1_204_064L, 124_800L, null))
    }

    /**
     * Y una cuota que ni siquiera cubre el seguro tampoco: por más que se abone, el saldo vuelve a
     * subir el mes siguiente. Ahí lo único que sirve es saldarla, y decir un monto sería mentir.
     */
    @Test
    fun `una cuota que no cubre ni el seguro no tiene minimo`() {
        assertNull(abonoMinimoParaQueSeTermine(50_000_000L, 20.0, 300_000L, 300_000L, null))
    }

    // ------------------------------------------------------------------ lo que no se simula

    /** Un abono de cero o negativo no es una pregunta. */
    @Test
    fun `sin monto no hay nada que simular`() {
        assertEquals(
            QueLograElAbono.NO_SE_PUEDE_SIMULAR,
            simularAbonoUnico(40_104_518L, 11.27, 1_204_064L, 124_800L, null, true, 0L).logro,
        )
        assertEquals(
            QueLograElAbono.NO_SE_PUEDE_SIMULAR,
            simularAbonoUnico(40_104_518L, 11.27, 1_204_064L, 124_800L, null, true, -500_000L).logro,
        )
    }

    /**
     * Sin tasa, sin cuota o sin deuda no hay proyección que acortar. Misma postura que
     * [ComoVaLaDeuda.SIN_TASA]: no se contesta 0, se contesta que no se sabe.
     */
    @Test
    fun `sin tasa sin cuota o sin deuda no se simula`() {
        assertEquals(
            QueLograElAbono.NO_SE_PUEDE_SIMULAR,
            simularAbonoUnico(40_104_518L, null, 1_204_064L, null, null, true, 2_000_000L).logro,
        )
        assertEquals(
            QueLograElAbono.NO_SE_PUEDE_SIMULAR,
            simularAbonoUnico(40_104_518L, 11.27, 0L, null, null, true, 2_000_000L).logro,
        )
        assertEquals(
            QueLograElAbono.NO_SE_PUEDE_SIMULAR,
            simularAbonoUnico(0L, 11.27, 1_204_064L, null, null, true, 2_000_000L).logro,
        )
    }

    // ------------------------------------------------------------------ de quién es el ahorro

    /**
     * **Cuatro de sus doce créditos no los paga él**, y el ahorro de un abono ahí no es suyo. La
     * simulación no lo esconde ni lo bloquea —la deuda es de él y puede abonarle— pero el dato
     * viaja: es lo que la hoja necesita para poder decirlo. Ver [saleDeTuBolsillo].
     */
    @Test
    fun `el ahorro de un credito que paga otro llega marcado como ajeno`() {
        val skandia = CreditTerms(
            accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
            termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
            insuranceMonthly = 209_219L, paidBy = "Skandia",
        )
        val propio = skandia.copy(paidBy = null)

        val ajeno = simularAbonoUnico(204_183_376L, 15.23, 2_613_714L, 209_219L, null, saleDeTuBolsillo(skandia), 5_000_000L)
        val mio = simularAbonoUnico(204_183_376L, 15.23, 2_613_714L, 209_219L, null, saleDeTuBolsillo(propio), 5_000_000L)

        assertTrue(!ajeno.antes.saleDeTuBolsillo && !ajeno.despues.saleDeTuBolsillo)
        assertTrue(mio.antes.saleDeTuBolsillo && mio.despues.saleDeTuBolsillo)
        // Y la aritmética es la misma: quién paga rotula el resultado, no lo cambia.
        assertEquals(mio.despues.mesesHastaLaUltimaCuota, ajeno.despues.mesesHastaLaUltimaCuota)
    }

    /**
     * El atajo que toma la pantalla —[simularAbonoUnico] sobre un `CreditSummary`— tiene que dar lo
     * mismo que armarlo a mano, y devolver `null` en los tres casos en que «el saldo que llegó no es
     * la deuda», exactamente como [planDelCredito].
     */
    @Test
    fun `sobre un credito cargado da lo mismo, y null cuando el saldo no es la deuda`() {
        val terms = CreditTerms(
            accountId = "acc_9695", bank = "Bancolombia", principal = 80_000_000L, rateEa = 11.27,
            termMonths = 105, installment = 1_204_064L, dayOfMonth = 15, startDate = "2021-06-15",
            insuranceMonthly = 124_800L,
        )
        val credito = CreditSummary(
            account = Account("acc_9695", "Libre inversión 9695", AccountType.LOAN, balance = 40_104_518L),
            terms = terms,
            paidPct = 0.5,
        )

        assertEquals(
            simularAbonoUnico(40_104_518L, 11.27, 1_204_064L, 124_800L, null, true, 2_000_000L),
            simularAbonoUnico(credito, 2_000_000L),
        )
        assertNull(simularAbonoUnico(credito.copy(terms = null), 2_000_000L))
        assertNull(simularAbonoUnico(credito.copy(hasMovements = false), 2_000_000L))
        assertNull(abonoMinimoParaQueSeTermine(credito.copy(hasMovements = false)))
    }

    /**
     * **Y la cuarta guarda, la que hasta ahora no mataba ninguna prueba: la deuda en otra moneda.**
     *
     * `account.balance` es solo el **componente COP** del saldo (ver `enrichWith`), así que un
     * préstamo con plata en dólares llega con el saldo COP de un lado y la deuda en moneda del
     * otro. Sin esta guarda, [simularAbonoUnico] contestaría un ahorro sobre una deuda que no es la
     * deuda, y [abonoMinimoParaQueSeTermine] —la cifra que la hoja convierte en un chip—
     * contestaría un mínimo calculado sobre un saldo que no conoce. Es el mismo `null` que ya
     * devuelve [planDelCredito]. Ver [deudaEnOtraMoneda].
     */
    @Test
    fun `una deuda con componente en otra moneda no se simula`() {
        val terms = CreditTerms(
            accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
            termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
            insuranceMonthly = 209_219L,
        )
        val enPesos = CreditSummary(
            account = Account(
                "acc_2334", "Hipotecario 2334", AccountType.LOAN, balance = 204_183_376L,
                balancesByCurrency = mapOf("COP" to 204_183_376L),
            ),
            terms = terms,
            paidPct = 0.0,
        )
        val conDolares = enPesos.copy(
            account = enPesos.account.copy(
                balancesByCurrency = mapOf("COP" to 204_183_376L, "USD" to 12_000L),
            ),
        )

        // El control: en pesos las dos preguntas SÍ se contestan, y con las cifras conocidas.
        assertEquals(QueLograElAbono.NO_ALCANZA, simularAbonoUnico(enPesos, 1_000_000L)?.logro)
        assertEquals(2_549_401L, abonoMinimoParaQueSeTermine(enPesos))
        // Y con un componente en dólares, ninguna de las dos inventa una respuesta.
        assertNull(simularAbonoUnico(conDolares, 1_000_000L))
        assertNull(abonoMinimoParaQueSeTermine(conDolares))
    }
}
