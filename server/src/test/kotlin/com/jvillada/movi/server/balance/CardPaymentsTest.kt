package com.jvillada.movi.server.balance

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `looksLikeCardPayment` es una **propuesta**, no una clasificación: el dueño confirma antes
 * de que cualquier evento cambie de categoría (Task 3). Por eso puede errar de más, pero el
 * caso "PAGO QR/PSE" — un gasto real en un comercio — no puede errar de menos: si ese patrón
 * empieza a matchear, cada compra por QR se propondría como pago de tarjeta.
 */
class CardPaymentsTest {

    @Test
    fun `matchea 'pago autom tc'`() {
        assertTrue(looksLikeCardPayment("PAGO AUTOM TC 1234", "Otros"))
    }

    @Test
    fun `matchea 'pago tarjeta'`() {
        assertTrue(looksLikeCardPayment("Pago tarjeta de crédito", "Otros"))
    }

    @Test
    fun `matchea 'pago tc ' con espacio`() {
        assertTrue(looksLikeCardPayment("PAGO TC 5678 BANCOLOMBIA", "Otros"))
    }

    @Test
    fun `matchea 'abono tarjeta'`() {
        assertTrue(looksLikeCardPayment("Abono tarjeta Visa", "Otros"))
    }

    @Test
    fun `matchea 'pago a tarjeta'`() {
        assertTrue(looksLikeCardPayment("Pago a tarjeta", "Otros"))
    }

    @Test
    fun `el match es case-insensitive`() {
        assertTrue(looksLikeCardPayment("PAGO A TARJETA", "Otros"))
    }

    @Test
    fun `PAGO QR es un gasto real en un comercio, no un pago de tarjeta`() {
        assertFalse(looksLikeCardPayment("PAGO QR Dogger", "Otros"))
    }

    @Test
    fun `PAGO PSE es un gasto real en un comercio, no un pago de tarjeta`() {
        assertFalse(looksLikeCardPayment("PAGO PSE Frisby S A", "Otros"))
    }

    @Test
    fun `un evento ya categorizado como pago de tarjeta no tiene nada que proponer`() {
        assertFalse(looksLikeCardPayment("Pago tarjeta de crédito", CARD_PAYMENT_CATEGORY))
    }

    @Test
    fun `una descripcion sin ninguno de los patrones no matchea`() {
        assertFalse(looksLikeCardPayment("Compra en Éxito", "Mercado"))
    }
}
