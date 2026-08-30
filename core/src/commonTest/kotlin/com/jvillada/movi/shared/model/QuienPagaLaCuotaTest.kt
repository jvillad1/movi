package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «¿De dónde sale esta cuota?» — la pregunta que Movi no sabía hacer.
 *
 * De los nueve créditos del dueño, **tres no se pagan con su sueldo**: las dos hipotecas de
 * Davibank las cubre su pensión voluntaria de Skandia ($11.761.122/mes entre las dos) y el
 * crédito de Cotrafa —que está a su nombre— lo paga su esposa ($1.371.000/mes). Cargarlos sin
 * poder decirlo habría metido **~$13,1 millones mensuales** de gasto inventado en su flujo, y le
 * habría reclamado cuotas vencidas que nadie le debe.
 *
 * Los números de esta suite son los suyos, de `investments/`, y no redondeos inventados: si
 * alguna regla se rompe, el mensaje del test dice cuánta plata real se movió.
 */
class QuienPagaLaCuotaTest {

    private fun hipoteca1254(paga: String?) = CreditTerms(
        accountId = "acc-1254",
        bank = "Davibank",
        principal = 784_000_000,
        rateEa = 10.99,
        termMonths = 180,
        installment = 9_147_408,
        dayOfMonth = 12,
        startDate = "2025-06-12",
        paidBy = paga,
    )

    @Test
    fun por_defecto_la_paga_el_dueno() {
        // Lo normal es que la cuota salga de su cuenta: `null` no puede significar otra cosa, o
        // los créditos que ya tiene cargados cambiarían de comportamiento al desplegar esto.
        assertNull(hipoteca1254(null).paidBy)
        assertFalse(hipoteca1254(null).payrollDeduction)
    }

    @Test
    fun la_cuota_que_paga_otro_no_es_gasto_ni_ingreso_del_mes() {
        // Por NOMBRE y no por tipo de cuenta: el movimiento vive en la cuenta LOAN, que ya está
        // excluida, pero dejarlo implícito haría que la exclusión dependiera de dónde quedó
        // guardado. Misma disciplina que el descuento de nómina.
        assertFalse(isCashFlow(AccountType.SAVINGS, TransactionType.EXPENSE, THIRD_PARTY_PAYMENT_CATEGORY))
        assertFalse(isCashFlow(AccountType.CHECKING, TransactionType.INCOME, THIRD_PARTY_PAYMENT_CATEGORY))
        assertFalse(isCashFlow(AccountType.LOAN, TransactionType.INCOME, THIRD_PARTY_PAYMENT_CATEGORY))
    }

    @Test
    fun esta_protegida_como_las_otras_cinco() {
        // Si dejara de estarlo, la pantalla de Categorías la ofrecería para renombrar y el mes
        // entero cambiaría de cifra.
        assertTrue(isReservedCategory(THIRD_PARTY_PAYMENT_CATEGORY))
        assertTrue(isReservedCategory("pago de un tercero"), "se compara sin distinguir mayúsculas")
    }

    @Test
    fun la_deuda_sigue_siendo_del_dueno_aunque_la_pague_otro() {
        // La regla central, y la más fácil de romper por «ayudar»: quién paga la cuota NO cambia
        // de quién es el pasivo. Si esto se invirtiera, la deuda del dueño caería $869,3M de un
        // saque —las dos hipotecas más Cotrafa— y su patrimonio se vería mejor por un cambio de
        // rótulo.
        val conTercero = hipoteca1254("Skandia")
        assertEquals(784_000_000, conTercero.principal)
        assertEquals(9_147_408, conTercero.installment)
        // Lo único que cambia es de dónde sale la plata cada mes.
        assertEquals("Skandia", conTercero.paidBy)
    }

    @Test
    fun sobrevive_a_un_cliente_viejo_que_no_conoce_el_campo() {
        // `paidBy` tiene default, así que un APK anterior deserializa sin romperse — pero va a
        // creer que la cuota la paga el dueño. Es la degradación correcta (la de siempre: mostrar
        // de más, no de menos) y queda dicha acá para que nadie la descubra en producción.
        val sinCampo = CreditTerms(
            accountId = "acc-x",
            bank = "Davibank",
            principal = 1,
            rateEa = 1.0,
            termMonths = 1,
            installment = 1,
            dayOfMonth = 1,
            startDate = "2026-01-01",
        )
        assertNull(sinCampo.paidBy)
    }
}
