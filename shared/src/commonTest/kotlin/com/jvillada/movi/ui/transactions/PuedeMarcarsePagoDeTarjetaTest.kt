package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CategoryPref
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ola L: «Pago de tarjeta» es la única reservada que la hoja del movimiento sigue ofreciendo. */
class PuedeMarcarsePagoDeTarjetaTest {

    @Test
    fun un_gasto_puede_marcarse() {
        assertTrue(puedeMarcarsePagoDeTarjeta(TransactionType.EXPENSE, emptyMap()))
    }

    @Test
    fun un_ingreso_no() {
        assertFalse(puedeMarcarsePagoDeTarjeta(TransactionType.INCOME, emptyMap()))
    }

    @Test
    fun si_la_escondio_no_se_ofrece() {
        val prefs = mapOf(CARD_PAYMENT_CATEGORY to CategoryPref(hidden = true))
        assertFalse(puedeMarcarsePagoDeTarjeta(TransactionType.EXPENSE, prefs))
    }

    @Test
    fun la_clave_de_las_preferencias_no_distingue_mayusculas() {
        val prefs = mapOf(CARD_PAYMENT_CATEGORY.uppercase() to CategoryPref(hidden = true))
        assertFalse(puedeMarcarsePagoDeTarjeta(TransactionType.EXPENSE, prefs))
    }

    @Test
    fun lo_que_el_dueno_fijo_gana_sobre_el_catalogo() {
        val prefs = mapOf(CARD_PAYMENT_CATEGORY to CategoryPref(pinnedType = "BOTH"))
        assertTrue(puedeMarcarsePagoDeTarjeta(TransactionType.INCOME, prefs))
    }
}
