package com.jvillada.movi.ui.compartir

import com.jvillada.movi.shared.model.VIGENCIAS_DE_ENLACE
import com.jvillada.movi.shared.model.VIGENCIA_POR_DEFECTO
import com.jvillada.movi.ui.esDeAjustes
import com.jvillada.movi.ui.Screen
import com.jvillada.movi.ui.navTabFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La pantalla «Compartir» sin Compose: las opciones de vencimiento, cómo se dice cuánto le queda a
 * un enlace y si alguien lo abrió, y que los textos digan lo que tienen que decir.
 */
class CompartirLogicTest {

    private val minuto = 60_000L
    private val hora = 60 * minuto
    private val dia = 24 * hora
    private val ahora = 1_790_000_000_000L

    @Test
    fun `las vigencias son uno, siete y treinta dias, y la marcada es una semana`() {
        assertEquals(listOf(1, 7, 30), VIGENCIAS_DE_ENLACE)
        assertEquals(7, VIGENCIA_POR_DEFECTO)
        assertEquals(listOf("1 día", "7 días", "30 días"), VIGENCIAS_DE_ENLACE.map(::rotuloDeVigencia))
    }

    @Test
    fun `un enlace recien creado dice los dias que eligio el duenio`() {
        assertEquals("Vence en 7 días", textoDeVencimiento(ahora + 7 * dia, ahora))
        assertEquals("Vence en 30 días", textoDeVencimiento(ahora + 30 * dia, ahora))
        // Un minuto después sigue diciendo 7, no 6.
        assertEquals("Vence en 7 días", textoDeVencimiento(ahora + 7 * dia - minuto, ahora))
    }

    @Test
    fun `con menos de dia y medio se habla en horas, y al final en minutos`() {
        assertEquals("Vence en 24 horas", textoDeVencimiento(ahora + dia, ahora))
        assertEquals("Vence en 1 hora", textoDeVencimiento(ahora + hora, ahora))
        assertEquals("Vence en 2 horas", textoDeVencimiento(ahora + hora + minuto, ahora))
        assertEquals("Vence en 5 minutos", textoDeVencimiento(ahora + 5 * minuto, ahora))
        assertEquals("Vence en 1 minuto", textoDeVencimiento(ahora + 10_000, ahora))
        assertEquals("Vencido", textoDeVencimiento(ahora, ahora))
        assertEquals("Vencido", textoDeVencimiento(ahora - hora, ahora))
    }

    @Test
    fun `si nadie lo abrio lo dice, y si lo abrieron dice cuantas veces y cuando`() {
        assertEquals("Nadie lo ha abierto todavía", textoDeVistas(null, 0, ahora))
        assertEquals("Abierto 1 vez · la última, hace un momento", textoDeVistas(ahora - 5_000, 1, ahora))
        assertEquals("Abierto 3 veces · la última, hace 2 horas", textoDeVistas(ahora - 2 * hora, 3, ahora))
        assertEquals("Abierto 2 veces · la última, hace 1 día", textoDeVistas(ahora - dia - hora, 2, ahora))
        assertEquals("Abierto 4 veces · la última, hace 15 minutos", textoDeVistas(ahora - 15 * minuto, 4, ahora))
    }

    @Test
    fun `el mensaje lleva el enlace y dice que es de solo lectura y cuanto vale`() {
        val url = "https://movi.test/compartido#abc"
        val mensaje = mensajeParaCompartir(url, 7)
        assertTrue(mensaje.endsWith(url), mensaje)
        assertTrue("solo lectura" in mensaje)
        assertTrue("7 días" in mensaje)
        assertTrue("1 día" in mensajeParaCompartir(url, 1))
    }

    @Test
    fun `la pantalla dice que va a ver la otra persona, que no, y que se puede revocar`() {
        val todo = (listOf(QUE_ES_COMPARTIR, LO_QUE_NO_VA_A_VER) + LO_QUE_VA_A_VER).joinToString(" ")
        assertTrue("solo lectura" in todo)
        assertTrue("patrimonio" in todo)
        assertTrue("deudas" in todo)
        assertTrue("últimos 4 dígitos" in todo, "promete que no se ven los números de cuenta completos")
        assertTrue("revocar" in LO_QUE_NO_VA_A_VER.lowercase())
        assertTrue("movimientos uno por uno" in LO_QUE_NO_VA_A_VER)
        assertTrue("no guarda el enlace" in SE_COMPARTE_AHORA)
    }

    @Test
    fun `Compartir vive en Ajustes, que no es una pestana`() {
        // Ola C: «Más» dejó de ser pestaña; Ajustes se abre por el avatar y no marca ninguna.
        assertEquals(null, navTabFor(Screen.Compartir))
        assertTrue(esDeAjustes(Screen.Compartir))
    }
}
