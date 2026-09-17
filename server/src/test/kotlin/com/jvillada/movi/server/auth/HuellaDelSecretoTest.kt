package com.jvillada.movi.server.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * La huella existe para que un secreto que cambia solo se vea en el log de arranque: en producción
 * `JWT_SECRET` se generaba en cada lectura y todas las sesiones morían en cada despliegue, sin
 * que nada lo dijera. Lo que se prueba acá es que la huella sirve para eso y no filtra el secreto.
 */
class HuellaDelSecretoTest {

    @Test
    fun `la misma llave da la misma huella, y otra llave da otra`() {
        assertEquals(JwtConfig.huellaDe("una-llave-larga"), JwtConfig.huellaDe("una-llave-larga"))
        assertNotEquals(JwtConfig.huellaDe("una-llave-larga"), JwtConfig.huellaDe("una-llave-largb"))
    }

    @Test
    fun `la huella no deja ver el secreto`() {
        val secreto = "0123456789abcdef0123456789abcdef"
        val huella = JwtConfig.huellaDe(secreto)
        assertEquals(6, huella.length)
        assertFalse(secreto.contains(huella), "la huella no puede ser un pedazo del secreto")
        assertFalse(huella.contains(secreto.take(4)))
    }
}
