package com.jvillada.movi.ui.recurrentes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La nota que acompaña a la cuadrícula de «DÍA DEL MES».
 *
 * La cuadrícula en sí no necesita test de lógica: no hay ninguna: 1..31 son los únicos valores que
 * puede producir, que es exactamente el punto del cambio (antes el campo aceptaba «45» y la
 * validación llegaba al guardar). Lo que sí tiene una regla es CUÁNDO se explica el ajuste de los
 * meses cortos.
 */
class DayOfMonthPickerTest {

    @Test
    fun `los dias que caen en todos los meses no necesitan aclaracion`() {
        assertNull(diaCortoHint(1))
        assertNull(diaCortoHint(5))
        assertNull(diaCortoHint(28))
    }

    @Test
    fun `sin dia elegido todavia no hay nada que aclarar`() {
        assertNull(diaCortoHint(null))
    }

    @Test
    fun `el 29, el 30 y el 31 explican el ajuste, con su propio numero`() {
        listOf(29, 30, 31).forEach { dia ->
            val nota = diaCortoHint(dia)
            assertTrue(nota != null && nota.contains("$dia"), "la nota del día $dia tiene que nombrarlo")
        }
    }

    /**
     * El texto dice las DOS mitades del comportamiento real (`occurrenceInMonth` recorta al largo
     * del mes, pero el día guardado no cambia, así que el mes siguiente vuelve a expandirse).
     * Decir solo la primera dejaría al dueño creyendo que elegir 31 le mueve la regla al 28 para
     * siempre.
     */
    @Test
    fun `la nota dice que se recorta y que despues vuelve`() {
        assertEquals(
            "En los meses que no llegan al 31, se toma el último día del mes; el siguiente vuelve al 31.",
            diaCortoHint(31),
        )
    }
}
