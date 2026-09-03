package com.jvillada.movi.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lo que se puede probar sin un navegador: **qué hace la app cuando llega el atajo**.
 *
 * Lo que NO cubre, y hay que decirlo: que ⌘A **llegue**. Eso depende del enrutado de teclado de
 * Compose-wasm, que es justamente lo que falla (ver [esAtajoDeSeleccionarTodo]), y ningún test de
 * este repo puede observarlo — se confirmó con un teclado físico y una sonda en la página.
 */
class SeleccionarTodoTest {

    @Test
    fun seleccionar_todo_no_toca_el_texto() {
        // La diferencia con el intento fallido del botón «borrar todo»: ahí se cambiaba el TEXTO y
        // el `<input>` oculto quedaba desincronizado. Acá solo se mueve la selección.
        val antes = TextFieldValue("18.000.009", TextRange(10))
        val despues = conTodoSeleccionado(antes)

        assertEquals(antes.text, despues.text)
        assertEquals(TextRange(0, 10), despues.selection)
    }

    @Test
    fun el_ancla_va_al_principio_y_no_al_final() {
        // Con `TextRange(length, 0)` el cursor quedaba al principio del texto nuevo al reescribir.
        val v = conTodoSeleccionado(TextFieldValue("7.000", TextRange(0)))
        assertEquals(0, v.selection.start)
        assertEquals(5, v.selection.end)
    }

    @Test
    fun sobre_un_campo_vacio_no_hay_nada_que_seleccionar() {
        val v = conTodoSeleccionado(TextFieldValue("", TextRange(0)))
        assertTrue(v.selection.collapsed)
    }

    /**
     * **El caso que abrió todo esto, cerrado de punta a punta.** Con la selección puesta, la
     * edición que reporta el sistema al escribir «7000» encima es el texto reemplazado — y
     * [nextMoneyField] lo devuelve como $7.000, no como $180.000.097.000.
     */
    @Test
    fun con_todo_seleccionado_reescribir_reemplaza_en_vez_de_concatenar() {
        val conMonto = TextFieldValue("18.000.009", TextRange(10))
        val seleccionado = conTodoSeleccionado(conMonto)
        // Lo que manda el sistema cuando se escribe sobre una selección completa: solo lo nuevo.
        val loQueLlega = TextFieldValue("7000", TextRange(4))

        assertEquals(TextRange(0, 10), seleccionado.selection)
        assertEquals("7.000", nextMoneyField(loQueLlega).text)
        assertEquals(7000L, parseMoneyDigits(nextMoneyField(loQueLlega).text))
    }
}
