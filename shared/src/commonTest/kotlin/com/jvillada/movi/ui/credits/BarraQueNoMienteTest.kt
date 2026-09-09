package com.jvillada.movi.ui.credits

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.ComoVaLaDeuda
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.PeriodoFinanciero
import com.jvillada.movi.shared.model.planDeUnaDeuda
import com.jvillada.movi.shared.model.resumirDeudas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Una barra que se satura en cero cuando la deuda creció comunica lo contrario de lo que pasa.**
 *
 * `paidPct` compara el saldo de hoy contra el capital original y clampa a `[0, 1]`, así que
 * cualquier deuda por encima del capital sale como 0 — y «0 % pagado», con la barra vacía, se lee
 * «todavía no empezaste» cuando lo cierto es «vas para atrás». Le pasaba en dos de sus doce
 * créditos, por $4,18 y $5,39 millones.
 *
 * Acá también viven las frases de la tarjeta, que son la otra mitad del arreglo: la aritmética está
 * en `:core` (`PlanDelCreditoTest`) y esto prueba **qué se lee**.
 */
class BarraQueNoMienteTest {

    private val hipotecario2334 = CreditTerms(
        accountId = "acc_2334",
        bank = "Davibank",
        principal = 200_000_000L,
        rateEa = 15.23,
        termMonths = 240,
        installment = 2_613_714L,
        dayOfMonth = 7,
        startDate = "2026-07-07",
        insuranceMonthly = 209_219L,
        paidBy = "Skandia",
    )

    private fun credito(
        deuda: Long,
        terms: CreditTerms? = hipotecario2334,
        paidPct: Double? = null,
        hasMovements: Boolean = true,
    ) = CreditSummary(
        account = Account("acc_2334", "Hipotecario 2334", AccountType.LOAN, balance = deuda),
        terms = terms,
        paidPct = paidPct,
        hasMovements = hasMovements,
    )

    // ------------------------------------------------------------------------------------ la barra

    /**
     * El caso real: capital original $200.000.000, deuda $204.183.376. La barra decía **0 %
     * pagado**; ahora dice cuánto se pasó, y **no dibuja barra**.
     */
    @Test
    fun `una deuda por encima del capital original dice cuanto se paso, no cero por ciento`() {
        val progreso = progresoDeCredito(credito(deuda = 204_183_376L, paidPct = 0.0))

        assertEquals("$4.183.376 más que al inicio", progreso.etiqueta)
        assertTrue(!progreso.mostrarBarra, "no hay progreso que pintar: la barra no se dibuja")
        assertTrue(progreso.esAviso, "es una frase, no una cifra alineable")
    }

    /**
     * El contracaso, que es lo que hace que esto no sea «esconder la barra siempre»: un crédito que
     * de verdad va bajando sigue mostrando su porcentaje y su barra, exactamente como antes.
     */
    @Test
    fun `un credito que si bajo sigue mostrando su porcentaje y su barra`() {
        val progreso = progresoDeCredito(credito(deuda = 150_000_000L, paidPct = 0.25))

        assertEquals("25% pagado", progreso.etiqueta)
        assertTrue(progreso.mostrarBarra)
        assertEquals(0.25f, progreso.fraccion)
    }

    /**
     * Deber **exactamente** el capital original sí es 0 % pagado, y eso es cierto: no se ha abonado
     * nada. El caso nuevo es estrictamente «deber de más», no «no haber abonado».
     */
    @Test
    fun `deber justo el capital original sigue siendo cero por ciento pagado`() {
        val progreso = progresoDeCredito(credito(deuda = 200_000_000L, paidPct = 0.0))

        assertEquals("0% pagado", progreso.etiqueta)
        assertTrue(progreso.mostrarBarra)
    }

    /**
     * Los avisos que ya existían siguen ganando: un crédito sin desembolso registrado tiene que
     * seguir diciendo eso y no «$X más que al inicio», que sería cierto pero no es lo que hay que
     * arreglar primero.
     */
    @Test
    fun `el aviso de desembolso faltante sigue ganandole al exceso`() {
        val progreso = progresoDeCredito(credito(deuda = 204_183_376L, hasMovements = false))

        assertEquals("Falta registrar el desembolso", progreso.etiqueta)
    }

    // ------------------------------------------------------------------------------ lo que se lee

    /** La primera pregunta: cuánto de la cuota es alquiler de la plata. */
    @Test
    fun `la tarjeta dice cuanto de la cuota es interes, con un decimal`() {
        val plan = planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, 124_800L, null, saleDeTuBolsillo = true)

        assertEquals("$358.488 de interés · 29,8 % de la cuota", textoDelInteres(plan))
    }

    /**
     * El decimal existe porque entre 29,8 % y 30 % hay $2.500 al mes, pero **desaparece cuando es
     * cero**: «100,0 %» se lee peor que «100 %».
     */
    @Test
    fun `un porcentaje redondo va sin decimal`() {
        val mama = planDeUnaDeuda(100_000_000L, 16.7652, 1_300_000L, null, null, saleDeTuBolsillo = true)

        assertTrue(textoDelInteres(mama).endsWith("100 % de la cuota"), textoDelInteres(mama))
    }

    /** Sin tasa no se dice «0 % de interés»: se dice que no se sabe. */
    @Test
    fun `sin tasa la tarjeta dice que no se sabe, no cero`() {
        val plan = planDeUnaDeuda(10_000_000L, 0.0, 10_000_000L, null, null, saleDeTuBolsillo = true)

        assertEquals("Sin tasa registrada: no se sabe cuánto de la cuota es interés", textoDelInteres(plan))
        assertNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)), "no hay nada más que agregar")
    }

    /**
     * **La alerta.** Va en pesos y por mes, que es como se siente — y es la única de las cuatro
     * frases que se marca como alerta.
     */
    @Test
    fun `la amortizacion negativa se dice en pesos y es la unica alerta`() {
        val plan = planDeUnaDeuda(204_183_376L, 15.23, 2_613_714L, 209_219L, null, saleDeTuBolsillo = false)

        val comoVa = assertNotNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)))
        assertEquals("La cuota no alcanza: tu deuda crece $21.894 cada mes", comoVa.texto)
        assertTrue(comoVa.esAlerta)
    }

    /**
     * **Y el Crédito Mamá, que no es una alerta.** Misma aritmética de capital negativo, otro
     * mensaje y sin color: es el acuerdo con su mamá, no una emergencia.
     */
    @Test
    fun `el credito mama se explica sin alarma`() {
        val plan = planDeUnaDeuda(100_000_000L, 16.7652, 1_300_000L, null, null, saleDeTuBolsillo = true)

        val comoVa = assertNotNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)))
        assertEquals("La cuota se va toda en intereses: la deuda se queda donde está", comoVa.texto)
        assertTrue(!comoVa.esAlerta, "un préstamo familiar sin plazo no es una alerta")
        assertEquals(ComoVaLaDeuda.SOLO_INTERESES, plan.comoVa)
    }

    /**
     * La tercera pregunta: cuándo termina. La cuenta de meses arranca en el mes en curso —la
     * cuota de este mes es la primera de las que faltan—, así que 46 cuotas desde septiembre de
     * 2026 terminan en **junio de 2030**, no en julio.
     *
     * **El supuesto va adentro de la frase**, y no solo en [SUPUESTO_DE_LA_PROYECCION]: esa vive en
     * la tarjeta de resumen, decenas de dp más arriba, y para cuando el dueño llega a la fecha de un
     * crédito ya no la tiene a la vista. Una fecha sin su condición es una promesa.
     */
    @Test
    fun `la tarjeta dice cuantas cuotas faltan y en que mes cae la ultima`() {
        val plan = planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, 124_800L, null, saleDeTuBolsillo = true)

        val comoVa = assertNotNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)))
        assertEquals("Te faltan 46 cuotas · la última en junio de 2030 si la cuota y la tasa no cambian", comoVa.texto)
        assertTrue(!comoVa.esAlerta)
    }

    /** Una sola cuota se dice en singular, y cae este mismo mes. */
    @Test
    fun `una sola cuota restante se dice en singular`() {
        val plan = planDeUnaDeuda(1_000_000L, 10.0, 50_000_000L, null, null, saleDeTuBolsillo = true)

        val comoVa = assertNotNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)))
        assertEquals("Te falta 1 cuota · la última en septiembre de 2026 si la cuota y la tasa no cambian", comoVa.texto)
    }

    // ------------------------------------------------------- lo que la tarjeta NO puede decir

    /**
     * **«Ya está pagada» sobre un crédito recién creado.** El paso 1 del flujo de dos pasos deja el
     * saldo en $0; con eso, el interés daba $0, el capital daba la cuota entera, la deuda
     * «amortizaba» y la proyección salía sin girar: cero meses. La tarjeta terminaba diciendo a la
     * vez «Falta registrar el desembolso», «\$0 de interés» y «Ya está pagada».
     */
    @Test
    fun `un credito sin desembolso no dice ya esta pagada ni cero de interes`() {
        val plan = planDeUnaDeuda(0L, 12.5, 3_500_000L, null, null, saleDeTuBolsillo = true)

        assertNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)), "no hay nada que proyectar")
        assertTrue(!textoDelInteres(plan).contains("0 % de la cuota"), textoDelInteres(plan))
        assertEquals("Sin deuda registrada: todavía no corren intereses", textoDelInteres(plan))
    }

    /** Y el crédito con la deuda en negativo, que decía «Deuda en negativo — revísala» y «Ya está pagada» juntas. */
    @Test
    fun `una deuda en negativo tampoco dice ya esta pagada`() {
        val plan = planDeUnaDeuda(-1_500_000L, 12.5, 3_500_000L, null, null, saleDeTuBolsillo = true)

        assertNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)))
    }

    /**
     * **Con tasa y sin cuota no se dice «Sin tasa registrada»**, que es lo que decía antes — tres
     * líneas debajo de la tasa, que está en la misma tarjeta y se ve.
     */
    @Test
    fun `sin cuota se dice el interes del mes y no se contradice la tasa de arriba`() {
        val plan = planDeUnaDeuda(100_000_000L, 15.23, 0L, null, null, saleDeTuBolsillo = true)

        assertEquals("\$1.188.338 de interés al mes · sin cuota registrada", textoDelInteres(plan))
        assertTrue(!textoDelInteres(plan).contains("Sin tasa"), textoDelInteres(plan))
        assertNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)))
    }

    /**
     * Más de cien años no es una fecha, y la frase que lo dice tiene que existir en alguna parte
     * que se pueda borrar y notar. Ver [MAX_MESES_PROYECTADOS].
     */
    @Test
    fun `una deuda de mas de cien anos se dice con palabras y sin fecha`() {
        val plan = planDeUnaDeuda(100_000_000L, 0.0001, 50_000L, null, null, saleDeTuBolsillo = true)

        val comoVa = assertNotNull(comoVaEstaDeuda(plan, PeriodoFinanciero(2026, 9)))
        assertEquals("A este ritmo tardarías más de cien años", comoVa.texto)
        assertTrue(!comoVa.esAlerta)
    }

    // ------------------------------------------------------------------ lo que dice el resumen

    /**
     * **La fila de la fecha final no puede dar una fecha habiendo deuda que no se termina.** Con
     * los doce créditos reales decía «diciembre de 2045» mientras \$304.183.376 no bajan.
     */
    @Test
    fun `sin fecha cuando hay una deuda que no se termina`() {
        val mama = planDeUnaDeuda(100_000_000L, 16.7652, 1_300_000L, null, null, saleDeTuBolsillo = true)
        val queTermina = planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, 124_800L, null, saleDeTuBolsillo = true)

        val resumen = resumirDeudas(listOf(mama, queTermina))

        assertEquals("Sin fecha", textoDeLaUltimaCuota(resumen, PeriodoFinanciero(2026, 9)))
    }

    /** Y con todas terminadas sí hay fecha: 46 cuotas desde septiembre de 2026. */
    @Test
    fun `con todas las deudas terminando si hay fecha final`() {
        val queTermina = planDeUnaDeuda(40_104_518L, 11.27, 1_204_064L, 124_800L, null, saleDeTuBolsillo = true)

        val resumen = resumirDeudas(listOf(queTermina))

        assertEquals("junio de 2030", textoDeLaUltimaCuota(resumen, PeriodoFinanciero(2026, 9)))
    }

    /**
     * **La plata que no se acaba nunca, dicha en pesos.** `creditosQueNoSeTerminan` se calculaba,
     * se probaba y no se dibujaba en ninguna pantalla.
     */
    @Test
    fun `lo que no se termina se cuenta en creditos y en pesos`() {
        assertEquals(
            "2 créditos no se terminan a este ritmo: \$304.183.376 que no bajan",
            textoDeLoQueNoSeTermina(2, 304_183_376L),
        )
        assertEquals(
            "1 crédito no se termina a este ritmo: \$100.000.000 que no bajan",
            textoDeLoQueNoSeTermina(1, 100_000_000L),
        )
    }

    /** La alerta de arriba, en singular y en plural. */
    @Test
    fun `la amortizacion negativa de arriba se cuenta en creditos`() {
        assertEquals(
            "En 1 crédito la cuota no cubre los intereses: esa deuda crece sola",
            textoDeLaAmortizacionNegativa(1),
        )
        assertEquals(
            "En 2 créditos la cuota no cubre los intereses: esas deudas crecen solas",
            textoDeLaAmortizacionNegativa(2),
        )
    }
}
