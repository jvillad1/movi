package com.jvillada.movi.ui.transactions

import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Arreglar uno arregla los parecidos** — la mitad que decide qué se ofrece y cómo se dice.
 *
 * La pregunta se hace una sola vez por cambio de categoría, así que tiene que valer la pena: no se
 * ofrece cambiar lo que ya está cambiado, y el texto tiene que nombrar el lugar para que el dueño
 * sepa sobre qué está decidiendo.
 */
class OfertaDeLoteTest {

    private fun mov(id: String, nombre: String, categoria: String) = FinancialEvent(
        id = id, accountId = "a1", type = TransactionType.EXPENSE, amount = 25_000,
        category = categoria, description = nombre, timestamp = 1_000,
    )

    @Test
    fun `no se ofrece cambiar lo que ya esta en esa categoria`() {
        val parecidos = listOf(
            mov("e1", "Mora Soccer", "Otros"),
            mov("e2", "Mora Soccer", "Fútbol"),
            mov("e3", "Mora Soccer", "Comida"),
        )

        val aCambiar = parecidosQueCambiarian(parecidos, "Fútbol")

        assertEquals(listOf("e1", "e3"), aCambiar.map { it.id })
    }

    @Test
    fun `sin parecidos en otra categoria no hay nada que preguntar`() {
        assertTrue(parecidosQueCambiarian(listOf(mov("e1", "Mora Soccer", "Fútbol")), "Fútbol").isEmpty())
        assertTrue(parecidosQueCambiarian(emptyList(), "Fútbol").isEmpty())
    }

    @Test
    fun `el titulo nombra el lugar y cuenta bien`() {
        val cuatro = OfertaDeLote(
            categoria = "Fútbol",
            movimiento = mov("e0", "Mora Soccer", "Fútbol"),
            otros = (1..4).map { mov("e$it", "Mora Soccer", "Otros") },
        )
        assertEquals("Hay 4 movimientos más de «Mora Soccer»", tituloDeLaOferta(cuatro))
    }

    /** «1 movimientos» es de las cosas que hacen que una app se sienta escrita por una máquina. */
    @Test
    fun `con uno solo el titulo va en singular`() {
        val uno = OfertaDeLote(
            categoria = "Fútbol",
            movimiento = mov("e0", "Mora Soccer", "Fútbol"),
            otros = listOf(mov("e1", "Mora Soccer", "Otros")),
        )
        assertEquals("Hay 1 movimiento más de «Mora Soccer»", tituloDeLaOferta(uno))
    }

    @Test
    fun `sin nombre el titulo sigue siendo una frase`() {
        val sinNombre = OfertaDeLote(
            categoria = "Fútbol",
            movimiento = mov("e0", "   ", "Fútbol"),
            otros = listOf(mov("e1", "Mora Soccer", "Otros")),
        )
        assertEquals("Hay 1 movimiento más de «este lugar»", tituloDeLaOferta(sinNombre))
    }
}
