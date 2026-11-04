package com.jvillada.movi.server.ai

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElGastoDelDiaTest {
    private val topeOriginal = ElGastoDelDia.topeEnDolares
    private val guardadoOriginal = ElGastoDelDia.loGuardadoDelDia
    private val hoyOriginal = ElGastoDelDia.hoy
    private var dia = "2026-11-04"
    private var guardado = mutableMapOf<String, Double>()

    @BeforeTest
    fun armar() {
        ElGastoDelDia.olvidar()
        ElGastoDelDia.topeEnDolares = { 1.0 }
        ElGastoDelDia.loGuardadoDelDia = { guardado[it] ?: 0.0 }
        ElGastoDelDia.hoy = { dia }
    }

    @AfterTest
    fun desarmar() {
        ElGastoDelDia.topeEnDolares = topeOriginal
        ElGastoDelDia.loGuardadoDelDia = guardadoOriginal
        ElGastoDelDia.hoy = hoyOriginal
        ElGastoDelDia.olvidar()
    }

    @Test
    fun `pasado el tope del dia no se llama`() {
        ElGastoDelDia.sumar(600_000.0)
        assertFalse(ElGastoDelDia.seAcabo())
        ElGastoDelDia.sumar(400_000.0)
        assertTrue(ElGastoDelDia.seAcabo())
        assertFailsWith<TopeDiarioDeLaIa> { ElGastoDelDia.comprobar() }
    }

    @Test
    fun `el tope se lee como sin credito, que no prueba el respaldo`() {
        assertEquals(FallaDeLaIa.SIN_CREDITO, fallaDeLaIa(RuntimeException("envuelta", TopeDiarioDeLaIa())))
    }

    @Test
    fun `al otro dia vuelve a empezar`() {
        ElGastoDelDia.sumar(2_000_000.0)
        assertTrue(ElGastoDelDia.seAcabo())
        dia = "2026-11-05"
        assertFalse(ElGastoDelDia.seAcabo())
    }

    @Test
    fun `tras un reinicio arranca desde lo guardado del dia`() {
        guardado[dia] = 999_000.0
        assertFalse(ElGastoDelDia.seAcabo())
        ElGastoDelDia.sumar(1_000.0)
        assertTrue(ElGastoDelDia.seAcabo())
    }

    @Test
    fun `si la base no contesta arranca en cero`() {
        ElGastoDelDia.loGuardadoDelDia = { error("sin base") }
        assertFalse(ElGastoDelDia.seAcabo())
    }

    @Test
    fun `la variable`() {
        assertEquals(ElGastoDelDia.TOPE_POR_DEFECTO_USD, ElGastoDelDia.topeDeLaVariable(null))
        assertEquals(ElGastoDelDia.TOPE_POR_DEFECTO_USD, ElGastoDelDia.topeDeLaVariable("no-es-un-numero"))
        assertEquals(ElGastoDelDia.TOPE_POR_DEFECTO_USD, ElGastoDelDia.topeDeLaVariable("-3"))
        assertEquals(7.5, ElGastoDelDia.topeDeLaVariable("7,5"))
        assertNull(ElGastoDelDia.topeDeLaVariable("0"), "0 lo apaga")
    }

    @Test
    fun `sin tope no cuenta ni frena`() {
        ElGastoDelDia.topeEnDolares = { null }
        ElGastoDelDia.sumar(10_000_000_000.0)
        assertFalse(ElGastoDelDia.seAcabo())
    }
}
