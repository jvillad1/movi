package com.jvillada.movi.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // ── [CampoConSeleccion]: el intermediario de los campos que guardan un `String` ──────────
    //
    // Cableado a un campo de verdad se ejerce en `CampoConSeleccionTest` (Robolectric). Acá,
    // las invariantes que se pueden mirar sin arrancar Compose — y la primera es la que
    // importa.

    @Test
    fun el_campo_no_empuja_texto_hacia_el_input() {
        // La propiedad que hace viable todo esto: el texto que pinta el campo es SIEMPRE el que
        // vino de afuera. Lo único que sale de acá es la selección. Empujar texto es el intento
        // que ya falló (el botón de «borrar todo» de MoneyField).
        val campo = CampoConSeleccion("Bancolombia") {}
        campo.alCambiar(TextFieldValue("Nequi", TextRange(5)))

        // Todavía nadie sincronizó texto nuevo: el campo sigue mostrando lo de afuera.
        assertEquals("Bancolombia", campo.valor.text)
        // Y cuando la pantalla decide qué guardó, eso es lo que se pinta.
        campo.sincronizar("Nequi")
        assertEquals("Nequi", campo.valor.text)
    }

    @Test
    fun mover_el_cursor_no_dispara_el_onValueChange_de_la_pantalla() {
        // Si cada movimiento de cursor avisara un texto nuevo, media app estaría reguardando
        // en cada tecla de flecha.
        val avisos = mutableListOf<String>()
        val campo = CampoConSeleccion("Bancolombia") { avisos += it }

        campo.alCambiar(TextFieldValue("Bancolombia", TextRange(3)))
        assertTrue(avisos.isEmpty())

        campo.alCambiar(TextFieldValue("Bancolombi", TextRange(10)))
        assertEquals(listOf("Bancolombi"), avisos)
    }

    @Test
    fun sobre_un_campo_vacio_el_atajo_no_se_queda_con_la_tecla() {
        // No hay nada que seleccionar: quedarse con ⌘A sería robarle al navegador algo que sí
        // podría usar. Mismo criterio que MoneyField.
        val vacio = CampoConSeleccion("") {}
        assertFalse(vacio.seleccionarTodo())

        val conTexto = CampoConSeleccion("Bancolombia") {}
        assertTrue(conTexto.seleccionarTodo())
        assertEquals(TextRange(0, 11), conTexto.valor.selection)
    }

    @Test
    fun la_seleccion_sobrevive_al_texto_que_llega_de_afuera() {
        val campo = CampoConSeleccion("Bancolombia") {}
        campo.alCambiar(TextFieldValue("Bancolombia", TextRange(2, 7)))
        campo.sincronizar("Bancolombia")

        assertEquals(TextRange(2, 7), campo.valor.selection)
    }

    @Test
    fun una_seleccion_mas_larga_que_el_texto_nuevo_no_revienta() {
        // Pasa cuando la pantalla recorta lo que se escribió (`it.take(n)`): la selección que
        // teníamos apunta más allá del final. `TextFieldValue` la acota sola, pero si eso
        // cambiara, se rompería acá y no en la mano del dueño.
        val campo = CampoConSeleccion("Bancolombia") {}
        campo.alCambiar(TextFieldValue("Bancolombia", TextRange(0, 11)))
        campo.sincronizar("Nequi")

        assertEquals(TextRange(0, 5), campo.valor.selection)
    }
}
