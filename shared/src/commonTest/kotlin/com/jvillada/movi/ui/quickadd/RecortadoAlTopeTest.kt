package com.jvillada.movi.ui.quickadd

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jvillada.movi.shared.model.MAX_CONCEPTO_LENGTH
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El tope del campo de nota de la hoja de «Agregar». Sin él, una nota larga se guardaba en el
 * teléfono, el server explotaba al meterla en su `varchar(255)` y —como un 500 no cae en el
 * 400..499 que marca `syncError`— la fila se reintentaba cada 30 segundos para siempre, callada.
 */
class RecortadoAlTopeTest {

    @Test
    fun lo_que_entra_pasa_tal_cual() {
        val corto = TextFieldValue("Almuerzo con Ana", TextRange(5))
        assertEquals(corto, recortadoAlTope(corto, MAX_CONCEPTO_LENGTH))
    }

    @Test
    fun lo_que_no_entra_se_recorta_en_vez_de_descartarse() {
        val pegado = TextFieldValue("a".repeat(400), TextRange(400))

        val recortado = recortadoAlTope(pegado, MAX_CONCEPTO_LENGTH)

        assertEquals(MAX_CONCEPTO_LENGTH, recortado.text.length, "queda lo que entra, no nada")
    }

    /**
     * La selección no puede quedar apuntando al texto que ya no está: un rango fuera de límites en
     * un campo de texto es una excepción, no un detalle cosmético.
     */
    @Test
    fun la_seleccion_queda_dentro_de_lo_que_quedo() {
        val pegado = TextFieldValue("a".repeat(400), TextRange(380, 400))

        val recortado = recortadoAlTope(pegado, MAX_CONCEPTO_LENGTH)

        assertEquals(TextRange(MAX_CONCEPTO_LENGTH, MAX_CONCEPTO_LENGTH), recortado.selection)
    }
}
