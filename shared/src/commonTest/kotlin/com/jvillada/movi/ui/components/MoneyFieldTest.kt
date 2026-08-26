package com.jvillada.movi.ui.components

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * F14 · F23 · F34 · F53: un solo componente de monto para toda la app. Estos son los tests de
 * las funciones puras que arman el formateo mientras se escribe (miles al vuelo) y el parseo de
 * vuelta a [Long] — el mismo par de funciones que usan [MoneyField] y los teclados numéricos
 * propios de Presupuestos y Agregar movimiento.
 */
class MoneyFieldTest {

    @Test
    fun `groupDigitsForDisplay agrupa de a tres desde la derecha`() {
        assertEquals("2.000.000", groupDigitsForDisplay("2000000"))
    }

    @Test
    fun `groupDigitsForDisplay con menos de cuatro digitos no agrega puntos`() {
        assertEquals("500", groupDigitsForDisplay("500"))
    }

    @Test
    fun `groupDigitsForDisplay de vacio es vacio`() {
        assertEquals("", groupDigitsForDisplay(""))
    }

    @Test
    fun `formatAmountKeypadDisplay de vacio muestra cero`() {
        assertEquals("0", formatAmountKeypadDisplay(""))
    }

    @Test
    fun `formatAmountKeypadDisplay agrupa la parte entera y respeta el decimal`() {
        assertEquals("2.000.000.5", formatAmountKeypadDisplay("2000000.5"))
    }

    @Test
    fun `parseMoneyDigits de digitos crudos`() {
        assertEquals(2000000L, parseMoneyDigits("2000000"))
    }

    @Test
    fun `parseMoneyDigits ignora separadores de miles y el simbolo de moneda`() {
        assertEquals(2000000L, parseMoneyDigits("$2.000.000"))
    }

    @Test
    fun `parseMoneyDigits de vacio es null`() {
        assertNull(parseMoneyDigits(""))
    }

    @Test
    fun `parseMoneyDigits de solo separadores es null`() {
        assertNull(parseMoneyDigits("$.,"))
    }

    // ── La máquina de estados del campo ────────────────────────────────────────────────────
    //
    // Ola 9 · C: esto es lo único de MoneyField que ya se equivocó DOS veces guardando una cifra
    // distinta de la que el dueño escribió, y hasta acá vivía como lambda inline dentro del
    // `@Composable`: nada en CI podía detectar una regresión. Cada test de abajo es un caso que
    // se rompió de verdad o que una revisión pidió fijar.

    /** Como se ve el campo: el texto y dónde queda el cursor, marcado con "|". */
    private fun pintado(v: TextFieldValue): String =
        if (v.selection.collapsed) {
            v.text.substring(0, v.selection.start) + "|" + v.text.substring(v.selection.start)
        } else {
            v.text.substring(0, v.selection.min) + "[" + v.text.substring(v.selection.min, v.selection.max) +
                "]" + v.text.substring(v.selection.max)
        }

    /** Lo que el sistema manda cuando alguien tipea/borra: texto nuevo con el cursor colapsado. */
    private fun tecleado(texto: String, cursor: Int) = TextFieldValue(texto, TextRange(cursor))

    @Test
    fun `prellenado el cursor va al final del texto AGRUPADO, no del crudo`() {
        // El bloqueante: el texto se medía con los dígitos crudos (7) y se aplicaba sobre el
        // agrupado (9), así que un recurrente de $1.800.000 abría en `1.800.0|00` y una sola
        // tecla lo dejaba en $18.000.900 con «Guardar cambios» habilitado.
        assertEquals("1.800.000|", pintado(moneyFieldFromDigits("1800000")))
    }

    @Test
    fun `prellenado vacio`() {
        assertEquals("|", pintado(moneyFieldFromDigits("")))
    }

    @Test
    fun `tipear de a un digito agrupa y deja el cursor al final`() {
        assertEquals("1|", pintado(nextMoneyField(tecleado("1", 1))))
        assertEquals("18|", pintado(nextMoneyField(tecleado("18", 2))))
        assertEquals("180|", pintado(nextMoneyField(tecleado("180", 3))))
        assertEquals("1.800|", pintado(nextMoneyField(tecleado("1800", 4))))
        assertEquals("18.000|", pintado(nextMoneyField(tecleado("1.8000", 6))))
    }

    @Test
    fun `un evento que no cambia el texto se devuelve TAL CUAL, con su seleccion`() {
        // El caso que hace que "seleccionar todo y reescribir" reemplace en vez de concatenar:
        // seleccionar no edita, así que el campo no tiene nada que reformatear.
        val seleccionTotal = TextFieldValue("1.800.000", TextRange(0, 9))
        assertSame(seleccionTotal, nextMoneyField(seleccionTotal))
        assertEquals("[1.800.000]", pintado(nextMoneyField(seleccionTotal)))
    }

    @Test
    fun `reemplazar con todo seleccionado guarda la cifra nueva, no la concatenada`() {
        // 50000 + ⌘A + 7000 daba $500.007.000. Ahora el sistema manda el texto ya reemplazado.
        val despues = nextMoneyField(tecleado("7000", 4))
        assertEquals("7.000|", pintado(despues))
        assertEquals(7000L, parseMoneyDigits(despues.text))
    }

    @Test
    fun `reemplazar una seleccion parcial reagrupa y el cursor sigue al digito tecleado`() {
        // "1.[800].000" y se teclea 9 -> el sistema manda "1.9.000" con el cursor tras el 9.
        val despues = nextMoneyField(tecleado("1.9.000", 3))
        assertEquals("19|.000", pintado(despues))
        assertEquals(19000L, parseMoneyDigits(despues.text))
    }

    @Test
    fun `un digito con el cursor en el medio reagrupa sin mover el cursor de lugar`() {
        // El medio que quedó abierto en la revisión anterior: el texto pasaba sin tocarse y
        // quedaba `$ 1.8050.000`, que guardaba $18.050.000 sin reagrupar hasta volver al final.
        val despues = nextMoneyField(tecleado("1.8500.000", 4))
        assertEquals("18.5|00.000", pintado(despues))
        assertEquals(18500000L, parseMoneyDigits(despues.text))
    }

    @Test
    fun `backspace al final borra un digito y reagrupa`() {
        assertEquals("180.000|", pintado(nextMoneyField(tecleado("1.800.00", 8))))
    }

    @Test
    fun `backspace sobre una seleccion deja el cursor donde estaba el hueco`() {
        // "1.[800].000" + Backspace -> el sistema manda "1..000" con el cursor en 2.
        assertEquals("1|.000", pintado(nextMoneyField(tecleado("1..000", 2))))
    }

    @Test
    fun `vaciar y volver a escribir no arrastra lo de antes`() {
        val vacio = nextMoneyField(tecleado("", 0))
        assertEquals("|", pintado(vacio))
        assertNull(parseMoneyDigits(vacio.text))
        assertEquals("1.800.000|", pintado(nextMoneyField(tecleado("1800000", 7))))
    }

    @Test
    fun `pegar un monto ya agrupado se acepta sin tocarlo`() {
        assertEquals("1.800.000|", pintado(nextMoneyField(tecleado("1.800.000", 9))))
    }

    @Test
    fun `pegar con separadores ajenos deja solo los digitos`() {
        val despues = nextMoneyField(tecleado("1,800,000", 9))
        assertEquals("1.800.000|", pintado(despues))
        assertEquals(1800000L, parseMoneyDigits(despues.text))
    }

    @Test
    fun `pegar algo que no son digitos no ensucia el campo`() {
        assertEquals("123|", pintado(nextMoneyField(tecleado("abc123", 6))))
        assertEquals("|", pintado(nextMoneyField(tecleado("hola", 4))))
    }

    @Test
    fun `pasarse de maxDigits recorta y no acepta el digito de mas`() {
        // 12 dígitos ya escritos, se teclea el 13 al final: el nuevo es el que se cae.
        val lleno = "999999999999"
        val despues = nextMoneyField(tecleado(groupDigitsForDisplay(lleno) + "7", 16))
        assertEquals(999999999999L, parseMoneyDigits(despues.text))
        assertEquals("999.999.999.999|", pintado(despues))
    }

    @Test
    fun `maxDigits mas chico recorta igual`() {
        assertEquals(1234L, parseMoneyDigits(nextMoneyField(tecleado("12345", 5), maxDigits = 4).text))
    }
}
