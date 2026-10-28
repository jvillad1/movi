package com.jvillada.movi.ui.ai

import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.PreguntaSugerida
import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Vuelta 45 · Movi AI no abre con cifras viejas.** Al abrir el chat a las 3 p. m. se leía, como de
 * hoy, la respuesta de las 9:40 —«Te quedan $2.784.550»— cuando ya quedaban $773.950, y contradecía al
 * chip sugerido. Una respuesta de hoy que cita cifras se pone vieja si pasó más de una hora o si su «Te
 * quedan» ya no es el del Inicio (leído después de ella): se atenúa y dice «Esto era a las 9:40; ahora te
 * quedan $773.950», con «Preguntar de nuevo».
 */
class LasRespuestasDeHoySePonenViejasTest {

    private val hoy = LocalDate(2026, 10, 28)
    private fun a(hora: Int, minuto: Int) =
        LocalDateTime(2026, 10, 28, hora, minuto).toInstant(AppTimeZone.zone).toEpochMilliseconds()

    private val nueveYCuarenta = a(9, 40)
    private val lasTres = a(15, 0)
    private fun pesos(n: Long) = "\$" + n.toString().reversed().chunked(3).joinToString(".").reversed()

    private val comoVoy = ChatMessage(ChatRole.USER, PreguntaSugerida.COMO_VOY.texto, preguntaSugerida = PreguntaSugerida.COMO_VOY.id, enviadoEn = nueveYCuarenta)
    private val laDeLaManana = ChatMessage(ChatRole.ASSISTANT, "Te quedan \$2.784.550 para 4 días · \$696.137 por día.", enviadoEn = nueveYCuarenta + 2_000)

    private fun respuestas(piezas: List<PiezaDelChat>) =
        piezas.filterIsInstance<PiezaDelChat.Mensaje>().filter { it.mensaje.role == ChatRole.ASSISTANT }

    @Test
    fun `la cifra de te quedan se lee de la respuesta`() {
        assertEquals(2_784_550L, teQuedanCitado(laDeLaManana.content))
        assertEquals(773_950L, teQuedanCitado("Te quedan \$773.950 para hoy."))
        assertNull(teQuedanCitado("Entró \$850.000 y salió \$1.752.250."))
        assertTrue(citaCifras(laDeLaManana.content))
        assertFalse(citaCifras("Todavía no puedo decirte cuánto te queda."))
    }

    @Test
    fun `una respuesta de hace 5 horas con cifras distintas a las de ahora no se muestra como actual`() {
        val piezas = piezasDelChat(
            listOf(comoVoy, laDeLaManana),
            hoy,
            ahora = lasTres,
            teQuedanAhora = 773_950L,
            datosDe = lasTres - 60_000,
            cifra = ::pesos,
        )
        val respuesta = respuestas(piezas).single()
        assertTrue(respuesta.deOtroDia, "se atenúa como la de otro día")
        assertEquals("Esto era a las 9:40; ahora te quedan \$773.950", respuesta.vieja)
        assertEquals(comoVoy, respuesta.pregunta, "«Preguntar de nuevo» manda la misma pregunta")
        // Y no abre separadores de día: todo es de hoy.
        assertTrue(piezas.none { it is PiezaDelChat.Dia })
    }

    @Test
    fun `pasada una hora, se pone vieja aunque no se sepa la cifra de ahora`() {
        val piezas = piezasDelChat(listOf(comoVoy, laDeLaManana), hoy, ahora = lasTres)
        assertEquals("Esto era a las 9:40", respuestas(piezas).single().vieja)
    }

    @Test
    fun `dentro de la hora, con la misma cifra, sigue valiendo`() {
        val piezas = piezasDelChat(
            listOf(comoVoy, laDeLaManana),
            hoy,
            ahora = nueveYCuarenta + 20 * 60_000,
            teQuedanAhora = 2_784_550L,
            datosDe = nueveYCuarenta + 10 * 60_000,
        )
        val respuesta = respuestas(piezas).single()
        assertFalse(respuesta.deOtroDia)
        assertNull(respuesta.vieja)
    }

    @Test
    fun `dentro de la hora, si lo que queda cambio, ya es vieja`() {
        val piezas = piezasDelChat(
            listOf(comoVoy, laDeLaManana),
            hoy,
            ahora = nueveYCuarenta + 20 * 60_000,
            teQuedanAhora = 2_000_000L,
            datosDe = nueveYCuarenta + 10 * 60_000,
            cifra = ::pesos,
        )
        assertEquals("Esto era a las 9:40; ahora te quedan \$2.000.000", respuestas(piezas).single().vieja)
    }

    @Test
    fun `una lectura del Inicio anterior a la respuesta no la desmiente`() {
        val piezas = piezasDelChat(
            listOf(comoVoy, laDeLaManana),
            hoy,
            ahora = nueveYCuarenta + 20 * 60_000,
            teQuedanAhora = 5_000_000L,
            datosDe = nueveYCuarenta - 60 * 60_000,
        )
        assertNull(respuestas(piezas).single().vieja)
    }

    @Test
    fun `una respuesta sin cifras no se pone vieja, y una recien llegada tampoco`() {
        val sinCifras = ChatMessage(ChatRole.ASSISTANT, "Puedo ayudarte con tus pagos.", enviadoEn = nueveYCuarenta)
        assertNull(loQueDiceLaRespuestaVieja(sinCifras, lasTres, 773_950L, lasTres, ::pesos))
        val recien = laDeLaManana.copy(enviadoEn = lasTres + 1_000)
        assertNull(loQueDiceLaRespuestaVieja(recien, lasTres, 773_950L, lasTres, ::pesos))
    }
}
