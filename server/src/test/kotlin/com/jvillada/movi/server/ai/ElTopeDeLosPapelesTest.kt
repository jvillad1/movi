package com.jvillada.movi.server.ai

import com.jvillada.movi.server.auth.RateLimiter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElTopeDeLosPapelesTest {

    @BeforeTest fun limpiar() = RateLimiter.reset()
    @AfterTest fun dejarLimpio() = RateLimiter.reset()

    @Test
    fun `un usuario lee hasta su tope por hora y despues no`() {
        repeat(ElTopeDeLosPapeles.POR_USUARIO_POR_HORA) {
            assertTrue(ElTopeDeLosPapeles.pasa("ana", cuesta = true), "la lectura ${it + 1} tenía que pasar")
        }
        assertFalse(ElTopeDeLosPapeles.pasa("ana", cuesta = true), "pasó del tope por usuario")
        assertTrue(ElTopeDeLosPapeles.pasa("beto", cuesta = true), "el tope de uno frenó a otro")
    }

    @Test
    fun `muchas cuentas no pasan el tope de todos`() {
        var pasaron = 0
        repeat(ElTopeDeLosPapeles.DE_TODOS_POR_HORA + 50) { i ->
            if (ElTopeDeLosPapeles.pasa("cuenta-$i", cuesta = true)) pasaron++
        }
        assertTrue(pasaron == ElTopeDeLosPapeles.DE_TODOS_POR_HORA, "pasaron $pasaron lecturas desde cuentas nuevas")
    }

    @Test
    fun `lo que no cuesta no cuenta`() {
        repeat(ElTopeDeLosPapeles.POR_USUARIO_POR_HORA * 3) {
            assertTrue(ElTopeDeLosPapeles.pasa("ana", cuesta = false))
        }
        assertTrue(ElTopeDeLosPapeles.pasa("ana", cuesta = true), "lo que no costó gastó del balde")
    }
}
