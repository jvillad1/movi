package com.jvillada.movi.ui.recurrentes

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.CREDIT_RULE_PREFIX
import com.jvillada.movi.shared.model.CreditSummary
import com.jvillada.movi.shared.model.CreditTerms
import com.jvillada.movi.shared.model.planDelCredito
import com.jvillada.movi.ui.dashboard.PagoDelPeriodo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * # «¿Cuánto debo pagar?», con sus cifras reales
 *
 * El motivo de todo esto, con sus palabras: *«no me sale un extracto en el sitio ni valor a pagar,
 * tenía ese valor en mi cabeza y por eso lo puse»*. Las cifras de acá son las de sus dos créditos
 * al 19 de septiembre de 2026, y los intereses esperados salen de su extracto — no de correr la
 * función y copiar lo que devolvió.
 *
 * Lo que fija esta prueba no es solo la aritmética (para eso está `PlanDelCreditoTest` en `:core`,
 * y esto usa **la misma** función) sino las tres fronteras de la honestidad: sin tasa no se
 * estima, «no cobra intereses» no se dice como «$0 de interés», y una tarjeta no recibe estimación
 * ninguna.
 */
class CuantoDeboPagarTest {

    /**
     * **Vehículo ·8761** (Banco de Occidente): saldo $177.052.715 al 18,16 % E.A., seguro $89.100,
     * cuota $4.101.123. Es el crédito que pagó de memoria.
     */
    private val vehiculo = CreditSummary(
        account = Account("acc_8761", "Vehículo 8761", AccountType.LOAN, balance = 177_052_715L),
        terms = CreditTerms(
            accountId = "acc_8761", bank = "Banco de Occidente", principal = 190_000_000L,
            rateEa = 18.16, termMonths = 60, installment = 4_101_123L, dayOfMonth = 19,
            startDate = "2025-09-19", insuranceMonthly = 89_100L,
        ),
        paidPct = 0.07,
    )

    /** **Cotrafa ·5413**: saldo $86.799.906 al 16,6 %, seguro $58.656, cuota $1.931.488. */
    private val cotrafa = CreditSummary(
        account = Account("acc_5413", "Cotrafa 5413", AccountType.LOAN, balance = 86_799_906L),
        terms = CreditTerms(
            accountId = "acc_5413", bank = "Cotrafa", principal = 90_000_000L,
            rateEa = 16.6, termMonths = 84, installment = 1_931_488L, dayOfMonth = 5,
            startDate = "2025-01-05", insuranceMonthly = 58_656L,
        ),
        paidPct = 0.03,
    )

    /** Techo Gardenera: tasa 0 como marcador, **sin** la casilla. Es «no sabemos», no «no cobra». */
    private val sinTasa = CreditSummary(
        account = Account("acc_techo", "Crédito Techo Gardenera", AccountType.LOAN, balance = 10_000_000L),
        terms = CreditTerms(
            accountId = "acc_techo", bank = "Constructora", principal = 10_000_000L, rateEa = 0.0,
            termMonths = 1, installment = 10_000_000L, dayOfMonth = 30, startDate = "2026-08-30",
        ),
        paidPct = 0.0,
    )

    /** El mismo, pero con «No cobra intereses» declarado por el dueño. */
    private val noCobraIntereses = CreditSummary(
        account = sinTasa.account,
        terms = sinTasa.terms!!.copy(sinIntereses = true, insuranceMonthly = 0L),
        paidPct = 0.0,
    )

    private fun plan(credito: CreditSummary) = assertNotNull(planDelCredito(credito))

    private fun fila(credito: CreditSummary, pagado: Boolean = false, esSaldo: Boolean = false) =
        PagoDelPeriodo(
            ruleId = CREDIT_RULE_PREFIX + credito.account.id,
            nombre = "Cuota ${credito.account.name}",
            monto = credito.terms?.installment ?: 0L,
            pagado = pagado,
            diasParaVencer = 3,
            montoEsSaldo = esSaldo,
            vence = "2026-09-19",
            periodoDelSello = "2026-09",
        )

    @Test
    fun el_vehiculo_dice_interes_seguro_y_capital_con_las_cifras_del_extracto() {
        val p = plan(vehiculo)
        // Las tres cifras del extracto del Banco de Occidente, calculadas sobre el saldo de hoy.
        assertEquals(2_479_256L, p.interes)
        assertEquals(89_100L, p.seguro)
        assertEquals(1_532_767L, p.capital)
        // Y suman la cuota: la estimación reparte lo pactado, no propone otro monto.
        assertEquals(vehiculo.terms!!.installment, p.interes + p.seguro + p.otrosCargos + p.capital)

        val texto = assertNotNull(textoDeLaCuotaEstimada(p))
        assertEquals(
            "Movi estima: \$2.479.256 de interés · \$89.100 el seguro · \$1.532.767 a capital",
            texto,
        )
    }

    @Test
    fun cotrafa_dice_su_propio_interes() {
        val p = plan(cotrafa)
        assertEquals(1_118_027L, p.interes)
        assertEquals(58_656L, p.seguro)
        assertEquals(754_805L, p.capital)

        val texto = assertNotNull(textoDeLaCuotaEstimada(p))
        assertTrue(texto.contains("\$1.118.027 de interés"), texto)
        assertTrue(texto.startsWith(ETIQUETA_ESTIMADO), "la estimación se rotula como estimación: $texto")
    }

    @Test
    fun sin_tasa_registrada_no_hay_estimacion() {
        // No es «$0 de interés»: es que no se sabe. Ver ComoVaLaDeuda.SIN_TASA.
        assertNull(textoDeLaCuotaEstimada(plan(sinTasa)))
    }

    @Test
    fun sin_cuota_registrada_no_hay_estimacion() {
        val sinCuota = vehiculo.copy(terms = vehiculo.terms!!.copy(installment = 0L))
        assertNull(textoDeLaCuotaEstimada(plan(sinCuota)))
    }

    @Test
    fun no_cobra_intereses_lo_dice_asi_y_no_como_cero_de_interes() {
        val texto = assertNotNull(textoDeLaCuotaEstimada(plan(noCobraIntereses)))
        assertTrue(texto.startsWith("No cobra intereses"), texto)
        assertTrue(!texto.contains("de interés"), "no puede decir «$0 de interés»: $texto")
        assertTrue(texto.contains("\$10.000.000 a capital"), texto)
    }

    @Test
    fun la_cuota_que_no_alcanza_dice_que_nada_baja_la_deuda() {
        // Hipotecario ·2334: la cuota no cubre interés más seguro. El reparto no puede terminar en
        // «$0 a capital», que se leería como un renglón más del reparto.
        val hipotecario = CreditSummary(
            account = Account("acc_2334", "Hipotecario 2334", AccountType.LOAN, balance = 204_183_376L),
            terms = CreditTerms(
                accountId = "acc_2334", bank = "Davibank", principal = 200_000_000L, rateEa = 15.23,
                termMonths = 240, installment = 2_613_714L, dayOfMonth = 7, startDate = "2026-07-07",
                insuranceMonthly = 209_219L,
            ),
            paidPct = 0.0,
        )
        val texto = assertNotNull(textoDeLaCuotaEstimada(plan(hipotecario)))
        assertTrue(texto.endsWith("nada baja la deuda"), texto)
    }

    @Test
    fun la_fila_encuentra_su_credito_por_el_id_de_la_regla() {
        val planes = planesDeLasCuotas(listOf(vehiculo, cotrafa, sinTasa))
        // El crédito sin tasa SÍ tiene plan (con su motivo); lo que no tiene es estimación.
        assertEquals(setOf("credit_acc_8761", "credit_acc_5413", "credit_acc_techo"), planes.keys)

        val estimacion = assertNotNull(estimacionDeLaFila(fila(vehiculo), planes))
        assertTrue(estimacion.contains("\$2.479.256 de interés"), estimacion)
        assertNull(estimacionDeLaFila(fila(sinTasa), planes))
    }

    @Test
    fun lo_ya_marcado_y_el_saldo_de_una_tarjeta_no_reciben_estimacion() {
        val planes = planesDeLasCuotas(listOf(vehiculo))
        // Ya pagado: la pregunta es qué pagar, no qué pagué.
        assertNull(estimacionDeLaFila(fila(vehiculo, pagado = true), planes))
        // Y el saldo de una tarjeta no se estima nunca. Ver RecurringRule.montoEsSaldo.
        assertNull(estimacionDeLaFila(fila(vehiculo, esSaldo = true), planes))
    }

    @Test
    fun una_fila_sin_credito_cargado_no_inventa_nada() {
        assertNull(estimacionDeLaFila(fila(vehiculo), emptyMap()))
    }
}
