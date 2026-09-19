package com.jvillada.movi.data

import com.jvillada.movi.ui.components.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class FalloDeRedTest {

    @Test
    fun `un Error del fetch sale como IOException con la causa adentro`() {
        // Lo que rechaza el motor JS de Ktor en wasm: un `kotlin.Error`, no una Exception.
        val delFetch = Error("Fail to fetch", RuntimeException("TypeError: Failed to fetch"))
        val traducida = comoFalloDeRed(delFetch)!!
        assertEquals("Fail to fetch", traducida.message)
        assertSame(delFetch, traducida.cause)
    }

    @Test
    fun `la traduccion se sigue leyendo como un problema de conexion`() {
        val traducida = comoFalloDeRed(Error("Fail to fetch"))!!
        assertEquals("No se pudo conectar al servidor. Intenta más tarde.", traducida.toUserMessage())
    }

    @Test
    fun `un Throwable sin mensaje igual queda como fallo de red`() {
        val traducida = comoFalloDeRed(object : Throwable() {})!!
        assertEquals("Fail to fetch", traducida.message)
    }

    @Test
    fun `una Exception se deja como esta`() {
        assertNull(comoFalloDeRed(IllegalStateException("HTTP 500")))
        assertNull(comoFalloDeRed(kotlinx.io.IOException("sin red")))
    }

    @Test
    fun `la cancelacion no se toca`() {
        assertNull(comoFalloDeRed(CancellationException("la hoja se cerró")))
    }
}
