package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Vuelta 107 · la guía para bajar el extracto: qué banco la tiene, sus pasos y el rango ideal. */
class ComoSacarElExtractoTest {

    @Test
    fun `Bancolombia tiene la guia verificada, con los pasos de la cuenta y los de la tarjeta`() {
        val guia = guiaDelExtractoPara("Bancolombia Ahorros 8133 · 4057")
        assertSame(GUIA_DE_BANCOLOMBIA, guia)
        assertTrue(guia!!.verificada)
        assertTrue(guia.conRango)
        assertTrue(guia.pasosPara(esTarjeta = false).any { "pestaña «Movimientos»" in it })
        assertTrue(guia.pasosPara(esTarjeta = true).any { "Tarjetas de crédito" in it })
        assertEquals("Formato: PDF o Excel.", formatoDelExtracto(guia))
        assertEquals("Cómo sacar tu extracto de Bancolombia", tituloDeLaGuiaDelExtracto(guia))
    }

    @Test
    fun `Nu tiene una guia generica que dice que no esta verificada`() {
        val guia = guiaDelExtractoPara("Notificación · Nu")
        assertSame(GUIA_DE_NU, guia)
        assertFalse(guia!!.verificada)
        assertFalse(guia.conRango)
        assertTrue(guia.pasos.single().startsWith("Desde la app de Nu:"))
        assertSame(GUIA_DE_NU, guiaDelExtractoPara("Nu Tarjeta"))
    }

    @Test
    fun `un banco sin guia no tiene nada, y Nu no es cualquier palabra que empiece igual`() {
        assertNull(guiaDelExtractoPara("Davivienda 9418"))
        assertNull(guiaDelExtractoPara("Número nuevo"))
        assertNull(guiaDelExtractoPara(""))
        assertNull(guiaDelExtractoPara(null))
        // Dos bancos en el mismo texto: no se adivina.
        assertNull(guiaDelExtractoPara("Traspaso Bancolombia a Nu"))
    }

    @Test
    fun `el banco de la tarjeta gana sobre un nombre de cuenta que no lo dice`() {
        assertSame(GUIA_DE_BANCOLOMBIA, guiaDelExtractoParaAlguno("Bancolombia", "Master Black 3684"))
        assertSame(GUIA_DE_BANCOLOMBIA, guiaDelExtractoParaAlguno(null, "Bancolombia Ahorros 0031"))
        assertNull(guiaDelExtractoParaAlguno(null, "Master Black 3684"))
    }

    @Test
    fun `el rango ideal va desde el ultimo corte hasta hoy`() {
        assertEquals(
            "Rango ideal: desde tu último corte, el 23 de octubre, hasta hoy.",
            rangoIdealDelExtracto(LocalDate(2026, 10, 23), LocalDate(2026, 11, 2)),
        )
        assertEquals("Rango ideal: hoy.", rangoIdealDelExtracto(LocalDate(2026, 11, 2), LocalDate(2026, 11, 2)))
    }
}
