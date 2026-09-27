package com.jvillada.movi.ui.sms

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ola R: el texto crudo del SMS se lee todo con el mismo peso, y el dueño pidió que lo importante
 * —el banco, los montos, las cuentas— salte a la vista al escanear la lista. Estas pruebas fijan
 * EXACTAMENTE qué rangos quedan en negrita, no solo que "algo" se resalte.
 */
class ResaltadoDelTextoDelBancoTest {

    private val negrita = SpanStyle(fontWeight = FontWeight.Bold)

    /** Los rangos con negrita, como pares (texto, inicio, fin-exclusivo) para comparar fácil. */
    private fun rangosEnNegrita(resultado: androidx.compose.ui.text.AnnotatedString) =
        resultado.spanStyles
            .filter { it.item == negrita }
            .map { resultado.text.substring(it.start, it.end) to (it.start to it.end) }

    private fun rango(texto: String, sub: String): Pair<Int, Int> {
        val inicio = texto.indexOf(sub)
        assertTrue(inicio >= 0, "«$sub» no está en «$texto»")
        return inicio to (inicio + sub.length)
    }

    @Test
    fun `el SMS real del dueno resalta banco, monto y las dos cuentas, ni un caracter mas`() {
        val texto = "Bancolombia: Pagaste \$386.902 en la tarjeta de crédito *3684 desde la " +
            "cuenta *8133, el 27/09/2026 09:17…"

        val resultado = resaltadoDelTextoDelBanco(texto)

        assertEquals(texto, resultado.text)
        val esperados = setOf(
            "Bancolombia" to rango(texto, "Bancolombia"),
            "\$386.902" to rango(texto, "\$386.902"),
            "*3684" to rango(texto, "*3684"),
            "*8133" to rango(texto, "*8133"),
        )
        assertEquals(esperados, rangosEnNegrita(resultado).toSet())
    }

    @Test
    fun `una transferencia con cuenta origen y destino resalta las dos`() {
        val texto = "Bancolombia: Transferiste \$1,300,000 desde la cuenta *1234 hacia la " +
            "cuenta *5678, el 27/09/2026 10:00…"

        val resultado = resaltadoDelTextoDelBanco(texto)

        val esperados = setOf(
            "Bancolombia" to rango(texto, "Bancolombia"),
            "\$1,300,000" to rango(texto, "\$1,300,000"),
            "*1234" to rango(texto, "*1234"),
            "*5678" to rango(texto, "*5678"),
        )
        assertEquals(esperados, rangosEnNegrita(resultado).toSet())
    }

    @Test
    fun `el formato con punto de miles tambien se reconoce`() {
        val texto = "Bancolombia: Pagaste \$4.000.000 en la tarjeta *9999, el 27/09/2026 10:00…"

        val resultado = resaltadoDelTextoDelBanco(texto)

        val esperados = setOf(
            "Bancolombia" to rango(texto, "Bancolombia"),
            "\$4.000.000" to rango(texto, "\$4.000.000"),
            "*9999" to rango(texto, "*9999"),
        )
        assertEquals(esperados, rangosEnNegrita(resultado).toSet())
    }

    @Test
    fun `un SMS sin monto ni cuenta ni dos puntos no lleva nada en negrita`() {
        val texto = "Tu clave de un solo uso ya venció, pídela de nuevo."

        val resultado = resaltadoDelTextoDelBanco(texto)

        assertEquals(texto, resultado.text)
        assertEquals(emptyList(), resultado.spanStyles)
    }
}
