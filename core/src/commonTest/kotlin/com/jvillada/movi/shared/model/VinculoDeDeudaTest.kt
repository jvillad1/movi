package com.jvillada.movi.shared.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ola Y — `ofreceVincularDeuda`: cuándo ofrecer «¿A cuál crédito o tarjeta corresponde?».
 *
 * Las tres puertas del KDoc: la categoría, EXPENSE, y que no sea ya la mitad de un traspaso. La
 * usan la hoja de reconciliar un SMS y el editor del movimiento, para no divergir sobre cuándo
 * mostrarlo.
 */
class VinculoDeDeudaTest {

    @Test
    fun se_ofrece_para_un_gasto_suelto_categorizado_como_cuota_de_credito() {
        assertTrue(ofreceVincularDeuda(TransactionType.EXPENSE, CUOTA_CATEGORY, transferId = null))
    }

    @Test
    fun se_ofrece_para_un_gasto_suelto_categorizado_como_pago_de_tarjeta() {
        assertTrue(ofreceVincularDeuda(TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, transferId = null))
    }

    @Test
    fun no_se_ofrece_para_otra_categoria() {
        assertFalse(ofreceVincularDeuda(TransactionType.EXPENSE, "Comida", transferId = null))
    }

    @Test
    fun no_se_ofrece_sobre_un_ingreso() {
        // Un traspaso de deuda siempre sale de una cuenta de dinero: nunca sobre un INCOME.
        assertFalse(ofreceVincularDeuda(TransactionType.INCOME, CUOTA_CATEGORY, transferId = null))
    }

    @Test
    fun no_se_ofrece_si_ya_es_la_mitad_de_un_traspaso() {
        // Ya tiene su pata: ofrecer esto sería una segunda forma de armar lo que ya existe.
        assertFalse(ofreceVincularDeuda(TransactionType.EXPENSE, CUOTA_CATEGORY, transferId = "tr-1"))
    }

    @Test
    fun no_se_ofrece_si_ya_es_la_mitad_de_un_traspaso_de_tarjeta() {
        assertFalse(ofreceVincularDeuda(TransactionType.EXPENSE, CARD_PAYMENT_CATEGORY, transferId = "tr-1"))
    }
}
