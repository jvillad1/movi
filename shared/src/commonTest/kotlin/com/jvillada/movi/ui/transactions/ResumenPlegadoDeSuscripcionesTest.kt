package com.jvillada.movi.ui.transactions

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **Lo que dice «Suscripciones activas» cuando está plegada.**
 *
 * El dueño: *«Debemos dejar que suscripciones sea una opción de filtro o de menú colapsable dentro
 * de recurrentes»*. Plegarla solo sirve si sigue diciendo **lo que se mira de reojo**: cuántos
 * cobros son y cuánto suman al mes. Sin eso, plegar escondería la cifra junto con las filas y la
 * sección pasaría de ocupar mucho a no informar nada.
 *
 * Esa cifra no aparece en ninguna otra parte de la pantalla: «Gastos recurrentes», arriba, mezcla
 * reglas, cuotas de créditos y suscripciones.
 */
class ResumenPlegadoDeSuscripcionesTest {

    @Test
    fun `dice cuantas son y cuanto suman`() {
        assertEquals("9 cobros · $324.500 al mes", resumenPlegadoDeSuscripciones(9, 324_500L))
    }

    @Test
    fun `una sola no dice cobros en plural`() {
        assertEquals("1 cobro · $44.900 al mes", resumenPlegadoDeSuscripciones(1, 44_900L))
    }
}
